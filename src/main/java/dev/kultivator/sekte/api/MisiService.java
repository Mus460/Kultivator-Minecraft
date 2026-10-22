package dev.kultivator.sekte.api;

import java.util.List;
import java.util.UUID;

/**
 * Module X, Misi luar &amp; kepulangan (DESIGN 36.2.7, contract C36.X1): story missions to the outside world on top of
 * the existing {@code sect.event.Expeditions}/{@code AwayTeams} away mechanics. The default sends nobody.
 */
public interface MisiService {

	MisiService NONE = new MisiService() {
	};

	/** True while the soul is away on one of X's missions. */
	default boolean away(UUID owner, UUID soul) {
		return false;
	}

	/** The souls away on X's missions right now. */
	default List<UUID> awaySouls(UUID owner) {
		return List.of();
	}

	/** Missions finished by the owner's hall so far (E's report and the hub read it). */
	default int finished(UUID owner) {
		return 0;
	}
}
