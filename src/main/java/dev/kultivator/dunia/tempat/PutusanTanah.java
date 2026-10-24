package dev.kultivator.dunia.tempat;

import dev.kultivator.Kultivator;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The shared terrain verdict of a planned place (DESIGN 38.B.2.4): a 7 x 7 grid of samples 72 blocks apart around the
 * centre ({@code WORLD_SURFACE_WG} and {@code OCEAN_FLOOR_WG}; water where the surface is above the floor) plus the
 * biome at the 9 inner district centres. Everything comes from {@code ChunkGenerator.getBaseHeight} and the biome
 * resolver, which are deterministic from the seed (26.3 {@code ChunkGenerator:662}), so all eleven capital starts and
 * the server (map, rumours, teleport) agree without sharing state. Results are cached per (plan, seed, candidate),
 * at most {@link #CACHE_MAX} entries.
 * <p>
 * Plans with other rules (sect compounds 38.C.3.1, themed cities 38.T.2.2) pass their own {@link Aturan}.
 */
public final class PutusanTanah {

	public static final int CACHE_MAX = 64;

	/** {@code #kultivator:kota_besar/tanah}: every Overworld land biome except oceans, mushroom fields, peaks, deep dark. */
	public static final TagKey<Biome> TANAH_KOTA_BESAR = TagKey.create(Registries.BIOME, Kultivator.id("kota_besar/tanah"));

	/**
	 * The rule set of one plan.
	 *
	 * @param langkah     distance between samples (blocks)
	 * @param sisi        samples per side (odd; 7 -> 49 samples)
	 * @param airMaks     most water samples allowed
	 * @param selisihMaks largest height difference (max - min ground) allowed
	 * @param atasLautMin lowest median above sea level
	 * @param atasLautMaks highest median above sea level
	 * @param bioma       biome tag every district centre must be in
	 * @param selDistrik  distance between district centres (biome samples at -1..+1 cells)
	 */
	public record Aturan(int langkah, int sisi, int airMaks, int selisihMaks, int atasLautMin, int atasLautMaks,
			TagKey<Biome> bioma, int selDistrik) {

		/** The capital rules (38.B.2.4); {@code kotaBesar.selisihMaks} changes {@code selisihMaks}. */
		public static Aturan kotaBesar(int selisihMaks) {
			return new Aturan(72, 7, 3, selisihMaks, 2, 48, TANAH_KOTA_BESAR, 144);
		}
	}

	/**
	 * Where the samples come from: a structure generation context (worldgen) or the server's overworld generator
	 * (simulation). Both read the same deterministic noise.
	 */
	public record Sumber(ChunkGenerator generator, RandomState randomState, LevelHeightAccessor height, BiomeResolver biomes, long seed) {

		public static Sumber of(Structure.GenerationContext context) {
			return new Sumber(context.chunkGenerator(), context.randomState(), context.heightAccessor(), context.biomeResolver(), context.seed());
		}

		public static Sumber of(ServerLevel level) {
			ChunkGenerator generator = level.getChunkSource().getGenerator();
			RandomState randomState = level.getChunkSource().randomState();
			return new Sumber(generator, randomState, level, generator.getBiomeSource().createUncachedResolver(randomState), level.getSeed());
		}

		public int surface(int x, int z) {
			return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, randomState);
		}

		public int floor(int x, int z) {
			return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, randomState);
		}

		public Holder<Biome> biome(int x, int y, int z) {
			return biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
		}
	}

	private static final Map<Long, Putusan> CACHE = new ConcurrentHashMap<>();

	/** The verdict for {@code kandidat} under {@code aturan} (cached). */
	public static Putusan of(Sumber sumber, Kandidat kandidat, Aturan aturan) {
		long key = sumber.seed() ^ (kandidat.rencana().hashCode() * 0x9E3779B97F4A7C15L) ^ ((long) kandidat.index() << 32) ^ aturan.hashCode();
		Putusan cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		Putusan result;
		try {
			result = compute(sumber, kandidat, aturan);
		} catch (RuntimeException e) {
			Kultivator.LOGGER.warn("Tempat terrain verdict failed for {} #{}", kandidat.rencana(), kandidat.index(), e);
			result = Putusan.tidak("galat");
		}
		if (CACHE.size() >= CACHE_MAX) {
			CACHE.clear();
		}
		CACHE.put(key, result);
		return result;
	}

	/** Forget every cached verdict (seed change, {@code uji tempat rencana segar}). */
	public static void lupakan() {
		CACHE.clear();
	}

	private static Putusan compute(Sumber sumber, Kandidat kandidat, Aturan aturan) {
		int half = aturan.sisi() / 2;
		int n = aturan.sisi() * aturan.sisi();
		int[] ground = new int[n];
		int water = 0;
		int i = 0;
		for (int gx = -half; gx <= half; gx++) {
			for (int gz = -half; gz <= half; gz++) {
				int x = kandidat.pusatX() + gx * aturan.langkah();
				int z = kandidat.pusatZ() + gz * aturan.langkah();
				int surface = sumber.surface(x, z);
				int floor = sumber.floor(x, z);
				if (surface > floor) {
					water++;
				}
				ground[i++] = floor;
			}
		}
		if (water > aturan.airMaks()) {
			return Putusan.tidak("air " + water + " > " + aturan.airMaks());
		}
		int[] sorted = ground.clone();
		Arrays.sort(sorted);
		int spread = sorted[n - 1] - sorted[0];
		if (spread > aturan.selisihMaks()) {
			return Putusan.tidak("selisih " + spread + " > " + aturan.selisihMaks());
		}
		int median = sorted[n / 2];
		int sea = sumber.generator().getSeaLevel();
		if (median < sea + aturan.atasLautMin() || median > sea + aturan.atasLautMaks()) {
			return Putusan.tidak("tinggi " + (median - sea));
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int x = kandidat.pusatX() + dx * aturan.selDistrik();
				int z = kandidat.pusatZ() + dz * aturan.selDistrik();
				if (!sumber.biome(x, median, z).is(aturan.bioma())) {
					return Putusan.tidak("bioma");
				}
			}
		}
		return Putusan.ya(Math.clamp(median, sea + aturan.atasLautMin(), sea + aturan.atasLautMaks()));
	}

	private PutusanTanah() {
	}
}
