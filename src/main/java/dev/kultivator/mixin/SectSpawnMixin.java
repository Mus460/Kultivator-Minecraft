package dev.kultivator.mixin;

import dev.kultivator.sect.build.engine.BreakRefund;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spawn-proofing of sect cells (DESIGN 21.10, {@code sectBuildSpawnProof}): natural spawning of {@code MobCategory.MONSTER}
 * is refused when the block below is a sect cell (the image of Yue Lu Hall's covering formation, <i>A Record of a
 * Mortal's Journey</i> ch. 153). Reads the cell index only (a map lookup and a bit test).
 */
@Mixin(NaturalSpawner.class)
public abstract class SectSpawnMixin {

	@Inject(method = "isValidSpawnPostitionForType", at = @At("HEAD"), cancellable = true)
	private static void kultivator$sectSpawnProof(ServerLevel level, MobCategory mobCategory, StructureManager structureManager,
			ChunkGenerator generator, MobSpawnSettings.SpawnerData currentSpawnData, BlockPos.MutableBlockPos pos, double nearestPlayerDistanceSqr,
			CallbackInfoReturnable<Boolean> cir) {
		if (BreakRefund.spawnBlocked(level, mobCategory, pos.below())) {
			cir.setReturnValue(false);
		}
	}
}
