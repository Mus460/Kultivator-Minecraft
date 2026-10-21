package dev.kultivator.client.dunia.peta;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Small drawing helpers shared by module T's screens (the world map and the ship's helm), in the jade-scroll look of
 * DESIGN 5b / 33 ({@link Brush}, {@link Palette}): talisman buttons, inked panels, value bars. Client thread only.
 */
public final class PetaUi {

	/** A button rectangle (for hit tests and drawing). */
	public static final class Button {
		public int x;
		public int y;
		public int w;
		public int h;
		public Component label;
		public boolean enabled = true;
		public boolean selected;
		public int accent = Palette.SEAL;
		public Runnable action;
		public Component tip;

		public Button(Component label, Runnable action) {
			this.label = label;
			this.action = action;
		}

		public Button at(int x, int y, int w, int h) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			return this;
		}

		public boolean over(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}

		public void draw(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
			boolean hover = enabled && over(mouseX, mouseY);
			int paper = !enabled ? Palette.LOCKED_PAPER : selected ? Palette.TALISMAN : hover ? 0xFFF6EBC4 : Palette.TALISMAN_LOW;
			g.fill(x + 1, y + 1, x + w + 1, y + h + 1, 0x40000000);
			g.fill(x, y, x + w, y + h, paper);
			Brush.frame(g, x, y, x + w, y + h, enabled ? (hover || selected ? accent : Palette.TALISMAN_EDGE) : Palette.argb(0xA0, Palette.LOCKED));
			if (selected) {
				g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, accent);
			}
			int ink = enabled ? (hover ? Palette.SEAL_DARK : Palette.INK) : Palette.INK_FAINT;
			Brush.fitCentered(g, font, label, x + w / 2.0F, y + (h - 7) / 2.0F, w - 4, ink, 0.75F, 0.5F, false);
		}
	}

	/** An inked parchment panel with a gold frame. */
	public static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		g.fill(x0 + 2, y0 + 2, x1 + 2, y1 + 2, 0x50000000);
		g.fillGradient(x0, y0, x1, y1, Palette.PAPER_TOP, Palette.PAPER_BOTTOM);
		Brush.frame(g, x0, y0, x1, y1, Palette.GOLD_DARK);
		Brush.frame(g, x0 + 2, y0 + 2, x1 - 2, y1 - 2, Palette.argb(0x60, Palette.GOLD));
	}

	/** A dark lacquer panel (the map's side bar, the HUD). */
	public static void lacquer(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float alpha) {
		int a = Math.round(0xD8 * alpha);
		g.fillGradient(x0, y0, x1, y1, Palette.argb(a, 0x1A1410), Palette.argb(a, 0x0E0B09));
		Brush.frame(g, x0, y0, x1, y1, Palette.argb(Math.round(0xC0 * alpha), Palette.GOLD_DARK));
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
