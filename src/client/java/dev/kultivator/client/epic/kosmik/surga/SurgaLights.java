package dev.kultivator.client.epic.kosmik.surga;

import dev.kultivator.client.epic.kosmik.StageFrame;
import dev.kultivator.client.epic.render.EpicRenderer;

/**
 * The additive lights of the Heaven stage (DESIGN 32.3.1): the Nine Heavens as nine glowing rings (gold at ring 9, violet
 * at ring 1, alternately turning ±0.2°/tick, a passing prop lifting a ring's alpha softly), the golden light shafts of the
 * gate and the hall (alpha breathing by a slow cosine, never size), the gate glow behind the opening doors and the
 * waterfalls of light of the Heavenly River. Additive layers write depth, so they are written far to near by layer: rings,
 * waterfalls, the gate glow, the shafts.
 */
final class SurgaLights {

	private static final int[] RING_SEGMENTS = {10, 24, 32};
	private static final int[] SHAFTS = {4, 8, 16};
	private static final int[] FALLS = {0, 2, 4};
	private static final int SHAFT_PIECES = 3;
	private static final int FALL_PIECES = 5;
	/** Rows of nine_rings.png: 9 × 8 px of 72. */
	private static final float ROW = 8.0F / 72.0F;
	private static final float ROW_INSET = 0.5F / 72.0F;

	private final double[] ringAngle = new double[9];

	// ---------------------------------------------------------------- rings

	private static int ringSegments(int quality, boolean pack) {
		int q = Math.max(0, Math.min(2, quality));
		return pack ? Math.min(RING_SEGMENTS[q], q == 1 ? 16 : 24) : RING_SEGMENTS[q];
	}

	/** Visible ring quads this frame. */
	int countRings(StageFrame frame, SurgaDraw view, int quality, boolean pack, double time) {
		return rings(frame, view, null, quality, pack, time, null, false);
	}

	void drawRings(StageFrame frame, SurgaDraw view, int quality, boolean pack, double time, double[] boost) {
		rings(frame, view, frame.add(SurgaLayout.RINGS), quality, pack, time, boost, true);
	}

	private int rings(StageFrame frame, SurgaDraw view, EpicRenderer.Sink sink, int quality, boolean pack, double time, double[] boost, boolean draw) {
		int s = ringSegments(quality, pack);
		int quads = 0;
		for (int k = 1; k <= 9; k++) {
			double sign = (k & 1) == 0 ? 1.0D : -1.0D;
			ringAngle[k - 1] = Math.toRadians(k * 17.0D + sign * 0.2D * time);
			double alpha = 0.5D + 0.04D * k + (boost != null ? boost[k - 1] : 0.0D);
			quads += annulus(frame, view, sink, k, SurgaLayout.ringR(k), 0.09D * SurgaLayout.ringR(k), ringAngle[k - 1], s, 1.0F, alpha, draw);
			// Maximal: a turning glyph band inside the three highest rings
			if (quality >= 2 && !pack && k >= 7) {
				quads += annulus(frame, view, sink, k, SurgaLayout.ringR(k) * 0.84D, 0.04D * SurgaLayout.ringR(k), -ringAngle[k - 1] * 1.7D, s, 2.0F,
						alpha * 0.7D, draw);
			}
		}
		return quads;
	}

	/** One horizontal annulus of ring k's texture row round the Nine Heavens' column. */
	private static int annulus(StageFrame frame, SurgaDraw view, EpicRenderer.Sink sink, int k, double radius, double width, double angle,
							   int segments, float uWraps, double alpha, boolean draw) {
		double y = SurgaLayout.ringY(k);
		double cz = SurgaLayout.COLUMN_Z;
		double rIn = radius - width * 0.5D;
		double rOut = radius + width * 0.5D;
		float v0 = (k - 1) * ROW + ROW_INSET;
		float v1 = k * ROW - ROW_INSET;
		int argb = StageFrame.argb((float) Math.min(1.0D, alpha), 0xFFFFFF);
		int quads = 0;
		double step = Math.PI * 2.0D / segments;
		double reach = Math.max(width, radius * step) * 0.75D;
		for (int j = 0; j < segments; j++) {
			double a0 = angle + j * step;
			double a1 = a0 + step;
			double am = a0 + step * 0.5D;
			if (!view.sees(Math.cos(am) * radius, y, cz + Math.sin(am) * radius, reach)) {
				continue;
			}
			quads++;
			if (!draw || sink == null) {
				continue;
			}
			float u0 = uWraps * j / segments;
			float u1 = uWraps * (j + 1) / segments;
			double c0 = Math.cos(a0);
			double s0 = Math.sin(a0);
			double c1 = Math.cos(a1);
			double s1 = Math.sin(a1);
			frame.vertex(sink, c0 * rIn, y, cz + s0 * rIn, u0, v0, argb);
			frame.vertex(sink, c1 * rIn, y, cz + s1 * rIn, u1, v0, argb);
			frame.vertex(sink, c1 * rOut, y, cz + s1 * rOut, u1, v1, argb);
			frame.vertex(sink, c0 * rOut, y, cz + s0 * rOut, u0, v1, argb);
		}
		return quads;
	}

	// ---------------------------------------------------------------- shafts and the gate glow

	/** Shaft and gate-glow quads this frame. */
	int countShafts(StageFrame frame, SurgaDraw view, int quality, boolean pack, double shafts, double gateLight) {
		int n = 0;
		if (gateLight > 0.02D) {
			n += view.sees(0.0D, SurgaLayout.GATE_Y, 8.0D, 70.0D) ? (quality >= 2 && !pack ? 2 : 1) : 0;
		}
		if (shafts > 0.02D) {
			int count = SHAFTS[Math.max(0, Math.min(2, quality))];
			for (int i = 0; i < count; i++) {
				n += shaftVisible(view, i, count) ? SHAFT_PIECES : 0;
			}
		}
		return n;
	}

	void drawShafts(StageFrame frame, SurgaDraw view, int quality, boolean pack, double shafts, double gateLight, double time, long seed) {
		if (gateLight > 0.02D && view.sees(0.0D, SurgaLayout.GATE_Y, 8.0D, 70.0D)) {
			EpicRenderer.Sink glow = frame.add(SurgaLayout.GATE_GLOW);
			float breathe = (float) (0.9D + 0.1D * Math.cos(time * 0.07D));
			frame.billboard(glow, 0.0D, SurgaLayout.GATE_Y, 8.0D, 34.0D, 0.0F, StageFrame.argb((float) (0.95D * gateLight * breathe), 0xFFF2C8));
			// Maximal: a wide second glow (one large additive layer, dropped by the pack profile)
			if (quality >= 2 && !pack) {
				frame.billboard(glow, 0.0D, SurgaLayout.GATE_Y + 10.0D, 40.0D, 80.0D, 0.0F, StageFrame.argb((float) (0.35D * gateLight * breathe), 0xFFD58A));
			}
		}
		if (shafts <= 0.02D) {
			return;
		}
		EpicRenderer.Sink sink = frame.add(SurgaLayout.SHAFT);
		int count = SHAFTS[Math.max(0, Math.min(2, quality))];
		for (int i = 0; i < count; i++) {
			if (!shaftVisible(view, i, count)) {
				continue;
			}
			double phase = i * 1.7D + (seed & 0xFF) * 0.01D;
			float a = (float) (shafts * (0.42D + 0.18D * Math.cos(time * 0.045D + phase)));
			shaftPoints(i, count);
			view.beam(frame, sink, sx, sy, sz, ex, ey, ez, SHAFT_PIECES, sw0, sw1, StageFrame.argb(a, i % 2 == 0 ? 0xFFE8A0 : 0xFFF4D8),
					StageFrame.argb(0.0F, 0xFFD070), 0.0F, 1.0F);
		}
	}

	private double sx;
	private double sy;
	private double sz;
	private double ex;
	private double ey;
	private double ez;
	private double sw0;
	private double sw1;

	/** Shaft i of n: the first half pour out of the gate's opening (south and up), the rest rise from the hall's roof. */
	private void shaftPoints(int i, int n) {
		int gate = (n + 1) / 2;
		if (i < gate) {
			double t = gate == 1 ? 0.5D : i / (double) (gate - 1);
			double x = -9.0D + 18.0D * t;
			sx = x;
			sy = 10.0D + 40.0D * ((i * 37 % 11) / 10.0D);
			sz = 4.0D;
			double dx = x * 0.02D + (t - 0.5D) * 0.5D;
			double dy = 0.18D + 0.22D * ((i * 53 % 7) / 6.0D);
			double l = 190.0D;
			double norm = Math.sqrt(dx * dx + dy * dy + 1.0D);
			ex = sx + dx / norm * l;
			ey = sy + dy / norm * l;
			ez = sz - 1.0D / norm * l;
			sw0 = 7.0D;
			sw1 = 26.0D;
		} else {
			int j = i - gate;
			int m = n - gate;
			double t = m == 1 ? 0.5D : j / (double) (m - 1);
			sx = -30.0D + 60.0D * t;
			sy = 60.0D;
			sz = SurgaLayout.HALL_Z + 30.0D + 30.0D * ((j * 29 % 5) / 4.0D);
			ex = sx * 1.6D;
			ey = sy + 300.0D;
			ez = sz + (t - 0.5D) * 60.0D;
			sw0 = 16.0D;
			sw1 = 44.0D;
		}
	}

	private boolean shaftVisible(SurgaDraw view, int i, int n) {
		shaftPoints(i, n);
		double mx = (sx + ex) * 0.5D;
		double my = (sy + ey) * 0.5D;
		double mz = (sz + ez) * 0.5D;
		double dx = ex - sx;
		double dy = ey - sy;
		double dz = ez - sz;
		return view.sees(mx, my, mz, Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.5D + sw1);
	}

	// ---------------------------------------------------------------- waterfalls of light

	int countFalls(SurgaDraw view, int quality) {
		int n = 0;
		int count = FALLS[Math.max(0, Math.min(2, quality))];
		for (int i = 0; i < count; i++) {
			double[] f = SurgaLayout.FALLS[i];
			n += view.sees(f[0], (f[1] + SurgaLayout.SEA_Y) * 0.5D, f[2], (f[1] - SurgaLayout.SEA_Y) * 0.5D + 20.0D) ? FALL_PIECES : 0;
		}
		return n;
	}

	void drawFalls(StageFrame frame, SurgaDraw view, int quality, double time) {
		int count = FALLS[Math.max(0, Math.min(2, quality))];
		if (count == 0) {
			return;
		}
		EpicRenderer.Sink sink = frame.add(SurgaLayout.WATERFALL);
		for (int i = 0; i < count; i++) {
			double[] f = SurgaLayout.FALLS[i];
			double bottom = SurgaLayout.SEA_Y + 20.0D;
			if (!view.sees(f[0], (f[1] + bottom) * 0.5D, f[2], (f[1] - bottom) * 0.5D + 20.0D)) {
				continue;
			}
			float scroll = (float) (-time * 0.035D * (1.0D + 0.2D * i));
			float repeats = (float) ((f[1] - bottom) / 90.0D);
			view.beam(frame, sink, f[0], f[1], f[2], f[0] * 1.02D, bottom, f[2], FALL_PIECES, 10.0D, 22.0D, StageFrame.argb(0.75F, 0xFFFFFF),
					StageFrame.argb(0.0F, 0xFFFFFF), scroll, scroll + repeats);
		}
	}
}
