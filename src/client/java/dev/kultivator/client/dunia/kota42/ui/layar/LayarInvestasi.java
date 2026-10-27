package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihJumlah;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Investasi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code investasi} (DESIGN 42.11.7) — stakes in townspeople's businesses, style kayu. Tabs on the single kit:
 * <ul>
 * <li>Portofolio — the total put in, the value now, this week's gain, the weekly faucet bar and the business count against
 * the limits; each stake with its share, deposit to value, the week's profit with a seven-week sparkline, the owner and a
 * red warning ("pemilik sakit, ahli waris ..."); buttons Tambah (number picker, steps of 10), Tarik (7-day notice), Batal
 * tarik, Lepas pengelola, Tunjuk;</li>
 * <li>Peluang — businesses to invest in: daily turnover estimate, margin, owner and temperament, the minimum and maximum
 * amount; "Tanam" with the picker; Tarik — the withdrawals that are running (the notice ends on a day) with "Batal tarik".</li>
 * </ul>
 */
public final class LayarInvestasi extends LayarKotaDasar {

	private final DaftarGulir dPorto = new DaftarGulir();
	private final DaftarGulir dPeluang = new DaftarGulir();
	private final DaftarGulir dTarik = new DaftarGulir();
	private final PemilihJumlah jumlah = new PemilihJumlah();
	private String idTerakhir = "";

	public LayarInvestasi(int npcId, CompoundTag lembar) {
		super(Investasi.LAYAR, npcId, lembar, KotaGambar.t("investasi.judul"), LembarKunci.GAYA_KAYU);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(Investasi.TAB_PORTOFOLIO, "kota.tab.portofolio", Glyphs.Atlas.GULUNGAN), tab(Investasi.TAB_PELUANG, "kota.tab.peluang", Glyphs.Atlas.BINTANG_PENUH),
				tab(Investasi.TAB_TARIK, "kota.tab.tarik", Glyphs.Atlas.PANAH_KANAN));
	}

	@Override
	protected void tabBerubah() {
		long kini = Util.getMillis();
		dPorto.mulaiUlang(kini);
		dPeluang.mulaiUlang(kini);
		dTarik.mulaiUlang(kini);
		idTerakhir = "";
	}

	@Override
	public String helpTopic() {
		return KotaLembar.TOPIK_EKONOMI;
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		int y = ringkas(l, x0, y0, x1);
		switch (tab) {
			case Investasi.TAB_PELUANG -> peluang(l, x0, y, x1, y1);
			case Investasi.TAB_TARIK -> tarik(l, x0, y, x1, y1);
			default -> portofolio(l, x0, y, x1, y1);
		}
	}

	private int ringkas(Lukis l, int x0, int y0, int x1) {
		Lembar r = lembar.sub(Investasi.RINGKAS);
		if (r.kosongSemua()) {
			return y0;
		}
		int y = y0;
		long ditanam = r.l(Investasi.DITANAM, 0L);
		long nilai = r.l(LembarKunci.NILAI, 0L);
		long minggu = r.l(Investasi.MINGGU_INI, 0L);
		IkonKota.gambar(l.g, IkonKota.INVESTASI, x0, y - 1, 14);
		l.teks(KotaGambar.t("investasi.total", Brush.angka(ditanam), Brush.angka(nilai)), x0 + 18, y, x1 - x0 - 18 - 70, l.gaya.teks);
		l.kanan(KotaGambar.t("investasi.minggu_ini", Brush.bertanda(minggu)), x1, y, minggu >= 0 ? l.gaya.naik() : l.gaya.turun());
		y += 14;
		long batas = r.l(KotaLembar.BATAS, 0L);
		if (batas > 0) {
			long pakai = r.l(KotaLembar.TERPAKAI, 0L);
			int lw = Math.min(120, (x1 - x0) / 2);
			l.teks(KotaGambar.t("investasi.batas", Brush.angka(pakai), Brush.angka(batas)), x0, y, lw, l.gaya.teksLemah);
			Bilah.gambar(l, x0 + lw + 4, y + 3, x1, pakai / (float) batas, pakai >= batas ? 0xFFD0603A : 0xFF5FA88C, null);
			y += 12;
		}
		l.teks(KotaGambar.t("investasi.usaha", r.i(Investasi.USAHA, 0), r.i(Investasi.USAHA_MAKS, 10), r.i(Investasi.PER_TEMPAT_MAKS, 3)), x0, y, x1 - x0, l.gaya.teksSamar);
		y += 12;
		l.garis(x0, x1, y);
		return y + 5;
	}

	private int[] grafik(Lembar e) {
		return e.ia(KotaLembar.GRAFIK);
	}

	// ------------------------------------------------------------------ portfolio

	private void portofolio(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(LembarKunci.DAFTAR);
		if (rows.isEmpty()) {
			kosong(l, KotaGambar.t("investasi.kosong"), x0, y0, x1, y1);
			return;
		}
		int bawah = y1 - 34;
		dPorto.gambar(l, "porto", x0, y0, x1, bawah, rows.size(), 40, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			IkonKota.gambar(lk.g, IkonKota.USAHA, rx0 + 4, ry0 + 12, 16);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 26, ry0 + 3, rx1 - rx0 - 110, lk.gaya.teks);
			Component tempat = e.teksAtau(LembarKunci.TEMPAT);
			if (tempat != null) {
				lk.teks(tempat, rx0 + 26, ry0 + 14, rx1 - rx0 - 110, lk.gaya.teksSamar);
			}
			lk.kanan(KotaGambar.t("investasi.saham", Math.round(e.f(Investasi.SAHAM, 0.0F) * 100.0F)), rx1 - 5, ry0 + 3, KotaGambar.EMAS);
			long laba = e.l(Investasi.LABA_MINGGU, 0L);
			lk.kanan(KotaGambar.t("investasi.setor_nilai", Brush.angka(e.l(Investasi.SETOR, 0L)), Brush.angka(e.l(LembarKunci.NILAI, 0L))), rx1 - 5, ry0 + 14,
					lk.gaya.teksLemah);
			Component warn = e.teksAtau(Investasi.PERINGATAN);
			int baris3 = ry0 + 26;
			if (warn != null) {
				IkonKota.gambar(lk.g, IkonKota.WASPADA, rx0 + 26, baris3 - 1, 10);
				lk.teks(warn, rx0 + 38, baris3, rx1 - rx0 - 110, Palette.BAHAYA);
			} else if (e.l(Investasi.TARIK_HARI, 0L) > 0) {
				lk.teks(KotaGambar.t("investasi.tarik_sampai", e.l(Investasi.TARIK_HARI, 0L)), rx0 + 26, baris3, rx1 - rx0 - 110, Palette.PERINGATAN);
			} else if (e.ada(Investasi.PENGELOLA)) {
				lk.teks(KotaGambar.t("investasi.pengelola", e.teks(Investasi.PENGELOLA)), rx0 + 26, baris3, rx1 - rx0 - 110, lk.gaya.teksLemah);
			}
			KotaGambar.garis(lk, grafik(e), rx1 - 90, ry0 + 27, rx1 - 38, ry1 - 4, laba >= 0 ? 0xFF2E7A4E : 0xFFB03030);
			lk.kanan(Component.literal(Brush.bertanda(laba)), rx1 - 5, ry0 + 26, laba >= 0 ? lk.gaya.naik() : lk.gaya.turun());
		}, null, null);
		int sel = dPorto.pilihan();
		if (sel < 0 || sel >= rows.size()) {
			return;
		}
		Lembar e = rows.get(sel);
		String id = e.id();
		int by = y1 - 30;
		int bx = x0;
		int pw = jumlah.min(10).gambar(l, "tambah_n", bx, by, 5000);
		Component tambah = KotaGambar.t("investasi.tambah");
		int w = lebarTombol(l, tambah, 54, 100);
		tulis(l, "tambah", bx + pw + 4, by, w, 14, tambah, true, null, LembarKunci.op(Investasi.OP_TANAM, id, jumlah.nilai()));
		by += 16;
		bx = x0;
		boolean menarik = e.l(Investasi.TARIK_HARI, 0L) > 0;
		Component tarik = KotaGambar.t(menarik ? "investasi.batal_tarik" : "investasi.tarik");
		w = lebarTombol(l, tarik, 60, 130);
		tulis(l, "tarik", bx, by, w, 14, tarik, true, null, LembarKunci.op(menarik ? Investasi.OP_BATAL_TARIK : Investasi.OP_TARIK, id));
		bx += w + 3;
		if (e.ada(Investasi.PENGELOLA)) {
			Component lepas = KotaGambar.t("investasi.lepas_pengelola");
			w = lebarTombol(l, lepas, 70, 150);
			tulis(l, "lepas", bx, by, w, 14, lepas, true, null, LembarKunci.op(Investasi.OP_LEPAS_PENGELOLA, id));
			bx += w + 3;
		}
		if (bx + 48 < x1) {
			tunjuk(l, "tunjuk", bx, by, 48, 14, 0, 0);
		}
	}

	// ------------------------------------------------------------------ opportunities

	private void peluang(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Investasi.PELUANG);
		if (rows.isEmpty()) {
			kosong(l, KotaGambar.t("investasi.peluang_kosong"), x0, y0, x1, y1);
			return;
		}
		int bawah = y1 - 18;
		dPeluang.gambar(l, "peluang", x0, y0, x1, bawah, rows.size(), 40, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			IkonKota.gambar(lk.g, IkonKota.PELUANG, rx0 + 4, ry0 + 12, 16);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 26, ry0 + 3, rx1 - rx0 - 110, lk.gaya.teks);
			Component tempat = e.teksAtau(LembarKunci.TEMPAT);
			lk.teks(tempat == null ? Component.empty() : tempat, rx0 + 26, ry0 + 14, rx1 - rx0 - 110, lk.gaya.teksSamar);
			lk.kanan(KotaGambar.t("investasi.margin", e.i(Investasi.MARGIN, 0)), rx1 - 5, ry0 + 3, lk.gaya.naik());
			lk.kanan(KotaGambar.t("investasi.omzet", Brush.angka(e.l(Investasi.OMZET, 0L))), rx1 - 5, ry0 + 14, lk.gaya.teksLemah);
			Component pemilik = e.teksAtau(Investasi.PEMILIK);
			Component watak = e.teksAtau(KotaLembar.WATAK_NAMA);
			if (pemilik != null) {
				lk.teks(watak == null ? pemilik : KotaGambar.t("investasi.pemilik_watak", pemilik, watak), rx0 + 26, ry0 + 26, rx1 - rx0 - 120, lk.gaya.teksLemah);
			}
			LabelHarga.gambar(lk, e.l(Investasi.MODAL, 0L), -1L, rx1 - 5, ry0 + 26, true, false);
			if (!e.b(LembarKunci.BISA) && e.ada(LembarKunci.ALASAN)) {
				IkonKota.gambar(lk.g, IkonKota.WASPADA, rx0 + 4, ry1 - 12, 10);
			}
		}, null, i -> {
			String id = rows.get(i).id();
			if (!id.equals(idTerakhir)) {
				idTerakhir = id;
				jumlah.min(10).set((int) Math.max(10L, rows.get(i).l(Investasi.MIN, 50L)));
			}
		});
		int sel = dPeluang.pilihan();
		if (sel < 0 || sel >= rows.size()) {
			return;
		}
		Lembar e = rows.get(sel);
		int by = y1 - 16;
		int maks = (int) Math.min(100000L, Math.max(10L, e.l(Investasi.MAKS, 1000L)));
		int pw = jumlah.min((int) Math.min(maks, Math.max(1L, e.l(Investasi.MIN, 10L)))).gambar(l, "tanam_n", x0, by, maks);
		Component tanam = KotaGambar.t("investasi.tanam");
		int w = lebarTombol(l, tanam, 54, 100);
		tulis(l, "tanam", x0 + pw + 4, by, w, 14, tanam, e.b(LembarKunci.BISA), e.teksAtau(LembarKunci.ALASAN),
				LembarKunci.op(Investasi.OP_TANAM, e.id(), jumlah.nilai()));
		tunjuk(l, "tunjuk", x0 + pw + w + 8, by, 48, 14, e.i(LembarKunci.X, 0), e.i(LembarKunci.Z, 0));
	}

	// ------------------------------------------------------------------ withdrawals

	private void tarik(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> semua = lembar.daftar(LembarKunci.DAFTAR);
		List<Lembar> rows = semua.stream().filter(e -> e.l(Investasi.TARIK_HARI, 0L) > 0).toList();
		if (rows.isEmpty()) {
			kosong(l, KotaGambar.t("investasi.tarik_kosong"), x0, y0, x1, y1);
			return;
		}
		dTarik.gambar(l, "tarik", x0, y0, x1, y1, rows.size(), 32, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			IkonKota.gambar(lk.g, IkonKota.TARIK, rx0 + 4, ry0 + 8, 16);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 26, ry0 + 3, rx1 - rx0 - 130, lk.gaya.teks);
			lk.teks(KotaGambar.t("investasi.tarik_sampai", e.l(Investasi.TARIK_HARI, 0L)), rx0 + 26, ry0 + 15, rx1 - rx0 - 130, Palette.PERINGATAN);
			LabelHarga.gambar(lk, e.l(LembarKunci.NILAI, 0L), -1L, rx1 - 84, ry0 + 4, true, false);
			tulis(lk, "batal#" + i, rx1 - 80, ry0 + (ry1 - ry0 - 14) / 2, 76, 14, KotaGambar.t("investasi.batal_tarik"), true, null,
					LembarKunci.op(Investasi.OP_BATAL_TARIK, e.id()));
		}, null, null);
	}
}
