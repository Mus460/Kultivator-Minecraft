package dev.kultivator.abadi;

import dev.kultivator.harta.HartaText;
import dev.kultivator.registry.ModItems;
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
 * The items of P9-langit (DESIGN 26.3.4, 26.3.5): Batu Asal Abadi (the Immortal Realm's currency) and the reusable Jimat Lintas
 * Alam. Each tooltip shows its use and its Sumber line ({@code item.kultivator.<id>.source} / {@code .inspired}). Loaded by
 * {@code Abadi.init()} before {@code ModItems.init()}; the other phase-9 modules find them by registry id.
 */
public final class AbadiItems {

	/** Batu Asal Abadi / Immortal Origin Stone: bids, conversion, crossing talismans, star condensing discount (RMJIR ch. 567, 615). */
	public static final Item IMMORTAL_ORIGIN_STONE = ModItems.register("immortal_origin_stone", properties -> new AbadiItem(properties, true),
			new Item.Properties().stacksTo(64).rarity(Rarity.EPIC));
	/** Jimat Lintas Alam / Realm Crossing Talisman: reusable, a 5 s channel between the homeworld and Alam Abadi. */
	public static final Item REALM_CROSSING_TALISMAN = ModItems.register("realm_crossing_talisman", CrossingTalisman::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

	/** Loads the class so the items above are registered. */
	static void init() {
	}

	/** A plain item of the module with its use line and Sumber line. */
	public static class AbadiItem extends Item {

		private final boolean foil;

		public AbadiItem(Item.Properties properties, boolean foil) {
			super(properties);
			this.foil = foil;
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

	/** The crossing talisman: use it to start the 5 s channel ({@link Crossing#start}). */
	public static class CrossingTalisman extends AbadiItem {

		public CrossingTalisman(Item.Properties properties) {
			super(properties, true);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.CONSUME;
			}
			return Crossing.start(serverPlayer) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
									TooltipFlag flag) {
			super.appendHoverText(stack, context, display, builder, flag);
			builder.accept(Component.translatable("kultivator.abadi.item.talisman_use").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	private AbadiItems() {
	}
}
