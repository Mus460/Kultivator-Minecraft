package dev.kultivator.dunia.reruntuhan;

import dev.kultivator.harta.HartaText;
import dev.kultivator.registry.ModItems;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * The items of module R (DESIGN 34.6 R): Sobekan Segel (a torn seal talisman, the Hong Xin temptation of 34.5.6),
 * Inti Iblis Kuno (the core of a slain demon king) and Kitab Formasi Kuno (the formation book of an ancient ruin: a lore
 * item, because {@code formasi} has no public pattern-cost hook to take a discount voucher). Each tooltip shows its use
 * and its Sumber line ({@code item.kultivator.<id>.source} / {@code .inspired}). Loaded by {@link Reruntuhan#init()}
 * before {@code ModItems.init()}.
 */
public final class RItems {

	public static final Item SOBEKAN_SEGEL = ModItems.register("sobekan_segel", properties -> new RItem(properties, false, 0),
			new Item.Properties().stacksTo(64).rarity(Rarity.RARE));
	public static final Item INTI_IBLIS_KUNO = ModItems.register("inti_iblis_kuno", properties -> new RItem(properties, true, 0),
			new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
	public static final Item KITAB_FORMASI_KUNO = ModItems.register("kitab_formasi_kuno", properties -> new RItem(properties, true, 4),
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

	/** The page each player reads next (session only). */
	private static final java.util.Map<java.util.UUID, Integer> PAGES = new java.util.HashMap<>();

	/** Loads the class so the items above are registered. */
	static void init() {
	}

	/** One item of the module; {@code pages} > 0 makes it readable (one chat page per use, in order). */
	public static class RItem extends Item {

		private final boolean foil;
		private final int pages;

		public RItem(Item.Properties properties, boolean foil, int pages) {
			super(properties);
			this.foil = foil;
			this.pages = pages;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (pages <= 0) {
				return InteractionResult.PASS;
			}
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.CONSUME;
			}
			ItemStack stack = player.getItemInHand(hand);
			int page = Math.floorMod(PAGES.merge(serverPlayer.getUUID(), 1, Integer::sum) - 1, pages);
			Msg.chat(serverPlayer, Component.translatable(getDescriptionId() + ".page", page + 1, pages).withStyle(ChatFormatting.GOLD)
					.append(Component.literal(" ")).append(Component.translatable(getDescriptionId() + ".page." + page).withStyle(ChatFormatting.GRAY)));
			serverPlayer.getCooldowns().addCooldown(stack, 20);
			return InteractionResult.SUCCESS_SERVER;
		}

		@Override
		public boolean isFoil(ItemStack stack) {
			return foil || super.isFoil(stack);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
									TooltipFlag flag) {
			builder.accept(Component.translatable(getDescriptionId() + ".tooltip").withColor(HartaText.USE_COLOR));
			HartaText.sources(getDescriptionId(), builder);
		}
	}

	private RItems() {
	}
}
