package dev.kultivator.dunia.tempat.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.dunia.tempat.Distrik;
import dev.kultivator.dunia.tempat.Rencana;
import dev.kultivator.dunia.tempat.RencanaRegistry;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

import java.util.Optional;

/**
 * {@code kultivator:rencana_tempat} (DESIGN 38.B.2.3, 38.D.1): a structure starts only in the chunk a deterministic
 * place plan gives district {@code distrik} of plan {@code rencana} ({@link RencanaRegistry}: {@code kota_besar},
 * {@code kompleks_sekte}, {@code kota_tema_<tema>}, {@code jalan_titik}). Underneath it is a vanilla random spread
 * placement (pattern {@code GerbangSektePlacement}), so {@code /locate} walks the {@code spacing} grid as usual:
 * {@link #getPotentialStructureChunk} answers the planned start inside a cell, or the cell corner, which
 * {@link #applyAdditionalChunkRestrictions} then refuses. The JSON fields {@code spacing} (default 256) and
 * {@code separation} (default 0) only shape that locate grid. Registered always (38.0 rule 17); an unregistered plan
 * id means no start anywhere.
 */
public class RencanaPlacement extends RandomSpreadStructurePlacement {

	public static final MapCodec<RencanaPlacement> CODEC = RecordCodecBuilder.<RencanaPlacement>mapCodec(
			i -> placementCodec(i)
					.and(i.group(
							Codec.intRange(1, 4096).optionalFieldOf("spacing", 256).forGetter(RandomSpreadStructurePlacement::spacing),
							Codec.intRange(0, 4096).optionalFieldOf("separation", 0).forGetter(RandomSpreadStructurePlacement::separation),
							RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(RandomSpreadStructurePlacement::spreadType)))
					.and(Codec.STRING.fieldOf("rencana").forGetter(RencanaPlacement::rencana))
					.and(Codec.STRING.optionalFieldOf("distrik", "").forGetter(RencanaPlacement::distrik))
					.apply(i, RencanaPlacement::new));

	private final String rencana;
	private final String distrik;

	public RencanaPlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt,
			Optional<ExclusionZone> exclusionZone, int spacing, int separation, RandomSpreadType spreadType, String rencana, String distrik) {
		super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, Math.max(spacing, separation + 1), separation, spreadType);
		this.rencana = rencana;
		this.distrik = distrik;
	}

	public String rencana() {
		return rencana;
	}

	public String distrik() {
		return distrik;
	}

	private Rencana plan() {
		return RencanaRegistry.get(rencana);
	}

	@Override
	public ChunkPos getPotentialStructureChunk(long seed, int sourceX, int sourceZ) {
		int cellX = Math.floorDiv(sourceX, spacing());
		int cellZ = Math.floorDiv(sourceZ, spacing());
		for (Distrik d : plan().semuaDistrik(seed)) {
			if (d.id().equals(distrik) && Math.floorDiv(d.chunkX(), spacing()) == cellX && Math.floorDiv(d.chunkZ(), spacing()) == cellZ) {
				return new ChunkPos(d.chunkX(), d.chunkZ());
			}
		}
		return new ChunkPos(cellX * spacing(), cellZ * spacing());
	}

	@Override
	protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int sourceX, int sourceZ) {
		return plan().isStart(state.getLevelSeed(), sourceX, sourceZ, distrik);
	}

	@Override
	public boolean applyAdditionalChunkRestrictions(int sourceX, int sourceZ, long levelSeed) {
		return plan().isStart(levelSeed, sourceX, sourceZ, distrik) && super.applyAdditionalChunkRestrictions(sourceX, sourceZ, levelSeed);
	}

	@Override
	public MapCodec<RandomSpreadStructurePlacement> codec() {
		@SuppressWarnings({"unchecked", "rawtypes"})
		MapCodec<RandomSpreadStructurePlacement> codec = (MapCodec) CODEC;
		return codec;
	}
}
