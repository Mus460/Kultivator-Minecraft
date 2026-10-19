package dev.kultivator.client.anim;

import dev.kultivator.technique.spec.dsl.Pose;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The keyframes of every cast {@link Pose} (DESIGN 27.2.3): a windup key (the anticipation, reached in 3 ticks with
 * easeOutCubic) and a strike key (reached with easeOutBack, overshoot 1.08), held with a per-pose breathing or tremble,
 * plus the per-pose extras (alternating fists, the floating bob, the growing charge shake...). Pure functions; the timing
 * of a real cast lives in {@link CastPoses}.
 * <p>
 * Model space (verified on {@code MeditationPose} and {@code AnimationUtils}): y points down, the front is -z. Arm
 * {@code xRot < 0} raises the arm forward and up (-π is straight up); right arm {@code zRot > 0} moves it outward, left
 * arm {@code zRot < 0}; right arm {@code yRot < 0} swings it inward across the chest, left arm {@code yRot > 0}. Head
 * channels are offsets added to the look. {@code LEAN} is the body's forward bend at the hips, {@code TWIST} its turn,
 * {@code TILT} a whole-body forward pitch about the feet (dashes) and {@code LIFT} a vertical offset in blocks.
 */
public final class PoseLibrary {

	// Channels of the internal keys (the first 15 match PoseAngles' order, the rest are extras).
	public static final int HX = 0, HY = 1, LEAN = 2, TWIST = 3, RAX = 4, RAY = 5, RAZ = 6, LAX = 7, LAY = 8, LAZ = 9, RLX = 10,
			RLZ = 11, LLX = 12, LLZ = 13, LIFT = 14, RLY = 15, LLY = 16, TILT = 17;
	public static final int CHANNELS = 18;

	// Hold modifiers.
	static final int MOD_BREATH_X = 1, MOD_BREATH_Z = 2, MOD_TREMBLE = 4, MOD_FLOAT = 8, MOD_BEAT = 16, MOD_SHAKE_HEAD = 32,
			MOD_LIFT_GROW = 64, MOD_CHARGE_SHAKE = 128;

	/** How a pose looks from one's own eyes (FirstPersonCast). */
	public enum FirstPerson { NONE, THRUST, TWIN, RAISE, SLAM, SPREAD, CHARGE }

	/** One pose: its keys, timing and extras. */
	public static final class Def {
		final float[] wind = nanKey();
		final float[] strike = nanKey();
		int mask;
		int windTicks = 3;
		int strikeTicks = 3;
		int mods;
		float modAmp;
		boolean alternate;
		boolean twoHanded;
		boolean strikePose;
		FirstPerson firstPerson = FirstPerson.NONE;

		Def wind(float... pairs) {
			set(wind, pairs);
			return this;
		}

		Def strike(float... pairs) {
			set(strike, pairs);
			return this;
		}

		private void set(float[] key, float[] pairs) {
			for (int i = 0; i + 1 < pairs.length; i += 2) {
				int channel = (int) pairs[i];
				key[channel] = pairs[i + 1];
				mask |= 1 << channel;
			}
		}

		Def ticks(int windup, int strikeTicks) {
			this.windTicks = windup;
			this.strikeTicks = strikeTicks;
			return this;
		}

		Def mods(int mods, float amp) {
			this.mods = mods;
			this.modAmp = amp;
			return this;
		}

		Def alt() {
			this.alternate = true;
			return this;
		}

		Def twoHand() {
			this.twoHanded = true;
			return this;
		}

		Def strikes() {
			this.strikePose = true;
			return this;
		}

		Def fp(FirstPerson style) {
			this.firstPerson = style;
			return this;
		}

		public int windTicks() {
			return windTicks;
		}

		public int strikeTicks() {
			return strikeTicks;
		}

		public boolean twoHanded() {
			return twoHanded;
		}

		public boolean alternate() {
			return alternate;
		}

		public boolean strikePose() {
			return strikePose;
		}

		public FirstPerson firstPerson() {
			return firstPerson;
		}

		public boolean drives(int channel) {
			return (mask & 1 << channel) != 0;
		}

		public int mask() {
			return mask;
		}
	}

	private static final Map<Pose, Def> DEFS = new EnumMap<>(Pose.class);

	private static float[] nanKey() {
		float[] key = new float[CHANNELS];
		java.util.Arrays.fill(key, Float.NaN);
		return key;
	}

	private static Def put(Pose pose) {
		Def def = new Def();
		DEFS.put(pose, def);
		return def;
	}

	static {
		put(Pose.SEAL).wind(RAX, -0.40F, RAY, -0.30F, LAX, -0.40F, LAY, 0.30F, HX, 0.05F)
				.strike(RAX, -1.10F, RAY, -0.55F, RAZ, 0.05F, LAX, -1.10F, LAY, 0.55F, LAZ, -0.05F, HX, 0.15F)
				.mods(MOD_TREMBLE, 0.05F).twoHand().fp(FirstPerson.TWIN);
		put(Pose.PALM_THRUST).wind(RAX, -0.90F, TWIST, -0.25F, LAX, -0.30F, RAZ, 0.10F)
				.strike(RAX, -1.55F, RAY, -0.05F, RAZ, 0.0F, TWIST, 0.35F, LAX, -0.20F, LAZ, -0.25F, LEAN, 0.08F)
				.ticks(3, 2).strikes().fp(FirstPerson.THRUST);
		put(Pose.TWIN_PALM).wind(RAX, -0.80F, RAZ, 0.35F, LAX, -0.80F, LAZ, -0.35F, LEAN, -0.05F)
				.strike(RAX, -1.50F, RAZ, 0.12F, RAY, -0.10F, LAX, -1.50F, LAZ, -0.12F, LAY, 0.10F, LEAN, 0.12F, RLX, -0.25F, LLX, 0.10F)
				.ticks(3, 2).strikes().twoHand().fp(FirstPerson.TWIN);
		put(Pose.PUNCH).wind(RAX, -0.60F, RAY, 0.30F, TWIST, -0.40F, LAX, -0.90F, LAY, 0.20F)
				.strike(RAX, -1.60F, RAY, 0.0F, TWIST, 0.45F, LAX, 0.40F, LAY, 0.0F, LEAN, 0.15F, RLX, -0.20F)
				.ticks(2, 2).strikes().alt().fp(FirstPerson.THRUST);
		put(Pose.SWORD_THRUST).wind(RAX, -0.70F, TWIST, -0.30F, LAX, -0.50F, LAZ, -0.20F)
				.strike(RAX, -1.62F, RAY, 0.0F, LAX, -0.40F, LAZ, -0.60F, LEAN, 0.10F, TWIST, 0.20F, RLX, -0.35F)
				.ticks(3, 2).strikes().fp(FirstPerson.THRUST);
		put(Pose.SWORD_SLASH).wind(RAX, -2.70F, RAZ, -0.40F, TWIST, -0.35F, LAX, -0.50F, LAZ, -0.30F, HX, -0.10F)
				.strike(RAX, -0.25F, RAZ, 0.90F, TWIST, 0.50F, LAX, -0.30F, LAZ, -0.45F, LEAN, 0.12F, HX, 0.10F)
				.ticks(4, 3).strikes().fp(FirstPerson.SLAM);
		put(Pose.SWORD_SKY).wind(RAX, -1.60F, LAX, -0.60F, LAY, 0.30F)
				.strike(RAX, -3.05F, RAZ, -0.05F, HX, -0.45F, LAX, -1.10F, LAY, 0.55F)
				.ticks(3, 4).mods(MOD_TREMBLE, 0.03F).fp(FirstPerson.RAISE);
		put(Pose.SKY_RAISE).wind(RAX, -1.50F, LAX, -0.20F)
				.strike(RAX, -2.95F, RAZ, 0.10F, HX, -0.50F, LAX, -0.30F, LAZ, -0.30F)
				.ticks(3, 4).mods(MOD_TREMBLE, 0.025F).fp(FirstPerson.RAISE);
		put(Pose.OVERHEAD_SLAM).wind(RAX, -2.90F, RAZ, 0.15F, LAX, -2.90F, LAZ, -0.15F, LEAN, -0.15F, LIFT, 0.10F, HX, -0.25F)
				.strike(RAX, -0.35F, RAZ, 0.10F, LAX, -0.35F, LAZ, -0.10F, LEAN, 0.40F, LIFT, -0.15F, HX, 0.20F, RLX, -0.30F)
				.ticks(5, 2).strikes().twoHand().fp(FirstPerson.SLAM);
		put(Pose.ARMS_SPREAD).wind(RAZ, 0.60F, LAZ, -0.60F, RAX, -0.20F, LAX, -0.20F)
				.strike(RAZ, 1.25F, LAZ, -1.25F, RAX, -0.35F, LAX, -0.35F, HX, -0.25F, LIFT, 0.05F)
				.ticks(4, 6).mods(MOD_BREATH_Z, 0.06F).twoHand().fp(FirstPerson.SPREAD);
		put(Pose.MEDITATE_FLOAT).wind(RAX, -0.50F, LAX, -0.50F, LIFT, 0.15F)
				.strike(RLX, -1.48F, RLY, -0.52F, RLZ, 0.06F, LLX, -1.56F, LLY, 0.52F, LLZ, -0.06F, RAX, -0.68F, RAY, 0.18F, RAZ, -0.32F,
						LAX, -0.68F, LAY, -0.18F, LAZ, 0.32F, HX, 0.06F, LIFT, 0.40F, TWIST, 0.0F)
				.ticks(6, 8).mods(MOD_FLOAT, 0.08F);
		put(Pose.STOMP).wind(RLX, -1.10F, LIFT, 0.10F, RAZ, 0.60F, LAZ, -0.60F, LEAN, -0.05F)
				.strike(RLX, 0.15F, LIFT, -0.12F, LEAN, 0.20F, RAX, -0.40F, LAX, -0.40F, RAZ, 0.50F, LAZ, -0.50F, HX, 0.15F)
				.ticks(4, 2).strikes().fp(FirstPerson.SPREAD);
		put(Pose.FINGER_POINT).wind(RAX, -0.90F, RAY, 0.10F, HX, -0.05F)
				.strike(RAX, -1.58F, RAY, -0.08F, LAX, -0.20F, LAZ, -0.15F, HX, 0.05F, TWIST, 0.15F)
				.ticks(3, 2).strikes().fp(FirstPerson.THRUST);
		put(Pose.SUMMON_RAISE).wind(RAX, -0.60F, LAX, -0.60F, HX, 0.10F)
				.strike(RAX, -2.85F, RAZ, 0.35F, LAX, -2.85F, LAZ, -0.35F, HX, -0.40F, LIFT, 0.08F)
				.ticks(3, 4).mods(MOD_BREATH_Z, 0.04F).twoHand().fp(FirstPerson.RAISE);
		put(Pose.KNEEL_STRIKE).wind(LIFT, 0.20F, RAX, -2.20F, LAX, -0.60F)
				.strike(LIFT, -0.45F, RLX, -1.40F, LLX, 0.30F, RAX, -0.20F, LAX, -0.70F, LAZ, -0.30F, LEAN, 0.45F, HX, -0.30F)
				.ticks(4, 2).strikes().fp(FirstPerson.SLAM);
		put(Pose.CHARGE_BACK).wind(RAX, 0.30F, LAX, -0.60F, TWIST, -0.20F)
				.strike(RAX, 0.70F, LAX, -1.20F, TWIST, -0.40F, LEAN, -0.05F, RLX, 0.15F, LLX, -0.20F)
				.ticks(3, 4).mods(MOD_CHARGE_SHAKE | MOD_BREATH_X, 0.05F).fp(FirstPerson.CHARGE);
		put(Pose.CHARGE_OVERHEAD).wind(RAX, -1.60F, LAX, -1.60F)
				.strike(RAX, -2.90F, RAZ, 0.25F, RAY, 0.20F, LAX, -2.90F, LAZ, -0.25F, LAY, -0.20F, HX, -0.50F, LIFT, 0.0F)
				.ticks(3, 5).mods(MOD_LIFT_GROW | MOD_CHARGE_SHAKE, 0.15F).twoHand().fp(FirstPerson.RAISE);
		put(Pose.DASH_LEAN).wind(TILT, 0.20F, RAX, 0.40F, LAX, 0.40F)
				.strike(TILT, 0.45F, RAX, 0.90F, LAX, 0.90F, RAZ, 0.15F, LAZ, -0.15F, HX, -0.20F, RLX, 0.50F, LLX, -0.50F)
				.ticks(2, 2);
		put(Pose.ROAR).wind(HX, 0.20F, RAX, -0.30F, LAX, -0.30F, LEAN, 0.10F)
				.strike(HX, -0.55F, HY, 0.0F, RAX, 0.35F, RAZ, 0.50F, LAX, 0.35F, LAZ, -0.50F, LEAN, -0.20F)
				.ticks(4, 3).mods(MOD_SHAKE_HEAD, 0.10F).fp(FirstPerson.SPREAD);
		put(Pose.CHEST_BEAT).wind(RAX, -0.80F, LAX, -0.80F, LEAN, -0.05F)
				.strike(RAX, -1.30F, RAY, -0.60F, LAX, -0.90F, LAY, 0.60F, LEAN, -0.10F, HX, -0.15F)
				.ticks(3, 2).mods(MOD_BEAT, 0.40F).twoHand().fp(FirstPerson.TWIN);
		put(Pose.FAN_SWING).wind(RAX, -2.40F, RAZ, -0.90F, TWIST, -0.30F, LAX, -0.30F)
				.strike(RAX, -1.20F, RAZ, 1.10F, TWIST, 0.55F, LAX, -0.20F, LAZ, -0.40F, LEAN, 0.08F)
				.ticks(4, 3).strikes().fp(FirstPerson.SLAM);
		put(Pose.FLUTE).wind(RAX, -0.80F, LAX, -0.80F)
				.strike(RAX, -1.25F, RAY, -0.75F, LAX, -1.25F, LAY, 0.40F, HX, 0.10F)
				.ticks(4, 4).mods(MOD_TREMBLE | MOD_BREATH_X, 0.03F).twoHand().fp(FirstPerson.TWIN);
		put(Pose.PRAY).wind(RAX, -0.70F, RAY, -0.40F, LAX, -0.70F, LAY, 0.40F)
				.strike(RAX, -1.35F, RAY, -0.70F, LAX, -1.35F, LAY, 0.70F, HX, 0.20F)
				.ticks(4, 5).mods(MOD_BREATH_X, 0.03F).twoHand().fp(FirstPerson.TWIN);
		put(Pose.HOLD_ITEM_UP).wind(RAX, -1.40F, HX, -0.10F)
				.strike(RAX, -2.60F, RAZ, -0.10F, HX, -0.35F, LAX, -0.20F, LAZ, -0.15F)
				.ticks(3, 4).mods(MOD_TREMBLE, 0.02F).fp(FirstPerson.RAISE);
		put(Pose.GATE_FLEX).wind(RAZ, 0.40F, LAZ, -0.40F, LEAN, 0.05F)
				.strike(RAX, -0.30F, RAZ, 0.90F, LAX, -0.30F, LAZ, -0.90F, LEAN, -0.10F, HX, -0.10F)
				.ticks(3, 3).mods(MOD_TREMBLE, 0.04F).twoHand().fp(FirstPerson.SPREAD);
		put(Pose.FLAME_CUP).wind(RAX, -0.60F, LAX, -0.60F, LIFT, -0.20F)
				.strike(RLX, -1.48F, RLY, -0.52F, RLZ, 0.06F, LLX, -1.56F, LLY, 0.52F, LLZ, -0.06F, RAX, -0.90F, RAY, -0.45F,
						LAX, -0.90F, LAY, 0.45F, HX, 0.25F, LIFT, -0.50F)
				.ticks(6, 6).mods(MOD_BREATH_X, 0.02F).twoHand().fp(FirstPerson.TWIN);
	}

	/** The definition of {@code pose}, or null for AUTO, NONE and unknown poses. */
	public static @Nullable Def def(Pose pose) {
		return pose == null ? null : DEFS.get(pose);
	}

	// ================================================================== easing

	public static float easeOutCubic(float t) {
		float u = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
		return 1.0F - u * u * u;
	}

	/** easeOutBack scaled so the overshoot peaks near 1.08. */
	public static float easeOutBack(float t) {
		float x = Mth.clamp(t, 0.0F, 1.0F) - 1.0F;
		float c1 = 1.15F;
		float c3 = c1 + 1.0F;
		return 1.0F + c3 * x * x * x + c1 * x * x;
	}

	public static float easeInOutSine(float t) {
		return -(Mth.cos((float) Math.PI * Mth.clamp(t, 0.0F, 1.0F)) - 1.0F) / 2.0F;
	}

	// ================================================================== sampling

	/**
	 * Writes the key values of {@code def} at {@code t} ticks into its pose (driven channels only; others become NaN).
	 * {@code hold} is the hold length (-1 = endless), {@code age} the entity age for the breathing, {@code mirror} swaps
	 * the arms (left-handed casters, alternating fists).
	 */
	static void keyAt(Def def, float t, float hold, float age, boolean mirror, float[] out) {
		int w = Math.max(1, def.windTicks);
		int s = Math.max(1, def.strikeTicks);
		for (int c = 0; c < CHANNELS; c++) {
			if (!def.drives(c)) {
				out[c] = Float.NaN;
				continue;
			}
			float wind = Float.isNaN(def.wind[c]) ? restOf(c, def) : def.wind[c];
			float strike = Float.isNaN(def.strike[c]) ? wind : def.strike[c];
			float v;
			if (t < w) {
				v = wind;
			} else if (t < w + s) {
				v = wind + (strike - wind) * easeOutBack((t - w) / s);
			} else {
				v = strike;
			}
			out[c] = v;
		}
		float inHold = Math.max(0.0F, t - w - s);
		float holdProgress = hold <= 0.0F ? Math.min(1.0F, inHold / 40.0F) : Mth.clamp(inHold / hold, 0.0F, 1.0F);
		applyMods(def, out, inHold, holdProgress, age);
		if (mirror) {
			mirror(out);
		}
	}

	/** The rest value of a channel the windup leaves out: the strike's value scaled towards neutral. */
	private static float restOf(int channel, Def def) {
		float strike = def.strike[channel];
		return Float.isNaN(strike) ? 0.0F : strike * 0.35F;
	}

	private static void applyMods(Def def, float[] out, float inHold, float holdProgress, float age) {
		if (def.mods == 0 || inHold <= 0.0F) {
			return;
		}
		float ramp = Math.min(1.0F, inHold / 4.0F);
		float a = def.modAmp * ramp;
		if ((def.mods & MOD_BREATH_X) != 0) {
			float b = Mth.sin(age * 0.0785F) * a; // 0.25 Hz
			add(out, RAX, b);
			add(out, LAX, b);
		}
		if ((def.mods & MOD_BREATH_Z) != 0) {
			float b = Mth.sin(age * 0.0785F) * a;
			add(out, RAZ, b);
			add(out, LAZ, -b);
		}
		if ((def.mods & MOD_TREMBLE) != 0) {
			float b = Mth.sin(age * 0.628F) * a; // 2 Hz
			add(out, RAX, b);
			add(out, LAX, -b);
		}
		if ((def.mods & MOD_FLOAT) != 0) {
			add(out, LIFT, Mth.sin(age * 0.1F) * def.modAmp);
			add(out, TWIST, Mth.sin(age * 0.045F) * 0.04F);
		}
		if ((def.mods & MOD_BEAT) != 0) {
			// alternate fists on the chest at 4 Hz
			float beat = Mth.sin(age * 1.256F);
			add(out, RAX, beat * def.modAmp * 0.5F);
			add(out, LAX, -beat * def.modAmp * 0.5F);
		}
		if ((def.mods & MOD_SHAKE_HEAD) != 0 && inHold < 12.0F) {
			add(out, HY, Mth.sin(age * 2.2F) * def.modAmp * (1.0F - inHold / 12.0F));
		}
		if ((def.mods & MOD_LIFT_GROW) != 0) {
			add(out, LIFT, def.modAmp * holdProgress);
		}
		if ((def.mods & MOD_CHARGE_SHAKE) != 0) {
			float shake = Mth.sin(age * 2.5F) * 0.03F * holdProgress;
			add(out, RAX, shake);
			add(out, LAX, -shake);
		}
	}

	private static void add(float[] out, int channel, float value) {
		if (!Float.isNaN(out[channel])) {
			out[channel] += value;
		}
	}

	/** Swaps right and left limbs (and turns, rolls and twist the other way). */
	static void mirror(float[] v) {
		swap(v, RAX, LAX);
		swap(v, RAY, LAY);
		swap(v, RAZ, LAZ);
		swap(v, RLX, LLX);
		swap(v, RLZ, LLZ);
		swap(v, RLY, LLY);
		negate(v, RAY);
		negate(v, LAY);
		negate(v, RAZ);
		negate(v, LAZ);
		negate(v, RLZ);
		negate(v, LLZ);
		negate(v, RLY);
		negate(v, LLY);
		negate(v, TWIST);
		negate(v, HY);
	}

	private static void swap(float[] v, int a, int b) {
		float t = v[a];
		v[a] = v[b];
		v[b] = t;
	}

	private static void negate(float[] v, int c) {
		if (!Float.isNaN(v[c])) {
			v[c] = -v[c];
		}
	}

	/**
	 * Pure sample for giant figures and avatars (contract C3): {@code t01} 0 = rest, 0..0.3 the windup, 0.3..0.5 the
	 * strike, 0.5..1 the hold; the result is at full weight with undriven channels at 0.
	 */
	public static PoseAngles sample(Pose pose, float t01, float ageTicks) {
		Def def = def(pose);
		if (def == null) {
			return PoseAngles.REST;
		}
		float t = Mth.clamp(t01, 0.0F, 1.0F);
		float ticks;
		float weight = 1.0F;
		if (t < 0.3F) {
			weight = easeOutCubic(t / 0.3F);
			ticks = 0.0F;
		} else if (t < 0.5F) {
			ticks = def.windTicks + (t - 0.3F) / 0.2F * def.strikeTicks;
		} else {
			ticks = def.windTicks + def.strikeTicks + (t - 0.5F) / 0.5F * 40.0F;
		}
		float[] v = new float[CHANNELS];
		keyAt(def, ticks, 40.0F, ageTicks, false, v);
		for (int c = 0; c < CHANNELS; c++) {
			v[c] = Float.isNaN(v[c]) ? 0.0F : v[c] * weight;
		}
		return new PoseAngles(v[HX], v[HY], v[LEAN] + v[TILT], v[TWIST], v[RAX], v[RAY], v[RAZ], v[LAX], v[LAY], v[LAZ], v[RLX], v[RLZ],
				v[LLX], v[LLZ], v[LIFT]);
	}

	private PoseLibrary() {
	}
}
