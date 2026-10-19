package dev.kultivator.tempa;

import dev.kultivator.Kultivator;
import dev.kultivator.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.function.Consumer;

/**
 * The blocks, items, components and tags of P8-tempa (DESIGN 25.5, 25.7), registered when {@link #init()} loads the class
 * from {@code Tempa.init()} (before {@code ModItems.init()}). Items of other modules (A's realm materials and the Kuali, the
 * disciples' Heavenly Flame seed) are only looked up by registry id or tag, never imported.
 */
public final class TempaItems {

	/** {@code kultivator:artifact}: the forged artifact's record {form, grade, sub, element, refine, spirit, maker, bound, id}. */
	public static final DataComponentType<CompoundTag> ARTIFACT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("artifact"), DataComponentType.<CompoundTag>builder()
					.persistent(CompoundTag.CODEC)
					.networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
					.build());

	/** {@code kultivator:ring}: a storage ring's record {tier, extra_slots, owner, owner_name, ring_id}. */
	public static final DataComponentType<CompoundTag> RING = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("ring"), DataComponentType.<CompoundTag>builder()
					.persistent(CompoundTag.CODEC)
					.networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
					.build());

	/** {@code #kultivator:ring_immune}: fire, lava, cactus and explosions never destroy a dropped ring. */
	public static final TagKey<DamageType> RING_IMMUNE = TagKey.create(Registries.DAMAGE_TYPE, Kultivator.id("ring_immune"));

	/** Module A's realm material tags (DESIGN 25.3.9), by grade 2 Bumi, 3 Langit, 4 Abadi, 5 Dewa. */
	public static final TagKey<Item> REALM_BUMI = TagKey.create(Registries.ITEM, Kultivator.id("realm_material/bumi"));
	public static final TagKey<Item> REALM_LANGIT = TagKey.create(Registries.ITEM, Kultivator.id("realm_material/langit"));
	public static final TagKey<Item> REALM_ABADI = TagKey.create(Registries.ITEM, Kultivator.id("realm_material/abadi"));
	public static final TagKey<Item> REALM_DEWA = TagKey.create(Registries.ITEM, Kultivator.id("realm_material/dewa"));
	/** {@code #kultivator:spirit_seed} (module A): weapon-spirit seeds and the tier-4 ring. */
	public static final TagKey<Item> SPIRIT_SEED = TagKey.create(Registries.ITEM, Kultivator.id("spirit_seed"));

	/** Kuali Hampa Langit (module A, DESIGN 25.3.9), looked up by id. */
	public static final Identifier CAULDRON_ID = Kultivator.id("heavenvoid_cauldron");
	/** Benih Api Surgawi (DESIGN 20.3.16), looked up by id. */
	public static final Identifier FLAME_SEED_ID = Kultivator.id("heavenly_flame_seed");

	public static final ResourceKey<Block> FORGE_KEY = ResourceKey.create(Registries.BLOCK, Kultivator.id("earthfire_forge"));

	/** Tungku Api Bumi: hardness 3.5, pistons cannot move it, no block entity, no state, no random tick. */
	public static final Block EARTHFIRE_FORGE = Registry.register(BuiltInRegistries.BLOCK, FORGE_KEY,
			new EarthfireForgeBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.DEEPSLATE)
					.strength(3.5F, 6.0F)
					.sound(SoundType.DEEPSLATE_BRICKS)
					.pushReaction(PushReaction.IMMOVEABLE)
					.setId(FORGE_KEY)));

	public static final Item EARTHFIRE_FORGE_ITEM = ModItems.register("earthfire_forge", properties -> new BlockItem(EARTHFIRE_FORGE, properties) {
		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
				TooltipFlag flag) {
			builder.accept(Component.translatable("block.kultivator.earthfire_forge.tooltip").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("block.kultivator.earthfire_forge.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}, new Item.Properties().useBlockDescriptionPrefix().rarity(Rarity.UNCOMMON));

	/** The forged artifact (one item; the form is in the component and picks the model). Never breaks. */
	public static final Item FORGED_ARTIFACT = ModItems.register("forged_artifact", ArtifactItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

	/** Ring tiers 0-4: the pouch and the four rings. */
	public static final Item STORAGE_POUCH = ring("storage_pouch", 0, Rarity.COMMON);
	public static final Item STORAGE_RING_LOW = ring("storage_ring_low", 1, Rarity.UNCOMMON);
	public static final Item STORAGE_RING_MID = ring("storage_ring_mid", 2, Rarity.UNCOMMON);
	public static final Item STORAGE_RING_HIGH = ring("storage_ring_high", 3, Rarity.RARE);
	public static final Item STORAGE_RING_SUPREME = ring("storage_ring_supreme", 4, Rarity.EPIC);

	private static Item ring(String name, int tier, Rarity rarity) {
		return ModItems.register(name, properties -> new StorageRingItem(properties, tier), new Item.Properties()
				.stacksTo(1)
				.rarity(rarity)
				.delayedComponent(DataComponents.DAMAGE_RESISTANT, context -> new DamageResistant(context.getOrThrow(RING_IMMUNE))));
	}

	/** The ring item of {@code tier} (0 pouch … 4 supreme). */
	public static Item ringItem(int tier) {
		return switch (Math.max(0, Math.min(4, tier))) {
			case 1 -> STORAGE_RING_LOW;
			case 2 -> STORAGE_RING_MID;
			case 3 -> STORAGE_RING_HIGH;
			case 4 -> STORAGE_RING_SUPREME;
			default -> STORAGE_POUCH;
		};
	}

	/** The item registered under {@code id}, or AIR when that module is absent. */
	public static Item byId(Identifier id) {
		return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : Items.AIR;
	}

	/** The realm material tag a grade needs (null below Bumi). */
	public static TagKey<Item> realmTag(int grade) {
		return switch (grade) {
			case 2 -> REALM_BUMI;
			case 3 -> REALM_LANGIT;
			case 4 -> REALM_ABADI;
			case 5 -> REALM_DEWA;
			default -> null;
		};
	}

	/** Loads the class (registration happens in the static fields). */
	public static void init() {
	}

	private TempaItems() {
	}
}
