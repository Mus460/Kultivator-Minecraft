package dev.kultivator.mixin;

import dev.kultivator.sect.build.engine.BreakRefund;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Explosion credit for sect cells (DESIGN 21.13, {@code sectBuildExplosionCredit}): for a destroying explosion only
 * ({@code DESTROY} or {@code DESTROY_WITH_DECAY}; a wind charge or a mace's Wind Burst is {@code TRIGGER_BLOCK} and never
 * breaks blocks), sect cells among the exploded positions are taken out of the list (so they neither drop nor burn),
 * removed without item drops by the engine's {@code BuildPlacer}, and their placed cost is credited to the owner's
 * ledger; Perbaiki can rebuild them.
 */
@Mixin(ServerExplosion.class)
public abstract class SectExplosionMixin {

	@Shadow
	@Final
	private ServerLevel level;

	@Shadow
	@Final
	private Explosion.BlockInteraction blockInteraction;

	@Inject(method = "interactWithBlocks", at = @At("HEAD"))
	private void kultivator$sectExplosionCredit(List<BlockPos> targetBlocks, CallbackInfo ci) {
		if (this.blockInteraction == Explosion.BlockInteraction.DESTROY || this.blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY) {
			BreakRefund.explosion(this.level, targetBlocks);
		}
	}
}
