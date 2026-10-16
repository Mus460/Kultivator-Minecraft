package dev.kultivator.client.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Pill effects. A pill (PILL, a = RGB, b = strength 1..3) makes strands of its colour swirl up around the eater
 * (more strands and qi glyphs for stronger pills, a ground ring for the strongest, sparkles at Maximal) and ends in a
 * small burst above the head. b = 0 is pill poisoning: sickly green motes and droplets around the head.
 * The swirl follows the player who took the pill, so it stays around them while they walk.
 */
public final class PillFx {

	private static final int MAX_ACTIVE = 32;
	private static final int SICK_LIGHT = 0xA4C639;
	private static final int SICK_DARK = 0x56702A;
	private static final int SICK_TICKS = 30;
	/** How far from the effect position the eater may be to be followed. */
	private static final double FOLLOW_RANGE_SQ = 2.5D * 2.5D;

	private static final List<Swirl> ACTIVE = new ArrayList<>();
	private static long lastTick = Long.MIN_VALUE;
	private static boolean hooked;

	/** Hooks the animation into the client tick once (ticking twice in the same game tick is ignored). */
	public static void init() {
		if (!hooked) {
			hooked = true;
			ClientTickEvents.END_CLIENT_TICK.register(PillFx::tick);
		}
	}

	public static void play(FxType type, Payloads.Fx fx) {
		if (type != FxType.PILL) {
			return;
		}
		ClientLevel level = CommonFx.level();
		if (level == null) {
			return;
		}
		RandomSource random = CommonFx.random();
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		int strength = Mth.clamp(fx.b(), 0, 3);
		int rgb = strength == 0 ? SICK_LIGHT : fx.a() & 0xFFFFFF;
		int duration = strength == 0 ? SICK_TICKS : 14 + 6 * strength;
		Swirl swirl = new Swirl(followId(level, pos), pos, rgb, strength, random.nextFloat() * (float) (Math.PI * 2.0D), duration);
		ACTIVE.add(swirl);
		while (ACTIVE.size() > MAX_ACTIVE) {
			ACTIVE.remove(0);
		}

		if (strength == 0) {
			CommonFx.burst(pos.add(0.0D, 1.5D, 0.0D), SICK_DARK, 10, 0.8F);
			return;
		}
		// Qi gathered from the air flows into the dantian.
		if (strength >= 2) {
			int glyphs = EffectQuality.scale(6 * strength);
			for (int i = 0; i < glyphs; i++) {
				CommonFx.add(ParticleTypes.ENCHANT, pos.x, pos.y + 1.0D, pos.z, random.nextGaussian() * 1.1D,
						random.nextFloat() * 1.2D, random.nextGaussian() * 1.1D);
			}
		}
		if (strength >= 3) {
			CommonFx.ring(pos, rgb, 1.3F, 36);
		}
	}

	/** Every client tick (only while in a world and not paused). */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			ACTIVE.clear();
			return;
		}
		if (minecraft.player == null || minecraft.isPaused() || ACTIVE.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		if (now == lastTick) {
			return;
		}
		lastTick = now;
		RandomSource random = level.getRandom();
		Iterator<Swirl> iterator = ACTIVE.iterator();
		while (iterator.hasNext()) {
			Swirl swirl = iterator.next();
			Vec3 center = swirl.center(level);
			if (swirl.strength == 0) {
				sickTick(swirl, center, random);
			} else {
				swirlTick(swirl, center, random);
			}
			swirl.age++;
			if (swirl.age >= swirl.duration) {
				if (swirl.strength > 0) {
					CommonFx.burst(center.add(0.0D, 2.0D + 0.15D * swirl.strength, 0.0D), swirl.rgb, 4 + 4 * swirl.strength, 1.0F);
				}
				iterator.remove();
			}
		}
	}

	/** One step of the rising strands: they climb from the feet to above the head and narrow as they rise. */
	private static void swirlTick(Swirl swirl, Vec3 center, RandomSource random) {
		float t = (float) swirl.age / swirl.duration;
		int strands = 1 + swirl.strength;
		double height = 0.1D + t * (1.9D + 0.15D * swirl.strength);
		double radius = 0.85D - 0.35D * t;
		DustParticleOptions dust = CommonFx.dust(swirl.rgb, 0.7F + 0.2F * swirl.strength);
		for (int k = 0; k < strands; k++) {
			if (!EffectQuality.chance(random, 0.9F)) {
				continue;
			}
			double angle = swirl.phase + swirl.age * 0.55D + Math.PI * 2.0D * k / strands;
			double x = center.x + Math.cos(angle) * radius;
			double y = center.y + height;
			double z = center.z + Math.sin(angle) * radius;
			CommonFx.add(dust, x, y, z, 0.0D, 0.02D, 0.0D);
			if (EffectQuality.maximal()) {
				// A trailing mote and now and then a sparkle.
				double back = angle - 0.3D;
				CommonFx.add(dust, center.x + Math.cos(back) * radius, y - 0.08D, center.z + Math.sin(back) * radius, 0.0D, 0.0D, 0.0D);
				if (random.nextInt(3) == 0) {
					CommonFx.add(ParticleTypes.END_ROD, x, y, z, random.nextGaussian() * 0.01D, 0.02D, random.nextGaussian() * 0.01D);
				}
			}
		}
		if (swirl.strength >= 2 && swirl.age % 3 == 0 && EffectQuality.chance(random, 0.8F)) {
			CommonFx.add(ParticleTypes.ENCHANT, center.x, center.y + 1.1D, center.z, random.nextGaussian() * 1.2D,
					random.nextFloat() * 0.9D, random.nextGaussian() * 1.2D);
		}
	}

	/** One step of pill poisoning: sickly motes hang around the head and drip down. */
	private static void sickTick(Swirl swirl, Vec3 center, RandomSource random) {
		int motes = EffectQuality.maximal() ? 3 : 2;
		for (int i = 0; i < motes; i++) {
			if (!EffectQuality.chance(random, 0.7F)) {
				continue;
			}
			int rgb = random.nextBoolean() ? SICK_LIGHT : SICK_DARK;
			CommonFx.add(CommonFx.dust(rgb, 0.9F + random.nextFloat() * 0.5F), center.x + (random.nextFloat() - 0.5F) * 0.9D,
					center.y + 1.35D + random.nextFloat() * 0.6D, center.z + (random.nextFloat() - 0.5F) * 0.9D, 0.0D, -0.02D, 0.0D);
		}
		if (swirl.age % 4 == 0 && EffectQuality.chance(random, 1.0F)) {
			CommonFx.add(ParticleTypes.SNEEZE, center.x + (random.nextFloat() - 0.5F) * 0.6D, center.y + 1.6D,
					center.z + (random.nextFloat() - 0.5F) * 0.6D, 0.0D, -0.03D, 0.0D);
		}
	}

	/** The nearest player at the effect position (the one who took the pill), -1 when none. */
	private static int followId(ClientLevel level, Vec3 pos) {
		int best = -1;
		double bestSq = FOLLOW_RANGE_SQ;
		for (Entity player : level.players()) {
			double distSq = player.distanceToSqr(pos);
			if (distSq < bestSq) {
				bestSq = distSq;
				best = player.getId();
			}
		}
		return best;
	}

	/** One running pill effect. */
	private static final class Swirl {
		private final int entityId;
		private final Vec3 origin;
		private final int rgb;
		private final int strength;
		private final float phase;
		private final int duration;
		private int age;

		private Swirl(int entityId, Vec3 origin, int rgb, int strength, float phase, int duration) {
			this.entityId = entityId;
			this.origin = origin;
			this.rgb = rgb;
			this.strength = strength;
			this.phase = phase;
			this.duration = duration;
		}

		/** The followed player's feet, or the original position when they are gone. */
		private Vec3 center(ClientLevel level) {
			Entity entity = entityId < 0 ? null : level.getEntity(entityId);
			return entity == null || entity.isRemoved() ? origin : entity.position();
		}
	}

	private PillFx() {
	}
}
