package dev.kultivator.client.dunia.peta;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.dunia.DuniaMarkers;
import dev.kultivator.dunia.peta.PetaItems;
import dev.kultivator.dunia.peta.PetaNet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
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
 * Peta Dunia Kultivasi, the world map screen (DESIGN 34.5.16): an ink-wash parchment of the explored world
 * ({@link PetaPainter}), the known sites with their marker icons (DESIGN 34.4.8; undiscovered sites never show), live
 * omens, waypoints, the own ship and its voyage line, the return point and a soul's body, sect territories with their
 * vassal villages, the NPC sect domains with inked borders and seals, and the overlay coins (Cuaca Roh, Peristiwa
 * Langit, Segel, Jalur Kapal). Drag to pan, wheel for the three zooms (1 px = 16 / 64 / 256 b), click a marker for its
 * card ("Berlayar ke sini" for a captain, "Tandai"), right-click the paper to set a waypoint; a search box and a list of
 * known sites on the side. Markers and overlays are GUI fills; one texture holds the painted paper. Client only.
 */
public final class PetaScreen extends Screen {

	private static final int[] ZOOMS = {16, 64, 256};
	private static final Identifier MARKERS = dev.kultivator.Kultivator.id("textures/dunia/peta/markers.png");
	private static final Identifier COMPASS = dev.kultivator.Kultivator.id("textures/dunia/peta/compass.png");
	private static final int SIDE_W = 108;

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

	private final List<Mark> marks = new ArrayList<>();
	private final List<Mark> listed = new ArrayList<>();
	private final List<PetaUi.Button> buttons = new ArrayList<>();
	private final List<PetaUi.Button> cardButtons = new ArrayList<>();
	private int seenSheet = -1;
	private String search = "";
	private @Nullable EditBox searchBox;
	private @Nullable Mark selected;
	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private int mx0;
	private int my0;
	private int mx1;
	private int my1;
	private boolean side;
	private boolean dragging;
	private double dragX;
	private double dragY;
	private double pressX;
	private double pressY;
	private long openedAt;
	private float listScroll;
	private long lastAsk;
	private @Nullable List<Component> tooltip;
	private int cardX;
	private int cardY;
	private int cardW;
	private int cardH;

	public PetaScreen() {
		super(Component.translatable("kultivator.peta.title"));
	}

	// ================================================================== layout

	@Override
	protected void init() {
		if (openedAt == 0L) {
			openedAt = Util.getMillis();
			UiSound.open();
			PetaClient.request(PetaNet.OP_OPEN, 0, 0, "", new long[0]);
			PetaCache.forgetAsked();
		}
		int w = Math.min(width - 8, 480);
		int h = Math.min(height - 8, 290);
		x0 = (width - w) / 2;
		y0 = (height - h) / 2;
		x1 = x0 + w;
		y1 = y0 + h;
		side = w >= 300;
		mx0 = x0 + 10;
		my0 = y0 + 24;
		mx1 = side ? x1 - SIDE_W - 14 : x1 - 10;
		my1 = y1 - 28;
		LocalPlayer player = minecraft == null ? null : minecraft.player;
		String dim = PetaCache.dim();
		if (player != null && (Double.isNaN(viewX) || !dim.equals(viewDim))) {
			viewX = player.getX();
			viewZ = player.getZ();
			viewDim = dim;
		}
		buttons.clear();
		// toolbar: zooms, overlays, centre
		int bx = mx0;
		int by = y1 - 22;
		for (int i = 0; i < ZOOMS.length; i++) {
			int index = i;
			PetaUi.Button zoom = new PetaUi.Button(Component.translatable("kultivator.peta.zoom." + i), () -> setZoom(index, (mx0 + mx1) / 2.0D,
					(my0 + my1) / 2.0D));
			zoom.at(bx, by, 30, 13);
			zoom.selected = zoomIndex == i;
			zoom.tip = Component.translatable("kultivator.peta.zoom.tip", ZOOMS[i]);
			buttons.add(zoom);
			bx += 32;
		}
		bx += 6;
		bx = overlay(bx, by, "weather", overlayWeather, () -> overlayWeather = !overlayWeather);
		bx = overlay(bx, by, "sky", overlaySky, () -> overlaySky = !overlaySky);
		bx = overlay(bx, by, "seal", overlaySeals, () -> overlaySeals = !overlaySeals);
		bx = overlay(bx, by, "route", overlayRoute, () -> overlayRoute = !overlayRoute);
		PetaUi.Button centre = new PetaUi.Button(Component.translatable("kultivator.peta.centre"), this::centre);
		centre.at(Math.max(bx + 4, mx1 - 54), by, 54, 13);
		centre.accent = Palette.JADE;
		buttons.add(centre);
		if (side) {
			int sx = mx1 + 8;
			searchBox = new EditBox(font, sx, my0, SIDE_W, 12, Component.translatable("kultivator.peta.search"));
			searchBox.setMaxLength(32);
			searchBox.setHint(Component.translatable("kultivator.peta.search").withStyle(ChatFormatting.DARK_GRAY));
			searchBox.setValue(search);
			searchBox.setResponder(value -> {
				search = value == null ? "" : value;
				listScroll = 0.0F;
				rebuildList();
			});
			addRenderableWidget(searchBox);
			int fx = sx;
			int fy = my0 + 16;
			for (Category category : Category.values()) {
				PetaUi.Button coin = new PetaUi.Button(Component.empty(), () -> {
					if (!HIDDEN.remove(category)) {
						HIDDEN.add(category);
					}
					rebuildList();
					rebuildWidgets();
				});
				coin.at(fx, fy, 20, 14);
				coin.selected = !HIDDEN.contains(category);
				coin.accent = category.ordinal();
				coin.tip = Component.translatable("kultivator.peta.category." + category.name().toLowerCase(Locale.ROOT));
				buttons.add(coin);
				fx += 22;
				if (fx + 20 > sx + SIDE_W) {
					fx = sx;
					fy += 16;
				}
			}
		} else {
			searchBox = null;
		}
		readSheet(true);
	}

	private int overlay(int x, int y, String id, boolean on, Runnable toggle) {
		PetaUi.Button b = new PetaUi.Button(Component.translatable("kultivator.peta.overlay." + id), () -> {
			toggle.run();
			rebuildWidgets();
		});
		int w = Math.max(30, Math.min(52, font.width(b.label) + 8));
		b.at(x, y, w, 13);
		b.selected = on;
		b.accent = Palette.JADE;
		b.tip = Component.translatable("kultivator.peta.overlay." + id + ".tip");
		buttons.add(b);
		return x + w + 2;
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
		rebuildWidgets();
	}

	private void centre() {
		LocalPlayer player = minecraft == null ? null : minecraft.player;
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
			mark.name = Component.translatable("kultivator.peta.mark.ship", Component.translatable("kultivator.kapal.class."
					+ dev.kultivator.dunia.kapal.KapalClass.byOrdinal(ship.getIntOr("class", 0)).model()));
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
		String query = search.strip().toLowerCase(Locale.ROOT);
		LocalPlayer player = minecraft == null ? null : minecraft.player;
		for (Mark mark : marks) {
			if (HIDDEN.contains(mark.category)) {
				continue;
			}
			if (!query.isEmpty() && !mark.name.getString().toLowerCase(Locale.ROOT).contains(query) && !mark.kind.contains(query)) {
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

	// ================================================================== drawing

	@Override
	public void tick() {
		super.tick();
		readSheet(false);
		askRegions();
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

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, Palette.argb(0xA0, Palette.NIGHT), Palette.argb(0xC8, Palette.NIGHT_MIST));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		tooltip = null;
		ScrollFrame.paper(g, x0, x1, y0, y1, y0, y1);
		ScrollFrame.rod(g, x0, x1, y0);
		ScrollFrame.rod(g, x0, x1, y1);
		g.nextStratum();
		drawHeader(g, mouseX, mouseY);
		drawMap(g, mouseX, mouseY);
		if (side) {
			drawSide(g, mouseX, mouseY);
		}
		for (PetaUi.Button button : buttons) {
			if (button.label.getString().isEmpty()) {
				drawCoin(g, button, mouseX, mouseY);
			} else {
				button.draw(g, font, mouseX, mouseY);
			}
			if (button.over(mouseX, mouseY) && button.tip != null) {
				tooltip = List.of(button.tip);
			}
		}
		Brush.fit(g, font, Component.translatable("kultivator.peta.hint"), mx0, y1 - 8, x1 - mx0 - 10, Palette.INK_FAINT, 0.55F, 0.4F, false);
		super.extractRenderState(g, mouseX, mouseY, a);
		g.nextStratum();
		if (selected != null) {
			drawCard(g, mouseX, mouseY);
		}
		if (tooltip != null && !tooltip.isEmpty()) {
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	private void drawHeader(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Brush.fit(g, font, Brush.bold(title), x0 + 12, y0 + 9, 180, Palette.SEAL_DARK, 0.95F, 0.6F, false);
		String dim = PetaCache.dim();
		String path = dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
		Component dimName = Component.translatableWithFallback("kultivator.peta.dim." + path, pretty("x/" + path));
		CompoundTag sheet = PetaCache.sheet();
		Component sub = Component.translatable("kultivator.peta.header", dimName, sheet.getIntOr("known", 0), sheet.getIntOr("explored", 0));
		Brush.fit(g, font, sub, x0 + 196, y0 + 10, mx1 - x0 - 200, Palette.INK_SOFT, 0.62F, 0.45F, false);
		if (overMap(mouseX, mouseY)) {
			int wx = (int) Math.floor(worldX(mouseX));
			int wz = (int) Math.floor(worldZ(mouseY));
			Component at = Component.translatable("kultivator.peta.cursor", wx, wz);
			float w = font.width(at) * 0.62F;
			Brush.text(g, font, at, x1 - 12 - w, y0 + 10, Palette.INK_SOFT, 0.62F, false);
		}
	}

	private boolean overMap(double mx, double my) {
		return mx >= mx0 && mx < mx1 && my >= my0 && my < my1;
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

	private void drawMap(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int bpp = bpp();
		PetaPainter.frame(viewX, viewZ, bpp);
		g.fill(mx0 - 2, my0 - 2, mx1 + 2, my1 + 2, Palette.argb(0xFF, 0x3A2A1A));
		g.fill(mx0, my0, mx1, my1, 0xFFDCCFA8);
		g.enableScissor(mx0, my0, mx1, my1);
		if (PetaPainter.ready()) {
			float f = PetaPainter.shownBpp() / (float) bpp;
			float sx = screenX(PetaPainter.shownX());
			float sy = screenY(PetaPainter.shownZ());
			g.pose().pushMatrix();
			g.pose().translate(sx, sy);
			g.pose().scale(f, f);
			g.blit(RenderPipelines.GUI_TEXTURED, PetaPainter.TEXTURE, 0, 0, 0.0F, 0.0F, PetaPainter.SIZE, PetaPainter.SIZE, PetaPainter.SIZE,
					PetaPainter.SIZE);
			g.pose().popMatrix();
		} else {
			Brush.fitCentered(g, font, Component.translatable("kultivator.peta.painting"), (mx0 + mx1) / 2.0F, (my0 + my1) / 2.0F, mx1 - mx0,
					Palette.INK_SOFT, 0.8F, 0.5F, false);
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
		Brush.frame(g, mx0 - 3, my0 - 3, mx1 + 3, my1 + 3, Palette.argb(0x80, Palette.GOLD));
		int cs = Math.min(40, Math.max(24, (mx1 - mx0) / 8));
		g.blit(RenderPipelines.GUI_TEXTURED, COMPASS, mx1 - cs - 4, my0 + 4, 0.0F, 0.0F, cs, cs, 64, 64, 64, 64, 0xD8FFFFFF);
		drawScale(g);
		if (overlaySky && sheet.contains("sky")) {
			Component sky = Component.translatable("kultivator.peta.sky", Component.translatable("kultivator.peta.sky."
					+ sheet.getStringOr("sky", "").toLowerCase(Locale.ROOT)));
			int w = Math.min(mx1 - mx0 - 60, Math.round(font.width(sky) * 0.7F) + 22);
			int bx = (mx0 + mx1 - w) / 2;
			g.fill(bx, my0 + 3, bx + w, my0 + 15, Palette.argb(0xC0, 0x1A1410));
			icon(g, 29, bx + 2, my0 + 3, 12, 0xFFFFFFFF);
			Brush.fit(g, font, sky, bx + 16, my0 + 6, w - 18, 0xFFFFE070, 0.7F, 0.5F, false);
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
		int x = mx0 + 6;
		int y = my1 - 8;
		g.fill(x, y, x + len, y + 2, 0xE01E1E1B);
		g.fill(x, y - 3, x + 1, y + 2, 0xE01E1E1B);
		g.fill(x + len - 1, y - 3, x + len, y + 2, 0xE01E1E1B);
		g.fill(x + len / 2, y - 2, x + len / 2 + 1, y + 2, 0xA01E1E1B);
		Brush.text(g, font, Component.translatable("kultivator.peta.scale", pick), x, y - 9, 0xFF1E1E1B, 0.55F, false);
	}

	private void drawDomains(GuiGraphicsExtractor g, CompoundTag sheet) {
		var registries = minecraft == null || minecraft.level == null ? null : minecraft.level.registryAccess();
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
				Component name = Component.literal(d.getStringOr("id", "?"));
				if (registries != null) {
					try {
						name = d.read("name", ComponentSerialization.CODEC, registries.createSerializationContext(NbtOps.INSTANCE)).orElse(name);
					} catch (RuntimeException ignored) {
						// keep the id
					}
				}
				String seal = d.getStringOr("seal", "?");
				if (seal.startsWith("#")) {
					seal = Language.getInstance().getOrDefault(seal.substring(1), "?");
				}
				g.fill(Math.round(cx - 6), Math.round(cy - r + 2), Math.round(cx + 6), Math.round(cy - r + 14), Palette.argb(0xE0, Palette.SEAL));
				Brush.centered(g, font, Component.literal(seal), cx, cy - r + 4, 0xFFF4E0D0, 0.8F, false);
				Brush.fitCentered(g, font, name, cx, cy - r + 16, Math.max(40.0F, r * 1.6F), Palette.argb(0xF0, Palette.darken(0xFF000000
						| color, 0.55F)), 0.62F, 0.45F, false);
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

	private void drawMarks(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int zoom = markerZoom();
		Mark hover = null;
		float best = 49.0F;
		long time = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L;
		for (Mark mark : marks) {
			if (HIDDEN.contains(mark.category) || zoom < mark.minZoom && !mark.live && mark.waypoint < 0) {
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
				float t = mark.seal / 100.0F;
				int ring = Palette.mix(0xFFD5322A, 0xFF2E8963, t);
				Brush.ring(g, sx, sy, 7.0F, 8.4F, ring);
				Brush.centered(g, font, Component.literal(mark.seal + "%"), sx, sy + 8.0F, ring, 0.55F, false);
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
		if (hover != null && selected == null) {
			List<Component> lines = new ArrayList<>();
			lines.add(Brush.bold(hover.name));
			lines.add(Component.translatable("kultivator.peta.card.where", distanceText(hover)).withStyle(ChatFormatting.GRAY));
			if (hover.live && hover.ends > time) {
				lines.add(Component.translatable("kultivator.peta.card.ends", PetaUi.duration((hover.ends - time) / 20L))
						.withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			lines.add(Component.translatable("kultivator.peta.card.click").withStyle(ChatFormatting.DARK_GREEN));
			tooltip = lines;
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
		LocalPlayer player = minecraft == null ? null : minecraft.player;
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

	private void icon(GuiGraphicsExtractor g, int index, int x, int y, int size, int tint) {
		int i = Mth.clamp(index, 0, 31);
		g.blit(RenderPipelines.GUI_TEXTURED, MARKERS, x, y, (i % 8) * 16.0F, (i / 8) * 16.0F, size, size, 16, 16, 128, 64, tint);
	}

	private void drawCoin(GuiGraphicsExtractor g, PetaUi.Button coin, int mouseX, int mouseY) {
		Category category = Category.values()[Mth.clamp(coin.accent, 0, Category.values().length - 1)];
		boolean on = !HIDDEN.contains(category);
		boolean hover = coin.over(mouseX, mouseY);
		g.fill(coin.x, coin.y, coin.x + coin.w, coin.y + coin.h, on ? (hover ? 0xFFF6EBC4 : Palette.TALISMAN_LOW) : Palette.LOCKED_PAPER);
		Brush.frame(g, coin.x, coin.y, coin.x + coin.w, coin.y + coin.h, on ? Palette.GOLD_DARK : Palette.argb(0x90, Palette.LOCKED));
		icon(g, category.icon, coin.x + (coin.w - 12) / 2, coin.y + 1, 12, on ? 0xFFFFFFFF : 0x70FFFFFF);
	}

	private void drawSide(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int sx = mx1 + 8;
		int top = my0 + 16 + 2 * 16 + 4;
		int bottom = my1;
		PetaUi.panel(g, sx - 2, top - 2, sx + SIDE_W + 2, bottom + 2);
		Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.peta.list", listed.size())), sx + 3, top + 2, SIDE_W - 6, Palette.SEAL_DARK,
				0.62F, 0.45F, false);
		int rowH = 13;
		int listTop = top + 12;
		int rows = Math.max(1, (bottom - listTop - 2) / rowH);
		int maxScroll = Math.max(0, listed.size() - rows);
		listScroll = Mth.clamp(listScroll, 0.0F, maxScroll);
		int first = Math.round(listScroll);
		LocalPlayer player = minecraft == null ? null : minecraft.player;
		for (int r = 0; r < rows && first + r < listed.size(); r++) {
			Mark mark = listed.get(first + r);
			int y = listTop + r * rowH;
			boolean hover = mouseX >= sx && mouseX < sx + SIDE_W && mouseY >= y && mouseY < y + rowH;
			if (hover || mark == selected) {
				g.fill(sx, y, sx + SIDE_W, y + rowH, mark == selected ? 0x50D4A637 : 0x30FFFFFF);
			}
			icon(g, mark.icon, sx + 1, y + 1, 11, mark.waypoint >= 0 ? 0xFF000000 | mark.color : 0xFFFFFFFF);
			Brush.fit(g, font, mark.name, sx + 14, y + 3, SIDE_W - 46, Palette.INK, 0.6F, 0.4F, false);
			if (player != null) {
				int d = (int) Math.round(Math.sqrt(dist2(player, mark)));
				Component dt = Component.literal(d >= 10000 ? (d / 1000) + "k" : String.valueOf(d));
				float w = font.width(dt) * 0.55F;
				Brush.text(g, font, dt, sx + SIDE_W - 3 - w, y + 4, Palette.INK_FAINT, 0.55F, false);
			}
		}
		if (listed.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(marks.isEmpty() ? "kultivator.peta.list.none" : "kultivator.peta.list.filtered"), sx + 3,
					listTop + 2, SIDE_W - 6, Palette.INK_FAINT, 0.58F, false);
		}
	}

	// ================================================================== the marker card

	private void openCard(Mark mark) {
		selected = mark;
		cardButtons.clear();
		cardW = 150;
		CompoundTag sheet = PetaCache.sheet();
		boolean captain = sheet.getBooleanOr("captain", false);
		List<PetaUi.Button> list = new ArrayList<>();
		PetaUi.Button sail = new PetaUi.Button(Component.translatable("kultivator.peta.card.sail"), () -> {
			PetaClient.request(PetaNet.OP_SAIL, mark.x, mark.z, mark.name.getString(), new long[0]);
			onClose();
		});
		sail.enabled = captain;
		sail.tip = Component.translatable(captain ? "kultivator.peta.card.sail.tip" : "kultivator.peta.sail.not_captain");
		sail.accent = Palette.JADE;
		list.add(sail);
		if (mark.waypoint >= 0) {
			PetaUi.Button delete = new PetaUi.Button(Component.translatable("kultivator.peta.card.delete"), () -> {
				PetaClient.request(PetaNet.OP_WAYPOINT_DELETE, mark.waypoint, 0, "", new long[0]);
				selected = null;
			});
			list.add(delete);
		} else {
			PetaUi.Button mark2 = new PetaUi.Button(Component.translatable("kultivator.peta.card.mark"), () -> {
				PetaClient.request(PetaNet.OP_WAYPOINT_ADD, mark.x, mark.z, mark.name.getString(), new long[0]);
				selected = null;
			});
			list.add(mark2);
		}
		list.add(new PetaUi.Button(Component.translatable("kultivator.peta.card.close"), () -> selected = null));
		cardButtons.addAll(list);
	}

	private Component distanceText(Mark mark) {
		LocalPlayer player = minecraft == null ? null : minecraft.player;
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
		long time = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L;
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
		var registries = minecraft == null || minecraft.level == null ? null : minecraft.level.registryAccess();
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

	private void drawCard(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Mark mark = selected;
		if (mark == null) {
			return;
		}
		List<Component> lines = cardLines(mark);
		float scale = 0.62F;
		int textW = cardW - 12;
		float h = 18.0F;
		for (Component line : lines) {
			h += Brush.wrapHeight(font, line, textW, scale) + 1.0F;
		}
		cardH = Math.round(h) + 22;
		float sx = screenX(mark.x);
		float sy = screenY(mark.z);
		cardX = Mth.clamp(Math.round(sx + 12), mx0 + 2, mx1 - cardW - 2);
		cardY = Mth.clamp(Math.round(sy - cardH / 2.0F), my0 + 2, my1 - cardH - 2);
		if (cardX < sx && sx < cardX + cardW) {
			cardX = Mth.clamp(Math.round(sx - 12 - cardW), mx0 + 2, mx1 - cardW - 2);
		}
		PetaUi.panel(g, cardX, cardY, cardX + cardW, cardY + cardH);
		icon(g, mark.icon, cardX + 5, cardY + 5, 13, mark.waypoint >= 0 ? 0xFF000000 | mark.color : 0xFFFFFFFF);
		Brush.fit(g, font, Brush.bold(mark.name), cardX + 21, cardY + 8, cardW - 26, Palette.SEAL_DARK, 0.78F, 0.5F, false);
		float y = cardY + 21;
		for (Component line : lines) {
			y += Brush.wrap(g, font, line, cardX + 6, y, textW, 0xFF1B2521, scale, false) + 1.0F;
		}
		int bw = (cardW - 12 - 4 * (cardButtons.size() - 1)) / Math.max(1, cardButtons.size());
		int bx = cardX + 6;
		int by = cardY + cardH - 17;
		for (PetaUi.Button button : cardButtons) {
			button.at(bx, by, bw, 12);
			button.draw(g, font, mouseX, mouseY);
			if (button.over(mouseX, mouseY) && button.tip != null) {
				tooltip = List.of(button.tip);
			}
			bx += bw + 4;
		}
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (selected != null && mx >= cardX && mx < cardX + cardW && my >= cardY && my < cardY + cardH) {
			for (PetaUi.Button button : cardButtons) {
				if (button.over(mx, my)) {
					if (button.enabled) {
						UiSound.press();
						button.action.run();
					} else {
						UiSound.denied();
					}
					return true;
				}
			}
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (PetaUi.Button button : buttons) {
				if (button.over(mx, my)) {
					if (button.enabled) {
						UiSound.press();
						button.action.run();
					} else {
						UiSound.denied();
					}
					return true;
				}
			}
			if (side && clickList(mx, my)) {
				return true;
			}
		}
		if (overMap(mx, my)) {
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
					UiSound.press();
					openCard(near);
				} else {
					int max = PetaCache.sheet().getIntOr("max_waypoints", 32);
					if (max > 0) {
						UiSound.toggle(true);
						PetaClient.request(PetaNet.OP_WAYPOINT_ADD, (int) Math.floor(worldX(mx)), (int) Math.floor(worldZ(my)), "", new long[0]);
					} else {
						UiSound.denied();
					}
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean clickList(double mx, double my) {
		int sx = mx1 + 8;
		int top = my0 + 16 + 2 * 16 + 4;
		int listTop = top + 12;
		if (mx < sx || mx >= sx + SIDE_W || my < listTop || my >= my1) {
			return false;
		}
		int index = Math.round(listScroll) + (int) Math.floor((my - listTop) / 13.0D);
		if (index >= 0 && index < listed.size()) {
			Mark mark = listed.get(index);
			viewX = mark.x;
			viewZ = mark.z;
			UiSound.press();
			openCard(mark);
			return true;
		}
		return false;
	}

	private @Nullable Mark markAt(double mx, double my) {
		Mark best = null;
		double bestD = 49.0D;
		int zoom = markerZoom();
		for (Mark mark : marks) {
			if (HIDDEN.contains(mark.category) || zoom < mark.minZoom && !mark.live && mark.waypoint < 0) {
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
		if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			dragging = false;
			if (Math.abs(event.x() - pressX) < 3.0D && Math.abs(event.y() - pressY) < 3.0D) {
				Mark near = markAt(event.x(), event.y());
				if (near != null) {
					UiSound.press();
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
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (side && x >= mx1 + 8 && x < mx1 + 8 + SIDE_W && y >= my0 + 52 && y < my1) {
			listScroll -= (float) scrollY;
			return true;
		}
		if (overMap(x, y) && scrollY != 0.0D) {
			setZoom(zoomIndex + (scrollY > 0.0D ? -1 : 1), x, y);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (searchBox != null && searchBox.isFocused()) {
			if (event.key() == InputConstants.KEY_ESCAPE) {
				searchBox.setFocused(false);
				return true;
			}
			return super.keyPressed(event);
		}
		if (PetaClient.PETA.matches(event)) {
			onClose();
			return true;
		}
		double step = 24.0D * bpp();
		switch (event.key()) {
			case InputConstants.KEY_LEFT -> viewX -= step;
			case InputConstants.KEY_RIGHT -> viewX += step;
			case InputConstants.KEY_UP -> viewZ -= step;
			case InputConstants.KEY_DOWN -> viewZ += step;
			case InputConstants.KEY_HOME -> centre();
			case InputConstants.KEY_EQUALS, InputConstants.KEY_ADD -> setZoom(zoomIndex - 1, (mx0 + mx1) / 2.0D, (my0 + my1) / 2.0D);
			case InputConstants.KEY_MINUS -> setZoom(zoomIndex + 1, (mx0 + mx1) / 2.0D, (my0 + my1) / 2.0D);
			default -> {
				return super.keyPressed(event);
			}
		}
		return true;
	}

	@Override
	public void removed() {
		PetaClient.request(PetaNet.OP_CLOSE, 0, 0, "", new long[0]);
		UiSound.close();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

}
