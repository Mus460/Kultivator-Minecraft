package dev.kultivator.dunia.tempat.tema;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.air.AlatTenunEntity;
import dev.kultivator.dunia.tempat.tema.air.PerahuKanalEntity;
import dev.kultivator.dunia.tempat.tema.air.PerahuNagaEntity;
import dev.kultivator.dunia.tempat.tema.bersama.PintuAirEntity;
import dev.kultivator.dunia.tempat.tema.bersama.TanduEntity;
import dev.kultivator.dunia.tempat.tema.gunung.BatuRuntuhEntity;
import dev.kultivator.dunia.tempat.tema.gunung.HerbaTebingEntity;
import dev.kultivator.dunia.tempat.tema.gunung.JeratEntity;
import dev.kultivator.dunia.tempat.tema.gunung.KatrolEntity;
import dev.kultivator.dunia.tempat.tema.gunung.KeraGunungEntity;
import dev.kultivator.dunia.tempat.tema.gunung.KeretaGantungEntity;
import dev.kultivator.dunia.tempat.tema.gunung.PalangEntity;
import dev.kultivator.dunia.tempat.tema.gunung.RodaKatrolEntity;
import dev.kultivator.dunia.tempat.tema.gunung.TaliTurunEntity;
import dev.kultivator.dunia.tempat.tema.iblis.GerbangTengkorakEntity;
import dev.kultivator.dunia.tempat.tema.iblis.PintuArenaEntity;
import dev.kultivator.dunia.tempat.tema.iblis.SegelPintuEntity;
import dev.kultivator.dunia.tempat.tema.iblis.SelTawananEntity;
import dev.kultivator.dunia.tempat.tema.iblis.TongMinyakEntity;
import dev.kultivator.dunia.tempat.tema.iblis.TuasGerbangEntity;
import dev.kultivator.dunia.tempat.tema.oasis.BatuUkurAirEntity;
import dev.kultivator.dunia.tempat.tema.oasis.KalajengkingRajaEntity;
import dev.kultivator.dunia.tempat.tema.oasis.PilarRuneEntity;
import dev.kultivator.dunia.tempat.tema.oasis.PintuPasirEntity;
import dev.kultivator.dunia.tempat.tema.oasis.TemuanPasirEntity;
import dev.kultivator.dunia.tempat.tema.oasis.TimbaSumurEntity;
import dev.kultivator.dunia.tempat.tema.oasis.UntaKafilahEntity;
import dev.kultivator.dunia.tempat.tema.pelabuhan.ApiSuarEntity;
import dev.kultivator.dunia.tempat.tema.pelabuhan.DerekDermagaEntity;
import dev.kultivator.dunia.tempat.tema.pelabuhan.KapalBajakLautEntity;
import dev.kultivator.dunia.tempat.tema.pelabuhan.KapalDagangEntity;
import dev.kultivator.dunia.tempat.tema.pelabuhan.KapalGalanganEntity;
import dev.kultivator.dunia.tempat.tema.pelabuhan.PerahuNelayanEntity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contract C38.T2 (DESIGN 38.T.14.4): every themed-town entity type of 38.T.9 plus the quest entities of 38.T.3-38.T.7
 * ({@code pintu_pasir}, {@code batu_ukur_air}, {@code tong_minyak}, {@code tuas_gerbang}), registered from wave W0 on so
 * 38TV-M can register renderers at once. Rule K1: always registered; switches only gate behaviour.
 * <p>
 * All types are {@code noSave}, {@code noSummon} and {@code noLootTable}: bodies are re-made by 38TK from anchors, NBT
 * titik or data (38.T.0 butir 1) and count toward {@code DuniaBudget} kind {@code mekanisme_tema} (cap 24 global, at most
 * {@code kotaTema.mekanismeMaks} per town) or {@code npc_kota_tema}. Moving vehicles send positions every 2 ticks (riders
 * stay smooth); scenery every 20 ticks. Tracking range in chunks: ships 12 (192 blocks at sea), others 6-10.
 * <p>
 * 38TV-M reads only the synced {@link dev.kultivator.dunia.tempat.tema.bersama.TemaAnimasi} state; it never changes these
 * builders. Owners: this file 38TK inti; the classes their theme agents (38TK-P/O/A/G/I) and {@code bersama} 38TK inti.
 */
public final class TemaEntities {

	private static final List<EntityType<?>> SEMUA = new ArrayList<>();

	// ---------------------------------------------------------------- Pelabuhan (38.T.3)

	public static final EntityType<KapalDagangEntity> KAPAL_DAGANG = mekanisme("kapal_dagang", KapalDagangEntity::new, 4.0F, 3.0F, 12, 2);
	public static final EntityType<KapalBajakLautEntity> KAPAL_BAJAK_LAUT = mekanisme("kapal_bajak_laut", KapalBajakLautEntity::new, 4.0F, 3.0F, 12, 2);
	public static final EntityType<PerahuNelayanEntity> PERAHU_NELAYAN = mekanisme("perahu_nelayan", PerahuNelayanEntity::new, 2.0F, 0.9F, 8, 2);
	public static final EntityType<ApiSuarEntity> API_SUAR = mekanisme("api_suar", ApiSuarEntity::new, 1.5F, 1.5F, 12, 20);
	public static final EntityType<DerekDermagaEntity> DEREK_DERMAGA = mekanisme("derek_dermaga", DerekDermagaEntity::new, 2.0F, 8.0F, 8, 20);
	public static final EntityType<KapalGalanganEntity> KAPAL_GALANGAN = mekanisme("kapal_galangan", KapalGalanganEntity::new, 3.0F, 3.0F, 8, 20);

	// ---------------------------------------------------------------- Oasis (38.T.4)

	public static final EntityType<TimbaSumurEntity> TIMBA_SUMUR = mekanisme("timba_sumur", TimbaSumurEntity::new, 1.0F, 3.0F, 6, 20);
	public static final EntityType<TemuanPasirEntity> TEMUAN_PASIR = mekanisme("temuan_pasir", TemuanPasirEntity::new, 0.6F, 0.3F, 6, 20);
	public static final EntityType<PintuPasirEntity> PINTU_PASIR = mekanisme("pintu_pasir", PintuPasirEntity::new, 2.0F, 3.0F, 6, 20);
	public static final EntityType<PilarRuneEntity> PILAR_RUNE = mekanisme("pilar_rune", PilarRuneEntity::new, 0.6F, 2.0F, 6, 20);
	public static final EntityType<BatuUkurAirEntity> BATU_UKUR_AIR = mekanisme("batu_ukur_air", BatuUkurAirEntity::new, 0.6F, 2.0F, 6, 20);
	public static final EntityType<UntaKafilahEntity> UNTA_KAFILAH = daftar("unta_kafilah", EntityType.Builder.<UntaKafilahEntity>of(UntaKafilahEntity::new, MobCategory.MISC)
			.sized(1.7F, 2.375F)
			.eyeHeight(2.275F)
			.fireImmune()
			.noSave()
			.noSummon()
			.noLootTable()
			.clientTrackingRange(8));
	public static final EntityType<KalajengkingRajaEntity> KALAJENGKING_RAJA = daftar("kalajengking_raja", EntityType.Builder.<KalajengkingRajaEntity>of(KalajengkingRajaEntity::new, MobCategory.MISC)
			.sized(2.6F, 2.2F)
			.eyeHeight(1.4F)
			.noSave()
			.noSummon()
			.noLootTable()
			.clientTrackingRange(8));

	// ---------------------------------------------------------------- Air (38.T.5)

	public static final EntityType<PerahuKanalEntity> PERAHU_KANAL = mekanisme("perahu_kanal", PerahuKanalEntity::new, 2.0F, 0.9F, 8, 2);
	public static final EntityType<PerahuNagaEntity> PERAHU_NAGA = mekanisme("perahu_naga", PerahuNagaEntity::new, 2.0F, 1.0F, 8, 2);
	public static final EntityType<AlatTenunEntity> ALAT_TENUN = mekanisme("alat_tenun", AlatTenunEntity::new, 2.0F, 2.25F, 6, 20);

	// ---------------------------------------------------------------- Gunung (38.T.6)

	public static final EntityType<KeretaGantungEntity> KERETA_GANTUNG = mekanisme("kereta_gantung", KeretaGantungEntity::new, 2.5F, 2.5F, 10, 2);
	public static final EntityType<KatrolEntity> KATROL = mekanisme("katrol", KatrolEntity::new, 1.5F, 1.25F, 8, 2);
	public static final EntityType<RodaKatrolEntity> RODA_KATROL = mekanisme("roda_katrol", RodaKatrolEntity::new, 3.0F, 9.0F, 10, 20);
	public static final EntityType<BatuRuntuhEntity> BATU_RUNTUH = mekanisme("batu_runtuh", BatuRuntuhEntity::new, 1.25F, 1.25F, 8, 2);
	public static final EntityType<HerbaTebingEntity> HERBA_TEBING = mekanisme("herba_tebing", HerbaTebingEntity::new, 0.75F, 1.0F, 6, 20);
	public static final EntityType<PalangEntity> PALANG = mekanisme("palang", PalangEntity::new, 3.0F, 1.2F, 6, 20);
	public static final EntityType<TaliTurunEntity> TALI_TURUN = mekanisme("tali_turun", TaliTurunEntity::new, 0.4F, 1.0F, 8, 20);
	public static final EntityType<JeratEntity> JERAT = mekanisme("jerat", JeratEntity::new, 0.75F, 0.1F, 6, 20);
	public static final EntityType<KeraGunungEntity> KERA_GUNUNG = daftar("kera_gunung", EntityType.Builder.<KeraGunungEntity>of(KeraGunungEntity::new, MobCategory.MISC)
			.sized(0.6F, 0.7F)
			.eyeHeight(0.55F)
			.noSave()
			.noSummon()
			.noLootTable()
			.clientTrackingRange(6));

	// ---------------------------------------------------------------- Iblis (38.T.7)

	public static final EntityType<GerbangTengkorakEntity> GERBANG_TENGKORAK = mekanisme("gerbang_tengkorak", GerbangTengkorakEntity::new, 6.0F, 6.0F, 10, 20);
	public static final EntityType<PintuArenaEntity> PINTU_ARENA = mekanisme("pintu_arena", PintuArenaEntity::new, 3.0F, 4.0F, 8, 20);
	public static final EntityType<SelTawananEntity> SEL_TAWANAN = mekanisme("sel_tawanan", SelTawananEntity::new, 1.0F, 2.0F, 6, 20);
	public static final EntityType<SegelPintuEntity> SEGEL_PINTU = mekanisme("segel_pintu", SegelPintuEntity::new, 2.0F, 3.0F, 6, 20);
	public static final EntityType<TongMinyakEntity> TONG_MINYAK = mekanisme("tong_minyak", TongMinyakEntity::new, 0.9F, 1.1F, 6, 20);
	public static final EntityType<TuasGerbangEntity> TUAS_GERBANG = mekanisme("tuas_gerbang", TuasGerbangEntity::new, 0.5F, 1.0F, 6, 20);

	// ---------------------------------------------------------------- Bersama (38.T.9.3)

	public static final EntityType<TanduEntity> TANDU = mekanisme("tandu", TanduEntity::new, 1.5F, 2.0F, 8, 2);
	public static final EntityType<PintuAirEntity> PINTU_AIR = mekanisme("pintu_air", PintuAirEntity::new, 3.0F, 4.0F, 8, 20);

	/** Registers the creature attributes; from {@link KotaTema#init()} (loading the class registers the types). */
	static void init() {
		FabricDefaultAttributeRegistry.register(UNTA_KAFILAH, UntaKafilahEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(KALAJENGKING_RAJA, KalajengkingRajaEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(KERA_GUNUNG, KeraGunungEntity.createAttributes());
	}

	/** Every themed entity type in registration order ({@code uji tema model}, renderer registration checks). */
	public static List<EntityType<?>> semua() {
		return Collections.unmodifiableList(SEMUA);
	}

	private static <T extends Entity> EntityType<T> mekanisme(String id, EntityType.EntityFactory<T> factory, float w, float h,
			int trackChunks, int updateInterval) {
		return daftar(id, EntityType.Builder.of(factory, MobCategory.MISC)
				.sized(w, h)
				.fireImmune()
				.noSave()
				.noSummon()
				.noLootTable()
				.clientTrackingRange(trackChunks)
				.updateInterval(updateInterval));
	}

	private static <T extends Entity> EntityType<T> daftar(String id, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id(id));
		EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
		SEMUA.add(type);
		return type;
	}

	private TemaEntities() {
	}
}
