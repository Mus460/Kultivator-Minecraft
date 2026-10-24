package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.BandingStat;
import dev.kultivator.client.tempat.ui.kit.CincinWaktu;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.KartuBarang;
import dev.kultivator.client.tempat.ui.kit.KotakCari;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.PemilihJumlah;
import dev.kultivator.client.tempat.ui.kit.Pratinjau3D;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.TokoSenjata;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code toko_senjata} (DESIGN 38.U.3.2): Paviliun Seribu Bilah, the forge guild, sect markets. Lantern per weapon shape
 * (jian, dao, qiang, kapak, gada, kipas besi, kebut, zirah) and "Pesan" (forge orders). Left: the search slip ({@code /}),
 * sort coins (price / quality) and the goods cards; right: the turning preview, the stat bars against the weapon in hand,
 * the duel class, buy with an amount picker, repair and appraisal. "Pesan": choose shape and grade, ask for a quote (the
 * materials with have / missing marks, cost, days), place the order; running orders with countdown rings and "Ambil".
 */
public final class LayarTokoSenjata extends LayarTempatDasar {

	private final DaftarGulir daftar = new DaftarGulir();
	private final DaftarGulir daftarBentuk = new DaftarGulir();
	private final Pratinjau3D pratinjau = new Pratinjau3D();
	private final PemilihJumlah jumlah = new PemilihJumlah();
	private boolean urutMutu;
	private int mutuPesan = 1;
	private List<Lembar> tampil = new ArrayList<>();

	public LayarTokoSenjata(int npcId, CompoundTag lembar) {
		super(TokoSenjata.LAYAR, npcId, lembar, TeksTempat.k("layar.toko_senjata"), TempatWarna.Gaya.KAYU);
		cari = new KotakCari();
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(tabBaru(TokoSenjata.TAB_JIAN, "tab.jian", LembarKunci.Ikon.JIAN), tabBaru(TokoSenjata.TAB_DAO, "tab.dao", LembarKunci.Ikon.DAO),
				tabBaru(TokoSenjata.TAB_QIANG, "tab.qiang", LembarKunci.Ikon.QIANG), tabBaru(TokoSenjata.TAB_KAPAK, "tab.kapak", LembarKunci.Ikon.KAPAK),
				tabBaru(TokoSenjata.TAB_GADA, "tab.gada", LembarKunci.Ikon.GADA),
				tabBaru(TokoSenjata.TAB_KIPAS_BESI, "tab.kipas_besi", LembarKunci.Ikon.KIPAS_BESI),
				tabBaru(TokoSenjata.TAB_KEBUT, "tab.kebut", LembarKunci.Ikon.KEBUT), tabBaru(TokoSenjata.TAB_ZIRAH, "tab.zirah", LembarKunci.Ikon.ZIRAH),
				tabBaru(TokoSenjata.TAB_PESAN, "tab.pesan", LembarKunci.Ikon.JAM_PASIR));
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
		return TeksTempat.k("kunci.jumlah");
	}

	private List<Lembar> saring() {
		List<Lembar> out = new ArrayList<>();
		for (Lembar e : lembar.daftar(LembarKunci.DAFTAR)) {
			if (e.s(TokoSenjata.TAB_ENTRI).equals(tab) && (cari == null || cari.cocok(e.teks(LembarKunci.NAMA)))) {
				out.add(e);
			}
		}
		Comparator<Lembar> c = urutMutu ? Comparator.comparingInt((Lembar e) -> -e.i(LembarKunci.MUTU, -1))
				.thenComparingLong(e -> e.l(LembarKunci.HARGA, 0L)) : Comparator.comparingLong(e -> e.l(LembarKunci.HARGA, 0L));
		out.sort(c);
		return out;
	}

	@Override
	protected void isi(Lukis l, int x0, int y0, int x1, int y1) {
		if (TokoSenjata.TAB_PESAN.equals(tab)) {
			pesan(l, x0, y0, x1, y1);
			return;
		}
		Kotak[] k = bagi(x0, y0, x1, y1);
		Kotak kiri = k[0];
		// search + sort row
		int cw = Math.max(60, kiri.w() - 76);
		cari.gambar(l, "cari", kiri.x0(), kiri.y0(), cw, 12);
		koinUrut(l, "urut_harga", kiri.x0() + cw + 4, kiri.y0(), TeksTempat.k("toko.urut_harga"), !urutMutu, () -> urutMutu = false);
		koinUrut(l, "urut_mutu", kiri.x0() + cw + 40, kiri.y0(), TeksTempat.k("toko.urut_mutu"), urutMutu, () -> urutMutu = true);
		tampil = saring();
		int ly0 = kiri.y0() + 16;
		if (tampil.isEmpty()) {
			kosong(l, cari.kosong() ? TeksTempat.k("toko.kosong") : TeksTempat.k("toko.tidak_ketemu"), kiri.x0(), ly0, kiri.x1(), kiri.y1());
		} else {
			long uang = uang();
			long muncul = diterima;
			daftar.gambar(l, "daftar", kiri.x0(), ly0, kiri.x1(), kiri.y1() - 9, tampil.size(), 22,
					(lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> KartuBarang.gambar(lk, tampil.get(i), rx0, ry0, rx1, ry1, sorot, pilih, uang, muncul),
					this::beli, i -> jumlah.set(1));
			Brush.text(l.g, font, TeksTempat.k("daftar.dari", Math.min(tampil.size(), daftar.pilihan() + 1), tampil.size()), kiri.x0(), kiri.y1() - 7,
					gaya.teksSamar, 0.6F, false);
		}
		int sel = daftar.pilihan();
		detail(l, k[1], sel >= 0 && sel < tampil.size() ? tampil.get(sel) : null);
	}

	private void koinUrut(Lukis l, String kunci, int x, int y, Component label, boolean on, Runnable aksi) {
		int w = 34;
		boolean over = l.di(x, y, x + w, y + 12);
		l.g.fill(x, y, x + w, y + 12, on ? Palette.argb(0xD0, Palette.JADE & 0xFFFFFF) : Palette.argb(over ? 0x60 : 0x30, 0x000000));
		Brush.frame(l.g, x, y, x + w, y + 12, on ? Palette.JADE_LIGHT : Palette.argb(0x80, TempatWarna.EMAS & 0xFFFFFF));
		Brush.fitCentered(l.g, font, label, x + w / 2.0F, y + 3, w - 4, on ? 0xFFF4FFF8 : gaya.teksLemah, 0.75F, 0.6F, false);
		l.zona(kunci, x, y, x + w, y + 12, aksi, true, null, true);
		l.fokusCincin(kunci, x, y, x + w, y + 12);
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
		PanelTempat.kartu(l.g, gaya, k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		if (e == null) {
			Brush.centered(l.g, font, TeksTempat.k("toko.pilih_barang"), (k.x0() + k.x1()) / 2.0F, k.y0() + k.h() / 2.0F - 4, gaya.teksSamar, 1.0F, false);
			return;
		}
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		boolean sempit = tata == Tata.SEMPIT;
		// the preview takes at most 2/5 of the detail width and leaves room for stats and the buy row (scale 4: 45 px)
		int pvMaks = sempit ? 30 : tata == Tata.LEBAR ? 96 : 64;
		int pv = sempit ? 30 : Math.max(32, Math.min(pvMaks, Math.min((x1 - x0) * 2 / 5, k.h() - 62)));
		pratinjau.gambar(l, "pratinjau", x0, y, x0 + pv, y + pv, e.barang(), null, e.i(LembarKunci.IKON, -1));
		int tx = x0 + pv + 6;
		int nameColor = TempatWarna.mutu(e.i(LembarKunci.MUTU, -1), gaya.gelap(), gaya.teks);
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, y, x1 - tx, nameColor);
		int ty = y + 11;
		String kelas = e.s(TokoSenjata.KELAS_DUEL);
		if (!kelas.isEmpty()) {
			l.keterangan(TeksTempat.k("toko.kelas_duel", TeksTempat.k("kelas." + kelas.toLowerCase(java.util.Locale.ROOT))), tx, ty, x1 - tx, gaya.teksLemah);
			ty += 9;
		}
		Lembar tangan = lembar.sub(LembarKunci.TANGAN);
		if (tangan.ada(LembarKunci.NAMA)) {
			l.keterangan(TeksTempat.k("toko.vs_tangan", tangan.teks(LembarKunci.NAMA)), tx, ty, x1 - tx, gaya.teksSamar);
			ty += 9;
		}
		if (e.b(LembarKunci.LANGKA)) {
			IkonTempat.gambar(l.g, IkonTempat.BINTANG_PENUH, tx, ty, 9, 0xFFFFFFFF);
			l.keterangan(TeksTempat.k("kartu.langka"), tx + 11, ty + 1, x1 - tx - 11, TempatWarna.EMAS_TERANG);
			ty += 10;
		}
		// bottom rows first, so the stat bars know where to stop
		int by = k.y1() - 19;
		Lembar layanan = lembar.sub(TokoSenjata.LAYANAN);
		boolean perbaiki = layanan.ada(TokoSenjata.PERBAIKI);
		boolean nilaiAda = layanan.ada(TokoSenjata.NILAI_SISA);
		int nLayanan = (perbaiki ? 1 : 0) + (nilaiAda ? 1 : 0);
		int ly = by - 17;
		int sy = Math.max(ty + 2, y + pv + 4);
		// too short for its own row (narrow layout, scale 4): the services share the buy row
		boolean sebaris = nLayanan > 0 && ly < sy;
		int statBawah = (nLayanan > 0 && !sebaris ? ly : by) - 3;
		for (Lembar s : e.daftar(TokoSenjata.STAT)) {
			if (sy + 10 > statBawah) {
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
			sy += BandingStat.gambar(l, TeksTempat.k("stat." + kunci), nilai, banding, maks, x0, sy, x1, desimal) + 1;
		}
		// buy row
		long harga = e.l(LembarKunci.HARGA, 0L);
		int stok = e.i(LembarKunci.STOK, -1);
		int maks = stok < 0 ? 64 : Math.max(1, stok);
		int pw = jumlah.gambar(l, "jumlah", x1 - 50, by + 1, maks);
		long total = harga * jumlah.nilai();
		boolean bisa = e.bisa() && (uang() < 0 || total <= uang());
		Component alasan = e.teksAtau(LembarKunci.ALASAN);
		if (alasan == null && !bisa) {
			alasan = LembarKunci.K_HABIS.equals(e.keadaan()) ? TeksTempat.k("kartu.habis") : TeksTempat.k("harga.kurang", TeksTempat.angka(total - uang()));
		}
		Component label = TeksTempat.k("toko.beli", TeksTempat.angka(total));
		int ruang = x1 - x0 - pw - 6;
		int beliW = sebaris ? Math.max(50, ruang * 2 / 5) : ruang;
		tombolOp(l, "beli", x0, by, beliW, 15, label, bisa, alasan, LembarKunci.op(TokoSenjata.OP_BELI, e.id(), jumlah.nilai()));
		// services (repair / appraisal): their own row above the buy row, or beside "Beli" when short
		int sx = sebaris ? x0 + beliW + 3 : x0;
		int sw = sebaris ? (ruang - beliW - 3 - (nLayanan - 1) * 3) / Math.max(1, nLayanan) : (x1 - x0 - 4) / 2;
		int sRow = sebaris ? by : ly;
		int sh = sebaris ? 15 : 14;
		if (perbaiki) {
			long p = layanan.l(TokoSenjata.PERBAIKI, 0L);
			tombolOp(l, "perbaiki", sx, sRow, sw, sh, TeksTempat.k("toko.perbaiki", TeksTempat.angka(p)), uang() < 0 || p <= uang(),
					TeksTempat.k("harga.kurang", TeksTempat.angka(p - Math.max(0L, uang()))), TokoSenjata.OP_PERBAIKI);
			sx += sw + (sebaris ? 3 : 4);
		}
		if (nilaiAda) {
			int sisa = layanan.i(TokoSenjata.NILAI_SISA, 0);
			tombolOp(l, "nilai", sx, sRow, sw, sh, TeksTempat.k("toko.nilai", sisa), sisa > 0, TeksTempat.k("toko.nilai_habis"), TokoSenjata.OP_NILAI);
		}
		if (nLayanan == 0 && !sempit && ly >= sy) {
			LabelHarga.gambar(l, harga, uang(), x0, ly + 3, false, 1.0F, false);
		}
	}

	// ---------------------------------------------------------------- forge orders

	private void pesan(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar p = lembar.sub(TokoSenjata.PESAN_TEMPA);
		List<Lembar> bentuk = p.daftar(TokoSenjata.BENTUK);
		Kotak[] k = bagi(x0, y0, x1, y1);
		Kotak kiri = k[0];
		int y = kiri.y0() + subjudul(l, TeksTempat.k("toko.pesan_bentuk"), kiri.x0(), kiri.y0(), kiri.x1());
		int listBottom = kiri.y1() - 34;
		daftarBentuk.gambar(l, "bentuk", kiri.x0(), y, kiri.x1(), listBottom, bentuk.size(), 20, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar b = bentuk.get(i);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			IkonTempat.gambar(lk.g, b.i(LembarKunci.IKON, -1), rx0 + 3, ry0 + 2, 16, 0xFFFFFFFF);
			lk.teks(b.teks(LembarKunci.NAMA), rx0 + 23, ry0 + 6, rx1 - rx0 - 26, gaya.teks);
		}, this::kutip, i -> {
		});
		// grade picker: coins 1..mutu_maks
		int maks = Math.max(1, p.i(TokoSenjata.MUTU_MAKS, 1));
		mutuPesan = Math.max(1, Math.min(maks, mutuPesan));
		l.keterangan(TeksTempat.k("toko.mutu"), kiri.x0(), listBottom + 4, 40, gaya.teksLemah);
		for (int m = 1; m <= maks; m++) {
			int cx = kiri.x0() + 40 + (m - 1) * 16;
			final int mm = m;
			boolean on = m == mutuPesan;
			Brush.disc(l.g, cx + 6, listBottom + 7, 6.0F, on ? Palette.GOLD : Palette.argb(0x60, 0x000000));
			Brush.centered(l.g, font, Component.literal(Integer.toString(m)), cx + 6, listBottom + 3, on ? 0xFF3A2208 : gaya.teksLemah, 1.0F, false);
			l.zona("mutu" + m, cx, listBottom + 1, cx + 13, listBottom + 14, () -> mutuPesan = mm, true, null, true);
		}
		tombol.gambar(l, "kutip", kiri.x0(), kiri.y1() - 15, kiri.w(), 15, TeksTempat.k("toko.kutip"), !bentuk.isEmpty(), null, this::kutip);
		// right: the quote, then the running orders
		Kotak kanan = k[1];
		PanelTempat.kartu(l.g, gaya, kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1(), 0.0F, false);
		int rx0 = kanan.x0() + 5;
		int rx1 = kanan.x1() - 5;
		int ry = kanan.y0() + 4;
		Lembar kutipan = p.sub(TokoSenjata.KUTIPAN);
		if (kutipan.ada(LembarKunci.ID)) {
			ry += subjudul(l, TeksTempat.k("toko.kutipan", kutipan.i(LembarKunci.MUTU, 1)), rx0, ry, rx1);
			for (Lembar b : kutipan.daftar(TokoSenjata.BAHAN)) {
				int perlu = b.i(TokoSenjata.PERLU, 0);
				int punya = b.i(TokoSenjata.PUNYA, 0);
				boolean ok = punya >= perlu;
				IkonTempat.gambar(l.g, ok ? IkonTempat.CENTANG : IkonTempat.SILANG, rx0, ry, 9, 0xFFFFFFFF);
				if (!b.barang().isEmpty()) {
					l.g.pose().pushMatrix();
					l.g.pose().translate(rx0 + 11, ry - 1);
					l.g.pose().scale(0.6F, 0.6F);
					l.g.item(b.barang(), 0, 0);
					l.g.pose().popMatrix();
				}
				l.teks(b.teks(LembarKunci.NAMA), rx0 + 23, ry + 1, rx1 - rx0 - 70, gaya.teks, 0.75F);
				l.kanan(Component.literal(punya + " / " + perlu), rx1, ry + 1, ok ? gaya.teks : TempatWarna.BAHAYA, 0.75F);
				ry += 11;
				if (ry > kanan.y1() - 70) {
					break;
				}
			}
			ry += 2;
			ry += barisNilai(l, TeksTempat.k("toko.ongkos"), TeksTempat.nb(kutipan.l(TokoSenjata.ONGKOS, 0L), false), rx0, ry, rx1);
			ry += barisNilai(l, TeksTempat.k("toko.hari_jadi"), TeksTempat.hari(kutipan.i(TokoSenjata.HARI_JADI, 1)), rx0, ry, rx1);
			int pesananAda = p.daftar(TokoSenjata.PESANAN).size();
			boolean penuh = pesananAda >= p.i(TokoSenjata.PESANAN_MAKS, 2);
			Component alasan = penuh ? TeksTempat.k("toko.pesanan_penuh") : kutipan.teksAtau(LembarKunci.ALASAN);
			tombolOp(l, "pesan", rx0, ry + 2, rx1 - rx0, 15, TeksTempat.k("toko.pesan"), kutipan.b(LembarKunci.BISA) && !penuh, alasan,
					LembarKunci.op(TokoSenjata.OP_PESAN, kutipan.id(), kutipan.i(LembarKunci.MUTU, 1)));
			ry += 21;
		} else {
			l.bungkus(TeksTempat.k("toko.kutip_petunjuk"), rx0, ry, rx1 - rx0, gaya.teksSamar, 0.75F, 24);
			ry += 26;
		}
		List<Lembar> pesanan = p.daftar(TokoSenjata.PESANAN);
		if (!pesanan.isEmpty() && ry < kanan.y1() - 24) {
			ry += subjudul(l, TeksTempat.k("toko.pesanan_berjalan"), rx0, ry, rx1);
			for (int i = 0; i < pesanan.size() && ry + 18 <= kanan.y1(); i++) {
				Lembar o = pesanan.get(i);
				boolean siap = o.b(TokoSenjata.SIAP);
				if (!o.barang().isEmpty()) {
					l.g.item(o.barang(), rx0, ry);
				}
				l.teks(o.teks(LembarKunci.NAMA), rx0 + 19, ry + 4, rx1 - rx0 - 80, TempatWarna.mutu(o.i(LembarKunci.MUTU, -1), gaya.gelap(), gaya.teks));
				if (siap) {
					tombolOp(l, "ambil" + i, rx1 - 56, ry + 1, 56, 14, TeksTempat.k("toko.ambil"), true, null,
							LembarKunci.op(TokoSenjata.OP_AMBIL, o.id()));
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
