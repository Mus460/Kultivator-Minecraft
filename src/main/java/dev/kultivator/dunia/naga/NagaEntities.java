package dev.kultivator.dunia.naga;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The entity types of module B (DESIGN 34.6 B): {@code kultivator:naga_liar} (a wild or tamed Jiao: one hitbox at the
 * head, the body is client only) and {@code kultivator:kesengsaraan_binatang} (the beast tribulation's telegraph host).
 * Neither is ever saved ({@code noSave}: a wild Jiao simply leaves with its chunk, a tamed one returns to its pearl, a
 * tribulation host is remade by nothing), neither can be summoned by players, neither has a loot table. Loaded by
 * {@code Naga.init()}.
 */
public final class NagaEntities {

	private static final ResourceKey<EntityType<?>> NAGA_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("naga_liar"));
	private static final ResourceKey<EntityType<?>> HOST_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("kesengsaraan_binatang"));

	/** The Jiao: the head hitbox is 2.0 × 1.2 (DESIGN 34.5.11); tracked from 16 chunks so the long body is seen from afar. */
	public static final EntityType<NagaLiarEntity> NAGA_LIAR = Registry.register(BuiltInRegistries.ENTITY_TYPE, NAGA_KEY,
			EntityType.Builder.<NagaLiarEntity>of(NagaLiarEntity::new, MobCategory.MISC)
					.sized(2.0F, 1.2F)
					.eyeHeight(0.7F)
					.fireImmune()
					.noSave()
					.noSummon()
					.noLootTable()
					.clientTrackingRange(16)
					.updateInterval(2)
					.build(NAGA_KEY));

	/** The tribulation host floating above the beast (never collides, never saved). */
	public static final EntityType<KesengsaraanEntity> KESENGSARAAN = Registry.register(BuiltInRegistries.ENTITY_TYPE, HOST_KEY,
			EntityType.Builder.<KesengsaraanEntity>of(KesengsaraanEntity::new, MobCategory.MISC)
					.sized(0.5F, 0.5F)
					.fireImmune()
					.noSave()
					.noSummon()
					.noLootTable()
					.clientTrackingRange(10)
					.updateInterval(4)
					.build(HOST_KEY));

	static void init() {
		FabricDefaultAttributeRegistry.register(NAGA_LIAR, NagaLiarEntity.createAttributes());
	}

	private NagaEntities() {
	}
}
