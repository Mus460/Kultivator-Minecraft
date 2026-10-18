package dev.kultivator.formasi;

import dev.kultivator.Kultivator;
import dev.kultivator.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.function.Consumer;

/**
 * The blocks and items of phase 7c (DESIGN 24.2.1), registered when {@link #init()} loads the class from
 * {@code Formasi.init()} (before {@code ModItems.init()}). The eye is in {@code #kultivator:indestructible},
 * {@code #kultivator:sect_protected} and {@code #minecraft:mineable/pickaxe}.
 */
public final class FormasiItems {

	public static final ResourceKey<Block> FORMATION_EYE_KEY = ResourceKey.create(Registries.BLOCK, Kultivator.id("formation_eye"));

	/** Mata Formasi: hardness 3.5, any pickaxe, blast resistance 1200, pistons cannot move it, light 5, drops itself. */
	public static final Block FORMATION_EYE = Registry.register(BuiltInRegistries.BLOCK, FORMATION_EYE_KEY,
			new FormationEyeBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_PURPLE)
					.requiresCorrectToolForDrops()
					.strength(3.5F, 1200.0F)
					.sound(SoundType.LODESTONE)
					.lightLevel(state -> 5)
					.pushReaction(PushReaction.IMMOVEABLE)
					.setId(FORMATION_EYE_KEY)));

	public static final Item FORMATION_EYE_ITEM = ModItems.register("formation_eye", properties -> new BlockItem(FORMATION_EYE, properties) {
		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
				TooltipFlag flag) {
			builder.accept(Component.translatable("block.kultivator.formation_eye.tooltip").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("block.kultivator.formation_eye.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}, new Item.Properties().useBlockDescriptionPrefix().rarity(Rarity.UNCOMMON));

	/** Loads the class (registration happens in the static fields). */
	public static void init() {
	}

	private FormasiItems() {
	}
}
