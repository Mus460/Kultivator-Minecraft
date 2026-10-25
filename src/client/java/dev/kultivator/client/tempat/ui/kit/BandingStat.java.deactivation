package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.network.chat.Component;

/**
 * {@code BandingStat} (DESIGN 38.U.2): a stat bar with the value on the right and the difference to the weapon in hand:
 * a green ▲ and a green extension when better, a red ▼ and a red notch when worse, "=" when equal. Bars are scaled to a
 * per-stat maximum so a long sword and a short dagger read at a glance.
 */
public final class BandingStat {

	/** One row; {@code banding} NaN when there is nothing to compare. Returns the row height. */
	public static int gambar(Lukis l, Component nama, float nilai, float banding, float maks, int x0, int y, int x1, boolean desimal) {
		int labelW = Math.min(64, (x1 - x0) / 3);
		l.teks(nama, x0, y, labelW - 2, l.gaya.teksLemah, 0.75F);
		String angka = desimal ? TeksTempat.desimal1(nilai) : Integer.toString(Math.round(nilai));
		Component delta;
		int dc;
		if (Float.isNaN(banding)) {
			delta = Component.empty();
			dc = l.gaya.teksSamar;
		} else if (Math.abs(banding) < 0.05F) {
			delta = Component.literal("=");
			dc = l.gaya.teksSamar;
		} else if (banding > 0) {
			delta = Component.literal("▲" + (desimal ? TeksTempat.desimal1(banding) : Integer.toString(Math.round(banding))));
			dc = l.gaya.gelap() ? 0xFF9AE0B0 : 0xFF2E7A4E;
		} else {
			delta = Component.literal("▼" + (desimal ? TeksTempat.desimal1(-banding) : Integer.toString(Math.round(-banding))));
			dc = l.gaya.gelap() ? 0xFFFF8A7A : TempatWarna.BAHAYA;
		}
		int deltaW = Math.round(l.font.width(delta) * 0.75F);
		int angkaW = Math.round(l.font.width(angka) * 0.75F);
		int barX0 = x0 + labelW;
		int barX1 = x1 - angkaW - deltaW - 6;
		int by = y + 2;
		if (barX1 - barX0 > 12) {
			l.g.fill(barX0, by, barX1, by + 4, Palette.argb(0x50, 0x000000));
			float frac = maks <= 0 ? 0 : Math.min(1.0F, nilai / maks);
			int fill = barX0 + Math.round((barX1 - barX0) * frac);
			l.g.fillGradient(barX0, by, fill, by + 4, TempatWarna.EMAS_TERANG, TempatWarna.EMAS_GELAP);
			if (!Float.isNaN(banding) && Math.abs(banding) >= 0.05F && maks > 0) {
				float lain = Math.min(1.0F, Math.max(0.0F, (nilai - banding) / maks));
				int px = barX0 + Math.round((barX1 - barX0) * lain);
				if (banding > 0) {
					l.g.fill(px, by, fill, by + 4, Palette.argb(0xC0, 0x4FC07A));
				} else {
					l.g.fill(fill, by, px, by + 4, Palette.argb(0x90, 0xD04030));
				}
				l.g.fill(px, by - 1, px + 1, by + 5, l.gaya.teks);
			}
		}
		Brush.text(l.g, l.font, Component.literal(angka), x1 - angkaW - deltaW - 3, y, l.gaya.teks, 0.75F, false);
		Brush.text(l.g, l.font, delta, x1 - deltaW, y, dc, 0.75F, false);
		return 9;
	}

	private BandingStat() {
	}
}
