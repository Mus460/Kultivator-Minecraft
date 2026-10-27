package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihJumlah;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Jabatan;
import dev.kultivator.dunia.kota42.ui.LembarJabatan;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * {@code jabatan} (DESIGN 42.11.9, keys {@link Jabatan}) — the player's office in a town, on the single kit (it was the
 * court screen's half-built 42 mode). Four tabs:
 * <ul>
 * <li>Jabatan — the rank seal with the title and the last wage against the cap, attendance and the absence warning, the
 * duties of the week with their bars, the two duty buttons (inspect the scales, hire a guard), the rights per rank, and the
 * next rank with every requirement and "Ajukan". Wide boxes show two columns, narrow ones one scrolled column.</li>
 * <li>Pajak — the rate picker within the rank's band (the band shaded on its scale, 10 % the break-even mark), what the rate
 * does, the festival budget of a Wali Kota, and the last weeks' income and spending as bars.</li>
 * <li>Perkara — open and decided cases (Enter opens one); Kavling — the town's growth lots (Enter opens one).</li>
 * </ul>
 * Every row, picker and button is a Tab stop; the long tabs scroll with the keys Up Down PgUp PgDn Home End.
 */
public final class LayarJabatan extends LayarKotaDasar {

	private static final int LEBAR_DUA = 300;

	private final GulirIsi gJabatan = new GulirIsi();
	private final GulirIsi gPajak = new GulirIsi();
	private final DaftarGulir dPerkara = new DaftarGulir();
	private final DaftarGulir dKavling = new DaftarGulir();
	private final PemilihJumlah pTarif = new PemilihJumlah();
	private final PemilihJumlah pFestival = new PemilihJumlah().min(0);
	private int tarifServer = Integer.MIN_VALUE;
	private long festivalServer = Long.MIN_VALUE;

	public LayarJabatan(int npcId, CompoundTag lembar) {
		super(Jabatan.LAYAR, npcId, lembar, SketsaKota.t("jabatan.judul"), LembarKunci.GAYA_BATU);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(Jabatan.TAB_JABATAN, "kota.tab.jabatan", LembarKunci.Ikon.CAP_MERAH),
				tab(Jabatan.TAB_PAJAK, "kota.tab.pajak", LembarKunci.Ikon.TIMBANGAN),
				tab(Jabatan.TAB_PERKARA, "kota.tab.perkara", LembarKunci.Ikon.YAMEN),
				tab(Jabatan.TAB_KAVLING, "kota.tab.kavling", Glyphs.Atlas.TONGGAK));
	}

	@Override
	protected void tabBerubah() {
		long kini = Util.getMillis();
		dPerkara.mulaiUlang(kini);
		dKavling.mulaiUlang(kini);
		gJabatan.atas();
		gPajak.atas();
	}

	@Override
	protected void setelahLembar(boolean buka) {
		Lembar p = lembar.sub(Jabatan.PAJAK);
		int tarif = p.i(Jabatan.TARIF, 8);
		long festival = p.l(Jabatan.FESTIVAL, 0L);
		// the picker follows the server's value when a new sheet carries another one (after "Tetapkan" or a rank change)
		if (buka || tarif != tarifServer) {
			tarifServer = tarif;
			pTarif.set(tarif);
		}
		if (buka || festival != festivalServer) {
			festivalServer = festival;
			pFestival.set((int) Math.min(Integer.MAX_VALUE, Math.max(0L, festival)));
		}
	}

	@Override
	public String helpTopic() {
		return KotaLembar.TOPIK_EKONOMI;
	}

	@Override
	protected @Nullable GulirIsi gulirTab() {
		return switch (tab) {
			case Jabatan.TAB_PAJAK -> gPajak;
			case Jabatan.TAB_PERKARA, Jabatan.TAB_KAVLING -> null;
			default -> gJabatan;
		};
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		int y = KotaGambar.kota(l, lembar, x0, y0, x1);
		switch (tab) {
			case Jabatan.TAB_PAJAK -> pajak(l, x0, y, x1, y1);
			case Jabatan.TAB_PERKARA -> daftarPerkara(l, x0, y, x1, y1);
			case Jabatan.TAB_KAVLING -> daftarKavling(l, x0, y, x1, y1);
			default -> kartuJabatan(l, x0, y, x1, y1);
		}
	}

	// =================================================================== Jabatan

	private void kartuJabatan(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar j = lembar.sub(Jabatan.JABATAN);
		int xr = x1 - 6;
		int y = gJabatan.mulai(l, x0, y0, xr, y1);
		boolean dua = xr - x0 >= LEBAR_DUA;
		int mid = x0 + (xr - x0) * 52 / 100;
		int yKiri = kolomKiri(l, j, x0, y, dua ? mid - 6 : xr);
		int yAkhir;
		if (dua) {
			yAkhir = Math.max(yKiri, kolomKanan(l, j, mid + 6, y, xr));
		} else {
			yAkhir = kolomKanan(l, j, x0, yKiri + 6, xr);
		}
		gJabatan.selesai(l, yAkhir + 4);
	}

	/** Rank seal, wage, attendance, duties and the two duty buttons; returns the y under the column. */
	private int kolomKiri(Lukis l, Lembar j, int x0, int y0, int x1) {
		int pangkat = j.i(Jabatan.PANGKAT, 0);
		int tinggiKartu = 38;
		l.kartu(x0, y0, x1, y0 + tinggiKartu, 0.0F, false);
		int ss = 30;
		int sx = x0 + 4;
		int sy = y0 + 4;
		int segel = pangkat > 0 ? Palette.SEAL : Palette.LOCKED;
		Glyphs.Atlas.stempel(l.g, Glyphs.Atlas.Stempel.PERSEGI, sx, sy, ss, ss, Palette.argb(0xE8, segel & 0xFFFFFF));
		Brush.centered(l.g, l.font, Component.literal(pangkat > 0 ? Integer.toString(pangkat) : "—"), sx + ss / 2.0F, sy + ss / 2.0F - 6, 0xFFFFF4E0, 1.5F, true);
		Lembar c = lembar.sub(LembarKunci.CATATAN);
		if (pangkat >= Jabatan.HAKIM_PEMBANTU && !c.kosongSemua() && l.di(sx, sy, sx + ss, sy + ss)) {
			l.tip(SketsaKota.t("jabatan.catatan", c.i(Jabatan.DIPUTUS, 0), c.i(Jabatan.SUAP, 0), c.i(Jabatan.AUDIT, 0)));
		}
		int tx = sx + ss + 6;
		l.teks(Brush.bold(j.teks(LembarKunci.GELAR)), tx, sy + 1, x1 - tx - 4, l.gaya.teks);
		long gaji = j.l(Jabatan.GAJI, 0L);
		long gajiMaks = j.l(Jabatan.GAJI_MAKS, 0L);
		l.teks(SketsaKota.t("jabatan.gaji", Brush.angka(gaji), Brush.angka(gajiMaks)), tx, sy + 12, x1 - tx - 4, l.gaya.teksLemah);
		Bilah.gambar(l, tx, sy + 25, x1 - 6, gajiMaks <= 0 ? 0.0F : gaji / (float) gajiMaks, Palette.GOLD, new float[0]);
		int y = y0 + tinggiKartu + 4;
		if (pangkat > 0) {
			long hadir = j.l(Jabatan.HADIR, -1L);
			l.teks(hadir < 0 ? SketsaKota.t("jabatan.hadir_belum") : SketsaKota.t("jabatan.hadir", hadir), x0 + 2, y, x1 - x0 - 4, l.gaya.teksLemah);
			y += 11;
		}
		Component warn = j.teksAtau(Jabatan.PERINGATAN);
		if (warn != null) {
			Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.LONCENG, x0 + 2, y - 1, 10, 0xFFFFFFFF);
			l.teks(warn, x0 + 15, y, x1 - x0 - 17, Palette.BAHAYA);
			y += 11;
		}
		y += l.kepala(SketsaKota.t("jabatan.tugas"), x0, y, x1) + 1;
		List<Lembar> tugas = j.daftar(Jabatan.TUGAS);
		if (tugas.isEmpty()) {
			l.samar(SketsaKota.t("jabatan.tugas_kosong"), x0 + 2, y, x1 - x0 - 4);
			y += 11;
		}
		for (Lembar t : tugas) {
			int maks = t.i(LembarKunci.MAKS, 0);
			boolean selesai = t.b(Jabatan.SELESAI);
			Glyphs.Atlas.ikon(l.g, maks > 0 && selesai ? Glyphs.Atlas.CENTANG : Glyphs.Atlas.TONGGAK, x0 + 2, y, 9, 0xFFFFFFFF);
			int kw = 0;
			if (maks > 0) {
				int kemajuan = t.i(LembarKunci.KEMAJUAN, 0);
				Component angka = Component.literal(kemajuan + "/" + maks);
				kw = l.font.width(angka) + 4;
				l.kanan(angka, x1 - 2, y, l.gaya.teksLemah);
				Bilah.gambar(l, x0 + 14, y + 10, x1 - 2, kemajuan / (float) maks, selesai ? Palette.OK : Palette.PERINGATAN, new float[0]);
			}
			l.teks(t.teks(LembarKunci.TEKS), x0 + 14, y, x1 - x0 - 16 - kw, selesai ? l.gaya.teksSamar : l.gaya.teks);
			y += maks > 0 ? 17 : 11;
		}
		// duty buttons, one per row so a narrow box never clips a label
		if (pangkat >= Jabatan.PENGAWAS_PASAR) {
			boolean periksa = j.b(LembarJabatan.BOLEH_PERIKSA);
			tombolOp(l, "periksa", x0, y + 2, x1 - x0, TOMBOL_H, SketsaKota.t("jabatan.periksa"), periksa, SketsaKota.t("jabatan.periksa_habis"),
					LembarJabatan.OP_PERIKSA);
			y += TOMBOL_H + 5;
		}
		if (pangkat >= Jabatan.WALI_KOTA) {
			int pj = j.i(LembarJabatan.PENJAGA, 0);
			int pjm = j.i(LembarJabatan.PENJAGA_MAKS, 6);
			tombolOp(l, "penjaga", x0, y + 2, x1 - x0, TOMBOL_H, SketsaKota.t("jabatan.penjaga", pj, pjm), pj < pjm, SketsaKota.t("jabatan.penjaga_penuh"),
					LembarJabatan.OP_PENJAGA);
			y += TOMBOL_H + 5;
		}
		return y;
	}

	/** Rights per rank and the next rank with its requirements and "Ajukan"; returns the y under the column. */
	private int kolomKanan(Lukis l, Lembar j, int x0, int y0, int x1) {
		int y = y0 + l.kepala(SketsaKota.t("jabatan.hak"), x0, y0, x1) + 1;
		for (Lembar h : j.daftar(Jabatan.HAK)) {
			boolean ok = h.b(LembarKunci.OK);
			int no = h.i(Jabatan.PANGKAT, 0);
			l.g.fill(x0 + 1, y, x0 + 10, y + 9, ok ? Palette.SEAL : Palette.argb(0x60, 0x000000));
			Brush.centered(l.g, l.font, Component.literal(Integer.toString(no)), x0 + 5.5F, y, ok ? 0xFFFFF4E0 : l.gaya.teksSamar, 1.0F, false);
			l.teks(h.teks(LembarKunci.TEKS), x0 + 14, y, x1 - x0 - 16, ok ? l.gaya.teks : l.gaya.teksSamar);
			y += 11;
		}
		Lembar berikut = j.sub(Jabatan.BERIKUT);
		if (berikut.kosongSemua()) {
			l.teks(Brush.bold(SketsaKota.t("jabatan.puncak")), x0 + 2, y + 4, x1 - x0 - 4, KotaGambar.EMAS);
			return y + 16;
		}
		y += 5;
		int dalam = x0 + 5;
		int dalamKanan = x1 - 5;
		Component titel = Brush.bold(SketsaKota.t("jabatan.berikut", berikut.teks(LembarKunci.GELAR)));
		int th = Math.min(40, Math.max(1, l.font.split(titel, Math.max(10, dalamKanan - dalam)).size()) * 9);
		List<Lembar> syarat = berikut.daftar(LembarKunci.SYARAT);
		int tinggi = 4 + th + 3 + syarat.size() * 11 + 3 + TOMBOL_H + 5;
		l.sumur(x0, y, x1, y + tinggi);
		int yy = y + 4;
		l.bungkus(titel, dalam, yy, dalamKanan - dalam, l.gaya.teksSumur(), 40.0F);
		yy += th + 3;
		for (Lembar s : syarat) {
			yy += KotaGambar.syarat(l, s, dalam, yy, dalamKanan, Integer.MAX_VALUE);
		}
		tombolOp(l, "ajukan", dalam, yy + 3, dalamKanan - dalam, TOMBOL_H, SketsaKota.t("jabatan.ajukan"), berikut.b(LembarKunci.BISA),
				berikut.teksAtau(LembarKunci.ALASAN), Jabatan.OP_AJUKAN);
		return y + tinggi + 2;
	}

	// =================================================================== Pajak

	private void pajak(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar p = lembar.sub(Jabatan.PAJAK);
		int min = p.i(Jabatan.MIN, 5);
		int maks = Math.max(min, p.i(LembarKunci.MAKS, 20));
		int sekarang = p.i(Jabatan.TARIF, 8);
		boolean bisa = p.b(LembarKunci.BISA);
		Component alasan = p.teksAtau(LembarKunci.ALASAN);
		int xr = x1 - 6;
		int y = gPajak.mulai(l, x0, y0, xr, y1);
		boolean dua = xr - x0 >= LEBAR_DUA;
		int mid = x0 + (xr - x0) * 50 / 100;
		int kiriKanan = dua ? mid - 6 : xr;
		int yKiri = kolomTarif(l, p, x0, y, kiriKanan, min, maks, sekarang, bisa, alasan);
		int yAkhir;
		if (dua) {
			yAkhir = Math.max(yKiri, kolomRiwayat(l, p, mid + 6, y, xr));
		} else {
			yAkhir = kolomRiwayat(l, p, x0, yKiri + 6, xr);
		}
		gPajak.selesai(l, yAkhir + 4);
	}

	private int kolomTarif(Lukis l, Lembar p, int x0, int y0, int x1, int min, int maks, int sekarang, boolean bisa, @Nullable Component alasan) {
		pTarif.min(min);
		int tarif = Math.max(min, Math.min(maks, pTarif.nilai()));
		int y = y0 + l.kepala(SketsaKota.t("pajak.tarif"), x0, y0, x1) + 2;
		// the picked rate, big, over the scale of 5-20 % with the allowed band shaded
		Brush.centered(l.g, l.font, Component.literal(tarif + " %"), (x0 + x1) / 2.0F, y, tarif > 10 ? Palette.BAHAYA : l.gaya.teks, 1.5F, false);
		y += 16;
		int sx0 = x0 + 8;
		int sx1 = x1 - 8;
		int sy = y + 4;
		l.g.fill(sx0, sy, sx1, sy + 4, Palette.argb(0x50, 0x000000));
		int bx0 = sx0 + Math.round((sx1 - sx0) * (min - 5) / 15.0F);
		int bx1 = sx0 + Math.round((sx1 - sx0) * (maks - 5) / 15.0F);
		l.g.fill(bx0, sy, Math.max(bx0 + 1, bx1), sy + 4, Palette.argb(0xA0, 0x5FA88C));
		int mx = sx0 + Math.round((sx1 - sx0) * 5 / 15.0F);
		l.g.fill(mx, sy - 3, mx + 1, sy + 7, Palette.PERINGATAN);
		int nx = sx0 + Math.round((sx1 - sx0) * (sekarang - 5) / 15.0F);
		Brush.disc(l.g, nx + 0.5F, sy + 2, 2.5F, l.gaya.teksSamar);
		int px = sx0 + Math.round((sx1 - sx0) * (tarif - 5) / 15.0F);
		Brush.disc(l.g, px + 0.5F, sy + 2, 4.0F, Palette.SEAL);
		if (l.di(sx0, sy - 4, sx1, sy + 8)) {
			l.tip(SketsaKota.t("pajak.skala", min, maks, sekarang));
		}
		y = sy + 12;
		y += Math.round(l.bungkus(p.teks(Jabatan.EFEK), x0 + 2, y, x1 - x0 - 4, l.gaya.teksLemah, 30.0F)) + 4;
		int pw = pTarif.gambar(l, "tarif_n", x0 + 2, y, maks);
		int tombolX = x0 + 2 + pw + 4;
		tombolOp(l, "tarif", tombolX, y, Math.max(40, x1 - tombolX), TOMBOL_H, SketsaKota.t("pajak.tetapkan", tarif), bisa && tarif != sekarang,
				alasan != null ? alasan : SketsaKota.t("pajak.sama"), LembarKunci.op(Jabatan.OP_PAJAK, tarif));
		y += TOMBOL_H + 6;
		if (p.ada(Jabatan.FESTIVAL)) {
			long fmaks = Math.max(1L, p.l(LembarKunci.HARGA, 400L));
			int fm = (int) Math.min(Integer.MAX_VALUE, fmaks);
			long festival = pFestival.nilai();
			y += l.kepala(SketsaKota.t("pajak.festival_judul"), x0, y, x1) + 1;
			l.teks(SketsaKota.t("pajak.festival", Brush.angka(festival), Brush.angka(fmaks)), x0 + 2, y, x1 - x0 - 4, l.gaya.teksLemah);
			y += 12;
			int fw = pFestival.gambar(l, "festival_n", x0 + 2, y, fm);
			int fx = x0 + 2 + fw + 4;
			tombolOp(l, "festival", fx, y, Math.max(40, x1 - fx), TOMBOL_H, SketsaKota.t("pajak.festival_simpan"),
					bisa && festival != p.l(Jabatan.FESTIVAL, 0L), alasan != null ? alasan : SketsaKota.t("pajak.sama"),
					LembarKunci.op(Jabatan.OP_FESTIVAL, festival));
			y += TOMBOL_H + 6;
		}
		return y;
	}

	private int kolomRiwayat(Lukis l, Lembar p, int x0, int y0, int x1) {
		int y = riwayat(l, SketsaKota.t("pajak.masuk"), p.daftar(Jabatan.MASUK), x0, y0, x1, Palette.OK);
		return riwayat(l, SketsaKota.t("pajak.keluar"), p.daftar(Jabatan.KELUAR), x0, y + 6, x1, Palette.BAHAYA);
	}

	/** One week-bar chart (newest left) with a tooltip per bar; returns the y under it. */
	private int riwayat(Lukis l, Component judul, List<Lembar> rows, int x0, int y0, int x1, int warna) {
		int y = y0 + l.kepala(judul, x0, y0, x1) + 2;
		if (rows.isEmpty()) {
			l.samar(SketsaKota.t("pajak.riwayat_kosong"), x0 + 2, y, x1 - x0 - 4);
			return y + 12;
		}
		long maks = 1L;
		for (Lembar r : rows) {
			maks = Math.max(maks, r.l(LembarKunci.HARGA, 0L));
		}
		int n = rows.size();
		int gap = 3;
		int bw = Math.max(4, (x1 - x0 - (n - 1) * gap) / Math.max(1, n));
		int tinggi = 40;
		int base = y + tinggi;
		for (int i = 0; i < n; i++) {
			long v = rows.get(i).l(LembarKunci.HARGA, 0L);
			int h = Math.max(1, Math.round((tinggi - 2) * v / (float) maks));
			int bx = x0 + i * (bw + gap);
			l.g.fillGradient(bx, base - h, bx + bw, base, warna, Palette.darken(warna, 0.35F));
			if (l.di(bx, base - tinggi, bx + bw, base)) {
				l.tip(List.of(rows.get(i).teks(LembarKunci.TEKS), Component.literal(Brush.angka(v) + " NB")));
			}
		}
		l.g.fill(x0, base, x1, base + 1, Palette.argb(0x80, l.gaya.teksSamar));
		l.teks(SketsaKota.t("pajak.minggu_ini", Brush.angka(rows.get(0).l(LembarKunci.HARGA, 0L))), x0 + 2, base + 3, x1 - x0 - 4, l.gaya.teksLemah);
		return base + 15;
	}

	// =================================================================== Perkara, Kavling

	private void daftarPerkara(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Jabatan.PERKARA);
		Lembar c = lembar.sub(LembarKunci.CATATAN);
		l.teks(SketsaKota.t("jabatan.catatan", c.i(Jabatan.DIPUTUS, 0), c.i(Jabatan.SUAP, 0), c.i(Jabatan.AUDIT, 0)), x0, y0, x1 - x0, l.gaya.teksSamar);
		int y = y0 + 12;
		if (rows.isEmpty()) {
			kosong(l, SketsaKota.t("jabatan.perkara_kosong"), x0, y, x1, y1);
			return;
		}
		dPerkara.gambar(l, "perkara", x0, y, x1, y1, rows.size(), 30, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			boolean selesai = e.b(Jabatan.SELESAI);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			Glyphs.Atlas.ikon(lk.g, selesai ? LembarKunci.Ikon.CAP_MERAH : LembarKunci.Ikon.TIMBANGAN, rx0 + 4, ry0 + 7, 16, 0xFFFFFFFF);
			int kanan = Math.min(70, (rx1 - rx0) / 3);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 25, ry0 + 3, rx1 - rx0 - 29 - kanan, selesai ? lk.gaya.teksLemah : lk.gaya.teks);
			Component bawah = selesai ? SketsaKota.t("perkara.diputus", e.teks(Jabatan.PUTUSAN))
					: SketsaKota.t("perkara.baris", e.i(Jabatan.SAKSI, 0), e.i(Jabatan.BUKTI, 0));
			lk.teks(bawah, rx0 + 25, ry0 + 16, rx1 - rx0 - 29 - kanan, lk.gaya.teksLemah);
			if (!selesai) {
				int sisa = e.i(LembarKunci.SISA_HARI, 0);
				lk.kanan(SketsaKota.t("perkara.sisa", sisa), rx1 - 5, ry0 + 3, sisa <= 1 ? Palette.BAHAYA : lk.gaya.teksLemah);
			}
			lk.kanan(SketsaKota.t(selesai ? "perkara.lihat" : "perkara.sidang"), rx1 - 5, ry0 + 16, KotaGambar.EMAS);
		}, () -> {
			int i = dPerkara.pilihan();
			if (i >= 0 && i < rows.size()) {
				kirim(LembarKunci.op(Jabatan.OP_PERKARA, rows.get(i).id()));
			}
		}, null);
	}

	private void daftarKavling(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Jabatan.TAB_KAVLING);
		if (rows.isEmpty()) {
			kosong(l, SketsaKota.t("kavling.kosong"), x0, y0, x1, y1);
			return;
		}
		boolean sempit = x1 - x0 < 260;
		dKavling.gambar(l, "kavling", x0, y0, x1, y1, rows.size(), sempit ? 34 : 28, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			String keadaan = e.s(LembarKunci.KEADAAN);
			IkonKota.gambar(lk.g, IkonKota.KAVLING, rx0 + 4, ry0 + (ry1 - ry0 - 14) / 2, 14);
			int tahap = e.i("tahap", 0);
			int total = Math.max(1, e.i(LembarKunci.TAHAP_TOTAL, 5));
			int segelLebar = total * 9;
			int kiriLebar = sempit ? rx1 - rx0 - 28 : (rx1 - rx0) / 2 - 24;
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 23, ry0 + 3, kiriLebar, lk.gaya.teks);
			lk.teks(e.teks(KotaLembar.Kavling.RENCANA), rx0 + 23, ry0 + 14, kiriLebar, lk.gaya.teksLemah);
			int bx = sempit ? rx0 + 23 : rx0 + (rx1 - rx0) / 2 + 4;
			int by = sempit ? ry0 + 25 : ry0 + 5;
			for (int s = 0; s < total; s++) {
				int c = s < tahap ? 0xFF5FA88C : Palette.argb(0x50, 0x000000);
				lk.g.fill(bx + s * 9, by, bx + s * 9 + 7, by + 5, c);
			}
			Component k = SketsaKota.t("kavling.keadaan." + keadaan);
			int kx = sempit ? bx + segelLebar + 4 : bx;
			int ky = sempit ? by - 2 : ry0 + 14;
			lk.teks(k, kx, ky, rx1 - kx - 4, KotaLembar.Kavling.KV_TIDAK_LAYAK.equals(keadaan) ? Palette.BAHAYA : lk.gaya.teksSamar);
		}, () -> {
			int i = dKavling.pilihan();
			if (i >= 0 && i < rows.size()) {
				kirim(LembarKunci.op(Jabatan.OP_KAVLING, Integer.toString(rows.get(i).i(LembarKunci.ID, 0))));
			}
		}, null);
	}
}
