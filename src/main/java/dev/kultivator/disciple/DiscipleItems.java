package dev.kultivator.disciple;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Rank;
import dev.kultivator.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

/**
 * Items of the disciple module: the recruitment scroll and the Soul Tablet a fallen disciple leaves behind. Loaded (and
 * so registered) by {@link Disciples#init()}. The tablet keeps the soul ({@code DiscipleEntity.writeTablet}) under
 * {@code minecraft:custom_data.kultivator_tablet} and shows the disciple's name, rank and master as lore.
 */
public final class DiscipleItems {

	/** Sub-key of a tablet's custom data holding the soul. */
	static final String CUSTOM_KEY = "kultivator_tablet";
	private static final int LORE_NAME_COLOR = 0x9FF3E6;
	private static final int LORE_MASTER_COLOR = 0xB8A98A;

	/** Fire, lava, explosions and cactus cannot destroy a tablet (data/kultivator/tags/damage_type/disciple_tablet_immune.json). */
	public static final TagKey<DamageType> TABLET_IMMUNE = TagKey.create(Registries.DAMAGE_TYPE, Kultivator.id("disciple_tablet_immune"));

	/** Gulungan Penerimaan Murid: use it to accept a new disciple. */
	public static final Item RECRUITMENT_SCROLL = ModItems.register("recruitment_scroll", RecruitmentScrollItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

	/** Papan Jiwa Murid: the soul of a fallen disciple; its master uses it to call the disciple back. */
	public static final Item DISCIPLE_TABLET = ModItems.register("disciple_tablet", DiscipleTabletItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
					.delayedComponent(DataComponents.DAMAGE_RESISTANT, context -> new DamageResistant(context.getOrThrow(TABLET_IMMUNE))));

	/** Loads the class so the items above are registered. */
	public static void init() {
	}

	/** A Soul Tablet holding {@code disciple}: its soul in the custom data plus two lore lines (name · rank, master). */
	static ItemStack tabletFor(DiscipleEntity disciple) {
		ItemStack stack = new ItemStack(DISCIPLE_TABLET);
		CompoundTag custom = new CompoundTag();
		custom.put(CUSTOM_KEY, disciple.writeTablet());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
		Rank rank = disciple.data().rank();
		Component name = disciple.getName();
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("kultivator.disciples.tablet.lore_name", name, rank.displayName()).withColor(LORE_NAME_COLOR),
				Component.translatable("kultivator.disciples.tablet.lore_master", disciple.ownerName()).withColor(LORE_MASTER_COLOR))));
		return stack;
	}

	/** The soul a tablet holds (an empty tag when none). */
	static CompoundTag soulOf(ItemStack stack) {
		CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
		return custom == null ? new CompoundTag() : custom.copyTag().getCompoundOrEmpty(CUSTOM_KEY);
	}

	// ================================================================== items

	/** Use: a disciple appears at the master's side (consumed on success). */
	public static class RecruitmentScrollItem extends Item {

		public RecruitmentScrollItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.CONSUME;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!Disciples.recruit(serverPlayer)) {
				return InteractionResult.FAIL;
			}
			stack.consume(1, player);
			return InteractionResult.SUCCESS_SERVER;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
									TooltipFlag flag) {
			builder.accept(Component.translatable("kultivator.disciples.scroll.tooltip").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("kultivator.disciples.scroll.tooltip_limit", Disciples.MAX_DISCIPLES)
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/** Use: its master calls the soul back into a body next to them (consumed on success); anyone else is refused. */
	public static class DiscipleTabletItem extends Item {

		public DiscipleTabletItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.CONSUME;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!Disciples.revive(serverPlayer, stack)) {
				return InteractionResult.FAIL;
			}
			stack.consume(1, player);
			return InteractionResult.SUCCESS_SERVER;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
									TooltipFlag flag) {
			builder.accept(Component.translatable("kultivator.disciples.tablet.tooltip").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("kultivator.disciples.tablet.tooltip_safe").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private DiscipleItems() {
	}
}
