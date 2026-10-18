package dev.kultivator.sect.build.api;

import dev.kultivator.Kultivator;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Construction events (DESIGN 21.24): implemented by the flow module ({@code sect.build.flow.Ceremonies}) for the
 * ceremonies, missions and messages, fired by the engine on the server thread through {@link #fire()}. Every method has
 * an empty default, so a listener overrides only what it needs.
 */
public interface BuildListener {

	/** The owner confirmed a footprint: the site is written and tier 1 is queued (the Batu Pertama ceremony). */
	default void onConfirmed(UUID owner, int template) {
	}

	/** A component's bill was reserved and it started building. */
	default void onComponentStarted(UUID owner, ComponentView component) {
	}

	/** A component finished (DONE). */
	default void onComponentDone(UUID owner, ComponentView component) {
	}

	/** Every component of {@code tier} is DONE or SKIPPED. */
	default void onTierDone(UUID owner, int template, int tier) {
	}

	/** The open component hit {@code count} protected or player-made cells; {@code sample} holds at most 32 of them. */
	default void onBlocked(UUID owner, ComponentView component, int count, List<BlockPos> sample) {
	}

	/** The next component waits for materials: {@code shortfall} is what its bill still lacks. */
	default void onWaitingFunds(UUID owner, Bill shortfall) {
	}

	// ================================================================== registration

	/** Adds a listener (the flow module's {@code init()}). */
	static void add(BuildListener listener) {
		if (listener != null && !Holder.LISTENERS.contains(listener)) {
			Holder.LISTENERS.add(listener);
		}
	}

	/** One listener that forwards every event to all registered ones (a failing listener is logged and skipped). */
	static BuildListener fire() {
		return Holder.ALL;
	}

	/** Keeps the registered listeners (not part of the API). */
	final class Holder {
		private static final List<BuildListener> LISTENERS = new CopyOnWriteArrayList<>();
		private static final BuildListener ALL = new BuildListener() {
			@Override
			public void onConfirmed(UUID owner, int template) {
				each(listener -> listener.onConfirmed(owner, template));
			}

			@Override
			public void onComponentStarted(UUID owner, ComponentView component) {
				each(listener -> listener.onComponentStarted(owner, component));
			}

			@Override
			public void onComponentDone(UUID owner, ComponentView component) {
				each(listener -> listener.onComponentDone(owner, component));
			}

			@Override
			public void onTierDone(UUID owner, int template, int tier) {
				each(listener -> listener.onTierDone(owner, template, tier));
			}

			@Override
			public void onBlocked(UUID owner, ComponentView component, int count, List<BlockPos> sample) {
				each(listener -> listener.onBlocked(owner, component, count, sample));
			}

			@Override
			public void onWaitingFunds(UUID owner, Bill shortfall) {
				each(listener -> listener.onWaitingFunds(owner, shortfall));
			}
		};

		private static void each(java.util.function.Consumer<BuildListener> call) {
			for (BuildListener listener : LISTENERS) {
				try {
					call.accept(listener);
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("A sect construction listener failed", exception);
				}
			}
		}

		private Holder() {
		}
	}
}
