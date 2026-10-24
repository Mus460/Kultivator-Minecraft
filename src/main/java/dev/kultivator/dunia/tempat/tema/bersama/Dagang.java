package dev.kultivator.dunia.tempat.tema.bersama;

import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.tema.KotaTemaData;
import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaInti;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Inter-town trade of DESIGN 38.T.10.1 (single rate {@code NilaiBatu.VALUE}, 38.M.0): buying trade goods at their
 * origin, selling them in other towns, saturation, the weekly profit cap, smuggling and gate customs, the event shifts of
 * demand and the player's Buku Dagang (last prices seen). All state lives in {@code KotaTemaData.inti()}; nothing here
 * touches a block. Money only through {@code NilaiBatu.pay/give} (never an Agung stone handed out).
 * <ul>
 * <li><b>Buy</b> {@link #hargaBeli}: at a town whose market column is the good's origin; base price minus the Nama Baik
 * discount ({@code Barang.diskon}, at most 15 %), -10 % for two days after a caravan or ship of that good arrived
 * ({@link #kafilahTiba}); {@value #STOK_HARIAN} units per good per player per world day.</li>
 * <li><b>Sell</b> {@link #hargaJual}: {@code dasar x permintaan x jenuh}, {@code jenuh = max(0.6, 1 - jenuhPerUnit x n)},
 * n = units of that good sold by all players to that town in the last 24 world hours (one unit recovers per world
 * hour). Demand drifts +-0.05 every 7 world days (hash) and by events ({@link #geserPermintaan}). Goods bought in the
 * same town within 3 days sell back for at most {@code 0.5 x dasar} (38.M.5).</li>
 * <li><b>Cap</b>: profit (sale minus cost basis) per player per world week {@code batasLabaMingguan x (band + 1)}; past
 * it, trade goods sell for {@code 0.5 x dasar} until the next week.</li>
 * <li><b>Smuggling</b>: {@code minyak_bayangan} is bought only by fences ({@link Opsi#penadah}); Infami +2 per sale.
 * Customs {@link #bea}: 40 % chance each smuggled stack is seized for good, Nama Baik -5 in that town; the Gerbang Giok
 * charges 3 % of the value of trade goods brought in (free with a Surat Jalan Gurun).</li>
 * </ul>
 * Screens: the Buku Dagang ({@code tema_dagang}, {@link DagangLayar}); theme markets open it in market mode with
 * {@link DagangLayar#bukaPasar}.
 */
public final class Dagang {

	/** Units of one good a player may buy per world day. */
	public static final int STOK_HARIAN = 16;
	/** Lowest saturation factor. */
	public static final double JENUH_MIN = 0.6D;
	/** Ticks for one unit of saturation to recover (one world hour). */
	public static final long PULIH_TICK = 1000L;
	/** Weekly drift of demand (+-). */
	public static final double GESER_MINGGUAN = 0.05D;
	/** Highest demand factor. */
	public static final double PERMINTAAN_MAKS = 1.35D;
	/** Caravan / ship arrival discount and its length in world days. */
	public static final double DISKON_KAFILAH = 0.10D;
	public static final int HARI_KAFILAH = 2;
	/** Sale price factor past the weekly cap and for buy-back in the origin town. */
	public static final double JUAL_RENDAH = 0.5D;
	/** Days goods bought in a town count as "bought here" (38.M.5). */
	public static final int HARI_ASAL = Barang.ASAL_HARI;
	/** Chance a smuggled stack is seized at customs, the Nama Baik lost and the Infami per smuggling sale. */
	public static final double PELUANG_SITA = 0.40D;
	public static final int NAMA_BAIK_SITA = -5;
	public static final int INFAMI_SELUNDUP = 2;
	/** Duty rate of the Gerbang Giok. */
	public static final double BEA_GIOK = 0.03D;
	/** Advancement {@code tempat/tema_saudagar}: lifetime trade profit. */
	public static final long LABA_SAUDAGAR = 1000L;

	/**
	 * Options of one sale.
	 *
	 * @param penadah a fence buys (smuggled goods allowed; only fences call with true)
	 * @param bea     a sales duty taken from the payment (Kantor Urusan Laut 0.02 at the Pasar Seberang)
	 */
	public record Opsi(boolean penadah, double bea) {
		public static final Opsi BIASA = new Opsi(false, 0.0D);
	}

	/** The result of a buy or sell: units moved, NB paid or received, and the line shown to the player. */
	public record Hasil(int jumlah, long nb, Component pesan, boolean ok) {
	}

	private Dagang() {
	}

	// ================================================================================================ data

	private static CompoundTag inti(MinecraftServer server) {
		return KotaTemaData.get(server).inti();
	}

	private static CompoundTag sub(CompoundTag parent, String key) {
		CompoundTag tag = parent.getCompoundOrEmpty(key);
		if (!parent.contains(key)) {
			parent.put(key, tag);
		}
		return tag;
	}

	/** The player's trade record ({@code inti.pemain.<uuid>}). */
	static CompoundTag pemain(MinecraftServer server, ServerPlayer player) {
		return sub(sub(inti(server), "pemain"), player.getUUID().toString());
	}

	private static void ubah(MinecraftServer server) {
		KotaTemaData.get(server).ubah();
	}

	/** The world day (town day). */
	public static long hari(MinecraftServer server) {
		return Barang.hari(server);
	}

	/** The world week. */
	public static long minggu(MinecraftServer server) {
		return Math.floorDiv(hari(server), 7L);
	}

	// ================================================================================================ demand

	/**
	 * The live demand factor of {@code barang} at site {@code situsId} whose market column is {@code pasar}: the table
	 * value, the weekly drift (+-0.05 by hash, 0 for "not bought"), and the running event shifts; 0 = not bought here.
	 */
	public static double permintaan(MinecraftServer server, long situsId, int pasar, DagangItems.Barang barang) {
		double base = barang.di(pasar);
		if (base <= 0.0D) {
			return 0.0D;
		}
		long week = minggu(server);
		long h = situsId * 0x9E3779B97F4A7C15L ^ week * 0xC2B2AE3D27D4EB4FL ^ barang.id().hashCode() * 0x165667B19E3779F9L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		int step = (int) Math.floorMod(h, 3L) - 1;
		double factor = base + step * GESER_MINGGUAN + geser(server, situsId, barang.id());
		return Mth.clamp(factor, 0.5D, PERMINTAAN_MAKS);
	}

	/** The running event shift of a good at a site (0 when none). */
	public static double geser(MinecraftServer server, long situsId, String barang) {
		CompoundTag site = inti(server).getCompoundOrEmpty("geser").getCompoundOrEmpty(Long.toString(situsId));
		CompoundTag entry = site.getCompoundOrEmpty(barang);
		if (entry.isEmpty() || entry.getLongOr("sampai", 0L) < hari(server)) {
			return 0.0D;
		}
		return entry.getDoubleOr("d", 0.0D);
	}

	/**
	 * Shifts the demand of {@code barang} ({@code dagang/<id>} or {@code <id>}) at site {@code situsId} by {@code delta}
	 * for {@code hari} world days (the design events: sandstorm +0.15 for dates and salted fish in the oasis, Pelita padam
	 * +0.1 for pearls, Pesta Darah -0.1 for venom). A later shift of the same good replaces the earlier one.
	 */
	public static void geserPermintaan(MinecraftServer server, long situsId, String barang, double delta, int hari) {
		DagangItems.Barang b = DagangItems.barang(barang);
		if (server == null || b == null) {
			return;
		}
		CompoundTag site = sub(sub(inti(server), "geser"), Long.toString(situsId));
		CompoundTag entry = new CompoundTag();
		entry.putDouble("d", Mth.clamp(delta, -0.5D, 0.5D));
		entry.putLong("sampai", hari(server) + Math.max(1, hari) - 1);
		site.put(b.id(), entry);
		ubah(server);
	}

	/**
	 * A caravan or ship of goods {@code barangIds} arrived at site {@code situsId}: their buy price there drops 10 % for
	 * {@value #HARI_KAFILAH} days.
	 */
	public static void kafilahTiba(ServerLevel level, long situsId, List<String> barangIds) {
		MinecraftServer server = level.getServer();
		CompoundTag site = sub(sub(inti(server), "kafilah"), Long.toString(situsId));
		long until = hari(server) + HARI_KAFILAH - 1;
		for (String id : barangIds) {
			DagangItems.Barang b = DagangItems.barang(id);
			if (b != null) {
				site.putLong(b.id(), until);
			}
		}
		ubah(server);
	}

	private static boolean diskonKafilah(MinecraftServer server, long situsId, String barang) {
		return inti(server).getCompoundOrEmpty("kafilah").getCompoundOrEmpty(Long.toString(situsId)).getLongOr(barang, -1L) >= hari(server);
	}

	// ================================================================================================ saturation

	/** Units of saturation of a good at a site now (after the hourly recovery). */
	public static double jenuhUnit(MinecraftServer server, long situsId, String barang) {
		CompoundTag entry = inti(server).getCompoundOrEmpty("jenuh").getCompoundOrEmpty(Long.toString(situsId)).getCompoundOrEmpty(barang);
		if (entry.isEmpty()) {
			return 0.0D;
		}
		long now = server.overworld().getGameTime();
		double n = entry.getDoubleOr("n", 0.0D) - (now - entry.getLongOr("t", now)) / (double) PULIH_TICK;
		return Math.max(0.0D, n);
	}

	/** The saturation factor {@code max(0.6, 1 - jenuhPerUnit x n)} for {@code n} units. */
	public static double jenuhDari(double n) {
		return Math.max(JENUH_MIN, 1.0D - TemaConfig.inti().jenuhPerUnit * n);
	}

	/** The saturation factor of a good at a site now. */
	public static double jenuh(MinecraftServer server, long situsId, String barang) {
		return jenuhDari(jenuhUnit(server, situsId, barang));
	}

	private static void tambahJenuh(MinecraftServer server, long situsId, String barang, int units) {
		CompoundTag site = sub(sub(inti(server), "jenuh"), Long.toString(situsId));
		CompoundTag entry = new CompoundTag();
		entry.putDouble("n", Math.min(48.0D, jenuhUnit(server, situsId, barang) + units));
		entry.putLong("t", server.overworld().getGameTime());
		site.put(barang, entry);
	}

	// ================================================================================================ weekly profit

	/** The weekly cap of the player: {@code batasLabaMingguan x (band + 1)} NB. */
	public static long batas(ServerPlayer player) {
		return (long) TemaConfig.inti().batasLabaMingguan * (Barang.pita(player) + 1L);
	}

	/** Profit this world week (may be below 0 after losses). */
	public static long labaMinggu(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompoundTag laba = pemain(server, player).getCompoundOrEmpty("laba");
		return laba.getLongOr("minggu", -1L) == minggu(server) ? laba.getLongOr("nb", 0L) : 0L;
	}

	/** True when the player passed the weekly cap. */
	public static boolean lewatBatas(ServerPlayer player) {
		return labaMinggu(player) >= batas(player);
	}

	/** Profit per day of the last 7 world days, oldest first. */
	public static int[] labaHarian(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompoundTag laba = pemain(server, player).getCompoundOrEmpty("laba");
		long today = hari(server);
		int[] ring = laba.getIntArray("hari").orElse(new int[7]);
		long last = laba.getLongOr("hari_akhir", today);
		int[] out = new int[7];
		for (int i = 0; i < 7; i++) {
			long day = today - 6 + i;
			if (ring.length == 7 && day <= last && last - day < 7) {
				out[i] = ring[(int) Math.floorMod(day, 7L)];
			}
		}
		return out;
	}

	/** Lifetime trade profit of the player. */
	public static long labaTotal(ServerPlayer player) {
		return pemain(player.level().getServer(), player).getCompoundOrEmpty("laba").getLongOr("total", 0L);
	}

	private static void catatLaba(ServerPlayer player, long delta) {
		MinecraftServer server = player.level().getServer();
		CompoundTag laba = sub(pemain(server, player), "laba");
		long week = minggu(server);
		if (laba.getLongOr("minggu", -1L) != week) {
			laba.putLong("minggu", week);
			laba.putLong("nb", 0L);
		}
		laba.putLong("nb", laba.getLongOr("nb", 0L) + delta);
		long today = hari(server);
		int[] ring = laba.getIntArray("hari").orElse(new int[7]);
		if (ring.length != 7) {
			ring = new int[7];
		}
		long last = laba.getLongOr("hari_akhir", today);
		for (long d = last + 1; d <= today && d - last <= 7; d++) {
			ring[(int) Math.floorMod(d, 7L)] = 0;
		}
		if (today - last > 7) {
			ring = new int[7];
		}
		int slot = (int) Math.floorMod(today, 7L);
		ring[slot] = (int) Mth.clamp((long) ring[slot] + delta, Integer.MIN_VALUE, Integer.MAX_VALUE);
		laba.putIntArray("hari", ring);
		laba.putLong("hari_akhir", today);
		long total = laba.getLongOr("total", 0L) + Math.max(0L, delta);
		laba.putLong("total", total);
		if (delta > 0L) {
			Advancements.grant(player, "tempat/tema_dagang");
			CeritaApi.peristiwa(player, TemaCerita.DAGANG_LABA, Long.toString(total));
			if (total >= LABA_SAUDAGAR) {
				Advancements.grant(player, "tempat/tema_saudagar");
			}
		}
	}

	// ================================================================================================ cost basis

	private static void tambahPokok(ServerPlayer player, String barang, int n, long nb, long situsId) {
		MinecraftServer server = player.level().getServer();
		CompoundTag me = pemain(server, player);
		CompoundTag pokok = sub(me, "pokok");
		CompoundTag entry = pokok.getCompoundOrEmpty(barang);
		entry.putInt("n", entry.getIntOr("n", 0) + n);
		entry.putLong("nb", entry.getLongOr("nb", 0L) + nb);
		pokok.put(barang, entry);
		CompoundTag sini = sub(me, "beli_sini");
		CompoundTag here = new CompoundTag();
		String key = situsId + "|" + barang;
		CompoundTag old = sini.getCompoundOrEmpty(key);
		long today = hari(server);
		int keep = old.getLongOr("hari", -99L) >= today - HARI_ASAL + 1 ? old.getIntOr("n", 0) : 0;
		here.putInt("n", keep + n);
		here.putLong("hari", today);
		sini.put(key, here);
	}

	/** Removes {@code n} units from the cost basis and returns their cost (base price for goods of unknown origin). */
	private static long ambilPokok(ServerPlayer player, DagangItems.Barang barang, int n) {
		CompoundTag pokok = sub(pemain(player.level().getServer(), player), "pokok");
		CompoundTag entry = pokok.getCompoundOrEmpty(barang.id());
		int have = entry.getIntOr("n", 0);
		long cost = entry.getLongOr("nb", 0L);
		int known = Math.min(have, n);
		long part = have <= 0 ? 0L : Math.round(cost * (known / (double) have));
		long rest = (long) (n - known) * barang.dasar();
		if (have - known <= 0) {
			pokok.remove(barang.id());
		} else {
			entry.putInt("n", have - known);
			entry.putLong("nb", cost - part);
			pokok.put(barang.id(), entry);
		}
		return part + rest;
	}

	/** Units of {@code barang} the player bought in town {@code situsId} within the last 3 days (buy-back rule). */
	private static int beliSini(ServerPlayer player, long situsId, String barang) {
		MinecraftServer server = player.level().getServer();
		CompoundTag entry = pemain(server, player).getCompoundOrEmpty("beli_sini").getCompoundOrEmpty(situsId + "|" + barang);
		return entry.getLongOr("hari", -99L) >= hari(server) - HARI_ASAL + 1 ? entry.getIntOr("n", 0) : 0;
	}

	private static void kurangiBeliSini(ServerPlayer player, long situsId, String barang, int n) {
		CompoundTag sini = sub(pemain(player.level().getServer(), player), "beli_sini");
		String key = situsId + "|" + barang;
		CompoundTag entry = sini.getCompoundOrEmpty(key);
		int left = entry.getIntOr("n", 0) - n;
		if (left <= 0) {
			sini.remove(key);
		} else {
			entry.putInt("n", left);
			sini.put(key, entry);
		}
	}

	// ================================================================================================ buy

	/** Units of {@code barang} the player may still buy today. */
	public static int sisaStok(ServerPlayer player, String barang) {
		MinecraftServer server = player.level().getServer();
		CompoundTag stok = pemain(server, player).getCompoundOrEmpty("stok");
		if (stok.getLongOr("hari", -1L) != hari(server)) {
			return STOK_HARIAN;
		}
		return Math.max(0, STOK_HARIAN - stok.getIntOr(barang, 0));
	}

	/**
	 * The buy price per unit of {@code barang} for the player at site {@code situsId} (rounded up NB), or -1 when it is not
	 * sold there (not its origin market; smuggled goods only on the black market).
	 */
	public static long hargaBeli(ServerPlayer player, long situsId, DagangItems.Barang barang) {
		MinecraftServer server = player.level().getServer();
		int pasar = TemaInti.pasar(server, situsId);
		if (pasar < 0 || pasar != TemaInti.kolom(barang.asal())) {
			return -1L;
		}
		double price = barang.dasar() * (1.0D - Barang.diskon(player, situsId, false));
		if (diskonKafilah(server, situsId, barang.id())) {
			price *= 1.0D - DISKON_KAFILAH;
		}
		return Math.max(1L, (long) Math.ceil(price - 1.0E-9D));
	}

	/** Buys {@code n} units of {@code barang} at site {@code situsId}. */
	public static Hasil beli(ServerPlayer player, long situsId, DagangItems.Barang barang, int n) {
		MinecraftServer server = player.level().getServer();
		if (!TemaConfig.inti().aktif || !TemaConfig.inti().dagang) {
			return gagal("kultivator.kota_tema.dagang.mati");
		}
		long unit = hargaBeli(player, situsId, barang);
		if (unit < 0L) {
			return gagal("kultivator.kota_tema.dagang.tak_dijual");
		}
		int want = Math.min(n, sisaStok(player, barang.id()));
		if (want <= 0) {
			return gagal("kultivator.kota_tema.dagang.stok_habis", STOK_HARIAN);
		}
		long total = unit * want;
		if (!NilaiBatu.pay(player, total)) {
			return gagal("kultivator.kota.kurang_batu", NilaiBatu.text(total));
		}
		Item item = DagangItems.item(barang.id());
		if (item != null) {
			Barang.beri(player, new ItemStack(item, want));
		}
		CompoundTag me = pemain(server, player);
		CompoundTag stok = sub(me, "stok");
		if (stok.getLongOr("hari", -1L) != hari(server)) {
			for (String key : List.copyOf(stok.keySet())) {
				stok.remove(key);
			}
			stok.putLong("hari", hari(server));
		}
		stok.putInt(barang.id(), stok.getIntOr(barang.id(), 0) + want);
		tambahPokok(player, barang.id(), want, total, situsId);
		lihatHarga(player, situsId);
		ubah(server);
		return new Hasil(want, total, Component.translatable("kultivator.kota_tema.dagang.beli_ok", want, nama(barang), NilaiBatu.text(total)), true);
	}

	// ================================================================================================ sell

	/**
	 * The sale price of ONE more unit of {@code barang} at site {@code situsId} for the player now (NB, may have decimals),
	 * or 0 when nobody buys it there (not bought, smuggled without a fence, trade switched off).
	 */
	public static double hargaJual(ServerPlayer player, long situsId, DagangItems.Barang barang, Opsi opsi) {
		MinecraftServer server = player.level().getServer();
		return hargaJual(server, player, situsId, barang, opsi, 0);
	}

	private static double hargaJual(MinecraftServer server, ServerPlayer player, long situsId, DagangItems.Barang barang, Opsi opsi, int sudah) {
		if (!TemaConfig.inti().aktif || !TemaConfig.inti().dagang) {
			return 0.0D;
		}
		if (barang.selundupan() && !opsi.penadah()) {
			return 0.0D;
		}
		int pasar = TemaInti.pasar(server, situsId);
		if (pasar < 0) {
			return 0.0D;
		}
		double demand = permintaan(server, situsId, pasar, barang);
		if (demand <= 0.0D) {
			return 0.0D;
		}
		double price = barang.dasar() * demand * jenuhDari(jenuhUnit(server, situsId, barang.id()) + sudah);
		if (lewatBatas(player) || beliSini(player, situsId, barang.id()) > sudah) {
			price = Math.min(price, barang.dasar() * JUAL_RENDAH);
		}
		return price * (1.0D - Mth.clamp(opsi.bea(), 0.0D, 0.5D));
	}

	/** Sells {@code n} units of {@code barang} from the player's inventory at site {@code situsId}. */
	public static Hasil jual(ServerPlayer player, long situsId, DagangItems.Barang barang, int n, Opsi opsi) {
		MinecraftServer server = player.level().getServer();
		Item item = DagangItems.item(barang.id());
		if (item == null) {
			return gagal("kultivator.kota_tema.dagang.tak_dibeli");
		}
		int have = hitung(player, item);
		int want = Math.min(n, have);
		if (want <= 0) {
			return gagal("kultivator.kota_tema.dagang.tak_punya", nama(barang));
		}
		if (hargaJual(server, player, situsId, barang, opsi, 0) <= 0.0D) {
			return gagal(barang.selundupan() && !opsi.penadah() ? "kultivator.kota_tema.dagang.selundupan" : "kultivator.kota_tema.dagang.tak_dibeli");
		}
		double sum = 0.0D;
		int back = beliSini(player, situsId, barang.id());
		for (int i = 0; i < want; i++) {
			sum += hargaJual(server, player, situsId, barang, opsi, i);
		}
		long pay = Math.max(1L, Math.round(sum));
		ambil(player, item, want);
		NilaiBatu.give(player, pay);
		long cost = ambilPokok(player, barang, want);
		if (back > 0) {
			kurangiBeliSini(player, situsId, barang.id(), Math.min(back, want));
		}
		catatLaba(player, pay - cost);
		tambahJenuh(server, situsId, barang.id(), want);
		if (opsi.bea() > 0.0D) {
			long duty = Math.round(sum / (1.0D - Mth.clamp(opsi.bea(), 0.0D, 0.5D)) - sum);
			dev.kultivator.dunia.tempat.ekonomi.EkonomiApi.kasKota(situsId, Math.max(0L, duty));
		}
		if (barang.selundupan()) {
			TemaApi.tambahInfami(player, INFAMI_SELUNDUP, "selundupan");
		}
		lihatHarga(player, situsId);
		ubah(server);
		boolean saturated = jenuh(server, situsId, barang.id()) < 0.8D;
		MutableComponent line = Component.translatable("kultivator.kota_tema.dagang.jual_ok", want, nama(barang), NilaiBatu.text(pay),
				Component.literal(String.format(Locale.ROOT, "%+d", pay - cost)));
		if (saturated) {
			line.append(Component.literal(" ")).append(Component.translatable("kultivator.kota_tema.dagang.jenuh_peringatan"));
		}
		return new Hasil(want, pay, line, true);
	}

	// ================================================================================================ crates

	/**
	 * Seals {@code PetiDagangItem.ISI_PETI} units of {@code barang} from the player's inventory into one crate (seal
	 * {@value #SEGEL} NB); {@code kontrak} binds it to a cargo contract ("" = none).
	 */
	public static Hasil segel(ServerPlayer player, long situsId, DagangItems.Barang barang, String kontrak) {
		Item item = DagangItems.item(barang.id());
		if (item == null || hitung(player, item) < PetiDagangItem.ISI_PETI) {
			return gagal("kultivator.kota_tema.dagang.peti_kurang", PetiDagangItem.ISI_PETI, barang != null ? nama(barang) : Component.empty());
		}
		if (!NilaiBatu.pay(player, SEGEL)) {
			return gagal("kultivator.kota.kurang_batu", NilaiBatu.text(SEGEL));
		}
		ambil(player, item, PetiDagangItem.ISI_PETI);
		Barang.beri(player, peti(barang, situsId, kontrak));
		return new Hasil(PetiDagangItem.ISI_PETI, SEGEL, Component.translatable("kultivator.kota_tema.dagang.peti_ok", nama(barang)), true);
	}

	/** Price of the seal of one crate. */
	public static final long SEGEL = 1L;

	/** A new sealed crate of 8 units of {@code barang} (created lazily at runtime). */
	public static ItemStack peti(DagangItems.Barang barang, long asal, String kontrak) {
		ItemStack stack = new ItemStack(DagangItems.PETI);
		stack.set(PetiDagangItem.ISI, new PetiDagangItem.Isi("dagang/" + barang.id(), PetiDagangItem.ISI_PETI, asal, kontrak == null ? "" : kontrak));
		return stack;
	}

	/**
	 * Opens a sealed crate in the player's hand: only inside a town (themed town, capital or Nama Baik town); a crate of a
	 * running cargo contract fails that contract. Returns true when it was opened.
	 */
	public static boolean bukaPeti(ServerPlayer player, ItemStack stack) {
		PetiDagangItem.Isi isi = stack.get(PetiDagangItem.ISI);
		if (isi == null) {
			player.sendSystemMessage(Component.translatable("kultivator.kota_tema.dagang.peti_kosong").withStyle(ChatFormatting.GRAY));
			return false;
		}
		ServerLevel level = player.level();
		if (TemaKota.di(level, player.blockPosition()) == null && !Towns.inTown(level, player.blockPosition())) {
			player.sendSystemMessage(Component.translatable("kultivator.kota_tema.dagang.peti_luar_kota").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (!isi.kontrak().isEmpty()) {
			KontrakMuatan.petiDibuka(player, isi.kontrak());
		}
		DagangItems.Barang barang = DagangItems.barang(isi.barang());
		Item item = barang == null ? null : DagangItems.item(barang.id());
		stack.shrink(1);
		if (item != null) {
			Barang.beri(player, new ItemStack(item, isi.jumlah()));
			player.sendSystemMessage(Component.translatable("kultivator.kota_tema.dagang.peti_buka", isi.jumlah(), nama(barang)).withStyle(ChatFormatting.GOLD));
		}
		return true;
	}

	// ================================================================================================ customs

	/** The result of a customs check. */
	public record HasilBea(int disita, long bea, boolean bebas) {
	}

	/**
	 * Customs at a themed-town gate ({@code gerbang}: {@code giok} the Gerbang Giok 3 % duty, {@code laut} Kantor Urusan
	 * Laut, {@code air} Pintu Air, {@code kaki} Gerbang Kaki; all of them seize smuggled goods). Called by the theme's gate
	 * system when the player passes. A Surat Jalan Gurun waives the duty. Duty is taken only if the player can pay; else
	 * the clerk notes it and lets him pass (no debt).
	 */
	public static HasilBea bea(ServerPlayer player, long situsId, String gerbang) {
		MinecraftServer server = player.level().getServer();
		if (!TemaConfig.inti().aktif || !TemaConfig.inti().dagang) {
			return new HasilBea(0, 0L, true);
		}
		Inventory inv = player.getInventory();
		int seized = 0;
		long value = 0L;
		net.minecraft.util.RandomSource random = player.getRandom();
		for (int slot = 0; slot < inv.getContainerSize(); slot++) {
			ItemStack stack = inv.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			DagangItems.Barang barang = DagangItems.barang(stack.getItem());
			int units = stack.getCount();
			PetiDagangItem.Isi isi = stack.get(PetiDagangItem.ISI);
			if (isi != null) {
				barang = DagangItems.barang(isi.barang());
				units = isi.jumlah() * stack.getCount();
			}
			if (barang == null) {
				continue;
			}
			if (barang.selundupan()) {
				if (random.nextDouble() < PELUANG_SITA) {
					seized += units;
					inv.setItem(slot, ItemStack.EMPTY);
				}
				continue;
			}
			value += (long) units * barang.dasar();
		}
		if (seized > 0) {
			long town = Towns.within(player.level(), player.blockPosition());
			if (town != 0L) {
				KotaData data = KotaData.get(server);
				data.addReputation(town, player.getUUID(), NAMA_BAIK_SITA);
				data.touch();
			}
			inv.setChanged();
			player.sendSystemMessage(Component.translatable("kultivator.kota_tema.dagang.sita", seized).withStyle(ChatFormatting.RED));
		}
		boolean free = !"giok".equals(gerbang) || dev.kultivator.dunia.tempat.tema.oasis.Bea.suratSah(player); // expired passes pay
		long duty = free ? 0L : (long) Math.floor(value * BEA_GIOK);
		if (duty > 0L) {
			if (NilaiBatu.pay(player, duty)) {
				dev.kultivator.dunia.tempat.ekonomi.EkonomiApi.kasKota(situsId, duty);
				player.sendSystemMessage(Component.translatable("kultivator.kota_tema.dagang.bea", NilaiBatu.text(duty)).withStyle(ChatFormatting.GRAY));
			} else {
				player.sendSystemMessage(Component.translatable("kultivator.kota_tema.dagang.bea_maaf").withStyle(ChatFormatting.GRAY));
				duty = 0L;
			}
		} else if (value > 0L && "giok".equals(gerbang)) {
			player.sendSystemMessage(Component.translatable("kultivator.kota_tema.dagang.bea_bebas").withStyle(ChatFormatting.GRAY));
		}
		return new HasilBea(seized, duty, free);
	}

	// ================================================================================================ the trade book

	/** Records the prices of every good at site {@code situsId} as seen by the player now (market visit or a rumour). */
	public static void lihatHarga(ServerPlayer player, long situsId) {
		MinecraftServer server = player.level().getServer();
		int pasar = TemaInti.pasar(server, situsId);
		if (pasar < 0) {
			return;
		}
		CompoundTag site = new CompoundTag();
		long today = hari(server);
		for (DagangItems.Barang b : DagangItems.barang()) {
			double sell = hargaJual(server, player, situsId, b, new Opsi(pasar == DagangItems.PASAR_IBLIS || b.selundupan(), 0.0D), 0);
			CompoundTag cell = new CompoundTag();
			cell.putDouble("nb", sell);
			cell.putDouble("jenuh", jenuh(server, situsId, b.id()));
			cell.putLong("hari", today);
			cell.putBoolean("asal", TemaInti.kolom(b.asal()) == pasar);
			site.put(b.id(), cell);
		}
		sub(pemain(server, player), "buku").put(Long.toString(situsId), site);
		ubah(server);
	}

	/** The player's book entry for site {@code situsId} (empty when never seen). */
	public static CompoundTag buku(ServerPlayer player, long situsId) {
		return pemain(player.level().getServer(), player).getCompoundOrEmpty("buku").getCompoundOrEmpty(Long.toString(situsId));
	}

	/** The site ids the player's book has prices for. */
	public static List<Long> bukuSitus(ServerPlayer player) {
		List<Long> out = new ArrayList<>();
		for (String key : pemain(player.level().getServer(), player).getCompoundOrEmpty("buku").keySet()) {
			try {
				out.add(Long.parseLong(key));
			} catch (NumberFormatException ignored) {
				// not a site
			}
		}
		return out;
	}

	/** The market site the player stands in: a themed town (its site id) or a known Nama Baik town (capital, 34 towns). */
	public static long pasarDi(ServerPlayer player) {
		TemaKota.Kota kota = TemaKota.di(player.level(), player.blockPosition());
		if (kota != null) {
			return kota.situsId();
		}
		return Towns.within(player.level(), player.blockPosition());
	}

	// ================================================================================================ helpers

	/** Display name of a good. */
	public static Component nama(DagangItems.Barang barang) {
		return Component.translatable("item.kultivator.dagang." + barang.id());
	}

	/** Units of {@code item} in the player's inventory. */
	public static int hitung(ServerPlayer player, Item item) {
		Inventory inv = player.getInventory();
		int n = 0;
		for (int slot = 0; slot < inv.getContainerSize(); slot++) {
			ItemStack stack = inv.getItem(slot);
			if (stack.is(item) && stack.get(PetiDagangItem.ISI) == null) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** Removes {@code n} units of {@code item} from the player's inventory. */
	public static void ambil(ServerPlayer player, Item item, int n) {
		Inventory inv = player.getInventory();
		int left = n;
		for (int slot = 0; slot < inv.getContainerSize() && left > 0; slot++) {
			ItemStack stack = inv.getItem(slot);
			if (stack.is(item) && stack.get(PetiDagangItem.ISI) == null) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		inv.setChanged();
	}

	private static Hasil gagal(String key, Object... args) {
		return new Hasil(0, 0L, Component.translatable(key, args), false);
	}

	/** The theme id of a site for the book columns ({@code ibu_kota} for the capital and ordinary towns), or null. */
	public static @Nullable String temaKolom(MinecraftServer server, long situsId) {
		Tema tema = TemaInti.tema(server, situsId);
		if (tema != null) {
			return tema.id();
		}
		return TemaInti.pasar(server, situsId) == DagangItems.PASAR_IBU_KOTA ? "ibu_kota" : null;
	}

	/** The name of a market site: themed town name, capital name, town name, else the marker name of its kind. */
	public static Component namaSitus(MinecraftServer server, long situsId) {
		TemaKota.Kota kota = TemaKota.dari(server, situsId);
		if (kota != null) {
			return kota.nama(server.overworld().getSeed());
		}
		DuniaSites.Site site = DuniaSites.byId(server, situsId);
		String kind = site != null ? site.kind() : Towns.known(server).contains(situsId) ? Towns.jenis(server, situsId) : "";
		if (Towns.JENIS_KOTA_BESAR.equals(kind)) {
			for (dev.kultivator.dunia.tempat.Kandidat k : dev.kultivator.dunia.tempat.RencanaKotaBesar.of(server.overworld().getSeed()).kandidat()) {
				if (dev.kultivator.dunia.tempat.kotabesar.KotaBesarKota.situs(k) == situsId) {
					return dev.kultivator.dunia.tempat.kotabesar.KotaBesarKota.nama(server, k);
				}
			}
		}
		if (Towns.JENIS_KOTA.equals(kind) || Towns.JENIS_ABADI.equals(kind) || Towns.JENIS_KOTA_TEMA.equals(kind)) {
			return dev.kultivator.dunia.kota.KotaNames.town(situsId);
		}
		return kind.isEmpty() ? Component.translatable("kultivator.kota_tema.dagang.kota_tanpa_nama")
				: Component.translatable("kultivator.dunia.marker." + kind.replace('/', '.'));
	}

	/** Position of a market site for distances (null when unknown). */
	public static @Nullable BlockPos posSitus(MinecraftServer server, long situsId) {
		TemaKota.Kota kota = TemaKota.dari(server, situsId);
		if (kota != null) {
			return kota.pusat();
		}
		DuniaSites.Site site = DuniaSites.byId(server, situsId);
		return site != null ? site.pos() : Towns.centre(server, situsId);
	}
}
