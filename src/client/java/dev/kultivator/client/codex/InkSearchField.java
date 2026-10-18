package dev.kultivator.client.codex;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.function.Consumer;

/**
 * The ink search field of the Kitab Teknik (DESIGN 22.12.3): a slip of paper with a brush mark; click (or start typing
 * anywhere on the Kodeks page) to write, Backspace erases, Escape or Enter puts the brush down, and every change filters
 * the grid at once. A faint hint is written while it is empty.
 */
public final class InkSearchField extends Widget {

	public static final int MAX = 40;

	private final Consumer<String> onChange;
	private String text = "";
	private boolean focused;

	public InkSearchField(int x, int y, int w, int h, String initial, Consumer<String> onChange) {
		super(x, y, w, h);
		this.onChange = onChange;
		this.text = initial == null ? "" : initial;
	}

	public boolean focused() {
		return focused;
	}

	public void setFocused(boolean value) {
		focused = value;
	}

	public String text() {
		return text;
	}

	public void clear() {
		if (!text.isEmpty()) {
			text = "";
			onChange.accept(text);
		}
	}

	@Override
	protected void activate() {
		UiSound.press();
		focused = true;
	}

	public boolean keyPressed(KeyEvent event) {
		if (!focused) {
			return false;
		}
		int key = event.key();
		if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			focused = false;
			return true;
		}
		if (key == InputConstants.KEY_BACKSPACE) {
			if (!text.isEmpty()) {
				text = text.substring(0, text.length() - 1);
				onChange.accept(text);
			}
			return true;
		}
		return key != InputConstants.KEY_TAB;
	}

	public boolean charTyped(CharacterEvent event) {
		int c = event.codepoint();
		if (!Character.isBmpCodePoint(c) || Character.isISOControl(c)) {
			return focused;
		}
		if (!focused) {
			if (!Character.isLetterOrDigit(c)) {
				return false;
			}
			focused = true;
		}
		if (text.length() < MAX) {
			text = text + (char) c;
			onChange.accept(text);
		}
		return true;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
		int edge = focused ? Palette.SEAL : Palette.argb(over ? 0xE0 : 0xA0, Palette.GOLD);
		g.fill(x, y, x + w, y + h, focused ? 0xFFF6EED2 : Palette.argb(0x70, 0xF6EED2));
		Brush.frame(g, x, y, x + w, y + h, edge);
		// The brush: a short slanted stroke at the left end.
		Brush.line(g, x + 3.0F, y + h - 3.0F, x + 7.0F, y + 3.0F, 1.2F, Palette.INK_SOFT);
		Brush.dot(g, x + 3.0F, y + h - 3.0F, 1.0F, Palette.INK);
		float scale = Math.min(0.75F, (h - 3) / 9.0F);
		float tx = x + 10.0F;
		float room = w - 13.0F;
		if (text.isEmpty() && !focused) {
			Brush.fit(g, font, Component.translatable("kultivator.codex.search.hint"), tx, y + (h - 8.0F * scale) / 2.0F, room, Palette.INK_FAINT,
					scale, 0.45F, false);
			return;
		}
		boolean caret = focused && Util.getMillis() / 400L % 2L == 0L;
		String shown = text;
		while (shown.length() > 1 && font.width(shown + "_") * scale > room) {
			shown = shown.substring(1);
		}
		Brush.text(g, font, Component.literal(shown + (caret ? "_" : "")), tx, y + (h - 8.0F * scale) / 2.0F, Palette.INK, scale, false);
	}
}
