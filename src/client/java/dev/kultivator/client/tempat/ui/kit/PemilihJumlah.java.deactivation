package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * {@code PemilihJumlah} (DESIGN 38.U.2): "(−) (3) (+)" with jade coins; Shift steps by 10; ←/→ change it while it has
 * focus. The count stays within 1..{@code maks}.
 */
public final class PemilihJumlah {

	private int nilai = 1;

	public int nilai() {
		return nilai;
	}

	public void set(int v) {
		nilai = Math.max(1, v);
	}

	private void ubah(int arah, int maks) {
		int step = Minecraft.getInstance().hasShiftDown() ? 10 : 1;
		int baru = Math.max(1, Math.min(Math.max(1, maks), nilai + arah * step));
		if (baru != nilai) {
			nilai = baru;
			UiSound.toggle(arah > 0);
		} else {
			UiSound.denied();
		}
	}

	/** Draws the picker at (x, y), 14 px high; returns its width. */
	public int gambar(Lukis l, String kunci, int x, int y, int maks) {
		nilai = Math.max(1, Math.min(Math.max(1, maks), nilai));
		int d = 12;
		int w = d * 2 + 26;
		koin(l, kunci + "#kurang", x, y, d, "−", nilai > 1, () -> ubah(-1, maks));
		int mx0 = x + d + 1;
		PanelTempat.sumur(l.g, l.gaya, mx0, y, mx0 + 24, y + d);
		Brush.centered(l.g, l.font, Component.literal(Integer.toString(nilai)), mx0 + 12.0F, y + 2.0F, l.gaya.teksSumur(), 1.0F, false);
		koin(l, kunci + "#tambah", mx0 + 25, y, d, "+", nilai < maks, () -> ubah(1, maks));
		Lukis.Zona z = l.zona(kunci, mx0, y, mx0 + 24, y + d, null, true, null, true);
		if (z != null) {
			z.geser = arah -> ubah(arah, maks);
		}
		if (l.di(x, y, x + w, y + d)) {
			l.tip(TeksTempat.k("jumlah.tip"));
		}
		l.fokusCincin(kunci, x, y, x + w, y + d);
		return w;
	}

	private static void koin(Lukis l, String kunci, int x, int y, int d, String tanda, boolean on, Runnable aksi) {
		boolean over = l.di(x, y, x + d, y + d);
		float r = d / 2.0F;
		Brush.disc(l.g, x + r, y + r + 0.5F, r, Palette.argb(0x50, 0x000000));
		Brush.disc(l.g, x + r, y + r, r, on ? (over ? Palette.JADE_LIGHT : Palette.JADE) : Palette.JADE_DULL);
		Brush.ring(l.g, x + r, y + r, r - 1.2F, r, on ? Palette.JADE_DARK : Palette.grey(Palette.JADE_DARK, 0.6F));
		Brush.centered(l.g, l.font, Component.literal(tanda), x + r, y + r - 4.0F, on ? 0xFFF4FFF8 : 0xFFB8C4BC, 1.0F, false);
		l.zona(kunci, x, y, x + d, y + d, aksi, on, null, false);
	}
}
