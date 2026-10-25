package dev.kultivator.dunia.kota42.api;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The bonds between residents (DESIGN 42.4.5). A resident keeps at most {@code kotaWarga.hubunganMaks} (8) bonds as packed
 * longs: {@code idLain 32 | jenis 8 | kekuatan 8 (-100..100) | sejakHari 16}; family is read from the family tree and never
 * stored as a bond. {@link #KEKASIH} and {@link #PASANGAN} only ever join one man and one woman (42.16 K9). Ordinals are
 * packed into saved longs; constants are only appended.
 */
public enum JenisHubungan {
	/** strength >= +40: share the afternoon chat titik, walk to the tea house together, visit when sick. */
	SAHABAT(40),
	/** >= +60 between a single adult man and woman 16-45 whose temperaments do not clash: walk side by side at dusk. */
	KEKASIH(60),
	/** Married: breakfast and supper together, wave in the morning, sit on the porch. */
	PASANGAN(0),
	/** <= -40: avoid each other, glare, argue when forced to one titik; 5 %/day fight. */
	MUSUH(-40),
	/** Same work role in the same place, <= -20: louder calls, side glances, rival shops cut prices 5 %. */
	SAINGAN(-20),
	/** Apprenticeship (42.4.7): the master demonstrates, the apprentice copies 10 ticks later. */
	GURU_MURID(0),
	/** Known to each other, strength between the thresholds: the bond that grows toward a friend or sours toward an enemy. */
	KENALAN(0);

	private static final JenisHubungan[] ALL = values();

	/** The strength at which the bond forms (0 = formed by an event, not by strength). */
	public final int ambang;

	JenisHubungan(int ambang) {
		this.ambang = ambang;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** {@code kultivator.kota42.hubungan.<id>}. */
	public Component nama() {
		return Component.translatable("kultivator.kota42.hubungan." + id());
	}

	public static @Nullable JenisHubungan byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < ALL.length ? ALL[ordinal] : null;
	}

	// ------------------------------------------------------------------ packed bond

	/** Packs one bond: the other resident's id, the kind, strength -100..100 and the world day it formed (low 16 bits). */
	public static long kemas(int idLain, JenisHubungan jenis, int kekuatan, long sejakHari) {
		long k = Mth.clamp(kekuatan, -100, 100) & 0xFFL;
		return ((long) idLain << 32) | ((long) (jenis.ordinal() & 0xFF) << 24) | (k << 16) | (sejakHari & 0xFFFFL);
	}

	public static int idLain(long bond) {
		return (int) (bond >>> 32);
	}

	public static @Nullable JenisHubungan jenis(long bond) {
		return byOrdinal((int) ((bond >>> 24) & 0xFF));
	}

	/** Strength -100..100. */
	public static int kekuatan(long bond) {
		return (byte) ((bond >>> 16) & 0xFF);
	}

	/** The low 16 bits of the world day the bond formed (compare with {@code hari & 0xFFFF}). */
	public static int sejak(long bond) {
		return (int) (bond & 0xFFFFL);
	}

	/** The same bond with a new strength. */
	public static long denganKekuatan(long bond, int kekuatan) {
		long k = Mth.clamp(kekuatan, -100, 100) & 0xFFL;
		return (bond & ~(0xFFL << 16)) | (k << 16);
	}

	/** Self-check of the packing (run by {@code /kultivasi uji kota_hidup cek}). */
	public static boolean cekKemas() {
		long b = kemas(123456, MUSUH, -77, 70000L);
		return idLain(b) == 123456 && jenis(b) == MUSUH && kekuatan(b) == -77 && sejak(b) == (70000 & 0xFFFF)
				&& kekuatan(denganKekuatan(b, 55)) == 55 && idLain(denganKekuatan(b, 55)) == 123456;
	}
}
