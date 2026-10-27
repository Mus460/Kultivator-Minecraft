package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Saring;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Buku;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Milikku;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kota_buku} (DESIGN 42.11.4) — Buku Kota, home Dunia › Kota, and {@code kota_milikku} (hub "Rumah &amp; Usahaku"),
 * one class with two layouts because both are read-only ledgers of what a place holds. Buku Kota tabs on the single kit:
 * <ul>
 * <li>Ringkas — the town strip, population against beds with the week's births, deaths and moves, the mayor and the player's
 * office, a gold "upeti siap" note, the "Kota Penuh" seal, the next three events with "Tunjuk", the Ghost Market hint;</li>
 * <li>Warga — search + age/root/acquaintance filters answered by the server (a pause after typing), pages of 48, rows with
 * portrait, mood and bond heart, click = the person's card; Kavling — the town's lots with five stage seals, click = the lot;
 * Kabar — the gossip the player heard here, who told it and how hot it still is;</li>
 * <li>Acara, Harga and Milikku are jumps: the server opens the calendar (38 {@code acara}), the trade ledger (price tab of
 * {@code gudang}) or "Rumah &amp; Usahaku".</li>
 * </ul>
 * {@code kota_milikku}: tab Daftar = one row per asset (house, shop, stall, stake, warehouse, contract, office) with its icon,
 * a summary line and a warning in red, click = that asset's screen; tab Minggu = the week's income against the faucet caps.
 */
public final class LayarBukuKota extends LayarKotaDasar {

	private static final long JEDA_SARING_MS = 450L;

	private final boolean milikku;
	private final DaftarGulir dWarga = new DaftarGulir();
	private final DaftarGulir dKavling = new DaftarGulir();
	private final DaftarGulir dKabar = new DaftarGulir();
	private final DaftarGulir dMilikku = new DaftarGulir();
	private final GulirIsi gulirRingkas = new GulirIsi();
	private final GulirIsi gulirMinggu = new GulirIsi();
	private final Saring saring = new Saring(this::saringBerubah);
	private long kirimSaringPada = -1L;
	private boolean saringSiap;

	public LayarBukuKota(String layar, int npcId, CompoundTag lembar) {
		super(layar, npcId, lembar, KotaGambar.t(Milikku.LAYAR.equals(layar) ? "milikku.judul" : "buku.judul"),
				Milikku.LAYAR.equals(layar) ? LembarKunci.GAYA_KAYU : LembarKunci.GAYA_KERTAS);
		this.milikku = Milikku.LAYAR.equals(layar);
		List<Saring.Pilihan> jenis = new ArrayList<>();
		for (String id : KotaLembar.SEMUA_SARING) {
			if (!KotaLembar.SR_SEMUA.equals(id)) {
				jenis.add(new Saring.Pilihan(id, KotaGambar.t("saring." + id)));
			}
		}
		saring.jenis(jenis);
	}

	@Override
	protected List<Tab> daftarTab() {
		if (milikku) {
			return List.of(tab("daftar", "kota.tab.daftar", Glyphs.Atlas.KUNCI), tab("minggu", "kota.tab.minggu", Glyphs.Atlas.PIALA));
		}
		return List.of(tab(Buku.TAB_RINGKAS, "kota.tab.ringkas", Glyphs.Atlas.GULUNGAN), tab(Buku.TAB_WARGA, "kota.tab.warga", Glyphs.Atlas.HATI),
				tab(Buku.TAB_ACARA, "kota.tab.acara", Glyphs.Atlas.PIALA), tab(Buku.TAB_HARGA, "kota.tab.harga", dev.kultivator.dunia.tempat.ui.LembarKunci.Ikon.NB),
				tab(Buku.TAB_KAVLING, "kota.tab.kavling", Glyphs.Atlas.TONGGAK), tab(Buku.TAB_KABAR, "kota.tab.kabar", Glyphs.Atlas.SURAT),
				tab(Buku.TAB_MILIKKU, "kota.tab.milikku", Glyphs.Atlas.KUNCI));
	}

	@Override
	protected Set<String> lompat() {
		return milikku ? Set.of() : Set.of(Buku.TAB_ACARA, Buku.TAB_HARGA, Buku.TAB_MILIKKU);
	}

	@Override
	protected void tabBerubah() {
		long kini = Util.getMillis();
		dWarga.mulaiUlang(kini);
		dKavling.mulaiUlang(kini);
		dKabar.mulaiUlang(kini);
		dMilikku.mulaiUlang(kini);
		gulirRingkas.atas();
		gulirMinggu.atas();
	}

	@Override
	protected void setelahLembar(boolean buka) {
		if (!milikku && !saringSiap) {
			saringSiap = true;
			saring.setJenis(lembar.s(Buku.SARING).equals(KotaLembar.SR_SEMUA) ? Saring.SEMUA : lembar.s(Buku.SARING));
			saring.kolom().setText(lembar.teks(Buku.CARI).getString());
			kirimSaringPada = -1L;
		}
	}

	@Override
	public String helpTopic() {
		return milikku ? KotaLembar.TOPIK_EKONOMI : KotaLembar.TOPIK_KEHIDUPAN;
	}

	private void saringBerubah() {
		kirimSaringPada = Util.getMillis() + JEDA_SARING_MS;
	}

	private void kirimHalaman(int halaman) {
		String jenis = Saring.SEMUA.equals(saring.jenisAktif()) ? KotaLembar.SR_SEMUA : saring.jenisAktif();
		kirim(LembarKunci.op(LembarKunci.OP_HALAMAN, halaman, jenis, saring.teks()));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (Buku.TAB_RINGKAS.equals(tab) && !milikku && gulirRingkas.roda(mouseX, mouseY, scrollY)) {
			return true;
		}
		if (milikku && "minggu".equals(tab) && gulirMinggu.roda(mouseX, mouseY, scrollY)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	protected boolean tombolLain(KeyEvent event) {
		if (Buku.TAB_RINGKAS.equals(tab) && !milikku) {
			return gulirRingkas.tombol(event);
		}
		return milikku && "minggu".equals(tab) && gulirMinggu.tombol(event);
	}

	// =================================================================== drawing

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		if (milikku) {
			if ("minggu".equals(tab)) {
				minggu(l, x0, y0, x1, y1);
			} else {
				daftarMilikku(l, x0, y0, x1, y1);
			}
			return;
		}
		switch (tab) {
			case Buku.TAB_WARGA -> warga(l, x0, y0, x1, y1);
			case Buku.TAB_KAVLING -> kavling(l, x0, y0, x1, y1);
			case Buku.TAB_KABAR -> kabar(l, x0, y0, x1, y1);
			default -> ringkas(l, x0, y0, x1, y1);
		}
	}

	private void ringkas(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar r = lembar.sub(Buku.RINGKAS);
		if (r.kosongSemua()) {
			kosong(l, KotaGambar.t("buku.kosong"), x0, y0, x1, y1);
			return;
		}
		int xr = x1 - 6;
		int y = gulirRingkas.mulai(l, x0, y0, xr, y1);
		y = KotaGambar.kota(l, lembar, x0, y, xr);
		if (lembar.ada(KotaLembar.KAS_KOTA)) {
			y += KotaGambar.baris(l, KotaGambar.t("buku.kas"), KotaGambar.nb(lembar.l(KotaLembar.KAS_KOTA, 0L)), x0, y, xr, l.gaya.teks);
		}
		l.garis(x0, xr, y + 2);
		y += 7;
		int penduduk = r.i(Buku.PENDUDUK, 0);
		int kasur = Math.max(1, r.i(Buku.KASUR, 1));
		y += KotaGambar.baris(l, KotaGambar.t("buku.penduduk"), KotaGambar.t("buku.penduduk_n", penduduk, kasur), x0, y, xr, l.gaya.teks);
		Bilah.gambar(l, x0, y, xr, penduduk / (float) kasur, penduduk >= kasur ? 0xFFD0A040 : 0xFF5FA88C, new float[] {1.0F});
		y += 8;
		y += KotaGambar.baris(l, KotaGambar.t("buku.minggu"), KotaGambar.t("buku.minggu_n", r.i(Buku.LAHIR, 0), r.i(Buku.WAFAT, 0), r.i(Buku.MASUK, 0),
				r.i(Buku.KELUAR, 0)), x0, y, xr, l.gaya.teks);
		Component wali = r.teksAtau(Buku.WALI);
		if (wali != null) {
			y += KotaGambar.baris(l, KotaGambar.t("buku.wali"), wali, x0, y, xr, l.gaya.teks);
		}
		Component jabatan = r.teksAtau(Buku.JABATAN);
		if (jabatan != null) {
			y += KotaGambar.baris(l, KotaGambar.t("buku.jabatanmu"), jabatan, x0, y, xr, KotaGambar.EMAS);
		}
		y += KotaGambar.baris(l, KotaGambar.t("buku.kenal"), KotaGambar.t("buku.kenal_n", r.i(Buku.KENAL_WARGA, 0)), x0, y, xr, l.gaya.teks);
		if (r.b(Buku.UPETI_SIAP)) {
			IkonKota.gambar(l.g, IkonKota.KAS, x0, y - 2, 12);
			l.teks(Brush.bold(KotaGambar.t("buku.upeti_siap")), x0 + 15, y, xr - x0 - 15, KotaGambar.EMAS);
			y += 13;
		}
		if (r.b(Buku.PENUH)) {
			IkonKota.gambar(l.g, IkonKota.KAVLING, x0, y - 2, 12);
			l.teks(Brush.bold(KotaGambar.t("buku.penuh")), x0 + 15, y, xr - x0 - 15, 0xFF2E8963);
			y += 13;
		}
		y += l.kepala(KotaGambar.t("buku.acara_berikut"), x0, y + 2, xr) + 2;
		List<Lembar> acara = r.daftar(Buku.ACARA);
		if (acara.isEmpty()) {
			l.teks(KotaGambar.t("buku.acara_kosong"), x0, y, xr - x0, l.gaya.teksSamar);
			y += 13;
		}
		for (int i = 0; i < acara.size(); i++) {
			Lembar a = acara.get(i);
			y = barisAcara(l, a, i, x0, y, xr);
		}
		Component malam = r.teksAtau(Buku.MALAM);
		if (malam != null) {
			y += 4;
			IkonKota.gambar(l.g, IkonKota.BULAN_HANTU, x0, y - 2, 12);
			l.teks(malam, x0 + 15, y, xr - x0 - 15, 0xFF3FA86A);
			y += 13;
		}
		gulirRingkas.selesai(l, y + 2);
	}

	/** One event line with its icon, "hari N jam HH:MM · tempat" and a Tunjuk button; returns the y under it. */
	private int barisAcara(Lukis l, Lembar a, int i, int x0, int y, int xr) {
		IkonKota.gambar(l.g, IkonKota.acara(a.s(LembarKunci.JENIS)), x0, y, 14);
		int bw = 52;
		l.teks(a.teks(LembarKunci.TEKS).getString().isEmpty() ? a.teks(LembarKunci.NAMA) : a.teks(LembarKunci.TEKS), x0 + 18, y, xr - x0 - 18 - bw - 4,
				l.gaya.teks);
		Component waktu = KotaGambar.t("buku.acara_waktu", a.l(LembarKunci.HARI, 0L), String.format("%02d:%02d", a.i(KotaLembar.JAM, 0), a.i(KotaLembar.MENIT, 0)));
		Component tempat = a.teksAtau(LembarKunci.TEMPAT);
		l.teks(tempat == null ? waktu : KotaGambar.t("buku.acara_di", waktu, tempat), x0 + 18, y + 10, xr - x0 - 18 - bw - 4, l.gaya.teksLemah);
		tunjuk(l, "acara#" + i, xr - bw, y + 1, bw, 14, a.i(LembarKunci.X, 0), a.i(LembarKunci.Z, 0));
		return y + 25;
	}

	// ------------------------------------------------------------------ warga

	private void warga(Lukis l, int x0, int y0, int x1, int y1) {
		if (kirimSaringPada > 0 && Util.getMillis() >= kirimSaringPada) {
			kirimSaringPada = -1L;
			kirimHalaman(0);
		}
		int y = y0 + saring.gambar(l, "saring", x0, y0, x1) + 3;
		int halaman = lembar.i(Buku.HALAMAN, 0);
		int maks = lembar.i(Buku.HALAMAN_MAKS, 0);
		int bawah = y1;
		if (maks > 0) {
			bawah = y1 - 17;
			int cx = (x0 + x1) / 2;
			aksi(l, "hal#kiri", cx - 70, bawah + 3, 20, 14, Component.literal("‹"), halaman > 0, null, () -> kirimHalaman(halaman - 1));
			l.tengah(KotaGambar.t("buku.halaman", halaman + 1, maks + 1, lembar.i(LembarKunci.JUMLAH, 0)), cx, bawah + 6, 100, l.gaya.teksLemah);
			aksi(l, "hal#kanan", cx + 50, bawah + 3, 20, 14, Component.literal("›"), halaman < maks, null, () -> kirimHalaman(halaman + 1));
		}
		List<Lembar> rows = lembar.daftar(LembarKunci.DAFTAR);
		if (rows.isEmpty()) {
			kosong(l, KotaGambar.t("buku.warga_kosong"), x0, y, x1, bawah);
			return;
		}
		dWarga.gambar(l, "warga", x0, y, x1, bawah, rows.size(), 38, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			KotaGambar.warga(lk, e, rx0, ry0, rx1, ry1, sorot, pilih, e.b(KotaLembar.KENAL) ? 0 : 0);
			if (!e.b(KotaLembar.KENAL) && !KotaLembar.ST_ARSIP.equals(e.s(KotaLembar.STATUS))) {
				return;
			}
			if (KotaLembar.ST_ARSIP.equals(e.s(KotaLembar.STATUS))) {
				IkonKota.gambar(lk.g, IkonKota.MAKAM, rx1 - 38, ry0 + 4, 14);
			}
		}, () -> {
			int i = dWarga.pilihan();
			if (i >= 0 && i < rows.size() && rows.get(i).i(KotaLembar.WARGA, 0) > 0 && !KotaLembar.ST_ARSIP.equals(rows.get(i).s(KotaLembar.STATUS))) {
				pilihBaris(Buku.TAB_WARGA, Integer.toString(rows.get(i).i(KotaLembar.WARGA, 0)));
			}
		}, null);
	}

	// ------------------------------------------------------------------ kavling, kabar

	private void kavling(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Buku.KAVLING);
		if (rows.isEmpty()) {
			kosong(l, KotaGambar.t("buku.kavling_kosong"), x0, y0, x1, y1);
			return;
		}
		dKavling.gambar(l, "kavling", x0, y0, x1, y1, rows.size(), 34, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			IkonKota.gambar(lk.g, IkonKota.KAVLING, rx0 + 4, ry0 + 8, 16);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 26, ry0 + 3, rx1 - rx0 - 120, lk.gaya.teks);
			Component rencana = e.teksAtau(Buku.RENCANA);
			if (rencana != null) {
				lk.teks(rencana, rx0 + 26, ry0 + 14, rx1 - rx0 - 120, lk.gaya.teksLemah);
			}
			int total = Math.max(1, e.i(LembarKunci.TAHAP_TOTAL, 5));
			int tahap = e.i(LembarKunci.TAHAP, 0);
			String keadaan = e.s(LembarKunci.KEADAAN);
			int sw = 12;
			int sx = rx0 + 26;
			for (int s = 0; s < total; s++) {
				boolean selesai = s < tahap || "selesai".equals(keadaan);
				boolean kini = s == tahap && ("bangun".equals(keadaan) || "dibangun".equals(keadaan));
				int warna = selesai ? 0xFF2E8963 : kini ? Palette.argb(Math.round(0x90 + 0x60 * lk.denyut()), 0x67C99B) : Palette.argb(0x50, 0x000000);
				Brush.chamfer(lk.g, sx + s * (sw + 2), ry0 + 24, sx + s * (sw + 2) + sw, ry0 + 30, 1, warna);
			}
			lk.kanan(KotaGambar.t("buku.kavling." + keadaan), rx1 - 5, ry0 + 4, "tidak_layak".equals(keadaan) ? Palette.PERINGATAN : lk.gaya.teksLemah);
		}, () -> {
			int i = dKavling.pilihan();
			if (i >= 0 && i < rows.size()) {
				pilihBaris(Buku.TAB_KAVLING, Integer.toString(rows.get(i).i(LembarKunci.ID, 0)));
			}
		}, null);
	}

	private void kabar(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Buku.KABAR);
		if (rows.isEmpty()) {
			kosong(l, KotaGambar.t("buku.kabar_kosong"), x0, y0, x1, y1);
			return;
		}
		dKabar.gambar(l, "kabar", x0, y0, x1, y1, rows.size(), 36, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			Glyphs.Atlas.ikon(lk.g, Glyphs.Atlas.SURAT, rx0 + 4, ry0 + 4, 16, 0xFFFFFFFF);
			lk.bungkus(e.teks(LembarKunci.TEKS), rx0 + 26, ry0 + 3, rx1 - rx0 - 32, lk.gaya.teks, 21.0F);
			Component dari = e.teksAtau(KotaLembar.DARI);
			String jenis = e.s(LembarKunci.JENIS);
			Component baris = KotaGambar.t("buku.kabar_dari", dari == null ? KotaGambar.t("buku.kabar_seseorang") : dari, e.l(LembarKunci.HARI, 0L));
			lk.teks(baris, rx0 + 26, ry1 - 12, rx1 - rx0 - 90, lk.gaya.teksSamar);
			if (!jenis.isEmpty()) {
				lk.kanan(KotaGambar.t("kabar.jenis." + jenis), rx1 - 5, ry1 - 12, lk.gaya.teksSamar);
			}
			Bilah.gambar(lk, rx1 - 62, ry0 + 4, rx1 - 5, e.i(Buku.PANAS, 0) / 100.0F, 0xFFD0603A, null);
		}, null, null);
	}

	// ------------------------------------------------------------------ kota_milikku

	private void daftarMilikku(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(LembarKunci.DAFTAR);
		Lembar j = lembar.sub(Milikku.JUMLAH);
		int y = y0;
		if (!j.kosongSemua()) {
			l.teks(KotaGambar.t("milikku.jumlah", j.i("rumah", 0), j.i(Milikku.MAKS_RUMAH, 0), j.i("toko", 0), j.i("usaha", 0), j.i(Milikku.MAKS_USAHA, 0),
					j.i("gudang", 0), j.i("kontrak", 0), j.i("jabatan", 0)), x0, y, x1 - x0, l.gaya.teksLemah);
			y += 13;
		}
		if (rows.isEmpty()) {
			kosong(l, KotaGambar.t("milikku.kosong"), x0, y, x1, y1);
			return;
		}
		dMilikku.gambar(l, "milikku", x0, y, x1, y1, rows.size(), 36, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			IkonKota.gambar(lk.g, ikonAset(e.s(LembarKunci.JENIS)), rx0 + 4, ry0 + 9, 16);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 26, ry0 + 3, rx1 - rx0 - 100, lk.gaya.teks);
			lk.kanan(e.teks(LembarKunci.TEMPAT), rx1 - 5, ry0 + 3, lk.gaya.teksSamar);
			lk.teks(e.teks(Milikku.RINGKAS), rx0 + 26, ry0 + 14, rx1 - rx0 - 32, lk.gaya.teksLemah);
			Component warn = e.teksAtau(Milikku.PERINGATAN);
			if (warn != null) {
				IkonKota.gambar(lk.g, IkonKota.WASPADA, rx0 + 26, ry0 + 23, 10);
				lk.teks(warn, rx0 + 38, ry0 + 24, rx1 - rx0 - 44, Palette.BAHAYA);
			}
		}, () -> {
			int i = dMilikku.pilihan();
			if (i >= 0 && i < rows.size()) {
				pilihBaris("daftar", rows.get(i).id());
			}
		}, null);
	}

	private static int ikonAset(String jenis) {
		return switch (jenis) {
			case Milikku.MJ_RUMAH -> IkonKota.RUMAH_KECIL + 1;
			case Milikku.MJ_TOKO -> IkonKota.TOKO;
			case Milikku.MJ_LAPAK -> IkonKota.USAHA;
			case Milikku.MJ_USAHA -> IkonKota.INVESTASI;
			case Milikku.MJ_GUDANG -> IkonKota.GUDANG;
			case Milikku.MJ_KONTRAK -> IkonKota.KONTRAK;
			default -> IkonKota.SEGEL_JABATAN;
		};
	}

	private void minggu(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar m = lembar.sub(Milikku.MINGGU);
		if (m.kosongSemua()) {
			kosong(l, KotaGambar.t("milikku.minggu_kosong"), x0, y0, x1, y1);
			return;
		}
		int xr = x1 - 6;
		int y = gulirMinggu.mulai(l, x0, y0, xr, y1);
		Lembar batas = m.sub(Milikku.BATAS);
		l.teks(KotaGambar.t("milikku.minggu_info"), x0, y, xr - x0, l.gaya.teksSamar);
		y += 14;
		String[] kunci = {"toko", Milikku.M_INVESTASI, "kontrak", Milikku.M_GAJI, Milikku.M_UPETI, Milikku.M_BUNGA};
		int[] ikon = {IkonKota.TOKO, IkonKota.INVESTASI, IkonKota.KONTRAK, IkonKota.SEGEL_JABATAN, IkonKota.KAS, IkonKota.TARIK};
		long total = 0;
		for (int i = 0; i < kunci.length; i++) {
			long nilai = m.l(kunci[i], 0L);
			long cap = batas.l(kunci[i], 0L);
			total += nilai;
			IkonKota.gambar(l.g, ikon[i], x0, y - 2, 14);
			Component label = KotaGambar.t("milikku.sumber." + kunci[i]);
			int lw = Math.min(100, (xr - x0) / 3);
			l.teks(label, x0 + 18, y, lw - 18, l.gaya.teks);
			Component angka = cap > 0 ? KotaGambar.t("milikku.dari_batas", Brush.angka(nilai), Brush.angka(cap)) : KotaGambar.nb(nilai);
			int aw = l.font.width(angka);
			l.kanan(angka, xr, y, l.gaya.teks);
			Bilah.gambar(l, x0 + lw + 4, y + 3, xr - aw - 6, cap <= 0 ? (nilai > 0 ? 1.0F : 0.0F) : nilai / (float) cap, nilai >= cap && cap > 0 ? 0xFFD0603A : 0xFF5FA88C,
					null);
			y += 15;
		}
		l.garis(x0, xr, y + 2);
		y += 8;
		y += KotaGambar.baris(l, KotaGambar.t("milikku.total"), KotaGambar.nb(total), x0, y, xr, KotaGambar.EMAS);
		gulirMinggu.selesai(l, y);
	}
}
