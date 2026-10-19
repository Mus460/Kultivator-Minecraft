package dev.kultivator.client.abadi;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;

/**
 * The client side of Alam Abadi, the ascension, the crossing talisman and the Immortal auction (DESIGN 26.15, owned by
 * P9-langit): keeps the last {@code DuniaSync} of channel {@code CH_ABADI} (routed by {@code client.dunia.DuniaClient}) with
 * its two parts {@code alam} (the Alam tablet) and {@code lelang} (the Lelang tablet). The module has no entity renderers:
 * the black hole is drawn by {@link VortexRenderer} from {@link AbadiFx} packets.
 */
public final class AbadiClient {

	private static CompoundTag data = new CompoundTag();

	/** Called once from {@code KultivatorClient}. */
	public static void init() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			data = new CompoundTag();
			AbadiFx.clear();
		});
	}

	/** Called once from {@code KultivatorClient} after {@link #init()}: the module has no entity renderers (the vortex is particles). */
	public static void registerRenderers() {
	}

	/** The module's last {@code DuniaSync} data arrived. */
	public static void onSync(CompoundTag tag) {
		data = tag == null ? new CompoundTag() : tag.copy();
		receivedAt = net.minecraft.util.Util.getMillis();
	}

	private static long receivedAt;

	/** Ticks since the last sync arrived (countdowns run on between syncs). */
	static long age() {
		return Math.max(0L, (net.minecraft.util.Util.getMillis() - receivedAt) / 50L);
	}

	/** The last synced data (never null; empty before the first sync). */
	public static CompoundTag data() {
		return data;
	}

	/** The Alam tablet's part. */
	static CompoundTag alam() {
		return data.getCompoundOrEmpty("alam");
	}

	/** The Lelang tablet's part. */
	static CompoundTag lelang() {
		return data.getCompoundOrEmpty("lelang");
	}

	private AbadiClient() {
	}
}
