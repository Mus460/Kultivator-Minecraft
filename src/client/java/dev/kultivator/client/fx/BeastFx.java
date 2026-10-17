package dev.kultivator.client.fx;

import dev.kultivator.beast.BeastData;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Spirit beast effects. Every tick, for each beast within 48 blocks (read from the synced {@code kultivator:spirit_beast}
 * attachment): a wolf sheds pale blue moon motes, from tier 2 tiny electric sparks, at tier 3 a faint halo; a fox
 * carries orbiting soul-fire wisps, its "tails" (1 / 3 / 9 at tier 1 / 2 / 3), with golden glints at tier 3.
 * {@code BEAST_EVOLVE} with a >= 0 plays the evolution cinematic (a = new tier, 0 = awakening; b = entity id, f = kind):
 * a spiral of the tier colour closing in, a burst, then a rising ring. Negative a are small ability effects (see
 * {@code SpiritBeasts.FX_*}). Everything is scaled by Effect Quality.
 */
public final class BeastFx {

	private static final double RANGE = 48.0D;
	private static final int[] FOX_TAILS = {0, 1, 3, 9};
	private static final int HALO = 0xFFF4D0;
	private static final int GOLD = 0xFFD54A;
	private static final int THUNDER = 0xD8E8FF;

	private static final List<Cinematic> CINEMATICS = new ArrayList<>();

	/** One running evolution cinematic, following its beast while it is visible. */
	private static final class Cinematic {
		final int entityId;
		final int tier;
		final int kind;
		final int duration;
		final int burstAt;
		Vec3 pos;
		float height = 0.85F;
		int age;

		Cinematic(int entityId, Vec3 pos, int tier, int kind) {
			this.entityId = entityId;
			this.pos = pos;
			this.tier = tier;
			this.kind = kind;
			this.duration = tier == 0 ? 36 : 50;
			this.burstAt = tier == 0 ? 22 : 30;
		}
	}

	public static void play(FxType type, Payloads.Fx fx) {
		if (type != FxType.BEAST_EVOLVE) {
			return;
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		int kind = Math.round(fx.f()) == BeastData.FOX ? BeastData.FOX : BeastData.WOLF;
		switch (fx.a()) {
			case SpiritBeasts.FX_MOON_THUNDER -> moonThunder(pos, target);
			case SpiritBeasts.FX_FOXFIRE -> foxfire(pos);
			case SpiritBeasts.FX_BLESSING -> blessing(pos, target, kind);
			default -> {
				if (fx.a() >= 0) {
					if (CINEMATICS.size() >= 16) {
						CINEMATICS.remove(0);
					}
					CINEMATICS.add(new Cinematic(fx.b(), pos, Math.min(BeastData.MAX_TIER, fx.a()), kind));
				}
			}
		}
	}

	/** Every client tick (only while in a world and not paused). */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		tickCinematics(level);
		RandomSource random = level.getRandom();
		long time = level.getGameTime();
		Vec3 center = minecraft.player.position();
		AABB box = new AABB(center, center).inflate(RANGE);
		List<LivingEntity> beasts = level.getEntitiesOfClass(LivingEntity.class, box,
				entity -> entity.isAlive() && !entity.isInvisible() && entity.hasAttached(BeastData.TYPE)
						&& entity.distanceToSqr(center) <= RANGE * RANGE);
		for (LivingEntity beast : beasts) {
			BeastData data = beast.getAttached(BeastData.TYPE);
			if (data == null) {
				continue;
			}
			if (data.isFox()) {
				foxAura(beast, data.tier(), random, time);
			} else {
				wolfAura(beast, data.tier(), random, time);
			}
		}
	}

	/** Forgets running cinematics (leaving a world). */
	public static void clear() {
		CINEMATICS.clear();
	}

	// ================================================================== auras

	private static void wolfAura(LivingEntity wolf, int tier, RandomSource random, long time) {
		double x = wolf.getX();
		double y = wolf.getY();
		double z = wolf.getZ();
		float width = wolf.getBbWidth();
		float height = wolf.getBbHeight();
		int color = SpiritBeasts.tierColor(BeastData.WOLF, tier);

		// Pale blue moon motes drifting up from the fur.
		float rate = 0.1F + 0.09F * tier;
		int motes = EffectQuality.chance(random, rate) ? (EffectQuality.maximal() && random.nextBoolean() ? 2 : 1) : 0;
		for (int i = 0; i < motes; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double radius = width * (0.4D + random.nextDouble() * 0.4D);
			CommonFx.add(CommonFx.dust(color, 0.45F + 0.08F * tier + random.nextFloat() * 0.2F),
					new Vec3(x + Math.cos(angle) * radius, y + random.nextDouble() * height, z + Math.sin(angle) * radius),
					new Vec3(0.0D, 0.012D + random.nextDouble() * 0.01D, 0.0D), false);
		}

		// Tier 2+: tiny electric sparks crackling over the back.
		if (tier >= 2 && EffectQuality.chance(random, 0.05F + 0.03F * (tier - 2))) {
			CommonFx.add(ParticleTypes.ELECTRIC_SPARK, new Vec3(x + (random.nextDouble() - 0.5D) * width, y + height * (0.6D + random.nextDouble() * 0.4D),
					z + (random.nextDouble() - 0.5D) * width), new Vec3(random.nextGaussian() * 0.04D, 0.03D, random.nextGaussian() * 0.04D), false);
		}

		// Tier 3: a faint halo turning above the head.
		if (tier >= 3) {
			int every = EffectQuality.minimal() ? 6 : EffectQuality.maximal() ? 1 : 2;
			if ((time + wolf.getId()) % every == 0) {
				double spin = time * 0.08D + wolf.getId();
				double radius = Math.max(0.25D, width * 0.45D);
				double haloY = y + height + 0.25D;
				int points = EffectQuality.maximal() ? 4 : 2;
				for (int i = 0; i < points; i++) {
					double angle = spin + i * Math.PI * 2.0D / points;
					CommonFx.add(CommonFx.dust(HALO, 0.4F), new Vec3(x + Math.cos(angle) * radius, haloY, z + Math.sin(angle) * radius), Vec3.ZERO, false);
				}
			}
		}
	}

	private static void foxAura(LivingEntity fox, int tier, RandomSource random, long time) {
		double x = fox.getX();
		double y = fox.getY();
		double z = fox.getZ();
		float width = fox.getBbWidth();
		float height = fox.getBbHeight();

		// Tier 0: a faint warm glow only.
		if (tier == 0) {
			if (EffectQuality.chance(random, 0.08F)) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				CommonFx.add(CommonFx.dust(SpiritBeasts.tierColor(BeastData.FOX, 0), 0.5F),
						new Vec3(x + Math.cos(angle) * width * 0.6D, y + random.nextDouble() * height, z + Math.sin(angle) * width * 0.6D),
						new Vec3(0.0D, 0.015D, 0.0D), false);
			}
			return;
		}

		// The tails: soul-fire wisps circling the body.
		int tails = FOX_TAILS[Math.min(BeastData.MAX_TIER, tier)];
		int every = EffectQuality.minimal() ? 5 : EffectQuality.maximal() ? 1 : 2;
		if ((time + fox.getId()) % every == 0) {
			double spin = time * (0.09D + 0.015D * tier) + fox.getId();
			double radius = width * 0.75D + 0.25D + 0.05D * tier;
			double middle = y + height * 0.55D;
			for (int i = 0; i < tails; i++) {
				double angle = spin + i * Math.PI * 2.0D / tails;
				double bob = Math.sin(spin * 1.6D + i * 0.7D) * 0.12D;
				CommonFx.add(ParticleTypes.SOUL_FIRE_FLAME, new Vec3(x + Math.cos(angle) * radius, middle + bob, z + Math.sin(angle) * radius),
						Vec3.ZERO, false);
			}
		}

		// Tier 3: golden glints.
		if (tier >= 3 && EffectQuality.chance(random, 0.12F)) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double radius = width * (0.5D + random.nextDouble() * 0.6D);
			CommonFx.add(CommonFx.dust(GOLD, 0.55F + random.nextFloat() * 0.25F),
					new Vec3(x + Math.cos(angle) * radius, y + random.nextDouble() * height * 1.2D, z + Math.sin(angle) * radius),
					new Vec3(0.0D, 0.02D, 0.0D), false);
			if (EffectQuality.maximal() && random.nextInt(3) == 0) {
				CommonFx.add(ParticleTypes.END_ROD, new Vec3(x, y + height * 0.8D, z),
						new Vec3(random.nextGaussian() * 0.02D, 0.03D, random.nextGaussian() * 0.02D), false);
			}
		}
	}

	// ================================================================== evolution cinematic

	private static void tickCinematics(ClientLevel level) {
		if (CINEMATICS.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		for (Iterator<Cinematic> it = CINEMATICS.iterator(); it.hasNext(); ) {
			Cinematic cinematic = it.next();
			Entity entity = level.getEntity(cinematic.entityId);
			if (entity != null && entity.isAlive()) {
				cinematic.pos = entity.position();
				cinematic.height = entity.getBbHeight();
			}
			step(cinematic, random);
			cinematic.age++;
			if (cinematic.age > cinematic.duration) {
				it.remove();
			}
		}
	}

	private static void step(Cinematic c, RandomSource random) {
		int color = SpiritBeasts.tierColor(c.kind, c.tier);
		Vec3 base = c.pos;
		double height = Math.max(0.5D, c.height);
		Vec3 middle = base.add(0.0D, height * 0.5D, 0.0D);
		boolean fox = c.kind == BeastData.FOX;
		if (c.age < c.burstAt) {
			// Spiral: arms of the tier colour winding in and up around the beast.
			double t = (double) c.age / c.burstAt;
			double radius = 0.45D + 2.0D * (1.0D - t) * (1.0D + 0.15D * c.tier);
			int arms = 2 + Math.min(2, c.tier);
			int perArm = Math.max(1, EffectQuality.scale(2));
			for (int arm = 0; arm < arms; arm++) {
				for (int k = 0; k < perArm; k++) {
					double angle = c.age * 0.45D + arm * Math.PI * 2.0D / arms + k * 0.18D;
					double y = base.y + height * (0.1D + 1.1D * t) + (random.nextDouble() - 0.5D) * 0.1D;
					Vec3 at = new Vec3(base.x + Math.cos(angle) * radius, y, base.z + Math.sin(angle) * radius);
					CommonFx.add(CommonFx.dust(color, 0.7F + 0.1F * c.tier), at, middle.subtract(at).scale(0.04D), false);
				}
			}
			if (fox && c.tier >= 1 && EffectQuality.chance(random, 0.35F)) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				CommonFx.add(ParticleTypes.SOUL_FIRE_FLAME, new Vec3(base.x + Math.cos(angle) * radius, base.y + height * t, base.z + Math.sin(angle) * radius),
						Vec3.ZERO, false);
			} else if (!fox && c.tier >= 2 && EffectQuality.chance(random, 0.3F)) {
				CommonFx.add(ParticleTypes.ELECTRIC_SPARK, middle.add((random.nextDouble() - 0.5D) * radius, (random.nextDouble() - 0.5D) * height,
						(random.nextDouble() - 0.5D) * radius), Vec3.ZERO, false);
			}
		} else if (c.age == c.burstAt) {
			// Burst of light; a moon-thunder bolt for the wolf, a bloom of soul fire for the fox.
			CommonFx.burst(middle, color, 30 + 15 * c.tier, 2.0F + 0.5F * c.tier);
			if (!fox && c.tier >= 2) {
				CommonFx.bolt(base.add(0.0D, 10.0D, 0.0D), base, THUNDER, 8);
			}
			if (fox && c.tier >= 1) {
				int n = EffectQuality.scale(6 + 4 * c.tier);
				for (int i = 0; i < n; i++) {
					Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.5D, random.nextGaussian()).normalize();
					CommonFx.add(ParticleTypes.SOUL_FIRE_FLAME, middle, dir.scale(0.12D), false);
				}
			}
			if (c.tier >= 3) {
				CommonFx.burst(middle.add(0.0D, height * 0.4D, 0.0D), fox ? GOLD : HALO, 24, 3.0F);
			}
		} else if ((c.age - c.burstAt) % 2 == 0) {
			// Rising ring.
			double k = (double) (c.age - c.burstAt) / Math.max(1, c.duration - c.burstAt);
			CommonFx.ring(base.add(0.0D, height * 1.6D * k, 0.0D), color, (float) (0.6D + 1.8D * k), 18 + 4 * c.tier);
		}
	}

	// ================================================================== ability effects

	/** A jagged particle bolt from the sky onto the bitten target. */
	private static void moonThunder(Vec3 pos, Vec3 sky) {
		CommonFx.bolt(sky, pos.add(0.0D, 0.3D, 0.0D), THUNDER, 7);
		CommonFx.burst(pos.add(0.0D, 0.5D, 0.0D), 0xBFD8FF, 10, 0.8F);
	}

	/** Soul fire licking up around the bitten target. */
	private static void foxfire(Vec3 pos) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(8);
		for (int i = 0; i < n; i++) {
			CommonFx.add(ParticleTypes.SOUL_FIRE_FLAME, new Vec3(pos.x + random.nextGaussian() * 0.25D, pos.y + random.nextGaussian() * 0.25D,
					pos.z + random.nextGaussian() * 0.25D), new Vec3(random.nextGaussian() * 0.02D, 0.04D + random.nextDouble() * 0.04D,
					random.nextGaussian() * 0.02D), false);
		}
	}

	/** A thin thread of light from a tier 3 beast to its owner. */
	private static void blessing(Vec3 from, Vec3 to, int kind) {
		CommonFx.beam(from, to, kind == BeastData.FOX ? GOLD : HALO, 0.12F);
		CommonFx.burst(to, kind == BeastData.FOX ? GOLD : 0x9FD8A0, 6, 0.6F);
	}

	private BeastFx() {
	}
}
