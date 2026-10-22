package dev.kultivator.client.screen;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Widget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Pengaturan, the hub's last section (DESIGN 33.5): one group at a time, chosen with the five sub-coins of the hub
 * header (Tampilan & Efek, Kamera & Film, Suara, HUD, Kontrol; {@link #showGroup}). Tampilan & Efek keeps the effect
 * quality talismans on the left (Minimal / Normal / Maksimal / Otomatis, the chosen one stamped) and its rows on the
 * right; the other groups lay their rows in two columns (one on a narrow scroll). Jade coins are toggles, talismans
 * cycle; every stored key, default and setter is the one it always was ({@link SettingsRows} has the group table).
 * Kontrol lists every Kultivator key live and opens vanilla's key binds screen. Fits 320×240.
 */
final class SettingsPage extends Page {

	private static final String C = "kultivator.codex.settings.";
	/** The "?" topic per group (audit M5, R25): Tampilan, Film, Suara, HUD, Kontrol. */
	private static final String[] HELP = {"visuals", "momen", "suara", "hud", "tombol"};
	private static final int GAP = 12;

	/** A heading, text line or note drawn by the page itself (not a control). */
	private record Drawn(SettingsRows.Item item, int x, int baseY, int w, int h) {
	}

	private final SettingsRows rows;
	private int group = SettingsRows.TAMPILAN;
	private int leftW;
	private int rightX;
	private int rightW;
	private float descY;
	private final List<Widget> column = new ArrayList<>();
	private final List<Integer> columnY = new ArrayList<>();
	private final List<Drawn> drawn = new ArrayList<>();
	private final Eased scrollShown = new Eased(14.0F);
	private float scroll;
	private float columnHeight;
	/** The left edge of the scrolling area (the mouse wheel scrolls only there). */
	private int scrollX;

	SettingsPage(CultivationScreen screen) {
		super(screen);
		this.rows = new SettingsRows(screen);
	}

	@Override
	Component title() {
		return Component.translatable("kultivator.screen.tab.settings");
	}

	@Override
	Component shortName() {
		return Component.translatable("kultivator.screen.tab.settings.short");
	}

	// ================================================================ hub header (C33.N3, C33.G1)

	/** Shows one group ({@code HubNav.GROUP_*}: 0 Tampilan & Efek, 1 Kamera & Film, 2 Suara, 3 HUD, 4 Kontrol). */
	public void showGroup(int group) {
		int next = SettingsRows.clamp(group);
		if (next == this.group) {
			return;
		}
		this.group = next;
		scroll = 0.0F;
		scrollShown.set(0.0F);
		if (w > 0 && h > 0) {
			layout(x, y, w, h);
		}
	}

	/** The group shown now. */
	public int group() {
		return group;
	}

	@Override
	public List<Component> subNames() {
		List<Component> names = new ArrayList<>();
		for (int i = 0; i < SettingsRows.GROUP_IDS.length; i++) {
			names.add(SettingsRows.groupName(i));
		}
		return names;
	}

	@Override
	public void showSub(int index) {
		showGroup(index);
	}

	@Override
	public String helpTopic() {
		return HELP[group];
	}

	// ================================================================ layout

	@Override
	protected void build() {
		column.clear();
		columnY.clear();
		drawn.clear();
		int row = Math.max(12, Math.min(16, (h - 30) / 9));
		List<SettingsRows.Item> items = rows.group(group);
		if (group == SettingsRows.TAMPILAN) {
			leftW = Math.round((w - GAP) * 0.42F);
			rightX = x + leftW + GAP;
			rightW = w - leftW - GAP;
			buildQuality();
			scrollX = rightX - 4;
			columnHeight = place(strip(items), rightX, rightW, row) - y + 4;
			return;
		}
		leftW = 0;
		scrollX = x;
		boolean two = w >= 250;
		int colW = two ? (w - GAP - 4) / 2 : w - 4;
		int split = -1;
		if (two) {
			for (int i = 0; i < items.size(); i++) {
				if (items.get(i).type() == SettingsRows.Type.BREAK) {
					split = i;
					break;
				}
			}
			if (split < 0 && height(items, colW, row) > h) {
				split = balance(items, colW, row);
			}
		}
		if (split < 0) {
			// fits in one column (or the scroll is narrow): one column, not stretched across a wide scroll
			int width = two ? Math.min(w - 4, Math.max(colW, 260)) : w - 4;
			columnHeight = place(strip(items), x, width, row) - y + 4;
			return;
		}
		int bottomA = place(strip(items.subList(0, split)), x, colW, row);
		int bottomB = place(strip(items.subList(split, items.size())), x + colW + GAP, colW, row);
		columnHeight = Math.max(bottomA, bottomB) - y + 4;
	}

	/** Effect quality: four talismans, one above the other, then Otomatis (the left column of Tampilan & Efek). */
	private void buildQuality() {
		int th = Math.max(14, Math.min(19, (h - 70) / 4));
		int ty = y + 15;
		for (EffectQuality.Level level : EffectQuality.Level.values()) {
			TalismanButton talisman = new TalismanButton(x + 2, ty, leftW - 4, th, level::displayName);
			talisman.stampedWhen(() -> !EffectQuality.auto() && EffectQuality.chosenLevel() == level);
			talisman.onPress(() -> EffectQuality.setLevel(level));
			talisman.tooltip(() -> List.of(level.displayName(), Component.translatable("kultivator.screen.quality." + level.id() + ".tip")));
			add(talisman);
			ty += th + 4;
		}
		TalismanButton auto = new TalismanButton(x + 2, ty, leftW - 4, th, () -> Component.translatable(C + "auto"));
		auto.stampedWhen(EffectQuality::auto);
		auto.onPress(() -> EffectQuality.setAuto(true));
		auto.tooltip(() -> List.of(Component.translatable(C + "auto"), Component.translatable(C + "auto.tip"),
				Component.translatable(C + "auto.now", EffectQuality.level().displayName()).withStyle(ChatFormatting.GRAY)));
		add(auto);
		ty += th + 4;
		descY = ty + 2;
	}

	/** The items without column breaks. */
	private static List<SettingsRows.Item> strip(List<SettingsRows.Item> items) {
		List<SettingsRows.Item> out = new ArrayList<>(items.size());
		for (SettingsRows.Item item : items) {
			if (item.type() != SettingsRows.Type.BREAK) {
				out.add(item);
			}
		}
		return out;
	}

	/** The split index that makes the two columns most even (never right after a heading). */
	private int balance(List<SettingsRows.Item> items, int colW, int row) {
		int best = -1;
		int bestHeight = Integer.MAX_VALUE;
		for (int i = 1; i < items.size(); i++) {
			if (items.get(i - 1).type() == SettingsRows.Type.HEADING) {
				continue;
			}
			int tall = Math.max(height(items.subList(0, i), colW, row), height(items.subList(i, items.size()), colW, row));
			if (tall < bestHeight) {
				bestHeight = tall;
				best = i;
			}
		}
		return best;
	}

	/** The height of {@code items} laid out in one column of width {@code colW}. */
	private int height(List<SettingsRows.Item> items, int colW, int row) {
		int total = 0;
		boolean first = true;
		for (SettingsRows.Item item : items) {
			if (item.type() == SettingsRows.Type.BREAK) {
				continue;
			}
			total += size(item, colW, row, first);
			first = false;
		}
		return total;
	}

	/** The vertical room one item takes (a heading after other rows gets 2 px more above it). */
	private int size(SettingsRows.Item item, int colW, int row, boolean first) {
		return switch (item.type()) {
			case HEADING -> first ? 15 : 17;
			case COIN -> row;
			case CYCLE -> row + 2;
			case BUTTON -> row + 4;
			case LINE -> lineHeight(row);
			case NOTE -> Math.round(Brush.wrapHeight(font, item.label().get(), colW - 8.0F, 0.65F)) + 4;
			case BREAK -> 0;
		};
	}

	private static int lineHeight(int row) {
		return Math.max(9, row - 4);
	}

	/** Lays {@code items} out from the top of the box in the column at {@code cx}; returns the y under the last one. */
	private int place(List<SettingsRows.Item> items, int cx, int cw, int row) {
		int coin = row - 3;
		int cy = y;
		boolean first = true;
		for (SettingsRows.Item item : items) {
			int size = size(item, cw, row, first);
			switch (item.type()) {
				case HEADING -> drawn.add(new Drawn(item, cx, first ? cy : cy + 2, cw, 12));
				case COIN -> {
					JadeCoin toggle = new JadeCoin(cx + 2, cy, cw - 6, coin, item.label(), item.on());
					toggle.askingWhen(item.asking());
					control(toggle, item, cy);
				}
				case CYCLE -> control(new TalismanButton(cx + 2, cy, cw - 6, coin + 2, item.label()), item, cy);
				case BUTTON -> control(new TalismanButton(cx + 2, cy + 1, cw - 6, coin + 3, item.label()), item, cy + 1);
				case LINE, NOTE -> drawn.add(new Drawn(item, cx, cy, cw, size));
				case BREAK -> {
				}
			}
			cy += size;
			first = false;
		}
		return cy;
	}

	private void control(Widget widget, SettingsRows.Item item, int baseY) {
		widget.enabledWhen(item.enabled());
		if (item.action() != null) {
			widget.onPress(item.action());
		}
		widget.tooltip(item.tooltip());
		add(widget);
		column.add(widget);
		columnY.add(baseY);
	}

	@Override
	protected boolean visible(Widget widget) {
		if (!column.contains(widget)) {
			return true;
		}
		return widget.y + widget.h > y && widget.y < y + h;
	}

	// ================================================================ drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (group == SettingsRows.TAMPILAN) {
			heading(g, Component.translatable("kultivator.screen.settings.quality"), x + 2.0F, y, leftW - 4.0F, 0.85F);
			EffectQuality.Level level = EffectQuality.level();
			float room = y + h - descY;
			if (room > 10.0F) {
				Component tip = EffectQuality.auto() ? Component.translatable(C + "auto.tip") : Component.translatable("kultivator.screen.quality." + level.id()
						+ ".tip");
				Brush.wrap(g, font, tip, x + 4.0F, descY, leftW - 8.0F, Palette.INK_SOFT, 0.68F, false);
			}
		}
		float maxScroll = Math.max(0.0F, columnHeight - h);
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		int offset = Math.round(scrollShown.to(scroll, seconds));
		for (int i = 0; i < column.size(); i++) {
			column.get(i).y = columnY.get(i) - offset;
		}
		g.enableScissor(x, y, x + w, y + h);
		int stripe = 0;
		for (Drawn item : drawn) {
			int top = item.baseY() - offset;
			if (top + item.h() <= y || top >= y + h) {
				if (item.item().type() == SettingsRows.Type.LINE) {
					stripe++;
				}
				continue;
			}
			switch (item.item().type()) {
				case HEADING -> heading(g, item.item().label().get(), item.x() + 2.0F, top, item.w() - 6.0F, 0.85F);
				case LINE -> {
					drawLine(g, item, top, stripe++ % 2 == 0);
					if (inside(mouseX, mouseY) && mouseX >= item.x() && mouseX < item.x() + item.w() && mouseY >= top && mouseY < top + item.h()) {
						screen.showTooltip(item.item().tooltip().get());
					}
				}
				case NOTE -> Brush.wrap(g, font, item.item().label().get(), item.x() + 4.0F, top + 1.0F, item.w() - 8.0F,
						item.item().color().getAsInt(), 0.65F, false);
				default -> {
				}
			}
		}
	}

	/** One key line: the key's name on the left, the bound key on the right (orange when another key shares it). */
	private void drawLine(GuiGraphicsExtractor g, Drawn line, int top, boolean shaded) {
		int lx = line.x() + 2;
		int lw = line.w() - 6;
		if (shaded) {
			g.fill(lx, top, lx + lw, top + line.h(), Palette.argb(0x14, Palette.INK));
		}
		float ty = top + (line.h() - 7.0F) / 2.0F;
		Component value = line.item().value() != null ? line.item().value().get() : Component.empty();
		float valueRoom = lw * 0.42F;
		float valueScale = Brush.fitScale(font, value, valueRoom, 0.75F, 0.6F);
		float valueW = Math.min(valueRoom, font.width(value) * valueScale);
		Brush.fit(g, font, value, lx + lw - 2.0F - valueW, ty, valueRoom, line.item().color().getAsInt(), 0.75F, 0.6F, false);
		Brush.fit(g, font, line.item().label().get(), lx + 2.0F, ty, lw - valueW - 8.0F, Palette.INK, 0.75F, 0.6F, false);
	}

	@Override
	protected void drawAbove(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		g.disableScissor();
		float maxScroll = Math.max(0.0F, columnHeight - h);
		if (maxScroll > 0.0F) {
			float bead = y + 3.0F + (h - 6.0F) * (scrollShown.get() / maxScroll);
			g.fill(x + w - 2, y, x + w - 1, y + h, Palette.CORD);
			Brush.disc(g, x + w - 1.5F, bead, 2.2F, Palette.JADE);
			Brush.disc(g, x + w - 2.0F, bead - 0.6F, 0.8F, Palette.JADE_PALE);
		}
	}

	@Override
	boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (inside(mouseX, mouseY) && mouseX >= scrollX && columnHeight > h) {
			scroll -= (float) amount * 14.0F;
			return true;
		}
		return false;
	}
}
