package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.CincinWaktu;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KartuBarang;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihJumlah;
import dev.kultivator.client.screen.ui.Pratinjau3D;
import dev.kultivator.client.screen.ui.Saring;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.TokoSenjata;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code toko_senjata} (DESIGN 38.U.3.2, G19): Paviliun Seribu Bilah, the forge guild, sect markets. A sub per weapon shape
 * (jian, dao, qiang, kapak, gada, kipas besi, kebut, zirah) and "Pesan" (forge orders). Left: the filter row ({@link Saring}:
 * search {@code /} and the price / quality order) and the goods cards; right: the turning preview, the stat bars against the
 * weapon in hand, the duel class, buy with an amount picker, repair and appraisal. "Pesan": choose shape and grade, ask for
 * a quote (the materials with have / missing marks, cost, days), place the order; running orders with countdown rings and
 * "Ambil". Single kit, text 1.0.
 */
public final class LayarTokoSenjata extends LayarInti {

	private final DaftarGulir daftar = new DaftarGulir();
	private final DaftarGulir daftarBentuk = new DaftarGulir();
	private final Pratinjau3D pratinjau = new Pratinjau3D();
	private final PemilihJumlah jumlah = new PemilihJumlah();
	private final Saring saring;
	private int mutuPesan = 1;
	private List<Lembar> tampil = new ArrayList<>();

	public LayarTokoSenjata(int npcId, CompoundTag lembar) {
		super(TokoSenjata.LAYAR, npcId, lembar, k("layar.toko_senjata"), LembarKunci.GAYA_KAYU);
		saring = new Saring(() -> daftar.mulaiUlang(Util.getMillis()))
				.urut(List.of(new Saring.Pilihan("harga", k("toko.urut_harga")), new Saring.Pilihan("mutu", k("toko.urut_mutu"))));
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(TokoSenjata.TAB_JIAN, "tab.jian", LembarKunci.Ikon.JIAN), tab(TokoSenjata.TAB_DAO, "tab.dao", LembarKunci.Ikon.DAO),
				tab(TokoSenjata.TAB_QIANG, "tab.qiang", LembarKunci.Ikon.QIANG), tab(TokoSenjata.TAB_KAPAK, "tab.kapak", LembarKunci.Ikon.KAPAK),
				tab(TokoSenjata.TAB_GADA, "tab.gada", LembarKunci.Ikon.GADA), tab(TokoSenjata.TAB_KIPAS_BESI, "tab.kipas_besi", LembarKunci.Ikon.KIPAS_BESI),
				tab(TokoSenjata.TAB_KEBUT, "tab.kebut", LembarKunci.Ikon.KEBUT), tab(TokoSenjata.TAB_ZIRAH, "tab.zirah", LembarKunci.Ikon.ZIRAH),
				tab(TokoSenjata.TAB_PESAN, "tab.pesan", LembarKunci.Ikon.JAM_PASIR));
	}

	@Override
	protected boolean tabAda(String id) {
		return !TokoSenjata.TAB_PESAN.equals(id) || lembar.ada(TokoSenjata.PESAN_TEMPA);
	}

	@Override
	protected void tabBerubah() {
		daftar.mulaiUlang(Util.getMillis());
		jumlah.set(1);
	}

	@Override
	protected Component petunjuk() {
		return k("kunci.jumlah");
	}

	private List<Lembar> saring() {
		List<Lembar> out = new ArrayList<>();
		for (Lembar e : lembar.daftar(LembarKunci.DAFTAR)) {
			if (e.s(TokoSenjata.TAB_ENTRI).equals(tab) && saring.cocok(e.teks(LembarKunci.NAMA), null)) {
				out.add(e);
			}
		}
		Comparator<Lembar> c = "mutu".equals(saring.urutAktif()) ? Comparator.comparingInt((Lembar e) -> -e.i(LembarKunci.MUTU, -1))
				.thenComparingLong(e -> e.l(LembarKunci.HARGA, 0L)) : Comparator.comparingLong(e -> e.l(LembarKunci.HARGA, 0L));
		out.sort(c);
		return out;
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		if (TokoSenjata.TAB_PESAN.equals(tab)) {
			pesan(l, x0, y0, x1, y1);
			return;
		}
		Kotak[] k = bagi(x0, y0, x1, y1);
		Kotak kiri = k[0];
		int ly0 = kiri.y0() + saring.gambar(l, "saring", kiri.x0(), kiri.y0(), kiri.x1()) + 3;
		tampil = saring();
		if (tampil.isEmpty()) {
			kosong(l, saring.teks().isEmpty() ? k("toko.kosong") : k("toko.tidak_ketemu"), kiri.x0(), ly0, kiri.x1(), kiri.y1());
		} else {
			long uang = uang();
			long muncul = diterima;
			daftar.gambar(l, "daftar", kiri.x0(), ly0, kiri.x1(), kiri.y1() - 11, tampil.size(), 22,
					(lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> KartuBarang.gambar(lk, tampil.get(i), rx0, ry0, rx1, ry1, sorot, pilih, uang, muncul),
					this::beli, i -> jumlah.set(1));
			l.samar(k("daftar.dari", Math.min(tampil.size(), daftar.pilihan() + 1), tampil.size()), kiri.x0(), kiri.y1() - 9, kiri.w());
		}
		int sel = daftar.pilihan();
		detail(l, k[1], sel >= 0 && sel < tampil.size() ? tampil.get(sel) : null);
	}

	private void beli() {
		int i = daftar.pilihan();
		if (i < 0 || i >= tampil.size()) {
			return;
		}
		Lembar e = tampil.get(i);
		if (e.bisa() && tulisBoleh()) {
			kirim(LembarKunci.op(TokoSenjata.OP_BELI, e.id(), jumlah.nilai()));
		}
	}

	private void detail(Lukis l, Kotak k, Lembar e) {
		l.kartu(k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		if (e == null) {
			l.tengah(k("toko.pilih_barang"), (k.x0() + k.x1()) / 2.0F, k.y0() + k.h() / 2.0F - 4, k.w() - 8, l.gaya.teksSamar);
			return;
		}
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		boolean sempit = tata() == Tata.SEMPIT;
		// the preview takes at most 2/5 of the detail width and leaves room for stats and the buy row (scale 4)
		int pvMaks = sempit ? 30 : tata() == Tata.LEBAR ? 96 : 64;
		int pv = sempit ? 30 : Math.max(32, Math.min(pvMaks, Math.min((x1 - x0) * 2 / 5, k.h() - 62)));
		pratinjau.gambar(l, "pratinjau", x0, y, x0 + pv, y + pv, e.barang(), null, e.i(LembarKunci.IKON, -1));
		int tx = x0 + pv + 6;
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, y, x1 - tx, mutu(l, e.i(LembarKunci.MUTU, -1)));
		int ty = y + 11;
		String kelas = e.s(TokoSenjata.KELAS_DUEL);
		if (!kelas.isEmpty()) {
			l.teks(k("toko.kelas_duel", k("kelas." + kelas.toLowerCase(java.util.Locale.ROOT))), tx, ty, x1 - tx, l.gaya.teksLemah);
			ty += 10;
		}
		Lembar tangan = lembar.sub(LembarKunci.TANGAN);
		if (tangan.ada(LembarKunci.NAMA)) {
			l.samar(k("toko.vs_tangan", tangan.teks(LembarKunci.NAMA)), tx, ty, x1 - tx);
			ty += 10;
		}
		if (e.b(LembarKunci.LANGKA)) {
			Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.BINTANG_PENUH, tx, ty, 9, 0xFFFFFFFF);
			l.teks(k("kartu.langka"), tx + 11, ty, x1 - tx - 11, l.gaya.gelap() ? Palette.GOLD_LIGHT : Palette.GOLD_DARK);
			ty += 10;
		}
		// bottom rows first, so the stat bars know where to stop
		int by = k.y1() - TOMBOL_H - 4;
		Lembar layanan = lembar.sub(TokoSenjata.LAYANAN);
		boolean perbaiki = layanan.ada(TokoSenjata.PERBAIKI);
		boolean nilaiAda = layanan.ada(TokoSenjata.NILAI_SISA);
		int nLayanan = (perbaiki ? 1 : 0) + (nilaiAda ? 1 : 0);
		int ly = by - TOMBOL_H - 3;
		int sy = Math.max(ty + 2, y + pv + 4);
		// too short for its own row (narrow layout, scale 4): the services share the buy row
		boolean sebaris = nLayanan > 0 && ly < sy;
		int statBawah = (nLayanan > 0 && !sebaris ? ly : by) - 3;
		for (Lembar s : e.daftar(TokoSenjata.STAT)) {
			if (sy + 11 > statBawah) {
				break;
			}
			String kunci = s.s(TokoSenjata.STAT_KUNCI);
			float nilai = s.f(LembarKunci.NILAI, 0.0F);
			float banding = s.ada(TokoSenjata.BANDING) ? s.f(TokoSenjata.BANDING, 0.0F) : Float.NaN;
			float maks = switch (kunci) {
				case TokoSenjata.STAT_SERANGAN -> 24.0F;
				case TokoSenjata.STAT_KECEPATAN -> 4.0F;
				case TokoSenjata.STAT_DAYA_TAHAN -> 4000.0F;
				case TokoSenjata.STAT_JANGKAUAN -> 6.0F;
				case TokoSenjata.STAT_ZIRAH -> 20.0F;
				default -> Math.max(1.0F, nilai);
			};
			boolean desimal = TokoSenjata.STAT_KECEPATAN.equals(kunci) || TokoSenjata.STAT_JANGKAUAN.equals(kunci);
			sy += Bilah.banding(l, k("stat." + kunci), nilai, banding, maks, x0, sy, x1, desimal) + 1;
		}
		// buy row
		long harga = e.l(LembarKunci.HARGA, 0L);
		int stok = e.i(LembarKunci.STOK, -1);
		int maks = stok < 0 ? 64 : Math.max(1, stok);
		int pw = PemilihJumlah.lebar(l, maks);
		jumlah.gambar(l, "jumlah", x1 - pw, by, maks);
		long total = harga * jumlah.nilai();
		boolean bisa = e.bisa() && (uang() < 0 || total <= uang());
		Component alasan = e.teksAtau(LembarKunci.ALASAN);
		if (alasan == null && !bisa) {
			alasan = LembarKunci.K_HABIS.equals(e.keadaan()) ? k("kartu.habis") : k("harga.kurang", Brush.angka(total - uang()));
		}
		int ruang = x1 - x0 - pw - 6;
		int beliW = sebaris ? Math.max(50, ruang * 2 / 5) : ruang;
		tombolOp(l, "beli", x0, by, beliW, TOMBOL_H, k("toko.beli", Brush.angka(total)), bisa, alasan,
				LembarKunci.op(TokoSenjata.OP_BELI, e.id(), jumlah.nilai()));
		// services (repair / appraisal): their own row above the buy row, or beside "Beli" when short
		int sx = sebaris ? x0 + beliW + 3 : x0;
		int sw = sebaris ? (ruang - beliW - 3 - (nLayanan - 1) * 3) / Math.max(1, nLayanan) : (x1 - x0 - 4) / 2;
		int sRow = sebaris ? by : ly;
		if (perbaiki) {
			long p = layanan.l(TokoSenjata.PERBAIKI, 0L);
			tombolOp(l, "perbaiki", sx, sRow, sw, TOMBOL_H, k("toko.perbaiki", Brush.angka(p)), uang() < 0 || p <= uang(),
					k("harga.kurang", Brush.angka(p - Math.max(0L, uang()))), TokoSenjata.OP_PERBAIKI);
			sx += sw + (sebaris ? 3 : 4);
		}
		if (nilaiAda) {
			int sisa = layanan.i(TokoSenjata.NILAI_SISA, 0);
			tombolOp(l, "nilai", sx, sRow, sw, TOMBOL_H, k("toko.nilai", sisa), sisa > 0, k("toko.nilai_habis"), TokoSenjata.OP_NILAI);
		}
		if (nLayanan == 0 && !sempit && ly >= sy) {
			LabelHarga.gambar(l, harga, uang(), x0, ly + 3, false, false);
		}
	}

	// ---------------------------------------------------------------- forge orders

	private void pesan(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar p = lembar.sub(TokoSenjata.PESAN_TEMPA);
		List<Lembar> bentuk = p.daftar(TokoSenjata.BENTUK);
		Kotak[] k = bagi(x0, y0, x1, y1);
		Kotak kiri = k[0];
		int y = kiri.y0() + l.kepala(k("toko.pesan_bentuk"), kiri.x0(), kiri.y0(), kiri.x1());
		int listBottom = kiri.y1() - TOMBOL_H * 2 - 8;
		daftarBentuk.gambar(l, "bentuk", kiri.x0(), y, kiri.x1(), listBottom, bentuk.size(), 20, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar b = bentuk.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			Glyphs.Atlas.ikon(lk.g, b.i(LembarKunci.IKON, -1), rx0 + 3, ry0 + 2, 16, 0xFFFFFFFF);
			lk.teks(b.teks(LembarKunci.NAMA), rx0 + 23, ry0 + 6, rx1 - rx0 - 26, lk.gaya.teks);
		}, this::kutip, null);
		// grade picker: talismans 1..mutu_maks
		int maks = Math.max(1, p.i(TokoSenjata.MUTU_MAKS, 1));
		mutuPesan = Math.max(1, Math.min(maks, mutuPesan));
		int gy = listBottom + 3;
		Component labelMutu = k("toko.mutu");
		int lw = l.font.width(labelMutu) + 4;
		l.teks(labelMutu, kiri.x0(), gy + 3, lw, l.gaya.teksLemah);
		for (int m = 1; m <= maks; m++) {
			int cx = kiri.x0() + lw + (m - 1) * (TalismanButton.MIN_W + 2);
			if (cx + TalismanButton.MIN_W > kiri.x1()) {
				break;
			}
			final int mm = m;
			TalismanButton.gambar(l, "mutu" + m, cx, gy, TalismanButton.MIN_W, TOMBOL_H, Component.literal(Integer.toString(m)), true, null,
					() -> mutuPesan = mm, false, m == mutuPesan);
		}
		TalismanButton.gambar(l, "kutip", kiri.x0(), kiri.y1() - TOMBOL_H, kiri.w(), TOMBOL_H, k("toko.kutip"), !bentuk.isEmpty(), null, this::kutip);
		// right: the quote, then the running orders
		Kotak kanan = k[1];
		l.kartu(kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1(), 0.0F, false);
		int rx0 = kanan.x0() + 5;
		int rx1 = kanan.x1() - 5;
		int ry = kanan.y0() + 4;
		Lembar kutipan = p.sub(TokoSenjata.KUTIPAN);
		if (kutipan.ada(LembarKunci.ID)) {
			ry += l.kepala(k("toko.kutipan", kutipan.i(LembarKunci.MUTU, 1)), rx0, ry, rx1);
			for (Lembar b : kutipan.daftar(TokoSenjata.BAHAN)) {
				if (ry + 17 > kanan.y1() - 54) {
					break;
				}
				int perlu = b.i(TokoSenjata.PERLU, 0);
				int punya = b.i(TokoSenjata.PUNYA, 0);
				boolean ok = punya >= perlu;
				Glyphs.Atlas.ikon(l.g, ok ? Glyphs.Atlas.CENTANG : Glyphs.Atlas.SILANG, rx0, ry + 4, 9, 0xFFFFFFFF);
				if (!b.barang().isEmpty()) {
					l.g.item(b.barang(), rx0 + 11, ry);
				}
				Component jml = Component.literal(punya + " / " + perlu);
				int jw = l.font.width(jml);
				l.teks(b.teks(LembarKunci.NAMA), rx0 + 29, ry + 4, rx1 - rx0 - 33 - jw, l.gaya.teks);
				l.kanan(jml, rx1, ry + 4, ok ? l.gaya.teks : l.gaya.turun());
				ry += 17;
			}
			ry += 2;
			ry += nilai(l, k("toko.ongkos"), Brush.nb(kutipan.l(TokoSenjata.ONGKOS, 0L), false), rx0, ry, rx1);
			ry += nilai(l, k("toko.hari_jadi"), Brush.hari(kutipan.i(TokoSenjata.HARI_JADI, 1)), rx0, ry, rx1);
			int pesananAda = p.daftar(TokoSenjata.PESANAN).size();
			boolean penuh = pesananAda >= p.i(TokoSenjata.PESANAN_MAKS, 2);
			Component alasan = penuh ? k("toko.pesanan_penuh") : kutipan.teksAtau(LembarKunci.ALASAN);
			tombolOp(l, "pesan", rx0, ry + 2, rx1 - rx0, TOMBOL_H, k("toko.pesan"), kutipan.b(LembarKunci.BISA) && !penuh, alasan,
					LembarKunci.op(TokoSenjata.OP_PESAN, kutipan.id(), kutipan.i(LembarKunci.MUTU, 1)));
			ry += TOMBOL_H + 6;
		} else {
			ry += Math.round(l.bungkus(k("toko.kutip_petunjuk"), rx0, ry, rx1 - rx0, l.gaya.teksSamar, 27)) + 4;
		}
		List<Lembar> pesanan = p.daftar(TokoSenjata.PESANAN);
		if (!pesanan.isEmpty() && ry < kanan.y1() - 24) {
			ry += l.kepala(k("toko.pesanan_berjalan"), rx0, ry, rx1);
			for (int i = 0; i < pesanan.size() && ry + 18 <= kanan.y1(); i++) {
				Lembar o = pesanan.get(i);
				boolean siap = o.b(TokoSenjata.SIAP);
				if (!o.barang().isEmpty()) {
					l.g.item(o.barang(), rx0, ry);
				}
				l.teks(o.teks(LembarKunci.NAMA), rx0 + 19, ry + 4, rx1 - rx0 - 80, mutu(l, o.i(LembarKunci.MUTU, -1)));
				if (siap) {
					tombolOp(l, "ambil" + i, rx1 - 56, ry + 1, 56, TOMBOL_H, k("toko.ambil"), true, null, LembarKunci.op(TokoSenjata.OP_AMBIL, o.id()));
				} else {
					CincinWaktu.gambar(l, rx1 - 8, ry + 8, 7.0F, o.l(LembarKunci.SISA_TICK, 0L), 0L, diterima, CincinWaktu.KIRI);
				}
				ry += 18;
			}
		}
	}

	private void kutip() {
		List<Lembar> bentuk = lembar.sub(TokoSenjata.PESAN_TEMPA).daftar(TokoSenjata.BENTUK);
		int i = daftarBentuk.pilihan();
		if (i >= 0 && i < bentuk.size()) {
			kirim(LembarKunci.op(TokoSenjata.OP_KUTIP, bentuk.get(i).id(), mutuPesan));
		}
	}
}
