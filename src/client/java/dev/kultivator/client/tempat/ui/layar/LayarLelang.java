package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.AngkaGulir;
import dev.kultivator.client.tempat.ui.kit.CincinWaktu;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.KartuBarang;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.PapanSkor;
import dev.kultivator.client.tempat.ui.kit.Pratinjau3D;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Lelang;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code lelang} (DESIGN 38.U.3.3): the town Balai Lelang and the capital's Lelang Agung, live. Tablet "Lot": the lot
 * (number of total, name, seller, base price, the turning preview or a sealed mystery box), the current price rolling up
 * with its leader, the lot's countdown ring, the bid board (newest first, a new bid flashes gold), and bid buttons for each
 * step (Space = the first step). States: not yet started (schedule and countdown), between lots, sold (the red "TERJUAL"
 * seal spins in). Tablet "Titip": the item in hand with its estimate and three reserve choices, and the consigned items
 * with their status. Tablet "Hasil": won lots, consignment income and what is owed.
 */
public final class LayarLelang extends LayarTempatDasar {

	private final Pratinjau3D pratinjau = new Pratinjau3D();
	private final PapanSkor papan = new PapanSkor();
	private final AngkaGulir hargaGulir = new AngkaGulir();
	private final DaftarGulir daftarTitip = new DaftarGulir();
	private final DaftarGulir daftarHasil = new DaftarGulir();
	private int lotTerakhir = -1;
	private long terjualSejak = -1L;

	public LayarLelang(int npcId, CompoundTag lembar) {
		super(Lelang.LAYAR, npcId, lembar, TeksTempat.k("layar.lelang"), TempatWarna.Gaya.KAIN);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(tabBaru(Lelang.TAB_LOT, "tab.lot", LembarKunci.Ikon.LELANG), tabBaru(Lelang.TAB_TITIP, "tab.titip", LembarKunci.Ikon.TIMBANGAN),
				tabBaru(Lelang.TAB_HASIL, "tab.hasil", IkonTempat.GULUNGAN));
	}

	@Override
	protected void setelahLembar(boolean buka) {
		Lembar lot = lembar.sub(Lelang.LOT);
		int no = lot.i(LembarKunci.NO, -1);
		if (no != lotTerakhir) {
			lotTerakhir = no;
			terjualSejak = -1L;
		}
		if (lot.b(Lelang.TERJUAL) && terjualSejak < 0L) {
			terjualSejak = Util.getMillis();
			UiSound.ask();
		}
	}

	@Override
	protected Component petunjuk() {
		return Lelang.TAB_LOT.equals(tab) ? TeksTempat.k("kunci.tawar") : null;
	}

	@Override
	protected boolean spasi() {
		if (!Lelang.TAB_LOT.equals(tab) || !bolehTawar()) {
			return false;
		}
		int[] langkah = lembar.ia(Lelang.LANGKAH);
		kirim(LembarKunci.op(Lelang.OP_TAWAR, langkah.length > 0 ? langkah[0] : 5));
		return true;
	}

	private boolean bolehTawar() {
		Lembar lot = lembar.sub(Lelang.LOT);
		return Lelang.S_BERJALAN.equals(lembar.s(LembarKunci.STATUS)) && lot.ada(LembarKunci.NO) && !lot.b(Lelang.TERJUAL) && !lot.b(LembarKunci.ANDA)
				&& tulisBoleh();
	}

	@Override
	protected void isi(Lukis l, int x0, int y0, int x1, int y1) {
		switch (tab) {
			case Lelang.TAB_TITIP -> titip(l, x0, y0, x1, y1);
			case Lelang.TAB_HASIL -> hasil(l, x0, y0, x1, y1);
			default -> lot(l, x0, y0, x1, y1);
		}
	}

	private void lot(Lukis l, int x0, int y0, int x1, int y1) {
		String status = lembar.s(LembarKunci.STATUS, Lelang.S_BELUM);
		long sisa = lembar.l(LembarKunci.SISA_TICK, 0L);
		boolean agung = Lelang.PROFIL_AGUNG.equals(lembar.s(Lelang.PROFIL));
		if (Lelang.S_BELUM.equals(status) || Lelang.S_SELESAI.equals(status)) {
			float cx = (x0 + x1) / 2.0F;
			IkonTempat.gambar(l.g, LembarKunci.Ikon.LELANG, Math.round(cx - 16), y0 + 10, 32, 0xFFFFFFFF);
			Component judul = TeksTempat.k(Lelang.S_BELUM.equals(status) ? "lelang.belum" : "lelang.selesai");
			Brush.centered(l.g, font, Brush.bold(judul), cx, y0 + 48, gaya.teks, 1.0F, false);
			Component jadwal = lembar.teksAtau(Lelang.JADWAL);
			if (jadwal != null) {
				Brush.fitCentered(l.g, font, jadwal, cx, y0 + 60, x1 - x0 - 20, gaya.teksLemah, 1.0F, 0.6F, false);
			}
			if (Lelang.S_BELUM.equals(status) && sisa > 0) {
				CincinWaktu.gambar(l, cx, y0 + 92, 16.0F, sisa, 0L, diterima, CincinWaktu.DALAM);
			}
			if (agung) {
				l.keterangan(TeksTempat.k("lelang.komisi", Math.round(lembar.f(Lelang.KOMISI, 0.1F) * 100)), x0 + 4, y1 - 10, x1 - x0, gaya.teksSamar);
			}
			return;
		}
		Lembar lot = lembar.sub(Lelang.LOT);
		Kotak[] k = bagi(x0, y0, x1, y1);
		boolean sempit = tata == Tata.SEMPIT;
		// left: lot card
		Kotak kiri = sempit ? new Kotak(x0, y0, x1, y0 + 70) : k[0];
		int lx0 = kiri.x0();
		int ly = kiri.y0();
		Component lotNo = TeksTempat.k("lelang.lot_no", lot.i(LembarKunci.NO, 1), lot.i(Lelang.TOTAL, 1));
		l.teks(Brush.bold(lotNo), lx0, ly, kiri.w(), TempatWarna.EMAS_TERANG);
		ly += 11;
		l.teks(Brush.bold(lot.teks(LembarKunci.NAMA)), lx0, ly, kiri.w(), TempatWarna.mutu(lot.i(LembarKunci.MUTU, -1), true, gaya.teks));
		ly += 11;
		Component penjual = lot.teksAtau(Lelang.PENJUAL);
		if (penjual != null) {
			l.keterangan(TeksTempat.k("lelang.penjual", penjual), lx0, ly, kiri.w(), gaya.teksLemah);
			ly += 9;
		}
		l.keterangan(TeksTempat.k("lelang.dasar", TeksTempat.angka(lot.l(Lelang.DASAR, 0L))), lx0, ly, kiri.w(), gaya.teksLemah);
		ly += 11;
		int pv = sempit ? 0 : Math.min(kiri.w() - 8, Math.min(kiri.y1() - ly - 4, tata == Tata.LEBAR ? 110 : 80));
		if (pv >= 32) {
			int px = lx0 + (kiri.w() - pv) / 2;
			if (lot.b(Lelang.MISTERI)) {
				kotakMisteri(l, px, ly, pv);
			} else {
				pratinjau.gambar(l, "pratinjau", px, ly, px + pv, ly + pv, lot.barang(), null, LembarKunci.Ikon.LELANG);
			}
			if (lot.b(Lelang.TERJUAL)) {
				float t = (Util.getMillis() - terjualSejak) / 350.0F;
				KartuBarang.cap(l, TeksTempat.k("lelang.terjual"), px + pv / 2.0F, ly + pv / 2.0F, pv * 0.9F, t);
			}
		}
		// right: price, leader, ring, bid board, bid buttons
		Kotak kanan = sempit ? new Kotak(x0, y0 + 72, x1, y1) : k[1];
		PanelTempat.kartu(l.g, gaya, kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1(), 0.0F, false);
		int rx0 = kanan.x0() + 5;
		int rx1 = kanan.x1() - 5;
		int ry = kanan.y0() + 4;
		long kini = lot.l(Lelang.KINI, lot.l(Lelang.DASAR, 0L));
		long shown = hargaGulir.nilai(kini, l.kini);
		l.keterangan(TeksTempat.k("lelang.harga_kini"), rx0, ry, rx1 - rx0 - 40, gaya.teksLemah);
		long lotSisa = lot.l(LembarKunci.SISA_TICK, sisa);
		if (!lot.b(Lelang.TERJUAL)) {
			CincinWaktu.gambar(l, rx1 - 12, ry + 12, 12.0F, lotSisa, 0L, diterima, CincinWaktu.DALAM);
		}
		ry += 9;
		LabelHarga.gambar(l, shown, -1L, rx0, ry, false, 1.5F, false);
		if (hargaGulir.bergulir(l.kini)) {
			l.g.fill(rx0, ry + 13, rx0 + 60, ry + 14, Palette.argb(0xC0, TempatWarna.EMAS_TERANG & 0xFFFFFF));
		}
		ry += 16;
		Component pemimpin = lot.teksAtau(Lelang.PEMIMPIN);
		if (lot.b(LembarKunci.ANDA)) {
			l.teks(TeksTempat.k("lelang.anda_memimpin"), rx0, ry, rx1 - rx0 - 28, 0xFF9AE0B0, 0.85F);
		} else if (pemimpin != null) {
			l.teks(TeksTempat.k("lelang.oleh", pemimpin), rx0, ry, rx1 - rx0 - 28, gaya.teksLemah, 0.85F);
		}
		ry += 12;
		if (Lelang.S_JEDA.equals(status)) {
			l.keterangan(TeksTempat.k("lelang.jeda", TeksTempat.waktu(CincinWaktu.sisa(sisa, diterima, l.kini))), rx0, ry, rx1 - rx0, gaya.teksSamar);
			ry += 10;
		}
		ry += subjudul(l, TeksTempat.k("lelang.papan"), rx0, ry, rx1);
		List<Lembar> tawaran = lembar.daftar(Lelang.TAWARAN);
		int rows = Math.max(1, (kanan.y1() - 22 - ry) / 10);
		if (tawaran.isEmpty()) {
			l.keterangan(TeksTempat.k("lelang.belum_ada_tawaran"), rx0, ry + 2, rx1 - rx0, gaya.teksSamar);
		} else {
			papan.gambar(l, tawaran, LembarKunci.NB, rx0, ry, rx1, 10, Math.min(8, rows), true);
		}
		// bid buttons
		int[] langkah = lembar.ia(Lelang.LANGKAH);
		if (langkah.length == 0) {
			langkah = new int[] {5, 10, 25};
		}
		int by = kanan.y1() - 18;
		int bw = (rx1 - rx0 - (langkah.length - 1) * 3) / langkah.length;
		boolean boleh = bolehTawar();
		long min = lembar.l(Lelang.TAWAR_MIN, kini);
		for (int i = 0; i < langkah.length; i++) {
			long tawar = Math.max(min, Math.round(kini * (1.0D + langkah[i] / 100.0D)));
			boolean cukup = uang() < 0 || tawar <= uang();
			Component alasan = !boleh ? (lot.b(LembarKunci.ANDA) ? TeksTempat.k("lelang.anda_memimpin") : TeksTempat.k("lelang.tidak_bisa"))
					: TeksTempat.k("harga.kurang", TeksTempat.angka(tawar - Math.max(0L, uang())));
			Component label = TeksTempat.k("lelang.tawar", langkah[i], TeksTempat.singkat(tawar));
			tombolOp(l, "tawar" + i, rx0 + i * (bw + 3), by, bw, 15, label, boleh && cukup, alasan, LembarKunci.op(Lelang.OP_TAWAR, langkah[i]));
		}
	}

	private void kotakMisteri(Lukis l, int x, int y, int size) {
		float bob = (float) Math.sin(Util.getMillis() / 400.0D) * 1.5F;
		int s = Math.round(size * 0.7F);
		int bx = x + (size - s) / 2;
		int by = Math.round(y + (size - s) / 2.0F + bob);
		l.g.fill(bx + 2, by + 3, bx + s + 2, by + s + 3, 0x50000000);
		l.g.fillGradient(bx, by, bx + s, by + s, 0xFF5A1E1E, 0xFF2E0E0E);
		l.g.fill(bx + s / 2 - 2, by, bx + s / 2 + 2, by + s, TempatWarna.EMAS);
		l.g.fill(bx, by + s / 2 - 2, bx + s, by + s / 2 + 2, TempatWarna.EMAS);
		Brush.frame(l.g, bx, by, bx + s, by + s, TempatWarna.EMAS_GELAP);
		IkonTempat.gambar(l.g, IkonTempat.TANYA, bx + s / 2 - 8, by + s / 2 - 8, 16, 0xFFFFFFFF);
		if (l.di(bx, by, bx + s, by + s)) {
			l.tip(TeksTempat.k("lelang.misteri"));
		}
	}

	private void titip(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar titip = lembar.sub(Lelang.TITIP);
		Kotak[] k = bagi(x0, y0, x1, y1);
		Kotak kiri = k[0];
		int y = kiri.y0() + subjudul(l, TeksTempat.k("lelang.titip_judul"), kiri.x0(), kiri.y0(), kiri.x1());
		Lembar tangan = titip.sub(LembarKunci.TANGAN);
		if (tangan.ada(LembarKunci.NAMA)) {
			if (!tangan.barang().isEmpty()) {
				l.g.item(tangan.barang(), kiri.x0(), y);
			}
			l.teks(tangan.teks(LembarKunci.NAMA), kiri.x0() + 20, y + 4, kiri.w() - 20, gaya.teks);
			y += 20;
			long kira = tangan.l(Lelang.PERKIRAAN, 0L);
			y += barisNilai(l, TeksTempat.k("lelang.perkiraan"), TeksTempat.nb(kira, false), kiri.x0(), y, kiri.x1());
			l.keterangan(TeksTempat.k("lelang.pilih_dasar"), kiri.x0(), y + 2, kiri.w(), gaya.teksSamar);
			y += 12;
			int[] persen = {80, 100, 120};
			boolean bisa = titip.b(LembarKunci.BISA);
			for (int i = 0; i < persen.length; i++) {
				long dasar = Math.max(1L, kira * persen[i] / 100L);
				tombolOp(l, "titip" + i, kiri.x0(), y, kiri.w(), 14, TeksTempat.k("lelang.titip_dasar", persen[i], TeksTempat.angka(dasar)), bisa,
						titip.teksAtau(LembarKunci.ALASAN), LembarKunci.op(Lelang.OP_TITIP, dasar));
				y += 16;
			}
		} else {
			l.bungkus(titip.ada(LembarKunci.ALASAN) ? titip.teks(LembarKunci.ALASAN) : TeksTempat.k("lelang.pegang_barang"), kiri.x0(), y + 2, kiri.w(),
					gaya.teksSamar, 0.85F, kiri.y1() - 12 - y);
		}
		l.keterangan(TeksTempat.k("lelang.titip_maks", lembar.sub(Lelang.TITIP).daftar(LembarKunci.DAFTAR).size(), titip.i(LembarKunci.MAKS, 3)),
				kiri.x0(), kiri.y1() - 9, kiri.w(), gaya.teksSamar);
		// right: consigned items
		Kotak kanan = k[1];
		List<Lembar> daftar = titip.daftar(LembarKunci.DAFTAR);
		int ry = kanan.y0() + subjudul(l, TeksTempat.k("lelang.titipan"), kanan.x0(), kanan.y0(), kanan.x1());
		if (daftar.isEmpty()) {
			kosong(l, TeksTempat.k("lelang.titipan_kosong"), kanan.x0(), ry, kanan.x1(), kanan.y1());
			return;
		}
		daftarTitip.gambar(l, "titipan", kanan.x0(), ry, kanan.x1(), kanan.y1(), daftar.size(), 24, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = daftar.get(i);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			if (!e.barang().isEmpty()) {
				lk.g.item(e.barang(), rx0 + 3, ry0 + 3);
			}
			lk.teks(e.teks(LembarKunci.NAMA), rx0 + 22, ry0 + 3, rx1 - rx0 - 82, gaya.teks, 0.85F);
			String st = e.s(LembarKunci.STATUS, Lelang.T_ANTRE);
			Component status = TeksTempat.k("lelang.status." + st);
			int sc = switch (st) {
				case Lelang.T_TERJUAL -> 0xFF9AE0B0;
				case Lelang.T_KEMBALI -> 0xFFFFB07A;
				case Lelang.T_DILELANG -> TempatWarna.EMAS_TERANG;
				default -> gaya.teksSamar;
			};
			lk.keterangan(st.equals(Lelang.T_TERJUAL) ? TeksTempat.k("lelang.terjual_seharga", TeksTempat.angka(e.l(Lelang.HASIL, 0L))) : status, rx0 + 22,
					ry0 + 13, rx1 - rx0 - 82, sc);
			if (Lelang.T_ANTRE.equals(st)) {
				tombolOp(lk, "tarik" + i, rx1 - 56, ry0 + 4, 52, 15, TeksTempat.k("lelang.tarik"), true, null, LembarKunci.op(Lelang.OP_TARIK, e.id()));
			}
		}, null, null);
	}

	private void hasil(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> hasil = lembar.daftar(Lelang.HASIL);
		long utang = lembar.l(Lelang.UTANG, 0L);
		int y = y0;
		if (utang > 0) {
			PanelTempat.kartu(l.g, gaya, x0, y, x1, y + 22, 0.0F, false);
			l.teks(TeksTempat.k("lelang.utang"), x0 + 5, y + 7, x1 - x0 - 130, gaya.teks);
			LabelHarga.gambar(l, utang, -1L, x1 - 76, y + 7, true, 1.0F, false);
			tombolOp(l, "ambil_hasil", x1 - 70, y + 4, 66, 15, TeksTempat.k("lelang.ambil"), true, null, Lelang.OP_AMBIL_HASIL);
			y += 26;
		}
		if (hasil.isEmpty()) {
			kosong(l, TeksTempat.k("lelang.hasil_kosong"), x0, y, x1, y1);
			return;
		}
		daftarHasil.gambar(l, "hasil", x0, y, x1, y1, hasil.size(), 18, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = hasil.get(i);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			String jenis = e.s(LembarKunci.JENIS, Lelang.H_MENANG);
			int ikon = switch (jenis) {
				case Lelang.H_TITIP -> LembarKunci.Ikon.TIMBANGAN;
				case Lelang.H_UTANG -> LembarKunci.Ikon.GEMBOK;
				default -> IkonTempat.PIALA;
			};
			IkonTempat.gambar(lk.g, ikon, rx0 + 3, ry0 + 1, 16, 0xFFFFFFFF);
			lk.teks(e.teks(LembarKunci.NAMA), rx0 + 23, ry0 + 5, rx1 - rx0 - 140, gaya.teks, 0.85F);
			lk.keterangan(TeksTempat.k("lelang.hasil." + jenis), rx1 - 116, ry0 + 6, 50, gaya.teksSamar);
			LabelHarga.nbBertanda(lk, Lelang.H_MENANG.equals(jenis) ? -e.l(LembarKunci.NB, 0L) : e.l(LembarKunci.NB, 0L), rx1 - 4, ry0 + 5, true, 0.85F);
		}, null, null);
	}
}
