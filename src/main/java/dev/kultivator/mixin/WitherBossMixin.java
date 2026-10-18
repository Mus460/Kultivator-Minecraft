package dev.kultivator.mixin;

import dev.kultivator.util.DamageHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Technique damage never arms the Wither's block breaking (DESIGN 22.14.2). Vanilla {@code WitherBoss.hurtServer} sets
 * {@code destroyBlocksTick = 20} on every accepted hit (a second later the Wither breaks the blocks around it when
 * {@code mobGriefing} is on) and adds 3 to both {@code idleHeadUpdates} counters, which on Normal and Hard fire a random
 * dangerous blue skull (an explosion that breaks blocks) once a counter passes 15. For {@code kultivator:technique} and
 * {@code kultivator:technique_pierce} both values are put back when {@code hurtServer} returns, so the player's techniques
 * may target the Wither as often as they hit without any block changing (a break armed by something else stays armed).
 * Pure HEAD/RETURN injections.
 */
@Mixin(WitherBoss.class)
public abstract class WitherBossMixin {

	@Shadow
	private int destroyBlocksTick;

	@Shadow
	@Final
	private int[] idleHeadUpdates;

	@Unique
	private int kultivator$savedDestroyTick = Integer.MIN_VALUE;

	@Unique
	private int[] kultivator$savedIdleHeads;

	@Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"))
	private void kultivator$rememberDestroyTick(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		boolean technique = DamageHooks.isTechnique(source);
		kultivator$savedDestroyTick = technique ? this.destroyBlocksTick : Integer.MIN_VALUE;
		kultivator$savedIdleHeads = technique ? this.idleHeadUpdates.clone() : null;
	}

	@Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("RETURN"))
	private void kultivator$restoreDestroyTick(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		if (kultivator$savedDestroyTick != Integer.MIN_VALUE) {
			this.destroyBlocksTick = kultivator$savedDestroyTick;
			kultivator$savedDestroyTick = Integer.MIN_VALUE;
		}
		int[] saved = kultivator$savedIdleHeads;
		if (saved != null) {
			System.arraycopy(saved, 0, this.idleHeadUpdates, 0, Math.min(saved.length, this.idleHeadUpdates.length));
			kultivator$savedIdleHeads = null;
		}
	}
}
