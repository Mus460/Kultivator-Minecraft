package dev.kultivator.client.epic.kosmik.surga;

/**
 * The choreography of the Heaven stage's beats (DESIGN 32.3.3): every value is a pure function of the beat, the shot time,
 * the shot length and the tier's prop scale {@code fs}, so a re-timed, held or replayed shot looks the same. Sizes only
 * change along authored paths (the pillar forming out of the light, a ring lighting as a prop passes); glows breathe by
 * alpha. One instance is written per frame and one per anchor query (no allocation).
 */
final class SurgaChoreo {

	static final String TIANG_GERBANG = "tiang_gerbang";
	static final String TIANG_LEPAS = "tiang_lepas";
	static final String TIANG_JATUH = "tiang_jatuh";
	static final String GUNUNG_SEGEL = "gunung_segel";
	static final String TIANG_TENANG = "tiang_tenang";
	static final String GUNUNG_TENANG = "gunung_tenang";
	static final String PREVIEW_A = "preview_a";
	static final String PREVIEW_B = "preview_b";
	static final String PREVIEW_C = "preview_c";

	/** The javelin's flight (tiang_gerbang): a quadratic Bezier from below and behind the rising camera to the bell. */
	private static final double J0X = -4.0D;
	private static final double J0Y = -64.0D;
	private static final double J0Z = -190.0D;
	private static final double JCX = -3.0D;
	private static final double JCY = -12.0D;
	private static final double JCZ = -128.0D;

	/** The seal's height (model units of {@code yishan_seal}: 1.14 tall, base at its anchor). */
	static final double SEAL_HEIGHT = 1.14D;

	// ---------------------------------------------------------------- outputs

	/** Door angle (degrees, 0 shut, 100 open). */
	double doors;
	/** The light behind the opening doors (0..1). */
	double gateLight;
	/** Gate and hall light shafts (0..1). */
	double shafts;
	/** Falling petals (0..1). */
	double petals;

	boolean javelin;
	double jx;
	double jy;
	double jz;
	/** The javelin's flight direction (unit). */
	double jtx;
	double jty;
	double jtz;
	/** The javelin's path parameter (0..1), for its trail. */
	double javS;

	/** The bell's struck glow (0..1). */
	double bell;
	boolean orb;
	double ox;
	double oy;
	double oz;
	double orbHalf;

	boolean pillar;
	double px;
	double py;
	double pz;
	/** 0 = lying along −z (tip leading south), π/2 = upright, tip down. */
	double theta;
	/** Spin about its own axis (radians). */
	double spin;
	/** Formation scale 0..1 (the pillar condensing out of the light, authored). */
	double grow;
	/** Hoops alight (0..1) and their heat (0 gold, 1 ember). */
	double hoops;
	double heat;
	/** Ember trail strength (0..1) and the white-hot tip (0..1). */
	double trail;
	double tip;
	/** The flash where the pillar forms (0..1). */
	double flash;

	boolean seal;
	double sx;
	double sy;
	double sz;
	double sealYaw;
	/** The gold beam from the gate onto the seal (0..1). */
	double beam;

	/** Extra ring alpha (≤ 0.2 rise over 6 ticks as a prop passes, then a lit residual of 0.06). */
	final double[] ringBoost = new double[9];

	// ---------------------------------------------------------------- the pillar's geometry

	/** The heavenly pillar's length: 60 × fs stage metres (32.3.3). */
	static double pillarLength(double fs) {
		return 60.0D * fs;
	}

	/** The pillar's spawn centre z behind the gate (its leading end at z = 20). */
	static double pillarStartZ(double fs) {
		return 20.0D + pillarLength(fs) * 0.5D;
	}

	/** The pillar's fall column z: in front of the stairs, beyond them for a long (high-tier) pillar. */
	static double pillarFallZ(double fs) {
		return Math.min(SurgaLayout.COLUMN_Z, -12.0D - pillarLength(fs) * 0.5D);
	}

	/** The falling pillar's centre height at shot fraction f (tiang_jatuh, preview_c): −20 to the sea, accelerating. */
	static double fallY(double f, double fs) {
		double end = SurgaLayout.SEA_Y + 0.25D * pillarLength(fs);
		double e = 0.25D * f + 0.75D * f * f;
		return -20.0D + (end + 20.0D) * e;
	}

	/** The rising seal's base height at fraction f (gunung_segel, gunung_tenang). */
	static double sealY(double f) {
		return -470.0D + 430.0D * easeInOut(clamp01(f / 0.85D));
	}

	// ---------------------------------------------------------------- compute

	/** Every output at shot time {@code t} (ticks) of a {@code ticks}-long shot of {@code beat}. */
	void compute(String beat, double t, int ticks, double fs, double[] cross) {
		double f = clamp01(t / Math.max(1, ticks));
		reset(fs);
		switch (beat) {
			case TIANG_GERBANG -> gerbang(f, fs);
			case TIANG_LEPAS -> lepas(f, fs);
			case TIANG_JATUH -> jatuh(f, t, fs);
			case GUNUNG_SEGEL -> segel(f, t, fs, false);
			case TIANG_TENANG -> tenang(f, t, fs);
			case GUNUNG_TENANG -> segel(f, t, fs, true);
			case PREVIEW_A -> {
				doors = 100.0D * easeInOut(clamp01((f - 0.1D) / 0.5D));
				gateLight = doors / 100.0D;
				shafts = smooth(0.15D, 0.6D, f);
				petals = smooth(0.0D, 0.3D, f);
			}
			case PREVIEW_B -> {
				doors = 100.0D;
				gateLight = 1.0D;
				shafts = 1.0D;
				petals = 1.0D;
				// the heavenly pillar resting in the light above the court, turning slowly
				pillar = true;
				px = 0.0D;
				py = 45.0D;
				pz = 220.0D;
				theta = 0.0D;
				spin = t * Math.toRadians(1.5D);
				grow = 1.0D;
				hoops = 0.75D;
				heat = 0.0D;
			}
			case PREVIEW_C -> jatuh(f, t, fs);
			default -> {
			}
		}
		for (int k = 0; k < 9; k++) {
			ringBoost[k] = boost(cross[k], t);
		}
	}

	private void reset(double fs) {
		doors = 0.0D;
		gateLight = 0.0D;
		shafts = 0.0D;
		petals = 0.0D;
		javelin = false;
		bell = 0.0D;
		orb = false;
		pillar = false;
		px = 0.0D;
		py = SurgaLayout.GATE_Y;
		pz = pillarStartZ(fs);
		theta = 0.0D;
		spin = 0.0D;
		grow = 1.0D;
		hoops = 0.0D;
		heat = 0.0D;
		trail = 0.0D;
		tip = 0.0D;
		flash = 0.0D;
		seal = false;
		sx = 0.0D;
		sy = -46.0D;
		sz = SurgaLayout.COLUMN_Z;
		sealYaw = 0.0D;
		beam = 0.0D;
		jx = SurgaLayout.BELL_X;
		jy = SurgaLayout.BELL_Y;
		jz = SurgaLayout.BELL_Z;
		jtx = 0.0D;
		jty = 1.0D;
		jtz = 0.0D;
		javS = 1.0D;
	}

	/** tiang_gerbang: the javelin rises out of the clouds past the camera and strikes the bell; the gate opens. */
	private void gerbang(double f, double fs) {
		double x = clamp01((f - 0.05D) / 0.30D);
		double s = 1.0D - Math.pow(1.0D - x, 1.6D);
		javS = s;
		javelin = f >= 0.05D && f < 0.35D;
		javelinAt(s, this);
		bell = smooth(0.35D, 0.39D, f) * (1.0D - 0.5D * smooth(0.4D, 0.7D, f));
		if (f >= 0.35D) {
			orb = true;
			ox = SurgaLayout.BELL_X;
			oy = SurgaLayout.BELL_Y;
			oz = SurgaLayout.BELL_Z - 5.0D;
			orbHalf = 4.0D + 6.0D * bell;
		}
		doors = 100.0D * easeOut3(clamp01((f - 0.40D) / 0.45D));
		gateLight = doors / 100.0D;
		shafts = smooth(0.42D, 0.8D, f);
		petals = smooth(0.45D, 0.7D, f);
	}

	/** tiang_lepas: the javelin's glow merges into the heavenly pillar, which glides out through the gate and is released. */
	private void lepas(double f, double fs) {
		doors = 100.0D;
		gateLight = 1.0D;
		shafts = 1.0D;
		petals = 1.0D;
		double length = pillarLength(fs);
		double za = pillarStartZ(fs);
		double zb = pillarFallZ(fs);
		// the orb flies from the bell to where the pillar forms
		if (f < 0.12D) {
			double e = easeInOut(clamp01(f / 0.1D));
			orb = true;
			ox = SurgaLayout.BELL_X + (0.0D - SurgaLayout.BELL_X) * e;
			oy = SurgaLayout.BELL_Y + (SurgaLayout.GATE_Y - SurgaLayout.BELL_Y) * e;
			oz = (SurgaLayout.BELL_Z - 5.0D) + (za - length * 0.5D + 4.0D - (SurgaLayout.BELL_Z - 5.0D)) * e;
			orbHalf = 7.0D - 2.0D * e;
		}
		flash = smooth(0.1D, 0.13D, f) * (1.0D - smooth(0.16D, 0.3D, f));
		if (f >= 0.1D) {
			pillar = true;
			grow = 0.25D + 0.75D * easeOut3(clamp01((f - 0.1D) / 0.1D));
			px = 0.0D;
			py = SurgaLayout.GATE_Y;
			pz = za + (zb - za) * easeInOut(clamp01((f - 0.1D) / 0.45D));
			theta = Math.PI * 0.5D * easeInOut(clamp01((f - 0.55D) / 0.2D));
			hoops = smooth(0.55D, 0.75D, f);
			heat = smooth(0.6D, 0.75D, f);
			if (f > 0.75D) {
				double d = (f - 0.75D) / 0.25D;
				py = SurgaLayout.GATE_Y - 50.0D * d * d;
				trail = 0.6D * smooth(0.8D, 1.0D, f);
			}
		}
	}

	/** tiang_jatuh (and preview_c): the pillar falls through the nine rings to the sea of clouds. */
	private void jatuh(double f, double t, double fs) {
		doors = 100.0D;
		gateLight = 1.0D;
		shafts = 1.0D;
		pillar = true;
		px = 0.0D;
		pz = pillarFallZ(fs);
		py = fallY(f, fs);
		theta = Math.PI * 0.5D;
		spin = t * Math.toRadians(3.0D);
		hoops = 1.0D;
		heat = 1.0D;
		trail = 0.6D + 0.4D * smooth(0.0D, 0.3D, f);
		tip = smooth(0.3D, 1.0D, f);
	}

	/** gunung_segel / gunung_tenang: the jade seal rises from the sea through the rings into the gate's light. */
	private void segel(double f, double t, double fs, boolean calm) {
		seal = true;
		sx = 0.0D;
		sz = SurgaLayout.COLUMN_Z;
		sy = sealY(f);
		sealYaw = Math.PI * 0.5D * f;
		doors = 40.0D * (calm ? smooth(0.1D, 0.5D, f) : smooth(0.2D, 0.6D, f));
		gateLight = 0.5D * doors / 40.0D;
		shafts = gateLight;
		petals = gateLight;
		beam = calm ? smooth(0.7D, 0.8D, f) : smooth(0.8D, 0.9D, f);
	}

	/** tiang_tenang (Kamera Penuh): the doors open, the pillar glides out and is released at 0.9. */
	private void tenang(double f, double t, double fs) {
		doors = 100.0D * easeInOut(clamp01((f - 0.2D) / 0.5D));
		gateLight = doors / 100.0D;
		shafts = smooth(0.25D, 0.6D, f);
		petals = smooth(0.3D, 0.6D, f);
		double za = pillarStartZ(fs);
		double zb = pillarFallZ(fs);
		flash = smooth(0.3D, 0.33D, f) * (1.0D - smooth(0.36D, 0.5D, f));
		if (f >= 0.3D) {
			pillar = true;
			grow = 0.25D + 0.75D * easeOut3(clamp01((f - 0.3D) / 0.08D));
			px = 0.0D;
			py = SurgaLayout.GATE_Y;
			pz = za + (zb - za) * easeInOut(clamp01((f - 0.38D) / 0.37D));
			theta = Math.PI * 0.5D * easeInOut(clamp01((f - 0.75D) / 0.15D));
			hoops = smooth(0.75D, 0.9D, f);
			heat = smooth(0.8D, 0.9D, f);
			if (f > 0.9D) {
				double d = (f - 0.9D) / 0.1D;
				py = SurgaLayout.GATE_Y - 15.0D * d * d;
				trail = 0.4D * d;
			}
		}
	}

	// ---------------------------------------------------------------- the javelin's path

	/** The javelin's centre and direction at path parameter s into {@code out} (jx.., jtx..). */
	static void javelinAt(double s, SurgaChoreo out) {
		double u = 1.0D - s;
		double bx = SurgaLayout.BELL_X;
		double by = SurgaLayout.BELL_Y;
		double bz = SurgaLayout.BELL_Z - 5.0D;
		out.jx = u * u * J0X + 2.0D * s * u * JCX + s * s * bx;
		out.jy = u * u * J0Y + 2.0D * s * u * JCY + s * s * by;
		out.jz = u * u * J0Z + 2.0D * s * u * JCZ + s * s * bz;
		double tx = 2.0D * u * (JCX - J0X) + 2.0D * s * (bx - JCX);
		double ty = 2.0D * u * (JCY - J0Y) + 2.0D * s * (by - JCY);
		double tz = 2.0D * u * (JCZ - J0Z) + 2.0D * s * (bz - JCZ);
		double l = Math.sqrt(tx * tx + ty * ty + tz * tz);
		out.jtx = tx / l;
		out.jty = ty / l;
		out.jtz = tz / l;
	}

	/** A point of the javelin's path (for its trail). */
	static void javelinPoint(double s, double[] out, int index) {
		double u = 1.0D - s;
		out[index] = u * u * J0X + 2.0D * s * u * JCX + s * s * SurgaLayout.BELL_X;
		out[index + 1] = u * u * J0Y + 2.0D * s * u * JCY + s * s * SurgaLayout.BELL_Y;
		out[index + 2] = u * u * J0Z + 2.0D * s * u * JCZ + s * s * (SurgaLayout.BELL_Z - 5.0D);
	}

	// ---------------------------------------------------------------- rings lit by a passing prop

	/**
	 * The shot tick at which the leading point of the beat's moving prop crosses ring k's plane (k 0..8 = ring 1..9):
	 * negative infinity when it is already past at the start, positive infinity when it never crosses. Computed once per
	 * shot start ({@code beat}) by bisection on the monotonic path.
	 */
	static void crossings(String beat, int ticks, double fs, double[] out) {
		for (int k = 0; k < 9; k++) {
			out[k] = Double.POSITIVE_INFINITY;
		}
		int dir;
		switch (beat) {
			case TIANG_JATUH, PREVIEW_C -> dir = -1;
			case GUNUNG_SEGEL, GUNUNG_TENANG -> dir = 1;
			default -> {
				return;
			}
		}
		for (int k = 0; k < 9; k++) {
			double ring = SurgaLayout.ringY(k + 1);
			double y0 = leadY(beat, 0.0D, fs);
			double y1 = leadY(beat, 1.0D, fs);
			boolean past0 = dir < 0 ? y0 <= ring : y0 >= ring;
			boolean past1 = dir < 0 ? y1 <= ring : y1 >= ring;
			if (past0) {
				out[k] = Double.NEGATIVE_INFINITY;
				continue;
			}
			if (!past1) {
				continue;
			}
			double lo = 0.0D;
			double hi = 1.0D;
			for (int i = 0; i < 24; i++) {
				double mid = (lo + hi) * 0.5D;
				double y = leadY(beat, mid, fs);
				boolean past = dir < 0 ? y <= ring : y >= ring;
				if (past) {
					hi = mid;
				} else {
					lo = mid;
				}
			}
			out[k] = hi * Math.max(1, ticks);
		}
	}

	/** The leading point's height of the beat's vertical prop at fraction f (the pillar's lower tip, the seal's top). */
	private static double leadY(String beat, double f, double fs) {
		return switch (beat) {
			case TIANG_JATUH, PREVIEW_C -> fallY(f, fs) - pillarLength(fs) * 0.5D;
			case GUNUNG_SEGEL, GUNUNG_TENANG -> sealY(f) + SEAL_HEIGHT * 10.0D * fs;
			default -> Double.NaN;
		};
	}

	/** A ring's extra alpha at tick t after its crossing tick: rises 0.2 over 6 ticks, settles at 0.06. */
	private static double boost(double crossTick, double t) {
		if (crossTick == Double.POSITIVE_INFINITY) {
			return 0.0D;
		}
		if (crossTick == Double.NEGATIVE_INFINITY) {
			return 0.06D;
		}
		return 0.2D * smooth(crossTick, crossTick + 6.0D, t) - 0.14D * smooth(crossTick + 6.0D, crossTick + 20.0D, t);
	}

	// ---------------------------------------------------------------- easing

	static double clamp01(double v) {
		return v < 0.0D ? 0.0D : v > 1.0D ? 1.0D : v;
	}

	static double smooth(double e0, double e1, double x) {
		double t = clamp01((x - e0) / (e1 - e0));
		return t * t * (3.0D - 2.0D * t);
	}

	static double easeOut3(double x) {
		double u = 1.0D - clamp01(x);
		return 1.0D - u * u * u;
	}

	static double easeInOut(double x) {
		double t = clamp01(x);
		return t < 0.5D ? 4.0D * t * t * t : 1.0D - Math.pow(-2.0D * t + 2.0D, 3.0D) * 0.5D;
	}
}
