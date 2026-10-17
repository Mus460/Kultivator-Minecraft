package dev.kultivator.disciple;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * The disciple's goals. Movement: hold still (meditating, breaking through, bowing), retreat to the master below 25%
 * health, melee combat with a technique every second, return to the post (STAY / MEDITATE) and follow the master
 * (FOLLOW / ASSIST: walk when farther than 5 blocks, teleport when farther than 24). Targets: one goal that picks an
 * enemy according to the order. There is no wandering, no door goal and no item goal.
 */
final class DiscipleAi {

	/** Following disciples walk to the master when farther than this ... */
	static final double FOLLOW_START = 5.0D;
	/** ... stop when this close ... */
	static final double FOLLOW_STOP = 2.5D;
	/** ... and teleport next to them when farther than this (same dimension). */
	static final double TELEPORT_DISTANCE = 24.0D;
	/** STAY guards this radius around its post. */
	static final double GUARD_RADIUS = 12.0D;
	/** A fight never drags a following disciple farther than this from its master ... */
	static final double LEASH_DISTANCE = 32.0D;
	/** ... or a guarding one farther than this from its post. */
	static final double POST_LEASH = 20.0D;
	/** The master's fights count for this long (ticks). */
	static final int MEMORY = 100;
	private static final double FOLLOW_SPEED = 1.25D;
	private static final double COMBAT_SPEED = 1.25D;
	private static final double RETREAT_SPEED = 1.4D;

	/**
	 * True when fighting {@code target} keeps the disciple at its duty: near its master (following orders) or near its
	 * post (STAY / MEDITATE).
	 */
	static boolean withinDuty(DiscipleEntity disciple, LivingEntity target, @Nullable ServerPlayer owner) {
		DiscipleEntity.Mode mode = disciple.mode();
		BlockPos post = disciple.post();
		if (mode.guardsPost() && post != null) {
			return target.distanceToSqr(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D) <= POST_LEASH * POST_LEASH;
		}
		if (owner != null && owner.level() == disciple.level()) {
			return disciple.distanceToSqr(owner) <= LEASH_DISTANCE * LEASH_DISTANCE
					&& target.distanceToSqr(owner) <= LEASH_DISTANCE * LEASH_DISTANCE;
		}
		return disciple.distanceToSqr(target) <= 16.0D * 16.0D;
	}

	private static boolean recent(LivingEntity holder, int timestamp) {
		return holder.tickCount - timestamp < MEMORY;
	}

	/** The master when online in the disciple's dimension (and not a spectator), else null. */
	private static @Nullable ServerPlayer presentOwner(DiscipleEntity disciple) {
		ServerPlayer owner = disciple.owner();
		return owner != null && owner.level() == disciple.level() && !owner.isSpectator() ? owner : null;
	}

	/**
	 * The enemy the disciple should fight now, or null: whoever hurts it, then whoever hurts the master, then (ASSIST)
	 * whatever the master attacks, then a scan: STAY monsters within 12 of the post (not calm neutral ones), FOLLOW and
	 * ASSIST mobs hunting the master or the disciple. MEDITATE only fights back.
	 */
	static @Nullable LivingEntity findTarget(DiscipleEntity disciple) {
		ServerPlayer owner = presentOwner(disciple);
		LivingEntity attacker = disciple.getLastHurtByMob();
		if (attacker != null && recent(disciple, disciple.getLastHurtByMobTimestamp()) && valid(disciple, attacker, owner)) {
			return attacker;
		}
		DiscipleEntity.Mode mode = disciple.mode();
		if (mode == DiscipleEntity.Mode.MEDITATE) {
			return null;
		}
		if (owner != null) {
			LivingEntity enemy = owner.getLastHurtByMob();
			if (enemy != null && recent(owner, owner.getLastHurtByMobTimestamp()) && valid(disciple, enemy, owner)) {
				return enemy;
			}
			if (mode == DiscipleEntity.Mode.ASSIST) {
				LivingEntity victim = owner.getLastHurtMob();
				if (victim != null && recent(owner, owner.getLastHurtMobTimestamp()) && valid(disciple, victim, owner)) {
					return victim;
				}
			}
		}
		Vec3 center;
		double radius;
		switch (mode) {
			case STAY -> {
				BlockPos post = disciple.post();
				if (post == null) {
					return null;
				}
				center = new Vec3(post.getX() + 0.5D, post.getY() + 0.5D, post.getZ() + 0.5D);
				radius = GUARD_RADIUS;
			}
			case FOLLOW -> {
				if (owner == null) {
					return null;
				}
				center = owner.position();
				radius = GUARD_RADIUS;
			}
			case ASSIST -> {
				if (owner == null) {
					return null;
				}
				center = owner.position();
				radius = 10.0D;
			}
			default -> {
				return null;
			}
		}
		final Vec3 scanCenter = center;
		final double radiusSq = radius * radius;
		AABB box = new AABB(scanCenter, scanCenter).inflate(radius, 6.0D, radius);
		List<Mob> mobs = disciple.level().getEntitiesOfClass(Mob.class, box, mob -> mob.distanceToSqr(scanCenter) <= radiusSq
				&& wanted(disciple, mob, owner, mode) && valid(disciple, mob, owner));
		Mob best = null;
		double bestSq = Double.MAX_VALUE;
		for (Mob mob : mobs) {
			double distanceSq = disciple.distanceToSqr(mob);
			if (distanceSq < bestSq) {
				bestSq = distanceSq;
				best = mob;
			}
		}
		return best;
	}

	/**
	 * Which nearby mobs the order cares about (before the general rules in {@link DiscipleEntity#mayAttack}): whatever
	 * hunts the disciple or the master, and for STAY also the monsters near the post, except neutral ones the master left
	 * alone on purpose ({@link #calmNeutral}). FOLLOW and ASSIST never start a fight on their own.
	 */
	private static boolean wanted(DiscipleEntity disciple, Mob mob, @Nullable ServerPlayer owner, DiscipleEntity.Mode mode) {
		boolean hunting = mob.getTarget() == disciple || owner != null && mob.getTarget() == owner;
		return switch (mode) {
			case STAY -> hunting || mob instanceof Enemy && !calmNeutral(mob);
			case FOLLOW, ASSIST -> hunting;
			case MEDITATE -> false;
		};
	}

	/**
	 * Monsters that leave everyone alone until provoked: a neutral mob that is not angry (enderman, zombified piglin), a
	 * piglin and the Warden. Picking a fight with them would set a whole horde (or the Warden) on the master.
	 */
	private static boolean calmNeutral(Mob mob) {
		return mob instanceof NeutralMob neutral && !neutral.isAngry() || mob instanceof AbstractPiglin || mob instanceof Warden;
	}

	private static boolean valid(DiscipleEntity disciple, LivingEntity target, @Nullable ServerPlayer owner) {
		return target.isAlive() && disciple.mayAttack(target) && withinDuty(disciple, target, owner);
	}

	// ================================================================== movement goals

	/** Sits still while meditating or breaking through, and stands bowing while it takes its leave. */
	static final class HoldStillGoal extends Goal {
		private final DiscipleEntity disciple;

		HoldStillGoal(DiscipleEntity disciple) {
			this.disciple = disciple;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return disciple.shouldHoldStill();
		}

		@Override
		public boolean canContinueToUse() {
			return disciple.shouldHoldStill();
		}

		@Override
		public void start() {
			disciple.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (!disciple.getNavigation().isDone()) {
				disciple.getNavigation().stop();
			}
		}
	}

	/**
	 * Below 25% health it runs back to its master (within 2.5 blocks it fights again beside them). When the master cannot
	 * be reached (no path, no safe spot beside them) it gives up for 5 s and fights where it stands.
	 */
	static final class RetreatGoal extends Goal {
		private final DiscipleEntity disciple;
		private int recalc;
		private boolean stuck;
		private int retryAt;

		RetreatGoal(DiscipleEntity disciple) {
			this.disciple = disciple;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (disciple.tickCount < retryAt) {
				return false;
			}
			ServerPlayer owner = presentOwner(disciple);
			return owner != null && !disciple.shouldHoldStill() && disciple.getHealth() < disciple.getMaxHealth() * 0.25F
					&& disciple.distanceToSqr(owner) > 9.0D;
		}

		@Override
		public boolean canContinueToUse() {
			ServerPlayer owner = presentOwner(disciple);
			return owner != null && !stuck && !disciple.shouldHoldStill() && disciple.getHealth() < disciple.getMaxHealth() * 0.35F
					&& disciple.distanceToSqr(owner) > FOLLOW_STOP * FOLLOW_STOP;
		}

		@Override
		public void start() {
			recalc = 0;
			stuck = false;
		}

		@Override
		public void stop() {
			disciple.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			ServerPlayer owner = presentOwner(disciple);
			if (owner == null || --recalc > 0) {
				return;
			}
			recalc = this.adjustedTickDelay(10);
			boolean far = disciple.distanceToSqr(owner) > TELEPORT_DISTANCE * TELEPORT_DISTANCE;
			boolean moving = !far && disciple.getNavigation().moveTo(owner, RETREAT_SPEED);
			if (far || !moving || disciple.getNavigation().isDone()) {
				if (!disciple.teleportNear(owner) || disciple.distanceToSqr(owner) > FOLLOW_STOP * FOLLOW_STOP) {
					// Out of reach (a pillar, a gap): fight on instead of standing there defenceless.
					stuck = true;
					retryAt = disciple.tickCount + 100;
				}
			}
		}
	}

	/** Melee, and every second (before the blow) a chance to use a taught technique on the target. */
	static final class CombatGoal extends MeleeAttackGoal {
		private final DiscipleEntity disciple;
		private int castTimer;

		CombatGoal(DiscipleEntity disciple) {
			super(disciple, COMBAT_SPEED, true);
			this.disciple = disciple;
		}

		@Override
		public boolean canUse() {
			return !disciple.shouldHoldStill() && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return !disciple.shouldHoldStill() && super.canContinueToUse();
		}

		@Override
		public void start() {
			super.start();
			castTimer = 10;
		}

		@Override
		public void tick() {
			LivingEntity target = disciple.getTarget();
			if (target != null && --castTimer <= 0) {
				castTimer = 20;
				if (target.isAlive() && disciple.mayAttack(target)) {
					DiscipleCultivation.tryCastInCombat(disciple, target);
				}
			}
			if (disciple.getTarget() != null) {
				super.tick();
			}
		}
	}

	/** STAY and MEDITATE walk back to their post; carried more than 48 blocks away they return in a flash. */
	static final class ReturnToPostGoal extends Goal {
		private final DiscipleEntity disciple;
		private int recalc;

		ReturnToPostGoal(DiscipleEntity disciple) {
			this.disciple = disciple;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		private double reach() {
			return disciple.mode() == DiscipleEntity.Mode.MEDITATE ? 1.0D : 2.0D;
		}

		private double distanceSq() {
			BlockPos post = disciple.post();
			return post == null ? 0.0D : disciple.distanceToSqr(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D);
		}

		@Override
		public boolean canUse() {
			return disciple.mode().guardsPost() && disciple.post() != null && disciple.getTarget() == null
					&& !disciple.shouldHoldStill() && distanceSq() > reach() * reach();
		}

		@Override
		public boolean canContinueToUse() {
			return disciple.mode().guardsPost() && disciple.post() != null && disciple.getTarget() == null
					&& !disciple.shouldHoldStill() && distanceSq() > 0.36D;
		}

		@Override
		public void start() {
			recalc = 0;
		}

		@Override
		public void stop() {
			disciple.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			BlockPos post = disciple.post();
			if (post == null || --recalc > 0) {
				return;
			}
			recalc = this.adjustedTickDelay(20);
			if (distanceSq() > 48.0D * 48.0D) {
				disciple.teleportToPost();
				return;
			}
			boolean moving = disciple.getNavigation().moveTo(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D, 1.0D);
			if (!moving && distanceSq() > 9.0D) {
				// No path back (it fell into a pit, the way got walled off): step back in a flash.
				disciple.teleportToPost();
			}
		}
	}

	/** FOLLOW and ASSIST walk after the master and teleport next to them when they are far away. */
	static final class FollowMasterGoal extends Goal {
		private final DiscipleEntity disciple;
		private int recalc;

		FollowMasterGoal(DiscipleEntity disciple) {
			this.disciple = disciple;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			ServerPlayer owner = presentOwner(disciple);
			return owner != null && disciple.mode().follows() && disciple.getTarget() == null && !disciple.shouldHoldStill()
					&& disciple.distanceToSqr(owner) > FOLLOW_START * FOLLOW_START;
		}

		@Override
		public boolean canContinueToUse() {
			ServerPlayer owner = presentOwner(disciple);
			return owner != null && disciple.mode().follows() && disciple.getTarget() == null && !disciple.shouldHoldStill()
					&& disciple.distanceToSqr(owner) > FOLLOW_STOP * FOLLOW_STOP;
		}

		@Override
		public void start() {
			recalc = 0;
		}

		@Override
		public void stop() {
			disciple.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			ServerPlayer owner = presentOwner(disciple);
			if (owner == null) {
				return;
			}
			disciple.getLookControl().setLookAt(owner, 10.0F, (float) disciple.getMaxHeadXRot());
			if (--recalc > 0) {
				return;
			}
			recalc = this.adjustedTickDelay(10);
			if (disciple.distanceToSqr(owner) > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
				disciple.teleportNear(owner);
			} else {
				disciple.getNavigation().moveTo(owner, FOLLOW_SPEED);
			}
		}
	}

	// ================================================================== target goal

	/** Twice a second, when it has no target, picks one with {@link #findTarget}. */
	static final class ChooseTargetGoal extends Goal {
		private final DiscipleEntity disciple;
		private int cooldown;
		private @Nullable LivingEntity candidate;

		ChooseTargetGoal(DiscipleEntity disciple) {
			this.disciple = disciple;
			this.setFlags(EnumSet.of(Goal.Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			if (--cooldown > 0) {
				return false;
			}
			// The selector asks every other tick, so this scans about twice a second.
			cooldown = 5;
			LivingEntity current = disciple.getTarget();
			if (current != null && current.isAlive() || disciple.data().breakingThrough() || disciple.isReleasing()) {
				return false;
			}
			candidate = findTarget(disciple);
			return candidate != null;
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			disciple.setTarget(candidate);
			candidate = null;
		}
	}

	private DiscipleAi() {
	}
}
