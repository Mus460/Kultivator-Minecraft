package dev.kultivator.client.aura;

/**
 * Deterministic hash noise for the Aura Kultivasi (DESIGN 31.2.2): the same entity, tick and salt always give the same
 * value, so an aura looks the same every time it is shown. No {@code RandomSource} is used for any shape or particle
 * position of the aura.
 */
public final class AuraRng {

	/** A value in 0..1 (exclusive of 1) for this entity, tick and salt. */
	public static float of(int entityId, long tick, int salt) {
		long z = tick * 0x9E3779B97F4A7C15L + entityId * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L + 0x27D4EB2F165667C5L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z = z ^ (z >>> 31);
		return (z >>> 40) / (float) (1L << 24);
	}

	/** A value in −1..1. */
	public static float signed(int entityId, long tick, int salt) {
		return of(entityId, tick, salt) * 2.0F - 1.0F;
	}

	private AuraRng() {
	}
}
