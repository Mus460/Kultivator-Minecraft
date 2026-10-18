package dev.kultivator.mixin;

import dev.kultivator.util.DamageHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The damage-modification hook of DESIGN 22.14.3 (Fabric has no such event). In 26.3 {@code actuallyHurt} is declared by
 * {@code LivingEntity} and overridden by {@code Player} with the same structure:
 * <pre>
 * dmg = getDamageAfterArmorAbsorb(source, dmg);   // STORE 0 of the dmg argument
 * dmg = getDamageAfterMagicAbsorb(source, dmg);   // STORE 1  ← hooked right after this store
 * float originalDamage = dmg;                     // then absorption is applied
 * </pre>
 * so the amount after armour and enchantment protection and before absorption goes through
 * {@link DamageHooks#modify} (unblockable damage passes untouched; then technique DR, Perisai Qi, COUNTER).
 */
@Mixin({LivingEntity.class, Player.class})
public abstract class LivingEntityDamageMixin {

	@ModifyVariable(method = "actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V",
			at = @At(value = "STORE", ordinal = 1), argsOnly = true)
	private float kultivator$techniqueDamageHooks(float dmg, ServerLevel level, DamageSource source) {
		return DamageHooks.modify((LivingEntity) (Object) this, source, dmg);
	}
}
