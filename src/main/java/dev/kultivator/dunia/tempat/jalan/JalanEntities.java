package dev.kultivator.dunia.tempat.jalan;

import dev.kultivator.Kultivator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Entity types of 38W roads (DESIGN 38.C.6.2, 38.O.2): the ferry raft of a wide-river crossing.
 * Registered always (38.0 rule 17), from wave W0, so 38MD registers renderers from the start (38.E.0 step 1). Every
 * type is {@code noSave} / {@code noSummon}: bodies and mechanisms are re-made by anchors, quests or plans, never
 * stored in chunks. The owner (38W) keeps this file and adds behaviour in its own classes.
 */
public final class JalanEntities {

	private static final ResourceKey<EntityType<?>> RAKIT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("rakit"));
	public static final EntityType<RakitEntity> RAKIT = Registry.register(BuiltInRegistries.ENTITY_TYPE, RAKIT_KEY,
			EntityType.Builder.<RakitEntity>of(RakitEntity::new, MobCategory.MISC)
					.sized(1.8F, 0.4F)
					.noSave()
					.noSummon()
					.noLootTable()
					.fireImmune()
					.clientTrackingRange(8)
					.build(RAKIT_KEY));

	private static boolean registered;

	/** From the package init; safe to call twice. Touching the class registered the types above. */
	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
	}

	private JalanEntities() {
	}
}
