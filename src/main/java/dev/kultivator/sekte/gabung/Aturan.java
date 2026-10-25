package dev.kultivator.sekte.gabung;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The sect rules A1-A9 of DESIGN 41.6.11 (the rules read at the oath come from
 * {@code data/kultivator/tempat/dialog/gabung_aturan_<aliran>.json}). Each rule has its violation points on the
 * {@code Keanggotaan.pelanggaran} record (0-100, decays 1 per 2 world days) and says how a demonic sect bends it. 41A0
 * fixes the ids and points (contract C41.A1 names {@code langgar(player, Aturan, at, perluSaksi)}); 41A writes the
 * detection, thresholds and punishments in {@code BalaiHukum}.
 * <p>
 * Thresholds of the record: {@link #TEGURAN} reprimand, {@link #DENDA} fine of {@code 20 x class} PK, {@link #KURUNGAN}
 * confinement in the Gua Renungan ({@code 4 x points} real seconds, at most 20 minutes), {@link #TURUN} demotion,
 * {@link #USIR} expulsion.
 */
public enum Aturan {

	/** A1: no harming a fellow member outside the lei tai (demonic: allowed after a formal challenge). */
	A1("a1", 15, true),
	/** A2: no killing a fellow member (demonic: outside a Blood Duel); straight to trial: expelled, or deserter if fled. */
	A2("a2", 60, false),
	/** A3: no stealing from the Paviliun, gardens or stores (demonic: only being caught counts). */
	A3("a3", 10, true),
	/** A4: answer the War Call. */
	A4("a4", 10, false),
	/** A5: no plotting with the sect's enemies (pacts or gifts of the own hall to a sect at war with this one). */
	A5("a5", 20, false),
	/** A6: no leaking the Teknik Warisan without the master's blessing. */
	A6("a6", 25, false),
	/** A7: honour the elders (demonic: "the strong are honoured", so a won challenge is no offence). */
	A7("a7", 20, true),
	/** A8: no demonic arts inside a righteous sect's grounds (the Forbidden Scroll of 38.T.7.7). */
	A8("a8", 5, true),
	/** A9: presence; gone more than 30 days without leave = demoted one rank (no points). */
	A9("a9", 0, false);

	/** Record thresholds (41.6.11). */
	public static final int TEGURAN = 10;
	public static final int DENDA = 20;
	public static final int KURUNGAN = 35;
	public static final int TURUN = 50;
	public static final int USIR = 60;
	/** The record never exceeds this. */
	public static final int MAKS = 100;
	/** Confinement seconds per point, and the cap. */
	public static final int DETIK_PER_POIN = 4;
	public static final int KURUNGAN_MAKS_DETIK = 1200;
	/** World days per point of decay. */
	public static final int SUSUT_HARI = 2;

	private final String id;
	private final int poin;
	private final boolean iblisLonggar;

	Aturan(String id, int poin, boolean iblisLonggar) {
		this.id = id;
		this.poin = poin;
		this.iblisLonggar = iblisLonggar;
	}

	public String id() {
		return id;
	}

	/** Violation points added to the record. */
	public int poin() {
		return poin;
	}

	/** True when a demonic sect words the rule differently (challenge first, only when caught, the strong are honoured). */
	public boolean iblisLonggar() {
		return iblisLonggar;
	}

	/** The rule's text for alignment {@code aliran} (0 lurus, 1 netral, 2 iblis). */
	public MutableComponent teks(int aliran) {
		return Component.translatable("kultivator.gabung.aturan." + id + (aliran == 2 && iblisLonggar ? ".iblis" : ""));
	}

	public static Aturan byId(String id) {
		for (Aturan a : values()) {
			if (a.id.equals(id)) {
				return a;
			}
		}
		return null;
	}
}
