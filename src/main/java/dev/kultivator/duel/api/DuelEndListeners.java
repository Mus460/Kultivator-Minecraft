package dev.kultivator.duel.api;

import dev.kultivator.Kultivator;
import dev.kultivator.duel.session.DuelSession;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The end of every formal Sword Duel, told to other systems (DESIGN 36.4 module T, contract C36.T3). The session calls
 * {@link #fire} once from {@code DuelResults.settle} when it reaches ENDED, after the stakes, records, rewards and the
 * result card, and before the session is removed (so {@code DuelSessions.byEntity} still finds it inside a listener).
 * {@code winner} and {@code loser} are null for a draw or a cancelled duel; {@link #reason()} gives the result code
 * ({@code DuelIds.RES_*}) while a listener runs.
 * <p>
 * Module T (the sect tournament on the lei tai) uses it to read each bout's result; modules P and R may use it for their
 * rebellion and honour duels. A listener must be cheap (queue heavy work to its own tick) and must not start a new duel
 * from inside the call. A listener that throws is logged and skipped. Server thread only.
 */
public final class DuelEndListeners {

	/** One listener: the session that ended, its winner and loser (both null for a draw or a cancellation). */
	@FunctionalInterface
	public interface Listener {
		void onDuelEnd(DuelSession session, @Nullable LivingEntity winner, @Nullable LivingEntity loser);
	}

	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
	private static int reason = -1;

	/** Adds a listener (from a module's init). */
	public static void add(Listener listener) {
		if (listener != null) {
			LISTENERS.add(listener);
		}
	}

	public static void remove(Listener listener) {
		LISTENERS.remove(listener);
	}

	/** The result code ({@code DuelIds.RES_*}) of the duel being told right now, -1 outside a {@link #fire} call. */
	public static int reason() {
		return reason;
	}

	/** Called once by {@code DuelResults.settle} when a session ends (the one line module T added there). */
	public static void fire(DuelSession session, @Nullable LivingEntity winner, @Nullable LivingEntity loser, int resultReason) {
		if (session == null || LISTENERS.isEmpty()) {
			return;
		}
		int previous = reason;
		reason = resultReason;
		try {
			for (Listener listener : LISTENERS) {
				try {
					listener.onDuelEnd(session, winner, loser);
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("A duel end listener failed on session {}", session.id(), exception);
				}
			}
		} finally {
			reason = previous;
		}
	}

	private DuelEndListeners() {
	}
}
