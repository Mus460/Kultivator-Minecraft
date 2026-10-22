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
				if (breath(level, dragon, target, t, victims, (float) (BREATH_PULSE * dragon.power()))) {
					dragon.finishSkill();
				}
			}
			case NagaLiarEntity.SKILL_TAIL -> tail(level, dragon, target, t);
			case NagaLiarEntity.SKILL_ROAR -> roar(level, dragon, target, t);
			case NagaLiarEntity.SKILL_COIL -> coil(level, dragon, target, t);
			case NagaLiarEntity.SKILL_BITE -> bite(level, dragon, target, t);
			case NagaLiarEntity.SKILL_SPECIAL -> {
				Predicate<LivingEntity> victims = entity -> entity == target || wildVictim(entity);
				if (special(level, dragon, target, t, victims, dragon.power(), null)) {
					dragon.finishSkill();
				}
			}
			default -> dragon.finishSkill();
		}
	}

	// ================================================================== the kind skills (detail pass)

	/** Wild damage at Nascent Soul of the kind skills (× the power of the moment). */
	static final float WHIRL = 2.5F;
	static final float VEIL_BITE = 13.0F;
	static final float THUNDER = 9.0F;

	/**
	 * One tick of the kind skill at {@code aim}; {@code owner} is the tamer of a Naga Sejati (null for a wild Jiao, whose
	 * victims are players). True when it is over. Telegraphs first ({@link NagaLiarEntity#SPECIAL_TELEGRAPH}), entity
	 * effects only: velocity, damage, potion effects, particles and sounds; never a block or a LightningBolt.
	 */
	static boolean special(ServerLevel level, NagaLiarEntity dragon, LivingEntity aim, int t, Predicate<LivingEntity> victims, double power,
			@Nullable ServerPlayer owner) {
		return switch (dragon.kind()) {
			case AIR -> whirl(level, dragon, aim, t, victims, power, owner);
			case AWAN -> veil(level, dragon, aim, t, victims, power, owner);
			case PETIR -> thunder(level, dragon, aim, t, victims, power, owner);
		};
	}

	/**
	 * Pusaran Samudra: a ring of water marks the ground under the target for 1.5 s, then a whirl of radius 8 drags
	 * everything in it round and inward for 3.5 s, hitting every half second and slowing.
	 */
	private static boolean whirl(ServerLevel level, NagaLiarEntity dragon, LivingEntity aim, int t, Predicate<LivingEntity> victims, double power,
			@Nullable ServerPlayer owner) {
		Vec3 centre = dragon.specialPoint;
		int tele = NagaLiarEntity.SPECIAL_TELEGRAPH;
		double radius = NagaLiarEntity.WHIRL_RADIUS;
		if (owner == null) {
			// it circles the whirl, low and fast, as if stirring the sea
			double angle = t * 0.09D;
			dragon.steer(centre.add(Math.cos(angle) * 11.0D, 5.0D, Math.sin(angle) * 11.0D), 0.9D, 0.12D);
		}
		if (t == 0) {
			level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.HOSTILE, 2.0F, 0.6F);
			level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.6F, 1.3F);
			NagaPayloads.moment(level, NagaPayloads.MOMENT_WHIRL, dragon.getId(), centre, dragon.kind().ordinal(), (int) Math.round(radius * 10.0D),
					2, tele + NagaLiarEntity.WHIRL_TICKS, owner, 96.0D);
			warnNear(level, centre, radius + 4.0D, victims, "kultivator.naga.skill.whirl.warn");
		}
		if (t < tele) {
			if (t % 6 == 0) {
				Fx.ring(level, centre, dragon.kind().glow, (float) (radius * (1.0D - 0.25D * t / tele)));
			}
			return false;
		}
		int active = t - tele;
		if (active == 0) {
			level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 2.0F, 0.5F);
			level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.TRIDENT_RIPTIDE_2.value(), SoundSource.HOSTILE, 1.6F, 0.7F);
		}
		if (active % 8 == 0) {
			level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.HOSTILE, 1.2F,
					0.7F + 0.02F * active / 8.0F);
		}
		AABB box = new AABB(centre, centre).inflate(radius, 6.0D, radius);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != dragon && e.isAlive() && victims.test(e))) {
			Vec3 to = centre.subtract(victim.position()).multiply(1.0D, 0.0D, 1.0D);
			double distance = to.length();
			if (distance > radius) {
				continue;
			}
			double resist = Mth.clamp(victim.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE), 0.0D, 1.0D);
			Vec3 inward = distance < 0.6D ? Vec3.ZERO : to.scale(1.0D / distance);
			Vec3 round = new Vec3(-inward.z, 0.0D, inward.x);
			Vec3 drag = inward.scale(0.085D).add(round.scale(0.07D)).scale(1.0D - resist);
			victim.setDeltaMovement(victim.getDeltaMovement().add(drag.x, 0.0D, drag.z));
			victim.needsSync = true;
			if (active % 10 == 0 && hurt(level, dragon, victim, (float) (WHIRL * power))) {
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, true), dragon);
			}
		}
		return active >= NagaLiarEntity.WHIRL_TICKS;
	}

	/**
	 * Tirai Awan: a cloud billows out of the Jiao (1 s), blinds everyone in 14 blocks for 2.5 s, and the Jiao comes out
	 * of the mist behind its target with a bite. A tamed one instead blinds and slows its owner's foes and lends its owner
	 * the wind (Speed).
	 */
	private static boolean veil(ServerLevel level, NagaLiarEntity dragon, LivingEntity aim, int t, Predicate<LivingEntity> victims, double power,
			@Nullable ServerPlayer owner) {
		double radius = NagaLiarEntity.VEIL_RADIUS;
		if (t == 0) {
			dragon.specialPoint = dragon.position();
			level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.BREEZE_WHIRL, SoundSource.HOSTILE, 2.0F, 0.5F);
			level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 2.0F, 0.6F);
			NagaPayloads.moment(level, NagaPayloads.MOMENT_VEIL, dragon.getId(), dragon.position(), dragon.kind().ordinal(), (int) radius, 2,
					NagaLiarEntity.VEIL_STRIKE + 20, owner, 96.0D);
			warnNear(level, dragon.position(), radius + 6.0D, victims, "kultivator.naga.skill.veil.warn");
		}
		if (owner == null && t < NagaLiarEntity.VEIL_STRIKE) {
			dragon.hover(dragon.position(), 0.0D);
		}
		if (t == 20) {
			level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 2.0F, 0.6F);
			Vec3 centre = owner != null ? owner.position() : dragon.position();
			for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(radius),
					e -> e != dragon && e.isAlive() && victims.test(e) && e.distanceToSqr(centre) <= radius * radius)) {
				victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, true), dragon);
				if (owner != null) {
					victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, false, true), dragon);
				}
			}
			if (owner != null) {
				owner.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 1, false, true), dragon);
				return false;
			}
		}
		if (owner != null) {
			return t >= 30;
		}
		if (t == NagaLiarEntity.VEIL_STRIKE) {
			// out of the mist behind its target
			Vec3 look = aim.getViewVector(1.0F).multiply(1.0D, 0.0D, 1.0D);
			look = look.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : look.normalize();
			Vec3 behind = aim.position().subtract(look.scale(4.0D)).add(0.0D, 2.5D, 0.0D);
			Fx.burst(level, dragon.position(), 0xF4FFF8, 24, 3.0F);
			dragon.snapTo(behind.x, behind.y, behind.z, dragon.getYRot(), dragon.getXRot());
			dragon.setDeltaMovement(Vec3.ZERO);
			dragon.faceVelocity(aim.getEyePosition());
			level.playSound(null, behind.x, behind.y, behind.z, SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 2.0F, 0.6F);
			Fx.burst(level, behind, 0xF4FFF8, 24, 3.0F);
		}
		if (t > NagaLiarEntity.VEIL_STRIKE) {
			Vec3 eye = aim.getEyePosition();
			dragon.steer(eye, 1.2D, 0.2D);
			if (dragon.position().add(0.0D, 0.6D, 0.0D).distanceToSqr(eye) < 3.0D * 3.0D) {
				if (hurt(level, dragon, aim, (float) (VEIL_BITE * power))) {
					push(aim, dragon.position(), 1.0D, 0.3D);
				}
				level.playSound(null, aim.getX(), aim.getY(), aim.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 1.6F, 1.6F);
				dragon.setDeltaMovement(dragon.getDeltaMovement().scale(-0.4D).add(0.0D, 0.6D, 0.0D));
				return true;
			}
		}
		return t >= NagaLiarEntity.VEIL_STRIKE + 40;
	}

	/**
	 * Hujan Petir Langit: rings of light mark the target and the ground around it (or its owner's other foes) for 1.5 s,
	 * then bolts of light fall on them one after another, a quarter of a second apart. Each ring hits what stands in it.
	 * Particles and sounds only, never a LightningBolt.
	 */
	private static boolean thunder(ServerLevel level, NagaLiarEntity dragon, LivingEntity aim, int t, Predicate<LivingEntity> victims, double power,
			@Nullable ServerPlayer owner) {
		int tele = NagaLiarEntity.SPECIAL_TELEGRAPH;
		List<Vec3> marks = dragon.marks;
		if (t == 0) {
			marks.clear();
			marks.add(aim.position());
			int extra = owner != null ? 2 : dragon.hasFlag(NagaLiarEntity.FLAG_ENRAGED) ? 4 : 2;
			List<LivingEntity> others = level.getEntitiesOfClass(LivingEntity.class, aim.getBoundingBox().inflate(16.0D),
					e -> e != aim && e != dragon && e.isAlive() && victims.test(e));
			others.sort(Comparator.comparingDouble(other -> other.distanceToSqr(aim)));
			for (LivingEntity other : others) {
				if (marks.size() > extra) {
					break;
				}
				marks.add(other.position());
			}
			while (owner == null && marks.size() <= extra) {
				double angle = dragon.getRandom().nextDouble() * Math.PI * 2.0D;
				double r = 3.5D + dragon.getRandom().nextDouble() * 3.0D;
				marks.add(aim.position().add(Math.cos(angle) * r, 0.0D, Math.sin(angle) * r));
			}
			for (int i = 0; i < marks.size(); i++) {
				NagaPayloads.moment(level, NagaPayloads.MOMENT_THUNDER_MARK, dragon.getId(), marks.get(i), dragon.kind().ordinal(),
						(int) Math.round(NagaLiarEntity.MARK_RADIUS * 10.0D), 2, tele + i * NagaLiarEntity.MARK_STAGGER, owner, 96.0D);
			}
			level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.0F, 0.6F);
			level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.2F, 1.6F);
			warnNear(level, aim.position(), 20.0D, victims, "kultivator.naga.skill.thunder.warn");
		}
		if (owner == null) {
			// high above the target, head down, the storm gathering on its scales
			dragon.steer(aim.position().add(0.0D, 12.0D, 0.0D), 0.6D, 0.08D);
			dragon.faceVelocity(aim.position());
		}
		for (int i = 0; i < marks.size(); i++) {
			if (t == tele + i * NagaLiarEntity.MARK_STAGGER) {
				strikeMark(level, dragon, marks.get(i), victims, (float) (THUNDER * power));
			}
		}
		return t >= tele + Math.max(0, marks.size() - 1) * NagaLiarEntity.MARK_STAGGER + 12;
	}

	private static void strikeMark(ServerLevel level, NagaLiarEntity dragon, Vec3 mark, Predicate<LivingEntity> victims, float damage) {
		level.playSound(null, mark.x, mark.y, mark.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.6F, 1.0F);
		level.playSound(null, mark.x, mark.y, mark.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.2F, 1.3F);
		Fx.burst(level, mark.add(0.0D, 0.3D, 0.0D), dragon.kind().glow, 14, 1.8F);
		double r = NagaLiarEntity.MARK_RADIUS;
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(mark, mark).inflate(r, 4.0D, r),
				e -> e != dragon && e.isAlive() && victims.test(e))) {
			double dx = victim.getX() - mark.x;
			double dz = victim.getZ() - mark.z;
			if (dx * dx + dz * dz > r * r) {
				continue;
			}
			if (hurt(level, dragon, victim, damage)) {
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 3, false, true), dragon);
			}
		}
	}

	/** The players a skill threatens get its warning on the action bar (the telegraph in words). */
	private static void warnNear(ServerLevel level, Vec3 at, double radius, Predicate<LivingEntity> victims, String key) {
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(at, at).inflate(radius), victims::test)) {
			player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD));
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
				if (hurt(level, dragon, victim, (float) (TAIL * dragon.power()))) {
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
			if (hurt(level, dragon, target, (float) (COIL * dragon.power()))) {
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
			if (hurt(level, dragon, target, (float) (BITE * dragon.power()))) {
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
