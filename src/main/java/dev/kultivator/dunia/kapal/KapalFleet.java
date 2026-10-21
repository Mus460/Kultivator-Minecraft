package dev.kultivator.dunia.kapal;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SavedData {@code kultivator:kapal} in the overworld (DESIGN 34.5.15): every deployed ship by owner (one per owner: its
 * entity UUID, dimension, last known position and class, kept fresh by the ship every 100 ticks), so "panggil" and the
 * map find a ship whose chunk is unloaded; and disciples that were aboard a ship destroyed while they could not land
 * (their departure tags wait here until their master is near and they can arrive; never lost).
 */
public final class KapalFleet extends SavedData {

	public static final Codec<KapalFleet> CODEC = CompoundTag.CODEC.xmap(KapalFleet::fromTag, KapalFleet::toTag);
	public static final SavedDataType<KapalFleet> TYPE = new SavedDataType<>(Kultivator.id("kapal"), KapalFleet::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** A deployed ship. */
	public record Entry(UUID ship, ResourceKey<Level> dim, BlockPos pos, int kind, String ownerName) {
	}

	private final Map<UUID, Entry> ships = new LinkedHashMap<>();
	private final Map<UUID, ListTag> pending = new LinkedHashMap<>();

	public KapalFleet() {
	}

	static KapalFleet get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	// ================================================================== ships

	static @Nullable Entry ship(MinecraftServer server, UUID owner) {
		return owner == null ? null : get(server).ships.get(owner);
	}

	static List<Map.Entry<UUID, Entry>> all(MinecraftServer server) {
		return new ArrayList<>(get(server).ships.entrySet());
	}

	static void put(MinecraftServer server, UUID owner, Entry entry) {
		KapalFleet data = get(server);
		Entry old = data.ships.get(owner);
		if (entry.equals(old)) {
			return;
		}
		data.ships.put(owner, entry);
		data.setDirty();
	}

	/** Forgets the owner's ship when it is {@code ship} (or always when {@code ship} is null). */
	static void remove(MinecraftServer server, UUID owner, @Nullable UUID ship) {
		KapalFleet data = get(server);
		Entry old = data.ships.get(owner);
		if (old != null && (ship == null || old.ship().equals(ship))) {
			data.ships.remove(owner);
			data.setDirty();
		}
	}

	// ================================================================== disciples waiting to land

	static void addPending(MinecraftServer server, UUID owner, ListTag tags) {
		if (owner == null || tags == null || tags.isEmpty()) {
			return;
		}
		KapalFleet data = get(server);
		ListTag list = data.pending.computeIfAbsent(owner, id -> new ListTag());
		for (Tag tag : tags) {
			list.add(tag.copy());
		}
		data.setDirty();
	}

	static @Nullable ListTag pending(MinecraftServer server, UUID owner) {
		ListTag list = get(server).pending.get(owner);
		return list == null || list.isEmpty() ? null : list;
	}

	static void setPending(MinecraftServer server, UUID owner, ListTag left) {
		KapalFleet data = get(server);
		if (left == null || left.isEmpty()) {
			data.pending.remove(owner);
		} else {
			data.pending.put(owner, left);
		}
		data.setDirty();
	}

	static List<UUID> pendingOwners(MinecraftServer server) {
		return new ArrayList<>(get(server).pending.keySet());
	}

	// ================================================================== save

	private static KapalFleet fromTag(CompoundTag tag) {
		KapalFleet data = new KapalFleet();
		for (Tag each : tag.getListOrEmpty("ships")) {
			if (!(each instanceof CompoundTag entry)) {
				continue;
			}
			UUID owner = entry.read("owner", UUIDUtil.CODEC).orElse(null);
			UUID ship = entry.read("ship", UUIDUtil.CODEC).orElse(null);
			Identifier dim = Identifier.tryParse(entry.getStringOr("dim", ""));
			if (owner == null || ship == null || dim == null) {
				continue;
			}
			BlockPos pos = new BlockPos(entry.getIntOr("x", 0), entry.getIntOr("y", 0), entry.getIntOr("z", 0));
			data.ships.put(owner, new Entry(ship, ResourceKey.create(Registries.DIMENSION, dim), pos, entry.getIntOr("kind", 0),
					entry.getStringOr("name", "")));
		}
		for (Tag each : tag.getListOrEmpty("pending")) {
			if (!(each instanceof CompoundTag entry)) {
				continue;
			}
			UUID owner = entry.read("owner", UUIDUtil.CODEC).orElse(null);
			ListTag list = entry.getListOrEmpty("tags");
			if (owner != null && !list.isEmpty()) {
				data.pending.put(owner, list.copy());
			}
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		ships.forEach((owner, entry) -> {
			CompoundTag e = new CompoundTag();
			e.store("owner", UUIDUtil.CODEC, owner);
			e.store("ship", UUIDUtil.CODEC, entry.ship());
			e.putString("dim", entry.dim().identifier().toString());
			e.putInt("x", entry.pos().getX());
			e.putInt("y", entry.pos().getY());
			e.putInt("z", entry.pos().getZ());
			e.putInt("kind", entry.kind());
			e.putString("name", entry.ownerName());
			list.add(e);
		});
		tag.put("ships", list);
		ListTag waiting = new ListTag();
		pending.forEach((owner, tags) -> {
			CompoundTag e = new CompoundTag();
			e.store("owner", UUIDUtil.CODEC, owner);
			e.put("tags", tags.copy());
			waiting.add(e);
		});
		tag.put("pending", waiting);
		return tag;
	}
}
