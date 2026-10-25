package dev.kultivator.client.screen.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The theme's tooltip: a dark jade slip with a gold edge and a red seal in its corner. The first line is the title
 * (gold), the rest pale jade; lines keep their own colours when they have one.
 */
public final class SlipTooltip {

	private static final int MAX_WIDTH = 190;
	/** A queued tooltip older than this is from a frame that never drew its queue (an old screen): ignored. */
	private static final long BASI_MS = 150L;

	// One tooltip per frame (DESIGN 43.7): a strong one (a widget, a zone) wins over a weak one (text cut by Brush.fit).
	private static List<Component> kuat = List.of();
	private static long kuatSejak;
	private static List<Component> lemah = List.of();
	private static long lemahSejak;

	/** Asks for {@code lines} as this frame's tooltip (the last strong request of the frame wins). */
	public static void antre(List<Component> lines) {
		if (lines != null && !lines.isEmpty()) {
			kuat = lines;
			kuatSejak = net.minecraft.util.Util.getMillis();
		}
	}

	/** A weak request (the whole text of a cut label): shown only when nothing asked strongly this frame. */
	public static void antreLemah(List<Component> lines) {
		if (lines != null && !lines.isEmpty()) {
			lemah = lines;
			lemahSejak = net.minecraft.util.Util.getMillis();
		}
	}

	/** Start of a host frame: forgets last frame's requests. */
	public static void mulaiBingkai() {
		kuat = List.of();
		lemah = List.of();
	}

	/** True when a tooltip waits for this frame. */
	public static boolean ada() {
		long now = net.minecraft.util.Util.getMillis();
		return !kuat.isEmpty() && now - kuatSejak < BASI_MS || !lemah.isEmpty() && now - lemahSejak < BASI_MS;
	}

	/** Draws this frame's tooltip (strong over weak) and clears the queue. Call last in the host frame. */
	public static void gambarAntrean(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenWidth, int screenHeight) {
		long now = net.minecraft.util.Util.getMillis();
		List<Component> lines = !kuat.isEmpty() && now - kuatSejak < BASI_MS ? kuat : !lemah.isEmpty() && now - lemahSejak < BASI_MS ? lemah : List.of();
		mulaiBingkai();
		draw(g, font, lines, mouseX, mouseY, screenWidth, screenHeight);
	}

	public static void draw(GuiGraphicsExtractor g, Font font, List<Component> lines, int mouseX, int mouseY, int screenWidth, int screenHeight) {
		if (lines.isEmpty()) {
			return;
		}
		List<FormattedCharSequence> rows = new ArrayList<>();
		int titleRows = 0;
		for (int i = 0; i < lines.size(); i++) {
			List<FormattedCharSequence> split = font.split(lines.get(i), MAX_WIDTH);
			if (split.isEmpty()) {
				split = List.of(FormattedCharSequence.EMPTY);
			}
			rows.addAll(split);
			if (i == 0) {
				titleRows = split.size();
			}
		}
		int width = 0;
		for (FormattedCharSequence row : rows) {
			width = Math.max(width, font.width(row));
		}
		int height = rows.size() * 10 - 1 + (titleRows < rows.size() ? 2 : 0);
		int x = mouseX + 12;
		int y = mouseY - 8;
		if (x + width + 6 > screenWidth) {
			x = Math.max(6, mouseX - width - 14);
		}
		y = Math.max(6, Math.min(y, screenHeight - height - 6));

		g.fill(x - 4, y - 3, x + width + 6, y + height + 5, 0x60000000);
		g.fill(x - 5, y - 4, x + width + 5, y + height + 4, 0xF2112A23);
		Brush.frame(g, x - 5, y - 4, x + width + 5, y + height + 4, Palette.GOLD);
		Brush.frame(g, x - 4, y - 3, x + width + 4, y + height + 3, Palette.argb(0xA0, Palette.JADE_DARK));
		g.fill(x - 7, y - 6, x - 2, y - 1, Palette.SEAL);
		Brush.frame(g, x - 6, y - 5, x - 3, y - 2, Palette.argb(0xB0, Palette.TALISMAN));
		if (titleRows < rows.size()) {
			int lineY = y + titleRows * 10 - 1;
			g.fill(x, lineY, x + width, lineY + 1, Palette.argb(0x70, Palette.GOLD));
		}
		for (int i = 0; i < rows.size(); i++) {
			int extra = i >= titleRows ? 2 : 0;
			g.text(font, rows.get(i), x, y + i * 10 + extra, i < titleRows ? Palette.GOLD_LIGHT : Palette.JADE_PALE, false);
		}
	}

	private SlipTooltip() {
	}
}
