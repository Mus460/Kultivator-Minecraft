package dev.kultivator.client.sectevent;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * The bracket on bamboo slips (DESIGN 20.3.12, 20.3.13): one column per round, each bout a pair of slips joined by a
 * cord to the next round; the winner's slip is bright with a red seal, the loser's faded, the bout being fought pulses.
 * Visitors (north-bank rivals, rival champions) are written in grey; the bout for third place sits under the final.
 */
final class BracketView {

	/** Kinds as in {@code Bracket}: 0 the master's disciple, 1 rival, 2 champion, 3 youth. */
	private static final int RIVAL = 1;
	private static final int CHAMPION = 2;

	static void draw(GuiGraphicsExtractor g, Font font, CompoundTag bracket, int x, int y, int w, int h) {
		int size = Math.max(2, bracket.getIntOr("size", 2));
		int roundsTotal = Math.max(1, Integer.numberOfTrailingZeros(Integer.highestOneBit(size)));
		ListTag rounds = bracket.getListOrEmpty("rounds");
		int gap = 6;
		int colW = Math.max(30, (w - gap * (roundsTotal - 1)) / roundsTotal);
		int firstMatches = size / 2;
		float slot = (float) (h - 14) / firstMatches;
		float boxH = Math.max(10.0F, Math.min(20.0F, slot - 2.0F));
		float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 160.0D);
		for (int r = 0; r < roundsTotal; r++) {
			int matches = firstMatches >> r;
			float span = slot * (1 << r);
			int cx = x + r * (colW + gap);
			ListTag played = r < rounds.size() && rounds.get(r) instanceof CompoundTag wrapper ? wrapper.getListOrEmpty("m") : new ListTag();
			for (int m = 0; m < matches; m++) {
				float centre = y + span * m + span / 2.0F;
				int top = Math.round(centre - boxH / 2.0F);
				int bottom = Math.round(centre + boxH / 2.0F);
				CompoundTag match = m < played.size() && played.get(m) instanceof CompoundTag tag ? tag : null;
				drawMatch(g, font, match, cx, top, cx + colW, bottom, pulse);
				if (r + 1 < roundsTotal) {
					// The cord to the next round.
					float nextCentre = y + span * 2 * (m / 2) + span;
					int cord = Palette.argb(0xB0, Palette.CORD);
					Brush.line(g, cx + colW, centre, cx + colW + gap / 2.0F, centre, 1.0F, cord);
					Brush.line(g, cx + colW + gap / 2.0F, centre, cx + colW + gap / 2.0F, nextCentre, 1.0F, cord);
					Brush.line(g, cx + colW + gap / 2.0F, nextCentre, cx + colW + gap, nextCentre, 1.0F, cord);
				}
			}
		}
		if (bracket.contains("bronze")) {
			CompoundTag bronze = bracket.getCompoundOrEmpty("bronze");
			int bx = x + (roundsTotal - 1) * (colW + gap);
			int by = y + h - 12;
			Brush.fit(g, font, Component.translatable("kultivator.sectevent.ui.bronze"), bx, by - 8, colW, Palette.SEAL_DARK, 0.5F, 0.4F, false);
			drawMatch(g, font, bronze, bx, by, bx + colW, by + 11, pulse);
		}
	}

	private static void drawMatch(GuiGraphicsExtractor g, Font font, CompoundTag match, int x0, int y0, int x1, int y1, float pulse) {
		int mid = (y0 + y1) / 2;
		if (match == null) {
			g.fill(x0, y0, x1, y1, Palette.argb(0x40, Palette.BAMBOO_ALT));
			Brush.frame(g, x0, y0, x1, y1, Palette.argb(0x60, Palette.BAMBOO_KNOT));
			Brush.centered(g, font, Component.literal("?"), (x0 + x1) / 2.0F, mid - 3.0F, Palette.INK_FAINT, 0.6F, false);
			return;
		}
		int winner = match.getIntOr("w", -1);
		if (match.getBooleanOr("now", false)) {
			Brush.frame(g, x0 - 2, y0 - 2, x1 + 2, y1 + 2, Palette.argb(Math.round(90 + 120 * pulse), Palette.SEAL_GLOW));
		}
		half(g, font, match, "a", match.getIntOr("ak", 0), winner == 0, winner == 1, x0, y0, x1, mid);
		half(g, font, match, "b", match.getIntOr("bk", 0), winner == 1, winner == 0, x0, mid, x1, y1);
	}

	private static void half(GuiGraphicsExtractor g, Font font, CompoundTag match, String key, int kind, boolean won, boolean lost,
			int x0, int y0, int x1, int y1) {
		g.fill(x0, y0, x1, y1 - 1, won ? Palette.BAMBOO : lost ? Palette.argb(0x70, Palette.BAMBOO_ALT) : Palette.BAMBOO_ALT);
		g.fill(x0, y0, x0 + 2, y1 - 1, Palette.BAMBOO_KNOT);
		if (won) {
			g.fill(x1 - 5, y0 + 1, x1 - 1, y1 - 2, Palette.SEAL);
		}
		Component name = SectEventClient.name(match, key);
		if (won) {
			name = name.copy().withStyle(ChatFormatting.BOLD);
		}
		int color = lost ? Palette.INK_FAINT : kind == RIVAL || kind == CHAMPION ? Palette.INK_SOFT : Palette.INK;
		float scale = Math.max(0.35F, Math.min(0.6F, (y1 - y0 - 1) / 9.0F));
		Brush.fit(g, font, name, x0 + 4.0F, y0 + (y1 - y0 - 8.0F * scale) / 2.0F, x1 - x0 - (won ? 11.0F : 6.0F), color, scale, 0.35F, false);
	}

	private BracketView() {
	}
}
