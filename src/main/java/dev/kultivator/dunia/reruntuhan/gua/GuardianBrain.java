package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.dunia.reruntuhan.RCreature;
import dev.kultivator.dunia.reruntuhan.RFx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiConsumer;

/**
 * The fight of the Boneka Batu Penjaga (DESIGN 34.5.5): it rises from the arena floor (invulnerable for 1.5 s), walks at
 * the nearest challenger and, between heavy punches, plays three telegraphed skills, all entity effects: Hantaman
 * Bumi (a slam: a 4.5 block ring, 18 % of the target's max health and a knock-up), Pusaran Batu (a 1.5 s spin, 5 % per
 * hit and a push) and Cincin Kerikil (eight lines of thrown pebbles, 10 %). The Kuno tier casts more often. It never
 * leaves its arena.
 */
public final class GuardianBrain implements RCreature.Brain {

	private final Vec3 home;
	private final double arena;
	private final int tier;
	private final BiConsumer<RCreature, DamageSource> onDeath;
	private final BiConsumer<RCreature, LivingEntity> onHit;
	private int cooldown = 80;
	private int melee = 20;
	private float spinAngle;

	public GuardianBrain(Vec3 home, double arena, int tier, BiConsumer<RCreature, DamageSource> onDeath,
						 BiConsumer<RCreature, LivingEntity> onHit) {
		this.home = home;
		this.arena = arena;
		this.tier = tier;
		this.onDeath = onDeath;
		this.onHit = onHit;
	}

	@Override
	public void tick(RCreature c, ServerLevel level) {
		if (c.has(RCreature.FLAG_RISING)) {
			if (c.skillAge() >= 30) {
				c.setFlag(RCreature.FLAG_RISING, false);
				c.setSkill(0);
				level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4F, 0.6F);
			}
			c.getNavigation().stop();
			return;
		}
		if (c.position().distanceToSqr(home) > arena * arena) {
			c.teleportTo(home.x, home.y, home.z);
			c.getNavigation().stop();
		}
		LivingEntity target = c.nearestTarget(arena + 4.0D);
		if (target == null) {
			c.setTarget(null);
			c.getNavigation().stop();
			return;
		}
		c.setTarget(target);
		c.getLookControl().setLookAt(target, 20.0F, 20.0F);
		int skill = c.skill();
		if (skill != 0) {
			play(c, level, target, skill);
			return;
		}
		if (c.tickCount % 10 == 0) {
			c.getNavigation().moveTo(target, 1.0D);
		}
		double reach = c.getBbWidth() * 0.5D + 2.4D;
		if (--melee <= 0 && c.distanceToSqr(target) < reach * reach) {
			melee = 26;
			c.swingForAttack(InteractionHand.MAIN_HAND);
			if (c.strike(level, target, (float) c.getAttributeValue(Attributes.ATTACK_DAMAGE))) {
				onHit.accept(c, target);
				RCreature.push(target, RCreature.flat(c.position(), target.position()).scale(0.7D).add(0.0D, 0.2D, 0.0D));
			}
		}
		if (--cooldown <= 0) {
			cooldown = (tier >= 3 ? 60 : 85) + c.getRandom().nextInt(30);
			int pick = 1 + c.getRandom().nextInt(3);
			c.setSkill(pick);
			c.setFlag(RCreature.FLAG_TELEGRAPH, true);
			c.getNavigation().stop();
			switch (pick) {
				case BonekaBatuEntity.SLAM -> RFx.at(level, c.position(), RFx.TELEGRAPH, 0xC8A060, 24, 4.5F);
				case BonekaBatuEntity.SPIN -> RFx.at(level, c.position(), RFx.TELEGRAPH, 0x9FE0C0, 16, 3.2F);
				default -> {
					for (int i = 0; i < 8; i++) {
						double a = Math.PI * 2.0D * i / 8.0D + c.getYRot() * Math.PI / 180.0D;
						Vec3 to = c.position().add(Math.cos(a) * 10.0D, 0.1D, Math.sin(a) * 10.0D);
						RFx.line(level, c.position().add(0.0D, 0.1D, 0.0D), to, RFx.LINE, 0xB09070, 20, 0.5F);
					}
				}
			}
		}
	}

	private void play(RCreature c, ServerLevel level, LivingEntity target, int skill) {
		int age = c.skillAge();
		switch (skill) {
			case BonekaBatuEntity.SLAM -> {
				if (age == 24) {
					RFx.at(level, c.position(), RFx.SLAM, 0xC8A060, 0, 4.5F);
					level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1.5F, 0.5F);
					for (LivingEntity hit : c.targetsAround(c.position(), 4.5D, 2.5D)) {
						if (c.strike(level, hit, (float) (hit.getMaxHealth() * 0.18D))) {
							onHit.accept(c, hit);
						}
						RCreature.push(hit, new Vec3(0.0D, 0.65D, 0.0D).add(RCreature.flat(c.position(), hit.position()).scale(0.4D)));
					}
					end(c);
				}
			}
			case BonekaBatuEntity.SPIN -> {
				if (age >= 16 && age <= 46) {
					spinAngle += 40.0F;
					c.setYRot(c.getYRot() + 40.0F);
					if ((age - 16) % 6 == 0) {
						for (LivingEntity hit : c.targetsAround(c.position(), 3.2D, 2.5D)) {
							if (c.strike(level, hit, (float) (hit.getMaxHealth() * 0.05D))) {
								onHit.accept(c, hit);
							}
							RCreature.push(hit, RCreature.flat(c.position(), hit.position()).scale(0.6D).add(0.0D, 0.15D, 0.0D));
						}
						level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 0.8F, 1.2F);
					}
					c.getNavigation().moveTo(target, 0.6D);
				}
				if (age > 46) {
					end(c);
				}
			}
			default -> {
				if (age == 20) {
					level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.4F, 0.6F);
					for (int i = 0; i < 8; i++) {
						double a = Math.PI * 2.0D * i / 8.0D + c.getYRot() * Math.PI / 180.0D;
						Vec3 from = c.position().add(0.0D, 0.6D, 0.0D);
						Vec3 to = from.add(Math.cos(a) * 10.0D, 0.0D, Math.sin(a) * 10.0D);
						RFx.line(level, from, to, RFx.SKILL_HIT, 0xB09070, 1, 0.5F);
						for (LivingEntity hit : c.targetsAlong(from, to, 1.0D)) {
							if (c.strike(level, hit, (float) (hit.getMaxHealth() * 0.10D))) {
								onHit.accept(c, hit);
							}
						}
					}
					end(c);
				}
			}
		}
		if (age > 60) {
			end(c);
		}
	}

	private static void end(RCreature c) {
		c.setSkill(0);
		c.setFlag(RCreature.FLAG_TELEGRAPH, false);
	}

	@Override
	public void died(RCreature creature, ServerLevel level, DamageSource source) {
		onDeath.accept(creature, source);
	}
}
