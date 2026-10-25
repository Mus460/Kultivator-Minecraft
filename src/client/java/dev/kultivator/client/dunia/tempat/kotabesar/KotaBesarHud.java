package dev.kultivator.client.dunia.tempat.kotabesar;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.codex.CodexHud;
import dev.kultivator.client.hud.Kabar;
import dev.kultivator.client.hud.SlotSisi;
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
 * The capital's HUD (DESIGN 38.B.4.3, 38.B.6.3, 43.9): the gate defence scroll in the right-side slot ({@link SlotSisi},
 * priority {@link SlotSisi#GERBANG}) while a beast tide attacks a gate the player is near ("Gelombang 2/3", monsters left,
 * the gate's health as a red-gold bar that cracks when broken), and the formation ring around the crosshair
 * ({@link CodexHud#cincin}) while the player channels a teleport or waits for the cloud boat. Fills, arcs and text only
 * (no shader, no texture), hidden with F1.
 */
final class KotaBesarHud implements HudElement {

	private static final int MERAH = 0xE0483A;
	private static final int GIOK = 0x5FE8C0;
	private static final int AWAN = 0xCFE6FF;
	private static final int W = 150;
	private static final int H = 36;

	KotaBesarHud() {
		SlotSisi.daftar(SlotSisi.GERBANG, KotaBesarHud::dekatGerbang);
	}

	/** A beast tide at a gate within 160 blocks. */
	private static boolean dekatGerbang() {
		Player player = Minecraft.getInstance().player;
		return player != null && KotaBesarFx.pertahanan
				&& Math.hypot(player.getX() - KotaBesarFx.gerbangX, player.getZ() - KotaBesarFx.gerbangZ) < 160.0D;
	}

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
		if (EffectQuality.hudVisible() && !minecraft.getDebugOverlay().showDebugScreen() && SlotSisi.giliran(SlotSisi.GERBANG)) {
			pertahanan(g, font, width, height);
		}
		if (KotaBesarFx.kanalSampai != 0L) {
			float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
			float span = Math.max(1.0F, KotaBesarFx.kanalSampai - KotaBesarFx.kanalMulai);
			float progress = Mth.clamp((level.getGameTime() + partial - KotaBesarFx.kanalMulai) / span, 0.0F, 1.0F);
			int rgb = KotaBesarFx.kanalAwan ? AWAN : GIOK;
			float cx = width / 2.0F;
			float cy = height / 2.0F;
			// the turning formation beads outside the one crosshair ring (DESIGN 43.9)
			for (int i = 0; i < 8; i++) {
				float ang = Mth.TWO_PI * i / 8.0F + progress * Mth.PI;
				Brush.dot(g, cx + Mth.cos(ang) * 17.0F, cy + Mth.sin(ang) * 17.0F, 1.2F, Palette.argb(0xB0, rgb));
			}
			CodexHud.cincin(g, cx, cy, progress, rgb, Component.translatable(KotaBesarFx.kanalAwan ? "kultivator.kota_besar.hud.awan"
					: "kultivator.kota_besar.hud.kanal", Math.round(progress * 100.0F)));
		}
	}

	private static void pertahanan(GuiGraphicsExtractor g, Font font, int width, int height) {
		int x1 = SlotSisi.kanan(width);
		int x0 = x1 - W;
		int y0 = SlotSisi.atas(height, H);
		int y1 = y0 + H;
		Kabar.slip(g, x0, y0, x1, y1, Palette.opaque(MERAH));
		Brush.disc(g, x0 + 12.0F, y0 + 10.0F, 7.0F, Palette.darken(Palette.opaque(MERAH), 0.2F));
		Brush.centered(g, font, Component.literal("獸"), x0 + 12.5F, y0 + 6.0F, 0xFFFFF4E0, 1.0F, false);
		Component title = Component.translatable("kultivator.kota_besar.hud.gelombang", Math.max(1, KotaBesarFx.gelombang), KotaBesarFx.total, KotaBesarFx.sisa);
		Brush.fit(g, font, Brush.bold(title), x0 + 22.0F, y0 + 5.0F, W - 26.0F, Palette.INK, 1.0F, 1.0F, false);
		float hp = Mth.clamp(KotaBesarFx.gerbang, 0.0F, 1.0F);
		float bx0 = x0 + 6.0F;
		float bx1 = x1 - 6.0F;
		float by0 = y0 + 18.0F;
		float by1 = y0 + 23.0F;
		Brush.rect(g, bx0, by0, bx1, by1, Palette.argb(0xC0, Palette.PAPER_DEEP));
		int barRgb = KotaBesarFx.roboh ? 0x7A2A22 : hp > 0.5F ? 0xD0A030 : MERAH;
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
		Brush.fit(g, font, gate, bx0, y0 + 25.0F, bx1 - bx0, KotaBesarFx.roboh ? Palette.SEAL_DARK : Palette.INK_SOFT, 1.0F, 1.0F, false);
	}
}
