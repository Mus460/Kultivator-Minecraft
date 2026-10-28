package dev.kultivator.client.dunia.peta;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.Keys;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.dunia.peta.PetaNet;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Set;

/**
 * The world map's client (DESIGN 34.5.16, 43.16.2 M12), started by {@code KapalClient.init()}: the {@code peta} key (N by
 * default, 43.10.3; {@code Keys} consumes it and opens Dunia › Peta, or closes it when shown), the region and sheet
 * receivers, and the Gulungan pages of the module: Dunia › Peta ({@link PetaScreen}: world map + the capital's sheet) and
 * Dunia › Perjalanan (the sheet {@code jurnal}, mounted with all its tabs: Aktif, Selesai, Alur).
 */
public final class PetaClient {

	public static final KeyMapping PETA = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.kultivator.peta", InputConstants.Type.KEYBOARD, InputConstants.KEY_N, Keys.DUNIA.getCategory()));

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(PetaNet.Region.TYPE, (payload, context) -> PetaCache.onRegion(payload));
		ClientPlayNetworking.registerGlobalReceiver(PetaNet.Info.TYPE, (payload, context) -> onSheet(payload));
		ClientDisconnect.register((handler, client) -> {
			PetaCache.clear();
			// DISCONNECT can fire on the netty thread; GL textures must be freed on the render thread
			client.execute(() -> {
				PetaPainter.release();
				PetaPainter.reloadSources();
			});
		});
		// the map page asks the server for a fresh sheet itself when it shows
		HubPages.daftar(HubNav.DUNIA, Urutan.ID_PETA, Urutan.PETA, PetaScreen::new);
		// the town map from the gate guard lands on Dunia › Peta › Kota
		HubPages.daftarRumah(LembarKunci.PetaKota.LAYAR, Set.of(), HubNav.DUNIA, Urutan.ID_PETA, PetaScreen.SUB_KOTA, null);
		HubPages.daftarLembar(HubNav.DUNIA, Urutan.ID_PERJALANAN, Urutan.PERJALANAN, LembarKunci.Jurnal.LAYAR, Set.of());
	}

	/** Opens Dunia › Peta on the world map (the page asks the server for a fresh sheet). */
	public static void requestOpen() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		if (!ClientPlayNetworking.canSend(PetaNet.Request.TYPE)) {
			minecraft.player.sendOverlayMessage(Component.translatable("kultivator.peta.no_server"));
			return;
		}
		HubNav.buka(Tujuan.of(HubNav.DUNIA, Urutan.ID_PETA).sub(PetaScreen.SUB_DUNIA));
	}

	/** A sheet arrived; {@code open} (the map item, {@code /kultivasi peta}) opens the map page. */
	private static void onSheet(PetaNet.Info payload) {
		PetaCache.onSheet(payload.info());
		if (payload.open()) {
			requestOpen();
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
