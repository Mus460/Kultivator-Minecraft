package dev.kultivator.client.screen.ui;

import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The single numbered choice list of the kit (DESIGN 43.7; from the choice list of {@code LayarDialog}, replaces the strips
 * of Dimensi, Ujian 1-3, Bait, Racik, Acara riddles, the Dao discussion of Pertemuan, Hukum 1-3 and Shifu 1-3). Each choice
 * is a talisman "1  text"; a locked choice is greyed with a padlock ribbon and its reason (tooltip and footer when clicked).
 * ↑↓ move between the choices, Enter picks, and the number keys pick while the host allows them (43.17 K3: the choice
 * numbers are registered in the frame, {@link Fokus#angka}). Many choices on a short box go into two columns. While
 * {@code tunggu} (the line is still being written) the choices show as faint slots and cannot be picked.
 */
public final class DaftarPilihan {

	/** One choice: its number (1-9 marks a key), text, lock reason (null = open), done (picked before: dimmer, still pickable). */
	public record Pilihan(int no, Component teks, @Nullable Component kunci, boolean selesai) {

		public static Pilihan of(int no, Component teks) {
			return new Pilihan(no, teks, null, false);
		}

		/** The choices of a sheet list (keys no, teks, terkunci, alasan). */
		public static List<Pilihan> dari(List<Lembar> daftar) {
			List<Pilihan> out = new ArrayList<>(daftar.size());
			for (int i = 0; i < daftar.size(); i++) {
				Lembar p = daftar.get(i);
				Component alasan = p.teksAtau(LembarKunci.ALASAN);
				Component kunci = p.b(LembarKunci.TERKUNCI) ? (alasan != null ? alasan : Component.translatable("kultivator.gui43.kit.terkunci")) : null;
				out.add(new Pilihan(p.i(LembarKunci.NO, i + 1), p.teks(LembarKunci.TEKS), kunci, false));
			}
			return out;
		}
	}

	private static final int GAP = 2;

	/** Rows of {@code n} choices in {@code kolom} columns, each {@code h} high: the block height. */
	public static int tinggi(int n, int kolom, int h) {
		return n <= 0 ? 0 : ((n + kolom - 1) / kolom) * (h + GAP);
	}

	/** Columns to use for {@code n} choices in a box {@code lebar} wide and {@code tinggi} high (2 when one column does not fit). */
	public static int kolom(int n, int lebar, int tinggi) {
		return n >= 3 && tinggi(n, 1, 15) > tinggi && lebar >= 220 ? 2 : 1;
	}

	/**
	 * Draws the choices in the box (x0, y0)..(x1, y1) from the top; {@code pilih} gets the chosen one. Returns the height
	 * used. Choices that do not fit are not drawn (the page should scroll or give more room).
	 */
	public static int gambar(Lukis l, String kunci, List<Pilihan> pilihan, int x0, int y0, int x1, int y1, boolean tunggu, Consumer<Pilihan> pilih) {
		int n = pilihan.size();
		if (n == 0) {
			return 0;
		}
		int lebar = x1 - x0;
		int kolom = kolom(n, lebar, y1 - y0);
		int baris = (n + kolom - 1) / kolom;
		int h = Math.max(13, Math.min(17, (y1 - y0) / baris - GAP));
		int kw = kolom == 1 ? lebar : (lebar - 4) / 2;
		int dipakai = 0;
		for (int i = 0; i < n; i++) {
			Pilihan p = pilihan.get(i);
			int px = x0 + (kolom == 1 ? 0 : (i % kolom) * (kw + 4));
			int py = y0 + (kolom == 1 ? i : i / kolom) * (h + GAP);
			if (py + h > y1) {
				break;
			}
			dipakai = py + h - y0;
			if (tunggu) {
				l.g.fill(px, py, px + kw, py + h, Palette.argb(0x18, 0x000000));
				continue;
			}
			boolean buka = p.kunci() == null;
			int warnaNo = buka ? (p.selesai() ? l.gaya.teksSamar : l.gaya.aksen) : Palette.terkunci(l.gaya.teks);
			Component label = Component.literal(p.no() + "  ").withColor(warnaNo).append(p.teks());
			String k = kunci + "." + p.no();
			int sebelum = l.zona.size();
			TalismanButton.gambar(l, k, px, py, kw, h, label, buka, p.kunci(), () -> pilih.accept(p));
			if (l.zona.size() > sebelum) {
				Lukis.Zona z = l.zona.get(l.zona.size() - 1);
				z.grup = kunci;
				if (p.no() >= 1 && p.no() <= 9) {
					l.nomor.putIfAbsent(p.no(), z);
				}
			}
			if (!buka) {
				int pw = Math.min(90, kw / 2);
				int pita = px + kw - pw - 4;
				Glyphs.Atlas.pita(l.g, false, pita, py + (h - 10) / 2, pw, 10, 0xC0FFFFFF);
				Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.GEMBOK, pita + 4, py + (h - 8) / 2, 8, 0xFFFFFFFF);
			} else if (p.selesai()) {
				Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.CENTANG, px + kw - 13, py + (h - 9) / 2, 9, 0xC0FFFFFF);
			}
		}
		return dipakai;
	}

	private DaftarPilihan() {
	}
}
