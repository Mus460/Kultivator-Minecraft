package dev.kultivator.item;

import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.data.RootGrade;
import dev.kultivator.harta.HartaText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Pil Pembersih Sumsum / Marrow Cleansing Pill: right-click to swallow. The server decides (see
 * {@link MarrowCleansing#use}); the client only waits for its answer. Since phase 7 a refined pill carries a
 * {@code pill_quality} (crafting-table pills count as Mid) that scales the chance of each step.
 */
public class MarrowCleansingPillItem extends Item {

	private static final int EFFECT_COLOR = 0x8FE3C8;
	private static final int ELEMENT_COLOR = 0xC9D7E6;
	private static final int PAIN_COLOR = 0xC98A7A;
	private static final int TOXIN_COLOR = 0x7FA650;
	private static final int STEP_COLOR = 0xB8D8C8;

	public MarrowCleansingPillItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.CONSUME;
		}
		ItemStack stack = player.getItemInHand(hand);
		return MarrowCleansing.use(serverPlayer, stack) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
	}

	@Override
	public Component getName(ItemStack stack) {
		if (!stack.has(AlchemyComponents.PILL_QUALITY)) {
			return super.getName(stack);
		}
		return super.getName(stack).copy().withColor(AlchemyComponents.quality(stack).color());
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return AlchemyComponents.quality(stack) == PillQuality.PERFECT || super.isFoil(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
								TooltipFlag flag) {
		PillQuality quality = AlchemyComponents.quality(stack);
		builder.accept(Component.translatable("kultivator.roots.tooltip.effect").withColor(EFFECT_COLOR));
		builder.accept(Component.translatable("kultivator.harta.marrow.tooltip.steps",
				pct(MarrowCleansing.chance(RootGrade.LOW, quality)),
				pct(MarrowCleansing.chance(RootGrade.MID, quality)),
				pct(MarrowCleansing.chance(RootGrade.HIGH, quality))).withColor(STEP_COLOR));
		builder.accept(Component.translatable("kultivator.harta.marrow.tooltip.requirements").withColor(STEP_COLOR));
		builder.accept(Component.translatable("kultivator.roots.tooltip.element",
				Integer.toString(Math.round(MarrowCleansing.ELEMENT_CHANCE * 100.0F)),
				Integer.toString(MarrowCleansing.MIN_ELEMENTS_TO_REMOVE)).withColor(ELEMENT_COLOR));
		builder.accept(Component.translatable("kultivator.roots.tooltip.pain",
				Integer.toString(MarrowCleansing.DURATION / 20)).withColor(PAIN_COLOR));
		builder.accept(Component.translatable("kultivator.harta.marrow.tooltip.toxin", Integer.toString(MarrowCleansing.TOXIN),
				Integer.toString(MarrowCleansing.TOXIN_HIGH)).withColor(TOXIN_COLOR));
		builder.accept(Component.translatable("kultivator.alchemy.tooltip.quality", quality.displayName(),
				HartaText.decimal(quality.multiplier())).withStyle(ChatFormatting.GRAY));
		HartaText.sources("kultivator.harta.marrow", builder);
		builder.accept(Component.translatable("kultivator.roots.tooltip.use").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	private static String pct(double chance) {
		return Long.toString(Math.round(chance * 100.0D));
	}
}
