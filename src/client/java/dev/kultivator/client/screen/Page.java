package dev.kultivator.client.screen;

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
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One page of the cultivation scroll (since DESIGN 33 the hub: a section shows one or more pages). It is laid out into the content box of the scroll, owns its controls, draws its
 * own pictures (dantian, pentagram, Bagua...) and handles the mouse inside the box. Data is always read live from
 * {@link ClientCultivation}; {@link #onData()} is called when a new snapshot arrives so cached text can be rebuilt.
 */
abstract class Page {

	protected final CultivationScreen screen;
	protected final Font font;
	protected int x;
	protected int y;
	protected int w;
	protected int h;
	protected final List<Widget> widgets = new ArrayList<>();
	private @Nullable Widget pressed;

	Page(CultivationScreen screen) {
		this.screen = screen;
		this.font = Minecraft.getInstance().font;
	}

	/** The tab's full name (scroll header and tooltip). */
	abstract Component title();

	/** The tab's short name written on its jade tablet. */
	abstract Component shortName();

	/**
	 * The Ensiklopedia topic the hub's "?" seal opens for this page (a {@code kultivator.screen.help.<topic>} key; DESIGN
	 * 33.3, C33.N3). Pages override it.
	 */
	String helpTopic() {
		return "jalan";
	}

	/**
	 * The page's own sub-pages, shown as the hub's sub-coins when the page is alone in its section (Pengaturan's groups);
	 * empty for none. The hub calls {@link #showSub(int)} with the chosen index.
	 */
	List<Component> subNames() {
		return List.of();
	}

	/** Shows sub-page {@code index} of {@link #subNames()} (the hub clamps it). */
	void showSub(int index) {
	}

	final void layout(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		widgets.clear();
		pressed = null;
		build();
		onData();
	}

	/** Creates the controls for the current box (and data, for controls that only exist in some states). */
	protected abstract void build();

	/** A new snapshot of the player's cultivation arrived. */
	void onData() {
	}

	/** True when the controls depend on data that changed so much that {@link #build()} must run again. */
	boolean needsRebuild() {
		return false;
	}

	final void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		drawBehind(g, mouseX, mouseY, seconds);
		for (Widget widget : widgets) {
			if (!visible(widget)) {
				continue;
			}
			widget.render(g, font, mouseX, mouseY, seconds);
			if (widget.contains(mouseX, mouseY) && hoverable(mouseX, mouseY)) {
				List<Component> tip = widget.tooltipLines();
				if (!tip.isEmpty()) {
					screen.showTooltip(tip);
				}
			}
		}
		drawAbove(g, mouseX, mouseY, seconds);
	}

	/** Pictures under the controls. */
	protected abstract void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds);

	/** Pictures over the controls. */
	protected void drawAbove(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	/** Whether a control is drawn and clickable right now (scrolled-out cards are not). */
	protected boolean visible(Widget widget) {
		return true;
	}

	boolean mouseClicked(double mouseX, double mouseY, int button) {
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
		return clickedElsewhere(mouseX, mouseY);
	}

	/** A left click that hit none of the controls. */
	protected boolean clickedElsewhere(double mouseX, double mouseY) {
		return false;
	}

	boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (pressed == null) {
			return false;
		}
		Widget widget = pressed;
		pressed = null;
		widget.release(mouseX, mouseY);
		return true;
	}

	boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		return false;
	}

	protected <T extends Widget> T add(T widget) {
		widgets.add(widget);
		return widget;
	}

	/** Where control tooltips may show (the whole box unless part of it is clipped). */
	protected boolean hoverable(double mouseX, double mouseY) {
		return inside(mouseX, mouseY);
	}

	protected boolean inside(double mouseX, double mouseY) {
		return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
	}

	protected static CultivationData data() {
		return ClientCultivation.data();
	}

	protected static void send(int action) {
		Keys.send(Payloads.Action.of(action));
	}

	protected static void send(int action, int arg, String text) {
		Keys.send(new Payloads.Action(action, arg, text));
	}

	/** A heading drawn in seal-red with a little stamp before it and a gold rule under it. */
	protected void heading(GuiGraphicsExtractor g, Component text, float hx, float hy, float width, float scale) {
		Brush.rect(g, hx, hy + 1.0F, hx + 4.0F, hy + 5.0F, Palette.SEAL);
		Brush.fit(g, font, Brush.bold(text), hx + 6.0F, hy, width - 6.0F,
				Palette.SEAL_DARK, scale, scale * 0.6F, false);
		Brush.rect(g, hx, hy + 9.0F * scale + 1.0F, hx + width, hy + 9.0F * scale + 1.6F,
				Palette.argb(0xB0, Palette.GOLD));
	}
}
