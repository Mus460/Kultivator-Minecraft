package dev.kultivator.sosok;

import dev.kultivator.dunia.DuniaConfig;
import net.minecraft.util.Mth;

/**
 * Section {@code sosok} of {@code config/kultivator-dunia.json} (DESIGN 40.6.2, server half): how many recruits are
 * women, children or grown wanderers, how fast a disciple's visible age moves on, and whether body size uses
 * {@code Attributes.SCALE}. Missing values get the defaults below, out-of-range values are clamped and the file is
 * rewritten (the DuniaConfig rule). The client's own settings ({@code sosok_detail}, {@code sosok_wajah}) live in
 * {@code kultivator-client.json} (40R).
 */
public final class SosokConfig {

	/** The POJO Gson reads (public fields with initialisers). */
	public static final class Settings {
		/** Share of women among new disciples, candidates and Special Candidates (0-1). */
		public double rasioPerempuan = 0.5D;
		/** Share of recruits who arrive as children: orphans brought to the sect (0-0.5). */
		public double anakSaatRekrut = 0.10D;
		/** Share of recruits who arrive grown: wanderers who turned back (0-0.5). */
		public double dewasaSaatRekrut = 0.08D;
		/** World days until a child disciple looks young (8-400). */
		public int hariAnakKeMuda = 48;
		/** World days until a young disciple of at most Qi Refining looks adult (24-1000). */
		public int hariMudaKeDewasa = 160;
		/** Body size through {@code Attributes.SCALE}; false = everyone 1.0 except children, who stay 0.66. */
		public boolean skalaTubuh = true;
		/** Height spread by build variant (false = every build of a group has the group's base size). */
		public boolean variasiTinggi = true;

		Settings clamp() {
			rasioPerempuan = Mth.clamp(rasioPerempuan, 0.0D, 1.0D);
			anakSaatRekrut = Mth.clamp(anakSaatRekrut, 0.0D, 0.5D);
			dewasaSaatRekrut = Mth.clamp(dewasaSaatRekrut, 0.0D, 0.5D);
			hariAnakKeMuda = Mth.clamp(hariAnakKeMuda, 8, 400);
			hariMudaKeDewasa = Mth.clamp(hariMudaKeDewasa, 24, 1000);
			return this;
		}
	}

	private static Settings settings = new Settings();
	private static boolean loaded;

	/** Reads the section (called once from {@link Sosok#init()}). */
	static void init() {
		settings = DuniaConfig.section("sosok", Settings.class, Settings::new, Settings::clamp);
		loaded = true;
	}

	/** The section (never null; the same object after a reload). Defaults until {@link #init()} ran. */
	public static Settings get() {
		return settings;
	}

	/** True once the file was read (before that the defaults answer). */
	public static boolean loaded() {
		return loaded;
	}

	/**
	 * The {@code Attributes.SCALE} base value a body of {@code sosok} gets under these settings: the 40.2.3 size, without
	 * the build spread when {@link Settings#variasiTinggi} is off, and 1.0 for everyone but children when
	 * {@link Settings#skalaTubuh} is off.
	 */
	public static float skala(Sosok sosok) {
		Settings s = settings;
		if (!s.skalaTubuh) {
			return sosok.anak() ? 0.66F : 1.0F;
		}
		if (!s.variasiTinggi) {
			return sosok.withPostur(Sosok.POSTUR_MAKS / 2).skala();
		}
		return sosok.skala();
	}

	private SosokConfig() {
	}
}
