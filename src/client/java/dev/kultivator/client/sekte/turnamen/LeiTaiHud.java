package dev.kultivator.client.sekte.turnamen;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.hud.Kabar;
import dev.kultivator.client.hud.SlotSisi;
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
 * The Lingkar Lei Tai scroll in the right-side slot ({@link SlotSisi}, priority {@link SlotSisi#LEI_TAI}; DESIGN 36.2.3,
 * 43.9) while the player stands within 40 blocks of a running ring: the season and bout number, the pair now fighting
 * (or the last result), and the champion's name for a few seconds after the final. HUD only (no post chain); hidden with
 * F1, the debug screen or the HUD switch of {@code EffectQuality}. The Sword Duel's own HUD and the old boss bars stay as
 * they are.
 */
final class LeiTaiHud implements HudElement {

	private static final double RANGE = 40.0D;
	private static final int W = 150;
	private static final int H = 30;

	LeiTaiHud() {
		SlotSisi.daftar(SlotSisi.LEI_TAI, () -> {
			Minecraft minecraft = Minecraft.getInstance();
			return minecraft.player != null && LeiTaiRenderer.nearest(minecraft.player.position(), RANGE) != null;
		});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden() || minecraft.getDebugOverlay().showDebugScreen() || !EffectQuality.hudVisible()
				|| !SlotSisi.giliran(SlotSisi.LEI_TAI)) {
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
		int x1 = SlotSisi.kanan(g.guiWidth()) + Math.round((1.0F - appear) * (W + 8));
		int x0 = x1 - W;
		int y0 = SlotSisi.atas(g.guiHeight(), H);
		int rgb = ring.kind == TurnamenPayloads.KIND_GUESTS ? LeiTaiRenderer.JADE
				: ring.kind == TurnamenPayloads.KIND_ELITE || ring.has(TurnamenPayloads.FLAG_FINAL) ? LeiTaiRenderer.GOLD : LeiTaiRenderer.CINNABAR;
		Kabar.slip(g, x0, y0, x1, y0 + H, Palette.opaque(rgb));
		Brush.disc(g, x0 + 12.0F, y0 + 10.0F, 7.0F, Palette.darken(Palette.opaque(rgb), 0.3F));
		Brush.centered(g, minecraft.font, Component.literal("擂"), x0 + 12.5F, y0 + 6.0F, 0xFFFFF4E0, 1.0F, false);
		Component head = Component.translatable(ring.has(TurnamenPayloads.FLAG_FINAL) ? "kultivator.sekte.turnamen.hud.final"
				: "kultivator.sekte.turnamen.hud.judul", ring.season, ring.bout);
		Brush.fit(g, minecraft.font, Brush.bold(head), x0 + 22.0F, y0 + 5.0F, W - 26.0F, Palette.INK, 1.0F, 1.0F, false);
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
		Brush.fit(g, minecraft.font, body, x0 + 6.0F, y0 + 18.0F, W - 10.0F, color, 1.0F, 1.0F, false);
	}
}
