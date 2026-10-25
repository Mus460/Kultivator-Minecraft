package dev.kultivator.client.kehancuran;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.spec.ScreenFlash;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * The red sky of a Mode Kehancuran countdown (DESIGN 20.3.21): the whole dimension sees a crimson edge (the one edge
 * painter {@link ScreenFlash#tepi}, DESIGN 43.9) that deepens and pulses like a slow heartbeat as the countdown runs
 * out, with a small seal and the seconds left at the top. Drawn only while the server's countdown runs; hidden with F1.
 */
final class RedSkyHud implements HudElement {

	private static final int CRIMSON = 0x8A0F0A;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !KehancuranClient.skyActive() || minecraft.gui.hud.isHidden()) {
			return;
		}
		// Phase 7b (DESIGN 23.13 D5): a 7b sky grade replaces the red sky; this stays the fallback with "Langit berubah" off.
		if (dev.kultivator.client.epic.sky.SkyDirector.coversRedSky()) {
			return;
		}
		int width = g.guiWidth();
		float progress = KehancuranClient.skyProgress();
		long now = Util.getMillis();
		// A slow double beat, faster in the last seconds.
		int left = KehancuranClient.skySecondsLeft();
		double period = left <= 5 ? 600.0D : 1200.0D;
		double phase = now % (long) period / period;
		float beat = (float) Math.max(Math.exp(-Math.pow((phase - 0.08D) * 14.0D, 2.0D)), 0.6D * Math.exp(-Math.pow((phase - 0.28D) * 14.0D, 2.0D)));
		float strength = EffectQuality.minimal() ? 0.6F : 1.0F;
		// the crimson edge, through the one edge painter (DESIGN 43.9); it deepens and beats as the countdown runs out
		ScreenFlash.tepi(CRIMSON, (40 + 70 * progress + 30 * beat) * strength / 255.0F);

		// The seal and the seconds left at the top centre.
		float cx = width / 2.0F;
		float sy = 6.0F;
		Brush.rect(g, cx - 7.0F, sy, cx + 7.0F, sy + 14.0F, Palette.argb(0xD0, Palette.SEAL));
		Brush.frame(g, Math.round(cx - 6.0F), Math.round(sy + 1.0F), Math.round(cx + 6.0F), Math.round(sy + 13.0F), Palette.argb(0xC0, 0x1A0000));
		Brush.rect(g, cx - 3.5F, sy + 6.5F, cx + 3.5F, sy + 7.5F, 0xD0F1D57E);
		Brush.rect(g, cx - 0.5F, sy + 3.0F, cx + 0.5F, sy + 7.0F, 0xD0F1D57E);
		Component text = Component.translatable("kultivator.kehancuran.ui.sky", left);
		Brush.centered(g, minecraft.font, Brush.bold(text), cx, sy + 17.0F, Palette.argb(0xFF, 0xFF6A5A), 1.0F, true);
	}
}
