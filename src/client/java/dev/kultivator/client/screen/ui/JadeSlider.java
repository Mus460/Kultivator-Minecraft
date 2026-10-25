package dev.kultivator.client.screen.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * A jade slider with discrete steps (Phase 10b, DESIGN 29.2.2), rewritten as a kit {@link Widget} (DESIGN 43.7) so a page
 * puts it in its widget list and Tab ring ({@link Lukis#widget}; ←/→ step it while focused, the mouse drags the handle).
 * The label and the value (1.0) sit above a jade groove with a bead per step and gold filling up to a small jade seal
 * handle. Steps above the allowed maximum ({@link #limit}) are drawn in seal red with a red seal at the limit, and the
 * handle never passes it. The value is committed once, when the mouse is released or a key moves it ({@code commit} gets
 * the step index), so dragging sends nothing until the player lets go.
 *
 * <p>Until the old screens that hold it move into pages (43-M1), it also behaves as a vanilla screen child
 * ({@code addRenderableWidget}: click, drag, keys, narration, {@link #active}); P1 drops that bridge.
 */
public class JadeSlider extends Widget implements Renderable, GuiEventListener, NarratableEntry {

	private final float[] steps;
	private final Function<Float, Component> text;
	private final IntConsumer commit;
	private IntSupplier maxIndex;
	private int index;
	private int committed;
	private boolean held;
	private boolean focused;
	private boolean hovered;
	private long lastFrame = -1L;
	/** Vanilla-compatible switch (old screens set it); a page uses {@link #enabledWhen} as well. */
	public boolean active = true;

	/**
	 * @param steps  the step values in order (at least one)
	 * @param index  the starting step
	 * @param text   the label with the value of a step (drawn above the groove)
	 * @param commit called with the new step index when the player settles on it
	 */
	public JadeSlider(int x, int y, int width, int height, float[] steps, int index, Function<Float, Component> text, IntConsumer commit) {
		super(x, y, Math.max(TalismanButton.MIN_W, width), Math.max(16, height));
		this.steps = steps.length == 0 ? new float[] {0.0F} : steps;
		this.text = text;
		this.commit = commit;
		this.maxIndex = () -> this.steps.length - 1;
		this.index = clampIndex(index);
		this.committed = this.index;
		enabledWhen(() -> active);
		tooltip(() -> java.util.List.of(message()));
	}

	/** The highest step the player may choose now (realm limit); steps above it are shown red and cannot be reached. */
	public JadeSlider limit(IntSupplier maxIndex) {
		this.maxIndex = maxIndex;
		return this;
	}

	public int index() {
		return index;
	}

	public float stepValue() {
		return steps[index];
	}

	/** True while the mouse holds the handle. */
	public boolean held() {
		return held;
	}

	/** True while the mouse is over the slider (last frame). */
	public boolean isHovered() {
		return hovered;
	}

	/** The label with the current value. */
	public Component message() {
		return text.apply(steps[index]);
	}

	/** Moves to {@code newIndex} without committing (the server's sanitised value arrived); ignored while held. */
	public void sync(int newIndex) {
		if (held) {
			return;
		}
		index = clampIndex(newIndex);
		committed = index;
	}

	private int clampIndex(int i) {
		int max = Math.max(0, Math.min(steps.length - 1, maxIndex == null ? steps.length - 1 : maxIndex.getAsInt()));
		return Math.max(0, Math.min(max, i));
	}

	private float fraction(int i) {
		return steps.length <= 1 ? 0.0F : i / (float) (steps.length - 1);
	}

	private float left() {
		return x + 4.0F;
	}

	private float right() {
		return x + w - 4.0F;
	}

	/** Moves the handle to the step nearest the mouse x (no commit). */
	private void follow(double mouseX) {
		float f = (float) ((mouseX - left()) / Math.max(1.0F, right() - left()));
		int i = clampIndex(Math.round(Math.max(0.0F, Math.min(1.0F, f)) * (steps.length - 1)));
		if (i != index) {
			index = i;
			UiSound.hover();
		}
	}

	/** One step left (-1) or right (+1), committed at once. */
	public void step(int arah) {
		if (!isEnabled()) {
			UiSound.denied();
			return;
		}
		int next = clampIndex(index + arah);
		if (next != index) {
			index = next;
			UiSound.hover();
			settle();
		}
	}

	/** Commits the step once it differs from the last committed one. */
	private void settle() {
		if (index != committed) {
			committed = index;
			commit.accept(index);
		}
	}

	// ------------------------------------------------------------------ Widget (page) input

	@Override
	public void pressAt(double mouseX, double mouseY) {
		super.press();
		held = isEnabled();
		if (held) {
			follow(mouseX);
		}
	}

	@Override
	public boolean dragTo(double mouseX, double mouseY) {
		if (!held) {
			return false;
		}
		follow(mouseX);
		return true;
	}

	@Override
	public boolean release(double mouseX, double mouseY) {
		pressed = false;
		if (!held) {
			return false;
		}
		held = false;
		settle();
		return true;
	}

	@Override
	protected void activate() {
		// Enter / Space on a focused slider: nothing to run (←/→ move it)
	}

	@Override
	public @Nullable IntConsumer geser() {
		return this::step;
	}

	// ------------------------------------------------------------------ drawing

	@Override
	protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
		hovered = over;
		boolean on = isEnabled();
		boolean lit = on && (over || focused || held);
		// the label and the value
		Brush.fit(g, font, message(), x + 1.0F, y, w - 2.0F, on ? Palette.INK : Palette.INK_FAINT, 1.0F, 1.0F, false);
		// the groove
		float left = left();
		float right = right();
		float gy = y + h - 5.0F;
		Brush.rect(g, left - 1.0F, gy - 2.0F, right + 1.0F, gy + 2.0F, Palette.argb(0x60, Palette.JADE_DARK));
		Brush.rect(g, left - 0.5F, gy - 1.5F, right + 0.5F, gy + 1.5F, on ? Palette.JADE_PALE : Palette.PAPER_SHADE);
		float handle = left + fraction(index) * (right - left);
		if (on) {
			Brush.rect(g, left - 0.5F, gy - 1.0F, handle, gy + 1.0F, lit ? Palette.GOLD_LIGHT : Palette.GOLD);
		}
		// a bead per step; red above the limit, with a red seal at the limit itself
		int max = clampIndex(steps.length - 1);
		for (int i = 0; i < steps.length; i++) {
			float bx = left + fraction(i) * (right - left);
			int color = i > max ? Palette.argb(0xB0, Palette.DANGER) : i <= index && on ? Palette.GOLD_DARK : Palette.argb(0x90, Palette.JADE_DARK);
			Brush.dot(g, bx, gy, 0.8F, color);
		}
		if (max < steps.length - 1 && on) {
			float sx = left + fraction(max) * (right - left);
			Brush.rect(g, sx + 1.5F, gy - 4.0F, sx + 4.5F, gy - 1.0F, Palette.SEAL);
			Brush.rect(g, sx + 2.3F, gy - 3.2F, sx + 3.7F, gy - 1.8F, Palette.argb(0xC0, Palette.TALISMAN));
		}
		// the handle: a small jade seal with a red stamp
		if (lit) {
			Brush.halo(g, handle, gy, 3.5F, 2.0F, 0x9CFFD0, 0.8F);
		}
		Brush.rect(g, handle - 2.5F, gy - 3.0F, handle + 3.5F, gy + 5.0F, 0x50000000);
		Brush.rect(g, handle - 3.0F, gy - 4.0F, handle + 3.0F, gy + 4.0F, on ? Palette.JADE_DARK : Palette.LOCKED);
		Brush.rect(g, handle - 2.4F, gy - 3.4F, handle + 2.4F, gy + 3.4F, on ? (lit ? Palette.JADE_LIGHT : Palette.JADE) : Palette.LOCKED_PAPER);
		Brush.rect(g, handle - 1.0F, gy - 1.0F, handle + 1.0F, gy + 1.0F, on ? Palette.SEAL : Palette.INK_FAINT);
	}

	// ------------------------------------------------------------------ vanilla screen child (bridge until 43-M1)

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		float seconds = lastFrame < 0L ? 0.0F : Math.min(0.25F, (now - lastFrame) / 1000.0F);
		lastFrame = now;
		render(g, Minecraft.getInstance().font, mouseX, mouseY, seconds);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !contains(event.x(), event.y())) {
			return false;
		}
		pressAt(event.x(), event.y());
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return held && release(event.x(), event.y());
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return dragTo(event.x(), event.y());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (focused && (event.isLeft() || event.isRight())) {
			step(event.isLeft() ? -1 : 1);
			return true;
		}
		return false;
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return contains(mouseX, mouseY);
	}

	@Override
	public void setFocused(boolean focused) {
		this.focused = focused;
	}

	@Override
	public boolean isFocused() {
		return focused;
	}

	@Override
	public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent navigationEvent) {
		return isEnabled() && !focused ? ComponentPath.leaf(this) : null;
	}

	@Override
	public ScreenRectangle getRectangle() {
		return new ScreenRectangle(x, y, w, h);
	}

	@Override
	public NarrationPriority narrationPriority() {
		return focused ? NarrationPriority.FOCUSED : hovered ? NarrationPriority.HOVERED : NarrationPriority.NONE;
	}

	@Override
	public void updateNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, message());
	}
}
