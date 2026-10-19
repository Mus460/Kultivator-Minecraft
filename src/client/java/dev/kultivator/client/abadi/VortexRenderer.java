package dev.kultivator.client.abadi;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.CommonFx;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The black hole of the ascension (DESIGN 26.3.3 step 4; RMJ ch. 2451: "a giant black hole appears inside the five-coloured
 * cloud"): a slowly turning disc of dark motes and five-coloured sparks 14 blocks above the start point, with streamers falling
 * toward it, and the 8-block "stay under the vortex" circle on the ground. Particles only, scaled by Effect Quality and
 * thinned beyond 48 blocks; drawn while the server's {@code VORTEX} packets keep it alive.
 */
public final class VortexRenderer {

	private static final int[] FIVE = {0xFF5A5A, 0xFFD54A, 0x5CCB4A, 0x47B2FF, 0xB98CFF};
	private static final int DARK = 0x1A0A2A;

	private static @Nullable Vec3 centre;
	private static @Nullable Vec3 base;
	private static float radius = 8.0F;
	private static long until;
	private static long ticks;

	/** Starts (or keeps alive) the vortex at {@code centre} over {@code base} with the ground circle {@code radius}. */
	static void start(Vec3 vortex, Vec3 ground, float circle, int duration) {
		centre = vortex;
		base = ground;
		radius = Math.max(1.0F, circle);
		until = ticks + Math.max(20, duration);
	}

	static void stop() {
		centre = null;
		base = null;
	}

	static boolean active() {
		return centre != null;
	}

	/** Every client tick. */
	static void tick() {
		ticks++;
		Vec3 at = centre;
		Vec3 ground = base;
		if (at == null || ground == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null || ticks > until) {
			stop();
			return;
		}
		boolean far = minecraft.player.position().distanceToSqr(at) > 48.0D * 48.0D;
		if (ticks % (far ? 4L : 2L) != 0L) {
			return;
		}
		RandomSource random = CommonFx.random();
		int points = EffectQuality.scale(far ? 10 : 22);
		double spin = ticks * 0.09D;
		for (int i = 0; i < points; i++) {
			double angle = spin + Math.PI * 2.0D * i / points;
			double r = 1.0D + (i % 5) * 1.1D;
			Vec3 p = at.add(Math.cos(angle) * r, (random.nextFloat() - 0.5F) * 0.4D, Math.sin(angle) * r);
			Vec3 inward = at.subtract(p).scale(0.05D).add(-Math.sin(angle) * 0.08D, 0.0D, Math.cos(angle) * 0.08D);
			CommonFx.add(CommonFx.dust(i % 3 == 0 ? FIVE[(i / 3) % FIVE.length] : DARK, i % 3 == 0 ? 1.2F : 1.8F), p, inward, false);
		}
		if (random.nextInt(3) == 0) {
			CommonFx.add(ParticleTypes.REVERSE_PORTAL, at.x + random.nextGaussian() * 2.0D, at.y - 2.0D - random.nextDouble() * 6.0D,
					at.z + random.nextGaussian() * 2.0D, 0.0D, 0.15D, 0.0D);
		}
		if (ticks % 10L == 0L) {
			CommonFx.ring(ground.add(0.0D, 0.1D, 0.0D), 0xB98CFF, radius, EffectQuality.scale(far ? 16 : 40));
		}
	}

	private VortexRenderer() {
	}
}
