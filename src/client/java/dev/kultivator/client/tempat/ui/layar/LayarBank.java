package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.AngkaGulir;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.PemilihJumlah;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Bank;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * {@code bank} (DESIGN 38.U.3.5): Rumah Uang Seribu Tael. "Simpan": the balance (rolling), the stones in the bag, deposit
 * all stones, withdraw presets with the 1 % fee shown, the history. "Wesel": write a note for an amount (2 %), cash the
 * note in hand. "Gadai": pawn the item in hand (40 % loan, 46 % to redeem in 7 days) and the pawned list with days left.
 * "Tukar": buy and sell Batu Asal with the weekly / daily limits. "Brankas": the vault grid (9 or 18 slots).
 */
public final class LayarBank extends LayarTempatDasar {

	private static final long[] PRESET = {100L, 1_000L, 10_000L};

	private final AngkaGulir saldoGulir = new AngkaGulir();
	private final DaftarGulir daftarRiwayat = new DaftarGulir();
	private final DaftarGulir daftarGadai = new DaftarGulir();
	private final PemilihJumlah jumlahTukar = new PemilihJumlah();

	public LayarBank(int npcId, CompoundTag lembar) {
		super(Bank.LAYAR, npcId, lembar, TeksTempat.k("layar.bank"), TempatWarna.Gaya.KAIN);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(tabBaru(Bank.TAB_SIMPAN, "tab.simpan", LembarKunci.Ikon.BANK), tabBaru(Bank.TAB_WESEL, "tab.wesel", IkonTempat.SURAT),
				tabBaru(Bank.TAB_GADAI, "tab.gadai", LembarKunci.Ikon.TIMBANGAN), tabBaru(Bank.TAB_TUKAR, "tab.tukar", LembarKunci.Ikon.BATU_ASAL),
				tabBaru(Bank.TAB_BRANKAS, "tab.brankas", IkonTempat.KUNCI));
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
	protected void isi(Lukis l, int x0, int y0, int x1, int y1) {
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
		Kotak kiri = tata == Tata.SEMPIT ? new Kotak(x0, y0, x1, y1) : k[0];
		// GUI scale 4 (126-138 px): a slimmer balance card and tighter rows, so "Tarik semua" stays inside
		boolean ringkas = kiri.h() < 136;
		int kartuH = ringkas ? 30 : 38;
		PanelTempat.kartu(l.g, gaya, kiri.x0(), kiri.y0(), kiri.x1(), kiri.y0() + kartuH, 0.0F, false);
		l.keterangan(TeksTempat.k("bank.saldo"), kiri.x0() + 5, kiri.y0() + (ringkas ? 3 : 4), kiri.w(), gaya.teksLemah);
		LabelHarga.gambar(l, saldoGulir.nilai(saldo, l.kini), -1L, kiri.x0() + 5, kiri.y0() + (ringkas ? 12 : 15), false, ringkas ? 1.3F : 1.6F, false);
		int y = kiri.y0() + kartuH + (ringkas ? 4 : 6);
		l.keterangan(TeksTempat.k("bank.di_tas"), kiri.x0(), y, kiri.w(), gaya.teksLemah);
		y += 9;
		int[] batu = lembar.ia(LembarKunci.BATU);
		int bx = kiri.x0();
		for (int t = 0; t < 4; t++) {
			int n = t < batu.length ? batu[t] : 0;
			IkonTempat.gambar(l.g, LembarKunci.Ikon.BATU_RENDAH + t, bx, y, 12, n > 0 ? 0xFFFFFFFF : 0x60FFFFFF);
			Component c = Component.literal(TeksTempat.angka(n));
			Brush.text(l.g, font, c, bx + 13, y + 3, gaya.teks, 0.75F, false);
			bx += 16 + Math.round(font.width(c) * 0.75F) + 4;
		}
		y += ringkas ? 14 : 16;
		boolean adaBatu = false;
		for (int n : batu) {
			adaBatu |= n > 0;
		}
		tombolOp(l, "setor", kiri.x0(), y, kiri.w(), 15, TeksTempat.k("bank.setor_semua"), adaBatu, TeksTempat.k("bank.tanpa_batu"), Bank.OP_SETOR);
		y += ringkas ? 18 : 19;
		float fee = lembar.f(Bank.BIAYA_TARIK, 0.01F);
		l.keterangan(TeksTempat.k("bank.tarik_biaya", Math.round(fee * 100)), kiri.x0(), y, kiri.w(), gaya.teksLemah);
		y += ringkas ? 9 : 10;
		int w3 = (kiri.w() - 6) / 3;
		for (int i = 0; i < PRESET.length; i++) {
			long n = PRESET[i];
			long biaya = Math.max(1, Math.round(n * fee));
			tombolOp(l, "tarik" + i, kiri.x0() + i * (w3 + 3), y, w3, 14, Component.literal(TeksTempat.singkat(n)), saldo >= n + biaya,
					TeksTempat.k("bank.saldo_kurang_n", TeksTempat.angka(n + biaya)), LembarKunci.op(Bank.OP_TARIK, n));
		}
		y += ringkas ? 16 : 17;
		long semua = Math.max(0L, saldo - Math.max(1L, Math.round(saldo * fee / (1 + fee))));
		tombolOp(l, "tarik_semua", kiri.x0(), y, kiri.w(), 14, TeksTempat.k("bank.tarik_semua", TeksTempat.angka(semua)), semua > 0, TeksTempat.k(
				"bank.saldo_kurang"), LembarKunci.op(Bank.OP_TARIK, semua));
		if (tata == Tata.SEMPIT) {
			return;
		}
		// history
		Kotak kanan = k[1];
		List<Lembar> riwayat = lembar.daftar(LembarKunci.RIWAYAT);
		int ry = kanan.y0() + subjudul(l, TeksTempat.k("bank.riwayat"), kanan.x0(), kanan.y0(), kanan.x1());
		if (riwayat.isEmpty()) {
			l.keterangan(TeksTempat.k("bank.riwayat_kosong"), kanan.x0(), ry + 2, kanan.w(), gaya.teksSamar);
			return;
		}
		daftarRiwayat.gambar(l, "riwayat", kanan.x0(), ry, kanan.x1(), kanan.y1(), riwayat.size(), 12, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = riwayat.get(i);
			if (i % 2 == 0) {
				lk.g.fill(rx0, ry0, rx1, ry1, gaya.kartu);
			}
			lk.keterangan(TeksTempat.k("bank.hari", e.i(LembarKunci.HARI, 0)), rx0 + 2, ry0 + 2, 34, gaya.teksSamar);
			int w = LabelHarga.nbBertanda(lk, e.l(LembarKunci.NB, 0L), rx1 - 2, ry0 + 2, true, 0.75F);
			lk.keterangan(e.teks(LembarKunci.CATATAN), rx0 + 38, ry0 + 2, rx1 - rx0 - 44 - w, gaya.teksLemah);
		}, null, null);
	}

	private void wesel(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar w = lembar.sub(Bank.WESEL);
		long saldo = lembar.l(Bank.SALDO, 0L);
		float biaya = w.f(LembarKunci.BIAYA, 0.02F);
		int y = y0 + subjudul(l, TeksTempat.k("bank.wesel_tulis"), x0, y0, x1);
		l.bungkus(TeksTempat.k("bank.wesel_jelas", Math.round(biaya * 100)), x0, y, x1 - x0, gaya.teksLemah, 0.8F, 18);
		y += 20;
		int w3 = (x1 - x0 - 6) / 3;
		for (int i = 0; i < PRESET.length; i++) {
			long n = PRESET[i];
			long total = n + Math.max(1L, Math.round(n * biaya));
			tombolOp(l, "wesel" + i, x0 + i * (w3 + 3), y, w3, 15, TeksTempat.k("bank.wesel_nilai", TeksTempat.singkat(n)), saldo >= total,
					TeksTempat.k("bank.saldo_kurang"), LembarKunci.op(Bank.OP_WESEL, n));
		}
		y += 24;
		y += subjudul(l, TeksTempat.k("bank.wesel_cair"), x0, y, x1);
		Lembar tangan = w.sub(LembarKunci.TANGAN);
		if (tangan.ada(LembarKunci.NAMA)) {
			IkonTempat.gambar(l.g, IkonTempat.SURAT, x0, y, 16, 0xFFFFFFFF);
			l.teks(tangan.teks(LembarKunci.NAMA), x0 + 20, y + 4, x1 - x0 - 120, gaya.teks);
			LabelHarga.gambar(l, tangan.l(LembarKunci.NB, 0L), -1L, x1 - 80, y + 4, true, 1.0F, false);
			tombolOp(l, "cair", x1 - 74, y + 1, 74, 15, TeksTempat.k("bank.cairkan"), true, null, Bank.OP_CAIR);
		} else {
			l.keterangan(TeksTempat.k("bank.wesel_tak_dipegang"), x0, y + 2, x1 - x0, gaya.teksSamar);
		}
	}

	private void gadai(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar g = lembar.sub(Bank.GADAI);
		Kotak[] k = bagi(x0, y0, x1, y1);
		Kotak kiri = k[0];
		int y = kiri.y0() + subjudul(l, TeksTempat.k("bank.gadai_tangan"), kiri.x0(), kiri.y0(), kiri.x1());
		l.bungkus(TeksTempat.k("bank.gadai_syarat", Math.round(g.f(Bank.PINJAM, 0.4F) * 100), Math.round(g.f(Bank.TEBUS, 0.46F) * 100),
				g.i(LembarKunci.HARI, 7)), kiri.x0(), y, kiri.w(), gaya.teksLemah, 0.75F, 25);
		y += 26;
		Lembar tangan = g.sub(LembarKunci.TANGAN);
		if (tangan.ada(LembarKunci.NAMA)) {
			if (!tangan.barang().isEmpty()) {
				l.g.item(tangan.barang(), kiri.x0(), y);
			}
			l.teks(tangan.teks(LembarKunci.NAMA), kiri.x0() + 20, y + 4, kiri.w() - 20, gaya.teks);
			y += 20;
			y += barisNilai(l, TeksTempat.k("bank.nilai"), TeksTempat.nb(tangan.l(LembarKunci.NILAI, 0L), false), kiri.x0(), y, kiri.x1());
			y += barisNilai(l, TeksTempat.k("bank.pinjaman"), TeksTempat.nb(tangan.l(Bank.PINJAMAN, 0L), false), kiri.x0(), y, kiri.x1());
			tombolOp(l, "gadai", kiri.x0(), y + 3, kiri.w(), 15, TeksTempat.k("bank.gadaikan"), tangan.l(Bank.PINJAMAN, 0L) > 0,
					TeksTempat.k("bank.tak_bernilai"), Bank.OP_GADAI);
		} else {
			l.keterangan(TeksTempat.k("bank.pegang_barang"), kiri.x0(), y, kiri.w(), gaya.teksSamar);
		}
		Kotak kanan = k[1];
		List<Lembar> daftar = g.daftar(LembarKunci.DAFTAR);
		int ry = kanan.y0() + subjudul(l, TeksTempat.k("bank.digadai"), kanan.x0(), kanan.y0(), kanan.x1());
		if (daftar.isEmpty()) {
			l.keterangan(TeksTempat.k("bank.gadai_kosong"), kanan.x0(), ry + 2, kanan.w(), gaya.teksSamar);
			return;
		}
		daftarGadai.gambar(l, "digadai", kanan.x0(), ry, kanan.x1(), kanan.y1(), daftar.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = daftar.get(i);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			if (!e.barang().isEmpty()) {
				lk.g.item(e.barang(), rx0 + 3, ry0 + 4);
			}
			lk.teks(e.teks(LembarKunci.NAMA), rx0 + 22, ry0 + 3, rx1 - rx0 - 84, gaya.teks, 0.85F);
			int hari = e.i(LembarKunci.SISA_HARI, 0);
			lk.keterangan(TeksTempat.k("bank.sisa_hari", hari), rx0 + 22, ry0 + 14, rx1 - rx0 - 84, hari <= 1 ? 0xFFFF8A7A : gaya.teksSamar);
			long tebus = e.l(Bank.TEBUS_NB, 0L);
			tombolOp(lk, "tebus" + i, rx1 - 60, ry0 + 5, 56, 15, TeksTempat.k("bank.tebus", TeksTempat.singkat(tebus)), uang() < 0 || tebus <= uang()
					|| tebus <= lembar.l(Bank.SALDO, 0L), TeksTempat.k("harga.kurang", TeksTempat.angka(tebus - Math.max(0L, uang()))),
					LembarKunci.op(Bank.OP_TEBUS, e.id()));
		}, null, null);
	}

	private void tukar(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar t = lembar.sub(Bank.TUKAR);
		float cx = (x0 + x1) / 2.0F;
		IkonTempat.gambar(l.g, LembarKunci.Ikon.BATU_ASAL, Math.round(cx - 16), y0 + 2, 32, 0xFFFFFFFF);
		int y = y0 + 38;
		Brush.centered(l.g, font, TeksTempat.k("bank.batu_asal_di_tas", t.i(Bank.BATU_ASAL, 0)), cx, y, gaya.teks, 1.0F, false);
		y += 14;
		int col = Math.min(140, (x1 - x0 - 8) / 2);
		int lx = Math.round(cx - col - 4);
		int rx = Math.round(cx + 4);
		long beli = t.l(Bank.BELI, 180L);
		long jual = t.l(Bank.JUAL, 90L);
		subjudul(l, TeksTempat.k("bank.beli_asal"), lx, y, lx + col);
		subjudul(l, TeksTempat.k("bank.jual_asal"), rx, y, rx + col);
		y += 14;
		LabelHarga.gambar(l, beli, uang(), lx, y, false, 1.0F, false);
		l.keterangan(TeksTempat.k("bank.sisa_minggu", t.i(Bank.BELI_SISA, 0)), lx, y + 10, col, gaya.teksSamar);
		LabelHarga.gambar(l, jual, -1L, rx, y, false, 1.0F, false);
		l.keterangan(TeksTempat.k("bank.sisa_hari_ini", t.i(Bank.JUAL_SISA, 0)), rx, y + 10, col, gaya.teksSamar);
		y += 24;
		int n = jumlahTukar.nilai();
		jumlahTukar.gambar(l, "jumlah", Math.round(cx - 25), y, Math.max(1, Math.max(t.i(Bank.BELI_SISA, 0), t.i(Bank.JUAL_SISA, 0))));
		y += 18;
		boolean bolehBeli = t.b(Bank.BOLEH_BELI) && t.i(Bank.BELI_SISA, 0) >= n && (uang() < 0 || beli * n <= uang());
		Component alasanBeli = t.b(Bank.BOLEH_BELI) ? TeksTempat.k("bank.batas_tukar") : t.teks(LembarKunci.ALASAN);
		tombolOp(l, "tukar_beli", lx, y, col, 15, TeksTempat.k("bank.beli_n", n, TeksTempat.angka(beli * n)), bolehBeli, alasanBeli,
				LembarKunci.op(Bank.OP_TUKAR_BELI, n));
		boolean bolehJual = t.i(Bank.JUAL_SISA, 0) >= n && t.i(Bank.BATU_ASAL, 0) >= n;
		tombolOp(l, "tukar_jual", rx, y, col, 15, TeksTempat.k("bank.jual_n", n, TeksTempat.angka(jual * n)), bolehJual, TeksTempat.k("bank.batas_jual"),
				LembarKunci.op(Bank.OP_TUKAR_JUAL, n));
	}

	private void brankas(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar b = lembar.sub(Bank.BRANKAS);
		int slots = Math.max(1, b.i(LembarKunci.SLOT, 9));
		List<Lembar> isi = b.daftar(LembarKunci.DAFTAR);
		int y = y0 + subjudul(l, TeksTempat.k("bank.brankas", isi.size(), slots), x0, y0, x1);
		int cell = 22;
		int cols = 9;
		int gridW = cols * cell;
		int gx = (x0 + x1 - gridW) / 2;
		// the iron vault door frame
		l.g.fill(gx - 4, y - 2, gx + gridW + 4, y + (slots / cols) * cell + 4, Palette.argb(0xD0, 0x2A2E32));
		Brush.frame(l.g, gx - 4, y - 2, gx + gridW + 4, y + (slots / cols) * cell + 4, TempatWarna.EMAS_GELAP);
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
				l.g.item(item.barang(), cx + 2, cy + 2);
				l.g.itemDecorations(font, item.barang(), cx + 2, cy + 2);
				final Lembar it = item;
				final int slot = s;
				l.zona("slot" + s, cx, cy, cx + cell - 2, cy + cell - 2, () -> {
					if (tulisBoleh()) {
						kirim(LembarKunci.op(Bank.OP_AMBIL, slot));
					}
				}, tulisBoleh(), alasanBaca(), true);
				if (over) {
					l.tip(List.of(it.teks(LembarKunci.NAMA), TeksTempat.k("bank.klik_ambil")));
				}
				l.fokusCincin("slot" + s, cx, cy, cx + cell - 2, cy + cell - 2);
			}
		}
		int by = y + (slots / cols) * cell + 12;
		tombolOp(l, "simpan_barang", gx, Math.min(by, y1 - 16), gridW, 15, TeksTempat.k("bank.simpan_tangan"), isi.size() < slots, TeksTempat.k("bank.brankas_penuh"),
				Bank.OP_SIMPAN);
	}
}
