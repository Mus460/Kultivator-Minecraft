package dev.kultivator.client.epic.kosmik.orbit;

import dev.kultivator.client.epic.kosmik.KosmikMesh;
import dev.kultivator.client.epic.kosmik.KosmikTypes;
import dev.kultivator.client.epic.kosmik.StageFrame;
import dev.kultivator.client.epic.render.EpicRenderer;
import net.minecraft.resources.Identifier;

/**
 * The bodies of the Orbit stage (DESIGN 32.2.1): the planet (the day map, or the biome-baked one, lit per vertex by
 * {@code day × (0.08 + 0.92 × max(0, n·sun)^0.8)} with a warm twilight band), its night lights, the cloud shell, the
 * atmosphere (fresnel shells and one camera-facing limb annulus placed on the planet's silhouette circle), the moon, the
 * sun (core, corona), the bright stars and, near home, the local land decal and cloud decks. Grid sizes follow the
 * quality latched at the shot start (no popping); the shader-pack profile trims them (32.1.6).
 */
final class OrbitBodies implements OrbitSphere.Shader {

	static final int L_PLANET = 0;
	static final int L_NIGHT = 1;
	static final int L_CLOUDS = 2;
	static final int L_ATMO = 3;
	static final int L_MOON = 4;

	/** Planet grids (rings, segments) per quality, and under a shader pack. */
	private static final int[][] PLANET_GRID = {{12, 24}, {24, 48}, {32, 64}};
	private static final int[][] PLANET_GRID_PACK = {{10, 20}, {20, 40}, {26, 52}};
	private static final int[][] CLOUD_GRID = {{0, 0}, {16, 32}, {24, 48}};
	private static final int[][] ATMO_GRID = {{0, 0}, {12, 24}, {16, 32}};
	private static final int[][] MOON_GRID = {{0, 0}, {10, 20}, {16, 32}};
	/** Atmosphere shells: radius (× r_p), fresnel power, alpha. */
	private static final double[] SHELL_R = {1.015D, 1.03D, 1.05D};
	private static final double[] SHELL_P = {3.0D, 4.0D, 6.0D};
	private static final double[] SHELL_A = {0.5D, 0.42D, 0.34D};
	private static final int ATMO_DAY = 0x7EC8FF;
	private static final int ATMO_DUSK = 0xFF9A5A;
	private static final int ATMO_NIGHT = 0x2A3A8A;

	private static final int LIMB_SEG = 32;
	private static final float[] LIMB_COS = new float[LIMB_SEG + 1];
	private static final float[] LIMB_SIN = new float[LIMB_SEG + 1];
	private static final float[] LIMB_RADII = {0.80F, 0.87F, 1.0F};
	private static final int PATCH_RINGS = 6;
	private static final int PATCH_SEG = 12;

	static {
		for (int j = 0; j <= LIMB_SEG; j++) {
			double a = Math.PI * 2.0D * j / LIMB_SEG;
			LIMB_COS[j] = (float) Math.cos(a);
			LIMB_SIN[j] = (float) Math.sin(a);
		}
	}

	private static final int MAX_STARS = 80;

	private final OrbitBeats b;
	private final OrbitSphere sphere = new OrbitSphere();
	private final float[] starDir = new float[MAX_STARS * 3];
	private final float[] starHalf = new float[MAX_STARS];
	private final float[] starRate = new float[MAX_STARS];
	private final float[] starPhase = new float[MAX_STARS];
	private final int[] starRgb = new int[MAX_STARS];
	private final int[] limbColor = new int[LIMB_SEG + 1];
	private final float[] limbAlpha = new float[LIMB_SEG + 1];
	private final double[] patch = new double[(PATCH_RINGS + 1) * (PATCH_SEG + 1) * 5];
	private boolean prepared;

	// latched at the shot start
	private Identifier planetTex = OrbitTex.PLANET_DAY[1];
	private int q = 1;
	private boolean pack;

	// the atmosphere shell being drawn
	private double shellPower = 3.0D;
	private double shellAlpha = 0.5D;
	private double eyeX;
	private double eyeY;
	private double eyeZ;

	OrbitBodies(OrbitBeats beats) {
		this.b = beats;
	}

	/** Builds the cached grids and the star table once (≪ 4 ms). */
	void prepare() {
		if (prepared) {
			return;
		}
		prepared = true;
		for (int quality = 0; quality < 3; quality++) {
			KosmikMesh.sphere(PLANET_GRID[quality][0], PLANET_GRID[quality][1]);
			KosmikMesh.sphere(PLANET_GRID_PACK[quality][0], PLANET_GRID_PACK[quality][1]);
			if (quality > 0) {
				KosmikMesh.sphere(CLOUD_GRID[quality][0], CLOUD_GRID[quality][1]);
				KosmikMesh.sphere(ATMO_GRID[quality][0], ATMO_GRID[quality][1]);
				KosmikMesh.sphere(MOON_GRID[quality][0], MOON_GRID[quality][1]);
			}
		}
		// the bright stars: a fixed sky (the same every film), uniform on the sphere
		long z = 0x5EED0B17L;
		for (int i = 0; i < MAX_STARS; i++) {
			z = mix(z + i);
			double y = (z >>> 11) * 0x1.0p-53 * 2.0D - 1.0D;
			z = mix(z);
			double th = (z >>> 11) * 0x1.0p-53 * Math.PI * 2.0D;
			double rr = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
			starDir[i * 3] = (float) (rr * Math.sin(th));
			starDir[i * 3 + 1] = (float) y;
			starDir[i * 3 + 2] = (float) (rr * Math.cos(th));
			z = mix(z);
			double mag = (z >>> 11) * 0x1.0p-53;
			starHalf[i] = (float) Math.toRadians(0.25D + 0.5D * mag * mag);
			z = mix(z);
			starRate[i] = (float) (0.05D + 0.1D * ((z >>> 11) * 0x1.0p-53));
			z = mix(z);
			starPhase[i] = (float) ((z >>> 11) * 0x1.0p-53 * Math.PI * 2.0D);
			z = mix(z);
			double tint = (z >>> 11) * 0x1.0p-53;
			starRgb[i] = tint < 0.2D ? 0xFFD8B0 : tint < 0.55D ? 0xF4F6FF : 0xC8DAFF;
		}
	}

	/** A shot starts: latch the grids and the planet texture (the baked map only when it is ready now). */
	void latch() {
		q = b.quality;
		pack = b.pack;
		planetTex = !b.ringan && PlanetBake.ready() ? OrbitTex.BAKED_PLANET : OrbitTex.PLANET_DAY[q];
	}

	// ================================================================ layers

	/** The planet: −1 when the budget refused it. */
	int planet(StageFrame f) {
		int[] grid = pack ? PLANET_GRID_PACK[q] : PLANET_GRID[q];
		KosmikMesh.Sphere mesh = KosmikMesh.sphere(grid[0], grid[1]);
		EpicRenderer.Sink s = KosmikTypes.emissiveBodies(pack) ? f.glow(planetTex) : f.solid(planetTex);
		return sphere.draw(f, s, mesh, 0.0D, 0.0D, 0.0D, OrbitBeats.R_P, b.spin, this, L_PLANET, false);
	}

	/** The night-side city lights (Normal and up, not under a pack). True = refused by the budget. */
	boolean nightLights(StageFrame f) {
		if (q < 1 || pack) {
			return false;
		}
		int[] grid = PLANET_GRID[q];
		KosmikMesh.Sphere mesh = KosmikMesh.sphere(grid[0], grid[1]);
		return sphere.draw(f, f.add(OrbitTex.PLANET_NIGHT[q]), mesh, 0.0D, 0.0D, 0.0D, OrbitBeats.R_P * 1.001D, b.spin, this, L_NIGHT, true) < 0;
	}

	/** The cloud shell, turning faster than the ground (Normal and up, not under a pack). True = refused. */
	boolean clouds(StageFrame f) {
		if (q < 1 || pack) {
			return false;
		}
		KosmikMesh.Sphere mesh = KosmikMesh.sphere(CLOUD_GRID[q][0], CLOUD_GRID[q][1]);
		return sphere.draw(f, f.glow(OrbitTex.CLOUDS[q]), mesh, 0.0D, 0.0D, 0.0D, OrbitBeats.R_P * 1.008D, b.cloudSpin, this, L_CLOUDS, true) < 0;
	}

	/** The fresnel shells: 2 (Normal) or 3 (Maximal); one under a pack; none at Minimal (the limb annulus only). True = refused. */
	boolean atmosphere(StageFrame f) {
		if (q < 1) {
			return false;
		}
		KosmikMesh.Sphere mesh = KosmikMesh.sphere(ATMO_GRID[q][0], ATMO_GRID[q][1]);
		EpicRenderer.Sink s = f.add(OrbitTex.ATMO);
		eyeX = f.ex;
		eyeY = f.ey;
		eyeZ = f.ez;
		int first = pack ? 1 : 0;
		int last = pack ? 1 : q == 1 ? 1 : 2;
		for (int i = first; i <= last; i++) {
			shellPower = SHELL_P[i];
			shellAlpha = SHELL_A[i];
			if (sphere.draw(f, s, mesh, 0.0D, 0.0D, 0.0D, OrbitBeats.R_P * SHELL_R[i], 0.0D, this, L_ATMO, true) < 0) {
				return true;
			}
		}
		return false;
	}

	/** The moon (Normal and up). True = refused. */
	boolean moon(StageFrame f) {
		if (q < 1) {
			return false;
		}
		KosmikMesh.Sphere mesh = KosmikMesh.sphere(MOON_GRID[q][0], MOON_GRID[q][1]);
		EpicRenderer.Sink s = KosmikTypes.emissiveBodies(pack) ? f.glow(OrbitTex.MOON[q]) : f.solid(OrbitTex.MOON[q]);
		return sphere.draw(f, s, mesh, OrbitBeats.MOON_X, OrbitBeats.MOON_Y, OrbitBeats.MOON_Z, OrbitBeats.MOON_R, 0.0D, this, L_MOON, false) < 0;
	}

	/** The sun: 2 / 3 / 4 sky layers (core, glow, corona turning 0.05°/tick, the wide halo). True = refused. */
	boolean sun(StageFrame f) {
		int layers = q == 0 ? 2 : q == 1 ? 3 : 4;
		if (KosmikTypes.emissiveBodies(pack) && OrbitBeats.occluded(b.sx * 1.0E5D, b.sy * 1.0E5D, b.sz * 1.0E5D, f.ex, f.ey, f.ez)) {
			// the emissive bodies write no depth: a hidden sun must not be drawn over them
			return false;
		}
		if (!f.take(layers)) {
			return true;
		}
		float k = b.sunGlow;
		float sx = (float) b.sx;
		float sy = (float) b.sy;
		float sz = (float) b.sz;
		float roll = (float) (Math.toRadians(0.05D) * b.clock);
		EpicRenderer.Sink corona = f.add(OrbitTex.SUN_CORONA);
		EpicRenderer.Sink core = f.add(OrbitTex.SUN_CORE);
		if (layers >= 4) {
			f.skyBillboard(corona, sx, sy, sz, (float) Math.toRadians(24.0D), -roll * 0.6F, StageFrame.argb(0.28F * k, 0xFFD49A));
		}
		if (layers >= 3) {
			f.skyBillboard(corona, sx, sy, sz, (float) Math.toRadians(11.0D), roll, StageFrame.argb(0.6F * k, 0xFFE2B4));
		}
		f.skyBillboard(core, sx, sy, sz, (float) Math.toRadians(4.0D), 0.0F, StageFrame.argb(0.75F * k, 0xFFEFD0));
		f.skyBillboard(core, sx, sy, sz, (float) Math.toRadians(1.3D), 0.0F, 0xFFFFFFFF);
		return false;
	}

	/** The bright stars: 40 (Normal) / 80 (Maximal), × 0.6 under a pack; the twinkle is alpha only. True = refused. */
	boolean stars(StageFrame f) {
		int n = q == 1 ? 40 : q == 2 ? 80 : 0;
		if (pack) {
			n = n * 6 / 10;
		}
		if (n == 0 || KosmikTypes.emissiveBodies(pack)) {
			return false;
		}
		if (!f.take(n)) {
			return true;
		}
		EpicRenderer.Sink s = f.add(OrbitTex.STAR_SPIKE);
		for (int i = 0; i < n; i++) {
			float tw = (float) (0.55D + 0.45D * Math.sin(b.clock * starRate[i] + starPhase[i]));
			f.skyBillboard(s, starDir[i * 3], starDir[i * 3 + 1], starDir[i * 3 + 2], starHalf[i], 0.0F, StageFrame.argb(0.9F * tw, starRgb[i]));
		}
		return false;
	}

	/**
	 * The limb glow: one annulus (32 × 2 quads) on the plane of the planet's silhouette circle seen from the eye (centre
	 * {@code E + (C − E)(1 − r²/D²)}, radius {@code r·√(D² − r²)/D}); the inner part lies inside the planet and is hidden by
	 * it, so only the halo beyond the rim shows. Coloured per angle by the sun on that part of the rim (day blue, dusk
	 * orange, night indigo) with a warm sunrise boost where the sun stands just behind the limb.
	 */
	boolean limb(StageFrame f) {
		double ex = f.ex;
		double ey = f.ey;
		double ez = f.ez;
		double r = OrbitBeats.R_P;
		double d2 = ex * ex + ey * ey + ez * ez;
		double d = Math.sqrt(d2);
		if (d <= r * 1.002D) {
			return false;
		}
		if (!f.take(LIMB_SEG * 2)) {
			return true;
		}
		double wx = -ex / d;
		double wy = -ey / d;
		double wz = -ez / d;
		double keep = r * r / d2;
		double ox = ex * keep;
		double oy = ey * keep;
		double oz = ez * keep;
		double rs = r * Math.sqrt(d2 - r * r) / d;
		// a basis of the plane perpendicular to the view of the centre
		double hx = Math.abs(wy) < 0.9D ? 0.0D : 1.0D;
		double hy = Math.abs(wy) < 0.9D ? 1.0D : 0.0D;
		double ax = hy * wz;
		double ay = -hx * wz;
		double az = hx * wy - hy * wx;
		double al = Math.sqrt(ax * ax + ay * ay + az * az);
		ax /= al;
		ay /= al;
		az /= al;
		double bx = wy * az - wz * ay;
		double by = wz * ax - wx * az;
		double bz = wx * ay - wy * ax;
		double outer = rs / 0.87D;
		for (int j = 0; j <= LIMB_SEG; j++) {
			double c = LIMB_COS[j];
			double s = LIMB_SIN[j];
			double px = ox + (ax * c + bx * s) * rs;
			double py = oy + (ay * c + by * s) * rs;
			double pz = oz + (az * c + bz * s) * rs;
			double ns = (px * b.sx + py * b.sy + pz * b.sz) / r;
			double vx = px - ex;
			double vy = py - ey;
			double vz = pz - ez;
			double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
			double toward = vl > 0.0D ? (vx * b.sx + vy * b.sy + vz * b.sz) / vl : 0.0D;
			double boost = Math.pow(Math.max(0.0D, toward), 24.0D);
			int base = atmoColor(ns);
			limbColor[j] = StageFrame.lerpColor(0xFF000000 | base, 0xFFFFC890, (float) Math.min(1.0D, boost * 1.2D)) & 0xFFFFFF;
			limbAlpha[j] = (float) Math.min(1.0D, 0.9D * (0.3D + 0.7D * OrbitBeats.smoothstep(-0.35D, 0.35D, ns)) + 0.6D * boost);
		}
		EpicRenderer.Sink sink = f.add(OrbitTex.LIMB_GLOW);
		for (int band = 0; band < 2; band++) {
			double r0 = LIMB_RADII[band] * outer;
			double r1 = LIMB_RADII[band + 1] * outer;
			for (int j = 0; j < LIMB_SEG; j++) {
				limbVertex(f, sink, ox, oy, oz, ax, ay, az, bx, by, bz, r0, outer, j);
				limbVertex(f, sink, ox, oy, oz, ax, ay, az, bx, by, bz, r0, outer, j + 1);
				limbVertex(f, sink, ox, oy, oz, ax, ay, az, bx, by, bz, r1, outer, j + 1);
				limbVertex(f, sink, ox, oy, oz, ax, ay, az, bx, by, bz, r1, outer, j);
			}
		}
		return false;
	}

	private void limbVertex(StageFrame f, EpicRenderer.Sink s, double ox, double oy, double oz, double ax, double ay, double az, double bx,
							double by, double bz, double rad, double outer, int j) {
		double c = LIMB_COS[j];
		double sn = LIMB_SIN[j];
		double x = ox + (ax * c + bx * sn) * rad;
		double y = oy + (ay * c + by * sn) * rad;
		double z = oz + (az * c + bz * sn) * rad;
		float k = (float) (rad / outer) * 0.5F;
		f.vertex(s, x, y, z, 0.5F + k * (float) c, 0.5F + k * (float) sn, StageFrame.argb(limbAlpha[j], limbColor[j]));
	}

	/**
	 * The local patch near home (when the eye is within 1500 × fs sm): the land detail decal (tiled 4 ×, tinted by the
	 * home grass colour, radial fade) just above the ground on a 6 × 12 polar grid, and 0 / 1 / 2 cloud decks at +8 and
	 * +14 sm.
	 */
	boolean patch(StageFrame f) {
		double fs = b.fs;
		if (f.distance(b.hx, b.hy, b.hz) > 1500.0D * fs) {
			return false;
		}
		int decks = q == 0 ? 0 : q == 1 ? 1 : 2;
		int quads = PATCH_RINGS * PATCH_SEG * (1 + decks);
		if (!f.take(quads)) {
			return true;
		}
		double light = 0.12D + 0.88D * Math.max(0.0D, b.upx * b.sx + b.upy * b.sy + b.upz * b.sz);
		int grass = b.colors.grass();
		int tint = scale(grass, 0.85D * light);
		double radius = 150.0D * Math.sqrt(fs);
		disc(f, f.glow(OrbitTex.PATCH_LAND[q]), radius, 1.0D, 4.0D, 0.0F, tint, 0.85F, 0.55D);
		int white = scale(0xFFFFFF, light);
		for (int i = 0; i < decks; i++) {
			float drift = (float) (b.clock * (i == 0 ? 0.0006D : -0.0004D));
			disc(f, f.glow(OrbitTex.PATCH_CLOUD[q]), radius * 1.6D, i == 0 ? 8.0D : 14.0D, 3.0D, drift, white, 0.72F - 0.12F * i, 0.4D);
		}
		return false;
	}

	/** One polar disc on the planet around home at {@code alt} above the surface (it follows the curvature). */
	private void disc(StageFrame f, EpicRenderer.Sink s, double radius, double alt, double tiles, float drift, int rgb, float alpha, double fadeFrom) {
		int w = PATCH_SEG + 1;
		for (int i = 0; i <= PATCH_RINGS; i++) {
			double rr = radius * i / PATCH_RINGS;
			double fade = 1.0D - OrbitBeats.smoothstep(fadeFrom, 1.0D, (double) i / PATCH_RINGS);
			for (int j = 0; j <= PATCH_SEG; j++) {
				double a = Math.PI * 2.0D * j / PATCH_SEG;
				double oe = Math.cos(a) * rr;
				double on = Math.sin(a) * rr;
				double px = b.hx + b.ex * oe + b.nx * on;
				double py = b.hy + b.ey * oe + b.ny * on;
				double pz = b.hz + b.ez * oe + b.nz * on;
				double l = Math.sqrt(px * px + py * py + pz * pz);
				double k = (OrbitBeats.R_P + alt) / l;
				int o = (i * w + j) * 5;
				patch[o] = px * k;
				patch[o + 1] = py * k;
				patch[o + 2] = pz * k;
				patch[o + 3] = fade;
				patch[o + 4] = oe;
			}
		}
		for (int i = 0; i < PATCH_RINGS; i++) {
			for (int j = 0; j < PATCH_SEG; j++) {
				discVertex(f, s, i, j, w, radius, tiles, drift, rgb, alpha);
				discVertex(f, s, i, j + 1, w, radius, tiles, drift, rgb, alpha);
				discVertex(f, s, i + 1, j + 1, w, radius, tiles, drift, rgb, alpha);
				discVertex(f, s, i + 1, j, w, radius, tiles, drift, rgb, alpha);
			}
		}
	}

	private void discVertex(StageFrame f, EpicRenderer.Sink s, int i, int j, int w, double radius, double tiles, float drift, int rgb, float alpha) {
		int o = (i * w + j) * 5;
		double a = Math.PI * 2.0D * j / PATCH_SEG;
		double rr = (double) i / PATCH_RINGS;
		float uu = (float) (0.5D + 0.5D * rr * Math.cos(a) * tiles) + drift;
		float vv = (float) (0.5D + 0.5D * rr * Math.sin(a) * tiles);
		f.vertex(s, patch[o], patch[o + 1], patch[o + 2], uu, vv, StageFrame.argb((float) (alpha * patch[o + 3]), rgb));
	}

	// ================================================================ per-vertex colours

	@Override
	public int argb(int layer, double nx, double ny, double nz, double px, double py, double pz, float u, float v) {
		double ns = nx * b.sx + ny * b.sy + nz * b.sz;
		switch (layer) {
			case L_PLANET -> {
				double l = 0.08D + 0.92D * Math.pow(Math.max(0.0D, ns), 0.8D);
				// a warm band along the terminator
				double w = Math.max(0.0D, 1.0D - Math.abs(ns - 0.04D) / 0.16D) * 0.45D;
				return 0xFF000000 | channel(l) << 16 | channel(l * (1.0D - 0.24D * w)) << 8 | channel(l * (1.0D - 0.42D * w));
			}
			case L_NIGHT -> {
				double night = OrbitBeats.smoothstep(0.1D, -0.15D, ns);
				return StageFrame.argb((float) (0.95D * night), 0xFFFFFF);
			}
			case L_CLOUDS -> {
				double l = Math.max(0.0D, (ns + 0.08D) / 1.08D);
				double g = 0.06D + 0.94D * l;
				double w = Math.max(0.0D, 1.0D - Math.abs(ns - 0.03D) / 0.14D) * 0.5D;
				int rgb = channel(g) << 16 | channel(g * (1.0D - 0.2D * w)) << 8 | channel(g * (1.0D - 0.38D * w));
				return StageFrame.argb((float) ((0.15D + 0.85D * l) * 0.95D), rgb);
			}
			case L_ATMO -> {
				double vx = eyeX - px;
				double vy = eyeY - py;
				double vz = eyeZ - pz;
				double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
				double nv = vl > 0.0D ? Math.abs((nx * vx + ny * vy + nz * vz) / vl) : 1.0D;
				double fres = Math.pow(1.0D - nv, shellPower);
				double sunf = 0.25D + 0.75D * OrbitBeats.smoothstep(-0.3D, 0.4D, ns);
				return StageFrame.argb((float) Math.min(1.0D, shellAlpha * fres * sunf), atmoColor(ns));
			}
			default -> {
				double l = 0.06D + 0.94D * Math.max(0.0D, ns);
				int g = channel(l);
				return 0xFF000000 | g << 16 | g << 8 | g;
			}
		}
	}

	/** The atmosphere colour at n·sun: day 7EC8FF → dusk FF9A5A → night 2A3A8A. */
	static int atmoColor(double ns) {
		if (ns >= 0.25D) {
			return ATMO_DAY;
		}
		if (ns >= -0.1D) {
			return StageFrame.lerpColor(0xFF000000 | ATMO_DUSK, 0xFF000000 | ATMO_DAY, (float) ((ns + 0.1D) / 0.35D)) & 0xFFFFFF;
		}
		return StageFrame.lerpColor(0xFF000000 | ATMO_NIGHT, 0xFF000000 | ATMO_DUSK, (float) OrbitBeats.clamp((ns + 0.45D) / 0.35D)) & 0xFFFFFF;
	}

	private static int channel(double v) {
		return Math.max(0, Math.min(255, (int) Math.round(v * 255.0D)));
	}

	private static int scale(int rgb, double k) {
		return channel((rgb >> 16 & 0xFF) / 255.0D * k) << 16 | channel((rgb >> 8 & 0xFF) / 255.0D * k) << 8 | channel((rgb & 0xFF) / 255.0D * k);
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** Grid sizes for {@code /sinematik kosmik3d info}. */
	String gridInfo() {
		int[] grid = pack ? PLANET_GRID_PACK[q] : PLANET_GRID[q];
		return "planet " + grid[0] + "x" + grid[1] + (planetTex == OrbitTex.BAKED_PLANET ? " (baked)" : " (static)");
	}
}
