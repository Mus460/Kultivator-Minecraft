package dev.kultivator.sect.build.blueprint;

import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.blueprint.templates.T1Unranked;
import dev.kultivator.sect.build.blueprint.templates.T2ThirdRate;
import dev.kultivator.sect.build.blueprint.templates.T3SecondRate;
import dev.kultivator.sect.build.blueprint.templates.T4FirstRate;
import dev.kultivator.sect.build.blueprint.templates.T5Great;
import dev.kultivator.sect.build.blueprint.templates.T6HolyLand;
import dev.kultivator.sect.build.blueprint.templates.T7CaveHeaven;
import dev.kultivator.sect.build.blueprint.templates.T8AncientLineage;
import dev.kultivator.sect.build.blueprint.templates.T9HeavenlyCourt;

/**
 * The registry of the nine templates (DESIGN 21.1): {@link #get(int)} builds each {@link TemplateSpec} once (lazily,
 * thread-safe) from its class in {@code blueprint.templates}. A template whose class is still a placeholder has no
 * components ({@link TemplateSpec#isPlaceholder()}); the engine refuses to found it.
 *
 * <h2>How to write a template</h2>
 * <ol>
 * <li><b>Read DESIGN 21.1-21.4 and 21.5-21.8 for your template</b>: footprint, envelope, relief, earthwork cap, disc,
 * territory (Table A/B), the tier table of 21.3 and the site plan or composition of 21.4. Every tier must already look
 * finished (21.2): a component is plot terrain + structure + landscaping, a terrace comes with its first building, paving
 * grows with use, the axis never dead-ends in an empty lot (a screen-wall rock before T6), each tier ends with an
 * ornament component.</li>
 * <li><b>Start the spec</b> with the numbers of Table A/B:
 * <pre>
 * TemplateSpec.Builder b = TemplateSpec.builder(5).footprint(-36, 36, -50, 30).envelope(48, 22).relief(28).earthwork(29565)
 *         .disc(6, 5).territory(58).towerRooms(5).qiTier(2).gateAltars(0)
 *         .zone("plaza", -11, -6, 11, 12, 0)                  // fixed level 0
 *         .zone("east", 20, -49, 35, -18, 2, 1, 4);           // fitted to the terrain within +1..+4
 * </pre>
 * Coordinates are template-local: the stele at (0,0,0), +z toward the gate, +x east, the plaza floor at y = -1. A
 * level {@link Lv} is where people stand ({@code Lv.PLAZA} = 0, {@code Lv.zone("east")}, {@code Lv.zone("east", 2)},
 * {@code Lv.at(5)}). The duty posts (0, ±5) and (±5, 0) stay air automatically: never plan solids there.</li>
 * <li><b>Add components</b>, each with a stable id (never rename one later), a tier, a kind, a name key and a group:
 * <pre>
 * b.add(Component.of("t6_main_hall", 6).kind("hall").name("main_hall").group(Component.Group.MAIN)
 *         .add(new Terrace(-19, -46, 19, -19, Lv.at(2), Lv.PLAZA).style(Terrace.Style.MARBLE).opening(0, -19, 2))
 *         .add(new Hall(0, -33).site(Lv.at(5)).bays(4, 4, 6, 4, 4).depth(11).pillar(6).rank(Rank.D)
 *                 .roof(RoofBuilder.Type.XIESHAN, 2).interior(Interiors.Preset.MAIN_HALL).platform(2)));
 * </pre>
 * Parts later in a component overwrite earlier ones cell by cell; a later component replaces earlier sect blocks only
 * in structural phases (plants and ornaments never overwrite built blocks; earthwork never removes them). Use
 * {@link Marks#demolish} before a light swap (wooden gate → paifang).</li>
 * <li><b>The component library</b> (every class documents its parameters): {@link Hall} (any rank, bays, double eave, two
 * storeys, gate halls via {@link Gates}), {@link RoofBuilder} (all roof forms of 21.6), {@link Pavilion}, {@link Pagoda},
 * {@link Paifang}, {@link Gates} (inner gate, gate hall, gate tower, corner tower), {@link Wall}, {@link Corridor},
 * {@link Terrace}, {@link StairFlight}, {@link TerrainZone}, {@link Paving}, {@link Peak}, {@link Pond} (ponds, basins,
 * streams, moats), {@link Waterfall}, {@link Bridge}, {@link Tree}, {@link BambooGrove}, {@link Rock}, {@link Grounds}
 * (herb beds, drill ground, archery, arena, beast garden, screen wall), {@link SetPieces} (spirit way, stone boat, grotto
 * hill), {@link LanternPost}, {@link FormationDisc}, {@link TowerShaft}, {@link QiSeat}, {@link HangingIsland},
 * {@link Stamp} (char-grid set pieces: lions, dings, vats, drums, bell frames...), {@link Interiors}, {@link Ornament},
 * {@link Marks} (anchors, demolish, petals). Colours and blocks come from {@link Palette}, {@link Rank} and
 * {@link Glaze}.</li>
 * <li><b>Anchors</b> the other systems need (DESIGN 21.9): "post_water", "post_kitchen", "post_garden", "post_farm",
 * "arrival", "arena", "library" (within 16 of the stele), "formation" (within 24), "tower" (entrance within 12, the
 * deepest campfire at most 28 below), "treasury", "main_hall". Many parts set them (Interiors, Grounds, FormationDisc,
 * TowerShaft); add the rest with {@link Marks#anchor}.</li>
 * <li><b>Exact items</b> per tier follow DESIGN 21.11 (cauldrons, bookshelves, lecterns, alchemy furnaces, brewing stands,
 * the disc, the dings, targets, bells, barrels, the Qi seat, end rods, tower rooms): the bill counts what you place.</li>
 * <li><b>Check</b> with {@code /kultivasi uji sekte info <n>} (components, blocks and the bill per tier),
 * {@code uji sekte audit <n>} (ops outside the column, unsupported blocks, uncontained water, stair shapes, unknown blocks,
 * anchors), {@code uji sekte pamer <n> tingkat <t>} on flat land and {@code uji sekte kamera 1-3}.</li>
 * </ol>
 * Every story idea keeps its DESIGN 21 source line; real architecture is art direction, never presented as canon.
 */
public final class Templates {

	private static final TemplateSpec[] CACHE = new TemplateSpec[SectTemplates.COUNT + 1];

	/** The spec of template 1-9 (built once). */
	public static TemplateSpec get(int template) {
		int t = Math.max(1, Math.min(SectTemplates.COUNT, template));
		TemplateSpec spec = CACHE[t];
		if (spec != null) {
			return spec;
		}
		synchronized (CACHE) {
			if (CACHE[t] == null) {
				CACHE[t] = switch (t) {
					case 1 -> T1Unranked.build();
					case 2 -> T2ThirdRate.build();
					case 3 -> T3SecondRate.build();
					case 4 -> T4FirstRate.build();
					case 5 -> T5Great.build();
					case 6 -> T6HolyLand.build();
					case 7 -> T7CaveHeaven.build();
					case 8 -> T8AncientLineage.build();
					default -> T9HeavenlyCourt.build();
				};
			}
			return CACHE[t];
		}
	}

	/** True when template {@code template} has a real plan (not a placeholder awaiting its author). */
	public static boolean ready(int template) {
		return SectTemplates.valid(template) && !get(template).isPlaceholder();
	}

	private Templates() {
	}
}
