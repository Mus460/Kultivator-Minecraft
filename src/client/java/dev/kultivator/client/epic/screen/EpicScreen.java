package dev.kultivator.client.epic.screen;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.fx.spec.ScreenFlash;
import dev.kultivator.client.screen.ui.Palette;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The {@code kultivator:epic_screen} HUD element, right after {@code MISC_OVERLAYS} (DESIGN 23.8.1, 23.11.1): the sky dim
 * and tint (23.9.4), the grey wash, the ink edge and vignette, the chromatic fringe and the letterbox bars, in that order;
 * the boss bars, health, the Qi HUD and the tribulation HUD draw above it. Every effect is a HUD effect (no post chain).
 * Also the frame guard's one {@code System.nanoTime()} per frame. F1 hides it.
 * <p>
 * Comfort (23.1.6): at most one full-screen luminance event per 500 ms (HUD flashes and the sky flash of one strike count
 * as one), every flash obeys vanilla "Hide Lightning Flashes", "Kilatan layar" and {@code screenEffectScale}; the
 * chromatic fringe is Normal+, ≥ 3 s apart and never under "Kurangi gerak".
 */
public final class EpicScreen implements HudElement {

	private static final Identifier VIGNETTE = Kultivator.id("textures/gui/epic/vignette.png");
	private static final Identifier INK_EDGE = Kultivator.id("textures/gui/epic/ink_edge.png");
	public static final float MAX_FLASH = 0.35F;
	public static final long LUMINANCE_GAP = 500L;

	private static int edgeRgb;
	private static float edgeAlpha;
	private static long edgeStart;
	private static long edgeEnd;
	private static int vignetteRgb;
	private static float vignetteAlpha;
	private static long vignetteStart;
	private static long vignetteEnd;
	private static float greyAlpha;
	private static long greyStart;
	private static long greyEnd;
	private static long chromaStart;
	private static long chromaEnd;
	private static long lastChroma;
	private static long lastLuminance;

	// ================================================================ effects

	/** A mottled ink edge in {@code rgb} for {@code ticks} (≤ 4 s). */
	public static void inkEdge(int rgb, float alpha, int ticks) {
		long now = Util.getMillis();
		edgeRgb = rgb & 0xFFFFFF;
		edgeAlpha = Mth.clamp(alpha, 0.0F, 0.8F);
		edgeStart = now;
		edgeEnd = now + Math.min(80, Math.max(4, ticks)) * 50L;
	}

	/** A radial vignette in {@code rgb} (α ≤ 0.35). */
	public static void vignette(int rgb, float alpha, int ticks) {
		long now = Util.getMillis();
		vignetteRgb = rgb & 0xFFFFFF;
		vignetteAlpha = Math.min(0.35F, alpha);
		vignetteStart = now;
		vignetteEnd = now + Math.max(4, ticks) * 50L;
	}

	/** A grey wash (α ≤ 0.2, ≤ 2 s) standing in for desaturation. */
	public static void grey(float alpha, int ticks) {
		long now = Util.getMillis();
		greyAlpha = Math.min(0.2F, alpha);
		greyStart = now;
		greyEnd = now + Math.min(40, Math.max(4, ticks)) * 50L;
	}

	/** The chromatic fringe for 4-8 ticks (Normal+, ≥ 3 s apart, never with "Kurangi gerak"). */
	public static void chroma(int ticks) {
		long now = Util.getMillis();
		if (EffectQuality.reduceMotion() || EpicBudget.tierOrMinimal() < 1 || now - lastChroma < 3000L) {
			return;
		}
		lastChroma = now;
		chromaStart = now;
		chromaEnd = now + Mth.clamp(ticks, 4, 8) * 50L;
	}

	/**
	 * A full-screen flash and, with it, the vanilla sky flash ({@code ClientLevel.setSkyFlashTime}): one luminance event,
	 * dropped within 500 ms of the last one or with "Hide Lightning Flashes". α ≤ 0.35 × {@code screenEffectScale}.
	 */
	public static void flash(int rgb, float alpha, int ticks, int skyFlash) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!tryLuminance()) {
			return;
		}
		if (alpha > 0.0F) {
			ScreenFlash.add(rgb, Math.min(MAX_FLASH, alpha), Math.min(ScreenFlash.MAX_TICKS, ticks));
		}
		if (skyFlash > 0 && minecraft.level != null) {
			minecraft.level.setSkyFlashTime(skyFlash);
		}
	}

	/**
	 * The shared luminance gate (Phase 10, DESIGN 28.1.2): true when a full-screen luminance event may play now, and then
	 * counts it (the next one waits {@link #LUMINANCE_GAP}). False with vanilla "Hide Lightning Flashes". Used by the
	 * 7b flashes, the impact frames and the cloud wipes, so any two of them are at least 500 ms apart.
	 */
	public static boolean tryLuminance() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.options.hideLightningFlash().get()) {
			return false;
		}
		long now = Util.getMillis();
		if (now - lastLuminance < LUMINANCE_GAP) {
			return false;
		}
		lastLuminance = now;
		return true;
	}

	public static void clear() {
		edgeEnd = 0L;
		vignetteEnd = 0L;
		greyEnd = 0L;
		chromaEnd = 0L;
	}

	private static boolean anything(long now) {
		return now < edgeEnd || now < vignetteEnd || now < greyEnd || now < chromaEnd || SkyDirector.active() || Letterbox.amount() > 0.0F
				|| Letterbox.wanted();
	}

	// ================================================================ drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		EpicBudget.frame();
		long now = Util.getMillis();
		if (!anything(now)) {
			return;
		}
		long start = System.nanoTime();
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden()) {
			return;
		}
		int width = g.guiWidth();
		int height = g.guiHeight();
		float effect = (float) (double) minecraft.options.screenEffectScale().get();
		// Sky dim and tint (the primary sky channel under a shader pack).
		float top = SkyDirector.hudTop();
		float tint = SkyDirector.hudTint();
		if (top > 0.005F || tint > 0.005F) {
			int rgb = SkyDirector.hudColor();
			if (top > 0.005F) {
				g.fillGradient(0, 0, width, Math.round(height * 0.6F), Palette.argb(Math.round(top * 255.0F), rgb), Palette.argb(0, rgb));
			}
			if (tint > 0.005F) {
				g.fill(0, 0, width, height, Palette.argb(Math.round(tint * 255.0F), rgb));
			}
		}
		if (now < greyEnd) {
			float a = envelope(now, greyStart, greyEnd) * greyAlpha;
			g.fill(0, 0, width, height, Palette.argb(Math.round(a * 255.0F), 0x8A8A8A));
		}
		if (now < edgeEnd) {
			float a = envelope(now, edgeStart, edgeEnd) * edgeAlpha * Math.max(0.4F, effect);
			g.blit(RenderPipelines.GUI_TEXTURED, INK_EDGE, 0, 0, 0.0F, 0.0F, width, height, 256, 256, 256, 256,
					Palette.argb(Math.round(a * 255.0F), edgeRgb));
		}
		if (now < vignetteEnd) {
			float a = envelope(now, vignetteStart, vignetteEnd) * vignetteAlpha * Math.max(0.4F, effect);
			g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, 256, 256, 256, 256,
					Palette.argb(Math.round(a * 255.0F), vignetteRgb));
		}
		if (now < chromaEnd && effect > 0.0F) {
			float a = (1.0F - (now - chromaStart) / (float) Math.max(1L, chromaEnd - chromaStart)) * 0.22F * effect;
			int band = Math.max(4, width / 40);
			int alpha = Math.round(a * 255.0F);
			g.fillGradient(0, 0, band * 2, height, Palette.argb(alpha, 0xFF2030), Palette.argb(alpha / 2, 0xFF2030));
			g.fill(band * 2, 0, band * 3, height, Palette.argb(alpha / 3, 0xFF2030));
			g.fillGradient(width - band * 2, 0, width, height, Palette.argb(alpha / 2, 0x20F0FF), Palette.argb(alpha, 0x20F0FF));
			g.fill(width - band * 3, 0, width - band * 2, height, Palette.argb(alpha / 3, 0x20F0FF));
		}
		Letterbox.draw(g);
		EpicBudget.addCpu(System.nanoTime() - start);
	}

	private static float envelope(long now, long start, long end) {
		float t = (now - start) / (float) Math.max(1L, end - start);
		return t < 0.15F ? t / 0.15F : t > 0.7F ? Math.max(0.0F, (1.0F - t) / 0.3F) : 1.0F;
	}
}
