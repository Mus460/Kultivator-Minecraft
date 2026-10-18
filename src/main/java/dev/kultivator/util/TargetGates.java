package dev.kultivator.util;

import dev.kultivator.Kultivator;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Event-driven target gates (DESIGN 24.2.10, 24.13 item 8): {@code mixin/MobSetTargetMixin} asks {@link #allow} whenever a
 * mob on the server is given a new non-null target, and cancels the change when any gate refuses it. Phase 7c registers
 * its concealment and maze gates here. No gate means every target is allowed (nothing changes). Brain-driven mobs
 * (piglins, hoglins, the Warden) do not use {@code setTarget} this way and are not covered.
 */
public final class TargetGates {

	/** One rule: false refuses {@code target} as {@code mob}'s new target. */
	@FunctionalInterface
	public interface TargetGate {
		boolean allow(Mob mob, LivingEntity target);
	}

	private static final List<TargetGate> GATES = new CopyOnWriteArrayList<>();

	/** Registers a gate (once, from a module's init). */
	public static void addGate(TargetGate gate) {
		GATES.add(gate);
	}

	/** True when every gate allows {@code target}; a gate that throws is logged and counts as allowing. */
	public static boolean allow(Mob mob, LivingEntity target) {
		if (GATES.isEmpty()) {
			return true;
		}
		for (TargetGate gate : GATES) {
			try {
				if (!gate.allow(mob, target)) {
					return false;
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Target gate failed for {}", mob, exception);
			}
		}
		return true;
	}

	/** True when at least one gate is registered (the mixin skips all work otherwise). */
	public static boolean any() {
		return !GATES.isEmpty();
	}

	private TargetGates() {
	}
}
