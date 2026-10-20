package dev.kultivator.client.screen.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A jade slider with discrete steps (Phase 10b, DESIGN 29.2.2): the vanilla {@link AbstractSliderButton} behaviour
 * (click, drag, keyboard focus, narration) drawn in the theme of the jade scroll: the label and the value above a
 * jade groove, a bead for every step, gold filling up to a small jade seal handle. Steps above the allowed maximum
 * ({@link #limit}) are drawn in seal red with a red seal at the limit, and the handle never passes it. The value is
 * committed once, when the mouse is released or a key moves it ({@code commit} gets the step index), so dragging sends
 * nothing until the player lets go.
 */
public class JadeSlider extends AbstractSliderButton {

	private final float[] steps;
	private final Function<Float, Component> text;
	private final IntConsumer commit;
	private IntSupplier maxIndex;
	private int index;
	private int committed;
	private boolean held;

	/**
	 * @param steps the step values in order (at least one)
	 * @param index the starting step
	 * @param text  the label with the value of a step (drawn above the groove)
	 * @param commit called with the new step index when the player settles on it
	 */
	public JadeSlider(int x, int y, int width, int height, float[] steps, int index, Function<Float, Component> text, IntConsumer commit) {
		super(x, y, width, height, Component.empty(), 0.0D);
		this.steps = steps.length == 0 ? new float[]{0.0F} : steps;
		this.text = text;
		this.commit = commit;
		this.maxIndex = () -> this.steps.length - 1;
		this.index = clampIndex(index);
		this.committed = this.index;
		this.value = fraction(this.index);
		updateMessage();
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

	/** Moves to {@code newIndex} without committing (the server's sanitised value arrived); ignored while held. */
	public void sync(int newIndex) {
		if (held) {
			return;
		}
		int clamped = clampIndex(newIndex);
		if (clamped != index) {
			index = clamped;
			value = fraction(index);
			updateMessage();
		}
		committed = index;
	}

	private int clampIndex(int i) {
		int max = Math.max(0, Math.min(steps.length - 1, maxIndex == null ? steps.length - 1 : maxIndex.getAsInt()));
		return Math.max(0, Math.min(max, i));
	}

	private double fraction(int i) {
		return steps.length <= 1 ? 0.0D : i / (double) (steps.length - 1);
	}

	@Override
	protected void updateMessage() {
		setMessage(text.apply(steps[index]));
	}

	@Override
	protected void applyValue() {
		int i = clampIndex((int) Math.round(value * (steps.length - 1)));
		if (i != index) {
			index = i;
			UiSound.hover();
		}
		// snap the handle to the step (and keep it under the limit)
		value = fraction(index);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		held = active;
		super.onClick(event, doubleClick);
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		super.onRelease(event);
		held = false;
		settle();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (active && canChangeValue && (event.isLeft() || event.isRight())) {
			int next = clampIndex(index + (event.isLeft() ? -1 : 1));
			if (next != index) {
				index = next;
				value = fraction(index);
				updateMessage();
				UiSound.hover();
				settle();
			}
			return true;
		}
		return super.keyPressed(event);
	}

	/** Commits the step once it differs from the last committed one. */
	private void settle() {
		if (index != committed) {
			committed = index;
			commit.accept(index);
		}
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Font font = Minecraft.getInstance().font;
		int x0 = getX();
		int y0 = getY();
		int w = getWidth();
		int h = getHeight();
		boolean on = active;
		boolean lit = on && (isHovered() || isFocused() || held);
		// the label and the value
		Brush.fit(g, font, getMessage(), x0 + 1.0F, y0, w - 2.0F, on ? Palette.INK : Palette.INK_FAINT, 0.75F, 0.55F, false);
		// the groove
		float left = x0 + 4.0F;
		float right = x0 + w - 4.0F;
		float gy = y0 + h - 5.0F;
		Brush.rect(g, left - 1.0F, gy - 2.0F, right + 1.0F, gy + 2.0F, Palette.argb(0x60, Palette.JADE_DARK));
		Brush.rect(g, left - 0.5F, gy - 1.5F, right + 0.5F, gy + 1.5F, on ? Palette.JADE_PALE : Palette.PAPER_SHADE);
		float handle = left + (float) value * (right - left);
		if (on) {
			Brush.rect(g, left - 0.5F, gy - 1.0F, handle, gy + 1.0F, lit ? Palette.GOLD_LIGHT : Palette.GOLD);
		}
		// a bead per step; red above the limit, with a red seal at the limit itself
		int max = clampIndex(steps.length - 1);
		for (int i = 0; i < steps.length; i++) {
			float bx = left + (float) fraction(i) * (right - left);
			int color = i > max ? Palette.argb(0xB0, Palette.DANGER) : i <= index && on ? Palette.GOLD_DARK : Palette.argb(0x90, Palette.JADE_DARK);
			Brush.dot(g, bx, gy, 0.8F, color);
		}
		if (max < steps.length - 1 && on) {
			float sx = left + (float) fraction(max) * (right - left);
			Brush.rect(g, sx + 1.5F, gy - 4.0F, sx + 4.5F, gy - 1.0F, Palette.SEAL);
			Brush.rect(g, sx + 2.3F, gy - 3.2F, sx + 3.7F, gy - 1.8F, Palette.argb(0xC0, Palette.TALISMAN));
		}
		// the handle: a small jade seal with a red stamp
		float hx0 = handle - 3.0F;
		float hx1 = handle + 3.0F;
		float hy0 = gy - 4.0F;
		float hy1 = gy + 4.0F;
		if (lit) {
			Brush.halo(g, handle, gy, 3.5F, 2.0F, 0x9CFFD0, 0.8F);
		}
		Brush.rect(g, hx0 + 0.5F, hy0 + 1.0F, hx1 + 0.5F, hy1 + 1.0F, 0x50000000);
		Brush.rect(g, hx0, hy0, hx1, hy1, on ? Palette.JADE_DARK : Palette.LOCKED);
		Brush.rect(g, hx0 + 0.6F, hy0 + 0.6F, hx1 - 0.6F, hy1 - 0.6F, on ? (lit ? Palette.JADE_LIGHT : Palette.JADE) : Palette.LOCKED_PAPER);
		Brush.rect(g, handle - 1.0F, gy - 1.0F, handle + 1.0F, gy + 1.0F, on ? Palette.SEAL : Palette.INK_FAINT);
		handleCursor(g);
	}
}
