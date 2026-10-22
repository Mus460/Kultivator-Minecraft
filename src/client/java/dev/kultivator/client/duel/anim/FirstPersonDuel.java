package dev.kultivator.client.duel.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.anim.CastPoses;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.duel.blade.BladeKinematics;
import dev.kultivator.duel.blade.BladeShape;
import dev.kultivator.duel.blade.DuelPose;
import dev.kultivator.duel.blade.DuelPoseTable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The duel seen from one's own eyes (DESIGN 35.5.5): a compressed copy of the third-person choreography on the
 * first-person arm. The blade the third-person pose holds right now (shared FK, {@link BladeKinematics}) is compared with
 * the blade of the middle guard: the hand moves on screen as the third-person hand moves against the body (the windup
 * pulls it back over the shoulder, the strike sweeps it across the view in the cut's direction, a parry crosses it in front
 * of the face, the lock holds it across the middle and trembles), and the item turns about the hand by the change of the
 * blade's angle on screen, so the sprite's edge leads the cut. Two-handed grips bring the empty off hand to the hilt.
 * <p>
 * Called from {@code FirstPersonCast.apply} (the vanilla {@code pushPose} of {@code submitArmWithItem}) when the local
 * player's pose is a duel pose; everything scales with the pose weight and fades with the meditation seat. Off while
 * Better Combat animates the arms (it has its own first-person swings).
 */
public final class FirstPersonDuel {

	/** The vanilla first-person hand position ({@code applyItemArmTransform}, right arm; the left mirrors x). */
	private static final float REST_X = 0.56F;
	private static final float REST_Y = -0.52F;
	private static final float REST_Z = -0.72F;
	/** How much of the third-person hand travel shows on screen, per axis, and the limits that keep it in view. */
	private static final float KX = 0.62F;
	private static final float KY = 0.48F;
	private static final float KZ = 0.40F;

	private static final float[] REF_GRIP = new float[3];
	private static final float[] REF_DIR = new float[3];
	private static boolean refReady;

	private static float frameAge = Float.NaN;
	private static final float[] FRAME = new float[5];
	private static boolean frameValid;

	/**
	 * Moves the first-person arm about to be drawn ({@code arm} is the screen side) for the duel snapshot {@code pose}.
	 * Returns true when it moved it.
	 */
	public static boolean apply(PoseStack poseStack, HumanoidArm arm, float partialTicks, @Nullable CastPoses.Snapshot pose) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || pose == null || DuelPoses.armsHandedOver(player.getId())) {
			return false;
		}
		float seat = MeditationPose.strength(player, partialTicks);
		float w = Mth.clamp(DuelPoses.weightOf(player.getId()), 0.0F, 1.0F) * (1.0F - seat);
		if (w <= 0.01F || !frame(player, partialTicks)) {
			return false;
		}
		boolean leftMain = player.getMainArm() == HumanoidArm.LEFT;
		boolean main = arm == player.getMainArm();
		float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		float mirror = leftMain ? -1.0F : 1.0F;
		// the hand travel in right-handed screen space (towards the centre is −x), mirrored for a left-handed player
		float dxr = Mth.clamp(FRAME[0] * KX, -0.80F, 0.32F);
		float dy = FRAME[1];
		float dz = FRAME[2];
		float roll = FRAME[3] * mirror;
		float pitch = FRAME[4];
		DuelPose shown = DuelPoses.poseOf(player.getId());
		DuelPoseTable.FirstPerson style = shown == null ? DuelPoseTable.FirstPerson.GUARD : DuelPoseTable.def(shown).firstPerson();
		if (style == DuelPoseTable.FirstPerson.RECOIL) {
			// a knock is felt more than seen: a little extra kick back and down
			dz += 0.06F;
			dy -= 0.04F;
		}
		float hx = side * REST_X;
		if (main) {
			float tx = dxr * mirror * w;
			float ty = Mth.clamp(dy * KY, -0.38F, 0.46F) * w;
			float tz = Mth.clamp(dz * KZ, -0.32F, 0.26F) * w;
			poseStack.translate(hx + tx, REST_Y + ty, REST_Z + tz);
			poseStack.rotate(Axis.ZP, roll * w);
			poseStack.rotate(Axis.XP, pitch * w);
			poseStack.translate(-hx, -REST_Y, -REST_Z);
			return true;
		}
		if (!pose.twoHanded()) {
			return false;
		}
		// the off hand closes on the hilt below the sword hand (the sword hand rests on the other side of the screen)
		float mainX = -side * REST_X + dxr * mirror;
		float targetX = mainX + side * 0.14F;
		float tx = (targetX - hx) * w;
		float ty = (Mth.clamp(dy * KY, -0.38F, 0.46F) - 0.10F) * w;
		float tz = (Mth.clamp(dz * KZ, -0.32F, 0.26F) + 0.06F) * w;
		poseStack.translate(hx + tx, REST_Y + ty, REST_Z + tz);
		poseStack.rotate(Axis.ZP, roll * 0.6F * w);
		poseStack.translate(-hx, -REST_Y, -REST_Z);
		return true;
	}

	/**
	 * The blade of the local player's duel pose this frame against the middle guard's: hand offset (facing frame, blocks,
	 * right-handed) and the screen roll and pitch of the blade, cached per frame for both hands.
	 */
	private static boolean frame(LocalPlayer player, float partialTicks) {
		float age = player.tickCount + partialTicks;
		if (Float.compare(age, frameAge) == 0) {
			return frameValid;
		}
		frameAge = age;
		float[] ch = DuelPoses.authored(player.getId());
		if (ch == null) {
			frameValid = false;
			return false;
		}
		reference();
		Vector3f grip = new Vector3f();
		Vector3f tip = new Vector3f();
		blade(ch, grip, tip);
		Vector3f dir = new Vector3f(tip).sub(grip);
		if (dir.lengthSquared() < 1.0E-8F) {
			frameValid = false;
			return false;
		}
		dir.normalize();
		FRAME[0] = grip.x - REF_GRIP[0];
		FRAME[1] = grip.y - REF_GRIP[1];
		FRAME[2] = grip.z - REF_GRIP[2];
		// the blade's angle on the screen plane (x right, y up) and how far it leans into the view
		float theta = (float) Math.atan2(dir.y, dir.x);
		float thetaRef = (float) Math.atan2(REF_DIR[1], REF_DIR[0]);
		FRAME[3] = Mth.clamp(Mth.wrapDegrees((theta - thetaRef) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD, -2.6F, 2.6F);
		float phi = (float) Math.asin(Mth.clamp(-dir.z, -1.0F, 1.0F));
		float phiRef = (float) Math.asin(Mth.clamp(-REF_DIR[2], -1.0F, 1.0F));
		FRAME[4] = Mth.clamp(-(phi - phiRef) * 0.6F, -1.0F, 1.0F);
		frameValid = true;
		return true;
	}

	private static void reference() {
		if (refReady) {
			return;
		}
		float[] guard = new float[DuelPoseTable.CHANNELS];
		DuelPoseTable.key(DuelPose.KUDA_TENGAH, DuelPoseTable.PH_STRIKE, guard);
		Vector3f grip = new Vector3f();
		Vector3f tip = new Vector3f();
		blade(guard, grip, tip);
		Vector3f dir = new Vector3f(tip).sub(grip).normalize();
		REF_GRIP[0] = grip.x;
		REF_GRIP[1] = grip.y;
		REF_GRIP[2] = grip.z;
		REF_DIR[0] = dir.x;
		REF_DIR[1] = dir.y;
		REF_DIR[2] = dir.z;
		refReady = true;
	}

	/** A sword's blade for authored channels, in the facing frame (feet at the origin, +y up, front −z). */
	private static void blade(float[] ch, Vector3f grip, Vector3f tip) {
		Matrix4f m = BladeKinematics.hand(ch, false, false, new Matrix4f());
		BladeKinematics.toItem(BladeShape.PEDANG, false, m);
		BladeKinematics.blade(BladeShape.PEDANG, false, m, grip, tip);
	}

	private FirstPersonDuel() {
	}
}
