package dev.kultivator.client.dunia.langit;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.dunia.api.SkyKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.ArrayList;
import java.util.List;

/**
 * The sky of a Hujan Meteor Roh night on the client (DESIGN 34.5.8): short-lived streaks simulated as points on the sky
 * sphere, radiating from one point of the sky (the event's seed, like a real shower's radiant), and every 30-50 s one
 * slow green-white fireball. Positions are directions only; {@link LangitRenderer} projects them onto the sky sphere
 * (DESIGN 23.10.3), so they are the same at every render distance. Streaks alive: 6 / 16 / 32 by Effect Quality.
 */
final class LangitSky {

	static final int[] ALIVE = {6, 16, 32};

	static final class Streak {
		// unit start direction and unit tangent (the great circle it moves along)
		float sx;
		float sy;
		float sz;
		float tx;
		float ty;
		float tz;
		/** Radians per tick, the trail length in radians, the half width (fraction of the sky radius). */
		float speed;
		float length;
		float width;
		int life;
		int age;
		boolean big;
		int color;

		/** The direction at {@code angle} radians along the path, written into {@code out}. */
		void at(float angle, float[] out) {
			float c = Mth.cos(angle);
			float s = Mth.sin(angle);
			out[0] = sx * c + tx * s;
			out[1] = sy * c + ty * s;
			out[2] = sz * c + tz * s;
		}

		float fade(float partial) {
			float t = (age + partial) / life;
			return Mth.clamp(Math.min(t * 6.0F, (1.0F - t) * 2.5F), 0.0F, 1.0F);
		}
	}

	static final List<Streak> STREAKS = new ArrayList<>();
	private static int nextFireball = 400;

	static void tick(ClientLevel level, Minecraft minecraft) {
		for (int i = STREAKS.size() - 1; i >= 0; i--) {
			Streak streak = STREAKS.get(i);
			if (++streak.age >= streak.life) {
				STREAKS.remove(i);
			}
		}
		if (LangitState.shown() != SkyKind.METEOR_SHOWER || level.dimensionType().skybox() != DimensionType.Skybox.OVERWORLD) {
			return;
		}
		RandomSource random = level.getRandom();
		float weight = LangitState.skyWeight;
		int cap = ALIVE[EffectQuality.level().ordinal()];
		// a lull and a burst every half minute or so, like a real shower
		float wave = 0.65F + 0.35F * Mth.sin((float) (level.getGameTime() % 1200L) / 1200.0F * Mth.TWO_PI);
		float perTick = cap / 26.0F * weight * wave;
		while (perTick > 0.0F && STREAKS.size() < cap) {
			if (random.nextFloat() >= Math.min(1.0F, perTick)) {
				break;
			}
			perTick -= 1.0F;
			spawn(random, false);
		}
		if (--nextFireball <= 0) {
			nextFireball = 600 + random.nextInt(400);
			if (weight > 0.5F) {
				spawn(random, true);
			}
		}
	}

	private static void spawn(RandomSource random, boolean big) {
		int seed = LangitState.skySeed;
		float radiantAz = (seed & 0xFFFF) / 65536.0F * Mth.TWO_PI;
		float radiantEl = 0.9F + ((seed >>> 16) & 0xFF) / 255.0F * 0.35F;
		float rx = Mth.cos(radiantEl) * Mth.cos(radiantAz);
		float ry = Mth.sin(radiantEl);
		float rz = Mth.cos(radiantEl) * Mth.sin(radiantAz);
		float sx = 0.0F;
		float sy = 0.0F;
		float sz = 0.0F;
		for (int attempt = 0; attempt < 6; attempt++) {
			float az = random.nextFloat() * Mth.TWO_PI;
			float el = (big ? 0.5F : 0.35F) + random.nextFloat() * 0.95F;
			sx = Mth.cos(el) * Mth.cos(az);
			sy = Mth.sin(el);
			sz = Mth.cos(el) * Mth.sin(az);
			if (sx * rx + sy * ry + sz * rz < 0.985F) {
				break;
			}
		}
		// away from the radiant: the part of -radiant perpendicular to the start direction
		float dot = sx * rx + sy * ry + sz * rz;
		float tx = sx * dot - rx;
		float ty = sy * dot - ry;
		float tz = sz * dot - rz;
		float length = Mth.sqrt(tx * tx + ty * ty + tz * tz);
		if (length < 1.0E-4F) {
			tx = -sz;
			ty = 0.0F;
			tz = sx;
			length = Math.max(1.0E-4F, Mth.sqrt(tx * tx + tz * tz));
		}
		Streak streak = new Streak();
		streak.sx = sx;
		streak.sy = sy;
		streak.sz = sz;
		streak.tx = tx / length;
		streak.ty = ty / length;
		streak.tz = tz / length;
		streak.big = big;
		if (big) {
			streak.speed = 0.0055F + random.nextFloat() * 0.002F;
			streak.length = 0.32F + random.nextFloat() * 0.12F;
			streak.width = 0.022F;
			streak.life = 70 + random.nextInt(20);
			streak.color = 0xD8FFE8;
		} else {
			streak.speed = 0.012F + random.nextFloat() * 0.014F;
			streak.length = 0.07F + random.nextFloat() * 0.13F;
			streak.width = 0.006F + random.nextFloat() * 0.005F;
			streak.life = 16 + random.nextInt(22);
			// most white-blue, some gold or green like real meteors (sodium, magnesium)
			int pick = random.nextInt(10);
			streak.color = pick < 6 ? 0xCFE8FF : pick < 8 ? 0xFFE6B0 : 0xB8FFD0;
		}
		STREAKS.add(streak);
	}

	static void clearStreaks() {
		STREAKS.clear();
	}

	private LangitSky() {
	}
}
