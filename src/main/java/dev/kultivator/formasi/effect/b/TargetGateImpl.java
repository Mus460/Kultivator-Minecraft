package dev.kultivator.formasi.effect.b;

import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.core.Cores;
import dev.kultivator.formasi.effect.FormationTargets;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.util.TargetGates;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * The concealment and maze target gates of DESIGN 24.2.10 (registered once through {@code TargetGates.addGate}). Event
 * driven: asked only when a server mob is given a new target, so they cost nothing per tick.
 * <ul>
 *   <li><b>Concealment:</b> a member (player, disciple or pet of the owner or a sect mate) standing inside a running
 *   concealment field cannot become the new target of a mob outside that field, unless the member hurt that mob in the
 *   last 60 ticks. Bosses and Dao Protectors always see; Daya Tembus elites see through a weak formation.</li>
 *   <li><b>Maze:</b> a mob inside a running maze field cannot take a member within {@code r − 4} of the eye as its new
 *   target, unless that member hurt it in the last 60 ticks.</li>
 * </ul>
 */
public final class TargetGateImpl implements TargetGates.TargetGate {

	/** Maze targets closer to the eye than {@code r − MAZE_INNER} are refused. */
	public static final double MAZE_INNER = 4.0D;

	@Override
	public boolean allow(Mob mob, LivingEntity target) {
		if (!BUtil.shieldable(target) || !(mob.level() instanceof ServerLevel level) || BUtil.recentlyHurtBy(mob, target)) {
			return true;
		}
		if (FormationTargets.immuneToMind(mob) || BUtil.protector(mob) || BUtil.boss(mob)) {
			return true;
		}
		// Concealment: the target inside, the mob outside.
		List<CoreView> hiding = Cores.covering(level, target.position(), Family.PENYEMBUNYI);
		if (!hiding.isEmpty()) {
			List<CoreView> aroundMob = Cores.covering(level, mob.position(), Family.PENYEMBUNYI);
			for (CoreView core : hiding) {
				if (!BUtil.member(level.getServer(), core.owner(), target) || contains(aroundMob, core) || FormationTargets.resists(mob, core)) {
					continue;
				}
				return false;
			}
		}
		// Maze: the mob inside, the target deep inside.
		List<CoreView> mazes = Cores.covering(level, mob.position(), Family.ILUSI);
		for (CoreView core : mazes) {
			if (!BUtil.member(level.getServer(), core.owner(), target) || FormationTargets.resists(mob, core)) {
				continue;
			}
			double inner = BUtil.radius(core) - MAZE_INNER;
			if (inner > 0.0D && target.position().distanceToSqr(BUtil.centre(core)) <= inner * inner) {
				return false;
			}
		}
		return true;
	}

	private static boolean contains(List<CoreView> cores, CoreView core) {
		for (CoreView each : cores) {
			if (each.id() == core.id() && each.dim().equals(core.dim())) {
				return true;
			}
		}
		return false;
	}
}
