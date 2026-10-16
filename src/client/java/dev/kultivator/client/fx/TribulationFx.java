package dev.kultivator.client.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tribulation visuals, all particles: the dark cloud disc gathering high above the cultivator (kept alive every client
 * tick while omen/strike packets keep coming), the strikes (a particle bolt from the clouds, never a LightningBolt
 * entity), the clouds parting at the end, and Dao Protector arrivals. The client plays no sounds; the server does.
 */
public final class TribulationFx {

	/** The cloud starts to dissolve this long after the last packet about it. */
	private static final int EXPIRE_TICKS = 80;
	private static final int DISSOLVE_TICKS = 40;
	private static final float START_RADIUS = 3.0F;
	private static final float FULL_RADIUS = 12.0F;
	private static final double SKY_HEIGHT = 22.0D;
	private static final int MAX_CLOUDS = 8;

	private static final class Cloud {
		final Vec3 center;
		final int variant;
		float radius = START_RADIUS;
		float targetRadius = START_RADIUS + 1.0F;
		int age;
		/** {@link #age} when the last packet about this cloud arrived (client ticks, so it works in any world). */
		int packetAge;
		int dissolving = -1;
		boolean success;

		Cloud(Vec3 center, int variant) {
			this.center = center;
			this.variant = variant;
		}
	}

	private static final List<Cloud> CLOUDS = new ArrayList<>();
	/** Clouds belong to one world: they are dropped when the client changes or leaves it. */
	private static ClientLevel lastLevel;

	public static void play(FxType type, Payloads.Fx fx) {
		ClientLevel level = CommonFx.level();
		if (level == null) {
			return;
		}
		if (level != lastLevel) {
			lastLevel = level;
			CLOUDS.clear();
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		Vec3 sky = target.distanceToSqr(pos) < 1.0D ? pos.add(0.0D, SKY_HEIGHT, 0.0D) : target;
		int variant = Math.max(0, Math.min(2, fx.a()));
		switch (type) {
			case TRIBULATION_OMEN -> {
				Cloud cloud = cloudAt(sky, variant, true);
				float progress = 1.0F - Math.max(0, Math.min(240, fx.b())) / 240.0F;
				cloud.targetRadius = Math.max(cloud.targetRadius, START_RADIUS + (FULL_RADIUS - START_RADIUS) * progress);
			}
			case TRIBULATION_STRIKE -> {
				Cloud cloud = cloudAt(sky, variant, true);
				cloud.targetRadius = FULL_RADIUS;
				strike(sky, pos, variant, fx.f() >= 1.5F);
			}
			case TRIBULATION_END -> {
				Cloud cloud = cloudAt(sky, variant, false);
				if (cloud != null) {
					cloud.dissolving = 0;
					cloud.success = fx.b() == 1;
				}
				if (fx.b() == 1) {
					heavenlyLight(pos, sky);
				}
			}
			case DAO_PROTECTOR -> protector(pos, variant, fx.b() == 1);
			default -> {
			}
		}
	}

	/** Every client tick (only while in a world and not paused). */
	public static void tick(Minecraft minecraft) {
		// BreakthroughFx's continuous effects ride along on this tick (it ignores a second call in the same tick).
		BreakthroughFx.tick(minecraft);
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			lastLevel = level;
			CLOUDS.clear();
		}
		if (level == null || CLOUDS.isEmpty()) {
			return;
		}
		Iterator<Cloud> iterator = CLOUDS.iterator();
		while (iterator.hasNext()) {
			Cloud cloud = iterator.next();
			cloud.age++;
			if (cloud.dissolving < 0 && cloud.age - cloud.packetAge > EXPIRE_TICKS) {
				cloud.dissolving = 0;
			}
			if (cloud.dissolving >= 0) {
				cloud.dissolving++;
				if (cloud.dissolving > DISSOLVE_TICKS) {
					iterator.remove();
					continue;
				}
			}
			if (cloud.radius < cloud.targetRadius) {
				cloud.radius = Math.min(cloud.targetRadius, cloud.radius + 0.08F);
			}
			drawCloud(cloud);
		}
	}

	// ================================================================== clouds

	private static Cloud cloudAt(Vec3 sky, int variant, boolean create) {
		for (Cloud cloud : CLOUDS) {
			double dx = cloud.center.x - sky.x;
			double dz = cloud.center.z - sky.z;
			if (dx * dx + dz * dz < 100.0D && Math.abs(cloud.center.y - sky.y) < 12.0D && cloud.dissolving < 0) {
				cloud.packetAge = cloud.age;
				return cloud;
			}
		}
		if (!create) {
			return null;
		}
		if (CLOUDS.size() >= MAX_CLOUDS) {
			CLOUDS.remove(0);
		}
		Cloud cloud = new Cloud(sky, variant);
		CLOUDS.add(cloud);
		return cloud;
	}

	/** A wide, dark, slowly turning disc of large dust motes; it thins out while dissolving. */
	private static void drawCloud(Cloud cloud) {
		RandomSource random = CommonFx.random();
		float fade = cloud.dissolving < 0 ? 1.0F : 1.0F - (float) cloud.dissolving / DISSOLVE_TICKS;
		int motes = EffectQuality.scale(Math.max(1, Math.round(6 * fade)));
		int[] palette = palette(cloud.variant);
		float radius = cloud.radius * (cloud.dissolving < 0 ? 1.0F : 1.0F + (1.0F - fade) * 0.4F);
		for (int i = 0; i < motes; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double distance = radius * Math.sqrt(random.nextDouble());
			double x = cloud.center.x + Math.cos(angle) * distance;
			double z = cloud.center.z + Math.sin(angle) * distance;
			double y = cloud.center.y + random.nextGaussian() * 0.5D * (1.0D - distance / (radius + 1.0D) * 0.5D);
			// Turning slowly around the centre, a little drawn inwards: the eye of the tribulation.
			double swirl = 0.03D;
			double vx = -Math.sin(angle) * swirl - Math.cos(angle) * 0.004D;
			double vz = Math.cos(angle) * swirl - Math.sin(angle) * 0.004D;
			DustParticleOptions dust = CommonFx.dust(palette[random.nextInt(palette.length)], 3.0F + random.nextFloat());
			CommonFx.add(dust, new Vec3(x, y, z), new Vec3(vx, cloud.dissolving < 0 ? 0.0D : 0.01D, vz), true);
		}
		if (cloud.dissolving >= 0) {
			if (cloud.success && EffectQuality.chance(random, 0.5F)) {
				// Light breaking through the parting clouds.
				double angle = random.nextDouble() * Math.PI * 2.0D;
				double distance = radius * 0.5D * random.nextDouble();
				CommonFx.add(ParticleTypes.END_ROD, new Vec3(cloud.center.x + Math.cos(angle) * distance, cloud.center.y - 0.5D,
						cloud.center.z + Math.sin(angle) * distance), new Vec3(0.0D, -0.35D, 0.0D), true);
			}
			return;
		}
		if (EffectQuality.chance(random, 0.08F)) {
			ParticleOptions smoke = cloud.variant != 1 && random.nextInt(4) == 0 ? ParticleTypes.SQUID_INK : ParticleTypes.LARGE_SMOKE;
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double distance = radius * 0.8D * random.nextDouble();
			CommonFx.add(smoke, new Vec3(cloud.center.x + Math.cos(angle) * distance, cloud.center.y, cloud.center.z + Math.sin(angle) * distance),
					Vec3.ZERO, true);
		}
		if (EffectQuality.chance(random, 0.05F)) {
			// A flicker of lightning inside the clouds.
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double distance = radius * 0.7D * random.nextDouble();
			Vec3 at = new Vec3(cloud.center.x + Math.cos(angle) * distance, cloud.center.y - 0.3D, cloud.center.z + Math.sin(angle) * distance);
			DustParticleOptions glow = CommonFx.dust(boltInner(cloud.variant), 2.0F);
			for (int i = 0; i < 3; i++) {
				CommonFx.add(glow, at.add(random.nextGaussian() * 0.6D, random.nextGaussian() * 0.2D, random.nextGaussian() * 0.6D), Vec3.ZERO, true);
			}
		}
		if (EffectQuality.maximal()) {
			// Two spiral arms turning towards the eye.
			DustParticleOptions arm = CommonFx.dust(palette[0], 2.5F);
			for (int a = 0; a < 2; a++) {
				double t = (cloud.age * 0.05D) % 1.0D;
				double angle = cloud.age * 0.08D + Math.PI * a + t * Math.PI * 2.0D;
				double distance = radius * (1.0D - t);
				CommonFx.add(arm, new Vec3(cloud.center.x + Math.cos(angle) * distance, cloud.center.y - 0.2D, cloud.center.z + Math.sin(angle) * distance),
						Vec3.ZERO, true);
			}
		}
		if (cloud.variant == 1 && EffectQuality.chance(random, 0.06F)) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double distance = radius * 0.8D * random.nextDouble();
			CommonFx.add(ParticleTypes.LAVA, new Vec3(cloud.center.x + Math.cos(angle) * distance, cloud.center.y - 0.5D,
					cloud.center.z + Math.sin(angle) * distance), Vec3.ZERO, true);
		} else if (cloud.variant == 2 && EffectQuality.chance(random, 0.1F)) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double distance = radius * 0.8D * random.nextDouble();
			CommonFx.add(ParticleTypes.REVERSE_PORTAL, new Vec3(cloud.center.x + Math.cos(angle) * distance, cloud.center.y - 0.5D,
					cloud.center.z + Math.sin(angle) * distance), new Vec3(0.0D, -0.05D, 0.0D), true);
		}
	}

	private static int[] palette(int variant) {
		return switch (variant) {
			case 1 -> new int[]{0x2B0F08, 0x4A1A0A, 0x1A0A06, 0x3A140C};
			case 2 -> new int[]{0x1A0B26, 0x2A1240, 0x0E0616, 0x231033};
			default -> new int[]{0x23232B, 0x34343F, 0x18181E, 0x2C2A36};
		};
	}

	// ================================================================== strikes

	private static int boltOuter(int variant) {
		return switch (variant) {
			case 1 -> 0xFF7A1A;
			case 2 -> 0x8A4FE0;
			default -> 0xB57CFF;
		};
	}

	private static int boltInner(int variant) {
		return switch (variant) {
			case 1 -> 0xFFD04A;
			case 2 -> 0xE6D2FF;
			default -> 0xF8F4FF;
		};
	}

	/** One strike from the clouds down to the cultivator's feet. */
	private static void strike(Vec3 sky, Vec3 feet, int variant, boolean last) {
		RandomSource random = CommonFx.random();
		int inner = boltInner(variant);
		int outer = boltOuter(variant);
		CommonFx.bolt(sky, feet, inner, 14);
		CommonFx.bolt(sky.add(random.nextGaussian() * 0.6D, 0.0D, random.nextGaussian() * 0.6D), feet, outer, 12);
		if (last) {
			for (int i = 0; i < 2; i++) {
				Vec3 from = sky.add(random.nextGaussian() * 3.0D, 0.0D, random.nextGaussian() * 3.0D);
				CommonFx.bolt(from, feet, i == 0 ? inner : outer, 14);
			}
		}
		ColorParticleOption flash = ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | inner);
		CommonFx.add(flash, feet.add(0.0D, 1.0D, 0.0D), Vec3.ZERO, true);
		CommonFx.add(flash, sky.add(0.0D, -0.5D, 0.0D), Vec3.ZERO, true);
		if (last) {
			CommonFx.add(flash, feet.add(0.0D, 2.0D, 0.0D), Vec3.ZERO, true);
			CommonFx.add(flash, feet.add(0.0D, 0.3D, 0.0D), Vec3.ZERO, true);
		}
		CommonFx.ring(feet.add(0.0D, 0.1D, 0.0D), outer, last ? 4.0F : 2.5F, last ? 56 : 32);
		CommonFx.burst(feet.add(0.0D, 0.6D, 0.0D), inner, last ? 50 : 26, last ? 3.0F : 2.0F);
		if (last) {
			CommonFx.ring(feet.add(0.0D, 0.6D, 0.0D), inner, 6.0F, 64);
		}
		switch (variant) {
			case 1 -> netherStrike(feet, random, last);
			case 2 -> endStrike(sky, feet, random, last);
			default -> {
				if (EffectQuality.maximal()) {
					CommonFx.ring(feet.add(0.0D, 1.2D, 0.0D), inner, 1.5F, 24);
				}
			}
		}
	}

	/** Columns of flame and soul fire around the cultivator, lava sparks at the feet. */
	private static void netherStrike(Vec3 feet, RandomSource random, boolean last) {
		int column = EffectQuality.scale(last ? 50 : 30);
		for (int i = 0; i < column; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double radius = 0.4D + random.nextDouble() * 0.5D;
			double y = feet.y + random.nextDouble() * 7.0D;
			ParticleOptions flame = random.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME;
			CommonFx.add(flame, new Vec3(feet.x + Math.cos(angle) * radius, y, feet.z + Math.sin(angle) * radius),
					new Vec3(0.0D, 0.08D + random.nextDouble() * 0.1D, 0.0D), false);
		}
		int lava = EffectQuality.scale(last ? 14 : 8);
		for (int i = 0; i < lava; i++) {
			CommonFx.add(ParticleTypes.LAVA, feet.x, feet.y + 0.2D, feet.z, 0.0D, 0.0D, 0.0D);
		}
	}

	/** End rods along the bolt, portal motes around the feet, dragon's breath and a void pulse. */
	private static void endStrike(Vec3 sky, Vec3 feet, RandomSource random, boolean last) {
		Vec3 delta = feet.subtract(sky);
		int rods = EffectQuality.scale(18);
		for (int i = 0; i < rods; i++) {
			Vec3 at = sky.add(delta.scale(random.nextDouble()));
			CommonFx.add(ParticleTypes.END_ROD, at, new Vec3(random.nextGaussian() * 0.02D, 0.0D, random.nextGaussian() * 0.02D), false);
		}
		int portal = EffectQuality.scale(last ? 50 : 30);
		for (int i = 0; i < portal; i++) {
			ParticleOptions type = random.nextBoolean() ? ParticleTypes.PORTAL : ParticleTypes.REVERSE_PORTAL;
			CommonFx.add(type, feet.x + random.nextGaussian() * 0.8D, feet.y + random.nextDouble() * 2.0D, feet.z + random.nextGaussian() * 0.8D,
					random.nextGaussian() * 0.3D, random.nextDouble() * 0.2D, random.nextGaussian() * 0.3D);
		}
		PowerParticleOption breath = PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F);
		int breaths = EffectQuality.scale(last ? 24 : 14);
		for (int i = 0; i < breaths; i++) {
			double angle = Math.PI * 2.0D * i / breaths;
			CommonFx.add(breath, feet.x + Math.cos(angle) * 1.2D, feet.y + 0.2D, feet.z + Math.sin(angle) * 1.2D,
					Math.cos(angle) * 0.05D, 0.01D, Math.sin(angle) * 0.05D);
		}
		// The void pulse: dark rings pushing outwards (purely visual, nothing is moved).
		CommonFx.ring(feet.add(0.0D, 0.2D, 0.0D), 0x2A0F45, 3.0F, 36);
		CommonFx.ring(feet.add(0.0D, 0.2D, 0.0D), 0x14061F, 4.5F, 44);
	}

	// ================================================================== end and protectors

	/** The clouds part: a soft column of light falls on the survivor. */
	private static void heavenlyLight(Vec3 feet, Vec3 sky) {
		RandomSource random = CommonFx.random();
		int rays = EffectQuality.scale(24);
		for (int i = 0; i < rays; i++) {
			Vec3 at = new Vec3(feet.x + random.nextGaussian() * 0.8D, sky.y - random.nextDouble() * 4.0D, feet.z + random.nextGaussian() * 0.8D);
			CommonFx.add(ParticleTypes.END_ROD, at, new Vec3(0.0D, -0.4D - random.nextDouble() * 0.3D, 0.0D), true);
		}
		CommonFx.beam(sky, feet, 0xFFF4C8, 0.4F);
	}

	/** A monster drawn by the phenomenon arrives (smoke, a red ring, souls) or vanishes in a poof. */
	private static void protector(Vec3 pos, int variant, boolean vanish) {
		RandomSource random = CommonFx.random();
		if (vanish) {
			int poofs = EffectQuality.scale(10);
			for (int i = 0; i < poofs; i++) {
				CommonFx.add(ParticleTypes.POOF, pos.x + random.nextGaussian() * 0.3D, pos.y + 0.5D + random.nextDouble(), pos.z + random.nextGaussian() * 0.3D,
						random.nextGaussian() * 0.03D, 0.04D, random.nextGaussian() * 0.03D);
			}
			CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), 0x303030, 10, 0.8F);
			return;
		}
		int smoke = EffectQuality.scale(6);
		for (int i = 0; i < smoke; i++) {
			CommonFx.add(ParticleTypes.LARGE_SMOKE, pos.x + random.nextGaussian() * 0.3D, pos.y + random.nextDouble() * 1.5D,
					pos.z + random.nextGaussian() * 0.3D, 0.0D, 0.05D, 0.0D);
		}
		CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), variant == 2 ? 0x3A1060 : 0x5A0000, 0.9F, 20);
		int souls = EffectQuality.scale(6);
		ParticleOptions soul = variant == 2 ? ParticleTypes.PORTAL : ParticleTypes.SOUL;
		for (int i = 0; i < souls; i++) {
			CommonFx.add(soul, pos.x + random.nextGaussian() * 0.4D, pos.y + random.nextDouble(), pos.z + random.nextGaussian() * 0.4D,
					0.0D, 0.06D, 0.0D);
		}
	}

	private TribulationFx() {
	}
}
