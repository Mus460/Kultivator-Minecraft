package dev.kultivator.client.duel.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The jade scroll both duel screens share (DESIGN 35.9 module U, contract C35.U6): a night backdrop, the scroll unrolling
 * between its two rods (fits 320 × 240), the theme's own controls ({@link Widget}: talismans and jade coins) pressed on
 * release, their tooltips in the slip style, and Esc back to the screen it came from (the hub's own child handling,
 * DESIGN 33 C33.N2, returns a hub child to the hub). Subclasses lay out in {@link #build} and draw in {@link #drawPaper}.
 */
abstract class DuelScrollScreen extends Screen {

	private static final int MAX_W = 304;
	private static final int MAX_H = 228;
	private static final long UNROLL_MILLIS = 220L;

	protected final @Nullable Screen parent;
	protected final List<Widget> widgets = new ArrayList<>();
	private @Nullable Widget pressed;
	private long openedAt;
	private long lastFrame;
	protected @Nullable List<Component> tooltip;

	/** The paper's inner box (after the rods and margins), set by {@link #init}. */
	protected int left;
	protected int right;
	protected int top;
	protected int bottom;
	private int x0;
	private int x1;
	private int y0;
	private int y1;

	protected DuelScrollScreen(Component title, @Nullable Screen parent) {
		super(title);
		this.parent = parent;
	}

	@Override
	protected final void init() {
		if (openedAt == 0L) {
			openedAt = Util.getMillis();
			lastFrame = openedAt;
			UiSound.open();
		}
		int w = Math.min(MAX_W, width - 12);
		int h = Math.min(MAX_H, height - 8);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		y0 = (height - h) / 2 + 4;
		y1 = y0 + h - 8;
		left = x0 + ScrollFrame.SIDE + 8;
		right = x1 - ScrollFrame.SIDE - 8;
		top = y0 + ScrollFrame.END + 4;
		bottom = y1 - ScrollFrame.END - 4;
		widgets.clear();
		pressed = null;
		build();
	}

	/** Lays the controls out inside {@link #left}..{@link #right}, {@link #top}..{@link #bottom}. */
	protected abstract void build();

	/** Draws the content on the paper (the controls are drawn after it). */
	protected abstract void drawPaper(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds);

	/** Optional: a click on the paper that is not on a control (rows of a list). True when handled. */
	protected boolean clickPaper(double mouseX, double mouseY) {
		return false;
	}

	protected <W extends Widget> W add(W widget) {
		widgets.add(widget);
		return widget;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, Palette.argb(0xB0, Palette.NIGHT), Palette.argb(0xD0, Palette.NIGHT_MIST));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		float seconds = Math.max(0.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F));
		lastFrame = now;
		tooltip = null;
		float open = Eased.out(Math.min(1.0F, (now - openedAt) / (float) UNROLL_MILLIS));
		int mid = (y0 + y1) / 2;
		int half = Math.round((y1 - y0) / 2.0F * open);
		ScrollFrame.paper(g, x0, x1, mid - half, mid + half, y0, y1);
		ScrollFrame.rod(g, x0, x1, mid - half);
		ScrollFrame.rod(g, x0, x1, mid + half);
		g.nextStratum();
		if (open < 0.95F) {
			return;
		}
		drawPaper(g, mouseX, mouseY, seconds);
		for (Widget widget : widgets) {
			widget.render(g, font, mouseX, mouseY, seconds);
			if (widget.contains(mouseX, mouseY) && !widget.tooltipLines().isEmpty()) {
				tooltip = widget.tooltipLines();
			}
		}
		// vanilla widgets (sliders) on top
		super.extractRenderState(g, mouseX, mouseY, a);
		if (tooltip != null && !tooltip.isEmpty()) {
			g.nextStratum();
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	/** A heading with a gold rule under it; returns the y below it. */
	protected float heading(GuiGraphicsExtractor g, Component text, float x, float y, float w) {
		Brush.fit(g, font, Brush.bold(text), x, y, w, Palette.SEAL_DARK, 0.8F, 0.55F, false);
		Brush.rect(g, x, y + 9.0F, x + w, y + 9.6F, Palette.argb(0xB0, Palette.GOLD));
		return y + 12.0F;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (Widget widget : widgets) {
				if (widget.contains(event.x(), event.y())) {
					widget.press();
					pressed = widget;
					return true;
				}
			}
			if (super.mouseClicked(event, doubleClick)) {
				return true;
			}
			if (clickPaper(event.x(), event.y())) {
				return true;
			}
			return false;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		Widget widget = pressed;
		if (widget != null) {
			pressed = null;
			widget.release(event.x(), event.y());
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		if (parent != null && minecraft != null) {
			minecraft.gui.setScreen(parent);
			return;
		}
		super.onClose();
	}

	@Override
	public void removed() {
		UiSound.close();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
