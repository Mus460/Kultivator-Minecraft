package dev.kultivator.client.sekte.misi;

import dev.kultivator.client.disciple.DiscipleRenderState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;

/**
 * The line-up pose of activity {@code ACT_MUSTER} (DESIGN 36.3.3, module X; module T may reuse the activity for its
 * entrants): a disciple back from a mission stands straight at the gate, the travel bundle on its stick over the left
 * shoulder, the chin up; every few seconds the right fist meets the left palm before the chest (the cupped-fist salute,
 * baoquan), offset per disciple so a line never salutes in lockstep. Blended in and out by {@code DutyPoses} (only while
 * the disciple stands still and calm).
 */
final class MisiPose {

	/** One salute cycle in ticks and the share of it the fist is up. */
	private static final float CYCLE = 120.0F;
	private static final float SALUTE = 30.0F;

	static void apply(PlayerModel model, DiscipleRenderState state, float s, float t) {
		// Each disciple salutes on its own beat.
		float phase = (t + (state.id & 63) * 7.0F) % CYCLE;
		float up = phase < SALUTE ? Mth.sin(phase / SALUTE * Mth.PI) : 0.0F;
		float ease = up * up * (3.0F - 2.0F * up);
		float breath = Mth.sin(t * 0.08F) * 0.02F;
		// the bundle's stick over the left shoulder
		pose(model.leftArm, s, -2.55F + breath, 0.15F, 0.35F);
		// the right arm hangs straight, then rises for the salute (fist before the chest, a little across)
		pose(model.rightArm, s, Mth.lerp(ease, 0.04F, -1.35F), Mth.lerp(ease, 0.0F, -0.55F), Mth.lerp(ease, 0.05F, 0.12F));
		model.body.xRot = Mth.lerp(s, model.body.xRot, -0.03F + ease * 0.08F);
		model.head.xRot = Mth.lerp(s, model.head.xRot, -0.12F + ease * 0.22F);
		model.head.yRot = Mth.lerp(s, model.head.yRot, 0.0F);
		// heels together
		pose(model.rightLeg, s, 0.0F, 0.0F, 0.02F);
		pose(model.leftLeg, s, 0.0F, 0.0F, -0.02F);
	}

	private static void pose(ModelPart part, float s, float x, float y, float z) {
		part.xRot = Mth.lerp(s, part.xRot, x);
		part.yRot = Mth.lerp(s, part.yRot, y);
		part.zRot = Mth.lerp(s, part.zRot, z);
	}

	private MisiPose() {
	}
}
