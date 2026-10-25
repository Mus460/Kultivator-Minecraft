package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.dunia.kota42.ui.layar.SketsaKota;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.CincinWaktu;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.KeadaanTerkunci;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.PasarHantu;
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
 * player's Gentar and Infami meters; the goods are the kit's one shop composition, each with its band and a "dark
 * origin" mark (such goods are confiscated by righteous guards). An intruder ({@code penyusup}) is not served: the whole
 * page locks.
 * <p>
 * The one black-market page of the mod (jadwal 41/42 0.1 "No GUI twice"): the DESIGN 42.11.10 Ghost Market
 * ({@code pasar_hantu}, keys {@link KotaLembar.PasarHantu}) opens here as a second mode — darker cloth with jade-green
 * accents, a moon, the spot and a dawn countdown ring, the mask and raid-tension chips, the masked seller's whisper; tabs
 * Lapak (tonight's wares, the shop composition), Curian (stolen goods to redeem, the player's own in gold) and Penadah
 * (the fence buys from the bag, +1 infamy each). A Di Sini page on the single kit (43.8.3).
 */
public final class LayarPasarGelap extends TemaHalaman {

	/** Entry key of {@code barang_d} (C38.T3: pita_min I). */
	private static final String PITA_MIN = "pita_min";

	private final Toko toko = new Toko();
	private final DaftarGulir hantuDaftar = new DaftarGulir();

	public LayarPasarGelap(int npcId, CompoundTag lembar) {
		this(PasarGelap.LAYAR, npcId, lembar);
	}

	/** The black-market page for the 38.T id or the 42 Ghost Market id ({@code pasar_hantu}). */
	public LayarPasarGelap(String layar, int npcId, CompoundTag lembar) {
		super(layar, npcId, lembar, "layar.pasar_gelap", TempatWarna.Gaya.KAIN);
	}

	private boolean hantu() {
		return PasarHantu.LAYAR.equals(layar);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		if (hantu()) {
			return List.of(new TabLentera.Tab(PasarHantu.TAB_LAPAK, SketsaKota.t("pasar_hantu.tab.lapak"), LembarKunci.Ikon.JIMAT, null),
					new TabLentera.Tab(PasarHantu.TAB_CURIAN, SketsaKota.t("pasar_hantu.tab.curian"), LembarKunci.Ikon.GEMBOK, null),
					new TabLentera.Tab(PasarHantu.TAB_PENADAH, SketsaKota.t("pasar_hantu.tab.penadah"), LembarKunci.Ikon.TIMBANGAN, null));
		}
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
		if (hantu()) {
			return SketsaKota.t("pasar_hantu.petunjuk");
		}
		return lembar.b(PasarGelap.PENYUSUP) ? null : tt("kunci.jumlah");
	}

	@Override
	protected void tabGanti() {
		toko.mulaiUlang();
		hantuDaftar.mulaiUlang(Util.getMillis());
	}

	@Override
	protected void lukisTema(Lukis l, int x0, int y0, int x1, int y1) {
		if (hantu()) {
			hantu(l, x0, y0, x1, y1);
			return;
		}
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
				lk.teks(TemaUi.k("gelap.pita_min", min), ex0, ey, ex1 - ex0, cukup ? gaya.teksSamar : lk.gaya.turun());
				h += 10;
			}
			if (e.b(PasarGelap.GELAP)) {
				int cw = TemaUi.chip(lk, TemaUi.I_TOPENG, TemaUi.k("gelap.asal_gelap"), ex0, ey + h, 0xFF5A1020);
				if (lk.di(ex0, ey + h, ex0 + cw, ey + h + 12)) {
					lk.tip(List.of(TemaUi.k("gelap.asal_gelap"), TemaUi.k("gelap.asal_gelap_tip")));
				}
				h += 14;
			}
			return h;
		}, null, TemaUi.k("gelap.kosong"));
	}

	/** Markup chip with its reason, then the Gentar and Infami meters with their numbers. Returns the height used. */
	private int kepala(Lukis l, int x0, int y, int x1) {
		float markup = lembar.f(PasarGelap.MARKUP, 1.3F);
		int gentar = lembar.i(PasarGelap.GENTAR, 0);
		int infami = lembar.i(PasarGelap.INFAMI, 0);
		Component alasan = TemaUi.k(markup <= 1.1F + 0.001F ? "gelap.markup.tamu" : markup <= 1.15F + 0.001F ? "gelap.markup.infami"
				: markup <= 1.2F + 0.001F ? "gelap.markup.gentar" : "gelap.markup.biasa");
		String angka = String.format(Locale.ROOT, Math.round(markup * 100.0F) % 10 == 0 ? "%.1f" : "%.2f", markup);
		int w = TemaUi.chip(l, TemaUi.I_KOIN_DARAH, TemaUi.k("gelap.markup", "×" + angka), x0, y, TemaUi.DARAH);
		l.samar(alasan, x0 + w + 4, y + 2, x1 - x0 - w - 4);
		if (l.di(x0, y, x0 + w, y + 12)) {
			l.tip(List.of(TemaUi.k("gelap.markup_tip.1"), TemaUi.k("gelap.markup_tip.2"), TemaUi.k("gelap.markup_tip.3"),
					TemaUi.k("gelap.markup_tip.4")));
		}
		int my = y + 14;
		int mw = (x1 - x0 - 6) / 2;
		meter(l, TemaUi.I_GENTAR, gentar, 0xFFC04030, 0xFFFF9A8A, x0, my, x0 + mw, List.of(TemaUi.k("gelap.gentar", gentar), TemaUi.k("gelap.gentar_tip")));
		meter(l, TemaUi.I_INFAMI, infami, 0xFF8A40A0, 0xFFD8A8F0, x0 + mw + 6, my, x1,
				List.of(TemaUi.k("gelap.infami", infami), TemaUi.k("gelap.infami_tip")));
		return 27;
	}

	/** One reputation meter (icon, bar with the 30 mark, the number at its right). */
	private static void meter(Lukis l, int ikon, int nilai, int warna, int warnaAngka, int x0, int y, int x1, List<Component> tip) {
		Component n = Component.literal(Integer.toString(nilai));
		TemaUi.meter(l, ikon, x0, y + 1, x1 - l.font.width(n) - 3, nilai / 100.0F, warna, 0.3F);
		l.kanan(n, x1, y, warnaAngka);
		if (l.di(x0, y, x1, y + 10)) {
			l.tip(tip);
		}
	}

	// =================================================================== 42 Pasar Hantu

	private void hantu(Lukis l, int x0, int y0, int x1, int y1) {
		// the night cloth: darker than the Kota Iblis market, a faint green mist rising from the bottom
		l.g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, Palette.argb(0x70, 0x05080A));
		float kabut = 0.6F + 0.4F * (float) Math.sin(l.kini / 900.0D);
		l.g.fillGradient(x0, y1 - 40, x1, y1, 0x00000000, Palette.argb(Math.round(0x38 * kabut), SketsaKota.HIJAU & 0xFFFFFF));
		Lembar pasar = lembar.sub(PasarHantu.PASAR);
		int y = y0;
		// a crescent moon, the spot, the dawn countdown
		Brush.disc(l.g, x0 + 7, y + 7, 6.0F, 0xFFE8EED8);
		Brush.disc(l.g, x0 + 10, y + 5, 5.0F, 0xFF0A0E12);
		long bubar = pasar.l(PasarHantu.BUBAR_TICK, 0L);
		int kanan = 0;
		if (bubar > 0L) {
			long sisa = CincinWaktu.gambar(l, x1 - 9, y + 7, 7.0F, bubar, 4000L, diterima, CincinWaktu.TANPA);
			Component sb = SketsaKota.t("pasar_hantu.bubar", Brush.waktu(sisa));
			kanan = Math.min((x1 - x0) / 2, font.width(sb) + 22);
			l.teks(sb, x1 - kanan, y + 3, kanan - 20, 0xFFB8D8C0);
		}
		l.teks(Brush.bold(SketsaKota.t("pasar_hantu.di", pasar.teks(PasarHantu.LOKASI))), x0 + 17, y + 3, x1 - x0 - 21 - kanan, SketsaKota.HIJAU);
		y += 16;
		// chips: mask, raid tension, password
		int cx = x0;
		boolean topeng = pasar.b(PasarHantu.TOPENG);
		cx += TemaUi.chip(l, TemaUi.I_TOPENG, SketsaKota.t(topeng ? "pasar_hantu.topeng_ya" : "pasar_hantu.topeng_tidak"), cx, y,
				topeng ? Palette.darken(SketsaKota.HIJAU, 0.2F) : 0xFF5A1020) + 4;
		if (l.di(x0, y, cx, y + 12)) {
			l.tip(SketsaKota.t("pasar_hantu.topeng_tip"));
		}
		int razia = pasar.i(PasarHantu.RAZIA, 0);
		int rx = cx;
		int rw = Math.min(90, x1 - cx - 4);
		if (rw > 30) {
			TemaUi.meter(l, TemaUi.I_GENTAR, cx, y + 2, cx + rw, razia / 100.0F, razia >= 60 ? 0xFFC04030 : 0xFFD0A040, 0.6F);
			if (l.di(rx, y, rx + rw, y + 12)) {
				l.tip(List.of(SketsaKota.t("pasar_hantu.razia", razia), SketsaKota.t("pasar_hantu.razia_tip")));
			}
			cx += rw + 6;
		}
		if (pasar.b(PasarHantu.SANDI) && cx + 50 < x1) {
			TemaUi.chip(l, TemaUi.I_SURAT, SketsaKota.t("pasar_hantu.sandi"), cx, y, 0xFF2E5A40);
		}
		y += 15;
		// the masked seller's whisper
		Lembar penjual = lembar.sub(PasarHantu.PENJUAL);
		if (!penjual.kosongSemua()) {
			Component nama = penjual.teks(LembarKunci.NAMA);
			int nw = Math.min(90, font.width(nama) + 4);
			l.teks(nama, x0, y, nw, 0xFF8AB89A);
			Component bisik = SketsaKota.t("pasar_hantu.kutip", penjual.teks(PasarHantu.BISIK)).withStyle(net.minecraft.ChatFormatting.ITALIC);
			l.teks(bisik, x0 + nw + 2, y, x1 - x0 - nw - 2, 0xFFD8E8D8);
			y += 12;
		}
		Kotak isi = new Kotak(x0, y, x1, y1);
		Lembar kosong = lembar.sub(LembarKunci.KOSONG);
		switch (tab) {
			case PasarHantu.TAB_CURIAN -> curian(l, isi);
			case PasarHantu.TAB_PENADAH -> penadah(l, isi);
			default -> {
				Kotak[] k = bagi(isi.x0(), isi.y0(), isi.x1(), isi.y1());
				Component tutup = kosong.kosongSemua() ? null : kosong.teks(LembarKunci.TEKS);
				toko(l, "hantu", toko, lembar.daftar(LembarKunci.DAFTAR), k[0], k[1], PasarHantu.OP_BELI, false, (lk, e, ex0, ey, ex1) -> {
					int h = 0;
					if (e.b(LembarKunci.LANGKA)) {
						TemaUi.chip(lk, TemaUi.I_TERBAIK, SketsaKota.t("pasar_hantu.langka"), ex0, ey, 0xFF8A6A1A);
						h += 14;
					}
					lk.teks(SketsaKota.t("pasar_hantu.lapak_ke", e.i(PasarHantu.LAPAK, 0) + 1), ex0, ey + h, ex1 - ex0, 0xFF8AB89A);
					return h + 10;
				}, tutup, SketsaKota.t("pasar_hantu.lapak_kosong"));
			}
		}
	}

	private void curian(Lukis l, Kotak k) {
		List<Lembar> rows = lembar.daftar(PasarHantu.CURIAN);
		if (rows.isEmpty()) {
			kosong(l, SketsaKota.t("pasar_hantu.curian_kosong"), k.x0(), k.y0(), k.x1(), k.y1());
			return;
		}
		hantuDaftar.gambar(l, "curian", k.x0(), k.y0(), k.x1(), k.y1(), rows.size(), 24, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			boolean milik = e.b(PasarHantu.MILIKMU);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			if (milik) {
				lk.g.fill(rx0, ry0, rx0 + 2, ry1, Palette.GOLD);
			}
			lk.g.item(e.barang(), rx0 + 4, ry0 + 4);
			long tebus = e.l(PasarHantu.TEBUS, 0L);
			Component label = SketsaKota.t("pasar_hantu.tebus", Brush.angka(tebus));
			int bw = Math.min(90, Math.max(64, font.width(label) + 14));
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 24, ry0 + 2, rx1 - rx0 - 30 - bw, milik ? Palette.GOLD_LIGHT : gaya.teks);
			lk.teks(SketsaKota.t(milik ? "pasar_hantu.curian_milikmu" : "pasar_hantu.curian_hari", e.l(LembarKunci.HARI, 0L)), rx0 + 24, ry0 + 12,
					rx1 - rx0 - 30 - bw, milik ? Palette.GOLD : gaya.teksSamar);
			tombolOp(lk, "tebus" + i, rx1 - bw - 3, ry0 + 5, bw, 14, label, mampu(tebus), kurang(tebus), LembarKunci.op(PasarHantu.OP_TEBUS, e.id()));
		}, null, null);
	}

	private void penadah(Lukis l, Kotak k) {
		List<Lembar> rows = lembar.daftar(PasarHantu.PENADAH);
		l.teks(SketsaKota.t("pasar_hantu.penadah_info"), k.x0(), k.y0(), k.w(), 0xFF8AB89A);
		int y0 = k.y0() + 12;
		if (rows.isEmpty()) {
			kosong(l, SketsaKota.t("pasar_hantu.penadah_kosong"), k.x0(), y0, k.x1(), k.y1());
			return;
		}
		hantuDaftar.gambar(l, "penadah", k.x0(), y0, k.x1(), k.y1(), rows.size(), 24, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			lk.g.item(e.barang(), rx0 + 3, ry0 + 4);
			int bw = 56;
			int pw = LabelHarga.gambar(lk, e.l(LembarKunci.HARGA, 0L), -1L, rx1 - bw - 8, ry0 + 3, true, false);
			Component nama = e.teks(LembarKunci.NAMA).copy().append(" ×" + e.i(LembarKunci.JUMLAH, 1));
			lk.teks(nama, rx0 + 23, ry0 + 2, rx1 - rx0 - 35 - bw - pw, gaya.teks);
			lk.teks(SketsaKota.t("pasar_hantu.infami", e.i(PasarHantu.INFAMI, 1)), rx0 + 23, ry0 + 12, rx1 - rx0 - 31 - bw, 0xFFD8A8F0);
			tombolOp(lk, "jual" + i, rx1 - bw - 3, ry0 + 5, bw, 14, SketsaKota.t("pasar_hantu.jual"), true, null,
					LembarKunci.op(PasarHantu.OP_JUAL, e.i(LembarKunci.SLOT, -1)));
		}, null, null);
	}
}
