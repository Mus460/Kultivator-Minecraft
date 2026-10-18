package dev.kultivator.client.hall;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Sect Hall scroll (DESIGN 20.3.1, 5b): the jade scroll at most 320 x 240 with vertical jade tablets on its left
 * edge for the pages Aula, Bangun (phase 6b), Formasi (phase 7c), Calon, Jenjang, Perbendaharaan (module A), Tugas, Misi, Arena (module B), Catatan (A) and
 * Segel Merah (module D, only while {@code HallInfo.cfg.kehancuran} is true). When fewer than all tablets fit, the
 * column scrolls with the mouse wheel. Reads the last {@code HallInfo} from {@link HallClient} and asks for fresh details
 * every few seconds while open. Not a pause screen.
 */
public class HallScreen extends Screen {

	private static final long UNROLL_MILLIS = 250L;
	private static final int TABLET_W = 20;
	private static final int MAX_W = 320;
	private static final int MAX_H = 240;
	private static final int MIN_TABLET_H = 24;
	private static final int MAX_TABLET_H = 40;

	/** Every page by {@code Halls.PAGE_*} index (the server's page ids). */
	private final List<HallPage> allPages = new ArrayList<>();
	/** The same pages in tablet order (the Formasi tablet sits third, DESIGN 24.13 item 6). */
	private final List<HallPage> drawOrder = new ArrayList<>();
	private final List<HallPage> pages = new ArrayList<>();
	private final List<JadeTablet> tablets = new ArrayList<>();
	private final Eased backdrop = new Eased(6.0F);
	private CompoundTag info;
	private long seenVersion;
	private int page;
	private int tabletScroll;
	private int tabletsFit;
	private long openedAt;
	private long lastFrame;
	private boolean opened;
	private @Nullable JadeTablet pressedTablet;
	private @Nullable List<Component> tooltip;
	/** The page the server asked for, applied when the pages are laid out (-1: none pending). */
	private int requestedPage = -1;
	private long lastRefresh;
	/** The scroll asks for fresh details this often while open (countdowns, arrivals, the inventory's offers). */
	private static final long REFRESH_MILLIS = 4000L;

	private int paperX0;
	private int paperX1;
	private int paperTop;
	private int paperBottom;
	private int contentX;
	private int contentY;
	private int contentW;
	private int contentH;
	private int tabletTop;
	private int tabletBottom;

	public HallScreen() {
		this(0);
	}

	/** Opens on page {@code initialPage} ({@code Halls.PAGE_*}: 0 Aula, 1 Bangun, 2 Calon, 3 Jenjang, 4 Perbendaharaan, ...). */
	public HallScreen(int initialPage) {
		super(Component.translatable("kultivator.spine6.hall.title"));
		this.info = HallClient.info();
		this.seenVersion = HallClient.version();
		this.requestedPage = Math.max(0, initialPage);
		allPages.add(new AulaPage(this));
		// Phase 6b (DESIGN 21.21, 21.24): the Bangun tablet, second after Aula.
		allPages.add(new dev.kultivator.client.sectbuild.BuildPage(this));
		allPages.add(new CalonPage(this));
		allPages.add(new JenjangPage(this));
		allPages.add(new TreasuryPage(this));
		allPages.add(new dev.kultivator.client.sectevent.DutyPage(this));
		allPages.add(new dev.kultivator.client.sectevent.MissionPage(this));
		allPages.add(new dev.kultivator.client.sectevent.ArenaPage(this));
		allPages.add(new LogPage(this));
		allPages.add(new dev.kultivator.client.kehancuran.RedSealPage(this));
		// Phase 7c (DESIGN 24.13 item 6): the Formasi tablet is index Halls.PAGE_FORMASI (10) of allPages, drawn third.
		HallPage formasi = new dev.kultivator.client.formasi.FormasiPage(this);
		allPages.add(formasi);
		drawOrder.addAll(allPages);
		drawOrder.remove(formasi);
		drawOrder.add(Math.min(2, drawOrder.size()), formasi);
	}

	/** The last hall details (never null). */
	public CompoundTag info() {
		return info;
	}

	/** Shows {@code lines} as this frame's tooltip (drawn last, over everything). */
	public void showTooltip(List<Component> lines) {
		if (tooltip == null && !lines.isEmpty()) {
			tooltip = lines;
		}
	}

	/** The hall's name from the details, else the screen title. */
	public Component hallName() {
		Component shown = info.read("name_shown", ComponentSerialization.CODEC).orElse(null);
		if (shown != null) {
			return shown;
		}
		String name = info.getStringOr("name", "");
		return name.isEmpty() ? Component.translatable("kultivator.spine6.hall.title") : Component.literal(name);
	}

	@Override
	protected void init() {
		if (!opened) {
			opened = true;
			openedAt = Util.getMillis();
			lastFrame = openedAt;
			UiSound.open();
		}
		HallPage current = pages.isEmpty() ? null : pages.get(Math.min(page, pages.size() - 1));
		pages.clear();
		for (HallPage each : drawOrder) {
			if (each.shown()) {
				pages.add(each);
			}
		}
		page = current == null ? 0 : Math.max(0, pages.indexOf(current));
		if (requestedPage >= 0 && requestedPage < allPages.size()) {
			int index = pages.indexOf(allPages.get(requestedPage));
			if (index >= 0) {
				page = index;
				tabletScroll = Math.max(0, index - 2);
			}
		}
		requestedPage = -1;
		lastRefresh = Util.getMillis();

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
		tabletTop = paperTop + 10;
		tabletBottom = paperBottom - 10;
		layoutTablets();
		for (HallPage each : pages) {
			each.layout(contentX, contentY, contentW, contentH);
		}
	}

	/** As many tablets as fit (at least 24 px each); the rest are reached by scrolling the column. */
	private void layoutTablets() {
		tablets.clear();
		int count = pages.size();
		if (count == 0) {
			return;
		}
		int gap = height < 250 ? 2 : 3;
		int room = tabletBottom - tabletTop;
		tabletsFit = Math.max(1, Math.min(count, (room + gap) / (MIN_TABLET_H + gap)));
		tabletScroll = Math.max(0, Math.min(tabletScroll, count - tabletsFit));
		int tabletH = Math.max(MIN_TABLET_H, Math.min(MAX_TABLET_H, (room - gap * (tabletsFit - 1)) / tabletsFit));
		for (int i = 0; i < tabletsFit; i++) {
			int index = tabletScroll + i;
			HallPage each = pages.get(index);
			JadeTablet tablet = new JadeTablet(paperX0 - TABLET_W + 1, tabletTop + i * (tabletH + gap), TABLET_W, tabletH, each.icon(),
					each::shortName, () -> page == index);
			tablet.onPress(() -> switchPage(index));
			tablet.tooltip(() -> index < 9 ? List.of(each.title(), Component.translatable("kultivator.spine6.hall.key", index + 1))
					: List.of(each.title()));
			tablets.add(tablet);
		}
	}

	/** The server asked for page {@code index} of all pages while the scroll is open (a candidate was clicked). */
	public void showPage(int index) {
		if (index >= 0 && index < allPages.size()) {
			int shown = pages.indexOf(allPages.get(index));
			if (shown >= 0) {
				switchPage(shown);
			}
		}
	}

	private void switchPage(int index) {
		if (index == page || index < 0 || index >= pages.size()) {
			return;
		}
		page = index;
		if (page < tabletScroll || page >= tabletScroll + tabletsFit) {
			tabletScroll = Math.max(0, Math.min(page, pages.size() - tabletsFit));
			layoutTablets();
		}
		pages.get(page).layout(contentX, contentY, contentW, contentH);
	}

	/** Picks up details that arrived since the last frame; the Segel Merah tablet may appear or vanish. */
	private void refresh() {
		long version = HallClient.version();
		if (version == seenVersion) {
			return;
		}
		seenVersion = version;
		info = HallClient.info();
		int shown = 0;
		for (HallPage each : allPages) {
			if (each.shown()) {
				shown++;
			}
		}
		if (shown != pages.size()) {
			init();
			return;
		}
		if (pages.isEmpty()) {
			return;
		}
		HallPage current = pages.get(page);
		if (current.needsRebuild()) {
			current.layout(contentX, contentY, contentW, contentH);
		} else {
			current.onInfo();
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
		// Far mountains and the roofs of a sect along the bottom.
		int step = Math.max(2, width / 160);
		int far = Palette.argb(Math.round(0x70 * fade), Palette.MOUNTAIN);
		int close = Palette.argb(Math.round(0x90 * fade), Palette.NIGHT);
		for (int x = 0; x < width; x += step) {
			double ridge = Math.sin(x * 0.013D + 0.4D) * 14.0D + Math.sin(x * 0.041D + 1.7D) * 5.0D;
			int peak = (int) Math.round(height * 0.2D + ridge);
			int low = Math.max(0, (int) Math.round(height * 0.07D + Math.sin(x * 0.029D + 0.6D) * 6.0D));
			if (peak > low) {
				g.fill(x, height - peak, x + step, height - low, far);
			}
			g.fill(x, height - low, x + step, height, close);
		}
		g.nextStratum();
		Brush.halo(g, width * 0.84F, height * 0.16F, 7.0F, 7.0F, 0xE8EEF5, 0.3F * fade);
		Brush.disc(g, width * 0.84F, height * 0.16F, 7.0F, Palette.argb(Math.round(0x88 * fade), 0xE8EEF5));
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
		if (now - lastRefresh >= REFRESH_MILLIS) {
			lastRefresh = now;
			HallClient.send(dev.kultivator.sect.hall.Halls.OP_REFRESH, "");
		}

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
		if (tabletsOut > 0.9F && pages.size() > tabletsFit) {
			// Little arrows when the column scrolls.
			float ax = paperX0 - TABLET_W / 2.0F;
			if (tabletScroll > 0) {
				Brush.centered(g, font, Component.literal("▲"), ax, tabletTop - 9.0F, Palette.GOLD_LIGHT, 0.6F, false);
			}
			if (tabletScroll + tabletsFit < pages.size()) {
				Brush.centered(g, font, Component.literal("▼"), ax, tabletBottom + 3.0F, Palette.GOLD_LIGHT, 0.6F, false);
			}
		}
		g.pose().popMatrix();
		g.nextStratum();

		ScrollFrame.paper(g, paperX0, paperX1, top, bottom, paperTop, paperBottom);
		ScrollFrame.rod(g, paperX0, paperX1, top);
		ScrollFrame.rod(g, paperX0, paperX1, bottom);
		g.nextStratum();

		if (bottom - top > 24 && !pages.isEmpty()) {
			g.enableScissor(paperX0 + ScrollFrame.SIDE + 1, top + ScrollFrame.END + 1, paperX1 - ScrollFrame.SIDE - 1, bottom - ScrollFrame.END - 1);
			drawHeader(g);
			if (info.getBooleanOr("has", true)) {
				pages.get(page).render(g, mouseX, mouseY, seconds);
			} else {
				// No hall yet: how to found one.
				Brush.wrap(g, font, Component.translatable("kultivator.hall.ui.no_hall"), contentX + 6.0F, contentY + 10.0F, contentW - 12.0F,
						Palette.INK_SOFT, 0.85F, false);
			}
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

	/** "&lt;hall name&gt; · &lt;page&gt;" between two gold rules, a seal at each end and the page's mark in the corners. */
	private void drawHeader(GuiGraphicsExtractor g) {
		HallPage current = pages.get(page);
		Component text = Brush.bold(Component.translatable("kultivator.spine6.hall.header", hallName(), current.title()));
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
		if (!pages.isEmpty() && pages.get(page).mouseClicked(event.x(), event.y(), event.button())) {
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
		if (!pages.isEmpty() && pages.get(page).mouseReleased(event.x(), event.y(), event.button())) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (x < paperX0 && x >= paperX0 - TABLET_W - 8 && pages.size() > tabletsFit && scrollY != 0.0D) {
			int next = Math.max(0, Math.min(pages.size() - tabletsFit, tabletScroll + (scrollY < 0.0D ? 1 : -1)));
			if (next != tabletScroll) {
				tabletScroll = next;
				layoutTablets();
				UiSound.tab();
			}
			return true;
		}
		if (!pages.isEmpty() && pages.get(page).mouseScrolled(x, y, scrollY)) {
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		HallPage current = pages.isEmpty() ? null : pages.get(page);
		if (current != null && current.capturesKeys()) {
			// A text field has the keyboard: it gets every key (Escape lets it go first).
			if (current.keyPressed(event)) {
				return true;
			}
			return event.key() == InputConstants.KEY_ESCAPE ? super.keyPressed(event) : true;
		}
		int number = event.key() - InputConstants.KEY_1;
		if (number >= 0 && number < Math.min(9, pages.size())) {
			if (number != page) {
				UiSound.tab();
				switchPage(number);
			}
			return true;
		}
		if (current != null && current.keyPressed(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		HallPage current = pages.isEmpty() ? null : pages.get(page);
		if (current != null && current.capturesKeys() && current.charTyped(event)) {
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public void removed() {
		UiSound.close();
		HallClient.closed();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
