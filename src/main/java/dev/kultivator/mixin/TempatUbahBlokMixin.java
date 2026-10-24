package dev.kultivator.mixin;

import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Damage attribution for cared-for places (DESIGN 38.R.2): a block destroyed, placed or changed by a mob or a
 * projectile (an enderman taking a block, a zombie breaking a door, a wither, an arrow breaking a pot) is reported to
 * place repair, which repairs only reported damage. Players are reported by the break and use events of 38PR.
 */
@Mixin(ServerLevel.class)
public abstract class TempatUbahBlokMixin {

	@Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
			at = @At("HEAD"))
	private void kultivator$ubahBlokTempat(Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context, CallbackInfo ci) {
		if (gameEvent != GameEvent.BLOCK_DESTROY && gameEvent != GameEvent.BLOCK_PLACE && gameEvent != GameEvent.BLOCK_CHANGE) {
			return;
		}
		Entity source = context.sourceEntity();
		if (source instanceof Mob || source instanceof Projectile) {
			PerbaikanApi.ubahBlok((ServerLevel) (Object) this, BlockPos.containing(position), source);
		}
	}
}
