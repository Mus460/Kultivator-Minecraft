package dev.kultivator.purba;

import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The phase scripts of the three primordials (DESIGN 26.4.5), run every tick by their {@link PrimordialEvents.Fight}:
 * <ul>
 * <li><b>Kun-Peng</b> (Zhuangzi ch. 1; Perfect World ch. 261, 347): Kun swims a loop below the island edge and surfaces
 * every 8 s with Gelombang Tiga Ribu Li (ring 2 s, 18% + knockback; Celah 4 s); Peng casts wing shadows (Darkness +
 * Slowness II) and pounces along a telegraphed line (25%; Celah 3 s after each dive); then the whirlwind pulls players
 * in, wind blades cut 8% every 3 s and three Mata Angin orbit (breaking one opens 6 s and drops the beast 20 blocks);</li>
 * <li><b>Zhulong</b> (Shan Hai Jing 8, 17): the open-eyed gaze cone (6%/s + entity fire; behind the head is safe; Celah
 * 5 s), the closed-eyed night (vanilla Darkness, client sky only, no time change; strikes from the dark at 20% with a
 * candle flare; the Lilin opens 4 s), then the breath of the seasons (winter: Slowness III and frost, 10%; summer: entity
 * fire, 12%; Celah 3 s between);</li>
 * <li><b>Taotie</b> (Zuo Zhuan; Lüshi Chunqiu 16/3a): six Pelahap walk to the head and are eaten (+5%; Celah while none
 * lives); the 3 s inhale through a 30° cone, devouring after 2 s in the mouth (30%, then spat out 12 blocks); then the
 * open / closed mouth of 5 s (technique damage while open heals it, Celah while closed).</li>
 * </ul>
 * Every hit is a share of the target's max health through {@link PurbaHits}; pulls and knockbacks are velocities; fire is
 * entity fire; nothing touches a block.
 */
final class PrimordialKinds {

	public static final double KUN_ORBIT = 20.0D;
	public static final double KUN_DEPTH = 5.0D;
	public static final int KUN_PERIOD = 160;
	public static final int KUN_CELAH = 80;
	public static final double WAVE_RADIUS = 10.0D;
	public static final double WAVE_SHARE = 0.18D;
	public static final double WAVE_KNOCKBACK = 1.5D;
	public static final double PENG_HEIGHT = 14.0D;
	public static final double PENG_ORBIT = 16.0D;
	public static final int WING_PERIOD = 120;
	public static final double WING_RADIUS = 12.0D;
	public static final int POUNCE_PERIOD = 140;
	public static final double POUNCE_SHARE = 0.25D;
	public static final double POUNCE_WIDTH = 2.5D;
	public static final int POUNCE_CELAH = 60;
	public static final double WHIRL_RADIUS = 32.0D;
	public static final double WHIRL_PULL = 0.07D;
	public static final int BLADE_PERIOD = 60;
	public static final double BLADE_SHARE = 0.08D;
	public static final double WIND_EYE_HEALTH = 0.06D;
	public static final double WIND_EYE_ORBIT = 10.0D;
	public static final int WIND_EYE_CELAH = 120;
	public static final double HIGH = 22.0D;
	public static final int WIND_EYE_RESPAWN = 300;
	public static final double HEAD_HEIGHT = 3.0D;
	public static final int GAZE_TELEGRAPH = 40;
	public static final int GAZE_TICKS = 120;
	public static final int GAZE_CELAH = 100;
	public static final double GAZE_RANGE = 40.0D;
	public static final double GAZE_HALF_ANGLE = 30.0D;
	public static final double GAZE_SHARE = 0.06D;
	public static final double NIGHT_RANGE = 64.0D;
	public static final int STRIKE_PERIOD = 100;
	public static final double STRIKE_SHARE = 0.20D;
	public static final int LILIN_CELAH = 80;
	public static final int LILIN_COOLDOWN = 160;
	public static final int BREATH_PERIOD = 120;
	public static final double BREATH_RADIUS = 32.0D;
	public static final double COLD_SHARE = 0.10D;
	public static final double HOT_SHARE = 0.12D;
	public static final int BREATH_CELAH = 60;
	public static final double TAOTIE_HEIGHT = 3.5D;
	public static final int PELAHAP_COUNT = 6;
	public static final double PELAHAP_HEAL = 0.05D;
	public static final int PELAHAP_RESPAWN = 160;
	public static final int INHALE_PERIOD = 160;
	public static final int INHALE_TICKS = 60;
	public static final double INHALE_RANGE = 24.0D;
	public static final double INHALE_HALF_ANGLE = 15.0D;
	public static final double INHALE_PULL = 0.12D;
	public static final double MOUTH_ZONE = 4.5D;
	public static final int DEVOUR_TICKS = 40;
	public static final double DEVOUR_SHARE = 0.30D;
	public static final int INHALE_CELAH = 80;
	public static final int MOUTH_PERIOD = 100;

	private static final int P_WAVE = 0;
	private static final int P_POUNCE = 1;
	private static final int P_STRIKE = 2;
	private static final int P_BREATH_COLD = 3;
	private static final int P_BREATH_HOT = 4;

	/** A telegraphed skill waiting for its moment. */
	record Pending(int type, Vec3 a, Vec3 b, double radius, long at) {
	}

	/** The script clocks of one fight (reset on every phase). */
	static final class State {
		long nextA;
		long nextB;
		long nextC;
		long subUntil;
		int sub;
		double angle;
		float lockedYaw;
		@Nullable Vec3 goal;
		boolean coldNext;
		boolean mouthOpen;
		long lilinCooldown;
		long relicRespawn;
		final Map<UUID, Integer> mouth = new HashMap<>();
		final List<Pending> pending = new ArrayList<>();

		void reset(long now) {
			nextA = now + 60L;
			nextB = now + 100L;
			nextC = now + 40L;
			subUntil = 0L;
			sub = 0;
			goal = null;
			relicRespawn = 0L;
			mouth.clear();
			pending.clear();
		}
	}

	// ================================================================== positions

	static Vec3 startPosition(PrimordialEvents.Fight f) {
		return switch (f.kind) {
			case PrimordialBeastEntity.KUNPENG -> f.center.add(KUN_ORBIT, -KUN_DEPTH, 0.0D);
			case PrimordialBeastEntity.ZHULONG -> f.center.add(0.0D, HEAD_HEIGHT, 0.0D);
			default -> f.center.add(0.0D, TAOTIE_HEIGHT, 0.0D);
		};
	}

	private static void moveToward(PrimordialBeastEntity mob, Vec3 goal, double speed) {
		Vec3 pos = mob.position();
		Vec3 delta = goal.subtract(pos);
		double length = delta.length();
		Vec3 next = length <= speed ? goal : pos.add(delta.scale(speed / length));
		mob.setPos(next.x, next.y, next.z);
		if (length > 0.05D && Math.abs(delta.x) + Math.abs(delta.z) > 0.01D) {
			face(mob, delta);
		}
	}

	private static void face(Mob mob, Vec3 direction) {
		float yaw = (float) (Mth.atan2(direction.z, direction.x) * (180.0D / Math.PI)) - 90.0F;
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.yBodyRot = yaw;
	}

	private static Vec3 yawVector(float yaw) {
		double radians = Math.toRadians(yaw + 90.0F);
		return new Vec3(Math.cos(radians), 0.0D, Math.sin(radians));
	}

	/** During the 3 s transition the beast holds still (relics keep orbiting). */
	static void hover(PrimordialEvents.Fight f, PrimordialBeastEntity mob) {
		mob.setDeltaMovement(Vec3.ZERO);
		orbitRelics(f);
	}

	// ================================================================== targets

	private static List<LivingEntity> targets(PrimordialEvents.Fight f, PrimordialBeastEntity mob, Vec3 centre, double radius, double height) {
		return PurbaHits.around(f.level, mob, centre, radius, height, e -> !(e instanceof ServerPlayer player && f.isOut(player)));
	}

	private static @Nullable ServerPlayer randomFighter(PrimordialEvents.Fight f, PrimordialBeastEntity mob, double range) {
		List<ServerPlayer> list = new ArrayList<>();
		for (ServerPlayer player : f.fighters()) {
			if (player.distanceToSqr(mob) <= range * range && PurbaHits.mayHit(mob, player)) {
				list.add(player);
			}
		}
		return list.isEmpty() ? null : list.get(f.level.getRandom().nextInt(list.size()));
	}

	private static @Nullable ServerPlayer nearestFighter(PrimordialEvents.Fight f, PrimordialBeastEntity mob) {
		ServerPlayer best = null;
		double bestSq = Double.MAX_VALUE;
		for (ServerPlayer player : f.fighters()) {
			double sq = player.distanceToSqr(mob);
			if (sq < bestSq && PurbaHits.mayHit(mob, player)) {
				bestSq = sq;
				best = player;
			}
		}
		return best;
	}

	private static void ring(PrimordialEvents.Fight f, Vec3 centre, double radius, int ticks, int rgb) {
		Fx.send(f.level, centre.add(0.0D, 0.1D, 0.0D), new Vec3(ticks, 0.0D, 0.0D), FxType.PURBA, PurbaFxIds.RING, rgb, (float) radius);
	}

	private static void sound(PrimordialEvents.Fight f, Vec3 at, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		f.level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
	}

	// ================================================================== phases

	static void enterPhase(PrimordialEvents.Fight f, int phase) {
		PrimordialBeastEntity mob = f.beast;
		if (mob == null) {
			return;
		}
		long now = f.now();
		State s = f.script;
		s.reset(now + PrimordialEvents.TRANSITION_TICKS);
		mob.setFlag(PrimordialBeastEntity.FLAG_HIDDEN, false);
		mob.setFlag(PrimordialBeastEntity.FLAG_MOUTH, false);
		switch (f.kind) {
			case PrimordialBeastEntity.KUNPENG -> {
				if (phase == 2) {
					spawnWindEyes(f, mob);
				}
			}
			case PrimordialBeastEntity.ZHULONG -> {
				if (phase == 1) {
					mob.setFlag(PrimordialBeastEntity.FLAG_HIDDEN, true);
					Fx.send(f.level, mob.position(), FxType.PURBA, PurbaFxIds.NIGHT, 1, 0.0F);
					spawnLilin(f);
				}
			}
			default -> {
				if (phase == 0) {
					spawnPelahap(f, PELAHAP_COUNT);
				}
			}
		}
	}

	static void leavePhase(PrimordialEvents.Fight f, int phase) {
		PrimordialBeastEntity mob = f.beast;
		if (f.kind == PrimordialBeastEntity.ZHULONG && phase == 1 && mob != null) {
			Fx.send(f.level, mob.position(), FxType.PURBA, PurbaFxIds.NIGHT, 0, 0.0F);
		}
		for (PrimordialRelicEntity relic : f.relics) {
			relic.fight = null;
			if (!relic.isRemoved()) {
				relic.discard();
			}
		}
		f.relics.clear();
		if (f.kind == PrimordialBeastEntity.TAOTIE && phase == 0) {
			for (Mob each : f.pelahap) {
				if (!each.isRemoved()) {
					each.discard();
				}
			}
			f.pelahap.clear();
		}
		if (mob != null) {
			mob.setFlag(PrimordialBeastEntity.FLAG_HIDDEN, false);
			mob.setFlag(PrimordialBeastEntity.FLAG_MOUTH, false);
			mob.setFlag(PrimordialBeastEntity.FLAG_GLOW, false);
		}
	}

	static void tick(PrimordialEvents.Fight f, PrimordialBeastEntity mob, long now) {
		firePending(f, mob, now);
		switch (f.kind) {
			case PrimordialBeastEntity.KUNPENG -> {
				switch (mob.phase()) {
					case 0 -> kun(f, mob, now);
					case 1 -> peng(f, mob, now);
					default -> whirlwind(f, mob, now);
				}
			}
			case PrimordialBeastEntity.ZHULONG -> {
				switch (mob.phase()) {
					case 0 -> gaze(f, mob, now);
					case 1 -> night(f, mob, now);
					default -> seasons(f, mob, now);
				}
			}
			default -> {
				switch (mob.phase()) {
					case 0 -> headless(f, mob, now);
					case 1 -> inhale(f, mob, now);
					default -> gluttony(f, mob, now);
				}
			}
		}
	}

	// ================================================================== Kun-Peng

	private static void kun(PrimordialEvents.Fight f, PrimordialBeastEntity mob, long now) {
		State s = f.script;
		if (s.sub == 0) {
			s.angle += 0.02D;
			Vec3 goal = f.center.add(Math.cos(s.angle) * KUN_ORBIT, -KUN_DEPTH, Math.sin(s.angle) * KUN_ORBIT);
			moveToward(mob, goal, 0.6D);
			if (now >= s.nextA) {
				// Gelombang Tiga Ribu Li: it surfaces, the ring is telegraphed for 2 s, the Celah lasts 4 s.
				s.sub = 1;
				s.nextA = now + KUN_PERIOD;
				s.subUntil = now + KUN_CELAH;
				s.goal = new Vec3(mob.getX(), f.center.y + 1.5D, mob.getZ());
				Vec3 ground = new Vec3(mob.getX(), f.center.y, mob.getZ());
				ring(f, ground, WAVE_RADIUS, PurbaFxIds.WAVE_TICKS, 0x4A8BE8);
				s.pending.add(new Pending(P_WAVE, ground, ground, WAVE_RADIUS, now + PurbaFxIds.WAVE_TICKS));
				mob.setFlag(PrimordialBeastEntity.FLAG_GLOW, true);
				f.openCelah(KUN_CELAH);
				sound(f, ground, SoundEvents.ELDER_GUARDIAN_AMBIENT, 3.0F, 0.5F);
			}
		} else {
			if (s.goal != null) {
				moveToward(mob, s.goal, 0.5D);
			}
			if (now >= s.subUntil) {
				s.sub = 0;
				mob.setFlag(PrimordialBeastEntity.FLAG_GLOW, false);
			}
		}
	}

	private static void peng(PrimordialEvents.Fight f, PrimordialBeastEntity mob, long now) {
		State s = f.script;
		switch (s.sub) {
			case 0 -> {
				s.angle += 0.03D;
				Vec3 goal = f.center.add(Math.cos(s.angle) * PENG_ORBIT, PENG_HEIGHT, Math.sin(s.angle) * PENG_ORBIT);
				moveToward(mob, goal, 0.8D);
				if (now >= s.nextA) {
					// Sayap Bagai Awan: the wing shadow darkens and slows whoever stands under it.
					s.nextA = now + WING_PERIOD;
					Vec3 ground = new Vec3(mob.getX(), f.center.y, mob.getZ());
					Fx.send(f.level, ground, FxType.PURBA, PurbaFxIds.WING_SHADOW, 0, (float) WING_RADIUS);
					sound(f, mob.position(), SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.6F);
					for (LivingEntity each : targets(f, mob, ground, WING_RADIUS, 16.0D)) {
						each.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), mob);
						each.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), mob);
					}
				} else if (now >= s.nextB) {
					ServerPlayer target = randomFighter(f, mob, 40.0D);
					s.nextB = now + POUNCE_PERIOD;
					if (target != null) {
						// Terkam: a line telegraphed 1.5 s on a marked player.
						Vec3 from = mob.position().add(0.0D, 1.0D, 0.0D);
						Vec3 aim = target.position().add(0.0D, 0.6D, 0.0D);
						Vec3 to = aim.add(PurbaHits.flatDirection(from, aim).scale(6.0D));
						Fx.send(f.level, from, to, FxType.PURBA, PurbaFxIds.LINE, 0x9AD8FF, PurbaFxIds.POUNCE_TICKS);
						s.pending.add(new Pending(P_POUNCE, from, to, POUNCE_WIDTH, now + PurbaFxIds.POUNCE_TICKS));
						s.sub = 1;
						s.goal = to;
						mob.setFlag(PrimordialBeastEntity.FLAG_GLOW, true);
						Msg.actionBar(target, Component.translatable("kultivator.purba.warn.pounce").withStyle(ChatFormatting.RED));
						sound(f, from, SoundEvents.PHANTOM_SWOOP, 3.0F, 0.6F);
					}
				}
			}
			case 1 -> mob.setDeltaMovement(Vec3.ZERO);
			case 2 -> {
				if (s.goal != null) {
					moveToward(mob, s.goal, 3.0D);
					if (mob.position().distanceToSqr(s.goal) < 1.0D) {
						s.sub = 3;
						s.subUntil = now + POUNCE_CELAH;
						f.openCelah(POUNCE_CELAH);
					}
				} else {
					s.sub = 0;
				}
			}
			default -> {
				if (now >= s.subUntil) {
					s.sub = 0;
				}
			}
		}
	}

	private static void whirlwind(PrimordialEvents.Fight f, PrimordialBeastEntity mob, long now) {
		State s = f.script;
		s.angle += 0.015D;
		double height = f.celah() ? 3.0D : HIGH;
		Vec3 goal = f.center.add(Math.cos(s.angle) * 4.0D, height, Math.sin(s.angle) * 4.0D);
		moveToward(mob, goal, f.celah() ? 1.5D : 0.6D);
		orbitRelics(f);
		if (now % 2L == 0L) {
			// Angin Puyuh: a velocity toward the centre (the void catch of Alam Abadi still applies).
			for (ServerPlayer player : f.fighters()) {
				double sq = PurbaHits.horizontalSq(player.position(), f.center);
				if (sq > WHIRL_RADIUS * WHIRL_RADIUS || sq < 9.0D || player.isCreative()) {
					continue;
				}
				Vec3 pull = PurbaHits.flatDirection(player.position(), f.center).scale(WHIRL_PULL);
				PurbaHits.push(player, pull);
			}
		}
		if (now % 20L == 0L) {
			Fx.send(f.level, f.center, FxType.PURBA, PurbaFxIds.WHIRLWIND, 0, (float) WHIRL_RADIUS);
		}
		if (now >= s.nextA) {
			s.nextA = now + BLADE_PERIOD;
			sound(f, f.center, SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.5F);
			for (LivingEntity each : targets(f, mob, f.center, WHIRL_RADIUS, 24.0D)) {
				Fx.send(f.level, each.position().add(0.0D, 1.0D, 0.0D), FxType.PURBA, PurbaFxIds.HIT, 0xC8F0FF, 1.0F);
				PurbaHits.share(f.level, mob, each, BLADE_SHARE);
			}
		}
		if (f.relics.isEmpty() && s.relicRespawn > 0L && now >= s.relicRespawn) {
			s.relicRespawn = 0L;
			spawnWindEyes(f, mob);
		}
	}

	private static void spawnWindEyes(PrimordialEvents.Fight f, PrimordialBeastEntity mob) {
		for (int i = 0; i < 3; i++) {
			PrimordialRelicEntity relic = PurbaEntities.PRIMORDIAL_RELIC.create(f.level, EntitySpawnReason.EVENT);
			if (relic == null) {
				continue;
			}
			double angle = Math.PI * 2.0D * i / 3.0D;
			relic.setKind(PrimordialRelicEntity.MATA_ANGIN);
			relic.snapTo(f.center.x + Math.cos(angle) * WIND_EYE_ORBIT, f.center.y + 2.5D, f.center.z + Math.sin(angle) * WIND_EYE_ORBIT, 0.0F, 0.0F);
			AttributeInstance max = relic.getAttribute(Attributes.MAX_HEALTH);
			if (max != null) {
				max.setBaseValue(Math.max(20.0D, mob.getMaxHealth() * WIND_EYE_HEALTH));
			}
			relic.setHealth(relic.getMaxHealth());
			relic.fight = f;
			if (f.level.addFreshEntity(relic)) {
				f.relics.add(relic);
			}
		}
	}

	private static void orbitRelics(PrimordialEvents.Fight f) {
		if (f.relics.isEmpty()) {
			return;
		}
		long now = f.now();
		int index = 0;
		for (PrimordialRelicEntity relic : f.relics) {
			if (relic.isRemoved() || relic.kind() != PrimordialRelicEntity.MATA_ANGIN) {
				index++;
				continue;
			}
			double angle = now * 0.02D + Math.PI * 2.0D * index / 3.0D;
			relic.setPos(f.center.x + Math.cos(angle) * WIND_EYE_ORBIT, f.center.y + 2.5D, f.center.z + Math.sin(angle) * WIND_EYE_ORBIT);
			index++;
		}
	}

	/** A Mata Angin broke: a 6 s Celah and the beast drops 20 blocks. */
	static void windEyeBroken(PrimordialEvents.Fight f, PrimordialRelicEntity relic) {
		f.relics.remove(relic);
		relic.fight = null;
		f.openCelah(WIND_EYE_CELAH);
		sound(f, relic.position(), SoundEvents.GLASS_BREAK, 2.0F, 0.6F);
		Fx.send(f.level, relic.position(), FxType.PURBA, PurbaFxIds.HIT, 0x8EF0E0, 3.0F);
		for (ServerPlayer player : f.fighters()) {
			Msg.actionBar(player, Component.translatable("kultivator.purba.celah.wind_eye").withStyle(ChatFormatting.AQUA));
		}
		boolean any = false;
		for (PrimordialRelicEntity each : f.relics) {
			if (!each.isRemoved() && each.isAlive()) {
				any = true;
				break;
			}
		}
		if (!any) {
			f.relics.clear();
			f.script.relicRespawn = f.now() + WIND_EYE_RESPAWN;
		}
	}

	// ================================================================== Zhulong

	private static Vec3 headSpot(PrimordialEvents.Fight f, long now) {
		double drift = now * 0.01D;
		return f.center.add(Math.cos(drift) * 6.0D, HEAD_HEIGHT + Math.sin(now * 0.05D) * 0.5D, Math.sin(drift) * 6.0D);
	}

	private static void gaze(PrimordialEvents.Fight f, PrimordialBeastEntity mob, long now) {
		State s = f.script;
		moveToward(mob, headSpot(f, now), 0.3D);
		ServerPlayer nearest = nearestFighter(f, mob);
		switch (s.sub) {
			case 0 -> {
				if (nearest != null) {
					face(mob, nearest.position().subtract(mob.position()));
				}
				if (now >= s.nextA && nearest != null) {
					s.sub = 1;
					s.subUntil = now + GAZE_TELEGRAPH;
					s.lockedYaw = mob.getYRot();
					mob.setFlag(PrimordialBeastEntity.FLAG_GLOW, true);
					for (ServerPlayer player : f.fighters()) {
						Msg.actionBar(player, Component.translatable("kultivator.purba.warn.gaze").withStyle(ChatFormatting.GOLD));
					}
					sound(f, mob.position(), SoundEvents.BEACON_ACTIVATE, 3.0F, 0.6F);
				}
			}
			case 1 -> {
				lockYaw(mob, s.lockedYaw);
				if (now >= s.subUntil) {
					s.sub = 2;
					s.subUntil = now + GAZE_TICKS;
				}
			}
			case 2 -> {
				lockYaw(mob, s.lockedYaw);
				Vec3 head = mob.position().add(0.0D, mob.getBbHeight() * 0.6D, 0.0D);
				Vec3 dir = yawVector(s.lockedYaw);
				if (now % 10L == 0L) {
					Fx.send(f.level, head, head.add(dir.scale(GAZE_RANGE)), FxType.PURBA, PurbaFxIds.GAZE, 0xFFC040, (float) GAZE_HALF_ANGLE);
				}
				if (now % 20L == 0L) {
					double cos = Math.cos(Math.toRadians(GAZE_HALF_ANGLE));
					for (LivingEntity each : targets(f, mob, head, GAZE_RANGE, 20.0D)) {
						Vec3 to = PurbaHits.flatDirection(head, each.position());
						if (to.dot(dir) >= cos && PurbaHits.share(f.level, mob, each, GAZE_SHARE)) {
							each.setRemainingFireTicks(Math.max(each.getRemainingFireTicks(), 60));
						}
					}
				}
				if (now >= s.subUntil) {
					s.sub = 3;
					s.subUntil = now + GAZE_CELAH;
					mob.setFlag(PrimordialBeastEntity.FLAG_GLOW, false);
					f.openCelah(GAZE_CELAH);
				}
			}
			default -> {
				if (now >= s.subUntil) {
					s.sub = 0;
					s.nextA = now + 40L;
				}
			}
		}
	}

	private static void lockYaw(PrimordialBeastEntity mob, float yaw) {
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.yBodyRot = yaw;
	}

	private static void night(PrimordialEvents.Fight f, PrimordialBeastEntity mob, long now) {
		State s = f.script;
		moveToward(mob, headSpot(f, now), 0.3D);
		if (now % 40L == 0L) {
			for (ServerPlayer player : f.fighters()) {
				if (player.distanceToSqr(mob) <= NIGHT_RANGE * NIGHT_RANGE) {
					player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false, true), mob);
				}
			}
		}
		if (now % 200L == 0L) {
			Fx.send(f.level, mob.position(), FxType.PURBA, PurbaFxIds.NIGHT, 1, 0.0F);
		}
		if (now >= s.nextA) {
			s.nextA = now + STRIKE_PERIOD;
			ServerPlayer target = randomFighter(f, mob, NIGHT_RANGE);
			if (target != null) {
				// Sambaran dari Gelap: the candle flares on the side the strike comes from, 1 s before.
				RandomSource random = f.level.getRandom();
				double angle = random.nextDouble() * Math.PI * 2.0D;
				Vec3 side = target.position().add(Math.cos(angle) * 7.0D, 1.5D, Math.sin(angle) * 7.0D);
				Vec3 aim = target.position().add(0.0D, 1.0D, 0.0D);
				Vec3 to = aim.add(PurbaHits.flatDirection(side, aim).scale(3.0D));
				Fx.send(f.level, side, FxType.PURBA, PurbaFxIds.CANDLE, 0, PurbaFxIds.CANDLE_TICKS);
				Fx.send(f.level, side, to, FxType.PURBA, PurbaFxIds.LINE, 0xFF5A2A, PurbaFxIds.CANDLE_TICKS);
				s.pending.add(new Pending(P_STRIKE, side, to, 1.6D, now + PurbaFxIds.CANDLE_TICKS));
				sound(f, side, SoundEvents.FIRECHARGE_USE, 1.5F, 0.5F);
			}
		}
		if (f.relics.isEmpty()) {
			spawnLilin(f);
		}
	}

	private static void spawnLilin(PrimordialEvents.Fight f) {
		PrimordialRelicEntity relic = PurbaEntities.PRIMORDIAL_RELIC.create(f.level, EntitySpawnReason.EVENT);
		if (relic == null) {
			return;
		}
		relic.setKind(PrimordialRelicEntity.LILIN);
		Vec3 at = lilinSpot(f);
		relic.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		relic.fight = f;
		if (f.level.addFreshEntity(relic)) {
			f.relics.add(relic);
		}
	}

	private static Vec3 lilinSpot(PrimordialEvents.Fight f) {
		RandomSource random = f.level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0D;
		double distance = 4.0D + random.nextDouble() * 8.0D;
		return f.center.add(Math.cos(angle) * distance, 2.0D, Math.sin(angle) * distance);
	}

	/** Hitting the Lilin opens 4 s; it then moves and rests for 8 s. */
	static void lilinHit(PrimordialEvents.Fight f, PrimordialRelicEntity relic) {
		long now = f.now();
		if (now < f.script.lilinCooldown) {
			return;
		}
		f.script.lilinCooldown = now + LILIN_COOLDOWN;
		f.openCelah(LILIN_CELAH);
		sound(f, relic.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 1.4F);
		Fx.send(f.level, relic.position(), FxType.PURBA, PurbaFxIds.CANDLE, 1, 20.0F);
		for (ServerPlayer player : f.fighters()) {
			Msg.actionBar(player, Component.translatable("kultivator.purba.celah.lilin").withStyle(ChatFormatting.GOLD));
		}
		Vec3 next = lilinSpot(f);
		relic.setPos(next.x, next.y, next.z);
	}

	private static void seasons(PrimordialEvents.Fight f, PrimordialBeastEntity mob, long now) {
		State s = f.script;
		moveToward(mob, headSpot(f, now), 0.3D);
		ServerPlayer nearest = nearestFighter(f, mob);
		if (nearest != null) {
			face(mob, nearest.position().subtract(mob.position()));
		}
		if (now >= s.nextA) {
			// Napas Musim: winter and summer in turn, the next one telegraphed 2 s by chat and colour.
			s.nextA = now + BREATH_PERIOD;
			s.coldNext = !s.coldNext;
			boolean cold = s.coldNext;
			Vec3 head = mob.position().add(0.0D, mob.getBbHeight() * 0.5D, 0.0D);
			mob.setFlag(PrimordialBeastEntity.FLAG_GLOW, true);
			ring(f, new Vec3(head.x, f.center.y, head.z), BREATH_RADIUS, PurbaFxIds.BREATH_TICKS, cold ? 0x9AD8FF : 0xFF7A2A);
			s.pending.add(new Pending(cold ? P_BREATH_COLD : P_BREATH_HOT, head, head, BREATH_RADIUS, now + PurbaFxIds.BREATH_TICKS));
			for (ServerPlayer player : f.fighters()) {
				Msg.chat(player, Component.translatable(cold ? "kultivator.purba.warn.winter" : "kultivator.purba.warn.summer")
						.withStyle(cold ? ChatFormatting.AQUA : ChatFormatting.GOLD));
			}
			sound(f, head, SoundEvents.ENDER_DRAGON_GROWL, 2.0F, cold ? 1.2F : 0.7F);
		}
	}

	// ================================================================== Taotie

	private static Vec3 taotieSpot(PrimordialEvents.Fight f, long now) {
		return f.center.add(0.0D, TAOTIE_HEIGHT + Math.sin(now * 0.04D) * 0.4D, 0.0D);
	}

	private static void headless(PrimordialEvents.Fight f, PrimordialBeastEntity mob, long now) {
		State s = f.script;
		moveToward(mob, taotieSpot(f, now), 0.3D);
		ServerPlayer nearest = nearestFighter(f, mob);
		if (nearest != null) {
			face(mob, nearest.position().subtract(mob.position()));
		}
		if (now % 20L != 0L) {
			return;
		}
		Iterator<Mob> iterator = f.pelahap.iterator();
		Vec3 head = mob.position();
		Vec3 ground = new Vec3(head.x, f.center.y, head.z);
		while (iterator.hasNext()) {
			Mob each = iterator.next();
			if (each.isRemoved() || !each.isAlive()) {
				iterator.remove();
				continue;
			}
			if (PurbaHits.horizontalSq(each.position(), head) <= 16.0D && Math.abs(each.getY() - head.y) < 7.0D) {
				// Eaten: the head heals 5% (it harms without swallowing; it heals by swallowing its own).
				iterator.remove();
				Fx.send(f.level, each.position().add(0.0D, 0.5D, 0.0D), FxType.PURBA, PurbaFxIds.DEVOUR, f.kind, 1.0F);
				sound(f, head, SoundEvents.PLAYER_BURP, 2.5F, 0.4F);
				each.discard();
				mob.setHealth(Math.min(f.ceiling(), mob.getHealth() + (float) (mob.getMaxHealth() * PELAHAP_HEAL)));
				continue;
			}
			if (each.getLastHurtByMob() == null) {
				each.setTarget(null);
			}
			if (each.getTarget() == null) {
				each.getNavigation().moveTo(ground.x, ground.y, ground.z, 1.2D);
			}
		}
		if (f.pelahap.isEmpty()) {
			// Celah while no Pelahap lives; a new six come after 8 s.
			f.celahUntil = Math.max(f.celahUntil, now + 25L);
			if (s.nextB <= 0L || s.nextB > now + PELAHAP_RESPAWN) {
				s.nextB = now + PELAHAP_RESPAWN;
			}
			if (now >= s.nextB) {
				s.nextB = 0L;
				spawnPelahap(f, PELAHAP_COUNT);
			}
		} else {
			s.nextB = 0L;
		}
	}

	private static void spawnPelahap(PrimordialEvents.Fight f, int count) {
		ServerLevel level = f.level;
		RandomSource random = level.getRandom();
		double health = 24.0D * 2.5D * (1.0D + 0.35D * f.realm);
		for (int i = 0; i < count; i++) {
			Mob mob = EntityTypes.SPIDER.create(level, EntitySpawnReason.EVENT);
			if (mob == null) {
				continue;
			}
			double angle = Math.PI * 2.0D * i / count + random.nextDouble() * 0.4D;
			Vec3 near = f.center.add(Math.cos(angle) * 14.0D, 0.0D, Math.sin(angle) * 14.0D);
			if (!level.isPositionEntityTicking(BlockPos.containing(near))) {
				continue;
			}
			Vec3 spot = Targeting.safeSpot(level, mob, near);
			if (spot == null) {
				continue;
			}
			mob.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
			AttributeInstance max = mob.getAttribute(Attributes.MAX_HEALTH);
			if (max != null) {
				max.setBaseValue(health);
			}
			mob.setHealth(mob.getMaxHealth());
			mob.setCanPickUpLoot(false);
			mob.addTag(NoGrief.TAG);
			mob.addTag(TechniquePower.TAG_ABADI_FOE);
			mob.addTag(PurbaHits.PELAHAP_TAG);
			if (level.addFreshEntity(mob)) {
				f.pelahap.add(mob);
				Fx.send(level, spot, FxType.PURBA, PurbaFxIds.DESCEND, 0, 0.0F);
			}
		}
	}

	private static void inhale(PrimordialEvents.Fight f, PrimordialBeastEntity mob, long now) {
		State s = f.script;
		moveToward(mob, taotieSpot(f, now), 0.3D);
		if (s.sub == 0) {
			ServerPlayer nearest = nearestFighter(f, mob);
			if (nearest != null) {
				face(mob, nearest.position().subtract(mob.position()));
			}
			if (now >= s.nextA && nearest != null) {
				s.sub = 1;
				s.subUntil = now + INHALE_TICKS;
				s.lockedYaw = mob.getYRot();
				s.mouth.clear();
				mob.setFlag(PrimordialBeastEntity.FLAG_GLOW, true);
				mob.setFlag(PrimordialBeastEntity.FLAG_MOUTH, true);
				for (ServerPlayer player : f.fighters()) {
					Msg.actionBar(player, Component.translatable("kultivator.purba.warn.inhale").withStyle(ChatFormatting.RED));
				}
				sound(f, mob.position(), SoundEvents.RAVAGER_AMBIENT, 3.0F, 0.4F);
			}
			return;
		}
		lockYaw(mob, s.lockedYaw);
		Vec3 mouth = mob.position().add(0.0D, mob.getBbHeight() * 0.4D, 0.0D);
		Vec3 dir = yawVector(s.lockedYaw);
		if (now % 10L == 0L) {
			Fx.send(f.level, mouth, mouth.add(dir.scale(INHALE_RANGE)), FxType.PURBA, PurbaFxIds.INHALE, f.kind, (float) INHALE_HALF_ANGLE);
		}
		if (now % 2L == 0L) {
			double cos = Math.cos(Math.toRadians(INHALE_HALF_ANGLE));
			for (ServerPlayer player : f.fighters()) {
				if (player.isCreative() || !PurbaHits.mayHit(mob, player)) {
					continue;
				}
				double sq = player.position().distanceToSqr(mouth);
				if (sq <= MOUTH_ZONE * MOUTH_ZONE * 1.6D) {
					int held = s.mouth.getOrDefault(player.getUUID(), 0) + 2;
					if (held >= DEVOUR_TICKS) {
						// Devoured: 30%, then spat out 12 blocks (it harms without swallowing).
						s.mouth.remove(player.getUUID());
						PurbaHits.share(f.level, mob, player, DEVOUR_SHARE);
						Vec3 away = dir.scale(2.2D).add(0.0D, 0.6D, 0.0D);
						PurbaHits.push(player, away);
						Fx.send(f.level, player.position(), FxType.PURBA, PurbaFxIds.DEVOUR, f.kind, 2.0F);
						sound(f, mouth, SoundEvents.PLAYER_BURP, 3.0F, 0.3F);
					} else {
						s.mouth.put(player.getUUID(), held);
					}
					continue;
				}
				if (sq > INHALE_RANGE * INHALE_RANGE) {
					continue;
				}
				Vec3 to = PurbaHits.flatDirection(mouth, player.position());
				if (to.dot(dir) >= cos) {
					PurbaHits.push(player, PurbaHits.flatDirection(player.position(), mouth).scale(INHALE_PULL));
				}
			}
		}
		if (now >= s.subUntil) {
			s.sub = 0;
			s.nextA = now + INHALE_PERIOD;
			s.mouth.clear();
			mob.setFlag(PrimordialBeastEntity.FLAG_GLOW, false);
			mob.setFlag(PrimordialBeastEntity.FLAG_MOUTH, false);
			f.openCelah(INHALE_CELAH);
		}
	}

	private static void gluttony(PrimordialEvents.Fight f, PrimordialBeastEntity mob, long now) {
		State s = f.script;
		moveToward(mob, taotieSpot(f, now), 0.3D);
		ServerPlayer nearest = nearestFighter(f, mob);
		if (nearest != null) {
			face(mob, nearest.position().subtract(mob.position()));
		}
		if (now >= s.nextA) {
			s.nextA = now + MOUTH_PERIOD;
			s.mouthOpen = !s.mouthOpen;
			mob.setFlag(PrimordialBeastEntity.FLAG_MOUTH, s.mouthOpen);
			if (s.mouthOpen) {
				sound(f, mob.position(), SoundEvents.RAVAGER_AMBIENT, 3.0F, 0.5F);
				for (ServerPlayer player : f.fighters()) {
					Msg.actionBar(player, Component.translatable("kultivator.purba.warn.mouth_open").withStyle(ChatFormatting.RED));
				}
			} else {
				f.openCelah(MOUTH_PERIOD);
			}
		}
	}

	// ================================================================== telegraphed skills

	private static void firePending(PrimordialEvents.Fight f, PrimordialBeastEntity mob, long now) {
		State s = f.script;
		if (s.pending.isEmpty()) {
			return;
		}
		List<Pending> due = new ArrayList<>();
		Iterator<Pending> iterator = s.pending.iterator();
		while (iterator.hasNext()) {
			Pending pending = iterator.next();
			if (now >= pending.at()) {
				iterator.remove();
				due.add(pending);
			}
		}
		for (Pending pending : due) {
			fire(f, mob, pending);
		}
	}

	private static void fire(PrimordialEvents.Fight f, PrimordialBeastEntity mob, Pending p) {
		ServerLevel level = f.level;
		switch (p.type()) {
			case P_WAVE -> {
				Fx.send(level, p.a(), FxType.PURBA, PurbaFxIds.WAVE, 0, (float) p.radius());
				sound(f, p.a(), SoundEvents.ELDER_GUARDIAN_CURSE, 3.0F, 0.6F);
				for (LivingEntity each : targets(f, mob, p.a(), p.radius(), 6.0D)) {
					if (PurbaHits.share(level, mob, each, WAVE_SHARE)) {
						Vec3 away = PurbaHits.flatDirection(p.a(), each.position()).scale(WAVE_KNOCKBACK).add(0.0D, 0.4D, 0.0D);
						PurbaHits.push(each, away);
					}
				}
			}
			case P_POUNCE -> {
				f.script.sub = 2;
				sound(f, p.a(), SoundEvents.PHANTOM_SWOOP, 3.0F, 0.4F);
				Fx.send(level, p.b(), FxType.PURBA, PurbaFxIds.HIT, 0x9AD8FF, 3.0F);
				for (LivingEntity each : PurbaHits.alongLine(level, mob, p.a(), p.b(), p.radius())) {
					if (!(each instanceof ServerPlayer player && f.isOut(player))) {
						PurbaHits.share(level, mob, each, POUNCE_SHARE);
					}
				}
			}
			case P_STRIKE -> {
				Fx.send(level, p.b(), FxType.PURBA, PurbaFxIds.HIT, 0xFF5A2A, 1.5F);
				sound(f, p.b(), SoundEvents.BLAZE_SHOOT, 2.0F, 0.5F);
				for (LivingEntity each : PurbaHits.alongLine(level, mob, p.a(), p.b(), p.radius())) {
					if (!(each instanceof ServerPlayer player && f.isOut(player))) {
						PurbaHits.share(level, mob, each, STRIKE_SHARE);
					}
				}
			}
			case P_BREATH_COLD, P_BREATH_HOT -> {
				boolean cold = p.type() == P_BREATH_COLD;
				Vec3 ground = new Vec3(p.a().x, f.center.y, p.a().z);
				Fx.send(level, p.a(), ground, FxType.PURBA, cold ? PurbaFxIds.BREATH_COLD : PurbaFxIds.BREATH_HOT, 0, (float) p.radius());
				sound(f, p.a(), cold ? SoundEvents.POWDER_SNOW_STEP : SoundEvents.BLAZE_SHOOT, 3.0F, cold ? 0.5F : 0.6F);
				for (LivingEntity each : targets(f, mob, ground, p.radius(), 16.0D)) {
					if (!PurbaHits.share(level, mob, each, cold ? COLD_SHARE : HOT_SHARE)) {
						continue;
					}
					if (cold) {
						each.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2), mob);
						if (each.canFreeze()) {
							each.setTicksFrozen(Math.max(each.getTicksFrozen(), each.getTicksRequiredToFreeze()));
						}
					} else {
						each.setRemainingFireTicks(Math.max(each.getRemainingFireTicks(), 100));
					}
				}
				mob.setFlag(PrimordialBeastEntity.FLAG_GLOW, false);
				f.openCelah(BREATH_CELAH);
			}
			default -> {
			}
		}
	}

	private PrimordialKinds() {
	}
}
