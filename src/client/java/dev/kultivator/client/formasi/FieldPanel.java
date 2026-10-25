package dev.kultivator.client.formasi;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.formasi.stack.Stacking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Medan sub of Sekte › Formasi (DESIGN 24.7.1): the player's current {@code FormationField} at the moment the scroll was opened, one
 * line per active term with its cap ("Kultivasi ×1,26 · Sekte · batas ×2,6", "Petir ×0,77 · lantai formasi 0,70",
 * "Lapisan 6/6", "Teman sekte ×1,15"). Neutral terms are left out. Text is 1.0 (43.0.5).
 */
final class FieldPanel {

	private record Line(Component text, int color, Component tip) {
	}

	static void draw(FormasiPage page, GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
		CompoundTag f = page.f().getCompoundOrEmpty("field");
		List<Line> lines = lines(f);
		meditation(page.f().getCompoundOrEmpty("med"), lines);
		page.title(g, Component.translatable("kultivator.formasiui.field.title"), x + 2.0F, y + 1.0F, w - 6.0F, 1.0F);
		float top = y + 15.0F;
		float view = h - 15.0F;
		float rowH = 12.0F;
		Component note = Component.translatable("kultivator.formasiui.field.note");
		float noteH = page.font().split(note, Math.max(10, w - 10)).size() * 9.0F + 6.0F;
		float content = Math.max(1, lines.size()) * rowH + noteH;
		float offset = page.clampScroll(2, content, view);
		page.scrollArea(x, Math.round(top), x + w, Math.round(top + view));
		page.klip(x, Math.round(top), x + w, Math.round(top + view));
		float cursor = top - offset;
		if (lines.isEmpty()) {
			cursor += page.wrap(Component.translatable("kultivator.formasiui.field.none"), x + 4.0F, cursor + 2.0F, w - 10.0F, Palette.INK_SOFT) + 4.0F;
		}
		for (Line line : lines) {
			if (cursor + rowH >= top && cursor <= top + view) {
				Brush.rect(g, x + 3.0F, cursor + 3.0F, x + 6.0F, cursor + 6.0F, line.color());
				page.line(line.text(), x + 9.0F, cursor + 1.0F, w - 16.0F, Palette.INK);
				page.hit(x, cursor, x + w - 4.0F, cursor + rowH, null, List.of(line.text(), line.tip()));
			}
			cursor += rowH;
		}
		page.wrap(note, x + 4.0F, cursor + 4.0F, w - 10.0F, Palette.INK_FAINT);
		page.lepasKlip();
		FormasiPage.bead(g, x + w, Math.round(top), Math.round(top + view), offset, Math.max(0.0F, content - view));
	}

	/** Audit M4 T6: the meditation absorption limit ({@code formasi.med} from InfoPart.meditationLimit), shown first. */
	private static void meditation(CompoundTag med, List<Line> out) {
		if (!med.contains("cap")) {
			return;
		}
		double inner = med.getFloatOr("inner", 0.0F);
		double cap = med.getFloatOr("cap", 3.5F);
		double sect = med.getFloatOr("sect", 1.0F);
		double sectCap = med.getFloatOr("sectCap", 1.5F);
		int color = med.getBooleanOr("capped", false) ? 0xFFE08040 : Palette.GOLD;
		out.add(0, new Line(Component.translatable("kultivator.kekuatan.formasi.medcap", FormasiPage.decimal(inner), FormasiPage.decimal(cap),
				FormasiPage.decimal(sect), FormasiPage.decimal(sectCap)), color, Component.translatable("kultivator.kekuatan.formasi.medcap.tip")));
	}

	private static List<Line> lines(CompoundTag f) {
		List<Line> out = new ArrayList<>();
		double gather = f.getFloatOr("g", 1.0F);
		double cap = f.getFloatOr("cap", (float) Stacking.FORMATION_STACK_CAP);
		if (gather > 1.001D) {
			out.add(new Line(Component.translatable("kultivator.formasiui.field.gather", FormasiPage.decimal(gather), FormasiPage.decimal(cap)),
					0xFFA070E0, Component.translatable("kultivator.formasiui.field.gather.tip", FormasiPage.decimal(Stacking.GATHER_SECT_CAP))));
		}
		double drain = f.getFloatOr("drain", 1.0F);
		if (drain < 0.999D) {
			out.add(new Line(Component.translatable("kultivator.formasiui.field.drain", FormasiPage.decimal(drain), FormasiPage.decimal(Stacking.DRAIN_FLOOR)),
					0xFF70A0E0, Component.translatable("kultivator.formasiui.field.drain.tip")));
		}
		int heal = f.getIntOr("h", -1);
		double qi = f.getFloatOr("qi", 1.0F);
		double dev = f.getFloatOr("dev", 1.0F);
		if (heal >= 0 || qi > 1.001D || dev > 1.001D) {
			out.add(new Line(Component.translatable("kultivator.formasiui.field.heal", heal + 1, FormasiPage.decimal(qi), FormasiPage.decimal(dev),
					FormasiPage.decimal(Stacking.QI_REGEN_CAP)), 0xFF8CD8A8, Component.translatable("kultivator.formasiui.field.heal.tip")));
		}
		double trib = f.getFloatOr("t", 1.0F);
		if (trib < 0.999D) {
			out.add(new Line(Component.translatable("kultivator.formasiui.field.trib", FormasiPage.decimal(trib),
					FormasiPage.decimal(Stacking.TRIB_FORMATION_FLOOR)), Palette.GOLD, Component.translatable("kultivator.formasiui.field.trib.tip")));
		}
		double refine = f.getFloatOr("rc", 0.0F);
		int steps = f.getIntOr("rs", 0);
		double time = f.getFloatOr("rt", 1.0F);
		if (refine > 0.0001D || steps > 0 || time < 0.999D) {
			out.add(new Line(Component.translatable("kultivator.formasiui.field.refine", percent(refine), steps, FormasiPage.decimal(time),
					percent(Stacking.REFINE_CHANCE_CAP)), 0xFFE07040, Component.translatable("kultivator.formasiui.field.refine.tip")));
		}
		double inscribe = f.getFloatOr("ins", 0.0F);
		if (inscribe > 0.0001D) {
			out.add(new Line(Component.translatable("kultivator.formasiui.field.inscribe", percent(inscribe), percent(Stacking.INSCRIBE_CAP)), 0xFFE0B060,
					Component.translatable("kultivator.formasiui.field.inscribe.tip")));
		}
		double quality = f.getFloatOr("q", 0.0F);
		if (quality > 0.0001D) {
			out.add(new Line(Component.translatable("kultivator.formasiui.field.quality", percent(quality), percent(Stacking.QUALITY_FORMATION_CAP)),
					Palette.GOLD_LIGHT, Component.translatable("kultivator.formasiui.field.quality.tip")));
		}
		int layers = f.getIntOr("l", 0);
		if (layers > 0) {
			out.add(new Line(Component.translatable("kultivator.formasiui.field.layers", layers, Stacking.BARRIER_LAYER_CAP), Palette.JADE,
					Component.translatable("kultivator.formasiui.field.layers.tip")));
		}
		int conceal = f.getIntOr("c", 0);
		if (conceal > 0) {
			out.add(new Line(Component.translatable("kultivator.formasiui.field.conceal", conceal), 0xFF7C9C80,
					Component.translatable("kultivator.formasiui.field.conceal.tip")));
		}
		int illusion = f.getIntOr("i", 0);
		if (illusion > 0) {
			out.add(new Line(Component.translatable("kultivator.formasiui.field.illusion", illusion), 0xFFE0C040,
					Component.translatable("kultivator.formasiui.field.illusion.tip")));
		}
		double mate = f.getFloatOr("mate", 1.0F);
		if (mate > 1.001D) {
			out.add(new Line(Component.translatable("kultivator.formasiui.field.mate", FormasiPage.decimal(mate)), Palette.JADE_LIGHT,
					Component.translatable("kultivator.formasiui.field.mate.tip")));
		}
		return out;
	}

	/** "6,5" for 0.065. */
	private static String percent(double fraction) {
		return String.format(java.util.Locale.ROOT, "%.1f", fraction * 100.0D).replace('.', ',');
	}

	private FieldPanel() {
	}
}
