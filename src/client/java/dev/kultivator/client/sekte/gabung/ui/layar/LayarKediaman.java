package dev.kultivator.client.sekte.gabung.ui.layar;

import static dev.kultivator.client.sekte.gabung.ui.layar.LayarGabung2.pk;
import static dev.kultivator.client.sekte.gabung.ui.layar.LayarGabung2.u;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.sekte.gabung.ui.IkonGabung;
import dev.kultivator.client.tempat.ui.kit.CincinWaktu;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.KeadaanKosong;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Kediaman;
import dev.kultivator.sekte.gabung.ui.LembarKediaman;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * {@code gabung_kediaman} (DESIGN 41.6.6, 41.10.7): cave dwellings and spirit pools. Gua: the rented cave as a jade
 * plaque (name and number, paid-to day with a fading rent bar, meditation factor, Jimat Pulang), the nine-slot deposit
 * chest (click a slot to take it back, "Titipkan barang di tangan"), "Perpanjang", "Pindah" and "Lepas" (medium confirm
 * seal); a member without a cave sees the dormitory card. Pilih: every dwelling of the sect by kind (rank seal, factor, rent per
 * 7 days, who lives there, why it is closed) with "Sewa" / "Pindah". Kolam: the meditation seats of the compound, free
 * or taken, the own session's countdown and "Pesan sesi" (free once a day for Inti and above).
 * <p>
 * Home (DESIGN 43.12.4, G66): Diri › Harta, its three tabs as the subs gua / pilih / kolam of {@code screen.HartaPage}
 * (beside the Cave Abode point). All its text is drawn at 1.0 (43.0.5).
 */
public final class LayarKediaman extends LayarGabungDasar {

	private static final long SEWA_HARI = 7L;

	private final DaftarGulir pilih = new DaftarGulir();
	private final DaftarGulir kolam = new DaftarGulir();
	/** The stacked plaque + chest of a narrow page (GUI scale 4) scroll as one column. */
	private final GulirKolom kolomGua = new GulirKolom();

	/** Height of the dwelling plaque (the lines down to the amulet button) and of the stacked chest. */
	private static final int PLAKAT_H = 78;
	private static final int PETI_H = 94;

	public LayarKediaman(int npcId, CompoundTag lembar) {
		super(Kediaman.LAYAR, npcId, lembar, u("kediaman.judul"), TempatWarna.Gaya.GIOK);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(new TabLentera.Tab(Kediaman.TAB_GUA, u("kediaman.tab.gua"), IkonTempat.DUPA, null),
				new TabLentera.Tab(Kediaman.TAB_PILIH, u("kediaman.tab.pilih"), IkonTempat.KUNCI, null),
				new TabLentera.Tab(Kediaman.TAB_KOLAM, u("kediaman.tab.kolam"), IkonTempat.HATI, null));
	}

	@Override
	protected KeadaanKosong.Gambar gambarKosong() {
		return KeadaanKosong.Gambar.LENTERA;
	}

	@Override
	protected void tabBerubah() {
		long now = Util.getMillis();
		pilih.mulaiUlang(now);
		kolam.mulaiUlang(now);
		kolomGua.ulang();
	}

	@Override
	protected void isiGabung(Lukis l, int x0, int y0, int x1, int y1) {
		int y = kepalaSekte(l, x0, y0, x1);
		int pkPunya = lembar.i(LembarKunci.PK, 0);
		l.kanan(u("kediaman.pk_anda"), x1 - pk(l, pkPunya, -1L, x1, y, true, 1.0F) - 4, y, gaya.teksSamar, 1.0F);
		y += 12;
		switch (tab) {
			case Kediaman.TAB_PILIH -> pilih(l, x0, y, x1, y1);
			case Kediaman.TAB_KOLAM -> kolam(l, x0, y, x1, y1);
			default -> gua(l, x0, y, x1, y1);
		}
	}

	// ================================================================== Gua

	private void gua(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar h = lembar.sub(Kediaman.HUNIAN);
		if (h.kosongSemua()) {
			bilik(l, x0, y0, x1, y1);
			return;
		}
		int by = y1 - 16;
		// Side by side down to 240 px (the Harta paper at GUI scale 4 is ~289 wide); narrower, the plaque and the chest
		// stack in one scrolled column (arrow keys / wheel) instead of two clipped halves.
		boolean tumpuk = x1 - x0 < 240;
		int py0 = y0;
		int px1 = tumpuk ? x1 : x0 + (x1 - x0) * 11 / 20;
		int py1 = by - 4;
		int cx0 = px1 + 6;
		int cy0 = y0;
		int cy1 = by - 4;
		if (tumpuk) {
			gulirAktif = kolomGua;
			py0 = kolomGua.mulai(l, x0, y0, x1, by - 3);
			py1 = py0 + PLAKAT_H;
			cx0 = x0;
			cy0 = py1 + 5;
			cy1 = cy0 + PETI_H;
		}
		// the dwelling plaque (its lines never spill onto the chest)
		IkonGabung.kartu(l.g, x0, py0, px1, py1);
		l.klip(x0, py0, px1, py1);
		int ink = 0xFF16302A;
		int lemah = 0xFF3E5E52;
		int x = x0 + 9;
		int y = py0 + 8;
		IkonGabung.gambar(l.g, IkonGabung.MEDITASI, x, y, 20);
		l.teks(Brush.bold(h.teks(LembarKunci.NAMA)), x + 24, y + 1, px1 - x - 34, ink, 1.0F);
		l.teks(u("kediaman.jenis." + h.s(LembarKunci.JENIS)), x + 24, y + 11, px1 - x - 34, lemah, 1.0F);
		y += 25;
		long sampai = h.l(Kediaman.SAMPAI, 0L);
		int sisa = h.i(LembarKunci.SISA_HARI, 0);
		l.teks(u("kediaman.lunas", sampai, sisa), x, y, px1 - x - 9, sisa <= 1 ? 0xFFB03030 : ink, 1.0F);
		y += 10;
		batang(l, x, y, px1 - x - 9, 4, Math.min(1.0F, sisa / (float) (SEWA_HARI * 2)), sisa <= 1 ? TempatWarna.BAHAYA : 0xFF2E8963, 0.5F);
		y += 8;
		float faktor = h.f(Kediaman.FAKTOR, 1.0F);
		l.teks(u("kediaman.faktor", String.format(Locale.ROOT, "%.2f", faktor)), x, y, px1 - x - 9, ink, 1.0F);
		y += 10;
		boolean jimat = h.b(Kediaman.JIMAT);
		IkonTempat.gambar(l.g, jimat ? IkonTempat.CENTANG : IkonTempat.SILANG, x, y, 9, 0xFFFFFFFF);
		l.teks(u(jimat ? "kediaman.jimat_ya" : "kediaman.jimat_tidak"), x + 12, y + 1, px1 - x - 9 - 70, jimat ? ink : lemah, 1.0F);
		if (!jimat && py1 - y > 12) {
			tombolOp(l, "jimat", px1 - 74, y - 2, 66, 13, u("kediaman.jadikan_jimat"), true, null, LembarKediaman.OP_JIMAT);
		}
		l.lepasKlip();
		// the deposit chest
		peti(l, h, cx0, cy0, x1, cy1);
		if (tumpuk) {
			kolomGua.selesai(l, cy1);
		}
		// buttons
		int sewa = h.i(Kediaman.SEWA, 0);
		int bw = (x1 - x0 - 8) / 3;
		boolean penuh = sisa >= SEWA_HARI * 2 - 1;
		tombolOp(l, "perpanjang", x0, by, bw, 15, u("kediaman.perpanjang", Brush.angka(sewa)), !penuh && sewa <= lembar.i(LembarKunci.PK, 0),
				penuh ? u("kediaman.sudah_penuh") : u("kediaman.kurang_pk", sewa), Kediaman.OP_PERPANJANG);
		tombol.gambar(l, "pindah", x0 + bw + 4, by, bw, 15, u("kediaman.pindah"), true, null, () -> gantiTab(Kediaman.TAB_PILIH, true));
		tombolYakin(l, "lepas", x0 + (bw + 4) * 2, by, bw, 15, u("kediaman.lepas"), true, null, Kediaman.OP_LEPAS);
	}

	private void peti(Lukis l, Lembar h, int x0, int y0, int x1, int y1) {
		sumur(l, new Kotak(x0, y0, x1, y1));
		int teks = gaya.teksSumur();
		l.teks(Brush.bold(u("kediaman.peti")), x0 + 4, y0 + 3, x1 - x0 - 8, teks, 1.0F);
		List<Lembar> slot = h.daftar(Kediaman.PETI);
		// three rows must fit between the title (15) and the deposit talisman (17) too
		int sel = Math.max(16, Math.min(24, Math.min((x1 - x0 - 12) / 3, (y1 - y0 - 38) / 3)));
		int gx = x0 + (x1 - x0 - sel * 3 - 4) / 2;
		int gy = y0 + 15;
		for (int i = 0; i < 9; i++) {
			int sx = gx + (i % 3) * (sel + 2);
			int sy = gy + (i / 3) * (sel + 2);
			if (sy + sel > y1 - 17) {
				break;
			}
			Lembar s = i < slot.size() ? slot.get(i) : Lembar.kosong();
			ItemStack item = s.barang();
			boolean hover = l.di(sx, sy, sx + sel, sy + sel);
			l.g.fill(sx, sy, sx + sel, sy + sel, hover && !item.isEmpty() ? 0x60FFFFFF : Palette.argb(0x40, 0x000000));
			l.g.fill(sx, sy, sx + sel, sy + 1, 0x40000000);
			if (!item.isEmpty()) {
				int o = (sel - 16) / 2;
				l.g.item(item, sx + o, sy + o);
				l.g.itemDecorations(font, item, sx + o, sy + o);
				final int slotNo = s.i(LembarKunci.SLOT, i);
				// a Tab stop too: Enter takes the item back (43.4.2)
				l.zona("peti." + i, sx, sy, sx + sel, sy + sel, () -> kirim(LembarKunci.op(Kediaman.OP_PETI_AMBIL, slotNo)), true, null, true);
				l.fokusCincin("peti." + i, sx, sy, sx + sel, sy + sel);
				if (hover) {
					l.tip(List.of(item.getHoverName(), u("kediaman.peti.ambil_tip")));
				}
			}
		}
		tombolOp(l, "taruh", x0 + 4, y1 - 16, x1 - x0 - 8, 13, u("kediaman.peti.taruh"), true, null, Kediaman.OP_PETI_TARUH);
	}

	private void bilik(Lukis l, int x0, int y0, int x1, int y1) {
		float cx = (x0 + x1) / 2.0F;
		int y = y0 + Math.max(4, (y1 - y0) / 2 - 52);
		IkonTempat.gambar(l.g, IkonTempat.DUPA, Math.round(cx - 16), y, 32, 0xFFFFFFFF);
		y += 36;
		Brush.fitCentered(l.g, font, Brush.bold(u("kediaman.bilik.judul")), cx, y, x1 - x0 - 20, gaya.teks, 1.0F, 1.0F, false);
		y += 13;
		int w = Math.min(270, x1 - x0 - 16);
		y += Math.round(l.bungkus(u("kediaman.bilik.isi"), cx - w / 2.0F, y, w, gaya.teksLemah, 1.0F, y1 - y - 24)) + 6;
		int bw = Math.min(160, x1 - x0 - 16);
		tombol.gambar(l, "kePilih", Math.round(cx - bw / 2.0F), Math.min(y, y1 - 17), bw, 15, u("kediaman.bilik.cari"), true, null,
				() -> gantiTab(Kediaman.TAB_PILIH, true));
	}

	// ================================================================== Pilih

	private void pilih(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> list = lembar.daftar(LembarKunci.DAFTAR);
		if (list.isEmpty()) {
			kosong(l, u("kediaman.pilih.kosong"), x0, y0, x1, y1);
			return;
		}
		String kini = lembar.sub(Kediaman.HUNIAN).s(LembarKunci.JENIS);
		int pkPunya = lembar.i(LembarKunci.PK, 0);
		pilih.gambar(l, "pilih", x0, y0, x1, y1, list.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, sel) -> {
			Lembar e = list.get(i);
			String keadaan = e.keadaan();
			boolean anda = e.b(LembarKunci.ANDA);
			boolean bisa = LembarKunci.K_OK.equals(keadaan) && !anda;
			boolean redup = !bisa && !anda;
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, sel);
			if (anda) {
				lk.g.fill(rx0, ry0, rx0 + 2, ry1, TempatWarna.EMAS);
			}
			int pangkat = e.i(GabungLembar.PANGKAT, 0);
			IkonGabung.gambar(lk.g, IkonGabung.pangkat(pangkat), rx0 + 4, ry0 + 5, 16, redup ? 0x90FFFFFF : 0xFFFFFFFF);
			int bw = Math.min(64, (rx1 - rx0) / 5);
			int bx = rx1 - bw - 4;
			int harga = e.i(LembarKunci.HARGA, 0);
			int pw = harga > 0 ? pk(lk, harga, redup ? -1L : pkPunya, bx - 6, ry0 + 4, true, 1.0F) : 0;
			if (harga > 0) {
				lk.kanan(u("kediaman.per_7"), bx - 6, ry0 + 14, gaya.teksSamar, 1.0F);
			}
			// the caption below the name stops before "/7 hari" under the price
			int perW = harga > 0 ? font.width(u("kediaman.per_7")) + 4 : 0;
			int tx = rx0 + 24;
			int warna = redup ? TempatWarna.terkunci(gaya.teks) : gaya.teks;
			Component nama = e.teks(LembarKunci.NAMA).copy().append(Component.literal(String.format(Locale.ROOT, "  ×%.2f", e.f(Kediaman.FAKTOR, 1.0F))));
			lk.teks(nama, tx, ry0 + 4, bx - pw - 12 - tx, warna, 1.0F);
			Component bawah;
			int bwarna = gaya.teksSamar;
			if (anda) {
				bawah = u("kediaman.pilih.milikmu");
				bwarna = TempatWarna.OK;
			} else if (e.teksAtau(Kediaman.PEMAKAI) != null && LembarKunci.K_HABIS.equals(keadaan)) {
				bawah = u("kediaman.pilih.dipakai", e.teks(Kediaman.PEMAKAI));
			} else if (e.teksAtau(LembarKunci.ALASAN) != null) {
				bawah = e.teks(LembarKunci.ALASAN);
				bwarna = gaya.gelap() ? 0xFFFF9A8A : TempatWarna.BAHAYA;
			} else {
				bawah = u("kediaman.pilih.bebas", namaPangkat(pangkat));
			}
			lk.teks(bawah, tx, ry0 + 14.0F, bx - tx - 6 - perW, bwarna, 1.0F);
			if (!anda && "bilik".equals(e.s(LembarKunci.JENIS))) {
				// back to the dormitory = giving the cave up (the chest's items come back)
				tombolYakin(lk, "bilik" + i, bx, ry0 + 5, bw, 15, u("kediaman.ke_bilik"), true, null, Kediaman.OP_LEPAS);
			} else if (!anda) {
				boolean pindah = !kini.isEmpty() && kini.equals(e.s(LembarKunci.JENIS));
				String op = LembarKunci.op(pindah ? Kediaman.OP_PINDAH : Kediaman.OP_SEWA, e.id());
				tombolOp(lk, "sewa" + i, bx, ry0 + 5, bw, 15, pindah ? u("kediaman.pindah_ke") : u("kediaman.sewa"), bisa, e.teksAtau(LembarKunci.ALASAN) != null
						? e.teks(LembarKunci.ALASAN) : u("kediaman.pilih.tak_bisa"), op);
			}
		}, null, null);
	}

	// ================================================================== Kolam

	private void kolam(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> list = lembar.daftar(Kediaman.KOLAM);
		if (list.isEmpty()) {
			Component alasan = lembar.teksAtau(LembarKediaman.KOLAM_KOSONG);
			kosong(l, alasan != null ? alasan : u("kediaman.kolam.kosong"), x0, y0, x1, y1);
			return;
		}
		boolean gratis = !list.isEmpty() && list.getFirst().b(Kediaman.GRATIS);
		l.teks(gratis ? u("kediaman.kolam.ket_gratis") : u("kediaman.kolam.ket"), x0, y0, x1 - x0, gratis ? TempatWarna.OK : gaya.teksSamar, 1.0F);
		y0 += 11;
		int pkPunya = lembar.i(LembarKunci.PK, 0);
		kolam.gambar(l, "kolam", x0, y0, x1, y1, list.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, sel) -> {
			Lembar e = list.get(i);
			boolean anda = e.b(LembarKunci.ANDA);
			boolean terisi = e.b(Kediaman.TERISI);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, sel);
			// a seat ring: jade when free, grey when taken, gold for the own session
			int warna = anda ? TempatWarna.EMAS : terisi ? 0xFF7A8C83 : 0xFF2E8963;
			Brush.ring(lk.g, rx0 + 12, ry0 + 13, 5.0F, 8.0F, warna);
			if (terisi || anda) {
				Brush.disc(lk.g, rx0 + 12, ry0 + 13, 4.0F, warna);
			}
			int bw = Math.min(84, (rx1 - rx0) / 3);
			int bx = rx1 - bw - 4;
			int tx = rx0 + 26;
			lk.teks(e.teks(LembarKunci.NAMA), tx, ry0 + 4, bx - tx - 30, gaya.teks, 1.0F);
			Component bawah = anda ? u("kediaman.kolam.sesimu") : terisi ? u("kediaman.kolam.terisi", e.teks(Kediaman.PEMAKAI)) : u("kediaman.kolam.bebas");
			lk.teks(bawah, tx, ry0 + 14.0F, bx - tx - 30, anda ? TempatWarna.OK : gaya.teksSamar, 1.0F);
			if (anda && e.ada(LembarKunci.SISA_TICK)) {
				CincinWaktu.gambar(lk, bx - 14, ry0 + 13, 8.0F, e.l(LembarKunci.SISA_TICK, 0L), 30L * 60L * 20L, diterima, CincinWaktu.TANPA);
				tombolOp(lk, "tandaiK" + i, bx, ry0 + 5, bw, 15, u("kediaman.kolam.tandai"), true, null,
						LembarKunci.op(LembarKunci.OP_TANDAI, e.i(LembarKunci.X, 0), e.i(LembarKunci.Z, 0)));
				return;
			}
			int harga = e.i(LembarKunci.HARGA, 20);
			boolean free = e.b(Kediaman.GRATIS);
			boolean bisa = !terisi && (free || harga <= pkPunya);
			Component label = free ? u("kediaman.kolam.pesan_gratis") : u("kediaman.kolam.pesan", Brush.angka(harga));
			tombolOp(lk, "sesi" + i, bx, ry0 + 5, bw, 15, label, bisa, terisi ? u("kediaman.kolam.sudah_terisi") : u("kediaman.kurang_pk", harga),
					LembarKunci.op(Kediaman.OP_SESI, e.id()));
		}, null, null);
	}
}
