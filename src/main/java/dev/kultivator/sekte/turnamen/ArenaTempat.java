package dev.kultivator.sekte.turnamen;

import dev.kultivator.sect.build.api.EngineApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Where the sect tournament is fought (DESIGN 36.2.3 "tempat"): the Lingkar Lei Tai. With the tribune component of
 * templates 6-9 built ({@link TribunKomponen}), the centre of the arena's stage ({@code turnamen_pusat}) and the tribune
 * seats ({@code kursi_<n>}); in templates 1-3 the plaza's arena mark ({@code arena}); otherwise the ring at the stele, as
 * before. Only reads the build engine's anchors and the blocks at the seats (to skip a seat a player built over); never
 * changes a block.
 */
public final class ArenaTempat {

	/** The anchor of the stage centre (standing level on the stage), set by {@link TribunKomponen}. */
	public static final String ANCHOR_CENTRE = "turnamen_pusat";
	/** Tribune seats {@code kursi_0 ..}; flag poles {@code panji_arena_0 ..} (module S may hang cloth there). */
	public static final String ANCHOR_SEAT = "kursi_";
	public static final String ANCHOR_FLAG = "panji_arena_";
	/** The plaza arena mark of templates 1-3. */
	public static final String ANCHOR_PLAZA = "arena";
	public static final int MAX_SEATS = 48;
	public static final int MAX_FLAGS = 8;
	/** The venue must lie this close (horizontally) to the stele. */
	public static final double MAX_DISTANCE = 96.0D;
	/** The ring radius of the lei tai (the Lingkar drawn by the client). */
	public static final float STAGE_RING = 6.0F;
	/** The old ring at the stele. */
	public static final float STELE_RING = 9.0F;

	/** The venue of the owner's tournaments: a stage centre, or null for the stele. */
	public static @Nullable Vec3 venue(UUID owner, ServerLevel level, Vec3 stele) {
		EngineApi engine = EngineApi.get();
		BlockPos centre = engine.anchor(owner, ANCHOR_CENTRE);
		if (centre == null && engine.template(owner) >= 1 && engine.template(owner) <= 3) {
			centre = engine.anchor(owner, ANCHOR_PLAZA);
		}
		if (centre == null || !level.isLoaded(centre)) {
			return null;
		}
		double dx = centre.getX() + 0.5D - stele.x;
		double dz = centre.getZ() + 0.5D - stele.z;
		if (dx * dx + dz * dz > MAX_DISTANCE * MAX_DISTANCE) {
			return null;
		}
		return Vec3.atBottomCenterOf(centre);
	}

	/** True when the owner's tribune is built (its centre anchor exists). */
	public static boolean hasTribune(UUID owner) {
		return EngineApi.get().anchor(owner, ANCHOR_CENTRE) != null;
	}

	/**
	 * The tribune seats (standing cells on the steps), the nearest to the stage first; a seat whose cell or head space is
	 * blocked now (a player built over it) is left out. Empty without a tribune.
	 */
	public static List<BlockPos> seats(UUID owner, ServerLevel level, Vec3 venue) {
		EngineApi engine = EngineApi.get();
		List<BlockPos> list = new ArrayList<>();
		for (int i = 0; i < MAX_SEATS; i++) {
			BlockPos seat = engine.anchor(owner, ANCHOR_SEAT + i);
			if (seat == null) {
				break;
			}
			if (!level.isLoaded(seat) || blocked(level, seat) || blocked(level, seat.above())) {
				continue;
			}
			list.add(seat);
		}
		list.sort((a, b) -> Double.compare(Vec3.atBottomCenterOf(a).distanceToSqr(venue), Vec3.atBottomCenterOf(b).distanceToSqr(venue)));
		return list;
	}

	/** The flag pole tops of the tribune ({@code panji_arena_<n>}); empty without one. */
	public static List<BlockPos> flags(UUID owner) {
		EngineApi engine = EngineApi.get();
		List<BlockPos> list = new ArrayList<>();
		for (int i = 0; i < MAX_FLAGS; i++) {
			BlockPos flag = engine.anchor(owner, ANCHOR_FLAG + i);
			if (flag == null) {
				break;
			}
			list.add(flag);
		}
		return list;
	}

	private static boolean blocked(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.getCollisionShape(level, pos).isEmpty();
	}

	private ArenaTempat() {
	}
}
