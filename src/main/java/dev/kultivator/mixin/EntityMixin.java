package dev.kultivator.mixin;

import dev.kultivator.util.NoGrief;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entities the mod controls (Dao Protectors, thrown monsters, spirit beasts...) never press pressure plates or trip
 * wires (that could open doors or fire traps) and never travel through a portal (that could build an exit portal, the
 * End platform or an End gateway exit). {@code LivingEntity.canUsePortal} calls this one through {@code super}, and
 * {@code Mob} overrides neither, so one inject here reaches every mob (block safety).
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

	@Inject(method = "isIgnoringBlockTriggers", at = @At("HEAD"), cancellable = true)
	private void kultivator$noTriggers(CallbackInfoReturnable<Boolean> cir) {
		if (NoGrief.isProtected((Entity) (Object) this)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "canUsePortal", at = @At("HEAD"), cancellable = true)
	private void kultivator$noPortal(boolean ignorePassenger, CallbackInfoReturnable<Boolean> cir) {
		if (NoGrief.isProtected((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
