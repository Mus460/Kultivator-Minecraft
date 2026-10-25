package dev.kultivator.client.screen.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * The single chronicle / log of the kit (DESIGN 43.7; replaces {@code LogPage}, the news of MandiriBoard, the Langit
 * chronicle, the per-sect chronicle of Diplomasi, {@code formasi/LogPanel} and the history of MusuhPage). The last 50
 * entries, newest first, grouped under day headings ("Hari ini", "Hari 12": the time stamp, 43.0.4 headings, never tabs);
 * each entry wraps at 1.0 with a small seal in its kind colour. An optional {@link Saring} row filters by text and kind.
 * Built on {@link DaftarGulir} (one row per wrapped line), so the wheel, the scroll bar and ↑↓ PgUp PgDn work as
 * everywhere else.
 */
public final class Kronik {

	/** One entry: text, day index (game day; -1 = no day), kind id for the filter chips (null = none), seal colour (0 = seal red). */
	public record Entri(Component teks, long hari, @Nullable String jenis, int warna) {

		public static Entri of(Component teks, long hari) {
			return new Entri(teks, hari, null, 0);
		}
	}

	public static final int MAKS = 50;
	private static final int BARIS = 10;
	private static final int JUDUL = 13;

	/** One drawn row: a day heading (teks) or a wrapped line (baris) of an entry. */
	private record Baris(@Nullable Component judul, @Nullable FormattedCharSequence baris, boolean awal, int warna) {
	}

	private final DaftarGulir daftar = new DaftarGulir();
	private final @Nullable Saring saring;

	/** {@code saring} null = no filter row. */
	public Kronik(@Nullable Saring saring) {
		this.saring = saring;
	}

	/** Back to the top (new subject). */
	public void mulaiUlang(long kini) {
		daftar.mulaiUlang(kini);
	}

	/**
	 * Draws entries {@code entri} (oldest first, as the server keeps them; the newest {@link #MAKS} are shown newest first)
	 * in the box; {@code hariIni} today's day index.
	 */
	public void gambar(Lukis l, String kunci, List<Entri> entri, long hariIni, int x0, int y0, int x1, int y1) {
		int y = y0;
		if (saring != null) {
			y += saring.gambar(l, kunci + ":saring", x0, y, x1) + 3;
		}
		int lebar = x1 - x0 - 16 - 6;
		List<Baris> out = new ArrayList<>();
		long hariLalu = Long.MIN_VALUE;
		int n = 0;
		for (int i = entri.size() - 1; i >= 0 && n < MAKS; i--) {
			Entri e = entri.get(i);
			if (saring != null && !saring.cocok(e.teks(), e.jenis())) {
				continue;
			}
			n++;
			if (e.hari() >= 0 && e.hari() != hariLalu) {
				Component judul = e.hari() == hariIni ? Component.translatable("kultivator.gui43.kit.kronik.hari_ini")
						: Component.translatable("kultivator.gui43.kit.kronik.hari", e.hari());
				out.add(new Baris(judul, null, false, 0));
				hariLalu = e.hari();
			}
			int warna = e.warna() == 0 ? Palette.SEAL : Palette.opaque(e.warna());
			List<FormattedCharSequence> lines = l.font.split(e.teks(), Math.max(20, lebar));
			for (int k = 0; k < lines.size(); k++) {
				out.add(new Baris(null, lines.get(k), k == 0, warna));
			}
		}
		if (out.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable("kultivator.gui43.kit.kronik.kosong"), null, null, x0, y, x1, y1);
			return;
		}
		List<Baris> r = out;
		daftar.judul(i -> i < r.size() && r.get(i).judul() != null, JUDUL);
		daftar.gambar(l, kunci, x0, y, x1, y1, r.size(), BARIS, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Baris b = r.get(i);
			if (b.judul() != null) {
				lk.kepala(b.judul(), rx0, ry0, rx1);
				return;
			}
			if (b.awal()) {
				Brush.rect(lk.g, rx0 + 6.0F, ry0 + 2.0F, rx0 + 10.0F, ry0 + 6.0F, b.warna());
			} else {
				Brush.rect(lk.g, rx0 + 7.5F, ry0 - 1.0F, rx0 + 8.5F, ry0 + 9.0F, Palette.argb(0x50, b.warna()));
			}
			Brush.text(lk.g, lk.font, b.baris(), rx0 + 16, ry0, lk.gaya.teks, 1.0F, false);
		}, null, null);
	}
}
