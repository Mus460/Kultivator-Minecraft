package dev.kultivator.dunia.kota42.api;

import dev.kultivator.sosok.Gaya;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The visible mood of a resident (DESIGN 42.4.11): the face code a summoned body shows through
 * {@code SosokApi.setSuasana(npc, kode.sosok, 0)} and the mood column of dialogs, prices and festivals. Computed lazily by
 * {@code WargaApi.wajah(warga, now)} from {@code suasana = pribadi + tempat + peristiwa} (clamped -100..100).
 * Ordinals are saved inside one world; constants are only appended.
 */
public enum KodeWajah {
	/** mood >= +40: smile, light step, humming. */
	SENANG(Gaya.SUASANA_SENANG),
	/** -20..+40. */
	NETRAL(Gaya.SUASANA_NETRAL),
	/** mood <= -20 or mourning: eyes down, slow step, white clothes, wiping eyes. */
	SEDIH(Gaya.SUASANA_SEDIH),
	/** an attack or the place's Fear >= 40: wide eyes, hiding, children cling to parents. */
	TAKUT(Gaya.SUASANA_TAKUT),
	/** after a quarrel or a theft (<= 1 world hour): brows down, pointing. */
	MARAH(Gaya.SUASANA_MARAH),
	/** the night drinker habit: red face, staggering walk (the flushed Sosok face). */
	MABUK(Gaya.SUASANA_MALU),
	/** health < 30: pale, coughing, leaning (the drowsy Sosok face). */
	SAKIT(Gaya.SUASANA_MENGANTUK);

	private static final KodeWajah[] ALL = values();

	/** The {@code Gaya.SUASANA_*} face this code shows on a Sosok body. */
	public final int sosok;

	KodeWajah(int sosok) {
		this.sosok = sosok;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** {@code kultivator.kota42.wajah.<id>}. */
	public Component nama() {
		return Component.translatable("kultivator.kota42.wajah." + id());
	}

	/** The code of a mood value without special causes (sad, neutral or happy). */
	public static KodeWajah dariNilai(int suasana) {
		if (suasana >= 40) {
			return SENANG;
		}
		return suasana <= -20 ? SEDIH : NETRAL;
	}

	public static KodeWajah byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < ALL.length ? ALL[ordinal] : NETRAL;
	}

	public static KodeWajah byId(String id) {
		for (KodeWajah k : ALL) {
			if (k.id().equals(id)) {
				return k;
			}
		}
		return NETRAL;
	}
}
