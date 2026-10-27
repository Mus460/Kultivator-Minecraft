package dev.kultivator.client.screen.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The single people picker of the kit (DESIGN 43.7; replaces {@code sectevent/RosterView}, the worker picker of Kebun,
 * the promote picker of BalaiTetua, the cycling picker of TreasuryPage and the worker chips of BuildPage; the candidate
 * cards of {@code hall/CalonPage} stay a card owned by that one page, {@code hall/CandidateCard}). One row per person: a small {@link Potret} (or a seal in the person's colour), the name,
 * extra columns (realm, task...) and a short tag on the right; a person that cannot be chosen now is greyed with the
 * reason as tooltip. Two uses:
 * <ul>
 * <li><b>list</b> ({@code maks} 0): click selects, a second click or Enter opens the person ({@code onBuka});</li>
 * <li><b>pick</b> ({@code maks} ≥ 1): the box at the left of each row ticks / unticks (Enter / second click too); with
 * {@code maks} 1 a new tick replaces the old one; a "›" seal on the right opens the person when {@code onBuka} is set.</li>
 * </ul>
 * Either can be drawn in place ({@link #gambar}) or as a pop-up card over the page ({@link #gambarSembul}: the page dims,
 * a click outside or Esc through the page's {@code mundur} closes it). An optional {@link Saring} row filters by name and
 * kind. The selection is kept by id, so it survives new data. Text is 1.0.
 */
public final class PemilihOrang {

	/**
	 * One person: id (soul / uuid, stable), name, seal colour (RGB, the root colour), live entity id for the portrait (-1 =
	 * seal only), extra column values, tag on the right (null = none), lock reason (null = can be chosen), kind for the
	 * filter chips (null = none).
	 */
	public record Orang(String id, Component nama, int warna, int entityId, List<Component> kolom, @Nullable Component tanda,
			@Nullable Component kunci, @Nullable String jenis) {

		public static Orang of(String id, Component nama, int warna) {
			return new Orang(id, nama, warna, -1, List.of(), null, null, null);
		}
	}

	private static final int BARIS = 16;
	private static final int KOTAK = 9;

	private final int maks;
	private final Set<String> terpilih = new LinkedHashSet<>();
	private final DaftarGulir daftar = new DaftarGulir();
	private @Nullable Saring saring;
	private List<Component> judulKolom = List.of();
	private @Nullable Consumer<Orang> onBuka;
	private @Nullable Runnable onUbah;
	private boolean sembul;
	private List<Orang> tampil = List.of();

	/** {@code maks} 0 = list mode (no ticks), 1 = single choice, more = multiple choice up to that many. */
	public PemilihOrang(int maks) {
		this.maks = Math.max(0, maks);
	}

	/** Adds the filter row (search + kind chips) above the list. */
	public PemilihOrang saring(Saring saring) {
		this.saring = saring;
		return this;
	}

	/** Headings of the extra columns (the values come with each {@link Orang#kolom}). */
	public PemilihOrang kolom(List<Component> judul) {
		this.judulKolom = judul;
		return this;
	}

	/** What opening a person does (list mode: second click / Enter; pick mode: the "›" seal). */
	public PemilihOrang onBuka(@Nullable Consumer<Orang> onBuka) {
		this.onBuka = onBuka;
		return this;
	}

	/** Runs whenever the ticks change. */
	public PemilihOrang onUbah(@Nullable Runnable onUbah) {
		this.onUbah = onUbah;
		return this;
	}

	/** The ticked ids in tick order (the caller may also seed it). */
	public Set<String> terpilih() {
		return terpilih;
	}

	/** The ticked people that are still in {@code semua} and allowed, in list order. */
	public List<Orang> pilihan(List<Orang> semua) {
		List<Orang> out = new ArrayList<>();
		for (Orang o : semua) {
			if (o.kunci() == null && terpilih.contains(o.id())) {
				out.add(o);
			}
		}
		return out;
	}

	/** The highlighted person of the last frame (null when none). */
	public @Nullable Orang sorotan() {
		int i = daftar.pilihan();
		return i < 0 || i >= tampil.size() ? null : tampil.get(i);
	}

	public void bukaSembul() {
		sembul = true;
	}

	public boolean terbuka() {
		return sembul;
	}

	/** Closes the pop-up; true when it was open (a page's {@code mundur} returns this). */
	public boolean tutup() {
		boolean was = sembul;
		sembul = false;
		return was;
	}

	private void centang(Orang o) {
		if (o.kunci() != null) {
			return;
		}
		if (terpilih.remove(o.id())) {
			UiSound.toggle(false);
		} else {
			if (maks == 1) {
				terpilih.clear();
			} else if (terpilih.size() >= maks) {
				UiSound.denied();
				return;
			}
			terpilih.add(o.id());
			UiSound.toggle(true);
		}
		if (onUbah != null) {
			onUbah.run();
		}
	}

	/** Draws the picker in the box; returns nothing (it fills the box). */
	public void gambar(Lukis l, String kunci, List<Orang> semua, int x0, int y0, int x1, int y1) {
		int y = y0;
		List<Orang> rows = new ArrayList<>(semua.size());
		if (saring != null) {
			y += saring.gambar(l, kunci + ":saring", x0, y, x1) + 3;
			for (Orang o : semua) {
				if (saring.cocok(o.nama(), o.jenis())) {
					rows.add(o);
				}
			}
		} else {
			rows.addAll(semua);
		}
		tampil = rows;
		int[] lebarKolom = lebarKolom(l, rows, x1 - x0);
		if (lebarKolom.length > 0 || maks > 1) {
			// header row: the tick count on the left, the column headings over their columns
			int cx = x1 - lebarTanda(l, rows) - (onBuka != null && maks > 0 ? 14 : 0) - 10;
			for (int c = lebarKolom.length - 1; c >= 0; c--) {
				cx -= lebarKolom[c];
				l.samar(judulKolom.get(c), cx, y, lebarKolom[c] - 3);
			}
			if (maks > 1) {
				l.samar(Component.translatable("kultivator.gui43.kit.orang.terpilih", pilihan(semua).size(), maks), x0, y, cx - x0 - 4);
			}
			y += 10;
		}
		if (rows.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable("kultivator.gui43.kit.orang.kosong"), null, null, x0, y, x1, y1);
			return;
		}
		int tandaW = lebarTanda(l, rows);
		daftar.gambar(l, kunci, x0, y, x1, y1, rows.size(), BARIS, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> baris(lk, kunci, rows.get(i), rx0,
				ry0, rx1, ry1, sorot, pilih, lebarKolom, tandaW), () -> {
					Orang o = sorotan();
					if (o == null) {
						return;
					}
					if (maks > 0) {
						centang(o);
					} else if (onBuka != null) {
						UiSound.press();
						onBuka.accept(o);
					}
				}, null);
	}

	/** Draws the picker as a pop-up card titled {@code judul} over the page box (only while {@link #terbuka()}). */
	public void gambarSembul(Lukis l, String kunci, Component judul, List<Orang> semua) {
		if (!sembul) {
			return;
		}
		l.g.fill(l.kx0, l.ky0, l.kx1, l.ky1, 0x90101418);
		// a click anywhere outside the card closes it (registered before the card, so the card's zones win)
		l.zona(kunci + "#luar", l.kx0, l.ky0, l.kx1, l.ky1, this::tutup, true, null, false);
		int w = Math.min(300, l.kx1 - l.kx0 - 16);
		int h = Math.min(220, l.ky1 - l.ky0 - 16);
		int x0 = (l.kx0 + l.kx1 - w) / 2;
		int y0 = (l.ky0 + l.ky1 - h) / 2;
		l.g.fill(x0 + 3, y0 + 3, x0 + w + 3, y0 + h + 3, 0x60000000);
		l.g.fillGradient(x0, y0, x0 + w, y0 + h, Palette.PAPER_TOP, Palette.PAPER_BOTTOM);
		Brush.frame(l.g, x0, y0, x0 + w, y0 + h, Palette.GOLD);
		l.zona(kunci + "#kartu", x0, y0, x0 + w, y0 + h, null, true, null, false);
		Palette.Gaya lama = l.gaya;
		l.gaya = Palette.Gaya.KERTAS;
		int kepala = l.kepala(judul, x0 + 6, y0 + 5, x0 + w - 26);
		TalismanButton.gambar(l, kunci + ":tutup", x0 + w - 22, y0 + 3, 16, 12, Component.literal("×"), true, null, this::tutup);
		gambar(l, kunci, semua, x0 + 6, y0 + 6 + kepala, x0 + w - 6, y0 + h - 6);
		l.gaya = lama;
	}

	private void baris(Lukis l, String kunci, Orang o, int x0, int y0, int x1, int y1, boolean sorot, boolean pilih, int[] lebarKolom, int tandaW) {
		boolean bisa = o.kunci() == null;
		l.kartu(x0, y0, x1, y1, sorot ? 1.0F : 0.0F, pilih);
		int x = x0 + 3;
		int cy = (y0 + y1) / 2;
		if (maks > 0) {
			boolean on = terpilih.contains(o.id());
			int bx = x;
			int by = cy - KOTAK / 2;
			l.g.fill(bx, by, bx + KOTAK, by + KOTAK, bisa ? 0xFFF6EED2 : Palette.LOCKED_PAPER);
			Brush.frame(l.g, bx, by, bx + KOTAK, by + KOTAK, bisa ? Palette.JADE_DARK : Palette.LOCKED);
			if (on) {
				Brush.line(l.g, bx + 1.5F, by + 4.5F, bx + 3.8F, by + 7.5F, 1.4F, Palette.JADE);
				Brush.line(l.g, bx + 3.8F, by + 7.5F, bx + 7.8F, by + 1.5F, 1.4F, Palette.JADE);
			}
			// click box at least the kit's 16 px wide (43.7)
			l.zona(kunci + "#centang:" + o.id(), x0, y0, Math.max(bx + KOTAK + 2, x0 + TalismanButton.MIN_W), y1, () -> centang(o), bisa, o.kunci(),
					false);
			x += KOTAK + 4;
		}
		int ps = y1 - y0 - 2;
		if (o.entityId() >= 0 && ps >= 12) {
			Potret.gambar(l, x, y0 + 1, ps, o.entityId(), -1, false, 0.0F);
		} else {
			Brush.disc(l.g, x + ps / 2.0F, cy, Math.min(5.0F, ps / 2.0F), bisa ? Palette.opaque(o.warna()) : Palette.LOCKED);
			Brush.ring(l.g, x + ps / 2.0F, cy, Math.min(5.0F, ps / 2.0F) - 1.0F, Math.min(5.0F, ps / 2.0F), Palette.argb(0xA0, 0x000000));
		}
		x += ps + 4;
		int kanan = x1 - 3;
		if (onBuka != null && maks > 0) {
			Consumer<Orang> buka = onBuka;
			TalismanButton.gambar(l, kunci + ".buka:" + o.id(), kanan - 12, y0 + 1, 12, y1 - y0 - 2, Component.literal("›"), true, null,
					() -> buka.accept(o));
			kanan -= 14;
		}
		if (o.tanda() != null && tandaW > 0) {
			l.kanan(o.tanda(), kanan, cy - 4, bisa ? l.gaya.teksLemah : Palette.terkunci(l.gaya.teksLemah));
		}
		kanan -= tandaW + (tandaW > 0 ? 4 : 0);
		for (int c = lebarKolom.length - 1; c >= 0; c--) {
			kanan -= lebarKolom[c];
			if (c < o.kolom().size()) {
				l.teks(o.kolom().get(c), kanan, cy - 4, lebarKolom[c] - 3, bisa ? l.gaya.teks : Palette.terkunci(l.gaya.teks));
			}
		}
		Component nama = maks > 0 && terpilih.contains(o.id()) ? Brush.bold(o.nama()) : o.nama();
		l.teks(nama, x, cy - 4, kanan - x - 3, bisa ? l.gaya.teks : Palette.terkunci(l.gaya.teks));
		if (!bisa && l.di(x0, y0, x1, y1)) {
			l.tip(List.of(o.nama(), o.kunci().copy().withColor(0xFFFF8A7A)));
		}
	}

	/** Widths of the extra columns that fit (narrow boxes drop columns from the right). */
	private int[] lebarKolom(Lukis l, List<Orang> rows, int lebar) {
		int n = judulKolom.size();
		int[] w = new int[n];
		int total = 0;
		int boleh = 0;
		for (int c = 0; c < n; c++) {
			int m = l.font.width(judulKolom.get(c));
			for (Orang o : rows) {
				if (c < o.kolom().size()) {
					m = Math.max(m, l.font.width(o.kolom().get(c)));
				}
			}
			w[c] = Math.min(80, m + 6);
			if (total + w[c] > lebar - 120) {
				break;
			}
			total += w[c];
			boleh++;
		}
		return java.util.Arrays.copyOf(w, boleh);
	}

	private static int lebarTanda(Lukis l, List<Orang> rows) {
		int m = 0;
		for (Orang o : rows) {
			if (o.tanda() != null) {
				m = Math.max(m, l.font.width(o.tanda()));
			}
		}
		return Math.min(70, m);
	}
}
