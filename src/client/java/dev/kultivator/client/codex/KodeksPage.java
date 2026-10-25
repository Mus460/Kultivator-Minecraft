package dev.kultivator.client.codex;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.InkField;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SealCard;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.goals.Guide;
import dev.kultivator.network.Actions;
import dev.kultivator.technique.spec.TechniquePower;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Teknik › Kodeks (DESIGN 22.12.2-22.12.4, 43.5; a wide page): the strip of the old Teknik launcher (selected seal, power,
 * best mastery, the keys) when the paper is tall enough, else in the counter's tooltip; row A the search, the counter and
 * the sort talisman; row B the Dao and grade coins; row C the Ranah, Jenis and state talismans and the Pamungkas coin;
 * then the virtualized seal-card grid, grouped by grade when sorted by grade. The bamboo detail slip docks on the right
 * when the page is wide enough (R31); on narrow paper it replaces the grid as a detail view (Esc / back returns).
 * Keyboard (43.4.2): the grid is one stop of the Tab ring ("kisi"); the arrows move the selected card (↑/↓ also focus the
 * grid when nothing has the focus), Enter opens its slip and puts the focus on Pilih; ↑↓ PgUp PgDn scroll the slip while it
 * is the detail view or one of its talismans is focused. No match: the empty state offers to clear every filter.
 */
public final class KodeksPage extends Page {

	private static final CodexFilters FILTERS = CodexFilters.player();
	private static final int ROW = 14;
	private static final int CTRL_H = 13;
	private static final int COIN_W = 16;
	private static final String M = "kultivator.gui43.m02.";
	private static final String KISI = "kisi";

	/** Draws an icon centred on (cx, cy) of the given size and colour. */
	private interface Ikon {
		void draw(GuiGraphicsExtractor g, float cx, float cy, float size, int color);
	}

	private final CodexFilters filters = FILTERS;
	private final CodexGrid grid = new CodexGrid();
	private final DetailSlip slip = new DetailSlip();
	private final List<Widget> filterWidgets = new ArrayList<>();
	private boolean docked;
	/** Narrow paper: the slip is shown instead of the grid. */
	private boolean detail;
	private int strip;
	private int gridX;
	private int gridW;
	private int rowA;
	private int rowB;
	private int coinRows;
	private int counterX;
	private int counterW;
	private int gridTop;
	private @Nullable InkField search;
	private long builtVersion = -1L;
	private final CodexFilters.View view = new CodexFilters.View() {
		@Override
		public SpecInfo.State state(TechniqueSpec spec) {
			return SpecInfo.state(data(), spec);
		}

		@Override
		public boolean castable(TechniqueSpec spec) {
			return SpecInfo.castable(data(), spec);
		}

		@Override
		public int mastery(TechniqueSpec spec) {
			return data().knows(spec.id()) ? SpecInfo.mastery(data(), spec) : -1;
		}
	};

	public KodeksPage() {
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.teknik.kodeks");
	}

	@Override
	public int glyph() {
		return Glyphs.CODEX;
	}

	@Override
	public String helpTopic() {
		return "kitab_teknik";
	}

	@Override
	public boolean lebar() {
		return true;
	}

	@Override
	public boolean rinci() {
		return detail;
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		// The guide's "open the Kitab Teknik" step (was GoalsClient's check for the old codex screen).
		if (Keys.connected() && !Guide.seen(data())) {
			send(Actions.JALAN, 0, "seen:codex");
		}
		super.onShow(fokus);
	}

	@Override
	public void fokus(CompoundTag fokus) {
		int net = fokus.getIntOr("spec", -1);
		TechniqueSpec spec = net < 0 ? null : TechniqueRegistry.byNet(net);
		if (spec != null && spec.playerVisible()) {
			focus(spec);
		}
	}

	/** Opens the detail of {@code spec} and scrolls the grid to it. */
	void focus(TechniqueSpec spec) {
		grid.select(spec);
		slip.setSpec(spec);
		detail = !docked;
		grid.reveal(spec);
	}

	@Override
	protected void build() {
		filterWidgets.clear();
		int slipW = Math.max(160, Math.min(210, Math.round(w * 0.38F)));
		docked = w - slipW - 4 >= 200;
		if (docked) {
			detail = false;
		}
		gridX = x;
		gridW = docked ? w - slipW - 4 : w;
		strip = h >= 200 ? ROW : 0;
		rowA = y + strip;

		// Row A: search, counter, sort.
		int sortW = Math.max(64, Math.min(96, gridW / 4));
		int searchW = Math.max(70, Math.round(gridW * 0.42F));
		search = filter(new InkField(gridX, rowA, searchW, CTRL_H, 40, null)
				.cari(Component.translatable("kultivator.codex.search.hint"), text -> {
					filters.query = text;
					refresh();
				}));
		search.setText(filters.query);
		TalismanButton sort = filter(new TalismanButton(gridX + gridW - sortW, rowA, sortW, CTRL_H,
				() -> Component.translatable(M + "urut", filters.sortLabel())));
		sort.siklus(() -> {
			filters.cycleSort();
			refresh();
		}, () -> {
			for (int i = 0; i < CodexFilters.Sort.values().length - 1; i++) {
				filters.cycleSort();
			}
			refresh();
		});
		sort.tooltip(() -> List.of(Component.translatable("kultivator.codex.sort"), filters.sortLabel(),
				Component.translatable("kultivator.codex.filter.cycle").withStyle(ChatFormatting.GRAY)));
		counterX = gridX + searchW + 4;
		counterW = gridW - searchW - sortW - 8;

		// Row B: the Dao and grade coins (drawn as zones in lukis), one row when they fit, else two.
		rowB = rowA + ROW;
		coinRows = gridW >= (10 + 6) * (COIN_W + 1) + 4 ? 1 : 2;

		// Row C: Ranah, Jenis, state; the Pamungkas coin at its end.
		int rowC = rowB + ROW * coinRows;
		int cycleW = (gridW - COIN_W - 8) / 3;
		TalismanButton realm = filter(new TalismanButton(gridX, rowC, cycleW, CTRL_H, filters::realmLabel));
		realm.siklus(() -> {
			filters.cycleRealm(1);
			refresh();
		}, () -> {
			filters.cycleRealm(-1);
			refresh();
		});
		realm.tooltip(() -> List.of(Component.translatable("kultivator.codex.filter.realm"), filters.realmLabel(),
				Component.translatable("kultivator.codex.filter.cycle").withStyle(ChatFormatting.GRAY)));
		TalismanButton type = filter(new TalismanButton(gridX + cycleW + 2, rowC, cycleW, CTRL_H, filters::typeLabel));
		type.siklus(() -> {
			filters.cycleType(1);
			refresh();
		}, () -> {
			filters.cycleType(-1);
			refresh();
		});
		type.tooltip(() -> List.of(Component.translatable("kultivator.codex.filter.type"), filters.typeLabel(),
				Component.translatable("kultivator.codex.filter.cycle").withStyle(ChatFormatting.GRAY)));
		TalismanButton state = filter(new TalismanButton(gridX + (cycleW + 2) * 2, rowC, cycleW, CTRL_H,
				() -> CodexFilters.stateLabel(filters.state)));
		state.siklus(() -> stepState(1), () -> stepState(-1));
		state.tooltip(() -> List.of(Component.translatable(M + "keadaan"), CodexFilters.stateLabel(filters.state),
				Component.translatable("kultivator.codex.filter.state." + filters.state + ".tip").withStyle(ChatFormatting.GRAY)));

		gridTop = rowC + ROW + 2;
		grid.layout(gridX, gridTop, gridW, y + h - gridTop, 28, 40);
		if (docked) {
			slip.layout(x + w - slipW, y, slipW, h);
		} else {
			slip.layout(x, y, w, h);
		}
		refresh();
	}

	private <T extends Widget> T filter(T widget) {
		filterWidgets.add(widget);
		return add(widget);
	}

	private void stepState(int step) {
		filters.state = Math.floorMod(filters.state + step, 4);
		filters.save();
		refresh();
	}

	private void refresh() {
		List<TechniqueSpec> list = filters.apply(SpecInfo.visible(), view);
		grid.setSpecs(list, filters.sort == CodexFilters.Sort.GRADE);
		builtVersion = ClientCultivation.version();
	}

	@Override
	public void onData() {
		if (ClientCultivation.version() != builtVersion && (filters.state != 0 || filters.realm == 1 || filters.sort == CodexFilters.Sort.MASTERY)) {
			refresh();
		}
	}

	@Override
	protected boolean visible(Widget widget) {
		return !detail || !filterWidgets.contains(widget);
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		CultivationData data = data();
		if (detail) {
			slip.render(l);
			return;
		}
		GuiGraphicsExtractor g = l.g;
		if (strip > 0) {
			drawStrip(g, data);
		}
		Component counter = Component.translatable("kultivator.codex.launcher.counter", SpecInfo.learnedCount(data), TechniqueRegistry.playerTotal());
		Brush.fitCentered(g, font, Brush.bold(counter), counterX + counterW / 2.0F, rowA + 2.5F, counterW, Palette.SEAL_DARK, 1.0F, 1.0F, false);
		if (l.di(counterX, rowA, counterX + counterW, rowA + CTRL_H)) {
			l.tip(stripLines(data));
		}
		coins(l);
		koin(l, "pamungkas", gridX + gridW - COIN_W, rowB + ROW * coinRows, Palette.SEAL,
				(gg, px, py, s, c) -> Glyphs.typeMark(gg, TechniqueType.PAMUNGKAS, px, py, s * 0.8F, c), filters.ultimates, () -> {
					filters.ultimates = !filters.ultimates;
					filters.save();
					refresh();
				}, List.of(Component.translatable("kultivator.codex.filter.ultimates"),
						Component.translatable("kultivator.codex.filter.ultimates.tip").withStyle(ChatFormatting.GRAY)));

		String[] slots = TeknikSession.slots();
		String selected = data.selectedId();
		boolean overSlip = docked && slip.contains(l.mx, l.my);
		grid.render(g, font, overSlip ? -1 : l.mx, overSlip ? -1 : l.my, l.detik, spec -> look(data, spec, slots, selected));
		if (grid.specs().isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable("kultivator.codex.empty"),
					Component.translatable("kultivator.gui43.p2.saring.kosongkan"), this::kosongkanSaringan, gridX, gridTop, gridX + gridW, y + h);
		} else {
			// The grid as one stop of the Tab ring: ←/→ here, ↑↓ PgUp PgDn Home End in keyPressed, Enter opens the slip.
			Lukis.Zona kisi = l.zona(KISI, gridX, gridTop, gridX + gridW, y + h, this::bukaTerpilih, true, null, true);
			if (kisi != null) {
				kisi.geser = arah -> pindah(arah > 0 ? InputConstants.KEY_RIGHT : InputConstants.KEY_LEFT);
			}
			int[] box = grid.kotakTerpilih();
			if (box != null) {
				l.fokusCincin(KISI, box[0], box[1], box[2], box[3]);
			}
		}
		TechniqueSpec hovered = grid.hovered();
		if (hovered != null && !overSlip) {
			l.tip(tooltip(data, hovered));
		}
		if (docked) {
			slip.render(l);
		}
	}

	/** The strip of the old Teknik launcher: selected seal, its name, power and best mastery. */
	private void drawStrip(GuiGraphicsExtractor g, CultivationData data) {
		TechniqueSpec selected = data.selectedId().isEmpty() ? null : TechniqueRegistry.byId(data.selectedId());
		Brush.rect(g, x, y + strip - 2.0F, x + w, y + strip - 1.4F, Palette.argb(0x80, Palette.GOLD));
		float tx = x + 2.0F;
		if (selected != null) {
			Glyphs.spec(g, selected, x + 6.0F, y + 5.5F, 10.0F, SpecInfo.ink(selected));
			tx = x + 14.0F;
		}
		Component line = Component.translatable(M + "strip",
				selected == null ? Component.translatable("kultivator.screen.tech.none_selected") : selected.displayName(),
				SpecInfo.compact(TechniquePower.power(data)), SpecInfo.masteryName(bestMastery(data)));
		Brush.fit(g, font, line, tx, y + 1.5F, x + w - tx - 2.0F, selected == null ? Palette.INK_SOFT : Palette.INK, 1.0F, 1.0F, false);
	}

	private static int bestMastery(CultivationData data) {
		int best = 0;
		for (TechniqueSpec spec : data.learnedSpecs()) {
			best = Math.max(best, SpecInfo.mastery(data, spec));
		}
		return best;
	}

	/** The launcher's summary as tooltip lines (the counter's tooltip; the whole strip on short paper). */
	private static List<Component> stripLines(CultivationData data) {
		TechniqueSpec selected = data.selectedId().isEmpty() ? null : TechniqueRegistry.byId(data.selectedId());
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.codex.counter", SpecInfo.learnedCount(data), TechniqueRegistry.playerTotal()));
		lines.add(Component.translatable("kultivator.codex.launcher.power", SpecInfo.compact(TechniquePower.power(data))));
		lines.add(Component.translatable("kultivator.codex.launcher.mastery", SpecInfo.masteryName(bestMastery(data))));
		lines.add(selected == null ? Component.translatable("kultivator.screen.tech.none_selected")
				: Component.translatable("kultivator.codex.launcher.selected", selected.displayName()));
		lines.add(Component.translatable("kultivator.codex.launcher.keys", KeyHints.name(Keys.CAST), KeyHints.name(Keys.PREVIOUS),
				KeyHints.name(Keys.NEXT), KeyHints.name(Keys.CODEX)).withStyle(ChatFormatting.GRAY));
		return lines;
	}

	/** Row B: all, universal, the eight trigrams; then the six grade coins (a second row on narrow paper). */
	private void coins(Lukis l) {
		int cx = gridX;
		int cy = rowB;
		cx = daoCoin(l, cx, cy, -2, (g, px, py, s, c) -> {
			Brush.ring(g, px, py, s * 0.25F, s * 0.5F, c);
			Brush.dot(g, px, py, 0.8F, c);
		});
		cx = daoCoin(l, cx, cy, -1, (g, px, py, s, c) -> {
			Brush.ring(g, px, py, s * 0.25F, s * 0.45F, c);
			Brush.disc(g, px, py, s * 0.13F, c);
		});
		for (Dao dao : Glyphs.BAGUA) {
			cx = daoCoin(l, cx, cy, dao.ordinal(), (g, px, py, s, c) -> Glyphs.trigram(g, px, py, s, Math.max(0.8F, s * 0.15F), s * 0.3F,
					Glyphs.trigramOf(dao), c));
		}
		if (coinRows == 1) {
			cx += 4;
		} else {
			cx = gridX;
			cy += ROW;
		}
		for (TechniqueGrade grade : TechniqueGrade.values()) {
			int ribbon = SpecInfo.ribbon(grade);
			koin(l, "grade." + grade.name(), cx, cy, ribbon, (g, px, py, s, c) -> Brush.disc(g, px, py, s * 0.35F, ribbon), filters.gradeOn(grade), () -> {
				filters.toggleGrade(grade);
				refresh();
			}, List.of(Component.translatable("kultivator.codex.filter.grade", grade.displayName()),
					Component.translatable("kultivator.codex.filter.grade.tip").withStyle(ChatFormatting.GRAY)));
			cx += COIN_W + 1;
		}
	}

	private int daoCoin(Lukis l, int cx, int cy, int dao, Ikon icon) {
		int tint = dao >= 0 ? Palette.opaque(Dao.values()[dao].color()) : Palette.JADE_LIGHT;
		int saved = filters.dao;
		filters.dao = dao;
		Component label = filters.daoLabel();
		filters.dao = saved;
		koin(l, "dao." + dao, cx, cy, tint, icon, filters.dao == dao, () -> {
			filters.dao = dao;
			filters.save();
			refresh();
		}, List.of(Component.translatable("kultivator.codex.filter.dao", label)));
		return cx + COIN_W + 1;
	}

	/** A round jade coin with a carved icon in a 16×13 focusable zone; lit (gold rim, bright jade) while on. */
	private static void koin(Lukis l, String kunci, int x0, int y0, int tint, Ikon icon, boolean on, Runnable aksi, List<Component> tip) {
		int x1 = x0 + COIN_W;
		int y1 = y0 + CTRL_H;
		l.zona(kunci, x0, y0, x1, y1, aksi, true, null, true);
		boolean over = l.di(x0, y0, x1, y1);
		GuiGraphicsExtractor g = l.g;
		float r = CTRL_H / 2.0F - 0.5F;
		float cx = x0 + COIN_W / 2.0F;
		float cy = y0 + CTRL_H / 2.0F;
		if (on || over) {
			Brush.halo(g, cx, cy, r, 2.0F, on ? 0xFFE07A : 0x9CFFD0, on ? 0.8F : 0.6F);
		}
		Brush.disc(g, cx, cy, r, on ? Palette.GOLD : Palette.JADE_DULL);
		Brush.disc(g, cx, cy, r - 1.0F, on ? Palette.mix(Palette.JADE_LIGHT, tint, 0.35F) : Palette.mix(0xFFA9B5AE, tint, 0.2F));
		icon.draw(g, cx, cy, CTRL_H * 0.62F, on ? Palette.INK : Palette.INK_SOFT);
		l.fokusCincin(kunci, x0, y0, x1, y1);
		if (over) {
			l.tip(tip);
		}
	}

	/** How a card looks for the player (shared by the loadout picker). */
	static SealCard.Look look(CultivationData data, TechniqueSpec spec, String[] slots, String selected) {
		SpecInfo.State state = SpecInfo.state(data, spec);
		boolean learned = state == SpecInfo.State.LEARNED;
		int slot = -1;
		for (int i = 0; i < slots.length; i++) {
			if (slots[i].equals(spec.id())) {
				slot = i;
				break;
			}
		}
		long left = learned ? SpecInfo.cooldownLeft(spec) : 0L;
		return new SealCard.Look(state != SpecInfo.State.LOCKED, state == SpecInfo.State.LOCKED, learned && spec.id().equals(selected), left,
				left > 0L ? SpecInfo.cooldown(data, spec) : 0, learned ? SpecInfo.mastery(data, spec) : 0, slot,
				learned && CodexFilters.isNew(spec), false);
	}

	/** The card tooltip: both names, grade and type, the Sumber line, scaled qi and cooldown, and the lock reason. */
	static List<Component> tooltip(CultivationData data, TechniqueSpec spec) {
		List<Component> lines = new ArrayList<>();
		lines.add(spec.displayName().copy().withColor(spec.dao() == null ? 0xFFE6B3 : spec.dao().color()));
		lines.add(Component.literal(SearchIndex.otherName(spec)).withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("kultivator.codex.tip.grade_type", SpecInfo.gradeName(spec), spec.type().displayName())
				.withColor(SpecInfo.ribbon(spec.grade()) & 0xFFFFFF));
		lines.add(SpecInfo.sourceLine(spec));
		Component qi = spec.qiPerSecond() > 0.0D ? Component.translatable("kultivator.codex.detail.qi_per_second", SpecInfo.decimal(spec.qiPerSecond(), 1))
				: Component.literal(SpecInfo.number(SpecInfo.qi(data, spec)));
		lines.add(Component.translatable("kultivator.codex.detail.costs", qi, SpecInfo.seconds(SpecInfo.cooldown(data, spec))).withColor(0x8FD0FF));
		// How the technique wants to be used: a charge, a channel or stages.
		if (spec.charge() != null) {
			lines.add(Component.translatable("kultivator.codex.detail.flow.charge", SpecInfo.seconds(spec.charge().ticks())).withColor(0xFFD58A));
		}
		if (spec.channel() != null) {
			lines.add(Component.translatable("kultivator.codex.detail.flow.channel", SpecInfo.seconds(spec.channel().ticks())).withColor(0xFFD58A));
		}
		if (spec.stages() != null) {
			lines.add(Component.translatable("kultivator.codex.detail.flow.stages", spec.stages().count(), SpecInfo.seconds(spec.stages().window()))
					.withColor(0xFFD58A));
		}
		SpecInfo.State state = SpecInfo.state(data, spec);
		Component reason = SpecInfo.lockReason(data, spec);
		if (state == SpecInfo.State.LEARNED) {
			lines.add(Component.translatable("kultivator.codex.tip.mastery", SpecInfo.masteryName(SpecInfo.mastery(data, spec))).withStyle(ChatFormatting.GOLD));
			if (reason != null) {
				lines.add(reason.copy().withStyle(ChatFormatting.RED));
			}
		} else if (state == SpecInfo.State.LEARNABLE) {
			lines.add(Component.translatable("kultivator.codex.tip.learnable").withStyle(ChatFormatting.GREEN));
		} else if (reason != null) {
			lines.add(reason.copy().withStyle(ChatFormatting.RED));
		}
		lines.add(Component.translatable("kultivator.codex.tip.click").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	// ================================================================== input

	/** Every filter back to "all" and the search emptied (the empty state's talisman). */
	private void kosongkanSaringan() {
		filters.reset();
		if (search != null) {
			search.setText("");
		}
		refresh();
	}

	/** A grid key: moves the selected card and shows it in the slip; true when it moved. */
	private boolean pindah(int key) {
		if (!grid.tombol(key)) {
			return false;
		}
		slip.setSpec(grid.selected());
		UiSound.hover();
		return true;
	}

	/** Enter on the grid: the selected card's slip (the detail view on narrow paper), the focus on its first talisman. */
	private void bukaTerpilih() {
		TechniqueSpec spec = grid.selected();
		if (spec == null && !pindah(InputConstants.KEY_HOME)) {
			return;
		}
		slip.setSpec(grid.selected());
		detail = !docked;
		fokusRing.set("slip.pilih");
	}

	private static boolean vertikal(int key) {
		return key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN || key == InputConstants.KEY_PAGEUP || key == InputConstants.KEY_PAGEDOWN
				|| key == InputConstants.KEY_HOME || key == InputConstants.KEY_END;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (!fokusRing.mauTombol() && vertikal(key)) {
			String focused = fokusRing.kunci();
			if (detail || (docked && focused != null && focused.startsWith("slip."))) {
				return slip.gulirTombol(key);
			}
			if (KISI.equals(focused)) {
				pindah(key);
				return true;
			}
			if (focused == null && (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) && !grid.specs().isEmpty()) {
				fokusRing.set(KISI);
				if (grid.selected() == null) {
					pindah(InputConstants.KEY_HOME);
				}
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		// The grid before the focus ring: its "kisi" zone covers the cards, a click picks the card under the mouse.
		if (!detail && !modal() && !grid.specs().isEmpty() && grid.contains(mx, my)) {
			fokusRing.set(KISI);
			TechniqueSpec spec = grid.at(mx, my);
			if (spec != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				UiSound.press();
				grid.select(spec);
				slip.setSpec(spec);
				detail = !docked;
			}
			return true;
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		return (detail || docked) && slip.contains(mx, my);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if ((detail || docked) && slip.mouseScrolled(mouseX, mouseY, scrollY)) {
			return true;
		}
		return !detail && grid.scrolled(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean mundur() {
		if (detail) {
			detail = false;
			// back on the grid, the cursor where it was
			fokusRing.set(KISI);
			return true;
		}
		String focused = fokusRing.kunci();
		if (docked && focused != null && focused.startsWith("slip.") && !grid.specs().isEmpty()) {
			// from the docked slip's talismans Esc goes back to the cards first
			fokusRing.set(KISI);
			return true;
		}
		return false;
	}
}
