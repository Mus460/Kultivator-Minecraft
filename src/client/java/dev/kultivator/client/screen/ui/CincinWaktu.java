package dev.kultivator.client.screen.ui;

import net.minecraft.network.chat.Component;

/**
 * The single countdown of the kit (DESIGN 43.7; moved from {@code tempat/ui/kit/CincinWaktu}, replaces the m:ss lines of
 * {@code KotaPage} and the sweeps of Lelang / Alam / Titah). The server sends ticks left at send time; the countdown runs
 * from the moment that arrived. Two forms with one colour rule: the ring ({@link #gambar}: the arc shrinks clockwise from
 * the top) and the plain text ({@link #teks}). Amber under 25 %, red and pulsing in the last 10 s. Text is 1.0 (43.0.5):
 * when it does not fit inside a small ring it goes beside it instead of shrinking.
 */
public final class CincinWaktu {

	/** Where the time text goes. */
	public static final int TANPA = 0;
	public static final int DALAM = 1;
	public static final int KANAN = 2;
	public static final int KIRI = 3;

	private static final long BAHAYA_TICK = 200L;

	/** Ticks left now, counting down from {@code diterima} (ms) when {@code sisaTick} was received. */
	public static long sisa(long sisaTick, long diterima, long kini) {
		return Math.max(0L, sisaTick - (kini - diterima) / 50L);
	}

	/** The colour of a countdown with {@code left} ticks of {@code total} (≤ 0 = unknown length). */
	public static int warna(long left, long total) {
		float frac = total > 0 ? Math.min(1.0F, left / (float) total) : 1.0F;
		return left <= BAHAYA_TICK ? Palette.BAHAYA : frac < 0.25F ? Palette.PERINGATAN : Palette.OK;
	}

	/**
	 * Draws the ring at centre (cx, cy) radius {@code r}; {@code total} the full length in ticks (for the arc; ≤ 0 = full);
	 * {@code teks} one of {@link #TANPA}, {@link #DALAM}, {@link #KANAN}, {@link #KIRI}. Returns the ticks left.
	 */
	public static long gambar(Lukis l, float cx, float cy, float r, long sisaTick, long total, long diterima, int teks) {
		long left = sisa(sisaTick, diterima, l.kini);
		float frac = total > 0 ? Math.min(1.0F, left / (float) total) : 1.0F;
		int warna = warna(left, total);
		Brush.disc(l.g, cx, cy, r, Palette.argb(0x50, 0x000000));
		Brush.ring(l.g, cx, cy, r - 2.2F, r, Palette.argb(0x60, 0xFFFFFF));
		if (frac > 0.0F) {
			float from = (float) (-Math.PI / 2.0D);
			Brush.arc(l.g, cx, cy, r - 2.2F, r, from, from + (float) (Math.PI * 2.0D) * frac, warna);
		}
		if (left <= BAHAYA_TICK && left > 0L) {
			Brush.halo(l.g, cx, cy, r + 1.0F, 2.0F, Palette.BAHAYA & 0xFFFFFF, l.denyut());
		}
		Component t = Brush.waktu(left);
		int tw = l.font.width(t);
		if (teks == DALAM && tw > r * 1.7F) {
			teks = KANAN;
		}
		switch (teks) {
			case DALAM -> Brush.centered(l.g, l.font, t, cx, cy - 4.0F, l.gaya.gelap() ? 0xFFF4E8D0 : 0xFFF4FAF6, 1.0F, true);
			case KANAN -> Brush.text(l.g, l.font, t, cx + r + 3.0F, cy - 4.0F, l.gaya.teks, 1.0F, false);
			case KIRI -> l.kanan(t, cx - r - 3.0F, cy - 4.0F, l.gaya.teks);
			default -> {
			}
		}
		if (teks != DALAM && l.di(Math.round(cx - r), Math.round(cy - r), Math.round(cx + r), Math.round(cy + r))) {
			l.tip(Component.translatable("kultivator.tempat_ui.waktu.sisa", t));
		}
		return left;
	}

	/**
	 * The text form ("m:ss" / "j:mm:ss"), coloured by the same rule and pulsing in the last 10 s; left edge at {@code x}
	 * (right edge when {@code kanan}). Returns the width.
	 */
	public static int teks(Lukis l, float x, float y, long sisaTick, long total, long diterima, boolean kanan) {
		long left = sisa(sisaTick, diterima, l.kini);
		Component t = Brush.waktu(left);
		int w = l.font.width(t);
		int warna = left <= BAHAYA_TICK && left > 0L ? Palette.mix(Palette.BAHAYA, Palette.SEAL_GLOW, l.denyut())
				: total > 0 ? warna(left, total) : l.gaya.teks;
		Brush.text(l.g, l.font, t, kanan ? x - w : x, y, warna, 1.0F, false);
		return w;
	}

	private CincinWaktu() {
	}
}
