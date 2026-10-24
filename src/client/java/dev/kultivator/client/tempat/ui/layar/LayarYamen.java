package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.CincinWaktu;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.KartuBarang;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Yamen;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * {@code yamen} (DESIGN 38.U.3.9, 38.B.5.2): the magistrate. The crime record as a bar 0-200 with the Dicurigai (50),
 * Buronan (100) and Buronan Berat (150) marks and the status seal; the fine with "Bayar denda" and "Serahkan diri" and
 * what each means; the confiscated goods to redeem. In a cell the screen shows only a big countdown.
 */
public final class LayarYamen extends LayarTempatDasar {

	private final DaftarGulir sitaan = new DaftarGulir();

	public LayarYamen(int npcId, CompoundTag lembar) {
		super(Yamen.LAYAR, npcId, lembar, TeksTempat.k("layar.yamen"), TempatWarna.Gaya.BATU);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of();
	}

	@Override
	protected void isi(Lukis l, int x0, int y0, int x1, int y1) {
		if (lembar.ada(Yamen.SEL)) {
			sel(l, x0, y0, x1, y1);
			return;
		}
		int poin = Math.max(0, Math.min(200, lembar.i(Yamen.POIN, 0)));
		String status = lembar.s(LembarKunci.STATUS, Yamen.ST_BERSIH);
		int warna = switch (status) {
			case Yamen.ST_DICURIGAI -> TempatWarna.PERINGATAN;
			case Yamen.ST_BURONAN -> 0xFFD06030;
			case Yamen.ST_BURONAN_BERAT -> TempatWarna.BAHAYA;
			default -> TempatWarna.OK;
		};
		// status seal
		int ss = 34;
		IkonTempat.stempel(l.g, IkonTempat.Stempel.BULAT, x1 - ss, y0, ss, ss, Palette.argb(0xE0, warna & 0xFFFFFF));
		Brush.fitCentered(l.g, font, Brush.bold(TeksTempat.k("yamen.status." + status)), x1 - ss / 2.0F, y0 + ss / 2.0F - 4, ss - 6, warna, 0.75F, 0.6F,
				false);
		l.teks(Brush.bold(TeksTempat.k("yamen.catatan_kejahatan")), x0, y0 + 2, x1 - x0 - ss - 6, gaya.teks);
		int by = y0 + 16;
		int bx1 = x1 - ss - 8;
		l.g.fill(x0, by, bx1, by + 7, Palette.argb(0x50, 0x000000));
		l.g.fillGradient(x0, by, x0 + Math.round((bx1 - x0) * poin / 200.0F), by + 7, warna, Palette.darken(warna, 0.3F));
		for (int mark : new int[] {50, 100, 150}) {
			int mx = x0 + Math.round((bx1 - x0) * mark / 200.0F);
			l.g.fill(mx, by - 2, mx + 1, by + 9, gaya.teks);
		}
		l.keterangan(TeksTempat.k("yamen.poin", poin), x0, by + 9, bx1 - x0, gaya.teksLemah);
		int y = y0 + ss + 6;
		long denda = lembar.l(Yamen.DENDA, 0L);
		Kotak[] k = bagi(x0, y, x1, y1);
		Kotak kiri = k[0];
		PanelTempat.kartu(l.g, gaya, kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1(), 0.0F, false);
		int ky = kiri.y0() + 4;
		ky += subjudul(l, TeksTempat.k("yamen.denda_judul"), kiri.x0() + 4, ky, kiri.x1() - 4);
		if (denda > 0) {
			LabelHarga.gambar(l, denda, uang(), kiri.x0() + 6, ky, false, 1.3F, false);
			ky += 16;
		} else {
			l.keterangan(TeksTempat.k("yamen.tanpa_denda"), kiri.x0() + 6, ky, kiri.w() - 12, gaya.teksSamar);
			ky += 12;
		}
		Component catatan = lembar.teksAtau(LembarKunci.CATATAN);
		if (catatan != null) {
			l.bungkus(catatan, kiri.x0() + 6, ky, kiri.w() - 12, gaya.teksLemah, 0.75F, kiri.y1() - 39 - ky);
		}
		boolean cukup = uang() < 0 || denda <= uang();
		tombolOp(l, "denda", kiri.x0() + 4, kiri.y1() - 36, kiri.w() - 8, 15, TeksTempat.k("yamen.bayar_denda", TeksTempat.angka(denda)), denda > 0 && cukup,
				denda <= 0 ? TeksTempat.k("yamen.tanpa_denda") : TeksTempat.k("harga.kurang", TeksTempat.angka(denda - Math.max(0L, uang()))), Yamen.OP_DENDA);
		tombolOp(l, "serah", kiri.x0() + 4, kiri.y1() - 19, kiri.w() - 8, 15, TeksTempat.k("yamen.serahkan_diri"), poin > 0, TeksTempat.k("yamen.bersih"),
				Yamen.OP_SERAH);
		// confiscated goods
		Kotak kanan = k[1];
		List<Lembar> rows = lembar.daftar(Yamen.SITAAN);
		int ry = kanan.y0() + subjudul(l, TeksTempat.k("yamen.sitaan"), kanan.x0(), kanan.y0(), kanan.x1());
		if (rows.isEmpty()) {
			l.keterangan(TeksTempat.k("yamen.sitaan_kosong"), kanan.x0(), ry + 2, kanan.w(), gaya.teksSamar);
			return;
		}
		long uang = uang();
		long muncul = diterima;
		sitaan.gambar(l, "sitaan", kanan.x0(), ry, kanan.x1(), kanan.y1() - 9, rows.size(), 22,
				(lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> KartuBarang.gambar(lk, rows.get(i), rx0, ry0, rx1, ry1, sorot, pilih, uang, muncul), () -> {
					int i = sitaan.pilihan();
					if (i >= 0 && i < rows.size() && tulisBoleh()) {
						kirim(LembarKunci.op(Yamen.OP_TEBUS, rows.get(i).id()));
					}
				}, null);
		l.keterangan(TeksTempat.k("yamen.tebus_petunjuk"), kanan.x0(), kanan.y1() - 7, kanan.w(), gaya.teksSamar);
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
		Component t = TeksTempat.waktu(left);
		Brush.centered(l.g, font, t, cx, cy - 6, 0xFFF4ECE0, 1.6F, true);
		Brush.centered(l.g, font, TeksTempat.k("yamen.dalam_sel"), cx, cy + r + 10, gaya.teks, 1.0F, false);
	}
}
