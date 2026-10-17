package dev.kultivator.beast;

import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The spirit beasts currently loaded on the server, by owner. Filled when a beast is awakened or its chunk loads,
 * emptied when it unloads, dies or changes dimension (the copy in the new dimension registers itself first, so an entry
 * is only removed when it still points at the very entity that is unloading).
 */
final class BeastRegistry {

	private static final Map<UUID, Map<UUID, Mob>> BY_OWNER = new HashMap<>();

	static void add(Mob beast, BeastData data) {
		BY_OWNER.computeIfAbsent(data.owner(), id -> new LinkedHashMap<>()).put(beast.getUUID(), beast);
	}

	static void remove(Mob beast, BeastData data) {
		Map<UUID, Mob> beasts = BY_OWNER.get(data.owner());
		if (beasts == null) {
			return;
		}
		if (beasts.get(beast.getUUID()) == beast) {
			beasts.remove(beast.getUUID());
		}
		if (beasts.isEmpty()) {
			BY_OWNER.remove(data.owner());
		}
	}

	/** A copy of the owner's loaded spirit beasts that are still alive and in a world (stale entries are dropped). */
	static List<Mob> of(UUID owner) {
		Map<UUID, Mob> beasts = BY_OWNER.get(owner);
		if (beasts == null) {
			return List.of();
		}
		List<Mob> list = new ArrayList<>(beasts.size());
		beasts.values().removeIf(beast -> beast.isRemoved() || !beast.hasAttached(BeastData.TYPE));
		for (Mob beast : beasts.values()) {
			if (beast.isAlive()) {
				list.add(beast);
			}
		}
		if (beasts.isEmpty()) {
			BY_OWNER.remove(owner);
		}
		return list;
	}

	/** Runs {@code action} for every loaded, living spirit beast (any owner, any dimension); it must not change the registry. */
	static void forEach(Consumer<Mob> action) {
		for (Map<UUID, Mob> beasts : BY_OWNER.values()) {
			for (Mob beast : beasts.values()) {
				if (!beast.isRemoved() && beast.isAlive()) {
					action.accept(beast);
				}
			}
		}
	}

	static void clear() {
		BY_OWNER.clear();
	}

	private BeastRegistry() {
	}
}
