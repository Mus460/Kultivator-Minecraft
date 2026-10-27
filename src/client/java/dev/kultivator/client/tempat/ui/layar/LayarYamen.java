package dev.kultivator.client.tempat.ui.layar;

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
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
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
 * Both are drawn with the single kit at text 1.0. The DESIGN 42.11.9 office and hearing sheets ({@code jabatan},
 * {@code perkara}) are pages of their own on the single kit, {@code client/dunia/kota42/ui/layar/LayarJabatan} and
 * {@code LayarPerkara} (D-42U).
 */
public final class LayarYamen extends LayarInti {

	/** The record thresholds of 41.6.11 ({@code Aturan.TEGURAN..USIR}). */
	private static final int[] AMBANG = {10, 20, 35, 50, 60};

	private final DaftarGulir sitaan = new DaftarGulir();
	private final DaftarGulir catatan = new DaftarGulir();

	public LayarYamen(int npcId, CompoundTag lembar) {
		this(Yamen.LAYAR, npcId, lembar);
	}

	/** The court for the 38 {@code yamen} id or the 41 {@code gabung_hukum} hearing. */
	public LayarYamen(String layar, int npcId, CompoundTag lembar) {
		super(layar, npcId, lembar, judulMode(layar), LembarKunci.GAYA_BATU);
	}

	/** The Balai Hukum hearing ({@code gabung_hukum}; G64: the court's hearing mode, was {@code LayarHukum}). */
	public static LayarYamen hukum(int npcId, CompoundTag lembar) {
		return new LayarYamen(Hukum.LAYAR, npcId, lembar);
	}

	private static Component judulMode(String layar) {
		return switch (layar) {
			case Hukum.LAYAR -> LayarGabung2.u("hukum.judul");
			default -> k("layar.yamen");
		};
	}

	private boolean hukum() {
		return Hukum.LAYAR.equals(layar);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of();
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
	protected void setelahLembar(boolean buka) {
		if (hukum() && buka) {
			catatan.mulaiUlang(Util.getMillis());
		}
	}

	@Override
	protected @Nullable Component petunjuk() {
		return hukum() && lembar.ada(Hukum.SOAL) ? LayarGabung2.u("hukum.petunjuk_soal") : null;
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		if (hukum()) {
			hukum(l, x0, y0, x1, y1);
		} else {
			yamen(l, x0, y0, x1, y1);
		}
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
}
