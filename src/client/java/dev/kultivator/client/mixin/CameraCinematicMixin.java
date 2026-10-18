package dev.kultivator.client.mixin;

import dev.kultivator.client.epic.camera.CinematicDirector;
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
 * 7b camera shots (DESIGN 23.7.1). In 26.3 {@code Camera.update(DeltaTracker)} calls {@code alignWithEntity}, then
 * {@code calculateFov}, then builds the cull frustum from {@code position}, so a pose set right after
 * {@code alignWithEntity} is what the frame (and Sodium's culling) uses; {@code detached} is reset there every frame, so
 * nothing lingers after a shot. Only calls {@link CinematicDirector}, which costs one boolean while no shot runs.
 */
@Mixin(Camera.class)
public abstract class CameraCinematicMixin {

	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
	private void kultivator$direct(DeltaTracker deltaTracker, CallbackInfo ci) {
		CinematicDirector.Pose pose = CinematicDirector.pose((Camera) (Object) this, deltaTracker);
		if (pose != null) {
			setRotation(pose.yaw(), pose.pitch());
			setPosition(pose.pos());
			detached = true;
		}
	}

	@Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true)
	private void kultivator$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
		float scale = CinematicDirector.fovScale();
		if (scale != 1.0F) {
			cir.setReturnValue(cir.getReturnValueF() * scale);
		}
	}
}
