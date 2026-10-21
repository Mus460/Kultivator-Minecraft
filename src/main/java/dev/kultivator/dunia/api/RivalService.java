package dev.kultivator.dunia.api;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Module K's rival cultivator groups (DESIGN 34.5.3, contract C34.K1). The default has no rivals. */
public interface RivalService {

	RivalService NONE = new RivalService() {
	};

	/** The ids of the rival groups whose home region lies within {@code radius} blocks of {@code pos}. */
	default List<String> nearRivals(ServerLevel level, BlockPos pos, int radius) {
		return List.of();
	}

	/** Sends the groups near {@code birth} racing to it (K listens to TREASURE_BORN itself; this is the manual entry). */
	default void raceTo(ServerLevel level, TreasureBirth birth) {
	}

	/** A rival band (leader + members) ambushes {@code player}; {@code tier} 1-4 sets its strength (T's sky pirates). */
	default void ambush(ServerPlayer player, int tier) {
	}

	/** The group's display name ("Keluarga Mo dari Lembah Hitam"), or null for an unknown id. */
	default @Nullable Component name(String rivalId) {
		return null;
	}

	/** Every group id of the world (empty when off); T's NPC sect domains align with these. */
	default List<String> all() {
		return List.of();
	}

	/** The seeded centre of a group's home region (T draws the domain there), or null. */
	default @Nullable BlockPos region(String rivalId) {
		return null;
	}

	/** The group's colour (robes, descent trail, map ink). */
	default int color(String rivalId) {
		return 0xB0B0B0;
	}
}
