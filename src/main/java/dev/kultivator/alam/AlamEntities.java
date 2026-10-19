package dev.kultivator.alam;

import dev.kultivator.Kultivator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The secret realm entity type {@code kultivator:realm_relic} (DESIGN 25.3.6): never written to disk, not summonable, no loot
 * table, fire immune. Loaded by {@code Alam.init()}.
 */
public final class AlamEntities {

	private static final ResourceKey<EntityType<?>> RELIC_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("realm_relic"));

	public static final EntityType<RealmRelicEntity> REALM_RELIC = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			RELIC_KEY,
			EntityType.Builder.<RealmRelicEntity>of(RealmRelicEntity::new, MobCategory.MISC)
					.sized(1.2F, 2.4F)
					.fireImmune()
					.noSave()
					.noSummon()
					.noLootTable()
					.clientTrackingRange(8)
					.build(RELIC_KEY));

	/** Loads the registration above. */
	public static void init() {
	}

	private AlamEntities() {
	}
}
