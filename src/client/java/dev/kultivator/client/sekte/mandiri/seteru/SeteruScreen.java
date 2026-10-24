package dev.kultivator.client.sekte.mandiri.seteru;

import dev.kultivator.Kultivator;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.sekte.api.Lambang;
import dev.kultivator.sekte.mandiri.seteru.SeteruIds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Perseteruan (DESIGN 39.F.4, 39.F.9), the hub card of section MURID: a dark rosewood war-council board with a gold rim
 * and an ember strip, three tabs.
 * <ul>
 * <li><b>Seteru</b>: the hall's feuds on the left (their sect's seal, ours against theirs, the cause icon, a heat bar and
 * the status); the chosen feud on the right: the bamboo ladder T0-T7 with the rungs reached burnt red, the cap locked
 * and a flame on the heat; the heat gauge with its thresholds; our disciples and their members (role, realm, traits,
 * attitude bar, grudge); what waits (a duel in N hours, an ambush, the vow, the elder away, frozen); the last eight lines of
 * its history; and the master's eight actions (closed ones grey with the reason in the tooltip; Restui / Pisahkan for
 * forbidden lovers).</li>
 * <li><b>Murid sekte lain</b>: every sect of the world (seal, tier pips, stance) and the chosen sect's named disciples as
 * cards.</li>
 * <li><b>Sahabat &amp; patroli</b>: friends from other sects, and the patrol gang on the hall's road.</li>
 * </ul>
 * Everything is fills, the font and the generated icon atlas {@code textures/sekte/mandiri/seteru/ikon.png}; the page
 * scales down on small windows (GUI scale 2-4), keyboard: Tab/1-3 switch tabs, arrows choose, Esc closes.
 */
public final class SeteruScreen extends Screen {

	private static final Identifier IKON = Kultivator.id("textures/sekte/mandiri/seteru/ikon.png");
	private static final int W = 470;
	private static final int H = 296;
	private static final int TOP = 44;
	private static final int LIST_X0 = 12;
	private static final int LIST_X1 = 160;
	private static final int DX0 = 168;
	private static final int DX1 = 458;
	private static final int ROW_H = 34;
	private static final int BOTTOM = 284;
	private static final String SIMBOL = "剑莲鹤龙凤虎龟山月日云雷火水木金土风星海玉竹梅松霜雪花鼎钟塔天道";
	private static final String[] SIKAP = {"perang", "bermusuhan", "dingin", "netral", "ramah", "sekutu"};
	private static final int[] SIKAP_RGB = {0x8B1A1A, 0xC0392B, 0xE5821C, 0x7A8C83, 0x2E8963, 0xD4A637};
	private static final int WOOD_TOP = 0xFF2A1612;
	private static final int WOOD_BOTTOM = 0xFF160B09;
	private static final int EMBER = 0xFFE0552B;
	private static final int PAPER = 0xFFF1E4C8;
	private static final int PAPER_DIM = 0xFFBFAE8E;

	private CompoundTag sheet;
	private int tab;
	private @Nullable String selected;
	private int scroll;
	private float scale = 1.0F;
	private int ox;
	private int oy;
	private long opened;
	private final List<Hit> hits = new ArrayList<>();
	private @Nullable List<Component> tooltip;

	private record Hit(int x0, int y0, int x1, int y1, boolean enabled, Runnable action, @Nullable List<Component> tip) {
		boolean over(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	public SeteruScreen(@Nullable CompoundTag sheet) {
		super(Component.translatable(SeteruIds.k("layar.judul")));
		this.sheet = sheet == null ? new CompoundTag() : sheet;
	}

	void update(CompoundTag fresh) {
		this.sheet = fresh == null ? new CompoundTag() : fresh;
	}

	@Override
	protected void init() {
		opened = Util.getMillis();
		scale = Math.min(1.0F, Math.min((width - 10) / (float) W, (height - 10) / (float) H));
		ox = (int) ((width - W * scale) / 2.0F);
		oy = (int) ((height - H * scale) / 2.0F);
		SeteruClient.minta(SeteruIds.OP_SEGAR, "", 0, sheet.getStringOr("fokus", ""));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ================================================================== sheet access

	private List<CompoundTag> list(CompoundTag tag, String key) {
		ListTag l = tag.getListOrEmpty(key);
		List<CompoundTag> out = new ArrayList<>(l.size());
		for (int i = 0; i < l.size(); i++) {
			out.add(l.getCompoundOrEmpty(i));
		}
		return out;
	}

	private static Component comp(CompoundTag tag, String key) {
		return tag.read(key, ComponentSerialization.CODEC).orElse(Component.empty());
	}

	private @Nullable CompoundTag feud() {
		List<CompoundTag> feuds = list(sheet, "seteru");
		if (feuds.isEmpty()) {
			return null;
		}
		for (CompoundTag f : feuds) {
			if (f.getStringOr("id", "").equals(selected)) {
				return f;
			}
		}
		// active feuds come first in the list
		for (CompoundTag f : feuds) {
			if (aktif(f)) {
				selected = f.getStringOr("id", "");
				return f;
			}
		}
		selected = feuds.get(0).getStringOr("id", "");
		return feuds.get(0);
	}

	private static boolean aktif(CompoundTag f) {
		String s = f.getStringOr("status", "");
		return SeteruIds.AKTIF.equals(s) || SeteruIds.TERLARANG.equals(s);
	}

	private static int sikapIndex(String id) {
		for (int i = 0; i < SIKAP.length; i++) {
			if (SIKAP[i].equals(id)) {
				return i;
			}
		}
		return 3;
	}

	// ================================================================== drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, 0xC0100806, 0xD8080404);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		hits.clear();
		tooltip = null;
		float mx = (mouseX - ox) / scale;
		float my = (mouseY - oy) / scale;
		float open = Mth.clamp((Util.getMillis() - opened) / 220.0F, 0.0F, 1.0F);
		g.pose().pushMatrix();
		g.pose().translate(ox, oy + (1.0F - open) * 12.0F);
		g.pose().scale(scale, scale);
		papan(g);
		header(g, mx, my);
		switch (tab) {
			case 1 -> roster(g, mx, my);
			case 2 -> sahabat(g);
			default -> {
				daftar(g, mx, my);
				rinci(g, mx, my);
			}
		}
		for (Hit hit : hits) {
			if (hit.over(mx, my) && hit.tip != null) {
				tooltip = hit.tip;
			}
		}
		g.pose().popMatrix();
		if (tooltip != null && !tooltip.isEmpty()) {
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	/** The board: rosewood with grain, a double gold rim, an ember strip at the top and brass corner caps. */
	private void papan(GuiGraphicsExtractor g) {
		g.fill(3, 4, W + 3, H + 4, 0x70000000);
		g.fillGradient(0, 0, W, H, WOOD_TOP, WOOD_BOTTOM);
		for (int i = 0; i < 18; i++) {
			int y = 6 + i * 16 + (i * 7 % 5);
			g.fill(4, y, W - 4, y + 1, 0x14FFD8B0);
		}
		g.fillGradient(4, 4, W - 4, 22, Palette.argb(0x70, EMBER), 0x00000000);
		Brush.frame(g, 2, 2, W - 2, H - 2, Palette.GOLD);
		Brush.frame(g, 5, 5, W - 5, H - 5, Palette.argb(0xA0, Palette.GOLD_DARK));
		for (int[] c : new int[][]{{2, 2}, {W - 10, 2}, {2, H - 10}, {W - 10, H - 10}}) {
			g.fill(c[0], c[1], c[0] + 8, c[1] + 8, Palette.GOLD_DARK);
			g.fill(c[0] + 2, c[1] + 2, c[0] + 6, c[1] + 6, Palette.GOLD);
		}
	}

	private void header(GuiGraphicsExtractor g, float mx, float my) {
		Brush.text(g, font, Brush.bold(title), 16, 11, Palette.GOLD_LIGHT, 1.3F, true);
		Brush.text(g, font, Component.translatable(SeteruIds.k("layar.sub")), 16, 25, PAPER_DIM, 0.65F, false);
		int aktif = 0;
		for (CompoundTag f : list(sheet, "seteru")) {
			if (aktif(f)) {
				aktif++;
			}
		}
		Component info = Component.translatable(SeteruIds.k("layar.info"), aktif, sheet.getIntOr("maks", 3),
				Component.translatable(SeteruIds.k("intensitas." + sheet.getStringOr("intensitas", "SEDANG").toLowerCase(Locale.ROOT))),
				Component.translatable(SeteruIds.k("kebijakan." + sheet.getStringOr("kebijakan", "TANYA").toLowerCase(Locale.ROOT))));
		float iw = font.width(info) * 0.65F;
		Brush.text(g, font, info, W - 16 - iw, 13, PAPER, 0.65F, false);
		// the tabs
		String[] tabs = {"tab.seteru", "tab.roster", "tab.sahabat"};
		int x = W - 16;
		for (int i = tabs.length - 1; i >= 0; i--) {
			Component label = Component.translatable(SeteruIds.k("layar." + tabs[i]));
			int tw = (int) (font.width(label) * 0.7F) + 14;
			int x0 = x - tw;
			boolean sel = tab == i;
			boolean hover = mx >= x0 && mx < x && my >= 24 && my < 38;
			Brush.chamfer(g, x0, 24, x, 38, 3, sel ? Palette.argb(0xE0, 0x6E1E14) : hover ? 0x50FFFFFF : 0x30FFFFFF);
			if (sel) {
				g.fill(x0 + 3, 37, x - 3, 38, EMBER);
			}
			Brush.text(g, font, label, x0 + 7, 28, sel ? Palette.GOLD_LIGHT : PAPER_DIM, 0.7F, false);
			int index = i;
			hits.add(new Hit(x0, 24, x, 38, true, () -> pilihTab(index), null));
			x = x0 - 4;
		}
		g.fill(LIST_X0, 40, W - 12, 41, Palette.argb(0x80, Palette.GOLD_DARK));
	}

	private void pilihTab(int index) {
		if (tab != index) {
			tab = index;
			scroll = 0;
			UiSound.tab();
		}
	}

	// ------------------------------------------------------------------ tab 1: feuds

	private void daftar(GuiGraphicsExtractor g, float mx, float my) {
		List<CompoundTag> feuds = list(sheet, "seteru");
		feud();
		if (feuds.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(SeteruIds.k("layar.kosong")), LIST_X0 + 4, TOP + 6, LIST_X1 - LIST_X0 - 8, PAPER_DIM, 0.7F,
					false);
			return;
		}
		int rows = (BOTTOM - TOP) / ROW_H;
		scroll = Mth.clamp(scroll, 0, Math.max(0, feuds.size() - rows));
		for (int row = 0; row < rows && row + scroll < feuds.size(); row++) {
			CompoundTag f = feuds.get(row + scroll);
			String id = f.getStringOr("id", "");
			int y0 = TOP + row * ROW_H;
			int y1 = y0 + ROW_H - 3;
			boolean sel = id.equals(selected);
			boolean hover = mx >= LIST_X0 && mx < LIST_X1 && my >= y0 && my < y1;
			boolean live = aktif(f);
			Brush.chamferGradient(g, LIST_X0, y0, LIST_X1, y1, 3, sel ? 0xE05A2014 : hover ? 0x60402018 : 0x40201010,
					sel ? 0xE0301008 : 0x40100808);
			if (sel) {
				Brush.frame(g, LIST_X0, y0, LIST_X1, y1, Palette.argb(0xC0, Palette.GOLD));
			}
			lambang(g, f.getIntOr("lambang", Lambang.DEFAULT.pack()), LIST_X0 + 13, y0 + 12, 8.0F, live ? 1.0F : 0.5F);
			ikon(g, SeteruIds.sebabIndex(f.getStringOr("sebab", "")), LIST_X0 + 5, y0 + 21, 10, live ? 1.0F : 0.5F);
			List<CompoundTag> souls = list(f, "jiwa");
			List<CompoundTag> them = list(f, "tokoh");
			Component ours = souls.isEmpty() ? Component.literal("?") : comp(souls.get(0), "nama");
			Component theirs = them.isEmpty() ? Component.literal("?") : comp(them.get(0), "nama");
			Brush.fit(g, font, ours, LIST_X0 + 26, y0 + 3, LIST_X1 - LIST_X0 - 30, live ? PAPER : PAPER_DIM, 0.68F, 0.45F, false);
			Brush.fit(g, font, Component.literal("× ").append(theirs), LIST_X0 + 26, y0 + 11, LIST_X1 - LIST_X0 - 30,
					live ? 0xFFF0B8A0 : PAPER_DIM, 0.62F, 0.45F, false);
			String status = f.getStringOr("status", "");
			if (live && !SeteruIds.TERLARANG.equals(status)) {
				int heat = f.getIntOr("panas", 0);
				bar(g, LIST_X0 + 26, y0 + 22, LIST_X1 - 8, 3, heat / 100.0F, panasWarna(heat), 0x40FFFFFF);
				Brush.text(g, font, Component.translatable(SeteruIds.k("tingkat.pendek." + Math.max(0, f.getIntOr("tingkat", 0)))), LIST_X0 + 26,
						y0 + 25.5F, 0xFFE8C090, 0.5F, false);
			} else {
				Component badge = Component.translatable(SeteruIds.k("status." + status));
				Brush.text(g, font, badge, LIST_X0 + 26, y0 + 21.5F, statusWarna(status), 0.6F, false);
			}
			hits.add(new Hit(LIST_X0, y0, LIST_X1, y1, true, () -> {
				if (!id.equals(selected)) {
					selected = id;
					UiSound.tab();
				}
			}, null));
		}
	}

	private void rinci(GuiGraphicsExtractor g, float mx, float my) {
		CompoundTag f = feud();
		if (f == null) {
			Brush.wrap(g, font, Component.translatable(SeteruIds.k("layar.penjelasan")), DX0 + 8, TOP + 10, DX1 - DX0 - 16, PAPER_DIM, 0.72F, false);
			return;
		}
		String status = f.getStringOr("status", "");
		boolean lover = SeteruIds.TERLARANG.equals(status);
		List<CompoundTag> souls = list(f, "jiwa");
		List<CompoundTag> them = list(f, "tokoh");
		// -------------------------------------------------- title: ours ⟷ theirs, the sect, the cause
		lambang(g, f.getIntOr("lambang", Lambang.DEFAULT.pack()), DX0 + 18, TOP + 14, 14.0F, 1.0F);
		MutableComponent names = Component.empty();
		for (int i = 0; i < souls.size(); i++) {
			if (i > 0) {
				names.append(", ");
			}
			names.append(comp(souls.get(i), "nama"));
		}
		Brush.fit(g, font, Brush.bold(names), DX0 + 38, TOP + 1, DX1 - DX0 - 44, Palette.GOLD_LIGHT, 0.95F, 0.5F, true);
		MutableComponent other = Component.translatable(SeteruIds.k(lover ? "layar.kekasih_dari" : "layar.melawan"),
				them.isEmpty() ? Component.literal("?") : comp(them.get(0), "nama"), comp(f, "namaSekte"));
		Brush.fit(g, font, other, DX0 + 38, TOP + 12, DX1 - DX0 - 44, 0xFFF0B8A0, 0.72F, 0.45F, false);
		int sikap = sikapIndex(f.getStringOr("sikap", "netral"));
		Component cause = Component.translatable(SeteruIds.k("layar.sebab"), Component.translatable(SeteruIds.k("sebab." + f.getStringOr("sebab", ""))),
				Component.translatable(SeteruIds.k("salah." + f.getStringOr("salah", "-").replace("-", "tidak"))),
				Component.translatable("kultivator.sekte.sikap." + SIKAP[sikap]).withColor(SIKAP_RGB[sikap]), f.getIntOr("standing", 0));
		ikon(g, SeteruIds.sebabIndex(f.getStringOr("sebab", "")), DX0 + 38, TOP + 21, 9, 1.0F);
		Brush.fit(g, font, cause, DX0 + 50, TOP + 22, DX1 - DX0 - 56, PAPER_DIM, 0.62F, 0.42F, false);
		// -------------------------------------------------- the ladder and the heat
		int ly = TOP + 34;
		if (!lover) {
			tangga(g, f, DX0 + 4, ly, DX1 - 4, mx, my);
		} else {
			Brush.wrap(g, font, Component.translatable(SeteruIds.k("layar.kekasih_info")), DX0 + 6, ly + 2, DX1 - DX0 - 12, 0xFFF5B8C8, 0.66F,
					false);
		}
		// -------------------------------------------------- people
		int py = TOP + 82;
		orang(g, them, DX0 + 4, py, mx, my);
		// -------------------------------------------------- what waits
		int wy = TOP + 128;
		List<Component> waits = menanti(f);
		for (int i = 0; i < waits.size() && i < 2; i++) {
			Brush.fit(g, font, waits.get(i), DX0 + 6, wy + i * 8, DX1 - DX0 - 12, 0xFFFFD49A, 0.62F, 0.45F, false);
		}
		// -------------------------------------------------- history
		int hy = TOP + 147;
		g.fill(DX0 + 2, hy - 2, DX1 - 2, hy - 1, Palette.argb(0x60, Palette.GOLD_DARK));
		Brush.text(g, font, Component.translatable(SeteruIds.k("layar.riwayat")), DX0 + 4, hy, Palette.GOLD, 0.62F, false);
		List<CompoundTag> history = list(f, "riwayat");
		int shown = 0;
		for (int i = history.size() - 1; i >= 0 && shown < 6; i--, shown++) {
			CompoundTag line = history.get(i);
			Component text = Component.translatable(SeteruIds.k("layar.hari"), line.getLongOr("hari", 0L)).append(" ").append(baris(line));
			Brush.fit(g, font, text, DX0 + 6, hy + 9 + shown * 7.5F, DX1 - DX0 - 12, shown == 0 ? PAPER : PAPER_DIM, 0.58F, 0.42F, false);
		}
		// -------------------------------------------------- the master's actions
		aksi(g, f, lover, mx, my, BOTTOM - 34);
	}

	/** The bamboo ladder T0-T7 with burnt rungs, the cap lock, the flame on the heat and the gauge below. */
	private void tangga(GuiGraphicsExtractor g, CompoundTag f, int x0, int y, int x1, float mx, float my) {
		int heat = f.getIntOr("panas", 0);
		int reached = f.getIntOr("tingkat", -1);
		int cap = f.getIntOr("batas", 6);
		float span = x1 - x0 - 16;
		// the two rails
		g.fill(x0 + 6, y + 4, x1 - 6, y + 6, Palette.BAMBOO_KNOT);
		g.fill(x0 + 6, y + 18, x1 - 6, y + 20, Palette.BAMBOO_KNOT);
		g.fill(x0 + 6, y + 4, x1 - 6, y + 5, Palette.BAMBOO);
		g.fill(x0 + 6, y + 18, x1 - 6, y + 19, Palette.BAMBOO);
		for (int t = 0; t <= 7; t++) {
			float rx = x0 + 8 + span * SeteruIds.AMBANG[t] / 100.0F;
			boolean burnt = t <= reached;
			boolean locked = t > cap;
			int col = locked ? 0xFF4A4038 : burnt ? Palette.mix(0xFFE07040, 0xFF8B1A1A, t / 7.0F) : Palette.BAMBOO_ALT;
			g.fill(Math.round(rx) - 1, y + 4, Math.round(rx) + 2, y + 20, col);
			Brush.centered(g, font, Component.literal("T" + t), rx, y + 22, locked ? 0xFF7A6A5A : burnt ? 0xFFFFB080 : PAPER_DIM, 0.5F, false);
			if (locked) {
				ikon(g, 30, Math.round(rx) - 4, y + 8, 8, 0.9F);
			}
			List<Component> tip = new ArrayList<>();
			tip.add(Component.translatable(SeteruIds.k("tingkat." + t)).withStyle(ChatFormatting.GOLD));
			tip.add(Component.translatable(SeteruIds.k("tingkat." + t + ".desc")));
			if (locked) {
				tip.add(Component.translatable(SeteruIds.k("layar.terkunci")).withStyle(ChatFormatting.GRAY));
			}
			hits.add(new Hit(Math.round(rx) - 5, y, Math.round(rx) + 6, y + 28, true, () -> {
			}, tip));
		}
		// the heat: a flame riding the ladder
		float hx = x0 + 8 + span * heat / 100.0F;
		float flicker = 1.0F + 0.12F * Mth.sin(Util.getMillis() / 90.0F);
		Brush.halo(g, hx, y + 12, 7.0F * flicker, 4.0F, 0xFF6A2A, 0.6F);
		ikon(g, 29, Math.round(hx) - 5, y + 6, 11, 1.0F);
		// the gauge
		int gy = y + 31;
		bar(g, x0 + 8, gy, x1 - 8, 4, heat / 100.0F, panasWarna(heat), 0x30FFFFFF);
		Brush.text(g, font, Component.translatable(SeteruIds.k("layar.panas"), heat), x0 + 8, gy + 6, 0xFFFFC090, 0.6F, false);
		if (f.getBooleanOr("beku", false)) {
			Brush.text(g, font, Component.translatable(SeteruIds.k("layar.beku")), x0 + 80, gy + 6, 0xFF9FD8FF, 0.6F, false);
		}
	}

	private void orang(GuiGraphicsExtractor g, List<CompoundTag> them, int x, int y, float mx, float my) {
		int cardW = Math.min(140, (DX1 - DX0 - 12) / Math.max(1, Math.min(2, them.size())));
		for (int i = 0; i < them.size() && i < 2; i++) {
			CompoundTag t = them.get(i);
			int x0 = x + i * (cardW + 4);
			kartuTokoh(g, t, x0, y, x0 + cardW, y + 42, mx, my);
		}
	}

	/** A member card: name, role, realm, traits, an attitude bar from -100 to 100, grudge and wound marks. */
	private void kartuTokoh(GuiGraphicsExtractor g, CompoundTag t, int x0, int y0, int x1, int y1, float mx, float my) {
		Brush.chamferGradient(g, x0, y0, x1, y1, 3, 0x70301A12, 0x70180C08);
		Brush.frame(g, x0, y0, x1, y1, Palette.argb(0x70, Palette.GOLD_DARK));
		boolean woman = t.getBooleanOr("perempuan", false);
		Brush.text(g, font, Component.literal(woman ? "♀" : "♂"), x0 + 4, y0 + 3, woman ? 0xFFF5A0C0 : 0xFF9FC8FF, 0.7F, false);
		Brush.fit(g, font, Brush.bold(comp(t, "nama")), x0 + 12, y0 + 3, x1 - x0 - 16, PAPER, 0.66F, 0.42F, false);
		Component role = Component.translatable(t.getStringOr("peran", "")).append(" · ")
				.append(Rank.ofStep(Math.max(0, Math.min(Rank.MAX_STEP, t.getIntOr("langkah", 2)))).displayName());
		Brush.fit(g, font, role, x0 + 4, y0 + 12, x1 - x0 - 8, PAPER_DIM, 0.55F, 0.4F, false);
		MutableComponent traits = Component.empty();
		ListTag sifat = t.getListOrEmpty("sifat");
		for (int i = 0; i < sifat.size(); i++) {
			if (i > 0) {
				traits.append(", ");
			}
			traits.append(Component.translatable("kultivator.sekte.sifat." + sifat.getStringOr(i, "")));
		}
		Dao dao = Dao.byId(t.getStringOr("dao", ""));
		if (dao != null) {
			traits.append(" · ").append(dao.displayName().copy().withColor(dao.color()));
		}
		Brush.fit(g, font, traits, x0 + 4, y0 + 19.5F, x1 - x0 - 8, 0xFFD8C8A8, 0.55F, 0.4F, false);
		int sikap = t.getIntOr("sikap", 0);
		int bx0 = x0 + 4;
		int bx1 = x1 - 4;
		int mid = (bx0 + bx1) / 2;
		int by = y0 + 30;
		g.fill(bx0, by, bx1, by + 3, 0x40FFFFFF);
		g.fill(mid, by - 1, mid + 1, by + 4, 0x90FFFFFF);
		int end = mid + Math.round((bx1 - mid) * sikap / 100.0F);
		g.fill(Math.min(mid, end), by, Math.max(mid, end), by + 3, sikap >= 0 ? Palette.JADE_LIGHT : 0xFFE0503A);
		Brush.text(g, font, Component.translatable(SeteruIds.k("layar.sikap"), sikap), x0 + 4, y0 + 34.5F, PAPER_DIM, 0.5F, false);
		int mark = x1 - 12;
		if (t.getBooleanOr("dendam", false)) {
			ikon(g, 11, mark, y0 + 32, 8, 1.0F);
			mark -= 10;
		}
		if (t.getBooleanOr("luka", false)) {
			ikon(g, 28, mark, y0 + 32, 8, 1.0F);
		}
		List<Component> tip = new ArrayList<>();
		tip.add(comp(t, "nama").copy().withStyle(ChatFormatting.GOLD));
		tip.add(Component.translatable(SeteruIds.k("layar.tokoh.tip"), t.getIntOr("jumpa", 0),
				Component.translatable(SeteruIds.k("senjata." + t.getIntOr("senjata", 0)))));
		if (t.getBooleanOr("dendam", false)) {
			tip.add(Component.translatable(SeteruIds.k("layar.tokoh.dendam")).withStyle(ChatFormatting.RED));
		}
		hits.add(new Hit(x0, y0, x1, y1, true, () -> {
		}, tip));
	}

	/** The things waiting: a duel or brawl, the ambush, the vow, the elder away, the patrol. */
	private List<Component> menanti(CompoundTag f) {
		List<Component> out = new ArrayList<>();
		String scene = f.getStringOr("adegan", "");
		if (!scene.isEmpty()) {
			long hours = Math.max(0L, f.getLongOr("adeganSisa", 0L) / 1000L);
			out.add(Component.translatable(SeteruIds.k("layar.menanti." + scene), hours,
					Component.translatable(SeteruIds.k(f.getBooleanOr("dukung", false) ? "layar.didukung" : "layar.tanpa_dukungan"))));
		}
		if (f.getBooleanOr("sergap", false)) {
			out.add(Component.translatable(SeteruIds.k("layar.menanti.sergap")));
		}
		if (f.getLongOr("sumpah", 0L) > 0L) {
			out.add(Component.translatable(SeteruIds.k("layar.menanti.sumpah"), f.getLongOr("sumpah", 0L)));
		}
		if (f.getBooleanOr("tetua", false)) {
			out.add(Component.translatable(SeteruIds.k("layar.menanti.tetua")));
		}
		CompoundTag patrol = sheet.getCompoundOrEmpty("patroli");
		if (!patrol.isEmpty() && patrol.getStringOr("seteru", "").equals(f.getStringOr("id", ""))) {
			out.add(Component.translatable(SeteruIds.k("layar.menanti.patroli"), comp(patrol, "kota")));
		}
		return out;
	}

	/** The eight actions (two rows of four), or Restui / Pisahkan for lovers. */
	private void aksi(GuiGraphicsExtractor g, CompoundTag f, boolean lover, float mx, float my, int y) {
		ListTag reasons = f.getListOrEmpty("aksi");
		String id = f.getStringOr("id", "");
		int[] ids = lover ? new int[]{SeteruIds.AKSI_RESTUI, SeteruIds.AKSI_PISAHKAN}
				: new int[]{SeteruIds.AKSI_DAMAIKAN, SeteruIds.AKSI_UTUS_TETUA, SeteruIds.AKSI_HUKUM, SeteruIds.AKSI_TUNTUT, SeteruIds.AKSI_DUKUNG,
						SeteruIds.AKSI_TANTANG_TETUA, SeteruIds.AKSI_LARANG, SeteruIds.AKSI_BIARKAN};
		int cols = lover ? 2 : 4;
		int bw = (DX1 - DX0 - 8 - (cols - 1) * 4) / cols;
		int bh = 14;
		for (int i = 0; i < ids.length; i++) {
			int a = ids[i];
			String name = a == SeteruIds.AKSI_RESTUI ? "restui" : a == SeteruIds.AKSI_PISAHKAN ? "pisahkan" : SeteruIds.AKSI[a];
			int col = i % cols;
			int row = i / cols;
			int x0 = DX0 + 4 + col * (bw + 4);
			int y0 = y + row * (bh + 3);
			String why = a < reasons.size() ? reasons.getStringOr(a, "") : "";
			boolean ok = why.isEmpty();
			boolean hover = mx >= x0 && mx < x0 + bw && my >= y0 && my < y0 + bh;
			int top = ok ? (hover ? 0xF07A2A18 : 0xE04E1C12) : 0xA0302824;
			int bottom = ok ? (hover ? 0xF04A140C : 0xE02E0E08) : 0xA0201A18;
			Brush.chamferGradient(g, x0, y0, x0 + bw, y0 + bh, 2, top, bottom);
			Brush.frame(g, x0, y0, x0 + bw, y0 + bh, ok ? Palette.argb(0xB0, Palette.GOLD) : 0x60807060);
			ikon(g, 16 + Math.min(a, 9), x0 + 2, y0 + 2, 10, ok ? 1.0F : 0.4F);
			Component label = Component.translatable(SeteruIds.k("aksi." + name));
			Brush.fit(g, font, label, x0 + 14, y0 + 3.5F, bw - 16, ok ? PAPER : 0xFF8A7E70, 0.62F, 0.42F, false);
			List<Component> tip = new ArrayList<>();
			tip.add(label.copy().withStyle(ChatFormatting.GOLD));
			tip.add(Component.translatable(SeteruIds.k("aksi." + name + ".desc")));
			if (!ok) {
				tip.add(Component.translatable(why).withStyle(ChatFormatting.RED));
			}
			int action = a;
			hits.add(new Hit(x0, y0, x0 + bw, y0 + bh, ok, () -> {
				UiSound.press();
				SeteruClient.minta(SeteruIds.OP_AKSI, id, action, f.getStringOr("sekte", ""));
			}, tip));
		}
	}

	// ------------------------------------------------------------------ tab 2: the other sects' disciples

	private void roster(GuiGraphicsExtractor g, float mx, float my) {
		List<CompoundTag> sects = list(sheet, "sekte");
		String focus = sheet.getStringOr("fokus", "");
		for (int i = 0; i < sects.size() && i < 10; i++) {
			CompoundTag s = sects.get(i);
			String id = s.getStringOr("id", "");
			int y0 = TOP + i * 24;
			int y1 = y0 + 21;
			boolean sel = id.equals(focus);
			boolean hover = mx >= LIST_X0 && mx < LIST_X1 && my >= y0 && my < y1;
			int st = sikapIndex(s.getStringOr("sikap", "netral"));
			Brush.chamfer(g, LIST_X0, y0, LIST_X1, y1, 2, sel ? 0xD05A2014 : hover ? 0x50402018 : 0x30201010);
			g.fill(LIST_X0, y0 + 2, LIST_X0 + 2, y1 - 2, Palette.opaque(SIKAP_RGB[st]));
			lambang(g, s.getIntOr("lambang", Lambang.DEFAULT.pack()), LIST_X0 + 13, y0 + 10, 7.5F, 1.0F);
			Brush.fit(g, font, comp(s, "nama"), LIST_X0 + 24, y0 + 3, LIST_X1 - LIST_X0 - 28, PAPER, 0.62F, 0.42F, false);
			Brush.text(g, font, Component.translatable(SeteruIds.k("layar.tier"), s.getIntOr("tier", 1))
					.append(" · ").append(Component.translatable("kultivator.sekte.sikap." + SIKAP[st])), LIST_X0 + 24, y0 + 12.5F,
					Palette.opaque(SIKAP_RGB[st]), 0.52F, false);
			hits.add(new Hit(LIST_X0, y0, LIST_X1, y1, true, () -> {
				if (!id.equals(focus)) {
					UiSound.tab();
					SeteruClient.minta(SeteruIds.OP_ROSTER, "", 0, id);
				}
			}, null));
		}
		List<CompoundTag> roster = list(sheet, "roster");
		if (roster.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(SeteruIds.k("layar.roster_kosong")), DX0 + 8, TOP + 8, DX1 - DX0 - 16, PAPER_DIM, 0.7F, false);
			return;
		}
		int cardW = (DX1 - DX0 - 12) / 2;
		for (int i = 0; i < roster.size() && i < 10; i++) {
			int col = i % 2;
			int row = i / 2;
			int x0 = DX0 + 4 + col * (cardW + 4);
			int y0 = TOP + row * 47;
			CompoundTag t = roster.get(i);
			kartuTokoh(g, t, x0, y0, x0 + cardW, y0 + 44, mx, my);
			if (t.getBooleanOr("seteru", false)) {
				ikon(g, 9, x0 + cardW - 12, y0 + 3, 8, 1.0F);
			}
			if (!SeteruIds.TOKOH_AKTIF.equals(t.getStringOr("status", SeteruIds.TOKOH_AKTIF))) {
				g.fill(x0, y0, x0 + cardW, y0 + 44, 0x70000000);
				Brush.centered(g, font, Component.translatable(SeteruIds.k("status_tokoh." + t.getStringOr("status", "").toLowerCase(Locale.ROOT))),
						x0 + cardW / 2.0F, y0 + 18, 0xFFE0C080, 0.7F, true);
			}
		}
	}

	// ------------------------------------------------------------------ tab 3: friends and the patrol

	private void sahabat(GuiGraphicsExtractor g) {
		List<CompoundTag> friends = list(sheet, "sahabat");
		Brush.text(g, font, Component.translatable(SeteruIds.k("layar.sahabat")), LIST_X0 + 4, TOP, Palette.GOLD, 0.75F, false);
		if (friends.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(SeteruIds.k("layar.sahabat_kosong")), LIST_X0 + 4, TOP + 12, 210, PAPER_DIM, 0.66F, false);
		}
		for (int i = 0; i < friends.size() && i < 8; i++) {
			CompoundTag fr = friends.get(i);
			int y0 = TOP + 12 + i * 26;
			Brush.chamferGradient(g, LIST_X0, y0, 230, y0 + 23, 2, 0x502A3020, 0x50141810);
			ikon(g, 31, LIST_X0 + 3, y0 + 6, 11, 1.0F);
			Brush.fit(g, font, Component.translatable(SeteruIds.k("layar.sahabat_baris"), comp(fr, "murid"), comp(fr, "tokoh")), LIST_X0 + 18,
					y0 + 3, 205, PAPER, 0.62F, 0.42F, false);
			Brush.fit(g, font, comp(fr, "sekte").copy().append(" · ").append(Component.translatable(SeteruIds.k("layar.sikap"), fr.getIntOr("sikap", 0))),
					LIST_X0 + 18, y0 + 12.5F, 205, PAPER_DIM, 0.55F, 0.4F, false);
		}
		int px = 244;
		Brush.text(g, font, Component.translatable(SeteruIds.k("layar.patroli")), px, TOP, Palette.GOLD, 0.75F, false);
		CompoundTag patrol = sheet.getCompoundOrEmpty("patroli");
		if (patrol.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(SeteruIds.k("layar.patroli_kosong")), px, TOP + 12, DX1 - px - 4, PAPER_DIM, 0.66F, false);
		} else {
			long a = patrol.getLongOr("a", 0L);
			long b = patrol.getLongOr("b", 0L);
			net.minecraft.core.BlockPos pa = net.minecraft.core.BlockPos.of(a);
			net.minecraft.core.BlockPos pb = net.minecraft.core.BlockPos.of(b);
			ikon(g, 26, px, TOP + 12, 16, 1.0F);
			Brush.wrap(g, font, Component.translatable(SeteruIds.k("layar.patroli_info"), comp(patrol, "sekte"), pa.getX(), pa.getZ(), pb.getX(),
					pb.getZ(), comp(patrol, "kota")), px + 20, TOP + 12, DX1 - px - 24, PAPER, 0.62F, false);
			long usir = patrol.getLongOr("usir", 0L) - sheet.getLongOr("hari", 0L);
			if (usir > 0L) {
				Brush.text(g, font, Component.translatable(SeteruIds.k("layar.patroli_usir"), usir), px + 20, TOP + 46, Palette.JADE_LIGHT, 0.62F,
						false);
			}
		}
		Brush.wrap(g, font, Component.translatable(SeteruIds.k("layar.tubuh"), sheet.getIntOr("tubuh", 0)), px, BOTTOM - 20, DX1 - px - 4,
				0xFF8A7E70, 0.55F, false);
	}

	// ================================================================== drawing helpers

	private static int panasWarna(int heat) {
		return Palette.mix(0xFFE8C060, 0xFFB01810, Mth.clamp(heat / 100.0F, 0.0F, 1.0F));
	}

	private static int statusWarna(String status) {
		return switch (status) {
			case SeteruIds.DAMAI -> Palette.JADE_LIGHT;
			case SeteruIds.SAINGAN -> 0xFFF2C66A;
			case SeteruIds.DENDAM -> 0xFFE0503A;
			case SeteruIds.TERLARANG, SeteruIds.KAWIN_LARI -> 0xFFF5A0C0;
			default -> PAPER_DIM;
		};
	}

	private static void bar(GuiGraphicsExtractor g, int x0, int y, int x1, int h, float share, int fill, int back) {
		g.fill(x0, y, x1, y + h, back);
		g.fill(x0, y, x0 + Math.round((x1 - x0) * Mth.clamp(share, 0.0F, 1.0F)), y + h, fill);
	}

	/** One 16 x 16 cell of the icon atlas (8 columns x 4 rows) drawn at {@code size}. */
	private static void ikon(GuiGraphicsExtractor g, int cell, int x, int y, int size, float alpha) {
		int c = Math.floorMod(cell, 32);
		g.blit(RenderPipelines.GUI_TEXTURED, IKON, x, y, (c % 8) * 16.0F, (c / 8) * 16.0F, size, size, 16, 16, 128, 64,
				Palette.argb(Math.round(alpha * 255.0F), 0xFFFFFF));
	}

	/** The sect's emblem as a drawn seal. */
	private void lambang(GuiGraphicsExtractor g, int packed, float cx, float cy, float r, float alpha) {
		Lambang l = Lambang.unpack(packed);
		Brush.disc(g, cx, cy, r + 1.0F, Palette.fade(Palette.opaque(l.borderRgb()), alpha));
		Brush.disc(g, cx, cy, r - Math.max(1.0F, r * 0.13F), Palette.fade(Palette.opaque(l.fieldRgb()), alpha));
		Component glyph = Component.literal(String.valueOf(SIMBOL.charAt(Math.floorMod(l.symbol(), SIMBOL.length()))));
		float s = r / 6.0F;
		Brush.text(g, font, glyph, cx - font.width(glyph) * s / 2.0F, cy - 4.0F * s, Palette.fade(Palette.opaque(l.chargeRgb()), alpha), s, false);
	}

	/** A history line: its key and stored arguments (names, nested sect names, towns, numbers). */
	private static Component baris(CompoundTag line) {
		ListTag args = line.getListOrEmpty("arg");
		Object[] parts = new Object[args.size()];
		for (int i = 0; i < parts.length; i++) {
			parts[i] = arg(args.getStringOr(i, ""));
		}
		return Component.translatable(SeteruIds.k(line.getStringOr("kunci", "")), parts);
	}

	static Object arg(String a) {
		if (a == null || a.isEmpty()) {
			return "";
		}
		try {
			if (a.startsWith("n:")) {
				return a.substring(2);
			}
			if (a.startsWith("@kota_nama:")) {
				return KotaNames.town(Long.parseLong(a.substring(11)));
			}
			if (a.indexOf('|') > 0 && a.startsWith("kultivator.")) {
				String[] part = a.split("\\|");
				Object[] inner = new Object[part.length - 1];
				for (int i = 1; i < part.length; i++) {
					inner[i - 1] = arg(part[i]);
				}
				return Component.translatable(part[0], inner);
			}
			if (a.indexOf(' ') < 0 && a.indexOf('.') > 0 && (a.startsWith("kultivator.") || a.startsWith("entity."))) {
				return Component.translatable(a);
			}
		} catch (RuntimeException exception) {
			return a;
		}
		return a;
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
		if (tab == 0 && mx < LIST_X1 + 4) {
			scroll -= (int) Math.signum(scrollY);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == com.mojang.blaze3d.platform.InputConstants.KEY_TAB) {
			pilihTab((tab + 1) % 3);
			return true;
		}
		if (key >= com.mojang.blaze3d.platform.InputConstants.KEY_1 && key <= com.mojang.blaze3d.platform.InputConstants.KEY_3) {
			pilihTab(key - com.mojang.blaze3d.platform.InputConstants.KEY_1);
			return true;
		}
		if (tab == 0 && (key == com.mojang.blaze3d.platform.InputConstants.KEY_DOWN || key == com.mojang.blaze3d.platform.InputConstants.KEY_UP)) {
			List<CompoundTag> feuds = list(sheet, "seteru");
			int at = 0;
			for (int i = 0; i < feuds.size(); i++) {
				if (feuds.get(i).getStringOr("id", "").equals(selected)) {
					at = i;
				}
			}
			at = Mth.clamp(at + (key == com.mojang.blaze3d.platform.InputConstants.KEY_DOWN ? 1 : -1), 0, Math.max(0, feuds.size() - 1));
			if (!feuds.isEmpty()) {
				selected = feuds.get(at).getStringOr("id", "");
				UiSound.tab();
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		super.removed();
		UiSound.close();
	}
}
