package dev.kultivator.client.screen.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A tall, narrow jade tablet hanging on the left edge of the scroll: a carved icon at the top and the short name
 * written down its length. The active tablet glows gold and slides out a little; hovering lifts its colour.
 */
public class JadeTablet extends Widget {

	private final int icon;
	private final Supplier<Component> name;
	private final BooleanSupplier active;
	private final Eased slide = new Eased(12.0F);

	public JadeTablet(int x, int y, int w, int h, int icon, Supplier<Component> name, BooleanSupplier active) {
		super(x, y, w, h);
		this.icon = icon;
		this.name = name;
		this.active = active;
	}

	/** The tablet reaches a bit further left than its resting box when it is out. */
	@Override
	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x - 6 && mouseY >= y && mouseX < x + w && mouseY < y + h;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
		float s = slide.to(active.getAsBoolean() ? 1.0F : 0.0F, seconds);
		float glow = hover.get();
		int out = Math.round(s * 5.0F + glow * 2.0F);
		int bx = x - out;
		int r = bx + w;
		int b = y + h;

		if (s > 0.02F) {
			Brush.chamfer(g, bx - 2, y - 2, r + 2, b + 2, 4, Palette.argb(Math.round(70 * s), Palette.GOLD_LIGHT));
		}
		g.fill(bx + 2, y + 2, r + 2, b + 2, 0x50000000);
		int top = Palette.mix(Palette.mix(Palette.JADE, Palette.JADE_LIGHT, 0.25F), Palette.JADE_LIGHT, 0.45F * s + 0.25F * glow);
		int bottom = Palette.mix(Palette.JADE_DARK, Palette.JADE, 0.5F * s + 0.2F * glow);
		Brush.chamferGradient(g, bx, y, r, b, 3, top, bottom);
		// Carved border and a polished highlight down the left side.
		int carve = s > 0.5F ? Palette.argb(0xC0, Palette.GOLD) : Palette.argb(0x90, Palette.JADE_DARK);
		g.fill(bx + 2, y + 3, r - 2, y + 4, carve);
		g.fill(bx + 2, b - 4, r - 2, b - 3, carve);
		g.fill(bx + 2, y + 4, bx + 3, b - 4, carve);
		g.fill(r - 3, y + 4, r - 2, b - 4, carve);
		g.fill(bx + 1, y + 4, bx + 2, b - 4, 0x50FFFFFF);

		int ink = s > 0.5F ? Palette.GOLD_LIGHT : Palette.mix(Palette.JADE_PALE, 0xFFFFFFFF, glow * 0.5F);
		// Short tablets (the smallest windows, or seven tablets on one scroll) get a smaller icon and tighter margins so
		// the name stays readable.
		boolean tiny = h < 32;
		boolean small = h < 44;
		float iconSize = Math.min(w - 9, tiny ? 6 : small ? 7 : 10);
		float pad = tiny ? 8.0F : small ? 12.0F : 14.0F;
		float iconTop = tiny ? 4.0F : 6.0F;
		Glyphs.tab(g, icon, bx + w / 2.0F, y + iconTop + iconSize / 2.0F, iconSize, ink);

		// The name, written down the tablet (rotated a quarter turn).
		Component text = name.get();
		float length = h - iconSize - pad;
		if (length > (tiny ? 6.0F : 8.0F)) {
			float scale = Brush.fitScale(font, text, length, 0.75F, 0.45F);
			float drawn = Math.min(length, font.width(text) * scale);
			float startY = y + iconSize + (tiny ? 5.0F : small ? 8.0F : 9.0F) + (length - drawn) / 2.0F;
			g.pose().pushMatrix();
			g.pose().translate(bx + w / 2.0F + 4.0F * scale, startY);
			g.pose().rotate((float) (Math.PI / 2.0D));
			Brush.fit(g, font, text, 0.0F, 0.0F, length, ink, 0.75F, 0.45F, false);
			g.pose().popMatrix();
		}
	}

	@Override
	protected void activate() {
		UiSound.tab();
		if (action != null) {
			action.run();
		}
	}
}
