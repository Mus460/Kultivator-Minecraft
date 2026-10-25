package dev.kultivator.client.screen.ui;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The single progress bar of the kit (DESIGN 43.7, G61; replaces the jade bars of {@code KotaPage}, {@code PetaUi} values,
 * {@code TemaUi} meters, {@code BandingStat}, the grudge bar of Saingan, tower progress and the daily-limit bar of the
 * treasury): a gold fill in a dark groove, optional tick marks, an optional label row above, a compare mode (green
 * extension when better, red notch when worse) and the 7-day ledger chart ({@link #grafik7}, G62). Text is 1.0.
 */
public final class Bilah {

	private static final int TEBAL = 4;

	/** A bare bar from x0 to x1 at y (4 px high); {@code frac} 0..1; {@code warna} the fill (0 = gold); marks at fractions ({@code tanda} may be null). */
	public static void gambar(Lukis l, int x0, int y, int x1, float frac, int warna, float[] tanda) {
		if (x1 - x0 < 4) {
			return;
		}
		l.g.fill(x0, y, x1, y + TEBAL, Palette.argb(0x50, 0x000000));
		int fill = x0 + Math.round((x1 - x0) * Math.max(0.0F, Math.min(1.0F, frac)));
		if (warna == 0) {
			l.g.fillGradient(x0, y, fill, y + TEBAL, Palette.GOLD_LIGHT, Palette.GOLD_DARK);
		} else {
			l.g.fillGradient(x0, y, fill, y + TEBAL, Palette.lighten(warna, 0.25F), Palette.darken(warna, 0.2F));
		}
		if (tanda != null) {
			for (float t : tanda) {
				int tx = x0 + Math.round((x1 - x0) * Math.max(0.0F, Math.min(1.0F, t)));
				l.g.fill(tx, y - 1, tx + 1, y + TEBAL + 1, Palette.argb(0xC0, l.gaya.teks));
			}
		}
	}

	/**
	 * A labelled row: the name on the left, the bar in the middle, the value text (or "nilai / maks") on the right.
	 * Returns the row height (10).
	 */
	public static int baris(Lukis l, Component nama, float nilai, float maks, int x0, int y, int x1, @Nullable Component teksNilai, int warna) {
		int labelW = Math.min(80, (x1 - x0) / 3);
		l.teks(nama, x0, y, labelW - 3, l.gaya.teksLemah);
		Component angka = teksNilai != null ? teksNilai : Component.literal(Math.round(nilai) + " / " + Math.round(maks));
		int angkaW = l.font.width(angka);
		l.kanan(angka, x1, y, l.gaya.teks);
		gambar(l, x0 + labelW, y + 2, x1 - angkaW - 4, maks <= 0 ? 0.0F : nilai / maks, warna, null);
		if (l.di(x0, y, x1, y + 9)) {
			l.tip(java.util.List.of(nama, angka));
		}
		return 10;
	}

	/**
	 * A compare row (was {@code BandingStat}): the value and the difference to {@code banding} (NaN = nothing to compare):
	 * green ▲ and a green extension when better, red ▼ and a red notch when worse, "=" when equal. Returns the height (10).
	 */
	public static int banding(Lukis l, Component nama, float nilai, float banding, float maks, int x0, int y, int x1, boolean desimal) {
		int labelW = Math.min(70, (x1 - x0) / 3);
		l.teks(nama, x0, y, labelW - 3, l.gaya.teksLemah);
		String angka = desimal ? Brush.desimal1(nilai) : Integer.toString(Math.round(nilai));
		Component delta;
		int dc;
		if (Float.isNaN(banding)) {
			delta = Component.empty();
			dc = l.gaya.teksSamar;
		} else if (Math.abs(banding) < 0.05F) {
			delta = Component.literal("=");
			dc = l.gaya.teksSamar;
		} else if (banding > 0) {
			delta = Component.literal("▲" + (desimal ? Brush.desimal1(banding) : Integer.toString(Math.round(banding))));
			dc = l.gaya.naik();
		} else {
			delta = Component.literal("▼" + (desimal ? Brush.desimal1(-banding) : Integer.toString(Math.round(-banding))));
			dc = l.gaya.turun();
		}
		int deltaW = l.font.width(delta);
		int angkaW = l.font.width(angka);
		int barX0 = x0 + labelW;
		int barX1 = x1 - angkaW - deltaW - 6;
		int by = y + 2;
		if (barX1 - barX0 > 12) {
			float frac = maks <= 0 ? 0 : Math.min(1.0F, nilai / maks);
			gambar(l, barX0, by, barX1, frac, 0, null);
			int fill = barX0 + Math.round((barX1 - barX0) * frac);
			if (!Float.isNaN(banding) && Math.abs(banding) >= 0.05F && maks > 0) {
				float lain = Math.min(1.0F, Math.max(0.0F, (nilai - banding) / maks));
				int px = barX0 + Math.round((barX1 - barX0) * lain);
				if (banding > 0) {
					l.g.fill(px, by, fill, by + TEBAL, Palette.argb(0xC0, 0x4FC07A));
				} else {
					l.g.fill(fill, by, px, by + TEBAL, Palette.argb(0x90, 0xD04030));
				}
				l.g.fill(px, by - 1, px + 1, by + TEBAL + 1, l.gaya.teks);
			}
		}
		Brush.text(l.g, l.font, Component.literal(angka), x1 - angkaW - deltaW - 3, y, l.gaya.teks, 1.0F, false);
		Brush.text(l.g, l.font, delta, x1 - deltaW, y, dc, 1.0F, false);
		return 10;
	}

	/**
	 * The 7-day ledger chart (G62, one painter for Kas and Dagang): one bar per day from a zero line, green up, red down,
	 * the last (today) outlined in gold; hovering a bar names its day and amount.
	 */
	public static void grafik7(Lukis l, long[] nilai, int x0, int y0, int x1, int y1) {
		int n = nilai.length;
		if (n == 0 || x1 - x0 < n * 3 || y1 - y0 < 8) {
			return;
		}
		long maks = 1L;
		boolean adaNegatif = false;
		for (long v : nilai) {
			maks = Math.max(maks, Math.abs(v));
			adaNegatif |= v < 0;
		}
		int nol = adaNegatif ? (y0 + y1) / 2 : y1 - 1;
		int ruang = adaNegatif ? (y1 - y0) / 2 - 1 : y1 - y0 - 1;
		l.g.fill(x0, nol, x1, nol + 1, Palette.argb(0x80, l.gaya.teksSamar));
		float per = (x1 - x0) / (float) n;
		for (int i = 0; i < n; i++) {
			int bx0 = Math.round(x0 + i * per + 1);
			int bx1 = Math.round(x0 + (i + 1) * per - 1);
			int tinggi = Math.round(ruang * Math.abs(nilai[i]) / (float) maks);
			int warna = nilai[i] >= 0 ? l.gaya.naik() : l.gaya.turun();
			if (nilai[i] >= 0) {
				l.g.fill(bx0, nol - tinggi, bx1, nol, warna);
			} else {
				l.g.fill(bx0, nol + 1, bx1, nol + 1 + tinggi, warna);
			}
			if (i == n - 1) {
				Brush.frame(l.g, bx0 - 1, Math.min(nol - tinggi, nol) - 1, bx1 + 1, Math.max(nol + 1 + (nilai[i] < 0 ? tinggi : 0), nol + 1) + 1,
						Palette.GOLD);
			}
			if (l.di(bx0, y0, bx1, y1)) {
				int lalu = n - 1 - i;
				Component hari = lalu == 0 ? Component.translatable("kultivator.tempat_ui.waktu.hari_ini") : Component.translatable("kultivator.gui43.kit.hari_lalu", lalu);
				l.tip(java.util.List.of(hari, Component.literal(Brush.bertanda(nilai[i]))));
			}
		}
	}

	private Bilah() {
	}
}
