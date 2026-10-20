package dev.kultivator.client.dampak.world;

import dev.kultivator.client.epic.EpicBudget;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gunung Terangkat (DESIGN 28.7): the land is cut by a light grid into cells that tear out and rise 12-30 b into the sky
 * over 40-60 ticks (easeOutCubic) while turning 2-5°/t about a seeded tilted axis, hang 10 ticks, then crumble. IMPLODE
 * cells spiral into a core instead; the Taiji palette flips its cells 180° (yin-yang) and lets them fall back as dust.
 * Real cells group the positions of a WorldBreaker job's batches (2 × 2 columns at Normal and Maximal, 3 × 3 at Minimal or
 * above the cap; one cell per group per job); visual cells (Mode Kehancuran off) sample the client's surface (one
 * heightmap read per cell) and rise from 0.1 b above the ground while the ground stays, a glowing crack decal under each.
 * The grid shows on the ground 10-20 ticks before the cells lift.
 */
final class TerrainLift {

	static final int LIFT = 0;
	static final int IMPLODE = 1;
	static final int FLIP = 2;

	private static final int HANG = 10;
	private static final int CRUMBLE = 6;
	private static final int GRID_AFTER = 24;
	private static final int JOB_MEMORY = 600;

	private record Square(double x0, double z0, float size, float y, int rgb, double born, int life) {
	}

	private static final List<Square> GRID = new ArrayList<>();
	/** Cells already lifted per job id (a deeper batch of the same columns lifts nothing new). */
	private static final Map<Integer, Set<Long>> JOB_CELLS = new HashMap<>();
	private static final Map<Integer, Double> JOB_SEEN = new HashMap<>();

	// ================================================================== real cells (Mode Kehancuran on)

	/** Groups a batch's carved positions into cells and lifts the new ones. */
	static void fromBatch(ClientLevel level, int job, BlockPos[] positions, BlockState[] states, int mode, @Nullable Vec3 core, long cast, boolean own,
						  boolean displays, int gridRgb) {
		int index = DampakWorldBudget.index();
		int cap = DampakWorldBudget.LIFT_CELLS[index];
		boolean particlesOnly = !displays || cap <= 0;
		int cellsAlive = DampakDisplays.count(DampakDisplays.CELL);
		int cs = index == 0 || cellsAlive >= cap ? 3 : 2;
		Set<Long> done = JOB_CELLS.computeIfAbsent(job, key -> new HashSet<>());
		JOB_SEEN.put(job, DampakWorld.clock(0.0F));
		Map<Long, Group> groups = new HashMap<>();
		for (int i = 0; i < positions.length; i++) {
			BlockPos pos = positions[i];
			long key = cellKey(Math.floorDiv(pos.getX(), cs), Math.floorDiv(pos.getZ(), cs));
			if (done.contains(key)) {
				continue;
			}
			groups.computeIfAbsent(key, k -> new Group(Math.floorDiv(pos.getX(), cs) * cs, Math.floorDiv(pos.getZ(), cs) * cs)).add(level, pos, states[i]);
		}
		RandomSource random = DampakParticles.random();
		for (Map.Entry<Long, Group> entry : groups.entrySet()) {
			Group group = entry.getValue();
			BlockState state = group.common();
			if (state == null) {
				continue;
			}
			done.add(entry.getKey());
			double cx = group.x0 + cs * 0.5D;
			double cz = group.z0 + cs * 0.5D;
			float h = Math.min(2.0F, Math.max(1.0F, group.depth()));
			Vec3 start = new Vec3(cx, group.topY + 1.0D - h * 0.5D, cz);
			if (particlesOnly || DampakDisplays.count(DampakDisplays.CELL) >= Math.max(cap, 1)
					|| !cell(level, start, state, cs, h, mode, core, cast, 0)) {
				DampakParticles.burst(level, DampakParticles.block(state), start, 4, 0.15F);
				continue;
			}
			if (DampakWorldBudget.tier() >= 1 && random.nextInt(2) == 0) {
				square(group.x0, group.z0, cs, group.topY + 1.0D, gridRgb, 6);
			}
		}
	}

	/** Job finished (DONE) or stopped: forget its cells. */
	static void forget(int job) {
		JOB_CELLS.remove(job);
		JOB_SEEN.remove(job);
	}

	private static final class Group {
		final int x0;
		final int z0;
		int topY = Integer.MIN_VALUE;
		int bottomY = Integer.MAX_VALUE;
		final Map<BlockState, Integer> counts = new HashMap<>();

		Group(int x0, int z0) {
			this.x0 = x0;
			this.z0 = z0;
		}

		void add(ClientLevel level, BlockPos pos, BlockState state) {
			topY = Math.max(topY, pos.getY());
			bottomY = Math.min(bottomY, pos.getY());
			if (DampakDisplays.solid(level, state, pos)) {
				counts.merge(state, 1, Integer::sum);
			}
		}

		float depth() {
			return topY >= bottomY ? topY - bottomY + 1 : 1;
		}

		@Nullable BlockState common() {
			BlockState best = null;
			int bestCount = 0;
			for (Map.Entry<BlockState, Integer> entry : counts.entrySet()) {
				if (entry.getValue() > bestCount) {
					best = entry.getKey();
					bestCount = entry.getValue();
				}
			}
			return best;
		}
	}

	// ================================================================== visual cells (Mode Kehancuran off)

	/**
	 * Visual cells over a disc of {@code radius} around {@code centre}, or along a strip {@code lineFrom → centre} when
	 * {@code lineFrom} is given; the grid shows {@code lead} ticks before they lift.
	 */
	static void visual(ClientLevel level, Vec3 centre, float radius, @Nullable Vec3 lineFrom, int mode, @Nullable Vec3 core, long cast,
					   boolean own, boolean displays, int gridRgb, int lead) {
		int index = DampakWorldBudget.index();
		int cap = DampakWorldBudget.scaled(DampakWorldBudget.LIFT_CELLS[index], own, DampakWorldBudget.LIFT_CELLS[index]);
		int cs = index == 0 ? 3 : 2;
		List<double[]> cells = new ArrayList<>();
		if (lineFrom != null && lineFrom.distanceToSqr(centre) > 4.0D) {
			Vec3 dir = centre.subtract(lineFrom);
			double length = Math.min(64.0D, dir.length());
			Vec3 unit = dir.normalize();
			Vec3 side = new Vec3(-unit.z, 0.0D, unit.x);
			Set<Long> seen = new HashSet<>();
			for (double along = 2.0D; along <= length; along += cs) {
				for (int k = -1; k <= 1; k++) {
					double x = lineFrom.x + unit.x * along + side.x * k * cs;
					double z = lineFrom.z + unit.z * along + side.z * k * cs;
					int gx = Mth.floor(x / cs);
					int gz = Mth.floor(z / cs);
					if (seen.add(cellKey(gx, gz))) {
						cells.add(new double[]{gx * cs, gz * cs});
					}
				}
			}
		} else {
			int reach = Math.max(1, Mth.ceil(Math.min(24.0F, radius) / cs));
			int gx0 = Mth.floor(centre.x / cs);
			int gz0 = Mth.floor(centre.z / cs);
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					if ((dx * dx + dz * dz) * cs * cs <= radius * radius) {
						cells.add(new double[]{(gx0 + dx) * cs, (gz0 + dz) * cs});
					}
				}
			}
		}
		RandomSource random = DampakParticles.random();
		// a seeded shuffle, then the first {@code cap} cells lift; every cell gets a grid square
		for (int i = cells.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			double[] t = cells.get(i);
			cells.set(i, cells.get(j));
			cells.set(j, t);
		}
		int gridCap = DampakWorldBudget.GRID_SEGMENTS[index] / 4;
		int made = 0;
		for (int i = 0; i < cells.size(); i++) {
			double x0 = cells.get(i)[0];
			double z0 = cells.get(i)[1];
			double cx = x0 + cs * 0.5D;
			double cz = z0 + cs * 0.5D;
			double ground = DampakDisplays.ground(level, cx, cz, Double.NaN);
			if (Double.isNaN(ground)) {
				continue;
			}
			if (i < gridCap) {
				square(x0, z0, cs, ground, gridRgb, lead + GRID_AFTER);
			} else if (index == 0 && i < 16) {
				gridParticles(x0, z0, cs, ground, gridRgb);
			}
			if (made >= cap || !displays) {
				continue;
			}
			BlockPos pos = BlockPos.containing(cx, ground - 0.5D, cz);
			BlockState state = level.getBlockState(pos);
			if (!DampakDisplays.solid(level, state, pos)) {
				continue;
			}
			float h = 1.0F + random.nextFloat();
			Vec3 start = new Vec3(cx, ground + 0.1D + h * 0.5D, cz);
			if (cell(level, start, state, cs, h, mode, core, cast, lead + random.nextInt(6))) {
				made++;
			}
		}
	}

	private static boolean cell(ClientLevel level, Vec3 start, BlockState state, float cs, float h, int mode, @Nullable Vec3 core, long cast, int delay) {
		float big = Math.max(cs, h);
		Display.BlockDisplay display = DampakDisplays.create(state, start, big, false);
		if (display == null) {
			return false;
		}
		RandomSource random = DampakParticles.random();
		Vector3f axis = new Vector3f(random.nextFloat() - 0.5F, 1.5F, random.nextFloat() - 0.5F).normalize();
		if (mode == FLIP) {
			axis.set(random.nextBoolean() ? 1.0F : 0.0F, 0.0F, 0.0F);
			if (axis.x == 0.0F) {
				axis.z = 1.0F;
			}
		}
		float spin = (2.0F + random.nextFloat() * 3.0F) * (random.nextBoolean() ? 1.0F : -1.0F);
		float rise = mode == FLIP ? 3.0F + random.nextFloat() * 2.0F : 12.0F + random.nextFloat() * 18.0F * DampakWorldBudget.speedScale();
		int riseTicks = mode == FLIP ? 30 : 40 + random.nextInt(21);
		Cell cell = new Cell(display, cast, start, state, cs, h, axis, spin, rise, riseTicks, delay, mode, core);
		cell.pose(cell.rotation(), cs, h, cs, true, 0);
		DampakDisplays.add(cell);
		return true;
	}

	private static final class Cell extends DampakDisplays.Piece {
		private final Vec3 start;
		private final BlockState state;
		private final float cs;
		private final float h;
		private final Vector3f axis;
		private final float spin;
		private final float rise;
		private final int riseTicks;
		private final int delay;
		private final int mode;
		private final @Nullable Vec3 core;
		private final double orbitAngle;
		private final double orbitRadius;
		private float angle;
		private float endAge = -1.0F;

		Cell(Display.BlockDisplay display, long cast, Vec3 start, BlockState state, float cs, float h, Vector3f axis, float spin, float rise,
			 int riseTicks, int delay, int mode, @Nullable Vec3 core) {
			super(display, DampakDisplays.CELL, cast);
			this.start = start;
			this.state = state;
			this.cs = cs;
			this.h = h;
			this.axis = axis;
			this.spin = spin;
			this.rise = rise;
			this.riseTicks = riseTicks;
			this.delay = delay;
			this.mode = mode;
			this.core = core;
			if (core != null) {
				this.orbitAngle = Math.atan2(start.z - core.z, start.x - core.x);
				this.orbitRadius = Math.sqrt((start.x - core.x) * (start.x - core.x) + (start.z - core.z) * (start.z - core.z));
			} else {
				this.orbitAngle = 0.0D;
				this.orbitRadius = 0.0D;
			}
		}

		Quaternionf rotation() {
			return new Quaternionf().rotateAxis((float) Math.toRadians(angle), axis.x, axis.y, axis.z);
		}

		@Override
		void abort() {
			if (endAge < 0.0F) {
				endAge = age;
				pose(rotation(), 0.01F, 0.01F, 0.01F, false, CRUMBLE);
			}
		}

		@Override
		boolean tick(ClientLevel level, float sigma, boolean keyNow, int duration) {
			if (endAge >= 0.0F) {
				return age - endAge < CRUMBLE;
			}
			float t = age - delay;
			if (t < 0.0F) {
				// the grid is showing: the cell trembles in place
				if (keyNow && DampakWorldBudget.index() >= 1) {
					float shake = 0.03F * (float) Math.sin(age * 2.7F);
					key.move(start.add(shake, 0.0D, -shake));
				}
				return true;
			}
			int total = riseTicks + HANG;
			if (t >= total) {
				endAge = age;
				crumble(level, 8);
				DampakParticles.burst(level, DampakParticles.block(state), display.position(), 6, 0.1F);
				if (mode == FLIP) {
					DampakParticles.burst(level, ParticleTypes.WHITE_ASH, display.position(), 6, 0.05F);
				}
				pose(rotation(), 0.01F, 0.01F, 0.01F, false, CRUMBLE);
				return true;
			}
			float p = Math.min(1.0F, t / riseTicks);
			float eased = ShockRing.cubic(p);
			Vec3 at;
			if (mode == IMPLODE && core != null) {
				double a = orbitAngle + eased * Math.PI * 1.5D;
				double r = orbitRadius * (1.0D - eased * 0.9D);
				double y = start.y + (core.y - start.y) * eased;
				at = new Vec3(core.x + Math.cos(a) * r, y, core.z + Math.sin(a) * r);
			} else {
				at = start.add(0.0D, rise * eased, 0.0D);
			}
			key.move(at);
			if (mode == FLIP) {
				angle = 180.0F * eased;
			} else {
				angle += spin * sigma;
			}
			if (keyNow) {
				float scale = mode == IMPLODE ? Math.max(0.25F, 1.0F - eased * 0.75F) : 1.0F;
				float ahead = mode == FLIP ? 180.0F * ShockRing.cubic(Math.min(1.0F, (t + duration) / riseTicks)) : angle + spin * sigma * duration;
				Quaternionf rotation = new Quaternionf().rotateAxis((float) Math.toRadians(ahead), axis.x, axis.y, axis.z);
				pose(rotation, cs * scale, h * scale, cs * scale, false, duration);
			}
			if (p < 0.3F && (int) t % 4 == 0) {
				DampakParticles.one(DampakParticles.block(state), at.x, at.y - h * 0.5D, at.z, 0.0D, -0.05D, 0.0D);
			}
			return true;
		}
	}

	// ================================================================== the light grid

	private static void square(double x0, double z0, float size, double groundY, int rgb, int life) {
		if (GRID.size() >= 128) {
			GRID.remove(0);
		}
		GRID.add(new Square(x0, z0, size, (float) groundY + SigilRenderer.LIFT, rgb, DampakWorld.clock(0.0F), Math.max(10, life)));
		DampakWorld.touchGeometry();
	}

	/** Minimal: glow dots every 2 b along a cell's borders. */
	private static void gridParticles(double x0, double z0, float size, double y, int rgb) {
		DustParticleOptions dust = new DustParticleOptions(rgb & 0xFFFFFF, 0.8F);
		int n = DampakParticles.grant(4 * Math.max(1, Mth.ceil(size / 2.0F)));
		for (int i = 0; i < n; i++) {
			double f = (i / 4) * 2.0D / size;
			double x = switch (i % 4) {
				case 0 -> x0 + size * f;
				case 1 -> x0 + size;
				case 2 -> x0 + size * (1.0D - f);
				default -> x0;
			};
			double z = switch (i % 4) {
				case 0 -> z0;
				case 1 -> z0 + size * f;
				case 2 -> z0 + size;
				default -> z0 + size * (1.0D - f);
			};
			DampakParticles.one(dust, x, y + 0.1D, z, 0.0D, 0.0D, 0.0D);
		}
	}

	static void tick(ClientLevel level, double clock) {
		GRID.removeIf(square -> clock - square.born() > square.life());
		if (!JOB_SEEN.isEmpty()) {
			for (Iterator<Map.Entry<Integer, Double>> it = JOB_SEEN.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<Integer, Double> entry = it.next();
				if (clock - entry.getValue() > JOB_MEMORY) {
					JOB_CELLS.remove(entry.getKey());
					it.remove();
				}
			}
		}
	}

	static void render(SigilRenderer.Frame frame) {
		if (GRID.isEmpty() || DampakWorldBudget.tier() <= 0) {
			return;
		}
		int budget = DampakWorldBudget.GRID_SEGMENTS[DampakWorldBudget.index()];
		for (int i = 0; i < GRID.size() && budget >= 4; i++) {
			Square square = GRID.get(i);
			float age = (float) (frame.clock - square.born());
			float a = Math.min(1.0F, age / 4.0F) * Math.min(1.0F, (square.life() - age) / 8.0F);
			if (a <= 0.02F || frame.dist2(square.x0(), square.y(), square.z0()) > 96.0D * 96.0D) {
				continue;
			}
			if (!EpicBudget.takeQuads(8)) {
				return;
			}
			budget -= 4;
			float x0 = frame.rx(square.x0());
			float z0 = frame.rz(square.z0());
			float x1 = x0 + square.size();
			float z1 = z0 + square.size();
			float y = frame.ry(square.y());
			int core = ARGB.color(Math.round(a * 230.0F), square.rgb());
			int bloom = ARGB.color(Math.round(a * 70.0F), square.rgb());
			edge(frame, x0, y, z0, x1, y, z0, core, bloom);
			edge(frame, x1, y, z0, x1, y, z1, core, bloom);
			edge(frame, x1, y, z1, x0, y, z1, core, bloom);
			edge(frame, x0, y, z1, x0, y, z0, core, bloom);
		}
	}

	private static void edge(SigilRenderer.Frame frame, float ax, float ay, float az, float bx, float by, float bz, int core, int bloom) {
		frame.groundStrip(frame.lightning, ax, ay, az, bx, by, bz, 0.12F, core, core, 0.0F, 1.0F);
		frame.groundStrip(frame.glow, ax, ay + 0.01F, az, bx, by + 0.01F, bz, 0.6F, bloom, bloom, 0.0F, 1.0F);
	}

	static boolean active() {
		return !GRID.isEmpty();
	}

	static int squares() {
		return GRID.size();
	}

	static void clear() {
		GRID.clear();
		JOB_CELLS.clear();
		JOB_SEEN.clear();
	}

	private static long cellKey(int gx, int gz) {
		return (long) gx << 32 | gz & 0xFFFFFFFFL;
	}

	private TerrainLift() {
	}
}
