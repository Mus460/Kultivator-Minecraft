package dev.kultivator.client.screen.ui;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.TechniqueType;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Small pictograms drawn with fills: the tablet icons, trigrams of the eight Daos, a lock and a technique's mark.
 * Every glyph is centred on {@code cx, cy} and about {@code size} pixels across.
 */
public final class Glyphs {

	public static final int STATUS = 0;
	public static final int ROOT_DAO = 1;
	public static final int TECHNIQUES = 2;
	public static final int SETTINGS = 3;
	public static final int HELP = 4;
	// Phase 6 (DESIGN 20.2.13).
	/** A lotus bud: talents and physiques. */
	public static final int TALENT = 5;
	/** A bell: the natal treasure. */
	public static final int TREASURE = 6;
	/** A four-pointed star: the path of stars. */
	public static final int PATH = 7;
	/** A pagoda roof: the Sect Hall. */
	public static final int HALL = 8;
	/** A seal stamp: the red seal of Mode Kehancuran. */
	public static final int SEAL = 9;
	// Phase 6b (DESIGN 21.24).
	/** Terraces climbing to a small roof: Pembangunan Sekte (the Bangun tablet). */
	public static final int BUILD = 10;
	// Phase 6c (DESIGN 22.12).
	/** An open book with a seal: the Kitab Teknik (Kodeks tablet). */
	public static final int CODEX = 11;
	/** A ring of seals around a core: the Cincin Segel. */
	public static final int RING = 12;
	// Phase 7 (DESIGN 19.2.13).
	/** Stepping stones winding up to a summit: the Jalan tablet. */
	public static final int JALAN = 13;
	/** A three-legged cauldron with rising qi: the Kitab Alkimia. */
	public static final int ALKIMIA = 14;

	/** The Daos around the Bagua, clockwise from the top (Earlier Heaven order: opposites face each other). */
	public static final Dao[] BAGUA = {Dao.SWORD, Dao.SHADOW, Dao.ICE, Dao.BODY, Dao.SPACE, Dao.LIGHTNING, Dao.FIRE, Dao.LIFE};

	/**
	 * The trigram of a Dao as three bits, bit 0 the bottom line (1 = solid yang line, 0 = broken yin line):
	 * Sword Heaven ☰, Shadow & Wind Xun ☴, Ice Water ☵, Body Mountain ☶, Space Earth ☷, Lightning Thunder ☳,
	 * Fire ☲, Life Lake ☱.
	 */
	public static int trigramOf(Dao dao) {
		return switch (dao) {
			case SWORD -> 0b111;
			case SHADOW -> 0b110;
			case ICE -> 0b010;
			case BODY -> 0b100;
			case SPACE -> 0b000;
			case LIGHTNING -> 0b001;
			case FIRE -> 0b101;
			case LIFE -> 0b011;
		};
	}

	/**
	 * Three trigram bars in the current pose, the bottom line (bit 0) lowest. {@code width} is the bar length,
	 * {@code bar} its thickness and {@code pitch} the distance between bar centres.
	 */
	public static void trigram(GuiGraphicsExtractor g, float cx, float cy, float width, float bar, float pitch, int bits, int color) {
		for (int line = 0; line < 3; line++) {
			float y = cy + (1 - line) * pitch;
			boolean yang = (bits & (1 << line)) != 0;
			float left = cx - width / 2.0F;
			float right = cx + width / 2.0F;
			if (yang) {
				Brush.rect(g, left, y - bar / 2.0F, right, y + bar / 2.0F, color);
			} else {
				float gap = width * 0.22F;
				Brush.rect(g, left, y - bar / 2.0F, cx - gap / 2.0F, y + bar / 2.0F, color);
				Brush.rect(g, cx + gap / 2.0F, y - bar / 2.0F, right, y + bar / 2.0F, color);
			}
		}
	}

	/** The icon of a jade tablet. */
	public static void tab(GuiGraphicsExtractor g, int icon, float cx, float cy, float size, int color) {
		float s = size / 2.0F;
		switch (icon) {
			case STATUS -> {
				// The dantian: a ring with the core in the middle.
				Brush.ring(g, cx, cy, s * 0.62F, s, color);
				Brush.disc(g, cx, cy, s * 0.3F, color);
			}
			case ROOT_DAO -> trigram(g, cx, cy, size * 0.9F, Math.max(1.0F, size * 0.14F), size * 0.3F, 0b101, color);
			case TECHNIQUES -> {
				// A seal: square frame with a mark inside.
				Brush.rect(g, cx - s, cy - s, cx + s, cy - s + 1.0F, color);
				Brush.rect(g, cx - s, cy + s - 1.0F, cx + s, cy + s, color);
				Brush.rect(g, cx - s, cy - s, cx - s + 1.0F, cy + s, color);
				Brush.rect(g, cx + s - 1.0F, cy - s, cx + s, cy + s, color);
				Brush.rect(g, cx - s * 0.45F, cy - 0.5F, cx + s * 0.45F, cy + 0.5F, color);
				Brush.rect(g, cx - 0.5F, cy - s * 0.45F, cx + 0.5F, cy + s * 0.45F, color);
			}
			case SETTINGS -> {
				// A coin with its square hole.
				Brush.ring(g, cx, cy, s * 0.72F, s, color);
				Brush.rect(g, cx - s * 0.3F, cy - s * 0.3F, cx + s * 0.3F, cy + s * 0.3F, color);
			}
			case TALENT -> {
				// A lotus bud: a middle petal between two leaning ones, on a short stem.
				Brush.disc(g, cx, cy - s * 0.25F, s * 0.38F, color);
				Brush.disc(g, cx - s * 0.5F, cy + s * 0.05F, s * 0.3F, color);
				Brush.disc(g, cx + s * 0.5F, cy + s * 0.05F, s * 0.3F, color);
				Brush.rect(g, cx - s * 0.75F, cy + s * 0.4F, cx + s * 0.75F, cy + s * 0.4F + 1.0F, color);
			}
			case TREASURE -> {
				// A bell: dome, rim and clapper.
				Brush.band(g, cx, cy, s * 0.65F, cy - s * 0.75F, cy + s * 0.35F, color, color);
				Brush.rect(g, cx - s * 0.9F, cy + s * 0.35F, cx + s * 0.9F, cy + s * 0.35F + 1.0F, color);
				Brush.disc(g, cx, cy + s * 0.75F, s * 0.18F, color);
				Brush.rect(g, cx - 0.5F, cy - s, cx + 0.5F, cy - s * 0.7F, color);
			}
			case PATH -> {
				// A four-pointed star with a bright core.
				Brush.line(g, cx, cy - s, cx, cy + s, Math.max(1.0F, s * 0.3F), color);
				Brush.line(g, cx - s, cy, cx + s, cy, Math.max(1.0F, s * 0.3F), color);
				Brush.disc(g, cx, cy, s * 0.35F, color);
			}
			case HALL -> {
				// A pagoda roof with upturned eaves over two pillars.
				Brush.rect(g, cx - s, cy - s * 0.35F, cx + s, cy - s * 0.35F + 1.2F, color);
				Brush.rect(g, cx - s * 0.6F, cy - s * 0.75F, cx + s * 0.6F, cy - s * 0.75F + 1.0F, color);
				Brush.rect(g, cx - s, cy - s * 0.55F, cx - s * 0.8F, cy - s * 0.35F, color);
				Brush.rect(g, cx + s * 0.8F, cy - s * 0.55F, cx + s, cy - s * 0.35F, color);
				Brush.rect(g, cx - s * 0.6F, cy - s * 0.3F, cx - s * 0.4F, cy + s, color);
				Brush.rect(g, cx + s * 0.4F, cy - s * 0.3F, cx + s * 0.6F, cy + s, color);
			}
			case SEAL -> {
				// A square seal stamp with a cross cut in it.
				Brush.rect(g, cx - s * 0.8F, cy - s * 0.8F, cx + s * 0.8F, cy + s * 0.8F, color);
				Brush.rect(g, cx - s * 0.5F, cy - 0.5F, cx + s * 0.5F, cy + 0.5F, Palette.darken(color, 0.6F));
				Brush.rect(g, cx - 0.5F, cy - s * 0.5F, cx + 0.5F, cy + s * 0.5F, Palette.darken(color, 0.6F));
			}
			case BUILD -> {
				// Three terraces stepping up to the right, a little upturned roof on the top one.
				Brush.rect(g, cx - s, cy + s * 0.7F, cx + s, cy + s, color);
				Brush.rect(g, cx - s * 0.35F, cy + s * 0.3F, cx + s, cy + s * 0.6F, color);
				Brush.rect(g, cx + s * 0.25F, cy - s * 0.1F, cx + s, cy + s * 0.2F, color);
				Brush.rect(g, cx + s * 0.05F, cy - s * 0.5F, cx + s * 1.0F, cy - s * 0.5F + 1.0F, color);
				Brush.rect(g, cx + s * 0.05F, cy - s * 0.65F, cx + s * 0.2F, cy - s * 0.5F, color);
				Brush.rect(g, cx + s * 0.85F, cy - s * 0.65F, cx + s * 1.0F, cy - s * 0.5F, color);
				Brush.rect(g, cx + s * 0.35F, cy - s * 0.85F, cx + s * 0.7F, cy - s * 0.85F + 1.0F, color);
				Brush.rect(g, cx + s * 0.3F, cy - s * 0.45F, cx + s * 0.4F, cy - s * 0.1F, color);
				Brush.rect(g, cx + s * 0.65F, cy - s * 0.45F, cx + s * 0.75F, cy - s * 0.1F, color);
			}
			case CODEX -> {
				// An open book: two pages, a spine, and a seal on the right page.
				Brush.rect(g, cx - s, cy - s * 0.7F, cx - 0.5F, cy + s * 0.8F, Palette.fade(color, 0.55F));
				Brush.rect(g, cx + 0.5F, cy - s * 0.7F, cx + s, cy + s * 0.8F, Palette.fade(color, 0.55F));
				Brush.rect(g, cx - 0.5F, cy - s * 0.85F, cx + 0.5F, cy + s * 0.9F, color);
				Brush.rect(g, cx - s * 0.8F, cy - s * 0.35F, cx - s * 0.2F, cy - s * 0.35F + 0.8F, color);
				Brush.rect(g, cx - s * 0.8F, cy + s * 0.05F, cx - s * 0.3F, cy + s * 0.05F + 0.8F, color);
				Brush.rect(g, cx + s * 0.3F, cy - s * 0.3F, cx + s * 0.8F, cy + s * 0.2F, color);
			}
			case RING -> {
				// Eight beads around a core.
				Brush.disc(g, cx, cy, s * 0.28F, color);
				for (int i = 0; i < 8; i++) {
					double a = Math.PI * 2.0D * i / 8.0D;
					Brush.dot(g, cx + (float) Math.cos(a) * s * 0.8F, cy + (float) Math.sin(a) * s * 0.8F, Math.max(0.7F, s * 0.17F), color);
				}
			}
			case JALAN -> {
				// Stepping stones winding from the lower left up to a small summit on the right.
				Brush.disc(g, cx - s * 0.7F, cy + s * 0.75F, s * 0.22F, color);
				Brush.disc(g, cx - s * 0.15F, cy + s * 0.45F, s * 0.2F, color);
				Brush.disc(g, cx - s * 0.5F, cy + s * 0.05F, s * 0.18F, color);
				Brush.disc(g, cx + s * 0.05F, cy - s * 0.3F, s * 0.17F, color);
				Brush.line(g, cx + s * 0.25F, cy - s * 0.45F, cx + s * 0.65F, cy - s, Math.max(1.0F, s * 0.18F), color);
				Brush.line(g, cx + s * 0.65F, cy - s, cx + s, cy - s * 0.45F, Math.max(1.0F, s * 0.18F), color);
			}
			case ALKIMIA -> {
				// A round cauldron on three legs with two wisps of qi above it.
				Brush.band(g, cx, cy + s * 0.1F, s * 0.7F, -s * 0.25F, s * 0.7F, color, color);
				Brush.rect(g, cx - s * 0.85F, cy - s * 0.15F, cx + s * 0.85F, cy - s * 0.15F + 1.0F, color);
				Brush.rect(g, cx - s * 0.55F, cy + s * 0.6F, cx - s * 0.4F, cy + s, color);
				Brush.rect(g, cx - 0.5F, cy + s * 0.6F, cx + 0.5F, cy + s, color);
				Brush.rect(g, cx + s * 0.4F, cy + s * 0.6F, cx + s * 0.55F, cy + s, color);
				Brush.dot(g, cx - s * 0.3F, cy - s * 0.5F, Math.max(0.7F, s * 0.14F), color);
				Brush.dot(g, cx + s * 0.25F, cy - s * 0.8F, Math.max(0.7F, s * 0.14F), color);
			}
			default -> {
				// A little scroll: paper between two rods.
				Brush.rect(g, cx - s, cy - s, cx + s, cy - s + 1.5F, color);
				Brush.rect(g, cx - s, cy + s - 1.5F, cx + s, cy + s, color);
				Brush.rect(g, cx - s * 0.75F, cy - s + 1.5F, cx + s * 0.75F, cy + s - 1.5F, Palette.fade(color, 0.45F));
				Brush.rect(g, cx - s * 0.45F, cy - s * 0.3F, cx + s * 0.45F, cy - s * 0.3F + 0.8F, color);
				Brush.rect(g, cx - s * 0.45F, cy + s * 0.15F, cx + s * 0.3F, cy + s * 0.15F + 0.8F, color);
			}
		}
	}

	/** A padlock (locked techniques). */
	public static void lock(GuiGraphicsExtractor g, float cx, float cy, float size, int color) {
		float s = size / 2.0F;
		Brush.arc(g, cx, cy - s * 0.15F, s * 0.38F, s * 0.6F, 0.75F, 1.25F, color);
		Brush.rect(g, cx - s * 0.6F, cy - s * 0.2F, cx - s * 0.38F, cy + s * 0.1F, color);
		Brush.rect(g, cx + s * 0.38F, cy - s * 0.2F, cx + s * 0.6F, cy + s * 0.1F, color);
		Brush.rect(g, cx - s * 0.8F, cy + s * 0.05F, cx + s * 0.8F, cy + s, color);
		Brush.rect(g, cx - s * 0.12F, cy + s * 0.35F, cx + s * 0.12F, cy + s * 0.7F, Palette.darken(color, 0.55F));
	}

	/** A technique's mark: its Dao's trigram, or a qi swirl (ring and core) for universal techniques. */
	public static void technique(GuiGraphicsExtractor g, Technique technique, float cx, float cy, float size, int color) {
		Dao dao = technique.dao();
		if (dao == null) {
			float s = size / 2.0F;
			Brush.ring(g, cx, cy, s * 0.55F, s * 0.85F, color);
			Brush.disc(g, cx, cy, s * 0.25F, color);
			Brush.rect(g, cx + s * 0.55F, cy - s * 0.95F, cx + s * 0.95F, cy - s * 0.55F, color);
			return;
		}
		trigram(g, cx, cy, size, Math.max(1.0F, size * 0.15F), size * 0.3F, trigramOf(dao), color);
	}

	/** A spec's glyph (DESIGN 22.12.2): its Dao's trigram (a qi swirl for universal arts) with its type mark at the lower right. */
	public static void spec(GuiGraphicsExtractor g, TechniqueSpec spec, float cx, float cy, float size, int color) {
		Dao dao = spec.dao();
		float main = size * 0.86F;
		float mx = cx - size * 0.08F;
		float my = cy - size * 0.06F;
		if (dao == null) {
			float s = main / 2.0F;
			Brush.ring(g, mx, my, s * 0.55F, s * 0.85F, color);
			Brush.disc(g, mx, my, s * 0.25F, color);
			Brush.rect(g, mx + s * 0.55F, my - s * 0.95F, mx + s * 0.95F, my - s * 0.55F, color);
		} else {
			trigram(g, mx, my, main, Math.max(1.0F, main * 0.15F), main * 0.3F, trigramOf(dao), color);
		}
		typeMark(g, spec.type(), cx + size * 0.46F, cy + size * 0.42F, Math.max(2.5F, size * 0.32F), color);
	}

	/** A small mark per technique type (SERANG arrow, AREA ring, KENDALI cross, GERAK chevron, LINDUNG shield, PULIH plus...). */
	public static void typeMark(GuiGraphicsExtractor g, TechniqueType type, float cx, float cy, float size, int color) {
		float s = size / 2.0F;
		float t = Math.max(0.6F, size * 0.14F);
		switch (type) {
			case SERANG -> {
				Brush.line(g, cx - s, cy + s, cx + s, cy - s, t, color);
				Brush.rect(g, cx + s * 0.2F, cy - s, cx + s, cy - s + t, color);
				Brush.rect(g, cx + s - t, cy - s, cx + s, cy - s * 0.2F, color);
			}
			case AREA -> Brush.ring(g, cx, cy, s * 0.55F, s, color);
			case KENDALI -> {
				Brush.line(g, cx - s, cy - s, cx + s, cy + s, t, color);
				Brush.line(g, cx - s, cy + s, cx + s, cy - s, t, color);
			}
			case GERAK -> {
				Brush.line(g, cx - s * 0.6F, cy - s, cx + s * 0.4F, cy, t, color);
				Brush.line(g, cx + s * 0.4F, cy, cx - s * 0.6F, cy + s, t, color);
			}
			case LINDUNG -> {
				Brush.rect(g, cx - s * 0.8F, cy - s, cx + s * 0.8F, cy + s * 0.2F, color);
				Brush.line(g, cx - s * 0.8F, cy + s * 0.2F, cx, cy + s, t * 1.4F, color);
				Brush.line(g, cx + s * 0.8F, cy + s * 0.2F, cx, cy + s, t * 1.4F, color);
			}
			case PULIH -> {
				Brush.rect(g, cx - s, cy - t / 2.0F, cx + s, cy + t / 2.0F, color);
				Brush.rect(g, cx - t / 2.0F, cy - s, cx + t / 2.0F, cy + s, color);
			}
			case PANGGIL -> {
				Brush.dot(g, cx - s * 0.5F, cy, Math.max(0.7F, s * 0.4F), color);
				Brush.dot(g, cx + s * 0.5F, cy, Math.max(0.7F, s * 0.4F), color);
			}
			case WILAYAH -> {
				Brush.rect(g, cx - s, cy - s, cx + s, cy - s + t, color);
				Brush.rect(g, cx - s, cy + s - t, cx + s, cy + s, color);
				Brush.rect(g, cx - s, cy - s, cx - s + t, cy + s, color);
				Brush.rect(g, cx + s - t, cy - s, cx + s, cy + s, color);
			}
			case UBAH -> {
				g.pose().pushMatrix();
				g.pose().translate(cx, cy);
				g.pose().rotate((float) (Math.PI / 4.0D));
				Brush.rect(g, -s * 0.7F, -s * 0.7F, s * 0.7F, s * 0.7F, color);
				g.pose().popMatrix();
			}
			case PASIF -> Brush.dot(g, cx, cy, Math.max(0.7F, s * 0.5F), color);
			case PAMUNGKAS -> {
				Brush.rect(g, cx - s, cy - t / 2.0F, cx + s, cy + t / 2.0F, color);
				Brush.rect(g, cx - t / 2.0F, cy - s, cx + t / 2.0F, cy + s, color);
				Brush.line(g, cx - s * 0.7F, cy - s * 0.7F, cx + s * 0.7F, cy + s * 0.7F, t, color);
				Brush.line(g, cx - s * 0.7F, cy + s * 0.7F, cx + s * 0.7F, cy - s * 0.7F, t, color);
			}
			case INDRA -> {
				Brush.ring(g, cx, cy, s * 0.6F, s, color);
				Brush.dot(g, cx, cy, Math.max(0.6F, s * 0.3F), color);
			}
		}
	}

	private Glyphs() {
	}
}
