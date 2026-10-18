package dev.kultivator.client.fx.spec;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.screen.ui.Palette;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/**
 * Full-screen technique flashes and edge vignettes (DESIGN 22.4.5), drawn as a HUD fill before the chat:
 * a flash has α ≤ 0.35, lasts ≤ 8 ticks, comes ≥ 10 ticks after the previous one, is × {@code screenEffectScale}, is
 * hidden in F1 and switched off by the "Kilatan layar" setting or "Kurangi gerak". A vignette darkens the screen edges
 * in a colour for a while (Langit+ ultimates).
 */
public final class ScreenFlash {

	public static final float MAX_ALPHA = 0.35F;
	public static final int MAX_TICKS = 8;
	public static final long GAP_MILLIS = 500L;

	private static int flashRgb;
	private static float flashAlpha;
	private static long flashStart;
	private static long flashEnd;
	private static long lastFlash;

	private static int vignetteRgb;
	private static long vignetteStart;
	private static long vignetteEnd;

	public static void add(int rgb, float alpha, int ticks) {
		if (!EffectQuality.flash() || EffectQuality.reduceMotion() || alpha <= 0.0F) {
			return;
		}
		long now = Util.getMillis();
		if (now - lastFlash < GAP_MILLIS) {
			return;
		}
		lastFlash = now;
		flashRgb = rgb & 0xFFFFFF;
		flashAlpha = Math.min(MAX_ALPHA, alpha);
		flashStart = now;
		flashEnd = now + Math.max(1, Math.min(MAX_TICKS, ticks)) * 50L;
	}

	/** An edge vignette of {@code rgb} for {@code ticks}. */
	public static void vignette(int rgb, int ticks) {
		long now = Util.getMillis();
		vignetteRgb = rgb & 0xFFFFFF;
		vignetteStart = now;
		vignetteEnd = now + Math.max(10, Math.min(200, ticks)) * 50L;
	}

	static void clear() {
		flashEnd = 0L;
		vignetteEnd = 0L;
	}

	/** The HUD element (registered by {@link SpecFx#init}). */
	static final class Hud implements HudElement {
		@Override
		public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player == null || minecraft.gui.hud.isHidden()) {
				return;
			}
			long now = Util.getMillis();
			float effect = (float) (double) minecraft.options.screenEffectScale().get();
			int width = g.guiWidth();
			int height = g.guiHeight();
			if (now < vignetteEnd) {
				float t = (now - vignetteStart) / (float) Math.max(1L, vignetteEnd - vignetteStart);
				float strength = (t < 0.15F ? t / 0.15F : t > 0.75F ? (1.0F - t) / 0.25F : 1.0F) * Math.max(0.3F, effect);
				int band = Math.max(8, Math.min(width, height) / 6);
				int edge = Palette.argb(Math.round(150 * strength), vignetteRgb);
				int clear = Palette.argb(0, vignetteRgb);
				g.fillGradient(0, 0, width, band, edge, clear);
				g.fillGradient(0, height - band, width, height, clear, edge);
				for (int i = 0; i < band; i += 2) {
					int alpha = Math.round(150 * strength * (1.0F - i / (float) band));
					if (alpha <= 0) {
						continue;
					}
					int color = Palette.argb(alpha, vignetteRgb);
					g.fill(i, band, i + 2, height - band, color);
					g.fill(width - i - 2, band, width - i, height - band, color);
				}
			}
			if (now < flashEnd && effect > 0.0F) {
				float t = (now - flashStart) / (float) Math.max(1L, flashEnd - flashStart);
				float alpha = flashAlpha * effect * (1.0F - t);
				if (alpha > 0.01F) {
					g.fill(0, 0, width, height, Palette.argb(Math.round(alpha * 255.0F), flashRgb));
				}
			}
		}
	}

	private ScreenFlash() {
	}
}
