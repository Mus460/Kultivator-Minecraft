package dev.kultivator.client.pusakalangit.world;

import dev.kultivator.client.dampak.world.DampakWorld;
import dev.kultivator.client.dampak.world.DampakWorldBudget;
import dev.kultivator.client.dampak.world.SigilRenderer;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.fx.EpicParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * What the sky shows of a decree (DESIGN 29.5), drawn for everyone in range as layered additive quads (no post chain):
 * the gold ring tearing the clouds above Pedang's impact (the Celestial Court's gate as bystanders see it), the red star of
 * the thrown pillar, the seven stars of the Northern Dipper flaring above the land and lighting one by one, the impact
 * light pillars (beacon beams with a soft base) and the flattening wave's blown grass and dust. Minimal draws particles
 * only.
 * <p>
 * Tingkat Titah (DESIGN 29.16.10): stars (the red star, the Dipper and the far star impacts of {@link #farStar}) beyond
 * render distance are drawn pulled in along their view ray to just inside it, scaled with the pull, so a sky thousands
 * of blocks wide keeps its true directions and sizes; a flattening wave wider than render distance spends its particles
 * only on the arc of its front that the camera can see.
 */
final class SkyMarks {

	private static final int GATE = 0;
	private static final int STAR = 1;
	private static final int DIPPER = 2;
	private static final int FLASH = 3;
	private static final int WAVE = 4;

	private static final int FADE = 20;
	private static final int WAVE_TICKS = 16;
	private static final int MAX = 32;

	private static final class Mark {
		final long cast;
		final int kind;
		final Vec3 at;
		final float size;
		final int rgb;
		final int rgb2;
		final double born;
		double until;
		final List<Vec3> stars = new ArrayList<>(7);
		double[] lit = new double[0];
		boolean leaves;
		int lastTick = -1;
		/** A far star impact (Tingkat Titah): a short flare at the star's landing point, drawn at any distance. */
		boolean farImpact;
		/** Drawn at any distance, pulled inside render distance (a tier 2-9 sky mark); tier-1 marks draw as before. */
		boolean projected;

		Mark(long cast, int kind, Vec3 at, float size, int rgb, int rgb2, double born, double until) {
			this.cast = cast;
			this.kind = kind;
			this.at = at;
			this.size = size;
			this.rgb = rgb;
			this.rgb2 = rgb2;
			this.born = born;
			this.until = until;
		}

		/** Overall alpha at {@code now}: in over 10 ticks, out over {@link #FADE} after {@code until}. */
		float alpha(double now) {
			float in = (float) Math.min(1.0D, Math.max(0.0D, (now - born) / 10.0D));
			float out = now <= until ? 1.0F : (float) Math.max(0.0D, 1.0D - (now - until) / FADE);
			return in * out;
		}

		boolean over(double now) {
			return now > until + FADE;
		}
	}

	private static final List<Mark> MARKS = new ArrayList<>();

	// ================================================================== API

	/** Pedang EXTRA1: a gold ring tears open in the clouds at {@code centre}, radius {@code r}, until {@code until}. */
	static void gate(long cast, Vec3 centre, float r, int rgb, int rgb2, double until) {
		add(new Mark(cast, GATE, centre, r, rgb, rgb2, TitahWorld.clock(), until));
		ParticleOptions rod = ParticleTypes.END_ROD;
		int n = EpicParticles.grant(16, false);
		RandomSource random = EpicParticles.random();
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			EpicParticles.add(rod, centre.x + Math.cos(angle) * r, centre.y, centre.z + Math.sin(angle) * r, 0.0D, -0.3D, 0.0D, true);
		}
	}

	/**
	 * A star of the Dipper landing beyond render distance (Tingkat Titah): only its sky sprite, a flare that fades over
	 * {@code ticks}, drawn pulled inside render distance along its view ray.
	 */
	static void farStar(long cast, Vec3 at, int rgb, float size, int ticks) {
		double now = TitahWorld.clock();
		Mark mark = new Mark(cast, STAR, at, size, rgb, 0xFFFFFF, now - 10.0D, now + Math.max(2, ticks) - FADE);
		mark.farImpact = true;
		mark.projected = true;
		add(mark);
	}

	/** A single bright star overhead (Tiang's red star) until {@code until}. */
	static void star(long cast, Vec3 at, int rgb, float size, double until) {
		star(cast, at, rgb, size, until, false);
	}

	/** {@link #star}; {@code projected} draws it at any distance, pulled inside render distance (tier 2-9). */
	static void star(long cast, Vec3 at, int rgb, float size, double until, boolean projected) {
		Mark mark = new Mark(cast, STAR, at, size, rgb, 0xFFFFFF, TitahWorld.clock(), until);
		mark.projected = projected;
		add(mark);
	}

	/**
	 * The seven Dipper stars flaring high above the land: the sigil's nodes ({@code nodes}, Tianshu → Yaoguang) lifted
	 * 110 b and drawn together at 60 %, or a drawn Dipper along {@code dir} when the sigil has none.
	 */
	static void dipper(long cast, Vec3 ground, List<Vec3> nodes, Vec3 dir, int rgb, double until) {
		dipper(cast, ground, nodes, dir, rgb, until, 1.0F);
	}

	/** {@link #dipper} of a Tingkat Titah decree: lifted {@code 110 × q} b, the stars {@code q} times larger. */
	static void dipper(long cast, Vec3 ground, List<Vec3> nodes, Vec3 dir, int rgb, double until, float q) {
		Mark mark = new Mark(cast, DIPPER, ground, Math.max(1.0F, q), rgb, TitahWorld.VIOLET, TitahWorld.clock(), until);
		mark.projected = q > 1.001F;
		Vec3 sky = ground.add(0.0D, 110.0D * Math.max(1.0F, q), 0.0D);
		if (nodes.size() >= 7) {
			double cx = 0.0D;
			double cz = 0.0D;
			for (int i = 0; i < 7; i++) {
				cx += nodes.get(i).x / 7.0D;
				cz += nodes.get(i).z / 7.0D;
			}
			for (int i = 0; i < 7; i++) {
				Vec3 n = nodes.get(i);
				mark.stars.add(new Vec3(sky.x + (n.x - cx) * 0.6D, sky.y + (i % 3) * 2.0D, sky.z + (n.z - cz) * 0.6D));
			}
		} else {
			// Tianshu, Tianxuan, Tianji, Tianquan, Yuheng, Kaiyang, Yaoguang (the asterism's shape, our own units)
			double[][] shape = {{0.0, 1.0}, {0.0, 0.0}, {1.3, -0.2}, {1.6, 0.6}, {2.6, 0.8}, {3.6, 1.0}, {4.6, 0.5}};
			Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
			double unit = 9.0D;
			for (int i = 0; i < 7; i++) {
				double a = (shape[i][0] - 2.3D) * unit;
				double b = (shape[i][1] - 0.5D) * unit;
				mark.stars.add(sky.add(dir.scale(a)).add(side.scale(b)).add(0.0D, (i % 3) * 2.0D, 0.0D));
			}
		}
		mark.lit = new double[7];
		java.util.Arrays.fill(mark.lit, Double.NaN);
		add(mark);
	}

	/** Star {@code index} (0 = Tianshu) of the cast's Dipper flares and stays bright. */
	static void lightStar(long cast, int index) {
		for (Mark mark : MARKS) {
			if (mark.cast == cast && mark.kind == DIPPER && index >= 0 && index < mark.lit.length && Double.isNaN(mark.lit[index])) {
				mark.lit[index] = TitahWorld.clock();
			}
		}
	}

	/** A light pillar from {@code at} up {@code height} b for {@code ticks}, {@code glow} = its glow radius factor. */
	static void flash(long cast, Vec3 at, int rgb, int ticks, int height, float glow) {
		double now = TitahWorld.clock();
		add(new Mark(cast, FLASH, at, glow, rgb, height, now, now + Math.max(2, ticks) - FADE));
	}

	/** A flattening wave: a front of blown grass, leaves and dust from {@code at} out to {@code r} over 16 ticks. */
	static void wave(long cast, Vec3 at, float r, int rgb, boolean leaves) {
		double now = TitahWorld.clock();
		Mark mark = new Mark(cast, WAVE, at, r, rgb, 0, now, now + WAVE_TICKS - FADE);
		mark.leaves = leaves;
		add(mark);
	}

	/** The cast's sky marks start to fade (the strike landed or the cast ended). */
	static void end(long cast) {
		double now = TitahWorld.clock();
		for (Mark mark : MARKS) {
			if (mark.cast == cast && (mark.kind == GATE || mark.kind == STAR || mark.kind == DIPPER)) {
				mark.until = Math.min(mark.until, now + (mark.kind == GATE ? 30.0D : mark.kind == DIPPER ? 40.0D : 2.0D));
			}
		}
	}

	static void abort(long cast) {
		double now = TitahWorld.clock();
		for (Mark mark : MARKS) {
			if (mark.cast == cast) {
				mark.until = Math.min(mark.until, now);
			}
		}
	}

	static int count() {
		return MARKS.size();
	}

	static void clear() {
		MARKS.clear();
	}

	private static void add(Mark mark) {
		if (!TitahWorld.finite(mark.at)) {
			return;
		}
		if (MARKS.size() >= MAX) {
			MARKS.remove(0);
		}
		MARKS.add(mark);
		TitahWorld.touch();
	}

	// ================================================================== tick: the wave's front, Minimal's particle stand-ins

	static void tick(ClientLevel level, double now) {
		if (MARKS.isEmpty()) {
			return;
		}
		boolean minimal = DampakWorldBudget.tier() <= 0;
		RandomSource random = EpicParticles.random();
		for (Iterator<Mark> it = MARKS.iterator(); it.hasNext(); ) {
			Mark mark = it.next();
			if (mark.over(now)) {
				it.remove();
				continue;
			}
			int tick = (int) (now - mark.born);
			if (tick == mark.lastTick) {
				continue;
			}
			mark.lastTick = tick;
			switch (mark.kind) {
				case WAVE -> waveFront(level, mark, tick, random);
				case FLASH -> {
					if (minimal && tick % 2 == 0) {
						int n = EpicParticles.grant(10, false);
						for (int i = 0; i < n; i++) {
							EpicParticles.add(EpicParticles.dust(mark.rgb, 2.0F), mark.at.x, mark.at.y + i * 3.0D, mark.at.z, 0.0D, 0.05D, 0.0D, true);
						}
					}
				}
				case STAR, GATE, DIPPER -> {
					if (minimal && tick % 10 == 0 && now <= mark.until) {
						List<Vec3> points = mark.kind == DIPPER ? mark.stars : List.of(mark.at);
						int n = EpicParticles.grant(points.size(), false);
						for (int i = 0; i < n; i++) {
							Vec3 p = points.get(i);
							EpicParticles.add(EpicParticles.dust(mark.rgb, 3.0F), p.x, p.y, p.z, 0.0D, 0.0D, 0.0D, true);
						}
					}
				}
				default -> {
				}
			}
		}
	}

	private static void waveFront(ClientLevel level, Mark mark, int tick, RandomSource random) {
		if (tick > WAVE_TICKS) {
			return;
		}
		float front = mark.size * ease(Math.min(1.0F, tick / (float) WAVE_TICKS));
		// a front wider than render distance: only the arc the camera can see (Tingkat Titah)
		double middle = 0.0D;
		double spread = Math.PI;
		if (front > 48.0F) {
			Vec3 camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position();
			double[] arc = new double[2];
			if (!DampakWorldBudget.visibleArc(mark.at.x, mark.at.z, front, camera.x, camera.z, Math.max(48.0D, TitahWorld.viewRange() - 32.0D), arc)) {
				return;
			}
			middle = arc[0];
			spread = arc[1];
		}
		int wanted = DampakWorldBudget.scaled(DampakWorldBudget.RING_DUST[DampakWorldBudget.index()], true, 48);
		int n = EpicParticles.grant(wanted, false);
		for (int i = 0; i < n; i++) {
			double angle = middle + (random.nextDouble() * 2.0D - 1.0D) * spread;
			double x = mark.at.x + Math.cos(angle) * front;
			double z = mark.at.z + Math.sin(angle) * front;
			double y = DampakWorld.titahGround(level, x, z, mark.at.y);
			ParticleOptions particle = ParticleTypes.POOF;
			if (mark.leaves) {
				// the plant standing on the column (short grass, flowers), else the ground itself
				BlockState plant = level.getBlockState(BlockPos.containing(x, y + 0.1D, z));
				BlockState ground = level.getBlockState(BlockPos.containing(x, y - 0.5D, z));
				if (!plant.isAir() && plant.getFluidState().isEmpty()) {
					particle = new BlockParticleOption(ParticleTypes.BLOCK, plant);
				} else if (!ground.isAir()) {
					particle = i % 3 == 0 ? ParticleTypes.PALE_OAK_LEAVES : new BlockParticleOption(ParticleTypes.BLOCK, ground);
				}
			} else {
				BlockState ground = level.getBlockState(BlockPos.containing(x, y - 0.5D, z));
				if (!ground.isAir()) {
					particle = new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground);
				}
			}
			double speed = 0.3D + random.nextDouble() * 0.2D;
			EpicParticles.add(particle, x, y + 0.2D, z, Math.cos(angle) * speed, 0.1D + random.nextDouble() * 0.15D, Math.sin(angle) * speed);
		}
	}

	private static float ease(float t) {
		float u = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
		return 1.0F - u * u * u;
	}

	// ================================================================== render

	static void render(SigilRenderer.Frame frame) {
		if (MARKS.isEmpty() || DampakWorldBudget.tier() < 1) {
			return;
		}
		double range = TitahWorld.viewRange() + 160.0D;
		for (int i = 0; i < MARKS.size(); i++) {
			Mark mark = MARKS.get(i);
			// a flash is at full strength from its first frame and shrinks by itself
			float a = mark.kind == FLASH ? 1.0F : mark.alpha(frame.clock);
			// stars and the Dipper are drawn at any distance, pulled inside render distance (Tingkat Titah)
			boolean sky = mark.projected && (mark.kind == STAR || mark.kind == DIPPER);
			if (a <= 0.01F || !sky && frame.dist2(mark.at.x, mark.at.y, mark.at.z) > range * range) {
				continue;
			}
			switch (mark.kind) {
				case GATE -> drawGate(frame, mark, a);
				case STAR -> {
					// a far star impact flares at once and shrinks with its fade
					float size = mark.farImpact ? mark.size * (0.6F + 0.8F * a) : mark.size;
					drawStar(frame, mark.at, size, mark.rgb, a, frame.clock + mark.cast % 97, mark.projected);
				}
				case DIPPER -> drawDipper(frame, mark, a);
				case FLASH -> drawFlash(frame, mark);
				default -> {
				}
			}
		}
	}

	/** The gate tear: a turning gold annulus, a soft white-gold disc inside it and eight rays tearing outward. */
	private static void drawGate(SigilRenderer.Frame frame, Mark mark, float a) {
		int segments = DampakWorldBudget.tier() >= 2 ? 64 : 40;
		if (!TitahWorld.quads(segments + 9) || mark.size > 16.0F && !EpicBudget.takeLarge()) {
			return;
		}
		double age = frame.clock - mark.born;
		float open = ease((float) Math.min(1.0D, age / 20.0D));
		float r = mark.size * (0.3F + 0.7F * open);
		float x = frame.rx(mark.at.x);
		float y = frame.ry(mark.at.y);
		float z = frame.rz(mark.at.z);
		float turn = (float) (frame.clock * 0.004D);
		frame.flatRing(frame.ring, x, y, z, r * 0.55F, r, segments, TitahWorld.argb(0.35F * a, mark.rgb2), TitahWorld.argb(0.95F * a, mark.rgb), turn);
		frame.flat(frame.ground, x, y - 0.2F, z, r * 0.62F, turn * 3.0F, TitahWorld.argb(0.75F * a * open, mark.rgb2));
		for (int k = 0; k < 8; k++) {
			double angle = Math.PI * 2.0D * k / 8.0D + frame.clock * 0.003D;
			float len = r * (1.25F + 0.2F * (float) Math.sin(frame.clock * 0.2D + k * 1.7D));
			float ex = x + (float) Math.cos(angle) * len;
			float ez = z + (float) Math.sin(angle) * len;
			float sx = x + (float) Math.cos(angle) * r * 0.9F;
			float sz = z + (float) Math.sin(angle) * r * 0.9F;
			frame.beam(frame.glow, sx, y, sz, ex, y, ez, Math.max(1.0F, r * 0.08F), TitahWorld.argb(0.8F * a, mark.rgb), TitahWorld.argb(0.0F, mark.rgb));
		}
	}

	/** One star: a soft halo, a white core and a four-point flare, twinkling. */
	private static void drawStar(SigilRenderer.Frame frame, Vec3 at, float size, int rgb, float a, double phase, boolean projected) {
		if (!TitahWorld.quads(4)) {
			return;
		}
		float x = frame.rx(at.x);
		float y = frame.ry(at.y);
		float z = frame.rz(at.z);
		if (projected) {
			float[] p = new float[3];
			float k = TitahWorld.project(frame, at.x, at.y, at.z, p);
			x = p[0];
			y = p[1];
			z = p[2];
			size *= k;
		}
		float twinkle = 0.85F + 0.15F * (float) Math.sin(phase * 0.35D);
		frame.billboard(frame.ground, x, y, z, size * twinkle, TitahWorld.argb(0.8F * a, rgb));
		frame.billboard(frame.ground, x, y, z, size * 0.35F, TitahWorld.argb(a, 0xFFFFFF));
		float flare = size * 2.6F * twinkle;
		frame.beam(frame.lightning, x - flare, y, z, x + flare, y, z, Math.max(0.15F, size * 0.08F), TitahWorld.argb(0.9F * a, rgb),
				TitahWorld.argb(0.9F * a, rgb));
		frame.beam(frame.lightning, x, y - flare, z, x, y + flare, z, Math.max(0.15F, size * 0.08F), TitahWorld.argb(0.9F * a, rgb),
				TitahWorld.argb(0.9F * a, rgb));
	}

	/** The Dipper: seven stars joined in order by faint lines; a star flares when its node lights, then stays bright. */
	private static void drawDipper(SigilRenderer.Frame frame, Mark mark, float a) {
		int n = Math.min(mark.stars.size(), mark.lit.length);
		for (int k = 0; k + 1 < n; k++) {
			if (!TitahWorld.quads(1)) {
				return;
			}
			Vec3 s0 = mark.stars.get(k);
			Vec3 s1 = mark.stars.get(k + 1);
			boolean lit = !Double.isNaN(mark.lit[k + 1]);
			int c = TitahWorld.argb((lit ? 0.6F : 0.18F) * a, lit ? mark.rgb2 : mark.rgb);
			if (mark.projected) {
				float[] p0 = new float[3];
				float[] p1 = new float[3];
				float k0 = TitahWorld.project(frame, s0.x, s0.y, s0.z, p0);
				float k1 = TitahWorld.project(frame, s1.x, s1.y, s1.z, p1);
				frame.beam(frame.glow, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], 1.2F * mark.size * (k0 + k1) * 0.5F, c, c);
			} else {
				frame.beam(frame.glow, frame.rx(s0.x), frame.ry(s0.y), frame.rz(s0.z), frame.rx(s1.x), frame.ry(s1.y), frame.rz(s1.z), 1.2F, c, c);
			}
		}
		for (int k = 0; k < n; k++) {
			double litAt = mark.lit[k];
			float size = 2.2F * mark.size;
			float glow = 0.55F;
			if (!Double.isNaN(litAt)) {
				float t = (float) Math.min(1.0D, Math.max(0.0D, (frame.clock - litAt) / 8.0D));
				size = (2.6F + 3.0F * (1.0F - t)) * mark.size;
				glow = 1.0F;
			}
			drawStar(frame, mark.stars.get(k), size, k == n - 1 ? TitahWorld.mix(mark.rgb, mark.rgb2, 0.4F) : mark.rgb, a * glow, frame.clock + k * 13.0D,
					mark.projected);
		}
	}

	/** A light pillar (beacon beam) with a soft base, shrinking over its life. */
	private static void drawFlash(SigilRenderer.Frame frame, Mark mark) {
		double life = mark.until + FADE - mark.born;
		double age = frame.clock - mark.born;
		float k = (float) Math.max(0.0D, 1.0D - age / Math.max(1.0D, life));
		if (k <= 0.02F) {
			return;
		}
		int height = Math.max(8, mark.rgb2);
		frame.pillar(mark.at.x, mark.at.y, mark.at.z, height, mark.rgb, Math.max(0.1F, mark.size * k));
		if (TitahWorld.quads(1)) {
			frame.billboard(frame.ground, frame.rx(mark.at.x), frame.ry(mark.at.y + 1.0D), frame.rz(mark.at.z), 6.0F * mark.size * k,
					TitahWorld.argb(0.85F * k, mark.rgb));
		}
	}

	private SkyMarks() {
	}
}
