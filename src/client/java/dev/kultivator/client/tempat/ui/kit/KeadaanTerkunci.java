package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * {@code KeadaanTerkunci} (DESIGN 38.U.2): a locked page or screen: the red wax padlock seal pressed onto the paper (it
 * lands with a small bounce), the reason in bold, and what is needed ({@code syarat}) under a thin rule.
 */
public final class KeadaanTerkunci {

	/** Draws the lock state centred in the box; {@code sejak} ms since it was first shown (for the stamp landing). */
	public static void gambar(Lukis l, Component alasan, @Nullable Component syarat, int x0, int y0, int x1, int y1, long sejak) {
		float cx = (x0 + x1) / 2.0F;
		int h = y1 - y0;
		float t = Math.min(1.0F, sejak / 260.0F);
		float bounce = t < 1.0F ? 1.0F + 0.35F * (1.0F - t) * (float) Math.cos(t * 9.0F) : 1.0F;
		int size = Math.round(Math.min(40, Math.max(24, h * 0.22F)) * bounce);
		float sy = y0 + Math.max(8.0F, h * 0.16F);
		IkonTempat.stempel(l.g, IkonTempat.Stempel.GEMBOK, Math.round(cx - size / 2.0F), Math.round(sy), size, size,
				Palette.argb(Math.round(255 * Math.min(1.0F, t * 2.0F)), 0xFFFFFF));
		float ty = sy + size + 8.0F;
		float lebar = Math.min(x1 - x0 - 20, 240);
		ty += tengah(l, Brush.bold(alasan), cx, ty, lebar, l.gaya.teks);
		if (syarat != null && !syarat.getString().isEmpty()) {
			PanelTempat.garis(l.g, l.gaya, Math.round(cx - lebar / 3), Math.round(cx + lebar / 3), Math.round(ty + 4));
			tengah(l, syarat, cx, ty + 9.0F, lebar, l.gaya.teksLemah);
		}
	}

	private static float tengah(Lukis l, Component teks, float cx, float y, float lebar, int warna) {
		java.util.List<FormattedCharSequence> lines = l.font.split(teks, Math.max(10, Math.round(lebar)));
		for (int i = 0; i < lines.size(); i++) {
			float w = l.font.width(lines.get(i));
			Brush.text(l.g, l.font, lines.get(i), cx - w / 2.0F, y + i * 10.0F, warna, 1.0F, false);
		}
		return lines.size() * 10.0F;
	}

	private KeadaanTerkunci() {
	}
}
