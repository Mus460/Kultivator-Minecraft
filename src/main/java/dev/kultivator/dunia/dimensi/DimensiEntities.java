package dev.kultivator.dunia.dimensi;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.dimensi.bawah.ArwahEntity;
import dev.kultivator.dunia.dimensi.laut.KepitingBatuEntity;
import dev.kultivator.dunia.dimensi.laut.LautNpcEntity;
import dev.kultivator.dunia.dimensi.laut.PenyuAoEntity;
import dev.kultivator.dunia.dimensi.laut.PenyuDekEntity;
import dev.kultivator.dunia.dimensi.laut.UlarLautEntity;
import dev.kultivator.dunia.dimensi.laut.YakshaEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Module D's entity types (DESIGN 34.6 D). Every type is {@code noSave}: the anchors, quests and spawners re-make them,
 * so nothing of this module ever piles up in saved chunks (the soul's body is a vanilla Mannequin, saved on purpose).
 * The deck colliders and the marks cannot be summoned.
 */
public final class DimensiEntities {

	public static final EntityType<ArwahEntity> ARWAH = register("arwah",
			EntityType.Builder.<ArwahEntity>of(ArwahEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F)
					.fireImmune().noSave().clientTrackingRange(8));
	public static final EntityType<LautNpcEntity> RAJA_NAGA = register("raja_naga",
			EntityType.Builder.<LautNpcEntity>of(LautNpcEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F)
					.noSave().clientTrackingRange(8));
	public static final EntityType<YakshaEntity> YAKSHA = register("yaksha",
			EntityType.Builder.<YakshaEntity>of(YakshaEntity::new, MobCategory.MONSTER).sized(0.7F, 2.1F).eyeHeight(1.85F)
					.noSave().clientTrackingRange(8));
	public static final EntityType<UlarLautEntity> ULAR_LAUT = register("ular_laut",
			EntityType.Builder.<UlarLautEntity>of(UlarLautEntity::new, MobCategory.MONSTER).sized(1.8F, 1.6F).eyeHeight(0.9F)
					.noSave().clientTrackingRange(10));
	public static final EntityType<KepitingBatuEntity> KEPITING_BATU = register("kepiting_batu",
			EntityType.Builder.<KepitingBatuEntity>of(KepitingBatuEntity::new, MobCategory.MONSTER).sized(1.9F, 1.1F).eyeHeight(0.8F)
					.noSave().clientTrackingRange(8));
	public static final EntityType<PenyuAoEntity> PENYU_AO = register("penyu_ao",
			EntityType.Builder.<PenyuAoEntity>of(PenyuAoEntity::new, MobCategory.MISC).sized(12.0F, 4.0F)
					.fireImmune().noSave().noSummon().clientTrackingRange(12).updateInterval(2));
	public static final EntityType<PenyuDekEntity> PENYU_DEK = register("penyu_dek",
			EntityType.Builder.<PenyuDekEntity>of(PenyuDekEntity::new, MobCategory.MISC).sized(PenyuDekEntity.WIDTH, PenyuDekEntity.HEIGHT)
					.fireImmune().noSave().noSummon().noLootTable().clientTrackingRange(12).updateInterval(2));
	public static final EntityType<TandaEntity> TANDA = register("tanda_dimensi",
			EntityType.Builder.<TandaEntity>of(TandaEntity::new, MobCategory.MISC).sized(1.0F, 1.6F)
					.fireImmune().noSave().noSummon().noLootTable().clientTrackingRange(10));

	private static <T extends Entity> EntityType<T> register(String id, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id(id));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	static void init() {
		FabricDefaultAttributeRegistry.register(ARWAH, DimensiNpc.createAttributes());
		FabricDefaultAttributeRegistry.register(RAJA_NAGA, DimensiNpc.createAttributes());
		FabricDefaultAttributeRegistry.register(YAKSHA, YakshaEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ULAR_LAUT, UlarLautEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(KEPITING_BATU, KepitingBatuEntity.createAttributes());
	}

	private DimensiEntities() {
	}
}
