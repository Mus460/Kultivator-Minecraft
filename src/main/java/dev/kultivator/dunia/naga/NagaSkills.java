package dev.kultivator.dunia.naga;

import dev.kultivator.fx.Fx;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * The fight skills of a Jiao (DESIGN 34.5.11), all telegraphed and all entity-only: damage, knockback and potion effects
 * on living things, particles and sounds; never a block, a fire on the ground, an explosion or a LightningBolt. The client
 * draws every telegraph from the synced skill id and its tick count ({@code NagaRenderer}).
 * <ul>
 * <li>Napas Unsur: a 14-block cone (about 25 degrees) in six pulses: water slows, wind throws back, lightning jumps to
 * two more targets;</li>
 * <li>Ekor Sabet: the tail end glows, then a ring of 6 blocks around it strikes and throws back;</li>
 * <li>Raungan Naga: Weakness for 4 s to every player within 20 blocks;</li>
 * <li>Lilitan: it circles its target ever closer for 5 s and squeezes every second it is within reach; 5 % of its health
 * in damage breaks the coil;</li>
 * <li>a diving bite.</li>
 * </ul>
 */
final class NagaSkills {

	/** Wild damage at Nascent Soul (× the realm damage factor). */
	static final float BITE = 10.0F;
	static final float BREATH_PULSE = 2.2F;
	static final float TAIL = 9.0F;
	static final float COIL = 4.0F;

	static void run(ServerLevel level, NagaLiarEntity dragon, LivingEntity target, int skill, int t) {
		switch (skill) {
			case NagaLiarEntity.SKILL_BREATH -> {
				Predicate<LivingEntity> victims = entity -> entity == target || wildVictim(entity);
				if (breath(level, dragon, target, t, victims, (float) (BREATH_PULSE * dragon.damageFactor))) {
					dragon.finishSkill();
				}
			}
			case NagaLiarEntity.SKILL_TAIL -> tail(level, dragon, target, t);
			case NagaLiarEntity.SKILL_ROAR -> roar(level, dragon, target, t);
			case NagaLiarEntity.SKILL_COIL -> coil(level, dragon, target, t);
			case NagaLiarEntity.SKILL_BITE -> bite(level, dragon, target, t);
			default -> dragon.finishSkill();
		}
	}

	/** A player a wild Jiao may hurt (never creative or spectator). */
	static boolean wildVictim(LivingEntity entity) {
		return entity instanceof Player player && player.isAlive() && !player.isCreative() && !player.isSpectator();
	}

	// ================================================================== Napas Unsur

	/**
	 * One tick of the breath at {@code aim}: hover and gather for {@link NagaLiarEntity#BREATH_TELEGRAPH} ticks, then six
	 * pulses over {@link NagaLiarEntity#BREATH_ACTIVE}. True when the breath is over.
	 */
	static boolean breath(ServerLevel level, NagaLiarEntity dragon, @Nullable LivingEntity aim, int t, Predicate<LivingEntity> victims, float pulse) {
		if (aim != null && !dragon.isTamed()) {
			// keep a fair distance in front of the target, a little above it
			Vec3 away = dragon.position().subtract(aim.position()).multiply(1.0D, 0.0D, 1.0D);
			if (away.lengthSqr() < 1.0E-3D) {
				away = new Vec3(1.0D, 0.0D, 0.0D);
			}
			Vec3 spot = aim.position().add(away.normalize().scale(10.0D)).add(0.0D, 3.0D, 0.0D);
			dragon.steer(spot, 0.25D, 0.06D);
		} else if (!dragon.isTamed()) {
			dragon.hover(dragon.position(), 0.0D);
		}
		if (aim != null && dragon.getControllingPassenger() == null) {
			// a ridden Jiao keeps the heading its rider gives it
			dragon.faceVelocity(aim.getEyePosition());
		}
		Vec3 mouth = dragon.mouth();
		if (t == 0) {
			level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.4F, 1.5F);
		}
		if (t == NagaLiarEntity.BREATH_TELEGRAPH) {
			switch (dragon.kind()) {
				case AIR -> level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2.0F, 0.6F);
				case AWAN -> level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 2.0F, 0.7F);
				case PETIR -> level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.6F, 1.3F);
			}
			level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 1.2F, 0.8F);
		}
		int active = t - NagaLiarEntity.BREATH_TELEGRAPH;
		if (active >= 0 && active < NagaLiarEntity.BREATH_ACTIVE && active % 5 == 0) {
			Vec3 dir = aim != null ? aim.getBoundingBox().getCenter().subtract(mouth) : forward(dragon);
			dir = dir.lengthSqr() < 1.0E-4D ? forward(dragon) : dir.normalize();
			for (LivingEntity victim : inCone(level, dragon, mouth, dir, victims)) {
				breathHit(level, dragon, victim, pulse, victims);
			}
		}
		return active >= NagaLiarEntity.BREATH_ACTIVE;
	}

	private static void breathHit(ServerLevel level, NagaLiarEntity dragon, LivingEntity victim, float pulse, Predicate<LivingEntity> victims) {
		if (!hurt(level, dragon, victim, pulse)) {
			return;
		}
		NagaKind kind = dragon.kind();
		Vec3 at = victim.getBoundingBox().getCenter();
		switch (kind) {
			case AIR -> victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, false, true), dragon);
			case AWAN -> {
				push(victim, dragon.position(), 1.1D, 0.25D);
			}
			case PETIR -> {
				// the lightning jumps to two more within 6 blocks (particles only)
				List<LivingEntity> chain = level.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(6.0D),
						other -> other != victim && other != dragon && other.isAlive() && victims.test(other));
				chain.sort(Comparator.comparingDouble(other -> other.distanceToSqr(victim)));
				Vec3 from = at;
				for (int i = 0; i < Math.min(2, chain.size()); i++) {
					LivingEntity next = chain.get(i);
					Vec3 to = next.getBoundingBox().getCenter();
					Fx.beam(level, from, to, kind.glow, 0.12F);
					hurt(level, dragon, next, pulse * 0.6F);
					from = to;
				}
			}
		}
		Fx.burst(level, at, kind.glow, 5, 0.5F);
	}

	/** Living things in the breath cone (14 blocks, about 25 degrees), nearest first. */
	static List<LivingEntity> inCone(ServerLevel level, NagaLiarEntity dragon, Vec3 mouth, Vec3 dir, Predicate<LivingEntity> victims) {
		AABB box = new AABB(mouth, mouth).inflate(NagaLiarEntity.BREATH_LENGTH);
		List<LivingEntity> list = new ArrayList<>();
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != dragon && e.isAlive() && victims.test(e))) {
			Vec3 to = entity.getBoundingBox().getCenter().subtract(mouth);
			double distance = to.length();
			if (distance > NagaLiarEntity.BREATH_LENGTH || distance < 1.0E-3D) {
				continue;
			}
			double slack = Math.min(0.08D, entity.getBbWidth() / Math.max(1.0D, distance) * 0.5D);
			if (to.scale(1.0D / distance).dot(dir) >= NagaLiarEntity.BREATH_COS - slack) {
				list.add(entity);
			}
		}
		list.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(mouth)));
		return list;
	}

	static Vec3 forward(NagaLiarEntity dragon) {
		float yaw = dragon.getYRot() * Mth.DEG_TO_RAD;
		float pitch = dragon.getXRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
	}

	/** Throws {@code victim} away from {@code from} (knockback resistance applies), synced to its client. */
	static void push(LivingEntity victim, Vec3 from, double strength, double up) {
		Vec3 away = victim.position().subtract(from).multiply(1.0D, 0.0D, 1.0D);
		if (away.lengthSqr() < 1.0E-4D) {
			away = new Vec3(1.0D, 0.0D, 0.0D);
		}
		double resist = Mth.clamp(victim.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE), 0.0D, 1.0D);
		Vec3 kick = away.normalize().scale(strength * (1.0D - resist));
		victim.setDeltaMovement(victim.getDeltaMovement().add(kick.x, up * (1.0D - resist), kick.z));
		victim.needsSync = true;
	}

	/** The dragon hurts {@code victim} (a mob attack, so armour and the usual rules apply). */
	static boolean hurt(ServerLevel level, NagaLiarEntity dragon, LivingEntity victim, float amount) {
		if (!(amount > 0.0F) || !victim.isAlive()) {
			return false;
		}
		return victim.hurtServer(level, dragon.damageSources().mobAttack(dragon), amount);
	}

	// ================================================================== Ekor Sabet

	private static void tail(ServerLevel level, NagaLiarEntity dragon, LivingEntity target, int t) {
		dragon.orbit(target, 12.0D, 0.35D);
		Vec3 point = dragon.storedTailPoint();
		if (t < NagaLiarEntity.TAIL_TELEGRAPH) {
			if (t % 4 == 0) {
				Fx.ring(level, point, dragon.kind().glow, 6.0F);
			}
			return;
		}
		if (t == NagaLiarEntity.TAIL_TELEGRAPH) {
			level.playSound(null, point.x, point.y, point.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 2.5F, 0.6F);
			level.playSound(null, point.x, point.y, point.z, SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.HOSTILE, 1.5F, 0.8F);
			Fx.ring(level, point, dragon.kind().glow, 7.0F);
			Fx.burst(level, point, dragon.kind().color, 24, 3.0F);
			for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(6.0D, 4.0D, 6.0D),
					entity -> entity != dragon && (entity == target || wildVictim(entity)))) {
				if (victim.position().distanceToSqr(point) > 36.0D + 16.0D) {
					continue;
				}
				if (hurt(level, dragon, victim, (float) (TAIL * dragon.damageFactor))) {
					push(victim, point, 1.5D, 0.45D);
				}
			}
		}
		if (t >= NagaLiarEntity.TAIL_TELEGRAPH + 6) {
			dragon.finishSkill();
		}
	}

	// ================================================================== Raungan Naga

	private static void roar(ServerLevel level, NagaLiarEntity dragon, LivingEntity target, int t) {
		dragon.hover(dragon.position(), 0.0D);
		dragon.faceVelocity(target.getEyePosition());
		if (t == NagaLiarEntity.ROAR_TELEGRAPH) {
			level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 0.8F);
			Voice.boom(dragon, "boss.roar", 96.0D);
			Fx.ring(level, dragon.position(), dragon.kind().glow, 10.0F);
			for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, dragon.getBoundingBox().inflate(20.0D),
					NagaSkills::wildVictim)) {
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, false, true), dragon);
				player.sendOverlayMessage(Component.translatable("kultivator.naga.roar").withStyle(ChatFormatting.RED));
			}
			NagaPayloads.moment(level, NagaPayloads.MOMENT_ROAR, dragon.getId(), dragon.position(), dragon.kind().ordinal(), dragon.tier(), 2,
					dragon.getId(), null, 96.0D);
		}
		if (t >= NagaLiarEntity.ROAR_TELEGRAPH + 10) {
			dragon.finishSkill();
		}
	}

	// ================================================================== Lilitan

	private static void coil(ServerLevel level, NagaLiarEntity dragon, LivingEntity target, int t) {
		double radius = 7.0D - 4.0D * Math.min(1.0D, t / (double) NagaLiarEntity.COIL_TICKS);
		double angle = dragon.nextCoilAngle(0.14D);
		Vec3 point = target.position().add(Math.cos(angle) * radius, 1.0D + Math.sin(t * 0.2D) * 0.8D, Math.sin(angle) * radius);
		dragon.steer(point, 1.0D, 0.25D);
		if (t > 0 && t % 20 == 0 && dragon.distanceToSqr(target) < 5.5D * 5.5D) {
			if (hurt(level, dragon, target, (float) (COIL * dragon.damageFactor))) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2, false, true), dragon);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 1.2F, 1.4F);
			}
		}
		if (dragon.coilDamageTaken() >= dragon.getMaxHealth() * 0.05F) {
			// a hard enough blow breaks the coil and staggers it
			level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 1.5F, 1.2F);
			if (target instanceof ServerPlayer player) {
				player.sendOverlayMessage(Component.translatable("kultivator.naga.coil_broken").withStyle(ChatFormatting.AQUA));
			}
			dragon.finishSkill();
			return;
		}
		if (t >= NagaLiarEntity.COIL_TICKS) {
			dragon.finishSkill();
		}
	}

	// ================================================================== the bite

	private static void bite(ServerLevel level, NagaLiarEntity dragon, LivingEntity target, int t) {
		Vec3 eye = target.getEyePosition();
		dragon.steer(eye, 1.15D, 0.12D);
		if (dragon.position().add(0.0D, 0.6D, 0.0D).distanceToSqr(eye) < 2.8D * 2.8D) {
			if (hurt(level, dragon, target, (float) (BITE * dragon.damageFactor))) {
				push(target, dragon.position(), 0.9D, 0.2D);
			}
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 1.6F, 1.6F);
			// pull away after the bite
			dragon.setDeltaMovement(dragon.getDeltaMovement().scale(-0.4D).add(0.0D, 0.6D, 0.0D));
			dragon.finishSkill();
			return;
		}
		if (t >= NagaLiarEntity.BITE_TICKS) {
			dragon.finishSkill();
		}
	}

	private NagaSkills() {
	}
}
