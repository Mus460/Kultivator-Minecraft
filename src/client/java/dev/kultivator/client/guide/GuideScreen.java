package dev.kultivator.client.guide;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.UiSound;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Kitab Panduan, the in-game guide, in the jade-scroll style of the cultivation scroll and the Titah menu: the twelve
 * chapters down the left (a red seal with the number, the name, a progress bar; a jade seal with a check once complete;
 * the current chapter framed in pulsing gold), and on the right the selected chapter's steps as cards (a check or an
 * empty ring, the title, the wrapped lines, a talisman chip that writes its command into the chat box, and a tick box
 * for steps the server cannot check by itself). The cards scroll with the wheel, the arrow keys or Page Up / Down;
 * left / right change the chapter, Home jumps to the current one. Fits 320 × 240 and every GUI scale; Escape or the
 * guide key closes it (back to the scroll when it was opened from there). Key names in the texts are live (DESIGN 33.6 R11).
 */
public final class GuideScreen extends Screen {

	private static final String K = "kultivator.guide_client.";
	private static final int MAX_W = 480;
	private static final int MAX_H = 304;
	private static final long UNROLL_MILLIS = 250L;
	private static final int CARD_GAP = 4;
	private static final int TICK = 9;
	private static final int CHIP_H = 11;
	private static final int MARK_COLUMN = 15;

	private final @Nullable Screen parent;
	private int selected = -1;
	private boolean picked;
	private boolean sawSync;
	private long openedAt;
	private long lastFrame;
	private int seenVersion = Integer.MIN_VALUE;
	private @Nullable List<Component> tooltip;

	private int x0;
	private int x1;
	private int top;
	private int bottom;
	private int inX;
	private int inW;
	private int bodyTop;
	private int bodyBottom;
	private int listW;
	private int rowH;
	private int cardsX;
	private int cardsW;
	private int viewTop;
	private float titleScale;
	private float bodyScale;

	private final List<Card> cards = new ArrayList<>();
	private List<FormattedCharSequence> descRows = List.of();
	private float contentH;
	private float scroll;
	private final Eased scrollShown = new Eased(14.0F);
	private float listScroll;
	private final Eased listShown = new Eased(14.0F);

	public GuideScreen(@Nullable Screen parent) {
		super(Component.translatable(K + "title"));
		this.parent = parent;
	}

	// ================================================================ layout

	@Override
	protected void init() {
		if (openedAt == 0L) {
			openedAt = Util.getMillis();
			lastFrame = openedAt;
			UiSound.open();
			GuideClient.requestSync();
		}
		int w = Math.min(MAX_W, width - 8);
		int h = Math.min(MAX_H, height - 8);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		top = (height - h) / 2 + 5;
		bottom = top + h - 10;
		inX = x0 + ScrollFrame.SIDE + 8;
		inW = x1 - x0 - 2 * (ScrollFrame.SIDE + 8);
		bodyTop = top + ScrollFrame.END + 20;
		bodyBottom = bottom - ScrollFrame.END - 12;
		listW = Math.max(92, Math.min(136, Math.round(inW * 0.3F)));
		int chapters = Math.max(1, GuideBook.chapterCount());
		rowH = Math.max(15, Math.min(20, (bodyBottom - bodyTop) / chapters));
		cardsX = inX + listW + 8;
		cardsW = inX + inW - cardsX - 5;
		titleScale = cardsW >= 240 ? 0.85F : 0.78F;
		bodyScale = cardsW >= 240 ? 0.75F : 0.68F;
		if (selected < 0) {
			selected = GuideClient.synced() ? GuideClient.current() : 0;
			sawSync = GuideClient.synced();
		}
		selected = Math.max(0, Math.min(chapters - 1, selected));
		layoutCards();
		seenVersion = GuideClient.version();
	}

	/** Measures the selected chapter's cards (positions relative to the top of the card view). */
	private void layoutCards() {
		cards.clear();
		viewTop = bodyTop + 14;
		if (GuideBook.chapterCount() == 0) {
			contentH = 0.0F;
			return;
		}
		Component desc = GuideBook.chapter(selected).desc();
		descRows = desc == null ? List.of() : font.split(KeyHints.fill(desc), Math.max(20, (int) (cardsW / 0.65F)));
		if (descRows.size() > 2) {
			descRows = descRows.subList(0, 2);
		}
		viewTop += Math.round(descRows.size() * 6.5F) + (descRows.isEmpty() ? 0 : 2);
		int y = 0;
		for (GuideBook.Step step : GuideBook.chapter(selected).steps()) {
			int tx = cardsX + MARK_COLUMN;
			int titleRight = cardsX + cardsW - 4 - (step.manual() ? TICK + 4 : 0);
			List<FormattedCharSequence> titleRows = font.split(Brush.bold(KeyHints.fill(GuideBook.text(step.titleKey(), step.id()))),
					Math.max(20, (int) ((titleRight - tx) / titleScale)));
			// Key tokens ([[key:meditate]] ...) show the keys bound right now (DESIGN 33.6 R11).
			List<FormattedCharSequence> bodyRows = font.split(KeyHints.fill(GuideBook.text(step.bodyKey(), "")),
					Math.max(20, (int) ((cardsX + cardsW - 5 - tx) / bodyScale)));
			String hint = step.hint();
			int chipW = 0;
			if (hint != null && !hint.isEmpty()) {
				chipW = Math.min(cardsX + cardsW - 5 - tx, Math.round(font.width(chatText(hint)) * 0.65F) + 14);
			}
			float height = 4.0F + Math.max(TICK, titleRows.size() * 9.0F * titleScale) + 2.0F + bodyRows.size() * 9.0F * bodyScale
					+ (chipW > 0 ? 3.0F + CHIP_H : 0.0F) + 4.0F;
			Card card = new Card(step, titleRows, bodyRows, y, (int) Math.ceil(height), chipW);
			cards.add(card);
			y += card.h + CARD_GAP;
		}
		contentH = y;
	}

	private void select(int chapter) {
		int count = GuideBook.chapterCount();
		if (count == 0) {
			return;
		}
		int target = Math.floorMod(chapter, count);
		picked = true;
		if (target == selected) {
			return;
		}
		UiSound.tab();
		selected = target;
		scroll = 0.0F;
		scrollShown.set(0.0F);
		layoutCards();
		revealRow(target);
	}

	/** Scrolls the chapter list so row {@code index} is visible. */
	private void revealRow(int index) {
		float rowTop = index * rowH;
		float view = bodyBottom - bodyTop;
		if (rowTop < listScroll) {
			listScroll = rowTop;
		} else if (rowTop + rowH > listScroll + view) {
			listScroll = rowTop + rowH - view;
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (seenVersion != GuideClient.version()) {
			seenVersion = GuideClient.version();
			if (!sawSync && GuideClient.synced()) {
				sawSync = true;
				if (!picked) {
					// the first sync of this book: open at the chapter the player is in
					selected = GuideClient.current();
					scroll = 0.0F;
					scrollShown.set(0.0F);
					layoutCards();
					revealRow(selected);
				}
			}
		}
	}

	// ================================================================ drawing

	private float unroll() {
		return Eased.out(Math.min(1.0F, (Util.getMillis() - openedAt) / (float) UNROLL_MILLIS));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, Palette.argb(0xB8, Palette.NIGHT), Palette.argb(0xD8, Palette.NIGHT_MIST));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		float seconds = Math.max(0.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F));
		lastFrame = now;
		tooltip = null;
		float open = unroll();
		int mid = (top + bottom) / 2;
		int half = Math.round((bottom - top) / 2.0F * open);
		ScrollFrame.paper(g, x0, x1, mid - half, mid + half, top, bottom);
		ScrollFrame.rod(g, x0, x1, mid - half);
		ScrollFrame.rod(g, x0, x1, mid + half);
		g.nextStratum();
		if (open < 0.95F) {
			return;
		}
		float pulse = 0.5F + 0.5F * (float) Math.sin(now / 240.0D);
		drawHeader(g);
		drawList(g, mouseX, mouseY, seconds, pulse);
		drawCards(g, mouseX, mouseY, seconds, pulse);
		Component help = Component.translatable(K + "keys");
		Brush.fitCentered(g, font, help, (x0 + x1) / 2.0F, bodyBottom + 3.0F, inW, Palette.INK_FAINT, 0.55F, 0.4F, false);
		if (tooltip != null && !tooltip.isEmpty()) {
			g.nextStratum();
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	/** The title between gold rules, the overall count on the right, the sync state on the left. */
	private void drawHeader(GuiGraphicsExtractor g) {
		float y = top + ScrollFrame.END + 5.0F;
		float cx = (x0 + x1) / 2.0F;
		float room = inW * 0.5F;
		Component text = Brush.bold(title);
		float scale = Brush.fitScale(font, text, room, 1.0F, 0.6F);
		float textW = Math.min(room, font.width(text) * scale);
		Brush.fitCentered(g, font, text, cx, y, room, Palette.INK, 1.0F, 0.6F, false);
		float lineY = y + 11.0F;
		Brush.rect(g, inX, lineY, inX + inW, lineY + 0.6F, Palette.GOLD);
		Glyphs.tab(g, Glyphs.PATH, cx - textW / 2.0F - 8.0F, y + 3.5F, 6.0F, Palette.GOLD_DARK);
		Glyphs.tab(g, Glyphs.PATH, cx + textW / 2.0F + 8.0F, y + 3.5F, 6.0F, Palette.GOLD_DARK);
		float side = (inW - textW) / 2.0F - 16.0F;
		if (side < 20.0F) {
			return;
		}
		Component count;
		int color;
		if (!GuideClient.synced()) {
			count = Component.translatable(K + "loading");
			color = Palette.INK_FAINT;
		} else if (GuideClient.allDone()) {
			count = Component.translatable(K + "all_done");
			color = Palette.JADE_DARK;
		} else {
			count = Component.translatable(K + "progress", GuideClient.doneCount(), GuideBook.stepCount());
			color = Palette.JADE_DARK;
		}
		float countScale = Brush.fitScale(font, count, side, 0.7F, 0.45F);
		Brush.fit(g, font, count, inX + inW - Math.min(side, font.width(count) * countScale), y + 2.0F, side, color, 0.7F, 0.45F, false);
		if (GuideClient.synced() && !GuideClient.allDone()) {
			Component current = Component.translatable(K + "current", GuideClient.current() + 1);
			Brush.fit(g, font, current, inX, y + 2.0F, side, Palette.GOLD_DARK, 0.7F, 0.45F, false);
		}
	}

	/** The chapter list: a seal, the name and a progress bar per chapter. */
	private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds, float pulse) {
		int count = GuideBook.chapterCount();
		float view = bodyBottom - bodyTop;
		float maxScroll = Math.max(0.0F, count * rowH - view);
		listScroll = Math.max(0.0F, Math.min(maxScroll, listScroll));
		float offset = listShown.to(listScroll, seconds);
		g.enableScissor(inX - 3, bodyTop - 2, inX + listW + 3, bodyBottom + 1);
		for (int i = 0; i < count; i++) {
			int ry = Math.round(bodyTop + i * rowH - offset);
			if (ry + rowH < bodyTop || ry > bodyBottom) {
				continue;
			}
			drawRow(g, i, inX, ry, mouseX, mouseY, pulse);
		}
		g.disableScissor();
		if (maxScroll > 0.0F) {
			drawBead(g, inX + listW + 3, bodyTop, bodyBottom, offset / maxScroll);
		}
		// a thin gold rule between the list and the cards
		Brush.rect(g, cardsX - 4.5F, bodyTop, cardsX - 4.0F, bodyBottom, Palette.argb(0x90, Palette.GOLD));
	}

	private void drawRow(GuiGraphicsExtractor g, int index, int x, int y, int mouseX, int mouseY, float pulse) {
		boolean synced = GuideClient.synced();
		int size = GuideBook.chapter(index).steps().size();
		int doneIn = synced ? GuideClient.doneIn(index) : 0;
		boolean complete = synced && size > 0 && doneIn >= size;
		boolean current = synced && !GuideClient.allDone() && index == GuideClient.current();
		boolean chosen = index == selected;
		boolean over = mouseX >= x && mouseX < x + listW && mouseY >= Math.max(y, bodyTop) && mouseY < Math.min(y + rowH - 1, bodyBottom);
		int r = x + listW;
		int b = y + rowH - 1;
		if (chosen) {
			g.fill(x, y, r, b, Palette.argb(0x58, 0xFFFFFF));
			Brush.frame(g, x, y, r, b, Palette.argb(0xA0, Palette.JADE_DARK));
		} else if (over) {
			g.fill(x, y, r, b, Palette.argb(0x2C, 0xFFFFFF));
		}
		if (current) {
			Brush.frame(g, x - 1, y - 1, r + 1, b + 1, Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse));
			g.fill(x, y + 1, x + 2, b - 1, Palette.GOLD);
		}
		// the seal: red with the number, gold for the current chapter, jade with a check when complete
		int sx = x + 3;
		int sy = y + 2;
		int seal = complete ? Palette.JADE : current ? Palette.GOLD : Palette.SEAL;
		g.fill(sx, sy, sx + 10, sy + 10, seal);
		Brush.frame(g, sx + 1, sy + 1, sx + 9, sy + 9, Palette.argb(0xA0, complete ? Palette.JADE_PALE : Palette.TALISMAN));
		if (complete) {
			check(g, sx + 5.0F, sy + 5.0F, 6.0F, 0xFFFFFFFF);
		} else {
			Brush.centered(g, font, Component.literal(String.valueOf(index + 1)), sx + 5.3F, sy + 2.2F, current ? Palette.INK : Palette.TALISMAN,
					0.7F, false);
		}
		// the name and the bar
		int nx = sx + 13;
		int nameColor = current ? Palette.GOLD_DARK : complete ? Palette.JADE_DARK : Palette.INK;
		Brush.fit(g, font, GuideBook.chapter(index).title(), nx, y + 2.5F, r - nx - 2, nameColor, 0.7F, 0.45F, false);
		float barY = b - 3.5F;
		float barX1 = r - 3.0F;
		Brush.rect(g, nx, barY, barX1, barY + 2.0F, Palette.argb(0x48, Palette.INK_FAINT));
		if (size > 0 && doneIn > 0) {
			int fill = complete ? Palette.JADE : current ? Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse * 0.5F) : Palette.JADE_DULL;
			Brush.rect(g, nx, barY, nx + (barX1 - nx) * Math.min(1.0F, doneIn / (float) size), barY + 2.0F, fill);
		}
		if (over) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.translatable(K + "chapter", index + 1, GuideBook.chapter(index).title()));
			if (synced) {
				lines.add(Component.translatable(K + "chapter.count", doneIn, size).withStyle(ChatFormatting.GRAY));
			}
			if (complete) {
				lines.add(Component.translatable(K + "chapter.done").withStyle(ChatFormatting.GREEN));
			} else if (current) {
				lines.add(Component.translatable(K + "chapter.current").withStyle(ChatFormatting.GOLD));
			}
			tooltip = lines;
		}
	}

	/** The selected chapter's heading and its step cards. */
	private void drawCards(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds, float pulse) {
		if (GuideBook.chapterCount() == 0) {
			return;
		}
		boolean synced = GuideClient.synced();
		// heading: a seal-red title over a gold rule, the chapter's count on the right
		Component heading = Brush.bold(Component.translatable(K + "chapter", selected + 1, GuideBook.chapter(selected).title()));
		int size = GuideBook.chapter(selected).steps().size();
		Component count = synced ? Component.translatable(K + "chapter.count", GuideClient.doneIn(selected), size) : Component.empty();
		float countW = font.width(count) * 0.65F;
		Brush.rect(g, cardsX, bodyTop + 1.0F, cardsX + 4.0F, bodyTop + 5.0F, Palette.SEAL);
		Brush.fit(g, font, heading, cardsX + 6.0F, bodyTop, cardsW - 10.0F - countW, Palette.SEAL_DARK, 0.85F, 0.5F, false);
		Brush.text(g, font, count, cardsX + cardsW - countW, bodyTop + 1.5F, Palette.JADE_DARK, 0.65F, false);
		Brush.rect(g, cardsX, bodyTop + 9.5F, cardsX + cardsW, bodyTop + 10.1F, Palette.argb(0xB0, Palette.GOLD));
		float dy = bodyTop + 12.5F;
		for (FormattedCharSequence row : descRows) {
			Brush.text(g, font, row, cardsX, dy, Palette.INK_SOFT, 0.65F, false);
			dy += 6.5F;
		}

		float view = bodyBottom - viewTop;
		float maxScroll = Math.max(0.0F, contentH - CARD_GAP - view);
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		float offset = scrollShown.to(scroll, seconds);
		int next = firstOpen();
		g.enableScissor(cardsX - 2, viewTop, cardsX + cardsW + 6, bodyBottom);
		for (Card card : cards) {
			int cy = Math.round(viewTop + card.relY - offset);
			if (cy + card.h < viewTop || cy > bodyBottom) {
				continue;
			}
			drawCard(g, card, cy, mouseX, mouseY, synced && card.step.index() == next, pulse, seconds);
		}
		g.disableScissor();
		if (maxScroll > 0.0F) {
			drawBead(g, cardsX + cardsW + 4, viewTop, bodyBottom, offset / maxScroll);
		}
	}

	/** The global index of the first unfinished step of the selected chapter, or −1. */
	private int firstOpen() {
		for (GuideBook.Step step : GuideBook.chapter(selected).steps()) {
			if (!GuideClient.done(step.index())) {
				return step.index();
			}
		}
		return -1;
	}

	private void drawCard(GuiGraphicsExtractor g, Card card, int y, int mouseX, int mouseY, boolean next, float pulse, float seconds) {
		GuideBook.Step step = card.step;
		boolean synced = GuideClient.synced();
		boolean done = synced && GuideClient.done(step.index());
		boolean inView = mouseY >= viewTop && mouseY < bodyBottom;
		int x = cardsX;
		int r = cardsX + cardsW;
		int b = y + card.h;
		// the slip
		g.fill(x + 1, y + 1, r + 1, b + 1, 0x18000000);
		g.fill(x, y, r, b, done ? Palette.argb(0x30, Palette.JADE_LIGHT) : Palette.argb(0x48, 0xFFFFFF));
		int edge = next ? Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse) : done ? Palette.argb(0xA0, Palette.JADE) : Palette.argb(0x70, Palette.GOLD_DARK);
		Brush.frame(g, x, y, r, b, edge);
		g.fill(x + 1, y + 1, x + 3, b - 1, done ? Palette.JADE : next ? Palette.GOLD : Palette.argb(0x50, Palette.INK_FAINT));

		// the mark: a jade disc with a check, or an empty ring (gold for the next step)
		float mx = x + 9.0F;
		float my = y + 4.0F + 4.5F;
		boolean overMark = inView && mouseX >= mx - 5 && mouseX < mx + 5 && mouseY >= my - 5 && mouseY < my + 5;
		if (done) {
			Brush.disc(g, mx, my, 4.2F, Palette.JADE);
			check(g, mx, my, 5.5F, 0xFFFFFFFF);
		} else {
			Brush.ring(g, mx, my, 3.0F, 4.0F, next ? Palette.GOLD_DARK : Palette.INK_FAINT);
		}
		if (overMark) {
			tooltip = List.of(Component.translatable(done ? K + "mark.done" : K + "mark.open"),
					Component.translatable(step.manual() ? K + "mark.manual" : K + "mark.auto").withStyle(ChatFormatting.GRAY));
		}

		// the title and the lines
		float tx = x + MARK_COLUMN;
		float ty = y + 4.0F;
		int titleColor = done ? Palette.JADE_DARK : Palette.INK;
		for (FormattedCharSequence row : card.titleRows) {
			Brush.text(g, font, row, tx, ty, titleColor, titleScale, false);
			ty += 9.0F * titleScale;
		}
		ty = Math.max(ty, y + 4.0F + TICK) + 2.0F;
		int bodyColor = done ? Palette.INK_SOFT : Palette.mix(Palette.INK, Palette.INK_SOFT, 0.3F);
		for (FormattedCharSequence row : card.bodyRows) {
			Brush.text(g, font, row, tx, ty, bodyColor, bodyScale, false);
			ty += 9.0F * bodyScale;
		}

		// the command chip: a talisman strip that writes the command into the chat box
		if (card.chipW > 0) {
			int cx = Math.round(tx);
			int cy = Math.round(ty + 3.0F);
			boolean over = inView && mouseX >= cx && mouseX < cx + card.chipW && mouseY >= cy && mouseY < cy + CHIP_H;
			float glow = card.chipHover.to(over ? 1.0F : 0.0F, seconds);
			g.fill(cx + 1, cy + 1, cx + card.chipW + 1, cy + CHIP_H + 1, 0x30000000);
			g.fillGradient(cx, cy, cx + card.chipW, cy + CHIP_H, Palette.lighten(Palette.TALISMAN, 0.15F * glow), Palette.TALISMAN_LOW);
			Brush.frame(g, cx, cy, cx + card.chipW, cy + CHIP_H, over ? Palette.GOLD : Palette.TALISMAN_EDGE);
			int seal = Palette.mix(Palette.SEAL, Palette.SEAL_GLOW, 0.6F * glow);
			g.fill(cx + 2, cy + 2, cx + CHIP_H - 2, cy + CHIP_H - 2, seal);
			Brush.rect(g, cx + 3.5F, cy + CHIP_H / 2.0F - 0.5F, cx + CHIP_H - 3.5F, cy + CHIP_H / 2.0F + 0.5F, Palette.argb(0xC8, Palette.TALISMAN));
			Brush.fit(g, font, Component.literal(chatText(step.hint())), cx + CHIP_H + 1.0F, cy + 2.5F, card.chipW - CHIP_H - 3.0F, Palette.INK,
					0.65F, 0.45F, false);
			if (over) {
				tooltip = List.of(Component.literal(chatText(step.hint())), Component.translatable(K + "chip.tip").withStyle(ChatFormatting.GRAY));
			}
		}

		// the tick box of a manual step
		if (step.manual()) {
			int bx = r - 4 - TICK;
			int by = y + 4;
			boolean over = inView && mouseX >= bx - 1 && mouseX < bx + TICK + 1 && mouseY >= by - 1 && mouseY < by + TICK + 1;
			boolean ticked = synced && GuideClient.ticked(step.index());
			g.fill(bx, by, bx + TICK, by + TICK, ticked ? Palette.JADE : Palette.argb(0xC0, Palette.PAPER_TOP));
			Brush.frame(g, bx, by, bx + TICK, by + TICK, over ? Palette.GOLD : ticked ? Palette.JADE_DARK : Palette.INK_SOFT);
			if (ticked) {
				check(g, bx + TICK / 2.0F, by + TICK / 2.0F, 6.0F, 0xFFFFFFFF);
			}
			if (over) {
				tooltip = List.of(Component.translatable(ticked ? K + "tick.off" : K + "tick.on"),
						Component.translatable(K + "mark.manual").withStyle(ChatFormatting.GRAY));
			}
		}
	}

	/** A small scroll bead on a cord (as on the Help tab). */
	private static void drawBead(GuiGraphicsExtractor g, int x, int y0, int y1, float t) {
		float bead = y0 + 3.0F + (y1 - y0 - 6.0F) * Math.max(0.0F, Math.min(1.0F, t));
		g.fill(x, y0, x + 1, y1, Palette.CORD);
		Brush.disc(g, x + 0.5F, bead, 2.2F, Palette.JADE);
		Brush.disc(g, x, bead - 0.6F, 0.8F, Palette.JADE_PALE);
	}

	/** A check mark of about {@code size} GUI px centred on {@code cx, cy}. */
	private static void check(GuiGraphicsExtractor g, float cx, float cy, float size, int color) {
		float s = size / 2.0F;
		float thick = Math.max(1.0F, size * 0.2F);
		Brush.line(g, cx - s * 0.75F, cy, cx - s * 0.2F, cy + s * 0.6F, thick, color);
		Brush.line(g, cx - s * 0.25F, cy + s * 0.6F, cx + s * 0.8F, cy - s * 0.65F, thick, color);
	}

	/** The command as it is written into the chat box (always starting with a slash). */
	private static String chatText(@Nullable String hint) {
		if (hint == null) {
			return "";
		}
		String text = hint.strip();
		return text.startsWith("/") ? text : "/" + text;
	}

	// ================================================================ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || unroll() < 0.95F) {
			return super.mouseClicked(event, doubleClick);
		}
		double mx = event.x();
		double my = event.y();
		// a chapter row
		if (mx >= inX && mx < inX + listW && my >= bodyTop && my < bodyBottom) {
			int row = (int) Math.floor((my - bodyTop + listShown.get()) / rowH);
			if (row >= 0 && row < GuideBook.chapterCount()) {
				select(row);
				return true;
			}
		}
		// a chip or a tick box on a card
		if (mx >= cardsX && mx < cardsX + cardsW && my >= viewTop && my < bodyBottom) {
			float offset = scrollShown.get();
			for (Card card : cards) {
				int y = Math.round(viewTop + card.relY - offset);
				if (my < y || my >= y + card.h) {
					continue;
				}
				if (card.step.manual()) {
					int bx = cardsX + cardsW - 4 - TICK;
					int by = y + 4;
					if (mx >= bx - 1 && mx < bx + TICK + 1 && my >= by - 1 && my < by + TICK + 1) {
						if (GuideClient.synced() && GuideClient.connected()) {
							UiSound.toggle(!GuideClient.ticked(card.step.index()));
							GuideClient.toggle(card.step);
						} else {
							UiSound.denied();
						}
						return true;
					}
				}
				if (card.chipW > 0) {
					int cx = cardsX + MARK_COLUMN;
					int cy = Math.round(chipTop(card, y));
					if (mx >= cx && mx < cx + card.chipW && my >= cy && my < cy + CHIP_H) {
						UiSound.press();
						minecraft.gui.setScreen(new ChatScreen(chatText(card.step.hint()), false));
						return true;
					}
				}
				break;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** The y of a card's chip when the card's top is at {@code y} (the same sums as {@link #drawCard}). */
	private float chipTop(Card card, int y) {
		float ty = y + 4.0F + card.titleRows.size() * 9.0F * titleScale;
		ty = Math.max(ty, y + 4.0F + TICK) + 2.0F;
		ty += card.bodyRows.size() * 9.0F * bodyScale;
		return Math.round(ty + 3.0F);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (x >= inX - 3 && x < cardsX - 4 && y >= bodyTop && y < bodyBottom) {
			listScroll -= (float) scrollY * rowH;
			return true;
		}
		scroll -= (float) scrollY * 18.0F;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (Keys.GUIDE.matches(event)) {
			onClose();
			return true;
		}
		float page = Math.max(20.0F, bodyBottom - viewTop - 20.0F);
		switch (event.key()) {
			case InputConstants.KEY_UP -> scroll -= 18.0F;
			case InputConstants.KEY_DOWN -> scroll += 18.0F;
			case InputConstants.KEY_PAGEUP -> scroll -= page;
			case InputConstants.KEY_PAGEDOWN -> scroll += page;
			case InputConstants.KEY_LEFT -> select(selected - 1);
			case InputConstants.KEY_RIGHT -> select(selected + 1);
			case InputConstants.KEY_HOME -> select(GuideClient.synced() ? GuideClient.current() : 0);
			default -> {
				return super.keyPressed(event);
			}
		}
		return true;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void removed() {
		UiSound.close();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** One measured step card. */
	private static final class Card {
		final GuideBook.Step step;
		final List<FormattedCharSequence> titleRows;
		final List<FormattedCharSequence> bodyRows;
		final int relY;
		final int h;
		final int chipW;
		final Eased chipHover = new Eased(14.0F);

		Card(GuideBook.Step step, List<FormattedCharSequence> titleRows, List<FormattedCharSequence> bodyRows, int relY, int h, int chipW) {
			this.step = step;
			this.titleRows = titleRows;
			this.bodyRows = bodyRows;
			this.relY = relY;
			this.h = h;
			this.chipW = chipW;
		}
	}
}
