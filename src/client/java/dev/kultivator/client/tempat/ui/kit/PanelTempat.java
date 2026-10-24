package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.Kultivator;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * {@code PanelTempat} (DESIGN 38.U.2): the styled nine-slice frame of every 38 screen ({@code panel_<gaya>.png} 32 x 32,
 * corner 8 px, edges and centre tiled every 16 px so the grain never stretches), the faint cloud-scroll lines over the
 * paper ({@code awan.png}), a soft drop shadow, and the smaller furniture drawn on it: inset wells, cards, rules and
 * the corner knot. Pure blits and fills (no shader), cheap enough for weak laptops.
 */
public final class PanelTempat {

	private static final int SUDUT = 8;
	private static final int UBIN = 16;
	private static final Map<TempatWarna.Gaya, Identifier> TEKSTUR = new EnumMap<>(TempatWarna.Gaya.class);

	private static Identifier tekstur(TempatWarna.Gaya gaya) {
		return TEKSTUR.computeIfAbsent(gaya, g -> Kultivator.id("textures/gui/tempat/panel_" + g.id + ".png"));
	}

	/** The full panel: shadow, nine-slice frame, cloud lines tinted for the style. */
	public static void gambar(GuiGraphicsExtractor g, TempatWarna.Gaya gaya, int x0, int y0, int x1, int y1) {
		// shadow: two offset translucent fills, like a lacquer box on a table
		g.fill(x0 + 3, y0 + 4, x1 + 3, y1 + 4, 0x50000000);
		g.fill(x0 + 1, y0 + 2, x1 + 1, y1 + 2, 0x40000000);
		bingkai(g, tekstur(gaya), x0, y0, x1, y1);
		int awan = switch (gaya) {
			case KERTAS -> 0x26B08A3A;
			case KAYU -> 0x18F7DC7E;
			case BATU -> 0x1EFFFFFF;
			case KAIN -> 0x22D4A637;
			case GIOK -> 0x2A2E8963;
		};
		awan(g, x0 + SUDUT, y0 + SUDUT, x1 - SUDUT, y1 - SUDUT, awan);
	}

	/** A nine-slice of a 32 x 32 texture with corner 8 and tiled 16 px edges and centre. */
	public static void bingkai(GuiGraphicsExtractor g, Identifier tex, int x0, int y0, int x1, int y1) {
		int w = x1 - x0;
		int h = y1 - y0;
		if (w < SUDUT * 2 || h < SUDUT * 2) {
			g.fill(x0, y0, x1, y1, 0xFF808080);
			return;
		}
		// corners
		blit(g, tex, x0, y0, 0, 0, SUDUT, SUDUT);
		blit(g, tex, x1 - SUDUT, y0, 32 - SUDUT, 0, SUDUT, SUDUT);
		blit(g, tex, x0, y1 - SUDUT, 0, 32 - SUDUT, SUDUT, SUDUT);
		blit(g, tex, x1 - SUDUT, y1 - SUDUT, 32 - SUDUT, 32 - SUDUT, SUDUT, SUDUT);
		// edges
		for (int x = x0 + SUDUT; x < x1 - SUDUT; x += UBIN) {
			int run = Math.min(UBIN, x1 - SUDUT - x);
			blit(g, tex, x, y0, SUDUT, 0, run, SUDUT);
			blit(g, tex, x, y1 - SUDUT, SUDUT, 32 - SUDUT, run, SUDUT);
		}
		for (int y = y0 + SUDUT; y < y1 - SUDUT; y += UBIN) {
			int run = Math.min(UBIN, y1 - SUDUT - y);
			blit(g, tex, x0, y, 0, SUDUT, SUDUT, run);
			blit(g, tex, x1 - SUDUT, y, 32 - SUDUT, SUDUT, SUDUT, run);
		}
		// centre
		for (int y = y0 + SUDUT; y < y1 - SUDUT; y += UBIN) {
			int rh = Math.min(UBIN, y1 - SUDUT - y);
			for (int x = x0 + SUDUT; x < x1 - SUDUT; x += UBIN) {
				int rw = Math.min(UBIN, x1 - SUDUT - x);
				blit(g, tex, x, y, SUDUT, SUDUT, rw, rh);
			}
		}
	}

	private static void blit(GuiGraphicsExtractor g, Identifier tex, int x, int y, int u, int v, int w, int h) {
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, w, h, 32, 32);
	}

	/** The 64 px cloud-scroll tile over a box, tinted ARGB. */
	public static void awan(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int warna) {
		for (int y = y0; y < y1; y += 64) {
			int rh = Math.min(64, y1 - y);
			for (int x = x0; x < x1; x += 64) {
				int rw = Math.min(64, x1 - x);
				g.blit(RenderPipelines.GUI_TEXTURED, IkonTempat.AWAN, x, y, (x - x0) % 64, (y - y0) % 64, rw, rh, rw, rh, 64, 64, warna);
			}
		}
	}

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
