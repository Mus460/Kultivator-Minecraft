package dev.kultivator.client.epic.kosmik.orbit;

import dev.kultivator.client.epic.kosmik.KosmikMesh;
import dev.kultivator.client.epic.kosmik.StageFrame;
import dev.kultivator.client.epic.render.EpicRenderer;

/**
 * Draws one sphere body or shell of the Orbit stage (DESIGN 32.2.1) from a cached {@link KosmikMesh.Sphere} grid: the
 * CPU drops quads whose four corners face away from the eye, quads outside the view cone (the dome's cone + 12°) and,
 * for glows, quads whose four colours are below alpha 0.02. The visible quads are counted first and the whole layer asks
 * the budget once ({@link StageFrame#take}); a refused layer is skipped, never half-drawn. Every vertex is mapped through
 * the diorama at most once per draw. Scratch arrays grow once (no allocation per frame).
 */
final class OrbitSphere {

	/** The colour of a vertex of a layer: unit normal n (after the spin), stage point P, texture (u, v). */
	interface Shader {
		int argb(int layer, double nx, double ny, double nz, double px, double py, double pz, float u, float v);
	}

	private float[] drawn = new float[0];
	private double[] point = new double[0];
	private float[] normal = new float[0];
	private boolean[] facing = new boolean[0];
	private boolean[] cone = new boolean[0];
	private int[] color = new int[0];
	private int[] colorMark = new int[0];
	private int[] mapMark = new int[0];
	private boolean[] quadOn = new boolean[0];
	private int mark;
	private final float[] tmp = new float[3];

	/**
	 * Draws the sphere of radius {@code r} centred at C, turned by {@code spin} about +Y, outward faces only. Returns the
	 * quads written, 0 when nothing is visible, −1 when the budget refused the layer.
	 */
	int draw(StageFrame f, EpicRenderer.Sink s, KosmikMesh.Sphere m, double cx, double cy, double cz, double r, double spin, Shader shader,
			 int layer, boolean alphaSkip) {
		int verts = m.vertices();
		int quads = m.quads();
		ensure(verts, quads);
		mark++;
		if (mark == Integer.MAX_VALUE) {
			mark = 1;
			java.util.Arrays.fill(colorMark, 0);
			java.util.Arrays.fill(mapMark, 0);
		}
		double ex = f.ex;
		double ey = f.ey;
		double ez = f.ez;
		double fx = f.forward.x;
		double fy = f.forward.y;
		double fz = f.forward.z;
		// the view cone (the dome's: diagonal half-FOV + 12°); off when the eye is close to the surface (large quads)
		double halfV = Math.toRadians(Math.max(10.0D, Math.min(170.0D, f.fovDeg)) * 0.5D);
		double diag = Math.atan(Math.tan(halfV) * Math.sqrt(1.0D + f.aspect * (double) f.aspect));
		double cosCone = Math.cos(Math.min(Math.PI, diag + Math.toRadians(12.0D)));
		double toCentre = Math.sqrt((ex - cx) * (ex - cx) + (ey - cy) * (ey - cy) + (ez - cz) * (ez - cz));
		double quadSize = r * Math.PI / m.rings;
		boolean coneCull = toCentre - r > 3.0D * quadSize;
		double c = Math.cos(spin);
		double sn = Math.sin(spin);
		for (int k = 0; k < verts; k++) {
			double x = m.pos[k * 3];
			double y = m.pos[k * 3 + 1];
			double z = m.pos[k * 3 + 2];
			double nx = x * c + z * sn;
			double nz = -x * sn + z * c;
			normal[k * 3] = (float) nx;
			normal[k * 3 + 1] = (float) y;
			normal[k * 3 + 2] = (float) nz;
			double px = cx + nx * r;
			double py = cy + y * r;
			double pz = cz + nz * r;
			point[k * 3] = px;
			point[k * 3 + 1] = py;
			point[k * 3 + 2] = pz;
			double vx = ex - px;
			double vy = ey - py;
			double vz = ez - pz;
			facing[k] = nx * vx + y * vy + nz * vz > 0.0D;
			if (coneCull) {
				double d = Math.sqrt(vx * vx + vy * vy + vz * vz);
				cone[k] = d < 1.0E-6D || -(vx * fx + vy * fy + vz * fz) / d >= cosCone;
			} else {
				cone[k] = true;
			}
		}
		int count = 0;
		for (int i = 0; i < m.rings; i++) {
			for (int j = 0; j < m.segments; j++) {
				int q = i * m.segments + j;
				quadOn[q] = false;
				int a = m.index(i, j);
				int b = m.index(i, j + 1);
				int cc = m.index(i + 1, j + 1);
				int d = m.index(i + 1, j);
				if (!facing[a] && !facing[b] && !facing[cc] && !facing[d]) {
					continue;
				}
				if (!cone[a] && !cone[b] && !cone[cc] && !cone[d]) {
					continue;
				}
				if (alphaSkip) {
					int ca = shade(m, a, shader, layer);
					int cb = shade(m, b, shader, layer);
					int ccol = shade(m, cc, shader, layer);
					int cd = shade(m, d, shader, layer);
					if ((ca >>> 24) <= 5 && (cb >>> 24) <= 5 && (ccol >>> 24) <= 5 && (cd >>> 24) <= 5) {
						continue;
					}
				}
				quadOn[q] = true;
				count++;
			}
		}
		if (count == 0) {
			return 0;
		}
		if (!f.take(count)) {
			return -1;
		}
		for (int i = 0; i < m.rings; i++) {
			for (int j = 0; j < m.segments; j++) {
				if (!quadOn[i * m.segments + j]) {
					continue;
				}
				int a = m.index(i, j);
				int b = m.index(i, j + 1);
				int cc = m.index(i + 1, j + 1);
				int d = m.index(i + 1, j);
				// outward: a, d, c, b is counter-clockwise seen from outside (as StageFrame.sphere)
				put(f, s, m, a, shader, layer);
				put(f, s, m, d, shader, layer);
				put(f, s, m, cc, shader, layer);
				put(f, s, m, b, shader, layer);
			}
		}
		return count;
	}

	private int shade(KosmikMesh.Sphere m, int k, Shader shader, int layer) {
		if (colorMark[k] != mark) {
			colorMark[k] = mark;
			color[k] = shader.argb(layer, normal[k * 3], normal[k * 3 + 1], normal[k * 3 + 2], point[k * 3], point[k * 3 + 1], point[k * 3 + 2],
					m.uv[k * 2], m.uv[k * 2 + 1]);
		}
		return color[k];
	}

	private void put(StageFrame f, EpicRenderer.Sink s, KosmikMesh.Sphere m, int k, Shader shader, int layer) {
		if (mapMark[k] != mark) {
			mapMark[k] = mark;
			f.map(point[k * 3], point[k * 3 + 1], point[k * 3 + 2], tmp);
			drawn[k * 3] = tmp[0];
			drawn[k * 3 + 1] = tmp[1];
			drawn[k * 3 + 2] = tmp[2];
		}
		f.raw(s, drawn[k * 3], drawn[k * 3 + 1], drawn[k * 3 + 2], m.uv[k * 2], m.uv[k * 2 + 1], shade(m, k, shader, layer));
	}

	private void ensure(int verts, int quads) {
		if (facing.length < verts) {
			int n = verts + 64;
			drawn = new float[n * 3];
			point = new double[n * 3];
			normal = new float[n * 3];
			facing = new boolean[n];
			cone = new boolean[n];
			color = new int[n];
			colorMark = new int[n];
			mapMark = new int[n];
		}
		if (quadOn.length < quads) {
			quadOn = new boolean[quads + 64];
		}
	}
}
