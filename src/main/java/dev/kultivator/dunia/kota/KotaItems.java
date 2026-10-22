package dev.kultivator.dunia.kota;

import dev.kultivator.harta.HartaText;
import dev.kultivator.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Module K's items (DESIGN 34.6 K): Token Pasar Gelap, the three teas, Kantong Saingan, Lencana Pengembara, Persik Abadi
 * and Catatan Pengembara. Each tooltip shows its use and its Sumber line ({@code item.kultivator.<id>.source} /
 * {@code .inspired}). Loaded by {@code Kota.init()} before {@code ModItems.init()}; stacks are made only when needed.
 */
public final class KotaItems {

	/** Token Pasar Gelap: opens the hidden door of the coffin shop (sold by the tea master after three rumours). */
	public static final Item TOKEN_PASAR_GELAP = ModItems.register("token_pasar_gelap", properties -> new KotaItem(properties, false),
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	/** Teh Embun Pagi: +10 % meditation XP for 10 in-game minutes. */
	public static final Item TEH_EMBUN_PAGI = ModItems.register("teh_embun_pagi", properties -> new TeaItem(properties, KedaiTeh.EMBUN),
			new Item.Properties().stacksTo(16));
	/** Teh Awan Ungu: +10 % comprehension gain for 10 in-game minutes (Golden Core+). */
	public static final Item TEH_AWAN_UNGU = ModItems.register("teh_awan_ungu", properties -> new TeaItem(properties, KedaiTeh.AWAN),
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	/** Teh Penenang Hati: −20 % Heart Demon pressure gain for 10 in-game minutes (Nascent Soul+). */
	public static final Item TEH_PENENANG_HATI = ModItems.register("teh_penenang_hati", properties -> new TeaItem(properties, KedaiTeh.PENENANG),
			new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
	/** Kantong Saingan: a beaten rival leader's pouch (two loot pouch rolls of its band and a Mid or High stone). */
	public static final Item KANTONG_SAINGAN = ModItems.register("kantong_saingan", RivalPouch::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
	/** Lencana Pengembara: a wanderer's token after a won sparring match. */
	public static final Item LENCANA_PENGEMBARA = ModItems.register("lencana_pengembara", properties -> new KotaItem(properties, false),
			new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON));
	/** Persik Abadi: a peach of the Kebun Persik Abadi. */
	public static final Item PERSIK_ABADI = ModItems.register("persik_abadi", Peach::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
	/** Catatan Pengembara: a wanderer's travel note (a site rumour and a lore line). */
	public static final Item CATATAN_PENGEMBARA = ModItems.register("catatan_pengembara", Note::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

	/** Loads the class so the items above are registered. */
	static void init() {
	}

	/** A plain item of the module with its use line and Sumber line. */
	public static class KotaItem extends Item {

		private final boolean foil;

		public KotaItem(Item.Properties properties, boolean foil) {
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
			if (this == LENCANA_PENGEMBARA) {
				// KedaiTeh: carrying the token gives one free rumour per day
				builder.accept(Component.translatable(getDescriptionId() + ".tooltip_rumour").withColor(HartaText.USE_COLOR));
			}
			HartaText.sources(getDescriptionId(), builder);
		}
	}

	/** A tea: right-click to drink it (one tea active at a time; a new one replaces the old). */
	public static class TeaItem extends KotaItem {

		private final int kind;

		public TeaItem(Item.Properties properties, int kind) {
			super(properties, false);
			this.kind = kind;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.CONSUME;
			}
			if (!KedaiTeh.drink(serverPlayer, kind, true)) {
				return InteractionResult.FAIL;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
			return InteractionResult.SUCCESS_SERVER;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
									TooltipFlag flag) {
			super.appendHoverText(stack, context, display, builder, flag);
			builder.accept(Component.translatable("kultivator.kota.teh.item.minutes", KedaiTeh.MINUTES).withStyle(ChatFormatting.GRAY,
					ChatFormatting.ITALIC));
		}
	}

	/** The rival leader's pouch: opened with a right-click. */
	public static class RivalPouch extends KotaItem {

		public RivalPouch(Item.Properties properties) {
			super(properties, true);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.CONSUME;
			}
			ItemStack stack = player.getItemInHand(hand);
			Saingan.openPouch(serverPlayer, stack);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.9F, 1.0F);
			return InteractionResult.SUCCESS_SERVER;
		}
	}

	/** Persik Abadi: right-click to eat (the gains of 34.5.4). */
	public static class Peach extends KotaItem {

		public Peach(Item.Properties properties) {
			super(properties, true);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.CONSUME;
			}
			if (!KotaAbadi.eatPeach(serverPlayer)) {
				return InteractionResult.FAIL;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
			return InteractionResult.SUCCESS_SERVER;
		}
	}

	/** Catatan Pengembara: right-click to read it (a rumour of one undiscovered site within 3 000 blocks, then a lore line). */
	public static class Note extends KotaItem {

		public Note(Item.Properties properties) {
			super(properties, false);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.CONSUME;
			}
			if (!KedaiTeh.readNote(serverPlayer)) {
				return InteractionResult.FAIL;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.9F, 1.0F);
			return InteractionResult.SUCCESS_SERVER;
		}
	}

	private KotaItems() {
	}
}
