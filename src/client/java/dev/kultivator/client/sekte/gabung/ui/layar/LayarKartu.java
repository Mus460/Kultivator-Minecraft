package dev.kultivator.client.sekte.gabung.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.CincinWaktu;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.DaftarSekte;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Potret;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.sekte.gabung.ui.IkonGabung;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.data.Dao;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Kartu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;

/**
 * {@code gabung_kartu} (DESIGN 41.10.3; 43.5 Sekte › Ikhtisar › kartu / hukum / keluar): three tabs.
 * <ul>
 * <li><b>Kartu</b>: one scroll with the jade member card (portrait, rank seal and name in the rank colour, sect and days,
 * PK and Papan Jasa place, Shifu and bond, eldest senior and rival, violation record, chores today, the weekly allowance,
 * the next rank and what it still needs, the jumps to the rank tree, mission board, pavilion, residence and politics),
 * then "Shifu" and "Murid titipan" as stacked headings (43.0.4: no fourth level of tabs);</li>
 * <li><b>Hukum</b>: the violation record and its thresholds, confinement, the history, the summons;</li>
 * <li><b>Keluar</b>: honourable leave, leaving without leave and deserting (the last two behind a confirm).</li>
 * </ul>
 * A sheet asking for the old tab {@code shifu} / {@code murid} opens Kartu scrolled to that heading. Text at 1.0 (43.0.5).
 */
public final class LayarKartu extends LayarGabungDasar {

	private static final int BARIS = 11;
	private static final int KARTU_H = 186;
	private static final int ROW_H = 24;

	private final DaftarGulir catatan = new DaftarGulir();
	private final DaftarGulir syaratKeluar = new DaftarGulir();
	private final GulirIsi kolom = new GulirIsi();
	/** The column has been laid out once (its height is known), so {@link #lompat} can scroll. */
	private boolean kolomSiap;
	/** Heading to scroll to once laid out ("shifu" / "murid"), or "". */
	private String lompat = "";
	private int shifuY;
	private int muridY;

	public LayarKartu(int npcId, CompoundTag lembar) {
		super(Kartu.LAYAR, npcId, lembar, t("kartu.judul"), LembarKunci.GAYA_GIOK);
	}

	/** The "?" seal: the Ensiklopedia topic of the 41 sects (the sheet id is not a topic). */
	@Override
	public String helpTopic() {
		return "gabung_sekte";
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(Kartu.TAB_KARTU, t("tab.kartu"), LembarKunci.Ikon.CAP_MERAH),
				tab(Kartu.TAB_HUKUM, t("tab.hukum"), LembarKunci.Ikon.TIMBANGAN),
				tab(Kartu.TAB_KELUAR, t("tab.keluar"), Glyphs.Atlas.PANAH_KANAN));
	}

	@Override
	protected KeadaanKosong.Gambar kosongGambar() {
		return KeadaanKosong.Gambar.LENTERA;
	}

	@Override
	protected void setelahLembar(boolean buka) {
		String diminta = lembar.s(LembarKunci.TAB);
		if (buka && (Kartu.TAB_SHIFU.equals(diminta) || Kartu.TAB_MURID.equals(diminta))) {
			gantiTab(Kartu.TAB_KARTU, false);
			lompat = diminta;
		}
	}

	@Override
	protected void tabBerubah() {
		catatan.mulaiUlang(Util.getMillis());
		syaratKeluar.mulaiUlang(Util.getMillis());
		kolom.atas();
		kolomSiap = false;
	}

	@Override
	protected void isiGabung(Lukis l, int x0, int y0, int x1, int y1) {
		int y = kepalaSekte(l, x0, y0, x1);
		switch (tab) {
			case Kartu.TAB_HUKUM -> hukum(l, x0, y, x1, y1);
			case Kartu.TAB_KELUAR -> keluar(l, x0, y, x1, y1);
			default -> tumpuk(l, x0, y, x1, y1);
		}
	}

	// ================================================================== Kartu: card, Shifu, Murid in one scroll

	private void tumpuk(Lukis l, int x0, int y0, int x1, int y1) {
		if (!lompat.isEmpty() && kolomSiap) {
			kolom.ke(Kartu.TAB_SHIFU.equals(lompat) ? shifuY : muridY);
			lompat = "";
		}
		gulirAktif = kolom;
		// the kit's scroll keeps 5 px at the right of its box for the bar
		int top = kolom.mulai(l, x0, y0, x1 - 5, y1);
		int bx1 = x1 - 8;
		int cy = top;
		kartu(l, x0, cy, bx1, cy + KARTU_H);
		cy += KARTU_H + 6;
		shifuY = cy - top;
		cy += subjudul(l, t("tab.shifu"), x0, cy, bx1);
		cy = shifu(l, x0, cy + 2, bx1);
		cy += 6;
		muridY = cy - top;
		cy += subjudul(l, t("tab.murid"), x0, cy, bx1);
		cy = murid(l, x0, cy + 2, bx1);
		kolom.selesai(l, cy);
		kolomSiap = true;
	}

	private void kartu(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar c = lembar.sub(Kartu.KARTU);
		int by = y1 - 32;
		int cy1 = by - 4;
		IkonGabung.kartu(l.g, x0, y0, x1, cy1);
		int ink = 0xFF16302A;
		int lemah = 0xFF3E5E52;
		int px = x0 + 9;
		int py = y0 + 9;
		int foto = 40;
		int pangkat = c.i(GabungLembar.PANGKAT, 0);
		int tepi = Palette.opaque(c.i(GabungLembar.WARNA, 0x8A8A8A));
		l.g.fill(px - 2, py - 2, px + foto + 2, py + foto + 2, tepi);
		Potret.gambar(l, px, py, foto, c.i(GabungLembar.POTRET, -1), Potret.IKON_BAWAAN, false, 0.0F);
		int tx = px + foto + 7;
		int tw = x1 - 9 - tx;
		IkonGabung.gambar(l.g, IkonGabung.pangkat(pangkat), tx, py - 1, 14);
		Component nama = c.teks(LembarKunci.NAMA).copy().append(" · ").append(namaPangkat(pangkat));
		l.teks(Brush.bold(nama), tx + 17, py + 2, tw - 17, ink);
		int y = py + 15;
		Component samaran = c.teksAtau(Kartu.SAMARAN);
		Component sekte = lembar.teks(GabungLembar.NAMA_SEKTE);
		l.teks(t("kartu.sekte_hari", sekte, c.l(Kartu.HARI_MASUK, 0L), c.i(Kartu.HARI_ANGGOTA, 0)), tx, y, tw, lemah);
		y += BARIS;
		if (samaran != null) {
			l.teks(t("kartu.samaran", samaran), tx, y, tw, 0xFF6A2A8A);
			y += BARIS;
		}
		int pw = LabelHarga.pk(l, c.i(LembarKunci.PK, 0), tx, y, false, ink);
		l.teks(t("kartu.pk_total", Brush.angka(c.l(GabungLembar.PK_TOTAL, 0L)), c.i(Kartu.PERINGKAT, 0)), tx + pw + 6, y, tw - pw - 6, lemah);
		y = Math.max(y + 13, py + foto + 6);
		int lx = x0 + 9;
		int lw = x1 - 9 - lx;
		// Shifu, senior, rival
		Lembar shifu = c.sub(Kartu.SHIFU);
		IkonGabung.gambar(l.g, IkonGabung.SHIFU, lx, y - 1, 10);
		if (!shifu.kosongSemua()) {
			Component s = t("kartu.shifu", shifu.teks(GabungLembar.JABATAN), shifu.teks(LembarKunci.NAMA));
			Component ikatan = t("kartu.ikatan");
			int sw = Math.max(20, Math.min(lw - 12 - 60 - font.width(ikatan), font.width(s)));
			l.teks(s, lx + 12, y, sw, ink);
			l.teks(ikatan, lx + 16 + sw, y, font.width(ikatan), lemah);
			meter(l, lx + 19 + sw + font.width(ikatan), y + 1, shifu.i(GabungLembar.IKATAN, 0), 100, 0xFF2E8963);
		} else {
			l.teks(t("kartu.shifu_belum"), lx + 12, y, lw - 12, lemah);
		}
		y += BARIS;
		Lembar kakak = c.sub(Kartu.KAKAK);
		Lembar saingan = c.sub(Kartu.SAINGAN);
		if (!kakak.kosongSemua() || !saingan.kosongSemua()) {
			IkonGabung.gambar(l.g, IkonGabung.MURID, lx, y - 1, 10);
			Component baris = Component.empty();
			if (!kakak.kosongSemua()) {
				baris = t("kartu.kakak", kakak.teks(LembarKunci.NAMA));
			}
			if (!saingan.kosongSemua()) {
				int sk = saingan.i(GabungLembar.SIKAP, 0);
				Component sg = t("kartu.saingan", saingan.teks(LembarKunci.NAMA), Component.literal(Brush.bertanda(sk)).withColor(DaftarSekte.warnaSikap(sk)));
				baris = kakak.kosongSemua() ? sg : baris.copy().append(" · ").append(sg);
			}
			l.teks(baris, lx + 12, y, lw - 12, ink);
			y += BARIS;
		}
		// record (with its bar), chores and allowance: one line each so they stay readable at 1.0
		int pel = c.i(Kartu.PELANGGARAN, 0);
		int pelWarna = pel >= 35 ? Palette.BAHAYA : pel >= 10 ? 0xFF9A6A10 : lemah;
		Component pelT = t("kartu.pelanggaran", pel);
		int pelW = Math.min(lw - 40, font.width(pelT));
		l.teks(pelT, lx, y, pelW, pelWarna);
		batang(l, lx + pelW + 4, y + 2, Math.min(60, lw - pelW - 4), pel / 100.0F,
				pel >= 35 ? Palette.BAHAYA : pel >= 10 ? Palette.PERINGATAN : Palette.OK, -1.0F);
		y += BARIS;
		int tugas = c.i(Kartu.TUGAS, 0);
		int tugasMaks = c.i(Kartu.TUGAS_MAKS, 3);
		boolean jatah = c.b(Kartu.JATAH);
		Component tugasT = t("kartu.tugas", tugas, tugasMaks);
		int tugasW = Math.min(lw / 2, font.width(tugasT));
		l.teks(tugasT, lx, y, tugasW, tugas > 0 ? ink : Palette.PERINGATAN);
		IkonGabung.gambar(l.g, IkonGabung.JATAH, lx + tugasW + 8, y - 1, 9);
		l.teks(t(jatah ? "kartu.jatah_siap" : "kartu.jatah_belum"), lx + tugasW + 19, y, lw - tugasW - 19, jatah ? 0xFF2E7A4E : lemah);
		y += BARIS;
		// the next rank and what it still needs
		Lembar berikut = c.sub(Kartu.BERIKUT);
		if (!berikut.kosongSemua() && y + 10 < cy1 - 6) {
			int bp = berikut.i(GabungLembar.PANGKAT, 0);
			IkonGabung.gambar(l.g, IkonGabung.pangkat(bp), lx, y - 1, 10);
			Component judulBerikut = t("kartu.berikut", namaPangkat(bp));
			l.teks(Brush.bold(judulBerikut), lx + 12, y, lw - 12, ink);
			int sx = lx + 12;
			int sy = y + BARIS;
			for (Lembar s : berikut.daftar(LembarKunci.SYARAT)) {
				boolean ok = s.b(LembarKunci.OK);
				Component teks = s.teks(LembarKunci.TEKS);
				int w = Math.min(lw - 12, font.width(teks) + 12);
				if (sx + w > x1 - 9) {
					sx = lx + 12;
					sy += BARIS;
				}
				if (sy + 9 > cy1 - 4) {
					break;
				}
				Glyphs.Atlas.ikon(l.g, ok ? Glyphs.Atlas.CENTANG : Glyphs.Atlas.SILANG, sx, sy, 8, 0xFFFFFFFF);
				l.teks(teks, sx + 10, sy, w - 12, ok ? ink : 0xFF9A3A2A);
				sx += w + 4;
			}
		}
		// buttons: two rows of three under the card
		String[][] tombolList = {{Kartu.OP_JATAH, "kartu.ambil_jatah"}, {Kartu.OP_PANGKAT, "kartu.pohon"}, {Kartu.OP_PAPAN, "kartu.papan"},
				{Kartu.OP_PAVILIUN, "kartu.paviliun"}, {Kartu.OP_KEDIAMAN, "kartu.kediaman"}, {Kartu.OP_POLITIK, "kartu.politik"}};
		int bw = (x1 - x0 - 2 * 3) / 3;
		for (int i = 0; i < tombolList.length; i++) {
			String op = tombolList[i][0];
			boolean aktif = !Kartu.OP_JATAH.equals(op) || jatah;
			tombolOp(l, "b" + op, x0 + (i % 3) * (bw + 3), by + (i / 3) * 16, bw, 15, t(tombolList[i][1]), aktif, t("kartu.jatah_belum"), op);
		}
	}

	// ================================================================== Shifu (stacked)

	/** The Shifu block; returns the y below. */
	private int shifu(Lukis l, int x0, int y0, int x1) {
		Lembar s = lembar.sub(Kartu.SHIFU);
		if (s.kosongSemua()) {
			return y0 + Math.round(l.bungkus(t("kartu.shifu_kosong"), x0 + 2, y0, x1 - x0 - 4, l.gaya.teksSamar)) + 2;
		}
		int size = 40;
		Potret.gambar(l, x0, y0 + 2, size, s.i(GabungLembar.POTRET, -1), Potret.IKON_BAWAAN, false, 0.0F);
		int tx = x0 + size + 8;
		int tw = x1 - tx;
		l.teks(Brush.bold(s.teks(LembarKunci.NAMA)), tx, y0 + 2, tw, l.gaya.teks);
		l.teks(s.teks(GabungLembar.JABATAN), tx, y0 + 2 + BARIS, tw, l.gaya.teksLemah);
		int y = y0 + 2 + 2 * BARIS;
		if (s.ada(GabungLembar.RANAH)) {
			Dao dao = daoDari(s.s(GabungLembar.DAO));
			Component rd = dao == null ? namaRanah(s.i(GabungLembar.RANAH, 0)) : namaRanah(s.i(GabungLembar.RANAH, 0)).copy().append(" · ")
					.append(dao.displayName());
			l.teks(rd, tx, y, tw, l.gaya.teks);
			y += BARIS;
		}
		Component watak = s.teksAtau(GabungLembar.WATAK);
		if (watak != null) {
			l.teks(t("kartu.watak", watak), tx, y, tw, l.gaya.teksLemah);
			y += BARIS;
		}
		Component ikatan = t("kartu.ikatan");
		l.teks(ikatan, tx, y, Math.min(tw, font.width(ikatan)), l.gaya.teksLemah);
		int ik = s.i(GabungLembar.IKATAN, 0);
		meter(l, tx + font.width(ikatan) + 4, y + 1, ik, 100, 0xFF2E8963);
		y = Math.max(y + BARIS + 2, y0 + size + 8);
		l.garis(x0, x1, y);
		y += 5;
		IkonGabung.gambar(l.g, IkonGabung.MEDITASI, x0, y - 1, 10);
		l.teks(t("kartu.pelajaran", hariKe(s.l(Kartu.PELAJARAN, -1L), hariIni())), x0 + 12, y, x1 - x0 - 12, l.gaya.teks);
		y += BARIS;
		y += Math.round(l.bungkus(t(ik >= 60 ? "kartu.pelajaran_dua" : ik >= 40 ? "kartu.lindung" : "kartu.ikatan_naik"), x0 + 12, y, x1 - x0 - 12,
				l.gaya.teksSamar)) + 3;
		List<Lembar> misi = s.daftar(Kartu.MISI);
		y += subjudul(l, t("kartu.misi_shifu"), x0, y, x1);
		if (misi.isEmpty()) {
			l.teks(t("kartu.misi_kosong"), x0 + 2, y, x1 - x0 - 4, l.gaya.teksSamar);
			y += BARIS;
		} else {
			for (Lembar m : misi) {
				Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.GULUNGAN, x0, y - 1, 9, 0xFFFFFFFF);
				l.teks(m.teks(LembarKunci.NAMA), x0 + 11, y, x1 - x0 - 11, l.gaya.teks);
				y += BARIS;
			}
		}
		y += 3;
		int bw = (x1 - x0 - 4) / 2;
		tombolOp(l, "hadiah", x0, y, bw, 15, t("kartu.hadiah"), s.b(Kartu.HADIAH), t("kartu.hadiah_sudah"), Kartu.OP_HADIAH);
		Lembar g = s.sub(Kartu.GANTI);
		tombolYakin(l, "ganti", x0 + bw + 4, y, bw, 15, t("kartu.ganti", g.i(LembarKunci.BIAYA, 0)), g.b(LembarKunci.BISA),
				g.teksAtau(LembarKunci.ALASAN), Kartu.OP_GANTI_SHIFU);
		return y + 17;
	}

	private static Dao daoDari(String id) {
		for (Dao d : Dao.values()) {
			if (d.id().equals(id)) {
				return d;
			}
		}
		return null;
	}

	// ================================================================== Murid titipan (stacked)

	/** The disciples entrusted to the sect; returns the y below. */
	private int murid(Lukis l, int x0, int y0, int x1) {
		Component kunci = lembar.sub(LembarKunci.TAB_KUNCI).teksAtau(Kartu.TAB_MURID);
		if (kunci != null) {
			return y0 + Math.round(l.bungkus(kunci, x0 + 2, y0, x1 - x0 - 4, l.gaya.teksSamar)) + 2;
		}
		Lembar m = lembar.sub(Kartu.MURID);
		int maks = m.i(LembarKunci.MAKS, 0);
		List<Lembar> rows = m.daftar(LembarKunci.DAFTAR);
		int terdaftar = 0;
		for (Lembar e : rows) {
			if (e.b(Kartu.TERDAFTAR)) {
				terdaftar++;
			}
		}
		IkonGabung.gambar(l.g, IkonGabung.MURID, x0, y0, 16);
		l.teks(Brush.bold(t("kartu.murid.judul", terdaftar, maks)), x0 + 20, y0 + 1, x1 - x0 - 20, l.gaya.teks);
		l.teks(t("kartu.murid.biaya", m.i(LembarKunci.BIAYA, 0)), x0 + 20, y0 + 1 + BARIS, x1 - x0 - 20, l.gaya.teksSamar);
		int y = y0 + 2 + 2 * BARIS + 2;
		if (rows.isEmpty()) {
			return y + Math.round(l.bungkus(t("kartu.murid.kosong"), x0 + 2, y, x1 - x0 - 4, l.gaya.teksSamar)) + 2;
		}
		int bw = 76;
		for (int i = 0; i < rows.size(); i++) {
			Lembar e = rows.get(i);
			int ry0 = y;
			int ry1 = y + ROW_H - 2;
			boolean ya = e.b(Kartu.TERDAFTAR);
			l.kartu(x0, ry0, x1, ry1, l.di(x0, ry0, x1, ry1) ? 1.0F : 0.0F, false);
			IkonGabung.gambar(l.g, ya ? IkonGabung.LENCANA : IkonGabung.MURID, x0 + 3, ry0 + 3, 16);
			l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), x0 + 22, ry0 + 2, x1 - x0 - bw - 30, l.gaya.teks);
			l.teks(namaRanah(e.i(GabungLembar.RANAH, 0)).copy().append(ya ? t("kartu.murid.titipan") : Component.empty()), x0 + 22, ry0 + 2 + 10,
					x1 - x0 - bw - 30, ya ? Palette.OK : l.gaya.teksSamar);
			if (ya) {
				tombolYakin(l, "lepas" + i, x1 - bw - 4, ry0 + 3, bw, 15, t("kartu.murid.lepas"), true, null, LembarKunci.op(Kartu.OP_LEPAS_MURID, e.id()),
						SegelKonfirmasi.Tingkat.RINGAN);
			} else {
				tombolOp(l, "daftar" + i, x1 - bw - 4, ry0 + 3, bw, 15, t("kartu.murid.daftar"), e.b(LembarKunci.BISA), e.teksAtau(LembarKunci.ALASAN),
						LembarKunci.op(Kartu.OP_DAFTAR_MURID, e.id()));
			}
			y += ROW_H;
		}
		return y;
	}

	// ================================================================== Hukum

	private void hukum(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar h = lembar.sub(Kartu.HUKUM);
		int pel = h.i(Kartu.PELANGGARAN, 0);
		IkonGabung.gambar(l.g, IkonGabung.HUKUM, x0, y0, 16);
		l.teks(Brush.bold(t("kartu.hukum.catatan", pel)), x0 + 20, y0 + 1, x1 - x0 - 20, pel >= 35 ? Palette.BAHAYA : l.gaya.teks);
		// the thresholds of 41.6.11 on one bar
		int bx = x0 + 20;
		int bw = x1 - bx - 4;
		int warna = pel >= 50 ? Palette.BAHAYA : pel >= 20 ? Palette.PERINGATAN : Palette.OK;
		batang(l, bx, y0 + 13, bw, pel / 100.0F, warna, -1.0F);
		int[] ambang = {10, 20, 35, 50, 60};
		String[] nama = {"teguran", "denda", "kurungan", "turun", "diusir"};
		for (int i = 0; i < ambang.length; i++) {
			int tx = bx + Math.round(bw * ambang[i] / 100.0F);
			l.g.fill(tx, y0 + 11, tx + 1, y0 + 20, pel >= ambang[i] ? 0xFFFF7A6A : 0xC0FFF0C0);
			if (l.di(tx - 3, y0 + 10, tx + 4, y0 + 21)) {
				l.tip(t("kartu.hukum.ambang." + nama[i], ambang[i]));
			}
		}
		int y = y0 + 24;
		if (h.ada(Kartu.KURUNGAN)) {
			CincinWaktu.gambar(l, x0 + 9, y + 8, 8.0F, h.l(Kartu.KURUNGAN, 0L), 0L, diterima, CincinWaktu.TANPA);
			l.teks(t("kartu.hukum.kurungan"), x0 + 21, y + 4, x1 - x0 - 21, Palette.BAHAYA);
			y += 20;
		}
		int by = y1 - 16;
		List<Lembar> rows = h.daftar(LembarKunci.CATATAN);
		y += subjudul(l, t("kartu.hukum.riwayat"), x0, y, x1);
		if (rows.isEmpty()) {
			l.bungkus(t("kartu.hukum.bersih"), x0 + 2, y, x1 - x0 - 4, l.gaya.teksSamar, by - y - 4);
		} else {
			catatan.gambar(l, "catatan", x0, y, x1, by - 3, rows.size(), 13, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar e = rows.get(i);
				lk.g.fill(rx0, ry0, rx1, ry1 - 1, i % 2 == 0 ? l.gaya.kartu : 0);
				Component hari = t("hari.pendek", e.l(LembarKunci.HARI, 0L));
				int hw = Math.min(56, font.width(hari));
				lk.teks(hari, rx0 + 2, ry0 + 2, hw, l.gaya.teksSamar);
				Component poin = Component.literal("+" + e.i(Kartu.POIN, 0));
				lk.kanan(poin, rx1 - 3, ry0 + 2, Palette.BAHAYA);
				lk.teks(e.teks(LembarKunci.TEKS), rx0 + hw + 6, ry0 + 2, rx1 - rx0 - hw - 10 - font.width(poin), l.gaya.teks);
			}, null, null);
		}
		boolean sidang = h.b(Kartu.SIDANG);
		tombolOp(l, "sidang", x0, by, Math.min(170, x1 - x0), 15, t(sidang ? "kartu.hukum.sidang" : "kartu.hukum.balai"), true, null, Kartu.OP_HUKUM);
	}

	// ================================================================== Keluar

	private void keluar(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar k = lembar.sub(Kartu.KELUAR);
		IkonGabung.gambar(l.g, IkonGabung.KELUAR, x0, y0, 16);
		l.teks(Brush.bold(t("kartu.keluar.judul")), x0 + 20, y0 + 1, x1 - x0 - 20, l.gaya.teks);
		l.teks(t("kartu.keluar.sub"), x0 + 20, y0 + 1 + BARIS, x1 - x0 - 20, l.gaya.teksSamar);
		int y = y0 + 25;
		int by = y1 - 16;
		int peringatanY = by - 34;
		List<Lembar> syarat = k.daftar(LembarKunci.SYARAT);
		if (syarat.isEmpty()) {
			l.teks(t("kartu.keluar.tanpa_syarat"), x0, y, x1 - x0, l.gaya.teksSamar);
		} else {
			daftarSyarat(l, syaratKeluar, "syarat", syarat, x0, y, x1, peringatanY - 12);
		}
		boolean bisa = k.b(LembarKunci.BISA);
		if (k.b(Kartu.RESTU)) {
			IkonGabung.gambar(l.g, IkonGabung.PINTU + 2, x0, peringatanY - 12, 9);
			l.teks(t("kartu.keluar.restu"), x0 + 11, peringatanY - 11, x1 - x0 - 11, Palette.OK);
		}
		// the deserter's warning in danger colours
		Component peringatan = k.teks(Kartu.PERINGATAN);
		l.g.fill(x0, peringatanY - 1, x1, peringatanY + 31, Palette.argb(0x40, Palette.BAHAYA & 0xFFFFFF));
		l.g.fill(x0, peringatanY - 1, x0 + 2, peringatanY + 31, Palette.BAHAYA);
		l.bungkus(peringatan, x0 + 5, peringatanY + 1, x1 - x0 - 8, l.gaya.gelap() ? 0xFFFFB0A0 : 0xFF8A1A10, 30);
		int bw = (x1 - x0 - 6) / 3;
		tombolYakin(l, "pamit", x0, by, bw, 15, t("kartu.keluar.tombol"), bisa, t("kartu.keluar.belum_bisa"), Kartu.OP_PAMIT);
		tombolYakin(l, "paksa", x0 + bw + 3, by, bw, 15, t("kartu.keluar.paksa"), true, null, Kartu.OP_PAMIT_PAKSA);
		tombolYakin(l, "belot", x0 + 2 * (bw + 3), by, bw, 15, t("kartu.keluar.belot"), true, null, Kartu.OP_BELOT);
	}
}
