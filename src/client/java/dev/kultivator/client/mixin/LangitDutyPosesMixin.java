package dev.kultivator.client.mixin;

import dev.kultivator.client.disciple.power.DutyPoses;
import dev.kultivator.client.sekte.langit.LangitPoses;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sekte Langit (DESIGN 37.5.3, 37.5.7): the poses of the sky sect's disciple activities 25-27 (sword patrol, rim guard,
 * watching the clouds) are laid over the disciple model at every return of {@code DutyPoses.apply}, after the duty and
 * Sekte yang Hidup poses. {@code SektePoses.register} only accepts 13-24, so this hook avoids editing DESIGN 36 files.
 * Every other activity returns at once (one int compare).
 */
@Mixin(DutyPoses.class)
public abstract class LangitDutyPosesMixin {

	@Inject(method = "apply(Lnet/minecraft/client/model/player/PlayerModel;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
			at = @At("RETURN"))
	private static void kultivator$skyPoses(PlayerModel model, AvatarRenderState state, CallbackInfo ci) {
		LangitPoses.apply(model, state);
	}
}
