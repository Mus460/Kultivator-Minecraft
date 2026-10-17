package dev.kultivator.disciple;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The disciple entity type {@code kultivator:disciple} (a humanoid 0.6 x 1.8 like a player) and its base attributes.
 * Disciples are saved with the world, have no loot table (death leaves only the soul tablet) and cannot be made with
 * /summon (a disciple needs a master; {@code /kultivasi debug murid summon} recruits one for free).
 */
public final class DiscipleEntities {

	private static final ResourceKey<EntityType<?>> DISCIPLE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("disciple"));

	public static final EntityType<DiscipleEntity> DISCIPLE = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			DISCIPLE_KEY,
			EntityType.Builder.of(DiscipleEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.noSummon()
					.noLootTable()
					.clientTrackingRange(10)
					.build(DISCIPLE_KEY));

	// Base attributes of a fresh Mortal disciple (cultivation adds permanent modifiers on top, see refreshSynced).
	public static final double BASE_HEALTH = 24.0D;
	public static final double BASE_ATTACK = 3.0D;
	public static final double BASE_SPEED = 0.3D;
	public static final double BASE_FOLLOW_RANGE = 32.0D;
	public static final double BASE_ARMOR = 2.0D;

	/** Permanent modifier ids applied from the disciple's cultivation bonuses. */
	public static final Identifier HEALTH_ID = Kultivator.id("disciple_health");
	public static final Identifier ATTACK_ID = Kultivator.id("disciple_attack");
	public static final Identifier SPEED_ID = Kultivator.id("disciple_speed");

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, BASE_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, BASE_ATTACK)
				.add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
				.add(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE)
				.add(Attributes.ARMOR, BASE_ARMOR);
	}

	/** Called from {@link Disciples#init()}: loads the registration above and gives the type its attributes. */
	public static void init() {
		FabricDefaultAttributeRegistry.register(DISCIPLE, createAttributes());
	}

	private DiscipleEntities() {
	}
}
