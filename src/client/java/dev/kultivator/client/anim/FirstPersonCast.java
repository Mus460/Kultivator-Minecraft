package dev.kultivator.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.render.MeditationPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.Nullable;

/**
 * The cast pose seen from one's own eyes (DESIGN 27.2.5): the first-person arm (and the item in it) moves on its pose
 * stack, right after the vanilla {@code pushPose} of {@code FirstPersonHandsAndItemsRenderer.submitArmWithItem}:
 * thrusts push the hand forward and up, seals and twin palms bring both hands to the centre of the view, raises lift the
 * hand up out of view and bring it back, a slam drops it, a spread opens the arms, a charge pulls back and trembles more
 * the longer it charges. The left arm mirrors the right. Everything fades with the meditation seat and is off while
 * scoping (vanilla skips the arm then). A sword-duel pose (DESIGN 35.5.5) is handed to {@code FirstPersonDuel}.
 */
public final class FirstPersonCast {

	/** The evaluated pose of the local player for this frame, cached by frame (both hands read it). */
	private static float cachedAge = Float.NaN;
	private static @Nullable CastPoses.Snapshot cached;

	public static @Nullable CastPoses.Snapshot localPose(float partialTicks) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		float age = player.tickCount + partialTicks;
		if (Float.compare(age, cachedAge) != 0) {
			cachedAge = age;
			cached = CastPoses.snapshot(player, age);
			if (cached == null || cached.strength() <= 0.5F) {
				// DESIGN 35.5.5: the sword duel's pose when no technique pose dominates
				CastPoses.Snapshot duel = dev.kultivator.client.duel.anim.DuelPoses.snapshot(player, age);
				if (duel != null) {
					cached = duel;
				}
			}
		}
		return cached;
	}

	/** Moves the arm about to be drawn ({@code arm} is the screen side: RIGHT for the right hand). */
	public static void apply(PoseStack poseStack, HumanoidArm arm, float partialTicks) {
		CastPoses.Snapshot pose = localPose(partialTicks);
		if (pose == null) {
			return;
		}
		if (pose.pose() == dev.kultivator.technique.spec.dsl.Pose.DUEL) {
			dev.kultivator.client.duel.anim.FirstPersonDuel.apply(poseStack, arm, partialTicks, pose);
			return;
		}
		PoseLibrary.Def def = PoseLibrary.def(pose.pose());
		if (def == null) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		float seat = player == null ? 0.0F : MeditationPose.strength(player, partialTicks);
		float w = pose.strength() * (1.0F - seat);
		if (w <= 0.01F) {
			return;
		}
		float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		// how far into the strike the main arm is: 0 at rest, 1 at the strike key (from the right-arm pitch channel)
		float raise = armRaise(pose, arm);
		float age = player == null ? 0.0F : player.tickCount + partialTicks;
		switch (def.firstPerson()) {
			case THRUST -> {
				poseStack.translate(side * 0.02F * w, 0.10F * raise * w, -0.35F * raise * w);
				poseStack.rotateDegrees(Axis.XP, -20.0F * raise * w);
			}
			case TWIN -> {
				poseStack.translate(-side * 0.25F * w, 0.15F * w, -0.20F * w);
				poseStack.rotateDegrees(Axis.YP, side * 25.0F * w);
				poseStack.rotateDegrees(Axis.ZP, side * Mth.sin(age * 0.628F) * 1.0F * w);
			}
			case RAISE -> {
				poseStack.translate(0.0F, 0.55F * raise * w, -0.10F * raise * w);
				poseStack.rotateDegrees(Axis.XP, 55.0F * raise * w);
			}
			case SLAM -> {
				// up with the windup, then a fast drop and forward with the strike
				float up = windupAmount(pose, arm);
				float down = raise;
				poseStack.translate(0.0F, (0.55F * up - 0.15F * down) * w, (-0.10F * up - 0.30F * down) * w);
				poseStack.rotateDegrees(Axis.XP, (55.0F * up - 25.0F * down) * w);
			}
			case SPREAD -> {
				poseStack.translate(side * 0.35F * w, 0.05F * w, 0.0F);
				poseStack.rotateDegrees(Axis.ZP, -side * 30.0F * w);
			}
			case CHARGE -> {
				float shake = Mth.sin(age * 2.5F) * 0.02F * Math.min(1.0F, w);
				poseStack.translate(side * 0.10F * w + shake, -0.10F * w + shake * 0.5F, 0.25F * w);
			}
			default -> {
			}
		}
	}

	/**
	 * 0..1+: how close the arm's pitch is to the strike pitch (overshoot passes 1). For two-handed and mirrored poses the
	 * screen arm reads its own channel.
	 */
	private static float armRaise(CastPoses.Snapshot pose, HumanoidArm arm) {
		int c = arm == HumanoidArm.RIGHT ? PoseLibrary.RAX : PoseLibrary.LAX;
		float v = pose.val()[c];
		if (Float.isNaN(v)) {
			int other = c == PoseLibrary.RAX ? PoseLibrary.LAX : PoseLibrary.RAX;
			v = pose.val()[other];
			if (Float.isNaN(v)) {
				return 1.0F;
			}
		}
		PoseLibrary.Def def = PoseLibrary.def(pose.pose());
		float strike = def == null ? -1.5F : strikeOf(def, c);
		if (Math.abs(strike) < 0.05F) {
			return 1.0F;
		}
		return Mth.clamp(v / strike, 0.0F, 1.2F);
	}

	/** 0..1: how far into the windup (the raised anticipation of a slam) the arm is. */
	private static float windupAmount(CastPoses.Snapshot pose, HumanoidArm arm) {
		int c = arm == HumanoidArm.RIGHT ? PoseLibrary.RAX : PoseLibrary.LAX;
		float v = pose.val()[c];
		if (Float.isNaN(v)) {
			return 0.0F;
		}
		// the slam winds up above the head (about -2.9) and strikes low (about -0.3)
		return Mth.clamp((-v - 0.3F) / 2.6F, 0.0F, 1.0F);
	}

	private static float strikeOf(PoseLibrary.Def def, int channel) {
		float[] key = new float[PoseLibrary.CHANNELS];
		PoseLibrary.keyAt(def, def.windTicks() + def.strikeTicks() + 1.0F, 4.0F, 0.0F, false, key);
		float v = key[channel];
		return Float.isNaN(v) ? -1.5F : v;
	}

	private FirstPersonCast() {
	}
}
