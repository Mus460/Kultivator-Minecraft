package dev.kultivator.client.tempa;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Vertical pan of a container screen that is taller than the window (DESIGN 43.0.6: GUI scale 4 on 1366x768 is only 192 px
 * high, the forge is 214 and a 6 row ring 226). The menu's slots sit at fixed offsets from {@code topPos} and cannot move,
 * so the whole panel moves instead: the wheel, Page Up / Page Down, the two arrow seals at the left edge of the window
 * and Tab (the focused control is brought into view) pan it. When the window is tall enough the panel stays centred and
 * nothing is drawn.
 */
final class TempaGeser {

	/** Space kept above the panel when it is panned (room for the scroll rod). */
	private static final int TEPI = 4;
	private static final int RODA = 12;
	private static final int LANGKAH = 18;
	private static final int HALAMAN = 60;
	private static final int TOMBOL_W = 16;
	private static final int TOMBOL_H = 12;
	private static final String K = "kultivator.gui43.geser.";

	private final Runnable susun;
	private int geser;
	private int maks;
	private @Nullable String kunciIkut;

	/** {@code susun} lays the screen out again after every pan (it calls {@link #atas}). */
	TempaGeser(Runnable susun) {
		this.susun = susun;
	}

	/** The {@code topPos} of a panel {@code panel} px tall in a window {@code layar} px tall (centred when it fits). */
	int atas(int layar, int panel) {
		maks = Math.max(0, panel + 2 * TEPI - layar);
		geser = Math.max(0, Math.min(maks, geser));
		return maks == 0 ? (layar - panel) / 2 : TEPI - geser;
	}

	/** True when the panel is taller than the window. */
	boolean aktif() {
		return maks > 0;
	}

	/** Pans by {@code delta} px (positive = show lower parts); true when the panel is pannable (the input is used). */
	boolean geser(int delta) {
		if (maks <= 0) {
			return false;
		}
		int baru = Math.max(0, Math.min(maks, geser + delta));
		if (baru != geser) {
			geser = baru;
			susun.run();
		}
		return true;
	}

	/** The mouse wheel (up shows the upper part). */
	boolean roda(double dy) {
		return dy != 0.0D && geser(dy > 0.0D ? -RODA : RODA);
	}

	/** Page Up / Page Down. */
	boolean tombol(KeyEvent event) {
		if (event.key() == InputConstants.KEY_PAGEUP) {
			return geser(-HALAMAN);
		}
		if (event.key() == InputConstants.KEY_PAGEDOWN) {
			return geser(HALAMAN);
		}
		return false;
	}

	/**
	 * Tab moved the focus to {@code zona} (key {@code kunci}): pans just enough to show it. Runs once per new focus, so a
	 * wheel pan away from a focused control is not undone. True when the pan changed (the screen lays out again).
	 */
	boolean ikuti(@Nullable String kunci, @Nullable Lukis.Zona zona, int layar) {
		if (maks <= 0 || kunci == null || zona == null || kunci.equals(kunciIkut)) {
			kunciIkut = kunci;
			return false;
		}
		kunciIkut = kunci;
		if (zona.y0 < TEPI) {
			return geser(zona.y0 - TEPI);
		}
		if (zona.y1 > layar - TEPI) {
			return geser(zona.y1 - layar + TEPI);
		}
		return false;
	}

	/** The two arrow seals at the window's left edge ({@code x0}), drawn only while the panel is pannable. */
	void gambar(Lukis l, int x0) {
		if (maks <= 0) {
			return;
		}
		tombol(l, "geser_atas", x0, 4, true, geser > 0, Component.translatable(K + "atas"));
		tombol(l, "geser_bawah", x0, l.ky1 - TOMBOL_H - 4, false, geser < maks, Component.translatable(K + "bawah"));
	}

	private void tombol(Lukis l, String kunci, int x0, int y0, boolean atas, boolean aktif, Component tip) {
		int x1 = x0 + TOMBOL_W;
		int y1 = y0 + TOMBOL_H;
		boolean di = aktif && l.di(x0, y0, x1, y1);
		int dasar = aktif ? (di ? 0x90 : 0x58) : 0x28;
		Brush.rect(l.g, x0, y0, x1, y1, Palette.argb(dasar, Palette.TALISMAN));
		Brush.frame(l.g, x0, y0, x1, y1, Palette.argb(aktif ? 0xC0 : 0x50, Palette.GOLD_DARK));
		int warna = aktif ? Palette.SEAL_DARK : Palette.INK_FAINT;
		float cx = (x0 + x1) / 2.0F;
		float cy = (y0 + y1) / 2.0F;
		for (int i = 0; i < 4; i++) {
			float y = atas ? cy - 2.0F + i : cy + 1.0F - i;
			Brush.rect(l.g, cx - i - 0.5F, y, cx + i + 1.5F, y + 1.0F, warna);
		}
		l.zona(kunci, x0, y0, x1, y1, () -> geser(atas ? -LANGKAH : LANGKAH), aktif, null, true);
		l.fokusCincin(kunci, x0, y0, x1, y1);
		if (l.di(x0, y0, x1, y1)) {
			l.tip(tip);
		}
	}
}
