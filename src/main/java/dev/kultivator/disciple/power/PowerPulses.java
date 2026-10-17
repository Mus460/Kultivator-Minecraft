package dev.kultivator.disciple.power;

import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Timed effects of the powers (sword domes, prisons, clouds, the dragon's breath, the lotus charge): a step runs every
 * {@code interval} ticks, {@code count} times, then the end runs once. Ticked from the END_SERVER_TICK of
 * {@link DisciplePowers}; an effect whose disciple left the world stops at once (its end still runs, so transient
 * modifiers are always taken off again). Nothing here touches a block.
 */
final class PowerPulses {

	/** One step; return false to stop early. {@code index} counts from 0. */
	@FunctionalInterface
	interface Step {
		boolean run(DiscipleEntity entity, ServerLevel level, int index);
	}

	private static final class Pulse {
		final DiscipleEntity entity;
		final int interval;
		final int count;
		final Step step;
		final @Nullable Consumer<DiscipleEntity> end;
		long next;
		int done;

		Pulse(DiscipleEntity entity, long next, int interval, int count, Step step, @Nullable Consumer<DiscipleEntity> end) {
			this.entity = entity;
			this.next = next;
			this.interval = interval;
			this.count = count;
			this.step = step;
			this.end = end;
		}
	}

	private static final List<Pulse> RUNNING = new ArrayList<>();

	/** Runs {@code step} {@code count} times every {@code interval} ticks, the first after {@code delay} ticks. */
	static void start(DiscipleEntity entity, int delay, int interval, int count, Step step, @Nullable Consumer<DiscipleEntity> end) {
		RUNNING.add(new Pulse(entity, entity.level().getGameTime() + Math.max(0, delay), Math.max(1, interval), Math.max(1, count), step, end));
	}

	/** Runs {@code action} once after {@code delay} ticks (while the disciple stays in the world). */
	static void later(DiscipleEntity entity, int delay, Consumer<DiscipleEntity> action) {
		start(entity, delay, 1, 1, (e, level, index) -> {
			action.accept(e);
			return false;
		}, null);
	}

	static void tick(MinecraftServer server) {
		if (RUNNING.isEmpty()) {
			return;
		}
		List<Pulse> finished = null;
		for (Iterator<Pulse> it = List.copyOf(RUNNING).iterator(); it.hasNext(); ) {
			Pulse pulse = it.next();
			DiscipleEntity entity = pulse.entity;
			boolean gone = entity.isRemoved() || !entity.isAlive() || !(entity.level() instanceof ServerLevel);
			boolean stop = gone;
			if (!gone) {
				ServerLevel level = (ServerLevel) entity.level();
				long now = level.getGameTime();
				if (now >= pulse.next) {
					boolean keep;
					try {
						keep = pulse.step.run(entity, level, pulse.done);
					} catch (RuntimeException exception) {
						dev.kultivator.Kultivator.LOGGER.warn("A disciple power effect failed", exception);
						keep = false;
					}
					pulse.done++;
					pulse.next = now + pulse.interval;
					stop = !keep || pulse.done >= pulse.count;
				}
			}
			if (stop) {
				if (finished == null) {
					finished = new ArrayList<>();
				}
				finished.add(pulse);
			}
		}
		if (finished != null) {
			RUNNING.removeAll(finished);
			for (Pulse pulse : finished) {
				if (pulse.end != null) {
					try {
						pulse.end.accept(pulse.entity);
					} catch (RuntimeException exception) {
						dev.kultivator.Kultivator.LOGGER.warn("A disciple power effect failed to end", exception);
					}
				}
			}
		}
	}

	/** True while a pulse of {@code entity} runs (any kind). */
	static boolean running(DiscipleEntity entity) {
		for (Pulse pulse : RUNNING) {
			if (pulse.entity == entity) {
				return true;
			}
		}
		return false;
	}

	static void clear() {
		RUNNING.clear();
	}

	private PowerPulses() {
	}
}
