package dev.kultivator.client.sekte.tetua;

import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.screen.Letterbox;
import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
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
 * The morning report card (DESIGN 36.2.6 "Laporan Pagi"): a rice-paper register unrolls at the upper left between two
 * lacquer rods, the vermilion seal 報 at its head, the day and who brought it, then the night's news grouped by module
 * (each group under a small coloured tab, at most 12 lines), and the day's totals at its foot (disciples present, the
 * restless, garden and mine). It waits for a title card or a film to clear, stays about 10-20 s by its length, and rolls
 * up again. Drawn as a HUD element (hidden with F1 and while a recording hides the HUD); the Balai Tetua shows the same
 * report in full. Client thread only.
 */
public final class LaporanCard implements HudElement {

	static final Identifier PAPER = Kultivator.id("textures/sekte/tetua/kertas.png");
	static final Identifier SEAL = Kultivator.id("textures/sekte/tetua/segel.png");
	private static final int W = 214;
	private static final int UNROLL = 12;
	private static final int WAIT_MAX = 80;

	/** One drawn row: a group tab or a line. */
	record Row(boolean tab, Component text, int rgb) {
	}

	private static @Nullable CompoundTag report;
	private static final List<Row> ROWS = new ArrayList<>();
	private static long ticks;
	private static long arrivedAt;
	private static long shownAt = -1L;
	private static int stay;

	static void show(CompoundTag p) {
		report = p;
		ROWS.clear();
		ROWS.addAll(rowsOf(p));
		arrivedAt = ticks;
		shownAt = -1L;
		stay = Mth.clamp(200 + ROWS.size() * 14, 200, 420);
	}

	static void tick() {
		ticks++;
		if (report == null) {
			return;
		}
		if (shownAt < 0L) {
			boolean busy = TitleCard.busy() || Letterbox.amount() > 0.05F;
			if (!busy || ticks - arrivedAt > WAIT_MAX) {
				shownAt = ticks;
			}
			return;
		}
		if (Minecraft.getInstance().gui.screen() != null && ticks - shownAt > UNROLL) {
			// hidden under an open screen: the card waits instead of running out unseen
			shownAt++;
			return;
		}
		if (ticks - shownAt > stay + UNROLL) {
			report = null;
			ROWS.clear();
		}
	}

	static void clear() {
		report = null;
		ROWS.clear();
		shownAt = -1L;
	}

	/** True while the card is up (the screen does not open a second copy). */
	static boolean showing() {
		return report != null;
	}

	static void dismiss() {
		if (report != null && shownAt >= 0L) {
			shownAt = Math.min(shownAt, ticks - stay);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		CompoundTag p = report;
		if (p == null || shownAt < 0L || minecraft.player == null || minecraft.gui.hud.isHidden() || EpicDirector.recordHide()
				|| minecraft.gui.screen() != null) {
			return;
		}
		float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
		float age = ticks - shownAt + partial;
		float in = Mth.clamp(age / UNROLL, 0.0F, 1.0F);
		float out = Mth.clamp((age - stay) / UNROLL, 0.0F, 1.0F);
		float open = ease(in) * (1.0F - ease(out));
		if (open <= 0.01F) {
			return;
		}
		draw(g, minecraft.font, p, ROWS, 8, Math.round(g.guiHeight() * 0.16F), Math.min(W, g.guiWidth() - 16), g.guiHeight(), open);
	}

	/** The card itself (also used by the Balai Tetua's report page with {@code open} 1). Returns the full height. */
	static int draw(GuiGraphicsExtractor g, Font font, CompoundTag p, List<Row> rows, int x0, int y0, int w, int screenH, float open) {
		int lineH = 8;
		int full = 34 + rows.size() * lineH + 18;
		full = Math.min(full, screenH - y0 - 24);
		int h = Math.max(4, Math.round(full * open));
		int x1 = x0 + w;
		int y1 = y0 + h;
		// paper and shadow
		g.fill(x0 + 2, y0 + 3, x1 + 2, y1 + 3, Palette.argb(70, 0x000000));
		g.blit(RenderPipelines.GUI_TEXTURED, PAPER, x0, y0, 0.0F, 0.0F, w, h, 128, Math.max(1, Math.round(128 * open)), 128, 128, 0xF8FFFFFF);
		// the two rods
		rod(g, x0 - 3, x1 + 3, y0 - 2);
		rod(g, x0 - 3, x1 + 3, y1 - 1);
		if (open < 0.9F) {
			return full;
		}
		g.enableScissor(x0, y0, x1, y1);
		// the seal and the head
		seal(g, font, x0 + 15, y0 + 15, 22, "報");
		Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.sekte.tetua.card.title")), x0 + 30, y0 + 5, w - 36, Palette.INK, 0.95F, 0.6F, false);
		Component by = p.read("by", ComponentSerialization.CODEC).orElse(Component.empty());
		Component sub = p.getBooleanOr("brought", false)
				? Component.translatable("kultivator.sekte.tetua.card.brought", p.getLongOr("day", 0L), by)
				: Component.translatable("kultivator.sekte.tetua.card.day", p.getLongOr("day", 0L), by);
		Brush.fit(g, font, sub, x0 + 30, y0 + 16, w - 36, Palette.SEAL_DARK, 0.62F, 0.45F, false);
		Brush.rect(g, x0 + 6, y0 + 27, x1 - 6, y0 + 27.6F, Palette.argb(0xB0, Palette.GOLD_DARK));
		float y = y0 + 31;
		if (rows.isEmpty()) {
			Brush.fit(g, font, Component.translatable("kultivator.sekte.tetua.card.quiet"), x0 + 8, y, w - 16, Palette.INK_SOFT, 0.65F, 0.45F, false);
			y += lineH;
		}
		for (Row row : rows) {
			if (y > y1 - 22) {
				break;
			}
			if (row.tab()) {
				float tw = Math.min(w - 16, font.width(row.text()) * 0.6F + 8);
				Brush.chamfer(g, x0 + 7, Math.round(y), Math.round(x0 + 7 + tw), Math.round(y + 7), 2, Palette.argb(220, row.rgb()));
				Brush.fit(g, font, row.text(), x0 + 11, y + 1, tw - 6, Palette.argb(255, 0xFFFFFF), 0.6F, 0.42F, false);
			} else {
				Brush.dot(g, x0 + 10, y + 3.2F, 1.1F, Palette.argb(200, row.rgb()));
				Brush.fit(g, font, row.text(), x0 + 14, y, w - 22, Palette.INK, 0.6F, 0.42F, false);
			}
			y += lineH;
		}
		// the foot: the sect at a glance
		Brush.rect(g, x0 + 6, y1 - 15, x1 - 6, y1 - 14.4F, Palette.argb(0x90, Palette.GOLD_DARK));
		Component foot = Component.translatable("kultivator.sekte.tetua.card.foot", p.getIntOr("hadir", 0), p.getIntOr("gelisah", 0),
				p.getIntOr("jabatan", 0), p.getIntOr("kebun", 0), p.getIntOr("tambang", 0));
		Brush.fit(g, font, foot, x0 + 8, y1 - 12, w - 16, Palette.INK_SOFT, 0.58F, 0.42F, false);
		g.disableScissor();
		return full;
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

	private static void rod(GuiGraphicsExtractor g, int x0, int x1, int y) {
		g.fill(x0, y, x1, y + 3, Palette.ROD);
		g.fill(x0, y, x1, y + 1, Palette.ROD_LIGHT);
		g.fill(x0 - 2, y - 1, x0 + 1, y + 4, Palette.GOLD_DARK);
		g.fill(x1 - 1, y - 1, x1 + 2, y + 4, Palette.GOLD_DARK);
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

	private static float ease(float t) {
		float u = 1.0F - t;
		return 1.0F - u * u * u;
	}
}
