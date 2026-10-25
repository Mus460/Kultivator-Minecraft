package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.dunia.kota42.ui.layar.SketsaKota;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.CincinWaktu;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.DaftarPilihan;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KartuBarang;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Potret;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.sekte.gabung.ui.IkonGabung;
import dev.kultivator.client.sekte.gabung.ui.layar.LayarGabung2;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Yamen;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Hukum;
import dev.kultivator.sekte.gabung.ui.LembarHukum;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The one court of the mod (G64, jadwal 41/42 0.1 "No GUI twice"; DESIGN 43.8.2: one hearing page under Di Sini).
 * <ul>
 * <li>{@code yamen} (DESIGN 38.U.3.9, 38.B.5.2): the magistrate. The crime record as a bar 0-200 with the Dicurigai (50),
 * Buronan (100) and Buronan Berat (150) marks and the status seal; the fine with "Bayar denda" and "Serahkan diri" (asks
 * the confirm seal) and what each means; the confiscated goods to redeem. In a cell only a big countdown.</li>
 * <li>{@code gabung_hukum} (DESIGN 41.6.11, 41.10.9; was {@code LayarHukum}): a hearing of the Balai Hukum. The Discipline
 * Elder presides (live portrait); the record is a black-and-vermilion meter with the five thresholds (reprimand, fine,
 * confinement, demotion, expulsion); the charge, the witnesses and the sentence asked; the offences on record. "Terima
 * hukuman" (confirm seal), "Bela diri" (one Ujian Hati question as numbered choices; keys 1-3 under Di Sini) and "Minta
 * Shifu membela" (bond &ge; 50). An A2 trial (a fellow member killed) offers only to accept the expulsion or to flee as a
 * deserter (heavy confirm). A running confinement shows its countdown ring and "Tebus" (PK per 10 seconds).</li>
 * </ul>
 * Both are drawn with the single kit at text 1.0. DESIGN 42.11.9 adds two modes, opened with the 42 screen ids so the
 * server sheet keys stay {@link KotaLembar}'s; they are half-built 42-G2 work drawn by the old sheet engine with the 42
 * painters ({@link SketsaKota}) until 42U rebuilds them on the single kit (43.16.6, {@link #mesinLama}):
 * <ul>
 * <li>{@code jabatan} — the player's town office: tabs Jabatan (rank seal, wage against the cap, duties of the week,
 * attendance, rights, the next rank and its requirements, "Ajukan"), Pajak (rate within the rank's limits, last weeks'
 * income and spending as bars, festival budget of a Wali Kota), Perkara (open and decided cases, opens one) and Kavling
 * (the town's growth lots, opens one).</li>
 * <li>{@code perkara} — one hearing: tabs Sidang (the two parties face to face with their claims, the witnesses with
 * "Panggil" and their testimony bubbles), Bukti (item / note / gossip cards with "Telusuri" and the traced spreader
 * chain) and Putusan (three verdicts with their estimated consequence, each asking the confirm seal; the bribe envelope
 * in danger colours; the result).</li>
 * </ul>
 */
public final class LayarYamen extends LayarInti {

	// perkara view tabs (client-side views of one sheet)
	private static final String T_SIDANG = "sidang";
	private static final String T_BUKTI = "bukti";
	private static final String T_PUTUSAN = "putusan";
	/** The record thresholds of 41.6.11 ({@code Aturan.TEGURAN..USIR}). */
	private static final int[] AMBANG = {10, 20, 35, 50, 60};

	private final DaftarGulir sitaan = new DaftarGulir();
	private final DaftarGulir catatan = new DaftarGulir();
	/** The lists of the 42 modes (old sheet engine). */
	private final dev.kultivator.client.tempat.ui.kit.DaftarGulir daftar = new dev.kultivator.client.tempat.ui.kit.DaftarGulir();
	private int tarif = -1;
	private long festival = -1L;

	public LayarYamen(int npcId, CompoundTag lembar) {
		this(Yamen.LAYAR, npcId, lembar);
	}

	/** The court for the 38 {@code yamen} id, the 41 {@code gabung_hukum} hearing or a 42 id ({@code jabatan}, {@code perkara}). */
	public LayarYamen(String layar, int npcId, CompoundTag lembar) {
		super(layar, npcId, lembar, judulMode(layar), LembarKunci.GAYA_BATU);
	}

	/** The Balai Hukum hearing ({@code gabung_hukum}; G64: the court's hearing mode, was {@code LayarHukum}). */
	public static LayarYamen hukum(int npcId, CompoundTag lembar) {
		return new LayarYamen(Hukum.LAYAR, npcId, lembar);
	}

	private static Component judulMode(String layar) {
		return switch (layar) {
			case KotaLembar.Jabatan.LAYAR -> SketsaKota.t("jabatan.judul");
			case KotaLembar.Perkara.LAYAR -> SketsaKota.t("perkara.judul");
			case Hukum.LAYAR -> LayarGabung2.u("hukum.judul");
			default -> k("layar.yamen");
		};
	}

	private boolean hukum() {
		return Hukum.LAYAR.equals(layar);
	}

	@Override
	protected boolean mesinLama() {
		return KotaLembar.Jabatan.LAYAR.equals(layar) || KotaLembar.Perkara.LAYAR.equals(layar);
	}

	@Override
	protected List<Tab> daftarTab() {
		return switch (layar) {
			case KotaLembar.Jabatan.LAYAR -> List.of(
					new Tab(KotaLembar.Jabatan.TAB_JABATAN, SketsaKota.t("jabatan.tab.jabatan"), LembarKunci.Ikon.CAP_MERAH),
					new Tab(KotaLembar.Jabatan.TAB_PAJAK, SketsaKota.t("jabatan.tab.pajak"), LembarKunci.Ikon.TIMBANGAN),
					new Tab(KotaLembar.Jabatan.TAB_PERKARA, SketsaKota.t("jabatan.tab.perkara"), LembarKunci.Ikon.YAMEN),
					new Tab(KotaLembar.Jabatan.TAB_KAVLING, SketsaKota.t("jabatan.tab.kavling"), Glyphs.Atlas.TONGGAK));
			case KotaLembar.Perkara.LAYAR -> List.of(
					new Tab(T_SIDANG, SketsaKota.t("perkara.tab.sidang"), LembarKunci.Ikon.YAMEN),
					new Tab(T_BUKTI, SketsaKota.t("perkara.tab.bukti"), Glyphs.Atlas.GULUNGAN),
					new Tab(T_PUTUSAN, SketsaKota.t("perkara.tab.putusan"), LembarKunci.Ikon.CAP_MERAH));
			default -> List.of();
		};
	}

	/** A Balai Hukum hearing is the player's own anchor session (41): it may act (the server still checks every op). */
	@Override
	public boolean hanyaBaca() {
		return !hukum() && super.hanyaBaca();
	}

	@Override
	protected KeadaanKosong.Gambar kosongGambar() {
		return hukum() ? KeadaanKosong.Gambar.GULUNGAN : KeadaanKosong.Gambar.NAMPAN;
	}

	@Override
	protected void tabBerubah() {
		daftar.mulaiUlang(Util.getMillis());
	}

	@Override
	protected void setelahLembar(boolean buka) {
		if (hukum()) {
			if (buka) {
				catatan.mulaiUlang(Util.getMillis());
			}
			return;
		}
		if (buka || tarif < 0) {
			tarif = lembar.sub(KotaLembar.Jabatan.PAJAK).i(KotaLembar.Jabatan.TARIF, -1);
			festival = lembar.sub(KotaLembar.Jabatan.PAJAK).l(KotaLembar.Jabatan.FESTIVAL, -1L);
		}
	}

	@Override
	protected @Nullable Component petunjuk() {
		if (hukum()) {
			return lembar.ada(Hukum.SOAL) ? LayarGabung2.u("hukum.petunjuk_soal") : null;
		}
		return KotaLembar.Perkara.LAYAR.equals(layar) ? SketsaKota.t("perkara.petunjuk") : null;
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		if (hukum()) {
			hukum(l, x0, y0, x1, y1);
		} else {
			yamen(l, x0, y0, x1, y1);
		}
	}

	/** The 42 modes (old sheet engine, {@link #mesinLama}). */
	@Override
	protected void isi(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
		if (KotaLembar.Jabatan.LAYAR.equals(layar)) {
			jabatan(l, x0, y0, x1, y1);
		} else {
			perkara(l, x0, y0, x1, y1);
		}
	}

	/**
	 * A dangerous write button of the 42 modes: the press opens the Gulungan's confirm seal (G13: medium, hold 1.5 s; was a
	 * second press within 3 s) and the op is sent once confirmed.
	 */
	private void tombolYakin(dev.kultivator.client.tempat.ui.kit.Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean aktif,
			@Nullable Component alasan, String op) {
		boolean baca = !tulisBoleh();
		tombol.gambar(l, kunci, x, y, w, h, label, aktif && !baca, baca ? alasanBaca() : alasan,
				() -> konfirmasi(SegelKonfirmasi.Tingkat.SEDANG, SketsaKota.t("yakin.pesan", label), () -> kirim(op)));
	}

	// =================================================================== 38 yamen

	private void yamen(Lukis l, int x0, int y0, int x1, int y1) {
		if (lembar.ada(Yamen.SEL)) {
			sel(l, x0, y0, x1, y1);
			return;
		}
		int poin = Math.max(0, Math.min(200, lembar.i(Yamen.POIN, 0)));
		String status = lembar.s(LembarKunci.STATUS, Yamen.ST_BERSIH);
		int warna = switch (status) {
			case Yamen.ST_DICURIGAI -> Palette.PERINGATAN;
			case Yamen.ST_BURONAN -> 0xFFD06030;
			case Yamen.ST_BURONAN_BERAT -> Palette.BAHAYA;
			default -> Palette.OK;
		};
		// status seal and its name beside it
		int ss = 30;
		Glyphs.Atlas.stempel(l.g, Glyphs.Atlas.Stempel.BULAT, x1 - ss, y0, ss, ss, Palette.argb(0xE0, warna & 0xFFFFFF));
		Component nama = Brush.bold(k("yamen.status." + status));
		int nw = Math.min(l.font.width(nama), (x1 - x0) / 3);
		l.teks(nama, x1 - ss - 6 - nw, y0 + 2, nw, warna);
		l.teks(Brush.bold(k("yamen.catatan_kejahatan")), x0, y0 + 2, x1 - x0 - ss - nw - 14, l.gaya.teks);
		int by = y0 + 14;
		int bx1 = x1 - ss - 8;
		l.g.fill(x0, by, bx1, by + 7, Palette.argb(0x50, 0x000000));
		l.g.fillGradient(x0, by, x0 + Math.round((bx1 - x0) * poin / 200.0F), by + 7, warna, Palette.darken(warna, 0.3F));
		for (int mark : new int[] {50, 100, 150}) {
			int mx = x0 + Math.round((bx1 - x0) * mark / 200.0F);
			l.g.fill(mx, by - 2, mx + 1, by + 9, l.gaya.teks);
			if (l.di(mx - 3, by - 2, mx + 4, by + 9)) {
				l.tip(k("yamen.status." + (mark == 50 ? Yamen.ST_DICURIGAI : mark == 100 ? Yamen.ST_BURONAN : Yamen.ST_BURONAN_BERAT)));
			}
		}
		l.teks(k("yamen.poin", poin), x0, by + 10, bx1 - x0, l.gaya.teksLemah);
		int y = y0 + Math.max(ss, 33) + 5;
		long denda = lembar.l(Yamen.DENDA, 0L);
		Kotak[] k = bagi(x0, y, x1, y1);
		Kotak kiri = k[0];
		l.kartu(kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1(), 0.0F, false);
		int ky = kiri.y0() + 4;
		ky += l.kepala(k("yamen.denda_judul"), kiri.x0() + 4, ky, kiri.x1() - 4);
		if (denda > 0) {
			LabelHarga.gambar(l, denda, uang(), kiri.x0() + 6, ky, false, false);
			ky += 12;
		} else {
			l.samar(k("yamen.tanpa_denda"), kiri.x0() + 6, ky, kiri.w() - 12);
			ky += 11;
		}
		Component catatanDenda = lembar.teksAtau(LembarKunci.CATATAN);
		int tombolY = kiri.y1() - 2 * (TOMBOL_H + 3) - 1;
		if (catatanDenda != null) {
			l.bungkus(catatanDenda, kiri.x0() + 6, ky, kiri.w() - 12, l.gaya.teksLemah, tombolY - 2 - ky);
		}
		boolean cukup = uang() < 0 || denda <= uang();
		tombolOp(l, "denda", kiri.x0() + 4, tombolY, kiri.w() - 8, TOMBOL_H, k("yamen.bayar_denda", Brush.angka(denda)), denda > 0 && cukup,
				denda <= 0 ? k("yamen.tanpa_denda") : k("harga.kurang", Brush.angka(denda - Math.max(0L, uang()))), Yamen.OP_DENDA);
		yakin(l, "serah", kiri.x0() + 4, tombolY + TOMBOL_H + 3, kiri.w() - 8, TOMBOL_H, k("yamen.serahkan_diri"), poin > 0, k("yamen.bersih"),
				Yamen.OP_SERAH, SegelKonfirmasi.Tingkat.SEDANG, Component.translatable("kultivator.gui43.konteks.yakin", k("yamen.serahkan_diri")));
		// confiscated goods
		Kotak kanan = k[1];
		List<Lembar> rows = lembar.daftar(Yamen.SITAAN);
		int ry = kanan.y0() + l.kepala(k("yamen.sitaan"), kanan.x0(), kanan.y0(), kanan.x1());
		if (rows.isEmpty()) {
			l.samar(k("yamen.sitaan_kosong"), kanan.x0(), ry + 2, kanan.w());
			return;
		}
		long uang = uang();
		long muncul = diterima;
		sitaan.gambar(l, "sitaan", kanan.x0(), ry, kanan.x1(), kanan.y1() - 11, rows.size(), 24,
				(lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> KartuBarang.gambar(lk, rows.get(i), rx0, ry0, rx1, ry1, sorot, pilih, uang, muncul), () -> {
					int i = sitaan.pilihan();
					if (i >= 0 && i < rows.size() && tulisBoleh()) {
						kirim(LembarKunci.op(Yamen.OP_TEBUS, rows.get(i).id()));
					}
				}, null);
		l.samar(k("yamen.tebus_petunjuk"), kanan.x0(), kanan.y1() - 9, kanan.w());
	}

	private void sel(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar sel = lembar.sub(Yamen.SEL);
		float cx = (x0 + x1) / 2.0F;
		float cy = (y0 + y1) / 2.0F - 6;
		// bars of the cell
		for (int x = x0 + 6; x < x1 - 4; x += 14) {
			l.g.fillGradient(x, y0, x + 3, y1, 0xFF5A5E62, 0xFF2A2E32);
		}
		float r = Math.min(40, (y1 - y0) / 3.0F);
		Brush.disc(l.g, cx, cy, r + 6, Palette.argb(0xC0, 0x1A1C1E));
		long left = CincinWaktu.gambar(l, cx, cy, r, sel.l(LembarKunci.SISA_TICK, 0L), 0L, diterima, CincinWaktu.TANPA);
		Brush.centered(l.g, l.font, Brush.waktu(left), cx, cy - 6, 0xFFF4ECE0, 1.5F, true);
		l.tengah(k("yamen.dalam_sel"), cx, cy + r + 10, x1 - x0, l.gaya.teks);
	}

	// =================================================================== 41 Balai Hukum hearing (G64)

	private void hukum(Lukis l, int x0, int y0, int x1, int y1) {
		if (lembar.i(GabungLembar.ALIRAN, -1) == 2 && lembar.ada(GabungLembar.SEKTE_ID)) {
			// the demonic sects' stone panel washed in dark red (41.10)
			l.g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, Palette.argb(0x30, 0x6A1420));
		}
		int y = kepalaSekte(l, x0, y0, x1);
		y = hakim(l, x0, y, x1);
		y = meter(l, x0, y, x1);
		int by = y1 - TOMBOL_H;
		boolean sidang = lembar.b(LembarHukum.SIDANG);
		Lembar tebus = lembar.sub(Hukum.TEBUS);
		Lembar soal = lembar.sub(Hukum.SOAL);
		boolean sempit = tata() == Tata.SEMPIT;
		if (sempit && !soal.kosongSemua()) {
			// narrow: the pending question takes the whole box (it must be answered first)
			soal(l, new Kotak(x0, y, x1, by - 4), soal);
			tombolBawah(l, x0, by, x1, sidang, tebus, true);
			return;
		}
		int kanan = sempit ? x1 : x0 + (x1 - x0) * 11 / 20;
		// left: the case
		int ly = y;
		if (sidang) {
			Component perkara = lembar.teksAtau(LembarHukum.PERKARA);
			if (perkara != null) {
				ly += Math.round(l.bungkus(LayarGabung2.u("hukum.perkara", perkara), x0, ly, kanan - x0, l.gaya.teks, 18)) + 2;
			}
			List<Lembar> saksi = lembar.daftar(Hukum.SAKSI);
			MutableComponent s = Component.empty();
			for (int i = 0; i < saksi.size(); i++) {
				if (i > 0) {
					s.append(", ");
				}
				s.append(saksi.get(i).teks(LembarKunci.NAMA));
			}
			l.teks(saksi.isEmpty() ? LayarGabung2.u("hukum.tanpa_saksi") : LayarGabung2.u("hukum.saksi", s), x0, ly, kanan - x0, l.gaya.teksLemah);
			ly += 11;
			l.teks(Brush.bold(LayarGabung2.u("hukum.vonis", lembar.teks(Hukum.HUKUMAN))), x0, ly, kanan - x0, l.gaya.turun());
			ly += 12;
		} else {
			l.teks(LayarGabung2.u("hukum.tidak_ada"), x0, ly, kanan - x0, l.gaya.naik());
			ly += 12;
		}
		ly += l.kepala(LayarGabung2.u("hukum.catatan"), x0, ly, kanan);
		List<Lembar> list = lembar.daftar(Hukum.CATATAN_HUKUM);
		int listBawah = by - 4;
		if (list.isEmpty()) {
			l.samar(LayarGabung2.u("hukum.catatan_bersih"), x0, ly, kanan - x0);
		} else {
			catatan.gambar(l, "catatan", x0, ly, kanan, listBawah, list.size(), 12, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar e = list.get(i);
				Component hari = LayarGabung2.u("hukum.hari", e.l(LembarKunci.HARI, 0L));
				Component poin = Component.literal("+" + e.i(GabungLembar.Kartu.POIN, 0));
				int hw = lk.font.width(hari) + 4;
				int pw = lk.font.width(poin) + 4;
				lk.teks(hari, rx0, ry0 + 2, hw, lk.gaya.teksSamar);
				lk.teks(e.teks(LembarKunci.TEKS), rx0 + hw, ry0 + 2, rx1 - rx0 - hw - pw, lk.gaya.teks);
				lk.kanan(poin, rx1 - 2, ry0 + 2, lk.gaya.turun());
			}, null, null);
		}
		// right: the question, the confinement, or the elder's line
		if (!sempit) {
			Kotak k = new Kotak(kanan + 6, y, x1, by - 4);
			if (!soal.kosongSemua()) {
				soal(l, k, soal);
			} else if (!tebus.kosongSemua()) {
				tebus(l, k, tebus);
			} else {
				nasihat(l, k, sidang);
			}
		}
		tombolBawah(l, x0, by, x1, sidang, tebus, sempit);
	}

	/**
	 * The sect strip of the sheet's header keys (41.10): emblem, class plaque + tier, corak, alignment, cloud palace,
	 * MEROSOT, and a thin rule in the sect's ink colour. Returns the y under it.
	 */
	private int kepalaSekte(Lukis l, int x0, int y0, int x1) {
		if (!lembar.ada(GabungLembar.SEKTE_ID)) {
			return y0;
		}
		int y = y0 + 1;
		IkonGabung.lambang(l.g, l.font, lembar.i(GabungLembar.LAMBANG, 0), x0 + 6.5F, y + 6.5F, 6.5F);
		int x = x0 + 16;
		int kelas = lembar.i(GabungLembar.KELAS, 1);
		IkonGabung.gambar(l.g, IkonGabung.kelas(kelas), x, y, 12);
		x += 14;
		Component kc = KelasSekte.byNomor(kelas).nama().copy().append(" · ")
				.append(Component.translatable("kultivator.gabung.ui.tier", lembar.i(GabungLembar.TINGKAT, 1)));
		int kw = Math.min(150, Math.min(l.font.width(kc), x1 - x - 50));
		l.teks(kc, x, y + 2, kw, l.gaya.teks);
		x += kw + 6;
		String corak = lembar.s(GabungLembar.CORAK);
		if (!corak.isEmpty() && x + 14 < x1) {
			IkonGabung.gambar(l.g, IkonGabung.corak(corak), x, y, 12);
			if (l.di(x, y, x + 12, y + 12)) {
				l.tip(Component.translatable("kultivator.gabung.ui.corak", Corak.byId(corak, Corak.PEDANG).nama()));
			}
			x += 14;
		}
		int aliran = lembar.i(GabungLembar.ALIRAN, 0);
		if (x + 12 < x1) {
			IkonGabung.gambar(l.g, IkonGabung.ALIRAN + Math.max(0, Math.min(2, aliran)), x, y, 12);
			if (l.di(x, y, x + 12, y + 12)) {
				l.tip(Component.translatable("kultivator.gabung.ui.aliran." + aliran));
			}
			x += 14;
		}
		if (lembar.b(GabungLembar.LANGIT) && x + 12 < x1) {
			IkonGabung.gambar(l.g, IkonGabung.LANGIT, x, y, 12);
			if (l.di(x, y, x + 12, y + 12)) {
				l.tip(Component.translatable("kultivator.gabung.ui.langit"));
			}
			x += 14;
		}
		if (lembar.b(GabungLembar.MEROSOT) && x + 12 < x1) {
			IkonGabung.gambar(l.g, IkonGabung.MEROSOT, x, y, 12);
			if (l.di(x, y, x + 12, y + 12)) {
				l.tip(Component.translatable("kultivator.gabung.ui.merosot"));
			}
		}
		int ink = Palette.opaque(lembar.i(GabungLembar.WARNA, 0xB0B0B0));
		l.g.fill(x0, y0 + 15, x1, y0 + 16, Palette.argb(0xB0, ink & 0xFFFFFF));
		return y0 + 19;
	}

	/** The presiding elder: portrait, name and office. Returns the y under it. */
	private int hakim(Lukis l, int x0, int y, int x1) {
		Lembar npc = lembar.sub(LembarKunci.NPC);
		int foto = 22;
		Potret.gambar(l, x0, y, foto, npc.i(LembarKunci.POTRET, -1), Potret.IKON_BAWAAN, false, 0.0F);
		IkonGabung.gambar(l.g, IkonGabung.HUKUM, x0 + foto + 4, y + 1, 10);
		l.teks(Brush.bold(npc.teks(LembarKunci.NAMA)), x0 + foto + 17, y + 1, x1 - x0 - foto - 20, l.gaya.teks);
		l.teks(npc.teks(LembarKunci.GELAR), x0 + foto + 4, y + 12, x1 - x0 - foto - 8, l.gaya.teksLemah);
		return y + foto + 4;
	}

	/** The record meter 0-100 with the five thresholds. Returns the y under it. */
	private int meter(Lukis l, int x0, int y, int x1) {
		int nilai = lembar.i(Hukum.PELANGGARAN, 0);
		Component label = LayarGabung2.u("hukum.pelanggaran", nilai);
		int lw = Math.min(l.font.width(label), (x1 - x0) / 2) + 6;
		l.teks(label, x0, y, lw - 6, nilai >= 35 ? l.gaya.turun() : l.gaya.teks);
		int bx0 = x0 + lw;
		int bw = x1 - bx0;
		l.g.fill(bx0, y + 1, x1, y + 8, Palette.argb(0x70, 0x000000));
		int isi = Math.round(bw * Math.min(1.0F, nilai / 100.0F));
		l.g.fillGradient(bx0, y + 2, bx0 + isi, y + 7, 0xFF8A1A1A, 0xFFD03A2A);
		for (int i = 0; i < AMBANG.length; i++) {
			int tx = bx0 + Math.round(bw * AMBANG[i] / 100.0F);
			boolean lewat = nilai >= AMBANG[i];
			l.g.fill(tx, y, tx + 1, y + 9, lewat ? 0xFFFFE0A0 : 0x80FFFFFF);
			if (l.di(tx - 3, y - 1, tx + 4, y + 10)) {
				l.tip(LayarGabung2.u("hukum.ambang." + i, AMBANG[i]));
			}
		}
		l.garis(x0, x1, y + 12);
		return y + 16;
	}

	/** The Ujian Hati question of "Bela diri": numbered choices (↑↓ Enter, a click, keys 1-3 under Di Sini). */
	private void soal(Lukis l, Kotak k, Lembar soal) {
		l.sumur(k.x0(), k.y0(), k.x1(), k.y1());
		int teks = l.gaya.teksSumur();
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		IkonGabung.gambar(l.g, IkonGabung.HATI, x0, y, 12);
		l.teks(Brush.bold(LayarGabung2.u("hukum.soal_judul")), x0 + 15, y + 2, x1 - x0 - 15, teks);
		y += 15;
		List<Lembar> daftarPilihan = soal.daftar(Hukum.PILIHAN);
		int sisa = k.y1() - 4 - DaftarPilihan.tinggi(daftarPilihan.size(), 1, 15);
		y += Math.round(l.bungkus(soal.teks(LembarKunci.TEKS).copy().withStyle(ChatFormatting.ITALIC), x0, y, x1 - x0, teks, Math.max(9, sisa - y - 4))) + 4;
		List<DaftarPilihan.Pilihan> pilihan = new ArrayList<>(daftarPilihan.size());
		for (int i = 0; i < daftarPilihan.size(); i++) {
			pilihan.add(DaftarPilihan.Pilihan.of(i + 1, daftarPilihan.get(i).teks(LembarKunci.TEKS)));
		}
		DaftarPilihan.gambar(l, "jawab", pilihan, x0, y, x1, k.y1() - 3, false, p -> {
			int i = p.no() - 1;
			if (i >= 0 && i < daftarPilihan.size()) {
				kirim(LembarKunci.op(Hukum.OP_JAWAB, daftarPilihan.get(i).i(LembarKunci.NO, i)));
			}
		});
	}

	private void tebus(Lukis l, Kotak k, Lembar t) {
		l.sumur(k.x0(), k.y0(), k.x1(), k.y1());
		int teks = l.gaya.teksSumur();
		float cx = (k.x0() + k.x1()) / 2.0F;
		int y = k.y0() + 4;
		l.tengah(Brush.bold(LayarGabung2.u("hukum.kurungan")), cx, y, k.w() - 10, teks);
		y += 12;
		float r = Math.min(20.0F, (k.y1() - y - 40) / 2.0F);
		if (r >= 8.0F) {
			CincinWaktu.gambar(l, cx, y + r, r, t.l(LembarKunci.SISA_TICK, 0L), 0L, diterima, CincinWaktu.DALAM);
			y += Math.round(r * 2) + 4;
		}
		l.tengah(LayarGabung2.u("hukum.tebus_harga", t.i(LembarKunci.HARGA, 5)), cx, y, k.w() - 10, Palette.argb(0xB0, teks & 0xFFFFFF));
		y += 11;
		int bw = Math.min(150, k.w() - 10);
		tombolOp(l, "tebus", Math.round(cx - bw / 2.0F), Math.min(y, k.y1() - TOMBOL_H - 2), bw, TOMBOL_H,
				LayarGabung2.u("hukum.tebus", Brush.angka(t.i(LembarKunci.BIAYA, 0))), t.b(LembarKunci.BISA), t.teksAtau(LembarKunci.ALASAN), Hukum.OP_TEBUS);
	}

	private void nasihat(Lukis l, Kotak k, boolean sidang) {
		l.sumur(k.x0(), k.y0(), k.x1(), k.y1());
		int teks = l.gaya.teksSumur();
		int x0 = k.x0() + 5;
		int y = k.y0() + 4;
		IkonGabung.gambar(l.g, IkonGabung.HUKUM, x0, y, 16);
		y += 20;
		Component isi = lembar.b(LembarHukum.A2) ? LayarGabung2.u("hukum.nasihat.a2")
				: sidang ? LayarGabung2.u("hukum.nasihat.sidang") : LayarGabung2.u("hukum.nasihat.bersih");
		y += Math.round(l.bungkus(isi.copy().withStyle(ChatFormatting.ITALIC), x0, y, k.w() - 10, teks, k.y1() - y - 14)) + 3;
		if (lembar.b(Hukum.SHIFU) && lembar.ada(GabungLembar.Kartu.SHIFU)) {
			l.teks(LayarGabung2.u("hukum.shifu_siap", lembar.teks(GabungLembar.Kartu.SHIFU), lembar.i(GabungLembar.IKATAN, 0)), x0, y, k.w() - 10,
					Palette.OK);
		} else if (lembar.ada(GabungLembar.IKATAN)) {
			l.teks(LayarGabung2.u("hukum.ikatan", lembar.i(GabungLembar.IKATAN, 0)), x0, y, k.w() - 10, Palette.argb(0xB0, teks & 0xFFFFFF));
		}
	}

	private void tombolBawah(Lukis l, int x0, int by, int x1, boolean sidang, Lembar tebus, boolean sempit) {
		if (!sidang) {
			if (!tebus.kosongSemua() && sempit) {
				tombolOp(l, "tebusS", x0, by, Math.min(180, x1 - x0), TOMBOL_H, LayarGabung2.u("hukum.tebus", Brush.angka(tebus.i(LembarKunci.BIAYA, 0))),
						tebus.b(LembarKunci.BISA), tebus.teksAtau(LembarKunci.ALASAN), Hukum.OP_TEBUS);
			}
			return;
		}
		if (lembar.b(LembarHukum.A2)) {
			// expulsion or desertion: both final, the heavy seal (tick + hold)
			int bw = (x1 - x0 - 4) / 2;
			Component usir = LayarGabung2.u("hukum.terima_usir");
			Component lari = LayarGabung2.u("hukum.lari");
			yakin(l, "terimaA2", x0, by, bw, TOMBOL_H, usir, true, null, Hukum.OP_TERIMA, SegelKonfirmasi.Tingkat.BERAT,
					Component.translatable("kultivator.gui43.konteks.yakin", usir));
			yakin(l, "lari", x0 + bw + 4, by, bw, TOMBOL_H, lari, true, null, LembarHukum.OP_LARI, SegelKonfirmasi.Tingkat.BERAT,
					Component.translatable("kultivator.gui43.konteks.yakin", lari));
			return;
		}
		int bw = (x1 - x0 - 8) / 3;
		Component terima = LayarGabung2.u("hukum.terima");
		yakin(l, "terima", x0, by, bw, TOMBOL_H, terima, true, null, Hukum.OP_TERIMA, SegelKonfirmasi.Tingkat.SEDANG,
				Component.translatable("kultivator.gui43.konteks.yakin", terima));
		boolean bela = lembar.b(LembarHukum.BELA) && !lembar.ada(Hukum.SOAL);
		tombolOp(l, "bela", x0 + bw + 4, by, bw, TOMBOL_H, LayarGabung2.u("hukum.bela"), bela,
				lembar.ada(Hukum.SOAL) ? LayarGabung2.u("hukum.jawab_dulu") : LayarGabung2.u("hukum.bela_habis"), Hukum.OP_BELA);
		tombolOp(l, "shifu", x0 + (bw + 4) * 2, by, bw, TOMBOL_H, LayarGabung2.u("hukum.minta_shifu"), lembar.b(Hukum.SHIFU),
				LayarGabung2.u("hukum.shifu_tidak", lembar.i(GabungLembar.IKATAN, 0)), Hukum.OP_MINTA_SHIFU);
	}

	// =================================================================== 42 jabatan

	private void jabatan(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
		int y = SketsaKota.kota(l, lembar, x0, y0, x1, gaya);
		switch (tab) {
			case KotaLembar.Jabatan.TAB_PAJAK -> pajak(l, x0, y, x1, y1);
			case KotaLembar.Jabatan.TAB_PERKARA -> daftarPerkara(l, x0, y, x1, y1);
			case KotaLembar.Jabatan.TAB_KAVLING -> daftarKavling(l, x0, y, x1, y1);
			default -> kartuJabatan(l, x0, y, x1, y1);
		}
	}

	private void kartuJabatan(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
		Lembar j = lembar.sub(KotaLembar.Jabatan.JABATAN);
		int pangkat = j.i(KotaLembar.Jabatan.PANGKAT, 0);
		Kotak[] k = bagi(x0, y0, x1, y1);
		Kotak kiri = k[0];
		PanelTempat.kartu(l.g, gaya, kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1(), 0.0F, false);
		// rank seal: a square cinnabar seal with the rank number
		int ss = 30;
		int sx = kiri.x0() + 4;
		int sy = kiri.y0() + 4;
		int segel = pangkat > 0 ? Palette.SEAL : Palette.LOCKED;
		IkonTempat.stempel(l.g, IkonTempat.Stempel.PERSEGI, sx, sy, ss, ss, Palette.argb(0xE8, segel & 0xFFFFFF));
		Brush.centered(l.g, font, Component.literal(pangkat > 0 ? Integer.toString(pangkat) : "—"), sx + ss / 2.0F, sy + ss / 2.0F - 6, 0xFFFFF4E0, 1.5F, true);
		int tx = sx + ss + 6;
		l.teks(Brush.bold(j.teks(LembarKunci.GELAR)), tx, sy + 2, kiri.x1() - tx - 4, gaya.teks, 1.0F);
		long gaji = j.l(KotaLembar.Jabatan.GAJI, 0L);
		long gajiMaks = j.l(KotaLembar.Jabatan.GAJI_MAKS, 0L);
		l.keterangan(SketsaKota.t("jabatan.gaji", TeksTempat.angka(gaji), TeksTempat.angka(gajiMaks)), tx, sy + 13, kiri.x1() - tx - 4, gaya.teksLemah);
		SketsaKota.batang(l, tx, sy + 22, kiri.x1() - tx - 8, 3, gajiMaks <= 0 ? 0.0F : gaji / (float) gajiMaks, Palette.GOLD, -1.0F);
		int y = sy + ss + 6;
		if (pangkat > 0) {
			long hadir = j.l(KotaLembar.Jabatan.HADIR, -1L);
			l.keterangan(hadir < 0 ? SketsaKota.t("jabatan.hadir_belum") : SketsaKota.t("jabatan.hadir", hadir), kiri.x0() + 6, y, kiri.w() - 12, gaya.teksLemah);
			y += 10;
		}
		Component warn = j.teksAtau(KotaLembar.Jabatan.PERINGATAN);
		if (warn != null) {
			IkonTempat.gambar(l.g, LembarKunci.Ikon.LONCENG, kiri.x0() + 5, y - 1, 9, 0xFFFFFFFF);
			l.keterangan(warn, kiri.x0() + 16, y, kiri.w() - 22, TempatWarna.BAHAYA);
			y += 10;
		}
		y += subjudul(l, SketsaKota.t("jabatan.tugas"), kiri.x0() + 4, y, kiri.x1() - 4);
		int tombolY = kiri.y1() - 18;
		for (Lembar t : j.daftar(KotaLembar.Jabatan.TUGAS)) {
			if (y + 10 > tombolY) {
				break;
			}
			int maks = t.i(LembarKunci.MAKS, 0);
			boolean selesai = t.b(KotaLembar.Jabatan.SELESAI);
			int bw = maks > 0 ? 34 : 0;
			IkonTempat.gambar(l.g, maks > 0 && selesai ? IkonTempat.CENTANG : IkonTempat.TONGGAK, kiri.x0() + 5, y, 8, 0xFFFFFFFF);
			l.teks(t.teks(LembarKunci.TEKS), kiri.x0() + 15, y, kiri.w() - 22 - bw, selesai ? gaya.teksSamar : gaya.teks, 0.75F);
			if (maks > 0) {
				int kemajuan = t.i(LembarKunci.KEMAJUAN, 0);
				SketsaKota.batang(l, kiri.x1() - 6 - bw, y + 2, bw - 12, 3, kemajuan / (float) maks, selesai ? TempatWarna.OK : TempatWarna.PERINGATAN, -1.0F);
				l.kanan(Component.literal(kemajuan + "/" + maks), kiri.x1() - 5, y, gaya.teksLemah, 0.7F);
			}
			y += 10;
		}
		// duty buttons
		int bw = (kiri.w() - 12) / 2;
		boolean periksa = j.b("boleh_periksa");
		if (pangkat >= KotaLembar.Jabatan.PENGAWAS_PASAR) {
			tombolOp(l, "periksa", kiri.x0() + 4, tombolY, bw, 14, SketsaKota.t("jabatan.periksa"), periksa, SketsaKota.t("jabatan.periksa_habis"), "periksa");
		}
		if (pangkat >= KotaLembar.Jabatan.WALI_KOTA) {
			int pj = j.i("penjaga", 0);
			int pjm = j.i("penjaga_maks", 6);
			tombolOp(l, "penjaga", kiri.x0() + 8 + bw, tombolY, bw, 14, SketsaKota.t("jabatan.penjaga", pj, pjm), pj < pjm, SketsaKota.t("jabatan.penjaga_penuh"),
					"penjaga");
		}
		// right: rights and the next rank
		Kotak kanan = k[1];
		int ry = kanan.y0();
		ry += subjudul(l, SketsaKota.t("jabatan.hak"), kanan.x0(), ry, kanan.x1());
		List<Lembar> hak = j.daftar(KotaLembar.Jabatan.HAK);
		Lembar berikut = j.sub(KotaLembar.Jabatan.BERIKUT);
		int batasHak = berikut.kosongSemua() ? kanan.y1() : kanan.y1() - 70;
		for (Lembar h : hak) {
			if (ry + 9 > batasHak) {
				break;
			}
			boolean ok = h.b(LembarKunci.OK);
			Brush.text(l.g, font, Component.literal(Integer.toString(h.i(KotaLembar.Jabatan.PANGKAT, 0))), kanan.x0(), ry, ok ? Palette.SEAL : gaya.teksSamar,
					0.7F, false);
			l.teks(h.teks(LembarKunci.TEKS), kanan.x0() + 8, ry, kanan.w() - 8, ok ? gaya.teks : TempatWarna.terkunci(gaya.teksSamar), 0.75F);
			ry += 9;
		}
		if (berikut.kosongSemua()) {
			l.keterangan(SketsaKota.t("jabatan.puncak"), kanan.x0(), Math.min(ry + 4, kanan.y1() - 10), kanan.w(), TempatWarna.EMAS);
			return;
		}
		int by = kanan.y1() - 68;
		PanelTempat.sumur(l.g, gaya, kanan.x0(), by, kanan.x1(), kanan.y1());
		l.teks(SketsaKota.t("jabatan.berikut", berikut.teks(LembarKunci.GELAR)), kanan.x0() + 4, by + 3, kanan.w() - 8, gaya.teksSumur(), 0.8F);
		int sy2 = by + 14;
		for (Lembar s : berikut.daftar(LembarKunci.SYARAT)) {
			int h = SketsaKota.syarat(l, s, kanan.x0() + 4, sy2, kanan.x1() - 4, kanan.y1() - 18);
			if (h == 0) {
				break;
			}
			sy2 += h;
		}
		boolean bisa = berikut.b(LembarKunci.BISA);
		tombolOp(l, "ajukan", kanan.x0() + 4, kanan.y1() - 17, kanan.w() - 8, 14, SketsaKota.t("jabatan.ajukan"), bisa, berikut.teksAtau(LembarKunci.ALASAN),
				KotaLembar.Jabatan.OP_AJUKAN);
		Lembar c = lembar.sub(LembarKunci.CATATAN);
		if (pangkat >= KotaLembar.Jabatan.HAKIM_PEMBANTU && !c.kosongSemua() && l.di(sx, sy, sx + ss, sy + ss)) {
			l.tip(SketsaKota.t("jabatan.catatan", c.i(KotaLembar.Jabatan.DIPUTUS, 0), c.i(KotaLembar.Jabatan.SUAP, 0), c.i(KotaLembar.Jabatan.AUDIT, 0)));
		}
	}

	private void pajak(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
		Lembar p = lembar.sub(KotaLembar.Jabatan.PAJAK);
		int min = p.i(KotaLembar.Jabatan.MIN, 5);
		int maks = p.i(LembarKunci.MAKS, 20);
		int sekarang = p.i(KotaLembar.Jabatan.TARIF, 8);
		if (tarif < 0) {
			tarif = sekarang;
		}
		tarif = Math.max(min, Math.min(maks, tarif));
		boolean bisa = p.b(LembarKunci.BISA);
		Kotak[] k = bagi(x0, y0, x1, y1);
		Kotak kiri = k[0];
		PanelTempat.kartu(l.g, gaya, kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1(), 0.0F, false);
		int y = kiri.y0() + 4;
		y += subjudul(l, SketsaKota.t("pajak.tarif"), kiri.x0() + 4, y, kiri.x1() - 4);
		float cx = (kiri.x0() + kiri.x1()) / 2.0F;
		// the rate on a 5-20 scale with the allowed band shaded
		int sx0 = kiri.x0() + 10;
		int sx1 = kiri.x1() - 10;
		int sy = y + 22;
		l.g.fill(sx0, sy, sx1, sy + 4, Palette.argb(0x50, 0x000000));
		int bx0 = sx0 + Math.round((sx1 - sx0) * (min - 5) / 15.0F);
		int bx1 = sx0 + Math.round((sx1 - sx0) * (maks - 5) / 15.0F);
		l.g.fill(bx0, sy, Math.max(bx0 + 1, bx1), sy + 4, Palette.argb(0xA0, 0x5FA88C));
		int mx = sx0 + Math.round((sx1 - sx0) * (10 - 5) / 15.0F);
		l.g.fill(mx, sy - 3, mx + 1, sy + 7, TempatWarna.PERINGATAN);
		int nx = sx0 + Math.round((sx1 - sx0) * (sekarang - 5) / 15.0F);
		Brush.disc(l.g, nx + 0.5F, sy + 2, 2.5F, gaya.teksSamar);
		int px = sx0 + Math.round((sx1 - sx0) * (tarif - 5) / 15.0F);
		Brush.disc(l.g, px + 0.5F, sy + 2, 4.0F, Palette.SEAL);
		Brush.centered(l.g, font, Component.literal(tarif + " %"), cx, y + 2, tarif > 10 ? TempatWarna.BAHAYA : gaya.teks, 1.4F, false);
		y = sy + 10;
		l.bungkus(p.teks(KotaLembar.Jabatan.EFEK), kiri.x0() + 6, y, kiri.w() - 12, gaya.teksLemah, 0.75F, 20);
		int by = kiri.y1() - 18;
		Component alasan = p.teksAtau(LembarKunci.ALASAN);
		tombol.gambar(l, "tarif#kurang", kiri.x0() + 4, by, 16, 14, Component.literal("−"), bisa && tarif > min, alasan, () -> tarif--);
		tombol.gambar(l, "tarif#tambah", kiri.x0() + 22, by, 16, 14, Component.literal("+"), bisa && tarif < maks, alasan, () -> tarif++);
		tombolOp(l, "tarif", kiri.x0() + 42, by, kiri.w() - 46, 14, SketsaKota.t("pajak.tetapkan", tarif), bisa && tarif != sekarang,
				alasan != null ? alasan : SketsaKota.t("pajak.sama"), LembarKunci.op(KotaLembar.Jabatan.OP_PAJAK, tarif));
		// festival budget of a Wali Kota
		if (p.ada(KotaLembar.Jabatan.FESTIVAL)) {
			long fmaks = p.l(LembarKunci.HARGA, 400L);
			if (festival < 0L) {
				festival = p.l(KotaLembar.Jabatan.FESTIVAL, 0L);
			}
			int fy = by - 30;
			l.keterangan(SketsaKota.t("pajak.festival", TeksTempat.angka(festival), TeksTempat.angka(fmaks)), kiri.x0() + 6, fy, kiri.w() - 12, gaya.teksLemah);
			tombol.gambar(l, "fest#kurang", kiri.x0() + 4, fy + 10, 16, 13, Component.literal("−"), festival > 0, null, () -> festival = Math.max(0L, festival - 20L));
			tombol.gambar(l, "fest#tambah", kiri.x0() + 22, fy + 10, 16, 13, Component.literal("+"), festival < fmaks, null,
					() -> festival = Math.min(fmaks, festival + 20L));
			tombolOp(l, "fest", kiri.x0() + 42, fy + 10, kiri.w() - 46, 13, SketsaKota.t("pajak.festival_simpan"),
					festival != p.l(KotaLembar.Jabatan.FESTIVAL, 0L), SketsaKota.t("pajak.sama"), LembarKunci.op(KotaLembar.Jabatan.OP_FESTIVAL, festival));
		}
		// income and spending of the last weeks
		Kotak kanan = k[1];
		int half = (kanan.y0() + kanan.y1()) / 2;
		riwayat(l, SketsaKota.t("pajak.masuk"), p.daftar(KotaLembar.Jabatan.MASUK), kanan.x0(), kanan.y0(), kanan.x1(), half - 2, TempatWarna.OK);
		riwayat(l, SketsaKota.t("pajak.keluar"), p.daftar(KotaLembar.Jabatan.KELUAR), kanan.x0(), half + 2, kanan.x1(), kanan.y1(), TempatWarna.BAHAYA);
	}

	private void riwayat(dev.kultivator.client.tempat.ui.kit.Lukis l, Component judul, List<Lembar> rows, int x0, int y0, int x1, int y1, int warna) {
		int y = y0 + subjudul(l, judul, x0, y0, x1);
		if (rows.isEmpty()) {
			l.keterangan(SketsaKota.t("pajak.riwayat_kosong"), x0, y + 2, x1 - x0, gaya.teksSamar);
			return;
		}
		long maks = 1L;
		for (Lembar r : rows) {
			maks = Math.max(maks, r.l(LembarKunci.HARGA, 0L));
		}
		// newest left, one bar per week
		int n = rows.size();
		int gap = 3;
		int bw = Math.max(4, (x1 - x0 - (n - 1) * gap) / Math.max(1, n));
		int base = y1 - 10;
		for (int i = 0; i < n; i++) {
			long v = rows.get(i).l(LembarKunci.HARGA, 0L);
			int h = Math.round((base - y - 2) * v / (float) maks);
			int bx = x0 + i * (bw + gap);
			l.g.fillGradient(bx, base - h, bx + bw, base, warna, Palette.darken(warna, 0.35F));
			if (l.di(bx, base - h - 2, bx + bw, base)) {
				l.tip(List.of(rows.get(i).teks(LembarKunci.TEKS), Component.literal(TeksTempat.angka(v) + " NB")));
			}
		}
		l.keterangan(SketsaKota.t("pajak.minggu_ini", TeksTempat.angka(rows.get(0).l(LembarKunci.HARGA, 0L))), x0, base + 1, x1 - x0, gaya.teksLemah);
	}

	private void daftarPerkara(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(KotaLembar.Jabatan.PERKARA);
		Lembar c = lembar.sub(LembarKunci.CATATAN);
		l.keterangan(SketsaKota.t("jabatan.catatan", c.i(KotaLembar.Jabatan.DIPUTUS, 0), c.i(KotaLembar.Jabatan.SUAP, 0), c.i(KotaLembar.Jabatan.AUDIT, 0)),
				x0, y0, x1 - x0, gaya.teksSamar);
		int y = y0 + 11;
		if (rows.isEmpty()) {
			kosong(l, SketsaKota.t("jabatan.perkara_kosong"), x0, y, x1, y1);
			return;
		}
		daftar.gambar(l, "perkara", x0, y, x1, y1, rows.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			boolean selesai = e.b(KotaLembar.Jabatan.SELESAI);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			IkonTempat.gambar(lk.g, selesai ? LembarKunci.Ikon.CAP_MERAH : LembarKunci.Ikon.TIMBANGAN, rx0 + 3, ry0 + 4, 16, 0xFFFFFFFF);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 23, ry0 + 3, rx1 - rx0 - 90, selesai ? gaya.teksLemah : gaya.teks, 0.85F);
			Component bawah = selesai ? SketsaKota.t("perkara.diputus", e.teks(KotaLembar.Jabatan.PUTUSAN))
					: SketsaKota.t("perkara.baris", e.i(KotaLembar.Jabatan.SAKSI, 0), e.i(KotaLembar.Jabatan.BUKTI, 0));
			lk.keterangan(bawah, rx0 + 23, ry0 + 14, rx1 - rx0 - 90, gaya.teksLemah);
			if (!selesai) {
				int sisa = e.i(LembarKunci.SISA_HARI, 0);
				lk.kanan(SketsaKota.t("perkara.sisa", sisa), rx1 - 5, ry0 + 4, sisa <= 1 ? TempatWarna.BAHAYA : gaya.teksLemah, 0.75F);
			}
			lk.kanan(SketsaKota.t(selesai ? "perkara.lihat" : "perkara.sidang"), rx1 - 5, ry0 + 15, TempatWarna.EMAS, 0.7F);
		}, () -> {
			int i = daftar.pilihan();
			if (i >= 0 && i < rows.size()) {
				kirim(LembarKunci.op(KotaLembar.Jabatan.OP_PERKARA, rows.get(i).id()));
			}
		}, null);
	}

	private void daftarKavling(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(KotaLembar.Jabatan.TAB_KAVLING);
		if (rows.isEmpty()) {
			kosong(l, SketsaKota.t("kavling.kosong"), x0, y0, x1, y1);
			return;
		}
		daftar.gambar(l, "kavling", x0, y0, x1, y1, rows.size(), 24, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			String keadaan = e.s(LembarKunci.KEADAAN);
			IkonTempat.gambar(lk.g, IkonTempat.TONGGAK, rx0 + 3, ry0 + 4, 14, 0xFFFFFFFF);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 21, ry0 + 3, (rx1 - rx0) / 2 - 24, gaya.teks, 0.85F);
			lk.keterangan(e.teks(KotaLembar.Kavling.RENCANA), rx0 + 21, ry0 + 13, (rx1 - rx0) / 2 - 24, gaya.teksLemah);
			int tahap = e.i("tahap", 0);
			int total = Math.max(1, e.i(LembarKunci.TAHAP_TOTAL, 5));
			int bx = rx0 + (rx1 - rx0) / 2 + 4;
			for (int s = 0; s < total; s++) {
				int c = s < tahap ? 0xFF5FA88C : Palette.argb(0x50, 0x000000);
				lk.g.fill(bx + s * 9, ry0 + 6, bx + s * 9 + 7, ry0 + 11, c);
			}
			lk.keterangan(SketsaKota.t("kavling.keadaan." + keadaan), bx, ry0 + 13, rx1 - bx - 4, KotaLembar.Kavling.KV_TIDAK_LAYAK.equals(keadaan)
					? TempatWarna.BAHAYA : gaya.teksSamar);
		}, () -> {
			int i = daftar.pilihan();
			if (i >= 0 && i < rows.size()) {
				kirim(LembarKunci.op(KotaLembar.Jabatan.OP_KAVLING, Integer.toString(rows.get(i).i(LembarKunci.ID, 0))));
			}
		}, null);
	}

	// =================================================================== 42 perkara

	private void perkara(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
		Lembar c = lembar.sub(KotaLembar.Perkara.PERKARA);
		if (c.kosongSemua()) {
			kosong(l, SketsaKota.t("perkara.kosong"), x0, y0, x1, y1);
			return;
		}
		// case line + days left
		int sisa = c.i(LembarKunci.SISA_HARI, 0);
		Component kanan = SketsaKota.t("perkara.sisa", sisa);
		int kw = Math.round(font.width(kanan) * 0.75F) + 4;
		l.teks(Brush.bold(c.teks(LembarKunci.NAMA)), x0, y0, x1 - x0 - kw, gaya.teks, 0.9F);
		l.kanan(kanan, x1, y0 + 1, sisa <= 1 ? TempatWarna.BAHAYA : gaya.teksLemah, 0.75F);
		l.keterangan(c.teks(LembarKunci.TEKS), x0, y0 + 10, x1 - x0, gaya.teksLemah);
		int y = y0 + 21;
		// other open cases: small chips to switch
		List<Lembar> lain = lembar.daftar("terbuka");
		if (lain.size() > 1) {
			int cx = x0;
			for (Lembar o : lain) {
				Component n = Component.literal("#" + o.id());
				int w = Math.round(font.width(n) * 0.75F) + 8;
				if (cx + w > x1) {
					break;
				}
				boolean dipilih = o.b(KotaLembar.PILIH);
				tombol.gambar(l, "kasus" + o.id(), cx, y, w, 10, n, !dipilih, null, () -> kirim(LembarKunci.op(LembarKunci.OP_TAB, KotaLembar.Perkara.LAYAR,
						o.id())), false, dipilih);
				cx += w + 3;
			}
			y += 13;
		}
		switch (tab) {
			case T_BUKTI -> bukti(l, x0, y, x1, y1);
			case T_PUTUSAN -> putusan(l, x0, y, x1, y1);
			default -> sidang(l, x0, y, x1, y1);
		}
	}

	private void sidang(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> pihak = lembar.daftar(KotaLembar.Perkara.PIHAK);
		int tinggiPihak = Math.min(58, (y1 - y0) / 2);
		int mid = (x0 + x1) / 2;
		for (int i = 0; i < pihak.size() && i < 2; i++) {
			Lembar p = pihak.get(i);
			boolean kiri = i == 0;
			int bx0 = kiri ? x0 : mid + 3;
			int bx1 = kiri ? mid - 3 : x1;
			PanelTempat.kartu(l.g, gaya, bx0, y0, bx1, y0 + tinggiPihak, 0.0F, false);
			int ps = Math.min(32, tinggiPihak - 6);
			int px = kiri ? bx0 + 3 : bx1 - 3 - ps;
			dev.kultivator.client.tempat.ui.kit.Potret.gambar(l, px, y0 + 3, ps, p.i(LembarKunci.POTRET, -1), LembarKunci.Ikon.TIDAK_ADA, false, 0.0F);
			int tx0 = kiri ? px + ps + 4 : bx0 + 4;
			int tx1 = kiri ? bx1 - 4 : px - 4;
			l.keterangan(SketsaKota.t("perkara.pihak." + p.s(KotaLembar.Perkara.PERAN_PIHAK)), tx0, y0 + 3, tx1 - tx0, gaya.teksSamar);
			l.teks(Brush.bold(p.teks(LembarKunci.NAMA)), tx0, y0 + 11, tx1 - tx0, gaya.teks, 0.85F);
			l.keterangan(SketsaKota.umurKerja(p), tx0, y0 + 21, tx1 - tx0, gaya.teksLemah);
			if (p.ada(KotaLembar.WATAK)) {
				l.keterangan(SketsaKota.t("watak." + p.s(KotaLembar.WATAK)), tx0, y0 + 29, tx1 - tx0, TempatWarna.PERINGATAN);
			}
			// claim bubble under the portrait
			int qy = y0 + Math.max(ps + 5, 38);
			l.bungkus(SketsaKota.t("perkara.klaim", p.teks(KotaLembar.Perkara.KLAIM)), bx0 + 4, qy, bx1 - bx0 - 8, gaya.teks, 0.7F, y0 + tinggiPihak - qy - 2);
		}
		// "vs" seal between them
		IkonTempat.stempel(l.g, IkonTempat.Stempel.BULAT, mid - 8, y0 + tinggiPihak / 2 - 8, 16, 16, Palette.argb(0xD0, 0x7A1A1A));
		Brush.centered(l.g, font, Component.literal("⚖"), mid, y0 + tinggiPihak / 2 - 4, 0xFFFFF0E0, 0.9F, false);
		int y = y0 + tinggiPihak + 4;
		y += subjudul(l, SketsaKota.t("perkara.saksi"), x0, y, x1);
		List<Lembar> saksi = lembar.daftar(KotaLembar.Perkara.SAKSI);
		if (saksi.isEmpty()) {
			l.keterangan(SketsaKota.t("perkara.saksi_kosong"), x0, y + 2, x1 - x0, gaya.teksSamar);
			return;
		}
		daftar.gambar(l, "saksi", x0, y, x1, y1, saksi.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar s = saksi.get(i);
			boolean dipanggil = s.b(KotaLembar.Perkara.DIPANGGIL);
			int bw = 56;
			int tx = SketsaKota.warga(lk, s, rx0, ry0, Math.min(rx1, rx0 + 150), ry1, sorot, pilih, gaya);
			if (dipanggil) {
				// the testimony as a speech bubble beside the card
				int bx0 = Math.min(rx1, rx0 + 154);
				lk.g.fill(bx0, ry0 + 2, rx1, ry1 - 2, Palette.argb(0xD8, 0xF4ECD4));
				lk.g.fill(bx0 - 3, ry0 + 9, bx0, ry0 + 12, Palette.argb(0xD8, 0xF4ECD4));
				lk.bungkus(s.teks(KotaLembar.Perkara.KESAKSIAN), bx0 + 3, ry0 + 4, rx1 - bx0 - 6, Palette.INK, 0.7F, ry1 - ry0 - 6);
			} else {
				tombolOp(lk, "panggil" + i, rx1 - bw - 2, ry0 + 5, bw, 14, SketsaKota.t("perkara.panggil"), true, null,
						LembarKunci.op(KotaLembar.Perkara.OP_PANGGIL, s.i(KotaLembar.WARGA, -1)));
			}
		}, null, null);
	}

	private void bukti(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(KotaLembar.Perkara.BUKTI);
		if (rows.isEmpty()) {
			kosong(l, SketsaKota.t("perkara.bukti_kosong"), x0, y0, x1, y1);
			return;
		}
		daftar.gambar(l, "bukti", x0, y0, x1, y1, rows.size(), 40, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar b = rows.get(i);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			String jenis = b.s(LembarKunci.JENIS);
			boolean telusur = b.b(KotaLembar.Perkara.DITELUSURI);
			if (KotaLembar.Perkara.BK_BARANG.equals(jenis) && !b.barang().isEmpty()) {
				lk.g.item(b.barang(), rx0 + 4, ry0 + 4);
			} else {
				IkonTempat.gambar(lk.g, KotaLembar.Perkara.BK_GOSIP.equals(jenis) ? IkonTempat.RANTAI : IkonTempat.GULUNGAN, rx0 + 4, ry0 + 4, 16, 0xFFFFFFFF);
			}
			int tx = rx0 + 24;
			int bw = 58;
			lk.teks(Brush.bold(b.teks(LembarKunci.NAMA)), tx, ry0 + 3, rx1 - tx - bw - 6, gaya.teks, 0.85F);
			lk.bungkus(b.teks(LembarKunci.TEKS), tx, ry0 + 13, rx1 - tx - bw - 6, telusur ? gaya.teks : gaya.teksSamar, 0.7F, 14);
			List<Lembar> rantai = b.daftar(KotaLembar.Perkara.RANTAI);
			if (!rantai.isEmpty()) {
				// the spreader chain, oldest last: A ← B ← C
				Component chain = Component.empty();
				for (int r = 0; r < rantai.size(); r++) {
					chain = chain.copy().append(r == 0 ? Component.empty() : Component.literal(" ← ")).append(rantai.get(r).teks(LembarKunci.NAMA));
				}
				lk.keterangan(SketsaKota.t("perkara.rantai", chain), tx, ry1 - 10, rx1 - tx - bw - 6, TempatWarna.PERINGATAN);
			}
			if (telusur) {
				IkonTempat.gambar(lk.g, IkonTempat.CENTANG, rx1 - 14, ry0 + 5, 10, 0xFFFFFFFF);
			} else {
				tombolOp(lk, "telusuri" + i, rx1 - bw - 3, ry0 + 5, bw, 14, SketsaKota.t("perkara.telusuri"), true, null,
						LembarKunci.op(KotaLembar.Perkara.OP_TELUSURI, b.id()));
			}
		}, null, null);
	}

	private void putusan(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
		int y = y0;
		Lembar amplop = lembar.sub(KotaLembar.Perkara.AMPLOP);
		if (!amplop.kosongSemua()) {
			// a red envelope on the table, danger colours
			int h = 30;
			l.g.fill(x0, y, x1, y + h, Palette.argb(0xC0, 0x5A0E0E));
			l.g.fill(x0, y, x1, y + 1, TempatWarna.BAHAYA);
			l.g.fill(x0 + 4, y + 5, x0 + 26, y + 25, 0xFFB3202A);
			l.g.fill(x0 + 4, y + 5, x0 + 26, y + 9, 0xFFD4A637);
			l.teks(SketsaKota.t("perkara.amplop", amplop.teks(KotaLembar.DARI), TeksTempat.angka(amplop.l(LembarKunci.HARGA, 0L))), x0 + 30, y + 4,
					x1 - x0 - 150, 0xFFFFE4D0, 0.8F);
			l.keterangan(SketsaKota.t("perkara.amplop_risiko"), x0 + 30, y + 16, x1 - x0 - 150, 0xFFE0A090);
			tombolYakin(l, "amplop_terima", x1 - 118, y + 8, 58, 14, SketsaKota.t("perkara.amplop_terima"), true, null, KotaLembar.Perkara.OP_AMPLOP_TERIMA);
			tombolOp(l, "amplop_tolak", x1 - 58, y + 8, 54, 14, SketsaKota.t("perkara.amplop_tolak"), true, null, KotaLembar.Perkara.OP_AMPLOP_TOLAK);
			y += h + 4;
		}
		Lembar hasil = lembar.sub(KotaLembar.Perkara.HASIL);
		if (!hasil.kosongSemua()) {
			PanelTempat.sumur(l.g, gaya, x0, y, x1, y + 34);
			IkonTempat.stempel(l.g, IkonTempat.Stempel.PERSEGI, x0 + 4, y + 4, 26, 26, Palette.argb(0xE0, Palette.SEAL & 0xFFFFFF));
			l.bungkus(hasil.teks(LembarKunci.TEKS), x0 + 34, y + 4, x1 - x0 - 38, gaya.teksSumur(), 0.8F, 28);
			y += 38;
		}
		y += subjudul(l, SketsaKota.t("perkara.putusan"), x0, y, x1);
		List<Lembar> rows = lembar.daftar(KotaLembar.Perkara.PUTUSAN);
		for (Lembar v : rows) {
			if (y + 30 > y1) {
				break;
			}
			int no = v.i(LembarKunci.NO, 0);
			PanelTempat.kartu(l.g, gaya, x0, y, x1, y + 28, 0.0F, false);
			Brush.text(l.g, font, Component.literal(Integer.toString(no)), x0 + 5, y + 4, Palette.SEAL, 1.3F, false);
			l.teks(v.teks(LembarKunci.TEKS), x0 + 18, y + 3, x1 - x0 - 92, gaya.teks, 0.85F);
			l.keterangan(SketsaKota.t("perkara.akibat", v.teks(KotaLembar.Perkara.AKIBAT)), x0 + 18, y + 15, x1 - x0 - 92, gaya.teksLemah);
			tombolYakin(l, "putus" + no, x1 - 72, y + 7, 68, 14, SketsaKota.t("perkara.putuskan"), v.b(LembarKunci.BISA), v.teksAtau(LembarKunci.ALASAN),
					LembarKunci.op(KotaLembar.Perkara.OP_PUTUSKAN, no));
			y += 31;
		}
	}
}
