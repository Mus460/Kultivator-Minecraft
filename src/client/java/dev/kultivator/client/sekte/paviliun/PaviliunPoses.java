package dev.kultivator.client.sekte.paviliun;

import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.sekte.SektePoses;
import dev.kultivator.sekte.SekteIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * The two poses module A owns (DESIGN 36.3.3, C36.L6):
 * <ul>
 * <li>{@code ACT_STUDY} (13): the reader on its way to its seat or standing a moment before sitting: the open sutra held in
 * both hands at the chest, the head bowed over it, a page turned now and then. Once it sits the body takes the seated
 * meditation pose and the renderer floats the sutra before it instead (props are hidden while seated).</li>
 * <li>{@code ACT_MOURN} (14): kneeling at a grave or before the altar, three sticks of incense held up in joined hands,
 * the head bowed. During a Qingming the kneeler kowtows with the rite: at 10, 25 and 40 s into it the body bends to the
 * ground, the hands flat before the knees, then rises (timed by the rite in {@code PaviliunPayloads.Places}, so every
 * row bows together without the server moving anyone).</li>
 * </ul>
 * Poses only move the body model's parts toward a target by the blend {@code s}; a few trig calls per visible body.
 */
final class PaviliunPoses {

	static void init() {
		SektePoses.register(SekteIds.ACT_STUDY, PaviliunPoses::study, List.of(new SektePoses.Prop("sekte_paviliun_scroll", true, 0.55F, -20.0F)));
		SektePoses.register(SekteIds.ACT_MOURN, PaviliunPoses::mourn, List.of(new SektePoses.Prop("sekte_paviliun_incense", false, 0.5F, 10.0F)));
	}

	/** Reading on its feet: both hands on the sutra, the head over it, a page turned every few seconds. */
	private static void study(PlayerModel model, DiscipleRenderState state, float s, float t) {
		float cycle = (t + state.id * 31.0F) % 140.0F;
		float turn = cycle > 120.0F ? Mth.sin((cycle - 120.0F) / 20.0F * Mth.PI) : 0.0F;
		pose(model.leftArm, s, -1.15F, 0.3F, 0.0F);
		pose(model.rightArm, s, -1.05F - 0.35F * turn, -0.35F + 0.25F * turn, 0.0F);
		model.head.xRot = Mth.lerp(s, model.head.xRot, 0.5F + Mth.sin(t * 0.04F) * 0.04F);
		model.head.yRot = Mth.lerp(s, model.head.yRot, 0.08F * turn);
		model.body.xRot = Mth.lerp(s, model.body.xRot, 0.06F);
	}

	/** Kneeling with incense; the Qingming kowtow when the rite reaches one. */
	private static void mourn(PlayerModel model, DiscipleRenderState state, float s, float t) {
		float now = clientNow();
		float bow = PaviliunWorld.kowtow(state.x, state.z, now);
		kneel(model, s);
		if (bow > 0.0F) {
			// the kowtow: the body bends to the ground, the hands flat before the knees
			model.body.xRot = Mth.lerp(s, model.body.xRot, 0.25F + 1.0F * bow);
			model.head.xRot = Mth.lerp(s, model.head.xRot, 0.5F + 0.5F * bow);
			model.head.y += 7.0F * bow * s;
			model.body.y += 3.5F * bow * s;
			model.rightArm.y += 4.0F * bow * s;
			model.leftArm.y += 4.0F * bow * s;
			model.head.z -= 4.0F * bow * s;
			model.rightArm.z -= 3.0F * bow * s;
			model.leftArm.z -= 3.0F * bow * s;
			pose(model.rightArm, s, -0.6F - 1.0F * bow, -0.15F, 0.0F);
			pose(model.leftArm, s, -0.6F - 1.0F * bow, 0.15F, 0.0F);
			return;
		}
		// the incense held up before the face in joined hands, a slow breath
		float breath = Mth.sin(t * 0.05F + state.id) * 0.03F;
		pose(model.rightArm, s, -1.35F + breath, -0.38F, 0.0F);
		pose(model.leftArm, s, -1.35F + breath, 0.38F, 0.0F);
		model.body.xRot = Mth.lerp(s, model.body.xRot, 0.2F);
		model.head.xRot = Mth.lerp(s, model.head.xRot, 0.45F);
	}

	/** Both knees down: the hips 6 px lower, the thighs swung back so the shins rest on the ground. */
	private static void kneel(PlayerModel model, float s) {
		model.rightLeg.xRot = Mth.lerp(s, model.rightLeg.xRot, 1.25F);
		model.leftLeg.xRot = Mth.lerp(s, model.leftLeg.xRot, 1.25F);
		model.rightLeg.yRot = Mth.lerp(s, model.rightLeg.yRot, 0.05F);
		model.leftLeg.yRot = Mth.lerp(s, model.leftLeg.yRot, -0.05F);
		model.rightLeg.y += 6.0F * s;
		model.leftLeg.y += 6.0F * s;
		model.rightLeg.z += 1.0F * s;
		model.leftLeg.z += 1.0F * s;
		model.body.y += 6.0F * s;
		model.head.y += 6.0F * s;
		model.rightArm.y += 6.0F * s;
		model.leftArm.y += 6.0F * s;
	}

	private static float clientNow() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? 0.0F : minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}

	private static void pose(ModelPart part, float s, float x, float y, float z) {
		part.xRot = Mth.lerp(s, part.xRot, x);
		part.yRot = Mth.lerp(s, part.yRot, y);
		part.zRot = Mth.lerp(s, part.zRot, z);
	}

	private PaviliunPoses() {
	}
}
