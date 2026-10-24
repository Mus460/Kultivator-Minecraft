package dev.kultivator.dunia.tempat.jalan;

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
 * {@code kultivator:jalan} (DESIGN 38.C.6.2, 38.C.6.3, 38.D.1). Without {@code titik}: one road start in every chunk a
 * road edge of {@link RencanaJalan} crosses (set {@code jalan/jalan}). With {@code titik} (a post kind of
 * {@link RencanaJalan#JENIS_POS}: {@code tonggak}, {@code kuil_jalan}, {@code singgah}, {@code singgah_besar},
 * {@code dagang}, {@code kemah_begal}): one start in each chunk holding the footprint centre of such a road-side post
 * (sets {@code pos/<jenis>}). Underneath a random spread with spacing 1 (every chunk is its own cell), so the vanilla
 * machinery and {@code /locate} still work; the JSON {@code spacing}/{@code separation} fields are accepted and forced
 * to 1/0. The placement hands its structure state to the road plan ({@link RencanaJalan#pakai}), which needs the other
 * sets' placements to find towns and villages.
 */
public class JalanPlacement extends RandomSpreadStructurePlacement {

	public static final MapCodec<JalanPlacement> CODEC = RecordCodecBuilder.<JalanPlacement>mapCodec(
			i -> placementCodec(i)
					.and(i.group(
							Codec.intRange(1, 4096).optionalFieldOf("spacing", 1).forGetter(RandomSpreadStructurePlacement::spacing),
							Codec.intRange(0, 4096).optionalFieldOf("separation", 0).forGetter(RandomSpreadStructurePlacement::separation),
							Codec.STRING.optionalFieldOf("titik", "").forGetter(JalanPlacement::titik)))
					.apply(i, JalanPlacement::new));

	private final String titik;

	public JalanPlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt,
			Optional<ExclusionZone> exclusionZone, int spacing, int separation, String titik) {
		super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, 1, 0, RandomSpreadType.LINEAR);
		this.titik = titik;
	}

	/** The post kind this placement starts ("" = the road itself). */
	public String titik() {
		return titik;
	}

	@Override
	public ChunkPos getPotentialStructureChunk(long seed, int sourceX, int sourceZ) {
		return new ChunkPos(sourceX, sourceZ);
	}

	@Override
	protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int sourceX, int sourceZ) {
		RencanaJalan.pakai(state);
		if (titik.isEmpty()) {
			return RencanaJalan.lewat(state, sourceX, sourceZ);
		}
		return !RencanaJalan.posDi(state, sourceX, sourceZ, titik).isEmpty();
	}

	@Override
	public MapCodec<RandomSpreadStructurePlacement> codec() {
		@SuppressWarnings({"unchecked", "rawtypes"})
		MapCodec<RandomSpreadStructurePlacement> codec = (MapCodec) CODEC;
		return codec;
	}
}
