package dev.kultivator.client.epic.film;

/**
 * The eases of a film segment (DESIGN 30.2.5). Each maps the segment's time fraction to its path fraction, so a key's
 * ease reparameterises time: {@link #HOLD} creeps, then rockets (the speed ramp); {@link #BACK_OUT} overshoots by at
 * most 6 % and settles.
 */
public enum Ease {
	LINEAR, IN, OUT, IN_OUT, EXPO_IN, EXPO_OUT, BACK_OUT, HOLD;

	public float apply(float t) {
		float x = t < 0.0F ? 0.0F : t > 1.0F ? 1.0F : t;
		return switch (this) {
			case LINEAR -> x;
			case IN -> (float) (1.0D - Math.cos(x * Math.PI * 0.5D));
			case OUT -> (float) Math.sin(x * Math.PI * 0.5D);
			case IN_OUT -> (float) ((1.0D - Math.cos(Math.PI * x)) * 0.5D);
			case EXPO_IN -> x <= 0.0F ? 0.0F : (float) Math.pow(2.0D, 10.0D * x - 10.0D);
			case EXPO_OUT -> x >= 1.0F ? 1.0F : (float) (1.0D - Math.pow(2.0D, -10.0D * x));
			case BACK_OUT -> {
				// c1 = 0.9 keeps the overshoot near 6 %
				double c1 = 0.9D;
				double c3 = c1 + 1.0D;
				double u = x - 1.0D;
				yield (float) (1.0D + c3 * u * u * u + c1 * u * u);
			}
			case HOLD -> x * x * x * x;
		};
	}
}
