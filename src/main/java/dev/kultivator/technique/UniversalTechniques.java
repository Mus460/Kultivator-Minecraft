package dev.kultivator.technique;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Technique;
import net.minecraft.server.level.ServerPlayer;

/** STUB — owned by P2-techniques-a. The techniques without a Dao (DESIGN sections 8 and 15). */
public final class UniversalTechniques {

	/** Performs {@code technique} (qi and cooldown are handled by Techniques). True when it really happened. */
	public static boolean cast(ServerPlayer player, CultivationData data, Technique technique) {
		return false;
	}

	private UniversalTechniques() {
	}
}
