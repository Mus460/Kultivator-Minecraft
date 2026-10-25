package dev.kultivator.client.codex;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The small controls of the Kitab Teknik, all in the jade-scroll look (DESIGN 5b): round jade coins with a carved icon
 * (Dao and grade filters), paper chips (state filters), cycling talismans (Ranah, Jenis, sort; right click steps back)
 * and the left-edge tablets, with a blood-red variant for the Kehancuran page.
 */
final class CodexWidgets {

	/** Draws an icon centred on (cx, cy) of the given size and colour. */
	interface Icon {
		void draw(GuiGraphicsExtractor g, float cx, float cy, float size, int color);
	}

	/** A round jade coin with a carved icon; lit (gold rim, bright jade) while active. */
	static final class Coin extends Widget {
		private final Icon icon;
		private final BooleanSupplier active;
		private final int tint;

		Coin(int x, int y, int size, int tint, Icon icon, BooleanSupplier active) {
			super(x, y, size, size);
			this.icon = icon;
			this.active = active;
			this.tint = tint;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			boolean on = active.getAsBoolean();
			float r = w / 2.0F;
			float cx = x + r;
			float cy = y + r + pressOffset(over);
			if (on || over) {
				Brush.halo(g, cx, cy, r, 2.0F, on ? 0xFFE07A : 0x9CFFD0, on ? 0.8F : hover.get());
			}
			Brush.disc(g, cx, cy, r, on ? Palette.GOLD : Palette.JADE_DULL);
			Brush.disc(g, cx, cy, r - 1.0F, on ? Palette.mix(Palette.JADE_LIGHT, tint, 0.35F) : Palette.mix(0xFFA9B5AE, tint, 0.2F));
			icon.draw(g, cx, cy, w * 0.62F, on ? Palette.INK : Palette.INK_SOFT);
		}

		@Override
		protected void activate() {
			UiSound.toggle(!active.getAsBoolean());
			if (action != null) {
				action.run();
			}
		}
	}

	/** A small paper chip with a label; stamped red while active. */
	static final class Chip extends Widget {
		private final Supplier<Component> label;
		private final BooleanSupplier active;

		Chip(int x, int y, int w, int h, Supplier<Component> label, BooleanSupplier active) {
			super(x, y, w, h);
			this.label = label;
			this.active = active;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			boolean on = active.getAsBoolean();
			int press = pressOffset(over);
			int top = on ? Palette.lighten(Palette.TALISMAN, 0.1F) : Palette.mix(Palette.PAPER_TOP, Palette.TALISMAN, 0.3F + 0.3F * hover.get());
			g.fillGradient(x, y + press, x + w, y + h + press, top, on ? Palette.TALISMAN_LOW : Palette.PAPER_SHADE);
			Brush.frame(g, x, y + press, x + w, y + h + press, on ? Palette.SEAL : Palette.argb(0xA0, Palette.TALISMAN_EDGE));
			if (on) {
				g.fill(x + 1, y + press + 1, x + 3, y + h + press - 1, Palette.SEAL);
			}
			Brush.fitCentered(g, font, label.get(), x + w / 2.0F + (on ? 1.0F : 0.0F), y + press + (h - 6.0F) / 2.0F, w - 5.0F, Palette.INK, 0.6F, 0.4F, false);
		}

		@Override
		protected void activate() {
			UiSound.toggle(!active.getAsBoolean());
			if (action != null) {
				action.run();
			}
		}
	}

	/** A talisman that cycles through choices: left click forward, right click back. */
	static final class Cycle extends Widget {
		private final Supplier<Component> label;
		private final IntConsumer step;
		private final int seal;

		Cycle(int x, int y, int w, int h, int seal, Supplier<Component> label, IntConsumer step) {
			super(x, y, w, h);
			this.label = label;
			this.step = step;
			this.seal = seal;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			int press = pressOffset(over);
			int bx = x + press;
			int by = y + press;
			float glow = hover.get();
			g.fill(bx + 1, by + 2, bx + w + 1, by + h + 1, 0x30000000);
			g.fillGradient(bx, by, bx + w, by + h, Palette.lighten(Palette.TALISMAN, 0.12F * glow), Palette.TALISMAN_LOW);
			Brush.frame(g, bx, by, bx + w, by + h, Palette.TALISMAN_EDGE);
			int size = Math.max(4, h - 5);
			int sx = bx + 2;
			int sy = by + (h - size) / 2;
			g.fill(sx, sy, sx + size, sy + size, Palette.mix(seal, Palette.SEAL_GLOW, 0.5F * glow));
			Brush.centered(g, font, Component.literal("‹"), bx + size + 5.0F, by + (h - 7.0F) / 2.0F, Palette.INK_SOFT, 0.7F, false);
			Brush.centered(g, font, Component.literal("›"), bx + w - 4.0F, by + (h - 7.0F) / 2.0F, Palette.INK_SOFT, 0.7F, false);
			float tx = bx + size + 8.0F;
			float room = w - size - 15.0F;
			float scale = Brush.fitScale(font, label.get(), room, 0.62F, 0.4F);
			Brush.fitCentered(g, font, label.get(), tx + room / 2.0F, by + (h - 8.0F * scale) / 2.0F, room, Palette.INK, 0.62F, 0.4F, false);
		}

		@Override
		protected void activate() {
			UiSound.tab();
			step.accept(1);
		}

		/** Right click: one step back. */
		void back() {
			UiSound.tab();
			step.accept(-1);
		}
	}

	/** A tablet on the left edge of the codex scroll; the Kehancuran tablet is blood red. */
	static final class Tablet extends Widget {
		private final int icon;
		private final Supplier<Component> name;
		private final BooleanSupplier active;
		private final boolean red;
		private final Eased slide = new Eased(12.0F);

		Tablet(int x, int y, int w, int h, int icon, boolean red, Supplier<Component> name, BooleanSupplier active) {
			super(x, y, w, h);
			this.icon = icon;
			this.name = name;
			this.active = active;
			this.red = red;
		}

		@Override
		public boolean contains(double mouseX, double mouseY) {
			return mouseX >= x - 6 && mouseY >= y && mouseX < x + w && mouseY < y + h;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			float s = slide.to(active.getAsBoolean() ? 1.0F : 0.0F, seconds);
			float glow = hover.get();
			int out = Math.round(s * 5.0F + glow * 2.0F);
			int bx = x - out;
			int r = bx + w;
			int b = y + h;
			int light = red ? 0xFFE0503C : Palette.JADE_LIGHT;
			int base = red ? 0xFFA0241A : Palette.JADE;
			int dark = red ? 0xFF4E0C08 : Palette.JADE_DARK;
			if (s > 0.02F) {
				Brush.chamfer(g, bx - 2, y - 2, r + 2, b + 2, 4, Palette.argb(Math.round(70 * s), red ? 0xFF8A6A : Palette.GOLD_LIGHT));
			}
			g.fill(bx + 2, y + 2, r + 2, b + 2, 0x50000000);
			Brush.chamferGradient(g, bx, y, r, b, 3, Palette.mix(Palette.mix(base, light, 0.25F), light, 0.45F * s + 0.25F * glow),
					Palette.mix(dark, base, 0.5F * s + 0.2F * glow));
			int carve = s > 0.5F ? Palette.argb(0xC0, Palette.GOLD) : Palette.argb(0x90, dark);
			g.fill(bx + 2, y + 3, r - 2, y + 4, carve);
			g.fill(bx + 2, b - 4, r - 2, b - 3, carve);
			g.fill(bx + 2, y + 4, bx + 3, b - 4, carve);
			g.fill(r - 3, y + 4, r - 2, b - 4, carve);
			int ink = s > 0.5F ? Palette.GOLD_LIGHT : red ? 0xFFFFD8CC : Palette.mix(Palette.JADE_PALE, 0xFFFFFFFF, glow * 0.5F);
			boolean small = h < 44;
			float iconSize = Math.min(w - 9, small ? 7 : 10);
			Glyphs.tab(g, icon, bx + w / 2.0F, y + 6.0F + iconSize / 2.0F, iconSize, ink);
			Component text = name.get();
			float length = h - iconSize - (small ? 12.0F : 14.0F);
			if (length > 8.0F) {
				float scale = Brush.fitScale(font, text, length, 0.75F, 0.45F);
				float drawn = Math.min(length, font.width(text) * scale);
				float startY = y + iconSize + (small ? 8.0F : 9.0F) + (length - drawn) / 2.0F;
				g.pose().pushMatrix();
				g.pose().translate(bx + w / 2.0F + 4.0F * scale, startY);
				g.pose().rotate((float) (Math.PI / 2.0D));
				Brush.fit(g, font, text, 0.0F, 0.0F, length, ink, 0.75F, 0.45F, false);
				g.pose().popMatrix();
			}
		}

		@Override
		protected void activate() {
			UiSound.tab();
			if (action != null) {
				action.run();
			}
		}
	}

	private CodexWidgets() {
	}
}
