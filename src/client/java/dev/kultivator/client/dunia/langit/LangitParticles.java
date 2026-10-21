package dev.kultivator.client.dunia.langit;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.dunia.api.SpiritWeather;
import dev.kultivator.dunia.langit.HartaLahirEntity;
import dev.kultivator.dunia.langit.MeteorRohEntity;
import dev.kultivator.dunia.langit.TreasureRewards;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The particles of module L (DESIGN 34.5.8-34.5.10), all {@code kultivator:qi} types through {@link Qi} (dust at
 * Minimal) and counted by {@link EffectQuality#scale}: drifting Qi motes in the mist (3 / 8 / 16 per s within 12 blocks),
 * rising rainbow motes at a pillar's foot (6 / 16 / 32 per s within 64 blocks), the meteor's spark tail, the red breath of
 * eclipse-touched monsters, the stream of Qi into a channelling player, and the one-off bursts (impact, tremor dust, the
 * claim, dew, an absorbed meteor stone, a broken channel).
 */
final class LangitParticles {

	/** The seven colours of the Pilar Pelangi, red to violet. */
	static final int[] RAINBOW = {0xFF4A4A, 0xFF9A3A, 0xFFE45A, 0x5AF08A, 0x4AC8FF, 0x6A7AFF, 0xC070FF};
	static final int MIST_RGB = 0xD8F2E8;
	static final int DAWN_RGB = 0xD8B0FF;
	static final int ECLIPSE_RGB = 0xFF3A2A;
	static final int METEOR_RGB = 0x9FE8FF;

	/** Meteors drawn this frame or recently (their renderer registers them), for the spark tail. */
	private static final Set<MeteorRohEntity> METEORS = Collections.newSetFromMap(new WeakHashMap<>());

	private static float mistCarry;
	private static float pillarCarry;

	static void track(MeteorRohEntity meteor) {
		METEORS.add(meteor);
	}

	static void tick(ClientLevel level, Minecraft minecraft) {
		Player player = minecraft.player;
		if (player == null || minecraft.isPaused()) {
			return;
		}
		RandomSource random = level.getRandom();
		mistMotes(level, player, random);
		pillarMotes(level, player, random);
		meteorTails(level, random);
		if (level.getGameTime() % 5L == 0L) {
			eclipseBreath(level, random);
			channelStreams(level, player, random);
		}
	}

	private static void mistMotes(ClientLevel level, Player player, RandomSource random) {
		float mist = LangitState.mist;
		if (mist < 0.05F) {
			return;
		}
		// 3 / 8 / 16 per second at full mist
		mistCarry += EffectQuality.scale(8) / 20.0F * mist;
		int rgb = LangitState.dawn > 0.5F ? DAWN_RGB : MIST_RGB;
		float light = 0.35F + 0.65F * LangitState.daylight();
		int shade = Qi.lerp(0x404848, rgb, light);
		while (mistCarry >= 1.0F) {
			mistCarry -= 1.0F;
			double x = player.getX() + (random.nextDouble() - 0.5D) * 24.0D;
			double z = player.getZ() + (random.nextDouble() - 0.5D) * 24.0D;
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
			double y = Math.max(ground, player.getY() - 4.0D) + 0.3D + random.nextDouble() * 2.2D;
			level.addParticle(Qi.mote(shade, 0.12F + random.nextFloat() * 0.1F, 70 + random.nextInt(50)), x, y, z,
					(random.nextDouble() - 0.5D) * 0.01D, 0.004D + random.nextDouble() * 0.01D, (random.nextDouble() - 0.5D) * 0.01D);
		}
	}

	private static void pillarMotes(ClientLevel level, Player player, RandomSource random) {
		if (LangitState.BIRTHS.isEmpty()) {
			return;
		}
		for (LangitState.BirthView view : LangitState.BIRTHS.values()) {
			if (!view.live()) {
				continue;
			}
			Vec3 base = view.base();
			if (player.distanceToSqr(base) > 64.0D * 64.0D) {
				continue;
			}
			// 6 / 16 / 32 per second
			pillarCarry += EffectQuality.scale(16) / 20.0F * view.alpha;
			while (pillarCarry >= 1.0F) {
				pillarCarry -= 1.0F;
				int rgb = RAINBOW[random.nextInt(RAINBOW.length)];
				double angle = random.nextDouble() * Math.PI * 2.0D;
				double r = 0.6D + random.nextDouble() * 2.4D;
				level.addParticle(Qi.mote(rgb, 0.14F + random.nextFloat() * 0.12F, 50 + random.nextInt(40)),
						base.x + Math.cos(angle) * r, base.y + 0.1D + random.nextDouble() * 0.6D, base.z + Math.sin(angle) * r,
						-Math.cos(angle) * 0.012D, 0.05D + random.nextDouble() * 0.06D, -Math.sin(angle) * 0.012D);
			}
		}
	}

	private static void meteorTails(ClientLevel level, RandomSource random) {
		if (METEORS.isEmpty()) {
			return;
		}
		int per = EffectQuality.scale(3);
		for (Iterator<MeteorRohEntity> it = METEORS.iterator(); it.hasNext(); ) {
			MeteorRohEntity meteor = it.next();
			if (meteor.isRemoved()) {
				it.remove();
				continue;
			}
			Vec3 dir = meteor.direction();
			for (int i = 0; i < per; i++) {
				double back = random.nextDouble() * 2.5D;
				level.addParticle(Qi.spark(i % 2 == 0 ? METEOR_RGB : 0xFFFFFF, 0.18F + random.nextFloat() * 0.12F, 14 + random.nextInt(10)),
						meteor.getX() - dir.x * back + (random.nextDouble() - 0.5D) * 0.6D,
						meteor.getY() + 0.5D - dir.y * back + (random.nextDouble() - 0.5D) * 0.6D,
						meteor.getZ() - dir.z * back + (random.nextDouble() - 0.5D) * 0.6D,
						-dir.x * 0.15D, -dir.y * 0.15D, -dir.z * 0.15D);
			}
			if (EffectQuality.chance(random, 0.5F)) {
				level.addParticle(Qi.smoke(0x506070, 0.6F, 30), meteor.getX() - dir.x * 3.0D, meteor.getY() + 0.5D - dir.y * 3.0D,
						meteor.getZ() - dir.z * 3.0D, 0.0D, 0.01D, 0.0D);
			}
		}
	}

	private static void eclipseBreath(ClientLevel level, RandomSource random) {
		int[] ids = LangitState.marks;
		if (ids.length == 0) {
			return;
		}
		int per = EffectQuality.minimal() ? 0 : 1;
		for (int id : ids) {
			Entity entity = level.getEntity(id);
			if (entity == null || !entity.isAlive()) {
				continue;
			}
			for (int i = 0; i <= per; i++) {
				level.addParticle(Qi.mote(ECLIPSE_RGB, 0.12F + random.nextFloat() * 0.08F, 24 + random.nextInt(16)),
						entity.getX() + (random.nextDouble() - 0.5D) * entity.getBbWidth(),
						entity.getY() + random.nextDouble() * entity.getBbHeight(),
						entity.getZ() + (random.nextDouble() - 0.5D) * entity.getBbWidth(), 0.0D, 0.03D, 0.0D);
			}
		}
	}

	private static void channelStreams(ClientLevel level, Player player, RandomSource random) {
		if (LangitState.BIRTHS.isEmpty()) {
			return;
		}
		for (HartaLahirEntity treasure : level.getEntitiesOfClass(HartaLahirEntity.class, player.getBoundingBox().inflate(48.0D))) {
			if (treasure.channel() <= 0) {
				continue;
			}
			Entity channeler = level.getEntity(treasure.channeler());
			if (channeler == null) {
				continue;
			}
			Vec3 from = treasure.position().add(0.0D, 1.1D, 0.0D);
			Vec3 to = channeler.position().add(0.0D, channeler.getBbHeight() * 0.6D, 0.0D);
			Vec3 delta = to.subtract(from);
			int count = EffectQuality.scale(3);
			int rgb = TreasureRewards.tierColor(treasure.tier());
			for (int i = 0; i < count; i++) {
				double t = random.nextDouble() * 0.2D;
				level.addParticle(Qi.mote(i % 2 == 0 ? rgb : RAINBOW[random.nextInt(RAINBOW.length)], 0.1F + random.nextFloat() * 0.08F, 16),
						from.x + delta.x * t, from.y + delta.y * t, from.z + delta.z * t,
						delta.x / 16.0D, delta.y / 16.0D, delta.z / 16.0D);
			}
		}
	}

	// ================================================================== bursts

	/** A Meteor Roh landed: dust, sparks and a ground ring. */
	static void impact(ClientLevel level, double x, double y, double z) {
		RandomSource random = level.getRandom();
		int sparks = EffectQuality.scale(24);
		for (int i = 0; i < sparks; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double speed = 0.25D + random.nextDouble() * 0.45D;
			level.addParticle(Qi.spark(i % 3 == 0 ? 0xFFFFFF : METEOR_RGB, 0.25F, 18 + random.nextInt(12)), x, y + 0.3D, z,
					Math.cos(angle) * speed, 0.2D + random.nextDouble() * 0.5D, Math.sin(angle) * speed);
		}
		int dust = EffectQuality.scale(14);
		for (int i = 0; i < dust; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = random.nextDouble() * 2.0D;
			level.addParticle(Qi.smoke(0x6A6070, 1.0F + random.nextFloat(), 50 + random.nextInt(30)), x + Math.cos(angle) * r, y + 0.2D,
					z + Math.sin(angle) * r, Math.cos(angle) * 0.08D, 0.03D + random.nextDouble() * 0.05D, Math.sin(angle) * 0.08D);
		}
		level.addParticle(Qi.groundRing(METEOR_RGB, 0.6F, 7.0F, 22), x, y + 0.05D, z, 0.0D, 0.0D, 0.0D);
		level.addParticle(Qi.ring(0xFFFFFF, 0.5F, 4.5F, 12), x, y + 1.0D, z, 0.0D, 0.0D, 0.0D);
		level.addParticle(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFBFEFFF), x, y + 0.5D, z, 0.0D, 0.0D, 0.0D);
	}

	/** The treasure tremor: dust shaken loose around the player. */
	static void tremorDust(ClientLevel level, Vec3 at, float strength) {
		RandomSource random = level.getRandom();
		int count = EffectQuality.scale(Math.round(10 * strength) + 4);
		for (int i = 0; i < count; i++) {
			double x = at.x + (random.nextDouble() - 0.5D) * 10.0D;
			double z = at.z + (random.nextDouble() - 0.5D) * 10.0D;
			BlockPos column = BlockPos.containing(x, at.y, z);
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			level.addParticle(Qi.smoke(0x8A7A60, 0.5F + random.nextFloat() * 0.5F, 30 + random.nextInt(20)), x, ground + 0.1D, z,
					0.0D, 0.04D + random.nextDouble() * 0.04D, 0.0D);
		}
	}

	/** The claim: seven-colour petals and rings bursting from the pillar's foot. */
	static void claimBurst(ClientLevel level, double x, double y, double z, int tier) {
		RandomSource random = level.getRandom();
		int petals = EffectQuality.scale(36);
		for (int i = 0; i < petals; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double speed = 0.15D + random.nextDouble() * 0.35D;
			int rgb = RAINBOW[i % RAINBOW.length];
			ParticleOptions petal = i % 3 == 0 ? Qi.mote(rgb, 0.22F, 40) : Qi.petal(rgb, 0.18F, 50 + random.nextInt(30));
			level.addParticle(petal, x, y + 1.0D, z, Math.cos(angle) * speed, 0.15D + random.nextDouble() * 0.4D, Math.sin(angle) * speed);
		}
		int rgb = TreasureRewards.tierColor(tier);
		level.addParticle(Qi.groundRing(rgb, 0.8F, 10.0F, 30), x, y + 0.05D, z, 0.0D, 0.0D, 0.0D);
		level.addParticle(Qi.groundRing(0xFFFFFF, 0.5F, 6.0F, 20), x, y + 0.1D, z, 0.0D, 0.0D, 0.0D);
		level.addParticle(Qi.ring(rgb, 0.6F, 5.0F, 16), x, y + 1.2D, z, 0.0D, 0.0D, 0.0D);
		level.addParticle(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFF4D8), x, y + 1.0D, z, 0.0D, 0.0D, 0.0D);
	}

	/** Dew gathered: violet or jade sparkles drawn into the bottle. */
	static void dew(ClientLevel level, double x, double y, double z, int weatherOrdinal) {
		RandomSource random = level.getRandom();
		int rgb = weatherOrdinal == SpiritWeather.PURPLE_DAWN.ordinal() ? DAWN_RGB : 0xB8F0D8;
		int count = EffectQuality.scale(12);
		for (int i = 0; i < count; i++) {
			double ox = (random.nextDouble() - 0.5D) * 2.4D;
			double oy = (random.nextDouble() - 0.5D) * 1.6D;
			double oz = (random.nextDouble() - 0.5D) * 2.4D;
			level.addParticle(Qi.glint(rgb, 0.12F, 18), x + ox, y + oy, z + oz, -ox / 18.0D, -oy / 18.0D, -oz / 18.0D);
		}
	}

	/** A Batu Meteor Roh absorbed: cyan Qi spiralling up around the player. */
	static void absorb(ClientLevel level, Player player) {
		RandomSource random = level.getRandom();
		int count = EffectQuality.scale(16);
		for (int i = 0; i < count; i++) {
			double angle = i / (double) count * Math.PI * 4.0D;
			double r = 0.9D;
			level.addParticle(Qi.mote(i % 4 == 0 ? 0xFFFFFF : METEOR_RGB, 0.14F, 30), player.getX() + Math.cos(angle) * r,
					player.getY() + 0.2D + i * 0.1D, player.getZ() + Math.sin(angle) * r, -Math.cos(angle) * 0.03D, 0.04D, -Math.sin(angle) * 0.03D);
		}
	}

	/** The channel broke: the gathered Qi scatters. */
	static void broken(ClientLevel level, double x, double y, double z) {
		RandomSource random = level.getRandom();
		int count = EffectQuality.scale(10);
		for (int i = 0; i < count; i++) {
			level.addParticle(Qi.spark(0xFF6A5A, 0.2F, 12), x, y + 1.0D, z, (random.nextDouble() - 0.5D) * 0.6D,
					random.nextDouble() * 0.4D, (random.nextDouble() - 0.5D) * 0.6D);
		}
	}

	/** True when an eclipse mark list holds {@code id}. */
	static boolean marked(int id) {
		for (int each : LangitState.marks) {
			if (each == id) {
				return true;
			}
		}
		return false;
	}

	static AABB around(Vec3 at, double r) {
		return new AABB(at, at).inflate(r);
	}

	static void clear() {
		METEORS.clear();
		mistCarry = 0.0F;
		pillarCarry = 0.0F;
	}

	private LangitParticles() {
	}
}
