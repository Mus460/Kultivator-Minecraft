package dev.kultivator.duel.clash;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.blade.BladeArcs;
import dev.kultivator.duel.blade.BladeSegment;
import dev.kultivator.duel.blade.DuelPose;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The server's geometry of a cut (DESIGN 35.5.3): the shared blade arcs of module A ({@code BladeArcs.sample}, pure
 * {@code org.joml}, the same forward kinematics the client draws) sampled around the impact and tested against the
 * victim's box grown by 0.1 (segment-box slab test), plus the contact anchor where two blades meet. A cone test (reach
 * and facing) backs the arcs up so a cut that plainly lands is never lost to a pose that has not been tuned yet.
 * Cheap: at most 5 samples per swing and side, about 2 µs each; at most 64 live swings.
 */
public final class HitTest {

	/** The arc samples around the strike (fractions of the whole cut, windup key 0, end of follow-through 1). */
	private static final float[] SAMPLES = {0.35F, 0.45F, 0.55F, 0.65F, 0.75F};
	/** The victim's box is grown by this much for the arc test. */
	public static final double INFLATE = 0.1D;
	/** Half-angles of the backing cone: cuts sweep wide, thrusts are narrow. */
	private static final double CUT_CONE = 55.0D;
	private static final double THRUST_CONE = 22.0D;
	private static final double SPIN_CONE = 180.0D;

	/** True when {@code swing} reaches {@code victim} at its impact. */
	public static boolean sweep(Swing swing, LivingEntity victim) {
		LivingEntity attacker = swing.owner.entity;
		if (attacker.level() != victim.level()) {
			return false;
		}
		double reach = reach(swing);
		AABB box = victim.getBoundingBox();
		Vec3 center = box.getCenter();
		if (attacker.position().distanceToSqr(center) > (reach + 2.5D) * (reach + 2.5D)) {
			return false;
		}
		if (cone(attacker, victim, reach, swing.dir == SwingDir.TUSUK ? THRUST_CONE : swing.dir == SwingDir.PUTAR ? SPIN_CONE : CUT_CONE)) {
			return true;
		}
		DuelPose pose = DuelPose.forSwing(swing.kind, swing.dir);
		for (float t : SAMPLES) {
			BladeSegment blade = blade(attacker, pose, swing.weapon, t);
			if (blade != null && blade.hits(box, INFLATE)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Where two fighters' blades meet for a clash, deflect or block: the closest approach of the attacker's sampled arc to
	 * the defender's blade in {@code defenderPose} (its guard or deflect). Without blades (a bare hand, a failed sample)
	 * a point between the two chests, 60% of the way toward the defender.
	 */
	public static Vec3 contact(LivingEntity attacker, @Nullable Swing swing, LivingEntity defender, DuelPose defenderPose,
			@Nullable Swing defenderSwing) {
		WeaponClass attackWeapon = swing != null ? swing.weapon : WeaponClass.of(attacker);
		WeaponClass defendWeapon = WeaponClass.of(defender);
		DuelPose attackPose = swing != null ? DuelPose.forSwing(swing.kind, swing.dir) : DuelPose.KUDA_TENGAH;
		DuelPose guardPose = defenderSwing != null ? DuelPose.forSwing(defenderSwing.kind, defenderSwing.dir) : defenderPose;
		BladeSegment best = null;
		BladeSegment bestOther = null;
		double bestDistance = Double.MAX_VALUE;
		for (float t : SAMPLES) {
			BladeSegment a = blade(attacker, attackPose, attackWeapon, t);
			BladeSegment b = blade(defender, guardPose, defendWeapon, defenderSwing != null ? t : 0.5F);
			if (a == null || b == null) {
				continue;
			}
			double distance = BladeSegment.closest(a, b)[2];
			if (distance < bestDistance) {
				bestDistance = distance;
				best = a;
				bestOther = b;
			}
		}
		if (best != null && bestDistance < 1.2D) {
			return BladeSegment.contact(best, bestOther);
		}
		return fallback(attacker, defender);
	}

	/** Where a cut lands on a body (HIT): the point of the arc nearest the victim's centre, else its chest. */
	public static Vec3 hitPoint(Swing swing, LivingEntity victim) {
		LivingEntity attacker = swing.owner.entity;
		Vec3 center = victim.getBoundingBox().getCenter();
		DuelPose pose = DuelPose.forSwing(swing.kind, swing.dir);
		Vec3 best = null;
		double bestDistance = Double.MAX_VALUE;
		for (float t : SAMPLES) {
			BladeSegment blade = blade(attacker, pose, swing.weapon, t);
			if (blade == null) {
				continue;
			}
			Vec3 near = blade.nearest(center);
			double distance = near.distanceToSqr(center);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = near;
			}
		}
		if (best != null && bestDistance < 1.0D) {
			return best;
		}
		return center.add(attacker.position().subtract(victim.position()).normalize().scale(victim.getBbWidth() * 0.5D));
	}

	/** Between two chests, 60% toward the defender. */
	public static Vec3 fallback(LivingEntity attacker, LivingEntity defender) {
		Vec3 a = attacker.position().add(0.0D, attacker.getBbHeight() * 0.62D, 0.0D);
		Vec3 b = defender.position().add(0.0D, defender.getBbHeight() * 0.62D, 0.0D);
		return a.lerp(b, 0.6D);
	}

	/** One sample of {@code entity}'s blade in {@code pose} at {@code t}; null when the arcs cannot tell. */
	private static @Nullable BladeSegment blade(LivingEntity entity, DuelPose pose, WeaponClass weapon, float t) {
		try {
			return BladeArcs.sample(pose, weapon, t, entity.position(), entity.yBodyRot, entity.getScale(), slim(entity),
					entity.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private static boolean slim(LivingEntity entity) {
		if (entity instanceof DiscipleEntity disciple) {
			return disciple.slimSkin();
		}
		if (entity instanceof dev.kultivator.musuh.RogueCultivatorEntity rogue) {
			return rogue.slim();
		}
		return false;
	}

	/** Reach and facing: the victim's box is within {@code reach} of the eyes and within {@code halfAngle} of the look. */
	public static boolean cone(LivingEntity attacker, LivingEntity victim, double reach, double halfAngle) {
		AABB box = victim.getBoundingBox();
		Vec3 eye = attacker.getEyePosition();
		Vec3 nearest = new Vec3(clamp(eye.x, box.minX, box.maxX), clamp(eye.y, box.minY, box.maxY), clamp(eye.z, box.minZ, box.maxZ));
		if (nearest.distanceToSqr(eye) > reach * reach) {
			return false;
		}
		if (halfAngle >= 180.0D) {
			return true;
		}
		return angleTo(attacker, box.getCenter()) <= halfAngle;
	}

	/** The horizontal angle (degrees) between {@code entity}'s look and the direction to {@code point}. */
	public static double angleTo(LivingEntity entity, Vec3 point) {
		double dx = point.x - entity.getX();
		double dz = point.z - entity.getZ();
		if (dx * dx + dz * dz < 1.0E-6D) {
			return 0.0D;
		}
		double yaw = Math.toDegrees(Math.atan2(-dx, dz));
		double diff = net.minecraft.util.Mth.wrapDegrees(yaw - entity.getYHeadRot());
		return Math.abs(diff);
	}

	/** True when {@code defender} looks at {@code attacker} within {@code arc} degrees (guard, deflect, clash). */
	public static boolean facing(LivingEntity defender, LivingEntity attacker, double arc) {
		return angleTo(defender, attacker.position()) <= arc;
	}

	/** The swing's reach: the weapon's, the player's interaction range for long weapons, +0.6 for a thrust. */
	public static double reach(Swing swing) {
		double reach = swing.weapon.reach;
		if (swing.dir == SwingDir.TUSUK) {
			reach += 0.4D;
		}
		if (swing.owner.entity instanceof ServerPlayer player) {
			reach = Math.max(reach, Math.min(reach + 0.5D, player.entityInteractionRange()));
		}
		return reach;
	}

	private static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : v > hi ? hi : v;
	}

	private HitTest() {
	}
}
