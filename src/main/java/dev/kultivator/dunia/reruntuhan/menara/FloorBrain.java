package dev.kultivator.dunia.reruntuhan.menara;

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
import java.util.function.Consumer;

/**
 * The fight of a Penjaga Lantai (DESIGN 34.5.7). Every guardian has three telegraphed skills shaped by its nature
 * (entity effects only, a share of the target's max health):
 * <ul>
 * <li>a line: Lembu Besi charges, Ular Giok spits poison, Bangau Api breathes flame, Harimau Petir pounces with
 * lightning (a light effect, never a {@code LightningBolt}), Naga Bayangan breathes shadow, ...;</li>
 * <li>a ring around itself: stomps, coils, wing gusts, the turtle's quake;</li>
 * <li>circles under the challengers: fire rain, fox fire, falling feathers, void pools.</li>
 * </ul>
 * Kura-kura Batu hardens its shell after its ring (damage taken ×0.3 for 3 s), Bangau Abadi heals a little, Rubah Ilusi
 * blinks behind its target. It stays in its arena.
 */
public final class FloorBrain implements RCreature.Brain {

	private static final int[] COLOR = {0xB0B8C8, 0x5ACB8A, 0xFF6A2A, 0xA89070, 0xB57CFF, 0xFF9AD8, 0xFFD54A, 0xE8F4FF, 0x6A4AA0};
	private final Vec3 home;
	private final double arena;
	private final Consumer<RCreature> onDeath;
	private int cooldown = 60;
	private int melee = 24;
	private int shell;
	private final List<Vec3> marks = new ArrayList<>();
	private Vec3 dir = Vec3.ZERO;

	public FloorBrain(Vec3 home, double arena, Consumer<RCreature> onDeath) {
		this.home = home;
		this.arena = arena;
		this.onDeath = onDeath;
	}

	/** The stone turtle's hardened shell takes 30 % of the damage. */
	@Override
	public double incoming(RCreature creature) {
		return shell > 0 ? 0.3D : 1.0D;
	}

	@Override
	public void tick(RCreature c, ServerLevel level) {
		if (shell > 0) {
			shell--;
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
		c.getLookControl().setLookAt(target, 30.0F, 30.0F);
		int skill = c.skill();
		if (skill != 0) {
			play(c, level, target, skill);
			return;
		}
		if (c.tickCount % 10 == 0) {
			c.getNavigation().moveTo(target, 1.1D);
		}
		double reach = c.getBbWidth() * 0.5D + 2.4D;
		if (--melee <= 0 && c.distanceToSqr(target) < reach * reach) {
			melee = 24;
			c.swingForAttack(InteractionHand.MAIN_HAND);
			c.strike(level, target, (float) (target.getMaxHealth() * 0.10D));
		}
		if (--cooldown <= 0) {
			cooldown = 65 + c.getRandom().nextInt(30) - (c.has(RCreature.FLAG_ENRAGED) ? 20 : 0);
			begin(c, level, target, 1 + c.getRandom().nextInt(3));
		}
		if (!c.has(RCreature.FLAG_ENRAGED) && c.getHealth() < c.getMaxHealth() * 0.4F) {
			c.setFlag(RCreature.FLAG_ENRAGED, true);
			Voice.boom(c, "boss.enraged", 48.0D);
		}
	}

	private int rgb(RCreature c) {
		return COLOR[Math.max(0, Math.min(COLOR.length - 1, c.kind()))];
	}

	private void begin(RCreature c, ServerLevel level, LivingEntity target, int skill) {
		c.setSkill(skill);
		c.setFlag(RCreature.FLAG_TELEGRAPH, true);
		c.getNavigation().stop();
		marks.clear();
		int rgb = rgb(c);
		switch (skill) {
			case 1 -> {
				dir = RCreature.flat(c.position(), target.position());
				RFx.line(level, c.position().add(0.0D, 0.1D, 0.0D), c.position().add(dir.scale(13.0D)).add(0.0D, 0.1D, 0.0D), RFx.LINE, rgb, 22,
						c.kind() == 0 || c.kind() == 8 ? 2.2F : 1.2F);
			}
			case 2 -> RFx.at(level, c.position(), RFx.TELEGRAPH, rgb, 24, c.kind() == 3 ? 6.0F : 4.5F);
			default -> {
				for (LivingEntity each : c.targetsAround(home, arena, 6.0D)) {
					if (marks.size() < 4) {
						marks.add(each.position());
						RFx.at(level, each.position(), RFx.TELEGRAPH, rgb, 28, 2.4F);
					}
				}
				while (marks.size() < 3) {
					double a = c.getRandom().nextDouble() * Math.PI * 2.0D;
					double r = 3.0D + c.getRandom().nextDouble() * (arena - 5.0D);
					Vec3 at = home.add(Math.cos(a) * r, 0.0D, Math.sin(a) * r);
					marks.add(at);
					RFx.at(level, at, RFx.TELEGRAPH, rgb, 28, 2.4F);
				}
			}
		}
	}

	private void play(RCreature c, ServerLevel level, LivingEntity target, int skill) {
		int age = c.skillAge();
		int kind = c.kind();
		int rgb = rgb(c);
		switch (skill) {
			case 1 -> {
				boolean charges = kind == 0 || kind == 4 || kind == 6;
				if (charges) {
					if (age >= 22 && age <= 34) {
						c.setDeltaMovement(dir.x * 1.15D, c.getDeltaMovement().y, dir.z * 1.15D);
						c.needsSync = true;
						for (LivingEntity hit : c.targetsAround(c.position(), c.getBbWidth() * 0.5D + 1.0D, 2.0D)) {
							if (hit.getInvulnerableTime() <= 0) {
								c.strike(level, hit, (float) (hit.getMaxHealth() * 0.20D));
								RCreature.push(hit, dir.scale(1.4D).add(0.0D, 0.45D, 0.0D));
								if (kind == 4) {
									hit.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
								}
							}
						}
					}
					if (age > 34) {
						end(c);
					}
				} else if (age == 22) {
					Vec3 from = c.position().add(0.0D, c.getBbHeight() * 0.6D, 0.0D);
					Vec3 to = from.add(dir.scale(13.0D));
					RFx.line(level, from, to, RFx.SKILL_HIT, rgb, kind, 1.2F);
					for (LivingEntity hit : c.targetsAlong(from, to, kind == 8 ? 2.0D : 1.2D)) {
						c.strike(level, hit, (float) (hit.getMaxHealth() * 0.16D));
						if (kind == 1) {
							hit.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
						} else if (kind == 2) {
							hit.igniteForSeconds(4.0F);
						} else if (kind == 8) {
							hit.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
						}
					}
					if (kind == 5) {
						Vec3 behind = target.position().add(RCreature.flat(c.position(), target.position()).scale(2.0D));
						c.teleportTo(behind.x, target.getY(), behind.z);
						level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.FOX_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
					}
					end(c);
				}
			}
			case 2 -> {
				if (age == 24) {
					float r = kind == 3 ? 6.0F : 4.5F;
					RFx.at(level, c.position(), kind == 2 || kind == 7 ? RFx.QI_WAVE : RFx.SLAM, rgb, 0, r);
					level.playSound(null, c.getX(), c.getY(), c.getZ(), kind == 2 || kind == 7 ? SoundEvents.PHANTOM_FLAP : SoundEvents.RAVAGER_STEP,
							SoundSource.HOSTILE, 1.5F, 0.6F);
					for (LivingEntity hit : c.targetsAround(c.position(), r, 3.0D)) {
						c.strike(level, hit, (float) (hit.getMaxHealth() * 0.15D));
						RCreature.push(hit, RCreature.flat(c.position(), hit.position()).scale(kind == 2 || kind == 7 ? 1.6D : 0.9D).add(0.0D, 0.4D, 0.0D));
					}
					if (kind == 3) {
						shell = 60;
					}
					if (kind == 7) {
						c.heal(c.getMaxHealth() * 0.03F);
					}
					end(c);
				}
			}
			default -> {
				if (age == 28) {
					for (Vec3 at : marks) {
						RFx.at(level, at, RFx.SKILL_HIT, rgb, kind, 2.4F);
						for (LivingEntity hit : c.targetsAround(at, 2.4D, 3.0D)) {
							c.strike(level, hit, (float) (hit.getMaxHealth() * 0.14D));
							if (kind == 2 || kind == 6) {
								hit.igniteForSeconds(3.0F);
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
		RFx.big(level, creature.position(), RFx.GUARDIAN_DOWN, COLOR[Math.max(0, Math.min(COLOR.length - 1, creature.kind()))], 2, 3.0F);
		onDeath.accept(creature);
	}
}
