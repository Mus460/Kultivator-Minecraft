package dev.kultivator.client.screen.ui;

import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * The single money label of the kit (DESIGN 43.7; moved from {@code tempat/ui/kit/LabelHarga}, with the rolling mode of
 * {@code AngkaGulir}): a price in Nilai Batu with the jade-coin icon, "1.234 NB" or the short form "2,5 rb", red when the
 * player cannot pay; hovering shows the stone breakdown "1 Tinggi · 4 Menengah · 3 Rendah". PK amounts use {@link #pk}
 * with their own bamboo-tally icon (38.M.0: never mixed with NB). Text is 1.0.
 */
public final class LabelHarga {

	private static final int IKON = 10;

	/**
	 * Draws a price with its left edge at {@code x} (or its right edge, {@code kanan}); {@code uang} the money on hand
	 * (-1 = do not compare). Returns the drawn width.
	 */
	public static int gambar(Lukis l, long harga, long uang, float x, float y, boolean kanan, boolean pendek) {
		boolean kurang = uang >= 0L && harga > uang;
		Component angka = Component.literal(pendek ? Brush.singkat(harga) : Brush.angka(harga));
		int lebar = IKON + 2 + l.font.width(angka);
		int x0 = Math.round(kanan ? x - lebar : x);
		int yi = Math.round(y - 1.0F);
		Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.NB, x0, yi, IKON, 0xFFFFFFFF);
		int warna = kurang ? l.gaya.turun() : l.gaya.teks;
		Brush.text(l.g, l.font, angka, x0 + IKON + 2, y, warna, 1.0F, false);
		if (l.di(x0, yi, x0 + lebar, yi + IKON)) {
			List<Component> lines = new ArrayList<>(3);
			lines.add(Component.translatable("kultivator.tempat_ui.harga.tip", Brush.angka(harga)));
			lines.add(Brush.rincianBatu(harga));
			if (kurang) {
				lines.add(Component.translatable("kultivator.tempat_ui.harga.kurang", Brush.angka(harga - uang)));
			}
			l.tip(lines);
		}
		return lebar;
	}

	/** A signed PK amount with the tally icon (ledgers). Returns the width. */
	public static int pk(Lukis l, long pk, float x, float y, boolean kanan, int warna) {
		Component angka = Component.literal(Brush.bertanda(pk));
		int lebar = IKON + 2 + l.font.width(angka);
		int x0 = Math.round(kanan ? x - lebar : x);
		Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.PK, x0, Math.round(y - 1.0F), IKON, 0xFFFFFFFF);
		Brush.text(l.g, l.font, angka, x0 + IKON + 2, y, warna, 1.0F, false);
		return lebar;
	}

	/** A signed NB amount with the coin icon (ledgers): green up, red down. Returns the width. */
	public static int nbBertanda(Lukis l, long nb, float x, float y, boolean kanan) {
		Component angka = Component.literal(Brush.bertanda(nb));
		int lebar = IKON + 2 + l.font.width(angka);
		int x0 = Math.round(kanan ? x - lebar : x);
		Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.NB, x0, Math.round(y - 1.0F), IKON, 0xFFFFFFFF);
		Brush.text(l.g, l.font, angka, x0 + IKON + 2, y, nb > 0 ? l.gaya.naik() : nb < 0 ? l.gaya.turun() : l.gaya.teksSamar, 1.0F, false);
		return lebar;
	}

	/**
	 * Money on hand that rolls to its new value (the Gulungan header's purse, auction prices, balances): drawn gold while
	 * rolling, green / red tinted by direction. Returns the width.
	 */
	public static int gulir(Lukis l, Gulir g, long nilai, float x, float y, boolean kanan) {
		long tampil = g.nilai(nilai, l.kini);
		int w = gambar(l, tampil, -1L, x, y, kanan, tampil >= 100_000L);
		int arah = g.arah(l.kini);
		if (arah != 0) {
			int x0 = Math.round(kanan ? x - w : x);
			l.g.fill(x0 + IKON + 1, Math.round(y + 8), x0 + w, Math.round(y + 9), arah > 0 ? l.gaya.naik() : l.gaya.turun());
		}
		return w;
	}

	/** A number that rolls to its new value over 300 ms when it changes; the first value shows at once. */
	public static final class Gulir {

		private static final long DURASI_MS = 300L;

		private long dari;
		private long ke;
		private long mulai;
		private boolean ada;

		/** The value to show now for target {@code nilai}. */
		public long nilai(long nilai, long kini) {
			if (!ada) {
				ada = true;
				dari = nilai;
				ke = nilai;
				mulai = kini - DURASI_MS;
			} else if (nilai != ke) {
				dari = sekarang(kini);
				ke = nilai;
				mulai = kini;
			}
			return sekarang(kini);
		}

		private long sekarang(long kini) {
			float t = Math.min(1.0F, (kini - mulai) / (float) DURASI_MS);
			float e = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
			return dari + Math.round((ke - dari) * e);
		}

		/** True while rolling. */
		public boolean bergulir(long kini) {
			return ada && kini - mulai < DURASI_MS && dari != ke;
		}

		/** +1 rising, -1 falling, 0 still. */
		public int arah(long kini) {
			return !bergulir(kini) ? 0 : ke > dari ? 1 : -1;
		}
	}

	private LabelHarga() {
	}
}
