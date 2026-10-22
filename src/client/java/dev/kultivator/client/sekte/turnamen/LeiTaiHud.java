package dev.kultivator.client.sekte.turnamen;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.sekte.turnamen.TurnamenPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A small hanging scroll in the top-right corner while the player stands within 40 blocks of a running Lingkar Lei Tai
 * (DESIGN 36.2.3): the season and bout number, the pair now fighting (or the last result), and the champion's name for a
 * few seconds after the final. HUD only (no post chain); hidden with F1, the debug screen or the HUD switch of
 * {@code EffectQuality}. The Sword Duel's own HUD and the old boss bars stay as they are.
 */
final class LeiTaiHud implements HudElement {

	private static final double RANGE = 40.0D;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden() || minecraft.getDebugOverlay().showDebugScreen() || !EffectQuality.hudVisible()) {
			return;
		}
		LeiTaiRenderer.Ring ring = LeiTaiRenderer.nearest(minecraft.player.position(), RANGE);
		if (ring == null) {
			return;
		}
		double now = LeiTaiRenderer.seconds();
		float appear = (float) Mth.clamp((now - ring.since) / 0.6D, 0.0D, 1.0D);
		if (appear <= 0.0F) {
			return;
		}
		int width = g.guiWidth();
		float w = 150.0F;
		float x1 = width - 8.0F;
		float x0 = x1 - w;
		float y0 = 8.0F;
		float y1 = y0 + 34.0F;
		int alpha = (int) (0xD8 * appear);
		Brush.rect(g, x0 - 3.0F, y0 - 2.0F, x1 + 3.0F, y0 + 1.0F, Palette.argb(alpha, Palette.ROD_LIGHT));
		Brush.rect(g, x0, y0, x1, y1, Palette.argb((int) (0xE0 * appear), Palette.PAPER_TOP));
		Brush.rect(g, x0 - 3.0F, y1 - 1.0F, x1 + 3.0F, y1 + 2.0F, Palette.argb(alpha, Palette.ROD_DARK));
		int rgb = ring.kind == TurnamenPayloads.KIND_GUESTS ? LeiTaiRenderer.JADE
				: ring.kind == TurnamenPayloads.KIND_ELITE || ring.has(TurnamenPayloads.FLAG_FINAL) ? LeiTaiRenderer.GOLD : LeiTaiRenderer.CINNABAR;
		Brush.disc(g, x0 + 9.0F, y0 + 9.0F, 6.0F, Palette.argb(alpha, rgb));
		Brush.centered(g, minecraft.font, Component.literal("擂"), x0 + 9.0F, y0 + 5.5F, Palette.argb(alpha, 0xFFF4E0), 0.8F, false);
		Component head = Component.translatable(ring.has(TurnamenPayloads.FLAG_FINAL) ? "kultivator.sekte.turnamen.hud.final"
				: "kultivator.sekte.turnamen.hud.judul", ring.season, ring.bout);
		Brush.fit(g, minecraft.font, Brush.bold(head), x0 + 18.0F, y0 + 4.0F, w - 22.0F, Palette.argb(alpha, Palette.INK), 0.68F, 0.45F, false);
		Component body;
		int color = Palette.INK_SOFT;
		if (ring.has(TurnamenPayloads.FLAG_CHAMPION)) {
			body = Component.translatable("kultivator.sekte.turnamen.hud.juara", ring.nameA);
			color = Palette.GOLD_DARK;
		} else if (ring.has(TurnamenPayloads.FLAG_FIGHTING)) {
			body = Component.translatable("kultivator.sekte.turnamen.hud.lawan", ring.nameA, ring.nameB);
			float pulse = 0.5F + 0.5F * Mth.sin((float) (now * 4.0D));
			color = Palette.mix(Palette.SEAL_DARK, Palette.SEAL, pulse);
		} else if (!ring.nameA.getString().isEmpty()) {
			body = Component.translatable("kultivator.sekte.turnamen.hud.hasil", ring.nameA, ring.nameB);
		} else {
			body = Component.translatable("kultivator.sekte.turnamen.hud.tunggu");
		}
		Brush.fit(g, minecraft.font, body, x0 + 6.0F, y0 + 19.0F, w - 10.0F, Palette.argb(alpha, color), 0.62F, 0.4F, false);
	}
}
