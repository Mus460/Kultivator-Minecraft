package dev.kultivator.client.hall;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * The Catatan tablet (DESIGN 20.3.1): the hall's last 50 log lines, newest first, each stamped with its day index on a
 * little red seal, written down the scroll like a sect's annals. Scrolls with the wheel.
 */
public class LogPage extends HallPage {

	private final Eased scrollShown = new Eased(14.0F);
	private float scroll;
	private float contentH;

	public LogPage(HallScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.page.log");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine6.page.log.short");
	}

	@Override
	public int icon() {
		return Glyphs.HELP;
	}

	@Override
	protected void build() {
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		ListTag log = info().getListOrEmpty("log");
		float maxScroll = Math.max(0.0F, contentH - h);
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		float offset = scrollShown.to(scroll, seconds);
		float scale = w >= 280 ? 0.62F : 0.56F;
		float stampW = 30.0F;
		float textX = x + stampW + 6.0F;
		float textW = w - stampW - 12.0F;
		g.enableScissor(x, y, x + w, y + h);
		float cursor = y + 2.0F - offset;
		if (log.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.hall.ui.log_empty"), x + 6.0F, cursor + 4.0F, w - 12.0F, Palette.INK_SOFT, 0.7F, false);
		}
		long today = info().getLongOr("day", 0L);
		long lastDay = Long.MIN_VALUE;
		for (int i = log.size() - 1; i >= 0; i--) {
			CompoundTag entry = log.getCompoundOrEmpty(i);
			Component text = entry.read("text", ComponentSerialization.CODEC).orElse(Component.empty());
			long day = entry.getLongOr("day", 0L);
			float height = Math.max(9.0F * scale, Brush.wrapHeight(font, text, textW, scale));
			if (cursor + height >= y && cursor <= y + h) {
				if (day != lastDay) {
					// A seal with the day index where a new day begins.
					Component stamp = day == today ? Component.translatable("kultivator.hall.ui.log_today") : Component.translatable("kultivator.hall.ui.log_day", day);
					g.fill(x + 2, Math.round(cursor), x + 2 + Math.round(stampW), Math.round(cursor + 8.0F), Palette.argb(0xD0, Palette.SEAL));
					Brush.fitCentered(g, font, stamp, x + 2.0F + stampW / 2.0F, cursor + 1.0F, stampW - 2.0F, Palette.TALISMAN, 0.5F, 0.35F, false);
				} else {
					Brush.rect(g, x + 2.0F + stampW / 2.0F - 0.5F, cursor - 1.0F, x + 2.0F + stampW / 2.0F + 0.5F, cursor + 7.0F,
							Palette.argb(0x70, Palette.SEAL));
				}
				Brush.wrap(g, font, text, textX, cursor, textW, Palette.INK, scale, false);
			}
			lastDay = day;
			cursor += height + 3.0F;
			Brush.rect(g, textX, cursor - 1.8F, x + w - 6.0F, cursor - 1.3F, Palette.argb(0x30, Palette.GOLD_DARK));
		}
		contentH = cursor + offset - y + 2.0F;
		g.disableScissor();
		if (maxScroll > 0.0F) {
			float bead = y + 3.0F + (h - 6.0F) * (offset / maxScroll);
			g.fill(x + w - 2, y, x + w - 1, y + h, Palette.CORD);
			Brush.disc(g, x + w - 1.5F, bead, 2.2F, Palette.JADE);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (inside(mouseX, mouseY)) {
			scroll -= (float) amount * 16.0F;
			return true;
		}
		return false;
	}
}
