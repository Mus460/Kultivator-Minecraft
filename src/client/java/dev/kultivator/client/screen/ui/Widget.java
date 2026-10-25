package dev.kultivator.client.screen.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
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
	/** Why the control is disabled right now (tooltip line and footer message, DESIGN 43.7). */
	protected @Nullable Supplier<Component> alasan;
	/** A right click (cycle backwards, DESIGN 43.7); null = right clicks do nothing. */
	protected @Nullable Runnable kanan;

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

	/** The reason shown while the control is disabled (tooltip and the Gulungan footer when it is clicked). */
	public Widget alasanWhen(Supplier<Component> alasan) {
		this.alasan = alasan;
		return this;
	}

	/** What a right click does (null: nothing). */
	public Widget onRightPress(@Nullable Runnable kanan) {
		this.kanan = kanan;
		return this;
	}

	/** The disabled reason right now (null while enabled or when none is given). */
	public @Nullable Component alasan() {
		return alasan == null || isEnabled() ? null : alasan.get();
	}

	/** The right-click action (null: none). */
	public @Nullable Runnable kanan() {
		return kanan;
	}

	public boolean isEnabled() {
		return enabled.getAsBoolean();
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
	}

	/** Tooltip lines right now (empty when none). */
	public List<Component> tooltipLines() {
		List<Component> lines = tooltip == null ? List.of() : tooltip.get();
		Component why = alasan();
		if (why == null) {
			return lines;
		}
		List<Component> all = new java.util.ArrayList<>(lines);
		all.add(why.copy().withColor(0xFFFF8A7A));
		return all;
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

	/** Mouse button went down at ({@code x}, {@code y}) on the control (sliders read the position; the default presses). */
	public void pressAt(double x, double y) {
		press();
	}

	/** The mouse moved while the press started here; true when the control used it (a slider handle). */
	public boolean dragTo(double x, double y) {
		return false;
	}

	/** What ←/→ do while the control has focus (null: nothing). */
	public @Nullable IntConsumer geser() {
		return null;
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
