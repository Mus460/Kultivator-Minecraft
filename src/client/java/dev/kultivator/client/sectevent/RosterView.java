package dev.kultivator.client.sectevent;

import dev.kultivator.client.hall.HallScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A scrolling list of the master's disciples on a parchment panel (one row each: a seal in the root colour, the name and
 * a short tag on the right), with single or multiple selection. Rows that cannot be chosen for the current task are
 * greyed. The selection is kept by soul, so it survives new details.
 */
final class RosterView {

	static final int ROW_H = 11;

	private final Font font;
	private final int max;
	private final Predicate<CompoundTag> shown;
	private final Predicate<CompoundTag> enabled;
	private final Function<CompoundTag, Component> tag;
	private final Set<String> selected;
	private int x;
	private int y;
	private int w;
	private int h;
	private int scroll;

	/**
	 * {@code max} 1 for a single choice; {@code selected} is the caller's own set (kept across rebuilds).
	 */
	RosterView(Font font, int max, Predicate<CompoundTag> shown, Predicate<CompoundTag> enabled, Function<CompoundTag, Component> tag,
			Set<String> selected) {
		this.font = font;
		this.max = Math.max(1, max);
		this.shown = shown;
		this.enabled = enabled;
		this.tag = tag;
		this.selected = selected;
	}

	void layout(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	List<CompoundTag> rows() {
		List<CompoundTag> list = new ArrayList<>();
		for (CompoundTag entry : SectEventClient.roster()) {
			if (shown.test(entry)) {
				list.add(entry);
			}
		}
		return list;
	}

	/** The chosen rows that are still there and allowed, in list order. */
	List<CompoundTag> chosen() {
		List<CompoundTag> list = new ArrayList<>();
		Set<String> present = new LinkedHashSet<>();
		for (CompoundTag entry : rows()) {
			String soul = entry.getStringOr("soul", "");
			present.add(soul);
			if (selected.contains(soul) && enabled.test(entry)) {
				list.add(entry);
			}
		}
		selected.retainAll(present);
		return list;
	}

	@Nullable CompoundTag first() {
		List<CompoundTag> list = chosen();
		return list.isEmpty() ? null : list.get(0);
	}

	/** Entity ids of the chosen rows as "12,34". */
	String ids() {
		StringBuilder builder = new StringBuilder();
		for (CompoundTag entry : chosen()) {
			if (builder.length() > 0) {
				builder.append(',');
			}
			builder.append(entry.getIntOr("id", 0));
		}
		return builder.toString();
	}

	private int visible() {
		return Math.max(1, (h - 4) / ROW_H);
	}

	void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, HallScreen screen) {
		g.fill(x, y, x + w, y + h, Palette.argb(0x30, Palette.PAPER_DEEP));
		Brush.frame(g, x, y, x + w, y + h, Palette.argb(0xB0, Palette.GOLD));
		List<CompoundTag> rows = rows();
		if (rows.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.sectevent.ui.roster_empty"), x + 4.0F, y + 4.0F, w - 8.0F, Palette.INK_FAINT, 0.6F, false);
			return;
		}
		scroll = Math.max(0, Math.min(scroll, rows.size() - visible()));
		g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
		for (int i = 0; i < visible() && scroll + i < rows.size(); i++) {
			CompoundTag entry = rows.get(scroll + i);
			int ry = y + 2 + i * ROW_H;
			boolean on = enabled.test(entry);
			boolean picked = selected.contains(entry.getStringOr("soul", ""));
			boolean over = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW_H;
			if (picked) {
				g.fill(x + 1, ry, x + w - 1, ry + ROW_H - 1, Palette.argb(0x90, Palette.JADE_PALE));
				g.fill(x + 1, ry, x + 3, ry + ROW_H - 1, Palette.JADE);
			} else if (over) {
				g.fill(x + 1, ry, x + w - 1, ry + ROW_H - 1, Palette.argb(0x40, 0xFFFFFF));
			}
			int seal = Palette.opaque(entry.getIntOr("color", 0xCFE8FF));
			g.fill(x + 5, ry + 3, x + 9, ry + 7, on ? seal : Palette.grey(seal, 0.8F));
			Component right = tag.apply(entry);
			float rightW = right == null ? 0.0F : Math.min(w * 0.42F, font.width(right) * 0.55F);
			Brush.fit(g, font, SectEventClient.name(entry, "name"), x + 12.0F, ry + 2.0F, w - 16.0F - rightW, on ? Palette.INK : Palette.INK_FAINT,
					0.65F, 0.45F, false);
			if (right != null) {
				Brush.fit(g, font, right, x + w - 3.0F - rightW, ry + 2.5F, rightW, on ? Palette.INK_SOFT : Palette.INK_FAINT, 0.55F, 0.4F, false);
			}
			if (over) {
				screen.showTooltip(tooltip(entry, on));
			}
		}
		g.disableScissor();
		if (rows.size() > visible()) {
			float bar = (float) visible() / rows.size();
			float top = (float) scroll / rows.size();
			Brush.rect(g, x + w - 2.5F, y + 2.0F + top * (h - 4), x + w - 1.0F, y + 2.0F + (top + bar) * (h - 4), Palette.argb(0xC0, Palette.GOLD_DARK));
		}
	}

	private List<Component> tooltip(CompoundTag entry, boolean on) {
		List<Component> lines = new ArrayList<>();
		lines.add(SectEventClient.name(entry, "name"));
		Rank rank = new Rank(Realm.byOrdinal(entry.getIntOr("realm", 0)), entry.getIntOr("layer", 0), entry.getIntOr("stage", 0));
		lines.add(rank.displayName().withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("kultivator.sectevent.ui.roster_line", SectEventPage.sectRank(entry.getIntOr("rank", 1)),
				entry.getIntOr("pk", 0), entry.getIntOr("stars", 0)).withStyle(ChatFormatting.DARK_AQUA));
		String duty = entry.getStringOr("duty", "");
		if (!duty.isEmpty()) {
			lines.add(Component.translatable("kultivator.sectevent.ui.on_duty", Component.translatable("kultivator.sectevent.duty." + duty))
					.withStyle(ChatFormatting.GOLD));
		}
		if (entry.getBooleanOr("injured", false)) {
			lines.add(Component.translatable("kultivator.sectevent.ui.injured").withStyle(ChatFormatting.RED));
		}
		if (!entry.getBooleanOr("near", false)) {
			lines.add(Component.translatable("kultivator.sectevent.ui.far").withStyle(ChatFormatting.DARK_GRAY));
		}
		if (!on) {
			lines.add(Component.translatable("kultivator.sectevent.ui.not_allowed").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
		return lines;
	}

	/** Toggles the row under the mouse. True when the click was inside the list. */
	boolean click(double mouseX, double mouseY) {
		if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) {
			return false;
		}
		int index = scroll + (int) ((mouseY - y - 2) / ROW_H);
		List<CompoundTag> rows = rows();
		if (index < 0 || index >= rows.size() || mouseY < y + 2) {
			return true;
		}
		CompoundTag entry = rows.get(index);
		if (!enabled.test(entry)) {
			UiSound.denied();
			return true;
		}
		String soul = entry.getStringOr("soul", "");
		if (selected.remove(soul)) {
			UiSound.toggle(false);
			return true;
		}
		if (max == 1) {
			selected.clear();
		} else if (chosen().size() >= max) {
			UiSound.denied();
			return true;
		}
		selected.add(soul);
		UiSound.toggle(true);
		return true;
	}

	/** The row under the mouse, or null. */
	@Nullable CompoundTag rowAt(double mouseX, double mouseY) {
		if (mouseX < x || mouseX >= x + w || mouseY < y + 2 || mouseY >= y + h) {
			return null;
		}
		int index = scroll + (int) ((mouseY - y - 2) / ROW_H);
		List<CompoundTag> rows = rows();
		return index >= 0 && index < rows.size() && index - scroll < visible() ? rows.get(index) : null;
	}

	boolean allowed(CompoundTag entry) {
		return enabled.test(entry);
	}

	/** Replaces the selection (for lists whose selection mirrors server state). */
	void select(Set<String> souls) {
		selected.clear();
		selected.addAll(souls);
	}

	boolean scroll(double mouseX, double mouseY, double amount) {
		if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) {
			return false;
		}
		scroll = Math.max(0, scroll + (amount < 0.0D ? 1 : -1));
		return true;
	}
}
