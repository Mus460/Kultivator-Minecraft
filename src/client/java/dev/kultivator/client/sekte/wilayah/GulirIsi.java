package dev.kultivator.client.sekte.wilayah;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Mth;

/**
 * The vertical scroll of a stacked page of Sekte › Wilayah (DESIGN 43.0.4: inside a sub the grouping is headings stacked in
 * one scroll). The page draws its content between {@link #mulai} and {@link #selesai} at the y {@link #mulai} returns; the
 * clip of {@link Lukis} keeps scrolled-out zones from being hovered or clicked. Mouse wheel over the box, PgUp/PgDn/Home/End
 * and ↑↓ (when the page has no list of its own) move it; a thin jade bar shows the place.
 */
public final class GulirIsi {

	private static final float LANGKAH = 14.0F;

	private float gulir;
	private float tampil;
	private int isi;
	private int x0;
	private int y0;
	private int x1;
	private int y1;

	/** Begins the scrolled box (the caller keeps 5 px at the right for the bar); returns the y of the content's top. */
	public int mulai(Lukis l, int x0, int y0, int x1, int y1) {
		this.x0 = x0;
		this.y0 = y0;
		this.x1 = x1;
		this.y1 = y1;
		gulir = Mth.clamp(gulir, 0.0F, maks());
		tampil += (gulir - tampil) * Math.min(1.0F, l.detik * 18.0F);
		if (Math.abs(gulir - tampil) < 0.3F) {
			tampil = gulir;
		}
		l.klip(x0, y0, x1, y1);
		return Math.round(y0 - tampil);
	}

	/** Ends the box; {@code yAkhir} is the y below the last content drawn (from the y {@link #mulai} returned). */
	public void selesai(Lukis l, int yAkhir) {
		isi = Math.max(0, yAkhir + Math.round(tampil) - y0);
		l.lepasKlip();
		float m = maks();
		if (m > 0.0F) {
			int tinggi = y1 - y0;
			float batang = Math.max(10.0F, tinggi * (tinggi / (float) Math.max(1, isi)));
			float by = y0 + (tinggi - batang) * Mth.clamp(tampil / m, 0.0F, 1.0F);
			Brush.rect(l.g, x1 + 2, y0, x1 + 3, y1, Palette.argb(0x50, Palette.CORD));
			Brush.rect(l.g, x1 + 1, by, x1 + 4, by + batang, Palette.argb(0xC0, Palette.JADE_DARK));
		}
	}

	private float maks() {
		return Math.max(0, isi - (y1 - y0));
	}

	/** The wheel over the box. */
	public boolean roda(double mx, double my, double dy) {
		if (mx < x0 || mx >= x1 + 5 || my < y0 || my >= y1 || maks() <= 0.0F) {
			return false;
		}
		gulir = Mth.clamp(gulir - (float) dy * LANGKAH, 0.0F, maks());
		return true;
	}

	/** PgUp/PgDn/Home/End/↑/↓ (after the page's focus ring declined the key). */
	public boolean tombol(KeyEvent event) {
		float halaman = Math.max(LANGKAH, (y1 - y0) - LANGKAH);
		float ke = switch (event.key()) {
			case InputConstants.KEY_PAGEUP -> gulir - halaman;
			case InputConstants.KEY_PAGEDOWN -> gulir + halaman;
			case InputConstants.KEY_HOME -> 0.0F;
			case InputConstants.KEY_END -> maks();
			case InputConstants.KEY_UP -> gulir - LANGKAH;
			case InputConstants.KEY_DOWN -> gulir + LANGKAH;
			default -> Float.NaN;
		};
		if (Float.isNaN(ke) || maks() <= 0.0F) {
			return false;
		}
		gulir = Mth.clamp(ke, 0.0F, maks());
		return true;
	}

	/** Scrolls so content offset {@code dy} (from the content top) is at the top of the box. */
	public void ke(int dy) {
		gulir = Math.max(0, dy);
	}

	/** Back to the top at once (new content). */
	public void atas() {
		gulir = 0.0F;
		tampil = 0.0F;
	}
}
