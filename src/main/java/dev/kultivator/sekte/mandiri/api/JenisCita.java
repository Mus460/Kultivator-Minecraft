package dev.kultivator.sekte.mandiri.api;

import org.jetbrains.annotations.Nullable;

/**
 * The fourteen kinds of long-term goal (Cita-cita, DESIGN 39.3.4). A disciple keeps at most three active goals
 * ({@code extra.mandiri.cita}, priority 1-3) and eight finished ones; every kind has a pure progress function over data
 * that already exists (written by module 39A {@code CitaCita}). Pure data, safe on both sides; never reorder.
 */
public enum JenisCita {
	/** Reach a realm (and layer). Never fails; the target rises. */
	RANAH("ranah", false),
	/** Reach a Dao comprehension level. */
	DAO("dao", true),
	/** Learn N techniques, one spec, or a grade. */
	TEKNIK("teknik", true),
	/** Natal treasure folds or a sword tier. */
	PUSAKA("pusaka", true),
	/** Win a tournament season or the Grand Tournament (38). */
	JUARA("juara", true),
	/** Defeat a named soul or character (honour duel / rival encounter). */
	DENDAM("dendam", true),
	/** A lover or Dao companion tie. */
	JODOH("jodoh", true),
	/** Surpass the master. Never cancelled by the master. */
	GURU("guru", false),
	/** A sect rank. */
	JENJANG("jenjang", true),
	/** Spirit-stone worth in the purse plus donations. */
	KEKAYAAN("kekayaan", true),
	/** Slay a beast of tier X. */
	BINATANG("binatang", true),
	/** Enter a secret realm of tier X. */
	RAHASIA("rahasia", true),
	/** Drive off N raiders or help N villages. */
	MENOLONG("menolong", true),
	/** Craft a pill or talisman of grade X. */
	KARYA("karya", true);

	private final String id;
	private final boolean bisaDibatalkan;

	JenisCita(String id, boolean bisaDibatalkan) {
		this.id = id;
		this.bisaDibatalkan = bisaDibatalkan;
	}

	public String id() {
		return id;
	}

	/** False for RANAH and GURU: the master's [Batalkan] button is hidden for them (39.7.3). */
	public boolean bisaDibatalkan() {
		return bisaDibatalkan;
	}

	/** {@code kultivator.sekte.mandiri.inti.cita.<id>}. */
	public String kunciNama() {
		return "kultivator.sekte.mandiri.inti.cita." + id;
	}

	public static @Nullable JenisCita byId(@Nullable String id) {
		if (id == null) {
			return null;
		}
		for (JenisCita jenis : values()) {
			if (jenis.id.equals(id)) {
				return jenis;
			}
		}
		return null;
	}
}
