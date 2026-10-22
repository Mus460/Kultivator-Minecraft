package dev.kultivator.client.sectevent;

import dev.kultivator.client.hall.HallScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.sekte.misi.MisiClient;
import dev.kultivator.client.sekte.misi.MisiTab;
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
 * <p>
 * Sekte yang Hidup (DESIGN 36.2.7, module X) adds two sections after them: Misi Cerita (the story board, a stance and a
 * team of 1-4 with the server's stage chances) and Kabar Misi (the running story missions with their stage beads,
 * "Tarik Pulang" with or without the wounded, and the reports to read again). Their data comes from module X's own
 * payloads ({@code client.sekte.misi.MisiClient}); the pictures are drawn by {@code MisiTab}.
 */
public class MissionPage extends SectEventPage {

	private static final String[] TABS = {"kultivator.sectevent.ui.tab.board", "kultivator.sectevent.ui.tab.teams",
			"kultivator.sectevent.ui.tab.envoy", "kultivator.sectevent.ui.tab.forbidden", "kultivator.sectevent.ui.tab.ark",
			"kultivator.sekte.misi.ui.tab.cerita", "kultivator.sekte.misi.ui.tab.kabar"};
	/** This tablet's index in the hall screen's page list ({@code Halls.OP_OPEN} argument, the hub card of module X). */
	public static final int PAGE = 6;
	private static final int TAB_STORY = 5;
	private static final int TAB_NEWS = 6;
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
	private static final Set<String> STORY = new HashSet<>();
	private static int storyOffer = -1;
	private static String stance = MisiTab.STANCES[0];
	private String previewKey = "";
	private long seenMisi = -1L;
	private int storyBottom;

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

	/** Opens the tablet on the Misi Cerita section next time (module X's hub card). */
	public static void selectStory() {
		tab = TAB_STORY;
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
			case TAB_STORY -> buildStory(rightX, rightW, bottom);
			case TAB_NEWS -> buildNews(bottom);
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

	// ================================================================== module X: Misi Cerita and Kabar Misi

	private void buildStory(int rightX, int rightW, int bottom) {
		MisiClient.request(true);
		int teamMax = Math.max(1, Math.min(4, MisiClient.state().getIntOr("tim", 4)));
		roster = new RosterView(font, teamMax, entry -> true, entry -> ready(entry) && entry.getIntOr("rank", 1) >= 1,
				entry -> sectRank(entry.getIntOr("rank", 1)), STORY);
		roster.layout(rightX, top + 11, rightW, bottom - top - 11);
		int stanceY = bottom - BUTTON_H * 2 - 13;
		storyBottom = stanceY - 12;
		int third = (colW - 8) / 3;
		for (int i = 0; i < MisiTab.STANCES.length; i++) {
			String id = MisiTab.STANCES[i];
			TalismanButton pick = new TalismanButton(x + 2 + i * (third + 2), stanceY, third, BUTTON_H,
					() -> Component.translatable("kultivator.sekte.misi.sikap." + id));
			pick.stampedWhen(() -> stance.equals(id));
			pick.tooltip(() -> List.of(Component.translatable("kultivator.sekte.misi.sikap." + id + ".desc")));
			pick.onPress(() -> {
				stance = id;
				UiSound.toggle(true);
			});
			add(pick);
		}
		TalismanButton send = new TalismanButton(x + 2, bottom - BUTTON_H, colW - 4, BUTTON_H, () -> Component.translatable("kultivator.sekte.misi.ui.kirim"));
		send.enabledWhen(() -> !readonly() && storyReady());
		send.tooltip(() -> List.of(Component.translatable("kultivator.sekte.misi.ui.kirim.tip")));
		send.onPress(() -> {
			if (readonly()) {
				UiSound.denied();
				return;
			}
			MisiClient.send("kirim", storyOffer, stance + "|" + storySouls());
			storyOffer = -1;
			STORY.clear();
		});
		add(send);
	}

	private boolean storyReady() {
		CompoundTag offer = MisiTab.offer(storyOffer);
		if (offer == null || offer.getBooleanOr("taken", false) || roster == null || roster.chosen().isEmpty() || MisiTab.full()) {
			return false;
		}
		CompoundTag preview = MisiClient.state().getCompoundOrEmpty("pratinjau");
		return preview.getIntOr("offer", -1) != storyOffer || preview.getStringOr("blocker", "").isEmpty() && preview.getBooleanOr("rank_ok", true);
	}

	private String storySouls() {
		StringBuilder souls = new StringBuilder();
		if (roster != null) {
			for (CompoundTag entry : roster.chosen()) {
				souls.append(souls.isEmpty() ? "" : ",").append(entry.getStringOr("soul", ""));
			}
		}
		return souls.toString();
	}

	private void buildNews(int bottom) {
		MisiClient.request(true);
		int cy = top + 12;
		int buttonW = 74;
		for (CompoundTag mission : MisiTab.missions()) {
			if (cy + MisiTab.MISSION_H > bottom) {
				break;
			}
			int id = mission.getIntOr("id", 0);
			boolean open = !mission.getBooleanOr("finished", false) && !mission.getBooleanOr("ditarik", false);
			TalismanButton recall = new TalismanButton(x + w - buttonW - 2, cy + 3, buttonW, BUTTON_H,
					() -> Component.translatable("kultivator.sekte.misi.ui.tarik"));
			recall.enabledWhen(() -> open && !readonly());
			recall.tooltip(() -> List.of(Component.translatable(MisiTab.hurt(mission) ? "kultivator.sekte.misi.ui.tarik.tip_luka"
					: "kultivator.sekte.misi.ui.tarik.tip")));
			recall.onPress(() -> MisiClient.send("tarik", id, ""));
			add(recall);
			if (open && MisiTab.hurt(mission)) {
				TalismanButton leave = new TalismanButton(x + w - buttonW - 2, cy + 4 + BUTTON_H, buttonW, BUTTON_H,
						() -> Component.translatable("kultivator.sekte.misi.ui.tinggal"));
				leave.urgentWhen(() -> true);
				leave.enabledWhen(() -> !readonly());
				leave.tooltip(() -> List.of(Component.translatable("kultivator.sekte.misi.ui.tinggal.tip").withStyle(ChatFormatting.RED)));
				leave.onPress(() -> MisiClient.send("tinggal", id, ""));
				add(leave);
			}
			cy += MisiTab.MISSION_H;
		}
		if (MisiTab.missions().isEmpty()) {
			cy += 14;
		}
		int rowsTop = cy + 12;
		int rows = Math.max(0, (bottom - rowsTop) / MisiTab.ROW_H);
		List<CompoundTag> history = MisiTab.history();
		for (int i = 0; i < Math.min(rows, history.size()); i++) {
			int index = history.get(i).getIntOr("i", 0);
			TalismanButton read = new TalismanButton(x + w - 40, rowsTop + i * MisiTab.ROW_H, 38, MisiTab.ROW_H - 1,
					() -> Component.translatable("kultivator.sekte.misi.ui.baca"));
			read.onPress(() -> MisiClient.send("baca", index, ""));
			add(read);
		}
		storyBottom = rowsTop;
	}

	private void drawStory(GuiGraphicsExtractor g, int mouseX, int mouseY, int rightX, int rightW) {
		MisiClient.request(false);
		heading(g, Component.translatable("kultivator.sekte.misi.ui.papan", MisiClient.state().getIntOr("selesai", 0)), x + 2.0F, top, colW - 4.0F, 0.8F);
		heading(g, Component.translatable("kultivator.sekte.misi.ui.tim", Math.max(1, MisiClient.state().getIntOr("tim", 4))), rightX + 2.0F, top,
				rightW - 4.0F, 0.8F);
		MisiTab.drawBoard(g, font, x + 2, top + 12, x + colW - 2, storyBottom, mouseX, mouseY, storyOffer, screen::showTooltip);
		String souls = storySouls();
		String key = storyOffer + "|" + stance + "|" + souls;
		if (!key.equals(previewKey)) {
			previewKey = key;
			if (storyOffer >= 0 && !souls.isEmpty()) {
				MisiClient.send("cek", storyOffer, stance + "|" + souls);
			}
		}
		MisiTab.drawPreview(g, font, x + 4.0F, storyBottom + 2.0F, colW - 8.0F, storyOffer, stance, souls, !souls.isEmpty(), screen::showTooltip,
				mouseX, mouseY);
	}

	private void drawNews(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		MisiClient.request(false);
		heading(g, Component.translatable("kultivator.sekte.misi.ui.kabar", MisiTab.missions().size(), Math.max(1, MisiClient.state().getIntOr("maks", 2))),
				x + 2.0F, top, w - 4.0F, 0.8F);
		MisiTab.drawMissions(g, font, x + 2, top + 12, x + w - 2, 78, mouseX, mouseY, screen::showTooltip);
		heading(g, Component.translatable("kultivator.sekte.misi.ui.laporan"), x + 2.0F, storyBottom - 11.0F, w - 4.0F, 0.7F);
		MisiTab.drawHistory(g, font, x + 2, storyBottom, x + w - 2, y + h, 40);
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
		if (tab == TAB_STORY || tab == TAB_NEWS) {
			builder.append(':').append(MisiTab.shape());
		}
		return builder.toString();
	}

	// ================================================================== drawing

	@Override
	protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		int rightX = x + colW + 4;
		int rightW = x + w - rightX;
		if ((tab == TAB_STORY || tab == TAB_NEWS) && MisiClient.version() != seenMisi) {
			// module X's own payload arrived: rebuild the buttons when what exists changed
			seenMisi = MisiClient.version();
			if (needsRebuild()) {
				layout(x, y, w, h);
			}
		}
		switch (tab) {
			case 0 -> drawBoard(g, mouseX, mouseY, rightX, rightW);
			case 1 -> drawTeams(g);
			case 2 -> drawEnvoy(g, rightX, rightW);
			case 3 -> drawForbidden(g, rightX, rightW);
			case TAB_STORY -> drawStory(g, mouseX, mouseY, rightX, rightW);
			case TAB_NEWS -> drawNews(g, mouseX, mouseY);
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
		if (tab == TAB_STORY && mouseX >= x + 2 && mouseX < x + colW - 2 && mouseY >= top + 12 && mouseY < storyBottom) {
			int index = (int) ((mouseY - top - 12) / MisiTab.CARD_H);
			CompoundTag tag = MisiTab.offer(index);
			if (tag != null && !tag.getBooleanOr("taken", false)) {
				storyOffer = storyOffer == index ? -1 : index;
				UiSound.toggle(storyOffer == index);
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
