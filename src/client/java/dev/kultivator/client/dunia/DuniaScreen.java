package dev.kultivator.client.dunia;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.JadeTablet;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.UiSound;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Dunia screen (DESIGN 25.10, {@code OpenScreen.DUNIA = 7}, {@code /kultivasi dunia [tab]}, the {@code dunia} key): the
 * jade scroll at most 320 x 240 with four jade tablets on its left edge, Alam · Pusaka · Musuh · Desa, filled by the pages
 * of modules A, C, B and D. On opening it asks every module for its data ("sync"); a page refreshes when its
 * {@code DuniaSync} arrives. Not a pause screen; the dunia key or Escape closes it.
 */
public class DuniaScreen extends Screen {

	private static final long UNROLL_MILLIS = 250L;
	private static final int TABLET_W = 20;
	private static final int MAX_W = 320;
	private static final int MAX_H = 240;
	private static final int MAX_TABLET_H = 46;

	private final List<DuniaPage> pages = new ArrayList<>();
	private final List<JadeTablet> tablets = new ArrayList<>();
	private final Eased backdrop = new Eased(6.0F);
	private int tab;
	private long seenVersion = Long.MIN_VALUE;
	private long openedAt;
	private long lastFrame;
	private boolean opened;
	private @Nullable JadeTablet pressedTablet;
	private @Nullable List<Component> tooltip;

	private int paperX0;
	private int paperX1;
	private int paperTop;
	private int paperBottom;
	private int contentX;
	private int contentY;
	private int contentW;
	private int contentH;

	/** Opens on {@code tab}: 0 Alam, 1 Pusaka, 2 Musuh, 3 Desa ({@code Payloads.OpenScreen.DUNIA_*}). */
	public DuniaScreen(int tab) {
		super(Component.translatable("kultivator.spine8.dunia.title"));
		pages.add(new dev.kultivator.client.alam.AlamPage(this));
		pages.add(new dev.kultivator.client.tempa.PusakaPage(this));
		pages.add(new dev.kultivator.client.musuh.MusuhPage(this));
		pages.add(new dev.kultivator.client.desa.DesaPage(this));
		this.tab = Math.max(0, Math.min(pages.size() - 1, tab));
	}

	/** Shows {@code lines} as this frame's tooltip (drawn last, over everything). */
	public void showTooltip(List<Component> lines) {
		if (tooltip == null && !lines.isEmpty()) {
			tooltip = lines;
		}
	}

	/** The tab shown now (0-3). */
	public int tab() {
		return tab;
	}

	/** Switches to {@code index} (0-3) while open (the server asked for another tab). */
	public void showTab(int index) {
		switchTab(Math.max(0, Math.min(pages.size() - 1, index)));
	}

	@Override
	protected void init() {
		if (!opened) {
			opened = true;
			openedAt = Util.getMillis();
			lastFrame = openedAt;
			UiSound.open();
			DuniaClient.requestAll();
		}
		int scrollW = Math.min(MAX_W, width - TABLET_W - 26);
		int scrollH = Math.min(MAX_H, height - 12);
		paperX0 = (width - scrollW + TABLET_W) / 2;
		paperX1 = paperX0 + scrollW;
		paperTop = (height - scrollH) / 2 + 5;
		paperBottom = paperTop + scrollH - 10;
		contentX = paperX0 + ScrollFrame.SIDE + 7;
		contentW = paperX1 - ScrollFrame.SIDE - 7 - contentX;
		contentY = paperTop + ScrollFrame.END + 17;
		contentH = paperBottom - ScrollFrame.END - 6 - contentY;

		tablets.clear();
		int count = pages.size();
		int top = paperTop + 10;
		int gap = height < 250 ? 2 : 3;
		int tabletH = Math.max(24, Math.min(MAX_TABLET_H, (paperBottom - paperTop - 20 - gap * (count - 1)) / count));
		for (int i = 0; i < count; i++) {
			int index = i;
			DuniaPage page = pages.get(i);
			JadeTablet tablet = new JadeTablet(paperX0 - TABLET_W + 1, top + i * (tabletH + gap), TABLET_W, tabletH, page.icon(), page::shortName,
					() -> tab == index);
			tablet.onPress(() -> switchTab(index));
			tablet.tooltip(() -> List.of(page.title(), Component.translatable("kultivator.spine8.dunia.key", index + 1)));
			tablets.add(tablet);
		}
		for (DuniaPage page : pages) {
			page.layout(contentX, contentY, contentW, contentH);
		}
		seenVersion = DuniaClient.version();
	}

	private void switchTab(int index) {
		if (index == tab || index < 0 || index >= pages.size()) {
			return;
		}
		tab = index;
		pages.get(tab).layout(contentX, contentY, contentW, contentH);
	}

	/** Picks up syncs that arrived since the last frame. */
	private void refresh() {
		long version = DuniaClient.version();
		if (version == seenVersion) {
			return;
		}
		seenVersion = version;
		DuniaPage current = pages.get(tab);
		if (current.needsRebuild()) {
			current.layout(contentX, contentY, contentW, contentH);
		} else {
			current.onData();
		}
	}

	private float unroll() {
		return Eased.out(Math.min(1.0F, (Util.getMillis() - openedAt) / (float) UNROLL_MILLIS));
	}

	// ================================================================== drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		float fade = backdrop.to(1.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F), 0.0F);
		g.fillGradient(0, 0, width, height, Palette.argb(Math.round(0xC0 * fade), Palette.NIGHT), Palette.argb(Math.round(0xD8 * fade), Palette.NIGHT_MIST));
		// Far peaks and a long road winding between them: the wide world beyond the sect.
		int step = Math.max(2, width / 160);
		int far = Palette.argb(Math.round(0x70 * fade), Palette.MOUNTAIN);
		int close = Palette.argb(Math.round(0x90 * fade), Palette.NIGHT);
		for (int x = 0; x < width; x += step) {
			double ridge = Math.sin(x * 0.017D + 2.1D) * 16.0D + Math.sin(x * 0.047D + 0.3D) * 6.0D + Math.sin(x * 0.12D) * 2.0D;
			int peak = (int) Math.round(height * 0.22D + ridge);
			int low = Math.max(0, (int) Math.round(height * 0.08D + Math.sin(x * 0.026D + 1.2D) * 7.0D));
			if (peak > low) {
				g.fill(x, height - peak, x + step, height - low, far);
			}
			g.fill(x, height - low, x + step, height, close);
		}
		g.nextStratum();
		Brush.halo(g, width * 0.16F, height * 0.17F, 8.0F, 8.0F, 0xE8EEF5, 0.3F * fade);
		Brush.disc(g, width * 0.16F, height * 0.17F, 8.0F, Palette.argb(Math.round(0x88 * fade), 0xE8EEF5));
		minecraft.gui.hud.extractDeferredSubtitles();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		float seconds = Math.max(0.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F));
		lastFrame = now;
		tooltip = null;
		refresh();

		float open = unroll();
		int mid = (paperTop + paperBottom) / 2;
		int half = Math.round((paperBottom - paperTop) / 2.0F * open);
		int top = mid - half;
		int bottom = mid + half;

		float tabletsOut = Eased.out(Math.min(1.0F, Math.max(0.0F, (now - openedAt - 150L) / 220.0F)));
		g.pose().pushMatrix();
		g.pose().translate((1.0F - tabletsOut) * (TABLET_W - 2), 0.0F);
		for (JadeTablet tablet : tablets) {
			if (tablet.y + tablet.h <= bottom && tablet.y >= top) {
				tablet.render(g, font, mouseX, mouseY, seconds);
			}
		}
		g.pose().popMatrix();
		g.nextStratum();

		ScrollFrame.paper(g, paperX0, paperX1, top, bottom, paperTop, paperBottom);
		ScrollFrame.rod(g, paperX0, paperX1, top);
		ScrollFrame.rod(g, paperX0, paperX1, bottom);
		g.nextStratum();

		if (bottom - top > 24) {
			g.enableScissor(paperX0 + ScrollFrame.SIDE + 1, top + ScrollFrame.END + 1, paperX1 - ScrollFrame.SIDE - 1, bottom - ScrollFrame.END - 1);
			drawHeader(g);
			pages.get(tab).render(g, mouseX, mouseY, seconds);
			g.disableScissor();
		}

		for (JadeTablet tablet : tablets) {
			if (tabletsOut > 0.9F && tablet.contains(mouseX, mouseY)) {
				showTooltip(tablet.tooltipLines());
			}
		}
		if (tooltip != null && open >= 1.0F) {
			g.nextStratum();
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	/** "World of Cultivation · &lt;tab&gt;" between two gold rules, a seal at each end and the tab's mark in the corners. */
	private void drawHeader(GuiGraphicsExtractor g) {
		DuniaPage current = pages.get(tab);
		Component text = Brush.bold(Component.translatable("kultivator.spine8.dunia.header", title, current.title()));
		float y = paperTop + ScrollFrame.END + 5.0F;
		float cx = (paperX0 + paperX1) / 2.0F;
		float room = contentW - 40.0F;
		float scale = Brush.fitScale(font, text, room, 1.0F, 0.6F);
		float textW = Math.min(room, font.width(text) * scale);
		Brush.fitCentered(g, font, text, cx, y, room, Palette.INK, 1.0F, 0.6F, false);
		float lineY = y + 4.0F * scale;
		float left = cx - textW / 2.0F - 6.0F;
		float right = cx + textW / 2.0F + 6.0F;
		Brush.rect(g, contentX + 8.0F, lineY - 0.5F, left - 4.0F, lineY + 0.5F, Palette.GOLD);
		Brush.rect(g, right + 4.0F, lineY - 0.5F, contentX + contentW - 8.0F, lineY + 0.5F, Palette.GOLD);
		Brush.rect(g, left - 3.0F, lineY - 2.0F, left + 1.0F, lineY + 2.0F, Palette.SEAL);
		Brush.rect(g, right - 1.0F, lineY - 2.0F, right + 3.0F, lineY + 2.0F, Palette.SEAL);
		Glyphs.tab(g, current.icon(), contentX + 4.0F, lineY, 6.0F, Palette.GOLD_DARK);
		Glyphs.tab(g, current.icon(), contentX + contentW - 4.0F, lineY, 6.0F, Palette.GOLD_DARK);
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (JadeTablet tablet : tablets) {
				if (tablet.contains(event.x(), event.y())) {
					tablet.press();
					pressedTablet = tablet;
					return true;
				}
			}
		}
		if (pages.get(tab).mouseClicked(event.x(), event.y(), event.button())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pressedTablet != null) {
			JadeTablet tablet = pressedTablet;
			pressedTablet = null;
			tablet.release(event.x(), event.y());
			return true;
		}
		if (pages.get(tab).mouseReleased(event.x(), event.y(), event.button())) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (pages.get(tab).mouseScrolled(x, y, scrollY)) {
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		DuniaPage current = pages.get(tab);
		if (current.capturesKeys()) {
			// A text field has the keyboard: it gets every key (Escape lets it go first).
			if (current.keyPressed(event)) {
				return true;
			}
			return event.key() == InputConstants.KEY_ESCAPE ? super.keyPressed(event) : true;
		}
		if (Keys.DUNIA.matches(event)) {
			onClose();
			return true;
		}
		int number = event.key() - InputConstants.KEY_1;
		if (number >= 0 && number < pages.size()) {
			if (number != tab) {
				UiSound.tab();
				switchTab(number);
			}
			return true;
		}
		if (current.keyPressed(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		DuniaPage current = pages.get(tab);
		if (current.capturesKeys() && current.charTyped(event)) {
			return true;
		}
		return super.charTyped(event);
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
}
