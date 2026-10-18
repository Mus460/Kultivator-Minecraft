package dev.kultivator.client.epic.render;

/**
 * Sky projection (DESIGN 23.10.3): a far vertex P is drawn at {@code cam + d × (R_draw / |d|)} with {@code d = P − cam}
 * and {@code R_draw = clamp(0.7 × fog start, 24, 96)}, so its angular shape is exact while the shaders' render-distance
 * fog (about 192 b at render distance 12) would otherwise erase it. Projected parts show only above 20° elevation and fade
 * to alpha 0 between 20° and 15°, so they never paint over distant hills. Works in camera-relative coordinates; the
 * result is written into a reused array (no allocation).
 */
public final class SkyProjection {

	public static final float MIN_ELEVATION = 15.0F;
	public static final float FULL_ELEVATION = 20.0F;

	/** Projects the camera-relative point into {@code out[0..2]} and returns its elevation alpha (0..1). */
	public static float project(float x, float y, float z, float drawRadius, float[] out) {
		float length = (float) Math.sqrt(x * x + y * y + z * z);
		if (length < 0.001F) {
			out[0] = 0.0F;
			out[1] = drawRadius;
			out[2] = 0.0F;
			return 1.0F;
		}
		float k = drawRadius / length;
		out[0] = x * k;
		out[1] = y * k;
		out[2] = z * k;
		float elevation = (float) Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, y / length))));
		return Math.max(0.0F, Math.min(1.0F, (elevation - MIN_ELEVATION) / (FULL_ELEVATION - MIN_ELEVATION)));
	}

	/** {@code R_draw} for a fog start distance. */
	public static float drawRadius(float fogStart) {
		return Math.max(24.0F, Math.min(96.0F, 0.7F * fogStart));
	}

	private SkyProjection() {
	}
}
