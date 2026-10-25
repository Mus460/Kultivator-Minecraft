package dev.kultivator.client.formasi;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.hall.HallClient;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.InkField;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.spec.FormationSpec;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sekte › Formasi of the Gulungan Utama (DESIGN 24.7.1, 43.5, 43.12.2), drawn from the {@code formasi} part of
 * {@code HallInfo}. A header strip with the Kas Daya, the slots and the talismans <i>Setor dari tas</i> and <i>Isi
 * Semua</i>; the subs Inti, Kitab, Medan, Simpul and Catatan (the old seal column); clicking a core or a Kitab card opens the
 * bamboo slip ({@link FormationSlip}) and a transport node's <i>Peta</i> the node map ({@link NodeMap}), both as the page's
 * detail view (the host's back control and Esc close them). Sect mates see everything read-only except <i>Setor</i>.
 * Everything is drawn immediately; each clickable thing is a {@link Lukis.Zona} so Tab, Enter and the mouse reach it.
 * Showing a hologram or starting a node trip closes the Gulungan on purpose so the world can be seen (43.13 R32).
 */
public class FormasiPage extends Page {

	static final String[] PANELS = {"inti", "kitab", "medan", "simpul", "catatan"};
	static final int HEADER_H = 24;
	static final int BUTTON_H = 14;
	static final int LINE = 10;

	private static int panel;
	private static final Map<Integer, Float> SCROLL = new HashMap<>();
	private static int slipCore = -1;
	private static @Nullable String slipSpec;
	private static int nodeMapFrom = -1;

	/** The frame being drawn (valid inside {@link #lukis}). */
	private @Nullable Lukis l;
	private int hitCount;
	private int renaming = -1;
	// the scroll area of this frame (for the wheel)
	private int scrollX0;
	private int scrollY0;
	private int scrollX1;
	private int scrollY1;

	public FormasiPage() {
	}

	// ================================================================== identity

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.sekte.formasi");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine7c.tablet.short");
	}

	@Override
	public int glyph() {
		return Glyphs.SEAL;
	}

	@Override
	public String helpTopic() {
		return "formasi";
	}

	/** Hidden for players without a hall (43.5 "*"); shown while the details are still unknown so they can be asked for. */
	@Override
	public boolean tampil() {
		return HallClient.version() == 0L || HallClient.info().contains("formasi");
	}

	@Override
	public List<Sub> subs() {
		List<Sub> subs = new ArrayList<>(PANELS.length);
		for (String id : PANELS) {
			subs.add(new Sub(id, Component.translatable("kultivator.gui43.sub.formasi." + id)));
		}
		return subs;
	}

	@Override
	public void showSub(String id) {
		for (int i = 0; i < PANELS.length; i++) {
			if (PANELS[i].equals(id)) {
				if (panel != i) {
					renaming = -1;
				}
				panel = i;
				closeSlip();
				closeNodeMap();
				return;
			}
		}
	}

	@Override
	public boolean rinci() {
		return slipCore >= 0 || slipSpec != null || nodeMapFrom >= 0;
	}

	@Override
	public boolean mundur() {
		if (renaming >= 0) {
			renaming = -1;
			return true;
		}
		if (rinci()) {
			closeSlip();
			closeNodeMap();
			return true;
		}
		return false;
	}

	// ================================================================== life

	@Override
	protected void build() {
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		// HallClient asks again every 4 s while the Sekte section shows (43.0.12).
		HallClient.minta();
		takeNodeMap();
		super.onShow(fokus);
	}

	/** The server asked for the node map of a core ({@code FxB.NODE_MAP}); the Formasi page followed. */
	private void takeNodeMap() {
		int id = FormasiClient.takeNodeMap();
		if (id >= 0) {
			nodeMapFrom = id;
			panel = 3;
			slipCore = -1;
			slipSpec = null;
		}
	}

	// ================================================================== data

	CompoundTag info() {
		return HallClient.info();
	}

	CompoundTag f() {
		return info().getCompoundOrEmpty("formasi");
	}

	CompoundTag b() {
		return f().getCompoundOrEmpty("b");
	}

	/** True when we may toggle, fill and assign keepers (the owner). */
	boolean canEdit() {
		CompoundTag can = f().getCompoundOrEmpty("can");
		return can.contains("edit") ? can.getBooleanOr("edit", false) : !info().getBooleanOr("readonly", false);
	}

	boolean canDeposit() {
		return f().getCompoundOrEmpty("can").getBooleanOr("deposit", true);
	}

	int hallLevel() {
		return Math.max(1, f().getIntOr("hall", info().getIntOr("level", 1)));
	}

	/** The core tag of {@code id} in the hall info, or null. */
	@Nullable CompoundTag core(int id) {
		for (Tag tag : f().getListOrEmpty("cores")) {
			if (tag instanceof CompoundTag core && core.getIntOr("id", -1) == id) {
				return core;
			}
		}
		return null;
	}

	/** True when a running core of {@code specId} is registered (Kitab "Butuh" checks). */
	boolean running(String specId) {
		for (Tag tag : f().getListOrEmpty("cores")) {
			if (tag instanceof CompoundTag core && core.getStringOr("spec", "").equals(specId)) {
				int st = core.getIntOr("st", 0);
				if (st == 4 || st == 5) {
					return true;
				}
			}
		}
		return false;
	}

	/** A component field written by the server ({@code why}, {@code n}), or its plain string. */
	static Component text(CompoundTag tag, String key) {
		Component read = tag.read(key, ComponentSerialization.CODEC).orElse(null);
		if (read != null) {
			return read;
		}
		String plain = tag.getStringOr(key, "");
		return plain.isEmpty() ? Component.empty() : Component.literal(plain);
	}

	/** "1.240" style numbers of whole Daya (the hall info already sends whole Daya). */
	static String daya(long whole) {
		return String.format(Locale.ROOT, "%,d", Math.max(0L, whole)).replace(',', '.');
	}

	/** "12 j 30 m" for hours left; "∞" when nothing drains (hrs −1). */
	static Component hours(double hours) {
		if (hours < 0.0D || Double.isInfinite(hours)) {
			return Component.translatable("kultivator.formasiui.hours.infinite");
		}
		long minutes = Math.round(hours * 60.0D);
		return Component.translatable("kultivator.formasiui.hours", minutes / 60L, minutes % 60L);
	}

	static String decimal(double value) {
		return String.format(Locale.ROOT, "%.2f", value).replace('.', ',');
	}

	void send(String text) {
		FormasiClient.send(text);
	}

	Font font() {
		return font;
	}

	/** The list of legacy rows (Pelindung, Agung) written by module A. */
	ListTag legacy() {
		return f().getListOrEmpty("legacy");
	}

	// ================================================================== slip, node map, rename

	void openCoreSlip(int id) {
		slipCore = id;
		slipSpec = null;
		SCROLL.put(100, 0.0F);
	}

	void openKitabSlip(FormationSpec spec) {
		slipCore = -1;
		slipSpec = spec.id();
		SCROLL.put(100, 0.0F);
	}

	void closeSlip() {
		slipCore = -1;
		slipSpec = null;
	}

	void openNodeMap(int fromId) {
		nodeMapFrom = fromId;
	}

	void closeNodeMap() {
		nodeMapFrom = -1;
	}

	void startRename(int id, String current) {
		renaming = id;
		InkField field = renameField();
		field.setText(current == null ? "" : current);
		field.setFocused(id >= 0);
	}

	int renaming() {
		return renaming;
	}

	/** The one rename field (kept by the focus ring across frames): Enter saves; Esc leaves it, a second Esc cancels. */
	InkField renameField() {
		return fokusRing.simpan("formasi:rename", () -> new InkField(0, 0, 40, 12, 24, text -> {
			if (renaming >= 0) {
				send("node:" + renaming + ":" + text.trim());
				renaming = -1;
			}
		}));
	}

	/** Draws the rename field into its box (called by the node panel while renaming). */
	void drawRenameField(int x0, int y0, int x1, int y1) {
		InkField field = renameField();
		field.x = x0;
		field.y = y0;
		field.w = Math.max(TalismanButton.MIN_W, x1 - x0);
		field.h = Math.max(TalismanButton.MIN_H, y1 - y0);
		if (l != null) {
			l.widget("formasi:rename", field);
		}
	}

	/** R32: a hologram or a node trip closes the Gulungan so the world shows; a Kabar card says why. */
	static void closeToWorld(Component why) {
		HubNav.tutupSemua();
		dev.kultivator.client.hud.Kabar.kirim(null, why, dev.kultivator.client.hud.Kabar.glyph(Glyphs.SEAL), false);
	}

	// ================================================================== zones and scrolling (used by the panels)

	/** A clickable / hoverable box: a zone of this frame (Tab-focusable when it acts) with its tooltip. */
	void hit(float x0, float y0, float x1, float y1, @Nullable Runnable action, @Nullable List<Component> tip) {
		if (l == null) {
			return;
		}
		int ix0 = Math.round(x0);
		int iy0 = Math.round(y0);
		int ix1 = Math.round(x1);
		int iy1 = Math.round(y1);
		l.zona("f" + panelKey() + ":" + hitCount++, ix0, iy0, ix1, iy1, action, true, null, action != null);
		if (tip != null && l.di(ix0, iy0, ix1, iy1)) {
			l.tip(tip);
		}
		if (action != null) {
			l.fokusCincin("f" + panelKey() + ":" + (hitCount - 1), ix0, iy0, ix1, iy1);
		}
	}

	private String panelKey() {
		return nodeMapFrom >= 0 ? "m" : rinci() ? "s" : Integer.toString(panel);
	}

	/** Clips the drawing and the zones to a box (lists that scroll). */
	void klip(int x0, int y0, int x1, int y1) {
		if (l != null) {
			l.klip(x0, y0, x1, y1);
		}
	}

	void lepasKlip() {
		if (l != null) {
			l.lepasKlip();
		}
	}

	float scroll(int key) {
		return SCROLL.getOrDefault(key, 0.0F);
	}

	/** Clamps the scroll of {@code key} to the content, remembers the area for the wheel and returns the offset. */
	float clampScroll(int key, float content, float view) {
		float value = Math.max(0.0F, Math.min(Math.max(0.0F, content - view), scroll(key)));
		SCROLL.put(key, value);
		return value;
	}

	/** The box the wheel scrolls this frame. */
	void scrollArea(int x0, int y0, int x1, int y1) {
		scrollX0 = x0;
		scrollY0 = y0;
		scrollX1 = x1;
		scrollY1 = y1;
	}

	private int scrollKey() {
		if (nodeMapFrom >= 0) {
			return -1;
		}
		return slipCore >= 0 || slipSpec != null ? 100 : panel;
	}

	/** PgUp / PgDn / Home / End scroll the shown list after the focus ring's own keys (43.0.7; clamped by the next frame). */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		}
		if (mauTombol()) {
			return false;
		}
		int key = scrollKey();
		if (key < 0) {
			return false;
		}
		float page = Math.max(18.0F, scrollY1 - scrollY0 - 18.0F);
		switch (event.key()) {
			case InputConstants.KEY_PAGEDOWN -> SCROLL.put(key, scroll(key) + page);
			case InputConstants.KEY_PAGEUP -> SCROLL.put(key, Math.max(0.0F, scroll(key) - page));
			case InputConstants.KEY_HOME -> SCROLL.put(key, 0.0F);
			case InputConstants.KEY_END -> SCROLL.put(key, Float.MAX_VALUE / 4.0F);
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (modal()) {
			return true;
		}
		if (!inside(mouseX, mouseY)) {
			return false;
		}
		if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
			return true;
		}
		int key = scrollKey();
		if (key >= 0 && mouseX >= scrollX0 && mouseX < scrollX1 && mouseY >= scrollY0 && mouseY < scrollY1) {
			SCROLL.put(key, scroll(key) - (float) scrollY * 18.0F);
		}
		return true;
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		this.l = l;
		hitCount = 0;
		scrollArea(0, 0, 0, 0);
		takeNodeMap();
		GuiGraphicsExtractor g = l.g;
		if (HallClient.version() == 0L || !info().contains("formasi")) {
			l.samar(Component.translatable("kultivator.gui43.m07.memuat"), x + 4, y + 4, w - 8);
			this.l = null;
			return;
		}
		drawHeader(l);
		int py = y + HEADER_H;
		int ph = h - HEADER_H;
		if (nodeMapFrom >= 0) {
			NodeMap.draw(this, g, x, py, w, ph, l.mx, l.my, nodeMapFrom);
			this.l = null;
			return;
		}
		if (slipCore >= 0 || slipSpec != null) {
			FormationSpec kitab = slipSpec == null ? null : FormationRegistry.byId(slipSpec);
			CompoundTag core = slipCore >= 0 ? core(slipCore) : null;
			if (core == null && kitab == null) {
				closeSlip();
			} else {
				klip(x, py, x + w, py + ph);
				FormationSlip.draw(this, g, x, py, w, ph, l.mx, l.my, core, kitab);
				lepasKlip();
				this.l = null;
				return;
			}
		}
		klip(x, py, x + w, py + ph);
		switch (panel) {
			case 0 -> CorePanel.draw(this, g, x, py, w, ph, l.mx, l.my);
			case 1 -> CodexPanel.draw(this, g, x, py, w, ph, l.mx, l.my, l);
			case 2 -> FieldPanel.draw(this, g, x, py, w, ph, l.mx, l.my);
			case 3 -> NodePanel.draw(this, g, x, py, w, ph, l.mx, l.my);
			default -> LogPanel.draw(this, g, x, py, w, ph, l.mx, l.my, l);
		}
		lepasKlip();
		this.l = null;
	}

	/** Kas Daya, slots and limits, and the two talismans. */
	private void drawHeader(Lukis l) {
		CompoundTag f = f();
		CompoundTag kas = f.getCompoundOrEmpty("kas");
		long a = kas.getLongOr("a", 0L);
		long s = kas.getLongOr("s", 0L);
		CompoundTag slots = f.getCompoundOrEmpty("slots");
		CompoundTag lim = f.getCompoundOrEmpty("lim");
		int buttonsW = Math.max(2 * TalismanButton.MIN_W + 3, Math.min(160, Math.round(w * 0.42F)));
		int textW = w - buttonsW - 6;
		Brush.rect(l.g, x, y + 2.0F, x + 4.0F, y + 6.0F, Palette.SEAL);
		Component kasLine = Component.translatable("kultivator.formasiui.header.kas", daya(a + s), daya(a), daya(s));
		l.teks(Brush.bold(kasLine), x + 6, y, textW - 6, Palette.SEAL_DARK);
		l.teks(Component.translatable("kultivator.formasiui.header.slots", slots.getIntOr("active", 0), slots.getIntOr("max", 0),
				lim.getIntOr("used", 0), lim.getIntOr("cores", 0), hallLevel()), x + 6, y + LINE, textW - 6, l.gaya.teksSamar);
		l.g.fill(x, y + HEADER_H - 3, x + w, y + HEADER_H - 2, Palette.argb(0xB0, Palette.GOLD));
		int bx = x + w - buttonsW;
		int half = (buttonsW - 3) / 2;
		talisman(l.g, bx, y + 2, half, BUTTON_H, Component.translatable("kultivator.formasiui.deposit"), canDeposit(), () -> send("deposit:all"),
				l.mx, l.my, List.of(Component.translatable("kultivator.formasiui.deposit"), Component.translatable("kultivator.formasiui.deposit.tip")));
		talisman(l.g, bx + half + 3, y + 2, buttonsW - half - 3, BUTTON_H, Component.translatable("kultivator.formasiui.fill_all"), canEdit(),
				() -> send("fillall"), l.mx, l.my,
				List.of(Component.translatable("kultivator.formasiui.fill_all"), Component.translatable("kultivator.formasiui.fill_all.tip")));
	}

	// ================================================================== shared drawing helpers (panels, slip, map)

	/** A heading: seal stamp, bold text in seal red (1.0) and a gold rule under it; 12 px high. */
	void title(GuiGraphicsExtractor g, Component text, float hx, float hy, float width, float scale) {
		if (l != null) {
			l.kepala(text, Math.round(hx), Math.round(hy), Math.round(hx + width));
		}
	}

	/**
	 * A talisman of the kit ({@link TalismanButton#gambar}, at least 16×12), disabled when {@code enabled} is false. Returns
	 * its height.
	 */
	float talisman(GuiGraphicsExtractor g, float bx, float by, float bw, float bh, Component label, boolean enabled, Runnable action, int mouseX,
			int mouseY, @Nullable List<Component> tip) {
		if (l == null) {
			return bh;
		}
		String key = "ft" + panelKey() + ":" + hitCount++;
		int x0 = Math.round(bx);
		int y0 = Math.round(by);
		int w0 = Math.max(TalismanButton.MIN_W, Math.round(bw));
		int h0 = Math.max(TalismanButton.MIN_H, Math.round(bh));
		boolean over = TalismanButton.gambar(l, key, x0, y0, w0, h0, label, enabled, enabled ? null : Component.translatable("kultivator.gui43.m07.baca_saja"),
				action);
		if (over && tip != null) {
			l.tip(tip);
		}
		return h0;
	}

	/** Per-key state of an immediate jade coin. */
	private static final class Koin {
		Component label = Component.empty();
		boolean on;
		boolean aktif = true;
		@Nullable Runnable aksi;
		final JadeCoin widget;

		Koin() {
			widget = new JadeCoin(0, 0, TalismanButton.MIN_W, TalismanButton.MIN_H, () -> label, () -> on);
			widget.enabledWhen(() -> aktif);
			widget.onPress(() -> {
				if (aksi != null) {
					aksi.run();
				}
			});
		}
	}

	/** A jade coin of the kit (on/off), drawn immediately and kept in the Tab ring. */
	void coin(GuiGraphicsExtractor g, float cx0, float cy0, float cw, float ch, Component label, boolean on, boolean enabled, Runnable action, int mouseX,
			int mouseY, @Nullable List<Component> tip) {
		if (l == null) {
			return;
		}
		String key = "fc" + panelKey() + ":" + hitCount++;
		Koin koin = fokusRing.simpan("koin:" + key, Koin::new);
		koin.label = label;
		koin.on = on;
		koin.aktif = enabled;
		koin.aksi = action;
		koin.widget.x = Math.round(cx0);
		koin.widget.y = Math.round(cy0);
		koin.widget.w = Math.max(TalismanButton.MIN_W, Math.round(cw));
		koin.widget.h = Math.max(TalismanButton.MIN_H, Math.round(ch));
		l.widget(key, koin.widget);
		if (tip != null && l.di(koin.widget.x, koin.widget.y, koin.widget.x + koin.widget.w, koin.widget.y + koin.widget.h)) {
			l.tip(tip);
		}
	}

	/** Body text at 1.0, cut with "…" (the whole text then becomes the tooltip). */
	void line(Component text, float tx, float ty, float width, int color) {
		if (l != null) {
			l.teks(text, tx, ty, width, color);
		}
	}

	/** Wrapped body text at 1.0; returns its height. */
	float wrap(Component text, float tx, float ty, float width, int color) {
		return l == null ? 0.0F : l.bungkus(text, tx, ty, width, color);
	}

	/** Wrapped body text kept inside {@code maxHeight} (the last line cut with "…", the whole text as tooltip). */
	float wrap(Component text, float tx, float ty, float width, int color, float maxHeight) {
		return l == null ? 0.0F : l.bungkus(text, tx, ty, width, color, maxHeight);
	}

	/** A thin labelled bar (Daya, fill). */
	static void bar(GuiGraphicsExtractor g, float x0, float y0, float width, float height, float fraction, int color) {
		Brush.rect(g, x0, y0, x0 + width, y0 + height, Palette.mix(Palette.PAPER_BOTTOM, Palette.INK, 0.25F));
		float fill = Math.max(0.0F, Math.min(1.0F, fraction));
		if (fill > 0.0F) {
			Brush.rect(g, x0 + 0.5F, y0 + 0.5F, x0 + 0.5F + (width - 1.0F) * fill, y0 + height - 0.5F, color);
		}
	}

	/** The state seal colour of a core state ordinal (grey Tertidur, red Rusak, ink Tertahan, amber Aktif, gold Berdaya, violet Dijaga). */
	static int stateColor(int state) {
		return switch (state) {
			case 1 -> Palette.DANGER;
			case 2 -> Palette.INK_SOFT;
			case 3 -> Palette.mix(Palette.WARN, Palette.GOLD_LIGHT, 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 200.0D));
			case 4 -> Palette.GOLD;
			case 5 -> 0xFF9A6AE0;
			default -> Palette.LOCKED;
		};
	}

	/** The translated state name ({@code kultivator.formasi.state.*}, module A's text). */
	static Component stateName(int state) {
		return dev.kultivator.formasi.core.CoreState.byOrdinal(state).label();
	}

	/** A small round state seal (colour only; the state's name is written beside it or in the tooltip, 43.0.5). */
	static void seal(GuiGraphicsExtractor g, Font font, Component word, float cx, float cy, float r, int color) {
		Brush.disc(g, cx, cy, r, Palette.argb(0xD8, color & 0xFFFFFF));
		Brush.ring(g, cx, cy, r - 1.2F, r - 0.6F, Palette.argb(0xA0, 0xFFF4D6));
		Brush.disc(g, cx, cy, Math.max(1.0F, r * 0.3F), 0xFFFFF4D6);
	}

	/** A scroll bead on the right edge of a list. */
	static void bead(GuiGraphicsExtractor g, int x1, int y0, int y1, float offset, float maxScroll) {
		if (maxScroll <= 0.0F) {
			return;
		}
		float bead = y0 + 3.0F + (y1 - y0 - 6.0F) * (offset / maxScroll);
		g.fill(x1 - 2, y0, x1 - 1, y1, Palette.CORD);
		Brush.disc(g, x1 - 1.5F, bead, 2.0F, Palette.JADE);
	}
}
