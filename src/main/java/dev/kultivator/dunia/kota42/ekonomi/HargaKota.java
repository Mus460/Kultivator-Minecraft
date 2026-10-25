package dev.kultivator.dunia.kota42.ekonomi;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.JenisUpacara;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.sekte.PaktaLindung;
import dev.kultivator.dunia.kota42.tumbuh.Kemakmuran;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarApi;
import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongPredicate;

/**
 * Dynamic town prices (DESIGN 42.6.3, contract C42.C1): one multiplier per category and place,
 * {@code clamp(hargaMin, hargaMaks, permintaan x langka x acara x musim x perang x pelindung x takut)}, cached per place per
 * world hour (12 values). Every seller of 34/38/42 multiplies each listed price by it exactly once ({@code Toko},
 * {@code TokoSenjata}, {@code PasarDesa}, {@code Hidangan}, {@code Penginapan} (JASA), {@code PasarGelap}, the player shops);
 * {@code Dagang} keeps its own demand and saturation and is only multiplied by {@link #faktorDagang} (events x war).
 * Buying back from players uses {@link #faktorJual}: the same factor times the player market flooding, never above
 * 0.5 x the selling price.
 * <ul>
 *   <li><b>permintaan</b>: residents' daily make vs use of the category (census of {@link StokKota}), 0.9-1.15; JASA by
 *   head count, BANGUNAN by how full the beds are.</li>
 *   <li><b>langka</b>: {@code 1 + 0.5 x (100 - stok) / 100} ({@link StokKota}).</li>
 *   <li><b>acara</b>: festival PANGAN x1.15, MEWAH x1.25, KAIN x1.1; a wedding MEWAH x1.05; a funeral KAIN x1.05; plus the
 *   38 capital calendar and every source of {@link #daftarAcara} (42D seasons' feasts).</li>
 *   <li><b>musim</b>: autumn PANGAN x0.8; winter PANGAN x1.1, BAHAN x1.2, KAIN x1.1; spring HERBA x0.9.</li>
 *   <li><b>perang</b> (raids of 42E, beast tides of 38, {@link #daftarPerang}): SENJATA x1.3, OBAT x1.25, PANGAN x1.2,
 *   MEWAH x0.8; production halves and caravans stop ({@link StokKota}).</li>
 *   <li><b>pelindung</b>: x0.97 under a player sect's protection (42E {@code PaktaLindung}).</li>
 *   <li><b>takut</b> (38.R fear meter): {@code 1 + 0.001 x takut}, at most +10 %.</li>
 * </ul>
 * {@code KotaBus.HARGA_BERUBAH} fires when an hour's factor moved more than 2 % from the hour before.
 */
public final class HargaKota {

	/** One multiplier with its reason for the price tooltip ("Langka · stok 23", "Festival +15 %"). */
	public record Pengali(double kali, Component sebab) {
	}

	/** The full breakdown of one factor: the clamped result and every multiplier that is not 1. */
	public record Rincian(Kategori kategori, double faktor, int tren, int stok, List<Pengali> sebab) {
	}

	/** A change for the Papan Harga ("6 biggest changes today"). */
	public record Perubahan(Kategori kategori, double lama, double baru) {
		public double selisih() {
			return baru - lama;
		}
	}

	/** An event source (42D: New Year, Mid-Autumn...): the multiplier for {@code k} at {@code situs}, or null for none. */
	@FunctionalInterface
	public interface SumberAcara {
		@Nullable Pengali kali(MinecraftServer server, long situs, Kategori k);
	}

	private record Cache(long jam, double[] f) {
	}

	private static final List<SumberAcara> ACARA = new CopyOnWriteArrayList<>();
	private static final List<LongPredicate> PERANG = new CopyOnWriteArrayList<>();
	private static final Map<Long, Cache> CACHE = new ConcurrentHashMap<>();
	/** Places at war from the town bus (raid start -> world day it lapses unless ended earlier). */
	private static final Map<Long, Long> SERBUAN = new ConcurrentHashMap<>();
	/** Weddings and funerals running per place (packed: bit 0 wedding, bit 1 funeral) and the day they hold. */
	private static final Map<Long, long[]> UPACARA = new ConcurrentHashMap<>();
	private static final Kategori[] SEMUA = Kategori.values();

	/** Registers an event source (multiplied in after the built-in events). */
	public static void daftarAcara(SumberAcara sumber) {
		if (sumber != null) {
			ACARA.add(sumber);
		}
	}

	/** Registers a war test ({@code situs -> true} while the place is at war or besieged). */
	public static void daftarPerang(LongPredicate sumber) {
		if (sumber != null) {
			PERANG.add(sumber);
		}
	}

	/** From {@code KotaEkonomi.init}: listens to the town bus for raids and ceremonies. */
	static void init() {
		KotaBus.SERBUAN.register((server, situs, mulai, menang) -> {
			if (mulai) {
				SERBUAN.put(situs, KotaHidup.hari(server) + 2L);
			} else {
				SERBUAN.remove(situs);
			}
			CACHE.remove(situs);
		});
		KotaBus.UPACARA.register((server, situs, jenis, id, fase) -> {
			if (jenis != JenisUpacara.NIKAH && jenis != JenisUpacara.MAKAM) {
				return;
			}
			long hari = KotaHidup.hari(server);
			long[] now = UPACARA.computeIfAbsent(situs, k -> new long[] {0L, hari});
			if (now[1] != hari) {
				now[0] = 0L;
				now[1] = hari;
			}
			now[0] |= jenis == JenisUpacara.NIKAH ? 1L : 2L;
			CACHE.remove(situs);
		});
	}

	/** Forgets the memory caches (server stop). */
	static void lupakan() {
		CACHE.clear();
		SERBUAN.clear();
		UPACARA.clear();
	}

	// ================================================================== the factor

	/** The price factor of category {@code kategori} in place {@code situs} now (1.0 = unchanged). */
	public static double faktor(MinecraftServer server, long situs, Kategori kategori) {
		if (server == null || situs == 0L || kategori == null || !aktif()) {
			return 1.0D;
		}
		return semua(server, situs)[kategori.ordinal()];
	}

	/**
	 * The factor a 38 seller applies: the NPC's town ({@code KultivatorNpc.town()}) when it has one, else the place the
	 * player stands in; 1.0 outside every place.
	 */
	public static double faktor(ServerPlayer player, @Nullable KultivatorNpc npc, Kategori kategori) {
		if (player == null || !aktif()) {
			return 1.0D;
		}
		double f = faktor(player.level().getServer(), situs(player, npc), kategori);
		// 42K: the seller's own memory of this player (friend's discount, a cheat's surcharge)
		dev.kultivator.dunia.kota42.warga.Warga w = npc == null || !dev.kultivator.dunia.kota42.cakap.Cakap.aktif() ? null
				: dev.kultivator.dunia.kota42.api.WargaApi.dari(npc);
		return w == null ? f : f * dev.kultivator.dunia.kota42.cakap.Cakap.faktorHarga(w, player);
	}

	/** The town a seller belongs to: the NPC's town, else the place at the player's feet (0 when none). */
	public static long situs(ServerPlayer player, @Nullable KultivatorNpc npc) {
		if (npc != null && npc.town() != 0L) {
			return npc.town();
		}
		if (player != null && player.level() instanceof ServerLevel level && KotaHidup.duniaKota(level)) {
			TempatInfo info = TempatApi.tempatDi(level, player.blockPosition());
			return info == null ? 0L : info.situsId();
		}
		return 0L;
	}

	/** All twelve factors of a place this hour (cached; {@code HARGA_BERUBAH} on a change above 2 %). */
	public static double[] semua(MinecraftServer server, long situs) {
		long jam = StokKota.jamDunia(server);
		Cache c = CACHE.get(situs);
		if (c != null && c.jam() == jam) {
			return c.f();
		}
		double[] f = hitungSemua(server, situs, StokKota.semua(server, situs));
		CACHE.put(situs, new Cache(jam, f));
		if (c != null) {
			for (Kategori k : SEMUA) {
				double lama = c.f()[k.ordinal()];
				double baru = f[k.ordinal()];
				if (Math.abs(baru - lama) > 0.02D * lama) {
					KotaBus.HARGA_BERUBAH.invoker().onHargaBerubah(server, situs, k, (float) lama, (float) baru);
				}
			}
		}
		return f;
	}

	/** The factors from a given stock row without touching the cache ({@link StokKota}'s day run). */
	static double[] hitungSemua(MinecraftServer server, long situs, int[] stok) {
		double[] f = new double[SEMUA.length];
		for (Kategori k : SEMUA) {
			f[k.ordinal()] = hitung(server, situs, k, stok[k.ordinal()], null);
		}
		return f;
	}

	/** One factor; {@code sebab} (when not null) receives every multiplier that is not 1. */
	private static double hitung(MinecraftServer server, long situs, Kategori k, int stok, @Nullable List<Pengali> sebab) {
		if (!aktif()) {
			return 1.0D;
		}
		double f = 1.0D;
		f *= catat(sebab, permintaan(server, situs, k), "permintaan");
		if (k != Kategori.JASA && k != Kategori.BANGUNAN && k != Kategori.DAGANG) {
			double langka = 1.0D + 0.5D * (StokKota.AWAL - stok) / (double) StokKota.AWAL;
			f *= catat(sebab, langka, langka >= 1.0D ? "langka" : "melimpah", stok);
		}
		for (Pengali p : acara(server, situs, k)) {
			f *= p.kali();
			if (sebab != null) {
				sebab.add(p);
			}
		}
		f *= catat(sebab, musim(server, k), "musim." + musimId(server));
		if (perang(server, situs)) {
			f *= catat(sebab, perangKali(k), "perang");
		}
		if (PaktaLindung.pelindung(situs) != null) {
			f *= catat(sebab, 0.97D, "pelindung");
		}
		int takut = PerbaikanApi.takut(situs);
		if (takut > 0) {
			f *= catat(sebab, 1.0D + Math.min(0.10D, 0.001D * takut), "takut", takut);
		}
		KotaConfig.Ekonomi cfg = KotaConfig.ekonomi();
		return Mth.clamp(f, cfg.hargaMin, cfg.hargaMaks);
	}

	private static double catat(@Nullable List<Pengali> sebab, double kali, String kunci, Object... isian) {
		if (sebab != null && Math.abs(kali - 1.0D) >= 0.005D) {
			sebab.add(new Pengali(kali, Component.translatable("kultivator.kota42.harga.sebab." + kunci, isian)));
		}
		return kali;
	}

	/** True while price changes are on (the 42C switch and {@code hargaDinamis}). */
	public static boolean aktif() {
		KotaConfig.Ekonomi cfg = KotaConfig.ekonomi();
		return cfg.aktif && cfg.hargaDinamis;
	}

	// ================================================================== parts

	private static double permintaan(MinecraftServer server, long situs, Kategori k) {
		switch (k) {
			case JASA -> {
				int pop = StokKota.penduduk(server, situs);
				return pop <= 0 ? 1.0D : Mth.clamp(0.9D + pop / 400.0D, 0.9D, 1.15D);
			}
			case BANGUNAN -> {
				TempatHuni huni = Pemanggil.huni(situs);
				if (huni == null) {
					return 1.0D;
				}
				int beds = Math.max(1, huni.titik("kasur").size());
				return Mth.clamp(0.8D + 0.25D * huni.penduduk().size() / beds, 0.9D, 1.15D);
			}
			case DAGANG -> {
				return 1.0D;
			}
			default -> {
				double[] pc = StokKota.imbang(server, situs, k);
				double sum = pc[0] + pc[1];
				return sum <= 0.0D ? 1.0D : Mth.clamp(1.0D + 0.25D * (pc[1] - pc[0]) / sum, 0.9D, 1.15D);
			}
		}
	}

	/** The event multipliers of {@code k} at {@code situs} (38 calendar, weddings and funerals, registered sources). */
	private static List<Pengali> acara(MinecraftServer server, long situs, Kategori k) {
		List<Pengali> out = new ArrayList<>(2);
		String acara = acara38(situs);
		if (acara != null) {
			double kali = switch (acara) {
				case LembarKunci.Acara.ACARA_FESTIVAL -> k == Kategori.PANGAN ? 1.15D : k == Kategori.MEWAH ? 1.25D : k == Kategori.KAIN ? 1.10D : 1.0D;
				case LembarKunci.Acara.ACARA_PASAR_MALAM -> k == Kategori.PANGAN || k == Kategori.MEWAH ? 1.05D : 1.0D;
				case LembarKunci.Acara.ACARA_LELANG_AGUNG -> k == Kategori.MEWAH ? 1.10D : k == Kategori.JASA ? 1.05D : 1.0D;
				case LembarKunci.Acara.ACARA_PERTEMUAN_PIL -> k == Kategori.HERBA ? 1.10D : k == Kategori.OBAT ? 0.95D : 1.0D;
				case LembarKunci.Acara.ACARA_TURNAMEN, LembarKunci.Acara.ACARA_PERTEMUAN_SEKTE ->
						k == Kategori.OBAT ? 1.10D : k == Kategori.JASA || k == Kategori.PANGAN ? 1.05D : 1.0D;
				default -> 1.0D;
			};
			if (kali != 1.0D) {
				out.add(new Pengali(kali, Component.translatable("kultivator.kota42.harga.sebab.acara." + acara)));
			}
		}
		long[] up = UPACARA.get(situs);
		if (up != null && up[1] == KotaHidup.hari(server)) {
			if ((up[0] & 1L) != 0L && k == Kategori.MEWAH) {
				out.add(new Pengali(1.05D, Component.translatable("kultivator.kota42.harga.sebab.nikah")));
			}
			if ((up[0] & 2L) != 0L && k == Kategori.KAIN) {
				out.add(new Pengali(1.05D, Component.translatable("kultivator.kota42.harga.sebab.makam")));
			}
		}
		for (SumberAcara sumber : ACARA) {
			try {
				Pengali p = sumber.kali(server, situs, k);
				if (p != null && p.kali() > 0.0D && Math.abs(p.kali() - 1.0D) >= 0.005D) {
					out.add(p);
				}
			} catch (RuntimeException ignored) {
				// a broken source never breaks a price
			}
		}
		return out;
	}

	/** The running 38 capital event of a place, or null (the beast tide counts as war, not as an event). */
	static @Nullable String acara38(long situs) {
		try {
			String a = KotaBesarApi.acaraAktif(situs);
			return a == null || LembarKunci.Acara.ACARA_PERTAHANAN.equals(a) ? null : a;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static double musim(MinecraftServer server, Kategori k) {
		return switch (musimIndeks(server)) {
			case Jadwal.GUGUR -> k == Kategori.PANGAN ? 0.8D : 1.0D;
			case Jadwal.DINGIN -> k == Kategori.PANGAN ? 1.1D : k == Kategori.BAHAN ? 1.2D : k == Kategori.KAIN ? 1.1D : 1.0D;
			case Jadwal.SEMI -> k == Kategori.HERBA ? 0.9D : 1.0D;
			default -> 1.0D;
		};
	}

	private static int musimIndeks(MinecraftServer server) {
		return (int) Math.floorMod(KotaHidup.hari(server) / Jadwal.HARI_MUSIM, 4L);
	}

	private static String musimId(MinecraftServer server) {
		return switch (musimIndeks(server)) {
			case Jadwal.SEMI -> "semi";
			case Jadwal.PANAS -> "panas";
			case Jadwal.GUGUR -> "gugur";
			default -> "dingin";
		};
	}

	/** True while place {@code situs} is at war: a raid of the town bus, a beast tide, or a registered war source. */
	public static boolean perang(MinecraftServer server, long situs) {
		Long until = SERBUAN.get(situs);
		if (until != null) {
			if (until >= KotaHidup.hari(server)) {
				return true;
			}
			SERBUAN.remove(situs);
		}
		try {
			if (LembarKunci.Acara.ACARA_PERTAHANAN.equals(KotaBesarApi.acaraAktif(situs))) {
				return true;
			}
		} catch (RuntimeException ignored) {
			// no capital calendar
		}
		for (LongPredicate p : PERANG) {
			try {
				if (p.test(situs)) {
					return true;
				}
			} catch (RuntimeException ignored) {
				// a broken source never starts a war
			}
		}
		return false;
	}

	private static double perangKali(Kategori k) {
		return switch (k) {
			case SENJATA -> 1.3D;
			case OBAT -> 1.25D;
			case PANGAN -> 1.2D;
			case MEWAH -> 0.8D;
			default -> 1.0D;
		};
	}

	// ================================================================== derived prices

	/**
	 * The buy-back factor when a player sells {@code k} to the place: {@link #faktor} x the player market flooding
	 * ({@code max(0.6, 1 - 0.01 n)}); callers keep the result at or below 0.5 x their own selling price.
	 */
	public static double faktorJual(MinecraftServer server, long situs, Kategori k) {
		if (server == null || situs == 0L || !aktif()) {
			return 1.0D;
		}
		return faktor(server, situs, k) * StokKota.jenuh(server, situs, k);
	}

	/** {@link #faktorJual} for a 38 seller (NPC town, else the player's place). */
	public static double faktorJual(ServerPlayer player, @Nullable KultivatorNpc npc, Kategori k) {
		if (player == null || !aktif()) {
			return 1.0D;
		}
		return faktorJual(player.level().getServer(), situs(player, npc), k);
	}

	/** The trade-goods multiplier of {@code Dagang} (it has its own demand and saturation): events x war only. */
	public static double faktorDagang(MinecraftServer server, long situs) {
		if (server == null || situs == 0L || !aktif()) {
			return 1.0D;
		}
		double f = 1.0D;
		for (Pengali p : acara(server, situs, Kategori.DAGANG)) {
			f *= p.kali();
		}
		if (perang(server, situs)) {
			f *= 1.1D;
		}
		KotaConfig.Ekonomi cfg = KotaConfig.ekonomi();
		return Mth.clamp(f, cfg.hargaMin, cfg.hargaMaks);
	}

	/** The town price of one {@code stack} (its shop value x the factor of its category), at least 1 NB. */
	public static long harga(MinecraftServer server, long situs, ItemStack stack) {
		long value = EkonomiApi.harga(stack);
		if (value <= 0L) {
			return 0L;
		}
		return Math.max(1L, Math.round(value * faktor(server, situs, Kategori.dari(stack))));
	}

	/** A player sold {@code n} units of {@code k} to the place: stock up a little, market flooding +n. */
	public static void catatJual(MinecraftServer server, long situs, Kategori k, int n) {
		if (server == null || situs == 0L || n <= 0 || !KotaConfig.ekonomi().aktif) {
			return;
		}
		StokKota.ubah(server, situs, k, Math.max(1, (n + 3) / 4));
		StokKota.tambahJenuh(server, situs, k, n);
	}

	/** A player bought {@code n} units of {@code k} in the place: stock down a little. */
	public static void catatBeli(MinecraftServer server, long situs, Kategori k, int n) {
		if (server == null || situs == 0L || n <= 0 || !KotaConfig.ekonomi().aktif) {
			return;
		}
		StokKota.ubah(server, situs, k, -Math.max(1, (n + 3) / 4));
	}

	// ================================================================== display

	/** The breakdown of one factor for the price tooltip ({@code LabelHarga}). */
	public static Rincian rincian(MinecraftServer server, long situs, Kategori k) {
		int stok = StokKota.stok(server, situs, k);
		List<Pengali> sebab = new ArrayList<>();
		double f = hitung(server, situs, k, stok, sebab);
		return new Rincian(k, f, tren(server, situs, k), stok, List.copyOf(sebab));
	}

	/** The tooltip line of a factor: "Langka · stok 23 · Festival +15 %" (empty when nothing moves it). */
	public static Component sebab(MinecraftServer server, long situs, Kategori k) {
		Rincian r = rincian(server, situs, k);
		net.minecraft.network.chat.MutableComponent line = Component.empty();
		boolean first = true;
		for (Pengali p : r.sebab()) {
			if (!first) {
				line.append(Component.literal(" · "));
			}
			first = false;
			int persen = (int) Math.round((p.kali() - 1.0D) * 100.0D);
			line.append(Component.translatable("kultivator.kota42.harga.pengali", p.sebab(), (persen > 0 ? "+" : "") + persen));
		}
		return line;
	}

	/** -1 cheaper, 0 steady, +1 dearer than at the day's start (2 % dead band). */
	public static int tren(MinecraftServer server, long situs, Kategori k) {
		double awal = StokKota.faktorAwal(server, situs)[k.ordinal()] / 1000.0D;
		double now = faktor(server, situs, k);
		if (now > awal * 1.02D) {
			return 1;
		}
		return now < awal * 0.98D ? -1 : 0;
	}

	/** The {@code n} biggest changes since the day's start, largest first (Papan Harga of the market). */
	public static List<Perubahan> perubahanHariIni(MinecraftServer server, long situs, int n) {
		int[] awal = StokKota.faktorAwal(server, situs);
		double[] now = semua(server, situs);
		List<Perubahan> out = new ArrayList<>();
		for (Kategori k : SEMUA) {
			out.add(new Perubahan(k, awal[k.ordinal()] / 1000.0D, now[k.ordinal()]));
		}
		out.sort(Comparator.comparingDouble((Perubahan p) -> Math.abs(p.selisih())).reversed());
		return List.copyOf(out.subList(0, Math.max(0, Math.min(n, out.size()))));
	}

	/** The prosperity multiplier of house prices {@code 0.8 + K / 250} (42F {@code Kemakmuran}, 50 = 1.0). */
	public static double kemakmuran(long situs) {
		return 0.8D + Kemakmuran.nilai(situs) / 250.0D;
	}

	private HargaKota() {
	}
}
