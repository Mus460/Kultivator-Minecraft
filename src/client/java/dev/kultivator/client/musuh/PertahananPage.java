package dev.kultivator.client.musuh;

import dev.kultivator.client.hall.HallPage;
import dev.kultivator.client.hall.HallScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/**
 * The Pertahanan tablet of the Sect Hall scroll ({@code Halls.PAGE_PERTAHANAN}, DESIGN 25.10): the raid schedule and its
 * omen, the defence score with the chance to hold an unattended raid, the raid level and waves, a live raid's progress and
 * the last ten raid results. Read-only; its data is the {@code musuh} part of the {@code HallInfo}.
 */
public class PertahananPage extends HallPage {

	private final Eased scrollShown = new Eased(14.0F);
	private float scroll;
	private float contentH;

	public PertahananPage(HallScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine8.page.pertahanan");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine8.page.pertahanan.short");
	}

	@Override
	public int icon() {
		return Glyphs.SEAL;
	}

	@Override
	public boolean shown() {
		return info().contains("musuh");
	}

	@Override
	protected void build() {
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag raid = info().getCompoundOrEmpty("musuh");
		heading(g, title(), x + 2.0F, y, w - 4.0F, 0.8F);
		float scale = w >= 260 ? 0.64F : 0.58F;
		float cursor = y + 14.0F;
		int top = Math.round(cursor);
		panel(g, x + 2, top, x + w - 2, top + 58);
		Brush.fit(g, font, MusuhText.nextRaid(raid), x + 6.0F, cursor + 3.0F, w - 12.0F, Palette.SEAL_DARK, scale, scale * 0.7F, false);
		boolean omen = raid.getIntOr("state", 0) == 1;
		Component omenLine = omen ? Component.translatable("kultivator.musuh.ui.omen_seen", MusuhText.sect(raid.getIntOr("sect", -1)))
				: Component.translatable("kultivator.musuh.ui.omen_none", raid.getIntOr("interval", 9));
		Brush.fit(g, font, omenLine, x + 6.0F, cursor + 13.0F, w - 12.0F, omen ? Palette.SEAL : Palette.INK_SOFT, scale, scale * 0.7F, false);
		Brush.fit(g, font, Component.translatable(raid.getBooleanOr("alarm", false) ? "kultivator.musuh.ui.alarm_on" : "kultivator.musuh.ui.alarm_off"),
				x + 6.0F, cursor + 23.0F, w - 12.0F, Palette.INK_SOFT, scale, scale * 0.7F, false);
		Brush.fit(g, font, Component.translatable("kultivator.musuh.ui.defence", raid.getIntOr("score", 0), raid.getIntOr("chance", 10)),
				x + 6.0F, cursor + 33.0F, w - 12.0F, Palette.JADE_DARK, scale, scale * 0.7F, false);
		Brush.fit(g, font, Component.translatable("kultivator.musuh.ui.level", raid.getIntOr("lr", 1), raid.getIntOr("waves", 3)),
				x + 6.0F, cursor + 43.0F, w - 12.0F, Palette.INK, scale, scale * 0.7F, false);
		cursor += 62.0F;
		if (raid.getBooleanOr("hurt", false)) {
			Brush.fit(g, font, Component.translatable("kultivator.musuh.ui.hurt"), x + 4.0F, cursor, w - 8.0F, Palette.SEAL, scale, scale * 0.7F, false);
			cursor += 10.0F;
		}
		heading(g, Component.translatable("kultivator.musuh.ui.raid_log"), x + 2.0F, cursor, w - 4.0F, 0.7F);
		cursor += 12.0F;
		int listTop = Math.round(cursor);
		int listBottom = y + h - 10;
		ListTag log = raid.getListOrEmpty("log");
		float maxScroll = Math.max(0.0F, contentH - (listBottom - listTop));
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		float offset = scrollShown.to(scroll, seconds);
		g.enableScissor(x, listTop, x + w, listBottom);
		float row = listTop - offset;
		if (log.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.musuh.ui.no_raids"), x + 6.0F, row + 2.0F, w - 12.0F, Palette.INK_SOFT, scale, false);
		}
		for (int i = log.size() - 1; i >= 0; i--) {
			CompoundTag entry = log.getCompoundOrEmpty(i);
			int res = entry.getIntOr("res", 0);
			int rowTop = Math.round(row);
			g.fill(x + 4, rowTop, x + w - 4, rowTop + 11, Palette.argb(0x40, i % 2 == 0 ? Palette.BAMBOO : Palette.BAMBOO_ALT));
			g.fill(x + 4, rowTop, x + 6, rowTop + 11, MusuhText.resultColor(res));
			Component line = Component.translatable("kultivator.musuh.ui.raid_entry", entry.getLongOr("day", 0L), MusuhText.sect(entry.getIntOr("sect", -1)),
					MusuhText.result(res), entry.getIntOr("waves", 3), entry.getIntOr("meter", 0));
			Brush.fit(g, font, line, x + 9.0F, row + 2.0F, w - 16.0F, MusuhText.resultColor(res), scale, scale * 0.7F, false);
			row += 13.0F;
		}
		contentH = row + offset - listTop;
		g.disableScissor();
		if (maxScroll > 0.0F) {
			float bead = listTop + 3.0F + (listBottom - listTop - 6.0F) * (offset / maxScroll);
			g.fill(x + w - 2, listTop, x + w - 1, listBottom, Palette.CORD);
			Brush.disc(g, x + w - 1.5F, bead, 2.2F, Palette.JADE);
		}
		Brush.fit(g, font, Component.translatable("kultivator.musuh.raid.source"), x + 4.0F, y + h - 8.0F, w - 8.0F, Palette.INK_FAINT, 0.5F, 0.35F, false);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (inside(mouseX, mouseY)) {
			scroll -= (float) amount * 13.0F;
			return true;
		}
		return false;
	}
}
