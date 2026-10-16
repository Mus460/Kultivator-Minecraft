package dev.kultivator.cultivation;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Rank;
import dev.kultivator.data.XpSource;
import net.minecraft.server.level.ServerPlayer;

/**
 * STUB (phase 0) — owned by P1-progression. All cultivation XP goes through {@link #addXp}: multipliers, stage-ups,
 * the bottleneck at Peak and Stored Qi (docs/DESIGN.md section 1).
 */
public final class Progression {

	public static void init() {
	}

	/** Adds XP from {@code source} with all multipliers (EXACT: none). */
	public static void addXp(ServerPlayer player, double amount, XpSource source) {
		CultivationData data = Cultivators.get(player);
		data.setXp(Math.min(data.rank().xpRequired(), data.xp() + amount));
		Cultivators.dirty(player);
	}

	/** The multiplier {@link #addXp} uses for {@code source}. */
	public static double xpMultiplier(ServerPlayer player, XpSource source) {
		return 1.0D;
	}

	/** Puts the player at {@code rank} (commands, breakthroughs): clamps XP, re-applies stats, unlocks techniques. */
	public static void setRank(ServerPlayer player, Rank rank) {
		CultivationData data = Cultivators.get(player);
		data.setRank(rank);
		data.setXp(Math.min(data.xp(), rank.xpRequired()));
		Stats.apply(player);
		Cultivators.dirty(player);
	}

	/** After a successful breakthrough: moves to the next rank with an empty bar, then lets Stored Qi flow in. */
	public static void completeBreakthrough(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		Rank next = data.rank().next();
		data.setXp(0.0D);
		setRank(player, next);
	}

	/** Hardcore failure: drops to the Peak of the previous realm with an empty bar (Mortal is the floor). */
	public static void dropRealm(ServerPlayer player) {
	}

	private Progression() {
	}
}
