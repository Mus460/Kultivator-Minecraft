package dev.kultivator.client.screen.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A talisman: a strip of paper-yellow paper with a red seal stamp at its left end and the label in ink. The seal glows
 * under the mouse, the paper sinks 1 px while pressed, an "urgent" talisman's seal pulses (ready to act or waiting for
 * a confirming click) and a "stamped" one (the chosen option of a stack) slides right and carries a round red stamp.
 */
public class TalismanButton extends Widget {

	private final Supplier<Component> label;
	private BooleanSupplier stamped = () -> false;
	private BooleanSupplier urgent = () -> false;

	public TalismanButton(int x, int y, int w, int h, Supplier<Component> label) {
		super(x, y, w, h);
		this.label = label;
	}

	public TalismanButton stampedWhen(BooleanSupplier stamped) {
		this.stamped = stamped;
		return this;
	}

	public TalismanButton urgentWhen(BooleanSupplier urgent) {
		this.urgent = urgent;
		return this;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
		boolean on = isEnabled();
		boolean chosen = stamped.getAsBoolean();
		float glow = on ? hover.get() : 0.0F;
		float pulse = on && urgent.getAsBoolean() ? 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 170.0D) : 0.0F;
		int press = pressOffset(over);
		int bx = x + press + (chosen ? 3 : 0);
		int by = y + press;
		int r = bx + w - (chosen ? 3 : 0);
		int b = by + h;

		if (press == 0) {
			g.fill(bx + 1, by + 2, r + 1, b + 1, 0x40000000);
		}
		int top = on ? Palette.lighten(Palette.TALISMAN, 0.12F * glow) : Palette.grey(Palette.TALISMAN, 0.7F);
		int bottom = on ? Palette.TALISMAN_LOW : Palette.grey(Palette.TALISMAN_LOW, 0.7F);
		g.fillGradient(bx, by, r, b, top, bottom);
		// Paper fibres and the edge of the strip.
		g.fill(bx + 2, by + h / 3, r - 2, by + h / 3 + 1, 0x10000000);
		g.fill(bx + 2, by + 2 * h / 3, r - 2, by + 2 * h / 3 + 1, 0x0C000000);
		Brush.frame(g, bx, by, r, b, chosen ? Palette.GOLD : on ? Palette.TALISMAN_EDGE : Palette.grey(Palette.TALISMAN_EDGE, 0.8F));
		// Two notches cut into the right end, like a torn talisman.
		g.fill(r - 1, by + h / 2 - 1, r, by + h / 2 + 1, Palette.mix(Palette.TALISMAN_EDGE, 0x00000000, 0.6F));

		// The seal stamp.
		int size = Math.max(6, h - 6);
		int sx = bx + 3;
		int sy = by + (h - size) / 2;
		float light = Math.max(glow, pulse);
		if (light > 0.02F) {
			g.fill(sx - 2, sy - 2, sx + size + 2, sy + size + 2, Palette.argb(Math.round(50 * light), Palette.SEAL_GLOW));
			g.fill(sx - 1, sy - 1, sx + size + 1, sy + size + 1, Palette.argb(Math.round(90 * light), Palette.SEAL_GLOW));
		}
		int seal = on ? Palette.mix(Palette.SEAL, Palette.SEAL_GLOW, 0.55F * light) : Palette.grey(Palette.SEAL, 0.75F);
		g.fill(sx, sy, sx + size, sy + size, seal);
		Brush.frame(g, sx + 1, sy + 1, sx + size - 1, sy + size - 1, Palette.argb(0xB0, Palette.TALISMAN));
		// A seal-script mark: a small square knot.
		float mid = size / 2.0F;
		Brush.rect(g, sx + 2.5F, sy + mid - 0.5F, sx + size - 2.5F, sy + mid + 0.5F, Palette.argb(0xC8, Palette.TALISMAN));
		Brush.rect(g, sx + mid - 0.5F, sy + 2.5F, sx + mid + 0.5F, sy + mid, Palette.argb(0xC8, Palette.TALISMAN));

		// A round red stamp at the right end of the chosen talisman.
		int textRight = r - 4;
		if (chosen) {
			float stampR = Math.max(3.0F, h / 2.0F - 3.0F);
			float scx = r - stampR - 4.0F;
			float scy = by + h / 2.0F;
			Brush.ring(g, scx, scy, stampR - 1.0F, stampR, Palette.argb(0xD0, Palette.SEAL));
			Brush.disc(g, scx, scy, stampR * 0.45F, Palette.argb(0xD0, Palette.SEAL));
			textRight = Math.round(scx - stampR - 2.0F);
		}

		Component text = label.get();
		int tx = sx + size + 4;
		float room = textRight - tx;
		float scale = Brush.fitScale(font, text, room, 1.0F, 0.6F);
		float ty = by + (h - 8.0F * scale) / 2.0F;
		Brush.fit(g, font, text, tx, ty, room, on ? Palette.INK : Palette.INK_FAINT, 1.0F, 0.6F, false);
	}
}
