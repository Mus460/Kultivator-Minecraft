package dev.kultivator.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.technique.spec.dsl.Pose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import static dev.kultivator.client.anim.PoseLibrary.HX;
import static dev.kultivator.client.anim.PoseLibrary.HY;
import static dev.kultivator.client.anim.PoseLibrary.LAX;
import static dev.kultivator.client.anim.PoseLibrary.LAY;
import static dev.kultivator.client.anim.PoseLibrary.LAZ;
import static dev.kultivator.client.anim.PoseLibrary.LEAN;
import static dev.kultivator.client.anim.PoseLibrary.LIFT;
import static dev.kultivator.client.anim.PoseLibrary.LLX;
import static dev.kultivator.client.anim.PoseLibrary.LLY;
import static dev.kultivator.client.anim.PoseLibrary.LLZ;
import static dev.kultivator.client.anim.PoseLibrary.RAX;
import static dev.kultivator.client.anim.PoseLibrary.RAY;
import static dev.kultivator.client.anim.PoseLibrary.RAZ;
import static dev.kultivator.client.anim.PoseLibrary.RLX;
import static dev.kultivator.client.anim.PoseLibrary.RLY;
import static dev.kultivator.client.anim.PoseLibrary.RLZ;
import static dev.kultivator.client.anim.PoseLibrary.TILT;
import static dev.kultivator.client.anim.PoseLibrary.TWIST;

/**
 * Puts the cast pose of a render state onto a {@link PlayerModel} after the vanilla animation and the meditation seat
 * (the PlayerModel mixin's TAIL; disciples run it again after their duty pose). Arms and legs blend from the vanilla
 * angles to the pose by the channel weights, the head adds its offsets to the look, and a lean or twist turns the body
 * about the hips and moves the head and shoulder pivots with it (the arms are root children, so this is done by hand
 * like vanilla's attack swing). Legs are left to the walk while walking; nothing plays while swimming, gliding, riding or
 * asleep except the dash lean; a meditation seat wins over every pose but the floating and seated ones.
 * <p>
 * {@link #lift} and {@link #tilt} move the whole model (the renderer's setupRotations, see the AvatarRenderer mixin).
 * Forms that replace the body (seventy-two transformations, the golden ape) hide the skin parts here.
 */
public final class PoseApplier {

	/** The vanilla shoulder and neck pivots (model pixels). */
	private static final float SHOULDER_X = 5.0F;
	private static final float SHOULDER_Y = 2.0F;
	private static final float HIP_Y = 12.0F;

	public static void apply(PlayerModel model, AvatarRenderState state) {
		ClientForms.View form = PoseRenderData.form(state);
		if (!form.id().isEmpty()) {
			FormHide hide = FormHide.of(form.id());
			if (hide != FormHide.NONE) {
				hide(model, hide);
			}
		}
		CastPoses.Snapshot pose = PoseRenderData.pose(state);
		if (pose == null) {
			return;
		}
		Pose kind = pose.pose();
		boolean seated = kind == Pose.MEDITATE_FLOAT || kind == Pose.FLAME_CUP;
		if (MeditationPose.strength(state) > 0.5F && !seated) {
			return;
		}
		boolean busy = state.swimAmount > 0.1F || state.isFallFlying || state.isPassenger
				|| state.hasPose(net.minecraft.world.entity.Pose.SLEEPING);
		if (busy && kind != Pose.DASH_LEAN) {
			return;
		}
		boolean walking = state.walkAnimationSpeed > 0.1F && !seated;

		// arms (absolute)
		boolean rx = drives(pose, RAX), ry = drives(pose, RAY), rz = drives(pose, RAZ);
		boolean lx = drives(pose, LAX), ly = drives(pose, LAY), lz = drives(pose, LAZ);
		model.rightArm.xRot = mix(pose, RAX, model.rightArm.xRot, false);
		model.rightArm.yRot = mix(pose, RAY, model.rightArm.yRot, false);
		model.rightArm.zRot = mix(pose, RAZ, model.rightArm.zRot, false);
		model.leftArm.xRot = mix(pose, LAX, model.leftArm.xRot, false);
		model.leftArm.yRot = mix(pose, LAY, model.leftArm.yRot, false);
		model.leftArm.zRot = mix(pose, LAZ, model.leftArm.zRot, false);

		// legs (absolute), left to the walk cycle while walking
		if (!walking) {
			model.rightLeg.xRot = mix(pose, RLX, model.rightLeg.xRot, false);
			model.rightLeg.yRot = mix(pose, RLY, model.rightLeg.yRot, false);
			model.rightLeg.zRot = mix(pose, RLZ, model.rightLeg.zRot, false);
			model.leftLeg.xRot = mix(pose, LLX, model.leftLeg.xRot, false);
			model.leftLeg.yRot = mix(pose, LLY, model.leftLeg.yRot, false);
			model.leftLeg.zRot = mix(pose, LLZ, model.leftLeg.zRot, false);
			if (seated) {
				// the right shin lies over the left one, like the meditation seat
				model.rightLeg.y -= 0.5F * Math.max(weight(pose, RLX), 0.0F);
			}
		}

		// head offsets on top of the look, plus a little follow-through towards a twisting strike
		float twistNow = pose.body(TWIST);
		model.head.xRot = mix(pose, HX, model.head.xRot, true);
		model.head.yRot = mix(pose, HY, model.head.yRot, true) + twistNow * 0.5F;

		// body lean and twist about the hips (not while crouching: vanilla's crouch already bends it)
		if (!state.isCrouching && (drives(pose, LEAN) || drives(pose, TWIST))) {
			float lean = mix(pose, LEAN, model.body.xRot, false);
			float twist = mix(pose, TWIST, model.body.yRot, false);
			if (walking) {
				lean *= 0.5F;
			}
			bendBody(model, lean, twist, !rx, !ry, !lx, !ly);
		}
	}

	/**
	 * Turns the body by {@code lean} (forward, about the hips) and {@code twist} and moves the head and shoulder pivots
	 * with it; arms the pose does not drive follow the torso.
	 */
	private static void bendBody(PlayerModel model, float lean, float twist, boolean rightXFollows, boolean rightYFollows,
			boolean leftXFollows, boolean leftYFollows) {
		float leanDelta = lean - model.body.xRot;
		float twistDelta = twist - model.body.yRot;
		float sa = Mth.sin(lean), ca = Mth.cos(lean);
		float st = Mth.sin(twist), ct = Mth.cos(twist);
		// neck = hip + Ry(twist) Rx(lean) (0, -12, 0)
		float nx = -HIP_Y * sa * st;
		float ny = HIP_Y - HIP_Y * ca;
		float nz = -HIP_Y * sa * ct;
		model.body.xRot = lean;
		model.body.yRot = twist;
		model.body.x = nx;
		model.body.y = ny;
		model.body.z = nz;
		model.head.x = nx;
		model.head.y = ny;
		model.head.z = nz;
		// shoulders: neck + Ry Rx (±5, 2, 0)
		for (int side = -1; side <= 1; side += 2) {
			float s = side * SHOULDER_X;
			float px = nx + s * ct + SHOULDER_Y * sa * st;
			float py = ny + SHOULDER_Y * ca;
			float pz = nz - s * st + SHOULDER_Y * sa * ct;
			ModelPart arm = side < 0 ? model.rightArm : model.leftArm;
			arm.x = px;
			arm.y = py;
			arm.z = pz;
		}
		if (rightXFollows) {
			model.rightArm.xRot += leanDelta;
		}
		if (leftXFollows) {
			model.leftArm.xRot += leanDelta;
		}
		if (rightYFollows) {
			model.rightArm.yRot += twistDelta;
		}
		if (leftYFollows) {
			model.leftArm.yRot += twistDelta;
		}
	}

	private static boolean drives(CastPoses.Snapshot pose, int c) {
		return pose.w()[c] > 0.0F && !Float.isNaN(pose.val()[c])
				|| pose.fromW() != null && pose.fromVal() != null && pose.fromW()[c] > 0.0F && !Float.isNaN(pose.fromVal()[c]);
	}

	private static float weight(CastPoses.Snapshot pose, int c) {
		return Float.isNaN(pose.val()[c]) ? 0.0F : pose.w()[c];
	}

	/** The blended value of one channel over the vanilla value (additive channels add their offset). */
	private static float mix(CastPoses.Snapshot pose, int c, float vanilla, boolean additive) {
		float now = blend(pose.val()[c], pose.w()[c], vanilla, additive);
		float[] fromVal = pose.fromVal();
		float[] fromW = pose.fromW();
		if (fromVal == null || fromW == null || pose.cross() >= 1.0F) {
			return now;
		}
		float before = blend(fromVal[c], fromW[c], vanilla, additive);
		return Mth.lerp(pose.cross(), before, now);
	}

	private static float blend(float value, float weight, float vanilla, boolean additive) {
		if (Float.isNaN(value) || weight <= 0.0F) {
			return vanilla;
		}
		return additive ? vanilla + value * weight : Mth.lerp(weight, vanilla, value);
	}

	// ================================================================== whole-model moves

	/** At the start of setupRotations (world frame, feet at the origin): the pose's lift. */
	public static void lift(AvatarRenderState state, PoseStack poseStack) {
		ClientForms.View form = PoseRenderData.form(state);
		if (form.has("earth_sink")) {
			// Worldwalking: down into the ground over 6 ticks, back up over the last 6
			float in = Mth.clamp(ClientForms.effectAge(state.id, "earth_sink") / 6.0F, 0.0F, 1.0F);
			float out = Mth.clamp(ClientForms.effectLeft(state.id, "earth_sink") / 6.0F, 0.0F, 1.0F);
			float sink = Math.min(in, out);
			poseStack.translate(0.0F, -1.8F * sink * sink * (3.0F - 2.0F * sink), 0.0F);
		}
		CastPoses.Snapshot pose = PoseRenderData.pose(state);
		if (pose == null || MeditationPose.strength(state) > 0.5F && pose.pose() != Pose.MEDITATE_FLOAT && pose.pose() != Pose.FLAME_CUP) {
			return;
		}
		float lift = pose.body(LIFT);
		if (lift != 0.0F && Float.isFinite(lift)) {
			poseStack.translate(0.0F, Mth.clamp(lift, -0.8F, dev.kultivator.client.sosok.gerak.GerakApplier.LIFT_MAKS), 0.0F);
		}
	}

	/** At the end of setupRotations (the entity's facing frame, feet at the origin): the whole-body dash tilt. */
	public static void tilt(AvatarRenderState state, PoseStack poseStack) {
		CastPoses.Snapshot pose = PoseRenderData.pose(state);
		if (pose == null) {
			return;
		}
		float tilt = pose.body(TILT);
		if (tilt != 0.0F && Float.isFinite(tilt)) {
			// the model faces -z here: a forward lean turns about -x
			poseStack.rotate(Axis.XP, -Mth.clamp(tilt, -1.0F, 1.0F));
		}
	}

	/** True when the render state carries a two-handed pose (first person draws the empty off hand too). */
	public static boolean twoHanded(@Nullable CastPoses.Snapshot pose) {
		return pose != null && pose.twoHanded() && pose.strength() > 0.05F;
	}

	// ================================================================== forms that replace the body

	/** Which skin parts a form hides. */
	enum FormHide {
		NONE, UPPER, ALL;

		static FormHide of(String form) {
			if (form.startsWith("seventy_two:")) {
				return ALL;
			}
			return "golden_ape".equals(form) ? UPPER : NONE;
		}
	}

	private static void hide(PlayerModel model, FormHide hide) {
		model.head.visible = false;
		model.body.visible = false;
		model.rightArm.visible = false;
		model.leftArm.visible = false;
		if (hide == FormHide.ALL) {
			model.rightLeg.visible = false;
			model.leftLeg.visible = false;
		}
	}

	private PoseApplier() {
	}
}
