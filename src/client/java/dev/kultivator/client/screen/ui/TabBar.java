package dev.kultivator.client.screen.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The single tab bar of the kit (DESIGN 43.7, G05/G06): the Gulungan's rail of sections (vertical jade tablets), the
 * header's page coins (horizontal jade coins) and the sub bar (flat chips). One class for every level of navigation, so a
 * tab looks and behaves the same everywhere:
 * <ul>
 * <li>a locked tab is drawn dim with a padlock; its reason is the tooltip and, when clicked, the footer message;</li>
 * <li>a hidden tab ({@link Tab#tampil} false) takes no room;</li>
 * <li>with {@code label} false only the glyph is drawn (narrow layouts) and the name becomes the tooltip;</li>
 * <li>tabs that do not fit scroll, with "‹" / "›" arrows at the ends, the active tab kept in view;</li>
 * <li>the bar is one stop of the Tab ring; ←/→ (or ↑/↓ on a vertical bar's owner) move to the next open tab.</li>
 * </ul>
 * Text is 1.0 (43.0.5). The bar keeps only its scroll and hover lights; the active id belongs to the owner.
 */
public final class TabBar {

	/** One tab: id, name, glyph ({@link Glyphs#tab} icon, -1 none), lock reason (null = open), shown or hidden. */
	public record Tab(String id, Component label, int glyph, @Nullable Component kunci, boolean tampil) {

		public static Tab of(String id, Component label, int glyph) {
			return new Tab(id, label, glyph, null, true);
		}

		public static Tab of(String id, Component label) {
			return new Tab(id, label, -1, null, true);
		}

		/** The same tab locked with {@code alasan} (null: open). */
		public Tab terkunci(@Nullable Component alasan) {
			return new Tab(id, label, glyph, alasan, tampil);
		}

		/** The same tab shown or hidden. */
		public Tab tampil(boolean tampil) {
			return new Tab(id, label, glyph, kunci, tampil);
		}
	}

	/** Drawing style: rail tablets, header coins or sub chips. */
	public enum Rupa {
		TABLET, KOIN, SUB
	}

	/** Overflow arrow click boxes: the kit's 16x12 minimum (wide on a horizontal bar, tall on the rail). */
	private static final int PANAH = TalismanButton.MIN_W;
	private static final int PANAH_TEGAK = TalismanButton.MIN_H;

	private final Map<String, Eased> nyala = new HashMap<>();
	private List<Tab> tabs = new ArrayList<>();
	private int awal;
	/** The active id the scroll was last kept around: the arrows' scroll survives until the active tab changes. */
	private @Nullable String aktifTerakhir;
	private final List<Tab> terlihat = new ArrayList<>();

	public void setTabs(List<Tab> tabs) {
		this.tabs = tabs;
		terlihat.clear();
		for (Tab t : tabs) {
			if (t.tampil()) {
				terlihat.add(t);
			}
		}
		awal = Math.min(awal, Math.max(0, terlihat.size() - 1));
	}

	public List<Tab> tabs() {
		return tabs;
	}

	/** The shown tabs in order. */
	public List<Tab> terlihat() {
		return terlihat;
	}

	public @Nullable Tab cari(String id) {
		for (Tab t : tabs) {
			if (t.id().equals(id)) {
				return t;
			}
		}
		return null;
	}

	/** The next open shown tab from {@code aktif} in direction {@code arah} (wrapping); null when there is none. */
	public @Nullable String tetangga(String aktif, int arah) {
		int n = terlihat.size();
		if (n == 0) {
			return null;
		}
		int i = 0;
		for (int k = 0; k < n; k++) {
			if (terlihat.get(k).id().equals(aktif)) {
				i = k;
				break;
			}
		}
		for (int step = 1; step <= n; step++) {
			Tab t = terlihat.get(Math.floorMod(i + arah * step, n));
			if (t.kunci() == null) {
				return t.id().equals(aktif) ? null : t.id();
			}
		}
		return null;
	}

	/** Height of one rail tablet for {@code jumlah} tablets in {@code tersedia} px (43.3.2: 18..36). */
	public static int tinggiTablet(int tersedia, int jumlah) {
		return jumlah <= 0 ? 36 : Math.max(18, Math.min(36, (tersedia - 4) / jumlah));
	}

	/**
	 * Draws the bar in the box. {@code tegak} = vertical (rail); {@code label} = names beside / on the tabs; {@code aktif}
	 * the owner's current id; {@code pilih} receives a click (or ←/→) on an open tab other than the active one.
	 */
	public void gambar(Lukis l, String kunci, int x0, int y0, int x1, int y1, boolean tegak, Rupa rupa, boolean label, String aktif,
			Consumer<String> pilih) {
		Lukis.Zona bar = l.zona(kunci, x0, y0, x1, y1, null, true, null, true);
		if (bar != null) {
			bar.geser = arah -> {
				String next = tetangga(aktif, arah);
				if (next != null) {
					UiSound.tab();
					pilih.accept(next);
				}
			};
		}
		if (tegak) {
			tegak(l, kunci, x0, y0, x1, y1, rupa, label, aktif, pilih);
		} else {
			mendatar(l, kunci, x0, y0, x1, y1, rupa, label, aktif, pilih);
		}
		l.fokusCincin(kunci, x0, y0, x1, y1);
	}

	// ------------------------------------------------------------------ vertical (rail)

	private void tegak(Lukis l, String kunci, int x0, int y0, int x1, int y1, Rupa rupa, boolean label, String aktif, Consumer<String> pilih) {
		int n = terlihat.size();
		int per = tinggiTablet(y1 - y0, n);
		int muat = Math.max(1, (y1 - y0) / per);
		boolean luap = n > muat;
		int ruang = luap ? Math.max(1, (y1 - y0 - 2 * PANAH_TEGAK) / per) : n;
		jagaAktif(aktif, ruang);
		int top = luap ? y0 + PANAH_TEGAK : y0;
		if (luap) {
			panah(l, kunci + "#naik", x0, y0, x1, y0 + PANAH_TEGAK, "▲", awal > 0, () -> awal = Math.max(0, awal - 1));
			panah(l, kunci + "#turun", x0, y1 - PANAH_TEGAK, x1, y1, "▼", awal + ruang < n, () -> awal = Math.min(n - ruang, awal + 1));
		}
		for (int k = 0; k < ruang && awal + k < n; k++) {
			Tab t = terlihat.get(awal + k);
			int ty = top + k * per;
			tablet(l, kunci, t, x0, ty + 1, x1, ty + per - 1, label, t.id().equals(aktif), pilih);
		}
	}

	private void tablet(Lukis l, String kunci, Tab t, int x0, int y0, int x1, int y1, boolean label, boolean on, Consumer<String> pilih) {
		boolean locked = t.kunci() != null;
		boolean over = l.di(x0, y0, x1, y1);
		float glow = nyala.computeIfAbsent(t.id(), k -> new Eased(12.0F)).to(on ? 1.0F : over ? 0.45F : 0.0F, l.detik);
		int out = Math.round(glow * 3.0F);
		int bx = x0 + 3 - out;
		if (on) {
			Brush.chamfer(l.g, bx - 2, y0 - 1, x1 + 1, y1 + 1, 3, Palette.argb(0x60, Palette.GOLD_LIGHT));
		}
		l.g.fill(bx + 1, y0 + 1, x1 + 1, y1 + 1, 0x40000000);
		int top = locked ? Palette.grey(Palette.JADE, 0.8F) : Palette.mix(Palette.mix(Palette.JADE, Palette.JADE_LIGHT, 0.2F), Palette.JADE_LIGHT, 0.5F * glow);
		int bottom = locked ? Palette.grey(Palette.JADE_DARK, 0.8F) : Palette.mix(Palette.JADE_DARK, Palette.JADE, 0.5F * glow);
		Brush.chamferGradient(l.g, bx, y0, x1, y1, 2, top, bottom);
		Brush.frame(l.g, bx + 1, y0 + 1, x1 - 1, y1 - 1, on ? Palette.argb(0xC0, Palette.GOLD) : Palette.argb(0x70, Palette.JADE_DARK));
		int ink = locked ? Palette.argb(0xA0, Palette.JADE_PALE) : on ? Palette.GOLD_LIGHT : Palette.mix(Palette.JADE_PALE, 0xFFFFFFFF, glow * 0.4F);
		int h = y1 - y0;
		float gs = Math.min(10.0F, h - 6.0F);
		float gcx = label ? bx + 4.0F + gs / 2.0F : (bx + x1) / 2.0F;
		if (t.glyph() >= 0) {
			Glyphs.tab(l.g, t.glyph(), gcx, (y0 + y1) / 2.0F, gs, ink);
		}
		if (label) {
			float tx = t.glyph() >= 0 ? gcx + gs / 2.0F + 3.0F : bx + 4.0F;
			l.teks(on ? Brush.bold(t.label()) : t.label(), tx, y0 + (h - 8) / 2.0F, x1 - 3 - tx, ink);
		} else if (t.glyph() < 0) {
			l.tengah(Component.literal(t.label().getString().isEmpty() ? "?" : t.label().getString().substring(0, 1)), (bx + x1) / 2.0F,
					y0 + (h - 8) / 2.0F, x1 - bx - 2, ink);
		}
		if (locked) {
			Glyphs.lock(l.g, x1 - 5.0F, y1 - 5.0F, 5.0F, Palette.argb(0xE0, Palette.SEAL));
		}
		zonaTab(l, kunci, t, x0, y0, x1, y1, on, over, label, pilih);
	}

	// ------------------------------------------------------------------ horizontal (coins, sub chips)

	private void mendatar(Lukis l, String kunci, int x0, int y0, int x1, int y1, Rupa rupa, boolean label, String aktif, Consumer<String> pilih) {
		int n = terlihat.size();
		int h = y1 - y0;
		int[] lebar = new int[n];
		int total = 0;
		for (int i = 0; i < n; i++) {
			Tab t = terlihat.get(i);
			int isi = label || t.glyph() < 0 ? l.font.width(Brush.bold(t.label())) + (t.glyph() >= 0 && label ? 12 : 0) : 10;
			lebar[i] = Math.max(TalismanButton.MIN_W, isi + (rupa == Rupa.KOIN ? 12 : 8));
			total += lebar[i] + 2;
		}
		boolean luap = total > x1 - x0;
		int kiri = luap ? x0 + PANAH : x0;
		int kanan = luap ? x1 - PANAH : x1;
		if (luap) {
			// keep the active tab in view when it changes (not every frame: the arrows would be undone)
			int ai = indeks(aktif);
			if (ai >= 0 && aktifBaru(aktif)) {
				awal = Math.min(awal, ai);
				while (awal < ai && lebarDari(lebar, awal, ai) > kanan - kiri) {
					awal++;
				}
			}
			int muat = 0;
			int sisa = kanan - kiri;
			for (int i = awal; i < n && sisa >= lebar[i]; i++) {
				sisa -= lebar[i] + 2;
				muat++;
			}
			final int m = muat;
			panah(l, kunci + "#kiri", x0, y0, x0 + PANAH, y1, "‹", awal > 0, () -> awal = Math.max(0, awal - 1));
			panah(l, kunci + "#kanan", x1 - PANAH, y0, x1, y1, "›", awal + m < n, () -> awal = Math.min(n - 1, awal + 1));
		} else {
			awal = 0;
		}
		int x = kiri;
		for (int i = awal; i < n; i++) {
			if (x + lebar[i] > kanan) {
				break;
			}
			Tab t = terlihat.get(i);
			boolean on = t.id().equals(aktif);
			if (rupa == Rupa.KOIN) {
				koin(l, kunci, t, x, y0, x + lebar[i], y1, label, on, pilih);
			} else {
				chip(l, kunci, t, x, y0, x + lebar[i], y1, label, on, pilih);
			}
			x += lebar[i] + 2;
		}
	}

	private int indeks(String id) {
		for (int i = 0; i < terlihat.size(); i++) {
			if (terlihat.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	private static int lebarDari(int[] lebar, int dari, int sampai) {
		int s = 0;
		for (int i = dari; i <= sampai; i++) {
			s += lebar[i] + 2;
		}
		return s;
	}

	private void koin(Lukis l, String kunci, Tab t, int x0, int y0, int x1, int y1, boolean label, boolean on, Consumer<String> pilih) {
		boolean locked = t.kunci() != null;
		boolean over = l.di(x0, y0, x1, y1);
		float glow = nyala.computeIfAbsent(t.id(), k -> new Eased(12.0F)).to(on ? 1.0F : over ? 0.5F : 0.0F, l.detik);
		float r = (y1 - y0) / 2.0F;
		int face = locked ? Palette.grey(Palette.JADE_DULL, 0.6F) : Palette.mix(Palette.JADE_DULL, Palette.JADE, 0.4F + 0.6F * glow);
		int rim = on ? Palette.GOLD : Palette.mix(Palette.JADE_DARK, Palette.GOLD_DARK, glow * 0.6F);
		l.g.fill(x0 + 1, y0 + 1, x1 + 1, y1 + 1, 0x40000000);
		Brush.disc(l.g, x0 + r, y0 + r, r, rim);
		Brush.disc(l.g, x1 - r, y0 + r, r, rim);
		l.g.fill(Math.round(x0 + r), y0, Math.round(x1 - r), y1, rim);
		Brush.disc(l.g, x0 + r, y0 + r, r - 1.0F, face);
		Brush.disc(l.g, x1 - r, y0 + r, r - 1.0F, face);
		l.g.fill(Math.round(x0 + r), y0 + 1, Math.round(x1 - r), y1 - 1, face);
		int ink = locked ? Palette.argb(0xB0, Palette.JADE_PALE) : on ? Palette.GOLD_LIGHT : 0xFFF4FFF8;
		inti(l, t, x0 + 6, y0, x1 - 6, y1, label, on, ink);
		if (locked) {
			Glyphs.lock(l.g, x1 - 4.0F, y0 + 4.0F, 5.0F, Palette.argb(0xE0, Palette.SEAL));
		}
		zonaTab(l, kunci, t, x0, y0, x1, y1, on, over, label, pilih);
	}

	private void chip(Lukis l, String kunci, Tab t, int x0, int y0, int x1, int y1, boolean label, boolean on, Consumer<String> pilih) {
		boolean locked = t.kunci() != null;
		boolean over = l.di(x0, y0, x1, y1);
		float glow = nyala.computeIfAbsent(t.id(), k -> new Eased(12.0F)).to(on ? 1.0F : over ? 0.5F : 0.0F, l.detik);
		l.g.fill(x0, y0, x1, y1, Palette.mix(l.gaya.kartu, l.gaya.kartuSorot, glow * 0.8F));
		if (on) {
			l.g.fill(x0, y1 - 2, x1, y1, l.gaya.aksen);
		}
		int ink = locked ? Palette.terkunci(l.gaya.teks) : on ? l.gaya.aksen : l.gaya.teksLemah;
		inti(l, t, x0 + 4, y0, x1 - 4, y1 - 1, label, on, ink);
		if (locked) {
			Glyphs.lock(l.g, x1 - 4.0F, y0 + 4.0F, 5.0F, Palette.argb(0xE0, Palette.SEAL));
		}
		zonaTab(l, kunci, t, x0, y0, x1, y1, on, over, label, pilih);
	}

	/** Glyph and/or name inside a horizontal tab. */
	private static void inti(Lukis l, Tab t, int x0, int y0, int x1, int y1, boolean label, boolean on, int ink) {
		float cy = (y0 + y1) / 2.0F;
		float x = x0;
		if (t.glyph() >= 0) {
			float gs = Math.min(8.0F, y1 - y0 - 4.0F);
			float gcx = label ? x + gs / 2.0F : (x0 + x1) / 2.0F;
			Glyphs.tab(l.g, t.glyph(), gcx, cy, gs, ink);
			x = gcx + gs / 2.0F + 3.0F;
		}
		if (label || t.glyph() < 0) {
			l.teks(on ? Brush.bold(t.label()) : t.label(), x, cy - 4.0F, x1 - x, ink);
		}
	}

	private void zonaTab(Lukis l, String kunci, Tab t, int x0, int y0, int x1, int y1, boolean on, boolean over, boolean label,
			Consumer<String> pilih) {
		final String id = t.id();
		final Component alasan = t.kunci();
		l.zona(kunci + "#" + id, x0, y0, x1, y1, () -> {
			if (alasan != null) {
				l.fokusRing().tolak(alasan);
			} else if (!on) {
				UiSound.tab();
				pilih.accept(id);
			}
		}, true, null, false);
		if (over) {
			List<Component> tip = new ArrayList<>(2);
			if (!label || t.glyph() < 0 && l.font.width(t.label()) > x1 - x0 - 8) {
				tip.add(t.label());
			}
			if (alasan != null) {
				tip.add(alasan.copy().withColor(0xFFFF8A7A));
			}
			l.tip(tip);
		}
	}

	private void jagaAktif(String aktif, int ruang) {
		int ai = indeks(aktif);
		if (ai >= 0 && aktifBaru(aktif)) {
			if (ai < awal) {
				awal = ai;
			} else if (ai >= awal + ruang) {
				awal = ai - ruang + 1;
			}
		}
		awal = Math.max(0, Math.min(awal, Math.max(0, terlihat.size() - ruang)));
	}

	/** True once per change of the owner's active id. */
	private boolean aktifBaru(String aktif) {
		boolean baru = !aktif.equals(aktifTerakhir);
		aktifTerakhir = aktif;
		return baru;
	}

	private static void panah(Lukis l, String kunci, int x0, int y0, int x1, int y1, String tanda, boolean on, Runnable aksi) {
		boolean over = on && l.di(x0, y0, x1, y1);
		l.tengah(Component.literal(tanda), (x0 + x1) / 2.0F, (y0 + y1) / 2.0F - 4.0F, x1 - x0 + 4,
				on ? (over ? Palette.GOLD_LIGHT : l.gaya.teksLemah) : Palette.terkunci(l.gaya.teksSamar));
		l.zona(kunci, x0, y0, x1, y1, () -> {
			UiSound.hover();
			aksi.run();
		}, on, null, false);
	}
}
