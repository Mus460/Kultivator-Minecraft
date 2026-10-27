package dev.kultivator.client.dunia.peta;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.dunia.kapal.HelmScreen;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.LembarHub;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.InkField;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.dunia.DuniaMarkers;
import dev.kultivator.dunia.kapal.KapalRohEntity;
import dev.kultivator.dunia.peta.PetaItems;
import dev.kultivator.dunia.peta.PetaNet;
import dev.kultivator.dunia.tempat.ui.LembarKunci.PetaKota;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Dunia › Peta (DESIGN 34.5.16, 43.5, module M12 of 43.16.2), the one map page of the Gulungan Utama, on the wide paper.
 * <ul>
 * <li>Sub {@code dunia}: Peta Dunia Kultivasi, an ink-wash parchment of the explored world ({@link PetaPainter}), the known
 * sites with their marker icons (34.4.8; undiscovered sites never show), live omens, waypoints, the own ship and its
 * voyage line, the return point and a soul's body, sect territories with their vassal villages, the NPC sect domains with
 * inked borders and seals, and the layers (Cuaca Roh, Peristiwa Langit, Segel, Jalur Kapal). Drag to pan, wheel or +/- for
 * the three zooms (1 px = 16 / 64 / 256 b), click a marker for its card ("Berlayar" for a captain, "Tandai"), right-click
 * the paper to set a waypoint; Tab focuses the map, then arrows pan and Home centres. The side panel (search, category
 * coins, the known sites) shows from 360 px; narrower, "Daftar" swaps the map for that list.</li>
 * <li>Subs {@code kota}, {@code layanan}, {@code tokoh}, {@code acara}: the capital's sheet {@code peta_kota}
 * ({@code LayarPetaKota}, G23/G70) mounted through {@link LembarHub} without a registration of its own; its four tabs are
 * this page's subs (no fourth level, 43.0.4).</li>
 * <li>Sail mode (R13, G58): the helm's "Pilih tujuan di Peta" ({@link #berlayar}) opens the map with a banner; the list
 * starts with the helm's voyages (the own sect hall, the routes other modules registered), every marker's card sails; after
 * a choice, "‹ Kemudi" or Esc the Gulungan goes back to Di Sini › Kemudi. The mode ends with the helm context.</li>
 * </ul>
 * Markers and layers are GUI fills; one texture holds the painted paper. Text at 1.0 (43.0.5). The map asks the server for
 * a fresh sheet when shown and for explored regions in view while shown (43.0.12). Client only.
 */
public final class PetaScreen extends Page {

	public static final String SUB_DUNIA = "dunia";
	public static final String SUB_KOTA = "kota";
	private static final String M = "kultivator.gui43.m12.";
	private static final String SUB = "kultivator.gui43.sub." + Urutan.ID_PETA + ".";
	private static final int[] ZOOMS = {16, 64, 256};
	private static final Identifier MARKERS = dev.kultivator.Kultivator.id("textures/dunia/peta/markers.png");
	/** Second marker atlas (38W), cells {@link dev.kultivator.dunia.DuniaMarkers#ATLAS2_FIRST} and up, same 8 x 4 layout. */
	private static final Identifier MARKERS_TEMPAT = dev.kultivator.Kultivator.id("textures/dunia/peta/markers_tempat.png");
	private static final Identifier COMPASS = dev.kultivator.Kultivator.id("textures/dunia/peta/compass.png");
	private static final int SIDE_W = 118;
	private static final int HEAD_H = 11;
	private static final int BAR_H = 14;
	private static final int BANNER_H = 16;
	private static final int ROW_H = 13;

	/** Marker categories (the side panel's filter coins). */
	enum Category {
		KOTA(0), RERUNTUHAN(3), PENJARA(5), MENARA(6), BAWAH(7), LAUT(10), LANGIT(14), SEKTE(19), TANDA(23), KAPAL(17);

		final int icon;

		Category(int icon) {
			this.icon = icon;
		}

		static Category of(String kind) {
			if (kind == null) {
				return LANGIT;
			}
			if (kind.startsWith("kota/")) {
				return KOTA;
			}
			if (kind.startsWith("reruntuhan/")) {
				return RERUNTUHAN;
			}
			if (kind.startsWith("penjara/")) {
				return PENJARA;
			}
			if (kind.startsWith("menara/")) {
				return MENARA;
			}
			if (kind.startsWith("bawah/")) {
				return BAWAH;
			}
			if (kind.startsWith("laut/")) {
				return LAUT;
			}
			if (kind.startsWith("sekte/") || kind.startsWith("desa/")) {
				return SEKTE;
			}
			if (kind.startsWith("kapal/")) {
				return KAPAL;
			}
			if (kind.startsWith("tanda")) {
				return TANDA;
			}
			return LANGIT;
		}
	}

	/** One marker on the map. */
	static final class Mark {
		String kind = "";
		int x;
		int z;
		int tier;
		int icon;
		int color;
		int minZoom;
		boolean live;
		long id;
		int waypoint = -1;
		int seal = -1;
		boolean broken;
		String guard = "";
		long ends;
		Component name = Component.empty();
		Category category = Category.LANGIT;
	}

	private static int zoomIndex;
	private static final Set<Category> HIDDEN = EnumSet.noneOf(Category.class);
	private static boolean overlayWeather = true;
	private static boolean overlaySky = true;
	private static boolean overlaySeals = true;
	private static boolean overlayRoute = true;
	private static double viewX = Double.NaN;
	private static double viewZ = Double.NaN;
	private static String viewDim = "";
	/** Sail mode (R13): asked by the helm; valid only while the helm is the Di Sini context ({@link #modeBerlayar}). */
	private static boolean berlayar;

	private final List<Mark> marks = new ArrayList<>();
	private final List<Mark> listed = new ArrayList<>();
	private int seenSheet = -1;
	private final InkField cari;
	private final DaftarGulir daftar = new DaftarGulir();
	private @Nullable Mark selected;
	private @Nullable Mark hover;
	private int mx0;
	private int my0;
	private int mx1;
	private int my1;
	private boolean side;
	/** Without the side panel: the list shown instead of the map. */
	private boolean daftarTampil;
	private boolean dragging;
	private double dragX;
	private double dragY;
	private double pressX;
	private double pressY;
	private long lastAsk;
	private int cardX;
	private int cardY;
	private int cardW;
	private int cardH;
	private @Nullable TalismanButton tombolDaftar;

	/** The capital's sheet (subs kota, layanan, tokoh, acara). */
	private final LembarHub.Halaman kota;
	private String sub = SUB_DUNIA;
	private boolean ditampilkan;
	private String laidKota = "";

	public PetaScreen() {
		cari = new InkField(0, 0, 80, 14, 32, null).cari(Component.translatable("kultivator.peta.search"), value -> rebuildList());
		// an unregistered mount: the page id stays "peta" (one coin); the sheet's read-only view and its ops work from here
		kota = LembarHub.dari(new HubPages.Entri(HubNav.DUNIA, Urutan.ID_PETA, Urutan.PETA, () -> null, PetaKota.LAYAR, Set.of()));
		daftar.judul(this::judulBaris, 13);
	}

	/** The helm's "Pilih tujuan di Peta": opens the map in sail mode (R13). */
	public static void berlayar() {
		berlayar = true;
		HubNav.buka(Tujuan.of(HubNav.DUNIA, Urutan.ID_PETA).sub(SUB_DUNIA));
	}

	/** The helm page when sail mode is on and the helm context still lives; ends the mode otherwise. */
	private static @Nullable HelmScreen modeBerlayar() {
		HelmScreen helm = berlayar ? HelmScreen.terbuka() : null;
		if (helm == null) {
			berlayar = false;
		}
		return helm;
	}

	/** Ends sail mode and goes back to Di Sini › Kemudi. */
	private void kembaliKeKemudi() {
		boolean ada = HelmScreen.terbuka() != null;
		berlayar = false;
		selected = null;
		if (ada) {
			tuju(Tujuan.bagian(HubNav.DI_SINI));
		}
	}

	private boolean subKota() {
		return !SUB_DUNIA.equals(sub);
	}

	private static String subDari(String tab) {
		return PetaKota.TAB_PETA.equals(tab) ? SUB_KOTA : tab;
	}

	private static String tabDari(String sub) {
		return SUB_KOTA.equals(sub) ? PetaKota.TAB_PETA : sub;
	}

	// ================================================================== identity

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.dunia." + Urutan.ID_PETA);
	}

	@Override
	public int glyph() {
		return Glyphs.PATH;
	}

	@Override
	public String helpTopic() {
		return subKota() ? kota.helpTopic() : "peta_dunia";
	}

	@Override
	public List<Sub> subs() {
		List<Sub> subs = new ArrayList<>();
		subs.add(new Sub(SUB_DUNIA, Component.translatable(SUB + SUB_DUNIA)));
		for (String tab : PetaKota.TABS) {
			subs.add(new Sub(subDari(tab), Component.translatable(SUB + subDari(tab))));
		}
		return subs;
	}

	@Override
	public void showSub(String id) {
		String next = id == null || id.isEmpty() ? SUB_DUNIA : id;
		if (!SUB_DUNIA.equals(next) && !PetaKota.TABS.contains(tabDari(next))) {
			return;
		}
		boolean dulu = subKota();
		sub = next;
		boolean kini = subKota();
		if (kini) {
			kota.showSub(tabDari(next));
		}
		if (ditampilkan && dulu != kini) {
			if (dulu) {
				kota.onHide();
				bukaPeta();
			} else {
				tutupPeta();
				pasangKota();
				kota.onShow(null);
			}
		}
	}

	@Override
	public boolean lebar() {
		return true;
	}

	@Override
	public long uang() {
		return subKota() ? kota.uang() : -1L;
	}

	@Override
	public boolean rinci() {
		return subKota() ? kota.rinci() : daftarTampil || berlayar && modeBerlayar() != null;
	}

	@Override
	public Palette.Gaya gaya() {
		return subKota() ? kota.gaya() : Palette.Gaya.KERTAS;
	}

	@Override
	public boolean angka(int n) {
		return subKota() ? kota.angka(n) : super.angka(n);
	}

	// ================================================================== life

	@Override
	protected void build() {
		laidKota = "";
		side = w >= 360;
		mx0 = x;
		my0 = y + HEAD_H + 1;
		mx1 = side ? x + w - SIDE_W - 6 : x + w;
		my1 = y + h - BAR_H - 3;
		// toolbar: zoom (one cycling talisman), the four layers, centre; without the side panel the list toggle
		int by = y + h - BAR_H;
		TalismanButton zoom = new TalismanButton(x, by, 58, BAR_H, () -> Component.translatable("kultivator.peta.zoom." + zoomIndex));
		zoom.siklus(() -> setZoom((zoomIndex + 1) % ZOOMS.length, (mx0 + mx1) / 2.0D, (my0 + my1) / 2.0D),
				() -> setZoom((zoomIndex + ZOOMS.length - 1) % ZOOMS.length, (mx0 + mx1) / 2.0D, (my0 + my1) / 2.0D));
		zoom.tooltip(() -> List.of(Component.translatable("kultivator.peta.zoom.tip", bpp())));
		add(zoom);
		Component centreLabel = Component.translatable("kultivator.peta.centre");
		int centreW = TalismanButton.lebar(font, centreLabel, 40, 72);
		Component daftarLabel = Component.translatable(M + "daftar");
		int daftarW = side ? 0 : TalismanButton.lebar(font, daftarLabel, 40, 64) + 4;
		String[] ids = {"weather", "sky", "seal", "route"};
		int labelled = 0;
		for (String id : ids) {
			labelled += BAR_H + 4 + font.width(Component.translatable("kultivator.peta.overlay." + id)) + 4;
		}
		boolean label = 62 + labelled + daftarW + centreW + 4 <= w;
		int bx = x + 62;
		for (String id : ids) {
			Component name = Component.translatable("kultivator.peta.overlay." + id);
			int cw = label ? BAR_H + 4 + font.width(name) + 1 : TalismanButton.MIN_W;
			JadeCoin coin = new JadeCoin(bx, by + 1, cw, BAR_H - 2, () -> label ? name : Component.empty(), () -> overlay(id));
			coin.onPress(() -> toggleOverlay(id));
			coin.tooltip(() -> List.of(Brush.bold(name), Component.translatable("kultivator.peta.overlay." + id + ".tip").withStyle(ChatFormatting.GRAY)));
			add(coin);
			bx += cw + (label ? 4 : 3);
		}
		TalismanButton centre = new TalismanButton(x + w - centreW, by, centreW, BAR_H, () -> centreLabel);
		centre.onPress(this::centre);
		add(centre);
		tombolDaftar = null;
		if (!side) {
			TalismanButton t = new TalismanButton(x + w - centreW - daftarW, by, daftarW - 4, BAR_H,
					() -> Component.translatable(daftarTampil ? M + "peta" : M + "daftar"));
			t.onPress(() -> {
				daftarTampil = !daftarTampil;
				selected = null;
			});
			t.stampedWhen(() -> daftarTampil);
			tombolDaftar = add(t);
		} else {
			daftarTampil = false;
		}
		if (subKota()) {
			pasangKota();
		}
	}

	/** Lays the capital's sheet page into the box when the box changed since its last layout. */
	private void pasangKota() {
		if (w <= 0 || h <= 0) {
			return;
		}
		if (screen != null) {
			kota.pasang(screen);
		}
		String box = x + "," + y + "," + w + "," + h;
		if (!box.equals(laidKota)) {
			laidKota = box;
			kota.layout(x, y, w, h);
		}
	}

	@Override
	protected boolean visible(Widget widget) {
		if (subKota()) {
			return false;
		}
		return widget == tombolDaftar || !daftarTampil;
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		ditampilkan = true;
		if (subKota()) {
			pasangKota();
			kota.onShow(fokus);
		} else {
			bukaPeta();
		}
	}

	@Override
	public void onHide() {
		ditampilkan = false;
		if (subKota()) {
			kota.onHide();
		} else {
			tutupPeta();
		}
	}

	/** The world map came on screen: a fresh sheet, the view on the player when the dimension changed. */
	private void bukaPeta() {
		PetaClient.request(PetaNet.OP_OPEN, 0, 0, "", new long[0]);
		PetaCache.forgetAsked();
		LocalPlayer player = minecraft.player;
		String dim = PetaCache.dim();
		if (player != null && (Double.isNaN(viewX) || !dim.equals(viewDim))) {
			viewX = player.getX();
			viewZ = player.getZ();
			viewDim = dim;
		}
		readSheet(true);
	}

	private void tutupPeta() {
		dragging = false;
		PetaClient.request(PetaNet.OP_CLOSE, 0, 0, "", new long[0]);
	}

	@Override
	public void tick() {
		if (subKota()) {
			kota.tick();
			return;
		}
		readSheet(false);
		askRegions();
	}

	@Override
	public void onData() {
		if (subKota()) {
			kota.onData();
		}
	}

	@Override
	public boolean needsRebuild() {
		return subKota() && kota.needsRebuild();
	}

	private boolean overlay(String id) {
		return switch (id) {
			case "weather" -> overlayWeather;
			case "sky" -> overlaySky;
			case "seal" -> overlaySeals;
			default -> overlayRoute;
		};
	}

	private void toggleOverlay(String id) {
		switch (id) {
			case "weather" -> overlayWeather = !overlayWeather;
			case "sky" -> overlaySky = !overlaySky;
			case "seal" -> overlaySeals = !overlaySeals;
			default -> overlayRoute = !overlayRoute;
		}
	}

	private int bpp() {
		return ZOOMS[Mth.clamp(zoomIndex, 0, ZOOMS.length - 1)];
	}

	/** K's zoom numbering of {@code DuniaMarkers} (0 = 1 px per 256 b ... 2 = 16 b). */
	private int markerZoom() {
		return 2 - Mth.clamp(zoomIndex, 0, 2);
	}

	private void setZoom(int index, double sx, double sy) {
		int next = Mth.clamp(index, 0, ZOOMS.length - 1);
		if (next == zoomIndex) {
			return;
		}
		double cx = (mx0 + mx1) / 2.0D;
		double cy = (my0 + my1) / 2.0D;
		double wx = viewX + (sx - cx) * bpp();
		double wz = viewZ + (sy - cy) * bpp();
		zoomIndex = next;
		viewX = wx - (sx - cx) * bpp();
		viewZ = wz - (sy - cy) * bpp();
		UiSound.tab();
	}

	private void centre() {
		LocalPlayer player = minecraft.player;
		if (player != null) {
			viewX = player.getX();
			viewZ = player.getZ();
		}
	}

	// ================================================================== the sheet

	private void readSheet(boolean force) {
		if (!force && seenSheet == PetaCache.sheetVersion()) {
			return;
		}
		seenSheet = PetaCache.sheetVersion();
		marks.clear();
		CompoundTag sheet = PetaCache.sheet();
		for (Tag tag : sheet.getListOrEmpty("sites")) {
			if (tag instanceof CompoundTag s) {
				marks.add(site(s));
			}
		}
		for (Tag tag : sheet.getListOrEmpty("live")) {
			if (tag instanceof CompoundTag s) {
				Mark mark = site(s);
				mark.live = true;
				mark.ends = s.getLongOr("ends", 0L);
				marks.add(mark);
			}
		}
		for (Tag tag : sheet.getListOrEmpty("waypoints")) {
			if (tag instanceof CompoundTag w) {
				Mark mark = new Mark();
				mark.kind = "tanda";
				mark.x = w.getIntOr("x", 0);
				mark.z = w.getIntOr("z", 0);
				mark.icon = 23;
				mark.color = 0xFFFFFF & w.getIntOr("color", 0xB3281D);
				mark.waypoint = w.getIntOr("i", -1);
				String name = w.getStringOr("name", "");
				mark.name = name.isEmpty() ? Component.translatable("kultivator.peta.waypoint.unnamed", mark.waypoint + 1) : Component.literal(name);
				mark.category = Category.TANDA;
				marks.add(mark);
			}
		}
		CompoundTag ship = sheet.getCompoundOrEmpty("ship");
		if (!ship.isEmpty()) {
			Mark mark = new Mark();
			mark.kind = "kapal/kapal";
			mark.x = ship.getIntOr("x", 0);
			mark.z = ship.getIntOr("z", 0);
			mark.icon = 17;
			mark.color = 0xD0A060;
			Component shipClass = Component.translatable("kultivator.kapal.class."
					+ dev.kultivator.dunia.kapal.KapalClass.byOrdinal(ship.getIntOr("class", 0)).model());
			String shipName = ship.getStringOr("name", "");
			mark.name = Component.translatable("kultivator.peta.mark.ship", shipName.isEmpty() ? shipClass
					: Component.translatable("kultivator.kapal.nameplate", Component.literal(shipName), shipClass));
			mark.category = Category.KAPAL;
			marks.add(mark);
		}
		CompoundTag ret = sheet.getCompoundOrEmpty("return");
		if (!ret.isEmpty()) {
			marks.add(simple("kembali", ret.getIntOr("x", 0), ret.getIntOr("z", 0), 25, 0x9A70E0, "kultivator.peta.mark.return", Category.TANDA));
		}
		CompoundTag body = sheet.getCompoundOrEmpty("body");
		if (!body.isEmpty()) {
			marks.add(simple("tubuh", body.getIntOr("x", 0), body.getIntOr("z", 0), 26, 0xB8FFF0, "kultivator.peta.mark.body", Category.TANDA));
		}
		for (Tag tag : sheet.getListOrEmpty("halls")) {
			if (!(tag instanceof CompoundTag h)) {
				continue;
			}
			Mark mark = new Mark();
			mark.kind = "sekte/aula";
			mark.x = h.getIntOr("x", 0);
			mark.z = h.getIntOr("z", 0);
			mark.icon = 19;
			mark.color = 0xFFFFFF & h.getIntOr("color", 0xFFD27A);
			String name = h.getStringOr("name", "");
			String owner = h.getStringOr("owner", "");
			mark.name = !name.isBlank() ? Component.literal(name) : owner.isEmpty() ? Component.translatable("kultivator.peta.mark.hall")
					: Component.translatable("kultivator.peta.mark.hall_of", owner);
			mark.tier = h.getIntOr("level", 0);
			mark.category = Category.SEKTE;
			marks.add(mark);
			for (Tag v : h.getListOrEmpty("villages")) {
				if (v instanceof CompoundTag village) {
					Mark m = simple("desa/desa", village.getIntOr("x", 0), village.getIntOr("z", 0), 21, 0xB0C890, null, Category.SEKTE);
					String vn = village.getStringOr("name", "");
					m.name = vn.isEmpty() ? Component.translatable("kultivator.dunia.marker.desa.desa") : Component.literal(vn);
					marks.add(m);
				}
			}
		}
		rebuildList();
	}

	private Mark simple(String kind, int x, int z, int icon, int color, @Nullable String key, Category category) {
		Mark mark = new Mark();
		mark.kind = kind;
		mark.x = x;
		mark.z = z;
		mark.icon = icon;
		mark.color = color;
		mark.name = key == null ? Component.empty() : Component.translatable(key);
		mark.category = category;
		return mark;
	}

	private Mark site(CompoundTag s) {
		Mark mark = new Mark();
		mark.kind = s.getStringOr("kind", "");
		mark.x = s.getIntOr("x", 0);
		mark.z = s.getIntOr("z", 0);
		mark.tier = s.getIntOr("tier", 0);
		mark.id = s.getLongOr("id", 0L);
		DuniaMarkers.Marker marker = DuniaMarkers.kind(mark.kind);
		mark.icon = marker.icon();
		mark.color = marker.color();
		mark.minZoom = marker.minZoom();
		mark.seal = s.contains("seal") ? s.getIntOr("seal", 100) : -1;
		mark.broken = s.getBooleanOr("broken", false);
		mark.guard = s.getStringOr("guard", "");
		mark.name = Component.translatableWithFallback(marker.langKey(), pretty(mark.kind));
		mark.category = Category.of(mark.kind);
		if (mark.broken) {
			mark.icon = 28;
		}
		return mark;
	}

	private static String pretty(String kind) {
		String tail = kind.contains("/") ? kind.substring(kind.indexOf('/') + 1) : kind;
		tail = tail.replace('_', ' ');
		return tail.isEmpty() ? "?" : Character.toUpperCase(tail.charAt(0)) + tail.substring(1);
	}

	private void rebuildList() {
		listed.clear();
		LocalPlayer player = minecraft.player;
		for (Mark mark : marks) {
			if (HIDDEN.contains(mark.category)) {
				continue;
			}
			if (!cari.kosong() && !cari.cocok(mark.name) && !mark.kind.contains(cari.text().strip().toLowerCase(Locale.ROOT))) {
				continue;
			}
			listed.add(mark);
		}
		if (player != null) {
			listed.sort((a, b) -> Double.compare(dist2(player, a), dist2(player, b)));
		}
	}

	private static double dist2(LocalPlayer player, Mark mark) {
		double dx = mark.x - player.getX();
		double dz = mark.z - player.getZ();
		return dx * dx + dz * dz;
	}

	/** Asks for the explored regions in view that were never asked (at most 64 per half second). */
	private void askRegions() {
		long now = Util.getMillis();
		if (now - lastAsk < 500L) {
			return;
		}
		long[] explored = PetaCache.sheet().getLongArray("regions").orElse(new long[0]);
		if (explored.length == 0) {
			return;
		}
		int bpp = bpp();
		int span = 32 * PetaCache.cellSize();
		double halfW = (mx1 - mx0) * 0.5D * bpp + span;
		double halfH = (my1 - my0) * 0.5D * bpp + span;
		int rx0 = Math.floorDiv((int) Math.floor(viewX - halfW), span);
		int rx1 = Math.floorDiv((int) Math.floor(viewX + halfW), span);
		int rz0 = Math.floorDiv((int) Math.floor(viewZ - halfH), span);
		int rz1 = Math.floorDiv((int) Math.floor(viewZ + halfH), span);
		LongArrayList ask = new LongArrayList();
		for (long key : explored) {
			int rx = (int) (key >> 32);
			int rz = (int) key;
			if (rx < rx0 || rx > rx1 || rz < rz0 || rz > rz1 || PetaCache.asked(key)) {
				continue;
			}
			ask.add(key);
			if (ask.size() >= PetaNet.MAX_REGIONS) {
				break;
			}
		}
		if (!ask.isEmpty()) {
			lastAsk = now;
			for (long key : ask) {
				PetaCache.markAsked(key);
			}
			PetaClient.request(PetaNet.OP_REGIONS, 0, 0, "", ask.toLongArray());
		}
	}

	// ================================================================== drawing: the map (behind)

	private boolean overMap(double mx, double my) {
		return !daftarTampil && mx >= mx0 && mx < mx1 && my >= my0 && my < my1;
	}

	private double worldX(double sx) {
		return viewX + (sx - (mx0 + mx1) / 2.0D) * bpp();
	}

	private double worldZ(double sy) {
		return viewZ + (sy - (my0 + my1) / 2.0D) * bpp();
	}

	private float screenX(double wx) {
		return (float) ((mx0 + mx1) / 2.0D + (wx - viewX) / bpp());
	}

	private float screenY(double wz) {
		return (float) ((my0 + my1) / 2.0D + (wz - viewZ) / bpp());
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (subKota()) {
			pasangKota();
			kota.render(g, mouseX, mouseY, seconds);
			return;
		}
		hover = null;
		if (!daftarTampil) {
			drawMap(g, mouseX, mouseY);
		}
	}

	private void drawMap(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int bpp = bpp();
		PetaPainter.frame(viewX, viewZ, bpp);
		g.fill(mx0 - 1, my0 - 1, mx1 + 1, my1 + 1, Palette.argb(0xFF, 0x3A2A1A));
		g.fill(mx0, my0, mx1, my1, 0xFFDCCFA8);
		g.enableScissor(mx0, my0, mx1, my1);
		if (PetaPainter.ready()) {
			float f = PetaPainter.shownBpp() / (float) bpp;
			float sx = screenX(PetaPainter.shownX());
			float sy = screenY(PetaPainter.shownZ());
			g.pose().pushMatrix();
			g.pose().translate(sx, sy);
			// a picture scale (the painted texture), not text
			g.pose().scale(f, f);
			g.blit(RenderPipelines.GUI_TEXTURED, PetaPainter.TEXTURE, 0, 0, 0.0F, 0.0F, PetaPainter.SIZE, PetaPainter.SIZE, PetaPainter.SIZE,
					PetaPainter.SIZE);
			g.pose().popMatrix();
		} else {
			Brush.fitCentered(g, font, Component.translatable("kultivator.peta.painting"), (mx0 + mx1) / 2.0F, (my0 + my1) / 2.0F - 4.0F, mx1 - mx0 - 8,
					Palette.INK_SOFT, 1.0F, 1.0F, false);
		}
		// the painted paper below everything drawn on it
		g.nextStratum();
		CompoundTag sheet = PetaCache.sheet();
		drawDomains(g, sheet);
		drawTerritories(g, sheet);
		if (overlayWeather) {
			drawWeather(g, sheet);
		}
		if (overlayRoute) {
			drawRoute(g, sheet);
		}
		drawMarks(g, mouseX, mouseY);
		drawPlayer(g);
		g.disableScissor();
		// frame, compass, scale bar, sky banner
		Brush.frame(g, mx0 - 1, my0 - 1, mx1 + 1, my1 + 1, Palette.GOLD_DARK);
		int top = my0 + (modeBerlayar() != null ? BANNER_H : 0);
		int cs = Math.min(40, Math.max(20, (mx1 - mx0) / 8));
		g.blit(RenderPipelines.GUI_TEXTURED, COMPASS, mx1 - cs - 4, top + 4, 0.0F, 0.0F, cs, cs, 64, 64, 64, 64, 0xD8FFFFFF);
		drawScale(g);
		if (overlaySky && sheet.contains("sky")) {
			Component sky = Component.translatable("kultivator.peta.sky", Component.translatable("kultivator.peta.sky."
					+ sheet.getStringOr("sky", "").toLowerCase(Locale.ROOT)));
			int bw = Math.min(mx1 - mx0 - cs - 16, font.width(sky) + 22);
			int bx = Math.max(mx0 + 4, (mx0 + mx1 - bw) / 2 - cs / 2);
			g.fill(bx, top + 3, bx + bw, top + 16, Palette.argb(0xC0, 0x1A1410));
			icon(g, 29, bx + 2, top + 3, 12, 0xFFFFFFFF);
			Brush.fit(g, font, sky, bx + 16, top + 5, bw - 18, 0xFFFFE070, 1.0F, 1.0F, false);
		}
	}

	private void drawScale(GuiGraphicsExtractor g) {
		int bpp = bpp();
		int[] options = {256, 512, 1000, 2000, 5000, 10000, 20000};
		int pick = options[0];
		for (int option : options) {
			if (option / (float) bpp <= (mx1 - mx0) / 4.0F) {
				pick = option;
			}
		}
		int len = Math.round(pick / (float) bpp);
		int sx = mx0 + 6;
		int sy = my1 - 6;
		g.fill(sx, sy, sx + len, sy + 2, 0xE01E1E1B);
		g.fill(sx, sy - 3, sx + 1, sy + 2, 0xE01E1E1B);
		g.fill(sx + len - 1, sy - 3, sx + len, sy + 2, 0xE01E1E1B);
		g.fill(sx + len / 2, sy - 2, sx + len / 2 + 1, sy + 2, 0xA01E1E1B);
		Brush.text(g, font, Component.translatable("kultivator.peta.scale", pick), sx, sy - 11, 0xFF1E1E1B, 1.0F, false);
	}

	private void drawDomains(GuiGraphicsExtractor g, CompoundTag sheet) {
		var registries = minecraft.level == null ? null : minecraft.level.registryAccess();
		for (Tag tag : sheet.getListOrEmpty("domains")) {
			if (!(tag instanceof CompoundTag d)) {
				continue;
			}
			float cx = screenX(d.getIntOr("x", 0));
			float cy = screenY(d.getIntOr("z", 0));
			float r = d.getIntOr("r", 600) / (float) bpp();
			if (cx + r < mx0 || cx - r > mx1 || cy + r < my0 || cy - r > my1) {
				continue;
			}
			int color = 0xFFFFFF & d.getIntOr("color", 0x808080);
			if (r >= 3.0F) {
				Brush.disc(g, cx, cy, r, Palette.argb(0x18, color));
			}
			// a dashed inked border
			int dashes = Math.max(12, Math.min(96, Math.round(r * 0.9F)));
			for (int i = 0; i < dashes; i += 2) {
				double a0 = i * Math.PI * 2.0D / dashes;
				double a1 = (i + 1) * Math.PI * 2.0D / dashes;
				Brush.line(g, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, cx + (float) Math.cos(a1) * r,
						cy + (float) Math.sin(a1) * r, 1.2F, Palette.argb(0xB0, Palette.darken(0xFF000000 | color, 0.45F)));
			}
			if (r >= 8.0F) {
				String seal = d.getStringOr("seal", "?");
				if (seal.startsWith("#")) {
					seal = Language.getInstance().getOrDefault(seal.substring(1), "?");
				}
				g.fill(Math.round(cx - 6), Math.round(cy - r + 2), Math.round(cx + 6), Math.round(cy - r + 14), Palette.argb(0xE0, Palette.SEAL));
				Brush.centered(g, font, Component.literal(seal), cx, cy - r + 4, 0xFFF4E0D0, 1.0F, false);
			}
			if (r >= 24.0F) {
				// the domain's name at 1.0 only where it has room (43.0.5); the seal alone below that
				Component name = Component.literal(d.getStringOr("id", "?"));
				if (registries != null) {
					try {
						name = d.read("name", ComponentSerialization.CODEC, registries.createSerializationContext(NbtOps.INSTANCE)).orElse(name);
					} catch (RuntimeException ignored) {
						// keep the id
					}
				}
				Brush.fitCentered(g, font, name, cx, cy - r + 16, r * 1.6F, Palette.argb(0xF0, Palette.darken(0xFF000000 | color, 0.55F)), 1.0F, 1.0F,
						false);
			}
		}
	}

	private void drawTerritories(GuiGraphicsExtractor g, CompoundTag sheet) {
		if (HIDDEN.contains(Category.SEKTE)) {
			return;
		}
		for (Tag tag : sheet.getListOrEmpty("halls")) {
			if (!(tag instanceof CompoundTag h)) {
				continue;
			}
			float cx = screenX(h.getIntOr("x", 0));
			float cy = screenY(h.getIntOr("z", 0));
			float r = Math.max(2.0F, h.getIntOr("r", 64) / (float) bpp());
			int color = 0xFFFFFF & h.getIntOr("color", 0xFFD27A);
			boolean own = h.getBooleanOr("own", false);
			Brush.disc(g, cx, cy, r, Palette.argb(own ? 0x40 : 0x2C, color));
			Brush.ring(g, cx, cy, r - 0.8F, r + 0.4F, Palette.argb(0xC8, Palette.darken(0xFF000000 | color, 0.25F)));
		}
	}

	private void drawWeather(GuiGraphicsExtractor g, CompoundTag sheet) {
		int step = Math.max(16, sheet.getIntOr("weather_step", 128));
		float r = step * 0.62F / bpp();
		for (Tag tag : sheet.getListOrEmpty("weather")) {
			if (!(tag instanceof CompoundTag w)) {
				continue;
			}
			int kind = w.getIntOr("w", 0);
			int color = switch (kind) {
				case 1 -> 0x48D8E8F0;
				case 2 -> 0x70EEF6FF;
				case 3 -> 0x58C0A0F0;
				default -> 0;
			};
			if (color != 0) {
				Brush.disc(g, screenX(w.getIntOr("x", 0)), screenY(w.getIntOr("z", 0)), Math.max(1.5F, r), color);
			}
		}
	}

	private void drawRoute(GuiGraphicsExtractor g, CompoundTag sheet) {
		CompoundTag ship = sheet.getCompoundOrEmpty("ship");
		String dest = ship.getStringOr("dest", "");
		if (dest.isEmpty()) {
			return;
		}
		String[] parts = dest.split(" ", 3);
		try {
			double tx = Double.parseDouble(parts[0]);
			double tz = Double.parseDouble(parts[1]);
			float ax = screenX(ship.getIntOr("x", 0));
			float ay = screenY(ship.getIntOr("z", 0));
			float bx = screenX(tx);
			float by = screenY(tz);
			float len = (float) Math.hypot(bx - ax, by - ay);
			int dashes = Math.max(2, Math.min(200, Math.round(len / 5.0F)));
			float phase = (Util.getMillis() % 1000L) / 1000.0F;
			for (int i = 0; i < dashes; i++) {
				float t0 = (i + phase) / dashes;
				float t1 = Math.min(1.0F, (i + phase + 0.5F) / dashes);
				Brush.line(g, Mth.lerp(t0, ax, bx), Mth.lerp(t0, ay, by), Mth.lerp(t1, ax, bx), Mth.lerp(t1, ay, by), 1.4F, 0xD0B3281D);
			}
			icon(g, 30, Math.round(bx - 6), Math.round(by - 11), 12, 0xFFFFFFFF);
		} catch (RuntimeException ignored) {
			// a broken destination
		}
	}

	private boolean shown(Mark mark) {
		return !HIDDEN.contains(mark.category) && (markerZoom() >= mark.minZoom || mark.live || mark.waypoint >= 0);
	}

	private void drawMarks(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		float best = 49.0F;
		for (Mark mark : marks) {
			if (!shown(mark)) {
				continue;
			}
			float sx = screenX(mark.x);
			float sy = screenY(mark.z);
			if (sx < mx0 - 8 || sx > mx1 + 8 || sy < my0 - 8 || sy > my1 + 8) {
				continue;
			}
			int size = mark.category == Category.SEKTE && mark.icon == 21 ? 10 : 13;
			if (mark.live) {
				// a rainbow ring pulses round a live omen
				float pulse = 0.5F + 0.5F * Mth.sin((Util.getMillis() % 100000L) / 180.0F);
				int ring = mark.kind.startsWith("langit/harta") ? rainbow(Util.getMillis()) : 0xFF000000 | mark.color;
				Brush.ring(g, sx, sy, 6.5F + pulse * 2.0F, 7.6F + pulse * 2.0F, Palette.fade(ring, 0.85F));
			}
			if (overlaySeals && mark.seal >= 0) {
				// the seal's strength as the ring colour; the number is in the tooltip and the card (text at 1.0 only)
				Brush.ring(g, sx, sy, 7.0F, 8.4F, Palette.mix(0xFFD5322A, 0xFF2E8963, mark.seal / 100.0F));
			}
			int tint = mark.waypoint >= 0 ? 0xFF000000 | mark.color : 0xFFFFFFFF;
			icon(g, mark.icon, Math.round(sx - size / 2.0F), Math.round(sy - size / 2.0F), size, tint);
			float d = (sx - mouseX) * (sx - mouseX) + (sy - mouseY) * (sy - mouseY);
			if (d < best && overMap(mouseX, mouseY)) {
				best = d;
				hover = mark;
			}
			if (mark == selected) {
				Brush.ring(g, sx, sy, 8.5F, 9.6F, Palette.GOLD);
			}
		}
	}

	private static int rainbow(long millis) {
		float h = (millis % 3000L) / 3000.0F * 6.0F;
		int sector = (int) Math.floor(h);
		float f = h - sector;
		float s = 0.6F;
		float p = 1.0F - s;
		float q = 1.0F - s * f;
		float t = 1.0F - s * (1.0F - f);
		float r;
		float g;
		float b;
		switch (sector % 6) {
			case 0 -> {
				r = 1.0F;
				g = t;
				b = p;
			}
			case 1 -> {
				r = q;
				g = 1.0F;
				b = p;
			}
			case 2 -> {
				r = p;
				g = 1.0F;
				b = t;
			}
			case 3 -> {
				r = p;
				g = q;
				b = 1.0F;
			}
			case 4 -> {
				r = t;
				g = p;
				b = 1.0F;
			}
			default -> {
				r = 1.0F;
				g = p;
				b = q;
			}
		}
		return 0xFF000000 | Math.round(r * 255.0F) << 16 | Math.round(g * 255.0F) << 8 | Math.round(b * 255.0F);
	}

	private void drawPlayer(GuiGraphicsExtractor g) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		float sx = screenX(player.getX());
		float sy = screenY(player.getZ());
		double yaw = Math.toRadians(player.getYRot());
		float fx = (float) -Math.sin(yaw);
		float fz = (float) Math.cos(yaw);
		float rx = -fz;
		float rz = fx;
		float tipX = sx + fx * 7.0F;
		float tipY = sy + fz * 7.0F;
		float lx = sx - fx * 4.0F + rx * 4.0F;
		float ly = sy - fz * 4.0F + rz * 4.0F;
		float qx = sx - fx * 4.0F - rx * 4.0F;
		float qy = sy - fz * 4.0F - rz * 4.0F;
		Brush.dot(g, sx, sy, 4.0F, 0x60FFFFFF);
		Brush.line(g, tipX, tipY, lx, ly, 2.2F, 0xFF1E1E1B);
		Brush.line(g, tipX, tipY, qx, qy, 2.2F, 0xFF1E1E1B);
		Brush.line(g, lx, ly, sx - fx * 1.5F, sy - fz * 1.5F, 2.0F, 0xFF1E1E1B);
		Brush.line(g, qx, qy, sx - fx * 1.5F, sy - fz * 1.5F, 2.0F, 0xFF1E1E1B);
		Brush.line(g, tipX, tipY, sx - fx * 1.5F, sy - fz * 1.5F, 1.4F, Palette.SEAL);
		Brush.dot(g, sx, sy, 1.3F, Palette.GOLD_LIGHT);
	}

	private void icon(GuiGraphicsExtractor g, int index, int ix, int iy, int size, int tint) {
		int i = Mth.clamp(index, 0, 63);
		Identifier atlas = i >= DuniaMarkers.ATLAS2_FIRST ? MARKERS_TEMPAT : MARKERS;
		int c = i % 32;
		g.blit(RenderPipelines.GUI_TEXTURED, atlas, ix, iy, (c % 8) * 16.0F, (c / 8) * 16.0F, size, size, 16, 16, 128, 64, tint);
	}

	// ================================================================== drawing: controls (immediate)

	@Override
	protected void lukis(Lukis l) {
		if (subKota()) {
			return;
		}
		HelmScreen helm = modeBerlayar();
		if (daftarTampil) {
			sisi(l, helm, x, y, x + w, y + h - BAR_H - 3);
			return;
		}
		kepala(l);
		// the map itself: Tab focuses it, then arrows pan, +/- zoom, Home centres
		l.zona("peta", mx0, my0, mx1, my1, null, true, null, true);
		l.fokusCincin("peta", mx0, my0, mx1, my1);
		if (helm != null) {
			spanduk(l, helm);
		}
		if (hover != null && selected == null) {
			l.tip(hoverLines(hover));
		}
		if (selected != null) {
			kartu(l, selected, helm);
		}
		if (side) {
			sisi(l, helm, mx1 + 6, y, x + w, my1);
		}
	}

	/** The strip above the map: dimension, sites and explored count; the cursor's coordinates at the right. */
	private void kepala(Lukis l) {
		String dim = PetaCache.dim();
		String path = dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
		Component dimName = Component.translatableWithFallback("kultivator.peta.dim." + path, pretty("x/" + path));
		CompoundTag sheet = PetaCache.sheet();
		int right = mx1;
		if (overMap(l.mx, l.my)) {
			Component at = Component.translatable("kultivator.peta.cursor", (int) Math.floor(worldX(l.mx)), (int) Math.floor(worldZ(l.my)));
			l.kanan(at, mx1, y, l.gaya.teksLemah);
			right = mx1 - l.font.width(at) - 6;
		}
		l.samar(Component.translatable("kultivator.peta.header", dimName, sheet.getIntOr("known", 0), sheet.getIntOr("explored", 0)), x, y, right - x);
	}

	/** Sail mode banner over the map's top: what to do, and back to the helm. */
	private void spanduk(Lukis l, HelmScreen helm) {
		int by0 = my0;
		int by1 = my0 + BANNER_H;
		l.g.fill(mx0, by0, mx1, by1, Palette.argb(0xE0, 0x1A1410));
		l.g.fill(mx0, by1 - 1, mx1, by1, Palette.argb(0xC0, Palette.GOLD));
		Component back = Component.translatable(M + "berlayar.kembali");
		int bw = TalismanButton.lebar(l.font, back, 50, 90);
		TalismanButton.gambar(l, "berlayar_kembali", mx1 - bw - 2, by0 + 1, bw, BANNER_H - 2, back, true, null, this::kembaliKeKemudi);
		Component judul = Component.translatable(helm.kapten() ? M + "berlayar.judul" : "kultivator.kapal.helm.voyage.not_captain");
		l.teks(judul, mx0 + 5, by0 + 4, mx1 - mx0 - bw - 12, helm.kapten() ? 0xFFFFE070 : 0xFFFF8A7A);
	}

	private List<Component> hoverLines(Mark mark) {
		long time = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
		List<Component> lines = new ArrayList<>();
		lines.add(Brush.bold(mark.name));
		lines.add(Component.translatable("kultivator.peta.card.where", distanceText(mark)).withStyle(ChatFormatting.GRAY));
		if (mark.seal >= 0) {
			lines.add(Component.translatable(mark.broken ? "kultivator.peta.card.seal_broken" : "kultivator.peta.card.seal", mark.seal)
					.withStyle(mark.broken ? ChatFormatting.RED : ChatFormatting.DARK_GREEN));
		}
		if (mark.live && mark.ends > time) {
			lines.add(Component.translatable("kultivator.peta.card.ends", PetaUi.duration((mark.ends - time) / 20L)).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		lines.add(Component.translatable("kultivator.peta.card.click").withStyle(ChatFormatting.DARK_GREEN));
		return lines;
	}

	// ================================================================== the marker card

	private void openCard(Mark mark) {
		selected = mark;
		UiSound.press();
	}

	/** Centres the map on {@code mark} and opens its card (from the list). */
	private void lihat(Mark mark) {
		viewX = mark.x;
		viewZ = mark.z;
		openCard(mark);
	}

	private Component distanceText(Mark mark) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return Component.empty();
		}
		double dx = mark.x + 0.5D - player.getX();
		double dz = mark.z + 0.5D - player.getZ();
		int d = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		return Component.translatable("kultivator.peta.card.distance", d, Component.translatable("kultivator.peta.dir." + PetaItems.direction(dx, dz)));
	}

	private List<Component> cardLines(Mark mark) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.peta.category." + mark.category.name().toLowerCase(Locale.ROOT)).withColor(0xFF6E5A3A));
		if (mark.tier > 0) {
			lines.add(Component.translatable(mark.category == Category.SEKTE ? "kultivator.peta.card.level" : "kultivator.peta.card.tier", mark.tier)
					.withColor(0xFF465950));
		}
		lines.add(distanceText(mark).copy().withColor(0xFF1B2521));
		lines.add(Component.translatable("kultivator.peta.card.coords", mark.x, mark.z).withColor(0xFF7A8C83));
		if (!mark.guard.isEmpty()) {
			lines.add(Component.translatable("kultivator.peta.card.guard", guardName(mark.guard)).withColor(0xFF8A671B));
		}
		if (mark.seal >= 0) {
			lines.add(Component.translatable(mark.broken ? "kultivator.peta.card.seal_broken" : "kultivator.peta.card.seal", mark.seal)
					.withColor(mark.broken ? 0xFFB3281D : 0xFF2E8963));
		}
		long time = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
		if (mark.live && mark.ends > time) {
			lines.add(Component.translatable("kultivator.peta.card.ends", PetaUi.duration((mark.ends - time) / 20L)).withColor(0xFF8E5BD8));
		}
		String base = "kultivator." + mark.kind.replace('/', '.');
		Language language = Language.getInstance();
		if (language.has(base + ".source")) {
			lines.add(Component.translatable(base + ".source").withColor(0xFF7A8C83));
		} else if (language.has(base + ".inspired")) {
			lines.add(Component.translatable(base + ".inspired").withStyle(ChatFormatting.ITALIC).withColor(0xFF7A8C83));
		}
		return lines;
	}

	private Component guardName(String id) {
		var registries = minecraft.level == null ? null : minecraft.level.registryAccess();
		for (Tag tag : PetaCache.sheet().getListOrEmpty("domains")) {
			if (tag instanceof CompoundTag d && id.equals(d.getStringOr("id", "")) && registries != null) {
				try {
					return d.read("name", ComponentSerialization.CODEC, registries.createSerializationContext(NbtOps.INSTANCE)).orElse(Component.literal(id));
				} catch (RuntimeException ignored) {
					return Component.literal(id);
				}
			}
		}
		return Component.literal(id);
	}

	/** The marker card beside its marker, kept inside the map; its text never leaves the card (cut with "…"). */
	private void kartu(Lukis l, Mark mark, @Nullable HelmScreen helm) {
		List<Component> lines = cardLines(mark);
		int mapW = mx1 - mx0;
		int mapH = my1 - my0 - (helm != null ? BANNER_H : 0);
		cardW = Math.max(100, Math.min(176, mapW - 8));
		int textW = cardW - 12;
		float want = 0.0F;
		for (Component line : lines) {
			want += Brush.wrapHeight(font, line, textW, 1.0F) + 1.0F;
		}
		cardH = Math.min(mapH - 4, Math.round(want) + 20 + 20);
		float sx = screenX(mark.x);
		float sy = screenY(mark.z);
		int top = my0 + (helm != null ? BANNER_H : 0) + 2;
		cardX = Mth.clamp(Math.round(sx + 12), mx0 + 2, mx1 - cardW - 2);
		cardY = Mth.clamp(Math.round(sy - cardH / 2.0F), top, my1 - cardH - 2);
		if (cardX < sx && sx < cardX + cardW) {
			cardX = Mth.clamp(Math.round(sx - 12 - cardW), mx0 + 2, mx1 - cardW - 2);
		}
		PetaUi.panel(l.g, cardX, cardY, cardX + cardW, cardY + cardH);
		// the card swallows clicks on its paper (the map under it does not pan)
		l.zona("kartu", cardX, cardY, cardX + cardW, cardY + cardH, null, true, null, false);
		icon(l.g, mark.icon, cardX + 5, cardY + 4, 13, mark.waypoint >= 0 ? 0xFF000000 | mark.color : 0xFFFFFFFF);
		l.teks(Brush.bold(mark.name), cardX + 21, cardY + 7, cardW - 26, Palette.SEAL_DARK);
		float ty = cardY + 20;
		float room = cardY + cardH - 20 - ty;
		for (Component line : lines) {
			if (room < 9.0F) {
				break;
			}
			float used = l.bungkus(line, cardX + 6, ty, textW, 0xFF1B2521, room);
			ty += used + 1.0F;
			room -= used + 1.0F;
		}
		boolean captain = PetaCache.sheet().getBooleanOr("captain", false) || helm != null && helm.kapten();
		int n = 3;
		int bw = (cardW - 12 - 4 * (n - 1)) / n;
		int bx = cardX + 6;
		int by = cardY + cardH - 17;
		TalismanButton.gambar(l, "kartu_layar", bx, by, bw, 14, Component.translatable("kultivator.peta.card.sail"), captain,
				Component.translatable("kultivator.peta.sail.not_captain"), () -> layarKe(mark), helm != null && captain, false);
		if (captain && l.di(bx, by, bx + bw, by + 14)) {
			l.tip(Component.translatable("kultivator.peta.card.sail.tip"));
		}
		bx += bw + 4;
		if (mark.waypoint >= 0) {
			TalismanButton.gambar(l, "kartu_tanda", bx, by, bw, 14, Component.translatable("kultivator.peta.card.delete"), true, null, () -> {
				PetaClient.request(PetaNet.OP_WAYPOINT_DELETE, mark.waypoint, 0, "", new long[0]);
				selected = null;
			});
		} else {
			TalismanButton.gambar(l, "kartu_tanda", bx, by, bw, 14, Component.translatable("kultivator.peta.card.mark"), true, null, () -> {
				PetaClient.request(PetaNet.OP_WAYPOINT_ADD, mark.x, mark.z, mark.name.getString(), new long[0]);
				selected = null;
			});
		}
		bx += bw + 4;
		TalismanButton.gambar(l, "kartu_tutup", bx, by, bw, 14, Component.translatable("kultivator.peta.card.close"), true, null, () -> selected = null);
	}

	/** "Berlayar": the ship's autopilot to the marker (the server checks the captain); in sail mode back to the helm. */
	private void layarKe(Mark mark) {
		PetaClient.request(PetaNet.OP_SAIL, mark.x, mark.z, mark.name.getString(), new long[0]);
		if (modeBerlayar() != null) {
			kembaliKeKemudi();
		} else {
			selected = null;
			pesan(Component.translatable(M + "berlayar_ke", mark.name), Palette.JADE_DARK);
		}
	}

	// ================================================================== the side list (or the whole box without side panel)

	/** Rows of the list: in sail mode a heading, the hall and the routes, then a heading; then the listed sites. */
	private int barisRute(@Nullable HelmScreen helm) {
		if (helm == null) {
			return 0;
		}
		return 2 + (helm.info().contains("hall_x") ? 1 : 0) + helm.info().getListOrEmpty("routes").size();
	}

	private boolean judulBaris(int i) {
		int extra = barisRute(modeBerlayar());
		return extra > 0 && (i == 0 || i == extra - 1);
	}

	private void sisi(Lukis l, @Nullable HelmScreen helm, int sx0, int sy0, int sx1, int sy1) {
		int y0 = sy0;
		cari.x = sx0;
		cari.y = y0;
		cari.w = sx1 - sx0;
		cari.h = 14;
		l.widget("cari", cari);
		y0 += 17;
		// category coins: show / hide each kind of marker
		int fx = sx0;
		for (Category category : Category.values()) {
			if (fx + 18 > sx1) {
				fx = sx0;
				y0 += 16;
			}
			boolean on = !HIDDEN.contains(category);
			String kunci = "kategori:" + category.name();
			boolean over = l.di(fx, y0, fx + 18, y0 + 14);
			l.g.fill(fx, y0, fx + 18, y0 + 14, on ? (over ? 0xFFF6EBC4 : Palette.TALISMAN_LOW) : Palette.LOCKED_PAPER);
			Brush.frame(l.g, fx, y0, fx + 18, y0 + 14, on ? Palette.GOLD_DARK : Palette.argb(0x90, Palette.LOCKED));
			icon(l.g, category.icon, fx + 3, y0 + 1, 12, on ? 0xFFFFFFFF : 0x70FFFFFF);
			l.zona(kunci, fx, y0, fx + 18, y0 + 14, () -> {
				if (!HIDDEN.remove(category)) {
					HIDDEN.add(category);
				}
				UiSound.toggle(HIDDEN.contains(category));
				rebuildList();
			}, true, null, true);
			l.fokusCincin(kunci, fx, y0, fx + 18, y0 + 14);
			if (over) {
				l.tip(Component.translatable("kultivator.peta.category." + category.name().toLowerCase(Locale.ROOT)));
			}
			fx += 20;
		}
		y0 += 17;
		int extra = barisRute(helm);
		if (extra == 0) {
			l.teks(Brush.bold(Component.translatable("kultivator.peta.list", listed.size())), sx0 + 2, y0, sx1 - sx0 - 4, Palette.SEAL_DARK);
			y0 += 11;
		}
		if (listed.isEmpty() && extra == 0) {
			l.bungkus(Component.translatable(marks.isEmpty() ? "kultivator.peta.list.none" : "kultivator.peta.list.filtered"), sx0 + 2, y0 + 2,
					sx1 - sx0 - 4, l.gaya.teksSamar, sy1 - y0 - 4);
			return;
		}
		List<CompoundTag> routes = new ArrayList<>();
		if (helm != null) {
			for (Tag tag : helm.info().getListOrEmpty("routes")) {
				routes.add(tag instanceof CompoundTag r ? r : new CompoundTag());
			}
		}
		boolean hall = helm != null && helm.info().contains("hall_x");
		LocalPlayer player = minecraft.player;
		daftar.gambar(l, "situs", sx0, y0, sx1, sy1, extra + listed.size(), ROW_H, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			if (i < extra) {
				baris(lk, helm, hall, routes, i, extra, rx0, ry0, rx1, sorot, pilih);
				return;
			}
			Mark mark = listed.get(i - extra);
			if (sorot || pilih || mark == selected) {
				lk.g.fill(rx0, ry0, rx1, ry1, pilih || mark == selected ? 0x50D4A637 : 0x30FFFFFF);
			}
			icon(lk.g, mark.icon, rx0 + 1, ry0 + 1, 11, mark.waypoint >= 0 ? 0xFF000000 | mark.color : 0xFFFFFFFF);
			int nameRight = rx1 - 3;
			if (player != null) {
				int d = (int) Math.round(Math.sqrt(dist2(player, mark)));
				Component dt = Component.literal(d >= 10000 ? (d / 1000) + "k" : String.valueOf(d));
				lk.kanan(dt, rx1 - 2, ry0 + 2, lk.gaya.teksSamar);
				nameRight = rx1 - lk.font.width(dt) - 6;
			}
			lk.teks(mark.name, rx0 + 14, ry0 + 2, nameRight - rx0 - 14, lk.gaya.teks);
		}, () -> pakai(helm, hall, routes, extra), i -> {
			if (side && i >= extra && i - extra < listed.size()) {
				lihat(listed.get(i - extra));
			}
		});
	}

	/** A sail-mode row: headings, the own sect hall, a route. */
	private void baris(Lukis l, @Nullable HelmScreen helm, boolean hall, List<CompoundTag> routes, int i, int extra, int rx0, int ry0, int rx1,
			boolean sorot, boolean pilih) {
		if (i == 0 || i == extra - 1) {
			l.kepala(i == 0 ? Component.translatable(M + "berlayar.rute") : Component.translatable("kultivator.peta.list", listed.size()), rx0, ry0, rx1);
			return;
		}
		boolean captain = helm != null && helm.kapten();
		Component label;
		boolean allowed;
		Component reason;
		if (hall && i == 1) {
			label = Component.translatable("kultivator.kapal.helm.dest.hall", helm.info().getStringOr("hall_name", ""));
			allowed = captain;
			reason = Component.translatable("kultivator.kapal.helm.voyage.not_captain");
		} else {
			CompoundTag route = routes.get(i - 1 - (hall ? 1 : 0));
			Component name = HelmScreen.namaRute(route);
			label = Component.translatable("kultivator.kapal.helm.dest.route", name);
			allowed = captain && route.getBooleanOr("allowed", false);
			reason = !captain ? Component.translatable("kultivator.kapal.helm.voyage.not_captain") : Component.translatable("kultivator.kapal.route_closed", name);
		}
		if (sorot || pilih) {
			l.g.fill(rx0, ry0, rx1, ry0 + ROW_H - 1, pilih ? 0x50D4A637 : 0x30FFFFFF);
		}
		icon(l.g, 17, rx0 + 1, ry0 + 1, 11, allowed ? 0xFFFFFFFF : 0x70FFFFFF);
		l.teks(label, rx0 + 14, ry0 + 2, rx1 - rx0 - 16, allowed ? l.gaya.teks : l.gaya.teksSamar);
		if (sorot) {
			l.tip(List.of(Brush.bold(label), (allowed ? Component.translatable(M + "berlayar.ulang") : reason).copy().withStyle(ChatFormatting.GRAY)));
		}
	}

	/** Enter / a second click on the selected row: a site opens on the map; a voyage row sails (sail mode). */
	private void pakai(@Nullable HelmScreen helm, boolean hall, List<CompoundTag> routes, int extra) {
		int i = daftar.pilihan();
		if (i >= extra) {
			if (i - extra < listed.size()) {
				daftarTampil = false;
				lihat(listed.get(i - extra));
			}
			return;
		}
		if (helm == null || i <= 0 || i >= extra - 1) {
			return;
		}
		if (!helm.kapten()) {
			fokusRing.tolak(Component.translatable("kultivator.kapal.helm.voyage.not_captain"));
			return;
		}
		if (hall && i == 1) {
			helm.perintah(KapalRohEntity.OP_HALL, 0, "");
		} else {
			CompoundTag route = routes.get(i - 1 - (hall ? 1 : 0));
			if (!route.getBooleanOr("allowed", false)) {
				fokusRing.tolak(Component.translatable("kultivator.kapal.route_closed", HelmScreen.namaRute(route)));
				return;
			}
			helm.perintah(KapalRohEntity.OP_ROUTE, 0, route.getStringOr("id", ""));
		}
		UiSound.press();
		kembaliKeKemudi();
	}

	// ================================================================== input

	/** True over the map's overlays that take their own clicks (the card, the sail banner). */
	private boolean diAtasPeta(double mx, double my) {
		if (selected != null && mx >= cardX && mx < cardX + cardW && my >= cardY && my < cardY + cardH) {
			return true;
		}
		return modeBerlayar() != null && my < my0 + BANNER_H;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (subKota()) {
			return kota.mouseClicked(event, doubleClick);
		}
		double mx = event.x();
		double my = event.y();
		if (!modal() && overMap(mx, my) && !diAtasPeta(mx, my)) {
			fokusRing.set("peta");
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				dragging = true;
				dragX = mx;
				dragY = my;
				pressX = mx;
				pressY = my;
				return true;
			}
			if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
				Mark near = markAt(mx, my);
				if (near != null) {
					openCard(near);
				} else if (PetaCache.sheet().getIntOr("max_waypoints", 32) > 0) {
					UiSound.toggle(true);
					PetaClient.request(PetaNet.OP_WAYPOINT_ADD, (int) Math.floor(worldX(mx)), (int) Math.floor(worldZ(my)), "", new long[0]);
				} else {
					UiSound.denied();
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private @Nullable Mark markAt(double mx, double my) {
		Mark best = null;
		double bestD = 49.0D;
		for (Mark mark : marks) {
			if (!shown(mark)) {
				continue;
			}
			double dx = screenX(mark.x) - mx;
			double dy = screenY(mark.z) - my;
			double d = dx * dx + dy * dy;
			if (d < bestD) {
				bestD = d;
				best = mark;
			}
		}
		return best;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (subKota()) {
			return kota.mouseDragged(event, dx, dy);
		}
		if (dragging) {
			viewX -= (event.x() - dragX) * bpp();
			viewZ -= (event.y() - dragY) * bpp();
			dragX = event.x();
			dragY = event.y();
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (subKota()) {
			return kota.mouseReleased(event);
		}
		if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			dragging = false;
			if (Math.abs(event.x() - pressX) < 3.0D && Math.abs(event.y() - pressY) < 3.0D) {
				Mark near = markAt(event.x(), event.y());
				if (near != null) {
					openCard(near);
				} else {
					selected = null;
				}
			}
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (subKota()) {
			return kota.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		if (overMap(mouseX, mouseY) && scrollY != 0.0D) {
			setZoom(zoomIndex + (scrollY > 0.0D ? -1 : 1), mouseX, mouseY);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (subKota()) {
			return kota.keyPressed(event);
		}
		if (mauTombol()) {
			return super.keyPressed(event);
		}
		int key = event.key();
		if (!daftarTampil && (key == InputConstants.KEY_EQUALS || key == InputConstants.KEY_ADD || key == InputConstants.KEY_MINUS)) {
			boolean in = key == InputConstants.KEY_EQUALS || key == InputConstants.KEY_ADD;
			setZoom(zoomIndex + (in ? -1 : 1), (mx0 + mx1) / 2.0D, (my0 + my1) / 2.0D);
			return true;
		}
		if (!daftarTampil && "peta".equals(fokusRing.kunci())) {
			double step = 24.0D * bpp();
			switch (key) {
				case InputConstants.KEY_LEFT -> viewX -= step;
				case InputConstants.KEY_RIGHT -> viewX += step;
				case InputConstants.KEY_UP -> viewZ -= step;
				case InputConstants.KEY_DOWN -> viewZ += step;
				case InputConstants.KEY_HOME -> centre();
				case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE -> {
					// Enter on the focused map: the card of the marker nearest its centre
					Mark near = markAt((mx0 + mx1) / 2.0D, (my0 + my1) / 2.0D);
					if (near == null) {
						return super.keyPressed(event);
					}
					openCard(near);
				}
				default -> {
					return super.keyPressed(event);
				}
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		return subKota() ? kota.keyReleased(event) : super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return subKota() ? kota.charTyped(event) : super.charTyped(event);
	}

	@Override
	public boolean mauTombol() {
		return subKota() ? kota.mauTombol() : super.mauTombol();
	}

	@Override
	public boolean modal() {
		return subKota() ? kota.modal() : super.modal();
	}

	@Override
	public boolean fokusTab(boolean back) {
		return subKota() ? kota.fokusTab(back) : super.fokusTab(back);
	}

	/** Esc: the card, then the list view, then sail mode (back to the helm); else the Gulungan's own step back. */
	@Override
	public boolean mundur() {
		if (subKota()) {
			return kota.mundur();
		}
		if (selected != null) {
			selected = null;
			return true;
		}
		if (daftarTampil) {
			daftarTampil = false;
			return true;
		}
		if (modeBerlayar() != null) {
			kembaliKeKemudi();
			return true;
		}
		return false;
	}
}
