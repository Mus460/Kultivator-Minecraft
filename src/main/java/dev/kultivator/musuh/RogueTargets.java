package dev.kultivator.musuh;

import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Who a rogue cultivator (or anything else this module spawns) may hit (DESIGN 25.1.2): survival and adventure players,
 * disciples of role DISCIPLE, spirit beasts, iron golems, and any mob that hurt it or hunts it. Never villagers, passive
 * animals, pets that leave it alone, candidates, rivals, other rogues, or creative and spectator players. Entities only.
 */
public final class RogueTargets {

	/** True when {@code rogue} may hurt or chase {@code target}. */
	public static boolean mayHit(@Nullable Mob rogue, @Nullable LivingEntity target) {
		if (rogue == null || target == null || target == rogue || !target.isAlive() || target.isRemoved() || target.isSpectator()
				|| target.isInvulnerable() || target.level() != rogue.level()) {
			return false;
		}
		if (target instanceof RogueCultivatorEntity || RogueCultivators.isRogue(target)) {
			return false;
		}
		if (target instanceof Player player) {
			return !player.isCreative() && !player.isSpectator();
		}
		if (target instanceof AbstractVillager) {
			return false;
		}
		if (target instanceof DiscipleEntity disciple) {
			return disciple.role() == DiscipleEntity.Role.DISCIPLE && !disciple.isReleasing();
		}
		if (target instanceof IronGolem || SpiritBeasts.data(target) != null) {
			return true;
		}
		// Anything that hurt it or hunts it (a tamed wolf that joined the fight, a monster that turned on it).
		if (rogue.getLastHurtByMob() == target) {
			return true;
		}
		return target instanceof Mob mob && mob.getTarget() == rogue;
	}

	private RogueTargets() {
	}
}
