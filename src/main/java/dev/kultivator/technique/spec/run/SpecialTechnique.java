package dev.kultivator.technique.spec.run;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Hand-written behaviour of the 9 special techniques (DESIGN 22.2.7), registered through
 * {@code TechniqueRegistry.Sink.special}. Specials obey every safety rule of 22.3.5. A step can also call a handler per
 * target with the payload {@code special(id)}. The spec's own timeline belongs to the runner, which schedules it once
 * (stages 0/1 when the cast starts, stage n on its recast); a handler never runs those steps itself, only steps it builds.
 */
public interface SpecialTechnique {

	/** Runs the technique; true when it happened (false spends nothing). */
	boolean cast(CastContext ctx);

	/** Every server tick while the cast is active. */
	default void tick(CastContext ctx) {
	}

	/** When the cast ends for any reason (forms revert, leases are released). */
	default void end(CastContext ctx, EndReason reason) {
	}

	/** Why the cast is refused before anything is spent (no safe exit, no partner...), or null. */
	default @Nullable Component blocker(CastContext ctx) {
		return null;
	}

	/** Disciples may learn and cast it (22.13.1). */
	default boolean supportsDisciples() {
		return false;
	}
}
