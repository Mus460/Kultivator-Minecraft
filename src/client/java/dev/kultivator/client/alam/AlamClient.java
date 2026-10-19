package dev.kultivator.client.alam;

import dev.kultivator.alam.AlamEntities;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * The client side of the secret realms (DESIGN 25.15, owned by P8-alam). One {@code DuniaSync} channel carries three kinds
 * of data: {@code page} (the Alam tablet), {@code scene} / {@code scene_end} (the chamber scenery for {@link RealmScenery})
 * and {@code treasure} (opens the {@link TreasureScreen}). The relic entity draws nothing itself ({@code NoopRenderer});
 * its looks come from the scenery.
 */
public final class AlamClient {

	private static CompoundTag data = new CompoundTag();
	private static @Nullable CompoundTag pendingTreasure;

	/** Called once from {@code KultivatorClient}. */
	public static void init() {
		EntityRenderers.register(AlamEntities.REALM_RELIC, NoopRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (pendingTreasure != null && minecraft.player != null && minecraft.gui.screen() == null) {
				CompoundTag treasure = pendingTreasure;
				pendingTreasure = null;
				minecraft.gui.setScreen(new TreasureScreen(treasure));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			data = new CompoundTag();
			pendingTreasure = null;
			AlamFx.clear();
		});
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
			CompoundTag treasure = tag.getCompoundOrEmpty("treasure").copy();
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new TreasureScreen(treasure));
			} else {
				pendingTreasure = treasure;
			}
		}
	}

	/** The last synced Alam tablet data (never null; empty before the first sync). */
	public static CompoundTag data() {
		return data;
	}

	private AlamClient() {
	}
}
