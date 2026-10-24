package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Entity types of 38GU caves and sites (DESIGN 38.C.4, 38.O.2): crystal guardian, tomb puppet, spirit rat,
 * cage door, glyph plate and cliff door.
 * Registered always (38.0 rule 17), from wave W0, so 38MD registers renderers from the start (38.E.0 step 1). Every
 * type is {@code noSave} / {@code noSummon}: bodies and mechanisms are re-made by anchors, quests or plans, never
 * stored in chunks. The owner (38GU) keeps this file and adds behaviour in its own classes.
 */
public final class GuaEntities {

	private static final ResourceKey<EntityType<?>> PENJAGA_KRISTAL_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("penjaga_kristal"));
	public static final EntityType<PenjagaKristalEntity> PENJAGA_KRISTAL = Registry.register(BuiltInRegistries.ENTITY_TYPE, PENJAGA_KRISTAL_KEY,
			EntityType.Builder.<PenjagaKristalEntity>of(PenjagaKristalEntity::new, MobCategory.MONSTER)
					.sized(1.4F, 2.4F)
					.noSave()
					.noSummon()
					.noLootTable()
					.fireImmune()
					.clientTrackingRange(8)
					.build(PENJAGA_KRISTAL_KEY));

	private static final ResourceKey<EntityType<?>> BONEKA_PENJAGA_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("boneka_penjaga"));
	public static final EntityType<BonekaPenjagaEntity> BONEKA_PENJAGA = Registry.register(BuiltInRegistries.ENTITY_TYPE, BONEKA_PENJAGA_KEY,
			EntityType.Builder.<BonekaPenjagaEntity>of(BonekaPenjagaEntity::new, MobCategory.MONSTER)
					.sized(0.7F, 1.95F)
					.noSave()
					.noSummon()
					.noLootTable()
					.fireImmune()
					.clientTrackingRange(8)
					.build(BONEKA_PENJAGA_KEY));

	private static final ResourceKey<EntityType<?>> TIKUS_ROH_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("tikus_roh"));
	public static final EntityType<TikusRohEntity> TIKUS_ROH = Registry.register(BuiltInRegistries.ENTITY_TYPE, TIKUS_ROH_KEY,
			EntityType.Builder.<TikusRohEntity>of(TikusRohEntity::new, MobCategory.CREATURE)
					.sized(0.5F, 0.4F)
					.noSave()
					.noSummon()
					.noLootTable()
					.fireImmune()
					.clientTrackingRange(6)
					.build(TIKUS_ROH_KEY));

	private static final ResourceKey<EntityType<?>> SANGKAR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("sangkar"));
	public static final EntityType<SangkarEntity> SANGKAR = Registry.register(BuiltInRegistries.ENTITY_TYPE, SANGKAR_KEY,
			EntityType.Builder.<SangkarEntity>of(SangkarEntity::new, MobCategory.MISC)
					.sized(1.0F, 1.5F)
					.noSave()
					.noSummon()
					.noLootTable()
					.fireImmune()
					.clientTrackingRange(6)
					.build(SANGKAR_KEY));

	private static final ResourceKey<EntityType<?>> LEMPENG_GLIF_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("lempeng_glif"));
	public static final EntityType<LempengGlifEntity> LEMPENG_GLIF = Registry.register(BuiltInRegistries.ENTITY_TYPE, LEMPENG_GLIF_KEY,
			EntityType.Builder.<LempengGlifEntity>of(LempengGlifEntity::new, MobCategory.MISC)
					.sized(0.9F, 0.15F)
					.noSave()
					.noSummon()
					.noLootTable()
					.fireImmune()
					.clientTrackingRange(6)
					.build(LEMPENG_GLIF_KEY));

	private static final ResourceKey<EntityType<?>> PINTU_TEBING_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("pintu_tebing"));
	public static final EntityType<PintuTebingEntity> PINTU_TEBING = Registry.register(BuiltInRegistries.ENTITY_TYPE, PINTU_TEBING_KEY,
			EntityType.Builder.<PintuTebingEntity>of(PintuTebingEntity::new, MobCategory.MISC)
					.sized(1.0F, 2.0F)
					.noSave()
					.noSummon()
					.noLootTable()
					.fireImmune()
					.clientTrackingRange(6)
					.build(PINTU_TEBING_KEY));

	private static boolean registered;

	/** From the package init; safe to call twice. Touching the class registered the types above. */
	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
		FabricDefaultAttributeRegistry.register(PENJAGA_KRISTAL, PenjagaKristalEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(BONEKA_PENJAGA, BonekaPenjagaEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(TIKUS_ROH, TikusRohEntity.createAttributes());
	}

	private GuaEntities() {
	}
}
