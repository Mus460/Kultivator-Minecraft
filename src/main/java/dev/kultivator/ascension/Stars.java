package dev.kultivator.ascension;

import dev.kultivator.Kultivator;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ObjIntConsumer;
import java.util.function.ToDoubleFunction;

/**
 * Hooks of the player's Bintang Abadi star condensing (phase 8, DESIGN 25.2 item 13, 25.8.4): cost modifiers (the glue's
 * Esensi Energi Makam discount) and a listener told after each star condensed (where the glue consumes the Esensi). The
 * condensing itself stays in {@code cultivation.ImmortalPerks.condense}. Zero cost while nothing is registered.
 */
public final class Stars {

	/** The product of every cost modifier is kept inside this range. */
	public static final double COST_FACTOR_MIN = 0.5D;
	public static final double COST_FACTOR_MAX = 1.0D;

	private static final List<ToDoubleFunction<ServerPlayer>> COST_MODIFIERS = new CopyOnWriteArrayList<>();
	private static final List<ObjIntConsumer<ServerPlayer>> CONDENSED = new CopyOnWriteArrayList<>();

	/**
	 * Multiplies the Qi Abadi cost of the next star (asked again before every star of a {@code star:all}); register from a
	 * module's {@code init()}. Must be pure: consume items in {@link #addCondenseListener} instead.
	 */
	public static void addCostModifier(ToDoubleFunction<ServerPlayer> modifier) {
		if (modifier != null) {
			COST_MODIFIERS.add(modifier);
		}
	}

	/** Told right after one star condensed at the modified cost, with the new star count. */
	public static void addCondenseListener(ObjIntConsumer<ServerPlayer> listener) {
		if (listener != null) {
			CONDENSED.add(listener);
		}
	}

	/** The product of every cost modifier, clamped to {@link #COST_FACTOR_MIN}..{@link #COST_FACTOR_MAX} (1 without any). */
	public static double costFactor(ServerPlayer player) {
		if (COST_MODIFIERS.isEmpty()) {
			return 1.0D;
		}
		double product = 1.0D;
		for (ToDoubleFunction<ServerPlayer> modifier : COST_MODIFIERS) {
			try {
				double value = modifier.applyAsDouble(player);
				if (Double.isFinite(value) && value >= 0.0D) {
					product *= value;
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A star cost modifier failed", exception);
			}
		}
		return Math.max(COST_FACTOR_MIN, Math.min(COST_FACTOR_MAX, product));
	}

	/** Tells the listeners that one star condensed (called by {@code ImmortalPerks.condense} once per star). */
	public static void condensed(ServerPlayer player, int stars) {
		for (ObjIntConsumer<ServerPlayer> listener : CONDENSED) {
			try {
				listener.accept(player, stars);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A star condense listener failed", exception);
			}
		}
	}

	private Stars() {
	}
}
