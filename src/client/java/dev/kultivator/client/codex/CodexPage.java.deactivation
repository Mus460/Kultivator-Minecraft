package dev.kultivator.client.codex;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.CultivationData;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One tablet of the Kitab Teknik scroll: laid out into the scroll's content box, owning its controls, drawing its own
 * pictures and handling the mouse and keys inside the box. Data is read live from {@link ClientCultivation}.
 */
abstract class CodexPage {

	protected final CodexScreen screen;
	protected final Font font;
	protected int x;
	protected int y;
	protected int w;
	protected int h;
	protected final List<Widget> widgets = new ArrayList<>();
	private @Nullable Widget pressed;

	CodexPage(CodexScreen screen) {
		this.screen = screen;
		this.font = Minecraft.getInstance().font;
	}

	abstract Component title();

	abstract Component shortName();

	abstract int icon();

	/** The Panduan › Ensiklopedia topic the header's "?" seal opens (DESIGN 33.6 R1). */
	String helpTopic() {
		return "kitab_teknik";
	}

	/** The blood-red tablet and paper (Kehancuran only). */
	boolean red() {
		return false;
	}

	final void layout(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		widgets.clear();
		pressed = null;
		build();
	}

	protected abstract void build();

	/** A new snapshot of the player's data arrived. */
	void onData() {
	}

	final void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		drawBehind(g, mouseX, mouseY, seconds);
		for (Widget widget : widgets) {
			if (!visible(widget)) {
				continue;
			}
			widget.render(g, font, mouseX, mouseY, seconds);
			if (widget.contains(mouseX, mouseY)) {
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

	boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (int i = widgets.size() - 1; i >= 0; i--) {
			Widget widget = widgets.get(i);
			if (visible(widget) && widget.contains(mouseX, mouseY)) {
				if (button == InputConstants.MOUSE_BUTTON_RIGHT && widget instanceof CodexWidgets.Cycle cycle) {
					cycle.back();
					return true;
				}
				if (button == InputConstants.MOUSE_BUTTON_LEFT) {
					widget.press();
					pressed = widget;
					return true;
				}
			}
		}
		return clickedElsewhere(mouseX, mouseY, button);
	}

	protected boolean clickedElsewhere(double mouseX, double mouseY, int button) {
		return false;
	}

	boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (pressed == null) {
			return releasedElsewhere(mouseX, mouseY, button);
		}
		Widget widget = pressed;
		pressed = null;
		widget.release(mouseX, mouseY);
		return true;
	}

	protected boolean releasedElsewhere(double mouseX, double mouseY, int button) {
		return false;
	}

	boolean mouseDragged(double mouseX, double mouseY, int button) {
		return false;
	}

	boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		return false;
	}

	boolean keyPressed(KeyEvent event) {
		return false;
	}

	boolean charTyped(CharacterEvent event) {
		return false;
	}

	/** Escape pressed: true when the page used it (closing an overlay) instead of the screen closing. */
	boolean escape() {
		return false;
	}

	protected <T extends Widget> T add(T widget) {
		widgets.add(widget);
		return widget;
	}

	protected boolean inside(double mouseX, double mouseY) {
		return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
	}

	protected static CultivationData data() {
		return ClientCultivation.data();
	}

	protected static void send(int action, int arg, String text) {
		Keys.send(new Payloads.Action(action, arg, text));
	}

	/** A heading in seal red with a little stamp before it and a gold rule under it. */
	protected void heading(GuiGraphicsExtractor g, Component text, float hx, float hy, float width, float scale) {
		Brush.rect(g, hx, hy + 1.0F, hx + 4.0F, hy + 5.0F, Palette.SEAL);
		Brush.fit(g, font, Brush.bold(text), hx + 6.0F, hy, width - 6.0F, Palette.SEAL_DARK, scale, scale * 0.6F, false);
		Brush.rect(g, hx, hy + 9.0F * scale + 1.0F, hx + width, hy + 9.0F * scale + 1.6F, Palette.argb(0xB0, Palette.GOLD));
	}
}
