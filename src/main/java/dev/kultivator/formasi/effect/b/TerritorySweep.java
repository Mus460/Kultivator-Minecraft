package dev.kultivator.formasi.effect.b;

import dev.kultivator.sect.hall.Halls;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The territory sweep of DESIGN 24.9 (K1 at territory scope and M2): the territory box (plus the approach band) in four
 * sectors, one sector per 40 ticks so the whole territory is swept every 8 s, plus a 32-block box around each online
 * member inside every 40 ticks. At most {@link #MAX_PER_QUERY} entities are kept per query. The result of one owner is
 * computed once per 40-tick window and shared by every handler that asks.
 */
public final class TerritorySweep {

	public static final int PERIOD_TICKS = 40;
	public static final int MAX_PER_QUERY = 32;
	public static final double MEMBER_BOX = 32.0D;
	/** The widest approach band any handler asks for (M2 with a Shadow keeper). */
	public static final double MAX_BAND = 24.0D;

	private record Result(long window, List<Mob> mobs) {
	}

	private record Key(UUID owner, ResourceKey<Level> dim) {
	}

	private static final Map<Key, Result> CACHE = new HashMap<>();

	/**
	 * Intruders found for {@code owner} in this 40-tick window (one sector plus the member boxes): mobs inside the
	 * territory or within {@link #MAX_BAND} of its edge. Empty when the owner has no active hall in {@code level}.
	 */
	public static List<Mob> sweep(ServerLevel level, UUID owner) {
		long now = level.getGameTime();
		long window = now / PERIOD_TICKS;
		Key cacheKey = new Key(owner, level.dimension());
		Result cached = CACHE.get(cacheKey);
		if (cached != null && cached.window() == window) {
			return cached.mobs();
		}
		Halls.HallView hall = Halls.hallOf(owner);
		List<Mob> found = new ArrayList<>();
		if (hall != null && hall.active() && hall.dim().equals(level.dimension())) {
			Set<Mob> set = new LinkedHashSet<>();
			Vec3 c = Vec3.atCenterOf(hall.pos());
			double reach = hall.radius() + MAX_BAND;
			int sector = (int) (window % 4L);
			double minX = sector % 2 == 0 ? c.x - reach : c.x;
			double maxX = sector % 2 == 0 ? c.x : c.x + reach;
			double minZ = sector < 2 ? c.z - reach : c.z;
			double maxZ = sector < 2 ? c.z : c.z + reach;
			AABB box = new AABB(minX, c.y - Halls.TERRITORY_HEIGHT, minZ, maxX, c.y + Halls.TERRITORY_HEIGHT, maxZ);
			query(level, owner, box, set);
			for (ServerPlayer member : BUtil.onlineMembers(level.getServer(), owner)) {
				if (member.level() == level && Halls.inTerritory(owner, level, member.position())) {
					query(level, owner, member.getBoundingBox().inflate(MEMBER_BOX / 2.0D), set);
				}
			}
			found.addAll(set);
		}
		CACHE.put(cacheKey, new Result(window, found));
		if (CACHE.size() > 64) {
			CACHE.entrySet().removeIf(entry -> entry.getValue().window() < window - 2L);
		}
		return found;
	}

	private static void query(ServerLevel level, UUID owner, AABB box, Set<Mob> into) {
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box, mob -> AlarmEffect.intruder(level.getServer(), owner, mob));
		int kept = 0;
		for (Mob mob : mobs) {
			if (kept++ >= MAX_PER_QUERY) {
				break;
			}
			into.add(mob);
		}
	}

	/** Horizontal distance of {@code pos} outside {@code owner}'s territory edge (negative inside); NaN without a hall. */
	public static double outsideBy(UUID owner, Vec3 pos) {
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null) {
			return Double.NaN;
		}
		double dx = pos.x - (hall.pos().getX() + 0.5D);
		double dz = pos.z - (hall.pos().getZ() + 0.5D);
		return Math.sqrt(dx * dx + dz * dz) - hall.radius();
	}

	static void clear() {
		CACHE.clear();
	}

	private TerritorySweep() {
	}
}
