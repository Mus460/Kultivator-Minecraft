package dev.kultivator.sekte.langit.api;

import dev.kultivator.Kultivator;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sky site events fired by the build module (37B) for the other Sekte Langit modules (sync, festivals, story beats).
 * Server thread; a listener that throws is logged and skipped.
 */
public final class LangitEvents {

	/** What happened to a sky site. */
	public interface Listener {
		/** A sky site was confirmed (tethered) or founded directly on a sky stele. */
		default void founded(UUID owner, ServerLevel level) {
		}

		/** A tier of the sky site was completed (1-9). */
		default void tier(UUID owner, ServerLevel level, int tier) {
		}

		/** The Ascension ceremony finished: the hall stands on the island now. */
		default void ascended(UUID owner, ServerLevel level) {
		}

		/** The sky site is gone (moved down, released, reset). */
		default void removed(UUID owner) {
		}

		/** Something else visible changed (gate closed, invitation, anchors). */
		default void changed(UUID owner) {
		}
	}

	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

	public static void add(Listener listener) {
		if (listener != null) {
			LISTENERS.add(listener);
		}
	}

	public static void founded(UUID owner, ServerLevel level) {
		each(listener -> listener.founded(owner, level));
	}

	public static void tier(UUID owner, ServerLevel level, int tier) {
		each(listener -> listener.tier(owner, level, tier));
	}

	public static void ascended(UUID owner, ServerLevel level) {
		each(listener -> listener.ascended(owner, level));
	}

	public static void removed(UUID owner) {
		each(listener -> listener.removed(owner));
	}

	public static void changed(UUID owner) {
		each(listener -> listener.changed(owner));
	}

	private static void each(java.util.function.Consumer<Listener> call) {
		for (Listener listener : LISTENERS) {
			try {
				call.accept(listener);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A Sekte Langit listener failed", exception);
			}
		}
	}

	private LangitEvents() {
	}
}
