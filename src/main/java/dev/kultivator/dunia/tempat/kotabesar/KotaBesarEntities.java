package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.Kultivator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Entity types of 38KS, the capital (DESIGN 38.B.4.1, 38.O.2): the city gate leaf that closes at curfew.
 * Registered always (38.0 rule 17), from wave W0, so 38MD registers renderers from the start (38.E.0 step 1). Every
 * type is {@code noSave} / {@code noSummon}: bodies and mechanisms are re-made by anchors, quests or plans, never
 * stored in chunks. The owner (38KS) keeps this file and adds behaviour in its own classes.
 */
public final class KotaBesarEntities {

	private static final ResourceKey<EntityType<?>> GERBANG_KOTA_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("gerbang_kota"));
	public static final EntityType<GerbangKotaEntity> GERBANG_KOTA = Registry.register(BuiltInRegistries.ENTITY_TYPE, GERBANG_KOTA_KEY,
			EntityType.Builder.<GerbangKotaEntity>of(GerbangKotaEntity::new, MobCategory.MISC)
					.sized(2.0F, 4.0F)
					.noSave()
					.noSummon()
					.noLootTable()
					.fireImmune()
					.clientTrackingRange(10)
					.build(GERBANG_KOTA_KEY));

	private static boolean registered;

	/** From the package init; safe to call twice. Touching the class registered the types above. */
	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
	}

	private KotaBesarEntities() {
	}
}
