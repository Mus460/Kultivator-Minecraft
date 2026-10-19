package dev.kultivator.desa;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only village lookups (DESIGN 25.6.2, 25.13): the closest bell ({@code PoiTypes.MEETING}) in loaded chunks, whether a
 * bell still stands, the villagers whose {@code MEETING_POINT} memory is this bell (capped at 20), and the vanilla
 * {@code MINOR_POSITIVE} gossip a finished request leaves. Nothing here changes a block.
 */
public final class VillageScan {

	/** A plaque must stand this close to a bell. */
	public static final int BELL_RANGE = 24;
	/** Villagers are counted inside this horizontal box around the bell. */
	public static final double COUNT_RANGE = 48.0D;
	public static final int COUNT_CAP = 20;
	/** Gossip of one finished request (DESIGN 25.6.6: max 25, decays 1 per day). */
	public static final int GOSSIP = 5;

	/** The closest bell within {@link #BELL_RANGE} of {@code pos} (loaded chunks only), or null. */
	public static @Nullable BlockPos findBell(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return null;
		}
		Optional<BlockPos> bell = level.getPoiManager().findClosest(holder -> holder.is(PoiTypes.MEETING), candidate -> level.isLoaded(candidate),
				pos, BELL_RANGE, PoiManager.Occupancy.ANY);
		return bell.map(BlockPos::immutable).orElse(null);
	}

	/** True when a bell POI still stands at {@code bell} (false when its chunk is not loaded: unknown). */
	public static boolean bellStands(ServerLevel level, BlockPos bell) {
		return level.isLoaded(bell) && level.getPoiManager().existsAtPosition(PoiTypes.MEETING, bell);
	}

	/** The villagers that meet at this bell (loaded ones only), at most {@link #COUNT_CAP}. */
	public static List<Villager> villagersOf(ServerLevel level, BlockPos bell) {
		GlobalPos meeting = GlobalPos.of(level.dimension(), bell);
		AABB box = new AABB(bell).inflate(COUNT_RANGE, 24.0D, COUNT_RANGE);
		List<Villager> list = level.getEntitiesOfClass(Villager.class, box, villager -> villager.isAlive() && meets(villager, meeting));
		return list.size() > COUNT_CAP ? list.subList(0, COUNT_CAP) : list;
	}

	private static boolean meets(Villager villager, GlobalPos meeting) {
		try {
			return villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT).map(meeting::equals).orElse(false);
		} catch (RuntimeException exception) {
			return false;
		}
	}

	/** Adds the finished request's gossip for {@code owner} with every counted villager of the bell. */
	public static void gossip(ServerLevel level, BlockPos bell, UUID owner) {
		for (Villager villager : villagersOf(level, bell)) {
			villager.getGossips().add(owner, GossipType.MINOR_POSITIVE, GOSSIP);
		}
	}

	private VillageScan() {
	}
}
