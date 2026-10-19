package dev.kultivator.musuh;

import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.event.RivalSkills;
import dev.kultivator.util.NoGrief;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The rogues' skills (DESIGN 25.4.2), a pass every 10 ticks over the tracked set: the 6c rival qi line (every 6 s, all
 * tiers) and element burst (every 10 s, tier 2+) through the public {@link RivalSkills} primitives, the telegraphed mark
 * of tier 3+ (a closing ring, 1.5 s) and the Patriarch's Segel Darah (a 6-block ring for 2 s, then Wither I for 4 s and
 * 1.5 U). Skill damage is {@code U × (0.5 + 0.25 × tier)} with {@code U = 3 + 2R + 0.5 × stage}. Every hit is a mob attack
 * of the rogue on a target {@link RogueTargets#mayHit} accepts; entities only, never a block.
 */
final class RogueSkills {

	public static final int LINE_COOLDOWN = 120;
	public static final int BURST_COOLDOWN = 200;
	public static final int MARK_COOLDOWN = 180;
	public static final int SEAL_COOLDOWN = 280;
	public static final double MARK_RADIUS = 1.8D;
	public static final double MARK_RANGE = 16.0D;
	public static final double SEAL_RADIUS = 6.0D;
	public static final double SEAL_FACTOR = 1.5D;
	public static final int WITHER_TICKS = 80;

	/** A telegraphed strike waiting for its moment. */
	private record Shot(RogueCultivatorEntity mob, boolean seal, Vec3 centre, double radius, double damage, long fireAt) {
	}

	private static final List<Shot> SHOTS = new ArrayList<>();

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SHOTS.clear());
	}

	/** U_legacy of DESIGN 22.5.1 for the rogue's realm and stage. */
	static double legacy(RogueCultivatorEntity mob) {
		return 3.0D + 2.0D * mob.realm() + 0.5D * mob.stage();
	}

	/** The skill damage of DESIGN 25.4.2. */
	static double skillDamage(RogueCultivatorEntity mob) {
		return legacy(mob) * (0.5D + 0.25D * mob.tier());
	}

	/** Every server tick: telegraphs that are due; every 10 ticks the skill pass. */
	static void tick(MinecraftServer server, long tick) {
		if (!SHOTS.isEmpty()) {
			fireDue();
		}
		if (tick % 10L == 0L) {
			pass();
		}
	}

	private static void pass() {
		for (RogueCultivators.Tracked tracked : RogueCultivators.tracked()) {
			RogueCultivatorEntity mob = tracked.mob;
			if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
				continue;
			}
			LivingEntity target = mob.getTarget();
			if (target == null || !target.isAlive() || !RogueTargets.mayHit(mob, target)) {
				continue;
			}
			double distance = mob.distanceTo(target);
			if (distance > MARK_RANGE) {
				continue;
			}
			long now = level.getGameTime();
			int tier = mob.tier();
			int rgb = mob.element().color();
			double damage = skillDamage(mob);
			if (tier >= 4 && now >= tracked.nextSeal && distance <= SEAL_RADIUS + 2.0D) {
				tracked.nextSeal = now + SEAL_COOLDOWN;
				Vec3 at = mob.position();
				SHOTS.add(new Shot(mob, true, at, SEAL_RADIUS, legacy(mob) * SEAL_FACTOR, now + MusuhFxIds.BLOOD_TICKS));
				Fx.send(level, at.add(0.0D, 0.1D, 0.0D), FxType.MUSUH, MusuhFxIds.BLOOD_SEAL, 0x9A0E14, (float) SEAL_RADIUS);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.0F, 0.6F);
				continue;
			}
			if (tier >= 3 && now >= tracked.nextMark && mob.hasLineOfSight(target)) {
				tracked.nextMark = now + MARK_COOLDOWN;
				Vec3 at = target.position();
				SHOTS.add(new Shot(mob, false, at, MARK_RADIUS, damage, now + MusuhFxIds.MARK_TICKS));
				Fx.send(level, at.add(0.0D, 0.1D, 0.0D), FxType.MUSUH, MusuhFxIds.MARK, rgb, (float) MARK_RADIUS);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.HOSTILE, 0.8F, 1.2F);
				continue;
			}
			if (distance > RivalSkills.LINE_RANGE) {
				continue;
			}
			if (tier >= 2 && now >= tracked.nextBurst) {
				tracked.nextBurst = now + BURST_COOLDOWN;
				RivalSkills.elementBurst(level, mob, target, damage, rgb, each -> RogueTargets.mayHit(mob, each));
				continue;
			}
			if (now >= tracked.nextLine && mob.hasLineOfSight(target)) {
				tracked.nextLine = now + LINE_COOLDOWN;
				RivalSkills.qiLine(level, mob, target, damage, rgb, each -> RogueTargets.mayHit(mob, each));
			}
		}
	}

	private static void fireDue() {
		List<Shot> due = new ArrayList<>();
		Iterator<Shot> iterator = SHOTS.iterator();
		while (iterator.hasNext()) {
			Shot shot = iterator.next();
			RogueCultivatorEntity mob = shot.mob();
			if (mob.isRemoved() || !mob.isAlive()) {
				iterator.remove();
				continue;
			}
			if (mob.level().getGameTime() >= shot.fireAt()) {
				iterator.remove();
				due.add(shot);
			}
		}
		for (Shot shot : due) {
			fire(shot);
		}
	}

	private static void fire(Shot shot) {
		RogueCultivatorEntity mob = shot.mob();
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 at = shot.centre();
		double radius = shot.radius();
		if (shot.seal()) {
			Fx.send(level, at.add(0.0D, 0.1D, 0.0D), FxType.MUSUH, MusuhFxIds.BLOOD_HIT, 0x9A0E14, (float) radius);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 0.7F, 0.7F);
		} else {
			Fx.send(level, at.add(0.0D, 0.1D, 0.0D), FxType.MUSUH, MusuhFxIds.MARK_HIT, mob.element().color(), (float) radius);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.8F, 1.1F);
		}
		AABB box = new AABB(at, at).inflate(radius, 2.5D, radius);
		for (LivingEntity each : level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && horizontalSq(e.position(), at) <= radius * radius && RogueTargets.mayHit(mob, e))) {
			float amount = (float) shot.damage();
			if (!(amount > 0.0F) || !Float.isFinite(amount)) {
				continue;
			}
			if (!(each instanceof Player)) {
				each.addTag(NoGrief.TAG);
				each.setPortalCooldown();
			}
			if (each.hurtServer(level, level.damageSources().mobAttack(mob), amount) && shot.seal()) {
				each.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, 0), mob);
			}
		}
	}

	private static double horizontalSq(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	/** The cloud-step puff and its sound where a rogue leaves. */
	static void vanishFx(ServerLevel level, RogueCultivatorEntity mob) {
		Vec3 at = mob.position();
		Fx.send(level, at.add(0.0D, 1.0D, 0.0D), FxType.MUSUH, MusuhFxIds.VANISH, mob.element().color(), 1.0F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 0.8F, 0.8F);
	}

	private RogueSkills() {
	}
}
