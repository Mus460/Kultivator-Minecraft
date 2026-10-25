package dev.kultivator.dunia.kota42.api;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A remembered event between a resident and a player (DESIGN 42.4.10): stored in the resident's memory of that player
 * ({@code peristiwa[4]}: id + day, the newest replaces the weakest) and quoted in dialogs ("my father often told of the
 * master who put out that fire"). {@link #delta} is the default impression change of the event and {@link #kuat} whether
 * it resists the slow drift back to 0. Ordinals are saved; constants are only appended.
 */
public enum Kenangan {
	/** A liked / plain / hated gift (+8 / +3 / -5, given by the caller); not strong. */
	DIBERI_HADIAH(3, false),
	/** A personal request completed. */
	DIBANTU(15, true),
	/** Saved from a monster, a fire or a pickpocket. */
	DISELAMATKAN(30, true),
	/** Attended a family wedding and gave a red envelope. */
	HADIR_NIKAH(10, true),
	/** Attended a family funeral and gave condolence money. */
	HADIR_MAKAM(12, true),
	/** Recruited the resident's child into a sect (proud family +20, refusing family -25: given by the caller). */
	ANAK_DIREKRUT(20, true),
	/** Struck by the player. */
	DIPUKUL(-25, true),
	/** Stolen from by the player. */
	DICURI(-40, true),
	/** The house was damaged by the player (38.R attributed damage). */
	RUMAH_RUSAK(-15, true),
	/** A debt repaid on time. */
	UTANG_LUNAS(5, true),
	/** A debt defaulted. */
	UTANG_MANGKIR(-20, true),
	/** Inherited: a child keeps half the impression of a dead parent. */
	CERITA_ORANG_TUA(0, true);

	private static final Kenangan[] ALL = values();

	/** Default impression change (-100..100). */
	public final int delta;
	/** Strong memories do not drift back to 0. */
	public final boolean kuat;

	Kenangan(int delta, boolean kuat) {
		this.delta = delta;
		this.kuat = kuat;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** {@code kultivator.kota42.kenangan.<id>}. */
	public Component nama() {
		return Component.translatable("kultivator.kota42.kenangan." + id());
	}

	public static @Nullable Kenangan byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < ALL.length ? ALL[ordinal] : null;
	}

	public static @Nullable Kenangan byId(@Nullable String id) {
		for (Kenangan k : ALL) {
			if (k.id().equals(id)) {
				return k;
			}
		}
		return null;
	}
}
