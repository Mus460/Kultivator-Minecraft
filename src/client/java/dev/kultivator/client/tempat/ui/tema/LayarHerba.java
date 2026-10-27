package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Pratinjau3D;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Herba;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code tema_herba} (DESIGN 38.T.6.4): Aula Herba Gunung, the herb hall of the mountain town.
 * <ul>
 * <li><b>Beli</b>: cliff herbs with their age in years and a growth-ring gauge (the kit's one shop composition).</li>
 * <li><b>Jual</b>: the herbs in the player's bag at half their value, sold one stack at a time.</li>
 * <li><b>Nilai</b>: the free appraisal of the held herb: the herb turning, its cut stem drawn with one ring per age band,
 * the age and the grade.</li>
 * <li><b>Peringkat</b>: the pickers' ladder of five ranks, points to the next rank, extra picking spots and the hall's
 * discount.</li>
 * </ul>
 * A Di Sini page on the single kit (43.8.3). In the narrow layout the sell list keeps a one-line detail with its button,
 * and the appraisal and ladder pictures sit beside their text instead of above it.
 */
public final class LayarHerba extends TemaHalaman {

	private final Toko toko = new Toko();
	private final DaftarGulir daftarJual = new DaftarGulir();
	private final Pratinjau3D pratinjauJual = new Pratinjau3D();
	private final Pratinjau3D pratinjauNilai = new Pratinjau3D();

	public LayarHerba(int npcId, CompoundTag lembar) {
		super(Herba.LAYAR, npcId, lembar, "layar.herba", Palette.Gaya.KAYU);
	}

	@Override
	protected List<TabBar.Tab> tabs() {
		return List.of(tabTema(Herba.TAB_BELI, "herba.tab.beli"),
				tabTema(Herba.TAB_JUAL, "herba.tab.jual"),
				tabTema(Herba.TAB_NILAI, "herba.tab.nilai"),
				tabTema(Herba.TAB_PERINGKAT, "herba.tab.peringkat"));
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
	protected void lukisTema(Lukis l, int x0, int y0, int x1, int y1) {
		switch (tab) {
			case Herba.TAB_JUAL -> jual(l, x0, y0, x1, y1);
			case Herba.TAB_NILAI -> nilai(l, x0, y0, x1, y1);
			case Herba.TAB_PERINGKAT -> peringkat(l, x0, y0, x1, y1);
			default -> {
				int y = y0 + strip(l, TemaUi.k("herba.ucap"), x0, y0, x1, ucapH());
				Kotak[] k = bagi(x0, y, x1, y1);
				toko(l, "beli", toko, lembar.daftar(Herba.BELI), k[0], k[1], Herba.OP_BELI, false,
						(lk, e, ex0, ey, ex1) -> umurBaris(lk, e.i(Herba.UMUR, 0), ex0, ey, ex1), null, TemaUi.k("herba.beli_kosong"));
			}
		}
	}

	private int ucapH() {
		return tata == Tata.SEMPIT ? 30 : 32;
	}

	/** Picture | text: the usual split, or in the narrow layout side by side ({@code bagian} of the width for the picture). */
	private Kotak[] sisi(int x0, int y0, int x1, int y1, float bagian) {
		if (tata != Tata.SEMPIT) {
			return bagi(x0, y0, x1, y1);
		}
		int mid = x0 + Math.round((x1 - x0) * bagian);
		return new Kotak[] {new Kotak(x0, y0, mid - 2, y1), new Kotak(mid + 2, y0, x1, y1)};
	}

	/** Age line with a growth-ring gauge on a log scale (10, 100, 1000 years marked). Returns the height (21). */
	private int umurBaris(Lukis l, int umur, int x0, int y, int x1) {
		l.teks(TemaUi.k("herba.umur", Brush.angka(umur)), x0, y, x1 - x0, aksen());
		float f = umur <= 0 ? 0.0F : (float) Math.min(1.0D, Math.log10(umur) / 3.0D);
		int warna = umur >= 500 ? 0xFFC890FF : umur >= 100 ? Palette.GOLD : umur >= 30 ? Palette.OK : 0xFF9AB89A;
		TemaUi.meter(l, -1, x0, y + 10, x1, f, warna, -1.0F);
		for (int m = 1; m <= 2; m++) {
			int tx = x0 + Math.round((x1 - x0) * m / 3.0F);
			l.g.fill(tx, y + 10, tx + 1, y + 18, 0x80F7DC7E);
		}
		return 21;
	}

	// ------------------------------------------------------------------------------------------------ sell

	private void jual(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> jual = lembar.daftar(Herba.JUAL);
		int y = y0 + strip(l, TemaUi.k("herba.ucap_jual"), x0, y0, x1, ucapH());
		boolean sempit = tata == Tata.SEMPIT;
		Kotak[] k = sempit ? new Kotak[] {new Kotak(x0, y, x1, y1 - 34), new Kotak(x0, y1 - 31, x1, y1)} : bagi(x0, y, x1, y1);
		if (jual.isEmpty()) {
			kosong(l, TemaUi.k("herba.jual_kosong"), k[0].x0(), k[0].y0(), k[0].x1(), sempit ? y1 : k[0].y1());
			if (sempit) {
				return;
			}
		} else {
			daftarJual.gambar(l, "jual", k[0].x0(), k[0].y0(), k[0].x1(), k[0].y1(), jual.size(), 22, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar e = jual.get(i);
				lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
				if (!e.barang().isEmpty()) {
					lk.g.item(e.barang(), rx0 + 3, ry0 + 3);
				} else {
					Glyphs.Atlas.ikon(lk.g, LembarKunci.Ikon.HERBA, rx0 + 3, ry0 + 3, 16, 0xFFFFFFFF);
				}
				int pw = LabelHarga.gambar(lk, e.l(LembarKunci.HARGA, 0L), -1L, rx1 - 4, ry0 + 7, true, true);
				lk.teks(e.teks(LembarKunci.NAMA), rx0 + 23, ry0 + 2, rx1 - rx0 - 32 - pw, gaya.teks);
				lk.samar(TemaUi.k("herba.umur", Brush.angka(e.i(Herba.UMUR, 0))), rx0 + 23, ry0 + 12, rx1 - rx0 - 32 - pw);
			}, this::jualPilihan, null);
		}
		Kotak d = k[1];
		int sel = daftarJual.pilihan();
		Lembar e = sel >= 0 && sel < jual.size() ? jual.get(sel) : null;
		if (sempit) {
			// one line: the chosen herb's age, then its sell button
			if (e != null) {
				l.teks(TemaUi.k("herba.umur", Brush.angka(e.i(Herba.UMUR, 0))), d.x0(), d.y0() + 1, d.w(), aksen());
				tombolJual(l, e, d.x0(), d.y1() - 15, d.x1());
			}
			return;
		}
		l.kartu(d.x0(), d.y0(), d.x1(), d.y1(), 0.0F, false);
		if (e == null) {
			tengah(l, TemaUi.k("herba.jual_pilih"), d);
			return;
		}
		int dx0 = d.x0() + 5;
		int dx1 = d.x1() - 5;
		int dy = d.y0() + 4;
		int by = d.y1() - 19;
		int pv = 52;
		pratinjauJual.gambar(l, "jual_3d", dx0, dy, dx0 + pv, dy + pv, e.barang(), null, LembarKunci.Ikon.HERBA);
		int tx = dx0 + pv + 6;
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, dy, dx1 - tx, aksen());
		umurBaris(l, e.i(Herba.UMUR, 0), tx, dy + 12, dx1);
		dy += Math.max(pv, 36) + 6;
		if (by - dy >= 11) {
			l.bungkus(TemaUi.k("herba.jual_jelas"), dx0, dy, dx1 - dx0, gaya.teksSamar, by - dy - 2);
		}
		tombolJual(l, e, dx0, by, dx1);
	}

	private void tombolJual(Lukis l, Lembar e, int x0, int by, int x1) {
		long harga = e.l(LembarKunci.HARGA, 0L);
		tombolOp(l, "jual_btn", x0, by, x1 - x0, 15, TemaUi.k("herba.jual", Brush.angka(harga)), true, null,
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
		int y = y0 + strip(l, TemaUi.k(ada ? "herba.ucap_nilai" : "herba.ucap_tangan_kosong"), x0, y0, x1, ucapH());
		Kotak[] k = sisi(x0, y, x1, y1, 0.36F);
		Kotak a = k[0];
		l.sumur(a.x0(), a.y0(), a.x1(), a.y1());
		if (!ada) {
			l.bungkus(TemaUi.k("herba.pegang"), a.x0() + 4, a.y0() + a.h() / 2 - 9, a.w() - 8, gaya.teksSamar, a.h() / 2);
		} else {
			int umur = tangan.i(Herba.UMUR, 0);
			int size = Math.min(a.w(), a.h()) - 10;
			int pv = Math.max(24, size / 2);
			pratinjauNilai.gambar(l, "nilai_3d", a.x0() + 5, a.y0() + 5, a.x0() + 5 + pv, a.y0() + 5 + pv, tangan.barang(), null,
					LembarKunci.Ikon.HERBA);
			batang(l, a.x1() - 6 - size / 2, a.y1() - 6 - size / 2, size / 2 - 2, umur);
		}
		Kotak d = k[1];
		l.kartu(d.x0(), d.y0(), d.x1(), d.y1(), 0.0F, false);
		int dx0 = d.x0() + 5;
		int dx1 = d.x1() - 5;
		int dy = d.y0() + 4;
		int by = d.y1() - 19;
		dy += subjudul(l, TemaUi.k("herba.penilaian"), dx0, dy, dx1);
		if (ada) {
			int mutu = tangan.i(LembarKunci.MUTU, -1);
			l.teks(Brush.bold(tangan.teks(LembarKunci.NAMA)), dx0, dy, dx1 - dx0, Palette.mutu(mutu, gaya.gelap(), gaya.teks));
			dy += 12;
			dy += umurBaris(l, tangan.i(Herba.UMUR, 0), dx0, dy, dx1) + 2;
			if (mutu >= 0 && dy + 10 <= by - 2) {
				dy += barisNilai(l, TemaUi.k("herba.mutu"), TemaUi.k("herba.mutu." + Math.min(4, mutu)), dx0, dy, dx1);
			}
			dy += 3;
			if (by - dy >= 11) {
				l.bungkus(TemaUi.k("herba.cincin_jelas"), dx0, dy, dx1 - dx0, gaya.teksSamar, by - dy - 2);
			}
		} else if (by - dy >= 11) {
			l.bungkus(TemaUi.k("herba.nilai_jelas"), dx0, dy, dx1 - dx0, gaya.teksLemah, by - dy - 2);
		}
		tombolOp(l, "nilai_btn", dx0, by, dx1 - dx0, 15, TemaUi.k("herba.nilai"), true, null, Herba.OP_NILAI);
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
		int y = y0 + strip(l, TemaUi.k("herba.ucap_peringkat"), x0, y0, x1, ucapH());
		Kotak[] k = sisi(x0, y, x1, y1, 0.45F);
		Kotak a = k[0];
		l.kartu(a.x0(), a.y0(), a.x1(), a.y1(), 0.0F, false);
		TemaUi.latarGunung(l.g, a.x0() + 1, a.y0() + 1, a.x1() - 1, a.y1() - 1, 0x90FFFFFF);
		// the five ranks climbing the mountain, the current one lit and named (the others name themselves on hover)
		for (int i = 1; i <= 5; i++) {
			float t = (i - 1) / 4.0F;
			int px = Math.round(a.x0() + 10 + (a.w() - 26) * t);
			int py = Math.round(a.y1() - 14 - (a.h() - 28) * t);
			if (i > 1) {
				float tp = (i - 2) / 4.0F;
				int qx = Math.round(a.x0() + 10 + (a.w() - 26) * tp);
				int qy = Math.round(a.y1() - 14 - (a.h() - 28) * tp);
				Brush.line(l.g, qx + 5, qy + 5, px + 5, py + 5, 1.0F, i <= pangkat ? Palette.GOLD : 0x80FFFFFF);
			}
			boolean kini = i == pangkat;
			Brush.disc(l.g, px + 5, py + 5, kini ? 7.0F : 5.5F, i <= pangkat ? Palette.GOLD : 0xA0303030);
			if (kini) {
				Brush.halo(l.g, px + 5, py + 5, 7.5F, 2.0F, Palette.GOLD_LIGHT & 0xFFFFFF, l.denyut());
			}
			l.tengah(Component.literal(Integer.toString(i)), px + 5.5F, py + 1, 10, i <= pangkat ? 0xFF3A2208 : 0xFFE0E0E0);
			Component nama = TemaUi.k("herba.pangkat." + i);
			if (kini) {
				int nx = px + 14;
				int nw = Math.max(0, a.x1() - 3 - nx);
				if (nw < 40) {
					nx = Math.max(a.x0() + 3, px - 4 - Math.min(font.width(nama), a.w() - 8));
					nw = Math.min(font.width(nama), a.w() - 8);
				}
				l.teks(nama, nx, py + 1, nw, 0xFFFFF4D0);
			}
			if (l.di(px - 2, py - 2, px + 12, py + 12)) {
				l.tip(nama);
			}
		}
		Kotak d = k[1];
		l.kartu(d.x0(), d.y0(), d.x1(), d.y1(), 0.0F, false);
		int dx0 = d.x0() + 5;
		int dx1 = d.x1() - 5;
		int dy = d.y0() + 4;
		int bawah = d.y1() - 3;
		dy += subjudul(l, TemaUi.k("herba.pangkat." + pangkat), dx0, dy, dx1);
		int poin = p.i(Herba.POIN, 0);
		int naik = p.i(Herba.POIN_NAIK, 0);
		if (pangkat < 5 && naik > 0) {
			l.teks(TemaUi.k("herba.poin", poin, naik), dx0, dy, dx1 - dx0, gaya.teksLemah);
			dy += 10;
			TemaUi.meter(l, TemaUi.I_GUNUNG, dx0, dy, dx1, poin / (float) naik, Palette.OK, -1.0F);
			dy += 12;
		} else {
			l.teks(TemaUi.k("herba.puncak"), dx0, dy, dx1 - dx0, aksen());
			dy += 12;
		}
		dy += barisNilai(l, TemaUi.k("herba.titik"), Component.literal("+" + p.i(Herba.TITIK_TAMBAHAN, 0)), dx0, dy, dx1);
		float diskon = p.f(LembarKunci.DISKON, 0.0F);
		dy += barisNilai(l, TemaUi.k("herba.diskon"), Component.literal(Math.round(diskon * 100.0F) + "%"), dx0, dy, dx1) + 3;
		if (bawah - dy >= 9) {
			l.bungkus(TemaUi.k("herba.peringkat_jelas"), dx0, dy, dx1 - dx0, gaya.teksSamar, bawah - dy);
		}
	}
}
