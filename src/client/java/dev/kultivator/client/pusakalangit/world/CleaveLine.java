package dev.kultivator.client.pusakalangit.world;

import dev.kultivator.client.dampak.world.DampakWorld;
import dev.kultivator.client.dampak.world.DampakWorldBudget;
import dev.kultivator.client.dampak.world.SigilRenderer;
import dev.kultivator.client.epic.fx.EpicParticles;
import dev.kultivator.client.fx.spec.CameraShake;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Bumi terbelah (DESIGN 29.5.1, EXTRA3 of Pedang Pemisah Langit): from the planted sword a cleaving front runs both ways
 * along the aim at 8 b/t (× Kecepatan), opening a glowing fissure that follows the terrain: a crack decal strip, a hot
 * bloom strip and a white-gold core line, sampled every {@code max(1, L / 48)} b; dust pillars and sparks burst at the
 * fronts and visual debris (Mode Kehancuran off) is thrown out of the line. The fissure cools from white-gold through red
 * to a dark ember over 30 s. Visual only: Mode Kehancuran carves the real fissures on the server.
 */
final class CleaveLine {

	private static final int COOL = 600;
	private static final int REFRESH = 10;
	private static final int MAX = 6;

	private static final class Line {
		final long cast;
		final Vec3 origin;
		final double ux;
		final double uz;
		final float half;
		final float speed;
		final int rgb;
		final int hot;
		final boolean own;
		final boolean kehancuran;
		final boolean lowLoad;
		final double start;
		final double step;
		final int samples;
		/** Ground heights of the samples from −samples to +samples (index + samples). */
		final float[] heights;
		final float width;
		int refreshIn;
		int lastTick = -1;

		Line(long cast, Vec3 origin, Vec3 dir, float half, float speed, int rgb, int hot, boolean own, boolean kehancuran, boolean lowLoad, double start) {
			this.cast = cast;
			this.origin = origin;
			this.ux = dir.x;
			this.uz = dir.z;
			this.half = half;
			this.speed = Math.max(1.0F, speed);
			this.rgb = rgb;
			this.hot = hot;
			this.own = own;
			this.kehancuran = kehancuran;
			this.lowLoad = lowLoad;
			this.start = start;
			this.step = Math.max(1.0D, half / 48.0D);
			this.samples = Math.max(1, (int) Math.ceil(half / step));
			this.heights = new float[samples * 2 + 1];
			this.width = Math.max(0.6F, Math.min(2.5F, half * 0.03F));
		}

		float front(double now) {
			return (float) Math.min(half, Math.max(0.0D, (now - start) * speed));
		}

		double travel() {
			return half / speed;
		}

		double end() {
			return start + travel() + COOL;
		}

		double x(int i) {
			return origin.x + ux * i * step;
		}

		double z(int i) {
			return origin.z + uz * i * step;
		}
	}

	private static final List<Line> LINES = new ArrayList<>();

	// ================================================================== API

	static void start(ClientLevel level, long cast, Vec3 origin, Vec3 dir, float half, float speed, int rgb, int hot, boolean own, boolean kehancuran,
					  boolean lowLoad) {
		if (!TitahWorld.finite(origin) || !(half > 1.0F)) {
			return;
		}
		Vec3 unit = new Vec3(dir.x, 0.0D, dir.z);
		unit = unit.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : unit.normalize();
		if (LINES.size() >= MAX) {
			LINES.remove(0);
		}
		Line line = new Line(cast, origin, unit, half, speed, rgb, hot, own, kehancuran, lowLoad, TitahWorld.clock());
		refresh(level, line);
		LINES.add(line);
		TitahWorld.touch();
		if (own) {
			CameraShake.add(Math.min(0.5F, 0.35F * DampakWorldBudget.power()), (int) Math.min(40.0D, line.travel() + 6.0D), origin, half, false);
		}
	}

	static void abort(long cast) {
		LINES.removeIf(line -> line.cast == cast);
	}

	static int count() {
		return LINES.size();
	}

	static void clear() {
		LINES.clear();
	}

	// ================================================================== tick: heights, the fronts' dust, sparks and debris

	static void tick(ClientLevel level, double now) {
		if (LINES.isEmpty()) {
			return;
		}
		RandomSource random = EpicParticles.random();
		for (Iterator<Line> it = LINES.iterator(); it.hasNext(); ) {
			Line line = it.next();
			if (now > line.end()) {
				it.remove();
				continue;
			}
			if (--line.refreshIn <= 0) {
				refresh(level, line);
			}
			int tick = (int) (now - line.start);
			if (tick == line.lastTick) {
				continue;
			}
			line.lastTick = tick;
			float front = line.front(now);
			boolean running = front < line.half;
			if (running || tick <= line.travel() + 1) {
				for (int side = -1; side <= 1; side += 2) {
					double fx = line.origin.x + line.ux * front * side;
					double fz = line.origin.z + line.uz * front * side;
					double fy = DampakWorld.titahGround(level, fx, fz, line.origin.y);
					BlockState ground = level.getBlockState(BlockPos.containing(fx, fy - 0.5D, fz));
					int n = EpicParticles.grant(DampakWorldBudget.scaled(6, line.own, 10), false);
					for (int i = 0; i < n; i++) {
						boolean dust = i % 2 == 0 && !ground.isAir();
						EpicParticles.add(dust ? new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground) : ParticleTypes.END_ROD,
								fx + random.nextGaussian() * 0.6D, fy + 0.2D, fz + random.nextGaussian() * 0.6D, random.nextGaussian() * 0.08D,
								0.25D + random.nextDouble() * 0.3D, random.nextGaussian() * 0.08D);
					}
					if (!line.kehancuran && tick % 2 == 0) {
						// the WAKE: chunks of the land thrown out of the opening fissure
						DampakWorld.titahDebris(new Vec3(fx, fy, fz), 2.0F, 2, false, null, line.cast, line.own, line.lowLoad);
					}
				}
			} else if (tick % 4 == 0 && now < line.start + line.travel() + COOL * 0.5D) {
				// the cooling fissure smokes and spits embers
				int n = EpicParticles.grant(DampakWorldBudget.scaled(3, line.own, 6), false);
				for (int i = 0; i < n; i++) {
					int s = random.nextInt(line.heights.length) - line.samples;
					EpicParticles.add(i % 2 == 0 ? ParticleTypes.SMOKE : ParticleTypes.LAVA, line.x(s), line.heights[s + line.samples] + 0.1D, line.z(s), 0.0D,
							0.05D, 0.0D);
				}
			}
		}
	}

	private static void refresh(ClientLevel level, Line line) {
		line.refreshIn = REFRESH;
		for (int i = -line.samples; i <= line.samples; i++) {
			line.heights[i + line.samples] = (float) (DampakWorld.titahGround(level, line.x(i), line.z(i), line.origin.y) + SigilRenderer.LIFT);
		}
	}

	// ================================================================== render

	static void render(SigilRenderer.Frame frame) {
		if (LINES.isEmpty() || DampakWorldBudget.tier() < 1) {
			return;
		}
		double range = TitahWorld.viewRange();
		for (int l = 0; l < LINES.size(); l++) {
			Line line = LINES.get(l);
			if (frame.dist2(line.origin.x, line.origin.y, line.origin.z) > (range + line.half) * (range + line.half)) {
				continue;
			}
			float front = line.front(frame.clock);
			int reach = Math.min(line.samples, (int) Math.ceil(front / line.step));
			if (reach <= 0) {
				continue;
			}
			if (!TitahWorld.quads(reach * 2 * 3 + 2)) {
				return;
			}
			// heat: white-gold while the front runs, then red, then a dark ember fading out
			double after = frame.clock - (line.start + line.travel());
			float cool = after <= 0.0D ? 0.0F : (float) Math.min(1.0D, after / COOL);
			int core = cool < 0.15F ? TitahWorld.mix(0xFFFFFF, line.rgb, cool / 0.15F) : TitahWorld.mix(line.rgb, line.hot, Math.min(1.0F, (cool - 0.15F) / 0.35F));
			int bloom = TitahWorld.mix(line.rgb, line.hot, Math.min(1.0F, cool * 2.0F));
			float alpha = 1.0F - cool * cool;
			int coreColor = TitahWorld.argb(0.95F * alpha, core);
			int bloomColor = TitahWorld.argb(0.35F * alpha, bloom);
			int crackColor = TitahWorld.argb(0.9F * alpha, TitahWorld.mix(bloom, 0x2A0A04, cool));
			float w = line.width;
			for (int side = -1; side <= 1; side += 2) {
				for (int k = 0; k < reach; k++) {
					int i0 = k * side;
					int i1 = (k + 1) * side;
					if (Math.abs(i1) > line.samples) {
						break;
					}
					// the last segment ends at the front itself
					double f1 = Math.min(front, (k + 1) * line.step);
					double x1 = line.origin.x + line.ux * f1 * side;
					double z1 = line.origin.z + line.uz * f1 * side;
					float ax = frame.rx(line.x(i0));
					float ay = frame.ry(line.heights[i0 + line.samples]);
					float az = frame.rz(line.z(i0));
					float bx = frame.rx(x1);
					float by = frame.ry(line.heights[i1 + line.samples]);
					float bz = frame.rz(z1);
					// narrower towards the ends of the line
					float taper = 1.0F - 0.6F * (float) (k * line.step / line.half);
					frame.groundStrip(frame.crack, ax, ay, az, bx, by, bz, w * 2.2F * taper, crackColor, crackColor, 0.0F, 1.0F);
					frame.groundStrip(frame.glow, ax, ay + 0.01F, az, bx, by + 0.01F, bz, w * 5.0F * taper, bloomColor, bloomColor, 0.0F, 1.0F);
					frame.groundStrip(frame.lightning, ax, ay + 0.02F, az, bx, by + 0.02F, bz, w * 0.35F * taper, coreColor, coreColor, 0.0F, 1.0F);
				}
				if (front < line.half) {
					// the front's flare
					double fx = line.origin.x + line.ux * front * side;
					double fz = line.origin.z + line.uz * front * side;
					int idx = Math.min(line.samples, (int) Math.round(front / line.step)) * side + line.samples;
					frame.billboard(frame.ground, frame.rx(fx), frame.ry(line.heights[idx] + 1.0D), frame.rz(fz), 3.0F + w, TitahWorld.argb(0.9F, line.rgb));
				}
			}
		}
	}

	private CleaveLine() {
	}
}
