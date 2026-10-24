package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.harta.HartaText;
import dev.kultivator.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

/**
 * The money loop's non-weapon items (DESIGN 38.M.2.1, 38.M.2.2, 38.M.2.4): four capital dishes, the four guild robes,
 * the Surat Wesel bearer note, the four raw gambling stones and the Grand Auction's Lot Misteri. Always registered
 * (38.0 rule 17); stacks only on demand. Textures and models: {@code tools/gen_tempat_ekonomi.py} (38EK, paths
 * {@code item/ekonomi/**}); names in lang fragment {@code tempat_ekonomi}.
 */
public final class EkonomiItems {

	// ---------------------------------------------------------------- dishes (38.M.2.2: 2 / 6 / 12 / 25 NB)
	/** Bakpao Roh: a spirit bun, simply filling. */
	public static final Item BAKPAO_ROH = ModItems.register("hidangan/bakpao_roh", p -> new HidanganItem(p, Hidangan.Jenis.BAKPAO),
			new Item.Properties().stacksTo(16).food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.7F).build()));
	/** Sup Ginseng Roh: +10 % qi regeneration for 5 minutes. */
	public static final Item SUP_GINSENG_ROH = ModItems.register("hidangan/sup_ginseng_roh", p -> new HidanganItem(p, Hidangan.Jenis.SUP),
			new Item.Properties().stacksTo(1).usingConvertsTo(Items.BOWL)
					.food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.9F).alwaysEdible().build()));
	/** Mi Panjang Umur: +2 hearts of absorption for 3 minutes. */
	public static final Item MI_PANJANG_UMUR = ModItems.register("hidangan/mi_panjang_umur", p -> new HidanganItem(p, Hidangan.Jenis.MI),
			new Item.Properties().stacksTo(1).usingConvertsTo(Items.BOWL).rarity(Rarity.UNCOMMON)
					.food(new FoodProperties.Builder().nutrition(9).saturationModifier(1.0F).alwaysEdible().build()));
	/** Arak Bunga Persik: +0.02 comprehension per minute of meditation for 10 minutes (not with Teh Awan Ungu). */
	public static final Item ARAK_BUNGA_PERSIK = ModItems.register("hidangan/arak_bunga_persik", p -> new HidanganItem(p, Hidangan.Jenis.ARAK),
			new Item.Properties().stacksTo(4).usingConvertsTo(Items.GLASS_BOTTLE).rarity(Rarity.RARE)
					.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.2F).alwaysEdible().build(), Consumables.DEFAULT_DRINK));

	// ---------------------------------------------------------------- guild robes (38.M.2.1: 30 NB, no attributes)
	public static final Item JUBAH_MERAH = jubah("merah");
	public static final Item JUBAH_BIRU = jubah("biru");
	public static final Item JUBAH_HIJAU = jubah("hijau");
	public static final Item JUBAH_EMAS = jubah("emas");

	// ---------------------------------------------------------------- bank and gambling
	/** Surat Wesel: a bearer note of the Rumah Uang, cashed at any Rumah Uang (record in {@link Barang#DATA}). */
	public static final Item SURAT_WESEL = ModItems.register("surat_wesel", SuratWesel.WeselItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	/** Raw stones of the Rumah Batu Sumber (38.M.2.4), cut at the cutting table. */
	public static final Item BATU_KERIKIL = batu(JudiBatu.Tingkat.KERIKIL, Rarity.COMMON);
	public static final Item BATU_BONGKAH = batu(JudiBatu.Tingkat.BONGKAH, Rarity.COMMON);
	public static final Item BATU_GUNUNG = batu(JudiBatu.Tingkat.GUNUNG, Rarity.UNCOMMON);
	public static final Item BATU_PURBA = batu(JudiBatu.Tingkat.PURBA, Rarity.RARE);
	/** Lot Misteri: the Grand Auction's closed box (contents from the Batu Gunung table). */
	public static final Item LOT_MISTERI = ModItems.register("lot_misteri", MisteriItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

	/** Loads the class so the items above are registered. */
	static void init() {
	}

	/** The four robes in colour order. */
	public static List<Item> jubah() {
		return List.of(JUBAH_MERAH, JUBAH_BIRU, JUBAH_HIJAU, JUBAH_EMAS);
	}

	/** The raw stone item of a tier. */
	public static Item batu(JudiBatu.Tingkat tingkat) {
		return switch (tingkat) {
			case KERIKIL -> BATU_KERIKIL;
			case BONGKAH -> BATU_BONGKAH;
			case GUNUNG -> BATU_GUNUNG;
			case PURBA -> BATU_PURBA;
		};
	}

	private static Item jubah(String warna) {
		return ModItems.register("jubah_kultivator_" + warna, p -> new EkonomiItem(p, false),
				new Item.Properties().stacksTo(1).equippable(EquipmentSlot.CHEST));
	}

	private static Item batu(JudiBatu.Tingkat tingkat, Rarity rarity) {
		return ModItems.register("batu_mentah/" + tingkat.id, p -> new JudiBatu.BatuItem(p, tingkat),
				new Item.Properties().stacksTo(1).rarity(rarity));
	}

	/** A plain item with its use line and Sumber line ({@code <description>.tooltip}, {@code .inspired}). */
	public static class EkonomiItem extends Item {

		private final boolean foil;

		public EkonomiItem(Item.Properties properties, boolean foil) {
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

	/** A dish: the food and its effect of 38.M.2.2 (applied on the server when eaten). */
	public static final class HidanganItem extends EkonomiItem {

		private final Hidangan.Jenis jenis;

		public HidanganItem(Item.Properties properties, Hidangan.Jenis jenis) {
			super(properties, false);
			this.jenis = jenis;
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			if (entity instanceof ServerPlayer player) {
				Hidangan.makan(player, jenis);
			}
			return super.finishUsingItem(stack, level, entity);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
				TooltipFlag flag) {
			builder.accept(Component.translatable("kultivator.tempat_ekonomi.hidangan.efek." + jenis.id).withColor(HartaText.USE_COLOR));
			if (jenis == Hidangan.Jenis.ARAK) {
				builder.accept(Component.translatable("kultivator.tempat_ekonomi.hidangan.tidak_bertumpuk").withStyle(ChatFormatting.GRAY,
						ChatFormatting.ITALIC));
			}
			HartaText.sources("kultivator.tempat_ekonomi.hidangan", builder);
		}
	}

	/** Lot Misteri: right-click to open (one roll of the Batu Gunung table, 38.M.2.3). */
	public static final class MisteriItem extends EkonomiItem {

		public MisteriItem(Item.Properties properties) {
			super(properties, true);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.CONSUME;
			}
			ItemStack stack = player.getItemInHand(hand);
			CompoundTag data = stack.getOrDefault(Barang.DATA, new CompoundTag());
			long nilai = data.getLongOr("nilai", JudiBatu.Tingkat.GUNUNG.harga);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			JudiBatu.bukaMisteri(serverPlayer, nilai);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.8F, 1.2F);
			return InteractionResult.SUCCESS_SERVER;
		}
	}

	private EkonomiItems() {
	}
}
