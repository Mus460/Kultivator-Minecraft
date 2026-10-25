package dev.kultivator.dunia.kota42.api;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The resident ceremonies of DESIGN 42.7.7 ({@code Upacara.jadwalkan(jenis, warga...)}, payload {@code kota_upacara}). Each
 * is a real event of the residents (42.4.6), scheduled for a following world day at a fixed hour; seen when a player is
 * within {@code kotaAcara.jarakTampil} blocks, otherwise it runs as data (gossip, mood). Ordinals are synced and saved;
 * constants are only appended.
 */
public enum JenisUpacara {
	/** Wedding 09:00-21:00: fetching the bride, red sedan, three bows, banquet (angpao). */
	NIKAH(9, 21, 1),
	/** Funeral 08:00-11:00 the day after death: procession with the coffin litter, burial at the grave hill. */
	MAKAM(8, 11, 1),
	/** Birth day: the father hands out red eggs. */
	LAHIR(7, 19, 0),
	/** The one-month feast (next world day 18:00): a small banquet at home. */
	SEBULAN(18, 21, 1),
	/** Zhuazhou at age 1 (second world day after birth, 10:00): the baby picks one of 8 objects. */
	ZHUAZHOU(10, 11, 2);

	private static final JenisUpacara[] ALL = values();

	/** Start hour 0-23 (clock hour as shown to players). */
	public final int jamMulai;
	/** End hour 0-23. */
	public final int jamAkhir;
	/** World days after the cause (death, birth, engagement minimum) the ceremony is held. */
	public final int hariSetelah;

	JenisUpacara(int jamMulai, int jamAkhir, int hariSetelah) {
		this.jamMulai = jamMulai;
		this.jamAkhir = jamAkhir;
		this.hariSetelah = hariSetelah;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** {@code kultivator.kota42.upacara.<id>}. */
	public Component nama() {
		return Component.translatable("kultivator.kota42.upacara." + id());
	}

	/** True for the ceremonies with a moving procession (path nodes in {@code kota_upacara}). */
	public boolean arakan() {
		return this == NIKAH || this == MAKAM;
	}

	public static @Nullable JenisUpacara byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < ALL.length ? ALL[ordinal] : null;
	}

	public static @Nullable JenisUpacara byId(@Nullable String id) {
		for (JenisUpacara j : ALL) {
			if (j.id().equals(id)) {
				return j;
			}
		}
		return null;
	}
}
