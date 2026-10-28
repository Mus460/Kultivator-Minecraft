package dev.kultivator.client.disciple;

import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.fx.DiscipleFx;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.disciple.DiscipleEntities;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Client side of disciples (DESIGN 18): the renderer ({@link DiscipleRenderer}), their continuous effects
 * ({@link DiscipleFx}), a cache of the last {@code DiscipleInfo} per disciple and the page Murid › Murid
 * ({@link MuridPage}, DESIGN 43), which the server's "open" request shows on that disciple.
 */
public final class DiscipleClient {

	private static final Map<Integer, CompoundTag> INFO = new HashMap<>();
	private static final Map<Integer, Long> VERSIONS = new HashMap<>();
	private static long version;

	public static void init() {
		EntityRenderers.register(DiscipleEntities.DISCIPLE, DiscipleRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level != null && minecraft.player != null && !minecraft.isPaused()) {
				DiscipleFx.tick(minecraft);
			}
		});
		ClientDisconnect.register((handler, client) -> client.execute(() -> clear()));
		HubPages.daftar(HubNav.MURID, Urutan.ID_MURID, Urutan.MURID, MuridPage::new);
	}

	/** Details of a disciple arrived (the open page picks them up by version). */
	public static void onInfo(Payloads.DiscipleInfo info) {
		int id = info.entityId();
		INFO.put(id, info.data());
		VERSIONS.put(id, ++version);
	}

	/** The server asks to show the disciple with this entity id: Murid › Murid on it (43.4.4). */
	public static void openScreen(int entityId) {
		HubNav.buka(MuridPage.tujuan(entityId, ""));
	}

	/** The last details of a disciple, or null. */
	static @Nullable CompoundTag info(int entityId) {
		return INFO.get(entityId);
	}

	/** Grows with every new DiscipleInfo for this disciple (0 when none arrived). */
	static long version(int entityId) {
		return VERSIONS.getOrDefault(entityId, 0L);
	}

	private static void clear() {
		INFO.clear();
		VERSIONS.clear();
		DiscipleLook.clear();
	}

	private DiscipleClient() {
	}
}
