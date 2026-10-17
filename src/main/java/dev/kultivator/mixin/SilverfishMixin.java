package dev.kultivator.mixin;

import dev.kultivator.util.NoGrief;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Silverfish;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A silverfish hurt by an entity calls its friends out of infested blocks, which breaks or replaces those blocks, and it
 * does so before any damage event runs. The entities the mod controls (spirit beasts, Dao Protectors, heart demons,
 * thrown monsters) therefore never hurt one (block safety).
 */
@Mixin(Silverfish.class)
public abstract class SilverfishMixin {

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void kultivator$noWakeUp(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		if (NoGrief.isProtected(source.getEntity())) {
			cir.setReturnValue(false);
		}
	}
}
