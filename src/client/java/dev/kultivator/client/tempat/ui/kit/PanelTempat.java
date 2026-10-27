package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * {@code PanelTempat} (DESIGN 38.U.2): the small kit-38 furniture still drawn by the sheets of the old engine: inset
 * wells, cards, rules and the focus ring (the nine-slice frame went with the Gulungan's own frame, 43.6). Pure fills.
 */
public final class PanelTempat {

	/** An inset well (detail pane, input): light fill, dark top-left rim, light bottom-right rim. */
	public static void sumur(GuiGraphicsExtractor g, TempatWarna.Gaya gaya, int x0, int y0, int x1, int y1) {
		g.fill(x0, y0, x1, y1, gaya.sumur);
		g.fill(x0, y0, x1, y0 + 1, 0x50000000);
		g.fill(x0, y0, x0 + 1, y1, 0x50000000);
		g.fill(x0, y1 - 1, x1, y1, 0x30FFFFFF);
		g.fill(x1 - 1, y0, x1, y1, 0x30FFFFFF);
	}

	/** A card behind a list row or a block of numbers; {@code sorot} 0..1 hover glow. */
	public static void kartu(GuiGraphicsExtractor g, TempatWarna.Gaya gaya, int x0, int y0, int x1, int y1, float sorot, boolean pilih) {
		int fill = Palette.mix(gaya.kartu, gaya.kartuSorot, Math.max(sorot * 0.8F, pilih ? 1.0F : 0.0F));
		g.fill(x0, y0, x1, y1, fill);
		if (pilih) {
			g.fill(x0, y0, x0 + 2, y1, gaya.aksen);
			Brush.frame(g, x0, y0, x1, y1, Palette.argb(0xA0, gaya.aksen & 0xFFFFFF));
		} else if (sorot > 0.05F) {
			Brush.frame(g, x0, y0, x1, y1, Palette.argb(Math.round(0x70 * sorot), TempatWarna.EMAS & 0xFFFFFF));
		}
	}

	/** A thin rule with a small diamond in the middle (section separator). */
	public static void garis(GuiGraphicsExtractor g, TempatWarna.Gaya gaya, int x0, int x1, int y) {
		int col = Palette.argb(0x90, (gaya.gelap() ? TempatWarna.EMAS : gaya.teksSamar) & 0xFFFFFF);
		g.fill(x0, y, x1, y + 1, col);
		int mid = (x0 + x1) / 2;
		g.fill(mid - 1, y - 1, mid + 2, y + 2, col);
	}

	/** The focus ring of 38.U.2: a 1 px gold ring just outside the box. */
	public static void cincinFokus(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float denyut) {
		int col = Palette.argb(Math.round(170 + 85 * denyut), TempatWarna.EMAS_TERANG & 0xFFFFFF);
		Brush.frame(g, x0 - 2, y0 - 2, x1 + 2, y1 + 2, col);
	}

	private PanelTempat() {
	}
}
