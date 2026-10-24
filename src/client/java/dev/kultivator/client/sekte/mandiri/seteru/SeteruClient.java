package dev.kultivator.client.sekte.mandiri.seteru;

import dev.kultivator.Kultivator;
import dev.kultivator.client.hub.HubCards;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.seteru.SeteruIds;
import dev.kultivator.sekte.mandiri.seteru.SeteruPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Perseteruan antar-sekte on the client (DESIGN 39.F.9): the entry of module 39F's client package, called from
 * {@code MandiriClient.init}. Registers the receivers of {@code mandiri_seteru_layar} (opens or refreshes
 * {@link SeteruScreen}) and {@code mandiri_seteru_judul} ({@link KartuJudul}, a HUD element), the hub card
 * {@code sekte_mandiri_seteru} in section MURID, and {@link SeteruPoses} (clips 250-299, props 130-149, activities 57-60).
 * Reads only its own payloads and 39F's shared constants.
 */
public final class SeteruClient {

	private static boolean initialised;
	private static @Nullable CompoundTag terakhir;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(SeteruPayloads.Layar.TYPE, (payload, context) -> lembar(payload));
		ClientPlayNetworking.registerGlobalReceiver(SeteruPayloads.Judul.TYPE, (payload, context) -> KartuJudul.tampil(payload));
		HubCards.register(HubNav.MURID, MandiriIds.KARTU_SETERU, Glyphs.SEAL, null, () -> {
			if (minta(SeteruIds.OP_BUKA, "", 0, "")) {
				HubNav.openChild(new SeteruScreen(terakhir), HubNav.MURID);
			}
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("mandiri_seteru_judul"), new KartuJudul());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			terakhir = null;
			KartuJudul.hapus();
		});
		SeteruPoses.daftar();
	}

	private static void lembar(SeteruPayloads.Layar payload) {
		terakhir = payload.sheet();
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		if (minecraft.gui.screen() instanceof SeteruScreen screen) {
			screen.update(payload.sheet());
		} else if (payload.buka()) {
			minecraft.gui.setScreen(new SeteruScreen(payload.sheet()));
		}
	}

	/** Sends one request; false (and a line above the hotbar) when the server does not run the module. */
	static boolean minta(int op, String seteru, int aksi, String sekte) {
		if (!ClientPlayNetworking.canSend(SeteruPayloads.Aksi.TYPE)) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player != null) {
				minecraft.player.sendOverlayMessage(Component.translatable(SeteruIds.k("layar.tanpa_server")));
			}
			return false;
		}
		ClientPlayNetworking.send(new SeteruPayloads.Aksi(op, seteru == null ? "" : seteru, aksi, sekte == null ? "" : sekte));
		return true;
	}

	private SeteruClient() {
	}
}
