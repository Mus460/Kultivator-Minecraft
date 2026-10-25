package dev.kultivator.client.screen.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Drawing primitives of the jade-scroll theme, all made of plain fills: discs and rings from horizontal spans (no
 * overlap, so translucent colours stay even), arcs and pies from small rotated strips (use opaque colours for those),
 * lines, and text helpers that scale or cut text to fit. Round shapes are drawn at {@link #FINE} sub-pixels per GUI
 * pixel so their edges stay smooth at every GUI scale.
 */
public final class Brush {

	/** Sub-pixels per GUI pixel for round shapes and lines. */
	public static final int FINE = 2;
	private static final float INV = 1.0F / FINE;
	private static final float TAU = (float) (Math.PI * 2.0D);
	/** The cut mark of {@link #fit} (DESIGN 43.0.5: "…", the whole text in the tooltip). */
	public static final String CUT = "…";

	// ================================================================== round shapes

	/** A filled disc. */
	public static void disc(GuiGraphicsExtractor g, float cx, float cy, float r, int color) {
		band(g, cx, cy, r, -r, r, color, color);
	}

	/**
	 * The rows of a disc between {@code yTop} and {@code yBottom} (relative to the centre), with a vertical gradient
	 * from {@code colorTop} to {@code colorBottom} over that band. Used for liquids rising inside a circle.
	 */
	public static void band(GuiGraphicsExtractor g, float cx, float cy, float r, float yTop, float yBottom, int colorTop, int colorBottom) {
		if (r <= 0.0F || ((colorTop | colorBottom) >>> 24) == 0) {
			return;
		}
		int rr = Math.max(1, Math.round(r * FINE));
		int from = Math.max(-rr, Math.round(yTop * FINE));
		int to = Math.min(rr, Math.round(yBottom * FINE));
		if (to <= from) {
			return;
		}
		boolean gradient = colorTop != colorBottom;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(INV, INV);
		int runStart = from;
		int runHalf = half(rr, from);
		for (int y = from + 1; y <= to; y++) {
			int h = y < to ? half(rr, y) : -1;
			if (h != runHalf || gradient || y == to) {
				if (runHalf > 0) {
					int color = gradient ? Palette.mix(colorTop, colorBottom, (runStart - from + 0.5F) / (to - from)) : colorTop;
					g.fill(-runHalf, runStart, runHalf, y, color);
				}
				runStart = y;
				runHalf = h;
			}
		}
		g.pose().popMatrix();
	}

	/** A full ring (annulus) from radius {@code r0} to {@code r1}, made of spans (safe for translucent colours). */
	public static void ring(GuiGraphicsExtractor g, float cx, float cy, float r0, float r1, int color) {
		ring(g, cx, cy, r0, r1, color, FINE);
	}

	/** A ring at {@code fine} sub-pixels per pixel (1 for soft, faint things like halos: half the fills). */
	private static void ring(GuiGraphicsExtractor g, float cx, float cy, float r0, float r1, int color, int fine) {
		if (r1 <= r0 || (color >>> 24) == 0) {
			return;
		}
		int outer = Math.max(1, Math.round(r1 * fine));
		int inner = Math.max(0, Math.round(r0 * fine));
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(1.0F / fine, 1.0F / fine);
		for (int y = -outer; y < outer; y++) {
			int ho = half(outer, y);
			if (ho <= 0) {
				continue;
			}
			int hi = inner > 0 && y >= -inner && y < inner ? half(inner, y) : 0;
			if (hi <= 0) {
				g.fill(-ho, y, ho, y + 1, color);
			} else if (ho > hi) {
				g.fill(-ho, y, -hi, y + 1, color);
				g.fill(hi, y, ho, y + 1, color);
			}
		}
		g.pose().popMatrix();
	}

	/**
	 * Part of a ring from fraction {@code from} to {@code to} of a full turn, clockwise from the top (0 = 12 o'clock).
	 * Made of small rotated strips that overlap slightly: use an opaque colour.
	 */
	public static void arc(GuiGraphicsExtractor g, float cx, float cy, float r0, float r1, float from, float to, int color) {
		float span = Math.min(1.0F, to - from);
		if (span <= 0.0F || r1 <= r0 || (color >>> 24) == 0) {
			return;
		}
		int full = Math.max(20, Math.min(120, Math.round(r1 * 2.4F)));
		int steps = Math.max(1, (int) Math.ceil(span * full));
		float step = span * TAU / steps;
		float halfHeight = r1 * (float) Math.tan(step / 2.0F) + 0.3F;
		int inner = Math.round(r0 * FINE);
		int outer = Math.max(inner + 1, Math.round(r1 * FINE));
		int top = -Math.max(1, Math.round(halfHeight * FINE));
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(INV, INV);
		g.pose().rotate((float) (-Math.PI / 2.0D) + from * TAU + step / 2.0F);
		for (int i = 0; i < steps; i++) {
			g.fill(inner, top, outer, -top, color);
			g.pose().rotate(step);
		}
		g.pose().popMatrix();
	}

	/** A pie slice (cooldown sweep) clockwise from the top; opaque colours only. */
	public static void pie(GuiGraphicsExtractor g, float cx, float cy, float r, float from, float to, int color) {
		arc(g, cx, cy, 0.0F, r, from, to, color);
	}

	/** A soft halo around a circle of radius {@code r}: a few faint rings fading outwards. */
	public static void halo(GuiGraphicsExtractor g, float cx, float cy, float r, float width, int rgb, float strength) {
		if (strength <= 0.01F) {
			return;
		}
		int layers = r + width < 14.0F ? 2 : 3;
		float each = width / layers;
		for (int i = 0; i < layers; i++) {
			int alpha = Math.round(110 * strength * (1.0F - i / (float) layers) * (1.0F - i / (float) layers));
			ring(g, cx, cy, r + i * each, r + (i + 1) * each, Palette.argb(alpha, rgb), 1);
		}
	}

	/** A small dot (square below 1.5 px, round above). */
	public static void dot(GuiGraphicsExtractor g, float cx, float cy, float r, int color) {
		if (r < 0.8F) {
			rect(g, cx - r, cy - r, cx + r, cy + r, color);
		} else {
			disc(g, cx, cy, r, color);
		}
	}

	private static int half(int rr, int row) {
		float yc = row + 0.5F;
		float s = rr * (float) rr - yc * yc;
		return s <= 0.0F ? 0 : Math.round((float) Math.sqrt(s));
	}

	// ================================================================== straight shapes

	/** A rectangle with sub-pixel corners. */
	public static void rect(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
		g.pose().pushMatrix();
		g.pose().scale(INV, INV);
		g.fill(Math.round(x0 * FINE), Math.round(y0 * FINE), Math.round(x1 * FINE), Math.round(y1 * FINE), color);
		g.pose().popMatrix();
	}

	/** A straight line {@code thickness} GUI pixels wide. */
	public static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float thickness, int color) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float length = (float) Math.sqrt(dx * dx + dy * dy);
		if (length < 0.01F || (color >>> 24) == 0) {
			return;
		}
		int t = Math.max(1, Math.round(thickness * FINE));
		g.pose().pushMatrix();
		g.pose().translate(x0, y0);
		g.pose().rotate((float) Math.atan2(dy, dx));
		g.pose().scale(INV, INV);
		g.fill(0, -t / 2, Math.round(length * FINE), -t / 2 + t, color);
		g.pose().popMatrix();
	}

	/** A 1 px frame inside the rectangle. */
	public static void frame(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		g.fill(x0, y0, x1, y0 + 1, color);
		g.fill(x0, y1 - 1, x1, y1, color);
		g.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
		g.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
	}

	/** A rectangle with its corners cut off by {@code cut} pixels (jade tablets, seal cards). */
	public static void chamfer(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int cut, int color) {
		for (int i = 0; i < cut; i++) {
			int inset = cut - i;
			g.fill(x0 + inset, y0 + i, x1 - inset, y0 + i + 1, color);
			g.fill(x0 + inset, y1 - i - 1, x1 - inset, y1 - i, color);
		}
		g.fill(x0, y0 + cut, x1, y1 - cut, color);
	}

	/** A chamfered rectangle filled with a vertical gradient. */
	public static void chamferGradient(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int cut, int top, int bottom) {
		int height = Math.max(1, y1 - y0);
		for (int i = 0; i < cut; i++) {
			int inset = cut - i;
			g.fill(x0 + inset, y0 + i, x1 - inset, y0 + i + 1, Palette.mix(top, bottom, (i + 0.5F) / height));
			g.fill(x0 + inset, y1 - i - 1, x1 - inset, y1 - i, Palette.mix(top, bottom, (height - i - 0.5F) / height));
		}
		g.fillGradient(x0, y0 + cut, x1, y1 - cut, Palette.mix(top, bottom, cut / (float) height),
				Palette.mix(top, bottom, (height - cut) / (float) height));
	}

	// ================================================================== text

	/** Text at {@code x, y} drawn {@code scale} times the normal size. */
	public static void text(GuiGraphicsExtractor g, Font font, Component text, float x, float y, int color, float scale, boolean shadow) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, text, 0, 0, color, shadow);
		g.pose().popMatrix();
	}

	/** A prepared line (from {@link Font#split}) drawn at a scale. */
	public static void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, float x, float y, int color, float scale, boolean shadow) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, text, 0, 0, color, shadow);
		g.pose().popMatrix();
	}

	/** Text centred on {@code cx}. */
	public static void centered(GuiGraphicsExtractor g, Font font, Component text, float cx, float y, int color, float scale, boolean shadow) {
		text(g, font, text, cx - font.width(text) * scale / 2.0F, y, color, scale, shadow);
	}

	/**
	 * The scale at which {@code text} is drawn into {@code width}. Since DESIGN 43.0.5 text is never shrunk to fit: a
	 * scale above 1.0 may come down to 1.0 (and to {@code min} when that is higher), never below {@code min(max, 1.0)};
	 * text that is still too wide is cut by {@link #fit}.
	 */
	public static float fitScale(Font font, Component text, float width, float max, float min) {
		int w = font.width(text);
		float floor = Math.max(min, Math.min(max, 1.0F));
		if (w <= 0) {
			return max;
		}
		return Math.max(floor, Math.min(max, width / w));
	}

	/**
	 * Text at its scale ({@link #fitScale}: never shrunk below 1.0 or the caller's {@code max}); when it does not fit
	 * {@code width} it is cut with "…" and, while the mouse is over the cut text on a screen, the whole text is offered to
	 * {@link SlipTooltip} (DESIGN 43.0.5). Returns the width actually drawn.
	 */
	public static float fit(GuiGraphicsExtractor g, Font font, Component text, float x, float y, float width, int color, float max,
							float min, boolean shadow) {
		if (width <= 4.0F) {
			return 0.0F;
		}
		float scale = fitScale(font, text, width, max, min);
		int room = (int) (width / scale);
		if (font.width(text) <= room) {
			text(g, font, text, x, y, color, scale, shadow);
			return font.width(text) * scale;
		}
		String cut = font.plainSubstrByWidth(text.getString(), Math.max(0, room - font.width(CUT))) + CUT;
		Component shortened = Component.literal(cut).withStyle(text.getStyle());
		text(g, font, shortened, x, y, color, scale, shadow);
		offerFull(g, text, x, y, x + width, y + 9.0F * scale);
		return font.width(shortened) * scale;
	}

	/** The mouse in GUI coordinates while a screen is open (null otherwise). */
	private static float[] mouse() {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.gui.screen() == null) {
			return null;
		}
		return new float[] {(float) mc.mouseHandler.getScaledXPos(mc.getWindow()), (float) mc.mouseHandler.getScaledYPos(mc.getWindow())};
	}

	/** Offers {@code full} as a weak tooltip when the mouse is over the box (local coordinates under the current pose). */
	private static void offerFull(GuiGraphicsExtractor g, Component full, float x0, float y0, float x1, float y1) {
		float[] m = mouse();
		if (m == null) {
			return;
		}
		org.joml.Vector2f a = g.pose().transformPosition(x0, y0, new org.joml.Vector2f());
		org.joml.Vector2f b = g.pose().transformPosition(x1, y1, new org.joml.Vector2f());
		if (m[0] >= Math.min(a.x, b.x) && m[0] < Math.max(a.x, b.x) && m[1] >= Math.min(a.y, b.y) && m[1] < Math.max(a.y, b.y)
				&& g.containsPointInScissor(Math.round(m[0]), Math.round(m[1]))) {
			SlipTooltip.antreLemah(List.of(full));
		}
	}

	// ================================================================== numbers and time (were TeksTempat, DESIGN 43.7)

	private static final String TK = "kultivator.tempat_ui.";
	/** Nilai Batu per stone tier (NilaiBatu.VALUE {1, 9, 81, 729}). */
	private static final long[] NILAI = {1L, 9L, 81L, 729L};
	private static final String[] TINGKAT = {"rendah", "menengah", "tinggi", "agung"};

	private static String ribu() {
		return Component.translatable(TK + "angka.ribu").getString();
	}

	private static String desimal() {
		return Component.translatable(TK + "angka.desimal").getString();
	}

	/** Full number with the language's thousands mark: 1234567 -> "1.234.567" (id) / "1,234,567" (en). */
	public static String angka(long n) {
		String digits = Long.toString(Math.abs(n));
		StringBuilder out = new StringBuilder();
		String mark = ribu();
		int lead = digits.length() % 3;
		for (int i = 0; i < digits.length(); i++) {
			if (i > 0 && (i - lead) % 3 == 0) {
				out.append(mark);
			}
			out.append(digits.charAt(i));
		}
		return (n < 0 ? "−" : "") + out;
	}

	/** Short number: under 10 000 full, then "12,5 rb", "1,2 jt", "3,4 M". */
	public static String singkat(long n) {
		long a = Math.abs(n);
		String sign = n < 0 ? "−" : "";
		if (a < 10_000L) {
			return sign + angka(a);
		}
		String unit;
		double v;
		if (a < 1_000_000L) {
			v = a / 1000.0D;
			unit = Component.translatable(TK + "angka.rb").getString();
		} else if (a < 1_000_000_000L) {
			v = a / 1_000_000.0D;
			unit = Component.translatable(TK + "angka.jt").getString();
		} else {
			v = a / 1_000_000_000.0D;
			unit = Component.translatable(TK + "angka.m").getString();
		}
		String mark = desimal();
		String body = v >= 100.0D ? Long.toString(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v).replace(".", mark);
		if (body.endsWith(mark + "0")) {
			body = body.substring(0, body.length() - 1 - mark.length());
		}
		return sign + body + " " + unit;
	}

	/** One decimal with the language's mark: 9.6 -> "9,6". */
	public static String desimal1(float v) {
		return String.format(java.util.Locale.ROOT, "%.1f", v).replace(".", desimal());
	}

	/** Signed amount "+214" / "−60" (0 = "·"). */
	public static String bertanda(long n) {
		return n == 0L ? "·" : (n > 0 ? "+" : "") + angka(n);
	}

	/** "1.234 NB" (full) or the short form. */
	public static Component nb(long n, boolean pendek) {
		return Component.translatable(TK + "nb", pendek ? singkat(n) : angka(n));
	}

	/** The fewest-stones breakdown of {@code nb}: "1 Tinggi · 4 Menengah · 3 Rendah" (agung first). */
	public static Component rincianBatu(long nb) {
		long rest = Math.max(0L, nb);
		int[] count = new int[4];
		for (int t = NILAI.length - 1; t >= 0; t--) {
			count[t] = (int) Math.min(Integer.MAX_VALUE, rest / NILAI[t]);
			rest -= count[t] * NILAI[t];
		}
		return batuDiTas(count);
	}

	/** Stones by tier [rendah, menengah, tinggi, agung] -> "3 Tinggi · 5 Menengah · 12 Rendah" (none: "tanpa batu"). */
	public static Component batuDiTas(int[] batu) {
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		boolean any = false;
		for (int t = Math.min(batu.length, 4) - 1; t >= 0; t--) {
			if (batu[t] <= 0) {
				continue;
			}
			if (any) {
				out.append(" · ");
			}
			out.append(Component.translatable(TK + "batu." + TINGKAT[t], angka(batu[t])));
			any = true;
		}
		return any ? out : Component.translatable(TK + "batu.nol");
	}

	/** Countdown of game ticks: "m:ss" below one hour, "2 j 10 m" below two game days, then "3 hari". */
	public static Component waktu(long ticks) {
		long t = Math.max(0L, ticks);
		long seconds = (t + 19L) / 20L;
		if (seconds < 3600L) {
			return Component.literal(String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L));
		}
		if (t < 24000L * 2L) {
			long minutes = seconds / 60L;
			return Component.translatable(TK + "waktu.jam_menit", minutes / 60L, minutes % 60L);
		}
		return Component.translatable(TK + "waktu.hari", t / 24000L);
	}

	/** "hari ini" / "besok" / "3 hari". */
	public static Component hari(int n) {
		return n <= 0 ? Component.translatable(TK + "waktu.hari_ini") : n == 1 ? Component.translatable(TK + "waktu.besok")
				: Component.translatable(TK + "waktu.hari", n);
	}

	/** Percent 0..1 -> "45 %". */
	public static String persen(float f) {
		return Math.round(f * 100.0F) + " %";
	}

	/** Like {@link #fit} but centred on {@code cx}. */
	public static void fitCentered(GuiGraphicsExtractor g, Font font, Component text, float cx, float y, float width, int color, float max,
								   float min, boolean shadow) {
		float scale = fitScale(font, text, width, max, min);
		float drawn = Math.min(width, font.width(text) * scale);
		fit(g, font, text, cx - drawn / 2.0F, y, width, color, max, min, shadow);
	}

	/** Word-wrapped text; returns the height used ({@code lines × 9 × scale}). */
	public static float wrap(GuiGraphicsExtractor g, Font font, Component text, float x, float y, float width, int color, float scale,
							 boolean shadow) {
		List<FormattedCharSequence> lines = font.split(text, Math.max(10, (int) (width / scale)));
		float lineHeight = 9.0F * scale;
		for (int i = 0; i < lines.size(); i++) {
			text(g, font, lines.get(i), x, y + i * lineHeight, color, scale, shadow);
		}
		return lines.size() * lineHeight;
	}

	/** The height {@link #wrap} would use. */
	public static float wrapHeight(Font font, Component text, float width, float scale) {
		return font.split(text, Math.max(10, (int) (width / scale))).size() * 9.0F * scale;
	}

	/** "Calligraphy" weight: the same text in bold. */
	public static Component bold(Component text) {
		return text.copy().withStyle(ChatFormatting.BOLD);
	}

	private Brush() {
	}
}
