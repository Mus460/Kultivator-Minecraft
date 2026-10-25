package dev.kultivator.client.hall;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
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

	private HallUi() {
	}
}
