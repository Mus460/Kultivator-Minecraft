package dev.kultivator.client.dunia.dimensi;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.film.FilmDirector;
import dev.kultivator.client.fx.spec.ScreenFlash;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import dev.kultivator.dunia.dimensi.DimensiLevels;

/**
 * The soul's own view in Dunia Bawah (DESIGN 34.5.13 "a pale client tint on the own view edges"): a breathing pale
 * vignette (never above 0.2 alpha, through the one edge painter {@link ScreenFlash#tepi}, DESIGN 43.9) and,
 * under the hotbar's top, the soul time left as a small jade-ink line that warms to amber in the last minute. Luka Jiwa
 * shows as a faint red line. Hidden with F1 and while a film plays.
 * <p>
 * At sea (DESIGN 34.5.14 "horizon haze planes", faked for weak laptops): a soft band of haze laid along the horizon line
 * of Laut Tanpa Batas, two HUD gradients placed from the camera's pitch and field of view, so the endless sea melts into
 * the sky without fog settings or a shader. It thins as the player climbs above the water and is off underwater and on
 * Minimal effects; the blood eclipse above tints it rose, the twin moons a pale jade.
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
		if (DimensiLevels.isLaut(minecraft.level)) {
			seaHaze(g, minecraft, width, height, deltaTracker.getGameTimeDeltaPartialTick(false));
		}
		if (DimensiState.soul()) {
			float t = (minecraft.player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(false)) / 60.0F;
			float breath = 0.7F + 0.3F * (float) (0.5D - 0.5D * Math.cos(Math.PI * 2.0D * t));
			long left = DimensiState.soulLeft();
			int rgb = left < 20L * 60L ? 0xF0C080 : DimensiFx.SOUL;
			ScreenFlash.tepi(rgb, MAX_ALPHA * breath);
			long seconds = left / 20L;
			Component line = Component.translatable("kultivator.dimensi.hud.jiwa", seconds / 60L, String.format(java.util.Locale.ROOT, "%02d", seconds % 60L));
			float y = height - 62.0F;
			Brush.centered(g, minecraft.font, line, width / 2.0F, y, Palette.argb(0xE0, rgb), 1.0F, true);
		}
		long luka = DimensiState.lukaLeft();
		// the soul timer above is a danger and always shows; the wound line follows the "HUD Kultivator" coin (audit M5, S30)
		if (luka > 0L && EffectQuality.hudVisible()) {
			long minutes = luka / 1200L + 1L;
			Brush.centered(g, minecraft.font, Component.translatable("kultivator.dimensi.hud.luka", minutes), width / 2.0F, height - 72.0F,
					Palette.argb(0xC0, 0xE08080), 1.0F, true);
		}
	}

	/** The haze band on the sea's horizon (see the class note). */
	private static void seaHaze(GuiGraphicsExtractor g, Minecraft minecraft, int width, int height, float partial) {
		if (EffectQuality.minimal() || minecraft.player.isUnderWater()) {
			return;
		}
		double above = minecraft.player.getEyeY() - (DimensiLevels.LAUT_SEA_TOP + 1.0D);
		float fade = (float) Mth.clamp(1.0D - above / 80.0D, 0.0D, 1.0D);
		if (fade <= 0.02F) {
			return;
		}
		float pitch = minecraft.player.getViewXRot(partial);
		if (Math.abs(pitch) > 70.0F) {
			return;
		}
		double halfFov = Math.toRadians(Math.max(30, minecraft.options.fov().get()) / 2.0D);
		float horizon = (float) (height / 2.0D - Math.tan(Math.toRadians(pitch)) * (height / 2.0D) / Math.tan(halfFov));
		float band = height * 0.16F;
		if (horizon < -band || horizon > height + band) {
			return;
		}
		int sky = DimensiState.sky();
		int rgb = sky == 2 ? 0xF0B8B0 : sky == 3 ? 0xC8F0E0 : 0xDDF4FA;
		int alpha = Math.round((EffectQuality.maximal() ? 0.30F : 0.22F) * 255.0F * fade);
		int haze = Palette.argb(alpha, rgb);
		int clear = Palette.argb(0, rgb);
		int top = Math.round(horizon - band);
		int mid = Math.round(horizon);
		int bottom = Math.round(horizon + band * 0.45F);
		g.fillGradient(0, Math.max(0, top), width, Math.max(0, Math.min(height, mid)), clear, haze);
		g.fillGradient(0, Math.max(0, mid), width, Math.max(0, Math.min(height, bottom)), haze, clear);
	}
}
