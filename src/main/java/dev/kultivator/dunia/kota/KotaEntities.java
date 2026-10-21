package dev.kultivator.dunia.kota;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaBudget;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Module K's entity types (DESIGN 34.6 K): {@code kultivator:kultivator_npc} (never saved, never summoned, no loot
 * table; refused on load as a safety net, like the rogues) and {@code kultivator:kota_pintu} (the hidden black-market
 * door, never saved). Both are re-made by their anchors or spawners; every NPC counts toward {@link DuniaBudget#NPC}.
 */
public final class KotaEntities {

	private static final ResourceKey<EntityType<?>> NPC_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("kultivator_npc"));
	private static final ResourceKey<EntityType<?>> PINTU_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("kota_pintu"));

	public static final EntityType<KultivatorNpc> NPC = Registry.register(BuiltInRegistries.ENTITY_TYPE, NPC_KEY,
			EntityType.Builder.<KultivatorNpc>of(KultivatorNpc::new, MobCategory.MISC)
					.sized(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.noSave()
					.noSummon()
					.noLootTable()
					.clientTrackingRange(8)
					.build(NPC_KEY));

	public static final EntityType<KotaPintuEntity> PINTU = Registry.register(BuiltInRegistries.ENTITY_TYPE, PINTU_KEY,
			EntityType.Builder.<KotaPintuEntity>of(KotaPintuEntity::new, MobCategory.MISC)
					.sized(1.0F, 2.0F)
					.noSave()
					.noSummon()
					.noLootTable()
					.fireImmune()
					.clientTrackingRange(6)
					.build(PINTU_KEY));

	static void init() {
		FabricDefaultAttributeRegistry.register(NPC, KultivatorNpc.createAttributes());
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, flag) -> reason != EntitySpawnReason.LOAD
				|| !(entity instanceof KultivatorNpc) && !(entity instanceof KotaPintuEntity) && !entity.entityTags().contains(KultivatorNpc.TAG));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof KultivatorNpc npc) {
				DuniaBudget.track(DuniaBudget.NPC, npc);
			}
		});
	}

	private KotaEntities() {
	}
}
