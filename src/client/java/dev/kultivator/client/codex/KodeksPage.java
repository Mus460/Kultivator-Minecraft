package dev.kultivator.client.codex;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SealCard;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.TechniqueType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Kodeks tablet (DESIGN 22.12.2-22.12.4): row A the ink search, the "Dikuasai 37/168" counter and the sort talisman;
 * row B the Dao coins (all, universal, the eight trigrams) and the six grade coins; row C the Ranah and Jenis talismans,
 * the state chips and the Pamungkas coin; then the virtualized seal-card grid, grouped by grade when sorted by grade.
 * The bamboo detail slip docks on the right from 420 GUI px wide screens, else it opens over the grid on click.
 */
final class KodeksPage extends CodexPage {

	private static final int ROW = 13;
	private static final int SLIP_W = 150;

	private final CodexFilters filters;
	private final CodexGrid grid = new CodexGrid();
	private final DetailSlip slip = new DetailSlip();
	private InkSearchField search;
	private boolean docked;
	private boolean overlay;
	private int gridX;
	private int gridW;
	private int counterX;
	private int counterW;
	private long builtVersion = -1L;
	private @Nullable TechniqueSpec focus;
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

	KodeksPage(CodexScreen screen, CodexFilters filters) {
		super(screen);
		this.filters = filters;
	}

	@Override
	Component title() {
		return Component.translatable("kultivator.codex.tab.kodeks");
	}

	@Override
	Component shortName() {
		return Component.translatable("kultivator.codex.tab.kodeks.short");
	}

	@Override
	int icon() {
		return Glyphs.CODEX;
	}

	/** Opens the detail of {@code spec} and scrolls the grid to it. */
	void focus(@Nullable TechniqueSpec spec) {
		focus = spec;
		if (spec != null) {
			grid.select(spec);
			slip.setSpec(spec);
			overlay = !docked;
			grid.reveal(spec);
		}
	}

	@Override
	protected void build() {
		docked = screen.width >= 420 && w >= SLIP_W + 160;
		gridX = x;
		gridW = docked ? w - SLIP_W - 4 : w;
		int top = y;

		// Row A.
		int searchW = Math.max(60, Math.round(gridW * 0.42F));
		search = add(new InkSearchField(gridX, top, searchW, ROW - 2, filters.query, text -> {
			filters.query = text;
			refresh();
		}));
		int sortW = Math.min(70, Math.max(54, gridW / 4));
		CodexWidgets.Cycle sort = add(new CodexWidgets.Cycle(gridX + gridW - sortW, top, sortW, ROW - 2, Palette.SEAL, filters::sortLabel, step -> {
			for (int i = 0; i < (step > 0 ? 1 : CodexFilters.Sort.values().length - 1); i++) {
				filters.cycleSort();
			}
			refresh();
		}));
		sort.tooltip(() -> List.of(Component.translatable("kultivator.codex.sort"), filters.sortLabel()));
		counterX = gridX + searchW + 4;
		counterW = gridW - searchW - sortW - 8;

		// Row B: Dao coins, then grade coins.
		int coin = 11;
		int cx = gridX;
		int rowB = top + ROW;
		cx = daoCoin(cx, rowB, coin, -2, (g, px, py, s, c) -> {
			Brush.ring(g, px, py, s * 0.25F, s * 0.5F, c);
			Brush.dot(g, px, py, 0.8F, c);
		});
		cx = daoCoin(cx, rowB, coin, -1, (g, px, py, s, c) -> {
			Brush.ring(g, px, py, s * 0.25F, s * 0.45F, c);
			Brush.disc(g, px, py, s * 0.13F, c);
		});
		for (Dao dao : Glyphs.BAGUA) {
			cx = daoCoin(cx, rowB, coin, dao.ordinal(), (g, px, py, s, c) -> Glyphs.trigram(g, px, py, s, Math.max(0.8F, s * 0.15F), s * 0.3F,
					Glyphs.trigramOf(dao), c));
		}
		cx += 5;
		for (TechniqueGrade grade : TechniqueGrade.values()) {
			if (cx + coin > gridX + gridW) {
				break;
			}
			CodexWidgets.Coin gradeCoin = add(new CodexWidgets.Coin(cx, rowB, coin, SpecInfo.ribbon(grade), (g, px, py, s, c) ->
					Brush.disc(g, px, py, s * 0.35F, SpecInfo.ribbon(grade)), () -> filters.gradeOn(grade)));
			gradeCoin.onPress(() -> {
				filters.toggleGrade(grade);
				refresh();
			});
			gradeCoin.tooltip(() -> List.of(Component.translatable("kultivator.codex.filter.grade", grade.displayName()),
					Component.translatable("kultivator.codex.filter.grade.tip").withStyle(ChatFormatting.GRAY)));
			cx += coin + 1;
		}

		// Row C: Ranah, Jenis, state chips, Pamungkas.
		int rowC = top + ROW * 2;
		int realmW = Math.max(52, Math.min(80, gridW / 4));
		CodexWidgets.Cycle realm = add(new CodexWidgets.Cycle(gridX, rowC, realmW, ROW - 2, Palette.JADE, filters::realmLabel, step -> {
			filters.cycleRealm(step);
			refresh();
		}));
		realm.tooltip(() -> List.of(Component.translatable("kultivator.codex.filter.realm"), filters.realmLabel(),
				Component.translatable("kultivator.codex.filter.cycle").withStyle(ChatFormatting.GRAY)));
		int typeW = Math.max(48, Math.min(72, gridW / 5));
		CodexWidgets.Cycle type = add(new CodexWidgets.Cycle(gridX + realmW + 2, rowC, typeW, ROW - 2, Palette.GOLD_DARK, filters::typeLabel, step -> {
			filters.cycleType(step);
			refresh();
		}));
		type.tooltip(() -> List.of(Component.translatable("kultivator.codex.filter.type"), filters.typeLabel(),
				Component.translatable("kultivator.codex.filter.cycle").withStyle(ChatFormatting.GRAY)));
		int chipX = gridX + realmW + typeW + 5;
		int chipRoom = gridX + gridW - chipX - coin - 3;
		int chipW = Math.max(18, Math.min(40, (chipRoom - 3) / 4));
		for (int state = 0; state < 4; state++) {
			int index = state;
			CodexWidgets.Chip chip = add(new CodexWidgets.Chip(chipX + state * (chipW + 1), rowC, chipW, ROW - 2,
					() -> CodexFilters.stateLabel(index), () -> filters.state == index));
			chip.onPress(() -> {
				filters.state = index;
				filters.save();
				refresh();
			});
			chip.tooltip(() -> List.of(CodexFilters.stateLabel(index), Component.translatable("kultivator.codex.filter.state." + index + ".tip")
					.withStyle(ChatFormatting.GRAY)));
		}
		CodexWidgets.Coin ultimate = add(new CodexWidgets.Coin(gridX + gridW - coin, rowC, coin, Palette.SEAL, (g, px, py, s, c) ->
				Glyphs.typeMark(g, TechniqueType.PAMUNGKAS, px, py, s * 0.8F, c), () -> filters.ultimates));
		ultimate.onPress(() -> {
			filters.ultimates = !filters.ultimates;
			filters.save();
			refresh();
		});
		ultimate.tooltip(() -> List.of(Component.translatable("kultivator.codex.filter.ultimates"),
				Component.translatable("kultivator.codex.filter.ultimates.tip").withStyle(ChatFormatting.GRAY)));

		int gridTop = top + ROW * 3 + 2;
		grid.layout(gridX, gridTop, gridW, y + h - gridTop, 28, 40);
		if (docked) {
			slip.layout(x + w - SLIP_W, y, SLIP_W, h);
		} else {
			int slipW = Math.min(w, Math.max(SLIP_W, Math.round(w * 0.62F)));
			slip.layout(x + w - slipW, y, slipW, h);
		}
		refresh();
		if (focus != null) {
			focus(focus);
		}
	}

	private int daoCoin(int cx, int y, int size, int dao, CodexWidgets.Icon icon) {
		int tint = dao >= 0 ? Palette.opaque(Dao.values()[dao].color()) : Palette.JADE_LIGHT;
		CodexWidgets.Coin coin = add(new CodexWidgets.Coin(cx, y, size, tint, icon, () -> filters.dao == dao));
		coin.onPress(() -> {
			filters.dao = dao;
			filters.save();
			refresh();
		});
		coin.tooltip(() -> {
			int saved = filters.dao;
			filters.dao = dao;
			Component label = filters.daoLabel();
			filters.dao = saved;
			return List.of(Component.translatable("kultivator.codex.filter.dao", label));
		});
		return cx + size + 1;
	}

	private void refresh() {
		List<TechniqueSpec> list = filters.apply(SpecInfo.visible(), view);
		grid.setSpecs(list, filters.sort == CodexFilters.Sort.GRADE);
		builtVersion = ClientCultivation.version();
	}

	@Override
	void onData() {
		if (ClientCultivation.version() != builtVersion && (filters.state != 0 || filters.realm == 1 || filters.sort == CodexFilters.Sort.MASTERY)) {
			refresh();
		}
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CultivationData data = data();
		Component counter = Component.translatable("kultivator.codex.counter", SpecInfo.learnedCount(data), TechniqueRegistry.playerTotal());
		Brush.fitCentered(g, font, Brush.bold(counter), counterX + counterW / 2.0F, y + 1.5F, counterW, Palette.SEAL_DARK, 0.7F, 0.45F, false);
		String[] slots = TeknikSession.slots();
		String selected = data.selectedId();
		boolean overSlip = overlay && slip.contains(mouseX, mouseY) || docked && slip.contains(mouseX, mouseY);
		grid.render(g, font, overSlip ? -1 : mouseX, overSlip ? -1 : mouseY, seconds, spec -> look(data, spec, slots, selected));
		if (grid.specs().isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.codex.empty"), gridX + 6.0F, y + ROW * 3 + 12.0F, gridW - 12.0F, Palette.INK_SOFT,
					0.7F, false);
		}
		TechniqueSpec hovered = grid.hovered();
		if (hovered != null && !overSlip) {
			screen.showTooltip(tooltip(data, hovered));
		}
	}

	@Override
	protected void drawAbove(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (docked || overlay) {
			g.nextStratum();
			slip.render(g, mouseX, mouseY, seconds, screen::showTooltip);
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

	@Override
	boolean mouseClicked(double mouseX, double mouseY, int button) {
		if ((docked || overlay) && slip.contains(mouseX, mouseY)) {
			if (button == InputConstants.MOUSE_BUTTON_LEFT) {
				slip.mouseClicked(mouseX, mouseY, button);
			}
			return true;
		}
		if (overlay && !slip.contains(mouseX, mouseY) && grid.contains(mouseX, mouseY) && grid.at(mouseX, mouseY) == null) {
			overlay = false;
			return true;
		}
		if (super.mouseClicked(mouseX, mouseY, button)) {
			if (!(search.contains(mouseX, mouseY))) {
				search.setFocused(false);
			}
			return true;
		}
		TechniqueSpec spec = grid.at(mouseX, mouseY);
		if (spec != null && button == InputConstants.MOUSE_BUTTON_LEFT) {
			search.setFocused(false);
			dev.kultivator.client.screen.ui.UiSound.press();
			grid.select(spec);
			slip.setSpec(spec);
			overlay = !docked;
			return true;
		}
		search.setFocused(false);
		return false;
	}

	@Override
	boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (slip.mouseReleased(mouseX, mouseY)) {
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if ((docked || overlay) && slip.mouseScrolled(mouseX, mouseY, amount)) {
			return true;
		}
		return grid.scrolled(mouseX, mouseY, amount);
	}

	@Override
	boolean keyPressed(KeyEvent event) {
		return search != null && search.keyPressed(event);
	}

	@Override
	boolean charTyped(CharacterEvent event) {
		return search != null && search.charTyped(event);
	}

	@Override
	boolean escape() {
		if (slip.escape()) {
			return true;
		}
		if (search != null && search.focused()) {
			search.setFocused(false);
			return true;
		}
		if (overlay) {
			overlay = false;
			return true;
		}
		return false;
	}
}
