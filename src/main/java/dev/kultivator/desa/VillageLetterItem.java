package dev.kultivator.desa;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Surat Permohonan (DESIGN 25.6.6): the letter a vassal village sends with a help request. {@code minecraft:custom_data}
 * holds {@code {village, type}}; {@code minecraft:lodestone_tracker} makes its compass seal point at the target with zero
 * client code (the item model is a vanilla {@code minecraft:compass} range dispatch).
 */
public class VillageLetterItem extends Item {

	private static final int TEXT = 0xE8D7A8;
	private static final int NOTE = 0x9FB7A8;

	public VillageLetterItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		String village = tag.getStringOr("village", "");
		String type = tag.getStringOr("type", "");
		if (!village.isEmpty()) {
			builder.accept(Component.translatable("kultivator.desa.letter.from", village).withColor(TEXT));
		}
		if (!type.isEmpty()) {
			builder.accept(Component.translatable("kultivator.desa.request." + type).withColor(TEXT));
			builder.accept(Component.translatable("kultivator.desa.request." + type + ".task").withColor(NOTE));
		}
		builder.accept(Component.translatable("kultivator.desa.letter.hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
