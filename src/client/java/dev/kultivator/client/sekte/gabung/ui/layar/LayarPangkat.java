package dev.kultivator.client.sekte.gabung.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.DaftarSekte;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.sekte.gabung.ui.IkonGabung;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.sekte.gabung.ui.GabungLembar.PohonPangkat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code gabung_pangkat} (DESIGN 41.6.1, 41.10.6): the rank tree drawn as jade caps in the rank colours on two lines (the
 * disciple line to the heir, the elder line to the vice master, both meeting at the master; the guest elder on a dashed
 * side link), passed ranks joined in gold, the own rank haloed, locked ranks at 45 % ink, seat holders on hover. A click
 * (or Tab + Enter) details a rank below: the seat summary, its requirement lines, rights and PK cost, "Ajukan Kenaikan"
 * and the seat holders that may be challenged.
 */
public final class LayarPangkat extends LayarGabungDasar {

	/** Grid place of every rank in ordinal order: column 0-7, row 0-1. */
	private static final int[][] TEMPAT = {{0, 0}, {1, 0}, {2, 0}, {3, 0}, {4, 0}, {5, 0}, {5, 1}, {6, 1}, {7, 1}, {3, 1}};

	private final DaftarGulir syaratGulir = new DaftarGulir();

	public LayarPangkat(int npcId, CompoundTag lembar) {
		super(PohonPangkat.LAYAR, npcId, lembar, t("pangkat.judul"), LembarKunci.GAYA_GIOK);
	}

	/** The "?" seal: the Ensiklopedia topic of the 41 sects (the sheet id is not a topic). */
	@Override
	public String helpTopic() {
		return "gabung_sekte";
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of();
	}

	@Override
	protected Component petunjuk() {
		return t("pangkat.petunjuk");
	}

	@Override
	protected void isiGabung(Lukis l, int x0, int y0, int x1, int y1) {
		int y = kepalaSekte(l, x0, y0, x1);
		List<Lembar> simpul = lembar.daftar(PohonPangkat.SIMPUL);
		if (simpul.isEmpty()) {
			kosong(l, t("pangkat.kosong"), x0, y, x1, y1);
			return;
		}
		int pilih = lembar.i(PohonPangkat.PILIH, lembar.i(PohonPangkat.KINI, 0));
		int bawahPohon = pohon(l, simpul, pilih, x0, y, x1, y1);
		y = bawahPohon + 2;
		l.teks(lembar.teks(PohonPangkat.RINGKAS), x0, y, x1 - x0, l.gaya.teksLemah);
		y += 10;
		subjudul(l, t(pilih == lembar.i(PohonPangkat.KINI, -1) ? "pangkat.detail_kini" : "pangkat.detail", namaPangkat(pilih)), x0, y, x1);
		y += 13;
		int by = y1 - 16;
		boolean dua = tata() != Tata.SEMPIT;
		int kiri1 = dua ? x0 + (x1 - x0) * 3 / 5 - 4 : x1;
		List<Lembar> syarat = lembar.daftar(LembarKunci.SYARAT);
		List<Lembar> hak = lembar.daftar(PohonPangkat.HAK);
		int biaya = lembar.i(LembarKunci.BIAYA, 0);
		// rights and cost: a card beside the requirements, under them when the box is tall enough, else more rows of the list
		boolean kartuHak = dua || by - 31 - y >= 40;
		int listY1 = dua ? by - 4 : kartuHak ? by - 31 : by - 3;
		int tambahan = kartuHak ? 0 : (biaya > 0 ? 1 : 0) + hak.size();
		final int biayaRow = biaya;
		syaratGulir.gambar(l, "syarat", x0, y, kiri1, listY1, syarat.size() + tambahan, 19, (lk, i, rx0, ry0, rx1, ry1, sorot, sel) -> {
			if (i < syarat.size()) {
				barisSyarat(lk, syarat.get(i), rx0 + 2, ry0 + 1, rx1 - 2, ry1);
				return;
			}
			int j = i - syarat.size() - (biayaRow > 0 ? 1 : 0);
			if (j < 0) {
				lk.teks(t("pangkat.biaya"), rx0 + 2, ry0 + 5, 40, l.gaya.teksSamar);
				LabelHarga.pk(lk, biayaRow, rx1 - 3, ry0 + 5, true, lembar.i(LembarKunci.PK, 0) >= biayaRow ? l.gaya.teks : Palette.BAHAYA);
			} else {
				lk.teks(Component.literal("· ").append(hak.get(j).teks(LembarKunci.TEKS)), rx0 + 2, ry0 + 5, rx1 - rx0 - 6, l.gaya.teks);
			}
		}, null, null);
		if (kartuHak) {
			int hx0 = dua ? kiri1 + 8 : x0;
			int hy = dua ? y : by - 29;
			l.kartu(hx0, hy, x1, by - 3, 0.0F, false);
			l.teks(t("pangkat.hak"), hx0 + 3, hy + 2, x1 - hx0 - 6, l.gaya.gelap() ? Palette.GOLD_LIGHT : l.gaya.aksen);
			int ry = hy + 11;
			for (Lembar h : hak) {
				if (ry + 8 > by - 14) {
					break;
				}
				l.teks(Component.literal("· ").append(h.teks(LembarKunci.TEKS)), hx0 + 3, ry, x1 - hx0 - 6, l.gaya.teks);
				ry += 10;
			}
			if (biaya > 0) {
				l.teks(t("pangkat.biaya"), hx0 + 3, by - 13, 40, l.gaya.teksSamar);
				LabelHarga.pk(l, biaya, x1 - 3, by - 13, true, lembar.i(LembarKunci.PK, 0) >= biaya ? l.gaya.teks : Palette.BAHAYA);
			}
		}
		// buttons: apply, then the challengeable seat holders
		int bw = Math.min(130, (x1 - x0) / 2 - 2);
		String id = Pangkat.byOrdinal(pilih).id();
		tombolOp(l, "ajukan", x0, by, bw, 15, t("pangkat.ajukan"), lembar.b(LembarKunci.BISA), lembar.teksAtau(LembarKunci.ALASAN),
				LembarKunci.op(PohonPangkat.OP_AJUKAN, id));
		List<Lembar> tantang = lembar.daftar(PohonPangkat.TANTANG);
		int tx = x0 + bw + 4;
		for (int i = 0; i < tantang.size(); i++) {
			Lembar e = tantang.get(i);
			Component lab = t("pangkat.tantang", e.teks(LembarKunci.NAMA));
			int w = Math.min(x1 - tx, Math.max(70, font.width(lab) + 20));
			if (w < 50) {
				break;
			}
			boolean over = tombolOp(l, "tantang" + i, tx, by, w, 15, lab, e.b(LembarKunci.BISA), e.teksAtau(LembarKunci.ALASAN),
					LembarKunci.op(PohonPangkat.OP_TANTANG, e.id()));
			if (over) {
				int sk = e.i(GabungLembar.SIKAP, 0);
				l.tip(List.of(e.teks(LembarKunci.NAMA), t("pangkat.sikap", Component.literal(Brush.bertanda(sk)).withColor(DaftarSekte.warnaSikap(sk))),
						t("pangkat.tantang_aturan")));
			}
			tx += w + 3;
		}
	}

	/** Draws the tree; returns the y under it. */
	private int pohon(Lukis l, List<Lembar> simpul, int pilih, int x0, int y0, int x1, int y1) {
		// the tree takes at most ~45 % of a short box (GUI scale 4): 2 x size + 31 tall
		int sizeTinggi = Math.max(12, (Math.round((y1 - y0) * 0.45F) - 31) / 2);
		int size = Math.max(12, Math.min(22, Math.min((x1 - x0 - 16) / 10, sizeTinggi)));
		float step = (x1 - x0 - 12 - size) / 7.0F;
		int baris2 = size + 15;
		int[] cx = new int[simpul.size()];
		int[] cy = new int[simpul.size()];
		for (int i = 0; i < simpul.size() && i < TEMPAT.length; i++) {
			cx[i] = Math.round(x0 + 6 + TEMPAT[i][0] * step + size / 2.0F);
			cy[i] = y0 + 4 + TEMPAT[i][1] * baris2 + size / 2;
		}
		int kini = lembar.i(PohonPangkat.KINI, 0);
		// links: disciple line, the fork from Inti, the elder line, the heir to the master, the guest's dashed link
		garis(l, simpul, cx, cy, 0, 1);
		garis(l, simpul, cx, cy, 1, 2);
		garis(l, simpul, cx, cy, 2, 3);
		garis(l, simpul, cx, cy, 3, 4);
		garis(l, simpul, cx, cy, 4, 5);
		int inti = Pangkat.INTI.ordinal();
		int tetua = Pangkat.TETUA.ordinal();
		boolean jalurTetua = lewat(simpul, tetua) || kini == tetua;
		int warnaFork = jalurTetua ? Palette.GOLD : Palette.argb(0x70, l.gaya.teksSamar & 0xFFFFFF);
		l.g.fill(cx[inti] - 1, cy[inti] + size / 2, cx[inti] + 1, cy[tetua] + 1, warnaFork);
		l.g.fill(cx[inti], cy[tetua] - 1, cx[tetua] - size / 2, cy[tetua] + 1, warnaFork);
		garis(l, simpul, cx, cy, tetua, Pangkat.WAKIL.ordinal());
		garis(l, simpul, cx, cy, Pangkat.WAKIL.ordinal(), Pangkat.KETUA.ordinal());
		int pewaris = Pangkat.PEWARIS.ordinal();
		int ketua = Pangkat.KETUA.ordinal();
		int warnaWaris = kini == ketua && lewat(simpul, pewaris) ? Palette.GOLD : Palette.argb(0x70, l.gaya.teksSamar & 0xFFFFFF);
		l.g.fill(cx[pewaris] + size / 2, cy[pewaris] - 1, cx[ketua] + 1, cy[pewaris] + 1, warnaWaris);
		l.g.fill(cx[ketua] - 1, cy[pewaris], cx[ketua] + 1, cy[ketua] - size / 2, warnaWaris);
		int tamu = Pangkat.TAMU.ordinal();
		for (int x = cx[tamu] + size / 2; x < cx[inti] - 2; x += 4) {
			l.g.fill(x, cy[tamu], x + 2, cy[tamu] + 1, Palette.argb(0x90, l.gaya.teksSamar & 0xFFFFFF));
		}
		// nodes
		for (int i = 0; i < simpul.size() && i < TEMPAT.length; i++) {
			Lembar n = simpul.get(i);
			String keadaan = n.s(LembarKunci.KEADAAN);
			boolean terkunci = PohonPangkat.K_TERKUNCI.equals(keadaan);
			boolean dikini = PohonPangkat.K_KINI.equals(keadaan);
			int nx = cx[i] - size / 2;
			int ny = cy[i] - size / 2;
			if (PohonPangkat.K_TERBUKA.equals(keadaan)) {
				Brush.halo(l.g, cx[i], cy[i], size * 0.62F, 2.0F, Palette.GOLD & 0xFFFFFF, l.denyut());
			}
			IkonGabung.cap(l.g, nx, ny, size, n.i(GabungLembar.WARNA, 0x8A8A8A), dikini, terkunci, l.denyut());
			Component angka = Component.literal(i == tamu ? "T" : Integer.toString(i));
			Brush.centered(l.g, font, angka, cx[i], cy[i] - 3.5F, terkunci ? 0x90FFFFFF : 0xFFFFF6DE, 1.0F, true);
			if (PohonPangkat.K_LEWAT.equals(keadaan)) {
				Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.CENTANG, nx + size - 7,
						ny + size - 7, 8, 0xFFFFFFFF);
			}
			if (i == pilih) {
				Brush.frame(l.g, nx - 3, ny - 3, nx + size + 3, ny + size + 3, Palette.argb(0xC0, l.gaya.aksen & 0xFFFFFF));
			}
			Component pendek = t("pangkat.pendek." + n.s(LembarKunci.ID));
			Brush.fitCentered(l.g, font, pendek, cx[i], ny + size + 2, step + 4, dikini ? l.gaya.aksen : terkunci ? Palette.terkunci(l.gaya.teksLemah)
					: l.gaya.teksLemah, 1.0F, 1.0F, false);
			String kunci = "n" + i;
			String op = LembarKunci.op(PohonPangkat.OP_PILIH, n.s(LembarKunci.ID));
			l.zona(kunci, nx - 2, ny - 2, nx + size + 2, ny + size + 10, () -> kirim(op), true, null, true);
			l.fokusCincin(kunci, nx - 2, ny - 2, nx + size + 2, ny + size + 2);
			if (l.di(nx - 2, ny - 2, nx + size + 2, ny + size + 10)) {
				l.tip(tipSimpul(n));
			}
		}
		return y0 + 4 + baris2 + size + 12;
	}

	private void garis(Lukis l, List<Lembar> simpul, int[] cx, int[] cy, int a, int b) {
		if (a >= simpul.size() || b >= simpul.size()) {
			return;
		}
		int size2 = 0;
		boolean emas = (lewat(simpul, a) || PohonPangkat.K_KINI.equals(simpul.get(a).s(LembarKunci.KEADAAN)))
				&& (lewat(simpul, b) || PohonPangkat.K_KINI.equals(simpul.get(b).s(LembarKunci.KEADAAN)));
		int warna = emas ? Palette.GOLD : Palette.argb(0x70, l.gaya.teksSamar & 0xFFFFFF);
		if (cy[a] == cy[b]) {
			l.g.fill(Math.min(cx[a], cx[b]) + size2, cy[a] - 1, Math.max(cx[a], cx[b]) - size2, cy[a] + 1, warna);
		} else {
			Brush.line(l.g, cx[a], cy[a], cx[b], cy[b], 2.0F, warna);
		}
	}

	private static boolean lewat(List<Lembar> simpul, int i) {
		return i < simpul.size() && PohonPangkat.K_LEWAT.equals(simpul.get(i).s(LembarKunci.KEADAAN));
	}

	private List<Component> tipSimpul(Lembar n) {
		List<Component> lines = new ArrayList<>();
		lines.add(Brush.bold(n.teks(LembarKunci.NAMA)));
		lines.add(t("pangkat.keadaan." + n.s(LembarKunci.KEADAAN)));
		int maks = n.i(PohonPangkat.KURSI_MAKS, 0);
		if (maks > 0) {
			lines.add(t("pangkat.kursi", n.i(PohonPangkat.KURSI, 0), maks));
			for (Lembar h : n.daftar(PohonPangkat.PEMEGANG)) {
				Component nama = h.teks(LembarKunci.NAMA);
				lines.add(Component.literal("  ").append(h.b(LembarKunci.ANDA) ? t("pangkat.anda", nama) : nama));
			}
		} else {
			lines.add(t("pangkat.kursi_bebas"));
		}
		lines.add(t("pangkat.klik"));
		return lines;
	}
}
