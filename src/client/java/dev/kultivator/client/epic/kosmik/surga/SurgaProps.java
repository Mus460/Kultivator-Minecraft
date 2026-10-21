package dev.kultivator.client.epic.kosmik.surga;

import dev.kultivator.client.epic.kosmik.StageFrame;
import dev.kultivator.client.epic.render.EpicRenderer;
import org.joml.Quaternionf;

/**
 * The decree props in the Heaven stage (DESIGN 32.3.3, 32.5.7): the thrown javelin and the heavenly pillar
 * ({@code dinghai_pillar}, 6 × fs and 60 × fs stage metres) and the jade seal ({@code yishan_seal}, 10 × fs), drawn as
 * their existing item models through {@link StageFrame#item} (uniform cluster scale), with their light: the ember trails,
 * the pillar's hoops (drawn with the pillar's own cluster scale so they wrap it at the right depth), its white-hot tip,
 * the flash where it forms out of the light, the struck bell's glow, and the gold beam from the gate onto the seal.
 */
final class SurgaProps {

	static final String PILLAR_MODEL = "dinghai_pillar";
	static final String SEAL_MODEL = "yishan_seal";
	/** {@code dinghai_pillar} is 3 model units long, its anchor at its middle. */
	private static final double PILLAR_UNITS = 3.0D;
	private static final int TRAIL_POINTS = 12;
	private static final int PILLAR_TRAIL_POINTS = 7;
	private static final int HOOP_SEGMENTS = 12;
	private static final int BEAM_PIECES = 3;
	private static final int GOLD_WHITE = 0xFFF0B0;
	private static final int EMBER = 0xFF7A1A;

	private final Quaternionf rot = new Quaternionf();
	private final double[] trail = new double[TRAIL_POINTS * 3];

	/** Quads of the props' light this frame (the item models themselves are capped by count, not quads). */
	int count(SurgaChoreo c, SurgaDraw view, double fs) {
		int n = 0;
		if (c.javelin) {
			n += TRAIL_POINTS - 1;
		}
		if (c.orb) {
			n++;
		}
		if (c.bell > 0.02D) {
			n++;
		}
		if (c.pillar) {
			n += c.hoops > 0.02D ? 2 * HOOP_SEGMENTS : 0;
			n += c.trail > 0.02D ? PILLAR_TRAIL_POINTS - 1 : 0;
			n += c.tip > 0.02D ? 1 : 0;
		}
		if (c.flash > 0.02D) {
			n++;
		}
		if (c.seal) {
			n++;
			n += c.beam > 0.02D ? BEAM_PIECES : 0;
		}
		return n;
	}

	/** The item models (cutout phase, submitted at once). */
	void drawItems(StageFrame frame, SurgaChoreo c, double fs) {
		if (c.javelin) {
			rot.rotationTo(0.0F, 1.0F, 0.0F, (float) c.jtx, (float) c.jty, (float) c.jtz);
			frame.item(PILLAR_MODEL, c.jx, c.jy, c.jz, rot, 6.0D * fs / PILLAR_UNITS);
		}
		if (c.pillar) {
			pillarRotation(c);
			frame.item(PILLAR_MODEL, c.px, c.py, c.pz, rot, SurgaChoreo.pillarLength(fs) * c.grow / PILLAR_UNITS);
		}
		if (c.seal) {
			rot.rotationY((float) c.sealYaw);
			frame.item(SEAL_MODEL, c.sx, c.sy, c.sz, rot, 10.0D * fs);
		}
	}

	/** The model's +Y axis turned from −z (θ = 0, lying, its end leading south) to −y (θ = π/2, upright, falling). */
	private void pillarRotation(SurgaChoreo c) {
		rot.rotationX((float) (-Math.PI * 0.5D - c.theta)).rotateY((float) c.spin);
	}

	/** The props' additive light: glows into the gate-glow sink, the beam into the shaft sink, hoops, trails last. */
	void drawLight(StageFrame frame, SurgaDraw view, SurgaChoreo c, double fs, double time) {
		EpicRenderer.Sink glow = frame.add(SurgaLayout.GATE_GLOW);
		if (c.orb) {
			frame.billboard(glow, c.ox, c.oy, c.oz, c.orbHalf, 0.0F, StageFrame.argb(0.95F, 0xFFE8A0));
		}
		if (c.bell > 0.02D) {
			frame.billboard(glow, SurgaLayout.BELL_X, SurgaLayout.BELL_Y, SurgaLayout.BELL_Z - 5.5D, 6.0D + 8.0D * c.bell, 0.0F,
					StageFrame.argb((float) c.bell, 0xFFD54A));
		}
		double length = SurgaChoreo.pillarLength(fs) * c.grow;
		// the axis of the pillar's model (+Y turned): (0, −sin θ, −cos θ)
		double ay = -Math.sin(c.theta);
		double az = -Math.cos(c.theta);
		if (c.flash > 0.02D) {
			double z = SurgaChoreo.pillarStartZ(fs) - SurgaChoreo.pillarLength(fs) * 0.5D;
			frame.billboard(glow, 0.0D, SurgaLayout.GATE_Y, z, 14.0D + 22.0D * c.flash * fs, 0.0F, StageFrame.argb((float) c.flash, 0xFFF6D8));
		}
		if (c.pillar && c.tip > 0.02D) {
			double half = length * 0.5D * 1.02D;
			frame.billboard(glow, c.px, c.py + ay * half, c.pz + az * half, length * 0.09D, 0.0F, StageFrame.argb((float) c.tip, 0xFFE0B0));
		}
		if (c.seal) {
			double centre = c.sy + SurgaChoreo.SEAL_HEIGHT * 10.0D * fs * 0.5D;
			frame.billboard(glow, c.sx, centre, c.sz, 9.0D * fs * (1.0D + 0.4D * c.beam), 0.0F,
					StageFrame.argb((float) (0.35D + 0.5D * c.beam), 0x9FF0C8));
			if (c.beam > 0.02D) {
				EpicRenderer.Sink shaft = frame.add(SurgaLayout.SHAFT);
				double top = c.sy + SurgaChoreo.SEAL_HEIGHT * 10.0D * fs;
				float a = (float) c.beam;
				view.beam(frame, shaft, 0.0D, SurgaLayout.GATE_Y - 6.0D, -3.0D, c.sx, top, c.sz, BEAM_PIECES, 10.0D, 7.0D * fs + 6.0D,
						StageFrame.argb(a, 0xFFF0B0), StageFrame.argb(a * 0.8F, 0xFFD54A), 0.0F, 0.85F);
			}
		}
		if (c.pillar && c.hoops > 0.02D) {
			EpicRenderer.Sink rings = frame.add(SurgaLayout.RINGS);
			int argb = StageFrame.lerpColor(StageFrame.argb((float) c.hoops, GOLD_WHITE), StageFrame.argb((float) c.hoops, EMBER), (float) c.heat);
			view.cluster(frame, c.px, c.py, c.pz);
			for (int end = -1; end <= 1; end += 2) {
				double d = end * length * 0.42D;
				hoop(frame, view, rings, c.px, c.py + ay * d, c.pz + az * d, ay, az, length * 0.075D, length * 0.035D, argb,
						time * 0.08D * end);
			}
		}
		EpicRenderer.Sink ember = frame.add(SurgaLayout.EMBER);
		if (c.javelin) {
			for (int k = 0; k < TRAIL_POINTS; k++) {
				SurgaChoreo.javelinPoint(Math.max(0.0D, c.javS - k * 0.035D), trail, k * 3);
			}
			frame.ribbon(ember, trail, TRAIL_POINTS, 1.6D * fs, 0.2D, StageFrame.argb(1.0F, 0xFFFFFF), StageFrame.argb(0.0F, 0xFFFFFF), 0.0F);
		}
		if (c.pillar && c.trail > 0.02D) {
			// behind the moving end: up the pillar's axis from its trailing end
			double half = length * 0.5D;
			double step = length * 0.45D;
			for (int k = 0; k < PILLAR_TRAIL_POINTS; k++) {
				double d = half + k * step;
				trail[k * 3] = c.px;
				trail[k * 3 + 1] = c.py - ay * d;
				trail[k * 3 + 2] = c.pz - az * d;
			}
			frame.ribbon(ember, trail, PILLAR_TRAIL_POINTS, length * 0.16D, length * 0.04D, StageFrame.argb((float) c.trail, 0xFFFFFF),
					StageFrame.argb(0.0F, 0xFFFFFF), 0.0F);
		}
	}

	/**
	 * A glowing hoop round the pillar's axis (0, ay, az) at H: radius r, width w, in the pillar's cluster scale. The axis
	 * has no x, so (1, 0, 0) and (0, −az... ) span the hoop's plane: e1 = x, e2 = axis × x = (0, az, −ay).
	 */
	private static void hoop(StageFrame frame, SurgaDraw view, EpicRenderer.Sink sink, double hx, double hy, double hz, double ay, double az,
							 double r, double w, int argb, double turn) {
		double rIn = r - w * 0.5D;
		double rOut = r + w * 0.5D;
		float v0 = 64.5F / 72.0F;
		float v1 = 71.5F / 72.0F;
		for (int j = 0; j < HOOP_SEGMENTS; j++) {
			double a0 = turn + Math.PI * 2.0D * j / HOOP_SEGMENTS;
			double a1 = turn + Math.PI * 2.0D * (j + 1) / HOOP_SEGMENTS;
			float u0 = j / (float) HOOP_SEGMENTS;
			float u1 = (j + 1) / (float) HOOP_SEGMENTS;
			double c0 = Math.cos(a0);
			double s0 = Math.sin(a0);
			double c1 = Math.cos(a1);
			double s1 = Math.sin(a1);
			// p = H + e1·cos·ρ + e2·sin·ρ with e1 = (1, 0, 0), e2 = (0, az, −ay)
			view.clusterVertex(frame, sink, hx + c0 * rIn, hy + s0 * az * rIn, hz - s0 * ay * rIn, u0, v0, argb);
			view.clusterVertex(frame, sink, hx + c1 * rIn, hy + s1 * az * rIn, hz - s1 * ay * rIn, u1, v0, argb);
			view.clusterVertex(frame, sink, hx + c1 * rOut, hy + s1 * az * rOut, hz - s1 * ay * rOut, u1, v1, argb);
			view.clusterVertex(frame, sink, hx + c0 * rOut, hy + s0 * az * rOut, hz - s0 * ay * rOut, u0, v1, argb);
		}
	}
}
