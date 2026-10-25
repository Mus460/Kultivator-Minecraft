package dev.kultivator.client.hall;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;

/**
 * One scrolling column of stacked sections for the Ikhtisar subs (43.0.4: groups inside a sub are headings in one scroll).
 * Usage per frame: {@code int y = kolom.mulai(l, x0, y0, x1, y1);} draw from {@code y} down, then
 * {@code kolom.selesai(l, yAkhir)}; the page forwards the wheel to {@link #roda}. Zones drawn in between are clipped to the
 * column, so a scrolled-away button cannot be clicked.
 */
final class GulirKolom {

	private static final float LANGKAH = 18.0F;

	private final Eased tampil = new Eased(14.0F);
	private float gulir;
	private float isi;
	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private float offset;

	/** Starts the column in the box; returns the y of its first line (scrolled). */
	int mulai(Lukis l, int x0, int y0, int x1, int y1) {
		this.x0 = x0;
		this.y0 = y0;
		this.x1 = x1;
		this.y1 = y1;
		float maks = Math.max(0.0F, isi - (y1 - y0));
		gulir = Math.max(0.0F, Math.min(maks, gulir));
		offset = tampil.to(gulir, l.detik);
		l.klip(x0, y0, x1, y1);
		return Math.round(y0 - offset);
	}

	/** Ends the column at {@code yAkhir} (the y below its last line) and draws the scroll bead when it overflows. */
	void selesai(Lukis l, int yAkhir) {
		l.lepasKlip();
		isi = yAkhir + offset - y0 + 2.0F;
		float maks = isi - (y1 - y0);
		if (maks > 0.0F) {
			float bead = y0 + 3.0F + (y1 - y0 - 6.0F) * Math.min(1.0F, offset / maks);
			l.g.fill(x1 - 2, y0, x1 - 1, y1, Palette.CORD);
			Brush.disc(l.g, x1 - 1.5F, bead, 2.2F, Palette.JADE);
		}
	}

	/** The wheel over the column; true when it was over it. */
	boolean roda(double mx, double my, double dy) {
		if (mx >= x0 && mx < x1 && my >= y0 && my < y1 && isi > y1 - y0) {
			gulir -= (float) dy * LANGKAH;
			return true;
		}
		return false;
	}

	/** Pages up/down (PgUp/PgDn); {@code arah} -1 or 1. */
	void halaman(int arah) {
		gulir += arah * Math.max(LANGKAH, (y1 - y0) - LANGKAH);
	}

	/** Back to the top. */
	void atas() {
		gulir = 0.0F;
		tampil.set(0.0F);
	}

	/** PgUp / PgDn / Home / End; true when {@code key} was one of them. */
	boolean tombol(int key) {
		switch (key) {
			case InputConstants.KEY_PAGEDOWN -> halaman(1);
			case InputConstants.KEY_PAGEUP -> halaman(-1);
			case InputConstants.KEY_HOME -> gulir = 0.0F;
			case InputConstants.KEY_END -> gulir = Float.MAX_VALUE / 4.0F; // clamped to the end by the next mulai
			default -> {
				return false;
			}
		}
		return true;
	}

	/** The usable width of the column (room left for the bead). */
	int lebar() {
		return x1 - x0 - 4;
	}
}
