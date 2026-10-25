package dev.kultivator.client.dunia.peta;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Small drawing helpers shared by module T's pages (the world map, the ship's helm and HUD), in the jade-scroll look of
 * DESIGN 5b / 33 ({@link Brush}, {@link Palette}): inked panels, value bars, durations (the ship HUD draws on the single HUD slip, {@code Kabar.slip}). The old talisman button went to the
 * single kit's {@code TalismanButton} (DESIGN 43.7, G07). Client thread only.
 */
public final class PetaUi {

	/** An inked parchment panel with a gold frame. */
	public static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		g.fill(x0 + 2, y0 + 2, x1 + 2, y1 + 2, 0x50000000);
		g.fillGradient(x0, y0, x1, y1, Palette.PAPER_TOP, Palette.PAPER_BOTTOM);
		Brush.frame(g, x0, y0, x1, y1, Palette.GOLD_DARK);
		Brush.frame(g, x0 + 2, y0 + 2, x1 - 2, y1 - 2, Palette.argb(0x60, Palette.GOLD));
	}

	/** A thin bar 0..1 with a colour and its frame. */
	public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float value, int color, int back) {
		g.fill(x, y, x + w, y + h, back);
		int filled = Math.round(Math.max(0.0F, Math.min(1.0F, value)) * (w - 2));
		if (filled > 0) {
			g.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, color);
			g.fill(x + 1, y + 1, x + 1 + filled, y + 2, Palette.lighten(color, 0.35F));
		}
		Brush.frame(g, x, y, x + w, y + h, 0x80000000);
	}

	/** "1 j 20 m" / "12 m" / "40 d" from seconds. */
	public static Component duration(long seconds) {
		if (seconds >= 3600L) {
			return Component.translatable("kultivator.peta.time.hm", seconds / 3600L, (seconds % 3600L) / 60L);
		}
		if (seconds >= 60L) {
			return Component.translatable("kultivator.peta.time.m", seconds / 60L);
		}
		return Component.translatable("kultivator.peta.time.s", Math.max(0L, seconds));
	}

	private PetaUi() {
	}
}
