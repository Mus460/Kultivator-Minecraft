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

	/** The server asked for the Dao choice while another screen was open: it opens once that screen is closed. */
	private static boolean pendingDaoChoice;

	@Override
	public void onInitializeClient() {
		EffectQuality.load();
		Keys.init();
		PillTooltips.init();
		TrialClient.init();
		BeastClient.init();
		CultivatorRender.init();
		dev.kultivator.client.disciple.DiscipleClient.init();
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
		dev.kultivator.client.fx.spec.SpecFx.init();
		dev.kultivator.client.codex.CodexClient.init();
		// Phase 7 (DESIGN 19.2.14); GoalsClient registers its own HUD elements.
		dev.kultivator.client.harta.HartaClient.init();
		dev.kultivator.client.elite.EliteClient.init();
		dev.kultivator.client.talisman.TalismanClient.init();
		dev.kultivator.client.goals.GoalsClient.init();
		// Phase 7b Momen Epik (DESIGN 23.5.1 item 5).
		dev.kultivator.client.epic.EpicClient.init();
		// Phase 7c Formasi Sekte (DESIGN 24.13 item 5).
		dev.kultivator.client.formasi.FormasiClient.init();

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
		ClientPlayNetworking.registerGlobalReceiver(Payloads.EpicMoment.TYPE, dev.kultivator.client.epic.EpicClient::onPayload);
		ClientPlayNetworking.registerGlobalReceiver(Payloads.FormasiSync.TYPE, (payload, context) ->
				dev.kultivator.client.formasi.FormasiClient.onSync(payload.data()));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenScreen.TYPE, (payload, context) -> {
			if (payload.screen() == Payloads.OpenScreen.INFO) {
				context.client().gui.setScreen(new CultivationScreen(payload.arg()));
			} else if (payload.screen() == Payloads.OpenScreen.CHOOSE_DAO) {
				// Never cover a screen the player is using (chat, inventory...): wait until it is closed.
				if (context.client().gui.screen() == null) {
					context.client().gui.setScreen(new CultivationScreen(CultivationScreen.TAB_ROOT_DAO));
				} else {
					pendingDaoChoice = true;
				}
			} else if (payload.screen() == Payloads.OpenScreen.DISCIPLE) {
				dev.kultivator.client.disciple.DiscipleClient.openScreen(payload.arg());
			} else if (payload.screen() == Payloads.OpenScreen.HALL) {
				dev.kultivator.client.hall.HallClient.openScreen(payload.arg());
			} else if (payload.screen() == Payloads.OpenScreen.KEHANCURAN_CONFIRM) {
				dev.kultivator.client.kehancuran.KehancuranClient.openConfirm(payload.arg());
			} else if (payload.screen() == Payloads.OpenScreen.SECT_TEMPLATE) {
				dev.kultivator.client.sectbuild.BuildClient.openScreen(payload.arg());
			} else if (payload.screen() == Payloads.OpenScreen.CODEX) {
				dev.kultivator.client.codex.Codex.open(payload.arg());
			}
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("qi_hud"), new QiHud());
		ClientTickEvents.END_CLIENT_TICK.register(FxDispatcher::tick);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (pendingDaoChoice && minecraft.player != null && minecraft.gui.screen() == null) {
				pendingDaoChoice = false;
				minecraft.gui.setScreen(new CultivationScreen(CultivationScreen.TAB_ROOT_DAO));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			pendingDaoChoice = false;
			ClientCultivation.clear();
		});
	}
}
