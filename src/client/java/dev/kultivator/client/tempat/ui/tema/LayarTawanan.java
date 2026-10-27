package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Potret;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.screen.ui.Lembar;
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
 * on their way home. A Di Sini page on the single kit (43.8.3); in the narrow layout the cells take the area and Enter /
 * a second click shows the captive in their place (Esc back).
 */
public final class LayarTawanan extends TemaHalaman {

	private final DaftarGulir daftar = new DaftarGulir();
	private final Map<String, Component[]> kenal = new HashMap<>();
	private Component pulang;
	private long pulangSejak = -1L;
	private boolean rinciSempit;

	public LayarTawanan(int npcId, CompoundTag lembar) {
		super(Tawanan.LAYAR, npcId, lembar, "layar.tawanan", Palette.Gaya.BATU);
	}

	@Override
	protected List<TabBar.Tab> tabs() {
		return List.of();
	}

	@Override
	public boolean rinci() {
		return rinciSempit && tata == Tata.SEMPIT || super.rinci();
	}

	@Override
	public boolean mundur() {
		if (rinciSempit && tata == Tata.SEMPIT) {
			rinciSempit = false;
			return true;
		}
		return super.mundur();
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
	protected void lukisTema(Lukis l, int x0, int y0, int x1, int y1) {
		boolean sempit = tata == Tata.SEMPIT;
		int y = y0;
		if (pulang != null && l.kini - pulangSejak < 5000L) {
			y += pita(l, TemaUi.I_TANGGA, pulang, x0, y, x1, Palette.OK);
		}
		List<Lembar> semua = lembar.daftar(Tawanan.TAWANAN);
		y += strip(l, TemaUi.k(semua.isEmpty() ? "tawanan.ucap_kosong" : "tawanan.ucap"), x0, y, x1, sempit ? 30 : 32);
		int sisa = lembar.i(Tawanan.SISA, 3);
		// ransoms left today
		Component sisaT = TemaUi.k("tawanan.sisa");
		int sw = Math.min(x1 - x0 - 40, font.width(sisaT) + 4);
		l.teks(sisaT, x0, y + 1, sw, gaya.teksLemah);
		for (int i = 0; i < 3; i++) {
			Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.KUNCI, x0 + sw + i * 12, y, 10, i < sisa ? 0xFFFFFFFF : 0x50FFFFFF);
		}
		y += 13;
		int sel = daftar.pilihan();
		Lembar dipilih = sel >= 0 && sel < semua.size() ? semua.get(sel) : null;
		if (sempit && rinciSempit && dipilih != null) {
			rinci(l, new Kotak(x0, y, x1, y1), dipilih, sisa);
			return;
		}
		rinciSempit = false;
		Kotak[] k = sempit ? new Kotak[] {new Kotak(x0, y, x1, y1), null} : bagi(x0, y, x1, y1);
		if (semua.isEmpty()) {
			kosong(l, TemaUi.k("tawanan.kosong"), k[0].x0(), k[0].y0(), k[0].x1(), k[0].y1());
		} else {
			daftar.gambar(l, "sel", k[0].x0(), k[0].y0(), k[0].x1(), k[0].y1(), semua.size(), 30, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar t = semua.get(i);
				lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
				int ps = ry1 - ry0 - 4;
				Potret.gambar(lk, rx0 + 2, ry0 + 2, ps, t.i(LembarKunci.POTRET, -1), Glyphs.Atlas.HATI, true, 0.0F);
				jeruji(lk, rx0 + 2, ry0 + 2, rx0 + 2 + ps, ry0 + 2 + ps);
				int tx = rx0 + ps + 6;
				lk.teks(Brush.bold(t.teks(LembarKunci.NAMA)), tx, ry0 + 4, rx1 - tx - 4, gaya.teks);
				lk.samar(TemaUi.k("tawanan.asal_umur", t.teks(Tawanan.ASAL), t.i(Tawanan.UMUR, 0)), tx, ry0 + 15, rx1 - tx - 4);
			}, () -> rinciSempit = true, null);
		}
		if (k[1] != null) {
			rinci(l, k[1], dipilih, sisa);
		}
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
			l.kartu(k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
			tengah(l, TemaUi.k("tawanan.pilih"), k);
			return;
		}
		TemaUi.kandang(l.g, k.x0(), k.y0(), k.x1(), k.y1());
		l.g.fill(k.x0() + 6, k.y0() + 6, k.x1() - 6, k.y1() - 6, Palette.argb(0xD8, 0xD4D4CA));
		int x0 = k.x0() + 9;
		int x1 = k.x1() - 9;
		int y = k.y0() + 8;
		int by = k.y1() - 46;
		int ps = Math.min(56, Math.max(28, (k.h() - 80) / 2));
		Potret.gambar(l, x0, y, ps, t.i(LembarKunci.POTRET, -1), Glyphs.Atlas.HATI, true, 0.0F);
		jeruji(l, x0, y, x0 + ps, y + ps);
		int tx = x0 + ps + 6;
		int ink = gaya.teksSumur();
		l.teks(Brush.bold(t.teks(LembarKunci.NAMA)), tx, y + 1, x1 - tx, ink);
		l.teks(TemaUi.k("tawanan.dari", t.teks(Tawanan.ASAL)), tx, y + 12, x1 - tx, gaya.teksLemah);
		l.samar(TemaUi.k("tawanan.umur", t.i(Tawanan.UMUR, 0)), tx, y + 22, x1 - tx);
		y += ps + 4;
		Component cerita = t.teksAtau(Tawanan.CERITA);
		if (cerita != null && by - y >= 9) {
			l.bungkus(cerita, x0, y, x1 - x0, gaya.teksLemah, by - y - 1);
		}
		long harga = t.l(LembarKunci.HARGA, 0L);
		int pw = LabelHarga.gambar(l, harga, uang(), x1, by + 1, true, false);
		l.teks(TemaUi.k("tawanan.harga"), x0, by + 1, x1 - x0 - pw - 4, gaya.teksLemah);
		boolean batas = sisa <= 0;
		Component alasan = batas ? TemaUi.k("tawanan.batas") : !mampu(harga) ? kurang(harga) : null;
		tombolOp(l, "tebus", x0, by + 12, x1 - x0, 14, TemaUi.k("tawanan.tebus", Brush.angka(harga)), !batas && mampu(harga), alasan,
				LembarKunci.op(Tawanan.OP_TEBUS, t.id()));
		tombolOp(l, "darah", x0, by + 28, x1 - x0, 14, TemaUi.k("tawanan.tebusan_darah"), !batas, batas ? TemaUi.k("tawanan.batas") : null,
				LembarKunci.op(Tawanan.OP_TEBUSAN_DARAH, t.id()));
		if (l.di(x0, by + 28, x1, by + 42)) {
			l.tip(List.of(TemaUi.k("tawanan.tebusan_darah"), TemaUi.k("tawanan.tebusan_darah_tip")));
		}
	}
}
