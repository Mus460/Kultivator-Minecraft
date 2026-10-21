package dev.kultivator.dunia.reruntuhan;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaAnchorEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The live {@link AnchorRun}s of module R, one per anchor (identity), made on a player's approach and ticked every
 * server tick until idle. Memory only; a server stop takes everything down.
 */
public final class Runs {

	private static final Map<DuniaAnchorEntity, AnchorRun> RUNS = new IdentityHashMap<>();

	/** A player is near {@code anchor}: keeps (or makes) its run alive. */
	public static @Nullable AnchorRun touch(DuniaAnchorEntity anchor, ServerLevel level, Function<DuniaAnchorEntity, AnchorRun> factory) {
		AnchorRun run = RUNS.get(anchor);
		if (run == null) {
			try {
				run = factory.apply(anchor);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kultivator: a ruin anchor {} could not start", anchor.kind(), exception);
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
	public static @Nullable AnchorRun of(DuniaAnchorEntity anchor) {
		return RUNS.get(anchor);
	}

	/** The anchor unloaded: its run goes at once. */
	public static void drop(DuniaAnchorEntity anchor) {
		AnchorRun run = RUNS.remove(anchor);
		if (run != null) {
			safeDespawn(run);
		}
	}

	/** Every run of a class (tests, the spine listeners). */
	public static <T extends AnchorRun> List<T> all(Class<T> type) {
		List<T> list = new ArrayList<>();
		for (AnchorRun run : RUNS.values()) {
			if (type.isInstance(run)) {
				list.add(type.cast(run));
			}
		}
		return list;
	}

	/** Once per server tick. */
	static void tick(MinecraftServer server) {
		if (RUNS.isEmpty()) {
			return;
		}
		for (AnchorRun run : List.copyOf(RUNS.values())) {
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
				Kultivator.LOGGER.error("Kultivator: a ruin run {} failed; it is taken down", run.anchor.kind(), exception);
				RUNS.remove(run.anchor);
				safeDespawn(run);
			}
		}
	}

	/** Takes everything down (server stop, {@code uji gua reset}). */
	public static void clear() {
		for (AnchorRun run : List.copyOf(RUNS.values())) {
			safeDespawn(run);
		}
		RUNS.clear();
	}

	/** Takes down and forgets the runs of one class (a reset of one system). */
	public static void clear(Class<? extends AnchorRun> type) {
		for (AnchorRun run : List.copyOf(RUNS.values())) {
			if (type.isInstance(run)) {
				RUNS.remove(run.anchor);
				safeDespawn(run);
			}
		}
	}

	private static void safeDespawn(AnchorRun run) {
		try {
			run.despawn();
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: a ruin run {} failed to clean up", run.anchor.kind(), exception);
		}
	}

	private Runs() {
	}
}
