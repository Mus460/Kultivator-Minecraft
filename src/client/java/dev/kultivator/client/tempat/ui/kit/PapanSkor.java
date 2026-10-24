package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

/**
 * {@code PapanSkor} (DESIGN 38.U.2): a live ranking whose rows slide to their new places when the order changes (rows are
 * matched by name between sheets; a row that moved up glows green for a moment, down red), the player's row highlighted,
 * the top three with gold / silver / bronze rank seals. Used by festivals, the Pill Meet, the auction bid board and the
 * Young Dragon list.
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

	/**
	 * Draws rows {@code baris} (keys nama T, skor I or nb L, anda B, baru B, peringkat I optional) from y0 down; at most
	 * {@code maks} rows. {@code nilaiKunci} picks the number column ({@link LembarKunci#SKOR} or {@link LembarKunci#NB}).
	 */
	public void gambar(Lukis l, List<Lembar> baris, String nilaiKunci, int x0, int y0, int x1, int tinggi, int maks, boolean uang) {
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
			int fill = anda ? Palette.argb(0x70, TempatWarna.EMAS & 0xFFFFFF) : (i % 2 == 0 ? l.gaya.kartu : 0x00000000);
			l.g.fill(x0, ry, x1, ry + tinggi - 1, fill);
			if (since < 900L && arah.containsKey(id)) {
				int a = Math.round(0x80 * (1.0F - since / 900.0F));
				l.g.fill(x0, ry, x1, ry + tinggi - 1, Palette.argb(a, arah.get(id) > 0 ? 0x4FC07A : 0xD04030));
			}
			if (e.b(LembarKunci.BARU)) {
				float flash = (float) Math.max(0.0D, Math.sin(l.kini / 100.0D));
				l.g.fill(x0, ry, x1, ry + tinggi - 1, Palette.argb(Math.round(0x50 * flash), TempatWarna.EMAS_TERANG & 0xFFFFFF));
			}
			int rank = e.i("peringkat", i + 1);
			int seal = rank == 1 ? 0xFFE0B040 : rank == 2 ? 0xFFC0C8D0 : rank == 3 ? 0xFFC07A40 : 0;
			float cy = ry + (tinggi - 1) / 2.0F;
			if (seal != 0) {
				Brush.disc(l.g, x0 + 7.0F, cy, 5.0F, seal);
			}
			Component rk = Component.literal(Integer.toString(rank));
			Brush.centered(l.g, l.font, rk, x0 + 7.0F, cy - 3.0F, seal != 0 ? 0xFF2A1A10 : l.gaya.teksSamar, 0.75F, false);
			int numRight = x1 - 3;
			int numW;
			if (uang) {
				numW = LabelHarga.gambar(l, e.l(nilaiKunci, 0L), -1L, numRight, cy - 3.5F, true, 0.85F, false);
			} else {
				Component sk = Component.literal(TeksTempat.angka(e.i(nilaiKunci, 0)));
				numW = Math.round(l.font.width(sk) * 0.85F);
				Brush.text(l.g, l.font, sk, numRight - numW, cy - 3.5F, l.gaya.teks, 0.85F, false);
			}
			Component nama = anda ? Brush.bold(e.teks(LembarKunci.NAMA)) : e.teks(LembarKunci.NAMA);
			l.teks(nama, x0 + 15, cy - 4.0F, numRight - numW - 20 - x0, anda ? l.gaya.aksen : l.gaya.teks);
			if (e.b(LembarKunci.BARU)) {
				IkonTempat.gambar(l.g, IkonTempat.BINTANG_PENUH, numRight - numW - 12, Math.round(cy - 4), 8, 0xFFFFFFFF);
			}
		}
	}
}
