package dev.kultivator.client.disciple;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SealCard;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import dev.kultivator.network.Actions;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Techniques: the master's arts as seal cards (left, scrolling) and the picked card read out on the right with its
 * Dao, cost, whether the disciple can learn it (or why not) and the "Teach" talisman. Teaching costs the master 20% of
 * their max qi; the server checks everything again.
 */
final class TechniqueTab extends DiscipleTab {

	private static final int GAP = 4;
	private static final int HEADER = 13;
	private static final int BUTTON_H = 18;

	private final List<TeachCard> cards = new ArrayList<>();
	private final List<Integer> baseY = new ArrayList<>();
	private final Eased scrollShown = new Eased(14.0F);
	private List<Technique> builtFor = List.of();
	private @Nullable Technique selected;
	private float scroll;
	private float contentHeight;
	private int gridX;
	private int gridY;
	private int gridW;
	private int gridH;
	private int detailX;
	private int detailW;

	TechniqueTab(DiscipleScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.discipleui.tab.techniques");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.discipleui.tab.techniques.short");
	}

	private List<Technique> listed() {
		List<Technique> list = new ArrayList<>();
		for (DiscipleView.Entry entry : view().teachable) {
			list.add(entry.technique());
		}
		return list;
	}

	@Override
	protected void build() {
		cards.clear();
		baseY.clear();
		builtFor = listed();
		detailW = Math.max(92, Math.min(170, Math.round(w * 0.4F)));
		detailX = x + w - detailW;
		gridX = x;
		gridY = y + HEADER;
		gridW = w - detailW - 6;
		gridH = h - HEADER;
		int cols = Math.max(2, (gridW + GAP) / (40 + GAP));
		int size = Math.max(32, Math.min(52, (gridW - 4 - (cols - 1) * GAP) / cols));
		int used = cols * size + (cols - 1) * GAP;
		int left = gridX + Math.max(0, (gridW - 4 - used) / 2);
		int rows = (builtFor.size() + cols - 1) / cols;
		contentHeight = rows * (size + GAP) - GAP + 6;
		for (int i = 0; i < builtFor.size(); i++) {
			Technique technique = builtFor.get(i);
			int cx = left + (i % cols) * (size + GAP);
			int cy = gridY + 3 + (i / cols) * (size + GAP);
			TeachCard card = new TeachCard(cx, cy, size, technique, () -> view().entry(technique), () -> technique == selected);
			card.onPress(() -> selected = technique);
			card.tooltip(() -> cardTip(technique));
			cards.add(card);
			baseY.add(cy);
			add(card);
		}
		if (selected == null || !builtFor.contains(selected)) {
			selected = firstTeachable();
		}

		TalismanButton teach = new TalismanButton(detailX + 5, y + h - BUTTON_H - 4, detailW - 10, BUTTON_H,
				() -> Component.translatable("kultivator.discipleui.tech.teach"));
		teach.enabledWhen(this::canTeach);
		teach.urgentWhen(() -> canTeach() && masterQi() >= cost());
		teach.onPress(() -> {
			if (selected != null) {
				screen.send(Actions.DISCIPLE_TEACH, selected.id());
			}
		});
		teach.tooltip(this::teachTip);
		add(teach);
	}

	@Override
	public boolean needsRebuild() {
		return !listed().equals(builtFor);
	}

	private @Nullable Technique firstTeachable() {
		for (DiscipleView.Entry entry : view().teachable) {
			if (entry.ok()) {
				return entry.technique();
			}
		}
		return builtFor.isEmpty() ? null : builtFor.get(0);
	}

	private boolean canTeach() {
		if (selected == null) {
			return false;
		}
		DiscipleView.Entry entry = view().entry(selected);
		return entry != null && entry.ok() && !entry.learned();
	}

	/** Qi the master pays: the server's figure when it sent one, else 20% of the master's max qi. */
	private double cost() {
		double cost = view().teachCost;
		return cost >= 0.0D ? cost : Qi.max(ClientCultivation.data()) * 0.2D;
	}

	private static double masterQi() {
		return ClientCultivation.data().qi();
	}

	private List<Component> cardTip(Technique technique) {
		List<Component> lines = new ArrayList<>();
		lines.add(technique.displayName());
		lines.add(statusLine(technique));
		return lines;
	}

	private List<Component> teachTip() {
		List<Component> lines = new ArrayList<>();
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
	private Component statusLine(Technique technique) {
		DiscipleView.Entry entry = view().entry(technique);
		if (entry == null) {
			return Component.translatable("kultivator.discipleui.reason.unknown").withStyle(ChatFormatting.RED);
		}
		if (entry.learned()) {
			return Component.translatable("kultivator.discipleui.tech.learned").withStyle(ChatFormatting.GOLD);
		}
		if (entry.ok()) {
			return Component.translatable("kultivator.discipleui.tech.ok").withStyle(ChatFormatting.DARK_GREEN);
		}
		// Name the realm or Dao it is waiting for; otherwise the server's reason.
		DiscipleView view = view();
		Component reason;
		if (!DiscipleView.forbidden(technique) && !view.rank.realm().atLeast(technique.minRealm())) {
			reason = Component.translatable("kultivator.discipleui.reason.realm", technique.minRealm().displayName());
		} else if (!DiscipleView.forbidden(technique) && technique.dao() != null && view.dao != null && technique.dao() != view.dao) {
			reason = Component.translatable("kultivator.discipleui.reason.dao", technique.dao().displayName());
		} else {
			reason = entry.reason() != null ? entry.reason() : Component.translatable("kultivator.discipleui.tech.not_ok");
		}
		return reason.copy().withStyle(ChatFormatting.RED);
	}

	@Override
	protected boolean visible(Widget widget) {
		if (widget instanceof TeachCard) {
			return widget.y + widget.h > gridY && widget.y < gridY + gridH;
		}
		return true;
	}

	@Override
	protected int[] clipFor(Widget widget) {
		return widget instanceof TeachCard ? new int[]{gridX, gridY, gridX + gridW, gridY + gridH} : null;
	}

	@Override
	protected boolean hoverable(double mouseX, double mouseY) {
		if (mouseX < detailX - 3) {
			return inside(mouseX, mouseY) && mouseY >= gridY && mouseY < gridY + gridH;
		}
		return inside(mouseX, mouseY);
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		DiscipleView view = view();
		float maxScroll = Math.max(0.0F, contentHeight - gridH);
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		int offset = Math.round(scrollShown.to(scroll, seconds));
		for (int i = 0; i < cards.size(); i++) {
			cards.get(i).y = baseY.get(i) - offset;
		}
		Component count = Component.translatable("kultivator.discipleui.tech.count", view.learned.size(), builtFor.size());
		heading(g, Component.translatable("kultivator.discipleui.tech.heading"), x + 2.0F, y, gridW * 0.6F, 0.8F);
		float countScale = Brush.fitScale(font, count, gridW * 0.38F, 0.7F, 0.45F);
		float countW = Math.min(gridW * 0.38F, font.width(count) * countScale);
		Brush.fit(g, font, count, x + gridW - countW, y + 1.0F, gridW * 0.38F, Palette.INK_SOFT, 0.7F, 0.45F, false);

		drawDetail(g);
		if (builtFor.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.discipleui.tech.none"), gridX + 4.0F, gridY + 8.0F, gridW - 8.0F, Palette.INK_SOFT,
					0.75F, false);
		}
	}

	@Override
	protected void drawAbove(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		float maxScroll = Math.max(0.0F, contentHeight - gridH);
		if (maxScroll > 0.0F) {
			float track = gridH - 6.0F;
			float bead = gridY + 3.0F + track * (scrollShown.get() / maxScroll);
			int rail = gridX + gridW - 1;
			g.fill(rail, gridY, rail + 1, gridY + gridH, Palette.CORD);
			Brush.disc(g, rail + 0.5F, bead, 2.2F, Palette.JADE);
			Brush.disc(g, rail, bead - 0.6F, 0.8F, Palette.JADE_PALE);
		}
	}

	/** The picked art: name, Dao, cost and cooldown, status, description (the talisman is a control below). */
	private void drawDetail(GuiGraphicsExtractor g) {
		int x0 = detailX;
		int x1 = x + w;
		int y0 = y;
		int y1 = y + h;
		panel(g, x0, y0, x1, y1);
		float tx = x0 + 5.0F;
		float room = detailW - 10.0F;
		float ty = y0 + 5.0F;
		if (selected == null) {
			Brush.wrap(g, font, Component.translatable("kultivator.discipleui.tech.select"), tx, ty, room, Palette.INK_SOFT, 0.7F, false);
			return;
		}
		Technique technique = selected;
		Dao dao = technique.dao();
		int tone = dao == null ? Palette.JADE_DARK : Palette.onPaper(dao.color());
		Brush.fit(g, font, Brush.bold(technique.displayName()), tx, ty, room, tone, 0.9F, 0.55F, false);
		ty += 10.0F;
		Component school = dao == null ? Component.translatable("kultivator.discipleui.tech.universal") : dao.displayName();
		Brush.fit(g, font, school, tx, ty, room, Palette.INK_SOFT, 0.65F, 0.45F, false);
		ty += 7.0F;
		// The disciple's own qi cost and cooldown for the art when the server sent them.
		DiscipleView.Entry entry = view().entry(technique);
		double qiCost = entry != null && entry.qiCost() >= 0.0D ? entry.qiCost() : technique.qiCost();
		int cooldown = entry != null && entry.cooldown() >= 0 ? entry.cooldown() : technique.cooldownTicks();
		Component cost = Component.translatable("kultivator.discipleui.tech.cost_cd", Msg.number(Math.ceil(qiCost)), SealCard.seconds(cooldown));
		Brush.fit(g, font, cost, tx, ty, room, Palette.onPaper(Palette.QI), 0.65F, 0.45F, false);
		ty += 8.0F;
		Brush.rect(g, tx, ty, x1 - 5.0F, ty + 0.6F, Palette.argb(0x90, Palette.GOLD));
		ty += 3.0F;
		Component status = statusLine(technique);
		ty += Brush.wrap(g, font, status, tx, ty, room, Palette.INK, 0.65F, false) + 2.0F;
		float descBottom = y1 - BUTTON_H - 8.0F;
		if (descBottom - ty > 8.0F) {
			g.enableScissor(x0 + 1, Math.round(ty), x1 - 1, Math.round(descBottom));
			Brush.wrap(g, font, technique.description(), tx, ty, room, Palette.INK_SOFT, 0.6F, false);
			g.disableScissor();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH) {
			scroll -= (float) amount * 14.0F;
			return true;
		}
		return false;
	}
}
