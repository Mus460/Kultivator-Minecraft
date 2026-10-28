package dev.kultivator.client.sekte.turnamen;

import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.epic.SekteScenes;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.sekte.turnamen.Turnamen;
import dev.kultivator.sekte.turnamen.TurnamenEntities;
import dev.kultivator.sekte.turnamen.TurnamenPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Module T on the client (DESIGN 36.2.3, 43.5): the Lingkar Lei Tai ({@link LeiTaiRenderer}) and its HUD scroll
 * ({@link LeiTaiHud}), the visiting spectators' renderer and head pieces ({@link PenontonRenderer},
 * {@link PenontonModels}), the season board shown on Sekte › Kegiatan › Arena › Turnamen ({@link #data()}, asked with
 * {@link #ask}; the old "Turnamen" card and screen are gone, G40) and the three scene recipes of beats 120-122 (through
 * {@code SekteScenes}, every 7b camera guard applies). Reads only its own payloads and the shared entity type. One line in
 * {@code SekteContentClient}.
 */
public final class TurnamenClient {

	private static boolean initialised;
	private static @Nullable CompoundTag data;

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
		ClientPlayNetworking.registerGlobalReceiver(TurnamenPayloads.Papan.TYPE, (payload, context) -> data = payload.data() == null ? null
				: payload.data().copy());
		ClientDisconnect.register((handler, client) -> client.execute(() -> {
			LeiTaiRenderer.clear();
			data = null;
		}));
		scenes();
	}

	/** The last season board from the server (Sekte › Kegiatan › Arena › Turnamen), or null before the first answer. */
	public static @Nullable CompoundTag data() {
		return data;
	}

	/** False while the module is off on the server (no answer will ever come). */
	public static boolean aktif() {
		return ClientPlayNetworking.canSend(TurnamenPayloads.Minta.TYPE);
	}

	/** Asks for the board ({@code Minta.ASK}) or answers the waiting inter-sect challenge ({@code ACCEPT} / {@code REFUSE}). */
	public static void ask(int action) {
		if (aktif()) {
			ClientPlayNetworking.send(new TurnamenPayloads.Minta(action));
		}
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
