package dev.kultivator.client.codex;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
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
 * The Kitab Teknik (DESIGN 22.12): a jade scroll that unrolls in a quarter second over the ink-blue night, with three
 * tablets on its left edge: Kodeks, Rangkai Segel and Kehancuran Diri (a red tablet). There is no passive tab (PASIF is
 * reserved). The help lives in Panduan › Ensiklopedia: the "?" seal at the right end of the header opens it on this
 * page's topic (DESIGN 33.6 R1); {@link #TAB_HELP} still opens there. Fits 320×240; the detail slip docks on the right
 * from 420 GUI px. The codex key or Escape closes it (an open overlay or the search closes first; opened from the hub,
 * Escape returns to it, C33.N2). Not a pause screen.
 */
public final class CodexScreen extends Screen {

	public static final int TAB_KODEKS = 0;
	public static final int TAB_RING = 1;
	public static final int TAB_KEHANCURAN = 2;
	/** The old Bantuan tablet (DESIGN 33.6 R1): opening on it opens Panduan › Ensiklopedia on {@code kitab_teknik} instead. */
	public static final int TAB_HELP = 3;

	private static final long UNROLL_MILLIS = 250L;
	private static final int TABLET_W = 20;
	private static final int MAX_W = 560;
	private static final int MAX_H = 300;

	private static final CodexFilters FILTERS = CodexFilters.player();
	private static int lastTab;

	private final CodexPage[] pages;
	private final KodeksPage kodeks;
	private final List<CodexWidgets.Tablet> tablets = new ArrayList<>();
	private final Eased backdrop = new Eased(6.0F);
	private int tab;
	/** Opened on {@link #TAB_HELP}: hand over to the Ensiklopedia on the first tick. */
	private boolean helpPending;
	private final KeyHints.HelpSeal help = new KeyHints.HelpSeal(0.0F, 0.0F, 10, this::topic);
	private long openedAt;
	private long lastFrame;
	private long seenVersion = Long.MIN_VALUE;
	private boolean opened;
	private @Nullable CodexWidgets.Tablet pressedTablet;
	private @Nullable List<Component> tooltip;
	private int paperX0;
	private int paperX1;
	private int paperTop;
	private int paperBottom;
	private int contentX;
	private int contentY;
	private int contentW;
	private int contentH;

	public CodexScreen(@Nullable TechniqueSpec focus, int tab) {
		super(Component.translatable("kultivator.codex.title"));
		this.kodeks = new KodeksPage(this, FILTERS);
		this.pages = new CodexPage[]{kodeks, new LoadoutPage(this), new KehancuranPage(this)};
		this.helpPending = tab == TAB_HELP;
		this.tab = Math.max(0, Math.min(pages.length - 1, tab < 0 || tab == TAB_HELP ? lastTab : tab));
		if (focus != null && focus.playerVisible()) {
			this.tab = TAB_KODEKS;
			kodeks.focus(focus);
		}
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
		int scrollW = Math.min(MAX_W, width - TABLET_W - 22);
		int scrollH = Math.min(MAX_H, height - 10);
		paperX0 = (width - scrollW + TABLET_W) / 2;
		paperX1 = paperX0 + scrollW;
		paperTop = (height - scrollH) / 2 + 5;
		paperBottom = paperTop + scrollH - 10;
		contentX = paperX0 + ScrollFrame.SIDE + 6;
		contentW = paperX1 - ScrollFrame.SIDE - 6 - contentX;
		contentY = paperTop + ScrollFrame.END + 16;
		contentH = paperBottom - ScrollFrame.END - 5 - contentY;

		tablets.clear();
		int count = pages.length;
		int top = paperTop + 10;
		int gap = height < 250 ? 2 : 3;
		int tabletH = Math.max(28, Math.min(58, (paperBottom - paperTop - 20 - gap * (count - 1)) / count));
		for (int i = 0; i < count; i++) {
			int index = i;
			CodexWidgets.Tablet tablet = new CodexWidgets.Tablet(paperX0 - TABLET_W + 1, top + i * (tabletH + gap), TABLET_W, tabletH,
					pages[i].icon(), pages[i].red(), () -> pages[index].shortName(), () -> tab == index);
			tablet.onPress(() -> switchTab(index));
			tablet.tooltip(() -> List.of(pages[index].title()));
			tablets.add(tablet);
		}
		for (CodexPage page : pages) {
			page.layout(contentX, contentY, contentW, contentH);
		}
		seenVersion = ClientCultivation.version();
	}

	/** The Ensiklopedia topic of the page shown now (the header's "?" seal). */
	private String topic() {
		return pages[tab].helpTopic();
	}

	@Override
	public void tick() {
		super.tick();
		if (helpPending) {
			helpPending = false;
			HubNav.help("kitab_teknik");
		}
	}

	/** Shows {@code lines} as this frame's tooltip (the first one asked for wins). */
	void showTooltip(List<Component> lines) {
		if (tooltip == null && !lines.isEmpty()) {
			tooltip = lines;
		}
	}

	/** Opens the Kodeks on {@code spec}. */
	public void focus(TechniqueSpec spec) {
		switchTab(TAB_KODEKS);
		kodeks.focus(spec);
	}

	private void switchTab(int index) {
		if (index == tab || index < 0 || index >= pages.length) {
			return;
		}
		tab = index;
		lastTab = index;
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
		int step = Math.max(2, width / 160);
		int far = Palette.argb(Math.round(0x70 * fade), Palette.MOUNTAIN);
		int close = Palette.argb(Math.round(0x90 * fade), Palette.NIGHT);
		for (int x = 0; x < width; x += step) {
			double ridge = Math.sin(x * 0.019D + 0.6D) * 13.0D + Math.sin(x * 0.047D + 2.2D) * 6.0D;
			int peak = (int) Math.round(height * 0.19D + ridge);
			int low = Math.max(0, (int) Math.round(height * 0.09D + Math.sin(x * 0.031D + 1.1D) * 8.0D));
			if (peak > low) {
				g.fill(x, height - peak, x + step, height - low, far);
			}
			g.fill(x, height - low, x + step, height, close);
		}
		g.nextStratum();
		Brush.halo(g, width * 0.16F, height * 0.17F, 8.0F, 7.0F, 0xE8EEF5, 0.3F * fade);
		Brush.disc(g, width * 0.16F, height * 0.17F, 8.0F, Palette.argb(Math.round(0x90 * fade), 0xE8EEF5));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		float seconds = Math.max(0.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F));
		lastFrame = now;
		tooltip = null;
		long version = ClientCultivation.version();
		if (version != seenVersion) {
			seenVersion = version;
			pages[tab].onData();
		}
		float open = unroll();
		int mid = (paperTop + paperBottom) / 2;
		int half = Math.round((paperBottom - paperTop) / 2.0F * open);
		int top = mid - half;
		int bottom = mid + half;

		float tabletsOut = Eased.out(Math.min(1.0F, Math.max(0.0F, (now - openedAt - 150L) / 220.0F)));
		g.pose().pushMatrix();
		g.pose().translate((1.0F - tabletsOut) * (TABLET_W - 2), 0.0F);
		for (CodexWidgets.Tablet tablet : tablets) {
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
			help.render(g, font, mouseX, mouseY, seconds);
			pages[tab].render(g, mouseX, mouseY, seconds);
			g.disableScissor();
		}
		for (CodexWidgets.Tablet tablet : tablets) {
			if (tabletsOut > 0.9F && tablet.contains(mouseX, mouseY)) {
				showTooltip(tablet.tooltipLines());
			}
		}
		if (bottom - top > 24 && help.contains(mouseX, mouseY)) {
			showTooltip(help.tooltipLines());
		}
		if (tooltip != null && open >= 1.0F) {
			g.nextStratum();
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	/** "Kitab Teknik · Kodeks" between two gold rules, with a seal at each end (blood red on the Kehancuran page). */
	private void drawHeader(GuiGraphicsExtractor g) {
		boolean red = pages[tab].red();
		Component text = Brush.bold(Component.translatable("kultivator.codex.header", title, pages[tab].title()));
		float y = paperTop + ScrollFrame.END + 4.0F;
		float cx = (paperX0 + paperX1) / 2.0F;
		float room = contentW - 56.0F;
		float scale = Brush.fitScale(font, text, room, 1.0F, 0.6F);
		float textW = Math.min(room, font.width(text) * scale);
		Brush.fitCentered(g, font, text, cx, y, room, red ? Palette.SEAL_DARK : Palette.INK, 1.0F, 0.6F, false);
		float lineY = y + 4.0F * scale;
		float left = cx - textW / 2.0F - 6.0F;
		float right = cx + textW / 2.0F + 6.0F;
		int rule = red ? Palette.SEAL : Palette.GOLD;
		Brush.rect(g, contentX + 8.0F, lineY - 0.5F, left - 4.0F, lineY + 0.5F, rule);
		Brush.rect(g, right + 4.0F, lineY - 0.5F, contentX + contentW - 20.0F, lineY + 0.5F, rule);
		Brush.rect(g, left - 3.0F, lineY - 2.0F, left + 1.0F, lineY + 2.0F, Palette.SEAL);
		Brush.rect(g, right - 1.0F, lineY - 2.0F, right + 3.0F, lineY + 2.0F, Palette.SEAL);
		Glyphs.tab(g, pages[tab].icon(), contentX + 4.0F, lineY, 6.0F, red ? Palette.SEAL_DARK : Palette.GOLD_DARK);
		Glyphs.tab(g, pages[tab].icon(), contentX + contentW - 15.0F, lineY, 6.0F, red ? Palette.SEAL_DARK : Palette.GOLD_DARK);
		// The "?" seal at the right end (its own box, clear of the title and the rules).
		help.moveTo(contentX + contentW - 4.0F, lineY);
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			if (unroll() >= 1.0F && help.contains(event.x(), event.y())) {
				help.press();
				return true;
			}
			for (CodexWidgets.Tablet tablet : tablets) {
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
		if (help.release(event.x(), event.y())) {
			return true;
		}
		if (pressedTablet != null) {
			CodexWidgets.Tablet tablet = pressedTablet;
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
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (pages[tab].mouseDragged(event.x(), event.y(), event.button())) {
			return true;
		}
		return super.mouseDragged(event, dx, dy);
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
		if (event.key() == InputConstants.KEY_ESCAPE) {
			if (pages[tab].escape()) {
				return true;
			}
			onClose();
			return true;
		}
		if (pages[tab].keyPressed(event)) {
			return true;
		}
		if (Keys.CODEX.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (pages[tab].charTyped(event)) {
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
