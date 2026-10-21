package dev.kultivator.dunia.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Module B's spirit beast evolution (DESIGN 34.5.12, contract C34.B1). The default: no beast evolves. */
public interface BeastEvolutionService {

	BeastEvolutionService NONE = new BeastEvolutionService() {
	};

	/** The beast's tier 0-6 (0 for anything that is no spirit beast). */
	default int tier(LivingEntity beast) {
		return 0;
	}

	/** The beast's evolution line id, or "" for none. */
	default String line(LivingEntity beast) {
		return "";
	}

	default boolean canEvolve(ServerPlayer owner, LivingEntity beast) {
		return false;
	}

	default boolean startTribulation(ServerPlayer owner, LivingEntity beast, boolean test) {
		return false;
	}

	/** Gives {@code player} one of B's items by short id (D's Dragon Pearl reward); no-op when unknown. */
	default void grantItem(ServerPlayer player, String what) {
	}
}
