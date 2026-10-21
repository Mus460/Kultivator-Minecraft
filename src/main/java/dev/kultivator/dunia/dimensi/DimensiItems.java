package dev.kultivator.dunia.dimensi;

import dev.kultivator.dunia.dimensi.bawah.GhostQuests;
import dev.kultivator.dunia.dimensi.bawah.SoulJourney;
import dev.kultivator.dunia.dimensi.laut.SeaGate;
import dev.kultivator.dunia.dimensi.laut.SeaPeople;
import dev.kultivator.harta.HartaText;
import dev.kultivator.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Module D's items (DESIGN 34.6 D). Every tooltip shows its use line ({@code item.kultivator.<id>.tooltip}) and its
 * Sumber line ({@code .source} / {@code .inspired}). Loaded by {@link Dimensi#init()} before {@code ModItems.init()}.
 * Stacks are only ever made at runtime (never in a static initializer).
 */
public final class DimensiItems {

	/** Dupa Arwah: lit at a Kuil Gerbang Hantu altar, it lets the soul walk Dunia Bawah for 20 minutes. */
	public static final Item DUPA_ARWAH = reg("dupa_arwah", p -> new DimensiItem(p, false), 16, Rarity.RARE);
	/** Lentera Arwah: Raja Yama's gift, +10 % meditation at night for Nascent Soul to Dao Union (carried). */
	public static final Item LENTERA_ARWAH = reg("lentera_arwah", p -> new DimensiItem(p, true), 1, Rarity.EPIC);
	/** Giok Arwah: soul jade from the courts and the ghosts; eaten, it mends Luka Jiwa and gives back a quarter of the Qi. */
	public static final Item GIOK_ARWAH = reg("giok_arwah", SoulJade::new, 64, Rarity.UNCOMMON);
	/** Surat Arwah: a ghost's letter to the living, carried to a town or village. */
	public static final Item SURAT_ARWAH = reg("surat_arwah", GhostLetter::new, 1, Rarity.UNCOMMON);
	/** Mutiara Penunjuk Laut: points to the nearest sea gate; held, it opens the whirlpool. */
	public static final Item MUTIARA_PENUNJUK_LAUT = reg("mutiara_penunjuk_laut", SeaPearl::new, 1, Rarity.RARE);
	/** Mutiara Penghindar Air: carried in Laut Tanpa Batas, one breathes in its water. */
	public static final Item MUTIARA_PENGHINDAR_AIR = reg("mutiara_penghindar_air", p -> new DimensiItem(p, true), 1, Rarity.EPIC);
	/** Mutiara Naga: the Dragon King's pearl, a trade good and the Jiao offering. */
	public static final Item MUTIARA_NAGA = reg("mutiara_naga", p -> new DimensiItem(p, true), 16, Rarity.EPIC);
	/** Air Mata Air Penglai: the spring water of the Isle of Immortals; drunk once, a deep insight into one's Dao. */
	public static final Item AIR_MATA_AIR_PENGLAI = reg("air_mata_air_penglai", PenglaiWater::new, 1, Rarity.EPIC);

	private static Item reg(String id, Function<Item.Properties, Item> factory, int stack, Rarity rarity) {
		return ModItems.register(id, factory, new Item.Properties().stacksTo(stack).rarity(rarity));
	}

	static void init() {
		// loads the class
	}

	/** A plain item of the module with its use line and Sumber line. */
	public static class DimensiItem extends Item {

		private final boolean foil;

		public DimensiItem(Item.Properties properties, boolean foil) {
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

	/** Giok Arwah. */
	public static class SoulJade extends DimensiItem {

		public SoulJade(Item.Properties properties) {
			super(properties, false);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.CONSUME;
			}
			if (SoulJourney.eatJade(serverPlayer)) {
				if (!serverPlayer.isCreative()) {
					player.getItemInHand(hand).shrink(1);
				}
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.FAIL;
		}
	}

	/** Surat Arwah: shows where the letter goes. */
	public static class GhostLetter extends DimensiItem {

		public GhostLetter(Item.Properties properties) {
			super(properties, false);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
									TooltipFlag flag) {
			super.appendHoverText(stack, context, display, builder, flag);
			LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
			if (tracker != null && tracker.target().isPresent()) {
				GlobalPos target = tracker.target().get();
				builder.accept(Component.translatable("kultivator.dimensi.surat.target", target.pos().getX(), target.pos().getZ())
						.withStyle(ChatFormatting.GRAY));
			}
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer serverPlayer) {
				GhostQuests.letterHint(serverPlayer, player.getItemInHand(hand));
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.CONSUME;
		}
	}

	/** Mutiara Penunjuk Laut: use to find the nearest sea gate. */
	public static class SeaPearl extends DimensiItem {

		public SeaPearl(Item.Properties properties) {
			super(properties, true);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.CONSUME;
			}
			SeaGate.pointToGate(serverPlayer);
			player.getCooldowns().addCooldown(player.getItemInHand(hand), 100);
			return InteractionResult.SUCCESS_SERVER;
		}
	}

	/** Air Mata Air Penglai: drunk once per player. */
	public static class PenglaiWater extends DimensiItem {

		public PenglaiWater(Item.Properties properties) {
			super(properties, true);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.CONSUME;
			}
			if (SeaPeople.drinkPenglai(serverPlayer)) {
				if (!serverPlayer.isCreative()) {
					player.getItemInHand(hand).shrink(1);
				}
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.FAIL;
		}
	}

	private DimensiItems() {
	}
}
