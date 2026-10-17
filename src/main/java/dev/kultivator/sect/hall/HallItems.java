package dev.kultivator.sect.hall;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.registry.ModItems;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

import java.util.function.Consumer;

/**
 * The blocks, items and component of the hall module, loaded (and so registered) by {@link Halls#init()}: the Sect Hall
 * stele, the Immortal Ascension Token (DESIGN 20.3.4) and the Lost Scripture Page with {@code kultivator:scripture_page}
 * (20.3.8), plus their chest loot (built-in tables only).
 */
public final class HallItems {

	/** A page of a lost art: set 1-3, piece 1-5. */
	public record ScripturePage(int set, int piece) {
		public static final Codec<ScripturePage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ExtraCodecs.intRange(1, LostArts.SETS).fieldOf("set").forGetter(ScripturePage::set),
				ExtraCodecs.intRange(1, LostArts.PIECES).fieldOf("piece").forGetter(ScripturePage::piece)
		).apply(instance, ScripturePage::new));
		public static final StreamCodec<ByteBuf, ScripturePage> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, ScripturePage::set, ByteBufCodecs.VAR_INT, ScripturePage::piece, ScripturePage::new);
	}

	public static final ResourceKey<Block> STELE_KEY = ResourceKey.create(Registries.BLOCK, Kultivator.id("sect_hall_stele"));

	/** Prasasti Aula Sekte: hardness 3.5, any pickaxe, blast resistance 1200, pistons cannot move it, light 7. */
	public static final Block SECT_HALL_STELE = Registry.register(BuiltInRegistries.BLOCK, STELE_KEY, new HallBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.EMERALD)
			.requiresCorrectToolForDrops()
			.strength(3.5F, 1200.0F)
			.sound(SoundType.LODESTONE)
			.lightLevel(state -> 7)
			.pushReaction(PushReaction.IMMOVEABLE)
			.noOcclusion()
			.setId(STELE_KEY)));

	public static final Item SECT_HALL_STELE_ITEM = ModItems.register("sect_hall_stele", properties -> new BlockItem(SECT_HALL_STELE, properties),
			new Item.Properties().useBlockDescriptionPrefix().rarity(Rarity.RARE));

	/** {@code kultivator:scripture_page}: which page of which lost art (saved and synced). */
	public static final DataComponentType<ScripturePage> SCRIPTURE_PAGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("scripture_page"), DataComponentType.<ScripturePage>builder()
					.persistent(ScripturePage.CODEC)
					.networkSynchronized(ScripturePage.STREAM_CODEC)
					.build());

	/** Token Kenaikan Abadi: used on an active stele, a tested candidate arrives at once (not craftable). */
	public static final Item IMMORTAL_ASCENSION_TOKEN = ModItems.register("immortal_ascension_token", TokenItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));

	/** Lembar Kitab yang Hilang: five different pieces of one set restore a lost art. */
	public static final Item LOST_SCRIPTURE_PAGE = ModItems.register("lost_scripture_page", ScripturePageItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

	/** Token chance per built-in chest table. */
	public static final float TOKEN_STRONGHOLD = 0.06F;
	public static final float TOKEN_ANCIENT_CITY = 0.06F;
	public static final float TOKEN_OMINOUS = 0.04F;
	/** Scripture page chance per built-in chest table. */
	public static final float PAGE_STRONGHOLD = 0.25F;
	public static final float PAGE_MANSION = 0.20F;
	public static final float PAGE_ANCIENT_CITY = 0.15F;
	public static final float PAGE_OMINOUS = 0.10F;
	public static final float PAGE_TEMPLE = 0.08F;

	/** Loads the class (registering everything above) and adds the chest loot. */
	public static void init() {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			float token = 0.0F;
			float page = 0.0F;
			if (key.equals(BuiltInLootTables.STRONGHOLD_LIBRARY)) {
				token = TOKEN_STRONGHOLD;
				page = PAGE_STRONGHOLD;
			} else if (key.equals(BuiltInLootTables.ANCIENT_CITY)) {
				token = TOKEN_ANCIENT_CITY;
				page = PAGE_ANCIENT_CITY;
			} else if (key.equals(BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS)) {
				token = TOKEN_OMINOUS;
				page = PAGE_OMINOUS;
			} else if (key.equals(BuiltInLootTables.WOODLAND_MANSION)) {
				page = PAGE_MANSION;
			} else if (key.equals(BuiltInLootTables.DESERT_PYRAMID) || key.equals(BuiltInLootTables.JUNGLE_TEMPLE)) {
				page = PAGE_TEMPLE;
			}
			if (token > 0.0F) {
				builder.withPool(LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(token))
						.add(LootItem.lootTableItem(IMMORTAL_ASCENSION_TOKEN)));
			}
			if (page > 0.0F) {
				addPagePool(builder, page);
			}
		});
	}

	/** One random page (any set, any piece, equal weights) with {@code chance}. */
	private static void addPagePool(LootTable.Builder builder, float chance) {
		LootPool.Builder pool = LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(chance));
		for (int set = 1; set <= LostArts.SETS; set++) {
			for (int piece = 1; piece <= LostArts.PIECES; piece++) {
				pool.add(LootItem.lootTableItem(LOST_SCRIPTURE_PAGE).apply(SetComponentsFunction.setComponent(SCRIPTURE_PAGE, new ScripturePage(set, piece))));
			}
		}
		builder.withPool(pool);
	}

	/** A page of {@code set}, piece {@code piece}. */
	public static ItemStack page(int set, int piece) {
		ItemStack stack = new ItemStack(LOST_SCRIPTURE_PAGE);
		stack.set(SCRIPTURE_PAGE, new ScripturePage(Math.max(1, Math.min(LostArts.SETS, set)), Math.max(1, Math.min(LostArts.PIECES, piece))));
		return stack;
	}

	// ================================================================== items

	/** The token: its use is on the stele ({@link HallBlock}); the tooltip says so. */
	public static class TokenItem extends Item {

		public TokenItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("kultivator.hall.token.tooltip").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("kultivator.hall.token.tooltip_use").withStyle(ChatFormatting.GOLD));
			builder.accept(Component.translatable("kultivator.hall.token.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	/** A lost scripture page: which art and which piece. */
	public static class ScripturePageItem extends Item {

		public ScripturePageItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			ScripturePage page = stack.get(SCRIPTURE_PAGE);
			if (page == null) {
				builder.accept(Component.translatable("kultivator.hall.page.blank").withStyle(ChatFormatting.GRAY));
			} else {
				builder.accept(Component.translatable("kultivator.hall.page.tooltip", LostArts.setName(page.set()), page.piece(), LostArts.PIECES)
						.withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			builder.accept(Component.translatable("kultivator.hall.page.tooltip_use", LostArts.PIECES).withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("kultivator.hall.page.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	private HallItems() {
	}
}
