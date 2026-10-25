package dev.kultivator.client.screen.ui;

import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * The single empty state of the kit (DESIGN 43.7; moved from {@code tempat/ui/kit/KeadaanKosong}): a small brush
 * illustration (an unlit lantern, an empty tray with a cat asleep beside it, a blank scroll, an empty board), one sentence
 * at 1.0 and an optional talisman that points somewhere useful. A short box drops the illustration first; lines never
 * leave the box (the whole sentence becomes the tooltip when cut).
 */
public final class KeadaanKosong {

	/** Illustration kinds. */
	public enum Gambar {
		LENTERA, NAMPAN, GULUNGAN, PAPAN
	}

	/** Draws the empty state centred in the box; {@code tombol}/{@code aksi} null = no button. */
	public static void gambar(Lukis l, Gambar gambar, Component teks, @Nullable Component tombol, @Nullable Runnable aksi, int x0, int y0, int x1,
			int y1) {
		float cx = (x0 + x1) / 2.0F;
		int h = y1 - y0;
		float lebar = Math.min(x1 - x0 - 16, 240);
		boolean gambarAda = h >= 70;
		float iy = y0 + (gambarAda ? Math.max(6, h * 0.16F) : 4.0F);
		if (gambarAda) {
			ilustrasi(l, gambar, cx, iy + 14.0F);
		}
		float ty = gambarAda ? iy + 34.0F : iy;
		List<FormattedCharSequence> lines = l.font.split(teks, Math.max(10, Math.round(lebar)));
		int muat = Math.max(1, (int) ((y1 - ty) / 9.0F));
		int n = Math.min(lines.size(), muat);
		for (int i = 0; i < n; i++) {
			float w = l.font.width(lines.get(i));
			Brush.text(l.g, l.font, lines.get(i), cx - w / 2.0F, ty + i * 9.0F, l.gaya.teksLemah, 1.0F, false);
		}
		if (n < lines.size() && l.di(x0, y0, x1, y1)) {
			l.tip(teks);
		}
		float th = n * 9.0F;
		if (tombol != null && aksi != null && ty + th + 22 < y1) {
			int bw = TalismanButton.lebar(l.font, tombol, 70, 170);
			TalismanButton.gambar(l, "kosong", Math.round(cx - bw / 2.0F), Math.round(ty + th + 6), bw, 15, tombol, true, null, aksi);
		}
	}

	/**
	 * The empty state from a sheet's {@code kosong} compound (teks, op, tombol); {@code kirim} sends the button op;
	 * {@code cadangan} when the sheet has no sentence.
	 */
	public static void gambar(Lukis l, Gambar gambar, Lembar kosong, @Nullable Component cadangan, int x0, int y0, int x1, int y1,
			@Nullable Consumer<String> kirim) {
		Component teks = kosong.ada(LembarKunci.TEKS) ? kosong.teks(LembarKunci.TEKS)
				: cadangan != null ? cadangan : Component.translatable("kultivator.tempat_ui.kosong.bawaan");
		String op = kosong.s(LembarKunci.OP);
		Component tombol = op.isEmpty() || kirim == null ? null
				: kosong.ada(LembarKunci.TOMBOL) ? kosong.teks(LembarKunci.TOMBOL) : Component.translatable("kultivator.tempat_ui.kosong.tombol");
		gambar(l, gambar, teks, tombol, tombol == null ? null : () -> kirim.accept(op), x0, y0, x1, y1);
	}

	private static void ilustrasi(Lukis l, Gambar gambar, float cx, float cy) {
		int ink = Palette.argb(0xA0, l.gaya.teksLemah);
		int soft = Palette.argb(0x50, l.gaya.teksLemah);
		switch (gambar) {
			case LENTERA -> {
				Brush.line(l.g, cx, cy - 16, cx, cy - 10, 1.0F, ink);
				Brush.disc(l.g, cx, cy, 9.0F, soft);
				Brush.ring(l.g, cx, cy, 8.0F, 9.0F, ink);
				for (int k = -1; k <= 1; k++) {
					Brush.line(l.g, cx + k * 4.0F, cy - 8.0F, cx + k * 4.5F, cy + 8.0F, 0.6F, ink);
				}
				l.g.fill(Math.round(cx - 4), Math.round(cy - 11), Math.round(cx + 4), Math.round(cy - 9), ink);
				l.g.fill(Math.round(cx - 4), Math.round(cy + 9), Math.round(cx + 4), Math.round(cy + 11), ink);
			}
			case NAMPAN -> {
				l.g.fill(Math.round(cx - 18), Math.round(cy + 4), Math.round(cx + 6), Math.round(cy + 7), ink);
				Brush.line(l.g, cx - 20, cy + 2, cx - 18, cy + 4, 1.0F, ink);
				Brush.line(l.g, cx + 8, cy + 2, cx + 6, cy + 4, 1.0F, ink);
				Brush.disc(l.g, cx + 16, cy + 2, 6.0F, soft);
				Brush.disc(l.g, cx + 20, cy - 2, 3.5F, soft);
				Brush.line(l.g, cx + 18, cy - 5, cx + 19, cy - 7, 0.8F, ink);
				Brush.line(l.g, cx + 22, cy - 5, cx + 21.5F, cy - 7, 0.8F, ink);
				Brush.line(l.g, cx + 11, cy + 5, cx + 7, cy + 7, 1.2F, ink);
				// the sleeping cat's "z" as brush strokes (no small text, 43.0.5)
				Brush.line(l.g, cx + 23, cy - 12, cx + 26, cy - 12, 0.7F, ink);
				Brush.line(l.g, cx + 26, cy - 12, cx + 23, cy - 9, 0.7F, ink);
				Brush.line(l.g, cx + 23, cy - 9, cx + 26, cy - 9, 0.7F, ink);
			}
			case GULUNGAN -> {
				l.g.fill(Math.round(cx - 14), Math.round(cy - 9), Math.round(cx + 14), Math.round(cy + 9), soft);
				l.g.fill(Math.round(cx - 16), Math.round(cy - 11), Math.round(cx + 16), Math.round(cy - 8), ink);
				l.g.fill(Math.round(cx - 16), Math.round(cy + 8), Math.round(cx + 16), Math.round(cy + 11), ink);
			}
			case PAPAN -> {
				l.g.fill(Math.round(cx - 16), Math.round(cy - 10), Math.round(cx + 16), Math.round(cy + 8), soft);
				Brush.frame(l.g, Math.round(cx - 16), Math.round(cy - 10), Math.round(cx + 16), Math.round(cy + 8), ink);
				l.g.fill(Math.round(cx - 14), Math.round(cy + 8), Math.round(cx - 12), Math.round(cy + 14), ink);
				l.g.fill(Math.round(cx + 12), Math.round(cy + 8), Math.round(cx + 14), Math.round(cy + 14), ink);
				Brush.dot(l.g, cx - 6, cy - 6, 1.0F, ink);
			}
		}
	}

	private KeadaanKosong() {
	}
}
