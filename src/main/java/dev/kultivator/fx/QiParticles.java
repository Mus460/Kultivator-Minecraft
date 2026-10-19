package dev.kultivator.fx;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The qi particle types (polish pass S5): one {@link QiParticleOptions} per type, each with its own sprite set in
 * {@code assets/kultivator/particles/<id>.json}. Created as values here and registered by {@link #register} from the main
 * initializer (registries must match on both sides, so the client never registers them itself). The client provider
 * lives in {@code client/fx/qi}; until it is registered nothing spawns them.
 */
public final class QiParticles {

	/** The soft emissive mote (and the star glint with {@link QiParticleOptions#STAR}). */
	public static final ParticleType<QiParticleOptions> QI = make();
	/** A streak along its velocity (sword sparks, lightning crackle, speed lines). */
	public static final ParticleType<QiParticleOptions> QI_SPARK = make();
	/** A noisy puff, not emissive unless GLOW (smoke, ink, dust clouds, mist). */
	public static final ParticleType<QiParticleOptions> QI_SMOKE = make();
	/** A thin luminous ring, camera-facing or flat on the ground (GROUND_HUG). */
	public static final ParticleType<QiParticleOptions> QI_RING = make();
	/** A rising flame tongue. */
	public static final ParticleType<QiParticleOptions> QI_FLAME = make();
	/** A crystal shard that tumbles and falls (ice, glass, broken seals). */
	public static final ParticleType<QiParticleOptions> QI_SHARD = make();
	/** A drifting petal (lotus, blossom, willow). */
	public static final ParticleType<QiParticleOptions> QI_PETAL = make();
	/** A slowly falling snowflake. */
	public static final ParticleType<QiParticleOptions> QI_SNOW = make();
	/** A gliding feather (phoenix, crane, kunpeng). */
	public static final ParticleType<QiParticleOptions> QI_FEATHER = make();

	private static boolean registered;

	private static ParticleType<QiParticleOptions> make() {
		// alwaysSpawn = false: the vanilla "Particles" setting may still cull them, like every technique particle.
		return FabricParticleTypes.complex(false, QiParticleOptions::codec, QiParticleOptions::streamCodec);
	}

	/** Registers every type once (called by {@code Kultivator.onInitialize}). */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		reg("qi", QI);
		reg("qi_spark", QI_SPARK);
		reg("qi_smoke", QI_SMOKE);
		reg("qi_ring", QI_RING);
		reg("qi_flame", QI_FLAME);
		reg("qi_shard", QI_SHARD);
		reg("qi_petal", QI_PETAL);
		reg("qi_snow", QI_SNOW);
		reg("qi_feather", QI_FEATHER);
	}

	/** True once the types are in the registry (the client falls back to dust otherwise). */
	public static boolean registered() {
		return BuiltInRegistries.PARTICLE_TYPE.getKey(QI) != null;
	}

	private static void reg(String path, ParticleType<QiParticleOptions> type) {
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, Kultivator.id(path), type);
	}

	private QiParticles() {
	}
}
