package dev.kultivator.duel.blade;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;

/**
 * The keys of every {@link DuelPose} (DESIGN 35.5.2, contract C35.A1) and the one sampler both sides use.
 * <p>
 * <b>Channels</b> are the 18 of the client's {@code PoseLibrary} in the same order (radians, model space: y down, the
 * front is −z; arm {@code xRot < 0} raises the arm forward, right arm {@code yRot > 0} swings it out to the right,
 * {@code zRot > 0} lifts it sideways; {@code TWIST > 0} turns the right shoulder back; {@code LEAN > 0} bends forward;
 * {@code LIFT} in blocks) plus {@link #WRIST}, the roll of the sword hand about the forearm, so the edge of the blade
 * faces where the cut goes. 19 channels in all ({@link #CHANNELS}).
 * <p>
 * <b>Keys are authored by intent.</b> In the vanilla grip the blade leaves the fist at right angles to the forearm, so the
 * angles that put a blade where a cut needs it are not obvious. Each key is therefore written as the body's lean and
 * twist, the direction of the sword arm and the direction of the blade (azimuth: 0 ahead, +90 to the fighter's right;
 * elevation: +90 straight up), and solved at class load into arm angles and wrist (zRot kept at 0, the wrist solution
 * nearest the previous key so the hand rolls the short way). The solved numbers are what the client draws and what the
 * server tests, so they always agree.
 * <p>
 * <b>Timing.</b> A cut has a windup key (reached with easeOutCubic in {@code w} ticks, or sooner if the swing's real
 * windup is shorter), a strike key that is the moment of contact (reached at the impact tick, accelerating like a real
 * cut: the blade is fastest where it meets the other), and a follow-through key ({@code f} ticks, easeOutCubic). Other
 * poses snap to their strike key with easeOutBack (overshoot about 8%, like {@code PoseLibrary}) and settle into the
 * follow key, which they hold. Reactions and parries may lean towards the side the blow came from ({@link Ctx#side}),
 * and the parry has a second, overhead key set for blows from above ({@link Ctx#alt}).
 */
public final class DuelPoseTable {

	public static final int HX = 0, HY = 1, LEAN = 2, TWIST = 3, RAX = 4, RAY = 5, RAZ = 6, LAX = 7, LAY = 8, LAZ = 9, RLX = 10,
			RLZ = 11, LLX = 12, LLZ = 13, LIFT = 14, RLY = 15, LLY = 16, TILT = 17, WRIST = 18;
	/** The 19 duel channels (the 18 of {@code PoseLibrary} plus the wrist). */
	public static final int CHANNELS = 19;
	/** The channels {@code PoseLibrary} knows (everything but the wrist). */
	public static final int BODY_CHANNELS = 18;

	public static final int PH_WINDUP = 0, PH_STRIKE = 1, PH_FOLLOW = 2;
	public static final int PHASES = 3;

	/** Hold modifiers. */
	public static final int MOD_BREATH = 1, MOD_TREMBLE = 2, MOD_CHARGE = 4, MOD_LOCK = 8, MOD_STAGGER = 16;

	/** How a pose looks from one's own eyes ({@code FirstPersonDuel}). */
	public enum FirstPerson { NONE, GUARD, CUT_HIGH, CUT_SIDE, CUT_LOW, THRUST, PARRY, LOCK, CHARGE, RECOIL, DRAW }

	/** Per-frame inputs of the modifiers. The server samples with {@link #NONE} (no breathing, no shaking). */
	public static final class Ctx {
		/** Entity age in ticks for the breathing and shaking (NaN: no modifiers). */
		public float age = Float.NaN;
		/** Blade-lock pressure, −1..1 (positive: this fighter is winning). */
		public float pressure;
		/** Heavy charge progress 0..1. */
		public float charge;
		/** −1 (left) .. 1 (right): the side a parried or landed blow came from, in this fighter's own frame. */
		public float side;
		/** 0..1: blend towards the pose's alternative keys (the overhead parry). */
		public float alt;

		public Ctx set(float age, float pressure, float charge, float side, float alt) {
			this.age = age;
			this.pressure = pressure;
			this.charge = charge;
			this.side = side;
			this.alt = alt;
			return this;
		}
	}

	/** No modifiers (the server's samples). */
	public static final Ctx NONE = new Ctx();

	/** One pose: three keys (plus an optional alternative set), timing and extras. */
	public static final class Def {
		final float[][] keys = new float[PHASES][CHANNELS];
		@Nullable float[][] alt;
		int w = 3;
		int s = 2;
		int f = 3;
		int mods;
		float amp;
		/** The off hand reaches for the hilt (two-handed grip). */
		boolean offAim;
		/** How strongly a side input turns the pose (radians of TWIST and arm yaw per unit of side). */
		float sideTurn;
		/** Ticks after the start when the held item is put away (SARUNG), or -1. */
		int hideItemAt = -1;
		FirstPerson firstPerson = FirstPerson.NONE;

		public int windTicks() {
			return w;
		}

		public int strikeTicks() {
			return s;
		}

		public int followTicks() {
			return f;
		}

		public int mods() {
			return mods;
		}

		public boolean offAim() {
			return offAim;
		}

		public int hideItemAt() {
			return hideItemAt;
		}

		public FirstPerson firstPerson() {
			return firstPerson;
		}
	}

	private static final Map<DuelPose, Def> DEFS = new EnumMap<>(DuelPose.class);

	// ============================================================================================ public API

	/** The definition of a pose (never null: every DuelPose has keys). */
	public static Def def(DuelPose pose) {
		Def def = DEFS.get(pose);
		return def != null ? def : DEFS.get(DuelPose.KUDA_TENGAH);
	}

	/** Copies the key of {@code phase} ({@link #PH_WINDUP}, {@link #PH_STRIKE}, {@link #PH_FOLLOW}) into {@code out}. */
	public static void key(DuelPose pose, int phase, float[] out) {
		float[] key = def(pose).keys[Math.max(0, Math.min(PHASES - 1, phase))];
		System.arraycopy(key, 0, out, 0, Math.min(out.length, CHANNELS));
	}

	public static int windTicks(DuelPose pose) {
		return def(pose).w;
	}

	public static int strikeTicks(DuelPose pose) {
		return def(pose).s;
	}

	public static int followTicks(DuelPose pose) {
		return def(pose).f;
	}

	/** The canonical impact tick of a pose (windup + strike), used when the real windup is not known. */
	public static int impactTick(DuelPose pose) {
		Def def = def(pose);
		return def.w + def.s;
	}

	/** Total ticks until the follow key is reached for a swing with this windup ({@code windup <= 0}: canonical). */
	public static int length(DuelPose pose, int windup) {
		Def def = def(pose);
		return (windup > 0 ? windup : def.w + def.s) + def.f;
	}

	/**
	 * Samples {@code pose} at {@code t} ticks after its start into {@code out} (19 channels, never NaN).
	 *
	 * @param windup ticks from the start to the impact (the swing's real windup) for cuts; {@code <= 0} for the canonical
	 *               {@code w + s}; ignored by other poses
	 * @param from   the channels the body had when the pose began (it eases out of them), or null to start at the
	 *               windup key
	 * @param ctx    the modifier inputs ({@link #NONE} on the server)
	 * @return where in the pose {@code t} is: 0..1 windup, 1..2 strike (2 = the impact of a cut), 2..3 follow, 3 held
	 */
	public static float sample(DuelPose pose, float t, int windup, @Nullable float[] from, @Nullable Ctx ctx, float[] out) {
		Def def = def(pose);
		Ctx c = ctx == null ? NONE : ctx;
		float[][] keys = keys(def, c);
		float position;
		if (pose.cut()) {
			int impact = windup > 0 ? windup : def.w + def.s;
			int s = Math.max(1, Math.min(def.s, impact));
			float strikeStart = impact - s;
			float reach = Math.max(1.0F, Math.min(def.w, strikeStart));
			if (t < strikeStart) {
				float k = easeOutCubic(t / reach);
				blend(from != null ? from : keys[PH_WINDUP], keys[PH_WINDUP], k, out);
				position = Math.min(1.0F, t / Math.max(1.0F, strikeStart));
				if (strikeStart > reach + 4.0F && (def.mods & MOD_CHARGE) == 0) {
					// a long windup (a heavy cut's charge) trembles a little as it builds
					float held = Math.min(1.0F, (t - reach) / Math.max(1.0F, strikeStart - reach));
					charge(out, c.age, held * 0.6F);
				}
			} else if (t < impact) {
				float u = (t - strikeStart) / s;
				blend(keys[PH_WINDUP], keys[PH_STRIKE], whip(u), out);
				position = 1.0F + u;
			} else {
				float u = Math.min(1.0F, (t - impact) / Math.max(1, def.f));
				blend(keys[PH_STRIKE], keys[PH_FOLLOW], easeOutCubic(u), out);
				position = 2.0F + u;
			}
		} else {
			int w = Math.max(1, def.w);
			int s = Math.max(1, def.s);
			int f = Math.max(1, def.f);
			if (t < w) {
				float u = t / w;
				blend(from != null ? from : keys[PH_WINDUP], keys[PH_WINDUP], easeOutCubic(u), out);
				position = u;
			} else if (t < w + s) {
				float u = (t - w) / s;
				blendOver(keys[PH_WINDUP], keys[PH_STRIKE], easeOutBack(u), out);
				position = 1.0F + u;
			} else if (t < w + s + f) {
				float u = (t - w - s) / f;
				blend(keys[PH_STRIKE], keys[PH_FOLLOW], easeOutCubic(u), out);
				position = 2.0F + u;
			} else {
				System.arraycopy(keys[PH_FOLLOW], 0, out, 0, CHANNELS);
				position = 3.0F;
			}
		}
		if (def.sideTurn != 0.0F && c.side != 0.0F) {
			float side = Math.max(-1.0F, Math.min(1.0F, c.side));
			out[TWIST] += -side * def.sideTurn * 0.5F;
			out[RAY] += side * def.sideTurn;
		}
		mods(def, pose, t, c, out);
		return position;
	}

	/** {@code sample} for the server: no modifiers, no side, starting at the windup key. */
	public static float sample(DuelPose pose, float t, int windup, float[] out) {
		return sample(pose, t, windup, null, NONE, out);
	}

	/** Swaps right and left (left-handed fighters): arms, legs, turns and the wrist roll. */
	public static void mirror(float[] v) {
		swap(v, RAX, LAX);
		swap(v, RAY, LAY);
		swap(v, RAZ, LAZ);
		swap(v, RLX, LLX);
		swap(v, RLZ, LLZ);
		swap(v, RLY, LLY);
		v[RAY] = -v[RAY];
		v[LAY] = -v[LAY];
		v[RAZ] = -v[RAZ];
		v[LAZ] = -v[LAZ];
		v[RLZ] = -v[RLZ];
		v[LLZ] = -v[LLZ];
		v[RLY] = -v[RLY];
		v[LLY] = -v[LLY];
		v[TWIST] = -v[TWIST];
		v[HY] = -v[HY];
		v[WRIST] = -v[WRIST];
	}

	// ============================================================================================ easing

	public static float easeOutCubic(float t) {
		float u = 1.0F - clamp01(t);
		return 1.0F - u * u * u;
	}

	/** easeOutBack with its overshoot near 1.08 (the same curve as {@code PoseLibrary.easeOutBack}). */
	public static float easeOutBack(float t) {
		float x = clamp01(t) - 1.0F;
		float c1 = 1.15F;
		float c3 = c1 + 1.0F;
		return 1.0F + c3 * x * x * x + c1 * x * x;
	}

	public static float easeInOutSine(float t) {
		return (float) (-(Math.cos(Math.PI * clamp01(t)) - 1.0D) / 2.0D);
	}

	/** The cut's strike: a short hesitation, then accelerating into contact (fastest at the impact). */
	public static float whip(float t) {
		float u = clamp01(t);
		return u * u * (0.35F + 0.65F * u);
	}

	// ============================================================================================ sampling helpers

	private static float[][] keys(Def def, Ctx c) {
		if (def.alt == null || c.alt <= 0.0F) {
			return def.keys;
		}
		float a = clamp01(c.alt);
		float[][] mixed = new float[PHASES][CHANNELS];
		for (int p = 0; p < PHASES; p++) {
			blend(def.keys[p], def.alt[p], a, mixed[p]);
		}
		return mixed;
	}

	private static void blend(float[] a, float[] b, float k, float[] out) {
		for (int i = 0; i < CHANNELS; i++) {
			float x = i < a.length ? a[i] : 0.0F;
			float y = b[i];
			if (Float.isNaN(x)) {
				x = y;
			}
			out[i] = x + (y - x) * k;
		}
	}

	/** Like {@link #blend} but allowed to overshoot (easeOutBack). */
	private static void blendOver(float[] a, float[] b, float k, float[] out) {
		blend(a, b, k, out);
	}

	private static void mods(Def def, DuelPose pose, float t, Ctx c, float[] out) {
		float age = c.age;
		if (Float.isNaN(age)) {
			return;
		}
		int mods = def.mods;
		if (mods == 0) {
			return;
		}
		float ramp = Math.min(1.0F, Math.max(0.0F, t - def.w - def.s) / 4.0F);
		if (pose.kind() == DuelPose.Kind.STANCE) {
			ramp = Math.min(1.0F, t / 6.0F);
		}
		float a = def.amp * ramp;
		if ((mods & MOD_BREATH) != 0) {
			float b = (float) Math.sin(age * 0.0785F) * a; // 0.25 Hz
			out[RAX] += b;
			out[LAX] += b;
			out[LEAN] += b * 0.5F;
			out[HX] -= b * 0.3F;
		}
		if ((mods & MOD_TREMBLE) != 0) {
			float b = (float) Math.sin(age * 0.628F) * a; // 2 Hz
			out[RAX] += b;
			out[LAX] -= b;
		}
		if ((mods & MOD_CHARGE) != 0) {
			charge(out, age, c.charge);
		}
		if ((mods & MOD_LOCK) != 0) {
			float p = Math.max(-1.0F, Math.min(1.0F, c.pressure));
			float amp = Math.abs(p) * 0.06F + 0.012F;
			float shake = (float) Math.sin(age * 2.2F) * amp * ramp;
			out[LEAN] += 0.15F * p * ramp;
			out[TWIST] += 0.05F * p * ramp;
			out[RAX] += shake;
			out[LAX] += shake;
			out[WRIST] += (float) Math.sin(age * 3.3F) * amp * 0.5F * ramp;
			out[HX] += (float) Math.sin(age * 1.9F) * amp * 0.4F * ramp;
		}
		if ((mods & MOD_STAGGER) != 0) {
			float sway = (float) Math.sin(age * 0.31F) * a;
			float shiver = (float) Math.sin(age * 1.7F) * a * 0.5F;
			out[LEAN] += sway * 0.5F;
			out[TWIST] += sway * 0.6F;
			out[HX] += sway;
			out[RAX] += shiver;
			out[LAX] -= shiver;
		}
	}

	/** The growing shake of a held charge (0..1). */
	private static void charge(float[] out, float age, float progress) {
		if (Float.isNaN(age) || progress <= 0.0F) {
			return;
		}
		float p = clamp01(progress);
		float shake = (float) Math.sin(age * 2.5F) * 0.03F * p;
		out[RAX] += shake;
		out[LAX] -= shake;
		out[WRIST] += (float) Math.sin(age * 3.1F) * 0.04F * p;
		out[LEAN] -= 0.04F * p;
	}

	private static void swap(float[] v, int a, int b) {
		float t = v[a];
		v[a] = v[b];
		v[b] = t;
	}

	private static float clamp01(float v) {
		return v < 0.0F ? 0.0F : v > 1.0F ? 1.0F : v;
	}

	// ============================================================================================ authoring by intent

	/** One key as an intent: body, sword arm and blade directions (degrees), off arm, legs and head as channels. */
	private static final class K {
		float lean, twist, lift, tilt, hx;
		float armAz, armEl, bladeAz, bladeEl;
		float lax, lay, laz;
		float rlx, rlz, llx, llz;
		/** The wrist solution to prefer (radians), NaN: the previous key's. */
		float wristPrior = Float.NaN;

		K body(float lean, float twist) {
			this.lean = lean;
			this.twist = twist;
			return this;
		}

		K arm(float az, float el) {
			this.armAz = az;
			this.armEl = el;
			return this;
		}

		K blade(float az, float el) {
			this.bladeAz = az;
			this.bladeEl = el;
			return this;
		}

		K off(float x, float y, float z) {
			this.lax = x;
			this.lay = y;
			this.laz = z;
			return this;
		}

		K legs(float rlx, float llx) {
			this.rlx = rlx;
			this.llx = llx;
			return this;
		}

		K wide(float rlz, float llz) {
			this.rlz = rlz;
			this.llz = llz;
			return this;
		}

		K head(float hx) {
			this.hx = hx;
			return this;
		}

		K lift(float lift) {
			this.lift = lift;
			return this;
		}

		K tilt(float tilt) {
			this.tilt = tilt;
			return this;
		}

		K wrist(float prior) {
			this.wristPrior = prior;
			return this;
		}

		/** The same key cut from the other side (the mirrored left cuts): azimuths and twist negated. */
		K mirrored() {
			K k = new K();
			k.lean = lean;
			k.twist = -twist;
			k.lift = lift;
			k.tilt = tilt;
			k.hx = hx;
			k.armAz = -armAz;
			k.armEl = armEl;
			k.bladeAz = -bladeAz;
			k.bladeEl = bladeEl;
			k.lax = lax;
			k.lay = lay;
			k.laz = laz;
			k.rlx = rlx;
			k.rlz = rlz;
			k.llx = llx;
			k.llz = llz;
			k.wristPrior = Float.isNaN(wristPrior) ? Float.NaN : -wristPrior;
			return k;
		}
	}

	private static K k() {
		return new K();
	}

	/** The blade's direction in the sword arm's own frame (model space, wrist 0), from the real item chain. */
	private static final Vector3f BLADE_LOCAL = bladeLocal();

	private static Vector3f bladeLocal() {
		float[] zero = new float[CHANNELS];
		Matrix4f m = BladeKinematics.hand(zero, false, false, new Matrix4f());
		BladeKinematics.toItem(BladeShape.PEDANG, false, m);
		Vector3f grip = new Vector3f();
		Vector3f tip = new Vector3f();
		BladeKinematics.blade(BladeShape.PEDANG, false, m, grip, tip);
		// facing frame -> model frame (the root's scale(-1, -1, 1))
		Vector3f d = tip.sub(grip);
		return new Vector3f(-d.x, -d.y, d.z).normalize();
	}

	/** A body direction (degrees: azimuth 0 ahead, + right; elevation + up) in model space (y down, front −z, right −x). */
	private static Vector3f bodyDir(float az, float el) {
		double a = Math.toRadians(az);
		double e = Math.toRadians(el);
		double r = Math.sin(a) * Math.cos(e);
		double f = Math.cos(a) * Math.cos(e);
		double u = Math.sin(e);
		return new Vector3f((float) -r, (float) -u, (float) -f);
	}

	/**
	 * Solves a key: the arm rotation {@code Ry(y) Rx(x) Ry(wrist)} (zRot 0) that turns the arm's long axis onto the arm
	 * direction and the blade onto the blade direction (made perpendicular to the arm, as the grip demands).
	 */
	private static float[] solve(K k, float wristPrior, @Nullable float[] previous) {
		Vector3f e1 = new Vector3f(0.0F, 1.0F, 0.0F);
		Vector3f e2 = new Vector3f(BLADE_LOCAL).sub(new Vector3f(e1).mul(BLADE_LOCAL.dot(e1))).normalize();
		Vector3f e3 = new Vector3f(e1).cross(e2);
		Vector3f a = bodyDir(k.armAz, k.armEl).normalize();
		Vector3f b = bodyDir(k.bladeAz, k.bladeEl);
		Vector3f t2 = new Vector3f(b).sub(new Vector3f(a).mul(b.dot(a)));
		if (t2.lengthSquared() < 1.0E-6F) {
			// blade asked along the arm: take any perpendicular (forward-ish)
			t2.set(bodyDir(k.armAz, k.armEl - 90.0F));
		}
		t2.normalize();
		Vector3f t3 = new Vector3f(a).cross(t2);
		// R = F_target * F_local^T (row-major 3x3)
		float[] fl = {e1.x, e2.x, e3.x, e1.y, e2.y, e3.y, e1.z, e2.z, e3.z};
		float[] ft = {a.x, t2.x, t3.x, a.y, t2.y, t3.y, a.z, t2.z, t3.z};
		float[] r = mul(ft, transpose(fl));
		// R Ry(-w) = Ry(y) Rx(x) needs its (1, 0) element zero: R10 cos w + R12 sin w = 0
		double w0 = Math.atan2(-r[3], r[5]);
		float[] best = null;
		double bestCost = Double.MAX_VALUE;
		for (int i = 0; i < 2; i++) {
			double w = w0 + i * Math.PI;
			float[] m = mul(r, rotY((float) -w));
			// the arm's natural range: xRot from a little behind the head (−4.2) to a little behind the hip (2.1)
			float xn = wrap((float) Math.atan2(-m[5], m[4]), -4.2F);
			float yn = wrap((float) Math.atan2(-m[6], m[0]), (float) -Math.PI);
			float wf = near((float) w, wristPrior);
			// keyed against the previous key so the blend between them takes the short way round
			float xf = previous != null ? near(xn, previous[RAX]) : xn;
			float yf = previous != null ? near(yn, previous[RAY]) : yn;
			double cost = 4.0D * Math.max(0.0F, xn - 0.6F) + 0.35D * Math.abs(yn) + 0.25D * Math.abs(wf - wristPrior)
					+ (previous != null ? 0.2D * (Math.abs(yf - previous[RAY]) + Math.abs(xf - previous[RAX])) : 0.0D);
			if (cost < bestCost) {
				bestCost = cost;
				best = new float[] {xf, yf, wf};
			}
		}
		float[] out = new float[CHANNELS];
		out[HX] = k.hx;
		out[LEAN] = k.lean;
		out[TWIST] = k.twist;
		out[RAX] = best[0];
		out[RAY] = best[1];
		out[RAZ] = 0.0F;
		out[LAX] = k.lax;
		out[LAY] = k.lay;
		out[LAZ] = k.laz;
		out[RLX] = k.rlx;
		out[RLZ] = k.rlz;
		out[LLX] = k.llx;
		out[LLZ] = k.llz;
		out[LIFT] = k.lift;
		out[TILT] = k.tilt;
		out[WRIST] = best[2];
		return out;
	}

	private static Def put(DuelPose pose, K wind, K strike, K follow) {
		return put(pose, wind, strike, follow, 0.0F);
	}

	private static Def put(DuelPose pose, K wind, K strike, K follow, float firstWrist) {
		Def def = new Def();
		def.keys[PH_WINDUP] = solve(wind, Float.isNaN(wind.wristPrior) ? firstWrist : wind.wristPrior, null);
		def.keys[PH_STRIKE] = solve(strike, Float.isNaN(strike.wristPrior) ? def.keys[PH_WINDUP][WRIST] : strike.wristPrior,
				def.keys[PH_WINDUP]);
		def.keys[PH_FOLLOW] = solve(follow, Float.isNaN(follow.wristPrior) ? def.keys[PH_STRIKE][WRIST] : follow.wristPrior,
				def.keys[PH_STRIKE]);
		DEFS.put(pose, def);
		INTENTS.put(pose, new K[] {wind, strike, follow});
		return def;
	}

	/** A pose that holds one key (stances). */
	private static Def hold(DuelPose pose, K key) {
		return put(pose, key, key, key);
	}

	/** The mirrored cut of {@code source}'s intents. */
	private static Def mirror(DuelPose pose, DuelPose source, float firstWrist) {
		K[] src = INTENTS.get(source);
		Def from = DEFS.get(source);
		Def def = put(pose, src[0].mirrored(), src[1].mirrored(), src[2].mirrored(), firstWrist);
		def.w = from.w;
		def.s = from.s;
		def.f = from.f;
		def.mods = from.mods;
		def.amp = from.amp;
		def.offAim = from.offAim;
		def.firstPerson = from.firstPerson;
		return def;
	}

	private static final Map<DuelPose, K[]> INTENTS = new EnumMap<>(DuelPose.class);

	private static Def timing(Def def, int w, int s, int f) {
		def.w = w;
		def.s = s;
		def.f = f;
		return def;
	}

	static {
		final float HALF_PI = (float) (Math.PI / 2.0D);

		// ------------------------------------------------------------------ stances
		// The opening stance: side-on, right foot ahead, the blade low and forward at the opponent's chest.
		Def d = hold(DuelPose.SIKAP_PEMBUKA, k().body(0.05F, -0.2F).arm(15, -40).blade(-5, 8).off(-0.5F, 0.15F, -0.3F).legs(-0.25F, 0.15F)
				.head(0.05F));
		timing(d, 6, 4, 4).mods = MOD_BREATH;
		d.amp = 0.03F;
		d.firstPerson = FirstPerson.GUARD;
		// High guard: the hands above the brow, the blade slanting back over the head, ready to come down.
		d = hold(DuelPose.KUDA_TINGGI, k().body(-0.05F, 0.1F).arm(10, 68).blade(170, 22).off(-2.1F, 0.25F, 0.1F).legs(-0.2F, 0.2F)
				.head(-0.1F));
		timing(d, 3, 2, 2).mods = MOD_BREATH;
		d.amp = 0.02F;
		d.offAim = true;
		d.firstPerson = FirstPerson.GUARD;
		// Middle guard: the blade forward and up at the opponent's throat, the off hand ready at the chest.
		d = hold(DuelPose.KUDA_TENGAH, k().body(0.08F, -0.15F).arm(5, -35).blade(-8, 38).off(-1.0F, 0.5F, 0.0F).legs(-0.2F, 0.2F));
		timing(d, 3, 2, 2).mods = MOD_BREATH;
		d.amp = 0.025F;
		d.firstPerson = FirstPerson.GUARD;
		// Low guard: knees bent, the point low at the opponent's knees, the feet wide.
		d = hold(DuelPose.KUDA_RENDAH, k().body(0.2F, -0.1F).arm(60, -35).blade(5, -15).off(-0.4F, 0.2F, -0.4F).legs(-0.45F, 0.35F)
				.wide(0.08F, -0.08F).lift(-0.12F).head(-0.1F));
		timing(d, 4, 2, 2).mods = MOD_BREATH;
		d.amp = 0.02F;
		d.firstPerson = FirstPerson.GUARD;

		// ------------------------------------------------------------------ cuts
		// From above: the blade lifted behind the head, then straight down onto the opponent's crown, the body bending
		// into it on the front foot, the point ending low ahead.
		d = put(DuelPose.TEBAS_ATAS,
				k().body(-0.12F, 0.05F).arm(5, 80).blade(180, 18).off(-0.6F, 0.2F, -0.25F).legs(0.1F, -0.1F).head(-0.2F),
				k().body(0.3F, -0.05F).arm(-14, -32).blade(-6, 58).off(-0.5F, 0.3F, -0.3F).legs(-0.35F, 0.25F).head(0.1F),
				k().body(0.38F, -0.05F).arm(-14, -78).blade(-6, 10).off(-0.3F, 0.2F, -0.45F).legs(-0.4F, 0.3F).lift(-0.04F).head(0.15F));
		timing(d, 4, 2, 3).firstPerson = FirstPerson.CUT_HIGH;
		// From the upper right: cocked over the right shoulder, down across the body to the lower left.
		d = put(DuelPose.TEBAS_ATAS_KANAN,
				k().body(-0.05F, 0.45F).arm(60, 55).blade(150, 40).off(-0.6F, 0.4F, -0.2F).legs(0.1F, -0.1F).head(-0.1F),
				k().body(0.22F, -0.1F).arm(30, -10).blade(-38, 12).off(-0.4F, 0.2F, -0.35F).legs(-0.3F, 0.25F).head(0.05F),
				k().body(0.25F, -0.45F).arm(-40, -55).blade(-120, -35).off(-0.2F, 0.0F, -0.5F).legs(-0.35F, 0.3F).head(0.1F));
		timing(d, 4, 2, 3).firstPerson = FirstPerson.CUT_SIDE;
		// From the right: the blade over the right shoulder, the body wound right; it unwinds into a level sweep at chest
		// height that crosses the centre a blade's length ahead, and wraps past the left side.
		d = put(DuelPose.TEBAS_KANAN,
				k().body(-0.05F, 0.55F).arm(110, 5).blade(160, 15).off(-0.7F, 0.6F, -0.1F).legs(0.1F, -0.15F),
				k().body(0.2F, -0.2F).arm(35, 0).blade(-50, -5).off(-0.4F, 0.1F, -0.35F).legs(-0.3F, 0.25F),
				k().body(0.1F, -0.6F).arm(-60, -10).blade(-150, -10).off(-0.2F, -0.2F, -0.6F).legs(-0.3F, 0.3F));
		timing(d, 4, 2, 3).firstPerson = FirstPerson.CUT_SIDE;
		// From the lower right: low behind the right hip, rising across the body to the upper left.
		d = put(DuelPose.TEBAS_BAWAH_KANAN,
				k().body(0.12F, 0.35F).arm(50, -70).blade(140, -30).off(-0.7F, 0.4F, -0.2F).legs(0.15F, -0.15F).lift(-0.08F),
				k().body(0.15F, -0.1F).arm(30, -25).blade(-40, 25).off(-0.4F, 0.2F, -0.35F).legs(-0.3F, 0.25F),
				k().body(-0.05F, -0.4F).arm(-30, 50).blade(-110, 55).off(-0.3F, 0.0F, -0.5F).legs(-0.25F, 0.25F).head(-0.15F));
		timing(d, 4, 2, 3).firstPerson = FirstPerson.CUT_LOW;
		// From below: the point low ahead, the body crouched; it rises straight up through the opponent's guard and ends
		// high over the head.
		d = put(DuelPose.TEBAS_BAWAH,
				k().body(0.25F, 0.05F).arm(8, -82).blade(4, 8).off(-0.5F, 0.3F, -0.3F).legs(-0.2F, 0.3F).lift(-0.15F).head(0.1F),
				k().body(0.1F, -0.05F).arm(-12, -48).blade(-6, 42).off(-0.4F, 0.3F, -0.35F).legs(-0.35F, 0.25F).lift(-0.05F),
				k().body(-0.1F, -0.05F).arm(5, 42).blade(180, 48).off(-0.3F, 0.2F, -0.45F).legs(-0.3F, 0.3F).head(-0.3F));
		timing(d, 5, 2, 3).firstPerson = FirstPerson.CUT_LOW;
		// The three cuts from the left are their right twins seen in a mirror, but cut by the same right arm crossing the
		// chest (backhand): the twist and the turns change sign, and since the sword shoulder is off the centre line the
		// arm reaches further across so that the blade still meets the opponent on the centre line.
		d = put(DuelPose.TEBAS_BAWAH_KIRI,
				k().body(0.12F, -0.4F).arm(-55, -60).blade(-150, -25).off(-0.3F, 0.1F, -0.5F).legs(-0.1F, 0.15F).lift(-0.08F),
				k().body(0.15F, 0.1F).arm(-40, -35).blade(30, 22).off(-0.5F, 0.3F, -0.3F).legs(-0.3F, 0.25F),
				k().body(-0.05F, 0.4F).arm(30, 48).blade(110, 50).off(-0.6F, 0.4F, -0.2F).legs(-0.25F, 0.25F).head(-0.15F));
		timing(d, 4, 2, 3).firstPerson = FirstPerson.CUT_LOW;
		d = put(DuelPose.TEBAS_KIRI,
				k().body(-0.05F, -0.55F).arm(-70, 12).blade(-155, 18).off(-0.3F, 0.0F, -0.5F).legs(0.1F, -0.15F),
				k().body(0.2F, 0.15F).arm(-60, -12).blade(25, -6).off(-0.6F, 0.4F, -0.3F).legs(-0.3F, 0.25F),
				k().body(0.1F, 0.6F).arm(70, -10).blade(150, -10).off(-0.7F, 0.6F, -0.1F).legs(-0.3F, 0.3F));
		timing(d, 4, 2, 3).firstPerson = FirstPerson.CUT_SIDE;
		d = put(DuelPose.TEBAS_ATAS_KIRI,
				k().body(-0.05F, -0.45F).arm(-45, 60).blade(-150, 40).off(-0.4F, 0.1F, -0.5F).legs(0.1F, -0.1F).head(-0.1F),
				k().body(0.22F, 0.1F).arm(-50, -20).blade(18, 14).off(-0.6F, 0.4F, -0.25F).legs(-0.3F, 0.25F).head(0.05F),
				k().body(0.25F, 0.45F).arm(45, -55).blade(120, -35).off(-0.7F, 0.5F, -0.1F).legs(-0.35F, 0.3F).head(0.1F));
		timing(d, 4, 2, 3).firstPerson = FirstPerson.CUT_SIDE;
		// The thrust: the hand drawn back to the right hip, the point aimed; then a lunge on the front leg, arm and blade
		// one line at the opponent's heart.
		d = put(DuelPose.TUSUK,
				k().body(-0.05F, 0.45F).arm(95, -25).blade(5, 0).off(-0.9F, 0.4F, 0.1F).legs(0.3F, -0.1F),
				k().body(0.28F, -0.3F).arm(55, -28).blade(-28, -2).off(0.3F, 0.0F, -0.5F).legs(-0.65F, 0.4F).lift(-0.06F),
				k().body(0.3F, -0.32F).arm(52, -30).blade(-26, -4).off(0.35F, 0.0F, -0.55F).legs(-0.65F, 0.4F).lift(-0.07F));
		timing(d, 4, 2, 2).firstPerson = FirstPerson.THRUST;
		// The spin: coiled right and low, the whole body turns through a wide level sweep and past the left.
		d = put(DuelPose.PUTAR,
				k().body(0.1F, 0.95F).arm(125, -5).blade(170, 25).off(-0.4F, 0.6F, -0.5F).legs(0.2F, -0.2F).wide(0.1F, -0.1F).lift(-0.1F),
				k().body(0.2F, -0.3F).arm(25, -8).blade(-62, -8).off(0.2F, -0.3F, -0.9F).legs(-0.2F, 0.2F).wide(0.1F, -0.1F).lift(-0.12F),
				k().body(0.1F, -1.4F).arm(-115, -5).blade(-170, -5).off(0.3F, -0.5F, -1.0F).legs(-0.1F, 0.2F).wide(0.1F, -0.1F).lift(-0.08F));
		timing(d, 3, 4, 3).firstPerson = FirstPerson.CUT_SIDE;

		// ------------------------------------------------------------------ the charge
		// Charging a heavy cut: both hands high over the head, the blade back, trembling more as it builds.
		d = hold(DuelPose.ANGKAT_BERAT, k().body(-0.15F, 0.2F).arm(8, 84).blade(180, 8).off(-2.8F, 0.2F, 0.0F).legs(0.2F, -0.3F)
				.head(-0.15F));
		timing(d, 4, 3, 2).mods = MOD_CHARGE | MOD_BREATH;
		d.amp = 0.02F;
		d.offAim = true;
		d.firstPerson = FirstPerson.CHARGE;

		// ------------------------------------------------------------------ deflects
		// Deflect: the blade snapped upright before the body on the side the blow comes from, flat to the attack; a
		// second key set catches blows from above with the blade held level over the head.
		d = put(DuelPose.TANGKIS,
				k().body(0.05F, -0.05F).arm(12, -30).blade(-10, 45).off(-0.8F, 0.4F, 0.0F).legs(-0.2F, 0.2F),
				k().body(0.0F, -0.1F).arm(2, -12).blade(-30, 72).off(-0.7F, 0.4F, -0.1F).legs(-0.2F, 0.25F),
				k().body(0.02F, -0.1F).arm(4, -22).blade(-22, 58).off(-0.8F, 0.4F, -0.05F).legs(-0.2F, 0.22F));
		timing(d, 1, 1, 3).sideTurn = 0.6F;
		d.firstPerson = FirstPerson.PARRY;
		d.alt = altKeys(
				k().body(0.0F, 0.0F).arm(10, 45).blade(-60, 40).off(-1.6F, 0.4F, 0.0F).legs(-0.15F, 0.2F).head(-0.15F),
				k().body(-0.05F, 0.0F).arm(14, 72).blade(-88, 12).off(-2.2F, 0.3F, 0.0F).legs(-0.15F, 0.25F).lift(-0.06F).head(-0.25F),
				k().body(-0.03F, 0.0F).arm(12, 64).blade(-80, 18).off(-2.0F, 0.3F, 0.0F).legs(-0.15F, 0.22F).lift(-0.04F).head(-0.2F));
		// Perfect deflect: the same snap, the body lifting, then the attacking blade thrown outward and up.
		d = put(DuelPose.TANGKIS_SEMPURNA,
				k().body(0.05F, -0.05F).arm(12, -30).blade(-10, 45).off(-0.8F, 0.4F, 0.0F).legs(-0.2F, 0.2F),
				k().body(-0.05F, -0.12F).arm(2, -10).blade(-32, 74).off(-0.6F, 0.4F, -0.1F).legs(-0.2F, 0.25F),
				k().body(-0.02F, 0.15F).arm(48, -6).blade(40, 62).off(-0.6F, 0.4F, -0.2F).legs(-0.2F, 0.25F).head(-0.05F));
		timing(d, 1, 1, 4).sideTurn = 0.6F;
		d.firstPerson = FirstPerson.PARRY;
		d.alt = altKeys(
				k().body(0.0F, 0.0F).arm(10, 45).blade(-60, 40).off(-1.6F, 0.4F, 0.0F).legs(-0.15F, 0.2F).head(-0.15F),
				k().body(-0.08F, 0.0F).arm(14, 74).blade(-88, 14).off(-2.2F, 0.3F, 0.0F).legs(-0.15F, 0.25F).lift(-0.04F).head(-0.25F),
				k().body(-0.05F, 0.2F).arm(40, 70).blade(60, 40).off(-1.8F, 0.3F, 0.1F).legs(-0.15F, 0.22F).head(-0.2F));
		// Blade Step: the right knee comes up, the foot stamps the thrusting blade into the ground, the sword points down
		// at the trapped blade.
		d = put(DuelPose.INJAK,
				k().body(-0.05F, -0.05F).arm(12, -20).blade(-5, 50).off(-0.9F, 0.4F, -0.1F).legs(-1.2F, 0.15F).lift(0.05F),
				k().body(0.28F, -0.1F).arm(10, -8).blade(0, -60).off(-0.4F, 0.2F, -0.4F).legs(-0.35F, 0.25F).lift(0.1F).head(0.3F),
				k().body(0.22F, -0.1F).arm(10, -20).blade(-2, -45).off(-0.5F, 0.2F, -0.35F).legs(-0.3F, 0.25F).lift(0.04F).head(0.2F));
		timing(d, 2, 2, 4).firstPerson = FirstPerson.THRUST;

		// ------------------------------------------------------------------ the lock
		// Blade lock: both hands on the hilt, the blade upright and forward against the other, leaning in, legs braced.
		d = hold(DuelPose.KUNCI_BILAH, k().body(0.3F, -0.1F).arm(4, -16).blade(-12, 68).off(-1.4F, 0.4F, 0.0F).legs(-0.5F, 0.35F)
				.lift(-0.04F).head(-0.05F));
		timing(d, 2, 2, 2).mods = MOD_LOCK;
		d.amp = 1.0F;
		d.offAim = true;
		d.firstPerson = FirstPerson.LOCK;

		// ------------------------------------------------------------------ reactions
		// Recoil: the sword arm knocked up and back, the blade over the shoulder, a step back.
		d = put(DuelPose.HENTAK,
				k().body(0.0F, 0.1F).arm(30, 10).blade(80, 70).off(-0.5F, 0.3F, -0.3F).legs(0.1F, 0.2F),
				k().body(-0.25F, 0.3F).arm(42, 42).blade(150, 45).off(-0.3F, 0.2F, -0.6F).legs(0.3F, 0.4F).head(-0.2F),
				k().body(-0.1F, 0.15F).arm(25, -10).blade(40, 55).off(-0.6F, 0.3F, -0.3F).legs(0.1F, 0.25F).head(-0.05F));
		timing(d, 1, 2, 6).sideTurn = 0.3F;
		d.firstPerson = FirstPerson.RECOIL;
		// Thrown back: both arms flung open, the body tipping back, the head snapping up.
		d = put(DuelPose.TERPENTAL,
				k().body(-0.15F, 0.1F).arm(50, 20).blade(120, 60).off(-0.4F, 0.0F, -0.5F).legs(0.2F, 0.2F).head(-0.1F),
				k().body(-0.4F, 0.15F).arm(80, 12).blade(170, 30).off(-0.3F, 0.0F, -0.9F).legs(0.4F, 0.1F).tilt(-0.15F).head(-0.3F),
				k().body(-0.15F, 0.05F).arm(35, -25).blade(30, 40).off(-0.4F, 0.2F, -0.5F).legs(0.15F, 0.2F).tilt(-0.04F).head(-0.1F));
		timing(d, 1, 3, 8).sideTurn = 0.3F;
		d.firstPerson = FirstPerson.RECOIL;
		// Posture broken: head bowed, the blade hanging, knees soft, swaying and shaking.
		d = hold(DuelPose.TERHUYUNG, k().body(0.45F, 0.1F).arm(15, -80).blade(5, -12).off(0.1F, 0.0F, -0.15F).legs(-0.15F, 0.25F)
				.lift(-0.1F).head(0.5F));
		timing(d, 2, 3, 2).mods = MOD_STAGGER;
		d.amp = 0.08F;
		d.firstPerson = FirstPerson.RECOIL;
		// Struck: the body twists away from the blow, the guard knocked aside.
		d = put(DuelPose.TERKENA,
				k().body(0.0F, 0.0F).arm(20, -30).blade(10, 35).off(-0.6F, 0.3F, -0.2F).legs(-0.15F, 0.2F),
				k().body(-0.15F, 0.35F).arm(42, -28).blade(30, 22).off(-0.3F, 0.1F, -0.5F).legs(0.1F, 0.25F).head(-0.15F),
				k().body(0.0F, 0.1F).arm(15, -34).blade(0, 35).off(-0.7F, 0.4F, -0.1F).legs(-0.15F, 0.2F));
		timing(d, 1, 2, 4).sideTurn = 0.7F;
		d.firstPerson = FirstPerson.RECOIL;
		// Shadow Step: low and wide, the body turned into the step, the blade kept in guard.
		d = put(DuelPose.MENGHINDAR,
				k().body(0.1F, 0.0F).arm(10, -40).blade(-5, 35).off(-0.6F, 0.4F, -0.3F).legs(-0.2F, 0.2F).wide(0.2F, -0.2F),
				k().body(0.25F, 0.0F).arm(15, -48).blade(0, 25).off(-0.3F, 0.3F, -0.8F).legs(-0.3F, 0.3F).wide(0.4F, -0.4F).lift(-0.18F)
						.tilt(0.1F),
				k().body(0.12F, 0.0F).arm(8, -38).blade(-6, 36).off(-0.7F, 0.4F, -0.2F).legs(-0.2F, 0.2F).wide(0.15F, -0.15F).lift(-0.05F));
		timing(d, 1, 2, 4).sideTurn = 0.9F;
		d.firstPerson = FirstPerson.GUARD;

		// ------------------------------------------------------------------ ceremonies
		// Drawing: the left hand on the scabbard at the left hip, the right hand reaches across and draws the blade out
		// level, then presents it forward and up.
		d = put(DuelPose.HUNUS,
				k().body(0.08F, -0.3F).arm(-38, -62).blade(-160, -28).off(-0.45F, 0.65F, 0.0F).legs(-0.1F, 0.1F).head(0.15F),
				k().body(0.05F, -0.05F).arm(-5, -25).blade(65, 10).off(-0.4F, 0.6F, -0.05F).legs(-0.15F, 0.15F).head(0.05F).wrist(HALF_PI),
				k().body(0.06F, -0.18F).arm(15, -32).blade(-4, 40).off(-0.7F, 0.45F, -0.1F).legs(-0.25F, 0.15F));
		timing(d, 6, 4, 6).firstPerson = FirstPerson.DRAW;
		// Sheathing: a flick of the blade down to the right (shaking off blood and qi), then across into the scabbard at
		// the left hip; the item is put away after tick 12.
		d = put(DuelPose.SARUNG,
				k().body(0.05F, 0.2F).arm(60, -12).blade(80, -42).off(-0.4F, 0.4F, -0.1F).legs(-0.15F, 0.15F),
				k().body(0.08F, -0.3F).arm(-38, -60).blade(-162, -30).off(-0.45F, 0.65F, 0.0F).legs(-0.1F, 0.1F).head(0.15F),
				k().body(0.0F, -0.1F).arm(-30, -70).blade(-170, -45).off(-0.4F, 0.6F, 0.0F).legs(0.0F, 0.0F).head(-0.05F));
		timing(d, 4, 4, 10).hideItemAt = 12;
		d.firstPerson = FirstPerson.DRAW;
		// The loser kneels on the right knee, head bowed, the blade planted point down before him, the left hand on the
		// pommel.
		d = put(DuelPose.KALAH_BERLUTUT,
				k().body(0.2F, 0.0F).arm(10, -40).blade(0, -40).off(-0.6F, 0.3F, -0.1F).legs(-0.6F, 0.15F).lift(-0.2F).head(0.25F),
				k().body(0.3F, 0.0F).arm(8, -6).blade(0, -86).off(-1.1F, 0.4F, 0.0F).legs(-1.4F, 0.3F).lift(-0.45F).head(0.4F),
				k().body(0.35F, 0.0F).arm(8, -8).blade(0, -88).off(-1.1F, 0.4F, 0.0F).legs(-1.4F, 0.3F).lift(-0.45F).head(0.45F));
		timing(d, 6, 6, 8).mods = MOD_BREATH;
		d.amp = 0.03F;
		d.offAim = true;
		d.firstPerson = FirstPerson.RECOIL;
		// The salute: the sword hand's fist meets the open left palm before the chest, a bow from the hips; the blade hangs
		// down along the forearm's line.
		d = put(DuelPose.HORMAT,
				k().body(0.05F, 0.0F).arm(-15, -35).blade(0, -50).off(-0.9F, 0.4F, 0.0F).legs(0.0F, 0.0F),
				k().body(0.2F, 0.0F).arm(-32, -14).blade(-5, -76).off(-1.3F, 0.6F, 0.0F).legs(0.0F, 0.0F).head(0.25F),
				k().body(0.22F, 0.0F).arm(-32, -15).blade(-5, -77).off(-1.3F, 0.6F, 0.0F).legs(0.0F, 0.0F).head(0.28F));
		timing(d, 4, 4, 10).mods = MOD_BREATH;
		d.amp = 0.015F;
		d.firstPerson = FirstPerson.DRAW;
	}

	private static float[][] altKeys(K wind, K strike, K follow) {
		float[][] keys = new float[PHASES][];
		keys[PH_WINDUP] = solve(wind, 0.0F, null);
		keys[PH_STRIKE] = solve(strike, keys[PH_WINDUP][WRIST], keys[PH_WINDUP]);
		keys[PH_FOLLOW] = solve(follow, keys[PH_STRIKE][WRIST], keys[PH_STRIKE]);
		return keys;
	}

	// ============================================================================================ 3x3 helpers (row-major)

	private static float[] rotY(float a) {
		float c = (float) Math.cos(a), s = (float) Math.sin(a);
		return new float[] {c, 0, s, 0, 1, 0, -s, 0, c};
	}

	private static float[] transpose(float[] m) {
		return new float[] {m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]};
	}

	private static float[] mul(float[] a, float[] b) {
		float[] m = new float[9];
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) {
				m[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
			}
		}
		return m;
	}

	/** {@code angle} moved by whole turns into [{@code low}, {@code low} + 2π). */
	private static float wrap(float angle, float low) {
		float twoPi = (float) (Math.PI * 2.0D);
		float a = angle;
		while (a < low) {
			a += twoPi;
		}
		while (a >= low + twoPi) {
			a -= twoPi;
		}
		return a;
	}

	private static float near(float angle, float reference) {
		if (Float.isNaN(reference)) {
			return angle;
		}
		float twoPi = (float) (Math.PI * 2.0D);
		float d = angle - reference;
		d -= twoPi * Math.round(d / twoPi);
		return reference + d;
	}

	private DuelPoseTable() {
	}
}
