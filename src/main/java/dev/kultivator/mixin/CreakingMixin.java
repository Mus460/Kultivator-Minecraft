package dev.kultivator.mixin;

import dev.kultivator.util.NoGrief;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.creaking.Creaking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A creaking bound to a heart makes the heart grow resin blocks when a player, or a player's tamed wolf, hurts it, and it
 * does so before any damage event runs. The entities the mod controls (spirit beasts, Dao Protectors, heart demons)
 * therefore never hurt one (block safety).
 */
@Mixin(Creaking.class)
public abstract class CreakingMixin {

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void kultivator$noResin(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		if (((Creaking) (Object) this).isHeartBound() && NoGrief.isProtected(source.getEntity())) {
			cir.setReturnValue(false);
		}
	}
}
