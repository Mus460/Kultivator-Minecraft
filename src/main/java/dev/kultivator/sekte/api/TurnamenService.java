package dev.kultivator.sekte.api;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Module T, Turnamen sekte (DESIGN 36.2.3, contract C36.T1): the arena tournament built on the existing
 * {@code sect.event.Tournament} schedule and the Sword Duel ({@code duel}) bouts. The default runs nothing.
 */
public interface TurnamenService {

	TurnamenService NONE = new TurnamenService() {
	};

	/** True while a tournament (any kind) runs at the owner's hall. */
	default boolean running(UUID owner) {
		return false;
	}

	/** The reigning champion's soul, or null. */
	default @Nullable UUID champion(UUID owner) {
		return null;
	}

	/**
	 * Hosts an inter-sect tournament against NPC sect {@code guestSect} (module D's challenge); returns false when one is
	 * running, the hall has no arena or fewer than 2 disciples are free.
	 */
	default boolean hostGuests(ServerPlayer owner, String guestSect) {
		return false;
	}

	/** True when {@code soul} is busy in a bout or lined up for one (X and W leave it alone then). */
	default boolean busy(UUID owner, UUID soul) {
		return false;
	}
}
