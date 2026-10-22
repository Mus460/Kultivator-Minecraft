package dev.kultivator.duel.api;

import dev.kultivator.duel.ai.DuelistProfile;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional interface for entities of other modules that fight sword duels (DESIGN 35.7.6), for example the wandering
 * cultivators of Dunia (DESIGN 34): the duel never depends on their classes. An entity implementing it is a duelist
 * without registering a provider; otherwise {@code DuelistRegistry.register(type, provider)} does the same from outside.
 * Server thread only.
 */
public interface Duelist {

	/** This duelist's profile now (style, rank, weapon, colour, kiai, Honour, stakes...). */
	DuelistProfile duelProfile();

	/** True when it accepts {@code challenger}'s duel (asked 20-40 ticks after the challenge, like a person would). */
	boolean acceptsDuel(ServerPlayer challenger);

	/** Told once when its official duel ends ({@code won} false for a loss, a draw or a cancel). */
	void onDuelEnd(boolean won);
}
