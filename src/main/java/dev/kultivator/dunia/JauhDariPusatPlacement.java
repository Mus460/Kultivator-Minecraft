package dev.kultivator.dunia;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.Kultivator;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

import java.util.Optional;

/**
 * {@code kultivator:jauh_dari_pusat} (DESIGN 34.0 rule W4): the vanilla {@code random_spread} placement plus
 * {@code min_chunks} (default 6), so no structure of the set starts within that many chunks of the world's centre, where
 * the world spawn is. Everything else (spacing, separation, salt, frequency, exclusion zone, locate offset, spread type)
 * is the vanilla codec, and vanilla locate keeps working (it is still a random spread placement). Any module may use it
 * in its {@code structure_set} JSON: {@code "type": "kultivator:jauh_dari_pusat"}.
 */
public class JauhDariPusatPlacement extends RandomSpreadStructurePlacement {

	public static final MapCodec<JauhDariPusatPlacement> CODEC = RecordCodecBuilder.<JauhDariPusatPlacement>mapCodec(
			i -> placementCodec(i)
					.and(i.group(
							Codec.intRange(0, 4096).fieldOf("spacing").forGetter(RandomSpreadStructurePlacement::spacing),
							Codec.intRange(0, 4096).fieldOf("separation").forGetter(RandomSpreadStructurePlacement::separation),
							RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(RandomSpreadStructurePlacement::spreadType)))
					.and(Codec.intRange(0, 256).optionalFieldOf("min_chunks", 6).forGetter(JauhDariPusatPlacement::minChunks))
					.apply(i, JauhDariPusatPlacement::new));

	private final int minChunks;

	public JauhDariPusatPlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt,
			Optional<ExclusionZone> exclusionZone, int spacing, int separation, RandomSpreadType spreadType, int minChunks) {
		super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, Math.max(spacing, separation + 1), separation, spreadType);
		this.minChunks = minChunks;
	}

	public int minChunks() {
		return minChunks;
	}

	@Override
	protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int sourceX, int sourceZ) {
		if ((long) sourceX * sourceX + (long) sourceZ * sourceZ < (long) minChunks * minChunks) {
			return false;
		}
		return super.isPlacementChunk(state, sourceX, sourceZ);
	}

	@Override
	public MapCodec<RandomSpreadStructurePlacement> codec() {
		@SuppressWarnings({"unchecked", "rawtypes"})
		MapCodec<RandomSpreadStructurePlacement> codec = (MapCodec) CODEC;
		return codec;
	}

	/** From {@code DuniaSpine.init()}. */
	static void register() {
		Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, Kultivator.id("jauh_dari_pusat"), CODEC);
	}
}
