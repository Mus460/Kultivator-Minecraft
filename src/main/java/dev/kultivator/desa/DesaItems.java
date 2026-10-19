package dev.kultivator.desa;

import dev.kultivator.Kultivator;
import dev.kultivator.registry.ModItems;
import net.minecraft.core.Registry;
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

/**
 * The blocks and items of the vassal villages (DESIGN 25.6), loaded (and so registered) by {@link Desa#init()}: the Plakat
 * Pelindung Sekte and the Surat Permohonan (a help request letter whose needle points with the vanilla
 * {@code minecraft:lodestone_tracker}). The Pil Mata Terang of the glue is registered with the other pills in
 * {@code alchemy.AlchemyItems}.
 */
public final class DesaItems {

	public static final ResourceKey<Block> PLAQUE_KEY = ResourceKey.create(Registries.BLOCK, Kultivator.id("sect_patron_plaque"));

	/** Plakat Pelindung Sekte: a gold-capped stone plaque; placed by players, pistons cannot push it, no block entity. */
	public static final Block SECT_PATRON_PLAQUE = Registry.register(BuiltInRegistries.BLOCK, PLAQUE_KEY, new PatronPlaqueBlock(
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.GOLD)
					.strength(2.5F, 6.0F)
					.sound(SoundType.STONE)
					.pushReaction(PushReaction.IMMOVEABLE)
					.noOcclusion()
					.setId(PLAQUE_KEY)));

	public static final Item SECT_PATRON_PLAQUE_ITEM = ModItems.register("sect_patron_plaque", properties -> new BlockItem(SECT_PATRON_PLAQUE, properties),
			new Item.Properties().useBlockDescriptionPrefix().rarity(Rarity.UNCOMMON));

	/** Surat Permohonan: one help request of a vassal village; never stacks. */
	public static final Item VILLAGE_LETTER = ModItems.register("village_letter", VillageLetterItem::new, new Item.Properties().stacksTo(1));

	/** Loads the class so the entries above are registered. */
	static void init() {
	}

	private DesaItems() {
	}
}
