package dev.kultivator.client.screen.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The single number picker of the kit (DESIGN 43.7; moved from {@code tempat/ui/kit/PemilihJumlah}, replaces the number
 * cycles of Calon / Treasury and the auction's +5/+10/+25 %): "(−) [ 3 ] (+)" with jade coins. Shift steps by 10, ←/→
 * change it while it has focus; the count stays within {@code min..maks} (the maximum comes from the data).
 */
public final class PemilihJumlah {

	private static final int KOIN_W = TalismanButton.MIN_W;
	private static final int H = 14;

	private int nilai = 1;
	private int min = 1;

	public int nilai() {
		return nilai;
	}

	public void set(int v) {
		nilai = Math.max(min, v);
	}

	/** The lowest value (default 1; 0 allows "none"). */
	public PemilihJumlah min(int min) {
		this.min = min;
		nilai = Math.max(min, nilai);
		return this;
	}

	private void ubah(int arah, int maks) {
		int step = Minecraft.getInstance().hasShiftDown() ? 10 : 1;
		int baru = Math.max(min, Math.min(Math.max(min, maks), nilai + arah * step));
		if (baru != nilai) {
			nilai = baru;
			UiSound.toggle(arah > 0);
		} else {
			UiSound.denied();
		}
	}

	/** Width of the picker for values up to {@code maks}. */
	public static int lebar(Lukis l, int maks) {
		return KOIN_W * 2 + Math.max(24, l.font.width(Integer.toString(Math.max(1, maks))) + 10) + 2;
	}

	/** Draws the picker at (x, y), 14 px high; returns its width. */
	public int gambar(Lukis l, String kunci, int x, int y, int maks) {
		nilai = Math.max(min, Math.min(Math.max(min, maks), nilai));
		int tengah = Math.max(24, l.font.width(Integer.toString(Math.max(1, maks))) + 10);
		int w = KOIN_W * 2 + tengah + 2;
		koin(l, kunci + "#kurang", x, y, "−", nilai > min, () -> ubah(-1, maks));
		int mx0 = x + KOIN_W + 1;
		l.sumur(mx0, y, mx0 + tengah, y + H);
		l.tengah(Component.literal(Integer.toString(nilai)), mx0 + tengah / 2.0F, y + 3.0F, tengah, l.gaya.teksSumur());
		koin(l, kunci + "#tambah", mx0 + tengah + 1, y, "+", nilai < maks, () -> ubah(1, maks));
		Lukis.Zona z = l.zona(kunci, mx0, y, mx0 + tengah, y + H, null, true, null, true);
		if (z != null) {
			z.geser = arah -> ubah(arah, maks);
		}
		if (l.di(x, y, x + w, y + H)) {
			l.tip(java.util.List.of(Component.translatable("kultivator.gui43.kit.jumlah.tip"),
					Component.translatable("kultivator.gui43.kit.jumlah.maks", Math.max(min, maks))));
		}
		l.fokusCincin(kunci, x, y, x + w, y + H);
		return w;
	}

	private static void koin(Lukis l, String kunci, int x, int y, String tanda, boolean on, Runnable aksi) {
		boolean over = on && l.di(x, y, x + KOIN_W, y + H);
		float r = (H - 2) / 2.0F;
		float cx = x + KOIN_W / 2.0F;
		float cy = y + H / 2.0F;
		Brush.disc(l.g, cx, cy + 0.5F, r, Palette.argb(0x50, 0x000000));
		Brush.disc(l.g, cx, cy, r, on ? (over ? Palette.JADE_LIGHT : Palette.JADE) : Palette.JADE_DULL);
		Brush.ring(l.g, cx, cy, r - 1.2F, r, on ? Palette.JADE_DARK : Palette.grey(Palette.JADE_DARK, 0.6F));
		Brush.centered(l.g, l.font, Component.literal(tanda), cx, cy - 4.0F, on ? 0xFFF4FFF8 : 0xFFB8C4BC, 1.0F, false);
		l.zona(kunci, x, y, x + KOIN_W, y + H, aksi, on, null, false);
	}
}
