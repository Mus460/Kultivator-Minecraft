package dev.kultivator.client.musuh;

import dev.kultivator.client.dunia.DuniaPage;
import dev.kultivator.client.dunia.DuniaScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The Musuh tablet of the Dunia screen (DESIGN 25.10): the next raid ("Serbuan berikutnya: hari 31 · Sekte Bulan Darah")
 * as one line with a talisman to the sect's own raid page (Aula › Wilayah › Pertahanan holds the full schedule, DESIGN
 * 33.6 R7), the ambush cooldown and the rogues slain, then the last ten encounters (ambushes, camps, raids) on bamboo
 * slips, newest first, scrolling with the wheel. Read-only; the data comes from {@link MusuhClient}.
 */
public class MusuhPage extends DuniaPage {

	private final Eased scrollShown = new Eased(14.0F);
	private float scroll;
	private float contentH;

	public MusuhPage(DuniaScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine8.tab.musuh");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine8.tab.musuh.short");
	}

	@Override
	public int icon() {
		return Glyphs.SEAL;
	}

	@Override
	protected void build() {
		// The sect's raid page (schedule, omen, defence score, raid log) lives in the hall; this talisman leads there.
		int bw = Math.max(64, Math.min(104, Math.round(w * 0.36F)));
		TalismanButton defence = add(new TalismanButton(x + w - bw - 4, y + 16, bw, 11,
				() -> Component.translatable("kultivator.uxpages.musuh.to_defence")));
		defence.onPress(() -> dev.kultivator.client.hub.HubNav.openHall(Halls.PAGE_PERTAHANAN));
		defence.tooltip(() -> List.of(Component.translatable("kultivator.uxpages.musuh.to_defence.title"),
				Component.translatable("kultivator.uxpages.musuh.to_defence.tip").withStyle(ChatFormatting.GRAY)));
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag data = MusuhClient.data();
		heading(g, title(), x + 2.0F, y, w - 4.0F, 0.8F);
		float scale = w >= 260 ? 0.66F : 0.6F;
		float cursor = y + 14.0F;
		if (data.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.musuh.ui.loading"), x + 4.0F, cursor, w - 8.0F, Palette.INK_SOFT, scale, false);
			return;
		}
		// The schedule card.
		CompoundTag raid = data.getCompoundOrEmpty("raid");
		int top = Math.round(cursor);
		panel(g, x + 2, top, x + w - 2, top + 38);
		int bw = Math.max(64, Math.min(104, Math.round(w * 0.36F)));
		Brush.fit(g, font, MusuhText.nextRaid(raid), x + 6.0F, cursor + 3.0F, w - bw - 16.0F, Palette.SEAL_DARK, scale, scale * 0.7F, false);
		long cd = data.getLongOr("ambush_cd", 0L);
		Component ambush = !data.getBooleanOr("ambush_on", true) ? Component.translatable("kultivator.musuh.ui.ambush_off")
				: cd <= 0L ? Component.translatable("kultivator.musuh.ui.ambush_ready")
				: Component.translatable("kultivator.musuh.ui.ambush_cd", MusuhText.days(cd));
		Brush.fit(g, font, ambush, x + 6.0F, cursor + 14.0F, w - 12.0F, Palette.INK, scale, scale * 0.7F, false);
		Brush.fit(g, font, Component.translatable("kultivator.musuh.ui.kills", data.getIntOr("kills", 0)), x + 6.0F, cursor + 25.0F, w - 12.0F,
				Palette.INK_SOFT, scale, scale * 0.7F, false);
		cursor += 42.0F;
		heading(g, Component.translatable("kultivator.musuh.ui.encounters"), x + 2.0F, cursor, w - 4.0F, 0.7F);
		cursor += 12.0F;
		// The encounter slips, scrolling inside the rest of the box.
		int listTop = Math.round(cursor);
		int listBottom = y + h - 10;
		ListTag log = data.getListOrEmpty("log");
		float maxScroll = Math.max(0.0F, contentH - (listBottom - listTop));
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		float offset = scrollShown.to(scroll, seconds);
		g.enableScissor(x, listTop, x + w, listBottom);
		float row = listTop - offset;
		if (log.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.musuh.ui.no_encounters"), x + 6.0F, row + 2.0F, w - 12.0F, Palette.INK_SOFT, scale, false);
		}
		for (int i = log.size() - 1; i >= 0; i--) {
			CompoundTag entry = log.getCompoundOrEmpty(i);
			boolean won = entry.getBooleanOr("won", false);
			int kind = Math.max(0, Math.min(2, entry.getIntOr("kind", 0)));
			int rowTop = Math.round(row);
			g.fill(x + 4, rowTop, x + w - 4, rowTop + 11, Palette.argb(0x40, i % 2 == 0 ? Palette.BAMBOO : Palette.BAMBOO_ALT));
			g.fill(x + 4, rowTop, x + 6, rowTop + 11, won ? Palette.JADE : Palette.SEAL);
			Component line = Component.translatable("kultivator.musuh.ui.encounter", entry.getLongOr("day", 0L),
					Component.translatable("kultivator.musuh.kind." + kind), entry.getIntOr("n", 0),
					Component.translatable(won ? "kultivator.musuh.ui.won" : "kultivator.musuh.ui.lost"));
			Brush.fit(g, font, line, x + 9.0F, row + 2.0F, w - 16.0F, won ? Palette.INK : Palette.SEAL_DARK, scale, scale * 0.7F, false);
			row += 13.0F;
		}
		contentH = row + offset - listTop;
		g.disableScissor();
		if (maxScroll > 0.0F) {
			float bead = listTop + 3.0F + (listBottom - listTop - 6.0F) * (offset / maxScroll);
			g.fill(x + w - 2, listTop, x + w - 1, listBottom, Palette.CORD);
			Brush.disc(g, x + w - 1.5F, bead, 2.2F, Palette.JADE);
		}
		Brush.fit(g, font, Component.translatable("kultivator.musuh.ui.source"), x + 4.0F, y + h - 8.0F, w - 8.0F, Palette.INK_FAINT, 0.5F, 0.35F, false);
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
