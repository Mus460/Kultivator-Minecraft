package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The places of module 39B (DESIGN 39.5.2 step 3, 39.5.7, 39.4.2): an open spot under the sky for a breakthrough, a quiet
 * seat for seclusion, a view for contemplation, a guard's post around a friend. Read only (never a block change), from
 * the hall's named anchors ({@code arena}, {@code main_hall}, {@code library}) and the stele, plus the spot the planner's
 * point cache suggests; at most a few dozen block reads per question (no world scan). A spot is a cell whose own and
 * upper block have no collision, no fluid, and a floor.
 */
final class TempatTumbuh {

	/** Offsets tried around each anchor (rings of 0, 3, 5 and 7 blocks). */
	private static final int[][] CINCIN = {{0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}, {5, 2}, {-5, -2}, {2, -5}, {-2, 5},
			{7, 0}, {-7, 0}, {0, 7}, {0, -7}};
	/** The eight directions of a view search. */
	private static final int[][] ARAH = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

	/** An open spot under the sky for a breakthrough (39.5.2): the suggestion, the arena, the main hall's yard, the stele. */
	static @Nullable BlockPos terbuka(ServerLevel level, DiscipleEntity entity, @Nullable BlockPos saran) {
		for (BlockPos centre : pusat(entity, saran, "arena", "main_hall")) {
			for (int[] o : CINCIN) {
				BlockPos p = stand(level, centre.offset(o[0], 0, o[1]));
				if (p != null && level.canSeeSky(p.above())) {
					return p;
				}
			}
		}
		return null;
	}

	/** A quiet seat for seclusion (39.5.7): the suggestion, the measured best seat, the library, the main hall, here. */
	static @Nullable BlockPos tenang(ServerLevel level, DiscipleEntity entity, @Nullable BlockPos saran) {
		BlockPos best = null;
		long packed = MandiriApi.bagian(entity.data()).getCompoundOrEmpty(MandiriIds.K_TEMPAT_TERBAIK).getLongOr("pos", Long.MIN_VALUE);
		if (packed != Long.MIN_VALUE) {
			best = BlockPos.of(packed);
		}
		List<BlockPos> list = new ArrayList<>();
		if (saran != null) {
			list.add(saran);
		}
		if (best != null) {
			list.add(best);
		}
		list.addAll(pusat(entity, null, "library", "main_hall"));
		for (BlockPos centre : list) {
			for (int i = 0; i < 5 && i < CINCIN.length; i++) {
				BlockPos p = stand(level, centre.offset(CINCIN[i][0], 0, CINCIN[i][1]));
				if (p != null) {
					return p;
				}
			}
		}
		return null;
	}

	/**
	 * A view for contemplation (39.4.2 MERENUNG: a cliff, the island's edge): eight directions at 10 and 16 blocks from the
	 * stele (or from the disciple), the highest open spot wins.
	 */
	static @Nullable BlockPos pandangan(ServerLevel level, DiscipleEntity entity, @Nullable BlockPos saran) {
		if (saran != null) {
			BlockPos p = stand(level, saran);
			if (p != null) {
				return p;
			}
		}
		UUID owner = entity.ownerId();
		Halls.HallView hall = owner == null ? null : Halls.hallOf(owner);
		BlockPos centre = hall != null && hall.dim().equals(level.dimension()) ? hall.pos() : entity.blockPosition();
		BlockPos best = null;
		int start = Math.floorMod(entity.getId(), ARAH.length);
		for (int r : new int[]{10, 16}) {
			for (int i = 0; i < ARAH.length; i++) {
				int[] d = ARAH[(start + i) % ARAH.length];
				BlockPos column = centre.offset(d[0] * r, 0, d[1] * r);
				if (!level.isLoaded(column)) {
					continue;
				}
				int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
				BlockPos p = stand(level, new BlockPos(column.getX(), top, column.getZ()));
				if (p != null && level.canSeeSky(p.above()) && (best == null || p.getY() > best.getY())) {
					best = p;
				}
			}
			if (best != null) {
				return best;
			}
		}
		return stand(level, entity.blockPosition());
	}

	/** A guard's post 4-6 blocks from {@code friend}, on the side the guard comes from (39.5.2 Hu Fa). */
	static @Nullable BlockPos jaga(ServerLevel level, BlockPos friend, DiscipleEntity guard, int index) {
		double angle = Math.atan2(guard.getZ() - friend.getZ(), guard.getX() - friend.getX()) + index * 2.2D;
		for (int r : new int[]{5, 4, 6}) {
			for (int k = 0; k < 6; k++) {
				double a = angle + k * Math.PI / 3.0D;
				BlockPos p = stand(level, friend.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r)));
				if (p != null) {
					return p;
				}
			}
		}
		return null;
	}

	/** A seat 2-3 blocks in front of a teacher for a listening junior. */
	static @Nullable BlockPos depan(ServerLevel level, BlockPos teacher, int index) {
		int[][] seats = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}};
		for (int i = 0; i < seats.length; i++) {
			int[] o = seats[(index + i) % seats.length];
			BlockPos p = stand(level, teacher.offset(o[0], 0, o[1]));
			if (p != null) {
				return p;
			}
		}
		return null;
	}

	/** The search centres: the suggestion, the named anchors of the hall, the stele, the disciple itself. */
	private static List<BlockPos> pusat(DiscipleEntity entity, @Nullable BlockPos saran, String... anchors) {
		List<BlockPos> list = new ArrayList<>(anchors.length + 3);
		if (saran != null) {
			list.add(saran);
		}
		UUID owner = entity.ownerId();
		if (owner != null) {
			for (String id : anchors) {
				BlockPos anchor = SectBuild.anchor(owner, id);
				if (anchor != null) {
					list.add(anchor);
				}
			}
			Halls.HallView hall = Halls.hallOf(owner);
			if (hall != null && hall.dim().equals(entity.level().dimension())) {
				list.add(hall.pos().south(4));
			}
		}
		list.add(entity.blockPosition());
		return list;
	}

	/** A standable cell at, one above or one below {@code pos}, or null. */
	static @Nullable BlockPos stand(ServerLevel level, BlockPos pos) {
		for (int dy : new int[]{0, 1, -1}) {
			BlockPos p = pos.offset(0, dy, 0);
			if (standable(level, p)) {
				return p;
			}
		}
		return null;
	}

	static boolean standable(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState at = level.getBlockState(pos);
		BlockState head = level.getBlockState(pos.above());
		BlockState floor = level.getBlockState(pos.below());
		return at.getCollisionShape(level, pos).isEmpty() && head.getCollisionShape(level, pos.above()).isEmpty()
				&& level.getFluidState(pos).isEmpty() && !floor.getCollisionShape(level, pos.below()).isEmpty();
	}

	private TempatTumbuh() {
	}
}
