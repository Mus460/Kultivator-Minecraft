package dev.kultivator.client.hall;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.network.Actions;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Calon tablet (DESIGN 20.3.3): one scroll card per candidate with its talismans "Uji (1 Pecahan Kecubung)",
 * "Terima" and "Undang pergi", the next acceptance day, the Hanya Monster coin and, on a war day, "Mulai Perang"
 * (module B's Perang Seratus Dinasti). The cards scroll with the wheel.
 */
public class CalonPage extends HallPage {

	private static final int TOP = 15;
	private static final int GAP = 4;
	private static final int CARD_H = 96;
	private static final long CONFIRM_MILLIS = 3000L;

	private final Map<Widget, Integer> baseY = new HashMap<>();
	private final Eased scrollShown = new Eased(14.0F);
	private List<Integer> builtFor = List.of();
	private boolean builtReadonly;
	private boolean builtWar;
	private float scroll;
	private float contentH;
	private int listY;
	private int listH;
	private int dismissArmed = -1;
	private long dismissUntil;

	public CalonPage(HallScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.page.calon");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine6.page.calon.short");
	}

	@Override
	public int icon() {
		return Glyphs.STATUS;
	}

	private boolean readonly() {
		return info().getBooleanOr("readonly", false);
	}

	private List<Integer> ids() {
		List<Integer> ids = new ArrayList<>();
		for (Tag tag : info().getListOrEmpty("candidates")) {
			if (tag instanceof CompoundTag entry) {
				ids.add(entry.getIntOr("id", -1));
			}
		}
		return ids;
	}

	private @Nullable CompoundTag entry(int id) {
		for (Tag tag : info().getListOrEmpty("candidates")) {
			if (tag instanceof CompoundTag entry && entry.getIntOr("id", -1) == id) {
				return entry;
			}
		}
		return null;
	}

	@Override
	protected void build() {
		baseY.clear();
		builtFor = ids();
		builtReadonly = readonly();
		listY = y + TOP;
		listH = h - TOP;
		if (!builtReadonly) {
			JadeCoin monster = new JadeCoin(x + w - 104, y, 102, 11, () -> Component.translatable("kultivator.hall.ui.monster"),
					() -> info().getBooleanOr("monster", false));
			monster.onPress(() -> HallClient.send(Halls.OP_MONSTER, ""));
			monster.tooltip(() -> List.of(Component.translatable("kultivator.hall.ui.monster"), Component.translatable("kultivator.hall.ui.monster.tip"),
					Component.translatable("kultivator.hall.ui.monster.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
			add(monster);
		}
		int cols = w >= 250 ? 2 : 1;
		int cardW = (w - 4 - (cols - 1) * GAP) / cols;
		for (int i = 0; i < builtFor.size(); i++) {
			int id = builtFor.get(i);
			int cx = x + (i % cols) * (cardW + GAP);
			int cy = listY + 2 + (i / cols) * (CARD_H + GAP);
			CandidateCard card = new CandidateCard(cx, cy, cardW, CARD_H, () -> entry(id));
			track(add(card), cy);
			if (builtReadonly) {
				continue;
			}
			int by = cy + CARD_H - CandidateCard.BUTTON_ROW;
			int third = (cardW - 8) / 3;
			TalismanButton test = new TalismanButton(cx + 3, by, third, 11, () -> Component.translatable("kultivator.hall.ui.test"));
			test.enabledWhen(() -> {
				CompoundTag e = entry(id);
				return e != null && !e.getBooleanOr("tested", false) && !e.getBooleanOr("testing", false);
			});
			test.onPress(() -> HallClient.send(Halls.OP_TEST, String.valueOf(id)));
			test.tooltip(() -> List.of(Component.translatable("kultivator.hall.ui.test.full"), Component.translatable("kultivator.hall.ui.test.tip"),
					Component.translatable("kultivator.hall.ui.test.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
			track(add(test), by);
			TalismanButton accept = new TalismanButton(cx + 4 + third, by, third, 11, () -> Component.translatable("kultivator.hall.ui.accept"));
			accept.enabledWhen(() -> {
				CompoundTag e = entry(id);
				return e != null && !e.getBooleanOr("testing", false);
			});
			accept.urgentWhen(() -> {
				CompoundTag e = entry(id);
				return e != null && (e.getIntOr("verdict", -1) == 2 || e.getBooleanOr("token", false) || e.getBooleanOr("champion", false));
			});
			accept.onPress(() -> HallClient.send(Halls.OP_ACCEPT, String.valueOf(id)));
			accept.tooltip(() -> acceptTip(id));
			track(add(accept), by);
			TalismanButton dismiss = new TalismanButton(cx + 5 + third * 2, by, cardW - 8 - third * 2, 11,
					() -> Component.translatable(dismissArmed == id && Util.getMillis() < dismissUntil ? "kultivator.hall.ui.dismiss_sure"
							: "kultivator.hall.ui.dismiss"));
			dismiss.urgentWhen(() -> dismissArmed == id && Util.getMillis() < dismissUntil);
			dismiss.onPress(() -> {
				if (dismissArmed == id && Util.getMillis() < dismissUntil) {
					dismissArmed = -1;
					HallClient.send(Halls.OP_DISMISS, String.valueOf(id));
				} else {
					dismissArmed = id;
					dismissUntil = Util.getMillis() + CONFIRM_MILLIS;
				}
			});
			dismiss.tooltip(() -> List.of(Component.translatable("kultivator.hall.ui.dismiss"), Component.translatable("kultivator.hall.ui.dismiss.tip")));
			track(add(dismiss), by);
		}
		int rows = (builtFor.size() + cols - 1) / cols;
		contentH = rows * (CARD_H + GAP) + 4;
		builtWar = !builtReadonly && info().getBooleanOr("war_today", false);
		if (builtWar) {
			// "Mulai Perang" (DESIGN 20.3.13): module B runs the Hundred Dynasties War; its youths wait as data.
			TalismanButton war = new TalismanButton(x + w - 210, y, 102, 11, () -> Component.translatable("kultivator.hall.ui.war_start"));
			war.enabledWhen(this::warWaiting);
			war.urgentWhen(this::warWaiting);
			war.onPress(() -> send(Actions.SECT_EVENT, 0, "war_start"));
			war.tooltip(() -> List.of(Component.translatable("kultivator.hall.ui.war_start"), Component.translatable("kultivator.hall.ui.war_today.tip"),
					Component.translatable("kultivator.hall.ui.war.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
			add(war);
		}
	}

	/** True while today's youths wait for the war to start (module B's part of the details, when it sent one). */
	private boolean warWaiting() {
		CompoundTag war = info().getCompoundOrEmpty("sectevent").getCompoundOrEmpty("war");
		return war.isEmpty() || war.getBooleanOr("today", true) && war.getIntOr("state", 0) == 0;
	}

	private void track(Widget widget, int y) {
		baseY.put(widget, y);
	}

	/** What accepting this candidate means: the rank it joins at, and the servant fee. */
	private List<Component> acceptTip(int id) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.hall.ui.accept"));
		CompoundTag e = entry(id);
		if (e == null) {
			return lines;
		}
		if (e.getBooleanOr("token", false) || e.getBooleanOr("champion", false)) {
			lines.add(Component.translatable("kultivator.hall.ui.accept.token").withStyle(ChatFormatting.GOLD));
		} else if (!e.getBooleanOr("tested", false)) {
			lines.add(Component.translatable("kultivator.hall.ui.accept.blind").withStyle(ChatFormatting.GRAY));
		} else {
			int verdict = e.getIntOr("verdict", 0);
			lines.add(Component.translatable("kultivator.hall.ui.accept." + verdict, 100).withStyle(verdict == 0 ? ChatFormatting.RED
					: verdict == 1 ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
		}
		lines.add(Component.translatable("kultivator.hall.ui.accept.cap", info().getIntOr("alive", 0), info().getIntOr("cap", 5))
				.withStyle(ChatFormatting.DARK_AQUA));
		if (e.getBooleanOr("tested", false) && !e.getBooleanOr("token", false) && !e.getBooleanOr("champion", false)) {
			lines.add(Component.translatable("kultivator.hall.ui.verdict.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			if (e.getIntOr("verdict", 0) == 0) {
				lines.add(Component.translatable("kultivator.hall.ui.servant.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
		}
		if (e.getBooleanOr("gated", false)) {
			lines.addAll(gatedTip());
		}
		return lines;
	}

	/** The verdict seal of a tested candidate: what it means and where the test comes from (empty before the test). */
	private static List<Component> sealTip(@Nullable CompoundTag e) {
		if (e == null || !e.getBooleanOr("tested", false) || e.getBooleanOr("token", false) || e.getBooleanOr("champion", false)) {
			return List.of();
		}
		int verdict = e.getIntOr("verdict", 0);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.hall.ui.accept." + verdict, 100).withStyle(verdict == 0 ? ChatFormatting.RED
				: verdict == 1 ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
		lines.add(Component.translatable("kultivator.hall.ui.test.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		lines.add(Component.translatable("kultivator.hall.ui.verdict.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		if (verdict == 0) {
			lines.add(Component.translatable("kultivator.hall.ui.servant.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
		return lines;
	}

	/** The Return of the Mount Hua Sect gate note and its source. */
	private static List<Component> gatedTip() {
		return List.of(Component.translatable("kultivator.hall.ui.gated").withStyle(ChatFormatting.GRAY),
				Component.translatable("kultivator.hall.ui.gated.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	@Override
	public boolean needsRebuild() {
		return !ids().equals(builtFor) || readonly() != builtReadonly || (!readonly() && info().getBooleanOr("war_today", false)) != builtWar;
	}

	@Override
	protected boolean visible(Widget widget) {
		if (baseY.containsKey(widget)) {
			return widget.y + widget.h > listY && widget.y < listY + listH;
		}
		return true;
	}

	@Override
	protected int[] clipFor(Widget widget) {
		return baseY.containsKey(widget) ? new int[]{x, listY, x + w, listY + listH} : null;
	}

	@Override
	protected boolean hoverable(double mouseX, double mouseY) {
		return inside(mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseY >= listY && mouseY < listY + listH || mouseY < listY) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		return false;
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag info = info();
		float maxScroll = Math.max(0.0F, contentH - listH);
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		int offset = Math.round(scrollShown.to(scroll, seconds));
		for (Map.Entry<Widget, Integer> entry : baseY.entrySet()) {
			entry.getKey().y = entry.getValue() - offset;
		}
		long day = info.getLongOr("day", 0L);
		Component next = Component.translatable(info.getBooleanOr("war_next", false) ? "kultivator.hall.ui.next_war" : "kultivator.hall.ui.next_arrival",
				info.getLongOr("next_arrival", day));
		float topW = builtReadonly ? w - 4.0F : w - 110.0F - (builtWar ? 106.0F : 0.0F);
		if (info.getBooleanOr("war_today", false)) {
			// Module B's Hundred Dynasties War replaces today's arrival; it is started on the Arena tablet.
			Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.hall.ui.war_today")), x + 2.0F, y + 1.0F, topW, Palette.SEAL_DARK, 0.7F,
					0.45F, false);
			if (inside(mouseX, mouseY) && mouseY < listY - 2 && mouseX < x + 2.0F + topW) {
				screen.showTooltip(List.of(Component.translatable("kultivator.hall.ui.war_today"), Component.translatable("kultivator.hall.ui.war_today.tip"),
						Component.translatable("kultivator.hall.ui.war.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
			}
		} else {
			Brush.fit(g, font, next, x + 2.0F, y + 1.0F, topW, Palette.INK, 0.7F, 0.45F, false);
		}
		Brush.rect(g, x, listY - 2.0F, x + w, listY - 1.4F, Palette.argb(0x90, Palette.GOLD));
		if (builtFor.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(info.getBooleanOr("dormant", false) ? "kultivator.hall.ui.dormant_candidates"
					: "kultivator.hall.ui.no_candidates", info.getLongOr("next_arrival", day)), x + 6.0F, listY + 8.0F, w - 12.0F, Palette.INK_SOFT, 0.75F,
					false);
		}
	}

	@Override
	protected void drawAbove(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (inside(mouseX, mouseY) && mouseY >= listY && mouseY < listY + listH) {
			// The verdict seal and the gate note explain themselves with their sources; a talisman's own tooltip (shown first) still wins.
			for (Widget widget : baseY.keySet()) {
				if (widget instanceof CandidateCard card && widget.contains(mouseX, mouseY)) {
					List<Component> tip = card.overSeal(mouseX, mouseY) ? sealTip(card.entry()) : List.of();
					if (tip.isEmpty() && card.gated()) {
						tip = gatedTip();
					}
					if (!tip.isEmpty()) {
						screen.showTooltip(tip);
					}
					break;
				}
			}
		}
		float maxScroll = Math.max(0.0F, contentH - listH);
		if (maxScroll > 0.0F) {
			float bead = listY + 3.0F + (listH - 6.0F) * (scrollShown.get() / maxScroll);
			int rail = x + w - 1;
			g.fill(rail, listY, rail + 1, listY + listH, Palette.CORD);
			Brush.disc(g, rail + 0.5F, bead, 2.2F, Palette.JADE);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (inside(mouseX, mouseY) && mouseY >= listY) {
			scroll -= (float) amount * 18.0F;
			return true;
		}
		return false;
	}
}
