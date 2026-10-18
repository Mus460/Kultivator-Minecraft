package dev.kultivator.client.epic.screen;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.sound.EpicSound;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Calligraphy title cards in the jade-scroll style of 5b (DESIGN 23.8.3).
 * <ul>
 * <li>FULL: lower third (62 % of the height) between the bars, 3-4 s: the main line in the vanilla font up to ×3 (clamped
 * to 90 % of the width, +1 px letter spacing, a 2-px ink shadow), revealed by a brush sweep left to right over 10 ticks
 * on an ink band with ink bleed on the last characters; a tapered underline sweeps in over 8 ticks, the subtitle (×1.25,
 * gold-grey) follows 6 ticks later; it leaves with an ink dissolve bottom-up over 10 ticks. "Kurangi gerak": a plain
 * fade.</li>
 * <li>STAMP: a top-right seal tile with a name, 1.5 s. BANNER: a thin line under the boss bar, 1.5 s.</li>
 * </ul>
 * The seal is a red square with one CJK glyph in paper colour (Presentasi). One FULL card at a time, ≥ 1 s apart, at most 3
 * waiting; STAMPs drop when the queue is full. Cards come only for the protagonist's own scenes (the Kehancuran safety
 * BANNER is the one exception). For SUPPRESS_TITLE beats the vanilla title is cleared and {@code setTitle} /
 * {@code setSubtitle} are cancelled for 60 ticks. "Kartu judul" off: no cards, nothing suppressed.
 */
public final class TitleCard {

	public enum Kind { FULL, STAMP, BANNER }

	private static final Identifier INK_BAND = Kultivator.id("textures/gui/epic/ink_band.png");
	private static final Identifier SEAL = Kultivator.id("textures/gui/epic/seal.png");
	private static final int[] RAINBOW = {0xFF5A5A, 0xFFA03A, 0xFFE070, 0x6BE89A, 0x6BE8E8, 0x7A9BFF, 0xC08BFF};
	private static final int PAPER = 0xF4ECD6;
	private static final int GOLD_GREY = 0xD8C894;
	private static final long GAP = 1000L;

	/** One card. */
	public static final class Card {
		final Kind kind;
		final Component title;
		final @Nullable Component sub;
		final String seal;
		final int sealRgb;
		long millis;
		boolean rainbowSub;
		boolean goldRim;
		boolean shatter;
		@Nullable String secondSeal;
		boolean suppress;
		boolean safeBeat;
		long start;

		Card(Kind kind, Component title, @Nullable Component sub, String seal, int sealRgb, long millis) {
			this.kind = kind;
			this.title = title;
			this.sub = sub;
			this.seal = seal;
			this.sealRgb = sealRgb;
			this.millis = millis;
		}

		public Card rainbow() {
			this.rainbowSub = true;
			return this;
		}

		public Card goldRim() {
			this.goldRim = true;
			return this;
		}

		public Card shatter() {
			this.shatter = true;
			return this;
		}

		public Card second(String glyph) {
			this.secondSeal = glyph;
			return this;
		}

		public Card suppressTitle(boolean on) {
			this.suppress = on;
			return this;
		}

		public Card safeBeat(boolean on) {
			this.safeBeat = on;
			return this;
		}
	}

	private static final Deque<Card> QUEUE = new ArrayDeque<>();
	private static @Nullable Card full;
	private static @Nullable Card stamp;
	private static @Nullable Card banner;
	private static long lastFullEnd;
	private static long suppressUntil;

	// ================================================================ API

	public static Card full(Component title, @Nullable Component sub, String seal, int sealRgb, int ticks) {
		return new Card(Kind.FULL, title, sub, seal, sealRgb, Mth.clamp(ticks, 60, 80) * 50L);
	}

	public static Card stamp(Component title, String seal, int sealRgb) {
		return new Card(Kind.STAMP, title, null, seal, sealRgb, 1500L);
	}

	public static Card banner(Component title) {
		return new Card(Kind.BANNER, title, null, "", Palette.SEAL & 0xFFFFFF, 1500L);
	}

	/** Shows or queues a card (FULL waits its turn, STAMP drops when the queue is full, BANNER replaces the banner). */
	public static void show(Card card) {
		if (!EpicSettings.cards()) {
			return;
		}
		switch (card.kind) {
			case FULL -> {
				if (QUEUE.size() >= 3) {
					return;
				}
				QUEUE.addLast(card);
				pump();
			}
			case STAMP -> {
				if (QUEUE.size() >= 3) {
					return;
				}
				card.start = Util.getMillis();
				stamp = card;
				EpicSound.seal();
			}
			case BANNER -> {
				card.start = Util.getMillis();
				banner = card;
			}
		}
	}

	/** The skip key: clears the queue (the showing card leaves quickly). */
	public static void skip() {
		QUEUE.clear();
		if (full != null) {
			long now = Util.getMillis();
			full.millis = Math.min(full.millis, now - full.start + 500L);
		}
	}

	public static void clear() {
		QUEUE.clear();
		full = null;
		stamp = null;
		banner = null;
		suppressUntil = 0L;
	}

	/** True while a card shows for a beat flagged SUPPRESS_TITLE: the vanilla title and subtitle are then dropped. */
	public static boolean suppressVanillaTitle() {
		return suppressUntil != 0L && Util.getMillis() < suppressUntil;
	}

	/** True while a FULL card of a safe beat shows (the bars may come in for it). */
	public static boolean fullInSafeBeat() {
		return full != null && full.safeBeat;
	}

	public static boolean busy() {
		return full != null || stamp != null || banner != null || !QUEUE.isEmpty();
	}

	private static void pump() {
		long now = Util.getMillis();
		if (full != null && now - full.start >= full.millis) {
			full = null;
			lastFullEnd = now;
		}
		if (full == null && !QUEUE.isEmpty() && now - lastFullEnd >= GAP) {
			full = QUEUE.pollFirst();
			full.start = now;
			EpicSound.seal();
			if (full.suppress) {
				Minecraft.getInstance().gui.hud.clearTitles();
				suppressUntil = now + 3000L;
			}
		}
	}

	// ================================================================ drawing

	/** The {@code kultivator:epic_card} HUD element (before CHAT, so chat stays readable above it). */
	public static final class Hud implements HudElement {
		@Override
		public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
			if (full == null && stamp == null && banner == null && QUEUE.isEmpty()) {
				return;
			}
			Minecraft minecraft = Minecraft.getInstance();
			pump();
			if (minecraft.player == null || minecraft.gui.hud.isHidden()) {
				return;
			}
			long now = Util.getMillis();
			Font font = minecraft.font;
			if (full != null) {
				drawFull(g, font, full, now);
			}
			if (stamp != null) {
				if (now - stamp.start >= stamp.millis) {
					stamp = null;
				} else {
					drawStamp(g, font, stamp, now);
				}
			}
			if (banner != null) {
				if (now - banner.start >= banner.millis) {
					banner = null;
				} else {
					drawBanner(g, font, banner, now);
				}
			}
		}
	}

	private static void drawFull(GuiGraphicsExtractor g, Font font, Card card, long now) {
		long t = now - card.start;
		int width = g.guiWidth();
		int height = g.guiHeight();
		boolean plain = EffectQuality.reduceMotion();
		float reveal = Mth.clamp(t / 500.0F, 0.0F, 1.0F);
		float exit = Mth.clamp((t - (card.millis - 500L)) / 500.0F, 0.0F, 1.0F);
		float fade = plain ? Math.min(Mth.clamp(t / 300.0F, 0.0F, 1.0F), 1.0F - exit) : 1.0F;
		if (fade <= 0.01F) {
			return;
		}
		Component title = Brush.bold(card.title);
		float scale = Brush.fitScale(font, title, width * 0.9F - 40.0F, 3.0F, 1.0F);
		String text = title.getString();
		int spacing = 1;
		float textW = (font.width(title) + spacing * Math.max(0, text.codePointCount(0, text.length()) - 1)) * scale;
		float cy = height * 0.62F;
		float textH = 9.0F * scale;
		float x0 = (width - textW) / 2.0F;
		float y0 = cy - textH / 2.0F;
		int bandTop = Math.round(y0 - textH * 0.35F);
		int bandBottom = Math.round(y0 + textH * 1.35F + (card.sub != null ? 12.0F * 1.25F : 0.0F));
		int left = Math.round(x0 - 36.0F);
		int right = Math.round(x0 + textW + 24.0F);
		int revealX = plain ? right : Math.round(left + (right - left) * reveal);
		int dissolveBottom = plain ? bandBottom : Math.round(bandBottom - (bandBottom - bandTop) * exit);
		if (dissolveBottom <= bandTop) {
			return;
		}
		g.enableScissor(left, bandTop, Math.max(left + 1, revealX), dissolveBottom);
		int bandAlpha = Math.round(150 * fade);
		g.blit(RenderPipelines.GUI_TEXTURED, INK_BAND, left, bandTop, 0.0F, 0.0F, right - left, bandBottom - bandTop, 256, 32, 256, 32,
				Palette.argb(bandAlpha, 0x0B0A09));
		// Seal: a red square with one glyph in paper colour.
		int sealSize = Math.round(Math.max(14.0F, textH * 0.95F));
		int sealX = Math.round(x0 - sealSize - 8.0F);
		int sealY = Math.round(cy - sealSize / 2.0F);
		drawSeal(g, font, card.seal, card.sealRgb, sealX, sealY, sealSize, fade, card.goldRim, now);
		if (card.secondSeal != null) {
			drawSeal(g, font, card.secondSeal, 0xC8A030, Math.round(x0 + textW + 6.0F), sealY, Math.round(sealSize * 0.8F), fade, false, now);
		}
		// Main line: per glyph with +1 px spacing, a 2-px ink shadow, the reveal front bleeding.
		int shadow = Palette.argb(Math.round(160 * fade), 0x0A0806);
		float cursor = x0;
		int index = 0;
		for (int offset = 0; offset < text.length(); ) {
			int cp = text.codePointAt(offset);
			offset += Character.charCount(cp);
			Component glyph = Component.literal(new String(Character.toChars(cp))).withStyle(ChatFormatting.BOLD);
			float glyphW = font.width(glyph) * scale;
			float alpha = fade;
			float dy = 0.0F;
			if (!plain) {
				float front = left + (right - left) * reveal;
				float bleed = Mth.clamp((front - cursor) / Math.max(8.0F, glyphW * 3.0F), 0.0F, 1.0F);
				alpha *= bleed;
				if (card.shatter && exit > 0.0F) {
					dy = exit * exit * (20.0F + (index * 37 % 13) * 4.0F);
					alpha *= 1.0F - exit;
				}
			}
			if (alpha > 0.02F) {
				Brush.text(g, font, glyph, cursor + 2.0F, y0 + 2.0F + dy, Palette.argb(Math.round(ARGBa(shadow) * alpha), 0x0A0806), scale, false);
				Brush.text(g, font, glyph, cursor, y0 + dy, Palette.argb(Math.round(255 * alpha), PAPER), scale, false);
			}
			cursor += glyphW + spacing * scale;
			index++;
		}
		// Tapered underline from the centre outwards (8 ticks).
		float line = plain ? 1.0F : Mth.clamp((t - 100L) / 400.0F, 0.0F, 1.0F);
		if (line > 0.0F) {
			float half = textW * 0.5F * line;
			float ly = y0 + textH + 2.0F;
			float mid = width / 2.0F;
			for (int i = 0; i < 6; i++) {
				float k = i / 6.0F;
				float seg = half * (1.0F - k);
				int a = Math.round(220 * fade * (1.0F - k * 0.6F));
				Brush.rect(g, mid - seg, ly + (i % 2) * 0.5F, mid + seg, ly + 1.0F + (i < 2 ? 0.5F : 0.0F), Palette.argb(a / 3, 0xE8C766));
			}
		}
		// Subtitle, 6 ticks later.
		if (card.sub != null) {
			float subAlpha = plain ? fade : Mth.clamp((t - 300L) / 300.0F, 0.0F, 1.0F) * fade;
			if (subAlpha > 0.02F) {
				float subScale = Math.min(1.25F, Brush.fitScale(font, card.sub, width * 0.85F, 1.25F, 0.7F));
				float sy = y0 + textH + 6.0F;
				if (card.rainbowSub) {
					drawRainbow(g, font, card.sub, width / 2.0F, sy, subScale, subAlpha, now);
				} else {
					Brush.centered(g, font, card.sub, width / 2.0F, sy, Palette.argb(Math.round(240 * subAlpha), GOLD_GREY), subScale, true);
				}
			}
		}
		g.disableScissor();
	}

	private static int ARGBa(int color) {
		return color >>> 24;
	}

	private static void drawRainbow(GuiGraphicsExtractor g, Font font, Component text, float cx, float y, float scale, float alpha, long now) {
		String s = text.getString();
		float total = font.width(text) * scale;
		float cursor = cx - total / 2.0F;
		boolean still = EffectQuality.reduceMotion();
		int i = 0;
		for (int offset = 0; offset < s.length(); ) {
			int cp = s.codePointAt(offset);
			offset += Character.charCount(cp);
			Component glyph = Component.literal(new String(Character.toChars(cp)));
			int colorIndex = Math.floorMod(i + (still ? 0 : (int) (now / 120L)), RAINBOW.length);
			Brush.text(g, font, glyph, cursor, y, Palette.argb(Math.round(245 * alpha), RAINBOW[colorIndex]), scale, true);
			cursor += font.width(glyph) * scale;
			i++;
		}
	}

	private static void drawSeal(GuiGraphicsExtractor g, Font font, String glyph, int rgb, int x, int y, int size, float alpha, boolean goldRim,
								 long now) {
		g.blit(RenderPipelines.GUI_TEXTURED, SEAL, x, y, 0.0F, 0.0F, size, size, 32, 32, 32, 32, Palette.argb(Math.round(235 * alpha), rgb));
		if (!glyph.isEmpty()) {
			Component text = Component.literal(glyph);
			float scale = Math.max(0.8F, size / 11.0F);
			float w = font.width(text) * scale;
			Brush.text(g, font, text, x + (size - w) / 2.0F, y + (size - 8.0F * scale) / 2.0F, Palette.argb(Math.round(245 * alpha), PAPER), scale, false);
		}
		if (goldRim) {
			Brush.frame(g, x - 1, y - 1, x + size + 1, y + size + 1, Palette.argb(Math.round(220 * alpha), 0xE8C766));
			if (!EffectQuality.reduceMotion()) {
				float cx = x + size / 2.0F;
				float cy = y + size / 2.0F;
				float r = size * 0.85F;
				for (int i = 0; i < 4; i++) {
					double a = now / 600.0D + i * Math.PI / 2.0D;
					Brush.dot(g, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, 1.2F, Palette.argb(Math.round(230 * alpha), 0xFFE08A));
				}
			}
		}
	}

	private static void drawStamp(GuiGraphicsExtractor g, Font font, Card card, long now) {
		long t = now - card.start;
		float in = Mth.clamp(t / 200.0F, 0.0F, 1.0F);
		float out = Mth.clamp((card.millis - t) / 250.0F, 0.0F, 1.0F);
		float alpha = Math.min(in, out);
		int width = g.guiWidth();
		Component title = Brush.bold(card.title);
		float scale = Math.min(1.0F, Brush.fitScale(font, title, width * 0.35F, 1.0F, 0.6F));
		int textW = Math.round(font.width(title) * scale);
		int size = 18;
		int panelW = size + 8 + textW + 8;
		int slide = Math.round((1.0F - in) * 20.0F);
		int x = width - panelW - 6 + slide;
		int y = 6;
		Brush.chamfer(g, x, y, x + panelW, y + size + 4, 3, Palette.argb(Math.round(190 * alpha), 0x14110D));
		Brush.frame(g, x, y, x + panelW, y + size + 4, Palette.argb(Math.round(150 * alpha), 0xB98F34));
		drawSeal(g, font, card.seal, card.sealRgb, x + 2, y + 2, size, alpha, false, now);
		Brush.text(g, font, title, x + size + 8.0F, y + (size + 4 - 8.0F * scale) / 2.0F, Palette.argb(Math.round(250 * alpha), PAPER), scale, true);
	}

	private static void drawBanner(GuiGraphicsExtractor g, Font font, Card card, long now) {
		long t = now - card.start;
		float alpha = Math.min(Mth.clamp(t / 200.0F, 0.0F, 1.0F), Mth.clamp((card.millis - t) / 300.0F, 0.0F, 1.0F));
		int width = g.guiWidth();
		Component title = Brush.bold(card.title);
		float scale = Math.min(1.0F, Brush.fitScale(font, title, width * 0.7F, 1.0F, 0.6F));
		float textW = font.width(title) * scale;
		float y = 34.0F;
		float cx = width / 2.0F;
		Brush.rect(g, cx - textW / 2.0F - 16.0F, y - 2.0F, cx + textW / 2.0F + 16.0F, y + 10.0F * scale + 2.0F, Palette.argb(Math.round(150 * alpha), 0x0B0A09));
		Brush.rect(g, cx - textW / 2.0F - 16.0F, y + 10.0F * scale + 2.0F, cx + textW / 2.0F + 16.0F, y + 10.0F * scale + 3.0F,
				Palette.argb(Math.round(200 * alpha), 0xC8A040));
		Brush.centered(g, font, title, cx, y, Palette.argb(Math.round(250 * alpha), 0xF0DFA8), scale, true);
	}

	private TitleCard() {
	}
}
