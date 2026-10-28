package dev.kultivator.client.sekte.kepribadian;

import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.epic.SekteScenes;
import dev.kultivator.client.sekte.SektePoses;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.kepribadian.KepribadianPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Module P on the client (DESIGN 36.2.1, 36.4): the payload receivers, the thought bubbles ({@link GelembungPikiran}),
 * the poses of activities 19 ({@code ACT_SULK}: arms crossed, head turned away, a foot tapping) and 22 ({@code ACT_LEAVING}:
 * a cloth bundle on a stick over the shoulder, looking back once), the mood list of Murid › Daftar › suasana
 * ({@link SuasanaScreen}, DESIGN 43.5) and the scene recipes of beats 100-102 (a rebel's challenge, a rebel who submits, a
 * disciple who left). The Hati sub of Murid › Murid ({@code HatiTab}) is mounted by the Murid page.
 */
public final class KepribadianClient {

	private static boolean done;

	public static void init() {
		if (done) {
			return;
		}
		done = true;
		ClientPlayNetworking.registerGlobalReceiver(KepribadianPayloads.Suasana.TYPE, (payload, context) -> GelembungPikiran.onMoods(payload));
		ClientPlayNetworking.registerGlobalReceiver(KepribadianPayloads.Letup.TYPE, (payload, context) -> GelembungPikiran.onPop(payload));
		ClientPlayNetworking.registerGlobalReceiver(KepribadianPayloads.Daftar.TYPE, (payload, context) -> SuasanaScreen.onList(payload));
		ClientDisconnect.register((handler, client) -> client.execute(() -> SuasanaScreen.bersih()));
		GelembungPikiran.init();
		// 43: the Hati sub is built by the Murid page (M03); the mood list is the sub Murid › Daftar › suasana (M04).
		registerPoses();
		registerScenes();
	}

	private static void registerPoses() {
		SektePoses.register(SekteIds.ACT_SULK, (model, state, s, t) -> {
			// Arms crossed over the chest (each hand toward the other side), head turned away and down, a foot tapping.
			pose(model.rightArm, s, -0.95F, -0.62F, 0.12F);
			pose(model.leftArm, s, -0.78F, 0.62F, -0.12F);
			float away = 0.55F + 0.12F * Mth.sin(t * 0.04F);
			model.head.yRot = Mth.lerp(s, model.head.yRot, away);
			model.head.xRot = Mth.lerp(s, model.head.xRot, 0.22F);
			model.body.yRot = Mth.lerp(s, model.body.yRot, 0.08F);
			float tap = Math.max(0.0F, Mth.sin(t * 0.45F)) * 0.22F;
			model.rightLeg.xRot = Mth.lerp(s, model.rightLeg.xRot, -tap);
		}, List.of());
		SektePoses.register(SekteIds.ACT_LEAVING, (model, state, s, t) -> {
			// The bundle's stick in the right hand over the shoulder, the left arm loose, one long look back at the sect.
			pose(model.rightArm, s, -0.42F, 0.0F, 0.06F);
			pose(model.leftArm, s, 0.12F + 0.05F * Mth.sin(t * 0.2F), 0.0F, -0.08F);
			model.head.yRot = Mth.lerp(s, model.head.yRot, 0.95F);
			model.head.xRot = Mth.lerp(s, model.head.xRot, 0.18F);
			model.body.xRot = Mth.lerp(s, model.body.xRot, 0.1F);
		}, List.of(new SektePoses.Prop("sekte_kepribadian_buntalan", false, 0.85F, 0.0F)));
	}

	private static void pose(ModelPart part, float s, float x, float y, float z) {
		part.xRot = Mth.lerp(s, part.xRot, x);
		part.yRot = Mth.lerp(s, part.yRot, y);
		part.zRot = Mth.lerp(s, part.zRot, z);
	}

	private static void registerScenes() {
		SekteScenes.register(dev.kultivator.sekte.kepribadian.Kepribadian.BEAT_REBEL, new SekteScenes.Recipe(
				"kultivator.sekte.kepribadian.adegan.memberontak", SekteScenes.Arg.SUBJECT_NAME, null, "逆", 0xB3281D, false, 50,
				SekteScenes.Move.LOW_HERO, 3.0F, null, 0, null, -1, 80));
		SekteScenes.register(dev.kultivator.sekte.kepribadian.Kepribadian.BEAT_SUBMIT, SekteScenes.Recipe.stamp(
				"kultivator.sekte.kepribadian.adegan.tunduk", SekteScenes.Arg.SUBJECT_NAME, "服", 0x2E8963));
		SekteScenes.register(dev.kultivator.sekte.kepribadian.Kepribadian.BEAT_LEFT, new SekteScenes.Recipe(
				"kultivator.sekte.kepribadian.adegan.pergi", SekteScenes.Arg.NONE, null, "去", 0x465950, false, 50,
				SekteScenes.Move.PULLBACK, 3.5F, null, 0, null, -1, 90));
	}

	private KepribadianClient() {
	}
}
