package dev.kultivator.client.hall;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One page (tablet) of the Sect Hall scroll, like a {@code DiscipleTab}: laid out into the scroll's content box, it owns
 * its controls (theme widgets that read {@link #info()} live), draws its pictures and handles the mouse inside the box.
 * Public so the pages of modules B (Tugas, Misi, Arena) and D (Segel Merah) extend it.
 */
public abstract class HallPage {

	protected final HallScreen screen;
	protected final Font font;
	protected int x;
	protected int y;
	protected int w;
	protected int h;
	protected final List<Widget> widgets = new ArrayList<>();
	private @Nullable Widget pressed;

	protected HallPage(HallScreen screen) {
		this.screen = screen;
		this.font = Minecraft.getInstance().font;
	}

	/** The page's full name (header and tooltip). */
	public abstract Component title();

	/** The short name written on its jade tablet. */
	public abstract Component shortName();

	/** The tablet's icon ({@code Glyphs.*}). */
	public int icon() {
		return Glyphs.HALL;
	}

	/** False hides the tablet (the Segel Merah page only exists while the server allows Mode Kehancuran). */
	public boolean shown() {
		return true;
	}

	public final void layout(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		widgets.clear();
		pressed = null;
		build();
		onInfo();
	}

	/** Creates the controls for the current box. */
	protected abstract void build();

	/** New hall details arrived. */
	public void onInfo() {
	}

	/** True when the new details changed what controls exist, so {@link #build()} must run again. */
	public boolean needsRebuild() {
		return false;
	}

	public final void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		drawBehind(g, mouseX, mouseY, seconds);
		for (Widget widget : widgets) {
			if (!visible(widget)) {
				continue;
			}
			int[] clip = clipFor(widget);
			if (clip != null) {
				g.enableScissor(clip[0], clip[1], clip[2], clip[3]);
			}
			widget.render(g, font, mouseX, mouseY, seconds);
			if (clip != null) {
				g.disableScissor();
			}
			if (widget.contains(mouseX, mouseY) && hoverable(mouseX, mouseY)) {
				List<Component> tip = widget.tooltipLines();
				if (!tip.isEmpty()) {
					screen.showTooltip(tip);
				}
			}
		}
		drawAbove(g, mouseX, mouseY, seconds);
	}

	protected abstract void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds);

	protected void drawAbove(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	protected boolean visible(Widget widget) {
		return true;
	}

	/** A scissor box {x0, y0, x1, y1} the control is drawn inside (scrolling lists), or null for none. */
	protected int[] clipFor(Widget widget) {
		return null;
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		for (int i = widgets.size() - 1; i >= 0; i--) {
			Widget widget = widgets.get(i);
			if (visible(widget) && widget.contains(mouseX, mouseY)) {
				widget.press();
				pressed = widget;
				return true;
			}
		}
		return false;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (pressed == null) {
			return false;
		}
		Widget widget = pressed;
		pressed = null;
		widget.release(mouseX, mouseY);
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		return false;
	}

	/** A key press while this page is shown; true when the page used it. */
	public boolean keyPressed(KeyEvent event) {
		return false;
	}

	/** A typed character while this page is shown; true when the page used it. */
	public boolean charTyped(CharacterEvent event) {
		return false;
	}

	/** True while a text field of this page has the keyboard (number keys then type instead of switching pages). */
	public boolean capturesKeys() {
		return false;
	}

	protected <T extends Widget> T add(T widget) {
		widgets.add(widget);
		return widget;
	}

	protected boolean hoverable(double mouseX, double mouseY) {
		return inside(mouseX, mouseY);
	}

	protected boolean inside(double mouseX, double mouseY) {
		return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
	}

	/** The last {@code HallInfo} (never null). */
	protected CompoundTag info() {
		return screen.info();
	}

	/** Sends an action ({@code Actions.HALL}, {@code SECT_EVENT}, {@code KEHANCURAN}...) with its sub-op and text. */
	protected void send(int action, int arg, String text) {
		Keys.send(new Payloads.Action(action, arg, text));
	}

	/** A heading in seal red with a little stamp before it and a gold rule under it. */
	protected void heading(GuiGraphicsExtractor g, Component text, float hx, float hy, float width, float scale) {
		Brush.rect(g, hx, hy + 1.0F, hx + 4.0F, hy + 5.0F, Palette.SEAL);
		Brush.fit(g, font, Brush.bold(text), hx + 6.0F, hy, width - 6.0F, Palette.SEAL_DARK, scale, scale * 0.6F, false);
		Brush.rect(g, hx, hy + 9.0F * scale + 1.0F, hx + width, hy + 9.0F * scale + 1.6F, Palette.argb(0xB0, Palette.GOLD));
	}

	/** A faint parchment panel with a thin gold frame (detail boxes). */
	protected static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		g.fill(x0, y0, x1, y1, Palette.argb(0x38, Palette.PAPER_DEEP));
		Brush.frame(g, x0, y0, x1, y1, Palette.argb(0xC0, Palette.GOLD));
		g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, 0x30FFFFFF);
	}

	/** The placeholder of a page whose module has not filled it yet: its heading and a "being prepared" line. */
	protected void placeholder(GuiGraphicsExtractor g) {
		heading(g, title(), x + 2.0F, y, w - 4.0F, 0.8F);
		Brush.wrap(g, font, Component.translatable("kultivator.spine6.placeholder"), x + 4.0F, y + 16.0F, w - 8.0F, Palette.INK_SOFT, 0.8F, false);
	}
}
