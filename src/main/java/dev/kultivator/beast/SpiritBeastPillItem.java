package dev.kultivator.beast;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Pil Binatang Roh / Spirit Beast Pill. It does nothing on its own: right-clicking a wolf or fox with it is handled by
 * {@link SpiritBeasts} (a use-entity callback, so it runs before the wolf's own sit / stand toggle).
 */
public class SpiritBeastPillItem extends Item {

	private static final int EFFECT_COLOR = 0xFFC870;
	private static final int FEED_COLOR = 0xA8D8FF;

	public SpiritBeastPillItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
								TooltipFlag flag) {
		builder.accept(Component.translatable("kultivator.beasts.tooltip.effect").withColor(EFFECT_COLOR));
		builder.accept(Component.translatable("kultivator.beasts.tooltip.feed", Integer.toString((int) SpiritBeasts.PILL_XP),
				Integer.toString(SpiritBeasts.PILL_COOLDOWN_TICKS / 20)).withColor(FEED_COLOR));
		builder.accept(Component.translatable("kultivator.beasts.tooltip.use").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
