package dev.kultivator.client.screen.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The unrolled scroll: jade parchment with a thin gold double border, knotted corners and faint auspicious-cloud
 * lines, held by two lacquered rods with gold knobs. Drawn for the visible (partly unrolled) height; the clouds stay
 * where they are on the full sheet so the paper seems to open over them.
 */
public final class ScrollFrame {

	/** Border inset from the sides and from the rods. */
	public static final int SIDE = 3;
	public static final int END = 7;

	/** The paper between {@code top} and {@code bottom} of a sheet that is {@code fullTop..fullBottom} when open. */
	public static void paper(GuiGraphicsExtractor g, int x0, int x1, int top, int bottom, int fullTop, int fullBottom) {
		if (bottom - top < 2) {
			return;
		}
		float full = Math.max(1, fullBottom - fullTop);
		int colorTop = paperAt(top, fullTop, full);
		int colorBottom = paperAt(bottom, fullTop, full);
		g.fill(x0 + 2, top + 2, x1 + 2, bottom + 2, 0x60000000);
		g.fillGradient(x0, top, x1, bottom, colorTop, colorBottom);
		// Darker paper at the sides, where it curls.
		g.fill(x0, top, x0 + 2, bottom, Palette.argb(0x40, Palette.PAPER_DEEP));
		g.fill(x1 - 2, top, x1, bottom, Palette.argb(0x40, Palette.PAPER_DEEP));
		g.fill(x0 + 2, top, x0 + 3, bottom, Palette.argb(0x20, Palette.PAPER_DEEP));
		g.fill(x1 - 3, top, x1 - 2, bottom, Palette.argb(0x20, Palette.PAPER_DEEP));

		g.enableScissor(x0, top, x1, bottom);
		int w = x1 - x0;
		cloud(g, x0 + w * 0.17F, fullTop + full * 0.24F, 6.0F, fullTop, full);
		cloud(g, x1 - w * 0.2F, fullTop + full * 0.4F, 8.0F, fullTop, full);
		cloud(g, x0 + w * 0.36F, fullTop + full * 0.83F, 7.0F, fullTop, full);
		cloud(g, x1 - w * 0.34F, fullTop + full * 0.9F, 5.0F, fullTop, full);
		mist(g, x0 + w * 0.5F, fullTop + full * 0.6F, w * 0.3F, fullTop, full);
		mist(g, x0 + w * 0.22F, fullTop + full * 0.52F, w * 0.12F, fullTop, full);
		g.disableScissor();

		// The gold double border with a knot at each corner.
		int bx0 = x0 + SIDE;
		int bx1 = x1 - SIDE;
		int by0 = top + END;
		int by1 = bottom - END;
		if (by1 - by0 > 6) {
			Brush.frame(g, bx0, by0, bx1, by1, Palette.GOLD);
			Brush.frame(g, bx0 + 2, by0 + 2, bx1 - 2, by1 - 2, Palette.argb(0xC0, Palette.GOLD_DARK));
			knot(g, bx0, by0, colorTop, 1, 1);
			knot(g, bx1 - 1, by0, colorTop, -1, 1);
			knot(g, bx0, by1 - 1, colorBottom, 1, -1);
			knot(g, bx1 - 1, by1 - 1, colorBottom, -1, -1);
		}
	}

	/** One lacquered scroll rod centred on {@code cy}, a little wider than the paper, with gold knobs. */
	public static void rod(GuiGraphicsExtractor g, int x0, int x1, int cy) {
		int l = x0 - 5;
		int r = x1 + 5;
		g.fill(l + 1, cy + 2, r + 1, cy + 6, 0x50000000);
		g.fillGradient(l, cy - 4, r, cy + 4, Palette.ROD_LIGHT, Palette.ROD_DARK);
		g.fill(l, cy - 3, r, cy - 2, 0x38FFFFFF);
		g.fill(l, cy + 3, r, cy + 4, 0x40000000);
		// Gold bands where the paper is fixed.
		g.fill(x0 - 2, cy - 4, x0, cy + 4, Palette.GOLD);
		g.fill(x1, cy - 4, x1 + 2, cy + 4, Palette.GOLD);
		knob(g, l - 1.5F, cy);
		knob(g, r + 1.5F, cy);
	}

	private static void knob(GuiGraphicsExtractor g, float cx, float cy) {
		Brush.disc(g, cx + 0.5F, cy + 1.0F, 4.6F, 0x50000000);
		Brush.disc(g, cx, cy, 4.6F, Palette.GOLD_DARK);
		Brush.disc(g, cx, cy, 3.7F, Palette.GOLD);
		Brush.disc(g, cx - 1.1F, cy - 1.2F, 1.2F, Palette.GOLD_LIGHT);
	}

	/**
	 * A corner knot: nested squares like an endless knot on the border corner {@code x, y}, with a bead tied on each
	 * border line a little further in ({@code dx, dy} point into the sheet).
	 */
	private static void knot(GuiGraphicsExtractor g, int x, int y, int paper, int dx, int dy) {
		g.fill(x - 3, y - 3, x + 4, y + 4, Palette.GOLD);
		g.fill(x - 2, y - 2, x + 3, y + 3, paper);
		g.fill(x - 1, y - 1, x + 2, y + 2, Palette.GOLD_DARK);
		g.fill(x, y, x + 1, y + 1, Palette.GOLD_LIGHT);
		int bx = x + dx * 7;
		int by = y + dy * 7;
		g.fill(bx - 1, y - 1, bx + 2, y + 2, Palette.GOLD);
		g.fill(bx, y, bx + 1, y + 1, Palette.GOLD_LIGHT);
		g.fill(x - 1, by - 1, x + 2, by + 2, Palette.GOLD);
		g.fill(x, by, x + 1, by + 1, Palette.GOLD_LIGHT);
	}

	/** An auspicious cloud: three bumps, a curl inside and a tail. */
	private static void cloud(GuiGraphicsExtractor g, float cx, float cy, float s, int fullTop, float full) {
		int color = Palette.mix(paperAt(Math.round(cy), fullTop, full), Palette.PAPER_DEEP, 0.55F);
		float t = 0.7F;
		Brush.arc(g, cx - s, cy, s * 0.55F, s * 0.55F + t, 0.75F, 1.25F, color);
		Brush.arc(g, cx, cy - s * 0.3F, s * 0.78F, s * 0.78F + t, 0.75F, 1.25F, color);
		Brush.arc(g, cx + s, cy, s * 0.55F, s * 0.55F + t, 0.75F, 1.25F, color);
		Brush.arc(g, cx, cy - s * 0.3F, s * 0.32F, s * 0.32F + t, 0.0F, 0.7F, color);
		Brush.line(g, cx - s * 1.55F, cy, cx + s * 1.9F, cy, t, color);
		Brush.arc(g, cx + s * 1.9F, cy + s * 0.32F, s * 0.32F - t, s * 0.32F, 0.0F, 0.5F, color);
	}

	/** A long thin wisp of mist. */
	private static void mist(GuiGraphicsExtractor g, float cx, float cy, float half, int fullTop, float full) {
		int color = Palette.mix(paperAt(Math.round(cy), fullTop, full), Palette.PAPER_DEEP, 0.35F);
		Brush.line(g, cx - half, cy, cx + half, cy, 0.7F, color);
		Brush.line(g, cx - half * 0.6F, cy + 2.5F, cx + half * 0.8F, cy + 2.5F, 0.7F, color);
	}

	private static int paperAt(int y, int fullTop, float full) {
		return Palette.mix(Palette.PAPER_TOP, Palette.PAPER_BOTTOM, Math.max(0.0F, Math.min(1.0F, (y - fullTop) / full)));
	}

	private ScrollFrame() {
	}
}
