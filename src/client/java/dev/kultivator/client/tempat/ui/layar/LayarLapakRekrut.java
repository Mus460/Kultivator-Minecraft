package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.dunia.kota42.ui.layar.SketsaKota;
import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Potret;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Kantor;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.LapakRekrut;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

/**
 * {@code lapak_rekrut} (DESIGN 38.U.3.9, 38.M.4.3, G77): the recruitment stall on Gate Opening Day. The stall rent first;
 * then the young candidates as cards with their live portrait, spirit root, a short talent line and the chance they accept
 * (a jade bar), "Undang" with the invitations left today. Locked as a whole below hall level 2 ({@code kunci_layar}).
 * Drawn with the single kit at text 1.0.
 * <p>
 * The one town-recruitment screen of the mod (jadwal 41/42 0.1 "No GUI twice"): the DESIGN 42.11.11 recruitment office
 * ({@code kantor_rekrut}, keys {@link KotaLembar.Kantor}) opens here as a second mode — the office line (rent paid to,
 * accepted this week x/3); without an office the rent card with its requirements; tabs Pelamar (rooted townsfolk: a list
 * with the ✦ of a child who chose the sword at zhuazhou, root shown "MID?" until tested; the detail with portrait,
 * parents and their bond to the player, watak, habits, the family's stance and blessing money, a rival NPC recruiter, and
 * "Uji akar" / "Terima" / "Tolak"), Ujian (the open test day: next day, cost, schedule) and Kantor (keeper, banner,
 * statistics, sect gossip, extend the rent). That mode is half-built 42-G2 work drawn by the old sheet engine with the 42
 * painters ({@link SketsaKota}) until 42U rebuilds it on the single kit (43.16.6, {@link #mesinLama}).
 */
public final class LayarLapakRekrut extends LayarInti {

	private final DaftarGulir calon = new DaftarGulir();
	/** The applicants list of the 42 office (old sheet engine). */
	private final dev.kultivator.client.tempat.ui.kit.DaftarGulir daftarPelamar = new dev.kultivator.client.tempat.ui.kit.DaftarGulir();

	public LayarLapakRekrut(int npcId, CompoundTag lembar) {
		this(LapakRekrut.LAYAR, npcId, lembar);
	}

	/** The recruitment screen for the 38 stall id or the 42 office id ({@code kantor_rekrut}). */
	public LayarLapakRekrut(String layar, int npcId, CompoundTag lembar) {
		super(layar, npcId, lembar, Kantor.LAYAR.equals(layar) ? SketsaKota.t("kantor.judul") : k("layar.lapak_rekrut"),
				Kantor.LAYAR.equals(layar) ? LembarKunci.GAYA_KERTAS : LembarKunci.GAYA_KAYU);
	}

	private boolean kantor() {
		return Kantor.LAYAR.equals(layar);
	}

	@Override
	protected boolean mesinLama() {
		return kantor();
	}

	@Override
	protected List<Tab> daftarTab() {
		if (kantor()) {
			return List.of(new Tab(Kantor.TAB_PELAMAR, SketsaKota.t("kantor.tab.pelamar"), LembarKunci.Ikon.LAPAK_SEKTE),
					new Tab(Kantor.TAB_UJIAN, SketsaKota.t("kantor.tab.ujian"), LembarKunci.Ikon.MATA_BATIN),
					new Tab(Kantor.TAB_KANTOR, SketsaKota.t("kantor.tab.kantor"), LembarKunci.Ikon.KUIL));
		}
		return List.of();
	}

	@Override
	public boolean lebar() {
		return false;
	}

	@Override
	protected void tabBerubah() {
		calon.mulaiUlang(Util.getMillis());
		daftarPelamar.mulaiUlang(Util.getMillis());
	}

	/** The 42 office (old sheet engine, {@link #mesinLama}). */
	@Override
	protected void isi(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
		kantor(l, x0, y0, x1, y1);
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		int y = y0;
		boolean sudahSewa = lembar.b(LapakRekrut.SUDAH_SEWA);
		int sisa = lembar.i(LapakRekrut.UNDANG_SISA, 0);
		if (!sudahSewa) {
			long sewa = lembar.l(LapakRekrut.SEWA, 0L);
			int bw = Math.min(130, (x1 - x0) / 2);
			l.kartu(x0, y, x1, y + 22, 0.0F, false);
			l.teks(k("rekrut.sewa_dulu"), x0 + 5, y + 7, x1 - x0 - bw - 14, l.gaya.teks);
			boolean cukup = uang() < 0 || sewa <= uang();
			tombolOp(l, "sewa", x1 - bw - 4, y + 4, bw, TOMBOL_H, k("rekrut.sewa", Brush.angka(sewa)), cukup, k("harga.kurang", Brush.angka(sewa - Math.max(0L, uang()))),
					LapakRekrut.OP_SEWA);
			y += 26;
		} else {
			l.teks(k("rekrut.undang_sisa", sisa), x0, y + 1, x1 - x0, sisa > 0 ? l.gaya.teksLemah : l.gaya.turun());
			y += 12;
		}
		List<Lembar> rows = lembar.daftar(LapakRekrut.CALON);
		if (rows.isEmpty()) {
			kosong(l, k("rekrut.kosong"), x0, y, x1, y1);
			return;
		}
		calon.gambar(l, "calon", x0, y, x1, y1, rows.size(), 42, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			Potret.gambar(lk, rx0 + 2, ry0 + 3, 36, e.i(LembarKunci.POTRET, -1), LembarKunci.Ikon.LAPAK_SEKTE, false, sorot ? 0.6F : 0.0F);
			int tx = rx0 + 42;
			int bw = 62;
			int bx = rx1 - bw - 4;
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, ry0 + 2, bx - tx - 4, lk.gaya.teks);
			lk.teks(k("rekrut.akar", e.teks(LapakRekrut.AKAR)), tx, ry0 + 12, bx - tx - 4, lk.gaya.teksLemah);
			lk.teks(e.teks(LapakRekrut.BAKAT), tx, ry0 + 22, rx1 - tx - 4, lk.gaya.teksSamar);
			int peluang = Math.max(0, Math.min(100, e.i(LembarKunci.PELUANG, 0)));
			Component pt = k("rekrut.peluang", peluang);
			int pw = lk.font.width(pt);
			Bilah.gambar(lk, tx, ry0 + 35, rx1 - pw - 10, peluang / 100.0F, Palette.JADE_LIGHT, new float[0]);
			lk.kanan(pt, rx1 - 4, ry0 + 32, lk.gaya.teksLemah);
			Component alasan = e.teksAtau(LembarKunci.ALASAN);
			boolean bisa = e.bisa() && sudahSewa && sisa > 0;
			if (alasan == null) {
				alasan = !sudahSewa ? k("rekrut.sewa_dulu") : k("rekrut.undangan_habis");
			}
			tombolOp(lk, "undang" + i, bx, ry0 + 4, bw, TOMBOL_H, k("rekrut.undang"), bisa, alasan, LembarKunci.op(LapakRekrut.OP_UNDANG, e.id()));
		}, null, null);
	}

	// =================================================================== 42 kantor rekrutmen

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

	private void kantor(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
		int y = SketsaKota.kota(l, lembar, x0, y0, x1, gaya);
		Lembar k = lembar.sub(Kantor.KANTOR);
		if (k.kosongSemua()) {
			sewa(l, x0, y, x1, y1);
			return;
		}
		int diterima = k.i(Kantor.DITERIMA, 0);
		int maks = Math.max(1, k.i(Kantor.DITERIMA_MAKS, 3));
		Component kanan = SketsaKota.t("kantor.diterima", diterima, maks);
		int kw = Math.round(font.width(kanan) * 0.75F) + 4;
		l.teks(Brush.bold(k.teks(LembarKunci.NAMA)), x0, y, x1 - x0 - kw, gaya.teks, 0.85F);
		l.kanan(kanan, x1, y + 1, diterima >= maks ? TempatWarna.BAHAYA : gaya.teksLemah, 0.75F);
		y += 11;
		switch (tab) {
			case Kantor.TAB_UJIAN -> ujian(l, x0, y, x1, y1);
			case Kantor.TAB_KANTOR -> statistik(l, k, x0, y, x1, y1);
			default -> pelamar(l, diterima < maks, x0, y, x1, y1);
		}
	}

	private void sewa(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
		Lembar s = lembar.sub(Kantor.SEWA);
		PanelTempat.kartu(l.g, gaya, x0, y0, x1, y1, 0.0F, false);
		int y = y0 + 5;
		IkonTempat.gambar(l.g, LembarKunci.Ikon.KUIL, x0 + 5, y, 24, 0xFFFFFFFF);
		l.teks(Brush.bold(SketsaKota.t("kantor.sewa_judul")), x0 + 34, y + 2, x1 - x0 - 40, gaya.teks, 1.0F);
		l.keterangan(SketsaKota.t("kantor.sewa_info"), x0 + 34, y + 14, x1 - x0 - 40, gaya.teksLemah);
		y += 32;
		for (Lembar r : s.daftar(LembarKunci.SYARAT)) {
			int h = SketsaKota.syarat(l, r, x0 + 6, y, x1 - 6, y1 - 22);
			if (h == 0) {
				break;
			}
			y += h;
		}
		long harga = s.l(LembarKunci.HARGA, 0L);
		boolean cukup = uang() < 0 || harga <= uang();
		Component alasan = s.teksAtau(LembarKunci.ALASAN);
		if (alasan == null && !cukup) {
			alasan = TeksTempat.k("harga.kurang", Brush.angka(harga - Math.max(0L, uang())));
		}
		tombolOp(l, "sewa", x0 + 6, y1 - 19, x1 - x0 - 12, 15, SketsaKota.t("kantor.sewa", Brush.angka(harga)), s.b(LembarKunci.BISA) && cukup, alasan,
				Kantor.OP_SEWA);
	}

	private void pelamar(dev.kultivator.client.tempat.ui.kit.Lukis l, boolean kuotaAda, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Kantor.PELAMAR);
		if (rows.isEmpty()) {
			kosong(l, SketsaKota.t("kantor.pelamar_kosong"), x0, y0, x1, y1);
			return;
		}
		Kotak[] b = bagi(x0, y0, x1, y1);
		Kotak kiri = b[0];
		daftarPelamar.gambar(l, "pelamar", kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1(), rows.size(), 22, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			int tx = rx0 + 4;
			if (e.b(Kantor.ISTIMEWA)) {
				Brush.text(lk.g, font, Component.literal("✦"), tx, ry0 + 3, TempatWarna.EMAS, 1.0F, false);
			}
			tx += 9;
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, ry0 + 3, rx1 - tx - 40, gaya.teks, 0.85F);
			lk.keterangan(SketsaKota.t("umur", e.i(KotaLembar.UMUR, 0)), tx, ry0 + 13, 40, gaya.teksLemah);
			int akar = e.i(KotaLembar.AKAR, 0);
			lk.kanan(akar(akar, e.b(KotaLembar.AKAR_PASTI)), rx1 - 4, ry0 + 7, warnaAkar(akar), 0.85F);
			if (e.ada(Kantor.SAINGAN)) {
				IkonTempat.gambar(lk.g, LembarKunci.Ikon.LONCENG, rx1 - 46, ry0 + 6, 8, 0xFFFFFFFF);
			}
		}, null, null);
		int sel = daftarPelamar.pilihan();
		Lembar e = sel >= 0 && sel < rows.size() ? rows.get(sel) : null;
		Kotak kanan = b[1];
		PanelTempat.kartu(l.g, gaya, kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1(), 0.0F, false);
		if (e == null) {
			return;
		}
		int ps = Math.min(44, kanan.h() / 3);
		dev.kultivator.client.tempat.ui.kit.Potret.gambar(l, kanan.x0() + 4, kanan.y0() + 4, ps, e.i(LembarKunci.POTRET, -1), LembarKunci.Ikon.TIDAK_ADA, false, 0.0F);
		int tx = kanan.x0() + ps + 8;
		int tw = kanan.x1() - tx - 4;
		int y = kanan.y0() + 4;
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, y, tw, gaya.teks, 0.9F);
		y += 10;
		int akar = e.i(KotaLembar.AKAR, 0);
		l.keterangan(SketsaKota.t("kantor.akar_umur", akar(akar, e.b(KotaLembar.AKAR_PASTI)), e.i(KotaLembar.UMUR, 0)), tx, y, tw, warnaAkar(akar));
		y += 9;
		Component ayah = e.teksAtau(Kantor.AYAH);
		if (ayah != null) {
			String akrab = e.s(Kantor.AKRAB_KELUARGA);
			l.keterangan(SketsaKota.t("kantor.ayah", ayah, SketsaKota.t("akrab_pendek." + (akrab.isEmpty() ? KotaLembar.AKRAB_ASING : akrab))), tx, y, tw,
					gaya.teksLemah);
			y += 9;
		}
		Component ibu = e.teksAtau(Kantor.IBU);
		if (ibu != null) {
			l.keterangan(SketsaKota.t("kantor.ibu", ibu), tx, y, tw, gaya.teksLemah);
			y += 9;
		}
		y = Math.max(y, kanan.y0() + ps + 6);
		int lx = kanan.x0() + 5;
		int lw = kanan.w() - 10;
		Component zhua = e.teksAtau(Kantor.ZHUAZHOU);
		if (zhua != null) {
			l.keterangan(SketsaKota.t("kantor.zhuazhou", zhua), lx, y, lw, e.b(Kantor.ISTIMEWA) ? TempatWarna.EMAS : gaya.teksLemah);
			y += 9;
		}
		if (e.ada(KotaLembar.WATAK)) {
			l.keterangan(SketsaKota.t("kantor.watak", SketsaKota.t("watak." + e.s(KotaLembar.WATAK))), lx, y, lw, gaya.teksLemah);
			y += 9;
		}
		List<Lembar> kebiasaan = e.daftar(KotaLembar.KEBIASAAN);
		if (!kebiasaan.isEmpty()) {
			Component kb = Component.empty();
			for (int i = 0; i < kebiasaan.size(); i++) {
				kb = kb.copy().append(i == 0 ? Component.empty() : Component.literal(" · ")).append(kebiasaan.get(i).teks(LembarKunci.NAMA));
			}
			l.keterangan(SketsaKota.t("kantor.kebiasaan", kb), lx, y, lw, gaya.teksSamar);
			y += 9;
		}
		// the family's stance and the blessing money
		String sikap = e.s(Kantor.SIKAP);
		long restu = e.l(Kantor.RESTU, 0L);
		int warnaSikap = Kantor.SK_BANGGA.equals(sikap) ? TempatWarna.OK : Kantor.SK_MENOLAK.equals(sikap) ? TempatWarna.BAHAYA : TempatWarna.PERINGATAN;
		Component restuTeks = restu < 0L ? SketsaKota.t("kantor.restu_ayah") : restu == 0L ? SketsaKota.t("kantor.restu_nol")
				: SketsaKota.t("kantor.restu", Brush.angka(restu));
		if (!sikap.isEmpty()) {
			l.g.fill(lx, y, lx + 2, y + 9, warnaSikap);
			l.keterangan(SketsaKota.t("kantor.keluarga", SketsaKota.t("kantor.sikap." + sikap), restuTeks), lx + 5, y, lw - 5, warnaSikap);
			y += 10;
		}
		Component saingan = e.teksAtau(Kantor.SAINGAN);
		if (saingan != null) {
			l.keterangan(SketsaKota.t("kantor.saingan", saingan, e.i(Kantor.SAINGAN_HARI, 0)), lx, y, lw, TempatWarna.BAHAYA);
		}
		int by = kanan.y1() - 17;
		int w = (kanan.w() - 14) / 3;
		String id = Integer.toString(e.i(KotaLembar.WARGA, -1));
		boolean pasti = e.b(KotaLembar.AKAR_PASTI);
		boolean cukupUji = uang() < 0 || uang() >= 2L;
		tombolOp(l, "uji", kanan.x0() + 4, by, w, 14, SketsaKota.t("kantor.uji"), !pasti && cukupUji,
				pasti ? SketsaKota.t("kantor.sudah_diuji") : TeksTempat.k("harga.kurang", "2"), LembarKunci.op(Kantor.OP_UJI, id));
		boolean bisaTerima = kuotaAda && e.bisa() && (restu <= 0L || uang() < 0 || uang() >= restu);
		Component alasan = !kuotaAda ? SketsaKota.t("kantor.kuota_habis") : e.teksAtau(LembarKunci.ALASAN) != null ? e.teks(LembarKunci.ALASAN)
				: TeksTempat.k("harga.kurang", Brush.angka(Math.max(0L, restu - Math.max(0L, uang()))));
		tombolOp(l, "terima", kanan.x0() + 7 + w, by, w, 14, SketsaKota.t("kantor.terima"), bisaTerima, alasan, LembarKunci.op(Kantor.OP_TERIMA, id));
		tombolOp(l, "tolak", kanan.x0() + 10 + 2 * w, by, w, 14, SketsaKota.t("kantor.tolak"), true, null, LembarKunci.op(Kantor.OP_TOLAK, id));
	}

	private void ujian(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
		Lembar u = lembar.sub(Kantor.UJIAN);
		PanelTempat.kartu(l.g, gaya, x0, y0, x1, y0 + 46, 0.0F, false);
		IkonTempat.gambar(l.g, LembarKunci.Ikon.MATA_BATIN, x0 + 5, y0 + 5, 20, 0xFFFFFFFF);
		l.teks(Brush.bold(SketsaKota.t("kantor.ujian_judul")), x0 + 30, y0 + 4, x1 - x0 - 34, gaya.teks, 0.9F);
		l.bungkus(SketsaKota.t("kantor.ujian_info"), x0 + 30, y0 + 15, x1 - x0 - 34, gaya.teksLemah, 0.7F, 28);
		int y = y0 + 50;
		if (u.b(Kantor.BERLANGSUNG)) {
			float p = 0.6F + 0.4F * l.denyut();
			l.g.fill(x0, y, x1, y + 13, Palette.argb(Math.round(0xB0 * p), 0x2E8963));
			l.teks(SketsaKota.t("kantor.ujian_berlangsung"), x0 + 5, y + 3, x1 - x0 - 10, 0xFFF4FFF8, 0.85F);
			y += 16;
		}
		long berikut = u.l(Kantor.BERIKUT, -1L);
		if (berikut >= 0L) {
			l.keterangan(SketsaKota.t("kantor.ujian_berikut", berikut), x0, y, x1 - x0, gaya.teksLemah);
			y += 11;
		}
		y += subjudul(l, SketsaKota.t("kantor.jadwal"), x0, y, x1);
		List<Lembar> jadwal = u.daftar(Kantor.JADWAL);
		if (jadwal.isEmpty()) {
			l.keterangan(SketsaKota.t("kantor.jadwal_kosong"), x0, y, x1 - x0, gaya.teksSamar);
		}
		for (Lembar j : jadwal) {
			if (y + 10 > y1 - 20) {
				break;
			}
			l.keterangan(SketsaKota.t("kantor.jadwal_baris", j.l(LembarKunci.HARI, 0L), j.teks(LembarKunci.TEKS)), x0 + 4, y, x1 - x0 - 4, gaya.teks);
			y += 10;
		}
		long biaya = u.l(LembarKunci.BIAYA, 20L);
		boolean cukup = uang() < 0 || biaya <= uang();
		Component alasan = u.teksAtau(LembarKunci.ALASAN);
		if (alasan == null && !cukup) {
			alasan = TeksTempat.k("harga.kurang", Brush.angka(biaya - Math.max(0L, uang())));
		}
		tombolOp(l, "adakan", x0, y1 - 16, x1 - x0, 15, SketsaKota.t("kantor.adakan", Brush.angka(biaya)), u.b(LembarKunci.BISA) && cukup, alasan,
				Kantor.OP_ADAKAN_UJIAN);
	}

	private void statistik(dev.kultivator.client.tempat.ui.kit.Lukis l, Lembar k, int x0, int y0, int x1, int y1) {
		Lembar s = lembar.sub(Kantor.STATISTIK);
		Kotak[] b = bagi(x0, y0, x1, y1);
		Kotak kiri = b[0];
		PanelTempat.kartu(l.g, gaya, kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1(), 0.0F, false);
		int y = kiri.y0() + 4;
		int lx0 = kiri.x0() + 5;
		int lx1 = kiri.x1() - 5;
		y += barisNilai(l, SketsaKota.t("kantor.sampai"), Component.literal(Long.toString(k.l(KotaLembar.SAMPAI, 0L))), lx0, y, lx1);
		Component penjaga = k.teksAtau(Kantor.PENJAGA);
		y += barisNilai(l, SketsaKota.t("kantor.penjaga"), penjaga == null ? SketsaKota.t("kantor.penjaga_tiada") : penjaga, lx0, y, lx1);
		y += barisNilai(l, SketsaKota.t("kantor.panji"), SketsaKota.t(k.b(Kantor.PANJI) ? "ya" : "tidak"), lx0, y, lx1);
		y += 4;
		y += barisNilai(l, SketsaKota.t("kantor.pelamar_minggu"), Component.literal(Integer.toString(s.i(Kantor.PELAMAR_MINGGU, 0))), lx0, y, lx1);
		y += barisNilai(l, SketsaKota.t("kantor.diterima_total"), Component.literal(Integer.toString(s.i(Kantor.DITERIMA_TOTAL, 0))), lx0, y, lx1);
		y += barisNilai(l, SketsaKota.t("kantor.ditolak"), Component.literal(Integer.toString(s.i(Kantor.DITOLAK, 0))), lx0, y, lx1);
		barisNilai(l, SketsaKota.t("kantor.renown"), Component.literal(Integer.toString(s.i(Kantor.RENOWN, 0))), lx0, y, lx1);
		long sewa = k.l(Kantor.SEWA, 0L);
		boolean cukup = uang() < 0 || sewa <= uang();
		tombolOp(l, "perpanjang", kiri.x0() + 4, kiri.y1() - 18, kiri.w() - 8, 14, SketsaKota.t("kantor.perpanjang", Brush.angka(sewa)), cukup,
				TeksTempat.k("harga.kurang", Brush.angka(sewa - Math.max(0L, uang()))), Kantor.OP_PERPANJANG);
		Kotak kanan = b[1];
		int ry = kanan.y0() + subjudul(l, SketsaKota.t("kantor.gosip"), kanan.x0(), kanan.y0(), kanan.x1());
		List<Lembar> gosip = s.daftar(Kantor.GOSIP);
		if (gosip.isEmpty()) {
			l.keterangan(SketsaKota.t("kantor.gosip_kosong"), kanan.x0(), ry, kanan.w(), gaya.teksSamar);
		}
		for (Lembar g : gosip) {
			if (ry + 10 > kanan.y1()) {
				break;
			}
			ry += Math.round(l.bungkus(g.teks(LembarKunci.TEKS), kanan.x0(), ry, kanan.w(), gaya.teks, 0.75F, kanan.y1() - ry)) + 3;
		}
	}
}
