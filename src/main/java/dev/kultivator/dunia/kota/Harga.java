package dev.kultivator.dunia.kota;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Element;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.harta.Harta;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.talisman.TalismanKind;
import dev.kultivator.talisman.Talismans;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.tempa.StorageRingItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prices in Nilai Batu (DESIGN 34.5.1): the base NB value of every item the towns trade, and the wares a shop, the
 * auction and the black market can make. Cores follow their Kantor Persembahan value ×{@link #NB_PER_PK} (one Low stone is
 * 0.5 PK and 1 NB, the one exchange rate of the mod); aged herbs follow a town curve that rises faster with age (audit
 * T16: {@code 2 × (1 + (age/100)^1.6)}: 100 years 4 NB, 300 years 12, 1000 years 82); pills, talismans, artifacts,
 * storage and treasures have a table here. Items of other modules are found by registry id and simply missing while their
 * module is off. Stacks are made only on demand.
 * <p>
 * Balance changes of the economy audit (approved "Ubah angkanya"): the pill pavilion sells herbs up to 300 years only (T16;
 * older herbs come from the auction, treasures and Pasar Abadi); the black-market wares cost about twice their recipe
 * (R14: Dupa Arwah 40, Selendang 300) and the Mutiara Penunjuk Laut has one price everywhere (S24: 20 NB, the sea
 * merchant too); phase-9 wares of Alam Abadi are never bought by a town shop ({@link #phase9}).
 */
public final class Harga {

	/** Realm bands: 0 Mortal, 1 Qi Refining, 2 Foundation, 3 Golden Core, 4 Nascent Soul, 5 Deity, 6 Dao Union, 7 Immortal Ascension, 8 True Immortal. */
	public static final int BANDS = 9;
	/** The one exchange rate between towns and sects (audit T17): NB per Poin Kontribusi (1 Low stone = 1 NB = 0.5 PK). */
	public static final double NB_PER_PK = 2.0D;
	/** The town herb curve (audit T16): NB = HERB_FACTOR × (1 + (age / 100)^HERB_POWER). */
	public static final double HERB_FACTOR = 2.0D;
	public static final double HERB_POWER = 1.6D;
	/** The oldest herb a town pavilion sells (audit T16). */
	public static final int SHOP_HERB_MAX_AGE = 300;
	/** The Mutiara Penunjuk Laut: one price in every shop (audit S24, R14; the recipe costs a Mid stone and sea shells). */
	public static final long MUTIARA_NB = 20L;

	/** A ware: item id, the lowest and highest band it is offered in, its base NB and the stack maker. */
	public record Ware(String id, int minBand, int maxBand, long base, Maker make) {
	}

	@FunctionalInterface
	public interface Maker {
		ItemStack make(RandomSource random, int band);
	}

	private static final Map<String, Long> BASE = new HashMap<>();

	static {
		// pills (Mid quality)
		BASE.put("qi_gathering_pill", 4L);
		BASE.put("qi_recovery_pill", 4L);
		BASE.put("healing_pill", 4L);
		BASE.put("toxin_purging_pill", 12L);
		BASE.put("bone_tempering_pill", 30L);
		BASE.put("foundation_pill", 60L);
		BASE.put("marrow_cleansing_pill", 80L);
		BASE.put("mata_terang_pill", 90L);
		BASE.put("heart_calming_pill", 100L);
		BASE.put("golden_core_pill", 400L);
		BASE.put("enlightenment_pill", 700L);
		BASE.put("soul_nurturing_pill", 900L);
		BASE.put("dao_insight_pill", 1200L);
		BASE.put("tribulation_warding_pill", 2000L);
		BASE.put("spirit_beast_pill", 150L);
		BASE.put("immortal_pill", 9000L);
		// talismans
		BASE.put("blank_talisman", 3L);
		BASE.put("thunder_talisman", 10L);
		BASE.put("fire_cloud_talisman", 10L);
		BASE.put("shield_talisman", 12L);
		BASE.put("binding_talisman", 12L);
		BASE.put("thousand_mile_talisman", 18L);
		BASE.put("limpidity_talisman", 30L);
		BASE.put("return_talisman", 40L);
		BASE.put("tribulation_ward_talisman", 160L);
		// storage
		BASE.put("storage_pouch", 20L);
		BASE.put("storage_ring_low", 60L);
		BASE.put("storage_ring_mid", 400L);
		BASE.put("storage_ring_high", 2500L);
		BASE.put("storage_ring_supreme", 12000L);
		// treasures and keys
		BASE.put("jade_slip", 40L);
		BASE.put("heavenvoid_fragment", 120L);
		BASE.put("heavenvoid_map", 300L);
		BASE.put("token_darah_api", 250L);
		BASE.put("token_naik", 1200L); // DESIGN 41.5.2 Token Naik Sekte (Grand Auction lot)
		BASE.put("soul_nurturing_wood", 1500L);
		BASE.put("soul_seal_jade", 2000L);
		BASE.put("heavenly_flame_seed", 20000L);
		BASE.put("inheritance_crystal", 900L);
		BASE.put("lost_scripture_page", 150L);
		BASE.put("vein_compass", 220L);
		BASE.put("immortal_essence_pearl", 4000L);
		BASE.put("land_expansion_token", 15000L);
		// trophies the black market buys
		BASE.put("loot_pouch", 30L);
		BASE.put("demonic_loot_pouch", 120L);
		BASE.put("demonic_banner", 200L);
		// other modules' wares (black market; present only when their module is on)
		BASE.put("selendang_pengikat_naga", 300L);
		BASE.put("dupa_arwah", 40L);
		BASE.put("mutiara_penunjuk_laut", MUTIARA_NB);
		// module K
		BASE.put("token_pasar_gelap", 60L);
		BASE.put("teh_embun_pagi", 2L);
		BASE.put("teh_awan_ungu", 9L);
		BASE.put("teh_penenang_hati", 20L);
		BASE.put("catatan_pengembara", 9L);
		BASE.put("kantong_saingan", 200L);
		// Sekte Langit 37G: Raksasa Longbo's trophy, worth its 600 PK use at 1 PK = 2 NB (audit 4.10)
		BASE.put("cangkang_kura_langit", 1200L);
		// 38TK-I: the Kontrak Kepala lot of the Black Auction (DESIGN 38.T.7.6), about one Foundation-band bounty
		BASE.put("tema/kontrak_kepala", 180L);
	}

	/** Phase-9 wares of Alam Abadi that no town shop buys (audit S24: no Batu Asal to NB loop). */
	private static final java.util.Set<String> PHASE9 = java.util.Set.of("immortal_pill", "immortal_essence_pearl", "land_expansion_token",
			"immortal_origin_stone", "kun_scale", "zhulong_scale", "taotie_fang", "peng_feather", "kunpeng_bone_fragment");

	/** True for a phase-9 ware of Alam Abadi (bought and sold only for Batu Asal). */
	public static boolean phase9(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return Kultivator.MOD_ID.equals(id.getNamespace()) && PHASE9.contains(id.getPath());
	}

	/** The town NB of an aged herb (audit T16 curve, at least 1). */
	public static long herbValue(int age) {
		return Math.max(1L, Math.round(HERB_FACTOR * (1.0D + Math.pow(Math.max(0, age) / 100.0D, HERB_POWER))));
	}

	/** Artifact base NB by grade 0-5. */
	public static final long[] ARTIFACT = {20L, 60L, 200L, 700L, 2400L, 8000L};
	/** Beast core NB by grade 1-4 (index 0 unused): the Kantor Misi value ×2. */
	static final long[] CORE = {0L, 6L, 20L, 60L, 240L};

	/** The base NB of {@code stack} (one item), 0 when the towns do not trade it. */
	public static long value(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0L;
		}
		if (SpiritStones.isStone(stack)) {
			return 0L;
		}
		if (SpiritHerbs.isHerb(stack)) {
			return herbValue(SpiritHerbs.age(stack));
		}
		if (BeastCores.isCore(stack)) {
			return Math.max(1L, Math.round(Harta.missionValue(stack) * NB_PER_PK));
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (!Kultivator.MOD_ID.equals(id.getNamespace())) {
			return 0L;
		}
		if ("forged_artifact".equals(id.getPath())) {
			int grade = ArtifactItem.grade(ArtifactItem.data(stack));
			return ARTIFACT[Math.max(0, Math.min(ARTIFACT.length - 1, grade))];
		}
		if (id.getPath().startsWith("heavenly_flame_seed")) {
			return BASE.get("heavenly_flame_seed");
		}
		return BASE.getOrDefault(id.getPath(), 0L);
	}

	/** The base NB of an item id of this mod (0 when unknown). */
	public static long base(String id) {
		return BASE.getOrDefault(id, 0L);
	}

	/** One item of this mod by id (empty when its module is off). */
	public static ItemStack item(String id) {
		Item item = BuiltInRegistries.ITEM.getValue(Kultivator.id(id));
		return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
	}

	public static boolean exists(String id) {
		return BuiltInRegistries.ITEM.getValue(Kultivator.id(id)) != Items.AIR;
	}

	// ================================================================== wares

	/** The pill pavilion: pills, aged herbs and beast cores. */
	public static final List<Ware> PILLS = List.of(
			new Ware("qi_gathering_pill", 0, 2, 4L, (r, b) -> item("qi_gathering_pill")),
			new Ware("qi_recovery_pill", 0, 3, 4L, (r, b) -> item("qi_recovery_pill")),
			new Ware("healing_pill", 0, 4, 4L, (r, b) -> item("healing_pill")),
			new Ware("toxin_purging_pill", 1, 5, 12L, (r, b) -> item("toxin_purging_pill")),
			new Ware("bone_tempering_pill", 1, 3, 30L, (r, b) -> item("bone_tempering_pill")),
			new Ware("foundation_pill", 1, 2, 60L, (r, b) -> item("foundation_pill")),
			new Ware("marrow_cleansing_pill", 1, 3, 80L, (r, b) -> item("marrow_cleansing_pill")),
			new Ware("mata_terang_pill", 2, 6, 90L, (r, b) -> item("mata_terang_pill")),
			new Ware("heart_calming_pill", 2, 6, 100L, (r, b) -> item("heart_calming_pill")),
			new Ware("golden_core_pill", 2, 3, 400L, (r, b) -> item("golden_core_pill")),
			new Ware("enlightenment_pill", 3, 7, 700L, (r, b) -> item("enlightenment_pill")),
			new Ware("soul_nurturing_pill", 3, 5, 900L, (r, b) -> item("soul_nurturing_pill")),
			new Ware("dao_insight_pill", 4, 8, 1200L, (r, b) -> item("dao_insight_pill")),
			new Ware("tribulation_warding_pill", 4, 8, 2000L, (r, b) -> item("tribulation_warding_pill")),
			new Ware("herb", 0, 8, 0L, (r, b) -> SpiritHerbs.randomFor(null, r, herbMin(b), herbMax(b))),
			new Ware("core", 1, 8, 0L, (r, b) -> BeastCores.create(randomElement(r), Math.max(1, Math.min(4, 1 + b / 2)))));

	/** The treasure pavilion: forged artifacts up to the band's grade, talismans, storage, jade slips. */
	public static final List<Ware> TREASURES = List.of(
			new Ware("artifact", 0, 8, 0L, (r, b) -> ArtifactItem.create(ArtifactItem.FORMS.get(r.nextInt(ArtifactItem.FORMS.size())),
					Math.max(0, Math.min(5, b / 2 + r.nextInt(2) - 1)), 0, r.nextInt(3))),
			new Ware("blank_talisman", 0, 8, 3L, (r, b) -> item("blank_talisman")),
			new Ware("thunder_talisman", 1, 6, 10L, (r, b) -> Talismans.create(TalismanKind.THUNDER, Math.min(9, b), "")),
			new Ware("fire_cloud_talisman", 1, 6, 10L, (r, b) -> Talismans.create(TalismanKind.FIRE_CLOUD, Math.min(9, b), "")),
			new Ware("shield_talisman", 1, 7, 12L, (r, b) -> Talismans.create(TalismanKind.SHIELD, Math.min(9, b), "")),
			new Ware("binding_talisman", 2, 7, 12L, (r, b) -> Talismans.create(TalismanKind.BINDING, Math.min(9, b), "")),
			new Ware("thousand_mile_talisman", 1, 8, 18L, (r, b) -> Talismans.create(TalismanKind.THOUSAND_MILE, Math.min(9, b), "")),
			new Ware("limpidity_talisman", 2, 8, 30L, (r, b) -> Talismans.create(TalismanKind.LIMPIDITY, Math.min(9, b), "")),
			new Ware("return_talisman", 1, 8, 40L, (r, b) -> Talismans.create(TalismanKind.RETURN, Math.min(9, b), "")),
			new Ware("tribulation_ward_talisman", 3, 8, 160L, (r, b) -> Talismans.create(TalismanKind.TRIBULATION_WARD, Math.min(9, b), "")),
			new Ware("storage_pouch", 0, 3, 20L, (r, b) -> StorageRingItem.create(0, null)),
			new Ware("storage_ring_low", 1, 4, 60L, (r, b) -> StorageRingItem.create(1, null)),
			new Ware("storage_ring_mid", 3, 6, 400L, (r, b) -> StorageRingItem.create(2, null)),
			new Ware("storage_ring_high", 5, 8, 2500L, (r, b) -> StorageRingItem.create(3, null)),
			new Ware("jade_slip", 1, 8, 40L, (r, b) -> item("jade_slip")));

	/** The black market (no realm gate, 1.5 × base). */
	public static final List<Ware> SHADOW = List.of(
			new Ware("heavenvoid_map", 0, 8, 300L, (r, b) -> item("heavenvoid_map")),
			new Ware("heavenvoid_fragment", 0, 8, 120L, (r, b) -> item("heavenvoid_fragment")),
			new Ware("token_darah_api", 0, 8, 250L, (r, b) -> item("token_darah_api")),
			new Ware("king_core", 0, 8, 240L, (r, b) -> BeastCores.create(randomElement(r), 4)),
			new Ware("inheritance_crystal", 0, 8, 900L, (r, b) -> item("inheritance_crystal")),
			new Ware("lost_scripture_page", 0, 8, 150L, (r, b) -> item("lost_scripture_page")),
			new Ware("selendang_pengikat_naga", 0, 8, 300L, (r, b) -> item("selendang_pengikat_naga")),
			new Ware("dupa_arwah", 0, 8, 40L, (r, b) -> item("dupa_arwah")),
			new Ware("mutiara_penunjuk_laut", 0, 8, MUTIARA_NB, (r, b) -> item("mutiara_penunjuk_laut")),
			new Ware("vein_compass", 0, 8, 220L, (r, b) -> item("vein_compass")));

	static int herbMin(int band) {
		return band <= 1 ? 10 : band <= 3 ? 100 : 200;
	}

	/** Audit T16: never above {@link #SHOP_HERB_MAX_AGE} (it was 1000 from band 4). */
	static int herbMax(int band) {
		return band <= 1 ? 100 : SHOP_HERB_MAX_AGE;
	}

	static Element randomElement(RandomSource random) {
		Element[] all = Element.values();
		return all[random.nextInt(all.length)];
	}

	/** The NB of a made ware (its stack's own value when the table says 0). */
	public static long priceOf(Ware ware, ItemStack stack) {
		long table = ware.base();
		return table > 0L ? table : Math.max(1L, value(stack));
	}

	public static @Nullable Ware byId(List<Ware> list, String id) {
		for (Ware ware : list) {
			if (ware.id().equals(id)) {
				return ware;
			}
		}
		return null;
	}

	private Harga() {
	}
}
