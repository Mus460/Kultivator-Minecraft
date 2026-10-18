package dev.kultivator.client.epic.fx;

/**
 * Flat point clouds of the 7b figures (DESIGN 23.10.4 F_SOUL, F_ORB, F_SILHOUETTE outlines): polylines in a unit box
 * (x right, y up, about −1..1) sampled once into evenly spaced points and cached, so cues never allocate per frame.
 */
public final class PointClouds {

	private static final float STEP = 0.04F;

	private static float[] humanoid;
	private static float[] infant;
	private static float[] crane;
	private static float[] ape;
	private static float[] deity;
	private static float[] tails;
	private static float[] lotus;
	private static float[] gate;

	/** A standing figure (feet at y −1, head at 0.85). */
	public static float[] humanoid() {
		if (humanoid == null) {
			humanoid = sample(join(circle(0.0F, 0.84F, 0.13F, 14), new float[][]{
					{0.0F, 0.7F, 0.0F, 0.05F}, {-0.32F, 0.6F, 0.32F, 0.6F},
					{-0.32F, 0.6F, -0.42F, 0.2F, -0.38F, -0.15F}, {0.32F, 0.6F, 0.42F, 0.2F, 0.38F, -0.15F},
					{-0.2F, 0.05F, 0.2F, 0.05F}, {-0.2F, 0.05F, -0.2F, -1.0F}, {0.2F, 0.05F, 0.2F, -1.0F},
					{-0.22F, 0.6F, -0.2F, 0.05F}, {0.22F, 0.6F, 0.2F, 0.05F}}));
		}
		return humanoid;
	}

	/** A seated infant with crossed legs (the Nascent Soul, B3). */
	public static float[] infant() {
		if (infant == null) {
			infant = sample(join(circle(0.0F, 0.55F, 0.3F, 20), ellipse(0.0F, -0.15F, 0.38F, 0.42F, 22), new float[][]{
					{-0.85F, -0.75F, -0.3F, -0.55F, 0.3F, -0.55F, 0.85F, -0.75F},
					{-0.85F, -0.75F, -0.2F, -0.95F, 0.2F, -0.95F, 0.85F, -0.75F},
					{-0.35F, 0.05F, -0.45F, -0.35F, -0.1F, -0.45F}, {0.35F, 0.05F, 0.45F, -0.35F, 0.1F, -0.45F}}));
		}
		return infant;
	}

	/** A crane in flight (Pengakuan Langit, recruitment day, Istana Langit). */
	public static float[] crane() {
		if (crane == null) {
			crane = sample(join(circle(0.62F, 0.18F, 0.06F, 8), new float[][]{
					{-0.6F, 0.0F, 0.55F, 0.12F}, {0.55F, 0.12F, 0.82F, 0.14F},
					{0.0F, 0.06F, -0.25F, 0.55F, -0.6F, 0.9F, -0.75F, 0.95F}, {0.0F, 0.06F, 0.2F, 0.5F, 0.0F, 0.95F, -0.2F, 1.0F},
					{-0.1F, 0.0F, -0.35F, -0.4F, -0.7F, -0.7F}, {-0.6F, 0.0F, -1.0F, -0.08F}, {-0.6F, 0.0F, -1.0F, 0.04F}}));
		}
		return crane;
	}

	/** A leaping ape (the life-saving hair, F2). */
	public static float[] ape() {
		if (ape == null) {
			ape = sample(join(circle(0.0F, 0.72F, 0.2F, 14), new float[][]{
					{-0.6F, 0.48F, 0.6F, 0.48F}, {-0.6F, 0.48F, -0.75F, 0.0F, -0.8F, -0.55F}, {0.6F, 0.48F, 0.75F, 0.9F, 0.8F, 1.0F},
					{-0.4F, 0.48F, -0.3F, -0.25F, 0.3F, -0.25F, 0.4F, 0.48F}, {-0.3F, -0.25F, -0.6F, -1.0F}, {0.3F, -0.25F, 0.6F, -0.9F},
					{0.3F, -0.2F, 0.7F, -0.1F, 0.95F, 0.3F}}));
		}
		return ape;
	}

	/** A thunder deity with a halo and raised arms (A1 silhouettes). */
	public static float[] deity() {
		if (deity == null) {
			deity = sample(join(circle(0.0F, 0.72F, 0.15F, 14), circle(0.0F, 0.74F, 0.32F, 22), new float[][]{
					{-0.15F, 0.56F, -0.55F, -1.0F, 0.55F, -1.0F, 0.15F, 0.56F},
					{-0.2F, 0.45F, -0.6F, 0.75F, -0.7F, 1.0F}, {0.2F, 0.45F, 0.6F, 0.75F, 0.7F, 1.0F},
					{0.7F, 1.0F, 0.6F, 0.7F, 0.78F, 0.55F, 0.66F, 0.25F}, {-0.35F, -0.3F, 0.35F, -0.3F}}));
		}
		return deity;
	}

	/** A fan of nine light tails (the Nine-Tailed Fox, G). */
	public static float[] tails() {
		if (tails == null) {
			float[][] lines = new float[9][];
			for (int i = 0; i < 9; i++) {
				double angle = Math.toRadians(-70.0D + i * 17.5D);
				float tipX = (float) Math.sin(angle) * 0.95F;
				float tipY = (float) Math.cos(angle) * 0.95F;
				lines[i] = new float[]{0.0F, -0.8F, tipX * 0.4F, tipY * 0.2F - 0.4F, tipX * 0.8F, tipY * 0.7F, tipX, tipY};
			}
			tails = sample(lines);
		}
		return tails;
	}

	/** A lotus seen from the side: eight petals (enlightenment, the lotus body). */
	public static float[] lotus() {
		if (lotus == null) {
			float[][] lines = new float[9][];
			for (int i = 0; i < 8; i++) {
				float x = -0.9F + i * 0.257F;
				float tip = 0.25F + 0.55F * (float) Math.sin(Math.PI * (i + 0.5F) / 8.0F);
				lines[i] = new float[]{x, -0.5F, x + 0.06F, tip * 0.6F - 0.3F, x + 0.13F, tip, x + 0.2F, tip * 0.6F - 0.3F, x + 0.257F, -0.5F};
			}
			lines[8] = new float[]{-0.95F, -0.55F, 0.95F, -0.55F};
			lotus = sample(lines);
		}
		return lotus;
	}

	/** A paifang gate outline (the gold dust fallback of G_GATE): pillars, three beams, a curved roof. */
	public static float[] gate() {
		if (gate == null) {
			gate = sample(new float[][]{
					{-0.7F, -1.0F, -0.7F, 0.55F}, {0.7F, -1.0F, 0.7F, 0.55F},
					{-0.85F, 0.3F, 0.85F, 0.3F}, {-0.9F, 0.55F, 0.9F, 0.55F}, {-0.6F, 0.75F, 0.6F, 0.75F},
					{-1.0F, 0.68F, -0.5F, 0.86F, 0.0F, 0.9F, 0.5F, 0.86F, 1.0F, 0.68F}, {0.0F, 0.3F, 0.0F, 0.75F}});
		}
		return gate;
	}

	private static float[][] circle(float cx, float cy, float r, int segments) {
		return ellipse(cx, cy, r, r, segments);
	}

	private static float[][] ellipse(float cx, float cy, float rx, float ry, int segments) {
		float[] line = new float[(segments + 1) * 2];
		for (int i = 0; i <= segments; i++) {
			double a = Math.PI * 2.0D * i / segments;
			line[i * 2] = cx + rx * (float) Math.cos(a);
			line[i * 2 + 1] = cy + ry * (float) Math.sin(a);
		}
		return new float[][]{line};
	}

	private static float[][] join(float[][]... parts) {
		int total = 0;
		for (float[][] part : parts) {
			total += part.length;
		}
		float[][] all = new float[total][];
		int index = 0;
		for (float[][] part : parts) {
			for (float[] line : part) {
				all[index++] = line;
			}
		}
		return all;
	}

	private static float[] sample(float[][] lines) {
		int count = 0;
		for (float[] line : lines) {
			for (int i = 2; i + 1 < line.length; i += 2) {
				float length = (float) Math.hypot(line[i] - line[i - 2], line[i + 1] - line[i - 1]);
				count += Math.max(1, (int) Math.ceil(length / STEP));
			}
		}
		float[] points = new float[count * 2];
		int index = 0;
		for (float[] line : lines) {
			for (int i = 2; i + 1 < line.length; i += 2) {
				float x0 = line[i - 2];
				float y0 = line[i - 1];
				float x1 = line[i];
				float y1 = line[i + 1];
				float length = (float) Math.hypot(x1 - x0, y1 - y0);
				int steps = Math.max(1, (int) Math.ceil(length / STEP));
				for (int s = 0; s < steps && index + 1 < points.length; s++) {
					float t = s / (float) steps;
					points[index++] = x0 + (x1 - x0) * t;
					points[index++] = y0 + (y1 - y0) * t;
				}
			}
		}
		return points;
	}

	private PointClouds() {
	}
}
