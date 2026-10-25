package dev.kultivator.client.screen.ui;

import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

/**
 * The single live ranking of the kit (DESIGN 43.7, moved from {@code tempat/ui/kit/PapanSkor}): rows slide to their new
 * places when the order changes (rows are matched by name between sheets; a row that moved up glows green for a moment,
 * down red), the player's row highlighted, the top three with gold / silver / bronze rank seals. Used by festivals, the
 * Pill Meet, the auction bid board, the Young Dragon list and the tournament standings. Text is 1.0.
 */
public final class PapanSkor {

	private static final long GESER_MS = 450L;

	private final Map<String, float[]> posisi = new HashMap<>();
	private final Map<String, Long> berubah = new HashMap<>();
	private final Map<String, Integer> arah = new HashMap<>();

	private float tampil(String id, float[] p, long kini) {
		long since = kini - berubah.getOrDefault(id, 0L);
		float t = Math.min(1.0F, since / (float) GESER_MS);
		float ease = 1.0F - (1.0F - t) * (1.0F - t);
		return p[0] + (p[1] - p[0]) * ease;
	}

	/** Forgets the slide positions (another board is shown). */
	public void lupakan() {
		posisi.clear();
		berubah.clear();
		arah.clear();
	}

	/**
	 * Draws rows {@code baris} (keys nama T, skor I or nb L, anda B, baru B, peringkat I optional) from y0 down, each
	 * {@code tinggi} px (at least 11); at most {@code maks} rows. {@code nilaiKunci} picks the number column
	 * ({@link LembarKunci#SKOR} or {@link LembarKunci#NB}); {@code uang} draws it as money.
	 */
	public void gambar(Lukis l, List<Lembar> baris, String nilaiKunci, int x0, int y0, int x1, int tinggi, int maks, boolean uang) {
		tinggi = Math.max(11, tinggi);
		int n = Math.min(maks, baris.size());
		for (int i = 0; i < n; i++) {
			Lembar e = baris.get(i);
			String id = e.teks(LembarKunci.NAMA).getString();
			float[] p = posisi.get(id);
			if (p == null) {
				p = new float[] {i, i};
				posisi.put(id, p);
			} else if ((int) p[1] != i) {
				arah.put(id, i < p[1] ? 1 : -1);
				p[0] = tampil(id, p, l.kini);
				p[1] = i;
				berubah.put(id, l.kini);
			}
			long since = l.kini - berubah.getOrDefault(id, 0L);
			float pos = tampil(id, p, l.kini);
			if (since >= GESER_MS) {
				p[0] = p[1];
			}
			int ry = Math.round(y0 + pos * tinggi);
			boolean anda = e.b(LembarKunci.ANDA);
			int fill = anda ? Palette.argb(0x70, Palette.GOLD & 0xFFFFFF) : (i % 2 == 0 ? l.gaya.kartu : 0x00000000);
			l.g.fill(x0, ry, x1, ry + tinggi - 1, fill);
			if (since < 900L && arah.containsKey(id)) {
				int a = Math.round(0x80 * (1.0F - since / 900.0F));
				l.g.fill(x0, ry, x1, ry + tinggi - 1, Palette.argb(a, arah.get(id) > 0 ? 0x4FC07A : 0xD04030));
			}
			if (e.b(LembarKunci.BARU)) {
				float flash = (float) Math.max(0.0D, Math.sin(l.kini / 100.0D));
				l.g.fill(x0, ry, x1, ry + tinggi - 1, Palette.argb(Math.round(0x50 * flash), Palette.GOLD_LIGHT & 0xFFFFFF));
			}
			int rank = e.i("peringkat", i + 1);
			int seal = rank == 1 ? 0xFFE0B040 : rank == 2 ? 0xFFC0C8D0 : rank == 3 ? 0xFFC07A40 : 0;
			float cy = ry + (tinggi - 1) / 2.0F;
			Component rk = Component.literal(Integer.toString(rank));
			float sealR = Math.max(5.0F, l.font.width(rk) / 2.0F + 2.0F);
			if (seal != 0) {
				Brush.disc(l.g, x0 + 7.0F, cy, sealR, seal);
			}
			Brush.centered(l.g, l.font, rk, x0 + 7.0F, cy - 4.0F, seal != 0 ? 0xFF2A1A10 : l.gaya.teksSamar, 1.0F, false);
			int numRight = x1 - 3;
			int numW;
			if (uang) {
				long v = e.l(nilaiKunci, 0L);
				numW = LabelHarga.gambar(l, v, -1L, numRight, cy - 4.0F, true, v >= 100_000L);
			} else {
				Component sk = Component.literal(Brush.angka(e.i(nilaiKunci, 0)));
				numW = l.font.width(sk);
				Brush.text(l.g, l.font, sk, numRight - numW, cy - 4.0F, l.gaya.teks, 1.0F, false);
			}
			int namaX = x0 + 9 + Math.round(sealR) + 2;
			Component nama = anda ? Brush.bold(e.teks(LembarKunci.NAMA)) : e.teks(LembarKunci.NAMA);
			int bintang = e.b(LembarKunci.BARU) ? 11 : 0;
			l.teks(nama, namaX, cy - 4.0F, numRight - numW - 6 - bintang - namaX, anda ? l.gaya.aksen : l.gaya.teks);
			if (bintang > 0) {
				Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.BINTANG_PENUH, numRight - numW - 12, Math.round(cy - 4), 8, 0xFFFFFFFF);
			}
		}
	}
}
