package dev.kultivator.dunia.tempat.tema.bersama;

import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.registry.ModItems;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The inter-town trade goods of DESIGN 38.T.10.1 ({@code kultivator:dagang/<id>}) and the sealed crate
 * {@code kultivator:dagang/peti}. Always registered (K1); loaded by {@code TemaItems.init()} before
 * {@code ModItems.init()}; no ItemStack is created here. Item models and textures are 38TV-M's.
 * <p>
 * {@link #BARANG} is the reference table of the design (origin, base buy price in NB, demand factor per market). The
 * live demand comes from {@code data/kultivator/tempat/permintaan/kota_tema_<tema>.json} (written by 38TK inti's
 * generator from this very table, read generically by 38EK {@code KafilahSekte}); {@code 0} in a column = "not bought
 * there" (shown as "—" in the design). Market columns: pelabuhan, oasis, air, gunung, ibu kota / kota 34, Kota Iblis
 * (black market). Selling: {@code dasar x permintaan x jenuh}, {@code jenuh = max(0.6, 1 - 0.04 n)}.
 */
public final class DagangItems {

	/** Index of a market column in {@link Barang#permintaan()}. */
	public static final int PASAR_PELABUHAN = 0;
	public static final int PASAR_OASIS = 1;
	public static final int PASAR_AIR = 2;
	public static final int PASAR_GUNUNG = 3;
	public static final int PASAR_IBU_KOTA = 4;
	public static final int PASAR_IBLIS = 5;

	/**
	 * One trade good.
	 *
	 * @param id         the item path ({@code dagang/<id>})
	 * @param asal       the producing theme
	 * @param dasar      base buy price at the origin in Nilai Batu
	 * @param permintaan demand factor per market column (0 = not bought; producer 0.8-1.0; at most 1.35)
	 * @param selundupan smuggled goods: only fences buy them; gate customs seize 40 %
	 * @param catatan    the design note for the ibu kota column when only a fence buys ("penadah"), else ""
	 */
	public record Barang(String id, Tema asal, int dasar, double[] permintaan, boolean selundupan, String catatan) {

		/** The demand factor in market column {@code pasar} ({@link #PASAR_PELABUHAN} ...). */
		public double di(int pasar) {
			return pasar >= 0 && pasar < permintaan.length ? permintaan[pasar] : 0.0D;
		}
	}

	private static final Map<String, Barang> BARANG = new LinkedHashMap<>();
	private static final Map<String, Item> ITEM = new LinkedHashMap<>();
	private static final List<Item> SEMUA = new ArrayList<>();

	// ---------------------------------------------------------------- pelabuhan
	public static final Item IKAN_ASIN_ROH = barang("ikan_asin_roh", Tema.PELABUHAN, 4, Rarity.COMMON, 64,
			new FoodProperties.Builder().nutrition(4).saturationModifier(0.5F).build(), false, "",
			0.8, 1.30, 0.95, 1.25, 1.15, 1.0);
	public static final Item MUTIARA_LAUT = barang("mutiara_laut", Tema.PELABUHAN, 40, Rarity.UNCOMMON, 64, null, false, "",
			0.8, 1.30, 1.25, 1.0, 1.20, 1.1);
	public static final Item REMPAH_SEBERANG = barang("rempah_seberang", Tema.PELABUHAN, 12, Rarity.COMMON, 64, null, false, "",
			0.85, 0.9, 1.25, 1.30, 1.20, 1.0);
	public static final Item PORSELEN_HIJAU = barang("porselen_hijau", Tema.PELABUHAN, 24, Rarity.UNCOMMON, 16, null, false, "",
			0.85, 1.35, 1.0, 1.10, 1.20, 0.0);
	// ---------------------------------------------------------------- oasis
	public static final Item KURMA_EMAS = barang("kurma_emas", Tema.OASIS, 3, Rarity.COMMON, 64,
			new FoodProperties.Builder().nutrition(3).saturationModifier(0.6F).build(), false, "",
			1.25, 0.8, 1.30, 1.20, 1.10, 1.0);
	public static final Item PERMADANI_GURUN = barang("permadani_gurun", Tema.OASIS, 30, Rarity.UNCOMMON, 16, null, false, "",
			1.15, 0.85, 1.20, 1.0, 1.30, 1.1);
	public static final Item GIOK_BARAT = barang("giok_barat", Tema.OASIS, 60, Rarity.UNCOMMON, 64, null, false, "",
			1.10, 0.85, 1.30, 1.15, 1.25, 1.2);
	public static final Item KACA_WARNA = barang("kaca_warna", Tema.OASIS, 16, Rarity.COMMON, 64, null, false, "",
			1.0, 0.85, 1.25, 1.30, 1.15, 0.0);
	// ---------------------------------------------------------------- air
	public static final Item SUTRA_AWAN = barang("sutra_awan", Tema.AIR, 36, Rarity.UNCOMMON, 64, null, false, "",
			1.30, 1.35, 0.8, 1.10, 1.20, 1.1);
	public static final Item TEH_BI_LUO = barang("teh_bi_luo", Tema.AIR, 10, Rarity.COMMON, 16, null, false, "",
			1.25, 1.30, 0.8, 0.95, 1.15, 0.0);
	public static final Item ARAK_BERAS = barang("arak_beras", Tema.AIR, 6, Rarity.COMMON, 16, null, false, "",
			1.20, 1.25, 0.85, 1.0, 1.05, 1.2);
	// ---------------------------------------------------------------- gunung
	public static final Item HERBA_TEBING_KERING = barang("herba_tebing_kering", Tema.GUNUNG, 14, Rarity.COMMON, 64, null, false, "",
			1.20, 1.15, 1.25, 0.8, 1.30, 1.1);
	public static final Item DUPA_GUNUNG = barang("dupa_gunung", Tema.GUNUNG, 5, Rarity.COMMON, 64, null, false, "",
			1.30, 1.20, 1.15, 0.8, 1.10, 0.0);
	public static final Item TEH_AWAN_GUNUNG = barang("teh_awan_gunung", Tema.GUNUNG, 9, Rarity.COMMON, 16, null, false, "",
			1.20, 1.30, 0.9, 0.8, 1.10, 0.0);
	public static final Item LINGZHI_GUNUNG = barang("lingzhi_gunung", Tema.GUNUNG, 45, Rarity.UNCOMMON, 16, null, false, "",
			1.15, 1.20, 1.20, 0.85, 1.30, 1.25);
	// ---------------------------------------------------------------- iblis
	/** Bought only by the capital's Pasar Gelap fence (1.2) and the Kota Iblis market. */
	public static final Item RACUN_ULAR_HITAM = barang("racun_ular_hitam", Tema.IBLIS, 18, Rarity.UNCOMMON, 16, null, false, "penadah",
			0.0, 0.0, 0.0, 0.0, 1.2, 0.8);
	/** Smuggled shadow oil: never sold by a righteous shop; fences in harbour, oasis and canal towns pay 1.35. */
	public static final Item MINYAK_BAYANGAN = barang("minyak_bayangan", Tema.IBLIS, 30, Rarity.UNCOMMON, 16, null, true, "penadah",
			1.35, 1.35, 1.35, 0.0, 0.0, 0.8);

	/** The sealed crate of 8 units of one good. */
	public static final Item PETI = daftar("peti", PetiDagangItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.COMMON));

	/** Loads the class so the entries above are registered. */
	public static void init() {
	}

	/** Every trade good in table order (not the crate). */
	public static List<Barang> barang() {
		return List.copyOf(BARANG.values());
	}

	/** The trade good with item path {@code dagang/<id>} or bare {@code <id>}, or null. */
	public static @Nullable Barang barang(String id) {
		if (id == null) {
			return null;
		}
		return BARANG.get(id.startsWith("dagang/") ? id.substring("dagang/".length()) : id);
	}

	/** The table entry of {@code item}, or null when it is not a trade good. */
	public static @Nullable Barang barang(Item item) {
		for (Map.Entry<String, Item> entry : ITEM.entrySet()) {
			if (entry.getValue() == item) {
				return BARANG.get(entry.getKey());
			}
		}
		return null;
	}

	/** The item of a trade good id, or null. */
	public static @Nullable Item item(String id) {
		return ITEM.get(id != null && id.startsWith("dagang/") ? id.substring("dagang/".length()) : id);
	}

	/** Every item of this class (17 goods + the crate) in registration order. */
	public static List<Item> semua() {
		return Collections.unmodifiableList(SEMUA);
	}

	private static Item barang(String id, Tema asal, int dasar, Rarity rarity, int stack, @Nullable FoodProperties food,
			boolean selundupan, String catatan, double... permintaan) {
		Item.Properties properties = new Item.Properties().stacksTo(stack).rarity(rarity);
		if (food != null) {
			properties.food(food);
		}
		Item item = daftar(id, Item::new, properties);
		BARANG.put(id, new Barang(id, asal, dasar, permintaan, selundupan, catatan));
		ITEM.put(id, item);
		return item;
	}

	private static Item daftar(String id, java.util.function.Function<Item.Properties, Item> factory, Item.Properties properties) {
		Item item = ModItems.register("dagang/" + id, factory, properties);
		SEMUA.add(item);
		return item;
	}

	private DagangItems() {
	}
}
