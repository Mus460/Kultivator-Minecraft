package dev.kultivator.client.sekte.tetua;

import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.screen.Letterbox;
import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.client.hud.Kabar;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The morning report (DESIGN 36.2.6 "Laporan Pagi"), shown as the long variant of the single notice slot {@link Kabar}
 * (DESIGN 43.9, G27): the vermilion seal 報, the day and who brought it, then the night's news grouped by module (each
 * group under its coloured name, as many lines as the screen allows) and the day's totals at its foot (disciples present,
 * the restless, garden and mine). It waits for a title card or a film to clear (at most 4 s) and stays about 10-20 s by
 * its length; {@link Kabar} stops its clock under an open screen. The Balai Tetua shows the same report in full
 * ({@link #rowsOf}, {@link #seal}). Client thread only.
 */
public final class LaporanCard {

	static final Identifier SEAL = Kultivator.id("textures/sekte/tetua/segel.png");
	private static final int W = 214;
	private static final int WAIT_MAX = 80;
	private static final String KUNCI = "laporan_pagi";

	/** One drawn row: a group tab or a line. */
	record Row(boolean tab, Component text, int rgb) {
	}

	private static @Nullable CompoundTag report;
	private static long ticks;
	private static long arrivedAt;

	static void show(CompoundTag p) {
		report = p;
		arrivedAt = ticks;
	}

	static void tick() {
		ticks++;
		CompoundTag p = report;
		if (p == null) {
			return;
		}
		boolean busy = TitleCard.busy() || Letterbox.amount() > 0.05F;
		if (busy && ticks - arrivedAt <= WAIT_MAX) {
			return;
		}
		report = null;
		List<Row> rows = rowsOf(p);
		Component by = p.read("by", ComponentSerialization.CODEC).orElse(Component.empty());
		Component sub = p.getBooleanOr("brought", false)
				? Component.translatable("kultivator.sekte.tetua.card.brought", p.getLongOr("day", 0L), by)
				: Component.translatable("kultivator.sekte.tetua.card.day", p.getLongOr("day", 0L), by);
		List<Component> lines = new ArrayList<>();
		if (rows.isEmpty()) {
			lines.add(Component.translatable("kultivator.sekte.tetua.card.quiet"));
		}
		for (Row row : rows) {
			lines.add(Component.empty().append(Component.literal(row.tab() ? "▌ " : "· ").withColor(row.rgb() & 0xFFFFFF))
					.append(row.tab() ? Brush.bold(row.text()) : row.text()));
		}
		lines.add(Component.translatable("kultivator.sekte.tetua.card.foot", p.getIntOr("hadir", 0), p.getIntOr("gelisah", 0),
				p.getIntOr("jabatan", 0), p.getIntOr("kebun", 0), p.getIntOr("tambang", 0)));
		int stay = Mth.clamp(200 + rows.size() * 14, 200, 420);
		Kabar.kirim(Kabar.kartu(Component.translatable("kultivator.sekte.tetua.card.title"), sub).ikon(Kabar.segel("報", Palette.SEAL))
				.baris(lines).lebar(W).lama(stay * 50L).kunci(KUNCI));
	}

	static void clear() {
		report = null;
		Kabar.tutup(KUNCI);
	}

	/** True while the report waits or shows (the screen does not open a second copy). */
	static boolean showing() {
		return report != null || Kabar.ada(KUNCI);
	}

	static void dismiss() {
		report = null;
		Kabar.tutup(KUNCI);
	}

	static List<Row> rowsOf(CompoundTag p) {
		List<Row> rows = new ArrayList<>();
		for (Tag tag : p.getListOrEmpty("groups")) {
			if (tag instanceof CompoundTag group) {
				String module = group.getStringOr("mod", "");
				int rgb = moduleColour(module);
				rows.add(new Row(true, moduleName(module), rgb));
				for (Tag l : group.getListOrEmpty("lines")) {
					if (l instanceof CompoundTag line) {
						rows.add(new Row(false, line.read("text", ComponentSerialization.CODEC).orElse(Component.empty()), rgb));
					}
				}
				int hidden = group.getIntOr("hidden", 0);
				if (hidden > 0) {
					rows.add(new Row(false, Component.translatable("kultivator.sekte.tetua.card.more", hidden).withStyle(st -> st.withItalic(true)), rgb));
				}
			}
		}
		return rows;
	}

	/** The vermilion seal with a white glyph. */
	static void seal(GuiGraphicsExtractor g, Font font, float cx, float cy, int size, String glyph) {
		int x = Math.round(cx - size / 2.0F);
		int y = Math.round(cy - size / 2.0F);
		g.blit(RenderPipelines.GUI_TEXTURED, SEAL, x, y, 0.0F, 0.0F, size, size, 32, 32, 32, 32, 0xFFFFFFFF);
		Component text = Component.literal(glyph);
		float s = size / 14.0F;
		g.pose().pushMatrix();
		g.pose().translate(cx - font.width(text) * s / 2.0F, cy - 4.0F * s);
		g.pose().scale(s, s);
		g.text(font, text, 0, 0, 0xFFFFF4E0, false);
		g.pose().popMatrix();
	}

	static Component moduleName(String module) {
		return Component.translatable("kultivator.sekte.tetua.modul." + (module == null || module.isEmpty() ? "lain" : module));
	}

	static int moduleColour(String module) {
		return switch (module == null ? "" : module) {
			case "kepribadian" -> 0xC8463C;
			case "hubungan" -> 0xD86AA0;
			case "turnamen" -> 0xE0842C;
			case "tangga" -> 0x8C7A5A;
			case "paviliun" -> 0x4A6CC8;
			case "tetua" -> 0xB08A2A;
			case "misi" -> 0x2E8963;
			case "seragam" -> 0x7A4AC8;
			case "diplomasi" -> 0x4A4A8C;
			case "kebun" -> 0x5A9A3A;
			default -> 0x6A6A6A;
		};
	}
}
