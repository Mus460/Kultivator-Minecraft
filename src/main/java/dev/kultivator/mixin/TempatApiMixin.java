package dev.kultivator.mixin;

import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Damage attribution for cared-for places (DESIGN 38.R.2): a fire about to eat a block reports it to place repair, so a
 * roof that burned away is repaired while a block that changed for any other reason is not.
 */
@Mixin(FireBlock.class)
public abstract class TempatApiMixin {

	@Inject(method = "checkBurnOut", at = @At("HEAD"))
	private void kultivator$apiTempat(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
		if (level instanceof ServerLevel server && !level.getBlockState(pos).isAir()) {
			PerbaikanApi.terbakar(server, pos);
		}
	}
}
