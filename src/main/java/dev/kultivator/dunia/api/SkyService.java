package dev.kultivator.dunia.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Module L's sky and weather service (DESIGN 34.4.3, contract C34.L1). The default answers "nothing happens". */
public interface SkyService {

	SkyService NONE = new SkyService() {
	};

	/** The sky event running in {@code level} right now, or null. */
	default @Nullable SkyKind active(ServerLevel level) {
		return null;
	}

	/** The spirit weather of the cell around {@code pos}. */
	default SpiritWeather weatherAt(ServerLevel level, BlockPos pos) {
		return SpiritWeather.CLEAR;
	}

	/** The meditation site factor L adds for this player (1 = none). */
	default double siteFactor(ServerPlayer player) {
		return 1.0D;
	}
}
