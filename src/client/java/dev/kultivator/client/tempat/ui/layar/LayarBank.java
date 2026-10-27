package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihJumlah;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Bank;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * {@code bank} (DESIGN 38.U.3.5): Rumah Uang Seribu Tael. "Simpan": the balance (rolling, title size), the stones in the
 * bag, deposit all stones, withdraw presets with the 1 % fee shown, the history. "Wesel": write a note for an amount (2 %),
 * cash the note in hand. "Gadai": pawn the item in hand (40 % loan, 46 % to redeem in 7 days) and the pawned list with days
 * left. "Tukar": buy and sell Batu Asal with the weekly / daily limits. "Brankas": the vault grid (9 or 18 slots). Single
 * kit, text 1.0.
 */
public final class LayarBank extends LayarInti {

	private static final long[] PRESET = {100L, 1_000L, 10_000L};

	private final LabelHarga.Gulir saldoGulir = new LabelHarga.Gulir();
	private final DaftarGulir daftarRiwayat = new DaftarGulir();
	private final DaftarGulir daftarGadai = new DaftarGulir();
	private final PemilihJumlah jumlahTukar = new PemilihJumlah();

	public LayarBank(int npcId, CompoundTag lembar) {
		super(Bank.LAYAR, npcId, lembar, k("layar.bank"), LembarKunci.GAYA_KAIN);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(Bank.TAB_SIMPAN, "tab.simpan", LembarKunci.Ikon.BANK), tab(Bank.TAB_WESEL, "tab.wesel", Glyphs.Atlas.SURAT),
				tab(Bank.TAB_GADAI, "tab.gadai", LembarKunci.Ikon.TIMBANGAN), tab(Bank.TAB_TUKAR, "tab.tukar", LembarKunci.Ikon.BATU_ASAL),
				tab(Bank.TAB_BRANKAS, "tab.brankas", Glyphs.Atlas.KUNCI));
	}

	@Override
	protected boolean tabAda(String id) {
		return switch (id) {
			case Bank.TAB_WESEL -> lembar.ada(Bank.WESEL);
			case Bank.TAB_GADAI -> lembar.ada(Bank.GADAI);
			case Bank.TAB_TUKAR -> lembar.ada(Bank.TUKAR);
			case Bank.TAB_BRANKAS -> lembar.ada(Bank.BRANKAS);
			default -> true;
		};
	}

	@Override
	public boolean lebar() {
		return false;
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		switch (tab) {
			case Bank.TAB_WESEL -> wesel(l, x0, y0, x1, y1);
			case Bank.TAB_GADAI -> gadai(l, x0, y0, x1, y1);
			case Bank.TAB_TUKAR -> tukar(l, x0, y0, x1, y1);
			case Bank.TAB_BRANKAS -> brankas(l, x0, y0, x1, y1);
			default -> simpan(l, x0, y0, x1, y1);
		}
	}

	private void simpan(Lukis l, int x0, int y0, int x1, int y1) {
		long saldo = lembar.l(Bank.SALDO, 0L);
		Kotak[] k = bagi(x0, y0, x1, y1);
		// the narrow layout keeps the counter whole and drops the history column
		boolean sempit = tata() == Tata.SEMPIT;
		Kotak kiri = sempit ? new Kotak(x0, y0, x1, y1) : k[0];
		int kartuH = 30;
		l.kartu(kiri.x0(), kiri.y0(), kiri.x1(), kiri.y0() + kartuH, 0.0F, false);
		l.teks(k("bank.saldo"), kiri.x0() + 5, kiri.y0() + 3, kiri.w() - 10, l.gaya.teksLemah);
		long tampil = saldoGulir.nilai(saldo, l.kini);
		l.judul(Brush.nb(tampil, false), kiri.x0() + 5, kiri.y0() + 13, kiri.w() - 10, saldoGulir.bergulir(l.kini) ? Palette.GOLD : l.gaya.teks);
		int y = kiri.y0() + kartuH + 4;
		l.teks(k("bank.di_tas"), kiri.x0(), y, kiri.w(), l.gaya.teksLemah);
		y += 10;
		int[] batu = lembar.ia(LembarKunci.BATU);
		int bx = kiri.x0();
		for (int t = 0; t < 4; t++) {
			int n = t < batu.length ? batu[t] : 0;
			Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.BATU_RENDAH + t, bx, y, 12, n > 0 ? 0xFFFFFFFF : 0x60FFFFFF);
			Component c = Component.literal(Brush.angka(n));
			l.teks(c, bx + 13, y + 2, l.font.width(c) + 1, l.gaya.teks);
			bx += 16 + l.font.width(c) + 4;
		}
		y += 14;
		boolean adaBatu = false;
		for (int n : batu) {
			adaBatu |= n > 0;
		}
		tombolOp(l, "setor", kiri.x0(), y, kiri.w(), TOMBOL_H, k("bank.setor_semua"), adaBatu, k("bank.tanpa_batu"), Bank.OP_SETOR);
		y += TOMBOL_H + 3;
		float fee = lembar.f(Bank.BIAYA_TARIK, 0.01F);
		l.teks(k("bank.tarik_biaya", Math.round(fee * 100)), kiri.x0(), y, kiri.w(), l.gaya.teksLemah);
		y += 11;
		int w3 = (kiri.w() - 6) / 3;
		for (int i = 0; i < PRESET.length; i++) {
			long n = PRESET[i];
			long biaya = Math.max(1, Math.round(n * fee));
			tombolOp(l, "tarik" + i, kiri.x0() + i * (w3 + 3), y, w3, TOMBOL_H, Component.literal(Brush.singkat(n)), saldo >= n + biaya,
					k("bank.saldo_kurang_n", Brush.angka(n + biaya)), LembarKunci.op(Bank.OP_TARIK, n));
		}
		y += TOMBOL_H + 3;
		long semua = Math.max(0L, saldo - Math.max(1L, Math.round(saldo * fee / (1 + fee))));
		tombolOp(l, "tarik_semua", kiri.x0(), y, kiri.w(), TOMBOL_H, k("bank.tarik_semua", Brush.angka(semua)), semua > 0, k("bank.saldo_kurang"),
				LembarKunci.op(Bank.OP_TARIK, semua));
		if (sempit) {
			return;
		}
		// history
		Kotak kanan = k[1];
		List<Lembar> riwayat = lembar.daftar(LembarKunci.RIWAYAT);
		int ry = kanan.y0() + l.kepala(k("bank.riwayat"), kanan.x0(), kanan.y0(), kanan.x1());
		if (riwayat.isEmpty()) {
			l.samar(k("bank.riwayat_kosong"), kanan.x0(), ry + 2, kanan.w());
			return;
		}
		daftarRiwayat.gambar(l, "riwayat", kanan.x0(), ry, kanan.x1(), kanan.y1(), riwayat.size(), 12, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = riwayat.get(i);
			if (i % 2 == 0) {
				lk.g.fill(rx0, ry0, rx1, ry1, lk.gaya.kartu);
			}
			Component hari = k("bank.hari", e.i(LembarKunci.HARI, 0));
			int hw = Math.min(40, lk.font.width(hari)) + 4;
			lk.teks(hari, rx0 + 2, ry0 + 2, hw - 4, lk.gaya.teksSamar);
			int w = LabelHarga.nbBertanda(lk, e.l(LembarKunci.NB, 0L), rx1 - 2, ry0 + 2, true);
			lk.teks(e.teks(LembarKunci.CATATAN), rx0 + 2 + hw, ry0 + 2, rx1 - rx0 - hw - 8 - w, lk.gaya.teksLemah);
		}, null, null);
	}

	private void wesel(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar w = lembar.sub(Bank.WESEL);
		long saldo = lembar.l(Bank.SALDO, 0L);
		float biaya = w.f(LembarKunci.BIAYA, 0.02F);
		int y = y0 + l.kepala(k("bank.wesel_tulis"), x0, y0, x1);
		y += Math.round(l.bungkus(k("bank.wesel_jelas", Math.round(biaya * 100)), x0, y, x1 - x0, l.gaya.teksLemah, 18)) + 3;
		int w3 = (x1 - x0 - 6) / 3;
		for (int i = 0; i < PRESET.length; i++) {
			long n = PRESET[i];
			long total = n + Math.max(1L, Math.round(n * biaya));
			tombolOp(l, "wesel" + i, x0 + i * (w3 + 3), y, w3, TOMBOL_H, k("bank.wesel_nilai", Brush.singkat(n)), saldo >= total, k("bank.saldo_kurang"),
					LembarKunci.op(Bank.OP_WESEL, n));
		}
		y += TOMBOL_H + 8;
		y += l.kepala(k("bank.wesel_cair"), x0, y, x1);
		Lembar tangan = w.sub(LembarKunci.TANGAN);
		if (tangan.ada(LembarKunci.NAMA)) {
			int bw = 74;
			Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.SURAT, x0, y, 16, 0xFFFFFFFF);
			int hw = LabelHarga.gambar(l, tangan.l(LembarKunci.NB, 0L), -1L, x1 - bw - 6, y + 4, true, false);
			l.teks(tangan.teks(LembarKunci.NAMA), x0 + 20, y + 4, x1 - x0 - 30 - bw - hw, l.gaya.teks);
			tombolOp(l, "cair", x1 - bw, y + 1, bw, TOMBOL_H, k("bank.cairkan"), true, null, Bank.OP_CAIR);
		} else {
			l.samar(k("bank.wesel_tak_dipegang"), x0, y + 2, x1 - x0);
		}
	}

	private void gadai(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar g = lembar.sub(Bank.GADAI);
		Kotak[] k = bagi(x0, y0, x1, y1);
		Kotak kiri = k[0];
		int y = kiri.y0() + l.kepala(k("bank.gadai_tangan"), kiri.x0(), kiri.y0(), kiri.x1());
		y += Math.round(l.bungkus(k("bank.gadai_syarat", Math.round(g.f(Bank.PINJAM, 0.4F) * 100), Math.round(g.f(Bank.TEBUS, 0.46F) * 100),
				g.i(LembarKunci.HARI, 7)), kiri.x0(), y, kiri.w(), l.gaya.teksLemah, 27)) + 3;
		Lembar tangan = g.sub(LembarKunci.TANGAN);
		if (tangan.ada(LembarKunci.NAMA)) {
			if (!tangan.barang().isEmpty()) {
				l.g.item(tangan.barang(), kiri.x0(), y);
			}
			l.teks(tangan.teks(LembarKunci.NAMA), kiri.x0() + 20, y + 4, kiri.w() - 20, l.gaya.teks);
			y += 19;
			y += nilai(l, k("bank.nilai"), Brush.nb(tangan.l(LembarKunci.NILAI, 0L), false), kiri.x0(), y, kiri.x1());
			y += nilai(l, k("bank.pinjaman"), Brush.nb(tangan.l(Bank.PINJAMAN, 0L), false), kiri.x0(), y, kiri.x1());
			tombolOp(l, "gadai", kiri.x0(), Math.min(y + 2, kiri.y1() - TOMBOL_H), kiri.w(), TOMBOL_H, k("bank.gadaikan"), tangan.l(Bank.PINJAMAN, 0L) > 0,
					k("bank.tak_bernilai"), Bank.OP_GADAI);
		} else {
			l.samar(k("bank.pegang_barang"), kiri.x0(), y, kiri.w());
		}
		Kotak kanan = k[1];
		List<Lembar> daftar = g.daftar(LembarKunci.DAFTAR);
		int ry = kanan.y0() + l.kepala(k("bank.digadai"), kanan.x0(), kanan.y0(), kanan.x1());
		if (daftar.isEmpty()) {
			l.samar(k("bank.gadai_kosong"), kanan.x0(), ry + 2, kanan.w());
			return;
		}
		daftarGadai.gambar(l, "digadai", kanan.x0(), ry, kanan.x1(), kanan.y1(), daftar.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = daftar.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			if (!e.barang().isEmpty()) {
				lk.g.item(e.barang(), rx0 + 3, ry0 + 5);
			}
			int bw = 62;
			lk.teks(e.teks(LembarKunci.NAMA), rx0 + 22, ry0 + 3, rx1 - rx0 - 28 - bw, lk.gaya.teks);
			int hari = e.i(LembarKunci.SISA_HARI, 0);
			lk.teks(k("bank.sisa_hari", hari), rx0 + 22, ry0 + 14, rx1 - rx0 - 28 - bw, hari <= 1 ? lk.gaya.turun() : lk.gaya.teksSamar);
			long tebus = e.l(Bank.TEBUS_NB, 0L);
			tombolOp(lk, "tebus" + i, rx1 - bw - 3, ry0 + (ry1 - ry0 - TOMBOL_H) / 2, bw, TOMBOL_H, k("bank.tebus", Brush.singkat(tebus)),
					uang() < 0 || tebus <= uang() || tebus <= lembar.l(Bank.SALDO, 0L), k("harga.kurang", Brush.angka(tebus - Math.max(0L, uang()))),
					LembarKunci.op(Bank.OP_TEBUS, e.id()));
		}, null, null);
	}

	private void tukar(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar t = lembar.sub(Bank.TUKAR);
		float cx = (x0 + x1) / 2.0F;
		Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.BATU_ASAL, Math.round(cx - 12), y0, 24, 0xFFFFFFFF);
		int y = y0 + 27;
		l.tengah(k("bank.batu_asal_di_tas", t.i(Bank.BATU_ASAL, 0)), cx, y, x1 - x0 - 8, l.gaya.teks);
		y += 13;
		int col = Math.min(140, (x1 - x0 - 8) / 2);
		int lx = Math.round(cx - col - 4);
		int rx = Math.round(cx + 4);
		long beli = t.l(Bank.BELI, 180L);
		long jual = t.l(Bank.JUAL, 90L);
		l.kepala(k("bank.beli_asal"), lx, y, lx + col);
		l.kepala(k("bank.jual_asal"), rx, y, rx + col);
		y += 14;
		LabelHarga.gambar(l, beli, uang(), lx, y, false, false);
		l.samar(k("bank.sisa_minggu", t.i(Bank.BELI_SISA, 0)), lx, y + 11, col);
		LabelHarga.gambar(l, jual, -1L, rx, y, false, false);
		l.samar(k("bank.sisa_hari_ini", t.i(Bank.JUAL_SISA, 0)), rx, y + 11, col);
		y += 24;
		int n = jumlahTukar.nilai();
		int maks = Math.max(1, Math.max(t.i(Bank.BELI_SISA, 0), t.i(Bank.JUAL_SISA, 0)));
		jumlahTukar.gambar(l, "jumlah", Math.round(cx - PemilihJumlah.lebar(l, maks) / 2.0F), y, maks);
		y += TOMBOL_H + 4;
		boolean bolehBeli = t.b(Bank.BOLEH_BELI) && t.i(Bank.BELI_SISA, 0) >= n && (uang() < 0 || beli * n <= uang());
		Component alasanBeli = t.b(Bank.BOLEH_BELI) ? k("bank.batas_tukar") : t.teks(LembarKunci.ALASAN);
		tombolOp(l, "tukar_beli", lx, y, col, TOMBOL_H, k("bank.beli_n", n, Brush.angka(beli * n)), bolehBeli, alasanBeli,
				LembarKunci.op(Bank.OP_TUKAR_BELI, n));
		boolean bolehJual = t.i(Bank.JUAL_SISA, 0) >= n && t.i(Bank.BATU_ASAL, 0) >= n;
		tombolOp(l, "tukar_jual", rx, y, col, TOMBOL_H, k("bank.jual_n", n, Brush.angka(jual * n)), bolehJual, k("bank.batas_jual"),
				LembarKunci.op(Bank.OP_TUKAR_JUAL, n));
	}

	private void brankas(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar b = lembar.sub(Bank.BRANKAS);
		int slots = Math.max(1, b.i(LembarKunci.SLOT, 9));
		List<Lembar> isi = b.daftar(LembarKunci.DAFTAR);
		int y = y0 + l.kepala(k("bank.brankas", isi.size(), slots), x0, y0, x1);
		int cols = 9;
		int cell = Math.max(18, Math.min(22, (x1 - x0 - 8) / cols));
		int gridW = cols * cell;
		int rows = (slots + cols - 1) / cols;
		int gx = (x0 + x1 - gridW) / 2;
		// the iron vault door frame
		l.g.fill(gx - 4, y - 2, gx + gridW + 4, y + rows * cell + 4, Palette.argb(0xD0, 0x2A2E32));
		Brush.frame(l.g, gx - 4, y - 2, gx + gridW + 4, y + rows * cell + 4, Palette.GOLD_DARK);
		for (int s = 0; s < slots; s++) {
			int cx = gx + (s % cols) * cell + 1;
			int cy = y + (s / cols) * cell + 1;
			Lembar item = null;
			for (Lembar e : isi) {
				if (e.i(LembarKunci.SLOT, -1) == s) {
					item = e;
				}
			}
			boolean over = l.di(cx, cy, cx + cell - 2, cy + cell - 2);
			l.g.fill(cx, cy, cx + cell - 2, cy + cell - 2, over ? 0xFF4A5056 : 0xFF3A3E42);
			l.g.fill(cx, cy, cx + cell - 2, cy + 1, 0x60000000);
			if (item != null) {
				int io = (cell - 2 - 16) / 2;
				l.g.item(item.barang(), cx + io, cy + io);
				l.g.itemDecorations(l.font, item.barang(), cx + io, cy + io);
				final Lembar it = item;
				final int slot = s;
				l.zona("slot" + s, cx, cy, cx + cell - 2, cy + cell - 2, () -> kirim(LembarKunci.op(Bank.OP_AMBIL, slot)), tulisBoleh(), alasanBaca(), true);
				if (over) {
					l.tip(List.of(it.teks(LembarKunci.NAMA), k("bank.klik_ambil")));
				}
				l.fokusCincin("slot" + s, cx, cy, cx + cell - 2, cy + cell - 2);
			}
		}
		int by = y + rows * cell + 10;
		tombolOp(l, "simpan_barang", gx, Math.min(by, y1 - TOMBOL_H), gridW, TOMBOL_H, k("bank.simpan_tangan"), isi.size() < slots, k("bank.brankas_penuh"),
				Bank.OP_SIMPAN);
	}
}
