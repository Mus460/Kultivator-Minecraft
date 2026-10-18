package dev.kultivator.client.screen;

import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/** Help: short paragraphs on the path, written on the scroll, with the mod's keys at the end. Scrolls with the wheel. */
final class HelpPage extends Page {

	/**
	 * Topics in reading order. The phase-6 topics (Sect Hall onwards, DESIGN 20.4.1) and the phase-7 topics (DESIGN 19.6:
	 * the Jalan tablet first, for new players) are written by their own modules; a topic whose text is not in the
	 * language files yet is skipped.
	 */
	private static final String[] TOPICS = {"jalan", "meditation", "xp", "bottleneck", "breakthrough", "persiapan", "tribulation", "protectors",
			"pills", "techniques", "roots", "dao", "alchemy", "heart_demon", "beasts", "formations", "sects", "ascension", "visuals",
			"aula", "bangun", "calon", "jenjang", "kontribusi", "tugas", "misi", "turnamen", "alam_hampa", "bakat", "pusaka", "bintang", "ikatan", "bimbingan",
			"kehancuran", "teknik", "penguasaan", "kitab_teknik", "kehancuran_diri",
			"harian", "batu_roh", "tanaman_roh", "inti_binatang", "alkimia_lanjut", "relik", "urat_roh", "jimat", "anugerah", "elit", "unsur",
			"kejenuhan", "hu_fa_tribulasi", "momen", "formasi", "formasi_pola"};
	private static final KeyMapping[] KEYS = {Keys.MEDITATE, Keys.CAST, Keys.PREVIOUS, Keys.NEXT, Keys.FLYING_SWORD, Keys.INFO, Keys.CODEX, Keys.WHEEL,
			Keys.SEAL_1, Keys.SEAL_2, Keys.SEAL_3, Keys.SEAL_4};

	private final Eased scrollShown = new Eased(14.0F);
	private float scroll;
	private float contentH;

	HelpPage(CultivationScreen screen) {
		super(screen);
	}

	@Override
	Component title() {
		return Component.translatable("kultivator.screen.tab.help");
	}

	@Override
	Component shortName() {
		return Component.translatable("kultivator.screen.tab.help.short");
	}

	@Override
	protected void build() {
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		float maxScroll = Math.max(0.0F, contentH - h);
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		float offset = scrollShown.to(scroll, seconds);
		float scale = w >= 300 ? 0.85F : 0.72F;
		float textW = w - 14.0F;
		g.enableScissor(x, y, x + w, y + h);
		float cursor = y + 1.0F - offset;
		for (String topic : TOPICS) {
			if (!Language.getInstance().has("kultivator.screen.help." + topic)) {
				continue;
			}
			heading(g, Component.translatable("kultivator.screen.help." + topic + ".title"), x + 2.0F, cursor, textW, 0.85F);
			cursor += 12.0F;
			Component body = Component.translatable("kultivator.screen.help." + topic);
			cursor += Brush.wrap(g, font, body, x + 5.0F, cursor, textW - 3.0F, Palette.INK, scale, false) + 6.0F;
		}
		heading(g, Component.translatable("kultivator.screen.help.keys.title"), x + 2.0F, cursor, textW, 0.85F);
		cursor += 12.0F;
		for (KeyMapping key : KEYS) {
			Component line = Component.translatable("kultivator.screen.help.key_line", Component.translatable(key.getName()),
					key.getTranslatedKeyMessage().copy().withColor(Palette.SEAL_DARK));
			Brush.rect(g, x + 6.0F, cursor + 3.0F * scale, x + 8.0F, cursor + 5.0F * scale, Palette.JADE);
			cursor += Brush.wrap(g, font, line, x + 11.0F, cursor, textW - 9.0F, Palette.INK, scale, false) + 1.5F;
		}
		cursor += Brush.wrap(g, font, Component.translatable("kultivator.screen.help.commands"), x + 5.0F, cursor + 4.0F, textW - 3.0F,
				Palette.INK_SOFT, scale * 0.9F, false) + 8.0F;
		contentH = cursor + offset - y;
		g.disableScissor();

		if (maxScroll > 0.0F) {
			float bead = y + 3.0F + (h - 6.0F) * (offset / maxScroll);
			g.fill(x + w - 2, y, x + w - 1, y + h, Palette.CORD);
			Brush.disc(g, x + w - 1.5F, bead, 2.2F, Palette.JADE);
			Brush.disc(g, x + w - 2.0F, bead - 0.6F, 0.8F, Palette.JADE_PALE);
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
