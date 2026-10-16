package dev.kultivator.mixin;

import dev.kultivator.util.NoGrief;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Monsters the mod spawned never trample farmland (block safety). */
@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {

	@Inject(method = "turnToBaseBlock", at = @At("HEAD"), cancellable = true)
	private void kultivator$noTrample(@Nullable Entity sourceEntity, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
		if (NoGrief.isProtected(sourceEntity)) {
			ci.cancel();
		}
	}
}
