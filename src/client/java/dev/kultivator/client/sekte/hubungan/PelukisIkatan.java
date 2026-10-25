package dev.kultivator.client.sekte.hubungan;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.sekte.api.Ikatan;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The one painter of ties between people (DESIGN 43.11 G69, taken from the Jaring Hubungan and the Hubungan tab): the
 * colour and seal of each kind of tie, a thread between two discs (double cord for sworn kin and Dao companions, a glow for
 * Dao companions, a faint hair for acquaintances, thicker the stronger the feeling), a person's disc in the colour of its
 * root (hollow while away), the kind's seal disc, the centred affinity bar, the full -100..100 scale with the kinds'
 * thresholds, and the legend. Used by Murid › Jaring, Murid › Murid › hubungan and the people cards of Di Sini (sub Pohon).
 * Text is drawn at 1.0 only (43.0.5).
 */
public final class PelukisIkatan {

	/** The kinds in legend order (no {@code NONE}). */
	public static final Ikatan[] JENIS = {Ikatan.SAHABAT, Ikatan.SAUDARA_ANGKAT, Ikatan.KEKASIH, Ikatan.PASANGAN_DAO, Ikatan.SAINGAN, Ikatan.DENDAM,
			Ikatan.KENALAN};
	/** Thresholds of the kinds on the affinity scale. */
	private static final int[] TANDA = {-60, -30, 40, 75, 90};

	/** RGB colour of a kind. */
	public static int warna(Ikatan kind) {
		return HubunganColors.of(kind);
	}

	/** The CJK seal of a kind. */
	public static String segel(Ikatan kind) {
		return HubunganColors.seal(kind);
	}

	/** RGB colour of an affinity value (grudge red through grey to jade and rose). */
	public static int warnaAfinitas(int affinity) {
		return HubunganColors.affinity(affinity);
	}

	/** True for the kinds drawn as a double cord. */
	public static boolean ganda(Ikatan kind) {
		return kind == Ikatan.SAUDARA_ANGKAT || kind == Ikatan.PASANGAN_DAO;
	}

	/** "+12" / "-30" / "0". */
	public static Component nilai(int affinity) {
		return Component.literal((affinity > 0 ? "+" : "") + affinity);
	}

	/**
	 * A thread from (ax, ay) to (bx, by). {@code lit} false draws it faint (another person is in focus); {@code pulse} 0..1
	 * breathes the glow of Dao companions; {@code label} writes the affinity on the middle of the thread.
	 */
	public static void benang(GuiGraphicsExtractor g, Font font, float ax, float ay, float bx, float by, Ikatan kind, int affinity, boolean lit,
			float pulse, boolean label) {
		if (kind == Ikatan.NONE) {
			return;
		}
		int rgb = warna(kind);
		float strength = Math.min(1.0F, Math.abs(affinity) / 100.0F);
		float thick = kind == Ikatan.KENALAN ? 0.6F : 0.9F + 1.6F * strength;
		int alpha = lit ? (kind == Ikatan.KENALAN ? 0x70 : 0xE0) : 0x22;
		if (kind == Ikatan.PASANGAN_DAO && lit) {
			Brush.line(g, ax, ay, bx, by, thick + 3.0F, Palette.argb(Math.round(0x30 + 0x30 * pulse), rgb));
		}
		if (ganda(kind)) {
			float dx = by - ay;
			float dy = ax - bx;
			float len = (float) Math.sqrt(dx * dx + dy * dy);
			if (len > 0.01F) {
				dx = dx / len * 1.3F;
				dy = dy / len * 1.3F;
			}
			Brush.line(g, ax + dx, ay + dy, bx + dx, by + dy, thick * 0.7F, Palette.argb(alpha, rgb));
			Brush.line(g, ax - dx, ay - dy, bx - dx, by - dy, thick * 0.7F, Palette.argb(alpha, rgb));
		} else {
			Brush.line(g, ax, ay, bx, by, thick, Palette.argb(alpha, rgb));
		}
		if (label && lit) {
			Component value = nilai(affinity);
			float mx = (ax + bx) / 2.0F;
			float my = (ay + by) / 2.0F;
			float half = font.width(value) / 2.0F + 2.0F;
			Brush.rect(g, mx - half, my - 5.0F, mx + half, my + 5.0F, Palette.argb(0xE0, Palette.PAPER_TOP));
			Brush.frame(g, Math.round(mx - half), Math.round(my - 5.0F), Math.round(mx + half), Math.round(my + 5.0F), Palette.argb(0xA0, rgb));
			Brush.text(g, font, value, mx - font.width(value) / 2.0F, my - 4.0F, Palette.onPaper(rgb), 1.0F, false);
		}
	}

	/** A person's disc: filled in its root colour when present, hollow while away; a halo when in focus. */
	public static void simpul(GuiGraphicsExtractor g, float x, float y, float r, int rootRgb, boolean hadir, boolean lit, boolean fokus) {
		int root = Palette.opaque(rootRgb);
		if (fokus) {
			Brush.halo(g, x, y, r + 1.0F, 4.0F, rootRgb, 0.5F);
		}
		Brush.disc(g, x, y, r + 1.2F, lit ? Palette.INK : Palette.argb(0x60, Palette.INK));
		if (hadir) {
			Brush.disc(g, x, y, r, lit ? root : Palette.argb(0x70, root));
			Brush.disc(g, x - r * 0.3F, y - r * 0.3F, r * 0.3F, 0x80FFFFFF);
		} else {
			Brush.disc(g, x, y, r, Palette.argb(0xC0, Palette.PAPER_TOP));
			Brush.ring(g, x, y, r - 1.6F, r, lit ? root : Palette.argb(0x70, root));
		}
	}

	/** The kind's seal: a disc in its colour with the CJK seal at 1.0 (radius at least 6 so the glyph fits). */
	public static void segelJenis(GuiGraphicsExtractor g, Font font, float cx, float cy, float r, Ikatan kind) {
		float rr = Math.max(6.0F, r);
		int rgb = warna(kind);
		Brush.disc(g, cx, cy, rr, Palette.darken(Palette.opaque(rgb), 0.25F));
		Brush.disc(g, cx, cy, rr - 1.2F, Palette.opaque(rgb));
		Component seal = Component.literal(segel(kind));
		Brush.text(g, font, seal, cx - font.width(seal) / 2.0F, cy - 4.0F, 0xFFFFFFFF, 1.0F, false);
	}

	/** The affinity bar with zero in the middle, 2 px high, from x0 to x1 at y. */
	public static void bilahAfinitas(GuiGraphicsExtractor g, float x0, float x1, float y, int affinity) {
		float mid = (x0 + x1) / 2.0F;
		Brush.rect(g, x0, y, x1, y + 2.0F, Palette.argb(0x60, Palette.PAPER_SHADE));
		float end = mid + (x1 - x0) / 2.0F * Math.max(-100, Math.min(100, affinity)) / 100.0F;
		Brush.rect(g, Math.min(mid, end), y, Math.max(mid, end), y + 2.0F, Palette.opaque(warnaAfinitas(affinity)));
		Brush.rect(g, mid - 0.5F, y - 1.0F, mid + 0.5F, y + 3.0F, Palette.argb(0x90, Palette.INK_SOFT));
	}

	/** The full scale -100..100 in the affinity colours, the kinds' thresholds and a pointer at {@code affinity}; returns 12. */
	public static float skala(GuiGraphicsExtractor g, float x0, float x1, float y, int affinity) {
		float bx0 = x0 + 2.0F;
		float bx1 = x1 - 2.0F;
		float by = y + 2.0F;
		int segments = 40;
		for (int i = 0; i < segments; i++) {
			float a = -100.0F + 200.0F * i / segments;
			Brush.rect(g, bx0 + (bx1 - bx0) * i / segments, by, bx0 + (bx1 - bx0) * (i + 1) / segments, by + 4.0F,
					Palette.opaque(warnaAfinitas(Math.round(a))));
		}
		Brush.frame(g, (int) bx0 - 1, (int) by - 1, (int) bx1 + 1, (int) by + 5, Palette.argb(0xA0, Palette.INK_SOFT));
		for (int m : TANDA) {
			float mx = bx0 + (bx1 - bx0) * (m + 100) / 200.0F;
			Brush.rect(g, mx - 0.5F, by - 2.0F, mx + 0.5F, by + 6.0F, Palette.argb(0xC0, Palette.INK));
		}
		float px = bx0 + (bx1 - bx0) * (Math.max(-100, Math.min(100, affinity)) + 100) / 200.0F;
		Brush.rect(g, px - 1.0F, by - 3.0F, px + 1.0F, by + 7.0F, 0xFFFFFFFF);
		Brush.frame(g, (int) px - 2, (int) by - 4, (int) px + 2, (int) by + 8, Palette.INK);
		return 12.0F;
	}

	/** The legend: one line per kind (a short thread and its name at 1.0), stopping above {@code maksY}; returns the height used. */
	public static int legenda(Lukis l, int x, int y, int lebar, int maksY) {
		int yy = y;
		for (Ikatan kind : JENIS) {
			if (yy + 9 > maksY) {
				break;
			}
			int rgb = warna(kind);
			Brush.line(l.g, x, yy + 3.0F, x + 14.0F, yy + 3.0F, kind == Ikatan.KENALAN ? 0.8F : 1.8F, Palette.opaque(rgb));
			if (ganda(kind)) {
				Brush.line(l.g, x, yy + 5.5F, x + 14.0F, yy + 5.5F, 1.0F, Palette.opaque(rgb));
			}
			l.teks(Component.translatable(kind.key()), x + 18, yy, lebar - 18, Palette.onPaper(rgb));
			yy += 10;
		}
		return yy - y;
	}

	private PelukisIkatan() {
	}
}
