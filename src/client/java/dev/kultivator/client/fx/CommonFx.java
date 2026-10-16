package dev.kultivator.client.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Simple building blocks for effects, usable by every client FX class: bursts, rings, beams, pillars, spirals. All
 * counts are given for Normal quality and scaled by {@link EffectQuality}.
 */
public final class CommonFx {

	public static void play(FxType type, Payloads.Fx fx) {
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		switch (type) {
			case BURST -> burst(pos, fx.a(), Math.max(1, fx.b()), fx.f());
			case RING -> ring(pos, fx.a(), fx.f(), 48);
			case BEAM -> beam(pos, target, fx.a(), Math.max(0.05F, fx.f()));
			case PILLAR -> pillar(pos, fx.a(), fx.f());
			default -> {
			}
		}
	}

	public static ClientLevel level() {
		return Minecraft.getInstance().level;
	}

	public static RandomSource random() {
		ClientLevel level = level();
		return level == null ? RandomSource.create() : level.getRandom();
	}

	/** A coloured dust mote (scale 0.5..4). */
	public static DustParticleOptions dust(int rgb, float scale) {
		return new DustParticleOptions(rgb & 0xFFFFFF, Mth.clamp(scale, 0.05F, 4.0F));
	}

	/** Adds one particle; always shown (not culled by the vanilla particle setting) when {@code important}. */
	public static void add(ParticleOptions particle, Vec3 pos, Vec3 velocity, boolean important) {
		ClientLevel level = level();
		if (level == null) {
			return;
		}
		level.addParticle(particle, important, important, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
	}

	public static void add(ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
		ClientLevel level = level();
		if (level != null) {
			level.addParticle(particle, x, y, z, vx, vy, vz);
		}
	}

	public static void burst(Vec3 pos, int rgb, int count, float radius) {
		RandomSource random = random();
		int n = EffectQuality.scale(count);
		DustParticleOptions dust = dust(rgb, 1.2F);
		for (int i = 0; i < n; i++) {
			Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
			Vec3 at = pos.add(dir.scale(radius * random.nextFloat() * 0.3F));
			add(dust, at, dir.scale(0.05D + radius * 0.04D), false);
		}
		if (EffectQuality.maximal()) {
			for (int i = 0; i < n / 4; i++) {
				add(ParticleTypes.END_ROD, pos, new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.08D), false);
			}
		}
	}

	/** A horizontal ring of radius {@code radius} at pos ({@code points} at Normal quality). */
	public static void ring(Vec3 pos, int rgb, float radius, int points) {
		int n = EffectQuality.scale(points);
		DustParticleOptions dust = dust(rgb, 1.0F);
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / n;
			add(dust, pos.x + Math.cos(angle) * radius, pos.y + 0.05D, pos.z + Math.sin(angle) * radius, 0.0D, 0.0D, 0.0D);
		}
	}

	/** Motes along a straight line, about 4 per block at Normal quality. */
	public static void beam(Vec3 from, Vec3 to, int rgb, float width) {
		RandomSource random = random();
		Vec3 delta = to.subtract(from);
		double length = delta.length();
		int n = Math.min(400, EffectQuality.scale((int) Math.ceil(length * 4.0D)));
		DustParticleOptions dust = dust(rgb, 0.9F + width);
		for (int i = 0; i <= n; i++) {
			Vec3 at = from.add(delta.scale((double) i / Math.max(1, n)));
			add(dust, at.x + (random.nextFloat() - 0.5F) * width, at.y + (random.nextFloat() - 0.5F) * width,
					at.z + (random.nextFloat() - 0.5F) * width, 0.0D, 0.0D, 0.0D);
		}
	}

	/** A column rising from pos up to {@code height}. */
	public static void pillar(Vec3 pos, int rgb, float height) {
		RandomSource random = random();
		int n = EffectQuality.scale((int) Math.ceil(height * 6.0F));
		DustParticleOptions dust = dust(rgb, 1.4F);
		for (int i = 0; i < n; i++) {
			double y = pos.y + random.nextFloat() * height;
			add(dust, pos.x + random.nextGaussian() * 0.15D, y, pos.z + random.nextGaussian() * 0.15D, 0.0D, 0.04D, 0.0D);
		}
	}

	/** A jagged lightning path from {@code from} to {@code to} (particles only), with branches at Maximal quality. */
	public static void bolt(Vec3 from, Vec3 to, int rgb, int segments) {
		RandomSource random = random();
		Vec3 previous = from;
		Vec3 delta = to.subtract(from);
		for (int i = 1; i <= segments; i++) {
			double t = (double) i / segments;
			double jitter = i == segments ? 0.0D : 0.9D;
			Vec3 next = from.add(delta.scale(t)).add((random.nextFloat() - 0.5F) * jitter * 2.0D, 0.0D, (random.nextFloat() - 0.5F) * jitter * 2.0D);
			beam(previous, next, rgb, 0.08F);
			if (EffectQuality.maximal() && random.nextInt(4) == 0) {
				Vec3 branch = next.add((random.nextFloat() - 0.5F) * 3.0D, -random.nextFloat() * 2.0D, (random.nextFloat() - 0.5F) * 3.0D);
				beam(next, branch, rgb, 0.04F);
			}
			previous = next;
		}
		for (int i = 0; i < EffectQuality.scale(6); i++) {
			add(ParticleTypes.ELECTRIC_SPARK, to.x, to.y + 0.2D, to.z, random.nextGaussian() * 0.3D, random.nextFloat() * 0.3D,
					random.nextGaussian() * 0.3D);
		}
	}

	/** Motes spiralling inwards to {@code center} from radius {@code radius}. */
	public static void spiralIn(Vec3 center, int rgb, float radius, int count, float time) {
		RandomSource random = random();
		int n = EffectQuality.scale(count);
		DustParticleOptions dust = dust(rgb, 0.8F);
		for (int i = 0; i < n; i++) {
			double angle = time * 0.25D + Math.PI * 2.0D * i / n + random.nextFloat() * 0.3D;
			double r = radius * (0.6D + random.nextFloat() * 0.4D);
			Vec3 at = center.add(Math.cos(angle) * r, (random.nextFloat() - 0.3F) * 1.2D, Math.sin(angle) * r);
			Vec3 velocity = center.subtract(at).scale(0.08D);
			add(dust, at, velocity, false);
		}
	}

	private CommonFx() {
	}
}
