package dev.kultivator.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.JadeTablet;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The cultivation info screen in the "Gulungan Giok" (jade scroll) style (DESIGN 5b, 12): a scroll that unrolls in a
 * quarter second over an ink-blue night with distant mountains, five jade tablets hanging on its left edge as the
 * tabs, and the tab's pictures and talismans on the parchment. Not a pause screen; the info key or Escape closes it.
 * {@code tab}: 0 Status, 1 Root and Dao, 2 Techniques, 3 Settings, 4 Help.
 */
public class CultivationScreen extends Screen {

	public static final int TAB_STATUS = 0;
	public static final int TAB_ROOT_DAO = 1;
	public static final int TAB_TECHNIQUES = 2;
	public static final int TAB_SETTINGS = 3;
	public static final int TAB_HELP = 4;

	private static final long UNROLL_MILLIS = 250L;
	private static final int TABLET_W = 20;
	private static final int MAX_W = 440;
	private static final int MAX_H = 284;

	protected int tab;

	private final Page[] pages;
	private final List<JadeTablet> tablets = new ArrayList<>();
	private final Eased backdrop = new Eased(6.0F);
	private long openedAt;
	private long lastFrame;
	private long seenVersion = Long.MIN_VALUE;
	private boolean opened;
	private @Nullable JadeTablet pressedTablet;
	private @Nullable List<Component> tooltip;
	private int mouseX;
	private int mouseY;

	private int paperX0;
	private int paperX1;
	private int paperTop;
	private int paperBottom;
	private int contentX;
	private int contentY;
	private int contentW;
	private int contentH;

	public CultivationScreen(int tab) {
		super(Component.translatable("kultivator.screen.title"));
		this.tab = Math.max(TAB_STATUS, Math.min(TAB_HELP, tab));
		this.pages = new Page[]{new StatusPage(this), new RootDaoPage(this), new TechniquesPage(this), new SettingsPage(this), new HelpPage(this)};
	}

	@Override
	protected void init() {
		if (!opened) {
			opened = true;
			openedAt = Util.getMillis();
			lastFrame = openedAt;
			UiSound.open();
			Keys.send(Payloads.Action.of(Actions.REQUEST_SYNC));
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
		int count = pages.length;
		int top = paperTop + 10;
		int gap = height < 250 ? 2 : 3;
		int tabletH = Math.max(24, Math.min(54, (paperBottom - paperTop - 20 - gap * (count - 1)) / count));
		for (int i = 0; i < count; i++) {
			int index = i;
			JadeTablet tablet = new JadeTablet(paperX0 - TABLET_W + 1, top + i * (tabletH + gap), TABLET_W, tabletH, i,
					() -> pages[index].shortName(), () -> tab == index);
			tablet.onPress(() -> switchTab(index));
			tablet.tooltip(() -> List.of(pages[index].title(), Component.translatable("kultivator.screen.tab.key", index + 1)));
			tablets.add(tablet);
		}
		for (Page page : pages) {
			page.layout(contentX, contentY, contentW, contentH);
		}
		seenVersion = ClientCultivation.version();
	}

	/** Shows {@code lines} as the tooltip of this frame (drawn last, on top of everything). */
	void showTooltip(List<Component> lines) {
		if (tooltip == null && !lines.isEmpty()) {
			tooltip = lines;
		}
	}

	private void switchTab(int index) {
		if (index == tab || index < 0 || index >= pages.length) {
			return;
		}
		tab = index;
		pages[tab].layout(contentX, contentY, contentW, contentH);
	}

	private float unroll() {
		return Eased.out(Math.min(1.0F, (Util.getMillis() - openedAt) / (float) UNROLL_MILLIS));
	}

	// ================================================================== drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		float fade = backdrop.to(1.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F), 0.0F);
		g.fillGradient(0, 0, width, height, Palette.argb(Math.round(0xC8 * fade), Palette.NIGHT), Palette.argb(Math.round(0xE0 * fade), Palette.NIGHT_MIST));
		// Distant ink-wash mountains along the bottom.
		int base = height;
		int step = Math.max(2, width / 160);
		int far = Palette.argb(Math.round(0x70 * fade), Palette.MOUNTAIN);
		int close = Palette.argb(Math.round(0x90 * fade), Palette.NIGHT);
		for (int x = 0; x < width; x += step) {
			double ridge = Math.sin(x * 0.021D) * 14.0D + Math.sin(x * 0.053D + 1.7D) * 7.0D + Math.sin(x * 0.11D + 0.4D) * 2.5D;
			int peak = (int) Math.round(height * 0.2D + ridge);
			double near = Math.sin(x * 0.034D + 3.1D) * 9.0D + Math.sin(x * 0.08D) * 4.0D;
			int low = Math.max(0, (int) Math.round(height * 0.1D + near));
			// The far range only above the near one, so the two never overlap (no doubled shade).
			if (peak > low) {
				g.fill(x, base - peak, x + step, base - low, far);
			}
			g.fill(x, base - low, x + step, base, close);
		}
		g.nextStratum();
		// A pale moon behind the scroll.
		Brush.halo(g, width * 0.82F, height * 0.18F, 9.0F, 8.0F, 0xE8EEF5, 0.35F * fade);
		Brush.disc(g, width * 0.82F, height * 0.18F, 9.0F, Palette.argb(Math.round(0x90 * fade), 0xE8EEF5));
		minecraft.gui.hud.extractDeferredSubtitles();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		float seconds = Math.max(0.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F));
		lastFrame = now;
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		tooltip = null;

		long version = ClientCultivation.version();
		if (version != seenVersion) {
			seenVersion = version;
			Page current = pages[tab];
			if (current.needsRebuild()) {
				current.layout(contentX, contentY, contentW, contentH);
			} else {
				current.onData();
			}
		}

		float open = unroll();
		int mid = (paperTop + paperBottom) / 2;
		int half = Math.round((paperBottom - paperTop) / 2.0F * open);
		int top = mid - half;
		int bottom = mid + half;

		// Tablets hang behind the paper and slide out once it has opened.
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
			pages[tab].render(g, mouseX, mouseY, seconds);
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

	/** "Cultivation · Status" between two gold rules, with a seal at each end. */
	private void drawHeader(GuiGraphicsExtractor g) {
		Component text = Brush.bold(Component.translatable("kultivator.screen.header", title, pages[tab].title()));
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
		Glyphs.tab(g, tab, contentX + 4.0F, lineY, 6.0F, Palette.GOLD_DARK);
		Glyphs.tab(g, tab, contentX + contentW - 4.0F, lineY, 6.0F, Palette.GOLD_DARK);
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
		if (pages[tab].mouseClicked(event.x(), event.y(), event.button())) {
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
		if (pages[tab].mouseReleased(event.x(), event.y(), event.button())) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (pages[tab].mouseScrolled(x, y, scrollY)) {
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (Keys.INFO.matches(event)) {
			onClose();
			return true;
		}
		// The codex key opens the Kitab Teknik from here too (DESIGN 22.12.1).
		if (Keys.CODEX.matches(event)) {
			dev.kultivator.client.codex.Codex.open(-1);
			return true;
		}
		int number = event.key() - InputConstants.KEY_1;
		if (number >= 0 && number < pages.length && event.key() <= InputConstants.KEY_5) {
			if (number != tab) {
				UiSound.tab();
				switchTab(number);
			}
			return true;
		}
		return super.keyPressed(event);
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
