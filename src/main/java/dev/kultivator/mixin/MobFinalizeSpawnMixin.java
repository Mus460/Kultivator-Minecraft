package dev.kultivator.mixin;

import dev.kultivator.elite.EliteMonsters;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hands every finished {@code Mob.finalizeSpawn} to the elite module (DESIGN 19.2.12): spawner mobs get their tag,
 * natural spawns roll for an elite. Subclasses (skeletons, zombies, ...) still equip themselves after this TAIL returns
 * from their {@code super} call, so the module applies its hardening once the mob has fully spawned. No block is read or
 * changed here.
 */
@Mixin(Mob.class)
public abstract class MobFinalizeSpawnMixin {

	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void kultivator$eliteSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason,
			@Nullable SpawnGroupData groupData, CallbackInfoReturnable<SpawnGroupData> cir) {
		EliteMonsters.onFinalizeSpawn((Mob) (Object) this, level, spawnReason);
	}
}
