package dev.kultivator.client.sekte.turnamen;

import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.SekteScenes;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.client.hub.HubCards;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.sekte.turnamen.Turnamen;
import dev.kultivator.sekte.turnamen.TurnamenEntities;
import dev.kultivator.sekte.turnamen.TurnamenPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.renderer.entity.EntityRenderers;

/**
 * Module T on the client (DESIGN 36.2.3): the Lingkar Lei Tai ({@link LeiTaiRenderer}) and its HUD scroll
 * ({@link LeiTaiHud}), the visiting spectators' renderer and head pieces ({@link PenontonRenderer},
 * {@link PenontonModels}), the hub card "Turnamen" in the Sekte section ({@link TurnamenScreen}) and the three scene
 * recipes of beats 120-122 (through {@code SekteScenes}, every 7b camera guard applies). Reads only its own payloads and
 * the shared entity type. One line in {@code SekteContentClient}.
 */
public final class TurnamenClient {

	private static boolean initialised;

	public static void init() {
		if (initialised || TurnamenEntities.PENONTON == null) {
			// The server side of the module is off (no entity type, no payloads): nothing to draw.
			return;
		}
		initialised = true;
		PenontonModels.register();
		EntityRenderers.register(TurnamenEntities.PENONTON, PenontonRenderer::new);
		LeiTaiRenderer.init();
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("sekte_turnamen_lingkar"), new LeiTaiHud());
		ClientPlayNetworking.registerGlobalReceiver(TurnamenPayloads.Lingkar.TYPE, (payload, context) -> LeiTaiRenderer.onLingkar(payload));
		ClientPlayNetworking.registerGlobalReceiver(TurnamenPayloads.Papan.TYPE, (payload, context) -> TurnamenScreen.onData(payload.data()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LeiTaiRenderer.clear());
		HubCards.register(HubNav.SEKTE, "sekte_turnamen_papan", Glyphs.SEAL, null, () -> HubNav.openChild(new TurnamenScreen(), HubNav.SEKTE));
		scenes();
	}

	private static void scenes() {
		// 120 "Babak Final": a stamp only, the Sword Duel's intro film holds the camera.
		SekteScenes.register(Turnamen.BEAT_FINAL, new SekteScenes.Recipe("kultivator.sekte.turnamen.adegan.final", SekteScenes.Arg.NUMBER_A, null,
				"決", 0xF2C14E, false, 50, SekteScenes.Move.NONE, 3.0F, null, 0, null, -1, 70));
		// 121 "Juara": the champion on the stage, the camera rising, gold sky and the triumph music.
		SekteScenes.register(Turnamen.BEAT_JUARA, new SekteScenes.Recipe("kultivator.sekte.turnamen.adegan.juara", SekteScenes.Arg.SUBJECT_NAME,
				"kultivator.sekte.turnamen.adegan.juara.sub", "冠", 0xF2C14E, true, 90, SekteScenes.Move.CRANE_UP, 5.0F, MusicCue.Cue.M_TRIUMPH,
				14, SkyDirector.Grade.SK_GOLD, 0xFFE9A0, 150));
		// 122 a visitor took the title: a quiet stamp, no camera.
		SekteScenes.register(Turnamen.BEAT_TAMU, new SekteScenes.Recipe("kultivator.sekte.turnamen.adegan.tamu", SekteScenes.Arg.NUMBER_A, null,
				"客", 0x5ED6A0, false, 50, SekteScenes.Move.NONE, 3.0F, null, 0, null, -1, 70));
	}

	private TurnamenClient() {
	}
}
