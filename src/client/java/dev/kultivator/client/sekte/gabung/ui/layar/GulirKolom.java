package dev.kultivator.client.sekte.gabung.ui.layar;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import net.minecraft.client.input.KeyEvent;

/**
 * One scrolled column of a 41 sheet whose content is taller than its box (a stacked card, a short page at GUI scale 4):
 * the wheel, the arrow / page / Home / End keys (keyboard only, D-41U) and a cord with a jade bead. The sheet draws its
 * content from {@link #mulai}'s y and calls {@link #selesai} with the y where the content ended; the base class routes the
 * wheel and the keys to the column the sheet marked active this frame.
 */
final class GulirKolom {

	private final Eased tampil = new Eased(14.0F);
	private float gulir;
	private int isi;
	private int top;
	private int x0;
	private int y0;
	private int x1;
	private int y1;

	/** Back to the top (new tab). */
	void ulang() {
		gulir = 0.0F;
		tampil.set(0.0F);
		isi = 0;
	}

	/** Jumps to content offset {@code y} (clamped on the next frame). */
	void ke(float y) {
		gulir = y;
	}

	/** Content height of the last frame. */
	int isi() {
		return isi;
	}

	private float maks() {
		return Math.max(0.0F, isi - (y1 - y0));
	}

	/** Clips to the box and returns the y at which the content starts (scrolled). */
	int mulai(Lukis l, int bx0, int by0, int bx1, int by1) {
		x0 = bx0;
		y0 = by0;
		x1 = bx1;
		y1 = by1;
		gulir = Math.max(0.0F, Math.min(maks(), gulir));
		top = Math.round(by0 - tampil.to(gulir, l.detik));
		l.klip(bx0, by0, bx1, by1);
		return top;
	}

	/** Ends the column; {@code bawah} is the y where the content ended (in the scrolled coordinates of {@link #mulai}). */
	void selesai(Lukis l, int bawah) {
		l.lepasKlip();
		isi = bawah - top + 4;
		float maks = maks();
		if (maks > 0.0F) {
			float offset = Math.max(0.0F, Math.min(maks, y0 - top));
			float bead = y0 + 3.0F + (y1 - y0 - 6.0F) * (offset / maks);
			l.g.fill(x1 - 2, y0, x1 - 1, y1, Palette.CORD);
			Brush.disc(l.g, x1 - 1.5F, bead, 2.2F, Palette.JADE);
		}
	}

	/** The mouse wheel over the box; true when the column scrolled (or could). */
	boolean roda(double mx, double my, double dy) {
		if (maks() <= 0.0F || mx < x0 || mx >= x1 || my < y0 || my >= y1) {
			return false;
		}
		gulir -= (float) dy * 18.0F;
		return true;
	}

	/** Up / Down / PageUp / PageDown / Home / End; true when used. */
	boolean tombol(KeyEvent event) {
		if (maks() <= 0.0F) {
			return false;
		}
		float halaman = Math.max(18.0F, y1 - y0 - 18.0F);
		switch (event.key()) {
			case InputConstants.KEY_UP -> gulir -= 18.0F;
			case InputConstants.KEY_DOWN -> gulir += 18.0F;
			case InputConstants.KEY_PAGEUP -> gulir -= halaman;
			case InputConstants.KEY_PAGEDOWN -> gulir += halaman;
			case InputConstants.KEY_HOME -> gulir = 0.0F;
			case InputConstants.KEY_END -> gulir = maks();
			default -> {
				return false;
			}
		}
		return true;
	}
}
