package dev.kultivator.client.epic.kosmik.surga;

import dev.kultivator.client.epic.kosmik.StageFrame;
import dev.kultivator.client.epic.render.EpicRenderer;

/**
 * The Heaven stage's drawing helpers on top of {@link StageFrame}: a cheap sphere-in-view-cone test for the frame, a
 * camera-facing beam with u across and v along (our shaft, beam and waterfall textures), a billboard with a sub-rectangle
 * of its texture (the petal frames) and points drawn with an item's cluster scale (decorations hugging an item model, so
 * they keep their depth against it, 32.1.3). Scratch arrays only; no allocation per frame.
 */
final class SurgaDraw {

	private double ex;
	private double ey;
	private double ez;
	private double fx;
	private double fy;
	private double fz;
	private double cosH;
	private double sinH;

	private final float[] a = new float[3];
	private final float[] b = new float[3];
	private final float[] c = new float[3];
	private final float[] d = new float[3];
	private final float[] centre = new float[3];
	private float clusterK;
	private double clusterX;
	private double clusterY;
	private double clusterZ;

	/** Sets the frame's eye and view cone (the diagonal half-FOV + 12°, like the dome's). */
	void begin(StageFrame frame) {
		ex = frame.ex;
		ey = frame.ey;
		ez = frame.ez;
		fx = frame.forward.x;
		fy = frame.forward.y;
		fz = frame.forward.z;
		double halfV = Math.toRadians(Math.max(10.0F, Math.min(170.0F, frame.fovDeg)) * 0.5D);
		double diag = Math.atan(Math.tan(halfV) * Math.sqrt(1.0D + frame.aspect * frame.aspect)) + Math.toRadians(12.0D);
		diag = Math.min(Math.PI * 0.5D, diag);
		cosH = Math.cos(diag);
		sinH = Math.sin(diag);
	}

	/** True when a sphere of radius r at P may be on screen (sphere against the view cone; conservative). */
	boolean sees(double x, double y, double z, double r) {
		double vx = x - ex;
		double vy = y - ey;
		double vz = z - ez;
		double along = vx * fx + vy * fy + vz * fz;
		double d2 = vx * vx + vy * vy + vz * vz;
		if (d2 <= r * r) {
			return true;
		}
		double side = Math.sqrt(Math.max(0.0D, d2 - along * along));
		// the distance from P to the cone's surface (negative inside)
		return side * cosH - along * sinH <= r;
	}

	/** The stage distance from the eye. */
	double distance(double x, double y, double z) {
		double vx = x - ex;
		double vy = y - ey;
		double vz = z - ez;
		return Math.sqrt(vx * vx + vy * vy + vz * vz);
	}

	double eyeY() {
		return ey;
	}

	/**
	 * A camera-facing beam from A to B in {@code segments} pieces (mapped per vertex), width {@code w0} → {@code w1},
	 * colour {@code c0} → {@code c1}; u 0..1 across, v {@code v0} → {@code v1} along. Returns the quads written.
	 */
	int beam(StageFrame frame, EpicRenderer.Sink s, double ax, double ay, double az, double bx, double by, double bz, int segments, double w0,
			 double w1, int c0, int c1, float v0, float v1) {
		double tx = bx - ax;
		double ty = by - ay;
		double tz = bz - az;
		int n = Math.max(1, segments);
		int quads = 0;
		for (int i = 0; i < n; i++) {
			float t0 = i / (float) n;
			float t1 = (i + 1) / (float) n;
			side(frame, ax + tx * t0, ay + ty * t0, az + tz * t0, tx, ty, tz, (w0 + (w1 - w0) * t0) * 0.5D, a, b);
			side(frame, ax + tx * t1, ay + ty * t1, az + tz * t1, tx, ty, tz, (w0 + (w1 - w0) * t1) * 0.5D, d, c);
			int ca = StageFrame.lerpColor(c0, c1, t0);
			int cb = StageFrame.lerpColor(c0, c1, t1);
			float va = v0 + (v1 - v0) * t0;
			float vb = v0 + (v1 - v0) * t1;
			frame.raw(s, a[0], a[1], a[2], 0.0F, va, ca);
			frame.raw(s, b[0], b[1], b[2], 1.0F, va, ca);
			frame.raw(s, c[0], c[1], c[2], 1.0F, vb, cb);
			frame.raw(s, d[0], d[1], d[2], 0.0F, vb, cb);
			quads++;
		}
		return quads;
	}

	/** The two drawn edge points of a beam at P with tangent T and half width w (side = T × view). */
	private void side(StageFrame frame, double px, double py, double pz, double tx, double ty, double tz, double w, float[] left, float[] right) {
		double vx = px - ex;
		double vy = py - ey;
		double vz = pz - ez;
		double sx = ty * vz - tz * vy;
		double sy = tz * vx - tx * vz;
		double sz = tx * vy - ty * vx;
		double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (l < 1.0E-9D) {
			sx = frame.right.x;
			sy = frame.right.y;
			sz = frame.right.z;
		} else {
			sx /= l;
			sy /= l;
			sz /= l;
		}
		frame.map(px - sx * w, py - sy * w, pz - sz * w, left);
		frame.map(px + sx * w, py + sy * w, pz + sz * w, right);
	}

	/** A camera-facing square at P (mapped corners) with texture rectangle u0..u1 × v0..v1, turned by {@code roll}. */
	void sprite(StageFrame frame, EpicRenderer.Sink s, double x, double y, double z, double half, double roll, float u0, float v0, float u1,
				float v1, int argb) {
		double cr = Math.cos(roll);
		double sr = Math.sin(roll);
		double rx = (frame.right.x * cr + frame.up.x * sr) * half;
		double ry = (frame.right.y * cr + frame.up.y * sr) * half;
		double rz = (frame.right.z * cr + frame.up.z * sr) * half;
		double ux = (-frame.right.x * sr + frame.up.x * cr) * half;
		double uy = (-frame.right.y * sr + frame.up.y * cr) * half;
		double uz = (-frame.right.z * sr + frame.up.z * cr) * half;
		frame.map(x - rx - ux, y - ry - uy, z - rz - uz, a);
		frame.map(x + rx - ux, y + ry - uy, z + rz - uz, b);
		frame.map(x + rx + ux, y + ry + uy, z + rz + uz, c);
		frame.map(x - rx + ux, y - ry + uy, z - rz + uz, d);
		frame.raw(s, a[0], a[1], a[2], u0, v1, argb);
		frame.raw(s, b[0], b[1], b[2], u1, v1, argb);
		frame.raw(s, c[0], c[1], c[2], u1, v0, argb);
		frame.raw(s, d[0], d[1], d[2], u0, v0, argb);
	}

	/** Starts a cluster at stage point C: following {@link #clusterVertex} calls share C's uniform scale (32.1.3). */
	void cluster(StageFrame frame, double cx, double cy, double cz) {
		clusterK = frame.cluster(cx, cy, cz, centre);
		clusterX = cx;
		clusterY = cy;
		clusterZ = cz;
	}

	/** One vertex of the current cluster at stage point P. */
	void clusterVertex(StageFrame frame, EpicRenderer.Sink s, double x, double y, double z, float u, float v, int argb) {
		frame.raw(s, centre[0] + (float) ((x - clusterX) * clusterK), centre[1] + (float) ((y - clusterY) * clusterK),
				centre[2] + (float) ((z - clusterZ) * clusterK), u, v, argb);
	}
}
