package dev.kultivator.mixin;

import dev.kultivator.util.NoGrief;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A big dripleaf tilts (a block state change) under whatever stands on it; never under entities the mod controls. */
@Mixin(BigDripleafBlock.class)
public abstract class BigDripleafBlockMixin {

	@Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
	private void kultivator$noTilt(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier,
			boolean isPrecise, CallbackInfo ci) {
		if (NoGrief.isProtected(entity)) {
			ci.cancel();
		}
	}
}
