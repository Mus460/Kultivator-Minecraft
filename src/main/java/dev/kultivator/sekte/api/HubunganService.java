package dev.kultivator.sekte.api;

import java.util.List;
import java.util.UUID;

/**
 * Module R, Hubungan antar-murid (DESIGN 36.2.2, contract C36.R1): the pair table of one hall (souls a/b, unordered),
 * kept in R's hall section {@code sekte_hubungan}. Others change a pair only through {@link #adjust}, with a reason key,
 * so R can cap per-day changes and write the memories; R fires {@code SekteBus.RELATION_CHANGED} when the kind changes.
 * The default knows no pairs.
 */
public interface HubunganService {

	HubunganService NONE = new HubunganService() {
	};

	/** Affinity -100..100 of the pair (0 unknown). */
	default int affinity(UUID owner, UUID a, UUID b) {
		return 0;
	}

	default Ikatan kind(UUID owner, UUID a, UUID b) {
		return Ikatan.NONE;
	}

	/**
	 * Changes the pair's affinity by {@code delta} (R clamps it to +-15 per call and +-25 per pair per day), reason
	 * {@code <module>.<what>} (also the memory kind).
	 */
	default void adjust(UUID owner, UUID a, UUID b, int delta, String reason) {
	}

	/** The souls tied to {@code soul} by {@code kind}, strongest first. */
	default List<UUID> with(UUID owner, UUID soul, Ikatan kind) {
		return List.of();
	}

	/** The sum of the soul's positive (+) and negative (-) ties, -100..100 (P reads it as a contentment drift). */
	default int socialBalance(UUID owner, UUID soul) {
		return 0;
	}
}
