package dev.kultivator.alam;

import dev.kultivator.data.Realm;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The four realms of DESIGN 25.3.2 (Sumber per realm in the lang keys {@code kultivator.alam.realm.<id>.source}):
 * <ol>
 * <li>Lembah Ujian Darah dan Api: Qi Refining and Foundation only, 8 min, 1 day (RMJ ch. 155-160);</li>
 * <li>Aula Hampa Langit: Golden Core to Dao Union, 15 min, 1 day (RMJ ch. 1126, 1129);</li>
 * <li>Tanah Dewa Kuno: Deity Transformation and up, 15 min, 1 day (RI ch. 187-199);</li>
 * <li>Makam Langit: Immortal Ascension and up, 6 min per layer, no cooldown (BTTH ch. 1352, 1600).</li>
 * </ol>
 * Times, cooldowns and R ranges are game design.
 */
public final class RealmSpecs {

	public static final String BLOOD_FIRE = "blood_fire_trial";
	public static final String HEAVENVOID = "heavenvoid_hall";
	public static final String ANCIENT_GOD = "ancient_god_land";
	public static final String HEAVENLY_TOMB = "heavenly_tomb";

	public static final long DAY = 24000L;

	public static final RealmSpec BLOOD_FIRE_TRIAL = new RealmSpec(BLOOD_FIRE, 1, Realm.QI_REFINING, Realm.FOUNDATION, 8 * 1200, false, DAY,
			1, 2, 0xE0503A, 1);
	public static final RealmSpec HEAVENVOID_HALL = new RealmSpec(HEAVENVOID, 2, Realm.GOLDEN_CORE, Realm.DAO_UNION, 15 * 1200, false, DAY,
			3, 6, 0x5AA8FF, 1);
	public static final RealmSpec ANCIENT_GOD_LAND = new RealmSpec(ANCIENT_GOD, 3, Realm.DEITY_TRANSFORMATION, null, 15 * 1200, false, DAY,
			5, 8, 0x9FF3FF, 1);
	public static final RealmSpec HEAVENLY_TOMB_SPEC = new RealmSpec(HEAVENLY_TOMB, 4, Realm.IMMORTAL_ASCENSION, null, 6 * 1200, true, 0L,
			7, 8, 0xC89BFF, 1);

	public static final List<RealmSpec> ALL = List.of(BLOOD_FIRE_TRIAL, HEAVENVOID_HALL, ANCIENT_GOD_LAND, HEAVENLY_TOMB_SPEC);

	/** The realm with this id, or null. */
	public static @Nullable RealmSpec byId(@Nullable String id) {
		if (id == null) {
			return null;
		}
		for (RealmSpec spec : ALL) {
			if (spec.id().equals(id)) {
				return spec;
			}
		}
		return null;
	}

	/** The realm ids (commands). */
	public static List<String> ids() {
		return List.of(BLOOD_FIRE, HEAVENVOID, ANCIENT_GOD, HEAVENLY_TOMB);
	}

	private RealmSpecs() {
	}
}
