package dev.kultivator.client.epic.screen;

import dev.kultivator.Kultivator;
import dev.kultivator.client.screen.ui.Palette;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * The cloud wipe of Perjalanan Langit (Phase 10, DESIGN 28.3.2): a sea of clouds that fills the screen (α 0 → 0.9) and
 * scrolls downward (the camera rises through it), so the sky ride can cut between its segments while nothing of the jump
 * is visible. Two layers of one tileable texture at different speeds give the parallax. A HUD layer (no post chain),
 * hidden by F1; every wipe is one luminance event ({@link EpicScreen#tryLuminance}). Client thread only.
 */
public final class CloudWipe implements HudElement {

	private static final Identifier CLOUDS = Kultivator.id("textures/gui/dampak/cloud_wipe.png");
	private static final float MAX_ALPHA = 0.9F;

	private static long start;
	private static long inEnd;
	private static long holdEnd;
	private static long outEnd;
	private static int rgb = 0xFFFFFF;

	/**
	 * Starts a wipe: in over {@code in} ticks, covering for {@code hold}, clearing over {@code out}. {@code tint} colours
	 * the clouds (white, or the sky ride's star-dark blue). False when the luminance gate refused it (the cut then blends).
	 */
	public static boolean start(int in, int hold, int out, int tint) {
		if (!EpicScreen.tryLuminance()) {
			return false;
		}
		long now = Util.getMillis();
		start = now;
		inEnd = now + Math.max(1, in) * 50L;
		holdEnd = inEnd + Math.max(0, hold) * 50L;
		outEnd = holdEnd + Math.max(1, out) * 50L;
		rgb = tint & 0xFFFFFF;
		return true;
	}

	/** Clears the wipe from now over {@code ticks} (the ride ended or was skipped). */
	public static void release(int ticks) {
		long now = Util.getMillis();
		if (start == 0L || now >= outEnd) {
			return;
		}
		float a = alpha(now);
		long out = Math.max(1, ticks) * 50L;
		// continue from the current alpha
		start = now - 1L;
		inEnd = now - 1L;
		holdEnd = now - (long) ((1.0F - a / MAX_ALPHA) * out);
		outEnd = holdEnd + out;
	}

	public static void clear() {
		start = 0L;
	}

	/** How much of the screen the wipe hides now (0..1; a masked cut needs ≥ 0.8). */
	public static float cover() {
		if (start == 0L) {
			return 0.0F;
		}
		return alpha(Util.getMillis()) / MAX_ALPHA;
	}

	private static float alpha(long now) {
		if (start == 0L || now >= outEnd) {
			return 0.0F;
		}
		if (now < inEnd) {
			return MAX_ALPHA * (now - start) / (float) Math.max(1L, inEnd - start);
		}
		if (now < holdEnd) {
			return MAX_ALPHA;
		}
		return MAX_ALPHA * (1.0F - (now - holdEnd) / (float) Math.max(1L, outEnd - holdEnd));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		if (start == 0L) {
			return;
		}
		long now = Util.getMillis();
		if (now >= outEnd) {
			start = 0L;
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden()) {
			return;
		}
		float a = alpha(now);
		if (a <= 0.01F) {
			return;
		}
		int width = g.guiWidth();
		int height = g.guiHeight();
		float seconds = (now - start) / 1000.0F;
		// the far layer: big puffs, slow; the near layer: smaller puffs, fast (we rise, the clouds fall past)
		layer(g, width, height, Math.max(width, height) * 0.75F, seconds * 0.9F, 0.37F, Palette.argb(Math.round(a * 255.0F), rgb));
		layer(g, width, height, Math.max(width, height) * 0.45F, seconds * 2.2F, 0.71F, Palette.argb(Math.round(a * 0.55F * 255.0F), 0xFFFFFF));
	}

	/** One scrolling layer of tiles {@code tile} pixels wide, moved down by {@code scroll} tiles, shifted {@code shift} across. */
	private static void layer(GuiGraphicsExtractor g, int width, int height, float tile, float scroll, float shift, int color) {
		int size = Math.max(16, Math.round(tile));
		float down = (scroll % 1.0F) * size;
		int x0 = -Math.round(shift * size);
		for (int y = Math.round(down) - size; y < height; y += size) {
			for (int x = x0; x < width; x += size) {
				g.blit(RenderPipelines.GUI_TEXTURED, CLOUDS, x, y, 0.0F, 0.0F, size, size, 256, 256, 256, 256, color);
			}
		}
	}
}
