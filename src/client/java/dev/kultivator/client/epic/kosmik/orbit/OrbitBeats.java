package dev.kultivator.client.epic.kosmik.orbit;

import dev.kultivator.client.epic.kosmik.KosmikColors;
import dev.kultivator.client.epic.kosmik.StageAnchor;
import dev.kultivator.client.epic.kosmik.StageContext;

/**
 * The layout and the beats of the Orbit stage (DESIGN 32.2, 32.2.3; contract C32.6): every position is a pure function of
 * the shot time, the tier and the cast seed. Units are stage metres (sm); the planet centre is the origin, its axis +Y,
 * radius 1000 sm, turning 0.02°/tick (clouds 0.03°/tick). Home (the point under the caster) sits on the meridian that
 * faces +Z at t = 0, at a latitude from the caster's biome ({@link #homeLatitude}). The home frame is x east,
 * y radial up, z local north (right = forward × up, as {@link StageAnchor}).
 * <p>
 * {@link #at} computes the frame of one shot time into reused fields (no allocation); both the anchors and the renderer
 * read it. Beat timings are authored as fractions of the shot (so a re-timed, shorter shot compresses them); spins and
 * turns run on the stage clock (ticks), which continues across consecutive shots of this stage.
 */
final class OrbitBeats {

	static final double R_P = 1000.0D;
	static final double MOON_X = -6000.0D;
	static final double MOON_Y = 2500.0D;
	static final double MOON_Z = -6500.0D;
	static final double MOON_R = 270.0D;
	static final double SPIN = Math.toRadians(0.02D);
	static final double CLOUD_SPIN = Math.toRadians(0.03D);

	static final int HADIR = 0;
	static final int MENUKIK = 1;
	static final int MASUK = 2;
	static final int BIDUK = 3;
	static final int SINAR = 4;
	static final int SEL = 5;
	static final int PEDANG_TENANG = 6;
	static final int BINTANG_TENANG = 7;
	static final int PREVIEW_A = 8;
	static final int PREVIEW_B = 9;
	static final int PREVIEW_C = 10;
	static final String[] IDS = {"pedang_hadir", "pedang_menukik", "pedang_masuk", "bintang_biduk", "bintang_sinar", "gunung_sel",
			"pedang_tenang", "bintang_tenang", "preview_a", "preview_b", "preview_c"};

	// ---- the sword (32.2.3): heaven_splitting_sword, grip at the model origin, blade along +Y, tip 1.47 units up
	static final String SWORD_MODEL = "heaven_splitting_sword";
	static final double SWORD_LENGTH = 40.0D;
	static final double SWORD_UNITS = 1.47D;
	static final double SWORD_HOVER = 320.0D;
	static final double DIVE = 120.0D;
	static final double ENTRY = 180.0D;
	static final double TRAIL_MAX = 260.0D;
	/** Rune rings: where along the blade (grip 0 → tip 1), radius (× fs), when they ignite in pedang_hadir. */
	static final float[] RING_AT = {0.30F, 0.55F, 0.80F, 0.08F, 1.04F};
	static final float[] RING_R = {6.0F, 8.0F, 10.0F, 4.5F, 12.0F};
	static final float[] RING_IGNITE = {0.15F, 0.35F, 0.55F, 0.66F, 0.76F};

	// ---- the Northern Dipper (32.2.3): Tianshu, Tianxuan, Tianji, Tianquan, Yuheng, Kaiyang, Yaoguang
	static final String CORE_MODEL = "star_core";
	static final double DIPPER_HEIGHT = 500.0D;
	static final double DIPPER_SPACING = 30.0D;
	static final double CORE_SIZE = 6.0D;
	/** The neighbour spacing of the Dipper's ground points on the home patch (× fs^0.5). */
	static final double GROUND_SPACING = 9.0D;
	/**
	 * The Dipper's frame looks down toward the planet: its forward is north tipped 45° down (its up leans north), so the
	 * film's key 160 sm behind it looks down through the seven stars at the night side and its glowing rim; the stars lie
	 * in the frame's right-up plane, face-on to that camera.
	 */
	static final double DIPPER_TILT = Math.toRadians(45.0D);
	/** Sky positions (degrees; x east of the centre RA × cos dec, y dec − 55), centred and scaled at class load. */
	private static final double[] DIPPER_RAW = {
			-11.22D, 6.75D, // Tianshu (Dubhe)
			-11.48D, 1.38D, // Tianxuan (Merak)
			-4.21D, -1.31D, // Tianji (Phecda)
			-1.20D, 2.03D, // Tianquan (Megrez)
			4.20D, 0.96D, // Yuheng (Alioth)
			8.37D, -0.07D, // Kaiyang (Mizar)
			11.68D, -5.69D, // Yaoguang (Alkaid)
	};
	/** The Dipper in units of the mean neighbour spacing (centred). */
	static final double[] DIPPER = new double[14];

	static {
		double mx = 0.0D;
		double my = 0.0D;
		for (int i = 0; i < 7; i++) {
			mx += DIPPER_RAW[i * 2];
			my += DIPPER_RAW[i * 2 + 1];
		}
		mx /= 7.0D;
		my /= 7.0D;
		double spacing = 0.0D;
		for (int i = 0; i < 6; i++) {
			double dx = DIPPER_RAW[i * 2 + 2] - DIPPER_RAW[i * 2];
			double dy = DIPPER_RAW[i * 2 + 3] - DIPPER_RAW[i * 2 + 1];
			spacing += Math.sqrt(dx * dx + dy * dy);
		}
		spacing /= 6.0D;
		for (int i = 0; i < 7; i++) {
			DIPPER[i * 2] = (DIPPER_RAW[i * 2] - mx) / spacing;
			DIPPER[i * 2 + 1] = (DIPPER_RAW[i * 2 + 1] - my) / spacing;
		}
	}

	// ---- the lifted cells of Gunung (32.2.3)
	static final int[] CELL_N = {4, 6, 8};
	static final double CELL_SIZE = 14.0D;
	static final double CELL_RISE = 260.0D;
	static final int MAX_CELLS = 64;

	// ================================================================ latched at a shot start
	/**
	 * The sword frame's tilt (radians): its forward tips down and its up leans north by this much, so the film's sword keys
	 * (60, 40, −110 × fs) look down far enough that the planet's limb stays about 18° below the sword at every tier
	 * (τ = dip − 38°, 0-35°; the planet never scales, the sword hovers higher with the tier).
	 */
	double swordTilt;
	/** The elevation (degrees, home frame) of the planet's limb seen from the pedang_hadir eye, toward the sun's azimuth. */
	double limbElevation = -45.0D;
	int beat = PREVIEW_A;
	int ticks = 20;
	/** Ticks of the previous shots of this stage that the running one continues (the stage clock). */
	double base;
	int quality = 1;
	boolean pack;
	boolean ringan;
	int tier = 1;
	float fs = 1.0F;
	/** fs^0.5 (the Dipper) and fs^0.7 (the cells). */
	double fsh = 1.0D;
	double fsc = 1.0D;
	long seed;
	double homeLat = Math.toRadians(40.0D);
	KosmikColors colors = KosmikColors.DEFAULT;

	// ================================================================ the frame of one shot time (at)
	private double lastTime = Double.NaN;
	private int lastStamp = -1;
	private int stamp;
	double time;
	/** 0..1 of the shot. */
	double u;
	/** The stage clock (ticks). */
	double clock;
	double spin;
	double cloudSpin;
	/** Home point, radial up, local north, east. */
	double hx;
	double hy;
	double hz;
	double upx;
	double upy;
	double upz;
	double nx;
	double ny;
	double nz;
	double ex;
	double ey;
	double ez;
	/** The sun direction (unit) and the brightness of the sun layers and the flare (0..1). */
	double sx;
	double sy;
	double sz;
	float sunGlow;

	// sword
	boolean swordOn;
	double swordLen;
	double itemScale;
	double cx;
	double cy;
	double cz;
	double gx;
	double gy;
	double gz;
	double tx;
	double ty;
	double tz;
	double dx;
	double dy;
	double dz;
	/** The blade's model X and Z axes in the stage (after the spin): the rune rings lie in their plane. */
	double bxx;
	double bxy;
	double bxz;
	double bzx;
	double bzy;
	double bzz;
	int rings;
	final float[] ringAlpha = new float[5];
	final float[] ringAngle = new float[5];
	float ringScale = 1.0F;
	float ringGlow = 1.0F;
	double trailLen;
	double trailW0;
	double trailW1;
	float trailAlpha;
	float heat;
	float headGlow;
	double tipAlt;
	/** The cloud punch progress 0..1 (−1 = none). */
	float punch = -1.0F;

	// dipper (origin and its frame's forward and up)
	boolean dipperOn;
	double dipX;
	double dipY;
	double dipZ;
	double dfx;
	double dfy = 1.0D;
	double dfz;
	double dux;
	double duy = 1.0D;
	double duz;
	final double[] core = new double[21];
	final double[] ground = new double[21];
	final float[] coreLit = new float[7];
	final float[] line = new float[6];
	final float[] beam = new float[7];

	// cells
	boolean cellsOn;
	int cellCount;
	int cellN;
	double cellSize;
	final double[] cellPos = new double[MAX_CELLS * 3];
	final double[] cellHeight = new double[MAX_CELLS];
	final double[] cellAxis = new double[MAX_CELLS * 3];
	final double[] cellAngle = new double[MAX_CELLS];
	final float[] cellRise = new float[MAX_CELLS];
	double cellsX;
	double cellsY;
	double cellsZ;

	/** The beat index of an id, −1 when unknown. */
	static int indexOf(String id) {
		if (id == null) {
			return -1;
		}
		for (int i = 0; i < IDS.length; i++) {
			if (IDS[i].equals(id)) {
				return i;
			}
		}
		return -1;
	}

	/** A shot of {@code id} starts (latches the context; the stage clock continues when {@code continued}). */
	void start(int id, StageContext ctx, int shotTicks, boolean continued) {
		if (continued) {
			base += Math.max(1, ticks);
		} else {
			base = 0.0D;
		}
		beat = id < 0 ? PREVIEW_A : id;
		ticks = Math.max(1, shotTicks);
		if (ctx != null) {
			quality = OrbitTex.q(ctx.quality());
			pack = ctx.pack();
			ringan = ctx.ringan();
			tier = Math.max(1, Math.min(9, ctx.tier()));
			fs = ctx.fs() > 0.0F && Float.isFinite(ctx.fs()) ? ctx.fs() : StageContext.propScale(tier);
			seed = ctx.seed();
			colors = ctx.colors() != null ? ctx.colors() : KosmikColors.DEFAULT;
		}
		fsh = Math.sqrt(fs);
		fsc = Math.pow(fs, 0.7D);
		homeLat = Math.toRadians(homeLatitude(colors));
		cellN = CELL_N[quality];
		double eyeAlt = (SWORD_HOVER + 45.0D) * fs;
		double dip = Math.toDegrees(Math.acos(R_P / (R_P + eyeAlt)));
		swordTilt = Math.toRadians(Math.max(0.0D, Math.min(35.0D, dip - 38.0D)));
		limbElevation = limbElevation(PEDANG_SUN_AZIMUTH);
		stamp++;
		lastTime = Double.NaN;
	}

	/** The azimuth (degrees from north toward east, home frame) of the pedang sunrise: right of the sword in that framing. */
	static final double PEDANG_SUN_AZIMUTH = 5.0D;

	/**
	 * The elevation where a ray from the pedang_hadir eye (the sword key (60, 40, −110) × fs in the tilted sword frame)
	 * toward azimuth {@code az} grazes the planet: below it the planet hides the sun.
	 */
	private double limbElevation(double az) {
		double ct = Math.cos(swordTilt);
		double st = Math.sin(swordTilt);
		// home frame (east, radial up, north) about the planet centre; the sword frame's forward = north·ct − up·st,
		// its up = up·ct + north·st
		double eE = 60.0D * fs;
		double eU = R_P + (SWORD_HOVER + 110.0D * st + 40.0D * ct) * fs;
		double eN = (-110.0D * ct + 40.0D * st) * fs;
		double c = eE * eE + eU * eU + eN * eN - R_P * R_P;
		double ar = Math.toRadians(az);
		double lo = -89.0D;
		double hi = 0.0D;
		for (int i = 0; i < 32; i++) {
			double mid = 0.5D * (lo + hi);
			double er = Math.toRadians(mid);
			double dE = Math.cos(er) * Math.sin(ar);
			double dU = Math.sin(er);
			double dN = Math.cos(er) * Math.cos(ar);
			double b = eE * dE + eU * dU + eN * dN;
			boolean hits = b < 0.0D && b * b - c > 0.0D;
			if (hits) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return 0.5D * (lo + hi);
	}

	/** Computes the frame of shot time {@code t} (ticks). */
	void at(double t) {
		if (t == lastTime && lastStamp == stamp) {
			return;
		}
		lastTime = t;
		lastStamp = stamp;
		time = Math.max(0.0D, Double.isFinite(t) ? t : 0.0D);
		u = Math.max(0.0D, Math.min(1.0D, time / ticks));
		clock = base + time;
		spin = SPIN * clock;
		cloudSpin = CLOUD_SPIN * clock;
		homeFrame();
		sun();
		sword();
		dipper();
		cells();
	}

	private void homeFrame() {
		double cl = Math.cos(homeLat);
		double sl = Math.sin(homeLat);
		double cs = Math.cos(spin);
		double sn = Math.sin(spin);
		upx = cl * sn;
		upy = sl;
		upz = cl * cs;
		nx = -sl * sn;
		ny = cl;
		nz = -sl * cs;
		ex = cs;
		ey = 0.0D;
		ez = -sn;
		hx = upx * R_P;
		hy = upy * R_P;
		hz = upz * R_P;
	}

	// ================================================================ the sun (a direction set per beat)

	private void sun() {
		if (beat == PREVIEW_A) {
			previewSun();
			return;
		}
		double e;
		double a;
		double q = pedangSunrise();
		if (q >= 0.0D) {
			// sunrise over the limb: from just behind the limb (seen from the hadir eye) to 6.5° clear of it, right of the
			// sword in that framing
			e = limbElevation - 2.5D + 9.0D * q;
			a = PEDANG_SUN_AZIMUTH;
			sunGlow = (float) (0.5D + 0.4D * smooth(q));
		} else {
			switch (beat) {
				case MASUK -> {
					// morning at home, the terminator near the far edge of the view
					e = 26.0D + 4.0D * u;
					a = 100.0D;
				}
				case BIDUK, BINTANG_TENANG -> {
					e = -75.0D;
					a = 0.0D;
				}
				case SINAR, PREVIEW_C -> {
					e = -8.0D;
					a = 270.0D;
				}
				default -> {
					// gunung_sel: home in daylight, a low sun behind the cells for a rim light
					e = 22.0D;
					a = 40.0D;
				}
			}
			sunGlow = 0.85F;
		}
		double er = Math.toRadians(e);
		double ar = Math.toRadians(a);
		double ce = Math.cos(er);
		double se = Math.sin(er);
		double sa = Math.sin(ar);
		double ca = Math.cos(ar);
		sx = ce * (sa * ex + ca * nx) + se * upx;
		sy = ce * (sa * ey + ca * ny) + se * upy;
		sz = ce * (sa * ez + ca * nz) + se * upz;
		double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
		sx /= l;
		sy /= l;
		sz /= l;
	}

	/**
	 * preview_a, the planet wide (seen from about planet(−900, 700, 3000), the preview's eye): the sun rises from just
	 * behind the limb to 9° clear of it, up and to one side, so a growing crescent, the lit rim and the flare show.
	 */
	private void previewSun() {
		double cs = Math.cos(spin);
		double sn = Math.sin(spin);
		// the planet frame: forward (sin λ, 0, cos λ), up +Y, right = forward × up = (−cos λ, 0, sin λ)
		double rx = -cs;
		double rz = sn;
		double ox = rx * -900.0D + sn * 3000.0D;
		double oy = 700.0D;
		double oz = rz * -900.0D + cs * 3000.0D;
		double d = Math.sqrt(ox * ox + oy * oy + oz * oz);
		double ax = -ox / d;
		double ay = -oy / d;
		double az = -oz / d;
		// up in that view, leaning right (the moon stands upper left)
		double px = -0.6D * rx;
		double py = 1.0D;
		double pz = -0.6D * rz;
		double dot = px * ax + py * ay + pz * az;
		px -= ax * dot;
		py -= ay * dot;
		pz -= az * dot;
		double pl = Math.sqrt(px * px + py * py + pz * pz);
		px /= pl;
		py /= pl;
		pz /= pl;
		double alpha = Math.asin(Math.min(1.0D, R_P / d)) - Math.toRadians(1.5D) + Math.toRadians(9.0D) * smooth(u);
		double ca = Math.cos(alpha);
		double sa = Math.sin(alpha);
		sx = ax * ca + px * sa;
		sy = ay * ca + py * sa;
		sz = az * ca + pz * sa;
		double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
		sx /= l;
		sy /= l;
		sz /= l;
		sunGlow = (float) (0.5D + 0.45D * smooth(u));
	}

	/** The sunrise progress of the pedang beats (−1 for the other beats). */
	private double pedangSunrise() {
		return switch (beat) {
			case HADIR -> 0.64D * u;
			case MENUKIK -> 0.64D + 0.36D * u;
			case PEDANG_TENANG -> 0.5D * u;
			case PREVIEW_B -> 0.5D + 0.5D * u;
			default -> -1.0D;
		};
	}

	// ================================================================ the sword

	private void sword() {
		swordOn = beat == HADIR || beat == MENUKIK || beat == MASUK || beat == PEDANG_TENANG || beat == PREVIEW_B;
		swordLen = SWORD_LENGTH * fs;
		itemScale = swordLen / SWORD_UNITS;
		rings = 3 + (tier >= 5 ? 1 : 0) + (tier >= 8 ? 1 : 0);
		trailLen = 0.0D;
		trailAlpha = 0.0F;
		heat = 0.0F;
		headGlow = 0.0F;
		punch = -1.0F;
		ringScale = 1.0F;
		ringGlow = 1.0F;
		for (int i = 0; i < 5; i++) {
			ringAlpha[i] = 0.0F;
			ringAngle[i] = (float) ((i % 2 == 0 ? 1.0D : -1.0D) * Math.toRadians(3.0D) * clock + i * 0.7D);
		}
		if (!swordOn) {
			// a resting pose so the sword and beam anchors stay defined in every beat
			poseSword(0.0D, SWORD_HOVER * fs, 0.0D);
			return;
		}
		double spinRate = beat == PEDANG_TENANG ? 0.25D : 0.5D;
		double turn = Math.toRadians(spinRate) * clock;
		double theta = 0.0D;
		double centreAlt = SWORD_HOVER * fs;
		switch (beat) {
			case MENUKIK -> {
				if (u < 0.4D) {
					theta = Math.PI * easeInOut(u / 0.4D);
					float k = (float) smooth(u / 0.4D);
					ringScale = 1.0F - 0.5F * k;
					ringGlow = 1.0F + 0.35F * k;
					fillRings(1.0F);
				} else {
					theta = Math.PI;
					double d = (u - 0.4D) / 0.6D;
					double travelled = DIVE * fs * expoIn(d);
					centreAlt -= travelled;
					ringScale = 0.5F;
					ringGlow = 1.35F;
					fillRings((float) (1.0D - 0.45D * d));
					trailLen = travelled + 0.5D * swordLen;
					trailW0 = 7.0D * fs;
					trailW1 = 1.5D * fs;
					trailAlpha = (float) Math.min(1.0D, d * 4.0D);
					headGlow = (float) (0.5D * smooth(d));
				}
			}
			case MASUK -> {
				theta = Math.PI;
				double entry = ENTRY * fs;
				double tip;
				if (u < 0.85D) {
					double x = u / 0.85D;
					tip = 10.0D + (entry - 10.0D) * (1.0D - Math.pow(x, 1.7D));
				} else {
					tip = 10.0D - 6.0D * ((u - 0.85D) / 0.15D);
				}
				centreAlt = tip + 0.5D * swordLen;
				heat = (float) smoothstep(60.0D, 20.0D, tip);
				trailLen = Math.min(TRAIL_MAX * fs, (entry - tip) + DIVE * fs + swordLen);
				trailW0 = 9.0D * fs * (1.0D + 0.6D * heat);
				trailW1 = 2.0D * fs;
				trailAlpha = 1.0F;
				headGlow = 0.6F + 0.4F * heat;
				if (u >= 0.85D) {
					punch = (float) ((u - 0.85D) / 0.15D);
				}
			}
			default -> {
				// hadir, tenang, preview_b: hanging tip up over home, the rings igniting one by one
				centreAlt += 1.5D * fs * Math.sin(clock * 0.08D);
				for (int i = 0; i < rings; i++) {
					double ignite;
					double over;
					if (beat == PEDANG_TENANG) {
						ignite = 0.2D + 0.12D * i;
						over = 8.0D;
					} else if (beat == PREVIEW_B) {
						ignite = 0.05D + 0.08D * i;
						over = 4.0D;
					} else {
						ignite = RING_IGNITE[i];
						over = 4.0D;
					}
					ringAlpha[i] = (float) clamp((time - ignite * ticks) / over);
				}
			}
		}
		poseSword(theta, centreAlt, turn);
	}

	private void fillRings(float alpha) {
		for (int i = 0; i < rings; i++) {
			ringAlpha[i] = alpha;
		}
	}

	/** The sword pitched {@code theta} about east (0 tip up, π tip down), its centre {@code alt} above home, spun {@code turn}. */
	private void poseSword(double theta, double alt, double turn) {
		double c = Math.cos(theta);
		double s = Math.sin(theta);
		// model Y → the blade, model X → east, model Z → (−north) turned with the pitch (right-handed)
		dx = c * upx + s * nx;
		dy = c * upy + s * ny;
		dz = c * upz + s * nz;
		double zx = -c * nx + s * upx;
		double zy = -c * ny + s * upy;
		double zz = -c * nz + s * upz;
		double ct = Math.cos(turn);
		double st = Math.sin(turn);
		bxx = ex * ct - zx * st;
		bxy = ey * ct - zy * st;
		bxz = ez * ct - zz * st;
		bzx = ex * st + zx * ct;
		bzy = ey * st + zy * ct;
		bzz = ez * st + zz * ct;
		cx = hx + upx * alt;
		cy = hy + upy * alt;
		cz = hz + upz * alt;
		double half = 0.5D * swordLen;
		gx = cx - dx * half;
		gy = cy - dy * half;
		gz = cz - dz * half;
		tx = cx + dx * half;
		ty = cy + dy * half;
		tz = cz + dz * half;
		tipAlt = alt + (theta > Math.PI * 0.5D ? -half : half);
	}

	// ================================================================ the Dipper

	private void dipper() {
		dipperOn = beat == BIDUK || beat == SINAR || beat == BINTANG_TENANG || beat == PREVIEW_C;
		double lift = DIPPER_HEIGHT * fsh;
		dipX = hx + upx * lift;
		dipY = hy + upy * lift;
		dipZ = hz + upz * lift;
		double ct = Math.cos(DIPPER_TILT);
		double st = Math.sin(DIPPER_TILT);
		// the frame: forward = north tipped down, up = radial up leaning north (right stays east)
		dfx = nx * ct - upx * st;
		dfy = ny * ct - upy * st;
		dfz = nz * ct - upz * st;
		double vx = upx * ct + nx * st;
		double vy = upy * ct + ny * st;
		double vz = upz * ct + nz * st;
		dux = vx;
		duy = vy;
		duz = vz;
		double k = DIPPER_SPACING * fsh;
		double kg = GROUND_SPACING * fsh;
		for (int i = 0; i < 7; i++) {
			double px = DIPPER[i * 2];
			double py = DIPPER[i * 2 + 1];
			core[i * 3] = dipX + (ex * px + vx * py) * k;
			core[i * 3 + 1] = dipY + (ey * px + vy * py) * k;
			core[i * 3 + 2] = dipZ + (ez * px + vz * py) * k;
			ground[i * 3] = hx + upx * 0.6D + (ex * px + nx * py) * kg;
			ground[i * 3 + 1] = hy + upy * 0.6D + (ey * px + ny * py) * kg;
			ground[i * 3 + 2] = hz + upz * 0.6D + (ez * px + nz * py) * kg;
			coreLit[i] = 0.0F;
			beam[i] = 0.0F;
		}
		for (int i = 0; i < 6; i++) {
			line[i] = 0.0F;
		}
		if (!dipperOn) {
			return;
		}
		if (beat == BIDUK || beat == BINTANG_TENANG) {
			// the cores light one by one at ticks 4, 8, … 28 of 34 (alpha over 3 ticks); each line draws to the next over 3
			double step = 4.0D / 34.0D;
			double over = 3.0D / 34.0D;
			for (int i = 0; i < 7; i++) {
				double lit = step * (i + 1);
				coreLit[i] = (float) clamp((u - lit) / over);
				if (i < 6) {
					line[i] = (float) clamp((u - lit) / over);
				}
			}
			return;
		}
		for (int i = 0; i < 7; i++) {
			coreLit[i] = 1.0F;
			if (i < 6) {
				line[i] = 1.0F;
			}
			double fire;
			double over;
			if (beat == SINAR) {
				// from 0.1 each core fires its beam every 3 ticks (of 26)
				fire = 0.1D + 3.0D * i / 26.0D;
				over = 3.0D / 26.0D;
			} else {
				fire = 0.25D + 0.06D * i;
				over = 0.08D;
			}
			beam[i] = (float) clamp((u - fire) / over);
		}
	}

	// ================================================================ the lifted cells

	private void cells() {
		cellsOn = beat == SEL;
		cellSize = CELL_SIZE * fsc;
		int n = cellN;
		cellCount = cellsOn ? n * n : 0;
		double pitch = cellSize * 1.18D;
		double sumX = 0.0D;
		double sumY = 0.0D;
		double sumZ = 0.0D;
		for (int a = 0; a < n && cellsOn; a++) {
			for (int b = 0; b < n; b++) {
				int k = a * n + b;
				double h1 = hash(k, 1);
				double h2 = hash(k, 2);
				double h3 = hash(k, 3);
				double delay = 0.3D * h1;
				double prog = clamp((u - delay) / (1.0D - delay));
				double eased = 0.5D - 0.5D * Math.cos(Math.PI * prog);
				double rise = CELL_RISE * fs * (0.78D + 0.22D * h3) * eased;
				double spread = 1.0D + 0.35D * eased;
				double oe = (a - (n - 1) * 0.5D) * pitch * spread;
				double on = (b - (n - 1) * 0.5D) * pitch * spread;
				double alt = 1.0D + rise;
				double px = hx + ex * oe + nx * on + upx * alt;
				double py = hy + ey * oe + ny * on + upy * alt;
				double pz = hz + ez * oe + nz * on + upz * alt;
				double height = cellSize * (0.65D + 0.35D * h2);
				cellPos[k * 3] = px;
				cellPos[k * 3 + 1] = py;
				cellPos[k * 3 + 2] = pz;
				cellHeight[k] = height;
				// a seeded tumble axis and a rate of 0.3-1°/tick, from the moment the cell leaves the ground
				double ax = hash(k, 4) * 2.0D - 1.0D;
				double ay = hash(k, 5) * 2.0D - 1.0D;
				double az = hash(k, 6) * 2.0D - 1.0D;
				double al = Math.sqrt(ax * ax + ay * ay + az * az);
				if (al < 1.0E-3D) {
					ax = 1.0D;
					ay = 0.0D;
					az = 0.0D;
					al = 1.0D;
				}
				cellAxis[k * 3] = ax / al;
				cellAxis[k * 3 + 1] = ay / al;
				cellAxis[k * 3 + 2] = az / al;
				double rate = Math.toRadians(0.3D + 0.7D * hash(k, 7));
				cellAngle[k] = rate * Math.max(0.0D, time - delay * ticks);
				cellRise[k] = (float) prog;
				sumX += px + upx * height * 0.5D;
				sumY += py + upy * height * 0.5D;
				sumZ += pz + upz * height * 0.5D;
			}
		}
		if (cellCount > 0) {
			cellsX = sumX / cellCount;
			cellsY = sumY / cellCount;
			cellsZ = sumZ / cellCount;
		} else {
			double lift = CELL_RISE * fs * 0.5D;
			cellsX = hx + upx * lift;
			cellsY = hy + upy * lift;
			cellsZ = hz + upz * lift;
		}
	}

	// ================================================================ anchors (C32.6)

	/** The anchor {@code name} of the current frame into {@code out}; false = unknown. */
	boolean anchor(String name, StageAnchor out) {
		if (name == null) {
			return false;
		}
		switch (name) {
			case "planet" -> out.set(0.0D, 0.0D, 0.0D, Math.sin(spin), 0.0D, Math.cos(spin), 0.0D, 1.0D, 0.0D, 1.0D);
			case "home" -> out.set(hx, hy, hz, nx, ny, nz, upx, upy, upz, fs);
			case "sword" -> {
				double ct = Math.cos(swordTilt);
				double st = Math.sin(swordTilt);
				out.set(cx, cy, cz, nx * ct - upx * st, ny * ct - upy * st, nz * ct - upz * st, upx * ct + nx * st, upy * ct + ny * st,
						upz * ct + nz * st, fs);
			}
			case "beam" -> out.set(tx, ty, tz, nx, ny, nz, upx, upy, upz, fs);
			case "moon" -> out.set(MOON_X, MOON_Y, MOON_Z, 0.0D, 0.0D, 1.0D, 0.0D, 1.0D, 0.0D, 1.0D);
			case "sun" -> out.set(sx * 1.0E5D, sy * 1.0E5D, sz * 1.0E5D, nx, ny, nz, upx, upy, upz, 1.0D);
			case "dipper" -> out.set(dipX, dipY, dipZ, dfx, dfy, dfz, dux, duy, duz, fsh);
			case "cells" -> out.set(cellsX, cellsY, cellsZ, nx, ny, nz, upx, upy, upz, fsc);
			default -> {
				if (name.length() == 5 && name.startsWith("core")) {
					int i = name.charAt(4) - '1';
					if (i >= 0 && i < 7) {
						out.set(core[i * 3], core[i * 3 + 1], core[i * 3 + 2], dfx, dfy, dfz, dux, duy, duz, fsh);
						return true;
					}
				}
				return false;
			}
		}
		return true;
	}

	/** True when the planet or the moon hides stage point P from the eye E. */
	static boolean occluded(double px, double py, double pz, double ex, double ey, double ez) {
		return segmentHits(px, py, pz, ex, ey, ez, 0.0D, 0.0D, 0.0D, R_P) || segmentHits(px, py, pz, ex, ey, ez, MOON_X, MOON_Y, MOON_Z, MOON_R);
	}

	private static boolean segmentHits(double px, double py, double pz, double ex, double ey, double ez, double cx, double cy, double cz, double r) {
		double dx = px - ex;
		double dy = py - ey;
		double dz = pz - ez;
		double fx = ex - cx;
		double fy = ey - cy;
		double fz = ez - cz;
		double a = dx * dx + dy * dy + dz * dz;
		if (!(a > 1.0E-12D)) {
			return false;
		}
		double c = fx * fx + fy * fy + fz * fz - r * r;
		if (c < 0.0D) {
			return true;
		}
		double b = 2.0D * (fx * dx + fy * dy + fz * dz);
		double disc = b * b - 4.0D * a * c;
		if (disc < 0.0D) {
			return false;
		}
		double root = Math.sqrt(disc);
		double t1 = (-b - root) / (2.0D * a);
		double t2 = (-b + root) / (2.0D * a);
		return (t1 > 0.0D && t1 < 1.0D) || (t2 > 0.0D && t2 < 1.0D);
	}

	// ================================================================ helpers

	/**
	 * The home latitude of 32.2 (degrees north): snowy 60, dry 22, jungle-like 6, otherwise 40. Jungle-like is the warm,
	 * saturated green of the jungle foliage (vanilla 30BB0B, sparse jungle 3EB80F: bright green with little red and almost no
	 * blue); plains (77AB2F), forest (59AE30), birch, taiga and swamp foliage stay temperate.
	 */
	static float homeLatitude(KosmikColors c) {
		return c.homeLatitude();
	}

	/** A deterministic hash of the cast seed, an index and a salt in [0, 1) (SplitMix64). */
	double hash(int index, int salt) {
		long z = seed + 0x9E3779B97F4A7C15L * (index * 31L + salt + 1L);
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z = z ^ (z >>> 31);
		return (z >>> 11) * 0x1.0p-53;
	}

	static double clamp(double v) {
		return v < 0.0D ? 0.0D : v > 1.0D ? 1.0D : v;
	}

	static double smooth(double v) {
		double t = clamp(v);
		return t * t * (3.0D - 2.0D * t);
	}

	static double smoothstep(double e0, double e1, double v) {
		return smooth((v - e0) / (e1 - e0));
	}

	static double easeInOut(double v) {
		double t = clamp(v);
		return t < 0.5D ? 4.0D * t * t * t : 1.0D - Math.pow(-2.0D * t + 2.0D, 3.0D) / 2.0D;
	}

	static double expoIn(double v) {
		double t = clamp(v);
		return t <= 0.0D ? 0.0D : Math.pow(2.0D, 10.0D * (t - 1.0D));
	}
}
