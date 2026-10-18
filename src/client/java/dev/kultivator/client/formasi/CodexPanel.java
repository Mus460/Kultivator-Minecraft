package dev.kultivator.client.formasi;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.spec.Flag;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Grade;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Kitab panel (DESIGN 24.7.1): a seal-card grid of every formation spec grouped by grade; it doubles as the
 * formation codex. Locked cards show the hall level they need ("Aula Tingkat 5") or the formation they need ("Butuh:
 * ..."). A click opens the slip in Kitab mode. Legacy mirrors are listed too (read-only).
 */
final class CodexPanel {

	static final int CARD_W = 52;
	static final int CARD_H = 30;
	static final int GAP = 3;

	static void draw(FormasiPage page, GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
		Font font = page.font();
		int columns = Math.max(1, (w - 4 + GAP) / (CARD_W + GAP));
		int cardW = (w - 4 - GAP * (columns - 1)) / columns;
		// Measure.
		float content = 0.0F;
		for (Grade grade : Grade.values()) {
			int count = specs(grade).size();
			if (count > 0) {
				content += 12.0F + (float) Math.ceil(count / (double) columns) * (CARD_H + GAP);
			}
		}
		float offset = page.clampScroll(1, content, h);
		float cursor = y - offset;
		for (Grade grade : Grade.values()) {
			List<FormationSpec> specs = specs(grade);
			if (specs.isEmpty()) {
				continue;
			}
			if (cursor + 12.0F >= y && cursor <= y + h) {
				Brush.rect(g, x + 1.0F, cursor + 2.0F, x + 5.0F, cursor + 7.0F, Palette.opaque(grade.color()));
				Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.formasiui.codex.grade", grade.label(), grade.gate())), x + 8.0F, cursor,
						w - 10.0F, Palette.SEAL_DARK, 0.62F, 0.4F, false);
			}
			cursor += 12.0F;
			for (int i = 0; i < specs.size(); i++) {
				int col = i % columns;
				float cx = x + 2.0F + col * (cardW + GAP);
				float cy = cursor + (i / columns) * (CARD_H + GAP);
				if (cy + CARD_H >= y && cy <= y + h) {
					card(page, g, font, specs.get(i), cx, cy, cardW, mouseX, mouseY, y, y + h);
				}
			}
			cursor += (float) Math.ceil(specs.size() / (double) columns) * (CARD_H + GAP);
		}
		FormasiPage.bead(g, x + w, y, y + h, offset, Math.max(0.0F, content - h));
	}

	private static List<FormationSpec> specs(Grade grade) {
		List<FormationSpec> out = new ArrayList<>();
		for (FormationSpec spec : FormationRegistry.all()) {
			if (spec.grade() == grade) {
				out.add(spec);
			}
		}
		return out;
	}

	/** Why {@code spec} is locked here, or null. */
	static @Nullable Component lock(FormasiPage page, FormationSpec spec) {
		if (spec.has(Flag.LEGACY_MIRROR)) {
			return null;
		}
		if (page.hallLevel() < spec.minHallLevel()) {
			return Component.translatable("kultivator.formasiui.codex.need_hall", spec.minHallLevel());
		}
		for (String need : spec.requires()) {
			if (need.startsWith("family:")) {
				continue;
			}
			FormationSpec other = FormationRegistry.byId(need);
			if (other != null && !page.running(need)) {
				return Component.translatable("kultivator.formasiui.codex.need", other.name());
			}
		}
		return null;
	}

	private static void card(FormasiPage page, GuiGraphicsExtractor g, Font font, FormationSpec spec, float cx, float cy, int cardW, int mouseX,
			int mouseY, int clipTop, int clipBottom) {
		Component locked = lock(page, spec);
		boolean over = mouseX >= cx && mouseX < cx + cardW && mouseY >= cy && mouseY < cy + CARD_H && mouseY >= clipTop && mouseY < clipBottom;
		int frame = Palette.opaque(spec.family().color());
		int x0 = Math.round(cx);
		int y0 = Math.round(cy);
		int x1 = x0 + cardW;
		int y1 = y0 + CARD_H;
		g.fill(x0 + 1, y0 + 2, x1 + 1, y1 + 1, 0x40000000);
		g.fill(x0, y0, x1, y1, locked == null ? Palette.mix(frame, Palette.INK, 0.15F) : Palette.LOCKED);
		g.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, locked == null ? (over ? 0xFFFFF8D8 : 0xFFF4E6B8) : Palette.LOCKED_PAPER);
		// The grade ribbon in the corner.
		int ribbon = Palette.opaque(spec.grade().color());
		for (int i = 0; i < 6; i++) {
			g.fill(x1 - 6 + i, y0 + 2 + i, x1 - 2, y0 + 3 + i, locked == null ? ribbon : Palette.grey(ribbon, 0.6F));
		}
		Brush.rect(g, x0 + 4.0F, y0 + 4.0F, x0 + 7.0F, y0 + 7.0F, locked == null ? frame : Palette.INK_FAINT);
		Brush.fit(g, font, spec.family().label(), x0 + 9.0F, y0 + 3.5F, cardW - 16.0F, locked == null ? Palette.onPaper(frame & 0xFFFFFF) : Palette.INK_FAINT,
				0.42F, 0.3F, false);
		float textW = cardW - 6.0F;
		Brush.wrap(g, font, spec.name(), x0 + 3.0F, y0 + 10.0F, textW, locked == null ? Palette.INK : Palette.INK_SOFT, 0.45F, false);
		if (locked != null) {
			Glyphs.lock(g, x1 - 7.0F, y1 - 7.0F, 6.0F, Palette.INK_SOFT);
		}
		List<Component> tip = new ArrayList<>();
		tip.add(spec.name());
		tip.add(Component.translatable("kultivator.formasiui.codex.card_line", spec.family().label(), spec.grade().label(),
				Math.round(spec.radiusAt(page.hallLevel())), spec.pattern().size()));
		if (locked != null) {
			tip.add(locked.copy().withColor(0xFF7A60));
		}
		if (cy + CARD_H > clipTop && cy < clipBottom) {
			page.hit(cx, Math.max(cy, clipTop), cx + cardW, Math.min(cy + CARD_H, clipBottom), () -> page.openKitabSlip(spec), tip);
		}
	}

	private CodexPanel() {
	}
}
