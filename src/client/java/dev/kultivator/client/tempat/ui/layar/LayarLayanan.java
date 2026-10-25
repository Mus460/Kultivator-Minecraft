package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.CincinWaktu;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Layanan;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * {@code penginapan}, {@code rumah_makan} and {@code jasa} (DESIGN 38.U.3.9, 38.M.2.2): rooms of an inn, dishes of an
 * eating house, and the services healer / temple incense / library / news archive. One menu: icon or item, name, the
 * effect it gives, price and state; the effects already running with their rings; "already slept here tonight" for inns.
 * Inns and eating houses are lacquered wood, services jade. Single kit, text 1.0.
 */
public final class LayarLayanan extends LayarInti {

	private final DaftarGulir menu = new DaftarGulir();

	public LayarLayanan(String layar, int npcId, CompoundTag lembar) {
		super(layar, npcId, lembar, k("layar." + layar), Layanan.LAYAR_JASA.equals(layar) ? LembarKunci.GAYA_GIOK : LembarKunci.GAYA_KAYU);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of();
	}

	@Override
	public boolean lebar() {
		return false;
	}

	private String jenis() {
		return switch (layar) {
			case Layanan.LAYAR_PENGINAPAN -> Layanan.JENIS_PENGINAPAN;
			case Layanan.LAYAR_RUMAH_MAKAN -> Layanan.JENIS_RUMAH_MAKAN;
			default -> lembar.s(LembarKunci.JENIS, Layanan.JENIS_TABIB);
		};
	}

	@Override
	protected Component judulBawaan() {
		return Layanan.LAYAR_JASA.equals(layar) ? k("layanan.jenis." + jenis()) : title;
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		int y = y0;
		if (lembar.b(Layanan.SUDAH_MENGINAP)) {
			l.g.fill(x0, y, x1, y + 13, Palette.argb(0x90, Palette.PERINGATAN & 0xFFFFFF));
			l.tengah(k("layanan.sudah_menginap"), (x0 + x1) / 2.0F, y + 2, x1 - x0 - 8, 0xFF2A1A10);
			y += 16;
		}
		List<Lembar> aktif = lembar.daftar(LembarKunci.AKTIF);
		if (!aktif.isEmpty()) {
			int ax = x0;
			for (Lembar a : aktif) {
				Component nama = a.teks(LembarKunci.NAMA);
				int w = l.font.width(nama) + 21;
				if (ax + w > x1) {
					break;
				}
				l.g.fill(ax, y, ax + w, y + 14, l.gaya.kartuSorot);
				CincinWaktu.gambar(l, ax + 7, y + 7, 5.5F, a.l(LembarKunci.SISA_TICK, 0L), 0L, diterima, CincinWaktu.TANPA);
				l.teks(nama, ax + 15, y + 3, w - 17, l.gaya.teks);
				ax += w + 4;
			}
			y += 18;
		}
		List<Lembar> rows = lembar.daftar(Layanan.MENU);
		if (rows.isEmpty()) {
			kosong(l, k("layanan.kosong"), x0, y, x1, y1);
			return;
		}
		long uang = uang();
		int rowH = tata() == Tata.SEMPIT ? 25 : 28;
		menu.gambar(l, "menu", x0, y, x1, y1, rows.size(), rowH, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			boolean bisa = e.bisa();
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			int iy = ry0 + (ry1 - ry0 - 16) / 2;
			if (!e.barang().isEmpty()) {
				lk.g.item(e.barang(), rx0 + 4, iy);
			} else {
				Glyphs.Atlas.ikon(lk.g, e.i(LembarKunci.IKON, LembarKunci.Ikon.layanan(layar)), rx0 + 4, iy, 16, bisa ? 0xFFFFFFFF : 0x90FFFFFF);
			}
			int btnW = 64;
			int hargaX = rx1 - btnW - 6;
			int hw = 0;
			if (e.ada(LembarKunci.HARGA)) {
				hw = LabelHarga.gambar(lk, e.l(LembarKunci.HARGA, 0L), bisa ? uang : -1L, hargaX, ry0 + 3, true, false);
			}
			int warna = bisa ? lk.gaya.teks : Palette.terkunci(lk.gaya.teks);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 24, ry0 + 3, hargaX - hw - rx0 - 30, warna);
			Component bawah = !bisa && e.ada(LembarKunci.ALASAN) ? e.teks(LembarKunci.ALASAN) : e.teks(Layanan.EFEK);
			lk.teks(bawah, rx0 + 24, ry0 + 14, hargaX - rx0 - 28, !bisa ? lk.gaya.turun() : lk.gaya.teksLemah);
			long harga = e.l(LembarKunci.HARGA, 0L);
			boolean cukup = uang < 0 || harga <= uang;
			Component alasan = !bisa ? e.teksAtau(LembarKunci.ALASAN) : k("harga.kurang", Brush.angka(harga - Math.max(0L, uang)));
			tombolOp(lk, "pesan" + i, rx1 - btnW - 3, ry0 + (ry1 - ry0 - TOMBOL_H) / 2, btnW, TOMBOL_H, k("layanan.tombol." + jenis()), bisa && cukup, alasan,
					LembarKunci.op(Layanan.OP_PESAN, e.id()));
		}, () -> {
			int i = menu.pilihan();
			if (i >= 0 && i < rows.size() && rows.get(i).bisa() && tulisBoleh()) {
				kirim(LembarKunci.op(Layanan.OP_PESAN, rows.get(i).id()));
			}
		}, null);
	}
}
