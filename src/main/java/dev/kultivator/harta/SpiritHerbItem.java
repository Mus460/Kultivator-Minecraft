package dev.kultivator.harta;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Tanaman Roh, a spirit herb with an age in years (DESIGN 19.3.4). Never plantable. From
 * {@link SpiritHerbs#MILLENNIUM} years its name turns gold ("… Seribu Tahun").
 */
public class SpiritHerbItem extends Item {

	private static final int AGE_COLOR = 0xB8E6A0;

	private final HerbKind kind;

	public SpiritHerbItem(Item.Properties properties, HerbKind kind) {
		super(properties);
		this.kind = kind;
	}

	public HerbKind kind() {
		return kind;
	}

	@Override
	public Component getName(ItemStack stack) {
		Component name = super.getName(stack);
		if (SpiritHerbs.age(stack) >= SpiritHerbs.MILLENNIUM) {
			return Component.translatable("item.kultivator.herb.millennium", name).withColor(SpiritHerbs.MILLENNIUM_COLOR);
		}
		return name;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return SpiritHerbs.age(stack) >= SpiritHerbs.MILLENNIUM || super.isFoil(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
								TooltipFlag flag) {
		int age = SpiritHerbs.age(stack);
		builder.accept(Component.translatable("kultivator.harta.herb.tooltip.age", age)
				.withColor(age >= SpiritHerbs.MILLENNIUM ? SpiritHerbs.MILLENNIUM_COLOR : AGE_COLOR));
		builder.accept(Component.translatable("kultivator.harta.herb.tooltip.auxiliary", HartaText.decimal(SpiritHerbs.auxiliaryQuality(age)))
				.withColor(HartaText.USE_COLOR));
		builder.accept(Component.translatable("kultivator.harta.tooltip.mission", HartaText.decimal(SpiritHerbs.missionValue(age)))
				.withColor(HartaText.VALUE_COLOR));
		builder.accept(Component.translatable("kultivator.harta.herb.tooltip.not_plantable").withColor(HartaText.SOURCE_COLOR));
		HartaText.sources(getDescriptionId(), builder);
	}
}
