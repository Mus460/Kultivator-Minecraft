package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.CincinWaktu;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.KartuBarang;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.PemilihJumlah;
import dev.kultivator.client.tempat.ui.kit.Pratinjau3D;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Kapal;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * {@code tema_kapal} (DESIGN 38.T.3.4): the moored merchant ship, talked to through its NAKHODA_LAUT on deck.
 * <ul>
 * <li><b>Muatan</b>: the ship's card (the real ship model turning, origin, berth, departure countdown), its cargo as goods
 * cards with the turning preview and an amount picker, and the berth schedule along the bottom (arriving, moored,
 * leaving, empty).</li>
 * <li><b>Kapal Lintas Laut</b> (Kapten Gao Fan): the crossing to the docks of the Laut Tanpa Batas, the sailing hour,
 * the fare, up to two disciples along, and the sea chart.</li>
 * </ul>
 * A storm stops every departure (warning band, buttons off).
 */
public final class LayarKapal extends LayarTemaDasar {

	private final DaftarGulir daftar = new DaftarGulir();
	private final Pratinjau3D pratinjauKapal = new Pratinjau3D();
	private final Pratinjau3D pratinjauBarang = new Pratinjau3D();
	private final PemilihJumlah jumlah = new PemilihJumlah();
	private int murid;

	public LayarKapal(int npcId, CompoundTag lembar) {
		super(Kapal.LAYAR, npcId, lembar, "layar.kapal", TempatWarna.Gaya.KAYU);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(tabTema(Kapal.TAB_MUATAN, "kapal.tab.muatan", LembarKunci.Ikon.DERMAGA),
				tabTema(Kapal.TAB_LINTAS, "kapal.tab.lintas", IkonTempat.KOMPAS));
	}

	@Override
	protected boolean tabAda(String id) {
		return !Kapal.TAB_LINTAS.equals(id) || lembar.ada(Kapal.LINTAS);
	}

	@Override
	protected Component petunjuk() {
		return Kapal.TAB_MUATAN.equals(tab) ? TeksTempat.k("kunci.jumlah") : null;
	}

	@Override
	protected void tabGanti() {
		daftar.mulaiUlang(Util.getMillis());
		jumlah.set(1);
	}

	@Override
	protected void gambar(Lukis l, int x0, int y0, int x1, int y1) {
		int y = y0;
		if (lembar.b(Kapal.BADAI)) {
			y += pita(l, TemaUi.I_OMBAK, TemaUi.k("kapal.badai"), x0, y, x1, 0xFF3E86B0);
		}
		if (Kapal.TAB_LINTAS.equals(tab)) {
			lintas(l, x0, y, x1, y1);
		} else {
			muatan(l, x0, y, x1, y1);
		}
	}

	// ------------------------------------------------------------------------------------------------ cargo

	private void muatan(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar kapal = lembar.sub(Kapal.KAPAL);
		List<Lembar> jadwal = lembar.daftar(Kapal.JADWAL);
		int jadwalH = jadwal.isEmpty() ? 0 : 24;
		int y = y0;
		if (kapal.ada(LembarKunci.NAMA)) {
			y += kartuKapal(l, kapal, x0, y, x1);
		}
		Kotak[] k = bagi(x0, y, x1, y1 - jadwalH);
		List<Lembar> muatan = lembar.daftar(Kapal.MUATAN);
		Kotak kiri = k[0];
		if (muatan.isEmpty()) {
			kosong(l, TemaUi.k(kapal.ada(LembarKunci.NAMA) ? "kapal.muatan_kosong" : "kapal.belum_datang"), kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1());
		} else {
			long uang = uang();
			long muncul = diterima;
			daftar.gambar(l, "muatan", kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1(), muatan.size(), 22, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> KartuBarang
					.gambar(lk, muatan.get(i), rx0, ry0, rx1, ry1, sorot, pilih, uang, muncul), this::beli, i -> jumlah.set(1));
		}
		int sel = daftar.pilihan();
		detail(l, k[1], sel >= 0 && sel < muatan.size() ? muatan.get(sel) : null);
		if (jadwalH > 0) {
			jadwal(l, jadwal, x0, y1 - jadwalH + 3, x1, y1);
		}
	}

	private int kartuKapal(Lukis l, Lembar kapal, int x0, int y0, int x1) {
		int h = tata == Tata.SEMPIT ? 30 : 44;
		PanelTempat.kartu(l.g, gaya, x0, y0, x1, y0 + h, 0.0F, false);
		int pv = h - 4;
		String entitas = kapal.s(LembarKunci.ENTITAS, "kultivator:kapal_dagang");
		pratinjauKapal.gambar(l, "kapal_3d", x0 + 2, y0 + 2, x0 + 2 + pv * 3 / 2, y0 + 2 + pv, ItemStack.EMPTY, entitas,
				LembarKunci.Ikon.DERMAGA);
		int tx = x0 + 6 + pv * 3 / 2;
		l.teks(Brush.bold(kapal.teks(LembarKunci.NAMA)), tx, y0 + 4, x1 - tx - 60, aksen());
		l.keterangan(TemaUi.k("kapal.asal", kapal.teks(Kapal.ASAL), kapal.i(Kapal.TAMBATAN, 1)), tx, y0 + 15, x1 - tx - 60, gaya.teksLemah);
		if (kapal.ada(Kapal.BERANGKAT_TICK) && h > 30) {
			l.keterangan(TemaUi.k("kapal.berangkat_dalam"), tx, y0 + 26, x1 - tx - 60, gaya.teksSamar);
		}
		if (kapal.ada(Kapal.BERANGKAT_TICK)) {
			long total = 12000L;
			CincinWaktu.gambar(l, x1 - 22, y0 + h / 2.0F, 12.0F, kapal.l(Kapal.BERANGKAT_TICK, 0L), total, diterima, CincinWaktu.KIRI);
		}
		return h + 4;
	}

	private void beli() {
		List<Lembar> muatan = lembar.daftar(Kapal.MUATAN);
		int i = daftar.pilihan();
		if (i >= 0 && i < muatan.size() && muatan.get(i).bisa() && tulisBoleh()) {
			kirim(LembarKunci.op(Kapal.OP_BELI, muatan.get(i).id(), jumlah.nilai()));
		}
	}

	private void detail(Lukis l, Kotak k, Lembar e) {
		PanelTempat.kartu(l.g, gaya, k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		if (e == null) {
			tengah(l, TemaUi.k("kapal.pilih"), k);
			return;
		}
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		int pv = tata == Tata.SEMPIT ? 30 : tata == Tata.LEBAR ? 84 : 56;
		pratinjauBarang.gambar(l, "barang_3d", x0, y, x0 + pv, y + pv, e.barang(), null, TemaUi.ikonBarang(e.id()) < 0 ? LembarKunci.Ikon.HERBA : -1);
		if (e.barang().isEmpty()) {
			TemaUi.ikon(l.g, TemaUi.ikonBarang(e.id()), x0 + pv / 2 - 12, y + pv / 2 - 14, 24);
		}
		int tx = x0 + pv + 6;
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, y, x1 - tx, TempatWarna.mutu(e.i(LembarKunci.MUTU, -1), gaya.gelap(), gaya.teks));
		int ty = y + 11;
		Component catatan = e.teksAtau(LembarKunci.CATATAN);
		if (catatan != null) {
			ty += Math.round(l.bungkus(catatan, tx, ty, x1 - tx, gaya.teksLemah, 0.75F)) + 2;
		}
		int stok = e.i(LembarKunci.STOK, -1);
		l.keterangan(stok < 0 ? TeksTempat.k("kartu.stok_tak_terbatas") : TeksTempat.k("kartu.stok", stok), tx, ty, x1 - tx, gaya.teksSamar);
		ty += 10;
		LabelHarga.gambar(l, e.l(LembarKunci.HARGA, 0L), uang(), tx, ty, false, 1.0F, false);
		int by = k.y1() - 19;
		int maks = stok < 0 ? 64 : Math.max(1, stok);
		int pw = jumlah.gambar(l, "jumlah", x1 - 50, by + 1, maks);
		long total = e.l(LembarKunci.HARGA, 0L) * jumlah.nilai();
		boolean badai = lembar.b(Kapal.BADAI);
		boolean bisa = e.bisa() && mampu(total);
		Component alasan = !e.bisa() ? alasanKeadaan(e) : !mampu(total) ? kurang(total) : null;
		tombolOp(l, "beli", x0, by, x1 - x0 - pw - 6, 15, TemaUi.k("kapal.beli", TeksTempat.angka(total)), bisa, alasan,
				LembarKunci.op(Kapal.OP_BELI, e.id(), jumlah.nilai()));
		if (badai) {
			l.keterangan(TemaUi.k("kapal.badai_beli"), x0, by - 10, x1 - x0, gaya.teksSamar);
		}
	}

	private void jadwal(Lukis l, List<Lembar> jadwal, int x0, int y0, int x1, int y1) {
		PanelTempat.garis(l.g, gaya, x0, x1, y0 - 2);
		l.keterangan(TemaUi.k("kapal.jadwal"), x0, y0 + 1, 60, gaya.teksLemah);
		int n = Math.min(jadwal.size(), 6);
		int w = Math.max(40, (x1 - x0 - 62) / Math.max(1, n));
		for (int i = 0; i < n; i++) {
			Lembar j = jadwal.get(i);
			int cx = x0 + 62 + i * w;
			String s = j.s(LembarKunci.KEADAAN);
			int warna = switch (s) {
				case "datang" -> 0xFF6FA8C8;
				case "tambat" -> TempatWarna.OK;
				case "berangkat" -> TempatWarna.PERINGATAN;
				default -> 0xFF8D928E;
			};
			l.g.fill(cx, y0, cx + w - 3, y1 - 2, Palette.argb(0x50, warna & 0xFFFFFF));
			l.g.fill(cx, y0, cx + 2, y1 - 2, warna);
			Brush.text(l.g, font, TemaUi.k("kapal.tambatan", j.i(Kapal.TAMBATAN, i + 1)), cx + 4, y0 + 2, gaya.teks, 0.6F, false);
			Component isi = "kosong".equals(s) ? TemaUi.k("kapal.j.kosong") : j.teks(Kapal.ASAL);
			l.teks(isi, cx + 4, y0 + 10, w - 9, gaya.teksLemah, 0.6F);
			if (l.di(cx, y0, cx + w - 3, y1 - 2)) {
				l.tip(List.of(TemaUi.k("kapal.j." + (s.isEmpty() ? "kosong" : s)), j.teks(Kapal.ASAL), TemaUi.k("kapal.j.hari", j.i(Kapal.DATANG_HARI, 0))));
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ crossing

	private void lintas(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar lintas = lembar.sub(Kapal.LINTAS);
		boolean badai = lembar.b(Kapal.BADAI) || lintas.b(Kapal.BADAI);
		int y = y0 + strip(l, TemaUi.k(badai ? "kapal.ucap_badai" : "kapal.ucap_lintas"), x0, y0, x1, 36);
		Kotak[] k = bagi(x0, y, x1, y1);
		peta(l, k[0], badai);
		Kotak kanan = k[1];
		PanelTempat.kartu(l.g, gaya, kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1(), 0.0F, false);
		int rx0 = kanan.x0() + 5;
		int rx1 = kanan.x1() - 5;
		int ry = kanan.y0() + 4;
		ry += subjudul(l, TemaUi.k("kapal.lintas_judul"), rx0, ry, rx1);
		ry += barisNilai(l, TemaUi.k("kapal.berlayar_jam"), lintas.teks(Kapal.BERANGKAT), rx0, ry, rx1);
		long ongkos = lintas.l(Kapal.ONGKOS, 0L);
		ry += barisNilai(l, TemaUi.k("kapal.ongkos"), TeksTempat.nb(ongkos, false), rx0, ry, rx1) + 4;
		int maksMurid = Math.max(0, Math.min(2, lintas.i(Kapal.MURID, 0)));
		murid = Math.min(murid, maksMurid);
		l.keterangan(TemaUi.k("kapal.bawa_murid"), rx0, ry + 2, 70, gaya.teksLemah);
		for (int m = 0; m <= maksMurid; m++) {
			int cx = rx0 + 72 + m * 18;
			boolean on = m == murid;
			final int mm = m;
			Brush.disc(l.g, cx + 7, ry + 6, 7.0F, on ? Palette.GOLD : Palette.argb(0x60, 0x000000));
			Brush.centered(l.g, font, Component.literal(Integer.toString(m)), cx + 7, ry + 2, on ? 0xFF3A2208 : gaya.teksLemah, 1.0F, false);
			l.zona("murid" + m, cx, ry, cx + 14, ry + 13, () -> murid = mm, true, null, true);
			l.fokusCincin("murid" + m, cx, ry, cx + 14, ry + 13);
		}
		ry += 18;
		l.bungkus(TemaUi.k("kapal.lintas_jelas"), rx0, ry, rx1 - rx0, gaya.teksSamar, 0.7F);
		boolean bisa = lintas.b(LembarKunci.BISA) && !badai && mampu(ongkos);
		Component alasan = badai ? TemaUi.k("kapal.badai_tolak") : !mampu(ongkos) ? kurang(ongkos) : lintas.teksAtau(LembarKunci.ALASAN);
		tombolOp(l, "lintas", rx0, kanan.y1() - 19, rx1 - rx0, 15, TemaUi.k("kapal.berlayar", TeksTempat.angka(ongkos)), bisa, alasan,
				LembarKunci.op(Kapal.OP_LINTAS, murid));
	}

	/** The sea chart: harbour, dotted course over the waves, the Laut Tanpa Batas docks, a ship riding the course. */
	private void peta(Lukis l, Kotak k, boolean badai) {
		int x0 = k.x0();
		int y0 = k.y0();
		int x1 = k.x1();
		int y1 = k.y1();
		PanelTempat.sumur(l.g, gaya, x0, y0, x1, y1);
		l.klip(x0 + 1, y0 + 1, x1 - 1, y1 - 1);
		l.g.fillGradient(x0, y0, x1, y1, badai ? 0xFF3A4A5A : 0xFF8AB8D0, badai ? 0xFF1E2A36 : 0xFF3E7AA0);
		long kini = l.kini;
		for (int row = 0; row < (y1 - y0) / 8; row++) {
			int wy = y0 + 6 + row * 8;
			int off = (int) ((kini / (badai ? 25L : 60L) + row * 7L) % 16L);
			for (int wx = x0 - 16 + off; wx < x1; wx += 16) {
				l.g.fill(wx, wy, wx + 6, wy + 1, Palette.argb(badai ? 0x90 : 0x60, 0xFFFFFF));
			}
		}
		int ax = x0 + 14;
		int ay = y1 - 18;
		int bx = x1 - 18;
		int by = y0 + 16;
		TemaUi.ikon(l.g, TemaUi.I_PELABUHAN, ax - 8, ay - 8, 16);
		TemaUi.ikon(l.g, TemaUi.I_SUAR, ax + 4, ay - 18, 12);
		Brush.disc(l.g, bx, by, 9.0F, Palette.argb(0xC0, 0xE8D8A8));
		IkonTempat.gambar(l.g, LembarKunci.Ikon.DERMAGA, bx - 8, by - 8, 16, 0xFFFFFFFF);
		for (int i = 0; i <= 20; i++) {
			float t = i / 20.0F;
			float px = ax + (bx - ax) * t;
			float py = ay + (by - ay) * t - (float) Math.sin(t * Math.PI) * 14.0F;
			if (i % 2 == 0) {
				l.g.fill(Math.round(px), Math.round(py), Math.round(px) + 2, Math.round(py) + 1, 0xC0F4E8C0);
			}
		}
		float t = (kini % 9000L) / 9000.0F;
		float sx = ax + (bx - ax) * t;
		float sy = ay + (by - ay) * t - (float) Math.sin(t * Math.PI) * 14.0F + (float) Math.sin(kini / 300.0D) * (badai ? 2.0F : 0.8F);
		TemaUi.ikon(l.g, TemaUi.I_KAPAL, Math.round(sx) - 7, Math.round(sy) - 10, 14);
		l.lepasKlip();
		l.keterangan(TemaUi.k("kapal.laut_tanpa_batas"), x0 + 4, y0 + 3, x1 - x0 - 30, 0xFFF4E8D0);
	}
}
