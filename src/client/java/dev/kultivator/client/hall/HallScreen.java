package dev.kultivator.client.hall;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.JadeTablet;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.UiSound;
import net.minecraft.ChatFormatting;
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
 * edge. Since DESIGN 33.6 R8 the 13 pages sit on 8 tablets, related pages together as sub-coins in the header strip (no
 * tablet scrolling at 240 px): Aula (Aula · Catatan), Bangun (phase 6b), Formasi (phase 7c), Anggota (Calon Murid ·
 * Jenjang), Perbendaharaan, Kegiatan (Tugas · Misi · Arena), Wilayah (Pertahanan · Desa) and Segel Merah Murid (module D,
 * only while {@code HallInfo.cfg.kehancuran} is true). The {@code Halls.PAGE_*} ids are unchanged: a page id from the
 * server lands on its tablet and sub-coin. Keys 1-8 pick a tablet, Tab / Shift+Tab the sub-coins; a "?" seal at the
 * right end of the header opens Panduan › Ensiklopedia on the page's topic. Reads the last {@code HallInfo} from
 * {@link HallClient} and asks for fresh details every few seconds while open. Not a pause screen.
 */
public class HallScreen extends Screen {

	private static final long UNROLL_MILLIS = 250L;
	private static final int TABLET_W = 20;
	private static final int MAX_W = 320;
	private static final int MAX_H = 240;
	private static final int MIN_TABLET_H = 18;
	private static final int MAX_TABLET_H = 40;
	private static final int COIN_H = 10;

	/** One tablet: its name ({@code null}: the only page's own name) and its pages by {@code Halls.PAGE_*} id, in sub-coin order. */
	private record Group(@Nullable String name, int... ids) {
	}

	/** The 8 tablets (page ids: 0 Aula, 1 Bangun, 2 Calon, 3 Jenjang, 4 Perbendaharaan, 5 Tugas, 6 Misi, 7 Arena, 8 Catatan, 9 Segel Merah, 10 Formasi, 11 Pertahanan, 12 Desa). */
	private static final Group[] GROUPS = {
			new Group("aula", 0, 8),
			new Group(null, 1),
			new Group(null, 10),
			new Group("anggota", 2, 3),
			new Group(null, 4),
			new Group("kegiatan", 5, 6, 7),
			new Group("wilayah", 11, 12),
			new Group(null, 9)};
	/** The Ensiklopedia topic of each page id (the "?" seal). */
	private static final String[] TOPICS = {"aula", "bangun", "calon", "jenjang", "kontribusi", "tugas", "misi", "turnamen", "aula", "kehancuran", "formasi",
			"serbuan", "desa"};
	/** The sub-coin last chosen on each tablet (by {@link #GROUPS} index), for this session. */
	private static final int[] LAST_SUB = new int[GROUPS.length];

	/** Every page by {@code Halls.PAGE_*} index (the server's page ids). */
	private final List<HallPage> allPages = new ArrayList<>();
	/** The tablets shown now (a tablet with no shown page is left out) with their shown pages. */
	private final List<Shown> shown = new ArrayList<>();
	private final List<JadeTablet> tablets = new ArrayList<>();
	private final List<JadeCoin> subCoins = new ArrayList<>();
	private final Eased backdrop = new Eased(6.0F);
	private final KeyHints.HelpSeal help = new KeyHints.HelpSeal(0.0F, 0.0F, 10, this::topic);
	private CompoundTag info;
	private long seenVersion;
	private int group;
	private int sub;
	private long openedAt;
	private long lastFrame;
	private boolean opened;
	private @Nullable JadeTablet pressedTablet;
	private @Nullable JadeCoin pressedCoin;
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

	/** A tablet that is shown, with the {@link #GROUPS} index it comes from. */
	private record Shown(int index, List<HallPage> pages) {
	}

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
		// Phase 6b (DESIGN 21.21, 21.24): the Bangun page.
		allPages.add(new dev.kultivator.client.sectbuild.BuildPage(this));
		allPages.add(new CalonPage(this));
		allPages.add(new JenjangPage(this));
		allPages.add(new TreasuryPage(this));
		allPages.add(new dev.kultivator.client.sectevent.DutyPage(this));
		allPages.add(new dev.kultivator.client.sectevent.MissionPage(this));
		allPages.add(new dev.kultivator.client.sectevent.ArenaPage(this));
		allPages.add(new LogPage(this));
		allPages.add(new dev.kultivator.client.kehancuran.RedSealPage(this));
		// Phase 7c (DESIGN 24.13 item 6): the Formasi page is index Halls.PAGE_FORMASI (10), its tablet third.
		allPages.add(new dev.kultivator.client.formasi.FormasiPage(this));
		// Phase 8 (DESIGN 25.2 item 3): Pertahanan (Halls.PAGE_PERTAHANAN = 11, module B) and Desa (PAGE_DESA = 12, module D).
		allPages.add(new dev.kultivator.client.musuh.PertahananPage(this));
		allPages.add(new dev.kultivator.client.desa.HallDesaPage(this));
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

	// ================================================================== pages and tablets

	private @Nullable HallPage current() {
		if (group < 0 || group >= shown.size()) {
			return null;
		}
		List<HallPage> pages = shown.get(group).pages();
		return pages.isEmpty() ? null : pages.get(Math.max(0, Math.min(pages.size() - 1, sub)));
	}

	/** Puts {@link #group} and {@link #sub} on {@code page}; false when it is not shown. */
	private boolean locate(@Nullable HallPage page) {
		if (page == null) {
			return false;
		}
		for (int i = 0; i < shown.size(); i++) {
			int at = shown.get(i).pages().indexOf(page);
			if (at >= 0) {
				group = i;
				sub = at;
				return true;
			}
		}
		return false;
	}

	private int shownCount() {
		int count = 0;
		for (HallPage each : allPages) {
			if (each.shown()) {
				count++;
			}
		}
		return count;
	}

	private Component groupTitle(Shown each) {
		String name = GROUPS[each.index()].name();
		return name != null ? Component.translatable("kultivator.uxpages.hall.group." + name) : each.pages().getFirst().title();
	}

	private Component groupShort(Shown each) {
		String name = GROUPS[each.index()].name();
		return name != null ? Component.translatable("kultivator.uxpages.hall.group." + name + ".short") : each.pages().getFirst().shortName();
	}

	private String topic() {
		HallPage page = current();
		int id = page == null ? 0 : allPages.indexOf(page);
		return TOPICS[Math.max(0, Math.min(TOPICS.length - 1, id))];
	}

	@Override
	protected void init() {
		if (!opened) {
			opened = true;
			openedAt = Util.getMillis();
			lastFrame = openedAt;
			UiSound.open();
		}
		HallPage before = current();
		shown.clear();
		for (int i = 0; i < GROUPS.length; i++) {
			List<HallPage> pages = new ArrayList<>();
			for (int id : GROUPS[i].ids()) {
				if (id < allPages.size() && allPages.get(id).shown()) {
					pages.add(allPages.get(id));
				}
			}
			if (!pages.isEmpty()) {
				shown.add(new Shown(i, pages));
			}
		}
		boolean placed = requestedPage >= 0 && requestedPage < allPages.size() && locate(allPages.get(requestedPage));
		if (!placed && !locate(before)) {
			group = Math.max(0, Math.min(group, shown.size() - 1));
			sub = shown.isEmpty() ? 0 : Math.max(0, Math.min(sub, shown.get(group).pages().size() - 1));
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
		layoutSubCoins();
		for (Shown each : shown) {
			for (HallPage page : each.pages()) {
				page.layout(contentX, contentY, contentW, contentH);
			}
		}
	}

	/** One tablet per shown group, all fitting the column (no scrolling). */
	private void layoutTablets() {
		tablets.clear();
		int count = shown.size();
		if (count == 0) {
			return;
		}
		int gap = height < 250 ? 2 : 3;
		int room = tabletBottom - tabletTop;
		int tabletH = Math.max(MIN_TABLET_H, Math.min(MAX_TABLET_H, (room - gap * (count - 1)) / count));
		for (int i = 0; i < count; i++) {
			int index = i;
			Shown each = shown.get(i);
			JadeTablet tablet = new JadeTablet(paperX0 - TABLET_W + 1, tabletTop + i * (tabletH + gap), TABLET_W, tabletH,
					each.pages().getFirst().icon(), () -> groupShort(each), () -> group == index);
			tablet.onPress(() -> switchGroup(index));
			tablet.tooltip(() -> tabletTip(each, index));
			tablets.add(tablet);
		}
	}

	private List<Component> tabletTip(Shown each, int index) {
		List<Component> lines = new ArrayList<>();
		lines.add(groupTitle(each));
		if (each.pages().size() > 1) {
			List<String> names = new ArrayList<>();
			for (HallPage page : each.pages()) {
				names.add(page.title().getString());
			}
			lines.add(Component.literal(String.join(" · ", names)).withStyle(ChatFormatting.GRAY));
		}
		if (index < 9) {
			lines.add(Component.translatable("kultivator.spine6.hall.key", index + 1).withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	/** The sub-coins of the current tablet, right of the title in the header strip (none for a one-page tablet). */
	private void layoutSubCoins() {
		subCoins.clear();
		pressedCoin = null;
		if (group < 0 || group >= shown.size()) {
			return;
		}
		List<HallPage> pages = shown.get(group).pages();
		if (pages.size() < 2) {
			return;
		}
		float x0 = contentX + contentW * 0.44F;
		float x1 = contentX + contentW - 13.0F;
		float each = (x1 - x0) / pages.size();
		int coinY = Math.round(headerLineY() - COIN_H / 2.0F);
		for (int i = 0; i < pages.size(); i++) {
			int index = i;
			HallPage page = pages.get(i);
			JadeCoin coin = new JadeCoin(Math.round(x0 + i * each), coinY, Math.max(COIN_H + 8, Math.round(each) - 3), COIN_H, page::shortName,
					() -> sub == index);
			coin.onPress(() -> switchSub(index));
			coin.tooltip(() -> List.of(page.title(), Component.translatable("kultivator.uxpages.hall.sub.tip").withStyle(ChatFormatting.DARK_GRAY)));
			subCoins.add(coin);
		}
	}

	private float headerTextY() {
		return paperTop + ScrollFrame.END + 5.0F;
	}

	private float headerLineY() {
		return headerTextY() + 4.0F;
	}

	/** The server asked for page {@code index} of all pages while the scroll is open (a candidate was clicked). */
	public void showPage(int index) {
		if (index < 0 || index >= allPages.size()) {
			return;
		}
		HallPage target = allPages.get(index);
		int oldGroup = group;
		int oldSub = sub;
		if (!locate(target)) {
			return;
		}
		if (group != oldGroup || sub != oldSub) {
			int newGroup = group;
			int newSub = sub;
			group = oldGroup;
			sub = oldSub;
			rememberSub();
			group = newGroup;
			sub = newSub;
			layoutSubCoins();
			target.layout(contentX, contentY, contentW, contentH);
		}
	}

	private void rememberSub() {
		if (group >= 0 && group < shown.size()) {
			LAST_SUB[shown.get(group).index()] = sub;
		}
	}

	private void switchGroup(int index) {
		if (index == group || index < 0 || index >= shown.size()) {
			return;
		}
		rememberSub();
		group = index;
		sub = Math.max(0, Math.min(shown.get(index).pages().size() - 1, LAST_SUB[shown.get(index).index()]));
		layoutSubCoins();
		HallPage page = current();
		if (page != null) {
			page.layout(contentX, contentY, contentW, contentH);
		}
	}

	private void switchSub(int index) {
		if (group < 0 || group >= shown.size()) {
			return;
		}
		List<HallPage> pages = shown.get(group).pages();
		if (index == sub || index < 0 || index >= pages.size()) {
			return;
		}
		sub = index;
		LAST_SUB[shown.get(group).index()] = sub;
		pages.get(sub).layout(contentX, contentY, contentW, contentH);
	}

	/** Picks up details that arrived since the last frame; the Segel Merah Murid or Desa page may appear or vanish. */
	private void refresh() {
		long version = HallClient.version();
		if (version == seenVersion) {
			return;
		}
		seenVersion = version;
		info = HallClient.info();
		int visible = 0;
		for (Shown each : shown) {
			visible += each.pages().size();
		}
		if (shownCount() != visible) {
			init();
			return;
		}
		HallPage current = current();
		if (current == null) {
			return;
		}
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
		g.pose().popMatrix();
		g.nextStratum();

		ScrollFrame.paper(g, paperX0, paperX1, top, bottom, paperTop, paperBottom);
		ScrollFrame.rod(g, paperX0, paperX1, top);
		ScrollFrame.rod(g, paperX0, paperX1, bottom);
		g.nextStratum();

		HallPage current = current();
		if (bottom - top > 24 && current != null) {
			g.enableScissor(paperX0 + ScrollFrame.SIDE + 1, top + ScrollFrame.END + 1, paperX1 - ScrollFrame.SIDE - 1, bottom - ScrollFrame.END - 1);
			drawHeader(g, current, mouseX, mouseY, seconds);
			if (info.getBooleanOr("has", true)) {
				current.render(g, mouseX, mouseY, seconds);
			} else {
				// No hall yet: how to found one.
				Brush.wrap(g, font, Component.translatable("kultivator.hall.ui.no_hall"), contentX + 6.0F, contentY + 10.0F, contentW - 12.0F,
						Palette.INK_SOFT, 0.85F, false);
			}
			g.disableScissor();
		}

		if (open >= 1.0F) {
			for (JadeTablet tablet : tablets) {
				if (tabletsOut > 0.9F && tablet.contains(mouseX, mouseY)) {
					showTooltip(tablet.tooltipLines());
				}
			}
			for (JadeCoin coin : subCoins) {
				if (coin.contains(mouseX, mouseY)) {
					showTooltip(coin.tooltipLines());
				}
			}
			if (help.contains(mouseX, mouseY)) {
				showTooltip(help.tooltipLines());
			}
		}
		if (tooltip != null && open >= 1.0F) {
			g.nextStratum();
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	/**
	 * One-page tablet: "&lt;hall name&gt; · &lt;page&gt;" centred between two gold rules, a seal at each end and the page's
	 * mark in the corners. A tablet with sub-coins: the title on the left, the coins on the right. The "?" seal always sits
	 * in its own box at the right end.
	 */
	private void drawHeader(GuiGraphicsExtractor g, HallPage current, int mouseX, int mouseY, float seconds) {
		float y = headerTextY();
		float lineY = headerLineY();
		if (subCoins.isEmpty()) {
			Component text = Brush.bold(Component.translatable("kultivator.spine6.hall.header", hallName(), current.title()));
			float cx = (paperX0 + paperX1) / 2.0F;
			float room = contentW - 56.0F;
			float scale = Brush.fitScale(font, text, room, 1.0F, 0.6F);
			float textW = Math.min(room, font.width(text) * scale);
			Brush.fitCentered(g, font, text, cx, y, room, Palette.INK, 1.0F, 0.6F, false);
			float left = cx - textW / 2.0F - 6.0F;
			float right = cx + textW / 2.0F + 6.0F;
			Brush.rect(g, contentX + 8.0F, lineY - 0.5F, left - 4.0F, lineY + 0.5F, Palette.GOLD);
			Brush.rect(g, right + 4.0F, lineY - 0.5F, contentX + contentW - 20.0F, lineY + 0.5F, Palette.GOLD);
			Brush.rect(g, left - 3.0F, lineY - 2.0F, left + 1.0F, lineY + 2.0F, Palette.SEAL);
			Brush.rect(g, right - 1.0F, lineY - 2.0F, right + 3.0F, lineY + 2.0F, Palette.SEAL);
			Glyphs.tab(g, current.icon(), contentX + contentW - 15.0F, lineY, 6.0F, Palette.GOLD_DARK);
		} else {
			// The lit coin names the page, so the title is the hall's name alone.
			float room = contentW * 0.44F - 16.0F;
			Brush.fit(g, font, Brush.bold(hallName()), contentX + 10.0F, y, room, Palette.INK, 1.0F, 0.6F, false);
			// A gold rule under the whole strip ties the title and its coins together.
			Brush.rect(g, contentX + 8.0F, lineY + 7.0F, contentX + contentW - 8.0F, lineY + 7.6F, Palette.argb(0xB0, Palette.GOLD));
			for (JadeCoin coin : subCoins) {
				coin.render(g, font, mouseX, mouseY, seconds);
			}
		}
		Glyphs.tab(g, current.icon(), contentX + 4.0F, lineY, 6.0F, Palette.GOLD_DARK);
		help.moveTo(contentX + contentW - 4.0F, lineY);
		help.render(g, font, mouseX, mouseY, seconds);
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && unroll() >= 1.0F) {
			if (help.contains(event.x(), event.y())) {
				help.press();
				return true;
			}
			for (JadeCoin coin : subCoins) {
				if (coin.contains(event.x(), event.y())) {
					coin.press();
					pressedCoin = coin;
					return true;
				}
			}
			for (JadeTablet tablet : tablets) {
				if (tablet.contains(event.x(), event.y())) {
					tablet.press();
					pressedTablet = tablet;
					return true;
				}
			}
		}
		HallPage current = current();
		if (current != null && current.mouseClicked(event.x(), event.y(), event.button())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (help.release(event.x(), event.y())) {
			return true;
		}
		if (pressedCoin != null) {
			JadeCoin coin = pressedCoin;
			pressedCoin = null;
			coin.release(event.x(), event.y());
			return true;
		}
		if (pressedTablet != null) {
			JadeTablet tablet = pressedTablet;
			pressedTablet = null;
			tablet.release(event.x(), event.y());
			return true;
		}
		HallPage current = current();
		if (current != null && current.mouseReleased(event.x(), event.y(), event.button())) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		HallPage current = current();
		if (current != null && current.mouseScrolled(x, y, scrollY)) {
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		HallPage current = current();
		if (current != null && current.capturesKeys()) {
			// A text field has the keyboard: it gets every key (Escape lets it go first).
			if (current.keyPressed(event)) {
				return true;
			}
			return event.key() == InputConstants.KEY_ESCAPE ? super.keyPressed(event) : true;
		}
		int number = event.key() - InputConstants.KEY_1;
		if (number >= 0 && number < Math.min(9, shown.size()) && event.key() <= InputConstants.KEY_9) {
			if (number != group) {
				UiSound.tab();
				switchGroup(number);
			}
			return true;
		}
		if (event.key() == InputConstants.KEY_TAB && group >= 0 && group < shown.size()) {
			int count = shown.get(group).pages().size();
			if (count > 1) {
				UiSound.tab();
				switchSub(Math.floorMod(sub + (event.hasShiftDown() ? -1 : 1), count));
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
		HallPage current = current();
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
