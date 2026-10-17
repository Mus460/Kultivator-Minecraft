package dev.kultivator.client.sectevent;

import dev.kultivator.client.hall.HallScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Misi tablet (DESIGN 20.3.10, 20.3.11): sub-tabs Papan (the day's mission board, a team of 1-3 and its success
 * chance), Tim (teams away with their time left and "Panggil Pulang"), Utusan (the envoy), Terlarang (the Blood-Colored
 * Forbidden Ground and its last report) and Bahtera (the Primordial Ark and the six Kunpeng bone pieces).
 */
public class MissionPage extends SectEventPage {

	private static final String[] TABS = {"kultivator.sectevent.ui.tab.board", "kultivator.sectevent.ui.tab.teams",
			"kultivator.sectevent.ui.tab.envoy", "kultivator.sectevent.ui.tab.forbidden", "kultivator.sectevent.ui.tab.ark"};
	private static final String[] TYPES = {"patrol", "herbs", "caravan", "hunt", "secret_realm"};
	private static final long[] DURATION = {12000L, 12000L, 24000L, 24000L, 48000L};
	private static final int BUTTON_H = 13;
	private static final int CARD_H = 22;

	private static int tab;
	private static int offer = -1;
	private static final Set<String> PARTY = new HashSet<>();
	private static final Set<String> ENVOY = new HashSet<>();
	private static final Set<String> FORBIDDEN = new HashSet<>();
	private static final Set<String> ARK = new HashSet<>();

	private RosterView roster;
	private int top;
	private int colW;

	public MissionPage(HallScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.page.mission");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine6.page.mission.short");
	}

	@Override
	public int icon() {
		return Glyphs.PATH;
	}

	private static boolean busy(CompoundTag entry) {
		int act = entry.getIntOr("act", 0);
		return act == DiscipleEntity.ACTIVITY_FORMATION_GUARD || act >= DiscipleEntity.ACTIVITY_VOID_BODY;
	}

	private static boolean ready(CompoundTag entry) {
		return entry.getBooleanOr("near", false) && entry.getBooleanOr("free", false) && !busy(entry);
	}

	@Override
	protected void build() {
		roster = null;
		if (!data().getBooleanOr("hall", false)) {
			return;
		}
		top = tabs(TABS, () -> tab, index -> tab = index);
		colW = Math.max(120, Math.round(w * 0.54F));
		int rightX = x + colW + 4;
		int rightW = x + w - rightX;
		int bottom = y + h;
		switch (tab) {
			case 0 -> {
				roster = new RosterView(font, 3, entry -> true, entry -> ready(entry) && entry.getIntOr("rank", 1) >= 1,
						entry -> sectRank(entry.getIntOr("rank", 1)), PARTY);
				roster.layout(rightX, top + 11, rightW, bottom - top - 11);
				TalismanButton send = new TalismanButton(x + 2, bottom - BUTTON_H, colW - 4, BUTTON_H,
						() -> Component.translatable("kultivator.sectevent.ui.send"));
				send.enabledWhen(() -> currentOffer() != null && !roster.chosen().isEmpty());
				send.onPress(() -> act(0, "mission:" + offer + ":" + roster.ids()));
				add(send);
			}
			case 1 -> {
				int ty = top + 12;
				for (CompoundTag team : teams()) {
					if (ty + 24 > bottom) {
						break;
					}
					int id = team.getIntOr("id", 0);
					TalismanButton recall = new TalismanButton(x + w - 74, ty + 5, 72, BUTTON_H,
							() -> Component.translatable("kultivator.sectevent.ui.recall"));
					recall.enabledWhen(() -> !team.getBooleanOr("resolved", false));
					recall.tooltip(() -> List.of(Component.translatable("kultivator.sectevent.ui.recall.tip")));
					recall.onPress(() -> act(0, "recall:" + id));
					add(recall);
					ty += 26;
				}
			}
			case 2 -> {
				roster = new RosterView(font, 1, entry -> true, entry -> ready(entry) && entry.getBooleanOr("envoy_ok", false),
						entry -> sectRank(entry.getIntOr("rank", 1)), ENVOY);
				roster.layout(rightX, top + 11, rightW, bottom - top - 11);
				int half = (colW - 6) / 2;
				TalismanButton send = new TalismanButton(x + 2, bottom - BUTTON_H, half, BUTTON_H,
						() -> Component.translatable("kultivator.sectevent.ui.envoy.send"));
				send.enabledWhen(() -> envoyReady() && roster.first() != null);
				send.onPress(() -> {
					CompoundTag chosen = roster.first();
					if (chosen != null) {
						act(chosen.getIntOr("id", 0), "envoy");
					}
				});
				add(send);
				TalismanButton reset = new TalismanButton(x + 4 + half, bottom - BUTTON_H, half, BUTTON_H,
						() -> Component.translatable("kultivator.sectevent.ui.envoy.reset", 300));
				reset.enabledWhen(() -> !envoyReady());
				reset.tooltip(() -> List.of(Component.translatable("kultivator.sectevent.ui.envoy.reset.tip")));
				reset.onPress(() -> act(0, "envoy_reset"));
				add(reset);
			}
			case 3 -> {
				roster = new RosterView(font, Math.max(1, forbidden().getIntOr("slots", 3)), entry -> true,
						entry -> ready(entry) && entry.getBooleanOr("qr", false),
						entry -> entry.getBooleanOr("qr", false) ? Component.literal(percent(entry.getDoubleOr("fg", 0.0D)))
								: Component.translatable("kultivator.sectevent.ui.forbidden.sealed"), FORBIDDEN);
				roster.layout(rightX, top + 11, rightW, bottom - top - 11);
				TalismanButton enter = new TalismanButton(x + 2, bottom - BUTTON_H, colW - 4, BUTTON_H,
						() -> Component.translatable("kultivator.sectevent.ui.forbidden.enter"));
				enter.enabledWhen(() -> forbidden().getBooleanOr("open", false) && !roster.chosen().isEmpty());
				enter.onPress(() -> act(0, "forbidden:" + roster.ids()));
				add(enter);
			}
			default -> {
				roster = new RosterView(font, 1, entry -> true,
						entry -> ready(entry) && (entry.getBooleanOr("ark_ok", false) || entry.getIntOr("rank", 1) >= 3),
						entry -> entry.getBooleanOr("ark_ok", false) ? Component.translatable("kultivator.sectevent.ui.ark.may")
								: sectRank(entry.getIntOr("rank", 1)), ARK);
				roster.layout(rightX, top + 11, rightW, bottom - top - 11);
				int half = (colW - 6) / 2;
				TalismanButton board = new TalismanButton(x + 2, bottom - BUTTON_H, half, BUTTON_H,
						() -> Component.translatable("kultivator.sectevent.ui.ark.board"));
				board.enabledWhen(() -> {
					CompoundTag chosen = roster.first();
					return chosen != null && chosen.getBooleanOr("ark_ok", false);
				});
				board.onPress(() -> {
					CompoundTag chosen = roster.first();
					if (chosen != null) {
						act(chosen.getIntOr("id", 0), "ark");
					}
				});
				add(board);
				TalismanButton unite = new TalismanButton(x + 4 + half, bottom - BUTTON_H, half, BUTTON_H,
						() -> Component.translatable("kultivator.sectevent.ui.kunpeng.unite"));
				unite.enabledWhen(() -> {
					CompoundTag chosen = roster.first();
					return chosen != null && chosen.getIntOr("rank", 1) >= 3 && data().getIntOr("kunpeng", 0) == 0x3F;
				});
				unite.tooltip(() -> List.of(Component.translatable("kultivator.sectevent.ui.kunpeng.tip"),
						Component.translatable("kultivator.sectevent.item.kunpeng_bone_fragment.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
				unite.onPress(() -> {
					CompoundTag chosen = roster.first();
					if (chosen != null) {
						act(chosen.getIntOr("id", 0), "kunpeng");
					}
				});
				add(unite);
			}
		}
	}

	private List<CompoundTag> teams() {
		List<CompoundTag> list = new ArrayList<>();
		for (Tag tag : data().getListOrEmpty("teams")) {
			if (tag instanceof CompoundTag team) {
				list.add(team);
			}
		}
		return list;
	}

	private CompoundTag forbidden() {
		return data().getCompoundOrEmpty("forbidden");
	}

	private boolean envoyReady() {
		return data().getLongOr("day", 0L) >= data().getLongOr("envoy_ready", Long.MIN_VALUE);
	}

	private CompoundTag currentOffer() {
		ListTag board = data().getListOrEmpty("board");
		if (offer < 0 || offer >= board.size() || !(board.get(offer) instanceof CompoundTag tag) || tag.getBooleanOr("taken", false)) {
			return null;
		}
		return tag;
	}

	@Override
	protected String shape() {
		StringBuilder builder = new StringBuilder(data().getBooleanOr("hall", false) ? "h" : "n");
		if (tab == 1) {
			for (CompoundTag team : teams()) {
				builder.append(',').append(team.getIntOr("id", 0)).append(team.getBooleanOr("resolved", false) ? 'r' : 'a');
			}
		}
		if (tab == 3) {
			builder.append(':').append(forbidden().getIntOr("slots", 3));
		}
		return builder.toString();
	}

	// ================================================================== drawing

	@Override
	protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		int rightX = x + colW + 4;
		int rightW = x + w - rightX;
		switch (tab) {
			case 0 -> drawBoard(g, mouseX, mouseY, rightX, rightW);
			case 1 -> drawTeams(g);
			case 2 -> drawEnvoy(g, rightX, rightW);
			case 3 -> drawForbidden(g, rightX, rightW);
			default -> drawArk(g, rightX, rightW);
		}
		if (roster != null) {
			roster.draw(g, mouseX, mouseY, screen);
		}
	}

	private void drawBoard(GuiGraphicsExtractor g, int mouseX, int mouseY, int rightX, int rightW) {
		CompoundTag data = data();
		heading(g, Component.translatable("kultivator.sectevent.ui.board", data.getIntOr("strongest", 1)), x + 2.0F, top, colW - 4.0F, 0.8F);
		heading(g, Component.translatable("kultivator.sectevent.ui.party"), rightX + 2.0F, top, rightW - 4.0F, 0.8F);
		ListTag board = data.getListOrEmpty("board");
		int cy = top + 12;
		int bottom = y + h - BUTTON_H - 14;
		for (int i = 0; i < board.size(); i++) {
			if (!(board.get(i) instanceof CompoundTag tag) || cy + CARD_H > bottom) {
				break;
			}
			int type = Math.max(0, Math.min(TYPES.length - 1, tag.getIntOr("type", 0)));
			int tier = tag.getIntOr("tier", 1);
			boolean taken = tag.getBooleanOr("taken", false);
			boolean chosen = i == offer && !taken;
			boolean over = mouseX >= x + 2 && mouseX < x + colW - 2 && mouseY >= cy && mouseY < cy + CARD_H - 2;
			int x0 = x + 2;
			int x1 = x + colW - 2;
			g.fill(x0, cy, x1, cy + CARD_H - 2, taken ? Palette.argb(0x40, Palette.LOCKED_PAPER) : chosen ? Palette.argb(0xA0, Palette.JADE_PALE)
					: over ? Palette.argb(0x60, Palette.TALISMAN) : Palette.argb(0x48, Palette.PAPER_DEEP));
			Brush.frame(g, x0, cy, x1, cy + CARD_H - 2, chosen ? Palette.GOLD : Palette.argb(0xA0, Palette.GOLD_DARK));
			g.fill(x0 + 2, cy + 2, x0 + 6, cy + 6, taken ? Palette.LOCKED : Palette.SEAL);
			Component name = Brush.bold(Component.translatable("kultivator.sectevent.mission." + TYPES[type]));
			Brush.fit(g, font, name, x0 + 9.0F, cy + 2.0F, x1 - x0 - 12.0F, taken ? Palette.INK_FAINT : Palette.INK, 0.7F, 0.45F, false);
			Component detail = taken ? Component.translatable("kultivator.sectevent.ui.taken")
					: Component.translatable("kultivator.sectevent.ui.offer_line", tier, String.format(Locale.ROOT, "%.1f", DURATION[type] / 24000.0D),
					Component.translatable("kultivator.sectevent.mission." + TYPES[type] + ".need"));
			Brush.fit(g, font, detail, x0 + 9.0F, cy + 11.0F, x1 - x0 - 12.0F, Palette.INK_SOFT, 0.55F, 0.4F, false);
			if (over && !taken) {
				screen.showTooltip(List.of(name, Component.translatable("kultivator.sectevent.mission." + TYPES[type] + ".desc"),
						Component.translatable("kultivator.sectevent.mission.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
			}
			cy += CARD_H;
		}
		if (board.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.sectevent.ui.board_empty"), x + 4.0F, top + 14.0F, colW - 8.0F, Palette.INK_FAINT, 0.6F, false);
		}
		CompoundTag current = currentOffer();
		Component line;
		if (current == null) {
			line = Component.translatable("kultivator.sectevent.ui.pick_offer");
		} else if (roster == null || roster.chosen().isEmpty()) {
			line = Component.translatable("kultivator.sectevent.ui.pick_party");
		} else {
			line = Component.translatable("kultivator.sectevent.ui.chance", percent(chance(roster.chosen(), current.getIntOr("tier", 1))));
		}
		Brush.fit(g, font, line, x + 4.0F, y + h - BUTTON_H - 11.0F, colW - 8.0F, Palette.JADE_DARK, 0.65F, 0.45F, false);
	}

	/** The success chance of DESIGN 20.3.10 from the roster's numbers (the server rolls with the same formula). */
	private static double chance(List<CompoundTag> team, int tier) {
		double best = 0.0D;
		double sum = 0.0D;
		int stars = 0;
		boolean core = false;
		for (CompoundTag entry : team) {
			double m = entry.getIntOr("realm", 0) + entry.getIntOr("stage", 0) / 4.0D + entry.getIntOr("layer", 0) / 9.0D
					+ entry.getIntOr("stars", 0) / 9.0D;
			sum += m;
			if (m > best) {
				best = m;
				stars = entry.getIntOr("stars", 0);
			}
			core |= entry.getIntOr("rank", 1) >= 3;
		}
		double party = best + 0.5D * (sum - best);
		double difficulty = tier + (tier == 9 ? stars / 9.0D : 0.0D);
		return Math.max(0.10D, Math.min(0.95D, 0.45D + 0.12D * (party - (difficulty - 1.0D)) + (core ? 0.05D : 0.0D)));
	}

	private static String percent(double value) {
		return Math.round(value * 100.0D) + "%";
	}

	private void drawTeams(GuiGraphicsExtractor g) {
		heading(g, Component.translatable("kultivator.sectevent.ui.teams"), x + 2.0F, top, w - 4.0F, 0.8F);
		List<CompoundTag> teams = teams();
		if (teams.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.sectevent.ui.teams_none"), x + 4.0F, top + 14.0F, w - 8.0F, Palette.INK_FAINT, 0.65F, false);
			return;
		}
		int ty = top + 12;
		long now = now();
		for (CompoundTag team : teams) {
			if (ty + 24 > y + h) {
				break;
			}
			panel(g, x + 2, ty, x + w - 2, ty + 24);
			String kind = team.getStringOr("kind", "");
			Component what = switch (kind) {
				case "expedition" -> Component.translatable("kultivator.sectevent.ui.team.expedition",
						Component.translatable("kultivator.sectevent.mission." + TYPES[Math.max(0, Math.min(TYPES.length - 1, team.getIntOr("type", 0)))]),
						team.getIntOr("tier", 1));
				case "envoy" -> Component.translatable("kultivator.sectevent.ui.team.envoy");
				case "forbidden" -> Component.translatable("kultivator.sectevent.ui.team.forbidden");
				default -> Component.translatable("kultivator.sectevent.ui.team.ark");
			};
			Brush.fit(g, font, Brush.bold(what), x + 6.0F, ty + 3.0F, w - 86.0F, Palette.INK, 0.65F, 0.45F, false);
			Component names = Component.empty();
			boolean firstName = true;
			for (Tag each : team.getListOrEmpty("names")) {
				if (each instanceof CompoundTag entry) {
					names = Component.empty().append(names).append(firstName ? Component.empty() : Component.literal(", ")).append(SectEventClient.name(entry, "name"));
					firstName = false;
				}
			}
			long left = team.getLongOr("return_at", 0L) - now;
			Component state = team.getBooleanOr("resolved", false) || left <= 0L ? Component.translatable("kultivator.sectevent.ui.team.returning")
					: Component.translatable("kultivator.sectevent.ui.team.left", String.format(Locale.ROOT, "%.1f", left / 24000.0D), clock(left));
			Brush.fit(g, font, names, x + 6.0F, ty + 11.0F, w - 86.0F, Palette.INK_SOFT, 0.55F, 0.4F, false);
			Brush.fit(g, font, state, x + 6.0F, ty + 17.0F, w - 86.0F, Palette.JADE_DARK, 0.5F, 0.4F, false);
			ty += 26;
		}
	}

	private void drawEnvoy(GuiGraphicsExtractor g, int rightX, int rightW) {
		CompoundTag data = data();
		heading(g, Component.translatable("kultivator.sectevent.ui.envoy"), x + 2.0F, top, colW - 4.0F, 0.8F);
		heading(g, Component.translatable("kultivator.sectevent.ui.envoy.pick"), rightX + 2.0F, top, rightW - 4.0F, 0.8F);
		List<Component> list = new ArrayList<>();
		list.add(Component.translatable("kultivator.sectevent.ui.envoy.desc"));
		long ready = data.getLongOr("envoy_ready", Long.MIN_VALUE);
		list.add(envoyReady() ? Component.translatable("kultivator.sectevent.ui.envoy.ready").withColor(Palette.JADE_DARK)
				: Component.translatable("kultivator.sectevent.ui.envoy.wait", ready).withColor(Palette.DANGER));
		list.add(Component.translatable("kultivator.sectevent.ui.envoy.source").withColor(Palette.INK_FAINT));
		lines(g, list, x + 4.0F, top + 13.0F, colW - 8.0F, y + h - BUTTON_H - 3.0F, Palette.INK_SOFT, 0.6F);
	}

	private void drawForbidden(GuiGraphicsExtractor g, int rightX, int rightW) {
		CompoundTag info = forbidden();
		heading(g, Component.translatable("kultivator.sectevent.ui.forbidden"), x + 2.0F, top, colW - 4.0F, 0.8F);
		heading(g, Component.translatable("kultivator.sectevent.ui.forbidden.pick", info.getIntOr("slots", 3)), rightX + 2.0F, top, rightW - 4.0F, 0.8F);
		List<Component> list = new ArrayList<>();
		if (info.getBooleanOr("open", false)) {
			list.add(Component.translatable("kultivator.sectevent.ui.forbidden.open", clock(info.getLongOr("until", 0L) - now())).withColor(Palette.DANGER));
		} else {
			list.add(Component.translatable("kultivator.sectevent.ui.forbidden.closed", info.getLongOr("next", 0L)).withColor(Palette.INK_SOFT));
		}
		list.add(Component.translatable("kultivator.sectevent.ui.forbidden.desc"));
		if (roster != null && !roster.chosen().isEmpty()) {
			boolean company = roster.chosen().size() > 1;
			for (CompoundTag entry : roster.chosen()) {
				double chance = Math.min(0.85D, entry.getDoubleOr("fg", 0.0D) + (company ? 0.10D : 0.0D));
				list.add(Component.translatable("kultivator.sectevent.ui.forbidden.chance", SectEventClient.name(entry, "name"), percent(chance))
						.withColor(Palette.JADE_DARK));
			}
		}
		float ty = lines(g, list, x + 4.0F, top + 13.0F, colW - 8.0F, y + h - BUTTON_H - 3.0F, Palette.INK_SOFT, 0.6F);
		ListTag report = info.getListOrEmpty("report");
		if (!report.isEmpty() && ty + 10.0F < y + h - BUTTON_H - 4) {
			Brush.fit(g, font, Component.translatable("kultivator.sectevent.ui.forbidden.report"), x + 4.0F, ty, colW - 8.0F, Palette.SEAL_DARK, 0.6F, 0.45F, false);
			int sy = Math.round(ty) + 8;
			for (Tag each : report) {
				if (!(each instanceof CompoundTag line) || sy + 9 > y + h - BUTTON_H - 3) {
					break;
				}
				boolean ok = line.getBooleanOr("ok", false);
				Component text = Component.translatable(ok ? "kultivator.sectevent.ui.forbidden.slip_ok" : line.getBooleanOr("tablet", false)
						? "kultivator.sectevent.ui.forbidden.slip_tablet" : "kultivator.sectevent.ui.forbidden.slip_hurt",
						SectEventClient.name(line, "name"), line.getIntOr("herbs", 0));
				slip(g, x + 4, sy, x + colW - 4, sy + 9, text, ok ? Palette.JADE_DARK : Palette.SEAL_DARK, ok);
				sy += 10;
			}
		}
	}

	private void drawArk(GuiGraphicsExtractor g, int rightX, int rightW) {
		CompoundTag data = data();
		CompoundTag ark = data.getCompoundOrEmpty("ark");
		heading(g, Component.translatable("kultivator.sectevent.ui.ark"), x + 2.0F, top, colW - 4.0F, 0.8F);
		heading(g, Component.translatable("kultivator.sectevent.ui.ark.pick"), rightX + 2.0F, top, rightW - 4.0F, 0.8F);
		List<Component> list = new ArrayList<>();
		if (ark.getBooleanOr("open", false)) {
			list.add(Component.translatable("kultivator.sectevent.ui.ark.open", clock(ark.getLongOr("until", 0L) - now())).withColor(Palette.onPaper(0x9F6BFF)));
		} else {
			list.add(Component.translatable("kultivator.sectevent.ui.ark.closed").withColor(Palette.INK_SOFT));
		}
		list.add(Component.translatable("kultivator.sectevent.ui.ark.desc"));
		float ty = lines(g, list, x + 4.0F, top + 13.0F, colW - 8.0F, y + h - BUTTON_H - 40.0F, Palette.INK_SOFT, 0.6F);
		// The six bone pieces.
		int mask = data.getIntOr("kunpeng", 0);
		float py = Math.max(ty + 2.0F, y + h - BUTTON_H - 36.0F);
		Brush.fit(g, font, Component.translatable("kultivator.sectevent.ui.kunpeng", Integer.bitCount(mask & 0x3F)), x + 4.0F, py, colW - 8.0F,
				Palette.SEAL_DARK, 0.6F, 0.45F, false);
		float size = Math.min(14.0F, (colW - 12.0F) / 6.0F - 3.0F);
		for (int i = 0; i < 6; i++) {
			float bx = x + 4.0F + i * (size + 3.0F);
			float by = py + 9.0F;
			boolean have = (mask & 1 << i) != 0;
			Brush.rect(g, bx, by, bx + size, by + size, have ? Palette.argb(0xFF, 0xF2EAD0) : Palette.argb(0x50, Palette.LOCKED));
			Brush.frame(g, Math.round(bx), Math.round(by), Math.round(bx + size), Math.round(by + size), have ? Palette.GOLD : Palette.argb(0x80, Palette.INK_FAINT));
			// A bone: a bar with knobs at both ends.
			int bone = have ? Palette.INK_SOFT : Palette.argb(0x80, Palette.INK_FAINT);
			Brush.line(g, bx + size * 0.25F, by + size * 0.75F, bx + size * 0.75F, by + size * 0.25F, 1.4F, bone);
			Brush.disc(g, bx + size * 0.22F, by + size * 0.78F, size * 0.12F, bone);
			Brush.disc(g, bx + size * 0.78F, by + size * 0.22F, size * 0.12F, bone);
			Brush.centered(g, font, Component.literal(String.valueOf(i + 1)), bx + size / 2.0F, by + size + 1.0F, Palette.INK_FAINT, 0.45F, false);
		}
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button != 0 || !data().getBooleanOr("hall", false)) {
			return false;
		}
		if (roster != null && roster.click(mouseX, mouseY)) {
			return true;
		}
		if (tab == 0 && mouseX >= x + 2 && mouseX < x + colW - 2 && mouseY >= top + 12) {
			int index = (int) ((mouseY - top - 12) / CARD_H);
			ListTag board = data().getListOrEmpty("board");
			if (index >= 0 && index < board.size() && board.get(index) instanceof CompoundTag tag && !tag.getBooleanOr("taken", false)
					&& mouseY < y + h - BUTTON_H - 14) {
				offer = offer == index ? -1 : index;
				UiSound.toggle(offer == index);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		return roster != null && roster.scroll(mouseX, mouseY, amount);
	}
}
