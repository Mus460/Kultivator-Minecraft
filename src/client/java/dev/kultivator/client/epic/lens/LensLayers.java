package dev.kultivator.client.epic.lens;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.film.FilmLens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The film's lens and HUD layers (DESIGN 30.2.7): grain, flare and ghosts, the anamorphic streak, light leaks, the whip
 * smear, the impact fringe and the 2-tick black dip that masks a hard cut. Drawn from the {@code epic_screen} HUD element
 * (after the 7b layers, before the letterbox) with {@code RenderPipelines.GUI_TEXTURED} blits only: no post chain. The
 * per-shot mask comes from {@code FilmDirector}; the budgets of 30.2.13 and the setting "Lapisan lensa" (Nyala / Ringan:
 * letterbox, vignette, smear and speed lines only / Mati) decide what draws. Nothing allocates per frame. Idle cost: one
 * boolean. Client thread only.
 */
public final class LensLayers {

	private static boolean on;
	private static int mask;
	private static int tint = 0xFFE8B0;
	private static long seed;
	private static long shotStart;
	private static @Nullable Vec3 lightPoint;
	// the black dip
	private static long dipStart;
	private static final long DIP_HOLD = 100L;
	private static final long DIP_OUT = 100L;
	// the impact fringe
	private static long fringeEnd;
	private static long fringeStart;
	// motion (whip smear)
	private static float smear;
	private static float smearYaw;
	private static float smearPitch;
	private static long ticks;

	/** "Lapisan lensa": the stored choice. */
	public enum Setting { NYALA, RINGAN, MATI }

	/** A film shot starts: its layer mask, tint and light point (null = none). */
	public static void shot(int layerMask, int rgb, long shotSeed, @Nullable Vec3 light) {
		on = true;
		mask = layerMask;
		tint = rgb & 0xFFFFFF;
		seed = shotSeed;
		shotStart = Util.getMillis();
		lightPoint = light;
		Flare.reset();
		if (has(FilmLens.LIGHT_LEAK)) {
			LightLeak.start(tint, (int) (shotSeed & 3), 14);
		}
	}

	/** Moves the light point (a falling sword, a rising sun), once per tick from the director. */
	public static void light(@Nullable Vec3 point) {
		lightPoint = point;
	}

	/** The film stopped showing (between acts, at the end): every layer fades. */
	public static void off() {
		on = false;
		mask = 0;
		lightPoint = null;
		smear = 0.0F;
	}

	public static void clear() {
		off();
		dipStart = 0L;
		fringeEnd = 0L;
		LightLeak.clear();
		Flare.reset();
	}

	/** A 2-tick black dip (the mask of a hard cut; not a luminance event). */
	public static void dip() {
		dipStart = Util.getMillis();
	}

	/** 0..1: how much of the screen the dip covers now. */
	public static float dipCover() {
		if (dipStart == 0L) {
			return 0.0F;
		}
		long t = Util.getMillis() - dipStart;
		if (t < 0L) {
			return 0.0F;
		}
		if (t <= DIP_HOLD) {
			return 1.0F;
		}
		return Math.max(0.0F, 1.0F - (t - DIP_HOLD) / (float) DIP_OUT);
	}

	/** The RGB-offset edge copies of an impact (4 ticks; Normal+, never with "Kurangi gerak"). */
	public static void fringe(int fringeTicks) {
		if (EffectQuality.reduceMotion() || !full()) {
			return;
		}
		fringeStart = Util.getMillis();
		fringeEnd = fringeStart + Math.max(2, fringeTicks) * 50L;
	}

	/** The camera's turn rate this frame (degrees per tick) from the film path: drives the smear. */
	public static void motion(float yawPerTick, float pitchPerTick, boolean whipping) {
		float speed = Math.abs(yawPerTick) + Math.abs(pitchPerTick);
		float wanted = has(FilmLens.SMEAR) ? Mth.clamp((speed - 6.0F) / (whipping ? 30.0F : 45.0F), 0.0F, 0.9F) : 0.0F;
		smear = Math.max(wanted, smear * 0.8F);
		if (speed > 0.5F) {
			smearYaw = yawPerTick;
			smearPitch = pitchPerTick;
		}
	}

	/** The smear level now (a masked cut may hide under ≥ 0.8). */
	public static float smear() {
		return smear;
	}

	/** True while any layer may draw. */
	public static boolean active() {
		return on || dipStart != 0L || Util.getMillis() < fringeEnd || LightLeak.active();
	}

	private static int testLeft;

	/** {@code /sinematik lensa}: one layer alone for {@code ticks}, its light point {@code light}. */
	public static void test(FilmLens lens, int testTicks, @Nullable Vec3 light) {
		shot(FilmLens.mask(FilmLens.LETTERBOX, lens), 0xFFE8B0, Util.getMillis(), light);
		testLeft = Math.max(1, testTicks);
		if (lens == FilmLens.FRINGE) {
			fringe(Math.min(8, testTicks));
		}
		if (lens == FilmLens.SMEAR) {
			smear = 0.85F;
			smearYaw = 30.0F;
		}
	}

	/** Once per client tick. */
	public static void tick() {
		ticks++;
		if (testLeft > 0) {
			if (has(FilmLens.SMEAR)) {
				smear = Math.max(smear, 0.85F);
			}
			if (--testLeft == 0) {
				off();
			}
		}
		if (!on) {
			smear *= 0.6F;
		}
		if (on && (ticks & 3) == 0 && lightPoint != null && has(FilmLens.FLARE)) {
			Flare.occlusion(lightPoint);
		}
		LightLeak.tick();
	}

	public static Setting setting() {
		return EpicSettings.lens();
	}

	/** True when the shot asked for {@code lens}. */
	static boolean has(FilmLens lens) {
		return on && FilmLens.has(mask, lens);
	}

	/** Normal+ budget, lens Nyala. */
	static boolean full() {
		return setting() == Setting.NYALA && EpicBudget.tierOrMinimal() >= 1;
	}

	/** Maximal budget, lens Nyala. */
	static boolean maximal() {
		return setting() == Setting.NYALA && EpicBudget.tierOrMinimal() >= 2;
	}

	static int tint() {
		return tint;
	}

	static long seed() {
		return seed;
	}

	static long ticks() {
		return ticks;
	}

	static long shotStart() {
		return shotStart;
	}

	// ================================================================ drawing (from EpicScreen)

	public static void draw(GuiGraphicsExtractor g) {
		Minecraft minecraft = Minecraft.getInstance();
		int width = g.guiWidth();
		int height = g.guiHeight();
		float effect = (float) (double) minecraft.options.screenEffectScale().get();
		Setting setting = setting();
		if (on && setting != Setting.MATI) {
			if (full() && has(FilmLens.GRAIN)) {
				Grain.draw(g, width, height, seed + ticks);
			}
			if (full() && LightLeak.active()) {
				LightLeak.draw(g, width, height, effect);
			}
			if (full() && has(FilmLens.FLARE) && lightPoint != null) {
				Flare.draw(g, width, height, lightPoint, tint, maximal() ? 6 : 3, maximal() && has(FilmLens.ANAMORPHIC), effect);
			}
		}
		if (smear > 0.05F && setting != Setting.MATI) {
			int strokes = EpicBudget.tierOrMinimal() == 0 || setting == Setting.RINGAN ? 4 : maximal() ? 10 : 7;
			Smear.draw(g, width, height, smear, smearYaw, smearPitch, strokes, seed + ticks / 2, tint);
		}
		long now = Util.getMillis();
		if (now < fringeEnd && setting == Setting.NYALA && effect > 0.0F) {
			float a = (1.0F - (now - fringeStart) / (float) Math.max(1L, fringeEnd - fringeStart)) * 0.3F * effect;
			int alpha = Math.round(a * 255.0F);
			int band = Math.max(3, width / 60);
			g.fill(1, 0, band + 1, height, (alpha << 24) | 0xFF2030);
			g.fill(0, 0, band, height, ((alpha / 2) << 24) | 0x30FF60);
			g.fill(width - band - 1, 0, width - 1, height, (alpha << 24) | 0x2060FF);
			g.fill(width - band, 0, width, height, ((alpha / 2) << 24) | 0x30FF60);
		} else if (now >= fringeEnd) {
			fringeEnd = 0L;
		}
	}

	/** The dip draws last, over the bars. */
	public static void drawDip(GuiGraphicsExtractor g) {
		float cover = dipCover();
		if (cover <= 0.0F) {
			if (dipStart != 0L && Util.getMillis() - dipStart > DIP_HOLD + DIP_OUT) {
				dipStart = 0L;
			}
			return;
		}
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), (Math.round(cover * 255.0F) << 24));
	}

	private LensLayers() {
	}
}
