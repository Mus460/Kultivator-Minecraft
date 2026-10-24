package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * {@code KartuBarang} (DESIGN 38.U.2): one goods card of a list: the item (or the sheet icon), its name in the quality
 * colour, the price ({@link LabelHarga}), the stock and its state:
 * <ul>
 * <li>{@code habis}: grey card, "Habis, kembali besok";</li>
 * <li>{@code terkunci}: a grey ribbon with a padlock across the price and the reason under the name;</li>
 * <li>{@code kurang_uang}: the price in red;</li>
 * <li>{@code batas} / {@code tutup} / {@code ditolak}: dimmed with the reason;</li>
 * <li>{@code langka}: a gold star "langka hari ini"; {@code baru}: a gold flash for 400 ms after it appears.</li>
 * </ul>
 * Hovering shows the item tooltip (vanilla lines) plus the state reason.
 */
public final class KartuBarang {

	/** Draws entry {@code e} as a card row; {@code uang} money on hand (-1: do not compare). */
	public static void gambar(Lukis l, Lembar e, int x0, int y0, int x1, int y1, boolean sorot, boolean pilih, long uang, long muncul) {
		String keadaan = e.keadaan();
		boolean habis = LembarKunci.K_HABIS.equals(keadaan);
		boolean kunci = LembarKunci.K_TERKUNCI.equals(keadaan);
		boolean redup = habis || kunci || LembarKunci.K_BATAS.equals(keadaan) || LembarKunci.K_TUTUP.equals(keadaan)
				|| LembarKunci.K_DITOLAK.equals(keadaan);
		PanelTempat.kartu(l.g, l.gaya, x0, y0, x1, y1, sorot ? 1.0F : 0.0F, pilih);
		if (habis) {
			l.g.fill(x0, y0, x1, y1, Palette.argb(0x50, 0x808080));
		}
		if (e.b(LembarKunci.BARU)) {
			float t = Math.min(1.0F, (l.kini - muncul) / 400.0F);
			if (t < 1.0F) {
				l.g.fill(x0, y0, x1, y1, Palette.argb(Math.round(0x90 * (1.0F - t)), TempatWarna.EMAS_TERANG & 0xFFFFFF));
			}
		}
		int h = y1 - y0;
		int ix = x0 + 3;
		int iy = y0 + (h - 16) / 2;
		ItemStack item = e.barang();
		if (!item.isEmpty()) {
			l.g.item(item, ix, iy);
		} else {
			IkonTempat.gambar(l.g, e.i(LembarKunci.IKON, -1), ix, iy, 16, redup ? 0x90FFFFFF : 0xFFFFFFFF);
		}
		if (redup) {
			l.g.fill(ix, iy, ix + 16, iy + 16, Palette.argb(0x60, l.gaya.dasar & 0xFFFFFF));
		}
		int teksX = ix + 20;
		int kanan = x1 - 4;
		int hargaLebar = 0;
		if (e.ada(LembarKunci.HARGA)) {
			long harga = e.l(LembarKunci.HARGA, 0L);
			boolean merah = LembarKunci.K_KURANG_UANG.equals(keadaan);
			hargaLebar = LabelHarga.gambar(l, harga, merah ? Math.min(uang, harga - 1) : redup ? -1L : uang, kanan, y0 + (h - 8) / 2.0F, true,
					1.0F, harga >= 100_000L);
			if (kunci) {
				IkonTempat.pita(l.g, false, kanan - hargaLebar - 6, y0 + (h - 12) / 2, hargaLebar + 8, 12, 0xD0FFFFFF);
				IkonTempat.gambar(l.g, LembarKunci.Ikon.GEMBOK, kanan - hargaLebar / 2 - 5, y0 + (h - 10) / 2, 10, 0xFFFFFFFF);
			}
		}
		int nameColor = TempatWarna.mutu(e.i(LembarKunci.MUTU, -1), l.gaya.gelap(), l.gaya.teks);
		if (redup) {
			nameColor = TempatWarna.terkunci(l.gaya.teks);
		}
		float room = kanan - hargaLebar - 6 - teksX;
		Component nama = e.teks(LembarKunci.NAMA);
		boolean duaBaris = h >= 20;
		float ny = duaBaris ? y0 + 2.5F : y0 + (h - 8) / 2.0F;
		l.teks(nama, teksX, ny, room - (e.b(LembarKunci.LANGKA) ? 10 : 0), nameColor);
		if (e.b(LembarKunci.LANGKA)) {
			float nw = Math.min(room - 10, l.font.width(nama));
			IkonTempat.gambar(l.g, dev.kultivator.client.tempat.ui.kit.IkonTempat.BINTANG_PENUH, Math.round(teksX + nw + 1), Math.round(ny - 1), 9,
					0xFFFFFFFF);
		}
		if (duaBaris) {
			Component bawah = baris2(e, keadaan);
			if (bawah != null) {
				int warna = redup ? (l.gaya.gelap() ? 0xFFFF9A8A : TempatWarna.BAHAYA) : l.gaya.teksSamar;
				l.keterangan(bawah, teksX, y0 + 12.5F, room, warna);
			}
		}
		if (l.di(x0, y0, x1, y1)) {
			List<Component> tip = new ArrayList<>();
			if (!item.isEmpty()) {
				tip.add(item.getHoverName());
			} else {
				tip.add(nama);
			}
			Component alasan = e.teksAtau(LembarKunci.ALASAN);
			if (alasan != null) {
				tip.add(alasan.copy().withColor(0xFFFF8A7A));
			} else if (habis) {
				tip.add(TeksTempat.k("kartu.habis"));
			}
			if (e.b(LembarKunci.LANGKA)) {
				tip.add(TeksTempat.k("kartu.langka").withColor(TempatWarna.EMAS_TERANG));
			}
			Component catatan = e.teksAtau(LembarKunci.CATATAN);
			if (catatan != null) {
				tip.add(catatan);
			}
			l.tip(tip);
		}
	}

	private static Component baris2(Lembar e, String keadaan) {
		Component alasan = e.teksAtau(LembarKunci.ALASAN);
		if (LembarKunci.K_HABIS.equals(keadaan)) {
			return TeksTempat.k("kartu.habis");
		}
		if (alasan != null && !LembarKunci.K_OK.equals(keadaan)) {
			return alasan;
		}
		Component catatan = e.teksAtau(LembarKunci.CATATAN);
		if (catatan != null) {
			return catatan;
		}
		if (e.ada(LembarKunci.STOK)) {
			int stok = e.i(LembarKunci.STOK, -1);
			return stok < 0 ? TeksTempat.k("kartu.stok_tak_terbatas") : TeksTempat.k("kartu.stok", stok);
		}
		return null;
	}

	/** Brush helper for a tiny state stamp (e.g. "TERJUAL") over a box; {@code t} 0..1 animation (spins in). */
	public static void cap(Lukis l, Component teks, float cx, float cy, float lebar, float t) {
		float s = 0.6F + 0.4F * Math.min(1.0F, t);
		float angle = (1.0F - Math.min(1.0F, t)) * 1.6F - 0.18F;
		l.g.pose().pushMatrix();
		l.g.pose().translate(cx, cy);
		l.g.pose().rotate(angle);
		l.g.pose().scale(s, s);
		int w = Math.round(lebar);
		int h = Math.round(lebar * 0.42F);
		IkonTempat.stempel(l.g, IkonTempat.Stempel.PERSEGI, -w / 2, -h / 2, w, h, Palette.argb(Math.round(230 * Math.min(1.0F, t * 1.5F)), 0xFFFFFF));
		Brush.centered(l.g, l.font, Brush.bold(teks), 0.0F, -4.5F, Palette.argb(Math.round(240 * Math.min(1.0F, t * 1.5F)), 0xB3281D), 1.0F, false);
		l.g.pose().popMatrix();
	}

	private KartuBarang() {
	}
}
