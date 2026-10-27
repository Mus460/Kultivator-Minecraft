package dev.kultivator.client.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Realm;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Minor breakthroughs (stage up), the Mortal awakening, qi gathering before a breakthrough, the success burst and the
 * black-red Qi Deviation. Particles only, scaled by {@link EffectQuality}; longer effects are kept in a small list and
 * drawn every client tick.
 */
public final class BreakthroughFx {

	private static final int GOLD = 0xFFD54A;
	private static final int LIGHT = 0xFFF6D8;
	private static final int MAX_ACTIVE = 24;

	private enum Kind { AWAKENING, GATHER, SUCCESS }

	private static final class Active {
		final Kind kind;
		final Vec3 pos;
		final int color;
		final int duration;
		final int quality;
		int age;

		Active(Kind kind, Vec3 pos, int color, int duration, int quality) {
			this.kind = kind;
			this.pos = pos;
			this.color = color;
			this.duration = duration;
			this.quality = quality;
		}
	}

	private static final List<Active> ACTIVE = new ArrayList<>();
	private static long lastTickTime = Long.MIN_VALUE;
	/** Running effects belong to one world: they are dropped when the client changes or leaves it. */
	private static ClientLevel lastLevel;

	public static void play(FxType type, Payloads.Fx fx) {
		ClientLevel level = CommonFx.level();
		if (level != lastLevel) {
			lastLevel = level;
			ACTIVE.clear();
			lastTickTime = Long.MIN_VALUE;
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		switch (type) {
			case STAGE_UP -> stageUp(pos, Realm.byOrdinal(fx.a()).color());
			case AWAKENING -> {
				if (fx.b() <= 0) {
					stop(Kind.AWAKENING, pos);
				} else {
					start(new Active(Kind.AWAKENING, pos, fx.a() & 0xFFFFFF, fx.b(), 0));
				}
			}
			case BREAKTHROUGH_START -> {
				if (fx.b() <= 0) {
					stop(Kind.GATHER, pos);
				} else {
					start(new Active(Kind.GATHER, pos, Realm.byOrdinal(fx.a()).color(), fx.b(), 0));
				}
			}
			case BREAKTHROUGH_SUCCESS -> success(pos, Realm.byOrdinal(fx.a()), fx.b());
			case DEVIATION -> deviation(pos, Math.max(1.0F, fx.f()));
			default -> {
			}
		}
	}

	/** Every client tick (only while in a world and not paused). Safe to call more than once per tick. */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			lastLevel = level;
			ACTIVE.clear();
			lastTickTime = Long.MIN_VALUE;
		}
		if (level == null || ACTIVE.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		if (now == lastTickTime) {
			return;
		}
		lastTickTime = now;
		Iterator<Active> iterator = ACTIVE.iterator();
		while (iterator.hasNext()) {
			Active active = iterator.next();
			active.age++;
			switch (active.kind) {
				case AWAKENING -> tickAwakening(active);
				case GATHER -> tickGather(active);
				case SUCCESS -> tickSuccess(active);
			}
			if (active.age >= active.duration) {
				if (active.kind == Kind.AWAKENING) {
					awakeningFinale(active);
				}
				iterator.remove();
			}
		}
	}

	// ================================================================== list

	private static void start(Active active) {
		stop(active.kind, active.pos);
		if (ACTIVE.size() >= MAX_ACTIVE) {
			ACTIVE.remove(0);
		}
		ACTIVE.add(active);
	}

	private static void stop(Kind kind, Vec3 pos) {
		ACTIVE.removeIf(each -> each.kind == kind && each.pos.distanceToSqr(pos) < 9.0D);
	}

	// ================================================================== one-shot effects

	/** Small golden ring at the feet and motes rising in the realm colour. */
	private static void stageUp(Vec3 pos, int realmColor) {
		RandomSource random = CommonFx.random();
		CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), GOLD, 1.2F, 28);
		int motes = EffectQuality.scale(14);
		DustParticleOptions dust = CommonFx.dust(realmColor, 0.9F);
		for (int i = 0; i < motes; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double radius = 0.3D + random.nextDouble() * 0.7D;
			CommonFx.add(dust, pos.x + Math.cos(angle) * radius, pos.y + random.nextDouble() * 0.6D, pos.z + Math.sin(angle) * radius,
					0.0D, 0.06D + random.nextDouble() * 0.05D, 0.0D);
		}
		if (EffectQuality.maximal()) {
			CommonFx.ring(pos.add(0.0D, 0.6D, 0.0D), realmColor, 0.8F, 20);
			for (int i = 0; i < 4; i++) {
				CommonFx.add(ParticleTypes.END_ROD, pos.x, pos.y + 1.0D, pos.z, random.nextGaussian() * 0.03D, 0.08D, random.nextGaussian() * 0.03D);
			}
		}
	}

	/** Huge expanding ring, a pillar of light and a burst in the realm colour; the quality adds its own touch. */
	private static void success(Vec3 pos, Realm realm, int qualityOrdinal) {
		RandomSource random = CommonFx.random();
		int color = realm.color();
		Vec3 chest = pos.add(0.0D, 1.1D, 0.0D);
		CommonFx.burst(chest, color, 70, 3.0F);
		CommonFx.burst(chest, LIGHT, 30, 2.0F);
		CommonFx.pillar(pos, LIGHT, 24.0F);
		CommonFx.pillar(pos, color, 14.0F);
		int rods = EffectQuality.scale(26);
		for (int i = 0; i < rods; i++) {
			CommonFx.add(ParticleTypes.END_ROD, new Vec3(pos.x + random.nextGaussian() * 0.25D, pos.y + random.nextDouble() * 18.0D,
					pos.z + random.nextGaussian() * 0.25D), new Vec3(0.0D, 0.05D + random.nextDouble() * 0.1D, 0.0D), true);
		}
		CommonFx.add(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | color), chest, Vec3.ZERO, true);

		BreakthroughQuality quality = BreakthroughQuality.byOrdinal(qualityOrdinal);
		if (quality == BreakthroughQuality.HEAVENLY) {
			int totems = EffectQuality.scale(50);
			for (int i = 0; i < totems; i++) {
				CommonFx.add(ParticleTypes.TOTEM_OF_UNDYING, chest, new Vec3(random.nextGaussian() * 0.35D, 0.2D + random.nextDouble() * 0.5D,
						random.nextGaussian() * 0.35D), false);
			}
			CommonFx.ring(pos.add(0.0D, 0.2D, 0.0D), GOLD, 4.0F, 60);
		} else if (quality == BreakthroughQuality.PERFECT) {
			CommonFx.ring(pos.add(0.0D, 0.2D, 0.0D), quality.color(), 3.0F, 48);
		} else if (quality == BreakthroughQuality.CRACKED) {
			int smoke = EffectQuality.scale(10);
			for (int i = 0; i < smoke; i++) {
				CommonFx.add(ParticleTypes.SMOKE, chest.x + random.nextGaussian() * 0.4D, chest.y + random.nextGaussian() * 0.4D,
						chest.z + random.nextGaussian() * 0.4D, 0.0D, 0.03D, 0.0D);
			}
		}
		start(new Active(Kind.SUCCESS, pos, color, 26, quality == null ? 1 : quality.ordinal()));
	}

	/** Black and red jagged motes tearing out of the body. */
	private static void deviation(Vec3 pos, float strength) {
		RandomSource random = CommonFx.random();
		Vec3 chest = pos.add(0.0D, 1.0D, 0.0D);
		int lines = EffectQuality.scale(Math.round(7 * strength));
		for (int i = 0; i < lines; i++) {
			Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6D, random.nextGaussian()).normalize();
			Vec3 previous = chest.add(dir.scale(0.25D));
			for (int segment = 0; segment < 4; segment++) {
				Vec3 kink = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.25D);
				Vec3 next = previous.add(dir.scale(0.45D)).add(kink);
				CommonFx.beam(previous, next, segment % 2 == 0 ? 0xC01818 : 0x140404, 0.04F);
				previous = next;
			}
		}
		CommonFx.burst(chest, 0x8B0000, 26, 1.6F);
		CommonFx.burst(chest, 0x101010, 18, 1.2F);
		int smoke = EffectQuality.scale(8);
		for (int i = 0; i < smoke; i++) {
			CommonFx.add(ParticleTypes.SMOKE, chest.x + random.nextGaussian() * 0.3D, chest.y + random.nextGaussian() * 0.4D,
					chest.z + random.nextGaussian() * 0.3D, random.nextGaussian() * 0.02D, 0.04D, random.nextGaussian() * 0.02D);
		}
		if (EffectQuality.maximal()) {
			for (int i = 0; i < 6; i++) {
				CommonFx.add(ParticleTypes.LARGE_SMOKE, chest.x, chest.y, chest.z, random.nextGaussian() * 0.05D, 0.05D, random.nextGaussian() * 0.05D);
			}
			CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), 0x5A0000, 1.8F, 30);
		}
	}

	// ================================================================== continuous effects

	/** Qi of heaven and earth rushing into the body. */
	private static void tickAwakening(Active active) {
		RandomSource random = CommonFx.random();
		float progress = (float) active.age / Math.max(1, active.duration);
		Vec3 chest = active.pos.add(0.0D, 1.0D, 0.0D);
		CommonFx.spiralIn(chest, active.color, 4.5F - progress * 2.5F, 3 + Math.round(progress * 5), active.age);
		int rising = EffectQuality.scale(2);
		DustParticleOptions dust = CommonFx.dust(LIGHT, 0.7F);
		for (int i = 0; i < rising; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double radius = 1.5D + random.nextDouble() * 2.5D;
			Vec3 at = active.pos.add(Math.cos(angle) * radius, 0.1D, Math.sin(angle) * radius);
			CommonFx.add(dust, at, chest.subtract(at).scale(0.05D), false);
		}
		if (active.age % 10 == 0) {
			CommonFx.ring(active.pos.add(0.0D, 0.1D, 0.0D), active.color, 1.0F + progress * 1.5F, 24);
		}
		if (EffectQuality.maximal() && random.nextInt(3) == 0) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			Vec3 from = chest.add(Math.cos(angle) * 4.0D, random.nextDouble() * 2.0D - 0.5D, Math.sin(angle) * 4.0D);
			CommonFx.add(ParticleTypes.END_ROD, from, chest.subtract(from).scale(0.06D), false);
		}
	}

	private static void awakeningFinale(Active active) {
		Vec3 chest = active.pos.add(0.0D, 1.0D, 0.0D);
		CommonFx.burst(chest, active.color, 40, 1.5F);
		CommonFx.ring(active.pos.add(0.0D, 0.1D, 0.0D), LIGHT, 3.0F, 40);
		CommonFx.add(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | active.color), chest, Vec3.ZERO, true);
	}

	/** Motes gathering from all around, with a slowly turning ring of qi at the waist. */
	private static void tickGather(Active active) {
		RandomSource random = CommonFx.random();
		float progress = (float) active.age / Math.max(1, active.duration);
		Vec3 chest = active.pos.add(0.0D, 1.0D, 0.0D);
		int motes = EffectQuality.scale(3 + Math.round(progress * 4));
		DustParticleOptions dust = CommonFx.dust(active.color, 0.8F);
		for (int i = 0; i < motes; i++) {
			Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.5D, random.nextGaussian()).normalize();
			Vec3 at = chest.add(dir.scale(3.5D + random.nextDouble() * 2.0D));
			CommonFx.add(dust, at, chest.subtract(at).scale(0.07D), false);
		}
		double turn = active.age * 0.2D;
		DustParticleOptions ringDust = CommonFx.dust(GOLD, 0.6F);
		int points = EffectQuality.scale(3);
		for (int i = 0; i < points; i++) {
			double angle = turn + Math.PI * 2.0D * i / points;
			CommonFx.add(ringDust, active.pos.x + Math.cos(angle) * 0.9D, active.pos.y + 0.9D, active.pos.z + Math.sin(angle) * 0.9D,
					0.0D, 0.0D, 0.0D);
		}
	}

	/** The ring of the success burst growing outwards; Maximal adds a gold ring and spirals around the pillar. */
	private static void tickSuccess(Active active) {
		float radius = 1.0F + active.age * 0.5F;
		CommonFx.ring(active.pos.add(0.0D, 0.15D, 0.0D), active.color, radius, 40 + active.age * 2);
		if (active.quality >= BreakthroughQuality.PERFECT.ordinal() || EffectQuality.maximal()) {
			CommonFx.ring(active.pos.add(0.0D, 0.6D, 0.0D), GOLD, radius * 0.7F, 30);
		}
		if (EffectQuality.maximal()) {
			DustParticleOptions dust = CommonFx.dust(LIGHT, 1.0F);
			for (int arm = 0; arm < 3; arm++) {
				double angle = active.age * 0.45D + Math.PI * 2.0D * arm / 3.0D;
				double height = active.age * 0.6D;
				CommonFx.add(dust, active.pos.x + Math.cos(angle) * 1.2D, active.pos.y + height, active.pos.z + Math.sin(angle) * 1.2D,
						0.0D, 0.02D, 0.0D);
			}
		}
	}

	/** Disconnect: drops the old world (the next world's first tick would only do it after it loads). */
	static void forget() {
		lastLevel = null;
		ACTIVE.clear();
		lastTickTime = Long.MIN_VALUE;
	}

	private BreakthroughFx() {
	}
}
