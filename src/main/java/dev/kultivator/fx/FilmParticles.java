package dev.kultivator.fx;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;

/**
 * The film particle types of Phase 30 (DESIGN 30.2.11, contract C30.4): thirteen simple types whose sprites come from
 * Kenney's CC0 Particle Pack (white sprites, tinted by the client). Registered on both sides from the main initializer
 * (registries must match), drawn only by {@code client/epic/film/FilmParticleProviders}. Nothing on the server spawns
 * them; they are presentation only.
 */
public final class FilmParticles {

	public static final SimpleParticleType SPARK = FabricParticleTypes.simple(false);
	public static final SimpleParticleType FLARE = FabricParticleTypes.simple(false);
	public static final SimpleParticleType STAR = FabricParticleTypes.simple(false);
	public static final SimpleParticleType SMOKE = FabricParticleTypes.simple(false);
	public static final SimpleParticleType FLAME = FabricParticleTypes.simple(false);
	public static final SimpleParticleType MAGIC = FabricParticleTypes.simple(false);
	public static final SimpleParticleType SLASH = FabricParticleTypes.simple(false);
	public static final SimpleParticleType TRACE = FabricParticleTypes.simple(false);
	public static final SimpleParticleType TWIRL = FabricParticleTypes.simple(false);
	public static final SimpleParticleType CIRCLE = FabricParticleTypes.simple(false);
	public static final SimpleParticleType MUZZLE = FabricParticleTypes.simple(false);
	public static final SimpleParticleType DIRT = FabricParticleTypes.simple(false);
	public static final SimpleParticleType SYMBOL = FabricParticleTypes.simple(false);

	/** Every type with its id path ({@code kultivator:film_<name>}), in registration order. */
	public static final List<Entry> ALL = List.of(
			new Entry("film_spark", SPARK), new Entry("film_flare", FLARE), new Entry("film_star", STAR),
			new Entry("film_smoke", SMOKE), new Entry("film_flame", FLAME), new Entry("film_magic", MAGIC),
			new Entry("film_slash", SLASH), new Entry("film_trace", TRACE), new Entry("film_twirl", TWIRL),
			new Entry("film_circle", CIRCLE), new Entry("film_muzzle", MUZZLE), new Entry("film_dirt", DIRT),
			new Entry("film_symbol", SYMBOL));

	public record Entry(String path, SimpleParticleType type) {
	}

	private static boolean registered;

	/** Registers every type once (called by {@code Kultivator.onInitialize}). */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		for (Entry entry : ALL) {
			Registry.register(BuiltInRegistries.PARTICLE_TYPE, Kultivator.id(entry.path()), entry.type());
		}
	}

	/** True once the types are in the registry. */
	public static boolean registered() {
		return BuiltInRegistries.PARTICLE_TYPE.getKey(SPARK) != null;
	}

	private FilmParticles() {
	}
}
