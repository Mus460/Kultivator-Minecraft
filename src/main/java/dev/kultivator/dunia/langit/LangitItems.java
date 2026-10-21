package dev.kultivator.dunia.langit;

import dev.kultivator.Kultivator;
import dev.kultivator.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * The items and entity types of module L (DESIGN 34.6 L), loaded by {@link Langit#init()} before {@code ModItems.init()}:
 * <ul>
 * <li>{@code batu_meteor_roh} Batu Meteor Roh: what a Meteor Roh leaves; sneak-use absorbs 150 Qi (between a Low and a
 * Mid spirit stone), also the ship fuel of module T and the tier-4 beast offering of module B (both find it by id);</li>
 * <li>{@code embun_roh} Embun Roh: gathered with an empty bottle in Kabut Qi Pekat or at Fajar Ungu; drinking it gives
 * +20 % Qi regeneration for 5 minutes; tag {@code #kultivator:dunia/spirit_dew} for alchemy;</li>
 * <li>entities {@code meteor_roh} and {@code harta_lahir}: never saved, not summonable, no loot.</li>
 * </ul>
 */
public final class LangitItems {

	/** Qi a Batu Meteor Roh gives at once (Low stone 30, Mid 200). */
	public static final double METEOR_QI = 150.0D;

	public static final Item BATU_METEOR_ROH = ModItems.register("batu_meteor_roh", MeteorStoneItem::new,
			new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON));

	public static final Item EMBUN_ROH = ModItems.register("embun_roh", SpiritDewItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)
					.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)
					.usingConvertsTo(Items.GLASS_BOTTLE));

	private static final ResourceKey<EntityType<?>> METEOR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("meteor_roh"));
	private static final ResourceKey<EntityType<?>> HARTA_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("harta_lahir"));

	/** A falling spirit meteor: the client moves it itself, so the server sends no position updates. */
	public static final EntityType<MeteorRohEntity> METEOR = Registry.register(BuiltInRegistries.ENTITY_TYPE, METEOR_KEY,
			EntityType.Builder.<MeteorRohEntity>of(MeteorRohEntity::new, MobCategory.MISC)
					.sized(1.0F, 1.0F)
					.fireImmune()
					.noSave()
					.noSummon()
					.noLootTable()
					.clientTrackingRange(12)
					.noUpdateInterval()
					.build(METEOR_KEY));

	/** The born treasure in its jade lotus. */
	public static final EntityType<HartaLahirEntity> HARTA = Registry.register(BuiltInRegistries.ENTITY_TYPE, HARTA_KEY,
			EntityType.Builder.<HartaLahirEntity>of(HartaLahirEntity::new, MobCategory.MISC)
					.sized(1.0F, 1.6F)
					.fireImmune()
					.noSave()
					.noSummon()
					.noLootTable()
					.clientTrackingRange(6)
					.build(HARTA_KEY));

	/** Loads the class so the entries above are registered. */
	static void init() {
	}

	/** Batu Meteor Roh: sneak + use outside meditation absorbs its Qi at once. */
	public static class MeteorStoneItem extends Item {

		public MeteorStoneItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!player.isSecondaryUseActive() || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.CONSUME;
			}
			return Langit.absorbMeteorStone(serverPlayer, player.getItemInHand(hand)) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
		}

		@Override
		public boolean isFoil(ItemStack stack) {
			return false;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
									TooltipFlag flag) {
			builder.accept(Component.translatable("item.kultivator.batu_meteor_roh.lore").withColor(0x8FE6FF));
			builder.accept(Component.translatable("item.kultivator.batu_meteor_roh.use", (int) METEOR_QI).withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("item.kultivator.batu_meteor_roh.fuel").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("item.kultivator.batu_meteor_roh.offering").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("kultivator.langit.peristiwa.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	/** Embun Roh: drunk like a potion; the bottle comes back. */
	public static class SpiritDewItem extends Item {

		public SpiritDewItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			if (entity instanceof ServerPlayer player) {
				Langit.drinkDew(player);
			}
			return super.finishUsingItem(stack, level, entity);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
									TooltipFlag flag) {
			builder.accept(Component.translatable("item.kultivator.embun_roh.lore").withColor(0xD8B8FF));
			builder.accept(Component.translatable("item.kultivator.embun_roh.use", Math.round((Langit.DEW_REGEN - 1.0D) * 100.0D),
					Langit.DEW_TICKS / 1200).withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("item.kultivator.embun_roh.gather").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("kultivator.langit.cuaca.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	private LangitItems() {
	}
}
