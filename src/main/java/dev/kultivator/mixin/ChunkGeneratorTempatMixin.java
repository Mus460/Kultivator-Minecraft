package dev.kultivator.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.kultivator.dunia.tempat.TempatKeepOut;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * Structure-free zones of DESIGN 38.D.4 (module 38W) and world-mod compatibility (docs/progres/kompat-mod-dunia.md) on
 * {@code ChunkGenerator.tryGenerateStructure} (private, the one place starts are created):
 * <ul>
 *   <li>before a start: a structure reaching the surface whose start chunk lies inside the keep-out box of a planned
 *       place that is really born (capital districts + 3 chunks, sect compounds + 2, themed cities + 3) is refused;
 *       place pieces ({@code kultivator:tata_letak}, {@code kultivator:jalan}), {@code #kultivator:boleh_di_tempat},
 *       strongholds and underground structures pass;</li>
 *   <li>after {@code Structure.generate}: {@link TempatKeepOut#saring} lifts floating starts clear of the terrain and
 *       refuses a start of another mod or vanilla whose pieces touch a planned box or a predicted Kultivator start.</li>
 * </ul>
 * {@code createStructures} hands its structure state to {@link TempatKeepOut} for the prediction. World generation of new
 * chunks only; never a runtime block change.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorTempatMixin {

	@Inject(method = "createStructures", at = @At("HEAD"))
	private void kultivator$mulaiStruktur(RegistryAccess registryAccess, ChunkGeneratorStructureState state, StructureManager structureManager,
			ChunkAccess centerChunk, StructureTemplateManager structureTemplateManager, ResourceKey<Level> level, CallbackInfo ci) {
		TempatKeepOut.mulai(state);
	}

	@Inject(method = "createStructures", at = @At("RETURN"))
	private void kultivator$selesaiStruktur(RegistryAccess registryAccess, ChunkGeneratorStructureState state, StructureManager structureManager,
			ChunkAccess centerChunk, StructureTemplateManager structureTemplateManager, ResourceKey<Level> level, CallbackInfo ci) {
		TempatKeepOut.selesai();
	}

	@Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
	private void kultivator$tempatKeepOut(StructureSet.StructureSelectionEntry selected, StructureManager structureManager,
			RegistryAccess registryAccess, RandomState randomState, StructureTemplateManager structureTemplateManager, long seed,
			ChunkAccess centerChunk, ChunkPos sourceChunkPos, ResourceKey<Level> level, Climate.Sampler climateSampler,
			CallbackInfoReturnable<Boolean> cir) {
		if (level == Level.OVERWORLD && TempatKeepOut.tolak(seed, selected.structure(), sourceChunkPos, (ChunkGenerator) (Object) this,
				randomState, centerChunk)) {
			cir.setReturnValue(false);
		}
	}

	@WrapOperation(method = "tryGenerateStructure", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/structure/Structure;generate(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/world/level/biome/Climate$Sampler;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;JLnet/minecraft/world/level/ChunkPos;ILnet/minecraft/world/level/LevelHeightAccessor;Ljava/util/function/Predicate;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;"))
	private StructureStart kultivator$saring(Structure structure, Holder<Structure> selected, ResourceKey<Level> dimension,
			RegistryAccess registryAccess, ChunkGenerator chunkGenerator, BiomeSource biomeSource, Climate.Sampler climateSampler, RandomState randomState,
			StructureTemplateManager structureTemplateManager, long seed, ChunkPos sourceChunkPos, int references, LevelHeightAccessor heightAccessor,
			Predicate<Holder<Biome>> validBiome, Operation<StructureStart> original) {
		StructureStart start = original.call(structure, selected, dimension, registryAccess, chunkGenerator, biomeSource, climateSampler, randomState,
				structureTemplateManager, seed, sourceChunkPos, references, heightAccessor, validBiome);
		return TempatKeepOut.saring(selected, start, new TempatKeepOut.Konteks(dimension, registryAccess, chunkGenerator, climateSampler,
				randomState, structureTemplateManager, seed, heightAccessor));
	}
}
