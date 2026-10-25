package dev.kultivator.dunia.kota42.ekonomi;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Kepribadian;
import dev.kultivator.dunia.kota42.warga.NamaWarga;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.TempatInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongPredicate;

/**
 * Resident businesses players may invest in (DESIGN 42.6.4, contract C42.C1). A business is a work lot owned by a stored
 * resident: eating houses, tea houses, wine shops, cloth shops, smithies, apothecaries, inns, soup stalls, cake shops,
 * gambling houses (night district), post boats (Kota Air) and bazaar tents (Oasis). A place gets one for every adult
 * resident whose trade runs one ({@link #JENIS}), at most {@link #MAKS_PER_TEMPAT}, found again every day so a new master
 * opens a shop and a closed one leaves.
 * <p>
 * Every world day: {@code omzet = dasar[jenis] x traffic x (1 + mood/200) x HargaKota.faktor x (0.7 + skill/200) x rivals
 * x festival x event}; the day's profit is omzet x margin (5-15 % by the owner's temperament and habits: greedy +3 %,
 * sly +1 %, wise +1 %, lazy -4 %, drunkard -3 %, hard worker +3 %). Every world week the week's profit plus a seeded
 * spread of 5 % of the capital is booked (8 weeks kept), taxed by {@link PajakKota} and shared out by {@link Investasi}.
 * Events: a small fire on the lot (42D calls {@link #kebakaran}) costs the week; a festival day +40 % (42D registers
 * {@link #daftarFestival}); a rival of the same kind opening -15 % for four weeks; an owner turned drunkard -20 %; a
 * disciple sent as manager +10 %. When the owner dies or moves away an heir who learned the trade (a child of age, the
 * best skill) carries on with even odds, else the business closes and shareholders are paid 70-100 % of their stake.
 * <p>
 * State in {@code KotaEkonomiData.situs("usaha", situs)}: {@code n} next id, {@code L} list of businesses ({@code i} id,
 * {@code j} kind, {@code lot}, {@code p} owner warga id, {@code m} capital NB, {@code h} weekly profit long[8] newest
 * first, {@code o} last day's turnover, {@code ow} turnover this week, {@code pw} profit this week (double), {@code ev}
 * event, {@code evs} event until day, {@code sg} rival penalty until day, {@code buka} day opened, {@code s} shares
 * (Investasi), {@code pg}/{@code pgp} manager disciple soul and its master).
 */
public final class UsahaWarga {

	/** One business: id in its place, kind ({@link #JENIS} id), lot (the owner's titik slot), owner resident id, capital NB. */
	public record Usaha(int id, String jenis, int lot, int pemilikWarga, int modal) {
	}

	/** A business kind: id, base daily turnover NB, the price category its sales follow, trades that run it. */
	public record Jenis(String id, int dasar, Kategori kategori, List<NpcRole> peran) {
	}

	/** The business kinds of 42.6.4 in table order. */
	public static final List<Jenis> JENIS = List.of(
			new Jenis("rumah_makan", 30, Kategori.PANGAN, List.of(NpcRole.KOKI)),
			new Jenis("kedai_teh", 24, Kategori.MEWAH, List.of(NpcRole.PEMILIK_TEH, NpcRole.AHLI_TEH)),
			new Jenis("kedai_arak", 22, Kategori.MEWAH, List.of(NpcRole.PELAYAN)),
			new Jenis("toko_kain", 26, Kategori.KAIN, List.of(NpcRole.NYONYA_SUTRA, NpcRole.PENENUN_SUTRA)),
			new Jenis("bengkel_pandai", 28, Kategori.SENJATA, List.of(NpcRole.PANDAI_DESA, NpcRole.EMPU, NpcRole.PANDAI_PUSAKA)),
			new Jenis("apotek", 32, Kategori.OBAT, List.of(NpcRole.ALKEMIS, NpcRole.TABIB, NpcRole.PEDAGANG_PIL)),
			new Jenis("penginapan", 36, Kategori.JASA, List.of(NpcRole.PEMILIK_PENGINAPAN)),
			new Jenis("warung_sup", 14, Kategori.PANGAN, List.of(NpcRole.PENJUAL_IKAN)),
			new Jenis("toko_kue", 18, Kategori.PANGAN, List.of()),
			new Jenis("rumah_judi", 40, Kategori.JASA, List.of(NpcRole.BANDAR_BATU)),
			new Jenis("perahu_pos", 18, Kategori.JASA, List.of(NpcRole.PENDAYUNG)),
			new Jenis("tenda_bazar", 22, Kategori.DAGANG, List.of(NpcRole.PEDAGANG_BAZAR)));

	/** Most businesses one place keeps. */
	public static final int MAKS_PER_TEMPAT = 12;
	/** Capital = base turnover x this (a typical week returns 3-6 %). */
	public static final int MODAL_KALI = 22;
	public static final double MARGIN_DASAR = 0.10D;
	public static final double SIMPANGAN = 0.05D;
	public static final double FESTIVAL = 1.40D;
	public static final double SAINGAN_BARU = 0.85D;
	public static final int SAINGAN_HARI = 28;
	public static final double PEMABUK = 0.80D;
	public static final double PENGELOLA = 1.10D;
	public static final int MINGGU = 8;

	public static final String EV_KEBAKARAN = "kebakaran";
	public static final String EV_WARIS = "waris";
	public static final String EV_SAINGAN = "saingan";
	public static final String EV_SAKIT = "sakit";

	private static final List<LongPredicate> FESTIVAL_SUMBER = new CopyOnWriteArrayList<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		KotaBus.WAFAT.register((server, situs, warga, sebab) -> pemilikPergi(server, situs, warga, "wafat"));
		KotaBus.PINDAH.register((server, warga, asal, tujuan) -> {
			if (asal != 0L && asal != tujuan) {
				pemilikPergi(server, asal, warga, "pindah");
			}
		});
	}

	/** 42D: a source that says a place holds a festival today (+40 % turnover). */
	public static void daftarFestival(LongPredicate sumber) {
		if (sumber != null) {
			FESTIVAL_SUMBER.add(sumber);
		}
	}

	public static boolean aktif() {
		return KotaConfig.ekonomi().aktif && KotaConfig.ekonomi().investasi;
	}

	// ================================================================== reading

	/** The businesses of place {@code situs} (empty before the place was raised). */
	public static List<Usaha> usaha(long situs) {
		MinecraftServer server = KotaHidup.server();
		if (server == null) {
			return List.of();
		}
		List<Usaha> out = new ArrayList<>();
		for (Tag tag : daftar(server, situs)) {
			if (tag instanceof CompoundTag c) {
				out.add(dari(c));
			}
		}
		return out;
	}

	/** One business, or null. */
	public static @Nullable Usaha usaha(long situs, int id) {
		MinecraftServer server = KotaHidup.server();
		CompoundTag c = server == null ? null : mentah(server, situs, id);
		return c == null ? null : dari(c);
	}

	/** The business owned by resident {@code wargaId} in {@code situs}, or null. */
	public static @Nullable Usaha milikWarga(long situs, int wargaId) {
		for (Usaha u : usaha(situs)) {
			if (u.pemilikWarga() == wargaId) {
				return u;
			}
		}
		return null;
	}

	/** The kind with id {@code id}, or null. */
	public static @Nullable Jenis jenis(String id) {
		for (Jenis j : JENIS) {
			if (j.id().equals(id)) {
				return j;
			}
		}
		return null;
	}

	/** "Bakmi Wu": the kind's shop name with the owner's family name. */
	public static Component nama(Usaha u) {
		Warga owner = WargaApi.byId(u.pemilikWarga());
		String marga = owner == null ? "" : NamaWarga.marga(owner.marga);
		return Component.translatable("kultivator.kota42.usaha.nama." + u.jenis(), marga);
	}

	/** Weekly profits of the business, newest first (up to 8). */
	public static long[] laba(MinecraftServer server, long situs, int id) {
		CompoundTag c = mentah(server, situs, id);
		return c == null ? new long[0] : c.getLongArray("h").orElse(new long[0]);
	}

	/** The last day's turnover NB. */
	public static int omzet(MinecraftServer server, long situs, int id) {
		CompoundTag c = mentah(server, situs, id);
		return c == null ? 0 : c.getIntOr("o", 0);
	}

	/** The margin in % for the business's owner. */
	public static int marginPersen(Usaha u) {
		Warga owner = WargaApi.byId(u.pemilikWarga());
		return (int) Math.round(margin(owner) * 100.0D);
	}

	/**
	 * A warning about the business for the investment screen ("owner sick (heir: Lin Hu, apprentice)", "fire last week"),
	 * or null.
	 */
	public static @Nullable Component peringatan(MinecraftServer server, long situs, int id) {
		CompoundTag c = mentah(server, situs, id);
		if (c == null) {
			return null;
		}
		long hari = KotaHidup.hari(server);
		Warga owner = WargaApi.byId(c.getIntOr("p", 0));
		if (owner != null && owner.kesehatan < 30) {
			Warga heir = pewaris(owner);
			return heir == null ? Component.translatable("kultivator.kota42.usaha.peringatan.sakit_tanpa")
					: Component.translatable("kultivator.kota42.usaha.peringatan.sakit", WargaApi.nama(heir));
		}
		String ev = c.getStringOr("ev", "");
		if (!ev.isEmpty() && c.getLongOr("evs", 0L) >= hari) {
			return Component.translatable("kultivator.kota42.usaha.peringatan." + ev);
		}
		if (c.getLongOr("sg", 0L) >= hari) {
			return Component.translatable("kultivator.kota42.usaha.peringatan.saingan");
		}
		return null;
	}

	// ================================================================== raw access for the share ledger

	static ListTag daftar(MinecraftServer server, long situs) {
		CompoundTag site = KotaEkonomiData.get(server).situs(KotaEkonomiData.USAHA, situs);
		ListTag list = site.getListOrEmpty("L");
		if (!site.contains("L")) {
			site.put("L", list);
		}
		return list;
	}

	static @Nullable CompoundTag mentah(MinecraftServer server, long situs, int id) {
		for (Tag tag : daftar(server, situs)) {
			if (tag instanceof CompoundTag c && c.getIntOr("i", -1) == id) {
				return c;
			}
		}
		return null;
	}

	private static Usaha dari(CompoundTag c) {
		return new Usaha(c.getIntOr("i", 0), c.getStringOr("j", "rumah_makan"), c.getIntOr("lot", -1), c.getIntOr("p", 0), c.getIntOr("m", 0));
	}

	// ================================================================== founding

	/** Finds today's businesses of a place: opens one for every master without one (cap 12). */
	static void pastikan(MinecraftServer server, long situs, long hari) {
		ListTag list = daftar(server, situs);
		List<Integer> owners = new ArrayList<>();
		for (Tag tag : list) {
			if (tag instanceof CompoundTag c) {
				owners.add(c.getIntOr("p", 0));
			}
		}
		int koki = 0;
		for (Warga w : WargaApi.wargaTempat(situs)) {
			if (list.size() >= MAKS_PER_TEMPAT) {
				break;
			}
			if (!w.hidup() || owners.contains(w.id) || WargaApi.umur(w) < 16) {
				continue;
			}
			Jenis kind = jenisUntuk(w.peranNpc(), w.id, w.peranNpc() == NpcRole.KOKI ? koki++ : 0);
			if (kind == null) {
				continue;
			}
			buka(server, situs, kind.id(), w.id, w.slot, hari, list);
			owners.add(w.id);
		}
	}

	private static @Nullable Jenis jenisUntuk(NpcRole role, int wargaId, int nthKoki) {
		if (role == NpcRole.KOKI) {
			// the first cook of a place runs the eating house, later ones a cake shop or a soup stall
			return jenis(nthKoki == 0 ? "rumah_makan" : (wargaId & 1) == 0 ? "toko_kue" : "warung_sup");
		}
		for (Jenis j : JENIS) {
			if (j.peran().contains(role)) {
				return j;
			}
		}
		return null;
	}

	/**
	 * Opens a business (also 42F: a new business on a plot financed by a player, who then holds 60 %). Returns its id.
	 * A rival of the same kind already in the place takes -15 % for four weeks.
	 */
	public static int buka(MinecraftServer server, long situs, String jenis, int pemilikWarga, int lot) {
		return buka(server, situs, jenis, pemilikWarga, lot, KotaHidup.hari(server), daftar(server, situs));
	}

	private static int buka(MinecraftServer server, long situs, String jenis, int pemilikWarga, int lot, long hari, ListTag list) {
		CompoundTag site = KotaEkonomiData.get(server).situs(KotaEkonomiData.USAHA, situs);
		int id = site.getIntOr("n", 1);
		site.putInt("n", id + 1);
		Jenis kind = jenis(jenis);
		CompoundTag c = new CompoundTag();
		c.putInt("i", id);
		c.putString("j", jenis);
		c.putInt("lot", lot);
		c.putInt("p", pemilikWarga);
		c.putInt("m", (kind == null ? 20 : kind.dasar()) * MODAL_KALI);
		c.putLong("buka", hari);
		for (Tag tag : list) {
			if (tag instanceof CompoundTag other && jenis.equals(other.getStringOr("j", "")) && other.getLongOr("buka", 0L) < hari) {
				other.putLong("sg", hari + SAINGAN_HARI);
			}
		}
		list.add(c);
		KotaEkonomiData.get(server).ubah();
		return id;
	}

	// ================================================================== the day and the week

	/** The daily pass: finds businesses, then books each one's turnover and profit of the day. */
	static void harian(MinecraftServer server, long situs, long hari) {
		if (!aktif()) {
			return;
		}
		pastikan(server, situs, hari);
		boolean festival = false;
		for (LongPredicate source : FESTIVAL_SUMBER) {
			festival |= source.test(situs);
		}
		double traffic = lalu(server, situs);
		ListTag list = daftar(server, situs);
		for (Tag tag : list) {
			if (!(tag instanceof CompoundTag c)) {
				continue;
			}
			Jenis kind = jenis(c.getStringOr("j", ""));
			Warga owner = WargaApi.byId(c.getIntOr("p", 0));
			if (kind == null || owner == null) {
				continue;
			}
			double rivals = Math.pow(0.85D, Math.max(0, hitung(list, kind.id()) - 1));
			double omzet = kind.dasar() * traffic * (1.0D + Mth.clamp(owner.suasana, -100, 100) / 200.0D)
					* HargaKota.faktor(server, situs, kind.kategori()) * (0.7D + Mth.clamp(owner.ahli, 0, 100) / 200.0D) * rivals;
			if (festival) {
				omzet *= FESTIVAL;
			}
			if (c.getLongOr("sg", 0L) >= hari) {
				omzet *= SAINGAN_BARU;
			}
			if (Kepribadian.punya(owner, "pemabuk")) {
				omzet *= PEMABUK;
			}
			if (!c.getStringOr("pg", "").isEmpty()) {
				omzet *= PENGELOLA;
			}
			if (owner.kesehatan < 30) {
				omzet *= 0.6D;
			}
			boolean fire = EV_KEBAKARAN.equals(c.getStringOr("ev", "")) && c.getLongOr("evs", 0L) >= hari;
			int day = (int) Math.round(fire ? 0.0D : omzet);
			c.putInt("o", day);
			c.putLong("ow", c.getLongOr("ow", 0L) + day);
			c.putDouble("pw", c.getDoubleOr("pw", 0.0D) + day * margin(owner));
		}
		KotaEkonomiData.get(server).ubah();
	}

	private static int hitung(ListTag list, String jenis) {
		int n = 0;
		for (Tag tag : list) {
			if (tag instanceof CompoundTag c && jenis.equals(c.getStringOr("j", ""))) {
				n++;
			}
		}
		return n;
	}

	/** Street traffic of the place's businesses (capital and themed cities are busiest). */
	private static double lalu(MinecraftServer server, long situs) {
		return switch (PajakKota.jenisTempat(server, situs)) {
			case PajakKota.JT_IBU_KOTA -> 1.15D;
			case PajakKota.JT_TEMA -> 1.0D;
			case PajakKota.JT_KOTA -> 0.85D;
			default -> 0.6D;
		};
	}

	/** Margin 5-15 % by temperament and habits (42.6.4). */
	static double margin(@Nullable Warga owner) {
		if (owner == null) {
			return MARGIN_DASAR;
		}
		double m = MARGIN_DASAR;
		switch (Kepribadian.watak(owner)) {
			case "serakah" -> m += 0.03D;
			case "licik", "bijak" -> m += 0.01D;
			case "penakut" -> m -= 0.01D;
			default -> {
			}
		}
		if (Kepribadian.punya(owner, "pemalas")) {
			m -= 0.04D;
		}
		if (Kepribadian.punya(owner, "pemabuk")) {
			m -= 0.03D;
		}
		if (Kepribadian.punya(owner, "pekerja_keras")) {
			m += 0.03D;
		}
		return Mth.clamp(m, 0.05D, 0.15D);
	}

	/**
	 * The weekly pass: books every business's week (profit of the days + a seeded 5 % spread of the capital), shares it
	 * out ({@link Investasi#bagiLaba}) and returns the place's summed profit for the tax.
	 */
	static long mingguan(MinecraftServer server, long situs, long minggu) {
		if (!aktif()) {
			return 0L;
		}
		long sum = 0L;
		long hari = KotaHidup.hari(server);
		for (Tag tag : daftar(server, situs)) {
			if (!(tag instanceof CompoundTag c)) {
				continue;
			}
			long modal = c.getIntOr("m", 0) + Investasi.totalSaham(c);
			RandomSource random = RandomSource.create(situs * 31L + c.getIntOr("i", 0) * 7919L + minggu * 104729L);
			double profit = c.getDoubleOr("pw", 0.0D) + random.nextGaussian() * SIMPANGAN * modal * 0.5D;
			if (EV_KEBAKARAN.equals(c.getStringOr("ev", "")) && c.getLongOr("evs", 0L) >= hari - 7L) {
				profit = -0.02D * modal;
			}
			long week = Math.round(profit);
			long[] old = c.getLongArray("h").orElse(new long[0]);
			long[] next = new long[MINGGU];
			next[0] = week;
			System.arraycopy(old, 0, next, 1, Math.min(old.length, MINGGU - 1));
			c.putLongArray("h", next);
			c.putLong("ow", 0L);
			c.putDouble("pw", 0.0D);
			Investasi.bagiLaba(server, situs, c, week, modal, minggu);
			sum += week;
		}
		KotaEkonomiData.get(server).ubah();
		return sum;
	}

	// ================================================================== events

	/** 42D: a small fire on the lot of resident {@code pemilikWarga}'s business: the week's profit is lost. */
	public static void kebakaran(MinecraftServer server, long situs, int pemilikWarga) {
		for (Tag tag : daftar(server, situs)) {
			if (tag instanceof CompoundTag c && c.getIntOr("p", 0) == pemilikWarga) {
				c.putString("ev", EV_KEBAKARAN);
				c.putLong("evs", KotaHidup.hari(server) + 7L);
				KotaEkonomiData.get(server).ubah();
			}
		}
	}

	/** The adult child who learned the trade best (same trade first, then skill), or null. */
	static @Nullable Warga pewaris(Warga owner) {
		Warga best = null;
		int bestScore = Integer.MIN_VALUE;
		for (int childId : owner.anak) {
			Warga child = WargaApi.byId(childId);
			if (child == null || !child.hidup() || WargaApi.umur(child) < 16) {
				continue;
			}
			int score = child.ahli + (child.peranNpc() == owner.peranNpc() ? 100 : 0);
			if (score > bestScore) {
				bestScore = score;
				best = child;
			}
		}
		return best;
	}

	/** Owner died or moved: an heir carries on (even odds, seeded) or the business closes and pays its shareholders. */
	private static void pemilikPergi(MinecraftServer server, long situs, Warga owner, String sebab) {
		if (server == null) {
			return;
		}
		ListTag list = daftar(server, situs);
		for (int i = list.size() - 1; i >= 0; i--) {
			CompoundTag c = list.getCompoundOrEmpty(i);
			if (c.getIntOr("p", 0) != owner.id) {
				continue;
			}
			Warga heir = "wafat".equals(sebab) ? pewaris(owner) : null;
			RandomSource random = RandomSource.create(situs ^ owner.id * 2654435761L);
			if (heir != null && random.nextBoolean()) {
				c.putInt("p", heir.id);
				c.putString("ev", EV_WARIS);
				c.putLong("evs", KotaHidup.hari(server) + 14L);
				Investasi.kabar(server, c, Component.translatable("kultivator.kota42.usaha.kabar.waris", nama(dari(c)), WargaApi.nama(owner),
						WargaApi.nama(heir)));
			} else {
				Component name = nama(dari(c));
				Investasi.tutup(server, situs, c, 0.70D + random.nextDouble() * 0.30D,
						Component.translatable("kultivator.kota42.usaha.kabar.tutup", name, WargaApi.nama(owner)));
				list.remove(i);
			}
		}
		KotaEkonomiData.get(server).ubah();
	}

	// ================================================================== uji kota_hidup ekonomi usaha

	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("usaha").executes(ctx -> daftarUji(ctx.getSource()));
	}

	private static int daftarUji(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		TempatInfo tempat = PajakKota.tempat(player);
		if (tempat == null) {
			source.sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
			return 0;
		}
		MinecraftServer server = source.getServer();
		pastikan(server, tempat.situsId(), KotaHidup.hari(server));
		List<Usaha> all = usaha(tempat.situsId());
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.usaha.judul", PajakKota.nama(server, tempat.situsId()), all.size())
				.withStyle(ChatFormatting.GOLD), false);
		for (Usaha u : all) {
			long[] h = laba(server, tempat.situsId(), u.id());
			Warga owner = WargaApi.byId(u.pemilikWarga());
			source.sendSuccess(() -> Component.translatable("kultivator.kota42.usaha.baris", u.id(), nama(u), owner == null ? "?" : WargaApi.nama(owner),
					u.modal(), omzet(server, tempat.situsId(), u.id()), marginPersen(u), h.length > 0 ? h[0] : 0L), false);
		}
		return all.size();
	}

	/** Shares of every business of a place by holder, for the town book ({@code uji}). */
	static Map<String, Long> sahamTempat(MinecraftServer server, long situs) {
		Map<String, Long> out = new java.util.LinkedHashMap<>();
		for (Tag tag : daftar(server, situs)) {
			if (tag instanceof CompoundTag c) {
				CompoundTag s = c.getCompoundOrEmpty("s");
				for (String key : s.keySet()) {
					out.merge(key, s.getCompoundOrEmpty(key).getLongOr("v", 0L), Long::sum);
				}
			}
		}
		return out;
	}

	private UsahaWarga() {
	}
}
