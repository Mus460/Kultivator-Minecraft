package dev.kultivator.formasi;

import dev.kultivator.formasi.spec.Flag;
import dev.kultivator.formasi.spec.FormationSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * The 6b fit table of DESIGN 24.6.2: a spec fits a Titik Formasi anchor when {@code pattern.radius() ≤ anchor.radius}
 * and its dy span lies within −1 … +5, and it is not {@code NO_6B}. Audited by {@code uji formasi audit}.
 */
public final class SectFormationSlots {

	public static final int MIN_DY = -1;
	public static final int MAX_DY = 5;

	/** Anchor radii of the template table (gate tower 6, Bagua court 6, alchemy hall 8, pavilion 8, nodes 3, altar 11). */
	public static final int[] ANCHOR_RADII = {3, 6, 8, 11};

	public static boolean fits(FormationSpec spec, int anchorRadius) {
		return !spec.has(Flag.NO_6B) && !spec.has(Flag.LEGACY_MIRROR) && spec.pattern().size() > 0 && spec.pattern().radius() <= anchorRadius
				&& spec.pattern().minDy() >= MIN_DY && spec.pattern().maxDy() <= MAX_DY;
	}

	/** Every spec that fits an anchor of {@code anchorRadius}. */
	public static List<FormationSpec> fitting(int anchorRadius) {
		List<FormationSpec> list = new ArrayList<>();
		for (FormationSpec spec : FormationRegistry.all()) {
			if (fits(spec, anchorRadius)) {
				list.add(spec);
			}
		}
		return list;
	}

	private SectFormationSlots() {
	}
}
