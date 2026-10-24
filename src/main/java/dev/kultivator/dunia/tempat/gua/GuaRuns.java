package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaAnchorEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The live {@link GuaRun}s of 38GU, one per anchor (identity), made on a player's approach and ticked every server tick
 * until idle. Memory only; a server stop takes everything down. The place checks of the factor providers
 * ({@link GuaTempat}) read only this small list, never every loaded anchor of the world.
 */
public final class GuaRuns {

	private static final Map<DuniaAnchorEntity, GuaRun> RUNS = new IdentityHashMap<>();

	/** A player is near {@code anchor}: keeps (or makes) its run alive; null when the anchor has no run. */
	static @Nullable GuaRun touch(DuniaAnchorEntity anchor, ServerLevel level, Function<DuniaAnchorEntity, GuaRun> factory) {
		GuaRun run = RUNS.get(anchor);
		if (run == null) {
			try {
				run = factory.apply(anchor);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kultivator: a cave anchor {} could not start", anchor.kind(), exception);
				return null;
			}
			if (run == null) {
				return null;
			}
			RUNS.put(anchor, run);
		}
		run.touch(level.getGameTime());
		return run;
	}

	/** The run of {@code anchor}, or null. */
	public static @Nullable GuaRun of(DuniaAnchorEntity anchor) {
		return RUNS.get(anchor);
	}

	/** The anchor unloaded: its run goes at once. */
	static void drop(DuniaAnchorEntity anchor) {
		GuaRun run = RUNS.remove(anchor);
		if (run != null) {
			safeDespawn(run);
		}
	}

	/** Every live run of a class. */
	public static <T extends GuaRun> List<T> all(Class<T> type) {
		List<T> list = new ArrayList<>();
		for (GuaRun run : RUNS.values()) {
			if (type.isInstance(run)) {
				list.add(type.cast(run));
			}
		}
		return list;
	}

	/** The live run of {@code type} whose place {@code player} stands in (nearest first), or null. */
	public static <T extends GuaRun> @Nullable T at(ServerPlayer player, Class<T> type) {
		T best = null;
		double bestSq = Double.MAX_VALUE;
		for (GuaRun run : RUNS.values()) {
			if (!type.isInstance(run) || !run.inside(player)) {
				continue;
			}
			double d = run.centre().distanceToSqr(player.position());
			if (d < bestSq) {
				bestSq = d;
				best = type.cast(run);
			}
		}
		return best;
	}

	/** The nearest live run of {@code type} within {@code range} of the player (tests). */
	public static <T extends GuaRun> @Nullable T nearest(ServerPlayer player, Class<T> type, double range) {
		T best = null;
		double bestSq = range * range;
		for (GuaRun run : RUNS.values()) {
			if (!type.isInstance(run) || run.level != player.level()) {
				continue;
			}
			double d = run.centre().distanceToSqr(player.position());
			if (d <= bestSq) {
				bestSq = d;
				best = type.cast(run);
			}
		}
		return best;
	}

	/** Once per server tick. */
	static void tick(MinecraftServer server) {
		if (RUNS.isEmpty()) {
			return;
		}
		for (GuaRun run : List.copyOf(RUNS.values())) {
			long now = run.level.getGameTime();
			if (run.idle(now)) {
				RUNS.remove(run.anchor);
				safeDespawn(run);
				continue;
			}
			try {
				run.start();
				run.prune();
				run.tick(now);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kultivator: a cave run {} failed; it is taken down", run.anchor.kind(), exception);
				RUNS.remove(run.anchor);
				safeDespawn(run);
			}
		}
	}

	/** Takes everything down (server stop, config switch off). */
	static void clear() {
		for (GuaRun run : List.copyOf(RUNS.values())) {
			safeDespawn(run);
		}
		RUNS.clear();
	}

	/** How many runs are alive (tests). */
	public static int count() {
		return RUNS.size();
	}

	private static void safeDespawn(GuaRun run) {
		try {
			run.despawn();
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: a cave run {} failed to clean up", run.anchor.kind(), exception);
		}
	}

	private GuaRuns() {
	}
}
