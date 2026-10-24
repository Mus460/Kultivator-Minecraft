package dev.kultivator.mixin;

import dev.kultivator.dunia.tempat.TempatKeepOut;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Structure-free zones of DESIGN 38.D.4 (module 38W): before vanilla makes any structure start
 * ({@code ChunkGenerator.tryGenerateStructure}, private, the one place starts are created), a surface structure whose
 * start chunk lies inside the keep-out box of a planned place that is really born (capital districts + 3 chunks, sect
 * compounds + 2, themed cities + 3) is refused, so no village, outpost or old town appears inside a capital. Place
 * pieces themselves ({@code kultivator:tata_letak}, {@code kultivator:jalan}), structures tagged
 * {@code #kultivator:boleh_di_tempat}, strongholds and every underground structure pass. World generation of new chunks
 * only; never a runtime block change.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorTempatMixin {

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
}
