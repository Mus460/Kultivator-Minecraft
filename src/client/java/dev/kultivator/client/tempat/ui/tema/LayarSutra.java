package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanTerkunci;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Sutra;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code tema_sutra} (DESIGN 38.T.5.4): Rumah Sutra Su of the Kota Air.
 * <ul>
 * <li><b>Toko</b>: bolts and garments of the house (the kit's one shop composition, amount picker).</li>
 * <li><b>Jubah</b>: the robe order. Dye swatches (the sheet's colours), the four embroidered motifs, and the robe itself on
 * its lacquer stand, swaying in the shop's draught and re-dyed live as the player picks; "Pesan jubah" sends colour and
 * motif. A running order shows its own colours, the days left and, when ready, "Ambil jubah".</li>
 * <li><b>Tenun</b>: Kain Xiu Niang's loom, once a day: the loom with the shuttle running and the reward, opening the
 * weaving rhythm game ({@code tema_irama}).</li>
 * </ul>
 * A Di Sini page on the single kit (43.8.3); in the narrow layout the robe stand and the loom are a slim column on the
 * left so the choices keep their full height.
 */
public final class LayarSutra extends TemaHalaman {

	private final Toko toko = new Toko();
	private int warnaPilih;
	private String motifPilih = "";
	private long ubahSejak = -1L;

	public LayarSutra(int npcId, CompoundTag lembar) {
		super(Sutra.LAYAR, npcId, lembar, "layar.sutra", TempatWarna.Gaya.KAIN);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(tabTema(Sutra.TAB_TOKO, "sutra.tab.toko", LembarKunci.Ikon.TIMBANGAN),
				tabTema(Sutra.TAB_JUBAH, "sutra.tab.jubah", LembarKunci.Ikon.ZIRAH),
				tabTema(Sutra.TAB_TENUN, "sutra.tab.tenun", Glyphs.Atlas.BINTANG_PENUH));
	}

	@Override
	protected boolean tabAda(String id) {
		return switch (id) {
			case Sutra.TAB_JUBAH -> lembar.ada(Sutra.JUBAH);
			case Sutra.TAB_TENUN -> lembar.ada(Sutra.TENUN);
			default -> true;
		};
	}

	@Override
	protected Component petunjuk() {
		return Sutra.TAB_TOKO.equals(tab) ? tt("kunci.jumlah") : Sutra.TAB_JUBAH.equals(tab) ? TemaUi.k("sutra.kunci_jubah") : null;
	}

	@Override
	protected void tabGanti() {
		toko.mulaiUlang();
	}

	@Override
	protected void lembarBaru(boolean buka) {
		Lembar j = lembar.sub(Sutra.JUBAH);
		int[] warna = j.ia(Sutra.WARNA);
		List<String> motif = j.sl(Sutra.MOTIF);
		warnaPilih = Math.max(0, Math.min(warnaPilih, Math.max(0, warna.length - 1)));
		if (buka || (!motifPilih.isEmpty() && !motif.contains(motifPilih))) {
			motifPilih = motif.isEmpty() ? "" : motif.get(0);
		}
	}

	/** K3 under Di Sini: on the robe tab the number keys pick the dye. */
	@Override
	public boolean angka(int n) {
		if (Sutra.TAB_JUBAH.equals(tab)) {
			int[] warna = lembar.sub(Sutra.JUBAH).ia(Sutra.WARNA);
			if (n >= 1 && n <= warna.length) {
				pilihWarna(n - 1);
				return true;
			}
		}
		return super.angka(n);
	}

	private void pilihWarna(int i) {
		if (i != warnaPilih) {
			warnaPilih = i;
			ubahSejak = Util.getMillis();
			UiSound.toggle(true);
		}
	}

	@Override
	protected void lukisTema(Lukis l, int x0, int y0, int x1, int y1) {
		switch (tab) {
			case Sutra.TAB_JUBAH -> jubah(l, x0, y0, x1, y1);
			case Sutra.TAB_TENUN -> tenun(l, x0, y0, x1, y1);
			default -> {
				int y = y0 + strip(l, TemaUi.k("sutra.ucap"), x0, y0, x1, tata == Tata.SEMPIT ? 30 : 32);
				Kotak[] k = bagi(x0, y, x1, y1);
				toko(l, "toko", toko, lembar.daftar(Sutra.TOKO), k[0], k[1], Sutra.OP_BELI, true, null, null, TemaUi.k("sutra.toko_kosong"));
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ robe order

	private static int rgb(int warna) {
		return 0xFF000000 | (warna & 0xFFFFFF);
	}

	/** Picture | detail: the usual split, or in the narrow layout a slim picture column on the left. */
	private Kotak[] bagiGambar(int x0, int y0, int x1, int y1) {
		if (tata != Tata.SEMPIT) {
			return bagi(x0, y0, x1, y1);
		}
		int kol = Math.min(72, (x1 - x0) / 4);
		return new Kotak[] {new Kotak(x0, y0, x0 + kol, y1), new Kotak(x0 + kol + 4, y0, x1, y1)};
	}

	private void jubah(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar j = lembar.sub(Sutra.JUBAH);
		Lembar pesanan = j.sub(Sutra.PESANAN);
		boolean adaPesanan = pesanan.ada(LembarKunci.SISA_HARI) || pesanan.ada(Sutra.SIAP);
		int[] warna = j.ia(Sutra.WARNA);
		List<String> motif = j.sl(Sutra.MOTIF);
		Kotak[] k = bagiGambar(x0, y0, x1, y1);
		// the stand
		Kotak kiri = k[0];
		l.sumur(kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1());
		l.g.fillGradient(kiri.x0() + 1, kiri.y0() + 1, kiri.x1() - 1, kiri.y1() - 1, 0xFF3A2030, 0xFF1A0C16);
		for (int s = 0; s < 6; s++) {
			int lx = kiri.x0() + 6 + s * (kiri.w() - 12) / 5;
			l.g.fill(lx, kiri.y0() + 1, lx + 1, kiri.y1() - 1, 0x18F7DC7E);
		}
		int dye = adaPesanan ? rgb(pesanan.i(Sutra.WARNA, 0xC84A3A)) : warna.length == 0 ? 0xFFC84A3A : rgb(warna[warnaPilih]);
		String m = adaPesanan ? pesanan.s(Sutra.MOTIF) : motifPilih;
		float skala = Math.max(0.75F, Math.min(3.0F, Math.min((kiri.h() - 22) / 64.0F, (kiri.w() - 8) / 32.0F)));
		int rw = Math.round(32 * skala);
		int rh = Math.round(64 * skala);
		int rx = (kiri.x0() + kiri.x1()) / 2 - rw / 2;
		int ry = kiri.y0() + 8;
		float ayun = (float) Math.sin(l.kini / 900.0D) * 1.2F;
		float celup = ubahSejak >= 0L ? Math.min(1.0F, (l.kini - ubahSejak) / 350.0F) : 1.0F;
		TemaUi.cahaya(l, rx + rw / 2, ry + rh / 2, Math.round(rw * 0.9F), Palette.argb(0x50, dye & 0xFFFFFF));
		l.g.fill(rx - 4, ry - 2, rx + rw + 4, ry, 0xFF6A4020);
		l.g.fill(rx + rw / 2 - 1, ry + rh, rx + rw / 2 + 1, kiri.y1() - 8, 0xFF6A4020);
		l.g.fill(rx + rw / 2 - 8, kiri.y1() - 9, rx + rw / 2 + 8, kiri.y1() - 6, 0xFF4A2A14);
		l.g.pose().pushMatrix();
		l.g.pose().translate(ayun, 0.0F);
		TemaUi.jubah(l.g, rx, ry, skala, Palette.mix(0xFFF4ECD4, dye, celup), m);
		l.g.pose().popMatrix();
		if (kiri.w() > rw + 40) {
			TemaUi.labelJubah(l.g, rx + rw + 2, ry + 4, Math.min(1.5F, skala * 0.7F));
		}
		// right: order or choices
		Kotak d = k[1];
		l.kartu(d.x0(), d.y0(), d.x1(), d.y1(), 0.0F, false);
		int dx0 = d.x0() + 5;
		int dx1 = d.x1() - 5;
		int y = d.y0() + 4;
		int by = d.y1() - 19;
		if (adaPesanan) {
			y += subjudul(l, TemaUi.k("sutra.pesananmu"), dx0, y, dx1);
			boolean siap = pesanan.b(Sutra.SIAP);
			Brush.disc(l.g, dx0 + 6, y + 6, 5.5F, dye);
			Brush.ring(l.g, dx0 + 6, y + 6, 4.5F, 5.5F, Palette.GOLD);
			l.teks(TemaUi.k("sutra.motif." + (m.isEmpty() ? "polos" : m)), dx0 + 16, y + 2, dx1 - dx0 - 16, gaya.teks);
			y += 16;
			if (siap) {
				l.bungkus(TemaUi.k("sutra.siap"), dx0, y, dx1 - dx0, l.gaya.naik(), Math.max(9, by - y - 2));
			} else {
				barisNilai(l, TemaUi.k("sutra.sisa"), Brush.hari(pesanan.i(LembarKunci.SISA_HARI, 0)), dx0, y, dx1);
				y += 12;
				if (by - y >= 11) {
					l.bungkus(TemaUi.k("sutra.dijahit"), dx0, y, dx1 - dx0, gaya.teksSamar, by - y - 2);
				}
			}
			tombolOp(l, "ambil", dx0, by, dx1 - dx0, 15, TemaUi.k(siap ? "sutra.ambil" : "sutra.belum"), siap,
					siap ? null : TemaUi.k("sutra.tunggu"), Sutra.OP_AMBIL_JUBAH);
			return;
		}
		y += subjudul(l, TemaUi.k("sutra.warna"), dx0, y, dx1);
		int sw = 14;
		int perBaris = Math.max(1, (dx1 - dx0) / (sw + 3));
		for (int i = 0; i < warna.length; i++) {
			int sx = dx0 + (i % perBaris) * (sw + 3);
			int sy = y + (i / perBaris) * (sw + 3);
			boolean on = i == warnaPilih;
			l.g.fill(sx, sy, sx + sw, sy + sw, rgb(warna[i]));
			l.g.fillGradient(sx, sy, sx + sw, sy + sw / 2, 0x30FFFFFF, 0x00FFFFFF);
			Brush.frame(l.g, sx - (on ? 1 : 0), sy - (on ? 1 : 0), sx + sw + (on ? 1 : 0), sy + sw + (on ? 1 : 0),
					on ? Palette.GOLD_LIGHT : Palette.argb(0x80, 0x000000));
			final int idx = i;
			l.zona("warna" + i, sx, sy, sx + sw, sy + sw, () -> pilihWarna(idx), true, null, true);
			l.fokusCincin("warna" + i, sx - 1, sy - 1, sx + sw + 1, sy + sw + 1);
		}
		y += ((warna.length + perBaris - 1) / perBaris) * (sw + 3) + 3;
		y += subjudul(l, TemaUi.k("sutra.motif"), dx0, y, dx1);
		int mw = Math.max(30, (dx1 - dx0 - 3 * 3) / Math.max(1, motif.size()));
		for (int i = 0; i < motif.size(); i++) {
			String id = motif.get(i);
			int mx = dx0 + i * (mw + 3);
			boolean on = id.equals(motifPilih);
			boolean over = l.di(mx, y, mx + mw, y + 26);
			l.kartu(mx, y, mx + mw, y + 26, over ? 1.0F : 0.0F, on);
			TemaUi.ikon(l.g, ikonMotif(id), mx + mw / 2 - 6, y + 2, 12);
			l.tengah(TemaUi.k("sutra.motif." + id), mx + mw / 2.0F, y + 15, mw - 4, on ? aksen() : gaya.teksLemah);
			l.zona("motif" + i, mx, y, mx + mw, y + 26, () -> {
				motifPilih = id;
				ubahSejak = Util.getMillis();
			}, true, null, true);
			l.fokusCincin("motif" + i, mx, y, mx + mw, y + 26);
		}
		y += 29;
		long harga = j.l(LembarKunci.HARGA, 80L);
		if (y + 10 <= by - 11) {
			y += barisNilai(l, TemaUi.k("sutra.lama"), Brush.hari(j.i(LembarKunci.HARI, 2)), dx0, y, dx1);
		}
		if (y + 10 <= by - 1) {
			l.teks(TemaUi.k("sutra.harga"), dx0, y + 1, dx1 - dx0 - 60, gaya.teksLemah);
			LabelHarga.gambar(l, harga, uang(), dx1, y + 1, true, false);
		}
		boolean bisa = warna.length > 0 && !motifPilih.isEmpty() && mampu(harga);
		Component alasan = warna.length == 0 || motifPilih.isEmpty() ? TemaUi.k("sutra.pilih_dulu") : !mampu(harga) ? kurang(harga) : null;
		int pilihWarna = warna.length == 0 ? 0 : warna[warnaPilih] & 0xFFFFFF;
		tombolOp(l, "pesan", dx0, by, dx1 - dx0, 15, TemaUi.k("sutra.pesan", Brush.angka(harga)), bisa, alasan,
				LembarKunci.op(Sutra.OP_PESAN_JUBAH, pilihWarna, motifPilih));
	}

	private static int ikonMotif(String id) {
		return switch (id) {
			case "awan" -> TemaUi.I_AIR;
			case "bangau" -> TemaUi.I_KUAS;
			case "teratai" -> TemaUi.I_KUIL;
			case "naga" -> TemaUi.I_GENDERANG;
			default -> TemaUi.I_JUBAH;
		};
	}

	// ------------------------------------------------------------------------------------------------ loom

	private void tenun(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar t = lembar.sub(Sutra.TENUN);
		int y = y0 + strip(l, TemaUi.k("sutra.ucap_tenun"), x0, y0, x1, tata == Tata.SEMPIT ? 30 : 32);
		boolean bisa = t.b(LembarKunci.BISA);
		if (!bisa && t.ada(LembarKunci.ALASAN)) {
			KeadaanTerkunci.gambar(l, t.teks(LembarKunci.ALASAN), TemaUi.k("sutra.tenun_besok"), x0, y, x1, y1, Util.getMillis() - diterima);
			return;
		}
		Kotak[] k = bagiGambar(x0, y, x1, y1);
		Kotak a = k[0];
		l.sumur(a.x0(), a.y0(), a.x1(), a.y1());
		int s = Math.max(24, Math.min(a.w() - 8, a.h() - 8));
		int lx = (a.x0() + a.x1()) / 2 - s / 2;
		int ly = (a.y0() + a.y1()) / 2 - s / 2;
		TemaUi.iramaBesar(l.g, 2, lx, ly, s, 0xFFFFFFFF);
		// the shuttle running across the warp, and the cloth growing under it
		float t01 = (float) ((Math.sin(l.kini / 380.0D) + 1.0D) / 2.0D);
		int sx = lx + Math.round(s * (0.15F + 0.62F * t01));
		TemaUi.iramaKecil(l.g, 0, sx, ly + s / 2 - 4, Math.max(10, s / 4), 0xFFFFFFFF);
		int kain = Math.round((l.kini / 120L) % Math.max(1, s / 3));
		l.g.fill(lx + s / 6, ly + s * 2 / 3, lx + s * 5 / 6, ly + s * 2 / 3 + kain / 2 + 1, 0x90C84A3A);
		Kotak d = k[1];
		l.kartu(d.x0(), d.y0(), d.x1(), d.y1(), 0.0F, false);
		int dx0 = d.x0() + 5;
		int dx1 = d.x1() - 5;
		int dy = d.y0() + 4;
		int by = d.y1() - 19;
		dy += subjudul(l, TemaUi.k("sutra.tenun_judul"), dx0, dy, dx1);
		dy += Math.round(l.bungkus(TemaUi.k("sutra.tenun_jelas"), dx0, dy, dx1 - dx0, gaya.teksLemah, Math.max(9, by - dy - 30))) + 3;
		int hadiah = Math.max(1, t.i(Sutra.HADIAH_MAKS, 3));
		Component hl = TemaUi.k("sutra.hadiah");
		int hw = Math.min(80, font.width(hl) + 4);
		l.teks(hl, dx0, dy + 1, hw, gaya.teksLemah);
		for (int i = 0; i < hadiah; i++) {
			TemaUi.ikon(l.g, TemaUi.ikonBarang("sutra_awan"), dx0 + hw + i * 14, dy - 1, 12);
		}
		dy += 14;
		if (by - dy >= 11) {
			l.bungkus(TemaUi.k("sutra.tenun_aturan"), dx0, dy, dx1 - dx0, gaya.teksSamar, by - dy - 2);
		}
		tombolOp(l, "tenun", dx0, by, dx1 - dx0, 15, TemaUi.k("sutra.tenun"), bisa, t.teksAtau(LembarKunci.ALASAN), Sutra.OP_TENUN);
	}
}
