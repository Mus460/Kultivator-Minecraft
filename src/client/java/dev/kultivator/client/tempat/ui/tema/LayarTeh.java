package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.CincinWaktu;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Teh;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * {@code tema_teh} (DESIGN 38.T.5.4): Paviliun Teh Awan, Lu Yuqing's tea house over the canal. The active tea (only the
 * strongest one works) is a steaming cup band on top with its countdown; the menu is the shared shop block with each
 * tea's effect and the band it needs; along the bottom the tea ceremony card: the clay pot pouring a thread of tea into
 * three cups with the steam curling, the price and "Upacara teh" (a 4 s scene in the world).
 */
public final class LayarTeh extends LayarTemaDasar {

	private final Toko toko = new Toko();

	public LayarTeh(int npcId, CompoundTag lembar) {
		super(Teh.LAYAR, npcId, lembar, "layar.teh", TempatWarna.Gaya.KAYU);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of();
	}

	@Override
	protected boolean spasi() {
		Lembar u = lembar.sub(Teh.UPACARA);
		if (u.b(LembarKunci.BISA) && mampu(u.l(LembarKunci.HARGA, 0L)) && tulisBoleh()) {
			kirim(Teh.OP_UPACARA);
			return true;
		}
		return false;
	}

	@Override
	protected Component petunjuk() {
		return lembar.ada(Teh.UPACARA) ? TemaUi.k("teh.kunci") : null;
	}

	@Override
	protected void gambar(Lukis l, int x0, int y0, int x1, int y1) {
		int y = y0;
		Lembar aktif = lembar.sub(Teh.AKTIF_TEH);
		if (aktif.ada(LembarKunci.NAMA)) {
			y += efekAktif(l, aktif, x0, y, x1);
		} else {
			y += strip(l, TemaUi.k("teh.ucap"), x0, y, x1, 32);
		}
		int upH = lembar.ada(Teh.UPACARA) ? 42 : 0;
		Kotak[] k = bagi(x0, y, x1, y1 - upH);
		int pita = lembar.i(LembarKunci.PITA, -1);
		toko(l, "menu", toko, lembar.daftar(Teh.MENU), k[0], k[1], Teh.OP_BELI, false, (lk, e, ex0, ey, ex1) -> {
			int h = 0;
			Component efek = e.teksAtau(Teh.EFEK);
			if (efek != null) {
				TemaUi.ikon(lk.g, TemaUi.I_CAWAN, ex0, ey, 10);
				h += Math.round(lk.bungkus(efek, ex0 + 12, ey + 1, ex1 - ex0 - 12, aksen(), 0.75F)) + 3;
			}
			int min = e.i(Teh.PITA_MIN, 0);
			if (min > 0) {
				boolean cukup = pita < 0 || pita >= min;
				lk.keterangan(TemaUi.k("teh.pita_min", min), ex0, ey + h, ex1 - ex0, cukup ? gaya.teksSamar : 0xFFFF8A7A);
				h += 10;
			}
			if (aktif.ada(LembarKunci.NAMA)) {
				lk.keterangan(TemaUi.k("teh.terkuat"), ex0, ey + h, ex1 - ex0, gaya.teksSamar);
				h += 10;
			}
			return h;
		}, null, TemaUi.k("teh.kosong"));
		if (upH > 0) {
			upacara(l, x0, y1 - upH + 4, x1, y1);
		}
	}

	private int efekAktif(Lukis l, Lembar aktif, int x0, int y, int x1) {
		int h = 26;
		PanelTempat.kartu(l.g, gaya, x0, y, x1, y + h, 0.0F, false);
		l.g.fillGradient(x0 + 1, y + 1, x1 - 1, y + h - 1, 0x307FC8B0, 0x00000000);
		cangkir(l, x0 + 6, y + 6, 1.0F);
		l.teks(Brush.bold(TemaUi.k("teh.aktif", aktif.teks(LembarKunci.NAMA))), x0 + 28, y + 4, x1 - x0 - 70, aksen(), 0.9F);
		l.keterangan(TemaUi.k("teh.aktif_jelas"), x0 + 28, y + 15, x1 - x0 - 70, gaya.teksLemah);
		CincinWaktu.gambar(l, x1 - 16, y + h / 2.0F, 10.0F, aktif.l(LembarKunci.SISA_TICK, 0L), 6000L, diterima, CincinWaktu.KIRI);
		return h + 4;
	}

	/** A celadon cup with three curls of steam rising. */
	private void cangkir(Lukis l, int x, int y, float skala) {
		int w = Math.round(16 * skala);
		int h = Math.round(10 * skala);
		int cy = y + Math.round(6 * skala);
		for (int s = 0; s < 3; s++) {
			float t = ((l.kini / 40L + s * 23L) % 60L) / 60.0F;
			float sx = x + w * (0.3F + 0.2F * s) + (float) Math.sin(t * 6.0F + s) * 1.5F;
			Brush.dot(l.g, sx, cy - t * 9.0F * skala, 1.2F * skala, Palette.argb(Math.round(0x90 * (1.0F - t)), 0xFFFFFF));
		}
		Brush.rect(l.g, x, cy, x + w, cy + h, 0xFFA8D0B8);
		Brush.rect(l.g, x + 1, cy + h, x + w - 1, cy + h + 1, 0xFF6A9A80);
		Brush.rect(l.g, x + 1, cy, x + w - 1, cy + 2, 0xFF7A5A20);
		Brush.rect(l.g, x + 2, cy + 3, x + 4, cy + h - 2, 0x60FFFFFF);
	}

	/** The ceremony card: the pot pours a thread of tea into three cups in turn. */
	private void upacara(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar u = lembar.sub(Teh.UPACARA);
		PanelTempat.kartu(l.g, gaya, x0, y0, x1, y1, 0.0F, false);
		int sx = x0 + 4;
		int sy = y0 + 3;
		int sw = 86;
		l.g.fillGradient(sx, sy, sx + sw, y1 - 3, 0xFF3A2A1C, 0xFF24180E);
		Brush.rect(l.g, sx, y1 - 9, sx + sw, y1 - 3, 0xFF5A3A20);
		long fase = (l.kini / 1300L) % 3L;
		for (int c = 0; c < 3; c++) {
			cangkir(l, sx + 30 + c * 18, y1 - 21, 0.8F);
		}
		// the pot tilting over the current cup
		float tuang = (float) ((Math.sin(l.kini / 210.0D) + 1.0D) / 2.0D);
		int px = sx + 12 + (int) fase * 18;
		int py = sy + 3;
		Brush.disc(l.g, px + 8, py + 8, 7.0F, 0xFF8A4A2A);
		Brush.rect(l.g, px + 5, py, px + 11, py + 2, 0xFF6A3A1E);
		Brush.line(l.g, px + 14, py + 7, px + 19, py + 5 + tuang * 2, 2.0F, 0xFF8A4A2A);
		Brush.line(l.g, px + 19, py + 6 + tuang * 2, px + 20, y1 - 17, 1.0F, Palette.argb(Math.round(0x60 + 0x90 * tuang), 0xC89040));
		int tx = sx + sw + 6;
		l.teks(Brush.bold(TemaUi.k("teh.upacara_judul")), tx, y0 + 4, x1 - tx - 100, aksen(), 0.9F);
		l.keterangan(TemaUi.k("teh.upacara_jelas"), tx, y0 + 15, x1 - tx - 100, gaya.teksLemah);
		long harga = u.l(LembarKunci.HARGA, 0L);
		LabelHarga.gambar(l, harga, uang(), tx, y0 + 25, false, 0.85F, true);
		boolean bisa = u.b(LembarKunci.BISA) && mampu(harga);
		Component alasan = !u.b(LembarKunci.BISA) ? u.teksAtau(LembarKunci.ALASAN) != null ? u.teks(LembarKunci.ALASAN) : TemaUi.k("teh.upacara_tidak")
				: !mampu(harga) ? kurang(harga) : null;
		tombolOp(l, "upacara", x1 - 96, y0 + (y1 - y0) / 2 - 8, 90, 15, TemaUi.k("teh.upacara", TeksTempat.angka(harga)), bisa, alasan,
				Teh.OP_UPACARA);
	}
}
