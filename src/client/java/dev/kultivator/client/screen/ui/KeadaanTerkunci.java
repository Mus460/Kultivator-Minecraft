package dev.kultivator.client.screen.ui;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * The single locked state of the kit (DESIGN 43.7; moved from {@code tempat/ui/kit/KeadaanTerkunci}): the red wax
 * padlock seal pressed onto the paper (it lands with a small bounce), the reason in bold and what is needed
 * ({@code syarat}) under a thin rule, all at 1.0. Used for a locked page, a locked sub (a far disciple, 43.18.3) and a
 * sheet's {@code kunci_layar}.
 */
public final class KeadaanTerkunci {

	/** Draws the lock state centred in the box; {@code sejak} ms since it was first shown (for the stamp landing). */
	public static void gambar(Lukis l, Component alasan, @Nullable Component syarat, int x0, int y0, int x1, int y1, long sejak) {
		float cx = (x0 + x1) / 2.0F;
		int h = y1 - y0;
		float t = Math.min(1.0F, sejak / 260.0F);
		float bounce = t < 1.0F ? 1.0F + 0.35F * (1.0F - t) * (float) Math.cos(t * 9.0F) : 1.0F;
		boolean kecil = h < 80;
		int size = Math.round((kecil ? 18 : Math.min(40, Math.max(24, h * 0.22F))) * bounce);
		float sy = y0 + (kecil ? 4.0F : Math.max(8.0F, h * 0.16F));
		Glyphs.Atlas.stempel(l.g, Glyphs.Atlas.Stempel.GEMBOK, Math.round(cx - size / 2.0F), Math.round(sy), size, size,
				Palette.argb(Math.round(255 * Math.min(1.0F, t * 2.0F)), 0xFFFFFF));
		float ty = sy + size + 6.0F;
		float lebar = Math.min(x1 - x0 - 20, 260);
		ty += tengah(l, Brush.bold(alasan), cx, ty, lebar, l.gaya.teks, y1);
		if (syarat != null && !syarat.getString().isEmpty() && ty + 14 < y1) {
			l.garis(Math.round(cx - lebar / 3), Math.round(cx + lebar / 3), Math.round(ty + 3));
			tengah(l, syarat, cx, ty + 8.0F, lebar, l.gaya.teksLemah, y1);
		}
	}

	private static float tengah(Lukis l, Component teks, float cx, float y, float lebar, int warna, int batas) {
		List<FormattedCharSequence> lines = l.font.split(teks, Math.max(10, Math.round(lebar)));
		int n = Math.max(0, Math.min(lines.size(), (int) ((batas - y) / 10.0F)));
		for (int i = 0; i < n; i++) {
			float w = l.font.width(lines.get(i));
			Brush.text(l.g, l.font, lines.get(i), cx - w / 2.0F, y + i * 10.0F, warna, 1.0F, false);
		}
		if (n < lines.size() && l.di(Math.round(cx - lebar / 2), Math.round(y), Math.round(cx + lebar / 2), batas)) {
			l.tip(teks);
		}
		return n * 10.0F;
	}

	private KeadaanTerkunci() {
	}
}
