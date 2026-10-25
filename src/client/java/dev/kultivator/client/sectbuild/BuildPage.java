package dev.kultivator.client.sectbuild;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.hall.HallClient;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihOrang;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.api.ComponentView;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.api.SiteView;
import dev.kultivator.sect.build.blueprint.Glaze;
import dev.kultivator.sect.build.flow.Deposits;
import dev.kultivator.sect.build.flow.Fmt;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sekte › Bangun of the Gulungan Utama (DESIGN 21.21, 43.5, 43.12.2), drawn from the {@code build} part of
 * {@code HallInfo}. On the left the site diagram (done parts in full ink, the open one with a pulsing gold edge, queued ones
 * faint, blocked ones with a red seal, future tiers faded; hovering names a part with its tier and bill); on the right the
 * chosen sub (the old seal column):
 * <ul>
 * <li><b>Ikhtisar</b>: "Tingkat 4/9 · Tembok dan Air", the nine terraces of the tier ladder, the open building, the rate,
 * the status, what the next building still needs, <i>Jeda</i> and the deposit talismans (before a template: the founding
 * text, "Pilih Templat" and "Bangun sendiri");</li>
 * <li><b>Bahan</b>: bamboo bars per category for this tier and the next, exact items, the bag's accepted items (click: a
 * stack, shift-click: all of that item) and "Ambil dari Lumbung";</li>
 * <li><b>Misi</b>: tier missions, daily offers and the gathering parties under stacked headings; the party is picked in
 * the kit's {@link PemilihOrang} pop-up;</li>
 * <li><b>Pekerja</b>: up to four builders, ticked in a {@link PemilihOrang}; <b>Galian</b>: the surplus, the Peti Galian
 * and its coins;</li>
 * <li><b>Templat</b>: before a template the catalog ({@link TemplateScreen}, wide paper); after it upgrade, repair,
 * deconstruct, abandon, change, the obstacles, the queue and the history, with "Naik Templat" opening the catalog as the
 * detail view; <b>Penyumbang</b>: donors (LAN only).</li>
 * </ul>
 * A sect mate sees it read-only and may deposit. Everything is drawn immediately (zones in the Tab ring); text is 1.0
 * (43.0.5); dangerous acts ask the kit's {@link SegelKonfirmasi} (43.7) instead of the old second click.
 */
public class BuildPage extends Page {

	static final String[] SUBS = {"ikhtisar", "bahan", "misi", "pekerja", "galian", "templat", "penyumbang"};
	private static final int LINE = 10;
	private static final String K = "kultivator.sectbuild.ui.";
	private static final String M = "kultivator.gui43.m07.";
	/** Sect rank names of module A (Tercatat ... Tetua Agung). */
	private static final String[] RANK_IDS = {"tercatat", "luar", "dalam", "inti", "tetua", "tetua_agung"};

	private static String sub = SUBS[0];
	/** The catalog is open as the detail view of Templat ("Naik Templat"). */
	private static boolean katalog;
	private static int gatherCat;
	private static boolean gatherLong;
	private static final Map<String, Float> SCROLL = new HashMap<>();

	private final TemplateScreen catalog = new TemplateScreen(this);
	private final PemilihOrang pekerja = new PemilihOrang(4).onUbah(this::pekerjaBerubah);
	private final PemilihOrang regu = new PemilihOrang(3);
	private final List<String> builderSouls = new ArrayList<>();
	private @Nullable CompoundTag partsFor;
	private List<SiteDiagram.Part> parts = List.of();
	private SiteDiagram.Bounds bounds = SiteDiagram.Bounds.of(1);
	private int panelX;
	private int panelW;
	// the scroll of this frame
	private String scrollKey = "";
	private int scrollTop;
	private int scrollBottom;
	private boolean catalogShown;

	public BuildPage() {
		regu.kolom(List.of(Component.translatable(M + "kolom.pangkat"), Component.translatable(M + "kolom.kekuatan")));
		pekerja.kolom(List.of(Component.translatable(M + "kolom.keadaan")));
	}

	// ================================================================== identity

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.sekte.bangun");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine6b.page.bangun.short");
	}

	@Override
	public int glyph() {
		return Glyphs.BUILD;
	}

	@Override
	public String helpTopic() {
		return "bangun";
	}

	/** Hidden for players without a hall (43.5 "*"); shown while the details are still unknown so they can be asked for. */
	@Override
	public boolean tampil() {
		return HallClient.version() == 0L || HallClient.info().getBooleanOr("has", false);
	}

	@Override
	public List<Sub> subs() {
		List<Sub> subs = new ArrayList<>();
		for (String id : SUBS) {
			if (shows(id)) {
				subs.add(new Sub(id, Component.translatable("kultivator.gui43.sub.bangun." + id)));
			}
		}
		return subs;
	}

	/** Whether sub {@code id} applies now (without a template only Ikhtisar and Templat; Penyumbang only on LAN). */
	private boolean shows(String id) {
		if (template() <= 0) {
			return "ikhtisar".equals(id) || "templat".equals(id);
		}
		return !"penyumbang".equals(id) || lan();
	}

	@Override
	public void showSub(String id) {
		if (!id.equals(sub)) {
			katalog = false;
		}
		sub = id;
		if ("templat".equals(id) && template() <= 0) {
			catalog.opened();
		}
	}

	private String shownSub() {
		return shows(sub) ? sub : SUBS[0];
	}

	@Override
	public boolean lebar() {
		return "templat".equals(shownSub()) && (template() <= 0 || katalog);
	}

	@Override
	public boolean rinci() {
		return katalog && template() > 0 && "templat".equals(shownSub());
	}

	@Override
	public boolean mundur() {
		if (regu.tutup()) {
			return true;
		}
		if (rinci()) {
			katalog = false;
			return true;
		}
		return false;
	}

	@Override
	public void fokus(CompoundTag fokus) {
		if (fokus.contains("templat")) {
			catalog.select(fokus.getIntOr("templat", 0));
			if (template() > 0) {
				katalog = true;
			}
			catalog.opened();
		}
	}

	// ================================================================== life

	@Override
	protected void build() {
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		// HallClient asks again every 4 s while the Sekte section shows (43.0.12).
		HallClient.minta();
		if ("templat".equals(shownSub()) && (template() <= 0 || katalog)) {
			catalog.opened();
		}
		super.onShow(fokus);
	}

	/** Fresh details now (after an action; the server answers in order, so they already show its effect). */
	private void refresh() {
		HallClient.send(Halls.OP_REFRESH, "");
	}

	// ================================================================== data

	private static CompoundTag info() {
		return HallClient.info();
	}

	private static CompoundTag b() {
		return info().getCompoundOrEmpty("build");
	}

	int template() {
		return b().getIntOr("t", 0);
	}

	private int tier() {
		return b().getIntOr("tier", 0);
	}

	private CompoundTag site() {
		return b().getCompoundOrEmpty("site");
	}

	boolean readonly() {
		return b().getBooleanOr("readonly", false);
	}

	private Bill bill(String key) {
		return Bill.readFrom(b().getCompoundOrEmpty(key));
	}

	private boolean lan() {
		return b().getBooleanOr("lan", false);
	}

	private CompoundTag flags() {
		return b().getCompoundOrEmpty("flags");
	}

	/** Sends a construction action and asks for fresh details right after (the server answers in order). */
	private void act(String text) {
		BuildClient.send(0, text);
		refresh();
	}

	private @Nullable Component roWhy() {
		return readonly() ? Component.translatable(M + "baca_saja") : null;
	}

	/** Re-reads the diagram parts when new details arrived. */
	private void refreshParts() {
		CompoundTag current = info();
		if (current == partsFor) {
			return;
		}
		partsFor = current;
		CompoundTag site = site();
		int[] stele = site.getIntArray("stele").orElse(null);
		int[] fp = site.getIntArray("fp").orElse(null);
		int rot = site.getIntOr("rot", 0);
		if (template() > 0 && stele != null && stele.length == 3 && fp != null && fp.length == 6 && !b().getListOrEmpty("comps").isEmpty()) {
			parts = SiteDiagram.fromComponents(b().getListOrEmpty("comps"), stele, rot);
			bounds = SiteDiagram.bounds(fp, stele, rot);
		} else {
			// rank(): sky templates are numbered 11-19, the sketches 1-9.
			int t = Math.max(1, SectTemplates.rank(template()));
			parts = SiteDiagram.sketch(t);
			bounds = SiteDiagram.Bounds.of(t);
		}
	}

	// ================================================================== services for the catalog

	/** Button height of the current layout (43.3.2: 12 narrow, else 14). */
	int buttonH() {
		return tata() == Tata.SEMPIT ? 12 : 14;
	}

	/** Asks for confirmation over the page. */
	void minta(SegelKonfirmasi.Tingkat tingkat, Component teks, Runnable ya) {
		konfirmasi(tingkat, teks, ya);
	}

	/** The footprint was confirmed: back to Ikhtisar, where the first building starts. */
	void afterConfirm() {
		katalog = false;
		refresh();
		tuju(Tujuan.of(HubNav.SEKTE, Urutan.ID_BANGUN).sub("ikhtisar"));
	}

	/** A scroll bead on the right edge of a list. */
	void bead(Lukis l, int x1, int y0, int y1, float offset, float max) {
		if (max <= 0.0F) {
			return;
		}
		float bead = y0 + 3.0F + (y1 - y0 - 6.0F) * (offset / max);
		l.g.fill(x1 - 2, y0, x1 - 1, y1, Palette.argb(0x80, Palette.CORD));
		Brush.disc(l.g, x1 - 1.5F, bead, 2.0F, Palette.JADE);
	}

	/** Per-key state of an immediate jade coin. */
	private static final class Koin {
		Component label = Component.empty();
		boolean on;
		boolean aktif = true;
		@Nullable Component alasan;
		@Nullable Runnable aksi;
		final JadeCoin widget;

		Koin() {
			widget = new JadeCoin(0, 0, TalismanButton.MIN_W, TalismanButton.MIN_H, () -> label, () -> on);
			widget.enabledWhen(() -> aktif);
			widget.alasanWhen(() -> alasan);
			widget.onPress(() -> {
				if (aksi != null) {
					aksi.run();
				}
			});
		}
	}

	/** A jade coin of the kit (on/off) drawn immediately and kept in the Tab ring; {@code alasan} shows while disabled. */
	static void koin(Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean on, boolean aktif, @Nullable Component alasan,
			Runnable aksi, @Nullable List<Component> tip) {
		Koin koin = l.fokusRing().simpan("koin:" + kunci, Koin::new);
		koin.label = label;
		koin.on = on;
		koin.aktif = aktif;
		koin.alasan = aktif ? null : alasan;
		koin.aksi = aksi;
		koin.widget.x = x;
		koin.widget.y = y;
		koin.widget.w = Math.max(TalismanButton.MIN_W, w);
		koin.widget.h = Math.max(TalismanButton.MIN_H, h);
		l.widget(kunci, koin.widget);
		if (tip != null && l.di(x, y, x + koin.widget.w, y + koin.widget.h)) {
			l.tip(tip);
		}
	}

	/** A talisman with a two-line tooltip (name, explanation). */
	private void tombol(Lukis l, String kunci, int bx, int by, int bw, int bh, String label, boolean aktif, @Nullable Component alasan, Runnable aksi,
			@Nullable String tipKey, boolean mendesak) {
		Component name = Component.translatable(label);
		boolean over = TalismanButton.gambar(l, kunci, bx, by, bw, bh, name, aktif, aktif ? null : alasan, aksi, mendesak && aktif, false);
		if (over && tipKey != null) {
			l.tip(List.of(name, Component.translatable(tipKey)));
		}
	}

	// ================================================================== scrolling

	private float scroll(String key) {
		return SCROLL.getOrDefault(key, 0.0F);
	}

	/** Starts the clipped, scrolling list {@code key} between {@code top} and {@code bottom}; returns the first line's y. */
	private float mulaiGulir(Lukis l, String key, int top, int bottom) {
		scrollKey = key;
		scrollTop = top;
		scrollBottom = Math.max(top + 10, bottom);
		l.klip(panelX, scrollTop, panelX + panelW, scrollBottom);
		return scrollTop - scroll(key);
	}

	/** Ends the list started by {@link #mulaiGulir}; {@code end} is the y below its last line. */
	private void akhirGulir(Lukis l, float end) {
		l.lepasKlip();
		float content = end + scroll(scrollKey) - scrollTop;
		float max = Math.max(0.0F, content - (scrollBottom - scrollTop));
		SCROLL.put(scrollKey, Math.max(0.0F, Math.min(max, scroll(scrollKey))));
		bead(l, panelX + panelW, scrollTop, scrollBottom, scroll(scrollKey), max);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (modal()) {
			return true;
		}
		if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
			return true;
		}
		if (catalogShown) {
			return catalog.scrolled(mouseX, mouseY, scrollY) || inside(mouseX, mouseY);
		}
		if (!scrollKey.isEmpty() && mouseX >= panelX && mouseX < panelX + panelW && mouseY >= scrollTop && mouseY < scrollBottom) {
			SCROLL.put(scrollKey, Math.max(0.0F, scroll(scrollKey) - (float) scrollY * 18.0F));
			return true;
		}
		return inside(mouseX, mouseY);
	}

	/** PgUp / PgDn / Home / End scroll the shown list (or the catalog's column) after the focus ring's own keys (43.0.7). */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		}
		if (mauTombol()) {
			return false;
		}
		if (catalogShown) {
			return catalog.tombol(event.key());
		}
		if (scrollKey.isEmpty()) {
			return false;
		}
		float page = Math.max(18.0F, scrollBottom - scrollTop - 18.0F);
		switch (event.key()) {
			case InputConstants.KEY_PAGEDOWN -> SCROLL.put(scrollKey, scroll(scrollKey) + page);
			case InputConstants.KEY_PAGEUP -> SCROLL.put(scrollKey, Math.max(0.0F, scroll(scrollKey) - page));
			case InputConstants.KEY_HOME -> SCROLL.put(scrollKey, 0.0F);
			case InputConstants.KEY_END -> SCROLL.put(scrollKey, Float.MAX_VALUE / 4.0F); // clamped by akhirGulir
			default -> {
				return false;
			}
		}
		return true;
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		scrollKey = "";
		catalogShown = false;
		if (HallClient.version() == 0L || !info().getBooleanOr("has", false)) {
			l.samar(Component.translatable(M + "memuat"), x + 4, y + 4, w - 8);
			return;
		}
		refreshParts();
		String shown = shownSub();
		if ("templat".equals(shown) && (template() <= 0 || katalog)) {
			catalogShown = true;
			catalog.draw(l, x, y, w, h);
			return;
		}
		boolean diagram = "ikhtisar".equals(shown) || tata() != Tata.SEMPIT;
		int size = diagram ? Math.max(64, Math.min(120, Math.min(h - 14, Math.round(w * 0.36F)))) : 0;
		panelX = diagram ? x + size + 6 : x;
		panelW = x + w - panelX;
		if (diagram) {
			drawDiagram(l, size);
		}
		CompoundTag build = b();
		int top = y;
		if (!build.getBooleanOr("on", true) || !build.getBooleanOr("engine", false)) {
			top += l.kepala(title(), panelX, top, panelX + panelW);
			top += Math.round(l.bungkus(Component.translatable(build.getBooleanOr("on", true) ? "kultivator.spine6b.unavailable" : "kultivator.sectbuild.disabled"),
					panelX, top, panelW, l.gaya.teksSamar, 40.0F)) + 4;
			if (template() > 0) {
				return;
			}
		}
		if (template() <= 0) {
			drawFounding(l, top);
		} else {
			switch (shown) {
				case "bahan" -> drawMaterials(l);
				case "misi" -> drawMissions(l);
				case "pekerja" -> drawBuilders(l);
				case "galian" -> drawSalvage(l);
				case "templat" -> drawTemplate(l);
				case "penyumbang" -> drawDonors(l);
				default -> drawOverview(l);
			}
		}
		if (regu.terbuka()) {
			Bahan cat = Bahan.values()[gatherCat];
			regu.gambarSembul(l, "bangun:regu", Component.translatable(K + "party_pick", cat.displayName()), candidates(cat));
		}
	}

	private void drawDiagram(Lukis l, int size) {
		int scrub = template() > 0 ? Math.min(9, tier() + 1) : BuildClient.scrubTier();
		int hover = SiteDiagram.draw(l.g, x, y + 1, size, parts, bounds, template() > 0 ? 9 : scrub, template() > 0 ? Math.min(9, tier() + 1) : 0,
				l.mx, l.my, true);
		Brush.frame(l.g, x - 1, y, x + size + 1, y + size + 2, Palette.argb(0xC0, Palette.GOLD));
		if (hover >= 0 && hover < parts.size()) {
			l.tip(partTip(hover));
		}
		if (size + 16 < h) {
			Component caption = template() > 0 ? SectTemplates.name(template()) : Component.translatable(K + "no_template_short");
			l.tengah(caption, x + size / 2.0F, y + size + 5.0F, size, Palette.SEAL_DARK);
		}
	}

	private List<Component> partTip(int index) {
		SiteDiagram.Part part = parts.get(index);
		List<Component> tip = new ArrayList<>();
		tip.add(part.name() != null ? part.name() : Component.translatable(K + "part." + part.shape()));
		tip.add(Component.translatable(K + "part_tier", part.tier(), SectTemplates.tierName(part.tier())));
		if (part.state() >= 0) {
			tip.add(Component.translatable(K + "state." + part.state(), part.progress()));
		}
		ListTag comps = b().getListOrEmpty("comps");
		if (index < comps.size() && comps.get(index) instanceof CompoundTag comp && comp.contains("bill")) {
			Bill bill = Bill.readFrom(comp.getCompoundOrEmpty("bill"));
			if (!bill.isEmpty()) {
				tip.add(Component.translatable(K + "part_bill", Fmt.joined(bill, 5)));
			}
		}
		return tip;
	}

	/** The top of {@code rows} rows of buttons at the bottom of the box. */
	private int rowsTop(int rows) {
		return y + h - rows * (buttonH() + 2) + 2;
	}

	// ---- no template yet

	private void drawFounding(Lukis l, int top) {
		int bh = buttonH();
		int rows = rowsTop(2);
		top += l.kepala(Component.translatable(K + "no_template"), panelX, top, panelX + panelW);
		float ty = mulaiGulir(l, "ikhtisar0", top, rows - 3);
		int tw = panelW - 5;
		ty += l.bungkus(Component.translatable(K + "founding_text"), panelX, ty, tw, Palette.INK) + 4;
		Bill ledger = bill("ledger");
		if (!ledger.isEmpty()) {
			ty += l.bungkus(Component.translatable(K + "ledger_now", Fmt.joined(ledger, 6)), panelX, ty, tw, Palette.INK_SOFT) + 2;
		}
		ty += l.bungkus(Component.translatable(K + "stele_hint"), panelX, ty, tw, l.gaya.teksSamar);
		akhirGulir(l, ty);
		CompoundTag build = b();
		boolean canChoose = !readonly() && build.getBooleanOr("engine", false) && build.getBooleanOr("on", true);
		Component why = readonly() ? roWhy() : Component.translatable(M + "mesin_mati");
		tombol(l, "bangun:pilih", panelX, rows, panelW, bh, K + "choose", canChoose, why,
				() -> tuju(Tujuan.of(HubNav.SEKTE, Urutan.ID_BANGUN).sub("templat")), null, true);
		boolean self = flags().getBooleanOr("self", false);
		koin(l, "bangun:sendiri", panelX, rows + bh + 2, panelW, bh, Component.translatable(K + "self_build"), self, !readonly(), roWhy(),
				() -> act("flag:self:" + (self ? "0" : "1")), List.of(Component.translatable(K + "self_build"), Component.translatable(K + "self_build_tip")));
	}

	// ---- Ikhtisar

	private void drawOverview(Lukis l) {
		int bh = buttonH();
		int rows = rowsTop(2);
		int tier = tier();
		int building = Math.min(9, tier + 1);
		Component title = tier >= 9 ? Component.translatable(K + "tier_complete", SectTemplates.TIERS, SectTemplates.TIERS)
				: Component.translatable(K + "tier_title", tier, SectTemplates.TIERS, SectTemplates.tierName(building));
		int top = y + l.kepala(title, panelX, y, panelX + panelW);
		float ty = mulaiGulir(l, "ikhtisar", top, rows - 3);
		int tw = panelW - 5;
		drawLadder(l, panelX + 2.0F, ty, tw - 4.0F, 32.0F, tier);
		ty += 36.0F;
		CompoundTag site = site();
		// The open building.
		String openId = b().getStringOr("open", "");
		CompoundTag open = null;
		for (Tag each : b().getListOrEmpty("comps")) {
			if (each instanceof CompoundTag comp && !openId.isEmpty() && comp.getStringOr("id", "").equals(openId)) {
				open = comp;
			}
		}
		if (open != null) {
			Component name = open.read("name", ComponentSerialization.CODEC).orElse(Component.literal(openId));
			int pg = open.getIntOr("pg", 0);
			bar(l, panelX, ty, tw, pg / 100.0F, Palette.GOLD, Component.translatable("kultivator.sectbuild.holo.open", name, pg));
			ty += 13.0F;
		}
		// Rate and status.
		double rate = site.getDoubleOr("rate", 0.0D);
		int active = b().getCompoundOrEmpty("builders").getIntOr("active", 0);
		boolean banked = site.getLongOr("bank", 0L) > 0L;
		l.teks(Component.translatable(banked ? K + "rate_bank" : K + "rate", String.format(Locale.ROOT, "%.0f", rate), active), panelX, ty, tw,
				Palette.INK_SOFT);
		ty += LINE;
		Component status = b().read("status_text", ComponentSerialization.CODEC).orElse(Component.empty());
		int code = site.getIntOr("status", 0);
		int color = code == SiteView.STATUS_RUNNING || code == SiteView.STATUS_COMPLETE ? Palette.JADE_DARK
				: code == SiteView.STATUS_WAITING_FUNDS ? Palette.GOLD_DARK : code == SiteView.STATUS_BLOCKED ? Palette.SEAL : Palette.INK_SOFT;
		ty += l.bungkus(status, panelX, ty, tw, color) + 2;
		// What the next building still needs.
		CompoundTag next = b().getCompoundOrEmpty("next");
		if (!next.isEmpty()) {
			Bill shortfall = Bill.readFrom(next.getCompoundOrEmpty("short"));
			Component name = next.read("name", ComponentSerialization.CODEC).orElse(Component.empty());
			if (shortfall.isEmpty()) {
				ty += l.bungkus(Component.translatable(K + "next_ready", name), panelX, ty, tw, Palette.JADE_DARK) + 1;
			} else {
				ty += l.bungkus(Component.translatable(K + "next_needs", name), panelX, ty, tw, Palette.SEAL_DARK) + 1;
				ty = shortfallLines(l, shortfall, ty, tw);
			}
		}
		akhirGulir(l, ty);
		boolean paused = site.getIntOr("state", 0) == SiteView.STATE_PAUSED;
		koin(l, "bangun:jeda", panelX, rows, panelW, bh, Component.translatable(K + "pause"), paused, !readonly(), roWhy(),
				() -> act("pause:" + (paused ? "0" : "1")), null);
		int half = (panelW - 3) / 2;
		int by = rows + bh + 2;
		tombol(l, "bangun:setor_perlu", panelX, by, half, bh, K + "deposit_needed", true, null, () -> act("deposit_needed"), K + "deposit_needed_tip",
				code == SiteView.STATUS_WAITING_FUNDS);
		tombol(l, "bangun:setor_semua", panelX + half + 3, by, panelW - half - 3, bh, K + "deposit_all", true, null, () -> act("deposit_all"),
				K + "deposit_all_tip", false);
	}

	/** Nine terraces climbing a mountain path; done in jade, the one being built glowing gold, later ones in faint ink. */
	private void drawLadder(Lukis l, float lx, float ly, float lw, float lh, int tier) {
		GuiGraphicsExtractor g = l.g;
		float step = lw / 9.0F;
		float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 260.0D);
		float base = ly + lh;
		float rise = lh - 12.0F;
		for (int i = 0; i < 18; i++) {
			float t = i / 18.0F;
			float mh = rise * (0.25F + 0.7F * t) * (0.9F + 0.1F * (float) Math.sin(i * 1.7F));
			Brush.rect(g, lx + lw * t, base - mh, lx + lw * (t + 1.0F / 18.0F), base, Palette.argb(0x28, Palette.MOUNTAIN));
		}
		for (int i = 0; i < 9; i++) {
			float tx0 = lx + i * step;
			float top = base - (i + 1) * (rise - 3.0F) / 9.0F;
			boolean done = i < tier;
			boolean current = i == tier;
			int color = done ? Palette.JADE : current ? Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse) : Palette.argb(0x70, Palette.INK_FAINT);
			Brush.rect(g, tx0 + 0.5F, top, tx0 + step - 0.5F, top + 2.2F, color);
			Brush.rect(g, tx0 + 0.5F, top + 2.2F, tx0 + step - 0.5F, base, Palette.argb(done ? 0x50 : 0x20, Palette.PAPER_DEEP));
			if (i > 0) {
				float prevTop = base - i * (rise - 3.0F) / 9.0F;
				Brush.line(g, tx0, prevTop + 1.0F, tx0 + 1.5F, top + 1.0F, 0.6F, Palette.argb(0x90, Palette.INK_SOFT));
			}
			Brush.centered(g, l.font, Component.literal(Integer.toString(i + 1)), tx0 + step / 2.0F, top - 10.0F,
					done ? Palette.JADE_DARK : current ? Palette.GOLD_DARK : Palette.INK_FAINT, 1.0F, false);
			if (l.di(Math.round(tx0), Math.round(top - 10.0F), Math.round(tx0 + step), Math.round(base))) {
				l.tip(List.of(Component.translatable(K + "part_tier", i + 1, SectTemplates.tierName(i + 1)),
						Component.translatable("kultivator.sectbuild.tier_feature." + (i + 1))));
			}
		}
		// A small hall at the summit.
		float sx = lx + lw - 3.0F;
		float sy = ly + 1.0F;
		Brush.rect(g, sx - 3.0F, sy, sx + 3.0F, sy + 1.2F, Palette.INK);
		Brush.rect(g, sx - 2.0F, sy + 1.2F, sx + 2.0F, sy + 3.0F, Palette.SEAL);
	}

	/** One bamboo bar (11 px) with its label in it at 1.0. */
	private void bar(Lukis l, float bx, float by, float bw, float fill, int color, Component label) {
		GuiGraphicsExtractor g = l.g;
		g.fill(Math.round(bx), Math.round(by), Math.round(bx + bw), Math.round(by + 11.0F), Palette.argb(0x60, Palette.BAMBOO_ALT));
		Brush.rect(g, bx, by, bx + bw * Math.max(0.0F, Math.min(1.0F, fill)), by + 11.0F, Palette.argb(0xC0, color));
		Brush.frame(g, Math.round(bx), Math.round(by), Math.round(bx + bw), Math.round(by + 11.0F), Palette.argb(0xA0, Palette.BAMBOO_KNOT));
		l.teks(label, bx + 2.0F, by + 2.0F, bw - 4.0F, Palette.INK);
	}

	/** Lines of what is still missing, by category and exact item; returns the y below them. */
	private float shortfallLines(Lukis l, Bill shortfall, float ty, int tw) {
		for (Bahan cat : Bahan.values()) {
			long centi = shortfall.centi(cat);
			if (centi <= 0L) {
				continue;
			}
			Brush.rect(l.g, panelX, ty + 2.0F, panelX + 3.0F, ty + 7.0F, Palette.opaque(cat.rgb()));
			l.teks(Fmt.line(cat, centi), panelX + 5.0F, ty, tw - 5.0F, Palette.INK);
			ty += LINE;
		}
		for (Map.Entry<Item, Integer> entry : shortfall.exact().entrySet()) {
			Brush.rect(l.g, panelX, ty + 2.0F, panelX + 3.0F, ty + 7.0F, Palette.SEAL);
			l.teks(Fmt.line(entry.getKey(), entry.getValue()), panelX + 5.0F, ty, tw - 5.0F, Palette.INK);
			ty += LINE;
		}
		return ty;
	}

	// ---- Bahan

	private void drawMaterials(Lukis l) {
		int bh = buttonH();
		int rows = rowsTop(1);
		int top = y + l.kepala(Component.translatable("kultivator.sectbuild.panel.materials"), panelX, y, panelX + panelW);
		Bill ledger = bill("ledger");
		Bill need1 = bill("need1");
		Bill need2 = bill("need2");
		float ty = mulaiGulir(l, "bahan", top, rows - 3);
		int tw = panelW - 5;
		l.samar(Component.translatable(K + "bills_head", Math.min(9, tier() + 1)), panelX, ty, tw);
		ty += LINE + 1;
		for (Bahan cat : Bahan.values()) {
			long have = ledger.centi(cat);
			long now = need1.centi(cat);
			long later = need2.centi(cat);
			if (now <= 0L && later <= 0L && have <= 0L) {
				continue;
			}
			float fill = now <= 0L ? 1.0F : Math.min(1.0F, have / (float) now);
			Component label = now > 0L ? Component.translatable(K + "bill_line", cat.displayName(), Fmt.units(have), Fmt.units(now))
					: Component.translatable(K + "bill_have", cat.displayName(), Fmt.units(have));
			bar(l, panelX, ty, tw, fill, Palette.opaque(cat.rgb()), label);
			if (later > 0L) {
				float nextFill = Math.min(1.0F, Math.max(0L, have - now) / (float) later);
				Brush.rect(l.g, panelX, ty + 11.0F, panelX + tw * nextFill, ty + 12.0F, Palette.argb(0xA0, Palette.JADE));
			}
			if (l.di(panelX, Math.round(ty), panelX + tw, Math.round(ty + 12.0F))) {
				l.tip(List.of(cat.displayName(), Component.translatable(K + "bill_tip", Fmt.units(have), Fmt.units(now), Fmt.units(later)),
						Component.translatable("kultivator.sectbuild.bahan_tip." + cat.id())));
			}
			ty += 14.0F;
		}
		// Exact items.
		Map<Item, Integer> exactNeed = new LinkedHashMap<>(need1.exact());
		need2.exact().forEach((item, count) -> exactNeed.merge(item, count, Integer::sum));
		ledger.exact().forEach((item, count) -> exactNeed.putIfAbsent(item, 0));
		if (!exactNeed.isEmpty()) {
			l.samar(Component.translatable(K + "exact_head"), panelX, ty, tw);
			ty += LINE + 1;
			int cellW = 56;
			int perRow = Math.max(1, tw / cellW);
			int index = 0;
			for (Map.Entry<Item, Integer> entry : exactNeed.entrySet()) {
				int ix = panelX + (index % perRow) * cellW;
				int iy = Math.round(ty) + (index / perRow) * 18;
				l.g.item(new ItemStack(entry.getKey()), ix, iy);
				int have = ledger.exact(entry.getKey());
				l.teks(Component.literal(have + "/" + entry.getValue()), ix + 18, iy + 4, cellW - 20, have >= entry.getValue() ? Palette.JADE_DARK : Palette.SEAL_DARK);
				if (l.di(ix, iy, ix + cellW - 2, iy + 17)) {
					l.tip(List.of(new ItemStack(entry.getKey()).getHoverName(), Component.translatable(K + "exact_tip", have, entry.getValue())));
				}
				index++;
			}
			ty += ((index + perRow - 1) / perRow) * 18.0F + 2.0F;
		}
		// The deposit grid: accepted items in the bag.
		l.samar(Component.translatable(K + "bag_head"), panelX, ty, tw);
		ty += LINE + 1;
		ty = depositGrid(l, ty, tw);
		akhirGulir(l, ty);
		int third = (panelW - 4) / 3;
		tombol(l, "bahan:perlu", panelX, rows, third, bh, K + "deposit_needed_short", true, null, () -> act("deposit_needed"), K + "deposit_needed_tip", false);
		tombol(l, "bahan:semua", panelX + third + 2, rows, third, bh, K + "deposit_all_short", true, null, () -> act("deposit_all"), K + "deposit_all_tip", false);
		tombol(l, "bahan:lumbung", panelX + 2 * (third + 2), rows, panelW - 2 * (third + 2), bh, K + "storehouse", !readonly(), roWhy(), () -> act("lumbung"),
				K + "storehouse_tip", false);
	}

	/** The bag's accepted items, one cell per item; click deposits a stack, shift-click all of that item. */
	private float depositGrid(Lukis l, float ty, int tw) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return ty;
		}
		Inventory inventory = mc.player.getInventory();
		Map<Item, int[]> cells = new LinkedHashMap<>();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || Deposits.valueOf(stack.getItem()) == null) {
				continue;
			}
			int first = slot;
			int[] cell = cells.computeIfAbsent(stack.getItem(), item -> new int[]{first, 0});
			cell[1] += stack.getCount();
		}
		if (cells.isEmpty()) {
			return ty + l.bungkus(Component.translatable(K + "bag_empty"), panelX, ty, tw, l.gaya.teksSamar) + 2;
		}
		int cell = 20;
		int perRow = Math.max(1, tw / cell);
		int index = 0;
		// Listed by category (Batu ... Emas & Giok), the exact items last.
		List<Map.Entry<Item, int[]>> sorted = new ArrayList<>(cells.entrySet());
		sorted.sort(java.util.Comparator.comparingInt(entry -> {
			Deposits.Value value = Deposits.valueOf(entry.getKey());
			return value == null ? 99 : value.exact() ? 50 : value.cat().ordinal();
		}));
		for (Map.Entry<Item, int[]> entry : sorted) {
			Item item = entry.getKey();
			int slot = entry.getValue()[0];
			int count = entry.getValue()[1];
			int ix = panelX + (index % perRow) * cell;
			int iy = Math.round(ty) + (index / perRow) * cell;
			Deposits.Value value = Deposits.valueOf(item);
			int edge = value == null ? Palette.INK_FAINT : value.exact() ? Palette.SEAL : Palette.opaque(value.cat().rgb());
			boolean over = l.di(ix, iy, ix + cell - 1, iy + cell - 1);
			l.g.fill(ix, iy, ix + cell - 1, iy + cell - 1, Palette.argb(over ? 0x90 : 0x50, Palette.PAPER_TOP));
			Brush.frame(l.g, ix, iy, ix + cell - 1, iy + cell - 1, Palette.argb(0xC0, edge));
			l.g.item(new ItemStack(item), ix + 1, iy + 1);
			Component n = Component.literal(count > 99 ? "99+" : Integer.toString(count));
			Brush.text(l.g, l.font, n, ix + cell - 1 - l.font.width(n), iy + cell - 9, 0xFFFFFFFF, 1.0F, true);
			List<Component> tip = new ArrayList<>();
			tip.add(new ItemStack(item).getHoverName());
			if (value != null) {
				tip.add(value.exact() ? Component.translatable(K + "value_exact")
						: Component.translatable(K + "value", Fmt.units(value.centi()), value.cat().displayName()));
			}
			tip.add(Component.literal(Integer.toString(count)));
			tip.add(Component.translatable(K + "deposit_click"));
			String key = "bahan:tas:" + index;
			l.zona(key, ix, iy, ix + cell - 1, iy + cell - 1, () -> {
				if (Minecraft.getInstance().hasShiftDown()) {
					act("deposit_item:" + BuiltInRegistries.ITEM.getKey(item));
				} else {
					act("deposit:" + slot);
				}
			}, true, null, true);
			l.fokusCincin(key, ix, iy, ix + cell - 1, iy + cell - 1);
			if (over) {
				l.tip(tip);
			}
			index++;
		}
		return ty + ((index + perRow - 1) / perRow) * (float) cell + 2.0F;
	}

	// ---- Misi

	private void drawMissions(Lukis l) {
		int bh = buttonH();
		int rows = rowsTop(2);
		int top = y + l.kepala(Component.translatable("kultivator.sectbuild.panel.missions"), panelX, y, panelX + panelW);
		CompoundTag missions = b().getCompoundOrEmpty("missions");
		float ty = mulaiGulir(l, "misi", top, rows - 3);
		int tw = panelW - 5;
		if (missions.getBooleanOr("complete", false)) {
			ty += l.bungkus(Component.translatable(K + "sect_complete"), panelX, ty, tw, Palette.JADE_DARK) + 3;
		}
		l.teks(Brush.bold(Component.translatable(K + "tier_missions", missions.getIntOr("tier", 0))), panelX, ty, tw, Palette.SEAL_DARK);
		ty += LINE + 1;
		for (Tag each : missions.getListOrEmpty("lines")) {
			if (!(each instanceof CompoundTag line)) {
				continue;
			}
			Bahan cat = Bahan.byId(line.getStringOr("cat", ""));
			Item item = item(line.getStringOr("item", ""));
			long amount = line.getLongOr("amount", 0L);
			long have = line.getLongOr("have", 0L);
			boolean prep = line.getBooleanOr("prep", false);
			boolean covered = line.getBooleanOr("covered", false);
			boolean paid = line.getBooleanOr("paid", false);
			Component what = cat != null ? Fmt.line(cat, amount) : item != null ? Fmt.line(item, (int) amount) : Component.literal("?");
			Component label = Component.translatable(prep ? K + "line_prep" : K + "line", what, line.getIntOr("pk", 0));
			int color = cat != null ? Palette.opaque(cat.rgb()) : Palette.SEAL;
			bar(l, panelX, ty, tw, amount <= 0L ? 1.0F : have / (float) amount, paid ? Palette.JADE_LIGHT : covered ? Palette.GOLD : color,
					paid ? Component.literal("✓ ").append(label) : label);
			ty += 13.0F;
		}
		ty += 2.0F;
		l.teks(Brush.bold(Component.translatable(K + "offers", missions.getIntOr("pk_today", 0), missions.getIntOr("pk_cap", 150))), panelX, ty, tw,
				Palette.SEAL_DARK);
		ty += LINE + 1;
		ListTag offers = missions.getListOrEmpty("offers");
		if (offers.isEmpty()) {
			ty += l.bungkus(Component.translatable(K + "no_offers"), panelX, ty, tw, l.gaya.teksSamar) + 2;
		}
		Component acceptLabel = Component.translatable(K + "accept");
		int acceptW = TalismanButton.lebar(l.font, acceptLabel, 36, 70);
		for (int i = 0; i < offers.size(); i++) {
			if (!(offers.get(i) instanceof CompoundTag offer)) {
				continue;
			}
			Bahan cat = Bahan.byId(offer.getStringOr("cat", ""));
			if (cat == null) {
				continue;
			}
			float rowH = Math.max(13.0F, bh + 2.0F);
			if ("deliver".equals(offer.getStringOr("kind", ""))) {
				long amount = offer.getLongOr("amount", 0L);
				long progress = offer.getLongOr("progress", 0L) / Bahan.CENTI;
				boolean accepted = offer.getBooleanOr("accepted", false);
				boolean done = offer.getBooleanOr("done", false);
				Component label = Component.translatable(done ? K + "offer_done" : accepted ? K + "offer_progress" : K + "offer_deliver", amount,
						cat.displayName(), progress, offer.getIntOr("pk", 0), offer.getLongOr("bonus", 0L));
				boolean button = !accepted && !done && !readonly();
				float rowW = button ? tw - acceptW - 3 : tw;
				bar(l, panelX, ty + (rowH - 11.0F) / 2.0F, rowW, amount <= 0L ? 0.0F : progress / (float) amount,
						done ? Palette.JADE_LIGHT : Palette.opaque(cat.rgb()), label);
				if (button) {
					int index = i;
					TalismanButton.gambar(l, "misi:terima:" + i, panelX + tw - acceptW, Math.round(ty), acceptW, bh, acceptLabel, true, null,
							() -> act("accept:" + index));
				}
			} else {
				bar(l, panelX, ty + (rowH - 11.0F) / 2.0F, tw, 1.0F, Palette.argb(0xFF, 0xB8E6D0),
						Component.translatable(K + "offer_bless", cat.displayName()));
			}
			ty += rowH;
		}
		// The gathering parties away.
		ty += 2.0F;
		ListTag parties = b().getListOrEmpty("parties");
		l.teks(Brush.bold(Component.translatable(K + "parties", parties.size(), b().getIntOr("party_max", 1))), panelX, ty, tw, Palette.SEAL_DARK);
		ty += LINE + 1;
		for (Tag each : parties) {
			if (!(each instanceof CompoundTag party)) {
				continue;
			}
			Bahan cat = Bahan.byId(party.getStringOr("cat", "batu"));
			List<String> names = new ArrayList<>();
			for (Tag nameTag : party.getListOrEmpty("names")) {
				if (nameTag instanceof CompoundTag compound) {
					compound.read("name", ComponentSerialization.CODEC).ifPresent(name -> names.add(name.getString()));
				}
			}
			long left = HallClient.ticksLeft(party.getLongOr("left", 0L));
			Component line = Component.translatable(K + "party", cat == null ? Component.literal("?") : cat.displayName(), String.join(", ", names),
					Fmt.clock(left), Math.round(party.getDoubleOr("chance", 0.0D) * 100.0D), Fmt.units(party.getLongOr("yield", 0L)));
			ty += l.bungkus(line, panelX, ty, tw, Palette.INK) + 2;
		}
		akhirGulir(l, ty);
		// The party to send: category, length, the people (pop-up picker) and the send talisman.
		int half = (panelW - 3) / 2;
		Bahan cat = Bahan.values()[gatherCat];
		Component catLabel = Component.translatable(M + "kategori", cat.displayName());
		TalismanButton.gambar(l, "misi:kategori", panelX, rows, half, bh, catLabel, !readonly(), roWhy(), () -> {
			gatherCat = (gatherCat + 1) % Bahan.COUNT;
			regu.terpilih().clear();
		});
		koin(l, "misi:lama", panelX + half + 3, rows, panelW - half - 3, bh, Component.translatable(gatherLong ? K + "trip_long" : K + "trip_short"),
				gatherLong, !readonly(), roWhy(), () -> gatherLong = !gatherLong, null);
		List<PemilihOrang.Orang> cands = candidates(cat);
		List<PemilihOrang.Orang> chosen = regu.pilihan(cands);
		int by = rows + bh + 2;
		TalismanButton.gambar(l, "misi:pilih", panelX, by, half, bh, Component.translatable(M + "pilih_regu", chosen.size(), 3), !readonly(), roWhy(),
				regu::bukaSembul);
		boolean canSend = !readonly() && !chosen.isEmpty() && parties.size() < b().getIntOr("party_max", 1);
		Component noSend = readonly() ? roWhy() : chosen.isEmpty() ? Component.translatable(M + "regu_kosong") : Component.translatable(M + "regu_penuh");
		TalismanButton.gambar(l, "misi:kirim", panelX + half + 3, by, panelW - half - 3, bh, Component.translatable(K + "send_party"), canSend, noSend, () -> {
			List<String> ids = new ArrayList<>();
			for (PemilihOrang.Orang o : regu.pilihan(candidates(Bahan.values()[gatherCat]))) {
				ids.add(o.id());
			}
			act("gather:" + Bahan.values()[gatherCat].id() + ":" + (gatherLong ? "1" : "0") + ":" + String.join(",", ids));
			regu.terpilih().clear();
		}, canSend, false);
	}

	/** The disciples that could join a party for {@code cat} (rank and realm checked; the server decides). */
	private List<PemilihOrang.Orang> candidates(Bahan cat) {
		List<PemilihOrang.Orang> out = new ArrayList<>();
		for (Tag each : b().getCompoundOrEmpty("builders").getListOrEmpty("cands")) {
			if (!(each instanceof CompoundTag cand)) {
				continue;
			}
			int eid = cand.getIntOr("eid", -1);
			if (eid < 0) {
				continue;
			}
			int rank = cand.getIntOr("rank", 0);
			boolean rankOk = rank >= minRank(cat) && (cat != Bahan.LOGAM || cand.getIntOr("realm", 0) >= 1);
			Component name = cand.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			Component lock = !rankOk ? Component.translatable(M + "regu_pangkat") : !cand.getBooleanOr("free", false)
					? Component.translatable(M + "regu_sibuk") : null;
			out.add(new PemilihOrang.Orang(Integer.toString(eid), name, 0x7CC8A0, eid,
					List.of(rankName(rank), Component.literal(String.format(Locale.ROOT, "%.1f", cand.getDoubleOr("power", 0.0D)))), null, lock, null));
		}
		return out;
	}

	private static int minRank(Bahan cat) {
		return switch (cat) {
			case GENTENG, KAPUR, PERNIS, LOGAM -> 1;
			case PERMATA -> 2;
			default -> 0;
		};
	}

	// ---- Pekerja

	private void drawBuilders(Lukis l) {
		CompoundTag builders = b().getCompoundOrEmpty("builders");
		int top = y + l.kepala(Component.translatable("kultivator.sectbuild.panel.builders"), panelX, y, panelX + panelW);
		l.teks(Component.translatable(K + "builders_line", builders.getIntOr("active", 0), String.format(Locale.ROOT, "%.2f", builders.getDoubleOr("factor", 1.0D))),
				panelX, top, panelW, Palette.INK_SOFT);
		top += LINE + 1;
		Component hint = Component.translatable(K + "builders_hint");
		int hintH = Math.min(30, l.font.split(hint, Math.max(10, panelW)).size() * 9);
		List<PemilihOrang.Orang> people = new ArrayList<>();
		builderSouls.clear();
		ListTag list = builders.getListOrEmpty("list");
		Component ro = roWhy();
		for (Tag each : list) {
			if (!(each instanceof CompoundTag builder)) {
				continue;
			}
			String soul = builder.getStringOr("soul", "");
			builderSouls.add(soul);
			Component name = builder.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			boolean working = builder.getBooleanOr("working", false);
			Component state = Component.translatable(working ? K + "builder_working" : builder.getBooleanOr("loaded", false) ? K + "builder_idle"
					: K + "builder_away", builder.getLongOr("ticks", 0L) / 1200L);
			people.add(new PemilihOrang.Orang("s:" + soul, name, 0xE8C060, builder.getIntOr("eid", -1), List.of(state), null, ro, null));
		}
		for (Tag each : builders.getListOrEmpty("cands")) {
			if (!(each instanceof CompoundTag cand)) {
				continue;
			}
			int eid = cand.getIntOr("eid", -1);
			if (eid < 0) {
				continue;
			}
			Component name = cand.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			String duty = cand.getStringOr("duty", "");
			Component info = duty.isEmpty() ? rankName(cand.getIntOr("rank", 0)) : Component.translatable(K + "on_duty");
			Component lock = ro != null ? ro : !cand.getBooleanOr("free", false) ? Component.translatable(K + "on_duty") : null;
			people.add(new PemilihOrang.Orang("e:" + eid, name, 0x7CC8A0, eid, List.of(info), null, lock, null));
		}
		// The ticks mirror the server: every builder ticked, every candidate not (a click sends the change at once).
		Set<String> ticked = pekerja.terpilih();
		ticked.clear();
		for (String soul : builderSouls) {
			ticked.add("s:" + soul);
		}
		pekerja.gambar(l, "bangun:pekerja", people, panelX, top, panelX + panelW, y + h - hintH - 2);
		l.bungkus(hint, panelX, y + h - hintH, panelW, l.gaya.teksSamar, hintH);
	}

	/** A tick changed in the builder picker: add the newly ticked candidate, dismiss the unticked builder. */
	private void pekerjaBerubah() {
		if (readonly()) {
			return;
		}
		Set<String> ticked = pekerja.terpilih();
		for (String id : ticked) {
			if (id.startsWith("e:")) {
				act("builder:" + id.substring(2) + ":1");
			}
		}
		for (String soul : builderSouls) {
			if (!ticked.contains("s:" + soul)) {
				act("builder_off:" + soul);
			}
		}
	}

	// ---- Galian

	private void drawSalvage(Lukis l) {
		int bh = buttonH();
		boolean auth = flags().getBooleanOr("can_auth", false);
		int rows = rowsTop(auth ? 2 : 1);
		int top = y + l.kepala(Component.translatable("kultivator.sectbuild.panel.salvage"), panelX, y, panelX + panelW);
		float ty = mulaiGulir(l, "galian", top, rows - 3);
		int tw = panelW - 5;
		// The surplus of Batu, Tanah and Kayu.
		CompoundTag surplus = b().getCompoundOrEmpty("surplus");
		int third = (tw - 4) / 3;
		for (int i = 0; i < Deposits.SURPLUS.length; i++) {
			Bahan cat = Deposits.SURPLUS[i];
			long units = surplus.getLongOr(cat.id(), 0L);
			int bx = panelX + i * (third + 2);
			Component label = Component.translatable(K + "surplus", cat.displayName(), units);
			boolean over = TalismanButton.gambar(l, "galian:lebih:" + i, bx, Math.round(ty), third, bh, label, units > 0L && !readonly(),
					readonly() ? roWhy() : Component.translatable(M + "kosong"), () -> act("surplus:" + cat.id()));
			if (over) {
				l.tip(List.of(label, Component.translatable(K + "surplus_tip", cat.displayName())));
			}
		}
		ty += bh + 4;
		// The Peti Galian.
		CompoundTag salvage = b().getCompoundOrEmpty("salvage");
		ListTag counts = salvage.getListOrEmpty("list");
		if (counts.isEmpty() && salvage.getListOrEmpty("rich").isEmpty()) {
			ty += l.bungkus(Component.translatable(K + "salvage_empty"), panelX, ty, tw, l.gaya.teksSamar) + 2;
		}
		int cellW = 56;
		int perRow = Math.max(1, tw / cellW);
		int index = 0;
		for (Tag each : counts) {
			if (!(each instanceof CompoundTag entry)) {
				continue;
			}
			Item item = item(entry.getStringOr("item", ""));
			if (item == null) {
				continue;
			}
			long count = entry.getLongOr("n", 0L);
			int ix = panelX + (index % perRow) * cellW;
			int iy = Math.round(ty) + (index / perRow) * 19;
			boolean over = l.di(ix, iy, ix + cellW - 2, iy + 18);
			l.g.fill(ix, iy, ix + cellW - 2, iy + 18, Palette.argb(over ? 0x90 : 0x40, Palette.PAPER_TOP));
			l.g.item(new ItemStack(item), ix + 1, iy + 1);
			l.teks(Component.literal(count > 9999 ? "9999+" : Long.toString(count)), ix + 19, iy + 5, cellW - 22, Palette.INK);
			String key = "galian:peti:" + index;
			l.zona(key, ix, iy, ix + cellW - 2, iy + 18, () -> act("take:" + BuiltInRegistries.ITEM.getKey(item)), !readonly(), roWhy(), true);
			l.fokusCincin(key, ix, iy, ix + cellW - 2, iy + 18);
			if (over) {
				l.tip(List.of(new ItemStack(item).getHoverName(), Component.translatable(K + "take_tip", count)));
			}
			index++;
		}
		ty += ((index + perRow - 1) / perRow) * 19.0F + 2.0F;
		if (salvage.getIntOr("more", 0) > 0) {
			l.samar(Component.translatable(K + "salvage_more", salvage.getIntOr("more", 0)), panelX, ty, tw);
			ty += LINE;
		}
		for (Tag each : salvage.getListOrEmpty("rich")) {
			if (!(each instanceof CompoundTag entry)) {
				continue;
			}
			int richIndex = entry.getIntOr("index", 0);
			Component name = entry.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			TalismanButton.gambar(l, "galian:kaya:" + richIndex, panelX, Math.round(ty), tw, bh,
					Component.empty().append(name).append(" ×" + entry.getIntOr("n", 1)), !readonly(), roWhy(), () -> act("take_rich:" + richIndex));
			ty += bh + 2;
		}
		akhirGulir(l, ty);
		boolean exc = flags().getBooleanOr("exc", true);
		koin(l, "galian:pakai", panelX, rows, panelW, bh, Component.translatable(K + "use_excavation"), exc, !readonly(), roWhy(),
				() -> act("flag:exc:" + (exc ? "0" : "1")),
				List.of(Component.translatable(K + "use_excavation"), Component.translatable(K + "use_excavation_tip")));
		if (auth) {
			int half = (panelW - 3) / 2;
			int by = rows + bh + 2;
			boolean rm = flags().getBooleanOr("rm", false);
			koin(l, "galian:hapus", panelX, by, half, bh, Component.translatable(K + "remove_player"), rm, true, null, () -> act("flag:rm:" + (rm ? "0" : "1")),
					List.of(Component.translatable(K + "remove_player"), Component.translatable(K + "remove_player_tip")));
			boolean empty = flags().getBooleanOr("empty", false);
			koin(l, "galian:kosongkan", panelX + half + 3, by, panelW - half - 3, bh, Component.translatable(K + "empty_containers"), empty, true, null,
					() -> act("flag:empty:" + (empty ? "0" : "1")),
					List.of(Component.translatable(K + "empty_containers"), Component.translatable(K + "empty_containers_tip")));
		}
	}

	// ---- Templat (with a template standing)

	private void drawTemplate(Lukis l) {
		int bh = buttonH();
		int rows = rowsTop(3);
		int top = y + l.kepala(Component.translatable("kultivator.sectbuild.panel.template"), panelX, y, panelX + panelW);
		float ty = mulaiGulir(l, "templat", top, rows - 3);
		int tw = panelW - 5;
		CompoundTag site = site();
		Glaze glaze = Glaze.byId(site.getStringOr("glaze", "tinta"));
		Component gate = Component.translatable("kultivator.sectbuild.gate." + SectTemplates.gate(site.getIntOr("rot", 0)).getSerializedName());
		ty += l.bungkus(Component.translatable(K + "template_line", SectTemplates.name(template()), glaze == null ? Component.literal("?") : glaze.displayName(), gate),
				panelX, ty, tw, Palette.INK) + 1;
		CompoundTag guarantee = b().getCompoundOrEmpty("guarantee");
		ty += l.bungkus(Component.translatable(K + "coupling", b().getIntOr("frozen_cap", -1), guarantee.getIntOr("tier", 0)), panelX, ty, tw,
				Palette.INK_SOFT) + 3;
		// The obstacles: blocked components with their talismans.
		boolean anyBlocked = false;
		for (Tag each : b().getListOrEmpty("comps")) {
			if (!(each instanceof CompoundTag comp) || comp.getIntOr("st", 0) != ComponentView.STATE_BLOCKED) {
				continue;
			}
			if (!anyBlocked) {
				l.teks(Brush.bold(Component.translatable(K + "obstacles")), panelX, ty, tw, Palette.SEAL);
				ty += LINE + 1;
				anyBlocked = true;
			}
			String id = comp.getStringOr("id", "");
			Component name = comp.read("name", ComponentSerialization.CODEC).orElse(Component.literal(id));
			l.teks(Component.translatable(K + "blocked_line", name, comp.getIntOr("bl", 0)), panelX, ty, tw, Palette.INK);
			ty += LINE;
			String[] keys = {K + "recheck", K + "skip", K + "finish_blocked"};
			String[] actions = {"recheck", "skip:" + id, "finish_blocked:" + id};
			int each3 = (tw - 4) / 3;
			for (int i = 0; i < 3; i++) {
				String action = actions[i];
				TalismanButton.gambar(l, "templat:hambat:" + id + ":" + i, panelX + i * (each3 + 2), Math.round(ty), each3, bh, Component.translatable(keys[i]),
						!readonly(), roWhy(), () -> act(action));
			}
			ty += bh + 3;
		}
		// Prioritaskan: the queued components of the tier being built.
		int building = Math.min(9, tier() + 1);
		boolean anyQueued = false;
		Component prioLabel = Component.translatable(K + "prioritize");
		int prioW = TalismanButton.lebar(l.font, prioLabel, 40, 100);
		for (Tag each : b().getListOrEmpty("comps")) {
			if (!(each instanceof CompoundTag comp) || comp.getIntOr("st", 0) != ComponentView.STATE_QUEUED || comp.getIntOr("tier", 0) != building) {
				continue;
			}
			if (!anyQueued) {
				l.teks(Brush.bold(Component.translatable(K + "queue")), panelX, ty, tw, Palette.SEAL_DARK);
				ty += LINE + 1;
				anyQueued = true;
			}
			String id = comp.getStringOr("id", "");
			Component name = comp.read("name", ComponentSerialization.CODEC).orElse(Component.literal(id));
			l.teks(name, panelX, ty + (bh - 8) / 2.0F, tw - prioW - 4, Palette.INK);
			TalismanButton.gambar(l, "templat:prio:" + id, panelX + tw - prioW, Math.round(ty), prioW, bh, prioLabel, !readonly(), roWhy(), () -> act("prio:" + id));
			ty += bh + 2;
		}
		// The history.
		ListTag history = b().getListOrEmpty("history");
		if (!history.isEmpty()) {
			ty += 2.0F;
			l.teks(Brush.bold(Component.translatable(K + "history")), panelX, ty, tw, Palette.SEAL_DARK);
			ty += LINE + 1;
			for (int i = history.size() - 1; i >= 0; i--) {
				if (history.get(i) instanceof CompoundTag entry) {
					long to = entry.getLongOr("to", -1L);
					Component line = Component.translatable(to < 0L ? K + "history_now" : K + "history_line", SectTemplates.name(entry.getIntOr("t", 1)),
							entry.getLongOr("from", 0L), to);
					l.teks(line, panelX, ty, tw, Palette.INK_SOFT);
					ty += LINE;
				}
			}
		}
		akhirGulir(l, ty);
		// The six talismans: upgrade, repair / deconstruct, total / abandon, change.
		int half = (panelW - 3) / 2;
		int right = panelX + half + 3;
		int rightW = panelW - half - 3;
		boolean canUpgrade = !readonly() && SectTemplates.rank(template()) < b().getIntOr("max_t", 9);
		tombol(l, "templat:naik", panelX, rows, half, bh, K + "upgrade", canUpgrade, readonly() ? roWhy() : Component.translatable(M + "naik_maks"), () -> {
			katalog = true;
			catalog.select(SectTemplates.isSky(template()) ? Math.min(SectTemplates.SKY_LAST, template() + 1) : Math.min(9, template() + 1));
			catalog.opened();
		}, M + "naik.tip", false);
		tombol(l, "templat:perbaiki", right, rows, rightW, bh, K + "repair", !readonly(), roWhy(), () -> act("repair"), K + "repair_tip", false);
		int r2 = rows + bh + 2;
		tombol(l, "templat:bongkar", panelX, r2, half, bh, K + "deconstruct", !readonly(), roWhy(),
				() -> minta(SegelKonfirmasi.Tingkat.SEDANG, Component.translatable(M + "bongkar"), () -> act("deconstruct:0")), M + "bongkar", false);
		tombol(l, "templat:bongkar_total", right, r2, rightW, bh, K + "deconstruct_total", !readonly(), roWhy(),
				() -> minta(SegelKonfirmasi.Tingkat.BERAT, Component.translatable(M + "bongkar_total"), () -> act("deconstruct:1")), M + "bongkar_total", false);
		int r3 = r2 + bh + 2;
		tombol(l, "templat:tinggalkan", panelX, r3, half, bh, K + "abandon", !readonly(), roWhy(),
				() -> minta(SegelKonfirmasi.Tingkat.BERAT, Component.translatable(M + "tinggalkan"), () -> act("abandon")), M + "tinggalkan", false);
		boolean canChange = !readonly() && b().getBooleanOr("can_change", false);
		tombol(l, "templat:ganti", right, r3, rightW, bh, K + "change", canChange, readonly() ? roWhy() : Component.translatable(M + "ganti_tidak"),
				() -> act("change"), K + "change_tip", false);
	}

	// ---- Penyumbang

	private void drawDonors(Lukis l) {
		int top = y + l.kepala(Component.translatable("kultivator.sectbuild.panel.donors"), panelX, y, panelX + panelW);
		float ty = mulaiGulir(l, "penyumbang", top, y + h);
		int tw = panelW - 5;
		ty += l.bungkus(Component.translatable(K + "donors_text"), panelX, ty, tw, Palette.INK_SOFT) + 3;
		ListTag donors = b().getListOrEmpty("donors");
		int rank = 1;
		for (Tag each : donors) {
			if (each instanceof CompoundTag donor) {
				l.teks(Component.translatable(K + "donor_line", rank++, donor.getStringOr("name", "?"), donor.getLongOr("units", 0L)), panelX, ty, tw, Palette.INK);
				ty += LINE;
			}
		}
		if (donors.isEmpty()) {
			ty += l.bungkus(Component.translatable(K + "no_donors"), panelX, ty, tw, l.gaya.teksSamar);
		}
		akhirGulir(l, ty);
	}

	// ================================================================== small helpers

	private static Component rankName(int rank) {
		return Component.translatable("kultivator.hall.rank." + RANK_IDS[Math.max(0, Math.min(RANK_IDS.length - 1, rank))]);
	}

	private static @Nullable Item item(String id) {
		Identifier key = Identifier.tryParse(id);
		Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
		return item == null || item == Items.AIR ? null : item;
	}
}
