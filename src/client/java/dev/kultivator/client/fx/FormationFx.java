package dev.kultivator.client.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Protective formation effects. The server refreshes every switched-on formation near us every 2 s
 * ({@code PROTECTIVE_FORMATION} with a = 1); each refresh keeps a dome in {@link #DOMES}, drawn every tick until 3 s
 * after the last packet: a faint hemisphere of slowly drifting jade motes with a rotating meridian, a ground ring with
 * three bright runners, and qi lines from the four Crying Obsidian pillars into the lodestone. One-shot effects:
 * a = 1 with b = 1 the awakening (a jade ring rising from the ground to the apex), b = 2 a shard charging it; a = 0 the
 * dome collapsing; a = 2 / 3 a missing (red) / present (jade) pillar marker; a = 4 a monster driven back at pos.
 */
public final class FormationFx {

	private static final int JADE = 0x5FE3A1;
	private static final int PALE = 0xC4FBE0;
	private static final int DEEP = 0x2BB673;
	private static final int GOLD = 0xFFE08A;
	private static final int RED = 0xFF4B4B;
	private static final int ASH = 0x8C9C94;
	/** A dome without a refresh for this many ticks is gone (unloaded, out of range). */
	private static final int EXPIRE_TICKS = 60;
	/** Domes are drawn while the camera is within radius + this. */
	private static final double VIEW_MARGIN = 40.0D;
	/** The vanilla particle limiter drops ordinary particles farther than this from the camera. */
	private static final double PARTICLE_RANGE_SQ = 32.0D * 32.0D;
	private static final int MAX_ANIMATIONS = 64;
	private static final int[][] PILLARS = {{4, 0}, {-4, 0}, {0, 4}, {0, -4}};

	private static final Map<BlockPos, Dome> DOMES = new HashMap<>();
	private static final List<Animation> ANIMATIONS = new ArrayList<>();
	private static ClientLevel lastLevel;
	private static long clock;

	/**
	 * A switched-on formation: lodestone centre, radius and when the server last mentioned it; for a Formasi Agung Sekte
	 * (DESIGN 20.3.2) also its restriction layers (0 for a plain formation) and whether we own it.
	 */
	private static final class Dome {
		final Vec3 centre;
		float radius;
		long lastSeen;
		int layers;
		boolean owner;

		Dome(Vec3 centre, float radius) {
			this.centre = centre;
			this.radius = radius;
		}
	}

	/** Restriction layer tints of a Formasi Agung: 2 azure, 3 violet, 4 crimson-gold. */
	private static final int[] LAYER_TINT = {JADE, JADE, 0x6FB8FF, 0xB98CFF, 0xFF7A4A};

	private enum Kind {
		RISE, COLLAPSE, MISSING, PRESENT
	}

	/** A short effect played over several ticks. */
	private static final class Animation {
		final Kind kind;
		final Vec3 pos;
		final float radius;
		final int life;
		int age;

		Animation(Kind kind, Vec3 pos, float radius, int life) {
			this.kind = kind;
			this.pos = pos;
			this.radius = radius;
			this.life = life;
		}
	}

	public static void play(FxType type, Payloads.Fx fx) {
		if (type != FxType.PROTECTIVE_FORMATION) {
			return;
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		float radius = fx.f() > 0.5F ? Math.min(fx.f(), 64.0F) : 16.0F;
		switch (fx.a()) {
			case 1 -> {
				Dome dome = DOMES.computeIfAbsent(BlockPos.containing(pos), key -> new Dome(pos, radius));
				dome.radius = radius;
				dome.lastSeen = clock;
				dome.layers = fx.b() >> 4 & 0xF;
				dome.owner = (fx.b() & 1 << 8) != 0;
				int flourish = fx.b() & 0xF;
				if (flourish == 1) {
					awakening(pos, radius);
				} else if (flourish == 2) {
					charge(pos);
				}
			}
			case 0 -> {
				DOMES.remove(BlockPos.containing(pos));
				collapse(pos, radius);
			}
			case 2 -> animate(new Animation(Kind.MISSING, pos, 1.0F, 60));
			case 3 -> animate(new Animation(Kind.PRESENT, pos, 1.0F, 40));
			case 4 -> ripple(pos, new Vec3(fx.tx(), fx.ty(), fx.tz()));
			default -> {
			}
		}
	}

	/** Every client tick (only while in a world and not paused). */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		if (level != lastLevel) {
			lastLevel = level;
			DOMES.clear();
			ANIMATIONS.clear();
		}
		clock++;
		RandomSource random = level.getRandom();
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		Iterator<Dome> domes = DOMES.values().iterator();
		while (domes.hasNext()) {
			Dome dome = domes.next();
			if (clock - dome.lastSeen > EXPIRE_TICKS) {
				domes.remove();
				continue;
			}
			double view = dome.radius + VIEW_MARGIN;
			if (dome.centre.distanceToSqr(camera) <= view * view) {
				drawDome(dome, random, camera);
				if (dome.layers >= 2) {
					drawLayers(dome, level, random, camera);
				}
			}
		}
		Iterator<Animation> animations = ANIMATIONS.iterator();
		while (animations.hasNext()) {
			Animation animation = animations.next();
			drawAnimation(animation, random);
			if (++animation.age > animation.life) {
				animations.remove();
			}
		}
	}

	// ================================================================ the dome

	private static void drawDome(Dome dome, RandomSource random, Vec3 camera) {
		Vec3 centre = dome.centre;
		double radius = dome.radius;
		double ground = centre.y - 0.5D;

		// A hemisphere of drifting motes: a uniform height on a sphere gives a uniform spread over its surface.
		int motes = EffectQuality.scale(5);
		for (int i = 0; i < motes; i++) {
			double height = random.nextDouble() * radius;
			double ring = Math.sqrt(Math.max(0.0D, radius * radius - height * height));
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double x = centre.x + Math.cos(angle) * ring;
			double y = ground + height;
			double z = centre.z + Math.sin(angle) * ring;
			if (camera.distanceToSqr(x, y, z) > PARTICLE_RANGE_SQ) {
				continue;
			}
			double drift = 0.012D;
			CommonFx.add(CommonFx.dust(random.nextInt(3) == 0 ? PALE : JADE, 0.6F + random.nextFloat() * 0.4F), x, y, z,
					-Math.sin(angle) * drift, 0.004D, Math.cos(angle) * drift);
		}

		// A meridian sweeping slowly around the dome (two crossed ones at Maximal).
		if (!EffectQuality.minimal() && clock % 4 == 0) {
			double theta = clock * 0.008D;
			meridian(centre, ground, radius, theta, random, camera);
			if (EffectQuality.maximal()) {
				meridian(centre, ground, radius, theta + Math.PI / 2.0D, random, camera);
			}
		}

		// Ground ring: bright runners circling, plus scattered motes on the rest of the circle.
		int runners = EffectQuality.minimal() ? 1 : 3;
		DustParticleOptions runner = CommonFx.dust(PALE, 1.1F);
		for (int j = 0; j < runners; j++) {
			double angle = clock * 0.03D + j * Math.PI * 2.0D / runners;
			CommonFx.add(runner, centre.x + Math.cos(angle) * radius, ground + 0.1D, centre.z + Math.sin(angle) * radius, 0.0D, 0.01D, 0.0D);
		}
		if (EffectQuality.chance(random, 0.8F)) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(CommonFx.dust(DEEP, 0.8F), centre.x + Math.cos(angle) * radius, ground + 0.1D, centre.z + Math.sin(angle) * radius,
					0.0D, 0.0D, 0.0D);
		}

		// Qi lines from the four pillars into the lodestone.
		Vec3 top = new Vec3(centre.x, centre.y + 0.6D, centre.z);
		boolean trails = clock % 8 == 0;
		boolean beams = clock % 20 == 0;
		for (int[] offset : PILLARS) {
			Vec3 pillar = new Vec3(centre.x + offset[0], centre.y + 0.6D, centre.z + offset[1]);
			if (trails && EffectQuality.chance(random, 0.7F)) {
				Vec3 from = pillar.add((random.nextFloat() - 0.5F) * 0.4D, random.nextFloat() * 0.3D, (random.nextFloat() - 0.5F) * 0.4D);
				CommonFx.add(new TrailParticleOption(top, JADE, 16 + random.nextInt(8)), from, Vec3.ZERO, false);
			}
			if (beams) {
				int n = EffectQuality.scale(6);
				Vec3 delta = top.subtract(pillar);
				double offsetT = random.nextDouble() / n;
				DustParticleOptions dust = CommonFx.dust(JADE, 0.45F);
				for (int i = 0; i < n; i++) {
					Vec3 at = pillar.add(delta.scale(offsetT + (double) i / n));
					CommonFx.add(dust, at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
				}
			}
		}

		// The lodestone heart glows.
		if (EffectQuality.chance(random, 0.25F)) {
			CommonFx.add(ParticleTypes.GLOW, top.add((random.nextFloat() - 0.5F) * 0.5D, 0.0D, (random.nextFloat() - 0.5F) * 0.5D),
					new Vec3(0.0D, 0.03D, 0.0D), false);
		}
		if (EffectQuality.maximal() && random.nextInt(3) == 0) {
			double height = random.nextDouble() * radius;
			double ring = Math.sqrt(Math.max(0.0D, radius * radius - height * height));
			double angle = random.nextDouble() * Math.PI * 2.0D;
			Vec3 at = new Vec3(centre.x + Math.cos(angle) * ring, ground + height, centre.z + Math.sin(angle) * ring);
			if (camera.distanceToSqr(at) <= PARTICLE_RANGE_SQ) {
				CommonFx.add(ParticleTypes.END_ROD, at, new Vec3(0.0D, -0.01D, 0.0D), false);
			}
		}
	}

	/**
	 * The restriction layers of a Formasi Agung Sekte (DESIGN 20.3.2): a tinted second skin of motes (layer 2+), a bright
	 * band at the edge (layer 3+), crimson-gold sparks and, for its owner only, outlines around the monsters inside
	 * (layer 4; particles, never the Glowing effect).
	 */
	private static void drawLayers(Dome dome, ClientLevel level, RandomSource random, Vec3 camera) {
		int layers = Math.min(4, dome.layers);
		int tint = LAYER_TINT[layers];
		Vec3 centre = dome.centre;
		double radius = dome.radius;
		double ground = centre.y - 0.5D;
		int motes = EffectQuality.scale(layers);
		for (int i = 0; i < motes; i++) {
			double height = random.nextDouble() * radius * 0.96D;
			double ring = Math.sqrt(Math.max(0.0D, radius * radius * 0.92D - height * height));
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double x = centre.x + Math.cos(angle) * ring;
			double y = ground + height;
			double z = centre.z + Math.sin(angle) * ring;
			if (camera.distanceToSqr(x, y, z) <= PARTICLE_RANGE_SQ) {
				CommonFx.add(CommonFx.dust(tint, 0.55F), x, y, z, 0.0D, 0.003D, 0.0D);
			}
		}
		if (layers >= 3 && clock % 3 == 0) {
			int n = EffectQuality.scale(4);
			DustParticleOptions band = CommonFx.dust(tint, 0.9F);
			for (int i = 0; i < n; i++) {
				double angle = -clock * 0.02D + Math.PI * 2.0D * i / n;
				CommonFx.add(band, centre.x + Math.cos(angle) * radius, ground + 0.6D, centre.z + Math.sin(angle) * radius, 0.0D, 0.0D, 0.0D);
			}
		}
		if (layers >= 4) {
			if (EffectQuality.chance(random, 0.3F)) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				CommonFx.add(ParticleTypes.CRIMSON_SPORE, centre.x + Math.cos(angle) * radius * 0.9D, ground + random.nextDouble() * 2.0D,
						centre.z + Math.sin(angle) * radius * 0.9D, 0.0D, 0.01D, 0.0D);
			}
			if (dome.owner && clock % 10 == 0) {
				double radiusSq = radius * radius;
				int shown = 0;
				for (net.minecraft.world.entity.Mob mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
						new net.minecraft.world.phys.AABB(centre, centre).inflate(radius),
						mob -> mob instanceof net.minecraft.world.entity.monster.Enemy && mob.isAlive() && mob.distanceToSqr(centre) <= radiusSq)) {
					if (shown++ >= 16) {
						break;
					}
					markMonster(mob.position(), mob.getBbWidth(), mob.getBbHeight());
				}
			}
		}
	}

	/** Gold corner posts and a crimson ring around a monster's box. */
	private static void markMonster(Vec3 feet, float width, float height) {
		double half = width / 2.0D + 0.15D;
		DustParticleOptions gold = CommonFx.dust(GOLD, 0.6F);
		DustParticleOptions red = CommonFx.dust(RED, 0.7F);
		int steps = Math.max(2, EffectQuality.scale(3));
		for (int corner = 0; corner < 4; corner++) {
			double cx = feet.x + (corner < 2 ? -half : half);
			double cz = feet.z + (corner % 2 == 0 ? -half : half);
			for (int i = 0; i <= steps; i++) {
				CommonFx.add(gold, cx, feet.y + height * i / steps, cz, 0.0D, 0.0D, 0.0D);
			}
		}
		int ring = EffectQuality.scale(8);
		for (int i = 0; i < ring; i++) {
			double angle = Math.PI * 2.0D * i / ring;
			CommonFx.add(red, feet.x + Math.cos(angle) * half * 1.3D, feet.y + 0.1D, feet.z + Math.sin(angle) * half * 1.3D, 0.0D, 0.0D, 0.0D);
		}
	}

	/** Half a great circle from the ground over the apex to the other side, in the vertical plane at {@code theta}. */
	private static void meridian(Vec3 centre, double ground, double radius, double theta, RandomSource random, Vec3 camera) {
		int n = EffectQuality.scale(8);
		DustParticleOptions dust = CommonFx.dust(PALE, 0.7F);
		double cos = Math.cos(theta);
		double sin = Math.sin(theta);
		for (int i = 0; i < n; i++) {
			double t = (i + random.nextDouble()) / n * Math.PI;
			double across = Math.cos(t) * radius;
			double x = centre.x + cos * across;
			double y = ground + Math.sin(t) * radius;
			double z = centre.z + sin * across;
			if (camera.distanceToSqr(x, y, z) <= PARTICLE_RANGE_SQ) {
				CommonFx.add(dust, x, y, z, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	// ================================================================ one-shot effects

	private static void animate(Animation animation) {
		if (ANIMATIONS.size() >= MAX_ANIMATIONS) {
			ANIMATIONS.remove(0);
		}
		ANIMATIONS.add(animation);
	}

	/** Switched on: a pillar of light, beams from the pillars, a ground ring, then a ring rising up the dome. */
	private static void awakening(Vec3 centre, float radius) {
		Vec3 top = new Vec3(centre.x, centre.y + 0.6D, centre.z);
		CommonFx.pillar(top, PALE, 8.0F);
		CommonFx.burst(top, JADE, 30, 2.0F);
		for (int[] offset : PILLARS) {
			CommonFx.beam(new Vec3(centre.x + offset[0], centre.y + 0.6D, centre.z + offset[1]), top, JADE, 0.15F);
		}
		CommonFx.ring(new Vec3(centre.x, centre.y - 0.45D, centre.z), JADE, radius, 96);
		animate(new Animation(Kind.RISE, centre, radius, 24));
		if (EffectQuality.maximal()) {
			RandomSource random = CommonFx.random();
			for (int i = 0; i < 24; i++) {
				CommonFx.add(ParticleTypes.END_ROD, top, new Vec3(random.nextGaussian() * 0.06D, 0.15D + random.nextFloat() * 0.2D,
						random.nextGaussian() * 0.06D), false);
			}
		}
	}

	/** An Amethyst Shard feeds a running formation. */
	private static void charge(Vec3 centre) {
		Vec3 top = new Vec3(centre.x, centre.y + 0.6D, centre.z);
		CommonFx.spiralIn(top, GOLD, 1.6F, 14, clock);
		CommonFx.ring(new Vec3(centre.x, centre.y - 0.45D, centre.z), GOLD, 1.6F, 20);
		RandomSource random = CommonFx.random();
		for (int i = 0; i < EffectQuality.scale(4); i++) {
			CommonFx.add(ParticleTypes.GLOW, top, new Vec3(random.nextGaussian() * 0.05D, 0.05D + random.nextFloat() * 0.05D,
					random.nextGaussian() * 0.05D), false);
		}
	}

	/** Switched off: grey motes fall, smoke puffs from the lodestone and a ring sinks from the apex. */
	private static void collapse(Vec3 centre, float radius) {
		Vec3 top = new Vec3(centre.x, centre.y + 0.6D, centre.z);
		RandomSource random = CommonFx.random();
		CommonFx.burst(top, ASH, 16, 1.2F);
		for (int i = 0; i < EffectQuality.scale(6); i++) {
			CommonFx.add(ParticleTypes.SMOKE, top, new Vec3(random.nextGaussian() * 0.02D, 0.04D, random.nextGaussian() * 0.02D), false);
		}
		CommonFx.ring(new Vec3(centre.x, centre.y - 0.45D, centre.z), ASH, radius, 64);
		animate(new Animation(Kind.COLLAPSE, centre, radius, 20));
	}

	/** A monster hits the barrier: jade motes spray outward from it. */
	private static void ripple(Vec3 pos, Vec3 centre) {
		RandomSource random = CommonFx.random();
		Vec3 out = new Vec3(pos.x - centre.x, 0.0D, pos.z - centre.z);
		out = out.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : out.normalize();
		int n = EffectQuality.scale(8);
		for (int i = 0; i < n; i++) {
			Vec3 at = pos.add((random.nextFloat() - 0.5F) * 0.8D, (random.nextFloat() - 0.5F) * 0.8D, (random.nextFloat() - 0.5F) * 0.8D);
			CommonFx.add(CommonFx.dust(random.nextBoolean() ? PALE : JADE, 0.9F), at,
					out.scale(0.12D + random.nextFloat() * 0.1D).add(0.0D, random.nextFloat() * 0.05D, 0.0D), false);
		}
		if (EffectQuality.chance(random, 0.6F)) {
			CommonFx.add(ParticleTypes.GLOW, pos, out.scale(0.08D), false);
		}
	}

	private static void drawAnimation(Animation animation, RandomSource random) {
		Vec3 pos = animation.pos;
		switch (animation.kind) {
			case RISE, COLLAPSE -> {
				double progress = Math.min(1.0D, (double) animation.age / animation.life);
				boolean rise = animation.kind == Kind.RISE;
				double radius = animation.radius;
				double height = radius * (rise ? progress : 1.0D - progress);
				double ring = Math.sqrt(Math.max(0.0D, radius * radius - height * height));
				int n = EffectQuality.scale(Math.max(6, (int) Math.round(ring * (rise ? 3.0D : 2.0D))));
				DustParticleOptions dust = CommonFx.dust(rise ? PALE : ASH, rise ? 1.1F : 0.9F);
				double y = pos.y - 0.5D + height;
				double spin = random.nextDouble() * Math.PI * 2.0D;
				for (int i = 0; i < n; i++) {
					double angle = spin + Math.PI * 2.0D * i / n;
					CommonFx.add(dust, pos.x + Math.cos(angle) * ring, y, pos.z + Math.sin(angle) * ring, 0.0D, rise ? 0.0D : -0.02D, 0.0D);
				}
			}
			case MISSING, PRESENT -> {
				if (animation.age % 5 != 0) {
					return;
				}
				boolean missing = animation.kind == Kind.MISSING;
				outline(pos, missing ? RED : JADE, missing ? 3 : 2);
				if (missing) {
					DustParticleOptions dust = CommonFx.dust(RED, 1.0F);
					for (int i = 0; i < EffectQuality.scale(4); i++) {
						CommonFx.add(dust, pos.x + random.nextGaussian() * 0.08D, pos.y + 0.6D + random.nextFloat() * 2.5D,
								pos.z + random.nextGaussian() * 0.08D, 0.0D, 0.02D, 0.0D);
					}
				} else if (animation.age == 0) {
					CommonFx.add(ParticleTypes.GLOW, pos.add(0.0D, 0.6D, 0.0D), new Vec3(0.0D, 0.04D, 0.0D), false);
				}
			}
		}
	}

	/** Motes along the 12 edges of the block centred at {@code centre}. */
	private static void outline(Vec3 centre, int rgb, int perEdge) {
		int n = Math.max(1, EffectQuality.scale(perEdge));
		DustParticleOptions dust = CommonFx.dust(rgb, 0.7F);
		double h = 0.52D;
		double[] signs = {-h, h};
		for (double a : signs) {
			for (double b : signs) {
				for (int i = 0; i <= n; i++) {
					double t = -h + 2.0D * h * i / n;
					CommonFx.add(dust, centre.x + t, centre.y + a, centre.z + b, 0.0D, 0.0D, 0.0D);
					CommonFx.add(dust, centre.x + a, centre.y + t, centre.z + b, 0.0D, 0.0D, 0.0D);
					CommonFx.add(dust, centre.x + a, centre.y + b, centre.z + t, 0.0D, 0.0D, 0.0D);
				}
			}
		}
	}

	/** Disconnect: drops the old world (the next world's first tick would only do it after it loads). */
	static void forget() {
		lastLevel = null;
		DOMES.clear();
		ANIMATIONS.clear();
	}

	private FormationFx() {
	}
}
