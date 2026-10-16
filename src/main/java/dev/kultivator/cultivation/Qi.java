package dev.kultivator.cultivation;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import net.minecraft.server.level.ServerPlayer;

/** STUB (phase 0) — owned by P1-progression. Qi pool: maximum, regeneration, spending (DESIGN section 2). */
public final class Qi {

	public static void init() {
	}

	/** Called every server tick for every player. */
	public static void tick(ServerPlayer player, CultivationData data) {
	}

	/** Max qi from rank, root grade and breakthrough qualities. Pure: also used on the client. */
	public static double max(CultivationData data) {
		Rank rank = data.rank();
		return 100.0D + rank.realm().ordinal() * 120.0D + rank.stage() * 30.0D + rank.layer() * 60.0D;
	}

	/** Qi regenerated per second right now (meditating, deviation...). Pure: also used on the client. */
	public static double regenPerSecond(CultivationData data) {
		return max(data) * 0.004D;
	}

	/** Spends {@code amount} if the player has it. */
	public static boolean trySpend(ServerPlayer player, double amount) {
		CultivationData data = Cultivators.get(player);
		if (data.qi() + 1.0E-6D < amount) {
			return false;
		}
		data.setQi(data.qi() - amount);
		Cultivators.dirty(player);
		return true;
	}

	/** Adds qi up to the maximum. */
	public static void restore(ServerPlayer player, double amount) {
		CultivationData data = Cultivators.get(player);
		data.setQi(Math.min(max(data), data.qi() + amount));
		Cultivators.dirty(player);
	}

	private Qi() {
	}
}
