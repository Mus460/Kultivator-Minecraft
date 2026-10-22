package dev.kultivator.sekte.paviliun;

import dev.kultivator.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Module A's one item, {@code kultivator:dupa} (Dupa, a bundle of incense sticks; DESIGN 36.2.5): offered at the Altar
 * Leluhur (use it on the altar, its censer or table, or on the cemetery's stele) it makes the day's incense rite, once a
 * world day per sect ({@link Upacara#incense}). Crafted from a stick, charcoal and sweet berries (four sticks). The rite
 * is handled by {@code Paviliun}'s block-use listener; the item itself only explains itself. No ItemStack is made here.
 * <p>
 * Sumber: dupa dipersembahkan di depan papan arwah leluhur (adat Tiongkok).
 */
public final class Dupa {

	public static final Item DUPA = ModItems.register("dupa", DupaItem::new, new Item.Properties().stacksTo(64));

	/** Loads the class (registers the item); from {@link Paviliun#init}, before {@code ModItems.init()}. */
	static void init() {
	}

	public static final class DupaItem extends Item {

		public DupaItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
									TooltipFlag flag) {
			builder.accept(Component.translatable("item.kultivator.dupa.tooltip").withStyle(ChatFormatting.GOLD));
			builder.accept(Component.translatable("item.kultivator.dupa.tooltip2").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("item.kultivator.dupa.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	private Dupa() {
	}
}
