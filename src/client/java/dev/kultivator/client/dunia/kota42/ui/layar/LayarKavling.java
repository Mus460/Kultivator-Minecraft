package dev.kultivator.client.dunia.kota42.ui.layar;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihJumlah;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Kavling;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kavling} (DESIGN 42.11.13, keys {@link KotaLembar.Kavling}) — one growth lot of a town, style giok, on the single
 * kit. Four tabs:
 * <ul>
 * <li>Kavling — lot, plan and style, the five cumulative stages as a row of seals (done jade, current pulsing), progress
 * inside the stage, crew and days left, what blocks it, the requirements, "Tunjuk", and the finished building as a slowly
 * turning pile of blocks (hover pauses it, Left / Right turn it). A wide box puts the building beside the text, a narrow one
 * under it.</li>
 * <li>Bahan — this stage's five materials: collected / needed against the town stock, how many matching blocks are in the
 * bag, the town price; "Jual" sells blocks from the bag to the lot, "Sumbang" gives them (amount picker).</li>
 * <li>Pilih — office rank 4+: every plan of the lot's style with the need it answers and its cost (Enter chooses it).</li>
 * <li>Dana — donors with their share; donating NB (steps of 10, Shift 100); a gold note when the player's name will be on
 * the sign.</li>
 * </ul>
 */
public final class LayarKavling extends LayarKotaDasar {

	private final DaftarGulir dBahan = new DaftarGulir();
	private final DaftarGulir dPilih = new DaftarGulir();
	private final DaftarGulir dDana = new DaftarGulir();
	private final GulirIsi gulir = new GulirIsi();
	private final PemilihJumlah jumlah = new PemilihJumlah();
	private float sudut = 0.6F;
	private long nb = 20L;

	public LayarKavling(int npcId, CompoundTag lembar) {
		super(Kavling.LAYAR, npcId, lembar, SketsaKota.t("kavling.judul"), LembarKunci.GAYA_GIOK);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(Kavling.TAB_KAVLING, "kota.tab.kavling", Glyphs.Atlas.TONGGAK), tab(Kavling.TAB_BAHAN, "kota.tab.bahan", LembarKunci.Ikon.BATU_TINGGI),
				tab(Kavling.TAB_PILIH, "kota.tab.pilih", Glyphs.Atlas.PETA), tab(Kavling.TAB_DANA, "kota.tab.dana", LembarKunci.Ikon.NB));
	}

	@Override
	protected KeadaanKosong.Gambar kosongGambar() {
		return KeadaanKosong.Gambar.PAPAN;
	}

	@Override
	protected void tabBerubah() {
		long kini = Util.getMillis();
		dBahan.mulaiUlang(kini);
		dPilih.mulaiUlang(kini);
		dDana.mulaiUlang(kini);
		gulir.atas();
		jumlah.set(1);
	}

	@Override
	public String helpTopic() {
		return KotaLembar.TOPIK_TUMBUH;
	}

	@Override
	protected @Nullable GulirIsi gulirTab() {
		return Kavling.TAB_KAVLING.equals(tab) ? gulir : null;
	}

	@Override
	protected boolean tombolLain(KeyEvent event) {
		int key = event.key();
		if (Kavling.TAB_KAVLING.equals(tab) && (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT)) {
			sudut += key == InputConstants.KEY_RIGHT ? 0.4F : -0.4F;
			return true;
		}
		return super.tombolLain(event);
	}

	@Override
	protected @Nullable Component petunjuk() {
		return Kavling.TAB_KAVLING.equals(tab) ? SketsaKota.t("kavling.petunjuk") : null;
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar k = lembar.sub(Kavling.KAVLING);
		if (k.kosongSemua()) {
			kosong(l, SketsaKota.t("kavling.kosong"), x0, y0, x1, y1);
			return;
		}
		int y = KotaGambar.kota(l, lembar, x0, y0, x1);
		switch (tab) {
			case Kavling.TAB_BAHAN -> bahan(l, x0, y, x1, y1);
			case Kavling.TAB_PILIH -> pilih(l, x0, y, x1, y1);
			case Kavling.TAB_DANA -> dana(l, x0, y, x1, y1);
			default -> ringkas(l, k, x0, y, x1, y1);
		}
	}

	// =================================================================== Kavling

	private void ringkas(Lukis l, Lembar k, int x0, int y0, int x1, int y1) {
		boolean dua = x1 - x0 >= 300;
		int mid = x0 + (x1 - x0) * 52 / 100;
		int kiriX1 = dua ? mid - 6 : x1 - 6;
		int y = gulir.mulai(l, x0, y0, kiriX1, y1);
		int akhir = teksKavling(l, k, x0, y, kiriX1);
		if (dua) {
			gulir.selesai(l, akhir + 4);
			bangunan(l, mid + 2, y0, x1, y1);
			return;
		}
		akhir += 6;
		bangunan(l, x0, akhir, x1 - 6, akhir + 110);
		gulir.selesai(l, akhir + 114);
	}

	/** The text column: name, plan, seals, progress, crew, block, requirements, "Tunjuk". Returns the y under it. */
	private int teksKavling(Lukis l, Lembar k, int x0, int y0, int x1) {
		int y = y0;
		l.teks(Brush.bold(k.teks(LembarKunci.NAMA)), x0, y, x1 - x0, l.gaya.teks);
		y += 11;
		l.teks(k.teks(Kavling.RENCANA), x0, y, x1 - x0, l.gaya.teksLemah);
		y += 13;
		int tahap = k.i("tahap", 0);
		int total = Math.max(1, k.i(Kavling.TAHAP_TOTAL, 5));
		String keadaan = k.s(LembarKunci.KEADAAN);
		int sw = Math.min(28, (x1 - x0 - (total - 1) * 3) / total);
		for (int s = 0; s < total; s++) {
			int sx = x0 + s * (sw + 3);
			boolean selesai = s < tahap;
			boolean kini = s == tahap && Kavling.KV_DIBANGUN.equals(keadaan);
			int warna = selesai ? 0xFF2E8963 : kini ? Palette.argb(Math.round(0x90 + 0x60 * l.denyut()), 0x67C99B) : Palette.argb(0x50, 0x000000);
			Brush.chamfer(l.g, sx, y, sx + sw, y + 16, 3, warna);
			Brush.centered(l.g, l.font, Component.literal(Integer.toString(s + 1)), sx + sw / 2.0F, y + 4, selesai || kini ? 0xFFF4FFF8 : l.gaya.teksSamar, 1.0F,
					false);
			if (l.di(sx, y, sx + sw, y + 16)) {
				l.tip(SketsaKota.t("kavling.tahap." + (s + 1)));
			}
		}
		y += 21;
		l.teks(SketsaKota.t("kavling.tahap_ini", Math.min(total, tahap + 1), total, k.teks(Kavling.TAHAP_NAMA)), x0, y, x1 - x0, l.gaya.teks);
		y += 11;
		int kemajuan = k.i(Kavling.KEMAJUAN, 0);
		Bilah.gambar(l, x0, y, x1, kemajuan / 100.0F, 0xFF67C99B, new float[0]);
		y += 9;
		int regu = k.i(Kavling.REGU, 0);
		int sisa = k.i(LembarKunci.SISA_HARI, -1);
		Component baris = regu > 0 ? SketsaKota.t("kavling.regu", regu, sisa < 0 ? SketsaKota.t("kavling.sisa_tak_tentu") : Brush.hari(sisa))
				: SketsaKota.t("kavling.keadaan." + keadaan);
		l.teks(baris, x0, y, x1 - x0, Kavling.KV_TIDAK_LAYAK.equals(keadaan) ? Palette.BAHAYA : l.gaya.teksLemah);
		y += 11;
		Component halang = k.teksAtau(Kavling.TERHALANG);
		if (halang != null) {
			Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.GEMBOK, x0, y - 1, 10, 0xFFFFFFFF);
			y += Math.round(l.bungkus(halang, x0 + 12, y, x1 - x0 - 12, Palette.BAHAYA)) + 2;
		}
		y += 3;
		y += l.kepala(SketsaKota.t("kavling.syarat"), x0, y, x1) + 1;
		for (Lembar s : lembar.daftar(LembarKunci.SYARAT)) {
			y += KotaGambar.syarat(l, s, x0, y, x1, Integer.MAX_VALUE);
		}
		y += 4;
		tombolOp(l, "tunjuk", x0, y, Math.min(110, x1 - x0), TOMBOL_H, SketsaKota.t("tunjuk"), true, null,
				LembarKunci.op(LembarKunci.OP_TANDAI, k.i(LembarKunci.X, 0), k.i(LembarKunci.Z, 0)));
		return y + TOMBOL_H + 2;
	}

	/** The finished building, turning. */
	private void bangunan(Lukis l, int x0, int y0, int x1, int y1) {
		l.sumur(x0, y0, x1, y1);
		Lembar siluet = lembar.sub(Kavling.SILUET);
		boolean diam = l.di(x0, y0, x1, y1);
		if (!diam) {
			sudut += l.detik * 0.35F;
		}
		l.teks(SketsaKota.t("kavling.siluet"), x0 + 4, y0 + 3, x1 - x0 - 8, l.gaya.teksSumur());
		if (siluet.kosongSemua()) {
			l.tengah(SketsaKota.t("kavling.siluet_kosong"), (x0 + x1) / 2.0F, (y0 + y1) / 2.0F - 4, x1 - x0 - 8, Palette.INK_FAINT);
		} else {
			KotaGambar.siluet(l, siluet, x0 + 4, y0 + 14, x1 - 4, y1 - 4, sudut);
		}
	}

	// =================================================================== Bahan

	private void bahan(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Kavling.BAHAN);
		if (rows.isEmpty()) {
			kosong(l, SketsaKota.t("kavling.bahan_kosong"), x0, y0, x1, y1);
			return;
		}
		boolean dua = x1 - x0 < 240;
		int bh = dua ? 2 * (TOMBOL_H + 3) + 4 : TOMBOL_H + 7;
		int bawah = y1 - bh;
		dBahan.gambar(l, "bahan", x0, y0, x1, bawah - 2, rows.size(), 30, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			Brush.chamfer(lk.g, rx0 + 4, ry0 + 7, rx0 + 20, ry0 + 23, 3, warnaBahan(e.id()));
			int tengah = rx0 + (rx1 - rx0) * 55 / 100;
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 25, ry0 + 3, tengah - rx0 - 28, lk.gaya.teks);
			int ada = e.i(Kavling.TERKUMPUL, 0);
			int perlu = e.i(Kavling.PERLU, 0);
			Component hitung = Component.literal(ada + " / " + perlu);
			int hw = lk.font.width(hitung);
			Bilah.gambar(lk, rx0 + 25, ry0 + 18, tengah - hw - 6, perlu <= 0 ? 1.0F : ada / (float) perlu, e.b(LembarKunci.OK) ? 0xFF2E8963 : Palette.PERINGATAN,
					new float[0]);
			lk.teks(hitung, tengah - hw - 2, ry0 + 16, hw + 2, lk.gaya.teksLemah);
			lk.teks(SketsaKota.t("kavling.di_tas", e.i(Kavling.TAS, 0)), tengah + 2, ry0 + 3, rx1 - tengah - 6, lk.gaya.teksLemah);
			LabelHarga.gambar(lk, e.l(LembarKunci.HARGA, 0L), -1L, rx1 - 4, ry0 + 16, true, true);
		}, null, i -> jumlah.set(1));
		int sel = dBahan.pilihan();
		Lembar e = sel >= 0 && sel < rows.size() ? rows.get(sel) : null;
		if (e == null) {
			return;
		}
		int tas = e.i(Kavling.TAS, 0);
		int by = bawah + 2;
		int pw = jumlah.gambar(l, "bahan_n", x0, by, Math.max(1, tas));
		Component alasan = tas <= 0 ? SketsaKota.t("kavling.tas_kosong") : null;
		Component jual = SketsaKota.t("kavling.jual", jumlah.nilai());
		Component sumbang = SketsaKota.t("kavling.sumbang", jumlah.nilai());
		if (dua) {
			int w = x1 - x0 - pw - 4;
			tombolOp(l, "jual", x0 + pw + 4, by, Math.max(50, w), TOMBOL_H, jual, tas > 0, alasan, LembarKunci.op(Kavling.OP_JUAL, e.id(), jumlah.nilai()));
			tombolOp(l, "sumbang", x0, by + TOMBOL_H + 3, x1 - x0, TOMBOL_H, sumbang, tas > 0, alasan, LembarKunci.op(Kavling.OP_SUMBANG, e.id(), jumlah.nilai()));
		} else {
			int w = (x1 - x0 - pw - 8) / 2;
			tombolOp(l, "jual", x0 + pw + 4, by, w, TOMBOL_H, jual, tas > 0, alasan, LembarKunci.op(Kavling.OP_JUAL, e.id(), jumlah.nilai()));
			tombolOp(l, "sumbang", x0 + pw + 8 + w, by, w, TOMBOL_H, sumbang, tas > 0, alasan, LembarKunci.op(Kavling.OP_SUMBANG, e.id(), jumlah.nilai()));
		}
	}

	private static int warnaBahan(String id) {
		return switch (id) {
			case Kavling.BH_KAYU -> 0xFF9A6A3A;
			case Kavling.BH_BATU -> 0xFF8A8F8C;
			case Kavling.BH_GENTENG -> 0xFF3A4048;
			case Kavling.BH_KACA_KERTAS -> 0xFFE8E4D4;
			case Kavling.BH_KAIN -> 0xFFB04050;
			default -> 0xFF6A6A6A;
		};
	}

	// =================================================================== Pilih

	private void pilih(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Kavling.PILIHAN);
		if (rows.isEmpty()) {
			kosong(l, SketsaKota.t("kavling.pilih_kosong"), x0, y0, x1, y1);
			return;
		}
		l.teks(SketsaKota.t("kavling.pilih_info"), x0, y0, x1 - x0, l.gaya.teksSamar);
		dPilih.gambar(l, "pilih", x0, y0 + 12, x1, y1, rows.size(), 30, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			boolean dipilih = e.b(KotaLembar.PILIH);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih || dipilih);
			if (dipilih) {
				Glyphs.Atlas.stempel(lk.g, Glyphs.Atlas.Stempel.BULAT, rx0 + 3, ry0 + 5, 20, 20, Palette.argb(0xE0, Palette.SEAL & 0xFFFFFF));
			} else {
				Glyphs.Atlas.ikon(lk.g, Glyphs.Atlas.PETA, rx0 + 5, ry0 + 7, 16, 0xFFFFFFFF);
			}
			int kanan = Math.min(86, (rx1 - rx0) / 3);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 27, ry0 + 3, rx1 - rx0 - 31 - kanan, lk.gaya.teks);
			lk.teks(e.teks(LembarKunci.TEKS), rx0 + 27, ry0 + 16, rx1 - rx0 - 31 - kanan, lk.gaya.teksLemah);
			LabelHarga.gambar(lk, e.l(LembarKunci.HARGA, 0L), -1L, rx1 - 5, ry0 + 3, true, true);
			if (dipilih) {
				lk.kanan(SketsaKota.t("kavling.terpilih"), rx1 - 5, ry0 + 16, KotaGambar.EMAS);
			}
		}, () -> {
			int i = dPilih.pilihan();
			if (i >= 0 && i < rows.size() && rows.get(i).b(LembarKunci.BISA) && tulisBoleh()) {
				kirim(LembarKunci.op(Kavling.OP_RENCANA, rows.get(i).id()));
			}
		}, null);
	}

	// =================================================================== Dana

	private void dana(Lukis l, int x0, int y0, int x1, int y1) {
		long total = lembar.l(Kavling.DANA_TOTAL, 0L);
		long biaya = Math.max(1L, lembar.l(Kavling.BIAYA_TOTAL, 1L));
		l.teks(SketsaKota.t("kavling.dana_total", Brush.angka(total), Brush.angka(biaya)), x0, y0, x1 - x0, l.gaya.teks);
		Bilah.gambar(l, x0, y0 + 12, x1, total / (float) biaya, Palette.GOLD, new float[] {0.25F});
		int y = y0 + 22;
		if (lembar.b(Kavling.PAPAN_NAMA)) {
			Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.BINTANG_PENUH, x0, y - 1, 10, 0xFFFFFFFF);
			l.teks(SketsaKota.t("kavling.papan_nama"), x0 + 12, y, x1 - x0 - 12, KotaGambar.EMAS);
			y += 12;
		}
		boolean dua = x1 - x0 < 230;
		int bh = dua ? 2 * (TOMBOL_H + 3) + 4 : TOMBOL_H + 7;
		int bawah = y1 - bh;
		List<Lembar> rows = lembar.daftar(Kavling.DANA);
		if (rows.isEmpty()) {
			l.samar(SketsaKota.t("kavling.dana_kosong"), x0, y + 2, x1 - x0);
		} else {
			dDana.gambar(l, "dana", x0, y, x1, bawah - 2, rows.size(), 18, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar e = rows.get(i);
				boolean anda = e.b(LembarKunci.ANDA);
				int persen = e.i(Kavling.PERSEN, 0);
				lk.g.fill(rx0, ry0 + 1, rx0 + Math.round((rx1 - rx0) * persen / 100.0F), ry1 - 1, Palette.argb(0x40, (anda ? Palette.GOLD : 0x67C99B) & 0xFFFFFF));
				Component angka = Component.literal(Brush.angka(e.l(LembarKunci.HARGA, 0L)) + " NB · " + persen + " %");
				int aw = lk.font.width(angka);
				lk.teks(e.teks(LembarKunci.NAMA), rx0 + 4, ry0 + 5, rx1 - rx0 - aw - 14, anda ? KotaGambar.EMAS : lk.gaya.teks);
				lk.kanan(angka, rx1 - 4, ry0 + 5, lk.gaya.teksLemah);
			}, null, null);
		}
		int by = bawah + 2;
		int langkah = Minecraft.getInstance().hasShiftDown() ? 100 : 10;
		aksi(l, "nb#kurang", x0, by, 20, TOMBOL_H, Component.literal("−"), nb > 10L, null, () -> nb = Math.max(10L, nb - langkah));
		aksi(l, "nb#tambah", x0 + 22, by, 20, TOMBOL_H, Component.literal("+"), true, null, () -> nb += langkah);
		Component nbTeks = Component.literal(Brush.angka(nb) + " NB");
		boolean cukup = uang() < 0 || nb <= uang();
		Component label = SketsaKota.t("kavling.sumbang_nb", Brush.angka(nb));
		Component alasan = k("harga.kurang", Brush.angka(nb - Math.max(0L, uang())));
		if (dua) {
			l.teks(nbTeks, x0 + 46, by + 3, x1 - x0 - 46, l.gaya.teks);
			tombolOp(l, "sumbang_nb", x0, by + TOMBOL_H + 3, x1 - x0, TOMBOL_H, label, cukup, alasan, LembarKunci.op(Kavling.OP_SUMBANG_NB, nb));
		} else {
			tombolOp(l, "sumbang_nb", x0 + 46, by, x1 - x0 - 46, TOMBOL_H, label, cukup, alasan, LembarKunci.op(Kavling.OP_SUMBANG_NB, nb));
		}
	}
}
