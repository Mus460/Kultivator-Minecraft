package dev.kultivator.disciple.power;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.DiscipleTechniques;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.hall.Talents;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shared helpers of module C: the {@code extra.power} section, talent reads through module A's {@link Talents}, saved
 * cooldowns (absolute game time), the damage of the powers (exactly the rules of {@code DiscipleTechniques.hurt}: only
 * {@link DiscipleTechniques#mayHit} targets, the {@code damageFactor} hooks, {@link NoGrief#TAG}), the hit note that tells
 * the after-damage listener what kind of blow landed, and FX / sound / chat shortcuts.
 */
final class PowerCore {

	/** Kinds of a disciple's blow, noted right before it lands. */
	static final int HIT_MELEE = 0;
	static final int HIT_TECHNIQUE = 1;
	static final int HIT_ULTIMATE = 2;
	/** Auras, petals, clouds: no on-hit effects (and never counted), so nothing chains. */
	static final int HIT_AREA = 3;

	/** Ultimates are never centred this close to the master or a villager (DESIGN 20.3.16). */
	static final double SAFE_CENTRE = 4.0D;

	private static @Nullable DiscipleEntity noteEntity;
	private static int noteKind;

	// ================================================================== data

	/** Module C's section for reading (an empty, unattached tag when missing). */
	static CompoundTag tag(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(DisciplePowers.EXTRA);
	}

	/** Module C's section for writing (created when missing). */
	static CompoundTag edit(DiscipleData data) {
		return data.extraSection(DisciplePowers.EXTRA);
	}

	/** A sub-compound of the section for writing (created when missing). */
	static CompoundTag editSub(DiscipleData data, String key) {
		CompoundTag section = edit(data);
		CompoundTag sub = section.getCompound(key).orElse(null);
		if (sub == null) {
			sub = new CompoundTag();
			section.put(key, sub);
		}
		return sub;
	}

	static int physique(DiscipleData data) {
		return Talents.physique(data);
	}

	static boolean talent(DiscipleData data, int bit) {
		return (Talents.talents(data) & bit) != 0;
	}

	static int special(DiscipleData data) {
		return Talents.special(data);
	}

	static int colour(DiscipleData data) {
		return Math.max(0, Math.min(6, Talents.soulColor(data)));
	}

	/** True for the bodies the powers work in: a disciple and its Divine Clones. */
	static boolean powered(DiscipleEntity entity) {
		DiscipleEntity.Role role = entity.role();
		return role == DiscipleEntity.Role.DISCIPLE || role == DiscipleEntity.Role.CLONE;
	}

	static boolean isDisciple(DiscipleEntity entity) {
		return entity.role() == DiscipleEntity.Role.DISCIPLE;
	}

	// ================================================================== time and cooldowns

	static long now(Entity entity) {
		return entity.level().getGameTime();
	}

	/** The in-game day index (overworld clock / 24000). */
	static long day(ServerLevel level) {
		return level.getServer().overworld().getOverworldClockTime() / 24000L;
	}

	/** Ticks until cooldown {@code key} is over (0 when ready). */
	static long cooldownLeft(DiscipleData data, String key, long now) {
		return Math.max(0L, tag(data).getCompoundOrEmpty("cd").getLongOr(key, 0L) - now);
	}

	static boolean ready(DiscipleData data, String key, long now) {
		return cooldownLeft(data, key, now) <= 0L;
	}

	static void setCooldown(DiscipleData data, String key, long until) {
		editSub(data, "cd").putLong(key, until);
	}

	// ================================================================== targets and damage

	/** The Wither, the Ender Dragon, the Warden and the Elder Guardian. */
	static boolean isBoss(@Nullable Entity entity) {
		return entity != null && (entity.getType() == EntityTypes.ENDER_DRAGON || entity.getType() == EntityTypes.WITHER
				|| entity.getType() == EntityTypes.WARDEN || entity.getType() == EntityTypes.ELDER_GUARDIAN);
	}

	/** What the disciple may hit around {@code centre} (DiscipleTechniques.mayHit), nearest first. */
	static List<LivingEntity> hostiles(DiscipleEntity entity, ServerPlayer master, Vec3 centre, double radius) {
		return DiscipleTechniques.hostilesAround(entity, master, centre, radius);
	}

	/** True when a boss the disciple may hit is within {@code radius}. */
	static boolean bossNear(DiscipleEntity entity, ServerPlayer master, double radius) {
		for (LivingEntity each : hostiles(entity, master, entity.position(), radius)) {
			if (isBoss(each)) {
				return true;
			}
		}
		return false;
	}

	/** False when the master or a villager stands within 4 blocks of {@code centre} (read-only entity query). */
	static boolean safeCentre(ServerLevel level, @Nullable ServerPlayer master, Vec3 centre) {
		if (master != null && master.level() == level && master.position().distanceToSqr(centre) < SAFE_CENTRE * SAFE_CENTRE) {
			return false;
		}
		AABB box = new AABB(centre, centre).inflate(SAFE_CENTRE);
		return level.getEntitiesOfClass(AbstractVillager.class, box,
				villager -> villager.position().distanceToSqr(centre) < SAFE_CENTRE * SAFE_CENTRE).isEmpty();
	}

	/**
	 * Damage of a power, with the rules of {@code DiscipleTechniques.hurt}: the {@code damageFactor} hooks (stars, folds,
	 * flames...), only targets {@link DiscipleTechniques#mayHit} accepts, {@link NoGrief#TAG} and a portal cooldown on the
	 * target, a mob attack of the disciple (kills are its own). True when it landed.
	 */
	static boolean hurt(DiscipleEntity entity, @Nullable ServerPlayer master, LivingEntity target, double amount, int kind) {
		if (!(entity.level() instanceof ServerLevel level) || !DiscipleTechniques.mayHit(entity, master, target)) {
			return false;
		}
		float factored = (float) (amount * DiscipleHooks.damageFactor(entity, target, kind != HIT_MELEE));
		if (!(factored > 0.0F) || !Float.isFinite(factored)) {
			return false;
		}
		// The one damage function of DESIGN 22.5.5 (third choke point); a finished pre-6c amount passes it unchanged.
		float damage = dev.kultivator.technique.spec.TechniquePower.finalDamage(
				dev.kultivator.technique.spec.DamageContext.legacy(entity, target, factored));
		if (!(damage > 0.0F)) {
			return false;
		}
		if (target.damageCooldownTime > 10) {
			target.damageCooldownTime = 10;
		}
		if (!(target instanceof net.minecraft.world.entity.player.Player)) {
			// Never on a player (the master in a duel): a saved tag would keep him from portals and plates.
			target.addTag(NoGrief.TAG);
			target.setPortalCooldown();
		}
		// Noted after the damageFactor hooks (their note provider marks every hit as melee/technique).
		note(entity, kind);
		DamageSource source = level.damageSources().mobAttack(entity);
		boolean landed = target.hurtServer(level, source, damage);
		if (noteEntity == entity) {
			noteEntity = null;
		}
		if (landed) {
			entity.setLastHurtMob(target);
		}
		return landed;
	}

	/** Remembers what kind of blow {@code entity} is about to land (read once by the after-damage listener). */
	static void note(DiscipleEntity entity, int kind) {
		noteEntity = entity;
		noteKind = kind;
	}

	/** The kind of the blow {@code attacker} noted (and forgets it), or -1 when it noted none. */
	static int takeNote(@Nullable Entity attacker) {
		if (attacker == null || attacker != noteEntity) {
			return -1;
		}
		noteEntity = null;
		return noteKind;
	}

	/** Pushes a monster (never a player, the dragon or a ravager), with the block-safety tag. */
	static void push(LivingEntity target, Vec3 velocity) {
		if (target instanceof net.minecraft.world.entity.player.Player
				|| target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
				|| target instanceof net.minecraft.world.entity.monster.Ravager) {
			return;
		}
		target.addTag(NoGrief.TAG);
		target.setPortalCooldown();
		target.setDeltaMovement(velocity);
		target.needsSync = true;
		target.syncVelocity = true;
	}

	// ================================================================== effects and text

	static void fx(ServerLevel level, Vec3 pos, int sub, int b, float f) {
		Fx.send(level, pos, pos, FxType.DISCIPLE_POWER, sub, b, f);
	}

	static void fx(ServerLevel level, Vec3 pos, Vec3 target, int sub, int b, float f) {
		Fx.send(level, pos, target, FxType.DISCIPLE_POWER, sub, b, f);
	}

	static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, volume, pitch);
	}

	/** The disciple's name in the parchment colour of the phase-5 messages. */
	static Component name(DiscipleEntity entity) {
		return entity.getName().copy().withColor(0xF2E6C8);
	}

	/** A chat line to the disciple's master when online. */
	static void tell(DiscipleEntity entity, Component message) {
		ServerPlayer master = entity.owner();
		if (master != null) {
			Msg.chat(master, message);
		}
	}

	static MutableComponent key(String key, Object... args) {
		return Component.translatable(DisciplePowers.PREFIX + key, args);
	}

	private PowerCore() {
	}
}
