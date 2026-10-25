package dev.kultivator.client.formasi;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.spec.Flag;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Grade;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Kitab sub of Sekte › Formasi (DESIGN 24.7.1, 43.11 G46): a seal-card grid of every formation spec under one heading
 * per grade, drawn by the kit's single list ({@link DaftarGulir} in grid mode: wheel, scroll bar, arrows, Enter); it doubles
 * as the formation codex. Locked cards show a lock and the hall level or formation they need in the tooltip. A click on a
 * card (or Enter on the highlighted one) opens the slip in Kitab mode. Legacy mirrors are listed too (read-only). Text is
 * 1.0 (43.0.5): the family on the first line, the name on up to two lines.
 */
final class CodexPanel {

	static final int CARD_W = 84;
	static final int CARD_H = 34;
	static final int GAP = 3;
	static final int HEAD = 14;
	private static final DaftarGulir GRID = new DaftarGulir();

	static void draw(FormasiPage page, GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, Lukis l) {
		List<Object> entries = new ArrayList<>();
		for (Grade grade : Grade.values()) {
			List<FormationSpec> specs = specs(grade);
			if (!specs.isEmpty()) {
				entries.add(grade);
				entries.addAll(specs);
			}
		}
		GRID.judul(i -> i >= 0 && i < entries.size() && entries.get(i) instanceof Grade, HEAD);
		GRID.gambarKisi(l, "formasi:kitab", x, y, x + w, y + h, entries.size(), CARD_W + GAP, CARD_H + GAP, (lk, i, x0, y0, x1, y1, over, chosen) -> {
			Object entry = entries.get(i);
			if (entry instanceof Grade grade) {
				Brush.rect(lk.g, x0 + 1.0F, y0 + 3.0F, x0 + 5.0F, y0 + 8.0F, Palette.opaque(grade.color()));
				page.line(Brush.bold(Component.translatable("kultivator.formasiui.codex.grade", grade.label(), grade.gate())), x0 + 8.0F, y0 + 1.0F,
						x1 - x0 - 10.0F, Palette.SEAL_DARK);
			} else if (entry instanceof FormationSpec spec) {
				card(page, lk, spec, x0, y0, x1 - x0, Math.min(CARD_H, y1 - y0), over, chosen, "formasi:kitab:kartu:" + i);
			}
		}, () -> {
			int i = GRID.pilihan();
			if (i >= 0 && i < entries.size() && entries.get(i) instanceof FormationSpec spec) {
				page.openKitabSlip(spec);
			}
		}, null);
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

	private static void card(FormasiPage page, Lukis l, FormationSpec spec, int x0, int y0, int cardW, int cardH, boolean over, boolean chosen,
			String key) {
		GuiGraphicsExtractor g = l.g;
		Component locked = lock(page, spec);
		int frame = Palette.opaque(spec.family().color());
		int x1 = x0 + cardW;
		int y1 = y0 + cardH;
		g.fill(x0 + 1, y0 + 2, x1 + 1, y1 + 1, 0x40000000);
		g.fill(x0, y0, x1, y1, chosen ? Palette.GOLD : locked == null ? Palette.mix(frame, Palette.INK, 0.15F) : Palette.LOCKED);
		g.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, locked == null ? (over ? 0xFFFFF8D8 : 0xFFF4E6B8) : Palette.LOCKED_PAPER);
		// The grade ribbon in the corner.
		int ribbon = Palette.opaque(spec.grade().color());
		for (int i = 0; i < 6; i++) {
			g.fill(x1 - 6 + i, y0 + 2 + i, x1 - 2, y0 + 3 + i, locked == null ? ribbon : Palette.grey(ribbon, 0.6F));
		}
		Brush.rect(g, x0 + 4.0F, y0 + 5.0F, x0 + 7.0F, y0 + 8.0F, locked == null ? frame : Palette.INK_FAINT);
		page.line(spec.family().label(), x0 + 9.0F, y0 + 3.0F, cardW - 17.0F, locked == null ? Palette.onPaper(frame & 0xFFFFFF) : Palette.INK_FAINT);
		page.wrap(spec.name(), x0 + 4.0F, y0 + 13.0F, cardW - 8.0F, locked == null ? Palette.INK : Palette.INK_SOFT, 18.0F);
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
		// One click opens the slip (the list's own select-then-open stays for the keyboard: arrows, Enter).
		l.zona(key, x0, y0, x1, y1, () -> page.openKitabSlip(spec), true, null, false);
		if (over) {
			l.tip(tip);
		}
	}

	private CodexPanel() {
	}
}
