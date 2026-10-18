package dev.kultivator.client.screen;

import dev.kultivator.client.Keys;
import dev.kultivator.client.codex.Codex;
import dev.kultivator.client.codex.CodexScreen;
import dev.kultivator.client.codex.SpecInfo;
import dev.kultivator.client.codex.TeknikSession;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SealCard;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.technique.spec.TechniquePower;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;

/**
 * Techniques: since phase 6c a launcher card for the Kitab Teknik (DESIGN 22.12.1): the counter "Teknik 37/168", the
 * power P, the highest mastery, the selected art as a seal card, and talismans to open the Kodeks, the Cincin Segel and
 * the Kehancuran page, with the keys written underneath.
 */
final class TechniquesPage extends Page {

	private static final int BUTTON_H = 18;

	private int cardX;
	private int cardY;
	private int cardSize;
	private int infoX;

	TechniquesPage(CultivationScreen screen) {
		super(screen);
	}

	@Override
	Component title() {
		return Component.translatable("kultivator.screen.tab.techniques");
	}

	@Override
	Component shortName() {
		return Component.translatable("kultivator.screen.tab.techniques.short");
	}

	@Override
	protected void build() {
		int columnW = Math.max(110, Math.min(160, Math.round(w * 0.45F)));
		int bx = x + w - columnW;
		int top = y + 18;
		int gap = 6;
		TalismanButton open = add(new TalismanButton(bx, top, columnW, BUTTON_H, () -> Component.translatable("kultivator.codex.launcher.open")));
		open.urgentWhen(() -> Util.getMillis() / 1000L % 4L == 0L);
		open.onPress(() -> Codex.openTab(CodexScreen.TAB_KODEKS));
		open.tooltip(() -> List.of(Component.translatable("kultivator.codex.launcher.open"),
				Component.translatable("kultivator.codex.launcher.open.tip", Keys.CODEX.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY)));
		TalismanButton ring = add(new TalismanButton(bx, top + BUTTON_H + gap, columnW, BUTTON_H, () -> Component.translatable("kultivator.codex.tab.ring")));
		ring.onPress(() -> Codex.openTab(CodexScreen.TAB_RING));
		ring.tooltip(() -> List.of(Component.translatable("kultivator.codex.tab.ring"),
				Component.translatable("kultivator.codex.launcher.ring.tip").withStyle(ChatFormatting.GRAY)));
		TalismanButton red = add(new TalismanButton(bx, top + (BUTTON_H + gap) * 2, columnW, BUTTON_H,
				() -> Component.translatable(TeknikSession.breakerOn() ? "kultivator.codex.launcher.kh_on" : "kultivator.codex.launcher.kh_off")));
		red.urgentWhen(TeknikSession::breakerOn);
		red.onPress(() -> Codex.openTab(CodexScreen.TAB_KEHANCURAN));
		red.tooltip(() -> List.of(Component.translatable("kultivator.codex.tab.kehancuran"),
				Component.translatable("kultivator.codex.launcher.kh.tip").withStyle(ChatFormatting.GRAY)));
		cardSize = Math.max(36, Math.min(56, Math.min(bx - x - 16, h / 3)));
		cardX = x + 4;
		cardY = y + 16;
		infoX = cardX + cardSize + 8;
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CultivationData data = data();
		heading(g, Component.translatable("kultivator.codex.launcher.title"), x + 2.0F, y, w - 4.0F, 0.85F);
		TechniqueSpec selected = data.selectedId().isEmpty() ? null : TechniqueRegistry.byId(data.selectedId());
		int columnLeft = x + w - Math.max(110, Math.min(160, Math.round(w * 0.45F)));
		if (selected != null) {
			long left = SpecInfo.cooldownLeft(selected);
			SealCard.paint(g, font, selected, cardX, cardY, cardSize, new SealCard.Look(true, false, true, left,
					left > 0L ? SpecInfo.cooldown(data, selected) : 0, SpecInfo.mastery(data, selected), -1, false, false), 0.0F);
		} else {
			g.fill(cardX, cardY, cardX + cardSize, cardY + cardSize, Palette.argb(0x40, Palette.PAPER_DEEP));
			Brush.frame(g, cardX, cardY, cardX + cardSize, cardY + cardSize, Palette.argb(0x90, Palette.GOLD_DARK));
			Glyphs.tab(g, Glyphs.CODEX, cardX + cardSize / 2.0F, cardY + cardSize / 2.0F, cardSize * 0.4F, Palette.INK_FAINT);
		}
		float tx = infoX;
		float room = columnLeft - infoX - 8.0F;
		float cursor = cardY;
		int best = 0;
		for (TechniqueSpec spec : data.learnedSpecs()) {
			best = Math.max(best, SpecInfo.mastery(data, spec));
		}
		Component counter = Component.translatable("kultivator.codex.launcher.counter", SpecInfo.learnedCount(data), TechniqueRegistry.playerTotal());
		cursor += Brush.wrap(g, font, Brush.bold(counter), tx, cursor, room, Palette.SEAL_DARK, 0.8F, false) + 2.0F;
		Component power = Component.translatable("kultivator.codex.launcher.power", SpecInfo.compact(TechniquePower.power(data)));
		cursor += Brush.wrap(g, font, power, tx, cursor, room, Palette.INK, 0.7F, false) + 1.0F;
		Component mastery = Component.translatable("kultivator.codex.launcher.mastery", SpecInfo.masteryName(best));
		cursor += Brush.wrap(g, font, mastery, tx, cursor, room, Palette.INK, 0.7F, false) + 3.0F;
		Component current = selected == null ? Component.translatable("kultivator.screen.tech.none_selected")
				: Component.translatable("kultivator.codex.launcher.selected", selected.displayName());
		cursor += Brush.wrap(g, font, current, tx, cursor, room, selected == null ? Palette.INK_FAINT : SpecInfo.ink(selected), 0.65F, false) + 2.0F;
		float keysY = Math.max(cursor + 4.0F, cardY + cardSize + 8.0F);
		Component keys = Component.translatable("kultivator.codex.launcher.keys", Keys.CAST.getTranslatedKeyMessage(),
				Keys.PREVIOUS.getTranslatedKeyMessage(), Keys.NEXT.getTranslatedKeyMessage(), Keys.CODEX.getTranslatedKeyMessage());
		Brush.wrap(g, font, keys, x + 4.0F, keysY, columnLeft - x - 12.0F, Palette.INK_SOFT, 0.62F, false);
	}
}
