package dev.kultivator.client.desa;

import net.minecraft.nbt.CompoundTag;

/**
 * The client side of the vassal villages (DESIGN 25.15, owned by P8-desa). Spine stub: keeps the last {@code DuniaSync} of its channel
 * (routed by {@code client.dunia.DuniaClient}); the module adds its renderers, screens and HUD in {@link #init()}.
 */
public final class DesaClient {

	private static CompoundTag data = new CompoundTag();

	/** Called once from {@code KultivatorClient}. */
	public static void init() {
	}

	/** The module's last {@code DuniaSync} data arrived. */
	public static void onSync(CompoundTag tag) {
		data = tag == null ? new CompoundTag() : tag;
	}

	/** The last synced data (never null; empty before the first sync). */
	public static CompoundTag data() {
		return data;
	}

	private DesaClient() {
	}
}
