package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.network.chat.Component;

/**
 * {@code LencanaPangkat} (DESIGN 38.U.2): the guild rank badge E-S (hexagon icon of {@code ikon.png} on a colour frame),
 * the badge-star row "★★★★☆☆☆☆☆" and the Nama Baik rank seal of a place (38.B.5.1).
 */
public final class LencanaPangkat {

	private static final int[] WARNA = {0xFF8D928E, 0xFF4FA04A, 0xFF4F86C8, 0xFF8B4FC8, 0xFFD08A2A, 0xFFC83A30};

	/** Rank colour of "E".."S". */
	public static int warna(String pangkat) {
		int i = LembarKunci.GildaMisi.SEMUA_PANGKAT.indexOf(pangkat);
		return i < 0 ? WARNA[0] : WARNA[i];
	}

	/** The rank badge, {@code size} px. */
	public static void gambar(Lukis l, String pangkat, int x, int y, int size) {
		int slot = LembarKunci.Ikon.pangkat(pangkat);
		if (slot < 0) {
			Brush.centered(l.g, l.font, Component.literal("?"), x + size / 2.0F, y + (size - 8) / 2.0F, l.gaya.teksSamar, 1.0F, false);
			return;
		}
		if ("S".equals(pangkat)) {
			IkonTempat.cahaya(l.g, x - size / 2, y - size / 2, size * 2, size * 2, Palette.argb(Math.round(120 + 80 * l.denyut()), 0xFFFFFF));
		}
		IkonTempat.gambar(l.g, slot, x, y, size, 0xFFFFFFFF);
	}

	/** Stars: {@code isi} lit of {@code total}; returns the width. */
	public static int bintang(Lukis l, int isi, int total, int x, int y, int size) {
		for (int i = 0; i < total; i++) {
			IkonTempat.gambar(l.g, i < isi ? IkonTempat.BINTANG_PENUH : IkonTempat.BINTANG_KOSONG, x + i * (size - 1), y, size, 0xFFFFFFFF);
		}
		return total * (size - 1) + 1;
	}

	/** The Nama Baik seal of a place: rank name in a coloured round seal plus the number. Returns the width. */
	public static int namaBaik(Lukis l, int nilai, int pangkat, int xKanan, int y) {
		Component nama = TeksTempat.k("nama_baik.pangkat." + Math.max(0, Math.min(5, pangkat)));
		int warna = switch (Math.max(0, Math.min(5, pangkat))) {
			case 0 -> TempatWarna.BAHAYA;
			case 1 -> 0xFF8D928E;
			case 2 -> 0xFF6FA8C8;
			case 3 -> TempatWarna.OK;
			case 4 -> TempatWarna.EMAS;
			default -> 0xFFE0C070;
		};
		Component angka = TeksTempat.k("nama_baik.nilai", nilai);
		int w1 = l.font.width(nama);
		int w2 = l.font.width(angka);
		int w = w1 + w2 + 14;
		int x = xKanan - w;
		Brush.disc(l.g, x + 4.0F, y + 4.0F, 4.0F, warna);
		Brush.ring(l.g, x + 4.0F, y + 4.0F, 2.6F, 3.4F, Palette.argb(0xC0, 0xFFF4E0));
		Brush.text(l.g, l.font, angka, x + 10, y, l.gaya.teks, 1.0F, false);
		Brush.text(l.g, l.font, nama, x + 12 + w2, y, warna, 1.0F, false);
		if (l.di(x, y, xKanan, y + 9)) {
			l.tip(java.util.List.of(TeksTempat.k("nama_baik.judul"), nama, TeksTempat.k("nama_baik.tip")));
		}
		return w;
	}

	private LencanaPangkat() {
	}
}
