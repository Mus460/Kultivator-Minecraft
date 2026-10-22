package dev.kultivator.client.duel;

import dev.kultivator.Kultivator;
import dev.kultivator.duel.net.DuelPayloads;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The client-side event bus of the sword duel (DESIGN 35.9, contract C35.U2). {@link DuelClient} receives every
 * server-to-client duel payload, updates its mirror first, then posts it here; the visual module (V, sparks, trails,
 * films), the animation module (A, poses) and U's own sound, crowd and HUD listen. Local predictions are posted with
 * {@code predicted = true}: a swing the local player just started (before the server echoes it) and the Deflect or
 * Perfect Deflect the defending client is sure of (35.8.2). When the server's {@code DuelClash} for a predicted result
 * arrives it is posted with {@code predicted = false} and the {@code DuelIds.CF_PREDICTED_MATCH} flag set when it matched
 * (listeners then skip re-playing it); a prediction that turned out wrong gets {@link Listener#correction} first, so the
 * predicted sparks can fade without a second sound.
 * <p>
 * Client thread only. A listener that throws is logged once and skipped; it never stops the others.
 */
public final class DuelBus {

	/** Every method has an empty default; implement only what you need. */
	public interface Listener {
		default void session(DuelPayloads.DuelSessionSync p) {
		}

		default void fighter(DuelPayloads.DuelFighterSync p) {
		}

		default void swing(DuelPayloads.DuelSwing p, boolean predicted) {
		}

		default void clash(DuelPayloads.DuelClash p, boolean predicted) {
		}

		default void bind(DuelPayloads.DuelBindSync p) {
		}

		default void result(DuelPayloads.DuelResult p) {
		}

		default void preview(DuelPayloads.DuelPreview p) {
		}

		/** A predicted result ({@code swingId} of the attacker) was wrong; the true {@code outcome} follows as a clash. */
		default void correction(int swingId, int outcome) {
		}
	}

	private static final List<Listener> LISTENERS = new ArrayList<>();
	private static final List<Listener> BROKEN_LOGGED = new ArrayList<>();

	/** Adds a listener (once; adding the same instance twice has no effect). */
	public static void register(Listener listener) {
		if (listener != null && !LISTENERS.contains(listener)) {
			LISTENERS.add(listener);
		}
	}

	/** Removes a listener (tests and previews). */
	public static void unregister(Listener listener) {
		LISTENERS.remove(listener);
	}

	static void session(DuelPayloads.DuelSessionSync p) {
		post(l -> l.session(p));
	}

	static void fighter(DuelPayloads.DuelFighterSync p) {
		post(l -> l.fighter(p));
	}

	static void swing(DuelPayloads.DuelSwing p, boolean predicted) {
		post(l -> l.swing(p, predicted));
	}

	static void clash(DuelPayloads.DuelClash p, boolean predicted) {
		post(l -> l.clash(p, predicted));
	}

	static void bind(DuelPayloads.DuelBindSync p) {
		post(l -> l.bind(p));
	}

	static void result(DuelPayloads.DuelResult p) {
		post(l -> l.result(p));
	}

	static void preview(DuelPayloads.DuelPreview p) {
		post(l -> l.preview(p));
	}

	static void correction(int swingId, int outcome) {
		post(l -> l.correction(swingId, outcome));
	}

	private static void post(Consumer<Listener> call) {
		// A copy: a listener may register another while handling an event.
		for (Listener listener : List.copyOf(LISTENERS)) {
			try {
				call.accept(listener);
			} catch (RuntimeException | LinkageError error) {
				if (!BROKEN_LOGGED.contains(listener)) {
					BROKEN_LOGGED.add(listener);
					Kultivator.LOGGER.warn("Kultivator duel: a listener failed ({}); further failures of it are not logged",
							listener.getClass().getName(), error);
				}
			}
		}
	}

	private DuelBus() {
	}
}
