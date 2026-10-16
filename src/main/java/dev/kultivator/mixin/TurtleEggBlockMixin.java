package dev.kultivator.mixin;

import dev.kultivator.util.NoGrief;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.TurtleEggBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Monsters the mod spawned never crush turtle eggs (block safety). */
@Mixin(TurtleEggBlock.class)
public abstract class TurtleEggBlockMixin {

	@Inject(method = "canDestroyEgg", at = @At("HEAD"), cancellable = true)
	private void kultivator$noCrush(ServerLevel level, BlockPos pos, Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (NoGrief.isProtected(entity)) {
			cir.setReturnValue(false);
		}
	}
}
