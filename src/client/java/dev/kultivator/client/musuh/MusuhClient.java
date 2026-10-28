package dev.kultivator.client.musuh;

import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.musuh.RogueCultivators;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.nbt.CompoundTag;

/**
 * The client side of the rogue cultivators and sect raids (DESIGN 25.15, owned by P8-musuh): the rogue renderer and its
 * mask layer, and the last {@code DuniaSync} of the {@code CH_MUSUH} channel (routed by {@code client.dunia.DuniaClient})
 * that the Musuh tablet reads. Raid progress uses vanilla boss bars, so there is no HUD code here.
 */
public final class MusuhClient {

	private static CompoundTag data = new CompoundTag();

	/** Called once from {@code KultivatorClient}. */
	public static void init() {
		RogueMaskLayer.register();
		EntityRenderers.register(RogueCultivators.TYPE, RogueRenderer::new);
		ClientDisconnect.register((handler, client) -> client.execute(() -> {
			data = new CompoundTag();
			MusuhFx.clear();
		}));
	}

	/** The module's last {@code DuniaSync} data arrived. */
	public static void onSync(CompoundTag tag) {
		data = tag == null ? new CompoundTag() : tag;
	}

	/** The last synced data (never null; empty before the first sync). */
	public static CompoundTag data() {
		return data;
	}

	private MusuhClient() {
	}
}
