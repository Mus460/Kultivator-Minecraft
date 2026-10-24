package dev.kultivator.dunia.tempat.hidup;

import dev.kultivator.dunia.kota.NpcRole;

/**
 * One resident of a 38 place (DESIGN 38.N.1), generated deterministically from {@code (situsId, world seed)} by
 * {@code PendudukGen} when the place first loads; only changes are saved ({@code kultivator:tempat_penduduk}).
 *
 * @param id       resident index inside its place (stable for one world)
 * @param peran    the role (38.N.2 / 38.T roles of {@link NpcRole})
 * @param look     look seed: the DESIGN 40 sosok of the body is derived from role + look ({@code SosokGen.untukNpc})
 * @param suara    voice index (0-5, {@code KultivatorNpc.VOICES})
 * @param rumah    house slot: the resident lives in the {@code bangunan} titik whose slot hash is this ({@link #TANPA} =
 *                 none; {@link TempatHuni#rumah})
 * @param kasur    bed slot: the {@code kasur} titik whose slot hash is this ({@link #TANPA} = sleeps out of sight)
 * @param kerja    work slot among the titik of its role's work type ({@link KerjaPeran}; {@link #TANPA} = none)
 * @param kelompok household (residents of one house share it; 0 = alone, {@link #TAMU} = a market-day guest)
 * @param benih    per-resident seed (schedule offset of ±40 world minutes, chatter, variants)
 *
 * <p>Slots, not list indexes: the titik of a place are read from loaded pieces only, so the list grows while chunks
 * load. A titik's slot is a hash of its position ({@link TempatHuni#slot}), stable whatever loaded first.
 */
public record Penduduk(int id, NpcRole peran, int look, int suara, int rumah, int kasur, int kerja, int kelompok, long benih) {
	/** "No titik" for {@link #rumah}, {@link #kasur} and {@link #kerja}. */
	public static final int TANPA = -1;
	/** {@link #kelompok} of a guest who comes only on market day (day 0 of the week, 38.N.3). */
	public static final int TAMU = -2;

	/** True for a market-day guest (a peddler visiting the village or trading post). */
	public boolean tamu() {
		return kelompok == TAMU;
	}

	/** The 38.N.3 schedule group of this resident's role. */
	public NpcRole.Kelompok jadwal() {
		return peran.kelompok;
	}

	/** Schedule offset in ticks, -666..666 (±40 world minutes, 38.N.3), stable per resident. */
	public int geserJadwal() {
		return (int) Math.floorMod(benih ^ (benih >>> 29), 1333L) - 666;
	}
}
