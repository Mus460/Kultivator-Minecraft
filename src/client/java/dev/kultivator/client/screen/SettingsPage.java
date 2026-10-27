package dev.kultivator.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pengaturan (DESIGN 43.5 [8], 43.10): the five groups are five pages of the section (Tampilan & Efek, Kamera & Film,
 * Suara, HUD, Kontrol; {@link #daftar}). Tampilan & Efek keeps the effect quality talismans on the left; every group lays
 * its rows ({@link SettingsRows}) in one {@link DaftarGulir} column, or two from the SEDANG layout on (Kontrol: settings | keys).
 * Text is 1.0 (43.0.5). The light and motion warnings of Kamera Spektakuler and "Bingkai dampak: Penuh" are a medium
 * {@link SegelKonfirmasi} over the Kamera & Film page (43.10.1), whoever asks ({@link #peringatanGerak},
 * {@link #peringatanDampak}). Kontrol rebinds every Kultivator key in place (43.10.2): click a key row, press the new key
 * (Esc cancels, Backspace unbinds, a middle/side mouse button binds that button); right click restores the default; a key
 * another mapping also uses is red with that mapping's name.
 */
public final class SettingsPage extends Page {

	private static final String C = "kultivator.codex.settings.";
	private static final String M = "kultivator.gui43.m18.";
	/** The "?" topic per group: Tampilan, Film, Suara, HUD, Kontrol. */
	private static final String[] HELP = {"visuals", "momen", "suara", "hud", "tombol"};
	/** The page id of each group (index = {@code HubNav.GROUP_*}). */
	private static final String[] ID = {Urutan.ID_TAMPILAN, Urutan.ID_FILM, Urutan.ID_SUARA, Urutan.ID_HUD, Urutan.ID_KONTROL};
	private static final int[] URUTAN = {Urutan.TAMPILAN, Urutan.FILM, Urutan.SUARA, Urutan.HUD, Urutan.KONTROL};
	private static final int GAP = 10;
	private static final int ROW = 14;
	private static final int HEAD = 13;
	/** A warning nobody saw within this time is dropped (no camera shot or menu should pop it up much later). */
	private static final long TUNDA_MS = 10_000L;

	/** A warning waiting for its page: asked from a command, the camera cycle or another page. */
	private record Peringatan(int group, Component teks, Runnable ya, long sejak) {
	}

	/** One list entry: a heading or note line (no widget, no key), a control, or a key line. */
	private record Entry(SettingsRows.Item item, @Nullable FormattedCharSequence line, @Nullable Widget widget, @Nullable KeyMapping key) {
		boolean judul() {
			return widget == null && key == null;
		}
	}

	private static @Nullable Peringatan tunda;
	/** The {@link Tujuan} focus key naming a heading (its lang key) to scroll to. */
	public static final String FOKUS_BARIS = "baris";
	private @Nullable String fokusBaris;

	private final int group;
	private final SettingsRows rows;
	private final List<List<Entry>> columns = new ArrayList<>();
	private final List<int[]> boxes = new ArrayList<>();
	private final DaftarGulir[] lists = {new DaftarGulir(), new DaftarGulir()};
	private int leftW;
	private float descY;
	/** The key waiting for its new binding (43.10.2), or null. */
	private @Nullable KeyMapping capturing;

	/** Registers the five Pengaturan pages and the two warnings (once, after {@code CultivationScreen.daftarBawaan}). */
	public static void daftar() {
		for (int g = 0; g < ID.length; g++) {
			int group = g;
			HubPages.daftar(HubNav.PENGATURAN, ID[g], URUTAN[g], () -> new SettingsPage(group));
		}
		EpicSettings.motionWarning(SettingsPage::peringatanGerak);
		EpicSettings.impactWarning(SettingsPage::peringatanDampak);
	}

	/** The page id of Pengaturan group {@code group} ({@code HubNav.GROUP_*}). */
	public static String idGrup(int group) {
		return ID[SettingsRows.clamp(group)];
	}

	/** The motion warning of the first switch to Kamera Spektakuler: "Ya" remembers it and switches. */
	public static void peringatanGerak() {
		peringatan(SettingsRows.FILM, Component.translatable(M + "gerak"), EpicSettings::confirmSpektakuler);
	}

	/** The photosensitivity warning of the first switch to impact frames Penuh ({@code /dampak frames penuh} too). */
	public static void peringatanDampak() {
		peringatan(SettingsRows.FILM, Component.translatable(M + "dampak"), EpicSettings::confirmImpactFrames);
	}

	/** Opens the group's page (now, or once chat or another screen closes) and lays the warning over it. */
	private static void peringatan(int group, Component teks, Runnable ya) {
		tunda = new Peringatan(group, teks, ya, Util.getMillis());
		HubNav.antre(Tujuan.of(HubNav.PENGATURAN, ID[group]));
	}

	SettingsPage(int group) {
		super();
		this.group = SettingsRows.clamp(group);
		this.rows = new SettingsRows(this);
	}

	@Override
	public Component title() {
		return SettingsRows.groupName(group);
	}

	@Override
	public String helpTopic() {
		return HELP[group];
	}

	// ================================================================ life

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		super.onShow(fokus);
		tampilkanTunda();
	}

	/** {@code {"baris": headingLangKey}}: scrolls to that heading's rows on the next frame (Langit Sekte, the last block). */
	@Override
	public void fokus(CompoundTag fokus) {
		String key = fokus.getStringOr(FOKUS_BARIS, "");
		fokusBaris = key.isEmpty() ? null : key;
	}

	@Override
	public void tick() {
		tampilkanTunda();
	}

	@Override
	public void onHide() {
		capturing = null;
	}

	/** Lays a waiting warning of this group over the page (the Gulungan may already have been on this page). */
	private void tampilkanTunda() {
		Peringatan p = tunda;
		if (p == null) {
			return;
		}
		if (Util.getMillis() - p.sejak() > TUNDA_MS) {
			tunda = null;
		} else if (p.group() == group && w > 0) {
			tunda = null;
			konfirmasi(SegelKonfirmasi.Tingkat.SEDANG, p.teks(), p.ya());
		}
	}

	// ================================================================ layout

	@Override
	protected void build() {
		columns.clear();
		boxes.clear();
		capturing = null;
		List<SettingsRows.Item> items = rows.group(group);
		if (group == SettingsRows.TAMPILAN) {
			leftW = Math.max(90, Math.min(150, Math.round((w - GAP) * 0.38F)));
			buildQuality();
			column(strip(items), x + leftW + GAP, x + w);
			return;
		}
		leftW = 0;
		int split = -1;
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).type() == SettingsRows.Type.BREAK) {
				split = i;
				break;
			}
		}
		int colW = (w - GAP) / 2;
		// two columns only from SEDANG (43.3.2): at GUI scale 4 (box ~289 px) one readable column that scrolls
		boolean dua = tata() != Tata.SEMPIT;
		if (dua && split >= 0) {
			column(items.subList(0, split), x, x + colW);
			column(items.subList(split + 1, items.size()), x + colW + GAP, x + w);
			return;
		}
		List<SettingsRows.Item> all = strip(items);
		if (dua) {
			List<Entry> wide = entries(all, colW);
			int at = balance(wide);
			if (at > 0) {
				addColumn(wide.subList(0, at), x, x + colW);
				addColumn(wide.subList(at, wide.size()), x + colW + GAP, x + w);
				return;
			}
		}
		// one column, not stretched across a wide scroll
		column(all, x, x + (dua ? Math.min(w, Math.max(colW, 280)) : w));
	}

	/** Effect quality: four talismans, one above the other, then Otomatis (the left column of Tampilan & Efek). */
	private void buildQuality() {
		int th = Math.max(12, Math.min(19, (h - 20) / 5 - 4));
		int ty = y + 14;
		for (EffectQuality.Level level : EffectQuality.Level.values()) {
			TalismanButton talisman = new TalismanButton(x + 2, ty, leftW - 4, th, level::displayName);
			talisman.stampedWhen(() -> !EffectQuality.auto() && EffectQuality.chosenLevel() == level);
			talisman.onPress(() -> EffectQuality.setLevel(level));
			talisman.tooltip(() -> List.of(level.displayName(), Component.translatable("kultivator.screen.quality." + level.id() + ".tip")));
			add(talisman);
			ty += th + 4;
		}
		TalismanButton auto = new TalismanButton(x + 2, ty, leftW - 4, th, () -> Component.translatable(C + "auto"));
		auto.stampedWhen(EffectQuality::auto);
		auto.onPress(() -> EffectQuality.setAuto(true));
		auto.tooltip(() -> List.of(Component.translatable(C + "auto"), Component.translatable(C + "auto.tip"),
				Component.translatable(C + "auto.now", EffectQuality.level().displayName()).withStyle(ChatFormatting.GRAY)));
		add(auto);
		descY = ty + th + 6;
	}

	private void column(List<SettingsRows.Item> items, int x0, int x1) {
		addColumn(entries(items, x1 - x0 - 6), x0, x1);
	}

	private void addColumn(List<Entry> entries, int x0, int x1) {
		List<Entry> col = List.copyOf(entries);
		int c = columns.size();
		columns.add(col);
		boxes.add(new int[] {x0, x1});
		lists[c].judul(i -> i >= 0 && i < col.size() && col.get(i).judul(), HEAD);
	}

	/** The list entries of {@code items} for a column {@code width} wide (notes wrapped into lines). */
	private List<Entry> entries(List<SettingsRows.Item> items, int width) {
		List<Entry> out = new ArrayList<>();
		for (SettingsRows.Item item : items) {
			switch (item.type()) {
				case HEADING -> out.add(new Entry(item, null, null, null));
				case NOTE -> {
					for (FormattedCharSequence line : font.split(item.label().get(), Math.max(20, width - 8))) {
						out.add(new Entry(item, line, null, null));
					}
				}
				case COIN -> out.add(new Entry(item, null, control(new JadeCoin(0, 0, 10, ROW - 2, item.label(), item.on()), item), null));
				case CYCLE, BUTTON -> out.add(new Entry(item, null, control(new TalismanButton(0, 0, 16, ROW - 2, item.label()), item), null));
				case LINE -> out.add(new Entry(item, null, null, rows.keyOf(item)));
				case BREAK -> {
				}
			}
		}
		return out;
	}

	private static Widget control(Widget widget, SettingsRows.Item item) {
		widget.enabledWhen(item.enabled());
		// the only rows that can be disabled are the server-side ones (Keys::connected)
		widget.alasanWhen(() -> Component.translatable("kultivator.gui43.p8.luring"));
		if (item.action() != null) {
			widget.onPress(item.action());
		}
		widget.tooltip(item.tooltip());
		return widget;
	}

	/** The items without column breaks. */
	private static List<SettingsRows.Item> strip(List<SettingsRows.Item> items) {
		List<SettingsRows.Item> out = new ArrayList<>(items.size());
		for (SettingsRows.Item item : items) {
			if (item.type() != SettingsRows.Type.BREAK) {
				out.add(item);
			}
		}
		return out;
	}

	/** Where to cut a list taller than the box into two columns (at a heading, most even); -1 = it fits in one. */
	private int balance(List<Entry> entries) {
		int total = 0;
		for (Entry e : entries) {
			total += e.judul() ? HEAD : ROW;
		}
		if (total <= h) {
			return -1;
		}
		int best = -1;
		int bestTall = Integer.MAX_VALUE;
		int above = 0;
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			if (i > 0 && e.item().type() == SettingsRows.Type.HEADING && e.line() == null) {
				int tall = Math.max(above, total - above);
				if (tall < bestTall) {
					bestTall = tall;
					best = i;
				}
			}
			above += e.judul() ? HEAD : ROW;
		}
		return best;
	}

	// ================================================================ drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (group != SettingsRows.TAMPILAN) {
			return;
		}
		heading(g, Component.translatable("kultivator.screen.settings.quality"), x + 2.0F, y, leftW - 4.0F, 1.0F);
		if (y + h - descY > 9.0F) {
			EffectQuality.Level level = EffectQuality.level();
			Component tip = EffectQuality.auto() ? Component.translatable(C + "auto.tip")
					: Component.translatable("kultivator.screen.quality." + level.id() + ".tip");
			g.enableScissor(x, Math.round(descY), x + leftW, y + h);
			Brush.wrap(g, font, tip, x + 4.0F, descY, leftW - 8.0F, Palette.INK_SOFT, 1.0F, false);
			g.disableScissor();
		}
	}

	@Override
	protected void lukis(Lukis l) {
		for (int c = 0; c < columns.size(); c++) {
			List<Entry> col = columns.get(c);
			int[] box = boxes.get(c);
			String kunci = "kol" + c;
			int cc = c;
			lists[c].gambar(l, kunci, box[0], y, box[1], y + h, col.size(), ROW,
					(lk, i, x0, y0, x1, y1, sorot, pilih) -> baris(lk, kunci, i, col.get(i), x0, y0, x1, y1, sorot, pilih),
					() -> pakai(cc), null);
		}
		if (fokusBaris != null) {
			gulirKe(Component.translatable(fokusBaris).getString());
			fokusBaris = null;
		}
	}

	/** Selects the first row under the heading reading {@code judul} so its list scrolls there (after a frame laid it out). */
	private void gulirKe(String judul) {
		for (int c = 0; c < columns.size(); c++) {
			List<Entry> col = columns.get(c);
			for (int i = 0; i < col.size(); i++) {
				SettingsRows.Item item = col.get(i).item();
				if (item.type() == SettingsRows.Type.HEADING && item.label().get().getString().equals(judul)) {
					lists[c].setPilih(Math.min(i + 1, col.size() - 1));
					return;
				}
			}
		}
	}

	private void baris(Lukis l, String kunci, int i, Entry e, int x0, int y0, int x1, int y1, boolean sorot, boolean pilih) {
		Widget widget = e.widget();
		if (widget != null) {
			if (pilih && l.difokus(kunci)) {
				l.kartu(x0, y0, x1, y1, 0.0F, true);
			}
			widget.x = x0 + 2;
			widget.y = y0 + 1;
			widget.w = Math.max(16, x1 - x0 - 4);
			widget.h = Math.max(12, y1 - y0 - 1);
			l.widget(kunci + "w" + i, widget);
		} else if (e.key() != null) {
			keyRow(l, kunci + "#k" + i, e.key(), i, x0, y0, x1, y1, sorot, pilih);
		} else if (e.line() != null) {
			Brush.text(l.g, l.font, e.line(), x0 + 4.0F, y0 + 2.0F, e.item().color().getAsInt(), 1.0F, false);
		} else {
			l.kepala(e.item().label().get(), x0 + 1, y0 + 1, x1 - 2);
		}
	}

	/** One key line: the key's name on the left, its binding on the right (red with the other mapping on a clash). */
	private void keyRow(Lukis l, String kunci, KeyMapping key, int i, int x0, int y0, int x1, int y1, boolean sorot, boolean pilih) {
		boolean menunggu = key == capturing;
		Lukis.Zona zona = l.zona(kunci, x0, y0, x1, y1, () -> tangkap(key), true, null, false);
		if (zona != null) {
			zona.kanan = () -> bawaan(key);
		}
		if (sorot || pilih || menunggu) {
			l.kartu(x0, y0, x1, y1, sorot ? 1.0F : 0.0F, pilih || menunggu);
		} else if (i % 2 == 0) {
			l.g.fill(x0, y0, x1, y1, Palette.argb(0x14, Palette.INK));
		}
		float ty = y0 + (y1 - y0 - 8) / 2.0F;
		Component value = menunggu ? Component.translatable(M + "key.tekan") : SettingsRows.keyValue(key);
		int color = menunggu ? Palette.mix(Palette.GOLD_DARK, Palette.SEAL, l.denyut()) : SettingsRows.keyColor(key);
		float room = (x1 - x0) * 0.5F;
		float valueW = Math.min(room, font.width(value));
		l.teks(value, x1 - 3.0F - valueW, ty, room, color);
		l.teks(Component.translatable(key.getName()), x0 + 3.0F, ty, x1 - x0 - valueW - 10.0F, Palette.INK);
		if (l.di(x0, y0, x1, y1)) {
			l.tip(SettingsRows.keyTooltip(key));
		}
	}

	// ================================================================ input

	/** Enter on a list (or a second click on its selected row): runs the selected row. */
	private void pakai(int c) {
		List<Entry> col = columns.get(c);
		int i = lists[c].pilihan();
		if (i < 0 || i >= col.size()) {
			return;
		}
		Entry e = col.get(i);
		if (e.key() != null) {
			tangkap(e.key());
		} else if (e.widget() != null && e.item().action() != null) {
			if (e.item().enabled().getAsBoolean()) {
				UiSound.press();
				e.item().action().run();
			} else {
				UiSound.denied();
				pesan(Component.translatable("kultivator.gui43.p8.luring"), Palette.DANGER);
			}
		}
	}

	/** Starts (or, on the waiting row, cancels) the in-place rebind of {@code key}. */
	private void tangkap(KeyMapping key) {
		if (capturing == key) {
			capturing = null;
			UiSound.close();
			return;
		}
		capturing = key;
		UiSound.ask();
		pesan(Component.translatable(M + "key.tangkap", Component.translatable(key.getName())), Palette.GOLD_DARK);
	}

	/** Right click on a key row: back to its default. */
	private void bawaan(KeyMapping key) {
		capturing = null;
		ikat(key, key.getDefaultKey());
	}

	/** Binds {@code key} to {@code input}, applies and saves it (as vanilla's key binds screen), then reports any clash. */
	private void ikat(KeyMapping key, InputConstants.Key input) {
		key.setKey(input);
		KeyMapping.resetMapping();
		minecraft.options.save();
		UiSound.press();
		Component name = Component.translatable(key.getName());
		List<KeyMapping> others = SettingsRows.clashes(key);
		if (key.isUnbound()) {
			pesan(Component.translatable(M + "key.dilepas", name), Palette.INK_SOFT);
		} else if (!others.isEmpty()) {
			pesan(Component.translatable(M + "key.bentrok_pesan", name, key.getTranslatedKeyMessage(),
					Component.translatable(others.get(0).getName())), Palette.DANGER);
		} else {
			pesan(Component.translatable(M + "key.diikat", name, key.getTranslatedKeyMessage()), Palette.JADE_DARK);
		}
		// the clash note and the quick-actions note depend on the bindings
		layout(x, y, w, h);
	}

	@Override
	public boolean mauTombol() {
		return capturing != null || super.mauTombol();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		KeyMapping key = capturing;
		if (key == null) {
			return super.keyPressed(event);
		}
		capturing = null;
		if (event.isEscape()) {
			UiSound.close();
		} else if (event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE) {
			ikat(key, InputConstants.UNKNOWN);
		} else {
			ikat(key, InputConstants.getKey(event));
		}
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		KeyMapping key = capturing;
		if (key == null || modal()) {
			return super.mouseClicked(event, doubleClick);
		}
		capturing = null;
		int button = event.button();
		if (button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT) {
			UiSound.close();
		} else {
			ikat(key, InputConstants.Type.MOUSE.getOrCreate(button));
		}
		return true;
	}

	@Override
	public boolean mundur() {
		if (capturing != null) {
			capturing = null;
			UiSound.close();
			return true;
		}
		return false;
	}
}
