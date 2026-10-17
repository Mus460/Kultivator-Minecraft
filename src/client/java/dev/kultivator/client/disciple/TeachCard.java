package dev.kultivator.client.disciple;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A seal card for one of the master's techniques on the disciple scroll, in the look of the cultivation screen's seal
 * cards: bright with its Dao-coloured frame when it can be taught (its seal pulses softly), stamped with a round red
 * seal when the disciple already masters it, grey with a padlock when it cannot be taught. The card picked for reading
 * has a pulsing gold frame.
 */
final class TeachCard extends Widget {

	final Technique technique;
	private final Supplier<DiscipleView.Entry> entry;
	private final BooleanSupplier selected;

	TeachCard(int x, int y, int size, Technique technique, Supplier<DiscipleView.Entry> entry, BooleanSupplier selected) {
		super(x, y, size, size);
		this.technique = technique;
		this.entry = entry;
		this.selected = selected;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
		DiscipleView.Entry state = entry.get();
		boolean learned = state != null && state.learned();
		boolean ok = state != null && state.ok();
		boolean bright = learned || ok;
		Dao dao = technique.dao();
		int tone = dao == null ? Palette.JADE : Palette.opaque(dao.color());
		float glow = hover.get();
		long now = Util.getMillis();
		int lift = Math.round(glow);
		int x0 = x;
		int y0 = y - lift + pressOffset(over);
		int x1 = x + w;
		int y1 = y0 + h;

		if (selected.getAsBoolean()) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(now / 220.0D);
			Brush.frame(g, x0 - 3, y0 - 3, x1 + 3, y1 + 3, Palette.argb(Math.round(60 + 80 * pulse), Palette.GOLD_LIGHT));
			Brush.frame(g, x0 - 2, y0 - 2, x1 + 2, y1 + 2, Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse));
			Brush.frame(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, Palette.GOLD_DARK);
		} else if (glow > 0.02F) {
			Brush.frame(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, Palette.argb(Math.round(110 * glow), bright ? tone : Palette.LOCKED));
		}
		g.fill(x0 + 1, y0 + 2, x1 + 1, y1 + 1, 0x40000000);
		g.fill(x0, y0, x1, y1, bright ? Palette.mix(tone, Palette.INK, 0.15F) : Palette.LOCKED);
		int paper = bright ? Palette.lighten(0xFFF4E6B8, 0.15F * glow) : Palette.LOCKED_PAPER;
		g.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, paper);
		Brush.frame(g, x0 + 3, y0 + 3, x1 - 3, y1 - 3, Palette.argb(bright ? 0x70 : 0x40, bright ? tone : Palette.INK_FAINT));

		// The corner seal: pulsing on arts that wait to be taught.
		int seal = bright ? Palette.SEAL : Palette.grey(Palette.SEAL, 0.8F);
		if (ok) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(now / 300.0D);
			seal = Palette.mix(Palette.SEAL, Palette.SEAL_GLOW, 0.5F * pulse);
		}
		g.fill(x0 + 4, y0 + 4, x0 + 8, y0 + 8, seal);

		float mid = (x0 + x1) / 2.0F;
		float markSize = Math.max(7.0F, w * 0.24F);
		float markY = y0 + 5.0F + markSize / 2.0F;
		Glyphs.technique(g, technique, mid, markY, markSize, bright ? Palette.onPaper(tone) : Palette.INK_FAINT);

		Component name = technique.displayName();
		float scale = w >= 46 ? 0.6F : 0.5F;
		List<FormattedCharSequence> lines = font.split(name, Math.max(10, (int) ((w - 6) / scale)));
		int count = Math.min(2, lines.size());
		float lineHeight = 9.0F * scale;
		float nameTop = markY + markSize / 2.0F + 2.0F;
		for (int i = 0; i < count; i++) {
			FormattedCharSequence line = lines.get(i);
			Brush.text(g, font, line, mid - font.width(line) * scale / 2.0F, nameTop + i * lineHeight, bright ? Palette.INK : Palette.INK_SOFT, scale, false);
		}

		if (learned) {
			// A round red "mastered" stamp in the lower right corner.
			float r = Math.max(3.5F, w * 0.13F);
			float sx = x1 - r - 4.0F;
			float sy = y1 - r - 4.0F;
			Brush.ring(g, sx, sy, r - 1.0F, r, Palette.argb(0xD8, Palette.SEAL));
			Brush.line(g, sx - r * 0.45F, sy, sx - r * 0.1F, sy + r * 0.4F, 1.0F, Palette.argb(0xD8, Palette.SEAL));
			Brush.line(g, sx - r * 0.1F, sy + r * 0.4F, sx + r * 0.5F, sy - r * 0.4F, 1.0F, Palette.argb(0xD8, Palette.SEAL));
		} else if (!ok) {
			g.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, 0x30FFFFFF);
			Glyphs.lock(g, mid, markY, markSize + 2.0F, Palette.INK_SOFT);
		}
	}
}
