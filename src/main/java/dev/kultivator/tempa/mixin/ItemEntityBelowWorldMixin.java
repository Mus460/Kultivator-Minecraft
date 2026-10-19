package dev.kultivator.tempa.mixin;

import dev.kultivator.tempa.RingProtection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The ring void rescue (DESIGN 25.7.3, owned by P8-tempa): a bound storage ring that falls out of the world goes back to its
 * owner, or into the rescue store while they are offline. It targets {@link Entity} because {@code ItemEntity} inherits
 * {@code onBelowWorld()} without overriding it in 26.3; the injection checks {@code (Object) this instanceof ItemEntity}.
 * Registered in {@code kultivator.tempa.mixins.json}.
 */
@Mixin(Entity.class)
public abstract class ItemEntityBelowWorldMixin {

	@Inject(method = "onBelowWorld", at = @At("HEAD"))
	private void kultivator$rescueRing(CallbackInfo ci) {
		if ((Object) this instanceof ItemEntity item && !item.level().isClientSide()) {
			RingProtection.rescue(item);
		}
	}
}
