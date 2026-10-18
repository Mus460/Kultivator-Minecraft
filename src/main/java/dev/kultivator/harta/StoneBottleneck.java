package dev.kultivator.harta;

import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Rank;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Batu Roh di Hambatan / Stones at the bottleneck (DESIGN 19.3.2, use 4): High or Supreme spirit stones in the off hand
 * when a breakthrough succeeds raise its quality (+0.03 per High, +0.08 per Supreme, at most {@link #MAX_STONES} stones
 * and {@link #MAX_BONUS}). The stones are spent only by a success ({@code commit}); a failed or cancelled attempt keeps
 * them. Low and Mid stones give nothing; the awakening never uses it (the spine skips providers there).
 * <p>
 * Source: RMJ ch. 1152 (top-grade spirit stones are very useful for breaking through bottlenecks). The numbers are
 * game design.
 */
public final class StoneBottleneck implements Breakthrough.QualityBonus {

	/** Quality bonus per stone by grade (Low, Mid, High, Supreme). */
	public static final double[] PER_STONE = {0.0D, 0.0D, 0.03D, 0.08D};
	public static final int MAX_STONES = 4;
	public static final double MAX_BONUS = 0.16D;

	@Override
	public double bonus(ServerPlayer p, CultivationData d, Rank target) {
		return bonus(p);
	}

	@Override
	public void commit(ServerPlayer p, CultivationData d, Rank target) {
		ItemStack offhand = p.getOffhandItem();
		int used = stonesUsed(p);
		if (used <= 0) {
			return;
		}
		double bonus = bonus(p);
		Component name = offhand.getHoverName().copy();
		if (!p.hasInfiniteMaterials()) {
			offhand.shrink(used);
		}
		Msg.chat(p, Component.translatable("kultivator.harta.stone.bottleneck_used", used, name, HartaText.decimal(bonus))
				.withColor(SpiritStones.COLORS[SpiritStones.HIGH]));
		Advancements.grant(p, "stone_bottleneck");
	}

	/** The bonus the off-hand stones would give right now (pure; the Persiapan panel shows it as "+0.11 dari Batu Roh"). */
	public static double bonus(ServerPlayer player) {
		int grade = SpiritStones.grade(player.getOffhandItem());
		if (grade < 0) {
			return 0.0D;
		}
		return Math.min(MAX_BONUS, stonesUsed(player) * PER_STONE[grade]);
	}

	/** How many off-hand stones a success would spend (only as many as reach {@link #MAX_BONUS}). */
	public static int stonesUsed(ServerPlayer player) {
		ItemStack offhand = player.getOffhandItem();
		int grade = SpiritStones.grade(offhand);
		if (grade < 0 || PER_STONE[grade] <= 0.0D) {
			return 0;
		}
		int needed = (int) Math.ceil(MAX_BONUS / PER_STONE[grade] - 1.0E-9D);
		return Math.max(0, Math.min(offhand.getCount(), Math.min(MAX_STONES, needed)));
	}
}
