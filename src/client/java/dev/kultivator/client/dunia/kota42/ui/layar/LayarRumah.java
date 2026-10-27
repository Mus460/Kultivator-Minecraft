package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Rumah;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code rumah} (DESIGN 42.11.5) — the land office: houses for sale, for rent, the player's own and the household help, style
 * kain. Tabs on the single kit:
 * <ul>
 * <li>Dijual / Sewa — the houses of the town (list) beside the chosen one: its floor plan painted from the server's colour
 * grid, price with the town and prosperity factors, rent and tax per week, the neighbours with the bond heart, the
 * requirements ticked or crossed, and the buttons Beli / Sewa 1 minggu / Sewa 4 minggu / Tunjuk;</li>
 * <li>Milikku — each owned house with its status seal (own, rented, sealed), rent paid until, unpaid tax weeks; the buttons
 * Bayar pajak, Perpanjang, Cetak ulang kunci (5 NB), Jual (a medium confirm: the server asks twice), Tunjuk;</li>
 * <li>Pembantu — household help: employed people with their wage and "Berhentikan", unemployed candidates with "Pekerjakan".</li>
 * </ul>
 */
public final class LayarRumah extends LayarKotaDasar {

	private final DaftarGulir dJual = new DaftarGulir();
	private final DaftarGulir dSewa = new DaftarGulir();
	private final DaftarGulir dMilik = new DaftarGulir();
	private final DaftarGulir dPembantu = new DaftarGulir();
	private final GulirIsi gulirRinci = new GulirIsi();
	private String idKirim = "";

	public LayarRumah(int npcId, CompoundTag lembar) {
		super(Rumah.LAYAR, npcId, lembar, KotaGambar.t("rumah.judul"), LembarKunci.GAYA_KAIN);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(Rumah.TAB_DIJUAL, "kota.tab.dijual", Glyphs.Atlas.TONGGAK), tab(Rumah.TAB_SEWA, "kota.tab.sewa", Glyphs.Atlas.KUNCI),
				tab(Rumah.TAB_MILIKKU, "kota.tab.rumah_milikku", Glyphs.Atlas.GULUNGAN), tab(Rumah.TAB_PEMBANTU, "kota.tab.pembantu", Glyphs.Atlas.HATI));
	}

	@Override
	protected void tabBerubah() {
		long kini = Util.getMillis();
		dJual.mulaiUlang(kini);
		dSewa.mulaiUlang(kini);
		dMilik.mulaiUlang(kini);
		dPembantu.mulaiUlang(kini);
		gulirRinci.atas();
		idKirim = "";
	}

	@Override
	public String helpTopic() {
		return KotaLembar.TOPIK_EKONOMI;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return gulirRinci.roda(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	protected boolean tombolLain(KeyEvent event) {
		return false;
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		int y = KotaGambar.kota(l, lembar, x0, y0, x1);
		if (lembar.ada(Rumah.RUMAH_MAKS)) {
			l.kanan(KotaGambar.t("rumah.jumlah", lembar.i(Rumah.RUMAH_JUMLAH, 0), lembar.i(Rumah.RUMAH_MAKS, 0)), x1, y0, l.gaya.teksSamar);
		}
		switch (tab) {
			case Rumah.TAB_MILIKKU -> milik(l, x0, y, x1, y1);
			case Rumah.TAB_PEMBANTU -> pembantu(l, x0, y, x1, y1);
			case Rumah.TAB_SEWA -> toko(l, x0, y, x1, y1, true);
			default -> toko(l, x0, y, x1, y1, false);
		}
	}

	// ------------------------------------------------------------------ for sale / for rent

	private void toko(Lukis l, int x0, int y0, int x1, int y1, boolean sewa) {
		List<Lembar> rows = lembar.daftar(LembarKunci.DAFTAR);
		if (rows.isEmpty()) {
			kosong(l, KotaGambar.t(sewa ? "rumah.sewa_kosong" : "rumah.jual_kosong"), x0, y0, x1, y1);
			return;
		}
		DaftarGulir dg = sewa ? dSewa : dJual;
		Kotak[] b = bagi(x0, y0, x1, y1);
		Kotak kiri = b[0];
		dg.gambar(l, sewa ? "sewa" : "jual", kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1(), rows.size(), 38, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			IkonKota.gambar(lk.g, IkonKota.rumah(e.s(LembarKunci.JENIS)), rx0 + 4, ry0 + 10, 16);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 26, ry0 + 3, rx1 - rx0 - 32, lk.gaya.teks);
			lk.teks(KotaGambar.t("rumah.ukuran", e.i(Rumah.KASUR, 0), e.i(KotaLembar.LEBAR, 0), e.i(KotaLembar.PANJANG, 0)), rx0 + 26, ry0 + 14, rx1 - rx0 - 32,
					lk.gaya.teksLemah);
			long harga = sewa ? e.l(Rumah.SEWA, 0L) : e.l(LembarKunci.HARGA, 0L);
			lk.teks(KotaGambar.t(sewa ? "rumah.sewa_per" : "rumah.harga_n", Brush.angka(harga)), rx0 + 26, ry0 + 25, rx1 - rx0 - 32, KotaGambar.EMAS);
			Component alasan = e.teksAtau(LembarKunci.ALASAN);
			if (alasan != null) {
				lk.kanan(alasan, rx1 - 4, ry0 + 25, Palette.BAHAYA);
			}
		}, null, i -> {
			String id = rows.get(i).id();
			if (!id.equals(idKirim)) {
				idKirim = id;
				pilihBaris(tab, id);
			}
		});
		Kotak kanan = b[1];
		l.sumur(kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1());
		Lembar r = lembar.sub(KotaLembar.RINCI);
		int sel = dg.pilihan();
		Lembar baris = sel >= 0 && sel < rows.size() ? rows.get(sel) : null;
		if (r.kosongSemua() || baris == null || !r.id().equals(baris.id())) {
			l.tengah(KotaGambar.t("rumah.pilih_dulu"), (kanan.x0() + kanan.x1()) / 2.0F, kanan.y0() + kanan.h() / 2.0F - 4, kanan.w() - 8, l.gaya.teksSumur());
			return;
		}
		rinci(l, baris, r, sewa, kanan);
	}

	private void rinci(Lukis l, Lembar baris, Lembar r, boolean sewa, Kotak k) {
		int xr = k.x1() - 5;
		int bawahTombol = k.y1() - 18;
		int y = gulirRinci.mulai(l, k.x0() + 4, k.y0() + 3, xr, bawahTombol);
		int x0 = k.x0() + 5;
		int ink = l.gaya.teksSumur();
		l.teks(Brush.bold(baris.teks(LembarKunci.NAMA)), x0, y, xr - x0, ink);
		y += 12;
		// the floor plan, then the figures beside it when there is room
		int denahW = Math.min(xr - x0, Math.max(40, Math.min(84, (xr - x0) * 2 / 5)));
		int tinggiDenah = denah(l, r.sub(KotaLembar.DENAH), x0, y, denahW, 60);
		int fx = x0 + denahW + 6;
		int fy = y;
		if (xr - fx > 60) {
			fy += angka(l, KotaGambar.t("rumah.harga"), KotaGambar.nb(baris.l(LembarKunci.HARGA, 0L)), fx, fy, xr, ink);
			fy += angka(l, KotaGambar.t("rumah.sewa"), KotaGambar.t("rumah.per_minggu", Brush.angka(baris.l(Rumah.SEWA, 0L))), fx, fy, xr, ink);
			fy += angka(l, KotaGambar.t("rumah.pajak"), KotaGambar.t("rumah.per_minggu", Brush.angka(baris.l(Rumah.PAJAK, 0L))), fx, fy, xr, ink);
		} else {
			y += tinggiDenah + 3;
			fx = x0;
			fy = y;
			fy += angka(l, KotaGambar.t("rumah.harga"), KotaGambar.nb(baris.l(LembarKunci.HARGA, 0L)), fx, fy, xr, ink);
			fy += angka(l, KotaGambar.t("rumah.sewa"), KotaGambar.t("rumah.per_minggu", Brush.angka(baris.l(Rumah.SEWA, 0L))), fx, fy, xr, ink);
			tinggiDenah = 0;
		}
		fy += angka(l, KotaGambar.t("rumah.faktor"), KotaGambar.t("rumah.faktor_n", f2(r.f(Rumah.FAKTOR_KOTA, 1.0F)), f2(r.f(Rumah.FAKTOR_MAKMUR, 1.0F)),
				f2(r.f(Rumah.FAKTOR_HARGA, 1.0F))), fx, fy, xr, ink);
		y = Math.max(y + tinggiDenah, fy) + 3;
		List<Lembar> tetangga = r.daftar(Rumah.TETANGGA);
		if (!tetangga.isEmpty()) {
			l.teks(Brush.bold(KotaGambar.t("rumah.tetangga")), x0, y, xr - x0, ink);
			y += 11;
			for (Lembar t : tetangga) {
				KotaGambar.hati(l, x0, y, 8, KotaGambar.warnaHati(t.s(KotaLembar.AKRAB)));
				l.teks(t.teks(LembarKunci.NAMA), x0 + 11, y, xr - x0 - 11, ink);
				y += 11;
			}
		}
		for (Lembar s : r.daftar(LembarKunci.SYARAT)) {
			y += KotaGambar.syarat(l, s, x0, y, xr, Integer.MAX_VALUE / 2);
		}
		gulirRinci.selesai(l, y + 2);
		// buttons
		int by = k.y1() - 16;
		int bx = k.x0() + 4;
		String id = baris.id();
		boolean bisa = baris.b(LembarKunci.BISA) || baris.teksAtau(LembarKunci.ALASAN) == null;
		Component alasan = baris.teksAtau(LembarKunci.ALASAN);
		if (!sewa) {
			Component beli = KotaGambar.t("rumah.beli", Brush.angka(baris.l(LembarKunci.HARGA, 0L)));
			int w = lebarTombol(l, beli, 60, 110);
			tulis(l, "beli", bx, by, w, 14, beli, bisa, alasan, LembarKunci.op(Rumah.OP_BELI, id));
			bx += w + 3;
		} else {
			for (int minggu : new int[] {1, 4}) {
				Component label = KotaGambar.t("rumah.sewa_n", minggu);
				int w = lebarTombol(l, label, 54, 90);
				tulis(l, "sewa#" + minggu, bx, by, w, 14, label, bisa, alasan, LembarKunci.op(Rumah.OP_SEWA, id, minggu));
				bx += w + 3;
			}
		}
		tunjuk(l, "tunjuk", bx, by, 48, 14, baris.i(LembarKunci.X, 0), baris.i(LembarKunci.Z, 0));
	}

	private static String f2(float v) {
		return String.format(java.util.Locale.ROOT, "%.2f", v);
	}

	private int angka(Lukis l, Component label, Component nilai, int x0, int y, int x1, int ink) {
		int vw = l.font.width(nilai);
		l.teks(label, x0, y, Math.max(8, x1 - x0 - vw - 6), Palette.INK_FAINT);
		l.kanan(nilai, x1, y, ink);
		return 11;
	}

	/** Paints the server's colour grid as a floor plan in a box of at most {@code maksH} px; returns the height used. */
	private int denah(Lukis l, Lembar d, int x, int y, int lebar, int maksH) {
		int w = d.i(KotaLembar.LEBAR, 0);
		int p = d.i(KotaLembar.PANJANG, 0);
		int[] warna = d.ia(KotaLembar.WARNA);
		if (w <= 0 || p <= 0 || warna.length < w * p) {
			return 0;
		}
		int sel = Math.max(1, Math.min(lebar / w, maksH / p));
		int tw = sel * w;
		int th = sel * p;
		int ox = x + (lebar - tw) / 2;
		l.g.fill(ox - 2, y - 2, ox + tw + 2, y + th + 2, Palette.argb(0x30, 0x000000));
		Brush.frame(l.g, ox - 2, y - 2, ox + tw + 2, y + th + 2, Palette.argb(0x90, Palette.GOLD_DARK));
		for (int j = 0; j < p; j++) {
			for (int i = 0; i < w; i++) {
				int c = warna[j * w + i];
				if ((c >>> 24) != 0) {
					l.g.fill(ox + i * sel, y + j * sel, ox + (i + 1) * sel, y + (j + 1) * sel, c | 0xFF000000);
				}
			}
		}
		if (l.di(ox, y, ox + tw, y + th)) {
			l.tip(KotaGambar.t("rumah.denah_tip", w, p));
		}
		return th + 4;
	}

	// ------------------------------------------------------------------ owned houses

	private void milik(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Rumah.MILIK);
		if (rows.isEmpty()) {
			kosong(l, KotaGambar.t("rumah.milik_kosong"), x0, y0, x1, y1);
			return;
		}
		int bawah = y1 - 18;
		dMilik.gambar(l, "milik", x0, y0, x1, bawah, rows.size(), 38, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			String status = e.s(LembarKunci.STATUS);
			IkonKota.gambar(lk.g, IkonKota.RUMAH_KECIL + 1, rx0 + 4, ry0 + 10, 16);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 26, ry0 + 3, rx1 - rx0 - 110, lk.gaya.teks);
			int warna = Rumah.RS_SEGEL.equals(status) ? Palette.BAHAYA : Rumah.RS_SEWA.equals(status) ? Palette.PERINGATAN : 0xFF5FB86A;
			lk.kanan(KotaGambar.t("rumah.status." + status), rx1 - 5, ry0 + 3, warna);
			Component sampai = e.l(KotaLembar.SAMPAI, 0L) > 0 ? KotaGambar.t(Rumah.RS_SEGEL.equals(status) ? "rumah.segel_sampai" : "rumah.sewa_sampai", e.l(KotaLembar.SAMPAI, 0L))
					: KotaGambar.t("rumah.milik_sendiri");
			lk.teks(sampai, rx0 + 26, ry0 + 14, rx1 - rx0 - 32, lk.gaya.teksLemah);
			int tunggak = e.i(Rumah.TUNGGAK, 0);
			if (tunggak > 0) {
				IkonKota.gambar(lk.g, IkonKota.WASPADA, rx0 + 26, ry0 + 25, 10);
				lk.teks(KotaGambar.t("rumah.tunggak", tunggak), rx0 + 38, ry0 + 26, rx1 - rx0 - 44, Palette.BAHAYA);
			} else if (e.ada(Rumah.CABANG)) {
				lk.teks(KotaGambar.t("rumah.cabang", e.teks(Rumah.CABANG)), rx0 + 26, ry0 + 26, rx1 - rx0 - 32, lk.gaya.teksSamar);
			}
		}, null, null);
		int sel = dMilik.pilihan();
		if (sel < 0 || sel >= rows.size()) {
			return;
		}
		Lembar e = rows.get(sel);
		String id = e.id();
		String status = e.s(LembarKunci.STATUS);
		int by = y1 - 16;
		int bx = x0;
		if (e.i(Rumah.TUNGGAK, 0) > 0) {
			Component c = KotaGambar.t("rumah.bayar_pajak");
			int w = lebarTombol(l, c, 60, 100);
			tulis(l, "pajak", bx, by, w, 14, c, true, null, LembarKunci.op(Rumah.OP_BAYAR_PAJAK, id));
			bx += w + 3;
		}
		if (Rumah.RS_SEWA.equals(status)) {
			Component c = KotaGambar.t("rumah.perpanjang");
			int w = lebarTombol(l, c, 60, 100);
			tulis(l, "perpanjang", bx, by, w, 14, c, true, null, LembarKunci.op(Rumah.OP_PERPANJANG, id, 1));
			bx += w + 3;
		}
		Component kunci = KotaGambar.t("rumah.kunci");
		int kw = lebarTombol(l, kunci, 60, 110);
		tulis(l, "kunci", bx, by, kw, 14, kunci, true, null, LembarKunci.op(Rumah.OP_KUNCI, id));
		bx += kw + 3;
		if (Rumah.RS_MILIK.equals(status)) {
			Component jual = KotaGambar.t("rumah.jual", Brush.angka(e.l(Rumah.NILAI_JUAL, 0L)));
			int w = lebarTombol(l, jual, 60, 120);
			boolean toko = e.b(Rumah.TOKO);
			boolean ok = !toko && tulisBoleh();
			Component alasan = toko ? KotaGambar.t("rumah.jual_toko") : null;
			aksi(l, "jual", bx, by, w, 14, jual, ok, alasan, () -> konfirmasi(SegelKonfirmasi.Tingkat.SEDANG,
					KotaGambar.t("rumah.jual_yakin", e.teks(LembarKunci.NAMA), Brush.angka(e.l(Rumah.NILAI_JUAL, 0L))), () -> {
						kirim(LembarKunci.op(Rumah.OP_JUAL, id));
						kirim(LembarKunci.op(Rumah.OP_JUAL_YA, id));
					}));
			bx += w + 3;
		}
		if (bx + 48 < x1) {
			tunjuk(l, "tunjuk", bx, by, 48, 14, e.i(LembarKunci.X, 0), e.i(LembarKunci.Z, 0));
		}
	}

	// ------------------------------------------------------------------ household help

	private void pembantu(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Rumah.PEMBANTU);
		if (rows.isEmpty()) {
			kosong(l, KotaGambar.t("rumah.pembantu_kosong"), x0, y0, x1, y1);
			return;
		}
		dPembantu.gambar(l, "pembantu", x0, y0, x1, y1, rows.size(), 40, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			boolean kerja = e.b(Rumah.DIPEKERJAKAN);
			KotaGambar.warga(lk, e, rx0, ry0, rx1, ry1, sorot, pilih, 92);
			lk.teks(kerja ? KotaGambar.t("rumah.gaji_n", Brush.angka(e.l(Rumah.GAJI, 0L))) : KotaGambar.t("rumah.calon"), rx1 - 92, ry0 + 4, 88, kerja ? KotaGambar.EMAS : lk.gaya.teksLemah);
			Component watak = e.teksAtau(KotaLembar.WATAK_NAMA);
			if (watak != null) {
				lk.teks(KotaGambar.t("rumah.watak_ahli", watak, e.i(KotaLembar.AHLI, 0)), rx1 - 92, ry0 + 16, 88, lk.gaya.teksSamar);
			}
			int w = 72;
			int hh = 14;
			String warga = Integer.toString(e.i(KotaLembar.WARGA, 0));
			String rumahId = e.s(Rumah.RUMAH);
			String op = kerja ? LembarKunci.op(Rumah.OP_BERHENTIKAN, warga) : LembarKunci.op(Rumah.OP_PEKERJAKAN, warga, rumahId);
			tulis(lk, "kerja#" + i, rx1 - 92, ry1 - hh - 3, w, hh, KotaGambar.t(kerja ? "rumah.berhentikan" : "rumah.pekerjakan"), true, null, op);
		}, null, null);
	}
}
