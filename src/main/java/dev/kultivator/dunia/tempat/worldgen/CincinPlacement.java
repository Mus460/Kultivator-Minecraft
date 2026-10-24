package dev.kultivator.dunia.tempat.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.Kultivator;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

import java.util.List;
import java.util.Optional;

/**
 * {@code kultivator:cincin_tempat} (DESIGN 38.D.1, 38.C.9): the vanilla random spread plus
 * <ul>
 *   <li>{@code min_chunks} (default 6): no start within that many chunks of the world centre (rule W4, like
 *       {@code kultivator:jauh_dari_pusat});</li>
 *   <li>{@code cincin}: {@code [{sampai_chunk, frekuensi}]} distance rings; a candidate chunk survives with the
 *       frequency of the first ring whose {@code sampai_chunk} reaches its distance (the last ring applies beyond), from
 *       a hash of (seed, salt, chunk), so places thin out or grow denser with distance from spawn;</li>
 *   <li>{@code jarak_tempat} (0-16, default 8): refused when a start of another structure set in the structure-set tag
 *       {@code #kultivator:tempat_berpenghuni} lies within that many chunks ("full but not crowded", 38.C.9 rule 1),
 *       through {@code ChunkGeneratorStructureState.hasStructureChunkInRange}. While answering for another set the
 *       nested check is skipped (thread-local guard), so two inhabited sets never recurse into each other.</li>
 * </ul>
 * Everything else (spacing, separation, salt, frequency, exclusion zone, locate offset, spread type) is vanilla, and
 * {@code /locate} keeps working.
 */
public class CincinPlacement extends RandomSpreadStructurePlacement {

	/** Structure sets counted as inhabited places for {@code jarak_tempat}. */
	public static final TagKey<StructureSet> BERPENGHUNI = TagKey.create(Registries.STRUCTURE_SET, Kultivator.id("tempat_berpenghuni"));

	/** One distance ring: up to {@code sampaiChunk} chunks from the centre the frequency is {@code frekuensi}. */
	public record Cincin(int sampaiChunk, float frekuensi) {

		public static final Codec<Cincin> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(0, 1_000_000).fieldOf("sampai_chunk").forGetter(Cincin::sampaiChunk),
				Codec.floatRange(0.0F, 1.0F).fieldOf("frekuensi").forGetter(Cincin::frekuensi))
				.apply(i, Cincin::new));
	}

	public static final MapCodec<CincinPlacement> CODEC = RecordCodecBuilder.<CincinPlacement>mapCodec(
			i -> placementCodec(i)
					.and(i.group(
							Codec.intRange(1, 4096).fieldOf("spacing").forGetter(RandomSpreadStructurePlacement::spacing),
							Codec.intRange(0, 4096).fieldOf("separation").forGetter(RandomSpreadStructurePlacement::separation),
							RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(RandomSpreadStructurePlacement::spreadType)))
					.and(Codec.intRange(0, 4096).optionalFieldOf("min_chunks", 6).forGetter(CincinPlacement::minChunks))
					.and(Cincin.CODEC.listOf().optionalFieldOf("cincin", List.of()).forGetter(CincinPlacement::cincin))
					.and(Codec.intRange(0, 16).optionalFieldOf("jarak_tempat", 8).forGetter(CincinPlacement::jarakTempat))
					.apply(i, CincinPlacement::new));

	private static final ThreadLocal<Boolean> NESTED = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private final int minChunks;
	private final List<Cincin> cincin;
	private final int jarakTempat;

	public CincinPlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt,
			Optional<ExclusionZone> exclusionZone, int spacing, int separation, RandomSpreadType spreadType, int minChunks,
			List<Cincin> cincin, int jarakTempat) {
		super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, Math.max(spacing, separation + 1), separation, spreadType);
		this.minChunks = minChunks;
		this.cincin = List.copyOf(cincin);
		this.jarakTempat = jarakTempat;
	}

	public int minChunks() {
		return minChunks;
	}

	public List<Cincin> cincin() {
		return cincin;
	}

	public int jarakTempat() {
		return jarakTempat;
	}

	@Override
	protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int sourceX, int sourceZ) {
		if ((long) sourceX * sourceX + (long) sourceZ * sourceZ < (long) minChunks * minChunks) {
			return false;
		}
		return super.isPlacementChunk(state, sourceX, sourceZ);
	}

	@Override
	public boolean applyAdditionalChunkRestrictions(int sourceX, int sourceZ, long levelSeed) {
		if (!super.applyAdditionalChunkRestrictions(sourceX, sourceZ, levelSeed)) {
			return false;
		}
		float f = frequencyAt(sourceX, sourceZ);
		return f >= 1.0F || f > 0.0F && roll(levelSeed, salt(), sourceX, sourceZ) < f;
	}

	@Override
	public boolean applyInteractionsWithOtherStructures(ChunkGeneratorStructureState state, int sourceX, int sourceZ) {
		if (!super.applyInteractionsWithOtherStructures(state, sourceX, sourceZ)) {
			return false;
		}
		if (jarakTempat <= 0 || NESTED.get()) {
			return true;
		}
		NESTED.set(Boolean.TRUE);
		try {
			for (Holder<StructureSet> set : state.possibleStructureSets()) {
				if (set.value().placement() == this || !set.is(BERPENGHUNI)) {
					continue;
				}
				if (state.hasStructureChunkInRange(set, sourceX, sourceZ, jarakTempat)) {
					return false;
				}
			}
			return true;
		} finally {
			NESTED.set(Boolean.FALSE);
		}
	}

	/** The ring frequency at chunk {@code (x, z)} (1 when no ring is given). */
	public float frequencyAt(int x, int z) {
		if (cincin.isEmpty()) {
			return 1.0F;
		}
		double d = Math.sqrt((double) x * x + (double) z * z);
		for (Cincin c : cincin) {
			if (d <= c.sampaiChunk()) {
				return c.frekuensi();
			}
		}
		return cincin.get(cincin.size() - 1).frekuensi();
	}

	private static double roll(long seed, int salt, int x, int z) {
		long h = seed ^ (salt * 0x9E3779B97F4A7C15L) ^ (x * 341873128712L) ^ (z * 132897987541L);
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		h ^= h >>> 31;
		return (h >>> 11) * 0x1.0p-53;
	}

	@Override
	public MapCodec<RandomSpreadStructurePlacement> codec() {
		@SuppressWarnings({"unchecked", "rawtypes"})
		MapCodec<RandomSpreadStructurePlacement> codec = (MapCodec) CODEC;
		return codec;
	}
}
