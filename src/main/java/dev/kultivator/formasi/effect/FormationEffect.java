package dev.kultivator.formasi.effect;

import dev.kultivator.formasi.core.CoreState;
import dev.kultivator.formasi.stack.FieldSink;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A formation handler (DESIGN 24.2.12), registered by id through {@code EffectSink}. Module A owns the interface; both
 * modules implement their own handlers. Every method runs on the server thread and must stay cheap.
 */
public interface FormationEffect {

	/** Every {@code spec.pulseTicks} while the core runs. */
	default void pulse(CoreContext ctx) {
	}

	/** Contributes to the stacking fields (24.5). */
	default void field(CoreContext ctx, FieldSink sink) {
	}

	/** The derived state changed (drop marks, notify). */
	default void onState(CoreContext ctx, CoreState from, CoreState to) {
	}

	/** True: the family has nothing to do now, upkeep ×0.25 (standby, 24.2.7). */
	default boolean idle(CoreContext ctx) {
		return false;
	}

	/** An extra SUSPENDED reason, or null. */
	default @Nullable Component blocker(CoreContext ctx) {
		return null;
	}

	/** At most 64 bytes of extra client fields for {@code FormasiSync}. */
	default void sync(CoreContext ctx, CompoundTag out) {
	}
}
