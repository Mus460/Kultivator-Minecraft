package dev.kultivator.sekte.turnamen;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Module T's entity type (DESIGN 36.2.3): {@code kultivator:penonton}, the visiting spectator. Never saved, never
 * summoned by commands (the tournament makes and removes it), tracked within 4 chunks (the 64-block LOD of 36.3.9).
 */
public final class TurnamenEntities {

	public static EntityType<Penonton> PENONTON;
	private static boolean done;

	static void init() {
		if (done) {
			return;
		}
		done = true;
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("penonton"));
		PENONTON = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.<Penonton>of(Penonton::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F)
						.noSave().noSummon().noLootTable().fireImmune().clientTrackingRange(4).updateInterval(3).build(key));
		FabricDefaultAttributeRegistry.register(PENONTON, Penonton.createAttributes());
	}

	private TurnamenEntities() {
	}
}
