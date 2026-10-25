package dev.kultivator.client.screen.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * One frame of a page of the single kit (DESIGN 43.7; was {@code tempat/ui/kit/Lukis}): the graphics, the font, the mouse
 * in layout coordinates, the frame time, the paper style and the hit zones ({@link Zona}) the frame registered. A page draws
 * immediately and registers a zone for everything that can be clicked or focused; its {@link Fokus} answers input with the
 * zones of the last frame. Persistent {@link Widget}s are drawn through {@link #widget} so the same Tab ring reaches both.
 * Clipping is tracked here, so a zone scrolled out of a list is neither hovered nor clicked.
 *
 * <p>Text follows 43.0.5: body 1.0 ({@link #teks}), titles 1.5 ({@link #judul}); text never shrinks, it is cut with "…"
 * and the whole text becomes the tooltip. {@link #skala} is the layout-to-screen factor (1 normally; the {@code uji ui
 * skala} test lays out in a smaller virtual GUI); picture-in-picture boxes multiply by it ({@link #nyata}).
 */
public final class Lukis {

	/** A clickable / focusable box of the last frame. */
	public static final class Zona {
		public final String kunci;
		public final int x0;
		public final int y0;
		public final int x1;
		public final int y1;
		public final @Nullable Runnable aksi;
		public final boolean aktif;
		public final @Nullable Component alasan;
		public final boolean fokus;
		/** Optional persistent widget (press offset, its own release). */
		public @Nullable Widget widget;
		/** Optional handler of ←/→ while focused (number pickers, tab bars, cycles). */
		public @Nullable IntConsumer geser;
		/** Optional right click (cycle backwards). */
		public @Nullable Runnable kanan;
		/** Optional text field behind the zone (keys and characters go to it while it writes). */
		public @Nullable InkField kolom;
		/** Optional group: ↑↓ move the focus between zones of the same group (DaftarPilihan, PemilihOrang). */
		public @Nullable String grup;

		Zona(String kunci, int x0, int y0, int x1, int y1, @Nullable Runnable aksi, boolean aktif, @Nullable Component alasan, boolean fokus) {
			this.kunci = kunci;
			this.x0 = x0;
			this.y0 = y0;
			this.x1 = x1;
			this.y1 = y1;
			this.aksi = aksi;
			this.aktif = aktif;
			this.alasan = alasan;
			this.fokus = fokus;
		}

		public boolean berisi(double x, double y) {
			return x >= x0 && y >= y0 && x < x1 && y < y1;
		}
	}

	public final GuiGraphicsExtractor g;
	public final Font font;
	public final int mx;
	public final int my;
	public final float detik;
	public final long kini;
	public final float skala;
	public Palette.Gaya gaya;
	/** The page box of this frame (for overlays and empty states). */
	public final int kx0;
	public final int ky0;
	public final int kx1;
	public final int ky1;
	final Fokus pemilik;
	final List<Zona> zona = new ArrayList<>();
	private final Deque<int[]> klip = new ArrayDeque<>();
	final @Nullable String fokus;
	@Nullable List<Component> tip;
	@Nullable DaftarGulir daftarAktif;
	final List<DaftarGulir> semuaDaftar = new ArrayList<>();
	final List<Pratinjau3D> semuaPratinjau = new ArrayList<>();
	/** The search field of the page this frame ({@code /} focuses it). */
	@Nullable InkField cari;
	/** Numbered choices this frame (DaftarPilihan): number -> zone. */
	final Map<Integer, Zona> nomor = new HashMap<>();

	Lukis(Fokus pemilik, GuiGraphicsExtractor g, Font font, int mx, int my, float detik, long kini, float skala, Palette.Gaya gaya,
			@Nullable String fokus, int kx0, int ky0, int kx1, int ky1) {
		this.pemilik = pemilik;
		this.g = g;
		this.font = font;
		this.mx = mx;
		this.my = my;
		this.detik = detik;
		this.kini = kini;
		this.skala = skala;
		this.gaya = gaya;
		this.fokus = fokus;
		this.kx0 = kx0;
		this.ky0 = ky0;
		this.kx1 = kx1;
		this.ky1 = ky1;
	}

	/** The focus engine this frame belongs to (persistent state of immediate widgets lives there). */
	public Fokus fokusRing() {
		return pemilik;
	}

	// ---------------------------------------------------------------- zones and focus

	/** Registers a zone; returns it (null when clipped away entirely). */
	public @Nullable Zona zona(String kunci, int x0, int y0, int x1, int y1, @Nullable Runnable aksi, boolean aktif, @Nullable Component alasan,
			boolean fokus) {
		int[] c = klip.peek();
		if (c != null) {
			x0 = Math.max(x0, c[0]);
			y0 = Math.max(y0, c[1]);
			x1 = Math.min(x1, c[2]);
			y1 = Math.min(y1, c[3]);
			if (x1 <= x0 || y1 <= y0) {
				return null;
			}
		}
		Zona z = new Zona(kunci, x0, y0, x1, y1, aksi, aktif, alasan, fokus);
		zona.add(z);
		return z;
	}

	/** True when the mouse is over the box and the box is not clipped away. */
	public boolean di(int x0, int y0, int x1, int y1) {
		int[] c = klip.peek();
		if (c != null && (mx < c[0] || my < c[1] || mx >= c[2] || my >= c[3])) {
			return false;
		}
		return mx >= x0 && my >= y0 && mx < x1 && my < y1;
	}

	public boolean difokus(String kunci) {
		return kunci.equals(fokus);
	}

	/** A pulse 0..1 for focus rings and urgent things. */
	public float denyut() {
		return 0.5F + 0.5F * (float) Math.sin(kini / 180.0D);
	}

	/** Draws the gold focus ring around a box when {@code kunci} has focus. */
	public void fokusCincin(String kunci, int x0, int y0, int x1, int y1) {
		if (difokus(kunci)) {
			cincinFokus(g, x0, y0, x1, y1, denyut());
		}
	}

	/** The focus ring of the kit: a 1 px gold ring just outside the box. */
	public static void cincinFokus(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float denyut) {
		Brush.frame(g, x0 - 2, y0 - 2, x1 + 2, y1 + 2, Palette.argb(Math.round(170 + 85 * denyut), Palette.GOLD_LIGHT));
	}

	/** Tooltip for this frame (the last one asked wins). */
	public void tip(List<Component> lines) {
		if (lines != null && !lines.isEmpty()) {
			tip = lines;
		}
	}

	public void tip(Component line) {
		if (line != null && !line.getString().isEmpty()) {
			tip = List.of(line);
		}
	}

	/**
	 * Draws a persistent widget and registers it in the Tab ring under {@code kunci} (a key stable across rebuilds, e.g.
	 * "simpan" or "w3"): its tooltip, its disabled reason and the focus ring come with it.
	 */
	public <T extends Widget> T widget(String kunci, T w) {
		Zona z = zona(kunci, w.x, w.y, w.x + w.w, w.y + w.h, w.action, w.isEnabled(), w.alasan(), true);
		if (z != null) {
			z.widget = w;
			z.kanan = w.kanan();
			z.geser = w.geser();
			if (w instanceof InkField field) {
				z.kolom = field;
			}
		}
		w.render(g, font, mx, my, detik);
		if (w instanceof InkField field && field.modeCari()) {
			cari = field;
		}
		if (z != null && di(w.x, w.y, w.x + w.w, w.y + w.h)) {
			tip(w.tooltipLines());
		}
		fokusCincin(kunci, w.x, w.y, w.x + w.w, w.y + w.h);
		return w;
	}

	// ---------------------------------------------------------------- clipping

	public void klip(int x0, int y0, int x1, int y1) {
		int[] c = klip.peek();
		if (c != null) {
			x0 = Math.max(x0, c[0]);
			y0 = Math.max(y0, c[1]);
			x1 = Math.min(x1, c[2]);
			y1 = Math.min(y1, c[3]);
		}
		klip.push(new int[] {x0, y0, Math.max(x0, x1), Math.max(y0, y1)});
		g.enableScissor(x0, y0, Math.max(x0, x1), Math.max(y0, y1));
	}

	public void lepasKlip() {
		if (!klip.isEmpty()) {
			klip.pop();
			g.disableScissor();
		}
	}

	/** Layout coordinate to screen coordinate (for picture-in-picture boxes that ignore the pose). */
	public int nyata(float v) {
		return Math.round(v * skala);
	}

	// ---------------------------------------------------------------- paper furniture (was PanelTempat)

	/** An inset well (detail pane, input): light fill, dark top-left rim, light bottom-right rim. */
	public void sumur(int x0, int y0, int x1, int y1) {
		g.fill(x0, y0, x1, y1, gaya.sumur);
		g.fill(x0, y0, x1, y0 + 1, 0x50000000);
		g.fill(x0, y0, x0 + 1, y1, 0x50000000);
		g.fill(x0, y1 - 1, x1, y1, 0x30FFFFFF);
		g.fill(x1 - 1, y0, x1, y1, 0x30FFFFFF);
	}

	/** A card behind a list row or a block of numbers; {@code sorot} 0..1 hover glow. */
	public void kartu(int x0, int y0, int x1, int y1, float sorot, boolean pilih) {
		g.fill(x0, y0, x1, y1, Palette.mix(gaya.kartu, gaya.kartuSorot, Math.max(sorot * 0.8F, pilih ? 1.0F : 0.0F)));
		if (pilih) {
			g.fill(x0, y0, x0 + 2, y1, gaya.aksen);
			Brush.frame(g, x0, y0, x1, y1, Palette.argb(0xA0, gaya.aksen));
		} else if (sorot > 0.05F) {
			Brush.frame(g, x0, y0, x1, y1, Palette.argb(Math.round(0x70 * sorot), Palette.GOLD));
		}
	}

	/** A thin rule with a small diamond in the middle (section separator). */
	public void garis(int x0, int x1, int y) {
		int col = Palette.argb(0x90, gaya.gelap() ? Palette.GOLD : gaya.teksSamar);
		g.fill(x0, y, x1, y + 1, col);
		int mid = (x0 + x1) / 2;
		g.fill(mid - 1, y - 1, mid + 2, y + 2, col);
	}

	/**
	 * A stacked group heading inside one scroll (43.0.4: the fourth level is a heading, never a tab bar): a small seal,
	 * the title in the accent colour and a gold rule. Returns the height used (12).
	 */
	public int kepala(Component judul, int x0, int y, int x1) {
		Brush.rect(g, x0, y + 2.0F, x0 + 4.0F, y + 6.0F, Palette.SEAL);
		teks(Brush.bold(judul), x0 + 6, y, x1 - x0 - 6, gaya.aksen);
		g.fill(x0, y + 10, x1, y + 11, Palette.argb(0xB0, Palette.GOLD));
		return 13;
	}

	// ---------------------------------------------------------------- text (43.0.5: 1.0 and 1.5 only)

	/** Body text (1.0), cut with "…" when wider than {@code lebar}; the whole text is the tooltip when cut and hovered. */
	public void teks(Component t, float x, float y, float lebar, int warna) {
		if (lebar <= 4.0F) {
			return;
		}
		if (font.width(t) <= lebar) {
			Brush.text(g, font, t, x, y, warna, 1.0F, false);
			return;
		}
		Brush.fit(g, font, t, x, y, lebar, warna, 1.0F, 1.0F, false);
		if (di(Math.round(x), Math.round(y), Math.round(x + lebar), Math.round(y + 9))) {
			tip(t);
		}
	}

	/** A faint caption line (1.0 in the style's hint colour). */
	public void samar(Component t, float x, float y, float lebar) {
		teks(t, x, y, lebar, gaya.teksSamar);
	}

	/** Right-aligned text (numbers) at 1.0. */
	public void kanan(Component t, float xKanan, float y, int warna) {
		Brush.text(g, font, t, xKanan - font.width(t), y, warna, 1.0F, false);
	}

	/** Centred text at 1.0, cut to {@code lebar}. */
	public void tengah(Component t, float cx, float y, float lebar, int warna) {
		float w = Math.min(lebar, font.width(t));
		teks(t, cx - w / 2.0F, y, lebar, warna);
	}

	/** Title: 1.5 with a shadow (1.0 when even the title would not fit at 1.0 width-wise, then cut). */
	public void judul(Component t, float x, float y, float lebar, int warna) {
		Component b = Brush.bold(t);
		float s = font.width(b) * 1.5F <= lebar ? 1.5F : 1.0F;
		int bayang = gaya.gelap() ? 0xA0000000 : Palette.argb(0x60, Palette.GOLD_DARK);
		Brush.fit(g, font, b, x + 1.0F, y + 1.0F, lebar, bayang, s, s, false);
		Brush.fit(g, font, b, x, y, lebar, warna, s, s, false);
		if (font.width(b) * s > lebar && di(Math.round(x), Math.round(y), Math.round(x + lebar), Math.round(y + 9 * s))) {
			tip(t);
		}
	}

	/** Wrapped body text; returns the height used. */
	public float bungkus(Component t, float x, float y, float lebar, int warna) {
		return Brush.wrap(g, font, t, x, y, lebar, warna, 1.0F, false);
	}

	/**
	 * Wrapped body text kept inside {@code maksTinggi} (no overlap at GUI scale 4): the lines that fit are drawn, the last
	 * one is cut with "…" when more follow, and the whole text becomes the tooltip when the block is hovered. Returns the
	 * height used.
	 */
	public float bungkus(Component t, float x, float y, float lebar, int warna, float maksTinggi) {
		if (maksTinggi < 8.0F || lebar < 10.0F) {
			return 0.0F;
		}
		List<FormattedCharSequence> lines = font.split(t, Math.max(10, (int) lebar));
		int boleh = Math.max(0, (int) Math.floor((maksTinggi + 1.0F) / 9.0F));
		if (boleh <= 0) {
			return 0.0F;
		}
		if (lines.size() <= boleh) {
			for (int i = 0; i < lines.size(); i++) {
				Brush.text(g, font, lines.get(i), x, y + i * 9.0F, warna, 1.0F, false);
			}
			return lines.size() * 9.0F;
		}
		for (int i = 0; i < boleh - 1; i++) {
			Brush.text(g, font, lines.get(i), x, y + i * 9.0F, warna, 1.0F, false);
		}
		String akhir = polos(lines.get(boleh - 1));
		String cut = font.plainSubstrByWidth(akhir, Math.max(0, (int) lebar - font.width(Brush.CUT))) + Brush.CUT;
		Brush.text(g, font, Component.literal(cut).withStyle(t.getStyle()), x, y + (boleh - 1) * 9.0F, warna, 1.0F, false);
		if (di(Math.round(x), Math.round(y), Math.round(x + lebar), Math.round(y + boleh * 9.0F))) {
			tip(t);
		}
		return boleh * 9.0F;
	}

	/** The plain characters of a wrapped line. */
	static String polos(FormattedCharSequence seq) {
		StringBuilder b = new StringBuilder();
		seq.accept((index, style, cp) -> {
			b.appendCodePoint(cp);
			return true;
		});
		return b.toString();
	}
}
