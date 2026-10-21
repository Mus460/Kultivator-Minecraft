package dev.kultivator.dunia.reruntuhan.penjara;

import dev.kultivator.dunia.reruntuhan.RCreature;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.voice.Voice;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A lesser demon's mind (DESIGN 34.5.6): it hunts the nearest target within its leash of home, strikes for
 * {@code share} of the target's max health and now and then leaps (a short telegraphed pounce). It never wanders out
 * of its leash; with nothing to hunt it paces near home. Entities only.
 */
public final class LesserBrain implements RCreature.Brain {

	private final Vec3 home;
	private final double leash;
	private final double share;
	private int melee = 20;
	private int leap = 80;

	public LesserBrain(Vec3 home, double leash, double share) {
		this.home = home;
		this.leash = leash;
		this.share = share;
	}

	@Override
	public void tick(RCreature c, ServerLevel level) {
		if (c.position().distanceToSqr(home) > (leash + 8.0D) * (leash + 8.0D)) {
			c.teleportTo(home.x, home.y, home.z);
		}
		if (!dev.kultivator.dunia.DuniaBudget.throttled(c, 2) && c.skill() == 0) {
			return;
		}
		LivingEntity target = c.nearestTarget(24.0D);
		if (target != null && target.position().distanceToSqr(home) > leash * leash) {
			target = null;
		}
		if (target == null) {
			c.setTarget(null);
			if (c.getNavigation().isDone() && c.getRandom().nextInt(20) == 0) {
				double a = c.getRandom().nextDouble() * Math.PI * 2.0D;
				double r = c.getRandom().nextDouble() * Math.min(10.0D, leash);
				c.getNavigation().moveTo(home.x + Math.cos(a) * r, home.y, home.z + Math.sin(a) * r, 0.8D);
			}
			return;
		}
		c.setTarget(target);
		c.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (c.skill() == IblisKecilEntity.LEAP) {
			if (c.skillAge() == 10) {
				Vec3 dir = RCreature.flat(c.position(), target.position());
				c.setDeltaMovement(dir.x * 1.1D, 0.45D, dir.z * 1.1D);
				c.needsSync = true;
			}
			if (c.skillAge() >= 22) {
				for (LivingEntity hit : c.targetsAround(c.position(), 1.8D, 1.5D)) {
					c.strike(level, hit, (float) (hit.getMaxHealth() * share * 1.4D));
				}
				c.setSkill(0);
				c.setFlag(RCreature.FLAG_TELEGRAPH, false);
			}
			return;
		}
		if (c.tickCount % 10 == 0) {
			c.getNavigation().moveTo(target, 1.2D);
		}
		if (--melee <= 0 && c.distanceToSqr(target) < 2.4D * 2.4D) {
			melee = 20;
			c.swingForAttack(InteractionHand.MAIN_HAND);
			c.strike(level, target, (float) (target.getMaxHealth() * share));
		}
		if (--leap <= 0 && c.distanceToSqr(target) > 4.0D * 4.0D && c.distanceToSqr(target) < 9.0D * 9.0D) {
			leap = 100 + c.getRandom().nextInt(60);
			c.setSkill(IblisKecilEntity.LEAP);
			c.setFlag(RCreature.FLAG_TELEGRAPH, true);
			RFx.at(level, target.position(), RFx.TELEGRAPH, 0xC02020, 20, 1.8F);
			if (c.getRandom().nextInt(3) == 0) {
				Voice.say(c, "demon.growl", "");
			}
		}
	}
}
