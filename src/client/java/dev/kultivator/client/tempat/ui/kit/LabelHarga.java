package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * {@code LabelHarga} (DESIGN 38.U.2): a price in Nilai Batu with the jade-coin icon, "1.234 NB" or the short form "2,5 rb",
 * red when the player cannot pay; hovering shows the stone breakdown "1 Tinggi · 4 Menengah · 3 Rendah". PK amounts use
 * {@link #pk} with their own bamboo-tally icon (38.M.0: never mixed with NB).
 */
public final class LabelHarga {

	/**
	 * Draws a price with its left edge at {@code x} (or its right edge, {@code kanan}); {@code uang} is the money on hand
	 * (-1 = do not compare). Returns the drawn width.
	 */
	public static int gambar(Lukis l, long harga, long uang, float x, float y, boolean kanan, float ukuran, boolean pendek) {
		ukuran = Math.max(1.0F, ukuran); // 43.0.5: no text below 1.0
		boolean kurang = uang >= 0L && harga > uang;
		Component angka = Component.literal(pendek ? Brush.singkat(harga) : Brush.angka(harga));
		int ikon = Math.round(10 * ukuran);
		int lebarTeks = Math.round(l.font.width(angka) * ukuran);
		int lebar = ikon + 2 + lebarTeks;
		int x0 = Math.round(kanan ? x - lebar : x);
		int yi = Math.round(y + (8.0F * ukuran - ikon) / 2.0F);
		IkonTempat.gambar(l.g, LembarKunci.Ikon.NB, x0, yi, ikon, 0xFFFFFFFF);
		int warna = kurang ? TempatWarna.BAHAYA : l.gaya.teks;
		if (kurang && l.gaya.gelap()) {
			warna = 0xFFFF7A6A;
		}
		Brush.text(l.g, l.font, angka, x0 + ikon + 2, y, warna, ukuran, false);
		if (l.di(x0, yi, x0 + lebar, yi + Math.max(ikon, Math.round(9 * ukuran)))) {
			List<Component> lines = new java.util.ArrayList<>(3);
			lines.add(TeksTempat.k("harga.tip", Brush.angka(harga)));
			lines.add(Brush.rincianBatu(harga));
			if (kurang) {
				lines.add(TeksTempat.k("harga.kurang", Brush.angka(harga - uang)));
			}
			l.tip(lines);
		}
		return lebar;
	}

	/** A signed PK amount with the tally icon (ledgers). */
	public static int pk(Lukis l, long pk, float x, float y, boolean kanan, float ukuran, int warna) {
		ukuran = Math.max(1.0F, ukuran);
		Component angka = Component.literal(Brush.bertanda(pk));
		int ikon = Math.round(10 * ukuran);
		int lebar = ikon + 2 + Math.round(l.font.width(angka) * ukuran);
		int x0 = Math.round(kanan ? x - lebar : x);
		IkonTempat.gambar(l.g, LembarKunci.Ikon.PK, x0, Math.round(y + (8.0F * ukuran - ikon) / 2.0F), ikon, 0xFFFFFFFF);
		Brush.text(l.g, l.font, angka, x0 + ikon + 2, y, warna, ukuran, false);
		return lebar;
	}

	/** A signed NB amount with the coin icon (ledgers): green up, red down. */
	public static int nbBertanda(Lukis l, long nb, float x, float y, boolean kanan, float ukuran) {
		ukuran = Math.max(1.0F, ukuran);
		int naik = l.gaya.gelap() ? 0xFF9AE0B0 : 0xFF2E7A4E;
		int turun = l.gaya.gelap() ? 0xFFFF8A7A : TempatWarna.BAHAYA;
		Component angka = Component.literal(Brush.bertanda(nb));
		int ikon = Math.round(10 * ukuran);
		int lebar = ikon + 2 + Math.round(l.font.width(angka) * ukuran);
		int x0 = Math.round(kanan ? x - lebar : x);
		IkonTempat.gambar(l.g, LembarKunci.Ikon.NB, x0, Math.round(y + (8.0F * ukuran - ikon) / 2.0F), ikon, 0xFFFFFFFF);
		Brush.text(l.g, l.font, angka, x0 + ikon + 2, y, nb > 0 ? naik : nb < 0 ? turun : l.gaya.teksSamar, ukuran, false);
		return lebar;
	}

	private LabelHarga() {
	}
}
