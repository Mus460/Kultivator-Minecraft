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

	/** True when X carries the hall's envoys to NPC sects as story missions (T13: one envoy path for module D). */
	default boolean carriesEnvoys() {
		return false;
	}

	/**
	 * Sends the disciple {@code soul} as the hall's envoy to NPC sect {@code sectId} (template {@code utusan}); tells the
	 * player why not and returns false when it cannot go. {@code officer}: the Elder of External Affairs.
	 */
	default boolean sendEnvoy(net.minecraft.server.level.ServerPlayer player, String sectId, UUID soul, boolean officer) {
		return false;
	}
}
