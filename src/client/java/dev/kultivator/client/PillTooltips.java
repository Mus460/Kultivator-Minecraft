package dev.kultivator.client;

import dev.kultivator.client.fx.PillFx;
import dev.kultivator.cultivation.PillToxicity;
import dev.kultivator.cultivation.VanillaPills;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Dao;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Tooltip lines on vanilla items that work as pills: the effect in the pill's colour, how to absorb the sneak-use
 * ones, the toxin, and the player's current toxicity. Only shown when the server has the mod (a snapshot arrived).
 */
public final class PillTooltips {

	private static final int TOXIN_COLOR = 0x7FA650;
	private static final int TOXICITY_LOW = 0x9FD88A;
	private static final int TOXICITY_MID = 0xF2D45C;
	private static final int TOXICITY_HIGH = 0xFF6B5A;

	public static void init() {
		ItemTooltipCallback.EVENT.register(PillTooltips::addLines);
		// The effect dispatcher has no tick hook for pill effects, so the swirl animation hooks itself in here.
		PillFx.init();
	}

	private static void addLines(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		if (stack.isEmpty() || !ClientCultivation.received()) {
			return;
		}
		VanillaPills.Pill pill = VanillaPills.Pill.of(stack);
		if (pill == null) {
			return;
		}
		String headKey = pill.forbidden() ? "kultivator.pills.tooltip.forbidden" : "kultivator.pills.tooltip.pill";
		lines.add(Component.translatable(headKey, VanillaPills.join(pill.effectParts())).withColor(pill.color()));
		if (pill == VanillaPills.Pill.ECHO_SHARD) {
			lines.add(Component.translatable("kultivator.pills.tooltip.space").withColor(Dao.SPACE.color()));
		}
		if (pill.sneakUse()) {
			lines.add(Component.translatable("kultivator.pills.tooltip.sneak").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		if (pill.toxin() > 0) {
			lines.add(Component.translatable("kultivator.pills.tooltip.toxin", Integer.toString(pill.toxin())).withColor(TOXIN_COLOR));
		}
		CultivationData data = ClientCultivation.data();
		double toxicity = data.toxicity();
		if (toxicity >= 1.0D && pill.xp() > 0) {
			double efficacy = Math.max(0.0D, Math.min(1.0D, PillToxicity.effectiveness(data)));
			int color = toxicity > PillToxicity.POISON_THRESHOLD ? TOXICITY_HIGH
					: toxicity >= PillToxicity.WARNING_THRESHOLD ? TOXICITY_MID : TOXICITY_LOW;
			lines.add(Component.translatable("kultivator.pills.tooltip.toxicity", Msg.number(toxicity),
					Msg.number(efficacy * 100.0D + 1.0E-6D)).withColor(color));
		}
	}

	private PillTooltips() {
	}
}
