package dev.kultivator.technique.spec.run;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Known respawn points of every player (DESIGN 22.11.6, SavedData {@code kultivator_homes}: uuid → [dim, x, y, z]),
 * updated on join, on respawn and every 5 seconds from {@code ServerPlayer.getRespawnConfig()}. Mode Kehancuran never
 * carves within {@code kehancuranHomeSafeRadius} of any of them, so a LAN host never wrecks an offline guest's bed base.
 */
public final class Homes extends SavedData {

	private static final int POLL_TICKS = 100;

	public static final Codec<Homes> CODEC = CompoundTag.CODEC.xmap(Homes::new, Homes::write);
	public static final SavedDataType<Homes> TYPE = new SavedDataType<>(Kultivator.id("kultivator_homes"), Homes::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, GlobalPos> homes = new HashMap<>();

	public Homes() {
	}

	private Homes(CompoundTag tag) {
		for (String key : tag.keySet()) {
			CompoundTag entry = tag.getCompoundOrEmpty(key);
			try {
				UUID owner = UUID.fromString(key);
				ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, Identifier.parse(entry.getStringOr("dim", "minecraft:overworld")));
				homes.put(owner, GlobalPos.of(dim, new BlockPos(entry.getIntOr("x", 0), entry.getIntOr("y", 0), entry.getIntOr("z", 0))));
			} catch (RuntimeException ignored) {
				// A broken entry is dropped.
			}
		}
	}

	private CompoundTag write() {
		CompoundTag tag = new CompoundTag();
		homes.forEach((owner, pos) -> {
			CompoundTag entry = new CompoundTag();
			entry.putString("dim", pos.dimension().identifier().toString());
			entry.putInt("x", pos.pos().getX());
			entry.putInt("y", pos.pos().getY());
			entry.putInt("z", pos.pos().getZ());
			tag.put(owner.toString(), entry);
		});
		return tag;
	}

	public static Homes get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	static void init() {
		ServerPlayerEvents.JOIN.register(Homes::note);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> note(newPlayer));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % POLL_TICKS == 7) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					note(player);
				}
			}
		});
	}

	/** Records the player's current respawn point (bed, anchor or set spawn), if any. */
	public static void note(ServerPlayer player) {
		ServerPlayer.RespawnConfig config = player.getRespawnConfig();
		if (config == null) {
			return;
		}
		GlobalPos pos = config.respawnData().globalPos();
		Homes data = get(player.level().getServer());
		if (!pos.equals(data.homes.get(player.getUUID()))) {
			data.homes.put(player.getUUID(), pos);
			data.setDirty();
		}
	}

	/** Every known respawn point in {@code level}. */
	public static List<BlockPos> in(ServerLevel level) {
		List<BlockPos> list = new ArrayList<>();
		for (GlobalPos pos : get(level.getServer()).homes.values()) {
			if (pos.dimension().equals(level.dimension())) {
				list.add(pos.pos());
			}
		}
		return list;
	}
}
