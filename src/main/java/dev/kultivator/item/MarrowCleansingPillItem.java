package dev.kultivator.item;

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
 * {@link MarrowCleansing#use}); the client only waits for its answer.
 */
public class MarrowCleansingPillItem extends Item {

	private static final int EFFECT_COLOR = 0x8FE3C8;
	private static final int ELEMENT_COLOR = 0xC9D7E6;
	private static final int PAIN_COLOR = 0xC98A7A;
	private static final int TOXIN_COLOR = 0x7FA650;

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
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
								TooltipFlag flag) {
		builder.accept(Component.translatable("kultivator.roots.tooltip.effect").withColor(EFFECT_COLOR));
		builder.accept(Component.translatable("kultivator.roots.tooltip.element",
				Integer.toString(Math.round(MarrowCleansing.ELEMENT_CHANCE * 100.0F)),
				Integer.toString(MarrowCleansing.MIN_ELEMENTS_TO_REMOVE)).withColor(ELEMENT_COLOR));
		builder.accept(Component.translatable("kultivator.roots.tooltip.pain",
				Integer.toString(MarrowCleansing.DURATION / 20)).withColor(PAIN_COLOR));
		builder.accept(Component.translatable("kultivator.roots.tooltip.toxin", Integer.toString(MarrowCleansing.TOXIN)).withColor(TOXIN_COLOR));
		builder.accept(Component.translatable("kultivator.roots.tooltip.use").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
