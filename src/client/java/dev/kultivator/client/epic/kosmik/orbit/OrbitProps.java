package dev.kultivator.client.epic.kosmik.orbit;

import dev.kultivator.client.epic.kosmik.StageFrame;
import dev.kultivator.client.epic.render.EpicRenderer;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * The props of the Orbit beats (DESIGN 32.2.3): the Heaven-Splitting Sword with its rune rings, gold trail, plasma
 * sheath, head glow and the cloud punch; the seven Dipper cores with their coronas, lines and star beams; Gunung's lifted
 * cells with their dust. Item models go through {@link StageFrame#item} (the stack is made on first draw); an item's own
 * decorations (rings, sheath) use the same uniform cluster scale about the item's origin, so they stay glued to it.
 * Long things (trails, beams, lines, cells) are mapped per vertex. Caps per quality: ≤ 2 / 8 / 16 item models and
 * ≤ 40 / 160 / 320 quads (C32.7); every group asks the budget for its quads first.
 */
final class OrbitProps {

	/**
	 * Props quads per quality (C32.7: 40 / 160 / 320); Minimal gets 52 so its 4 × 4 lifted cells (about 3 faces each) still
	 * fit, and draws no dust.
	 */
	private static final int[] QUAD_CAP = {52, 160, 320};
	private static final int TRAIL_POINTS = 16;
	private static final int BEAM_POINTS = 8;
	private static final int LINE_POINTS = 4;
	private static final int CONE_SEG = 8;
	private static final int PUNCH_SEG = 24;
	/** The plasma cone: offsets from the grip along the blade (× length; apex ahead of the tip), radii (× length), v. */
	private static final double[] CONE_AT = {1.07D, 0.90D, 0.64D};
	private static final double[] CONE_R = {0.0D, 0.12D, 0.24D};
	private static final float[] CONE_V = {0.0F, 0.35F, 1.0F};
	/** The cloud punch ring's radii (× its radius): inner and outer edges fade to 0. */
	private static final double[] PUNCH_R = {0.7D, 1.0D, 1.3D};

	private final OrbitBeats b;
	private final float[] c0 = new float[3];
	private final double[] pts = new double[TRAIL_POINTS * 3];
	private final Quaternionf rot = new Quaternionf();
	private final Matrix3f basis = new Matrix3f();
	private int used;
	private int cap;
	/** Prop groups the budget refused earlier in this shot (they stay off until the next shot: no flicker at the cap). */
	private int off;
	private static final int RINGS = 1;
	private static final int PLASMA = 1 << 1;
	private static final int TRAIL = 1 << 2;
	private static final int HEAD = 1 << 3;
	private static final int PUNCH = 1 << 4;
	private static final int CORES = 1 << 5;
	private static final int LINES = 1 << 6;
	private static final int BEAMS = 1 << 7;
	private static final int CELLS = 1 << 8;
	private static final int DUST = 1 << 9;

	OrbitProps(OrbitBeats beats) {
		this.b = beats;
	}

	/** Draws the props of the running beat. */
	void render(StageFrame f) {
		cap = QUAD_CAP[b.quality];
		used = 0;
		if (b.swordOn) {
			sword(f);
		}
		if (b.dipperOn) {
			dipper(f);
		}
		if (b.cellsOn) {
			cells(f);
		}
	}

	/** A shot starts: every group may draw again. */
	void reset() {
		off = 0;
	}

	/** The groups refused in this shot (for info). */
	int off() {
		return off;
	}

	/** Takes {@code quads} for one group from the props cap and the frame budget; a refused group stays off for the shot. */
	private boolean take(StageFrame f, int quads, int group) {
		if ((off & group) != 0) {
			return false;
		}
		if (quads <= 0) {
			return true;
		}
		if (used + quads > cap || !f.take(quads)) {
			off |= group;
			return false;
		}
		used += quads;
		return true;
	}

	// ================================================================ the sword

	private void sword(StageFrame f) {
		double half = b.swordLen * 0.5D;
		boolean visible = f.inView(b.cx, b.cy, b.cz, half * 1.4D);
		if (visible) {
			// model X → bx, Y → the blade, Z → bz (columns of the rotation)
			basis.set((float) b.bxx, (float) b.bxy, (float) b.bxz, (float) b.dx, (float) b.dy, (float) b.dz, (float) b.bzx, (float) b.bzy,
					(float) b.bzz);
			rot.setFromNormalized(basis);
			f.item(OrbitBeats.SWORD_MODEL, b.gx, b.gy, b.gz, rot, b.itemScale);
			rings(f);
			plasma(f);
		}
		trail(f);
		headGlow(f);
		punch(f);
	}

	/** The rune rings: one quad each in the plane across the blade, glued to the item by its cluster scale. */
	private void rings(StageFrame f) {
		int n = 0;
		for (int i = 0; i < b.rings; i++) {
			if (b.ringAlpha[i] > 0.01F) {
				n++;
			}
		}
		if (n == 0 || !take(f, n, RINGS)) {
			return;
		}
		float k = f.cluster(b.gx, b.gy, b.gz, c0);
		EpicRenderer.Sink s = f.add(OrbitTex.RUNE_RING);
		double len = b.swordLen;
		for (int i = 0; i < b.rings; i++) {
			float alpha = b.ringAlpha[i];
			if (alpha <= 0.01F) {
				continue;
			}
			double along = OrbitBeats.RING_AT[i] * len;
			double ox = b.dx * along;
			double oy = b.dy * along;
			double oz = b.dz * along;
			double r = OrbitBeats.RING_R[i] * b.fs * b.ringScale;
			double ca = Math.cos(b.ringAngle[i]);
			double sa = Math.sin(b.ringAngle[i]);
			double ax = (b.bxx * ca + b.bzx * sa) * r;
			double ay = (b.bxy * ca + b.bzy * sa) * r;
			double az = (b.bxz * ca + b.bzz * sa) * r;
			double qx = (-b.bxx * sa + b.bzx * ca) * r;
			double qy = (-b.bxy * sa + b.bzy * ca) * r;
			double qz = (-b.bxz * sa + b.bzz * ca) * r;
			int argb = StageFrame.argb(Math.min(1.0F, alpha * b.ringGlow * 0.95F), b.ringGlow > 1.01F ? 0xFFF6DC : 0xFFEFC8);
			cluster(f, s, k, ox - ax - qx, oy - ay - qy, oz - az - qz, 0.0F, 1.0F, argb);
			cluster(f, s, k, ox + ax - qx, oy + ay - qy, oz + az - qz, 1.0F, 1.0F, argb);
			cluster(f, s, k, ox + ax + qx, oy + ay + qy, oz + az + qz, 1.0F, 0.0F, argb);
			cluster(f, s, k, ox - ax + qx, oy - ay + qy, oz - az + qz, 0.0F, 0.0F, argb);
		}
	}

	/** A vertex at an offset from the item's origin, drawn with the item's cluster scale. */
	private void cluster(StageFrame f, EpicRenderer.Sink s, float k, double ox, double oy, double oz, float u, float v, int argb) {
		f.raw(s, (float) (c0[0] + ox * k), (float) (c0[1] + oy * k), (float) (c0[2] + oz * k), u, v, argb);
	}

	/**
	 * The plasma sheath of the re-entry (below 60 sm): an 8-segment cone over the leading third, apex just ahead of the
	 * tip, white at the point to orange and violet at its rim; its alpha follows the depth into the atmosphere.
	 */
	private void plasma(StageFrame f) {
		if (b.heat <= 0.01F || !take(f, CONE_SEG * 2, PLASMA)) {
			return;
		}
		float k = f.cluster(b.gx, b.gy, b.gz, c0);
		EpicRenderer.Sink s = f.add(OrbitTex.PLASMA);
		double len = b.swordLen;
		// offsets from the grip along the blade: the apex ahead of the tip, two rings behind it
		int argb = StageFrame.argb(b.heat, 0xFFFFFF);
		double turn = b.clock * 0.35D;
		for (int band = 0; band < 2; band++) {
			double at0 = CONE_AT[band] * len;
			double at1 = CONE_AT[band + 1] * len;
			double r0 = CONE_R[band] * len;
			double r1 = CONE_R[band + 1] * len;
			for (int j = 0; j < CONE_SEG; j++) {
				coneVertex(f, s, k, at0, r0, turn, j, CONE_V[band], argb);
				coneVertex(f, s, k, at0, r0, turn, j + 1, CONE_V[band], argb);
				coneVertex(f, s, k, at1, r1, turn, j + 1, CONE_V[band + 1], argb);
				coneVertex(f, s, k, at1, r1, turn, j, CONE_V[band + 1], argb);
			}
		}
	}

	private void coneVertex(StageFrame f, EpicRenderer.Sink s, float k, double along, double radius, double turn, int j, float v, int argb) {
		double a = Math.PI * 2.0D * j / CONE_SEG + turn;
		double ca = Math.cos(a) * radius;
		double sa = Math.sin(a) * radius;
		double ox = b.dx * along + b.bxx * ca + b.bzx * sa;
		double oy = b.dy * along + b.bxy * ca + b.bzy * sa;
		double oz = b.dz * along + b.bxz * ca + b.bzz * sa;
		cluster(f, s, k, ox, oy, oz, j / (float) CONE_SEG, v, argb);
	}

	/** The gold trail behind the diving sword (16 points from the tip back along its path). */
	private void trail(StageFrame f) {
		if (b.trailLen <= 0.5D || b.trailAlpha <= 0.01F || !take(f, TRAIL_POINTS - 1, TRAIL)) {
			return;
		}
		for (int i = 0; i < TRAIL_POINTS; i++) {
			double d = b.trailLen * i / (TRAIL_POINTS - 1);
			pts[i * 3] = b.tx - b.dx * d;
			pts[i * 3 + 1] = b.ty - b.dy * d;
			pts[i * 3 + 2] = b.tz - b.dz * d;
		}
		int head = b.heat > 0.05F ? 0xFFF4D8 : 0xFFE6A0;
		f.ribbon(f.add(OrbitTex.TRAIL), pts, TRAIL_POINTS, b.trailW0, b.trailW1, StageFrame.argb(0.95F * b.trailAlpha, head),
				StageFrame.argb(0.0F, 0xFF6A20), (float) (-b.clock * 0.08D));
	}

	/** The burning head at the tip (the dive and the re-entry). */
	private void headGlow(StageFrame f) {
		if (b.headGlow <= 0.01F || !take(f, 2, HEAD)) {
			return;
		}
		EpicRenderer.Sink s = f.add(OrbitTex.SUN_CORE);
		double half = b.swordLen * (0.25D + 0.35D * b.heat);
		f.billboard(s, b.tx, b.ty, b.tz, half, 0.0F, StageFrame.argb(0.85F * b.headGlow, 0xFFE0B0));
		f.billboard(s, b.tx, b.ty, b.tz, half * 2.6D, 0.0F, StageFrame.argb(0.35F * b.headGlow, b.heat > 0.3F ? 0xFF8A40 : 0xFFD070));
	}

	/** The cloud punch (pedang_masuk from 0.85): a ring expanding on the cloud deck over home (deterministic growth). */
	private void punch(StageFrame f) {
		if (b.punch < 0.0F || !take(f, PUNCH_SEG * 2, PUNCH)) {
			return;
		}
		double p = b.punch;
		double grow = 1.0D - Math.pow(1.0D - p, 3.0D);
		double radius = Math.max(1.0D, 75.0D * b.fs * grow);
		float alpha = (float) (0.9D * Math.pow(1.0D - p, 1.2D));
		EpicRenderer.Sink s = f.add(OrbitTex.ATMO);
		double alt = 11.0D;
		double ox = b.hx + b.upx * alt;
		double oy = b.hy + b.upy * alt;
		double oz = b.hz + b.upz * alt;
		for (int band = 0; band < 2; band++) {
			// the middle radius carries the alpha, both edges fade to 0
			float a0 = band == 0 ? 0.0F : alpha;
			float a1 = band == 0 ? alpha : 0.0F;
			for (int j = 0; j < PUNCH_SEG; j++) {
				punchVertex(f, s, ox, oy, oz, radius * PUNCH_R[band], j, a0);
				punchVertex(f, s, ox, oy, oz, radius * PUNCH_R[band], j + 1, a0);
				punchVertex(f, s, ox, oy, oz, radius * PUNCH_R[band + 1], j + 1, a1);
				punchVertex(f, s, ox, oy, oz, radius * PUNCH_R[band + 1], j, a1);
			}
		}
	}

	private void punchVertex(StageFrame f, EpicRenderer.Sink s, double ox, double oy, double oz, double r, int j, float alpha) {
		double a = Math.PI * 2.0D * j / PUNCH_SEG;
		double ce = Math.cos(a) * r;
		double cn = Math.sin(a) * r;
		f.vertex(s, ox + b.ex * ce + b.nx * cn, oy + b.ey * ce + b.ny * cn, oz + b.ez * ce + b.nz * cn, 0.5F, 0.5F,
				StageFrame.argb(alpha, 0xFFF0D8));
	}

	// ================================================================ the Dipper

	private void dipper(StageFrame f) {
		double fsh = b.fsh;
		boolean items = b.quality >= 1;
		EpicRenderer.Sink glow = f.add(OrbitTex.SUN_CORE);
		EpicRenderer.Sink spike = f.add(OrbitTex.STAR_SPIKE);
		// each group asks once for all its quads (a group is never half-drawn)
		int coresLit = 0;
		int linesOn = 0;
		int beamsOn = 0;
		for (int i = 0; i < 7; i++) {
			coresLit += b.coreLit[i] > 0.01F ? 1 : 0;
			linesOn += i < 6 && b.line[i] > 0.01F ? 1 : 0;
			beamsOn += b.beam[i] > 0.01F ? 1 : 0;
		}
		boolean coresOk = take(f, coresLit * 2, CORES);
		for (int i = 0; i < 7 && coresOk; i++) {
			float lit = b.coreLit[i];
			if (lit <= 0.01F) {
				continue;
			}
			double x = b.core[i * 3];
			double y = b.core[i * 3 + 1];
			double z = b.core[i * 3 + 2];
			float breathe = (float) (0.85D + 0.15D * Math.sin(b.clock * 0.21D + 1.3D * i));
			float alpha = lit * breathe;
			if (items && lit > 0.4F && f.inView(x, y, z, OrbitBeats.CORE_SIZE * fsh)) {
				rot.rotationY((float) (Math.toRadians(1.0D) * b.clock + i));
				f.item(OrbitBeats.CORE_MODEL, x, y, z, rot, OrbitBeats.CORE_SIZE * fsh);
			}
			f.billboard(glow, x, y, z, 9.0D * fsh, 0.0F, StageFrame.argb(alpha, 0xE8DCFF));
			f.billboard(spike, x, y, z, 24.0D * fsh, (float) (Math.toRadians(1.0D) * b.clock + i * 0.4D), StageFrame.argb(0.9F * alpha, 0xD8C8FF));
		}
		EpicRenderer.Sink lines = f.add(OrbitTex.STAR_LINE);
		boolean linesOk = take(f, linesOn * (LINE_POINTS - 1), LINES);
		for (int i = 0; i < 6 && linesOk; i++) {
			float p = b.line[i];
			if (p <= 0.01F) {
				continue;
			}
			segment(i, i + 1, p, b.core, b.core, LINE_POINTS);
			int c = StageFrame.argb(0.85F, 0xC9A8FF);
			f.ribbon(lines, pts, LINE_POINTS, 1.6D * fsh, 1.6D * fsh, c, c, (float) (-b.clock * 0.05D));
		}
		EpicRenderer.Sink beams = f.add(OrbitTex.BEAM);
		double crossAlt = 30.0D;
		double coreAlt = OrbitBeats.DIPPER_HEIGHT * fsh;
		double sCross = (coreAlt - crossAlt) / coreAlt;
		boolean beamsOk = take(f, beamsOn * (BEAM_POINTS - 1 + 2), BEAMS);
		for (int i = 0; i < 7 && beamsOk; i++) {
			float p = b.beam[i];
			if (p <= 0.01F) {
				continue;
			}
			segment(i, i, p, b.core, b.ground, BEAM_POINTS);
			f.ribbon(beams, pts, BEAM_POINTS, 5.0D * fsh, 3.0D * fsh, StageFrame.argb(0.95F, 0xF4EEFF), StageFrame.argb(0.9F, 0xB57CFF),
					(float) (-b.clock * 0.05D + i * 0.3D));
			if (p > sCross) {
				// where the beam pierces the atmosphere: a soft glow on the shell
				double t = sCross;
				double x = b.core[i * 3] + (b.ground[i * 3] - b.core[i * 3]) * t;
				double y = b.core[i * 3 + 1] + (b.ground[i * 3 + 1] - b.core[i * 3 + 1]) * t;
				double z = b.core[i * 3 + 2] + (b.ground[i * 3 + 2] - b.core[i * 3 + 2]) * t;
				float a = (float) Math.min(1.0D, (p - sCross) / 0.05D) * 0.7F;
				f.billboard(glow, x, y, z, 10.0D * fsh, 0.0F, StageFrame.argb(a, 0xC8B0FF));
			}
			if (p >= 1.0F) {
				float flare = (float) (0.8D + 0.2D * Math.sin(b.clock * 0.5D + i));
				f.billboard(glow, b.ground[i * 3], b.ground[i * 3 + 1], b.ground[i * 3 + 2], 14.0D * fsh, 0.0F, StageFrame.argb(flare, 0xF0E4FF));
			}
		}
	}

	/** Fills {@link #pts} with {@code n} points from {@code from[i]} toward {@code to[j]}, {@code p} of the way. */
	private void segment(int i, int j, float p, double[] from, double[] to, int n) {
		double ax = from[i * 3];
		double ay = from[i * 3 + 1];
		double az = from[i * 3 + 2];
		double bx = ax + (to[j * 3] - ax) * p;
		double by = ay + (to[j * 3 + 1] - ay) * p;
		double bz = az + (to[j * 3 + 2] - az) * p;
		for (int k = 0; k < n; k++) {
			double t = k / (double) (n - 1);
			pts[k * 3] = ax + (bx - ax) * t;
			pts[k * 3 + 1] = ay + (by - ay) * t;
			pts[k * 3 + 2] = az + (bz - az) * t;
		}
	}

	// ================================================================ the lifted cells

	private void cells(StageFrame f) {
		int n = b.cellCount;
		if (n == 0) {
			return;
		}
		double half = b.cellSize * 0.5D;
		// count the faces that face the eye first, then ask once
		int faces = 0;
		for (int k = 0; k < n; k++) {
			cellFrame(k);
			faces += visibleFaces(f, k, half);
		}
		int dust = b.quality == 0 ? 0 : Math.min(n, 6);
		if (faces == 0 || !take(f, faces, CELLS)) {
			return;
		}
		EpicRenderer.Sink top = f.solid(OrbitTex.CELL_TOP);
		EpicRenderer.Sink side = f.solid(OrbitTex.CELL_SIDE);
		EpicRenderer.Sink bottom = f.solid(OrbitTex.CELL_BOTTOM);
		int grass = b.colors.grass();
		for (int k = 0; k < n; k++) {
			cellFrame(k);
			drawCell(f, k, half, top, side, bottom, grass);
		}
		if (dust == 0 || !take(f, dust * 2, DUST)) {
			return;
		}
		EpicRenderer.Sink haze = f.glow(OrbitTex.ATMO);
		int step = Math.max(1, n / dust);
		for (int d = 0; d < dust; d++) {
			int k = Math.min(n - 1, d * step + step / 2);
			float rise = b.cellRise[k];
			if (rise <= 0.02F) {
				continue;
			}
			double x = b.cellPos[k * 3];
			double y = b.cellPos[k * 3 + 1];
			double z = b.cellPos[k * 3 + 2];
			double len = 40.0D * b.fsc * Math.min(1.0D, rise * 3.0D);
			for (int i = 0; i < 3; i++) {
				double t = len * i / 2.0D;
				pts[i * 3] = x - b.upx * t;
				pts[i * 3 + 1] = y - b.upy * t;
				pts[i * 3 + 2] = z - b.upz * t;
			}
			f.ribbon(haze, pts, 3, 3.0D * b.fsc, 0.6D * b.fsc, StageFrame.argb(0.35F * (1.0F - 0.5F * rise), 0x9A7A58),
					StageFrame.argb(0.0F, 0x9A7A58), 0.0F);
		}
	}

	// the current cell: centre and its rotated axes (X east, Y up, Z south: right-handed), half extents
	private double ccx;
	private double ccy;
	private double ccz;
	private final double[] axes = new double[9];
	private double hh;

	private void cellFrame(int k) {
		double angle = b.cellAngle[k];
		double kx = b.cellAxis[k * 3];
		double ky = b.cellAxis[k * 3 + 1];
		double kz = b.cellAxis[k * 3 + 2];
		rotate(b.ex, b.ey, b.ez, kx, ky, kz, angle, 0);
		rotate(b.upx, b.upy, b.upz, kx, ky, kz, angle, 3);
		rotate(-b.nx, -b.ny, -b.nz, kx, ky, kz, angle, 6);
		hh = b.cellHeight[k] * 0.5D;
		// the cell's base point is its bottom centre: the centre is half its height up (before the tumble)
		ccx = b.cellPos[k * 3] + b.upx * hh;
		ccy = b.cellPos[k * 3 + 1] + b.upy * hh;
		ccz = b.cellPos[k * 3 + 2] + b.upz * hh;
	}

	/** Rodrigues: v turned by {@code angle} about the unit axis k, into {@link #ax} at {@code o}. */
	private void rotate(double vx, double vy, double vz, double kx, double ky, double kz, double angle, int o) {
		double c = Math.cos(angle);
		double s = Math.sin(angle);
		double dot = kx * vx + ky * vy + kz * vz;
		double cx = ky * vz - kz * vy;
		double cy = kz * vx - kx * vz;
		double cz = kx * vy - ky * vx;
		axes[o] = vx * c + cx * s + kx * dot * (1.0D - c);
		axes[o + 1] = vy * c + cy * s + ky * dot * (1.0D - c);
		axes[o + 2] = vz * c + cz * s + kz * dot * (1.0D - c);
	}

	// faces: normal axis index (0 X, 1 Y, 2 Z) and sign; tangent a, b with a × b = n (b = up on the sides)
	private static final int[] FACE_N = {1, 1, 0, 0, 2, 2};
	private static final int[] FACE_S = {1, -1, 1, -1, 1, -1};

	private int visibleFaces(StageFrame f, int k, double half) {
		int count = 0;
		for (int i = 0; i < 6; i++) {
			if (faceVisible(f, i, half)) {
				count++;
			}
		}
		return count;
	}

	private boolean faceVisible(StageFrame f, int face, double half) {
		int a = FACE_N[face] * 3;
		double sgn = FACE_S[face];
		double ext = FACE_N[face] == 1 ? hh : half;
		double nxv = axes[a] * sgn;
		double nyv = axes[a + 1] * sgn;
		double nzv = axes[a + 2] * sgn;
		double px = ccx + nxv * ext;
		double py = ccy + nyv * ext;
		double pz = ccz + nzv * ext;
		return nxv * (f.ex - px) + nyv * (f.ey - py) + nzv * (f.ez - pz) > 0.0D;
	}

	private void drawCell(StageFrame f, int k, double half, EpicRenderer.Sink top, EpicRenderer.Sink side, EpicRenderer.Sink bottom, int grass) {
		for (int face = 0; face < 6; face++) {
			if (!faceVisible(f, face, half)) {
				continue;
			}
			int n = FACE_N[face];
			double sgn = FACE_S[face];
			double nxv = axes[n * 3] * sgn;
			double nyv = axes[n * 3 + 1] * sgn;
			double nzv = axes[n * 3 + 2] * sgn;
			double light = 0.5D + 0.5D * Math.max(0.0D, nxv * b.sx + nyv * b.sy + nzv * b.sz);
			// tangents: top (+Y): a = X, b = −Z; bottom (−Y): a = X, b = Z; sides: b = Y, a = Y × n
			double tax;
			double tay;
			double taz;
			double tbx;
			double tby;
			double tbz;
			double ea;
			double eb;
			EpicRenderer.Sink s;
			int rgb;
			if (n == 1) {
				tax = axes[0];
				tay = axes[1];
				taz = axes[2];
				double zs = sgn > 0.0D ? -1.0D : 1.0D;
				tbx = axes[6] * zs;
				tby = axes[7] * zs;
				tbz = axes[8] * zs;
				ea = half;
				eb = half;
				s = sgn > 0.0D ? top : bottom;
				rgb = sgn > 0.0D ? scaled(grass, light) : scaled(0xFFFFFF, light * 0.8D);
			} else {
				tbx = axes[3];
				tby = axes[4];
				tbz = axes[5];
				// a = Y × n
				tax = tby * nzv - tbz * nyv;
				tay = tbz * nxv - tbx * nzv;
				taz = tbx * nyv - tby * nxv;
				ea = half;
				eb = hh;
				s = side;
				rgb = scaled(0xFFFFFF, light);
			}
			double ext = n == 1 ? hh : half;
			double fx = ccx + nxv * ext;
			double fy = ccy + nyv * ext;
			double fz = ccz + nzv * ext;
			int argb = 0xFF000000 | rgb;
			// counter-clockwise seen from outside: −a−b, +a−b, +a+b, −a+b
			f.vertex(s, fx - tax * ea - tbx * eb, fy - tay * ea - tby * eb, fz - taz * ea - tbz * eb, 0.0F, 1.0F, argb);
			f.vertex(s, fx + tax * ea - tbx * eb, fy + tay * ea - tby * eb, fz + taz * ea - tbz * eb, 1.0F, 1.0F, argb);
			f.vertex(s, fx + tax * ea + tbx * eb, fy + tay * ea + tby * eb, fz + taz * ea + tbz * eb, 1.0F, 0.0F, argb);
			f.vertex(s, fx - tax * ea + tbx * eb, fy - tay * ea + tby * eb, fz - taz * ea + tbz * eb, 0.0F, 0.0F, argb);
		}
	}

	private static int scaled(int rgb, double k) {
		int r = (int) Math.max(0, Math.min(255, Math.round((rgb >> 16 & 0xFF) * k)));
		int g = (int) Math.max(0, Math.min(255, Math.round((rgb >> 8 & 0xFF) * k)));
		int bl = (int) Math.max(0, Math.min(255, Math.round((rgb & 0xFF) * k)));
		return r << 16 | g << 8 | bl;
	}
}
