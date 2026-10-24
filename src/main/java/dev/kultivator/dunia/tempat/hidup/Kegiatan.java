package dev.kultivator.dunia.tempat.hidup;

import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;

/**
 * What a resident does at one moment (DESIGN 38.N.1 {@code Jadwal.kegiatan}): where it goes, the clip it plays there
 * and the props in its hands. A pure value made by {@link Jadwal}; positions are resolved only when needed (body, far
 * crowd, witness) by {@link TempatHuni#tujuan}.
 *
 * @param kunci        activity id ({@code tidur}, {@code kerja}, {@code makan}, {@code obrol}...; lang
 *                     {@code kultivator.hidup.kegiatan.<kunci>})
 * @param tujuan       the place it goes to: a titik type ({@code kursi}, {@code obrol}, {@code kerja:palu}, {@code lapak}...)
 *                     or one of {@link #KASUR}, {@link #KERJA}, {@link #RUMAH}, {@link #TETAP}, {@code @xz:<x>,<z>}
 * @param klip         clip at the goal ({@link GerakId}, 0 = stand)
 * @param varian       clip variant
 * @param propKanan    right-hand prop ({@link PropId}, 0 = the clip's own)
 * @param propKiri     left-hand prop
 * @param propPunggung back prop
 * @param jalan        walking clip on the way (0 = the role's own walk, {@link TubuhPenduduk#jalanPeran})
 * @param dalam        indoors and out of sight: no body is called for it and no far crowd entry is sent
 */
public record Kegiatan(String kunci, String tujuan, int klip, int varian, int propKanan, int propKiri, int propPunggung, int jalan,
		boolean dalam) {

	/** The resident's own bed (resolved by slot, {@link TempatHuni#kasur}). */
	public static final String KASUR = "@kasur";
	/** The resident's own work spot (the role's work titik type, resolved by slot). */
	public static final String KERJA = "@kerja";
	/** The resident's house (its building box or its bed). */
	public static final String RUMAH = "@rumah";
	/** Wherever it stands now. */
	public static final String TETAP = "@tetap";
	/** Prefix of a crowd point given in world coordinates ({@code @xz:120,-340}). */
	public static final String XZ = "@xz:";

	/** Asleep or indoors: no body, no crowd entry. */
	public static Kegiatan dalam(String kunci) {
		return new Kegiatan(kunci, KASUR, GerakId.TIDUR, 0, 0, 0, 0, 0, true);
	}

	/** Goes to {@code tujuan} and plays {@code klip} with a right-hand prop. */
	public static Kegiatan di(String kunci, String tujuan, int klip, int propKanan) {
		return new Kegiatan(kunci, tujuan, klip, 0, propKanan, 0, 0, 0, false);
	}

	/** Goes to {@code tujuan} and plays {@code klip} with three props. */
	public static Kegiatan di(String kunci, String tujuan, int klip, int kanan, int kiri, int punggung) {
		return new Kegiatan(kunci, tujuan, klip, 0, kanan, kiri, punggung, 0, false);
	}

	/** The same activity with another walking clip. */
	public Kegiatan berjalan(int klipJalan) {
		return new Kegiatan(kunci, tujuan, klip, varian, propKanan, propKiri, propPunggung, klipJalan, dalam);
	}

	/** The same activity with other props (0 keeps nothing: the clip's own). */
	public Kegiatan denganProp(int kanan, int kiri, int punggung) {
		return new Kegiatan(kunci, tujuan, klip, varian, kanan, kiri, punggung, jalan, dalam);
	}

	/** The same activity at another goal. */
	public Kegiatan ke(String tujuanBaru) {
		return new Kegiatan(kunci, tujuanBaru, klip, varian, propKanan, propKiri, propPunggung, jalan, dalam);
	}

	/** The same activity with another clip. */
	public Kegiatan klipBaru(int klipBaru) {
		return new Kegiatan(kunci, tujuan, klipBaru, varian, propKanan, propKiri, propPunggung, jalan, dalam);
	}

	/** True when it goes nowhere. */
	public boolean tetap() {
		return TETAP.equals(tujuan);
	}

	/** Lang key of the activity name. */
	public String kunciLang() {
		return "kultivator.hidup.kegiatan." + kunci;
	}
}
