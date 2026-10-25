package dev.kultivator.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Fokus;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.CultivationData;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The one page base of the Gulungan Utama (DESIGN 43.6, frozen in 43S-A step 1). Every menu of the mod is a page shown in
 * the content box of {@link CultivationScreen}: the hub pages of before, the old tab bases ({@code HallPage},
 * {@code DuniaPage}, {@code CodexPage}, {@code DiscipleTab}, {@code KotaPage}) and the sheet base {@code LayarTempatDasar}
 * all move onto this class. A page is laid out into the box ({@link #build()} with {@link #x}, {@link #y}, {@link #w},
 * {@link #h}), owns persistent {@link Widget}s ({@link #add}) and/or draws immediately, and receives every mouse and key
 * event of the box with the 26.3 {@code Screen} signatures so a former screen moves over with small edits.
 * <p>
 * Life: the host builds a page lazily the first time it is shown; {@link #onShow} runs each time it comes on screen (ask
 * for data there, never while hidden: 43.0.12), {@link #onHide} when it leaves or the Gulungan closes. Data snapshots of
 * the player call {@link #onData()}. Keys: a page with a focused text field ({@link #mauTombol()}) gets every key first;
 * otherwise the host takes its own keys (43.4.2) and passes the rest here. Tab focus over the page's widgets is built in
 * ({@link #fokusTab}); Enter/Space runs the focused widget.
 */
public abstract class Page {

	/** Responsive layout from the content width (38.U.2): below 320 one column, below 480 list + detail, else three columns. */
	public enum Tata { SEMPIT, SEDANG, LEBAR }

	/** One sub of the page's sub bar: id (stable, used by {@link Tujuan#sub}), name, lock reason (null = open). */
	public record Sub(String id, Component nama, @Nullable Component kunci) {
		public Sub(String id, Component nama) {
			this(id, nama, null);
		}
	}

	/**
	 * The "Sumber" chip (43.2.1): the same page seen from two roles (Aulaku / Perguruanku). The host shows it at the right of
	 * the sub bar only when the page returns one.
	 */
	public record Sumber(List<Component> pilihan, IntSupplier aktif, IntConsumer pilih) {
	}

	/** The host; set by the legacy constructor or when the host mounts the page. */
	protected @Nullable CultivationScreen screen;
	protected final Minecraft minecraft;
	protected final Font font;
	/** The content box. */
	protected int x;
	protected int y;
	protected int w;
	protected int h;
	protected final List<Widget> widgets = new ArrayList<>();
	/**
	 * The page's focus ring and input engine (S-B {@code Fokus}): Tab walks the widgets and the immediate zones drawn in
	 * {@link #lukis}, Enter/Space run the focused one, the page's {@code SegelKonfirmasi} lives here (43.4.1 step 1).
	 */
	protected final Fokus fokusRing = new Fokus(alasan -> pesan(alasan, Palette.DANGER));

	/** A page built by a {@code HubPages} supplier (the host mounts it). */
	protected Page() {
		this.minecraft = Minecraft.getInstance();
		this.font = minecraft.font;
	}

	/** The legacy constructor of the hub pages in this package. */
	protected Page(CultivationScreen screen) {
		this();
		this.screen = screen;
	}

	/** Called by the host before the first layout. */
	public final void pasang(CultivationScreen host) {
		this.screen = host;
	}

	// ================================================================== identity

	/** Full name: header title and coin tooltip. */
	public abstract Component title();

	/** Name on the page coin; defaults to {@link #title()}. */
	public Component shortName() {
		return title();
	}

	/** {@code Glyphs.*} of the page (coin in narrow layout, Di Sini tablet); -1 = the section's glyph. */
	public int glyph() {
		return -1;
	}

	/** The Ensiklopedia topic of the "?" seal ({@code kultivator.screen.help.<topic>} and its {@code .title}). */
	public String helpTopic() {
		return "jalan";
	}

	/** The sub bar (empty = none). The default turns the legacy {@link #subNames()} into subs with ids "0", "1", ... */
	public List<Sub> subs() {
		List<Component> names = subNames();
		if (names == null || names.isEmpty()) {
			return List.of();
		}
		List<Sub> subs = new ArrayList<>(names.size());
		for (int i = 0; i < names.size(); i++) {
			subs.add(new Sub(String.valueOf(i), names.get(i)));
		}
		return subs;
	}

	/** Shows sub {@code id} of {@link #subs()} (the host passes a valid id). The default parses legacy index ids. */
	public void showSub(String id) {
		try {
			showSub(Integer.parseInt(id));
		} catch (NumberFormatException ignored) {
			// Not a legacy index: a page with named subs overrides this method.
		}
	}

	/** Legacy: sub names by index (Pengaturan groups). New pages override {@link #subs()} instead. */
	public List<Component> subNames() {
		return List.of();
	}

	/** Legacy: shows sub {@code index} of {@link #subNames()}. */
	public void showSub(int index) {
	}

	/** True for pages on the wide paper (43.3.2: Kodeks, Cincin Segel, Peta, Buku Harian, Templat, wide Di Sini). */
	public boolean lebar() {
		return false;
	}

	/** Lock reason (coin drawn dim, page not enterable), or null when open. */
	public @Nullable Component kunci() {
		return null;
	}

	/** False hides the page's coin (a sub/page that does not apply is hidden, not locked). */
	public boolean tampil() {
		return true;
	}

	/** The Sumber chip, or null. */
	public @Nullable Sumber sumber() {
		return null;
	}

	/** Money shown in the header ({@code LabelHarga}) when {@code >= 0}. */
	public long uang() {
		return -1L;
	}

	/** True while the page shows a detail/child view; the header then draws the back control (= {@link #mundur()}). */
	public boolean rinci() {
		return false;
	}

	/** The panel colours of the page ({@code Palette.Gaya}; a sheet page returns its sheet's style). */
	public Palette.Gaya gaya() {
		return Palette.Gaya.KERTAS;
	}

	/**
	 * K3 (43.17): number key {@code n} (1-9) while the page is under the Di Sini tablet. True when the page drew a numbered
	 * choice {@code n} this frame and ran it (dialog, choice list); false lets the host switch section.
	 */
	public boolean angka(int n) {
		return fokusRing.angka(n);
	}

	// ================================================================== life

	/** Lays the page out into the box and rebuilds its widgets (host only). */
	public final void layout(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		widgets.clear();
		build();
		onData();
	}

	/** Creates the widgets for the current box and {@link #tata()}. */
	protected abstract void build();

	/** The page came on screen; {@code fokus} is the {@link Tujuan} focus tag or null. Ask for data here. */
	public void onShow(@Nullable CompoundTag fokus) {
		if (fokus != null) {
			fokus(fokus);
		}
	}

	/** The page left the screen or the Gulungan closed (send {@code OP_TUTUP}/close ops here). */
	public void onHide() {
	}

	/** Every client tick while shown. */
	public void tick() {
	}

	/** A new snapshot of the player's cultivation arrived. */
	public void onData() {
	}

	/** True when the widgets depend on data that changed so much that {@link #build()} must run again. */
	public boolean needsRebuild() {
		return false;
	}

	/** Applies a focus tag ({@code {"spec": id}}, {@code {"murid": id}} ...). */
	public void fokus(CompoundTag fokus) {
	}

	/** The layout class of the current box width. */
	protected final Tata tata() {
		return w < 320 ? Tata.SEMPIT : w < 480 ? Tata.SEDANG : Tata.LEBAR;
	}

	/**
	 * Draws the page (host only): one {@link Fokus} frame with the pictures behind, the immediate zones ({@link #lukis}),
	 * the widgets (each in the Tab ring), the pictures above, then the confirm seal and the tooltip queue.
	 */
	public final void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		Lukis l = fokusRing.mulai(g, font, mouseX, mouseY, 1.0F, gaya(), x, y, x + w, y + h);
		drawBehind(g, mouseX, mouseY, seconds);
		lukis(l);
		for (int i = 0; i < widgets.size(); i++) {
			Widget widget = widgets.get(i);
			if (visible(widget)) {
				l.widget("w" + i, widget);
			}
		}
		drawAbove(g, mouseX, mouseY, seconds);
		fokusRing.selesai(l, mouseX, mouseY);
	}

	/** Immediate-mode controls ({@code Lukis.zona}, kit painters); drawn after {@link #drawBehind}, before the widgets. */
	protected void lukis(Lukis l) {
	}

	/** Pictures under the widgets. */
	protected abstract void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds);

	/** Pictures over the widgets. */
	protected void drawAbove(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	/** Whether a widget is drawn and clickable right now (scrolled-out cards are not). */
	protected boolean visible(Widget widget) {
		return true;
	}

	// ================================================================== input

	/** Every click inside the box (left, right, middle). The default goes to {@link #fokusRing} (widgets and zones). */
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (fokusRing.klik(event.x(), event.y(), event.button())) {
			return true;
		}
		return event.button() == InputConstants.MOUSE_BUTTON_LEFT && clickedElsewhere(event.x(), event.y());
	}

	/** A left click that hit none of the widgets or zones. */
	protected boolean clickedElsewhere(double mouseX, double mouseY) {
		return false;
	}

	public boolean mouseReleased(MouseButtonEvent event) {
		return fokusRing.lepas(event.x(), event.y());
	}

	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return fokusRing.seret(event.x(), event.y(), dx);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return fokusRing.gulir(mouseX, mouseY, scrollY);
	}

	/** Keys the host did not take (43.4.1), and every key while {@link #mauTombol()}. Default: {@link #fokusRing}. */
	public boolean keyPressed(KeyEvent event) {
		return fokusRing.tombol(event);
	}

	public boolean keyReleased(KeyEvent event) {
		return false;
	}

	public boolean charTyped(CharacterEvent event) {
		return fokusRing.ketik(event);
	}

	/** True while a text field writes or the confirm is open: the page then gets keys before the host (only V/info excepted). */
	public boolean mauTombol() {
		return fokusRing.mauTombol();
	}

	/** True while the page's confirm seal is open: it then takes every input, the host's keys included (43.4.1 step 1). */
	public boolean modal() {
		return fokusRing.segel().terbuka();
	}

	/** Esc after the page's own keys: true when the page closed its detail/child view; false hands Esc to the host. */
	public boolean mundur() {
		return false;
	}

	/** Tab / Shift+Tab: moves the focus ring (widgets and focusable zones in drawing order). */
	public boolean fokusTab(boolean back) {
		fokusRing.geser(back ? -1 : 1);
		return true;
	}

	// ================================================================== services

	protected <T extends Widget> T add(T widget) {
		widgets.add(widget);
		return widget;
	}

	/** Where widget tooltips may show (the whole box unless part of it is clipped). */
	protected boolean hoverable(double mouseX, double mouseY) {
		return inside(mouseX, mouseY);
	}

	protected boolean inside(double mouseX, double mouseY) {
		return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
	}

	/** Shows {@code lines} as this frame's tooltip ({@code SlipTooltip}, drawn last by the host). */
	protected final void tooltip(List<Component> lines) {
		if (screen != null) {
			screen.showTooltip(lines);
		}
	}

	/** The footer message (6 s), {@code warna} an RGB colour ({@code Palette.*}). */
	protected final void pesan(Component text, int warna) {
		if (screen != null) {
			screen.pesan(text, warna);
		}
	}

	/** Asks for confirmation over the page (43.7 {@code SegelKonfirmasi}: ringan klik, sedang tahan 1.5 s, berat centang + tahan). */
	protected final void konfirmasi(SegelKonfirmasi.Tingkat tingkat, Component teks, Runnable ya) {
		fokusRing.konfirmasi(tingkat, teks, ya);
	}

	/** Moves the Gulungan to {@code tujuan} (another page, sub or section). */
	protected final void tuju(Tujuan tujuan) {
		HubNav.buka(tujuan);
	}

	protected static CultivationData data() {
		return ClientCultivation.data();
	}

	protected static void send(int action) {
		Keys.send(Payloads.Action.of(action));
	}

	protected static void send(int action, int arg, String text) {
		Keys.send(new Payloads.Action(action, arg, text));
	}

	/** A heading drawn in seal-red with a little stamp before it and a gold rule under it (text 1.0 or more, 43.0.5). */
	protected void heading(GuiGraphicsExtractor g, Component text, float hx, float hy, float width, float scale) {
		scale = Math.max(1.0F, scale);
		Brush.rect(g, hx, hy + 1.0F, hx + 4.0F, hy + 5.0F, Palette.SEAL);
		Brush.fit(g, font, Brush.bold(text), hx + 6.0F, hy, width - 6.0F,
				Palette.SEAL_DARK, scale, 1.0F, false);
		Brush.rect(g, hx, hy + 9.0F * scale + 1.0F, hx + width, hy + 9.0F * scale + 1.6F,
				Palette.argb(0xB0, Palette.GOLD));
	}
}
