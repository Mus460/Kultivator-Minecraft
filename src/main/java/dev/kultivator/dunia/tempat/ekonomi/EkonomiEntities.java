package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.Kultivator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Entity types of 38EK, the money loop (DESIGN 38.M.4.2, 38.O.2): the caravan cart.
 * Registered always (38.0 rule 17), from wave W0, so 38MD registers renderers from the start (38.E.0 step 1). Every
 * type is {@code noSave} / {@code noSummon}: bodies and mechanisms are re-made by anchors, quests or plans, never
 * stored in chunks. The owner (38EK) keeps this file and adds behaviour in its own classes.
 */
public final class EkonomiEntities {

	private static final ResourceKey<EntityType<?>> GEROBAK_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("gerobak"));
	public static final EntityType<GerobakEntity> GEROBAK = Registry.register(BuiltInRegistries.ENTITY_TYPE, GEROBAK_KEY,
			EntityType.Builder.<GerobakEntity>of(GerobakEntity::new, MobCategory.MISC)
					.sized(1.6F, 1.2F)
					.noSave()
					.noSummon()
					.noLootTable()
					.fireImmune()
					.clientTrackingRange(8)
					.build(GEROBAK_KEY));

	private static boolean registered;

	/** From the package init; safe to call twice. Touching the class registered the types above. */
	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
	}

	private EkonomiEntities() {
	}
}
