package dev.kultivator.client.hall;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.core.ClientAsset;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/** Small drawing helpers shared by the hall pages (jade-scroll style, DESIGN 5b). */
final class HallUi {

	/** The nine default characters, in the order of {@code DiscipleEntity.SKIN_COUNT}. */
	private static final String[] CHARACTERS = {"alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri"};
	/** Robe trim colour per sect rank (as {@code SectRanks.TRIM}). */
	static final int[] TRIM = {0x9A9A9A, 0xEDEDED, 0x4A8CFF, 0x3FC48A, 0xA06CFF, 0xFFE9A8};
	static final String[] RANK_ID = {"tercatat", "luar", "dalam", "inti", "tetua", "tetua_agung"};
	/** Soul colours (as {@code Talents.COLOUR_RGB}). */
	static final int[] COLOUR = {0xE0342C, 0xF08A24, 0xF2D43A, 0x4FCB5A, 0x3FD6D0, 0x4C8CFF, 0x6A4CE0};
	static final String[] TALENT_ID = {"hati_gigih", "darah_dewa_kuno", "bakat_pil", "jari_penempa_roh", "takut_mati", "indra_pedang"};
	static final String[] PHYSIQUE_ID = {"racun_malapetaka", "naga_taixu", "dou_di", "tubuh_emas", "simbol_melahap", "tulang_mahatinggi",
			"pupil_ganda", "nadi_dewa_sesat", "tanpa_atribut"};
	static final String[] SPECIAL_ID = {"pengemis", "regresor", "dantian_tersumbat"};

	static Component name(CompoundTag tag, String key) {
		return tag.read(key, ComponentSerialization.CODEC).orElse(Component.translatable("entity.kultivator.disciple"));
	}

	static MutableComponent rankName(int rank) {
		int index = Math.max(0, Math.min(5, rank));
		return Component.translatable("kultivator.hall.rank." + RANK_ID[index]).withColor(TRIM[index]);
	}

	static MutableComponent colourName(int colour) {
		int index = Math.max(0, Math.min(6, colour));
		return Component.translatable("kultivator.hall.colour." + index).withColor(COLOUR[index]);
	}

	static MutableComponent realmLine(CompoundTag entry) {
		Rank rank = new Rank(Realm.byOrdinal(entry.getIntOr("realm", 0)), entry.getIntOr("layer", 0), entry.getIntOr("stage", 0));
		return rank.displayName().withColor(rank.realm().color());
	}

	/** Ticks as real time "h:mm:ss". */
	static String clock(long ticks) {
		long seconds = Math.max(0L, ticks) / 20L;
		return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", seconds / 3600L, seconds / 60L % 60L, seconds % 60L);
	}

	/** The face of default character {@code skin} (wide or slim) at {@code size} pixels. */
	static void face(GuiGraphicsExtractor g, int skin, boolean slim, int x, int y, int size) {
		String character = CHARACTERS[Math.floorMod(skin, CHARACTERS.length)];
		Identifier texture = new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace("entity/player/" + (slim ? "slim" : "wide") + "/"
				+ character)).texturePath();
		g.fill(x - 1, y - 1, x + size + 1, y + size + 1, Palette.argb(0xC0, Palette.GOLD_DARK));
		PlayerFaceExtractor.extractRenderState(g, texture, x, y, size, true, false, -1);
	}

	/** A labelled bar with its value on the right; returns the height used. */
	static float bar(GuiGraphicsExtractor g, Font font, Component name, Component value, float fraction, int color, int light, float x0, float top,
			float x1, float scale) {
		float valueW = Math.min((x1 - x0) * 0.55F, font.width(value) * scale);
		Brush.fit(g, font, name, x0, top, x1 - x0 - valueW - 3.0F, Palette.INK_SOFT, scale, 0.45F, false);
		Brush.fit(g, font, value, x1 - valueW, top, valueW, Palette.onPaper(color), scale, 0.45F, false);
		float by = top + 8.0F * scale + 1.0F;
		Brush.rect(g, x0, by, x1, by + 4.0F, Palette.mix(Palette.PAPER_BOTTOM, Palette.INK, 0.25F));
		Brush.rect(g, x0 + 0.5F, by + 0.5F, x1 - 0.5F, by + 3.5F, Palette.mix(Palette.PAPER_BOTTOM, color, 0.15F));
		float fill = x0 + 0.5F + (x1 - x0 - 1.0F) * Math.max(0.0F, Math.min(1.0F, fraction));
		if (fill > x0 + 0.6F) {
			Brush.rect(g, x0 + 0.5F, by + 0.5F, fill, by + 3.5F, color);
			Brush.rect(g, x0 + 0.5F, by + 0.5F, fill, by + 1.3F, light);
		}
		return 8.0F * scale + 6.0F;
	}

	/** A small jade tick or red cross. */
	static void mark(GuiGraphicsExtractor g, float x, float y, boolean ok) {
		if (ok) {
			Brush.line(g, x, y + 2.5F, x + 1.6F, y + 4.2F, 1.1F, Palette.JADE);
			Brush.line(g, x + 1.6F, y + 4.2F, x + 4.5F, y + 0.6F, 1.1F, Palette.JADE);
		} else {
			Brush.line(g, x + 0.5F, y + 0.5F, x + 4.0F, y + 4.0F, 1.0F, Palette.DANGER);
			Brush.line(g, x + 4.0F, y + 0.5F, x + 0.5F, y + 4.0F, 1.0F, Palette.DANGER);
		}
	}

	/** A round red seal stamp with a short word in it (verdicts). */
	static void stamp(GuiGraphicsExtractor g, Font font, Component word, float cx, float cy, float r, int color) {
		Brush.ring(g, cx, cy, r - 1.2F, r, Palette.argb(0xD8, color));
		Brush.ring(g, cx, cy, r - 2.6F, r - 2.0F, Palette.argb(0x90, color));
		float scale = Brush.fitScale(font, word, r * 1.5F, 0.6F, 0.35F);
		Brush.centered(g, font, word, cx, cy - 4.0F * scale, Palette.argb(0xE0, color), scale, false);
	}

	/** One line of text shrunk to fit; returns the line height. */
	static float line(GuiGraphicsExtractor g, Font font, Component text, float x, float y, float width, int color, float scale) {
		Brush.fit(g, font, text, x, y, width, color, scale, Math.min(scale, 0.45F), false);
		return 9.0F * scale;
	}

	private HallUi() {
	}
}
