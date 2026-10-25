package dev.kultivator.client.sekte.gabung.ui;

import dev.kultivator.Kultivator;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.sekte.api.Lambang;
import dev.kultivator.sekte.gabung.api.Corak;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The textures of the 41 screens ({@code tools/gen_gabung_ui.py}, {@code textures/gui/gabung/}): the 16 px icon atlas
 * (slot constants below, same order as the generator), the rank-tree jade cap, the member card's nine-slice slab, the
 * test HUD pieces and the verdict stamps; plus the sect emblem drawn from its packed {@link Lambang} (shape, symbol glyph,
 * three colours) the way the Diplomasi screen draws it. Client only, read by 41U-1 and 41U-2.
 */
public final class IkonGabung {

	public static final Identifier IKON = Kultivator.id("textures/gui/gabung/ikon.png");
	public static final Identifier CAP = Kultivator.id("textures/gui/gabung/cap.png");
	public static final Identifier KARTU = Kultivator.id("textures/gui/gabung/kartu.png");
	public static final Identifier HUD = Kultivator.id("textures/gui/gabung/hud.png");
	public static final Identifier STEMPEL = Kultivator.id("textures/gui/gabung/stempel.png");

	// row 0
	/** {@code CORAK + Corak.ordinal()} (0-9). */
	public static final int CORAK = 0;
	/** {@code KELAS + nomor - 1} (10-14). */
	public static final int KELAS = 10;
	public static final int LANGIT = 15;
	// row 1
	/** {@code PINTU + Pintu.ordinal()} (16-20). */
	public static final int PINTU = 16;
	/** {@code ALIRAN + aliran} (21-23). */
	public static final int ALIRAN = 21;
	public static final int MEROSOT = 24;
	public static final int KABAR = 25;
	public static final int SHIFU = 26;
	public static final int HUKUM = 27;
	public static final int MURID = 28;
	public static final int KELUAR = 29;
	public static final int JATAH = 30;
	public static final int KURSI = 31;
	// row 2: test HUD
	public static final int TEKANAN = 32;
	public static final int ILUSI = 33;
	public static final int MEDITASI = 34;
	public static final int BONEKA = 35;
	public static final int TEBASAN = 36;
	public static final int PERUNGGU = 37;
	public static final int CAKAR = 38;
	public static final int MATA_FORMASI = 39;
	public static final int PILAR = 40;
	public static final int TUNGKU = 41;
	public static final int BATU_PIKUL = 42;
	public static final int KOLAM = 43;
	public static final int CAWAN = 44;
	public static final int PENJAGA = 45;
	public static final int HATI = 46;
	public static final int SUMPAH = 47;
	// row 3
	/** {@code PANGKAT + Pangkat.ordinal()} (48-57). */
	public static final int PANGKAT = 48;
	public static final int PAPAN_JASA = 58;
	public static final int PERINGKAT = 59;
	public static final int TANYA = 60;
	public static final int LENCANA = 61;
	public static final int ARAH = 62;
	public static final int KETUA = 63;

	/** The test icon of each corak's style test, in {@link Corak} order. */
	private static final int[] UJIAN_CORAK = {TEBASAN, PERUNGGU, CAKAR, MATA_FORMASI, PILAR, TUNGKU, BATU_PIKUL, KOLAM, CAWAN, PENJAGA};
	/** The 32 emblem symbols as seal glyphs (order of {@code Lambang} symbols, as the Diplomasi screen). */
	private static final String SIMBOL = "剑莲鹤龙凤虎龟山月日云雷火水木金土风星海玉竹梅松霜雪花鼎钟塔天道";

	/** Draws icon {@code slot} at {@code size} px with an ARGB tint (slot &lt; 0 draws nothing). */
	public static void gambar(GuiGraphicsExtractor g, int slot, int x, int y, int size, int warna) {
		if (slot < 0 || slot >= 64) {
			return;
		}
		g.blit(RenderPipelines.GUI_TEXTURED, IKON, x, y, (slot % 16) * 16.0F, (slot / 16) * 16.0F, size, size, 16, 16, 256, 64, warna);
	}

	public static void gambar(GuiGraphicsExtractor g, int slot, int x, int y, int size) {
		gambar(g, slot, x, y, size, 0xFFFFFFFF);
	}

	/** The icon slot of corak id {@code id}. */
	public static int corak(String id) {
		return CORAK + Corak.byId(id, Corak.PEDANG).ordinal();
	}

	/** The style-test icon of corak id {@code id}. */
	public static int ujianCorak(String id) {
		return UJIAN_CORAK[Corak.byId(id, Corak.PEDANG).ordinal()];
	}

	/** Class plaque of class number 1-5. */
	public static int kelas(int nomor) {
		return KELAS + Math.max(0, Math.min(4, nomor - 1));
	}

	/** Rank seal of Pangkat ordinal {@code ordinal}. */
	public static int pangkat(int ordinal) {
		return PANGKAT + Math.max(0, Math.min(9, ordinal));
	}

	/**
	 * A rank-tree node: the jade cap tinted {@code rgb}, the gold rim, the player's own rank haloed (pulsing with
	 * {@code denyut} 0..1), a locked node drawn at 45 % ink.
	 */
	public static void cap(GuiGraphicsExtractor g, int x, int y, int size, int rgb, boolean kini, boolean terkunci, float denyut) {
		int alpha = terkunci ? 0x73 : 0xFF;
		int tint = Palette.argb(alpha, terkunci ? Palette.grey(Palette.opaque(rgb), 0.55F) & 0xFFFFFF : rgb & 0xFFFFFF);
		if (kini) {
			int halo = Palette.argb(Math.round(140 + 115 * denyut), 0xFFFFFF);
			int pad = Math.round(size * 0.22F);
			g.blit(RenderPipelines.GUI_TEXTURED, CAP, x - pad, y - pad, 64.0F, 0.0F, size + pad * 2, size + pad * 2, 32, 32, 96, 32, halo);
		}
		g.blit(RenderPipelines.GUI_TEXTURED, CAP, x, y, 0.0F, 0.0F, size, size, 32, 32, 96, 32, tint);
		g.blit(RenderPipelines.GUI_TEXTURED, CAP, x, y, 32.0F, 0.0F, size, size, 32, 32, 96, 32, Palette.argb(alpha, 0xFFFFFF));
	}

	/** The member card slab as a nine-slice (corner 12 px, edges and centre stretched). */
	public static void kartu(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		int c = 12;
		int w = x1 - x0;
		int h = y1 - y0;
		if (w < c * 2 || h < c * 2) {
			g.blit(RenderPipelines.GUI_TEXTURED, KARTU, x0, y0, 0.0F, 0.0F, w, h, 64, 64, 64, 64, -1);
			return;
		}
		int mw = w - c * 2;
		int mh = h - c * 2;
		// corners
		g.blit(RenderPipelines.GUI_TEXTURED, KARTU, x0, y0, 0.0F, 0.0F, c, c, c, c, 64, 64, -1);
		g.blit(RenderPipelines.GUI_TEXTURED, KARTU, x1 - c, y0, 64.0F - c, 0.0F, c, c, c, c, 64, 64, -1);
		g.blit(RenderPipelines.GUI_TEXTURED, KARTU, x0, y1 - c, 0.0F, 64.0F - c, c, c, c, c, 64, 64, -1);
		g.blit(RenderPipelines.GUI_TEXTURED, KARTU, x1 - c, y1 - c, 64.0F - c, 64.0F - c, c, c, c, c, 64, 64, -1);
		// edges
		g.blit(RenderPipelines.GUI_TEXTURED, KARTU, x0 + c, y0, c, 0.0F, mw, c, 64 - c * 2, c, 64, 64, -1);
		g.blit(RenderPipelines.GUI_TEXTURED, KARTU, x0 + c, y1 - c, c, 64.0F - c, mw, c, 64 - c * 2, c, 64, 64, -1);
		g.blit(RenderPipelines.GUI_TEXTURED, KARTU, x0, y0 + c, 0.0F, c, c, mh, c, 64 - c * 2, 64, 64, -1);
		g.blit(RenderPipelines.GUI_TEXTURED, KARTU, x1 - c, y0 + c, 64.0F - c, c, c, mh, c, 64 - c * 2, 64, 64, -1);
		// centre
		g.blit(RenderPipelines.GUI_TEXTURED, KARTU, x0 + c, y0 + c, c, c, mw, mh, 64 - c * 2, 64 - c * 2, 64, 64, -1);
	}

	/** A verdict stamp 128x24 scaled to {@code w} x {@code h}: red (accepted) or grey (refused). */
	public static void stempel(GuiGraphicsExtractor g, boolean diterima, int x, int y, int w, int h, int warna) {
		g.blit(RenderPipelines.GUI_TEXTURED, STEMPEL, x, y, 0.0F, diterima ? 0.0F : 24.0F, w, h, 128, 24, 128, 48, warna);
	}

	/** A piece of the HUD texture at 1:1 (pieces documented in {@code gen_gabung_ui.py}). */
	public static void hud(GuiGraphicsExtractor g, int x, int y, int u, int v, int w, int h, int warna) {
		g.blit(RenderPipelines.GUI_TEXTURED, HUD, x, y, u, v, w, h, w, h, 128, 64, warna);
	}

	/** The HUD piece stretched to {@code w} x {@code h}. */
	public static void hud(GuiGraphicsExtractor g, int x, int y, int w, int h, int u, int v, int uw, int vh, int warna) {
		g.blit(RenderPipelines.GUI_TEXTURED, HUD, x, y, u, v, w, h, uw, vh, 128, 64, warna);
	}

	/** The sect emblem of packed {@code Lambang} {@code packed} centred at (cx, cy), radius {@code r}. */
	public static void lambang(GuiGraphicsExtractor g, Font font, int packed, float cx, float cy, float r) {
		Lambang l = Lambang.unpack(packed);
		bentuk(g, l.shape(), cx, cy, r, Palette.opaque(l.borderRgb()));
		bentuk(g, l.shape(), cx, cy, r - Math.max(1.2F, r * 0.13F), Palette.opaque(l.fieldRgb()));
		Component c = Component.literal(String.valueOf(SIMBOL.charAt(Math.floorMod(l.symbol(), SIMBOL.length()))));
		float s = r / 6.0F;
		Brush.text(g, font, c, cx - font.width(c) * s / 2.0F, cy - 4.0F * s, Palette.opaque(l.chargeRgb()), s, false);
	}

	/** One of the eight emblem shapes, filled in half-pixel rows (same outlines as the Diplomasi screen). */
	private static void bentuk(GuiGraphicsExtractor g, int shape, float cx, float cy, float r, int color) {
		if (shape == 4) {
			Brush.disc(g, cx, cy + r * 0.1F, r * 0.75F, color);
			Brush.disc(g, cx - r * 0.55F, cy - r * 0.25F, r * 0.42F, color);
			Brush.disc(g, cx + r * 0.55F, cy - r * 0.25F, r * 0.42F, color);
			Brush.disc(g, cx, cy - r * 0.55F, r * 0.42F, color);
			return;
		}
		if (shape == 0) {
			Brush.disc(g, cx, cy, r, color);
			return;
		}
		for (float dy = -r; dy < r; dy += 0.5F) {
			float t = (dy + r) / (2.0F * r);
			float ad = Math.abs(dy + 0.25F);
			float hw = switch (shape) {
				case 1 -> r * 0.9F;
				case 2 -> Math.min(r * 0.92F, r * 1.35F - ad);
				case 3 -> r - ad;
				case 5 -> t < 0.62F ? r * 0.82F * (float) Math.sqrt(t / 0.62F)
						: r * 0.82F * (float) Math.sqrt(Math.max(0.0F, 1.0F - ((t - 0.62F) / 0.38F) * ((t - 0.62F) / 0.38F)));
				case 6 -> dy < 0.0F ? r * 0.85F : r * 0.85F * (1.0F - (dy / r) * (dy / r));
				case 7 -> t < 0.72F ? r * 0.7F : r * 0.7F * (1.0F - (t - 0.72F) / 0.28F);
				default -> r * 0.9F;
			};
			if (hw <= 0.05F) {
				continue;
			}
			Brush.rect(g, cx - hw, cy + dy, cx + hw, cy + dy + 0.5F, color);
		}
	}

	private IkonGabung() {
	}
}
