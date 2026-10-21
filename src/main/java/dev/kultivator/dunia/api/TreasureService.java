package dev.kultivator.dunia.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Module L's treasure births (DESIGN 34.4.3, contract C34.L1). The default spawns nothing. */
public interface TreasureService {

	TreasureService NONE = new TreasureService() {
	};

	/** Starts a birth at {@code pos} (R on a re-seal, D on a sea treasure, K's test race); null when refused. */
	default @Nullable TreasureBirth spawn(ServerLevel level, BlockPos pos, int tier, String kind) {
		return null;
	}

	/** The live births of {@code level}. */
	default List<TreasureBirth> live(ServerLevel level) {
		return List.of();
	}

	/** Claims birth {@code id} for {@code player}; false when it is gone or already claimed. */
	default boolean claim(ServerPlayer player, long id) {
		return false;
	}

	/**
	 * Claims birth {@code id} for a rival group (K's race, DESIGN 34.5.3: "claims it with its rival id"): the treasure
	 * leaves without a player winner and L fires {@code TREASURE_CLAIMED} with {@code rivalId}. False when it is gone or
	 * already claimed. The default (L off) refuses.
	 */
	default boolean claimByRival(ServerLevel level, long id, String rivalId) {
		return false;
	}
}
