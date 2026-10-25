package dev.kultivator.client.tempat.ui.kit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.hub.DiSini;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.tempat.ui.UiTempatClient;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The base of every "Pasar Giok" sheet (DESIGN 38.U), now a page of the Gulungan Utama (DESIGN 43.6): the Gulungan draws
 * the frame (title, purse, "?" seal, footer) and this class the sheet's content in the page box. One class owns the sheet
 * so all sheets behave the same.
 * <ul>
 * <li><b>Sheet</b>: {@link #perbarui} takes the server's sheet ({@link LembarKunci}); a live refresh keeps tab, scroll,
 * selection and focus; an open jumps to the sheet's {@code tab}. Countdowns run from the moment the sheet arrived
 * ({@link #diterima}). The first sheet is applied on first use, when every subclass field exists (F2).</li>
 * <li><b>Tabs</b> are the page's subs ({@link #subs}): the screen's tabs minus {@code tab_sembunyi}, with {@code tab_kunci}
 * locks, and, on a home page, only the mounted tabs ({@link #batasTab}).</li>
 * <li><b>Layout</b> from the box width (38.U.2): below 320 one column ({@link Tata#SEMPIT}), 320-479 list + detail
 * ({@link Tata#SEDANG}), from 480 three columns ({@link Tata#LEBAR}). A strip over the content carries what the old
 * header did besides the title and money: district, subtitle/place, Nama Baik, discount, the test ribbon and "only
 * viewing".</li>
 * <li><b>Input</b>: mouse through the zones of the last frame, Tab / Shift+Tab focus with a gold ring, Enter, {@code /}
 * search, ←/→ amounts, ↑↓ PgUp PgDn Home End lists, Space for the screen's quick action; digits are the Gulungan's except
 * under Di Sini, where {@link #angka} picks numbered choices first (43.17 K3).</li>
 * <li><b>Read-only views</b> (a home page's hub view, {@code OP_LIHAT}): write buttons are drawn disabled with the reason
 * ({@link #hanyaBaca}).</li>
 * <li><b>{@code uji ui skala N}</b>: a sheet with {@code uji_skala} N lays the content out in the size GUI scale N would
 * give and scales it to the box (38.U.2).</li>
 * </ul>
 The old {@code Screen} size, purse and tab-label bridge is gone (43.16.4 P1); {@code minecraft} and {@code font} come
 * from {@link Page}. Subclasses draw the content area in {@link #isi} and send ops with {@link #kirim}. The kit-38
 * {@link Lukis} engine of {@link #isi} stays while sheets still draw with it ({@code LayarGabungDasar} and its sheets,
 * {@code LayarBukuKas}, {@code LayarJurnal}, {@code LayarPetaKota}, {@code LayarBukuDagang}, the 42 sheets); the helper
 * bases {@code LayarInti} and {@code TemaHalaman} draw with the single kit {@code screen/ui} instead.
 */
public abstract class LayarTempatDasar extends Page implements UiTempatClient.LayarTempat {

	/** Key of the {@code uji ui skala} test (38UI's test sheets only). */
	public static final String UJI_SKALA = "uji_skala";
	/** Key marking a test sheet (a ribbon "UJI" in the strip). */
	public static final String UJI = "uji";

	/** Height of the info strip over the content. */
	private static final int STRIP = 12;

	protected final String layar;
	protected final int npcId;
	protected Lembar lembar;
	/** The first sheet, applied on first use ({@link #siapkan}) so subclass fields exist before the hooks run. */
	private @Nullable CompoundTag tertunda;
	protected long diterima;
	protected TempatWarna.Gaya gaya;
	/** The screen's tab list (drawn by the Gulungan as subs). */
	protected final TabLentera tablet = new TabLentera();
	protected String tab = "";
	protected final TombolAksi tombol = new TombolAksi();
	protected @Nullable KotakCari cari;
	/** The screen's own name (the fallback of {@link #judulBawaan} when the sheet has no {@code judul}). */
	protected final Component title;
	private long frameTerakhir = Util.getMillis();
	private long terkunciSejak = -1L;
	private @Nullable String fokus;
	private List<Lukis.Zona> zonaTerakhir = new ArrayList<>();
	private List<DaftarGulir> daftarTerakhir = new ArrayList<>();
	private List<Pratinjau3D> pratinjauTerakhir = new ArrayList<>();
	private @Nullable DaftarGulir daftarFokus;
	private @Nullable Lukis.Zona ditekan;
	private @Nullable DaftarGulir diseret;
	private @Nullable Pratinjau3D diputar;
	private String opTerakhir = "";
	private long opSejak;
	private boolean lihatSaja;
	private Set<String> batas = Set.of();
	private boolean strip;
	private @Nullable Component pesanTunda;
	private int pesanTundaWarna;
	// layout (layout coordinates; equal to the page box unless uji_skala)
	protected int lebarV;
	protected int tinggiV;
	protected float skala = 1.0F;
	protected Tata tata = Tata.SEDANG;
	protected int px0;
	protected int py0;
	protected int px1;
	protected int py1;
	protected int ix0;
	protected int iy0;
	protected int ix1;
	protected int iy1;

	protected LayarTempatDasar(String layar, int npcId, CompoundTag lembar, Component judul, TempatWarna.Gaya gayaBawaan) {
		this.title = judul;
		this.layar = layar;
		this.npcId = npcId;
		this.gaya = gayaBawaan;
		// F2: the hooks (tabs, setelahLembar, tabBerubah) run on first use, not from this constructor, where a subclass's
		// own field initialisers have not run yet (LayarTokoSenjata.tabBerubah -> daftar.mulaiUlang crashed).
		this.lembar = new Lembar(lembar);
		this.tertunda = lembar;
	}

	// =================================================================== contract

	@Override
	public String layar() {
		return layar;
	}

	@Override
	public int npcId() {
		return npcId;
	}

	@Override
	public void perbarui(CompoundTag baru, boolean buka) {
		if (tertunda != null) {
			tertunda = baru;
			lembar = new Lembar(baru);
			return;
		}
		terima(baru, buka);
	}

	/** Applies the first sheet once the object is fully built (any first use: the router, the Gulungan). */
	private void siapkan() {
		if (tertunda != null) {
			CompoundTag pertama = tertunda;
			tertunda = null;
			lembar = null;
			terima(pertama, true);
		}
	}

	private void terima(CompoundTag tag, boolean buka) {
		Lembar lama = lembar;
		lembar = new Lembar(tag);
		diterima = Util.getMillis();
		gaya = TempatWarna.Gaya.dari(lembar.s(LembarKunci.GAYA), gaya);
		tablet.setTabs(saringTab(tabs()));
		String diminta = lembar.s(LembarKunci.TAB);
		if ((buka || tab.isEmpty()) && !diminta.isEmpty() && tabBoleh(diminta)) {
			gantiTab(diminta, false);
		}
		if (buka) {
			String hub = dev.kultivator.client.tempat.ui.UiHub.ambilTab(layar);
			if (hub != null && tabBoleh(hub)) {
				gantiTab(hub, false);
			}
		}
		pastikanTab();
		Component p = lembar.teksAtau(LembarKunci.PESAN);
		if (p != null && (lama == null || !p.getString().equals(lama.teks(LembarKunci.PESAN).getString()) || buka)) {
			tampilPesan(p, TempatWarna.status(lembar.s(LembarKunci.PESAN_JENIS, LembarKunci.PESAN_OK)));
			if (LembarKunci.PESAN_BAHAYA.equals(lembar.s(LembarKunci.PESAN_JENIS))) {
				UiSound.denied();
			}
		}
		if (lembar.ada(LembarKunci.KUNCI_LAYAR)) {
			if (terkunciSejak < 0L) {
				terkunciSejak = Util.getMillis();
			}
		} else {
			terkunciSejak = -1L;
		}
		setelahLembar(buka);
		tataUlang();
	}

	/** The shown tab exists and is open, else the first tab. */
	private void pastikanTab() {
		if (tab.isEmpty() || tablet.cari(tab) == null) {
			TabLentera.Tab pertama = tablet.tab(0);
			gantiTab(pertama == null ? "" : pertama.id(), false);
		}
	}

	// =================================================================== for the router and home pages (43.12.4)

	/** Shows only tabs {@code ids} (a home page's mounted tabs; empty = all). */
	public void batasTab(Set<String> ids) {
		batas = ids == null ? Set.of() : Set.copyOf(ids);
		if (tertunda == null) {
			tablet.setTabs(saringTab(tabs()));
			pastikanTab();
		}
	}

	/** Marks this page a read-only hub view ({@code OP_LIHAT} answer): write buttons are disabled with the reason. */
	public void lihatSaja(boolean baca) {
		lihatSaja = baca;
		tataUlang();
	}

	public boolean lihatSaja() {
		return lihatSaja;
	}

	/** The tab shown now (after the first sheet is applied). */
	public String tabSekarang() {
		siapkan();
		return tab;
	}

	// =================================================================== for subclasses

	/** The screen's tabs in order (ids of the screen's {@code TABS}); empty = no tabs. */
	protected abstract List<TabLentera.Tab> tabs();

	/** Draws the content of tab {@link #tab} into the box. */
	protected abstract void isi(Lukis l, int x0, int y0, int x1, int y1);

	/** A new sheet arrived (parse it; {@code buka} true when (re)opened). */
	protected void setelahLembar(boolean buka) {
	}

	/** The tab changed (reset lists here). */
	protected void tabBerubah() {
	}

	/** Space: the screen's quick action (auction bid). True when used. */
	protected boolean spasi() {
		return false;
	}

	/**
	 * Digit 1-9 under the Di Sini tablet (43.17 K3): the screen picks its numbered choice {@code n} (the dialog). True when
	 * used; false lets the Gulungan switch section.
	 */
	@Override
	public boolean angka(int n) {
		return false;
	}

	/** Extra key handling of a screen (after the common keys). */
	protected boolean tombolLain(KeyEvent event) {
		return false;
	}

	/** Largest content width for this screen per layout (narrow screens like the bill override it). */
	protected int panelMaks(Tata t) {
		return t == Tata.LEBAR ? 600 : 470;
	}

	/** Illustration of the empty state. */
	protected KeadaanKosong.Gambar gambarKosong() {
		return KeadaanKosong.Gambar.NAMPAN;
	}

	/** The screen's default title when the sheet has no {@code judul}. */
	protected Component judulBawaan() {
		return title;
	}

	/** Hint fragment of the screen (e.g. "Spasi tawar"); shown as the tooltip of the strip. Null = none. */
	protected @Nullable Component petunjuk() {
		return null;
	}

	// =================================================================== page identity (43.6)

	@Override
	public Component title() {
		siapkan();
		return lembar.ada(LembarKunci.JUDUL) ? lembar.teks(LembarKunci.JUDUL) : judulBawaan();
	}

	/** R26: every sheet page has its Ensiklopedia topic ({@code kultivator.help.<layar>}). */
	@Override
	public String helpTopic() {
		return layar;
	}

	@Override
	public List<Sub> subs() {
		siapkan();
		List<TabLentera.Tab> tabs = tablet.tabs();
		if (tabs.size() < 2) {
			return List.of();
		}
		List<Sub> subs = new ArrayList<>(tabs.size());
		for (TabLentera.Tab t : tabs) {
			subs.add(new Sub(t.id(), t.label(), t.kunci()));
		}
		return subs;
	}

	@Override
	public void showSub(String id) {
		siapkan();
		if (id != null && tabBoleh(id)) {
			gantiTab(id, true);
		}
	}

	/** Sheets drawn for the 600 px panel take the wide paper (43.3.2: Di Sini in the wide layout). */
	@Override
	public boolean lebar() {
		return panelMaks(Tata.LEBAR) > 470;
	}

	/** Money on hand from the sheet (-1 = none); the Gulungan shows it rolling in its header. */
	@Override
	public long uang() {
		return lembar.l(LembarKunci.UANG, -1L);
	}

	@Override
	public Palette.Gaya gaya() {
		return Palette.Gaya.dari(gaya.id, Palette.Gaya.KERTAS);
	}

	/** A service opened from a talk keeps the talk one step back (43.8.1.3). */
	@Override
	public boolean rinci() {
		return DiSini.bisaKembali(this);
	}

	@Override
	public boolean mundur() {
		if (cari != null && cari.aktif()) {
			cari.berhenti();
			return true;
		}
		return DiSini.kembali(this);
	}

	@Override
	public boolean mauTombol() {
		return cari != null && cari.aktif() || super.mauTombol();
	}

	// =================================================================== life

	@Override
	protected void build() {
		siapkan();
		tataUlang();
		if (pesanTunda != null && screen != null) {
			pesan(pesanTunda, pesanTundaWarna);
			pesanTunda = null;
		}
	}

	/**
	 * Shown: a live context whose server session another sheet took over (a home page's hub view) asks the server again
	 * (an NPC), or ends (an anchor cannot be reopened from here).
	 */
	@Override
	public void onShow(@Nullable CompoundTag fokusTag) {
		siapkan();
		if (!lihatSaja && !UiTempatClient.sesiAktif(layar, npcId)) {
			if (npcId >= 0) {
				kirim(LembarKunci.OP_LIHAT);
			} else if (DiSini.halaman() == this) {
				DiSini.selesai(Component.translatable("kultivator.gui43.konteks.berakhir"));
			}
		}
		super.onShow(fokusTag);
	}

	/** Left the screen for good (Gulungan closed, context replaced, home page hidden): the server ends the session. */
	@Override
	public void onHide() {
		UiTempatClient.ditutup(this);
		DiSini.lepas(this);
	}

	// =================================================================== helpers

	/** Builds a tab; its lock comes from the sheet's {@code tab_kunci}. */
	protected TabLentera.Tab tabBaru(String id, String labelKunci, int ikon) {
		return new TabLentera.Tab(id, TeksTempat.k(labelKunci), ikon, null);
	}

	private List<TabLentera.Tab> saringTab(List<TabLentera.Tab> semua) {
		List<String> sembunyi = lembar.sl(LembarKunci.TAB_SEMBUNYI);
		Lembar kunci = lembar.sub(LembarKunci.TAB_KUNCI);
		List<TabLentera.Tab> out = new ArrayList<>(semua.size());
		for (TabLentera.Tab t : semua) {
			if (sembunyi.contains(t.id()) || !tabAda(t.id()) || !batas.isEmpty() && !batas.contains(t.id())) {
				continue;
			}
			Component alasan = kunci.teksAtau(t.id());
			out.add(alasan == null ? t : new TabLentera.Tab(t.id(), t.label(), t.ikon(), alasan));
		}
		return out;
	}

	/** Whether tab {@code id} exists for this sheet (screens hide tabs whose data block is absent, e.g. dialog "Jasa"). */
	protected boolean tabAda(String id) {
		return true;
	}

	private boolean tabBoleh(String id) {
		TabLentera.Tab t = tablet.cari(id);
		return t != null && t.kunci() == null;
	}

	protected void gantiTab(String id, boolean kirimOp) {
		if (id.equals(tab)) {
			return;
		}
		tab = id;
		tabBerubah();
		if (kirimOp && !id.isEmpty()) {
			UiSound.tab();
			kirim(LembarKunci.op(LembarKunci.OP_TAB, id));
		}
	}

	/** True when this page may only read (a home page's hub view without NPC or anchor). */
	public boolean hanyaBaca() {
		return lihatSaja;
	}

	/** Reason shown on write buttons of a read-only page. */
	protected Component alasanBaca() {
		return TeksTempat.k("hanya_baca");
	}

	/** Sends an op (drops an identical op within 250 ms: double clicks). */
	protected void kirim(String op) {
		long now = Util.getMillis();
		if (op.equals(opTerakhir) && now - opSejak < 250L) {
			return;
		}
		opTerakhir = op;
		opSejak = now;
		UiTempatClient.kirim(layar, npcId, op);
	}

	/** A write action: disabled with the reason on read-only pages. */
	protected boolean tulisBoleh() {
		return !hanyaBaca();
	}

	/** The result line in the Gulungan's footer (6 s); kept until the page is mounted when it comes earlier. */
	public void tampilPesan(Component teks, int warna) {
		if (screen == null) {
			pesanTunda = teks;
			pesanTundaWarna = warna;
			return;
		}
		pesan(teks, warna);
	}

	/** A button that sends {@code op} when allowed (also checks read-only). */
	protected boolean tombolOp(Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean aktif, @Nullable Component alasan,
			String op) {
		boolean baca = !tulisBoleh() && !LembarKunci.opBaca(LembarKunci.verb(op));
		return tombol.gambar(l, kunci, x, y, w, h, label, aktif && !baca, baca ? alasanBaca() : alasan, () -> kirim(op));
	}

	/** Closes the Gulungan (the session ends with it): a sheet's own "Tutup" button. */
	public void onClose() {
		dev.kultivator.client.hub.HubNav.tutupSemua();
	}

	// =================================================================== layout

	private void tataUlang() {
		if (w <= 0 || h <= 0) {
			return;
		}
		int ujiSkala = lembar == null ? 0 : lembar.i(UJI_SKALA, 0);
		int gs = minecraft.getWindow().getGuiScale();
		if (ujiSkala > 0 && gs > 0) {
			skala = ujiSkala / (float) gs;
			lebarV = Math.max(160, Math.round(w / skala));
			tinggiV = Math.max(120, Math.round(h / skala));
			skala = Math.min(w / (float) lebarV, h / (float) tinggiV);
		} else {
			lebarV = w;
			tinggiV = h;
			skala = 1.0F;
		}
		tata = lebarV < 320 ? Tata.SEMPIT : lebarV < 480 ? Tata.SEDANG : Tata.LEBAR;
		px0 = x;
		py0 = y;
		px1 = x + lebarV;
		py1 = y + tinggiV;
		strip = adaStrip();
		ix0 = px0;
		iy0 = py0 + (strip ? STRIP + 3 : 0);
		ix1 = px1;
		iy1 = py1;
	}

	private boolean adaStrip() {
		return lembar != null && (!lembar.s(LembarKunci.DISTRIK).isEmpty() || lembar.ada(LembarKunci.SUBJUDUL) || lembar.ada(LembarKunci.TEMPAT)
				|| lembar.ada(LembarKunci.NAMA_BAIK) || lembar.f(LembarKunci.DISKON, 0.0F) > 0.001F || lembar.b(UJI) || hanyaBaca());
	}

	// =================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		siapkan();
		long now = Util.getMillis();
		float detik = Math.min(0.1F, (now - frameTerakhir) / 1000.0F);
		frameTerakhir = now;
		Lukis l = new Lukis(g, font, (int) Math.round(vx(mouseX)), (int) Math.round(vy(mouseY)), detik, now, skala, gaya, fokus);
		g.pose().pushMatrix();
		if (skala != 1.0F) {
			g.pose().translate(x, y);
			g.pose().scale(skala, skala);
			g.pose().translate(-x, -y);
		}
		if (gaya != TempatWarna.Gaya.KERTAS) {
			// the sheet's own style (dark night market, red lacquer ...) under its content: its text colours are made for it
			g.fill(px0, py0, px1, py1, gaya.dasar);
		}
		if (strip) {
			gambarStrip(l);
		}
		if (lembar.ada(LembarKunci.KUNCI_LAYAR)) {
			Lembar k = lembar.sub(LembarKunci.KUNCI_LAYAR);
			KeadaanTerkunci.gambar(l, k.teks(LembarKunci.ALASAN), k.teksAtau(LembarKunci.SYARAT), ix0, iy0, ix1, iy1,
					now - Math.max(0L, terkunciSejak));
		} else {
			try {
				isi(l, ix0, iy0, ix1, iy1);
			} catch (RuntimeException ex) {
				dev.kultivator.Kultivator.LOGGER.warn("Tempat page {} failed to draw", layar, ex);
				Brush.text(g, font, TeksTempat.k("galat"), ix0 + 4, iy0 + 4, TempatWarna.BAHAYA, 1.0F, false);
			}
		}
		g.pose().popMatrix();
		zonaTerakhir = l.zona;
		daftarTerakhir = l.semuaDaftar;
		pratinjauTerakhir = l.semuaPratinjau;
		daftarFokus = l.daftarAktif;
		if (fokus != null && !adaZona(fokus)) {
			fokus = null;
		}
		if (l.tip != null && !l.tip.isEmpty()) {
			SlipTooltip.antre(l.tip);
		}
	}

	/**
	 * The strip over the content (what the old header carried besides title and money): district, subtitle or place,
	 * then from the right "only viewing", the test ribbon, the discount and the place's Nama Baik.
	 */
	private void gambarStrip(Lukis l) {
		int y = py0;
		int x = px0 + 2;
		int kanan = px1 - 2;
		String distrik = lembar.s(LembarKunci.DISTRIK);
		if (!distrik.isEmpty()) {
			int warna = TempatWarna.distrik(distrik);
			l.g.fill(x - 1, y, x + 12, y + STRIP, Palette.argb(0xC0, warna & 0xFFFFFF));
			IkonTempat.gambar(l.g, LembarKunci.Ikon.distrik(distrik), x, y + 1, 10, 0xFFFFFFFF);
			x += 15;
		}
		if (hanyaBaca()) {
			Component b = TeksTempat.k("hanya_baca.kaki");
			int bw = Math.min(kanan - x - 20, font.width(b) + 6);
			if (bw > 20) {
				l.g.fill(kanan - bw, y, kanan, y + STRIP, Palette.argb(0x60, Palette.INK_FAINT & 0xFFFFFF));
				l.teks(b, kanan - bw + 3, y + 2, bw - 6, gaya.teks);
				kanan -= bw + 4;
			}
		}
		if (lembar.b(UJI) && kanan - x > 60) {
			IkonTempat.pita(l.g, true, kanan - 34, y, 32, STRIP, 0xFFFFFFFF);
			Brush.centered(l.g, font, TeksTempat.k("uji.pita"), kanan - 18, y + 2, 0xFF5A3A08, 1.0F, false);
			kanan -= 38;
		}
		float diskon = lembar.f(LembarKunci.DISKON, 0.0F);
		if (diskon > 0.001F) {
			Component d = TeksTempat.k("diskon", Math.round(diskon * 100));
			int dw = font.width(d) + 6;
			if (kanan - dw - x > 40) {
				l.g.fill(kanan - dw, y, kanan, y + STRIP, TempatWarna.SEGEL);
				l.teks(d, kanan - dw + 3, y + 2, dw - 4, 0xFFFFF0E0);
				kanan -= dw + 4;
			}
		}
		if (lembar.ada(LembarKunci.NAMA_BAIK) && kanan - x > 80) {
			kanan -= LencanaPangkat.namaBaik(l, lembar.i(LembarKunci.NAMA_BAIK, 0), lembar.i(LembarKunci.PANGKAT_NB, LembarKunci.PANGKAT_ASING), kanan,
					y + 2) + 4;
		}
		Component sub = lembar.teksAtau(LembarKunci.SUBJUDUL);
		Component tempat = lembar.teksAtau(LembarKunci.TEMPAT);
		Component baris = sub != null ? sub : tempat;
		if (baris != null && kanan - x > 12) {
			l.teks(baris, x, y + 2, kanan - x - 2, gaya.teksLemah);
		}
		Component hint = petunjuk();
		if (hint != null && l.di(px0, y, px1, y + STRIP)) {
			l.tip(hint);
		}
		PanelTempat.garis(l.g, gaya, px0, px1, y + STRIP + 1);
	}

	/** A box of the layout. */
	public record Kotak(int x0, int y0, int x1, int y1) {
		public int w() {
			return x1 - x0;
		}

		public int h() {
			return y1 - y0;
		}
	}

	/**
	 * The list / detail split of 38.U.2: narrow = list over detail, medium = list left (52 %), wide = list left (46 %) and
	 * a big detail with the large preview. Returns {list, detail}.
	 */
	protected Kotak[] bagi(int x0, int y0, int x1, int y1) {
		if (tata == Tata.SEMPIT) {
			int mid = y0 + Math.round((y1 - y0) * 0.52F);
			return new Kotak[] {new Kotak(x0, y0, x1, mid - 3), new Kotak(x0, mid + 3, x1, y1)};
		}
		float r = tata == Tata.LEBAR ? 0.46F : 0.52F;
		int mid = x0 + Math.round((x1 - x0) * r);
		return new Kotak[] {new Kotak(x0, y0, mid - 3, y1), new Kotak(mid + 3, y0, x1, y1)};
	}

	/** A section heading: bold accent text over a thin rule. Returns the height used (12). */
	protected int subjudul(Lukis l, Component t, int x0, int y, int x1) {
		l.teks(Brush.bold(t), x0, y, x1 - x0, gaya.gelap() ? TempatWarna.EMAS_TERANG : gaya.aksen);
		l.g.fill(x0, y + 10, x1, y + 11, Palette.argb(0x60, (gaya.gelap() ? TempatWarna.EMAS : gaya.teksSamar) & 0xFFFFFF));
		return 13;
	}

	/** A label / value row (value right-aligned); returns the height used (10). */
	protected int barisNilai(Lukis l, Component label, Component nilai, int x0, int y, int x1) {
		int vw = font.width(nilai);
		l.teks(label, x0, y, x1 - x0 - vw - 4, gaya.teksLemah);
		l.kanan(nilai, x1, y, gaya.teks, 1.0F);
		return 10;
	}

	/** Detail-pane well with a subtle paper colour on dark styles. */
	protected void sumur(Lukis l, Kotak k) {
		PanelTempat.sumur(l.g, gaya, k.x0(), k.y0(), k.x1(), k.y1());
	}

	/** Empty state with the sheet's {@code kosong} compound (or a fallback sentence). */
	protected void kosong(Lukis l, @Nullable Component cadangan, int x0, int y0, int x1, int y1) {
		KeadaanKosong.gambar(l, tombol, gambarKosong(), lembar.sub(LembarKunci.KOSONG), cadangan, x0, y0, x1, y1, this::kirim);
	}

	private boolean adaZona(String kunci) {
		for (Lukis.Zona z : zonaTerakhir) {
			if (z.kunci.equals(kunci) && z.fokus) {
				return true;
			}
		}
		return false;
	}

	private @Nullable Lukis.Zona zonaDi(double x, double y) {
		for (int i = zonaTerakhir.size() - 1; i >= 0; i--) {
			Lukis.Zona z = zonaTerakhir.get(i);
			if (z.berisi(x, y)) {
				return z;
			}
		}
		return null;
	}

	private @Nullable Lukis.Zona zonaFokus() {
		if (fokus == null) {
			return null;
		}
		for (Lukis.Zona z : zonaTerakhir) {
			if (z.kunci.equals(fokus)) {
				return z;
			}
		}
		return null;
	}

	private void jalankan(Lukis.Zona z) {
		if (!z.aktif) {
			UiSound.denied();
			if (z.alasan != null) {
				tampilPesan(z.alasan, TempatWarna.BAHAYA);
			}
			return;
		}
		if (z.aksi != null) {
			UiSound.press();
			z.aksi.run();
		}
	}

	// =================================================================== input

	/** Screen x to layout x (the uji_skala scale is about the box corner). */
	private double vx(double sx) {
		return x + (sx - x) / skala;
	}

	private double vy(double sy) {
		return y + (sy - y) / skala;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (modal()) {
			return super.mouseClicked(event, doubleClick);
		}
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		double mx = vx(event.x());
		double my = vy(event.y());
		Lukis.Zona z = zonaDi(mx, my);
		if (cari != null && cari.aktif() && (z == null || !z.kunci.equals("cari"))) {
			cari.berhenti();
		}
		for (DaftarGulir d : daftarTerakhir) {
			if (d.tekan(mx, my)) {
				diseret = d;
				return true;
			}
		}
		for (Pratinjau3D p : pratinjauTerakhir) {
			if (p.berisi(mx, my)) {
				diputar = p;
				p.mulaiSeret();
			}
		}
		if (z == null) {
			return diputar != null;
		}
		if (z.fokus) {
			fokus = z.kunci;
		} else {
			int hash = z.kunci.indexOf('#');
			if (hash > 0) {
				fokus = z.kunci.substring(0, hash);
			}
		}
		if (z.widget != null) {
			z.widget.press();
			ditekan = z;
			return true;
		}
		if (z.aksi != null && z.aktif && z.kunci.contains("#")) {
			// list rows and sub-buttons make their own sound
			z.aksi.run();
		} else if (z.aksi != null || !z.aktif) {
			jalankan(z);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (modal()) {
			return super.mouseReleased(event);
		}
		double mx = vx(event.x());
		double my = vy(event.y());
		if (diseret != null) {
			diseret.lepas();
			diseret = null;
			return true;
		}
		if (diputar != null) {
			diputar.lepas(Util.getMillis());
			diputar = null;
		}
		if (ditekan != null) {
			Lukis.Zona z = ditekan;
			ditekan = null;
			if (z.widget != null) {
				boolean acted = z.widget.release(mx, my);
				if (!acted && !z.aktif && z.berisi(mx, my) && z.alasan != null) {
					tampilPesan(z.alasan, TempatWarna.BAHAYA);
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (modal()) {
			return super.mouseDragged(event, dx, dy);
		}
		if (diseret != null) {
			diseret.seretKe(vy(event.y()));
			return true;
		}
		if (diputar != null) {
			diputar.seret(dx / skala, Util.getMillis());
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (modal()) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		double mx = vx(mouseX);
		double my = vy(mouseY);
		for (DaftarGulir d : daftarTerakhir) {
			if (d.berisi(mx, my)) {
				d.roda(scrollY);
				return true;
			}
		}
		return false;
	}

	/**
	 * Keys the Gulungan passes on (43.4.1): everything while the search writes or the confirm is open; otherwise Enter,
	 * Space, ←/→, lists, {@code /} and the screen's own keys. Esc, Tab and digits are the Gulungan's ({@link #mundur},
	 * {@link #fokusTab}, {@link #angka}).
	 */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (modal()) {
			return super.keyPressed(event);
		}
		if (cari != null && cari.tombol(event)) {
			return true;
		}
		int key = event.key();
		if (event.isEscape()) {
			return false;
		}
		if (key == InputConstants.KEY_TAB) {
			geserFokus(event.hasShiftDown() ? -1 : 1);
			return true;
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			Lukis.Zona z = zonaFokus();
			DaftarGulir d = daftarFokus;
			if (d != null && z != null && z.kunci.equals(fokus) && z.aksi == null) {
				d.pakai();
				return true;
			}
			if (z != null) {
				jalankan(z);
				return true;
			}
			if (d != null) {
				d.pakai();
				return true;
			}
			return true;
		}
		if (key == InputConstants.KEY_SPACE) {
			if (spasi()) {
				return true;
			}
			Lukis.Zona z = zonaFokus();
			if (z != null && z.widget != null) {
				jalankan(z);
				return true;
			}
		}
		if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT) {
			Lukis.Zona z = zonaFokus();
			if (z != null && z.geser != null) {
				z.geser.accept(key == InputConstants.KEY_RIGHT ? 1 : -1);
				return true;
			}
		}
		DaftarGulir daftar = daftarFokus != null ? daftarFokus : daftarTerakhir.isEmpty() ? null : daftarTerakhir.get(0);
		if (daftar != null) {
			int step = switch (key) {
				case InputConstants.KEY_UP -> -1;
				case InputConstants.KEY_DOWN -> 1;
				case InputConstants.KEY_PAGEUP -> -daftar.barisPerHalaman();
				case InputConstants.KEY_PAGEDOWN -> daftar.barisPerHalaman();
				case InputConstants.KEY_HOME -> -100000;
				case InputConstants.KEY_END -> 100000;
				default -> 0;
			};
			if (step != 0) {
				daftar.geser(step);
				return true;
			}
		}
		if (key == InputConstants.KEY_SLASH && cari != null) {
			cari.mulaiDariTombol();
			fokus = "cari";
			return true;
		}
		return tombolLain(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (modal()) {
			return super.charTyped(event);
		}
		return cari != null && cari.ketik(event);
	}

	/** Tab / Shift+Tab over the zones of the last frame. */
	@Override
	public boolean fokusTab(boolean back) {
		if (modal()) {
			return true;
		}
		geserFokus(back ? -1 : 1);
		return true;
	}

	private void geserFokus(int arah) {
		List<String> urut = new ArrayList<>();
		for (Lukis.Zona z : zonaTerakhir) {
			if (z.fokus && z.aktif && !urut.contains(z.kunci)) {
				urut.add(z.kunci);
			}
		}
		if (urut.isEmpty()) {
			return;
		}
		int i = fokus == null ? -1 : urut.indexOf(fokus);
		int next = i < 0 ? (arah > 0 ? 0 : urut.size() - 1) : Math.floorMod(i + arah, urut.size());
		fokus = urut.get(next);
		UiSound.hover();
	}
}
