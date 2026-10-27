package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The kit's textures (DESIGN 38.U.2, drawn by {@code tools/gen_tempat_ui.py}): the icon atlas {@code ikon.png} (slots of
 * {@link LembarKunci.Ikon}, plus 38UI's own slots from {@link LembarKunci.Ikon#BEBAS_PERTAMA} listed here), the lantern
 * tablets, the portrait frame and the stamps.
 */
public final class IkonTempat {

	public static final Identifier IKON = Kultivator.id("textures/gui/tempat/ikon.png");
	public static final Identifier LENTERA = Kultivator.id("textures/gui/tempat/lentera.png");
	public static final Identifier POTRET = Kultivator.id("textures/gui/tempat/potret.png");
	public static final Identifier STEMPEL = Kultivator.id("textures/gui/tempat/stempel.png");

	// 38UI additions (append only, generator list EXTRA)
	public static final int CENTANG = 112;
	public static final int SILANG = 113;
	public static final int SURAT = 114;
	public static final int KOMPAS = 115;
	public static final int PIALA = 116;
	public static final int TANYA = 117;
	public static final int BINTANG_KOSONG = 118;
	public static final int BINTANG_PENUH = 119;
	public static final int DUPA = 120;
	public static final int BUKU = 121;
	public static final int GULUNGAN = 122;
	public static final int KUNCI = 123;
	public static final int PANAH_KANAN = 124;
	public static final int PANAH_KIRI = 125;
	public static final int TONGGAK = 126;
	public static final int PETA = 127;
	public static final int HATI = 128;
	public static final int TUTUP = 129;
	public static final int RANTAI = 130;
	public static final int OBOR = 131;

	/** Draws icon {@code slot} at its native 16 px. */
	public static void gambar(GuiGraphicsExtractor g, int slot, int x, int y) {
		gambar(g, slot, x, y, 16, 0xFFFFFFFF);
	}

	/** Draws icon {@code slot} scaled to {@code size} px (nearest) with an ARGB tint; slot -1 draws nothing. */
	public static void gambar(GuiGraphicsExtractor g, int slot, int x, int y, int size, int warna) {
		if (slot < 0 || slot > 255) {
			return;
		}
		float u = (slot % 16) * 16.0F;
		float v = (slot / 16) * 16.0F;
		g.blit(RenderPipelines.GUI_TEXTURED, IKON, x, y, u, v, size, size, 16, 16, 256, 256, warna);
	}

	/** The soft lantern glow (32 x 32) stretched over the given box. */
	public static void cahaya(GuiGraphicsExtractor g, int x, int y, int w, int h, int warna) {
		g.blit(RenderPipelines.GUI_TEXTURED, LENTERA, x, y, 96.0F, 0.0F, w, h, 32, 32, 128, 32, warna);
	}

	/** Portrait frame 56 x 56 scaled to {@code size}. */
	public static void bingkaiPotret(GuiGraphicsExtractor g, int x, int y, int size) {
		g.blit(RenderPipelines.GUI_TEXTURED, POTRET, x, y, 0.0F, 0.0F, size, size, 56, 56, 64, 64, 0xFFFFFFFF);
	}

	/** Stamps of {@code stempel.png}. */
	public enum Stempel {
		BULAT(0, 0, 48, 48),
		PERSEGI(48, 0, 48, 48),
		GEMBOK(96, 0, 32, 32),
		BINTANG(96, 32, 32, 32),
		PITA_ABU(0, 48, 48, 16),
		PITA_EMAS(48, 48, 48, 16);

		final int u;
		final int v;
		final int w;
		final int h;

		Stempel(int u, int v, int w, int h) {
			this.u = u;
			this.v = v;
			this.w = w;
			this.h = h;
		}
	}

	public static void stempel(GuiGraphicsExtractor g, Stempel s, int x, int y, int w, int h, int warna) {
		g.blit(RenderPipelines.GUI_TEXTURED, STEMPEL, x, y, s.u, s.v, w, h, s.w, s.h, 128, 64, warna);
	}

	/** A ribbon stretched to width {@code w} keeping its 6 px ends (3-slice). */
	public static void pita(GuiGraphicsExtractor g, boolean emas, int x, int y, int w, int h, int warna) {
		Stempel s = emas ? Stempel.PITA_EMAS : Stempel.PITA_ABU;
		int end = Math.min(6, w / 2);
		g.blit(RenderPipelines.GUI_TEXTURED, STEMPEL, x, y, s.u, s.v, end, h, end, s.h, 128, 64, warna);
		if (w > end * 2) {
			g.blit(RenderPipelines.GUI_TEXTURED, STEMPEL, x + end, y, s.u + end, s.v, w - end * 2, h, s.w - 12, s.h, 128, 64, warna);
		}
		g.blit(RenderPipelines.GUI_TEXTURED, STEMPEL, x + w - end, y, s.u + s.w - end, s.v, end, h, end, s.h, 128, 64, warna);
	}

	private IkonTempat() {
	}
}
