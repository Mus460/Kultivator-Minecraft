package dev.kultivator.musuh;

import dev.kultivator.Kultivator;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.Sects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Sarang Perampok, bandit camps for village quests (DESIGN 25.4.4). A camp is only a data point (memory only, 25.14): no
 * block, just a client smoke column visible from 48 blocks. When the owner (or a sect mate) comes within 48 blocks, 3-5
 * rogues spawn there, tiered by the owner's raid level; they despawn after 2 minutes without a member near and come back
 * when one returns. Clearing them completes the camp. Module D may open camps for its help requests through
 * {@link #open}, or spawn its own band with {@code RogueCultivators.spawnBand}.
 */
public final class Camps {

	public static final double TRIGGER_RANGE = 48.0D;
	public static final int EMPTY_TICKS = 2400;
	public static final int SMOKE_INTERVAL = 40;

	private static final class Camp {
		final int id;
		final ResourceKey<Level> dim;
		final BlockPos pos;
		final UUID owner;
		final Consumer<Boolean> onCleared;
		@Nullable RogueCultivators.Band band;
		long emptySince = -1L;

		Camp(int id, ResourceKey<Level> dim, BlockPos pos, UUID owner, Consumer<Boolean> onCleared) {
			this.id = id;
			this.dim = dim;
			this.pos = pos;
			this.owner = owner;
			this.onCleared = onCleared;
		}
	}

	private static final Map<Integer, Camp> CAMPS = new LinkedHashMap<>();
	private static int nextId = 1;

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CAMPS.clear());
	}

	/**
	 * Opens a camp of {@code owner}'s quest at {@code pos}; {@code onCleared} is told once with true when its rogues were
	 * beaten. Returns the camp id (for {@link #close}).
	 */
	public static int open(ServerLevel level, BlockPos pos, UUID owner, Consumer<Boolean> onCleared) {
		int id = nextId++;
		CAMPS.put(id, new Camp(id, level.dimension(), pos.immutable(), owner, onCleared == null ? cleared -> {
		} : onCleared));
		return id;
	}

	/** Removes a camp (its rogues leave); nothing is told. */
	public static void close(int id) {
		Camp camp = CAMPS.remove(id);
		if (camp != null && camp.band != null) {
			RogueCultivators.Band band = camp.band;
			camp.band = null;
			band.dismiss();
		}
	}

	/** True while the camp exists. */
	public static boolean exists(int id) {
		return CAMPS.containsKey(id);
	}

	/** Where the camp is (null when gone). */
	public static @Nullable BlockPos pos(int id) {
		Camp camp = CAMPS.get(id);
		return camp == null ? null : camp.pos;
	}

	static void tick(MinecraftServer server, long tick) {
		if (tick % 20L != 0L || CAMPS.isEmpty()) {
			return;
		}
		for (Camp camp : List.copyOf(CAMPS.values())) {
			ServerLevel level = server.getLevel(camp.dim);
			if (level == null) {
				continue;
			}
			Vec3 centre = Vec3.atBottomCenterOf(camp.pos);
			long now = level.getGameTime();
			boolean member = memberNear(server, level, camp, centre);
			if (tick % SMOKE_INTERVAL == 0L && level.isLoaded(camp.pos)) {
				Fx.send(level, centre, FxType.MUSUH, MusuhFxIds.CAMP_SMOKE, 0, 2.0F);
			}
			if (camp.band == null) {
				if (member && level.isPositionEntityTicking(camp.pos)) {
					spawn(level, camp, centre);
				}
				continue;
			}
			if (member) {
				camp.emptySince = -1L;
			} else if (camp.emptySince < 0L) {
				camp.emptySince = now;
			} else if (now - camp.emptySince >= EMPTY_TICKS) {
				RogueCultivators.Band band = camp.band;
				camp.band = null;
				camp.emptySince = -1L;
				band.dismiss();
			}
		}
	}

	private static boolean memberNear(MinecraftServer server, ServerLevel level, Camp camp, Vec3 centre) {
		ServerPlayer owner = server.getPlayerList().getPlayer(camp.owner);
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.position().distanceToSqr(centre) > TRIGGER_RANGE * TRIGGER_RANGE) {
				continue;
			}
			if (player.getUUID().equals(camp.owner) || owner != null && Sects.areSectMates(owner, player)) {
				return true;
			}
		}
		return false;
	}

	private static void spawn(ServerLevel level, Camp camp, Vec3 centre) {
		int lr = SectRaids.raidLevel(camp.owner);
		int[] tiers = lr <= 2 ? new int[]{1, 1, 1} : lr <= 4 ? new int[]{1, 1, 2, 2} : lr <= 6 ? new int[]{1, 2, 2, 2} : new int[]{2, 2, 2, 3, 3};
		Vec3 ground = new Vec3(centre.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, camp.pos.getX(), camp.pos.getZ()), centre.z);
		RogueCultivators.Band[] holder = new RogueCultivators.Band[1];
		holder[0] = RogueCultivators.band(level, ground, tiers, lr, RogueCultivators.ROLE_CAMP, camp.pos, -1, RogueCultivators.VARIANT_ROGUE, cleared -> {
			if (camp.band != holder[0]) {
				return;
			}
			camp.band = null;
			if (cleared && CAMPS.remove(camp.id) != null) {
				try {
					camp.onCleared.accept(true);
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("A camp callback failed", exception);
				}
				ServerPlayer owner = level.getServer().getPlayerList().getPlayer(camp.owner);
				if (owner != null) {
					Musuh.logEncounter(owner, Musuh.KIND_CAMP, true, tiers.length);
				}
			}
		});
		camp.band = holder[0];
		camp.emptySince = -1L;
	}

	private Camps() {
	}
}
