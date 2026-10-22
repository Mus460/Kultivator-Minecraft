package dev.kultivator.sekte.kepribadian;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.disciple.path.SoulSeal;
import dev.kultivator.sekte.api.HatiService.Keadaan;
import dev.kultivator.sekte.api.Sifat;

/**
 * Kesetiaan, a disciple's loyalty (DESIGN 36.2.1): contentment blended with the existing master bond
 * ({@code DisciplePaths.bond}), plus 15 for a Setia heart, 0-100, and the four states.
 * <p>
 * The bond part is {@code 0.45 * (30 + 0.7 * bond)} instead of a flat {@code 0.45 * bond}: the bond of every new disciple
 * starts at 0, so the flat blend would leave every fresh recruit at 38 ("Gelisah") on its first day. With the offset a
 * new disciple at the starting contentment of 70 is just loyal (52) and still drifts to restless if it is never cared
 * for; a bond of 100 adds the same 45 as the flat blend. A disciple bound by the Soul Seal or named heir never drops
 * below 30: it may be restless, but it never defies and never leaves (rule 36.0.4).
 * <p>
 * States: Setia 50+, Gelisah 30-49, Membangkang from the leaving threshold to 29, Akan Pergi below the threshold
 * ({@code kepribadian.ambangPergi}, 15; 10 for a Setia heart).
 */
public final class Kesetiaan {

	public static final int SETIA_MIN = 50;
	public static final int GELISAH_MIN = 30;
	/** The floor of a soul-sealed disciple and of the heir. */
	public static final int BOUND_FLOOR = 30;

	public static int loyalty(DiscipleData data) {
		double k = Hati.contentmentExact(data);
		int bond = DisciplePaths.bond(data);
		double value = 0.55D * k + 0.45D * (30.0D + 0.7D * bond);
		if (Hati.has(data, Sifat.SETIA)) {
			value += 15.0D * Math.min(1.0D, KepribadianConfig.get().faktorWatak);
		}
		int loyalty = (int) Math.round(Math.max(0.0D, Math.min(100.0D, value)));
		if (bound(data)) {
			loyalty = Math.max(BOUND_FLOOR, loyalty);
		}
		return loyalty;
	}

	/** True for the soul-sealed and the heir: they never defy and never leave. */
	public static boolean bound(DiscipleData data) {
		return SoulSeal.sealed(data) || DisciplePaths.isHeir(data);
	}

	/** The loyalty below which a disciple prepares to leave. */
	public static int threshold(DiscipleData data) {
		int base = KepribadianConfig.get().ambangPergi;
		return Hati.has(data, Sifat.SETIA) ? Math.max(5, base - 5) : base;
	}

	public static Keadaan state(DiscipleData data) {
		return stateOf(loyalty(data), threshold(data));
	}

	public static Keadaan stateOf(int loyalty, int threshold) {
		if (loyalty >= SETIA_MIN) {
			return Keadaan.SETIA;
		}
		if (loyalty >= GELISAH_MIN) {
			return Keadaan.GELISAH;
		}
		return loyalty >= threshold ? Keadaan.MEMBANGKANG : Keadaan.AKAN_PERGI;
	}

	public static boolean defiant(DiscipleData data) {
		Keadaan state = state(data);
		return state == Keadaan.MEMBANGKANG || state == Keadaan.AKAN_PERGI;
	}

	/** Whole days left before the disciple leaves (0 = today), or -1 while it is not preparing to leave. */
	public static int daysLeft(DiscipleData data) {
		if (state(data) != Keadaan.AKAN_PERGI || bound(data)) {
			return -1;
		}
		return Math.max(0, KepribadianConfig.get().hariPergi - Hati.getInt(data, "low", 0));
	}

	private Kesetiaan() {
	}
}
