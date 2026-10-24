package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * One frame of a kit screen: the graphics, the font, the mouse in layout coordinates, the frame time, the style, and the
 * hit zones ({@link Zona}) the frame registered. The kit draws immediately and registers a zone for everything that can
 * be clicked or focused; the screen answers input with the zones of the last frame. Focus (Tab / Shift+Tab, 38.U.2) is a
 * zone key, so it survives refreshes and relayouts. Clipping is tracked here too, so a zone scrolled out of a list is
 * neither hovered nor clicked.
 *
 * <p>{@link #skala} is the layout-to-screen factor (1 normally; the {@code uji ui skala} test lays out in a smaller
 * virtual GUI and scales it up). Picture-in-picture boxes (entity previews) are not transformed by the pose, so
 * components multiply their box by it ({@link #nyata}).
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
		/** Optional widget that draws the press offset (TombolAksi). */
		public @Nullable dev.kultivator.client.screen.ui.Widget widget;
		/** Optional handler of ←/→ while focused (PemilihJumlah). */
		public @Nullable java.util.function.IntConsumer geser;

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
	public TempatWarna.Gaya gaya;
	final List<Zona> zona = new ArrayList<>();
	private final Deque<int[]> klip = new ArrayDeque<>();
	final @Nullable String fokus;
	@Nullable List<Component> tip;
	@Nullable DaftarGulir daftarAktif;
	final List<DaftarGulir> semuaDaftar = new ArrayList<>();
	final List<Pratinjau3D> semuaPratinjau = new ArrayList<>();
	@Nullable KotakCari cari;

	Lukis(GuiGraphicsExtractor g, Font font, int mx, int my, float detik, long kini, float skala, TempatWarna.Gaya gaya, @Nullable String fokus) {
		this.g = g;
		this.font = font;
		this.mx = mx;
		this.my = my;
		this.detik = detik;
		this.kini = kini;
		this.skala = skala;
		this.gaya = gaya;
		this.fokus = fokus;
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

	/** Draws the focus ring around a zone box when {@code kunci} has focus. */
	public void fokusCincin(String kunci, int x0, int y0, int x1, int y1) {
		if (difokus(kunci)) {
			PanelTempat.cincinFokus(g, x0, y0, x1, y1, denyut());
		}
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

	// ---------------------------------------------------------------- picture in picture

	/** Layout coordinate to screen coordinate (for PiP boxes that ignore the pose). */
	public int nyata(float v) {
		return Math.round(v * skala);
	}

	// ---------------------------------------------------------------- text shortcuts (sizes of 38.U.2)

	/** Body text, cut with ".." when wider than {@code lebar}, full text as tooltip when cut and hovered. */
	public void teks(Component t, float x, float y, float lebar, int warna) {
		teks(t, x, y, lebar, warna, 1.0F);
	}

	public void teks(Component t, float x, float y, float lebar, int warna, float ukuran) {
		float w = font.width(t) * ukuran;
		if (w <= lebar) {
			Brush.text(g, font, t, x, y, warna, ukuran, false);
			return;
		}
		float s = Brush.fitScale(font, t, lebar, ukuran, Math.max(0.6F, ukuran * 0.8F));
		Brush.fit(g, font, t, x, y, lebar, warna, s, Math.max(0.6F, ukuran * 0.8F), false);
		if (font.width(t) * s > lebar && di(Math.round(x), Math.round(y), Math.round(x + lebar), Math.round(y + 9 * s))) {
			tip(t);
		}
	}

	/** Right-aligned text (numbers). */
	public void kanan(Component t, float xKanan, float y, int warna, float ukuran) {
		Brush.text(g, font, t, xKanan - font.width(t) * ukuran, y, warna, ukuran, false);
	}

	/** Title: x1.5 with an ink shadow. */
	public void judul(Component t, float x, float y, float lebar, int warna) {
		float s = Brush.fitScale(font, t, lebar, 1.5F, 0.8F);
		int bayang = gaya.gelap() ? 0xA0000000 : Palette.argb(0x60, Palette.GOLD_DARK & 0xFFFFFF);
		Brush.fit(g, font, Brush.bold(t), x + 1.0F, y + 1.0F, lebar, bayang, s, 0.8F, false);
		Brush.fit(g, font, Brush.bold(t), x, y, lebar, warna, s, 0.8F, false);
	}

	/** Caption 0.75. */
	public void keterangan(Component t, float x, float y, float lebar, int warna) {
		teks(t, x, y, lebar, warna, 0.75F);
	}

	/** Wrapped text; returns the height used. */
	public float bungkus(Component t, float x, float y, float lebar, int warna, float ukuran) {
		return Brush.wrap(g, font, t, x, y, lebar, warna, ukuran, false);
	}

	/**
	 * Wrapped text kept inside {@code maksTinggi} (38.U.2: no overlap at GUI scale 4): the lines that fit are drawn, the
	 * last one is cut with ".." when more follow, and the whole text becomes the tooltip when the block is hovered. When
	 * the text does not fit at {@code ukuran} it first tries 0.85 x (never below 0.6). Returns the height used.
	 */
	public float bungkus(Component t, float x, float y, float lebar, int warna, float ukuran, float maksTinggi) {
		if (maksTinggi < 6.0F || lebar < 10.0F) {
			return 0.0F;
		}
		float s = ukuran;
		List<FormattedCharSequence> lines = font.split(t, Math.max(10, (int) (lebar / s)));
		if (muat(lines.size(), s) > maksTinggi && ukuran * 0.85F >= 0.6F) {
			float kecil = ukuran * 0.85F;
			List<FormattedCharSequence> lain = font.split(t, Math.max(10, (int) (lebar / kecil)));
			if (lain.size() * 9.0F * kecil < lines.size() * 9.0F * s) {
				s = kecil;
				lines = lain;
			}
		}
		float lh = 9.0F * s;
		int boleh = Math.max(0, (int) Math.floor((maksTinggi + s) / lh));
		if (boleh <= 0) {
			return 0.0F;
		}
		if (lines.size() <= boleh) {
			for (int i = 0; i < lines.size(); i++) {
				Brush.text(g, font, lines.get(i), x, y + i * lh, warna, s, false);
			}
			return lines.size() * lh;
		}
		for (int i = 0; i < boleh - 1; i++) {
			Brush.text(g, font, lines.get(i), x, y + i * lh, warna, s, false);
		}
		String akhir = polos(lines.get(boleh - 1));
		int ruang = (int) (lebar / s) - font.width("..");
		String cut = font.plainSubstrByWidth(akhir, Math.max(0, ruang)) + "..";
		Brush.text(g, font, Component.literal(cut).withStyle(t.getStyle()), x, y + (boleh - 1) * lh, warna, s, false);
		if (di(Math.round(x), Math.round(y), Math.round(x + lebar), Math.round(y + boleh * lh))) {
			tip(t);
		}
		return boleh * lh;
	}

	/** Height of {@code n} wrapped lines at {@code ukuran} (the last line without its gap). */
	private static float muat(int n, float ukuran) {
		return n <= 0 ? 0.0F : n * 9.0F * ukuran - ukuran;
	}

	/** The plain characters of a wrapped line. */
	private static String polos(FormattedCharSequence seq) {
		StringBuilder b = new StringBuilder();
		seq.accept((index, style, cp) -> {
			b.appendCodePoint(cp);
			return true;
		});
		return b.toString();
	}
}
