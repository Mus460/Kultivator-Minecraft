package dev.kultivator.client.epic.screen;

import dev.kultivator.Kultivator;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.technique.spec.dsl.Dampak;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Bingkai Dampak (Phase 10, DESIGN 28.4.1): the impact frames of an Abadi or Dewa climax, a HUD layer drawn right before
 * the chat (above the hotbar, under nothing but the chat). No post chain: tints are HUD fills, the inverted frame is the
 * vanilla {@code RenderPipelines.GUI_INVERT} fill and the streaks are one texture.
 * <ul>
 * <li><b>Penuh</b> (opt-in after the photosensitivity warning): tick 0 {@code frame1} (inverted, black, or a tint α 0.45),
 * tick 1 {@code frame2} α 0.8 with radial streaks turned by the seed, tick 2 {@code frame3} α 0.5 → 0;</li>
 * <li><b>Lembut</b> (default): one tint of the first colour, α ≤ {@link EpicScreen#MAX_FLASH}, fading over 3 ticks, with
 * faint streaks (none under "Kurangi gerak"); an inverted frame shows as a white tint;</li>
 * <li><b>Mati</b>: nothing (the caller does not call {@link #play}).</li>
 * </ul>
 * Every frame is one luminance event ({@link EpicScreen#tryLuminance}: 500 ms apart, never with "Hide Lightning
 * Flashes"), is × {@code screenEffectScale}, and is hidden by F1. Client thread only.
 */
public final class ImpactFrame implements HudElement {

	private static final Identifier STREAKS = Kultivator.id("textures/gui/dampak/dampak_streaks.png");
	private static final long TICK = 50L;

	private static long start;
	private static boolean penuh;
	private static boolean streaks;
	private static float alpha;
	private static int seed;
	private static int c1;
	private static int c2;
	private static int c3;
	/** The Lembut tint (the first real colour of the row). */
	private static int soft;

	/**
	 * Plays the frames of one climax. Colours are RGB or the {@link Dampak} markers {@code INV}, {@code BLACK} and
	 * {@code NO_FRAME} (the caller resolved {@code DAO}). {@code full} = Penuh, {@code alphaScale} 1 for one's own cast and
	 * 0.5 for a bystander. Returns false when the luminance gate refused it (nothing shows).
	 */
	public static boolean play(int frame1, int frame2, int frame3, boolean full, float alphaScale, boolean withStreaks, int frameSeed) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || alphaScale <= 0.0F) {
			return false;
		}
		int first = firstColour(frame1, frame2, frame3);
		if (first == Dampak.NO_FRAME) {
			return false;
		}
		if (!EpicScreen.tryLuminance()) {
			return false;
		}
		start = Util.getMillis();
		penuh = full;
		streaks = withStreaks;
		alpha = Math.min(1.0F, alphaScale);
		seed = frameSeed;
		c1 = frame1;
		c2 = frame2;
		c3 = frame3;
		soft = first;
		return true;
	}

	/** True while a frame covers the screen (a masked camera cut may jump under it). */
	public static boolean showing() {
		if (start == 0L) {
			return false;
		}
		long elapsed = Util.getMillis() - start;
		return elapsed >= 0L && elapsed < (penuh ? 3 : 2) * TICK;
	}

	public static void clear() {
		start = 0L;
	}

	/** The Lembut colour: the first frame that is a colour; INV reads as white, BLACK as black. */
	private static int firstColour(int a, int b, int c) {
		for (int value : new int[]{a, b, c}) {
			if (value == Dampak.INV) {
				return 0xFFFFFF;
			}
			if (value == Dampak.BLACK) {
				return 0x000000;
			}
			if (value >= 0) {
				return value & 0xFFFFFF;
			}
		}
		return Dampak.NO_FRAME;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		if (start == 0L) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		long elapsed = Util.getMillis() - start;
		long total = 3 * TICK;
		if (elapsed >= total || elapsed < 0L) {
			start = 0L;
			return;
		}
		if (minecraft.player == null || minecraft.gui.hud.isHidden()) {
			return;
		}
		float effect = (float) (double) minecraft.options.screenEffectScale().get();
		if (effect <= 0.0F) {
			return;
		}
		int width = g.guiWidth();
		int height = g.guiHeight();
		float scale = effect * alpha;
		if (!penuh) {
			// Lembut: one tint ≤ 0.35, fading over the three ticks (23.1.6 kept exactly)
			float t = elapsed / (float) total;
			float a = EpicScreen.MAX_FLASH * scale * (1.0F - t);
			if (a > 0.01F) {
				g.fill(0, 0, width, height, Palette.argb(Math.round(a * 255.0F), soft));
			}
			if (streaks && a > 0.02F) {
				streaks(g, width, height, soft, a * 0.8F);
			}
			return;
		}
		int tick = (int) (elapsed / TICK);
		float inTick = (elapsed - tick * TICK) / (float) TICK;
		int colour = tick == 0 ? c1 : tick == 1 ? c2 : c3;
		float base = tick == 0 ? 0.45F : tick == 1 ? 0.8F : 0.5F * (1.0F - inTick);
		float a = base * scale;
		if (colour == Dampak.INV) {
			// the vanilla invert blend: one full-screen fill, no post chain
			g.fill(RenderPipelines.GUI_INVERT, 0, 0, width, height, -1);
		} else if (colour == Dampak.BLACK) {
			g.fill(0, 0, width, height, Palette.argb(Math.round(Math.min(0.9F, a + 0.3F) * 255.0F), 0x000000));
		} else if (colour >= 0 && a > 0.01F) {
			g.fill(0, 0, width, height, Palette.argb(Math.round(Math.min(1.0F, a) * 255.0F), colour));
		}
		if (tick == 1 && streaks) {
			int streakColour = colour >= 0 ? colour : colour == Dampak.BLACK ? 0x000000 : 0xFFFFFF;
			streaks(g, width, height, streakColour, Math.min(1.0F, 0.9F * scale));
		}
	}

	/** The radial impact streaks around the screen centre, turned by the seed. */
	private static void streaks(GuiGraphicsExtractor g, int width, int height, int rgb, float a) {
		int alphaByte = Math.round(Math.min(1.0F, a) * 255.0F);
		if (alphaByte <= 2) {
			return;
		}
		int size = (int) Math.ceil(Math.hypot(width, height));
		g.pose().pushMatrix();
		g.pose().translate(width / 2.0F, height / 2.0F);
		g.pose().rotate((float) ((seed & 1023) / 1024.0D * Math.PI * 2.0D));
		g.blit(RenderPipelines.GUI_TEXTURED, STREAKS, -size / 2, -size / 2, 0.0F, 0.0F, size, size, 256, 256, 256, 256,
				Palette.argb(alphaByte, rgb));
		g.pose().popMatrix();
	}
}
