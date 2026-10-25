package dev.kultivator.client.disciple;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.codex.CodexFilters;
import dev.kultivator.client.codex.CodexGrid;
import dev.kultivator.client.codex.SpecInfo;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SealCard;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Actions;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Techniques in teach mode (DESIGN 22.13.6): the master's known specs in the codex grid (the same seal cards, the
 * filters Dao / state / sort cycling on talismans), the lock reasons from the server's {@code infoFor}, and on the right
 * the picked art read out (Dao, grade and type, Sumber, the disciple's own qi and cooldown, why it cannot be taught)
 * with the "Ajarkan" talisman and the "Siapkan" combat slots ({@code 3 + realm/2 + stars/9}, at most 10): click a
 * socket to put the picked learned art in it, right click empties it. Teaching costs the master a share of max qi by
 * grade; the server checks everything again. A 9-star title's Dewa offer ({@code dewa_pending}) lists its arts here too:
 * the talisman then reads "Pahami" and the pick is free. Keyboard (43.0.7): Tab reaches the grid (arrows, PgUp/PgDn,
 * Home/End pick a card, Enter teaches) and every socket (Enter fills it, Delete/Backspace empties it).
 */
final class TechniqueTab extends MuridSub {

	private static final int HEADER = 13;
	private static final int BUTTON_H = 16;
	/** A combat socket: 16 x 16, the kit's smallest click box or more (43.0.6); they wrap to a second row when narrow. */
	private static final int SOCKET = 16;
	private static final String KISI = "kisi";
	private static final String SOKET = "soket.";
	private static final CodexFilters FILTERS = new CodexFilters("teach");

	private final CodexGrid grid = new CodexGrid();
	private final Map<String, TeachCard.Entry> entries = new LinkedHashMap<>();
	/** The master's qi to teach each spec, from the server's {@code teach_cost} (grade share and hooks, 22.13.2). */
	private final Map<String, Double> teachCosts = new java.util.HashMap<>();
	/** The Dewa arts the master may pick for this disciple's 9-star title ({@code dewa_pending}, 22.13.2). */
	private final java.util.Set<String> dewaOffers = new java.util.LinkedHashSet<>();
	private List<String> builtFor = List.of();
	private @Nullable TechniqueSpec selected;
	private int gridX;
	private int gridY;
	private int gridW;
	private int gridH;
	private int detailX;
	private int detailW;

	private final CodexFilters.View view = new CodexFilters.View() {
		@Override
		public SpecInfo.State state(TechniqueSpec spec) {
			TeachCard.Entry entry = entries.get(spec.id());
			if (entry == null) {
				return SpecInfo.State.LOCKED;
			}
			return entry.learned() ? SpecInfo.State.LEARNED : entry.ok() ? SpecInfo.State.LEARNABLE : SpecInfo.State.LOCKED;
		}

		@Override
		public boolean castable(TechniqueSpec spec) {
			TeachCard.Entry entry = entries.get(spec.id());
			return entry != null && entry.learned();
		}

		@Override
		public int mastery(TechniqueSpec spec) {
			TeachCard.Entry entry = entries.get(spec.id());
			return entry == null ? -1 : entry.mastery();
		}
	};

	TechniqueTab(MuridPage murid) {
		super(murid);
	}

	/** Its own scroll when the box is shorter (GUI scale 4): the detail column, the sockets and the talisman at 1.0. */
	@Override
	public int tinggiMin() {
		return 184;
	}

	@Override
	public String helpTopic() {
		return "teknik";
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.discipleui.tab.techniques");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.discipleui.tab.techniques.short");
	}

	// ================================================================== the server's list

	/** Reads the teachable list ({id, ok, known, reason_key, qi_cost, cooldown, mastery}) and the disciple's learned ids. */
	private void readEntries() {
		entries.clear();
		teachCosts.clear();
		CompoundTag info = info();
		List<String> learned = new ArrayList<>();
		for (CompoundTag scope : scopes(info)) {
			for (Tag element : scope.getListOrEmpty("learned")) {
				String id = element instanceof CompoundTag entry ? entry.getStringOr("id", "") : element.asString().orElse("");
				if (!id.isEmpty()) {
					learned.add(id);
				}
			}
		}
		boolean any = false;
		for (CompoundTag scope : scopes(info)) {
			Tag teachable = scope.get("teachable");
			if (teachable instanceof ListTag list) {
				any = true;
				for (Tag element : list) {
					if (element instanceof CompoundTag entry) {
						add(entry.getStringOr("id", entry.getStringOr("technique", "")), entry, learned);
					} else {
						element.asString().ifPresent(id -> add(id, null, learned));
					}
				}
			} else if (teachable instanceof CompoundTag map) {
				any = true;
				for (String key : map.keySet()) {
					add(key, map.get(key) instanceof CompoundTag entry ? entry : null, learned);
				}
			}
		}
		if (!any) {
			guess(learned);
		}
		// a 9-star title's Dewa offer: picked for free, whether or not the master knows the art
		dewaOffers.clear();
		for (Tag element : info.getListOrEmpty("dewa_pending")) {
			String id = element.asString().orElse("");
			TechniqueSpec spec = TechniqueRegistry.byId(id);
			if (spec != null && !learned.contains(id)) {
				dewaOffers.add(id);
				TeachCard.Entry old = entries.get(id);
				entries.put(id, new TeachCard.Entry(spec, true, false, null, old == null ? -1.0D : old.qiCost(), old == null ? -1 : old.cooldown(), 0));
			}
		}
	}

	private boolean offered() {
		return selected != null && dewaOffers.contains(selected.id());
	}

	private static List<CompoundTag> scopes(CompoundTag info) {
		List<CompoundTag> scopes = new ArrayList<>();
		scopes.add(info);
		for (String key : new String[]{"data", "disciple", "cultivation"}) {
			if (info.get(key) instanceof CompoundTag nested) {
				scopes.add(nested);
			}
		}
		return scopes;
	}

	private void add(String id, @Nullable CompoundTag entry, List<String> learned) {
		TechniqueSpec spec = TechniqueRegistry.byId(id);
		if (spec == null || entries.containsKey(id) || "fusion_pair".equals(spec.special())) {
			return;
		}
		boolean known = learned.contains(id) || entry != null && (entry.getBooleanOr("known", false) || entry.getBooleanOr("learned", false));
		boolean ok = entry == null || entry.getBooleanOr("ok", entry.getBooleanOr("can", false));
		String reasonKey = entry == null ? "" : entry.getStringOr("reason_key", entry.getStringOr("reason", ""));
		Component reason = reasonKey.isEmpty() ? null : Component.translatable(reasonKey);
		double qiCost = entry == null ? -1.0D : entry.getDoubleOr("qi_cost", -1.0D);
		int cooldown = entry == null ? -1 : entry.getIntOr("cooldown", -1);
		int mastery = entry == null ? 0 : entry.getIntOr("mastery", 0);
		if (entry != null && entry.get("teach_cost") != null) {
			teachCosts.put(id, entry.getDoubleOr("teach_cost", 0.0D));
		}
		entries.put(id, new TeachCard.Entry(spec, ok && !known, known, reason, qiCost, cooldown, mastery));
	}

	/** Without a server list: the master's known teachable specs, checked by realm and Dao here. */
	private void guess(List<String> learned) {
		CultivationData master = ClientCultivation.data();
		DiscipleView view = view();
		for (TechniqueSpec spec : master.learnedSpecs()) {
			if (!spec.playerVisible() || "fusion_pair".equals(spec.special())) {
				continue;
			}
			boolean known = learned.contains(spec.id());
			Component reason = null;
			if (!spec.teachable()) {
				reason = Component.translatable("kultivator.codex.teach.never");
			} else if (!view.rank.realm().atLeast(spec.minRealm())) {
				reason = Component.translatable("kultivator.discipleui.reason.realm", spec.minRealm().displayName());
			} else if (spec.dao() != null && view.dao != null && spec.dao() != view.dao) {
				reason = Component.translatable("kultivator.discipleui.reason.dao", spec.dao().displayName());
			}
			entries.put(spec.id(), new TeachCard.Entry(spec, reason == null && !known, known, reason, -1.0D, -1, 0));
		}
	}

	private List<TechniqueSpec> listed() {
		List<TechniqueSpec> list = new ArrayList<>();
		for (TeachCard.Entry entry : entries.values()) {
			list.add(entry.spec());
		}
		return FILTERS.apply(list, view);
	}

	// ================================================================== layout

	@Override
	protected void build() {
		readEntries();
		builtFor = new ArrayList<>(entries.keySet());
		detailW = Math.max(100, Math.min(170, Math.round(w * 0.42F)));
		detailX = x + w - detailW;
		gridX = x;
		gridY = y + HEADER + 13;
		gridW = w - detailW - 6;
		gridH = y + h - gridY;
		int fw = (gridW - 4) / 3;
		TalismanButton dao = add(new TalismanButton(gridX, y + HEADER, fw, 12, FILTERS::daoLabel));
		dao.onPress(() -> {
			FILTERS.dao = FILTERS.dao >= Dao.values().length - 1 ? -2 : FILTERS.dao + 1;
			FILTERS.save();
			refresh();
		});
		dao.tooltip(() -> List.of(Component.translatable("kultivator.codex.filter.dao", FILTERS.daoLabel())));
		TalismanButton state = add(new TalismanButton(gridX + fw + 2, y + HEADER, fw, 12, () -> Component.translatable("kultivator.codex.teach.state."
				+ FILTERS.state)));
		state.onPress(() -> {
			FILTERS.state = (FILTERS.state + 1) % 4;
			FILTERS.save();
			refresh();
		});
		TalismanButton sort = add(new TalismanButton(gridX + (fw + 2) * 2, y + HEADER, fw, 12, FILTERS::sortLabel));
		sort.onPress(() -> {
			FILTERS.cycleSort();
			refresh();
		});
		grid.layout(gridX, gridY, gridW, gridH, 28, 40);
		refresh();
		if (selected == null || !entries.containsKey(selected.id())) {
			selected = firstTeachable();
		}
		grid.select(selected);

		TalismanButton teach = new TalismanButton(detailX + 5, y + h - BUTTON_H - 3, detailW - 10, BUTTON_H,
				() -> Component.translatable(offered() ? "kultivator.teknik.dewa_offer.button" : "kultivator.discipleui.tech.teach"));
		teach.enabledWhen(() -> offered() || canTeach());
		teach.urgentWhen(() -> offered() || canTeach() && masterQi() >= cost());
		teach.onPress(() -> {
			if (selected == null) {
				return;
			}
			if (offered()) {
				send(Actions.TECHNIQUE, "dlearn:" + selected.id());
			} else {
				send(Actions.DISCIPLE_TEACH, selected.id());
			}
		});
		teach.tooltip(this::teachTip);
		add(teach);
	}

	private void refresh() {
		grid.setSpecs(listed(), FILTERS.sort == CodexFilters.Sort.GRADE);
	}

	@Override
	public boolean needsRebuild() {
		List<String> before = builtFor;
		readEntries();
		boolean changed = !before.equals(new ArrayList<>(entries.keySet()));
		if (!changed) {
			refresh();
		}
		return changed;
	}

	private @Nullable TechniqueSpec firstTeachable() {
		for (TeachCard.Entry entry : entries.values()) {
			if (entry.ok()) {
				return entry.spec();
			}
		}
		return entries.isEmpty() ? null : entries.values().iterator().next().spec();
	}

	private boolean canTeach() {
		TeachCard.Entry entry = selected == null ? null : entries.get(selected.id());
		return entry != null && entry.ok() && !entry.learned();
	}

	/** Qi the master pays: the server's figure, else the grade share of the master's max qi (22.13.2). */
	private double cost() {
		Double perSpec = selected == null ? null : teachCosts.get(selected.id());
		if (perSpec != null) {
			return perSpec;
		}
		double cost = view().teachCost;
		if (cost >= 0.0D) {
			return cost;
		}
		double share = selected == null ? 0.2D : selected.grade().teachQiShare();
		return Qi.max(ClientCultivation.data()) * share;
	}

	private static double masterQi() {
		return ClientCultivation.data().qi();
	}

	private List<Component> teachTip() {
		List<Component> lines = new ArrayList<>();
		if (offered()) {
			lines.add(Component.translatable("kultivator.teknik.dewa_offer.button"));
			lines.add(Component.translatable("kultivator.teknik.dewa_offer.status").withStyle(ChatFormatting.GOLD));
			return lines;
		}
		lines.add(Component.translatable("kultivator.discipleui.tech.teach"));
		lines.add(Component.translatable("kultivator.discipleui.tech.cost", Msg.number(cost())).withStyle(ChatFormatting.AQUA));
		lines.add(Component.translatable("kultivator.discipleui.tech.your_qi", Msg.number(masterQi()), Msg.number(Qi.max(ClientCultivation.data())))
				.withStyle(ChatFormatting.GRAY));
		if (selected != null) {
			lines.add(statusLine(selected));
		}
		if (canTeach() && masterQi() < cost()) {
			lines.add(Component.translatable("kultivator.discipleui.tech.low_qi").withStyle(ChatFormatting.RED));
		}
		return lines;
	}

	/** "Already mastered" / "Can be taught" / the reason it cannot. */
	private Component statusLine(TechniqueSpec spec) {
		TeachCard.Entry entry = entries.get(spec.id());
		if (entry == null) {
			return Component.translatable("kultivator.discipleui.reason.unknown").withStyle(ChatFormatting.RED);
		}
		if (entry.learned()) {
			return Component.translatable("kultivator.discipleui.tech.learned").withStyle(ChatFormatting.GOLD);
		}
		if (dewaOffers.contains(spec.id())) {
			return Component.translatable("kultivator.teknik.dewa_offer.status").withStyle(ChatFormatting.GOLD);
		}
		if (entry.ok()) {
			return Component.translatable("kultivator.discipleui.tech.ok").withStyle(ChatFormatting.DARK_GREEN);
		}
		Component reason = entry.reason() != null ? entry.reason() : Component.translatable("kultivator.discipleui.tech.not_ok");
		return reason.copy().withStyle(ChatFormatting.RED);
	}

	// ================================================================== combat slots

	/** The disciple's combat slots: 3 + realm/2 + stars/9, at most 10 (22.13.3). */
	private int slotCount() {
		int stars = info().getCompoundOrEmpty("path").getIntOr("stars", 0);
		return Math.max(1, Math.min(10, 3 + view().rank.realm().ordinal() / 2 + Math.max(0, stars) / 9));
	}

	/** The ids in the disciple's combat slots ({@code teknik_slots}, or {@code teknik.slots}). */
	private String[] slots() {
		int count = slotCount();
		String[] slots = new String[count];
		java.util.Arrays.fill(slots, "");
		ListTag list = info().getListOrEmpty("teknik_slots");
		if (list.isEmpty()) {
			list = info().getCompoundOrEmpty("teknik").getListOrEmpty("slots");
		}
		for (int i = 0; i < Math.min(count, list.size()); i++) {
			slots[i] = list.get(i).asString().orElse("");
		}
		return slots;
	}

	private int slotOf(String id, String[] slots) {
		for (int i = 0; i < slots.length; i++) {
			if (slots[i].equals(id)) {
				return i;
			}
		}
		return -1;
	}

	/** Sockets per row in the detail box. */
	private int perRow() {
		return Math.max(1, (detailW - 10) / (SOCKET + 2));
	}

	/** The top of the socket rows for {@code count} sockets (above the talisman). */
	private int slotsTop(int count) {
		int rows = (count + perRow() - 1) / perRow();
		return y + h - BUTTON_H - 6 - rows * (SOCKET + 2);
	}

	private int socketX(int i) {
		return detailX + 5 + (i % perRow()) * (SOCKET + 2);
	}

	private int socketY(int i, int count) {
		return slotsTop(count) + (i / perRow()) * (SOCKET + 2);
	}

	private int socketAt(double mouseX, double mouseY) {
		int count = slotCount();
		for (int i = 0; i < count; i++) {
			int sx = socketX(i);
			int sy = socketY(i, count);
			if (mouseX >= sx && mouseX < sx + SOCKET && mouseY >= sy && mouseY < sy + SOCKET) {
				return i;
			}
		}
		return -1;
	}

	/** Puts the picked learned art in socket {@code i} (a click, Enter on a focused socket). */
	private void fill(int i) {
		if (selected != null && entries.containsKey(selected.id()) && entries.get(selected.id()).learned()) {
			UiSound.press();
			send(Actions.TECHNIQUE, "dslot:" + i + ":" + selected.id());
		} else {
			UiSound.denied();
			pesan(Component.translatable("kultivator.codex.teach.slot.tip"), Palette.INK_SOFT);
		}
	}

	private void empty(int i) {
		UiSound.toggle(false);
		send(Actions.TECHNIQUE, "dslot:" + i + ":-");
	}

	private void teachSelected() {
		if (selected == null || !(offered() || canTeach())) {
			UiSound.denied();
			return;
		}
		UiSound.press();
		send(offered() ? Actions.TECHNIQUE : Actions.DISCIPLE_TEACH, offered() ? "dlearn:" + selected.id() : selected.id());
	}

	// ================================================================== the Tab ring

	@Override
	protected void lukis(Lukis l) {
		if (entries.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.NAMPAN, Component.translatable("kultivator.discipleui.tech.none"), null, null, gridX, gridY,
					gridX + gridW, y + h);
		} else {
			Lukis.Zona kisi = l.zona(KISI, gridX, gridY, gridX + gridW, gridY + gridH, null, true, null, true);
			if (kisi != null) {
				kisi.geser = arah -> pickWith(arah > 0 ? InputConstants.KEY_RIGHT : InputConstants.KEY_LEFT);
			}
			int[] box = grid.kotakTerpilih();
			if (box != null) {
				l.fokusCincin(KISI, box[0], box[1], box[2], box[3]);
			}
		}
		int count = slotCount();
		for (int i = 0; i < count; i++) {
			int sx = socketX(i);
			int sy = socketY(i, count);
			int index = i;
			Lukis.Zona z = l.zona(SOKET + i, sx, sy, sx + SOCKET, sy + SOCKET, () -> fill(index), true, null, true);
			if (z != null) {
				z.kanan = () -> empty(index);
			}
			l.fokusCincin(SOKET + i, sx, sy, sx + SOCKET, sy + SOCKET);
		}
	}

	private void pickWith(int key) {
		if (grid.tombol(key)) {
			selected = grid.selected();
			UiSound.tab();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		String focused = fokusRing.kunci();
		if (!fokusRing.mauTombol() && focused != null && focused.startsWith(SOKET)
				&& (key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE)) {
			empty(Integer.parseInt(focused.substring(SOKET.length())));
			return true;
		}
		if (!fokusRing.mauTombol() && KISI.equals(focused) && event.isSelection()) {
			// Enter / Space on the grid: teach (or take the Dewa offer of) the picked card
			teachSelected();
			return true;
		}
		if (!fokusRing.mauTombol() && KISI.equals(focused) && (event.isUp() || event.isDown() || key == InputConstants.KEY_PAGEUP
				|| key == InputConstants.KEY_PAGEDOWN || key == InputConstants.KEY_HOME || key == InputConstants.KEY_END)) {
			pickWith(key);
			return true;
		}
		return super.keyPressed(event);
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		heading(g, Component.translatable("kultivator.discipleui.tech.heading"), x + 2.0F, y, gridW * 0.6F, 1.0F);
		int learned = 0;
		for (TeachCard.Entry entry : entries.values()) {
			if (entry.learned()) {
				learned++;
			}
		}
		Component count = Component.translatable("kultivator.discipleui.tech.count", learned, entries.size());
		float countW = Math.min(gridW * 0.38F, font.width(count));
		Brush.fit(g, font, count, x + gridW - countW, y + 1.0F, gridW * 0.38F, Palette.INK_SOFT, 1.0F, 1.0F, false);
		String[] slots = slots();
		grid.render(g, font, mouseX, mouseY, seconds, spec -> TeachCard.look(entries.get(spec.id()), slotOf(spec.id(), slots), spec == selected));
		TechniqueSpec hovered = grid.hovered();
		if (hovered != null) {
			List<Component> tip = new ArrayList<>();
			tip.add(hovered.displayName());
			tip.add(SpecInfo.sourceLine(hovered));
			tip.add(statusLine(hovered));
			tooltip(tip);
		}
		drawDetail(g, slots, mouseX, mouseY);
	}

	private void drawDetail(GuiGraphicsExtractor g, String[] slots, int mouseX, int mouseY) {
		int x0 = detailX;
		int x1 = x + w;
		int y0 = y;
		int y1 = y + h;
		panel(g, x0, y0, x1, y1);
		float tx = x0 + 5.0F;
		float room = detailW - 10.0F;
		float ty = y0 + 5.0F;
		if (selected == null) {
			Brush.wrap(g, font, Component.translatable("kultivator.discipleui.tech.select"), tx, ty, room, Palette.INK_SOFT, 1.0F, false);
			return;
		}
		TechniqueSpec spec = selected;
		int tone = SpecInfo.ink(spec);
		Glyphs.spec(g, spec, tx + 5.0F, ty + 5.0F, 9.0F, tone);
		Brush.fit(g, font, Brush.bold(spec.displayName()), tx + 13.0F, ty + 1.0F, room - 13.0F, tone, 1.0F, 1.0F, false);
		ty += 12.0F;
		Component kind = Component.translatable("kultivator.codex.tip.grade_type", SpecInfo.gradeName(spec), spec.type().displayName());
		Brush.fit(g, font, Component.empty().append(SpecInfo.daoName(spec)).append(" · ").append(kind), tx, ty, room, Palette.INK_SOFT, 1.0F, 1.0F,
				false);
		ty += 10.0F;
		// the source line: one line cut with "…" (full text in the card's tooltip)
		Brush.fit(g, font, SpecInfo.sourceLine(spec), tx, ty, room, Palette.INK_FAINT, 1.0F, 1.0F, false);
		ty += 10.0F;
		TeachCard.Entry entry = entries.get(spec.id());
		double qiCost = entry != null && entry.qiCost() >= 0.0D ? entry.qiCost() : spec.qi() * 0.6D;
		int cooldown = entry != null && entry.cooldown() >= 0 ? entry.cooldown() : spec.cooldownTicks();
		Component cost = Component.translatable("kultivator.discipleui.tech.cost_cd", Msg.number(Math.ceil(qiCost)), SealCard.seconds(cooldown));
		Brush.fit(g, font, cost, tx, ty, room, Palette.onPaper(Palette.QI), 1.0F, 1.0F, false);
		ty += 10.0F;
		Brush.rect(g, tx, ty, x1 - 5.0F, ty + 0.6F, Palette.argb(0x90, Palette.GOLD));
		ty += 3.0F;
		int slotsY = slotsTop(slots.length);
		float descBottom = slotsY - 12.0F;
		g.enableScissor(x0 + 1, Math.round(ty), x1 - 1, Math.max(Math.round(ty), Math.round(descBottom)));
		ty += Brush.wrap(g, font, statusLine(spec), tx, ty, room, Palette.INK, 1.0F, false) + 2.0F;
		if (descBottom - ty > 8.0F) {
			Brush.wrap(g, font, spec.description(), tx, ty, room, Palette.INK_SOFT, 1.0F, false);
		}
		g.disableScissor();
		// "Siapkan": the combat slots.
		Brush.fit(g, font, Component.translatable("kultivator.codex.teach.slots", slots.length), tx, slotsY - 11.0F, room, Palette.SEAL_DARK, 1.0F, 1.0F,
				false);
		int over = socketAt(mouseX, mouseY);
		int size = SOCKET;
		for (int i = 0; i < slots.length; i++) {
			int sx = socketX(i);
			int sy = socketY(i, slots.length);
			g.fill(sx, sy, sx + size, sy + size, i == over ? 0xFFF7DFA0 : Palette.TALISMAN);
			Brush.frame(g, sx, sy, sx + size, sy + size, Palette.TALISMAN_EDGE);
			TechniqueSpec there = slots[i].isEmpty() ? null : TechniqueRegistry.byId(slots[i]);
			if (there != null) {
				Glyphs.spec(g, there, sx + size / 2.0F, sy + size / 2.0F, size * 0.62F, SpecInfo.ink(there));
			}
		}
		if (over >= 0) {
			TechniqueSpec there = slots[over].isEmpty() ? null : TechniqueRegistry.byId(slots[over]);
			tooltip(List.of(Component.translatable("kultivator.codex.teach.slot", over + 1),
					there == null ? Component.translatable("kultivator.codex.ring.empty").withStyle(ChatFormatting.GRAY) : there.displayName(),
					Component.translatable("kultivator.codex.teach.slot.tip").withStyle(ChatFormatting.DARK_GRAY)));
		}
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();
		int socket = socketAt(mouseX, mouseY);
		if (socket >= 0) {
			fokusRing.set(SOKET + socket);
			if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
				empty(socket);
			} else {
				fill(socket);
			}
			return true;
		}
		// a card first: the grid's zone would otherwise teach on a plain click
		TechniqueSpec spec = grid.at(mouseX, mouseY);
		if (spec != null && button == InputConstants.MOUSE_BUTTON_LEFT) {
			UiSound.press();
			selected = spec;
			grid.select(spec);
			fokusRing.set(KISI);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double amount) {
		return grid.scrolled(mouseX, mouseY, amount) || super.mouseScrolled(mouseX, mouseY, scrollX, amount);
	}
}
