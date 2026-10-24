package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.tempat.ui.kit.CincinWaktu;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.KartuBarang;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.LencanaPangkat;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.PemilihJumlah;
import dev.kultivator.client.tempat.ui.kit.Pratinjau3D;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Gilda;
import dev.kultivator.dunia.tempat.ui.LembarKunci.TokoSenjata;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code gilda_alkemis} / {@code gilda_tempa} (DESIGN 38.U.3.9): the Alchemist and Forge guilds. The guild tier heads
 * every tab. "Ujian": the next tier exam with its checklist and fee (5 × n²). "Toko": guild goods, tier-gated ones locked
 * with their tier. "Pesanan": orders with countdown rings and "Ambil". "Nilai": free appraisals left today for the item
 * in hand.
 */
public final class LayarGilda extends LayarTempatDasar {

	private final DaftarGulir toko = new DaftarGulir();
	private final DaftarGulir pesanan = new DaftarGulir();
	private final Pratinjau3D pratinjau = new Pratinjau3D();
	private final PemilihJumlah jumlah = new PemilihJumlah();
	private final boolean tempa;

	public LayarGilda(String layar, int npcId, CompoundTag lembar) {
		super(layar, npcId, lembar, TeksTempat.k("layar." + layar), TempatWarna.Gaya.BATU);
		this.tempa = Gilda.LAYAR_TEMPA.equals(layar);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		int ikon = tempa ? LembarKunci.Ikon.GILDA_TEMPA : LembarKunci.Ikon.GILDA_ALKEMIS;
		return List.of(tabBaru(Gilda.TAB_UJIAN, "tab.ujian", LembarKunci.Ikon.bintang(1)), tabBaru(Gilda.TAB_TOKO, "tab.toko", ikon),
				tabBaru(Gilda.TAB_PESANAN, "tab.pesanan", LembarKunci.Ikon.JAM_PASIR), tabBaru(Gilda.TAB_NILAI, "tab.nilai", LembarKunci.Ikon.MATA_BATIN));
	}

	@Override
	protected void tabBerubah() {
		long now = Util.getMillis();
		toko.mulaiUlang(now);
		pesanan.mulaiUlang(now);
		jumlah.set(1);
	}

	@Override
	protected void isi(Lukis l, int x0, int y0, int x1, int y1) {
		int tingkat = lembar.i(Gilda.TINGKAT, 0);
		IkonTempat.gambar(l.g, tempa ? LembarKunci.Ikon.GILDA_TEMPA : LembarKunci.Ikon.GILDA_ALKEMIS, x0, y0, 14, 0xFFFFFFFF);
		Component nama = lembar.ada(Gilda.TINGKAT_NAMA) ? lembar.teks(Gilda.TINGKAT_NAMA) : TeksTempat.k("gilda.tingkat_n", tingkat);
		l.teks(Brush.bold(TeksTempat.k("gilda.tingkat_anda", nama)), x0 + 18, y0 + 3, x1 - x0 - 120, gaya.teks, 0.9F);
		LencanaPangkat.bintang(l, Math.min(9, tingkat), 9, x1 - 9 * 9 - 1, y0 + 2, 10);
		PanelTempat.garis(l.g, gaya, x0, x1, y0 + 16);
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
			kosong(l, TeksTempat.k("gilda.ujian_puncak"), x0, y0, x1, y1);
			return;
		}
		float cx = (x0 + x1) / 2.0F;
		Brush.centered(l.g, font, Brush.bold(TeksTempat.k("gilda.ujian_tingkat", u.i(LembarKunci.TARGET, 1))), cx, y0 + 2, gaya.teks, 1.0F, false);
		int y = y0 + 16;
		int lx0 = Math.round(cx - Math.min(130, (x1 - x0) / 2.0F - 6));
		int lx1 = Math.round(cx + Math.min(130, (x1 - x0) / 2.0F - 6));
		List<Lembar> syarat = u.daftar(LembarKunci.SYARAT);
		boolean ringkas = y1 - y0 < 120;
		int langkah = ringkas ? 10 : 12;
		// rows stop above the fee line and the button; the rest is counted
		int batas = y1 - 18 - 4 - 11;
		for (int i = 0; i < syarat.size(); i++) {
			Lembar s = syarat.get(i);
			if (y + langkah > batas && i < syarat.size() - 1) {
				l.keterangan(TeksTempat.k("gilda.syarat_lagi", syarat.size() - i), lx0 + 14, y + 1, lx1 - lx0 - 14, gaya.teksSamar);
				y += langkah;
				break;
			}
			boolean ok = s.b(LembarKunci.OK);
			IkonTempat.gambar(l.g, ok ? IkonTempat.CENTANG : IkonTempat.SILANG, lx0, y, ringkas ? 9 : 10, 0xFFFFFFFF);
			l.teks(s.teks(LembarKunci.TEKS), lx0 + 14, y + 1, lx1 - lx0 - 14, ok ? gaya.teks : gaya.teksLemah, ringkas ? 0.8F : 0.85F);
			y += langkah;
		}
		barisNilai(l, TeksTempat.k("gilda.biaya_ujian"), TeksTempat.nb(u.l(LembarKunci.BIAYA, 0L), false), lx0, Math.min(y + 3, y1 - 32), lx1);
		tombolOp(l, "ujian", lx0, y1 - 18, lx1 - lx0, 15, TeksTempat.k("gilda.ikut_ujian"), u.b(LembarKunci.BISA), u.teksAtau(LembarKunci.ALASAN),
				Gilda.OP_UJIAN);
	}

	private void toko(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Gilda.TOKO);
		if (rows.isEmpty()) {
			kosong(l, TeksTempat.k("gilda.toko_kosong"), x0, y0, x1, y1);
			return;
		}
		Kotak[] k = bagi(x0, y0, x1, y1);
		long uang = uang();
		long muncul = diterima;
		toko.gambar(l, "toko", k[0].x0(), k[0].y0(), k[0].x1(), k[0].y1(), rows.size(), 22,
				(lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> KartuBarang.gambar(lk, rows.get(i), rx0, ry0, rx1, ry1, sorot, pilih, uang, muncul), () -> {
					// Enter / second click on the selected card buys the chosen amount (keyboard only, 38.U.2)
					int i = toko.pilihan();
					if (i >= 0 && i < rows.size() && rows.get(i).bisa() && tulisBoleh()) {
						kirim(LembarKunci.op(Gilda.OP_BELI, rows.get(i).id(), jumlah.nilai()));
					}
				}, i -> jumlah.set(1));
		int sel = toko.pilihan();
		Lembar e = sel >= 0 && sel < rows.size() ? rows.get(sel) : null;
		Kotak d = k[1];
		PanelTempat.kartu(l.g, gaya, d.x0(), d.y0(), d.x1(), d.y1(), 0.0F, false);
		if (e == null) {
			return;
		}
		int pv = tata == Tata.SEMPIT ? 30 : Math.min(d.w() - 10, Math.min(d.h() - 50, 72));
		int px = d.x0() + (d.w() - pv) / 2;
		pratinjau.gambar(l, "pratinjau", px, d.y0() + 4, px + pv, d.y0() + 4 + pv, e.barang(), null, e.i(LembarKunci.IKON, -1));
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), d.x0() + 5, d.y0() + pv + 8, d.w() - 10, TempatWarna.mutu(e.i(LembarKunci.MUTU, -1), gaya.gelap(), gaya.teks));
		int by = d.y1() - 19;
		int stok = e.i(LembarKunci.STOK, -1);
		int pw = jumlah.gambar(l, "jumlah", d.x1() - 55, by + 1, stok < 0 ? 64 : Math.max(1, stok));
		long total = e.l(LembarKunci.HARGA, 0L) * jumlah.nilai();
		boolean cukup = uang() < 0 || total <= uang();
		Component alasan = e.teksAtau(LembarKunci.ALASAN);
		if (alasan == null && !cukup) {
			alasan = TeksTempat.k("harga.kurang", TeksTempat.angka(total - Math.max(0L, uang())));
		}
		tombolOp(l, "beli", d.x0() + 4, by, d.w() - pw - 14, 15, TeksTempat.k("toko.beli", TeksTempat.angka(total)), e.bisa() && cukup, alasan,
				LembarKunci.op(Gilda.OP_BELI, e.id(), jumlah.nilai()));
	}

	private void pesanan(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Gilda.PESANAN);
		if (rows.isEmpty()) {
			kosong(l, TeksTempat.k("gilda.pesanan_kosong"), x0, y0, x1, y1);
			return;
		}
		pesanan.gambar(l, "pesanan", x0, y0, x1, y1, rows.size(), 24, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar o = rows.get(i);
			boolean siap = o.b(TokoSenjata.SIAP);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			if (!o.barang().isEmpty()) {
				lk.g.item(o.barang(), rx0 + 3, ry0 + 4);
			}
			lk.teks(o.teks(LembarKunci.NAMA), rx0 + 23, ry0 + 4, rx1 - rx0 - 100, TempatWarna.mutu(o.i(LembarKunci.MUTU, -1), gaya.gelap(), gaya.teks), 0.9F);
			lk.keterangan(siap ? TeksTempat.k("gilda.siap") : TeksTempat.k("gilda.dikerjakan"), rx0 + 23, ry0 + 14, rx1 - rx0 - 100,
					siap ? 0xFF2E7A4E : gaya.teksSamar);
			if (siap) {
				tombolOp(lk, "ambil" + i, rx1 - 60, ry0 + 4, 56, 15, TeksTempat.k("toko.ambil"), true, null, LembarKunci.op(Gilda.OP_AMBIL, o.id()));
			} else {
				CincinWaktu.gambar(lk, rx1 - 12, ry0 + 12, 8.0F, o.l(LembarKunci.SISA_TICK, 0L), 0L, diterima, CincinWaktu.KIRI);
			}
		}, null, null);
	}

	private void nilai(Lukis l, int x0, int y0, int x1, int y1) {
		int sisa = lembar.i(Gilda.NILAI_SISA, 0);
		float cx = (x0 + x1) / 2.0F;
		// laid out from the bottom up so the button never leaves the panel (scale 4: 106 px)
		int by = y1 - 18;
		int sy = by - 13;
		boolean ringkas = y1 - y0 < 120;
		int ikon = ringkas ? 20 : 32;
		IkonTempat.gambar(l.g, LembarKunci.Ikon.MATA_BATIN, Math.round(cx - ikon / 2.0F), y0 + (ringkas ? 2 : 6), ikon, 0xFFFFFFFF);
		int ty = y0 + (ringkas ? 26 : 46);
		l.bungkus(TeksTempat.k(tempa ? "gilda.nilai_jelas_tempa" : "gilda.nilai_jelas_alkemis"), x0 + 10, ty, x1 - x0 - 20, gaya.teksLemah, 0.85F,
				sy - 3 - ty);
		Brush.centered(l.g, font, TeksTempat.k("gilda.nilai_sisa", sisa), cx, sy, sisa > 0 ? gaya.teks : TempatWarna.BAHAYA, 1.0F, false);
		int bw = Math.min(140, x1 - x0 - 8);
		tombolOp(l, "nilai", Math.round(cx - bw / 2.0F), by, bw, 15, TeksTempat.k("gilda.nilai_tangan"), sisa > 0, TeksTempat.k("toko.nilai_habis"),
				Gilda.OP_NILAI);
	}
}
