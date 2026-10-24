package dev.kultivator.sekte.mandiri;

import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The walking budget (DESIGN 39.4.6, 39.9): at most {@code mandiri.gerakMaks} disciples of one hall walk to a planned
 * place at the same time. A disciple whose chosen place is farther than {@link #DEKAT} blocks counts as walking until it
 * arrives; when the budget is full the planner prefers what can be done where the disciple stands (the next best activity
 * whose place is near, or the activity in place). The walk itself is the old {@code ReturnToPostGoal} of the activity
 * spot (path recalculated once a second, a flash back over 48 blocks), so no new navigation runs here.
 */
public final class AnggaranGerak {

	/** Within this many blocks a place needs no walk. */
	public static final double DEKAT = 3.0D;

	private record Hitung(long tick, int jalan) {
	}

	private static final Map<UUID, Hitung> CACHE = new ConcurrentHashMap<>();

	/** True when the disciple may walk to {@code tujuan} now (near places are always allowed). */
	public static boolean boleh(DiscipleEntity murid, BlockPos tujuan) {
		if (tujuan == null || dekat(murid, tujuan)) {
			return true;
		}
		UUID owner = murid.ownerId();
		return owner == null || berjalan(owner, murid.level().getGameTime()) < MandiriConfig.get().gerakMaks;
	}

	public static boolean dekat(DiscipleEntity murid, BlockPos tujuan) {
		return murid.distanceToSqr(tujuan.getX() + 0.5D, tujuan.getY(), tujuan.getZ() + 0.5D) <= DEKAT * DEKAT;
	}

	/** Disciples of the hall walking to a planned place now (counted once per tick). */
	public static int berjalan(UUID owner, long now) {
		Hitung cached = CACHE.get(owner);
		if (cached != null && cached.tick() == now) {
			return cached.jalan();
		}
		int count = 0;
		for (DiscipleEntity d : Perencana.dimuat(owner)) {
			if (Perencana.milikku(d)) {
				BlockPos spot = d.activitySpot();
				if (spot != null && !dekat(d, spot)) {
					count++;
				}
			}
		}
		CACHE.put(owner, new Hitung(now, count));
		return count;
	}

	/** Called right after the planner sent one more disciple walking, so the same tick counts it. */
	static void tambah(UUID owner, long now) {
		Hitung cached = CACHE.get(owner);
		if (cached != null && cached.tick() == now) {
			CACHE.put(owner, new Hitung(now, cached.jalan() + 1));
		}
	}

	static void kosongkan() {
		CACHE.clear();
	}

	private AnggaranGerak() {
	}
}
