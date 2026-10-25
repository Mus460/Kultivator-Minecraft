package dev.kultivator.dunia.kota42.ekonomi;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota42.sekte.PapanCabangBlock;
import dev.kultivator.registry.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.Set;

/**
 * The four blocks of DESIGN 42 (42.12.3), their block items and the three block entity types; registered always (42.0
 * rule: switches gate behaviour only) by {@link #register()}, which the kota42 root init calls before
 * {@code ModItems.init()}. Players place them in their own houses and shops (42.6.1-42.6.2, 42.8.4) and 42W puts them in
 * shop interiors of new chunks; the mod never places them at runtime. Models and textures are 42V's
 * ({@code tools/gen_kota_hidup_assets.py}, {@code block/kota42/**}); names in 42C's lang fragment {@code kota_hidup_ekonomi}.
 * <ul>
 *   <li>{@code kultivator:rak_dagang} {@link RakDagangBlock} + {@link RakDagangBlockEntity} (3 slots, prices, owner)</li>
 *   <li>{@code kultivator:meja_kasir} {@link MejaKasirBlock} (no block entity)</li>
 *   <li>{@code kultivator:papan_nama_toko} {@link PapanNamaTokoBlock} + {@link PapanNamaTokoBlock.Isi} (shop name, owner)</li>
 *   <li>{@code kultivator:papan_cabang} {@link PapanCabangBlock} + {@link PapanCabangBlock.Isi} (sect, emblem, name; 42E)</li>
 * </ul>
 */
public final class KotaEkonomiBlocks {

	private static final ResourceKey<Block> RAK_KEY = key("rak_dagang");
	private static final ResourceKey<Block> KASIR_KEY = key("meja_kasir");
	private static final ResourceKey<Block> PAPAN_TOKO_KEY = key("papan_nama_toko");
	private static final ResourceKey<Block> PAPAN_CABANG_KEY = key("papan_cabang");

	public static final Block RAK_DAGANG = Registry.register(BuiltInRegistries.BLOCK, RAK_KEY, new RakDagangBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD)
			.strength(2.0F, 3.0F)
			.sound(SoundType.WOOD)
			.noOcclusion()
			.setId(RAK_KEY)));

	public static final Block MEJA_KASIR = Registry.register(BuiltInRegistries.BLOCK, KASIR_KEY, new MejaKasirBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_RED)
			.strength(2.0F, 3.0F)
			.sound(SoundType.WOOD)
			.noOcclusion()
			.setId(KASIR_KEY)));

	public static final Block PAPAN_NAMA_TOKO = Registry.register(BuiltInRegistries.BLOCK, PAPAN_TOKO_KEY, new PapanNamaTokoBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_RED)
			.strength(1.0F)
			.sound(SoundType.WOOD)
			.noOcclusion()
			.noCollision()
			.pushReaction(PushReaction.POPPED)
			.setId(PAPAN_TOKO_KEY)));

	public static final Block PAPAN_CABANG = Registry.register(BuiltInRegistries.BLOCK, PAPAN_CABANG_KEY, new PapanCabangBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK)
			.strength(2.0F, 6.0F)
			.sound(SoundType.WOOD)
			.noOcclusion()
			.pushReaction(PushReaction.IMMOVEABLE)
			.setId(PAPAN_CABANG_KEY)));

	public static final Item RAK_DAGANG_ITEM = ModItems.register("rak_dagang", p -> new BlockItem(RAK_DAGANG, p),
			new Item.Properties().useBlockDescriptionPrefix());
	public static final Item MEJA_KASIR_ITEM = ModItems.register("meja_kasir", p -> new BlockItem(MEJA_KASIR, p),
			new Item.Properties().useBlockDescriptionPrefix());
	public static final Item PAPAN_NAMA_TOKO_ITEM = ModItems.register("papan_nama_toko", p -> new BlockItem(PAPAN_NAMA_TOKO, p),
			new Item.Properties().useBlockDescriptionPrefix());
	public static final Item PAPAN_CABANG_ITEM = ModItems.register("papan_cabang", p -> new BlockItem(PAPAN_CABANG, p),
			new Item.Properties().useBlockDescriptionPrefix());

	public static final BlockEntityType<RakDagangBlockEntity> RAK_DAGANG_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Kultivator.id("rak_dagang"), new BlockEntityType<>(RakDagangBlockEntity::new, Set.of(RAK_DAGANG)));
	public static final BlockEntityType<PapanNamaTokoBlock.Isi> PAPAN_NAMA_TOKO_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Kultivator.id("papan_nama_toko"), new BlockEntityType<>(PapanNamaTokoBlock.Isi::new, Set.of(PAPAN_NAMA_TOKO)));
	public static final BlockEntityType<PapanCabangBlock.Isi> PAPAN_CABANG_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Kultivator.id("papan_cabang"), new BlockEntityType<>(PapanCabangBlock.Isi::new, Set.of(PAPAN_CABANG)));

	/** Loads the class so every entry above is registered; safe to call twice. */
	public static void register() {
	}

	private static ResourceKey<Block> key(String name) {
		return ResourceKey.create(Registries.BLOCK, Kultivator.id(name));
	}

	private KotaEkonomiBlocks() {
	}
}
