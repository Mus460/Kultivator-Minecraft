package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.CincinWaktu;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KartuBarang;
import dev.kultivator.client.screen.ui.LencanaPangkat;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.PemilihJumlah;
import dev.kultivator.client.screen.ui.Pratinjau3D;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Gilda;
import dev.kultivator.dunia.tempat.ui.LembarKunci.TokoSenjata;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code gilda_alkemis} / {@code gilda_tempa} (DESIGN 38.U.3.9): the Alchemist and Forge guilds. The guild tier heads
 * every sub. "Ujian": the next tier exam with its checklist and fee (5 × n²). "Toko": guild goods, tier-gated ones locked
 * with their tier. "Pesanan": orders with countdown rings and "Ambil". "Nilai": free appraisals left today for the item
 * in hand. Single kit, text 1.0.
 */
public final class LayarGilda extends LayarInti {

	private final DaftarGulir toko = new DaftarGulir();
	private final DaftarGulir pesanan = new DaftarGulir();
	private final Pratinjau3D pratinjau = new Pratinjau3D();
	private final PemilihJumlah jumlah = new PemilihJumlah();
	private final boolean tempa;

	public LayarGilda(String layar, int npcId, CompoundTag lembar) {
		super(layar, npcId, lembar, k("layar." + layar), LembarKunci.GAYA_BATU);
		this.tempa = Gilda.LAYAR_TEMPA.equals(layar);
	}

	@Override
	protected List<Tab> daftarTab() {
		int ikon = Gilda.LAYAR_TEMPA.equals(layar) ? LembarKunci.Ikon.GILDA_TEMPA : LembarKunci.Ikon.GILDA_ALKEMIS;
		return List.of(tab(Gilda.TAB_UJIAN, "tab.ujian", LembarKunci.Ikon.bintang(1)), tab(Gilda.TAB_TOKO, "tab.toko", ikon),
				tab(Gilda.TAB_PESANAN, "tab.pesanan", LembarKunci.Ikon.JAM_PASIR), tab(Gilda.TAB_NILAI, "tab.nilai", LembarKunci.Ikon.MATA_BATIN));
	}

	@Override
	protected void tabBerubah() {
		long now = Util.getMillis();
		toko.mulaiUlang(now);
		pesanan.mulaiUlang(now);
		jumlah.set(1);
	}

	@Override
	public boolean lebar() {
		return false;
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		int tingkat = lembar.i(Gilda.TINGKAT, 0);
		Glyphs.Atlas.ikon(l.g, tempa ? LembarKunci.Ikon.GILDA_TEMPA : LembarKunci.Ikon.GILDA_ALKEMIS, x0, y0, 14, 0xFFFFFFFF);
		Component nama = lembar.ada(Gilda.TINGKAT_NAMA) ? lembar.teks(Gilda.TINGKAT_NAMA) : k("gilda.tingkat_n", tingkat);
		int bintangW = 9 * 9 + 1;
		l.teks(Brush.bold(k("gilda.tingkat_anda", nama)), x0 + 18, y0 + 3, x1 - x0 - 24 - bintangW, l.gaya.teks);
		LencanaPangkat.bintang(l, Math.min(9, tingkat), 9, x1 - bintangW, y0 + 2, 10);
		l.garis(x0, x1, y0 + 16);
		int y = y0 + 20;
		switch (tab) {
			case Gilda.TAB_TOKO -> toko(l, x0, y, x1, y1);
			case Gilda.TAB_PESANAN -> pesanan(l, x0, y, x1, y1);
			case Gilda.TAB_NILAI -> nilai(l, x0, y, x1, y1);
			default -> ujian(l, x0, y, x1, y1);
		}
	}

	private void ujian(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar u = lembar.sub(LembarKunci.UJIAN);
		if (!u.ada(LembarKunci.TARGET)) {
			kosong(l, k("gilda.ujian_puncak"), x0, y0, x1, y1);
			return;
		}
		float cx = (x0 + x1) / 2.0F;
		l.tengah(Brush.bold(k("gilda.ujian_tingkat", u.i(LembarKunci.TARGET, 1))), cx, y0 + 2, x1 - x0 - 8, l.gaya.teks);
		int y = y0 + 15;
		int lx0 = Math.round(cx - Math.min(130, (x1 - x0) / 2.0F - 6));
		int lx1 = Math.round(cx + Math.min(130, (x1 - x0) / 2.0F - 6));
		List<Lembar> syarat = u.daftar(LembarKunci.SYARAT);
		int langkah = 11;
		// rows stop above the fee line and the button; the rest is counted
		int by = y1 - TOMBOL_H;
		int biayaY = by - 13;
		for (int i = 0; i < syarat.size(); i++) {
			Lembar s = syarat.get(i);
			if (y + langkah > biayaY - 1 && i < syarat.size() - 1) {
				l.samar(k("gilda.syarat_lagi", syarat.size() - i), lx0 + 14, y, lx1 - lx0 - 14);
				break;
			}
			boolean ok = s.b(LembarKunci.OK);
			Glyphs.Atlas.ikon(l.g, ok ? Glyphs.Atlas.CENTANG : Glyphs.Atlas.SILANG, lx0, y, 9, 0xFFFFFFFF);
			l.teks(s.teks(LembarKunci.TEKS), lx0 + 13, y, lx1 - lx0 - 13, ok ? l.gaya.teks : l.gaya.teksLemah);
			y += langkah;
		}
		nilai(l, k("gilda.biaya_ujian"), Brush.nb(u.l(LembarKunci.BIAYA, 0L), false), lx0, biayaY, lx1);
		tombolOp(l, "ujian", lx0, by, lx1 - lx0, TOMBOL_H, k("gilda.ikut_ujian"), u.b(LembarKunci.BISA), u.teksAtau(LembarKunci.ALASAN), Gilda.OP_UJIAN);
	}

	private void toko(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Gilda.TOKO);
		if (rows.isEmpty()) {
			kosong(l, k("gilda.toko_kosong"), x0, y0, x1, y1);
			return;
		}
		Kotak[] k = bagi(x0, y0, x1, y1);
		long uang = uang();
		long muncul = diterima;
		toko.gambar(l, "toko", k[0].x0(), k[0].y0(), k[0].x1(), k[0].y1(), rows.size(), 22,
				(lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> KartuBarang.gambar(lk, rows.get(i), rx0, ry0, rx1, ry1, sorot, pilih, uang, muncul), () -> {
					// Enter / second click on the selected card buys the chosen amount
					int i = toko.pilihan();
					if (i >= 0 && i < rows.size() && rows.get(i).bisa() && tulisBoleh()) {
						kirim(LembarKunci.op(Gilda.OP_BELI, rows.get(i).id(), jumlah.nilai()));
					}
				}, i -> jumlah.set(1));
		int sel = toko.pilihan();
		Lembar e = sel >= 0 && sel < rows.size() ? rows.get(sel) : null;
		Kotak d = k[1];
		l.kartu(d.x0(), d.y0(), d.x1(), d.y1(), 0.0F, false);
		if (e == null) {
			return;
		}
		int by = d.y1() - TOMBOL_H - 4;
		int pv = tata() == Tata.SEMPIT ? 30 : Math.max(16, Math.min(d.w() - 10, Math.min(d.h() - 52, 72)));
		int px = d.x0() + (d.w() - pv) / 2;
		pratinjau.gambar(l, "pratinjau", px, d.y0() + 4, px + pv, d.y0() + 4 + pv, e.barang(), null, e.i(LembarKunci.IKON, -1));
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), d.x0() + 5, Math.min(d.y0() + pv + 8, by - 11), d.w() - 10, mutu(l, e.i(LembarKunci.MUTU, -1)));
		int stok = e.i(LembarKunci.STOK, -1);
		int maks = stok < 0 ? 64 : Math.max(1, stok);
		int pw = PemilihJumlah.lebar(l, maks);
		jumlah.gambar(l, "jumlah", d.x1() - 5 - pw, by, maks);
		long total = e.l(LembarKunci.HARGA, 0L) * jumlah.nilai();
		boolean cukup = uang() < 0 || total <= uang();
		Component alasan = e.teksAtau(LembarKunci.ALASAN);
		if (alasan == null && !cukup) {
			alasan = k("harga.kurang", Brush.angka(total - Math.max(0L, uang())));
		}
		tombolOp(l, "beli", d.x0() + 4, by, d.w() - pw - 13, TOMBOL_H, k("toko.beli", Brush.angka(total)), e.bisa() && cukup, alasan,
				LembarKunci.op(Gilda.OP_BELI, e.id(), jumlah.nilai()));
	}

	private void pesanan(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Gilda.PESANAN);
		if (rows.isEmpty()) {
			kosong(l, k("gilda.pesanan_kosong"), x0, y0, x1, y1);
			return;
		}
		pesanan.gambar(l, "pesanan", x0, y0, x1, y1, rows.size(), 25, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar o = rows.get(i);
			boolean siap = o.b(TokoSenjata.SIAP);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			if (!o.barang().isEmpty()) {
				lk.g.item(o.barang(), rx0 + 3, ry0 + 4);
			}
			int bw = 60;
			lk.teks(o.teks(LembarKunci.NAMA), rx0 + 23, ry0 + 3, rx1 - rx0 - 30 - bw, mutu(lk, o.i(LembarKunci.MUTU, -1)));
			lk.teks(siap ? k("gilda.siap") : k("gilda.dikerjakan"), rx0 + 23, ry0 + 14, rx1 - rx0 - 30 - bw, siap ? lk.gaya.naik() : lk.gaya.teksSamar);
			if (siap) {
				tombolOp(lk, "ambil" + i, rx1 - bw - 4, ry0 + (ry1 - ry0 - TOMBOL_H) / 2, bw, TOMBOL_H, k("toko.ambil"), true, null,
						LembarKunci.op(Gilda.OP_AMBIL, o.id()));
			} else {
				CincinWaktu.gambar(lk, rx1 - 12, ry0 + 12, 8.0F, o.l(LembarKunci.SISA_TICK, 0L), 0L, diterima, CincinWaktu.KIRI);
			}
		}, null, null);
	}

	private void nilai(Lukis l, int x0, int y0, int x1, int y1) {
		int sisa = lembar.i(Gilda.NILAI_SISA, 0);
		float cx = (x0 + x1) / 2.0F;
		// laid out from the bottom up so the button never leaves the panel (scale 4)
		int by = y1 - TOMBOL_H;
		int sy = by - 13;
		boolean ringkas = y1 - y0 < 120;
		int ikon = ringkas ? 20 : 32;
		Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.MATA_BATIN, Math.round(cx - ikon / 2.0F), y0 + (ringkas ? 2 : 6), ikon, 0xFFFFFFFF);
		int ty = y0 + (ringkas ? 26 : 46);
		l.bungkus(k(tempa ? "gilda.nilai_jelas_tempa" : "gilda.nilai_jelas_alkemis"), x0 + 10, ty, x1 - x0 - 20, l.gaya.teksLemah, sy - 3 - ty);
		l.tengah(k("gilda.nilai_sisa", sisa), cx, sy, x1 - x0 - 8, sisa > 0 ? l.gaya.teks : l.gaya.turun());
		int bw = Math.min(140, x1 - x0 - 8);
		tombolOp(l, "nilai", Math.round(cx - bw / 2.0F), by, bw, TOMBOL_H, k("gilda.nilai_tangan"), sisa > 0, k("toko.nilai_habis"), Gilda.OP_NILAI);
	}
}
