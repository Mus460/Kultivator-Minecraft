package dev.kultivator.client.sekte.mandiri;

import dev.kultivator.Kultivator;
import dev.kultivator.client.hud.Kabar;
import dev.kultivator.sekte.mandiri.cerita.MandiriPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
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
 * The disciple notices of DESIGN 39.7.5 (a disciple broke through, came home, was wounded or taken, bought something dear,
 * learned an art, changed its path, was tempted, or a feud moved; and the "while you were away" card at login), sent to
 * the single notice slot {@link Kabar} (DESIGN 43.9). At most one new notice every 5 seconds; when more are waiting they
 * merge into one card ("3 notices from your disciples") listing the first lines; an important one stays 7 s with a red
 * seal edge. Icons: {@code textures/sekte/mandiri/toast.png} (10 cells of 16 px, kind order of {@code Kebijakan
 * .Pemberitahuan}, then the summary). The chat line with its {@code [Lihat]} link comes from the server separately.
 * <p>
 * This HUD element ({@code mandiri_toast}) is also the host that draws {@link Kabar} every frame.
 */
public final class ToastMandiri implements HudElement {

	private static final Identifier ATLAS = Kultivator.id("textures/sekte/mandiri/toast.png");
	private static final long JEDA = 5000L;

	private record Kartu(int jenis, Component teks, boolean penting) {
	}

	private static final Deque<Kartu> ANTRE = new ArrayDeque<>();
	private static long terakhir;

	static void terima(MandiriPayloads.Toast toast) {
		if (ANTRE.size() >= 12) {
			ANTRE.pollFirst();
		}
		ANTRE.addLast(new Kartu(toast.jenis(), toast.teks(), toast.penting()));
	}

	static void bersih() {
		ANTRE.clear();
		Kabar.bersih();
	}

	/** Hands the next notice (one, or several merged) to {@link Kabar}, at most one every 5 s. */
	private static void pompa(long now) {
		if (ANTRE.isEmpty() || now - terakhir < JEDA) {
			return;
		}
		terakhir = now;
		if (ANTRE.size() <= 2) {
			Kartu k = ANTRE.pollFirst();
			Kabar.kirim(Kabar.kartu(null, k.teks()).ikon(ikon(k.jenis())).penting(k.penting()));
			return;
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
		Kabar.kirim(Kabar.kartu(Component.translatable("kultivator.sekte.mandiri.cerita.toast.gabung_klien", n), lines.getFirst())
				.baris(lines.subList(1, lines.size())).ikon(ikon(MandiriPayloads.TOAST_RINGKAS)).penting(penting));
	}

	private static Kabar.Ikon ikon(int jenis) {
		int cell = Math.max(0, Math.min(9, jenis == MandiriPayloads.TOAST_RINGKAS ? 9 : jenis));
		return (g, cx, cy) -> g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, Math.round(cx - 8.0F), Math.round(cy - 8.0F), cell * 16.0F, 0.0F,
				16, 16, 16, 16, 160, 16, 0xFFFFFFFF);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		pompa(Util.getMillis());
		Kabar.gambar(g);
	}
}
