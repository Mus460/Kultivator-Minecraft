package dev.kultivator.client.sekte.kebun;

import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.sekte.SektePoses;
import dev.kultivator.sekte.SekteIds;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * The two poses module W owns (DESIGN 36.3.3, C36.L6), registered with the lead's {@code SektePoses}:
 * <ul>
 * <li>{@code ACT_MINE} (15): a miner at the rock face with the black-iron pick ({@code sekte_kebun_pick}) in both hands:
 * the pick is raised over the right shoulder, falls in a fast stroke with the back bending into it, rests a beat on the
 * stone, and is lifted again (a 1.5 s stroke); every fourth stroke the miner straightens, rolls the shoulders and wipes
 * the brow (each miner offset by its id, so a shift never swings in step).</li>
 * <li>{@code ACT_TEND} (16): a gardener at the spirit beds with the watering gourd ({@code sekte_kebun_gourd}) in the right
 * hand: stooped, pouring in a slow sweep along the bed (the gourd tips with the forearm), then crouched to part the leaves
 * with the free hand and look at them closely (a 10 s cycle).</li>
 * </ul>
 * Poses only move the body model's parts toward a target by the blend {@code s}; the lead's DutyPoses does the blending
 * and stops it while walking, fighting or casting, so nothing here costs more than a few trig calls per visible body.
 */
final class KebunPoses {

	static void init() {
		SektePoses.register(SekteIds.ACT_MINE, KebunPoses::mine, List.of(new SektePoses.Prop("sekte_kebun_pick", false, 0.75F, 90.0F)));
		SektePoses.register(SekteIds.ACT_TEND, KebunPoses::tend, List.of(new SektePoses.Prop("sekte_kebun_gourd", false, 0.55F, -25.0F)));
	}

	// ================================================================== ACT_MINE

	private static void mine(PlayerModel model, DiscipleRenderState state, float s, float t) {
		float time = t + state.id * 13.0F;
		int stroke = (int) (time / 30.0F);
		float p = (time % 30.0F) / 30.0F;
		if (Math.floorMod(stroke + state.id, 4) == 3) {
			breather(model, s, p);
			return;
		}
		// 0-0.45 raise, 0.45-0.58 the stroke, 0.58-0.75 resting on the stone, 0.75-1 lifting toward the raise
		float lift;
		if (p < 0.45F) {
			lift = ease(p / 0.45F);
		} else if (p < 0.58F) {
			lift = 1.0F - ease((p - 0.45F) / 0.13F);
		} else if (p < 0.75F) {
			lift = 0.0F;
		} else {
			lift = ease((p - 0.75F) / 0.25F) * 0.35F;
		}
		if (p < 0.45F) {
			lift = 0.35F + 0.65F * lift;
		}
		float impact = p >= 0.56F && p < 0.62F ? 1.0F - Math.abs(p - 0.59F) / 0.03F : 0.0F;
		// both hands on the haft: high over the right shoulder (raised) .. low in front (struck)
		float armX = Mth.lerp(lift, -0.55F, -2.75F) + impact * 0.06F;
		pose(model.rightArm, s, armX, -0.28F + 0.18F * lift, 0.12F);
		pose(model.leftArm, s, armX + 0.15F, 0.55F - 0.2F * lift, -0.05F);
		float bend = Mth.lerp(lift, 0.42F, -0.08F);
		model.body.xRot = Mth.lerp(s, model.body.xRot, bend);
		model.body.yRot = Mth.lerp(s, model.body.yRot, -0.12F + 0.1F * lift);
		model.head.xRot = Mth.lerp(s, model.head.xRot, Mth.lerp(lift, 0.55F, 0.1F));
		lean(model, s * Math.max(0.0F, bend) * 1.8F);
		// a wide stance
		pose(model.rightLeg, s, 0.12F, 0.0F, 0.08F);
		pose(model.leftLeg, s, -0.18F, 0.0F, -0.08F);
	}

	/** Every fourth stroke: straighten, the pick lowered in the left hand, roll the shoulders, wipe the brow. */
	private static void breather(PlayerModel model, float s, float p) {
		float wipe = Mth.clamp(Mth.sin(Mth.clamp((p - 0.3F) / 0.5F, 0.0F, 1.0F) * Mth.PI), 0.0F, 1.0F);
		pose(model.leftArm, s, -0.25F, 0.1F, -0.1F);
		pose(model.rightArm, s, Mth.lerp(wipe, -0.3F, -2.2F), Mth.lerp(wipe, 0.0F, 0.65F), 0.0F);
		model.head.xRot = Mth.lerp(s, model.head.xRot, -0.2F * wipe);
		model.head.yRot = Mth.lerp(s, model.head.yRot, 0.25F * Mth.sin(p * Mth.TWO_PI));
		model.body.xRot = Mth.lerp(s, model.body.xRot, -0.06F);
	}

	// ================================================================== ACT_TEND

	private static void tend(PlayerModel model, DiscipleRenderState state, float s, float t) {
		float time = t + state.id * 41.0F;
		float cycle = (time % 200.0F) / 200.0F;
		if (cycle < 0.6F) {
			// pouring: stooped, the gourd sweeping slowly along the bed and tipping with the forearm
			float sweep = Mth.sin(time * 0.045F);
			float tip = 0.5F + 0.5F * Mth.sin(time * 0.09F);
			pose(model.rightArm, s, -1.05F - 0.2F * tip, -0.2F + 0.35F * sweep, 0.0F);
			pose(model.leftArm, s, -0.35F, 0.25F, -0.12F);
			model.body.xRot = Mth.lerp(s, model.body.xRot, 0.38F);
			model.body.yRot = Mth.lerp(s, model.body.yRot, 0.12F * sweep);
			model.head.xRot = Mth.lerp(s, model.head.xRot, 0.65F);
			model.head.yRot = Mth.lerp(s, model.head.yRot, 0.2F * sweep);
			lean(model, s * 0.7F);
			return;
		}
		// crouched: the free hand parts the leaves, the head bends close, a slow nod
		float k = Mth.clamp((cycle - 0.6F) / 0.08F, 0.0F, 1.0F) * Mth.clamp((1.0F - cycle) / 0.08F, 0.0F, 1.0F);
		float sk = s * k;
		float part = Mth.sin(time * 0.16F) * 0.12F;
		pose(model.leftArm, s, Mth.lerp(k, -0.35F, -1.25F + part), Mth.lerp(k, 0.25F, -0.15F), 0.0F);
		pose(model.rightArm, s, Mth.lerp(k, -1.05F, -0.55F), 0.1F, 0.12F);
		model.body.xRot = Mth.lerp(s, model.body.xRot, Mth.lerp(k, 0.38F, 0.5F));
		model.head.xRot = Mth.lerp(s, model.head.xRot, Mth.lerp(k, 0.65F, 0.8F + Mth.sin(time * 0.05F) * 0.06F));
		model.head.zRot = Mth.lerp(s, model.head.zRot, 0.15F * k);
		lean(model, s * Mth.lerp(k, 0.7F, 1.0F));
		// the knees bend (the body sinks with them)
		float sink = 3.0F * sk;
		pose(model.rightLeg, sk, -1.05F, 0.12F, 0.0F);
		pose(model.leftLeg, sk, -0.25F, -0.1F, 0.0F);
		model.rightLeg.z -= 2.0F * sk;
		model.body.y += sink;
		model.head.y += sink;
		model.rightArm.y += sink;
		model.leftArm.y += sink;
		model.rightLeg.y += sink * 0.6F;
		model.leftLeg.y += sink * 0.6F;
	}

	// ================================================================== helpers

	private static float ease(float x) {
		float c = Mth.clamp(x, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}

	/** A bending body takes the head and shoulders forward and down with it (like the duty poses' bend). */
	private static void lean(PlayerModel model, float k) {
		model.head.y += 3.0F * k;
		model.body.y += 2.0F * k;
		model.rightArm.y += 2.0F * k;
		model.leftArm.y += 2.0F * k;
		model.rightLeg.z += 2.6F * k;
		model.leftLeg.z += 2.6F * k;
	}

	private static void pose(ModelPart part, float s, float x, float y, float z) {
		part.xRot = Mth.lerp(s, part.xRot, x);
		part.yRot = Mth.lerp(s, part.yRot, y);
		part.zRot = Mth.lerp(s, part.zRot, z);
	}

	private KebunPoses() {
	}
}
