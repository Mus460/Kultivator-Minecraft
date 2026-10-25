package dev.kultivator.client.sekte.tangga;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.hud.Kabar;
import dev.kultivator.client.hud.SlotSisi;
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
 * The stair-trial scroll in the right-side slot ({@link SlotSisi}, priority {@link SlotSisi#TANGGA}; DESIGN 36.2.4, 43.9)
 * while the player is near a running stair: the trial's phase and, for each climber, its name and a jade bar of the 1000
 * steps with the three illusions marked, the bar gold at the summit, grey for one that turned back, the result word after
 * the reading. HUD only, hidden with F1, the debug screen or the HUD switch of {@code EffectQuality}.
 */
final class TanggaHud implements HudElement {

	private static final double RANGE = 96.0D;
	private static final int W = 156;
	private static final int ROW = 11;

	TanggaHud() {
		SlotSisi.daftar(SlotSisi.TANGGA, () -> {
			Minecraft minecraft = Minecraft.getInstance();
			return minecraft.player != null && tampil(TanggaIlusi.nearest(minecraft.player.position(), RANGE));
		});
	}

	private static boolean tampil(TanggaIlusi.Stair stair) {
		return stair != null && !stair.has(TanggaPayloads.FLAG_PREVIEW) && !stair.climbers.isEmpty() && stair.visible > 0.02F;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null || minecraft.gui.hud.isHidden() || minecraft.getDebugOverlay().showDebugScreen()
				|| !EffectQuality.hudVisible() || !SlotSisi.giliran(SlotSisi.TANGGA)) {
			return;
		}
		TanggaIlusi.Stair stair = TanggaIlusi.nearest(minecraft.player.position(), RANGE);
		if (!tampil(stair)) {
			return;
		}
		float appear = Mth.clamp(stair.visible, 0.0F, 1.0F);
		double now = TanggaIlusi.clock() + deltaTracker.getGameTimeDeltaPartialTick(false);
		int rows = Math.min(JalurTangga.LANES, stair.climbers.size());
		int h = 26 + rows * ROW + 3;
		int x1 = SlotSisi.kanan(g.guiWidth()) + Math.round((1.0F - appear) * (W + 8));
		int x0 = x1 - W;
		int y0 = SlotSisi.atas(g.guiHeight(), h);
		Kabar.slip(g, x0, y0, x1, y0 + h, Palette.JADE);
		Brush.disc(g, x0 + 12.0F, y0 + 10.0F, 7.0F, Palette.JADE_DARK);
		Brush.centered(g, minecraft.font, Component.literal("梯"), x0 + 12.5F, y0 + 6.0F, 0xFFFFF4E0, 1.0F, false);
		Component head = Component.translatable(stair.has(TanggaPayloads.FLAG_HEART) ? "kultivator.sekte.tangga.hud.heart" : "kultivator.sekte.tangga.hud.title");
		Component phase = Component.translatable("kultivator.sekte.tangga.hud.phase." + Mth.clamp(stair.phase, 1, 5));
		Brush.fit(g, minecraft.font, Brush.bold(head), x0 + 22.0F, y0 + 5.0F, W - 26.0F, Palette.INK, 1.0F, 1.0F, false);
		Brush.fit(g, minecraft.font, phase, x0 + 22.0F, y0 + 15.0F, W - 26.0F, Palette.INK_SOFT, 1.0F, 1.0F, false);
		float y = y0 + 26.0F;
		int shown = 0;
		for (TanggaIlusi.Climber c : stair.climbers.values()) {
			if (shown++ >= rows) {
				break;
			}
			Entity entity = minecraft.level.getEntity(c.entityId);
			Component name = entity == null ? Component.literal("?") : entity.getName();
			float step = c.limit >= 0 ? c.limit : c.shownStep(now);
			Brush.fit(g, minecraft.font, name, x0 + 6.0F, y + 1.0F, 46.0F, Palette.INK, 1.0F, 1.0F, false);
			float bx0 = x0 + 55.0F;
			float bx1 = x1 - 30.0F;
			float by0 = y + 3.0F;
			float by1 = y + 7.0F;
			Brush.rect(g, bx0, by0, bx1, by1, Palette.PAPER_DEEP);
			int fill = c.state == TanggaPayloads.C_PEAK || step >= JalurTangga.STEPS ? Palette.GOLD
					: c.state == TanggaPayloads.C_GAVE_UP ? Palette.LOCKED : c.state == TanggaPayloads.C_KNEEL ? Palette.STORED : Palette.JADE;
			float filled = bx0 + (bx1 - bx0) * Mth.clamp(step / JalurTangga.STEPS, 0.0F, 1.0F);
			Brush.rect(g, bx0, by0, filled, by1, fill);
			for (int mark : new int[]{300, 600, 900}) {
				float mx = bx0 + (bx1 - bx0) * mark / JalurTangga.STEPS;
				Brush.rect(g, mx - 0.5F, by0 - 1.0F, mx + 0.5F, by1 + 1.0F, Palette.SEAL_DARK);
			}
			Component right = c.limit >= 0
					? Component.translatable("kultivator.sekte.tangga.hati_short." + c.verdict)
					: Component.literal(Integer.toString(Math.round(step)));
			Brush.fit(g, minecraft.font, right, bx1 + 3.0F, y + 1.0F, 25.0F, c.limit >= JalurTangga.STEPS ? Palette.GOLD_DARK : Palette.INK_SOFT,
					1.0F, 1.0F, false);
			y += ROW;
		}
	}
}
