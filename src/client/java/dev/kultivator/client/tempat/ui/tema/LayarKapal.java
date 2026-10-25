package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.CincinWaktu;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Pratinjau3D;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Kapal;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * {@code tema_kapal} (DESIGN 38.T.3.4): the moored merchant ship, talked to through its NAKHODA_LAUT on deck.
 * <ul>
 * <li><b>Muatan</b>: the ship's card (the real ship model turning, origin, berth, departure countdown), its cargo in the
 * kit's one shop composition (G19: goods cards, turning preview, amount picker), and the berth schedule along the bottom
 * (arriving, moored, leaving, empty; in the narrow layout it is the ship card's tooltip).</li>
 * <li><b>Kapal Lintas Laut</b> (Kapten Gao Fan): the crossing to the docks of the Laut Tanpa Batas, the sailing hour,
 * the fare, up to two disciples along, and the sea chart.</li>
 * </ul>
 * A storm stops every departure (warning band, buttons off). A Di Sini page on the single kit (43.8.3).
 */
public final class LayarKapal extends TemaHalaman {

	private final Toko toko = new Toko();
	private final Pratinjau3D pratinjauKapal = new Pratinjau3D();
	private int murid;

	public LayarKapal(int npcId, CompoundTag lembar) {
		super(Kapal.LAYAR, npcId, lembar, "layar.kapal", TempatWarna.Gaya.KAYU);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(tabTema(Kapal.TAB_MUATAN, "kapal.tab.muatan", LembarKunci.Ikon.DERMAGA),
				tabTema(Kapal.TAB_LINTAS, "kapal.tab.lintas", Glyphs.Atlas.KOMPAS));
	}

	@Override
	protected boolean tabAda(String id) {
		return !Kapal.TAB_LINTAS.equals(id) || lembar.ada(Kapal.LINTAS);
	}

	@Override
	protected Component petunjuk() {
		return Kapal.TAB_MUATAN.equals(tab) ? tt("kunci.jumlah") : null;
	}

	@Override
	protected void tabGanti() {
		toko.mulaiUlang();
	}

	@Override
	protected void lukisTema(Lukis l, int x0, int y0, int x1, int y1) {
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
		boolean sempit = tata == Tata.SEMPIT;
		int jadwalH = jadwal.isEmpty() || sempit ? 0 : 26;
		int y = y0;
		if (kapal.ada(LembarKunci.NAMA)) {
			y += kartuKapal(l, kapal, jadwal, x0, y, x1);
		}
		Kotak[] k = bagi(x0, y, x1, y1 - jadwalH);
		boolean badai = lembar.b(Kapal.BADAI);
		toko(l, "muatan", toko, lembar.daftar(Kapal.MUATAN), k[0], k[1], Kapal.OP_BELI, true, badai ? (lk, e, ex0, ey, ex1) -> {
			lk.samar(TemaUi.k("kapal.badai_beli"), ex0, ey, ex1 - ex0);
			return 10;
		} : null, null, TemaUi.k(kapal.ada(LembarKunci.NAMA) ? "kapal.muatan_kosong" : "kapal.belum_datang"));
		if (jadwalH > 0) {
			jadwal(l, jadwal, x0, y1 - jadwalH + 3, x1, y1);
		}
	}

	private int kartuKapal(Lukis l, Lembar kapal, List<Lembar> jadwal, int x0, int y0, int x1) {
		boolean sempit = tata == Tata.SEMPIT;
		int h = sempit ? 24 : 44;
		l.kartu(x0, y0, x1, y0 + h, 0.0F, false);
		int pv = h - 4;
		String entitas = kapal.s(LembarKunci.ENTITAS, "kultivator:kapal_dagang");
		pratinjauKapal.gambar(l, "kapal_3d", x0 + 2, y0 + 2, x0 + 2 + pv * 3 / 2, y0 + 2 + pv, ItemStack.EMPTY, entitas, LembarKunci.Ikon.DERMAGA);
		int tx = x0 + 6 + pv * 3 / 2;
		int ring = kapal.ada(Kapal.BERANGKAT_TICK) ? (sempit ? 58 : 64) : 4;
		Component asal = TemaUi.k("kapal.asal", kapal.teks(Kapal.ASAL), kapal.i(Kapal.TAMBATAN, 1));
		if (sempit) {
			l.teks(Brush.bold(kapal.teks(LembarKunci.NAMA)), tx, y0 + 3, x1 - tx - ring, aksen());
			l.teks(asal, tx, y0 + 13, x1 - tx - ring, gaya.teksLemah);
		} else {
			l.teks(Brush.bold(kapal.teks(LembarKunci.NAMA)), tx, y0 + 4, x1 - tx - ring, aksen());
			l.teks(asal, tx, y0 + 15, x1 - tx - ring, gaya.teksLemah);
			if (kapal.ada(Kapal.BERANGKAT_TICK)) {
				l.samar(TemaUi.k("kapal.berangkat_dalam"), tx, y0 + 26, x1 - tx - ring);
			}
		}
		if (kapal.ada(Kapal.BERANGKAT_TICK)) {
			float r = sempit ? 9.0F : 12.0F;
			CincinWaktu.gambar(l, x1 - r - 4, y0 + h / 2.0F, r, kapal.l(Kapal.BERANGKAT_TICK, 0L), 12000L, diterima, CincinWaktu.KIRI);
		}
		if (sempit && !jadwal.isEmpty() && l.di(x0, y0, x1 - ring, y0 + h)) {
			List<Component> tip = new ArrayList<>();
			tip.add(TemaUi.k("kapal.jadwal"));
			for (int i = 0; i < jadwal.size() && i < 6; i++) {
				Lembar j = jadwal.get(i);
				String s = j.s(LembarKunci.KEADAAN);
				Component isi = "kosong".equals(s) ? TemaUi.k("kapal.j.kosong") : j.teks(Kapal.ASAL);
				tip.add(TemaUi.k("kapal.tambatan", j.i(Kapal.TAMBATAN, i + 1)).copy().append(": ").append(isi).append(" · ")
						.append(TemaUi.k("kapal.j." + (s.isEmpty() ? "kosong" : s))));
			}
			l.tip(tip);
		}
		return h + 4;
	}

	private void jadwal(Lukis l, List<Lembar> jadwal, int x0, int y0, int x1, int y1) {
		l.garis(x0, x1, y0 - 2);
		Component judul = TemaUi.k("kapal.jadwal");
		int jw = Math.min(64, font.width(judul) + 4);
		l.teks(judul, x0, y0 + 6, jw, gaya.teksLemah);
		int n = Math.min(jadwal.size(), 6);
		int w = Math.max(44, (x1 - x0 - jw) / Math.max(1, n));
		for (int i = 0; i < n; i++) {
			Lembar j = jadwal.get(i);
			int cx = x0 + jw + i * w;
			if (cx + w - 3 > x1) {
				break;
			}
			String s = j.s(LembarKunci.KEADAAN);
			int warna = switch (s) {
				case "datang" -> 0xFF6FA8C8;
				case "tambat" -> Palette.OK;
				case "berangkat" -> Palette.PERINGATAN;
				default -> 0xFF8D928E;
			};
			l.g.fill(cx, y0, cx + w - 3, y1 - 1, Palette.argb(0x50, warna));
			l.g.fill(cx, y0, cx + 2, y1 - 1, warna);
			Component isi = "kosong".equals(s) ? TemaUi.k("kapal.j.kosong") : j.teks(Kapal.ASAL);
			l.teks(TemaUi.k("kapal.tambatan", j.i(Kapal.TAMBATAN, i + 1)), cx + 4, y0 + 1, w - 9, gaya.teks);
			l.teks(isi, cx + 4, y0 + 11, w - 9, gaya.teksLemah);
			if (l.di(cx, y0, cx + w - 3, y1 - 1)) {
				l.tip(List.of(TemaUi.k("kapal.j." + (s.isEmpty() ? "kosong" : s)), j.teks(Kapal.ASAL), TemaUi.k("kapal.j.hari", j.i(Kapal.DATANG_HARI, 0))));
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ crossing

	private void lintas(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar lintas = lembar.sub(Kapal.LINTAS);
		boolean badai = lembar.b(Kapal.BADAI) || lintas.b(Kapal.BADAI);
		boolean sempit = tata == Tata.SEMPIT;
		int y = y0 + strip(l, TemaUi.k(badai ? "kapal.ucap_badai" : "kapal.ucap_lintas"), x0, y0, x1, sempit ? 30 : 36);
		Kotak kanan;
		if (sempit) {
			// the chart becomes a strip above the fare card
			int petaH = Math.max(0, Math.min(36, y1 - y - 96));
			if (petaH >= 20) {
				peta(l, new Kotak(x0, y, x1, y + petaH), badai);
				y += petaH + 4;
			}
			kanan = new Kotak(x0, y, x1, y1);
		} else {
			Kotak[] k = bagi(x0, y, x1, y1);
			peta(l, k[0], badai);
			kanan = k[1];
		}
		l.kartu(kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1(), 0.0F, false);
		int rx0 = kanan.x0() + 5;
		int rx1 = kanan.x1() - 5;
		int ry = kanan.y0() + 4;
		ry += subjudul(l, TemaUi.k("kapal.lintas_judul"), rx0, ry, rx1);
		ry += barisNilai(l, TemaUi.k("kapal.berlayar_jam"), lintas.teks(Kapal.BERANGKAT), rx0, ry, rx1);
		long ongkos = lintas.l(Kapal.ONGKOS, 0L);
		ry += barisNilai(l, TemaUi.k("kapal.ongkos"), Brush.nb(ongkos, false), rx0, ry, rx1) + 3;
		int maksMurid = Math.max(0, Math.min(2, lintas.i(Kapal.MURID, 0)));
		murid = Math.min(murid, maksMurid);
		Component bawa = TemaUi.k("kapal.bawa_murid");
		int bw = Math.min(90, font.width(bawa) + 4);
		l.teks(bawa, rx0, ry + 3, bw, gaya.teksLemah);
		for (int m = 0; m <= maksMurid; m++) {
			int cx = rx0 + bw + m * 18;
			boolean on = m == murid;
			final int mm = m;
			Brush.disc(l.g, cx + 7, ry + 7, 7.0F, on ? Palette.GOLD : Palette.argb(0x60, 0x000000));
			l.tengah(Component.literal(Integer.toString(m)), cx + 7, ry + 3, 14, on ? 0xFF3A2208 : gaya.teksLemah);
			l.zona("murid" + m, cx, ry, cx + 16, ry + 14, () -> murid = mm, true, null, true);
			l.fokusCincin("murid" + m, cx, ry, cx + 16, ry + 14);
		}
		ry += 18;
		int by = kanan.y1() - 19;
		if (by - ry >= 9) {
			l.bungkus(TemaUi.k("kapal.lintas_jelas"), rx0, ry, rx1 - rx0, gaya.teksSamar, by - ry - 2);
		}
		boolean bisa = lintas.b(LembarKunci.BISA) && !badai && mampu(ongkos);
		Component alasan = badai ? TemaUi.k("kapal.badai_tolak") : !mampu(ongkos) ? kurang(ongkos) : lintas.teksAtau(LembarKunci.ALASAN);
		tombolOp(l, "lintas", rx0, by, rx1 - rx0, 15, TemaUi.k("kapal.berlayar", Brush.angka(ongkos)), bisa, alasan,
				LembarKunci.op(Kapal.OP_LINTAS, murid));
	}

	/** The sea chart: harbour, dotted course over the waves, the Laut Tanpa Batas docks, a ship riding the course. */
	private void peta(Lukis l, Kotak k, boolean badai) {
		int x0 = k.x0();
		int y0 = k.y0();
		int x1 = k.x1();
		int y1 = k.y1();
		l.sumur(x0, y0, x1, y1);
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
		int lengkung = Math.min(14, (y1 - y0) / 3);
		int ax = x0 + 14;
		int ay = y1 - Math.min(18, (y1 - y0) / 2);
		int bx = x1 - 18;
		int by = y0 + Math.min(16, (y1 - y0) / 2);
		TemaUi.ikon(l.g, TemaUi.I_PELABUHAN, ax - 8, ay - 8, 16);
		if (y1 - y0 > 40) {
			TemaUi.ikon(l.g, TemaUi.I_SUAR, ax + 4, ay - 18, 12);
		}
		Brush.disc(l.g, bx, by, 9.0F, Palette.argb(0xC0, 0xE8D8A8));
		Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.DERMAGA, bx - 8, by - 8, 16, 0xFFFFFFFF);
		for (int i = 0; i <= 20; i++) {
			float t = i / 20.0F;
			float px = ax + (bx - ax) * t;
			float py = ay + (by - ay) * t - (float) Math.sin(t * Math.PI) * lengkung;
			if (i % 2 == 0) {
				l.g.fill(Math.round(px), Math.round(py), Math.round(px) + 2, Math.round(py) + 1, 0xC0F4E8C0);
			}
		}
		float t = (kini % 9000L) / 9000.0F;
		float sx = ax + (bx - ax) * t;
		float sy = ay + (by - ay) * t - (float) Math.sin(t * Math.PI) * lengkung + (float) Math.sin(kini / 300.0D) * (badai ? 2.0F : 0.8F);
		TemaUi.ikon(l.g, TemaUi.I_KAPAL, Math.round(sx) - 7, Math.round(sy) - 10, 14);
		l.lepasKlip();
		l.teks(TemaUi.k("kapal.laut_tanpa_batas"), x0 + 4, y0 + 3, x1 - x0 - 30, 0xFFF4E8D0);
	}
}
