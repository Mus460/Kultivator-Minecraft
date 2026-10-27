package dev.kultivator.client.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import dev.kultivator.trial.HeartDemonEntity;
import dev.kultivator.trial.TrialFxIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Heart demon visuals, all particles drawn on the client (see {@link TrialFxIds} for the packet layout):
 * the appearance (a pillar of black smoke and violet motes collapsing into the demon's shape), the dark qi dash, the
 * dark palm (gathering, then a blood-dark blast with a ring around the impact), blinking back to the owner, the warning
 * before a pressure demon, and the end (won: the shadow shatters and flows into the owner with a golden flash; lost:
 * black smoke pours back into the owner; dispelled: it dissolves). Every heart demon within 48 blocks also trails
 * rising black and violet wisps ({@link #tick}). Counts are scaled by {@link EffectQuality}; the server plays the sounds.
 */
public final class TrialFx {

	private static final int SHADOW = 0x140A1E;
	private static final int DEEP_VIOLET = 0x34114F;
	private static final int VIOLET = 0x7A35C2;
	private static final int BLOOD = 0x9A1028;
	private static final int GOLD = 0xFFD54A;
	private static final double WISP_RANGE = 48.0D;
	private static final int MAX_RUNNING = 24;

	private static final int RUN_APPEAR = 0;
	private static final int RUN_CHARGE = 1;
	private static final int RUN_WARNING = 2;
	private static final int RUN_SHATTER = 3;
	private static final int RUN_RETURN = 4;

	/** An effect drawn over several ticks. */
	private static final class Running {
		final int kind;
		final Vec3 pos;
		final Vec3 target;
		final int entityId;
		final int duration;
		int age;

		Running(int kind, Vec3 pos, Vec3 target, int entityId, int duration) {
			this.kind = kind;
			this.pos = pos;
			this.target = target;
			this.entityId = entityId;
			this.duration = duration;
		}
	}

	private static final List<Running> RUNNING = new ArrayList<>();
	/** Running effects belong to one world: they are dropped when the client changes or leaves it. */
	private static ClientLevel lastLevel;
	/** Game time of the last tick, so a second call in the same tick does nothing. */
	private static long lastTick = Long.MIN_VALUE;

	public static void play(FxType type, Payloads.Fx fx) {
		ClientLevel level = CommonFx.level();
		if (level == null) {
			return;
		}
		resetIfNewLevel(level);
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		if (type == FxType.HEART_DEMON_APPEAR) {
			switch (fx.a()) {
				case TrialFxIds.APPEAR -> {
					smokePillar(pos);
					start(new Running(RUN_APPEAR, pos, target, fx.b(), 24));
				}
				case TrialFxIds.DASH -> dash(pos, target);
				case TrialFxIds.PALM_CHARGE -> start(new Running(RUN_CHARGE, pos, target, fx.b(), 16));
				case TrialFxIds.PALM -> palm(pos, target);
				case TrialFxIds.BLINK -> {
					puff(pos);
					puff(target);
				}
				case TrialFxIds.WARNING -> start(new Running(RUN_WARNING, pos, target, fx.b(), Mth.clamp((int) fx.f(), 20, 400)));
				default -> {
				}
			}
		} else if (type == FxType.HEART_DEMON_DEFEAT) {
			switch (fx.a()) {
				case TrialFxIds.DEFEAT_SHATTERED -> {
					shatter(pos);
					start(new Running(RUN_SHATTER, pos, target, fx.b(), 30));
				}
				case TrialFxIds.DEFEAT_RETURNED -> start(new Running(RUN_RETURN, pos, target, fx.b(), 24));
				default -> dissolve(pos);
			}
		}
	}

	/** Every client tick (only while in a world and not paused): wisps around heart demons and the running effects. */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		resetIfNewLevel(level);
		long time = level.getGameTime();
		if (time == lastTick) {
			return;
		}
		lastTick = time;
		wisps(minecraft, level);
		Iterator<Running> iterator = RUNNING.iterator();
		while (iterator.hasNext()) {
			Running running = iterator.next();
			running.age++;
			if (running.age > running.duration) {
				end(level, running);
				iterator.remove();
				continue;
			}
			draw(level, running);
		}
	}

	private static void resetIfNewLevel(ClientLevel level) {
		if (level != lastLevel) {
			lastLevel = level;
			RUNNING.clear();
			lastTick = Long.MIN_VALUE;
		}
	}

	private static void start(Running running) {
		if (RUNNING.size() >= MAX_RUNNING) {
			RUNNING.remove(0);
		}
		RUNNING.add(running);
	}

	// ================================================================== continuous

	/** Black and violet motes rising from every heart demon nearby; blood-red sparks at the hand while the palm gathers. */
	private static void wisps(Minecraft minecraft, ClientLevel level) {
		RandomSource random = level.getRandom();
		AABB box = minecraft.player.getBoundingBox().inflate(WISP_RANGE);
		for (HeartDemonEntity demon : level.getEntitiesOfClass(HeartDemonEntity.class, box, LivingEntity::isAlive)) {
			if (EffectQuality.chance(random, 0.9F)) {
				wisp(demon, random, SHADOW, 1.1F);
			}
			if (EffectQuality.chance(random, 0.5F)) {
				wisp(demon, random, DEEP_VIOLET, 0.8F);
			}
			if (EffectQuality.chance(random, 0.1F)) {
				CommonFx.add(random.nextBoolean() ? ParticleTypes.WITCH : ParticleTypes.REVERSE_PORTAL,
						demon.getX() + random.nextGaussian() * 0.3D, demon.getY() + random.nextFloat() * 1.8D,
						demon.getZ() + random.nextGaussian() * 0.3D, 0.0D, 0.02D, 0.0D);
			}
			if (EffectQuality.maximal() && random.nextInt(6) == 0) {
				CommonFx.add(ParticleTypes.SMOKE, demon.getX() + random.nextGaussian() * 0.25D, demon.getY() + 0.1D,
						demon.getZ() + random.nextGaussian() * 0.25D, 0.0D, 0.03D, 0.0D);
			}
			if (demon.isChargingPalm()) {
				Vec3 hand = rightHand(demon);
				for (int i = 0; i < EffectQuality.scale(2); i++) {
					Vec3 from = hand.add(random.nextGaussian() * 0.5D, random.nextGaussian() * 0.5D, random.nextGaussian() * 0.5D);
					CommonFx.add(CommonFx.dust(i % 2 == 0 ? BLOOD : SHADOW, 0.9F), from, hand.subtract(from).scale(0.18D), false);
				}
			}
		}
	}

	private static void wisp(HeartDemonEntity demon, RandomSource random, int color, float scale) {
		Vec3 at = new Vec3(demon.getX() + random.nextGaussian() * 0.28D, demon.getY() + random.nextFloat() * 1.85D,
				demon.getZ() + random.nextGaussian() * 0.28D);
		CommonFx.add(CommonFx.dust(color, scale), at, new Vec3(random.nextGaussian() * 0.005D, 0.02D + random.nextFloat() * 0.03D,
				random.nextGaussian() * 0.005D), false);
	}

	/** Where the raised right hand of the demon is (the arm goes up while the palm gathers). */
	private static Vec3 rightHand(Entity demon) {
		float yaw = (demon instanceof LivingEntity living ? living.yBodyRot : demon.getYRot()) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0D, -Mth.sin(yaw));
		return demon.position().add(right.scale(0.38D)).add(0.0D, 2.05D, 0.0D);
	}

	private static void draw(ClientLevel level, Running running) {
		RandomSource random = level.getRandom();
		float t = (float) running.age / running.duration;
		switch (running.kind) {
			case RUN_APPEAR -> {
				// Motes on a shrinking ring fall in towards the body until the shape stands there.
				Vec3 center = entityPos(level, running.entityId, running.pos);
				double radius = 2.4D * (1.0D - t) + 0.25D;
				int n = EffectQuality.scale(10);
				for (int i = 0; i < n; i++) {
					double angle = running.age * 0.35D + Math.PI * 2.0D * i / n;
					Vec3 at = center.add(Math.cos(angle) * radius, random.nextFloat() * 1.9D, Math.sin(angle) * radius);
					Vec3 axis = new Vec3(center.x, at.y, center.z);
					CommonFx.add(CommonFx.dust(i % 3 == 0 ? VIOLET : DEEP_VIOLET, 1.0F), at, axis.subtract(at).scale(0.12D), false);
				}
				if (EffectQuality.chance(random, 0.6F)) {
					CommonFx.add(ParticleTypes.LARGE_SMOKE, center.x + random.nextGaussian() * 0.2D, center.y + random.nextFloat() * 2.0D,
							center.z + random.nextGaussian() * 0.2D, 0.0D, 0.02D, 0.0D);
				}
			}
			case RUN_CHARGE -> {
				Entity demon = level.getEntity(running.entityId);
				if (demon != null) {
					CommonFx.spiralIn(rightHand(demon), BLOOD, 0.9F * (1.0F - t) + 0.2F, 3, running.age);
				}
			}
			case RUN_WARNING -> {
				// The cultivator's heart beats: dark motes seep out of the ground around them, faster near the end.
				Vec3 center = entityPos(level, running.entityId, running.pos);
				if (EffectQuality.chance(random, 0.25F + 0.6F * t)) {
					double angle = random.nextDouble() * Math.PI * 2.0D;
					double r = 0.6D + random.nextDouble() * 0.9D;
					CommonFx.add(CommonFx.dust(random.nextBoolean() ? SHADOW : DEEP_VIOLET, 0.9F),
							center.add(Math.cos(angle) * r, 0.05D, Math.sin(angle) * r), new Vec3(0.0D, 0.03D, 0.0D), false);
				}
				int beat = Math.max(8, 20 - (int) (12 * t));
				if (running.age % beat == 0) {
					CommonFx.ring(center, BLOOD, 0.9F, 20);
				}
			}
			case RUN_SHATTER -> {
				// The shattered shadow flows into its owner, violet turning to gold, ending in a golden flash.
				Vec3 owner = chest(level, running.entityId, running.target);
				if (running.age >= 3 && running.age <= 22) {
					int n = EffectQuality.scale(6);
					int color = t < 0.45F ? VIOLET : t < 0.65F ? 0xC89AF0 : GOLD;
					for (int i = 0; i < n; i++) {
						Vec3 from = running.pos.add(random.nextGaussian() * 0.4D, random.nextGaussian() * 0.5D, random.nextGaussian() * 0.4D);
						Vec3 velocity = owner.subtract(from).scale(0.1D + random.nextFloat() * 0.08D);
						CommonFx.add(CommonFx.dust(color, 1.0F), from, velocity, false);
					}
				}
				if (running.age == 20) {
					goldenFlash(owner);
				}
			}
			case RUN_RETURN -> {
				// Black smoke pours back into the owner's chest.
				Vec3 owner = chest(level, running.entityId, running.target);
				int n = EffectQuality.scale(5);
				for (int i = 0; i < n; i++) {
					Vec3 from = running.pos.add(random.nextGaussian() * 0.35D, random.nextGaussian() * 0.5D, random.nextGaussian() * 0.35D);
					Vec3 velocity = owner.subtract(from).scale(0.09D + random.nextFloat() * 0.06D);
					CommonFx.add(CommonFx.dust(i % 3 == 0 ? BLOOD : SHADOW, 1.2F), from, velocity, false);
				}
				if (EffectQuality.chance(random, 0.5F)) {
					CommonFx.add(ParticleTypes.SQUID_INK, running.pos.x, running.pos.y, running.pos.z, 0.0D, 0.02D, 0.0D);
				}
			}
			default -> {
			}
		}
	}

	/** The last frame of a running effect. */
	private static void end(ClientLevel level, Running running) {
		RandomSource random = level.getRandom();
		switch (running.kind) {
			case RUN_APPEAR -> {
				Vec3 center = entityPos(level, running.entityId, running.pos).add(0.0D, 1.0D, 0.0D);
				for (int i = 0; i < EffectQuality.scale(14); i++) {
					CommonFx.add(ParticleTypes.REVERSE_PORTAL, center, new Vec3(random.nextGaussian() * 0.15D, random.nextGaussian() * 0.15D,
							random.nextGaussian() * 0.15D), false);
				}
				CommonFx.burst(center, SHADOW, 20, 2.0F);
			}
			case RUN_RETURN -> {
				Vec3 owner = chest(level, running.entityId, running.target);
				for (int i = 0; i < EffectQuality.scale(10); i++) {
					CommonFx.add(ParticleTypes.SQUID_INK, owner, new Vec3(random.nextGaussian() * 0.12D, random.nextFloat() * 0.1D,
							random.nextGaussian() * 0.12D), false);
				}
				CommonFx.burst(owner, BLOOD, 16, 1.2F);
			}
			default -> {
			}
		}
	}

	// ================================================================== instant effects

	/** A column of black smoke and violet motes where the demon rises. */
	private static void smokePillar(Vec3 pos) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(36);
		for (int i = 0; i < n; i++) {
			double y = pos.y + random.nextFloat() * 5.0D;
			CommonFx.add(ParticleTypes.LARGE_SMOKE, new Vec3(pos.x + random.nextGaussian() * 0.3D, y, pos.z + random.nextGaussian() * 0.3D),
					new Vec3(0.0D, 0.02D + random.nextFloat() * 0.06D, 0.0D), true);
			if (i % 3 == 0) {
				CommonFx.add(ParticleTypes.SQUID_INK, pos.x + random.nextGaussian() * 0.25D, y, pos.z + random.nextGaussian() * 0.25D,
						0.0D, 0.04D, 0.0D);
			}
		}
		CommonFx.pillar(pos, VIOLET, 5.0F);
		CommonFx.ring(pos, DEEP_VIOLET, 2.4F, 40);
	}

	/** A streak of shadow along the dash, with an afterimage left where the demon stood. */
	private static void dash(Vec3 from, Vec3 to) {
		RandomSource random = CommonFx.random();
		CommonFx.beam(from, to, SHADOW, 0.5F);
		int n = EffectQuality.scale(10);
		Vec3 delta = to.subtract(from);
		for (int i = 0; i < n; i++) {
			Vec3 at = from.add(delta.scale(random.nextDouble()));
			CommonFx.add(ParticleTypes.LARGE_SMOKE, at.x, at.y - 0.4D + random.nextFloat() * 0.8D, at.z, 0.0D, 0.01D, 0.0D);
		}
		for (int i = 0; i < EffectQuality.scale(12); i++) {
			CommonFx.add(CommonFx.dust(DEEP_VIOLET, 1.1F), from.x + random.nextGaussian() * 0.15D, from.y - 1.0D + random.nextFloat() * 1.8D,
					from.z + random.nextGaussian() * 0.15D, 0.0D, 0.0D, 0.0D);
		}
	}

	/** The dark palm: a blood-dark blast to the target, a ring of shadow around the impact, ink splashing out. */
	private static void palm(Vec3 from, Vec3 to) {
		RandomSource random = CommonFx.random();
		CommonFx.beam(from, to, BLOOD, 0.3F);
		Vec3 dir = to.subtract(from);
		if (dir.lengthSqr() < 1.0E-4D) {
			dir = new Vec3(0.0D, 0.0D, 1.0D);
		}
		dir = dir.normalize();
		Vec3 u = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
		if (u.lengthSqr() < 1.0E-4D) {
			u = new Vec3(1.0D, 0.0D, 0.0D);
		}
		u = u.normalize();
		Vec3 v = u.cross(dir).normalize();
		Vec3 center = to.subtract(dir.scale(0.3D));
		int points = EffectQuality.scale(18);
		for (int i = 0; i < points; i++) {
			double angle = Math.PI * 2.0D * i / points;
			Vec3 offset = u.scale(Math.cos(angle) * 0.75D).add(v.scale(Math.sin(angle) * 0.75D));
			CommonFx.add(CommonFx.dust(i % 2 == 0 ? BLOOD : SHADOW, 1.1F), center.add(offset), offset.scale(0.08D), false);
		}
		for (int i = 0; i < EffectQuality.scale(8); i++) {
			CommonFx.add(ParticleTypes.SQUID_INK, to, new Vec3(random.nextGaussian() * 0.15D, random.nextFloat() * 0.15D,
					random.nextGaussian() * 0.15D), false);
		}
		CommonFx.burst(to, DEEP_VIOLET, 20, 1.5F);
	}

	private static void puff(Vec3 pos) {
		RandomSource random = CommonFx.random();
		for (int i = 0; i < EffectQuality.scale(10); i++) {
			CommonFx.add(ParticleTypes.LARGE_SMOKE, pos.x + random.nextGaussian() * 0.3D, pos.y - 0.8D + random.nextFloat() * 1.8D,
					pos.z + random.nextGaussian() * 0.3D, 0.0D, 0.03D, 0.0D);
		}
		for (int i = 0; i < EffectQuality.scale(8); i++) {
			CommonFx.add(ParticleTypes.REVERSE_PORTAL, pos, new Vec3(random.nextGaussian() * 0.1D, random.nextGaussian() * 0.1D,
					random.nextGaussian() * 0.1D), false);
		}
		CommonFx.burst(pos, SHADOW, 12, 1.0F);
	}

	/** The shadow shatters: shards of ink and dark motes fly apart. */
	private static void shatter(Vec3 pos) {
		RandomSource random = CommonFx.random();
		for (int i = 0; i < EffectQuality.scale(16); i++) {
			Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.7D, random.nextGaussian()).normalize();
			CommonFx.add(ParticleTypes.SQUID_INK, pos, dir.scale(0.2D), true);
		}
		for (int i = 0; i < EffectQuality.scale(30); i++) {
			Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
			Vec3 at = pos.add(dir.scale(random.nextFloat() * 0.6D));
			CommonFx.add(CommonFx.dust(i % 2 == 0 ? SHADOW : DEEP_VIOLET, 1.3F), at, dir.scale(0.15D + random.nextFloat() * 0.2D), false);
		}
		if (EffectQuality.maximal()) {
			for (int i = 0; i < 12; i++) {
				CommonFx.add(ParticleTypes.END_ROD, pos, new Vec3(random.nextGaussian() * 0.12D, random.nextGaussian() * 0.12D,
						random.nextGaussian() * 0.12D), false);
			}
		}
	}

	private static void goldenFlash(Vec3 at) {
		RandomSource random = CommonFx.random();
		CommonFx.add(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | GOLD), at, Vec3.ZERO, true);
		for (int i = 0; i < EffectQuality.scale(20); i++) {
			CommonFx.add(ParticleTypes.TOTEM_OF_UNDYING, at, new Vec3(random.nextGaussian() * 0.25D, random.nextFloat() * 0.35D,
					random.nextGaussian() * 0.25D), false);
		}
		CommonFx.ring(at.subtract(0.0D, 1.0D, 0.0D), GOLD, 1.3F, 32);
		CommonFx.burst(at, GOLD, 18, 1.4F);
	}

	private static void dissolve(Vec3 pos) {
		puff(pos);
		CommonFx.burst(pos, DEEP_VIOLET, 16, 1.6F);
	}

	/** The chest of the entity with this id, or {@code fallback} (already a chest position) when it is not loaded here. */
	private static Vec3 chest(ClientLevel level, int entityId, Vec3 fallback) {
		Entity entity = entityId >= 0 ? level.getEntity(entityId) : null;
		return entity != null ? entity.position().add(0.0D, entity.getBbHeight() * 0.55D, 0.0D) : fallback;
	}

	/** Where the entity with this id is now (its feet), or {@code fallback} when it is not loaded here. */
	private static Vec3 entityPos(ClientLevel level, int entityId, Vec3 fallback) {
		Entity entity = entityId >= 0 ? level.getEntity(entityId) : null;
		return entity != null ? entity.position() : fallback;
	}

	/** Disconnect: drops the old world (the next world's first tick would only do it after it loads). */
	static void forget() {
		lastLevel = null;
		RUNNING.clear();
		lastTick = Long.MIN_VALUE;
	}

	private TrialFx() {
	}
}
