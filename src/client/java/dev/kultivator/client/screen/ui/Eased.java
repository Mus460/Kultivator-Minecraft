package dev.kultivator.client.screen.ui;

/**
 * A value that glides towards its target instead of jumping (bars, glows, slides). Frame-rate independent:
 * {@code rate} is how quickly it closes the gap per second.
 */
public final class Eased {

	private final float rate;
	private float value = Float.NaN;

	public Eased(float rate) {
		this.rate = rate;
	}

	/** Moves towards {@code target} for a frame of {@code seconds}; the first call jumps straight there. */
	public float to(float target, float seconds) {
		if (Float.isNaN(value) || Float.isNaN(target) || Float.isInfinite(target)) {
			value = Float.isNaN(target) || Float.isInfinite(target) ? 0.0F : target;
			return value;
		}
		float k = 1.0F - (float) Math.exp(-rate * Math.max(0.0F, seconds));
		value += (target - value) * k;
		if (Math.abs(target - value) < 1.0E-4F) {
			value = target;
		}
		return value;
	}

	/** Like {@link #to} but starting from {@code start} instead of jumping on the first call. */
	public float to(float target, float seconds, float start) {
		if (Float.isNaN(value)) {
			value = start;
		}
		return to(target, seconds);
	}

	public float get() {
		return Float.isNaN(value) ? 0.0F : value;
	}

	public void set(float newValue) {
		value = newValue;
	}

	/** 0..1 ease-out (fast start, soft landing). */
	public static float out(float t) {
		float u = 1.0F - Math.max(0.0F, Math.min(1.0F, t));
		return 1.0F - u * u * u;
	}

	/** 0..1 ease-in-out. */
	public static float inOut(float t) {
		float k = Math.max(0.0F, Math.min(1.0F, t));
		return k * k * (3.0F - 2.0F * k);
	}
}
