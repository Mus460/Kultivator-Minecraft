package dev.kultivator.mixin;

import dev.kultivator.util.NoGrief;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stepping on redstone ore lights it (a block state change) unless the step is careful. Entities the mod controls
 * never do that (block safety).
 */
@Mixin(RedStoneOreBlock.class)
public abstract class RedStoneOreBlockMixin {

	@Inject(method = "stepOn", at = @At("HEAD"), cancellable = true)
	private void kultivator$quietStep(Level level, BlockPos pos, BlockState onState, Entity entity, CallbackInfo ci) {
		if (NoGrief.isProtected(entity)) {
			ci.cancel();
		}
	}
}
