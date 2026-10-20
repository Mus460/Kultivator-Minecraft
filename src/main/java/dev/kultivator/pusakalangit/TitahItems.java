package dev.kultivator.pusakalangit;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.registry.ModItems;
import dev.kultivator.util.NoGrief;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.UUID;

/**
 * The registrations of Phase 10b (DESIGN 29.9 item 6), loaded by {@link PusakaLangit#init()}: the item
 * {@code kultivator:heaven_decree_seal} (Segel Titah Langit) and its components, the two no-drop monument blocks
 * {@code kultivator:batu_pusaka} and {@code kultivator:urat_pusaka} (no item form, {@code noLootTable}, pistons cannot
 * move them; placed only by WorldBreaker's Titah monument phase) and the saved entity type
 * {@code kultivator:titah_monument}. ItemStacks are only ever made on demand (never in a static initializer).
 */
public final class TitahItems {

	/** {@code kultivator:titah_owner}: the seal's bound owner (29.2). */
	public static final DataComponentType<UUID> OWNER = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Kultivator.id("titah_owner"),
			DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());

	/** {@code kultivator:titah_owner_name}: the bound owner's name (tooltips). */
	public static final DataComponentType<String> OWNER_NAME = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("titah_owner_name"), DataComponentType.<String>builder().persistent(Codec.STRING)
					.networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

	/** {@code kultivator:titah_view}: the selected decree (0-3) shown in the seal's tooltip; written when it changes. */
	public static final DataComponentType<Integer> VIEW = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Kultivator.id("titah_view"),
			DataComponentType.<Integer>builder().persistent(ExtraCodecs.intRange(0, 3)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	/**
	 * {@code kultivator:titah_tier_view} (Phase 10b Tingkat, DESIGN 29.16.3): the selected decree's Tingkat (bits 0-3) and
	 * bit 4 when its cast would be permanent, shown in the seal's tooltip; written when it changes.
	 */
	public static final DataComponentType<Integer> TIER_VIEW = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("titah_tier_view"), DataComponentType.<Integer>builder().persistent(ExtraCodecs.intRange(0, 31))
					.networkSynchronized(ByteBufCodecs.VAR_INT).build());

	public static final ResourceKey<Block> BATU_KEY = ResourceKey.create(Registries.BLOCK, Kultivator.id("batu_pusaka"));
	public static final ResourceKey<Block> URAT_KEY = ResourceKey.create(Registries.BLOCK, Kultivator.id("urat_pusaka"));

	/** Batu Pusaka: dark jade-black stone, hard as obsidian, drops nothing (Silk Touch, explosions and the Wither included). */
	public static final Block BATU_PUSAKA = Registry.register(BuiltInRegistries.BLOCK, BATU_KEY, new Block(BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK)
			.strength(50.0F, 1200.0F)
			.sound(SoundType.DEEPSLATE)
			.noLootTable()
			.pushReaction(PushReaction.IMMOVEABLE)
			.setId(BATU_KEY)));

	/** Urat Pusaka: the glowing gold veins (light 10), otherwise as Batu Pusaka. */
	public static final Block URAT_PUSAKA = Registry.register(BuiltInRegistries.BLOCK, URAT_KEY, new Block(BlockBehaviour.Properties.of()
			.mapColor(MapColor.GOLD)
			.strength(50.0F, 1200.0F)
			.lightLevel(state -> 10)
			.sound(SoundType.AMETHYST)
			.noLootTable()
			.pushReaction(PushReaction.IMMOVEABLE)
			.setId(URAT_KEY)));

	/** Segel Titah Langit: one per stack, epic; binds to its first user. */
	public static final Item SEAL = ModItems.register("heaven_decree_seal", HeavenDecreeSealItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

	private static final ResourceKey<EntityType<?>> MONUMENT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("titah_monument"));

	/** The planted weapon: saved (no {@code noSave}), not summonable, no loot table, fire immune (29.6.1). */
	public static final EntityType<TitahMonumentEntity> MONUMENT = Registry.register(BuiltInRegistries.ENTITY_TYPE, MONUMENT_KEY,
			EntityType.Builder.<TitahMonumentEntity>of(TitahMonumentEntity::new, MobCategory.MISC)
					.sized(1.2F, 4.0F)
					.fireImmune()
					.noSummon()
					.noLootTable()
					.clientTrackingRange(10)
					.updateInterval(20)
					.build(MONUMENT_KEY));

	/** Loads the class so the entries above are registered. */
	static void init() {
	}

	/** The monument block state of a kind byte (1 Urat Pusaka, otherwise Batu Pusaka). */
	public static BlockState monumentState(int kind) {
		return (kind == 1 ? URAT_PUSAKA : BATU_PUSAKA).defaultBlockState();
	}

	/** A new seal bound to {@code owner} (made on demand). */
	static ItemStack boundSeal(ServerPlayer owner) {
		ItemStack stack = new ItemStack(SEAL);
		bind(stack, owner);
		return stack;
	}

	/** Binds {@code stack} to {@code owner} (its name for the tooltip too). */
	static void bind(ItemStack stack, ServerPlayer owner) {
		stack.set(OWNER, owner.getUUID());
		stack.set(OWNER_NAME, owner.getName().getString());
	}

	/** Into the inventory, the rest at the feet (a block-safe item entity). */
	static void give(ServerPlayer player, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), stack);
			item.setDefaultPickUpDelay();
			item.addTag(NoGrief.TAG);
			player.level().addFreshEntity(item);
		}
	}

	private TitahItems() {
	}
}
