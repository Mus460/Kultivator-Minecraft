package dev.kultivator.dunia.tempat.tema;

import dev.kultivator.dunia.tempat.tema.bersama.DagangItems;
import dev.kultivator.dunia.tempat.tema.iblis.GulunganTerlarangItem;
import dev.kultivator.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * The themed-town items of DESIGN 38.T.3-38.T.7 and 38.T.10.2 ({@code kultivator:tema/<id>}); the trade goods
 * ({@code kultivator:dagang/<id>}) live in {@link DagangItems}. Always registered (K1); loaded by
 * {@link KotaTema#init()} before {@code ModItems.init()}; no ItemStack is created here (lazy-stack rule). Models and
 * textures: 38TV-M ({@code assets/kultivator/{items,models/item,textures/item}/tema/**}); names: lang fragment
 * {@code tempat_tema}. Behaviour (effects, compass needles, quest checks) lands in W2: 38TK inti owns this file, theme
 * agents ask inti for property changes and implement use behaviour through events in their own package.
 * <p>
 * Unique quest weapons (Gigi Hiu, Ekor Angin, Palu Darah Senja, Kipas Senja) and forbidden technique jades (Kitab Darah
 * Senja) are NOT items here: they are made from the existing artifact/jade systems with their own names (W2).
 */
public final class TemaItems {

	private static final List<Item> SEMUA = new ArrayList<>();

	// ---------------------------------------------------------------- Pelabuhan (38.T.3)
	/** Ikan Roh: food and pill ingredient, 10 % of bay fishing (6 NB). */
	public static final Item IKAN_ROH = daftar("ikan_roh", Item::new, new Item.Properties().stacksTo(64)
			.food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.6F).build()));
	/** Sup Ikan Pagi: fills and gives +5 % Qi regeneration for 3 minutes (3 NB). */
	public static final Item SUP_IKAN_PAGI = daftar("sup_ikan_pagi", Item::new, new Item.Properties().stacksTo(1)
			.food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.8F).build()).usingConvertsTo(Items.BOWL));
	/** Kompas Pasang: points to the nearest Pusaran Laut (150 NB, band >= 2, one per player). */
	public static final Item KOMPAS_PASANG = daftar("kompas_pasang", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	/** Kaca Garam Laut: lens glass of quest {@code tema_pelabuhan_pelita} bab 3 (crafted in the shipyard or bought). */
	public static final Item KACA_GARAM_LAUT = daftar("kaca_garam_laut", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	/** Mutiara Pelita: the spent lighthouse pearl Hei Sha hunts (bab 4). */
	public static final Item MUTIARA_PELITA = daftar("mutiara_pelita", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	/** Mutiara Fajar: the new light from the Boundless Sea (bab 5). */
	public static final Item MUTIARA_FAJAR = daftar("mutiara_fajar", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
	/** Jimat Ombak: cosmetic charm of the fishing-boat race winner. */
	public static final Item JIMAT_OMBAK = daftar("jimat_ombak", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	/** Astrolab Kecil: cosmetic, points to true north (Ibrahim's star nights). */
	public static final Item ASTROLAB_KECIL = daftar("astrolab_kecil", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	// ---------------------------------------------------------------- Oasis (38.T.4)
	/** Kerudung Pasir: head cloth, protects from the sandstorm's damage (15 NB). */
	public static final Item KERUDUNG_PASIR = daftar("kerudung_pasir", Item::new, new Item.Properties().stacksTo(1)
			.equippable(EquipmentSlot.HEAD));
	/** Air Suci: blessed water of the Kuil Air, cooling-pill ingredient and quest item (2 NB). */
	public static final Item AIR_SUCI = daftar("air_suci", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK).usingConvertsTo(Items.GLASS_BOTTLE));
	/** Surat Jalan Gurun: free passage at every oasis gate for 30 world days. */
	public static final Item SURAT_JALAN_GURUN = daftar("surat_jalan_gurun", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	/** Pecahan Mural: a sand find, sold to the painter or used by quests. */
	public static final Item PECAHAN_MURAL = daftar("pecahan_mural", Item::new, new Item.Properties().stacksTo(16));
	/** Koin Kuno: a sand find. */
	public static final Item KOIN_KUNO = daftar("koin_kuno", Item::new, new Item.Properties().stacksTo(64));
	/** Lempeng Peta: map plate pointing to the nearest {@code reruntuhan_gurun}. */
	public static final Item LEMPENG_PETA = daftar("lempeng_peta", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	/** Ekor Racun Kalajengking: the scorpion king's sting (pill and artifact ingredient, dye of the queen's carpet). */
	public static final Item EKOR_RACUN_KALAJENGKING = daftar("ekor_racun_kalajengking", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
	/** Selendang Penari: cosmetic shawl of the whirling-dance festival. */
	public static final Item SELENDANG_PENARI = daftar("selendang_penari", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	// ---------------------------------------------------------------- Air (38.T.5)
	/** Kipas Sutra: cosmetic silk fan held in the hand (20 NB). */
	public static final Item KIPAS_SUTRA = daftar("kipas_sutra", Item::new, new Item.Properties().stacksTo(1));
	/** Jubah Sutra: ordered silk robe (awan, bangau, teratai, naga motif; dyeable), no attributes (80 NB). */
	public static final Item JUBAH_SUTRA = daftar("jubah_sutra", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
			.equippable(EquipmentSlot.CHEST));
	/** Jubah Sutra Benang Merah: unique cosmetic of quest {@code tema_air_benang}. */
	public static final Item JUBAH_BENANG_MERAH = daftar("jubah_benang_merah", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
			.equippable(EquipmentSlot.CHEST));
	/** Teh Bi Luo (brewed cup): comprehension +0.03 per minute of meditation for 10 minutes (8 NB). */
	public static final Item TEH_BI_LUO = daftar("teh_bi_luo", Item::new, new Item.Properties().stacksTo(16)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK));
	/** Teh Embun Gunung (brewed cup): Qi regeneration +8 % for 5 minutes (5 NB). */
	public static final Item TEH_EMBUN_GUNUNG = daftar("teh_embun_gunung", Item::new, new Item.Properties().stacksTo(16)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK));
	/** Teh Seribu Tahun: once a world day eases heart-demon pressure 5, else comprehension +0.05/min (60 NB, band >= 3). */
	public static final Item TEH_SERIBU_TAHUN = daftar("teh_seribu_tahun", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.RARE)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK));
	/** Daun Teh Embun: fresh spring tea leaf (1 NB to the tea master, 20 a day). */
	public static final Item DAUN_TEH_EMBUN = daftar("daun_teh_embun", Item::new, new Item.Properties().stacksTo(64));
	/** Lentera Teratai: floating lotus lantern for the Malam Lentera Air (1 NB, 3 per festival). */
	public static final Item LENTERA_TERATAI = daftar("lentera_teratai", Item::new, new Item.Properties().stacksTo(16));
	/** Pita Naga: cosmetic ribbon of the dragon-boat champions. */
	public static final Item PITA_NAGA = daftar("pita_naga", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	/** Kain Xiu Niang: the ghost weaver's finished cloth (quest {@code tema_air_benang} bab 5-6). */
	public static final Item KAIN_XIU_NIANG = daftar("kain_xiu_niang", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

	// ---------------------------------------------------------------- Gunung (38.T.6)
	/** Tali Panjat: single use, prevents fall damage once while picking cliff herbs in that town (15 NB). */
	public static final Item TALI_PANJAT = daftar("tali_panjat", Item::new, new Item.Properties().stacksTo(16));
	/** Dupa Ziarah: pilgrim incense for the nine landings (2 NB). */
	public static final Item DUPA_ZIARAH = daftar("dupa_ziarah", Item::new, new Item.Properties().stacksTo(64));
	/** Rumput Awan Seribu Tahun: rare herb, age fixed at 300 (120 NB, stock 1 per 7 days). */
	public static final Item RUMPUT_AWAN_SERIBU_TAHUN = daftar("rumput_awan_seribu_tahun", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
	/** Tali Sutra Kera: the monkeys' spirit fibre for the new cable (quest {@code tema_gunung_tali} bab 4). */
	public static final Item TALI_SUTRA_KERA = daftar("tali_sutra_kera", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	/** Tongkat Kuli: cosmetic porter's staff (quest {@code tema_kuli_sehari}). */
	public static final Item TONGKAT_KULI = daftar("tongkat_kuli", Item::new, new Item.Properties().stacksTo(1));

	// ---------------------------------------------------------------- Iblis (38.T.7)
	/** Pil Darah: heals 30 % HP at once, Weakness I 20 s (25 NB, 3 a day). */
	public static final Item PIL_DARAH = daftar("pil_darah", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	/** Racun Lemah: weapon coating, Poison I 4 s on 3 hits (12 NB, band >= 2). */
	public static final Item RACUN_LEMAH = daftar("racun_lemah", Item::new, new Item.Properties().stacksTo(16));
	/** Racun Sedang: stronger coating (30 NB, band >= 3). */
	public static final Item RACUN_SEDANG = daftar("racun_sedang", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	/** Racun Kuat: strongest coating (80 NB, band >= 4). */
	public static final Item RACUN_KUAT = daftar("racun_kuat", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
	/** Topeng Tanpa Wajah: cosmetic mask; worn in the Kota Iblis, Jam Berburu challengers come half as often (70 NB). */
	public static final Item TOPENG_TANPA_WAJAH = daftar("topeng_tanpa_wajah", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
			.equippable(EquipmentSlot.HEAD));
	/** Peta Sarang: scroll pointing to a {@code gua/sarang_iblis} or {@code reruntuhan_gurun} (35 NB, 1 a day). */
	public static final Item PETA_SARANG = daftar("peta_sarang", Item::new, new Item.Properties().stacksTo(1));
	/** Kontrak Kepala: black-auction lot, the hiding place of a high-value demonic bounty. */
	public static final Item KONTRAK_KEPALA = daftar("kontrak_kepala", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	/** Peta Gerbang Rahasia: Mo Wen's map of the secret gate (quest {@code tema_iblis_rantai} bab 4). */
	public static final Item PETA_GERBANG_RAHASIA = daftar("peta_gerbang_rahasia", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item GULUNGAN_BAKAR_DARAH = gulungan(GulunganTerlarangItem.Jenis.BAKAR_DARAH, Rarity.UNCOMMON);
	public static final Item GULUNGAN_LANGKAH_HANTU = gulungan(GulunganTerlarangItem.Jenis.LANGKAH_HANTU, Rarity.UNCOMMON);
	public static final Item GULUNGAN_RANTAI_JIWA = gulungan(GulunganTerlarangItem.Jenis.RANTAI_JIWA, Rarity.RARE);
	public static final Item GULUNGAN_TOPENG_IBLIS_HATI = gulungan(GulunganTerlarangItem.Jenis.TOPENG_IBLIS_HATI, Rarity.RARE);

	/** Loads the classes so every themed item and trade good is registered; from {@link KotaTema#init()}. */
	static void init() {
		DagangItems.init();
	}

	/** Every {@code tema/*} item in registration order (the trade goods: {@link DagangItems#semua()}). */
	public static List<Item> semua() {
		return Collections.unmodifiableList(SEMUA);
	}

	private static Item gulungan(GulunganTerlarangItem.Jenis jenis, Rarity rarity) {
		return daftar("gulungan_" + jenis.id(), properties -> new GulunganTerlarangItem(properties, jenis),
				new Item.Properties().stacksTo(16).rarity(rarity));
	}

	private static Item daftar(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		Item item = ModItems.register("tema/" + id, factory, properties);
		SEMUA.add(item);
		return item;
	}

	private TemaItems() {
	}
}
