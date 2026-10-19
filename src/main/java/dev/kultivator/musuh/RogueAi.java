package dev.kultivator.musuh;

import dev.kultivator.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * The rogue's own goals (DESIGN 25.4.2): choosing a target by {@link RogueTargets}, marching on its anchor (raid stele,
 * village plaque) with 32-block waypoints, channelling the seal within 6 blocks of the anchor, the retreat of tiers 1-2 at
 * 20% health and the leap to a safe spot after 30 s without path progress. Vanilla goals do the rest (melee, float,
 * stroll, look). Nothing here reads more than the mob's own surroundings and nothing changes a block.
 */
final class RogueAi {

	/** Within this distance of its anchor a raider stands and channels. */
	static final double CHANNEL_RANGE = 6.0D;
	/** A target closer than this breaks the channel. */
	static final double CHANNEL_BREAK = 5.0D;
	static final double RETREAT_FRACTION = 0.20D;
	static final int STUCK_TICKS = 600;
	static final double WAYPOINT = 32.0D;

	/** True for roles that march on their anchor and channel there. */
	static boolean marches(RogueCultivatorEntity mob) {
		int role = mob.role();
		return mob.home() != null && (role == RogueCultivators.ROLE_RAID || role == RogueCultivators.ROLE_QUEST);
	}

	/** How far it looks for a new target. */
	static double huntRange(RogueCultivatorEntity mob) {
		return switch (mob.role()) {
			case RogueCultivators.ROLE_RAID, RogueCultivators.ROLE_QUEST -> mob.home() != null ? 12.0D : 24.0D;
			case RogueCultivators.ROLE_CAMP -> 16.0D;
			case RogueCultivators.ROLE_REALM -> 32.0D;
			default -> 24.0D;
		};
	}

	static boolean shouldChannel(RogueCultivatorEntity mob) {
		BlockPos home = mob.home();
		if (home == null || !marches(mob) || mob.distanceToSqr(Vec3.atBottomCenterOf(home)) > CHANNEL_RANGE * CHANNEL_RANGE) {
			return false;
		}
		LivingEntity target = mob.getTarget();
		return target == null || !target.isAlive() || mob.distanceToSqr(target) > CHANNEL_BREAK * CHANNEL_BREAK;
	}

	/**
	 * Every second: the retreat of tiers 1-2 (a vanish puff, counted as fled; never in a realm chamber) and the leap after
	 * 30 s without getting closer to its target or anchor.
	 */
	static void check(RogueCultivatorEntity mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		if (mob.tier() <= 2 && mob.role() != RogueCultivators.ROLE_REALM && mob.isAlive()
				&& mob.getHealth() <= mob.getMaxHealth() * RETREAT_FRACTION) {
			mob.vanish(true);
			return;
		}
		RogueCultivators.Tracked tracked = RogueCultivators.trackedOf(mob);
		if (tracked == null) {
			return;
		}
		Vec3 goal = goal(mob);
		long now = level.getGameTime();
		if (goal == null) {
			tracked.bestDistance = Double.MAX_VALUE;
			tracked.progressAt = now;
			return;
		}
		double distance = mob.position().distanceTo(goal);
		if (distance < 4.0D || distance < tracked.bestDistance - 1.0D) {
			tracked.bestDistance = distance;
			tracked.progressAt = now;
			return;
		}
		if (now - tracked.progressAt < STUCK_TICKS) {
			return;
		}
		tracked.bestDistance = Double.MAX_VALUE;
		tracked.progressAt = now;
		// Leap a few blocks short of the goal, onto a safe spot in a loaded chunk.
		Vec3 toward = mob.position().subtract(goal);
		Vec3 aim = toward.lengthSqr() < 1.0E-4D ? goal : goal.add(toward.normalize().scale(3.0D));
		BlockPos aimPos = BlockPos.containing(aim);
		if (!level.isLoaded(aimPos)) {
			return;
		}
		Vec3 spot = Targeting.safeSpot(level, mob, aim);
		if (spot == null) {
			return;
		}
		RogueSkills.vanishFx(level, mob);
		mob.snapTo(spot.x, spot.y, spot.z, mob.getYRot(), 0.0F);
		mob.setDeltaMovement(Vec3.ZERO);
		mob.resetFallDistance();
		mob.getNavigation().stop();
	}

	private static @Nullable Vec3 goal(RogueCultivatorEntity mob) {
		LivingEntity target = mob.getTarget();
		if (target != null && target.isAlive()) {
			return target.position();
		}
		BlockPos home = mob.home();
		if (home != null && (marches(mob) || mob.role() == RogueCultivators.ROLE_CAMP) && !shouldChannel(mob)) {
			return Vec3.atBottomCenterOf(home);
		}
		return null;
	}

	// ================================================================== goals

	/** Picks the nearest target {@link RogueTargets} allows (every 10 ticks); it never "runs", it only sets the target. */
	static final class ChooseTargetGoal extends Goal {

		private final RogueCultivatorEntity mob;
		private int cooldown;

		ChooseTargetGoal(RogueCultivatorEntity mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			if (--cooldown > 0) {
				return false;
			}
			cooldown = 10;
			double range = huntRange(mob);
			LivingEntity current = mob.getTarget();
			if (current != null && valid(current, range)) {
				return false;
			}
			LivingEntity found = find(range);
			if (found != current) {
				mob.setTarget(found);
			}
			return false;
		}

		private boolean valid(LivingEntity target, double range) {
			if (!RogueTargets.mayHit(mob, target)) {
				return false;
			}
			// Whoever hurt it may be chased further.
			double keep = mob.getLastHurtByMob() == target ? Math.max(32.0D, range * 1.5D) : range * 1.5D;
			return mob.distanceToSqr(target) <= keep * keep;
		}

		private @Nullable LivingEntity find(double range) {
			AABB box = mob.getBoundingBox().inflate(range, 6.0D, range);
			List<LivingEntity> list = mob.level().getEntitiesOfClass(LivingEntity.class, box, e -> RogueTargets.mayHit(mob, e));
			LivingEntity best = null;
			double bestSq = range * range;
			for (LivingEntity each : list) {
				double sq = mob.distanceToSqr(each);
				if (sq <= bestSq && mob.getSensing().hasLineOfSight(each)) {
					bestSq = sq;
					best = each;
				}
			}
			return best;
		}
	}

	/** Walks toward its anchor when it has no target, in waypoints of at most 32 blocks. */
	static final class MarchGoal extends Goal {

		private final RogueCultivatorEntity mob;
		private int repath;

		MarchGoal(RogueCultivatorEntity mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			BlockPos home = mob.home();
			return home != null && marches(mob) && mob.getTarget() == null
					&& mob.distanceToSqr(Vec3.atBottomCenterOf(home)) > (CHANNEL_RANGE - 1.0D) * (CHANNEL_RANGE - 1.0D);
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void start() {
			repath = 0;
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			if (--repath > 0) {
				return;
			}
			repath = 40;
			BlockPos home = mob.home();
			if (home == null) {
				return;
			}
			Vec3 to = Vec3.atBottomCenterOf(home);
			Vec3 from = mob.position();
			Vec3 delta = to.subtract(from);
			double length = delta.length();
			Vec3 point = to;
			if (length > WAYPOINT) {
				Vec3 step = from.add(delta.scale(WAYPOINT / length));
				int x = BlockPos.containing(step).getX();
				int z = BlockPos.containing(step).getZ();
				if (mob.level().isLoaded(BlockPos.containing(step))) {
					point = new Vec3(step.x, mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), step.z);
				} else {
					point = step;
				}
			}
			mob.getNavigation().moveTo(point.x, point.y, point.z, 1.0D);
		}
	}

	/** Stands by its anchor, faces it and channels the seal (the breach meter reads {@code isChanneling}). */
	static final class ChannelGoal extends Goal {

		private final RogueCultivatorEntity mob;

		ChannelGoal(RogueCultivatorEntity mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return shouldChannel(mob);
		}

		@Override
		public void start() {
			mob.getNavigation().stop();
			mob.setChanneling(true);
		}

		@Override
		public void stop() {
			mob.setChanneling(false);
		}

		@Override
		public void tick() {
			BlockPos home = mob.home();
			if (home != null) {
				mob.getLookControl().setLookAt(home.getX() + 0.5D, home.getY() + 1.0D, home.getZ() + 0.5D);
			}
		}
	}

	private RogueAi() {
	}
}
