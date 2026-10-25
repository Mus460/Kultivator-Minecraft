package dev.kultivator.client.screen.ui;

import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * The single rank badge of the kit (DESIGN 43.7, moved from {@code tempat/ui/kit/LencanaPangkat}): the guild rank badge
 * E-S (hexagon icon of {@code ikon.png} on a colour frame), the star row "★★★★☆☆☆☆☆" and the Nama Baik rank seal of a place
 * (38.B.5.1). Used by {@link PapanMisi}, the guild pages and the place headers. Text is 1.0.
 */
public final class LencanaPangkat {

	private static final int[] WARNA = {0xFF8D928E, 0xFF4FA04A, 0xFF4F86C8, 0xFF8B4FC8, 0xFFD08A2A, 0xFFC83A30};

	/** Rank colour of "E".."S". */
	public static int warna(String pangkat) {
		int i = LembarKunci.GildaMisi.SEMUA_PANGKAT.indexOf(pangkat);
		return i < 0 ? WARNA[0] : WARNA[i];
	}

	/** The rank badge, {@code size} px; "S" glows. */
	public static void gambar(Lukis l, String pangkat, int x, int y, int size) {
		int slot = LembarKunci.Ikon.pangkat(pangkat);
		if (slot < 0) {
			Brush.centered(l.g, l.font, Component.literal("?"), x + size / 2.0F, y + (size - 8) / 2.0F, l.gaya.teksSamar, 1.0F, false);
			return;
		}
		if ("S".equals(pangkat)) {
			Glyphs.Atlas.cahaya(l.g, x - size / 2, y - size / 2, size * 2, size * 2, Palette.argb(Math.round(120 + 80 * l.denyut()), 0xFFFFFF));
		}
		Glyphs.Atlas.ikon(l.g, slot, x, y, size, 0xFFFFFFFF);
		if (l.di(x, y, x + size, y + size)) {
			l.tip(Component.translatable("kultivator.gui43.kit.pangkat", pangkat).withColor(warna(pangkat)));
		}
	}

	/** Stars: {@code isi} lit of {@code total}; returns the width. */
	public static int bintang(Lukis l, int isi, int total, int x, int y, int size) {
		for (int i = 0; i < total; i++) {
			Glyphs.Atlas.ikon(l.g, i < isi ? Glyphs.Atlas.BINTANG_PENUH : Glyphs.Atlas.BINTANG_KOSONG, x + i * (size - 1), y, size, 0xFFFFFFFF);
		}
		return total * (size - 1) + 1;
	}

	/** The Nama Baik seal of a place: the number and the rank name beside a coloured round seal, right-aligned. Returns the width. */
	public static int namaBaik(Lukis l, int nilai, int pangkat, int xKanan, int y) {
		int p = Math.max(0, Math.min(5, pangkat));
		Component nama = Component.translatable("kultivator.tempat_ui.nama_baik.pangkat." + p);
		int warna = switch (p) {
			case 0 -> Palette.BAHAYA;
			case 1 -> 0xFF8D928E;
			case 2 -> 0xFF6FA8C8;
			case 3 -> Palette.OK;
			case 4 -> Palette.GOLD;
			default -> 0xFFE0C070;
		};
		Component angka = Component.translatable("kultivator.tempat_ui.nama_baik.nilai", nilai);
		int w1 = l.font.width(nama);
		int w2 = l.font.width(angka);
		int w = w1 + w2 + 14;
		int x = xKanan - w;
		Brush.disc(l.g, x + 4.0F, y + 4.0F, 4.0F, warna);
		Brush.ring(l.g, x + 4.0F, y + 4.0F, 2.6F, 3.4F, Palette.argb(0xC0, 0xFFF4E0));
		Brush.text(l.g, l.font, angka, x + 10, y, l.gaya.teks, 1.0F, false);
		Brush.text(l.g, l.font, nama, x + 12 + w2, y, l.gaya.gelap() ? warna : Palette.onPaper(warna), 1.0F, false);
		if (l.di(x, y, xKanan, y + 9)) {
			l.tip(List.of(Component.translatable("kultivator.tempat_ui.nama_baik.judul"), nama,
					Component.translatable("kultivator.tempat_ui.nama_baik.tip")));
		}
		return w;
	}

	private LencanaPangkat() {
	}
}
