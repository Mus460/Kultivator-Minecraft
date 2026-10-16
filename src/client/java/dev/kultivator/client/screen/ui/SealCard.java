package dev.kultivator.client.screen.ui;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.util.List;

/**
 * A square seal card for one technique: a frame in its Dao's colour (jade for universal arts), its mark, its name and
 * its qi cost and cooldown. Learned cards are bright, the selected one has a pulsing gold frame, cards still cooling
 * down are covered by a shrinking dark sweep, and locked cards are grey with a padlock.
 */
public class SealCard extends Widget {

	public final Technique technique;

	public SealCard(int x, int y, int size, Technique technique) {
		super(x, y, size, size);
		this.technique = technique;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
		CultivationData data = ClientCultivation.data();
		boolean learned = data.knows(technique);
		boolean selected = learned && technique == data.selected();
		Dao dao = technique.dao();
		int tone = dao == null ? Palette.JADE : Palette.opaque(dao.color());
		float glow = hover.get();
		int lift = learned ? Math.round(glow) : 0;
		int cx0 = x;
		int cy0 = y - lift + pressOffset(over);
		int cx1 = x + w;
		int cy1 = cy0 + h;

		if (selected) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 220.0D);
			Brush.frame(g, cx0 - 3, cy0 - 3, cx1 + 3, cy1 + 3, Palette.argb(Math.round(60 + 80 * pulse), Palette.GOLD_LIGHT));
			Brush.frame(g, cx0 - 2, cy0 - 2, cx1 + 2, cy1 + 2, Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse));
			Brush.frame(g, cx0 - 1, cy0 - 1, cx1 + 1, cy1 + 1, Palette.GOLD_DARK);
		} else if (glow > 0.02F) {
			Brush.frame(g, cx0 - 1, cy0 - 1, cx1 + 1, cy1 + 1, Palette.argb(Math.round(110 * glow), learned ? tone : Palette.LOCKED));
		}
		g.fill(cx0 + 1, cy0 + 2, cx1 + 1, cy1 + 1, 0x40000000);

		int frame = learned ? Palette.mix(tone, Palette.INK, 0.15F) : Palette.LOCKED;
		g.fill(cx0, cy0, cx1, cy1, frame);
		int paper = learned ? Palette.lighten(0xFFF4E6B8, 0.15F * glow) : Palette.LOCKED_PAPER;
		g.fill(cx0 + 2, cy0 + 2, cx1 - 2, cy1 - 2, paper);

		// Cooldown: a dark sweep that shrinks clockwise until the art is ready again.
		long left = learned ? ClientCultivation.cooldownLeft(technique) : 0L;
		if (left > 0L && technique.cooldownTicks() > 0) {
			float fraction = Math.min(1.0F, left / (float) technique.cooldownTicks());
			g.enableScissor(cx0 + 2, cy0 + 2, cx1 - 2, cy1 - 2);
			Brush.pie(g, (cx0 + cx1) / 2.0F, (cy0 + cy1) / 2.0F, w * 0.75F, 1.0F - fraction, 1.0F, Palette.mix(paper, Palette.NIGHT_MIST, 0.4F));
			g.disableScissor();
		}

		Brush.frame(g, cx0 + 3, cy0 + 3, cx1 - 3, cy1 - 3, Palette.argb(learned ? 0x70 : 0x40, learned ? tone : Palette.INK_FAINT));
		// The seal stamp in the corner.
		g.fill(cx0 + 4, cy0 + 4, cx0 + 8, cy0 + 8, learned ? Palette.SEAL : Palette.grey(Palette.SEAL, 0.8F));

		float mid = (cx0 + cx1) / 2.0F;
		int mark = learned ? Palette.onPaper(tone) : Palette.INK_FAINT;
		float markSize = Math.max(7.0F, w * 0.24F);
		float markY = cy0 + 5.0F + markSize / 2.0F;
		Glyphs.technique(g, technique, mid, markY, markSize, mark);

		// Name: up to two lines, small.
		Component name = technique.displayName();
		float scale = w >= 46 ? 0.6F : 0.5F;
		List<FormattedCharSequence> lines = font.split(name, Math.max(10, (int) ((w - 6) / scale)));
		int count = Math.min(2, lines.size());
		float lineHeight = 9.0F * scale;
		float nameTop = markY + markSize / 2.0F + 2.0F;
		for (int i = 0; i < count; i++) {
			FormattedCharSequence line = lines.get(i);
			Brush.text(g, font, line, mid - font.width(line) * scale / 2.0F, nameTop + i * lineHeight,
					learned ? Palette.INK : Palette.INK_SOFT, scale, false);
		}

		// Cost and cooldown along the bottom.
		float small = 0.5F;
		float bottom = cy1 - 4.0F - 8.0F * small;
		Component cost = Component.translatable("kultivator.screen.tech.cost_short", technique.qiCost());
		Brush.text(g, font, cost, cx0 + 4.0F, bottom, learned ? Palette.onPaper(Palette.QI) : Palette.INK_FAINT, small, false);
		Component cooldown = seconds(left > 0L ? left : technique.cooldownTicks());
		Brush.text(g, font, cooldown, cx1 - 4.0F - font.width(cooldown) * small, bottom,
				left > 0L ? Palette.DANGER : learned ? Palette.INK_SOFT : Palette.INK_FAINT, small, false);

		if (!learned) {
			g.fill(cx0 + 2, cy0 + 2, cx1 - 2, cy1 - 2, 0x30FFFFFF);
			Glyphs.lock(g, mid, markY, markSize + 2.0F, Palette.INK_SOFT);
		}
	}

	/** Ticks as "1.5s" / "40s" (translated unit). */
	public static Component seconds(long ticks) {
		double s = ticks / 20.0D;
		String number;
		if (s >= 10.0D || Math.abs(s - Math.rint(s)) < 0.05D) {
			number = String.valueOf(Math.round(Math.ceil(s - 0.05D)));
		} else {
			number = String.format(java.util.Locale.ROOT, "%.1f", s);
		}
		return Component.translatable("kultivator.screen.seconds", number);
	}
}
