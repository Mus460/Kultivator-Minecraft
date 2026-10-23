package dev.kultivator.mixin;

import dev.kultivator.sekte.langit.main.LangitCuaca;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sekte Langit weather immunity (DESIGN 37.7.2, module 37G): the vanilla per-chunk precipitation step
 * ({@code ServerLevel.tickPrecipitation(BlockPos)}: ice on water, snow layers, cauldrons filling) is skipped for a column
 * whose top lies on a sky island ("above the clouds the sky is always clear"). This only <b>prevents</b> a vanilla block
 * change; it never changes a block itself, the weather, the time or the tick rate. Off with {@code sekteLangit.kebalSalju}.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelPrecipitationMixin {

	@Inject(method = "tickPrecipitation", at = @At("HEAD"), cancellable = true)
	private void kultivator$skyIslandPrecipitation(BlockPos pos, CallbackInfo ci) {
		if (LangitCuaca.blockPrecipitation((ServerLevel) (Object) this, pos)) {
			ci.cancel();
		}
	}
}
