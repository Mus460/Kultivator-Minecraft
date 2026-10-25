package dev.kultivator.client.sekte.mandiri;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * The 24-hour strip of DESIGN 39.7.3 ("Hari ini"): one cell per in-game hour coloured by the activity the planner ran in
 * it (cell value = {@link Kegiatan} ordinal, written by 39A each hour), runs of the same activity merged into one bar
 * with its icon when it is wide enough, hour ticks every three hours with labels at 0/6/12/18, a red needle at the
 * current hour (slowly breathing), the hours still to come hatched faintly, and yesterday's strip under it, thinner and
 * dimmed. {@link #jamDi} maps a mouse position back to an hour so the tab can show a tooltip and offer
 * "[Larang kegiatan ini]". Stateless; client only.
 */
public final class PitaJadwal {

	/** Where the strip was last drawn (for hit tests). */
	public record Kotak(float x0, float y0, float x1, float y1, float kemarinY0, float kemarinY1) {
	}

	/**
	 * Draws today's strip in {@code [x0, x1] x [y, y + h]} and yesterday's below it ({@code hk} high, 2 px gap; 0 = none).
	 * {@code jam} the current hour (-1 unknown).
	 */
	public static Kotak gambar(GuiGraphicsExtractor g, Font font, float x0, float y, float x1, float h, float hk, int[] hari, int[] kemarin,
			int jam) {
		float w = x1 - x0;
		float cell = w / 24.0F;
		// the frame and the faint future hatch
		Brush.rect(g, x0 - 1.0F, y - 1.0F, x1 + 1.0F, y + h + 1.0F, Palette.argb(0x70, Palette.INK_SOFT));
		Brush.rect(g, x0, y, x1, y + h, Palette.argb(0x40, Palette.PAPER_DEEP));
		lari(g, font, x0, y, cell, h, hari, jam, 0xFF, true);
		if (jam >= 0) {
			for (int hr = jam + 1; hr < 24; hr++) {
				float cx = x0 + hr * cell;
				for (float d = 0; d < cell; d += 3.0F) {
					Brush.rect(g, cx + d, y + h - 1.5F, cx + Math.min(cell, d + 1.5F), y + h - 0.5F, Palette.argb(0x50, Palette.INK_FAINT));
				}
			}
		}
		// hour ticks and labels
		for (int hr = 0; hr <= 24; hr += 3) {
			float tx = x0 + hr * cell;
			boolean major = hr % 6 == 0;
			Brush.rect(g, tx - 0.25F, y + h, tx + 0.25F, y + h + (major ? 2.5F : 1.5F), Palette.argb(0xA0, Palette.INK_SOFT));
			if (major && hr < 24) {
				Brush.text(g, font, Component.literal(String.format("%02d", hr)), tx + 0.5F, y + h + 3.0F, Palette.INK_FAINT, 1.0F, false);
			}
		}
		// the needle at the current hour
		if (jam >= 0 && jam < 24) {
			float t = (Util.getMillis() % 2000L) / 2000.0F;
			float glow = 0.55F + 0.45F * (float) Math.sin(t * Math.PI * 2.0D);
			float nx = x0 + (jam + 0.5F) * cell;
			Brush.rect(g, nx - 1.5F, y - 2.5F, nx + 1.5F, y + h + 2.5F, Palette.argb(Math.round(0x40 * glow), Palette.SEAL_GLOW));
			Brush.rect(g, nx - 0.5F, y - 2.0F, nx + 0.5F, y + h + 2.0F, Palette.SEAL);
			Brush.rect(g, nx - 1.5F, y - 3.0F, nx + 1.5F, y - 2.0F, Palette.SEAL);
		}
		// under the hour labels (1.0, DESIGN 43.0.5)
		float ky0 = y + h + 13.0F;
		float ky1 = ky0 + hk;
		if (hk > 0.0F && kemarin != null) {
			Brush.rect(g, x0 - 1.0F, ky0 - 1.0F, x1 + 1.0F, ky1 + 1.0F, Palette.argb(0x40, Palette.INK_SOFT));
			lari(g, font, x0, ky0, cell, hk, kemarin, -1, 0x90, false);
		}
		return new Kotak(x0, y, x1, y + h, ky0, ky1);
	}

	/** Merged runs of one strip. */
	private static void lari(GuiGraphicsExtractor g, Font font, float x0, float y, float cell, float h, int[] strip, int jam, int alpha,
			boolean icons) {
		if (strip == null || strip.length < 24) {
			return;
		}
		int start = 0;
		while (start < 24) {
			int value = strip[start];
			int end = start + 1;
			while (end < 24 && strip[end] == value && (jam < 0 || (start <= jam) == (end <= jam))) {
				end++;
			}
			boolean future = jam >= 0 && start > jam;
			Kegiatan k = Kegiatan.byOrdinal(value);
			float bx0 = x0 + start * cell;
			float bx1 = x0 + end * cell;
			if (!future && value > 0) {
				int rgb = Ikon.warna(k);
				int top = Palette.argb(alpha, Palette.lighten(Palette.opaque(rgb), 0.18F));
				int bottom = Palette.argb(alpha, Palette.darken(Palette.opaque(rgb), 0.12F));
				g.fillGradient(Math.round(bx0), Math.round(y), Math.round(bx1), Math.round(y + h), top, bottom);
				Brush.rect(g, bx0, y, bx1, y + 0.6F, Palette.argb(alpha / 3, 0xFFFFFF));
				if (icons && bx1 - bx0 >= h + 2.0F && h >= 7.0F) {
					int size = Math.round(Math.min(h - 1.0F, 10.0F));
					Ikon.kegiatan(g, k, Math.round((bx0 + bx1) / 2.0F - size / 2.0F), Math.round(y + (h - size) / 2.0F), size, alpha);
				}
			}
			if (end < 24) {
				Brush.rect(g, bx1 - 0.25F, y, bx1 + 0.25F, y + h, Palette.argb(alpha / 4, Palette.INK));
			}
			start = end;
		}
	}

	/** The hour under the mouse in a drawn strip: 0-23 today, 100-123 yesterday, -1 outside. */
	public static int jamDi(Kotak kotak, double mx, double my) {
		if (kotak == null || mx < kotak.x0() || mx >= kotak.x1()) {
			return -1;
		}
		int hr = (int) ((mx - kotak.x0()) / ((kotak.x1() - kotak.x0()) / 24.0F));
		hr = Math.max(0, Math.min(23, hr));
		if (my >= kotak.y0() - 2 && my < kotak.y1() + 2) {
			return hr;
		}
		if (my >= kotak.kemarinY0() - 1 && my < kotak.kemarinY1() + 1) {
			return 100 + hr;
		}
		return -1;
	}

	/** The first and last hour of the run that contains {@code hr}. */
	public static int[] rentang(int[] strip, int hr) {
		int v = strip[hr];
		int a = hr;
		int b = hr;
		while (a > 0 && strip[a - 1] == v) {
			a--;
		}
		while (b < 23 && strip[b + 1] == v) {
			b++;
		}
		return new int[]{a, b + 1};
	}

	private PitaJadwal() {
	}
}
