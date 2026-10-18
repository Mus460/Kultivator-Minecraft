package dev.kultivator.mixin;

import dev.kultivator.util.TargetGates;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Asks {@link TargetGates} before a server mob takes a new non-null target (DESIGN 24.2.10, 24.13 item 8) and cancels
 * the change when a gate refuses it. Clearing a target and keeping the same one always pass; with no gate registered
 * this costs one list check. Nothing here touches a block.
 */
@Mixin(Mob.class)
public abstract class MobSetTargetMixin {

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void kultivator$gateTarget(@Nullable LivingEntity target, CallbackInfo ci) {
		if (target == null || !TargetGates.any()) {
			return;
		}
		Mob self = (Mob) (Object) this;
		if (self.level().isClientSide() || target == self.getTarget()) {
			return;
		}
		if (!TargetGates.allow(self, target)) {
			ci.cancel();
		}
	}
}
