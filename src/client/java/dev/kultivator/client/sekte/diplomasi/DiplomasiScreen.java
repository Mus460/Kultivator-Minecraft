package dev.kultivator.client.sekte.diplomasi;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.sekte.api.Lambang;
import dev.kultivator.sekte.diplomasi.DiplomasiPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Diplomasi Sekte (DESIGN 36.2.9): an unrolled scroll with the NPC sects on the left (emblem seal, name, tier pips, a
 * stance stripe) and the chosen sect on the right: its large seal, master, path and tier, strength against the hall's,
 * the standing gauge from war to alliance with the stance zones, pact and war badges, its gate (once met), a feud or a
 * Dunia clan it walks with, the eleven actions (closed ones grey with the reason in the tooltip) and the last lines of its
 * chronicle. Along the bottom: the envoys' open offers with Terima/Tolak, or the world news. Everything is drawn with
 * fills and the font (no textures, no post effects); the whole page scales down on small windows.
 */
public final class DiplomasiScreen extends Screen {

	private static final String KEY = "kultivator.sekte.diplomasi.";
	private static final int W = 440;
	private static final int H = 280;
	private static final int LIST_X0 = 12;
	private static final int LIST_X1 = 150;
	private static final int TOP = 38;
	private static final int BOTTOM = 226;
	private static final int ROW_H = 24;
	private static final int DX0 = 158;
	private static final int DX1 = 428;
	private static final int BTN_W = 64;
	private static final int BTN_H = 15;
	/** The 32 emblem symbols as seal glyphs (the same order as {@code api.Lambang} symbols). */
	private static final String SYMBOLS = "剑莲鹤龙凤虎龟山月日云雷火水木金土风星海玉竹梅松霜雪花鼎钟塔天道";
	private static final int[] STANCE_RGB = {0x8B1A1A, 0xC0392B, 0xE5821C, 0x7A8C83, 0x2E8963, 0xD4A637};
	private static final String[] STANCES = {"perang", "bermusuhan", "dingin", "netral", "ramah", "sekutu"};

	private CompoundTag sheet;
	private @Nullable String selected;
	private int focusOffer;
	private int scroll;
	private float scale = 1.0F;
	private int ox;
	private int oy;
	private long opened;
	private final List<Hit> hits = new ArrayList<>();
	private @Nullable List<Component> tooltip;

	/** A clickable area in page coordinates. */
	private record Hit(int x0, int y0, int x1, int y1, boolean enabled, Runnable action, @Nullable List<Component> tip) {
		boolean over(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	public DiplomasiScreen(@Nullable CompoundTag sheet, @Nullable String focus) {
		super(Component.translatable(KEY + "layar.judul"));
		this.sheet = sheet == null ? new CompoundTag() : sheet;
		this.selected = focus;
		readFocus();
	}

	/** A fresh sheet from the server (the screen stays open). */
	void update(CompoundTag fresh) {
		this.sheet = fresh == null ? new CompoundTag() : fresh;
		readFocus();
	}

	private void readFocus() {
		String focus = sheet.getStringOr("focus", "");
		if (!focus.isEmpty()) {
			selected = focus;
		}
		int offer = sheet.getIntOr("offer", 0);
		if (offer != 0) {
			focusOffer = offer;
		}
		List<CompoundTag> sects = sects();
		if ((selected == null || find(selected) == null) && !sects.isEmpty()) {
			selected = sects.get(0).getStringOr("id", "");
		}
	}

	@Override
	protected void init() {
		opened = Util.getMillis();
		scale = Math.min(1.0F, Math.min((width - 12) / (float) W, (height - 12) / (float) H));
		ox = (int) ((width - W * scale) / 2.0F);
		oy = (int) ((height - H * scale) / 2.0F);
		DiplomasiClient.request(DiplomasiPayloads.OP_REFRESH, selected, 0, 0);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ================================================================== sheet access

	private List<CompoundTag> sects() {
		ListTag list = sheet.getListOrEmpty("sects");
		List<CompoundTag> out = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			out.add(list.getCompoundOrEmpty(i));
		}
		return out;
	}

	private @Nullable CompoundTag find(@Nullable String id) {
		if (id == null) {
			return null;
		}
		for (CompoundTag s : sects()) {
			if (id.equals(s.getStringOr("id", ""))) {
				return s;
			}
		}
		return null;
	}

	private static Component comp(CompoundTag tag, String key, Component fallback) {
		return tag.read(key, ComponentSerialization.CODEC).orElse(fallback);
	}

	private static int stanceIndex(String id) {
		for (int i = 0; i < STANCES.length; i++) {
			if (STANCES[i].equals(id)) {
				return i;
			}
		}
		return 3;
	}

	// ================================================================== drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, Palette.argb(0xB0, Palette.NIGHT), Palette.argb(0xD0, Palette.NIGHT_MIST));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		hits.clear();
		tooltip = null;
		float mx = (mouseX - ox) / scale;
		float my = (mouseY - oy) / scale;
		// The scroll unrolls over the first 250 ms.
		float open = Mth.clamp((Util.getMillis() - opened) / 250.0F, 0.0F, 1.0F);
		int half = Math.round(H / 2.0F * (0.15F + 0.85F * (1.0F - (1.0F - open) * (1.0F - open))));
		g.pose().pushMatrix();
		g.pose().translate(ox, oy);
		g.pose().scale(scale, scale);
		int top = H / 2 - half;
		int bottom = H / 2 + half;
		ScrollFrame.paper(g, 0, W, top, bottom, 0, H);
		ScrollFrame.rod(g, 0, W, top);
		ScrollFrame.rod(g, 0, W, bottom);
		if (open >= 1.0F) {
			g.nextStratum();
			header(g);
			list(g, mx, my);
			detail(g, mx, my);
			strip(g, mx, my);
			for (Hit hit : hits) {
				if (hit.over(mx, my) && hit.tip != null) {
					tooltip = hit.tip;
				}
			}
		}
		g.pose().popMatrix();
		if (tooltip != null && !tooltip.isEmpty()) {
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	private void header(GuiGraphicsExtractor g) {
		Brush.text(g, font, Brush.bold(title), 16, 13, Palette.INK, 1.25F, false);
		Brush.text(g, font, Component.translatable(KEY + "layar.sub"), 16, 26, Palette.INK_FAINT, 0.7F, false);
		if (!sheet.getBooleanOr("hall", false)) {
			Brush.text(g, font, Component.translatable(KEY + "layar.tanpa_aula"), 200, 15, Palette.DANGER, 0.8F, false);
			return;
		}
		Component stats = Component.translatable(KEY + "layar.aula", sheet.getIntOr("hall_level", 0), sheet.getIntOr("kekuatan", 0),
				sheet.getIntOr("pool", 0));
		float w = font.width(stats) * 0.8F;
		Brush.text(g, font, stats, W - 18 - w, 15, Palette.INK_SOFT, 0.8F, false);
		g.fill(LIST_X0, 34, W - 12, 35, Palette.argb(0x60, Palette.GOLD_DARK));
	}

	private void list(GuiGraphicsExtractor g, float mx, float my) {
		List<CompoundTag> sects = sects();
		int rows = (BOTTOM - TOP) / ROW_H;
		scroll = Mth.clamp(scroll, 0, Math.max(0, sects.size() - rows));
		if (sects.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(KEY + "layar.kosong"), LIST_X0 + 4, TOP + 6, LIST_X1 - LIST_X0 - 8, Palette.INK_FAINT, 0.7F, false);
			return;
		}
		for (int row = 0; row < rows && row + scroll < sects.size(); row++) {
			CompoundTag s = sects.get(row + scroll);
			String id = s.getStringOr("id", "");
			int y0 = TOP + row * ROW_H;
			int y1 = y0 + ROW_H - 2;
			boolean sel = id.equals(selected);
			boolean hover = mx >= LIST_X0 && mx < LIST_X1 && my >= y0 && my < y1;
			int stance = stanceIndex(s.getStringOr("sikap", "netral"));
			Brush.chamfer(g, LIST_X0, y0, LIST_X1, y1, 2, sel ? Palette.argb(0x70, Palette.TALISMAN) : hover ? 0x30FFFFFF : 0x18000000);
			g.fill(LIST_X0, y0 + 2, LIST_X0 + 2, y1 - 2, Palette.opaque(STANCE_RGB[stance]));
			emblem(g, s.getIntOr("lambang", Lambang.DEFAULT.pack()), LIST_X0 + 13, y0 + 11, 8.5F);
			Component name = comp(s, "name", Component.literal(id));
			Brush.fit(g, font, name, LIST_X0 + 25, y0 + 3, LIST_X1 - LIST_X0 - 28, Palette.INK, 0.72F, 0.5F, false);
			pips(g, LIST_X0 + 25, y0 + 14, s.getIntOr("tier", 1), 2.4F);
			if (s.getBooleanOr("perang", false)) {
				Brush.text(g, font, Component.literal("戰"), LIST_X1 - 12, y0 + 11, Palette.DANGER, 0.8F, false);
			} else if (s.getIntOr("pakta", 0) == 2) {
				Brush.text(g, font, Component.literal("盟"), LIST_X1 - 12, y0 + 11, Palette.GOLD_DARK, 0.8F, false);
			} else if (s.getBooleanOr("bawahan", false)) {
				Brush.text(g, font, Component.literal("屬"), LIST_X1 - 12, y0 + 11, Palette.JADE_DARK, 0.8F, false);
			}
			hits.add(new Hit(LIST_X0, y0, LIST_X1, y1, true, () -> {
				if (!id.equals(selected)) {
					selected = id;
					UiSound.tab();
				}
			}, null));
		}
		if (sects.size() > rows) {
			float bar = (BOTTOM - TOP) * rows / (float) sects.size();
			float at = TOP + (BOTTOM - TOP - bar) * scroll / (float) Math.max(1, sects.size() - rows);
			Brush.rect(g, LIST_X1 + 2, at, LIST_X1 + 3.5F, at + bar, Palette.argb(0x90, Palette.GOLD_DARK));
		}
	}

	private void detail(GuiGraphicsExtractor g, float mx, float my) {
		CompoundTag s = find(selected);
		if (s == null) {
			return;
		}
		String id = s.getStringOr("id", "");
		int stance = stanceIndex(s.getStringOr("sikap", "netral"));
		// -------------------------------------------------- the seal, name and master
		Brush.halo(g, DX0 + 24, TOP + 22, 25.0F, 5.0F, STANCE_RGB[stance], 0.35F);
		emblem(g, s.getIntOr("lambang", Lambang.DEFAULT.pack()), DX0 + 24, TOP + 22, 21.0F);
		Component name = comp(s, "name", Component.literal(id));
		Brush.fit(g, font, Brush.bold(name), DX0 + 52, TOP, DX1 - DX0 - 52, Palette.INK, 1.1F, 0.6F, false);
		Brush.fit(g, font, comp(s, "master", Component.empty()), DX0 + 52, TOP + 12, DX1 - DX0 - 52, Palette.INK_SOFT, 0.75F, 0.5F, false);
		int aliran = Mth.clamp(s.getIntOr("aliran", 0), 0, 2);
		int[] aliranRgb = {0x2E8963, 0x7A8C83, 0x8B1A1A};
		Component path = Component.translatable(KEY + "aliran." + new String[]{"lurus", "netral", "iblis"}[aliran]);
		int pw = (int) (font.width(path) * 0.7F) + 8;
		Brush.chamfer(g, DX0 + 52, TOP + 22, DX0 + 52 + pw, TOP + 31, 2, Palette.opaque(aliranRgb[aliran]));
		Brush.text(g, font, path, DX0 + 56, TOP + 23.5F, 0xFFFFFFFF, 0.7F, false);
		int tier = s.getIntOr("tier", 1);
		Brush.text(g, font, Component.translatable(KEY + "layar.tingkat", tier), DX0 + 58 + pw, TOP + 23.5F, Palette.INK, 0.7F, false);
		pips(g, DX0 + 104 + pw, TOP + 26, tier, 3.0F);
		// growth toward the next tier
		float grow = tier >= 9 ? 1.0F : Mth.clamp(s.getIntOr("sumber", 0) / (float) Math.max(1, s.getIntOr("next", 1)), 0.0F, 1.0F);
		bar(g, DX0 + 52, TOP + 35, DX1 - 8, 3, grow, Palette.JADE, Palette.argb(0x40, Palette.INK_FAINT));
		hits.add(new Hit(DX0 + 52, TOP + 33, DX1 - 8, TOP + 40, true, () -> {
		}, List.of(Component.translatable(KEY + "layar.tip.tumbuh", s.getIntOr("sumber", 0), s.getIntOr("next", 0)))));
		// strength against the hall's
		int theirs = s.getIntOr("strength", 0);
		int ours = sheet.getIntOr("kekuatan", 0);
		float share = theirs + ours <= 0 ? 0.5F : ours / (float) (theirs + ours);
		int by = TOP + 44;
		Brush.text(g, font, Component.translatable(KEY + "layar.kekuatan", ours, theirs), DX0 + 52, by - 1, Palette.INK_SOFT, 0.62F, false);
		int bx0 = DX0 + 150;
		int bx1 = DX1 - 8;
		g.fill(bx0, by, bx1, by + 4, Palette.opaque(0x8B1A1A));
		g.fill(bx0, by, bx0 + Math.round((bx1 - bx0) * share), by + 4, Palette.JADE);
		g.fill(bx0 + Math.round((bx1 - bx0) * share), by - 1, bx0 + Math.round((bx1 - bx0) * share) + 1, by + 5, Palette.GOLD_LIGHT);
		// -------------------------------------------------- the standing gauge
		gauge(g, s, stance, TOP + 56);
		// -------------------------------------------------- facts
		List<Component> facts = new ArrayList<>();
		if (s.getBooleanOr("perang", false)) {
			facts.add(Component.translatable(KEY + "layar.perang", s.getLongOr("perang_hari", 0L), s.getIntOr("menang", 0), s.getIntOr("kalah", 0), 3)
					.withStyle(ChatFormatting.DARK_RED));
		}
		if (s.getBooleanOr("bawahan", false)) {
			facts.add(Component.translatable(KEY + "layar.bawahan").withStyle(ChatFormatting.DARK_GREEN));
		}
		if (s.getIntOr("upeti_paksa", 0) > 0) {
			facts.add(Component.translatable(KEY + "layar.upeti_paksa", s.getIntOr("upeti_paksa", 0)).withStyle(ChatFormatting.DARK_RED));
		}
		if (s.contains("gx")) {
			facts.add(Component.translatable(KEY + (s.getBooleanOr("gerbang", false) ? "layar.gerbang" : "layar.gerbang_belum"), s.getIntOr("gx", 0),
					s.getIntOr("gz", 0)));
		} else {
			facts.add(Component.translatable(KEY + "layar.gerbang_rahasia"));
		}
		if (s.contains("musuh")) {
			facts.add(Component.translatable(KEY + "layar.musuh", comp(s, "musuh", Component.empty())));
		}
		if (s.contains("klan")) {
			facts.add(Component.translatable(KEY + "layar.klan", comp(s, "klan", Component.empty())));
		}
		if (s.getIntOr("pembelot", 0) > 0) {
			facts.add(Component.translatable(KEY + "layar.pembelot", s.getIntOr("pembelot", 0)));
		}
		float fy = TOP + 84;
		for (int i = 0; i < facts.size() && i < 3; i++) {
			Brush.fit(g, font, facts.get(i), DX0 + 4, fy, DX1 - DX0 - 8, Palette.INK_SOFT, 0.66F, 0.5F, false);
			fy += 7.5F;
		}
		// -------------------------------------------------- the actions
		actions(g, s, id, mx, my, TOP + 108);
		// -------------------------------------------------- the chronicle
		ListTag kronik = s.getListOrEmpty("kronik");
		int ky = TOP + 164;
		g.fill(DX0 + 2, ky - 3, DX1 - 2, ky - 2, Palette.argb(0x50, Palette.GOLD_DARK));
		Brush.text(g, font, Component.translatable(KEY + "layar.kronik"), DX0 + 4, ky, Palette.GOLD_DARK, 0.62F, false);
		int shown = 0;
		for (int i = kronik.size() - 1; i >= 0 && shown < 3; i--, shown++) {
			CompoundTag line = kronik.getCompoundOrEmpty(i);
			Component text = Component.translatable(KEY + "layar.hari", line.getLongOr("day", 0L)).append(" ")
					.append(comp(line, "text", Component.empty()));
			Brush.fit(g, font, text, DX0 + 4, ky + 7 + shown * 7, DX1 - DX0 - 8, Palette.INK, 0.6F, 0.45F, false);
		}
		if (kronik.isEmpty()) {
			Brush.text(g, font, Component.translatable(KEY + "layar.kronik_kosong"), DX0 + 4, ky + 7, Palette.INK_FAINT, 0.6F, false);
		}
	}

	private void gauge(GuiGraphicsExtractor g, CompoundTag s, int stance, int y) {
		int x0 = DX0 + 4;
		int x1 = DX1 - 8;
		int standing = s.getIntOr("standing", 0);
		// zones: war < -70, hostile < -30, cold < -10, neutral < 20, friendly < 60, ally from 60 (with a pact)
		int[] edges = {-100, -70, -30, -10, 20, 60, 100};
		for (int i = 0; i < 6; i++) {
			int a = x0 + Math.round((x1 - x0) * (edges[i] + 100) / 200.0F);
			int b = x0 + Math.round((x1 - x0) * (edges[i + 1] + 100) / 200.0F);
			g.fillGradient(a, y + 6, b, y + 14, Palette.argb(0xD0, Palette.lighten(Palette.opaque(STANCE_RGB[i]), 0.15F)),
					Palette.argb(0xD0, STANCE_RGB[i]));
		}
		Brush.frame(g, x0 - 1, y + 5, x1 + 1, y + 15, Palette.argb(0xA0, Palette.INK));
		float at = x0 + (x1 - x0) * (standing + 100) / 200.0F;
		float pulse = 0.5F + 0.5F * Mth.sin((Util.getMillis() % 2000L) / 2000.0F * Mth.TWO_PI);
		Brush.rect(g, at - 1.5F, y + 3, at + 1.5F, y + 17, Palette.mix(Palette.GOLD_LIGHT, 0xFFFFFFFF, pulse));
		Brush.rect(g, at - 3.0F, y + 2, at + 3.0F, y + 3.5F, Palette.GOLD_DARK);
		Component label = Component.translatable(KEY + "layar.standing", standing, Component.translatable("kultivator.sekte.sikap." + STANCES[stance]));
		Brush.text(g, font, label, x0, y - 3, Palette.opaque(STANCE_RGB[stance]), 0.72F, false);
		int pakta = s.getIntOr("pakta", 0);
		if (pakta > 0) {
			Component badge = Component.translatable(KEY + (pakta == 2 ? "layar.pakta_sekutu" : "layar.pakta_damai"));
			float w = font.width(badge) * 0.66F;
			Brush.chamfer(g, Math.round(x1 - w - 8), y - 4, x1, y + 4, 2, pakta == 2 ? Palette.GOLD : Palette.JADE);
			Brush.text(g, font, badge, x1 - w - 4, y - 2.5F, pakta == 2 ? Palette.INK : 0xFFFFFFFF, 0.66F, false);
		}
		hits.add(new Hit(x0, y - 4, x1, y + 16, true, () -> {
		}, List.of(Component.translatable(KEY + "layar.tip.standing"))));
	}

	private void actions(GuiGraphicsExtractor g, CompoundTag s, String id, float mx, float my, int y) {
		ListTag why = s.getListOrEmpty("why");
		int perRow = 4;
		for (int a = 0; a < DiplomasiPayloads.ACTIONS.length; a++) {
			int col = a % perRow;
			int row = a / perRow;
			int x0 = DX0 + 4 + col * (BTN_W + 3);
			int y0 = y + row * (BTN_H + 3);
			int x1 = x0 + BTN_W;
			int y1 = y0 + BTN_H;
			CompoundTag w = why.getCompoundOrEmpty(a);
			boolean enabled = sheet.getBooleanOr("hall", false) && !w.contains("t");
			boolean hover = mx >= x0 && mx < x1 && my >= y0 && my < y1;
			boolean danger = a == 6 || a == 9;
			int top = !enabled ? Palette.LOCKED_PAPER : danger ? (hover ? 0xFFE0705E : 0xFFD0503E) : hover ? 0xFFF6EBC4 : Palette.TALISMAN;
			int bottom = !enabled ? Palette.argb(0xFF, 0xB0B4AE) : danger ? 0xFF8E2A20 : Palette.TALISMAN_LOW;
			Brush.chamferGradient(g, x0, y0, x1, y1, 2, top, bottom);
			Brush.frame(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, Palette.argb(enabled ? 0x90 : 0x40, danger ? 0x5A0E08 : Palette.TALISMAN_EDGE));
			String action = DiplomasiPayloads.ACTIONS[a];
			Component label = Component.translatable(KEY + "aksi." + action);
			Brush.fitCentered(g, font, label, (x0 + x1) / 2.0F, y0 + 4, BTN_W - 6, enabled ? (danger ? 0xFFFFFFFF : Palette.INK) : Palette.INK_FAINT,
					0.68F, 0.45F, false);
			List<Component> tip = new ArrayList<>();
			tip.add(Brush.bold(label));
			tip.add(Component.translatable(KEY + "aksi." + action + ".tip"));
			if (a == 4) {
				tip.add(Component.translatable(KEY + "layar.peluang", s.getIntOr("peluang_pakta", 0)).withStyle(ChatFormatting.DARK_GREEN));
			} else if (a == 5) {
				tip.add(Component.translatable(KEY + "layar.peluang", s.getIntOr("peluang_sekutu", 0)).withStyle(ChatFormatting.DARK_GREEN));
			}
			if (w.contains("t")) {
				tip.add(comp(w, "t", Component.empty()).copy().withStyle(ChatFormatting.RED));
			}
			int index = a;
			hits.add(new Hit(x0, y0, x1, y1, enabled, () -> {
				if (DiplomasiClient.request(DiplomasiPayloads.OP_ACTION, id, index, 0)) {
					UiSound.press();
				}
			}, tip));
		}
	}

	/** The bottom strip: open offers (with answers) or the world news. */
	private void strip(GuiGraphicsExtractor g, float mx, float my) {
		int y0 = 232;
		int y1 = 268;
		ListTag offers = sheet.getListOrEmpty("offers");
		g.fill(LIST_X0, y0 - 3, W - 12, y0 - 2, Palette.argb(0x60, Palette.GOLD_DARK));
		if (offers.isEmpty()) {
			Brush.text(g, font, Component.translatable(KEY + "layar.kabar"), LIST_X0 + 2, y0, Palette.GOLD_DARK, 0.66F, false);
			ListTag news = sheet.getListOrEmpty("news");
			int shown = 0;
			for (int i = news.size() - 1; i >= 0 && shown < 3; i--, shown++) {
				CompoundTag line = news.getCompoundOrEmpty(i);
				Component text = Component.translatable(KEY + "layar.hari", line.getLongOr("day", 0L)).append(" ")
						.append(comp(line, "text", Component.empty()));
				Brush.fit(g, font, text, LIST_X0 + 2, y0 + 8 + shown * 8, W - 28, Palette.INK_SOFT, 0.64F, 0.45F, false);
			}
			if (news.isEmpty()) {
				Brush.text(g, font, Component.translatable(KEY + "layar.kabar_kosong"), LIST_X0 + 2, y0 + 8, Palette.INK_FAINT, 0.64F, false);
			}
			return;
		}
		Brush.text(g, font, Component.translatable(KEY + "layar.tawaran", offers.size()), LIST_X0 + 2, y0 - 1, Palette.SEAL, 0.62F, false);
		int cards = Math.min(3, offers.size());
		int cw = (W - 24 - (cards - 1) * 6) / cards;
		for (int i = 0; i < cards; i++) {
			CompoundTag o = offers.getCompoundOrEmpty(offers.size() - 1 - i);
			int id = o.getIntOr("id", 0);
			int x0 = LIST_X0 + i * (cw + 6);
			int x1 = x0 + cw;
			int cy0 = y0 + 6;
			boolean focus = id == focusOffer;
			Brush.chamferGradient(g, x0, cy0, x1, y1, 3, focus ? 0xFFF7E3A0 : 0xFFEFE6C8, focus ? 0xFFE2C46A : 0xFFD8CCA6);
			Brush.frame(g, x0 + 1, cy0 + 1, x1 - 1, y1 - 1, focus ? Palette.SEAL : Palette.argb(0x90, Palette.TALISMAN_EDGE));
			CompoundTag sect = find(o.getStringOr("sect", ""));
			int kind = Mth.clamp(o.getIntOr("kind", 0), 0, DiplomasiPayloads.OFFER_KINDS.length - 1);
			if (sect != null) {
				emblem(g, sect.getIntOr("lambang", Lambang.DEFAULT.pack()), x0 + 10, cy0 + 9, 6.5F);
				Brush.fit(g, font, comp(sect, "name", Component.empty()), x0 + 19, cy0 + 3, cw - 22, Palette.INK, 0.6F, 0.42F, false);
			}
			Brush.fit(g, font, Component.translatable(KEY + "tawaran." + DiplomasiPayloads.OFFER_KINDS[kind]), x0 + 19, cy0 + 11, cw - 22,
					kind == 5 || kind == 6 ? Palette.DANGER : Palette.INK_SOFT, 0.6F, 0.42F, false);
			long left = Math.max(1L, (o.getLongOr("left", 0L) + 1199L) / 1200L);
			Brush.text(g, font, Component.translatable(KEY + "layar.sisa", left), x0 + 4, y1 - 9, Palette.INK_FAINT, 0.55F, false);
			button(g, x1 - 64, y1 - 11, x1 - 34, y1 - 2, Component.translatable(KEY + "layar.terima"), Palette.JADE, mx, my,
					() -> DiplomasiClient.request(DiplomasiPayloads.OP_ANSWER, "", id, 1));
			button(g, x1 - 32, y1 - 11, x1 - 3, y1 - 2, Component.translatable(KEY + "layar.tolak"), Palette.SEAL, mx, my,
					() -> DiplomasiClient.request(DiplomasiPayloads.OP_ANSWER, "", id, 0));
			hits.add(new Hit(x0, cy0, x1 - 66, y1, true, () -> {
				if (sect != null) {
					selected = sect.getStringOr("id", selected);
				}
			}, List.of(Component.translatable(KEY + "tawaran." + DiplomasiPayloads.OFFER_KINDS[kind] + ".tip"))));
		}
	}

	private void button(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, Component label, int color, float mx, float my, Runnable action) {
		boolean hover = mx >= x0 && mx < x1 && my >= y0 && my < y1;
		Brush.chamfer(g, x0, y0, x1, y1, 1, hover ? Palette.lighten(color, 0.2F) : color);
		Brush.fitCentered(g, font, label, (x0 + x1) / 2.0F, y0 + 2, x1 - x0 - 2, 0xFFFFFFFF, 0.58F, 0.4F, false);
		hits.add(new Hit(x0, y0, x1, y1, true, () -> {
			UiSound.press();
			action.run();
		}, null));
	}

	private static void bar(GuiGraphicsExtractor g, int x0, int y, int x1, int h, float share, int fill, int back) {
		g.fill(x0, y, x1, y + h, back);
		g.fill(x0, y, x0 + Math.round((x1 - x0) * Mth.clamp(share, 0.0F, 1.0F)), y + h, fill);
	}

	/** Nine tier pips; filled up to {@code tier}. */
	private static void pips(GuiGraphicsExtractor g, float x, float y, int tier, float size) {
		for (int i = 0; i < 9; i++) {
			float cx = x + i * (size * 1.7F) + size / 2.0F;
			int color = i < tier ? (i >= 6 ? Palette.SEAL : i >= 3 ? Palette.GOLD : Palette.JADE) : Palette.argb(0x50, Palette.INK_FAINT);
			// a small diamond
			for (float d = -size / 2.0F; d < size / 2.0F; d += 0.5F) {
				float hw = size / 2.0F - Math.abs(d);
				Brush.rect(g, cx - hw, y + d, cx + hw, y + d + 0.5F, color);
			}
		}
	}

	// ================================================================== the emblem seal (drawn, no texture)

	/** The emblem as a seal: border shape, field shape inside, the symbol glyph in the charge colour. */
	void emblem(GuiGraphicsExtractor g, int packed, float cx, float cy, float r) {
		Lambang l = Lambang.unpack(packed);
		shape(g, l.shape(), cx, cy, r, Palette.opaque(l.borderRgb()));
		shape(g, l.shape(), cx, cy, r - Math.max(1.2F, r * 0.13F), Palette.opaque(l.fieldRgb()));
		String glyph = String.valueOf(SYMBOLS.charAt(Math.floorMod(l.symbol(), SYMBOLS.length())));
		Component c = Component.literal(glyph);
		float s = r / 6.0F;
		Brush.text(g, font, c, cx - font.width(c) * s / 2.0F, cy - 4.0F * s, Palette.opaque(l.chargeRgb()), s, false);
	}

	/** One of the eight emblem shapes, filled in half-pixel rows. */
	private static void shape(GuiGraphicsExtractor g, int shape, float cx, float cy, float r, int color) {
		if (shape == 4) {
			// cloud scroll: a round heart with three small lobes
			Brush.disc(g, cx, cy + r * 0.1F, r * 0.75F, color);
			Brush.disc(g, cx - r * 0.55F, cy - r * 0.25F, r * 0.42F, color);
			Brush.disc(g, cx + r * 0.55F, cy - r * 0.25F, r * 0.42F, color);
			Brush.disc(g, cx, cy - r * 0.55F, r * 0.42F, color);
			return;
		}
		if (shape == 0) {
			Brush.disc(g, cx, cy, r, color);
			return;
		}
		for (float dy = -r; dy < r; dy += 0.5F) {
			float t = (dy + r) / (2.0F * r);
			float a = Math.abs(dy + 0.25F);
			float hw = switch (shape) {
				case 1 -> r * 0.9F;
				case 2 -> Math.min(r * 0.92F, r * 1.35F - a);
				case 3 -> r - a;
				case 5 -> t < 0.62F ? r * 0.82F * (float) Math.sqrt(t / 0.62F) : r * 0.82F * (float) Math.sqrt(Math.max(0.0F, 1.0F - ((t - 0.62F) / 0.38F) * ((t - 0.62F) / 0.38F)));
				case 6 -> dy < 0.0F ? r * 0.85F : r * 0.85F * (1.0F - (dy / r) * (dy / r));
				case 7 -> t < 0.72F ? r * 0.7F : r * 0.7F * (1.0F - (t - 0.72F) / 0.28F);
				default -> r * 0.9F;
			};
			if (hw <= 0.05F) {
				continue;
			}
			if (shape == 7 && t >= 0.72F) {
				// the banner's swallowtail: two points
				float notch = r * 0.7F * (t - 0.72F) / 0.28F;
				Brush.rect(g, cx - r * 0.7F, cy + dy, cx - notch, cy + dy + 0.5F, color);
				Brush.rect(g, cx + notch, cy + dy, cx + r * 0.7F, cy + dy + 0.5F, color);
				continue;
			}
			Brush.rect(g, cx - hw, cy + dy, cx + hw, cy + dy + 0.5F, color);
		}
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		float mx = (float) ((event.x() - ox) / scale);
		float my = (float) ((event.y() - oy) / scale);
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.over(mx, my)) {
				if (hit.enabled) {
					hit.action.run();
				} else {
					UiSound.denied();
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		float mx = (float) ((x - ox) / scale);
		if (mx < LIST_X1 + 4) {
			scroll -= (int) Math.signum(scrollY);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void removed() {
		super.removed();
		UiSound.close();
	}
}
