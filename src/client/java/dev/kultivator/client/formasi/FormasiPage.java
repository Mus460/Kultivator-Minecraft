package dev.kultivator.client.formasi;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.hall.HallPage;
import dev.kultivator.client.hall.HallScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.spec.FormationSpec;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
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
 * The Formasi tablet of the hall scroll (DESIGN 24.7.1, owned by P7c-katalog-ui): drawn third, after Bangun, from the
 * {@code formasi} part of {@code HallInfo}. A header strip with the Kas Daya, the slots and the talismans <i>Setor dari
 * tas</i> and <i>Isi Semua</i>; a seal column on the right edge picks the panel (Inti, Kitab, Medan, Simpul, Catatan);
 * clicking a core or a Kitab card opens the bamboo slip ({@link FormationSlip}); a transport eye opens the node map
 * ({@link NodeMap}). Sect mates see everything read-only except <i>Setor</i>. Shown only once the server sends the
 * {@code formasi} part. Jade scroll of DESIGN 5b; fits 320×240 (lists scroll with the wheel).
 */
public class FormasiPage extends HallPage {

	static final String[] PANELS = {"inti", "kitab", "medan", "simpul", "catatan"};
	private static final int[] PANEL_ICONS = {Glyphs.SEAL, Glyphs.CODEX, Glyphs.STATUS, Glyphs.JALAN, Glyphs.HELP};
	static final int SEAL_W = 15;
	static final int HEADER_H = 22;
	static final float TEXT = 0.6F;

	private static int panel;
	private static final Map<Integer, Float> SCROLL = new HashMap<>();
	private static int slipCore = -1;
	private static @Nullable String slipSpec;
	private static int nodeMapFrom = -1;

	/** A clickable area drawn this frame (rows, cards, seals, talismans). */
	record Hit(float x0, float y0, float x1, float y1, @Nullable Runnable action, @Nullable List<Component> tip) {
	}

	private final List<Hit> hits = new ArrayList<>();
	private int renaming = -1;
	private String renameText = "";

	public FormasiPage(HallScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine7c.tablet");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine7c.tablet.short");
	}

	@Override
	public int icon() {
		return Glyphs.SEAL;
	}

	@Override
	public boolean shown() {
		return info().contains("formasi");
	}

	@Override
	protected void build() {
		takeNodeMap();
	}

	@Override
	public void onInfo() {
		takeNodeMap();
	}

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

	void tip(List<Component> lines) {
		screen.showTooltip(lines);
	}

	// ================================================================== slip, node map, rename

	void openCoreSlip(int id) {
		slipCore = id;
		slipSpec = null;
	}

	void openKitabSlip(FormationSpec spec) {
		slipCore = -1;
		slipSpec = spec.id();
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
		renameText = current == null ? "" : current;
	}

	int renaming() {
		return renaming;
	}

	String renameText() {
		return renameText;
	}

	@Override
	public boolean capturesKeys() {
		return renaming >= 0;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (renaming < 0) {
			if (event.key() == InputConstants.KEY_ESCAPE && (slipCore >= 0 || slipSpec != null || nodeMapFrom >= 0)) {
				closeSlip();
				closeNodeMap();
				return true;
			}
			return false;
		}
		int key = event.key();
		if (key == InputConstants.KEY_ESCAPE) {
			renaming = -1;
		} else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			send("node:" + renaming + ":" + renameText.trim());
			renaming = -1;
		} else if (key == InputConstants.KEY_BACKSPACE && !renameText.isEmpty()) {
			renameText = renameText.substring(0, renameText.length() - 1);
		}
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (renaming < 0) {
			return false;
		}
		int c = event.codepoint();
		if (renameText.length() < 24 && Character.isBmpCodePoint(c) && (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_')) {
			renameText = renameText + (char) c;
		}
		return true;
	}

	// ================================================================== hits and scrolling

	void hit(float x0, float y0, float x1, float y1, @Nullable Runnable action, @Nullable List<Component> tip) {
		hits.add(new Hit(x0, y0, x1, y1, action, tip));
	}

	float scroll(int key) {
		return SCROLL.getOrDefault(key, 0.0F);
	}

	/** Clamps the scroll of {@code key} to the content and returns it. */
	float clampScroll(int key, float content, float view) {
		float value = Math.max(0.0F, Math.min(Math.max(0.0F, content - view), scroll(key)));
		SCROLL.put(key, value);
		return value;
	}

	/** The scroll key of the list under the mouse now. */
	private int scrollKey() {
		if (nodeMapFrom >= 0) {
			return -1;
		}
		if (slipCore >= 0 || slipSpec != null) {
			return 100;
		}
		return panel;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (!inside(mouseX, mouseY)) {
			return false;
		}
		int key = scrollKey();
		if (key < 0) {
			return true;
		}
		SCROLL.put(key, scroll(key) - (float) amount * 14.0F);
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.action() != null && mouseX >= hit.x0() && mouseX < hit.x1() && mouseY >= hit.y0() && mouseY < hit.y1()) {
				UiSound.press();
				hit.action().run();
				return true;
			}
		}
		if (renaming >= 0) {
			renaming = -1;
		}
		return false;
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		hits.clear();
		takeNodeMap();
		drawHeader(g, mouseX, mouseY);
		int px = x;
		int py = y + HEADER_H;
		int pw = w - SEAL_W - 3;
		int ph = h - HEADER_H;
		if (nodeMapFrom >= 0) {
			NodeMap.draw(this, g, x, py, w, ph, mouseX, mouseY, nodeMapFrom);
			return;
		}
		drawSeals(g, mouseX, mouseY, py, ph);
		if (slipCore >= 0 || slipSpec != null) {
			FormationSpec kitab = slipSpec == null ? null : FormationRegistry.byId(slipSpec);
			CompoundTag core = slipCore >= 0 ? core(slipCore) : null;
			if (core == null && kitab == null) {
				closeSlip();
			} else {
				g.enableScissor(px, py, px + pw, py + ph);
				FormationSlip.draw(this, g, px, py, pw, ph, mouseX, mouseY, core, kitab);
				g.disableScissor();
				return;
			}
		}
		g.enableScissor(px, py, px + pw, py + ph);
		switch (panel) {
			case 0 -> CorePanel.draw(this, g, px, py, pw, ph, mouseX, mouseY);
			case 1 -> CodexPanel.draw(this, g, px, py, pw, ph, mouseX, mouseY);
			case 2 -> FieldPanel.draw(this, g, px, py, pw, ph, mouseX, mouseY);
			case 3 -> NodePanel.draw(this, g, px, py, pw, ph, mouseX, mouseY);
			default -> LogPanel.draw(this, g, px, py, pw, ph, mouseX, mouseY);
		}
		g.disableScissor();
	}

	@Override
	protected void drawAbove(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		for (Hit hit : hits) {
			if (hit.tip() != null && mouseX >= hit.x0() && mouseX < hit.x1() && mouseY >= hit.y0() && mouseY < hit.y1()) {
				screen.showTooltip(hit.tip());
				break;
			}
		}
	}

	/** Kas Daya, slots and limits, and the two talismans. */
	private void drawHeader(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		CompoundTag f = f();
		CompoundTag kas = f.getCompoundOrEmpty("kas");
		long a = kas.getLongOr("a", 0L);
		long s = kas.getLongOr("s", 0L);
		CompoundTag slots = f.getCompoundOrEmpty("slots");
		CompoundTag lim = f.getCompoundOrEmpty("lim");
		int buttonsW = Math.min(150, Math.round(w * 0.42F));
		float textW = w - buttonsW - 6.0F;
		Brush.rect(g, x, y, x + 4.0F, y + 4.0F, Palette.SEAL);
		Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.formasiui.header.kas", daya(a + s), daya(a), daya(s))), x + 6.0F, y,
				textW - 6.0F, Palette.SEAL_DARK, 0.68F, 0.42F, false);
		Brush.fit(g, font, Component.translatable("kultivator.formasiui.header.slots", slots.getIntOr("active", 0), slots.getIntOr("max", 0),
				lim.getIntOr("used", 0), lim.getIntOr("cores", 0), hallLevel()), x + 6.0F, y + 9.0F, textW - 6.0F, Palette.INK_SOFT, TEXT, 0.4F, false);
		Brush.rect(g, x, y + HEADER_H - 3.0F, x + w, y + HEADER_H - 2.4F, Palette.argb(0xB0, Palette.GOLD));
		float bx = x + w - buttonsW;
		float half = (buttonsW - 3.0F) / 2.0F;
		talisman(g, bx, y, half, 11.0F, Component.translatable("kultivator.formasiui.deposit"), canDeposit(), () -> send("deposit:all"), mouseX, mouseY,
				List.of(Component.translatable("kultivator.formasiui.deposit"), Component.translatable("kultivator.formasiui.deposit.tip")));
		talisman(g, bx + half + 3.0F, y, half, 11.0F, Component.translatable("kultivator.formasiui.fill_all"), canEdit(), () -> send("fillall"), mouseX,
				mouseY, List.of(Component.translatable("kultivator.formasiui.fill_all"), Component.translatable("kultivator.formasiui.fill_all.tip")));
	}

	/** The seal column on the right edge. */
	private void drawSeals(GuiGraphicsExtractor g, int mouseX, int mouseY, int top, int height) {
		int sx = x + w - SEAL_W;
		int count = PANELS.length;
		int sealH = Math.max(14, Math.min(24, (height - 2) / count));
		for (int i = 0; i < count; i++) {
			int sy = top + i * sealH;
			boolean active = panel == i && slipCore < 0 && slipSpec == null;
			boolean over = mouseX >= sx && mouseX < sx + SEAL_W && mouseY >= sy && mouseY < sy + sealH - 1;
			int base = active ? Palette.SEAL : over ? Palette.mix(Palette.SEAL, Palette.SEAL_GLOW, 0.3F) : Palette.argb(0xC0, Palette.SEAL_DARK);
			int out = active ? -2 : 0;
			g.fill(sx + out, sy, sx + SEAL_W, sy + sealH - 1, base);
			Brush.frame(g, sx + out + 1, sy + 1, sx + SEAL_W - 1, sy + sealH - 2, Palette.argb(0xB0, Palette.TALISMAN));
			Glyphs.tab(g, PANEL_ICONS[i], sx + out / 2.0F + SEAL_W / 2.0F, sy + (sealH - 1) / 2.0F, Math.min(8.0F, sealH - 6.0F), Palette.TALISMAN);
			int index = i;
			hit(sx - 2, sy, sx + SEAL_W, sy + sealH - 1, () -> {
				panel = index;
				closeSlip();
				UiSound.tab();
			}, List.of(Component.translatable("kultivator.formasiui.panel." + PANELS[i]),
					Component.translatable("kultivator.formasiui.panel." + PANELS[i] + ".tip")));
		}
	}

	// ================================================================== shared drawing helpers (panels, slip, map)

	/** A heading in seal red with a gold rule under it. */
	void title(GuiGraphicsExtractor g, Component text, float hx, float hy, float width, float scale) {
		heading(g, text, hx, hy, width, scale);
	}

	/** A faint parchment box with a thin gold frame. */
	static void box(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		panel(g, x0, y0, x1, y1);
	}

	/**
	 * A talisman strip (paper, red seal stamp, ink label) registered as a hit; greyed when disabled. Returns the height.
	 */
	float talisman(GuiGraphicsExtractor g, float bx, float by, float bw, float bh, Component label, boolean enabled, Runnable action, int mouseX,
			int mouseY, @Nullable List<Component> tip) {
		boolean over = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
		int x0 = Math.round(bx);
		int y0 = Math.round(by);
		int x1 = Math.round(bx + bw);
		int y1 = Math.round(by + bh);
		g.fill(x0 + 1, y0 + 2, x1 + 1, y1 + 1, 0x40000000);
		int top = enabled ? Palette.lighten(Palette.TALISMAN, over ? 0.12F : 0.0F) : Palette.grey(Palette.TALISMAN, 0.7F);
		int bottom = enabled ? Palette.TALISMAN_LOW : Palette.grey(Palette.TALISMAN_LOW, 0.7F);
		g.fillGradient(x0, y0, x1, y1, top, bottom);
		Brush.frame(g, x0, y0, x1, y1, enabled ? Palette.TALISMAN_EDGE : Palette.grey(Palette.TALISMAN_EDGE, 0.8F));
		int size = Math.max(5, Math.round(bh) - 5);
		int sx = x0 + 2;
		int sy = y0 + (y1 - y0 - size) / 2;
		int seal = enabled ? (over ? Palette.mix(Palette.SEAL, Palette.SEAL_GLOW, 0.5F) : Palette.SEAL) : Palette.grey(Palette.SEAL, 0.75F);
		g.fill(sx, sy, sx + size, sy + size, seal);
		Brush.frame(g, sx + 1, sy + 1, sx + size - 1, sy + size - 1, Palette.argb(0xB0, Palette.TALISMAN));
		float room = x1 - sx - size - 5;
		float scale = Brush.fitScale(font, label, room, 0.8F, 0.45F);
		Brush.fit(g, font, label, sx + size + 3, y0 + (bh - 8.0F * scale) / 2.0F, room, enabled ? Palette.INK : Palette.INK_FAINT, 0.8F, 0.45F, false);
		hit(bx, by, bx + bw, by + bh, enabled ? action : () -> UiSound.denied(), tip);
		return bh;
	}

	/** A round jade coin (on/off toggle) with its label, registered as a hit. */
	void coin(GuiGraphicsExtractor g, float cx0, float cy0, float cw, float ch, Component label, boolean on, boolean enabled, Runnable action, int mouseX,
			int mouseY, @Nullable List<Component> tip) {
		float r = ch / 2.0F - 0.5F;
		float cx = cx0 + ch / 2.0F;
		float cy = cy0 + ch / 2.0F;
		boolean over = mouseX >= cx0 && mouseX < cx0 + cw && mouseY >= cy0 && mouseY < cy0 + ch;
		if (over && enabled) {
			Brush.halo(g, cx, cy, r, 2.5F, 0x9CFFD0, 0.8F);
		}
		Brush.disc(g, cx, cy, r, on ? Palette.GOLD : Palette.JADE_DULL);
		Brush.disc(g, cx, cy, r - 1.0F, on ? Palette.JADE_LIGHT : 0xFFA9B5AE);
		float hole = Math.max(1.0F, r * 0.35F);
		Brush.rect(g, cx - hole, cy - hole, cx + hole, cy + hole, on ? Palette.GOLD_LIGHT : Palette.PAPER_SHADE);
		float tx = cx0 + ch + 3.0F;
		Brush.fit(g, font, label, tx, cy0 + (ch - 8.0F * 0.7F) / 2.0F, cx0 + cw - tx, enabled ? Palette.INK : Palette.INK_FAINT, 0.7F, 0.42F, false);
		hit(cx0, cy0, cx0 + cw, cy0 + ch, enabled ? () -> {
			UiSound.toggle(!on);
			action.run();
		} : () -> UiSound.denied(), tip);
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

	/** A small round seal with a word. */
	static void seal(GuiGraphicsExtractor g, Font font, Component word, float cx, float cy, float r, int color) {
		Brush.disc(g, cx, cy, r, Palette.argb(0xD8, color & 0xFFFFFF));
		Brush.ring(g, cx, cy, r - 1.2F, r - 0.6F, Palette.argb(0xA0, 0xFFF4D6));
		float scale = Brush.fitScale(font, word, r * 1.7F, 0.45F, 0.3F);
		Brush.centered(g, font, word, cx, cy - 4.0F * scale, 0xFFFFF4D6, scale, false);
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

	/** The list of legacy rows (Pelindung, Agung) written by module A. */
	ListTag legacy() {
		return f().getListOrEmpty("legacy");
	}
}
