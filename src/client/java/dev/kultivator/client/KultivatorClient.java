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
		BookTooltips.init();

		ClientPlayNetworking.registerGlobalReceiver(Payloads.SyncSelf.TYPE, (payload, context) -> ClientCultivation.update(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.Fx.TYPE, (payload, context) -> FxDispatcher.play(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.OrePings.TYPE, (payload, context) -> TechniqueFx.onOrePings(payload));
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
