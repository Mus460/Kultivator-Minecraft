package dev.kultivator.client.purba;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.client.fx.spec.FxBudget;
import dev.kultivator.network.Payloads;
import dev.kultivator.purba.HeavenEnvoyEntity;
import dev.kultivator.purba.PurbaFxIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * The PURBA effects (DESIGN 26.14 item 3, owned by P9-purba; sub-ids in {@code purba.PurbaFxIds}), routed by
 * {@code FxDispatcher}: closing telegraph rings and lines, skill hits, the Kun's wave, the Peng's wing shadow, Zhulong's gaze
 * and breaths, Taotie's inhale, the Celah glint, the candle flare, the whirlwind, the omen silhouette, the calamity strikes
 * (particle bolts from the sky, never a {@code LightningBolt}), the envoys' columns of light and their halos (≤ 8 within 32
 * blocks, through {@link FxBudget}), and the sky grades of Zhulong's night and the Heavens' gaze through the 7b
 * {@link SkyDirector} (client colour only: no time or weather change). Particles only; the server plays the sounds.
 */
public final class PurbaFx {

	private static final double DRAW_RANGE = 128.0D;
	private static final int MAX_TELEGRAPHS = 24;
	private static final int SKY_PRIORITY = 25;
	private static final double HALO_RANGE = 32.0D;
	private static final int MAX_HALOS = 8;

	/** A ring or a line drawn for its lifetime. */
	private static final class Telegraph {
		final boolean line;
		final Vec3 a;
		final Vec3 b;
		final float radius;
		final int color;
		final int life;
		int age;

		Telegraph(boolean line, Vec3 a, Vec3 b, float radius, int color, int life) {
			this.line = line;
			this.a = a;
			this.b = b;
			this.radius = radius;
			this.color = color;
			this.life = Math.max(1, life);
		}
	}

	private static final List<Telegraph> TELEGRAPHS = new ArrayList<>();
	private static final List<HeavenEnvoyEntity> ENVOYS = new ArrayList<>();
	private static ClientLevel lastLevel;
	private static int scanTimer;
	private static boolean nightOn;
	private static boolean wrathOn;

	/** One {@code FxType.PURBA} packet; {@code a} = the sub-id. */
	public static void play(Payloads.Fx fx) {
		ClientLevel level = CommonFx.level();
		Minecraft minecraft = Minecraft.getInstance();
		if (level == null || minecraft.player == null) {
			return;
		}
		sync(level);
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		RandomSource random = level.getRandom();
		int rgb = fx.b() & 0xFFFFFF;
		switch (fx.a()) {
			case PurbaFxIds.NIGHT -> {
				if (fx.b() == 1) {
					nightOn = true;
					SkyDirector.grade(SkyDirector.Grade.SK_STARS, 0.8F, 0.55F, 40, SKY_PRIORITY, pos, false, 1.0F, pos.y);
				} else if (nightOn) {
					nightOn = false;
					SkyDirector.release(40, SKY_PRIORITY);
				}
				return;
			}
			case PurbaFxIds.WRATH_SKY -> {
				if (fx.b() == 1) {
					wrathOn = true;
					SkyDirector.grade(SkyDirector.Grade.SK_GOLD, 0.65F, 0.1F, 60, SKY_PRIORITY, pos, true, 0.6F, pos.y);
				} else if (wrathOn) {
					wrathOn = false;
					SkyDirector.release(60, SKY_PRIORITY);
				}
				return;
			}
			default -> {
			}
		}
		if (minecraft.player.position().distanceToSqr(pos) > DRAW_RANGE * DRAW_RANGE) {
			return;
		}
		switch (fx.a()) {
			case PurbaFxIds.RING -> add(new Telegraph(false, pos, pos, Math.max(0.5F, fx.f()), rgb, (int) Math.round(fx.tx())));
			case PurbaFxIds.LINE -> add(new Telegraph(true, pos, target, 1.0F, rgb, Math.max(4, Math.round(fx.f()))));
			case PurbaFxIds.HIT -> {
				float radius = Math.max(0.5F, fx.f());
				CommonFx.burst(pos.add(0.0D, 0.6D, 0.0D), rgb, 18, radius);
				CommonFx.ring(pos, rgb, radius, 18);
			}
			case PurbaFxIds.WAVE -> {
				float radius = Math.max(1.0F, fx.f());
				for (int i = 0; i < 3; i++) {
					CommonFx.ring(pos.add(0.0D, 0.2D + i * 0.4D, 0.0D), 0x4A8BE8, radius * (0.6F + 0.2F * i), 40);
				}
				int splash = EffectQuality.scale(30);
				for (int i = 0; i < splash; i++) {
					double angle = random.nextDouble() * Math.PI * 2.0D;
					double r = radius * random.nextDouble();
					CommonFx.add(ParticleTypes.SPLASH, pos.add(Math.cos(angle) * r, 0.3D, Math.sin(angle) * r), new Vec3(0.0D, 0.3D, 0.0D), false);
					CommonFx.add(ParticleTypes.CLOUD, pos.add(Math.cos(angle) * r, 0.5D, Math.sin(angle) * r), new Vec3(0.0D, 0.08D, 0.0D), false);
				}
			}
			case PurbaFxIds.WING_SHADOW -> {
				float radius = Math.max(1.0F, fx.f());
				int motes = EffectQuality.scale(40);
				for (int i = 0; i < motes; i++) {
					double angle = random.nextDouble() * Math.PI * 2.0D;
					double r = radius * Math.sqrt(random.nextDouble());
					CommonFx.add(ParticleTypes.LARGE_SMOKE, pos.add(Math.cos(angle) * r, 0.3D, Math.sin(angle) * r), new Vec3(0.0D, 0.01D, 0.0D), false);
				}
				CommonFx.ring(pos, 0x20304A, radius, 36);
			}
			case PurbaFxIds.GAZE -> cone(pos, target, Math.max(5.0F, fx.f()), 0xFFC040, ParticleTypes.FLAME, random);
			case PurbaFxIds.BREATH_COLD -> breath(target, Math.max(4.0F, fx.f()), 0x9AD8FF, true, random);
			case PurbaFxIds.BREATH_HOT -> breath(target, Math.max(4.0F, fx.f()), 0xFF7A2A, false, random);
			case PurbaFxIds.INHALE -> {
				Vec3 dir = target.subtract(pos);
				int motes = EffectQuality.scale(16);
				for (int i = 0; i < motes; i++) {
					double t = 0.3D + random.nextDouble() * 0.7D;
					Vec3 at = pos.add(dir.scale(t)).add(random.nextGaussian() * 2.0D, random.nextGaussian(), random.nextGaussian() * 2.0D);
					CommonFx.add(CommonFx.dust(0xC08A3A, 1.0F), at, pos.subtract(at).scale(0.08D), false);
				}
			}
			case PurbaFxIds.CELAH -> {
				CommonFx.burst(pos, 0xFFFFFF, 24, 3.0F);
				int sparks = EffectQuality.scale(12);
				for (int i = 0; i < sparks; i++) {
					CommonFx.add(ParticleTypes.END_ROD, pos.add(random.nextGaussian() * 1.5D, random.nextGaussian() * 1.5D, random.nextGaussian() * 1.5D),
							new Vec3(0.0D, 0.04D, 0.0D), true);
				}
			}
			case PurbaFxIds.CANDLE -> {
				CommonFx.burst(pos, 0xFFC060, 16, 1.2F);
				CommonFx.add(ParticleTypes.FLAME, pos, new Vec3(0.0D, 0.05D, 0.0D), true);
				CommonFx.add(ParticleTypes.SOUL_FIRE_FLAME, pos, new Vec3(0.0D, 0.08D, 0.0D), true);
			}
			case PurbaFxIds.STRIKE -> {
				Vec3 sky = pos.add(random.nextGaussian() * 2.0D, 24.0D, random.nextGaussian() * 2.0D);
				CommonFx.bolt(sky, pos.add(0.0D, 0.2D, 0.0D), fx.f() >= 2.0F ? 0xFFF0B0 : 0xB8D8FF, 10);
				CommonFx.ring(pos, 0xE0EEFF, 2.5F, 24);
			}
			case PurbaFxIds.DESCEND -> {
				CommonFx.pillar(pos, fx.b() == 1 ? 0xFFE08A : 0xFFF6D0, fx.b() == 1 ? 18.0F : 10.0F);
				CommonFx.ring(pos, 0xFFF0C0, 1.2F, 16);
			}
			case PurbaFxIds.WHIRLWIND -> {
				float radius = Math.max(4.0F, fx.f());
				int motes = EffectQuality.scale(36);
				for (int i = 0; i < motes; i++) {
					double angle = random.nextDouble() * Math.PI * 2.0D;
					double r = radius * (0.3D + 0.7D * random.nextDouble());
					Vec3 at = pos.add(Math.cos(angle) * r, random.nextDouble() * 12.0D, Math.sin(angle) * r);
					Vec3 swirl = new Vec3(-Math.sin(angle), 0.05D, Math.cos(angle)).scale(0.6D).add(pos.subtract(at).scale(0.02D));
					CommonFx.add(ParticleTypes.CLOUD, at, swirl, false);
				}
			}
			case PurbaFxIds.PHASE -> {
				float radius = Math.max(2.0F, fx.f());
				CommonFx.ring(pos, 0xFFFFFF, radius, 48);
				CommonFx.ring(pos, 0xC8B0FF, radius * 0.6F, 32);
				CommonFx.burst(pos, 0xE0D0FF, 40, radius * 0.4F);
			}
			case PurbaFxIds.DEVOUR -> {
				CommonFx.burst(pos, 0xC08A3A, 20, 1.5F);
				CommonFx.add(ParticleTypes.LARGE_SMOKE, pos, new Vec3(0.0D, 0.1D, 0.0D), true);
			}
			case PurbaFxIds.OMEN -> {
				// The silhouette crossing the far sky: a long trail of pale motes high above the summon point.
				int motes = EffectQuality.scale(40);
				double angle = random.nextDouble() * Math.PI * 2.0D;
				Vec3 dir = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
				for (int i = 0; i < motes; i++) {
					double t = (i / (double) motes - 0.5D) * 60.0D;
					Vec3 at = pos.add(dir.scale(t)).add(random.nextGaussian() * 0.8D, Math.sin(i * 0.4D) * 2.0D, random.nextGaussian() * 0.8D);
					CommonFx.add(CommonFx.dust(fx.b() == 1 ? 0xFF5A2A : fx.b() == 2 ? 0xC08A3A : 0x3A7BD5, 2.0F), at, dir.scale(0.2D), true);
				}
			}
			default -> {
			}
		}
	}

	private static void cone(Vec3 head, Vec3 end, float halfAngle, int rgb, net.minecraft.core.particles.ParticleOptions particle, RandomSource random) {
		Vec3 dir = end.subtract(head);
		double length = dir.length();
		if (length < 1.0E-3D) {
			return;
		}
		Vec3 unit = dir.scale(1.0D / length);
		int motes = EffectQuality.scale(24);
		double spread = Math.tan(Math.toRadians(halfAngle));
		for (int i = 0; i < motes; i++) {
			double t = random.nextDouble();
			double side = (random.nextDouble() * 2.0D - 1.0D) * spread * t * length;
			Vec3 perpendicular = new Vec3(-unit.z, 0.0D, unit.x);
			Vec3 at = head.add(unit.scale(t * length)).add(perpendicular.scale(side)).add(0.0D, random.nextGaussian() * 0.6D, 0.0D);
			CommonFx.add(i % 3 == 0 ? particle : CommonFx.dust(rgb, 1.4F), at, unit.scale(0.1D), false);
		}
	}

	private static void breath(Vec3 centre, float radius, int rgb, boolean cold, RandomSource random) {
		int motes = EffectQuality.scale(50);
		for (int i = 0; i < motes; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = radius * Math.sqrt(random.nextDouble());
			Vec3 at = centre.add(Math.cos(angle) * r, 0.3D + random.nextDouble() * 2.0D, Math.sin(angle) * r);
			CommonFx.add(cold ? ParticleTypes.SNOWFLAKE : ParticleTypes.FLAME, at, new Vec3(0.0D, cold ? -0.02D : 0.04D, 0.0D), false);
		}
		CommonFx.ring(centre, rgb, radius, 48);
	}

	private static void add(Telegraph telegraph) {
		if (TELEGRAPHS.size() >= MAX_TELEGRAPHS) {
			TELEGRAPHS.remove(0);
		}
		TELEGRAPHS.add(telegraph);
	}

	/** Every client tick (continuous visuals). */
	public static void tick() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		sync(level);
		RandomSource random = level.getRandom();
		Iterator<Telegraph> iterator = TELEGRAPHS.iterator();
		while (iterator.hasNext()) {
			Telegraph telegraph = iterator.next();
			telegraph.age++;
			if (telegraph.age > telegraph.life) {
				iterator.remove();
				continue;
			}
			if (telegraph.age % 2 != 0) {
				continue;
			}
			float progress = (float) telegraph.age / telegraph.life;
			if (telegraph.line) {
				CommonFx.beam(telegraph.a, telegraph.b, telegraph.color, 0.05F + 0.15F * progress);
			} else {
				int points = Math.min(64, 12 + Math.round(telegraph.radius * 3.0F));
				CommonFx.ring(telegraph.a, telegraph.color, telegraph.radius, EffectQuality.minimal() ? points / 2 : points);
				CommonFx.ring(telegraph.a, 0xFFFFFF, telegraph.radius * (1.0F - progress) + 0.1F, Math.max(8, points / 2));
			}
		}
		if (--scanTimer <= 0) {
			scanTimer = 10;
			rescan(level, minecraft.player.position());
		}
		halos(random);
	}

	private static void rescan(ClientLevel level, Vec3 centre) {
		ENVOYS.clear();
		AABB box = new AABB(centre, centre).inflate(HALO_RANGE);
		List<HeavenEnvoyEntity> found = level.getEntitiesOfClass(HeavenEnvoyEntity.class, box, mob -> mob.isAlive());
		found.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(centre)));
		for (int i = 0; i < Math.min(MAX_HALOS, found.size()); i++) {
			ENVOYS.add(found.get(i));
		}
	}

	/** A small turning ring of light above each envoy's head. */
	private static void halos(RandomSource random) {
		if (ENVOYS.isEmpty() || EffectQuality.minimal()) {
			return;
		}
		for (HeavenEnvoyEntity mob : ENVOYS) {
			if (mob.isRemoved() || !mob.isAlive() || mob.tickCount % 3 != 0) {
				continue;
			}
			int motes = FxBudget.take(mob.isPanglima() ? 3 : 2, FxBudget.Priority.DETAIL);
			for (int i = 0; i < motes; i++) {
				double angle = mob.tickCount * 0.2D + Math.PI * 2.0D * i / Math.max(1, motes);
				double r = mob.isPanglima() ? 0.5D : 0.35D;
				Vec3 at = new Vec3(mob.getX() + Math.cos(angle) * r, mob.getY() + mob.getBbHeight() + 0.25D, mob.getZ() + Math.sin(angle) * r);
				CommonFx.add(CommonFx.dust(mob.isPanglima() ? 0xFFD060 : 0xFFF6D0, 0.6F), at, Vec3.ZERO, false);
			}
		}
	}

	/** Forgets everything (disconnect, world change) and lets go of any sky grade. */
	public static void clear() {
		TELEGRAPHS.clear();
		ENVOYS.clear();
		if (nightOn || wrathOn) {
			SkyDirector.release(20, SKY_PRIORITY);
		}
		nightOn = false;
		wrathOn = false;
	}

	private static void sync(ClientLevel level) {
		if (level != lastLevel) {
			lastLevel = level;
			clear();
		}
	}

	private PurbaFx() {
	}
}
