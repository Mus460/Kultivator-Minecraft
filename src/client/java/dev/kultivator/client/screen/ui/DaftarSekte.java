package dev.kultivator.client.screen.ui;

import dev.kultivator.client.sekte.gabung.ui.IkonGabung;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The single sect list of the kit (DESIGN 43.7; replaces the lists of Direktori, Diplomasi, the Langit neighbours and the
 * sects of Seteru). One 22 px row per sect: the rank seal (gold / silver / bronze for the top three), the emblem
 * ({@code IkonGabung.lambang}) with the sect's colour strip, the bold name, small state icons on the right, the class pips;
 * under it the orthodoxy icon, a second line (class · style, distance...) and the stance bar (red hostile .. gold
 * sworn). An unknown sect shows "?" and a hint. Rows go through {@link DaftarGulir} (click selects, second click / Enter
 * runs {@code pakai}). Text is 1.0.
 */
public final class DaftarSekte {

	/** No stance to show. */
	public static final int TANPA_SIKAP = Integer.MIN_VALUE;
	public static final int TINGGI = 22;

	/**
	 * One sect: id, name, emblem (packed {@code GabungLembar.LAMBANG}), colour (RGB), class pips {@code tingkat} of
	 * {@code tingkatMaks} (0 = none), stance -100..100 ({@link #TANPA_SIKAP} = none) and its name, orthodoxy 0 lurus, 1 netral,
	 * 2 iblis (-1 = none), rank (0 = none), second line, right tag, known, state icons ({@code IkonGabung} slots).
	 */
	public record Sekte(String id, Component nama, int lambang, int warna, int tingkat, int tingkatMaks, int sikap, @Nullable Component sikapNama,
			int aliran, int peringkat, @Nullable Component bawah, @Nullable Component tanda, boolean dikenal, List<Integer> ikon) {

		/** An unknown sect: name and the hint that replaces the second line. */
		public static Sekte asing(String id, Component nama, @Nullable Component petunjuk, int peringkat) {
			return new Sekte(id, nama, 0, 0xB0B0B0, 0, 0, TANPA_SIKAP, null, -1, peringkat, petunjuk, null, false, List.of());
		}
	}

	/** The stance colour (one rule for every page). */
	public static int warnaSikap(int sikap) {
		return sikap <= -30 ? 0xFFC0392B : sikap < -10 ? 0xFFE5821C : sikap < 20 ? 0xFF7A8C83 : sikap < 60 ? 0xFF2E8963 : 0xFFD4A637;
	}

	/** Draws {@code sekte} as rows of {@code daftar} in the box. */
	public static void gambar(Lukis l, String kunci, DaftarGulir daftar, List<Sekte> sekte, int x0, int y0, int x1, int y1, @Nullable Runnable pakai,
			@Nullable IntConsumer onPilih) {
		if (sekte.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable("kultivator.gui43.kit.sekte.kosong"), null, null, x0, y0, x1, y1);
			return;
		}
		daftar.gambar(l, kunci, x0, y0, x1, y1, sekte.size(), TINGGI, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> baris(lk, sekte.get(i), rx0, ry0,
				rx1, ry1, sorot, pilih), pakai, onPilih);
	}

	/** One row (a {@link DaftarGulir.Baris} painter). */
	public static void baris(Lukis l, Sekte s, int x0, int y0, int x1, int y1, boolean sorot, boolean pilih) {
		l.kartu(x0, y0, x1, y1, sorot ? 1.0F : 0.0F, pilih);
		int x = x0 + 2;
		if (s.peringkat() > 0) {
			Component rk = Component.literal(Integer.toString(s.peringkat()));
			int seal = s.peringkat() == 1 ? 0xFFE0B040 : s.peringkat() == 2 ? 0xFFC0C8D0 : s.peringkat() == 3 ? 0xFFC07A40 : 0;
			float r = Math.max(5.5F, l.font.width(rk) / 2.0F + 2.0F);
			if (seal != 0) {
				Brush.disc(l.g, x + r, y0 + 11.0F, r, seal);
			}
			Brush.centered(l.g, l.font, rk, x + r, y0 + 7.0F, seal != 0 ? 0xFF2A1A10 : l.gaya.teksSamar, 1.0F, false);
			x += Math.round(r * 2) + 2;
		}
		boolean kenal = s.dikenal();
		if (kenal) {
			IkonGabung.lambang(l.g, l.font, s.lambang(), x + 8.0F, y0 + 11.0F, 8.0F);
			l.g.fill(x + 18, y0 + 3, x + 20, y1 - 3, Palette.opaque(s.warna()));
		} else {
			IkonGabung.gambar(l.g, IkonGabung.TANYA, x, y0 + 3, 16);
		}
		int tx = x + 23;
		int kanan = x1 - 4;
		int ix = kanan;
		for (int slot : s.ikon()) {
			ix -= 11;
			IkonGabung.gambar(l.g, slot, ix, y0 + 2, 10);
		}
		if (s.tingkatMaks() > 0) {
			int pw = s.tingkatMaks() * 5;
			ix -= pw + 2;
			for (int p = 0; p < s.tingkatMaks(); p++) {
				int px = ix + p * 5;
				l.g.fill(px, y0 + 4, px + 4, y0 + 8, p < s.tingkat() ? Palette.GOLD : Palette.argb(0x50, 0x000000));
			}
		}
		if (s.tanda() != null) {
			int tw = Math.min(60, l.font.width(s.tanda()));
			ix -= tw + 4;
			l.teks(s.tanda(), ix, y0 + 2, tw, l.gaya.teksLemah);
		}
		l.teks(kenal ? Brush.bold(s.nama()) : s.nama(), tx, y0 + 2, ix - tx - 3, kenal ? l.gaya.teks : Palette.terkunci(l.gaya.teks));
		int y2 = y0 + 12;
		int bx = tx;
		if (kenal && s.aliran() >= 0) {
			IkonGabung.gambar(l.g, IkonGabung.ALIRAN + Math.min(2, s.aliran()), bx, y2, 9);
			bx += 11;
		}
		int sikapX = kanan;
		if (kenal && s.sikap() != TANPA_SIKAP) {
			int bw = Math.min(40, (x1 - x0) / 5);
			sikapX = kanan - bw;
			int warna = warnaSikap(s.sikap());
			Bilah.gambar(l, sikapX, y2 + 3, kanan, (s.sikap() + 100) / 200.0F, warna, new float[] {0.5F});
			if (l.di(sikapX, y2, kanan, y1)) {
				List<Component> tip = new ArrayList<>(2);
				tip.add(Component.translatable("kultivator.gui43.kit.sekte.sikap", Brush.bertanda(s.sikap())));
				if (s.sikapNama() != null) {
					tip.add(s.sikapNama().copy().withColor(warna));
				}
				l.tip(tip);
			}
			sikapX -= 4;
		}
		if (s.bawah() != null) {
			l.teks(s.bawah(), bx, y2, sikapX - bx, l.gaya.teksSamar);
		}
	}

	private DaftarSekte() {
	}
}
