package dev.kultivator.client;

import dev.kultivator.Kultivator;
import dev.kultivator.client.fx.FxDispatcher;
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
		PillTooltips.init();

		ClientPlayNetworking.registerGlobalReceiver(Payloads.SyncSelf.TYPE, (payload, context) -> ClientCultivation.update(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.Fx.TYPE, (payload, context) -> FxDispatcher.play(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.OrePings.TYPE, (payload, context) -> TechniqueFx.onOrePings(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenScreen.TYPE, (payload, context) -> {
			if (payload.screen() == Payloads.OpenScreen.INFO) {
				context.client().gui.setScreen(new CultivationScreen(payload.arg()));
			} else if (payload.screen() == Payloads.OpenScreen.CHOOSE_DAO) {
				context.client().gui.setScreen(new CultivationScreen(CultivationScreen.TAB_ROOT_DAO));
			}
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("qi_hud"), new QiHud());
		ClientTickEvents.END_CLIENT_TICK.register(FxDispatcher::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientCultivation.clear());
	}
}
