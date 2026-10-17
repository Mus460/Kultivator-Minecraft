package dev.kultivator.client.hall;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Aula tablet (DESIGN 20.3.1): the hall's name (renamable), level seal and renown bar, the numbers its level
 * decides, the building checklist with hints, the Formasi Agung line, the schedule of the next events and the teams
 * away. The right column scrolls with the wheel.
 */
public class AulaPage extends HallPage {

	private static final int BUTTON_H = 14;
	private final Eased scrollShown = new Eased(14.0F);
	private float scroll;
	private float contentH;
	private int leftW;
	private InkField nameField;
	private final List<float[]> hoverBoxes = new ArrayList<>();
	private final List<List<Component>> hoverTips = new ArrayList<>();

	public AulaPage(HallScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.page.aula");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine6.page.aula.short");
	}

	@Override
	public int icon() {
		return Glyphs.HALL;
	}

	private boolean readonly() {
		return info().getBooleanOr("readonly", false);
	}

	@Override
	protected void build() {
		leftW = Math.max(110, Math.min(150, Math.round(w * 0.46F)));
		nameField = null;
		if (!readonly()) {
			int fy = y + h - BUTTON_H * 2 - 6;
			nameField = add(new InkField(x + 2, fy, leftW - 6, BUTTON_H - 2, 24, text -> HallClient.send(Halls.OP_RENAME, text)));
			TalismanButton rename = new TalismanButton(x + 2, fy + BUTTON_H + 2, leftW - 6, BUTTON_H,
					() -> Component.translatable("kultivator.hall.ui.rename"));
			rename.onPress(() -> {
				if (nameField.focused() && !nameField.text().isBlank()) {
					HallClient.send(Halls.OP_RENAME, nameField.text().trim());
					nameField.setFocused(false);
				} else {
					nameField.setText(info().getStringOr("name", ""));
					nameField.setFocused(true);
				}
			});
			rename.tooltip(() -> List.of(Component.translatable("kultivator.hall.ui.rename"), Component.translatable("kultivator.hall.ui.rename.tip")));
			add(rename);
		}
	}

	@Override
	public boolean capturesKeys() {
		return nameField != null && nameField.focused();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return nameField != null && nameField.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return nameField != null && nameField.charTyped(event);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (nameField != null && nameField.focused() && !nameField.contains(mouseX, mouseY)) {
			nameField.setFocused(false);
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean needsRebuild() {
		return readonly() == (nameField != null);
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag info = info();
		hoverBoxes.clear();
		hoverTips.clear();
		drawLeft(g, info, mouseX, mouseY);
		drawRight(g, info, seconds);
		for (int i = 0; i < hoverBoxes.size() && inside(mouseX, mouseY); i++) {
			float[] box = hoverBoxes.get(i);
			if (mouseX >= box[0] && mouseX < box[2] && mouseY >= box[1] && mouseY < box[3]) {
				screen.showTooltip(hoverTips.get(i));
				break;
			}
		}
	}

	private void tip(float x0, float y0, float x1, float y1, List<Component> lines) {
		hoverBoxes.add(new float[]{x0, y0, x1, y1});
		hoverTips.add(lines);
	}

	/** Name, level seal, renown bar and the level's numbers. */
	private void drawLeft(GuiGraphicsExtractor g, CompoundTag info, int mouseX, int mouseY) {
		float x0 = x + 2.0F;
		float width = leftW - 6.0F;
		heading(g, screen.hallName(), x0, y, width, 0.8F);
		int level = info.getIntOr("level", 1);
		float sealR = 13.0F;
		float cx = x0 + sealR + 1.0F;
		float cy = y + 14.0F + sealR;
		Brush.disc(g, cx + 0.5F, cy + 1.0F, sealR, 0x40000000);
		Brush.disc(g, cx, cy, sealR, Palette.JADE_DARK);
		Brush.disc(g, cx, cy, sealR - 1.5F, Palette.JADE);
		Brush.ring(g, cx, cy, sealR - 3.5F, sealR - 2.8F, Palette.argb(0xC0, Palette.GOLD_LIGHT));
		Brush.centered(g, font, Brush.bold(Component.literal(String.valueOf(level))), cx, cy - 6.0F, Palette.GOLD_LIGHT, 1.4F, true);
		tip(cx - sealR, cy - sealR, cx + sealR, cy + sealR, List.of(Component.translatable("kultivator.hall.ui.level", level),
				Component.translatable("kultivator.hall.ui.level.tip", info.getIntOr("renown_level", 1), info.getIntOr("bcap", 2))));
		float tx = cx + sealR + 4.0F;
		float tw = x0 + width - tx;
		float ty = y + 14.0F;
		ty += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.level", level), tx, ty, tw, Palette.SEAL_DARK, 0.75F);
		String sect = info.getStringOr("sect", "");
		if (!sect.isEmpty()) {
			ty += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.sect", sect), tx, ty, tw, Palette.INK_SOFT, 0.6F);
		}
		ty += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.master", info.getStringOr("owner_name", "")), tx, ty, tw,
				Palette.INK_SOFT, 0.6F);
		if (info.getBooleanOr("dormant", false)) {
			HallUi.line(g, font, Component.translatable("kultivator.hall.ui.dormant").withStyle(ChatFormatting.BOLD), tx, ty, tw, Palette.DANGER, 0.6F);
		}
		float by = y + 16.0F + sealR * 2.0F;
		long renown = info.getLongOr("renown", 0L);
		long next = info.getLongOr("renown_next", -1L);
		long base = info.getLongOr("renown_base", 0L);
		float fraction = next < 0L ? 1.0F : (float) (renown - base) / Math.max(1L, next - base);
		Component value = Component.literal(next < 0L ? String.valueOf(renown) : renown + " / " + next);
		float barH = HallUi.bar(g, font, Component.translatable("kultivator.hall.ui.renown"), value, fraction, Palette.GOLD, Palette.GOLD_LIGHT,
				x0, by, x0 + width, 0.6F);
		tip(x0, by, x0 + width, by + barH, List.of(Component.translatable("kultivator.hall.ui.renown"),
				Component.translatable("kultivator.hall.ui.renown.tip"), Component.translatable("kultivator.hall.ui.renown.source")
						.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		by += barH + 3.0F;
		int alive = info.getIntOr("alive", -1);
		Component[] lines = {
				alive >= 0 ? Component.translatable("kultivator.hall.ui.cap", alive, info.getIntOr("cap", 5))
						: Component.translatable("kultivator.hall.ui.cap_only", info.getIntOr("cap", 5)),
				Component.translatable("kultivator.hall.ui.radius", info.getIntOr("radius", 24)),
				Component.translatable("kultivator.hall.ui.price", String.format(java.util.Locale.ROOT, "%.2f", info.getDoubleOr("price", 1.0D))),
				Component.translatable("kultivator.hall.ui.per_arrival", info.getIntOr("per_arrival", 2)),
				Component.translatable("kultivator.hall.ui.posts", info.getIntOr("posts", 1), info.getIntOr("missions", 3)),
				Component.translatable("kultivator.hall.ui.pool", info.getIntOr("pool", 0))};
		float limit = readonly() ? y + h : y + h - BUTTON_H * 2 - 8;
		for (Component line : lines) {
			if (by + 6.0F > limit) {
				break;
			}
			Brush.rect(g, x0 + 1.0F, by + 2.0F, x0 + 3.0F, by + 4.0F, Palette.JADE);
			by += HallUi.line(g, font, line, x0 + 6.0F, by, width - 6.0F, Palette.INK, 0.62F) + 0.5F;
		}
	}

	/** The building checklist, the formation, the schedule and the teams away (scrolls). */
	private void drawRight(GuiGraphicsExtractor g, CompoundTag info, float seconds) {
		int x0 = x + leftW;
		int x1 = x + w;
		panel(g, x0, y, x1, y + h);
		float maxScroll = Math.max(0.0F, contentH - h);
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		float offset = scrollShown.to(scroll, seconds);
		g.enableScissor(x0 + 1, y + 1, x1 - 1, y + h - 1);
		float tx = x0 + 5.0F;
		float width = x1 - x0 - 10.0F;
		float cursor = y + 4.0F - offset;
		heading(g, Component.translatable("kultivator.hall.ui.buildings"), tx, cursor, width, 0.75F);
		cursor += 12.0F;
		int flags = info.getIntOr("bflags", 0);
		CompoundTag counts = info.getCompoundOrEmpty("bcount");
		boolean pavilion = (flags & 1) != 0;
		boolean formation = (flags & 2) != 0;
		boolean drill = (flags & 4) != 0;
		boolean tower = (flags & 8) != 0;
		cursor = building(g, tx, cursor, width, true, "stele", 2, Component.translatable("kultivator.hall.ui.b.stele"));
		cursor = building(g, tx, cursor, width, pavilion, "pavilion", 4, Component.translatable("kultivator.hall.ui.b.pavilion",
				counts.getIntOr("books", 0), 10, counts.getIntOr("lecterns", 0)));
		cursor = building(g, tx, cursor, width, formation, "formation", 6, Component.translatable("kultivator.hall.ui.b.formation"));
		cursor = building(g, tx, cursor, width, drill, "drill", 8, Component.translatable("kultivator.hall.ui.b.drill", counts.getIntOr("targets", 0), 4));
		cursor = building(g, tx, cursor, width, tower, "tower", 9, Component.translatable("kultivator.hall.ui.b.tower", counts.getIntOr("rooms", 0), 3));
		cursor += 3.0F;
		CompoundTag grand = info.getCompoundOrEmpty("formation");
		if (!grand.isEmpty()) {
			Component line = grand.getBooleanOr("grand", false)
					? Component.translatable("kultivator.hall.ui.formation_grand", grand.getIntOr("layers", 1), grand.getIntOr("radius", 16))
					: Component.translatable("kultivator.hall.ui.formation_plain");
			float lineH = HallUi.line(g, font, line, tx, cursor, width, Palette.JADE_DARK, 0.6F);
			if (cursor + lineH > y && cursor < y + h) {
				tip(tx, Math.max(y, cursor), tx + width, Math.min(y + h, cursor + lineH), List.of(line,
						Component.translatable("kultivator.hall.ui.formation_grand.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
			}
			cursor += lineH;
			String guard = grand.getStringOr("guard", "");
			cursor += HallUi.line(g, font, guard.isEmpty() ? Component.translatable("kultivator.hall.ui.formation_unguarded")
					: Component.translatable("kultivator.hall.ui.formation_guard", guard), tx, cursor, width, Palette.INK_SOFT, 0.55F);
			cursor += 3.0F;
		}
		heading(g, Component.translatable("kultivator.hall.ui.schedule"), tx, cursor, width, 0.75F);
		cursor += 12.0F;
		long day = info.getLongOr("day", 0L);
		long nextArrival = info.getLongOr("next_arrival", day);
		cursor += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.today", day), tx, cursor, width, Palette.INK, 0.6F);
		Component arrival = Component.translatable(info.getBooleanOr("war_next", false) ? "kultivator.hall.ui.next_war" : "kultivator.hall.ui.next_arrival",
				nextArrival);
		cursor += HallUi.line(g, font, arrival, tx, cursor, width, info.getBooleanOr("war_next", false) ? Palette.SEAL_DARK : Palette.INK, 0.6F);
		cursor += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.interval", info.getIntOr("interval", 3)), tx, cursor, width,
				Palette.INK_SOFT, 0.55F);
		// Module B's events: the next Kompetisi, the forbidden ground and an open Ark (DESIGN 20.3.1 schedule).
		CompoundTag se = info.getCompoundOrEmpty("sectevent");
		if (se.getBooleanOr("hall", false)) {
			long time = se.getLongOr("time", 0L);
			long tourney = se.getCompoundOrEmpty("tourney").getLongOr("next", 0L);
			if (tourney > 0L) {
				cursor += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.next_tourney", tourney), tx, cursor, width, Palette.INK, 0.6F);
			}
			CompoundTag forbidden = se.getCompoundOrEmpty("forbidden");
			if (forbidden.getBooleanOr("open", false)) {
				cursor += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.forbidden_open", hours(forbidden.getLongOr("until", 0L) - time)),
						tx, cursor, width, Palette.SEAL_DARK, 0.6F);
			} else if (forbidden.getLongOr("next", 0L) > 0L) {
				cursor += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.forbidden_next", forbidden.getLongOr("next", 0L)), tx, cursor,
						width, Palette.INK, 0.6F);
			}
			CompoundTag ark = se.getCompoundOrEmpty("ark");
			if (ark.getBooleanOr("open", false)) {
				cursor += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.ark_open", hours(ark.getLongOr("until", 0L) - time)), tx,
						cursor, width, Palette.JADE_DARK, 0.6F);
			}
		}
		cursor += 3.0F;
		heading(g, Component.translatable("kultivator.hall.ui.away"), tx, cursor, width, 0.75F);
		cursor += 12.0F;
		ListTag away = info.getListOrEmpty("away");
		if (away.isEmpty()) {
			cursor += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.away_none"), tx, cursor, width, Palette.INK_FAINT, 0.6F);
		}
		for (Tag tag : away) {
			if (!(tag instanceof CompoundTag entry)) {
				continue;
			}
			long left = HallClient.ticksLeft(entry.getLongOr("left", 0L));
			Component line = Component.translatable("kultivator.hall.ui.away_entry", HallUi.name(entry, "name"),
					Component.translatableWithFallback("kultivator.hall.away." + entry.getStringOr("reason", "other"), entry.getStringOr("reason", "?")), String.format(java.util.Locale.ROOT,
							"%.1f", left / 24000.0D));
			cursor += HallUi.line(g, font, line, tx, cursor, width, Palette.INK, 0.58F);
		}
		cursor += 4.0F;
		contentH = cursor + offset - y;
		g.disableScissor();
		if (maxScroll > 0.0F) {
			float bead = y + 3.0F + (h - 6.0F) * (offset / maxScroll);
			g.fill(x1 - 2, y, x1 - 1, y + h, Palette.CORD);
			Brush.disc(g, x1 - 1.5F, bead, 2.2F, Palette.JADE);
		}
	}

	/** "1:05" (hours:minutes of real time) for a number of ticks left, counted from when the details arrived. */
	private static String hours(long ticks) {
		long minutes = HallClient.ticksLeft(Math.max(0L, ticks)) / 1200L;
		return String.format(java.util.Locale.ROOT, "%d:%02d", minutes / 60L, minutes % 60L);
	}

	/** One checklist line: a tick or cross, the building, and the level it allows; the hint is its tooltip. */
	private float building(GuiGraphicsExtractor g, float tx, float cursor, float width, boolean ok, String id, int cap, Component text) {
		HallUi.mark(g, tx, cursor + 1.0F, ok);
		Component cap1 = Component.translatable("kultivator.hall.ui.b.allows", cap);
		float capW = Math.min(width * 0.35F, font.width(cap1) * 0.55F);
		Brush.fit(g, font, text, tx + 7.0F, cursor, width - 9.0F - capW, ok ? Palette.INK : Palette.INK_SOFT, 0.6F, 0.4F, false);
		Brush.fit(g, font, cap1, tx + width - capW, cursor + 0.5F, capW, ok ? Palette.JADE_DARK : Palette.INK_FAINT, 0.55F, 0.4F, false);
		tip(tx, cursor, tx + width, cursor + 7.0F, List.of(text, Component.translatable("kultivator.hall.ui.b." + id + ".hint"),
				Component.translatable("kultivator.hall.ui.b." + id + ".source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		return cursor + 8.5F;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (mouseX >= x + leftW && inside(mouseX, mouseY)) {
			scroll -= (float) amount * 14.0F;
			return true;
		}
		return false;
	}
}
