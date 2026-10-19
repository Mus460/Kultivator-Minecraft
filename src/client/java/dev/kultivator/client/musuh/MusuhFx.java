package dev.kultivator.client.musuh;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.client.fx.spec.FxBudget;
import dev.kultivator.musuh.MusuhFxIds;
import dev.kultivator.musuh.RogueCultivatorEntity;
import dev.kultivator.network.Payloads;
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
 * Rogue and raid visuals (DESIGN 25.2 item 4, owned by P8-musuh; sub-ids in {@code musuh.MusuhFxIds}): vanish puffs and
 * arrival swirls, the blood-red seal threads of channelling raiders, the tier-3 mark and the Patriarch's Segel Darah as
 * closing rings, camp smoke columns and the killing intent before an ambush. Continuous rogue auras: dark motes on at most
 * 8 rogues within 32 blocks, through {@link FxBudget} and Effect Quality. Particles only; the server plays the sounds.
 */
public final class MusuhFx {

	public static final double AURA_RANGE = 32.0D;
	public static final int MAX_AURAS = 8;
	private static final int[] MOTES_PER_SECOND = {0, 2, 4, 6, 10};
	private static final int BLOOD = 0x9A0E14;
	private static final int BLOOD_LIGHT = 0xFF4A3A;
	private static final double DRAW_RANGE = 96.0D;
	private static final double SMOKE_RANGE = 64.0D;
	private static final int MAX_TELEGRAPHS = 24;
	private static final int MAX_SMOKES = 8;

	/** A ring drawn for its lifetime. */
	private static final class Telegraph {
		final boolean blood;
		final Vec3 centre;
		final float radius;
		final int color;
		final int life;
		int age;

		Telegraph(boolean blood, Vec3 centre, float radius, int color, int life) {
			this.blood = blood;
			this.centre = centre;
			this.radius = radius;
			this.color = color;
			this.life = Math.max(1, life);
		}
	}

	/** A smoke column that keeps rising for a while. */
	private static final class Smoke {
		final Vec3 pos;
		int life;

		Smoke(Vec3 pos, int life) {
			this.pos = pos;
			this.life = life;
		}
	}

	private static final List<Telegraph> TELEGRAPHS = new ArrayList<>();
	private static final List<Smoke> SMOKES = new ArrayList<>();
	private static final List<RogueCultivatorEntity> ROGUES = new ArrayList<>();
	private static ClientLevel lastLevel;
	private static int scanTimer;
	private static float carry;

	/** One {@code FxType} event of this module. */
	public static void play(Payloads.Fx fx) {
		ClientLevel level = CommonFx.level();
		Minecraft minecraft = Minecraft.getInstance();
		if (level == null || minecraft.player == null) {
			return;
		}
		sync(level);
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		if (minecraft.player.position().distanceToSqr(pos) > DRAW_RANGE * DRAW_RANGE) {
			return;
		}
		RandomSource random = level.getRandom();
		int rgb = fx.b() & 0xFFFFFF;
		switch (fx.a()) {
			case MusuhFxIds.VANISH -> {
				CommonFx.burst(pos, rgb, 14, 1.2F);
				int clouds = EffectQuality.scale(10);
				for (int i = 0; i < clouds; i++) {
					CommonFx.add(ParticleTypes.CLOUD, pos.add(random.nextGaussian() * 0.3D, random.nextDouble() * 0.6D - 0.6D, random.nextGaussian() * 0.3D),
							new Vec3(random.nextGaussian() * 0.04D, 0.05D + random.nextDouble() * 0.05D, random.nextGaussian() * 0.04D), false);
				}
			}
			case MusuhFxIds.ARRIVE -> {
				CommonFx.spiralIn(pos.add(0.0D, 1.0D, 0.0D), rgb, 1.4F, 8 + 4 * Math.max(1, Math.round(fx.f())), 0.0F);
				int smoke = EffectQuality.scale(6);
				for (int i = 0; i < smoke; i++) {
					CommonFx.add(ParticleTypes.LARGE_SMOKE, pos.add(random.nextGaussian() * 0.3D, random.nextDouble(), random.nextGaussian() * 0.3D),
							new Vec3(0.0D, 0.03D, 0.0D), false);
				}
			}
			case MusuhFxIds.SEAL -> {
				CommonFx.beam(pos, target, fx.a() == MusuhFxIds.SEAL && fx.b() >= 4 ? BLOOD_LIGHT : BLOOD, 0.06F);
				if (EffectQuality.chance(random, 0.5F)) {
					CommonFx.ring(target.add(0.0D, -0.9D, 0.0D), BLOOD, 1.2F, 10);
				}
			}
			case MusuhFxIds.MARK -> add(new Telegraph(false, pos, Math.max(0.5F, fx.f()), rgb, MusuhFxIds.MARK_TICKS));
			case MusuhFxIds.MARK_HIT -> {
				CommonFx.burst(pos.add(0.0D, 0.6D, 0.0D), rgb, 18, Math.max(1.0F, fx.f()));
				CommonFx.ring(pos, rgb, Math.max(0.5F, fx.f()), 18);
			}
			case MusuhFxIds.BLOOD_SEAL -> add(new Telegraph(true, pos, Math.max(1.0F, fx.f()), BLOOD, MusuhFxIds.BLOOD_TICKS));
			case MusuhFxIds.BLOOD_HIT -> {
				float radius = Math.max(1.0F, fx.f());
				CommonFx.ring(pos, BLOOD, radius, 36);
				CommonFx.ring(pos, BLOOD_LIGHT, radius * 0.6F, 20);
				CommonFx.burst(pos.add(0.0D, 0.8D, 0.0D), BLOOD, 24, radius * 0.5F);
			}
			case MusuhFxIds.CAMP_SMOKE -> {
				if (minecraft.player.position().distanceToSqr(pos) <= SMOKE_RANGE * SMOKE_RANGE) {
					for (Smoke smoke : SMOKES) {
						if (smoke.pos.distanceToSqr(pos) < 1.0D) {
							smoke.life = Math.max(smoke.life, Math.round(fx.f() * 20.0F) + 2);
							return;
						}
					}
					if (SMOKES.size() < MAX_SMOKES) {
						SMOKES.add(new Smoke(pos, Math.round(fx.f() * 20.0F) + 2));
					}
				}
			}
			case MusuhFxIds.AMBUSH_ALERT -> {
				Vec3 centre = minecraft.player.position().add(0.0D, 1.0D, 0.0D);
				CommonFx.spiralIn(centre, 0x3A0A0A, 3.0F, 12 + 6 * Math.max(1, fx.b()), 0.0F);
			}
			default -> {
			}
		}
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
		Vec3 centre = minecraft.player.position();
		if (--scanTimer <= 0) {
			scanTimer = 10;
			rescan(level, centre);
		}
		auras(random, centre);
		Iterator<Telegraph> telegraphs = TELEGRAPHS.iterator();
		while (telegraphs.hasNext()) {
			Telegraph telegraph = telegraphs.next();
			telegraph.age++;
			if (telegraph.age > telegraph.life) {
				telegraphs.remove();
				continue;
			}
			draw(telegraph, random);
		}
		Iterator<Smoke> smokes = SMOKES.iterator();
		while (smokes.hasNext()) {
			Smoke smoke = smokes.next();
			if (--smoke.life <= 0) {
				smokes.remove();
				continue;
			}
			if (smoke.life % 3 == 0 && FxBudget.take(1, FxBudget.Priority.DETAIL) > 0) {
				CommonFx.add(ParticleTypes.CAMPFIRE_COSY_SMOKE, smoke.pos.add(random.nextGaussian() * 0.25D, 0.2D, random.nextGaussian() * 0.25D),
						new Vec3(0.0D, 0.07D, 0.0D), true);
			}
		}
	}

	/** Forgets everything (disconnect, world change). */
	public static void clear() {
		TELEGRAPHS.clear();
		SMOKES.clear();
		ROGUES.clear();
	}

	private static void sync(ClientLevel level) {
		if (level != lastLevel) {
			lastLevel = level;
			clear();
		}
	}

	private static void rescan(ClientLevel level, Vec3 centre) {
		ROGUES.clear();
		AABB box = new AABB(centre, centre).inflate(AURA_RANGE);
		List<RogueCultivatorEntity> found = level.getEntitiesOfClass(RogueCultivatorEntity.class, box,
				mob -> mob.isAlive() && mob.distanceToSqr(centre) <= AURA_RANGE * AURA_RANGE);
		found.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(centre)));
		for (int i = 0; i < Math.min(MAX_AURAS, found.size()); i++) {
			ROGUES.add(found.get(i));
		}
	}

	private static void auras(RandomSource random, Vec3 centre) {
		if (ROGUES.isEmpty()) {
			return;
		}
		float quality = EffectQuality.level().factor();
		for (RogueCultivatorEntity mob : ROGUES) {
			if (mob.isRemoved() || !mob.isAlive() || mob.isInvisible() || mob.distanceToSqr(centre) > AURA_RANGE * AURA_RANGE) {
				continue;
			}
			int tier = mob.tier();
			float perTick = MOTES_PER_SECOND[tier] * quality / 20.0F;
			int wanted = (int) perTick;
			carry += perTick - wanted;
			if (carry >= 1.0F) {
				carry -= 1.0F;
				wanted++;
			}
			int motes = FxBudget.take(wanted, FxBudget.Priority.DETAIL);
			int color = tier >= 4 ? BLOOD : mob.element().color();
			double x = mob.getX();
			double y = mob.getY();
			double z = mob.getZ();
			float width = mob.getBbWidth();
			float height = mob.getBbHeight();
			for (int i = 0; i < motes; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				double r = width * (0.45D + random.nextDouble() * 0.35D);
				Vec3 at = new Vec3(x + Math.cos(angle) * r, y + random.nextDouble() * height, z + Math.sin(angle) * r);
				CommonFx.add(CommonFx.dust(i % 2 == 0 ? 0x1A1A22 : color, 0.6F + 0.12F * tier), at, new Vec3(0.0D, 0.015D, 0.0D), false);
			}
			if (mob.isChanneling() && FxBudget.take(2, FxBudget.Priority.DETAIL) > 0) {
				// The blood-red seal turns between its raised hands.
				double angle = mob.tickCount * 0.3D;
				for (int i = 0; i < 2; i++) {
					double a = angle + Math.PI * i;
					Vec3 at = new Vec3(x + Math.cos(a) * 0.6D, y + 1.3D, z + Math.sin(a) * 0.6D);
					CommonFx.add(CommonFx.dust(BLOOD_LIGHT, 0.9F), at, Vec3.ZERO, false);
				}
			}
		}
	}

	private static void draw(Telegraph t, RandomSource random) {
		float progress = (float) t.age / t.life;
		if (t.age % 2 == 0) {
			int points = t.blood ? 36 : 18;
			CommonFx.ring(t.centre, t.color, t.radius, EffectQuality.minimal() ? points / 2 : points);
			CommonFx.ring(t.centre, t.blood ? BLOOD_LIGHT : 0xFFFFFF, t.radius * (1.0F - progress) + 0.1F, Math.max(6, points / 2));
		}
		if (t.blood && EffectQuality.chance(random, 0.6F)) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = t.radius * random.nextDouble();
			CommonFx.add(ParticleTypes.CRIMSON_SPORE, t.centre.add(Math.cos(angle) * r, 0.1D, Math.sin(angle) * r), new Vec3(0.0D, 0.05D, 0.0D), true);
		}
	}

	private MusuhFx() {
	}
}
