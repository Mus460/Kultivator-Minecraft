package dev.kultivator.sekte.paviliun;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.SekteApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Places of module A (DESIGN 36.2.5): where the pavilion's readers sit around the {@code library} anchor, where the rites
 * gather (the Altar Leluhur, the cemetery's {@code makam_pusat}, the stele, else the hall stele), and the shared "is this
 * disciple free" test. Only reads blocks (never changes one): a spot is a cell whose own and upper block have no collision
 * and no fluid, standing on a solid block, in a loaded chunk.
 */
final class Tempat {

	/** The reading seats around the library anchor, nearest first (template-independent ring offsets). */
	private static final int[][] SEATS = {{-2, 0}, {2, 0}, {0, 2}, {-1, 2}, {1, 2}, {-2, 1}, {2, 1}, {-2, -1}, {2, -1}, {0, -2}, {-3, 0}, {3, 0},
			{-2, 2}, {2, 2}, {-1, -2}, {1, -2}, {0, 3}, {-3, 1}, {3, 1}};

	/** A standable cell at or within one block up or down of {@code pos}, or null. */
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

	/** A free reading seat around {@code library} not in {@code taken}, or null. */
	static @Nullable BlockPos seat(ServerLevel level, BlockPos library, Collection<BlockPos> taken) {
		for (int[] o : SEATS) {
			BlockPos p = stand(level, library.offset(o[0], 0, o[1]));
			if (p != null && !taken.contains(p)) {
				return p;
			}
		}
		return null;
	}

	/**
	 * The gathering point of the rites: the altar's kneeling cell, else the cemetery path ({@code makam_pusat}), else the
	 * memorial stele's face, else the hall stele. Null without a hall.
	 */
	static @Nullable BlockPos riteCentre(UUID owner) {
		BlockPos altar = SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_ALTAR);
		if (altar != null) {
			return altar;
		}
		BlockPos centre = SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_CENTER);
		if (centre != null) {
			return centre;
		}
		BlockPos stele = SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_STELE);
		if (stele != null) {
			return stele;
		}
		Halls.HallView hall = Halls.hallOf(owner);
		return hall == null ? null : hall.pos().south(3);
	}

	/** The side the altar opens to (from its table toward its kneeling cell), or null without an altar. */
	static @Nullable Direction front(UUID owner) {
		BlockPos kneel = SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_ALTAR);
		BlockPos table = SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_TABLE);
		if (kneel == null || table == null) {
			return null;
		}
		int dx = kneel.getX() - table.getX();
		int dz = kneel.getZ() - table.getZ();
		if (dx == 0 && dz == 0) {
			return null;
		}
		return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	/** Where incense smoke rises during a rite: the censer, else the stele face, else the rite centre. */
	static @Nullable BlockPos censer(UUID owner) {
		BlockPos censer = SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_CENSER);
		if (censer != null) {
			return censer;
		}
		BlockPos stele = SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_STELE);
		return stele != null ? stele : riteCentre(owner);
	}

	/**
	 * Up to {@code count} standable spots in rows behind {@code centre}, all facing it: rows of five, two blocks apart,
	 * starting {@code first} blocks out, the direction chosen where the ground is open.
	 */
	static List<BlockPos> rows(ServerLevel level, BlockPos centre, int count, int first) {
		return rows(level, centre, count, first, null);
	}

	/**
	 * {@link #rows(ServerLevel, BlockPos, int, int)} preferring {@code front} (the side the altar opens to): it is kept
	 * when it holds at least 60% of the rows, else the most open side wins.
	 */
	static List<BlockPos> rows(ServerLevel level, BlockPos centre, int count, int first, @Nullable Direction front) {
		List<BlockPos> best = List.of();
		List<Direction> order = new ArrayList<>(List.of(Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST));
		if (front != null && front.getAxis().isHorizontal()) {
			order.remove(front);
			order.add(0, front);
		}
		for (Direction away : order) {
			List<BlockPos> spots = new ArrayList<>();
			Direction across = away.getClockWise();
			for (int row = 0; row < 6 && spots.size() < count; row++) {
				for (int i = 0; i < 5 && spots.size() < count; i++) {
					int side = (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2);
					BlockPos p = centre.relative(away, first + row * 2).relative(across, side * 2);
					BlockPos s = stand(level, p);
					if (s != null && !spots.contains(s)) {
						spots.add(s);
					}
				}
			}
			if (away == front && spots.size() * 10 >= count * 6) {
				return spots;
			}
			if (spots.size() > best.size()) {
				best = spots;
			}
			if (best.size() >= count) {
				break;
			}
		}
		return best;
	}

	/**
	 * Free for A's study and rites: a living listed disciple, idle (no activity; {@code allow} also passes), not following
	 * its master ({@code anyOrder} lets followers join a rite), not fighting, breaking through, duelling, sulking, on a
	 * tournament, a shift or a mission, inside the hall territory.
	 */
	static boolean free(DiscipleEntity entity, boolean anyOrder, int allow) {
		if (entity.isRemoved() || !entity.isAlive() || entity.role() != DiscipleEntity.Role.DISCIPLE || entity.isReleasing()) {
			return false;
		}
		int activity = entity.activity();
		if (activity != DiscipleEntity.ACTIVITY_NONE && activity != allow) {
			return false;
		}
		DiscipleEntity.Mode mode = entity.mode();
		if (!anyOrder && (mode == DiscipleEntity.Mode.FOLLOW || mode == DiscipleEntity.Mode.ASSIST)) {
			return false;
		}
		DiscipleData data = entity.data();
		UUID owner = entity.ownerId();
		if (owner == null || entity.getTarget() != null || data.breakingThrough() || DuelRules.inDuel(entity)) {
			return false;
		}
		UUID soul = entity.soulId();
		return !SekteApi.hati().defiant(data) && !SekteApi.turnamen().busy(owner, soul) && !SekteApi.kebun().working(owner, soul)
				&& !SekteApi.misi().away(owner, soul) && Halls.inTerritory(owner, entity.level(), entity.position());
	}

	/** True when the activity belongs to A (reading or kneeling). */
	static boolean ours(int activity) {
		return activity == SekteIds.ACT_STUDY || activity == SekteIds.ACT_MOURN;
	}

	private Tempat() {
	}
}
