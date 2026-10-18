package dev.kultivator.disciple;

import dev.kultivator.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
import java.util.UUID;

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
	/** Below this share of max health a disciple runs back to its master (phase 5). */
	static final double RETREAT_FRACTION = 0.25D;
	private static final double PATROL_SPEED = 0.8D;
	private static final double STROLL_SPEED = 0.6D;
	/** A candidate strolls within this many blocks of its anchor. */
	static final double STROLL_RADIUS = 10.0D;

	/**
	 * True when fighting {@code target} keeps the disciple at its duty: near its master (following orders) or near its
	 * post (STAY / MEDITATE).
	 */
	static boolean withinDuty(DiscipleEntity disciple, LivingEntity target, @Nullable ServerPlayer owner) {
		if (DuelRules.dueling(disciple, target)) {
			return true;
		}
		BlockPos spot = disciple.activitySpot();
		if (disciple.holdsActivitySpot() && spot != null) {
			return target.distanceToSqr(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D) <= POST_LEASH * POST_LEASH;
		}
		if (disciple.role() == DiscipleEntity.Role.CLONE) {
			DiscipleEntity source = cloneSource(disciple);
			LivingEntity leader = source != null ? source : owner;
			return leader == null || leader.level() != disciple.level() ? disciple.distanceToSqr(target) <= 16.0D * 16.0D
					: target.distanceToSqr(leader) <= LEASH_DISTANCE * LEASH_DISTANCE;
		}
		DiscipleEntity.Mode mode = disciple.mode();
		if (mode == DiscipleEntity.Mode.GUARD_HALL) {
			return disciple.inGuardedTerritory(target.position());
		}
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
		// A registered duel decides everything: only the partner (DESIGN 20.2.8).
		LivingEntity partner = DuelRules.partnerOf(disciple);
		if (partner != null) {
			return partner.isAlive() ? partner : null;
		}
		DiscipleEntity.Role role = disciple.role();
		if (role == DiscipleEntity.Role.CANDIDATE || role == DiscipleEntity.Role.RIVAL || role == DiscipleEntity.Role.PHANTOM) {
			return null;
		}
		ServerPlayer owner = presentOwner(disciple);
		LivingEntity attacker = disciple.getLastHurtByMob();
		if (attacker != null && recent(disciple, disciple.getLastHurtByMobTimestamp()) && valid(disciple, attacker, owner)) {
			return attacker;
		}
		if (role == DiscipleEntity.Role.CLONE) {
			return scanClone(disciple, owner);
		}
		DiscipleEntity.Mode mode = disciple.mode();
		if (disciple.holdsActivitySpot()) {
			// Duties, the tower, the spirit body and spectators only fight back; the formation guard keeps its spot clear.
			if (disciple.activity() == DiscipleEntity.ACTIVITY_FORMATION_KEEPER) {
				// Phase 7c keepers: only a Waspada keeper seeks intruders, inside its core radius (DESIGN 24.2.8).
				double watch = DiscipleHooks.keeperWatchRadius(disciple);
				return watch > 0.0D ? scanAround(disciple, owner, disciple.activitySpot(), watch, true) : null;
			}
			return disciple.activity() == DiscipleEntity.ACTIVITY_FORMATION_GUARD
					? scanAround(disciple, owner, disciple.activitySpot(), GUARD_RADIUS, true) : null;
		}
		if (mode == DiscipleEntity.Mode.MEDITATE) {
			return null;
		}
		if (mode == DiscipleEntity.Mode.GUARD_HALL) {
			return scanAround(disciple, owner, disciple.guardAnchor(), DiscipleEntity.HALL_PATROL_RADIUS, true);
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
			case STAY, GUARD_HALL -> hunting || mob instanceof Enemy && !calmNeutral(mob);
			case FOLLOW, ASSIST -> hunting;
			case MEDITATE -> false;
		};
	}

	/**
	 * The nearest mob around {@code center} the disciple should fight: whatever hunts it or its master, and (with
	 * {@code monsters}) every monster that is not a calm neutral one. GUARD_HALL only takes those inside its territory.
	 */
	private static @Nullable LivingEntity scanAround(DiscipleEntity disciple, @Nullable ServerPlayer owner, @Nullable BlockPos center,
			double radius, boolean monsters) {
		if (center == null) {
			return null;
		}
		Vec3 scanCenter = new Vec3(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D);
		double radiusSq = radius * radius;
		boolean territory = disciple.role() == DiscipleEntity.Role.DISCIPLE && disciple.mode() == DiscipleEntity.Mode.GUARD_HALL
				&& !disciple.holdsActivitySpot();
		AABB box = new AABB(scanCenter, scanCenter).inflate(radius, territory ? 16.0D : 6.0D, radius);
		Mob best = null;
		double bestSq = Double.MAX_VALUE;
		for (Mob mob : disciple.level().getEntitiesOfClass(Mob.class, box, each -> each.distanceToSqr(scanCenter) <= radiusSq)) {
			boolean hunting = mob.getTarget() == disciple || owner != null && mob.getTarget() == owner;
			if (!hunting && !(monsters && mob instanceof Enemy && !calmNeutral(mob))) {
				continue;
			}
			if (territory && !disciple.inGuardedTerritory(mob.position()) || !valid(disciple, mob, owner)) {
				continue;
			}
			double distanceSq = disciple.distanceToSqr(mob);
			if (distanceSq < bestSq) {
				bestSq = distanceSq;
				best = mob;
			}
		}
		return best;
	}

	/** A clone fights the monsters around its source (or its master): whatever hunts them, and monsters close by. */
	private static @Nullable LivingEntity scanClone(DiscipleEntity clone, @Nullable ServerPlayer owner) {
		DiscipleEntity source = cloneSource(clone);
		LivingEntity leader = source != null ? source : owner;
		if (leader == null || leader.level() != clone.level()) {
			return null;
		}
		LivingEntity hurtLeader = leader.getLastHurtByMob();
		if (hurtLeader != null && recent(leader, leader.getLastHurtByMobTimestamp()) && valid(clone, hurtLeader, owner)) {
			return hurtLeader;
		}
		return scanAround(clone, owner, leader.blockPosition(), GUARD_RADIUS, true);
	}

	/** A clone's source disciple when it is loaded in the clone's level, else null. */
	static @Nullable DiscipleEntity cloneSource(DiscipleEntity clone) {
		UUID source = clone.cloneSource();
		UUID owner = clone.ownerId();
		if (source == null || owner == null) {
			return null;
		}
		for (DiscipleEntity each : Disciples.loaded(owner)) {
			if (each.soulId().equals(source) && each.level() == clone.level()) {
				return each;
			}
		}
		return null;
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
			return owner != null && !disciple.shouldHoldStill() && !DuelRules.inDuel(disciple)
					&& disciple.getHealth() < disciple.getMaxHealth() * fraction() && disciple.distanceToSqr(owner) > 9.0D;
		}

		@Override
		public boolean canContinueToUse() {
			ServerPlayer owner = presentOwner(disciple);
			return owner != null && !stuck && !disciple.shouldHoldStill() && !DuelRules.inDuel(disciple)
					&& disciple.getHealth() < disciple.getMaxHealth() * (fraction() + 0.1F)
					&& disciple.distanceToSqr(owner) > FOLLOW_STOP * FOLLOW_STOP;
		}

		/** 25% in phase 5; the retreatFraction hooks may raise it. */
		private float fraction() {
			return (float) DiscipleHooks.retreatFraction(disciple, RETREAT_FRACTION);
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
			return disciple.holdsActivitySpot() || disciple.mode() == DiscipleEntity.Mode.MEDITATE ? 1.0D : 2.0D;
		}

		/** An activity's spot first (DESIGN 20.2.5), else the STAY / MEDITATE post. */
		private @Nullable BlockPos target() {
			if (disciple.holdsActivitySpot()) {
				return disciple.activitySpot();
			}
			return disciple.role() == DiscipleEntity.Role.DISCIPLE && disciple.mode().guardsPost() ? disciple.post() : null;
		}

		private double distanceSq() {
			BlockPos post = target();
			return post == null ? 0.0D : disciple.distanceToSqr(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D);
		}

		@Override
		public boolean canUse() {
			return target() != null && disciple.getTarget() == null && !disciple.shouldHoldStill() && !DuelRules.inDuel(disciple)
					&& distanceSq() > reach() * reach();
		}

		@Override
		public boolean canContinueToUse() {
			return target() != null && disciple.getTarget() == null && !disciple.shouldHoldStill() && !DuelRules.inDuel(disciple)
					&& distanceSq() > 0.36D;
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
			BlockPos post = target();
			if (post == null || --recalc > 0) {
				return;
			}
			recalc = this.adjustedTickDelay(20);
			if (distanceSq() > 48.0D * 48.0D) {
				disciple.returnTo(post);
				return;
			}
			boolean moving = disciple.getNavigation().moveTo(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D, 1.0D);
			if (!moving && distanceSq() > 9.0D) {
				// No path back (it fell into a pit, the way got walled off): step back in a flash.
				disciple.returnTo(post);
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

		/** Whom it walks after: the master (FOLLOW / ASSIST), or a clone's source; null when nobody. */
		private @Nullable LivingEntity leader() {
			DiscipleEntity.Role role = disciple.role();
			if (disciple.holdsActivitySpot() || DuelRules.inDuel(disciple)) {
				return null;
			}
			if (role == DiscipleEntity.Role.CLONE) {
				DiscipleEntity source = cloneSource(disciple);
				return source != null ? source : presentOwner(disciple);
			}
			return role == DiscipleEntity.Role.DISCIPLE && disciple.mode().follows() ? presentOwner(disciple) : null;
		}

		@Override
		public boolean canUse() {
			LivingEntity owner = leader();
			return owner != null && disciple.getTarget() == null && !disciple.shouldHoldStill()
					&& disciple.distanceToSqr(owner) > FOLLOW_START * FOLLOW_START;
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity owner = leader();
			return owner != null && disciple.getTarget() == null && !disciple.shouldHoldStill()
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
			LivingEntity owner = leader();
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

	/**
	 * GUARD_HALL (DESIGN 20.2.5): walks back when farther than 32 blocks from its hall stele, otherwise walks a patrol
	 * leg to a random safe spot 8-24 blocks from it, then sits for 10-20 s before the next leg.
	 */
	static final class PatrolHallGoal extends Goal {
		private final DiscipleEntity disciple;

		PatrolHallGoal(DiscipleEntity disciple) {
			this.disciple = disciple;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		private boolean applies() {
			return disciple.role() == DiscipleEntity.Role.DISCIPLE && disciple.mode() == DiscipleEntity.Mode.GUARD_HALL
					&& !disciple.holdsActivitySpot() && disciple.guardAnchor() != null && disciple.getTarget() == null
					&& !disciple.shouldHoldStill() && !DuelRules.inDuel(disciple);
		}

		private double anchorDistanceSq() {
			BlockPos anchor = disciple.guardAnchor();
			return anchor == null ? 0.0D : disciple.distanceToSqr(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
		}

		@Override
		public boolean canUse() {
			return applies() && (anchorDistanceSq() > DiscipleEntity.HALL_PATROL_RADIUS * DiscipleEntity.HALL_PATROL_RADIUS
					|| disciple.tickCount >= disciple.patrolRestUntil());
		}

		@Override
		public boolean canContinueToUse() {
			return applies() && !disciple.getNavigation().isDone();
		}

		@Override
		public void start() {
			BlockPos anchor = disciple.guardAnchor();
			if (anchor == null) {
				return;
			}
			double far = DiscipleEntity.HALL_PATROL_RADIUS * DiscipleEntity.HALL_PATROL_RADIUS;
			if (anchorDistanceSq() > 48.0D * 48.0D) {
				disciple.returnTo(anchor);
			} else if (anchorDistanceSq() > far) {
				if (!disciple.getNavigation().moveTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, 1.0D)) {
					disciple.returnTo(anchor);
				}
			} else {
				Vec3 leg = patrolSpot(anchor);
				if (leg != null) {
					disciple.getNavigation().moveTo(leg.x, leg.y, leg.z, PATROL_SPEED);
				}
			}
			disciple.setPatrolRestUntil(disciple.tickCount + 200 + disciple.getRandom().nextInt(200));
		}

		@Override
		public void stop() {
			disciple.getNavigation().stop();
		}

		/** A safe, loaded spot 8-24 blocks from the anchor (read-only), or null. */
		private @Nullable Vec3 patrolSpot(BlockPos anchor) {
			if (!(disciple.level() instanceof ServerLevel level)) {
				return null;
			}
			for (int attempt = 0; attempt < 4; attempt++) {
				double angle = disciple.getRandom().nextDouble() * Math.PI * 2.0D;
				double radius = 8.0D + disciple.getRandom().nextDouble() * 16.0D;
				Vec3 near = new Vec3(anchor.getX() + 0.5D + Math.cos(angle) * radius, anchor.getY(), anchor.getZ() + 0.5D + Math.sin(angle) * radius);
				if (level.isLoaded(BlockPos.containing(near))) {
					Vec3 spot = Targeting.safeSpot(level, disciple, near);
					if (spot != null) {
						return spot;
					}
				}
			}
			return null;
		}
	}

	/**
	 * A candidate (DESIGN 20.2.5) strolls within 10 blocks of its anchor and looks at players (the look goal is shared);
	 * it decides every 10 ticks and rests a while between walks.
	 */
	static final class CandidateStrollGoal extends Goal {
		private final DiscipleEntity disciple;
		private int nextCheck;

		CandidateStrollGoal(DiscipleEntity disciple) {
			this.disciple = disciple;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			BlockPos anchor = disciple.roleAnchor();
			if (disciple.role() != DiscipleEntity.Role.CANDIDATE || anchor == null || disciple.shouldHoldStill()
					|| disciple.holdsActivitySpot() || DuelRules.inDuel(disciple) || disciple.tickCount < nextCheck) {
				return false;
			}
			nextCheck = disciple.tickCount + 10;
			double reach = STROLL_RADIUS + 2.0D;
			boolean far = disciple.distanceToSqr(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D) > reach * reach;
			return far || disciple.getRandom().nextInt(12) == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return disciple.role() == DiscipleEntity.Role.CANDIDATE && !disciple.shouldHoldStill() && !DuelRules.inDuel(disciple)
					&& !disciple.getNavigation().isDone();
		}

		@Override
		public void start() {
			BlockPos anchor = disciple.roleAnchor();
			if (anchor == null || !(disciple.level() instanceof ServerLevel level)) {
				return;
			}
			double angle = disciple.getRandom().nextDouble() * Math.PI * 2.0D;
			double radius = disciple.getRandom().nextDouble() * STROLL_RADIUS;
			Vec3 near = new Vec3(anchor.getX() + 0.5D + Math.cos(angle) * radius, anchor.getY(), anchor.getZ() + 0.5D + Math.sin(angle) * radius);
			Vec3 spot = level.isLoaded(BlockPos.containing(near)) ? Targeting.safeSpot(level, disciple, near) : null;
			if (spot != null) {
				disciple.getNavigation().moveTo(spot.x, spot.y, spot.z, STROLL_SPEED);
			}
		}

		@Override
		public void stop() {
			disciple.getNavigation().stop();
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
