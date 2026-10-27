package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.BukuKas;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code buku_kas} (DESIGN 38.U.3.6): the sect ledger. PK and Batu (NB) are always two separate columns with their own
 * icons (38.M.0). "Hari ini": today's lines by source with notes, the net line and a 7-day sparkline of both currencies.
 * "7 hari": one row per day with twin bars. "Sumber": 7-day totals per source with caps. "Lapak": the 18 stall slots
 * with price hints (sells ±80 / 50 / 20 %), the guard disciple, uncollected cash, rent; prices step by 10 %. "Kafilah":
 * running caravans on a mini map with progress, and the dispatch planner (destination, up to three escorts, two loads).
 * From the hub the ledger only reads.
 */
public final class LayarBukuKas extends LayarInti {

	private final DaftarGulir daftarBaris = new DaftarGulir();
	private final DaftarGulir daftarSumber = new DaftarGulir();
	private final DaftarGulir daftarKafilah = new DaftarGulir();
	private final DaftarGulir daftarTujuan = new DaftarGulir();
	private final DaftarGulir daftarMurid = new DaftarGulir();
	private final DaftarGulir daftarMuatan = new DaftarGulir();
	private final Set<Integer> muridDipilih = new LinkedHashSet<>();
	private final Set<Integer> muatanDipilih = new LinkedHashSet<>();
	private int slotDipilih = -1;

	public LayarBukuKas(int npcId, CompoundTag lembar) {
		super(BukuKas.LAYAR, npcId, lembar, k("layar.buku_kas"), LembarKunci.GAYA_KERTAS);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(BukuKas.TAB_HARI_INI, "tab.hari_ini", Glyphs.Atlas.BUKU), tab(BukuKas.TAB_MINGGU, "tab.minggu", LembarKunci.Ikon.TREN_NAIK),
				tab(BukuKas.TAB_SUMBER, "tab.sumber", LembarKunci.Ikon.TIMBANGAN), tab(BukuKas.TAB_LAPAK, "tab.lapak", LembarKunci.Ikon.LAPAK_SEKTE),
				tab(BukuKas.TAB_KAFILAH, "tab.kafilah", LembarKunci.Ikon.KAFILAH));
	}

	@Override
	protected KeadaanKosong.Gambar kosongGambar() {
		return KeadaanKosong.Gambar.GULUNGAN;
	}

	@Override
	protected void setelahLembar(boolean buka) {
		// a refreshed plan may list fewer disciples or loads: drop picks that no longer exist
		Lembar r = lembar.sub(BukuKas.KAFILAH).sub(BukuKas.RENCANA);
		int nm = r.daftar(BukuKas.MURID).size();
		int nl = r.daftar(BukuKas.MUATAN).size();
		muridDipilih.removeIf(i -> i >= nm);
		muatanDipilih.removeIf(i -> i >= nl);
	}

	@Override
	protected void tabBerubah() {
		long now = Util.getMillis();
		daftarBaris.mulaiUlang(now);
		daftarSumber.mulaiUlang(now);
		daftarKafilah.mulaiUlang(now);
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		switch (tab) {
			case BukuKas.TAB_MINGGU -> minggu(l, x0, y0, x1, y1);
			case BukuKas.TAB_SUMBER -> sumber(l, x0, y0, x1, y1);
			case BukuKas.TAB_LAPAK -> lapak(l, x0, y0, x1, y1);
			case BukuKas.TAB_KAFILAH -> kafilah(l, x0, y0, x1, y1);
			default -> hariIni(l, x0, y0, x1, y1);
		}
	}

	/** Column header "PK | BATU (NB)" with icons; returns the x of the PK and NB right edges. */
	private int[] kolom(Lukis l, int x0, int y, int x1, Component kiri) {
		int nbX = x1 - 4;
		int pkX = x1 - Math.max(90, (x1 - x0) / 3);
		l.teks(Brush.bold(kiri), x0, y, pkX - x0 - 50, l.gaya.aksen);
		Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.PK, pkX - 28, y - 1, 10, 0xFFFFFFFF);
		l.kanan(Brush.bold(k("kas.pk")), pkX, y, l.gaya.aksen);
		Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.NB, nbX - 50, y - 1, 10, 0xFFFFFFFF);
		l.kanan(Brush.bold(k("kas.batu")), nbX, y, l.gaya.aksen);
		l.garis(x0, x1, y + 10);
		return new int[] {pkX, nbX};
	}

	private void hariIni(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> baris = lembar.daftar(BukuKas.BARIS);
		int[] k = kolom(l, x0, y0, x1, k("kas.hari", lembar.i(LembarKunci.HARI, 0)));
		int top = y0 + 14;
		int bottom = y1 - 26;
		if (baris.isEmpty()) {
			kosong(l, k("kas.kosong"), x0, top, x1, bottom);
		} else {
			daftarBaris.gambar(l, "baris", x0, top, x1, bottom, baris.size(), 12, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar e = baris.get(i);
				if (sorot) {
					lk.g.fill(rx0, ry0, rx1, ry1, l.gaya.kartuSorot);
				}
				Component nama = e.ada(LembarKunci.NAMA) ? e.teks(LembarKunci.NAMA) : k("kas.sumber." + e.s(BukuKas.SUMBER));
				lk.teks(nama, rx0 + 2, ry0 + 2, k[0] - rx0 - 60, l.gaya.teks);
				int pk = e.i(LembarKunci.PK, 0);
				if (pk != 0) {
					LabelHarga.pk(lk, pk, k[0], ry0 + 2, true, pk > 0 ? 0xFF2E7A4E : Palette.BAHAYA);
				}
				long nb = e.l(LembarKunci.NB, 0L);
				int w = 0;
				if (nb != 0) {
					w = LabelHarga.nbBertanda(lk, nb, k[1], ry0 + 2, true);
				}
				Component cat = e.teksAtau(LembarKunci.CATATAN);
				if (cat != null) {
					lk.teks(cat, k[0] + 6, ry0 + 2, k[1] - w - k[0] - 10, l.gaya.teksSamar);
				}
			}, null, null);
		}
		// net line + sparkline
		int ny = y1 - 22;
		l.garis(x0, x1, ny);
		l.teks(Brush.bold(k("kas.bersih")), x0 + 2, ny + 5, 60, l.gaya.teks);
		int bpk = lembar.i(BukuKas.BERSIH_PK, 0);
		LabelHarga.pk(l, bpk, k[0], ny + 5, true, bpk >= 0 ? 0xFF2E7A4E : Palette.BAHAYA);
		LabelHarga.nbBertanda(l, lembar.l(BukuKas.BERSIH_NB, 0L), k[1], ny + 5, true);
		int sx0 = x0 + 64;
		int sx1 = Math.min(k[0] - 70, sx0 + 90);
		if (sx1 - sx0 > 30) {
			sparkline(l, lembar.la(BukuKas.GRAFIK_NB), sx0, ny + 3, sx1, ny + 18, 0xFF3E78C0);
			sparkline(l, toLong(lembar.ia(BukuKas.GRAFIK_PK)), sx0, ny + 3, sx1, ny + 18, 0xFFB98F34);
			l.teks(k("kas.tujuh_hari"), sx1 + 3, ny + 5, 40, l.gaya.teksSamar);
		}
	}

	private static long[] toLong(int[] a) {
		long[] out = new long[a.length];
		for (int i = 0; i < a.length; i++) {
			out[i] = a[i];
		}
		return out;
	}

	private void sparkline(Lukis l, long[] v, int x0, int y0, int x1, int y1, int warna) {
		if (v.length < 2) {
			return;
		}
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (long n : v) {
			min = Math.min(min, n);
			max = Math.max(max, n);
		}
		float span = Math.max(1L, max - min);
		float step = (x1 - x0) / (float) (v.length - 1);
		for (int i = 0; i < v.length - 1; i++) {
			float ax = x0 + i * step;
			float ay = y1 - (v[i] - min) / span * (y1 - y0);
			float bx = x0 + (i + 1) * step;
			float by = y1 - (v[i + 1] - min) / span * (y1 - y0);
			Brush.line(l.g, ax, ay, bx, by, 1.2F, warna);
		}
		float lx = x1;
		float ly = y1 - (v[v.length - 1] - min) / span * (y1 - y0);
		Brush.dot(l.g, lx, ly, 1.6F, warna);
	}

	private void minggu(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(BukuKas.MINGGU);
		int[] k = kolom(l, x0, y0, x1, k("kas.minggu"));
		if (rows.isEmpty()) {
			kosong(l, k("kas.kosong"), x0, y0 + 14, x1, y1);
			return;
		}
		long maxNb = 1L;
		int maxPk = 1;
		for (Lembar r : rows) {
			maxNb = Math.max(maxNb, Math.abs(r.l(LembarKunci.NB, 0L)));
			maxPk = Math.max(maxPk, Math.abs(r.i(LembarKunci.PK, 0)));
		}
		int y = y0 + 15;
		int rowH = Math.max(14, Math.min(22, (y1 - y) / Math.max(1, rows.size())));
		int barX0 = x0 + 46;
		int barX1 = k[0] - 64;
		for (Lembar r : rows) {
			if (y + rowH > y1) {
				break;
			}
			l.teks(k("kas.hari", r.i(LembarKunci.HARI, 0)), x0 + 2, y + 2, 42, l.gaya.teksLemah);
			int pk = r.i(LembarKunci.PK, 0);
			long nb = r.l(LembarKunci.NB, 0L);
			int mid = (barX0 + barX1) / 2;
			if (barX1 - barX0 > 30) {
				l.g.fill(mid, y + 1, mid + 1, y + rowH - 3, Palette.argb(0x60, l.gaya.teksSamar & 0xFFFFFF));
				int half = (barX1 - barX0) / 2;
				int pkLen = Math.round(half * Math.abs(pk) / (float) maxPk);
				int nbLen = Math.round(half * Math.abs(nb) / (float) maxNb);
				int pkCol = pk >= 0 ? 0xFFB98F34 : 0xFFB03030;
				int nbCol = nb >= 0 ? 0xFF3E78C0 : 0xFFB03030;
				l.g.fill(pk >= 0 ? mid + 1 : mid - pkLen, y + 2, pk >= 0 ? mid + 1 + pkLen : mid, y + 5, pkCol);
				l.g.fill(nb >= 0 ? mid + 1 : mid - nbLen, y + 6, nb >= 0 ? mid + 1 + nbLen : mid, y + 9, nbCol);
			}
			LabelHarga.pk(l, pk, k[0], y + 2, true, pk >= 0 ? 0xFF2E7A4E : Palette.BAHAYA);
			LabelHarga.nbBertanda(l, nb, k[1], y + 2, true);
			y += rowH;
		}
	}

	private void sumber(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(BukuKas.SUMBER_DAFTAR);
		int[] k = kolom(l, x0, y0, x1, k("kas.sumber_judul"));
		if (rows.isEmpty()) {
			kosong(l, k("kas.kosong"), x0, y0 + 14, x1, y1);
			return;
		}
		daftarSumber.gambar(l, "sumber", x0, y0 + 14, x1, y1, rows.size(), 20, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, false);
			Component nama = e.ada(LembarKunci.NAMA) ? e.teks(LembarKunci.NAMA) : k("kas.sumber." + e.s(BukuKas.SUMBER));
			lk.teks(nama, rx0 + 3, ry0 + 1, k[0] - rx0 - 60, l.gaya.teks);
			Component cat = e.teksAtau(LembarKunci.CATATAN);
			if (cat != null) {
				lk.teks(cat, rx0 + 3, ry0 + 10, k[0] - rx0 - 60, l.gaya.teksSamar);
			}
			int pk = e.i(LembarKunci.PK, 0);
			if (pk != 0) {
				LabelHarga.pk(lk, pk, k[0], ry0 + 5, true, pk > 0 ? 0xFF2E7A4E : Palette.BAHAYA);
			}
			long nb = e.l(LembarKunci.NB, 0L);
			if (nb != 0) {
				LabelHarga.nbBertanda(lk, nb, k[1], ry0 + 5, true);
			}
		}, null, null);
	}

	private void lapak(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar lp = lembar.sub(BukuKas.LAPAK);
		if (!lp.b(BukuKas.ADA)) {
			float cx = (x0 + x1) / 2.0F;
			Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.LAPAK_SEKTE, Math.round(cx - 16), y0 + 8, 32, 0xFFFFFFFF);
			Component alasan = lp.ada(LembarKunci.ALASAN) ? lp.teks(LembarKunci.ALASAN) : k("kas.lapak_belum");
			l.bungkus(alasan, x0 + 10, y0 + 46, x1 - x0 - 20, l.gaya.teksLemah, lp.ada(BukuKas.SEWA) ? 28 : y1 - y0 - 48);
			if (lp.ada(BukuKas.SEWA)) {
				tombolOp(l, "sewa", Math.round(cx - 70), y0 + 76, 140, 15, k("kas.sewa", Brush.angka(lp.l(BukuKas.SEWA, 0L))), true, null,
						BukuKas.OP_LAPAK_SEWA);
			}
			return;
		}
		// header facts
		int y = y0;
		Component kios = lp.teks(BukuKas.KIOS);
		l.teks(Brush.bold(kios), x0, y, (x1 - x0) / 2.0F, l.gaya.teks);
		l.kanan(k("kas.sewa_sisa", lp.i(BukuKas.SEWA_SISA_HARI, 0)), x1, y, l.gaya.teksLemah);
		y += 10;
		Component penjaga = lp.teksAtau(BukuKas.PENJAGA);
		l.teks(penjaga == null ? k("kas.tanpa_penjaga") : k("kas.penjaga", penjaga), x0, y, (x1 - x0) / 2.0F,
				penjaga == null ? Palette.BAHAYA : l.gaya.teksSamar);
		long kas = lp.l(BukuKas.KAS, 0L);
		tombolOp(l, "kas", x1 - 96, y - 1, 96, 13, k("kas.ambil_kas", Brush.singkat(kas)), kas > 0, k("kas.kas_kosong"),
				BukuKas.OP_LAPAK_KAS);
		y += 15;
		// 18 slots grid (6 x 3) and the selected slot editor on the right
		List<Lembar> slots = lp.daftar(LembarKunci.DAFTAR);
		int cell = 24;
		int cols = 6;
		int gx = x0;
		for (int s = 0; s < 18; s++) {
			int cx = gx + (s % cols) * cell;
			int cy = y + (s / cols) * cell;
			Lembar e = null;
			for (Lembar it : slots) {
				if (it.i(LembarKunci.SLOT, -1) == s) {
					e = it;
				}
			}
			boolean over = l.di(cx, cy, cx + cell - 2, cy + cell - 2);
			boolean sel = s == slotDipilih;
			l.g.fill(cx, cy, cx + cell - 2, cy + cell - 2, sel ? Palette.argb(0x90, Palette.GOLD & 0xFFFFFF) : over ? l.gaya.kartuSorot : l.gaya.kartu);
			Brush.frame(l.g, cx, cy, cx + cell - 2, cy + cell - 2, Palette.argb(0x60, l.gaya.teksSamar & 0xFFFFFF));
			if (e != null) {
				l.g.item(e.barang(), cx + 3, cy + 2);
				int peluang = e.i(LembarKunci.PELUANG, 0);
				int pc = peluang >= 80 ? Palette.OK : peluang >= 50 ? Palette.PERINGATAN : peluang > 0 ? 0xFFD07030 : Palette.BAHAYA;
				l.g.fill(cx + 1, cy + cell - 5, cx + cell - 3, cy + cell - 3, pc);
			}
			final int slot = s;
			l.zona("lapak" + s, cx, cy, cx + cell - 2, cy + cell - 2, () -> slotDipilih = slot, true, null, true);
			l.fokusCincin("lapak" + s, cx, cy, cx + cell - 2, cy + cell - 2);
		}
		int ex0 = gx + cols * cell + 6;
		int ey = y;
		Lembar sel = null;
		for (Lembar it : slots) {
			if (it.i(LembarKunci.SLOT, -1) == slotDipilih) {
				sel = it;
			}
		}
		if (ex0 < x1 - 60) {
			l.kartu(ex0, ey, x1, ey + 3 * cell - 2, 0.0F, false);
			int tx = ex0 + 4;
			if (sel == null) {
				l.bungkus(slotDipilih < 0 ? k("kas.pilih_slot") : k("kas.slot_kosong"), tx, ey + 4, x1 - tx - 4, l.gaya.teksSamar,
						3 * cell - 8 - (slotDipilih >= 0 ? 20 : 0));
				if (slotDipilih >= 0) {
					tombolOp(l, "isi", tx, ey + 3 * cell - 20, x1 - tx - 4, 14, k("kas.isi_tangan"), true, null, BukuKas.OP_LAPAK_ISI);
				}
			} else {
				l.teks(sel.teks(LembarKunci.NAMA), tx, ey + 3, x1 - tx - 4, l.gaya.teks);
				long harga = sel.l(LembarKunci.HARGA, 0L);
				long nilai = Math.max(1L, sel.l(LembarKunci.NILAI, harga));
				LabelHarga.gambar(l, harga, -1L, tx, ey + 14, false, false);
				l.teks(k("kas.peluang", sel.i(LembarKunci.PELUANG, 0), Brush.angka(nilai)), tx, ey + 25, x1 - tx - 4, l.gaya.teksSamar);
				int bw = (x1 - tx - 8) / 2;
				long turun = Math.max(1L, Math.round(harga * 0.9D));
				long naik = Math.max(harga + 1, Math.round(harga * 1.1D));
				tombolOp(l, "harga_turun", tx, ey + 36, bw, 13, Component.literal("−10%"), true, null,
						LembarKunci.op(BukuKas.OP_LAPAK_HARGA, slotDipilih, turun));
				tombolOp(l, "harga_naik", tx + bw + 4, ey + 36, bw, 13, Component.literal("+10%"), true, null,
						LembarKunci.op(BukuKas.OP_LAPAK_HARGA, slotDipilih, naik));
				tombolOp(l, "ambil", tx, ey + 52, x1 - tx - 4, 13, k("kas.tarik_barang"), true, null,
						LembarKunci.op(BukuKas.OP_LAPAK_AMBIL, slotDipilih));
			}
		}
		l.teks(k("kas.volume", Brush.angka(lp.l(BukuKas.VOLUME_MAKS, 0L))), x0, y + 3 * cell + 2, x1 - x0, l.gaya.teksSamar);
		l.teks(k("kas.legenda_peluang"), x0, y + 3 * cell + 12, x1 - x0, l.gaya.teksSamar);
	}

	private void kafilah(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar kf = lembar.sub(BukuKas.KAFILAH);
		List<Lembar> jalan = kf.daftar(LembarKunci.DAFTAR);
		Kotak[] k = bagi(x0, y0, x1, y1);
		Kotak kiri = k[0];
		int y = kiri.y0();
		l.teks(k("kas.laba_minggu", Brush.angka(kf.l(BukuKas.LABA_MINGGU, 0L)), Brush.angka(kf.l(BukuKas.LABA_BATAS, 0L))),
				kiri.x0(), y, kiri.w(), l.gaya.teksLemah);
		y += 10;
		if (jalan.isEmpty()) {
			l.teks(k("kas.kafilah_kosong"), kiri.x0(), y + 4, kiri.w(), l.gaya.teksSamar);
		} else {
			daftarKafilah.gambar(l, "kafilah", kiri.x0(), y, kiri.x1(), kiri.y1(), jalan.size(), 48, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar e = jalan.get(i);
				lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
				Glyphs.Atlas.ikon(lk.g, LembarKunci.Ikon.KAFILAH, rx0 + 3, ry0 + 3, 14, 0xFFFFFFFF);
				lk.teks(Brush.bold(e.teks(LembarKunci.TUJUAN)), rx0 + 20, ry0 + 4, rx1 - rx0 - 70, l.gaya.teks);
				lk.kanan(Brush.hari(e.i(LembarKunci.SISA_HARI, 0)), rx1 - 3, ry0 + 4, l.gaya.teksLemah);
				// mini map: start, goal and the caravan along the line
				int mx0 = rx0 + 6;
				int mx1 = rx1 - 6;
				int my = ry0 + 22;
				float p = Math.max(0.0F, Math.min(1.0F, e.f(LembarKunci.KEMAJUAN, 0.0F)));
				lk.g.fill(mx0, my, mx1, my + 1, Palette.argb(0x80, l.gaya.teksSamar & 0xFFFFFF));
				lk.g.fill(mx0, my, mx0 + Math.round((mx1 - mx0) * p), my + 1, Palette.GOLD);
				Brush.disc(lk.g, mx0, my + 0.5F, 2.0F, Palette.OK);
				Brush.disc(lk.g, mx1, my + 0.5F, 2.0F, Palette.SEAL);
				Glyphs.Atlas.ikon(lk.g, LembarKunci.Ikon.KAFILAH, Math.round(mx0 + (mx1 - mx0) * p - 5), my - 9, 10, 0xFFFFFFFF);
				int risiko = e.i(LembarKunci.RISIKO, 0);
				lk.teks(k("kas.risiko_laba", risiko, Brush.angka(e.l(BukuKas.LABA_KIRA, 0L))), rx0 + 6, ry0 + 27, rx1 - rx0 - 12,
						risiko >= 20 ? Palette.BAHAYA : l.gaya.teksSamar);
				List<Lembar> pengawal = e.daftar(BukuKas.PENGAWAL);
				if (!pengawal.isEmpty()) {
					List<String> nama = new ArrayList<>();
					for (Lembar pw : pengawal) {
						nama.add(pw.teks(LembarKunci.NAMA).getString());
					}
					lk.teks(k("kas.pengawal", String.join(", ", nama)), rx0 + 6, ry0 + 37, rx1 - rx0 - 12, l.gaya.teksSamar);
				}
			}, null, null);
		}
		rencana(l, kf, k[1]);
	}

	private void rencana(Lukis l, Lembar kf, Kotak k) {
		Lembar r = kf.sub(BukuKas.RENCANA);
		l.kartu(k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		int x0 = k.x0() + 4;
		int x1 = k.x1() - 4;
		int y = k.y0() + 3;
		y += l.kepala(k("kas.kirim_kafilah"), x0, y, x1);
		List<Lembar> tujuan = r.daftar(LembarKunci.TUJUAN);
		List<Lembar> murid = r.daftar(BukuKas.MURID);
		List<Lembar> muatan = r.daftar(BukuKas.MUATAN);
		Component alasanKunci = kf.teksAtau(LembarKunci.ALASAN);
		if (tujuan.isEmpty()) {
			l.bungkus(alasanKunci != null ? alasanKunci : k("kas.tak_ada_tujuan"), x0, y, x1 - x0, l.gaya.teksSamar, k.y1() - y - 4);
			return;
		}
		// destinations, escorts and loads share the card above the button; each is a list that scrolls when long
		int listH = Math.min(tujuan.size() * 13, Math.max(26, (k.y1() - y) / 4));
		daftarTujuan.gambar(l, "tujuan", x0, y, x1, y + listH, tujuan.size(), 13, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = tujuan.get(i);
			lk.g.fill(rx0, ry0, rx1, ry1, pilih ? Palette.argb(0x70, Palette.GOLD & 0xFFFFFF) : sorot ? l.gaya.kartuSorot : 0);
			Component jarak = k("kas.jarak_hari", e.i(LembarKunci.JARAK, 0), e.i(LembarKunci.HARI, 0), Math.round(e.f(BukuKas.PERMINTAAN, 1.0F) * 100));
			int jw = Math.min((rx1 - rx0) / 2, font.width(jarak));
			lk.teks(e.teks(LembarKunci.NAMA), rx0 + 2, ry0 + 2, rx1 - rx0 - jw - 8, l.gaya.teks);
			lk.teks(jarak, rx1 - 2 - jw, ry0 + 2, jw, l.gaya.teksSamar);
		}, null, null);
		y += listH + 3;
		int tombolY = k.y1() - 18;
		int ruang = Math.max(22, tombolY - 3 - y - 18);
		int muatanH = Math.min(Math.max(11, muatan.size() * 11), Math.max(11, ruang / 2));
		int muridH = Math.max(11, Math.min(Math.max(11, murid.size() * 11), ruang - muatanH));
		l.teks(k("kas.pilih_pengawal", muridDipilih.size() + "/3"), x0, y, x1 - x0, l.gaya.teksLemah);
		y += 10;
		pilihan(l, "murid", daftarMurid, murid, muridDipilih, 3, x0, y, x1, y + muridH, BukuKas.RANAH, k("kas.tanpa_murid"));
		y += muridH + 2;
		l.teks(k("kas.pilih_muatan", muatanDipilih.size() + "/2"), x0, y, x1 - x0, l.gaya.teksLemah);
		y += 10;
		pilihan(l, "muatan", daftarMuatan, muatan, muatanDipilih, 2, x0, y, x1, y + muatanH, null, k("kas.tanpa_muatan"));
		int t = daftarTujuan.pilihan();
		boolean siap = t >= 0 && !muatanDipilih.isEmpty() && !muridDipilih.isEmpty() && alasanKunci == null;
		Component alasan = alasanKunci != null ? alasanKunci : k("kas.lengkapi");
		String op = LembarKunci.op(BukuKas.OP_KAFILAH_KIRIM, t, gabung(muatanDipilih), gabung(muridDipilih));
		tombolOp(l, "kirim_kafilah", x0, tombolY, x1 - x0, 15, k("kas.berangkatkan"), siap, alasan, op);
	}

	/**
	 * A toggle list (escorts, loads): one click or Enter ticks / unticks the row; at most {@code maks} ticked. Keyboard:
	 * Tab to the list, ↑↓ to move, Enter to tick.
	 */
	private void pilihan(Lukis l, String kunci, DaftarGulir d, List<Lembar> daftar, Set<Integer> dipilih, int maks, int x0, int y0, int x1, int y1,
			String sub, Component kosong) {
		if (daftar.isEmpty()) {
			l.teks(kosong, x0 + 2, y0 + 1, x1 - x0 - 4, l.gaya.teksSamar);
			return;
		}
		d.gambar(l, kunci, x0, y0, x1, y1, daftar.size(), 11, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = daftar.get(i);
			boolean on = dipilih.contains(i);
			lk.g.fill(rx0, ry0, rx1, ry1, pilih ? l.gaya.kartuSorot : sorot ? Palette.argb(0x30, l.gaya.kartuSorot & 0xFFFFFF) : i % 2 == 0 ? l.gaya.kartu : 0);
			// the tick box
			lk.sumur(rx0 + 2, ry0 + 1, rx0 + 11, ry0 + 10);
			if (on) {
				Glyphs.Atlas.ikon(lk.g, Glyphs.Atlas.CENTANG, rx0 + 2, ry0 + 1, 9, 0xFFFFFFFF);
			}
			Component nama = e.teks(LembarKunci.NAMA);
			int subW = 0;
			if (sub != null && e.ada(sub)) {
				Component s = e.teks(sub);
				subW = Math.min((rx1 - rx0) / 2, font.width(s)) + 4;
				lk.teks(s, rx1 - subW + 2, ry0 + 1, subW - 4, l.gaya.teksSamar);
			}
			lk.teks(nama, rx0 + 14, ry0 + 1, rx1 - rx0 - 16 - subW, on ? l.gaya.aksen : l.gaya.teks);
			final int idx = i;
			lk.zona(kunci + "#cek" + i, rx0, ry0, rx1, ry1, () -> {
				d.setPilih(idx);
				centang(dipilih, idx, maks);
			}, true, null, false);
		}, () -> centang(dipilih, d.pilihan(), maks), null);
	}

	private void centang(Set<Integer> dipilih, int i, int maks) {
		if (i < 0) {
			return;
		}
		if (dipilih.remove(i)) {
			UiSound.toggle(false);
		} else if (dipilih.size() < maks) {
			dipilih.add(i);
			UiSound.toggle(true);
		} else {
			UiSound.denied();
			tampilPesan(k("kas.maks_pilih", maks), Palette.PERINGATAN);
		}
	}

	private static String gabung(Set<Integer> s) {
		StringBuilder b = new StringBuilder();
		for (int i : s) {
			if (b.length() > 0) {
				b.append('.');
			}
			b.append(i);
		}
		return b.toString();
	}
}
