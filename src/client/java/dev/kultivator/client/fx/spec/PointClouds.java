package dev.kultivator.client.fx.spec;

import dev.kultivator.technique.spec.dsl.FxLayer;

import java.util.EnumMap;
import java.util.Map;

/**
 * Flat silhouettes for the point-cloud templates (BEAST, SKY_SILHOUETTE, PALM, FIST, SWORD_SHAPE, WINGS, AFTERIMAGE and
 * the avatar outline): each is a set of polylines in a unit box (x right, y up, about −1..1), sampled once into evenly
 * spaced points and cached, so templates never allocate per frame. A template places the points in a plane of its
 * choice and scales them to blocks.
 */
final class PointClouds {

	/** Distance between sampled points in unit space. */
	private static final float STEP = 0.035F;

	private static final Map<FxLayer.BeastKind, float[]> BEASTS = new EnumMap<>(FxLayer.BeastKind.class);
	private static float[] sword;
	private static float[] humanoid;
	private static float[] wing;
	private static float[] fist;
	private static float[] palm;

	/** Sampled points of a beast as x0, y0, x1, y1, ... */
	static float[] beast(FxLayer.BeastKind kind) {
		return BEASTS.computeIfAbsent(kind, PointClouds::buildBeast);
	}

	/** A sword pointing up (+y), guard at y −0.3. */
	static float[] sword() {
		if (sword == null) {
			sword = sample(new float[][]{
					{-0.05F, -0.3F, -0.05F, 0.85F, 0.0F, 1.0F, 0.05F, 0.85F, 0.05F, -0.3F},
					{0.0F, -0.25F, 0.0F, 0.9F},
					{-0.28F, -0.3F, 0.28F, -0.3F},
					{-0.28F, -0.34F, 0.28F, -0.34F},
					{0.0F, -0.34F, 0.0F, -0.62F},
					{-0.05F, -0.66F, 0.05F, -0.66F, 0.05F, -0.58F, -0.05F, -0.58F, -0.05F, -0.66F}});
		}
		return sword;
	}

	/** A standing figure (feet at y −1, head at 0.85), for afterimages and avatar outlines. */
	static float[] humanoid() {
		if (humanoid == null) {
			humanoid = sample(join(circle(0.0F, 0.84F, 0.13F, 14), new float[][]{
					{0.0F, 0.7F, 0.0F, 0.05F},
					{-0.32F, 0.6F, 0.32F, 0.6F},
					{-0.32F, 0.6F, -0.42F, 0.2F, -0.38F, -0.15F},
					{0.32F, 0.6F, 0.42F, 0.2F, 0.38F, -0.15F},
					{-0.2F, 0.05F, 0.2F, 0.05F},
					{-0.2F, 0.05F, -0.2F, -1.0F},
					{0.2F, 0.05F, 0.2F, -1.0F},
					{-0.22F, 0.6F, -0.2F, 0.05F},
					{0.22F, 0.6F, 0.2F, 0.05F}}));
		}
		return humanoid;
	}

	/** One wing reaching right from the shoulder (0, 0): the leading edge and five feather tips. */
	static float[] wing() {
		if (wing == null) {
			wing = sample(new float[][]{
					{0.0F, 0.0F, 0.3F, 0.35F, 0.65F, 0.5F, 1.0F, 0.45F},
					{1.0F, 0.45F, 0.85F, 0.15F, 0.78F, 0.3F, 0.62F, -0.05F, 0.56F, 0.18F, 0.4F, -0.2F, 0.34F, 0.05F, 0.18F, -0.25F,
							0.12F, -0.02F, 0.0F, -0.15F},
					{0.15F, 0.12F, 0.7F, 0.38F},
					{0.1F, 0.0F, 0.55F, 0.12F}});
		}
		return wing;
	}

	/** A clenched fist seen from the front. */
	static float[] fist() {
		if (fist == null) {
			fist = sample(join(new float[][]{
							{-0.55F, -0.55F, 0.55F, -0.55F, 0.6F, 0.35F, -0.6F, 0.35F, -0.55F, -0.55F},
							{-0.55F, 0.0F, 0.1F, -0.05F, 0.25F, -0.3F},
							{-0.3F, 0.35F, -0.3F, 0.05F}, {0.0F, 0.35F, 0.0F, 0.05F}, {0.3F, 0.35F, 0.3F, 0.05F},
							{-0.4F, -0.55F, -0.35F, -0.9F, 0.35F, -0.9F, 0.4F, -0.55F}},
					circle(-0.45F, 0.42F, 0.15F, 8), circle(-0.15F, 0.48F, 0.15F, 8), circle(0.15F, 0.48F, 0.15F, 8),
					circle(0.45F, 0.42F, 0.15F, 8)));
		}
		return fist;
	}

	/** An open palm, fingers up (outline only: the template fills the inside with {@link #insidePalm}). */
	static float[] palm() {
		if (palm == null) {
			palm = sample(new float[][]{
					{-0.5F, -0.6F, 0.5F, -0.6F, 0.5F, 0.3F},
					{0.46F, 0.3F, 0.46F, 0.72F, 0.28F, 0.72F, 0.28F, 0.3F},
					{0.23F, 0.3F, 0.23F, 0.9F, 0.03F, 0.9F, 0.03F, 0.3F},
					{-0.02F, 0.3F, -0.02F, 0.95F, -0.22F, 0.95F, -0.22F, 0.3F},
					{-0.27F, 0.3F, -0.27F, 0.85F, -0.47F, 0.85F, -0.47F, 0.3F},
					{-0.5F, 0.3F, -0.5F, -0.15F, -0.85F, 0.22F, -0.75F, 0.32F, -0.5F, 0.05F},
					{-0.5F, -0.6F, -0.5F, -0.15F}});
		}
		return palm;
	}

	/** Inside the unit palm: palm, four fingers and a thumb (x −0.85..0.5, y −0.6..0.95). */
	static boolean insidePalm(double x, double y) {
		if (Math.abs(x) <= 0.5D && y >= -0.6D && y <= 0.32D) {
			return true;
		}
		double[][] fingers = {{-0.37D, 0.85D}, {-0.12D, 0.95D}, {0.13D, 0.9D}, {0.37D, 0.72D}};
		for (double[] finger : fingers) {
			if (Math.abs(x - finger[0]) <= 0.1D && y >= 0.3D && y <= finger[1]) {
				return true;
			}
		}
		// The thumb: a slanted bar from the palm edge outwards.
		double t = Math.max(0.0D, Math.min(1.0D, ((x + 0.5D) * -0.35D + (y + 0.05D) * 0.37D) / (0.35D * 0.35D + 0.37D * 0.37D)));
		double cx = -0.5D - 0.35D * t;
		double cy = -0.05D + 0.37D * t;
		return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= 0.12D * 0.12D;
	}

	private static float[] buildBeast(FxLayer.BeastKind kind) {
		return switch (kind) {
			case DRAGON -> sample(join(serpent(), new float[][]{
					{0.78F, 0.18F, 1.0F, 0.12F, 0.98F, 0.02F, 0.8F, 0.0F},
					{0.86F, 0.2F, 0.8F, 0.42F}, {0.92F, 0.17F, 0.95F, 0.38F},
					{1.0F, 0.07F, 1.12F, 0.18F}, {1.0F, 0.05F, 1.12F, -0.06F},
					{-0.4F, -0.08F, -0.48F, -0.3F, -0.42F, -0.34F}, {0.1F, -0.18F, 0.04F, -0.4F, 0.1F, -0.44F},
					{0.45F, 0.02F, 0.4F, -0.2F, 0.46F, -0.24F}, {-0.72F, 0.12F, -0.8F, -0.1F},
					{-0.3F, 0.28F, -0.25F, 0.4F}, {0.2F, 0.16F, 0.25F, 0.3F}, {0.55F, 0.3F, 0.6F, 0.42F}}));
			case BIRD -> sample(join(circle(0.0F, 0.52F, 0.09F, 10), new float[][]{
					{0.0F, 0.42F, 0.0F, -0.32F},
					{0.0F, 0.15F, 0.35F, 0.42F, 0.7F, 0.5F, 1.0F, 0.38F, 0.82F, 0.25F, 0.88F, 0.12F, 0.65F, 0.1F, 0.66F, -0.02F,
							0.42F, 0.02F, 0.3F, -0.08F, 0.0F, 0.0F},
					{0.0F, 0.15F, -0.35F, 0.42F, -0.7F, 0.5F, -1.0F, 0.38F, -0.82F, 0.25F, -0.88F, 0.12F, -0.65F, 0.1F, -0.66F,
							-0.02F, -0.42F, 0.02F, -0.3F, -0.08F, 0.0F, 0.0F},
					{0.0F, -0.32F, -0.28F, -0.85F}, {0.0F, -0.32F, 0.0F, -0.95F}, {0.0F, -0.32F, 0.28F, -0.85F},
					{0.0F, 0.6F, 0.05F, 0.66F, 0.14F, 0.56F}}));
			case KUN -> sample(join(ellipse(-0.1F, 0.0F, 0.8F, 0.28F, 26), new float[][]{
					{-0.88F, 0.0F, -1.05F, 0.38F, -0.98F, 0.0F, -1.05F, -0.38F, -0.88F, 0.0F},
					{-0.1F, -0.05F, -0.35F, -0.75F, 0.15F, -0.2F},
					{-0.1F, 0.05F, -0.35F, 0.75F, 0.15F, 0.2F},
					{0.5F, 0.0F, 0.62F, -0.08F}, {-0.3F, 0.27F, -0.1F, 0.42F, 0.1F, 0.27F}},
					circle(0.48F, 0.08F, 0.04F, 6)));
			case TIGER -> sample(join(circle(0.72F, 0.25F, 0.2F, 14), new float[][]{
					{-0.65F, 0.15F, 0.55F, 0.2F}, {-0.65F, -0.2F, 0.55F, -0.15F}, {-0.65F, 0.15F, -0.7F, -0.02F, -0.65F, -0.2F},
					{0.62F, 0.42F, 0.66F, 0.52F, 0.72F, 0.44F}, {0.8F, 0.43F, 0.86F, 0.52F, 0.9F, 0.4F},
					{-0.55F, -0.2F, -0.6F, -0.65F}, {-0.3F, -0.2F, -0.28F, -0.65F}, {0.3F, -0.17F, 0.28F, -0.65F},
					{0.5F, -0.16F, 0.56F, -0.65F}, {-0.7F, 0.05F, -0.95F, 0.25F, -1.0F, 0.5F},
					{-0.35F, 0.17F, -0.3F, -0.05F}, {-0.05F, 0.18F, 0.0F, -0.02F}, {0.25F, 0.19F, 0.3F, -0.0F}}));
			case WOLF -> sample(join(circle(0.15F, 0.32F, 0.05F, 6), new float[][]{
					{-0.6F, -0.7F, -0.55F, 0.2F, -0.35F, 0.65F, -0.2F, 0.95F, -0.05F, 0.6F, 0.25F, 0.5F, 0.9F, 0.2F, 0.95F, 0.08F,
							0.5F, -0.05F, 0.85F, -0.15F, 0.3F, -0.3F, -0.2F, -0.45F, -0.6F, -0.7F},
					{0.25F, 0.5F, 0.35F, 0.85F, 0.45F, 0.48F}, {0.6F, 0.05F, 0.75F, 0.0F}}));
			case TORTOISE -> sample(join(halfEllipse(0.0F, -0.1F, 0.7F, 0.6F, 20), new float[][]{
					{-0.7F, -0.1F, 0.7F, -0.1F},
					{-0.35F, -0.1F, -0.25F, 0.3F, 0.25F, 0.3F, 0.35F, -0.1F}, {-0.25F, 0.3F, -0.1F, 0.5F}, {0.25F, 0.3F, 0.1F, 0.5F},
					{0.7F, -0.05F, 0.92F, 0.1F, 1.02F, 0.02F, 0.95F, -0.1F, 0.7F, -0.12F},
					{-0.55F, -0.1F, -0.6F, -0.45F}, {-0.2F, -0.1F, -0.22F, -0.45F}, {0.2F, -0.1F, 0.22F, -0.45F},
					{0.5F, -0.1F, 0.55F, -0.45F}, {-0.7F, -0.1F, -0.88F, -0.2F}}));
			case APE -> sample(join(circle(0.0F, 0.72F, 0.2F, 14), new float[][]{
					{-0.6F, 0.48F, 0.6F, 0.48F}, {-0.6F, 0.48F, -0.75F, 0.0F, -0.8F, -0.55F}, {0.6F, 0.48F, 0.75F, 0.0F, 0.8F, -0.55F},
					{-0.4F, 0.48F, -0.3F, -0.25F, 0.3F, -0.25F, 0.4F, 0.48F}, {-0.3F, -0.25F, -0.38F, -1.0F},
					{0.3F, -0.25F, 0.38F, -1.0F}, {-0.08F, 0.66F, 0.08F, 0.66F}, {-0.1F, 0.58F, 0.1F, 0.58F}}));
			case SCORPION -> sample(join(ellipse(0.0F, -0.2F, 0.22F, 0.45F, 16), new float[][]{
					{0.0F, 0.25F, 0.05F, 0.55F, 0.25F, 0.85F, 0.5F, 0.9F, 0.62F, 0.72F, 0.52F, 0.6F},
					{-0.15F, -0.6F, -0.35F, -0.8F, -0.55F, -0.75F, -0.6F, -0.95F, -0.45F, -0.98F},
					{0.15F, -0.6F, 0.35F, -0.8F, 0.55F, -0.75F, 0.6F, -0.95F, 0.45F, -0.98F},
					{-0.2F, -0.3F, -0.6F, -0.15F}, {-0.2F, -0.1F, -0.62F, 0.05F}, {-0.2F, 0.08F, -0.55F, 0.25F},
					{0.2F, -0.3F, 0.6F, -0.15F}, {0.2F, -0.1F, 0.62F, 0.05F}, {0.2F, 0.08F, 0.55F, 0.25F}}));
			case DEITY -> sample(join(circle(0.0F, 0.72F, 0.15F, 14), circle(0.0F, 0.74F, 0.32F, 22), new float[][]{
					{-0.15F, 0.56F, -0.55F, -1.0F, 0.55F, -1.0F, 0.15F, 0.56F},
					{-0.2F, 0.45F, -0.6F, 0.75F, -0.7F, 1.0F}, {0.2F, 0.45F, 0.6F, 0.75F, 0.7F, 1.0F},
					{-0.12F, 0.88F, -0.06F, 0.98F, 0.0F, 0.9F, 0.06F, 0.98F, 0.12F, 0.88F},
					{-0.35F, -0.3F, 0.35F, -0.3F}}));
		};
	}

	/** The dragon's body: two waving lines from the tail to the neck. */
	private static float[][] serpent() {
		int n = 24;
		float[] top = new float[(n + 1) * 2];
		float[] bottom = new float[(n + 1) * 2];
		for (int i = 0; i <= n; i++) {
			float x = -1.0F + 1.8F * i / n;
			float width = 0.03F + 0.09F * i / n;
			float y = 0.2F * (float) Math.sin(x * Math.PI * 1.6D);
			top[i * 2] = x;
			top[i * 2 + 1] = y + width;
			bottom[i * 2] = x;
			bottom[i * 2 + 1] = y - width;
		}
		return new float[][]{top, bottom};
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

	private static float[][] halfEllipse(float cx, float cy, float rx, float ry, int segments) {
		float[] line = new float[(segments + 1) * 2];
		for (int i = 0; i <= segments; i++) {
			double a = Math.PI * i / segments;
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

	/** Evenly spaced points along every polyline. */
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
