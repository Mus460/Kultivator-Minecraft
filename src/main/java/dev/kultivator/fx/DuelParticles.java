package dev.kultivator.fx;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The particle types of the sword duel (DESIGN 35.4.2, module V): {@code kultivator:duel_spark} (a blade-contact spark: a
 * hot streak under gravity that bounces once on the ground and cools from white to dark red) and
 * {@code kultivator:duel_ember} (a small glowing ember drifting down and flickering). Both reuse the Kenney film sprites
 * ({@code particles/duel_spark.json}, {@code duel_ember.json}). Registered on both sides from {@code Duel.init} (registries
 * must match), drawn only by {@code client/duel/fx/DuelSparkParticle} and {@code DuelEmberParticle}; nothing on the
 * server spawns them, they are presentation only.
 */
public final class DuelParticles {

	public static final SimpleParticleType SPARK = FabricParticleTypes.simple(false);
	public static final SimpleParticleType EMBER = FabricParticleTypes.simple(false);

	private static boolean registered;

	/** Registers both types once (called by {@code dev.kultivator.duel.Duel.init}). */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, Kultivator.id("duel_spark"), SPARK);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, Kultivator.id("duel_ember"), EMBER);
	}

	/** True once the types are in the registry (a client of an older server jar draws film sparks instead). */
	public static boolean registered() {
		return BuiltInRegistries.PARTICLE_TYPE.getKey(SPARK) != null;
	}

	private DuelParticles() {
	}
}
