package dev.kultivator.client.duel.fx;

import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Shock waves of the duel (DESIGN 35.4.5): a flat ring on the ground in the spark colour (0.5 → 4.5 b in 8 ticks) and a
 * vertical ring standing across the duel line at the contact (0.3 → 2.2 b in 6 ticks), for a heavy perfect deflect, a won
 * blade lock, a guard break, a posture break and a deathblow. Each ring is a textured annulus ({@code shock_ring.png})
 * with an eased growth, a bright front and a soft wake, plus a faint second echo; it ages by the presentation clock.
 * Caps 1 / 3 / 6 live rings (35.4.12; the oldest leaves first). The ring's ropes tremble with every ground ring.
 */
public final class ShockRing {

	private record Ring(Vec3 at, Vec3 u, Vec3 v, float r0, float r1, int rgb, float life, double born, boolean ground) {
	}

	private static final List<Ring> LIVE = new ArrayList<>();
	private static final Vec3 X = new Vec3(1.0D, 0.0D, 0.0D);
	private static final Vec3 Z = new Vec3(0.0D, 0.0D, 1.0D);
	private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

	/** A flat ring on the ground at {@code at} (its y is the ground). */
	public static void ground(Vec3 at, int rgb, float r0, float r1, int life) {
		add(new Ring(at.add(0.0D, 0.03D, 0.0D), X, Z, r0, r1, rgb & 0xFFFFFF, Math.max(2, life), DuelTime.now(), true));
		DuelRingVisual.tremble(at, 0.15F);
	}

	/** A vertical ring at {@code at} facing along {@code line} (the duel line), so it stands across it. */
	public static void vertical(Vec3 at, Vec3 line, int rgb, float r0, float r1, int life) {
		Vec3 axis = new Vec3(line.x, 0.0D, line.z);
		axis = axis.lengthSqr() < 1.0E-6D ? X : axis.normalize();
		Vec3 side = axis.cross(UP).normalize();
		add(new Ring(at, side, UP, r0, r1, rgb & 0xFFFFFF, Math.max(2, life), DuelTime.now(), false));
	}

	private static void add(Ring ring) {
		int cap = DuelFxSettings.pick(1, 3, 6);
		while (LIVE.size() >= cap && !LIVE.isEmpty()) {
			LIVE.remove(0);
		}
		LIVE.add(ring);
	}

	static void tick() {
		if (LIVE.isEmpty()) {
			return;
		}
		double now = DuelTime.now();
		LIVE.removeIf(ring -> now - ring.born > ring.life + 4.0D);
	}

	static void render(DuelGeo frame) {
		if (LIVE.isEmpty()) {
			return;
		}
		for (Ring ring : LIVE) {
			float age = (float) (frame.time - ring.born);
			if (age < 0.0F || age > ring.life + 4.0F || frame.dist2(ring.at) > 96.0D * 96.0D || !frame.take(12)) {
				continue;
			}
			float p = Math.min(1.0F, age / ring.life);
			float eased = 1.0F - (1.0F - p) * (1.0F - p) * (1.0F - p);
			float r = ring.r0 + (ring.r1 - ring.r0) * eased;
			float fade = age <= ring.life ? 1.0F - p * p : 0.0F;
			if (fade <= 0.01F) {
				continue;
			}
			int colour = DuelGeo.mix(0xFFFFFF, ring.rgb, Math.min(1.0F, p * 1.6F));
			int light = LightCoordsUtil.FULL_BRIGHT;
			frame.plane(frame.shock, ring.at, ring.u, ring.v, r, r, DuelGeo.argb(0.9F * fade, colour), light, true);
			// a fainter echo lagging behind the front
			float echo = ring.r0 + (ring.r1 - ring.r0) * eased * 0.7F;
			frame.plane(frame.shock, ring.at, ring.u, ring.v, echo, echo, DuelGeo.argb(0.35F * fade, ring.rgb), light, true);
			if (ring.ground && p < 0.35F) {
				// the glow of the impact on the ground at its start
				frame.plane(frame.glow, ring.at.add(0.0D, 0.01D, 0.0D), ring.u, ring.v, 1.2F, 1.2F, DuelGeo.argb(0.5F * (1.0F - p / 0.35F), ring.rgb),
						light, true);
			}
		}
	}

	static boolean idle() {
		return LIVE.isEmpty();
	}

	static void clear() {
		LIVE.clear();
	}

	private ShockRing() {
	}
}
