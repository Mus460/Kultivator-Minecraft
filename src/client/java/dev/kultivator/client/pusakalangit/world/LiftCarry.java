package dev.kultivator.client.pusakalangit.world;

import dev.kultivator.client.dampak.world.DampakWorld;
import dev.kultivator.client.dampak.world.DampakWorldBudget;
import dev.kultivator.client.dampak.world.SigilRenderer;
import dev.kultivator.client.epic.fx.EpicParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Titah Pemindah Gunung on the land (DESIGN 29.5.4): the light grid that cuts the square into cells (EXTRA1, terrain-
 * following lines sweeping out from the centre), the cells torn out and carried into the sky (EXTRA3: three-layer slabs
 * from {@code TerrainLift}, 12 / 32 at Normal / Maximal, created a few per tick and staggered over the authored 40
 * ticks; Minimal: soil and cloud bursts) and the stepped glowing pit left behind (terraces of darkening depth with violet
 * and gold edges cooling over 30 s). Visual only: the ground never changes here (Mode Kehancuran carves the real
 * STEPPED_PIT on the server, and this glow then lies on it).
 */
final class LiftCarry {

	private static final int PIT_LIFE = 600;
	private static final int REFRESH = 20;

	/** The light grid over the square. */
	private static final class Grid {
		final long cast;
		final Vec3 centre;
		final float half;
		final float cell;
		final int rgb;
		final double born;
		final double until;
		final int lines;
		final int samples;
		/** Heights of every line's samples: [line][sample]; lines 0..n along x (fixed x), n+1.. along z. */
		final float[][] heights;
		int refreshIn;

		Grid(long cast, Vec3 centre, float half, float cell, int rgb, double born, double until, int samples) {
			this.cast = cast;
			this.centre = centre;
			this.half = half;
			this.cell = cell;
			this.rgb = rgb;
			this.born = born;
			this.until = until;
			this.lines = Math.max(1, Math.min(64, (int) Math.floor(half * 2.0F / cell))) + 1;
			this.samples = samples;
			this.heights = new float[lines * 2][samples + 1];
		}

		double x0() {
			return centre.x - half;
		}

		double z0() {
			return centre.z - half;
		}

		float offset(int line) {
			return Math.min(half * 2.0F, line * cell);
		}
	}

	/** A slab waiting to be made (displays are made a few per tick); {@code displays} false = a particle burst at its time. */
	private static final class Tear {
		final long cast;
		final double x0;
		final double z0;
		final float size;
		final double at;
		final Vec3 carry;
		final int rgb;
		boolean displays;

		Tear(long cast, double x0, double z0, float size, double at, Vec3 carry, int rgb, boolean displays) {
			this.cast = cast;
			this.x0 = x0;
			this.z0 = z0;
			this.size = size;
			this.at = at;
			this.carry = carry;
			this.rgb = rgb;
			this.displays = displays;
		}
	}

	/** The stepped pit's glow. */
	private static final class Pit {
		final long cast;
		final Vec3 centre;
		final float half;
		final int steps;
		final int rgb;
		final int rgb2;
		final double born;
		final int n;
		final float[] heights;
		int refreshIn;

		Pit(long cast, Vec3 centre, float half, int steps, int rgb, int rgb2, double born) {
			this.cast = cast;
			this.centre = centre;
			this.half = half;
			this.steps = steps;
			this.rgb = rgb;
			this.rgb2 = rgb2;
			this.born = born;
			this.n = Math.max(4, Math.min(12, (int) Math.ceil(half / 2.0F)));
			this.heights = new float[(n + 1) * (n + 1)];
		}
	}

	private static final List<Grid> GRIDS = new ArrayList<>();
	private static final List<Tear> TEARS = new ArrayList<>();
	private static final List<Pit> PITS = new ArrayList<>();

	// ================================================================== API

	/** EXTRA1: the light grid over the square of half side {@code half}, cells {@code cell} b, until {@code until}. */
	static void grid(long cast, Vec3 centre, float half, float cell, int rgb, double until, boolean own) {
		if (!TitahWorld.finite(centre) || !(half > 1.0F)) {
			return;
		}
		GRIDS.removeIf(g -> g.cast == cast);
		if (GRIDS.size() >= 4) {
			GRIDS.remove(0);
		}
		// samples per line: 16 / 32, fewer when the grid has many lines (it stays within ~700 / 1600 quads)
		int lineCount = Math.max(1, Math.min(64, (int) Math.floor(half * 2.0F / Math.max(2.0F, cell)))) + 1;
		boolean maximal = DampakWorldBudget.tier() >= 2;
		int samples = Math.max(4, Math.min(maximal ? 32 : 16, (maximal ? 1600 : 700) / (lineCount * 4)));
		Grid grid = new Grid(cast, centre, half, Math.max(2.0F, cell), rgb, TitahWorld.clock(), until, samples);
		ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
		if (level != null) {
			refresh(level, grid);
		}
		GRIDS.add(grid);
		TitahWorld.touch();
	}

	/**
	 * EXTRA3: the cells are torn out over {@code spread} ticks and carried off along {@code dir} (each cell to one side of
	 * it, as by the two giants); visual slabs at most {@link DampakWorldBudget#TITAH_LIFT_CELLS} (halved under Mode
	 * Kehancuran, whose real batches lift their own cells).
	 */
	static void tear(ClientLevel level, long cast, Vec3 centre, float half, float cell, Vec3 dir, int spread, int rgb, boolean own, boolean lowLoad,
					 boolean kehancuran, float others) {
		if (!TitahWorld.finite(centre) || !(half > 1.0F)) {
			return;
		}
		float size = Math.max(2.0F, Math.min(16.0F, cell));
		int per = Math.max(1, Math.min(64, (int) Math.floor(half * 2.0F / size)));
		List<double[]> cells = new ArrayList<>(per * per);
		double x0 = centre.x - per * size * 0.5D;
		double z0 = centre.z - per * size * 0.5D;
		for (int i = 0; i < per; i++) {
			for (int j = 0; j < per; j++) {
				cells.add(new double[]{x0 + i * size, z0 + j * size});
			}
		}
		// nearest the centre first (the seal's heart), then outward, with a seeded jitter
		RandomSource random = EpicParticles.random();
		cells.sort((a, b) -> Double.compare(dist2(a, size, centre), dist2(b, size, centre)));
		int index = DampakWorldBudget.index();
		boolean displays = DampakWorldBudget.displays(own, lowLoad) && index >= 1;
		int cap = DampakWorldBudget.scaled(DampakWorldBudget.TITAH_LIFT_CELLS[index], own, DampakWorldBudget.TITAH_LIFT_CELLS[index]);
		if (kehancuran) {
			cap /= 2;
		}
		int bursts = Math.max(4, Math.min(24, Math.round(16 * Math.max(0.25F, others))));
		Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
		double now = TitahWorld.clock();
		int made = 0;
		for (int k = 0; k < cells.size() && (made < cap || k < bursts); k++) {
			double[] c = cells.get(k);
			boolean slab = displays && made < cap;
			// the two giants: cells left of the aim go left-forward, the rest right-forward
			double sx = (c[0] + size * 0.5D - centre.x) * side.x + (c[1] + size * 0.5D - centre.z) * side.z;
			Vec3 carry = dir.add(side.scale(sx < 0.0D ? -0.8D : 0.8D)).normalize();
			double at = now + (cells.size() <= 1 ? 0.0D : random.nextDouble() * Math.max(1, spread));
			TEARS.add(new Tear(cast, c[0], c[1], size, at, carry, rgb, slab));
			if (slab) {
				made++;
			}
		}
		TitahWorld.touch();
	}

	/** The climax: a stepped glowing pit of half side {@code half}, {@code steps} terraces, cooling over 30 s. */
	static void pit(long cast, Vec3 centre, float half, int steps, int rgb, int rgb2, boolean own) {
		if (!TitahWorld.finite(centre) || !(half > 1.0F)) {
			return;
		}
		PITS.removeIf(p -> p.cast == cast);
		if (PITS.size() >= 4) {
			PITS.remove(0);
		}
		Pit pit = new Pit(cast, centre, half, Math.max(3, Math.min(5, steps)), rgb, rgb2, TitahWorld.clock());
		ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
		if (level != null) {
			refresh(level, pit);
		}
		PITS.add(pit);
		TitahWorld.touch();
	}

	static void abort(long cast) {
		GRIDS.removeIf(g -> g.cast == cast);
		TEARS.removeIf(t -> t.cast == cast);
	}

	static int count() {
		return GRIDS.size() + TEARS.size() + PITS.size();
	}

	static void clear() {
		GRIDS.clear();
		TEARS.clear();
		PITS.clear();
	}

	private static double dist2(double[] cell, float size, Vec3 centre) {
		double dx = cell[0] + size * 0.5D - centre.x;
		double dz = cell[1] + size * 0.5D - centre.z;
		return dx * dx + dz * dz;
	}

	// ================================================================== tick

	static void tick(ClientLevel level, double now) {
		GRIDS.removeIf(g -> now > g.until + 20.0D);
		PITS.removeIf(p -> now - p.born > PIT_LIFE);
		for (Grid grid : GRIDS) {
			if (--grid.refreshIn <= 0) {
				refresh(level, grid);
			}
		}
		for (Pit pit : PITS) {
			if (--pit.refreshIn <= 0) {
				refresh(level, pit);
			}
			// the terrace edges breathe sparks for the first 10 s
			if (now - pit.born < 200.0D && ((int) (now - pit.born) & 3) == 0) {
				RandomSource random = EpicParticles.random();
				int n = EpicParticles.grant(DampakWorldBudget.scaled(3, true, 6), false);
				for (int i = 0; i < n; i++) {
					double x = pit.centre.x + (random.nextDouble() * 2.0D - 1.0D) * pit.half;
					double z = pit.centre.z + (random.nextBoolean() ? -1.0D : 1.0D) * pit.half * random.nextDouble();
					double y = DampakWorld.titahGround(level, x, z, pit.centre.y);
					EpicParticles.add(i % 2 == 0 ? ParticleTypes.END_ROD : EpicParticles.dust(pit.rgb, 1.0F), x, y + 0.2D, z, 0.0D, 0.04D, 0.0D);
				}
			}
		}
		if (TEARS.isEmpty()) {
			return;
		}
		int index = DampakWorldBudget.index();
		// three displays per slab: at most a third of the pool's per-tick creations
		int perTick = Math.max(1, DampakWorldBudget.DISPLAYS_PER_TICK[index] / 3);
		int bursts = 6;
		RandomSource random = EpicParticles.random();
		for (Iterator<Tear> it = TEARS.iterator(); it.hasNext(); ) {
			Tear tear = it.next();
			// make the slab early enough to tremble before it lifts (its own delay), never later than its time
			if (tear.displays && perTick > 0 && now >= tear.at - 12.0D) {
				double cx = tear.x0 + tear.size * 0.5D;
				double cz = tear.z0 + tear.size * 0.5D;
				double ground = DampakWorld.titahGround(level, cx, cz, Double.NaN);
				int delay = (int) Math.max(0L, Math.round(tear.at - now));
				boolean made = !Double.isNaN(ground)
						&& DampakWorld.titahSlab(level, tear.x0, tear.z0, tear.size, ground, tear.cast, delay, tear.carry, tear.rgb, true);
				perTick--;
				if (made) {
					it.remove();
					continue;
				}
				// no room or out of range: it bursts at its time instead
				tear.displays = false;
			}
			if (!tear.displays && now >= tear.at && bursts > 0) {
				bursts--;
				it.remove();
				burst(level, tear, random);
			}
		}
	}

	/** A cell torn out without a display: soil and rock bursting up, a puff of cloud. */
	private static void burst(ClientLevel level, Tear tear, RandomSource random) {
		double cx = tear.x0 + tear.size * 0.5D;
		double cz = tear.z0 + tear.size * 0.5D;
		double y = DampakWorld.titahGround(level, cx, cz, Double.NaN);
		if (Double.isNaN(y)) {
			return;
		}
		BlockState state = level.getBlockState(BlockPos.containing(cx, y - 0.5D, cz));
		int n = EpicParticles.grant(Math.min(12, 4 + Math.round(tear.size)), false);
		for (int i = 0; i < n; i++) {
			EpicParticles.add(state.isAir() ? ParticleTypes.CLOUD : new BlockParticleOption(ParticleTypes.BLOCK, state),
					cx + (random.nextDouble() - 0.5D) * tear.size, y + 0.3D, cz + (random.nextDouble() - 0.5D) * tear.size,
					tear.carry.x * 0.1D, 0.4D + random.nextDouble() * 0.4D, tear.carry.z * 0.1D);
		}
		EpicParticles.add(ParticleTypes.CLOUD, cx, y + 1.0D, cz, 0.0D, 0.2D, 0.0D);
	}

	private static void refresh(ClientLevel level, Grid grid) {
		grid.refreshIn = REFRESH;
		float length = grid.half * 2.0F;
		for (int l = 0; l < grid.lines; l++) {
			float off = grid.offset(l);
			for (int s = 0; s <= grid.samples; s++) {
				float along = length * s / grid.samples;
				// along x: fixed x, z runs; along z: fixed z, x runs
				grid.heights[l][s] = (float) (DampakWorld.titahGround(level, grid.x0() + off, grid.z0() + along, grid.centre.y) + SigilRenderer.LIFT);
				grid.heights[grid.lines + l][s] = (float) (DampakWorld.titahGround(level, grid.x0() + along, grid.z0() + off, grid.centre.y) + SigilRenderer.LIFT);
			}
		}
	}

	private static void refresh(ClientLevel level, Pit pit) {
		pit.refreshIn = REFRESH;
		double x0 = pit.centre.x - pit.half;
		double z0 = pit.centre.z - pit.half;
		double cell = pit.half * 2.0D / pit.n;
		for (int i = 0; i <= pit.n; i++) {
			for (int j = 0; j <= pit.n; j++) {
				pit.heights[i * (pit.n + 1) + j] = (float) (DampakWorld.titahGround(level, x0 + i * cell, z0 + j * cell, pit.centre.y) + SigilRenderer.LIFT);
			}
		}
	}

	// ================================================================== render

	static void render(SigilRenderer.Frame frame) {
		if (DampakWorldBudget.tier() < 1) {
			return;
		}
		for (int i = 0; i < PITS.size(); i++) {
			drawPit(frame, PITS.get(i));
		}
		for (int i = 0; i < GRIDS.size(); i++) {
			drawGrid(frame, GRIDS.get(i));
		}
	}

	/** The light grid: every line sweeps out from the centre over 10 ticks, breathes, and fades after its time. */
	private static void drawGrid(SigilRenderer.Frame frame, Grid grid) {
		double range = TitahWorld.viewRange() + grid.half;
		if (frame.dist2(grid.centre.x, grid.centre.y, grid.centre.z) > range * range) {
			return;
		}
		double age = frame.clock - grid.born;
		float sweep = (float) Math.min(1.0D, age / 10.0D);
		float out = frame.clock <= grid.until ? 1.0F : (float) Math.max(0.0D, 1.0D - (frame.clock - grid.until) / 20.0D);
		float a = Math.min(1.0F, (float) age / 4.0F) * out * (0.85F + 0.15F * (float) Math.sin(frame.clock * 0.3D));
		if (a <= 0.02F) {
			return;
		}
		int quads = grid.lines * 2 * grid.samples * 2;
		if (!TitahWorld.quads(quads)) {
			return;
		}
		int core = TitahWorld.argb(0.9F * a, grid.rgb);
		int bloom = TitahWorld.argb(0.3F * a, grid.rgb);
		float length = grid.half * 2.0F;
		float width = Math.max(0.15F, Math.min(0.5F, grid.cell * 0.05F));
		// the sweep: from the middle sample outward in both directions
		int mid = grid.samples / 2;
		int reach = Math.round(sweep * (grid.samples - mid));
		for (int l = 0; l < grid.lines * 2; l++) {
			boolean alongZ = l < grid.lines;
			float off = grid.offset(l % grid.lines);
			float[] h = grid.heights[l];
			for (int s = Math.max(0, mid - reach); s < Math.min(grid.samples, mid + reach); s++) {
				float a0 = length * s / grid.samples;
				float a1 = length * (s + 1) / grid.samples;
				double wx0 = alongZ ? grid.x0() + off : grid.x0() + a0;
				double wz0 = alongZ ? grid.z0() + a0 : grid.z0() + off;
				double wx1 = alongZ ? grid.x0() + off : grid.x0() + a1;
				double wz1 = alongZ ? grid.z0() + a1 : grid.z0() + off;
				float ax = frame.rx(wx0);
				float ay = frame.ry(h[s]);
				float az = frame.rz(wz0);
				float bx = frame.rx(wx1);
				float by = frame.ry(h[s + 1]);
				float bz = frame.rz(wz1);
				frame.groundStrip(frame.lightning, ax, ay, az, bx, by, bz, width, core, core, 0.0F, 1.0F);
				frame.groundStrip(frame.glow, ax, ay + 0.01F, az, bx, by + 0.01F, bz, width * 5.0F, bloom, bloom, 0.0F, 1.0F);
			}
		}
	}

	/**
	 * The stepped pit: a darkening fill (deeper towards the middle, faking the drop) and the terrace edges glowing
	 * violet to gold (the inner, deeper ones hotter), cooling over 30 s.
	 */
	private static void drawPit(SigilRenderer.Frame frame, Pit pit) {
		double range = TitahWorld.viewRange() + pit.half;
		if (frame.dist2(pit.centre.x, pit.centre.y, pit.centre.z) > range * range) {
			return;
		}
		float t = (float) ((frame.clock - pit.born) / PIT_LIFE);
		if (t >= 1.0F || t < 0.0F) {
			return;
		}
		float appear = Math.min(1.0F, (float) (frame.clock - pit.born) / 8.0F);
		float cool = t < 0.3F ? 0.0F : (t - 0.3F) / 0.7F;
		float alpha = appear * (1.0F - cool * cool);
		if (alpha <= 0.02F) {
			return;
		}
		int n = pit.n;
		int edges = pit.steps * 4 * n;
		if (!TitahWorld.quads(n * n + edges * 2)) {
			return;
		}
		double x0 = pit.centre.x - pit.half;
		double z0 = pit.centre.z - pit.half;
		float cell = pit.half * 2.0F / n;
		// the dark fill: the ground-glow sheet tinted near-black, densest at the centre
		int dark = TitahWorld.argb(0.6F * alpha, 0x120A24);
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				float ax = frame.rx(x0 + i * cell);
				float az = frame.rz(z0 + j * cell);
				float y = frame.ry(pit.heights[i * (n + 1) + j]);
				frame.cell(frame.ground, ax, az, ax + cell, az + cell, y + 0.005F, (float) i / n, (float) j / n, (float) (i + 1) / n, (float) (j + 1) / n, dark);
			}
		}
		// the terrace edges: squares of half side half × (1 − k / steps)
		for (int k = 0; k < pit.steps; k++) {
			float hw = pit.half * (1.0F - (float) k / pit.steps);
			float depth = (float) k / Math.max(1, pit.steps - 1);
			int rgb = TitahWorld.mix(pit.rgb, pit.rgb2, depth);
			int core = TitahWorld.argb(Math.min(1.0F, (0.55F + 0.45F * depth) * alpha), TitahWorld.mix(rgb, 0x3A1408, cool));
			int bloom = TitahWorld.argb(0.3F * alpha, rgb);
			float width = Math.max(0.2F, Math.min(0.8F, pit.half * 0.02F));
			for (int e = 0; e < 4; e++) {
				for (int s = 0; s < n; s++) {
					float f0 = -hw + 2.0F * hw * s / n;
					float f1 = -hw + 2.0F * hw * (s + 1) / n;
					double wx0;
					double wz0;
					double wx1;
					double wz1;
					switch (e) {
						case 0 -> {
							wx0 = pit.centre.x + f0;
							wz0 = pit.centre.z - hw;
							wx1 = pit.centre.x + f1;
							wz1 = pit.centre.z - hw;
						}
						case 1 -> {
							wx0 = pit.centre.x + hw;
							wz0 = pit.centre.z + f0;
							wx1 = pit.centre.x + hw;
							wz1 = pit.centre.z + f1;
						}
						case 2 -> {
							wx0 = pit.centre.x - f0;
							wz0 = pit.centre.z + hw;
							wx1 = pit.centre.x - f1;
							wz1 = pit.centre.z + hw;
						}
						default -> {
							wx0 = pit.centre.x - hw;
							wz0 = pit.centre.z - f0;
							wx1 = pit.centre.x - hw;
							wz1 = pit.centre.z - f1;
						}
					}
					float ay = frame.ry(height(pit, wx0, wz0));
					float by = frame.ry(height(pit, wx1, wz1));
					frame.groundStrip(frame.lightning, frame.rx(wx0), ay + 0.02F, frame.rz(wz0), frame.rx(wx1), by + 0.02F, frame.rz(wz1), width, core, core, 0.0F,
							1.0F);
					frame.groundStrip(frame.glow, frame.rx(wx0), ay + 0.03F, frame.rz(wz0), frame.rx(wx1), by + 0.03F, frame.rz(wz1), width * 5.0F, bloom, bloom,
							0.0F, 1.0F);
				}
			}
		}
	}

	/** The cached ground height of the pit at a world point (nearest sample). */
	private static float height(Pit pit, double x, double z) {
		double cell = pit.half * 2.0D / pit.n;
		int i = Mth.clamp((int) Math.round((x - (pit.centre.x - pit.half)) / cell), 0, pit.n);
		int j = Mth.clamp((int) Math.round((z - (pit.centre.z - pit.half)) / cell), 0, pit.n);
		return pit.heights[i * (pit.n + 1) + j];
	}

	private LiftCarry() {
	}
}
