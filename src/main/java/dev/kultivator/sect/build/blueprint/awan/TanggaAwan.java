package dev.kultivator.sect.build.blueprint.awan;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Rank;
import dev.kultivator.sect.build.blueprint.RoofBuilder;
import dev.kultivator.sect.build.blueprint.Sink;
import dev.kultivator.sect.build.blueprint.Stamp;
import dev.kultivator.sect.build.blueprint.TerrainView;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * The Cloud Stair (Tangga Awan, DESIGN 37.4.6, 37.5.2): a helix of quartz slabs, 2 wide, 24 steps a turn, half a block a
 * step (12 blocks a turn), from a stone landing on the real ground straight up under the island, through its shell into
 * the hollow belly and out through the plateau under the Gerbang Akar, a small pyramid-roofed gate over the stairwell.
 * Pale-oak rails on both sides, a quartz post on the axis, a hanging soul lantern under one step of every turn, and a
 * flat landing turn (Pelataran Rehat, benches, anchor {@code tangga_rehat_<n>}) every 24 blocks of climb. No step is held
 * up by anything: slabs float, as stairs of Minecraft may (and none of them falls).
 * <p>
 * The foot: a stone-brick landing over the real ground (fitted zone {@code kaki}, footed down to the ground), two guardian
 * lions and the plaque "雲梯 · Tangga Awan"; anchors {@code tangga_kaki} (where one sets foot), {@code arrival_kaki} (where
 * candidates appear on the Day of Acceptance), {@code feri_bawah} (the ground stop of the cloud ferry). The top:
 * {@code tangga_puncak} at the exit on the plateau. Where the helix passes through the island it takes its own cells out
 * (the light swap).
 * <p>
 * Sumber: the ladder to heaven of Kunlun ("Eight Pillars" as the stair between earth and heaven, <i>Huainanzi</i>); the
 * thousand-step stairs of mountain sects are art direction.
 */
public final class TanggaAwan implements Part {

	public static final int STEPS_PER_TURN = 24;
	/** Rising half steps between two landing turns (24 blocks of climb). */
	public static final int LANDING_EVERY = 48;

	@Override
	public String kind() {
		return "cloud_stair";
	}

	private static int inner(AwanLayout layout) {
		return layout.kelas == 0 ? 2 : 3;
	}

	private static int outer(AwanLayout layout) {
		return layout.kelas == 0 ? 3 : 4;
	}

	/** The half size of the foot square (and the kaki zone). */
	public static int footHalf(AwanLayout layout) {
		return outer(layout) + 1;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		AwanLayout layout = AwanLayout.of(ctx.template());
		int h = footHalf(layout) + 2;
		int foot = ctx.level(Lv.zone("kaki"));
		return new BoundingBox(layout.stairX - h, foot - 2, layout.stairZ - h, layout.stairX + h, 6, layout.stairZ + h);
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		AwanLayout layout = AwanLayout.of(ctx.template());
		int sx = layout.stairX;
		int sz = layout.stairZ;
		int rin = inner(layout);
		int rout = outer(layout);
		int half = footHalf(layout);
		int y0 = ctx.level(Lv.zone("kaki"));
		// The cells of each 15° sector: the step annulus, the outer and inner rails.
		List<List<int[]>> steps = new ArrayList<>();
		List<List<int[]>> outerRail = new ArrayList<>();
		List<List<int[]>> innerRail = new ArrayList<>();
		for (int a = 0; a < STEPS_PER_TURN; a++) {
			steps.add(new ArrayList<>());
			outerRail.add(new ArrayList<>());
			innerRail.add(new ArrayList<>());
		}
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				double rho = Math.sqrt(dx * dx + dz * dz);
				double theta = Math.atan2(dx, dz);
				if (theta < 0) {
					theta += Math.PI * 2.0D;
				}
				int a = Math.min(STEPS_PER_TURN - 1, (int) Math.floor(theta / (Math.PI * 2.0D) * STEPS_PER_TURN));
				if (rho >= rin - 0.5D && rho <= rout + 0.5D) {
					steps.get(a).add(new int[]{dx, dz});
				} else if (rho > rout + 0.5D && rho <= rout + 1.5D) {
					outerRail.get(a).add(new int[]{dx, dz});
				} else if (rho >= rin - 1.5D && rho < rin - 0.5D && rho > 0.5D) {
					innerRail.get(a).add(new int[]{dx, dz});
				}
			}
		}
		TerrainView real = ctx.terrain() instanceof AwanTerrain at ? at.real() : ctx.terrain();
		Phase previous = sink.phase();

		// ---- the foot: a stone-brick landing footed down to the real ground
		sink.phase(Phase.PLINTH);
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				int x = sx + dx;
				int z = sz + dz;
				boolean edge = Math.abs(dx) == half || Math.abs(dz) == half;
				sink.set(x, y0 - 1, z, Palette.st(edge ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS));
				int ground = real.known(x, z) ? real.ground(x, z) : y0 - 2;
				for (int fy = Math.max(ground + 1, y0 - 7); fy < y0 - 1; fy++) {
					sink.set(x, fy, z, Palette.st(Blocks.STONE));
				}
			}
		}
		sink.phase(Phase.CLEAR);
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				int x = sx + dx;
				int z = sz + dz;
				int top = real.known(x, z) ? real.top(x, z) : y0 - 1;
				for (int cy = y0; cy <= Math.min(top, y0 + 4); cy++) {
					sink.air(x, cy, z);
				}
			}
		}
		sink.phase(Phase.ORNAMENT);
		sink.set(sx + half - 1, y0, sz + half, Palette.st(Blocks.CHISELED_STONE_BRICKS));
		sink.set(sx - half + 1, y0, sz + half, Palette.st(Blocks.CHISELED_STONE_BRICKS));
		sink.phase(Phase.PLINTH);
		Stamp.lion(sx - half + 1, sz + half - 1, Direction.SOUTH, Lv.at(y0)).emit(ctx, sink);
		Stamp.lion(sx + half - 1, sz + half - 1, Direction.SOUTH, Lv.at(y0)).emit(ctx, sink);
		// The plaque on a stone stand facing the arrivals.
		sink.phase(Phase.FRAME);
		sink.set(sx, y0, sz + half, Palette.st(Blocks.POLISHED_ANDESITE));
		sink.set(sx, y0 + 1, sz + half, Palette.st(Blocks.CHISELED_STONE_BRICKS));
		sink.phase(Phase.ORNAMENT);
		sink.set(sx, y0 + 1, sz + half + 1, Palette.facing(Blocks.BAMBOO_WALL_SIGN, Direction.SOUTH));

		// ---- the helix
		int climb = -y0;
		int rising = Math.max(0, climb * 2);
		int landings = Math.max(0, (rising - 1) / LANDING_EVERY);
		int total = rising + landings * STEPS_PER_TURN;
		int k = 0;
		int step = 0;
		int landingIndex = 0;
		int sinceLanding = 0;
		while (step < total) {
			boolean landing = sinceLanding >= LANDING_EVERY && landingIndex < landings;
			int count = landing ? STEPS_PER_TURN : 1;
			for (int c = 0; c < count && step < total; c++) {
				if (!landing) {
					k++;
					sinceLanding++;
				}
				int a = step % STEPS_PER_TURN;
				int surface = 2 * y0 + k;
				boolean halfStep = (surface & 1) != 0;
				int slabY = halfStep ? (surface - 1) / 2 : surface / 2 - 1;
				int feet = Math.floorDiv(surface, 2);
				// Carve whatever the island has in the way: the step, two blocks of head room, the rails.
				carve(layout, sink, sx, sz, steps.get(a), slabY, feet + 2);
				carve(layout, sink, sx, sz, outerRail.get(a), feet, feet + 1);
				carve(layout, sink, sx, sz, innerRail.get(a), feet, feet + 1);
				sink.phase(Phase.PLINTH);
				for (int[] cell : steps.get(a)) {
					BlockState slab = landing ? Palette.st(AwanPalette.AWAN_LANTAI) : Palette.slab(AwanPalette.AWAN_ANAK_TANGGA, !halfStep);
					sink.set(sx + cell[0], slabY, sz + cell[1], slab);
				}
				sink.phase(Phase.WALL);
				for (int[] cell : outerRail.get(a)) {
					if (step >= total - 2) {
						// The last steps open onto the plateau.
						break;
					}
					boolean bench = landing && a % 6 == 3;
					sink.set(sx + cell[0], feet, sz + cell[1], bench ? Palette.st(Blocks.QUARTZ_PILLAR) : Palette.st(AwanPalette.PAGAR_KAYU));
				}
				for (int[] cell : innerRail.get(a)) {
					sink.set(sx + cell[0], feet, sz + cell[1], Palette.st(AwanPalette.PAGAR_KAYU));
				}
				// The axis post.
				sink.phase(Phase.FRAME);
				sink.set(sx, slabY, sz, Palette.st(Blocks.QUARTZ_PILLAR));
				sink.set(sx, slabY + 1, sz, Palette.st(Blocks.QUARTZ_PILLAR));
				// One hanging lantern a turn, under a bottom slab (its underside carries it).
				if (!landing && halfStep && a == 12 && !steps.get(a).isEmpty()) {
					int[] cell = steps.get(a).get(steps.get(a).size() - 1);
					sink.phase(Phase.ORNAMENT);
					sink.setIfAbsent(sx + cell[0], slabY - 1, sz + cell[1], Palette.lantern(Blocks.SOUL_LANTERN, true));
				}
				if (landing && c == 0) {
					sink.anchor("tangga_rehat_" + landingIndex, sx, feet, sz + rout);
				}
				step++;
			}
			if (landing) {
				landingIndex++;
				sinceLanding = 0;
			}
		}

		// ---- the top: the opening's rail and the Gerbang Akar
		int ring = rout + 2;
		int exitA = (Math.max(0, step - 1)) % STEPS_PER_TURN;
		double exitTheta = (exitA + 0.5D) / STEPS_PER_TURN * Math.PI * 2.0D;
		int ex = (int) Math.round(Math.sin(exitTheta) * (rout + 1.5D));
		int ez = (int) Math.round(Math.cos(exitTheta) * (rout + 1.5D));
		sink.phase(Phase.WALL);
		for (int dx = -ring; dx <= ring; dx++) {
			for (int dz = -ring; dz <= ring; dz++) {
				double rho = Math.sqrt(dx * dx + dz * dz);
				if (rho <= rout + 1.5D || rho > rout + 2.5D) {
					continue;
				}
				if (Math.abs(dx - ex) <= 1 && Math.abs(dz - ez) <= 1) {
					continue;
				}
				sink.set(sx + dx, 0, sz + dz, Palette.st(AwanPalette.PAGAR_KAYU));
			}
		}
		// The axis post's lantern.
		sink.phase(Phase.ORNAMENT);
		sink.set(sx, 1, sz, Palette.lantern(Palette.jade(Blocks.COPPER_LANTERN), false));
		// Four posts and a pyramid roof over the stairwell.
		int post = rout + 1;
		sink.phase(Phase.FRAME);
		for (int[] c : new int[][]{{-post, -post}, {post, -post}, {-post, post}, {post, post}}) {
			sink.set(sx + c[0], 0, sz + c[1], Palette.st(Palette.PILLAR_BASE));
			for (int y = 1; y <= 4; y++) {
				sink.set(sx + c[0], y, sz + c[1], Palette.st(Palette.PILLAR));
			}
		}
		sink.push(sx, 0, sz, 0);
		new RoofBuilder(RoofBuilder.Type.PYRAMID, -post - 1, post + 1, -post - 1, post + 1, 5, ctx.roof(Rank.B)).overhang(1)
				.wall(-post, post, -post, post).emit(sink);
		sink.pop();
		sink.phase(previous);

		// ---- anchors
		sink.anchor("tangga_kaki", sx, y0, sz + half + 1);
		sink.anchor("arrival_kaki", sx, y0, sz + half + 2);
		sink.anchor("feri_bawah", sx, y0, sz + half + 7);
		sink.anchor("tangga_puncak", sx + ex + Integer.signum(ex), 0, sz + ez + Integer.signum(ez));
	}

	/** Air (the light swap) over the island cells in the way, from y {@code from} to {@code to}. */
	private static void carve(AwanLayout layout, Sink sink, int sx, int sz, List<int[]> cells, int from, int to) {
		Phase previous = sink.phase();
		sink.phase(Phase.FRAME);
		for (int[] cell : cells) {
			int x = sx + cell[0];
			int z = sz + cell[1];
			if (!PulauAwan.exists(layout, layout.main, x, z, 3)) {
				continue;
			}
			int top = PulauAwan.levelOf(layout, layout.main, x, z) - 1;
			int bottom = PulauAwan.bottomOf(layout.main, x, z, top);
			for (int y = Math.max(from, bottom); y <= Math.min(to, top); y++) {
				sink.demolish(x, y, z);
			}
		}
		sink.phase(previous);
	}
}
