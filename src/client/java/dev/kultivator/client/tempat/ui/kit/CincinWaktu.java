package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.network.chat.Component;

/**
 * {@code CincinWaktu} (DESIGN 38.U.2): a countdown ring. The sheet sends ticks left at send time ({@code sisa_tick}); the
 * ring counts down from the moment the sheet arrived, the arc shrinks clockwise from the top, turns amber under 25 % and
 * red and pulsing under 10 s, and the time is written in the middle (or beside it when the ring is small).
 */
public final class CincinWaktu {

	/** Ticks left now, counting down from {@code diterima} (ms) when {@code sisaTick} was received. */
	public static long sisa(long sisaTick, long diterima, long kini) {
		return Math.max(0L, sisaTick - (kini - diterima) / 50L);
	}

	/** Where the time text goes. */
	public static final int TANPA = 0;
	public static final int DALAM = 1;
	public static final int KANAN = 2;
	public static final int KIRI = 3;

	/**
	 * Draws the ring at centre (cx, cy) radius {@code r}; {@code total} the full length in ticks (for the arc; ≤ 0 = full);
	 * {@code teks} one of {@link #TANPA}, {@link #DALAM}, {@link #KANAN}, {@link #KIRI}. Returns the ticks left.
	 */
	public static long gambar(Lukis l, float cx, float cy, float r, long sisaTick, long total, long diterima, int teks) {
		long left = sisa(sisaTick, diterima, l.kini);
		float frac = total > 0 ? Math.min(1.0F, left / (float) total) : 1.0F;
		int warna = left <= 200L ? TempatWarna.BAHAYA : frac < 0.25F ? TempatWarna.PERINGATAN : TempatWarna.OK;
		Brush.disc(l.g, cx, cy, r, Palette.argb(0x50, 0x000000));
		Brush.ring(l.g, cx, cy, r - 2.2F, r, Palette.argb(0x60, 0xFFFFFF));
		if (frac > 0.0F) {
			float from = (float) (-Math.PI / 2.0D);
			float to = from + (float) (Math.PI * 2.0D) * frac;
			Brush.arc(l.g, cx, cy, r - 2.2F, r, from, to, warna);
		}
		if (left <= 200L && left > 0L) {
			Brush.halo(l.g, cx, cy, r + 1.0F, 2.0F, TempatWarna.BAHAYA & 0xFFFFFF, l.denyut());
		}
		Component t = TeksTempat.waktu(left);
		switch (teks) {
			case DALAM -> {
				float s = Brush.fitScale(l.font, t, r * 1.6F, 0.75F, 0.6F);
				Brush.centered(l.g, l.font, t, cx, cy - 4.0F * s, l.gaya.gelap() ? 0xFFF4E8D0 : 0xFFF4FAF6, s, true);
			}
			case KANAN -> Brush.text(l.g, l.font, t, cx + r + 3.0F, cy - 3.0F, l.gaya.teks, 0.75F, false);
			case KIRI -> l.kanan(t, cx - r - 3.0F, cy - 3.0F, l.gaya.teks, 0.75F);
			default -> {
			}
		}
		if (teks != DALAM && l.di(Math.round(cx - r), Math.round(cy - r), Math.round(cx + r), Math.round(cy + r))) {
			l.tip(TeksTempat.k("waktu.sisa", t));
		}
		return left;
	}

	private CincinWaktu() {
	}
}
