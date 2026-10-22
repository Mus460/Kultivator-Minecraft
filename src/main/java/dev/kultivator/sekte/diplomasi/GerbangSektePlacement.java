package dev.kultivator.sekte.diplomasi;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

import java.util.Optional;

/**
 * {@code kultivator:gerbang_sekte} (DESIGN 36.2.9, contract C36.D4): the mountain gate of each NPC sect is placed at the
 * chunk the seeded {@link GerbangPlan} gives it, and only for the sect's size {@code band} (0 kecil, 1 sedang, 2 besar;
 * one structure set per band). It is a vanilla random spread placement underneath, so {@code /locate} searches the grid
 * cells of {@code spacing} chunks as usual: {@link #getPotentialStructureChunk} answers the planned gate of a cell (or
 * the cell corner, which {@link #applyAdditionalChunkRestrictions} then refuses). Only new chunks get gates (world
 * generation); with {@code diplomasi.strukturAktif} off no chunk is a gate.
 */
public class GerbangSektePlacement extends RandomSpreadStructurePlacement {

	public static final MapCodec<GerbangSektePlacement> CODEC = RecordCodecBuilder.<GerbangSektePlacement>mapCodec(
			i -> placementCodec(i)
					.and(i.group(
							Codec.intRange(1, 4096).fieldOf("spacing").forGetter(RandomSpreadStructurePlacement::spacing),
							Codec.intRange(0, 4096).fieldOf("separation").forGetter(RandomSpreadStructurePlacement::separation),
							RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(RandomSpreadStructurePlacement::spreadType)))
					.and(Codec.intRange(0, 2).fieldOf("band").forGetter(GerbangSektePlacement::band))
					.apply(i, GerbangSektePlacement::new));

	private final int band;

	public GerbangSektePlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt,
			Optional<ExclusionZone> exclusionZone, int spacing, int separation, RandomSpreadType spreadType, int band) {
		super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, Math.max(spacing, separation + 1), separation, spreadType);
		this.band = band;
	}

	public int band() {
		return band;
	}

	@Override
	public ChunkPos getPotentialStructureChunk(long seed, int sourceX, int sourceZ) {
		GerbangPlan.Entry entry = GerbangPlan.enabled() ? GerbangPlan.of(seed).inCell(band, sourceX, sourceZ, spacing()) : null;
		if (entry != null) {
			return new ChunkPos(entry.chunkX(), entry.chunkZ());
		}
		return new ChunkPos(Math.floorDiv(sourceX, spacing()) * spacing(), Math.floorDiv(sourceZ, spacing()) * spacing());
	}

	@Override
	protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int sourceX, int sourceZ) {
		return GerbangPlan.enabled() && GerbangPlan.of(state.getLevelSeed()).isGate(band, sourceX, sourceZ);
	}

	@Override
	public boolean applyAdditionalChunkRestrictions(int sourceX, int sourceZ, long levelSeed) {
		return GerbangPlan.enabled() && GerbangPlan.of(levelSeed).isGate(band, sourceX, sourceZ)
				&& super.applyAdditionalChunkRestrictions(sourceX, sourceZ, levelSeed);
	}

	@Override
	public MapCodec<RandomSpreadStructurePlacement> codec() {
		@SuppressWarnings({"unchecked", "rawtypes"})
		MapCodec<RandomSpreadStructurePlacement> codec = (MapCodec) CODEC;
		return codec;
	}
}
