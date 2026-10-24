package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * {@code KeadaanKosong} (DESIGN 38.U.2): the empty state of a list: a small brush illustration (a closed lantern, an
 * empty tray with a cat asleep beside it, a blank scroll, depending on the screen), one sentence and an optional button
 * that points somewhere useful (the sheet's {@code kosong} compound: teks, op, tombol).
 */
public final class KeadaanKosong {

	/** Illustration kinds. */
	public enum Gambar {
		LENTERA, NAMPAN, GULUNGAN, PAPAN
	}

	/** Draws the empty state centred in the box. {@code kirim} sends the button op. */
	public static void gambar(Lukis l, TombolAksi tombol, Gambar gambar, Lembar kosong, @Nullable Component cadangan, int x0, int y0, int x1, int y1,
			@Nullable Consumer<String> kirim) {
		float cx = (x0 + x1) / 2.0F;
		int h = y1 - y0;
		Component teks = kosong.ada(LembarKunci.TEKS) ? kosong.teks(LembarKunci.TEKS) : cadangan != null ? cadangan : TeksTempat.k("kosong.bawaan");
		float lebar = Math.min(x1 - x0 - 16, 220);
		// a short box (GUI scale 4) drops the illustration first, then writes smaller; lines never leave the box
		boolean gambarAda = h >= 64;
		float iy = y0 + (gambarAda ? Math.max(6, h * 0.18F) : 4.0F);
		if (gambarAda) {
			ilustrasi(l, gambar, cx, iy + 14.0F);
		}
		float ty = gambarAda ? iy + 34.0F : iy;
		float ukuran = h < 40 ? 0.75F : 1.0F;
		java.util.List<net.minecraft.util.FormattedCharSequence> lines = l.font.split(teks, Math.max(10, Math.round(lebar / ukuran)));
		int muat = Math.max(1, (int) ((y1 - ty) / (9.0F * ukuran)));
		int n = Math.min(lines.size(), muat);
		for (int i = 0; i < n; i++) {
			float w = l.font.width(lines.get(i)) * ukuran;
			Brush.text(l.g, l.font, lines.get(i), cx - w / 2.0F, ty + i * 9.0F * ukuran, l.gaya.teksLemah, ukuran, false);
		}
		if (n < lines.size() && l.di(x0, y0, x1, y1)) {
			l.tip(teks);
		}
		float th = n * 9.0F * ukuran;
		String op = kosong.s(LembarKunci.OP);
		if (!op.isEmpty() && kirim != null && ty + th + 22 < y1) {
			Component label = kosong.ada(LembarKunci.TOMBOL) ? kosong.teks(LembarKunci.TOMBOL) : TeksTempat.k("kosong.tombol");
			int bw = TombolAksi.lebar(l, label, 70, 160);
			tombol.gambar(l, "kosong", Math.round(cx - bw / 2.0F), Math.round(ty + th + 6), bw, 15, label, true, null, () -> kirim.accept(op));
		}
	}

	private static void ilustrasi(Lukis l, Gambar gambar, float cx, float cy) {
		int ink = Palette.argb(0xA0, l.gaya.teksLemah & 0xFFFFFF);
		int soft = Palette.argb(0x50, l.gaya.teksLemah & 0xFFFFFF);
		switch (gambar) {
			case LENTERA -> {
				// an unlit lantern on its cord
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
				// an empty tray, and a cat curled up asleep beside it
				l.g.fill(Math.round(cx - 18), Math.round(cy + 4), Math.round(cx + 6), Math.round(cy + 7), ink);
				Brush.line(l.g, cx - 20, cy + 2, cx - 18, cy + 4, 1.0F, ink);
				Brush.line(l.g, cx + 8, cy + 2, cx + 6, cy + 4, 1.0F, ink);
				Brush.disc(l.g, cx + 16, cy + 2, 6.0F, soft);
				Brush.disc(l.g, cx + 20, cy - 2, 3.5F, soft);
				Brush.line(l.g, cx + 18, cy - 5, cx + 19, cy - 7, 0.8F, ink);
				Brush.line(l.g, cx + 22, cy - 5, cx + 21.5F, cy - 7, 0.8F, ink);
				Brush.line(l.g, cx + 11, cy + 5, cx + 7, cy + 7, 1.2F, ink);
				Brush.text(l.g, l.font, Component.literal("z"), cx + 23, cy - 12, ink, 0.6F, false);
				Brush.text(l.g, l.font, Component.literal("z"), cx + 26, cy - 16, ink, 0.75F, false);
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
