package dev.kultivator.sekte.gabung;

import dev.kultivator.sekte.SekteConfig;

import java.util.List;

/**
 * Section {@code gabungSekte} of {@code config/kultivator-sekte.json} (DESIGN 41.13.1). {@link #aktif} only gates
 * behaviour; items, entities, payloads, data components, clips and advancements of 41 are always registered (K1).
 * Re-read in place by {@code /kultivasi uji perguruan muat} through {@code SekteConfig.onReload}. The client toggles of
 * 41.13.1 ({@code gabung.jubahDiAtasZirah}, {@code gabung.visualKelas}, {@code gabung.tanggaIlusi},
 * {@code gabung.nametagPangkat}) live in {@code config/kultivator-client.json} (41V-P).
 * <p>
 * Decisions 41.15 (all defaults): K2 {@link #sebaran} {@code seimbang}, K3 {@link #hapusWarisanTertangkap} true, K4
 * {@link #bolehMerosot} true, K5 {@link #bolehSuksesi}/{@link #bolehLebur} true, K7 {@link #usiaTulang} true.
 */
public final class GabungConfig {

	/** The five tier presets of 41.4.2. */
	public static final List<String> SEBARAN = List.of("seimbang", "alami", "agung", "kecil", "datar");

	/** Master switch (registries stay). */
	public boolean aktif = true;
	/** Initial tier preset: {@code seimbang} (default, K2), {@code alami}, {@code agung}, {@code kecil}, {@code datar}. */
	public String sebaran = "seimbang";
	/** The tier of every sect under preset {@code datar} (1-9). */
	public int tingkatDatar = 3;
	/** Simulation growth stops at this tier (1-9). */
	public int tingkatMaks = 9;
	/** Days between a sect's Test Days (3-30). */
	public int hariUjian = 10;
	/** Days before a failed applicant may apply to the same sect again (0-15). */
	public int jedaLamarHari = 3;
	/** Seconds of entrance stage 1, the Test Stair (120-600). */
	public int waktuTangga = 300;
	/** Multiplier of all personal PK earned (0.25-4). */
	public double skalaPk = 1.0D;
	/** Multiplier of promotion costs and residence rent (0.25-4). */
	public double skalaBiaya = 1.0D;
	/** The Bone Age requirement (K7). */
	public boolean usiaTulang = true;
	/** The Infami requirement. */
	public boolean syaratInfami = true;
	/** Branch sects (41.8.2). */
	public boolean bolehCabang = true;
	/** Guest elders (41.8.3). */
	public boolean bolehTamu = true;
	/** Becoming master (41.8.5, K5). */
	public boolean bolehSuksesi = true;
	/** Absorb / merge (41.8.6, K5). */
	public boolean bolehLebur = true;
	/** The MEROSOT state (K4). */
	public boolean bolehMerosot = true;
	/** Deserter hunters (K3). */
	public boolean pemburu = true;
	/** World days a hunt lasts (5-120). */
	public int pemburuHari = 30;
	/** A caught deserter loses the Teknik Warisan (K3). */
	public boolean hapusWarisanTertangkap = true;
	/** Raids on the compound (41.6.10). */
	public boolean serbuanKompleks = true;
	/** Days between compound raids (4-60). */
	public int serbuanHari = 12;
	/** The War Call. */
	public boolean perangPanggil = true;
	/** Days between disciple tournaments (5-60). */
	public int turnamenHari = 15;
	/** Secret-realm quotas. */
	public boolean kuota = true;
	/** Entities of 41 per server (8-48), {@code DuniaBudget.setCap("gabung", n)}. */
	public int tubuhMaks = 24;
	/** Sync the member robe to other players. */
	public boolean jubahPemain = true;

	private static GabungConfig instance = new GabungConfig();
	private static boolean listening;

	/** Reads (or re-reads) the section; from {@code Gabung.init}. */
	static GabungConfig load() {
		instance = SekteConfig.section("gabungSekte", GabungConfig.class, GabungConfig::new, GabungConfig::clamp);
		if (!listening) {
			listening = true;
			SekteConfig.onReload(GabungConfig::load);
		}
		return instance;
	}

	public static GabungConfig get() {
		return instance;
	}

	static GabungConfig clamp(GabungConfig c) {
		c.sebaran = c.sebaran == null || !SEBARAN.contains(c.sebaran) ? "seimbang" : c.sebaran;
		c.tingkatDatar = clamp(c.tingkatDatar, 1, 9);
		c.tingkatMaks = clamp(c.tingkatMaks, 1, 9);
		c.hariUjian = clamp(c.hariUjian, 3, 30);
		c.jedaLamarHari = clamp(c.jedaLamarHari, 0, 15);
		c.waktuTangga = clamp(c.waktuTangga, 120, 600);
		c.skalaPk = clamp(c.skalaPk, 0.25D, 4.0D);
		c.skalaBiaya = clamp(c.skalaBiaya, 0.25D, 4.0D);
		c.pemburuHari = clamp(c.pemburuHari, 5, 120);
		c.serbuanHari = clamp(c.serbuanHari, 4, 60);
		c.turnamenHari = clamp(c.turnamenHari, 5, 60);
		c.tubuhMaks = clamp(c.tubuhMaks, 8, 48);
		return c;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Double.isNaN(value) ? 1.0D : Math.max(min, Math.min(max, value));
	}
}
