package dev.kultivator.client.screen.ui;

import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The single mission board of the kit (DESIGN 43.7, laid out like {@code LayarGildaMisi}; replaces the boards of Gilda
 * Misi, LayarPapan, the town mission board (K) and the bounty contracts). A list of missions (rank badge E-S, title, reward
 * and direction, or the lock reason) beside the detail slip: title with the rank stamp, requirement, the story (cut to the
 * room left, whole as tooltip), rewards, time and direction, a countdown when the mission runs, and two talismans
 * "Terima" and "Tunjuk di peta". In a narrow box the detail goes under the list. Text is 1.0.
 */
public final class PapanMisi {

	/**
	 * One mission. {@code pangkat} "E".."S" ("" = none); {@code imbalanLain} extra reward pieces (Nama Baik, points...);
	 * {@code waktu} the time / direction line; {@code kunci} lock reason (null = open); {@code adaLokasi} whether "Tunjuk di
	 * peta" can work; {@code sisaTick} > 0 with {@code diterima} shows a countdown of {@code totalTick}.
	 */
	public record Misi(String id, String pangkat, Component judul, @Nullable Component syarat, @Nullable Component cerita, long imbalanNb,
			List<Component> imbalanLain, @Nullable Component waktu, @Nullable Component arah, @Nullable Component kunci, boolean adaLokasi, long sisaTick,
			long totalTick, long diterima) {
	}

	private static final int BARIS = 20;

	/**
	 * Draws the board in the box: list (selection in {@code daftar}) and detail. {@code alasanTerima} a reason that blocks
	 * every "Terima" (daily limit reached; null = none); {@code terima} / {@code tunjuk} what the talismans do (tunjuk null =
	 * no map button). {@code labelTerima} null = "Terima" (a running board passes "Serahkan").
	 */
	public static void gambar(Lukis l, String kunci, DaftarGulir daftar, List<Misi> misi, int x0, int y0, int x1, int y1, @Nullable Component alasanTerima,
			@Nullable Component labelTerima, Consumer<Misi> terima, @Nullable Consumer<Misi> tunjuk) {
		if (misi.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.PAPAN, Component.translatable("kultivator.gui43.kit.misi.kosong"), null, null, x0, y0, x1, y1);
			return;
		}
		boolean sempit = x1 - x0 < 320;
		int lx1 = sempit ? x1 : x0 + (x1 - x0) * 9 / 20;
		int ly1 = sempit ? y0 + Math.max(BARIS * 2, (y1 - y0) * 2 / 5) : y1;
		daftar.gambar(l, kunci, x0, y0, lx1, ly1, misi.size(), BARIS, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> baris(lk, misi.get(i), rx0, ry0,
				rx1, ry1, sorot, pilih), () -> {
					int i = daftar.pilihan();
					if (i >= 0 && i < misi.size() && misi.get(i).kunci() == null && alasanTerima == null) {
						terima.accept(misi.get(i));
					}
				}, null);
		int sel = daftar.pilihan();
		Misi m = sel >= 0 && sel < misi.size() ? misi.get(sel) : null;
		if (m != null) {
			detail(l, kunci, m, sempit ? x0 : lx1 + 4, sempit ? ly1 + 4 : y0, x1, y1, alasanTerima, labelTerima, terima, tunjuk);
		}
	}

	/** One list row (a {@link DaftarGulir.Baris} painter). */
	public static void baris(Lukis l, Misi m, int x0, int y0, int x1, int y1, boolean sorot, boolean pilih) {
		boolean kunci = m.kunci() != null;
		l.kartu(x0, y0, x1, y1, sorot ? 1.0F : 0.0F, pilih);
		int tx = x0 + 3;
		if (!m.pangkat().isEmpty()) {
			LencanaPangkat.gambar(l, m.pangkat(), x0 + 2, y0 + 2, 15);
			tx = x0 + 21;
		}
		int kanan = x1 - 3;
		if (m.sisaTick() > 0L) {
			kanan -= CincinWaktu.teks(l, kanan, y0 + 1, m.sisaTick(), m.totalTick(), m.diterima(), true) + 4;
		}
		l.teks(m.judul(), tx, y0 + 1, kanan - tx, kunci ? Palette.terkunci(l.gaya.teks) : l.gaya.teks);
		if (kunci) {
			Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.GEMBOK, tx, y0 + 10, 8, 0xFFFFFFFF);
			l.teks(m.kunci(), tx + 10, y0 + 10, x1 - tx - 13, l.gaya.gelap() ? 0xFFFF9A8A : Palette.BAHAYA);
			return;
		}
		int w = LabelHarga.gambar(l, m.imbalanNb(), -1L, tx, y0 + 10, false, m.imbalanNb() >= 100_000L);
		if (m.arah() != null) {
			l.samar(m.arah(), tx + w + 6, y0 + 10, x1 - tx - w - 9);
		}
	}

	/** The detail slip of one mission in the box. */
	public static void detail(Lukis l, String kunci, Misi m, int bx0, int by0, int bx1, int by1, @Nullable Component alasanTerima,
			@Nullable Component labelTerima, Consumer<Misi> terima, @Nullable Consumer<Misi> tunjuk) {
		if (by1 - by0 < 40) {
			return;
		}
		l.sumur(bx0, by0, bx1, by1);
		int x0 = bx0 + 5;
		int x1 = bx1 - 5;
		int y = by0 + 4;
		int stempel = m.pangkat().isEmpty() ? 0 : 24;
		if (stempel > 0) {
			LencanaPangkat.gambar(l, m.pangkat(), x1 - 20, y, 20);
		}
		l.teks(Brush.bold(m.judul()), x0, y + 2, x1 - x0 - stempel, l.gaya.teksSumur());
		y += 12;
		if (m.syarat() != null) {
			l.teks(m.syarat(), x0, y, x1 - x0 - stempel, l.gaya.teksLemah);
			y += 10;
		}
		y += 2;
		int bawah = by1 - 19;
		int baris = 11 + (m.waktu() != null ? 10 : 0) + (m.sisaTick() > 0L ? 10 : 0);
		if (m.cerita() != null) {
			float ruang = bawah - 4 - baris - y;
			y += Math.round(l.bungkus(m.cerita().copy().withStyle(ChatFormatting.ITALIC), x0, y, x1 - x0, l.gaya.teksSumur(), ruang)) + 4;
		}
		// rewards
		int rx = x0;
		rx += LabelHarga.gambar(l, m.imbalanNb(), -1L, rx, y, false, false) + 6;
		for (Component c : m.imbalanLain()) {
			if (rx >= x1 - 10) {
				break;
			}
			l.teks(c, rx, y, x1 - rx, l.gaya.teksSumur());
			rx += l.font.width(c) + 6;
		}
		y += 11;
		if (m.waktu() != null) {
			l.teks(m.waktu(), x0, y, x1 - x0, l.gaya.teksLemah);
			y += 10;
		}
		if (m.sisaTick() > 0L) {
			Component sisa = Component.translatable("kultivator.gui43.kit.misi.sisa");
			l.teks(sisa, x0, y, 80, l.gaya.teksLemah);
			CincinWaktu.teks(l, x0 + Math.min(80, l.font.width(sisa)) + 4, y, m.sisaTick(), m.totalTick(), m.diterima(), false);
		}
		int half = tunjuk == null ? x1 - x0 : (x1 - x0 - 4) / 2;
		Component alasan = m.kunci() != null ? m.kunci() : alasanTerima;
		Component label = labelTerima != null ? labelTerima : Component.translatable("kultivator.gui43.kit.misi.terima");
		TalismanButton.gambar(l, kunci + ":terima", x0, bawah, half, 15, label, alasan == null, alasan, () -> terima.accept(m));
		if (tunjuk != null) {
			TalismanButton.gambar(l, kunci + ":tunjuk", x0 + half + 4, bawah, half, 15, Component.translatable("kultivator.gui43.kit.misi.tunjuk"),
					m.adaLokasi(), Component.translatable("kultivator.gui43.kit.misi.tanpa_lokasi"), () -> tunjuk.accept(m));
		}
	}

	private PapanMisi() {
	}
}
