package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.KeadaanKosong;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.PemilihJumlah;
import dev.kultivator.client.tempat.ui.kit.Pratinjau3D;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Dagang;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * {@code tema_dagang}, the Buku Dagang (DESIGN 38.T.10.1, wireframe of 38.T.13.3; hub card "Kota Bertema" and
 * {@code /kultivasi dagang}): a ledger on paper, read only.
 * <ul>
 * <li><b>Harga</b>: goods × known towns. Each cell is the last price seen (one decimal), ■ marks the town of origin, ▲ the
 * best place to sell, grey = older than 3 days, a red tint = saturated (jenuh). Columns page with ←/→ when they do not
 * fit; the selected row's best route is spelled out under the table.</li>
 * <li><b>Laba</b>: this week's profit against the weekly cap, the last seven days as bars, Infami and Gentar.</li>
 * <li><b>Kontrak</b>: running cargo contracts (crates, destination, days left, deposit and pay; sky ships pay × 1.3).</li>
 * <li><b>Pasar</b> (market mode only, first tab when present: a theme's market NPC or counter opened the book through
 * {@code DagangLayar.bukaPasar}): a bazaar stall under an awning with three ledgers, Beli (the town's own goods, unit
 * price and stock left of 16 today), Jual (goods carried, next-unit price, saturation, locked rows with their reason) and
 * Segel (8 units into one Peti Dagang for 1 NB); the detail side holds the turning preview, an amount picker and the
 * action button (ops {@code beli:ID:N}, {@code jual:ID:N}, {@code segel:ID}).</li>
 * </ul>
 */
public final class LayarBukuDagang extends LayarTemaDasar {

	private static final int KOL = 46;
	private static final int NAMA_KOL = 92;

	private final DaftarGulir daftar = new DaftarGulir();
	private final DaftarGulir daftarKontrak = new DaftarGulir();
	private int kolomAwal;
	private final Map<String, Integer> urutKota = new HashMap<>();
	private List<Lembar> kota = new ArrayList<>();
	private List<Lembar> harga = new ArrayList<>();
	// market mode
	private static final int BELI = 0;
	private static final int JUAL = 1;
	private static final int SEGEL = 2;
	private final DaftarGulir daftarPasar = new DaftarGulir();
	private final Pratinjau3D pratinjau = new Pratinjau3D();
	private final PemilihJumlah jumlah = new PemilihJumlah();
	private int segmen;
	private final List<List<Lembar>> pasar = new ArrayList<>(List.of(List.of(), List.of(), List.of()));

	public LayarBukuDagang(int npcId, CompoundTag lembar) {
		super(Dagang.LAYAR, npcId, lembar, "layar.dagang", TempatWarna.Gaya.KERTAS);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(tabTema(Dagang.PASAR, "dagang.tab.pasar", LembarKunci.Ikon.KAFILAH),
				tabTema(Dagang.TAB_HARGA, "dagang.tab.harga", LembarKunci.Ikon.TIMBANGAN),
				tabTema(Dagang.TAB_LABA, "dagang.tab.laba", LembarKunci.Ikon.TREN_NAIK),
				tabTema(Dagang.TAB_KONTRAK, "dagang.tab.kontrak", IkonTempat.SURAT));
	}

	/** The market tab exists only while the sheet carries a market block (client-only tab; the server just stores it). */
	@Override
	protected boolean tabAda(String id) {
		return !Dagang.PASAR.equals(id) || lembar.ada(Dagang.PASAR);
	}

	@Override
	protected KeadaanKosong.Gambar gambarKosong() {
		return KeadaanKosong.Gambar.GULUNGAN;
	}

	@Override
	protected int panelMaks(Tata t) {
		return t == Tata.LEBAR ? 620 : 470;
	}

	@Override
	protected @Nullable Component petunjuk() {
		return Dagang.TAB_HARGA.equals(tab) ? TemaUi.k("dagang.kunci") : Dagang.PASAR.equals(tab) ? TemaUi.k("dagang.pasar.kunci") : null;
	}

	@Override
	protected void lembarBaru(boolean buka) {
		kota = new ArrayList<>(new Lembar(lembar.tag()).daftar(Dagang.KOTA));
		harga = new ArrayList<>(new Lembar(lembar.tag()).daftar(Dagang.HARGA));
		urutKota.clear();
		for (int i = 0; i < kota.size(); i++) {
			urutKota.put(kota.get(i).id(), i);
		}
		// keep the selected good across refreshes (a sale can empty a row and shift the list)
		List<Lembar> lama = pasar.get(segmen);
		int sel = daftarPasar.pilihan();
		String pilihId = sel >= 0 && sel < lama.size() ? lama.get(sel).id() : null;
		Lembar p = lembar.sub(Dagang.PASAR);
		pasar.set(BELI, new ArrayList<>(p.daftar(Dagang.BELI)));
		pasar.set(JUAL, new ArrayList<>(p.daftar(Dagang.JUAL)));
		pasar.set(SEGEL, new ArrayList<>(p.daftar(Dagang.SEGEL)));
		if (buka) {
			// a trader who has nothing to sell you today opens on your own pack
			segmen = pasar.get(BELI).isEmpty() && !pasar.get(JUAL).isEmpty() ? JUAL : BELI;
			daftarPasar.mulaiUlang(Util.getMillis());
			jumlah.set(1);
		} else if (pilihId != null) {
			List<Lembar> baru = pasar.get(segmen);
			for (int i = 0; i < baru.size(); i++) {
				if (baru.get(i).id().equals(pilihId)) {
					daftarPasar.setPilih(i);
					break;
				}
			}
		}
	}

	@Override
	protected void gambar(Lukis l, int x0, int y0, int x1, int y1) {
		if (Dagang.PASAR.equals(tab) && lembar.ada(Dagang.PASAR)) {
			pasar(l, x0, y0, x1, y1);
			return;
		}
		if (kota.isEmpty()) {
			kosong(l, TemaUi.k("dagang.kosong"), x0, y0, x1, y1);
			return;
		}
		switch (tab) {
			case Dagang.TAB_LABA -> laba(l, x0, y0, x1, y1);
			case Dagang.TAB_KONTRAK -> kontrak(l, x0, y0, x1, y1);
			default -> tabel(l, x0, y0, x1, y1);
		}
	}

	// ------------------------------------------------------------------------------------------------ prices

	private int kolomMuat(int x0, int x1) {
		return Math.max(1, (x1 - x0 - NAMA_KOL - 6) / KOL);
	}

	private void geserKolom(int arah, int muat) {
		int baru = Math.max(0, Math.min(Math.max(0, kota.size() - muat), kolomAwal + arah));
		if (baru != kolomAwal) {
			kolomAwal = baru;
			UiSound.tab();
		} else {
			UiSound.denied();
		}
	}

	private void tabel(Lukis l, int x0, int y0, int x1, int y1) {
		int muat = kolomMuat(x0, x1);
		kolomAwal = Math.max(0, Math.min(kolomAwal, Math.max(0, kota.size() - muat)));
		int akhir = Math.min(kota.size(), kolomAwal + muat);
		// header row: city emblems and names
		int hy = y0;
		l.keterangan(TemaUi.k("dagang.barang"), x0 + 2, hy + 6, NAMA_KOL - 4, gaya.teksLemah);
		for (int c = kolomAwal; c < akhir; c++) {
			Lembar k = kota.get(c);
			int cx = x0 + NAMA_KOL + (c - kolomAwal) * KOL + KOL / 2;
			TemaUi.lambang(l, k.s(Dagang.TEMA), cx, hy + 6, 6);
			Brush.fitCentered(l.g, font, k.teks(LembarKunci.NAMA), cx, hy + 14, KOL - 4, gaya.teks, 0.6F, 0.45F, false);
			if (l.di(cx - KOL / 2, hy, cx + KOL / 2, hy + 20)) {
				List<Component> tip = new ArrayList<>();
				tip.add(k.teks(LembarKunci.NAMA));
				tip.add(TemaUi.k("tema." + k.s(Dagang.TEMA)));
				if (k.ada(LembarKunci.JARAK)) {
					tip.add(TemaUi.k("dagang.jarak", TeksTempat.angka(k.i(LembarKunci.JARAK, 0))));
				}
				l.tip(tip);
			}
		}
		// paging arrows when the columns do not fit
		if (kota.size() > muat) {
			panah(l, "kiri", x1 - 24, hy, IkonTempat.PANAH_KIRI, kolomAwal > 0, () -> geserKolom(-1, muat));
			panah(l, "kanan", x1 - 11, hy, IkonTempat.PANAH_KANAN, akhir < kota.size(), () -> geserKolom(1, muat));
		}
		PanelTempat.garis(l.g, gaya, x0, x1, hy + 21);
		int ty0 = hy + 24;
		int ty1 = y1 - 34;
		if (harga.isEmpty()) {
			kosong(l, TemaUi.k("dagang.harga_kosong"), x0, ty0, x1, y1);
			return;
		}
		daftar.gambar(l, "harga", x0, ty0, x1, ty1, harga.size(), 17, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> baris(lk, harga.get(i), rx0, ry0,
				rx1, ry1, sorot, pilih, akhir), null, i -> {
		});
		Lukis.Zona z = l.zona("tabel", x0, ty0, x0 + NAMA_KOL, ty1, null, true, null, false);
		if (z != null) {
			z.geser = arah -> geserKolom(arah, muat);
		}
		// legend and the selected row's best route
		int ly = ty1 + 3;
		legenda(l, x0, ly);
		int sel = daftar.pilihan();
		if (sel >= 0 && sel < harga.size()) {
			rute(l, harga.get(sel), x0, ly + 11, x1);
		}
	}

	private void panah(Lukis l, String kunci, int x, int y, int ikon, boolean on, Runnable aksi) {
		boolean over = l.di(x, y, x + 11, y + 11);
		l.g.fill(x, y, x + 11, y + 11, Palette.argb(on ? (over ? 0x70 : 0x40) : 0x18, 0x000000));
		IkonTempat.gambar(l.g, ikon, x + 1, y + 1, 9, on ? 0xFFFFFFFF : 0x60FFFFFF);
		l.zona(kunci, x, y, x + 11, y + 11, aksi, on, null, true);
		l.fokusCincin(kunci, x, y, x + 11, y + 11);
	}

	private void baris(Lukis l, Lembar e, int rx0, int ry0, int rx1, int ry1, boolean sorot, boolean pilih, int akhir) {
		PanelTempat.kartu(l.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
		ItemStack item = e.barang();
		if (!item.isEmpty()) {
			l.g.pose().pushMatrix();
			l.g.pose().translate(rx0 + 2, ry0);
			l.g.pose().scale(0.9F, 0.9F);
			l.g.item(item, 0, 0);
			l.g.pose().popMatrix();
		} else {
			TemaUi.ikon(l.g, TemaUi.ikonBarang(e.id()), rx0 + 2, ry0 + 1, 14);
		}
		l.teks(e.teks(LembarKunci.NAMA), rx0 + 19, ry0 + 4, NAMA_KOL - 21, gaya.teks, 0.8F);
		Map<Integer, Lembar> sel = new HashMap<>();
		for (Lembar s : e.daftar(Dagang.SEL)) {
			Integer idx = urutKota.get(s.s(Dagang.KOTA));
			if (idx != null) {
				sel.put(idx, s);
			}
		}
		for (int c = kolomAwal; c < akhir; c++) {
			int cx0 = rx0 + NAMA_KOL + (c - kolomAwal) * KOL;
			Lembar s = sel.get(c);
			if (s == null) {
				Brush.centered(l.g, font, Component.literal("—"), cx0 + KOL / 2.0F, ry0 + 4, TempatWarna.terkunci(gaya.teksSamar), 0.8F, false);
				continue;
			}
			sel(l, s, kota.get(c), cx0, ry0, cx0 + KOL, ry1);
		}
	}

	private void sel(Lukis l, Lembar s, Lembar k, int cx0, int ry0, int cx1, int ry1) {
		boolean tua = s.i(Dagang.UMUR_HARI, 0) > 3;
		float jenuh = s.f(Dagang.JENUH, 1.0F);
		if (jenuh < 0.97F) {
			l.g.fill(cx0 + 1, ry0 + 1, cx1 - 1, ry1, Palette.argb(Math.round(0x70 * (1.0F - jenuh) / 0.4F), 0xB03030));
		}
		int warna = tua ? TempatWarna.terkunci(gaya.teks) : s.b(Dagang.TERBAIK) ? 0xFF2E7A4E : gaya.teks;
		Component nb = Component.literal(TeksTempat.desimal1(s.f(LembarKunci.NB, 0.0F)));
		float w = font.width(nb) * 0.8F;
		float tx = (cx0 + cx1) / 2.0F - w / 2.0F - 3;
		Brush.text(l.g, font, nb, tx, ry0 + 4.5F, warna, 0.8F, false);
		int ix = Math.round(tx + w + 1);
		if (s.b(Dagang.TERBAIK)) {
			TemaUi.ikon(l.g, TemaUi.I_TERBAIK, ix, ry0 + 3, 8, tua ? 0x80FFFFFF : 0xFFFFFFFF);
		} else if (s.b(Dagang.ASAL)) {
			TemaUi.ikon(l.g, TemaUi.I_ASAL, ix, ry0 + 3, 8, tua ? 0x80FFFFFF : 0xFFFFFFFF);
		}
		if (l.di(cx0, ry0, cx1, ry1)) {
			List<Component> tip = new ArrayList<>();
			tip.add(k.teks(LembarKunci.NAMA));
			tip.add(TemaUi.k("dagang.sel_harga", TeksTempat.desimal1(s.f(LembarKunci.NB, 0.0F))));
			int umur = s.i(Dagang.UMUR_HARI, 0);
			tip.add(umur <= 0 ? TemaUi.k("dagang.sel_hari_ini") : TemaUi.k("dagang.sel_umur", umur));
			if (jenuh < 0.97F) {
				tip.add(TemaUi.k("dagang.sel_jenuh", Math.round((1.0F - jenuh) * 100.0F)).withColor(0xFFFF8A7A));
			}
			if (s.b(Dagang.ASAL)) {
				tip.add(TemaUi.k("dagang.sel_asal"));
			}
			if (s.b(Dagang.TERBAIK)) {
				tip.add(TemaUi.k("dagang.sel_terbaik").withColor(TempatWarna.EMAS_TERANG));
			}
			l.tip(tip);
		}
	}

	private void legenda(Lukis l, int x0, int y) {
		int x = x0;
		TemaUi.ikon(l.g, TemaUi.I_ASAL, x, y, 8);
		x += 10;
		l.keterangan(TemaUi.k("dagang.lg_asal"), x, y + 1, 50, gaya.teksSamar);
		x += Math.round(font.width(TemaUi.k("dagang.lg_asal")) * 0.75F) + 8;
		TemaUi.ikon(l.g, TemaUi.I_TERBAIK, x, y, 8);
		x += 10;
		l.keterangan(TemaUi.k("dagang.lg_terbaik"), x, y + 1, 60, gaya.teksSamar);
		x += Math.round(font.width(TemaUi.k("dagang.lg_terbaik")) * 0.75F) + 8;
		l.keterangan(TemaUi.k("dagang.lg_tua"), x, y + 1, 90, TempatWarna.terkunci(gaya.teks));
		x += Math.round(font.width(TemaUi.k("dagang.lg_tua")) * 0.75F) + 8;
		l.g.fill(x, y + 1, x + 8, y + 8, Palette.argb(0x60, 0xB03030));
		l.keterangan(TemaUi.k("dagang.lg_jenuh"), x + 10, y + 1, 60, gaya.teksSamar);
	}

	/** "Buy at A for 36, sell at B for 48.6 (+12.6 a unit)" from the fresh cells of a row. */
	private void rute(Lukis l, Lembar e, int x0, int y, int x1) {
		Lembar murah = null;
		Lembar mahal = null;
		for (Lembar s : e.daftar(Dagang.SEL)) {
			if (s.i(Dagang.UMUR_HARI, 0) > 3) {
				continue;
			}
			float nb = s.f(LembarKunci.NB, 0.0F);
			if (murah == null || nb < murah.f(LembarKunci.NB, 0.0F)) {
				murah = s;
			}
			if (mahal == null || nb > mahal.f(LembarKunci.NB, 0.0F)) {
				mahal = s;
			}
		}
		if (murah == null || mahal == null || murah == mahal) {
			l.keterangan(TemaUi.k("dagang.rute_kurang"), x0, y, x1 - x0, gaya.teksSamar);
			return;
		}
		float beda = mahal.f(LembarKunci.NB, 0.0F) - murah.f(LembarKunci.NB, 0.0F);
		Component a = namaKota(murah.s(Dagang.KOTA));
		Component b = namaKota(mahal.s(Dagang.KOTA));
		Component t = TemaUi.k("dagang.rute", e.teks(LembarKunci.NAMA), a, TeksTempat.desimal1(murah.f(LembarKunci.NB, 0.0F)), b,
				TeksTempat.desimal1(mahal.f(LembarKunci.NB, 0.0F)), TeksTempat.desimal1(beda));
		l.teks(t, x0, y, x1 - x0, beda > 0.0F ? 0xFF2E7A4E : gaya.teksLemah, 0.8F);
	}

	private Component namaKota(String id) {
		Integer i = urutKota.get(id);
		return i == null ? Component.literal(id) : kota.get(i).teks(LembarKunci.NAMA);
	}

	// ------------------------------------------------------------------------------------------------ profit

	private void laba(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar laba = lembar.sub(Dagang.LABA);
		Kotak[] k = bagi(x0, y0, x1, y1);
		Kotak kiri = k[0];
		int x = kiri.x0();
		int y = kiri.y0();
		y += subjudul(l, TemaUi.k("dagang.laba_minggu"), x, y, kiri.x1());
		long minggu = laba.l(Dagang.MINGGU, 0L);
		long batas = Math.max(1L, laba.l(Dagang.BATAS, 150L));
		LabelHarga.gambar(l, minggu, -1L, x, y + 2, false, 1.5F, false);
		l.kanan(TemaUi.k("dagang.dari_batas", TeksTempat.angka(batas)), kiri.x1(), y + 6, gaya.teksLemah, 0.8F);
		y += 18;
		float frac = minggu / (float) batas;
		int warna = frac >= 1.0F ? TempatWarna.BAHAYA : frac > 0.8F ? TempatWarna.PERINGATAN : TempatWarna.OK;
		y += TemaUi.meter(l, -1, x, y, kiri.x1(), frac, warna, -1.0F) + 4;
		if (laba.b(Dagang.LEWAT)) {
			y += pita(l, LembarKunci.Ikon.TIMBANGAN, TemaUi.k("dagang.lewat"), x, y, kiri.x1(), TempatWarna.BAHAYA);
		}
		l.bungkus(TemaUi.k("dagang.batas_jelas"), x, y, kiri.w(), gaya.teksSamar, 0.7F);
		y += 26;
		// lifetime profit toward the "Saudagar Lima Kota" advancement
		long total = laba.l(Dagang.TOTAL, 0L);
		y += barisNilai(l, TemaUi.k("dagang.laba_total"), TeksTempat.nb(total, false), x, y, kiri.x1());
		if (total < SAUDAGAR) {
			TemaUi.meter(l, TemaUi.I_TERBAIK, x, y, kiri.x1(), total / (float) SAUDAGAR, TempatWarna.EMAS_TERANG, -1.0F);
			if (l.di(x, y - 1, kiri.x1(), y + 9)) {
				l.tip(TemaUi.k("dagang.saudagar_tip", TeksTempat.angka(SAUDAGAR - Math.max(0L, total))));
			}
			y += 12;
		} else {
			TemaUi.chip(l, TemaUi.I_TERBAIK, TemaUi.k("dagang.saudagar"), x, y, TempatWarna.EMAS_TERANG);
			y += 14;
		}
		// seven-day bars
		int[] hari = laba.ia(Dagang.HARI_LABA);
		if (hari.length > 0 && y + 40 < kiri.y1()) {
			y += subjudul(l, TemaUi.k("dagang.tujuh_hari"), x, y, kiri.x1());
			int maks = 1;
			for (int h : hari) {
				maks = Math.max(maks, Math.abs(h));
			}
			int bh = Math.min(48, kiri.y1() - y - 12);
			int n = hari.length;
			int bw = Math.max(4, (kiri.w() - (n - 1) * 3) / n);
			int dasar = y + bh;
			l.g.fill(x, dasar, kiri.x1(), dasar + 1, Palette.argb(0x80, gaya.teksSamar & 0xFFFFFF));
			for (int i = 0; i < n; i++) {
				int bx = x + i * (bw + 3);
				int tinggi = Math.round(bh * Math.abs(hari[i]) / (float) maks);
				int c = hari[i] >= 0 ? 0xFF4F9A6A : TempatWarna.BAHAYA;
				l.g.fillGradient(bx, dasar - tinggi, bx + bw, dasar, Palette.lighten(c, 0.2F), c);
				Brush.centered(l.g, font, Component.literal(i == n - 1 ? TemaUi.k("dagang.hari_ini").getString() : "−" + (n - 1 - i)), bx + bw / 2.0F,
						dasar + 2, gaya.teksSamar, 0.55F, false);
				if (l.di(bx, dasar - bh, bx + bw, dasar)) {
					l.tip(TemaUi.k("dagang.bar_tip", TeksTempat.bertanda(hari[i])));
				}
			}
		}
		// right: standing in the dark world
		Kotak kanan = k[1];
		PanelTempat.kartu(l.g, gaya, kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1(), 0.0F, false);
		int rx0 = kanan.x0() + 5;
		int rx1 = kanan.x1() - 5;
		int ry = kanan.y0() + 4;
		ry += subjudul(l, TemaUi.k("dagang.nama_gelap"), rx0, ry, rx1);
		int infami = lembar.i(Dagang.INFAMI, 0);
		l.keterangan(TemaUi.k("dagang.infami", infami), rx0, ry, rx1 - rx0, gaya.teksLemah);
		ry += 9;
		ry += TemaUi.meter(l, TemaUi.I_INFAMI, rx0, ry, rx1, infami / 100.0F, TemaUi.DARAH, 0.3F) + 3;
		l.bungkus(TemaUi.k(infami >= 60 ? "dagang.infami_tinggi" : infami >= 30 ? "dagang.infami_sedang" : "dagang.infami_rendah"), rx0, ry, rx1 - rx0,
				gaya.teksSamar, 0.7F);
		ry += 24;
		List<Lembar> gentar = lembar.daftar(Dagang.GENTAR);
		if (!gentar.isEmpty()) {
			ry += subjudul(l, TemaUi.k("dagang.gentar"), rx0, ry, rx1);
			for (Lembar g : gentar) {
				if (ry + 12 > kanan.y1()) {
					break;
				}
				int n = g.i(LembarKunci.NILAI, 0);
				l.teks(g.teks(Dagang.KOTA), rx0, ry, (rx1 - rx0) / 2, gaya.teks, 0.75F);
				TemaUi.meter(l, TemaUi.I_GENTAR, rx0 + (rx1 - rx0) / 2, ry, rx1, n / 100.0F, 0xFF6A3A8A, 0.3F);
				ry += 11;
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ contracts

	private void kontrak(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> daftarK = lembar.daftar(Dagang.KONTRAK);
		if (daftarK.isEmpty()) {
			kosong(l, TemaUi.k("dagang.kontrak_kosong"), x0, y0, x1, y1);
			return;
		}
		Kotak[] k = bagi(x0, y0, x1, y1);
		Kotak kiri = k[0];
		daftarKontrak.gambar(l, "kontrak", kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1(), daftarK.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = daftarK.get(i);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			TemaUi.ikon(lk.g, e.b(Dagang.KAPAL) ? TemaUi.I_KAPAL : TemaUi.I_KARUNG, rx0 + 3, ry0 + 5, 16);
			lk.teks(e.teks(LembarKunci.NAMA), rx0 + 23, ry0 + 3, rx1 - rx0 - 70, gaya.teks, 0.85F);
			lk.keterangan(TemaUi.k("dagang.kontrak_ke", e.teks(LembarKunci.TUJUAN), e.i(Dagang.PETI, 0)), rx0 + 23, ry0 + 14, rx1 - rx0 - 70,
					gaya.teksSamar);
			int sisa = e.i(LembarKunci.SISA_HARI, 0);
			lk.kanan(TeksTempat.hari(sisa), rx1 - 4, ry0 + 4, sisa <= 1 ? TempatWarna.BAHAYA : gaya.teksLemah, 0.75F);
			LabelHarga.gambar(lk, e.l(Dagang.BAYAR, 0L), -1L, rx1 - 4, ry0 + 14, true, 0.75F, false);
		}, null, i -> {
		});
		int sel = daftarKontrak.pilihan();
		Kotak kanan = k[1];
		PanelTempat.kartu(l.g, gaya, kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1(), 0.0F, false);
		if (sel < 0 || sel >= daftarK.size()) {
			return;
		}
		Lembar e = daftarK.get(sel);
		int rx0 = kanan.x0() + 5;
		int rx1 = kanan.x1() - 5;
		int ry = kanan.y0() + 4;
		ry += subjudul(l, e.teks(LembarKunci.NAMA), rx0, ry, rx1);
		ry += barisNilai(l, TemaUi.k("dagang.k_tujuan"), e.teks(LembarKunci.TUJUAN), rx0, ry, rx1);
		ry += barisNilai(l, TemaUi.k("dagang.k_peti"), Component.literal(Integer.toString(e.i(Dagang.PETI, 0))), rx0, ry, rx1);
		ry += barisNilai(l, TemaUi.k("dagang.k_sisa"), TeksTempat.hari(e.i(LembarKunci.SISA_HARI, 0)), rx0, ry, rx1);
		ry += barisNilai(l, TemaUi.k("dagang.k_deposit"), TeksTempat.nb(e.l(Dagang.DEPOSIT, 0L), false), rx0, ry, rx1);
		ry += barisNilai(l, TemaUi.k("dagang.k_bayar"), TeksTempat.nb(e.l(Dagang.BAYAR, 0L), false), rx0, ry, rx1);
		if (e.b(Dagang.KAPAL)) {
			ry += 2;
			TemaUi.chip(l, TemaUi.I_KAPAL, TemaUi.k("dagang.k_kapal"), rx0, ry, 0xFF6FA8C8);
			ry += 14;
		}
		l.bungkus(TemaUi.k("dagang.k_jelas"), rx0, ry + 2, rx1 - rx0, gaya.teksSamar, 0.7F);
	}

	// ------------------------------------------------------------------------------------------------ market

	/** Units a town sells of one good per player per day, and units in one sealed crate (server constants). */
	private static final int STOK_HARIAN = dev.kultivator.dunia.tempat.tema.bersama.Dagang.STOK_HARIAN;
	private static final int ISI_PETI = dev.kultivator.dunia.tempat.tema.bersama.PetiDagangItem.ISI_PETI;
	/** Lifetime trade profit of the advancement {@code tempat/tema_saudagar} (Dagang.LABA_SAUDAGAR). */
	private static final long SAUDAGAR = dev.kultivator.dunia.tempat.tema.bersama.Dagang.LABA_SAUDAGAR;
	private static final int[] IKON_SEGMEN = {TemaUi.I_KARUNG, TemaUi.I_JENUH, TemaUi.I_CAP};
	private static final String[] KUNCI_SEGMEN = {"beli", "jual", "segel"};

	private void gantiSegmen(int s) {
		if (s == segmen) {
			return;
		}
		segmen = s;
		daftarPasar.mulaiUlang(Util.getMillis());
		jumlah.set(1);
		UiSound.tab();
	}

	private void pasar(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar p = lembar.sub(Dagang.PASAR);
		// the stall: an awning over a name plaque
		TemaUi.tenda(l.g, x0, x1, y0);
		Component nama = TemaUi.k("dagang.pasar.judul", p.teks(LembarKunci.NAMA));
		int pw = Math.min(x1 - x0 - 20, Math.round(font.width(nama) * 0.9F) + 16);
		int cx = (x0 + x1) / 2;
		l.g.fill(cx - pw / 2, y0 + 9, cx + pw / 2, y0 + 22, Palette.argb(0xE8, 0x3A2414));
		Brush.frame(l.g, cx - pw / 2, y0 + 9, cx + pw / 2, y0 + 22, TempatWarna.EMAS_GELAP);
		Brush.fitCentered(l.g, font, nama, cx, y0 + 12, pw - 8, 0xFFFFE6B0, 0.9F, 0.55F, false);
		int y = y0 + 25;
		if (p.b(Dagang.LEWAT)) {
			y += pita(l, TemaUi.I_JENUH, TemaUi.k("dagang.pasar.lewat"), x0, y, x1, TempatWarna.PERINGATAN);
		}
		// segment buttons: Beli · Jual · Segel (with counts)
		int sw = (x1 - x0 - 8) / 3;
		for (int i = 0; i < 3; i++) {
			int bx0 = x0 + i * (sw + 4);
			int bx1 = bx0 + sw;
			boolean on = segmen == i;
			boolean over = l.di(bx0, y, bx1, y + 14);
			int dasar = on ? Palette.darken(gaya.aksen, 0.1F) : Palette.argb(over ? 0x50 : 0x30, 0x000000);
			Brush.chamfer(l.g, bx0, y, bx1, y + 14, 2, dasar);
			if (on) {
				l.g.fill(bx0 + 2, y + 12, bx1 - 2, y + 14, TempatWarna.EMAS_TERANG);
			}
			TemaUi.ikon(l.g, IKON_SEGMEN[i], bx0 + 3, y + 2, 10);
			int n = pasar.get(i).size();
			Component label = TemaUi.k("dagang.pasar." + KUNCI_SEGMEN[i], n);
			l.teks(label, bx0 + 16, y + 3.5F, sw - 19, on ? 0xFFFFF6E8 : n == 0 ? TempatWarna.terkunci(gaya.teks) : gaya.teks, 0.8F);
			String kunci = "segmen_" + i;
			final int s = i;
			l.zona(kunci, bx0, y, bx1, y + 14, () -> gantiSegmen(s), true, null, true);
			l.fokusCincin(kunci, bx0, y, bx1, y + 14);
			if (over) {
				l.tip(TemaUi.k("dagang.pasar." + KUNCI_SEGMEN[i] + "_tip"));
			}
		}
		y += 18;
		Kotak[] k = bagi(x0, y, x1, y1);
		List<Lembar> rows = pasar.get(segmen);
		Kotak kiri = k[0];
		if (rows.isEmpty()) {
			kosong(l, TemaUi.k("dagang.pasar.kosong_" + KUNCI_SEGMEN[segmen]), kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1());
		} else {
			long u = uang();
			daftarPasar.gambar(l, "pasar", kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1(), rows.size(), 21,
					(lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> barisPasar(lk, rows.get(i), rx0, ry0, rx1, ry1, sorot, pilih, u),
					() -> {
						String op = opPasar(rows);
						if (op != null) {
							kirim(op);
						} else {
							UiSound.denied();
						}
					}, i -> jumlah.set(1));
		}
		detailPasar(l, k[1], rows);
	}

	private void ikonBarang(Lukis l, Lembar e, int x, int y) {
		ItemStack item = e.barang();
		if (!item.isEmpty()) {
			l.g.pose().pushMatrix();
			l.g.pose().translate(x, y);
			l.g.pose().scale(0.9F, 0.9F);
			l.g.item(item, 0, 0);
			l.g.pose().popMatrix();
		} else {
			TemaUi.ikon(l.g, TemaUi.ikonBarang(e.id()), x, y + 1, 14);
		}
	}

	private void barisPasar(Lukis l, Lembar e, int rx0, int ry0, int rx1, int ry1, boolean sorot, boolean pilih, long u) {
		PanelTempat.kartu(l.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
		boolean kunci = !e.bisa();
		ikonBarang(l, e, rx0 + 2, ry0 + 2);
		int namaW = rx1 - rx0 - 76;
		l.teks(e.teks(LembarKunci.NAMA), rx0 + 20, ry0 + 3, namaW, kunci ? TempatWarna.terkunci(gaya.teks) : gaya.teks, 0.8F);
		switch (segmen) {
			case BELI -> {
				int stok = e.i(Dagang.STOK, 0);
				l.keterangan(stok <= 0 ? TeksTempat.k("kartu.habis") : TemaUi.k("dagang.pasar.stok", stok, STOK_HARIAN), rx0 + 20, ry0 + 12,
						namaW, stok <= 0 ? TempatWarna.BAHAYA : gaya.teksSamar);
				LabelHarga.gambar(l, e.l(LembarKunci.NB, 0L), u, rx1 - 4, ry0 + 6, true, 0.85F, false);
			}
			case JUAL -> {
				l.keterangan(TemaUi.k("dagang.pasar.bawa", e.i(LembarKunci.JUMLAH, 0)), rx0 + 20, ry0 + 12, namaW, gaya.teksSamar);
				if (kunci) {
					IkonTempat.gambar(l.g, IkonTempat.KUNCI, rx1 - 14, ry0 + 5, 10, 0xC0FFFFFF);
					Component a = alasanKeadaan(e);
					if (a != null && l.di(rx0, ry0, rx1, ry1)) {
						l.tip(a);
					}
				} else {
					float j = e.f(Dagang.JENUH, 1.0F);
					Component nb = TemaUi.k("dagang.pasar.nb_kira", TeksTempat.desimal1(e.f(LembarKunci.NB, 0.0F)));
					l.kanan(nb, rx1 - 4, ry0 + 3, warnaJenuh(j), 0.8F);
					TemaUi.meter(l, -1, rx1 - 46, ry0 + 11, rx1 - 4, (j - 0.6F) / 0.4F, warnaJenuh(j), -1.0F);
				}
			}
			default -> {
				l.keterangan(TemaUi.k("dagang.pasar.segel_baris", ISI_PETI), rx0 + 20, ry0 + 12, namaW, gaya.teksSamar);
				TemaUi.ikon(l.g, TemaUi.I_CAP, rx1 - 34, ry0 + 4, 12);
				LabelHarga.gambar(l, e.l(LembarKunci.NB, 1L), u, rx1 - 4, ry0 + 6, true, 0.85F, false);
			}
		}
	}

	/** Green while the market still pays full, amber then red as it fills (jenuh 1.0 .. 0.6). */
	private static int warnaJenuh(float j) {
		return j >= 0.9F ? 0xFF2E7A4E : j >= 0.75F ? TempatWarna.PERINGATAN : TempatWarna.BAHAYA;
	}

	/** Units carried of good {@code id} (from the Jual ledger; 0 when none). */
	private int dibawa(String id) {
		for (Lembar e : pasar.get(JUAL)) {
			if (e.id().equals(id)) {
				return e.i(LembarKunci.JUMLAH, 0);
			}
		}
		return 0;
	}

	/** The op the selected row's action sends now (Enter on the list), or null when it cannot. */
	private @Nullable String opPasar(List<Lembar> rows) {
		int sel = daftarPasar.pilihan();
		if (sel < 0 || sel >= rows.size() || !tulisBoleh()) {
			return null;
		}
		Lembar e = rows.get(sel);
		int n = jumlah.nilai();
		return switch (segmen) {
			case BELI -> e.i(Dagang.STOK, 0) > 0 && mampu(e.l(LembarKunci.NB, 0L) * n) ? LembarKunci.op(Dagang.OP_BELI, e.id(), n) : null;
			case JUAL -> e.bisa() ? LembarKunci.op(Dagang.OP_JUAL, e.id(), n) : null;
			default -> dibawa(e.id()) >= ISI_PETI && mampu(e.l(LembarKunci.NB, 1L)) ? LembarKunci.op(Dagang.OP_SEGEL, e.id()) : null;
		};
	}

	private void detailPasar(Lukis l, Kotak k, List<Lembar> rows) {
		PanelTempat.kartu(l.g, gaya, k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		int sel = daftarPasar.pilihan();
		if (sel < 0 || sel >= rows.size()) {
			tengah(l, TemaUi.k(rows.isEmpty() ? "dagang.pasar.jelas_" + KUNCI_SEGMEN[segmen] : "toko.pilih"), k);
			return;
		}
		Lembar e = rows.get(sel);
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		int pv = tata == Tata.SEMPIT ? 28 : tata == Tata.LEBAR ? 64 : 44;
		int slot = TemaUi.ikonBarang(e.id());
		pratinjau.gambar(l, "pasar_3d", x0, y, x0 + pv, y + pv, e.barang(), null, e.barang().isEmpty() && slot < 0 ? LembarKunci.Ikon.KAFILAH : -1);
		if (e.barang().isEmpty() && slot >= 0) {
			TemaUi.ikon(l.g, slot, x0 + pv / 2 - 12, y + pv / 2 - 12, 24);
		}
		int tx = x0 + pv + 6;
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, y, x1 - tx, gaya.teks);
		int ty = y + 12;
		int by = k.y1() - 19;
		switch (segmen) {
			case BELI -> {
				long nb = e.l(LembarKunci.NB, 0L);
				int stok = e.i(Dagang.STOK, 0);
				ty += barisNilai(l, TemaUi.k("dagang.pasar.satuan"), TeksTempat.nb(nb, false), tx, ty, x1);
				ty += barisNilai(l, TemaUi.k("dagang.pasar.sisa"), Component.literal(stok + " / " + STOK_HARIAN), tx, ty, x1);
				TemaUi.meter(l, -1, tx, ty, x1, stok / (float) STOK_HARIAN, stok > 4 ? TempatWarna.OK : TempatWarna.PERINGATAN, -1.0F);
				ty = Math.max(ty + 12, y + pv + 4);
				l.bungkus(TemaUi.k("dagang.pasar.beli_jelas"), x0, ty, x1 - x0, gaya.teksSamar, 0.7F, by - ty - 12);
				int pw = jumlah.gambar(l, "pasar_n", x1 - 50, by + 1, Math.max(1, stok)) + 6;
				int n = jumlah.nilai();
				long total = nb * n;
				Component alasan = stok <= 0 ? TemaUi.k("dagang.pasar.habis") : !mampu(total) ? kurang(total) : null;
				tombolOp(l, "pasar_beli", x0, by, x1 - x0 - pw, 15, TemaUi.k("toko.beli", TeksTempat.angka(total)), alasan == null, alasan,
						LembarKunci.op(Dagang.OP_BELI, e.id(), n));
			}
			case JUAL -> {
				int bawa = e.i(LembarKunci.JUMLAH, 0);
				ty += barisNilai(l, TemaUi.k("dagang.pasar.dibawa"), Component.literal(Integer.toString(bawa)), tx, ty, x1);
				if (!e.bisa()) {
					Component a = alasanKeadaan(e);
					ty = Math.max(ty + 2, y + pv + 4);
					if (a != null) {
						ty += pita(l, TemaUi.I_TOPENG, a, x0, ty, x1, TempatWarna.BAHAYA);
					}
					l.bungkus(TemaUi.k("dagang.pasar.jual_tolak"), x0, ty + 2, x1 - x0, gaya.teksSamar, 0.7F, by - ty - 14);
					tombolOp(l, "pasar_jual", x0, by, x1 - x0, 15, TemaUi.k("dagang.pasar.tombol_jual", "—"), false, a,
							LembarKunci.op(Dagang.OP_JUAL, e.id(), 1));
					break;
				}
				float nb = e.f(LembarKunci.NB, 0.0F);
				float j = e.f(Dagang.JENUH, 1.0F);
				ty += barisNilai(l, TemaUi.k("dagang.pasar.unit_berikut"), Component.literal(TeksTempat.desimal1(nb) + " NB"), tx, ty, x1);
				l.keterangan(TemaUi.k("dagang.pasar.jenuh", Math.round((1.0F - j) * 100.0F)), tx, ty, x1 - tx, warnaJenuh(j));
				ty += 9;
				TemaUi.meter(l, TemaUi.I_JENUH, tx, ty, x1, (j - 0.6F) / 0.4F, warnaJenuh(j), -1.0F);
				ty = Math.max(ty + 12, y + pv + 4);
				Component jelas = TemaUi.k(lembar.sub(Dagang.PASAR).b(Dagang.LEWAT) ? "dagang.pasar.jual_lewat" : "dagang.pasar.jual_jelas");
				l.bungkus(jelas, x0, ty, x1 - x0, gaya.teksSamar, 0.7F, by - ty - 12);
				int pw = jumlah.gambar(l, "pasar_n", x1 - 50, by + 1, Math.max(1, Math.min(64, bawa))) + 6;
				int n = jumlah.nilai();
				// shortcut: the client does not know jenuhPerUnit, so the total shown is next-unit price x n (an upper bound)
				String kira = TeksTempat.angka(Math.max(1L, Math.round(nb * n)));
				tombolOp(l, "pasar_jual", x0, by, x1 - x0 - pw, 15, TemaUi.k("dagang.pasar.tombol_jual", kira), true, null,
						LembarKunci.op(Dagang.OP_JUAL, e.id(), n));
			}
			default -> {
				long nb = e.l(LembarKunci.NB, 1L);
				int bawa = dibawa(e.id());
				ty += barisNilai(l, TemaUi.k("dagang.pasar.dibawa"), Component.literal(Integer.toString(bawa)), tx, ty, x1);
				ty += barisNilai(l, TemaUi.k("dagang.pasar.jadi_peti"), Component.literal(Integer.toString(bawa / ISI_PETI)), tx, ty, x1);
				ty = Math.max(ty + 2, y + pv + 4);
				// eight goods flowing into the seal
				int gx = x0;
				for (int i = 0; i < ISI_PETI && gx + 12 < x1 - 30; i++) {
					ikonBarang(l, e, gx, ty);
					gx += 11;
				}
				IkonTempat.gambar(l.g, IkonTempat.PANAH_KANAN, gx + 2, ty + 3, 9, 0xC0FFFFFF);
				TemaUi.ikon(l.g, TemaUi.I_CAP, gx + 14, ty, 14);
				ty += 18;
				l.bungkus(TemaUi.k("dagang.pasar.segel_jelas", ISI_PETI), x0, ty, x1 - x0, gaya.teksSamar, 0.7F, by - ty - 12);
				Component alasan = bawa < ISI_PETI ? TemaUi.k("dagang.pasar.segel_kurang", ISI_PETI) : !mampu(nb) ? kurang(nb) : null;
				tombolOp(l, "pasar_segel", x0, by, x1 - x0, 15, TemaUi.k("dagang.pasar.tombol_segel", TeksTempat.angka(nb)), alasan == null, alasan,
						LembarKunci.op(Dagang.OP_SEGEL, e.id()));
			}
		}
		long u = uang();
		if (u >= 0L) {
			l.kanan(TemaUi.k("dagang.pasar.uang", TeksTempat.angka(u)), x1, by - 10, gaya.teksSamar, 0.7F);
		}
	}
}
