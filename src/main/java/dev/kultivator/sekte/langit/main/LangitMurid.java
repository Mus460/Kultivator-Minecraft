package dev.kultivator.sekte.langit.main;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.musuh.SectRaids;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.sekte.langit.api.SkyHallView;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Disciple life on the island (DESIGN 37.5.7, activities 26-27 of module 37G; the poses are module 37V's through
 * {@code SektePoses.register}). Both are transient pose activities (not spot-holding, never saved):
 * <ul>
 * <li>{@link LangitIds#ACT_JAGA_TEPI} 26, rim guard: a disciple in {@code GUARD_HALL} mode on a standing island walks to a
 * point inside the rim fence (the net pillars, the waterfall notches and the viewpoints, pulled {@link #INWARD} blocks
 * toward the throne) and stands there looking over the cloud sea for {@link #GAZE_SECONDS} seconds, then walks on.</li>
 * <li>{@link LangitIds#ACT_MEMANDANG} 27, watching the cloud sea: at dawn, at dusk and on festival nights 1-3 idle
 * disciples sit at the {@code meditasi_<i>} viewpoints until the window ends.</li>
 * </ul>
 * Nothing runs during a sect raid or a pirate raid (they fight), while no member is near, or with Sekte Langit off.
 * Checked every {@link #EVERY_SECONDS} seconds per hall; a disciple with any other activity is never taken.
 */
public final class LangitMurid {

	public static final int EVERY_SECONDS = 3;
	public static final int GAZE_SECONDS = 8;
	public static final double INWARD = 2.5D;
	public static final int WATCHERS = 3;
	public static final double ARRIVE = 2.2D;

	private record Job(int activity, Vec3 target, long until) {
	}

	private static final Map<UUID, Job> JOBS = new HashMap<>();
	private static int clock;

	static void clear() {
		JOBS.clear();
	}

	/** Once a second. */
	static void second(MinecraftServer server) {
		if (!LangitConfig.on() || ++clock % EVERY_SECONDS != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		boolean watchTime = LangitSites.dawn(level) || LangitSites.dusk(level);
		for (SkyHallView view : LangitApi.get().all(level)) {
			if (view.tethered() || LangitPeristiwa.present(server, view) == null) {
				continue;
			}
			UUID owner = view.owner();
			boolean fighting = SectRaids.active(owner) || LangitBajak.active(owner);
			boolean watch = watchTime || LangitPeristiwa.festival(server, owner);
			List<Vec3> rim = rimPoints(view);
			List<Vec3> seats = seats(view);
			int watchers = 0;
			for (DiscipleEntity disciple : Disciples.loaded(owner)) {
				if (disciple.level() != level || !view.inIsland(disciple.position()) || disciple.getY() < view.skyFloorY()) {
					continue;
				}
				Job job = JOBS.get(disciple.getUUID());
				if (fighting || disciple.getTarget() != null) {
					stop(disciple);
					continue;
				}
				if (job != null && job.activity() == LangitIds.ACT_MEMANDANG) {
					if (!watch) {
						stop(disciple);
					} else {
						watchers++;
						step(disciple, job, now);
					}
					continue;
				}
				if (job != null) {
					step(disciple, job, now);
					continue;
				}
				if (disciple.activity() != DiscipleEntity.ACTIVITY_NONE) {
					continue;
				}
				if (disciple.mode() == DiscipleEntity.Mode.GUARD_HALL && !rim.isEmpty()) {
					Vec3 target = rim.get(Math.floorMod(disciple.getUUID().hashCode() + (int) (now / 400L), rim.size()));
					start(disciple, new Job(LangitIds.ACT_JAGA_TEPI, target, 0L));
				} else if (watch && watchers < WATCHERS && !seats.isEmpty()) {
					Vec3 target = seats.get(watchers % seats.size());
					start(disciple, new Job(LangitIds.ACT_MEMANDANG, target, 0L));
					watchers++;
				}
			}
		}
		JOBS.keySet().removeIf(id -> level.getEntity(id) == null);
	}

	private static void start(DiscipleEntity disciple, Job job) {
		JOBS.put(disciple.getUUID(), job);
		disciple.getNavigation().moveTo(job.target().x, job.target().y, job.target().z, 0.8D);
	}

	/** Walking to the spot, then the pose; a rim guard moves on after its gaze. */
	private static void step(DiscipleEntity disciple, Job job, long now) {
		boolean there = disciple.position().distanceToSqr(job.target()) <= ARRIVE * ARRIVE;
		if (!there) {
			if (disciple.getNavigation().isDone()) {
				disciple.getNavigation().moveTo(job.target().x, job.target().y, job.target().z, 0.8D);
			}
			return;
		}
		if (disciple.activity() != job.activity()) {
			disciple.setActivity(job.activity(), null);
			JOBS.put(disciple.getUUID(), new Job(job.activity(), job.target(), now + GAZE_SECONDS * 20L));
			return;
		}
		if (job.activity() == LangitIds.ACT_JAGA_TEPI && job.until() > 0L && now >= job.until()) {
			stop(disciple);
		}
	}

	private static void stop(DiscipleEntity disciple) {
		JOBS.remove(disciple.getUUID());
		int activity = disciple.activity();
		if (activity == LangitIds.ACT_JAGA_TEPI || activity == LangitIds.ACT_MEMANDANG) {
			disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
	}

	/** Points inside the rim fence: net pillars, waterfall notches and viewpoints pulled toward the throne. */
	static List<Vec3> rimPoints(SkyHallView view) {
		List<Vec3> out = new ArrayList<>();
		Vec3 centre = Vec3.atBottomCenterOf(view.origin());
		for (Map.Entry<String, BlockPos> entry : LangitApi.get().anchors(view.owner()).entrySet()) {
			String id = entry.getKey();
			if (id.startsWith("jaring_") || id.startsWith("airterjun_") || id.startsWith("meditasi_")) {
				Vec3 at = Vec3.atBottomCenterOf(entry.getValue());
				Vec3 in = centre.subtract(at).multiply(1.0D, 0.0D, 1.0D);
				if (in.lengthSqr() > 1.0E-3D) {
					at = at.add(in.normalize().scale(INWARD));
				}
				out.add(new Vec3(at.x, Math.max(at.y, view.yP()), at.z));
			}
		}
		return out;
	}

	private static List<Vec3> seats(SkyHallView view) {
		List<Vec3> out = new ArrayList<>();
		for (Map.Entry<String, BlockPos> entry : LangitApi.get().anchors(view.owner()).entrySet()) {
			if (entry.getKey().startsWith("meditasi_")) {
				out.add(Vec3.atBottomCenterOf(entry.getValue()));
			}
		}
		return out;
	}

	private LangitMurid() {
	}
}
