package dev.kultivator.client.sekte.gabung.ui.layar;

import static dev.kultivator.client.sekte.gabung.ui.layar.LayarGabung2.pk;
import static dev.kultivator.client.sekte.gabung.ui.layar.LayarGabung2.u;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.CincinWaktu;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.KeadaanKosong;
import dev.kultivator.client.tempat.ui.kit.LencanaPangkat;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Papan;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code gabung_papan} (DESIGN 41.6.3-41.6.4, 41.6.10, 41.10.5): the sect mission board at {@code kompleks/papan_misi}.
 * The head line counts today's missions against the daily limit and, during a war, flies the war banner (enemy, our
 * score, days left, "wajib" for Dalam and above). Papan: missions with their rank stamp E-S (locked ones say why) beside
 * the mission slip (rank, kind, flavour text, PK, "Terima", "Tunjuk di peta"). Aktif: running missions (board and war)
 * with their countdown ring, "Tandai" and "Batal" (asks twice). Perang: the five war missions with the score each wins.
 * Kelompok: group missions with who already joined. Tugas: the daily chores with their place, PK (corak chores x1.5) and
 * "Mulai".
 * <p>
 * Home (DESIGN 43.12.4): Sekte › Kegiatan › papan (tabs Papan/Aktif/Perang/Kelompok as kind chips) and › tugas (tab
 * Tugas), mounted by {@code sectevent.KegiatanPage}. Text 1.0 (43.0.5).
 */
public final class LayarPapan extends LayarGabungDasar {

	private final DaftarGulir daftar = new DaftarGulir();
	private final DaftarGulir aktif = new DaftarGulir();
	private final DaftarGulir tugas = new DaftarGulir();
	private final List<Lembar> tampil = new ArrayList<>();
	private String tampilKunci = "";

	public LayarPapan(int npcId, CompoundTag lembar) {
		super(Papan.LAYAR, npcId, lembar, u("papan.judul"), TempatWarna.Gaya.KAYU);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(new TabLentera.Tab(Papan.TAB_PAPAN, u("papan.tab.papan"), LembarKunci.Ikon.GILDA_MISI, null),
				new TabLentera.Tab(Papan.TAB_AKTIF, u("papan.tab.aktif"), IkonTempat.KOMPAS, null),
				new TabLentera.Tab(Papan.TAB_PERANG, u("papan.tab.perang"), LembarKunci.Ikon.ARENA, null),
				new TabLentera.Tab(Papan.TAB_KELOMPOK, u("papan.tab.kelompok"), IkonTempat.OBOR, null),
				new TabLentera.Tab(Papan.TAB_TUGAS, u("papan.tab.tugas"), IkonTempat.TONGGAK, null));
	}

	@Override
	protected KeadaanKosong.Gambar gambarKosong() {
		return KeadaanKosong.Gambar.PAPAN;
	}

	@Override
	protected void tabBerubah() {
		long now = Util.getMillis();
		daftar.mulaiUlang(now);
		aktif.mulaiUlang(now);
		tugas.mulaiUlang(now);
		tampilKunci = "";
	}

	@Override
	protected void setelahLembar(boolean buka) {
		tampilKunci = "";
	}

	@Override
	protected void isiGabung(Lukis l, int x0, int y0, int x1, int y1) {
		int y = kepalaSekte(l, x0, y0, x1);
		y = bilah(l, x0, y, x1);
		switch (tab) {
			case Papan.TAB_AKTIF -> aktif(l, x0, y, x1, y1);
			case Papan.TAB_TUGAS -> tugas(l, x0, y, x1, y1);
			default -> papan(l, x0, y, x1, y1);
		}
	}

	/** Missions today and the war banner. Returns the y under it. */
	private int bilah(Lukis l, int x0, int y, int x1) {
		int hari = lembar.i(Papan.MISI_HARI, 0);
		int maks = Math.max(1, lembar.i(Papan.MISI_MAKS, 3));
		l.teks(u("papan.misi_hari", hari, maks), x0, y + 2, 110, hari >= maks ? TempatWarna.BAHAYA : gaya.teks);
		Lembar p = lembar.sub(Papan.PERANG);
		if (!p.kosongSemua()) {
			int skor = p.i(LembarKunci.SKOR, 0);
			Component banner = u("papan.perang_banner", p.teks(Papan.LAWAN), TeksTempat.bertanda(skor), p.i(LembarKunci.SISA_HARI, 0));
			int bw = Math.min(x1 - x0 - 116, font.width(banner) + 22);
			if (bw > 40) {
				int bx = x1 - bw;
				float denyut = p.b(Papan.WAJIB) ? l.denyut() : 0.0F;
				l.g.fill(bx, y, x1, y + 12, Palette.argb(0xD0 - Math.round(0x30 * denyut), 0x7A1A1A));
				l.g.fill(bx, y, bx + 2, y + 12, TempatWarna.EMAS);
				IkonTempat.gambar(l.g, LembarKunci.Ikon.ARENA, bx + 4, y + 1, 10, 0xFFFFFFFF);
				Brush.fit(l.g, font, banner, bx + 17, y + 2.5F, bw - 20, 0xFFFFF0E0, 1.0F, 1.0F, false);
				if (l.di(bx, y, x1, y + 12)) {
					l.tip(List.of(banner, p.b(Papan.WAJIB) ? u("papan.perang_wajib") : u("papan.perang_sukarela")));
				}
			}
		}
		PanelTempat.garis(l.g, gaya, x0, x1, y + 15);
		return y + 19;
	}

	// ================================================================== Papan / Perang / Kelompok

	private List<Lembar> saring() {
		String kunci = tab + "|" + System.identityHashCode(lembar);
		if (kunci.equals(tampilKunci)) {
			return tampil;
		}
		tampil.clear();
		for (Lembar e : lembar.daftar(LembarKunci.DAFTAR)) {
			if (e.s(Papan.TAB_ENTRI).equals(tab)) {
				tampil.add(e);
			}
		}
		tampilKunci = kunci;
		return tampil;
	}

	private void papan(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> list = saring();
		if (list.isEmpty()) {
			kosong(l, u("papan.kosong." + tab), x0, y0, x1, y1);
			return;
		}
		Kotak[] k = bagi(x0, y0, x1, y1);
		daftar.gambar(l, "misi", k[0].x0(), k[0].y0(), k[0].x1(), k[0].y1(), list.size(), 22, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) ->
				baris(lk, list.get(i), rx0, ry0, rx1, ry1, sorot, pilih), this::terimaPilihan, null);
		int sel = daftar.pilihan();
		slip(l, k[1], sel >= 0 && sel < list.size() ? list.get(sel) : null);
	}

	private void cap(Lukis l, Lembar e, int x, int y, int size) {
		String mutu = e.s(Papan.MUTU_MISI);
		if ("perang".equals(mutu)) {
			l.g.fill(x, y, x + size, y + size, Palette.argb(0xE0, 0x7A1A1A));
			IkonTempat.gambar(l.g, LembarKunci.Ikon.ARENA, x + 1, y + 1, size - 2, 0xFFFFFFFF);
		} else {
			LencanaPangkat.gambar(l, mutu, x, y, size);
		}
	}

	private void baris(Lukis l, Lembar e, int x0, int y0, int x1, int y1, boolean sorot, boolean pilih) {
		String keadaan = e.keadaan();
		boolean kunci = LembarKunci.K_TERKUNCI.equals(keadaan) || LembarKunci.K_BATAS.equals(keadaan);
		boolean jalan = LembarKunci.AKTIF.equals(keadaan);
		PanelTempat.kartu(l.g, gaya, x0, y0, x1, y1, sorot ? 1.0F : 0.0F, pilih);
		cap(l, e, x0 + 3, y0 + 3, 16);
		int kanan = x1 - 4;
		int pw = pk(l, e.i(LembarKunci.PK, 0), -1L, kanan, y0 + 3, true, 1.0F);
		int tx = x0 + 23;
		int warna = kunci ? TempatWarna.terkunci(gaya.teks) : gaya.teks;
		l.teks(e.teks(LembarKunci.NAMA), tx, y0 + 3, kanan - pw - 6 - tx, warna);
		Component bawah;
		int bw = gaya.teksSamar;
		if (kunci) {
			IkonTempat.gambar(l.g, LembarKunci.Ikon.GEMBOK, tx, y0 + 12, 8, 0xFFFFFFFF);
			tx += 10;
			bawah = e.teks(LembarKunci.ALASAN);
			bw = gaya.gelap() ? 0xFFFF9A8A : TempatWarna.BAHAYA;
		} else if (jalan) {
			bawah = u("papan.berjalan");
			bw = TempatWarna.OK;
		} else if (e.ada(Papan.ANGGOTA_MAKS)) {
			bawah = u("papan.kelompok_isi", e.daftar(Papan.ANGGOTA).size(), e.i(Papan.ANGGOTA_MAKS, 4));
		} else if (e.ada(LembarKunci.SKOR)) {
			bawah = u("papan.skor_perang", TeksTempat.bertanda(e.i(LembarKunci.SKOR, 0)));
		} else {
			bawah = e.teks(LembarKunci.TEKS);
		}
		l.teks(bawah, tx, y0 + 12.5F, kanan - tx - 4, bw);
	}

	private void slip(Lukis l, Kotak k, Lembar e) {
		PanelTempat.kartu(l.g, gaya, k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		if (e == null) {
			return;
		}
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		int by = k.y1() - 19;
		cap(l, e, x1 - 20, y, 20);
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), x0, y + 2, x1 - x0 - 24, gaya.teks);
		y += 13;
		String mutu = e.s(Papan.MUTU_MISI);
		Component jenis = "perang".equals(mutu) ? u("papan.slip.perang") : u("papan.slip.mutu", mutu);
		l.teks(jenis, x0, y, x1 - x0 - 24, gaya.teksLemah);
		y += 11;
		// the rewards and the group take what is left under the story
		List<Lembar> anggota = e.daftar(Papan.ANGGOTA);
		int bawahTinggi = 12 + (e.ada(Papan.ANGGOTA_MAKS) ? 11 + Math.min(4, anggota.size()) * 9 : 0) + 14;
		float tinggi = l.bungkus(e.teks(LembarKunci.TEKS).copy().withStyle(ChatFormatting.ITALIC), x0, y, x1 - x0, gaya.teks, 1.0F,
				Math.max(9, by - y - bawahTinggi));
		y += Math.round(tinggi) + 4;
		int rx = x0;
		l.teks(u("papan.imbalan"), rx, y, 60, gaya.teksSamar);
		rx += font.width(u("papan.imbalan")) + 4;
		rx += pk(l, e.i(LembarKunci.PK, 0), -1L, rx, y, false, 1.0F) + 6;
		if (e.ada(LembarKunci.SKOR)) {
			l.teks(u("papan.skor_perang", TeksTempat.bertanda(e.i(LembarKunci.SKOR, 0))), rx, y, x1 - rx, TempatWarna.EMAS_GELAP);
		}
		y += 12;
		if (e.ada(Papan.ANGGOTA_MAKS)) {
			l.teks(u("papan.regu", anggota.size(), e.i(Papan.ANGGOTA_MAKS, 4)), x0, y, x1 - x0, gaya.teksLemah);
			y += 10;
			for (int i = 0; i < anggota.size() && i < 4 && y + 9 < by; i++) {
				Lembar a = anggota.get(i);
				l.teks(Component.literal("• ").append(a.teks(LembarKunci.NAMA)), x0 + 4, y, x1 - x0 - 4,
						a.b(LembarKunci.ANDA) ? gaya.aksen : gaya.teks);
				y += 9;
			}
		}
		String keadaan = e.keadaan();
		boolean jalan = LembarKunci.AKTIF.equals(keadaan);
		boolean bisa = LembarKunci.K_OK.equals(keadaan);
		boolean kelompok = Papan.TAB_KELOMPOK.equals(e.s(Papan.TAB_ENTRI)) && !anggota.isEmpty();
		boolean ikut = false;
		for (Lembar a : anggota) {
			ikut |= a.b(LembarKunci.ANDA);
		}
		int half = (x1 - x0 - 4) / 2;
		if (jalan || ikut) {
			tombolYakin(l, "batal", x0, by, half, 15, u("papan.batal"), true, null, LembarKunci.op(Papan.OP_BATAL, e.id()));
		} else {
			String verb = kelompok ? Papan.OP_GABUNG : Papan.OP_TERIMA;
			tombolOp(l, "terima", x0, by, half, 15, kelompok ? u("papan.gabung") : u("papan.terima"), bisa, e.teksAtau(LembarKunci.ALASAN),
					LembarKunci.op(verb, e.id()));
		}
		boolean adaPos = e.ada(LembarKunci.X) && e.ada(LembarKunci.Z) && (e.i(LembarKunci.X, 0) != 0 || e.i(LembarKunci.Z, 0) != 0);
		tombolOp(l, "tandai", x0 + half + 4, by, half, 15, u("papan.tunjuk"), adaPos, u("papan.tanpa_lokasi"),
				LembarKunci.op(LembarKunci.OP_TANDAI, e.i(LembarKunci.X, 0), e.i(LembarKunci.Z, 0)));
	}

	private void terimaPilihan() {
		List<Lembar> list = saring();
		int i = daftar.pilihan();
		if (i < 0 || i >= list.size()) {
			return;
		}
		Lembar e = list.get(i);
		if (LembarKunci.K_OK.equals(e.keadaan())) {
			boolean kelompok = Papan.TAB_KELOMPOK.equals(e.s(Papan.TAB_ENTRI)) && !e.daftar(Papan.ANGGOTA).isEmpty();
			kirim(LembarKunci.op(kelompok ? Papan.OP_GABUNG : Papan.OP_TERIMA, e.id()));
		}
	}

	// ================================================================== Aktif

	private List<Lembar> berjalan() {
		List<Lembar> out = new ArrayList<>();
		for (Lembar e : lembar.daftar(LembarKunci.DAFTAR)) {
			String t = e.s(Papan.TAB_ENTRI);
			if (Papan.TAB_AKTIF.equals(t) || Papan.TAB_PERANG.equals(t) && LembarKunci.AKTIF.equals(e.keadaan())) {
				out.add(e);
			}
		}
		return out;
	}

	private void aktif(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> list = berjalan();
		if (list.isEmpty()) {
			kosong(l, u("papan.kosong.aktif"), x0, y0, x1, y1);
			return;
		}
		aktif.gambar(l, "aktif", x0, y0, x1, y1, list.size(), 36, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = list.get(i);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			cap(lk, e, rx0 + 3, ry0 + 3, 15);
			boolean sempit = rx1 - rx0 < 260;
			int bw = sempit ? Math.min(52, (rx1 - rx0 - 30) / 2) : 56;
			int bx = rx1 - 4 - bw * 2 - 3;
			int tx = rx0 + 22;
			int teksKanan = (sempit ? rx1 - 22 : bx - 22);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, ry0 + 3, teksKanan - tx, gaya.teks);
			if (!sempit) {
				lk.bungkus(e.teks(LembarKunci.TEKS), tx, ry0 + 13, teksKanan - tx, gaya.teksLemah, 1.0F, 19);
			} else if (lk.di(rx0, ry0, rx1, ry1)) {
				lk.tip(List.of(e.teks(LembarKunci.NAMA), e.teks(LembarKunci.TEKS)));
			}
			if (e.ada(LembarKunci.SISA_TICK)) {
				CincinWaktu.gambar(lk, sempit ? rx1 - 12 : bx - 12, sempit ? ry0 + 9 : ry0 + 18, sempit ? 6.0F : 8.0F, e.l(LembarKunci.SISA_TICK, 0L), 0L,
						diterima, CincinWaktu.TANPA);
			}
			int by = sempit ? ry0 + 19 : ry0 + 10;
			int bxx = sempit ? rx1 - 4 - bw * 2 - 3 : bx;
			boolean adaPos = e.ada(LembarKunci.X) && (e.i(LembarKunci.X, 0) != 0 || e.i(LembarKunci.Z, 0) != 0);
			tombolOp(lk, "tandai" + i, bxx, by, bw, 14, u("papan.tandai"), adaPos, u("papan.tanpa_lokasi"),
					LembarKunci.op(LembarKunci.OP_TANDAI, e.i(LembarKunci.X, 0), e.i(LembarKunci.Z, 0)));
			tombolYakin(lk, "batal" + i, bxx + bw + 3, by, bw, 14, u("papan.batal"), true, null, LembarKunci.op(Papan.OP_BATAL, e.id()));
		}, null, null);
	}

	// ================================================================== Tugas

	private void tugas(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> list = lembar.daftar(Papan.TUGAS);
		if (list.isEmpty()) {
			kosong(l, u("papan.kosong.tugas"), x0, y0, x1, y1);
			return;
		}
		int selesai = 0;
		for (Lembar e : list) {
			selesai += e.b(Papan.SELESAI) ? 1 : 0;
		}
		l.teks(u("papan.tugas_ket", selesai), x0, y0, x1 - x0, gaya.teksSamar);
		y0 += 11;
		tugas.gambar(l, "tugas", x0, y0, x1, y1, list.size(), 30, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = list.get(i);
			boolean done = e.b(Papan.SELESAI);
			boolean kunci = e.b(LembarKunci.TERKUNCI);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			IkonTempat.gambar(lk.g, done ? IkonTempat.CENTANG : IkonTempat.TONGGAK, rx0 + 4, ry0 + 7, 14, done ? 0xFFFFFFFF : 0xC0FFFFFF);
			int bw = Math.min(56, (rx1 - rx0) / 5);
			int bx = rx1 - 4 - bw * 2 - 3;
			int tx = rx0 + 22;
			int warna = done || kunci ? TempatWarna.terkunci(gaya.teks) : gaya.teks;
			int pw = pk(lk, e.i(LembarKunci.PK, 0), -1L, bx - 6, ry0 + 4, true, 1.0F);
			int nx = tx;
			lk.teks(e.teks(LembarKunci.NAMA), nx, ry0 + 4, bx - pw - 12 - nx, warna);
			if (e.b(Papan.CORAK_BONUS)) {
				Component c = u("papan.corak_bonus");
				int cw = font.width(c) + 4;
				int cx = bx - pw - 10 - cw;
				lk.g.fill(cx, ry0 + 2, cx + cw, ry0 + 13, Palette.argb(0xC0, 0x2E8963));
				Brush.text(lk.g, font, c, cx + 2, ry0 + 3.5F, 0xFFF4FFF8, 1.0F, false);
			}
			Component bawah = done ? u("papan.tugas_selesai") : kunci ? e.teks(LembarKunci.ALASAN) : e.teks(LembarKunci.TEKS);
			lk.teks(bawah, tx, ry0 + 15, bx - tx - 6, done ? TempatWarna.OK : kunci ? TempatWarna.BAHAYA : gaya.teksSamar);
			boolean adaPos = e.ada(LembarKunci.X);
			tombolOp(lk, "mulai" + i, bx, ry0 + 8, bw, 14, u("papan.mulai"), !done && !kunci, done ? u("papan.tugas_selesai") : e.teksAtau(LembarKunci.ALASAN),
					LembarKunci.op(Papan.OP_TUGAS, e.id()));
			tombolOp(lk, "tandaiT" + i, bx + bw + 3, ry0 + 8, bw, 14, u("papan.tandai"), adaPos, u("papan.tanpa_lokasi"),
					LembarKunci.op(LembarKunci.OP_TANDAI, e.i(LembarKunci.X, 0), e.i(LembarKunci.Z, 0)));
		}, null, null);
	}
}
