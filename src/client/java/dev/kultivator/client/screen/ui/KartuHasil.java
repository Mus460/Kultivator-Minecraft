package dev.kultivator.client.screen.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * The single result card of the kit (DESIGN 43.7; replaces {@code MisiLaporanScreen}, {@code LayarPutusan}, the result of
 * {@code TreasureScreen}, the last duel result and the event results). A fixed head (an optional atlas icon, the title,
 * a subtitle line, the grade stamp on the right: 敗 gagal, 半 sebagian, 成 berhasil, 極 legendaris), the team line, then the
 * body in parts (paragraphs with an optional heading and a bead in green / red / the part's colour) that scrolls through
 * {@link DaftarGulir} like every list. Opened inside the Gulungan (a page, a Di Sini card or a detail view), never as its
 * own screen; Esc is the page's {@code mundur}. Text is 1.0, the stamp glyph 1.5.
 */
public final class KartuHasil {

	/** A grade stamp: glyph, name, colour (RGB). */
	public record Nilai(String glyph, Component nama, int warna) {

		private static final String[] GLYPH = {"敗", "半", "成", "極"};
		private static final int[] WARNA = {0xA0352A, 0xB8860B, 0x2E8B57, 0xD4A017};

		/** The common grades 0 gagal, 1 sebagian, 2 berhasil, 3 legendaris. */
		public static Nilai of(int tingkat) {
			int t = Math.max(0, Math.min(3, tingkat));
			return new Nilai(GLYPH[t], Component.translatable("kultivator.gui43.kit.hasil.nilai." + t), WARNA[t]);
		}
	}

	/** Neutral part, a success, a failure. */
	public static final int BIASA = 0;
	public static final int BERHASIL = 1;
	public static final int GAGAL = 2;

	/** One part of the body: optional heading, the text, status ({@link #BIASA}...), bead colour (RGB; 0 = by status, none when neutral). */
	public record Bagian(@Nullable Component judul, Component teks, int status, int warna) {

		public static Bagian of(Component teks) {
			return new Bagian(null, teks, BIASA, 0);
		}

		public static Bagian judul(Component judul, Component teks) {
			return new Bagian(judul, teks, BIASA, 0);
		}
	}

	private record Baris(@Nullable Component judul, @Nullable FormattedCharSequence teks, int manik) {
	}

	private static final int KEPALA = 30;
	private final DaftarGulir daftar = new DaftarGulir();

	/** Back to the top (another result). */
	public void mulaiUlang(long kini) {
		daftar.mulaiUlang(kini);
	}

	/**
	 * Draws the card in the box. {@code ikon} an atlas slot for the head (-1 = none), {@code nilai} the stamp (null = none),
	 * {@code regu} the team line (null = none).
	 */
	public void gambar(Lukis l, String kunci, int ikon, Component judul, @Nullable Component sub, @Nullable Nilai nilai, @Nullable Component regu,
			List<Bagian> isi, int x0, int y0, int x1, int y1) {
		int tx = x0 + 2;
		if (ikon >= 0) {
			Glyphs.Atlas.ikon(l.g, ikon, x0 + 2, y0 + 3, 20, 0xFFFFFFFF);
			tx = x0 + 26;
		}
		int stempel = nilai != null ? 44 : 0;
		l.judul(judul, tx, y0 + 2, x1 - tx - stempel - 2, l.gaya.aksen);
		if (sub != null) {
			l.teks(sub, tx, y0 + 17, x1 - tx - stempel - 2, l.gaya.teksLemah);
		}
		if (nilai != null) {
			stempel(l, nilai, x1 - 22.0F, y0 + 13.0F);
		}
		int y = y0 + KEPALA;
		if (regu != null) {
			y += Math.round(l.bungkus(regu, x0 + 2, y, x1 - x0 - 4, l.gaya.teksLemah, 18.0F)) + 2;
		}
		l.garis(x0 + 2, x1 - 2, y + 1);
		y += 5;
		int lebar = x1 - x0 - 18;
		List<Baris> rows = new ArrayList<>();
		for (Bagian b : isi) {
			if (b.judul() != null) {
				rows.add(new Baris(b.judul(), null, 0));
			}
			int manik = b.warna() != 0 ? Palette.opaque(b.warna())
					: b.status() == BERHASIL ? Palette.JADE : b.status() == GAGAL ? Palette.DANGER : 0;
			List<FormattedCharSequence> lines = l.font.split(b.teks(), Math.max(20, lebar));
			for (int k = 0; k < lines.size(); k++) {
				rows.add(new Baris(null, lines.get(k), k == 0 ? manik : 0));
			}
			rows.add(new Baris(null, null, 0));
		}
		daftar.judul(i -> i < rows.size() && rows.get(i).judul() != null, 13);
		daftar.gambar(l, kunci, x0, y, x1, y1, rows.size(), 10, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Baris b = rows.get(i);
			if (b.judul() != null) {
				lk.kepala(b.judul(), rx0, ry0, rx1);
				return;
			}
			if (b.teks() == null) {
				return;
			}
			if (b.manik() != 0) {
				Brush.disc(lk.g, rx0 + 5.0F, ry0 + 4.0F, 2.5F, b.manik());
			}
			Brush.text(lk.g, lk.font, b.teks(), rx0 + 12, ry0, lk.gaya.teks, 1.0F, false);
		}, null, null);
	}

	/** The round grade stamp centred at (cx, cy): rings and the glyph at 1.5; the name is its tooltip. */
	public static void stempel(Lukis l, Nilai n, float cx, float cy) {
		int c = n.warna();
		Brush.disc(l.g, cx, cy, 14.0F, Palette.argb(0x30, c));
		Brush.ring(l.g, cx, cy, 11.5F, 13.5F, Palette.argb(0xE0, c));
		Brush.ring(l.g, cx, cy, 9.0F, 10.0F, Palette.argb(0x90, c));
		Brush.centered(l.g, l.font, Component.literal(n.glyph()), cx, cy - 6.5F, Palette.argb(0xFF, c), 1.5F, false);
		if (l.di(Math.round(cx - 14), Math.round(cy - 14), Math.round(cx + 14), Math.round(cy + 14))) {
			l.tip(n.nama().copy().withColor(c));
		}
	}
}
