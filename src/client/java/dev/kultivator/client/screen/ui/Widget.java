package dev.kultivator.client.screen.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Base of the theme's own controls (not vanilla buttons). A control reads its state live through suppliers, so the
 * screen only rebuilds controls when the layout changes. It acts when the mouse is released over it, and shows a
 * 1 px press offset while held.
 */
public abstract class Widget {

	public int x;
	public int y;
	public int w;
	public int h;
	protected final Eased hover = new Eased(14.0F);
	protected boolean pressed;
	protected @Nullable Runnable action;
	protected BooleanSupplier enabled = () -> true;
	protected @Nullable Supplier<List<Component>> tooltip;

	protected Widget(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	public Widget onPress(Runnable action) {
		this.action = action;
		return this;
	}

	public Widget enabledWhen(BooleanSupplier enabled) {
		this.enabled = enabled;
		return this;
	}

	public Widget tooltip(Supplier<List<Component>> tooltip) {
		this.tooltip = tooltip;
		return this;
	}

	public boolean isEnabled() {
		return enabled.getAsBoolean();
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
	}

	/** Tooltip lines right now (empty when none). */
	public List<Component> tooltipLines() {
		return tooltip == null ? List.of() : tooltip.get();
	}

	/** Draws the control; {@code seconds} is the frame time for its animations. */
	public final void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, float seconds) {
		boolean over = contains(mouseX, mouseY);
		hover.to(over ? 1.0F : 0.0F, seconds);
		draw(g, font, over, seconds);
	}

	protected abstract void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds);

	/** Mouse button went down on the control. */
	public void press() {
		pressed = true;
	}

	/** Mouse button released: runs the action when still over the control. True when it acted. */
	public boolean release(double mouseX, double mouseY) {
		boolean was = pressed;
		pressed = false;
		if (!was || !contains(mouseX, mouseY)) {
			return false;
		}
		if (!isEnabled()) {
			UiSound.denied();
			return false;
		}
		activate();
		return true;
	}

	/** What a completed click does (the default runs the action with a chime). */
	protected void activate() {
		UiSound.press();
		if (action != null) {
			action.run();
		}
	}

	/** 1 while the mouse is held on the control (for the press offset). */
	protected int pressOffset(boolean over) {
		return pressed && over && isEnabled() ? 1 : 0;
	}
}
