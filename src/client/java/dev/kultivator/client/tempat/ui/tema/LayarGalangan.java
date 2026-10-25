package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Pratinjau3D;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Galangan;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * {@code tema_galangan} (DESIGN 38.T.3.4): the shipyard of Mbah Lunas.
 * <ul>
 * <li><b>Lunas</b>: the two hull classes (river boat, sect ship) as cards with price, build days and the band they need;
 * the chosen one is ordered with "Pesan". A running order shows the hull on the slipway (the real {@code kapal_galangan}
 * model turning), the blueprint on squared paper drawn up to the stage reached (keel, ribs, planking, mast, sail) and the
 * five stage pips with the days left; when it is ready the button becomes "Ambil kapal".</li>
 * <li><b>Layar</b> (only when the sheet offers sails): the sail styles with their price and "Pasang".</li>
 * </ul>
 * A band too low for a class locks that card with the shipwright's reason. A Di Sini page on the single kit (43.8.3); in
 * the narrow layout the class cards are compact rows with the order button under them, and a running order takes the
 * whole width (blueprint, pips, button).
 */
public final class LayarGalangan extends TemaHalaman {

	private static final String TAB_LUNAS = "lunas";
	private static final String TAB_LAYAR = "layar";

	private final Pratinjau3D pratinjau = new Pratinjau3D();
	private final DaftarGulir daftarLayar = new DaftarGulir();
	private int kelasPilih;
	private int tahapLalu = -1;
	private long tahapNaikSejak = -1L;

	public LayarGalangan(int npcId, CompoundTag lembar) {
		super(Galangan.LAYAR, npcId, lembar, "layar.galangan", TempatWarna.Gaya.KAYU);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(tabTema(TAB_LUNAS, "galangan.tab.lunas", LembarKunci.Ikon.DERMAGA),
				tabTema(TAB_LAYAR, "galangan.tab.layar", Glyphs.Atlas.KOMPAS));
	}

	@Override
	protected boolean tabAda(String id) {
		return !TAB_LAYAR.equals(id) || !lembar.daftar(Galangan.GAYA_LAYAR).isEmpty();
	}

	@Override
	protected void lembarBaru(boolean buka) {
		Lembar p = lembar.sub(Galangan.PESANAN);
		int tahap = p.ada(LembarKunci.TAHAP) ? p.i(LembarKunci.TAHAP, 0) : -1;
		if (tahapLalu >= 0 && tahap > tahapLalu) {
			tahapNaikSejak = Util.getMillis();
		}
		tahapLalu = tahap;
		List<Lembar> kelas = lembar.daftar(Galangan.KELAS);
		if (buka) {
			kelasPilih = 0;
			for (int i = 0; i < kelas.size(); i++) {
				if (kelas.get(i).bisa()) {
					kelasPilih = i;
					break;
				}
			}
		}
		kelasPilih = Math.max(0, Math.min(kelasPilih, Math.max(0, kelas.size() - 1)));
	}

	@Override
	protected void tabGanti() {
		daftarLayar.mulaiUlang(Util.getMillis());
	}

	/** K3 under Di Sini: keys 1-2 pick the hull class before an order. */
	@Override
	public boolean angka(int n) {
		List<Lembar> kelas = lembar.daftar(Galangan.KELAS);
		if (TAB_LUNAS.equals(tab) && n >= 1 && n <= kelas.size() && !lembar.ada(Galangan.PESANAN)) {
			kelasPilih = n - 1;
			return true;
		}
		return false;
	}

	@Override
	protected Component petunjuk() {
		return TAB_LUNAS.equals(tab) && !lembar.ada(Galangan.PESANAN) ? TemaUi.k("galangan.kunci") : null;
	}

	@Override
	protected void lukisTema(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar p = lembar.sub(Galangan.PESANAN);
		boolean ada = p.ada(Galangan.SIAP) || p.ada(LembarKunci.TAHAP);
		boolean sempit = tata == Tata.SEMPIT;
		Component ucap = TemaUi.k(ada ? p.b(Galangan.SIAP) ? "galangan.ucap_siap" : "galangan.ucap_kerja" : "galangan.ucap");
		int y = y0 + strip(l, ucap, x0, y0, x1, sempit ? 30 : 34);
		if (TAB_LAYAR.equals(tab)) {
			layar(l, x0, y, x1, y1);
			return;
		}
		if (sempit) {
			if (ada) {
				pesanan(l, new Kotak(x0, y, x1, y1), p);
			} else {
				kelasSempit(l, new Kotak(x0, y, x1, y1));
			}
			return;
		}
		Kotak[] k = bagi(x0, y, x1, y1);
		if (ada) {
			kelasRingkas(l, k[0], p);
			pesanan(l, k[1], p);
		} else {
			kelas(l, k[0]);
			detailKelas(l, k[1]);
		}
	}

	// ------------------------------------------------------------------------------------------------ hull classes

	private void kelas(Lukis l, Kotak k) {
		List<Lembar> kelas = lembar.daftar(Galangan.KELAS);
		int y = k.y0() + subjudul(l, TemaUi.k("galangan.pilih_lunas"), k.x0(), k.y0(), k.x1());
		if (kelas.isEmpty()) {
			kosong(l, TemaUi.k("galangan.kosong"), k.x0(), y, k.x1(), k.y1());
			return;
		}
		int pita = lembar.i(LembarKunci.PITA, -1);
		int h = Math.max(32, Math.min(48, (k.y1() - y) / Math.max(1, kelas.size()) - 3));
		for (int i = 0; i < kelas.size() && y + h <= k.y1(); i++) {
			Lembar e = kelas.get(i);
			boolean pilih = i == kelasPilih;
			boolean over = l.di(k.x0(), y, k.x1(), y + h);
			l.kartu(k.x0(), y, k.x1(), y + h, over ? 1.0F : 0.0F, pilih);
			boolean sekte = "sekte".equals(e.id());
			lambung(l, k.x0() + 4, y + 4, 34, h - 8, sekte, e.bisa() ? 1.0F : 0.4F);
			int tx = k.x0() + 42;
			int warnaNama = e.bisa() ? aksen() : Palette.terkunci(gaya.teks);
			l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, y + 3, k.x1() - tx - 18, warnaNama);
			int pitaMin = e.i(Galangan.PITA_MIN, 0);
			boolean pitaCukup = pita < 0 || pita >= pitaMin;
			Component hari = TemaUi.k("galangan.hari", e.i(LembarKunci.HARI, sekte ? 4 : 2));
			int hw = Math.min(70, font.width(hari) + 6);
			l.teks(hari, tx, y + 13, hw, gaya.teksLemah);
			l.teks(TemaUi.k("galangan.pita_min", pitaMin), tx + hw, y + 13, k.x1() - tx - hw - 4, pitaCukup ? gaya.teksSamar : l.gaya.turun());
			if (h >= 34) {
				LabelHarga.gambar(l, e.l(LembarKunci.HARGA, 0L), uang(), tx, y + 23, false, true);
			}
			if (!e.bisa()) {
				Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.GEMBOK, k.x1() - 16, y + 3, 12, 0xFFFFFFFF);
			}
			final int idx = i;
			l.zona("kelas" + i, k.x0(), y, k.x1(), y + h, () -> kelasPilih = idx, true, null, true);
			l.fokusCincin("kelas" + i, k.x0(), y, k.x1(), y + h);
			l.kanan(Component.literal(Integer.toString(i + 1)), k.x1() - 4, y + h - 10, gaya.teksSamar);
			y += h + 3;
		}
	}

	/** Narrow layout: compact class rows (hull, name, days, price), then the chosen class's order button. */
	private void kelasSempit(Lukis l, Kotak k) {
		List<Lembar> kelas = lembar.daftar(Galangan.KELAS);
		int x0 = k.x0();
		int x1 = k.x1();
		int y = k.y0() + subjudul(l, TemaUi.k("galangan.pilih_lunas"), x0, k.y0(), x1);
		if (kelas.isEmpty()) {
			kosong(l, TemaUi.k("galangan.kosong"), x0, y, x1, k.y1());
			return;
		}
		int by = k.y1() - 17;
		int pita = lembar.i(LembarKunci.PITA, -1);
		int h = 24;
		for (int i = 0; i < kelas.size() && y + h <= by - 12; i++) {
			Lembar e = kelas.get(i);
			boolean sekte = "sekte".equals(e.id());
			boolean over = l.di(x0, y, x1, y + h);
			l.kartu(x0, y, x1, y + h, over ? 1.0F : 0.0F, i == kelasPilih);
			lambung(l, x0 + 3, y + 2, 26, h - 4, sekte, e.bisa() ? 1.0F : 0.4F);
			int tx = x0 + 33;
			Component harga = Brush.nb(e.l(LembarKunci.HARGA, 0L), true);
			int hw = font.width(harga);
			l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, y + 2, x1 - tx - hw - 22, e.bisa() ? aksen() : Palette.terkunci(gaya.teks));
			l.kanan(harga, x1 - 16, y + 2, mampu(e.l(LembarKunci.HARGA, 0L)) ? gaya.teks : l.gaya.turun());
			int pitaMin = e.i(Galangan.PITA_MIN, 0);
			boolean pitaCukup = pita < 0 || pita >= pitaMin;
			Component baris = TemaUi.k("galangan.hari", e.i(LembarKunci.HARI, sekte ? 4 : 2)).copy().append(" · ")
					.append(TemaUi.k("galangan.pita_min", pitaMin));
			l.teks(baris, tx, y + 13, x1 - tx - 18, pitaCukup ? gaya.teksSamar : l.gaya.turun());
			if (!e.bisa()) {
				Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.GEMBOK, x1 - 14, y + 6, 11, 0xFFFFFFFF);
			} else {
				l.kanan(Component.literal(Integer.toString(i + 1)), x1 - 4, y + 13, gaya.teksSamar);
			}
			final int idx = i;
			l.zona("kelas" + i, x0, y, x1, y + h, () -> kelasPilih = idx, true, null, true);
			l.fokusCincin("kelas" + i, x0, y, x1, y + h);
			y += h + 2;
		}
		tombolPesan(l, kelas.get(Math.min(kelasPilih, kelas.size() - 1)), x0, by, x1);
	}

	/** "Pesan (n NB)" for class {@code e}, its reason above when locked. */
	private void tombolPesan(Lukis l, Lembar e, int x0, int by, int x1) {
		long harga = e.l(LembarKunci.HARGA, 0L);
		boolean bisa = e.bisa() && mampu(harga);
		Component alasan = !e.bisa() ? alasanKeadaan(e) : !mampu(harga) ? kurang(harga) : null;
		tombolOp(l, "pesan", x0, by, x1 - x0, 15, TemaUi.k("galangan.pesan", Brush.angka(harga)), bisa, alasan,
				LembarKunci.op(Galangan.OP_PESAN, e.id()));
		if (!e.bisa() && alasan != null) {
			l.teks(alasan, x0, by - 10, x1 - x0, l.gaya.turun());
		}
	}

	private void detailKelas(Lukis l, Kotak k) {
		l.kartu(k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		List<Lembar> kelas = lembar.daftar(Galangan.KELAS);
		if (kelas.isEmpty()) {
			tengah(l, TemaUi.k("galangan.kosong"), k);
			return;
		}
		Lembar e = kelas.get(Math.min(kelasPilih, kelas.size() - 1));
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		boolean sekte = "sekte".equals(e.id());
		y += subjudul(l, e.teks(LembarKunci.NAMA), x0, y, x1);
		int by = k.y1() - 19;
		int bpH = Math.max(30, Math.min(80, by - y - 46));
		cetakBiru(l, x0, y, x1, y + bpH, sekte, 5, 0.0F);
		y += bpH + 4;
		y += Math.round(l.bungkus(TemaUi.k(sekte ? "galangan.kelas_sekte" : "galangan.kelas_perahu"), x0, y, x1 - x0, gaya.teksLemah,
				Math.max(9, by - y - 24))) + 2;
		if (y + 10 <= by - 12) {
			barisNilai(l, TemaUi.k("galangan.lama"), Brush.hari(e.i(LembarKunci.HARI, sekte ? 4 : 2)), x0, y, x1);
		}
		tombolPesan(l, e, x0, by, x1);
	}

	private void kelasRingkas(Lukis l, Kotak k, Lembar p) {
		l.kartu(k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		int x0 = k.x0() + 4;
		int x1 = k.x1() - 4;
		int y = k.y0() + 4;
		y += subjudul(l, TemaUi.k("galangan.di_landasan"), x0, y, x1);
		int pv = Math.max(30, Math.min(k.y1() - y - 16, x1 - x0));
		String entitas = p.s(LembarKunci.ENTITAS, "kultivator:kapal_galangan");
		pratinjau.gambar(l, "kapal_3d", x0, y, x1, y + pv, ItemStack.EMPTY, entitas, LembarKunci.Ikon.DERMAGA);
		y += pv + 3;
		for (Lembar e : lembar.daftar(Galangan.KELAS)) {
			if (e.id().equals(p.s(Galangan.KELAS)) && y + 9 <= k.y1()) {
				l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), x0, y, x1 - x0, aksen());
			}
		}
	}

	private void pesanan(Lukis l, Kotak k, Lembar p) {
		l.kartu(k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		int total = Math.max(1, p.i(LembarKunci.TAHAP_TOTAL, 5));
		int tahap = Math.max(0, Math.min(total - 1, p.i(LembarKunci.TAHAP, 0)));
		boolean siap = p.b(Galangan.SIAP);
		y += subjudul(l, TemaUi.k("galangan.pesananmu"), x0, y, x1);
		int by = k.y1() - 19;
		int bpH = Math.max(24, Math.min(84, by - y - 52));
		float naik = tahapNaikSejak >= 0L ? Math.min(1.0F, (l.kini - tahapNaikSejak) / 900.0F) : 1.0F;
		cetakBiru(l, x0, y, x1, y + bpH, "sekte".equals(p.s(Galangan.KELAS)), siap ? total : tahap + 1, naik);
		y += bpH + 4;
		// the five stage pips
		int pipW = (x1 - x0) / total;
		for (int i = 0; i < total; i++) {
			int cx = x0 + pipW * i + pipW / 2;
			boolean lewat = siap || i < tahap;
			boolean kini = !siap && i == tahap;
			if (i > 0) {
				l.g.fill(cx - pipW + 6, y + 5, cx - 6, y + 6, lewat || kini ? Palette.GOLD : Palette.argb(0x50, 0x000000));
			}
			Brush.disc(l.g, cx, y + 5.5F, 5.0F, lewat ? Palette.OK : kini ? Palette.GOLD : Palette.argb(0x60, 0x000000));
			if (kini) {
				Brush.halo(l.g, cx, y + 5.5F, 5.5F, 1.5F, Palette.GOLD_LIGHT & 0xFFFFFF, l.denyut());
			}
			if (lewat) {
				Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.CENTANG, cx - 4, y + 2, 8, 0xFFFFFFFF);
			}
			Component nama = TemaUi.k("galangan.tahap." + Math.min(i, 4));
			l.tengah(nama, cx, y + 13, pipW - 2, kini ? aksen() : gaya.teksSamar);
			if (l.di(cx - pipW / 2, y, cx + pipW / 2, y + 22)) {
				l.tip(nama);
			}
		}
		y += 25;
		if (siap) {
			l.bungkus(TemaUi.k("galangan.siap"), x0, y, x1 - x0, Palette.OK, Math.max(9, by - y - 2));
		} else if (y + 20 <= by - 2) {
			y += barisNilai(l, TemaUi.k("galangan.tahap_kini"), TemaUi.k("galangan.tahap." + Math.min(tahap, 4)), x0, y, x1);
			barisNilai(l, TemaUi.k("galangan.sisa"), Brush.hari(p.i(LembarKunci.SISA_HARI, 0)), x0, y, x1);
		} else if (y + 10 <= by - 2) {
			barisNilai(l, TemaUi.k("galangan.sisa"), Brush.hari(p.i(LembarKunci.SISA_HARI, 0)), x0, y, x1);
		}
		tombolOp(l, "ambil", x0, by, x1 - x0, 15, TemaUi.k(siap ? "galangan.ambil" : "galangan.belum_siap"), siap,
				siap ? null : TemaUi.k("galangan.tunggu", Brush.hari(p.i(LembarKunci.SISA_HARI, 0))), Galangan.OP_AMBIL);
	}

	// ------------------------------------------------------------------------------------------------ sails

	private void layar(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> semua = lembar.daftar(Galangan.GAYA_LAYAR);
		Kotak[] k = bagi(x0, y0, x1, y1);
		int ly = k[0].y0() + subjudul(l, TemaUi.k("galangan.layar_judul"), k[0].x0(), k[0].y0(), k[0].x1());
		if (semua.isEmpty()) {
			kosong(l, TemaUi.k("galangan.layar_kosong"), k[0].x0(), ly, k[0].x1(), k[0].y1());
			return;
		}
		long uang = uang();
		daftarLayar.gambar(l, "layar", k[0].x0(), ly, k[0].x1(), k[0].y1(), semua.size(), 20, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = semua.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			TemaUi.ikon(lk.g, TemaUi.I_LAYAR, rx0 + 3, ry0 + 2, 16, warnaLayar(i));
			int pw = LabelHarga.gambar(lk, e.l(LembarKunci.HARGA, 60L), uang, rx1 - 4, ry0 + 6, true, true);
			lk.teks(e.teks(LembarKunci.NAMA), rx0 + 22, ry0 + 6, rx1 - rx0 - 30 - pw, gaya.teks);
		}, this::pasangLayar, null);
		int sel = daftarLayar.pilihan();
		Kotak d = k[1];
		l.kartu(d.x0(), d.y0(), d.x1(), d.y1(), 0.0F, false);
		if (sel < 0 || sel >= semua.size()) {
			tengah(l, TemaUi.k("galangan.layar_pilih"), d);
			return;
		}
		Lembar e = semua.get(sel);
		int dx0 = d.x0() + 5;
		int dx1 = d.x1() - 5;
		int dy = d.y0() + 4;
		dy += subjudul(l, e.teks(LembarKunci.NAMA), dx0, dy, dx1);
		int by = d.y1() - 19;
		int sh = Math.min(70, by - dy - 30);
		if (sh >= 20) {
			gambarLayar(l, (dx0 + dx1) / 2, dy, sh, warnaLayar(sel));
			dy += sh + 6;
		}
		if (by - dy >= 11) {
			l.bungkus(TemaUi.k("galangan.layar_jelas"), dx0, dy, dx1 - dx0, gaya.teksLemah, by - dy - 2);
		}
		long harga = e.l(LembarKunci.HARGA, 60L);
		tombolOp(l, "pasang", dx0, by, dx1 - dx0, 15, TemaUi.k("galangan.pasang", Brush.angka(harga)), mampu(harga),
				mampu(harga) ? null : kurang(harga), LembarKunci.op(Galangan.OP_LAYAR, e.id()));
	}

	private void pasangLayar() {
		List<Lembar> semua = lembar.daftar(Galangan.GAYA_LAYAR);
		int i = daftarLayar.pilihan();
		if (i >= 0 && i < semua.size() && tulisBoleh() && mampu(semua.get(i).l(LembarKunci.HARGA, 60L))) {
			kirim(LembarKunci.op(Galangan.OP_LAYAR, semua.get(i).id()));
		}
	}

	private static int warnaLayar(int i) {
		int[] w = {0xFFF4ECD4, 0xFFC84A3A, 0xFF3E6EA8, 0xFF2E8963, 0xFFD4A637, 0xFF6A3A8A, 0xFF1E1E24, 0xFFE89AB0};
		return w[Math.floorMod(i, w.length)];
	}

	/** A junk sail with battens, centred on cx, {@code h} high, tinted. */
	private void gambarLayar(Lukis l, int cx, int y, int h, int warna) {
		int w = Math.round(h * 0.8F);
		int tiangX = cx - w / 2 + 3;
		l.g.fill(tiangX - 1, y, tiangX + 1, y + h + 6, 0xFF5A3A20);
		float kibar = (float) Math.sin(l.kini / 420.0D) * 1.5F;
		int batten = 5;
		for (int b = 0; b < batten; b++) {
			int by0 = y + 2 + b * (h - 4) / batten;
			int by1 = y + 2 + (b + 1) * (h - 4) / batten;
			int lebar = Math.round(w * (0.62F + 0.38F * (b + 1) / batten) + kibar * (b % 2 == 0 ? 1 : -1));
			l.g.fillGradient(tiangX + 1, by0, tiangX + 1 + lebar, by1, Palette.lighten(warna, 0.12F), Palette.darken(warna, 0.12F));
			l.g.fill(tiangX + 1, by1 - 1, tiangX + 2 + lebar, by1, 0xFF3A2414);
		}
		Brush.frame(l.g, tiangX + 1, y + 2, tiangX + 2 + w, y + h - 1, Palette.argb(0x50, 0x000000));
	}

	// ------------------------------------------------------------------------------------------------ drawings

	/** A small hull silhouette for the class cards (boat: one mast; sect ship: two masts and a cabin). */
	private void lambung(Lukis l, int x, int y, int w, int h, boolean sekte, float alfa) {
		int a = Math.round(255 * alfa);
		int kayu = Palette.argb(a, 0x8A5A30);
		int gelap = Palette.argb(a, 0x4A2A14);
		int dasar = y + h - 6;
		for (int i = 0; i < 6; i++) {
			int inset = 1 + i;
			l.g.fill(x + inset, dasar - 6 + i, x + w - inset, dasar - 5 + i, i < 2 ? kayu : gelap);
		}
		l.g.fill(x - 1, dasar - 8, x + w + 1, dasar - 6, gelap);
		int tiang = sekte ? 2 : 1;
		for (int t = 0; t < tiang; t++) {
			int tx = x + w * (t + 1) / (tiang + 1);
			l.g.fill(tx, y + 2, tx + 1, dasar - 8, gelap);
			l.g.fill(tx + 1, y + 3, tx + 1 + w / (tiang + 2), dasar - 11, Palette.argb(a, sekte ? 0xC84A3A : 0xF4ECD4));
		}
		if (sekte) {
			l.g.fill(x + w - 9, dasar - 13, x + w - 2, dasar - 8, kayu);
		}
		l.g.fill(x - 2, dasar, x + w + 2, dasar + 1, Palette.argb(a / 2, 0x3E86B0));
	}

	/**
	 * The blueprint on squared blue paper, drawn up to {@code tahap} of 5 (1 keel, 2 ribs, 3 planking, 4 mast, 5 sail);
	 * the newest stage draws itself in over {@code naik} 0..1.
	 */
	private void cetakBiru(Lukis l, int x0, int y0, int x1, int y1, boolean sekte, int tahap, float naik) {
		l.g.fill(x0, y0, x1, y1, 0xFF20405E);
		for (int gx = x0 + 6; gx < x1; gx += 6) {
			l.g.fill(gx, y0, gx + 1, y1, 0x2CFFFFFF);
		}
		for (int gy = y0 + 6; gy < y1; gy += 6) {
			l.g.fill(x0, gy, x1, gy + 1, 0x2CFFFFFF);
		}
		Brush.frame(l.g, x0, y0, x1, y1, 0xA0D8E8F4);
		int ink = 0xFFE8F2FA;
		int baru = 0xFFF7DC7E;
		float w = x1 - x0 - 16;
		float h = y1 - y0 - 10;
		float kx0 = x0 + 8;
		float kx1 = x0 + 8 + w;
		float garisAir = y0 + 5 + h * 0.78F;
		float dek = y0 + 5 + h * 0.58F;
		// 1 keel
		if (tahap >= 1) {
			float t = tahap == 1 ? naik : 1.0F;
			int warna = tahap == 1 ? baru : ink;
			Brush.line(l.g, kx0 + w * 0.1F, garisAir, kx0 + w * 0.1F + w * 0.8F * t, garisAir, 1.5F, warna);
			Brush.line(l.g, kx0, dek, kx0 + w * 0.1F, garisAir, 1.0F, warna);
			Brush.line(l.g, kx1, dek - 2, kx1 - w * 0.1F, garisAir, 1.0F, warna);
		}
		// 2 ribs
		if (tahap >= 2) {
			int n = sekte ? 9 : 6;
			int tampil = tahap == 2 ? Math.round(n * naik) : n;
			int warna = tahap == 2 ? baru : ink;
			for (int i = 0; i < tampil; i++) {
				float rx = kx0 + w * (0.14F + 0.72F * i / (n - 1));
				Brush.line(l.g, rx, garisAir, rx - 1.5F, dek, 1.0F, warna);
			}
		}
		// 3 planking
		if (tahap >= 3) {
			int warna = tahap == 3 ? Palette.argb(Math.round(0xA0 * naik), 0xF7DC7E) : 0x90E8F2FA;
			for (float py = dek + 2; py < garisAir; py += 3) {
				float f = (py - dek) / (garisAir - dek);
				Brush.line(l.g, kx0 + w * 0.1F * f, py, kx1 - w * 0.1F * f, py, 0.8F, warna);
			}
			Brush.line(l.g, kx0, dek, kx1, dek - 2, 1.5F, tahap == 3 ? baru : ink);
		}
		// 4 masts
		int tiang = sekte ? 2 : 1;
		if (tahap >= 4) {
			float t = tahap == 4 ? naik : 1.0F;
			int warna = tahap == 4 ? baru : ink;
			for (int i = 0; i < tiang; i++) {
				float mx = kx0 + w * (i + 1) / (tiang + 1);
				Brush.line(l.g, mx, dek, mx, dek - (dek - y0 - 4) * t, 1.5F, warna);
			}
			if (sekte) {
				Brush.rect(l.g, kx1 - w * 0.2F, dek - 7, kx1 - w * 0.06F, dek - 1, Palette.argb(0x50, 0xE8F2FA));
			}
		}
		// 5 sails
		if (tahap >= 5) {
			int warna = Palette.argb(Math.round(0xB0 * (tahap == 5 ? naik : 1.0F)), 0xF4ECD4);
			for (int i = 0; i < tiang; i++) {
				float mx = kx0 + w * (i + 1) / (tiang + 1);
				float top = y0 + 6;
				float sw = w / (tiang + 2.2F);
				for (int b = 0; b < 4; b++) {
					float sy0 = top + (dek - top - 6) * b / 4.0F;
					float sy1 = top + (dek - top - 6) * (b + 1) / 4.0F;
					Brush.rect(l.g, mx + 1, sy0, mx + 1 + sw * (0.7F + 0.1F * b), sy1 - 0.8F, warna);
				}
			}
		}
		// waterline and the stamp (the caption only when the paper is tall enough for it beside the drawing)
		for (float wx = x0 + 2; wx < x1 - 4; wx += 6) {
			l.g.fill(Math.round(wx), Math.round(garisAir) + 3, Math.round(wx) + 3, Math.round(garisAir) + 4, 0x6098C8E8);
		}
		if (y1 - y0 >= 40) {
			l.teks(TemaUi.k("galangan.cetak_biru"), x0 + 3, y0 + 2, (x1 - x0) / 2, 0xB0D8E8F4);
		} else if (l.di(x0, y0, x1, y1)) {
			l.tip(TemaUi.k("galangan.cetak_biru"));
		}
	}
}
