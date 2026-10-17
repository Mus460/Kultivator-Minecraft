package dev.kultivator.sect.event;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.XpSource;
import dev.kultivator.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Items of the sect activities (DESIGN 20.4.2): Bulu Ekor Roh from the chicken farm, Herba Fondasi from the forbidden
 * ground, Hidangan Roh from the Ovens and the six Kunpeng bone fragments (component {@code kultivator:kunpeng_piece}).
 * Loaded (and so registered) by {@link SectEvents#init()}.
 */
public final class SectEventItems {

	/** {@code kultivator:kunpeng_piece}: which of the six bone parts (1-6) a fragment is. */
	public static final DataComponentType<Integer> KUNPENG_PIECE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("kunpeng_piece"), DataComponentType.<Integer>builder()
					.persistent(ExtraCodecs.intRange(1, 6))
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	/** Player food of the meal: nutrition 8, saturation modifier 1.0 (DESIGN 20.3.9). */
	public static final FoodProperties MEAL_FOOD = new FoodProperties.Builder().nutrition(8).saturationModifier(1.0F).build();
	/** What eating a meal gives the player. */
	public static final double MEAL_PLAYER_XP = 8.0D;
	public static final double MEAL_PLAYER_QI = 10.0D;

	/** Bulu Ekor Roh: a Spirit Tail Chicken's feather (fuel of spirit enhancement, module C). */
	public static final Item SPIRIT_TAIL_FEATHER = ModItems.register("spirit_tail_feather",
			properties -> new LoreItem(properties, "spirit_tail_feather"), new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON));

	/** Herba Fondasi: the main herb of Foundation pills from the Blood-Colored Forbidden Ground. */
	public static final Item FOUNDATION_HERB = ModItems.register("foundation_herb",
			properties -> new LoreItem(properties, "foundation_herb"), new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON));

	/** Hidangan Roh: spirit rice from the Ovens. */
	public static final Item SPIRIT_MEAL = ModItems.register("spirit_meal", SpiritMealItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON).food(MEAL_FOOD));

	/** Pecahan Tulang Kunpeng: one of the six parts of the Kunpeng's primordial bone. */
	public static final Item KUNPENG_BONE_FRAGMENT = ModItems.register("kunpeng_bone_fragment", KunpengFragmentItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).component(KUNPENG_PIECE, 1));

	/** Loads the class so the items above are registered. */
	public static void init() {
	}

	/** A fragment of piece {@code piece} (1-6). */
	public static ItemStack fragment(int piece) {
		ItemStack stack = new ItemStack(KUNPENG_BONE_FRAGMENT);
		stack.set(KUNPENG_PIECE, Math.max(1, Math.min(6, piece)));
		return stack;
	}

	/** The piece (1-6) of a fragment stack, 0 for anything else. */
	public static int pieceOf(ItemStack stack) {
		return stack.is(KUNPENG_BONE_FRAGMENT) ? stack.getOrDefault(KUNPENG_PIECE, 1) : 0;
	}

	// ================================================================== items

	/** A plain item with a description line and its source line. */
	public static class LoreItem extends Item {
		private final String key;

		public LoreItem(Item.Properties properties, String key) {
			super(properties);
			this.key = key;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
									TooltipFlag flag) {
			builder.accept(Component.translatable("kultivator.sectevent.item." + key + ".tooltip").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("kultivator.sectevent.item." + key + ".source").withStyle(ChatFormatting.DARK_GRAY,
					ChatFormatting.ITALIC));
		}
	}

	/** Hidangan Roh: food for the player (+8 XP, +10 qi); fed to a disciple it gives XP x1.2 for 2 minutes. */
	public static class SpiritMealItem extends Item {

		public SpiritMealItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			if (entity instanceof ServerPlayer player) {
				Progression.addXp(player, MEAL_PLAYER_XP, XpSource.PILL);
				Qi.restore(player, MEAL_PLAYER_QI);
			}
			return super.finishUsingItem(stack, level, entity);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
									TooltipFlag flag) {
			builder.accept(Component.translatable("kultivator.sectevent.item.spirit_meal.tooltip", (int) MEAL_PLAYER_XP, (int) MEAL_PLAYER_QI)
					.withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("kultivator.sectevent.item.spirit_meal.disciple").withStyle(ChatFormatting.AQUA));
			builder.accept(Component.translatable("kultivator.sectevent.item.spirit_meal.source").withStyle(ChatFormatting.DARK_GRAY,
					ChatFormatting.ITALIC));
		}
	}

	/** A Kunpeng bone fragment: shows its piece; six different ones are united on the Misi tablet. */
	public static class KunpengFragmentItem extends Item {

		public KunpengFragmentItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
									TooltipFlag flag) {
			builder.accept(Component.translatable("kultivator.sectevent.item.kunpeng_bone_fragment.piece", pieceOf(stack))
					.withStyle(ChatFormatting.GOLD));
			builder.accept(Component.translatable("kultivator.sectevent.item.kunpeng_bone_fragment.tooltip").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("kultivator.sectevent.item.kunpeng_bone_fragment.source").withStyle(ChatFormatting.DARK_GRAY,
					ChatFormatting.ITALIC));
		}
	}

	private SectEventItems() {
	}
}
