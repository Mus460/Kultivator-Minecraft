package dev.kultivator.client.sekte.mandiri;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.sekte.mandiri.cerita.MandiriPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The small notice cards of DESIGN 39.7.5 in the top-right corner: a disciple broke through, came home, was wounded or
 * taken, bought something dear, learned an art, changed its path, was tempted, or a feud moved; and the "while you were
 * away" card at login. At most one new card every 5 seconds; when more are waiting they merge into one card ("3 notices
 * from your disciples") listing the first lines. A card slides in, stays 4.5 s (an important one 7 s, with a red seal
 * edge) and slides out. Hidden with the HUD (F1, the HUD switch), while a cinematic shot runs and while the debug screen
 * is open. Icons: {@code textures/sekte/mandiri/toast.png} (10 cells of 16 px, kind order of {@code Kebijakan
 * .Pemberitahuan}, then the summary). The chat line with its {@code [Lihat]} link comes from the server separately.
 */
public final class ToastMandiri implements HudElement {

	private static final Identifier ATLAS = Kultivator.id("textures/sekte/mandiri/toast.png");
	private static final long JEDA = 5000L;
	private static final long TAMPIL = 4500L;
	private static final long TAMPIL_PENTING = 7000L;
	private static final long GESER = 300L;
	private static final int LEBAR = 168;

	private record Kartu(int jenis, Component teks, boolean penting, List<Component> lain) {
	}

	private static final Deque<Kartu> ANTRE = new ArrayDeque<>();
	private static Kartu sekarang;
	private static long mulai;
	private static long terakhir;

	static void terima(MandiriPayloads.Toast toast) {
		if (ANTRE.size() >= 12) {
			ANTRE.pollFirst();
		}
		ANTRE.addLast(new Kartu(toast.jenis(), toast.teks(), toast.penting(), List.of()));
	}

	static void bersih() {
		ANTRE.clear();
		sekarang = null;
	}

	/** The next card: one waiting card as it is, or several merged into one. */
	private static Kartu ambil() {
		if (ANTRE.size() <= 2) {
			return ANTRE.pollFirst();
		}
		List<Component> lines = new ArrayList<>();
		boolean penting = false;
		int n = 0;
		while (!ANTRE.isEmpty()) {
			Kartu k = ANTRE.pollFirst();
			penting |= k.penting();
			if (lines.size() < 3) {
				lines.add(k.teks());
			}
			n++;
		}
		return new Kartu(MandiriPayloads.TOAST_RINGKAS, Component.translatable("kultivator.sekte.mandiri.cerita.toast.gabung_klien", n), penting, lines);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		long now = Util.getMillis();
		if (sekarang != null && now - mulai > (sekarang.penting() ? TAMPIL_PENTING : TAMPIL) + GESER) {
			sekarang = null;
		}
		if (sekarang == null && !ANTRE.isEmpty() && now - terakhir >= JEDA) {
			sekarang = ambil();
			mulai = now;
			terakhir = now;
		}
		if (sekarang == null || minecraft.player == null || minecraft.gui.hud.isHidden() || !EffectQuality.hudVisible()
				|| minecraft.getDebugOverlay().showDebugScreen() || CinematicDirector.active()) {
			return;
		}
		Font font = minecraft.font;
		Kartu k = sekarang;
		long age = now - mulai;
		long life = k.penting() ? TAMPIL_PENTING : TAMPIL;
		float slide = age < GESER ? age / (float) GESER : age > life ? 1.0F - (age - life) / (float) GESER : 1.0F;
		slide = Math.max(0.0F, Math.min(1.0F, slide));
		slide = slide * slide * (3.0F - 2.0F * slide);
		float textW = LEBAR - 30.0F;
		float h = 12.0F + Brush.wrapHeight(font, k.teks(), textW, 0.62F);
		for (Component line : k.lain()) {
			h += Math.min(14.0F, Brush.wrapHeight(font, line, textW - 4.0F, 0.52F)) + 1.0F;
		}
		h = Math.max(26.0F, h);
		int screenW = g.guiWidth();
		int x0 = Math.round(screenW - 6 - LEBAR * slide);
		int y0 = 6;
		int x1 = x0 + LEBAR;
		int y1 = Math.round(y0 + h);
		int alpha = Math.round(0xE8 * slide);
		g.fill(x0 + 2, y0 + 2, x1 + 2, y1 + 2, Palette.argb(Math.round(0x50 * slide), 0x000000));
		g.fillGradient(x0, y0, x1, y1, Palette.argb(alpha, Palette.PAPER_TOP), Palette.argb(alpha, Palette.PAPER_BOTTOM));
		Brush.frame(g, x0, y0, x1, y1, Palette.argb(alpha, Palette.GOLD_DARK));
		g.fill(x0, y0, x0 + 3, y1, Palette.argb(alpha, k.penting() ? Palette.SEAL : Palette.JADE));
		if (k.penting()) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(now / 220.0D);
			g.fill(x0, y0, x1, y0 + 1, Palette.argb(Math.round(alpha * (0.4F + 0.6F * pulse)), Palette.SEAL_GLOW));
		}
		int cell = Math.max(0, Math.min(9, k.jenis() == MandiriPayloads.TOAST_RINGKAS ? 9 : k.jenis()));
		Brush.disc(g, x0 + 15.0F, y0 + 13.0F, 10.0F, Palette.argb(alpha, Palette.darken(Palette.PAPER_DEEP, 0.2F)));
		g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, x0 + 7, y0 + 5, cell * 16.0F, 0.0F, 16, 16, 16, 16, 160, 16, Palette.argb(Math.round(255 * slide), 0xFFFFFF));
		float ty = y0 + 5.0F;
		ty += Brush.wrap(g, font, k.teks(), x0 + 28.0F, ty, textW, Palette.argb(Math.round(255 * slide), Palette.INK), 0.62F, false);
		for (Component line : k.lain()) {
			ty += 1.0F;
			ty += Brush.wrap(g, font, Component.literal("· ").append(line), x0 + 30.0F, ty, textW - 4.0F,
					Palette.argb(Math.round(255 * slide), Palette.INK_SOFT), 0.52F, false);
		}
	}
}
