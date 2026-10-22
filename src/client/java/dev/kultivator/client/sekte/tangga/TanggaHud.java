package dev.kultivator.client.sekte.tangga;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.sekte.tangga.JalurTangga;
import dev.kultivator.sekte.tangga.TanggaPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * A small hanging scroll at the right edge while the player is near a running stair (DESIGN 36.2.4): the trial's phase
 * and, for each climber, its name and a jade bar of the 1000 steps with the three illusions marked, the bar gold at the
 * summit, grey for one that turned back, the result word after the reading. Below the tournament's scroll (module T's
 * sits at the top), HUD only, hidden with F1, the debug screen or the HUD switch of {@code EffectQuality}.
 */
final class TanggaHud implements HudElement {

	private static final double RANGE = 96.0D;
	private static final float TOP = 48.0F;
	private static final float WIDTH = 156.0F;
	private static final float ROW = 12.0F;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null || minecraft.gui.hud.isHidden() || minecraft.getDebugOverlay().showDebugScreen()
				|| !EffectQuality.hudVisible()) {
			return;
		}
		TanggaIlusi.Stair stair = TanggaIlusi.nearest(minecraft.player.position(), RANGE);
		if (stair == null || stair.has(TanggaPayloads.FLAG_PREVIEW) || stair.climbers.isEmpty()) {
			return;
		}
		float appear = Mth.clamp(stair.visible, 0.0F, 1.0F);
		if (appear <= 0.02F) {
			return;
		}
		double now = TanggaIlusi.clock() + deltaTracker.getGameTimeDeltaPartialTick(false);
		int rows = Math.min(JalurTangga.LANES, stair.climbers.size());
		float x1 = g.guiWidth() - 8.0F;
		float x0 = x1 - WIDTH;
		float y0 = TOP;
		float y1 = y0 + 18.0F + rows * ROW;
		int alpha = (int) (0xD8 * appear);
		Brush.rect(g, x0 - 3.0F, y0 - 2.0F, x1 + 3.0F, y0 + 1.0F, Palette.argb(alpha, Palette.ROD_LIGHT));
		Brush.rect(g, x0, y0, x1, y1, Palette.argb((int) (0xE0 * appear), Palette.PAPER_TOP));
		Brush.rect(g, x0 - 3.0F, y1 - 1.0F, x1 + 3.0F, y1 + 2.0F, Palette.argb(alpha, Palette.ROD_DARK));
		Brush.disc(g, x0 + 9.0F, y0 + 9.0F, 6.0F, Palette.argb(alpha, Palette.JADE));
		Brush.centered(g, minecraft.font, Component.literal("梯"), x0 + 9.0F, y0 + 5.5F, Palette.argb(alpha, 0xFFF4E0), 0.8F, false);
		Component head = Component.translatable(stair.has(TanggaPayloads.FLAG_HEART) ? "kultivator.sekte.tangga.hud.heart" : "kultivator.sekte.tangga.hud.title");
		Component phase = Component.translatable("kultivator.sekte.tangga.hud.phase." + Mth.clamp(stair.phase, 1, 5));
		Brush.fit(g, minecraft.font, Brush.bold(head), x0 + 18.0F, y0 + 3.0F, WIDTH - 22.0F, Palette.argb(alpha, Palette.INK), 0.66F, 0.45F, false);
		Brush.fit(g, minecraft.font, phase, x0 + 18.0F, y0 + 10.5F, WIDTH - 22.0F, Palette.argb(alpha, Palette.INK_SOFT), 0.5F, 0.4F, false);
		float y = y0 + 18.0F;
		int shown = 0;
		for (TanggaIlusi.Climber c : stair.climbers.values()) {
			if (shown++ >= rows) {
				break;
			}
			Entity entity = minecraft.level.getEntity(c.entityId);
			Component name = entity == null ? Component.literal("?") : entity.getName();
			float step = c.limit >= 0 ? c.limit : c.shownStep(now);
			Brush.fit(g, minecraft.font, name, x0 + 5.0F, y + 1.5F, 46.0F, Palette.argb(alpha, Palette.INK), 0.5F, 0.35F, false);
			float bx0 = x0 + 54.0F;
			float bx1 = x1 - 30.0F;
			float by0 = y + 3.0F;
			float by1 = y + 7.0F;
			Brush.rect(g, bx0, by0, bx1, by1, Palette.argb(alpha, Palette.PAPER_DEEP));
			int fill = c.state == TanggaPayloads.C_PEAK || step >= JalurTangga.STEPS ? Palette.GOLD
					: c.state == TanggaPayloads.C_GAVE_UP ? Palette.LOCKED : c.state == TanggaPayloads.C_KNEEL ? Palette.STORED : Palette.JADE_LIGHT;
			float filled = bx0 + (bx1 - bx0) * Mth.clamp(step / JalurTangga.STEPS, 0.0F, 1.0F);
			Brush.rect(g, bx0, by0, filled, by1, Palette.argb(alpha, fill));
			for (int mark : new int[]{300, 600, 900}) {
				float mx = bx0 + (bx1 - bx0) * mark / JalurTangga.STEPS;
				Brush.rect(g, mx - 0.5F, by0 - 1.0F, mx + 0.5F, by1 + 1.0F, Palette.argb(alpha, Palette.SEAL_DARK));
			}
			Component right = c.limit >= 0
					? Component.translatable("kultivator.sekte.tangga.hati_short." + c.verdict)
					: Component.literal(Integer.toString(Math.round(step)));
			Brush.fit(g, minecraft.font, right, bx1 + 3.0F, y + 1.5F, 26.0F, Palette.argb(alpha, c.limit >= JalurTangga.STEPS ? Palette.GOLD_DARK
					: Palette.INK_SOFT), 0.5F, 0.35F, false);
			y += ROW;
		}
	}
}
