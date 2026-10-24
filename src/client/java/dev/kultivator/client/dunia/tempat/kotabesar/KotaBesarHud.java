package dev.kultivator.client.dunia.tempat.kotabesar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The capital's HUD (DESIGN 38.B.4.3, 38.B.6.3): the defence bar at the top centre while a beast tide attacks a gate the
 * player is near ("Gelombang 2/3", monsters left, the gate's health as a red-gold bar that cracks when broken), and the
 * formation ring around the crosshair while the player channels a teleport or waits for the cloud boat. Fills, arcs and
 * text only (no shader, no texture), hidden with F1.
 */
final class KotaBesarHud implements HudElement {

	private static final int MERAH = 0xE0483A;
	private static final int EMAS = 0xFFD060;
	private static final int GIOK = 0x5FE8C0;
	private static final int AWAN = 0xCFE6FF;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || minecraft.gui.hud.isHidden()) {
			return;
		}
		int width = g.guiWidth();
		int height = g.guiHeight();
		Font font = minecraft.font;
		if (KotaBesarFx.pertahanan && Math.hypot(player.getX() - KotaBesarFx.gerbangX, player.getZ() - KotaBesarFx.gerbangZ) < 160.0D) {
			pertahanan(g, font, width);
		}
		if (KotaBesarFx.kanalSampai != 0L) {
			float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
			float span = Math.max(1.0F, KotaBesarFx.kanalSampai - KotaBesarFx.kanalMulai);
			float progress = Mth.clamp((level.getGameTime() + partial - KotaBesarFx.kanalMulai) / span, 0.0F, 1.0F);
			int rgb = KotaBesarFx.kanalAwan ? AWAN : GIOK;
			float cx = width / 2.0F;
			float cy = height / 2.0F;
			Brush.arc(g, cx, cy, 12.0F, 14.5F, 0.0F, 1.0F, Palette.argb(0x60, 0x101418));
			Brush.arc(g, cx, cy, 12.0F, 14.5F, 0.0F, progress, Palette.opaque(rgb));
			for (int i = 0; i < 8; i++) {
				float ang = Mth.TWO_PI * i / 8.0F + progress * Mth.PI;
				Brush.dot(g, cx + Mth.cos(ang) * 19.0F, cy + Mth.sin(ang) * 19.0F, 1.2F, Palette.argb(0xB0, rgb));
			}
			Brush.centered(g, font, Component.translatable(KotaBesarFx.kanalAwan ? "kultivator.kota_besar.hud.awan" : "kultivator.kota_besar.hud.kanal",
					Math.round(progress * 100.0F)), cx, cy + 20.0F, Palette.opaque(rgb), 0.8F, true);
		}
	}

	private static void pertahanan(GuiGraphicsExtractor g, Font font, int width) {
		int w = 182;
		int x0 = (width - w) / 2;
		int y0 = 22;
		int x1 = x0 + w;
		int y1 = y0 + 30;
		Brush.chamferGradient(g, x0, y0, x1, y1, 3, Palette.argb(0xC8, 0x1A0C0A), Palette.argb(0xC8, 0x2A1410));
		Brush.frame(g, x0, y0, x1, y1, Palette.argb(0xA0, MERAH));
		Brush.rect(g, x0 + 2.0F, y0 + 2.0F, x0 + 14.0F, y1 - 2.0F, Palette.argb(0xD0, Palette.darken(Palette.opaque(MERAH), 0.45F)));
		Brush.centered(g, font, Component.literal("獸"), x0 + 8.0F, y0 + 10.0F, 0xFFF4EAD0, 0.8F, false);
		Component title = Component.translatable("kultivator.kota_besar.hud.gelombang", Math.max(1, KotaBesarFx.gelombang), KotaBesarFx.total, KotaBesarFx.sisa);
		Brush.text(g, font, title, x0 + 18.0F, y0 + 4.0F, Palette.opaque(EMAS), 0.85F, true);
		float hp = Mth.clamp(KotaBesarFx.gerbang, 0.0F, 1.0F);
		float bx0 = x0 + 18.0F;
		float bx1 = x1 - 6.0F;
		float by0 = y0 + 17.0F;
		float by1 = y0 + 24.0F;
		Brush.rect(g, bx0, by0, bx1, by1, Palette.argb(0x90, 0x0A0606));
		int barRgb = KotaBesarFx.roboh ? 0x7A2A22 : hp > 0.5F ? EMAS : MERAH;
		Brush.rect(g, bx0 + 1.0F, by0 + 1.0F, bx0 + 1.0F + (bx1 - bx0 - 2.0F) * hp, by1 - 1.0F, Palette.opaque(barRgb));
		// crack marks once the gate starts to give
		for (int i = 1; i <= 3; i++) {
			if (hp < 1.0F - i * 0.25F) {
				float cxk = bx0 + (bx1 - bx0) * (0.25F * i);
				Brush.line(g, cxk - 2.0F, by0, cxk + 1.5F, by1, 1.0F, Palette.argb(0xC0, 0x000000));
			}
		}
		Component gate = Component.translatable(KotaBesarFx.roboh ? "kultivator.kota_besar.hud.roboh" : "kultivator.kota_besar.hud.gerbang",
				Math.round(hp * 100.0F));
		Brush.centered(g, font, gate, (bx0 + bx1) / 2.0F, by0 + 0.5F, 0xFFFFF4E0, 0.6F, true);
	}
}
