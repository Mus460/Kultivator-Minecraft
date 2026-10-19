package dev.kultivator.purba;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The entity types of P9-purba (DESIGN 26.4.2, 26.5.4): {@code kultivator:primordial_beast}, {@code kultivator:primordial_relic}
 * and {@code kultivator:heaven_envoy}. All are never written to disk ({@code noSave} plus the {@code ALLOW_LOAD} refusal of
 * {@link PurbaGuard}), cannot be summoned and have no loot table. Loaded by {@code Purba.init()}.
 */
public final class PurbaEntities {

	private static final ResourceKey<EntityType<?>> BEAST_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("primordial_beast"));
	private static final ResourceKey<EntityType<?>> RELIC_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("primordial_relic"));
	private static final ResourceKey<EntityType<?>> ENVOY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("heaven_envoy"));

	/** The primordial beast; the hitbox changes with kind and phase ({@code getDefaultDimensions}). */
	public static final EntityType<PrimordialBeastEntity> PRIMORDIAL_BEAST = Registry.register(BuiltInRegistries.ENTITY_TYPE, BEAST_KEY,
			EntityType.Builder.<PrimordialBeastEntity>of(PrimordialBeastEntity::new, MobCategory.MONSTER)
					.sized(8.0F, 3.0F)
					.fireImmune()
					.noSave()
					.noSummon()
					.noLootTable()
					.clientTrackingRange(10)
					.build(BEAST_KEY));

	/** Mata Angin and the Lilin. */
	public static final EntityType<PrimordialRelicEntity> PRIMORDIAL_RELIC = Registry.register(BuiltInRegistries.ENTITY_TYPE, RELIC_KEY,
			EntityType.Builder.<PrimordialRelicEntity>of(PrimordialRelicEntity::new, MobCategory.MONSTER)
					.sized(1.4F, 1.4F)
					.fireImmune()
					.noSave()
					.noSummon()
					.noLootTable()
					.clientTrackingRange(8)
					.build(RELIC_KEY));

	/** Utusan Murka Langit and the Panglima Langit. */
	public static final EntityType<HeavenEnvoyEntity> HEAVEN_ENVOY = Registry.register(BuiltInRegistries.ENTITY_TYPE, ENVOY_KEY,
			EntityType.Builder.<HeavenEnvoyEntity>of(HeavenEnvoyEntity::new, MobCategory.MONSTER)
					.sized(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.fireImmune()
					.noSave()
					.noSummon()
					.noLootTable()
					.clientTrackingRange(10)
					.build(ENVOY_KEY));

	/** Registers the default attributes. */
	static void init() {
		FabricDefaultAttributeRegistry.register(PRIMORDIAL_BEAST, PrimordialBeastEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(PRIMORDIAL_RELIC, PrimordialRelicEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(HEAVEN_ENVOY, HeavenEnvoyEntity.createAttributes());
	}

	private PurbaEntities() {
	}
}
