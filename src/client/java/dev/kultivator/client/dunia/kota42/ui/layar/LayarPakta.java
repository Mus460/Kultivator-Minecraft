package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Pakta;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * {@code pakta} (DESIGN 42.11.12, keys {@link Pakta}) — a sect's protection pact over a town, on the single kit (it was a
 * set of 42 modes inside the 41 politics screen). Four tabs:
 * <ul>
 * <li>Pakta — without a pact: the NPC protector now holding the town, the requirements and the ways to take over ("rebut");
 * with one: since when, the tribute with its goods, renown, the rotation, the failures as pips, "Ambil upeti" and "Putus"
 * (asks the confirm seal). Beside it (under it in a narrow box): the guards on duty and every pact of the player.</li>
 * <li>Bela — calm or an attack in progress with the countdown, the enemy, both strengths as bars, "Datang" / "Kirim" /
 * "Tunjuk", the last result.</li>
 * <li>Murid — the disciples of the sect with their task in town (guard, work, shop) and "Tarik", or the three task buttons.</li>
 * <li>Cabang — opens the branch sheet.</li>
 * </ul>
 * The long tabs scroll with Up Down PgUp PgDn Home End; the Murid list is a keyboard list (Enter on a row does nothing, its
 * buttons are Tab stops).
 */
public final class LayarPakta extends LayarKotaDasar {

	private final GulirIsi gPakta = new GulirIsi();
	private final GulirIsi gBela = new GulirIsi();
	private final GulirIsi gCabang = new GulirIsi();
	private final DaftarGulir dMurid = new DaftarGulir();

	public LayarPakta(int npcId, CompoundTag lembar) {
		super(Pakta.LAYAR, npcId, lembar, SketsaKota.t("pakta.judul"), LembarKunci.GAYA_GIOK);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(Pakta.TAB_PAKTA, "kota.tab.pakta", LembarKunci.Ikon.CAP_MERAH), tab(Pakta.TAB_BELA, "kota.tab.bela", LembarKunci.Ikon.ARENA),
				tab(Pakta.TAB_MURID, "kota.tab.murid", LembarKunci.Ikon.LAPAK_SEKTE), tab(Pakta.TAB_CABANG, "kota.tab.cabang", LembarKunci.Ikon.KUIL));
	}

	@Override
	protected void tabBerubah() {
		dMurid.mulaiUlang(Util.getMillis());
		gPakta.atas();
		gBela.atas();
		gCabang.atas();
	}

	@Override
	public String helpTopic() {
		return KotaLembar.TOPIK_SEKTE;
	}

	@Override
	protected @Nullable GulirIsi gulirTab() {
		return switch (tab) {
			case Pakta.TAB_BELA -> gBela;
			case Pakta.TAB_MURID -> null;
			case Pakta.TAB_CABANG -> gCabang;
			default -> gPakta;
		};
	}

	private static Component namaRanah(int ordinal) {
		Realm[] all = Realm.values();
		return all[Math.max(0, Math.min(all.length - 1, ordinal))].displayName();
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		int y = KotaGambar.kota(l, lembar, x0, y0, x1);
		switch (tab) {
			case Pakta.TAB_BELA -> bela(l, x0, y, x1, y1);
			case Pakta.TAB_MURID -> murid(l, x0, y, x1, y1);
			case Pakta.TAB_CABANG -> cabang(l, x0, y, x1, y1);
			default -> pakta(l, x0, y, x1, y1);
		}
	}

	// =================================================================== Pakta

	private void pakta(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar p = lembar.sub(Pakta.PAKTA);
		int xr = x1 - 6;
		int y = gPakta.mulai(l, x0, y0, xr, y1);
		boolean dua = xr - x0 >= 300;
		int mid = x0 + (xr - x0) * 52 / 100;
		int kiriX1 = dua ? mid - 6 : xr;
		int yKiri = p.b(Pakta.ADA) ? adaPakta(l, p, x0, y, kiriX1) : belumPakta(l, x0, y, kiriX1);
		int akhir;
		if (dua) {
			akhir = Math.max(yKiri, kananPakta(l, p, mid + 6, y, xr));
		} else {
			akhir = kananPakta(l, p, x0, yKiri + 6, xr);
		}
		gPakta.selesai(l, akhir + 4);
	}

	/** No pact: the protector now, the requirements, the ways to take over. Returns the y under the column. */
	private int belumPakta(Lukis l, int x0, int y0, int x1) {
		Lembar c = lembar.sub(Pakta.CALON);
		int y = y0;
		l.teks(Brush.bold(SketsaKota.t("pakta.belum")), x0 + 2, y, x1 - x0 - 4, l.gaya.teks);
		y += 12;
		Component pel = c.teksAtau(KotaLembar.PELINDUNG);
		y += Math.round(l.bungkus(pel == null ? SketsaKota.t("pakta.tanpa_pelindung") : SketsaKota.t("pakta.pelindung_npc", pel), x0 + 2, y, x1 - x0 - 4,
				l.gaya.teksLemah)) + 4;
		for (Lembar s : c.daftar(LembarKunci.SYARAT)) {
			y += KotaGambar.syarat(l, s, x0 + 2, y, x1 - 2, Integer.MAX_VALUE);
		}
		y += 4;
		List<Lembar> cara = c.daftar(Pakta.CARA);
		if (cara.isEmpty()) {
			l.samar(SketsaKota.t("pakta.cara_kosong"), x0 + 2, y, x1 - x0 - 4);
			return y + 12;
		}
		for (Lembar r : cara) {
			tombolOp(l, "rebut" + r.id(), x0, y, x1 - x0, TOMBOL_H, r.teks(LembarKunci.NAMA), r.b(LembarKunci.BISA), r.teksAtau(LembarKunci.ALASAN),
					LembarKunci.op(Pakta.OP_REBUT, r.id()));
			y += TOMBOL_H + 3;
		}
		return y;
	}

	/** The pact: tribute, goods, renown, rotation, failures, the two buttons. Returns the y under the column. */
	private int adaPakta(Lukis l, Lembar p, int x0, int y0, int x1) {
		int y = y0;
		l.teks(Brush.bold(SketsaKota.t("pakta.ada", p.l(Pakta.SEJAK, 0L))), x0 + 2, y, x1 - x0 - 4, l.gaya.teks);
		y += 13;
		long upeti = p.l(Pakta.UPETI, 0L);
		boolean siap = p.b(Pakta.UPETI_SIAP);
		y += KotaGambar.baris(l, SketsaKota.t("pakta.upeti"), Component.literal(Brush.angka(upeti) + " NB" + (siap ? "  ✦" : "")), x0 + 2, y, x1 - 2,
				siap ? KotaGambar.EMAS : l.gaya.teks);
		for (Lembar b : p.daftar(Pakta.BARANG_UPETI)) {
			if (!b.barang().isEmpty()) {
				l.g.item(b.barang(), x0 + 2, y - 3);
			}
			l.teks(b.teks(LembarKunci.NAMA).copy().append(" ×" + b.i(LembarKunci.JUMLAH, 1)), x0 + 22, y + 1, x1 - x0 - 24, l.gaya.teksLemah);
			y += 16;
		}
		y += KotaGambar.baris(l, SketsaKota.t("pakta.renown"), Component.literal("+" + p.i(Pakta.RENOWN, 0)), x0 + 2, y, x1 - 2, l.gaya.teks);
		y += KotaGambar.baris(l, SketsaKota.t("pakta.rotasi"), SketsaKota.t("pakta.rotasi_hari", p.i(Pakta.ROTASI, 3)), x0 + 2, y, x1 - 2, l.gaya.teks);
		// failures as pips
		int gagal = p.i(Pakta.GAGAL, 0);
		int gmaks = Math.max(1, p.i(Pakta.GAGAL_MAKS, 3));
		l.teks(SketsaKota.t("pakta.gagal"), x0 + 2, y, x1 - x0 - gmaks * 9 - 12, l.gaya.teksLemah);
		for (int i = 0; i < gmaks; i++) {
			Brush.disc(l.g, x1 - 6 - (gmaks - 1 - i) * 9, y + 4, 3.0F, i < gagal ? Palette.BAHAYA : Palette.argb(0x50, 0x000000));
		}
		y += 15;
		boolean sempit = x1 - x0 < 200;
		Component ambil = SketsaKota.t("pakta.ambil_upeti");
		Component putus = SketsaKota.t("pakta.putus");
		if (sempit) {
			tombolOp(l, "upeti", x0, y, x1 - x0, TOMBOL_H, ambil, siap, SketsaKota.t("pakta.upeti_belum"), Pakta.OP_AMBIL_UPETI);
			yakin(l, "putus", x0, y + TOMBOL_H + 3, x1 - x0, TOMBOL_H, putus, true, null, Pakta.OP_PUTUS_YA, SegelKonfirmasi.Tingkat.SEDANG, yakinTeks(putus));
			return y + 2 * TOMBOL_H + 6;
		}
		int bw = (x1 - x0 - 4) / 2;
		tombolOp(l, "upeti", x0, y, bw, TOMBOL_H, ambil, siap, SketsaKota.t("pakta.upeti_belum"), Pakta.OP_AMBIL_UPETI);
		yakin(l, "putus", x0 + bw + 4, y, bw, TOMBOL_H, putus, true, null, Pakta.OP_PUTUS_YA, SegelKonfirmasi.Tingkat.SEDANG, yakinTeks(putus));
		return y + TOMBOL_H + 3;
	}

	/** The guards on duty and every pact of the player; returns the y under the column. */
	private int kananPakta(Lukis l, Lembar p, int x0, int y0, int x1) {
		List<Lembar> penjaga = p.daftar(Pakta.PENJAGA);
		int y = y0 + l.kepala(SketsaKota.t("pakta.penjaga", penjaga.size()), x0, y0, x1) + 1;
		if (penjaga.isEmpty()) {
			l.samar(SketsaKota.t("pakta.penjaga_kosong"), x0 + 2, y, x1 - x0 - 4);
			y += 12;
		}
		for (Lembar g : penjaga) {
			Component sisa = SketsaKota.t("pakta.sisa_hari", g.i(LembarKunci.SISA_HARI, 0));
			int sw = l.font.width(sisa) + 4;
			Component ranah = namaRanah(g.i(KotaLembar.RANAH, 0));
			int rw = Math.min(l.font.width(ranah) + 4, (x1 - x0) / 3);
			int nw = x1 - x0 - sw - rw - 8;
			l.teks(g.teks(LembarKunci.NAMA), x0 + 2, y, nw, l.gaya.teks);
			l.teks(ranah, x0 + 4 + nw, y, rw, l.gaya.teksLemah);
			l.kanan(sisa, x1, y, l.gaya.teksSamar);
			y += 11;
		}
		y += 4;
		List<Lembar> semua = lembar.daftar(Pakta.DAFTAR_PAKTA);
		if (!semua.isEmpty()) {
			y += l.kepala(SketsaKota.t("pakta.semua", semua.size()), x0, y, x1) + 1;
			for (Lembar d : semua) {
				String bela = d.s(Pakta.BELA);
				boolean darurat = Pakta.BL_PANGGILAN.equals(bela) || Pakta.BL_BERLANGSUNG.equals(bela);
				int kanan = (d.b(Pakta.UPETI_SIAP) ? 12 : 0) + (darurat ? 12 : 0);
				l.teks(d.teks(LembarKunci.TEMPAT), x0 + 2, y, x1 - x0 - 4 - kanan, darurat ? Palette.BAHAYA : l.gaya.teks);
				int ix = x1;
				if (darurat) {
					ix -= 10;
					Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.LONCENG, ix, y, 9, 0xFFFFFFFF);
				}
				if (d.b(Pakta.UPETI_SIAP)) {
					ix -= 12;
					l.kanan(Component.literal("✦"), ix + 10, y, KotaGambar.EMAS);
				}
				y += 11;
			}
		}
		return y;
	}

	// =================================================================== Bela

	private void bela(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar b = lembar.sub(Pakta.BELA);
		if (b.kosongSemua()) {
			kosong(l, SketsaKota.t("pakta.bela_kosong"), x0, y0, x1, y1);
			return;
		}
		String keadaan = b.s(LembarKunci.KEADAAN, Pakta.BL_TENANG);
		boolean tenang = Pakta.BL_TENANG.equals(keadaan);
		int bh = tenang ? 0 : TOMBOL_H + 7;
		int bawah = y1 - bh;
		int xr = x1 - 6;
		int y = gBela.mulai(l, x0, y0, xr, bawah - 1);
		int warna = tenang ? Palette.OK : Pakta.BL_BERLANGSUNG.equals(keadaan) ? Palette.BAHAYA : Palette.PERINGATAN;
		float pulse = tenang ? 1.0F : 0.6F + 0.4F * l.denyut();
		l.g.fill(x0, y, xr, y + 16, Palette.argb(Math.round(0xB0 * pulse), Palette.darken(warna, 0.35F) & 0xFFFFFF));
		l.g.fill(x0, y, x0 + 3, y + 16, warna);
		long sisa = b.l(LembarKunci.SISA_TICK, 0L);
		Component hitung = null;
		if (!tenang && sisa > 0L) {
			long left = Math.max(0L, sisa - (Util.getMillis() - diterima) / 50L);
			hitung = SketsaKota.t("pakta.bela_sisa", Brush.waktu(left));
		}
		int hw = hitung == null ? 0 : l.font.width(hitung) + 8;
		l.teks(Brush.bold(SketsaKota.t("pakta.bela." + keadaan)), x0 + 7, y + 4, xr - x0 - 10 - hw, 0xFFFFF4E0);
		if (hitung != null) {
			l.kanan(hitung, xr - 5, y + 4, 0xFFFFE0C0);
		}
		y += 22;
		if (!tenang) {
			String jenis = b.s(LembarKunci.JENIS);
			y += Math.round(l.bungkus(SketsaKota.t("pakta.musuh", SketsaKota.t("pakta.musuh." + jenis)), x0, y, xr - x0, l.gaya.teks)) + 3;
			int musuh = b.i(Pakta.KEKUATAN, 0);
			int kita = b.i(Pakta.KEKUATAN_KITA, 0);
			float maks = Math.max(1, Math.max(musuh, kita));
			Component lm = SketsaKota.t("pakta.kekuatan_musuh", musuh);
			Component lk = SketsaKota.t("pakta.kekuatan_kita", kita);
			int lw = Math.min(110, Math.max(l.font.width(lm), l.font.width(lk)) + 6);
			l.teks(lm, x0, y, lw, l.gaya.teksLemah);
			Bilah.gambar(l, x0 + lw, y + 3, xr, musuh / maks, Palette.BAHAYA, new float[0]);
			y += 11;
			l.teks(lk, x0, y, lw, l.gaya.teksLemah);
			Bilah.gambar(l, x0 + lw, y + 3, xr, kita / maks, Palette.OK, new float[0]);
			y += 14;
			y += Math.round(l.bungkus(SketsaKota.t("pakta.bela_info"), x0, y, xr - x0, l.gaya.teksSamar)) + 4;
		} else {
			y += Math.round(l.bungkus(SketsaKota.t("pakta.tenang_info"), x0, y, xr - x0, l.gaya.teksLemah)) + 4;
		}
		Lembar terakhir = b.sub(Pakta.TERAKHIR);
		if (!terakhir.kosongSemua()) {
			boolean menang = terakhir.b(Pakta.MENANG);
			y += Math.round(l.bungkus(SketsaKota.t(menang ? "pakta.terakhir_menang" : "pakta.terakhir_kalah", terakhir.l(LembarKunci.HARI, 0L)), x0, y, xr - x0,
					menang ? Palette.OK : Palette.BAHAYA)) + 3;
		}
		gBela.selesai(l, y);
		if (!tenang) {
			int bw = (x1 - x0 - 8) / 3;
			int by = bawah + 2;
			tombolOp(l, "datang", x0, by, bw, TOMBOL_H, SketsaKota.t("pakta.datang"), true, null, LembarKunci.op(Pakta.OP_BELA, Pakta.BD_DATANG));
			tombolOp(l, "kirim", x0 + bw + 4, by, bw, TOMBOL_H, SketsaKota.t("pakta.kirim"), true, null, LembarKunci.op(Pakta.OP_BELA, Pakta.BD_KIRIM));
			tombolOp(l, "tunjukBela", x0 + 2 * bw + 8, by, bw, TOMBOL_H, SketsaKota.t("tunjuk"), true, null,
					LembarKunci.op(LembarKunci.OP_TANDAI, b.i(LembarKunci.X, 0), b.i(LembarKunci.Z, 0)));
		}
	}

	// =================================================================== Murid

	private void murid(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Pakta.MURID);
		if (rows.isEmpty()) {
			kosong(l, SketsaKota.t("pakta.murid_kosong"), x0, y0, x1, y1);
			return;
		}
		String[] tugas = {Pakta.TK_JAGA_KOTA, Pakta.TK_KERJA_KOTA, Pakta.TK_BELANJA};
		boolean sempit = x1 - x0 < 300;
		dMurid.gambar(l, "murid42", x0, y0, x1, y1, rows.size(), sempit ? 42 : 28, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			String t = e.s(Pakta.TUGAS);
			int namaW = sempit ? rx1 - rx0 - 10 : Math.min(130, (rx1 - rx0) / 3);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 5, ry0 + 3, namaW, lk.gaya.teks);
			lk.teks(namaRanah(e.i(KotaLembar.RANAH, 0)), rx0 + 5, ry0 + 15, namaW, lk.gaya.teksLemah);
			int bx = sempit ? rx0 + 5 : rx0 + namaW + 12;
			int by = sempit ? ry0 + 26 : ry0 + (ry1 - ry0 - TOMBOL_H) / 2;
			if (!t.isEmpty()) {
				Component tarik = SketsaKota.t("pakta.tarik");
				int tw = TalismanButton.lebar(lk.font, tarik, 52, 90);
				if (sempit) {
					Component kerja = SketsaKota.t("pakta.tugas." + t).copy().append(" · ").append(e.teks(LembarKunci.TEMPAT));
					lk.teks(kerja, rx0 + 5, by + 3, rx1 - rx0 - tw - 18, KotaGambar.EMAS);
				} else {
					int teksW = rx1 - bx - tw - 10;
					lk.teks(SketsaKota.t("pakta.tugas." + t), bx, ry0 + 3, teksW, KotaGambar.EMAS);
					lk.teks(e.teks(LembarKunci.TEMPAT), bx, ry0 + 15, teksW, lk.gaya.teksSamar);
				}
				tombolOp(lk, "tarik" + i, rx1 - tw - 4, by, tw, TOMBOL_H, tarik, true, null, LembarKunci.op(Pakta.OP_TARIK_MURID, e.s(KotaLembar.SOUL)));
			} else {
				int w = Math.max(34, Math.min(80, (rx1 - bx - 6 - 2 * 3) / 3));
				for (int n = 0; n < tugas.length; n++) {
					tombolOp(lk, "tugas" + i + "_" + n, bx + n * (w + 3), by, w, TOMBOL_H, SketsaKota.t("pakta.tugas_pendek." + tugas[n]), e.b(LembarKunci.BISA),
							e.teksAtau(LembarKunci.ALASAN), LembarKunci.op(Pakta.OP_TUGASKAN, e.s(KotaLembar.SOUL), tugas[n]));
				}
			}
		}, null, null);
	}

	// =================================================================== Cabang

	private void cabang(Lukis l, int x0, int y0, int x1, int y1) {
		int xr = x1 - 6;
		int y = gCabang.mulai(l, x0, y0, xr, y1);
		Component info = SketsaKota.t("pakta.cabang_info");
		int tw = xr - x0 - 40;
		int ih = Math.max(10, l.font.split(info, Math.max(10, tw)).size() * 9);
		int tinggi = 6 + 11 + ih + 4 + TOMBOL_H + 6;
		l.kartu(x0, y, xr, y + tinggi, 0.0F, false);
		Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.KUIL, x0 + 6, y + 6, 24, 0xFFFFFFFF);
		l.teks(Brush.bold(SketsaKota.t("pakta.cabang_judul")), x0 + 36, y + 5, tw, l.gaya.teks);
		l.bungkus(info, x0 + 36, y + 16, tw, l.gaya.teksLemah);
		Component buka = SketsaKota.t("pakta.cabang_buka");
		int bw = Math.min(xr - x0 - 42, TalismanButton.lebar(l.font, buka, 120, 200));
		tombolOp(l, "cabang42", x0 + 36, y + 16 + ih + 4, bw, TOMBOL_H, buka, true, null, Pakta.OP_CABANG);
		gCabang.selesai(l, y + tinggi + 4);
	}
}
