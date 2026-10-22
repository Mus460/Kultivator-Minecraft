package dev.kultivator.client.duel.fx;

import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The red ropes of the duel ring (DESIGN 35.2.3): one rope from each post to the next, hanging in a parabola that sags
 * 0.25 b at the middle, drawn as a camera-facing strip of 8 segments in the world's light with {@code rope.png} tiled
 * along it (red tint). A big impact makes every rope tremble (up to 0.15 b, a standing wave that dies over 10
 * presentation ticks, so a hit-stop freezes it mid-swing), and while a duellist stands outside the ring a red emissive
 * copy pulses over the ropes (the rope "glows red").
 */
final class DuelRopeRenderer {

	static final int PIECES = 8;
	static final double SAG = 0.25D;
	static final float WIDTH = 0.07F;
	static final int RED = 0xB0201C;

	/** The point at {@code s} (0..1) along the rope from post {@code span} to the next, at presentation time {@code time}. */
	static Vec3 ropePoint(DuelRingVisual.Ring ring, int span, double s, double time, float tremble) {
		int n = ring.posts.size();
		if (n == 0) {
			return ring.center;
		}
		Vec3 a = ring.posts.get(Math.floorMod(span, n)).add(0.0D, DuelRingVisual.ROPE_HEIGHT, 0.0D);
		Vec3 b = ring.posts.get(Math.floorMod(span + 1, n)).add(0.0D, DuelRingVisual.ROPE_HEIGHT, 0.0D);
		Vec3 p = a.lerp(b, s);
		double hang = 4.0D * SAG * s * (1.0D - s);
		double wave = tremble * Math.sin(Math.PI * s) * Math.sin(time * 2.4D + span * 1.3D);
		// the tremble swings the rope in and out of the ring as well as up and down
		Vec3 inward = ring.center.subtract(p);
		inward = new Vec3(inward.x, 0.0D, inward.z);
		inward = inward.lengthSqr() < 1.0E-6D ? Vec3.ZERO : inward.normalize();
		return p.add(inward.scale(wave * 0.6D)).add(0.0D, -hang + wave * 0.8D, 0.0D);
	}

	static void render(DuelGeo frame, DuelRingVisual.Ring ring) {
		int n = ring.posts.size();
		if (n < 2 || ring.built < n || !frame.take(n * PIECES * 2)) {
			return;
		}
		float tremble = DuelRingVisual.currentTremble(ring, frame.time);
		float pulse = 0.5F + 0.5F * Mth.sin((float) frame.time * 0.6F);
		int colour = DuelGeo.argb(ring.alpha, RED);
		int glow = DuelGeo.argb((0.35F + 0.5F * pulse) * ring.alpha, 0xFF3A30);
		for (int span = 0; span < n; span++) {
			Vec3 prev = ropePoint(ring, span, 0.0D, frame.time, tremble);
			double length = prev.distanceTo(ropePoint(ring, span, 1.0D, frame.time, tremble));
			float uScale = (float) (length / 0.5D) / PIECES;
			for (int i = 1; i <= PIECES; i++) {
				Vec3 next = ropePoint(ring, span, i / (double) PIECES, frame.time, tremble);
				float u0 = (i - 1) * uScale;
				float u1 = i * uScale;
				frame.segment(frame.rope, prev, next, WIDTH, WIDTH, colour, colour, u0, u1, ring.light);
				if (ring.outside) {
					frame.segment(frame.ropeGlow, prev, next, WIDTH * 1.9F, WIDTH * 1.9F, glow, glow, u0, u1, LightCoordsUtil.FULL_BRIGHT);
				}
				prev = next;
			}
		}
	}

	private DuelRopeRenderer() {
	}
}
