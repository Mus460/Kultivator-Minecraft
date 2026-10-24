package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.Potret;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Tawanan;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code tema_tawanan} (DESIGN 38.T.7.6): the Kandang Tawanan under the arena. The only things the player can do here are
 * buy a captive free ("Tebus") or win them free in the arena ("Tebusan Darah"); a captive is never owned, employed or sold
 * (38.T.0 butir 5), and every text says they go home. Left the cells: each captive behind iron bars with the live portrait,
 * name, home village and age. Right the chosen captive: the portrait large behind the bars, their own story, the ransom
 * and the two buttons, with the ransoms left today (three). When a captive leaves the cells a short note says they are
 * on their way home.
 */
public final class LayarTawanan extends LayarTemaDasar {

	private final DaftarGulir daftar = new DaftarGulir();
	private final Map<String, Component[]> kenal = new HashMap<>();
	private Component pulang;
	private long pulangSejak = -1L;

	public LayarTawanan(int npcId, CompoundTag lembar) {
		super(Tawanan.LAYAR, npcId, lembar, "layar.tawanan", TempatWarna.Gaya.BATU);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of();
	}

	@Override
	protected void lembarBaru(boolean buka) {
		List<Lembar> semua = lembar.daftar(Tawanan.TAWANAN);
		if (!buka) {
			for (Map.Entry<String, Component[]> e : kenal.entrySet()) {
				boolean masih = false;
				for (Lembar t : semua) {
					masih |= t.id().equals(e.getKey());
				}
				if (!masih) {
					pulang = TemaUi.k("tawanan.pulang", e.getValue()[0], e.getValue()[1]);
					pulangSejak = Util.getMillis();
				}
			}
		}
		kenal.clear();
		for (Lembar t : semua) {
			kenal.put(t.id(), new Component[] {t.teks(LembarKunci.NAMA), t.teks(Tawanan.ASAL)});
		}
	}

	@Override
	protected void gambar(Lukis l, int x0, int y0, int x1, int y1) {
		int y = y0;
		if (pulang != null && l.kini - pulangSejak < 5000L) {
			y += pita(l, TemaUi.I_TANGGA, pulang, x0, y, x1, TempatWarna.OK);
		}
		List<Lembar> semua = lembar.daftar(Tawanan.TAWANAN);
		y += strip(l, TemaUi.k(semua.isEmpty() ? "tawanan.ucap_kosong" : "tawanan.ucap"), x0, y, x1, 32);
		int sisa = lembar.i(Tawanan.SISA, 3);
		// ransoms left today
		l.keterangan(TemaUi.k("tawanan.sisa"), x0, y + 1, 110, gaya.teksLemah);
		for (int i = 0; i < 3; i++) {
			IkonTempat.gambar(l.g, IkonTempat.KUNCI, x0 + 112 + i * 12, y - 1, 10, i < sisa ? 0xFFFFFFFF : 0x50FFFFFF);
		}
		y += 12;
		Kotak[] k = bagi(x0, y, x1, y1);
		if (semua.isEmpty()) {
			kosong(l, TemaUi.k("tawanan.kosong"), k[0].x0(), k[0].y0(), k[0].x1(), k[0].y1());
		} else {
			daftar.gambar(l, "sel", k[0].x0(), k[0].y0(), k[0].x1(), k[0].y1(), semua.size(), 30, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar t = semua.get(i);
				PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
				int ps = ry1 - ry0 - 4;
				Potret.gambar(lk, rx0 + 2, ry0 + 2, ps, t.i(LembarKunci.POTRET, -1), IkonTempat.HATI, true, 0.0F);
				jeruji(lk, rx0 + 2, ry0 + 2, rx0 + 2 + ps, ry0 + 2 + ps);
				int tx = rx0 + ps + 6;
				lk.teks(Brush.bold(t.teks(LembarKunci.NAMA)), tx, ry0 + 4, rx1 - tx - 4, gaya.teks, 0.85F);
				lk.keterangan(TemaUi.k("tawanan.asal_umur", t.teks(Tawanan.ASAL), t.i(Tawanan.UMUR, 0)), tx, ry0 + 15, rx1 - tx - 4, gaya.teksSamar);
			}, null, null);
		}
		int sel = daftar.pilihan();
		rinci(l, k[1], sel >= 0 && sel < semua.size() ? semua.get(sel) : null, sisa);
	}

	/** Iron bars over a portrait. */
	private static void jeruji(Lukis l, int x0, int y0, int x1, int y1) {
		int n = Math.max(3, (x1 - x0) / 7);
		for (int i = 1; i < n; i++) {
			int bx = x0 + (x1 - x0) * i / n;
			l.g.fill(bx - 1, y0, bx + 1, y1, 0xD03A3A40);
			l.g.fill(bx - 1, y0, bx, y1, 0x60A0A0A8);
		}
		l.g.fill(x0, y0 + (y1 - y0) / 3, x1, y0 + (y1 - y0) / 3 + 1, 0xC03A3A40);
	}

	private void rinci(Lukis l, Kotak k, Lembar t, int sisa) {
		if (t == null) {
			PanelTempat.kartu(l.g, gaya, k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
			tengah(l, TemaUi.k("tawanan.pilih"), k);
			return;
		}
		TemaUi.kandang(l.g, k.x0(), k.y0(), k.x1(), k.y1());
		l.g.fill(k.x0() + 6, k.y0() + 6, k.x1() - 6, k.y1() - 6, Palette.argb(0xD8, 0xD4D4CA));
		int x0 = k.x0() + 9;
		int x1 = k.x1() - 9;
		int y = k.y0() + 8;
		int ps = Math.min(56, Math.max(28, (k.h() - 80) / 2));
		Potret.gambar(l, x0, y, ps, t.i(LembarKunci.POTRET, -1), IkonTempat.HATI, true, 0.0F);
		jeruji(l, x0, y, x0 + ps, y + ps);
		int tx = x0 + ps + 6;
		l.teks(Brush.bold(t.teks(LembarKunci.NAMA)), tx, y + 2, x1 - tx, gaya.teks, 1.0F);
		l.keterangan(TemaUi.k("tawanan.dari", t.teks(Tawanan.ASAL)), tx, y + 14, x1 - tx, gaya.teksLemah);
		l.keterangan(TemaUi.k("tawanan.umur", t.i(Tawanan.UMUR, 0)), tx, y + 24, x1 - tx, gaya.teksSamar);
		y += ps + 5;
		Component cerita = t.teksAtau(Tawanan.CERITA);
		if (cerita != null) {
			l.klip(x0, y, x1, k.y1() - 48);
			l.bungkus(cerita, x0, y, x1 - x0, gaya.teksLemah, 0.72F);
			l.lepasKlip();
		}
		long harga = t.l(LembarKunci.HARGA, 0L);
		int by = k.y1() - 44;
		l.keterangan(TemaUi.k("tawanan.harga"), x0, by + 2, 60, gaya.teksLemah);
		LabelHarga.gambar(l, harga, uang(), x1, by + 1, true, 0.9F, false);
		boolean batas = sisa <= 0;
		Component alasan = batas ? TemaUi.k("tawanan.batas") : !mampu(harga) ? kurang(harga) : null;
		tombolOp(l, "tebus", x0, by + 12, x1 - x0, 14, TemaUi.k("tawanan.tebus", TeksTempat.angka(harga)), !batas && mampu(harga), alasan,
				LembarKunci.op(Tawanan.OP_TEBUS, t.id()));
		tombolOp(l, "darah", x0, by + 28, x1 - x0, 14, TemaUi.k("tawanan.tebusan_darah"), !batas, batas ? TemaUi.k("tawanan.batas") : null,
				LembarKunci.op(Tawanan.OP_TEBUSAN_DARAH, t.id()));
		if (l.di(x0, by + 28, x1, by + 42)) {
			l.tip(List.of(TemaUi.k("tawanan.tebusan_darah"), TemaUi.k("tawanan.tebusan_darah_tip")));
		}
	}
}
