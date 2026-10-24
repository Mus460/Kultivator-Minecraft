package dev.kultivator.client.sosok.gerak;

import net.minecraft.util.Mth;

/**
 * A wave added to one channel after the keys are interpolated (DESIGN 40.5.3): {@code amp} in the channel's stored
 * unit (radians for angles after {@link Klip.Pembangun#bangun()}), {@code periode} in ticks, {@code fase} in radians.
 */
public record Osilator(int kanal, float amp, float periode, float fase, Bentuk bentuk) {

	/** The wave shapes. */
	public enum Bentuk {
		SIN,
		COS,
		/** {@code max(0, sin)}: a push that rests half the time (throwing feed). */
		SIN_POSITIF,
		/** {@code (0.5 + 0.5 sin)^3}: a slow rise and a quick fall (the hammer). */
		SIN_KUBIK,
		/** A triangle wave -1..1. */
		SEGITIGA,
		/** {@code 0.5 + 0.5 sin}: 0..1 (a pour that tips and returns). */
		SIN_SETENGAH
	}

	/** The wave's value at clip time {@code t} (ticks). */
	public float nilai(float t) {
		float p = periode <= 0.0F ? 0.0F : t / periode * Mth.TWO_PI + fase;
		float w = switch (bentuk) {
			case SIN -> Mth.sin(p);
			case COS -> Mth.cos(p);
			case SIN_POSITIF -> Math.max(0.0F, Mth.sin(p));
			case SIN_KUBIK -> {
				float h = 0.5F + 0.5F * Mth.sin(p);
				yield h * h * h;
			}
			case SEGITIGA -> {
				float u = p / Mth.TWO_PI;
				u -= (float) Math.floor(u);
				yield u < 0.5F ? 4.0F * u - 1.0F : 3.0F - 4.0F * u;
			}
			case SIN_SETENGAH -> 0.5F + 0.5F * Mth.sin(p);
		};
		return amp * w;
	}

	/** The same wave with the amplitude multiplied (unit conversion, age scaling). */
	Osilator kali(float k) {
		return new Osilator(kanal, amp * k, periode, fase, bentuk);
	}
}
