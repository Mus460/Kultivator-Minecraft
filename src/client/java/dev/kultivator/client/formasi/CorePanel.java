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

import java.util.ArrayList;
import java.util.List;

/**
 * The Inti panel of the Formasi tablet (DESIGN 24.7.1): a row of family filter seals and a scrolling list of 18 px
 * rows: family glyph, name, grade ribbon, state seal (with "Siaga" and "Retak" tags) and a thin Daya bar. A click opens
 * the core's slip. The legacy Formasi Pelindung and Formasi Agung Sekte rows are read-only and point to their lodestone.
 */
final class CorePanel {

	static final int ROW_H = 18;
	static final int FILTER = 10;
	private static int filter = -1;

	static void draw(FormasiPage page, GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
		Font font = page.font();
		// Family filter seals ("all" first).
		Family[] families = Family.values();
		float step = Math.min(FILTER + 2.0F, (w - 2.0F) / (families.length + 1));
		for (int i = -1; i < families.length; i++) {
			float fx = x + (i + 1) * step;
			int color = i < 0 ? Palette.GOLD : Palette.opaque(families[i].color());
			boolean active = filter == i;
			Brush.rect(g, fx, y + 1.0F, fx + step - 2.0F, y + FILTER, Palette.mix(color, Palette.INK, active ? 0.0F : 0.35F));
			if (active) {
				Brush.frame(g, Math.round(fx) - 1, y, Math.round(fx + step - 1.0F), y + FILTER + 1, Palette.SEAL);
			}
			Component mark = i < 0 ? Component.literal("∗") : Component.literal(families[i].label().getString().substring(0, 1));
			Brush.centered(g, font, mark, fx + (step - 2.0F) / 2.0F, y + 2.5F, 0xFFFFF4D6, 0.55F, false);
			int index = i;
			page.hit(fx, y, fx + step - 1.0F, y + FILTER + 1.0F, () -> filter = index,
					List.of(i < 0 ? Component.translatable("kultivator.formasiui.filter.all") : families[i].label()));
		}
		int top = y + FILTER + 4;
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
		g.enableScissor(x, top, x + w, top + view);
		float cursor = top - offset;
		if (rows.isEmpty() && legacy.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.formasiui.cores.empty"), x + 4.0F, top + 4.0F, w - 8.0F, Palette.INK_SOFT, 0.62F, false);
		}
		for (CompoundTag core : rows) {
			if (cursor + ROW_H >= top && cursor <= top + view) {
				row(page, g, font, core, x, cursor, w, mouseX, mouseY, top, top + view);
			}
			cursor += ROW_H;
		}
		for (CompoundTag row : legacy) {
			if (cursor + ROW_H >= top && cursor <= top + view) {
				legacyRow(page, g, font, row, x, cursor, w);
			}
			cursor += ROW_H;
		}
		g.disableScissor();
		FormasiPage.bead(g, x + w, top, top + view, offset, Math.max(0.0F, content - view));
	}

	private static void row(FormasiPage page, GuiGraphicsExtractor g, Font font, CompoundTag core, int x, float ry, int w, int mouseX, int mouseY,
			int clipTop, int clipBottom) {
		FormationSpec spec = FormationRegistry.byId(core.getStringOr("spec", ""));
		int id = core.getIntOr("id", -1);
		int state = core.getIntOr("st", 0);
		boolean over = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW_H - 1 && mouseY >= clipTop && mouseY < clipBottom;
		g.fill(x, Math.round(ry), x + w - 3, Math.round(ry + ROW_H - 1), Palette.argb(over ? 0x50 : 0x28, Palette.PAPER_DEEP));
		int familyColor = spec == null ? Palette.LOCKED : Palette.opaque(spec.family().color());
		// Family glyph.
		Brush.rect(g, x + 2.0F, ry + 2.0F, x + 14.0F, ry + 14.0F, Palette.mix(familyColor, Palette.INK, 0.2F));
		Brush.frame(g, x + 2, Math.round(ry + 2), x + 14, Math.round(ry + 14), Palette.argb(0xC0, Palette.GOLD));
		if (spec != null) {
			Brush.centered(g, font, Component.literal(spec.family().label().getString().substring(0, 1)), x + 8.0F, ry + 4.5F, 0xFFFFF4D6, 0.7F, false);
		}
		// Name and grade ribbon.
		float nameX = x + 17.0F;
		float sealR = 6.5F;
		float right = x + w - 6.0F - sealR * 2.0F - 4.0F;
		Component name = spec == null ? Component.translatable("kultivator.formasiui.unknown_spec", core.getStringOr("spec", "?")) : spec.name();
		Brush.fit(g, font, name, nameX, ry + 2.0F, right - nameX - 10.0F, Palette.INK, 0.62F, 0.4F, false);
		if (spec != null) {
			Brush.rect(g, right - 8.0F, ry + 2.5F, right - 1.0F, ry + 6.5F, Palette.opaque(spec.grade().color()));
		}
		// Daya bar.
		long daya = core.getLongOr("daya", 0L);
		long cap = Math.max(1L, core.getLongOr("cap", 1L));
		FormasiPage.bar(g, nameX, ry + 10.5F, right - nameX - 2.0F, 3.0F, daya / (float) cap, Palette.QI);
		Brush.fit(g, font, Component.translatable("kultivator.formasiui.cores.hours", FormasiPage.hours(core.getDoubleOr("hrs", 0.0D))), nameX,
				ry + 13.5F, right - nameX, Palette.INK_FAINT, 0.42F, 0.35F, false);
		// State seal and tags.
		float scx = x + w - 6.0F - sealR;
		float scy = ry + ROW_H / 2.0F - 0.5F;
		FormasiPage.seal(g, font, FormasiPage.stateName(state), scx, scy, sealR, FormasiPage.stateColor(state));
		if (core.getBooleanOr("idle", false)) {
			Brush.fit(g, font, Component.translatable("kultivator.formasiui.tag.idle"), right - 30.0F, ry + 13.5F, 28.0F, Palette.INK_FAINT, 0.42F, 0.3F, false);
		}
		if (core.getBooleanOr("crack", false)) {
			Brush.fit(g, font, Component.translatable("kultivator.formasiui.tag.crack"), right - 30.0F, ry + 7.0F, 28.0F, Palette.DANGER, 0.42F, 0.3F, false);
		}
		List<Component> tip = new ArrayList<>();
		tip.add(name);
		tip.add(FormasiPage.stateName(state).copy().withColor(FormasiPage.stateColor(state) & 0xFFFFFF));
		Component why = FormasiPage.text(core, "why");
		if (!why.getString().isEmpty()) {
			tip.add(why);
		}
		tip.add(Component.translatable("kultivator.formasiui.cores.at", core.getIntOr("x", 0), core.getIntOr("y", 0), core.getIntOr("z", 0)));
		if (ry + ROW_H > clipTop && ry < clipBottom) {
			page.hit(x, Math.max(ry, clipTop), x + w - 3, Math.min(ry + ROW_H - 1, clipBottom), () -> page.openCoreSlip(id), tip);
		}
	}

	private static void legacyRow(FormasiPage page, GuiGraphicsExtractor g, Font font, CompoundTag row, int x, float ry, int w) {
		g.fill(x, Math.round(ry), x + w - 3, Math.round(ry + ROW_H - 1), Palette.argb(0x18, Palette.PAPER_DEEP));
		boolean agung = row.getStringOr("kind", "").contains("agung");
		Brush.rect(g, x + 2.0F, ry + 2.0F, x + 14.0F, ry + 14.0F, Palette.mix(Palette.JADE, Palette.INK, 0.2F));
		Brush.centered(g, font, Component.literal(agung ? "A" : "P"), x + 8.0F, ry + 4.5F, 0xFFFFF4D6, 0.7F, false);
		Component name = Component.translatable(agung ? "kultivator.formasiui.legacy.agung" : "kultivator.formasiui.legacy.pelindung");
		Brush.fit(g, font, name, x + 17.0F, ry + 2.0F, w - 70.0F, Palette.INK_SOFT, 0.62F, 0.4F, false);
		Component line = Component.translatable("kultivator.formasiui.legacy.line", row.getBooleanOr("on", false)
						? Component.translatable("kultivator.formasiui.on") : Component.translatable("kultivator.formasiui.off"),
				row.getIntOr("layers", 1), Math.round(row.getDoubleOr("r", 16.0D)), row.getIntOr("x", 0), row.getIntOr("y", 0), row.getIntOr("z", 0));
		Brush.fit(g, font, line, x + 17.0F, ry + 10.0F, w - 22.0F, Palette.INK_FAINT, 0.45F, 0.32F, false);
	}

	private CorePanel() {
	}
}
