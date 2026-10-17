package dev.kultivator.client.disciple;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.Dao;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import java.util.function.BooleanSupplier;

/**
 * A small jade tile carrying one Dao's trigram in its colour. The disciple's chosen Dao has a gold frame and a bright
 * face; a tile waiting for its confirming click pulses red; tiles that cannot be chosen are dull.
 */
final class TrigramTile extends Widget {

	final Dao dao;
	private final BooleanSupplier chosen;
	private final BooleanSupplier asking;

	TrigramTile(int x, int y, int size, Dao dao, BooleanSupplier chosen, BooleanSupplier asking) {
		super(x, y, size, size);
		this.dao = dao;
		this.chosen = chosen;
		this.asking = asking;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
		boolean isChosen = chosen.getAsBoolean();
		boolean ask = asking.getAsBoolean();
		boolean on = isEnabled() || isChosen;
		float glow = isEnabled() ? hover.get() : 0.0F;
		int press = pressOffset(over);
		int x0 = x + press;
		int y0 = y + press;
		int x1 = x0 + w;
		int y1 = y0 + h;
		int tone = Palette.opaque(dao.color());

		if (ask) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 140.0D);
			Brush.frame(g, x0 - 2, y0 - 2, x1 + 2, y1 + 2, Palette.argb(Math.round(90 + 120 * pulse), Palette.SEAL_GLOW));
		} else if (isChosen) {
			Brush.frame(g, x0 - 2, y0 - 2, x1 + 2, y1 + 2, Palette.GOLD_LIGHT);
		} else if (glow > 0.02F) {
			Brush.frame(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, Palette.argb(Math.round(120 * glow), tone));
		}
		if (press == 0) {
			g.fill(x0 + 1, y0 + 2, x1 + 1, y1 + 1, 0x40000000);
		}
		int top = on ? Palette.mix(Palette.JADE_DARK, tone, isChosen ? 0.35F : 0.12F + 0.15F * glow) : Palette.grey(Palette.JADE_DARK, 0.7F);
		int bottom = on ? Palette.darken(Palette.JADE_DARK, 0.3F) : Palette.grey(Palette.darken(Palette.JADE_DARK, 0.3F), 0.7F);
		Brush.chamferGradient(g, x0, y0, x1, y1, 2, top, bottom);
		Brush.frame(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, isChosen ? Palette.GOLD : Palette.argb(0x90, on ? tone : Palette.LOCKED));
		int ink = on ? Palette.lighten(tone, isChosen ? 0.25F : 0.1F + 0.3F * glow) : Palette.grey(tone, 0.8F);
		float size = w * 0.55F;
		Glyphs.trigram(g, (x0 + x1) / 2.0F, (y0 + y1) / 2.0F, size, Math.max(1.0F, size * 0.15F), size * 0.3F, Glyphs.trigramOf(dao), ink);
	}
}
