package dev.kultivator.client.sekte.mandiri;

import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.sekte.mandiri.api.Dorong;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * The icons and colours of module 39E's screens: the ten life paths ({@code jalan.png}, 8 x 2 cells of 32 px), the
 * activities ({@code kegiatan.png}, 8 x 4 cells of 16 px, one per {@link Kegiatan} in enum order), the seven moods of a
 * diary entry ({@code suasana.png}, 8 x 1 cells of 16 px), the seven drives ({@code dorongan.png}) and the ornaments
 * ({@code hiasan.png}: the red ribbon of an important entry, the path seal, a gold corner cloud). All drawn with the
 * plain {@code GUI_TEXTURED} pipeline, any size (the cell is scaled). Client only.
 */
public final class Ikon {

	/** Ornament cells of {@code hiasan.png} (128 x 64): {u, v, w, h}. */
	public static final int[] PITA = {0, 0, 12, 44};
	public static final int[] SEGEL = {16, 0, 32, 32};
	public static final int[] SUDUT = {48, 0, 16, 16};
	public static final int[] GULUNGAN = {64, 0, 32, 32};
	public static final int[] KANTONG = {96, 0, 32, 32};
	public static final int[] TINTA = {48, 16, 16, 16};
	public static final int[] BUKU = {0, 48, 16, 16};

	public static void jalan(GuiGraphicsExtractor g, JalanHidup jalan, int x, int y, int size, int alpha) {
		if (jalan == null) {
			return;
		}
		int i = jalan.ordinal();
		g.blit(RenderPipelines.GUI_TEXTURED, MandiriClient.ATLAS_JALAN, x, y, (i % 8) * 32.0F, (i / 8) * 32.0F, size, size, 32, 32, 256, 64,
				Palette.argb(alpha, 0xFFFFFF));
	}

	public static void kegiatan(GuiGraphicsExtractor g, Kegiatan k, int x, int y, int size, int alpha) {
		int i = k == null ? 0 : k.ordinal();
		if (i >= 32) {
			return;
		}
		g.blit(RenderPipelines.GUI_TEXTURED, MandiriClient.ATLAS_KEGIATAN, x, y, (i % 8) * 16.0F, (i / 8) * 16.0F, size, size, 16, 16, 128, 64,
				Palette.argb(alpha, 0xFFFFFF));
	}

	public static void suasana(GuiGraphicsExtractor g, EntriHarian.Suasana s, int x, int y, int size, int alpha) {
		int i = s == null ? 1 : s.ordinal();
		g.blit(RenderPipelines.GUI_TEXTURED, MandiriClient.ATLAS_SUASANA, x, y, i * 16.0F, 0.0F, size, size, 16, 16, 128, 16,
				Palette.argb(alpha, 0xFFFFFF));
	}

	public static void dorong(GuiGraphicsExtractor g, Dorong d, int x, int y, int size, int alpha) {
		int i = d == null ? 0 : d.ordinal();
		g.blit(RenderPipelines.GUI_TEXTURED, MandiriClient.ATLAS_DORONG, x, y, i * 16.0F, 0.0F, size, size, 16, 16, 128, 16,
				Palette.argb(alpha, 0xFFFFFF));
	}

	/** An ornament cell drawn at {@code w x h}. */
	public static void hiasan(GuiGraphicsExtractor g, int[] cell, int x, int y, int w, int h, int argb) {
		g.blit(RenderPipelines.GUI_TEXTURED, MandiriClient.HIASAN, x, y, cell[0], cell[1], w, h, cell[2], cell[3], 128, 64, argb);
	}

	/** The aged diary paper, tiled over a box (64 px tiles). */
	public static void kertas(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int argb) {
		for (int ty = y0; ty < y1; ty += 64) {
			for (int tx = x0; tx < x1; tx += 64) {
				int w = Math.min(64, x1 - tx);
				int h = Math.min(64, y1 - ty);
				g.blit(RenderPipelines.GUI_TEXTURED, MandiriClient.KERTAS, tx, ty, 0.0F, 0.0F, w, h, w, h, 64, 64, argb);
			}
		}
	}

	/** The colour of an activity on the hour strip and the board (0xRRGGBB). */
	public static int warna(Kegiatan k) {
		if (k == null) {
			return 0x5A6660;
		}
		return switch (k) {
			case KOSONG -> 0x5A6660;
			case MEDITASI -> 0x4FA3E0;
			case TUTUP_DIRI -> 0x3B5FBF;
			case LATIH_JURUS -> 0xC8503C;
			case TANDING -> 0xE07A30;
			case BELAJAR -> 0x9C7BD6;
			case MENGAJAR -> 0xB79BE8;
			case MERAMU -> 0xE0893A;
			case JIMAT -> 0xE6C34A;
			case KEBUN -> 0x6DBE5A;
			case TAMBANG -> 0x8C7A66;
			case POS_TUGAS -> 0x7FA08C;
			case JAGA_FORMASI -> 0x6FB8D8;
			case JAGA_GERBANG -> 0x8C9BAD;
			case SOSIAL -> 0xF29BC1;
			case MAKAN -> 0xE8A86A;
			case ISTIRAHAT -> 0x7A8899;
			case MERENUNG -> 0x9FD7A8;
			case SERAP_BATU -> 0x47C8B8;
			case JAGA_TEROBOSAN -> 0xD9A030;
			case TEROBOSAN -> 0xFFD84A;
			case BERKEMAS -> 0xC9A46A;
			case BERANGKAT -> 0xE8C65A;
			case TIBA -> 0xD4B060;
			case PERJALANAN -> 0xDDB548;
			case BERMAIN -> 0xF5B4D0;
			case CEKCOK -> 0xB03030;
			case TANTANG -> 0xD04A2A;
			case TERLUKA -> 0x903040;
			case SUMPAH -> 0xC03070;
			default -> 0x5A6660;
		};
	}

	/** The colour of a diary mood. */
	public static int warna(EntriHarian.Suasana s) {
		if (s == null) {
			return 0x7A8C83;
		}
		return switch (s) {
			case SENANG -> 0xE8A23A;
			case TENANG -> 0x3E8F7A;
			case LELAH -> 0x7A8899;
			case KESAL -> 0xC0502E;
			case SEDIH -> 0x4F6FB0;
			case BANGGA -> 0xC9961E;
			case TERGODA -> 0x8B3FC4;
		};
	}

	private Ikon() {
	}
}
