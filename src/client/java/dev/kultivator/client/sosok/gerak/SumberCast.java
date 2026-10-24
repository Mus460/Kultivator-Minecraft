package dev.kultivator.client.sosok.gerak;

import dev.kultivator.client.anim.PoseAngles;
import dev.kultivator.client.anim.PoseLibrary;
import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.List;

/**
 * Bakes the CAST source of a clip (DESIGN 40.5.5): the cast poses of DESIGN 27 ({@code PoseLibrary.sample}, the pure
 * sampler {@code CastPoses.sample} wraps) laid out as timed segments, one frame per tick, so playing the clip costs a
 * table lookup. It never calls {@code CastPoses.start}: that is the real cast path with priority 2, untouched. A
 * segment starts on the pose's windup key, strikes in its first 2/7 and holds the rest with the pose's own breathing; the
 * first 4 ticks of a segment ease from where the previous one ended so the palms flow from form to form.
 */
public final class SumberCast {

	private static final int SILANG = 4;

	/** Frames {@code (durasi + 1) x 24}, NaN where {@code mask} does not reach. */
	static float[] bakar(List<Klip.SegCast> segmen, int durasi, long mask) {
		int n = durasi + 1;
		float[] out = new float[n * Klip.KANAL];
		Arrays.fill(out, Float.NaN);
		float[] a = new float[Klip.KANAL];
		float[] b = new float[Klip.KANAL];
		for (int t = 0; t < n; t++) {
			int i = segmenPada(segmen, t);
			Klip.SegCast s = segmen.get(i);
			sampel(s, Math.max(s.t0(), Math.min(t, s.t1())), a);
			int masuk = t - s.t0();
			if (masuk >= 0 && masuk < SILANG && segmen.size() > 1) {
				Klip.SegCast lalu = segmen.get((i - 1 + segmen.size()) % segmen.size());
				sampel(lalu, lalu.t1(), b);
				float k = Easing.SINE_IO.f(masuk / (float) SILANG);
				for (int c = 0; c < Klip.KANAL; c++) {
					a[c] = Mth.lerp(k, b[c], a[c]);
				}
			}
			for (int c = 0; c < Klip.KANAL_POSE; c++) {
				if ((mask & Klip.bit(c)) != 0) {
					out[t * Klip.KANAL + c] = a[c];
				}
			}
		}
		return out;
	}

	private static int segmenPada(List<Klip.SegCast> segmen, int t) {
		int best = 0;
		for (int i = 0; i < segmen.size(); i++) {
			if (segmen.get(i).t0() <= t) {
				best = i;
			}
		}
		return best;
	}

	/** One cast pose at clip tick {@code t} into 24 channels (undriven ones 0). */
	private static void sampel(Klip.SegCast s, int t, float[] out) {
		Arrays.fill(out, 0.0F);
		float span = Math.max(1, s.t1() - s.t0());
		float p = Mth.clamp((t - s.t0()) / span, 0.0F, 1.0F);
		PoseAngles a = PoseLibrary.sample(s.pose(), 0.3F + 0.7F * p, t);
		out[Klip.HX] = a.headX();
		out[Klip.HY] = a.headY();
		out[Klip.LEAN] = a.bodyX();
		out[Klip.TWIST] = a.bodyY();
		out[Klip.RAX] = a.rArmX();
		out[Klip.RAY] = a.rArmY();
		out[Klip.RAZ] = a.rArmZ();
		out[Klip.LAX] = a.lArmX();
		out[Klip.LAY] = a.lArmY();
		out[Klip.LAZ] = a.lArmZ();
		out[Klip.RLX] = a.rLegX();
		out[Klip.RLZ] = a.rLegZ();
		out[Klip.LLX] = a.lLegX();
		out[Klip.LLZ] = a.lLegZ();
		out[Klip.LIFT] = a.lift();
		if (s.cermin()) {
			cermin(out);
		}
	}

	/** Right and left swapped (arm and leg turns mirrored, head and twist negated). */
	static void cermin(float[] v) {
		tukar(v, Klip.RAX, Klip.LAX);
		tukar(v, Klip.RAY, Klip.LAY);
		tukar(v, Klip.RAZ, Klip.LAZ);
		tukar(v, Klip.RLX, Klip.LLX);
		tukar(v, Klip.RLZ, Klip.LLZ);
		tukar(v, Klip.RLY, Klip.LLY);
		v[Klip.RAY] = -v[Klip.RAY];
		v[Klip.LAY] = -v[Klip.LAY];
		v[Klip.RAZ] = -v[Klip.RAZ];
		v[Klip.LAZ] = -v[Klip.LAZ];
		v[Klip.RLZ] = -v[Klip.RLZ];
		v[Klip.LLZ] = -v[Klip.LLZ];
		v[Klip.RLY] = -v[Klip.RLY];
		v[Klip.LLY] = -v[Klip.LLY];
		v[Klip.HY] = -v[Klip.HY];
		v[Klip.TWIST] = -v[Klip.TWIST];
	}

	private static void tukar(float[] v, int a, int b) {
		float x = v[a];
		v[a] = v[b];
		v[b] = x;
	}

	private SumberCast() {
	}
}
