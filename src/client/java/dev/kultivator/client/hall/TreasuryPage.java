package dev.kultivator.client.hall;

import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.LembarHub;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.PageKomposit;
import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihOrang;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.sekte.gabung.GabungClient;
import dev.kultivator.client.sekte.mandiri.MandiriClient;
import dev.kultivator.client.sekte.paviliun.PaviliunClient;
import dev.kultivator.client.sekte.paviliun.PaviliunScreen;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.ui.LembarKunci.BukuKas;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.sekte.paviliun.PaviliunPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sekte › Kas &amp; Toko (DESIGN 43.5 page {@code kas}; G38, G39, G56, G66, G67): one page for every money place of a
 * sect, registered by {@code UiHub}. Subs ({@code *} own hall only, {@code +} member of an NPC sect only):
 * <ul>
 * <li><b>kas</b>: one view at a time, picked with filter chips (a filter at the third level, 43.0.4): "Kantor Misi" (the
 * hall's PK pool and the week's in/out, today's offerings against the cap, every offerable stack of the inventory as a card
 * that offers it on click, the lost sect arts with "Pulihkan"), the ledger {@code buku_kas} views Hari ini / 7 hari /
 * Sumber*, Politik Kas+ ({@code gabung_politik} tab Kas) and Kas Mandiri* (the independent disciples' pocket money and
 * money book, from the {@code mandiri_papan} data that {@code HallClient.papanMandiri} hands over; was the Kas tab of the
 * MandiriBoard);</li>
 * <li><b>toko_pk</b>: the sect shop for PK. Sumber Aulaku = the hall's Paviliun Yue Lu (wares, pickers for the disciple and
 * the jade slip, "Beli"); Sumber Perguruanku = the Paviliun Jasa sheet ({@code gabung_paviliun}) with its goods kinds as
 * chips (Kediaman lives in Diri › Harta, G66). The Sumber chip shows only when both exist (43.2.1);</li>
 * <li><b>rak</b>*, <b>makam</b>*: module A's Rak Kitab and Makam Leluhur ({@link PaviliunScreen});</li>
 * <li><b>kafilah</b>*: the ledger's caravan tab.</li>
 * </ul>
 * The sheets are shown read-only from here ({@code OP_LIHAT}) through the sheet mounter's pages ({@link LembarHub}); a
 * world context of the same sheet (the ledger counter, the Paviliun Jasa steward) opens live under Di Sini. Data is asked
 * only while shown (43.0.12): the hall details through {@link HallClient#minta} (refreshed every 4 s while Sekte shows).
 */
public final class TreasuryPage extends PageKomposit {

	public static final String SUB_KAS = "kas";
	public static final String SUB_TOKO = "toko_pk";
	public static final String SUB_RAK = "rak";
	public static final String SUB_MAKAM = "makam";
	public static final String SUB_KAFILAH = "kafilah";
	private static final String V_KANTOR = "kantor";
	private static final String V_POLITIK = "politik";
	private static final String V_MANDIRI = "mandiri";
	/** {@link #tujuDunia}: the ledger came from the world (desk, MENTERI); the view follows the sheet's own tab. */
	private static final String V_BUKU = "buku";
	private static final String M = "kultivator.gui43.m08.";
	private static final String CHIP = "kas:chip";
	private static final int CARD_W = 24;
	private static final int CARD_H = 28;
	private static final int ROW_H = 16;
	private static final int BTN_H = 14;
	private static final int CHIP_H = 14;
	private static final String L_MANDIRI = "kultivator.sekte.mandiri.cerita.";

	/** One offerable stack (from the last details). */
	private record Offer(int slot, ItemStack stack, double unit) {
	}

	private static final Set<String> TAB_BUKU = Set.of(BukuKas.TAB_HARI_INI, BukuKas.TAB_MINGGU, BukuKas.TAB_SUMBER, BukuKas.TAB_KAFILAH);
	private final LembarHub.Halaman buku = lembar(BukuKas.LAYAR, TAB_BUKU);
	private static final Set<String> TAB_JASA = Set.of(GabungLembar.Paviliun.TAB_TEKNIK, GabungLembar.Paviliun.TAB_WARISAN,
			GabungLembar.Paviliun.TAB_PIL, GabungLembar.Paviliun.TAB_PUSAKA, GabungLembar.Paviliun.TAB_BAHAN, GabungLembar.Paviliun.TAB_KUOTA,
			GabungLembar.Paviliun.TAB_PESANAN, GabungLembar.Paviliun.TAB_CERAMAH);
	private static final Set<String> TAB_POLITIK = Set.of(GabungLembar.Politik.TAB_KAS);
	/** A world opener's wish for the next {@link #showSub}: {@link #V_POLITIK} or {@link #SUB_TOKO} (Perguruanku); null = none. */
	private static @Nullable String tujuDunia;

	private final LembarHub.Halaman jasa = lembar(GabungLembar.Paviliun.LAYAR, TAB_JASA);
	private final LembarHub.Halaman politik = lembar(GabungLembar.Politik.LAYAR, TAB_POLITIK);
	private final PaviliunScreen rak = new PaviliunScreen(PaviliunPayloads.PAGE_RAK);
	private final PaviliunScreen makam = new PaviliunScreen(PaviliunPayloads.PAGE_MAKAM);
	private final TabBar chips = new TabBar();
	/** Toko PK Aulaku: the receiving disciple, picked from a pop-up list (G35, as Balai Tetua / Tugas). */
	private final PemilihOrang pemilihUntuk = new PemilihOrang(0).onBuka(o -> {
		targetIndex = Integer.parseInt(o.id());
		tutupPemilih();
	});
	private Component labelKitab = Component.empty();
	/** Toko PK Aulaku: the jade slip, a cycling talisman (click next, right click / ← back: G08). */
	private final TalismanButton kitab = siklusKitab();
	private final List<Offer> offers = new ArrayList<>();

	private String sub = SUB_KAS;
	private String view = V_KANTOR;
	/** Toko PK source: 0 Aulaku, 1 Perguruanku. */
	private int sumber;
	private boolean terlihat;
	/** True while the keyboard focus is inside the shown sheet / sub page (Tab ring of the child). */
	private boolean diAnak;
	private @Nullable Page anak;
	private int anakY = Integer.MIN_VALUE;
	private int anakW;
	private int anakH;
	private long seenInfo = -1L;
	private final GulirIsi gulir = new GulirIsi();
	private int targetIndex;
	private int bookIndex;

	private TalismanButton siklusKitab() {
		TalismanButton b = new TalismanButton(0, 0, TalismanButton.MIN_W, BTN_H, () -> labelKitab).siklus(() -> bookIndex++, () -> bookIndex--);
		b.enabledWhen(() -> info().getListOrEmpty("books").size() > 1);
		return b;
	}

	private void tutupPemilih() {
		pemilihUntuk.tutup();
	}

	/**
	 * Homes of the NPC-sect sheets mounted here (43.12.4, P5): the Paviliun Jasa NPC lands on Toko PK (Perguruanku) and the
	 * treasury tab of the politics sheet on Kas › Politik Kas, with the live context, instead of under Di Sini.
	 */
	public static void daftarRumah() {
		HubPages.daftarRumah(GabungLembar.Paviliun.LAYAR, TAB_JASA, HubNav.SEKTE, Urutan.ID_KAS, SUB_TOKO, () -> tujuDunia = SUB_TOKO);
		HubPages.daftarRumah(GabungLembar.Politik.LAYAR, TAB_POLITIK, HubNav.SEKTE, Urutan.ID_KAS, SUB_KAS, () -> tujuDunia = V_POLITIK);
		// the ledger opened in the world (desk, Jasa MENTERI) lands here, not as a second ledger under Di Sini (G38)
		HubPages.daftarRumah(BukuKas.LAYAR, TAB_BUKU, HubNav.SEKTE, Urutan.ID_KAS, SUB_KAS, () -> tujuDunia = V_BUKU);
	}

	/** The tab the mounted ledger shows now ("" while it has no sheet). */
	private String tabBuku() {
		return buku.isi() instanceof LayarTempatDasar l ? l.tabSekarang() : "";
	}

	/** Applies {@link #tujuDunia}; true when it changed what the sub shows. */
	private boolean ambilTuju() {
		String t = tujuDunia;
		tujuDunia = null;
		if (V_POLITIK.equals(t)) {
			view = V_POLITIK;
			return true;
		}
		if (SUB_TOKO.equals(t)) {
			sumber = 1;
			return true;
		}
		if (V_BUKU.equals(t)) {
			if (bukuView(tabBuku())) {
				view = tabBuku();
			}
			return true;
		}
		return false;
	}

	private static LembarHub.Halaman lembar(String layar, Set<String> tab) {
		return LembarHub.dari(new HubPages.Entri(HubNav.SEKTE, Urutan.ID_KAS, Urutan.KAS, () -> null, layar, tab));
	}

	// ================================================================== who sees what

	private static CompoundTag info() {
		return HallClient.info();
	}

	private static boolean infoAda() {
		return HallClient.version() > 0L;
	}

	/** A hall is shown: the player's own, or another master's opened at its stele (read only). */
	private static boolean adaAula() {
		return info().getBooleanOr("has", false);
	}

	private static boolean readonly() {
		return info().getBooleanOr("readonly", false);
	}

	/** The player's own hall (assumed until the first details arrive, so the subs do not jump). */
	private static boolean milikAula() {
		return !infoAda() || adaAula() && !readonly();
	}

	private static boolean anggota() {
		return GabungClient.lencanaSendiri() != null;
	}

	private static boolean tokoAula() {
		return !infoAda() || adaAula();
	}

	/** Toko PK shows the Paviliun Jasa (Perguruanku) rather than the hall's Yue Lu (Aulaku). */
	private boolean tokoJasa() {
		return anggota() && (!tokoAula() || sumber == 1);
	}

	// ================================================================== identity

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.sekte.kas");
	}

	@Override
	public int glyph() {
		return Glyphs.TREASURE;
	}

	@Override
	public String helpTopic() {
		return "sekte_kas";
	}

	@Override
	public boolean tampil() {
		return !infoAda() || adaAula() || anggota();
	}

	@Override
	public List<Sub> subs() {
		List<Sub> list = new ArrayList<>();
		list.add(new Sub(SUB_KAS, nama(SUB_KAS)));
		if (tokoAula() || anggota()) {
			list.add(new Sub(SUB_TOKO, nama(SUB_TOKO)));
		}
		if (milikAula() && PaviliunClient.aktif()) {
			list.add(new Sub(SUB_RAK, nama(SUB_RAK)));
			list.add(new Sub(SUB_MAKAM, nama(SUB_MAKAM)));
		}
		if (milikAula()) {
			list.add(new Sub(SUB_KAFILAH, nama(SUB_KAFILAH)));
		}
		return list;
	}

	private static Component nama(String id) {
		return Component.translatable("kultivator.gui43.sub.kas." + id);
	}

	@Override
	public void showSub(String diminta) {
		String id = V_BUKU.equals(tujuDunia) && BukuKas.TAB_KAFILAH.equals(tabBuku()) ? SUB_KAFILAH : diminta;
		boolean dituju = ambilTuju();
		if (id == null || id.equals(sub) && !dituju || subs().stream().noneMatch(s -> s.id().equals(id))) {
			return;
		}
		sub = id;
		gulir.atas();
		pasangAnak();
	}

	@Override
	public @Nullable Sumber sumber() {
		if (!SUB_TOKO.equals(sub) || !tokoAula() || !anggota()) {
			return null;
		}
		return new Sumber(List.of(Component.translatable(M + "sumber.aulaku"), Component.translatable(M + "sumber.perguruanku")), () -> sumber, i -> {
			sumber = i;
			gulir.atas();
			pasangAnak();
		});
	}

	// ================================================================== the views of sub kas

	private List<TabBar.Tab> views() {
		List<TabBar.Tab> list = new ArrayList<>();
		if (!infoAda() || adaAula()) {
			list.add(TabBar.Tab.of(V_KANTOR, Component.translatable(M + "v.kantor")));
		}
		if (milikAula()) {
			list.add(TabBar.Tab.of(BukuKas.TAB_HARI_INI, Component.translatable(M + "v.hari_ini")));
			list.add(TabBar.Tab.of(BukuKas.TAB_MINGGU, Component.translatable(M + "v.minggu")));
			list.add(TabBar.Tab.of(BukuKas.TAB_SUMBER, Component.translatable(M + "v.sumber")));
		}
		if (anggota()) {
			list.add(TabBar.Tab.of(V_POLITIK, Component.translatable(M + "v.politik")));
		}
		if (milikAula() && SekteContent.on(SekteIds.MANDIRI)) {
			list.add(TabBar.Tab.of(V_MANDIRI, Component.translatable(M + "v.mandiri")));
		}
		if (list.isEmpty()) {
			list.add(TabBar.Tab.of(V_KANTOR, Component.translatable(M + "v.kantor")));
		}
		return list;
	}

	private static boolean bukuView(String v) {
		return BukuKas.TAB_HARI_INI.equals(v) || BukuKas.TAB_MINGGU.equals(v) || BukuKas.TAB_SUMBER.equals(v);
	}

	/** The chips above the body: the views of sub kas, or the Paviliun Jasa's goods kinds; fewer than two = no chip row. */
	private List<TabBar.Tab> chipTabs() {
		if (SUB_KAS.equals(sub)) {
			return views();
		}
		if (SUB_TOKO.equals(sub) && tokoJasa()) {
			List<TabBar.Tab> list = new ArrayList<>();
			for (Sub s : jasa.subs()) {
				list.add(new TabBar.Tab(s.id(), s.nama(), -1, s.kunci(), true));
			}
			return list;
		}
		return List.of();
	}

	private String chipAktif() {
		if (SUB_KAS.equals(sub)) {
			return view;
		}
		return jasa.isi() instanceof LayarTempatDasar l ? l.tabSekarang() : "";
	}

	private void pilihChip(String id) {
		if (SUB_KAS.equals(sub)) {
			view = id;
			gulir.atas();
			pasangAnak();
			mintaMandiri();
		} else {
			jasa.showSub(id);
		}
	}

	private boolean adaChip() {
		return chipTabs().size() > 1;
	}

	// ================================================================== the child page of the sub / view

	@Override
	protected @Nullable Page anak() {
		return anak;
	}

	private @Nullable Page anakSekarang() {
		return switch (sub) {
			case SUB_TOKO -> tokoJasa() ? jasa : null;
			case SUB_RAK -> rak;
			case SUB_MAKAM -> makam;
			case SUB_KAFILAH -> buku;
			default -> bukuView(view) ? buku : V_POLITIK.equals(view) ? politik : null;
		};
	}

	/** Puts the page of the current sub / view in place (shown / hidden, laid out). */
	private void pasangAnak() {
		if (SUB_KAS.equals(sub) && views().stream().noneMatch(t -> t.id().equals(view))) {
			view = views().get(0).id();
		}
		Page baru = anakSekarang();
		if (baru == buku) {
			buku.showSub(SUB_KAFILAH.equals(sub) ? BukuKas.TAB_KAFILAH : view);
		}
		if (baru == anak) {
			return;
		}
		if (anak != null && terlihat) {
			anak.onHide(); // LembarHub.Halaman.onHide keeps a live Di Sini session (43.8.1.4)
		}
		anak = baru;
		diAnak = false;
		anakY = Integer.MIN_VALUE;
		if (anak != null) {
			if (screen != null) {
				anak.pasang(screen);
			}
			tataAnak();
			if (terlihat) {
				anak.onShow(null);
			}
		}
	}

	private int bodyY() {
		return adaChip() ? y + CHIP_H + 3 : y;
	}

	private void tataAnak() {
		if (anak == null || w <= 0) {
			return;
		}
		int by = bodyY();
		int bh = Math.max(20, y + h - by);
		if (by != anakY || w != anakW || bh != anakH) {
			anakY = by;
			anakW = w;
			anakH = bh;
			anak.layout(x, by, w, bh);
		}
	}

	// ================================================================== life

	@Override
	protected void build() {
		anakY = Integer.MIN_VALUE;
		if (anak != null && screen != null) {
			anak.pasang(screen);
		}
		tataAnak();
	}

	/** The Kas Mandiri view asks for the board data when it comes on screen. */
	private void mintaMandiri() {
		if (terlihat && SUB_KAS.equals(sub) && V_MANDIRI.equals(view)) {
			MandiriClient.aksi("papan", "", "", 0);
		}
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		HallClient.minta();
		terlihat = false;
		pasangAnak();
		terlihat = true;
		if (anak != null) {
			anak.onShow(null);
		}
		mintaMandiri();
		if (fokus != null) {
			fokus(fokus);
		}
	}

	@Override
	public void onHide() {
		terlihat = false;
		if (anak != null) {
			anak.onHide(); // LembarHub.Halaman.onHide keeps a live Di Sini session (43.8.1.4)
		}
	}

	@Override
	public void tick() {
		if (HallClient.version() != seenInfo) {
			seenInfo = HallClient.version();
			bacaTawaran();
			if (subs().stream().noneMatch(s -> s.id().equals(sub))) {
				// the sub went away (no hall after all, module off): back to Kas, and the Gulungan's sub bar with it
				sub = SUB_KAS;
				if (terlihat) {
					tuju(Tujuan.of(HubNav.SEKTE, Urutan.ID_KAS).sub(SUB_KAS));
				}
			}
			pasangAnak();
		}
		if (anak != null) {
			anak.tick();
		}
	}

	/** The offerable stacks of the last details. */
	private void bacaTawaran() {
		offers.clear();
		for (Tag tag : info().getListOrEmpty("offers")) {
			if (!(tag instanceof CompoundTag entry)) {
				continue;
			}
			Identifier id = Identifier.tryParse(entry.getStringOr("item", ""));
			Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
			if (item == Items.AIR) {
				continue;
			}
			ItemStack stack = new ItemStack(item, Math.max(1, entry.getIntOr("count", 1)));
			int quality = entry.getIntOr("quality", -1);
			if (quality >= 0) {
				AlchemyComponents.withQuality(stack, PillQuality.byOrdinal(quality));
			}
			offers.add(new Offer(entry.getIntOr("slot", -1), stack, entry.getDoubleOr("unit", 0.0D)));
		}
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (anak != null) {
			tataAnak();
			anak.render(g, mouseX, mouseY, seconds);
		}
	}

	@Override
	protected void lukis(Lukis l) {
		List<TabBar.Tab> tabs = chipTabs();
		if (tabs.size() > 1) {
			chips.setTabs(tabs);
			chips.gambar(l, CHIP, x, y, x + w, y + CHIP_H, false, TabBar.Rupa.SUB, true, chipAktif(), this::pilihChip);
		}
		if (anak != null) {
			return;
		}
		int top = bodyY();
		int bottom = y + h;
		if (SUB_TOKO.equals(sub)) {
			gulirIsi(l, top, bottom, this::yueLu);
		} else if (V_MANDIRI.equals(view)) {
			gulirIsi(l, top, bottom, this::kasMandiri);
		} else if (!infoAda()) {
			l.tengah(Component.translatable("kultivator.gui43.konteks.memuat"), x + w / 2.0F, (top + bottom) / 2.0F - 4.0F, w - 8, Palette.INK_FAINT);
		} else if (!adaAula()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable(M + "tanpa_aula"), null, null, x, top, x + w, bottom);
		} else {
			gulirIsi(l, top, bottom, this::kantor);
		}
		if (SUB_TOKO.equals(sub)) {
			pemilihUntuk.gambarSembul(l, "kas:pilih_untuk", Component.translatable("kultivator.hall.ui.for_disciple.judul"), orangUntuk());
		} else {
			tutupPemilih();
		}
	}

	/** The disciples of {@link #targets()} for the pop-up (id = index in that list). */
	private static List<PemilihOrang.Orang> orangUntuk() {
		List<CompoundTag> list = targets();
		List<PemilihOrang.Orang> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			out.add(PemilihOrang.Orang.of(String.valueOf(i), list.get(i).read("name", ComponentSerialization.CODEC)
					.orElse(Component.translatable("entity.kultivator.disciple")), Palette.GOLD_DARK));
		}
		return out;
	}

	/** A column painter: draws from y between x0 and x1, returns the y under it. */
	@FunctionalInterface
	private interface Kolom {
		int lukis(Lukis l, int x0, int y, int x1);
	}

	/** One scrolled column in the body (stacked headings, 43.0.4) with the jade bead of the scroll. */
	private void gulirIsi(Lukis l, int top, int bottom, Kolom kolom) {
		int atas = gulir.mulai(l, x, top, x + w - 5, bottom) + 2;
		gulir.selesai(l, kolom.lukis(l, x + 2, atas, x + w - 7) + 2);
	}

	/** The pool line: the hall's PK and the week's in/out (S28). */
	private static Component poolLine(CompoundTag info) {
		Component line = Brush.bold(Component.translatable("kultivator.hall.ui.pool", info.getIntOr("pool", 0)));
		int[] week = info.getIntArray("pool_week").orElse(new int[0]);
		if (week.length >= 2) {
			line = Component.empty().append(line).append(Component.literal("  "))
					.append(Component.translatable("kultivator.sekte_sambung.kas.minggu", week[0], week[1]).withStyle(ChatFormatting.DARK_GRAY));
		}
		return line;
	}

	/** Kantor Misi: the pool, today's offerings, the offer cards (one click offers), the lost arts. */
	private int kantor(Lukis l, int x0, int yy, int x1) {
		CompoundTag info = info();
		int w0 = x1 - x0;
		boolean owner = !readonly();
		l.teks(poolLine(info), x0, yy, w0, Palette.GOLD_DARK);
		yy += 11;
		int offered = info.getIntOr("offered_today", 0);
		int cap = Math.max(1, info.getIntOr("offer_cap", 300));
		yy += Bilah.baris(l, Component.translatable("kultivator.hall.ui.offered"), offered, cap, x0, yy, x1, Component.literal(offered + "/" + cap),
				offered >= cap ? Palette.WARN : Palette.JADE) + 3;
		yy += l.kepala(Component.translatable("kultivator.hall.ui.kantor"), x0, yy, x1);
		yy += Math.round(l.bungkus(Component.translatable("kultivator.hall.ui.kantor.hint"), x0, yy, w0, l.gaya.teksSamar)) + 2;
		if (offers.isEmpty()) {
			yy += Math.round(l.bungkus(Component.translatable("kultivator.hall.ui.kantor.empty"), x0 + 2, yy, w0 - 4, l.gaya.teksSamar)) + 2;
		}
		int cols = Math.max(1, w0 / (CARD_W + 2));
		for (int i = 0; i < offers.size(); i++) {
			Offer offer = offers.get(i);
			int cx = x0 + (i % cols) * (CARD_W + 2);
			int cy = yy + (i / cols) * (CARD_H + 2);
			String kunci = "kas:tawar" + i;
			boolean over = l.di(cx, cy, cx + CARD_W, cy + CARD_H);
			Brush.chamferGradient(l.g, cx, cy, cx + CARD_W, cy + CARD_H, 2, over ? 0xFFFBF2D8 : 0xFFF1E6C6, 0xFFE0D0A6);
			Brush.frame(l.g, cx, cy, cx + CARD_W, cy + CARD_H, Palette.argb(over ? 0xF0 : 0xA0, Palette.GOLD));
			l.g.item(offer.stack(), cx + (CARD_W - 16) / 2, cy + 2);
			l.g.itemDecorations(font, offer.stack(), cx + (CARD_W - 16) / 2, cy + 2);
			int value = (int) Math.floor(offer.unit() * offer.stack().getCount() + 1.0E-6D);
			l.tengah(Component.literal("+" + value), cx + CARD_W / 2.0F, cy + CARD_H - 9, CARD_W, Palette.JADE_DARK);
			int slot = offer.slot();
			l.zona(kunci, cx, cy, cx + CARD_W, cy + CARD_H, () -> {
				UiSound.press();
				HallClient.send(Halls.OP_OFFER, String.valueOf(slot));
			}, true, null, true);
			l.fokusCincin(kunci, cx, cy, cx + CARD_W, cy + CARD_H);
			if (over) {
				l.tip(List.of(offer.stack().getHoverName().copy().append(Component.literal(" ×" + offer.stack().getCount())),
						Component.translatable("kultivator.hall.ui.offer_value", formatUnit(offer.unit()), value).withStyle(ChatFormatting.AQUA),
						Component.translatable(owner ? "kultivator.hall.ui.offer_click" : "kultivator.hall.ui.offer_donate").withStyle(ChatFormatting.GRAY),
						Component.translatable("kultivator.hall.ui.kantor.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
			}
		}
		if (!offers.isEmpty()) {
			yy += ((offers.size() + cols - 1) / cols) * (CARD_H + 2) + 2;
		}
		// the lost arts
		yy += 4;
		yy += l.kepala(Component.translatable("kultivator.hall.ui.arts"), x0, yy, x1);
		int[] pages = info.getIntArray("pages").orElse(new int[3]);
		int arts = info.getIntOr("arts", 0);
		int bw = Math.min(70, w0 / 3);
		for (int set = 1; set <= 3; set++) {
			int art = set;
			boolean restored = (arts & 1 << (set - 1)) != 0;
			l.teks(Component.translatable("kultivator.hall.art." + set), x0, yy, w0 - bw - 40, restored ? Palette.JADE_DARK : l.gaya.teks);
			int bits = set - 1 < pages.length ? pages[set - 1] : 0;
			for (int piece = 0; piece < 5; piece++) {
				float px = x1 - bw - 34 + piece * 6.0F;
				boolean have = restored || (bits & 1 << piece) != 0;
				Brush.rect(l.g, px, yy + 2.0F, px + 4.5F, yy + 7.0F, have ? Palette.argb(0xFF, 0xC27AE0) : Palette.argb(0x50, Palette.INK_FAINT));
			}
			if (restored) {
				l.teks(Component.translatable("kultivator.hall.ui.art_restored"), x1 - bw, yy, bw, Palette.JADE_DARK);
			} else if (owner) {
				TalismanButton.gambar(l, "kas:pulih" + set, x1 - bw, yy - 2, bw, BTN_H, Component.translatable("kultivator.hall.ui.restore"), canRestore(art),
						Component.translatable("kultivator.hall.ui.art_pages", Integer.bitCount(bits), 5), () -> HallClient.send(Halls.OP_RESTORE, String.valueOf(art)),
						canRestore(art), false);
			}
			if (l.di(x0, yy - 2, x1, yy + ROW_H - 2)) {
				l.tip(artTip(art));
			}
			yy += ROW_H;
		}
		if (!info.getBooleanOr("pavilion", false)) {
			yy += Math.round(l.bungkus(Component.translatable("kultivator.hall.art.need_pavilion"), x0, yy, w0, l.gaya.teksSamar));
		}
		return yy;
	}

	/** Kas Mandiri: the pocket money of the last 7 days, the hall's contribution pool and the money book (newest data). */
	private int kasMandiri(Lukis l, int x0, int yy, int x1) {
		CompoundTag d = HallClient.papanMandiri();
		int w0 = x1 - x0;
		if (d == null) {
			l.samar(Component.translatable("kultivator.gui43.konteks.memuat"), x0, yy, w0);
			return yy + 10;
		}
		l.teks(Component.translatable(L_MANDIRI + "papan.kas.saku7", NilaiBatu.text(d.getLongOr("saku7", 0L))), x0, yy, w0, l.gaya.teks);
		yy += 10;
		l.teks(Component.translatable(L_MANDIRI + "papan.kas.pool", d.getIntOr("pool", 0)), x0, yy, w0, l.gaya.teksLemah);
		yy += 12;
		yy += l.kepala(Component.translatable(M + "v.mandiri"), x0, yy, x1);
		ListTag book = d.getListOrEmpty("kas");
		if (book.isEmpty()) {
			return yy + Math.round(l.bungkus(Component.translatable(L_MANDIRI + "papan.kas.kosong"), x0 + 2, yy, w0 - 4, l.gaya.teksSamar));
		}
		int dayW = 44;
		int sumW = 64;
		int whoW = Math.max(30, (w0 - dayW - sumW) * 2 / 5);
		for (int i = 0; i < book.size(); i++) {
			CompoundTag b = book.getCompoundOrEmpty(i);
			long amount = b.getLongOr("j", 0L);
			l.teks(Component.translatable(L_MANDIRI + "papan.kabar_hari", b.getLongOr("h", 0L) + 1), x0, yy, dayW - 4, l.gaya.teksSamar);
			l.teks(b.read("n", ComponentSerialization.CODEC).orElse(Component.literal("?")), x0 + dayW, yy, whoW - 4, l.gaya.teks);
			String sebab = b.getStringOr("s", "");
			String key = L_MANDIRI + "kas.sebab." + (sebab.indexOf(':') > 0 ? sebab.substring(0, sebab.indexOf(':')) : sebab);
			Component why = Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(sebab);
			l.teks(why, x0 + dayW + whoW, yy, w0 - dayW - whoW - sumW - 4, l.gaya.teksLemah);
			Component sum = Component.literal(amount > 0 ? "+" : amount < 0 ? "-" : "").append(NilaiBatu.text(Math.abs(amount)));
			l.kanan(sum, x1, yy, amount >= 0 ? Palette.JADE_DARK : Palette.SEAL_DARK);
			yy += 10;
		}
		return yy;
	}

	private static String formatUnit(double unit) {
		return unit == Math.floor(unit) ? String.valueOf((int) unit) : String.format(java.util.Locale.ROOT, "%.1f", unit);
	}

	/** Toko PK, Sumber Aulaku: the hall's Paviliun Yue Lu: the pool, the two pickers, one row per ware with "Beli". */
	private int yueLu(Lukis l, int x0, int yy, int x1) {
		CompoundTag info = info();
		int w0 = x1 - x0;
		if (infoAda() && !adaAula()) {
			l.samar(Component.translatable(M + "tanpa_aula"), x0, yy, w0);
			return yy + 10;
		}
		boolean owner = !readonly();
		l.teks(poolLine(info), x0, yy, w0, Palette.GOLD_DARK);
		yy += 12;
		if (owner) {
			CompoundTag target = target();
			Component untuk = Component.translatable("kultivator.hall.ui.for_disciple", target == null ? Component.translatable("kultivator.hall.ui.nobody")
					: target.read("name", ComponentSerialization.CODEC).orElse(Component.translatable("entity.kultivator.disciple")));
			TalismanButton.gambar(l, "kas:untuk", x0, yy, w0, BTN_H, untuk, targets().size() > 1, Component.translatable("kultivator.hall.ui.for_disciple.tip"),
					pemilihUntuk::bukaSembul);
			if (l.di(x0, yy, x1, yy + BTN_H)) {
				l.tip(Component.translatable("kultivator.hall.ui.for_disciple.tip"));
			}
			yy += BTN_H + 2;
			TechniqueSpec book = book();
			labelKitab = Component.translatable("kultivator.hall.ui.for_book", book == null ? Component.literal("-") : book.displayName());
			kitab.x = x0;
			kitab.y = yy;
			kitab.w = Math.max(TalismanButton.MIN_W, w0);
			kitab.h = BTN_H;
			l.widget("kas:kitab", kitab);
			if (l.di(x0, yy, x1, yy + BTN_H)) {
				l.tip(Component.translatable("kultivator.hall.ui.for_book.tip"));
			}
			yy += BTN_H + 3;
		}
		yy += l.kepala(Component.translatable(M + "toko_aula"), x0, yy, x1);
		int bw = 40;
		for (Tag tag : info.getListOrEmpty("shop")) {
			if (!(tag instanceof CompoundTag ware)) {
				continue;
			}
			String id = ware.getStringOr("id", "");
			boolean locked = ware.getBooleanOr("locked", false);
			Component price = locked ? Component.translatable("kultivator.hall.ui.level_short", ware.getIntOr("need", 1))
					: Component.literal(ware.getIntOr("price", 0) + " PK");
			int priceX = owner ? x1 - bw - 4 : x1;
			int pw = font.width(price);
			l.teks(Component.translatable("kultivator.hall.ware." + id), x0, yy + 3, priceX - pw - 6 - x0, locked ? Palette.INK_FAINT : l.gaya.teks);
			l.kanan(price, priceX, yy + 3, locked ? Palette.SEAL_DARK : Palette.GOLD_DARK);
			if (owner) {
				boolean bisa = canBuy(id);
				TalismanButton.gambar(l, "kas:beli:" + id, x1 - bw, yy + 1, bw, BTN_H, Component.translatable("kultivator.hall.ui.buy"), bisa,
						bisa ? null : alasanBeli(id), () -> HallClient.send(Halls.OP_BUY, id + ":" + param(id)));
			}
			l.g.fill(x0, yy + ROW_H, x1, yy + ROW_H + 1, Palette.argb(0x40, Palette.GOLD_DARK));
			if (l.di(x0, yy, owner ? x1 - bw - 2 : x1, yy + ROW_H)) {
				l.tip(wareTip(id));
			}
			yy += ROW_H + 1;
		}
		if (!owner) {
			yy += 3 + Math.round(l.bungkus(Component.translatable("kultivator.hall.ui.shop_readonly"), x0, yy + 3, w0, l.gaya.teksLemah));
		}
		return yy;
	}

	// ================================================================== the Yue Lu's data (as the hall scroll had it)

	/** Disciples that can receive a sermon or a codex now (loaded, not away). */
	private static List<CompoundTag> targets() {
		List<CompoundTag> list = new ArrayList<>();
		for (Tag tag : info().getListOrEmpty("disciples")) {
			if (tag instanceof CompoundTag entry && !entry.getBooleanOr("away", false) && !entry.getBooleanOr("unloaded", false)) {
				list.add(entry);
			}
		}
		return list;
	}

	private @Nullable CompoundTag target() {
		List<CompoundTag> list = targets();
		return list.isEmpty() ? null : list.get(Math.floorMod(targetIndex, list.size()));
	}

	/** The jade slip picked for the Slip Giok Teknik row: every sellable spec id the hall sends in {@code books} (22.8.4). */
	private @Nullable TechniqueSpec book() {
		ListTag books = info().getListOrEmpty("books");
		if (books.isEmpty()) {
			return null;
		}
		return TechniqueRegistry.byId(books.getStringOr(Math.floorMod(bookIndex, books.size()), ""));
	}

	/** The server's {id, price, need, ok} entry of the picked slip, or null. */
	private @Nullable CompoundTag slip() {
		TechniqueSpec book = book();
		CompoundTag ware = ware("technique");
		if (book == null || ware == null) {
			return null;
		}
		for (Tag tag : ware.getListOrEmpty("slips")) {
			if (tag instanceof CompoundTag entry && entry.getStringOr("id", "").equals(book.id())) {
				return entry;
			}
		}
		return null;
	}

	private static @Nullable CompoundTag ware(String id) {
		for (Tag tag : info().getListOrEmpty("shop")) {
			if (tag instanceof CompoundTag entry && entry.getStringOr("id", "").equals(id)) {
				return entry;
			}
		}
		return null;
	}

	private String param(String id) {
		CompoundTag ware = ware(id);
		if (ware != null && ware.getBooleanOr("target", false)) {
			CompoundTag target = target();
			return target == null ? "" : target.getStringOr("soul", "");
		}
		if ("technique".equals(id)) {
			TechniqueSpec book = book();
			return book == null ? "" : book.id();
		}
		return "";
	}

	private boolean canBuy(String id) {
		CompoundTag ware = ware(id);
		if (ware == null || !ware.getBooleanOr("ok", false)) {
			return false;
		}
		if (ware.getBooleanOr("target", false) && target() == null) {
			return false;
		}
		if (!"technique".equals(id)) {
			return true;
		}
		CompoundTag slip = slip();
		return book() != null && (slip == null || slip.getBooleanOr("ok", false));
	}

	/** Why "Beli" is off (the disabled talisman's tooltip and footer line). */
	private Component alasanBeli(String id) {
		CompoundTag ware = ware(id);
		if (ware == null) {
			return Component.translatable("kultivator.hall.ware." + id);
		}
		if (ware.getBooleanOr("locked", false)) {
			return Component.translatable("kultivator.hall.ui.needs_level", ware.getIntOr("need", 1));
		}
		if (ware.getBooleanOr("used_today", false)) {
			return Component.translatable("kultivator.hall.ui.used_today");
		}
		if (ware.getBooleanOr("target", false) && target() == null) {
			return Component.translatable("kultivator.hall.ui.need_target");
		}
		if (ware.getIntOr("price", 0) > info().getIntOr("pool", 0)) {
			return Component.translatable("kultivator.hall.ui.pool_short");
		}
		CompoundTag slip = slip();
		if ("technique".equals(id) && slip != null && slip.getIntOr("need", 1) > info().getIntOr("level", 99)) {
			return Component.translatable("kultivator.hall.ui.needs_level", slip.getIntOr("need", 1));
		}
		return Component.translatable("kultivator.hall.ui.pool_short");
	}

	private static boolean canRestore(int set) {
		CompoundTag info = info();
		int[] pages = info.getIntArray("pages").orElse(new int[3]);
		boolean restored = (info.getIntOr("arts", 0) & 1 << (set - 1)) != 0;
		return !restored && info.getBooleanOr("pavilion", false) && set - 1 < pages.length && pages[set - 1] == 0x1F;
	}

	private List<Component> wareTip(String id) {
		List<Component> lines = new ArrayList<>();
		CompoundTag ware = ware(id);
		lines.add(Component.translatable("kultivator.hall.ware." + id));
		lines.add(Component.translatable("kultivator.hall.ware." + id + ".desc").withStyle(ChatFormatting.GRAY));
		if (ware == null) {
			return lines;
		}
		lines.add(Component.translatable("kultivator.hall.ui.price_pk", ware.getIntOr("price", 0), info().getIntOr("pool", 0)).withStyle(ChatFormatting.GOLD));
		if (ware.getBooleanOr("locked", false)) {
			lines.add(Component.translatable("kultivator.hall.ui.needs_level", ware.getIntOr("need", 1)).withStyle(ChatFormatting.RED));
		} else if (ware.getBooleanOr("used_today", false)) {
			lines.add(Component.translatable("kultivator.hall.ui.used_today").withStyle(ChatFormatting.RED));
		} else if (ware.getIntOr("price", 0) > info().getIntOr("pool", 0)) {
			lines.add(Component.translatable("kultivator.hall.ui.pool_short").withStyle(ChatFormatting.RED));
		}
		if (ware.getBooleanOr("target", false)) {
			CompoundTag target = target();
			lines.add(target == null ? Component.translatable("kultivator.hall.ui.need_target").withStyle(ChatFormatting.RED)
					: Component.translatable("kultivator.hall.ui.for_disciple",
							target.read("name", ComponentSerialization.CODEC).orElse(Component.translatable("entity.kultivator.disciple")))
							.withStyle(ChatFormatting.DARK_AQUA));
		}
		if ("technique".equals(id)) {
			TechniqueSpec book = book();
			if (book != null) {
				lines.add(Component.translatable("kultivator.hall.ui.for_book", book.displayName()).withStyle(ChatFormatting.DARK_AQUA));
				CompoundTag slip = slip();
				if (slip != null) {
					lines.add(Component.translatable("kultivator.hall.ui.price_pk", slip.getIntOr("price", 0), info().getIntOr("pool", 0))
							.withStyle(ChatFormatting.GOLD));
					if (slip.getIntOr("need", 1) > info().getIntOr("level", 99)) {
						lines.add(Component.translatable("kultivator.hall.ui.needs_level", slip.getIntOr("need", 1)).withStyle(ChatFormatting.RED));
					}
				}
			}
		}
		lines.add(Component.translatable("kultivator.hall.ui.paviliun.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		return lines;
	}

	private static List<Component> artTip(int set) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.hall.art." + set));
		lines.add(Component.translatable("kultivator.hall.art.effect." + set).withStyle(ChatFormatting.GRAY));
		CompoundTag info = info();
		if ((info.getIntOr("arts", 0) & 1 << (set - 1)) != 0) {
			lines.add(Component.translatable("kultivator.hall.ui.art_restored").withStyle(ChatFormatting.GREEN));
		} else {
			int[] pages = info.getIntArray("pages").orElse(new int[3]);
			int bits = set - 1 < pages.length ? pages[set - 1] : 0;
			lines.add(Component.translatable("kultivator.hall.ui.art_pages", Integer.bitCount(bits), 5).withStyle(bits == 0x1F ? ChatFormatting.GREEN
					: ChatFormatting.YELLOW));
			if (!info.getBooleanOr("pavilion", false)) {
				lines.add(Component.translatable("kultivator.hall.art.need_pavilion").withStyle(ChatFormatting.RED));
			}
			lines.add(Component.translatable("kultivator.hall.ui.art_reward").withStyle(ChatFormatting.GOLD));
		}
		lines.add(Component.translatable("kultivator.hall.art.source." + set).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		return lines;
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (fokusRing.klik(event.x(), event.y(), event.button())) {
			diAnak = false;
			return true;
		}
		if (anak != null && anak.mouseClicked(event, doubleClick)) {
			diAnak = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean a = anak != null && anak.mouseReleased(event);
		return fokusRing.lepas(event.x(), event.y()) || a;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return anak != null && anak.mouseDragged(event, dx, dy) || fokusRing.seret(event.x(), event.y(), dx);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (anak != null) {
			return anak.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		if (fokusRing.gulir(mouseX, mouseY, scrollY)) {
			return true;
		}
		return gulir.roda(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (anak != null && (diAnak || anak.mauTombol())) {
			return anak.keyPressed(event) || fokusRing.tombol(event);
		}
		if (fokusRing.tombol(event)) {
			return true;
		}
		if (anak != null) {
			return anak.keyPressed(event);
		}
		// 43.4.2: ↑↓ PgUp PgDn Home End move the stacked column
		return gulir.tombol(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (anak != null && (diAnak || anak.mauTombol())) {
			return anak.charTyped(event);
		}
		return fokusRing.ketik(event);
	}

	@Override
	public boolean mauTombol() {
		return anak != null && anak.mauTombol() || fokusRing.mauTombol();
	}

	@Override
	public boolean modal() {
		return anak != null && anak.modal() || fokusRing.segel().terbuka();
	}

	@Override
	public boolean mundur() {
		return pemilihUntuk.tutup() || super.mundur();
	}

	/**
	 * Tab walks the chip row, then the child page's ring (which wraps inside the child); Shift+Tab from inside the child goes
	 * back to the chips. Without a child the page's own ring (cards, talismans) is walked.
	 */
	@Override
	public boolean fokusTab(boolean back) {
		if (anak == null) {
			return super.fokusTab(back);
		}
		boolean chip = adaChip();
		if (diAnak) {
			if (back && chip) {
				diAnak = false;
				fokusRing.set(CHIP);
				return true;
			}
			return anak.fokusTab(back);
		}
		if (chip && fokusRing.kunci() == null) {
			fokusRing.set(CHIP);
			return true;
		}
		fokusRing.set(null);
		diAnak = true;
		return anak.fokusTab(back);
	}
}
