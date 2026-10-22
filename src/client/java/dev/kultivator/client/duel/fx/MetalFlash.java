package dev.kultivator.client.duel.fx;

import dev.kultivator.client.epic.lens.LensLayers;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The metal flash of a blade contact (DESIGN 35.4.3): a four-branch star of additive light turning 20° a tick at the
 * contact, white at first and cooling into the spark colour, over a soft glow disc; size per result (0.4 b for a block to
 * 1.4 b for a deathblow), 3 ticks (5 for a perfect deflect). It ages by the presentation clock, so a hit-stop holds the
 * flash at its brightest. Also drives the 6-tick lens flare of one's own perfect deflect (35.4.3, Maximal only).
 */
public final class MetalFlash {

	private static final int CAP = 24;

	private record Flash(Vec3 at, float size, int rgb, float life, double born, float spin) {
	}

	private static final List<Flash> LIVE = new ArrayList<>();
	private static long lensUntil = -1L;

	/** A flash of {@code size} blocks at {@code at}, cooling to {@code rgb}, for {@code life} presentation ticks. */
	public static void add(Vec3 at, float size, int rgb, int life) {
		if (LIVE.size() >= CAP) {
			LIVE.remove(0);
		}
		float spin = (float) ((at.x * 13.0D + at.z * 7.0D) % (Math.PI * 2.0D));
		LIVE.add(new Flash(at, size, rgb & 0xFFFFFF, Math.max(1, life), DuelTime.now(), spin));
	}

	/** The lens flare of a perfect deflect ends at client tick {@code tick}. */
	static void lensUntil(long tick) {
		lensUntil = tick;
	}

	static void tick() {
		if (lensUntil >= 0L && DuelFx.ticks() >= lensUntil) {
			lensUntil = -1L;
			LensLayers.off();
		}
		if (LIVE.isEmpty()) {
			return;
		}
		double now = DuelTime.now();
		LIVE.removeIf(flash -> now - flash.born > flash.life + 1.0D);
	}

	static void render(DuelGeo frame) {
		if (LIVE.isEmpty()) {
			return;
		}
		for (Iterator<Flash> it = LIVE.iterator(); it.hasNext(); ) {
			Flash flash = it.next();
			float age = (float) (frame.time - flash.born);
			if (age < 0.0F || age > flash.life) {
				continue;
			}
			if (frame.dist2(flash.at) > 96.0D * 96.0D || !frame.take(10)) {
				continue;
			}
			float t = age / flash.life;
			// a sharp pop: full size within the first fifth, then shrinking and cooling
			float grow = t < 0.2F ? 0.55F + 0.45F * (t / 0.2F) : 1.0F - 0.45F * (t - 0.2F) / 0.8F;
			float alpha = t < 0.2F ? 1.0F : 1.0F - (t - 0.2F) / 0.8F;
			int colour = DuelGeo.mix(0xFFFFFF, flash.rgb, t * 1.2F);
			float roll = flash.spin + (float) Math.toRadians(20.0D) * age;
			frame.billboard(frame.glow, flash.at, flash.size * 0.55F * grow, 0.0F, DuelGeo.argb(0.7F * alpha, colour));
			frame.star(flash.at, flash.size * grow, roll, colour, alpha);
		}
	}

	static boolean idle() {
		return LIVE.isEmpty();
	}

	static void clear() {
		LIVE.clear();
		if (lensUntil >= 0L) {
			lensUntil = -1L;
			LensLayers.off();
		}
	}

	private MetalFlash() {
	}
}
