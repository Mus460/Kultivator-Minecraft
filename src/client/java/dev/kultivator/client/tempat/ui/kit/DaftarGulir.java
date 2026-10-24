package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * {@code DaftarGulir} (DESIGN 38.U.2): a scrolling list of fixed-height rows with a jade scroll bar (drag it, or the mouse
 * wheel), keyboard ↑↓ PgUp PgDn Home End while the list has focus, the selected row kept in view, and rows that appear one
 * after another (20 ms per row, at most 8) when the list is (re)filled. Rows are drawn by the screen through {@link Baris};
 * a click selects a row, a second click on the selected row (or Enter) runs the screen's {@code pakai} action.
 */
public final class DaftarGulir {

	/** Draws row {@code i} into its box. */
	@FunctionalInterface
	public interface Baris {
		void gambar(Lukis l, int i, int x0, int y0, int x1, int y1, boolean sorot, boolean pilih);
	}

	private static final int BATANG = 4;
	private static final long MUNCUL_MS = 20L;
	private static final long MUNCUL_LAMA = 140L;

	private int pilih;
	private float gulir;
	private float gulirTampil;
	private int jumlah = -1;
	private int tinggi = 18;
	private int tinggiKotak;
	private long muncul;
	private @Nullable Runnable pakai;
	private @Nullable java.util.function.IntConsumer onPilih;
	// last frame geometry (for wheel and the scroll bar)
	int x0;
	int y0;
	int x1;
	int y1;
	private int batangY0;
	private int batangY1;
	private boolean seret;
	private float seretOffset;

	public int pilihan() {
		return jumlah <= 0 ? -1 : Mth.clamp(pilih, 0, jumlah - 1);
	}

	public void setPilih(int i) {
		pilih = Math.max(0, i);
		pastikanTerlihat();
	}

	/** Restarts the row cascade and goes to the top (new tab, new sheet content). */
	public void mulaiUlang(long kini) {
		muncul = kini;
		gulir = 0.0F;
		gulirTampil = 0.0F;
		pilih = 0;
	}

	/**
	 * Draws the list. {@code pakai} runs on Enter / second click on the selected row; {@code onPilih} on every selection
	 * change (may be null).
	 */
	public void gambar(Lukis l, String kunci, int x0, int y0, int x1, int y1, int jumlah, int tinggiBaris, Baris baris, @Nullable Runnable pakai,
			@Nullable java.util.function.IntConsumer onPilih) {
		if (jumlah != this.jumlah) {
			if (this.jumlah < 0) {
				muncul = l.kini;
			}
			this.jumlah = jumlah;
			pilih = Mth.clamp(pilih, 0, Math.max(0, jumlah - 1));
		}
		this.tinggi = Math.max(8, tinggiBaris);
		this.pakai = pakai;
		this.onPilih = onPilih;
		this.x0 = x0;
		this.y0 = y0;
		this.x1 = x1;
		this.y1 = y1;
		this.tinggiKotak = y1 - y0;
		int isiTinggi = jumlah * tinggi;
		float maks = Math.max(0, isiTinggi - tinggiKotak);
		gulir = Mth.clamp(gulir, 0.0F, maks);
		gulirTampil += (gulir - gulirTampil) * Math.min(1.0F, l.detik * 18.0F);
		if (Math.abs(gulir - gulirTampil) < 0.3F) {
			gulirTampil = gulir;
		}
		boolean adaBatang = isiTinggi > tinggiKotak;
		int kanan = adaBatang ? x1 - BATANG - 2 : x1;
		l.zona(kunci, x0, y0, x1, y1, null, true, null, true);
		l.daftarAktif = l.difokus(kunci) ? this : l.daftarAktif;
		l.semuaDaftar.add(this);
		l.klip(x0, y0, x1, y1);
		int first = Math.max(0, (int) (gulirTampil / tinggi));
		int last = Math.min(jumlah - 1, (int) ((gulirTampil + tinggiKotak) / tinggi));
		for (int i = first; i <= last; i++) {
			int ry0 = Math.round(y0 + i * tinggi - gulirTampil);
			int ry1 = ry0 + tinggi - 1;
			long sejak = l.kini - muncul - Math.min(i - first, 8) * MUNCUL_MS;
			float tampak = Mth.clamp(sejak / (float) MUNCUL_LAMA, 0.0F, 1.0F);
			if (tampak <= 0.0F) {
				continue;
			}
			int geser = Math.round((1.0F - tampak) * 6.0F);
			boolean over = l.di(x0, ry0, kanan, ry1);
			final int idx = i;
			l.zona(kunci + "#" + i, x0, ry0, kanan, ry1, () -> klik(idx), true, null, false);
			l.g.pose().pushMatrix();
			l.g.pose().translate(geser, 0.0F);
			baris.gambar(l, i, x0, ry0, kanan, ry1, over, i == pilihan());
			l.g.pose().popMatrix();
		}
		l.lepasKlip();
		if (adaBatang) {
			int bx = x1 - BATANG;
			l.g.fill(bx, y0, x1, y1, Palette.argb(0x40, 0x000000));
			float frac = tinggiKotak / (float) isiTinggi;
			int len = Math.max(10, Math.round(tinggiKotak * frac));
			int pos = Math.round((tinggiKotak - len) * (maks <= 0 ? 0 : gulirTampil / maks));
			batangY0 = y0 + pos;
			batangY1 = batangY0 + len;
			boolean overBatang = l.di(bx - 1, y0, x1 + 1, y1) || seret;
			l.g.fillGradient(bx, batangY0, x1, batangY1, overBatang ? Palette.JADE_LIGHT : Palette.JADE, Palette.JADE_DARK);
			l.g.fill(bx, batangY0, x1, batangY0 + 1, Palette.JADE_PALE);
			l.zona(kunci + "#batang", bx - 1, y0, x1 + 1, y1, null, true, null, false);
		} else {
			batangY0 = batangY1 = 0;
		}
		// "6 dari 9" style footer hint when not everything fits
		if (adaBatang && l.difokus(kunci)) {
			Brush.text(l.g, l.font, TeksTempat.k("daftar.posisi", pilihan() + 1, jumlah), x0 + 2, y1 + 1, l.gaya.teksSamar, 0.6F, false);
		}
		l.fokusCincin(kunci, x0, y0, x1, y1);
	}

	private void klik(int i) {
		if (i == pilihan()) {
			if (pakai != null) {
				pakai.run();
			}
			return;
		}
		pilih = i;
		UiSound.hover();
		if (onPilih != null) {
			onPilih.accept(i);
		}
	}

	private void pastikanTerlihat() {
		int p = pilihan();
		if (p < 0) {
			return;
		}
		float top = p * tinggi;
		float bottom = top + tinggi;
		if (top < gulir) {
			gulir = top;
		} else if (bottom > gulir + tinggiKotak) {
			gulir = bottom - tinggiKotak;
		}
	}

	/** Keyboard: delta rows (±1 arrows, ±page, ±huge for Home/End). */
	public void geser(int delta) {
		if (jumlah <= 0) {
			return;
		}
		int baru = Mth.clamp(pilihan() + delta, 0, jumlah - 1);
		if (baru != pilihan()) {
			pilih = baru;
			UiSound.hover();
			if (onPilih != null) {
				onPilih.accept(baru);
			}
			pastikanTerlihat();
		}
	}

	public int barisPerHalaman() {
		return Math.max(1, tinggiKotak / Math.max(1, tinggi) - 1);
	}

	/** Enter on the list. */
	public void pakai() {
		if (pakai != null && pilihan() >= 0) {
			pakai.run();
		}
	}

	public boolean berisi(double x, double y) {
		return x >= x0 && y >= y0 && x < x1 && y < y1;
	}

	/** Mouse wheel over the list. */
	public void roda(double delta) {
		gulir -= (float) delta * tinggi * 1.5F;
	}

	/** Mouse down: starts dragging when on the bar. */
	public boolean tekan(double x, double y) {
		if (batangY1 > batangY0 && x >= x1 - BATANG - 1 && x < x1 + 1 && y >= y0 && y < y1) {
			seret = true;
			seretOffset = (float) (y - batangY0);
			if (y < batangY0 || y > batangY1) {
				seretOffset = (batangY1 - batangY0) / 2.0F;
				seretKe(y);
			}
			return true;
		}
		return false;
	}

	public boolean menyeret() {
		return seret;
	}

	public void seretKe(double y) {
		int len = batangY1 - batangY0;
		float ruang = Math.max(1, tinggiKotak - len);
		float frac = Mth.clamp((float) (y - seretOffset - y0) / ruang, 0.0F, 1.0F);
		float maks = Math.max(0, jumlah * tinggi - tinggiKotak);
		gulir = frac * maks;
		gulirTampil = gulir;
	}

	public void lepas() {
		seret = false;
	}
}
