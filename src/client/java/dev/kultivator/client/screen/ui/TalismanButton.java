package dev.kultivator.client.screen.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * A talisman: a strip of paper-yellow paper with a red seal stamp at its left end and the label in ink. The seal glows
 * under the mouse, the paper sinks 1 px while pressed, an "urgent" talisman's seal pulses (ready to act or waiting for
 * a confirming click) and a "stamped" one (the chosen option of a stack) slides right and carries a round red stamp.
 *
 * <p>The single button of the kit (DESIGN 43.7): at least 16 x 12, label at 1.0 cut with "…" (never shrunk), a disabled
 * talisman names its reason ({@link #alasanWhen}) in the tooltip and the footer, a cycling talisman ({@link #siklus}) steps
 * forward on a click and back on a right click with small arrows at its end. Immediate pages draw it with
 * {@link #gambar(Lukis, String, int, int, int, int, Component, boolean, Component, Runnable)}.
 */
public class TalismanButton extends Widget {

	/** Smallest clickable box (43.0.6). */
	public static final int MIN_W = 16;
	public static final int MIN_H = 12;

	private final Supplier<Component> label;
	private BooleanSupplier stamped = () -> false;
	private BooleanSupplier urgent = () -> false;
	private boolean cycle;

	public TalismanButton(int x, int y, int w, int h, Supplier<Component> label) {
		super(x, y, Math.max(MIN_W, w), Math.max(MIN_H, h));
		this.label = label;
	}

	/** Cycle mode: a click runs {@code maju}, a right click {@code mundur} (G08). */
	public TalismanButton siklus(Runnable maju, Runnable mundur) {
		this.cycle = true;
		onPress(maju);
		onRightPress(mundur);
		return this;
	}

	/** A cycling talisman steps with ←/→ while focused. */
	@Override
	public @Nullable IntConsumer geser() {
		if (!cycle) {
			return null;
		}
		return arah -> {
			if (!isEnabled()) {
				UiSound.denied();
				return;
			}
			Runnable r = arah > 0 ? action : kanan;
			if (r != null) {
				UiSound.press();
				r.run();
			}
		};
	}

	/** Width that fits {@code label} at 1.0 plus the seal and margins (clamped to {@code min..max}). */
	public static int lebar(Font font, Component label, int min, int max) {
		return Math.max(Math.max(MIN_W, min), Math.min(max, font.width(label) + 24));
	}

	// ------------------------------------------------------------------ immediate use

	/** Per-key state of an immediate talisman (kept by the page's {@link Fokus}). */
	private static final class Imediat {
		final TalismanButton widget;
		Component label = Component.empty();
		boolean aktif = true;
		boolean mendesak;
		boolean terpilih;
		@Nullable Component alasan;
		@Nullable Runnable aksi;

		Imediat() {
			widget = new TalismanButton(0, 0, MIN_W, MIN_H, () -> label);
			widget.enabledWhen(() -> aktif);
			widget.urgentWhen(() -> mendesak);
			widget.stampedWhen(() -> terpilih);
			widget.alasanWhen(() -> alasan);
			widget.onPress(() -> {
				if (aksi != null) {
					aksi.run();
				}
			});
		}
	}

	/** Draws immediate talisman {@code kunci}; returns true when the mouse is over it. */
	public static boolean gambar(Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean aktif, @Nullable Component alasan,
			@Nullable Runnable aksi) {
		return gambar(l, kunci, x, y, w, h, label, aktif, alasan, aksi, false, false);
	}

	/** Like {@link #gambar(Lukis, String, int, int, int, int, Component, boolean, Component, Runnable)} with urgent / chosen. */
	public static boolean gambar(Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean aktif, @Nullable Component alasan,
			@Nullable Runnable aksi, boolean mendesak, boolean terpilih) {
		Imediat t = l.fokusRing().simpan("talisman:" + kunci, Imediat::new);
		t.label = label;
		t.aktif = aktif;
		t.mendesak = mendesak;
		t.terpilih = terpilih;
		t.alasan = alasan;
		t.aksi = aksi;
		t.widget.x = x;
		t.widget.y = y;
		t.widget.w = Math.max(MIN_W, w);
		t.widget.h = Math.max(MIN_H, h);
		l.widget(kunci, t.widget);
		return l.di(x, y, x + t.widget.w, y + t.widget.h);
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

		if (cycle) {
			// small arrows at the end: click forward, right click back
			int ink = on ? Palette.argb(0xB0, Palette.INK_SOFT) : Palette.argb(0x80, Palette.INK_FAINT);
			Brush.text(g, font, Component.literal("‹›"), textRight - font.width("‹›"), by + (h - 8.0F) / 2.0F, ink, 1.0F, false);
			textRight -= font.width("‹›") + 2;
		}

		Component text = label.get();
		int tx = sx + size + 4;
		float room = textRight - tx;
		float ty = by + (h - 8.0F) / 2.0F;
		Brush.fit(g, font, text, tx, ty, room, on ? Palette.INK : Palette.INK_FAINT, 1.0F, 1.0F, false);
	}
}
