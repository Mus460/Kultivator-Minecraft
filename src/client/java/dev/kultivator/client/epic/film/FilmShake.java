package dev.kultivator.client.epic.film;

import dev.kultivator.client.EffectQuality;
import net.minecraft.client.Minecraft;

/**
 * Frequency-shaped camera shake of the film (DESIGN 30.2.10): deterministic sums of sines with seeded phases under an
 * attack/decay envelope, added after the path and before the collision clip. Amplitude ≤ 0.35 b and ≤ 1.5° at full,
 * × "Guncangan layar" ({@code screenEffectScale}); "Kurangi gerak" → none.
 */
public final class FilmShake {

	public enum Profile {
		/** 5.0, 7.3 and 11.1 Hz, falling weights: build-ups, vortices, engines. */
		RUMBLE(new float[]{5.0F, 7.3F, 11.1F}, new float[]{1.0F, 0.55F, 0.3F}),
		/** a 2.2 Hz sway + 3.7 Hz + 14 Hz grit: stomps, mountains, the crater. */
		QUAKE(new float[]{2.2F, 3.7F, 14.0F}, new float[]{1.0F, 0.6F, 0.18F}),
		/** a damped 9 Hz spring (decay 6 ticks): single impacts. */
		JOLT(new float[]{9.0F}, new float[]{1.0F}),
		/** 25 Hz jitter, tiny: energy hums, the time stop. */
		HUM(new float[]{25.0F, 31.0F}, new float[]{0.5F, 0.3F});

		final float[] hz;
		final float[] weight;

		Profile(float[] hz, float[] weight) {
			this.hz = hz;
			this.weight = weight;
		}
	}

	public static final float MAX_OFFSET = 0.35F;
	public static final float MAX_DEGREES = 1.5F;

	/**
	 * The shake at {@code age} ticks of a {@code ticks}-long shake of {@code amp} (0..1): writes x, y, z (blocks) and
	 * yaw, pitch (degrees) into {@code out}. Zero outside the window.
	 */
	public static void sample(Profile profile, float amp, float age, int ticks, long seed, float[] out) {
		for (int i = 0; i < 5; i++) {
			out[i] = 0.0F;
		}
		if (profile == null || amp <= 0.0F || age < 0.0F || age > ticks || EffectQuality.reduceMotion()) {
			return;
		}
		float effect = (float) (double) Minecraft.getInstance().options.screenEffectScale().get();
		if (effect <= 0.0F) {
			return;
		}
		float envelope;
		if (profile == Profile.JOLT) {
			envelope = (float) Math.exp(-age / 6.0D);
		} else {
			float attack = Math.min(1.0F, age / 3.0F);
			float decay = Math.min(1.0F, (ticks - age) / Math.max(1.0F, ticks * 0.4F));
			envelope = attack * decay;
		}
		float a = Math.min(1.0F, amp) * envelope * effect;
		double seconds = age / 20.0D;
		for (int axis = 0; axis < 5; axis++) {
			double sum = 0.0D;
			double norm = 0.0D;
			for (int k = 0; k < profile.hz.length; k++) {
				double phase = ((seed >>> (axis * 7 + k * 3)) & 0xFF) / 255.0D * Math.PI * 2.0D;
				sum += Math.sin(seconds * profile.hz[k] * Math.PI * 2.0D + phase) * profile.weight[k];
				norm += profile.weight[k];
			}
			float v = (float) (sum / Math.max(1.0E-3D, norm));
			out[axis] = axis < 3 ? v * MAX_OFFSET * a : v * MAX_DEGREES * a;
		}
	}

	private FilmShake() {
	}
}
