package dev.kultivator.client.sekte.hubungan;

import dev.kultivator.client.disciple.DiscipleScreen;
import dev.kultivator.client.epic.SekteScenes;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.client.hub.HubCards;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.sekte.SektePoses;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.hubungan.HubunganPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * The client of module R, Hubungan antar-murid (DESIGN 36.2.2, 36.4 R), one line in {@code SekteContentClient}:
 * <ul>
 * <li>the payload receivers (bubbles, the lovers near the player, the Jaring data);</li>
 * <li>the "Hubungan" tab of the disciple scroll (after P's "Hati", {@code DiscipleScreen.addExtraTab});</li>
 * <li>the hub card "Jaring Hubungan" in the SEKTE section;</li>
 * <li>the pose of {@code SekteIds.ACT_SOCIAL}: a celadon tea cup held before the chest in the right hand, the left hand
 * beneath it as courtesy asks, and every eight seconds a sip (the cup tilts toward the lips);</li>
 * <li>the scenes of beats 110 (sworn kin), 111 (Dao companions) and 112 (a grudge settled), through {@code SekteScenes}
 * with all the 7b guards (Penuh/Singkat/Mati, Backspace, never in danger);</li>
 * <li>the bubble and mote renderer ({@link GelembungIkatan}).</li>
 * </ul>
 */
public final class HubunganClient {

	private static final String K = "kultivator.sekte.hubungan.";
	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(HubunganPayloads.Gelembung.TYPE, (payload, context) -> GelembungIkatan.onBubble(payload));
		ClientPlayNetworking.registerGlobalReceiver(HubunganPayloads.Pasangan.TYPE, (payload, context) -> GelembungIkatan.onPairs(payload));
		ClientPlayNetworking.registerGlobalReceiver(HubunganPayloads.Jaring.TYPE, (payload, context) -> JaringScreen.receive(payload.data()));
		ClientTickEvents.END_CLIENT_TICK.register(GelembungIkatan::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(GelembungIkatan::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GelembungIkatan.clear());

		DiscipleScreen.addExtraTab(Glyphs.RING, HubunganTab::new);
		HubCards.register(HubNav.SEKTE, "sekte_hubungan_jaring", Glyphs.RING, null, () -> {
			HubNav.openChild(new JaringScreen(), HubNav.SEKTE);
			JaringScreen.ask();
		});

		SektePoses.register(SekteIds.ACT_SOCIAL, HubunganClient::teaPose, List.of(new SektePoses.Prop("sekte_hubungan_teacup", false, 0.55F, 66.0F)));

		int first = EpicIds.EXTRA_SEKTE_FIRST + 10;
		SekteScenes.register(first, new SekteScenes.Recipe(K + "adegan.sumpah", SekteScenes.Arg.NONE, K + "adegan.sumpah.sub", "义", 0xD9A030,
				true, 70, SekteScenes.Move.ORBIT, 5.0F, MusicCue.Cue.M_GLORY, 10, SkyDirector.Grade.SK_GOLD, -1, 140));
		SekteScenes.register(first + 1, new SekteScenes.Recipe(K + "adegan.dao", SekteScenes.Arg.NONE, K + "adegan.dao.sub", "侣", 0xE0407E,
				true, 70, SekteScenes.Move.CRANE_UP, 5.0F, MusicCue.Cue.M_GENTLE, 10, SkyDirector.Grade.SK_DAWN, 0xFFC8DC, 140));
		SekteScenes.register(first + 2, SekteScenes.Recipe.stamp(K + "adegan.reda", SekteScenes.Arg.SUBJECT_NAME, "和", 0xD9A030));
	}

	/**
	 * Tea: the cup (right hand) before the chest, the left palm under it; a sip every 8 s (offset per body, so two
	 * drinkers do not sip in step), the head tipping back a little while drinking.
	 */
	private static void teaPose(net.minecraft.client.model.player.PlayerModel model, dev.kultivator.client.disciple.DiscipleRenderState state,
			float s, float t) {
		float cycle = (t + state.id * 37) % 160.0F;
		float sip = cycle < 118.0F ? 0.0F : cycle < 128.0F ? (cycle - 118.0F) / 10.0F : cycle < 142.0F ? 1.0F : Math.max(0.0F, 1.0F - (cycle - 142.0F) / 10.0F);
		sip = sip * sip * (3.0F - 2.0F * sip);
		float talk = Mth.sin(t * 0.09F + state.id) * 0.04F;
		pose(model.rightArm, s, -1.15F - 0.45F * sip + talk, -0.42F + 0.12F * sip, 0.0F);
		pose(model.leftArm, s, -0.92F - 0.22F * sip, 0.52F, 0.0F);
		model.head.xRot = Mth.lerp(s, model.head.xRot, 0.12F - 0.32F * sip);
		model.body.xRot = Mth.lerp(s, model.body.xRot, 0.04F);
	}

	private static void pose(ModelPart part, float s, float x, float y, float z) {
		part.xRot = Mth.lerp(s, part.xRot, x);
		part.yRot = Mth.lerp(s, part.yRot, y);
		part.zRot = Mth.lerp(s, part.zRot, z);
	}

	private HubunganClient() {
	}
}
