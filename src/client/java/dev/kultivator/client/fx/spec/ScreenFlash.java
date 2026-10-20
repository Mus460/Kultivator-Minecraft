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
		if (!EffectQuality.flash() || EffectQuality.reduceMotion() || !Float.isFinite(alpha) || alpha <= 0.0F) {
			return;
		}
		long now = Util.getMillis();
		if (now - lastFlash < GAP_MILLIS || now < suppressUntil) {
			return;
		}
		lastFlash = now;
		flashRgb = rgb & 0xFFFFFF;
		flashAlpha = Math.min(MAX_ALPHA, alpha);
		flashStart = now;
		flashEnd = now + Math.max(1, Math.min(MAX_TICKS, ticks)) * 50L;
	}

	private static long suppressUntil;

	/**
	 * Phase 10 (DESIGN 28.4.1): drops every technique flash for the next {@code ticks} (the impact frame of a climax is the
	 * one luminance event; the spec's own climax flash layer would be a second). Vignettes and speed lines still play.
	 */
	public static void suppress(int ticks) {
		suppressUntil = Math.max(suppressUntil, Util.getMillis() + Math.max(0, Math.min(20, ticks)) * 50L);
	}

	/** An edge vignette of {@code rgb} for {@code ticks}. */
	public static void vignette(int rgb, int ticks) {
		long now = Util.getMillis();
		vignetteRgb = rgb & 0xFFFFFF;
		vignetteStart = now;
		vignetteEnd = now + Math.max(10, Math.min(200, ticks)) * 50L;
	}

	/** Speed lines over the screen edges in {@code rgb} for {@code ticks} (one's own dashes and blinks, S16). */
	public static void speedLines(int rgb, int ticks) {
		if (EffectQuality.reduceMotion() || ticks <= 0) {
			return;
		}
		long now = Util.getMillis();
		speedRgb = rgb & 0xFFFFFF;
		speedStart = now;
		speedEnd = now + Math.max(2, Math.min(20, ticks)) * 50L;
		speedSeed = (int) (now & 0x7FFFFFFF);
	}

	private static int speedRgb;
	private static long speedStart;
	private static long speedEnd;
	private static int speedSeed;

	static void clear() {
		flashEnd = 0L;
		vignetteEnd = 0L;
		speedEnd = 0L;
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
			if (now < speedEnd) {
				// radial streaks converging on the centre, thickest at the edges (motion, not a camera snap)
				float t = (now - speedStart) / (float) Math.max(1L, speedEnd - speedStart);
				float strength = (t < 0.2F ? t / 0.2F : 1.0F - (t - 0.2F) / 0.8F) * Math.max(0.3F, effect);
				java.util.Random random = new java.util.Random(speedSeed + (now - speedStart) / 50L);
				int lines = 26;
				float cx = width / 2.0F;
				float cy = height / 2.0F;
				for (int i = 0; i < lines; i++) {
					double angle = random.nextDouble() * Math.PI * 2.0D;
					float inner = 0.55F + random.nextFloat() * 0.25F;
					int alpha = Math.round(120 * strength * (0.5F + random.nextFloat() * 0.5F));
					if (alpha <= 4) {
						continue;
					}
					int color = Palette.argb(alpha, speedRgb);
					float reach = (float) Math.hypot(cx, cy);
					int steps = 8;
					for (int k = 0; k < steps; k++) {
						float f = inner + (1.0F - inner) * k / steps;
						int x = Math.round(cx + (float) Math.cos(angle) * reach * f);
						int y = Math.round(cy + (float) Math.sin(angle) * reach * f);
						int size = 1 + k / 3;
						g.fill(x, y, x + size, y + size, color);
					}
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
