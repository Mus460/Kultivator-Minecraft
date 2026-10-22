package dev.kultivator.dunia.kapal;

import dev.kultivator.Kultivator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The ship's entity types (DESIGN 34.5.15): {@code kultivator:kapal_roh} (the ship; saved, tracked 10 chunks, position
 * sent every tick so a moving hull and its seated riders stay smooth) and {@code kultivator:kapal_dek} (a docked deck
 * collider; never saved, never summonable) and {@code kultivator:kapal_bajak} (the sky pirates' raider that draws
 * alongside a halted ship; scenery, never saved). Passengers sit on the ship itself in synced seats, so no seat entity
 * exists.
 */
public final class KapalEntities {

	private static final ResourceKey<EntityType<?>> ROH_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("kapal_roh"));
	private static final ResourceKey<EntityType<?>> DEK_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("kapal_dek"));
	private static final ResourceKey<EntityType<?>> BAJAK_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("kapal_bajak"));

	public static final EntityType<KapalRohEntity> ROH = Registry.register(BuiltInRegistries.ENTITY_TYPE, ROH_KEY,
			EntityType.Builder.<KapalRohEntity>of(KapalRohEntity::new, MobCategory.MISC)
					.sized(2.0F, 1.6F)
					.fireImmune()
					.noSummon()
					.noLootTable()
					.clientTrackingRange(10)
					.updateInterval(1)
					.build(ROH_KEY));

	public static final EntityType<KapalDekEntity> DEK = Registry.register(BuiltInRegistries.ENTITY_TYPE, DEK_KEY,
			EntityType.Builder.<KapalDekEntity>of(KapalDekEntity::new, MobCategory.MISC)
					.sized(3.0F, KapalDekEntity.THICKNESS)
					.fireImmune()
					.noSummon()
					.noSave()
					.noLootTable()
					.clientTrackingRange(8)
					.updateInterval(20)
					.build(DEK_KEY));

	public static final EntityType<KapalBajakEntity> BAJAK = Registry.register(BuiltInRegistries.ENTITY_TYPE, BAJAK_KEY,
			EntityType.Builder.<KapalBajakEntity>of(KapalBajakEntity::new, MobCategory.MISC)
					.sized(2.0F, 1.6F)
					.fireImmune()
					.noSummon()
					.noSave()
					.noLootTable()
					.clientTrackingRange(10)
					.updateInterval(1)
					.build(BAJAK_KEY));

	/** Loads the class so the types above are registered. */
	static void init() {
	}

	private KapalEntities() {
	}
}
