package dev.kultivator.client.desa;

import dev.kultivator.client.Keys;
import dev.kultivator.client.hall.HallPage;
import dev.kultivator.client.hall.HallScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.network.Actions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The Desa tablet of the Sect Hall scroll ({@code Halls.PAGE_DESA}, DESIGN 25.10): a jade coin per vassal on the left and
 * the chosen village's scroll card on the right (loyalty bar, villagers, next tribute, the owed buffer, the open request
 * with "Ambil Surat", the Pelindung Desa with assign / release, the last raid), plus the Penjarahan log. Reads the
 * {@code desa} part of the hall info (filled by {@code desa.Desa}); a sect mate sees it read-only.
 */
public class HallDesaPage extends HallPage {

	private static final int LOG = -1;
	private static int chosen;
	private int lastCount = -1;

	public HallDesaPage(HallScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine8.page.desa");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine8.page.desa.short");
	}

	@Override
	public int icon() {
		return Glyphs.HALL;
	}

	@Override
	public boolean shown() {
		CompoundTag desa = desa();
		return info().getBooleanOr("has", false) && desa.getBooleanOr("on", false);
	}

	private CompoundTag desa() {
		return info().getCompoundOrEmpty("desa");
	}

	private ListTag villages() {
		return desa().getListOrEmpty("villages");
	}

	private boolean own() {
		return desa().getBooleanOr("own", false) && !info().getBooleanOr("readonly", false);
	}

	private CompoundTag current() {
		ListTag list = villages();
		return chosen >= 0 && chosen < list.size() ? list.getCompoundOrEmpty(chosen) : new CompoundTag();
	}

	@Override
	protected void build() {
		ListTag list = villages();
		lastCount = list.size();
		if (chosen >= list.size()) {
			chosen = list.isEmpty() ? LOG : 0;
		}
		int colW = Math.max(62, Math.min(84, Math.round(w * 0.28F)));
		int top = y + 14;
		for (int i = 0; i < list.size(); i++) {
			int index = i;
			JadeCoin coin = new JadeCoin(x + 2, top + i * 14, colW, 12, () -> Component.literal(villages().getCompoundOrEmpty(index).getStringOr("name", "?")),
					() -> chosen == index);
			coin.onPress(() -> chosen = index);
			coin.tooltip(() -> List.of(DesaCards.headline(villages().getCompoundOrEmpty(index))));
			add(coin);
		}
		JadeCoin log = new JadeCoin(x + 2, top + Math.max(1, list.size()) * 14 + 4, colW, 12, () -> Component.translatable(DesaCards.P + "log"),
				() -> chosen == LOG);
		log.onPress(() -> chosen = LOG);
		add(log);

		int cardX = x + colW + 8;
		int cardW = x + w - cardX;
		int buttonW = Math.max(60, (cardW - 6) / 2);
		TalismanButton letter = new TalismanButton(cardX, y + h - 15, buttonW, 15, () -> Component.translatable(DesaCards.P + "letter"));
		letter.enabledWhen(() -> Keys.connected() && own() && chosen >= 0 && !current().getStringOr("req", "").isEmpty());
		letter.onPress(() -> send(Actions.DESA, 0, "letter:" + current().getIntOr("id", 0)));
		letter.tooltip(() -> List.of(Component.translatable(DesaCards.P + "letter.tip").withStyle(ChatFormatting.GRAY)));
		add(letter);
		TalismanButton guard = new TalismanButton(cardX + buttonW + 6, y + h - 15, Math.min(buttonW, cardW - buttonW - 6), 15,
				() -> Component.translatable(current().getStringOr("guard", "").isEmpty() ? DesaCards.P + "assign" : DesaCards.P + "release"));
		guard.enabledWhen(() -> Keys.connected() && own() && chosen >= 0 && current().getBooleanOr("pledged", false));
		guard.onPress(() -> send(Actions.DESA, 0, (current().getStringOr("guard", "").isEmpty() ? "guard:" : "release:") + current().getIntOr("id", 0)));
		guard.tooltip(() -> List.of(Component.translatable(current().getStringOr("guard", "").isEmpty() ? DesaCards.P + "assign.tip"
				: DesaCards.P + "release.tip").withStyle(ChatFormatting.GRAY)));
		add(guard);
	}

	@Override
	protected boolean visible(dev.kultivator.client.screen.ui.Widget widget) {
		// The card's talismans are hidden on the log view.
		return chosen >= 0 || !(widget instanceof TalismanButton);
	}

	@Override
	public boolean needsRebuild() {
		return villages().size() != lastCount;
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag desa = desa();
		heading(g, Component.translatable(DesaCards.P + "hall_title", desa.getIntOr("count", 0), desa.getIntOr("max", 1)), x + 2.0F, y, w - 4.0F, 0.8F);
		int colW = Math.max(62, Math.min(84, Math.round(w * 0.28F)));
		float cardX = x + colW + 8.0F;
		float cardW = x + w - cardX;
		float scale = cardW >= 180 ? 0.68F : 0.6F;
		ListTag list = villages();
		if (chosen == LOG) {
			drawLog(g, desa, cardX, cardW, scale);
			return;
		}
		if (list.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(DesaCards.P + "none", desa.getIntOr("range", 256)), cardX, y + 14.0F, cardW, Palette.INK_SOFT, scale,
					false);
			return;
		}
		CompoundTag v = current();
		long today = desa.getLongOr("day", 0L);
		panel(g, Math.round(cardX), y + 13, x + w - 1, y + h - 18);
		float cursor = y + 16.0F;
		float tx = cardX + 4.0F;
		float tw = cardW - 8.0F;
		Brush.fit(g, font, Brush.bold(Component.literal(v.getStringOr("name", "?"))), tx, cursor, tw - 8.0F, Palette.SEAL_DARK, scale + 0.12F, 0.5F, false);
		DesaCards.seal(g, font, v, cardX + cardW - 7.0F, cursor + 3.0F);
		cursor += 11.0F;
		cursor += Brush.wrap(g, font, DesaCards.headline(v), tx, cursor, tw, Palette.INK, scale, false) + 1.0F;
		if (v.getBooleanOr("pledged", false)) {
			DesaCards.loyaltyBar(g, tx, cursor, tw, v.getIntOr("loyalty", 0));
			cursor += 6.0F;
			cursor += Brush.wrap(g, font, Component.translatable(DesaCards.P + "tribute_owed", v.getLongOr("tribute_in", 0L), v.getIntOr("owed", 0)), tx, cursor,
					tw, Palette.INK_SOFT, scale - 0.04F, false) + 1.0F;
			Component request = DesaCards.request(v);
			cursor += Brush.wrap(g, font, request == null ? Component.translatable(DesaCards.P + "no_request") : request, tx, cursor, tw,
					request == null ? Palette.INK_FAINT : Palette.GOLD_DARK, scale - 0.04F, false) + 1.0F;
			cursor += Brush.wrap(g, font, DesaCards.guard(v), tx, cursor, tw, Palette.INK_SOFT, scale - 0.04F, false) + 1.0F;
			Component raid = DesaCards.lastRaid(v, today);
			if (raid != null) {
				cursor += Brush.wrap(g, font, raid, tx, cursor, tw, v.getIntOr("last_result", 0) == 1 ? DesaCards.LOYAL : DesaCards.WARN, scale - 0.04F, false)
						+ 1.0F;
			}
			if (v.getBooleanOr("siege", false)) {
				Brush.wrap(g, font, Brush.bold(Component.translatable(DesaCards.P + "siege")), tx, cursor, tw, DesaCards.WARN, scale, false);
			}
		} else {
			Brush.wrap(g, font, Component.translatable(DesaCards.P + "waiting_long"), tx, cursor, tw, Palette.INK_SOFT, scale - 0.04F, false);
		}
	}

	private void drawLog(GuiGraphicsExtractor g, CompoundTag desa, float cardX, float cardW, float scale) {
		float cursor = y + 14.0F;
		cursor += Brush.wrap(g, font, Brush.bold(Component.translatable(DesaCards.P + "log_title")), cardX, cursor, cardW, Palette.SEAL_DARK, scale, false)
				+ 2.0F;
		ListTag log = desa.getListOrEmpty("log");
		if (log.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(DesaCards.P + "log_empty"), cardX, cursor, cardW, Palette.INK_FAINT, scale, false);
			return;
		}
		for (int i = log.size() - 1; i >= 0 && cursor < y + h - 8.0F; i--) {
			CompoundTag entry = log.getCompoundOrEmpty(i);
			int color = entry.getIntOr("result", 0) == 1 ? DesaCards.LOYAL : DesaCards.WARN;
			cursor += Brush.wrap(g, font, DesaCards.logLine(entry), cardX, cursor, cardW, color, scale - 0.04F, false) + 1.0F;
		}
	}
}
