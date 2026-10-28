package dev.kultivator.client.batin;

import dev.kultivator.batin.BatinItems;
import dev.kultivator.batin.CiptaanArts;
import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.codex.CodexNames;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.Nullable;

/**
 * The client side of the inner world, self-created techniques and Jalan Abadi (DESIGN 26.15, owned by P9-batin): keeps the
 * last {@code DuniaSync} of channel {@code CH_BATIN} (routed by {@code client.dunia.DuniaClient}; the server sends it on
 * join and on every change), feeds the own names of {@code own_art_1..3} to {@link CodexNames} and registers the meteor
 * renderer.
 */
public final class BatinClient {

	private static CompoundTag data = new CompoundTag();

	/** Called once from {@code KultivatorClient}. */
	public static void init() {
		CodexNames.register(BatinClient::ownName);
		ClientDisconnect.register((handler, client) -> client.execute(() -> data = new CompoundTag()));
	}

	/** Called once from {@code KultivatorClient} after {@link #init()}: the module's entity renderers. */
	public static void registerRenderers() {
		EntityRenderers.register(BatinItems.METEOR, MeteorRenderer::new);
	}

	/** The module's last {@code DuniaSync} data arrived. */
	public static void onSync(CompoundTag tag) {
		data = tag == null ? new CompoundTag() : tag;
	}

	/** The last synced data (never null; empty before the first sync). */
	public static CompoundTag data() {
		return data;
	}

	/** The own name of {@code own_art_i}, or null (the Kitab, the slip and the cast carousel show it). */
	static @Nullable String ownName(String specId) {
		int slot = CiptaanArts.slotOf(specId);
		if (slot < 0) {
			return null;
		}
		ListTag arts = data.getListOrEmpty("ciptaan");
		if (slot >= arts.size()) {
			return null;
		}
		String name = arts.getCompoundOrEmpty(slot).getStringOr("name", "");
		return name.isEmpty() ? null : name;
	}

	private BatinClient() {
	}
}
