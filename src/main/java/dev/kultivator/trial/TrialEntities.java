package dev.kultivator.trial;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The trial module's entity: {@code kultivator:heart_demon}. It is never written to disk ({@code noSave}), cannot be
 * summoned with /summon (it needs an owner; the debug command spawns one), has no loot table and does not burn.
 */
public final class TrialEntities {

	private static final ResourceKey<EntityType<?>> HEART_DEMON_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("heart_demon"));

	public static final EntityType<HeartDemonEntity> HEART_DEMON = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			HEART_DEMON_KEY,
			EntityType.Builder.of(HeartDemonEntity::new, MobCategory.MONSTER)
					.sized(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.fireImmune()
					.noSave()
					.noSummon()
					.noLootTable()
					.clientTrackingRange(10)
					.build(HEART_DEMON_KEY));

	/** Called from {@link HeartDemons#init()}: loads the registration above and gives the type its attributes. */
	public static void init() {
		FabricDefaultAttributeRegistry.register(HEART_DEMON, HeartDemonEntity.createAttributes());
	}

	private TrialEntities() {
	}
}
