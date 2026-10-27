package dev.kultivator.client.tempat.ui;

import dev.kultivator.client.hub.DiSini;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanTerkunci;
import dev.kultivator.client.screen.ui.LencanaPangkat;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The sheet model of every "Pasar Giok" sheet (DESIGN 38.U), a page of the Gulungan Utama (DESIGN 43.6): the Gulungan
 * draws the frame (title, purse, "?" seal, footer) and the subclass the sheet's content in the page box. The old kit-38
 * drawing and input engine is gone (43B finish); this class only keeps the sheet's state, drawing nothing of its own
 * except the helpers {@link #stripInfo} and {@link #kunciLayar} that the two helper bases ({@code LayarInti},
 * {@code TemaHalaman}) call with the single kit {@code screen/ui}.
 * <ul>
 * <li><b>Sheet</b>: {@link #perbarui} takes the server's sheet ({@link LembarKunci}); a live refresh keeps tab, scroll,
 * selection and focus; an open jumps to the sheet's {@code tab}. Countdowns run from the moment the sheet arrived
 * ({@link #diterima}). The first sheet is applied on first use, when every subclass field exists (F2).</li>
 * <li><b>Tabs</b> are the page's subs ({@link #subs}): the screen's tabs minus {@code tab_sembunyi}, with {@code tab_kunci}
 * locks, and, on a home page, only the mounted tabs ({@link #batasTab}).</li>
 * <li><b>Layout</b> from the box width (38.U.2): below 320 one column ({@link Tata#SEMPIT}), 320-479 list + detail
 * ({@link Tata#SEDANG}), from 480 three columns ({@link Tata#LEBAR}).</li>
 * <li><b>Read-only views</b> (a home page's hub view, {@code OP_LIHAT}): write buttons are drawn disabled with the reason
 * ({@link #hanyaBaca}).</li>
 * </ul>
 * {@code minecraft} and {@code font} come from {@link Page}; input goes through the page's {@code fokusRing}.
 */
public abstract class LayarTempatDasar extends Page implements UiTempatClient.LayarTempat {

	/** Key of the {@code uji ui skala} test (the server still sends it; the single kit lays out in the real box). */
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
	protected Palette.Gaya gaya;
	/** The screen's tab list (drawn by the Gulungan as subs). */
	protected List<TabBar.Tab> tablet = new ArrayList<>();
	protected String tab = "";
	/** The screen's own name (the fallback of {@link #judulBawaan} when the sheet has no {@code judul}). */
	protected final Component title;
	private long terkunciSejak = -1L;
	private String opTerakhir = "";
	private long opSejak;
	private boolean lihatSaja;
	private Set<String> batas = Set.of();
	private @Nullable Component pesanTunda;
	private int pesanTundaWarna;
	protected Tata tata = Tata.SEDANG;

	protected LayarTempatDasar(String layar, int npcId, CompoundTag lembar, Component judul, Palette.Gaya gayaBawaan) {
		this.title = judul;
		this.layar = layar;
		this.npcId = npcId;
		this.gaya = gayaBawaan;
		// F2: the hooks (tabs, setelahLembar, tabBerubah) run on first use, not from this constructor, where a subclass's
		// own field initialisers have not run yet (LayarTokoSenjata.tabBerubah -> daftar.mulaiUlang crashed).
		this.lembar = new Lembar(lembar);
		this.tertunda = lembar;
	}

	/** A text of the 38 sheet fragment ({@code kultivator.tempat_ui.<kunci>}). */
	private static MutableComponent tk(String kunci, Object... args) {
		return Component.translatable("kultivator.tempat_ui." + kunci, args);
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
		gaya = Palette.Gaya.dari(lembar.s(LembarKunci.GAYA), gaya);
		tablet = saringTab(tabs());
		String diminta = lembar.s(LembarKunci.TAB);
		if ((buka || tab.isEmpty()) && !diminta.isEmpty() && tabBoleh(diminta)) {
			gantiTab(diminta, false);
		}
		pastikanTab();
		Component p = lembar.teksAtau(LembarKunci.PESAN);
		if (p != null && (lama == null || !p.getString().equals(lama.teks(LembarKunci.PESAN).getString()) || buka)) {
			tampilPesan(p, Palette.status(lembar.s(LembarKunci.PESAN_JENIS, LembarKunci.PESAN_OK)));
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

	private @Nullable TabBar.Tab tabDari(String id) {
		for (TabBar.Tab t : tablet) {
			if (t.id().equals(id)) {
				return t;
			}
		}
		return null;
	}

	/** The shown tab exists and is open, else the first tab. */
	private void pastikanTab() {
		if (tab.isEmpty() || tabDari(tab) == null) {
			gantiTab(tablet.isEmpty() ? "" : tablet.get(0).id(), false);
		}
	}

	// =================================================================== for the router and home pages (43.12.4)

	/** Shows only tabs {@code ids} (a home page's mounted tabs; empty = all). */
	@Override
	public void batasTab(Set<String> ids) {
		batas = ids == null ? Set.of() : Set.copyOf(ids);
		if (tertunda == null) {
			tablet = saringTab(tabs());
			pastikanTab();
		}
	}

	/** Marks this page a read-only hub view ({@code OP_LIHAT} answer): write buttons are disabled with the reason. */
	@Override
	public void lihatSaja(boolean baca) {
		lihatSaja = baca;
		tataUlang();
	}

	public boolean lihatSaja() {
		return lihatSaja;
	}

	/** The tab shown now (after the first sheet is applied). */
	@Override
	public String tabSekarang() {
		siapkan();
		return tab;
	}

	// =================================================================== for subclasses

	/** The screen's tabs in order (ids of the screen's {@code TABS}); empty = no tabs. */
	protected abstract List<TabBar.Tab> tabs();

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
	protected boolean tombolLain(net.minecraft.client.input.KeyEvent event) {
		return false;
	}

	/** Largest content width for this screen per layout (narrow screens like the bill override it). */
	protected int panelMaks(Tata t) {
		return t == Tata.LEBAR ? 600 : 470;
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
		if (tablet.size() < 2) {
			return List.of();
		}
		List<Sub> subs = new ArrayList<>(tablet.size());
		for (TabBar.Tab t : tablet) {
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
		return gaya;
	}

	/** A service opened from a talk keeps the talk one step back (43.8.1.3). */
	@Override
	public boolean rinci() {
		return DiSini.bisaKembali(this);
	}

	@Override
	public boolean mundur() {
		return DiSini.kembali(this);
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
			} else if (DiSini.halaman() == this
					|| DiSini.halaman() instanceof dev.kultivator.client.hub.LembarHub.Halaman h && h.isi() == this) {
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

	private List<TabBar.Tab> saringTab(List<TabBar.Tab> semua) {
		List<String> sembunyi = lembar.sl(LembarKunci.TAB_SEMBUNYI);
		Lembar kunci = lembar.sub(LembarKunci.TAB_KUNCI);
		List<TabBar.Tab> out = new ArrayList<>(semua.size());
		for (TabBar.Tab t : semua) {
			if (sembunyi.contains(t.id()) || !tabAda(t.id()) || !batas.isEmpty() && !batas.contains(t.id())) {
				continue;
			}
			Component alasan = kunci.teksAtau(t.id());
			out.add(alasan == null ? t : t.terkunci(alasan));
		}
		return out;
	}

	/** Whether tab {@code id} exists for this sheet (screens hide tabs whose data block is absent, e.g. dialog "Jasa"). */
	protected boolean tabAda(String id) {
		return true;
	}

	private boolean tabBoleh(String id) {
		TabBar.Tab t = tabDari(id);
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
		return tk("hanya_baca");
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

	/** Closes the Gulungan (the session ends with it): a sheet's own "Tutup" button. */
	public void onClose() {
		dev.kultivator.client.hub.HubNav.tutupSemua();
	}

	// =================================================================== layout

	private void tataUlang() {
		if (w <= 0) {
			return;
		}
		tata = w < 320 ? Tata.SEMPIT : w < 480 ? Tata.SEDANG : Tata.LEBAR;
	}

	/** True when the sheet carries something for the info strip over the content. */
	protected final boolean adaStrip() {
		return lembar != null && (!lembar.s(LembarKunci.DISTRIK).isEmpty() || lembar.ada(LembarKunci.SUBJUDUL) || lembar.ada(LembarKunci.TEMPAT)
				|| lembar.ada(LembarKunci.NAMA_BAIK) || lembar.f(LembarKunci.DISKON, 0.0F) > 0.001F || lembar.b(UJI) || hanyaBaca());
	}

	// =================================================================== strip and lock on the single kit (LayarInti, TemaHalaman)

	/**
	 * The info strip over the content (what the old header carried besides title and money), drawn with the single kit:
	 * district, subtitle or place, then from the right "only viewing", the test ribbon, the discount and the place's Nama
	 * Baik. Returns the height it takes from the content (0 when the sheet has nothing for it).
	 */
	protected final int stripInfo(Lukis l, int x0, int y, int x1) {
		if (!adaStrip()) {
			return 0;
		}
		int x = x0 + 2;
		int kanan = x1 - 2;
		String distrik = lembar.s(LembarKunci.DISTRIK);
		if (!distrik.isEmpty()) {
			l.g.fill(x - 1, y, x + 12, y + STRIP, Palette.argb(0xC0, Palette.distrik(distrik) & 0xFFFFFF));
			Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.distrik(distrik), x, y + 1, 10, 0xFFFFFFFF);
			x += 15;
		}
		if (hanyaBaca()) {
			Component b = tk("hanya_baca.kaki");
			int bw = Math.min(kanan - x - 20, l.font.width(b) + 6);
			if (bw > 20) {
				l.g.fill(kanan - bw, y, kanan, y + STRIP, Palette.argb(0x60, Palette.INK_FAINT & 0xFFFFFF));
				l.teks(b, kanan - bw + 3, y + 2, bw - 6, l.gaya.teks);
				kanan -= bw + 4;
			}
		}
		if (lembar.b(UJI) && kanan - x > 60) {
			Glyphs.Atlas.pita(l.g, true, kanan - 34, y, 32, STRIP, 0xFFFFFFFF);
			l.tengah(tk("uji.pita"), kanan - 18, y + 2, 30, 0xFF5A3A08);
			kanan -= 38;
		}
		float diskon = lembar.f(LembarKunci.DISKON, 0.0F);
		if (diskon > 0.001F) {
			Component d = tk("diskon", Math.round(diskon * 100));
			int dw = l.font.width(d) + 6;
			if (kanan - dw - x > 40) {
				l.g.fill(kanan - dw, y, kanan, y + STRIP, Palette.SEAL);
				l.teks(d, kanan - dw + 3, y + 2, dw - 4, 0xFFFFF0E0);
				kanan -= dw + 4;
			}
		}
		if (lembar.ada(LembarKunci.NAMA_BAIK) && kanan - x > 80) {
			kanan -= LencanaPangkat.namaBaik(l, lembar.i(LembarKunci.NAMA_BAIK, 0),
					lembar.i(LembarKunci.PANGKAT_NB, LembarKunci.PANGKAT_ASING), kanan, y + 2) + 4;
		}
		Component sub = lembar.teksAtau(LembarKunci.SUBJUDUL);
		Component baris = sub != null ? sub : lembar.teksAtau(LembarKunci.TEMPAT);
		if (baris != null && kanan - x > 12) {
			l.teks(baris, x, y + 2, kanan - x - 2, l.gaya.teksLemah);
		}
		Component hint = petunjuk();
		if (hint != null && l.di(x0, y, x1, y + STRIP)) {
			l.tip(hint);
		}
		l.garis(x0, x1, y + STRIP + 1);
		return STRIP + 3;
	}

	/** The locked sheet ({@code kunci_layar}) over the box with the single kit; true when drawn (draw nothing else). */
	protected final boolean kunciLayar(Lukis l, int x0, int y0, int x1, int y1) {
		if (!lembar.ada(LembarKunci.KUNCI_LAYAR)) {
			return false;
		}
		Lembar k = lembar.sub(LembarKunci.KUNCI_LAYAR);
		KeadaanTerkunci.gambar(l, k.teks(LembarKunci.ALASAN), k.teksAtau(LembarKunci.SYARAT), x0, y0, x1, y1,
				Util.getMillis() - Math.max(0L, terkunciSejak));
		return true;
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
}
