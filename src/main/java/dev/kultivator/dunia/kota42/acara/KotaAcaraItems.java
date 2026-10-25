package dev.kultivator.dunia.kota42.acara;

import dev.kultivator.registry.ModItems;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

import java.util.List;

/**
 * The festival foods and story items of DESIGN 42 (42.12.3, 42.7.6, 42.7.7, 42.10), registered always by {@link #register()}
 * before {@code ModItems.init()}; no {@code ItemStack} is made here. Models and textures are 42V's
 * ({@code tools/gen_kota_hidup_assets.py}, {@code item/kota42/**}); names in 42D's lang fragment {@code kota_hidup_acara}
 * (story items: 42B-C's {@code kota_hidup_cerita}; keys listed in {@code docs/progres/m2-42R0.md}).
 * <p>
 * Food effects that vanilla can express are on the item (Consumable effects); the town-only buffs are applied by their
 * owners on eating: {@code sup_hangat} "Hangat" (immune to powder-snow freezing, 42D {@code MusimKota}), {@code air_plum}
 * (desert heat 30 % slower, 38.T), {@code kue_pengantin} "Berkah" is vanilla Luck here (looting fortune +5 %, 20 min).
 *
 * <h2>Foods</h2>
 * {@code telur_merah} (red birth eggs, 42.7.7), {@code kue_pengantin} (wedding cake: Luck 20 min), {@code sup_hangat} (Nenek
 * Tang's winter soup: Regeneration I 30 s, bowl), {@code bubur_laba} (eight-treasure Laba porridge, bowl), {@code kue_bulan}
 * (mooncake), {@code kue_keranjang} (New Year rice cake), {@code air_plum} (cold plum drink, bottle), {@code permen_gula}
 * (sugar painting candy, quick to eat), {@code tanghulu} (candied haws: Speed I 30 s).
 *
 * <h2>Story items (42.10)</h2>
 * {@code kentongan_tua} (Lao Geng's old slit drum, "Lentera Terakhir Lao Geng"), {@code kwitansi_sumur} (the receipt at the
 * bottom of the well, "Dua Keluarga di Jalan Sumur"), {@code surat_utang} (an IOU), {@code wayang_kulit_kera_sakti},
 * {@code wayang_kulit_ular_putih}, {@code wayang_kulit_hua_mulan}, {@code wayang_kulit_hakim_bao} (the four shadow puppets
 * of the four scripts, "Malam Wayang"), {@code resep_sup} (Nenek Tang's soup recipe, "Musim Dingin Sup Hangat").
 */
public final class KotaAcaraItems {

	// ================================================================== foods

	public static final Item TELUR_MERAH = ModItems.register("telur_merah", Item::new,
			new Item.Properties().stacksTo(16).food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.6F).build()));
	public static final Item KUE_PENGANTIN = ModItems.register("kue_pengantin", Item::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)
					.food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.8F).alwaysEdible().build(),
							Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(
									new MobEffectInstance(MobEffects.LUCK, 20 * 60 * 20, 0))).build()));
	public static final Item SUP_HANGAT = ModItems.register("sup_hangat", Item::new,
			new Item.Properties().stacksTo(1).usingConvertsTo(Items.BOWL)
					.food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.8F).alwaysEdible().build(),
							Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(
									new MobEffectInstance(MobEffects.REGENERATION, 20 * 30, 0))).build()));
	public static final Item BUBUR_LABA = ModItems.register("bubur_laba", Item::new,
			new Item.Properties().stacksTo(1).usingConvertsTo(Items.BOWL)
					.food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.9F).build()));
	public static final Item KUE_BULAN = ModItems.register("kue_bulan", Item::new,
			new Item.Properties().stacksTo(16).food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.7F).build()));
	public static final Item KUE_KERANJANG = ModItems.register("kue_keranjang", Item::new,
			new Item.Properties().stacksTo(16).food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.6F).build()));
	public static final Item AIR_PLUM = ModItems.register("air_plum", Item::new,
			new Item.Properties().stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE)
					.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).alwaysEdible().build(), Consumables.DEFAULT_DRINK));
	public static final Item PERMEN_GULA = ModItems.register("permen_gula", Item::new,
			new Item.Properties().stacksTo(16)
					.food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1F).alwaysEdible().build(),
							Consumables.defaultFood().consumeSeconds(0.8F).build()));
	public static final Item TANGHULU = ModItems.register("tanghulu", Item::new,
			new Item.Properties().stacksTo(16)
					.food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.4F).alwaysEdible().build(),
							Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(
									new MobEffectInstance(MobEffects.SPEED, 20 * 30, 0))).build()));

	// ================================================================== story items

	public static final Item KENTONGAN_TUA = ModItems.register("kentongan_tua", Item::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item KWITANSI_SUMUR = ModItems.register("kwitansi_sumur", Item::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item SURAT_UTANG = ModItems.register("surat_utang", Item::new,
			new Item.Properties().stacksTo(1));
	public static final Item WAYANG_KULIT_KERA_SAKTI = wayang("kera_sakti");
	public static final Item WAYANG_KULIT_ULAR_PUTIH = wayang("ular_putih");
	public static final Item WAYANG_KULIT_HUA_MULAN = wayang("hua_mulan");
	public static final Item WAYANG_KULIT_HAKIM_BAO = wayang("hakim_bao");
	public static final Item RESEP_SUP = ModItems.register("resep_sup", Item::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	/** The four shadow puppets in script order ({@code LayarBayangEntity.NASKAH_*}). */
	public static final List<Item> WAYANG = List.of(WAYANG_KULIT_KERA_SAKTI, WAYANG_KULIT_ULAR_PUTIH, WAYANG_KULIT_HUA_MULAN,
			WAYANG_KULIT_HAKIM_BAO);

	/** Loads the class so every entry above is registered; safe to call twice. */
	public static void register() {
	}

	private static Item wayang(String naskah) {
		return ModItems.register("wayang_kulit_" + naskah, Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	}

	private KotaAcaraItems() {
	}
}
