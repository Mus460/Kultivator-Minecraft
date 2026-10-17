package dev.kultivator.client.disciple;

import dev.kultivator.client.fx.DiscipleFx;
import dev.kultivator.disciple.DiscipleEntities;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Client side of disciples (DESIGN 18): the renderer ({@link DiscipleRenderer}), their continuous effects
 * ({@link DiscipleFx}), a cache of the last {@code DiscipleInfo} per disciple and the disciple scroll
 * ({@link DiscipleScreen}), which opens when the server asks and its details are known.
 */
public final class DiscipleClient {

	/** How long an open request waits for the disciple's details. */
	private static final long PENDING_MILLIS = 5000L;

	private static final Map<Integer, CompoundTag> INFO = new HashMap<>();
	private static final Map<Integer, Long> VERSIONS = new HashMap<>();
	private static long version;
	private static int pendingOpen = -1;
	private static long pendingSince;

	public static void init() {
		EntityRenderers.register(DiscipleEntities.DISCIPLE, DiscipleRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level != null && minecraft.player != null && !minecraft.isPaused()) {
				DiscipleFx.tick(minecraft);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	/** Details for a disciple's screen arrived. */
	public static void onInfo(Payloads.DiscipleInfo info) {
		int id = info.entityId();
		INFO.put(id, info.data());
		VERSIONS.put(id, ++version);
		if (pendingOpen == id) {
			pendingOpen = -1;
			if (Util.getMillis() - pendingSince <= PENDING_MILLIS) {
				open(id);
			}
		}
	}

	/** The server asks to open the screen of the disciple with this entity id. */
	public static void openScreen(int entityId) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof DiscipleScreen screen && screen.entityId() == entityId) {
			return;
		}
		if (INFO.containsKey(entityId)) {
			open(entityId);
		} else {
			pendingOpen = entityId;
			pendingSince = Util.getMillis();
		}
	}

	private static void open(int entityId) {
		CompoundTag info = INFO.get(entityId);
		Minecraft minecraft = Minecraft.getInstance();
		if (info == null || minecraft.player == null) {
			return;
		}
		minecraft.gui.setScreen(new DiscipleScreen(entityId, info, version(entityId)));
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
		pendingOpen = -1;
		DiscipleLook.clear();
	}

	private DiscipleClient() {
	}
}
