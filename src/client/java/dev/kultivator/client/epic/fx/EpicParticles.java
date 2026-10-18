package dev.kultivator.client.epic.fx;

import dev.kultivator.client.epic.EpicClock;
import dev.kultivator.client.fx.spec.FxBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 7b's own particle cues (DESIGN 23.11.2, 23.14.1): every count goes through the shared {@link FxBudget} as DETAIL or
 * EXTRA (never CORE), and velocities are × the {@link EpicClock} σ with the normal lifetime, so slow motion costs no
 * extra tokens. Also the particle fallbacks of the geometry layers (rings, columns, helices, outlines, bolts).
 */
public final class EpicParticles {

	private static final RandomSource RANDOM = RandomSource.create();

	/** Particles granted out of {@code wanted} (each costs about one token: lifetimes ≤ 20-39 ticks). */
	public static int grant(int wanted, boolean extra) {
		if (wanted <= 0) {
			return 0;
		}
		return FxBudget.take(wanted, extra ? FxBudget.Priority.EXTRA : FxBudget.Priority.DETAIL);
	}

	public static RandomSource random() {
		return RANDOM;
	}

	public static DustParticleOptions dust(int rgb, float scale) {
		return new DustParticleOptions(rgb & 0xFFFFFF, Mth.clamp(scale, 0.05F, 4.0F));
	}

	public static BlockParticleOption block(BlockState state) {
		return new BlockParticleOption(ParticleTypes.BLOCK, state);
	}

	/** One particle; velocity × σ. {@code far} shows it beyond the vanilla particle distance (sky-scale cues). */
	public static void add(ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz, boolean far) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		float sigma = EpicClock.sigma();
		level.addParticle(particle, far, far, x, y, z, vx * sigma, vy * sigma, vz * sigma);
	}

	public static void add(ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
		add(particle, x, y, z, vx, vy, vz, false);
	}

	/** A horizontal ring of {@code n} particles. */
	public static void ring(Vec3 c, float r, int n, ParticleOptions particle, float outward) {
		n = grant(n, false);
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / Math.max(1, n) + RANDOM.nextDouble() * 0.1D;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			add(particle, c.x + cos * r, c.y + 0.05D, c.z + sin * r, cos * outward, 0.0D, sin * outward, r > 16.0F);
		}
	}

	/** A rising column of {@code n} particles of radius {@code r}. */
	public static void column(Vec3 base, float height, float r, int n, ParticleOptions particle, float rise) {
		n = grant(n, false);
		for (int i = 0; i < n; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
			double rr = r * Math.sqrt(RANDOM.nextDouble());
			add(particle, base.x + Math.cos(angle) * rr, base.y + RANDOM.nextDouble() * height, base.z + Math.sin(angle) * rr, 0.0D, rise, 0.0D,
					height > 24.0F);
		}
	}

	/** A spiral of particles climbing around an axis (fire tornadoes, aurora fallback). */
	public static void helix(Vec3 base, float height, float r, int n, double turn, ParticleOptions particle) {
		n = grant(n, true);
		for (int i = 0; i < n; i++) {
			double t = RANDOM.nextDouble();
			double angle = turn + t * Math.PI * 6.0D;
			add(particle, base.x + Math.cos(angle) * r, base.y + t * height, base.z + Math.sin(angle) * r, 0.0D, 0.02D, 0.0D, height > 24.0F);
		}
	}

	/** Particles drifting inward to {@code c} from radius {@code r} (motes, convergence). */
	public static void converge(Vec3 c, float r, int n, ParticleOptions particle, float speed, boolean extra) {
		n = grant(n, extra);
		for (int i = 0; i < n; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
			double y = (RANDOM.nextDouble() - 0.3D) * 2.0D;
			double x = Math.cos(angle) * r;
			double z = Math.sin(angle) * r;
			double k = speed / Math.max(0.5D, Math.sqrt(x * x + y * y + z * z));
			add(particle, c.x + x, c.y + y, c.z + z, -x * k, -y * k, -z * k, r > 20.0F);
		}
	}

	/** A spherical burst from {@code c}. */
	public static void burst(Vec3 c, int n, float speed, ParticleOptions particle, boolean extra) {
		n = grant(n, extra);
		for (int i = 0; i < n; i++) {
			double vx = RANDOM.nextGaussian();
			double vy = RANDOM.nextGaussian();
			double vz = RANDOM.nextGaussian();
			double k = speed / Math.max(0.01D, Math.sqrt(vx * vx + vy * vy + vz * vz));
			add(particle, c.x, c.y, c.z, vx * k, vy * k, vz * k);
		}
	}

	/** A spherical shell of particles of radius {@code r} (heaven and earth answer). */
	public static void shell(Vec3 c, float r, int n, ParticleOptions particle) {
		n = grant(n, true);
		for (int i = 0; i < n; i++) {
			double vx = RANDOM.nextGaussian();
			double vy = RANDOM.nextGaussian();
			double vz = RANDOM.nextGaussian();
			double k = r / Math.max(0.01D, Math.sqrt(vx * vx + vy * vy + vz * vz));
			add(particle, c.x + vx * k, c.y + vy * k, c.z + vz * k, 0.0D, 0.0D, 0.0D, r > 20.0F);
		}
	}

	/** Light falling inside radius {@code r} from {@code height} above (END_ROD ≤ 15 %, the rest gold dust). */
	public static void rain(Vec3 c, float r, float height, int n, int rgb) {
		n = grant(n, false);
		DustParticleOptions dust = dust(rgb, 1.2F);
		for (int i = 0; i < n; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
			double rr = r * Math.sqrt(RANDOM.nextDouble());
			ParticleOptions particle = i % 7 == 0 ? ParticleTypes.END_ROD : dust;
			add(particle, c.x + Math.cos(angle) * rr, c.y + height * (0.4D + RANDOM.nextDouble() * 0.6D), c.z + Math.sin(angle) * rr, 0.0D, -0.12D,
					0.0D, height > 24.0F);
		}
	}

	/** A dust crown of the ground block's own particles rising around {@code c}. */
	public static void dustCrown(Vec3 c, float r, int n) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		BlockState ground = level.getBlockState(BlockPos.containing(c.x, c.y - 0.5D, c.z));
		if (ground.isAir()) {
			ground = level.getBlockState(BlockPos.containing(c.x, c.y - 1.5D, c.z));
		}
		if (ground.isAir()) {
			return;
		}
		BlockParticleOption particle = block(ground);
		n = grant(n, false);
		for (int i = 0; i < n; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
			double rr = r * (0.6D + RANDOM.nextDouble() * 0.4D);
			add(particle, c.x + Math.cos(angle) * rr, c.y + 0.1D, c.z + Math.sin(angle) * rr, Math.cos(angle) * 0.15D, 0.35D + RANDOM.nextDouble() * 0.3D,
					Math.sin(angle) * 0.15D);
		}
	}

	/** A jagged particle bolt (the geometry fallback; the existing particle style). */
	public static void bolt(Vec3 from, Vec3 to, int rgb, int segments) {
		int n = grant(segments * 6, false);
		if (n <= 0) {
			return;
		}
		DustParticleOptions dust = dust(rgb, 1.3F);
		Vec3 previous = from;
		Vec3 delta = to.subtract(from);
		int per = Math.max(1, n / Math.max(1, segments));
		for (int i = 1; i <= segments; i++) {
			double t = (double) i / segments;
			double jitter = i == segments ? 0.0D : 1.2D;
			Vec3 next = from.add(delta.scale(t)).add((RANDOM.nextDouble() - 0.5D) * jitter * 2.0D, 0.0D, (RANDOM.nextDouble() - 0.5D) * jitter * 2.0D);
			for (int k = 0; k < per; k++) {
				Vec3 at = previous.add(next.subtract(previous).scale(k / (double) per));
				add(dust, at.x, at.y, at.z, 0.0D, 0.0D, 0.0D, true);
			}
			previous = next;
		}
		add(ParticleTypes.ELECTRIC_SPARK, to.x, to.y + 0.2D, to.z, 0.0D, 0.2D, 0.0D);
	}

	/** A flat point cloud placed in a vertical plane facing {@code side} (outlines, silhouettes). */
	public static void outline(float[] points, Vec3 base, Vec3 side, double height, int n, ParticleOptions particle, boolean far) {
		int count = points.length / 2;
		if (count == 0) {
			return;
		}
		n = grant(n, true);
		for (int i = 0; i < n; i++) {
			int p = RANDOM.nextInt(count);
			double x = points[p * 2] * height * 0.5D;
			double y = (points[p * 2 + 1] + 1.0D) * height * 0.5D;
			add(particle, base.x + side.x * x, base.y + y, base.z + side.z * x, 0.0D, 0.0D, 0.0D, far);
		}
	}

	private EpicParticles() {
	}
}
