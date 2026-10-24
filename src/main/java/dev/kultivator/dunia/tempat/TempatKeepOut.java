package dev.kultivator.dunia.tempat;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.worldgen.TempatTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

/**
 * Structure-free zones around planned places (DESIGN 38.D.4; owner 38W). The mixin {@code ChunkGeneratorTempatMixin}
 * (on the private {@code ChunkGenerator.tryGenerateStructure}) asks {@link #tolak} before any start: a
 * {@code surface_structures} start whose chunk centre lies in the keep-out box of a planned place
 * ({@code Rencana.zonaDistrik}: capital districts + 3 chunks, compounds + 2, themed cities + 3) that is really born
 * (its shared terrain verdict, from the same generator the mixin runs in) is refused, unless it is part of a plan itself
 * (types {@code tata_letak} / {@code jalan}) or tagged {@code #kultivator:boleh_di_tempat} (road pieces, natural
 * wells). Strongholds and underground structures are never refused (eyes of ender stay right). Pure function of the
 * seed: the zone index is built once per seed and shared by all worldgen threads.
 */
public final class TempatKeepOut {

	/** Structures allowed inside a planned place's keep-out box. */
	public static final TagKey<Structure> BOLEH = TagKey.create(Registries.STRUCTURE, Kultivator.id("boleh_di_tempat"));

	private static volatile long seedIndeks = Long.MIN_VALUE;
	private static volatile TempatZones.IndeksRencana indeks = new TempatZones.IndeksRencana();
	private static volatile long seedTempat = Long.MIN_VALUE;
	private static volatile TempatZones.IndeksRencana tempat = new TempatZones.IndeksRencana();

	/** True when the start of {@code structure} in chunk {@code chunk} must be refused (no verdict source: plan only). */
	public static boolean tolak(long seed, Holder<Structure> structure, ChunkPos chunk) {
		return tolak(seed, structure, chunk, null);
	}

	/**
	 * True when the start of {@code structure} in chunk {@code chunk} must be refused for world seed {@code seed}. With
	 * a verdict source only boxes of places that are really born count; without one every planned box counts.
	 */
	public static boolean tolak(long seed, Holder<Structure> structure, ChunkPos chunk, @Nullable PutusanTanah.Sumber sumber) {
		Structure s = structure.value();
		if (s.step() != GenerationStep.Decoration.SURFACE_STRUCTURES) {
			return false;
		}
		if (s.type() == TempatTypes.TATA_LETAK || s.type() == TempatTypes.JALAN || structure.is(BOLEH)) {
			return false;
		}
		TempatZones.IndeksRencana zones = zonesFor(seed);
		if (zones.kosong()) {
			return false;
		}
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		if (sumber == null) {
			return !zones.di(x, z).isEmpty();
		}
		return zones.berisiLahir(x, z, sumber);
	}

	/**
	 * The mixin entry: like {@link #tolak(long, Holder, ChunkPos, PutusanTanah.Sumber)}, building the verdict source
	 * from the generator only when the chunk lies in a planned box (most chunks do not).
	 */
	public static boolean tolak(long seed, Holder<Structure> structure, ChunkPos chunk, ChunkGenerator generator, RandomState randomState,
			LevelHeightAccessor height) {
		Structure s = structure.value();
		if (s.step() != GenerationStep.Decoration.SURFACE_STRUCTURES) {
			return false;
		}
		TempatZones.IndeksRencana zones = zonesFor(seed);
		if (zones.kosong() || zones.di(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()).isEmpty()) {
			return false;
		}
		PutusanTanah.Sumber sumber = new PutusanTanah.Sumber(generator, randomState, height,
				generator.getBiomeSource().createUncachedResolver(randomState), seed);
		return tolak(seed, structure, chunk, sumber);
	}

	/**
	 * True when column {@code (x, z)} lies inside the box of a planned place that is really born (roads and road-side
	 * posts stop there; the place has its own streets). World generation passes its verdict source.
	 */
	public static boolean dalamTempatLahir(long seed, int x, int z, PutusanTanah.Sumber sumber) {
		if (seedTempat != seed) {
			synchronized (TempatKeepOut.class) {
				if (seedTempat != seed) {
					tempat = TempatZones.IndeksRencana.of(seed, 2);
					seedTempat = seed;
				}
			}
		}
		return !tempat.kosong() && tempat.berisiLahir(x, z, sumber);
	}

	/** Drop the zone indexes (a plan's config changed). */
	public static void lupakan() {
		seedIndeks = Long.MIN_VALUE;
		seedTempat = Long.MIN_VALUE;
	}

	private static TempatZones.IndeksRencana zonesFor(long seed) {
		if (seedIndeks != seed) {
			synchronized (TempatKeepOut.class) {
				if (seedIndeks != seed) {
					indeks = TempatZones.IndeksRencana.of(seed, 0);
					seedIndeks = seed;
				}
			}
		}
		return indeks;
	}

	private TempatKeepOut() {
	}
}
