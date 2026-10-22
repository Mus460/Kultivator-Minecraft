package dev.kultivator.sekte.tangga;

/**
 * The path of the illusory Thousand-Step Stair (DESIGN 36.2.4), pure math shared by the server (it moves the climbers)
 * and the client ({@code TanggaIlusiRenderer} draws the ribbon from the same numbers), so the bodies always stand on the
 * drawn treads. No Minecraft state, safe on both sides.
 * <p>
 * The stair starts at the projector {@code O} (the {@code tangga_proyektor} anchor, at standing height) and leaves the
 * sect along {@code F}, the horizontal direction from the stele toward the projector: a short threshold of
 * {@link #LEAD} blocks, then a conical helix around a vertical axis {@code C = O + F * (LEAD + RADIUS_START)} whose
 * radius narrows from {@link #RADIUS_START} to {@link #RADIUS_END} over {@link #TURNS} turns while it rises
 * {@link #HEIGHT} blocks: the helix never comes back over the gate (it lies wholly in front of the projector). Step
 * {@code s} (0-1000) maps linearly to the helix parameter. {@code lateral} moves across the stair's width, outward
 * from the axis when positive; climbers keep a lane each ({@link #lane}).
 * <p>
 * Source of the shape: the "Eighteen Bends" of Mount Tai, a stair of about 1,600 steps climbing to the South Gate of
 * Heaven (DESIGN 36.5).
 */
public final class JalurTangga {

	/** Steps of the stair. */
	public static final int STEPS = 1000;
	/** The straight threshold from the projector to the start of the helix (blocks). */
	public static final double LEAD = 1.5D;
	/** Height the threshold rises. */
	public static final double LEAD_RISE = 0.4D;
	public static final double RADIUS_START = 7.0D;
	public static final double RADIUS_END = 4.5D;
	public static final double TURNS = 2.25D;
	/** Total rise from the projector to the top step (blocks). */
	public static final double HEIGHT = 24.0D;
	/** Width of the treads (blocks); lanes lie inside {@code ±WIDTH/2 - 0.5}. */
	public static final double WIDTH = 4.0D;
	/** Fraction of the steps spent on the threshold. */
	public static final double LEAD_FRACTION = 0.02D;
	/** Most climbers at once (lanes). */
	public static final int LANES = 6;

	/** The frame of one stair: projector {@code (ox, oy, oz)} and the unit forward {@code (fx, fz)}. */
	public record Frame(double ox, double oy, double oz, double fx, double fz) {

		/** A frame from the projector and any horizontal direction (normalised; +z when degenerate). */
		public static Frame of(double ox, double oy, double oz, double dx, double dz) {
			double length = Math.sqrt(dx * dx + dz * dz);
			if (length < 1.0E-6D) {
				return new Frame(ox, oy, oz, 0.0D, 1.0D);
			}
			return new Frame(ox, oy, oz, dx / length, dz / length);
		}

		/** The right-hand horizontal axis (forward turned a quarter clockwise seen from above). */
		public double rx() {
			return -fz;
		}

		public double rz() {
			return fx;
		}

		/** The helix axis. */
		public double cx() {
			return ox + fx * (LEAD + RADIUS_START);
		}

		public double cz() {
			return oz + fz * (LEAD + RADIUS_START);
		}

		/** The yaw of {@code F} in degrees (Minecraft convention: 0 = +z, 90 = -x). */
		public float yaw() {
			return (float) Math.toDegrees(Math.atan2(-fx, fz));
		}
	}

	/** The lateral offset of lane {@code index} (0-5) across the treads. */
	public static double lane(int index) {
		int i = Math.max(0, Math.min(LANES - 1, index));
		return -1.5D + i * 0.6D;
	}

	/** Helix parameter 0-1 of step {@code step} (0 on the threshold). */
	public static double helixU(double step) {
		double s = Math.max(0.0D, Math.min(STEPS, step)) / STEPS;
		return s <= LEAD_FRACTION ? 0.0D : (s - LEAD_FRACTION) / (1.0D - LEAD_FRACTION);
	}

	/** The radius at helix parameter {@code u}. */
	public static double radius(double u) {
		return RADIUS_START + (RADIUS_END - RADIUS_START) * u;
	}

	/** The angle (radians) at helix parameter {@code u}. */
	public static double angle(double u) {
		return u * TURNS * Math.PI * 2.0D;
	}

	/** The standing height above the projector at step {@code step}. */
	public static double rise(double step) {
		double s = Math.max(0.0D, Math.min(STEPS, step)) / STEPS;
		if (s <= LEAD_FRACTION) {
			return LEAD_RISE * s / LEAD_FRACTION;
		}
		return LEAD_RISE + (HEIGHT - LEAD_RISE) * helixU(step);
	}

	/**
	 * The world point (standing height) at {@code step} and {@code lateral}, written into {@code out} (x, y, z). On the
	 * threshold the lanes fan from a row across {@code R} at the projector into their helix lanes.
	 */
	public static double[] point(Frame frame, double step, double lateral, double[] out) {
		double[] o = out != null && out.length >= 3 ? out : new double[3];
		double s = Math.max(0.0D, Math.min(STEPS, step)) / STEPS;
		double y = frame.oy() + rise(step);
		if (s <= LEAD_FRACTION) {
			double t = s / LEAD_FRACTION;
			// from the base row (across R at the projector) to the helix start (across -F, at O + LEAD F)
			double bx = frame.ox() + frame.rx() * lateral;
			double bz = frame.oz() + frame.rz() * lateral;
			double hx = frame.ox() + frame.fx() * (LEAD - lateral);
			double hz = frame.oz() + frame.fz() * (LEAD - lateral);
			double e = t * t * (3.0D - 2.0D * t);
			o[0] = bx + (hx - bx) * e;
			o[1] = y;
			o[2] = bz + (hz - bz) * e;
			return o;
		}
		double u = helixU(step);
		double a = angle(u);
		double r = radius(u) + lateral;
		double nx = -Math.cos(a) * frame.fx() + Math.sin(a) * frame.rx();
		double nz = -Math.cos(a) * frame.fz() + Math.sin(a) * frame.rz();
		o[0] = frame.cx() + nx * r;
		o[1] = y;
		o[2] = frame.cz() + nz * r;
		return o;
	}

	/** The horizontal unit direction a climber faces at {@code step} (the helix tangent; {@code F} on the threshold). */
	public static double[] heading(Frame frame, double step, double[] out) {
		double[] o = out != null && out.length >= 2 ? out : new double[2];
		double s = Math.max(0.0D, Math.min(STEPS, step)) / STEPS;
		if (s <= LEAD_FRACTION) {
			o[0] = frame.fx();
			o[1] = frame.fz();
			return o;
		}
		double a = angle(helixU(step));
		// d/da of the radial unit (-cos a F + sin a R) = sin a F + cos a R
		o[0] = Math.sin(a) * frame.fx() + Math.cos(a) * frame.rx();
		o[1] = Math.sin(a) * frame.fz() + Math.cos(a) * frame.rz();
		return o;
	}

	/** The yaw (degrees, Minecraft convention) of a horizontal direction. */
	public static float yaw(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	/** The top of the stair (the peak platform centre), written into {@code out}. */
	public static double[] peak(Frame frame, double[] out) {
		return point(frame, STEPS, 0.0D, out);
	}

	/** True when {@code (x, z)} lies within {@code range} blocks of the helix axis (horizontal). */
	public static boolean near(Frame frame, double x, double y, double z, double range) {
		double dx = x - frame.cx();
		double dz = z - frame.cz();
		double dy = y - (frame.oy() + HEIGHT * 0.5D);
		return dx * dx + dz * dz + dy * dy * 0.25D <= range * range;
	}

	private JalurTangga() {
	}
}
