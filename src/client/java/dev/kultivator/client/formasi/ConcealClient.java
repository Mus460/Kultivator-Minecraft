package dev.kultivator.client.formasi;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Concealed nametags on this client (DESIGN 24.7.4, owned by P7c-katalog-ui): the entity ids of {@code FormasiSync.conceal}.
 * Never saved; cleared on disconnect.
 */
public final class ConcealClient {

	private static final IntSet HIDDEN = new IntOpenHashSet();

	/** True when the player with this entity id must not get a nametag here. */
	public static boolean hidden(int entityId) {
		return !HIDDEN.isEmpty() && HIDDEN.contains(entityId);
	}

	/** Replaces the set from a sync tag ({@code conceal} int list). */
	static void update(CompoundTag data) {
		HIDDEN.clear();
		int[] ids = data.getIntArray("conceal").orElse(null);
		if (ids != null) {
			for (int id : ids) {
				HIDDEN.add(id);
			}
			return;
		}
		for (Tag tag : data.getListOrEmpty("conceal")) {
			tag.asInt().ifPresent(HIDDEN::add);
		}
	}

	static void clear() {
		HIDDEN.clear();
	}

	private ConcealClient() {
	}
}
