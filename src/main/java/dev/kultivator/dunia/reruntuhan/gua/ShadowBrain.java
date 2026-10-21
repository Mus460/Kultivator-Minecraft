package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.dunia.reruntuhan.RCreature;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * The mind of a mirror shadow (P5 Cermin Hati, DESIGN 34.5.5, and the tower's Bayangan Diri, 34.5.7): it hunts the
 * player it mirrors, fights in close with three telegraphed arts (Langkah Bayangan, a dash behind the target; Telapak
 * Cermin, a ring of force; Panah Qi, a line of qi) and, whenever the mirrored player heals, the mirror reflects it: the
 * shadow heals twice as much. Its numbers are a share of the player's own ({@link #share}). It stays in its arena.
 * Entities only.
 */
public final class ShadowBrain implements RCreature.Brain {

	private final UUID owner;
	private final Vec3 home;
	private final double arena;
	private final double share;
	private final Consumer<RCreature> onDeath;
	private float lastOwnerHealth = -1.0F;
	private boolean toldHeal;
	private int cooldown = 60;
	private int melee;

	public ShadowBrain(UUID owner, Vec3 home, double arena, double share, Consumer<RCreature> onDeath) {
		this.owner = owner;
		this.home = home;
		this.arena = arena;
		this.share = share;
		this.onDeath = onDeath;
	}

	/** Sets the shadow's numbers from the mirrored player's (health and melee as a share of theirs). */
	public static void mirrorStats(RCreature shadow, ServerPlayer player, double share) {
		double health = Math.max(20.0D, player.getMaxHealth() * share);
		shadow.setBase(Attributes.MAX_HEALTH, health);
		shadow.setBase(Attributes.ATTACK_DAMAGE, Math.max(3.0D, player.getMaxHealth() * 0.10D * share));
		shadow.setBase(Attributes.ARMOR, Math.max(2.0D, player.getArmorValue() * share));
		shadow.setBase(Attributes.MOVEMENT_SPEED, 0.30D + 0.04D * share);
		shadow.setHealth(shadow.getMaxHealth());
	}

	private @Nullable ServerPlayer owner(ServerLevel level) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		return player != null && player.level() == level && player.isAlive() ? player : null;
	}

	@Override
	public void tick(RCreature c, ServerLevel level) {
		if (c.position().distanceToSqr(home) > arena * arena) {
			c.teleportTo(home.x, home.y, home.z);
			c.getNavigation().stop();
		}
		ServerPlayer mirrored = owner(level);
		LivingEntity target = mirrored != null && c.mayHit(mirrored) && mirrored.distanceToSqr(home) < arena * arena * 1.6D ? mirrored
				: c.nearestTarget(arena);
		reflectHeal(c, level, mirrored);
		if (target == null) {
			c.setTarget(null);
			c.getNavigation().stop();
			return;
		}
		c.setTarget(target);
		c.getLookControl().setLookAt(target, 30.0F, 30.0F);
		int skill = c.skill();
		if (skill != 0) {
			playSkill(c, level, target, skill);
			return;
		}
		if (c.tickCount % 10 == 0) {
			c.getNavigation().moveTo(target, 1.15D);
		}
		if (--melee <= 0 && c.distanceToSqr(target) < 2.8D * 2.8D) {
			melee = 16;
			c.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
			c.strike(level, target, (float) c.getAttributeValue(Attributes.ATTACK_DAMAGE));
		}
		if (--cooldown <= 0) {
			cooldown = 70 + c.getRandom().nextInt(30);
			int pick = 1 + c.getRandom().nextInt(3);
			c.setSkill(pick);
			c.setFlag(RCreature.FLAG_TELEGRAPH, true);
			c.getNavigation().stop();
			switch (pick) {
				case BayanganEntity.DASH -> RFx.line(level, c.position(), target.position(), RFx.LINE, 0x6A4AA0, 12, 1.0F);
				case BayanganEntity.PALM -> RFx.at(level, c.position(), RFx.TELEGRAPH, 0x9A7AE0, 18, 3.2F);
				default -> RFx.line(level, c.position().add(0.0D, 1.2D, 0.0D), target.position().add(0.0D, 1.0D, 0.0D), RFx.LINE, 0xC0A0FF, 16, 0.6F);
			}
		}
	}

	private void playSkill(RCreature c, ServerLevel level, LivingEntity target, int skill) {
		int age = c.skillAge();
		switch (skill) {
			case BayanganEntity.DASH -> {
				if (age == 12) {
					Vec3 dir = RCreature.flat(c.position(), target.position());
					Vec3 behind = target.position().add(dir.scale(1.6D));
					for (LivingEntity hit : c.targetsAlong(c.position(), behind, 1.2D)) {
						c.strike(level, hit, (float) (hit.getMaxHealth() * 0.15D * share));
					}
					c.teleportTo(behind.x, target.getY(), behind.z);
					level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 1.4F);
					end(c);
				}
			}
			case BayanganEntity.PALM -> {
				if (age == 18) {
					RFx.at(level, c.position(), RFx.SLAM, 0x9A7AE0, 0, 3.2F);
					for (LivingEntity hit : c.targetsAround(c.position(), 3.2D, 2.0D)) {
						c.strike(level, hit, (float) (hit.getMaxHealth() * 0.12D * share));
						RCreature.push(hit, RCreature.flat(c.position(), hit.position()).scale(1.1D).add(0.0D, 0.35D, 0.0D));
					}
					end(c);
				}
			}
			default -> {
				if (age == 16) {
					Vec3 from = c.position().add(0.0D, 1.2D, 0.0D);
					Vec3 to = from.add(RCreature.flat(c.position(), target.position()).scale(12.0D));
					RFx.line(level, from, to, RFx.SKILL_HIT, 0xC0A0FF, 0, 0.6F);
					for (LivingEntity hit : c.targetsAlong(from, to, 0.9D)) {
						c.strike(level, hit, (float) (hit.getMaxHealth() * 0.10D * share));
					}
					end(c);
				}
			}
		}
		if (age > 40) {
			end(c);
		}
	}

	private static void end(RCreature c) {
		c.setSkill(0);
		c.setFlag(RCreature.FLAG_TELEGRAPH, false);
	}

	/** The mirror reflects the player's healing: the shadow heals twice what the player gained beyond natural regeneration. */
	private void reflectHeal(RCreature c, ServerLevel level, @Nullable ServerPlayer player) {
		if (player == null) {
			lastOwnerHealth = -1.0F;
			return;
		}
		float now = player.getHealth();
		if (lastOwnerHealth >= 0.0F && now - lastOwnerHealth >= 2.0F) {
			float gained = now - lastOwnerHealth;
			c.heal(gained * 2.0F);
			RFx.at(level, c.position().add(0.0D, 1.0D, 0.0D), RFx.MIRROR_HEAL, 0, 0, 1.0F);
			if (!toldHeal) {
				toldHeal = true;
				Msg.chat(player, Component.translatable("kultivator.reruntuhan.teka.cermin.reflect").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
		lastOwnerHealth = now;
	}

	@Override
	public void died(RCreature creature, ServerLevel level, DamageSource source) {
		RFx.at(level, creature.position().add(0.0D, 1.0D, 0.0D), RFx.GUARDIAN_DOWN, 0x9A7AE0, 0, 1.0F);
		onDeath.accept(creature);
	}
}
