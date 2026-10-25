package dev.kultivator.client.alam;

import dev.kultivator.alam.AlamEntities;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.musuh.MusuhPage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.nbt.CompoundTag;

/**
 * The client side of the secret realms (DESIGN 25.15, owned by P8-alam). One {@code DuniaSync} channel carries three kinds
 * of data: {@code page} (Dunia › Alam), {@code scene} / {@code scene_end} (the chamber scenery for {@link RealmScenery})
 * and {@code treasure} (the Di Sini prompt {@link TreasureScreen}, DESIGN 43.8.2). The relic entity draws nothing itself
 * ({@code NoopRenderer}); its looks come from the scenery. Registers the Dunia pages of phase 8 in the Gulungan (DESIGN
 * 43.5): Alam (with the tower sub of module R) and Musuh.
 */
public final class AlamClient {

	private static CompoundTag data = new CompoundTag();

	/** Called once from {@code KultivatorClient}. */
	public static void init() {
		EntityRenderers.register(AlamEntities.REALM_RELIC, NoopRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(TreasureScreen::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			data = new CompoundTag();
			TreasureScreen.reset();
			dev.kultivator.client.dunia.kota.SainganScreen.reset();
			AlamFx.clear();
		});
		HubPages.daftar(HubNav.DUNIA, Urutan.ID_ALAM, Urutan.ALAM, AlamPage::new);
		// musuh/MusuhClient has no Gulungan hook of its own; the phase-8 Dunia spine registers both of its pages here
		HubPages.daftar(HubNav.DUNIA, Urutan.ID_MUSUH, Urutan.MUSUH, MusuhPage::new);
	}

	/** The module's last {@code DuniaSync} data arrived. */
	public static void onSync(CompoundTag tag) {
		if (tag == null) {
			return;
		}
		if (tag.contains("page")) {
			data = tag.getCompoundOrEmpty("page").copy();
		}
		if (tag.contains("scene")) {
			RealmScenery.setScene(tag.getCompoundOrEmpty("scene"));
		}
		if (tag.getBooleanOr("scene_end", false)) {
			RealmScenery.endScene();
		}
		if (tag.contains("treasure")) {
			TreasureScreen.tampil(tag.getCompoundOrEmpty("treasure").copy());
		}
	}

	/** The last synced Alam tablet data (never null; empty before the first sync). */
	public static CompoundTag data() {
		return data;
	}

	private AlamClient() {
	}
}
