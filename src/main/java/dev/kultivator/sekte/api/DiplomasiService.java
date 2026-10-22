package dev.kultivator.sekte.api;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Module D, Diplomasi &amp; Sekte Rival (DESIGN 36.2.9, contract C36.D1): the NPC sects of the world (seeded, data
 * first, their buildings only from world generation in new chunks), each sect's standing with each player hall, and the
 * alliances and wars. The default knows no NPC sects.
 */
public interface DiplomasiService {

	DiplomasiService NONE = new DiplomasiService() {
	};

	/** Every NPC sect id of the world. */
	default List<String> sects() {
		return List.of();
	}

	default @Nullable Component name(String sectId) {
		return null;
	}

	default Lambang emblem(String sectId) {
		return Lambang.DEFAULT;
	}

	/** The sect's tier 1-9 (the same scale as the player's templates) and so its strength. */
	default int tier(String sectId) {
		return 0;
	}

	/** Standing -100..100 between the owner's hall and the sect. */
	default int standing(UUID owner, String sectId) {
		return 0;
	}

	default Sikap stance(UUID owner, String sectId) {
		return Sikap.NETRAL;
	}

	/** Changes the standing (T after an inter-sect tournament, X after a mission met them), reason {@code <module>.<what>}. */
	default void adjust(UUID owner, String sectId, int delta, String reason) {
	}

	/** The sect's mountain gate (its worldgen structure) if known, or null. */
	default @Nullable BlockPos gate(String sectId) {
		return null;
	}

	/**
	 * First contact of the owner's hall with the sect (T12/R7, for Sekte Langit 37 and other modules): marks it met, grants
	 * {@code diplomasi_temu} and reveals its gate on the map when the gate exists. Idempotent.
	 */
	default void meet(UUID owner, String sectId) {
	}

	/** True when the owner's hall already met the sect (a gate visit, an envoy, a mission, a tournament). */
	default boolean met(UUID owner, String sectId) {
		return false;
	}

	/** The sect's planned gate column (x, 0, z) even before the gate was generated, or null (map domains, T12). */
	default @Nullable BlockPos plannedGate(String sectId) {
		return null;
	}
}
