package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.client.screen.ui.Brush;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The kit-38 lang helper {@link #k} ({@code kultivator.tempat_ui.*}, keys in {@code lang-src/tempat_ui.*}). The number and
 * time formatters moved to the single kit ({@link Brush}, DESIGN 43.7); every other caller uses {@link Brush}.
 * shortcut: the forwarders stay only for the half-built kota42 (42-G2) sheets; delete them when those call Brush.
 */
public final class TeksTempat {

	public static final String K = "kultivator.tempat_ui.";

	public static MutableComponent k(String kunci, Object... args) {
		return Component.translatable(K + kunci, args);
	}

	/** {@link Brush#angka}. */
	public static String angka(long n) {
		return Brush.angka(n);
	}

	/** {@link Brush#singkat}. */
	public static String singkat(long n) {
		return Brush.singkat(n);
	}

	/** {@link Brush#desimal1}. */
	public static String desimal1(float v) {
		return Brush.desimal1(v);
	}

	/** {@link Brush#nb}. */
	public static Component nb(long n, boolean pendek) {
		return Brush.nb(n, pendek);
	}

	/** {@link Brush#bertanda}. */
	public static String bertanda(long n) {
		return Brush.bertanda(n);
	}

	/** {@link Brush#rincianBatu}. */
	public static Component rincianBatu(long nb) {
		return Brush.rincianBatu(nb);
	}

	/** {@link Brush#waktu}. */
	public static Component waktu(long ticks) {
		return Brush.waktu(ticks);
	}

	/** {@link Brush#hari}. */
	public static Component hari(int n) {
		return Brush.hari(n);
	}

	private TeksTempat() {
	}
}
