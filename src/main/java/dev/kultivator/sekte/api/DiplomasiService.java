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

	// ------------------------------------------------------------------ read helpers (T12 one list; 38, 39F)

	/** The sect master's title and name ("Ketua Mo Tianhe"), or null for an unknown sect. */
	default @Nullable Component master(String sectId) {
		return null;
	}

	/** The sect's alignment: 0 righteous (Lurus), 1 neutral (Netral), 2 demonic (Iblis); -1 for an unknown sect. */
	default int aliran(String sectId) {
		return -1;
	}

	/**
	 * The sect of alignment {@code aliran} (-1 = any) whose gate lies nearest {@code pos} (planned gate columns, so it works
	 * before any gate was generated), or null (DESIGN 38: "the nearest righteous sect" of bounties and ransoms).
	 */
	default @Nullable String nearest(BlockPos pos, int aliran) {
		return null;
	}

	/** The Saingan rival group that is the sect's travelling band ({@code "g<i>"} for sect {@code "s<i>"}), or null. */
	default @Nullable String rivalGroup(String sectId) {
		return null;
	}

	/** The sect whose travelling band is rival group {@code rivalId}, or null. */
	default @Nullable String sectOfRival(String rivalId) {
		return null;
	}

	/** The sect's ink colour (map border, band robes, name plates; the same colour Saingan gives its band). */
	default int color(String sectId) {
		return 0xB0B0B0;
	}

	/** True when the sect and the owner's hall are at war right now (38 Turnamen Agung skips them). */
	default boolean atWar(UUID owner, String sectId) {
		return false;
	}
}
