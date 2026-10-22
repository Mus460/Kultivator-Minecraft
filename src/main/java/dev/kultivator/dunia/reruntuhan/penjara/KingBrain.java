package dev.kultivator.dunia.reruntuhan.penjara;

import dev.kultivator.dunia.reruntuhan.RCreature;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.voice.Voice;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The fight of a demon king (DESIGN 34.5.6): it rises out of the sealed pit (3 s, untouchable), then fights inside a
 * 22 block soft arena. Every skill is telegraphed on the ground first (rings and lines the client draws) and is an
 * entity effect only, a share of the target's max health:
 * <ul>
 * <li>Xiangliu: Sembur Racun Sembilan (nine poison lines fanned at the target), Lilitan Gonggong (a 6 block coil ring),
 * Rawa Busuk (three poison pools under the challengers);</li>
 * <li>Raja Banteng Iblis: Seruduk (a 14 block charge), Gada Besi (a 5 block slam ahead), Raungan Banteng Putih (a 10
 * block roar that slows and weakens);</li>
 * <li>Chiyou: Seratus Senjata (five weapons falling on the challengers), Angin Feng Bo (a wide wind that hurls back),
 * Hujan Yu Shi (six rain circles; Shan Hai Jing 17: Chiyou asked the Wind Earl and the Rain Master for a great
 * storm).</li>
 * </ul>
 * At half health it is enraged (faster skills) and calls two lesser demons once. Each re-lit seal pillar weakens its
 * blows by 15 % (the owner sets {@link RCreature#setDamageFactor}).
 */
public final class KingBrain implements RCreature.Brain {

	public static final int RISE_TICKS = 60;
	private final Vec3 home;
	private final double arena;
	private final BiConsumer<RCreature, DamageSource> onDeath;
	private final BiConsumer<DamageSource, Float> onDamage;
	private final Runnable onEnrage;
	private int cooldown = 70;
	private int melee = 30;
	private boolean enraged;
	private final List<Vec3> marks = new ArrayList<>();
	private Vec3 charge = Vec3.ZERO;

	public KingBrain(Vec3 home, double arena, BiConsumer<RCreature, DamageSource> onDeath, BiConsumer<DamageSource, Float> onDamage, Runnable onEnrage) {
		this.home = home;
		this.arena = arena;
		this.onDeath = onDeath;
		this.onDamage = onDamage;
		this.onEnrage = onEnrage;
	}

	@Override
	public void tick(RCreature c, ServerLevel level) {
		if (c.has(RCreature.FLAG_RISING)) {
			c.getNavigation().stop();
			c.setDeltaMovement(0.0D, 0.0D, 0.0D);
			if (c.skillAge() % 20 == 0) {
				RFx.at(level, home, RFx.SEAL_LEAK, 2, 0, 3.0F);
			}
			if (c.skillAge() >= RISE_TICKS) {
				c.setFlag(RCreature.FLAG_RISING, false);
				c.setSkill(0);
				level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 3.0F, 0.5F);
				Voice.boom(c, "penjara.free_at_last", 64.0D);
			}
			return;
		}
		if (c.position().distanceToSqr(home) > arena * arena) {
			c.getNavigation().moveTo(home.x, home.y, home.z, 1.2D);
			if (c.position().distanceToSqr(home) > (arena + 6.0D) * (arena + 6.0D)) {
				c.teleportTo(home.x, home.y, home.z);
			}
		}
		if (!enraged && c.getHealth() < c.getMaxHealth() * 0.5F) {
			enraged = true;
			c.setFlag(RCreature.FLAG_ENRAGED, true);
			Voice.boom(c, "boss.enraged", 64.0D);
			RFx.big(level, c.position(), RFx.KING_SKILL, c.kind(), 9, 6.0F);
			onEnrage.run();
		}
		LivingEntity target = c.nearestTarget(arena + 6.0D);
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
		double reach = c.getBbWidth() * 0.5D + 3.0D;
		if (--melee <= 0 && c.distanceToSqr(target) < reach * reach) {
			melee = enraged ? 22 : 30;
			c.swingForAttack(InteractionHand.MAIN_HAND);
			c.strike(level, target, (float) (target.getMaxHealth() * 0.12D));
			RCreature.push(target, RCreature.flat(c.position(), target.position()).scale(0.8D).add(0.0D, 0.25D, 0.0D));
		}
		if (--cooldown <= 0) {
			cooldown = (enraged ? 55 : 80) + c.getRandom().nextInt(30);
			begin(c, level, target, 1 + c.getRandom().nextInt(3));
		}
	}

	private void begin(RCreature c, ServerLevel level, LivingEntity target, int skill) {
		c.setSkill(skill);
		c.setFlag(RCreature.FLAG_TELEGRAPH, true);
		c.getNavigation().stop();
		marks.clear();
		int kind = c.kind();
		RFx.at(level, c.position(), RFx.KING_SKILL, kind, skill, 1.0F);
		switch (kind) {
			case RajaIblisEntity.XIANGLIU -> {
				if (skill == 1) {
					for (int i = -4; i <= 4; i++) {
						Vec3 dir = rotate(RCreature.flat(c.position(), target.position()), i * 10.0D);
						RFx.line(level, c.position().add(0.0D, 0.1D, 0.0D), c.position().add(dir.scale(14.0D)).add(0.0D, 0.1D, 0.0D), RFx.LINE, 0x7AC040, 30, 0.6F);
					}
				} else if (skill == 2) {
					RFx.at(level, c.position(), RFx.TELEGRAPH, 0x5A9A30, 28, 6.0F);
				} else {
					for (LivingEntity each : c.targetsAround(home, arena, 8.0D)) {
						if (marks.size() < 3) {
							marks.add(each.position());
							RFx.at(level, each.position(), RFx.TELEGRAPH, 0x6AB040, 36, 3.0F);
						}
					}
				}
			}
			case RajaIblisEntity.BANTENG -> {
				if (skill == 1) {
					charge = RCreature.flat(c.position(), target.position());
					RFx.line(level, c.position().add(0.0D, 0.1D, 0.0D), c.position().add(charge.scale(14.0D)).add(0.0D, 0.1D, 0.0D), RFx.LINE, 0xE04020, 24, 2.0F);
				} else if (skill == 2) {
					Vec3 ahead = c.position().add(RCreature.flat(c.position(), target.position()).scale(3.0D));
					marks.add(ahead);
					RFx.at(level, ahead, RFx.TELEGRAPH, 0xE06030, 26, 5.0F);
				} else {
					RFx.at(level, c.position(), RFx.TELEGRAPH, 0xFFFFFF, 20, 10.0F);
				}
			}
			default -> {
				if (skill == 1) {
					List<LivingEntity> all = c.targetsAround(home, arena, 8.0D);
					for (int i = 0; i < 5; i++) {
						Vec3 at = all.isEmpty() ? home.add(c.getRandom().nextGaussian() * 6.0D, 0.0D, c.getRandom().nextGaussian() * 6.0D)
								: all.get(i % all.size()).position().add(c.getRandom().nextGaussian() * 1.5D, 0.0D, c.getRandom().nextGaussian() * 1.5D);
						marks.add(at);
						RFx.at(level, at, RFx.TELEGRAPH, 0xC89050, 30, 2.0F);
					}
				} else if (skill == 2) {
					charge = RCreature.flat(c.position(), target.position());
					RFx.line(level, c.position().add(0.0D, 0.1D, 0.0D), c.position().add(charge.scale(16.0D)).add(0.0D, 0.1D, 0.0D), RFx.LINE, 0xB0D0E0, 24, 3.0F);
				} else {
					for (int i = 0; i < 6; i++) {
						double a = c.getRandom().nextDouble() * Math.PI * 2.0D;
						double r = 3.0D + c.getRandom().nextDouble() * (arena - 5.0D);
						Vec3 at = home.add(Math.cos(a) * r, 0.0D, Math.sin(a) * r);
						marks.add(at);
						RFx.at(level, at, RFx.TELEGRAPH, 0x6090E0, 30, 2.5F);
					}
				}
			}
		}
	}

	private void play(RCreature c, ServerLevel level, LivingEntity target, int skill) {
		int age = c.skillAge();
		int kind = c.kind();
		switch (kind) {
			case RajaIblisEntity.XIANGLIU -> {
				if (skill == 1 && age == 30) {
					for (int i = -4; i <= 4; i++) {
						Vec3 dir = rotate(RCreature.flat(c.position(), target.position()), i * 10.0D);
						Vec3 from = c.position().add(0.0D, 1.0D, 0.0D);
						Vec3 to = from.add(dir.scale(14.0D));
						RFx.line(level, from, to, RFx.SKILL_HIT, 0x7AC040, 0, 0.6F);
						for (LivingEntity hit : c.targetsAlong(from, to, 0.8D)) {
							hitWith(c, level, hit, 0.10D, new MobEffectInstance(MobEffects.POISON, 100, 1));
						}
					}
					end(c);
				} else if (skill == 2 && age == 28) {
					RFx.at(level, c.position(), RFx.SLAM, 0x5A9A30, 0, 6.0F);
					for (LivingEntity hit : c.targetsAround(c.position(), 6.0D, 3.0D)) {
						hitWith(c, level, hit, 0.20D, new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
						RCreature.push(hit, RCreature.flat(c.position(), hit.position()).scale(1.1D).add(0.0D, 0.3D, 0.0D));
					}
					end(c);
				} else if (skill == 3 && age == 36) {
					for (Vec3 at : marks) {
						RFx.at(level, at, RFx.SKILL_HIT, 0x6AB040, 1, 3.0F);
						for (LivingEntity hit : c.targetsAround(at, 3.0D, 2.0D)) {
							hitWith(c, level, hit, 0.16D, new MobEffectInstance(MobEffects.POISON, 80, 1));
						}
					}
					end(c);
				}
			}
			case RajaIblisEntity.BANTENG -> {
				if (skill == 1) {
					if (age >= 24 && age <= 38) {
						c.setDeltaMovement(charge.x * 1.2D, c.getDeltaMovement().y, charge.z * 1.2D);
						c.needsSync = true;
						for (LivingEntity hit : c.targetsAround(c.position(), c.getBbWidth() * 0.5D + 1.2D, 2.5D)) {
							if (hit.getInvulnerableTime() <= 0) {
								hitWith(c, level, hit, 0.22D, null);
								RCreature.push(hit, charge.scale(1.6D).add(0.0D, 0.5D, 0.0D));
							}
						}
					}
					if (age > 38) {
						end(c);
					}
				} else if (skill == 2 && age == 26) {
					Vec3 at = marks.isEmpty() ? c.position() : marks.get(0);
					RFx.at(level, at, RFx.SLAM, 0xE06030, 0, 5.0F);
					level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5F, 0.5F);
					for (LivingEntity hit : c.targetsAround(at, 5.0D, 3.0D)) {
						hitWith(c, level, hit, 0.20D, null);
						RCreature.push(hit, new Vec3(0.0D, 0.7D, 0.0D));
					}
					end(c);
				} else if (skill == 3 && age == 20) {
					RFx.at(level, c.position(), RFx.QI_WAVE, 0xFFFFFF, 0, 10.0F);
					level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5F, 0.6F);
					for (LivingEntity hit : c.targetsAround(c.position(), 10.0D, 4.0D)) {
						hitWith(c, level, hit, 0.08D, new MobEffectInstance(MobEffects.SLOWNESS, 80, 1));
						hit.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
					}
					end(c);
				}
			}
			default -> {
				if (skill == 1 && age == 30) {
					for (Vec3 at : marks) {
						RFx.line(level, at.add(0.0D, 12.0D, 0.0D), at, RFx.SKILL_HIT, 0xC89050, 2, 0.8F);
						for (LivingEntity hit : c.targetsAround(at, 2.0D, 3.0D)) {
							hitWith(c, level, hit, 0.18D, null);
						}
					}
					level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4F, 0.7F);
					end(c);
				} else if (skill == 2 && age == 24) {
					Vec3 from = c.position().add(0.0D, 1.0D, 0.0D);
					Vec3 to = from.add(charge.scale(16.0D));
					RFx.line(level, from, to, RFx.SKILL_HIT, 0xB0D0E0, 3, 3.0F);
					for (LivingEntity hit : c.targetsAlong(from, to, 2.5D)) {
						hitWith(c, level, hit, 0.10D, null);
						RCreature.push(hit, charge.scale(2.2D).add(0.0D, 0.4D, 0.0D));
					}
					end(c);
				} else if (skill == 3 && age == 30) {
					for (Vec3 at : marks) {
						RFx.at(level, at, RFx.SKILL_HIT, 0x6090E0, 4, 2.5F);
						for (LivingEntity hit : c.targetsAround(at, 2.5D, 3.0D)) {
							hitWith(c, level, hit, 0.15D, null);
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

	private static void hitWith(RCreature c, ServerLevel level, LivingEntity hit, double share, MobEffectInstance effect) {
		if (c.strike(level, hit, (float) (hit.getMaxHealth() * share)) && effect != null) {
			hit.addEffect(effect);
		}
	}

	private static Vec3 rotate(Vec3 dir, double degrees) {
		double a = Math.toRadians(degrees);
		double cos = Math.cos(a);
		double sin = Math.sin(a);
		return new Vec3(dir.x * cos - dir.z * sin, 0.0D, dir.x * sin + dir.z * cos);
	}

	private static void end(RCreature c) {
		c.setSkill(0);
		c.setFlag(RCreature.FLAG_TELEGRAPH, false);
	}

	@Override
	public void damaged(RCreature creature, ServerLevel level, DamageSource source, float amount) {
		onDamage.accept(source, amount);
	}

	@Override
	public void died(RCreature creature, ServerLevel level, DamageSource source) {
		onDeath.accept(creature, source);
	}
}
