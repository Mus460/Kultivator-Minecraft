package dev.kultivator.sekte.api;

import dev.kultivator.disciple.DiscipleData;

import java.util.UUID;

/**
 * Module G, Ujian Tangga Seribu (DESIGN 36.2.4, contract C36.G1): the thousand-step trial at the gate that the existing
 * recruitment day ({@code sect.hall.Candidates}) runs through when the stair component stands. The default has no stair.
 */
public interface TanggaService {

	TanggaService NONE = new TanggaService() {
	};

	/** True when the owner's hall has the stair (component {@code sx_tangga_*} DONE). */
	default boolean hasStair(UUID owner) {
		return false;
	}

	/** True while a climb runs (candidates on the stair). */
	default boolean running(UUID owner) {
		return false;
	}

	/** Steps reached 0-1000 recorded at recruitment in {@code extra.tangga} (0 when recruited without the trial). */
	default int steps(DiscipleData data) {
		return 0;
	}

	/** The "hati" verdict of the climb 0-3: 0 none, 1 gave up, 2 endured, 3 climbed past its limit (P reads it). */
	default int resolve(DiscipleData data) {
		return 0;
	}
}
