package dev.kultivator.client.fx;

import dev.kultivator.client.EffectQuality;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Particle shapes for technique effects: the open palm (Qi Palm prints, the giant Buddha Palm), hemispheres and
 * expanding rings. Counts are given for Normal quality and scaled by {@link EffectQuality}.
 */
final class TechniqueShapes {

	/** Finger centre lines: x, half width, tip height (unit palm, fingers pointing +y). */
	private static final double[][] FINGERS = {
			{-0.37D, 0.10D, 0.95D},
			{-0.12D, 0.10D, 1.05D},
			{0.13D, 0.10D, 1.00D},
			{0.37D, 0.09D, 0.82D}
	};
	/** Shift so the drawn palm is centred on the given point. */
	private static final double SHIFT_X = 0.17D;
	private static final double SHIFT_Y = -0.22D;

	/** Inside the unit palm: palm with rounded corners, four fingers and a thumb (x -0.85..0.5, y -0.6..1.05). */
	static boolean insidePalm(double x, double y) {
		if (Math.abs(x) <= 0.5D && y >= -0.6D && y <= 0.38D) {
			double cx = x < 0.0D ? -0.38D : 0.38D;
			if (Math.abs(x) > 0.38D && y < -0.48D) {
				double dx = x - cx;
				double dy = y + 0.48D;
				return dx * dx + dy * dy <= 0.12D * 0.12D;
			}
			return true;
		}
		for (double[] finger : FINGERS) {
			double half = finger[1];
			double tip = finger[2] - half;
			if (Math.abs(x - finger[0]) <= half && y >= 0.3D && y <= tip) {
				return true;
			}
			double dx = x - finger[0];
			double dy = y - tip;
			if (dy > 0.0D && dx * dx + dy * dy <= half * half) {
				return true;
			}
		}
		return distanceToSegment(x, y, -0.42D, -0.15D, -0.82D, 0.22D) <= 0.12D;
	}

	/** On the outline of the palm (within {@code e} of the outside). */
	static boolean edgeOfPalm(double x, double y, double e) {
		return insidePalm(x, y) && (!insidePalm(x + e, y) || !insidePalm(x - e, y) || !insidePalm(x, y + e) || !insidePalm(x, y - e));
	}

	/**
	 * Draws a palm in the plane spanned by {@code right} and {@code up} (fingers along {@code up}), centred on
	 * {@code center}, {@code size} blocks per unit. Outline motes use {@code edge}, the inside {@code fill}.
	 */
	static void palm(Vec3 center, Vec3 right, Vec3 up, double size, int fill, int edge, float dustSize, int count,
			RandomSource random, boolean important) {
		int n = EffectQuality.scale(count);
		double edgeWidth = Math.max(0.035D, 0.12D / Math.max(0.5D, size));
		for (int i = 0; i < n; i++) {
			for (int tries = 0; tries < 12; tries++) {
				double x = -0.85D + random.nextDouble() * 1.35D;
				double y = -0.6D + random.nextDouble() * 1.65D;
				if (!insidePalm(x, y)) {
					continue;
				}
				boolean outline = edgeOfPalm(x, y, edgeWidth);
				Vec3 at = center.add(right.scale((x + SHIFT_X) * size)).add(up.scale((y + SHIFT_Y) * size));
				CommonFx.add(CommonFx.dust(outline ? edge : fill, outline ? dustSize * 1.25F : dustSize), at, Vec3.ZERO, important);
				break;
			}
		}
		if (EffectQuality.maximal() && random.nextInt(3) == 0) {
			Vec3 tip = center.add(up.scale((1.05D + SHIFT_Y) * size)).add(right.scale((-0.12D + SHIFT_X) * size));
			CommonFx.add(ParticleTypes.END_ROD, tip, Vec3.ZERO, important);
		}
	}

	/** {@code count} motes on a hemisphere (upper half) of {@code radius} around {@code center}. */
	static void hemisphere(Vec3 center, double radius, int rgb, float dustSize, int count, RandomSource random, Vec3 velocity,
			boolean important) {
		int n = EffectQuality.scale(count);
		for (int i = 0; i < n; i++) {
			double u = random.nextDouble();
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double flat = Math.sqrt(Math.max(0.0D, 1.0D - u * u)) * radius;
			Vec3 at = center.add(Math.cos(angle) * flat, u * radius, Math.sin(angle) * flat);
			CommonFx.add(CommonFx.dust(rgb, dustSize), at, velocity, important);
		}
	}

	/** A horizontal ring that does not need an exact point count (random angles). */
	static void looseRing(Vec3 center, double radius, int rgb, float dustSize, int count, RandomSource random, boolean important) {
		int n = EffectQuality.scale(count);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(CommonFx.dust(rgb, dustSize), center.add(Math.cos(angle) * radius, 0.05D, Math.sin(angle) * radius), Vec3.ZERO, important);
		}
	}

	/** Brighter version of {@code rgb}: blends {@code amount} (0..1) towards white. */
	static int lighten(int rgb, float amount) {
		float t = Math.max(0.0F, Math.min(1.0F, amount));
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		r = Math.round(r + (255 - r) * t);
		g = Math.round(g + (255 - g) * t);
		b = Math.round(b + (255 - b) * t);
		return (r << 16) | (g << 8) | b;
	}

	/** Darker version of {@code rgb}: multiplies every channel by {@code factor} (0..1). */
	static int dim(int rgb, float factor) {
		float f = Math.max(0.0F, Math.min(1.0F, factor));
		int r = Math.round(((rgb >> 16) & 0xFF) * f);
		int g = Math.round(((rgb >> 8) & 0xFF) * f);
		int b = Math.round((rgb & 0xFF) * f);
		return (r << 16) | (g << 8) | b;
	}

	private static double distanceToSegment(double px, double py, double ax, double ay, double bx, double by) {
		double dx = bx - ax;
		double dy = by - ay;
		double lengthSq = dx * dx + dy * dy;
		double t = lengthSq <= 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, ((px - ax) * dx + (py - ay) * dy) / lengthSq));
		double cx = ax + dx * t - px;
		double cy = ay + dy * t - py;
		return Math.sqrt(cx * cx + cy * cy);
	}

	private TechniqueShapes() {
	}
}
