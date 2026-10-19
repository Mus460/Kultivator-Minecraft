package dev.kultivator.client.abadi;

import dev.kultivator.client.dunia.DuniaPage;
import dev.kultivator.client.dunia.DuniaScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.network.Actions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.List;

/**
 * The Alam tablet of the Keabadian screen (DESIGN 26.10, owned by P9-langit). Before the ascension: the gate checklist (Dewa
 * Abadi Puncak, ★9, the DESIGN 17 ascension, 9 Esensi Energi Makam, 1 Supreme spirit stone, no cooldown), the reason it is
 * locked and a "Mulai Kenaikan" talisman. After it: the Esensi Abadi bar, Batu carried, mined today, conversions left, the
 * star discount, the crossing cooldown, a [Lintas Alam] talisman and a [Konversi] talisman. Reads {@code AbadiClient.alam()}.
 */
public class AlamAbadiPage extends DuniaPage {

	private TalismanButton ascend;
	private TalismanButton cross;
	private TalismanButton convert;

	public AlamAbadiPage(DuniaScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine9.tab.alam");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine9.tab.alam.short");
	}

	@Override
	public int icon() {
		return Glyphs.PATH;
	}

	private static CompoundTag page() {
		return AbadiClient.alam();
	}

	private static boolean ascended() {
		return page().getBooleanOr("ascended", false);
	}

	@Override
	protected void build() {
		int bw = Math.min(110, w / 2 - 6);
		ascend = add(new TalismanButton(x + w - bw - 4, y + h - 18, bw, 14, () -> Component.translatable("kultivator.abadi.page.ascend")));
		ascend.enabledWhen(() -> page().getBooleanOr("ready", false));
		ascend.onPress(() -> send(Actions.ABADI, 0, "naik"));
		ascend.tooltip(() -> {
			Component why = page().read("why", ComponentSerialization.CODEC).orElse(null);
			return why == null ? List.of(Component.translatable("kultivator.abadi.page.ascend_tip")) : List.of(why);
		});
		cross = add(new TalismanButton(x + w - bw - 4, y + h - 18, bw, 14, () -> Component.translatable("kultivator.abadi.page.cross")));
		cross.enabledWhen(() -> page().getBooleanOr("talisman", false) && page().getLongOr("cross_cd", 0L) <= 0L);
		cross.onPress(() -> send(Actions.ABADI, 0, "lintas"));
		cross.tooltip(() -> List.of(Component.translatable(page().getBooleanOr("talisman", false) ? "kultivator.abadi.page.cross_tip"
				: "kultivator.abadi.cross.no_talisman")));
		convert = add(new TalismanButton(x + 4, y + h - 18, bw, 14, () -> Component.translatable("kultivator.abadi.page.convert")));
		convert.enabledWhen(() -> page().getIntOr("conv", 0) < 100 && page().getIntOr("conv_left", 0) > 0 && page().getIntOr("stones", 0) > 0);
		convert.onPress(() -> send(Actions.ABADI, 0, "konversi:1"));
		convert.tooltip(() -> List.of(Component.translatable("kultivator.abadi.page.convert_tip", page().getIntOr("conv_left", 0))));
	}

	@Override
	protected boolean visible(Widget widget) {
		if (widget == ascend) {
			return !ascended();
		}
		if (widget == cross || widget == convert) {
			return ascended();
		}
		return true;
	}

	/** Ticks as {@code m:ss}. */
	private static String clock(long ticks) {
		long seconds = (ticks + 19L) / 20L;
		return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag page = page();
		if (page.isEmpty()) {
			heading(g, title(), x + 2.0F, y, w - 4.0F, 0.8F);
			Brush.wrap(g, font, Component.translatable("kultivator.abadi.page.loading"), x + 4.0F, y + 16.0F, w - 8.0F, Palette.INK_SOFT, 0.8F, false);
			return;
		}
		if (!page.getBooleanOr("ascended", false)) {
			drawGate(g, page);
		} else {
			drawStatus(g, page);
		}
	}

	private void drawGate(GuiGraphicsExtractor g, CompoundTag page) {
		heading(g, Component.translatable("kultivator.abadi.page.gate_title"), x + 2.0F, y, w - 4.0F, 0.8F);
		float ly = y + 14.0F;
		Brush.fit(g, font, Component.translatable("kultivator.abadi.page.gate_intro"), x + 4.0F, ly, w - 8.0F, Palette.INK_SOFT, 0.7F, 0.45F, false);
		ly += 10.0F;
		int top = y + 24;
		int bottom = y + h - 22;
		panel(g, x + 2, top, x + w - 2, Math.min(bottom, top + 8 + 9 * 6));
		ly = top + 4.0F;
		for (Tag tag : page.getListOrEmpty("gate")) {
			if (!(tag instanceof CompoundTag line) || ly > bottom - 9) {
				continue;
			}
			boolean ok = line.getBooleanOr("ok", false);
			Brush.rect(g, x + 7.0F, ly + 1.0F, x + 12.0F, ly + 6.0F, ok ? Palette.JADE : Palette.DANGER);
			Component text = Component.translatable(line.getStringOr("key", ""), line.getIntOr("have", 0), line.getIntOr("need", 0));
			Brush.fit(g, font, text, x + 16.0F, ly, w - 22.0F, ok ? Palette.JADE_DARK : Palette.SEAL_DARK, 0.75F, 0.45F, false);
			ly += 9.0F;
		}
		float note = Math.min(bottom, top + 8 + 9 * 6) + 3.0F;
		Component why = page.read("why", ComponentSerialization.CODEC).orElse(null);
		if (why != null && note < bottom) {
			Brush.fit(g, font, why, x + 4.0F, note, w - 8.0F, Palette.SEAL_DARK, 0.7F, 0.45F, false);
			note += 9.0F;
		}
		if (note < bottom) {
			Brush.wrap(g, font, Component.translatable("kultivator.abadi.page.gate_source"), x + 4.0F, note, w - 8.0F, Palette.INK_FAINT, 0.6F, false);
		}
	}

	private void drawStatus(GuiGraphicsExtractor g, CompoundTag page) {
		heading(g, Component.translatable("kultivator.abadi.page.status_title"), x + 2.0F, y, w - 4.0F, 0.8F);
		float ly = y + 14.0F;
		Component where = Component.translatable(page.getBooleanOr("in_realm", false) ? "kultivator.abadi.page.here" : "kultivator.abadi.page.away");
		Brush.fit(g, font, where, x + 4.0F, ly, w - 8.0F, Palette.INK_SOFT, 0.7F, 0.45F, false);
		ly += 11.0F;
		// The Esensi Abadi bar.
		int conv = Math.max(0, Math.min(100, page.getIntOr("conv", 0)));
		Brush.fit(g, font, Component.translatable("kultivator.abadi.page.conv", conv), x + 4.0F, ly, w - 8.0F, Palette.INK, 0.75F, 0.45F, false);
		ly += 9.0F;
		int bx0 = x + 4;
		int bx1 = x + w - 4;
		int by = (int) ly;
		g.fill(bx0, by, bx1, by + 6, Palette.argb(0x60, Palette.PAPER_DEEP));
		g.fill(bx0, by, bx0 + Math.round((bx1 - bx0) * conv / 100.0F), by + 6, Palette.argb(0xE0, 0xFFE9A8));
		Brush.frame(g, bx0, by, bx1, by + 6, Palette.argb(0xC0, Palette.GOLD));
		ly += 10.0F;
		int bottom = y + h - 22;
		panel(g, x + 2, (int) ly, x + w - 2, Math.min(bottom, (int) ly + 6 + 9 * 5));
		ly += 3.0F;
		Component[] lines = {
				Component.translatable("kultivator.abadi.page.stones", page.getIntOr("stones", 0)),
				Component.translatable("kultivator.abadi.page.mined", page.getIntOr("mined", 0), page.getIntOr("mined_cap", 16)),
				Component.translatable("kultivator.abadi.page.conv_left", page.getIntOr("conv_left", 0)),
				Component.translatable("kultivator.abadi.page.discount", page.getIntOr("discount", 0)),
				page.getLongOr("cross_cd", 0L) > 0L ? Component.translatable("kultivator.abadi.page.cross_cd", clock(page.getLongOr("cross_cd", 0L) - AbadiClient.age()))
						: Component.translatable(page.getBooleanOr("talisman", false) ? "kultivator.abadi.page.cross_ready" : "kultivator.abadi.page.no_talisman")};
		for (Component line : lines) {
			if (ly > bottom - 9) {
				break;
			}
			Brush.fit(g, font, line, x + 7.0F, ly, w - 14.0F, Palette.INK, 0.72F, 0.45F, false);
			ly += 9.0F;
		}
		if (page.getBooleanOr("fallback", false) && ly < bottom - 8) {
			Brush.fit(g, font, Component.translatable("kultivator.abadi.page.fallback"), x + 4.0F, ly + 2.0F, w - 8.0F, Palette.WARN, 0.6F, 0.4F, false);
		}
	}
}
