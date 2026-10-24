package dev.kultivator.dunia.tempat;

import dev.kultivator.dunia.DuniaConfig;
import net.minecraft.util.Mth;

/**
 * Section {@code tempat} of {@code config/kultivator-dunia.json} (DESIGN 38.D.9, owner 38W), clamped on load. The other
 * 38 sections ({@code kotaBesar}, {@code ekonomi38}, {@code kampung}, {@code kompleksSekte}, {@code gua},
 * {@code perbaikan}, {@code cerita}, {@code tempat.hidup}) belong to their modules; {@code jalan} is {@link JalanConfig}.
 */
public final class TempatConfig {

	/** Master switch of DESIGN 38 behaviour (registry entries stay registered either way, 38.0 rule 17). */
	public boolean aktif = true;
	/** All 38 NPC bodies at once, world-wide (38.D.7). */
	public int tubuhGlobal = 64;
	/** 38 NPC bodies within 96 blocks of one player. */
	public int perPemain = 24;
	/** Shows life points as client ghost markers to operators ({@code uji tempat cek}). */
	public boolean debugTitik = false;

	TempatConfig clamp() {
		tubuhGlobal = Mth.clamp(tubuhGlobal, 16, 128);
		perPemain = Mth.clamp(perPemain, 6, 48);
		return this;
	}

	private static volatile TempatConfig instance;

	/** The live section (the same object is refreshed in place by {@code uji jelajah muat}, so it is cached). */
	public static TempatConfig get() {
		TempatConfig c = instance;
		if (c == null) {
			c = DuniaConfig.section("tempat", TempatConfig.class, TempatConfig::new, TempatConfig::clamp);
			instance = c;
		}
		return c;
	}

	/** Section {@code jalan} (38.D.9): road network world generation, road-side posts, highway bandits. */
	public static final class JalanConfig {

		/** Registers the {@code kultivator:dunia_jalan} pack (roads, bridges, posts in new chunks). */
		public boolean worldgen = true;
		/** Road-side buildings (milestones, shrines, rest posts, caravanserais, bandit camps). */
		public boolean kelahiranPos = true;
		/** Bandit bands at {@code pos/kemah_begal} (through the shared daily enemy pressure, {@code Sibuk}). */
		public boolean begal = true;

		JalanConfig clamp() {
			return this;
		}

		private static volatile JalanConfig instance;

		public static JalanConfig get() {
			JalanConfig c = instance;
			if (c == null) {
				c = DuniaConfig.section("jalan", JalanConfig.class, JalanConfig::new, JalanConfig::clamp);
				instance = c;
			}
			return c;
		}
	}
}
