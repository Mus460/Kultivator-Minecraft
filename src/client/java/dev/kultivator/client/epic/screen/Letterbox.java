package dev.kultivator.client.epic.screen;

import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.camera.SkipKey;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The letterbox bars (DESIGN 23.8.1): 11 % of the screen height each, eased in and out over 8 ticks, ink-black with a
 * 1-px gold inner line ({@code bar_trim.png}); the bottom bar carries the skip hint with the bound key's real name. Bars
 * show only while a shot runs, a FULL card shows in a safe beat, or a 1-s "flick" (Penuh first casts). "Bilah layar
 * lebar" off or a double press of the skip key (for the rest of that scene) hides them.
 */
public final class Letterbox {

	public static final float HEIGHT = 0.11F;
	public static final long EASE_MILLIS = 400L;
	private static final Identifier TRIM = Kultivator.id("textures/gui/epic/bar_trim.png");
	private static final int INK = 0xF20A0B0E;

	private static float amount;
	private static long lastMillis;
	private static long flickUntil;
	private static @Nullable Object suppressed;

	/** Bars only (no camera move) for {@code ticks}. */
	public static void flick(int ticks) {
		flickUntil = Util.getMillis() + ticks * 50L;
	}

	/** A double press: no bars for the rest of {@code scene}. */
	public static void suppress(@Nullable Object scene) {
		suppressed = scene;
	}

	/** Ends a suppression when its scene ended. */
	public static void sceneEnded(Object scene) {
		if (suppressed == scene) {
			suppressed = null;
		}
	}

	public static void clear() {
		amount = 0.0F;
		flickUntil = 0L;
		suppressed = null;
	}

	/** 0..1, how far the bars are in. */
	public static float amount() {
		return amount;
	}

	/** True while the bars want to be shown. */
	public static boolean wanted() {
		if (!EpicSettings.letterbox() || suppressed != null) {
			return false;
		}
		return CinematicDirector.active() || TitleCard.fullInSafeBeat() || Util.getMillis() < flickUntil;
	}

	/** Advances the ease (once per HUD frame) and draws the bars. */
	static void draw(GuiGraphicsExtractor g) {
		long now = Util.getMillis();
		long dt = lastMillis == 0L ? 0L : Math.min(100L, now - lastMillis);
		lastMillis = now;
		float step = dt / (float) EASE_MILLIS;
		amount = wanted() ? Math.min(1.0F, amount + step) : Math.max(0.0F, amount - step);
		if (amount <= 0.0F) {
			return;
		}
		float eased = amount * amount * (3.0F - 2.0F * amount);
		int width = g.guiWidth();
		int height = g.guiHeight();
		float share = HEIGHT;
		if (dev.kultivator.client.epic.camera.CinematicDirector.filmShooting()) {
			// Phase 30 (DESIGN 30.2.7 LETTERBOX): the film ratio 2.39:1, recomputed from the window aspect
			share = Math.max(HEIGHT, Math.min(0.2F, (height - width / 2.39F) / 2.0F / Math.max(1, height)));
		}
		int bar = Math.round(height * share * eased);
		if (bar <= 0) {
			return;
		}
		g.fill(0, 0, width, bar, INK);
		g.fill(0, height - bar, width, height, INK);
		g.blit(RenderPipelines.GUI_TEXTURED, TRIM, 0, bar - 2, 0.0F, 0.0F, width, 2, 64, 2, 64, 4);
		g.blit(RenderPipelines.GUI_TEXTURED, TRIM, 0, height - bar, 0.0F, 2.0F, width, 2, 64, 2, 64, 4);
		if (eased > 0.6F && CinematicDirector.active()) {
			Minecraft minecraft = Minecraft.getInstance();
			Component hint = Component.translatable("kultivator.momen.skip_hint", SkipKey.keyName());
			int alpha = Math.round(170 * (eased - 0.6F) / 0.4F);
			float scale = Math.max(0.6F, Math.min(1.0F, bar / 22.0F));
			float textW = minecraft.font.width(hint) * scale;
			Brush.text(g, minecraft.font, hint, width - textW - 8.0F, height - bar / 2.0F - 4.0F * scale, Palette.argb(alpha, 0xD9C9A0), scale, false);
		}
	}

	private Letterbox() {
	}
}
