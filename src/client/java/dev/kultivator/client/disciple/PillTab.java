package dev.kultivator.client.disciple;

import dev.kultivator.alchemy.PillItem;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SealCard;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.cultivation.VanillaPills;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.item.MarrowCleansingPillItem;
import dev.kultivator.network.Actions;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pills &amp; Breakthrough. Left: the item in the master's main hand with the "Give the pill in hand" talisman, then
 * the next rank, the kind of breakthrough and the "Breakthrough" talisman (or why it cannot start yet). Right: the eight
 * Daos as trigram tiles (chosen once from Golden Core: click, then click again within 3 s to swear the disciple to it)
 * and the "Release disciple" talisman, which also asks for a second click.
 */
final class PillTab extends DiscipleTab {

	private static final int BUTTON_H = 18;
	private static final long CONFIRM_MILLIS = 3000L;
	private static final long RELEASE_MILLIS = 4000L;

	private int columnW;
	private int rightX;
	private int slotX;
	private int slotY;
	private int btTextY;
	private int btButtonY;
	private int tilesBottom;
	private int pendingDao = -1;
	private long pendingUntil;
	private long releaseArmedUntil;

	PillTab(DiscipleScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.discipleui.tab.pills");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.discipleui.tab.pills.short");
	}

	@Override
	protected void build() {
		columnW = (w - 8) / 2;
		rightX = x + columnW + 8;
		pendingDao = -1;
		releaseArmedUntil = 0L;

		// ---- left: pill, then breakthrough
		slotX = x + 2;
		slotY = y + 14;
		TalismanButton feed = new TalismanButton(x + 2, slotY + 22, columnW - 4, BUTTON_H, () -> Component.translatable("kultivator.discipleui.pill.give"));
		feed.enabledWhen(() -> !held().isEmpty());
		feed.urgentWhen(() -> isPill(held()));
		feed.onPress(() -> screen.send(Actions.DISCIPLE_FEED, ""));
		feed.tooltip(this::feedTip);
		add(feed);

		btTextY = slotY + 22 + BUTTON_H + 20;
		btButtonY = Math.min(y + h - BUTTON_H - 22, btTextY + 30);
		TalismanButton breakthrough = new TalismanButton(x + 2, btButtonY, columnW - 4, BUTTON_H,
				() -> Component.translatable("kultivator.discipleui.bt.button"));
		breakthrough.enabledWhen(() -> view().canBreakthrough);
		breakthrough.urgentWhen(() -> view().canBreakthrough);
		breakthrough.onPress(() -> {
			// The mini tribulation plays around the disciple in the world: close the scroll so it can be watched.
			screen.send(Actions.DISCIPLE_BREAKTHROUGH, "");
			screen.onClose();
		});
		breakthrough.tooltip(this::breakthroughTip);
		add(breakthrough);

		// ---- right: the eight Dao tiles, then release
		int size = Math.max(16, Math.min(26, (columnW - 3 * 4) / 4));
		int used = 4 * size + 3 * 4;
		int tileX = rightX + Math.max(0, (columnW - used) / 2);
		int tileY = y + 15;
		for (int i = 0; i < Glyphs.BAGUA.length; i++) {
			int index = i;
			Dao dao = Glyphs.BAGUA[i];
			TrigramTile tile = new TrigramTile(tileX + (i % 4) * (size + 4), tileY + (i / 4) * (size + 4), size, dao,
					() -> view().dao == dao, () -> pendingDao == index && Util.getMillis() <= pendingUntil);
			tile.enabledWhen(() -> view().canChooseDao && view().dao == null);
			tile.onPress(() -> chooseDao(index));
			tile.tooltip(() -> daoTip(dao));
			add(tile);
		}
		tilesBottom = tileY + 2 * size + 4;

		TalismanButton release = new TalismanButton(rightX + 2, y + h - BUTTON_H, columnW - 4, BUTTON_H,
				() -> Component.translatable(releaseArmed() ? "kultivator.discipleui.release.confirm" : "kultivator.discipleui.release.button"));
		release.urgentWhen(this::releaseArmed);
		release.onPress(() -> {
			if (releaseArmed()) {
				releaseArmedUntil = 0L;
				screen.send(Actions.DISCIPLE_RELEASE, "confirm");
				screen.onClose();
			} else {
				releaseArmedUntil = Util.getMillis() + RELEASE_MILLIS;
				UiSound.ask();
			}
		});
		release.tooltip(() -> List.of(Component.translatable("kultivator.discipleui.release.button"),
				Component.translatable("kultivator.discipleui.release.tip"),
				Component.translatable("kultivator.discipleui.release.twice").withStyle(ChatFormatting.RED)));
		add(release);
	}

	private boolean releaseArmed() {
		return Util.getMillis() <= releaseArmedUntil;
	}

	private void chooseDao(int index) {
		long now = Util.getMillis();
		if (pendingDao == index && now <= pendingUntil) {
			pendingDao = -1;
			screen.send(Actions.DISCIPLE_CHOOSE_DAO, Glyphs.BAGUA[index].id());
		} else {
			pendingDao = index;
			pendingUntil = now + CONFIRM_MILLIS;
			UiSound.ask();
		}
	}

	private static ItemStack held() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
	}

	/** Vanilla pills, the mod's refined pills and the Marrow Cleansing Pill. */
	private static boolean isPill(ItemStack stack) {
		return !stack.isEmpty() && (VanillaPills.Pill.of(stack) != null || stack.getItem() instanceof PillItem
				|| stack.getItem() instanceof MarrowCleansingPillItem);
	}

	// ================================================================== tooltips

	private List<Component> feedTip() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.discipleui.pill.give"));
		lines.add(Component.translatable("kultivator.discipleui.pill.give.tip"));
		ItemStack stack = held();
		DiscipleView view = view();
		boolean sameItem = !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(view.pillItem);
		if (stack.isEmpty()) {
			lines.add(Component.translatable("kultivator.discipleui.pill.empty_hand").withStyle(ChatFormatting.RED));
		} else if (sameItem && view.pillReason != null) {
			// The server's verdict on this very item (cooldown, wrong kind of pill...).
			lines.add(view.pillReason.copy().withStyle(ChatFormatting.RED));
		} else if (!isPill(stack)) {
			lines.add(Component.translatable("kultivator.discipleui.pill.not_pill").withStyle(ChatFormatting.GOLD));
		}
		if (sameItem && view.pillCooldown > 0) {
			lines.add(Component.translatable("kultivator.discipleui.pill.cooldown", SealCard.seconds(view.pillCooldown)).withStyle(ChatFormatting.GRAY));
		}
		return lines;
	}

	private List<Component> breakthroughTip() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.discipleui.bt.button"));
		lines.addAll(breakthroughLines());
		Component state = breakthroughState();
		if (state != null) {
			lines.add(state);
		}
		return lines;
	}

	/** Next rank, the kind of breakthrough and its chance (when known). */
	private List<Component> breakthroughLines() {
		DiscipleView view = view();
		Rank rank = view.rank;
		List<Component> lines = new ArrayList<>();
		if (rank.isMax() || "none".equals(view.breakthroughKind)) {
			lines.add(Component.translatable("kultivator.discipleui.bt.summit").withStyle(ChatFormatting.GOLD));
			return lines;
		}
		Rank next = view.nextRank;
		lines.add(Component.translatable("kultivator.discipleui.bt.next", next.displayName().withColor(next.realm().color())));
		String kind = view.breakthroughKind;
		if (!"awakening".equals(kind) && !"chance".equals(kind) && !"tribulation".equals(kind)) {
			kind = rank.realm().isMortal() ? "awakening" : next.realm() == Realm.FOUNDATION ? "chance" : "tribulation";
		}
		lines.add(Component.translatable("kultivator.discipleui.bt." + kind).withStyle(ChatFormatting.GRAY));
		if ("chance".equals(kind) && view.breakthroughChance >= 0.0D) {
			lines.add(Component.translatable("kultivator.discipleui.bt.chance_value",
					String.format(Locale.ROOT, "%.0f", Math.min(1.0D, view.breakthroughChance) * 100.0D)).withStyle(ChatFormatting.AQUA));
		}
		return lines;
	}

	/** Ready, or why the breakthrough cannot start now. */
	private @Nullable Component breakthroughState() {
		DiscipleView view = view();
		if (view.rank.isMax()) {
			return null;
		}
		if (view.canBreakthrough) {
			return Component.translatable("kultivator.discipleui.bt.ready").withStyle(ChatFormatting.GOLD);
		}
		if (view.breakingThrough) {
			return Component.translatable("kultivator.discipleui.bt.running").withStyle(ChatFormatting.GOLD);
		}
		if (view.deviating) {
			return Component.translatable("kultivator.discipleui.bt.deviation").withStyle(ChatFormatting.RED);
		}
		// The detailed line (with the numbers) before the server's short reason "bar not full yet" / "not at the bottleneck".
		if (!view.barFull()) {
			return Component.translatable("kultivator.discipleui.bt.not_ready", Msg.number(view.xp), Msg.number(view.xpRequired))
					.withStyle(ChatFormatting.RED);
		}
		if (view.breakthroughReason != null) {
			return view.breakthroughReason.copy().withStyle(ChatFormatting.RED);
		}
		return Component.translatable("kultivator.discipleui.reason.unknown").withStyle(ChatFormatting.RED);
	}

	private List<Component> daoTip(Dao dao) {
		DiscipleView view = view();
		List<Component> lines = new ArrayList<>();
		lines.add(dao.displayName().copy().withColor(dao.color()));
		if (view.rootKnown && dao.hasAffinity(view.rootMask)) {
			lines.add(Component.translatable("kultivator.discipleui.dao.affinity").withStyle(ChatFormatting.GREEN));
		}
		if (view.dao == dao) {
			lines.add(Component.translatable("kultivator.discipleui.dao.chosen", dao.displayName()).withStyle(ChatFormatting.GOLD));
		} else if (view.dao != null) {
			lines.add(Component.translatable("kultivator.discipleui.dao.taken").withStyle(ChatFormatting.GRAY));
		} else if (view.canChooseDao) {
			lines.add(Component.translatable("kultivator.discipleui.dao.click").withStyle(ChatFormatting.GRAY));
		} else {
			lines.add(daoLocked().copy().withStyle(ChatFormatting.RED));
		}
		return lines;
	}

	private Component daoLocked() {
		DiscipleView view = view();
		return view.daoReason != null ? view.daoReason : Component.translatable("kultivator.discipleui.dao.locked");
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		DiscipleView view = view();
		float room = columnW - 4.0F;

		// ---- the pill in hand
		heading(g, Component.translatable("kultivator.discipleui.pill.heading"), x + 2.0F, y, room, 0.8F);
		ItemStack stack = held();
		g.fill(slotX, slotY, slotX + 18, slotY + 18, Palette.argb(0x60, Palette.PAPER_DEEP));
		Brush.frame(g, slotX, slotY, slotX + 18, slotY + 18, isPill(stack) ? Palette.GOLD : Palette.argb(0xA0, Palette.INK_FAINT));
		float textX = slotX + 22.0F;
		float textRoom = x + columnW - 2.0F - textX;
		if (stack.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.discipleui.pill.empty_hand"), textX, slotY + 1.0F, textRoom, Palette.INK_FAINT, 0.6F, false);
		} else {
			Brush.fit(g, font, Component.translatable("kultivator.discipleui.pill.hand"), textX, slotY, textRoom, Palette.INK_SOFT, 0.6F, 0.45F, false);
			Brush.fit(g, font, stack.getHoverName(), textX, slotY + 7.0F, textRoom, isPill(stack) ? Palette.INK : Palette.WARN, 0.75F, 0.45F, false);
		}
		if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
			screen.showTooltip(feedTip());
		}

		// ---- breakthrough
		heading(g, Component.translatable("kultivator.discipleui.bt.heading"), x + 2.0F, btTextY - 14.0F, room, 0.8F);
		float ty = btTextY;
		for (Component line : breakthroughLines()) {
			if (ty + 6.0F > btButtonY - 1.0F) {
				break;
			}
			ty += Brush.wrap(g, font, line, x + 4.0F, ty, room - 2.0F, Palette.INK_SOFT, 0.6F, false);
		}
		Component state = breakthroughState();
		if (state != null) {
			Brush.wrap(g, font, state, x + 4.0F, btButtonY + BUTTON_H + 3.0F, room - 2.0F, Palette.INK, 0.6F, false);
		}

		// ---- Dao
		heading(g, Component.translatable("kultivator.discipleui.dao.heading"), rightX + 2.0F, y, room, 0.8F);
		Component daoLine;
		if (view.dao != null) {
			daoLine = Component.translatable("kultivator.discipleui.dao.chosen", view.dao.displayName().copy().withColor(Palette.onPaper(view.dao.color())));
		} else if (pendingDao >= 0 && Util.getMillis() <= pendingUntil) {
			Dao pending = Glyphs.BAGUA[pendingDao];
			daoLine = Component.translatable("kultivator.discipleui.dao.confirm", pending.displayName().copy().withColor(Palette.onPaper(pending.color())))
					.withColor(Palette.SEAL_DARK);
		} else if (view.canChooseDao) {
			daoLine = Component.translatable("kultivator.discipleui.dao.pick");
		} else {
			daoLine = daoLocked();
		}
		Brush.wrap(g, font, daoLine, rightX + 4.0F, tilesBottom + 4.0F, room - 2.0F, Palette.INK_SOFT, 0.6F, false);

		// ---- release
		heading(g, Component.translatable("kultivator.discipleui.release.heading"), rightX + 2.0F, y + h - BUTTON_H - 15.0F, room, 0.8F);
		// The held item goes on its own layer so it sits on top of the slot.
		g.nextStratum();
		if (!stack.isEmpty()) {
			g.item(stack, slotX + 1, slotY + 1);
		}
	}
}
