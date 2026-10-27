package dev.kultivator.client.hub;

import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.tempat.ui.UiTempatClient;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The sheet mounter of the Gulungan Utama (DESIGN 43.6, 43.12.4): installs {@link HubPages#pembuatLembar} so a module
 * registers a sheet of 38/41 as a page with {@code HubPages.daftarLembar(bagian, id, urutan, layar, tabs)}, and finds the
 * home page of a sheet ({@link #rumah}). The page of a mounted sheet is a {@link Halaman}: one stable page per
 * (screen id, tab set), whose content is the sheet page of the last sheet that came for it: the read-only hub view it asks
 * for while shown ({@code OP_LIHAT}), or the live sheet of a world context (an NPC, a counter) whose home it is. Only the
 * mounted tabs appear as subs. Client thread only.
 */
public final class LembarHub {

	private static final String K = "kultivator.gui43.konteks.";
	/** Section names of the page-name keys {@code kultivator.gui43.hal.<bagian>.<halaman>} (43.15). */
	private static final String[] BAGIAN = {"diri", "teknik", "murid", "sekte", "dunia", "keabadian", "panduan", "pengaturan"};
	private static final long ULANG_MS = 3000L;
	private static final int CHIP_H = 14;

	private static final Map<String, Halaman> HALAMAN = new HashMap<>();
	private static boolean siap;

	/** Installs the sheet page builder (once, from {@code UiTempatClient.init}). */
	public static void init() {
		if (siap) {
			return;
		}
		siap = true;
		HubPages.pembuatLembar(LembarHub::halaman);
	}

	private static Page halaman(String layar, Set<String> tab) {
		return HALAMAN.computeIfAbsent(kunci(layar, tab), k -> new Halaman(layar, tab));
	}

	private static String kunci(String layar, Set<String> tab) {
		return layar + "|" + String.join(",", new TreeSet<>(tab));
	}

	/** The mounted page of registration {@code e} (built now when the host has not built it yet). */
	public static Halaman dari(HubPages.Entri e) {
		return (Halaman) halaman(e.layar(), e.tab());
	}

	/** The sheet mount of home {@code r} ({@link HubPages#daftarRumah}): the same page object its holder mounts. */
	public static Halaman dari(HubPages.Rumah r) {
		return (Halaman) halaman(r.layar(), r.tab());
	}

	/** The home page that asked for a read-only view of {@code layar} in the last 3 s, or null. */
	public static @Nullable Halaman menunggu(String layar) {
		long now = Util.getMillis();
		for (Halaman h : HALAMAN.values()) {
			if (h.layar.equals(layar) && now - h.minta <= ULANG_MS) {
				return h;
			}
		}
		return null;
	}

	/** The sheet page of {@code layar} / {@code npcId} held by a home page (the one drawn last when several hold it), or null. */
	public static @Nullable UiTempatClient.LayarTempat cari(String layar, int npcId) {
		UiTempatClient.LayarTempat found = null;
		long last = Long.MIN_VALUE;
		for (Halaman h : HALAMAN.values()) {
			if (h.isi instanceof UiTempatClient.LayarTempat t && t.layar().equals(layar) && t.npcId() == npcId && h.dilihat >= last) {
				found = t;
				last = h.dilihat;
			}
		}
		return found;
	}

	/** Forgets the held sheets (disconnect). */
	public static void reset() {
		for (Halaman h : HALAMAN.values()) {
			h.isi = null;
			h.konteks = false;
			h.bersama = null;
			h.minta = Long.MIN_VALUE / 2;
		}
	}

	private LembarHub() {
	}

	/**
	 * The page of a mounted sheet. Its content is a sheet page built by {@code UiTempatClient} from the last sheet: until one
	 * arrives it reads "memuat…" and asks the server (only while drawn, 43.0.12). With a world context it shows a chip
	 * "bersama &lt;nama&gt; ×" over the content (× ends the context and returns to the read-only view).
	 */
	public static final class Halaman extends Page {

		private final String layar;
		private final Set<String> tab;
		private @Nullable Page isi;
		private boolean konteks;
		private @Nullable Component bersama;
		private long minta = Long.MIN_VALUE / 2;
		/** When this page was last drawn (which of several pages of one sheet the server's refresh is for). */
		private long dilihat = Long.MIN_VALUE / 2;
		private String subTunda = "";

		Halaman(String layar, Set<String> tab) {
			this.layar = layar;
			this.tab = Set.copyOf(tab);
		}

		/** The read-only hub view arrived (it answers this page's {@code OP_LIHAT}, or a transition card). */
		public void isiHub(Page page) {
			pasangIsi(page, false, null);
		}

		/** A world context whose home this page is: its live sheet page, talking with {@code nama}. */
		public void isiKonteks(Page page, Component nama) {
			pasangIsi(page, true, nama);
		}

		/** The context ended (server side, or ×): drop the live sheet; the read-only view is asked again when drawn. */
		void konteksBerakhir() {
			if (konteks) {
				isi = null;
				konteks = false;
				bersama = null;
				minta = Long.MIN_VALUE / 2;
				tataUlang();
			}
		}

		private void pasangIsi(Page page, boolean hidup, @Nullable Component nama) {
			isi = page;
			konteks = hidup;
			bersama = nama;
			page.batasTab(tab);
			page.lihatSaja(!hidup);
			if (!subTunda.isEmpty()) {
				page.showSub(subTunda);
				subTunda = "";
			}
			if (screen != null) {
				page.pasang(screen);
			}
			tataUlang();
			// The Gulungan picked the sub before the sheet existed: show the sheet's tab as the chosen sub.
			HubPages.Entri e = entri();
			if (screen != null && e != null) {
				Tujuan di = screen.tempat();
				String t = page.tabSekarang();
				if (!t.isEmpty() && di.bagian() == e.bagian() && di.halaman().equals(e.id()) && !t.equals(di.sub())) {
					screen.tuju(Tujuan.of(e.bagian(), e.id()).sub(t));
				}
			}
		}

		private void tataUlang() {
			if (w > 0 && h > 0) {
				layout(x, y, w, h);
			}
		}

		private void mintaLihat() {
			minta = Util.getMillis();
			UiTempatClient.kirim(layar, -1, LembarKunci.OP_LIHAT);
		}

		/** The registration of this mount, or null. */
		private @Nullable HubPages.Entri entri() {
			for (int b = 0; b < HubNav.SECTIONS; b++) {
				for (HubPages.Entri e : HubPages.dari(b)) {
					if (layar.equals(e.layar()) && tab.equals(e.tab())) {
						return e;
					}
				}
			}
			return null;
		}

		/** The page-name key of this mount (43.15), from its registration. */
		private @Nullable String kunciNama() {
			HubPages.Entri e = entri();
			return e == null ? null : "kultivator.gui43.hal." + BAGIAN[e.bagian()] + "." + e.id();
		}

		/** A sub name while the sheet loads: {@code kultivator.gui43.sub.<halaman>.<sub>} (43.15), else the tab id. */
		private Component namaSub(String id) {
			HubPages.Entri e = entri();
			String k = e == null ? null : "kultivator.gui43.sub." + e.id() + "." + id;
			return k != null && Language.getInstance().has(k) ? Component.translatable(k) : Component.literal(id);
		}

		// ============================================================== identity

		@Override
		public Component title() {
			String k = kunciNama();
			if (k != null && Language.getInstance().has(k)) {
				return Component.translatable(k);
			}
			return isi != null ? isi.title() : Component.literal(layar);
		}

		@Override
		public int glyph() {
			return isi != null ? isi.glyph() : Glyphs.HALL;
		}

		@Override
		public String helpTopic() {
			return isi != null ? isi.helpTopic() : layar;
		}

		/** The sheet's tabs; while it loads, the mounted tabs (so the Gulungan's chosen sub reaches the sheet). */
		@Override
		public List<Sub> subs() {
			if (isi != null) {
				return isi.subs();
			}
			if (tab.size() < 2) {
				return List.of();
			}
			List<Sub> subs = new ArrayList<>(tab.size());
			for (String id : new TreeSet<>(tab)) {
				subs.add(new Sub(id, namaSub(id)));
			}
			return subs;
		}

		@Override
		public void showSub(String id) {
			if (isi != null) {
				isi.showSub(id);
			} else {
				subTunda = id == null ? "" : id;
			}
		}

		@Override
		public boolean lebar() {
			return isi != null && isi.lebar();
		}

		@Override
		public long uang() {
			return isi != null ? isi.uang() : -1L;
		}

		/** A service opened from a talk keeps the talk one step back (43.8.1.3); this holder is the context page. */
		@Override
		public boolean rinci() {
			return DiSini.bisaKembali(this) || isi != null && isi.rinci();
		}

		@Override
		public Palette.Gaya gaya() {
			return isi != null ? isi.gaya() : Palette.Gaya.KERTAS;
		}

		@Override
		public boolean angka(int n) {
			return isi != null && isi.angka(n);
		}

		// ============================================================== life

		@Override
		protected void build() {
			if (isi != null) {
				int top = konteks ? CHIP_H : 0;
				isi.layout(x, y + top, w, Math.max(20, h - top));
			}
		}

		@Override
		public void onShow(@Nullable CompoundTag fokus) {
			if (!konteks) {
				mintaLihat();
			}
			if (isi != null) {
				isi.onShow(fokus);
			}
		}

		@Override
		public void onHide() {
			// 43.8.1.4: a page holding this live context was only hidden (another section or sub); the Gulungan closing empties
			// the host's context first, so then the session does end here.
			if (konteks && DiSini.halaman() == this && dev.kultivator.client.screen.CultivationScreen.adaKonteks()) {
				return;
			}
			if (isi != null) {

				isi.onHide();
			}
			if (konteks) {
				// shown as the context (Gulungan closed, or a new context replaced it): the live session is over.
				isi = null;
				konteks = false;
				bersama = null;
				DiSini.lepas(this);
			}
		}

		@Override
		public void tick() {
			if (isi != null) {
				isi.tick();
			}
		}

		@Override
		public void onData() {
			if (isi != null) {
				isi.onData();
			}
		}

		@Override
		public boolean needsRebuild() {
			return isi != null && isi.needsRebuild();
		}

		// ============================================================== drawing

		@Override
		protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
			dilihat = Util.getMillis();
			if (isi == null) {
				if (Util.getMillis() - minta > ULANG_MS) {
					mintaLihat();
				}
				Brush.fitCentered(g, font, Component.translatable(K + "memuat"), x + w / 2.0F, y + h / 2.0F - 4.0F, w - 8.0F, Palette.INK_FAINT, 1.0F,
						1.0F, false);
				return;
			}
			isi.render(g, mouseX, mouseY, seconds);
		}

		@Override
		protected void lukis(Lukis l) {
			if (!konteks || isi == null) {
				return;
			}
			Component nama = Component.translatable(K + "bersama", bersama == null ? Component.empty() : bersama);
			int x1 = x + w;
			int tutupX0 = x1 - 16;
			Brush.rect(l.g, x, y, x1, y + CHIP_H - 2, Palette.argb(0x40, Palette.TALISMAN));
			l.teks(nama, x + 4, y + (CHIP_H - 10) / 2.0F, tutupX0 - x - 8, Palette.INK);
			boolean over = l.di(tutupX0, y, x1, y + CHIP_H - 2);
			Brush.rect(l.g, tutupX0, y, x1, y + CHIP_H - 2, Palette.argb(over ? 0x90 : 0x50, Palette.SEAL));
			l.tengah(Component.literal("×"), tutupX0 + 8, y + (CHIP_H - 10) / 2.0F, 14, Palette.INK);
			l.zona("konteks_tutup", tutupX0, y, x1, y + CHIP_H - 2, this::akhiri, true, null, true);
			if (over) {
				l.tip(Component.translatable(K + "bersama.tutup"));
			}
		}

		/** ×: ends the world context here (the server session closes) and goes back to the read-only view. */
		private void akhiri() {
			if (isi instanceof UiTempatClient.LayarTempat t) {
				UiTempatClient.kirim(t.layar(), t.npcId(), LembarKunci.OP_TUTUP);
			}
			UiSound.press();
			if (DiSini.halaman() == this) {
				DiSini.selesai(null);
			} else {
				konteksBerakhir();
			}
		}

		// ============================================================== input

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (super.mouseClicked(event, doubleClick)) {
				return true;
			}
			return isi != null && isi.mouseClicked(event, doubleClick);
		}

		@Override
		public boolean mouseReleased(MouseButtonEvent event) {
			boolean a = isi != null && isi.mouseReleased(event);
			return super.mouseReleased(event) || a;
		}

		@Override
		public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
			return isi != null && isi.mouseDragged(event, dx, dy) || super.mouseDragged(event, dx, dy);
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
			return isi != null && isi.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			return isi != null && isi.keyPressed(event);
		}

		@Override
		public boolean keyReleased(KeyEvent event) {
			return isi != null && isi.keyReleased(event);
		}

		@Override
		public boolean charTyped(CharacterEvent event) {
			return isi != null && isi.charTyped(event);
		}

		@Override
		public boolean mauTombol() {
			return isi != null && isi.mauTombol();
		}

		@Override
		public boolean modal() {
			return isi != null && isi.modal();
		}

		@Override
		public boolean mundur() {
			return isi != null && isi.mundur() || DiSini.kembali(this);
		}

		@Override
		public boolean fokusTab(boolean back) {
			return isi != null && isi.fokusTab(back);
		}

		/** The sheet page held now (null while loading). */
		public @Nullable Page isi() {
			return isi;
		}
	}
}
