package dev.kultivator.dunia.dimensi;

import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaConfig;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * Section {@code dimensi} of {@code config/kultivator-dunia.json} (DESIGN 34.5.13, 34.5.14): the switches and numbers of
 * Dunia Bawah and Laut Tanpa Batas. Missing values get the defaults below, out-of-range values are clamped and the file
 * is rewritten (the spine's rule). The two dimensions always exist (removing a dimension would strand players); the
 * switches close their <em>entries</em> instead.
 */
public final class DimensiConfig {

	/** The POJO Gson reads (public fields with initialisers). */
	public static final class Settings {
		/** The overworld structures of this module (Kuil Gerbang Hantu, Pusaran Laut) in new chunks. */
		public boolean worldgen = true;
		/** Perjalanan Arwah into Dunia Bawah. */
		public boolean underworld = true;
		/** The realm whose soul may leave the body. */
		public String minRealm = "nascent_soul";
		/** Soul time per incense, minutes (5-60). */
		public int incenseMinutes = 20;
		/** Days between two bowls of Meng Po's soup (1-30). */
		public int soupCooldownDays = 7;
		/** Courts to pass before Raja Yama opens the Book (3-10). */
		public int courtsRequired = 10;
		/** The True Immortal's "Coret Namamu" perk. */
		public boolean yamaPerk = true;
		/** Hungry ghosts around one player at most (0-12). */
		public int ghostsPerPlayer = 6;
		/** Laut Tanpa Batas entries (sea gate, ship route). */
		public boolean sea = true;
		/** The realm that may cross into the Boundless Sea. */
		public String seaMinRealm = "golden_core";
		/** Sea monsters around one player at most (0-8). */
		public int monstersPerPlayer = 4;
		/** Pulau Penyu Ao, the drifting turtle island. */
		public boolean turtle = true;
		/** The realm the Dragon King receives. */
		public String palaceMinRealm = "deity_transformation";
		/** The realm the Penglai hermit tests. */
		public String penglaiMinRealm = "dao_union";

		Settings clamp() {
			incenseMinutes = Mth.clamp(incenseMinutes, 5, 60);
			soupCooldownDays = Mth.clamp(soupCooldownDays, 1, 30);
			courtsRequired = Mth.clamp(courtsRequired, 3, 10);
			ghostsPerPlayer = Mth.clamp(ghostsPerPlayer, 0, 12);
			monstersPerPlayer = Mth.clamp(monstersPerPlayer, 0, 8);
			minRealm = realmId(minRealm, Realm.NASCENT_SOUL);
			seaMinRealm = realmId(seaMinRealm, Realm.GOLDEN_CORE);
			palaceMinRealm = realmId(palaceMinRealm, Realm.DEITY_TRANSFORMATION);
			penglaiMinRealm = realmId(penglaiMinRealm, Realm.DAO_UNION);
			return this;
		}
	}

	private static Settings settings = new Settings();

	static void init() {
		settings = DuniaConfig.section("dimensi", Settings.class, Settings::new, Settings::clamp);
	}

	/** The section (never null; the same object after a reload). */
	public static Settings get() {
		return settings;
	}

	/** The realm named {@code id}, or {@code fallback}. */
	public static Realm realm(String id, Realm fallback) {
		Realm realm = id == null ? null : Realm.byId(id.toLowerCase(Locale.ROOT));
		return realm == null ? fallback : realm;
	}

	private static String realmId(String id, Realm fallback) {
		return realm(id, fallback).id();
	}

	private DimensiConfig() {
	}
}
