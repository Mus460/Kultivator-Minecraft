package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.Pratinjau3D;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Herba;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code tema_herba} (DESIGN 38.T.6.4): Aula Herba Gunung, the herb hall of the mountain town.
 * <ul>
 * <li><b>Beli</b>: cliff herbs with their age in years and a growth-ring gauge (shared shop block).</li>
 * <li><b>Jual</b>: the herbs in the player's bag at half their value, sold one stack at a time.</li>
 * <li><b>Nilai</b>: the free appraisal of the held herb: the herb turning, its cut stem drawn with one ring per age band,
 * the age and the grade.</li>
 * <li><b>Peringkat</b>: the pickers' ladder of five ranks, points to the next rank, extra picking spots and the hall's
 * discount.</li>
 * </ul>
 */
public final class LayarHerba extends LayarTemaDasar {

	private final Toko toko = new Toko();
	private final DaftarGulir daftarJual = new DaftarGulir();
	private final Pratinjau3D pratinjauJual = new Pratinjau3D();
	private final Pratinjau3D pratinjauNilai = new Pratinjau3D();

	public LayarHerba(int npcId, CompoundTag lembar) {
		super(Herba.LAYAR, npcId, lembar, "layar.herba", TempatWarna.Gaya.KAYU);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(tabTema(Herba.TAB_BELI, "herba.tab.beli", LembarKunci.Ikon.HERBA),
				tabTema(Herba.TAB_JUAL, "herba.tab.jual", LembarKunci.Ikon.TIMBANGAN),
				tabTema(Herba.TAB_NILAI, "herba.tab.nilai", LembarKunci.Ikon.MATA_BATIN),
				tabTema(Herba.TAB_PERINGKAT, "herba.tab.peringkat", IkonTempat.PIALA));
	}

	@Override
	protected boolean tabAda(String id) {
		return switch (id) {
			case Herba.TAB_NILAI -> lembar.ada(Herba.NILAI_HERBA);
			case Herba.TAB_PERINGKAT -> lembar.ada(Herba.PERINGKAT);
			default -> true;
		};
	}

	@Override
	protected void tabGanti() {
		toko.mulaiUlang();
		daftarJual.mulaiUlang(Util.getMillis());
	}

	@Override
	protected void gambar(Lukis l, int x0, int y0, int x1, int y1) {
		switch (tab) {
			case Herba.TAB_JUAL -> jual(l, x0, y0, x1, y1);
			case Herba.TAB_NILAI -> nilai(l, x0, y0, x1, y1);
			case Herba.TAB_PERINGKAT -> peringkat(l, x0, y0, x1, y1);
			default -> {
				int y = y0 + strip(l, TemaUi.k("herba.ucap"), x0, y0, x1, 32);
				Kotak[] k = bagi(x0, y, x1, y1);
				toko(l, "beli", toko, lembar.daftar(Herba.BELI), k[0], k[1], Herba.OP_BELI, false,
						(lk, e, ex0, ey, ex1) -> umurBaris(lk, e.i(Herba.UMUR, 0), ex0, ey, ex1), null, TemaUi.k("herba.beli_kosong"));
			}
		}
	}

	/** Age line with a growth-ring gauge on a log scale (10, 100, 1000 years marked). Returns the height (20). */
	private int umurBaris(Lukis l, int umur, int x0, int y, int x1) {
		l.keterangan(TemaUi.k("herba.umur", TeksTempat.angka(umur)), x0, y, x1 - x0, aksen());
		float f = umur <= 0 ? 0.0F : (float) Math.min(1.0D, Math.log10(umur) / 3.0D);
		int warna = umur >= 500 ? 0xFFC890FF : umur >= 100 ? TempatWarna.EMAS : umur >= 30 ? TempatWarna.OK : 0xFF9AB89A;
		TemaUi.meter(l, -1, x0, y + 10, x1, f, warna, -1.0F);
		for (int m = 1; m <= 2; m++) {
			int tx = x0 + Math.round((x1 - x0) * m / 3.0F);
			l.g.fill(tx, y + 9, tx + 1, y + 19, 0x80F7DC7E);
		}
		return 21;
	}

	// ------------------------------------------------------------------------------------------------ sell

	private void jual(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> jual = lembar.daftar(Herba.JUAL);
		int y = y0 + strip(l, TemaUi.k("herba.ucap_jual"), x0, y0, x1, 32);
		Kotak[] k = bagi(x0, y, x1, y1);
		if (jual.isEmpty()) {
			kosong(l, TemaUi.k("herba.jual_kosong"), k[0].x0(), k[0].y0(), k[0].x1(), k[0].y1());
		} else {
			long u = uang();
			daftarJual.gambar(l, "jual", k[0].x0(), k[0].y0(), k[0].x1(), k[0].y1(), jual.size(), 22, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar e = jual.get(i);
				PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
				if (!e.barang().isEmpty()) {
					lk.g.item(e.barang(), rx0 + 3, ry0 + 3);
				} else {
					IkonTempat.gambar(lk.g, LembarKunci.Ikon.HERBA, rx0 + 3, ry0 + 3, 16, 0xFFFFFFFF);
				}
				lk.teks(e.teks(LembarKunci.NAMA), rx0 + 23, ry0 + 3, rx1 - rx0 - 76, gaya.teks, 0.85F);
				lk.keterangan(TemaUi.k("herba.umur", TeksTempat.angka(e.i(Herba.UMUR, 0))), rx0 + 23, ry0 + 13, rx1 - rx0 - 76, gaya.teksSamar);
				LabelHarga.gambar(lk, e.l(LembarKunci.HARGA, 0L), -1L, rx1 - 4, ry0 + 7, true, 0.8F, true);
			}, this::jualPilihan, null);
		}
		Kotak d = k[1];
		PanelTempat.kartu(l.g, gaya, d.x0(), d.y0(), d.x1(), d.y1(), 0.0F, false);
		int sel = daftarJual.pilihan();
		if (sel < 0 || sel >= jual.size()) {
			tengah(l, TemaUi.k("herba.jual_pilih"), d);
			return;
		}
		Lembar e = jual.get(sel);
		int dx0 = d.x0() + 5;
		int dx1 = d.x1() - 5;
		int dy = d.y0() + 4;
		int pv = tata == Tata.SEMPIT ? 28 : 52;
		pratinjauJual.gambar(l, "jual_3d", dx0, dy, dx0 + pv, dy + pv, e.barang(), null, LembarKunci.Ikon.HERBA);
		int tx = dx0 + pv + 6;
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, dy, dx1 - tx, aksen());
		umurBaris(l, e.i(Herba.UMUR, 0), tx, dy + 12, dx1);
		dy += Math.max(pv, 36) + 6;
		l.bungkus(TemaUi.k("herba.jual_jelas"), dx0, dy, dx1 - dx0, gaya.teksSamar, 0.7F);
		long harga = e.l(LembarKunci.HARGA, 0L);
		tombolOp(l, "jual_btn", dx0, d.y1() - 19, dx1 - dx0, 15, TemaUi.k("herba.jual", TeksTempat.angka(harga)), true, null,
				LembarKunci.op(Herba.OP_JUAL, e.i(LembarKunci.SLOT, -1)));
	}

	private void jualPilihan() {
		List<Lembar> jual = lembar.daftar(Herba.JUAL);
		int i = daftarJual.pilihan();
		if (i >= 0 && i < jual.size() && tulisBoleh()) {
			kirim(LembarKunci.op(Herba.OP_JUAL, jual.get(i).i(LembarKunci.SLOT, -1)));
		}
	}

	// ------------------------------------------------------------------------------------------------ appraisal

	private void nilai(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar n = lembar.sub(Herba.NILAI_HERBA);
		Lembar tangan = n.sub(LembarKunci.TANGAN);
		boolean ada = tangan.ada(LembarKunci.NAMA);
		int y = y0 + strip(l, TemaUi.k(ada ? "herba.ucap_nilai" : "herba.ucap_tangan_kosong"), x0, y0, x1, 32);
		Kotak[] k = bagi(x0, y, x1, y1);
		Kotak a = k[0];
		PanelTempat.sumur(l.g, gaya, a.x0(), a.y0(), a.x1(), a.y1());
		if (!ada) {
			tengah(l, TemaUi.k("herba.pegang"), a);
		} else {
			int umur = tangan.i(Herba.UMUR, 0);
			int size = Math.min(a.w(), a.h()) - 10;
			int pv = Math.max(24, size / 2);
			pratinjauNilai.gambar(l, "nilai_3d", a.x0() + 5, a.y0() + 5, a.x0() + 5 + pv, a.y0() + 5 + pv, tangan.barang(), null,
					LembarKunci.Ikon.HERBA);
			batang(l, a.x1() - 6 - size / 2, a.y1() - 6 - size / 2, size / 2 - 2, umur);
		}
		Kotak d = k[1];
		PanelTempat.kartu(l.g, gaya, d.x0(), d.y0(), d.x1(), d.y1(), 0.0F, false);
		int dx0 = d.x0() + 5;
		int dx1 = d.x1() - 5;
		int dy = d.y0() + 4;
		dy += subjudul(l, TemaUi.k("herba.penilaian"), dx0, dy, dx1);
		if (ada) {
			int mutu = tangan.i(LembarKunci.MUTU, -1);
			l.teks(Brush.bold(tangan.teks(LembarKunci.NAMA)), dx0, dy, dx1 - dx0, TempatWarna.mutu(mutu, gaya.gelap(), gaya.teks));
			dy += 12;
			dy += umurBaris(l, tangan.i(Herba.UMUR, 0), dx0, dy, dx1) + 2;
			if (mutu >= 0) {
				dy += barisNilai(l, TemaUi.k("herba.mutu"), TemaUi.k("herba.mutu." + Math.min(4, mutu)), dx0, dy, dx1);
			}
			dy += 3;
			l.bungkus(TemaUi.k("herba.cincin_jelas"), dx0, dy, dx1 - dx0, gaya.teksSamar, 0.7F);
		} else {
			l.bungkus(TemaUi.k("herba.nilai_jelas"), dx0, dy, dx1 - dx0, gaya.teksLemah, 0.75F);
		}
		tombolOp(l, "nilai_btn", dx0, d.y1() - 19, dx1 - dx0, 15, TemaUi.k("herba.nilai"), true, null, Herba.OP_NILAI);
	}

	/** The cut stem: one ring per age band (under 10, 30, 100, 300, 1000 years ...), the bark and a drop of sap. */
	private void batang(Lukis l, int cx, int cy, int r, int umur) {
		Brush.disc(l.g, cx, cy + 1.5F, r, 0x50000000);
		Brush.disc(l.g, cx, cy, r, 0xFF5A3A20);
		Brush.disc(l.g, cx, cy, r - 2.0F, 0xFFD8C08A);
		int[] batas = {1, 10, 30, 100, 300, 1000, 3000};
		int cincin = 0;
		for (int b : batas) {
			if (umur >= b) {
				cincin++;
			}
		}
		for (int i = 1; i <= cincin; i++) {
			float rr = (r - 3.0F) * i / (float) Math.max(1, batas.length);
			int warna = i >= 5 ? 0xC0A060C0 : i >= 4 ? 0xC0C89A30 : 0x90906A3A;
			Brush.ring(l.g, cx, cy, rr - 0.7F, rr, warna);
		}
		Brush.dot(l.g, cx, cy, 1.5F, 0xFF6A4A20);
		float tetes = (l.kini % 2400L) / 2400.0F;
		Brush.dot(l.g, cx + r * 0.6F, cy - r * 0.2F + tetes * 4.0F, 1.2F, Palette.argb(Math.round(0xD0 * (1.0F - tetes)), 0xF7DC7E));
		if (cincin >= 5) {
			Brush.halo(l.g, cx, cy, r, 2.0F, 0xC890FF, 0.5F + 0.5F * l.denyut());
		}
	}

	// ------------------------------------------------------------------------------------------------ pickers' ladder

	private void peringkat(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar p = lembar.sub(Herba.PERINGKAT);
		int pangkat = Math.max(1, Math.min(5, p.i(Herba.PANGKAT, 1)));
		int y = y0 + strip(l, TemaUi.k("herba.ucap_peringkat"), x0, y0, x1, 32);
		Kotak[] k = bagi(x0, y, x1, y1);
		Kotak a = k[0];
		PanelTempat.kartu(l.g, gaya, a.x0(), a.y0(), a.x1(), a.y1(), 0.0F, false);
		TemaUi.latarGunung(l.g, a.x0() + 1, a.y0() + 1, a.x1() - 1, a.y1() - 1, 0x90FFFFFF);
		// the five ranks climbing the mountain, the current one lit
		for (int i = 1; i <= 5; i++) {
			float t = (i - 1) / 4.0F;
			int px = Math.round(a.x0() + 14 + (a.w() - 40) * t);
			int py = Math.round(a.y1() - 14 - (a.h() - 34) * t);
			if (i > 1) {
				float tp = (i - 2) / 4.0F;
				int qx = Math.round(a.x0() + 14 + (a.w() - 40) * tp);
				int qy = Math.round(a.y1() - 14 - (a.h() - 34) * tp);
				Brush.line(l.g, qx + 5, qy + 5, px + 5, py + 5, 1.0F, i <= pangkat ? TempatWarna.EMAS : 0x80FFFFFF);
			}
			boolean kini = i == pangkat;
			Brush.disc(l.g, px + 5, py + 5, kini ? 7.0F : 5.0F, i <= pangkat ? TempatWarna.EMAS : 0xA0303030);
			if (kini) {
				Brush.halo(l.g, px + 5, py + 5, 7.5F, 2.0F, TempatWarna.EMAS_TERANG & 0xFFFFFF, l.denyut());
			}
			Brush.centered(l.g, font, Component.literal(Integer.toString(i)), px + 5, py + 1.5F, i <= pangkat ? 0xFF3A2208 : 0xFFE0E0E0, 0.75F, false);
			Brush.text(l.g, font, TemaUi.k("herba.pangkat." + i), px + 14, py + 2, kini ? 0xFFFFF4D0 : 0xC0F0F0F0, 0.6F, true);
		}
		Kotak d = k[1];
		PanelTempat.kartu(l.g, gaya, d.x0(), d.y0(), d.x1(), d.y1(), 0.0F, false);
		int dx0 = d.x0() + 5;
		int dx1 = d.x1() - 5;
		int dy = d.y0() + 4;
		dy += subjudul(l, TemaUi.k("herba.pangkat." + pangkat), dx0, dy, dx1);
		int poin = p.i(Herba.POIN, 0);
		int naik = p.i(Herba.POIN_NAIK, 0);
		if (pangkat < 5 && naik > 0) {
			l.keterangan(TemaUi.k("herba.poin", poin, naik), dx0, dy, dx1 - dx0, gaya.teksLemah);
			dy += 10;
			TemaUi.meter(l, TemaUi.I_GUNUNG, dx0, dy, dx1, poin / (float) naik, TempatWarna.OK, -1.0F);
			dy += 13;
		} else {
			l.keterangan(TemaUi.k("herba.puncak"), dx0, dy, dx1 - dx0, aksen());
			dy += 12;
		}
		dy += barisNilai(l, TemaUi.k("herba.titik"), Component.literal("+" + p.i(Herba.TITIK_TAMBAHAN, 0)), dx0, dy, dx1);
		float diskon = p.f(LembarKunci.DISKON, 0.0F);
		dy += barisNilai(l, TemaUi.k("herba.diskon"), Component.literal(Math.round(diskon * 100.0F) + "%"), dx0, dy, dx1) + 4;
		l.bungkus(TemaUi.k("herba.peringkat_jelas"), dx0, dy, dx1 - dx0, gaya.teksSamar, 0.7F);
	}
}
