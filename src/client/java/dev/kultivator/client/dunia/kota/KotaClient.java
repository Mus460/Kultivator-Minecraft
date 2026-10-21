package dev.kultivator.client.dunia.kota;

import dev.kultivator.dunia.kota.KotaEntities;
import dev.kultivator.dunia.kota.KotaNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.nbt.CompoundTag;

/**
 * The client side of module K (DESIGN 34.5.1-34.5.4): the NPC and door renderers, the town screens (Kota, Kota Abadi,
 * the wanderer meeting) fed by {@link KotaNet.Screen}, the world visuals of {@link KotaFx}, and the action sender.
 * Wired by one line in {@code DuniaContentClient}.
 */
public final class KotaClient {

	private static CompoundTag data = new CompoundTag();
	private static int npcId = -1;
	private static int screenKind = -1;
	private static long version;

	public static void init() {
		EntityRenderers.register(KotaEntities.NPC, NpcRenderer::new);
		EntityRenderers.register(KotaEntities.PINTU, PintuRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(KotaNet.Screen.TYPE, (payload, context) -> onScreen(payload));
		ClientPlayNetworking.registerGlobalReceiver(KotaNet.Fx.TYPE, (payload, context) -> KotaFx.onFx(payload));
		KotaFx.init();
		ClientTickEvents.END_CLIENT_TICK.register(KotaFx::tick);
		// the one hub card of module K (DESIGN 34.6 T): Kota Abadi in Keabadian, behind the ascent lock; it opens the help topic
		dev.kultivator.client.hub.HubCards.register(dev.kultivator.client.hub.HubNav.KEABADIAN, "kota_abadi",
				dev.kultivator.client.screen.ui.Glyphs.HALL, KotaClient::ascentLock, () -> dev.kultivator.client.hub.HubNav.help("kota_abadi"));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			data = new CompoundTag();
			npcId = -1;
			screenKind = -1;
			KotaFx.clear();
		});
	}

	private static void onScreen(KotaNet.Screen payload) {
		Minecraft minecraft = Minecraft.getInstance();
		Screen current = minecraft.gui.screen();
		boolean mine = current instanceof KotaScreens.Holder holder && holder.npcId() == payload.npcId();
		if (!payload.open() && !mine) {
			// a live refresh for a screen that is not open (the auction streams to everyone near the stage)
			return;
		}
		data = payload.data() == null ? new CompoundTag() : payload.data();
		npcId = payload.npcId();
		screenKind = payload.screen();
		version++;
		if (payload.open() && !mine) {
			minecraft.gui.setScreen(KotaScreens.create(payload.screen(), payload.tab(), payload.npcId()));
		} else if (payload.open() && current instanceof KotaScreens.Holder holder) {
			holder.showTab(payload.tab());
		}
	}

	/** The 33.3 ascent lock of the Keabadian cards: null once the player ascended to Alam Abadi. */
	private static @org.jetbrains.annotations.Nullable net.minecraft.network.chat.Component ascentLock() {
		if (!dev.kultivator.client.ClientCultivation.received()) {
			return null;
		}
		CompoundTag abadi = dev.kultivator.client.ClientCultivation.data().extra().getCompoundOrEmpty("abadi");
		if (abadi.getBooleanOr("ascended", false)
				|| dev.kultivator.client.abadi.AbadiClient.data().getCompoundOrEmpty("alam").getBooleanOr("ascended", false)) {
			return null;
		}
		return net.minecraft.network.chat.Component.translatable("kultivator.hub.lock.ascend");
	}

	/** The last data of the open screen (never null). */
	public static CompoundTag data() {
		return data;
	}

	/** One tab's part of the data (empty when absent). */
	public static CompoundTag tab(String key) {
		return data.getCompoundOrEmpty(key);
	}

	public static int npcId() {
		return npcId;
	}

	public static int screenKind() {
		return screenKind;
	}

	/** Bumped on every screen data that arrived. */
	public static long version() {
		return version;
	}

	/** Sends a screen action about the open NPC. */
	public static void send(String op) {
		if (npcId >= 0) {
			ClientPlayNetworking.send(new KotaNet.Action(npcId, op));
		}
	}

	private KotaClient() {
	}
}
