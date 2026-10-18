package dev.kultivator.formasi.integrate;

import dev.kultivator.cultivation.Meditation;
import dev.kultivator.formasi.stack.FormationField;
import dev.kultivator.formasi.stack.Stacking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Gathering for players (DESIGN 24.5, 24.13 item 8): the sect term {@code G = min(1.5, max(K2, B5) × M5)}, the formation
 * stack cap (2.6, 2.8 with A1) and the vein drain factor (≥ 0.7), read from the cached field.
 */
public final class MeditationBridge {

	public static void init() {
		Meditation.addGatheringProvider(player -> field(player).sectGathering());
		Meditation.addStackCapProvider(player -> field(player).stackCap());
		Meditation.addDrainFactorProvider(player -> field(player).drain());
	}

	static FormationField field(ServerPlayer player) {
		return player.level() instanceof ServerLevel level ? Stacking.fieldAt(level, player.position(), player) : FormationField.NONE;
	}

	private MeditationBridge() {
	}
}
