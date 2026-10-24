package dev.kultivator.client.sekte.mandiri.seteru;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.sekte.api.Lambang;
import dev.kultivator.sekte.mandiri.seteru.SeteruIds;
import dev.kultivator.sekte.mandiri.seteru.SeteruPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The 2 s title card of an honour duel (DESIGN 39.F.5): "PERSETERUAN — <ours> (<our hall>) vs <theirs> (<their sect>) —
 * <cause>", shown once per duel in the upper third while the duel's own intro of DESIGN 35 runs (that intro obeys the
 * camera setting Penuh/Singkat/Mati; this card is a HUD overlay only and never moves the camera). Two halves slide in
 * from the edges on a torn ink band, ours in jade and theirs in their sect's ink with their emblem as a drawn seal; a red
 * crossed-swords seal with a soft pulsing glow sits between them, the cause and the rung below. All of it is the generated texture
 * {@code textures/sekte/mandiri/seteru/kartu.png} and fills (no post effects, Sodium/Iris safe). It follows the setting
 * "Kartu judul" (off: no card), drops the glow and the slide at the Minimal quality or under a thin {@code EpicBudget},
 * moves below a running full title card, and hides with the HUD.
 */
public final class KartuJudul implements HudElement {

	private static final Identifier TEKSTUR = Kultivator.id("textures/sekte/mandiri/seteru/kartu.png");
	private static final int TEX_W = 256;
	private static final int TEX_H = 128;
	private static final long LAMA = SeteruIds.JUDUL_DETIK * 1000L + 400L;
	private static final long MASUK = 260L;
	private static final long KELUAR = 380L;
	/** The 32 emblem symbols as seal glyphs (the order of {@code api.Lambang} symbols, as D's screen). */
	private static final String SIMBOL = "剑莲鹤龙凤虎龟山月日云雷火水木金土风星海玉竹梅松霜雪花鼎钟塔天道";

	private static @Nullable SeteruPayloads.Judul kartu;
	private static long mulai;

	/** A new card (the previous one, if any, is replaced). */
	static void tampil(SeteruPayloads.Judul judul) {
		if (!EpicSettings.cards()) {
			return;
		}
		kartu = judul;
		mulai = Util.getMillis();
	}

	static void hapus() {
		kartu = null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		SeteruPayloads.Judul j = kartu;
		if (j == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		long age = Util.getMillis() - mulai;
		if (age > LAMA || minecraft.player == null) {
			kartu = null;
			return;
		}
		if (minecraft.gui.hud.isHidden() || !EffectQuality.hudVisible()) {
			return;
		}
		Font font = minecraft.font;
		boolean plain = EffectQuality.minimal() || EpicBudget.thinGeometry();
		int width = g.guiWidth();
		int height = g.guiHeight();
		int w = Math.min(380, width - 24);
		int h = 64;
		int cx = width / 2;
		int top = (int) (height * (TitleCard.busy() ? 0.08F : 0.20F));
		float in = Mth.clamp(age / (float) MASUK, 0.0F, 1.0F);
		float out = Mth.clamp((LAMA - age) / (float) KELUAR, 0.0F, 1.0F);
		float alpha = Math.min(plain ? Math.min(1.0F, age / 150.0F) : 1.0F, out);
		float ease = plain ? 1.0F : 1.0F - (1.0F - in) * (1.0F - in) * (1.0F - in);
		int slide = Math.round((1.0F - ease) * (w / 2.0F + 24.0F));
		// -------------------------------------------------- the torn ink band (two halves)
		int a8 = Math.round(alpha * 235.0F);
		int halfW = w / 2;
		int bandTint = Palette.argb(a8, 0xFFFFFF);
		g.blit(RenderPipelines.GUI_TEXTURED, TEKSTUR, cx - halfW - slide, top + 8, 0.0F, 0.0F, halfW, 48, 128, 48, TEX_W, TEX_H, bandTint);
		g.blit(RenderPipelines.GUI_TEXTURED, TEKSTUR, cx + slide, top + 8, 128.0F, 0.0F, halfW, 48, 128, 48, TEX_W, TEX_H, bandTint);
		// side colour strokes: jade for ours, the sect's ink for theirs
		int ours = Palette.argb(Math.round(alpha * 255.0F), j.warnaKita() & 0xFFFFFF);
		int theirs = Palette.argb(Math.round(alpha * 255.0F), j.warnaMereka() & 0xFFFFFF);
		g.fill(cx - halfW - slide + 6, top + 50, cx - 24 - slide, top + 52, ours);
		g.fill(cx + 24 + slide, top + 50, cx + halfW + slide - 6, top + 52, theirs);
		// -------------------------------------------------- the header
		Component head = Component.translatable(SeteruIds.k("judul.kepala"));
		Brush.centered(g, font, head, cx, top, Palette.fade(Palette.GOLD_LIGHT, alpha), 0.7F, true);
		// -------------------------------------------------- the two sides
		float nameW = halfW - 44.0F;
		Brush.fitCentered(g, font, Brush.bold(j.kita()), cx - halfW / 2.0F - slide - 6.0F, top + 18, nameW, Palette.fade(0xFFE8FFF4, alpha),
				1.25F, 0.6F, true);
		Brush.fitCentered(g, font, j.aulaKita(), cx - halfW / 2.0F - slide - 6.0F, top + 34, nameW, Palette.fade(Palette.lighten(ours, 0.35F),
				alpha), 0.7F, 0.45F, true);
		float theirX = cx + halfW / 2.0F + slide + 6.0F;
		Brush.fitCentered(g, font, Brush.bold(j.mereka()), theirX + 6.0F, top + 18, nameW - 14.0F, Palette.fade(0xFFFFF0E6, alpha), 1.25F,
				0.6F, true);
		Brush.fitCentered(g, font, j.sekteMereka(), theirX + 6.0F, top + 34, nameW - 14.0F, Palette.fade(Palette.lighten(theirs, 0.35F), alpha),
				0.7F, 0.45F, true);
		lambang(g, font, j.lambang(), cx + halfW + slide - 18.0F, top + 30.0F, 9.0F, alpha);
		// -------------------------------------------------- the seal between them, with its glow
		float pop = plain ? 1.0F : Mth.clamp((age - 120L) / 220.0F, 0.0F, 1.0F);
		float scale = plain ? 1.0F : (pop < 1.0F ? 0.4F + 0.8F * pop - 0.2F * pop * pop : 1.0F + 0.04F * Mth.sin(age / 120.0F));
		int seal = Math.round(30 * scale);
		if (!plain && pop > 0.0F) {
			float pulse = 0.55F + 0.25F * Mth.sin(age / 90.0F);
			int glow = Math.round(seal * 2.4F);
			g.blit(RenderPipelines.GUI_TEXTURED, TEKSTUR, cx - glow / 2, top + 32 - glow / 2, 64.0F, 48.0F, glow, glow, 64, 64, TEX_W, TEX_H,
					Palette.argb(Math.round(alpha * pulse * 200.0F), 0xFF4A2A));
			// rising heat wisps behind the seal
			int wisp = Math.round(alpha * 150.0F * Math.min(1.0F, j.tingkat() / 4.0F + 0.35F));
			int wy = top + 6 - (int) (age / 40L % 8L);
			g.blit(RenderPipelines.GUI_TEXTURED, TEKSTUR, cx - 32, wy, 128.0F, 48.0F, 64, 32, 128, 32, TEX_W, TEX_H, Palette.argb(wisp, 0xFFB070));
		}
		if (pop > 0.0F) {
			g.blit(RenderPipelines.GUI_TEXTURED, TEKSTUR, cx - seal / 2, top + 32 - seal / 2, 0.0F, 48.0F, seal, seal, 64, 64, TEX_W, TEX_H,
					Palette.argb(Math.round(alpha * 255.0F), 0xFFFFFF));
		}
		// -------------------------------------------------- the cause and the rung
		Component sebab = Component.translatable(SeteruIds.k("judul.sebab"), Component.translatable(SeteruIds.k("sebab." + j.sebab())),
				Component.translatable(SeteruIds.k("tingkat." + Math.max(0, Math.min(7, j.tingkat())))));
		Brush.fitCentered(g, font, sebab, cx, top + h - 2, w - 40.0F, Palette.fade(0xFFE6D2B0, alpha), 0.72F, 0.5F, true);
	}

	/** Their emblem as a small drawn seal (border, field, symbol). */
	private static void lambang(GuiGraphicsExtractor g, Font font, int packed, float x, float y, float r, float alpha) {
		Lambang l = Lambang.unpack(packed);
		Brush.disc(g, x, y, r + 1.2F, Palette.fade(Palette.opaque(l.borderRgb()), alpha));
		Brush.disc(g, x, y, r - 0.3F, Palette.fade(Palette.opaque(l.fieldRgb()), alpha));
		Component glyph = Component.literal(String.valueOf(SIMBOL.charAt(Math.floorMod(l.symbol(), SIMBOL.length()))));
		float s = r / 6.0F;
		Brush.text(g, font, glyph, x - font.width(glyph) * s / 2.0F, y - 4.0F * s, Palette.fade(Palette.opaque(l.chargeRgb()), alpha), s, false);
	}
}
