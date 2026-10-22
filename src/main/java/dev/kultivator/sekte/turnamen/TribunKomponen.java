package dev.kultivator.sekte.turnamen;

import dev.kultivator.sect.build.blueprint.Component;
import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import dev.kultivator.sect.build.blueprint.Stamp;
import dev.kultivator.sect.build.blueprint.TemplateExtensions;
import dev.kultivator.sect.build.blueprint.TemplateSpec;
import dev.kultivator.sekte.SekteIds;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * The tribune of the lei tai (DESIGN 36.2.3 "Bangunan", contract C36.T4): component {@code sx_turnamen_tribun} of
 * templates 6-9, tier 7 (with the arena), added through {@link TemplateExtensions} and built by the sect build engine from
 * deposited materials like every other component (a site already past tier 7 builds it first as "Tambahan").
 * <p>
 * Stone steps rise away from the stage on the sides that are free in each template (checked against a dry run of every
 * other component, DESIGN 36.3.5 plot "at most 3 blocks outside the arena square"): the front row one step high in
 * polished andesite, the second in stone brick, the back row three high with mangrove-wood seats for the elders, each on a
 * stone-brick footing. Lantern poles stand at the row ends (anchors {@code panji_arena_<n>}, where module S may hang its
 * banner cloth), drums ({@code Stamp.drum}) where the arena has none. Anchors: {@code turnamen_pusat} (the stage centre,
 * standing level) and {@code kursi_<n>} (the standing cell on every step, the front rows first, at most 48). The stage's
 * own stair and its approach stay free.
 * <p>
 * Sumber: lei tai (擂台), the raised open-sided platform of Chinese martial contests, ringed by onlookers; the sect
 * tournament sources of {@code sect.event.Tournament}.
 */
public final class TribunKomponen {

	public static final String ID = SekteIds.componentId(SekteIds.T, "tribun");
	public static final int TIER = 7;
	public static final int STAGE_HEIGHT = 2;

	/** One row of steps: the cells (x0..x1, z0..z1), its height 1-3 and the side its backrest faces (away from the stage). */
	private record Row(int x0, int z0, int x1, int z1, int height, Direction back) {
	}

	/** One template's tribune: the arena square, the stage level, its rows, lantern poles and drums. */
	private record Plan(int ax0, int az0, int ax1, int az1, Lv level, List<Row> rows, int[][] poles, int[][] drums) {
	}

	private static Plan plan(int template) {
		return switch (template) {
			// T6: the leitai sits between the herb garden, the beast garden and the watch tower; one step on each short side.
			case 6 -> new Plan(-36, 9, -24, 19, Lv.PLAZA, List.of(
					new Row(-37, 9, -37, 19, 1, Direction.WEST),
					new Row(-23, 9, -23, 11, 1, Direction.EAST)),
					new int[][]{{-37, 7}, {-23, 7}}, new int[][]{{-37, 8}, {-23, 8}});
			// T7: one step on the north edge of the outer court, three on the south side (the stage stair at x -34 stays free),
			// one on the west.
			case 7 -> new Plan(-42, 20, -27, 27, Lv.zone("court"), List.of(
					new Row(-41, 19, -33, 19, 1, Direction.NORTH),
					new Row(-42, 28, -36, 28, 1, Direction.SOUTH),
					new Row(-32, 28, -30, 28, 1, Direction.SOUTH),
					new Row(-42, 29, -36, 29, 2, Direction.SOUTH),
					new Row(-32, 29, -30, 29, 2, Direction.SOUTH),
					new Row(-42, 30, -36, 30, 3, Direction.SOUTH),
					new Row(-32, 30, -30, 30, 3, Direction.SOUTH),
					new Row(-43, 21, -43, 26, 1, Direction.WEST)),
					new int[][]{{-43, 20}, {-43, 27}}, new int[][]{{-35, 30}, {-33, 30}});
			// T8: three steps along the whole east side, three on the south-west (the stair at x -49 stays free).
			case 8 -> new Plan(-56, -22, -42, -11, Lv.PLAZA, List.of(
					new Row(-41, -21, -41, -12, 1, Direction.EAST),
					new Row(-40, -21, -40, -12, 2, Direction.EAST),
					new Row(-39, -21, -39, -12, 3, Direction.EAST),
					new Row(-56, -10, -51, -10, 1, Direction.SOUTH),
					new Row(-56, -9, -51, -9, 2, Direction.SOUTH),
					new Row(-56, -8, -51, -8, 3, Direction.SOUTH)),
					new int[][]{{-39, -22}, {-39, -11}, {-57, -8}}, new int[][]{});
			// T9: three steps along the west side between the drums, two on the east up to the mountain gate.
			case 9 -> new Plan(-44, 24, -28, 38, Lv.zone("court"), List.of(
					new Row(-45, 25, -45, 37, 1, Direction.WEST),
					new Row(-46, 25, -46, 37, 2, Direction.WEST),
					new Row(-47, 25, -47, 37, 3, Direction.WEST),
					new Row(-27, 25, -27, 36, 1, Direction.EAST),
					new Row(-26, 25, -26, 36, 2, Direction.EAST)),
					new int[][]{{-47, 24}, {-47, 38}, {-27, 24}}, new int[][]{});
			default -> null;
		};
	}

	/** The tribune part: steps, footing, seats and anchors (one part so its anchors are numbered in one place). */
	private record Steps(Plan plan) implements Part {

		@Override
		public BoundingBox bounds(Ctx ctx) {
			int y = ctx.level(plan.level());
			int minX = Integer.MAX_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (Row row : plan.rows()) {
				minX = Math.min(minX, Math.min(row.x0(), row.x1()));
				maxX = Math.max(maxX, Math.max(row.x0(), row.x1()));
				minZ = Math.min(minZ, Math.min(row.z0(), row.z1()));
				maxZ = Math.max(maxZ, Math.max(row.z0(), row.z1()));
			}
			return new BoundingBox(minX, y - 1, minZ, maxX, y + 3, maxZ);
		}

		@Override
		public String kind() {
			return "tribune";
		}

		@Override
		public void emit(Ctx ctx, Sink sink) {
			Phase previous = sink.phase();
			int y = ctx.level(plan.level());
			int seat = 0;
			// The front rows first, so the seats nearest the stage get the lowest numbers.
			List<Row> rows = new ArrayList<>(plan.rows());
			rows.sort((a, b) -> Integer.compare(a.height(), b.height()));
			for (Row row : rows) {
				for (int x = Math.min(row.x0(), row.x1()); x <= Math.max(row.x0(), row.x1()); x++) {
					for (int z = Math.min(row.z0(), row.z1()); z <= Math.max(row.z0(), row.z1()); z++) {
						sink.phase(Phase.PLINTH);
						sink.set(x, y - 1, z, Palette.st(Palette.STONE));
						for (int i = 0; i < row.height() - 1; i++) {
							boolean course = (x + z + i) % 5 == 0;
							sink.set(x, y + i, z, Palette.st(course ? Palette.STONE_MOSSY : Palette.STONE));
						}
						Block stairs = switch (row.height()) {
							case 1 -> Blocks.POLISHED_ANDESITE_STAIRS;
							case 2 -> Palette.STONE_STAIRS;
							default -> Palette.WOOD_STAIRS;
						};
						sink.set(x, y + row.height() - 1, z, Palette.stair(stairs, row.back(), false));
						if (seat < ArenaTempat.MAX_SEATS) {
							sink.anchor(ArenaTempat.ANCHOR_SEAT + seat++, x, y + row.height(), z);
						}
					}
				}
			}
			// The stage centre (standing level on the stage), where the Lingkar Lei Tai is drawn.
			sink.anchor(ArenaTempat.ANCHOR_CENTRE, (plan.ax0() + plan.ax1()) / 2, y + STAGE_HEIGHT, (plan.az0() + plan.az1()) / 2);
			// Lantern poles at the row ends; the cloth anchor sits one below the lantern.
			sink.phase(Phase.ORNAMENT);
			int flag = 0;
			for (int[] pole : plan.poles()) {
				for (int i = 0; i < 5; i++) {
					sink.set(pole[0], y + i, pole[1], Palette.st(Palette.FENCE));
				}
				sink.set(pole[0], y + 5, pole[1], Palette.lantern(Blocks.LANTERN, false));
				sink.anchor(ArenaTempat.ANCHOR_FLAG + flag++, pole[0], y + 4, pole[1]);
			}
			sink.phase(previous);
		}
	}

	private static boolean registered;

	/** From {@link Turnamen#init} (before the first {@code Templates.get}); also the harness of {@code uji sekte audit}. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		for (int template = 6; template <= 9; template++) {
			int t = template;
			TemplateExtensions.register("sekte:" + SekteIds.T, t, builder -> add(builder, t));
		}
	}

	private static void add(TemplateSpec.Builder builder, int template) {
		Plan plan = plan(template);
		if (plan == null || !TurnamenConfig.get().tribun || builder.hasComponent(ID)) {
			return;
		}
		Component.Builder component = Component.of(ID, TIER).kind("tribune").name(ID).group(Component.Group.ORNAMENT, 900)
				.add(new Steps(plan));
		for (int[] drum : plan.drums()) {
			component.add(Stamp.drum(drum[0], drum[1], plan.level()));
		}
		builder.add(component);
	}

	private TribunKomponen() {
	}
}
