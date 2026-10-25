package dev.kultivator.client.screen.ui;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The single scrolling list of the kit (DESIGN 43.7, G12; moved from {@code tempat/ui/kit/DaftarGulir} and replaces the
 * hand-made scrolls of the hub pages and {@code codex/CodexGrid}). Rows or a grid of cells, with optional group headings
 * spanning the full width ({@link #judul}); only the visible entries are drawn; a jade scroll bar (drag it, or the mouse
 * wheel); ↑↓ PgUp PgDn Home End while the list has focus (←/→ too in a grid), the selection kept in view and headings
 * skipped; entries appear one after another (20 ms each, at most 8) when the list is (re)filled. Entries are drawn by the
 * page through {@link Baris}; a click selects an entry, a second click on the selected one (or Enter) runs {@code pakai}.
 */
public final class DaftarGulir {

	/** Draws entry {@code i} into its box. */
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
	private int tinggiKotak;
	private int isiTinggi;
	private int kolom = 1;
	private long muncul;
	private @Nullable Runnable pakai;
	private @Nullable IntConsumer onPilih;
	private IntPredicate judul = i -> false;
	private int tinggiJudul = 13;
	// layout of the last frame, relative to the content top
	private int[] posX = new int[0];
	private int[] posY = new int[0];
	private int[] posW = new int[0];
	private int[] posH = new int[0];
	int x0;
	int y0;
	int x1;
	int y1;
	private int batangY0;
	private int batangY1;
	private boolean seret;
	private float seretOffset;

	/** Entries for which {@code judul} is true are group headings: full width, {@code tinggi} high, never selected. */
	public DaftarGulir judul(IntPredicate judul, int tinggi) {
		this.judul = judul;
		this.tinggiJudul = Math.max(9, tinggi);
		return this;
	}

	public int pilihan() {
		return jumlah <= 0 ? -1 : Mth.clamp(pilih, 0, jumlah - 1);
	}

	public void setPilih(int i) {
		pilih = Math.max(0, i);
		pastikanTerlihat();
	}

	/** Restarts the cascade and goes to the top (new tab, new content). */
	public void mulaiUlang(long kini) {
		muncul = kini;
		gulir = 0.0F;
		gulirTampil = 0.0F;
		pilih = 0;
	}

	/** Rows of {@code tinggiBaris} (see {@link #gambarKisi} for cells). */
	public void gambar(Lukis l, String kunci, int x0, int y0, int x1, int y1, int jumlah, int tinggiBaris, Baris baris, @Nullable Runnable pakai,
			@Nullable IntConsumer onPilih) {
		gambarKisi(l, kunci, x0, y0, x1, y1, jumlah, 0, tinggiBaris, baris, pakai, onPilih);
	}

	/**
	 * A grid of cells at least {@code lebarSel} wide (0 = one column) and {@code tinggiSel} high; headings break the grid.
	 * {@code pakai} runs on Enter / a second click on the selected entry; {@code onPilih} on every selection change.
	 */
	public void gambarKisi(Lukis l, String kunci, int x0, int y0, int x1, int y1, int jumlah, int lebarSel, int tinggiSel, Baris sel,
			@Nullable Runnable pakai, @Nullable IntConsumer onPilih) {
		if (jumlah != this.jumlah) {
			if (this.jumlah < 0) {
				muncul = l.kini;
			}
			this.jumlah = jumlah;
			pilih = Mth.clamp(pilih, 0, Math.max(0, jumlah - 1));
		}
		this.pakai = pakai;
		this.onPilih = onPilih;
		this.x0 = x0;
		this.y0 = y0;
		this.x1 = x1;
		this.y1 = y1;
		this.tinggiKotak = y1 - y0;
		int tinggi = Math.max(10, tinggiSel);
		// lay out once without a bar; again narrower if it overflows
		boolean adaBatang = tata(x1 - x0, lebarSel, tinggi) > tinggiKotak;
		if (adaBatang) {
			tata(x1 - x0 - BATANG - 2, lebarSel, tinggi);
		}
		int kanan = adaBatang ? x1 - BATANG - 2 : x1;
		float maks = Math.max(0, isiTinggi - tinggiKotak);
		gulir = Mth.clamp(gulir, 0.0F, maks);
		gulirTampil += (gulir - gulirTampil) * Math.min(1.0F, l.detik * 18.0F);
		if (Math.abs(gulir - gulirTampil) < 0.3F) {
			gulirTampil = gulir;
		}
		Lukis.Zona fokusZona = l.zona(kunci, x0, y0, x1, y1, null, true, null, true);
		if (fokusZona != null && kolom > 1) {
			fokusZona.geser = this::geser;
		}
		if (l.difokus(kunci)) {
			l.daftarAktif = this;
		}
		l.semuaDaftar.add(this);
		l.klip(x0, y0, kanan, y1);
		int urut = 0;
		for (int i = 0; i < jumlah; i++) {
			int ry0 = Math.round(y0 + posY[i] - gulirTampil);
			int ry1 = ry0 + posH[i];
			if (ry1 < y0 || ry0 > y1) {
				continue;
			}
			long sejak = l.kini - muncul - Math.min(urut++, 8) * MUNCUL_MS;
			float tampak = Mth.clamp(sejak / (float) MUNCUL_LAMA, 0.0F, 1.0F);
			if (tampak <= 0.0F) {
				continue;
			}
			int rx0 = x0 + posX[i];
			int rx1 = rx0 + posW[i];
			boolean kepala = judul.test(i);
			boolean over = !kepala && l.di(rx0, ry0, rx1, ry1 - 1);
			if (!kepala) {
				final int idx = i;
				l.zona(kunci + "#" + i, rx0, ry0, rx1, ry1 - 1, () -> klik(idx), true, null, false);
			}
			int geser = Math.round((1.0F - tampak) * 6.0F);
			l.g.pose().pushMatrix();
			l.g.pose().translate(geser, 0.0F);
			sel.gambar(l, i, rx0, ry0, rx1, ry1 - 1, over, !kepala && i == pilihan());
			l.g.pose().popMatrix();
		}
		l.lepasKlip();
		if (adaBatang) {
			int bx = x1 - BATANG;
			l.g.fill(bx, y0, x1, y1, Palette.argb(0x40, 0x000000));
			int len = Math.max(10, Math.round(tinggiKotak * (tinggiKotak / (float) isiTinggi)));
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
		l.fokusCincin(kunci, x0, y0, x1, y1);
	}

	/** Lays out the entries for {@code lebar}; returns the content height. */
	private int tata(int lebar, int lebarSel, int tinggi) {
		if (posY.length < jumlah) {
			posX = new int[jumlah];
			posY = new int[jumlah];
			posW = new int[jumlah];
			posH = new int[jumlah];
		}
		kolom = lebarSel <= 0 ? 1 : Math.max(1, lebar / lebarSel);
		int selW = lebar / kolom;
		int y = 0;
		int c = 0;
		for (int i = 0; i < jumlah; i++) {
			if (judul.test(i)) {
				if (c > 0) {
					y += tinggi;
					c = 0;
				}
				posX[i] = 0;
				posY[i] = y;
				posW[i] = lebar;
				posH[i] = tinggiJudul;
				y += tinggiJudul;
				continue;
			}
			posX[i] = c * selW;
			posY[i] = y;
			posW[i] = kolom == 1 ? lebar : selW - 2;
			posH[i] = tinggi;
			c++;
			if (c >= kolom) {
				c = 0;
				y += tinggi;
			}
		}
		if (c > 0) {
			y += tinggi;
		}
		isiTinggi = y;
		return y;
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
		if (p < 0 || p >= posY.length) {
			return;
		}
		float top = posY[p];
		float bottom = top + posH[p];
		if (top < gulir) {
			gulir = top;
		} else if (bottom > gulir + tinggiKotak) {
			gulir = bottom - tinggiKotak;
		}
	}

	/** Keyboard: ±1 entry (←/→ in a grid, ↑/↓ in rows); see {@link #geserBaris} for ↑/↓ in a grid. */
	public void geser(int delta) {
		pindah(delta, delta > 0 ? 1 : -1);
	}

	/** ↑/↓ PgUp PgDn Home End: whole rows ({@code delta} rows, ±kolom entries each). */
	public void geserBaris(int delta) {
		pindah(delta * kolom, delta > 0 ? 1 : -1);
	}

	private void pindah(int delta, int arah) {
		if (jumlah <= 0) {
			return;
		}
		int baru = Mth.clamp(pilihan() + delta, 0, jumlah - 1);
		// headings are skipped in the direction of travel (or back when the end is a heading)
		while (baru >= 0 && baru < jumlah && judul.test(baru)) {
			baru += arah;
		}
		if (baru < 0 || baru >= jumlah) {
			baru = pilihan();
		}
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
		int p = pilihan();
		int h = p >= 0 && p < posH.length && posH[p] > 0 ? posH[p] : 12;
		return Math.max(1, tinggiKotak / h - 1);
	}

	/** Enter on the list. */
	public void pakai() {
		if (pakai != null && pilihan() >= 0 && !judul.test(pilihan())) {
			pakai.run();
		}
	}

	public boolean berisi(double x, double y) {
		return x >= x0 && y >= y0 && x < x1 && y < y1;
	}

	/** Mouse wheel over the list. */
	public void roda(double delta) {
		gulir -= (float) delta * 18.0F;
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
		gulir = frac * Math.max(0, isiTinggi - tinggiKotak);
		gulirTampil = gulir;
	}

	public void lepas() {
		seret = false;
	}
}
