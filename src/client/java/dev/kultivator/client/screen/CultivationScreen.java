package dev.kultivator.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.hub.DiSini;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Fokus;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.screen.ui.TombolKembali;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Gulungan Utama (DESIGN 43.3): the one screen of the mod. A jade scroll over an ink night with a rail of jade
 * tablets on its left edge (the eight sections, keys 1-8, plus the Di Sini tablet, key 9, while a world context lives),
 * a header with the back control, the section name, the page coins (Q / E), the purse and the "?" seal, the sub bar of
 * the shown page (with the Sumber chip), the page's content box and a footer with the last message and the key hints.
 * <p>
 * The chrome is drawn with the single kit ({@link TabBar} rail / coins / sub chips, {@link TombolKembali},
 * {@link LabelHarga}) in the host's own {@link Fokus}. Pages come from {@link HubPages} ({@link Urutan} order), are built
 * lazily the first time they are shown and laid out into the content box; only the shown page draws, ticks and asks for
 * data (43.0.12). The scroll unrolls once when opened from the world; moving between sections and pages never replays it
 * (R29). Input follows 43.4.1: a page whose text field writes or whose confirm seal is open gets keys first, then the
 * Gulungan keys (43.4.2), then the page. Every mouse event inside the content box goes to the page.
 * <p>
 * The legacy tab ids of {@link #CultivationScreen(int)} (server {@code OpenScreen.INFO} args) stay valid.
 */
public class CultivationScreen extends Screen {

	public static final int TAB_STATUS = 0;
	public static final int TAB_ROOT_DAO = 1;
	public static final int TAB_TECHNIQUES = 2;
	public static final int TAB_SETTINGS = 3;
	public static final int TAB_HELP = 4;
	/** The Jalan page (P7-jalan); the server opens it with {@code Payloads.OpenScreen.TAB_JALAN}. */
	public static final int TAB_JALAN = 5;
	/** The Kitab Alkimia (P7-harta); the server opens it with {@code Payloads.OpenScreen.TAB_ALKIMIA}. */
	public static final int TAB_ALKIMIA = 6;

	private static final String[] SECTION_KEYS = {"diri", "teknik", "murid", "sekte", "dunia", "keabadian", "panduan", "pengaturan"};
	private static final int[] SECTION_GLYPHS = {Glyphs.STATUS, Glyphs.CODEX, Glyphs.TALENT, Glyphs.HALL, Glyphs.PATH, Glyphs.SEAL, Glyphs.HELP,
			Glyphs.SETTINGS};
	private static final String H = "kultivator.gui43.host.";

	private static final String DI_SINI = "di_sini";

	private static final long UNROLL_MILLIS = 250L;
	private static final long WIDE_MILLIS = 120L;
	private static final long PESAN_MILLIS = 6000L;
	private static final int RAIL_W = 20;
	private static final int RAIL_W_SEMPIT = 18;
	/** Widest named rail (43.3.1); the named width itself follows the longest section name ({@link #init}). */
	private static final int RAIL_W_NAMA_MAKS = 100;
	private static final int MAX_W = 440;
	private static final int MAX_H = 284;
	private static final int WIDE_W = 560;
	private static final int WIDE_H = 330;
	private static final int NAMED_TABLETS_MIN = 230;
	/** Header and sub bar: buttons at least 14 px tall, 12 in the narrow layout (43.3.2). */
	private static final int HEAD_H = 14;
	private static final int HEAD_H_SEMPIT = 12;
	private static final int SUB_H = 14;
	private static final int SUB_H_SEMPIT = 12;
	private static final int FOOT_H = 10;
	/** The "?" seal: a 13 px seal in a 16 px wide click box (43.0.6). */
	private static final int SEAL = 13;
	private static final int SEAL_BOX = 16;

	// ================================================================== the Di Sini context (43.8; it can arrive while closed)

	private static @Nullable Page konteks;
	private static Component konteksJudul = Component.empty();
	private static int konteksGlyph = Glyphs.HALL;
	private static long konteksSejak;

	// ================================================================== state

	private final Map<String, Page> pages = new HashMap<>();
	private final Map<Page, String> laid = new HashMap<>();
	private final Eased backdrop = new Eased(6.0F);
	/** The chrome's focus engine (rail, coins, sub chips, back, purse, seal): its zones take clicks outside the content box. */
	private final Fokus krom = new Fokus(alasan -> pesan(alasan, Palette.DANGER));
	private final TabBar rel = new TabBar();
	private final TabBar koin = new TabBar();
	private final TabBar subBar = new TabBar();
	private final LabelHarga.Gulir uangGulir = new LabelHarga.Gulir();
	private int section;
	private String pageId;
	private String subId = "";
	private @Nullable Page shown;
	private @Nullable CompoundTag fokusTunda;
	private String subTunda;
	private List<String> ids = List.of();
	private long openedAt;
	private long lastFrame;
	private long seenVersion = Long.MIN_VALUE;
	private boolean opened;
	private boolean sempit;
	private boolean bernama;
	private int mouseX;
	private int mouseY;
	private @Nullable Component pesan;
	private int pesanWarna = Palette.INK;
	private long pesanSampai;
	private @Nullable KeyHints.HelpSeal helpSeal;
	/** Where the "?" seal was pressed: the Ensiklopedia then offers one step back there (header back, Esc). */
	private @Nullable Tujuan asalBantuan;

	// geometry
	private int railW = RAIL_W;
	private int subH = SUB_H;
	private int headH = HEAD_H;
	private boolean wide;
	private int paperX0;
	private int paperX1;
	private int paperTop;
	private int paperBottom;
	private int contentX;
	private int contentY;
	private int contentW;
	private int contentH;
	private int headY;
	private int subY;
	private boolean hasSubs;
	private float animX0 = Float.NaN;
	private float animFromX0;
	private float animFromX1;
	private long animAt;

	private CultivationScreen(Tujuan tujuan) {
		super(Component.translatable("kultivator.screen.title"));
		this.section = Math.max(0, Math.min(HubNav.DI_SINI, tujuan.bagian()));
		this.pageId = tujuan.halaman();
		this.subTunda = tujuan.sub();
		this.fokusTunda = tujuan.fokus();
	}

	/** A Gulungan opened on {@code tujuan} ({@link HubNav#buka} is the way to open it). */
	public static CultivationScreen buka(Tujuan tujuan) {
		return new CultivationScreen(tujuan);
	}

	/** The legacy {@code OpenScreen.INFO} tab ids as places (43.4.4). */
	public static Tujuan tujuanDari(int legacyTab) {
		return switch (legacyTab) {
			case TAB_ROOT_DAO -> Tujuan.of(HubNav.DIRI, Urutan.ID_AKAR_DAO);
			case TAB_TECHNIQUES -> Tujuan.of(HubNav.TEKNIK, Urutan.ID_KODEKS);
			case TAB_SETTINGS -> Tujuan.bagian(HubNav.PENGATURAN);
			case TAB_HELP -> Tujuan.of(HubNav.PANDUAN, Urutan.ID_ENSIKLOPEDIA);
			case TAB_JALAN -> Tujuan.of(HubNav.DIRI, Urutan.ID_JALAN);
			case TAB_ALKIMIA -> Tujuan.of(HubNav.DIRI, Urutan.ID_ALKIMIA);
			default -> Tujuan.of(HubNav.DIRI, Urutan.ID_STATUS);
		};
	}

	/**
	 * Registers the pages of this package (a module may re-register an id from its own client class: same id replaces).
	 * Called once from {@code KultivatorClient} before the module inits; the five Pengaturan pages come from
	 * {@code SettingsPage.daftar} (called by {@code EpicClient}).
	 */
	public static void daftarBawaan() {
		HubPages.daftar(HubNav.DIRI, Urutan.ID_STATUS, Urutan.STATUS, () -> new StatusPage(null));
		HubPages.daftar(HubNav.DIRI, Urutan.ID_AKAR_DAO, Urutan.AKAR_DAO, () -> new RootDaoPage(null));
		HubPages.daftar(HubNav.DIRI, Urutan.ID_JALAN, Urutan.JALAN, () -> new JalanPage(null));
		HubPages.daftar(HubNav.DIRI, Urutan.ID_ALKIMIA, Urutan.ALKIMIA, () -> new AlchemyBookPage(null));
		HubPages.daftar(HubNav.DIRI, Urutan.ID_PUSAKA, Urutan.PUSAKA, dev.kultivator.client.tempa.PusakaPage::new);
		HubPages.daftar(HubNav.DIRI, Urutan.ID_HARTA, Urutan.HARTA, HartaPage::new);
		// the 41 abode sheet from the world (an NPC, /kultivasi gabung kediaman) lands on Diri › Harta, on its own tab
		HubPages.daftarRumah(dev.kultivator.sekte.gabung.ui.GabungLembar.Kediaman.LAYAR, java.util.Set.of(), HubNav.DIRI, Urutan.ID_HARTA, "", null);
		HubPages.daftar(HubNav.PANDUAN, Urutan.ID_ENSIKLOPEDIA, Urutan.ENSIKLOPEDIA, () -> new HelpPage(null));
	}

	// ================================================================== the Di Sini context

	/** Installs {@code halaman} as the Di Sini page (43.8); the old context's page is told it ended ({@link Page#onHide}). */
	public static void konteks(Page halaman, Component judul, int glyph) {
		Page old = konteks;
		konteks = halaman;
		konteksJudul = judul == null ? Component.empty() : judul;
		konteksGlyph = glyph < 0 ? Glyphs.HALL : glyph;
		konteksSejak = Util.getMillis();
		if (old != null && old != halaman) {
			old.onHide();
			if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof CultivationScreen hub) {
				hub.laid.remove(old);
				if (hub.shown == old) {
					hub.shown = null;
				}
			}
		}
	}

	/**
	 * The context ended on the server side: the tablet goes; when Di Sini was shown the Gulungan moves to the last section
	 * with footer {@code pesan}. The page's {@link Page#onHide} is not called (the session is already closed).
	 */
	public static void tutupKonteks(@Nullable Component pesan) {
		if (konteks == null) {
			return;
		}
		Page old = konteks;
		konteks = null;
		if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof CultivationScreen hub) {
			hub.laid.remove(old);
			if (hub.section == HubNav.DI_SINI) {
				hub.shown = null;
				hub.tampilkan(HubNav.lastSection(), "", "", null);
				if (pesan != null) {
					hub.pesan(pesan, Palette.INK_SOFT);
				}
			}
		}
	}

	/** True while a Di Sini context lives. */
	public static boolean adaKonteks() {
		return konteks != null;
	}

	/** The home place of the live context (43.12.4), or null when it lives under the Di Sini tablet itself. */
	private static @Nullable Tujuan rumahKonteks() {
		return konteks != null && konteks == DiSini.halaman() ? DiSini.rumah() : null;
	}

	/** True when the shown page holds the live context (Di Sini, or the context's home page). */
	private boolean memegangKonteks() {
		Tujuan rumah = rumahKonteks();
		return konteks != null && (section == HubNav.DI_SINI || rumah != null && section == rumah.bagian() && pageId.equals(rumah.halaman()));
	}

	// ================================================================== places

	/** The section shown ({@code HubNav.DIRI} ... {@code HubNav.DI_SINI}). */
	public int section() {
		return section;
	}

	/** The place shown now (section, page, sub). */
	public Tujuan tempat() {
		return new Tujuan(section, pageId, subId, null);
	}

	/** Moves to {@code tujuan} (another section, page or sub; a focus tag goes to the page). */
	public void tuju(Tujuan tujuan) {
		if (!opened) {
			section = Math.max(0, Math.min(HubNav.DI_SINI, tujuan.bagian()));
			pageId = tujuan.halaman();
			subTunda = tujuan.sub();
			fokusTunda = tujuan.fokus();
			return;
		}
		tampilkan(tujuan.bagian(), tujuan.halaman(), tujuan.sub(), tujuan.fokus());
	}

	/** Panduan › Ensiklopedia on {@code topic}. */
	public void showHelp(String topic) {
		tuju(Tujuan.of(HubNav.PANDUAN, Urutan.ID_ENSIKLOPEDIA));
		if (shown instanceof HelpPage help) {
			help.focus(topic == null || topic.isEmpty() ? "jalan" : topic);
		}
	}

	/** The visible pages of section {@code s} in order. */
	private List<String> halaman(int s) {
		List<String> list = new ArrayList<>();
		if (s == HubNav.DI_SINI) {
			if (konteks != null) {
				list.add(DI_SINI);
			}
			return list;
		}
		for (HubPages.Entri e : HubPages.dari(s)) {
			Page p = page(e.id());
			if (p != null && p.tampil()) {
				list.add(e.id());
			}
		}
		return list;
	}

	/** The page of {@code id}, built and mounted the first time; null when it cannot be built (yet). */
	private @Nullable Page page(String id) {
		if (DI_SINI.equals(id)) {
			if (konteks != null) {
				konteks.pasang(this);
			}
			return konteks;
		}
		Page p = pages.get(id);
		if (p != null) {
			return p;
		}
		HubPages.Entri entri = HubPages.cari(id);
		if (entri == null) {
			return null;
		}
		try {
			p = entri.buat().get();
		} catch (RuntimeException e) {
			Kultivator.LOGGER.warn("Kultivator: page {} could not be built", id, e);
			p = null;
		}
		if (p == null) {
			return null;
		}
		p.pasang(this);
		pages.put(id, p);
		return p;
	}

	/**
	 * Shows page {@code id} ("" = the section's last) of section {@code s} on sub {@code sub} ("" = the page's last), with
	 * focus tag {@code fokus}. A locked page is not entered (its reason goes to the footer).
	 */
	private void tampilkan(int s, String id, String sub, @Nullable CompoundTag fokus) {
		// 43.8.1.3: a context with a home page lives there only; its Di Sini tablet is the jump to it.
		Tujuan rumah = rumahKonteks();
		if (s == HubNav.DI_SINI && rumah != null) {
			s = rumah.bagian();
			id = rumah.halaman();
		}
		int target = Math.max(0, Math.min(HubNav.DI_SINI, s));
		if (target == HubNav.DI_SINI && konteks == null) {
			target = section == HubNav.DI_SINI ? HubNav.lastSection() : section;
		}
		List<String> list = halaman(target);
		String want = id == null || id.isEmpty() ? (target < HubNav.SECTIONS ? HubNav.halamanTerakhir(target) : DI_SINI) : id;
		Page asked = list.contains(want) ? page(want) : null;
		Component lock = asked == null ? null : asked.kunci();
		if (lock != null && shown != null && !(want.equals(pageId) && target == section)) {
			// A locked page is refused where the player stands; with nothing shown yet (first open) it falls back below.
			UiSound.denied();
			pesan(lock, Palette.DANGER);
			return;
		}
		if (asked == null || lock != null) {
			want = "";
			for (String candidate : list) {
				Page p = page(candidate);
				if (p != null && p.kunci() == null) {
					want = candidate;
					break;
				}
			}
			if (want.isEmpty() && !list.isEmpty()) {
				want = list.get(0);
			}
			if (lock != null) {
				pesan(lock, Palette.DANGER);
			}
		}
		Page next = want.isEmpty() ? null : page(want);
		boolean changed = next != shown;
		if (changed && shown != null && shown != konteks) {
			shown.onHide();
		}
		section = target;
		pageId = want;
		shown = next;
		if (!Urutan.ID_ENSIKLOPEDIA.equals(want)) {
			asalBantuan = null;
		}
		ids = list;
		if (target < HubNav.SECTIONS) {
			HubNav.ingat(target, want);
		}
		if (next != null) {
			pilihSub(next, sub == null || sub.isEmpty() ? HubNav.subTerakhir(want) : sub);
			geometri();
			if (changed) {
				next.onShow(fokus);
			} else if (fokus != null) {
				next.fokus(fokus);
			}
		} else {
			subId = "";
			geometri();
		}
	}

	/** Shows sub {@code id} of {@code page} (first open sub when unknown or locked). */
	private void pilihSub(Page page, String id) {
		List<Page.Sub> subs = page.subs();
		if (subs == null || subs.isEmpty()) {
			subId = "";
			return;
		}
		Page.Sub chosen = null;
		for (Page.Sub sub : subs) {
			if (sub.id().equals(id) && sub.kunci() == null) {
				chosen = sub;
			}
		}
		if (chosen == null) {
			for (Page.Sub sub : subs) {
				if (sub.kunci() == null) {
					chosen = sub;
					break;
				}
			}
		}
		if (chosen == null) {
			chosen = subs.get(0);
		}
		subId = chosen.id();
		page.showSub(subId);
		HubNav.ingatSub(pageId, subId);
	}

	/** A coin was picked (click, Q/E): enters page {@code id} of the shown section unless it is locked. */
	private void pindahHalaman(String id) {
		if (id.equals(pageId)) {
			return;
		}
		Page p = page(id);
		if (p != null && p.kunci() != null) {
			krom.tolak(p.kunci());
			return;
		}
		tampilkan(section, id, "", null);
	}

	/** A sub chip was picked (click, ←/→). */
	private void gantiSub(String id) {
		if (shown != null && !id.equals(subId)) {
			pilihSub(shown, id);
			geometri();
		}
	}

	private void stepPage(int step) {
		if (ids.size() < 2) {
			return;
		}
		int index = Math.max(0, ids.indexOf(pageId));
		for (int i = 1; i < ids.size(); i++) {
			String id = ids.get(Math.floorMod(index + step * i, ids.size()));
			Page p = page(id);
			if (p != null && p.kunci() == null) {
				UiSound.tab();
				tampilkan(section, id, "", null);
				return;
			}
		}
	}

	private boolean stepSub(int step) {
		String next = subBar.tetangga(subId, step);
		if (next == null || shown == null || shown.subs().isEmpty()) {
			return false;
		}
		UiSound.tab();
		gantiSub(next);
		return true;
	}

	/** Esc or the back control: the page's detail view first, then back from the Ensiklopedia to where "?" was pressed. */
	private boolean mundurHalaman() {
		if (shown != null && shown.mundur()) {
			return true;
		}
		if (balikBantuan()) {
			Tujuan asal = asalBantuan;
			asalBantuan = null;
			tampilkan(asal.bagian(), asal.halaman(), asal.sub(), null);
			return true;
		}
		return false;
	}

	/** True while the Ensiklopedia was reached by a "?" seal and can step back there. */
	private boolean balikBantuan() {
		return asalBantuan != null && Urutan.ID_ENSIKLOPEDIA.equals(pageId) && section == HubNav.PANDUAN;
	}

	/** The "?" seal: Panduan › Ensiklopedia on the shown page's topic, remembering this place for the way back. */
	private void bukaBantuan() {
		Tujuan asal = balikBantuan() ? asalBantuan : tempat();
		HubNav.help(helpTopic());
		if (!(asal.bagian() == HubNav.PANDUAN && Urutan.ID_ENSIKLOPEDIA.equals(asal.halaman()))) {
			asalBantuan = asal;
		}
	}

	// ================================================================== services for pages

	/** Shows {@code lines} as the tooltip of this frame (drawn last, on top of everything). */
	void showTooltip(List<Component> lines) {
		SlipTooltip.antre(lines);
	}

	/** The footer message for 6 s; {@code warna} an RGB/ARGB colour. */
	public void pesan(Component text, int warna) {
		pesan = text;
		pesanWarna = Palette.opaque(warna);
		pesanSampai = Util.getMillis() + PESAN_MILLIS;
	}

	private static Component sectionName(int index) {
		if (index == HubNav.DI_SINI) {
			return konteksJudul.getString().isEmpty() ? Component.translatable(H + "di_sini") : konteksJudul;
		}
		return Component.translatable("kultivator.hub.section." + SECTION_KEYS[index]);
	}

	private static int sectionGlyph(int index) {
		return index == HubNav.DI_SINI ? konteksGlyph : SECTION_GLYPHS[index];
	}

	// ================================================================== layout

	@Override
	protected void init() {
		boolean first = !opened;
		if (first) {
			opened = true;
			openedAt = Util.getMillis();
			lastFrame = openedAt;
			// A context that arrived while a container or chat was open (43.8.1.5) pulses once the tablets slide in.
			if (konteks != null && konteksSejak < openedAt) {
				konteksSejak = openedAt + 150L;
			}
			UiSound.open();
			Keys.send(Payloads.Action.of(Actions.REQUEST_SYNC));
		}
		sempit = width < 400 || height < 240;
		subH = sempit ? SUB_H_SEMPIT : SUB_H;
		headH = sempit ? HEAD_H_SEMPIT : HEAD_H;
		int ph = Math.min(MAX_H, height - 8);
		// Named tablets: glyph and gaps take 24 px (TabBar.tablet), the rest is the longest section name in bold.
		int nama = 0;
		for (int i = 0; i < HubNav.SECTIONS; i++) {
			nama = Math.max(nama, font.width(Brush.bold(sectionName(i))));
		}
		int railNama = Math.min(RAIL_W_NAMA_MAKS, nama + 24);
		bernama = !sempit && ph >= NAMED_TABLETS_MIN && width - railNama - 12 >= MAX_W;
		railW = sempit ? RAIL_W_SEMPIT : bernama ? railNama : RAIL_W;
		laid.clear();
		if (first) {
			tampilkan(section, pageId, subTunda, fokusTunda);
			fokusTunda = null;
			subTunda = "";
		} else {
			geometri();
		}
		if (helpSeal == null) {
			helpSeal = new KeyHints.HelpSeal(0.0F, 0.0F, SEAL, this::helpTopic);
		}
		seenVersion = ClientCultivation.version();
	}

	/** The paper and content box for the shown page (wide paper, sub bar), and its layout when the box changed. */
	private void geometri() {
		wide = shown != null && shown.lebar();
		int maxW = wide ? WIDE_W : MAX_W;
		int maxH = wide ? WIDE_H : MAX_H;
		int pw = Math.max(120, Math.min(maxW, width - railW - 12));
		int ph = Math.max(100, Math.min(maxH, height - 8));
		int x0 = (width - pw + railW) / 2;
		int top = (height - ph) / 2;
		if (!Float.isNaN(animX0) && (x0 != paperX0 || x0 + pw != paperX1)) {
			animFromX0 = currentX0();
			animFromX1 = currentX1();
			animAt = Util.getMillis();
		}
		paperX0 = x0;
		paperX1 = x0 + pw;
		paperTop = top;
		paperBottom = top + ph;
		if (Float.isNaN(animX0)) {
			animFromX0 = paperX0;
			animFromX1 = paperX1;
			animAt = 0L;
		}
		animX0 = paperX0;
		List<Page.Sub> subs = shown == null ? null : shown.subs();
		hasSubs = subs != null && !subs.isEmpty();
		headY = paperTop + ScrollFrame.END + 2;
		subY = headY + headH + 1;
		contentX = paperX0 + ScrollFrame.SIDE + 6;
		contentW = paperX1 - ScrollFrame.SIDE - 6 - contentX;
		contentY = headY + headH + (hasSubs ? subH + 2 : 1) + 2;
		contentH = paperBottom - ScrollFrame.END - FOOT_H - 2 - contentY;
		if (shown != null) {
			String box = contentX + "," + contentY + "," + contentW + "," + contentH;
			if (!box.equals(laid.get(shown))) {
				laid.put(shown, box);
				shown.layout(contentX, contentY, contentW, contentH);
			}
		}
	}

	private float currentX0() {
		float t = animAt == 0L ? 1.0F : Math.min(1.0F, (Util.getMillis() - animAt) / (float) WIDE_MILLIS);
		return animFromX0 + (paperX0 - animFromX0) * Eased.out(t);
	}

	private float currentX1() {
		float t = animAt == 0L ? 1.0F : Math.min(1.0F, (Util.getMillis() - animAt) / (float) WIDE_MILLIS);
		return animFromX1 + (paperX1 - animFromX1) * Eased.out(t);
	}

	private String helpTopic() {
		String topic = shown == null ? null : shown.helpTopic();
		return topic == null || topic.isEmpty() ? "jalan" : topic;
	}

	private static Component sumberLabel(Page.Sumber sumber) {
		int index = Math.max(0, Math.min(sumber.pilihan().size() - 1, sumber.aktif().getAsInt()));
		return Component.translatable(H + "sumber", sumber.pilihan().get(index));
	}

	private float unroll() {
		return Eased.out(Math.min(1.0F, (Util.getMillis() - openedAt) / (float) UNROLL_MILLIS));
	}

	// ================================================================== drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		float fade = backdrop.to(1.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F), 0.0F);
		int tint = warnaLangit();
		int sky = tint == 0 ? Palette.NIGHT : Palette.mix(Palette.NIGHT, tint, 0.22F);
		int mist = tint == 0 ? Palette.NIGHT_MIST : Palette.mix(Palette.NIGHT_MIST, tint, 0.32F);
		g.fillGradient(0, 0, width, height, Palette.argb(Math.round(0xC8 * fade), sky), Palette.argb(Math.round(0xE0 * fade), mist));
		// Distant ink-wash mountains along the bottom.
		int base = height;
		int step = Math.max(2, width / 160);
		int far = Palette.argb(Math.round(0x70 * fade), Palette.MOUNTAIN);
		int close = Palette.argb(Math.round(0x90 * fade), Palette.NIGHT);
		for (int x = 0; x < width; x += step) {
			double ridge = Math.sin(x * 0.021D) * 14.0D + Math.sin(x * 0.053D + 1.7D) * 7.0D + Math.sin(x * 0.11D + 0.4D) * 2.5D;
			int peak = (int) Math.round(height * 0.2D + ridge);
			double near = Math.sin(x * 0.034D + 3.1D) * 9.0D + Math.sin(x * 0.08D) * 4.0D;
			int low = Math.max(0, (int) Math.round(height * 0.1D + near));
			// The far range only above the near one, so the two never overlap (no doubled shade).
			if (peak > low) {
				g.fill(x, base - peak, x + step, base - low, far);
			}
			g.fill(x, base - low, x + step, base, close);
		}
		g.nextStratum();
		// A pale moon behind the scroll.
		Brush.halo(g, width * 0.82F, height * 0.18F, 9.0F, 8.0F, 0xE8EEF5, 0.35F * fade);
		Brush.disc(g, width * 0.82F, height * 0.18F, 9.0F, Palette.argb(Math.round(0x90 * fade), 0xE8EEF5));
		minecraft.gui.hud.extractDeferredSubtitles();
	}

	/**
	 * 43.3.1: the backdrop varies by colour only: the shown page's own {@code Palette.Gaya} (a lantern-lit town counter, a
	 * stone ruin), dusk for Titah Langit, the immortal sky for Keabadian; 0 = the plain ink night.
	 */
	private int warnaLangit() {
		if (shown != null && shown.gaya() != Palette.Gaya.KERTAS) {
			return shown.gaya().aksen;
		}
		if (Urutan.ID_TITAH.equals(pageId)) {
			return 0xFFC8703C;
		}
		return section == HubNav.KEABADIAN ? 0xFF6A4CA8 : 0;
	}

	/** Once per frame: registry or context changes refresh the coins; sub bar / wide paper changes relayout; new data reaches the page. */
	private void segarkan() {
		if (konteks == null && section == HubNav.DI_SINI) {
			shown = null;
			tampilkan(HubNav.lastSection(), "", "", null);
		}
		List<String> now = halaman(section);
		if (!now.equals(ids)) {
			ids = now;
			if (!ids.contains(pageId)) {
				tampilkan(section, "", "", null);
			}
		}
		if (shown != null) {
			List<Page.Sub> subs = shown.subs();
			boolean adaSub = subs != null && !subs.isEmpty();
			if (adaSub != hasSubs || shown.lebar() != wide) {
				geometri();
			}
		}
		long version = ClientCultivation.version();
		if (version != seenVersion) {
			seenVersion = version;
			if (shown != null) {
				if (shown.needsRebuild()) {
					laid.remove(shown);
					geometri();
				} else {
					shown.onData();
				}
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		SlipTooltip.mulaiBingkai();
		super.extractRenderState(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		float seconds = Math.max(0.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F));
		lastFrame = now;
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		segarkan();

		float open = unroll();
		int mid = (paperTop + paperBottom) / 2;
		int half = Math.round((paperBottom - paperTop) / 2.0F * open);
		int top = mid - half;
		int bottom = mid + half;
		int x0 = Math.round(currentX0());
		int x1 = Math.round(currentX1());

		Lukis l = krom.mulai(g, font, mouseX, mouseY, 1.0F, Palette.Gaya.KERTAS, 0, 0, width, height);
		// Tablets hang behind the paper and slide out once it has opened (zones follow the drawn place).
		float tabletsOut = Eased.out(Math.min(1.0F, Math.max(0.0F, (now - openedAt - 150L) / 220.0F)));
		drawRail(l, x0 - railW + 1 + Math.round((1.0F - tabletsOut) * (railW - 2)), now);
		g.nextStratum();

		ScrollFrame.paper(g, x0, x1, top, bottom, paperTop, paperBottom);
		ScrollFrame.rod(g, x0, x1, top);
		ScrollFrame.rod(g, x0, x1, bottom);
		g.nextStratum();

		if (bottom - top > 24) {
			g.enableScissor(x0 + ScrollFrame.SIDE + 1, top + ScrollFrame.END + 1, x1 - ScrollFrame.SIDE - 1, bottom - ScrollFrame.END - 1);
			drawHeader(l, seconds);
			if (hasSubs) {
				drawSubs(l);
			}
			if (shown != null) {
				shown.render(g, mouseX, mouseY, seconds);
			} else {
				Brush.fitCentered(g, font, Component.translatable(H + "kosong"), contentX + contentW / 2.0F, contentY + contentH / 2.0F - 4.0F,
						contentW - 8.0F, Palette.INK_FAINT, 1.0F, 1.0F, false);
			}
			drawFooter(g);
			g.disableScissor();
		}
		krom.selesai(l, mouseX, mouseY);
		if (open >= 1.0F) {
			g.nextStratum();
			SlipTooltip.gambarAntrean(g, font, mouseX, mouseY, width, height);
		} else {
			SlipTooltip.mulaiBingkai();
		}
	}

	/** The rail: Di Sini on top while a context lives (it glows once when it appears), then the eight sections. */
	private void drawRail(Lukis l, int railX0, long now) {
		List<TabBar.Tab> tabs = new ArrayList<>(HubNav.SECTIONS + 1);
		List<Integer> order = new ArrayList<>(HubNav.SECTIONS + 1);
		if (konteks != null) {
			order.add(HubNav.DI_SINI);
		}
		for (int i = 0; i < HubNav.SECTIONS; i++) {
			order.add(i);
		}
		for (int index : order) {
			tabs.add(TabBar.Tab.of(String.valueOf(index), sectionName(index), sectionGlyph(index)));
		}
		rel.setTabs(tabs);
		int y0 = paperTop + 2;
		int y1 = paperBottom - 2;
		int railX1 = railX0 + railW;
		int per = TabBar.tinggiTablet(y1 - y0, order.size());
		boolean muat = per * order.size() <= y1 - y0;
		if (konteks != null && now >= konteksSejak && now - konteksSejak < 900L && muat) {
			float pulse = (float) Math.sin((now - konteksSejak) / 900.0F * Math.PI);
			Brush.halo(l.g, (railX0 + railX1) / 2.0F, y0 + per / 2.0F, railW / 2.0F, 4.0F, 0xFFE07A, pulse);
		}
		rel.gambar(l, "rel", railX0, y0, railX1, y1, true, TabBar.Rupa.TABLET, bernama, String.valueOf(section),
				id -> tampilkan(Integer.parseInt(id), "", "", null));
		// The richer tooltip of a tablet: its name, what lives there and its number key.
		if (muat && l.di(railX0, y0, railX1, y0 + per * order.size())) {
			int index = order.get(Math.max(0, Math.min(order.size() - 1, (l.my - y0) / per)));
			String key = index == HubNav.DI_SINI ? "9" : String.valueOf(index + 1);
			Component tip = index == HubNav.DI_SINI ? Component.translatable(H + "di_sini.tip")
					: Component.translatable("kultivator.hub.section." + SECTION_KEYS[index] + ".tip");
			l.tip(List.of(Brush.bold(sectionName(index)), tip.copy().withStyle(ChatFormatting.GRAY),
					Component.translatable(H + "tombol_angka", key).withStyle(ChatFormatting.DARK_GREEN)));
		}
	}

	/** The header row: back control, section name, page coins (the shown one in gold), purse, "?" seal. */
	private void drawHeader(Lukis l, float seconds) {
		int y0 = headY;
		int y1 = headY + headH;
		float textY = y0 + (headH - 8) / 2.0F;
		int left = contentX;
		int right = contentX + contentW;
		// "?" seal at the right end: Panduan › Ensiklopedia on the page's topic.
		int sealX0 = right - SEAL_BOX;
		if (helpSeal != null) {
			helpSeal.moveTo(sealX0 + SEAL_BOX / 2.0F, y0 + headH / 2.0F);
			helpSeal.render(l.g, font, mouseX, mouseY, seconds);
			l.zona("bantuan", sealX0, y0, right, y1, this::bukaBantuan, true, null, false);
			if (l.di(sealX0, y0, right, y1)) {
				l.tip(helpSeal.tooltipLines());
			}
		}
		int end = sealX0 - 3;
		long uang = shown == null ? -1L : shown.uang();
		if (uang >= 0L) {
			end -= LabelHarga.gulir(l, uangGulir, uang, end, textY, true) + 4;
		}
		if (shown != null && shown.rinci() || balikBantuan()) {
			int bw = TombolKembali.gambar(l, "kembali", left, y0 + (headH - 12) / 2, Component.empty(), this::mundurHalaman);
			if (l.di(left, y0, left + bw, y1)) {
				l.tip(Component.translatable(H + "kembali"));
			}
			left += bw + 3;
		}
		if (ids.size() <= 1) {
			// Di Sini's name already reads "place · NPC" (43.8.1): the page title would repeat it.
			Component text = shown == null || section == HubNav.DI_SINI ? sectionName(section) : Component.translatable(H + "judul", sectionName(section), shown.title());
			l.teks(Brush.bold(text), left + 2, textY, end - left - 2, Palette.SEAL_DARK);
		} else {
			List<TabBar.Tab> tabs = new ArrayList<>(ids.size());
			int want = 0;
			for (String id : ids) {
				Page p = page(id);
				Component name = p == null ? Component.literal(id) : p.shortName();
				int glyph = p == null ? -1 : p.glyph();
				tabs.add(new TabBar.Tab(id, name, glyph, p == null ? null : p.kunci(), true));
				want += font.width(Brush.bold(name)) + 14;
			}
			Component sec = Brush.bold(sectionName(section));
			int secW = font.width(sec) + 8;
			if (!sempit && end - left - secW >= want) {
				l.teks(sec, left + 2, textY, secW - 4, Palette.SEAL_DARK);
				left += secW;
			}
			koin.setTabs(tabs);
			koin.gambar(l, "koin", left, y0, end, y1, false, TabBar.Rupa.KOIN, !sempit, pageId, this::pindahHalaman);
		}
		Brush.rect(l.g, contentX, y1, contentX + contentW, y1 + 0.6F, Palette.argb(0xB0, Palette.GOLD));
	}

	/** The sub bar: one chip per sub (the shown one underlined), the Sumber chip at its right. */
	private void drawSubs(Lukis l) {
		if (shown == null) {
			return;
		}
		int y0 = subY;
		int y1 = subY + subH;
		int sx1 = contentX + contentW;
		Page.Sumber sumber = shown.sumber();
		if (sumber != null && sumber.pilihan() != null && !sumber.pilihan().isEmpty()) {
			Component label = sumberLabel(sumber);
			int x1 = sx1;
			int x0 = Math.max(contentX + contentW / 2, x1 - font.width(label) - 10);
			boolean over = l.di(x0, y0, x1, y1);
			Brush.rect(l.g, x0, y0, x1, y1, over ? Palette.argb(0x70, Palette.TALISMAN) : Palette.argb(0x48, Palette.TALISMAN));
			l.teks(label, x0 + 4, y0 + (subH - 8) / 2.0F, x1 - x0 - 6, Palette.INK);
			l.zona("sumber", x0, y0, x1, y1, () -> gantiSumber(1), true, null, false);
			if (over) {
				l.tip(Component.translatable(H + "sumber.tip"));
			}
			sx1 = x0 - 4;
		}
		List<TabBar.Tab> tabs = new ArrayList<>();
		for (Page.Sub sub : shown.subs()) {
			tabs.add(new TabBar.Tab(sub.id(), sub.nama(), -1, sub.kunci(), true));
		}
		subBar.setTabs(tabs);
		subBar.gambar(l, "sub", contentX, y0, sx1, y1, false, TabBar.Rupa.SUB, true, subId, this::gantiSub);
		Brush.rect(l.g, contentX, y1 + 0.5F, contentX + contentW, y1 + 1.0F, Palette.argb(0x60, Palette.JADE_DULL));
	}

	/** Steps the Sumber chip of the shown page by {@code step} (click, Shift+←/→); false when it has no choice. */
	private boolean gantiSumber(int step) {
		Page.Sumber sumber = shown == null || !hasSubs ? null : shown.sumber();
		if (sumber == null || sumber.pilihan() == null || sumber.pilihan().size() < 2) {
			return false;
		}
		UiSound.tab();
		sumber.pilih().accept(Math.floorMod(sumber.aktif().getAsInt() + step, sumber.pilihan().size()));
		return true;
	}

	/** The footer: the last message for 6 s, else the key hints (not in the narrow layout). */
	private void drawFooter(GuiGraphicsExtractor g) {
		float y = paperBottom - ScrollFrame.END - FOOT_H + 1.0F;
		Brush.rect(g, contentX, y - 1.5F, contentX + contentW, y - 1.0F, Palette.argb(0x60, Palette.JADE_DULL));
		if (pesan != null && Util.getMillis() < pesanSampai) {
			Brush.fit(g, font, pesan, contentX + 2.0F, y, contentW - 4.0F, pesanWarna, 1.0F, 1.0F, false);
		} else if (!sempit) {
			Brush.fit(g, font, KeyHints.fill(Component.translatable(H + "petunjuk")), contentX + 2.0F, y, contentW - 4.0F, Palette.INK_FAINT, 1.0F,
					1.0F, false);
		}
	}

	private boolean inContent(double x, double y) {
		return x >= contentX && x < contentX + contentW && y >= contentY && y < contentY + contentH;
	}

	// ================================================================== input

	/** True while the shown page's confirm seal takes every input (43.4.1 step 1). */
	private boolean modal() {
		return shown != null && shown.modal();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (modal()) {
			return shown.mouseClicked(event, doubleClick);
		}
		if (shown != null && inContent(event.x(), event.y())) {
			if (shown.mouseClicked(event, doubleClick)) {
				return true;
			}
		} else if (krom.klik(event.x(), event.y(), event.button())) {
			// The chrome is driven by the Gulungan keys (1-9, Q/E, ←/→), not by a Tab ring: no focus ring stays on it.
			krom.set(null);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (!modal() && krom.lepas(event.x(), event.y())) {
			return true;
		}
		if (shown != null && shown.mouseReleased(event)) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!modal() && krom.seret(event.x(), event.y(), dx)) {
			return true;
		}
		if (shown != null && shown.mouseDragged(event, dx, dy)) {
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (shown != null && (modal() || inContent(x, y)) && shown.mouseScrolled(x, y, scrollX, scrollY)) {
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		Page page = shown;
		// 1-2. A modal seal or a writing text field gets every key first (43.4.1). While a field writes only Esc reaches the
		// Gulungan: letter and number keys (V, Q, E, 1-9, K, M ...) are text there, their character follows in charTyped.
		if (page != null && page.mauTombol()) {
			if (page.keyPressed(event) || modal()) {
				return true;
			}
			if (!event.isEscape()) {
				return false;
			}
		}
		// 3. The Gulungan keys (43.4.2).
		if (event.isEscape()) {
			if (mundurHalaman()) {
				UiSound.press();
				return true;
			}
			onClose();
			return true;
		}
		if (Keys.QUICK.matches(event) || Keys.INFO.matches(event)) {
			HubNav.tutupSemua();
			return true;
		}
		int key = event.key();
		if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9 && !event.hasControlDown() && !event.hasAltDown()) {
			int number = key - InputConstants.KEY_1;
			// K3 (43.17): on the page holding the live context (Di Sini or its home) a numbered choice it drew takes the number.
			if (memegangKonteks() && page != null && page.angka(number + 1)) {
				return true;
			}
			int target = number == HubNav.DI_SINI ? HubNav.DI_SINI : number;
			if (target == HubNav.DI_SINI && konteks == null) {
				return true;
			}
			if (target != section) {
				UiSound.tab();
				tampilkan(target, "", "", null);
			}
			return true;
		}
		if (key == InputConstants.KEY_Q && !event.hasControlDown()) {
			stepPage(-1);
			return true;
		}
		if (key == InputConstants.KEY_E && !event.hasControlDown()) {
			stepPage(1);
			return true;
		}
		if (key == InputConstants.KEY_TAB) {
			if (page != null) {
				page.fokusTab(event.hasShiftDown());
			}
			return true;
		}
		if (sectionKey(event)) {
			return true;
		}
		// 43.0.7: the "?" seal (F1) and the Sumber chip (Shift+←/→) are reachable from the keyboard too.
		if (key == InputConstants.KEY_F1 && helpSeal != null) {
			UiSound.press();
			bukaBantuan();
			return true;
		}
		if ((event.isLeft() || event.isRight()) && event.hasShiftDown() && gantiSumber(event.isLeft() ? -1 : 1)) {
			return true;
		}
		// 43.8.5: the ring key opens the bound ring from the Gulungan too (RingScreen returns here itself).
		if (Keys.CINCIN.matches(event)) {
			Keys.send(new Payloads.Action(Actions.TEMPA, 0, "ring:open"));
			return true;
		}
		// 4. The page (focus, Enter/Space, arrows, "/", its own keys); ←/→ it does not use switch the sub.
		if (page != null && page.keyPressed(event)) {
			return true;
		}
		if (event.isLeft() || event.isRight()) {
			return stepSub(event.isLeft() ? -1 : 1);
		}
		return super.keyPressed(event);
	}

	/** A section key (K Kodeks, M Murid, Dunia, Abadi, J Panduan, N Peta): go there; pressed where it already is: close. */
	private boolean sectionKey(KeyEvent event) {
		Tujuan place = HubNav.tombolBagian(event);
		if (place == null) {
			return false;
		}
		boolean here = place.bagian() == section && (place.halaman().isEmpty() || place.halaman().equals(pageId));
		if (here) {
			onClose();
		} else {
			HubNav.bukaTombol(event);
		}
		return true;
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (shown != null && shown.keyReleased(event)) {
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (shown != null && shown.charTyped(event)) {
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public void tick() {
		if (shown != null) {
			shown.tick();
		}
		// A context out of sight still ticks: its reach check ends it when the player walks away (43.8.1.4).
		if (konteks != null && !memegangKonteks()) {
			konteks.tick();
		}
	}

	@Override
	public void removed() {
		UiSound.close();
		if (shown != null && shown != konteks) {
			shown.onHide();
		}
		// The Gulungan closed: a world context ends with it (43.8.1.4, OP_TUTUP from the page), unless the Gulungan only
		// gave way to a screen that comes back here (ring, chat: 43.8.5).
		if (HubNav.menungguKembali()) {
			shown = null;
			super.removed();
			return;
		}
		Page ctx = konteks;
		konteks = null;
		if (ctx != null) {
			ctx.onHide();
		}
		shown = null;
		super.removed();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
