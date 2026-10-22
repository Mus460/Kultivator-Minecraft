package dev.kultivator.client.duel.ui;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.net.DuelPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The result card of a duel (DESIGN 35.8.1 {@code DuelResult}): after the closing film's 勝 title (module V), a lacquer
 * card unrolls at the lower left for the duellists and the onlookers who saw the fight: the seal of the outcome for the
 * viewer (勝 won, 敗 lost, 和 draw, 止 cancelled; the onlooker sees 勝), the server's title line, the reason, the two
 * names side by side with their hits and Perfect Deflects, the longest exchange chain, the length of the fight, the
 * change of Honour and the stake line. It waits while a title card or the film is still on screen (at most 3 s), stays
 * 9 s, and slides away. Drawn by {@link DuelHud}.
 */
public final class DuelResultCard {

	private static final int W = 200;
	private static final int H = 86;
	private static final int STAY = 180;
	private static final int SLIDE = 10;
	/** Waits this long for the closing film when the duel ended with one. */
	private static final int FILM_WAIT = 70;

	private static @Nullable DuelPayloads.DuelResult result;
	private static @Nullable DuelPayloads.DuelSessionSync session;
	private static long ticks;
	private static long arrivedAt;
	private static long shownAt = -1L;

	public static void show(DuelPayloads.DuelResult p, @Nullable DuelPayloads.DuelSessionSync s) {
		result = p;
		session = s;
		arrivedAt = ticks;
		shownAt = -1L;
	}

	public static void tick() {
		ticks++;
		DuelPayloads.DuelResult current = result;
		if (current == null) {
			return;
		}
		if (shownAt < 0L) {
			boolean decisive = session != null && (session.flags() & DuelIds.SF_DECISIVE) != 0;
			long wait = decisive || current.reason() == DuelIds.RES_PENENTU ? FILM_WAIT : 10L;
			boolean busy = dev.kultivator.client.epic.screen.TitleCard.busy() || dev.kultivator.client.epic.screen.Letterbox.amount() > 0.05F;
			if (ticks - arrivedAt >= wait && (!busy || ticks - arrivedAt > FILM_WAIT + 60L)) {
				shownAt = ticks;
			}
			return;
		}
		if (ticks - shownAt > STAY + SLIDE) {
			result = null;
			session = null;
		}
	}

	public static void clear() {
		result = null;
		session = null;
		shownAt = -1L;
	}

	static void draw(GuiGraphicsExtractor g, Font font, int width, int height, float partial) {
		DuelPayloads.DuelResult p = result;
		if (p == null || shownAt < 0L) {
			return;
		}
		float age = ticks - shownAt + partial;
		float in = Mth.clamp(age / SLIDE, 0.0F, 1.0F);
		float out = Mth.clamp((age - STAY) / SLIDE, 0.0F, 1.0F);
		float open = ease(in) * (1.0F - ease(out));
		if (open <= 0.01F) {
			return;
		}
		int x0 = 8;
		int y0 = Math.round(height * 0.5F);
		int fullH = Math.min(H, height - y0 - 30);
		int h = Math.max(6, Math.round(fullH * open));
		int x1 = x0 + Math.min(W, width - 16);
		int y1 = y0 + h;
		Brush.chamferGradient(g, x0, y0, x1, y1, 3, Palette.argb(232, 0x2A1610), Palette.argb(232, 0x120805));
		g.fill(x0 + 3, y0 + 1, x1 - 3, y0 + 2, Palette.argb(220, 0xD4A637));
		g.fill(x0 + 3, y1 - 2, x1 - 3, y1 - 1, Palette.argb(220, 0xD4A637));
		if (open < 0.9F) {
			return;
		}
		g.enableScissor(x0, y0, x1, y1);
		LocalPlayer player = Minecraft.getInstance().player;
		int self = player == null ? -1 : player.getId();
		String glyph;
		int sealRgb;
		if (p.reason() == DuelIds.RES_BATAL) {
			glyph = "止";
			sealRgb = 0x8D928E;
		} else if (p.reason() == DuelIds.RES_SERI || p.winnerId() < 0) {
			glyph = "和";
			sealRgb = 0xD4A637;
		} else if (p.loserId() == self) {
			glyph = "敗";
			sealRgb = 0x6E160F;
		} else {
			glyph = "勝";
			sealRgb = 0xB3281D;
		}
		DuelHud.seal(g, x0 + 20.0F, y0 + 22.0F, 0.85F, true, glyph, partial);
		if (sealRgb != 0xB3281D) {
			// tint the stamp toward its meaning (grey cancel, gold draw, dark loss)
			Brush.disc(g, x0 + 20.0F, y0 + 22.0F, 12.5F, Palette.argb(90, sealRgb));
		}
		float tx = x0 + 40.0F;
		float tw = x1 - tx - 6.0F;
		Brush.fit(g, font, p.title(), tx, y0 + 6.0F, tw, Palette.argb(255, 0xFFE7B0), 0.95F, 0.55F, true);
		Brush.fit(g, font, Component.translatable("kultivator.duel.ui.reason." + Mth.clamp(p.reason(), 0, 6)), tx, y0 + 17.0F, tw,
				Palette.argb(220, 0xF1D57E), 0.65F, 0.45F, true);
		Brush.fit(g, font, Component.translatable("kultivator.duel.ui.result.length", DuelHud.clock(p.durationTicks()), p.maxChain()), tx, y0 + 26.0F,
				tw, Palette.argb(190, 0xD8CCB0), 0.6F, 0.42F, true);

		// the two columns
		DuelPayloads.DuelSessionSync s = session;
		Component aName = s != null ? s.aName() : Component.literal("A");
		Component bName = s != null ? s.bName() : Component.literal("B");
		int aRgb = s != null && s.aRgb() != 0 ? s.aRgb() & 0xFFFFFF : 0xE8DCC0;
		int bRgb = s != null && s.bRgb() != 0 ? s.bRgb() & 0xFFFFFF : 0xE8DCC0;
		float colW = (x1 - x0 - 16) / 2.0F;
		float cy = y0 + 40.0F;
		column(g, font, aName, aRgb, p.hitsA(), p.perfectA(), x0 + 8.0F, cy, colW - 4.0F, s != null && s.aId() == p.winnerId());
		column(g, font, bName, bRgb, p.hitsB(), p.perfectB(), x0 + 12.0F + colW, cy, colW - 4.0F, s != null && s.bId() == p.winnerId());
		g.fill(Math.round(x0 + 8.0F + colW), Math.round(cy), Math.round(x0 + 9.0F + colW), Math.round(cy + 18.0F), Palette.argb(120, 0xD4A637));

		float by = y0 + 62.0F;
		if (p.honourDelta() != 0) {
			int rgb = p.honourDelta() > 0 ? 0x9FE08F : 0xFF7A6A;
			Component honour = Component.translatable("kultivator.duel.ui.result.honour", (p.honourDelta() > 0 ? "+" : "") + p.honourDelta());
			Brush.fit(g, font, honour, x0 + 8.0F, by, x1 - x0 - 16.0F, Palette.argb(230, rgb), 0.6F, 0.42F, true);
			by += 8.0F;
		}
		if (!p.stakeLine().getString().isEmpty()) {
			Brush.fit(g, font, p.stakeLine(), x0 + 8.0F, by, x1 - x0 - 16.0F, Palette.argb(220, 0xFFC870), 0.6F, 0.42F, true);
			by += 8.0F;
		}
		if (by < y0 + fullH - 8) {
			Brush.fit(g, font, Component.translatable("kultivator.duel.ui.result.where"), x0 + 8.0F, y0 + fullH - 9.0F, x1 - x0 - 16.0F,
					Palette.argb(120, 0xD8CCB0), 0.5F, 0.4F, true);
		}
		g.disableScissor();
	}

	private static void column(GuiGraphicsExtractor g, Font font, Component name, int rgb, int hits, int perfects, float x, float y, float w,
							   boolean winner) {
		Component shown = winner ? Component.translatable("kultivator.duel.ui.result.winner_mark", name) : name;
		Brush.fit(g, font, shown, x, y, w, Palette.argb(240, rgb), 0.7F, 0.45F, true);
		Brush.fit(g, font, Component.translatable("kultivator.duel.ui.result.stats", hits, perfects), x, y + 9.0F, w, Palette.argb(200, 0xE8DCC0),
				0.6F, 0.42F, true);
	}

	private static float ease(float t) {
		float u = 1.0F - t;
		return 1.0F - u * u * u;
	}

	private DuelResultCard() {
	}
}
