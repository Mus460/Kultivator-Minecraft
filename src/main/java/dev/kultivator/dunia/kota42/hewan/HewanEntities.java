package dev.kultivator.dunia.kota42.hewan;

import dev.kultivator.dunia.kota42.acara.KotaAcaraEntities;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The seven town animal types of DESIGN 42 (42.7.5, 42.12.2), registered always by {@link #register()} through the shared
 * helper of {@link KotaAcaraEntities} (noSave, noSummon, no loot table, refused on load, budget
 * {@link KotaAcaraEntities#ANGGARAN}); {@link #register()} also registers the default attributes of the six mobs.
 * Renderers and models are 42V-H's ({@code client/dunia/kota42/model/hewan/**}); behaviour is 42D-H's ({@code HewanKota}).
 * <ul>
 *   <li>{@code kultivator:kucing_kota} {@link KucingKotaEntity} 0.5 x 0.6</li>
 *   <li>{@code kultivator:anjing_kota} {@link AnjingKotaEntity} 0.6 x 0.8 (guard 0.8 x 1.0 by variant)</li>
 *   <li>{@code kultivator:ayam_kota} {@link AyamKotaEntity} 0.4 x 0.7 (chick by variant)</li>
 *   <li>{@code kultivator:kawanan_merpati} {@link KawananMerpatiEntity} 2 x 1, no collision (one flock, client boid)</li>
 *   <li>{@code kultivator:kuda_tambat} {@link KudaTambatEntity} 1.4 x 1.6</li>
 *   <li>{@code kultivator:unta_tambat} {@link UntaTambatEntity} 1.7 x 2.3</li>
 *   <li>{@code kultivator:keledai_kota} {@link KeledaiKotaEntity} 0.9 x 1.2</li>
 * </ul>
 */
public final class HewanEntities {

	public static final EntityType<KucingKotaEntity> KUCING_KOTA = KotaAcaraEntities.daftar("kucing_kota",
			EntityType.Builder.<KucingKotaEntity>of(KucingKotaEntity::new, MobCategory.MISC)
					.sized(0.5F, 0.6F).eyeHeight(0.5F).noSave().noSummon().noLootTable().clientTrackingRange(6));
	public static final EntityType<AnjingKotaEntity> ANJING_KOTA = KotaAcaraEntities.daftar("anjing_kota",
			EntityType.Builder.<AnjingKotaEntity>of(AnjingKotaEntity::new, MobCategory.MISC)
					.sized(0.6F, 0.8F).eyeHeight(0.68F).noSave().noSummon().noLootTable().clientTrackingRange(6));
	public static final EntityType<AyamKotaEntity> AYAM_KOTA = KotaAcaraEntities.daftar("ayam_kota",
			EntityType.Builder.<AyamKotaEntity>of(AyamKotaEntity::new, MobCategory.MISC)
					.sized(0.4F, 0.7F).eyeHeight(0.6F).noSave().noSummon().noLootTable().clientTrackingRange(6));
	public static final EntityType<KawananMerpatiEntity> KAWANAN_MERPATI = KotaAcaraEntities.mekanisme("kawanan_merpati",
			KawananMerpatiEntity::new, 2.0F, 1.0F, 8, 10);
	public static final EntityType<KudaTambatEntity> KUDA_TAMBAT = KotaAcaraEntities.daftar("kuda_tambat",
			EntityType.Builder.<KudaTambatEntity>of(KudaTambatEntity::new, MobCategory.MISC)
					.sized(1.4F, 1.6F).eyeHeight(1.52F).noSave().noSummon().noLootTable().clientTrackingRange(8));
	public static final EntityType<UntaTambatEntity> UNTA_TAMBAT = KotaAcaraEntities.daftar("unta_tambat",
			EntityType.Builder.<UntaTambatEntity>of(UntaTambatEntity::new, MobCategory.MISC)
					.sized(1.7F, 2.3F).eyeHeight(2.2F).noSave().noSummon().noLootTable().clientTrackingRange(8));
	public static final EntityType<KeledaiKotaEntity> KELEDAI_KOTA = KotaAcaraEntities.daftar("keledai_kota",
			EntityType.Builder.<KeledaiKotaEntity>of(KeledaiKotaEntity::new, MobCategory.MISC)
					.sized(0.9F, 1.2F).eyeHeight(1.1F).noSave().noSummon().noLootTable().clientTrackingRange(6));

	private static boolean registered;

	/** Loads the types above, registers the mobs' attributes and the shared 42 load guard; safe to call twice. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		KotaAcaraEntities.register();
		FabricDefaultAttributeRegistry.register(KUCING_KOTA, KucingKotaEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ANJING_KOTA, AnjingKotaEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(AYAM_KOTA, AyamKotaEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(KUDA_TAMBAT, KudaTambatEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(UNTA_TAMBAT, UntaTambatEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(KELEDAI_KOTA, KeledaiKotaEntity.createAttributes());
	}

	private HewanEntities() {
	}
}
