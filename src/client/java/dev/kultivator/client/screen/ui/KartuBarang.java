package dev.kultivator.client.screen.ui;

import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The single goods card of the kit (DESIGN 43.7; moved from {@code tempat/ui/kit/KartuBarang}, replaces
 * {@code KotaPage.Card} and the launcher cards of {@code HubCardsPage}): the item (or an atlas icon), its name in the
 * quality colour, the price ({@link LabelHarga}), the stock and its state:
 * <ul>
 * <li>{@code habis}: grey card, "Habis, kembali besok";</li>
 * <li>{@code terkunci}: a grey ribbon with a padlock across the price and the reason under the name;</li>
 * <li>{@code kurang_uang}: the price in red;</li>
 * <li>{@code batas} / {@code tutup} / {@code ditolak}: dimmed with the reason;</li>
 * <li>{@code langka}: a gold star "langka hari ini"; {@code baru}: a gold flash for 400 ms after it appears.</li>
 * </ul>
 * A row painter for {@link DaftarGulir} (the list owns the click zone). Hovering shows the item name plus the state
 * reason. Text is 1.0; the second line shows only when the card is at least 20 px high.
 */
public final class KartuBarang {

	/**
	 * What one card shows. {@code harga} < 0 = no price; {@code stok} -1 = unlimited, -2 = not shown; {@code mutu} -1 =
	 * plain; {@code keadaan} one of {@code LembarKunci.K_*}.
	 */
	public record Isi(ItemStack item, int ikon, Component nama, int mutu, long harga, int stok, String keadaan, @Nullable Component alasan,
			@Nullable Component catatan, boolean langka, boolean baru) {

		/** A plain card: item, name, an optional note line (the old launcher / town cards). */
		public static Isi polos(ItemStack item, Component nama, @Nullable Component catatan) {
			return new Isi(item, -1, nama, -1, -1L, -2, LembarKunci.K_OK, null, catatan, false, false);
		}

		/** The card of a sheet entry (38.U keys). */
		public static Isi dari(Lembar e) {
			return new Isi(e.barang(), e.i(LembarKunci.IKON, -1), e.teks(LembarKunci.NAMA), e.i(LembarKunci.MUTU, -1),
					e.ada(LembarKunci.HARGA) ? e.l(LembarKunci.HARGA, 0L) : -1L, e.ada(LembarKunci.STOK) ? e.i(LembarKunci.STOK, -1) : -2,
					e.keadaan(), e.teksAtau(LembarKunci.ALASAN), e.teksAtau(LembarKunci.CATATAN), e.b(LembarKunci.LANGKA), e.b(LembarKunci.BARU));
		}
	}

	/** Draws sheet entry {@code e} as a card row; {@code uang} money on hand (-1: do not compare). */
	public static void gambar(Lukis l, Lembar e, int x0, int y0, int x1, int y1, boolean sorot, boolean pilih, long uang, long muncul) {
		gambar(l, Isi.dari(e), x0, y0, x1, y1, sorot, pilih, uang, muncul);
	}

	/** Draws a card row; {@code uang} money on hand (-1: do not compare); {@code muncul} when it appeared (ms, for the flash). */
	public static void gambar(Lukis l, Isi e, int x0, int y0, int x1, int y1, boolean sorot, boolean pilih, long uang, long muncul) {
		String keadaan = e.keadaan();
		boolean habis = LembarKunci.K_HABIS.equals(keadaan);
		boolean kunci = LembarKunci.K_TERKUNCI.equals(keadaan);
		boolean redup = habis || kunci || LembarKunci.K_BATAS.equals(keadaan) || LembarKunci.K_TUTUP.equals(keadaan)
				|| LembarKunci.K_DITOLAK.equals(keadaan);
		l.kartu(x0, y0, x1, y1, sorot ? 1.0F : 0.0F, pilih);
		if (habis) {
			l.g.fill(x0, y0, x1, y1, Palette.argb(0x50, 0x808080));
		}
		if (e.baru()) {
			float t = Math.min(1.0F, (l.kini - muncul) / 400.0F);
			if (t < 1.0F) {
				l.g.fill(x0, y0, x1, y1, Palette.argb(Math.round(0x90 * (1.0F - t)), Palette.GOLD_LIGHT & 0xFFFFFF));
			}
		}
		int h = y1 - y0;
		int ix = x0 + 3;
		int iy = y0 + (h - 16) / 2;
		ItemStack item = e.item();
		if (!item.isEmpty()) {
			l.g.item(item, ix, iy);
		} else if (e.ikon() >= 0) {
			Glyphs.Atlas.ikon(l.g, e.ikon(), ix, iy, 16, redup ? 0x90FFFFFF : 0xFFFFFFFF);
		}
		if (redup) {
			l.g.fill(ix, iy, ix + 16, iy + 16, Palette.argb(0x60, l.gaya.dasar & 0xFFFFFF));
		}
		int teksX = ix + 20;
		int kanan = x1 - 4;
		int hargaLebar = 0;
		if (e.harga() >= 0L) {
			long harga = e.harga();
			boolean merah = LembarKunci.K_KURANG_UANG.equals(keadaan);
			hargaLebar = LabelHarga.gambar(l, harga, merah ? Math.min(uang, harga - 1) : redup ? -1L : uang, kanan, y0 + (h - 8) / 2.0F, true,
					harga >= 100_000L);
			if (kunci) {
				Glyphs.Atlas.pita(l.g, false, kanan - hargaLebar - 6, y0 + (h - 12) / 2, hargaLebar + 8, 12, 0xD0FFFFFF);
				Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.GEMBOK, kanan - hargaLebar / 2 - 5, y0 + (h - 10) / 2, 10, 0xFFFFFFFF);
			}
		}
		int nameColor = redup ? Palette.terkunci(l.gaya.teks) : Palette.mutu(e.mutu(), l.gaya.gelap(), l.gaya.teks);
		float room = kanan - hargaLebar - 6 - teksX;
		Component nama = e.nama();
		boolean duaBaris = h >= 20;
		float ny = duaBaris ? y0 + 2.0F : y0 + (h - 8) / 2.0F;
		l.teks(nama, teksX, ny, room - (e.langka() ? 10 : 0), nameColor);
		if (e.langka()) {
			float nw = Math.min(room - 10, l.font.width(nama));
			Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.BINTANG_PENUH, Math.round(teksX + nw + 1), Math.round(ny - 1), 9, 0xFFFFFFFF);
		}
		if (duaBaris) {
			Component bawah = baris2(e, keadaan);
			if (bawah != null) {
				int warna = redup ? (l.gaya.gelap() ? 0xFFFF9A8A : Palette.BAHAYA) : l.gaya.teksSamar;
				l.teks(bawah, teksX, y0 + 11.0F, room, warna);
			}
		}
		if (l.di(x0, y0, x1, y1)) {
			List<Component> tip = new ArrayList<>();
			tip.add(item.isEmpty() ? nama : item.getHoverName());
			if (e.alasan() != null) {
				tip.add(e.alasan().copy().withColor(0xFFFF8A7A));
			} else if (habis) {
				tip.add(Component.translatable("kultivator.tempat_ui.kartu.habis"));
			}
			if (e.langka()) {
				tip.add(Component.translatable("kultivator.tempat_ui.kartu.langka").withColor(Palette.GOLD_LIGHT));
			}
			if (e.catatan() != null) {
				tip.add(e.catatan());
			}
			l.tip(tip);
		}
	}

	private static @Nullable Component baris2(Isi e, String keadaan) {
		if (LembarKunci.K_HABIS.equals(keadaan)) {
			return Component.translatable("kultivator.tempat_ui.kartu.habis");
		}
		if (e.alasan() != null && !LembarKunci.K_OK.equals(keadaan)) {
			return e.alasan();
		}
		if (e.catatan() != null) {
			return e.catatan();
		}
		if (e.stok() >= -1) {
			return e.stok() < 0 ? Component.translatable("kultivator.tempat_ui.kartu.stok_tak_terbatas")
					: Component.translatable("kultivator.tempat_ui.kartu.stok", e.stok());
		}
		return null;
	}

	/** A small state stamp (e.g. "TERJUAL") over a box; {@code t} 0..1 animation (spins in). Text 1.0. */
	public static void cap(Lukis l, Component teks, float cx, float cy, float lebar, float t) {
		float s = 0.6F + 0.4F * Math.min(1.0F, t);
		float angle = (1.0F - Math.min(1.0F, t)) * 1.6F - 0.18F;
		l.g.pose().pushMatrix();
		l.g.pose().translate(cx, cy);
		l.g.pose().rotate(angle);
		l.g.pose().scale(s, s);
		int w = Math.round(Math.max(lebar, l.font.width(Brush.bold(teks)) + 10));
		int h = Math.round(w * 0.42F);
		Glyphs.Atlas.stempel(l.g, Glyphs.Atlas.Stempel.PERSEGI, -w / 2, -h / 2, w, h, Palette.argb(Math.round(230 * Math.min(1.0F, t * 1.5F)), 0xFFFFFF));
		Brush.centered(l.g, l.font, Brush.bold(teks), 0.0F, -4.5F, Palette.argb(Math.round(240 * Math.min(1.0F, t * 1.5F)), 0xB3281D), 1.0F, false);
		l.g.pose().popMatrix();
	}

	private KartuBarang() {
	}
}
