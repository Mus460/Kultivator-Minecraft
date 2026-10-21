package dev.kultivator.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.hub.HubNav;
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
import net.minecraft.ChatFormatting;
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
 * The Gulungan Kultivasi, since UI/UX Terpadu (DESIGN 33.3) the one hub of the mod, in the "Gulungan Giok" (jade scroll)
 * style (DESIGN 5b, 12): a scroll that unrolls in a quarter second over an ink-blue night with distant mountains, eight
 * jade tablets on its left edge as the sections in play order (number keys 1-8), the section's sub-coins in the header
 * strip ({@code Tab} / {@code Shift+Tab}) and a "?" seal at its right end (Panduan › Ensiklopedia on the page's topic).
 * <ol>
 * <li>Diri: Status · Akar &amp; Dao · Jalan · Alkimia</li>
 * <li>Teknik: the Kitab Teknik launcher</li>
 * <li>Murid, 4 Sekte, 5 Dunia, 6 Keabadian: launcher cards ({@link HubCardsPage}), locked ones greyed with the reason</li>
 * <li>Panduan: Kitab Panduan · Ensiklopedia</li>
 * <li>Pengaturan: the settings page with its own groups as sub-coins (module G)</li>
 * </ol>
 * Not a pause screen; the quick key (V), the info key or Escape closes it. Screens opened from here come back here on Esc
 * ({@link HubNav}). The legacy tab ids of {@link #CultivationScreen(int)} (server {@code OpenScreen.INFO} args) stay valid.
 */
public class CultivationScreen extends Screen {

	public static final int TAB_STATUS = 0;
	public static final int TAB_ROOT_DAO = 1;
	public static final int TAB_TECHNIQUES = 2;
	public static final int TAB_SETTINGS = 3;
	public static final int TAB_HELP = 4;
	/** The Jalan page (P7-jalan); the server opens it with {@code Payloads.OpenScreen.TAB_JALAN}. */
	public static final int TAB_JALAN = 5;
	/** The Kitab Alkimia (P7-harta); the server opens it with {@code Payloads.OpenScreen.TAB_ALKIMIA}. */
	public static final int TAB_ALKIMIA = 6;

	private static final String[] SECTION_KEYS = {"diri", "teknik", "murid", "sekte", "dunia", "keabadian", "panduan", "pengaturan"};
	private static final int[] SECTION_GLYPHS = {Glyphs.STATUS, Glyphs.CODEX, Glyphs.TALENT, Glyphs.HALL, Glyphs.PATH, Glyphs.SEAL, Glyphs.HELP,
			Glyphs.SETTINGS};

	private static final long UNROLL_MILLIS = 250L;
	private static final int TABLET_W = 20;
	private static final int MAX_W = 440;
	private static final int MAX_H = 284;
	/** Below this much paper the tablets show their glyph only (the name moves to the tooltip, 33.3). */
	private static final int NAMED_TABLETS_MIN = 230;
	private static final int HELP_SEAL = 9;

	private final Page[][] sections;
	private final HelpPage help;
	private final List<JadeTablet> tablets = new ArrayList<>();
	private final Eased backdrop = new Eased(6.0F);
	private int section;
	private int sub;
	private long openedAt;
	private long lastFrame;
	private long seenVersion = Long.MIN_VALUE;
	private boolean opened;
	private @Nullable JadeTablet pressedTablet;
	private @Nullable List<Component> tooltip;
	private int mouseX;
	private int mouseY;
	private @Nullable String pendingHelp;

	private int paperX0;
	private int paperX1;
	private int paperTop;
	private int paperBottom;
	private int contentX;
	private int contentY;
	private int contentW;
	private int contentH;

	/** The header strip: the title box, one box per sub-coin and the "?" seal (each its own box: nothing overlaps). */
	private float headerY;
	private float titleX0;
	private float titleX1;
	private float[] coinX0 = new float[0];
	private float[] coinX1 = new float[0];
	private float helpX0;

	/** Opens on a legacy tab id (0 Status, 1 Akar &amp; Dao, 2 Teknik, 3 Pengaturan, 4 Bantuan, 5 Jalan, 6 Alkimia; 33.3). */
	public CultivationScreen(int legacyTab) {
		this(sectionOf(legacyTab), subOf(legacyTab), true);
	}

	private CultivationScreen(int section, int sub, boolean unused) {
		super(Component.translatable("kultivator.screen.title"));
		this.help = new HelpPage(this);
		this.sections = new Page[][]{
				{new StatusPage(this), new RootDaoPage(this), new JalanPage(this), new AlchemyBookPage(this)},
				{new TechniquesPage(this)},
				{new HubCardsPage(this, HubNav.MURID)},
				{new HubCardsPage(this, HubNav.SEKTE)},
				{new HubCardsPage(this, HubNav.DUNIA)},
				{new HubCardsPage(this, HubNav.KEABADIAN)},
				{new HubCardsPage(this, HubNav.PANDUAN), help},
				{new SettingsPage(this)}};
		this.section = Math.max(0, Math.min(HubNav.SECTIONS - 1, section));
		this.sub = Math.max(0, sub);
	}

	/** The hub on {@code section} ({@code HubNav.DIRI} ...) and sub-coin {@code sub}. */
	public static CultivationScreen hub(int section, int sub) {
		return new CultivationScreen(section, sub, true);
	}

	private static int sectionOf(int legacyTab) {
		return switch (legacyTab) {
			case TAB_TECHNIQUES -> HubNav.TEKNIK;
			case TAB_SETTINGS -> HubNav.PENGATURAN;
			case TAB_HELP -> HubNav.PANDUAN;
			default -> HubNav.DIRI;
		};
	}

	private static int subOf(int legacyTab) {
		return switch (legacyTab) {
			case TAB_ROOT_DAO -> 1;
			case TAB_JALAN -> 2;
			case TAB_ALKIMIA -> 3;
			case TAB_SETTINGS -> HubNav.lastSub(HubNav.PENGATURAN);
			case TAB_HELP -> HubNav.SUB_ENSIKLOPEDIA;
			default -> 0;
		};
	}

	// ================================================================== sections

	/** The section shown ({@code HubNav.DIRI} ...). */
	public int section() {
		return section;
	}

	/** The page on screen. */
	private Page page() {
		Page[] pages = sections[section];
		return pages[pages.length > 1 ? Math.min(sub, pages.length - 1) : 0];
	}

	/** The sub-coins of the section: its pages when it has several, else the page's own sub-names. */
	private List<Component> subNames() {
		Page[] pages = sections[section];
		if (pages.length > 1) {
			List<Component> names = new ArrayList<>(pages.length);
			for (Page page : pages) {
				names.add(page.shortName());
			}
			return names;
		}
		List<Component> own = pages[0].subNames();
		return own == null ? List.of() : own;
	}

	private @Nullable Component subTitle(int index) {
		Page[] pages = sections[section];
		if (pages.length > 1) {
			return index >= 0 && index < pages.length ? pages[index].title() : null;
		}
		List<Component> names = subNames();
		return index >= 0 && index < names.size() ? names.get(index) : null;
	}

	/** Shows {@code newSection} on sub-coin {@code newSub} (clamped). */
	public void showSection(int newSection, int newSub) {
		int s = Math.max(0, Math.min(HubNav.SECTIONS - 1, newSection));
		boolean changed = s != section;
		section = s;
		int count = subNames().size();
		int wanted = count == 0 ? 0 : Math.max(0, Math.min(count - 1, newSub));
		changed |= wanted != sub;
		sub = wanted;
		if (opened) {
			applySection(changed);
		}
	}

	/** Panduan › Ensiklopedia on {@code topic} ({@code HelpPage.focus}, module P). */
	public void showHelp(String topic) {
		showSection(HubNav.PANDUAN, HubNav.SUB_ENSIKLOPEDIA);
		if (opened) {
			help.focus(topic);
		} else {
			pendingHelp = topic;
		}
	}

	/** Lays the current page out, tells it its sub-coin and remembers the place for the session. */
	private void applySection(boolean relayout) {
		Page page = page();
		if (relayout) {
			page.layout(contentX, contentY, contentW, contentH);
		}
		if (sections[section].length == 1 && !subNames().isEmpty()) {
			page.showSub(sub);
		}
		if (page instanceof HubCardsPage cards) {
			cards.shown();
		}
		HubNav.remember(section, sub);
		layoutHeader();
	}

	private void stepSub(int step) {
		int count = subNames().size();
		if (count > 1) {
			UiSound.tab();
			showSection(section, Math.floorMod(sub + step, count));
		}
	}

	/** Shows {@code lines} as the tooltip of this frame (drawn last, on top of everything). */
	void showTooltip(List<Component> lines) {
		if (tooltip == null && !lines.isEmpty()) {
			tooltip = lines;
		}
	}

	private static Component sectionName(int index) {
		return Component.translatable("kultivator.hub.section." + SECTION_KEYS[index]);
	}

	// ================================================================== layout

	@Override
	protected void init() {
		boolean first = !opened;
		if (first) {
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
		int count = HubNav.SECTIONS;
		int top = paperTop + 10;
		int gap = height < 250 ? 2 : 3;
		int tabletH = Math.max(18, Math.min(54, (paperBottom - paperTop - 20 - gap * (count - 1)) / count));
		boolean named = paperBottom - paperTop >= NAMED_TABLETS_MIN;
		for (int i = 0; i < count; i++) {
			int index = i;
			JadeTablet tablet = new JadeTablet(paperX0 - TABLET_W + 1, top + i * (tabletH + gap), TABLET_W, tabletH, SECTION_GLYPHS[i],
					() -> named ? sectionName(index) : Component.empty(), () -> section == index);
			tablet.onPress(() -> showSection(index, HubNav.lastSub(index)));
			tablet.tooltip(() -> List.of(Brush.bold(sectionName(index)),
					Component.translatable("kultivator.hub.section." + SECTION_KEYS[index] + ".tip").withStyle(ChatFormatting.GRAY),
					Component.translatable("kultivator.hub.number_key", index + 1).withStyle(ChatFormatting.DARK_GREEN)));
			tablets.add(tablet);
		}
		for (Page[] pages : sections) {
			for (Page page : pages) {
				page.layout(contentX, contentY, contentW, contentH);
			}
		}
		int subs = subNames().size();
		sub = subs == 0 ? 0 : Math.min(sub, subs - 1);
		applySection(false);
		if (pendingHelp != null) {
			help.focus(pendingHelp);
			pendingHelp = null;
		}
		seenVersion = ClientCultivation.version();
	}

	/** Places the title, the sub-coins and the "?" seal in the header strip, each in its own box. */
	private void layoutHeader() {
		headerY = paperTop + ScrollFrame.END + 5.0F;
		float left = contentX + 10.0F;
		float right = contentX + contentW - 2.0F;
		helpX0 = right - HELP_SEAL;
		List<Component> names = subNames();
		if (names.isEmpty()) {
			titleX0 = left;
			titleX1 = helpX0 - 4.0F;
			coinX0 = new float[0];
			coinX1 = new float[0];
			return;
		}
		Component title = Brush.bold(sectionName(section));
		float titleW = Math.min(font.width(title) * 0.85F, (helpX0 - left) * 0.28F);
		titleX0 = left;
		titleX1 = left + Math.max(20.0F, titleW);
		float from = titleX1 + 6.0F;
		float to = helpX0 - 5.0F;
		float each = Math.max(8.0F, (to - from) / names.size());
		coinX0 = new float[names.size()];
		coinX1 = new float[names.size()];
		for (int i = 0; i < names.size(); i++) {
			coinX0[i] = from + each * i;
			coinX1[i] = from + each * (i + 1) - 2.0F;
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
		Page current = page();
		if (version != seenVersion) {
			seenVersion = version;
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
			current.render(g, mouseX, mouseY, seconds);
			g.disableScissor();
		}

		for (JadeTablet tablet : tablets) {
			if (tabletsOut > 0.9F && tablet.contains(mouseX, mouseY)) {
				showTooltip(tablet.tooltipLines());
			}
		}
		headerTooltip();
		if (tooltip != null && open >= 1.0F) {
			g.nextStratum();
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	/**
	 * The header strip. Without sub-coins: "Kultivasi · Status" centred between two gold rules (as before). With them:
	 * the section name on the left, then one small jade coin per sub-page (the shown one in gold), then the "?" seal.
	 */
	private void drawHeader(GuiGraphicsExtractor g) {
		float y = headerY;
		float lineY = y + 4.0F;
		int glyph = SECTION_GLYPHS[section];
		Glyphs.tab(g, glyph, contentX + 4.0F, lineY, 6.0F, Palette.GOLD_DARK);
		List<Component> names = subNames();
		if (names.isEmpty() || coinX0.length != names.size()) {
			Component text = Brush.bold(Component.translatable("kultivator.screen.header", title, page().title()));
			float cx = (titleX0 + titleX1) / 2.0F;
			float room = titleX1 - titleX0 - 30.0F;
			float scale = Brush.fitScale(font, text, room, 1.0F, 0.6F);
			float textW = Math.min(room, font.width(text) * scale);
			Brush.fitCentered(g, font, text, cx, y, room, Palette.INK, 1.0F, 0.6F, false);
			float rule = y + 4.0F * scale;
			float left = cx - textW / 2.0F - 6.0F;
			float right = cx + textW / 2.0F + 6.0F;
			Brush.rect(g, titleX0, rule - 0.5F, left - 4.0F, rule + 0.5F, Palette.GOLD);
			Brush.rect(g, right + 4.0F, rule - 0.5F, titleX1, rule + 0.5F, Palette.GOLD);
			Brush.rect(g, left - 3.0F, rule - 2.0F, left + 1.0F, rule + 2.0F, Palette.SEAL);
			Brush.rect(g, right - 1.0F, rule - 2.0F, right + 3.0F, rule + 2.0F, Palette.SEAL);
		} else {
			Brush.fit(g, font, Brush.bold(sectionName(section)), titleX0, y, titleX1 - titleX0, Palette.SEAL_DARK, 0.85F, 0.6F, false);
			Brush.rect(g, titleX0, y + 10.0F, helpX0 - 4.0F, y + 10.6F, Palette.argb(0xB0, Palette.GOLD));
			for (int i = 0; i < names.size(); i++) {
				drawCoin(g, names.get(i), coinX0[i], coinX1[i], y, i == sub);
			}
		}
		drawHelpSeal(g, y);
	}

	private void drawCoin(GuiGraphicsExtractor g, Component name, float x0, float x1, float y, boolean active) {
		boolean over = mouseX >= x0 && mouseX < x1 && mouseY >= y - 2 && mouseY < y + 10;
		float r = 3.2F;
		float cx = x0 + r + 0.5F;
		float cy = y + 3.5F;
		if (over && !active) {
			Brush.halo(g, cx, cy, r, 2.0F, 0x9CFFD0, 0.6F);
		}
		Brush.disc(g, cx, cy, r, active ? Palette.GOLD : Palette.JADE_DULL);
		Brush.disc(g, cx, cy, r - 0.8F, active ? Palette.JADE_LIGHT : Palette.mix(Palette.JADE_DULL, Palette.JADE, over ? 0.6F : 0.2F));
		Brush.rect(g, cx - 0.9F, cy - 0.9F, cx + 0.9F, cy + 0.9F, active ? Palette.GOLD_LIGHT : Palette.PAPER_SHADE);
		float tx = x0 + r * 2.0F + 3.0F;
		float room = x1 - tx;
		if (room < 6.0F) {
			return;
		}
		Component label = active ? Brush.bold(name) : name;
		if (font.width(label) * 0.6F > room) {
			// Still too wide at the smallest scale: the first letters, the full name in the tooltip (33.3).
			String plain = name.getString();
			label = Component.literal(plain.substring(0, Math.min(4, plain.length())));
			if (active) {
				label = Brush.bold(label);
			}
		}
		Brush.fit(g, font, label, tx, y, room, active ? Palette.SEAL_DARK : Palette.INK_SOFT, 0.75F, 0.6F, false);
	}

	private void drawHelpSeal(GuiGraphicsExtractor g, float y) {
		float x0 = helpX0;
		float y0 = y - 1.0F;
		boolean over = mouseX >= x0 && mouseX < x0 + HELP_SEAL && mouseY >= y0 && mouseY < y0 + HELP_SEAL;
		Brush.rect(g, x0, y0, x0 + HELP_SEAL, y0 + HELP_SEAL, over ? Palette.SEAL_GLOW : Palette.SEAL);
		Brush.rect(g, x0 + 0.8F, y0 + 0.8F, x0 + HELP_SEAL - 0.8F, y0 + HELP_SEAL - 0.8F, Palette.argb(0x30, 0xFFFFFF));
		Brush.centered(g, font, Brush.bold(Component.literal("?")), x0 + HELP_SEAL / 2.0F + 0.3F, y0 + 1.6F, 0xFFFFF4E0, 0.75F, false);
	}

	private void headerTooltip() {
		float y = headerY;
		if (mouseY < y - 2 || mouseY >= y + 10) {
			return;
		}
		if (mouseX >= helpX0 && mouseX < helpX0 + HELP_SEAL) {
			showTooltip(List.of(Brush.bold(Component.translatable("kultivator.hub.help")),
					Component.translatable("kultivator.hub.help.tip").withStyle(ChatFormatting.GRAY)));
			return;
		}
		for (int i = 0; i < coinX0.length; i++) {
			if (mouseX >= coinX0[i] && mouseX < coinX1[i]) {
				Component name = subTitle(i);
				if (name != null) {
					showTooltip(List.of(Brush.bold(name), Component.translatable("kultivator.hub.sub.tip").withStyle(ChatFormatting.GRAY)));
				}
				return;
			}
		}
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
			if (headerClicked(event.x(), event.y())) {
				return true;
			}
		}
		if (page().mouseClicked(event.x(), event.y(), event.button())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean headerClicked(double x, double y) {
		if (y < headerY - 2 || y >= headerY + 10) {
			return false;
		}
		if (x >= helpX0 && x < helpX0 + HELP_SEAL) {
			UiSound.press();
			String topic = page().helpTopic();
			HubNav.help(topic == null || topic.isEmpty() ? "jalan" : topic);
			return true;
		}
		for (int i = 0; i < coinX0.length; i++) {
			if (x >= coinX0[i] && x < coinX1[i]) {
				if (i != sub) {
					UiSound.tab();
					showSection(section, i);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pressedTablet != null) {
			JadeTablet tablet = pressedTablet;
			pressedTablet = null;
			tablet.release(event.x(), event.y());
			return true;
		}
		if (page().mouseReleased(event.x(), event.y(), event.button())) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (page().mouseScrolled(x, y, scrollY)) {
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (Keys.QUICK.matches(event) || Keys.INFO.matches(event)) {
			onClose();
			return true;
		}
		// A screen's own key opens it from here too (it comes back here on Esc).
		if (Keys.CODEX.matches(event)) {
			dev.kultivator.client.codex.Codex.open(-1);
			return true;
		}
		if (Keys.GUIDE.matches(event)) {
			dev.kultivator.client.guide.GuideClient.open(this);
			return true;
		}
		if (Keys.DUNIA.matches(event)) {
			dev.kultivator.client.dunia.DuniaClient.open(Payloads.OpenScreen.DUNIA_ALAM);
			return true;
		}
		if (Keys.ABADI.matches(event)) {
			dev.kultivator.client.dunia.DuniaClient.openAbadi(Payloads.OpenScreen.ABADI_ALAM);
			return true;
		}
		if (Keys.MURID.matches(event)) {
			Keys.send(new Payloads.Action(Actions.DISCIPLE_OPEN, -1, ""));
			return true;
		}
		int key = event.key();
		if (key >= InputConstants.KEY_1 && key < InputConstants.KEY_1 + HubNav.SECTIONS) {
			int number = key - InputConstants.KEY_1;
			if (number != section) {
				UiSound.tab();
				showSection(number, HubNav.lastSub(number));
			}
			return true;
		}
		if (key == InputConstants.KEY_TAB) {
			stepSub(event.hasShiftDown() ? -1 : 1);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		UiSound.close();
		HubNav.hubRemoved(section);
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
