package dev.kultivator.client;

import dev.kultivator.Kultivator;
import dev.kultivator.client.beast.BeastClient;
import dev.kultivator.client.fx.FxDispatcher;
import dev.kultivator.client.render.CultivatorRender;
import dev.kultivator.client.trial.TrialClient;
import dev.kultivator.client.fx.TechniqueFx;
import dev.kultivator.client.screen.CultivationScreen;
import dev.kultivator.network.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public class KultivatorClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		EffectQuality.load();
		Keys.init();
		// UI/UX Terpadu (DESIGN 33.7 module N): the hub's back behaviour, the Akses Cepat wheel (V) and the one-time key move.
		dev.kultivator.client.hub.HubNav.init();
		// Gulungan Utama (DESIGN 43.6): the pages of the screen package, before the modules register theirs (same id replaces).
		CultivationScreen.daftarBawaan();
		dev.kultivator.client.hub.QuickWheel.init();
		dev.kultivator.client.hub.KeyLayout.init();
		PillTooltips.init();
		TrialClient.init();
		BeastClient.init();
		CultivatorRender.init();
		dev.kultivator.client.disciple.DiscipleClient.init();
		// Penampilan dan Animasi (DESIGN 40.7.2, module 40R): Sosok layers, Gerak catalogue, atlas release, /sosok.
		dev.kultivator.client.sosok.SosokClient.init();
		BookTooltips.init();
		// Phase 6 (DESIGN 20.2.12).
		dev.kultivator.client.hall.HallClient.init();
		dev.kultivator.client.sectevent.SectEventClient.init();
		dev.kultivator.client.disciple.power.PowerClient.init();
		dev.kultivator.client.disciple.path.PathClient.init();
		dev.kultivator.client.kehancuran.KehancuranClient.init();
		// Phase 6b (DESIGN 21.24).
		dev.kultivator.client.sectbuild.BuildClient.init();
		// Phase 6c (DESIGN 22.18.1 item 13). The technique registry itself is built by the main entrypoint only.
		// Polish pass (DESIGN 27): the emissive qi particles, the animation and model module, then the technique effects.
		dev.kultivator.client.fx.qi.Qi.init();
		dev.kultivator.client.fx.spec.AnimBridge.init();
		dev.kultivator.client.fx.spec.SpecFx.init();
		dev.kultivator.client.codex.CodexClient.init();
		// Phase 7 (DESIGN 19.2.14); GoalsClient registers its own HUD elements.
		dev.kultivator.client.harta.HartaClient.init();
		dev.kultivator.client.elite.EliteClient.init();
		dev.kultivator.client.talisman.TalismanClient.init();
		dev.kultivator.client.goals.GoalsClient.init();
		// Phase 7b Momen Epik (DESIGN 23.5.1 item 5).
		dev.kultivator.client.epic.EpicClient.init();
		// Phase 10 Dampak Langit (DESIGN 28.14 M2): marks, sky rides, impact frames, the delayed boom, the world FX.
		dev.kultivator.client.dampak.DampakClient.init();
		// Phase 10b Titah Langit (DESIGN 29.9 item 7): the TitahSync cache, the jade-scroll menu, the film, TitahWorld (M3).
		dev.kultivator.client.pusakalangit.TitahClient.init();
		// Voices (module V): plays the spoken lines and draws their captions.
		dev.kultivator.client.voice.VoiceClient.init();
		// Phase 7c Formasi Sekte (DESIGN 24.13 item 5).
		dev.kultivator.client.formasi.FormasiClient.init();
		// Phase 8 Jalan Dunia Kultivasi (DESIGN 25.2 item 18).
		dev.kultivator.client.alam.AlamClient.init();
		dev.kultivator.client.musuh.MusuhClient.init();
		dev.kultivator.client.tempa.TempaClient.init();
		dev.kultivator.client.desa.DesaClient.init();
		// Phase 9 Petualangan Dewa Abadi (DESIGN 26.14 item 18).
		dev.kultivator.client.abadi.AbadiClient.init();
		dev.kultivator.client.purba.PurbaClient.init();
		dev.kultivator.client.batin.BatinClient.init();
		dev.kultivator.client.abadi.AbadiClient.registerRenderers();
		dev.kultivator.client.purba.PurbaClient.registerRenderers();
		dev.kultivator.client.batin.BatinClient.registerRenderers();
		// Aura Kultivasi (DESIGN 31): after SpecFx.init, so its quads are collected after QuadFx's.
		dev.kultivator.client.aura.AuraClient.init();
		// Kitab Panduan: the guide book's sync, key and screen.
		dev.kultivator.client.guide.GuideClient.init();
		// Dunia Kultivasi yang Luas (DESIGN 34): the client side of the spine and the six world-content modules.
		dev.kultivator.client.dunia.DuniaContentClient.init();
		// Duel Pedang Kultivator (DESIGN 35.9 module U): receivers, input, HUD, sounds, then the visual and animation modules.
		dev.kultivator.client.duel.DuelClient.init();
		// Sekte yang Hidup (DESIGN 36): the client side of the ten sect-and-disciple modules.
		dev.kultivator.client.sekte.SekteContentClient.init();
		// Sekte Langit (DESIGN 37): the client side of the four sky-sect modules (37V owns this line).
		dev.kultivator.client.sekte.langit.LangitClient.init();
		// Kota yang Hidup (DESIGN 42): the client side of the eleven living-town modules (42A owns this line).
		dev.kultivator.client.dunia.kota42.KotaHidupClient.init();

		ClientPlayNetworking.registerGlobalReceiver(Payloads.SyncSelf.TYPE, (payload, context) -> ClientCultivation.update(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.Fx.TYPE, (payload, context) -> FxDispatcher.play(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.DiscipleInfo.TYPE, (payload, context) ->
				dev.kultivator.client.disciple.DiscipleClient.onInfo(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.OrePings.TYPE, (payload, context) -> TechniqueFx.onOrePings(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.HallInfo.TYPE, (payload, context) ->
				dev.kultivator.client.hall.HallClient.onInfo(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.BuildPreview.TYPE, (payload, context) ->
				dev.kultivator.client.sectbuild.BuildClient.onPreview(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.TechFx.TYPE, (payload, context) ->
				dev.kultivator.client.fx.spec.SpecFx.play(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.PhantomSync.TYPE, (payload, context) ->
				dev.kultivator.client.fx.spec.PhantomFx.onSync(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.EpicMoment.TYPE, dev.kultivator.client.epic.EpicClient::onPayload);
		ClientPlayNetworking.registerGlobalReceiver(Payloads.FormasiSync.TYPE, (payload, context) ->
				dev.kultivator.client.formasi.FormasiClient.onSync(payload.data()));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.DuniaSync.TYPE, (payload, context) ->
				dev.kultivator.client.dunia.DuniaClient.onSync(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenScreen.TYPE, (payload, context) -> {
			// Gulungan Utama (DESIGN 43.4.4): every server-opened menu is a place of the one screen (old opener until migrated).
			dev.kultivator.client.hub.HubNav.rute(payload.screen(), payload.arg());
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("qi_hud"), new QiHud());
		ClientTickEvents.END_CLIENT_TICK.register(FxDispatcher::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientCultivation.clear();
		});
	}
}
