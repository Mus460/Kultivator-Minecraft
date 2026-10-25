package dev.kultivator.client.screen.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The single text field of the kit (DESIGN 43.7, G10; was {@code hall/InkField}, also replaces {@code InkSearchField},
 * {@code KotakCari}, Ciptaan's name field and the vanilla {@code EditBox}es): a slip of paper to write on. Click (or Tab
 * to it) takes the brush; it accepts every printable character a chat line accepts, punctuation included (R10), up to
 * {@code max}; ←/→ Home/End move the caret (Shift selects), Backspace / Delete erase, Ctrl+A / C / V / X select all, copy,
 * paste and cut; Enter writes it ({@code onSubmit}) and puts the brush down, Escape puts it down. Since 26.3 (SDL) it
 * reports its focus to {@link Ketik} every frame it is drawn, or nothing could be typed.
 *
 * <p>Search mode ({@link #cari}): a brush-mark icon, a faint hint while empty, a "×" that clears, every change reported
 * at once ({@code onChange}), {@code /} on the page starts it, Escape clears the text first, ↑↓ PgUp PgDn stay with the
 * page's list. {@link #cocok} filters entries client side.
 */
public final class InkField extends Widget {

	private final int max;
	private @Nullable Consumer<String> onSubmit;
	private @Nullable Consumer<String> onChange;
	private @Nullable Component hint;
	private boolean search;
	private String text = "";
	private int caret;
	private int anchor;
	private int scroll;
	private boolean focused;
	/** The '/' that opened the field by keyboard also arrives as a typed character: drop that one. */
	private boolean dropSlash;

	public InkField(int x, int y, int w, int h, int max, @Nullable Consumer<String> onSubmit) {
		super(x, y, Math.max(TalismanButton.MIN_W, w), Math.max(TalismanButton.MIN_H, h));
		this.max = Math.max(1, max);
		this.onSubmit = onSubmit;
	}

	/** Search mode with {@code hint} written while empty; {@code onChange} gets every change. */
	public InkField cari(Component hint, Consumer<String> onChange) {
		this.search = true;
		this.hint = hint;
		this.onChange = onChange;
		return this;
	}

	/** A faint hint written while the field is empty. */
	public InkField hint(@Nullable Component hint) {
		this.hint = hint;
		return this;
	}

	/** Called on every change of the text. */
	public InkField onChange(@Nullable Consumer<String> onChange) {
		this.onChange = onChange;
		return this;
	}

	public boolean modeCari() {
		return search;
	}

	public boolean focused() {
		return focused;
	}

	public void setFocused(boolean value) {
		if (value && !focused) {
			caret = anchor = text.length();
		}
		focused = value;
	}

	public String text() {
		return text;
	}

	public boolean kosong() {
		return text.isEmpty();
	}

	public void setText(@Nullable String value) {
		String v = value == null ? "" : value;
		text = v.length() > max ? v.substring(0, max) : v;
		caret = anchor = text.length();
	}

	/** Starts writing from the page's {@code /} key (its character event is swallowed). */
	public void mulaiDariTombol() {
		setFocused(true);
		dropSlash = true;
		UiSound.press();
	}

	/** True when {@code nama} matches the search (case-insensitive substring; empty matches all). */
	public boolean cocok(Component nama) {
		return text.isEmpty() || nama.getString().toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
	}

	/** Clears the text (reported as a change). */
	public void clear() {
		if (!text.isEmpty()) {
			text = "";
			caret = anchor = 0;
			changed();
		}
	}

	private void changed() {
		if (onChange != null) {
			onChange.accept(text);
		}
	}

	// ------------------------------------------------------------------ mouse

	private boolean onClear(double mouseX) {
		return search && !text.isEmpty() && mouseX >= x + w - 10;
	}

	@Override
	public void pressAt(double mouseX, double mouseY) {
		super.press();
		if (onClear(mouseX)) {
			return;
		}
		if (focused) {
			caret = anchor = caretAt(mouseX);
		}
	}

	@Override
	public boolean release(double mouseX, double mouseY) {
		boolean was = pressed;
		pressed = false;
		if (!was || !contains(mouseX, mouseY)) {
			return false;
		}
		if (onClear(mouseX)) {
			UiSound.press();
			clear();
			return true;
		}
		if (!focused) {
			UiSound.press();
			setFocused(true);
			caret = anchor = caretAt(mouseX);
		}
		return true;
	}

	@Override
	protected void activate() {
		// Enter / Space on the focused zone: start writing
		if (!focused) {
			UiSound.press();
			setFocused(true);
		}
	}

	private int caretAt(double mouseX) {
		Font font = Minecraft.getInstance().font;
		String shown = text.substring(Math.min(scroll, text.length()));
		int rel = (int) Math.round(mouseX - textLeft());
		return Math.min(text.length(), scroll + font.plainSubstrByWidth(shown, Math.max(0, rel)).length());
	}

	private float textLeft() {
		return x + (search ? 11.0F : 3.0F);
	}

	private float textRight() {
		return x + w - (search && !text.isEmpty() ? 11.0F : 3.0F);
	}

	// ------------------------------------------------------------------ keys

	/** Keys while writing; true when used. */
	public boolean keyPressed(KeyEvent event) {
		if (!focused) {
			return false;
		}
		int key = event.key();
		if (key == InputConstants.KEY_ESCAPE) {
			if (search && !text.isEmpty()) {
				clear();
			} else {
				focused = false;
			}
			return true;
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			focused = false;
			if (onSubmit != null) {
				onSubmit.accept(text.trim());
			}
			return true;
		}
		if (key == InputConstants.KEY_TAB) {
			return false;
		}
		if (event.isSelectAll()) {
			anchor = 0;
			caret = text.length();
			return true;
		}
		if (event.isCopy()) {
			if (caret != anchor) {
				Minecraft.getInstance().keyboardHandler.setClipboard(selected());
			}
			return true;
		}
		if (event.isCut()) {
			if (caret != anchor) {
				Minecraft.getInstance().keyboardHandler.setClipboard(selected());
				insert("");
			}
			return true;
		}
		if (event.isPaste()) {
			insert(Minecraft.getInstance().keyboardHandler.getClipboard());
			return true;
		}
		boolean shift = event.hasShiftDown();
		switch (key) {
			case InputConstants.KEY_BACKSPACE -> {
				if (caret == anchor && caret > 0) {
					anchor = caret - 1;
				}
				insert("");
				return true;
			}
			case InputConstants.KEY_DELETE -> {
				if (caret == anchor && caret < text.length()) {
					anchor = caret + 1;
				}
				insert("");
				return true;
			}
			case InputConstants.KEY_LEFT -> {
				moveTo(caret != anchor && !shift ? Math.min(caret, anchor) : caret - 1, shift);
				return true;
			}
			case InputConstants.KEY_RIGHT -> {
				moveTo(caret != anchor && !shift ? Math.max(caret, anchor) : caret + 1, shift);
				return true;
			}
			case InputConstants.KEY_HOME -> {
				moveTo(0, shift);
				return true;
			}
			case InputConstants.KEY_END -> {
				moveTo(text.length(), shift);
				return true;
			}
			default -> {
			}
		}
		// in search mode the list keys stay with the page
		return !(search && (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN || key == InputConstants.KEY_PAGEUP
				|| key == InputConstants.KEY_PAGEDOWN));
	}

	/** A typed character while writing; true when used. */
	public boolean charTyped(CharacterEvent event) {
		if (!focused) {
			return false;
		}
		int c = event.codepoint();
		if (dropSlash) {
			dropSlash = false;
			if (c == '/') {
				return true;
			}
		}
		if (event.isAllowedChatCharacter()) {
			insert(Character.toString(c));
		}
		return true;
	}

	private void moveTo(int to, boolean select) {
		caret = Math.max(0, Math.min(text.length(), to));
		if (!select) {
			anchor = caret;
		}
	}

	private String selected() {
		return text.substring(Math.min(caret, anchor), Math.max(caret, anchor));
	}

	/** Replaces the selection with {@code s} (filtered to printable characters, kept under {@code max}). */
	private void insert(String s) {
		String clean = StringUtil.filterText(s == null ? "" : s);
		int a = Math.min(caret, anchor);
		int b = Math.max(caret, anchor);
		int room = max - (text.length() - (b - a));
		if (clean.length() > room) {
			clean = clean.substring(0, Math.max(0, room));
		}
		String next = text.substring(0, a) + clean + text.substring(b);
		caret = anchor = a + clean.length();
		if (!next.equals(text)) {
			text = next;
			changed();
		}
	}

	// ------------------------------------------------------------------ drawing

	@Override
	protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
		Ketik.fokus(this, focused);
		int edge = focused ? Palette.SEAL : Palette.argb(over ? 0xE0 : 0xA0, Palette.GOLD);
		g.fill(x, y, x + w, y + h, focused ? 0xFFF6EED2 : Palette.argb(0x90, 0xF6EED2));
		g.fill(x, y, x + w, y + 1, 0x40000000);
		Brush.frame(g, x, y, x + w, y + h, edge);
		float ty = y + (h - 8.0F) / 2.0F;
		if (search) {
			Brush.line(g, x + 3.0F, y + h - 3.0F, x + 7.0F, y + 3.0F, 1.2F, Palette.INK_SOFT);
			Brush.dot(g, x + 3.0F, y + h - 3.0F, 1.0F, Palette.INK);
			if (!text.isEmpty()) {
				Brush.text(g, font, Component.literal("×"), x + w - 8.0F, ty, over ? Palette.SEAL : Palette.INK_SOFT, 1.0F, false);
			}
		}
		float left = textLeft();
		float room = textRight() - left;
		if (text.isEmpty() && !focused) {
			if (hint != null) {
				Brush.fit(g, font, hint, left, ty, room, Palette.INK_FAINT, 1.0F, 1.0F, false);
			}
			return;
		}
		// keep the caret in view
		scroll = Math.min(scroll, caret);
		while (scroll < caret && font.width(text.substring(scroll, caret)) > room - 2) {
			scroll++;
		}
		String shown = font.plainSubstrByWidth(text.substring(scroll), (int) room);
		int s0 = Math.max(0, Math.min(caret, anchor) - scroll);
		int s1 = Math.min(shown.length(), Math.max(caret, anchor) - scroll);
		if (focused && s1 > s0) {
			float sx0 = left + font.width(shown.substring(0, s0));
			float sx1 = left + font.width(shown.substring(0, s1));
			Brush.rect(g, sx0, ty - 1.0F, sx1, ty + 9.0F, Palette.argb(0x70, Palette.JADE_LIGHT));
		}
		Brush.text(g, font, Component.literal(shown), left, ty, Palette.INK, 1.0F, false);
		if (focused && Util.getMillis() / 450L % 2L == 0L) {
			int at = Math.max(0, Math.min(shown.length(), caret - scroll));
			float cx = left + font.width(shown.substring(0, at));
			g.fill(Math.round(cx), Math.round(ty - 1.0F), Math.round(cx) + 1, Math.round(ty + 9.0F), Palette.INK);
		}
	}
}
