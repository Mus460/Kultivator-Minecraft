package dev.kultivator.client.tempa;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Fokus;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.tempa.ForgeMenu;
import dev.kultivator.tempa.Forging;
import dev.kultivator.tempa.Heat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Earthfire Forge screen (DESIGN 25.5.10, 43.8.5), one of the two container screens that stay outside the Gulungan
 * because of their slots, drawn with the same kit: a jade scroll of 270 x 214 (the slot layout is the menu's) with the
 * {@link TabBar} rail of four tablets on its left (Tempa Baru · Tempa Ulang · Roh · Cincin; locked while a job runs), the
 * material slots on the left, an anvil with the heat gauge and the progress ring in the middle, and the preview card
 * (lines from the server's plan, text 1.0 in a {@link DaftarGulir}) on the right. Talismans use vanilla's menu-button
 * packet; Runtuhkan Ruang asks a medium {@link SegelKonfirmasi}; the "?" seal opens the tab's Ensiklopedia topic. The
 * natal treasure's summary lives in Diri › Pusaka only; the Roh tab carries the actions.
 */
public class ForgeScreen extends AbstractContainerScreen<ForgeMenu> {

	private static final String K = "kultivator.gui43.tempa.";
	private static final int RAIL_W = 22;
	private static final int CARD_X = 150;
	private static final int CARD_Y = 20;
	private static final int CARD_W = 112;
	private static final int CARD_H = 106;
	private static final int ANVIL_X = 108;
	private static final int ANVIL_Y = 58;
	/** The two button columns under the grid and the anvil, and their two rows. */
	private static final int COL_L = 8;
	private static final int COL_L_W = 64;
	private static final int COL_C = 76;
	private static final int COL_C_W = 70;
	private static final int ROW_A = 92;
	private static final int ROW_B = 107;
	private static final int BTN_H = 13;
	private static final int[] TAB_ICONS = {Glyphs.BUILD, Glyphs.SEAL, Glyphs.TALENT, Glyphs.RING};
	private static final String[] TAB_KEYS = {"new", "refine", "spirit", "ring"};
	private static final String[] TOPICS = {"tempa", "tempa", "roh_senjata", "cincin"};
	private static final String[] SPIRIT = {"feed", "seed", "soul", "purify"};
	private static final int[] SPIRIT_IDS = {ForgeMenu.BTN_FEED, ForgeMenu.BTN_SEED, ForgeMenu.BTN_SOUL, ForgeMenu.BTN_PURIFY};

	private record Row(FormattedCharSequence text, int color) {
	}

	private final Fokus fokus = new Fokus(this::pesan);
	private final TempaGeser geser = new TempaGeser(this::tataUlang);
	private final TabBar rail = new TabBar();
	private final DaftarGulir kartu = new DaftarGulir();
	private final KeyHints.HelpSeal seal = new KeyHints.HelpSeal(0.0F, 0.0F, 13, () -> TOPICS[tab()]);
	private final JadeCoin natal;
	private final TalismanButton bentuk;
	private @Nullable Lukis frame;
	private @Nullable CompoundTag rowsOf;
	private List<Row> rows = List.of();
	private @Nullable Component pesan;
	private long pesanSampai;
	private int localTab;

	public ForgeScreen(ForgeMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, ForgeMenu.WIDTH, ForgeMenu.HEIGHT);
		this.localTab = menu.tab();
		this.natal = new JadeCoin(0, 0, COL_L_W, BTN_H, () -> Component.translatable("kultivator.tempa.btn.natal"), menu::natalTarget);
		natal.tooltip(() -> List.of(Component.translatable("kultivator.tempa.btn.natal"), Component.translatable("kultivator.tempa.btn.natal.tip")))
				.onPress(() -> click(ForgeMenu.BTN_NATAL));
		// The Kuali's embryo form: one cycling talisman instead of four coins (click forward, right click back).
		this.bentuk = new TalismanButton(0, 0, COL_L_W, BTN_H, () -> Component.translatable("kultivator.tempa.form." + ArtifactItem.FORMS.get(form())))
				.siklus(() -> click(ForgeMenu.BTN_KUALI_FORM + Math.floorMod(form() + 1, 4)), () -> click(ForgeMenu.BTN_KUALI_FORM + Math.floorMod(form() - 1, 4)));
		bentuk.tooltip(() -> List.of(Component.translatable(K + "bentuk", Component.translatable("kultivator.tempa.form." + ArtifactItem.FORMS.get(form()))),
				Component.translatable(K + "bentuk.tip")));
	}

	@Override
	protected void init() {
		super.init();
		tataUlang();
	}

	/**
	 * The panel's place: centred, or (a window shorter than the panel, e.g. GUI scale 4 on 1366x768) panned by {@link TempaGeser};
	 * the slots follow {@code topPos}, the widgets are moved here.
	 */
	private void tataUlang() {
		this.leftPos = (this.width - this.imageWidth + RAIL_W) / 2;
		this.topPos = geser.atas(this.height, this.imageHeight);
		natal.x = leftPos + COL_L;
		natal.y = topPos + ROW_B;
		bentuk.x = leftPos + COL_L;
		bentuk.y = topPos + ROW_A;
		seal.moveTo(leftPos + imageWidth - 13.0F, topPos + 13.0F);
	}

	/** Replaced by another screen without closing (the "?" seal opens the Gulungan): the server closes the menu too. */
	@Override
	public void removed() {
		super.removed();
		TempaClient.tutupDiServer(menu);
	}

	private int tab() {
		return menu.running() ? menu.tab() : localTab;
	}

	private static CompoundTag forge() {
		return TempaClient.forge();
	}

	private int form() {
		return Math.floorMod(forge().getIntOr("form", 0), 4);
	}

	private boolean kuali() {
		return forge().getIntOr("kind", 0) == Forging.KIND_KUALI;
	}

	private String startKey() {
		return switch (tab()) {
			case ForgeMenu.TAB_REFINE -> "kultivator.tempa.btn.refine";
			case ForgeMenu.TAB_RING -> "kultivator.tempa.btn.ring";
			default -> kuali() ? "kultivator.tempa.btn.kuali" : "kultivator.tempa.btn.forge";
		};
	}

	private void click(int id) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
		}
	}

	/** A refusal (a locked tab, a disabled talisman): shown in place of the heading for 3 s. */
	private void pesan(Component alasan) {
		pesan = alasan;
		pesanSampai = Util.getMillis() + 3000L;
	}

	// ================================================================== drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		SlipTooltip.mulaiBingkai();
		int x0 = leftPos;
		int y0 = topPos;
		int x1 = leftPos + imageWidth;
		int y1 = topPos + imageHeight;
		// the box is the visible part of the panel: the confirm centres in it, the pan seals sit at its edges
		Lukis l = fokus.mulai(g, font, mouseX, mouseY, 1.0F, Palette.Gaya.KERTAS, x0, Math.max(0, y0), x1, Math.min(height, y1));
		frame = l;
		drawRail(l);
		ScrollFrame.paper(g, x0, x1, y0, y1, y0, y1);
		ScrollFrame.rod(g, x0, x1, y0);
		ScrollFrame.rod(g, x0, x1, y1);
		if (pesan != null && Util.getMillis() < pesanSampai) {
			Brush.fitCentered(g, font, pesan, (x0 + x1) / 2.0F, y0 + 8.0F, imageWidth - 44.0F, Palette.DANGER, 1.0F, 1.0F, false);
		} else {
			Component heading = Brush.bold(Component.translatable("kultivator.tempa.forge.title", title,
					Component.translatable("kultivator.tempa.tab." + TAB_KEYS[tab()])));
			Brush.fitCentered(g, font, heading, (x0 + x1) / 2.0F, y0 + 6.0F, imageWidth - 44.0F, Palette.INK, 1.5F, 1.0F, false);
		}
		l.widget("bantuan", seal);
		geser.gambar(l, Math.max(2, leftPos - RAIL_W - 20));
		Brush.text(g, font, Component.translatable("kultivator.tempa.forge.materials"), x0 + ForgeMenu.GRID_X, y0 + ForgeMenu.GRID_Y - 10.0F,
				Palette.SEAL_DARK, 1.0F, false);
		for (Slot slot : menu.slots) {
			int sx = x0 + slot.x - 1;
			int sy = y0 + slot.y - 1;
			g.fill(sx, sy, sx + 18, sy + 18, Palette.argb(0x60, Palette.PAPER_DEEP));
			Brush.frame(g, sx, sy, sx + 18, sy + 18, Palette.argb(0xC0, Palette.GOLD_DARK));
			g.fill(sx + 1, sy + 1, sx + 17, sy + 2, 0x30000000);
			if (slot.index < ForgeMenu.INPUT && menu.running()) {
				g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x30B3281D);
			}
		}
		drawAnvil(l);
		drawCard(l);
		drawButtons(l);
	}

	/** The rail of four tablets left of the paper; while a job runs only its own tab stays open. */
	private void drawRail(Lukis l) {
		List<TabBar.Tab> tabs = new ArrayList<>(4);
		Component busy = menu.running() ? Component.translatable(K + "sibuk") : null;
		for (int i = 0; i < 4; i++) {
			TabBar.Tab t = TabBar.Tab.of(String.valueOf(i), Component.translatable("kultivator.tempa.tab." + TAB_KEYS[i]), TAB_ICONS[i]);
			tabs.add(i == tab() ? t : t.terkunci(busy));
		}
		rail.setTabs(tabs);
		int top = topPos + 14;
		rail.gambar(l, "tab", leftPos - RAIL_W + 1, top, leftPos + 1, top + 4 * 36 + 4, true, TabBar.Rupa.TABLET, false, String.valueOf(tab()), id -> {
			localTab = Integer.parseInt(id);
			click(localTab);
		});
	}

	/** The anvil over its fire, the heat gauge on its right and the progress ring around it. */
	private void drawAnvil(Lukis l) {
		GuiGraphicsExtractor g = l.g;
		float cx = leftPos + ANVIL_X;
		float cy = topPos + ANVIL_Y;
		int heat = menu.heat();
		int fire = Palette.opaque(Heat.COLORS[heat]);
		// Progress ring.
		Brush.ring(g, cx, cy, 24.0F, 26.5F, Palette.argb(0x50, Palette.INK_FAINT));
		if (menu.running()) {
			int color = menu.paused() ? Palette.LOCKED : fire;
			Brush.arc(g, cx, cy, 24.0F, 26.5F, 0.0F, Math.max(0.01F, menu.progress()), color);
		}
		// Flames under the anvil.
		float t = (l.kini % 100000L) / 1000.0F;
		for (int i = 0; i < 5; i++) {
			float fx = cx - 10.0F + i * 5.0F;
			float h = 5.0F + 3.0F * (float) Math.sin(t * 6.0F + i * 1.7F);
			Brush.rect(g, fx - 1.6F, cy + 16.0F - h, fx + 1.6F, cy + 16.0F, Palette.argb(0xC0, Palette.mix(fire, 0xFFFFF0B0, 0.3F)));
		}
		// The anvil: foot, waist, face and horn.
		int iron = 0xFF2B2F36;
		int ironLight = 0xFF4A505A;
		Brush.rect(g, cx - 11.0F, cy + 9.0F, cx + 11.0F, cy + 12.0F, iron);
		Brush.rect(g, cx - 5.0F, cy + 2.0F, cx + 5.0F, cy + 9.0F, iron);
		Brush.rect(g, cx - 13.0F, cy - 4.0F, cx + 9.0F, cy + 2.0F, iron);
		Brush.rect(g, cx - 13.0F, cy - 4.0F, cx + 9.0F, cy - 3.0F, ironLight);
		Brush.rect(g, cx + 9.0F, cy - 3.0F, cx + 15.0F, cy - 1.0F, iron);
		Brush.rect(g, cx + 15.0F, cy - 2.5F, cx + 17.0F, cy - 1.5F, iron);
		if (menu.running() && !menu.paused()) {
			Brush.halo(g, cx - 2.0F, cy - 4.0F, 4.0F, 6.0F, Heat.COLORS[heat], 0.45F);
		}
		// Heat gauge.
		int gx = leftPos + 138;
		int gTop = topPos + 26;
		int gBottom = topPos + 86;
		g.fill(gx, gTop, gx + 5, gBottom, Palette.argb(0x60, Palette.INK));
		int filled = Math.round((gBottom - gTop - 2) * (heat + 1) / 6.0F);
		g.fillGradient(gx + 1, gBottom - 1 - filled, gx + 4, gBottom - 1, fire, Palette.mix(fire, 0xFF401000, 0.5F));
		Brush.frame(g, gx, gTop, gx + 5, gBottom, Palette.GOLD_DARK);
		Component heatLine = Component.translatable("kultivator.tempa.forge.heat", Heat.name(heat), ArtifactItem.gradeName(heat));
		if (tab() != ForgeMenu.TAB_SPIRIT) {
			// The fire's name at 1.0; the cap it puts on the grade is the tooltip of the gauge and the name.
			l.tengah(Heat.name(heat), cx, topPos + 88.0F, COL_C_W, Palette.INK);
			if (l.di(leftPos + COL_C, topPos + 87, gx + 6, topPos + 97) || l.di(gx - 1, gTop, gx + 6, gBottom)) {
				l.tip(List.of(heatLine));
			}
		}
		if (menu.running()) {
			Component state = Component.translatable(menu.paused() ? "kultivator.tempa.forge.paused" : "kultivator.tempa.forge.working",
					Math.round(menu.progress() * 100.0F));
			l.tengah(state, cx, topPos + 20.0F, COL_C_W, menu.paused() ? Palette.DANGER : Palette.JADE_DARK);
		}
	}

	/** The preview card: the plan's title, then its lines and problem (as the server computed them) in a scrolling list. */
	private void drawCard(Lukis l) {
		GuiGraphicsExtractor g = l.g;
		int x0 = leftPos + CARD_X;
		int y0 = topPos + CARD_Y;
		int x1 = x0 + CARD_W;
		int y1 = y0 + CARD_H;
		g.fill(x0, y0, x1, y1, Palette.argb(0x40, Palette.PAPER_DEEP));
		Brush.frame(g, x0, y0, x1, y1, Palette.argb(0xC0, Palette.GOLD));
		CompoundTag card = forge();
		if (card.isEmpty()) {
			l.bungkus(Component.translatable("kultivator.tempa.preview.wait"), x0 + 4.0F, y0 + 4.0F, CARD_W - 8.0F, Palette.INK_SOFT);
			return;
		}
		Brush.rect(g, x0 + 3.0F, y0 + 5.0F, x0 + 6.0F, y0 + 8.0F, Palette.SEAL);
		l.teks(Brush.bold(Forging.component(card.getCompoundOrEmpty("title"))), x0 + 8.0F, y0 + 3.0F, CARD_W - 11.0F, Palette.SEAL_DARK);
		Brush.rect(g, x0 + 3.0F, y0 + 13.0F, x1 - 3.0F, y0 + 13.5F, Palette.argb(0x80, Palette.GOLD));
		List<Row> list = rows(card);
		kartu.gambar(l, "kartu", x0 + 3, y0 + 15, x1 - 2, y1 - 2, list.size(), 10,
				(ll, i, rx0, ry0, rx1, ry1, sorot, pilih) -> Brush.text(ll.g, font, list.get(i).text(), rx0 + 1.0F, ry0 + 1.0F, list.get(i).color(), 1.0F, false),
				null, null);
	}

	/** The card's lines wrapped at 1.0 (rebuilt only when a new card arrives); the problem last, in red. */
	private List<Row> rows(CompoundTag card) {
		if (card == rowsOf) {
			return rows;
		}
		int width = CARD_W - 16;
		List<Row> out = new ArrayList<>();
		ListTag lines = card.getListOrEmpty("lines");
		for (Tag entry : lines) {
			if (entry instanceof CompoundTag line) {
				int color = Palette.opaque(line.getIntOr("c", 0x1B2521));
				for (FormattedCharSequence part : font.split(Forging.component(line), width)) {
					out.add(new Row(part, color));
				}
			}
		}
		CompoundTag problem = card.getCompoundOrEmpty("problem");
		if (!problem.isEmpty()) {
			for (FormattedCharSequence part : font.split(Forging.component(problem), width)) {
				out.add(new Row(part, Palette.DANGER));
			}
		}
		kartu.mulaiUlang(Util.getMillis());
		rowsOf = card;
		rows = out;
		return out;
	}

	/** The reason the start talisman is dim: the plan's problem, else "put materials in". */
	private Component startReason() {
		CompoundTag problem = forge().getCompoundOrEmpty("problem");
		return problem.isEmpty() ? Component.translatable("kultivator.tempa.preview.wait") : Forging.component(problem);
	}

	private void drawButtons(Lukis l) {
		int x = leftPos;
		int y = topPos;
		int tab = tab();
		boolean running = menu.running();
		if (running) {
			talisman(l, "batal", x + COL_C, y + 104, COL_C_W, 16, Component.translatable("kultivator.tempa.btn.cancel"), null, true, null,
					() -> click(ForgeMenu.BTN_CANCEL), false);
		} else if (tab != ForgeMenu.TAB_SPIRIT) {
			boolean ok = forge().getBooleanOr("ok", false);
			talisman(l, "mulai", x + COL_C, y + 104, COL_C_W, 16, Component.translatable(startKey()), null, ok, ok ? null : startReason(),
					() -> click(ForgeMenu.BTN_START), ok);
		}
		switch (tab) {
			case ForgeMenu.TAB_NEW -> {
				if (kuali()) {
					l.widget("bentuk", bentuk);
				} else {
					assist(l);
					talisman(l, "lepas", x + COL_L, y + ROW_B, COL_L_W, BTN_H, Component.translatable("kultivator.tempa.btn.release"), null, true, null,
							() -> click(ForgeMenu.BTN_RELEASE), false);
				}
			}
			case ForgeMenu.TAB_REFINE -> {
				if (!kuali()) {
					assist(l);
				}
				l.widget("natal", natal);
			}
			case ForgeMenu.TAB_SPIRIT -> {
				boolean bound = forge().getBooleanOr("natal_bound", false);
				Component why = bound ? null : Component.translatable(K + "tanpa_natal");
				for (int i = 0; i < 4; i++) {
					int index = i;
					talisman(l, "roh" + i, x + (i < 2 ? COL_L : COL_C), y + (i % 2 == 0 ? ROW_A : ROW_B), i < 2 ? COL_L_W : COL_C_W, BTN_H,
							Component.translatable("kultivator.tempa.btn." + SPIRIT[i]), Component.translatable("kultivator.tempa.btn." + SPIRIT[i] + ".tip"),
							bound, why, () -> click(SPIRIT_IDS[index]), false);
				}
			}
			case ForgeMenu.TAB_RING -> {
				if (!running) {
					Component tip = Component.translatable("kultivator.tempa.btn.collapse.tip", Forging.COLLAPSE_STONES);
					talisman(l, "runtuh", x + COL_L, y + ROW_B, COL_L_W, BTN_H, Component.translatable("kultivator.tempa.btn.collapse"), tip, true, null,
							() -> fokus.konfirmasi(SegelKonfirmasi.Tingkat.SEDANG, Component.translatable(K + "runtuh.tanya", Forging.COLLAPSE_STONES),
									() -> click(ForgeMenu.BTN_COLLAPSE)), false);
				}
			}
			default -> {
			}
		}
	}

	private void assist(Lukis l) {
		talisman(l, "asisten", leftPos + COL_L, topPos + ROW_A, COL_L_W, BTN_H, Component.translatable("kultivator.tempa.btn.assist"),
				Component.translatable("kultivator.tempa.btn.assist.tip"), true, null, () -> click(ForgeMenu.BTN_ASSIST), false);
	}

	/** An immediate talisman whose tooltip is its whole name (it may be cut at 1.0), its explanation and its reason. */
	private static void talisman(Lukis l, String kunci, int x, int y, int w, int h, Component label, @Nullable Component tip, boolean aktif,
			@Nullable Component alasan, Runnable aksi, boolean mendesak) {
		if (TalismanButton.gambar(l, kunci, x, y, w, h, label, aktif, alasan, aksi, mendesak, false)) {
			List<Component> lines = new ArrayList<>(3);
			lines.add(label);
			if (tip != null) {
				lines.add(tip);
			}
			if (!aktif && alasan != null) {
				lines.add(alasan.copy().withColor(0xFFFF8A7A));
			}
			l.tip(lines);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		Brush.text(g, font, playerInventoryTitle, ForgeMenu.INV_X, ForgeMenu.INV_Y - 10.0F, Palette.INK_SOFT, 1.0F, false);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		Lukis l = frame;
		frame = null;
		if (l == null) {
			return;
		}
		// The confirm lies over the slots and items; the kit's tooltip only where no item tooltip shows.
		g.nextStratum();
		fokus.selesai(l, mouseX, mouseY);
		geser.ikuti(fokus.kunci(), fokus.zonaFokus(), height);
		if (fokus.segel().terbuka() || menu.getCarried().isEmpty() && (hoveredSlot == null || !hoveredSlot.hasItem())) {
			g.nextStratum();
			SlipTooltip.gambarAntrean(g, font, mouseX, mouseY, width, height);
		} else {
			SlipTooltip.mulaiBingkai();
		}
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (fokus.klik(event.x(), event.y(), event.button())) {
			return true;
		}
		fokus.set(null);
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (fokus.lepas(event.x(), event.y())) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (fokus.seret(event.x(), event.y(), dx)) {
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (fokus.gulir(x, y, scrollY)) {
			return true;
		}
		// a hovered bundle takes the wheel first; what is left pans the panel
		return super.mouseScrolled(x, y, scrollX, scrollY) || geser.roda(scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// The confirm takes every key (Esc cancels it); Tab / Enter / arrows walk the talismans and the rail.
		if (fokus.tombol(event)) {
			return true;
		}
		if (geser.tombol(event)) {
			return true;
		}
		return super.keyPressed(event);
	}
}
