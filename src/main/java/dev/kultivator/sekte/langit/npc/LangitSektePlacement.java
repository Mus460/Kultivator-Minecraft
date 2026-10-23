package dev.kultivator.sekte.langit.npc;

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
 * {@code kultivator:sekte_langit} (DESIGN 37.7.6): the floating palace of NPC sky sect {@code index} (0-1) is placed at the
 * chunk of its sect's mountain gate ({@link LangitNpcPlan}, from module D's seeded {@code GerbangPlan}); the jigsaw's
 * absolute start height (204-228) lifts it above the clouds. A vanilla random spread underneath, so {@code /locate} searches
 * the grid cells of {@code spacing} chunks as usual ({@link #getPotentialStructureChunk} answers the planned chunk of a
 * cell, else the cell corner, which {@link #applyAdditionalChunkRestrictions} refuses). World generation only, in new
 * chunks; the structure sets live in the built-in pack {@code kultivator:sekte_langit} (registered only while Sekte Langit
 * and {@code strukturNpc} are on), the placement type is always registered (K1).
 */
public class LangitSektePlacement extends RandomSpreadStructurePlacement {

	public static final MapCodec<LangitSektePlacement> CODEC = RecordCodecBuilder.<LangitSektePlacement>mapCodec(
			i -> placementCodec(i)
					.and(i.group(
							Codec.intRange(1, 4096).fieldOf("spacing").forGetter(RandomSpreadStructurePlacement::spacing),
							Codec.intRange(0, 4096).fieldOf("separation").forGetter(RandomSpreadStructurePlacement::separation),
							RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(RandomSpreadStructurePlacement::spreadType)))
					.and(Codec.intRange(0, LangitNpcPlan.MAX - 1).fieldOf("index").forGetter(LangitSektePlacement::index))
					.apply(i, LangitSektePlacement::new));

	private final int index;

	public LangitSektePlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt,
			Optional<ExclusionZone> exclusionZone, int spacing, int separation, RandomSpreadType spreadType, int index) {
		super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, Math.max(spacing, separation + 1), separation, spreadType);
		this.index = index;
	}

	public int index() {
		return index;
	}

	private boolean planned(long seed, int cx, int cz) {
		LangitNpcPlan.Istana istana = LangitNpcPlan.get(seed, index);
		return istana != null && istana.chunkX() == cx && istana.chunkZ() == cz;
	}

	@Override
	public ChunkPos getPotentialStructureChunk(long seed, int sourceX, int sourceZ) {
		LangitNpcPlan.Istana istana = LangitNpcPlan.get(seed, index);
		int spacing = spacing();
		if (istana != null && Math.floorDiv(istana.chunkX(), spacing) == Math.floorDiv(sourceX, spacing)
				&& Math.floorDiv(istana.chunkZ(), spacing) == Math.floorDiv(sourceZ, spacing)) {
			return new ChunkPos(istana.chunkX(), istana.chunkZ());
		}
		return new ChunkPos(Math.floorDiv(sourceX, spacing) * spacing, Math.floorDiv(sourceZ, spacing) * spacing);
	}

	@Override
	protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int sourceX, int sourceZ) {
		return planned(state.getLevelSeed(), sourceX, sourceZ);
	}

	@Override
	public boolean applyAdditionalChunkRestrictions(int sourceX, int sourceZ, long levelSeed) {
		return planned(levelSeed, sourceX, sourceZ) && super.applyAdditionalChunkRestrictions(sourceX, sourceZ, levelSeed);
	}

	@Override
	public MapCodec<RandomSpreadStructurePlacement> codec() {
		@SuppressWarnings({"unchecked", "rawtypes"})
		MapCodec<RandomSpreadStructurePlacement> codec = (MapCodec) CODEC;
		return codec;
	}
}
