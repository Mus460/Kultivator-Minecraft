package dev.kultivator.duel.ai;

import dev.kultivator.Kultivator;
import dev.kultivator.duel.clash.ClashClock;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.mixin.duel.MobGoalSelectorAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * The duel goal of a non-player duellist (DESIGN 35.7.1): priority 0 with MOVE | LOOK, added when the session starts
 * and removed at its end. Holding both flags it keeps the mob's own melee goal ({@code DiscipleAi.CombatGoal},
 * {@code MeleeAttackGoal} of the rogues) from running during the duel: GATHER walks to the mark, INTRO, COUNTDOWN and
 * FINISHER stand and face the opponent, FIGHT hands every tick to the {@link DuelistBrain}.
 */
public final class DuelistGoal extends Goal {

	private final Mob mob;
	private final DuelSession session;
	private final DuelistBrain brain;

	private DuelistGoal(Mob mob, DuelSession session, DuelistBrain brain) {
		this.mob = mob;
		this.session = session;
		this.brain = brain;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	/** Adds the goal to {@code mob}; null when its goal selector cannot be reached. */
	public static @Nullable DuelistGoal install(Mob mob, DuelSession session, DuelistBrain brain) {
		try {
			DuelistGoal goal = new DuelistGoal(mob, session, brain);
			((MobGoalSelectorAccessor) mob).kultivator$goalSelector().addGoal(0, goal);
			mob.getNavigation().stop();
			return goal;
		} catch (RuntimeException | LinkageError error) {
			Kultivator.LOGGER.warn("Duel: could not give {} its duel goal", mob, error);
			return null;
		}
	}

	/** Removes the goal again (end of the session). */
	public static void uninstall(Mob mob, DuelistGoal goal) {
		try {
			((MobGoalSelectorAccessor) mob).kultivator$goalSelector().removeGoal(goal);
			mob.getNavigation().stop();
		} catch (RuntimeException | LinkageError error) {
			Kultivator.LOGGER.warn("Duel: could not remove the duel goal of {}", mob, error);
		}
	}

	@Override
	public boolean canUse() {
		return mob.isAlive() && !session.ended() && session.phase() != DuelSession.Phase.ENDED && session.phase() != DuelSession.Phase.INVITE;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public boolean isInterruptable() {
		return false;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void stop() {
		mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		LivingEntity partner = session.partnerOf(mob);
		if (partner == null) {
			return;
		}
		long now = ClashClock.now();
		switch (session.phase()) {
			case GATHER -> {
				Vec3 mark = session.mark(mob);
				double dx = mob.getX() - mark.x;
				double dz = mob.getZ() - mark.z;
				if (dx * dx + dz * dz > DuelSession.MARK_REACHED * DuelSession.MARK_REACHED * 0.5D) {
					if (mob.getNavigation().isDone() || now % 20 == 0) {
						mob.getNavigation().moveTo(mark.x, mark.y, mark.z, 1.0D);
					}
				} else {
					mob.getNavigation().stop();
					mob.getLookControl().setLookAt(partner, 30.0F, 30.0F);
				}
			}
			case INTRO, COUNTDOWN, FINISHER -> {
				mob.getNavigation().stop();
				mob.getLookControl().setLookAt(partner, 30.0F, 30.0F);
			}
			case FIGHT -> brain.tick(now);
			default -> {
			}
		}
	}
}
