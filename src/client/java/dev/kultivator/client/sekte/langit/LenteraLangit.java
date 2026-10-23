package dev.kultivator.client.sekte.langit;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * Lentera Langit, the festival sky lanterns (DESIGN 37.6.5): on a festival night (flag bit 4: the three nights after the
 * Ascension, the world's new year every 96 days, after a sect tournament) or the test, 0 / 10 / 30 kongming lanterns
 * ({@code lentera.png}, with a soft warm glow behind each) are let go one after another from round the plaza and rise
 * slowly, swaying, drifting down the breeze and flickering, until they are small warm stars 60 blocks up and fade. Like
 * the petals each lantern is a pure function of the seed, its index and the time.
 */
final class LenteraLangit {

	private static final float LIFE = 48.0F;
	private static final float RISE = 1.3F;

	private static final AwanSink LANTERNS = new AwanSink(AwanSink.LENTERA, false);
	private static final AwanSink GLOW = new AwanSink(AwanSink.GATE_GLOW, false);
	/** Test festival (37G {@code uji langit_sekte pesta}): client ticks left. */
	private static int forced;

	static AwanSink lanterns() {
		return LANTERNS;
	}

	static AwanSink glow() {
		return GLOW;
	}

	static void force(int ticks) {
		forced = Math.max(forced, ticks);
	}

	static void tick() {
		if (forced > 0) {
			forced--;
		}
	}

	static void clear() {
		forced = 0;
	}

	static boolean festival(LangitSkyState.Hall hall) {
		return hall.festival() || forced > 0;
	}

	static void draw(LangitFrame f) {
		LangitSkyState.Hall hall = f.hall;
		if (!LangitClientConfig.on(LangitClientConfig.Toggle.LENTERA) || f.particleTier < 1 || !festival(hall)) {
			return;
		}
		boolean night = f.dayTime >= 12500L && f.dayTime <= 23500L;
		if (!night && forced <= 0) {
			return;
		}
		int want = LangitBudget.LANTERNS[f.particleTier];
		int granted = LangitBudget.takeUpTo(want * 2) / 2;
		BlockPos plaza = hall.throne != null ? hall.throne : hall.origin;
		long h = hall.seed * 0x9E3779B97F4A7C15L + f.day * 0xBF58476D1CE4E5B9L;
		h ^= h >>> 31;
		float wind = (h & 0xFFFF) / 65536.0F * Mth.TWO_PI;
		float wx = Mth.cos(wind);
		float wz = Mth.sin(wind);
		for (int i = 0; i < granted; i++) {
			float offset = KelopakJatuh.hash(hall.seed, i, 99) * LIFE;
			float age = f.seconds + offset;
			int cycle = Mth.floor(age / LIFE);
			float t = age - cycle * LIFE;
			float life = t / LIFE;
			float ang = KelopakJatuh.hash(hall.seed, i, cycle * 3 + 101) * Mth.TWO_PI;
			float rad = 3.0F + KelopakJatuh.hash(hall.seed, i, cycle * 3 + 102) * 10.0F;
			double x = plaza.getX() + 0.5D + Mth.cos(ang) * rad + wx * t * 0.35F + Mth.sin(t * 0.4F + i) * 0.6F;
			double z = plaza.getZ() + 0.5D + Mth.sin(ang) * rad + wz * t * 0.35F + Mth.cos(t * 0.33F + i) * 0.6F;
			// a slow start as the paper fills with hot air, then a steady climb
			double y = plaza.getY() + 1.5D + RISE * t * LautAwanRenderer.smooth(t / 4.0F) + 0.5F;
			float fade = Mth.clamp(t / 2.0F, 0.0F, 1.0F) * (1.0F - LautAwanRenderer.smooth((life - 0.75F) / 0.25F));
			if (fade < 0.04F) {
				continue;
			}
			float flicker = 0.85F + 0.15F * Mth.sin(f.ticks * 0.5F + i * 2.1F);
			float rx = f.rx(x);
			float ry = f.ry(y);
			float rz = f.rz(z);
			GLOW.billboard(rx, ry, rz, 1.4F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, AwanSink.argb(0.3F * fade * flicker, 0xFFB060));
			LANTERNS.billboard(rx, ry, rz, 0.45F, Mth.sin(t * 0.8F + i) * 0.08F, 0.0F, 1.0F, 0.0F, 1.0F,
					AwanSink.argb(fade, AwanSink.scale(0xFFFFFF, flicker)));
		}
	}

	private LenteraLangit() {
	}
}
