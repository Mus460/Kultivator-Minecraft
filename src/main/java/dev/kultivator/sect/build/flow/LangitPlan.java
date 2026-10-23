package dev.kultivator.sect.build.flow;

import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.blueprint.TemplateSpec;
import dev.kultivator.sect.build.blueprint.Templates;
import dev.kultivator.sekte.langit.api.LangitConfig;
import net.minecraft.world.level.Level;

/**
 * The numbers of a sky design (DESIGN 37.3.4): the plaza height band and the throne's shift from the ground stele.
 * <ul>
 * <li>{@code bandMin = max(PLAZA_MIN, highest top under the footprint + below + CLEARANCE)},
 * {@code bandMax = level max y - 2 - above}; both are computed from the level, never hard-coded, so a datapack that
 * changes the Overworld's height is followed.</li>
 * <li>The throne may stand up to {@code offsetMaks} (config, default {@link #OFFSET_MAX}) blocks from the ground stele
 * (each axis), so the owner stays in building range of both sites while tethered.</li>
 * <li>While tethered only tier {@link #TETHER_MAX_TIER} builds; the rest waits for the Ascension.</li>
 * </ul>
 */
public final class LangitPlan {

	public static final int PLAZA_MIN = 208;
	public static final int CLEARANCE = 24;
	public static final int OFFSET_MAX = 64;
	public static final int TETHER_MAX_TIER = 1;
	/** The plaza height steps of the screen. */
	public static final int HEIGHT_STEP = 4;

	/** The lowest plaza level of sky template {@code sky} over terrain whose highest top is {@code topMax} (world y). */
	public static int bandMin(int sky, int topMax) {
		TemplateSpec spec = Templates.get(sky);
		return Math.max(PLAZA_MIN, topMax + spec.below() + CLEARANCE);
	}

	/** The highest plaza level of sky template {@code sky} in {@code level}. */
	public static int bandMax(Level level, int sky) {
		TemplateSpec spec = Templates.get(sky);
		return level.getMaxY() - 2 - spec.above();
	}

	/** The configured default plaza level, clamped to the band. */
	public static int defaultHeight(Level level, int sky) {
		int wanted = LangitConfig.get().tinggiAsal;
		return Math.max(PLAZA_MIN, Math.min(bandMax(level, sky), wanted));
	}

	/** The largest horizontal shift allowed (config {@code offsetMaks}). */
	public static int offsetMax() {
		return Math.max(0, Math.min(96, LangitConfig.get().offsetMaks));
	}

	/** The sky template 11-19 of a sibling 1-9 (or a sky template itself), 0 when invalid. */
	public static int skyOf(int template) {
		if (SectTemplates.isSky(template)) {
			return template;
		}
		return SectTemplates.valid(template) ? template + 10 : 0;
	}

	private LangitPlan() {
	}
}
