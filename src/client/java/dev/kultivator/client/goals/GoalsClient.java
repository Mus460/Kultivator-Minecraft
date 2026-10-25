package dev.kultivator.client.goals;

import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.QiHud;
import dev.kultivator.goals.Guide;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/**
 * Client side of P7-jalan (DESIGN 19.3.9, 19.3.10, 19.3.15): registers the guide tracker (right edge), the tribulation
 * HUD (left edge) and the meditation danger alert as HUD elements, and ticks them, the formation blueprints and the HUD
 * comfort counters. Called once from {@code KultivatorClient}.
 */
public final class GoalsClient {

	public static void init() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("guide_tracker"), new GuideTracker());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("tribulation_hud"), new TribulationHud());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("meditation_alert"), new MeditationAlert());
		ClientTickEvents.END_CLIENT_TICK.register(GoalsClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		GuideTracker.tick(minecraft);
		MeditationAlert.tick(minecraft);
		FormationBlueprint.tick(minecraft);
		QiHud.clientTick(minecraft);
		// "seen:codex" is sent by Teknik › Kodeks itself (KodeksPage.onShow, DESIGN 43)
	}

	private static void reset() {
		GuideTracker.reset();
		MeditationAlert.reset();
		FormationBlueprint.reset();
	}

	/** {@code sessionExtra.cfg} (DESIGN 19.2.3): the server switches the screens must know. */
	public static CompoundTag cfgTag() {
		return ClientCultivation.data().sessionExtra().getCompoundOrEmpty("cfg");
	}

	/** One server switch of {@code sessionExtra.cfg}: hd, dp, guide, daily, tele. */
	public static boolean cfg(String key, boolean fallback) {
		return cfgTag().getBooleanOr(key, fallback);
	}

	private GoalsClient() {
	}
}
