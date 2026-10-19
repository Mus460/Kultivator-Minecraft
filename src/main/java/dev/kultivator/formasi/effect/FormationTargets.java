package dev.kultivator.formasi.effect;

import dev.kultivator.cultivation.DaoProtectors;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.formasi.core.CoreTicker;
import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.own.Ownership;
import dev.kultivator.formasi.stack.Stacking;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.trial.HeartDemonEntity;
import dev.kultivator.util.Targeting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.Npc;

import java.util.Set;

/**
 * Who a formation may touch (DESIGN 24.2.10). An intruder is alive, has AI, is not invulnerable, and is an {@link Enemy}
 * or a mob whose target is a member or a member's pet. Calm neutrals are skipped, and the never-affected list (players,
 * pets, spirit beasts, villagers, disciples, heart demons, trial and test mobs, the Wither and the Ender Dragon, player
 * golems, kept and caged mobs, Dao Protectors) is never touched.
 */
public final class FormationTargets {

	private static final Set<String> NEVER_TAGS = Set.of(TechniquePower.TAG_TRIAL_GUARD, "kultivator.inheritance", "kultivator.dummy",
			TechniquePower.TAG_DUMMY_BOSS, DaoProtectors.TAG);

	/** True when {@code mob} is an intruder this core may damage, root, push, steer or fool. */
	public static boolean mayAffect(Mob mob, CoreContext ctx) {
		if (!mob.isAlive() || mob.isNoAi() || mob.isInvulnerable() || neverAffected(mob)) {
			return false;
		}
		LivingEntity target = mob.getTarget();
		boolean enemy = mob instanceof Enemy;
		if (!enemy) {
			return target != null && (ctx.isMember(target) || Ownership.petOfMember(ctx.level().getServer(), ctx.owner(), target));
		}
		ServerPlayer owner = ctx.ownerOnline();
		if (owner != null) {
			if (target != null && target != owner && ctx.isMember(target)) {
				return true;
			}
			return owner.level() == mob.level() ? Targeting.isHostileTo(mob, owner, false) : !calmOffline(mob, ctx);
		}
		return !calmOffline(mob, ctx);
	}

	/** The offline calm rule: a neutral mob that is not angry, a piglin not targeting a member. */
	private static boolean calmOffline(Mob mob, CoreContext ctx) {
		if (mob instanceof NeutralMob neutral && !neutral.isAngry()) {
			return true;
		}
		if (mob instanceof AbstractPiglin piglin) {
			LivingEntity target = piglin.getTarget();
			return target == null || !ctx.isMember(target);
		}
		return false;
	}

	/** The never-affected list of 24.2.10 (killing, control, illusion and projectile discard alike). */
	public static boolean neverAffected(Mob mob) {
		if (mob instanceof DiscipleEntity || mob instanceof HeartDemonEntity || mob instanceof Npc || mob instanceof WitherBoss
				|| mob instanceof EnderDragon || mob instanceof SnowGolem) {
			return true;
		}
		if (mob instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) {
			return true;
		}
		if (Ownership.spiritBeast(mob)) {
			return true;
		}
		if (mob instanceof IronGolem golem && golem.isPlayerCreated()) {
			return true;
		}
		if (mob instanceof ZombieVillager villager && villager.isConverting()) {
			return true;
		}
		if (mob instanceof Creaking creaking && creaking.isHeartBound()) {
			return true;
		}
		if (mob.hasCustomName() || mob.isLeashed() || mob.isPassenger()) {
			return true;
		}
		for (String tag : mob.entityTags()) {
			if (NEVER_TAGS.contains(tag)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The target modifier applied before the caps: elites, rogue tiers 1-3 and phase-9 foes ({@code kultivator.abadi_foe},
	 * DESIGN 26.14 item 11) ×0.6, bosses (the Patriarch, primordials and the Panglima too) ×0.25, silverfish 0.
	 */
	public static double damageFactor(Mob mob) {
		if (mob instanceof Silverfish) {
			return 0.0D;
		}
		if (TechniquePower.isBoss(mob)) {
			return Stacking.BOSS_DAMAGE;
		}
		return EliteMonsters.isElite(mob) || RogueCultivators.isRogue(mob) || mob.entityTags().contains(TechniquePower.TAG_ABADI_FOE)
				? Stacking.ELITE_DAMAGE : 1.0D;
	}

	/**
	 * Daya Tembus: an elite or a rogue cultivator (phase 8, DESIGN 25.1.2) whose tier is at least the core's effective grade
	 * ignores its illusion and concealment.
	 */
	public static boolean resists(Mob mob, CoreView core) {
		if (immuneToMind(mob)) {
			return true;
		}
		int tier = Math.max(EliteMonsters.isElite(mob) ? EliteMonsters.tier(mob) : 0, RogueCultivators.tier(mob));
		return tier > 0 && tier >= CoreTicker.effectiveGrade(core);
	}

	/** Bosses and Dao Protectors ignore illusion and concealment. */
	public static boolean immuneToMind(Mob mob) {
		return TechniquePower.isBoss(mob) || DaoProtectors.isProtector(mob);
	}

	/** False for ravagers (they break leaves) and bosses: never steered. */
	public static boolean steerable(Mob mob) {
		return !(mob instanceof Ravager) && !TechniquePower.isBoss(mob);
	}

	/** False for ravagers: never moved. */
	public static boolean movable(Mob mob) {
		return !(mob instanceof Ravager);
	}

	private FormationTargets() {
	}
}
