package dev.kultivator.client.tempat.ui.kit;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Number and time formatting of the kit (DESIGN 38.U.2 {@code LabelHarga}): "1.234 NB", short "2,5 rb" / "1,2 jt", the
 * stone breakdown "1 Tinggi · 4 Menengah · 3 Rendah", countdowns "0:06" / "2 j 10 m" / "3 hari", signed deltas. The
 * thousands and decimal marks come from the language ({@code kultivator.tempat_ui.angka.ribu} / {@code .desimal}), so
 * English shows "1,234" and Indonesian "1.234". All keys live in {@code lang-src/tempat_ui.*}.
 */
public final class TeksTempat {

	public static final String K = "kultivator.tempat_ui.";
	/** Nilai Batu per stone tier (NilaiBatu.VALUE {1, 9, 81, 729}). */
	private static final long[] NILAI = {1L, 9L, 81L, 729L};
	private static final String[] TINGKAT = {"rendah", "menengah", "tinggi", "agung"};

	public static MutableComponent k(String kunci, Object... args) {
		return Component.translatable(K + kunci, args);
	}

	private static String ribu() {
		return Component.translatable(K + "angka.ribu").getString();
	}

	private static String desimal() {
		return Component.translatable(K + "angka.desimal").getString();
	}

	/** Full number with thousands marks: 1234567 -> "1.234.567". */
	public static String angka(long n) {
		String digits = Long.toString(Math.abs(n));
		StringBuilder out = new StringBuilder();
		String mark = ribu();
		int lead = digits.length() % 3;
		for (int i = 0; i < digits.length(); i++) {
			if (i > 0 && (i - lead) % 3 == 0) {
				out.append(mark);
			}
			out.append(digits.charAt(i));
		}
		return (n < 0 ? "−" : "") + out;
	}

	/** Short number: under 10 000 full, then "12,5 rb", "1,2 jt", "3,4 M". */
	public static String singkat(long n) {
		long a = Math.abs(n);
		String sign = n < 0 ? "−" : "";
		if (a < 10_000L) {
			return sign + angka(a);
		}
		String unit;
		double v;
		if (a < 1_000_000L) {
			v = a / 1000.0D;
			unit = Component.translatable(K + "angka.rb").getString();
		} else if (a < 1_000_000_000L) {
			v = a / 1_000_000.0D;
			unit = Component.translatable(K + "angka.jt").getString();
		} else {
			v = a / 1_000_000_000.0D;
			unit = Component.translatable(K + "angka.m").getString();
		}
		String body = v >= 100.0D ? Long.toString(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v).replace(".", desimal());
		if (body.endsWith(desimal() + "0")) {
			body = body.substring(0, body.length() - 1 - desimal().length());
		}
		return sign + body + " " + unit;
	}

	/** One decimal: 9.6 -> "9,6". */
	public static String desimal1(float v) {
		return String.format(java.util.Locale.ROOT, "%.1f", v).replace(".", desimal());
	}

	/** "1.234 NB" (full) or the short form when {@code pendek}. */
	public static Component nb(long n, boolean pendek) {
		return k("nb", pendek ? singkat(n) : angka(n));
	}

	/** Signed amount "+214" / "−60" (0 = "·"). */
	public static String bertanda(long n) {
		if (n == 0L) {
			return "·";
		}
		return (n > 0 ? "+" : "") + angka(n);
	}

	/** Coloured signed amount for ledgers. */
	public static MutableComponent bertandaWarna(long n, int naik, int turun, int nol) {
		return Component.literal(bertanda(n)).withColor(n > 0 ? naik : n < 0 ? turun : nol);
	}

	/** The fewest-stones breakdown of {@code nb}: "1 Tinggi · 4 Menengah · 3 Rendah" (agung first). */
	public static Component rincianBatu(long nb) {
		long rest = Math.max(0L, nb);
		List<Component> parts = new ArrayList<>();
		for (int t = NILAI.length - 1; t >= 0; t--) {
			long count = rest / NILAI[t];
			rest -= count * NILAI[t];
			if (count > 0) {
				parts.add(k("batu." + TINGKAT[t], angka(count)));
			}
		}
		if (parts.isEmpty()) {
			return k("batu.nol");
		}
		MutableComponent out = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				out.append(" · ");
			}
			out.append(parts.get(i));
		}
		return out;
	}

	/** Stones in the bag by tier [rendah, menengah, tinggi, agung] -> "3 Tinggi · 5 Menengah · 12 Rendah". */
	public static Component batuDiTas(int[] batu) {
		MutableComponent out = Component.empty();
		boolean any = false;
		for (int t = Math.min(batu.length, 4) - 1; t >= 0; t--) {
			if (batu[t] <= 0) {
				continue;
			}
			if (any) {
				out.append(" · ");
			}
			out.append(k("batu." + TINGKAT[t], angka(batu[t])));
			any = true;
		}
		return any ? out : k("batu.nol");
	}

	/** Countdown of game ticks: "m:ss" below one hour, "2 j 10 m" below two game days, then "3 hari". */
	public static Component waktu(long ticks) {
		long t = Math.max(0L, ticks);
		long seconds = (t + 19L) / 20L;
		if (seconds < 3600L) {
			return Component.literal(String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L));
		}
		if (t < 24000L * 2L) {
			long minutes = seconds / 60L;
			return k("waktu.jam_menit", minutes / 60L, minutes % 60L);
		}
		return k("waktu.hari", t / 24000L);
	}

	/** "3 hari" / "besok" / "hari ini". */
	public static Component hari(int n) {
		if (n <= 0) {
			return k("waktu.hari_ini");
		}
		if (n == 1) {
			return k("waktu.besok");
		}
		return k("waktu.hari", n);
	}

	/** Percent 0..1 -> "45 %". */
	public static String persen(float f) {
		return Math.round(f * 100.0F) + " %";
	}

	/** Grey italic helper line. */
	public static MutableComponent samar(Component text) {
		return text.copy().withStyle(ChatFormatting.ITALIC);
	}

	private TeksTempat() {
	}
}
