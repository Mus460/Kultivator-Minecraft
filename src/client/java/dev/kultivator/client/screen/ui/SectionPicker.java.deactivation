package dev.kultivator.client.screen.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * "‹ section ›": one bamboo selector that steps through a page's sections (DESIGN 5b: navigation is never a strip of
 * tabs along the top). Clicking the left half picks the previous section, the right half the next one.
 */
public class SectionPicker extends Widget {

	private final String[] keys;
	private final IntSupplier current;
	private final IntConsumer choose;

	public SectionPicker(int x, int y, int w, int h, String[] keys, IntSupplier current, IntConsumer choose) {
		super(x, y, w, h);
		this.keys = keys;
		this.current = current;
		this.choose = choose;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
		int offset = pressOffset(over);
		int top = Palette.lighten(Palette.BAMBOO, 0.1F * hover.get());
		g.fillGradient(x, y + offset, x + w, y + h + offset, top, Palette.BAMBOO_ALT);
		Brush.frame(g, x, y + offset, x + w, y + h + offset, Palette.BAMBOO_KNOT);
		float mid = y + offset + h / 2.0F;
		Brush.centered(g, font, Component.literal("‹"), x + 5.0F, mid - 4.0F, Palette.INK, 1.0F, false);
		Brush.centered(g, font, Component.literal("›"), x + w - 5.0F, mid - 4.0F, Palette.INK, 1.0F, false);
		int index = Math.floorMod(current.getAsInt(), Math.max(1, keys.length));
		Component text = keys.length == 0 ? Component.empty()
				: Component.translatable(keys[index]).append(Component.literal("  " + (index + 1) + "/" + keys.length));
		Brush.fitCentered(g, font, text, x + w / 2.0F, mid - 3.5F, w - 22.0F, Palette.INK, 0.8F, 0.5F, false);
	}

	@Override
	public boolean release(double mouseX, double mouseY) {
		boolean was = pressed;
		pressed = false;
		if (!was || !contains(mouseX, mouseY) || keys.length == 0) {
			return false;
		}
		int next = Math.floorMod(current.getAsInt() + (mouseX < x + w / 2.0D ? -1 : 1), keys.length);
		UiSound.tab();
		choose.accept(next);
		return true;
	}
}
