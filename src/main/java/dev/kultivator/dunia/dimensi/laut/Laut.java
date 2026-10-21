package dev.kultivator.dunia.dimensi.laut;

import dev.kultivator.dunia.dimensi.DimensiLevels;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Laut Tanpa Batas's part of module D (DESIGN 34.5.14): the entries and returns ({@link SeaGate}), the anchors
 * ({@link LautAnchors}), the people ({@link SeaPeople}) and the monsters and the turtle ({@link SeaMonsters}). Called by
 * {@code Dimensi}.
 */
public final class Laut {

	public static void init() {
		SeaGate.init();
		SeaMonsters.init();
	}

	public static void clear() {
		LautAnchors.clear();
		SeaGate.reset();
	}

	public static void tick(MinecraftServer server, long ticks) {
		SeaMonsters.tick(server, ticks);
		if (ticks % 20L == 9L) {
			ServerLevel sea = DimensiLevels.laut(server);
			if (sea != null) {
				for (ServerPlayer player : sea.players()) {
					SeaGate.breathingTick(player);
				}
			}
		}
	}

	private Laut() {
	}
}
