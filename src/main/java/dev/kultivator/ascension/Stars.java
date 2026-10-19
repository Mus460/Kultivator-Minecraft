package dev.kultivator.ascension;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.ImmortalPerks;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.XpSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ObjIntConsumer;
import java.util.function.ToDoubleFunction;

/**
 * Hooks of the player's Bintang Abadi star condensing (phase 8, DESIGN 25.2 item 13, 25.8.4): cost modifiers (the glue's
 * Esensi Energi Makam discount) and a listener told after each star condensed (where the glue consumes the Esensi). The
 * condensing itself stays in {@code cultivation.ImmortalPerks.condense}. Zero cost while nothing is registered.
 * <p>
 * Phase 9 (DESIGN 26.14 items 7-8): {@link StarGate}s that stop condensing before a gated star (the player's Seven
 * Calamities), {@link #barSize} and {@link #grantShare} for rewards paid as a share of the bar.
 */
public final class Stars {

	/** The product of every cost modifier is kept inside this range. */
	public static final double COST_FACTOR_MIN = 0.5D;
	public static final double COST_FACTOR_MAX = 1.0D;

	private static final List<ToDoubleFunction<ServerPlayer>> COST_MODIFIERS = new CopyOnWriteArrayList<>();
	private static final List<ObjIntConsumer<ServerPlayer>> CONDENSED = new CopyOnWriteArrayList<>();
	private static final List<StarGate> GATES = new CopyOnWriteArrayList<>();

	/**
	 * A gate before a star (DESIGN 26.14 item 7, the Seven Star Calamities): {@code ImmortalPerks.condense} asks every gate
	 * before each star and stops with the first message. Must be pure. Stored Qi keeps filling while a gate holds.
	 */
	@FunctionalInterface
	public interface StarGate {
		/** Why {@code nextStar} may not be condensed yet (shown in chat, may carry a click link), or null when it may. */
		@Nullable Component blocks(ServerPlayer p, int nextStar);
	}

	/** Adds a star gate; register from a module's {@code init()}. */
	public static void addGate(StarGate gate) {
		if (gate != null) {
			GATES.add(gate);
		}
	}

	/** The first gate's message for {@code nextStar}, or null when every gate lets it through (always null without gates). */
	public static @Nullable Component gateBlock(ServerPlayer player, int nextStar) {
		for (StarGate gate : GATES) {
			try {
				Component why = gate.blocks(player, nextStar);
				if (why != null) {
					return why;
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A star gate failed", exception);
			}
		}
		return null;
	}

	/**
	 * The size of the player's bar for rewards paid as a share (DESIGN 26.14 item 8): the rank's XP requirement or the next
	 * star's Qi Abadi cost, whichever is larger.
	 */
	public static double barSize(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		return Math.max(data.rank().xpRequired(), ImmortalPerks.nextStarCost(data));
	}

	/** Adds {@code fraction} × {@link #barSize} as XP from {@code XpSource.TRIAL} (with its multipliers; Stored Qi at the peak). */
	public static void grantShare(ServerPlayer player, double fraction) {
		if (!(fraction > 0.0D) || !Double.isFinite(fraction)) {
			return;
		}
		Progression.addXp(player, barSize(player) * fraction, XpSource.TRIAL);
	}

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
