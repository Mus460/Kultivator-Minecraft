package dev.kultivator.client.codex;

import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/**
 * The Bantuan tablet of the Kitab Teknik (DESIGN 22.17): the help topics {@code teknik}, {@code penguasaan},
 * {@code kitab_teknik} and {@code kehancuran_diri} (the last is written by its own module and skipped while missing),
 * then the technique keys. Scrolls with the wheel.
 */
final class BantuanPage extends CodexPage {

	private static final String[] TOPICS = {"teknik", "penguasaan", "kitab_teknik", "kehancuran_diri"};

	private final Eased scrollShown = new Eased(14.0F);
	private float scroll;
	private float contentH;

	BantuanPage(CodexScreen screen) {
		super(screen);
	}

	@Override
	Component title() {
		return Component.translatable("kultivator.codex.tab.help");
	}

	@Override
	Component shortName() {
		return Component.translatable("kultivator.codex.tab.help.short");
	}

	@Override
	int icon() {
		return Glyphs.HELP;
	}

	@Override
	protected void build() {
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		float maxScroll = Math.max(0.0F, contentH - h);
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		float offset = scrollShown.to(scroll, seconds);
		float scale = w >= 300 ? 0.8F : 0.7F;
		float textW = w - 12.0F;
		g.enableScissor(x, y, x + w, y + h);
		float cursor = y + 1.0F - offset;
		for (String topic : TOPICS) {
			if (!Language.getInstance().has("kultivator.screen.help." + topic)) {
				continue;
			}
			heading(g, Component.translatable("kultivator.screen.help." + topic + ".title"), x + 2.0F, cursor, textW, 0.8F);
			cursor += 12.0F;
			cursor += Brush.wrap(g, font, Component.translatable("kultivator.screen.help." + topic), x + 5.0F, cursor, textW - 3.0F,
					topic.equals("kehancuran_diri") ? Palette.SEAL_DARK : Palette.INK, scale, false) + 6.0F;
		}
		heading(g, Component.translatable("kultivator.codex.help.keys"), x + 2.0F, cursor, textW, 0.8F);
		cursor += 12.0F;
		for (KeyMapping key : new KeyMapping[]{Keys.CAST, Keys.PREVIOUS, Keys.NEXT, Keys.CODEX, Keys.WHEEL, Keys.SEAL_1, Keys.SEAL_2, Keys.SEAL_3,
				Keys.SEAL_4}) {
			Component bound = key.isUnbound() ? Component.translatable("kultivator.codex.help.unbound") : key.getTranslatedKeyMessage();
			Component line = Component.translatable("kultivator.screen.help.key_line", Component.translatable(key.getName()),
					bound.copy().withColor(Palette.SEAL_DARK));
			Brush.rect(g, x + 6.0F, cursor + 3.0F * scale, x + 8.0F, cursor + 5.0F * scale, Palette.JADE);
			cursor += Brush.wrap(g, font, line, x + 11.0F, cursor, textW - 9.0F, Palette.INK, scale, false) + 1.5F;
		}
		cursor += Brush.wrap(g, font, Component.translatable("kultivator.codex.help.commands"), x + 5.0F, cursor + 4.0F, textW - 3.0F, Palette.INK_SOFT,
				scale * 0.9F, false) + 8.0F;
		contentH = cursor + offset - y;
		g.disableScissor();
		if (maxScroll > 0.0F) {
			float bead = y + 3.0F + (h - 6.0F) * (offset / maxScroll);
			g.fill(x + w - 2, y, x + w - 1, y + h, Palette.CORD);
			Brush.disc(g, x + w - 1.5F, bead, 2.2F, Palette.JADE);
		}
	}

	@Override
	boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (inside(mouseX, mouseY)) {
			scroll -= (float) amount * 16.0F;
			return true;
		}
		return false;
	}
}
