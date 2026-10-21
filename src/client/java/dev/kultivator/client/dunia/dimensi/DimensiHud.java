package dev.kultivator.client.dunia.dimensi;

import dev.kultivator.client.epic.film.FilmDirector;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The soul's own view in Dunia Bawah (DESIGN 34.5.13 "a pale client tint on the own view edges"): a breathing pale
 * vignette of four gradient strips (never above 0.2 alpha, a HUD fill: unaffected by shader packs, no post chain) and,
 * under the hotbar's top, the soul time left as a small jade-ink line that warms to amber in the last minute. Luka Jiwa
 * shows as a faint red line. Hidden with F1 and while a film plays.
 */
final class DimensiHud implements HudElement {

	private static final float MAX_ALPHA = 0.2F;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden() || minecraft.player.isSpectator() || FilmDirector.playing()) {
			return;
		}
		int width = g.guiWidth();
		int height = g.guiHeight();
		if (DimensiState.soul()) {
			float t = (minecraft.player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(false)) / 60.0F;
			float breath = 0.7F + 0.3F * (float) (0.5D - 0.5D * Math.cos(Math.PI * 2.0D * t));
			long left = DimensiState.soulLeft();
			int rgb = left < 20L * 60L ? 0xF0C080 : DimensiFx.SOUL;
			edges(g, width, height, rgb, MAX_ALPHA * breath);
			long seconds = left / 20L;
			Component line = Component.translatable("kultivator.dimensi.hud.jiwa", seconds / 60L, String.format(java.util.Locale.ROOT, "%02d", seconds % 60L));
			float y = height - 62.0F;
			Brush.centered(g, minecraft.font, line, width / 2.0F, y, Palette.argb(0xE0, rgb), 0.85F, true);
		}
		long luka = DimensiState.lukaLeft();
		if (luka > 0L) {
			long minutes = luka / 1200L + 1L;
			Brush.centered(g, minecraft.font, Component.translatable("kultivator.dimensi.hud.luka", minutes), width / 2.0F, height - 72.0F,
					Palette.argb(0xC0, 0xE08080), 0.7F, true);
		}
	}

	private static void edges(GuiGraphicsExtractor g, int width, int height, int rgb, float alpha) {
		int band = Math.max(12, Math.min(width, height) / 4);
		int edge = Palette.argb(Math.round(alpha * 255.0F), rgb);
		int clear = Palette.argb(0, rgb);
		g.fillGradient(0, 0, width, band, edge, clear);
		g.fillGradient(0, height - band, width, height, clear, edge);
		int step = 2;
		for (int i = 0; i < band; i += step) {
			float k = 1.0F - i / (float) band;
			int c = Palette.argb(Math.round(alpha * 255.0F * k * k), rgb);
			g.fill(i, 0, i + step, height, c);
			g.fill(width - i - step, 0, width - i, height, c);
		}
	}
}
