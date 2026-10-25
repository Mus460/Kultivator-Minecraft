package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.CincinWaktu;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.LencanaPangkat;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PapanMisi;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.GildaMisi;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code gilda_misi} (DESIGN 38.U.3.4, G22): the Gilda Kultivator board, town boards and post copies. The badge line
 * (stars, points to the next star, missions taken today) heads every sub. "Papan": the one mission board of the kit
 * ({@link PapanMisi}: rank badge E-S, locked ones say why; the detail with requirement, story, rewards, time and direction,
 * "Terima" and "Tunjuk di peta"). "Aktif": running missions with stage, countdown ring, "Serahkan", "Tandai", "Batal" (asks
 * the light confirm). "Naga": the Daftar Naga Muda (50 rows, the player's row highlighted). "Ujian": the next badge exam
 * with its checklist. Single kit, text 1.0.
 */
public final class LayarGildaMisi extends LayarInti {

	private final DaftarGulir daftarPapan = new DaftarGulir();
	private final DaftarGulir daftarAktif = new DaftarGulir();
	private final DaftarGulir daftarNaga = new DaftarGulir();

	public LayarGildaMisi(int npcId, CompoundTag lembar) {
		super(GildaMisi.LAYAR, npcId, lembar, k("layar.gilda_misi"), LembarKunci.GAYA_BATU);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(GildaMisi.TAB_PAPAN, "tab.papan", LembarKunci.Ikon.GILDA_MISI), tab(GildaMisi.TAB_AKTIF, "tab.aktif", Glyphs.Atlas.KOMPAS),
				tab(GildaMisi.TAB_NAGA, "tab.naga", Glyphs.Atlas.PIALA), tab(GildaMisi.TAB_UJIAN, "tab.ujian", LembarKunci.Ikon.bintang(3)));
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
	protected KeadaanKosong.Gambar kosongGambar() {
		return KeadaanKosong.Gambar.PAPAN;
	}

	@Override
	public boolean lebar() {
		return false;
	}

	@Override
	protected void tabBerubah() {
		long now = Util.getMillis();
		daftarPapan.mulaiUlang(now);
		daftarAktif.mulaiUlang(now);
		daftarNaga.mulaiUlang(now);
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
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
		Component label = k("gilda.lencana");
		int lw = Math.min(60, l.font.width(label)) + 4;
		l.teks(label, x0, y0 + 1, lw - 4, l.gaya.teksLemah);
		int bw = LencanaPangkat.bintang(l, bintang, 9, x0 + lw, y0, 10);
		Component hari = k("gilda.misi_hari_ini", lembar.i(GildaMisi.MISI_HARI_INI, 0), lembar.i(GildaMisi.MISI_MAKS, 4));
		int hw = l.font.width(hari);
		l.kanan(hari, x1, y0 + 1, lembar.i(GildaMisi.MISI_HARI_INI, 0) >= lembar.i(GildaMisi.MISI_MAKS, 4) ? l.gaya.turun() : l.gaya.teks);
		int px = x0 + lw + bw + 4;
		int poin = lembar.i(GildaMisi.POIN, 0);
		int berikut = Math.max(1, lembar.i(GildaMisi.POIN_BERIKUT, 1));
		Component poinTeks = k("gilda.poin", poin, berikut);
		int ruang = x1 - hw - 6 - px;
		if (ruang >= 30) {
			int barW = Math.min(60, ruang / 2);
			l.g.fill(px, y0 + 3, px + barW, y0 + 7, Palette.argb(0x50, 0x000000));
			l.g.fill(px, y0 + 3, px + Math.round(barW * Math.min(1.0F, poin / (float) berikut)), y0 + 7, Palette.GOLD);
			l.teks(poinTeks, px + barW + 4, y0 + 1, ruang - barW - 4, l.gaya.teksSamar);
		}
		l.garis(x0, x1, y0 + 13);
		return y0 + 17;
	}

	private void papan(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> papan = lembar.daftar(GildaMisi.PAPAN);
		if (papan.isEmpty()) {
			kosong(l, k("gilda.papan_kosong"), x0, y0, x1, y1);
			return;
		}
		List<PapanMisi.Misi> misi = new ArrayList<>(papan.size());
		for (Lembar e : papan) {
			List<Component> lain = new ArrayList<>(2);
			int nb = e.i(GildaMisi.IMBALAN_NAMA_BAIK, 0);
			if (nb != 0) {
				lain.add(k("gilda.imbalan_nb", Brush.bertanda(nb)));
			}
			int poin = e.i(GildaMisi.IMBALAN_POIN, 0);
			if (poin > 0) {
				lain.add(k("gilda.imbalan_poin", poin));
			}
			float peluang = e.f(GildaMisi.PELUANG_BARANG, 0.0F);
			if (peluang > 0.0F) {
				lain.add(k("gilda.peluang_barang", Brush.persen(peluang)));
			}
			Component kunci = e.b(LembarKunci.TERKUNCI) ? (e.ada(LembarKunci.ALASAN) ? e.teks(LembarKunci.ALASAN) : Component.translatable("kultivator.gui43.kit.terkunci"))
					: null;
			misi.add(new PapanMisi.Misi(e.id(), e.s(GildaMisi.PANGKAT), e.teks(LembarKunci.JUDUL), e.teksAtau(LembarKunci.SYARAT), e.teksAtau(GildaMisi.CERITA),
					e.l(GildaMisi.IMBALAN_NB, 0L), lain, k("gilda.waktu_arah", Brush.hari(e.i(GildaMisi.WAKTU_HARI, 1)), e.teks(GildaMisi.ARAH)),
					e.teksAtau(GildaMisi.ARAH), kunci, e.ada(LembarKunci.X) && e.ada(LembarKunci.Z), 0L, 0L, diterima));
		}
		boolean penuh = lembar.i(GildaMisi.MISI_HARI_INI, 0) >= lembar.i(GildaMisi.MISI_MAKS, 4);
		Component alasan = !tulisBoleh() ? alasanBaca() : penuh ? k("gilda.penuh_hari_ini") : null;
		PapanMisi.gambar(l, "papan", daftarPapan, misi, x0, y0, x1, y1, alasan, null, m -> kirim(LembarKunci.op(GildaMisi.OP_TERIMA, m.id())), m -> {
			for (Lembar e : papan) {
				if (e.id().equals(m.id())) {
					kirim(LembarKunci.op(LembarKunci.OP_TANDAI, e.i(LembarKunci.X, 0), e.i(LembarKunci.Z, 0)));
					return;
				}
			}
		});
	}

	private void aktif(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> aktif = lembar.daftar(LembarKunci.AKTIF);
		if (aktif.isEmpty()) {
			kosong(l, k("gilda.aktif_kosong"), x0, y0, x1, y1);
			return;
		}
		daftarAktif.gambar(l, "aktif", x0, y0, x1, y1, aktif.size(), 34, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = aktif.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			LencanaPangkat.gambar(lk, e.s(GildaMisi.PANGKAT), rx0 + 3, ry0 + 3, 14);
			int tx = rx0 + 21;
			// narrow rows (under 250 px) put the three buttons on a second line under the title
			boolean sempitBaris = rx1 - rx0 < 250;
			int bw = sempitBaris ? Math.max(16, Math.min(52, (rx1 - tx - 30) / 3)) : 52;
			int bx = rx1 - 4 - bw * 3 - 4;
			int by = sempitBaris ? ry0 + 18 : ry0 + 3;
			int teksKanan = sempitBaris ? rx1 - 22 : bx - 20;
			lk.teks(Brush.bold(e.teks(LembarKunci.JUDUL)), tx, ry0 + 3, teksKanan - tx, lk.gaya.teks);
			if (!sempitBaris) {
				lk.teks(e.teks(LembarKunci.TEKS), tx, ry0 + 13, teksKanan - tx, lk.gaya.teksLemah);
			}
			int tahap = e.i(LembarKunci.TAHAP, 0);
			int total = Math.max(1, e.i(LembarKunci.TAHAP_TOTAL, 1));
			int tahapKanan = sempitBaris ? bx - 4 : rx1;
			for (int s = 0; s < total && s < 12 && tx + s * 9 + 7 <= tahapKanan; s++) {
				lk.g.fill(tx + s * 9, ry0 + 26, tx + s * 9 + 7, ry0 + 29, s < tahap ? Palette.OK : Palette.argb(0x50, 0x000000));
			}
			if (e.ada(LembarKunci.SISA_TICK)) {
				CincinWaktu.gambar(lk, sempitBaris ? rx1 - 12 : bx - 10, sempitBaris ? ry0 + 8 : ry0 + 16, sempitBaris ? 6.0F : 8.0F, e.l(LembarKunci.SISA_TICK, 0L),
						0L, diterima, CincinWaktu.TANPA);
			}
			if (sempitBaris && lk.di(rx0, ry0, rx1, ry1)) {
				lk.tip(List.of(e.teks(LembarKunci.JUDUL), e.teks(LembarKunci.TEKS)));
			}
			tombolOp(lk, "serah" + i, bx, by, bw, TOMBOL_H, k("gilda.serah"), e.b(GildaMisi.BISA_SERAH), k("gilda.belum_selesai"),
					LembarKunci.op(GildaMisi.OP_SERAH, e.id()));
			tombolOp(lk, "tandai" + i, bx + bw + 2, by, bw, TOMBOL_H, k("gilda.tandai"), e.ada(LembarKunci.X), k("gilda.tanpa_lokasi"),
					LembarKunci.op(LembarKunci.OP_TANDAI, e.i(LembarKunci.X, 0), e.i(LembarKunci.Z, 0)));
			yakin(lk, "batal" + i, bx + 2 * (bw + 2), by, bw, TOMBOL_H, k("gilda.batal"), true, null, LembarKunci.op(GildaMisi.OP_BATAL, e.id()),
					SegelKonfirmasi.Tingkat.RINGAN, Component.translatable("kultivator.gui43.konteks.yakin", k("gilda.batal")));
		}, null, null);
	}

	private void naga(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> naga = lembar.daftar(GildaMisi.NAGA);
		int anda = lembar.i(GildaMisi.NAGA_ANDA, 0);
		Component posisi = anda > 0 ? k("gilda.naga_anda", anda) : k("gilda.naga_belum");
		l.teks(posisi, x0, y0, x1 - x0, anda > 0 ? Palette.GOLD_DARK : l.gaya.teksSamar);
		y0 += 12;
		daftarNaga.gambar(l, "naga", x0, y0, x1, y1, naga.size(), 12, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = naga.get(i);
			boolean saya = e.b(LembarKunci.ANDA);
			int rank = e.i(GildaMisi.PERINGKAT, i + 1);
			if (saya || i % 2 == 0) {
				lk.g.fill(rx0, ry0, rx1, ry1, saya ? Palette.argb(0x70, Palette.GOLD & 0xFFFFFF) : lk.gaya.kartu);
			}
			int seal = rank == 1 ? 0xFFE0B040 : rank == 2 ? 0xFFC0C8D0 : rank == 3 ? 0xFFC07A40 : 0;
			Component no = Component.literal(Integer.toString(rank));
			int nw = Math.max(12, lk.font.width(no) + 4);
			if (seal != 0) {
				Brush.disc(lk.g, rx0 + nw / 2.0F + 1, ry0 + 5.5F, 5.5F, seal);
			}
			lk.tengah(no, rx0 + nw / 2.0F + 1, ry0 + 2, nw, seal != 0 ? 0xFF2A1A10 : lk.gaya.teksSamar);
			Component skor = Component.literal(Brush.angka(e.i(LembarKunci.SKOR, 0)));
			int sw = lk.font.width(skor);
			int nx = rx0 + nw + 4;
			int namaW = Math.round((rx1 - nx - sw - 6) * 0.55F);
			lk.teks(saya ? Brush.bold(e.teks(LembarKunci.NAMA)) : e.teks(LembarKunci.NAMA), nx, ry0 + 2, namaW, saya ? lk.gaya.aksen : lk.gaya.teks);
			lk.teks(e.teks(LembarKunci.SEKTE), nx + namaW + 4, ry0 + 2, rx1 - nx - namaW - sw - 10, lk.gaya.teksSamar);
			lk.kanan(skor, rx1 - 3, ry0 + 2, lk.gaya.teks);
		}, null, null);
	}

	private void ujian(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar u = lembar.sub(LembarKunci.UJIAN);
		int target = u.i(LembarKunci.TARGET, 3);
		float cx = (x0 + x1) / 2.0F;
		// short content (GUI scale 4): a smaller seal beside the title instead of above it
		boolean ringkas = y1 - y0 < 130;
		Component judul = Brush.bold(k("gilda.ujian_judul", target));
		int y;
		if (ringkas) {
			float jw = Math.min(x1 - x0 - 30, l.font.width(judul));
			Glyphs.Atlas.stempel(l.g, Glyphs.Atlas.Stempel.BINTANG, Math.round(cx - jw / 2.0F - 20), y0, 18, 18, 0xFFFFFFFF);
			l.teks(judul, cx - jw / 2.0F, y0 + 5, x1 - x0 - 30, l.gaya.teks);
			y = y0 + 22;
		} else {
			Glyphs.Atlas.stempel(l.g, Glyphs.Atlas.Stempel.BINTANG, Math.round(cx - 16), y0, 32, 32, 0xFFFFFFFF);
			l.tengah(judul, cx, y0 + 36, x1 - x0 - 8, l.gaya.teks);
			y = y0 + 50;
		}
		Component penguji = u.teksAtau(GildaMisi.PENGUJI);
		if (penguji != null) {
			l.tengah(k("gilda.penguji", penguji), cx, y, x1 - x0 - 20, l.gaya.teksLemah);
			y += 11;
		}
		int lx0 = Math.round(cx - Math.min(130, (x1 - x0) / 2.0F - 8));
		int lx1 = Math.round(cx + Math.min(130, (x1 - x0) / 2.0F - 8));
		List<Lembar> syarat = u.daftar(LembarKunci.SYARAT);
		int langkah = 11;
		// rows stop above the fee line and the button; the rest is counted ("+2 syarat lagi")
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
		tombolOp(l, "ujian", lx0, by, lx1 - lx0, TOMBOL_H, k("gilda.ikut_ujian"), u.b(LembarKunci.BISA), u.teksAtau(LembarKunci.ALASAN), GildaMisi.OP_UJIAN);
	}
}
