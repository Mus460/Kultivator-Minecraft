package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihJumlah;
import dev.kultivator.client.screen.ui.Pratinjau3D;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Lembar;
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
public final class LayarBukuDagang extends TemaHalaman {

	private static final int KOL = 46;
	private static final int NAMA_KOL = 92;
	/** Paging arrow talisman width: seal + glyph (a 16 px talisman has no room left for its label). */
	private static final int PANAH = 30;

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
		super(Dagang.LAYAR, npcId, lembar, "layar.dagang", Palette.Gaya.KERTAS);
	}

	@Override
	protected List<TabBar.Tab> tabs() {
		return List.of(tabTema(Dagang.PASAR, "dagang.tab.pasar"), tabTema(Dagang.TAB_HARGA, "dagang.tab.harga"),
				tabTema(Dagang.TAB_LABA, "dagang.tab.laba"), tabTema(Dagang.TAB_KONTRAK, "dagang.tab.kontrak"));
	}

	/** The market tab exists only while the sheet carries a market block (client-only tab; the server just stores it). */
	@Override
	protected boolean tabAda(String id) {
		return !Dagang.PASAR.equals(id) || lembar.ada(Dagang.PASAR);
	}

	@Override
	protected KeadaanKosong.Gambar kosongGambar() {
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
	protected void lukisTema(Lukis l, int x0, int y0, int x1, int y1) {
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

	/** Columns that fit (the paging arrows sit in the name column of the header, so they take no column room). */
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
		boolean halaman = kota.size() > muat;
		l.teks(TemaUi.k("dagang.barang"), x0 + 2, hy + (halaman ? 1 : 8), NAMA_KOL - 4, gaya.teksLemah);
		for (int c = kolomAwal; c < akhir; c++) {
			Lembar k = kota.get(c);
			int cx = x0 + NAMA_KOL + (c - kolomAwal) * KOL + KOL / 2;
			TemaUi.lambang(l, k.s(Dagang.TEMA), cx, hy + 6, 6);
			// 43.0.5: names at 1.0, cut with "…"; the full name is in the tooltip below
			Brush.fitCentered(l.g, font, k.teks(LembarKunci.NAMA), cx, hy + 14, KOL - 4, gaya.teks, 1.0F, 1.0F, false);
			if (l.di(cx - KOL / 2, hy, cx + KOL / 2, hy + 23)) {
				List<Component> tip = new ArrayList<>();
				tip.add(k.teks(LembarKunci.NAMA));
				tip.add(TemaUi.k("tema." + k.s(Dagang.TEMA)));
				if (k.ada(LembarKunci.JARAK)) {
					tip.add(TemaUi.k("dagang.jarak", Brush.angka(k.i(LembarKunci.JARAK, 0))));
				}
				l.tip(tip);
			}
		}
		// paging arrows under the caption when the columns do not fit; the zone makes the header a stop of the Tab ring for ← / →
		if (halaman) {
			Lukis.Zona kolom = l.zona("tabel", x0, hy, x0 + NAMA_KOL, hy + 23, null, true, null, true);
			if (kolom != null) {
				kolom.geser = arah -> geserKolom(arah, muat);
			}
			l.fokusCincin("tabel", x0, hy, x0 + NAMA_KOL, hy + 23);
			TalismanButton.gambar(l, "kiri", x0 + 2, hy + 10, PANAH, 12, Component.literal("‹"), kolomAwal > 0, null, () -> geserKolom(-1, muat));
			TalismanButton.gambar(l, "kanan", x0 + 4 + PANAH, hy + 10, PANAH, 12, Component.literal("›"), akhir < kota.size(), null,
					() -> geserKolom(1, muat));
		}
		l.garis(x0, x1, hy + 24);
		int ty0 = hy + 27;
		// legend line (3 + 9) and route line (2 + 9) under the table
		int ty1 = y1 - 25;
		if (harga.isEmpty()) {
			kosong(l, TemaUi.k("dagang.harga_kosong"), x0, ty0, x1, y1);
			return;
		}
		daftar.gambar(l, "harga", x0, ty0, x1, ty1, harga.size(), 17, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> baris(lk, harga.get(i), rx0, ry0,
				rx1, ry1, sorot, pilih, akhir), null, i -> {
		});
		// legend and the selected row's best route
		int ly = ty1 + 3;
		legenda(l, x0, ly, x1);
		int sel = daftar.pilihan();
		if (sel >= 0 && sel < harga.size()) {
			rute(l, harga.get(sel), x0, ly + 11, x1);
		}
	}

	private void baris(Lukis l, Lembar e, int rx0, int ry0, int rx1, int ry1, boolean sorot, boolean pilih, int akhir) {
		l.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
		ikonBarang(l, e, rx0 + 1, ry0);
		l.teks(e.teks(LembarKunci.NAMA), rx0 + 19, ry0 + 4, NAMA_KOL - 21, gaya.teks);
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
				Brush.centered(l.g, font, Component.literal("—"), cx0 + KOL / 2.0F, ry0 + 4, Palette.terkunci(gaya.teksSamar), 1.0F, false);
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
		int warna = tua ? Palette.terkunci(gaya.teks) : s.b(Dagang.TERBAIK) ? 0xFF2E7A4E : gaya.teks;
		Component nb = Component.literal(Brush.desimal1(s.f(LembarKunci.NB, 0.0F)));
		float w = font.width(nb);
		float tx = (cx0 + cx1) / 2.0F - w / 2.0F - 4;
		Brush.text(l.g, font, nb, tx, ry0 + 4, warna, 1.0F, false);
		int ix = Math.round(tx + w + 1);
		if (s.b(Dagang.TERBAIK)) {
			TemaUi.ikon(l.g, TemaUi.I_TERBAIK, ix, ry0 + 3, 8, tua ? 0x80FFFFFF : 0xFFFFFFFF);
		} else if (s.b(Dagang.ASAL)) {
			TemaUi.ikon(l.g, TemaUi.I_ASAL, ix, ry0 + 3, 8, tua ? 0x80FFFFFF : 0xFFFFFFFF);
		}
		if (l.di(cx0, ry0, cx1, ry1)) {
			List<Component> tip = new ArrayList<>();
			tip.add(k.teks(LembarKunci.NAMA));
			tip.add(TemaUi.k("dagang.sel_harga", Brush.desimal1(s.f(LembarKunci.NB, 0.0F))));
			int umur = s.i(Dagang.UMUR_HARI, 0);
			tip.add(umur <= 0 ? TemaUi.k("dagang.sel_hari_ini") : TemaUi.k("dagang.sel_umur", umur));
			if (jenuh < 0.97F) {
				tip.add(TemaUi.k("dagang.sel_jenuh", Math.round((1.0F - jenuh) * 100.0F)).withColor(0xFFFF8A7A));
			}
			if (s.b(Dagang.ASAL)) {
				tip.add(TemaUi.k("dagang.sel_asal"));
			}
			if (s.b(Dagang.TERBAIK)) {
				tip.add(TemaUi.k("dagang.sel_terbaik").withColor(Palette.GOLD_LIGHT));
			}
			l.tip(tip);
		}
	}

	/** One legend line at 1.0; items that do not fit are left out and the whole legend is the line's tooltip. */
	private void legenda(Lukis l, int x0, int y, int x1) {
		Component asal = TemaUi.k("dagang.lg_asal");
		Component terbaik = TemaUi.k("dagang.lg_terbaik");
		Component tua = TemaUi.k("dagang.lg_tua");
		Component jenuh = TemaUi.k("dagang.lg_jenuh");
		int x = x0;
		if (x + 10 + font.width(asal) <= x1) {
			TemaUi.ikon(l.g, TemaUi.I_ASAL, x, y, 8);
			Brush.text(l.g, font, asal, x + 10, y, gaya.teksSamar, 1.0F, false);
			x += 10 + font.width(asal) + 8;
		}
		if (x + 10 + font.width(terbaik) <= x1) {
			TemaUi.ikon(l.g, TemaUi.I_TERBAIK, x, y, 8);
			Brush.text(l.g, font, terbaik, x + 10, y, gaya.teksSamar, 1.0F, false);
			x += 10 + font.width(terbaik) + 8;
		}
		if (x + 10 + font.width(jenuh) <= x1) {
			l.g.fill(x, y, x + 8, y + 7, Palette.argb(0x60, 0xB03030));
			Brush.text(l.g, font, jenuh, x + 10, y, gaya.teksSamar, 1.0F, false);
			x += 10 + font.width(jenuh) + 8;
		}
		if (x + font.width(tua) <= x1) {
			Brush.text(l.g, font, tua, x, y, Palette.terkunci(gaya.teks), 1.0F, false);
		}
		if (l.di(x0, y - 1, x1, y + 9)) {
			l.tip(List.of(asal, terbaik, jenuh, tua));
		}
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
			l.teks(TemaUi.k("dagang.rute_kurang"), x0, y, x1 - x0, gaya.teksSamar);
			return;
		}
		float beda = mahal.f(LembarKunci.NB, 0.0F) - murah.f(LembarKunci.NB, 0.0F);
		Component a = namaKota(murah.s(Dagang.KOTA));
		Component b = namaKota(mahal.s(Dagang.KOTA));
		Component t = TemaUi.k("dagang.rute", e.teks(LembarKunci.NAMA), a, Brush.desimal1(murah.f(LembarKunci.NB, 0.0F)), b,
				Brush.desimal1(mahal.f(LembarKunci.NB, 0.0F)), Brush.desimal1(beda));
		l.teks(t, x0, y, x1 - x0, beda > 0.0F ? 0xFF2E7A4E : gaya.teksLemah);
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
		LabelHarga.gambar(l, minggu, -1L, x, y + 2, false, false);
		l.kanan(TemaUi.k("dagang.dari_batas", Brush.angka(batas)), kiri.x1(), y + 5, gaya.teksLemah);
		y += 18;
		float frac = minggu / (float) batas;
		int warna = frac >= 1.0F ? Palette.BAHAYA : frac > 0.8F ? Palette.PERINGATAN : Palette.OK;
		y += TemaUi.meter(l, -1, x, y, kiri.x1(), frac, warna, -1.0F) + 4;
		if (laba.b(Dagang.LEWAT)) {
			y += pita(l, LembarKunci.Ikon.TIMBANGAN, TemaUi.k("dagang.lewat"), x, y, kiri.x1(), Palette.BAHAYA);
		}
		// at most two lines (the rest on hover) so the lifetime row below always has room
		y += Math.round(l.bungkus(TemaUi.k("dagang.batas_jelas"), x, y, kiri.w(), gaya.teksSamar, Math.min(18, kiri.y1() - y - 24))) + 3;
		// lifetime profit toward the "Saudagar Lima Kota" advancement
		long total = laba.l(Dagang.TOTAL, 0L);
		if (y + 24 > kiri.y1()) {
			// narrow layout (GUI scale 4): no room under the weekly block; the right card follows below
		} else if (total < SAUDAGAR) {
			y += barisNilai(l, TemaUi.k("dagang.laba_total"), Brush.nb(total, false), x, y, kiri.x1());
			TemaUi.meter(l, TemaUi.I_TERBAIK, x, y, kiri.x1(), total / (float) SAUDAGAR, Palette.GOLD_LIGHT, -1.0F);
			if (l.di(x, y - 1, kiri.x1(), y + 9)) {
				l.tip(TemaUi.k("dagang.saudagar_tip", Brush.angka(SAUDAGAR - Math.max(0L, total))));
			}
			y += 12;
		} else {
			y += barisNilai(l, TemaUi.k("dagang.laba_total"), Brush.nb(total, false), x, y, kiri.x1());
			TemaUi.chip(l, TemaUi.I_TERBAIK, TemaUi.k("dagang.saudagar"), x, y, Palette.GOLD_LIGHT);
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
			int bh = Math.min(48, kiri.y1() - y - 11);
			int n = hari.length;
			int bw = Math.max(4, (kiri.w() - (n - 1) * 3) / n);
			int dasar = y + bh;
			l.g.fill(x, dasar, kiri.x1(), dasar + 1, Palette.argb(0x80, gaya.teksSamar & 0xFFFFFF));
			for (int i = 0; i < n; i++) {
				int bx = x + i * (bw + 3);
				int tinggi = Math.round(bh * Math.abs(hari[i]) / (float) maks);
				int c = hari[i] >= 0 ? 0xFF4F9A6A : Palette.BAHAYA;
				l.g.fillGradient(bx, dasar - tinggi, bx + bw, dasar, Palette.lighten(c, 0.2F), c);
				Component hariLabel = Component.literal(i == n - 1 ? TemaUi.k("dagang.hari_ini").getString() : "−" + (n - 1 - i));
				// day labels at 1.0 only where a bar is wide enough; the tooltip always names the day
				if (font.width(hariLabel) <= bw + 2) {
					Brush.centered(l.g, font, hariLabel, bx + bw / 2.0F, dasar + 2, gaya.teksSamar, 1.0F, false);
				}
				if (l.di(bx, dasar - bh, bx + bw, dasar + 10)) {
					l.tip(List.of(hariLabel, TemaUi.k("dagang.bar_tip", Brush.bertanda(hari[i]))));
				}
			}
		}
		// right: standing in the dark world
		Kotak kanan = k[1];
		l.kartu(kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1(), 0.0F, false);
		int rx0 = kanan.x0() + 5;
		int rx1 = kanan.x1() - 5;
		int ry = kanan.y0() + 4;
		ry += subjudul(l, TemaUi.k("dagang.nama_gelap"), rx0, ry, rx1);
		int infami = lembar.i(Dagang.INFAMI, 0);
		l.teks(TemaUi.k("dagang.infami", infami), rx0, ry, rx1 - rx0, gaya.teksLemah);
		ry += 10;
		ry += TemaUi.meter(l, TemaUi.I_INFAMI, rx0, ry, rx1, infami / 100.0F, TemaUi.DARAH, 0.3F) + 3;
		List<Lembar> gentar = lembar.daftar(Dagang.GENTAR);
		// the standing note gets what the Gentar rows leave (two lines at most; the rest on hover)
		float maksJelas = Math.min(27, kanan.y1() - ry - 4 - (gentar.isEmpty() ? 0 : 24));
		ry += Math.round(l.bungkus(TemaUi.k(infami >= 60 ? "dagang.infami_tinggi" : infami >= 30 ? "dagang.infami_sedang" : "dagang.infami_rendah"), rx0,
				ry, rx1 - rx0, gaya.teksSamar, maksJelas)) + 4;
		if (!gentar.isEmpty() && ry + 24 <= kanan.y1()) {
			ry += subjudul(l, TemaUi.k("dagang.gentar"), rx0, ry, rx1);
			for (Lembar g : gentar) {
				if (ry + 10 > kanan.y1()) {
					break;
				}
				int n = g.i(LembarKunci.NILAI, 0);
				l.teks(g.teks(Dagang.KOTA), rx0, ry, (rx1 - rx0) / 2 - 2, gaya.teks);
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
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			TemaUi.ikon(lk.g, e.b(Dagang.KAPAL) ? TemaUi.I_KAPAL : TemaUi.I_KARUNG, rx0 + 3, ry0 + 5, 16);
			int sisa = e.i(LembarKunci.SISA_HARI, 0);
			Component hari = Brush.hari(sisa);
			Component bayar = Component.literal(Brush.angka(e.l(Dagang.BAYAR, 0L)));
			int kananW = Math.max(font.width(hari), font.width(bayar) + 12) + 8;
			lk.teks(e.teks(LembarKunci.NAMA), rx0 + 23, ry0 + 3, rx1 - rx0 - 23 - kananW, gaya.teks);
			lk.teks(TemaUi.k("dagang.kontrak_ke", e.teks(LembarKunci.TUJUAN), e.i(Dagang.PETI, 0)), rx0 + 23, ry0 + 14, rx1 - rx0 - 23 - kananW,
					gaya.teksSamar);
			lk.kanan(hari, rx1 - 4, ry0 + 3, sisa <= 1 ? Palette.BAHAYA : gaya.teksLemah);
			LabelHarga.gambar(lk, e.l(Dagang.BAYAR, 0L), -1L, rx1 - 4, ry0 + 14, true, false);
		}, null, i -> {
		});
		int sel = daftarKontrak.pilihan();
		Kotak kanan = k[1];
		l.kartu(kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1(), 0.0F, false);
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
		ry += barisNilai(l, TemaUi.k("dagang.k_sisa"), Brush.hari(e.i(LembarKunci.SISA_HARI, 0)), rx0, ry, rx1);
		ry += barisNilai(l, TemaUi.k("dagang.k_deposit"), Brush.nb(e.l(Dagang.DEPOSIT, 0L), false), rx0, ry, rx1);
		ry += barisNilai(l, TemaUi.k("dagang.k_bayar"), Brush.nb(e.l(Dagang.BAYAR, 0L), false), rx0, ry, rx1);
		if (e.b(Dagang.KAPAL) && ry + 16 <= kanan.y1()) {
			ry += 2;
			TemaUi.chip(l, TemaUi.I_KAPAL, TemaUi.k("dagang.k_kapal"), rx0, ry, 0xFF6FA8C8);
			ry += 14;
		}
		l.bungkus(TemaUi.k("dagang.k_jelas"), rx0, ry + 2, rx1 - rx0, gaya.teksSamar, kanan.y1() - ry - 6);
	}

	// ------------------------------------------------------------------------------------------------ market

	/** Units a town sells of one good per player per day, and units in one sealed crate (server constants). */
	private static final int STOK_HARIAN = dev.kultivator.dunia.tempat.tema.bersama.Dagang.STOK_HARIAN;
	private static final int ISI_PETI = dev.kultivator.dunia.tempat.tema.bersama.PetiDagangItem.ISI_PETI;
	/** Lifetime trade profit of the advancement {@code tempat/tema_saudagar} (Dagang.LABA_SAUDAGAR). */
	private static final long SAUDAGAR = dev.kultivator.dunia.tempat.tema.bersama.Dagang.LABA_SAUDAGAR;
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
		Component nama = TemaUi.k("dagang.pasar.judul", p.teks(LembarKunci.NAMA));
		int y = y0;
		if (tata != Tata.SEMPIT) {
			// the stall: an awning over a name plaque (narrow: left out, the Di Sini title already names the stall)
			TemaUi.tenda(l.g, x0, x1, y0);
			int pw = Math.min(x1 - x0 - 20, font.width(nama) + 16);
			int cx = (x0 + x1) / 2;
			l.g.fill(cx - pw / 2, y0 + 9, cx + pw / 2, y0 + 22, Palette.argb(0xE8, 0x3A2414));
			Brush.frame(l.g, cx - pw / 2, y0 + 9, cx + pw / 2, y0 + 22, Palette.GOLD_DARK);
			Brush.fitCentered(l.g, font, nama, cx, y0 + 12, pw - 8, 0xFFFFE6B0, 1.0F, 1.0F, false);
			if (l.di(cx - pw / 2, y0 + 9, cx + pw / 2, y0 + 22)) {
				l.tip(nama);
			}
			y = y0 + 25;
		}
		if (p.b(Dagang.LEWAT)) {
			y += pita(l, TemaUi.I_JENUH, TemaUi.k("dagang.pasar.lewat"), x0, y, x1, Palette.PERINGATAN);
		}
		// segment buttons: Beli · Jual · Segel (with counts)
		int sw = (x1 - x0 - 8) / 3;
		for (int i = 0; i < 3; i++) {
			int bx0 = x0 + i * (sw + 4);
			Component label = TemaUi.k("dagang.pasar." + KUNCI_SEGMEN[i], pasar.get(i).size());
			final int s = i;
			if (TalismanButton.gambar(l, "segmen_" + i, bx0, y, sw, 14, label, true, null, () -> gantiSegmen(s), false, segmen == i)) {
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
			daftarPasar.gambar(l, "pasar", kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1(), rows.size(), 22,
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
			l.g.item(item, x, y);
		} else {
			TemaUi.ikon(l.g, TemaUi.ikonBarang(e.id()), x, y + 1, 14);
		}
	}

	private void barisPasar(Lukis l, Lembar e, int rx0, int ry0, int rx1, int ry1, boolean sorot, boolean pilih, long u) {
		l.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
		boolean kunci = !e.bisa();
		ikonBarang(l, e, rx0 + 2, ry0 + 2);
		int namaW = rx1 - rx0 - 76;
		l.teks(e.teks(LembarKunci.NAMA), rx0 + 20, ry0 + 2, namaW, kunci ? Palette.terkunci(gaya.teks) : gaya.teks);
		switch (segmen) {
			case BELI -> {
				int stok = e.i(Dagang.STOK, 0);
				l.teks(stok <= 0 ? tt("kartu.habis") : TemaUi.k("dagang.pasar.stok", stok, STOK_HARIAN), rx0 + 20, ry0 + 12, namaW,
						stok <= 0 ? Palette.BAHAYA : gaya.teksSamar);
				LabelHarga.gambar(l, e.l(LembarKunci.NB, 0L), u, rx1 - 4, ry0 + 7, true, false);
			}
			case JUAL -> {
				l.teks(TemaUi.k("dagang.pasar.bawa", e.i(LembarKunci.JUMLAH, 0)), rx0 + 20, ry0 + 12, namaW, gaya.teksSamar);
				if (kunci) {
					Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.KUNCI, rx1 - 14, ry0 + 5, 10, 0xC0FFFFFF);
					Component a = alasanKeadaan(e);
					if (a != null && l.di(rx0, ry0, rx1, ry1)) {
						l.tip(a);
					}
				} else {
					float j = e.f(Dagang.JENUH, 1.0F);
					Component nb = TemaUi.k("dagang.pasar.nb_kira", Brush.desimal1(e.f(LembarKunci.NB, 0.0F)));
					l.kanan(nb, rx1 - 4, ry0 + 2, warnaJenuh(j));
					TemaUi.meter(l, -1, rx1 - 46, ry0 + 12, rx1 - 4, (j - 0.6F) / 0.4F, warnaJenuh(j), -1.0F);
				}
			}
			default -> {
				l.teks(TemaUi.k("dagang.pasar.segel_baris", ISI_PETI), rx0 + 20, ry0 + 12, namaW, gaya.teksSamar);
				TemaUi.ikon(l.g, TemaUi.I_CAP, rx1 - 40, ry0 + 5, 12);
				LabelHarga.gambar(l, e.l(LembarKunci.NB, 1L), u, rx1 - 4, ry0 + 7, true, false);
			}
		}
	}

	/** Green while the market still pays full, amber then red as it fills (jenuh 1.0 .. 0.6). */
	private static int warnaJenuh(float j) {
		return j >= 0.9F ? 0xFF2E7A4E : j >= 0.75F ? Palette.PERINGATAN : Palette.BAHAYA;
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
		l.kartu(k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		int sel = daftarPasar.pilihan();
		if (sel < 0 || sel >= rows.size()) {
			tengah(l, TemaUi.k(rows.isEmpty() ? "dagang.pasar.jelas_" + KUNCI_SEGMEN[segmen] : "toko.pilih"), k);
			return;
		}
		Lembar e = rows.get(sel);
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		// a low detail box (GUI scale 4, list over detail) drops the turning preview and keeps name, main rows and button
		boolean rendah = k.h() < 84;
		int pv = rendah ? 0 : tata == Tata.SEMPIT ? 28 : tata == Tata.LEBAR ? 64 : 44;
		if (pv > 0) {
			int slot = TemaUi.ikonBarang(e.id());
			pratinjau.gambar(l, "pasar_3d", x0, y, x0 + pv, y + pv, e.barang(), null, e.barang().isEmpty() && slot < 0 ? LembarKunci.Ikon.KAFILAH : -1);
			if (e.barang().isEmpty() && slot >= 0) {
				TemaUi.ikon(l.g, slot, x0 + pv / 2 - 12, y + pv / 2 - 12, 24);
			}
		}
		int tx = pv > 0 ? x0 + pv + 6 : x0;
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, y, x1 - tx, gaya.teks);
		int ty = y + 12;
		int by = k.y1() - 19;
		// rows below are drawn only while they stay above the button row (no overlap at any GUI scale)
		int batas = by - 2;
		switch (segmen) {
			case BELI -> {
				long nb = e.l(LembarKunci.NB, 0L);
				int stok = e.i(Dagang.STOK, 0);
				if (ty + 10 <= batas) {
					ty += barisNilai(l, TemaUi.k("dagang.pasar.satuan"), Brush.nb(nb, false), tx, ty, x1);
				}
				if (ty + 10 <= batas) {
					ty += barisNilai(l, TemaUi.k("dagang.pasar.sisa"), Component.literal(stok + " / " + STOK_HARIAN), tx, ty, x1);
				}
				if (ty + 8 <= batas) {
					TemaUi.meter(l, -1, tx, ty, x1, stok / (float) STOK_HARIAN, stok > 4 ? Palette.OK : Palette.PERINGATAN, -1.0F);
				}
				ty = Math.max(ty + 12, y + pv + 4);
				l.bungkus(TemaUi.k("dagang.pasar.beli_jelas"), x0, ty, x1 - x0, gaya.teksSamar, by - ty - 12);
				int maksJ = Math.max(1, stok);
				int pw = PemilihJumlah.lebar(l, maksJ);
				jumlah.gambar(l, "pasar_n", x1 - pw, by + 1, maksJ);
				pw += 6;
				int n = jumlah.nilai();
				long total = nb * n;
				Component alasan = stok <= 0 ? TemaUi.k("dagang.pasar.habis") : !mampu(total) ? kurang(total) : null;
				tombolOp(l, "pasar_beli", x0, by, x1 - x0 - pw, 15, TemaUi.k("toko.beli", Brush.angka(total)), alasan == null, alasan,
						LembarKunci.op(Dagang.OP_BELI, e.id(), n));
			}
			case JUAL -> {
				int bawa = e.i(LembarKunci.JUMLAH, 0);
				if (ty + 10 <= batas) {
					ty += barisNilai(l, TemaUi.k("dagang.pasar.dibawa"), Component.literal(Integer.toString(bawa)), tx, ty, x1);
				}
				if (!e.bisa()) {
					Component a = alasanKeadaan(e);
					ty = Math.max(ty + 2, y + pv + 4);
					// the reason also rides on the disabled button's tooltip, so the band may be left out when low
					if (a != null && ty + 15 <= batas) {
						ty += pita(l, TemaUi.I_TOPENG, a, x0, ty, x1, Palette.BAHAYA);
					}
					l.bungkus(TemaUi.k("dagang.pasar.jual_tolak"), x0, ty + 2, x1 - x0, gaya.teksSamar, by - ty - 14);
					tombolOp(l, "pasar_jual", x0, by, x1 - x0, 15, TemaUi.k("dagang.pasar.tombol_jual", "—"), false, a,
							LembarKunci.op(Dagang.OP_JUAL, e.id(), 1));
					break;
				}
				float nb = e.f(LembarKunci.NB, 0.0F);
				float j = e.f(Dagang.JENUH, 1.0F);
				if (ty + 10 <= batas) {
					ty += barisNilai(l, TemaUi.k("dagang.pasar.unit_berikut"), Component.literal(Brush.desimal1(nb) + " NB"), tx, ty, x1);
				}
				if (ty + 10 <= batas) {
					l.teks(TemaUi.k("dagang.pasar.jenuh", Math.round((1.0F - j) * 100.0F)), tx, ty, x1 - tx, warnaJenuh(j));
					ty += 10;
				}
				if (ty + 8 <= batas) {
					TemaUi.meter(l, TemaUi.I_JENUH, tx, ty, x1, (j - 0.6F) / 0.4F, warnaJenuh(j), -1.0F);
				}
				ty = Math.max(ty + 12, y + pv + 4);
				Component jelas = TemaUi.k(lembar.sub(Dagang.PASAR).b(Dagang.LEWAT) ? "dagang.pasar.jual_lewat" : "dagang.pasar.jual_jelas");
				l.bungkus(jelas, x0, ty, x1 - x0, gaya.teksSamar, by - ty - 12);
				int maksJ = Math.max(1, Math.min(64, bawa));
				int pw = PemilihJumlah.lebar(l, maksJ);
				jumlah.gambar(l, "pasar_n", x1 - pw, by + 1, maksJ);
				pw += 6;
				int n = jumlah.nilai();
				// shortcut: the client does not know jenuhPerUnit, so the total shown is next-unit price x n (an upper bound)
				String kira = Brush.angka(Math.max(1L, Math.round(nb * n)));
				tombolOp(l, "pasar_jual", x0, by, x1 - x0 - pw, 15, TemaUi.k("dagang.pasar.tombol_jual", kira), true, null,
						LembarKunci.op(Dagang.OP_JUAL, e.id(), n));
			}
			default -> {
				long nb = e.l(LembarKunci.NB, 1L);
				int bawa = dibawa(e.id());
				if (ty + 10 <= batas) {
					ty += barisNilai(l, TemaUi.k("dagang.pasar.dibawa"), Component.literal(Integer.toString(bawa)), tx, ty, x1);
				}
				if (ty + 10 <= batas) {
					ty += barisNilai(l, TemaUi.k("dagang.pasar.jadi_peti"), Component.literal(Integer.toString(bawa / ISI_PETI)), tx, ty, x1);
				}
				ty = Math.max(ty + 2, y + pv + 4);
				if (ty + 16 <= batas) {
					// eight goods flowing into the seal
					int gx = x0;
					for (int i = 0; i < ISI_PETI && gx + 12 < x1 - 30; i++) {
						ikonBarang(l, e, gx, ty);
						gx += 11;
					}
					Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.PANAH_KANAN, gx + 6, ty + 3, 9, 0xC0FFFFFF);
					TemaUi.ikon(l.g, TemaUi.I_CAP, gx + 18, ty, 14);
					ty += 18;
				}
				l.bungkus(TemaUi.k("dagang.pasar.segel_jelas", ISI_PETI), x0, ty, x1 - x0, gaya.teksSamar, by - ty - 12);
				Component alasan = bawa < ISI_PETI ? TemaUi.k("dagang.pasar.segel_kurang", ISI_PETI) : !mampu(nb) ? kurang(nb) : null;
				tombolOp(l, "pasar_segel", x0, by, x1 - x0, 15, TemaUi.k("dagang.pasar.tombol_segel", Brush.angka(nb)), alasan == null, alasan,
						LembarKunci.op(Dagang.OP_SEGEL, e.id()));
			}
		}
		long u = uang();
		if (u >= 0L && by - 10 >= ty) {
			l.kanan(TemaUi.k("dagang.pasar.uang", Brush.angka(u)), x1, by - 10, gaya.teksSamar);
		}
	}
}
