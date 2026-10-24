package dev.kultivator.client.sosok.gerak;

import net.minecraft.util.Mth;

/**
 * The easing of a key (DESIGN 40.5.3): how a channel travels from the previous key to this one. {@code x} runs 0..1
 * over the segment; the result is the share of the way already covered (BACK_OUT and ELASTIS overshoot past 1).
 */
public enum Easing {
	/** Constant speed. */
	LINEAR,
	/** {@code -(cos(pi x) - 1) / 2}: the default, soft in and out. */
	SINE_IO,
	/** {@code 1 - (1 - x)^3}: fast start, soft landing (raising a hand). */
	CUBIC_OUT,
	/** {@code x^3}: slow start, hard landing (a falling hammer). */
	CUBIC_IN,
	/** Overshoot 1.08 then settle (blows, stamps). */
	BACK_OUT,
	/** A springy overshoot (ribbons, claps). */
	ELASTIS,
	/** No interpolation: the previous value is held until the key's tick (blinks, taps, hand seals). */
	TANGGA;

	private static final float BACK = 1.08F;

	/** The eased share for {@code x} (clamped to 0..1). */
	public float f(float x) {
		float t = x <= 0.0F ? 0.0F : Math.min(x, 1.0F);
		return switch (this) {
			case LINEAR -> t;
			case SINE_IO -> -(Mth.cos(Mth.PI * t) - 1.0F) * 0.5F;
			case CUBIC_OUT -> {
				float u = 1.0F - t;
				yield 1.0F - u * u * u;
			}
			case CUBIC_IN -> t * t * t;
			case BACK_OUT -> {
				float u = t - 1.0F;
				yield 1.0F + (BACK + 1.0F) * u * u * u + BACK * u * u;
			}
			case ELASTIS -> {
				if (t <= 0.0F || t >= 1.0F) {
					yield t;
				}
				yield (float) (Math.pow(2.0D, -10.0D * t) * Math.sin((10.0D * t - 0.75D) * (2.0D * Math.PI / 3.0D)) + 1.0D);
			}
			case TANGGA -> t >= 1.0F ? 1.0F : 0.0F;
		};
	}
}
