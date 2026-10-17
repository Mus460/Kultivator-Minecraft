package dev.kultivator.disciple.path;

import dev.kultivator.Kultivator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Delayed steps of module D's effects (a palm landing, the hammer's build-up blows, vortex pulses). A step runs only
 * while its entity is still alive in the world; everything is dropped when the server stops. Session only.
 */
final class PathTasks {

	private static final class Task {
		final Entity owner;
		final Runnable action;
		int left;

		Task(Entity owner, int delay, Runnable action) {
			this.owner = owner;
			this.left = delay;
			this.action = action;
		}
	}

	private static final List<Task> TASKS = new ArrayList<>();

	/** Runs {@code action} after {@code delay} ticks if {@code owner} is still alive and in the world. */
	static void schedule(Entity owner, int delay, Runnable action) {
		TASKS.add(new Task(owner, Math.max(1, delay), action));
	}

	static void tick(MinecraftServer server) {
		if (TASKS.isEmpty()) {
			return;
		}
		List<Task> due = new ArrayList<>();
		TASKS.removeIf(task -> {
			if (--task.left > 0) {
				return false;
			}
			due.add(task);
			return true;
		});
		for (Task task : due) {
			if (task.owner.isRemoved() || !task.owner.isAlive()) {
				continue;
			}
			try {
				task.action.run();
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A disciple path effect failed", exception);
			}
		}
	}

	static void clear() {
		TASKS.clear();
	}

	private PathTasks() {
	}
}
