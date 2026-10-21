package dev.kultivator.dunia.kota;

import dev.kultivator.disciple.DuelRules;
import dev.kultivator.dunia.DuniaBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * The NPCs' goals and targeting (DESIGN 34.5.1-34.5.4, rule V3: custom goals run every 4th tick, every 10th with no
 * player within 24 blocks, through {@link DuniaBudget#throttled}). Who an NPC may hit ({@link #mayHit}): its duel
 * partner, players it was made hostile to, the rival group it is fighting, monsters when it guards or travels with a
 * player, and anything that hurt it or hunts it. Never a peaceful player, never a villager, never another townsperson.
 */
final class NpcAi {

	static final double HOLD_RANGE = 2.5D;
	static final double WANDER_RANGE = 10.0D;
	static final double MONSTER_RANGE = 16.0D;
	static final double PLAYER_RANGE = 24.0D;

	/** True when {@code npc} may hurt or chase {@code target}. */
	static boolean mayHit(KultivatorNpc npc, @Nullable LivingEntity target) {
		if (target == null || target == npc || !target.isAlive() || target.isRemoved() || target.isSpectator() || target.level() != npc.level()) {
			return false;
		}
		if (DuelRules.dueling(npc, target)) {
			return true;
		}
		if (target instanceof Player player) {
			if (player.isCreative() || player.isSpectator()) {
				return false;
			}
			return npc.isHostileTo(player.getUUID());
		}
		if (target instanceof KultivatorNpc other) {
			String foe = npc.memory().getStringOr("foe", "");
			if (!foe.isEmpty() && foe.equals(other.group())) {
				return true;
			}
			if (other.role().townsfolk() && npc.role().townsfolk()) {
				return false;
			}
			// guards answer an outsider that harms the town
			return npc.role() == NpcRole.PENJAGA && other.hasFlag(KultivatorNpc.FLAG_HOSTILE) && other.getLastHurtMob() != null
					&& other.getLastHurtMob() instanceof KultivatorNpc victim && victim.role().townsfolk();
		}
		if (target instanceof net.minecraft.world.entity.npc.villager.AbstractVillager) {
			return false;
		}
		if (target instanceof Enemy && npc.fightsMonsters()) {
			return true;
		}
		if (npc.getLastHurtByMob() == target) {
			return true;
		}
		return target instanceof Mob mob && mob.getTarget() == npc;
	}

	/** Every 20 ticks (server): the lonely despawn, a companion's day, skills and the role's own pass. */
	static void check(KultivatorNpc npc) {
		if (!(npc.level() instanceof ServerLevel level) || npc.isRemoved()) {
			return;
		}
		long now = level.getGameTime();
		Player near = level.getNearestPlayer(npc.getX(), npc.getY(), npc.getZ(), npc.lonelyRange(), entity -> !entity.isSpectator());
		if (near != null) {
			npc.resetLonely(now);
		} else if (now - npc.lonelySince() >= npc.lonelyTicks()) {
			npc.vanish();
			return;
		}
		if (npc.companionOf() != null && now >= npc.companionUntil()) {
			Pengembara.companionEnd(npc);
			if (npc.isRemoved()) {
				return;
			}
		}
		LivingEntity target = npc.getTarget();
		if (target != null && (!target.isAlive() || !mayHit(npc, target))) {
			npc.setTarget(null);
			target = null;
		}
		if (target != null) {
			NpcSkills.use(level, npc, target, now);
		}
		Kota.tickNpc(level, npc, now);
	}

	// ================================================================== goals

	/** Stationary roles keep to their spot (a stage, a counter, a lectern) and face visitors. */
	static final class HoldGoal extends Goal {
		private final KultivatorNpc npc;

		HoldGoal(KultivatorNpc npc) {
			this.npc = npc;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			BlockPos home = npc.home();
			boolean holds = npc.role().stationary || npc.memory().getBooleanOr("hold", false);
			return holds && npc.getTarget() == null && home != null && DuniaBudget.throttled(npc, 4)
					&& npc.position().distanceToSqr(Vec3.atBottomCenterOf(home)) > HOLD_RANGE * HOLD_RANGE;
		}

		@Override
		public void start() {
			BlockPos home = npc.home();
			if (home != null) {
				npc.getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 0.7D);
			}
		}

		@Override
		public boolean canContinueToUse() {
			return npc.getTarget() == null && !npc.getNavigation().isDone();
		}
	}

	/** A companion walks with its player (teleporting back beside them when left far behind). */
	static final class FollowGoal extends Goal {
		private final KultivatorNpc npc;
		private @Nullable ServerPlayer owner;

		FollowGoal(KultivatorNpc npc) {
			this.npc = npc;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			UUID id = npc.companionOf();
			if (id == null || npc.getTarget() != null || !(npc.level() instanceof ServerLevel level) || !DuniaBudget.throttled(npc, 4)) {
				return false;
			}
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			if (player == null || player.level() != level) {
				return false;
			}
			owner = player;
			return npc.distanceToSqr(player) > 16.0D;
		}

		@Override
		public void start() {
			if (owner == null) {
				return;
			}
			if (npc.distanceToSqr(owner) > 40.0D * 40.0D) {
				Vec3 behind = owner.position().subtract(owner.getLookAngle().multiply(2.0D, 0.0D, 2.0D));
				npc.snapTo(behind.x, owner.getY(), behind.z, owner.getYRot(), 0.0F);
				npc.getNavigation().stop();
				return;
			}
			npc.getNavigation().moveTo(owner, 1.1D);
		}

		@Override
		public boolean canContinueToUse() {
			return owner != null && npc.getTarget() == null && !npc.getNavigation().isDone() && npc.distanceToSqr(owner) > 9.0D;
		}

		@Override
		public void stop() {
			owner = null;
		}
	}

	/**
	 * Walking roles stroll near home (townsfolk: by day between their home and the square, indoors at night; wanderers
	 * and rivals around where they stand).
	 */
	static final class HomeGoal extends Goal {
		private final KultivatorNpc npc;

		HomeGoal(KultivatorNpc npc) {
			this.npc = npc;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (npc.role().stationary || npc.memory().getBooleanOr("hold", false) || npc.companionOf() != null || npc.getTarget() != null || npc.hasFlag(KultivatorNpc.FLAG_SITTING)
					|| npc.memory().getBooleanOr("busy", false)) {
				return false;
			}
			return DuniaBudget.throttled(npc, 4) && npc.getRandom().nextInt(24) == 0;
		}

		@Override
		public void start() {
			Vec3 goal = goal();
			if (goal != null) {
				npc.getNavigation().moveTo(goal.x, goal.y, goal.z, 0.6D);
			}
		}

		private @Nullable Vec3 goal() {
			BlockPos home = npc.home();
			Vec3 base = home != null ? Vec3.atBottomCenterOf(home) : npc.position();
			if (npc.role() == NpcRole.WARGA && npc.level() instanceof ServerLevel level) {
				long time = Math.floorMod(level.getServer().overworld().getOverworldClockTime(), 24000L);
				boolean night = time >= 13000L && time < 23500L;
				if (night) {
					return base;
				}
				BlockPos square = BlockPos.of(npc.memory().getLongOr("square", 0L));
				if (npc.memory().contains("square") && npc.getRandom().nextBoolean()) {
					base = Vec3.atBottomCenterOf(square);
				}
			}
			double angle = npc.getRandom().nextDouble() * Math.PI * 2.0D;
			double distance = 2.0D + npc.getRandom().nextDouble() * (WANDER_RANGE - 2.0D);
			return base.add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
		}

		@Override
		public boolean canContinueToUse() {
			return npc.getTarget() == null && !npc.getNavigation().isDone();
		}
	}

	/** Picks a target every 10 ticks among those {@link #mayHit} allows: hostile players, rival foes, monsters. */
	static final class ChooseTargetGoal extends Goal {
		private final KultivatorNpc npc;

		ChooseTargetGoal(KultivatorNpc npc) {
			this.npc = npc;
			this.setFlags(EnumSet.of(Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			if (npc.tickCount % 10 != 0) {
				return false;
			}
			LivingEntity current = npc.getTarget();
			if (current != null && current.isAlive() && mayHit(npc, current)) {
				return false;
			}
			LivingEntity partner = DuelRules.partnerOf(npc);
			if (partner != null) {
				npc.setTarget(partner);
				return false;
			}
			LivingEntity best = find();
			if (best != null) {
				npc.setTarget(best);
			}
			return false;
		}

		private @Nullable LivingEntity find() {
			if (!(npc.level() instanceof ServerLevel level)) {
				return null;
			}
			if (!npc.hostiles().isEmpty()) {
				for (UUID id : npc.hostiles()) {
					ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
					if (player != null && player.level() == level && player.distanceToSqr(npc) <= PLAYER_RANGE * PLAYER_RANGE && mayHit(npc, player)) {
						return player;
					}
				}
			}
			boolean foe = !npc.memory().getStringOr("foe", "").isEmpty();
			if (!foe && !npc.fightsMonsters()) {
				return null;
			}
			Vec3 center = npc.position();
			if (npc.companionOf() != null) {
				ServerPlayer owner = level.getServer().getPlayerList().getPlayer(npc.companionOf());
				if (owner != null && owner.level() == level) {
					center = owner.position();
				}
			}
			List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(MONSTER_RANGE, 6.0D, MONSTER_RANGE),
					each -> each != npc && mayHit(npc, each) && !(each instanceof Player));
			LivingEntity best = null;
			double bestSq = Double.MAX_VALUE;
			for (LivingEntity each : around) {
				double d = each.distanceToSqr(npc);
				if (d < bestSq) {
					bestSq = d;
					best = each;
				}
			}
			return best;
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}
	}

	private NpcAi() {
	}
}
