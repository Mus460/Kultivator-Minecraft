package dev.kultivator.client.tempat.ui.kit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.tempat.ui.UiTempatClient;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The base of every "Pasar Giok" screen (DESIGN 38.U): one class owns the frame so all 38 screens behave the same.
 * <ul>
 * <li><b>Sheet</b>: {@link #perbarui} takes the server's sheet ({@link LembarKunci}); a live refresh keeps tab, scroll,
 * selection and focus; an open jumps to the sheet's {@code tab}. Countdowns run from the moment the sheet arrived
 * ({@link #diterima}).</li>
 * <li><b>Layout</b> from the scaled width (38.U.2): below 320 one column ({@link Tata#SEMPIT}), 320-479 list + detail
 * ({@link Tata#SEDANG}), from 480 three columns with lantern labels and a big preview ({@link Tata#LEBAR}). The panel never
 * exceeds the window; nothing is drawn below 0.6 scale.</li>
 * <li><b>Frame</b>: {@link PanelTempat} in the sheet's {@code gaya}, the title ×1.5 with the place and the district
 * colour, money on hand rolling when it changes, the place's Nama Baik seal, the lantern tablets ({@link TabLentera}) from
 * the screen's tabs minus {@code tab_sembunyi} with {@code tab_kunci} locks, the one-line result {@code pesan} in the
 * footer, key hints, {@code kunci_layar} (whole screen locked) and the "lantern opens" animation (200 ms, Y 0.92 → 1).</li>
 * <li><b>Input</b>: mouse through the zones of the last frame, Tab / Shift+Tab focus with a gold ring, Enter, Esc, 1-9
 * tabs, {@code /} search, ←/→ amounts, ↑↓ PgUp PgDn Home End lists, Space for the screen's quick action. Everything
 * can be done without the mouse.</li>
 * <li><b>Hub screens</b> ({@code npcId -1} opened from a hub card) only read: write buttons are drawn disabled with the
 * reason ({@link #hanyaBaca}).</li>
 * <li><b>{@code uji ui skala N}</b>: a sheet with {@code uji_skala} N lays the screen out in the GUI size that scale N
 * would give on this window and scales it to fit, so 2 / 3 / 4 can be checked on one machine (38.U.2).</li>
 * </ul>
 * Subclasses draw the content area in {@link #isi} with the kit and send ops with {@link #kirim}.
 */
public abstract class LayarTempatDasar extends Screen implements UiTempatClient.LayarTempat {

	/** Layout width classes of 38.U.2. */
	public enum Tata {
		SEMPIT, SEDANG, LEBAR
	}

	/** Key of the {@code uji ui skala} test (38UI's test sheets only). */
	public static final String UJI_SKALA = "uji_skala";
	/** Key marking a test sheet (a ribbon "UJI" in the header). */
	public static final String UJI = "uji";

	private static final long BUKA_MS = 200L;
	private static final long PESAN_MS = 6000L;
	private static final int KEPALA = 26;
	private static final int KAKI = 13;

	protected final String layar;
	protected final int npcId;
	protected Lembar lembar;
	/** The first sheet, applied in the first {@link #init()} so subclass fields exist before the hooks run. */
	private CompoundTag tertunda;
	protected long diterima;
	protected TempatWarna.Gaya gaya;
	protected final TabLentera tablet = new TabLentera();
	protected String tab = "";
	protected final TombolAksi tombol = new TombolAksi();
	protected final AngkaGulir uangGulir = new AngkaGulir();
	protected @Nullable KotakCari cari;
	private final long dibuka = Util.getMillis();
	private long frameTerakhir = Util.getMillis();
	private long terkunciSejak = -1L;
	private @Nullable Component pesan;
	private int pesanWarna;
	private long pesanSejak;
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
	// layout (layout coordinates)
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
		super(judul);
		this.layar = layar;
		this.npcId = npcId;
		this.gaya = gayaBawaan;
		// F2: the hooks (tabs, setelahLembar, tabBerubah) run from the first init(), not from this constructor, where a
		// subclass's own field initialisers have not run yet (LayarTokoSenjata.tabBerubah -> daftar.mulaiUlang crashed).
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
		if (tab.isEmpty() || tablet.cari(tab) == null) {
			TabLentera.Tab pertama = tablet.tab(0);
			gantiTab(pertama == null ? "" : pertama.id(), false);
		}
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
		if (minecraft != null) {
			tataUlang();
		}
	}

	// =================================================================== for subclasses

	/** The screen's tabs in order (ids of the screen's {@code TABS}); empty = no tablet column. */
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

	/** Digit 1-9 before it switches tabs (the dialog picks its numbered choices). True when used. */
	protected boolean angka(int n) {
		return false;
	}

	/** Extra key handling of a screen (after the common keys). */
	protected boolean tombolLain(KeyEvent event) {
		return false;
	}

	/** Largest panel width for this screen per layout (narrow screens like the bill override it). */
	protected int panelMaks(Tata t) {
		return t == Tata.LEBAR ? 600 : 470;
	}

	protected int panelTinggiMaks(Tata t) {
		return t == Tata.LEBAR ? 330 : 262;
	}

	/** True when lantern labels are written beside the lanterns in the wide layout. */
	protected boolean labelTab() {
		return true;
	}

	/** Illustration of the empty state. */
	protected KeadaanKosong.Gambar gambarKosong() {
		return KeadaanKosong.Gambar.NAMPAN;
	}

	/** The screen's default title when the sheet has no {@code judul}. */
	protected Component judulBawaan() {
		return title;
	}

	/** Hint fragment of the screen for the footer (e.g. "Spasi tawar"); null = none. */
	protected @Nullable Component petunjuk() {
		return null;
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
			if (sembunyi.contains(t.id()) || !tabAda(t.id())) {
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

	/** True when this screen may only read (hub card without NPC or anchor). */
	public boolean hanyaBaca() {
		return npcId < 0 && HubNav.fromHub(this);
	}

	/** Reason shown on write buttons of a read-only screen. */
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

	/** A write action: disabled with the reason on read-only screens. */
	protected boolean tulisBoleh() {
		return !hanyaBaca();
	}

	public void tampilPesan(Component teks, int warna) {
		pesan = teks;
		pesanWarna = warna;
		pesanSejak = Util.getMillis();
	}

	protected long uang() {
		return lembar.l(LembarKunci.UANG, -1L);
	}

	/** A button that sends {@code op} when allowed (also checks read-only). */
	protected boolean tombolOp(Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean aktif, @Nullable Component alasan,
			String op) {
		boolean baca = !tulisBoleh() && !LembarKunci.opBaca(LembarKunci.verb(op));
		return tombol.gambar(l, kunci, x, y, w, h, label, aktif && !baca, baca ? alasanBaca() : alasan, () -> kirim(op));
	}

	// =================================================================== layout

	@Override
	protected void init() {
		if (tertunda != null) {
			CompoundTag pertama = tertunda;
			tertunda = null;
			lembar = null;
			terima(pertama, true);
			return;
		}
		tataUlang();
	}

	private void tataUlang() {
		int ujiSkala = lembar.i(UJI_SKALA, 0);
		if (ujiSkala > 0 && minecraft != null) {
			int pxW = minecraft.getWindow().getWidth();
			int pxH = minecraft.getWindow().getHeight();
			lebarV = Math.max(160, pxW / ujiSkala);
			tinggiV = Math.max(120, pxH / ujiSkala);
			skala = Math.min(width / (float) lebarV, height / (float) tinggiV);
		} else {
			lebarV = width;
			tinggiV = height;
			skala = 1.0F;
		}
		tata = lebarV < 320 ? Tata.SEMPIT : lebarV < 480 ? Tata.SEDANG : Tata.LEBAR;
		int pw = Math.min(lebarV - 8, panelMaks(tata));
		int ph = Math.min(tinggiV - 8, panelTinggiMaks(tata));
		px0 = (lebarV - pw) / 2;
		py0 = (tinggiV - ph) / 2;
		px1 = px0 + pw;
		py1 = py0 + ph;
		int kiri = px0 + 7;
		boolean adaTab = !tablet.tabs().isEmpty();
		int tabLebar = adaTab ? TabLentera.LEBAR + (tata == Tata.LEBAR && labelTab() ? TabLentera.LEBAR_LABEL : 0) : 0;
		ix0 = kiri + tabLebar + (adaTab ? 4 : 1);
		iy0 = py0 + KEPALA + 4;
		ix1 = px1 - 8;
		iy1 = py1 - KAKI - 3;
	}

	// =================================================================== drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, 0x70101418, 0x90080A0C);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		float detik = Math.min(0.1F, (now - frameTerakhir) / 1000.0F);
		frameTerakhir = now;
		int mx = Math.round(mouseX / skala);
		int my = Math.round(mouseY / skala);
		Lukis l = new Lukis(g, font, mx, my, detik, now, skala, gaya, fokus);
		g.pose().pushMatrix();
		g.pose().scale(skala, skala);
		float t = Math.min(1.0F, (now - dibuka) / (float) BUKA_MS);
		float e = 1.0F - (1.0F - t) * (1.0F - t);
		float cy = (py0 + py1) / 2.0F;
		if (t < 1.0F) {
			float sy = 0.92F + 0.08F * e;
			g.pose().translate(0.0F, cy);
			g.pose().scale(1.0F, sy);
			g.pose().translate(0.0F, -cy);
		}
		PanelTempat.gambar(g, gaya, px0, py0, px1, py1);
		kepala(l);
		if (!tablet.tabs().isEmpty()) {
			tablet.gambar(l, px0 + 6, iy0 - 1, iy1, tata == Tata.LEBAR && labelTab(), tab, id -> gantiTab(id, true),
					alasan -> tampilPesan(alasan, TempatWarna.BAHAYA));
		}
		if (lembar.ada(LembarKunci.KUNCI_LAYAR)) {
			Lembar k = lembar.sub(LembarKunci.KUNCI_LAYAR);
			KeadaanTerkunci.gambar(l, k.teks(LembarKunci.ALASAN), k.teksAtau(LembarKunci.SYARAT), ix0, iy0, ix1, iy1,
					now - Math.max(0L, terkunciSejak));
		} else {
			try {
				isi(l, ix0, iy0, ix1, iy1);
			} catch (RuntimeException ex) {
				dev.kultivator.Kultivator.LOGGER.warn("Tempat screen {} failed to draw", layar, ex);
				Brush.text(g, font, TeksTempat.k("galat"), ix0 + 4, iy0 + 4, TempatWarna.BAHAYA, 1.0F, false);
			}
		}
		kaki(l);
		if (t < 1.0F) {
			g.fill(px0, py0, px1, py1, Palette.argb(Math.round(200 * (1.0F - e)), gaya.dasar & 0xFFFFFF));
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
			SlipTooltip.draw(g, font, l.tip, mouseX, mouseY, width, height);
		}
	}

	private void kepala(Lukis l) {
		Component judul = lembar.ada(LembarKunci.JUDUL) ? lembar.teks(LembarKunci.JUDUL) : judulBawaan();
		int x = px0 + 10;
		int y = py0 + 6;
		String distrik = lembar.s(LembarKunci.DISTRIK);
		if (!distrik.isEmpty()) {
			int warna = TempatWarna.distrik(distrik);
			int ikon = LembarKunci.Ikon.distrik(distrik);
			l.g.fill(x - 2, y - 1, x + 15, y + 16, Palette.argb(0xC0, warna & 0xFFFFFF));
			IkonTempat.gambar(l.g, ikon, x - 1, y, 16, 0xFFFFFFFF);
			x += 19;
		}
		int kananX = px1 - 10;
		// right side: money, then Nama Baik under it
		long uang = uang();
		if (uang >= 0L) {
			long shown = uangGulir.nilai(uang, l.kini);
			int arah = uangGulir.arah(l.kini);
			int w = LabelHarga.gambar(l, shown, -1L, kananX, y + 1, true, 1.0F, shown >= 1_000_000L);
			if (arah != 0) {
				IkonTempat.gambar(l.g, arah > 0 ? LembarKunci.Ikon.TREN_NAIK : LembarKunci.Ikon.TREN_TURUN, kananX - w - 11, y, 10, 0xFFFFFFFF);
			}
			if (lembar.ada(LembarKunci.BATU)) {
				int[] batu = lembar.ia(LembarKunci.BATU);
				if (l.di(kananX - w, y, kananX, y + 10)) {
					l.tip(List.of(TeksTempat.k("uang.judul"), TeksTempat.batuDiTas(batu)));
				}
			}
			kananX -= w + 14;
		}
		if (lembar.ada(LembarKunci.NAMA_BAIK)) {
			LencanaPangkat.namaBaik(l, lembar.i(LembarKunci.NAMA_BAIK, 0), lembar.i(LembarKunci.PANGKAT_NB, LembarKunci.PANGKAT_ASING), px1 - 10,
					y + 12);
		}
		float diskon = lembar.f(LembarKunci.DISKON, 0.0F);
		if (diskon > 0.001F) {
			Component d = TeksTempat.k("diskon", Math.round(diskon * 100));
			int dw = Math.round(font.width(d) * 0.75F) + 6;
			int dx = kananX - dw;
			l.g.fill(dx, y, dx + dw, y + 9, TempatWarna.SEGEL);
			Brush.text(l.g, font, d, dx + 3, y + 1, 0xFFFFF0E0, 0.75F, false);
			kananX = dx - 4;
		}
		if (lembar.b(UJI)) {
			IkonTempat.pita(l.g, true, kananX - 34, y, 32, 11, 0xFFFFFFFF);
			Brush.centered(l.g, font, TeksTempat.k("uji.pita"), kananX - 18, y + 2, 0xFF5A3A08, 0.75F, false);
			kananX -= 38;
		}
		l.judul(judul, x, y, kananX - x - 4, gaya.gelap() ? TempatWarna.EMAS_TERANG : gaya.aksen);
		Component sub = lembar.teksAtau(LembarKunci.SUBJUDUL);
		Component tempat = lembar.teksAtau(LembarKunci.TEMPAT);
		Component baris2 = sub != null ? sub : tempat;
		TabLentera.Tab aktif = tablet.cari(tab);
		if (tata != Tata.LEBAR && aktif != null) {
			baris2 = baris2 == null ? aktif.label() : aktif.label().copy().append(Component.literal("  ·  ")).append(baris2);
		}
		if (baris2 != null) {
			l.keterangan(baris2, x, y + 13, Math.min(px1 - 70, kananX + 30) - x, gaya.teksLemah);
		}
		PanelTempat.garis(l.g, gaya, px0 + 10, px1 - 10, py0 + KEPALA);
	}

	private void kaki(Lukis l) {
		int y = py1 - KAKI;
		long now = l.kini;
		if (pesan != null && now - pesanSejak < PESAN_MS) {
			float fade = Math.min(1.0F, (PESAN_MS - (now - pesanSejak)) / 600.0F);
			int alpha = Math.round(255 * fade);
			Component p = pesan;
			float w = Math.min(px1 - px0 - 30, font.width(p));
			float x = (px0 + px1) / 2.0F - w / 2.0F;
			l.g.fill(Math.round(x - 5), y - 2, Math.round(x + w + 5), y + 9, Palette.argb(Math.round(0xC0 * fade), 0x101010));
			l.g.fill(Math.round(x - 5), y - 2, Math.round(x - 3), y + 9, Palette.argb(alpha, pesanWarna & 0xFFFFFF));
			Brush.fit(l.g, font, p, x, y, px1 - px0 - 30, Palette.argb(alpha, 0xFFF4EC), 1.0F, 0.6F, false);
			return;
		}
		Component hint = TeksTempat.k(tablet.tabs().isEmpty() ? "kunci.dasar_tanpa_tab" : "kunci.dasar");
		Component extra = petunjuk();
		if (extra != null) {
			hint = hint.copy().append(" · ").append(extra);
		}
		if (cari != null) {
			hint = hint.copy().append(" · ").append(TeksTempat.k("kunci.cari"));
		}
		if (hanyaBaca()) {
			hint = TeksTempat.k("hanya_baca.kaki").copy().append(" · ").append(hint);
		}
		Brush.fitCentered(l.g, font, hint, (px0 + px1) / 2.0F, y + 1, px1 - px0 - 24, gaya.teksSamar, 0.75F, 0.6F, false);
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
		int vw = Math.round(font.width(nilai) * 0.85F);
		l.teks(label, x0, y, x1 - x0 - vw - 4, gaya.teksLemah, 0.85F);
		l.kanan(nilai, x1, y, gaya.teks, 0.85F);
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

	private double vx(double x) {
		return x / skala;
	}

	private double vy(double y) {
		return y / skala;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = vx(event.x());
		double y = vy(event.y());
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		Lukis.Zona z = zonaDi(x, y);
		if (cari != null && cari.aktif() && (z == null || !z.kunci.equals("cari"))) {
			cari.berhenti();
		}
		for (DaftarGulir d : daftarTerakhir) {
			if (d.tekan(x, y)) {
				diseret = d;
				return true;
			}
		}
		for (Pratinjau3D p : pratinjauTerakhir) {
			if (p.berisi(x, y)) {
				diputar = p;
				p.mulaiSeret();
			}
		}
		if (z == null) {
			return diputar != null || super.mouseClicked(event, doubleClick);
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
		double x = vx(event.x());
		double y = vy(event.y());
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
				boolean acted = z.widget.release(x, y);
				if (!acted && !z.aktif && z.berisi(x, y) && z.alasan != null) {
					tampilPesan(z.alasan, TempatWarna.BAHAYA);
				}
			}
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (diseret != null) {
			diseret.seretKe(vy(event.y()));
			return true;
		}
		if (diputar != null) {
			diputar.seret(dx / skala, Util.getMillis());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		double vx = vx(x);
		double vy = vy(y);
		for (DaftarGulir d : daftarTerakhir) {
			if (d.berisi(vx, vy)) {
				d.roda(scrollY);
				return true;
			}
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (cari != null && cari.tombol(event)) {
			return true;
		}
		int key = event.key();
		if (event.isEscape()) {
			onClose();
			return true;
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
		if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) {
			if (angka(key - InputConstants.KEY_1 + 1)) {
				return true;
			}
			TabLentera.Tab t = tablet.tab(key - InputConstants.KEY_1);
			if (t != null) {
				if (t.kunci() != null) {
					UiSound.denied();
					tampilPesan(t.kunci(), TempatWarna.BAHAYA);
				} else {
					gantiTab(t.id(), true);
				}
				return true;
			}
		}
		if (tombolLain(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (cari != null && cari.ketik(event)) {
			return true;
		}
		return super.charTyped(event);
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

	@Override
	public void removed() {
		UiTempatClient.ditutup(this);
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
