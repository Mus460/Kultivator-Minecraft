package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.KeadaanKosong;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.PemilihJumlah;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Kavling;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kavling} (DESIGN 42.11.13, keys {@link KotaLembar.Kavling}) — one growth lot of a town, style giok. A new screen
 * class because nothing in the mod showed a town lot before (jadwal 0.1: the audit rated it "weak", unlike the sect build
 * pages that build a player's own hall). Tabs:
 * <ul>
 * <li>Kavling — lot, plan and style, the five cumulative stages as a row of seals (done jade, current pulsing), progress
 * inside the stage, crew and days left, what blocks it, the requirements, and the finished building as a slowly turning
 * pile of blocks (the server's silhouette of stage 5; hover pauses it, ←/→ turn it).</li>
 * <li>Bahan — this stage's five materials: collected / needed against the town stock, how many matching blocks are in the
 * bag, the town price; "Jual" sells blocks from the bag to the lot, "Sumbang" gives them (amount picker).</li>
 * <li>Pilih — office rank 4+: every plan of the lot's style with the need it answers (crowded, jobless, children, raids)
 * and its cost; the chosen one is sealed.</li>
 * <li>Dana — donors with their share; donating NB (steps of 10); a gold note when the player's name will be on the sign.</li>
 * </ul>
 */
public final class LayarKavling extends LayarTempatDasar {

	private final DaftarGulir daftar = new DaftarGulir();
	private final PemilihJumlah jumlah = new PemilihJumlah();
	private float sudut = 0.6F;
	private long nb = 20L;

	public LayarKavling(int npcId, CompoundTag lembar) {
		super(Kavling.LAYAR, npcId, lembar, SketsaKota.t("kavling.judul"), TempatWarna.Gaya.GIOK);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(new TabLentera.Tab(Kavling.TAB_KAVLING, SketsaKota.t("kavling.tab.kavling"), IkonTempat.TONGGAK, null),
				new TabLentera.Tab(Kavling.TAB_BAHAN, SketsaKota.t("kavling.tab.bahan"), LembarKunci.Ikon.BATU_TINGGI, null),
				new TabLentera.Tab(Kavling.TAB_PILIH, SketsaKota.t("kavling.tab.pilih"), IkonTempat.PETA, null),
				new TabLentera.Tab(Kavling.TAB_DANA, SketsaKota.t("kavling.tab.dana"), LembarKunci.Ikon.NB, null));
	}

	@Override
	protected KeadaanKosong.Gambar gambarKosong() {
		return KeadaanKosong.Gambar.PAPAN;
	}

	@Override
	protected void tabBerubah() {
		daftar.mulaiUlang(Util.getMillis());
		jumlah.set(1);
	}

	@Override
	protected boolean tombolLain(net.minecraft.client.input.KeyEvent event) {
		int key = event.key();
		if (Kavling.TAB_KAVLING.equals(tab) && (key == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT
				|| key == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT)) {
			sudut += key == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT ? 0.4F : -0.4F;
			return true;
		}
		return false;
	}

	@Override
	protected @Nullable Component petunjuk() {
		return Kavling.TAB_KAVLING.equals(tab) ? SketsaKota.t("kavling.petunjuk") : null;
	}

	@Override
	protected void isi(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar k = lembar.sub(Kavling.KAVLING);
		if (k.kosongSemua()) {
			kosong(l, SketsaKota.t("kavling.kosong"), x0, y0, x1, y1);
			return;
		}
		int y = SketsaKota.kota(l, lembar, x0, y0, x1, gaya);
		switch (tab) {
			case Kavling.TAB_BAHAN -> bahan(l, x0, y, x1, y1);
			case Kavling.TAB_PILIH -> pilih(l, x0, y, x1, y1);
			case Kavling.TAB_DANA -> dana(l, x0, y, x1, y1);
			default -> ringkas(l, k, x0, y, x1, y1);
		}
	}

	private void ringkas(Lukis l, Lembar k, int x0, int y0, int x1, int y1) {
		Kotak[] b = bagi(x0, y0, x1, y1);
		Kotak kiri = b[0];
		int y = kiri.y0();
		l.teks(Brush.bold(k.teks(LembarKunci.NAMA)), kiri.x0(), y, kiri.w(), gaya.teks, 1.0F);
		y += 11;
		l.keterangan(k.teks(Kavling.RENCANA), kiri.x0(), y, kiri.w(), gaya.teksLemah);
		y += 12;
		// five stage seals
		int tahap = k.i("tahap", 0);
		int total = Math.max(1, k.i(Kavling.TAHAP_TOTAL, 5));
		String keadaan = k.s(LembarKunci.KEADAAN);
		int sw = Math.min(26, (kiri.w() - (total - 1) * 3) / total);
		for (int s = 0; s < total; s++) {
			int sx = kiri.x0() + s * (sw + 3);
			boolean selesai = s < tahap;
			boolean kini = s == tahap && Kavling.KV_DIBANGUN.equals(keadaan);
			int warna = selesai ? 0xFF2E8963 : kini ? Palette.argb(Math.round(0x90 + 0x60 * l.denyut()), 0x67C99B) : Palette.argb(0x50, 0x000000);
			Brush.chamfer(l.g, sx, y, sx + sw, y + 16, 3, warna);
			Brush.centered(l.g, font, Component.literal(Integer.toString(s + 1)), sx + sw / 2.0F, y + 4, selesai || kini ? 0xFFF4FFF8 : gaya.teksSamar, 0.85F,
					false);
			if (l.di(sx, y, sx + sw, y + 16)) {
				l.tip(SketsaKota.t("kavling.tahap." + (s + 1)));
			}
		}
		y += 20;
		l.teks(SketsaKota.t("kavling.tahap_ini", Math.min(total, tahap + 1), total, k.teks(Kavling.TAHAP_NAMA)), kiri.x0(), y, kiri.w(), gaya.teks, 0.85F);
		y += 10;
		int kemajuan = k.i(Kavling.KEMAJUAN, 0);
		SketsaKota.batang(l, kiri.x0(), y, kiri.w(), 5, kemajuan / 100.0F, 0xFF67C99B, -1.0F);
		y += 8;
		int regu = k.i(Kavling.REGU, 0);
		int sisa = k.i(LembarKunci.SISA_HARI, -1);
		Component baris = regu > 0 ? SketsaKota.t("kavling.regu", regu, sisa < 0 ? SketsaKota.t("kavling.sisa_tak_tentu") : TeksTempat.hari(sisa))
				: SketsaKota.t("kavling.keadaan." + keadaan);
		l.keterangan(baris, kiri.x0(), y, kiri.w(), Kavling.KV_TIDAK_LAYAK.equals(keadaan) ? TempatWarna.BAHAYA : gaya.teksLemah);
		y += 10;
		Component halang = k.teksAtau(Kavling.TERHALANG);
		if (halang != null) {
			IkonTempat.gambar(l.g, LembarKunci.Ikon.GEMBOK, kiri.x0(), y - 1, 9, 0xFFFFFFFF);
			l.keterangan(halang, kiri.x0() + 11, y, kiri.w() - 11, TempatWarna.BAHAYA);
			y += 10;
		}
		y += 2;
		y += subjudul(l, SketsaKota.t("kavling.syarat"), kiri.x0(), y, kiri.x1());
		for (Lembar s : lembar.daftar(LembarKunci.SYARAT)) {
			int h = SketsaKota.syarat(l, s, kiri.x0(), y, kiri.x1(), kiri.y1() - 18);
			if (h == 0) {
				break;
			}
			y += h;
		}
		tombolOp(l, "tunjuk", kiri.x0(), kiri.y1() - 15, Math.min(90, kiri.w()), 14, SketsaKota.t("tunjuk"), true, null,
				LembarKunci.op(LembarKunci.OP_TANDAI, k.i(LembarKunci.X, 0), k.i(LembarKunci.Z, 0)));
		// the finished building, turning
		Kotak kanan = b[1];
		PanelTempat.sumur(l.g, gaya, kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1());
		Lembar siluet = lembar.sub(Kavling.SILUET);
		boolean diam = l.di(kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1());
		if (!diam) {
			sudut += l.detik * 0.35F;
		}
		if (siluet.kosongSemua()) {
			Brush.fitCentered(l.g, font, SketsaKota.t("kavling.siluet_kosong"), (kanan.x0() + kanan.x1()) / 2.0F, (kanan.y0() + kanan.y1()) / 2.0F - 4,
					kanan.w() - 8, gaya.teksSamar, 0.8F, 0.6F, false);
		} else {
			SketsaKota.siluet(l, siluet, kanan.x0() + 4, kanan.y0() + 12, kanan.x1() - 4, kanan.y1() - 4, sudut);
		}
		l.keterangan(SketsaKota.t("kavling.siluet"), kanan.x0() + 4, kanan.y0() + 3, kanan.w() - 8, gaya.teksSamar);
	}

	private void bahan(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Kavling.BAHAN);
		if (rows.isEmpty()) {
			kosong(l, SketsaKota.t("kavling.bahan_kosong"), x0, y0, x1, y1);
			return;
		}
		int bawah = y1 - 20;
		daftar.gambar(l, "bahan", x0, y0, x1, bawah, rows.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			int warna = warnaBahan(e.id());
			Brush.chamfer(lk.g, rx0 + 4, ry0 + 4, rx0 + 20, ry0 + 20, 3, warna);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 25, ry0 + 3, 90, gaya.teks, 0.85F);
			int ada = e.i(Kavling.TERKUMPUL, 0);
			int perlu = e.i(Kavling.PERLU, 0);
			int bx = rx0 + 25;
			int bw = Math.max(40, (rx1 - rx0) / 2 - 30);
			SketsaKota.batang(lk, bx, ry0 + 15, bw, 4, perlu <= 0 ? 1.0F : ada / (float) perlu, e.b(LembarKunci.OK) ? 0xFF2E8963 : TempatWarna.PERINGATAN, -1.0F);
			lk.keterangan(Component.literal(ada + " / " + perlu), bx + bw + 4, ry0 + 13, 50, gaya.teksLemah);
			int kx = rx1 - 4;
			lk.kanan(SketsaKota.t("kavling.di_tas", e.i(Kavling.TAS, 0)), kx, ry0 + 3, gaya.teksLemah, 0.75F);
			LabelHarga.gambar(lk, e.l(LembarKunci.HARGA, 0L), -1L, kx, ry0 + 13, true, 0.75F, false);
		}, null, i -> jumlah.set(1));
		int sel = daftar.pilihan();
		Lembar e = sel >= 0 && sel < rows.size() ? rows.get(sel) : null;
		if (e == null) {
			return;
		}
		int tas = e.i(Kavling.TAS, 0);
		int by = y1 - 16;
		int pw = jumlah.gambar(l, "bahan_n", x0, by, Math.max(1, tas));
		Component alasan = tas <= 0 ? SketsaKota.t("kavling.tas_kosong") : null;
		int w = (x1 - x0 - pw - 12) / 2;
		tombolOp(l, "jual", x0 + pw + 6, by - 1, w, 14, SketsaKota.t("kavling.jual", jumlah.nilai()), tas > 0, alasan,
				LembarKunci.op(Kavling.OP_JUAL, e.id(), jumlah.nilai()));
		tombolOp(l, "sumbang", x0 + pw + 10 + w, by - 1, w, 14, SketsaKota.t("kavling.sumbang", jumlah.nilai()), tas > 0, alasan,
				LembarKunci.op(Kavling.OP_SUMBANG, e.id(), jumlah.nilai()));
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

	private void pilih(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Kavling.PILIHAN);
		if (rows.isEmpty()) {
			kosong(l, SketsaKota.t("kavling.pilih_kosong"), x0, y0, x1, y1);
			return;
		}
		l.keterangan(SketsaKota.t("kavling.pilih_info"), x0, y0, x1 - x0, gaya.teksSamar);
		daftar.gambar(l, "pilih", x0, y0 + 11, x1, y1, rows.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			boolean dipilih = e.b(KotaLembar.PILIH);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih || dipilih);
			if (dipilih) {
				IkonTempat.stempel(lk.g, IkonTempat.Stempel.BULAT, rx0 + 3, ry0 + 3, 18, 18, Palette.argb(0xE0, Palette.SEAL & 0xFFFFFF));
			} else {
				IkonTempat.gambar(lk.g, IkonTempat.PETA, rx0 + 4, ry0 + 5, 16, 0xFFFFFFFF);
			}
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 25, ry0 + 3, rx1 - rx0 - 100, gaya.teks, 0.85F);
			lk.keterangan(e.teks(LembarKunci.TEKS), rx0 + 25, ry0 + 14, rx1 - rx0 - 100, gaya.teksLemah);
			LabelHarga.gambar(lk, e.l(LembarKunci.HARGA, 0L), -1L, rx1 - 5, ry0 + 4, true, 0.8F, false);
			if (dipilih) {
				lk.kanan(SketsaKota.t("kavling.terpilih"), rx1 - 5, ry0 + 15, TempatWarna.EMAS, 0.7F);
			}
		}, () -> {
			int i = daftar.pilihan();
			if (i >= 0 && i < rows.size() && rows.get(i).b(LembarKunci.BISA) && tulisBoleh()) {
				kirim(LembarKunci.op(Kavling.OP_RENCANA, rows.get(i).id()));
			}
		}, null);
	}

	private void dana(Lukis l, int x0, int y0, int x1, int y1) {
		long total = lembar.l(Kavling.DANA_TOTAL, 0L);
		long biaya = Math.max(1L, lembar.l(Kavling.BIAYA_TOTAL, 1L));
		l.teks(SketsaKota.t("kavling.dana_total", TeksTempat.angka(total), TeksTempat.angka(biaya)), x0, y0, x1 - x0, gaya.teks, 0.85F);
		SketsaKota.batang(l, x0, y0 + 11, x1 - x0, 5, total / (float) biaya, Palette.GOLD, 0.25F);
		int y = y0 + 20;
		if (lembar.b(Kavling.PAPAN_NAMA)) {
			IkonTempat.gambar(l.g, IkonTempat.BINTANG_PENUH, x0, y - 1, 10, 0xFFFFFFFF);
			l.keterangan(SketsaKota.t("kavling.papan_nama"), x0 + 12, y, x1 - x0 - 12, TempatWarna.EMAS);
			y += 11;
		}
		List<Lembar> rows = lembar.daftar(Kavling.DANA);
		int bawah = y1 - 20;
		if (rows.isEmpty()) {
			l.keterangan(SketsaKota.t("kavling.dana_kosong"), x0, y + 2, x1 - x0, gaya.teksSamar);
		} else {
			daftar.gambar(l, "dana", x0, y, x1, bawah, rows.size(), 16, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar e = rows.get(i);
				boolean anda = e.b(LembarKunci.ANDA);
				int persen = e.i(Kavling.PERSEN, 0);
				lk.g.fill(rx0, ry0 + 1, rx0 + Math.round((rx1 - rx0) * persen / 100.0F), ry1 - 1, Palette.argb(0x40, (anda ? Palette.GOLD : 0x67C99B) & 0xFFFFFF));
				lk.teks(e.teks(LembarKunci.NAMA), rx0 + 3, ry0 + 4, rx1 - rx0 - 90, anda ? TempatWarna.EMAS : gaya.teks, 0.8F);
				lk.kanan(Component.literal(TeksTempat.angka(e.l(LembarKunci.HARGA, 0L)) + " NB · " + persen + " %"), rx1 - 3, ry0 + 4, gaya.teksLemah, 0.75F);
			}, null, null);
		}
		int by = y1 - 16;
		tombol.gambar(l, "nb#kurang", x0, by, 16, 14, Component.literal("−"), nb > 10L, null, () -> nb = Math.max(10L, nb - 10L));
		tombol.gambar(l, "nb#tambah", x0 + 18, by, 16, 14, Component.literal("+"), true, null, () -> nb += 10L);
		boolean cukup = uang() < 0 || nb <= uang();
		tombolOp(l, "sumbang_nb", x0 + 38, by, x1 - x0 - 38, 14, SketsaKota.t("kavling.sumbang_nb", TeksTempat.angka(nb)), cukup,
				TeksTempat.k("harga.kurang", TeksTempat.angka(nb - Math.max(0L, uang()))), LembarKunci.op(Kavling.OP_SUMBANG_NB, nb));
	}
}
