package dev.kultivator.dunia.tempat;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * Deterministic lot element choice (DESIGN 38.B.2.3 step 4): a weighted pick from the lot pool driven by a hash of
 * (seed, plan, candidate, district, lot index), never by the structure context random, so the server simulation
 * ({@code TempatApi.potongan}, NPC points, repair blueprints) knows which variant stands in every lot without reading
 * chunks. Pool weights are honoured exactly as vanilla ({@code StructureTemplatePool.getRandomTemplate}).
 */
public final class LotPick {

	/** The hash of one lot (also usable by modules that need a stable per-lot number, e.g. shop names). */
	public static long hash(long seed, Kandidat kandidat, String distrik, int lot) {
		long h = seed * 0x9E3779B97F4A7C15L;
		h = mix(h ^ kandidat.rencana().hashCode());
		h = mix(h ^ kandidat.index());
		h = mix(h ^ distrik.hashCode());
		h = mix(h ^ lot);
		return h;
	}

	/** The element of {@code pool} standing in lot {@code lot} of {@code distrik} (the empty element for an empty pool). */
	public static StructurePoolElement pilih(long seed, Kandidat kandidat, String distrik, int lot, StructureTemplatePool pool) {
		return pool.getRandomTemplate(RandomSource.create(hash(seed, kandidat, distrik, lot)));
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private LotPick() {
	}
}
