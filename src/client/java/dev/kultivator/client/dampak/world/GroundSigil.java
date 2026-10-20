package dev.kultivator.client.dampak.world;

import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.data.Dao;
import dev.kultivator.technique.spec.dsl.SigilPattern;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Sigil Bumi (DESIGN 28.9.2): formation diagrams drawn on the terrain at a cast's aim. Lines are thin glow strips
 * ({@code lightning()}, the Dao colour) with a wider soft strip at α 0.25 faking the bloom, sampled every 1 b with every
 * vertex on the client heightmap + 0.04 (so they climb hills and drop into craters); nodes are soft glow discs.
 * Ignition: one node and its incoming lines light per event of {@code Dampak.sigilIgnite}, the rest together on the
 * climax; a lit line brightens from its node outward over 6 ticks. Star impacts (BEIDOU_NINE, WUXING, LUOSHU): on the
 * climax each node gets a staggered strike 2 ticks apart (a short sky pillar, a small ShockRing and a glowing crystal
 * display that stays with the monument). A sigil lives until its climax + 100 ticks, then fades over 20. With Mode
 * Kehancuran and a trench, the lit lines burn (palette particles) for 10 s. The geometry comes from the shared
 * {@code SigilPattern} (the same points as the server's trenches); Minimal draws dust dots every 2 b instead.
 * <p>
 * Titah Langit sigils ({@link #createTitah}, DESIGN 29.8) may span up to 256 b: their lines are sampled every
 * {@code max(1, R / 64)} b (the point cap stays 2048), drawn wider, never make star impacts here (the Seven Stars fall in
 * {@code StarFall}), stay 200 ticks after the climax and, on the climax, ignite: the lines burn as glowing trenches in the
 * hot colour for the DOT window, visual only.
 * <p>
 * Tingkat Titah (DESIGN 29.16.10): a Titah sigil may span the decree's true radius (≤ 4096 b, so its nodes stay where the
 * server's stars fall). A sigil wider than this client draws ({@link DampakWorldBudget#titahRadius}) draws only the part
 * of its lines within render distance of the camera (tier-1 sigils, ≤ 256 b, draw as before), with the stride raised so
 * at most 128 / 256 / 512 points (Minimal
 * dust / Normal / Maximal) draw.
 */
final class GroundSigil {

	private static final int MAX_SIGILS = 8;
	private static final int LIGHT_TICKS = 6;
	private static final int AFTER_CLIMAX = 100;
	private static final int FADE = 20;
	private static final int NO_CLIMAX_LIFE = 900;
	private static final int REFRESH = 10;
	private static final int BURN_TICKS = 200;
	private static final int STRIKE_TICKS = 12;
	private static final int MAX_POINTS = 2048;

	private static final List<Sigil> SIGILS = new ArrayList<>();
	private static final List<Strike> STRIKES = new ArrayList<>();

	private record Strike(double x, double y, double z, int rgb, double at) {
	}

	/** One sigil on the ground. */
	static final class Sigil {
		final long cast;
		final String pattern;
		final int rgb;
		final Vec3 centre;
		final float radius;
		final boolean own;
		final boolean starImpacts;
		final String palette;
		final double born;
		/** Node x, z and cached ground y. */
		float[] nodes = new float[0];
		int nodeCount;
		double[] nodeLit = new double[0];
		/**
		 * Polylines of the segments: per segment its sample points (x, y, z triples), its lit time and direction
		 * (0 = from the start), and the node whose ignition lights it ({@code SigilPattern.Segment.node}, -1 = the climax).
		 */
		final List<float[]> lines = new ArrayList<>();
		final List<double[]> lineLit = new ArrayList<>();
		final List<Integer> lineNode = new ArrayList<>();
		int lit;
		double climaxAt = Double.NaN;
		double endAt = Double.NaN;
		double burnUntil = Double.NaN;
		int refreshIn = REFRESH;
		int crystalLife = 1200;
		int dotTick;
		/** Sample spacing of the lines in blocks (1; Titah: max(1, R / 64)). */
		double step = 1.0D;
		/** A Titah sigil (wide, no star impacts here, ignites on the climax). */
		boolean titah;
		/** Ticks it stays after the climax. */
		int afterClimax = AFTER_CLIMAX;
		/** The burning-trench colour after a Titah climax (-1 = none). */
		int hotRgb = -1;

		Sigil(long cast, SigilPattern pattern, int rgb, Vec3 centre, float radius, boolean own, String palette, double born) {
			this.cast = cast;
			this.pattern = pattern.name();
			this.rgb = rgb;
			this.centre = centre;
			this.radius = radius;
			this.own = own;
			this.palette = palette;
			this.born = born;
			this.starImpacts = pattern.starImpacts();
		}

		boolean chain() {
			return "EVENT_CHAIN".equals(pattern);
		}

		boolean daoCircle() {
			return "DAO_CIRCLE".equals(pattern);
		}

		double life() {
			if (!Double.isNaN(endAt)) {
				return endAt;
			}
			if (!Double.isNaN(climaxAt)) {
				return climaxAt + afterClimax;
			}
			return born + NO_CLIMAX_LIFE;
		}

		void addNode(float x, float y, float z) {
			if (nodeCount * 3 + 3 > nodes.length) {
				nodes = Arrays.copyOf(nodes, Math.max(12, nodes.length * 2));
				nodeLit = Arrays.copyOf(nodeLit, nodes.length / 3);
			}
			nodes[nodeCount * 3] = x;
			nodes[nodeCount * 3 + 1] = y;
			nodes[nodeCount * 3 + 2] = z;
			nodeLit[nodeCount] = Double.NaN;
			nodeCount++;
		}

		/** Adds a segment lit by node {@code node} (-1 = the climax), sampled every {@link #step} b (1 b by default). */
		void addLine(double ax, double az, double bx, double bz, int node) {
			double dx = bx - ax;
			double dz = bz - az;
			double length = Math.sqrt(dx * dx + dz * dz);
			if (!(length > 0.05D) || length > (titah ? 8192.0D : 256.0D)) {
				return;
			}
			int samples = Math.max(1, (int) Math.ceil(length / Math.max(1.0D, step)));
			float[] points = new float[(samples + 1) * 3];
			for (int i = 0; i <= samples; i++) {
				double f = (double) i / samples;
				points[i * 3] = (float) (ax + dx * f);
				points[i * 3 + 1] = (float) centre.y;
				points[i * 3 + 2] = (float) (az + dz * f);
			}
			lines.add(points);
			lineLit.add(new double[]{Double.NaN, 0.0D});
			lineNode.add(node);
		}

		int points() {
			int n = 0;
			for (float[] line : lines) {
				n += line.length / 3;
			}
			return n;
		}

		/** Lights node {@code k} and its lines; each brightens from the end nearer the node. */
		void lightNode(int k, double now) {
			if (k < 0 || k >= nodeCount || !Double.isNaN(nodeLit[k])) {
				return;
			}
			nodeLit[k] = now;
			float nx = nodes[k * 3];
			float nz = nodes[k * 3 + 2];
			for (int i = 0; i < lines.size(); i++) {
				double[] state = lineLit.get(i);
				if (lineNode.get(i) != k || !Double.isNaN(state[0])) {
					continue;
				}
				float[] line = lines.get(i);
				int last = line.length - 3;
				double toStart = sq(line[0] - nx) + sq(line[2] - nz);
				double toEnd = sq(line[last] - nx) + sq(line[last + 2] - nz);
				state[0] = now;
				state[1] = toEnd < toStart ? 1.0D : 0.0D;
			}
		}

		private static double sq(double v) {
			return v * v;
		}

		void lightAll(double now) {
			for (int k = 0; k < nodeCount; k++) {
				if (Double.isNaN(nodeLit[k])) {
					nodeLit[k] = now;
				}
			}
			for (double[] state : lineLit) {
				if (Double.isNaN(state[0])) {
					state[0] = now;
					state[1] = 0.0D;
				}
			}
			lit = nodeCount;
		}
	}

	// ================================================================== API

	/**
	 * A new sigil of {@code pattern} at {@code centre} (the CAST's aim) for a cast, oriented by
	 * {@link SigilPattern#yawFor} of {@code castPos → centre} with the event seed (the server's trench convention); null
	 * when none is drawn.
	 */
	static @Nullable Sigil create(ClientLevel level, long cast, @Nullable SigilPattern pattern, Vec3 castPos, Vec3 centre, float radius, int seed,
								  int rgb, boolean own, @Nullable String palette) {
		if (pattern == null || pattern == SigilPattern.NONE) {
			return null;
		}
		if (!(radius > 0.0F) && pattern != SigilPattern.EVENT_CHAIN) {
			return null;
		}
		end(cast);
		if (SIGILS.size() >= MAX_SIGILS) {
			SIGILS.remove(0);
		}
		Sigil sigil = new Sigil(cast, pattern, rgb & 0xFFFFFF, centre, Math.min(48.0F, Math.max(1.0F, radius)), own, palette == null ? "NONE" : palette,
				DampakWorld.clock(0.0F));
		if (!sigil.chain() && !sigil.daoCircle()) {
			double yaw = SigilPattern.yawFor(castPos, centre, seed);
			for (Vec3 node : SigilPattern.nodes(pattern, centre, yaw, sigil.radius, seed)) {
				sigil.addNode((float) node.x, (float) centre.y, (float) node.z);
			}
			for (SigilPattern.Segment segment : SigilPattern.segments(pattern, centre, yaw, sigil.radius, seed)) {
				if (sigil.points() >= MAX_POINTS) {
					break;
				}
				sigil.addLine(segment.from().x, segment.from().z, segment.to().x, segment.to().z, segment.node());
			}
		}
		refresh(level, sigil);
		SIGILS.add(sigil);
		DampakWorld.touchGeometry();
		return sigil;
	}

	/**
	 * A Titah sigil (DESIGN 29.3.2, 29.8): like {@link #create} but up to 256 b, sampled every {@code max(1, R / 64)} b,
	 * without star impacts; null when none is drawn.
	 */
	static @Nullable Sigil createTitah(ClientLevel level, long cast, @Nullable SigilPattern pattern, Vec3 castPos, Vec3 centre, float radius, int seed,
									   int rgb, boolean own, @Nullable String palette) {
		if (pattern == null || pattern == SigilPattern.NONE || pattern == SigilPattern.EVENT_CHAIN || !(radius > 0.0F)) {
			return null;
		}
		end(cast);
		if (SIGILS.size() >= MAX_SIGILS) {
			SIGILS.remove(0);
		}
		// up to the decree's true radius (29.16.10): the nodes stay where the server's stars fall
		Sigil sigil = new Sigil(cast, pattern, rgb & 0xFFFFFF, centre, Math.min(4096.0F, Math.max(1.0F, radius)), own, palette == null ? "NONE" : palette,
				DampakWorld.clock(0.0F));
		sigil.titah = true;
		sigil.afterClimax = 200;
		sigil.step = Math.max(1.0D, sigil.radius / 64.0D);
		if (!sigil.daoCircle()) {
			double yaw = SigilPattern.yawFor(castPos, centre, seed);
			for (Vec3 node : SigilPattern.nodes(pattern, centre, yaw, sigil.radius, seed)) {
				sigil.addNode((float) node.x, (float) centre.y, (float) node.z);
			}
			for (SigilPattern.Segment segment : SigilPattern.segments(pattern, centre, yaw, sigil.radius, seed)) {
				if (sigil.points() >= MAX_POINTS) {
					break;
				}
				sigil.addLine(segment.from().x, segment.from().z, segment.to().x, segment.to().z, segment.node());
			}
		}
		refresh(level, sigil);
		SIGILS.add(sigil);
		DampakWorld.touchGeometry();
		return sigil;
	}

	/**
	 * A Titah climax: every node and line lights; with {@code burnTicks} &gt; 0 the lines ignite as burning trenches in
	 * {@code hotRgb} (glow strips and palette particles) for that long. Visual only.
	 */
	static void climaxTitah(long cast, int burnTicks, int hotRgb) {
		Sigil sigil = find(cast);
		if (sigil == null) {
			return;
		}
		double now = DampakWorld.clock(0.0F);
		sigil.lightAll(now);
		sigil.climaxAt = now;
		if (burnTicks > 0) {
			sigil.burnUntil = now + burnTicks;
			sigil.hotRgb = hotRgb & 0xFFFFFF;
			sigil.afterClimax = Math.max(sigil.afterClimax, burnTicks + 60);
		}
		DampakWorld.touchGeometry();
	}

	/** The node positions (x, ground y, z) of a cast's sigil, or an empty list. */
	static List<Vec3> nodesOf(long cast) {
		Sigil sigil = find(cast);
		if (sigil == null) {
			return List.of();
		}
		List<Vec3> out = new ArrayList<>(sigil.nodeCount);
		for (int k = 0; k < sigil.nodeCount; k++) {
			out.add(new Vec3(sigil.nodes[k * 3], sigil.nodes[k * 3 + 1], sigil.nodes[k * 3 + 2]));
		}
		return out;
	}

	/** A glowing crystal display (a star impact's crystal; the Seven Stars use it): grows after {@code delay}, stays {@code life}. */
	static void crystal(ClientLevel level, BlockState state, Vec3 at, long cast, int delay, int life) {
		Crystal.make(level, state, at, cast, delay, life);
	}

	/** An ignition event of the cast: the next node lights (EVENT_CHAIN: a new node at {@code at}, joined to the last). */
	static void ignite(ClientLevel level, long cast, Vec3 at) {
		Sigil sigil = find(cast);
		if (sigil == null) {
			return;
		}
		double now = DampakWorld.clock(0.0F);
		if (sigil.chain()) {
			if (sigil.nodeCount >= 24) {
				return;
			}
			int previous = sigil.nodeCount - 1;
			sigil.addNode((float) at.x, (float) at.y, (float) at.z);
			if (previous >= 0) {
				sigil.addLine(sigil.nodes[previous * 3], sigil.nodes[previous * 3 + 2], at.x, at.z, sigil.nodeCount - 1);
			}
			refresh(level, sigil);
			sigil.lightNode(sigil.nodeCount - 1, now);
			sigil.lit = sigil.nodeCount;
			return;
		}
		if (sigil.lit < sigil.nodeCount) {
			sigil.lightNode(sigil.lit, now);
			sigil.lit++;
		}
	}

	/** The climax: the rest lights at once; star impacts; trench burning when the cast carved. */
	static void climax(ClientLevel level, long cast, boolean kehancuran, boolean trench, int crystalLife, boolean displays) {
		Sigil sigil = find(cast);
		if (sigil == null) {
			return;
		}
		double now = DampakWorld.clock(0.0F);
		sigil.lightAll(now);
		sigil.climaxAt = now;
		sigil.crystalLife = Math.max(200, crystalLife);
		if (kehancuran && trench) {
			sigil.burnUntil = now + BURN_TICKS;
		}
		if (!sigil.starImpacts) {
			return;
		}
		int index = DampakWorldBudget.index();
		int crystals = DampakWorldBudget.CRYSTALS[index];
		int perNode = index >= 2 ? 2 : 1;
		BlockState crystal = crystalFor(sigil.rgb);
		for (int k = 0; k < sigil.nodeCount; k++) {
			double x = sigil.nodes[k * 3];
			double y = sigil.nodes[k * 3 + 1];
			double z = sigil.nodes[k * 3 + 2];
			double at = now + k * 2.0D;
			STRIKES.add(new Strike(x, y, z, sigil.rgb, at));
			ShockRing.start(new Vec3(x, y, z), 3.0F, "DUST", sigil.rgb, sigil.own, k * 2, false);
			for (int c = 0; c < perNode && displays && crystals > 0 && DampakDisplays.count(DampakDisplays.CRYSTAL) < crystals; c++) {
				double ox = c == 0 ? 0.0D : 0.7D;
				Crystal.make(level, crystal, new Vec3(x + ox, y - SigilRenderer.LIFT, z + ox * 0.5D), cast, k * 2, sigil.crystalLife);
			}
		}
		DampakWorld.touchGeometry();
	}

	/** The cast ended without (or after) its climax: the sigil fades now unless it is already timed. */
	static void end(long cast) {
		Sigil sigil = find(cast);
		if (sigil != null && Double.isNaN(sigil.climaxAt)) {
			sigil.endAt = DampakWorld.clock(0.0F) + FADE;
		}
	}

	/** ABORT: fades at once. */
	static void abort(long cast) {
		Sigil sigil = find(cast);
		if (sigil != null) {
			sigil.endAt = Math.min(sigil.life(), DampakWorld.clock(0.0F));
		}
	}

	static @Nullable Sigil find(long cast) {
		for (int i = SIGILS.size() - 1; i >= 0; i--) {
			if (SIGILS.get(i).cast == cast) {
				return SIGILS.get(i);
			}
		}
		return null;
	}

	static boolean active() {
		return !SIGILS.isEmpty() || !STRIKES.isEmpty();
	}

	static int count() {
		return SIGILS.size();
	}

	static void clear() {
		SIGILS.clear();
		STRIKES.clear();
	}

	private static BlockState crystalFor(int rgb) {
		for (Dao dao : Dao.values()) {
			if (dao.color() == rgb) {
				return switch (dao) {
					case LIGHTNING, SPACE, SHADOW -> Blocks.AMETHYST_CLUSTER.defaultBlockState();
					case ICE, LIFE -> Blocks.SEA_LANTERN.defaultBlockState();
					default -> Blocks.END_ROD.defaultBlockState();
				};
			}
		}
		return Blocks.END_ROD.defaultBlockState();
	}

	// ================================================================== tick

	static void tick(ClientLevel level, double clock) {
		STRIKES.removeIf(strike -> clock - strike.at() > STRIKE_TICKS);
		boolean minimal = DampakWorldBudget.tier() <= 0;
		for (Iterator<Sigil> it = SIGILS.iterator(); it.hasNext(); ) {
			Sigil sigil = it.next();
			if (clock > sigil.life() + FADE) {
				it.remove();
				continue;
			}
			if (--sigil.refreshIn <= 0) {
				refresh(level, sigil);
			}
			sigil.dotTick++;
			if (minimal && sigil.dotTick % 10 == 0) {
				dots(sigil);
			}
			if (!Double.isNaN(sigil.burnUntil) && clock < sigil.burnUntil) {
				burn(sigil);
			}
			if (sigil.daoCircle() && sigil.dotTick % 20 == 0 && minimal) {
				ringDots(sigil);
			}
		}
		// star-impact pillars at Minimal: particle columns
		if (minimal) {
			for (Strike strike : STRIKES) {
				if ((int) (clock - strike.at()) == 0) {
					DustParticleOptions dust = new DustParticleOptions(strike.rgb(), 1.5F);
					int n = DampakParticles.grant(10);
					for (int i = 0; i < n; i++) {
						DampakParticles.one(dust, strike.x(), strike.y() + i * 1.5D, strike.z(), 0.0D, -0.05D, 0.0D);
					}
				}
			}
		}
	}

	private static void refresh(ClientLevel level, Sigil sigil) {
		sigil.refreshIn = REFRESH;
		for (int k = 0; k < sigil.nodeCount; k++) {
			sigil.nodes[k * 3 + 1] = (float) (DampakDisplays.ground(level, sigil.nodes[k * 3], sigil.nodes[k * 3 + 2], sigil.centre.y) + SigilRenderer.LIFT);
		}
		for (float[] line : sigil.lines) {
			for (int i = 0; i + 2 < line.length; i += 3) {
				line[i + 1] = (float) (DampakDisplays.ground(level, line[i], line[i + 2], sigil.centre.y) + SigilRenderer.LIFT);
			}
		}
	}

	/** Minimal: dust dots every 2 b along the lines (lit ones brighter). */
	private static void dots(Sigil sigil) {
		DustParticleOptions dim = new DustParticleOptions(sigil.rgb, 0.6F);
		DustParticleOptions bright = new DustParticleOptions(sigil.rgb, 1.1F);
		boolean wide = wide(sigil);
		int budget = DampakParticles.grant(wide ? DampakWorldBudget.FAR_SIGIL_POINTS[0] : 64);
		// a wide Titah sigil (29.16.10): only the dots within render distance of the camera
		Vec3 camera = wide ? net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position() : Vec3.ZERO;
		double range2 = wide ? visibleRange() * visibleRange() : 0.0D;
		for (int i = 0; i < sigil.lines.size() && budget > 0; i++) {
			float[] line = sigil.lines.get(i);
			boolean lit = !Double.isNaN(sigil.lineLit.get(i)[0]);
			for (int p = 0; p + 2 < line.length && budget > 0; p += 6) {
				if (wide) {
					double dx = line[p] - camera.x;
					double dz = line[p + 2] - camera.z;
					if (dx * dx + dz * dz > range2) {
						continue;
					}
				}
				budget--;
				DampakParticles.one(lit ? bright : dim, line[p], line[p + 1] + 0.1D, line[p + 2], 0.0D, 0.0D, 0.0D);
			}
		}
	}

	private static void ringDots(Sigil sigil) {
		DustParticleOptions dust = new DustParticleOptions(sigil.rgb, 0.9F);
		int n = DampakParticles.grant(Math.min(48, 8 + Math.round(sigil.radius * 3.0F)));
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / Math.max(1, n);
			DampakParticles.one(dust, sigil.centre.x + Math.cos(angle) * sigil.radius, sigil.centre.y + 0.15D, sigil.centre.z + Math.sin(angle) * sigil.radius,
					0.0D, 0.0D, 0.0D);
		}
	}

	/** Burning trenches: palette particles along the lit lines. */
	private static void burn(Sigil sigil) {
		if (sigil.lines.isEmpty()) {
			return;
		}
		if (sigil.titah && sigil.hotRgb >= 0 && (sigil.dotTick & 1) == 0) {
			// the burning trenches of a Titah: rising embers in the hot colour
			int embers = DampakParticles.grant(DampakWorldBudget.scaled(4, sigil.own, 8));
			DustParticleOptions ember = new DustParticleOptions(sigil.hotRgb, 1.2F);
			RandomSource sparks = DampakParticles.random();
			for (int i = 0; i < embers; i++) {
				float[] line = sigil.lines.get(sparks.nextInt(sigil.lines.size()));
				int p = sparks.nextInt(line.length / 3) * 3;
				DampakParticles.one(ember, line[p], line[p + 1] + 0.1D, line[p + 2], 0.0D, 0.06D + sparks.nextDouble() * 0.05D, 0.0D);
			}
		}
		ParticleOptions particle = switch (sigil.palette) {
			case "FIRE" -> ParticleTypes.FLAME;
			case "LIGHTNING" -> ParticleTypes.ELECTRIC_SPARK;
			default -> MoltenGlow.paletteParticle(sigil.palette);
		};
		RandomSource random = DampakParticles.random();
		int n = DampakParticles.grant(DampakWorldBudget.scaled(6, sigil.own, 12));
		for (int i = 0; i < n; i++) {
			float[] line = sigil.lines.get(random.nextInt(sigil.lines.size()));
			int p = random.nextInt(line.length / 3) * 3;
			DampakParticles.one(particle, line[p] + random.nextGaussian() * 0.2D, line[p + 1] - 0.6D, line[p + 2] + random.nextGaussian() * 0.2D, 0.0D,
					0.04D + random.nextDouble() * 0.04D, 0.0D);
		}
	}

	// ================================================================== render

	static void render(SigilRenderer.Frame frame) {
		int index = DampakWorldBudget.index();
		boolean draw = DampakWorldBudget.tier() >= 1;
		int segmentBudget = DampakWorldBudget.SIGIL_SEGMENTS[index];
		int nodeBudget = DampakWorldBudget.SIGIL_NODES[index];
		if (draw) {
			for (int s = 0; s < SIGILS.size(); s++) {
				Sigil sigil = SIGILS.get(s);
				if (frame.dist2(sigil.centre.x, sigil.centre.y, sigil.centre.z) > (sigil.radius + 112.0D) * (sigil.radius + 112.0D)) {
					continue;
				}
				double end = sigil.life();
				float fade = frame.clock > end ? 1.0F - (float) Math.min(1.0D, (frame.clock - end) / FADE) : 1.0F;
				float appear = (float) Math.min(1.0D, (frame.clock - sigil.born) / 10.0D);
				float master = fade * appear;
				if (master <= 0.01F) {
					continue;
				}
				if (sigil.daoCircle()) {
					drawCircle(frame, sigil, master);
					continue;
				}
				segmentBudget = drawLines(frame, sigil, master, segmentBudget);
				nodeBudget = drawNodes(frame, sigil, master, nodeBudget);
			}
		}
		for (int i = 0; i < STRIKES.size(); i++) {
			Strike strike = STRIKES.get(i);
			double age = frame.clock - strike.at();
			if (age < 0.0D || age > STRIKE_TICKS || DampakWorldBudget.tier() <= 0) {
				continue;
			}
			frame.pillar(strike.x(), strike.y(), strike.z(), 40, strike.rgb(), (float) (0.6D * (1.0D - age / STRIKE_TICKS)));
		}
	}

	private static void drawCircle(SigilRenderer.Frame frame, Sigil sigil, float master) {
		if (!EpicBudget.takeQuads(1)) {
			return;
		}
		boolean lit = !Double.isNaN(sigil.climaxAt) || sigil.lit > 0;
		float a = master * (lit ? 0.85F : 0.45F);
		int color = ARGB.color(Math.round(a * 255.0F), sigil.rgb);
		double y = DampakDisplays.ground(frame.level, sigil.centre.x, sigil.centre.z, sigil.centre.y) + SigilRenderer.LIFT;
		frame.flat(frame.rune, frame.rx(sigil.centre.x), frame.ry(y), frame.rz(sigil.centre.z), sigil.radius,
				(float) Math.toRadians(frame.clock * 1.5D), color);
	}

	private static int drawLines(SigilRenderer.Frame frame, Sigil sigil, float master, int budget) {
		float width = sigil.titah ? Math.max(0.5F, Math.min(1.6F, sigil.radius * 0.012F)) : Math.max(0.25F, Math.min(0.6F, sigil.radius * 0.03F));
		int dimCore = ARGB.color(Math.round(master * 70.0F), sigil.rgb);
		int dimBloom = ARGB.color(Math.round(master * 26.0F), sigil.rgb);
		int litCore = ARGB.color(Math.round(master * 240.0F), sigil.rgb);
		int litBloom = ARGB.color(Math.round(master * 64.0F), sigil.rgb);
		// a Titah climax: the lit lines burn as glowing trenches in the hot colour, flickering, then cool with the fade
		boolean burning = sigil.titah && sigil.hotRgb >= 0 && !Double.isNaN(sigil.burnUntil);
		int trench = 0;
		if (burning) {
			double left = sigil.burnUntil - frame.clock;
			float heat = (float) Math.max(0.25D, Math.min(1.0D, left / 40.0D + 0.25D));
			float flicker = 0.85F + 0.15F * (float) Math.sin(frame.clock * 0.9D + (sigil.cast & 0xFF));
			litCore = ARGB.color(Math.round(Math.min(1.0F, master * flicker) * 255.0F), mixRgb(sigil.hotRgb, 0xFFFFFF, 0.35F * heat));
			litBloom = ARGB.color(Math.round(master * 110.0F * heat), sigil.hotRgb);
			trench = ARGB.color(Math.round(master * 200.0F * heat * flicker), sigil.hotRgb);
		}
		if (wide(sigil)) {
			return drawWide(frame, sigil, budget, width, dimCore, dimBloom, litCore, litBloom, burning, trench);
		}
		for (int i = 0; i < sigil.lines.size() && budget > 0; i++) {
			float[] line = sigil.lines.get(i);
			int samples = line.length / 3 - 1;
			if (samples <= 0) {
				continue;
			}
			if (!EpicBudget.takeQuads(samples * (burning ? 3 : 2))) {
				return 0;
			}
			budget -= samples;
			double[] state = sigil.lineLit.get(i);
			float litShare = 0.0F;
			if (!Double.isNaN(state[0])) {
				litShare = (float) Math.min(1.0D, Math.max(0.0D, (frame.clock - state[0]) / LIGHT_TICKS));
			}
			boolean fromB = state[1] > 0.5D;
			for (int p = 0; p < samples; p++) {
				float f = (p + 0.5F) / samples;
				float along = fromB ? 1.0F - f : f;
				boolean lit = along <= litShare;
				int core = lit ? litCore : dimCore;
				int bloom = lit ? litBloom : dimBloom;
				int o = p * 3;
				float ax = frame.rx(line[o]);
				float ay = frame.ry(line[o + 1]);
				float az = frame.rz(line[o + 2]);
				float bx = frame.rx(line[o + 3]);
				float by = frame.ry(line[o + 4]);
				float bz = frame.rz(line[o + 5]);
				frame.groundStrip(frame.lightning, ax, ay, az, bx, by, bz, lit ? width : width * 0.6F, core, core, 0.0F, 1.0F);
				frame.groundStrip(frame.glow, ax, ay + 0.01F, az, bx, by + 0.01F, bz, width * 4.0F, bloom, bloom, 0.0F, 1.0F);
				if (burning && lit) {
					frame.groundStrip(frame.crack, ax, ay + 0.005F, az, bx, by + 0.005F, bz, width * 2.6F, trench, trench, 0.0F, 1.0F);
				}
			}
		}
		return budget;
	}

	/**
	 * A Titah sigil wider than any tier-1 sigil and than this client draws (Tingkat Titah, 29.16.10): only its visible
	 * part, stride-capped. Tier-1 sigils (≤ 256 b) draw as before.
	 */
	private static boolean wide(Sigil sigil) {
		return sigil.titah && sigil.radius > 256.0F && sigil.radius > DampakWorldBudget.titahRadius(sigil.radius) + 1.0F;
	}

	/** The render distance in blocks less a chunk (where the client has ground to draw on). */
	private static double visibleRange() {
		try {
			return Math.max(48.0D, net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0D - 16.0D);
		} catch (RuntimeException exception) {
			return 112.0D;
		}
	}

	/**
	 * The visible part of a wide Titah sigil: only samples within render distance of the camera, the stride raised so at
	 * most {@link DampakWorldBudget#FAR_SIGIL_POINTS} points draw this frame.
	 */
	private static int drawWide(SigilRenderer.Frame frame, Sigil sigil, int budget, float width, int dimCore, int dimBloom, int litCore, int litBloom,
								boolean burning, int trench) {
		double range = visibleRange();
		double range2 = range * range;
		int visible = 0;
		for (float[] line : sigil.lines) {
			for (int o = 0; o + 2 < line.length; o += 3) {
				float dx = frame.rx(line[o]);
				float dz = frame.rz(line[o + 2]);
				if (dx * dx + dz * dz <= range2) {
					visible++;
				}
			}
		}
		if (visible <= 0) {
			return budget;
		}
		int cap = DampakWorldBudget.FAR_SIGIL_POINTS[DampakWorldBudget.index()];
		int stride = Math.max(1, (int) Math.ceil(visible / (double) cap));
		for (int i = 0; i < sigil.lines.size() && budget > 0; i++) {
			float[] line = sigil.lines.get(i);
			int samples = line.length / 3 - 1;
			if (samples <= 0) {
				continue;
			}
			double[] state = sigil.lineLit.get(i);
			float litShare = Double.isNaN(state[0]) ? 0.0F : (float) Math.min(1.0D, Math.max(0.0D, (frame.clock - state[0]) / LIGHT_TICKS));
			boolean fromB = state[1] > 0.5D;
			for (int p = 0; p < samples && budget > 0; p += stride) {
				int q = Math.min(samples, p + stride);
				int o = p * 3;
				int e = q * 3;
				float ax = frame.rx(line[o]);
				float az = frame.rz(line[o + 2]);
				float bx = frame.rx(line[e]);
				float bz = frame.rz(line[e + 2]);
				if (ax * ax + az * az > range2 && bx * bx + bz * bz > range2) {
					continue;
				}
				if (!EpicBudget.takeQuads(burning ? 3 : 2)) {
					return 0;
				}
				budget--;
				float f = (p + q) * 0.5F / samples;
				boolean lit = (fromB ? 1.0F - f : f) <= litShare;
				int core = lit ? litCore : dimCore;
				int bloom = lit ? litBloom : dimBloom;
				float ay = frame.ry(line[o + 1]);
				float by = frame.ry(line[e + 1]);
				frame.groundStrip(frame.lightning, ax, ay, az, bx, by, bz, lit ? width : width * 0.6F, core, core, 0.0F, 1.0F);
				frame.groundStrip(frame.glow, ax, ay + 0.01F, az, bx, by + 0.01F, bz, width * 4.0F, bloom, bloom, 0.0F, 1.0F);
				if (burning && lit) {
					frame.groundStrip(frame.crack, ax, ay + 0.005F, az, bx, by + 0.005F, bz, width * 2.6F, trench, trench, 0.0F, 1.0F);
				}
			}
		}
		return budget;
	}

	private static int drawNodes(SigilRenderer.Frame frame, Sigil sigil, float master, int budget) {
		for (int k = 0; k < sigil.nodeCount && budget > 0; k++) {
			if (!EpicBudget.takeQuads(1)) {
				return 0;
			}
			budget--;
			double litAt = sigil.nodeLit[k];
			float glow = 0.3F;
			float size = 0.8F;
			if (!Double.isNaN(litAt)) {
				float t = (float) Math.min(1.0D, Math.max(0.0D, (frame.clock - litAt) / LIGHT_TICKS));
				// a flare on lighting, then a steady breathing glow
				glow = 0.55F + 0.45F * (1.0F - t) + 0.1F * (float) Math.sin(frame.clock * 0.15D + k);
				size = 0.8F + 0.7F * t;
			}
			int color = ARGB.color(Math.round(Math.min(1.0F, master * glow) * 255.0F), sigil.rgb);
			frame.flat(frame.ground, frame.rx(sigil.nodes[k * 3]), frame.ry(sigil.nodes[k * 3 + 1] + 0.02F), frame.rz(sigil.nodes[k * 3 + 2]), size,
					(float) (frame.clock * 0.02D), color);
		}
		return budget;
	}

	// ================================================================== crystals

	/** A glowing crystal of a star impact: grows in 6 ticks, stays with the monument, shrinks away. */
	private static final class Crystal extends DampakDisplays.Piece {
		private final float size;
		private final int delay;
		private final int life;
		private final Quaternionf turn;
		private boolean shown;
		private float endAge = -1.0F;

		Crystal(Display.BlockDisplay display, long cast, float size, int delay, int life, Quaternionf turn) {
			super(display, DampakDisplays.CRYSTAL, cast);
			this.size = size;
			this.delay = delay;
			this.life = life;
			this.turn = turn;
		}

		static void make(ClientLevel level, BlockState state, Vec3 at, long cast, int delay, int life) {
			RandomSource random = DampakParticles.random();
			float size = 0.6F + random.nextFloat();
			Display.BlockDisplay display = DampakDisplays.create(state, at.add(0.0D, size * 0.5D, 0.0D), size, true);
			if (display == null) {
				return;
			}
			Quaternionf turn = new Quaternionf().rotateY((float) (random.nextDouble() * Math.PI * 2.0D))
					.rotateX((float) Math.toRadians(random.nextDouble() * 16.0D - 8.0D));
			Crystal crystal = new Crystal(display, cast, size, delay, life, turn);
			crystal.pose(turn, 0.01F, 0.01F, 0.01F, true, 0);
			DampakDisplays.add(crystal);
		}

		@Override
		boolean tick(ClientLevel level, float sigma, boolean keyNow, int duration) {
			if (endAge >= 0.0F) {
				return age - endAge < 10.0F;
			}
			if (!shown && age >= delay) {
				shown = true;
				pose(turn, size, size, size, false, 6);
				DampakParticles.burst(level, ParticleTypes.END_ROD, display.position(), 6, 0.1F);
			}
			if (age >= delay + life) {
				endAge = age;
				crumble(level, 6);
				pose(turn, 0.01F, 0.01F, 0.01F, false, 10);
			}
			return true;
		}
	}

	private static int mixRgb(int a, int b, float t) {
		float k = Math.max(0.0F, Math.min(1.0F, t));
		int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * k);
		int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * k);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
		return r << 16 | g << 8 | bl;
	}

	private GroundSigil() {
	}
}
