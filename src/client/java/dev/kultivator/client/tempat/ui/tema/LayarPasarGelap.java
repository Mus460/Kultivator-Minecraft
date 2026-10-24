package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.KeadaanTerkunci;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.PasarGelap;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code tema_pasar_gelap} (DESIGN 38.T.7.6): Pasar Seribu Bayangan, the black market under the Kota Iblis. Black cloth,
 * red lantern light flickering over the counter and six stalls as tabs (poisons, pills, beasts, maps, masks, stolen
 * goods). The header row shows the market's markup on every price and why (Gentar, Infami, Tamu Darah) next to the
 * player's Gentar and Infami meters; the goods are the shared shop block, each with its band and a "dark origin" mark
 * (such goods are confiscated by righteous guards). An intruder ({@code penyusup}) is not served: the whole screen locks.
 */
public final class LayarPasarGelap extends LayarTemaDasar {

	/** Entry key of {@code barang_d} (C38.T3: pita_min I). */
	private static final String PITA_MIN = "pita_min";

	private final Toko toko = new Toko();

	public LayarPasarGelap(int npcId, CompoundTag lembar) {
		super(PasarGelap.LAYAR, npcId, lembar, "layar.pasar_gelap", TempatWarna.Gaya.KAIN);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		List<TabLentera.Tab> out = new ArrayList<>();
		int[] ikon = {LembarKunci.Ikon.CAP_MERAH, LembarKunci.Ikon.PIL, LembarKunci.Ikon.PASAR_BINATANG, LembarKunci.Ikon.MATA_BATIN,
				LembarKunci.Ikon.JIMAT, LembarKunci.Ikon.GEMBOK};
		for (int i = 0; i < PasarGelap.TABS.size(); i++) {
			String id = PasarGelap.TABS.get(i);
			out.add(tabTema(id, "gelap.tab." + id, ikon[i]));
		}
		return out;
	}

	@Override
	protected Component petunjuk() {
		return lembar.b(PasarGelap.PENYUSUP) ? null : TeksTempat.k("kunci.jumlah");
	}

	@Override
	protected void tabGanti() {
		toko.mulaiUlang();
	}

	@Override
	protected void gambar(Lukis l, int x0, int y0, int x1, int y1) {
		// lantern light on the black cloth
		float kedip = 0.75F + 0.25F * (float) Math.sin(l.kini / 140.0D) * (float) Math.sin(l.kini / 61.0D);
		l.g.fillGradient(x0, y0, x1, y0 + 30, Palette.argb(Math.round(0x40 * kedip), 0xB0202A), 0x00000000);
		if (lembar.b(PasarGelap.PENYUSUP)) {
			KeadaanTerkunci.gambar(l, TemaUi.k("gelap.penyusup"), TemaUi.k("gelap.penyusup_syarat"), x0, y0, x1, y1, Util.getMillis() - diterima);
			return;
		}
		int y = y0;
		y += kepala(l, x0, y, x1);
		Kotak[] k = bagi(x0, y, x1, y1);
		int pita = lembar.i(LembarKunci.PITA, -1);
		toko(l, "gelap", toko, lembar.daftar(PasarGelap.BARANG_DAFTAR), k[0], k[1], PasarGelap.OP_BELI, true, (lk, e, ex0, ey, ex1) -> {
			int h = 0;
			int min = e.i(PITA_MIN, 0);
			if (min > 0) {
				boolean cukup = pita < 0 || pita >= min;
				lk.keterangan(TemaUi.k("gelap.pita_min", min), ex0, ey, ex1 - ex0, cukup ? gaya.teksSamar : 0xFFFF8A7A);
				h += 10;
			}
			if (e.b(PasarGelap.GELAP)) {
				TemaUi.chip(lk, TemaUi.I_TOPENG, TemaUi.k("gelap.asal_gelap"), ex0, ey + h, 0xFF5A1020);
				if (lk.di(ex0, ey + h, ex0 + 90, ey + h + 11)) {
					lk.tip(List.of(TemaUi.k("gelap.asal_gelap"), TemaUi.k("gelap.asal_gelap_tip")));
				}
				h += 13;
			}
			return h;
		}, null, TemaUi.k("gelap.kosong"));
	}

	/** Markup chip with its reason, then the Gentar and Infami meters. Returns the height used. */
	private int kepala(Lukis l, int x0, int y, int x1) {
		float markup = lembar.f(PasarGelap.MARKUP, 1.3F);
		int gentar = lembar.i(PasarGelap.GENTAR, 0);
		int infami = lembar.i(PasarGelap.INFAMI, 0);
		Component alasan = TemaUi.k(markup <= 1.1F + 0.001F ? "gelap.markup.tamu" : markup <= 1.15F + 0.001F ? "gelap.markup.infami"
				: markup <= 1.2F + 0.001F ? "gelap.markup.gentar" : "gelap.markup.biasa");
		String angka = String.format(Locale.ROOT, Math.round(markup * 100.0F) % 10 == 0 ? "%.1f" : "%.2f", markup);
		int w = TemaUi.chip(l, TemaUi.I_KOIN_DARAH, TemaUi.k("gelap.markup", "×" + angka), x0, y, TemaUi.DARAH);
		l.keterangan(alasan, x0 + w + 4, y + 2, Math.max(20, (x1 - x0) / 2 - w - 8), gaya.teksSamar);
		if (l.di(x0, y, x0 + w, y + 11)) {
			l.tip(List.of(TemaUi.k("gelap.markup_tip.1"), TemaUi.k("gelap.markup_tip.2"), TemaUi.k("gelap.markup_tip.3"),
					TemaUi.k("gelap.markup_tip.4")));
		}
		int mx0 = x0 + (x1 - x0) / 2 + 4;
		int mw = (x1 - mx0 - 6) / 2;
		TemaUi.meter(l, TemaUi.I_GENTAR, mx0, y + 2, mx0 + mw, gentar / 100.0F, 0xFFC04030, 0.3F);
		TemaUi.meter(l, TemaUi.I_INFAMI, mx0 + mw + 6, y + 2, x1, infami / 100.0F, 0xFF8A40A0, 0.3F);
		if (l.di(mx0, y, mx0 + mw, y + 11)) {
			l.tip(List.of(TemaUi.k("gelap.gentar", gentar), TemaUi.k("gelap.gentar_tip")));
		}
		if (l.di(mx0 + mw + 6, y, x1, y + 11)) {
			l.tip(List.of(TemaUi.k("gelap.infami", infami), TemaUi.k("gelap.infami_tip")));
		}
		Brush.text(l.g, font, Component.literal(Integer.toString(gentar)), mx0 + mw - 10, y + 11, 0xFFFF9A8A, 0.55F, false);
		Brush.text(l.g, font, Component.literal(Integer.toString(infami)), x1 - 10, y + 11, 0xFFD8A8F0, 0.55F, false);
		return 20;
	}
}
