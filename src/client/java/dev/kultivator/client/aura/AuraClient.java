package dev.kultivator.client.aura;

import dev.kultivator.Kultivator;
import dev.kultivator.aura.AuraPreviewPayload;
import dev.kultivator.client.aura.high.AuraHigh;
import dev.kultivator.client.aura.realm.FoundationAura;
import dev.kultivator.client.aura.realm.GoldenCoreAura;
import dev.kultivator.client.aura.realm.MortalAura;
import dev.kultivator.client.aura.realm.NascentSoulAura;
import dev.kultivator.client.aura.realm.QiRefiningAura;
import dev.kultivator.data.Realm;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Entry of the Aura Kultivasi on the client (DESIGN 31): {@link #init()} (one line in {@code KultivatorClient}, after
 * {@code SpecFx.init}, so the aura's quads are collected after {@code QuadFx}'s) registers the preview receiver, the
 * render hook, the first-person edge glow and the realm drivers (Mortal .. Nascent Soul here, then
 * {@code AuraHigh.init()} for Deity Transformation .. True Immortal); {@link #tick} runs from {@code AuraFx.tick} every
 * unpaused client tick; {@link #clear()} on a world change or a disconnect.
 */
public final class AuraClient {

	private static boolean ready;
	private static ClientLevel lastLevel;

	public static void init() {
		if (ready) {
			return;
		}
		ready = true;
		ClientPlayNetworking.registerGlobalReceiver(AuraPreviewPayload.TYPE, (payload, context) -> AuraPreviewClient.onPayload(payload));
		LevelRenderEvents.COLLECT_SUBMITS.register(AuraEngine::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("aura_edge"), new AuraHud());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> clear()));
		AuraEngine.register(Realm.MORTAL, new MortalAura());
		AuraEngine.register(Realm.QI_REFINING, new QiRefiningAura());
		AuraEngine.register(Realm.FOUNDATION, new FoundationAura());
		AuraEngine.register(Realm.GOLDEN_CORE, new GoldenCoreAura());
		AuraEngine.register(Realm.NASCENT_SOUL, new NascentSoulAura());
		AuraHigh.init();
	}

	/** Every unpaused client tick (from {@code AuraFx.tick}). */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			lastLevel = level;
			clear();
		}
		if (level == null || minecraft.player == null) {
			return;
		}
		AuraPreviewClient.tick();
		AuraEngine.tick(minecraft);
	}

	/** Drops every aura, preview and hum (world change, disconnect). */
	public static void clear() {
		AuraEngine.clear();
		AuraPreviewClient.clear();
		AuraSettings.reload();
	}

	private AuraClient() {
	}
}
