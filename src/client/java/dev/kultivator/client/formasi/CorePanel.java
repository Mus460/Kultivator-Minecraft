package dev.kultivator.client.formasi;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.FormationSpec;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * The Inti sub of Sekte › Formasi (DESIGN 24.7.1): a family filter talisman (click forward, right-click back) and a
 * scrolling list of rows: family seal, name, state, a thin Daya bar and the hours left with the "Siaga" / "Retak" tags. A
 * click opens the core's slip. The legacy Formasi Pelindung and Formasi Agung Sekte rows are read-only and point to their
 * lodestone. Text is 1.0 (43.0.5).
 */
final class CorePanel {

	static final int ROW_H = 26;
	private static int filter = -1;

	static void draw(FormasiPage page, GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
		Font font = page.font();
		Family[] families = Family.values();
		Component filterName = filter < 0 ? Component.translatable("kultivator.formasiui.filter.all") : families[filter].label();
		Component filterLabel = Component.translatable("kultivator.gui43.m07.keluarga", filterName);
		int filterW = Math.min(w - 4, Math.max(90, font.width(filterLabel) + 26));
		page.talisman(g, x, y + 1, filterW, FormasiPage.BUTTON_H, filterLabel, true, () -> filter = filter + 1 >= families.length ? -1 : filter + 1,
				mouseX, mouseY, List.of(filterLabel, Component.translatable("kultivator.gui43.m07.keluarga.tip")));
		int top = y + FormasiPage.BUTTON_H + 4;
		int view = h - (top - y);
		List<CompoundTag> rows = new ArrayList<>();
		for (Tag tag : page.f().getListOrEmpty("cores")) {
			if (tag instanceof CompoundTag core) {
				FormationSpec spec = FormationRegistry.byId(core.getStringOr("spec", ""));
				if (filter < 0 || spec != null && spec.family().ordinal() == filter) {
					rows.add(core);
				}
			}
		}
		List<CompoundTag> legacy = new ArrayList<>();
		if (filter < 0 || filter == Family.PELINDUNG.ordinal()) {
			for (Tag tag : page.legacy()) {
				if (tag instanceof CompoundTag row) {
					legacy.add(row);
				}
			}
		}
		float content = (rows.size() + legacy.size()) * ROW_H + 2.0F;
		float offset = page.clampScroll(0, content, view);
		page.scrollArea(x, top, x + w, top + view);
		page.klip(x, top, x + w, top + view);
		float cursor = top - offset;
		if (rows.isEmpty() && legacy.isEmpty()) {
			page.wrap(Component.translatable("kultivator.formasiui.cores.empty"), x + 4.0F, top + 4.0F, w - 8.0F, Palette.INK_SOFT);
		}
		for (CompoundTag core : rows) {
			if (cursor + ROW_H >= top && cursor <= top + view) {
				row(page, g, core, x, cursor, w, mouseX, mouseY, top, top + view);
			}
			cursor += ROW_H;
		}
		for (CompoundTag row : legacy) {
			if (cursor + ROW_H >= top && cursor <= top + view) {
				legacyRow(page, g, row, x, cursor, w);
			}
			cursor += ROW_H;
		}
		page.lepasKlip();
		FormasiPage.bead(g, x + w, top, top + view, offset, Math.max(0.0F, content - view));
	}

	private static void row(FormasiPage page, GuiGraphicsExtractor g, CompoundTag core, int x, float ry, int w, int mouseX, int mouseY,
			int clipTop, int clipBottom) {
		Font font = page.font();
		FormationSpec spec = FormationRegistry.byId(core.getStringOr("spec", ""));
		int id = core.getIntOr("id", -1);
		int state = core.getIntOr("st", 0);
		boolean over = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW_H - 1 && mouseY >= clipTop && mouseY < clipBottom;
		g.fill(x, Math.round(ry), x + w - 3, Math.round(ry + ROW_H - 1), Palette.argb(over ? 0x50 : 0x28, Palette.PAPER_DEEP));
		int familyColor = spec == null ? Palette.LOCKED : Palette.opaque(spec.family().color());
		// Family seal.
		Brush.rect(g, x + 2.0F, ry + 4.0F, x + 18.0F, ry + 20.0F, Palette.mix(familyColor, Palette.INK, 0.2F));
		Brush.frame(g, x + 2, Math.round(ry + 4), x + 18, Math.round(ry + 20), Palette.argb(0xC0, Palette.GOLD));
		if (spec != null) {
			Brush.centered(g, font, Component.literal(spec.family().label().getString().substring(0, 1)), x + 10.0F, ry + 8.0F, 0xFFFFF4D6, 1.0F, false);
		}
		// Name, grade ribbon and state.
		float nameX = x + 22.0F;
		float right = x + w - 6.0F;
		Component stateName = FormasiPage.stateName(state);
		int stateW = font.width(stateName);
		float sealR = 4.0F;
		float stateX = right - stateW;
		FormasiPage.seal(g, font, stateName, stateX - sealR - 3.0F, ry + 6.0F, sealR, FormasiPage.stateColor(state));
		page.line(stateName, stateX, ry + 2.0F, stateW + 1.0F, Palette.onPaper(FormasiPage.stateColor(state) & 0xFFFFFF));
		float nameRight = stateX - 2.0F * sealR - 10.0F;
		Component name = spec == null ? Component.translatable("kultivator.formasiui.unknown_spec", core.getStringOr("spec", "?")) : spec.name();
		page.line(name, nameX, ry + 2.0F, nameRight - nameX - 8.0F, Palette.INK);
		if (spec != null) {
			Brush.rect(g, nameRight - 6.0F, ry + 3.0F, nameRight, ry + 8.0F, Palette.opaque(spec.grade().color()));
		}
		// Daya bar and hours left.
		long daya = core.getLongOr("daya", 0L);
		long cap = Math.max(1L, core.getLongOr("cap", 1L));
		FormasiPage.bar(g, nameX, ry + 12.0F, right - nameX, 3.0F, daya / (float) cap, Palette.QI);
		MutableComponent hours = Component.translatable("kultivator.formasiui.cores.hours", FormasiPage.hours(core.getDoubleOr("hrs", 0.0D))).copy();
		if (core.getBooleanOr("crack", false)) {
			hours.append(" · ").append(Component.translatable("kultivator.formasiui.tag.crack").copy().withColor(Palette.DANGER & 0xFFFFFF));
		}
		if (core.getBooleanOr("idle", false)) {
			hours.append(" · ").append(Component.translatable("kultivator.formasiui.tag.idle"));
		}
		page.line(hours, nameX, ry + 16.0F, right - nameX, Palette.INK_SOFT);
		List<Component> tip = new ArrayList<>();
		tip.add(name);
		tip.add(stateName.copy().withColor(FormasiPage.stateColor(state) & 0xFFFFFF));
		Component why = FormasiPage.text(core, "why");
		if (!why.getString().isEmpty()) {
			tip.add(why);
		}
		tip.add(Component.translatable("kultivator.formasiui.cores.at", core.getIntOr("x", 0), core.getIntOr("y", 0), core.getIntOr("z", 0)));
		if (ry + ROW_H > clipTop && ry < clipBottom) {
			page.hit(x, ry, x + w - 3, ry + ROW_H - 1, () -> page.openCoreSlip(id), tip);
		}
	}

	private static void legacyRow(FormasiPage page, GuiGraphicsExtractor g, CompoundTag row, int x, float ry, int w) {
		Font font = page.font();
		g.fill(x, Math.round(ry), x + w - 3, Math.round(ry + ROW_H - 1), Palette.argb(0x18, Palette.PAPER_DEEP));
		boolean agung = row.getStringOr("kind", "").contains("agung");
		Brush.rect(g, x + 2.0F, ry + 4.0F, x + 18.0F, ry + 20.0F, Palette.mix(Palette.JADE, Palette.INK, 0.2F));
		Brush.centered(g, font, Component.literal(agung ? "A" : "P"), x + 10.0F, ry + 8.0F, 0xFFFFF4D6, 1.0F, false);
		Component name = Component.translatable(agung ? "kultivator.formasiui.legacy.agung" : "kultivator.formasiui.legacy.pelindung");
		page.line(name, x + 22.0F, ry + 2.0F, w - 28.0F, Palette.INK_SOFT);
		Component line = Component.translatable("kultivator.formasiui.legacy.line", row.getBooleanOr("on", false)
						? Component.translatable("kultivator.formasiui.on") : Component.translatable("kultivator.formasiui.off"),
				row.getIntOr("layers", 1), Math.round(row.getDoubleOr("r", 16.0D)), row.getIntOr("x", 0), row.getIntOr("y", 0), row.getIntOr("z", 0));
		page.line(line, x + 22.0F, ry + 13.0F, w - 28.0F, Palette.INK_FAINT);
	}

	private CorePanel() {
	}
}
