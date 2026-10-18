package dev.kultivator.formasi.core;

import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.effect.CoreContextImpl;
import dev.kultivator.formasi.effect.FormationTargets;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.stack.Stacking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The shared intruder snapshot (DESIGN 24.2.12): every 20 ticks, per owner and dimension, at most
 * {@link #MAX_QUERIES} AABB queries over the union boxes of overlapping clusters of that owner's running cores (radius
 * ≤ 48). {@code CoreContext.hostiles} filters the cached list by distance; no formation runs its own overlapping scan. The
 * snapshot is built lazily on the first request of a window, so nothing is scanned while no handler asks.
 */
public final class IntruderSnapshot {

	public static final int MAX_QUERIES = 3;
	public static final int WINDOW_TICKS = 20;
	/** At most this many mobs are kept per owner snapshot. */
	public static final int MAX_MOBS = 96;

	private record Snapshot(long window, List<Mob> mobs) {
	}

	private static final Map<String, Snapshot> SNAPSHOTS = new HashMap<>();
	private static int queries;

	/** Intruders inside {@code ctx}'s field, nearest first, at most {@code max}. */
	public static List<Mob> hostiles(CoreContextImpl ctx, int max) {
		if (max <= 0) {
			return List.of();
		}
		ServerLevel level = ctx.level();
		long window = level.getGameTime() / WINDOW_TICKS;
		String key = ctx.owner() + "|" + level.dimension().identifier();
		Snapshot snapshot = SNAPSHOTS.get(key);
		if (snapshot == null || snapshot.window() != window) {
			snapshot = new Snapshot(window, scan(level, ctx.owner()));
			SNAPSHOTS.put(key, snapshot);
		}
		Vec3 centre = ctx.centre();
		List<Mob> out = new ArrayList<>();
		for (Mob mob : snapshot.mobs()) {
			if (mob.isAlive() && !mob.isRemoved() && ctx.covers(mob.position()) && FormationTargets.mayAffect(mob, ctx)) {
				out.add(mob);
			}
		}
		out.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(centre)));
		return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
	}

	private static List<Mob> scan(ServerLevel level, UUID owner) {
		List<AABB> boxes = new ArrayList<>();
		for (CoreRecord record : CoreTicker.store(level).all()) {
			FormationSpec spec = FormationRegistry.byId(record.spec);
			if (spec == null || !record.owner.equals(owner) || !record.state.running() || !hostile(spec.family())) {
				continue;
			}
			double r = Math.min(Stacking.RADIUS_CAP, record.radius);
			double dy = CoreTicker.dyLimit(spec, record);
			Vec3 c = Vec3.atCenterOf(record.eye);
			AABB box = new AABB(c.x - r, c.y - dy, c.z - r, c.x + r, c.y + dy, c.z + r);
			boolean merged = false;
			for (int i = 0; i < boxes.size(); i++) {
				if (boxes.get(i).intersects(box)) {
					boxes.set(i, boxes.get(i).minmax(box));
					merged = true;
					break;
				}
			}
			if (!merged) {
				if (boxes.size() < MAX_QUERIES) {
					boxes.add(box);
				} else {
					boxes.set(MAX_QUERIES - 1, boxes.get(MAX_QUERIES - 1).minmax(box));
				}
			}
		}
		List<Mob> mobs = new ArrayList<>();
		for (AABB box : boxes) {
			queries++;
			for (Mob mob : level.getEntitiesOfClass(Mob.class, box, mob -> mob.isAlive() && !FormationTargets.neverAffected(mob))) {
				if (mobs.size() >= MAX_MOBS) {
					break;
				}
				if (!mobs.contains(mob)) {
					mobs.add(mob);
				}
			}
		}
		return mobs;
	}

	/** Families whose handlers look for intruders. */
	public static boolean hostile(Family family) {
		return switch (family) {
			case PELINDUNG, PERINGATAN, PENYEMBUNYI, ILUSI, KENDALI, PEMBUNUH, GABUNGAN -> true;
			default -> false;
		};
	}

	/** Snapshot queries since the last call (performance stats). */
	public static int takeQueries() {
		int count = queries;
		queries = 0;
		return count;
	}

	/** Drops old windows (every 20 ticks) and everything on server stop. */
	public static void prune(long gameTime) {
		long window = gameTime / WINDOW_TICKS;
		SNAPSHOTS.values().removeIf(snapshot -> window - snapshot.window() > 1L);
	}

	public static void clear() {
		SNAPSHOTS.clear();
	}

	private IntruderSnapshot() {
	}
}
