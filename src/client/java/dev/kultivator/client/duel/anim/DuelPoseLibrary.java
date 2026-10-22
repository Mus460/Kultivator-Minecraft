package dev.kultivator.client.duel.anim;

import dev.kultivator.client.anim.CastPoses;
import dev.kultivator.client.anim.PoseLibrary;
import dev.kultivator.duel.blade.BladeKinematics;
import dev.kultivator.duel.blade.BladeShape;
import dev.kultivator.duel.blade.DuelPose;
import dev.kultivator.duel.blade.DuelPoseTable;
import dev.kultivator.duel.clash.WeaponClass;
import dev.kultivator.technique.spec.dsl.Pose;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The client half of the duel pose sampler (DESIGN 35.5.1): the keys and timing are the shared {@link DuelPoseTable}
 * (the server tests its blades on the same numbers), this class turns a sample into what {@code PoseApplier} draws.
 * <ul>
 * <li>Easing is {@link DuelPoseTable}'s ({@code easeOutCubic} into the windup, the accelerating whip into the strike,
 * {@code easeOutBack} with the 1.08 overshoot of {@code PoseLibrary} for reactions and guards).</li>
 * <li>Left-handed fighters get the mirrored pose; the wrist roll is folded into the sword arm's Euler angles
 * ({@link BladeKinematics#foldWrist}) so the model arm, the item in it and {@link BladeProbeLayer} carry it, and the
 * blade's edge faces where the cut goes.</li>
 * <li>Two-handed grips (the lock, the charge, the high guard, every pose of a great sword or spear) bring the off hand to
 * the hilt by solving the off arm onto the handle point of the held blade ({@link #aimOffHand}).</li>
 * <li>Better Combat's arm rule (35.5.6): a zero arm weight hands the arms to its animation, the body and head stay ours.</li>
 * </ul>
 * Pure functions plus per-thread scratch; client thread only.
 */
public final class DuelPoseLibrary {

	/** The arm channels a Better Combat swing takes over (35.5.6). */
	private static final int[] ARM_CHANNELS = {PoseLibrary.RAX, PoseLibrary.RAY, PoseLibrary.RAZ, PoseLibrary.LAX, PoseLibrary.LAY, PoseLibrary.LAZ};
	/** The leg channels (the walk cycle owns them while walking, {@code PoseApplier}). */
	private static final int[] LEG_CHANNELS = {PoseLibrary.RLX, PoseLibrary.RLZ, PoseLibrary.LLX, PoseLibrary.LLZ, PoseLibrary.RLY, PoseLibrary.LLY};

	/** The point on the handle where the second hand closes, in item space (below the sword hand, near the pommel). */
	private static final Vector3f HANDLE_ITEM = new Vector3f(-0.36F, -0.36F, 0.0F);
	/** The same for the disciples' 3D sword: model y 4 px, below the guard at 8 px. */
	private static final Vector3f HANDLE_3D = new Vector3f(0.0F, 4.0F / 16.0F - 0.5F, 0.0F);
	/** Model pixels from the arm pivot to the middle of the closed hand. */
	private static final float HAND_REACH = 10.0F;

	private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

	private static final class Scratch {
		final float[] channels = new float[DuelPoseTable.CHANNELS];
		final Matrix4f matrix = new Matrix4f();
		final Vector3f a = new Vector3f();
		final Vector3f b = new Vector3f();
		final float[] aim = new float[2];
	}

	/**
	 * Samples {@code pose} ({@link DuelPoseTable#sample}) into {@code out}: authored right-handed channels, not mirrored,
	 * the wrist not folded. Returns where in the pose {@code t} is (0..3).
	 */
	public static float sample(DuelPose pose, float t, int windup, @Nullable float[] from, DuelPoseTable.Ctx ctx, float[] out) {
		return DuelPoseTable.sample(pose, t, windup, from, ctx, out);
	}

	/** {@code out = a + (b − a) k}, channel by channel; NaN in {@code a} takes {@code b}. */
	public static void blend(float[] a, float[] b, float k, float[] out) {
		for (int i = 0; i < DuelPoseTable.CHANNELS; i++) {
			float x = a[i];
			float y = b[i];
			if (Float.isNaN(x)) {
				x = y;
			}
			out[i] = x + (y - x) * k;
		}
	}

	/**
	 * The final channels of a frame for the model: mirrored for a left-handed fighter, the off hand on the hilt when
	 * {@code offAim > 0} (0..1, eased by the caller), the wrist folded into the sword arm. {@code ch} is not changed.
	 */
	public static float[] modelChannels(float[] ch, boolean left, boolean slim, WeaponClass weapon, boolean sword3d, float offAim) {
		float[] out = new float[DuelPoseTable.CHANNELS];
		System.arraycopy(ch, 0, out, 0, DuelPoseTable.CHANNELS);
		if (left) {
			DuelPoseTable.mirror(out);
		}
		if (offAim > 0.001F) {
			aimOffHand(out, left, slim, weapon, sword3d, Math.min(1.0F, offAim));
		}
		BladeKinematics.foldWrist(out, left);
		return out;
	}

	/**
	 * Builds the render-state snapshot ({@code CastPoses.Snapshot}, kind {@link Pose#DUEL}) of a frame: 18 channels with
	 * their weights. {@code model} comes from {@link #modelChannels}; {@code weight} is the pose's overall weight (fade in
	 * and out), {@code armWeight} the share the arms keep (0 while Better Combat animates them).
	 */
	public static CastPoses.Snapshot snapshot(float[] model, float weight, float armWeight, boolean twoHanded) {
		float[] val = new float[PoseLibrary.CHANNELS];
		float[] w = new float[PoseLibrary.CHANNELS];
		float k = clamp01(weight);
		for (int c = 0; c < PoseLibrary.CHANNELS; c++) {
			float v = model[c];
			val[c] = Float.isFinite(v) ? v : 0.0F;
			w[c] = k;
		}
		float arms = k * clamp01(armWeight);
		for (int c : ARM_CHANNELS) {
			w[c] = arms;
		}
		return new CastPoses.Snapshot(Pose.DUEL, val, w, null, null, 1.0F, twoHanded && arms > 0.05F);
	}

	/**
	 * Merges a technique's cast pose (which wins, 35.5.1) with the duel pose underneath: channels the cast does not drive
	 * keep the duel's legs and body, so a fighter casting during a duel keeps its footwork.
	 */
	public static CastPoses.Snapshot underCast(CastPoses.Snapshot cast, CastPoses.Snapshot duel) {
		float[] val = cast.val().clone();
		float[] w = cast.w().clone();
		for (int c : LEG_CHANNELS) {
			if (w[c] <= 0.0F || Float.isNaN(val[c])) {
				val[c] = duel.val()[c];
				w[c] = duel.w()[c];
			}
		}
		for (int c : new int[] {PoseLibrary.LEAN, PoseLibrary.TWIST, PoseLibrary.LIFT}) {
			if (w[c] <= 0.0F || Float.isNaN(val[c])) {
				val[c] = duel.val()[c] * 0.5F;
				w[c] = duel.w()[c];
			}
		}
		return new CastPoses.Snapshot(cast.pose(), val, w, cast.fromVal(), cast.fromW(), cast.cross(), cast.twoHanded());
	}

	/**
	 * Turns the off arm (in place, {@code ch} already mirrored for {@code left}) towards the handle of the blade in the
	 * sword hand, blended by {@code amount}. Model pixels, y down: the sword hand's handle point is the shoulder pivot
	 * plus the arm rotation (with the wrist) applied to the item chain's handle point.
	 */
	static void aimOffHand(float[] ch, boolean left, boolean slim, WeaponClass weapon, boolean sword3d, float amount) {
		Scratch s = SCRATCH.get();
		float lean = value(ch, DuelPoseTable.LEAN);
		float twist = value(ch, DuelPoseTable.TWIST);
		int mx = left ? DuelPoseTable.LAX : DuelPoseTable.RAX;
		int my = left ? DuelPoseTable.LAY : DuelPoseTable.RAY;
		int mz = left ? DuelPoseTable.LAZ : DuelPoseTable.RAZ;
		int ox = left ? DuelPoseTable.RAX : DuelPoseTable.LAX;
		int oy = left ? DuelPoseTable.RAY : DuelPoseTable.LAY;
		int oz = left ? DuelPoseTable.RAZ : DuelPoseTable.LAZ;
		// the handle point in the sword arm's own frame (blocks), through the same item chain the probe uses
		BladeShape shape = sword3d ? BladeShape.SWORD_3D : BladeShape.of(weapon);
		Matrix4f item = s.matrix.identity();
		BladeKinematics.toItem(shape, left, item);
		Vector3f handle = s.a.set(shape.hold() == BladeShape.Hold.SWORD_3D ? HANDLE_3D : handleOf(shape));
		if (shape.hold() == BladeShape.Hold.ARM) {
			handle.set(left ? 1.0F / 16.0F : -1.0F / 16.0F, 9.0F / 16.0F, 0.0F);
		} else {
			item.transformPosition(handle);
		}
		handle.mul(16.0F);
		// into model pixels: rotationZYX of the arm, then the wrist about the arm's long axis
		Quaternionf rot = new Quaternionf().rotationZYX(value(ch, mz), value(ch, my), value(ch, mx)).rotateY(value(ch, DuelPoseTable.WRIST));
		rot.transform(handle);
		Vector3f mainPivot = BladeKinematics.shoulder(lean, twist, left, slim, s.b);
		handle.add(mainPivot);
		Vector3f offPivot = BladeKinematics.shoulder(lean, twist, !left, slim, new Vector3f());
		// aim the off arm's long axis at the handle; a hand closer than the arm's length bends the elbow in reality, here
		// the arm simply points (the hand lands on the handle line)
		float[] aim = s.aim;
		BladeKinematics.aim(offPivot, handle, aim);
		float dist = offPivot.distance(handle);
		// out of reach (the hilt far to the other side): reach less, so the arm does not swing through the body
		float reach = dist > HAND_REACH * 1.6F ? 0.5F : 1.0F;
		float k = clamp01(amount) * reach;
		ch[ox] = lerpAngle(value(ch, ox), aim[0], k);
		ch[oy] = lerpAngle(value(ch, oy), aim[1], k);
		ch[oz] = value(ch, oz) * (1.0F - k);
	}

	/** Where the off hand closes on a weapon of this shape (item space). */
	private static Vector3f handleOf(BladeShape shape) {
		if (shape == BladeShape.TOMBAK) {
			// spears are held wide: the second hand a hand's breadth further down the shaft
			return new Vector3f(-0.46F, -0.46F, 0.0F);
		}
		return HANDLE_ITEM;
	}

	/** The duel poses whose off hand joins the hilt for this weapon (35.5.2: two hands on great swords and spears). */
	public static boolean wantsOffHand(DuelPose pose, WeaponClass weapon) {
		if (pose == null) {
			return false;
		}
		if (DuelPoseTable.def(pose).offAim()) {
			return true;
		}
		if (weapon == WeaponClass.PEDANG_BERAT || weapon == WeaponClass.TOMBAK || weapon == WeaponClass.KAPAK) {
			DuelPose.Kind kind = pose.kind();
			return kind == DuelPose.Kind.STANCE && pose != DuelPose.TERHUYUNG || kind == DuelPose.Kind.CUT;
		}
		return false;
	}

	/** {@code from} towards {@code to} the short way round. */
	static float lerpAngle(float from, float to, float k) {
		float twoPi = (float) (Math.PI * 2.0D);
		float d = to - from;
		d -= twoPi * Math.round(d / twoPi);
		return from + d * k;
	}

	private static float value(float[] ch, int c) {
		float v = ch[c];
		return Float.isNaN(v) ? 0.0F : v;
	}

	static float clamp01(float v) {
		return v < 0.0F ? 0.0F : v > 1.0F ? 1.0F : v;
	}

	private DuelPoseLibrary() {
	}
}
