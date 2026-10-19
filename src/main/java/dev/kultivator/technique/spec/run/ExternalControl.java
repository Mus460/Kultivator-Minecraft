package dev.kultivator.technique.spec.run;

import dev.kultivator.technique.spec.dsl.Payload;
import net.minecraft.world.entity.LivingEntity;

/**
 * The public facade over the 6c bind path for code outside the technique runner (DESIGN 24.2.10, 24.13 item 8): phase 7c
 * formations root intruders through it, so a creeper's fuse is held every tick and the transient root modifier is removed
 * exactly as for a technique's bind. The bind is sourceless (no caster, no mastery, no feats) and always a ROOT; the
 * control diminishing returns and the boss rules of {@code StatusTracker.bind} apply unchanged. Players are never bound.
 */
public final class ExternalControl {

	/**
	 * Roots {@code target} for {@code ticks} ({@code bossTicks} for a boss, -1 = the normal rule). True when the root was
	 * applied or an existing bind was extended.
	 */
	public static boolean root(LivingEntity target, int ticks, int bossTicks) {
		if (target == null || !target.isAlive() || target.level().isClientSide()) {
			return false;
		}
		return StatusTracker.bind(null, target, Payload.BindKind.ROOT, ticks, bossTicks);
	}

	/** Ends the bind on {@code target} at once (a no-op when it is not bound; other technique effects stay). */
	public static void release(LivingEntity target) {
		if (target != null && !target.level().isClientSide()) {
			StatusTracker.unbind(target);
		}
	}

	private ExternalControl() {
	}
}
