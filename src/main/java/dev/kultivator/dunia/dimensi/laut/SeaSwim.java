package dev.kultivator.dunia.dimensi.laut;

import dev.kultivator.dunia.dimensi.DimensiLevels;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The light swimming of module D's sea monsters (no fish move control, no water path finding): in water the body
 * steers its own velocity toward its target or a slowly turning wander heading, stays a few blocks under the surface
 * and above the sea floor, and faces where it swims. Server side, once per tick; cheap enough for a weak laptop.
 */
public final class SeaSwim {

	/** Steers {@code mob} in water; returns true when it swam this tick (false out of water, gravity then applies). */
	public static boolean steer(Mob mob, @Nullable LivingEntity target, double speed, double[] heading) {
		if (!mob.isInWater()) {
			mob.setNoGravity(false);
			return false;
		}
		mob.setNoGravity(true);
		Vec3 goal;
		if (target != null && target.isAlive()) {
			goal = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
		} else {
			if (mob.tickCount % 80 == 0 || heading[0] == 0.0D && heading[1] == 0.0D) {
				double angle = mob.getRandom().nextDouble() * Math.PI * 2.0D;
				heading[0] = Math.cos(angle);
				heading[1] = Math.sin(angle);
				heading[2] = (mob.getRandom().nextDouble() - 0.5D) * 0.6D;
			}
			goal = mob.position().add(heading[0] * 8.0D, heading[2] * 4.0D, heading[1] * 8.0D);
			speed *= 0.45D;
		}
		// keep inside the water column: 2 below the surface, 2 above the sand
		double top = DimensiLevels.isLaut(mob.level()) ? DimensiLevels.LAUT_SEA_TOP - 2.0D : mob.level().getSeaLevel() - 3.0D;
		double bottom = DimensiLevels.isLaut(mob.level()) ? 42.0D : mob.level().getMinY() + 2.0D;
		goal = new Vec3(goal.x, Mth.clamp(goal.y, bottom, top), goal.z);
		Vec3 to = goal.subtract(mob.position());
		double length = to.length();
		Vec3 want = length < 1.0E-3D ? Vec3.ZERO : to.scale(speed / length);
		Vec3 now = mob.getDeltaMovement();
		Vec3 next = now.lerp(want, 0.12D);
		mob.setDeltaMovement(next);
		double horizontal = next.horizontalDistance();
		if (horizontal > 0.01D) {
			float yaw = (float) (Mth.atan2(next.z, next.x) * Mth.RAD_TO_DEG) - 90.0F;
			mob.setYRot(Mth.rotLerp(0.2F, mob.getYRot(), yaw));
			mob.yBodyRot = mob.getYRot();
			mob.yHeadRot = mob.getYRot();
			mob.setXRot((float) (-(Mth.atan2(next.y, horizontal) * Mth.RAD_TO_DEG)) * 0.6F);
		}
		return true;
	}

	private SeaSwim() {
	}
}
