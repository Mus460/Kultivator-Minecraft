package dev.kultivator.client.purba;

import dev.kultivator.client.fx.spec.PointClouds;

/**
 * The Taotie mask of the Zhou bronze vessels (DESIGN 26.4.2; Lüshi Chunqiu 16/3a: a head without a body) as polylines in a
 * unit box, sampled once through {@link PointClouds#sample} and cached: the brow and horns, two great round eyes, the
 * snout ridge, the cheek scrolls and the open jaw with its fangs. Also the relic shapes (a wind eye and a candle flame).
 */
public final class TaotieMask {

	private static float[] mask;
	private static float[] windEye;
	private static float[] candle;

	/** The mask as x0, y0, x1, y1, ... (never modify the array). */
	public static float[] mask() {
		if (mask == null) {
			mask = PointClouds.sample(join(new float[][]{
							// brow and the two curling horns
							{-0.95F, 0.55F, -0.7F, 0.85F, -0.45F, 0.95F, -0.2F, 0.8F, 0.0F, 0.62F, 0.2F, 0.8F, 0.45F, 0.95F, 0.7F, 0.85F, 0.95F, 0.55F},
							{-0.95F, 0.55F, -1.0F, 0.85F, -0.85F, 1.0F, -0.7F, 0.9F},
							{0.95F, 0.55F, 1.0F, 0.85F, 0.85F, 1.0F, 0.7F, 0.9F},
							// the snout ridge
							{0.0F, 0.62F, 0.0F, -0.1F},
							{-0.12F, -0.1F, 0.12F, -0.1F, 0.18F, -0.25F, -0.18F, -0.25F, -0.12F, -0.1F},
							// the cheek scrolls
							{-0.95F, 0.55F, -1.0F, 0.1F, -0.85F, -0.25F, -0.6F, -0.2F, -0.55F, 0.0F, -0.7F, 0.05F},
							{0.95F, 0.55F, 1.0F, 0.1F, 0.85F, -0.25F, 0.6F, -0.2F, 0.55F, 0.0F, 0.7F, 0.05F},
							// the open jaw and its fangs
							{-0.85F, -0.3F, -0.6F, -0.75F, -0.2F, -0.95F, 0.2F, -0.95F, 0.6F, -0.75F, 0.85F, -0.3F},
							{-0.6F, -0.4F, 0.6F, -0.4F},
							{-0.45F, -0.4F, -0.38F, -0.62F, -0.3F, -0.4F},
							{0.3F, -0.4F, 0.38F, -0.62F, 0.45F, -0.4F},
							{-0.15F, -0.4F, -0.08F, -0.55F, 0.0F, -0.4F, 0.08F, -0.55F, 0.15F, -0.4F}},
					circle(-0.45F, 0.3F, 0.22F, 18), circle(0.45F, 0.3F, 0.22F, 18), circle(-0.45F, 0.3F, 0.08F, 8), circle(0.45F, 0.3F, 0.08F, 8)));
		}
		return mask;
	}

	/** Mata Angin: a ring with a three-armed swirl. */
	public static float[] windEye() {
		if (windEye == null) {
			windEye = PointClouds.sample(join(new float[][]{
							{0.0F, 0.0F, 0.3F, 0.25F, 0.55F, 0.1F, 0.7F, -0.2F},
							{0.0F, 0.0F, -0.37F, 0.13F, -0.36F, 0.42F, -0.18F, 0.7F},
							{0.0F, 0.0F, 0.07F, -0.38F, -0.2F, -0.52F, -0.52F, -0.5F}},
					circle(0.0F, 0.0F, 0.85F, 28)));
		}
		return windEye;
	}

	/** The Lilin: a candle with its teardrop flame. */
	public static float[] candle() {
		if (candle == null) {
			candle = PointClouds.sample(new float[][]{
					{-0.2F, -1.0F, 0.2F, -1.0F, 0.2F, -0.1F, -0.2F, -0.1F, -0.2F, -1.0F},
					{0.0F, -0.1F, 0.0F, 0.0F},
					{0.0F, 0.0F, 0.22F, 0.25F, 0.25F, 0.5F, 0.12F, 0.75F, 0.0F, 1.0F, -0.12F, 0.75F, -0.25F, 0.5F, -0.22F, 0.25F, 0.0F, 0.0F},
					{0.0F, 0.2F, 0.1F, 0.4F, 0.0F, 0.62F, -0.1F, 0.4F, 0.0F, 0.2F}});
		}
		return candle;
	}

	/** A closed circle polyline with {@code segments} sides. */
	static float[] circle(float cx, float cy, float r, int segments) {
		float[] line = new float[(segments + 1) * 2];
		for (int i = 0; i <= segments; i++) {
			double a = Math.PI * 2.0D * i / segments;
			line[i * 2] = cx + (float) Math.cos(a) * r;
			line[i * 2 + 1] = cy + (float) Math.sin(a) * r;
		}
		return line;
	}

	/** {@code lines} followed by the single polylines {@code more}. */
	static float[][] join(float[][] lines, float[]... more) {
		float[][] all = new float[lines.length + more.length][];
		System.arraycopy(lines, 0, all, 0, lines.length);
		System.arraycopy(more, 0, all, lines.length, more.length);
		return all;
	}

	private TaotieMask() {
	}
}
