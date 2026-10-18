package dev.kultivator.client.screen;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.goals.GoalsClient;
import dev.kultivator.client.goals.GuideTracker;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Rank;
import dev.kultivator.goals.DailyChores;
import dev.kultivator.goals.GoalCounters;
import dev.kultivator.goals.Guide;
import dev.kultivator.goals.RealmTrials;
import dev.kultivator.network.Actions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The "Jalan / Path" tablet (DESIGN 19.3.9, {@code CultivationScreen.TAB_JALAN}, also {@code /kultivasi jalan}): three
 * sections switched with jade coins, Langkah · Ujian · Harian. Langkah shows the five chapters as small seal tablets on
 * the left and the chosen chapter's four steps (with live "how" lines) on the right, the chapter's reward talisman and
 * the "Sembunyikan panduan" coin; Ujian the open Foundation Trial set with progress bars and its bonus; Harian today's
 * chores, the streak and when the next set comes. Everything is read from the synced {@code extra.goals}; the server
 * owns every rule. Jade-scroll style, fits 320×240.
 */
class JalanPage extends Page {

	private static final String P = "kultivator.goals.page.";
	private static final int SECTIONS = 3;
	private static final int[] TRIAL_COLORS = {0xFF2E8963, 0xFF8A671B, 0xFF6E160F};

	private static int section;
	private static int chapter = -1;
	private final Map<Widget, Integer> owner = new HashMap<>();
	private final Eased stepScrollShown = new Eased(14.0F);
	private float stepScroll;
	private float stepHeight;
	private int listX;
	private int listW;
	private int bodyY;
	private int bodyH;

	JalanPage(CultivationScreen screen) {
		super(screen);
		// Each time the scroll opens, the Langkah section starts at the chapter being worked on.
		chapter = -1;
	}

	@Override
	Component title() {
		return Component.translatable("kultivator.spine7.tab.jalan");
	}

	@Override
	Component shortName() {
		return Component.translatable("kultivator.spine7.tab.jalan.short");
	}

	@Override
	protected void build() {
		owner.clear();
		if (chapter < 0) {
			chapter = Math.min(Guide.CHAPTERS - 1, Guide.currentChapter(Guide.bits(data())));
		}
		// The three section coins along the top.
		String[] names = {"steps", "trials", "daily"};
		int coinH = 11;
		int coinW = (w - 4) / SECTIONS;
		for (int i = 0; i < SECTIONS; i++) {
			int index = i;
			JadeCoin coin = new JadeCoin(x + 2 + i * coinW, y, coinW - 4, coinH, () -> Component.translatable(P + "section." + names[index]),
					() -> section == index);
			coin.onPress(() -> {
				section = index;
				stepScroll = 0.0F;
			});
			coin.tooltip(() -> List.of(Component.translatable(P + "section." + names[index]), Component.translatable(P + "section." + names[index]
					+ ".tip").withStyle(ChatFormatting.GRAY)));
			add(coin);
		}
		bodyY = y + coinH + 5;
		bodyH = y + h - bodyY;

		// Langkah: five chapter tablets, the claim talisman and the hide coin.
		int tabletW = Math.max(78, Math.min(118, Math.round(w * 0.36F)));
		int tabletH = Math.max(14, Math.min(22, (bodyH - 18) / Guide.CHAPTERS - 2));
		for (int i = 0; i < Guide.CHAPTERS; i++) {
			ChapterTablet tablet = new ChapterTablet(x + 2, bodyY + i * (tabletH + 2), tabletW, tabletH, i);
			tablet.onPress(() -> {
				chapter = tablet.index;
				stepScroll = 0.0F;
			});
			tablet.tooltip(() -> List.of(Component.translatable("kultivator.goals.chapter." + (tablet.index + 1)),
					Component.translatable("kultivator.goals.reward." + (tablet.index + 1)).withStyle(ChatFormatting.GOLD)));
			put(tablet, 0);
		}
		JadeCoin hide = new JadeCoin(x + 2, y + h - 12, tabletW, 11, () -> Component.translatable(P + "hide"), () -> Guide.hidden(data()));
		hide.enabledWhen(Keys::connected);
		hide.onPress(() -> send(Actions.JALAN, 0, Guide.hidden(data()) ? "show" : "hide"));
		hide.tooltip(() -> List.of(Component.translatable(P + "hide"), Component.translatable(P + "hide.tip").withStyle(ChatFormatting.GRAY)));
		put(hide, 0);
		listX = x + tabletW + 8;
		listW = x + w - listX;
		TalismanButton claim = new TalismanButton(listX, y + h - 15, Math.min(140, listW), 15, () -> Component.translatable(
				(Guide.claimed(data()) & (1 << chapter)) != 0 ? P + "claimed" : P + "claim"));
		claim.enabledWhen(() -> Keys.connected() && GoalsClient.cfg("guide", true) && Guide.chapterDone(Guide.bits(data()), chapter)
				&& (Guide.claimed(data()) & (1 << chapter)) == 0);
		claim.urgentWhen(() -> claim.isEnabled());
		claim.onPress(() -> send(Actions.JALAN, 0, "claim:" + (chapter + 1)));
		claim.tooltip(() -> List.of(Component.translatable(P + "claim"), Component.translatable("kultivator.goals.reward." + (chapter + 1))
				.withStyle(ChatFormatting.GOLD), Component.translatable(GoalsClient.cfg("guide", true) ? P + "claim.tip" : P + "claim.off")
				.withStyle(ChatFormatting.GRAY)));
		put(claim, 0);
	}

	private void put(Widget widget, int forSection) {
		add(widget);
		owner.put(widget, forSection);
	}

	@Override
	protected boolean visible(Widget widget) {
		Integer forSection = owner.get(widget);
		return forSection == null || forSection == section;
	}

	@Override
	void onData() {
		chapter = Math.max(0, Math.min(Guide.CHAPTERS - 1, chapter));
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CultivationData data = data();
		switch (section) {
			case 1 -> drawTrials(g, data);
			case 2 -> drawDaily(g, data);
			default -> drawSteps(g, data, seconds);
		}
	}

	private void drawSteps(GuiGraphicsExtractor g, CultivationData data, float seconds) {
		int bits = Guide.bits(data);
		float scale = listW >= 180 ? 0.68F : 0.6F;
		Component chapterName = Component.translatable(P + "chapter", chapter + 1, Component.translatable("kultivator.goals.chapter." + (chapter + 1)));
		Component total = Component.translatable(P + "total", Integer.bitCount(bits), Guide.STEP_COUNT);
		float totalW = font.width(total) * 0.6F;
		heading(g, chapterName, listX, bodyY, listW - totalW - 4.0F, 0.8F);
		Brush.text(g, font, total, x + w - totalW - 1.0F, bodyY + 1.5F, Palette.INK_SOFT, 0.6F, false);
		int top = Math.round(bodyY + 12.0F);
		int bottom = y + h - 30;
		float maxScroll = Math.max(0.0F, stepHeight - (bottom - top));
		stepScroll = Math.max(0.0F, Math.min(maxScroll, stepScroll));
		float offset = stepScrollShown.to(stepScroll, seconds);
		g.enableScissor(listX, top, listX + listW, bottom);
		float cursor = top + 1.0F - offset;
		float start = cursor;
		int current = Guide.currentStep(bits);
		float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 220.0D);
		for (int s = 0; s < Guide.PER_CHAPTER; s++) {
			int step = chapter * Guide.PER_CHAPTER + s;
			boolean done = (bits & (1 << step)) != 0;
			int mark = done ? 0xFF2E8963 : step == current ? Palette.mix(Palette.GOLD_DARK, Palette.SEAL, pulse) : Palette.INK_FAINT;
			Brush.text(g, font, Component.literal(done ? "✓" : step == current ? "◆" : "·"), listX + 1.0F, cursor, mark, scale + 0.1F, false);
			Component name = Component.translatable("kultivator.goals.step." + Guide.STEPS[step].id());
			cursor += Brush.wrap(g, font, done ? name : Brush.bold(name), listX + 10.0F, cursor, listW - 12.0F, done ? Palette.INK_SOFT : Palette.INK,
					scale, false);
			if (!done) {
				cursor += Brush.wrap(g, font, GuideTracker.how(step, data), listX + 10.0F, cursor, listW - 12.0F, Palette.INK_SOFT, scale - 0.06F, false);
			}
			cursor += 3.0F;
		}
		stepHeight = cursor - start;
		g.disableScissor();
		if (maxScroll > 0.0F) {
			float bead = top + 2.0F + (bottom - top - 4.0F) * (offset / maxScroll);
			Brush.disc(g, listX + listW - 2.0F, bead, 1.8F, Palette.JADE);
		}
		// The chapter's reward under the steps, the claim talisman under it.
		Component reward = Component.translatable(P + "reward", Component.translatable("kultivator.goals.reward." + (chapter + 1)));
		Brush.fit(g, font, reward, listX, y + h - 25.0F, listW, Palette.GOLD_DARK, 0.6F, 0.42F, false);
	}

	private void drawTrials(GuiGraphicsExtractor g, CultivationData data) {
		CompoundTag trial = RealmTrials.view(data);
		String key = trial.getStringOr("target", "");
		RealmTrials.Trial[] set = key.isEmpty() ? null : RealmTrials.set(key);
		float scale = w >= 260 ? 0.7F : 0.62F;
		float cursor = bodyY;
		if (set == null) {
			heading(g, Component.translatable(P + "trials.title_none"), x + 2.0F, cursor, w - 4.0F, 0.8F);
			cursor += 14.0F;
			Brush.wrap(g, font, Component.translatable(P + "trials.none"), x + 4.0F, cursor, w - 8.0F, Palette.INK_SOFT, scale, false);
			sourceLine(g, Component.translatable(P + "trials.source"));
			return;
		}
		heading(g, Component.translatable(P + "trials.title", RealmTrials.targetName(key)), x + 2.0F, cursor, w - 4.0F, 0.8F);
		cursor += 14.0F;
		int[] prog = trial.getIntArray("prog").orElse(new int[6]);
		int done = trial.getIntOr("done", 0);
		for (int i = 0; i < 3; i++) {
			RealmTrials.Trial each = set[i];
			boolean ok = (done & (1 << i)) != 0;
			int have = prog.length == 6 ? prog[i * 2] : 0;
			int goal = Math.max(1, each.goal());
			String amount = each.kind().minutes() ? have / 60 + "/" + goal / 60 : have + "/" + goal;
			Brush.text(g, font, Component.literal(ok ? "✓" : String.valueOf(i + 1)), x + 4.0F, cursor, ok ? 0xFF2E8963 : TRIAL_COLORS[i], scale + 0.1F, false);
			Brush.fit(g, font, RealmTrials.describe(each), x + 14.0F, cursor, w - 70.0F, ok ? Palette.INK_SOFT : Palette.INK, scale, 0.45F, false);
			Component number = Component.literal(amount);
			float numberW = font.width(number) * scale;
			Brush.text(g, font, number, x + w - 6.0F - numberW, cursor, ok ? 0xFF2E8963 : Palette.INK_SOFT, scale, false);
			cursor += 9.0F * scale + 1.5F;
			bar(g, x + 14.0F, cursor, w - 20.0F, Math.min(1.0F, have / (float) goal), ok);
			cursor += 6.0F;
		}
		Rank target = RealmTrials.rankOf(key);
		double bonus = target == null ? 0.0D : RealmTrials.bonus(data, target);
		cursor += 2.0F;
		cursor += Brush.wrap(g, font, Component.translatable(P + "trials.bonus", String.format(Locale.ROOT, "%+.2f", bonus)),
				x + 4.0F, cursor, w - 8.0F, bonus > 0.0D ? Palette.GOLD_DARK : Palette.INK_SOFT, scale, false) + 1.0F;
		if (done == 7) {
			cursor += Brush.wrap(g, font, Brush.bold(Component.translatable(P + "trials.full")), x + 4.0F, cursor, w - 8.0F, Palette.SEAL_DARK, scale,
					false) + 1.0F;
		}
		cursor += Brush.wrap(g, font, Component.translatable(P + "trials.rule", String.format(Locale.ROOT, "%.2f", RealmTrials.QUALITY_PER_TRIAL),
				String.format(Locale.ROOT, "%.2f", RealmTrials.FULL_BONUS), String.format(Locale.ROOT, "%.1f", RealmTrials.FULL_STRIKE_FACTOR)),
				x + 4.0F, cursor, w - 8.0F, Palette.INK_FAINT, scale - 0.06F, false);
		sourceLine(g, Component.translatable(P + "trials.source"));
	}

	private void drawDaily(GuiGraphicsExtractor g, CultivationData data) {
		float scale = w >= 260 ? 0.7F : 0.62F;
		float cursor = bodyY;
		CompoundTag daily = DailyChores.view(data);
		int streak = daily.getIntOr("streak", 0);
		heading(g, Component.translatable(P + "daily.title", streak, Math.round((DailyChores.streakFactor(streak) - 1.0D) * 100.0D)), x + 2.0F, cursor,
				w - 4.0F, 0.8F);
		cursor += 14.0F;
		if (!GoalsClient.cfg("daily", true)) {
			Brush.wrap(g, font, Component.translatable(P + "daily.off"), x + 4.0F, cursor, w - 8.0F, Palette.INK_SOFT, scale, false);
			sourceLine(g, Component.translatable(P + "daily.source"));
			return;
		}
		ListTag chores = daily.getListOrEmpty("chores");
		int done = daily.getIntOr("done", 0);
		if (chores.isEmpty()) {
			cursor += Brush.wrap(g, font, Component.translatable(P + "daily.wait"), x + 4.0F, cursor, w - 8.0F, Palette.INK_SOFT, scale, false) + 2.0F;
		}
		for (int i = 0; i < chores.size(); i++) {
			CompoundTag entry = chores.getCompoundOrEmpty(i);
			DailyChores.Chore chore = DailyChores.Chore.byId(entry.getStringOr("id", ""));
			if (chore == null) {
				continue;
			}
			boolean ok = (done & (1 << i)) != 0;
			int need = Math.max(1, entry.getIntOr("need", 1));
			int have = Math.min(need, entry.getIntOr("have", 0));
			String amount = chore.seconds() ? have / 60 + "/" + need / 60 : have + "/" + need;
			Brush.text(g, font, Component.literal(ok ? "✓" : "○"), x + 4.0F, cursor, ok ? 0xFF2E8963 : Palette.SEAL, scale + 0.1F, false);
			Brush.fit(g, font, DailyChores.name(chore, need), x + 14.0F, cursor, w - 70.0F, ok ? Palette.INK_SOFT : Palette.INK, scale, 0.45F, false);
			Component number = Component.literal(amount);
			float numberW = font.width(number) * scale;
			Brush.text(g, font, number, x + w - 6.0F - numberW, cursor, ok ? 0xFF2E8963 : Palette.INK_SOFT, scale, false);
			cursor += 9.0F * scale + 1.5F;
			bar(g, x + 14.0F, cursor, w - 20.0F, have / (float) need, ok);
			cursor += 6.0F;
		}
		cursor += 2.0F;
		cursor += Brush.wrap(g, font, Component.translatable(P + "daily.reward", Math.round(DailyChores.REWARD_SHARE * 100.0D)), x + 4.0F, cursor,
				w - 8.0F, Palette.GOLD_DARK, scale, false) + 1.0F;
		if (daily.getBooleanOr("full", false)) {
			cursor += Brush.wrap(g, font, Brush.bold(Component.translatable(P + "daily.all_done")), x + 4.0F, cursor, w - 8.0F, Palette.SEAL_DARK, scale,
					false) + 1.0F;
		}
		// When the next set may come: a new day and 18,000 ticks played since the last refresh.
		long dayLeft = DailyChores.DAY_TICKS - Math.floorMod(ClientCultivation.serverGameTime(), DailyChores.DAY_TICKS);
		int played = GoalCounters.value(data, GoalCounters.PLAYED) - daily.getIntOr("last", 0);
		int playLeft = Math.max(0, DailyChores.MIN_PLAYED - played);
		Component next = playLeft > 0 ? Component.translatable(P + "daily.next_play", minutes(dayLeft), minutes(playLeft))
				: Component.translatable(P + "daily.next", minutes(dayLeft));
		Brush.wrap(g, font, next, x + 4.0F, cursor, w - 8.0F, Palette.INK_FAINT, scale - 0.06F, false);
		sourceLine(g, Component.translatable(P + "daily.source"));
	}

	private static Component minutes(long ticks) {
		long minutes = Math.max(1L, (ticks + 1199L) / 1200L);
		return Component.translatable(P + "minutes", minutes);
	}

	/** A progress bar of bamboo with a gold (or jade, when done) fill. */
	private static void bar(GuiGraphicsExtractor g, float bx, float by, float width, float share, boolean done) {
		Brush.rect(g, bx, by, bx + width, by + 3.0F, Palette.argb(0x70, Palette.BAMBOO_KNOT));
		Brush.rect(g, bx, by, bx + width * Math.max(0.0F, Math.min(1.0F, share)), by + 3.0F, done ? Palette.JADE : Palette.GOLD);
		Brush.rect(g, bx, by, bx + width, by + 0.5F, 0x30FFFFFF);
	}

	/** The section's Sumber line at the bottom of the scroll, in faint italics. */
	private void sourceLine(GuiGraphicsExtractor g, Component text) {
		Component line = text.copy().withStyle(ChatFormatting.ITALIC);
		float scale = 0.52F;
		float height = Brush.wrapHeight(font, line, w - 8.0F, scale);
		Brush.wrap(g, font, line, x + 4.0F, y + h - height - 1.0F, w - 8.0F, Palette.INK_FAINT, scale, false);
	}

	@Override
	boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (section == 0 && mouseX >= listX && inside(mouseX, mouseY)) {
			stepScroll -= (float) amount * 12.0F;
			return true;
		}
		return false;
	}

	/** One chapter as a small jade tablet: the red seal with its number, the name, n/4 and a gold stamp once claimed. */
	private final class ChapterTablet extends Widget {

		final int index;

		ChapterTablet(int x, int y, int w, int h, int index) {
			super(x, y, w, h);
			this.index = index;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			CultivationData data = data();
			int bits = Guide.bits(data);
			boolean chosen = chapter == index;
			boolean done = Guide.chapterDone(bits, index);
			boolean claimed = (Guide.claimed(data) & (1 << index)) != 0;
			float glow = hover.get();
			int off = pressOffset(over);
			int x0 = x + (chosen ? 3 : 0) + off;
			int top = Palette.mix(Palette.JADE_PALE, Palette.JADE_LIGHT, chosen ? 0.6F : 0.25F * glow);
			int bottom = Palette.mix(Palette.JADE_DULL, Palette.JADE, chosen ? 0.6F : 0.2F);
			g.fill(x0 + 1, y + 2 + off, x0 + w - (chosen ? 3 : 0) + 1, y + h + 1 + off, 0x40000000);
			Brush.chamferGradient(g, x0, y + off, x0 + w - (chosen ? 3 : 0), y + h + off, 2, top, bottom);
			if (chosen) {
				Brush.frame(g, x0, y + off, x0 + w - 3, y + h + off, Palette.GOLD);
			}
			int seal = Math.max(8, h - 6);
			int sx = x0 + 3;
			int sy = y + off + (h - seal) / 2;
			g.fill(sx, sy, sx + seal, sy + seal, done ? Palette.SEAL : Palette.grey(Palette.SEAL, 0.6F));
			Brush.centered(g, font, Component.literal(String.valueOf(index + 1)), sx + seal / 2.0F + 0.3F, sy + (seal - 7.0F) / 2.0F, 0xFFF1D57E, 0.8F,
					false);
			Component count = Component.literal(Guide.doneIn(bits, index) + "/" + Guide.PER_CHAPTER);
			float countW = font.width(count) * 0.6F;
			float tx = sx + seal + 3.0F;
			float room = x0 + w - (chosen ? 3 : 0) - tx - countW - (claimed ? 9.0F : 4.0F);
			Brush.fit(g, font, Component.translatable("kultivator.goals.chapter." + (index + 1)), tx, y + off + (h - 7.0F) / 2.0F, room, Palette.INK, 0.7F,
					0.45F, false);
			float right = x0 + w - (chosen ? 3 : 0) - 3.0F;
			if (claimed) {
				Brush.ring(g, right - 3.0F, y + off + h / 2.0F, 2.2F, 3.0F, Palette.argb(0xD0, Palette.SEAL));
				right -= 7.5F;
			}
			Brush.text(g, font, count, right - countW, y + off + (h - 6.0F) / 2.0F, done ? 0xFF2E8963 : Palette.INK_SOFT, 0.6F, false);
		}

		@Override
		protected void activate() {
			UiSound.tab();
			if (action != null) {
				action.run();
			}
		}
	}
}
