package dev.kultivator.client.sosok.gerak;

import dev.kultivator.client.render.MeditationPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

import java.util.Arrays;

/**
 * Samples a {@link Klip} at a clip time into 24 channel values (DESIGN 40.5.3): per channel the keys that write it are
 * interpolated with the entering key's easing (a loop runs from its last key back to the first over the rest of the
 * cycle), baked CAST/DUEL frames are read with linear interpolation between ticks, then the waves and hold modifiers are
 * added. Channels the clip does not drive come out NaN. Pure and allocation free (client thread scratch only).
 * <p>
 * {@link #terapkan} puts a sampled clip straight onto a model with a blend weight: the duty poses
 * ({@code DutyPoses}) use it for the four Tugas Sekte poses and the autonomous activities 40-56.
 */
public final class Sampler {

	private static final float[] SCRATCH = new float[Klip.KANAL];

	/**
	 * Samples {@code k} at {@code t} ticks into {@code out} (length 24). {@code amp} scales the angle channels (age
	 * variants 40.5.6: children 1.15, elders' arms 0.8); {@code benih} seeds the tremble noise.
	 */
	public static void sampel(Klip k, float t, float[] out, float amp, int benih) {
		Arrays.fill(out, Float.NaN);
		if (k.sumber() == Klip.Sumber.FUNGSI) {
			return;
		}
		int dur = Math.max(1, k.durasi());
		float tt;
		if (k.loop()) {
			tt = t % dur;
			if (tt < 0.0F) {
				tt += dur;
			}
		} else {
			tt = Mth.clamp(t, 0.0F, dur);
		}
		long mask = k.mask();
		float[] bakar = k.bakar();
		for (int c = 0; c < Klip.KANAL; c++) {
			if ((mask & Klip.bit(c)) == 0) {
				continue;
			}
			Klip.Jalur j = k.jalur()[c];
			float v;
			if (j != null) {
				v = jalur(j, tt, dur, k.loop());
			} else if (bakar != null && (k.maskBakar() & Klip.bit(c)) != 0) {
				v = bakar(bakar, c, tt, dur, k.loop());
			} else {
				v = 0.0F;
			}
			out[c] = v;
		}
		// waves of a loop run on the unwrapped time, so periods that do not divide the cycle never jump
		float tw = k.loop() ? t : tt;
		for (Osilator o : k.osc()) {
			int c = o.kanal();
			if (!Float.isNaN(out[c])) {
				out[c] += o.nilai(tw);
			}
		}
		mods(k, tt, dur, out, benih);
		if (amp != 1.0F) {
			for (int c = 0; c < Klip.KANAL; c++) {
				if (!Float.isNaN(out[c]) && Klip.sudut(c) && c != Klip.HX && c != Klip.HY) {
					out[c] *= amp;
				}
			}
		}
	}

	/** One channel's keyed value at {@code t}. */
	static float jalur(Klip.Jalur j, float t, int dur, boolean loop) {
		int[] ts = j.t();
		float[] vs = j.v();
		Easing[] es = j.e();
		int n = ts.length;
		if (n == 1) {
			return vs[0];
		}
		int first = ts[0];
		int last = ts[n - 1];
		if (t < first || t >= last) {
			if (!loop) {
				return t < first ? vs[0] : vs[n - 1];
			}
			float span = dur - last + first;
			if (span <= 0.0F) {
				return vs[0];
			}
			float x = (t >= last ? t - last : t + dur - last) / span;
			return Mth.lerp(es[0].f(x), vs[n - 1], vs[0]);
		}
		for (int i = 0; i < n - 1; i++) {
			if (t < ts[i + 1]) {
				float seg = ts[i + 1] - ts[i];
				float x = seg <= 0.0F ? 1.0F : (t - ts[i]) / seg;
				return Mth.lerp(es[i + 1].f(x), vs[i], vs[i + 1]);
			}
		}
		return vs[n - 1];
	}

	private static float bakar(float[] frames, int c, float t, int dur, boolean loop) {
		int a = (int) Math.floor(t);
		float f = t - a;
		int b = a + 1;
		if (b > dur) {
			b = loop ? 0 : dur;
		}
		a = Math.min(a, dur);
		float va = frames[a * Klip.KANAL + c];
		float vb = frames[b * Klip.KANAL + c];
		if (Float.isNaN(va)) {
			return Float.isNaN(vb) ? 0.0F : vb;
		}
		return Float.isNaN(vb) ? va : va + (vb - va) * f;
	}

	/** The hold modifiers (40.5.3), in stored units. */
	private static void mods(Klip k, float t, int dur, float[] out, int benih) {
		int m = k.mods();
		if (m == 0) {
			return;
		}
		if ((m & Klip.MOD_NAPAS_X) != 0) {
			tambah(out, Klip.HX, Mth.sin(t * Mth.TWO_PI / 80.0F) * Mth.DEG_TO_RAD);
		}
		float gemetar = 0.0F;
		if ((m & Klip.MOD_GEMETAR) != 0) {
			gemetar = k.modAmp();
		}
		if ((m & Klip.MOD_GUNCANG_MUATAN) != 0) {
			gemetar = Math.max(gemetar, k.modAmp() * Mth.clamp(t / dur, 0.0F, 1.0F));
		}
		if (gemetar > 0.0F) {
			float g = gemetar * Mth.DEG_TO_RAD;
			float s = benih * 0.37F;
			tambah(out, Klip.RAX, g * gelombang(t, s));
			tambah(out, Klip.RAZ, g * gelombang(t, s + 1.9F));
			tambah(out, Klip.LAX, g * gelombang(t, s + 3.7F));
			tambah(out, Klip.LAZ, g * gelombang(t, s + 5.3F));
			tambah(out, Klip.HX, 0.5F * g * gelombang(t, s + 7.1F));
		}
		if ((m & Klip.MOD_DETAK) != 0) {
			float p = Math.max(0.0F, Mth.sin(t * Mth.TWO_PI / 8.0F));
			tambah(out, Klip.BAHU, 0.4F * p * p * p * p);
		}
		if ((m & Klip.MOD_GELENG) != 0) {
			tambah(out, Klip.HY, 12.0F * Mth.DEG_TO_RAD * Mth.sin(t * Mth.TWO_PI / 10.0F));
		}
		if ((m & Klip.MOD_ANGKAT_TUMBUH) != 0) {
			tambah(out, Klip.LIFT, k.modAmp() * Mth.clamp(t / dur, 0.0F, 1.0F));
		}
	}

	/** Deterministic high-frequency noise -1..1. */
	private static float gelombang(float t, float s) {
		return (Mth.sin(t * 1.7F + s) + Mth.sin(t * 2.9F + s * 1.3F) + Mth.sin(t * 4.3F + s * 0.7F)) / 3.0F;
	}

	private static void tambah(float[] out, int c, float v) {
		out[c] = Float.isNaN(out[c]) ? v : out[c] + v;
	}

	// ================================================================== direct application (duty poses)

	/**
	 * Samples clip {@code klip} (variant {@code varian}, chosen with the body's traits when the render state carries
	 * Gerak data) at {@code t} and lays it over the model with weight {@code s}: arms and legs blend to the absolute
	 * angles (legs only while standing, or always for {@code KAKI_PAKSA}), head channels add, lean and twist bend the body
	 * about the hips with the shoulders and head following, hip sway rolls the body. Unknown clips do nothing.
	 */
	public static void terapkan(PlayerModel model, AvatarRenderState state, int klip, int varian, float s, float t) {
		Klip dasar = GerakKatalog.klip(klip);
		if (dasar == null || s <= 0.0F) {
			return;
		}
		GerakData data = GerakData.dari(state);
		Klip k = data != null ? dasar.pilih(varian, data.ciri, data.sifatBit) : dasar.pilih(varian, 0, 0);
		float amp = data != null ? data.skalaAmp : 1.0F;
		float waktu = data != null ? t * data.skalaWaktu : t;
		sampel(k, waktu, SCRATCH, amp, state.id);
		boolean jalan = state.walkAnimationSpeed > 0.1F && !k.punya(Klip.KAKI_PAKSA);
		GerakApplier.letakkan(model, SCRATCH, s, jalan, MeditationPose.strength(state) > 0.5F);
	}

	private Sampler() {
	}
}
