package dev.kultivator.client.hud;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/**
 * Kabar (DESIGN 43.9, G27): the one notice slot of the HUD, top right. Every "something happened" notice of the mod comes
 * here: disciple news ({@code ToastMandiri}), the elders' morning report ({@code LaporanCard}, the long variant), a duel
 * challenge ({@code ChallengeOfferToast}), the codex seal notices, the village, hall and sect-building toasts. A card is a
 * jade paper slip ({@link #slip}) with an optional icon, a bold title, up to three lines of text and extra rows; text is
 * drawn at 1.0. At most three cards show at once (two on short screens, one below 220 px), stacked downwards; more wait
 * in a queue of 12.
 * A card slides in, stays its time ({@link #TAMPIL}, an important one {@link #TAMPIL_PENTING} with a red seal edge) and
 * slides out; a card with a {@code hidup} test stays while the test holds (a pending duel offer). Its clock stops while a
 * screen is open or the HUD is hidden, so nothing runs out unseen. Hidden with F1, the HUD switch, the debug screen, a
 * cinematic shot and Mode Rekam. Drawn by the {@code mandiri_toast} HUD element ({@code ToastMandiri} is the host).
 */
public final class Kabar {

	public static final long TAMPIL = 4500L;
	public static final long TAMPIL_PENTING = 7000L;
	private static final long GESER = 260L;
	private static final int LEBAR = 168;
	private static final int ANTRE_MAKS = 12;
	private static final int TEKS_BARIS = 3;
	private static final int TEPI = 6;

	/** The small picture at a card's left, drawn centred on {@code cx, cy} in a 16 px square. */
	@FunctionalInterface
	public interface Ikon {
		void gambar(GuiGraphicsExtractor g, float cx, float cy);
	}

	/** One notice; built with {@link #kartu} and its setters, then {@link #kirim(Kartu)}. */
	public static final class Kartu {
		final @Nullable Component judul;
		final Component teks;
		final List<Component> baris = new ArrayList<>();
		@Nullable Ikon ikon;
		boolean penting;
		long lama = -1L;
		int lebar = LEBAR;
		@Nullable String kunci;
		@Nullable BooleanSupplier hidup;
		@Nullable DoubleSupplier sisa;
		long mulai = -1L;
		long keluar = -1L;

		Kartu(@Nullable Component judul, Component teks) {
			this.judul = judul;
			this.teks = teks;
		}

		public Kartu ikon(@Nullable Ikon ikon) {
			this.ikon = ikon;
			return this;
		}

		public Kartu penting(boolean penting) {
			this.penting = penting;
			return this;
		}

		/** Extra rows under the text (one line each, cut with "…"). */
		public Kartu baris(List<Component> rows) {
			this.baris.addAll(rows);
			return this;
		}

		/** How long it stays, in ms (default {@link #TAMPIL} / {@link #TAMPIL_PENTING}). */
		public Kartu lama(long millis) {
			this.lama = millis;
			return this;
		}

		/** A wider card (the long variant, e.g. the morning report). */
		public Kartu lebar(int width) {
			this.lebar = width;
			return this;
		}

		/** A key: a newer card with the same key replaces it, and {@link Kabar#tutup} removes it. */
		public Kartu kunci(String key) {
			this.kunci = key;
			return this;
		}

		/** Stays while this holds (and at most {@link #lama}). */
		public Kartu hidup(BooleanSupplier alive) {
			this.hidup = alive;
			return this;
		}

		/** A thin "time left" line at the foot, 1..0. */
		public Kartu sisa(DoubleSupplier left) {
			this.sisa = left;
			return this;
		}

		long umur() {
			return lama > 0L ? lama : penting ? TAMPIL_PENTING : TAMPIL;
		}
	}

	private static final Deque<Kartu> ANTRE = new ArrayDeque<>();
	private static final List<Kartu> TAMPAK = new ArrayList<>();
	private static long bingkaiLalu;
	private static int bawah = TEPI;

	// ================================================================== API

	public static Kartu kartu(@Nullable Component judul, Component teks) {
		return new Kartu(judul, teks);
	}

	/** A plain notice with a jade edge. */
	public static void kirim(@Nullable Component judul, Component teks) {
		kirim(kartu(judul, teks));
	}

	public static void kirim(@Nullable Component judul, Component teks, @Nullable Ikon ikon, boolean penting) {
		kirim(kartu(judul, teks).ikon(ikon).penting(penting));
	}

	public static void kirim(Kartu kartu) {
		if (kartu.kunci != null) {
			tutup(kartu.kunci);
		}
		if (ANTRE.size() >= ANTRE_MAKS) {
			ANTRE.pollFirst();
		}
		ANTRE.addLast(kartu);
	}

	/** Removes the waiting card with {@code key} and lets a showing one slide out. */
	public static void tutup(String key) {
		ANTRE.removeIf(k -> key.equals(k.kunci));
		long now = Util.getMillis();
		for (Kartu k : TAMPAK) {
			if (key.equals(k.kunci) && k.keluar < 0L) {
				k.keluar = now;
			}
		}
	}

	/** True while a card with {@code key} waits or shows. */
	public static boolean ada(String key) {
		for (Kartu k : ANTRE) {
			if (key.equals(k.kunci)) {
				return true;
			}
		}
		for (Kartu k : TAMPAK) {
			if (key.equals(k.kunci) && k.keluar < 0L) {
				return true;
			}
		}
		return false;
	}

	public static void bersih() {
		ANTRE.clear();
		TAMPAK.clear();
		bawah = TEPI;
	}

	/** The lowest GUI y the stack used on the last frame ({@link SlotSisi} starts below it). */
	public static int bawah() {
		return bawah;
	}

	// ================================================================== icons

	/** A red (or {@code rgb}) seal with one glyph, e.g. 報 or 決. */
	public static Ikon segel(String glyph, int rgb) {
		return (g, cx, cy) -> {
			Font font = Minecraft.getInstance().font;
			Brush.rect(g, cx - 7.5F, cy - 7.5F, cx + 7.5F, cy + 7.5F, Palette.opaque(rgb));
			Brush.frame(g, Math.round(cx - 6.5F), Math.round(cy - 6.5F), Math.round(cx + 6.5F), Math.round(cy + 6.5F), Palette.argb(0xB0, Palette.TALISMAN));
			Brush.centered(g, font, Component.literal(glyph), cx + 0.5F, cy - 4.0F, 0xFFFFF4E0, 1.0F, false);
		};
	}

	/** A glyph of the {@link Glyphs} tab set on a jade disc. */
	public static Ikon glyph(int icon) {
		return (g, cx, cy) -> {
			Brush.disc(g, cx, cy, 8.5F, Palette.JADE_DARK);
			Glyphs.tab(g, icon, cx, cy, 11.0F, Palette.TALISMAN);
		};
	}

	/** An item, drawn as in the inventory. */
	public static Ikon item(ItemStack stack) {
		ItemStack copy = stack.copy();
		return (g, cx, cy) -> g.item(copy, Math.round(cx - 8.0F), Math.round(cy - 8.0F));
	}

	// ================================================================== drawing

	/**
	 * The single HUD slip (DESIGN 43.9): jade paper with a soft shadow, a gold edge, a faint inner line and a coloured
	 * strip at the left ({@code accent}; 0 for none). Also used by every {@link SlotSisi} content.
	 */
	public static void slip(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int accent) {
		g.fill(x0 + 2, y0 + 2, x1 + 2, y1 + 2, 0x50000000);
		g.fillGradient(x0, y0, x1, y1, Palette.argb(0xF0, Palette.PAPER_TOP), Palette.argb(0xF0, Palette.PAPER_BOTTOM));
		Brush.frame(g, x0, y0, x1, y1, Palette.GOLD_DARK);
		Brush.frame(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, Palette.argb(0x50, Palette.GOLD));
		if ((accent >>> 24) != 0) {
			g.fill(x0 + 1, y0 + 1, x0 + 3, y1 - 1, accent);
		}
	}

	/** Drawn every frame by the host HUD element. */
	public static void gambar(GuiGraphicsExtractor g) {
		Minecraft minecraft = Minecraft.getInstance();
		long now = Util.getMillis();
		long dt = bingkaiLalu == 0L ? 0L : Math.max(0L, Math.min(250L, now - bingkaiLalu));
		bingkaiLalu = now;
		boolean hidden = minecraft.player == null || minecraft.gui.hud.isHidden() || !EffectQuality.hudVisible()
				|| minecraft.getDebugOverlay().showDebugScreen() || CinematicDirector.active() || EpicDirector.recordHide();
		if (hidden || minecraft.gui.screen() != null) {
			// the clock stops: shift every showing card by this frame
			for (Kartu k : TAMPAK) {
				k.mulai += dt;
				if (k.keluar >= 0L) {
					k.keluar += dt;
				}
			}
			bawah = TEPI;
			return;
		}
		// retire finished cards, admit waiting ones
		for (Iterator<Kartu> it = TAMPAK.iterator(); it.hasNext(); ) {
			Kartu k = it.next();
			if (k.keluar < 0L && (now - k.mulai > k.umur() || k.hidup != null && !k.hidup.getAsBoolean())) {
				k.keluar = now;
			}
			if (k.keluar >= 0L && now - k.keluar > GESER) {
				it.remove();
			}
		}
		// 1366x768 at GUI scale 4 is 192 px tall: one card, so the side scroll (SlotSisi) still fits under it above the hotbar
		int maks = g.guiHeight() < 220 ? 1 : g.guiHeight() < 260 ? 2 : 3;
		while (!ANTRE.isEmpty() && TAMPAK.size() < maks) {
			Kartu k = ANTRE.pollFirst();
			k.mulai = now;
			k.keluar = -1L;
			TAMPAK.add(k);
		}
		Font font = minecraft.font;
		int x1 = g.guiWidth() - TEPI;
		float y = TEPI;
		int batas = g.guiHeight() - 48;
		for (Kartu k : TAMPAK) {
			float in = Math.min(1.0F, (now - k.mulai) / (float) GESER);
			float out = k.keluar < 0L ? 0.0F : Math.min(1.0F, (now - k.keluar) / (float) GESER);
			float slide = ease(in) * (1.0F - ease(out));
			int h = gambarKartu(g, font, k, x1, Math.round(y), slide, batas, now);
			y += (h + 3) * (1.0F - ease(out));
		}
		bawah = Math.round(y);
	}

	/** One card with its right edge on {@code x1}; returns its height. */
	private static int gambarKartu(GuiGraphicsExtractor g, Font font, Kartu k, int x1, int y0, float slide, int batas, long now) {
		int w = Math.min(k.lebar, Math.max(120, g.guiWidth() / 2));
		int ikonW = k.ikon != null ? 20 : 0;
		int tx = 7 + ikonW;
		int tw = w - tx - 5;
		List<FormattedCharSequence> teks = font.split(k.teks, Math.max(20, tw));
		int teksN = Math.min(TEKS_BARIS, teks.size());
		int h = 5 + (k.judul != null ? 10 : 0) + teksN * 9 + 3;
		int barisN = 0;
		for (int i = 0; i < k.baris.size() && y0 + h + 9 + (k.sisa != null ? 4 : 0) <= batas; i++) {
			h += 9;
			barisN++;
		}
		if (k.sisa != null) {
			h += 4;
		}
		if (k.ikon != null) {
			h = Math.max(h, 24);
		}
		if (slide <= 0.01F) {
			return h;
		}
		int x0 = Math.round(x1 + 4 - (w + 4) * slide);
		int right = x0 + w;
		slip(g, x0, y0, right, y0 + h, k.penting ? Palette.SEAL : Palette.JADE);
		if (k.penting) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(now / 220.0D);
			g.fill(x0 + 1, y0, right - 1, y0 + 1, Palette.argb(Math.round(255 * (0.4F + 0.6F * pulse)), Palette.SEAL_GLOW));
		}
		if (k.ikon != null) {
			k.ikon.gambar(g, x0 + 5 + 8.5F, y0 + 12.0F);
		}
		float y = y0 + 5.0F;
		if (k.judul != null) {
			Brush.fit(g, font, Brush.bold(k.judul), x0 + tx, y, tw, Palette.INK, 1.0F, 1.0F, false);
			y += 10.0F;
		}
		for (int i = 0; i < teksN; i++) {
			Brush.text(g, font, teks.get(i), x0 + tx, y, k.judul != null ? Palette.INK_SOFT : Palette.INK, 1.0F, false);
			y += 9.0F;
		}
		for (int i = 0; i < barisN; i++) {
			Brush.fit(g, font, k.baris.get(i), x0 + tx, y, tw, Palette.INK_SOFT, 1.0F, 1.0F, false);
			y += 9.0F;
		}
		if (k.sisa != null) {
			float share = (float) Math.max(0.0D, Math.min(1.0D, k.sisa.getAsDouble()));
			int bx0 = x0 + 4;
			int bx1 = right - 4;
			int by = y0 + h - 4;
			g.fill(bx0, by, bx1, by + 1, Palette.argb(0x80, Palette.PAPER_DEEP));
			g.fill(bx0, by, bx0 + Math.round((bx1 - bx0) * share), by + 1, k.penting ? Palette.SEAL : Palette.GOLD_DARK);
		}
		return h;
	}

	private static float ease(float t) {
		float u = 1.0F - Math.max(0.0F, Math.min(1.0F, t));
		return 1.0F - u * u * u;
	}

	private Kabar() {
	}
}
