package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.KitabTerlarang;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.PasarGelap;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * {@code tema_kitab_terlarang} (DESIGN 38.T.7.7): the Menara Kitab Terlarang of the blind keeper Mo Wen. Left the
 * nine-storey tower itself: each floor is a band of the tower picture; open floors glow at their windows, floors the
 * player's Gentar does not reach hang in chains with the Gentar they need, the ninth floor is sealed with a red talisman.
 * Clicking an open floor asks for its shelves ({@code lantai:NO}, read). Right the scrolls of the chosen floor and, when
 * offered, the soul jades: the kit's one shop composition with each scroll's effect, the band it needs and the Infami it
 * costs. A Di Sini page on the single kit (43.8.3); the number keys pick a floor under Di Sini (43.17 K3).
 */
public final class LayarKitabTerlarang extends TemaHalaman {

	/** Entry key of {@code gulungan} (C38.T3: pita_min I). */
	private static final String PITA_MIN = "pita_min";

	private final Toko toko = new Toko();
	private int lantaiPilih = 1;
	private int gentar;

	public LayarKitabTerlarang(int npcId, CompoundTag lembar) {
		super(KitabTerlarang.LAYAR, npcId, lembar, "layar.kitab", Palette.Gaya.KERTAS);
	}

	@Override
	protected List<TabBar.Tab> tabs() {
		return List.of();
	}

	@Override
	protected Component petunjuk() {
		return TemaUi.k("kitab.kunci");
	}

	@Override
	protected void lembarBaru(boolean buka) {
		// optional: builders may add the player's Gentar so locked floors can say how far it is
		gentar = lembar.i(PasarGelap.GENTAR, -1);
		if (buka) {
			int tertinggi = 1;
			for (Lembar f : lembar.daftar(KitabTerlarang.LANTAI)) {
				if (f.b(KitabTerlarang.TERBUKA)) {
					tertinggi = Math.max(tertinggi, f.i(LembarKunci.NO, 1));
				}
			}
			lantaiPilih = lembar.i(LembarKunci.NO, tertinggi);
			toko.mulaiUlang();
		}
	}

	/** K3 under Di Sini: the number keys pick a floor. */
	@Override
	public boolean angka(int n) {
		Lembar f = lantai(n);
		if (f != null) {
			pilihLantai(f);
			return true;
		}
		return false;
	}

	private Lembar lantai(int no) {
		for (Lembar f : lembar.daftar(KitabTerlarang.LANTAI)) {
			if (f.i(LembarKunci.NO, 0) == no) {
				return f;
			}
		}
		return null;
	}

	private void pilihLantai(Lembar f) {
		int no = f.i(LembarKunci.NO, 1);
		if (!f.b(KitabTerlarang.TERBUKA)) {
			UiSound.denied();
			tampilPesan(f.b(KitabTerlarang.SEGEL) ? TemaUi.k("kitab.segel") : TemaUi.k("kitab.terkunci", f.i(KitabTerlarang.GENTAR_MIN, 0)),
					Palette.BAHAYA);
			return;
		}
		if (no != lantaiPilih) {
			lantaiPilih = no;
			toko.mulaiUlang();
			kirim(LembarKunci.op(KitabTerlarang.OP_LANTAI, no));
		}
	}

	@Override
	protected void lukisTema(Lukis l, int x0, int y0, int x1, int y1) {
		int y = y0 + strip(l, TemaUi.k("kitab.ucap"), x0, y0, x1, tata == Tata.SEMPIT ? 30 : 32);
		int menaraH = y1 - y;
		int menaraW = Math.round(menaraH * 64.0F / 160.0F);
		menara(l, x0 + 9, y, menaraW, menaraH);
		int rx0 = x0 + 9 + menaraW + 12;
		List<Lembar> semua = new ArrayList<>(lembar.daftar(KitabTerlarang.GULUNGAN));
		semua.addAll(lembar.daftar(KitabTerlarang.GIOK));
		Lembar f = lantai(lantaiPilih);
		int hy = y;
		if (f != null) {
			hy += subjudul(l, TemaUi.k("kitab.lantai_judul", lantaiPilih, f.teks(LembarKunci.NAMA)), rx0, hy, x1);
		}
		Kotak[] k = bagi(rx0, hy, x1, y1);
		int pita = lembar.i(LembarKunci.PITA, -1);
		toko(l, "kitab", toko, semua, k[0], k[1], KitabTerlarang.OP_BELI, false, (lk, e, ex0, ey, ex1) -> {
			int h = 0;
			Component efek = e.teksAtau(KitabTerlarang.EFEK);
			if (efek != null) {
				TemaUi.ikon(lk.g, TemaUi.I_GULUNGAN, ex0, ey, 10);
				h += Math.round(lk.bungkus(efek, ex0 + 12, ey + 1, ex1 - ex0 - 12, aksen(), 27)) + 3;
			}
			int infami = e.i(KitabTerlarang.INFAMI, 0);
			if (infami != 0) {
				TemaUi.ikon(lk.g, TemaUi.I_INFAMI, ex0, ey + h - 1, 10);
				lk.teks(TemaUi.k("kitab.infami", delta(infami)), ex0 + 12, ey + h, ex1 - ex0 - 12, gaya.teksLemah);
				h += 11;
			}
			int min = e.i(PITA_MIN, 0);
			if (min > 0) {
				lk.teks(TemaUi.k("kitab.pita_min", min), ex0, ey + h, ex1 - ex0, pita < 0 || pita >= min ? gaya.teksSamar : lk.gaya.turun());
				h += 10;
			}
			return h;
		}, null, TemaUi.k(f == null ? "kitab.pilih_lantai" : "kitab.rak_kosong"));
	}

	/** The tower: spire 0-16 px of the picture, then floors 9 (top) .. 1 (bottom), 16 px each; numbers at its left. */
	private void menara(Lukis l, int x, int y, int w, int h) {
		TemaUi.cahaya(l, x + w / 2, y + h / 2, Math.round(w * 0.9F), Palette.argb(0x30, 0x8A1020));
		TemaUi.menara(l.g, x, y, h);
		float s = h / 160.0F;
		boolean nomor = 16 * s >= 9.0F;
		for (int no = 1; no <= 9; no++) {
			Lembar f = lantai(no);
			int fy0 = y + Math.round((16 + (9 - no) * 16) * s);
			int fy1 = y + Math.round((16 + (10 - no) * 16) * s);
			boolean buka = f != null && f.b(KitabTerlarang.TERBUKA);
			boolean segel = f != null && f.b(KitabTerlarang.SEGEL);
			boolean pilih = no == lantaiPilih;
			if (!buka) {
				l.g.fill(x + 2, fy0, x + w - 2, fy1, 0x90000000);
				if (segel) {
					int sw = Math.max(6, Math.round(8 * s));
					l.g.fill(x + w / 2 - sw / 2, fy0 + 1, x + w / 2 + sw / 2, fy1 - 1, 0xFFE8C860);
					l.g.fill(x + w / 2 - 1, fy0 + 2, x + w / 2 + 1, fy1 - 2, 0xFFB3281D);
				} else {
					int r = Math.min(10, fy1 - fy0);
					TemaUi.ikon(l.g, TemaUi.I_RANTAI, x + w / 2 - r / 2, fy0 + (fy1 - fy0 - r) / 2, r, 0xD0FFFFFF);
				}
			} else {
				float nyala = 0.4F + 0.3F * (float) Math.sin(l.kini / 300.0D + no);
				l.g.fill(x + 3, fy0 + 1, x + w - 3, fy1 - 1, Palette.argb(Math.round(0x30 * nyala), 0xFFC060));
			}
			if (pilih) {
				Brush.frame(l.g, x, fy0, x + w, fy1, Palette.GOLD_LIGHT);
				Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.PANAH_KIRI, x + w + 1, fy0 + (fy1 - fy0) / 2 - 4, 8, 0xFFFFFFFF);
			}
			if (nomor) {
				Brush.text(l.g, font, Component.literal(Integer.toString(no)), x - 8, fy0 + (fy1 - fy0) / 2.0F - 4, buka ? 0xFFF7DC7E : 0x90A0A0A0, 1.0F,
						true);
			}
			if (f != null) {
				final Lembar ff = f;
				l.zona("lantai" + no, x, fy0, x + w, fy1, () -> pilihLantai(ff), true, null, true);
				l.fokusCincin("lantai" + no, x, fy0, x + w, fy1);
				if (l.di(x, fy0, x + w, fy1)) {
					List<Component> tip = new ArrayList<>();
					tip.add(Brush.bold(TemaUi.k("kitab.lantai_judul", no, f.teks(LembarKunci.NAMA))));
					tip.add(segel ? TemaUi.k("kitab.segel") : buka ? TemaUi.k("kitab.terbuka") : TemaUi.k("kitab.terkunci", f.i(KitabTerlarang.GENTAR_MIN,
							0)));
					if (!buka && !segel && gentar >= 0) {
						tip.add(TemaUi.k("kitab.gentarmu", gentar));
					}
					l.tip(tip);
				}
			}
		}
	}
}
