package dev.kultivator.client.mixin.duel;

import dev.kultivator.client.duel.fx.DuelCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The duel camera (DESIGN 35.4.10; module V): the shoulder and onlooker poses of {@link DuelCamera}, set at the same
 * point as the 7b {@code CameraCinematicMixin} (right after {@code alignWithEntity}, before the FOV and the cull frustum
 * are computed, so the frame and Sodium's culling use it), with priority 1100. {@link DuelCamera#pose} returns null
 * whenever a 7b shot runs, so the film always wins. {@code detached} is reset by vanilla every frame, so nothing lingers.
 * Costs one field check while no duel camera runs.
 */
@Mixin(value = Camera.class, priority = 1100)
public abstract class DuelCameraMixin {

	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
	private void kultivator$duelCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
		DuelCamera.Pose pose = DuelCamera.pose((Camera) (Object) this, deltaTracker);
		if (pose != null) {
			setRotation(pose.yaw(), pose.pitch());
			setPosition(pose.pos());
			detached = true;
		}
	}

	@Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true)
	private void kultivator$duelFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
		float scale = DuelCamera.fovScale();
		if (scale != 1.0F) {
			cir.setReturnValue(cir.getReturnValueF() * scale);
		}
	}
}
