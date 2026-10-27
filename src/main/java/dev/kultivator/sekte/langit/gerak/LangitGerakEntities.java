package dev.kultivator.sekte.langit.gerak;

import dev.kultivator.Kultivator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The entity type of module 37M (DESIGN 37.5.5): {@code kultivator:feri_awan}, the Cloud Ferry (3.0 × 1.2, tracked 10
 * chunks, position sent every tick so the seated riders stay smooth). Registered always, whatever the config says (audit
 * K1). The type stays serializable (vanilla refuses to seat anyone on a type that is not), the entity itself is only
 * written while it carries a disciple ({@code FeriAwanEntity.shouldBeSaved()}). Not summonable, no loot.
 */
public final class LangitGerakEntities {

	private static final ResourceKey<EntityType<?>> FERI_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("feri_awan"));

	public static final EntityType<FeriAwanEntity> FERI = Registry.register(BuiltInRegistries.ENTITY_TYPE, FERI_KEY,
			EntityType.Builder.<FeriAwanEntity>of(FeriAwanEntity::new, MobCategory.MISC)
					.sized(FeriAwanEntity.WIDTH, FeriAwanEntity.HEIGHT)
					.fireImmune()
					.noSummon()
					.noLootTable()
					.clientTrackingRange(10)
					.updateInterval(1)
					.build(FERI_KEY));

	/** Loads the class so the type above is registered (from {@code LangitGerak.init}). */
	static void init() {
	}

	private LangitGerakEntities() {
	}
}
