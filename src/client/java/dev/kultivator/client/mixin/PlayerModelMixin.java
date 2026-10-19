package dev.kultivator.client.mixin;

import dev.kultivator.client.anim.PoseApplier;
import dev.kultivator.client.render.MeditationPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Poses the limbs after the vanilla animation: first the meditation seat, then the cast pose of DESIGN 27.2 (sleeves,
 * trousers, jacket and hat are child parts, so they follow; armour, robe, cape and ears models are PlayerModels too and
 * pose the same way). First-person hands reset their arm and are moved by FirstPersonHandsMixin instead.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void kultivator$meditationPose(AvatarRenderState state, CallbackInfo ci) {
		MeditationPose.apply((PlayerModel) (Object) this, state);
		PoseApplier.apply((PlayerModel) (Object) this, state);
	}
}
