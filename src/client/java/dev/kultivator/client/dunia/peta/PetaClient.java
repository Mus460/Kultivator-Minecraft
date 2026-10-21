package dev.kultivator.client.dunia.peta;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.Keys;
import dev.kultivator.client.hub.HubCards;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.dunia.peta.PetaNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The world map's client (DESIGN 34.5.16), started by {@code KapalClient.init()}: the {@code peta} key (unbound by
 * default, the 33.4 rule: the hub card "Peta Dunia" in Gulungan › Dunia reaches it), the region and sheet receivers,
 * and the hub card. The screen opens at once from the cache and asks the server for a fresh sheet.
 */
public final class PetaClient {

	public static final KeyMapping PETA = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.kultivator.peta", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), Keys.DUNIA.getCategory()));

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(PetaNet.Region.TYPE, (payload, context) -> PetaCache.onRegion(payload));
		ClientPlayNetworking.registerGlobalReceiver(PetaNet.Info.TYPE, (payload, context) -> onSheet(payload));
		ClientTickEvents.END_CLIENT_TICK.register(PetaClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			PetaCache.clear();
			PetaPainter.release();
			PetaPainter.reloadSources();
		});
		// the screen asks the server for a fresh sheet itself when it opens
		HubCards.register(HubNav.DUNIA, "peta_dunia", Glyphs.PATH, null, () -> HubNav.openChild(new PetaScreen(), HubNav.DUNIA));
	}

	private static void tick(Minecraft minecraft) {
		while (PETA.consumeClick()) {
			if (minecraft.gui.screen() == null && minecraft.player != null) {
				requestOpen();
			}
		}
	}

	/** Opens the map now (from the cache) and asks the server for a fresh sheet. */
	public static void requestOpen() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		if (!ClientPlayNetworking.canSend(PetaNet.Request.TYPE)) {
			minecraft.player.sendOverlayMessage(Component.translatable("kultivator.peta.no_server"));
			return;
		}
		if (!(minecraft.gui.screen() instanceof PetaScreen)) {
			minecraft.gui.setScreen(new PetaScreen());
		}
	}

	private static void onSheet(PetaNet.Info payload) {
		PetaCache.onSheet(payload.info());
		Minecraft minecraft = Minecraft.getInstance();
		if (payload.open() && !(minecraft.gui.screen() instanceof PetaScreen) && minecraft.player != null) {
			minecraft.gui.setScreen(new PetaScreen());
		}
	}

	static void request(int op, int x, int z, String text, long[] keys) {
		if (ClientPlayNetworking.canSend(PetaNet.Request.TYPE)) {
			ClientPlayNetworking.send(new PetaNet.Request(op, x, z, text == null ? "" : text, keys == null ? new long[0] : keys));
		}
	}

	private PetaClient() {
	}
}
