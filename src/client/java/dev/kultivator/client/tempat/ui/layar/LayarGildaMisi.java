package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.CincinWaktu;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.LencanaPangkat;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.GildaMisi;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code gilda_misi} (DESIGN 38.U.3.4): the Gilda Kultivator board, town boards and post copies. The badge line (stars,
 * points to the next star, missions taken today) heads every tab. "Papan": missions with their rank badge E-S (locked
 * ones say why) and the detail: title, rank and requirement, the story, rewards (NB, Nama Baik, guild points, item
 * chance), time and direction, "Terima" and "Tunjuk di peta". "Aktif": running missions with stage, countdown ring,
 * "Serahkan", "Batal", "Tandai". "Naga": the Daftar Naga Muda (50 rows, the player's row highlighted). "Ujian": the next
 * badge exam with its checklist.
 */
public final class LayarGildaMisi extends LayarTempatDasar {

	private final DaftarGulir daftarPapan = new DaftarGulir();
	private final DaftarGulir daftarAktif = new DaftarGulir();
	private final DaftarGulir daftarNaga = new DaftarGulir();

	public LayarGildaMisi(int npcId, CompoundTag lembar) {
		super(GildaMisi.LAYAR, npcId, lembar, TeksTempat.k("layar.gilda_misi"), TempatWarna.Gaya.BATU);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(tabBaru(GildaMisi.TAB_PAPAN, "tab.papan", LembarKunci.Ikon.GILDA_MISI),
				tabBaru(GildaMisi.TAB_AKTIF, "tab.aktif", IkonTempat.KOMPAS), tabBaru(GildaMisi.TAB_NAGA, "tab.naga", IkonTempat.PIALA),
				tabBaru(GildaMisi.TAB_UJIAN, "tab.ujian", LembarKunci.Ikon.bintang(3)));
	}

	@Override
	protected boolean tabAda(String id) {
		return switch (id) {
			case GildaMisi.TAB_NAGA -> lembar.ada(GildaMisi.NAGA);
			case GildaMisi.TAB_UJIAN -> lembar.ada(LembarKunci.UJIAN);
			default -> true;
		};
	}

	@Override
	protected dev.kultivator.client.tempat.ui.kit.KeadaanKosong.Gambar gambarKosong() {
		return dev.kultivator.client.tempat.ui.kit.KeadaanKosong.Gambar.PAPAN;
	}

	@Override
	protected void tabBerubah() {
		long now = Util.getMillis();
		daftarPapan.mulaiUlang(now);
		daftarAktif.mulaiUlang(now);
		daftarNaga.mulaiUlang(now);
	}

	@Override
	protected void isi(Lukis l, int x0, int y0, int x1, int y1) {
		int y = lencana(l, x0, y0, x1);
		switch (tab) {
			case GildaMisi.TAB_AKTIF -> aktif(l, x0, y, x1, y1);
			case GildaMisi.TAB_NAGA -> naga(l, x0, y, x1, y1);
			case GildaMisi.TAB_UJIAN -> ujian(l, x0, y, x1, y1);
			default -> papan(l, x0, y, x1, y1);
		}
	}

	/** The badge line: stars, points bar, missions today. Returns the y under it. */
	private int lencana(Lukis l, int x0, int y0, int x1) {
		int bintang = lembar.i(GildaMisi.LENCANA, 0);
		l.keterangan(TeksTempat.k("gilda.lencana"), x0, y0 + 2, 40, gaya.teksLemah);
		int bw = LencanaPangkat.bintang(l, bintang, 9, x0 + 40, y0, 10);
		int px = x0 + 44 + bw;
		int poin = lembar.i(GildaMisi.POIN, 0);
		int berikut = Math.max(1, lembar.i(GildaMisi.POIN_BERIKUT, 1));
		int barW = Math.max(30, Math.min(90, x1 - px - 90));
		l.g.fill(px, y0 + 3, px + barW, y0 + 7, Palette.argb(0x50, 0x000000));
		l.g.fill(px, y0 + 3, px + Math.round(barW * Math.min(1.0F, poin / (float) berikut)), y0 + 7, TempatWarna.EMAS);
		l.keterangan(TeksTempat.k("gilda.poin", poin, berikut), px, y0 + 9, barW + 40, gaya.teksSamar);
		Component hari = TeksTempat.k("gilda.misi_hari_ini", lembar.i(GildaMisi.MISI_HARI_INI, 0), lembar.i(GildaMisi.MISI_MAKS, 4));
		l.kanan(hari, x1, y0 + 2, lembar.i(GildaMisi.MISI_HARI_INI, 0) >= lembar.i(GildaMisi.MISI_MAKS, 4) ? TempatWarna.BAHAYA : gaya.teks, 0.85F);
		PanelTempat.garis(l.g, gaya, x0, x1, y0 + 19);
		return y0 + 23;
	}

	private void papan(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> papan = lembar.daftar(GildaMisi.PAPAN);
		if (papan.isEmpty()) {
			kosong(l, TeksTempat.k("gilda.papan_kosong"), x0, y0, x1, y1);
			return;
		}
		Kotak[] k = bagi(x0, y0, x1, y1);
		daftarPapan.gambar(l, "papan", k[0].x0(), k[0].y0(), k[0].x1(), k[0].y1(), papan.size(), 20, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = papan.get(i);
			boolean kunci = e.b(LembarKunci.TERKUNCI);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			LencanaPangkat.gambar(lk, e.s(GildaMisi.PANGKAT), rx0 + 2, ry0 + 2, 15);
			int warna = kunci ? TempatWarna.terkunci(gaya.teks) : gaya.teks;
			lk.teks(e.teks(LembarKunci.JUDUL), rx0 + 21, ry0 + 2, rx1 - rx0 - 26, warna, 0.85F);
			Component bawah = kunci ? e.teks(LembarKunci.ALASAN) : TeksTempat.k("gilda.imbalan_singkat", TeksTempat.angka(e.l(GildaMisi.IMBALAN_NB, 0L)),
					e.teks(GildaMisi.ARAH));
			if (kunci) {
				IkonTempat.gambar(lk.g, LembarKunci.Ikon.GEMBOK, rx0 + 21, ry0 + 10, 8, 0xFFFFFFFF);
			}
			lk.keterangan(bawah, rx0 + (kunci ? 31 : 21), ry0 + 11, rx1 - rx0 - 36, kunci ? TempatWarna.BAHAYA : gaya.teksSamar);
		}, this::terima, null);
		int sel = daftarPapan.pilihan();
		detail(l, k[1], sel >= 0 && sel < papan.size() ? papan.get(sel) : null);
	}

	private void terima() {
		List<Lembar> papan = lembar.daftar(GildaMisi.PAPAN);
		int i = daftarPapan.pilihan();
		if (i >= 0 && i < papan.size() && !papan.get(i).b(LembarKunci.TERKUNCI) && tulisBoleh()) {
			kirim(LembarKunci.op(GildaMisi.OP_TERIMA, papan.get(i).id()));
		}
	}

	private void detail(Lukis l, Kotak k, Lembar e) {
		PanelTempat.kartu(l.g, gaya, k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		if (e == null) {
			return;
		}
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		// the mission paper: a pinned slip with the rank stamp
		LencanaPangkat.gambar(l, e.s(GildaMisi.PANGKAT), x1 - 20, y, 20);
		l.teks(Brush.bold(e.teks(LembarKunci.JUDUL)), x0, y + 2, x1 - x0 - 24, gaya.teks);
		y += 12;
		Component syarat = e.teksAtau(LembarKunci.SYARAT);
		if (syarat != null) {
			l.keterangan(syarat, x0, y, x1 - x0 - 24, gaya.teksLemah);
			y += 10;
		}
		y += 2;
		// the story takes what the rewards, time line and buttons leave (cut with ".." and a tooltip when short)
		float peluangAda = e.f(GildaMisi.PELUANG_BARANG, 0.0F) > 0.0F ? 9.0F : 0.0F;
		float ruangCerita = k.y1() - 19 - 3 - 9 - peluangAda - 11 - 4 - y;
		float tinggi = l.bungkus(e.teks(GildaMisi.CERITA).copy().withStyle(net.minecraft.ChatFormatting.ITALIC), x0, y, x1 - x0, gaya.teks, 0.85F,
				ruangCerita);
		y += Math.round(tinggi) + 4;
		// rewards line
		int rx = x0;
		rx += LabelHarga.gambar(l, e.l(GildaMisi.IMBALAN_NB, 0L), -1L, rx, y, false, 0.85F, false) + 6;
		int nb = e.i(GildaMisi.IMBALAN_NAMA_BAIK, 0);
		if (nb != 0) {
			Component t = TeksTempat.k("gilda.imbalan_nb", TeksTempat.bertanda(nb));
			l.teks(t, rx, y, 60, gaya.teks, 0.85F);
			rx += Math.round(font.width(t) * 0.85F) + 6;
		}
		int poin = e.i(GildaMisi.IMBALAN_POIN, 0);
		if (poin > 0) {
			l.teks(TeksTempat.k("gilda.imbalan_poin", poin), rx, y, x1 - rx, TempatWarna.EMAS_GELAP, 0.85F);
		}
		y += 11;
		float peluang = e.f(GildaMisi.PELUANG_BARANG, 0.0F);
		if (peluang > 0.0F) {
			l.keterangan(TeksTempat.k("gilda.peluang_barang", TeksTempat.persen(peluang)), x0, y, x1 - x0, gaya.teksSamar);
			y += 9;
		}
		l.keterangan(TeksTempat.k("gilda.waktu_arah", TeksTempat.hari(e.i(GildaMisi.WAKTU_HARI, 1)), e.teks(GildaMisi.ARAH)), x0, y, x1 - x0, gaya.teksLemah);
		int by = k.y1() - 19;
		int half = (x1 - x0 - 4) / 2;
		boolean penuh = lembar.i(GildaMisi.MISI_HARI_INI, 0) >= lembar.i(GildaMisi.MISI_MAKS, 4);
		boolean kunci = e.b(LembarKunci.TERKUNCI);
		Component alasan = kunci ? e.teksAtau(LembarKunci.ALASAN) : penuh ? TeksTempat.k("gilda.penuh_hari_ini") : null;
		tombolOp(l, "terima", x0, by, half, 15, TeksTempat.k("gilda.terima"), !kunci && !penuh, alasan, LembarKunci.op(GildaMisi.OP_TERIMA, e.id()));
		boolean adaPos = e.ada(LembarKunci.X) && e.ada(LembarKunci.Z);
		tombolOp(l, "tandai", x0 + half + 4, by, half, 15, TeksTempat.k("gilda.tunjuk_peta"), adaPos, TeksTempat.k("gilda.tanpa_lokasi"),
				LembarKunci.op(LembarKunci.OP_TANDAI, e.i(LembarKunci.X, 0), e.i(LembarKunci.Z, 0)));
	}

	private void aktif(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> aktif = lembar.daftar(LembarKunci.AKTIF);
		if (aktif.isEmpty()) {
			kosong(l, TeksTempat.k("gilda.aktif_kosong"), x0, y0, x1, y1);
			return;
		}
		daftarAktif.gambar(l, "aktif", x0, y0, x1, y1, aktif.size(), 34, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = aktif.get(i);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			LencanaPangkat.gambar(lk, e.s(GildaMisi.PANGKAT), rx0 + 3, ry0 + 3, 14);
			int tx = rx0 + 21;
			// narrow rows (under 250 px) put the three buttons on a second line under the title
			boolean sempitBaris = rx1 - rx0 < 250;
			int btn = sempitBaris ? rx1 - 4 : rx1 - 150;
			int teksKanan = sempitBaris ? rx1 - 22 : btn - 20;
			lk.teks(Brush.bold(e.teks(LembarKunci.JUDUL)), tx, ry0 + 3, teksKanan - tx, gaya.teks, 0.85F);
			if (!sempitBaris) {
				lk.keterangan(e.teks(LembarKunci.TEKS), tx, ry0 + 13, teksKanan - tx, gaya.teksLemah);
			}
			int tahap = e.i(LembarKunci.TAHAP, 0);
			int total = Math.max(1, e.i(LembarKunci.TAHAP_TOTAL, 1));
			int bw = sempitBaris ? Math.min(48, (rx1 - tx - 8) / 3) : 48;
			int by = sempitBaris ? ry0 + 18 : ry0 + 3;
			int bx = sempitBaris ? rx1 - 4 - bw * 3 - 4 : btn;
			int tahapKanan = sempitBaris ? bx - 4 : rx1;
			for (int s = 0; s < total && s < 12 && tx + s * 9 + 7 <= tahapKanan; s++) {
				lk.g.fill(tx + s * 9, ry0 + 25, tx + s * 9 + 7, ry0 + 28, s < tahap ? TempatWarna.OK : Palette.argb(0x50, 0x000000));
			}
			if (e.ada(LembarKunci.SISA_TICK)) {
				CincinWaktu.gambar(lk, sempitBaris ? rx1 - 12 : btn - 10, sempitBaris ? ry0 + 8 : ry0 + 16, sempitBaris ? 6.0F : 8.0F,
						e.l(LembarKunci.SISA_TICK, 0L), 0L, diterima, CincinWaktu.TANPA);
			}
			if (sempitBaris && lk.di(rx0, ry0, rx1, ry1)) {
				lk.tip(List.of(e.teks(LembarKunci.JUDUL), e.teks(LembarKunci.TEKS)));
			}
			tombolOp(lk, "serah" + i, bx, by, bw, 13, TeksTempat.k("gilda.serah"), e.b(GildaMisi.BISA_SERAH), TeksTempat.k("gilda.belum_selesai"),
					LembarKunci.op(GildaMisi.OP_SERAH, e.id()));
			tombolOp(lk, "tandai" + i, bx + bw + 2, by, bw, 13, TeksTempat.k("gilda.tandai"), e.ada(LembarKunci.X), TeksTempat.k("gilda.tanpa_lokasi"),
					LembarKunci.op(LembarKunci.OP_TANDAI, e.i(LembarKunci.X, 0), e.i(LembarKunci.Z, 0)));
			tombolOp(lk, "batal" + i, bx + 2 * (bw + 2), by, bw - 2, 13, TeksTempat.k("gilda.batal"), true, null,
					LembarKunci.op(GildaMisi.OP_BATAL, e.id()));
		}, null, null);
	}

	private void naga(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> naga = lembar.daftar(GildaMisi.NAGA);
		int anda = lembar.i(GildaMisi.NAGA_ANDA, 0);
		Component posisi = anda > 0 ? TeksTempat.k("gilda.naga_anda", anda) : TeksTempat.k("gilda.naga_belum");
		l.keterangan(posisi, x0, y0, x1 - x0, anda > 0 ? TempatWarna.EMAS_GELAP : gaya.teksSamar);
		y0 += 11;
		daftarNaga.gambar(l, "naga", x0, y0, x1, y1, naga.size(), 12, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = naga.get(i);
			boolean saya = e.b(LembarKunci.ANDA);
			int rank = e.i(GildaMisi.PERINGKAT, i + 1);
			lk.g.fill(rx0, ry0, rx1, ry1, saya ? Palette.argb(0x70, TempatWarna.EMAS & 0xFFFFFF) : i % 2 == 0 ? gaya.kartu : 0);
			int seal = rank == 1 ? 0xFFE0B040 : rank == 2 ? 0xFFC0C8D0 : rank == 3 ? 0xFFC07A40 : 0;
			if (seal != 0) {
				Brush.disc(lk.g, rx0 + 7, ry0 + 5.5F, 5.0F, seal);
			}
			Brush.centered(lk.g, font, Component.literal(Integer.toString(rank)), rx0 + 7, ry0 + 2.5F, seal != 0 ? 0xFF2A1A10 : gaya.teksSamar, 0.75F, false);
			int skorW = Math.round(font.width(TeksTempat.angka(e.i(LembarKunci.SKOR, 0))) * 0.8F);
			lk.teks(saya ? Brush.bold(e.teks(LembarKunci.NAMA)) : e.teks(LembarKunci.NAMA), rx0 + 16, ry0 + 2, (rx1 - rx0) * 0.5F, saya ? gaya.aksen : gaya.teks,
					0.8F);
			lk.keterangan(e.teks(LembarKunci.SEKTE), rx0 + 16 + (rx1 - rx0) * 0.5F, ry0 + 2.5F, (rx1 - rx0) * 0.5F - skorW - 24, gaya.teksSamar);
			lk.kanan(Component.literal(TeksTempat.angka(e.i(LembarKunci.SKOR, 0))), rx1 - 3, ry0 + 2, gaya.teks, 0.8F);
		}, null, null);
	}

	private void ujian(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar u = lembar.sub(LembarKunci.UJIAN);
		int target = u.i(LembarKunci.TARGET, 3);
		float cx = (x0 + x1) / 2.0F;
		// short content (GUI scale 4): a smaller seal beside the title instead of above it
		boolean ringkas = y1 - y0 < 130;
		int y;
		if (ringkas) {
			Component judul = Brush.bold(TeksTempat.k("gilda.ujian_judul", target));
			float jw = Math.min(x1 - x0 - 30, font.width(judul));
			IkonTempat.stempel(l.g, IkonTempat.Stempel.BINTANG, Math.round(cx - jw / 2.0F - 20), y0, 18, 18, 0xFFFFFFFF);
			Brush.fit(l.g, font, judul, cx - jw / 2.0F, y0 + 5, x1 - x0 - 30, gaya.teks, 1.0F, 0.6F, false);
			y = y0 + 22;
		} else {
			IkonTempat.stempel(l.g, IkonTempat.Stempel.BINTANG, Math.round(cx - 16), y0, 32, 32, 0xFFFFFFFF);
			Brush.centered(l.g, font, Brush.bold(TeksTempat.k("gilda.ujian_judul", target)), cx, y0 + 36, gaya.teks, 1.0F, false);
			y = y0 + 50;
		}
		Component penguji = u.teksAtau(GildaMisi.PENGUJI);
		if (penguji != null) {
			Brush.fitCentered(l.g, font, TeksTempat.k("gilda.penguji", penguji), cx, y, x1 - x0 - 20, gaya.teksLemah, 0.85F, 0.6F, false);
			y += ringkas ? 10 : 12;
		}
		int lx0 = Math.round(cx - Math.min(130, (x1 - x0) / 2.0F - 8));
		int lx1 = Math.round(cx + Math.min(130, (x1 - x0) / 2.0F - 8));
		List<Lembar> syarat = u.daftar(LembarKunci.SYARAT);
		int langkah = ringkas ? 10 : 12;
		// rows stop above the fee line and the button; the rest is counted ("+2 syarat lagi")
		int batas = y1 - 18 - 4 - 11;
		for (int i = 0; i < syarat.size(); i++) {
			Lembar s = syarat.get(i);
			if (y + langkah > batas && i < syarat.size() - 1) {
				int lagi = syarat.size() - i;
				l.keterangan(TeksTempat.k("gilda.syarat_lagi", lagi), lx0 + 14, y + 1, lx1 - lx0 - 14, gaya.teksSamar);
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
				GildaMisi.OP_UJIAN);
	}
}
