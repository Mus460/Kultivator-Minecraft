package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Potret;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Kantor;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kantor_rekrut} (DESIGN 42.11.11, keys {@link Kantor}) — the sect's recruitment office in a town, on the single kit
 * (it was the recruitment stall's half-built 42 mode). Without an office the rent card with its requirements. With one the
 * head line (office name, accepted this week x/3) and three tabs:
 * <ul>
 * <li>Pelamar — rooted townsfolk as a list (a star for a child who chose the sword at zhuazhou, the root "MID?" until it is
 * tested, a bell for a rival recruiter) and, beside it or under it in a narrow box, the detail: portrait, parents and their
 * bond to the player, temperament, habits, the family's stance and blessing money, the rival; "Uji akar", "Terima", "Tolak"
 * stay in a row under the detail.</li>
 * <li>Ujian — the open test day: running now, next day, schedule, "Adakan" with the cost.</li>
 * <li>Kantor — keeper, banner, statistics, sect gossip, extend the rent.</li>
 * </ul>
 */
public final class LayarKantor extends LayarKotaDasar {

	private final DaftarGulir dPelamar = new DaftarGulir();
	private final GulirIsi gRinci = new GulirIsi();
	private final GulirIsi gUjian = new GulirIsi();
	private final GulirIsi gKantor = new GulirIsi();

	public LayarKantor(int npcId, CompoundTag lembar) {
		super(Kantor.LAYAR, npcId, lembar, SketsaKota.t("kantor.judul"), LembarKunci.GAYA_KERTAS);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(Kantor.TAB_PELAMAR, "kota.tab.pelamar", LembarKunci.Ikon.LAPAK_SEKTE),
				tab(Kantor.TAB_UJIAN, "kota.tab.ujian", LembarKunci.Ikon.MATA_BATIN), tab(Kantor.TAB_KANTOR, "kota.tab.kantor", LembarKunci.Ikon.KUIL));
	}

	@Override
	protected void tabBerubah() {
		dPelamar.mulaiUlang(Util.getMillis());
		gRinci.atas();
		gUjian.atas();
		gKantor.atas();
	}

	@Override
	public String helpTopic() {
		return KotaLembar.TOPIK_SEKTE;
	}

	@Override
	protected @Nullable GulirIsi gulirTab() {
		return switch (tab) {
			case Kantor.TAB_UJIAN -> gUjian;
			case Kantor.TAB_KANTOR -> gKantor;
			default -> lembar.sub(Kantor.KANTOR).kosongSemua() ? gKantor : gRinci;
		};
	}

	/** "MID" / "MID?" of a root I (0 mortal, 1-5 RootGrade + 1). */
	private static Component akar(int akar, boolean pasti) {
		MutableComponent t = SketsaKota.t("akar." + Math.max(0, Math.min(5, akar)));
		return pasti || akar <= 0 ? t : t.append("?");
	}

	private static int warnaAkar(int akar) {
		return switch (akar) {
			case 1 -> 0xFF8A8A8A;
			case 2 -> 0xFFC8C8C8;
			case 3 -> 0xFF4FA84F;
			case 4 -> 0xFF3E88D0;
			case 5 -> 0xFFE0A020;
			default -> 0xFF6A6A6A;
		};
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		int y = KotaGambar.kota(l, lembar, x0, y0, x1);
		Lembar k = lembar.sub(Kantor.KANTOR);
		if (k.kosongSemua()) {
			sewa(l, x0, y, x1, y1);
			return;
		}
		int diterima = k.i(Kantor.DITERIMA, 0);
		int maks = Math.max(1, k.i(Kantor.DITERIMA_MAKS, 3));
		Component kanan = SketsaKota.t("kantor.diterima", diterima, maks);
		int kw = l.font.width(kanan) + 6;
		l.teks(Brush.bold(k.teks(LembarKunci.NAMA)), x0, y, x1 - x0 - kw, l.gaya.teks);
		l.kanan(kanan, x1, y, diterima >= maks ? Palette.BAHAYA : l.gaya.teksLemah);
		y += 13;
		switch (tab) {
			case Kantor.TAB_UJIAN -> ujian(l, x0, y, x1, y1);
			case Kantor.TAB_KANTOR -> statistik(l, k, x0, y, x1, y1);
			default -> pelamar(l, diterima < maks, x0, y, x1, y1);
		}
	}

	// =================================================================== no office yet

	private void sewa(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar s = lembar.sub(Kantor.SEWA);
		int xr = x1 - 6;
		int y = gKantor.mulai(l, x0, y0, xr, y1);
		Component info = SketsaKota.t("kantor.sewa_info");
		int tw = xr - x0 - 40;
		int ih = Math.max(10, l.font.split(info, Math.max(10, tw)).size() * 9);
		List<Lembar> syarat = s.daftar(LembarKunci.SYARAT);
		int tinggi = 6 + 11 + ih + 6 + syarat.size() * 11 + 6;
		l.kartu(x0, y, xr, y + tinggi, 0.0F, false);
		Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.KUIL, x0 + 6, y + 6, 24, 0xFFFFFFFF);
		l.teks(Brush.bold(SketsaKota.t("kantor.sewa_judul")), x0 + 36, y + 5, tw, l.gaya.teks);
		l.bungkus(info, x0 + 36, y + 16, tw, l.gaya.teksLemah);
		int sy = y + 6 + 11 + ih + 6;
		for (Lembar r : syarat) {
			sy += KotaGambar.syarat(l, r, x0 + 6, sy, xr - 6, Integer.MAX_VALUE);
		}
		y += tinggi + 6;
		long harga = s.l(LembarKunci.HARGA, 0L);
		boolean cukup = uang() < 0 || harga <= uang();
		Component alasan = s.teksAtau(LembarKunci.ALASAN);
		if (alasan == null && !cukup) {
			alasan = k("harga.kurang", Brush.angka(harga - Math.max(0L, uang())));
		}
		tombolOp(l, "sewa", x0, y, xr - x0, TOMBOL_H + 2, SketsaKota.t("kantor.sewa", Brush.angka(harga)), s.b(LembarKunci.BISA) && cukup, alasan,
				Kantor.OP_SEWA);
		gKantor.selesai(l, y + TOMBOL_H + 8);
	}

	// =================================================================== Pelamar

	private void pelamar(Lukis l, boolean kuotaAda, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Kantor.PELAMAR);
		if (rows.isEmpty()) {
			kosong(l, SketsaKota.t("kantor.pelamar_kosong"), x0, y0, x1, y1);
			return;
		}
		Kotak[] b = bagi(x0, y0, x1, y1);
		Kotak kiri = b[0];
		dPelamar.gambar(l, "pelamar", kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1(), rows.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			int tx = rx0 + 5;
			if (e.b(Kantor.ISTIMEWA)) {
				Brush.text(lk.g, lk.font, Component.literal("✦"), tx, ry0 + 4, KotaGambar.EMAS, 1.0F, false);
				tx += 10;
			}
			int akar = e.i(KotaLembar.AKAR, 0);
			Component ak = akar(akar, e.b(KotaLembar.AKAR_PASTI));
			int aw = lk.font.width(ak) + (e.ada(Kantor.SAINGAN) ? 16 : 6);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, ry0 + 3, rx1 - tx - aw, lk.gaya.teks);
			lk.teks(SketsaKota.t("umur", e.i(KotaLembar.UMUR, 0)), tx, ry0 + 14, rx1 - tx - aw, lk.gaya.teksLemah);
			lk.kanan(ak, rx1 - 4, ry0 + 3, warnaAkar(akar));
			if (e.ada(Kantor.SAINGAN)) {
				Glyphs.Atlas.ikon(lk.g, LembarKunci.Ikon.LONCENG, rx1 - lk.font.width(ak) - 16, ry0 + 3, 9, 0xFFFFFFFF);
			}
		}, null, null);
		int sel = dPelamar.pilihan();
		Lembar e = sel >= 0 && sel < rows.size() ? rows.get(sel) : null;
		Kotak kanan = b[1];
		l.sumur(kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1());
		if (e == null) {
			return;
		}
		int bh = TOMBOL_H + 5;
		int bawah = kanan.y1() - bh;
		int xr = kanan.x1() - 6;
		int y = gRinci.mulai(l, kanan.x0() + 3, kanan.y0() + 3, xr, bawah - 1);
		int akhir = rinci(l, e, kanan.x0() + 4, y, xr - 2);
		gRinci.selesai(l, akhir);
		// the three actions stay under the detail
		int w = (kanan.w() - 14) / 3;
		String id = Integer.toString(e.i(KotaLembar.WARGA, -1));
		boolean pasti = e.b(KotaLembar.AKAR_PASTI);
		boolean cukupUji = uang() < 0 || uang() >= 2L;
		int by = bawah + 2;
		tombolOp(l, "uji", kanan.x0() + 4, by, w, TOMBOL_H, SketsaKota.t("kantor.uji"), !pasti && cukupUji,
				pasti ? SketsaKota.t("kantor.sudah_diuji") : k("harga.kurang", "2"), LembarKunci.op(Kantor.OP_UJI, id));
		long restu = e.l(Kantor.RESTU, 0L);
		boolean bisaTerima = kuotaAda && e.bisa() && (restu <= 0L || uang() < 0 || uang() >= restu);
		Component alasan = !kuotaAda ? SketsaKota.t("kantor.kuota_habis") : e.teksAtau(LembarKunci.ALASAN) != null ? e.teks(LembarKunci.ALASAN)
				: k("harga.kurang", Brush.angka(Math.max(0L, restu - Math.max(0L, uang()))));
		tombolOp(l, "terima", kanan.x0() + 7 + w, by, w, TOMBOL_H, SketsaKota.t("kantor.terima"), bisaTerima, alasan, LembarKunci.op(Kantor.OP_TERIMA, id));
		tombolOp(l, "tolak", kanan.x0() + 10 + 2 * w, by, w, TOMBOL_H, SketsaKota.t("kantor.tolak"), true, null, LembarKunci.op(Kantor.OP_TOLAK, id));
	}

	/** The detail of the selected applicant; returns the y under it. */
	private int rinci(Lukis l, Lembar e, int x0, int y0, int x1) {
		int ps = 44;
		Potret.gambar(l, x0, y0, ps, e.i(LembarKunci.POTRET, -1), -1, false, 0.0F);
		int tx = x0 + ps + 6;
		int tw = x1 - tx;
		int y = y0;
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, y, tw, l.gaya.teksSumur());
		y += 11;
		int akar = e.i(KotaLembar.AKAR, 0);
		l.teks(SketsaKota.t("kantor.akar_umur", akar(akar, e.b(KotaLembar.AKAR_PASTI)), e.i(KotaLembar.UMUR, 0)), tx, y, tw, warnaAkar(akar));
		y += 11;
		Component ayah = e.teksAtau(Kantor.AYAH);
		if (ayah != null) {
			String akrab = e.s(Kantor.AKRAB_KELUARGA);
			l.teks(SketsaKota.t("kantor.ayah", ayah, SketsaKota.t("akrab_pendek." + (akrab.isEmpty() ? KotaLembar.AKRAB_ASING : akrab))), tx, y, tw,
					l.gaya.teksSumur());
			y += 11;
		}
		Component ibu = e.teksAtau(Kantor.IBU);
		if (ibu != null) {
			l.teks(SketsaKota.t("kantor.ibu", ibu), tx, y, tw, l.gaya.teksSumur());
			y += 11;
		}
		y = Math.max(y, y0 + ps) + 4;
		Component zhua = e.teksAtau(Kantor.ZHUAZHOU);
		if (zhua != null) {
			y += Math.round(l.bungkus(SketsaKota.t("kantor.zhuazhou", zhua), x0, y, x1 - x0, e.b(Kantor.ISTIMEWA) ? KotaGambar.EMAS : l.gaya.teksSumur())) + 2;
		}
		if (e.ada(KotaLembar.WATAK)) {
			y += Math.round(l.bungkus(SketsaKota.t("kantor.watak", SketsaKota.t("watak." + e.s(KotaLembar.WATAK))), x0, y, x1 - x0, l.gaya.teksSumur())) + 2;
		}
		List<Lembar> kebiasaan = e.daftar(KotaLembar.KEBIASAAN);
		if (!kebiasaan.isEmpty()) {
			Component kb = Component.empty();
			for (int i = 0; i < kebiasaan.size(); i++) {
				kb = kb.copy().append(i == 0 ? Component.empty() : Component.literal(" · ")).append(kebiasaan.get(i).teks(LembarKunci.NAMA));
			}
			y += Math.round(l.bungkus(SketsaKota.t("kantor.kebiasaan", kb), x0, y, x1 - x0, Palette.INK_FAINT)) + 2;
		}
		// the family's stance and the blessing money
		String sikap = e.s(Kantor.SIKAP);
		long restu = e.l(Kantor.RESTU, 0L);
		int warnaSikap = Kantor.SK_BANGGA.equals(sikap) ? Palette.OK : Kantor.SK_MENOLAK.equals(sikap) ? Palette.BAHAYA : Palette.PERINGATAN;
		Component restuTeks = restu < 0L ? SketsaKota.t("kantor.restu_ayah") : restu == 0L ? SketsaKota.t("kantor.restu_nol")
				: SketsaKota.t("kantor.restu", Brush.angka(restu));
		if (!sikap.isEmpty()) {
			Component keluarga = SketsaKota.t("kantor.keluarga", SketsaKota.t("kantor.sikap." + sikap), restuTeks);
			int kh = Math.max(1, l.font.split(keluarga, Math.max(10, x1 - x0 - 5)).size()) * 9;
			l.g.fill(x0, y, x0 + 2, y + kh, warnaSikap);
			l.bungkus(keluarga, x0 + 5, y, x1 - x0 - 5, warnaSikap);
			y += kh + 3;
		}
		Component saingan = e.teksAtau(Kantor.SAINGAN);
		if (saingan != null) {
			y += Math.round(l.bungkus(SketsaKota.t("kantor.saingan", saingan, e.i(Kantor.SAINGAN_HARI, 0)), x0, y, x1 - x0, Palette.BAHAYA)) + 2;
		}
		return y;
	}

	// =================================================================== Ujian

	private void ujian(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar u = lembar.sub(Kantor.UJIAN);
		int bh = TOMBOL_H + 5;
		int bawah = y1 - bh;
		int xr = x1 - 6;
		int y = gUjian.mulai(l, x0, y0, xr, bawah - 2);
		Component info = SketsaKota.t("kantor.ujian_info");
		int tw = xr - x0 - 36;
		int ih = Math.max(10, l.font.split(info, Math.max(10, tw)).size() * 9);
		int tinggi = 6 + 11 + ih + 6;
		l.kartu(x0, y, xr, y + tinggi, 0.0F, false);
		Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.MATA_BATIN, x0 + 6, y + 6, 20, 0xFFFFFFFF);
		l.teks(Brush.bold(SketsaKota.t("kantor.ujian_judul")), x0 + 32, y + 5, tw, l.gaya.teks);
		l.bungkus(info, x0 + 32, y + 16, tw, l.gaya.teksLemah);
		y += tinggi + 4;
		if (u.b(Kantor.BERLANGSUNG)) {
			float p = 0.6F + 0.4F * l.denyut();
			l.g.fill(x0, y, xr, y + 14, Palette.argb(Math.round(0xB0 * p), 0x2E8963));
			l.teks(SketsaKota.t("kantor.ujian_berlangsung"), x0 + 5, y + 3, xr - x0 - 10, 0xFFF4FFF8);
			y += 18;
		}
		long berikut = u.l(Kantor.BERIKUT, -1L);
		if (berikut >= 0L) {
			l.teks(SketsaKota.t("kantor.ujian_berikut", berikut), x0 + 2, y, xr - x0 - 4, l.gaya.teksLemah);
			y += 12;
		}
		y += l.kepala(SketsaKota.t("kantor.jadwal"), x0, y, xr) + 1;
		List<Lembar> jadwal = u.daftar(Kantor.JADWAL);
		if (jadwal.isEmpty()) {
			l.samar(SketsaKota.t("kantor.jadwal_kosong"), x0 + 2, y, xr - x0 - 4);
			y += 12;
		}
		for (Lembar j : jadwal) {
			l.teks(SketsaKota.t("kantor.jadwal_baris", j.l(LembarKunci.HARI, 0L), j.teks(LembarKunci.TEKS)), x0 + 4, y, xr - x0 - 6, l.gaya.teks);
			y += 11;
		}
		gUjian.selesai(l, y + 2);
		long biaya = u.l(LembarKunci.BIAYA, 20L);
		boolean cukup = uang() < 0 || biaya <= uang();
		Component alasan = u.teksAtau(LembarKunci.ALASAN);
		if (alasan == null && !cukup) {
			alasan = k("harga.kurang", Brush.angka(biaya - Math.max(0L, uang())));
		}
		tombolOp(l, "adakan", x0, bawah + 2, x1 - x0, TOMBOL_H, SketsaKota.t("kantor.adakan", Brush.angka(biaya)), u.b(LembarKunci.BISA) && cukup,
				alasan, Kantor.OP_ADAKAN_UJIAN);
	}

	// =================================================================== Kantor

	private void statistik(Lukis l, Lembar k, int x0, int y0, int x1, int y1) {
		Lembar s = lembar.sub(Kantor.STATISTIK);
		int xr = x1 - 6;
		int y = gKantor.mulai(l, x0, y0, xr, y1);
		boolean dua = xr - x0 >= 300;
		int mid = x0 + (xr - x0) / 2;
		int kananX0 = dua ? mid + 6 : x0;
		int kiriX1 = dua ? mid - 6 : xr;
		int ya = y + l.kepala(SketsaKota.t("kantor.statistik"), x0, y, kiriX1) + 1;
		ya += KotaGambar.baris(l, SketsaKota.t("kantor.sampai"), Component.literal(Long.toString(k.l(KotaLembar.SAMPAI, 0L))), x0 + 2, ya, kiriX1,
				l.gaya.teks);
		Component penjaga = k.teksAtau(Kantor.PENJAGA);
		ya += KotaGambar.baris(l, SketsaKota.t("kantor.penjaga"), penjaga == null ? SketsaKota.t("kantor.penjaga_tiada") : penjaga, x0 + 2, ya, kiriX1,
				l.gaya.teks);
		ya += KotaGambar.baris(l, SketsaKota.t("kantor.panji"), SketsaKota.t(k.b(Kantor.PANJI) ? "ya" : "tidak"), x0 + 2, ya, kiriX1, l.gaya.teks);
		ya += 4;
		ya += KotaGambar.baris(l, SketsaKota.t("kantor.pelamar_minggu"), Component.literal(Integer.toString(s.i(Kantor.PELAMAR_MINGGU, 0))), x0 + 2, ya,
				kiriX1, l.gaya.teks);
		ya += KotaGambar.baris(l, SketsaKota.t("kantor.diterima_total"), Component.literal(Integer.toString(s.i(Kantor.DITERIMA_TOTAL, 0))), x0 + 2, ya,
				kiriX1, l.gaya.teks);
		ya += KotaGambar.baris(l, SketsaKota.t("kantor.ditolak"), Component.literal(Integer.toString(s.i(Kantor.DITOLAK, 0))), x0 + 2, ya, kiriX1,
				l.gaya.teks);
		ya += KotaGambar.baris(l, SketsaKota.t("kantor.renown"), Component.literal(Integer.toString(s.i(Kantor.RENOWN, 0))), x0 + 2, ya, kiriX1,
				l.gaya.teks);
		long sewa = k.l(Kantor.SEWA, 0L);
		boolean cukup = uang() < 0 || sewa <= uang();
		tombolOp(l, "perpanjang", x0, ya + 4, kiriX1 - x0, TOMBOL_H, SketsaKota.t("kantor.perpanjang", Brush.angka(sewa)), cukup,
				k("harga.kurang", Brush.angka(sewa - Math.max(0L, uang()))), Kantor.OP_PERPANJANG);
		ya += TOMBOL_H + 8;
		int yb = (dua ? y : ya + 4) + l.kepala(SketsaKota.t("kantor.gosip"), kananX0, dua ? y : ya + 4, xr) + 1;
		List<Lembar> gosip = s.daftar(Kantor.GOSIP);
		if (gosip.isEmpty()) {
			l.samar(SketsaKota.t("kantor.gosip_kosong"), kananX0 + 2, yb, xr - kananX0 - 4);
			yb += 12;
		}
		for (Lembar g : gosip) {
			yb += Math.round(l.bungkus(g.teks(LembarKunci.TEKS), kananX0 + 2, yb, xr - kananX0 - 4, l.gaya.teks)) + 4;
		}
		gKantor.selesai(l, Math.max(ya, yb) + 2);
	}
}
