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
 * <li>{@code inti_meteor_agung} Inti Meteor Agung: the heart of the one Meteor Agung of a shower night; sneak-use gives 600
 * Qi and 1 Space Dao comprehension;</li>
 * <li>{@code kristal_gerhana} Kristal Gerhana: 35 % from an eclipse-touched monster a player slays; sneak-use teaches 0.3
 * Shadow Dao comprehension, five a day;</li>
 * <li>entities {@code meteor_roh} and {@code harta_lahir}: never saved, not summonable, no loot.</li>
 * </ul>
 */
public final class LangitItems {

	/** Qi a Batu Meteor Roh gives at once (Low stone 30, Mid 200). */
	public static final double METEOR_QI = 150.0D;
	/** Qi an Inti Meteor Agung gives at once (between a Mid stone 200 and a High stone 900). */
	public static final double GREAT_CORE_QI = 600.0D;
	/** Space Dao comprehension an Inti Meteor Agung teaches (250 s of a matching site's 0.004 per second). */
	public static final double GREAT_CORE_SPACE = 1.0D;
	/** Shadow Dao comprehension one Kristal Gerhana teaches, at most {@link #ECLIPSE_PER_DAY} a day (1.5, half a technique day). */
	public static final double ECLIPSE_SHADOW = 0.3D;
	public static final int ECLIPSE_PER_DAY = 5;
	/** The chance an eclipse-touched monster slain by a player leaves a Kristal Gerhana. */
	public static final double ECLIPSE_DROP = 0.35D;
	/** The chance watched from a star stage or the capital's observatory (DESIGN 38.A #22; the daily cap is unchanged). */
	public static final double ECLIPSE_DROP_WATCHED = 0.50D;

	/**
	 * The Kristal Gerhana chance for {@code slayer}: {@link #ECLIPSE_DROP}, raised by the bounded place factor
	 * {@code bintang} (Panggung Bintang, Menara Pengamat) to at most {@link #ECLIPSE_DROP_WATCHED}.
	 */
	public static double eclipseDrop(net.minecraft.server.level.ServerPlayer slayer) {
		double f = dev.kultivator.dunia.tempat.TempatApi.faktor(slayer, "bintang");
		return f > 1.0D ? Math.min(ECLIPSE_DROP_WATCHED, ECLIPSE_DROP * f) : ECLIPSE_DROP;
	}

	public static final Item BATU_METEOR_ROH = ModItems.register("batu_meteor_roh", MeteorStoneItem::new,
			new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON));

	public static final Item EMBUN_ROH = ModItems.register("embun_roh", SpiritDewItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)
					.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)
					.usingConvertsTo(Items.GLASS_BOTTLE));

	/** Inti Meteor Agung: the heart of the one Meteor Agung of a shower night (DESIGN 34.5.8, detail pass). */
	public static final Item INTI_METEOR_AGUNG = ModItems.register("inti_meteor_agung", GreatCoreItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

	/** Kristal Gerhana: what an eclipse-touched monster may leave (DESIGN 34.5.8, detail pass). */
	public static final Item KRISTAL_GERHANA = ModItems.register("kristal_gerhana", EclipseCrystalItem::new,
			new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON));

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

	/** Inti Meteor Agung: sneak + use outside meditation gives its Qi and its Space Dao insight at once. */
	public static class GreatCoreItem extends Item {

		public GreatCoreItem(Item.Properties properties) {
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
			return Langit.absorbGreatCore(serverPlayer, player.getItemInHand(hand)) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
		}

		@Override
		public boolean isFoil(ItemStack stack) {
			return true;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
									TooltipFlag flag) {
			builder.accept(Component.translatable("item.kultivator.inti_meteor_agung.lore").withColor(0xA8F0FF));
			builder.accept(Component.translatable("item.kultivator.inti_meteor_agung.use", (int) GREAT_CORE_QI,
					dev.kultivator.util.Msg.number(GREAT_CORE_SPACE)).withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("item.kultivator.inti_meteor_agung.where").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("kultivator.langit.peristiwa.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	/** Kristal Gerhana: sneak + use teaches a little Shadow Dao ({@link #ECLIPSE_PER_DAY} a day). */
	public static class EclipseCrystalItem extends Item {

		public EclipseCrystalItem(Item.Properties properties) {
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
			return Langit.absorbEclipseCrystal(serverPlayer, player.getItemInHand(hand)) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
									TooltipFlag flag) {
			builder.accept(Component.translatable("item.kultivator.kristal_gerhana.lore").withColor(0xFF6A58));
			builder.accept(Component.translatable("item.kultivator.kristal_gerhana.use", dev.kultivator.util.Msg.number(ECLIPSE_SHADOW),
					ECLIPSE_PER_DAY).withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("item.kultivator.kristal_gerhana.where", Math.round(ECLIPSE_DROP * 100.0D))
					.withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("kultivator.langit.peristiwa.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	private LangitItems() {
	}
}
