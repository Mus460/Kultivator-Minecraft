package dev.kultivator.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.kultivator.dunia.tempat.TempatKeepOut;
import dev.kultivator.dunia.tempat.worldgen.TempatTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Roads yield to other mods' structures (docs/progres/kompat-mod-dunia.md): while a {@code kultivator:jalan} start is
 * placed in a chunk, a road piece whose part in the chunk touches a piece of a structure of another mod or vanilla is
 * skipped ({@link TempatKeepOut#jalanTerhalang}), so no road cuts through a tavern, a ruin or a village. World generation
 * of new chunks only; never a runtime block change.
 */
@Mixin(StructureStart.class)
public abstract class StructureStartJalanMixin {

	@Shadow
	@Final
	private Structure structure;

	@WrapOperation(method = "placeInChunk", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/structure/StructurePiece;postProcess(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/core/BlockPos;)V"))
	private void kultivator$jalanMengalah(StructurePiece piece, WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos, Operation<Void> original) {
		if (structure.type() == TempatTypes.JALAN && TempatKeepOut.jalanTerhalang(level, structureManager, chunkPos, chunkBB, piece.getBoundingBox())) {
			return;
		}
		original.call(piece, level, structureManager, generator, random, chunkBB, chunkPos, referencePos);
	}
}
