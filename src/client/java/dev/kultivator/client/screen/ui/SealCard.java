package dev.kultivator.client.screen.ui;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.data.TechniqueSpec;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.function.Supplier;

/**
 * A square seal card for one technique spec (DESIGN 5b, 22.12.2): a frame in its Dao's colour (jade for universal arts),
 * the glyph (Dao trigram plus type mark), a grade ribbon in the upper right corner (Dewa shimmers through the rainbow,
 * still with "Kurangi gerak"), four mastery beads, a cooldown sweep, the loadout slot number, a small red crack mark when
 * the art carves in Mode Kehancuran, a "BARU" badge for arts never opened, a padlock when locked and a pulsing gold frame
 * when selected. From 36 px the name is written under the glyph. Drawn by {@link #paint} (the codex grids draw only
 * visible cards) or as a widget.
 */
public class SealCard extends Widget {

	/** How one card looks right now. */
	public record Look(boolean bright, boolean locked, boolean selected, long cooldownLeft, int cooldownTotal, int mastery, int slot,
			boolean isNew, boolean stamped) {
		public static final Look LOCKED = new Look(false, true, false, 0L, 0, 0, -1, false, false);
	}

	public final TechniqueSpec spec;
	private final Supplier<Look> look;

	public SealCard(int x, int y, int size, TechniqueSpec spec, Supplier<Look> look) {
		super(x, y, size, size);
		this.spec = spec;
		this.look = look;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
		paint(g, font, spec, x, y + pressOffset(over), w, look.get(), hover.get());
	}

	/** Draws a card of {@code size} at {@code x, y}; {@code glow} 0..1 is the hover light. */
	public static void paint(GuiGraphicsExtractor g, Font font, TechniqueSpec spec, int x, int y, int size, Look look, float glow) {
		boolean bright = look.bright();
		int tone = spec.dao() == null ? Palette.JADE : Palette.opaque(spec.dao().color());
		int lift = bright ? Math.round(glow) : 0;
		int x0 = x;
		int y0 = y - lift;
		int x1 = x + size;
		int y1 = y0 + size;
		long now = Util.getMillis();

		if (look.selected()) {
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

		// Cooldown: a dark sweep that shrinks clockwise until the art is ready again.
		if (look.cooldownLeft() > 0L && look.cooldownTotal() > 0) {
			float fraction = Math.min(1.0F, look.cooldownLeft() / (float) look.cooldownTotal());
			g.enableScissor(x0 + 2, y0 + 2, x1 - 2, y1 - 2);
			Brush.pie(g, (x0 + x1) / 2.0F, (y0 + y1) / 2.0F, size * 0.75F, 1.0F - fraction, 1.0F, Palette.mix(paper, Palette.NIGHT_MIST, 0.4F));
			g.disableScissor();
		}
		Brush.frame(g, x0 + 3, y0 + 3, x1 - 3, y1 - 3, Palette.argb(bright ? 0x70 : 0x40, bright ? tone : Palette.INK_FAINT));

		// The grade ribbon across the upper right corner.
		int ribbon = ribbonColor(spec);
		int corner = Math.max(5, size / 4);
		for (int i = 0; i < corner; i++) {
			g.fill(x1 - corner + i, y0 + 2 + i, x1 - 2, y0 + 3 + i, bright ? ribbon : Palette.grey(ribbon, 0.6F));
		}

		float mid = (x0 + x1) / 2.0F;
		boolean named = size >= 46; // room for the name at 1.0 under the mark (43.0.5)
		float markSize = Math.max(7.0F, size * (named ? 0.34F : 0.42F));
		float markY = y0 + (named ? 4.0F + markSize / 2.0F + 1.0F : size / 2.0F - 1.0F);
		int mark = bright ? Palette.onPaper(tone) : Palette.INK_FAINT;
		Glyphs.spec(g, spec, mid, markY, markSize, mark);

		if (named) {
			// 43.0.5: the name at 1.0, cut with "…" (the whole name in the tooltip and the detail slip); small cards show the mark only
			Component name = dev.kultivator.client.codex.CodexNames.name(spec);
			Brush.fitCentered(g, font, name, mid, markY + markSize / 2.0F + 1.0F, size - 6.0F, bright ? Palette.INK : Palette.INK_SOFT, 1.0F, 1.0F,
					false);
		}

		// Four mastery beads along the bottom.
		float beadY = y1 - 4.5F;
		float spacing = Math.max(3.0F, size / 9.0F);
		for (int i = 0; i < 4; i++) {
			float bx = mid + (i - 1.5F) * spacing;
			boolean lit = i < look.mastery();
			Brush.dot(g, bx, beadY, size >= 32 ? 1.1F : 0.8F, lit ? Palette.GOLD : Palette.argb(0x50, Palette.INK_FAINT));
		}

		// The loadout slot number (upper left), the crack mark (lower left) and the BARU badge.
		if (look.slot() >= 0 && size >= 28) {
			Component slot = Component.literal(String.valueOf(look.slot() + 1));
			Brush.text(g, font, slot, x0 + 3.0F, y0 + 3.0F, look.slot() < 4 ? Palette.GOLD_DARK : Palette.INK_SOFT, 1.0F, false);
		} else if (look.slot() >= 0) {
			g.fill(x0 + 4, y0 + 4, x0 + 7, y0 + 7, look.slot() < 4 ? Palette.GOLD_DARK : Palette.INK_SOFT);
		} else {
			g.fill(x0 + 4, y0 + 4, x0 + 7, y0 + 7, bright ? Palette.SEAL : Palette.grey(Palette.SEAL, 0.8F));
		}
		if (spec.hasDestruction()) {
			float cx = x0 + 5.0F;
			float cy = y1 - 6.0F;
			int red = bright ? Palette.DANGER : Palette.grey(Palette.DANGER, 0.6F);
			Brush.line(g, cx - 2.0F, cy - 2.0F, cx, cy, 0.7F, red);
			Brush.line(g, cx, cy, cx - 1.0F, cy + 1.0F, 0.7F, red);
			Brush.line(g, cx - 1.0F, cy + 1.0F, cx + 1.5F, cy + 2.5F, 0.7F, red);
		}
		if (look.isNew() && bright) {
			Component badge = Component.translatable("kultivator.codex.card.new");
			float badgeW = font.width(badge) + 3.0F;
			if (badgeW <= size - 4.0F) {
				Brush.rect(g, x0 + 1.0F, y0 + 1.0F, x0 + 1.0F + badgeW, y0 + 10.0F, Palette.SEAL);
				Brush.text(g, font, badge, x0 + 2.5F, y0 + 2.0F, 0xFFFFF4D6, 1.0F, false);
			} else {
				// too small for the word at 1.0 (43.0.5): a red seal dot marks the new art
				Brush.disc(g, x0 + 5.0F, y0 + 5.0F, 2.5F, Palette.SEAL);
			}
		}
		if (look.stamped()) {
			float r = Math.max(3.0F, size * 0.13F);
			float sx = x1 - r - 4.0F;
			float sy = y1 - r - 5.0F;
			Brush.ring(g, sx, sy, r - 1.0F, r, Palette.argb(0xD8, Palette.SEAL));
			Brush.line(g, sx - r * 0.45F, sy, sx - r * 0.1F, sy + r * 0.4F, 1.0F, Palette.argb(0xD8, Palette.SEAL));
			Brush.line(g, sx - r * 0.1F, sy + r * 0.4F, sx + r * 0.5F, sy - r * 0.4F, 1.0F, Palette.argb(0xD8, Palette.SEAL));
		}
		if (look.locked()) {
			g.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, 0x30FFFFFF);
			Glyphs.lock(g, mid, markY, Math.min(markSize, 10.0F) + 2.0F, Palette.INK_SOFT);
		}
	}

	/** The grade ribbon colour (Dewa shimmers unless "Kurangi gerak"). */
	public static int ribbonColor(TechniqueSpec spec) {
		if (spec.dewa() && !EffectQuality.reduceMotion()) {
			float hue = (Util.getMillis() % 6000L) / 6000.0F;
			return dev.kultivator.client.codex.SpecInfo.rainbow(hue);
		}
		return Palette.opaque(spec.grade().ribbon());
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
