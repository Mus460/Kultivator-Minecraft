package dev.kultivator.client.screen.ui;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
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

	private Glyphs() {
	}
}
