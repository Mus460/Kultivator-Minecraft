package dev.kultivator.formasi.own;

import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Grade;

/**
 * Registered-core limits of DESIGN 24.2.4. A core over a limit is {@code SUSPENDED} with its reason, never deleted;
 * registration itself is refused when it would go over. Virtual test cores never count.
 */
public final class Limits {

	/** Each family other than killing, transport and the grand arrays: cores per owner. */
	public static final int PER_FAMILY = 2;
	/** Each Abadi array (A1, A2): one per owner. */
	public static final int ABADI_EACH = 1;
	/** Ancient Transportation (L5) nodes per owner. */
	public static final int L5_NODES = 4;
	/** The Ancient Transportation spec id. */
	public static final String L5_SPEC = "ancient_transfer";
	/** L5 nodes that may stand outside the territory as outposts (DESIGN 24.4.10), counted inside {@link #L5_NODES}. */
	public static final int L5_OUTPOSTS = 2;
	/** An outpost stands within this many blocks (horizontal) of the stele, in the hall's dimension. */
	public static final int L5_OUTPOST_RANGE = 4096;
	/** Personal cores (outside every territory) per player. */
	public static final int PERSONAL_MAX = 2;
	/** Personal cores count as this hall level for gates and radii. */
	public static final int PERSONAL_HALL_LEVEL = 3;

	/** Registered sect cores per owner, transport nodes excluded: {@code 2 + L}. */
	public static int registered(int hallLevel) {
		return 2 + Math.max(1, hallLevel);
	}

	/** Killing (PEMBUNUH) cores per owner: {@code 1 + floor(L / 3)}. */
	public static int killing(int hallLevel) {
		return 1 + Math.max(0, hallLevel) / 3;
	}

	/** Sect Transfer (M3) nodes per owner: {@code 2 + floor(L / 2)}. */
	public static int m3Nodes(int hallLevel) {
		return 2 + Math.max(0, hallLevel) / 2;
	}

	/** True for a transport node spec (not counted in {@link #registered}). */
	public static boolean transport(FormationSpec spec) {
		return spec.family() == Family.PERPINDAHAN;
	}

	/** The per-group limit key of a spec: family, or the spec itself for transport and grand arrays. */
	public static String group(FormationSpec spec) {
		if (spec.family() == Family.PERPINDAHAN || spec.grade() == Grade.ABADI) {
			return "spec:" + spec.id();
		}
		return "family:" + spec.family().id();
	}

	/** The maximum of a group (see {@link #group}). */
	public static int groupMax(FormationSpec spec, int hallLevel) {
		if (spec.family() == Family.PERPINDAHAN) {
			return spec.grade() == Grade.LANGIT ? L5_NODES : m3Nodes(hallLevel);
		}
		if (spec.grade() == Grade.ABADI) {
			return ABADI_EACH;
		}
		if (spec.family() == Family.PEMBUNUH) {
			return killing(hallLevel);
		}
		return PER_FAMILY;
	}

	private Limits() {
	}
}
