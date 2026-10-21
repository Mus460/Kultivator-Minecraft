package dev.kultivator.dunia.reruntuhan.menara;

import dev.kultivator.Kultivator;
import dev.kultivator.alam.ReturnPoint;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaTickets;
import dev.kultivator.dunia.reruntuhan.RGuard;
import dev.kultivator.dunia.reruntuhan.Reruntuhan;
import dev.kultivator.dunia.reruntuhan.Runs;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Menara Seratus Lantai (idea 65, DESIGN 34.5.7): the tower's own dimension {@code kultivator:menara} (a flat floor of
 * polished deepslate, a dark sky; data in the base pack so a world never loses it), the lobby at its origin (a
 * structure placed by {@code dimension_origin} world generation), the party slots on the flat floor
 * ({@link #slotCentre}), the players' best floors ({@code extra.menara.max}), return points ({@code extra.dunia.menara_ret})
 * and Luka Menara. Floors are entities and client Displays only: nothing is ever built.
 */
public final class Menara {

	public static final ResourceKey<Level> TOWER_LEVEL = ResourceKey.create(Registries.DIMENSION, Kultivator.id("menara"));
	public static final String MODULE = "menara";
	public static final int MAX_SLOTS = 8;
	public static final int FLOOR_Y = 3;
	public static final int LUKA_TICKS = 5 * 60 * 20;
	public static final int PARTY_MAX = 4;
	public static final double PARTY_RANGE = 6.0D;
	public static final String RETURN_KEY = "menara_ret";
	/** The lobby's centre: the start jigsaw lands on world 0, 0 (DESIGN 34.2.2). */
	public static final Vec3 LOBBY = new Vec3(0.5D, FLOOR_Y + 1.0D, 0.5D);

	private static final boolean[] SLOTS = new boolean[MAX_SLOTS];
	static final List<TowerRun> RUNS = new ArrayList<>();
	static final Map<UUID, TowerRun> BY_PLAYER = new HashMap<>();

	/** From {@code Reruntuhan.init()}. */
	public static void init() {
		DuniaAnchors.register(MODULE, new AnchorHandler() {
			@Override
			public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
				if (!Reruntuhan.config().tower) {
					return;
				}
				switch (anchor.what()) {
					case "gerbang" -> Runs.touch(anchor, level, a -> new PagodaRun(a, level));
					case "prasasti" -> Runs.touch(anchor, level, a -> new RankStele(a, level));
					case "lobi" -> Runs.touch(anchor, level, a -> new LobbyRun(a, level));
					default -> {
					}
				}
			}

			@Override
			public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
				Runs.drop(anchor);
			}

			@Override
			public int radius() {
				return 160;
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			TowerRun run = BY_PLAYER.get(player.getUUID());
			if (run != null) {
				run.drop(player);
			}
		});
		ServerPlayerEvents.JOIN.register(player -> Reruntuhan.schedule(2, () -> rejoin(player)));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			TowerRun run = BY_PLAYER.get(player.getUUID());
			if (run != null && isTowerLevel(origin) && !isTowerLevel(destination)) {
				run.drop(player);
			}
		});
	}

	public static boolean isTowerLevel(@Nullable Level level) {
		return level != null && TOWER_LEVEL.equals(level.dimension());
	}

	public static @Nullable ServerLevel tower(MinecraftServer server) {
		return server.getLevel(TOWER_LEVEL);
	}

	// ================================================================== ticking

	public static void tick(MinecraftServer server) {
		if (!RUNS.isEmpty()) {
			for (TowerRun run : List.copyOf(RUNS)) {
				try {
					run.tick();
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Kultivator: a tower run failed; it ends", exception);
					run.end(false, true);
				}
			}
		}
		if (server.getTickCount() % 40 == 0) {
			ServerLevel tower = tower(server);
			if (tower != null) {
				for (ServerPlayer player : List.copyOf(tower.players())) {
					if (!BY_PLAYER.containsKey(player.getUUID()) && !player.isCreative() && !player.isSpectator()
							&& player.position().distanceToSqr(LOBBY) > 30.0D * 30.0D) {
						toLobby(player);
					}
				}
			}
		}
	}

	public static void shutdown(MinecraftServer server) {
		for (TowerRun run : List.copyOf(RUNS)) {
			run.end(false, true);
		}
		RUNS.clear();
		BY_PLAYER.clear();
		java.util.Arrays.fill(SLOTS, false);
	}

	private static void rejoin(ServerPlayer player) {
		if (player.isRemoved() || !isTowerLevel(player.level()) || BY_PLAYER.containsKey(player.getUUID())) {
			return;
		}
		toLobby(player);
	}

	// ================================================================== slots

	static synchronized int lease() {
		int allowed = Math.max(1, Math.min(MAX_SLOTS, Reruntuhan.config().towerSlots));
		for (int s = 0; s < allowed; s++) {
			if (!SLOTS[s]) {
				SLOTS[s] = true;
				return s;
			}
		}
		return -1;
	}

	static synchronized void release(int slot) {
		if (slot >= 0 && slot < MAX_SLOTS) {
			SLOTS[slot] = false;
		}
	}

	public static synchronized int leased() {
		int n = 0;
		for (boolean b : SLOTS) {
			if (b) {
				n++;
			}
		}
		return n;
	}

	/** The centre of slot {@code s} on the flat floor (DESIGN 34.5.7: x = 4 096 + s × 1 024, z = 4 096). */
	public static BlockPos slotCentre(int slot) {
		return new BlockPos(4096 + slot * 1024, FLOOR_Y, 4096);
	}

	// ================================================================== players

	public static boolean inRun(ServerPlayer player) {
		return BY_PLAYER.containsKey(player.getUUID());
	}

	public static @Nullable TowerRun runOf(ServerPlayer player) {
		return BY_PLAYER.get(player.getUUID());
	}

	/** The best floor the player has cleared. */
	public static int best(ServerPlayer player) {
		return Cultivators.get(player).extra().getCompoundOrEmpty(MODULE).getIntOr("max", 0);
	}

	static void setBest(ServerPlayer player, int floor) {
		CompoundTag extra = Cultivators.get(player).extra();
		CompoundTag own = extra.getCompound(MODULE).orElse(null);
		if (own == null) {
			own = new CompoundTag();
			extra.put(MODULE, own);
		}
		own.putInt("max", Math.max(0, Math.min(100, floor)));
		Cultivators.dirty(player);
	}

	/** The highest band gate the player may take (1-10). */
	public static int unlockedBand(ServerPlayer player) {
		return Math.min(10, best(player) / 10 + 1);
	}

	public static long lukaLeft(ServerPlayer player) {
		return RGuard.lukaLeft(player, "luka_menara");
	}

	static void giveLuka(ServerPlayer player) {
		RGuard.setLuka(player, "luka_menara", LUKA_TICKS);
		Msg.chat(player, Component.translatable("kultivator.reruntuhan.menara.luka", LUKA_TICKS / 1200).withStyle(ChatFormatting.RED));
	}

	/** Saves where the player stands as the tower's return point. */
	static void saveReturn(ServerPlayer player) {
		CompoundTag ret = new CompoundTag();
		ret.putString("dim", player.level().dimension().identifier().toString());
		ret.putDouble("x", player.getX());
		ret.putDouble("y", player.getY());
		ret.putDouble("z", player.getZ());
		ret.putFloat("yr", player.getYRot());
		ReturnPoint.dunia(player).put(RETURN_KEY, ret);
		Cultivators.dirty(player);
	}

	/** Back to the return point (else the respawn point); true when a teleport happened. */
	public static boolean sendHome(ServerPlayer player) {
		CompoundTag ret = ReturnPoint.dunia(player).getCompoundOrEmpty(RETURN_KEY);
		ReturnPoint.dunia(player).remove(RETURN_KEY);
		Cultivators.dirty(player);
		TeleportTransition target = null;
		Identifier dim = Identifier.tryParse(ret.getStringOr("dim", ""));
		if (dim != null) {
			ServerLevel level = player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dim));
			if (level != null && !isTowerLevel(level)) {
				target = new TeleportTransition(level, new Vec3(ret.getDoubleOr("x", 0.0D), ret.getDoubleOr("y", 64.0D), ret.getDoubleOr("z", 0.0D)),
						Vec3.ZERO, ret.getFloatOr("yr", 0.0F), 0.0F, TeleportTransition.DO_NOTHING);
			}
		}
		if (target == null) {
			target = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
		}
		ServerPlayer moved = player.teleport(target);
		(moved != null ? moved : player).setPortalCooldown();
		return moved != null;
	}

	/** Into the lobby (a short chunk ticket first, DESIGN 34.4.7). */
	public static void toLobby(ServerPlayer player) {
		ServerLevel tower = tower(player.level().getServer());
		if (tower == null) {
			Msg.chat(player, Component.translatable("kultivator.reruntuhan.menara.no_dimension").withStyle(ChatFormatting.RED));
			return;
		}
		DuniaTickets.touch(tower, ChunkPos.containing(BlockPos.containing(LOBBY)), 1);
		if (player.level() == tower) {
			player.teleportTo(LOBBY.x, LOBBY.y, LOBBY.z);
			player.setDeltaMovement(Vec3.ZERO);
			player.needsSync = true;
		} else {
			player.teleport(new TeleportTransition(tower, LOBBY, Vec3.ZERO, 180.0F, 0.0F, TeleportTransition.DO_NOTHING));
		}
		player.setPortalCooldown();
	}

	/** Enters the tower from the pagoda: the party (≤ 4 within 6 blocks of the gate) goes to the lobby. */
	static void enterFromPagoda(ServerPlayer leader, Vec3 gate) {
		if (!Reruntuhan.config().tower) {
			Msg.chat(leader, Component.translatable("kultivator.reruntuhan.menara.closed").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (lukaLeft(leader) > 0L) {
			Msg.chat(leader, Component.translatable("kultivator.reruntuhan.menara.why.luka", Msg.number(Math.ceil(lukaLeft(leader) / 1200.0D)))
					.withStyle(ChatFormatting.RED));
			return;
		}
		if (blocked(leader)) {
			Msg.chat(leader, Component.translatable("kultivator.reruntuhan.menara.why.busy").withStyle(ChatFormatting.GRAY));
			return;
		}
		ServerLevel tower = tower(leader.level().getServer());
		if (tower == null) {
			Msg.chat(leader, Component.translatable("kultivator.reruntuhan.menara.no_dimension").withStyle(ChatFormatting.RED));
			return;
		}
		List<ServerPlayer> party = new ArrayList<>();
		party.add(leader);
		for (ServerPlayer other : leader.level().players()) {
			if (other != leader && party.size() < PARTY_MAX && other.position().distanceToSqr(gate) <= PARTY_RANGE * PARTY_RANGE && !other.isSpectator()
					&& lukaLeft(other) <= 0L && !blocked(other)) {
				party.add(other);
			}
		}
		for (ServerPlayer member : party) {
			saveReturn(member);
			toLobby(member);
			Msg.title(member, Component.translatable("kultivator.reruntuhan.menara.title").withStyle(ChatFormatting.GOLD),
					Component.translatable("kultivator.reruntuhan.menara.lobby.sub", best(member)).withStyle(ChatFormatting.GRAY), 10, 60, 20);
		}
	}

	/** Busy with something that must not be left (the same refusals as a secret realm's gate). */
	static boolean blocked(ServerPlayer player) {
		return dev.kultivator.alam.Alam.blocked(player) || inRun(player) || player.isPassenger()
				|| dev.kultivator.purba.Purba.inFight(player) || dev.kultivator.purba.Purba.inCalamity(player);
	}

	/** Starts a run for the party at {@code floor}; null (with a message) when no slot is free. */
	static @Nullable TowerRun start(List<ServerPlayer> party, int floor, boolean free) {
		if (party.isEmpty()) {
			return null;
		}
		ServerLevel tower = tower(party.get(0).level().getServer());
		if (tower == null) {
			return null;
		}
		int slot = lease();
		if (slot < 0) {
			for (ServerPlayer player : party) {
				Msg.chat(player, Component.translatable("kultivator.reruntuhan.menara.why.slots").withStyle(ChatFormatting.GRAY));
			}
			return null;
		}
		TowerRun run = new TowerRun(tower, slot, party, Math.max(1, Math.min(100, floor)), free);
		RUNS.add(run);
		for (ServerPlayer player : party) {
			BY_PLAYER.put(player.getUUID(), run);
		}
		run.begin();
		return run;
	}

	/**
	 * The tower card of the hub (op {@code info}, DESIGN 34.6 T card "Menara Seratus"): the player's best floor, open
	 * bands, Luka Menara, the week's ranking and the best of all time, plus the ruins and prisons of module R (cleared ruins,
	 * the best ruin tier, Pahala, the prisons whose seal lies broken now). Rate-limited to one answer a second.
	 */
	public static void sendInfo(ServerPlayer player) {
		if (!dev.kultivator.voice.Voice.ready(player.getUUID(), "menara_info", 20)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		TowerRecords records = TowerRecords.get(server);
		records.rollWeek(server, server.overworld().getGameTime());
		CompoundTag tag = new CompoundTag();
		tag.putString("op", "info");
		tag.putInt("best", best(player));
		tag.putInt("unlocked", unlockedBand(player));
		tag.putInt("luka", (int) (lukaLeft(player) / 20L));
		tag.putBoolean("tower", Reruntuhan.config().tower);
		tag.putBoolean("in_run", inRun(player));
		net.minecraft.nbt.ListTag weekly = new net.minecraft.nbt.ListTag();
		for (TowerRecords.Entry entry : records.weekly(5)) {
			weekly.add(net.minecraft.nbt.StringTag.valueOf(entry.name() + "|" + entry.week()));
		}
		tag.put("weekly", weekly);
		List<TowerRecords.Entry> all = records.allTime(1);
		if (!all.isEmpty()) {
			tag.putString("best_name", all.get(0).name());
			tag.putInt("best_floor", all.get(0).best());
		}
		CompoundTag own = Cultivators.get(player).extra().getCompoundOrEmpty(Reruntuhan.SECTION);
		tag.putInt("ruins", own.getIntOr("ruins", 0));
		tag.putInt("ruin_best", own.getIntOr("ruin_best", 0));
		tag.putInt("pahala", own.getIntOr("pahala", 0));
		tag.putBoolean("penakluk", own.getBooleanOr("penakluk", false));
		tag.putLong("luka_iblis", RGuard.lukaLeft(player, "luka_iblis") / 20L);
		int broken = 0;
		int known = 0;
		for (dev.kultivator.dunia.reruntuhan.penjara.Prisons.Seal seal : dev.kultivator.dunia.reruntuhan.penjara.Prisons.all(server)) {
			known++;
			if (seal.broken()) {
				broken++;
			}
		}
		tag.putInt("prisons", known);
		tag.putInt("prisons_broken", broken);
		dev.kultivator.dunia.reruntuhan.RNet.send(player, tag);
	}

	/** A riddle answer from the Ujian Hati screen. */
	public static void answer(ServerPlayer player, int option) {
		TowerRun run = BY_PLAYER.get(player.getUUID());
		if (run != null) {
			run.answer(player, option);
		}
	}

	/** A lethal blow inside the tower (DeathGuard REALM): 1 HP and out to the lobby with Luka Menara; the run ends for them. */
	public static void lethal(ServerPlayer player) {
		TowerRun run = BY_PLAYER.get(player.getUUID());
		if (run != null) {
			run.fallen(player);
		}
	}

	private Menara() {
	}
}
