package dev.kultivator.client.purba;

import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.purba.PurbaEntities;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.nbt.CompoundTag;

/**
 * The client side of primordial beasts, star calamities and the Heavenly Wrath (DESIGN 26.15, owned by P9-purba): the last
 * {@code DuniaSync} of channel {@code CH_PURBA} (routed by {@code client.dunia.DuniaClient}) that the Purba and Bencana
 * tablets read, and the entity renderers ({@link PrimordialRenderer}, {@link RelicRenderer}, {@link EnvoyRenderer}).
 * Progress uses vanilla boss bars, so there is no HUD code here.
 */
public final class PurbaClient {

	private static CompoundTag data = new CompoundTag();

	/** Called once from {@code KultivatorClient}. */
	public static void init() {
		ClientDisconnect.register((handler, client) -> client.execute(() -> {
			data = new CompoundTag();
			PurbaFx.clear();
		}));
	}

	/** Called once from {@code KultivatorClient} after {@link #init()}: the module's entity renderers. */
	public static void registerRenderers() {
		EntityRenderers.register(PurbaEntities.PRIMORDIAL_BEAST, PrimordialRenderer::new);
		EntityRenderers.register(PurbaEntities.PRIMORDIAL_RELIC, RelicRenderer::new);
		EntityRenderers.register(PurbaEntities.HEAVEN_ENVOY, EnvoyRenderer::new);
	}

	/** The module's last {@code DuniaSync} data arrived. */
	public static void onSync(CompoundTag tag) {
		data = tag == null ? new CompoundTag() : tag;
	}

	/** The last synced data (never null; empty before the first sync). */
	public static CompoundTag data() {
		return data;
	}

	private PurbaClient() {
	}
}
