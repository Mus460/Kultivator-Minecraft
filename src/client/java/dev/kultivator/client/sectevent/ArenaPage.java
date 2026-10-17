package dev.kultivator.client.sectevent;

import dev.kultivator.client.hall.HallScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Arena tablet (DESIGN 20.3.12-20.3.15): sub-tabs Turnamen (the schedule, who enters, the bracket while it runs and
 * the all-time Daftar Kuat on bamboo slips), Alam Hampa (the Void God Realm's queue and best-floor board), Menara (the
 * Blazing Sky tower's rooms and trainees) and Perang (the Hundred Dynasties War's youths and bracket).
 */
public class ArenaPage extends SectEventPage {

	private static final String[] TABS = {"kultivator.sectevent.ui.tab.tourney", "kultivator.sectevent.ui.tab.void",
			"kultivator.sectevent.ui.tab.tower", "kultivator.sectevent.ui.tab.war"};
	private static final int BUTTON_H = 13;

	private static int tab;
	private static int floor = 1;
	private static final Set<String> VOID = new HashSet<>();
	private static final Set<String> TOWER = new HashSet<>();
	private static final Set<String> ENTRANTS = new HashSet<>();

	private RosterView roster;
	private int top;
	private int leftW;
	private int midX;
	private int midW;
	private int rightX;
	private int rightW;

	public ArenaPage(HallScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.page.arena");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine6.page.arena.short");
	}

	@Override
	public int icon() {
		return Glyphs.TREASURE;
	}

	private CompoundTag tourney() {
		return data().getCompoundOrEmpty("tourney");
	}

	private CompoundTag war() {
		return data().getCompoundOrEmpty("war");
	}

	private boolean bracketShown() {
		return tab == 0 && tourney().contains("bracket") || tab == 3 && war().contains("bracket");
	}

	private static boolean near(CompoundTag entry) {
		return entry.getBooleanOr("near", false) && entry.getBooleanOr("free", false);
	}

	@Override
	protected void build() {
		roster = null;
		if (!data().getBooleanOr("hall", false)) {
			return;
		}
		top = tabs(TABS, () -> tab, index -> tab = index);
		leftW = Math.round(w * 0.36F);
		midX = x + leftW + 4;
		midW = Math.round(w * 0.31F);
		rightX = midX + midW + 4;
		rightW = x + w - rightX;
		int bottom = y + h;
		if (bracketShown()) {
			return;
		}
		int half = (leftW - 6) / 2;
		switch (tab) {
			case 0 -> {
				roster = new RosterView(font, 99, entry -> entry.getIntOr("rank", 1) >= 1, entry -> true,
						entry -> Component.literal(entry.getBooleanOr("excluded", false) ? "✗" : "✓"), ENTRANTS);
				roster.layout(midX, top + 11, midW, bottom - top - 11);
				TalismanButton start = new TalismanButton(x + 2, bottom - 2 * BUTTON_H - 2, leftW - 4, BUTTON_H,
						() -> Component.translatable("kultivator.sectevent.ui.tourney.start"));
				start.enabledWhen(() -> data().getLongOr("day", 0L) >= tourney().getLongOr("manual_ready", Long.MIN_VALUE));
				start.tooltip(() -> List.of(Component.translatable("kultivator.sectevent.ui.tourney.start.tip", 3)));
				start.onPress(() -> act(0, "tourney_start"));
				add(start);
				TalismanButton skip = new TalismanButton(x + 2, bottom - BUTTON_H, leftW - 4, BUTTON_H,
						() -> Component.translatable("kultivator.sectevent.ui.tourney.skip"));
				skip.enabledWhen(() -> data().getLongOr("day", 0L) >= tourney().getLongOr("next", 0L));
				skip.onPress(() -> act(0, "tourney_skip"));
				add(skip);
			}
			case 1 -> {
				roster = new RosterView(font, 1, entry -> true,
						entry -> near(entry) && entry.getIntOr("rank", 1) >= 2 && !entry.getBooleanOr("void_today", false),
						entry -> Component.translatable("kultivator.sectevent.ui.void.best_short", entry.getIntOr("void_best", 0)), VOID);
				roster.layout(midX, top + 11, midW, bottom - top - 11);
				TalismanButton enter = new TalismanButton(x + 2, bottom - BUTTON_H, leftW - 4, BUTTON_H,
						() -> Component.translatable("kultivator.sectevent.ui.void.enter"));
				enter.enabledWhen(() -> roster.first() != null);
				enter.onPress(() -> {
					CompoundTag chosen = roster.first();
					if (chosen != null) {
						act(chosen.getIntOr("id", 0), "void");
					}
				});
				add(enter);
			}
			case 2 -> {
				roster = new RosterView(font, 1, entry -> true, entry -> entry.getBooleanOr("near", false),
						entry -> entry.getBooleanOr("tower", false) ? Component.translatable("kultivator.sectevent.ui.tower.training")
								: rankShort(entry), TOWER);
				roster.layout(midX, top + 11, midW, bottom - top - 11);
				int rooms = rooms().size();
				int buttons = Math.max(1, Math.min(7, rooms));
				int each = Math.max(12, (leftW - 4 - (buttons - 1) * 2) / buttons);
				for (int i = 1; i <= rooms; i++) {
					int n = i;
					TalismanButton pick = new TalismanButton(x + 2 + (i - 1) * (each + 2), bottom - 2 * BUTTON_H - 4, each, BUTTON_H,
							() -> Component.literal(String.valueOf(n)));
					pick.stampedWhen(() -> floor == n);
					pick.tooltip(() -> List.of(Component.translatable("kultivator.sectevent.ui.tower.floor", n,
							String.format(java.util.Locale.ROOT, "%.1f", 1.2D + 0.2D * n), n)));
					pick.onPress(() -> floor = n);
					add(pick);
				}
				TalismanButton train = new TalismanButton(x + 2, bottom - BUTTON_H, half, BUTTON_H,
						() -> Component.translatable("kultivator.sectevent.ui.tower.train", floor));
				train.enabledWhen(() -> roster.first() != null && roster.first().getBooleanOr("tower_ok", true) && floor >= 1
						&& floor <= rooms().size());
				train.tooltip(() -> {
					CompoundTag chosen = roster.first();
					return chosen != null && !chosen.getBooleanOr("tower_ok", true)
							? List.of(Component.translatable("kultivator.sectevent.tower.rank"))
							: List.of(Component.translatable("kultivator.sectevent.ui.tower.train", floor));
				});
				train.onPress(() -> {
					CompoundTag chosen = roster.first();
					if (chosen != null) {
						act(chosen.getIntOr("id", 0), "tower:" + floor);
					}
				});
				add(train);
				TalismanButton stop = new TalismanButton(x + 4 + half, bottom - BUTTON_H, half, BUTTON_H,
						() -> Component.translatable("kultivator.sectevent.ui.tower.stop"));
				stop.enabledWhen(() -> {
					CompoundTag chosen = roster.first();
					return chosen != null && chosen.getBooleanOr("tower", false);
				});
				stop.onPress(() -> {
					CompoundTag chosen = roster.first();
					if (chosen != null) {
						act(chosen.getIntOr("id", 0), "tower_stop");
					}
				});
				add(stop);
			}
			default -> {
				TalismanButton start = new TalismanButton(x + 2, bottom - BUTTON_H, leftW - 4, BUTTON_H,
						() -> Component.translatable("kultivator.sectevent.ui.war.start"));
				start.enabledWhen(() -> war().getBooleanOr("today", false) && war().getIntOr("state", 0) == 0);
				start.onPress(() -> act(0, "war_start"));
				add(start);
			}
		}
	}

	private List<CompoundTag> rooms() {
		List<CompoundTag> list = new ArrayList<>();
		for (Tag tag : data().getCompoundOrEmpty("tower").getListOrEmpty("rooms")) {
			if (tag instanceof CompoundTag room) {
				list.add(room);
			}
		}
		return list;
	}

	private static Component rankShort(CompoundTag entry) {
		return dev.kultivator.data.Realm.byOrdinal(entry.getIntOr("realm", 0)).displayName();
	}

	@Override
	protected String shape() {
		return (data().getBooleanOr("hall", false) ? "h" : "n") + (bracketShown() ? "b" : "") + ":" + rooms().size();
	}

	// ================================================================== drawing

	@Override
	protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (bracketShown()) {
			CompoundTag bracket = tab == 0 ? tourney().getCompoundOrEmpty("bracket") : war().getCompoundOrEmpty("bracket");
			Component name = tab == 3 ? Component.translatable("kultivator.sectevent.war.title")
					: Component.translatable(tourney().getBooleanOr("elite", false) ? "kultivator.sectevent.tourney.elite" : "kultivator.sectevent.tourney.name");
			heading(g, name, x + 2.0F, top, w - 4.0F, 0.8F);
			BracketView.draw(g, font, bracket, x + 2, top + 13, w - 4, y + h - top - 13);
			return;
		}
		switch (tab) {
			case 0 -> drawTourney(g, mouseX, mouseY);
			case 1 -> drawVoid(g);
			case 2 -> drawTower(g);
			default -> drawWar(g);
		}
		if (roster != null) {
			roster.draw(g, mouseX, mouseY, screen);
		}
	}

	private void drawTourney(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		CompoundTag tourney = tourney();
		long day = data().getLongOr("day", 0L);
		heading(g, Component.translatable("kultivator.sectevent.tourney.name"), x + 2.0F, top, leftW - 4.0F, 0.8F);
		heading(g, Component.translatable("kultivator.sectevent.ui.tourney.entrants"), midX + 2.0F, top, midW - 4.0F, 0.8F);
		heading(g, Component.translatable("kultivator.sectevent.ui.ranking"), rightX + 2.0F, top, rightW - 4.0F, 0.8F);
		Set<String> included = new HashSet<>();
		for (CompoundTag entry : SectEventClient.roster()) {
			if (!entry.getBooleanOr("excluded", false)) {
				included.add(entry.getStringOr("soul", ""));
			}
		}
		if (roster != null) {
			roster.select(included);
		}
		List<Component> list = new ArrayList<>();
		long next = tourney.getLongOr("next", 0L);
		list.add(next <= day ? Component.translatable("kultivator.sectevent.ui.tourney.due").withColor(Palette.SEAL_DARK)
				: Component.translatable("kultivator.sectevent.ui.tourney.next", next, next - day));
		if (tourney.getBooleanOr("elite_next", false)) {
			list.add(Component.translatable("kultivator.sectevent.ui.tourney.elite_next").withColor(Palette.onPaper(0x9F6BFF)));
		}
		list.add(Component.translatable("kultivator.sectevent.ui.tourney.held", tourney.getIntOr("count", 0)));
		list.add(Component.translatable("kultivator.sectevent.ui.tourney.rules"));
		list.add(Component.translatable("kultivator.sectevent.ui.tourney.source").withColor(Palette.INK_FAINT));
		lines(g, list, x + 4.0F, top + 13.0F, leftW - 8.0F, y + h - 2 * BUTTON_H - 4.0F, Palette.INK_SOFT, 0.58F);
		slips(g, tourney.getListOrEmpty("ranking"), "points");
	}

	/** The top ten on bamboo slips in the right column ({@code key}: points or best floor). */
	private void slips(GuiGraphicsExtractor g, ListTag list, String key) {
		int sy = top + 12;
		int index = 0;
		for (Tag tag : list) {
			if (!(tag instanceof CompoundTag entry) || sy + 11 > y + h) {
				break;
			}
			index++;
			Component text = Component.translatable(key.equals("points") ? "kultivator.sectevent.ui.ranking.line" : "kultivator.sectevent.ui.void.line",
					index, SectEventClient.name(entry, "name"), entry.getIntOr(key, 0), entry.getIntOr("firsts", 0));
			slip(g, rightX, sy, rightX + rightW, sy + 10, text, index == 1 ? Palette.SEAL_DARK : Palette.INK, index <= 3);
			sy += 12;
		}
		if (index == 0) {
			Brush.wrap(g, font, Component.translatable("kultivator.sectevent.ui.ranking.empty"), rightX + 2.0F, top + 14.0F, rightW - 4.0F,
					Palette.INK_FAINT, 0.55F, false);
		}
	}

	private void drawVoid(GuiGraphicsExtractor g) {
		CompoundTag info = data().getCompoundOrEmpty("void");
		heading(g, Component.translatable("kultivator.sectevent.ui.void"), x + 2.0F, top, leftW - 4.0F, 0.8F);
		heading(g, Component.translatable("kultivator.sectevent.ui.void.pick"), midX + 2.0F, top, midW - 4.0F, 0.8F);
		heading(g, Component.translatable("kultivator.sectevent.ui.void.board"), rightX + 2.0F, top, rightW - 4.0F, 0.8F);
		List<Component> list = new ArrayList<>();
		if (info.contains("climber")) {
			list.add(Component.translatable("kultivator.sectevent.ui.void.climbing", SectEventClient.name(info, "climber"), info.getIntOr("floor", 1),
					info.getIntOr("cleared", 0)).withColor(Palette.onPaper(0x9F6BFF)));
		}
		ListTag queue = info.getListOrEmpty("queue");
		if (!queue.isEmpty()) {
			list.add(Component.translatable("kultivator.sectevent.ui.void.queue", queue.size()));
		}
		CompoundTag chosen = roster == null ? null : roster.first();
		if (chosen != null) {
			int next = chosen.getIntOr("void_best", 0) + 1;
			list.add(Component.translatable("kultivator.sectevent.ui.void.next", next, floorName(next)).withColor(Palette.JADE_DARK));
		}
		list.add(Component.translatable("kultivator.sectevent.ui.void.desc"));
		list.add(Component.translatable("kultivator.sectevent.ui.void.source").withColor(Palette.INK_FAINT));
		lines(g, list, x + 4.0F, top + 13.0F, leftW - 8.0F, y + h - BUTTON_H - 3.0F, Palette.INK_SOFT, 0.58F);
		slips(g, info.getListOrEmpty("board"), "best");
	}

	/** The client copy of the floor names (DESIGN 20.3.14). */
	private static Component floorName(int floor) {
		String key;
		if (floor <= 9) {
			key = "start";
		} else if (floor <= 19) {
			key = "stairs";
		} else if (floor <= 29) {
			key = "clouds";
		} else if (floor <= 39) {
			key = "palace";
		} else if (floor <= 49) {
			key = "peak";
		} else if (floor == 50) {
			key = "border";
		} else if (floor <= 59) {
			key = "beyond";
		} else if (floor == 60) {
			key = "devil";
		} else {
			return Component.translatable("kultivator.sectevent.void.floor.bottomless", floor - 60);
		}
		return Component.translatable("kultivator.sectevent.void.floor." + key);
	}

	private void drawTower(GuiGraphicsExtractor g) {
		CompoundTag info = data().getCompoundOrEmpty("tower");
		heading(g, Component.translatable("kultivator.sectevent.ui.tower"), x + 2.0F, top, leftW - 4.0F, 0.8F);
		heading(g, Component.translatable("kultivator.sectevent.ui.tower.pick"), midX + 2.0F, top, midW - 4.0F, 0.8F);
		heading(g, Component.translatable("kultivator.sectevent.ui.tower.trainees"), rightX + 2.0F, top, rightW - 4.0F, 0.8F);
		List<Component> list = new ArrayList<>();
		int rooms = rooms().size();
		list.add(rooms == 0 ? Component.translatable("kultivator.sectevent.ui.tower.no_rooms").withColor(Palette.DANGER)
				: Component.translatable("kultivator.sectevent.ui.tower.rooms", rooms));
		if (info.getBooleanOr("free", false)) {
			list.add(Component.translatable("kultivator.sectevent.ui.tower.free_day").withColor(Palette.JADE_DARK));
		}
		list.add(Component.translatable("kultivator.sectevent.ui.tower.desc"));
		list.add(Component.translatable("kultivator.sectevent.ui.tower.source").withColor(Palette.INK_FAINT));
		lines(g, list, x + 4.0F, top + 13.0F, leftW - 8.0F, y + h - 2 * BUTTON_H - 6.0F, Palette.INK_SOFT, 0.58F);
		int sy = top + 12;
		for (Tag tag : info.getListOrEmpty("trainees")) {
			if (!(tag instanceof CompoundTag entry) || sy + 11 > y + h) {
				break;
			}
			Component text = Component.translatable(entry.getBooleanOr("out", false) ? "kultivator.sectevent.ui.tower.out" : "kultivator.sectevent.ui.tower.line",
					SectEventClient.name(entry, "name"), entry.getIntOr("floor", 1));
			slip(g, rightX, sy, rightX + rightW, sy + 10, text, entry.getBooleanOr("out", false) ? Palette.SEAL_DARK : Palette.INK, true);
			sy += 12;
		}
	}

	private void drawWar(GuiGraphicsExtractor g) {
		CompoundTag war = war();
		heading(g, Component.translatable("kultivator.sectevent.war.title"), x + 2.0F, top, leftW - 4.0F, 0.8F);
		heading(g, Component.translatable("kultivator.sectevent.ui.war.youths"), midX + 2.0F, top, x + w - midX - 4.0F, 0.8F);
		List<Component> list = new ArrayList<>();
		boolean today = war.getBooleanOr("today", false);
		int state = war.getIntOr("state", 0);
		if (today && state == 0) {
			list.add(Component.translatable("kultivator.sectevent.ui.war.waiting", war.getListOrEmpty("youths").size()).withColor(Palette.SEAL_DARK));
		} else if (state == 2) {
			list.add(Component.translatable("kultivator.sectevent.ui.war.done").withColor(Palette.JADE_DARK));
		} else if (!today) {
			list.add(Component.translatable("kultivator.sectevent.ui.war.not_today"));
		}
		if (war.contains("champion")) {
			list.add(Component.translatable("kultivator.sectevent.ui.war.champion", SectEventClient.name(war, "champion")).withColor(Palette.GOLD_DARK));
		}
		list.add(Component.translatable("kultivator.sectevent.ui.war.desc"));
		list.add(Component.translatable("kultivator.sectevent.ui.war.source").withColor(Palette.INK_FAINT));
		lines(g, list, x + 4.0F, top + 13.0F, leftW - 8.0F, y + h - BUTTON_H - 3.0F, Palette.INK_SOFT, 0.58F);
		int sy = top + 12;
		if (!today) {
			return;
		}
		for (Tag tag : war.getListOrEmpty("youths")) {
			if (!(tag instanceof CompoundTag youth) || sy + 11 > y + h) {
				break;
			}
			Component text = Component.translatable("kultivator.sectevent.ui.war.youth", SectEventClient.name(youth, "name"),
					youth.getIntOr("tekad", 0));
			slip(g, midX, sy, x + w, sy + 10, text, Palette.INK, true);
			sy += 12;
		}
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button != 0 || roster == null || !data().getBooleanOr("hall", false)) {
			return false;
		}
		if (tab == 0) {
			CompoundTag row = roster.rowAt(mouseX, mouseY);
			if (row != null) {
				UiSound.toggle(row.getBooleanOr("excluded", false));
				act(row.getIntOr("id", 0), "exclude");
				return true;
			}
			return false;
		}
		return roster.click(mouseX, mouseY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		return roster != null && roster.scroll(mouseX, mouseY, amount);
	}
}
