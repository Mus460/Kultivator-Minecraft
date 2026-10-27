package dev.kultivator.sekte.mandiri.api;

import dev.kultivator.sekte.mandiri.MandiriKegiatan;
import org.jetbrains.annotations.Nullable;

/**
 * The activity catalogue of the daily planner (DESIGN 39.4.2 plus the four feud scenes of 39.F.5). Each entry names the
 * synced body activity it shows ({@link #act()}, 0 = the executor chooses: duty posts 1-4, the gate guard mode, the
 * breakthrough, a journey away), its permission group, the cultivation factor {@code K} of 39.5.1, the block length and
 * the module that owns its executor ({@link Pelaksana}, registered with {@code MandiriApi.register}). Ids 13-20 belong to
 * the DESIGN 36 modules and are only borrowed through their bridges. Pure data, safe on both sides; never reorder (the
 * ordinal is saved in {@code riwayat}/{@code kemarin} hour strips, 0 = idle is {@link #KOSONG}).
 */
public enum Kegiatan {
	/** Nothing planned (the hour strip's empty cell). */
	KOSONG("kosong", 0, IzinKegiatan.KULTIVASI, 0.0D, 0, 0, "39A"),
	MEDITASI("meditasi", MandiriKegiatan.ACT_MEDITASI_MANDIRI, IzinKegiatan.KULTIVASI, 1.5D, 2000, 6000, "39A"),
	TUTUP_DIRI("tutup_diri", MandiriKegiatan.ACT_TUTUP_DIRI, IzinKegiatan.KULTIVASI, 1.8D, 24000, 120000, "39B"),
	LATIH_JURUS("latih_jurus", MandiriKegiatan.ACT_LATIH_JURUS, IzinKegiatan.LATIHAN, 0.6D, 1000, 2000, "39D"),
	TANDING("tanding", MandiriKegiatan.ACT_TANDING, IzinKegiatan.TANDING, 0.5D, 1000, 1000, "39D"),
	/** Borrowed: A's {@code ACT_STUDY} (13) through the bridge {@code PaviliunMandiri}. */
	BELAJAR("belajar", 13, IzinKegiatan.BELAJAR, 0.4D, 2000, 4000, "39B"),
	MENGAJAR("mengajar", MandiriKegiatan.ACT_MENGAJAR, IzinKegiatan.BELAJAR, 0.3D, 1000, 2000, "39B"),
	MERAMU("meramu", MandiriKegiatan.ACT_ALKIMIA, IzinKegiatan.KERAJINAN, 0.3D, 2000, 2000, "39D"),
	JIMAT("jimat", MandiriKegiatan.ACT_JIMAT, IzinKegiatan.KERAJINAN, 0.3D, 1000, 2000, "39D"),
	/** Borrowed: W's {@code ACT_TEND} (16) through {@code KebunMandiri}. */
	KEBUN("kebun", 16, IzinKegiatan.KERJA, 0.3D, 2000, 3000, "39D"),
	/** Borrowed: W's {@code ACT_MINE} (15) through {@code KebunMandiri}. */
	TAMBANG("tambang", 15, IzinKegiatan.KERJA, 0.3D, 2000, 3000, "39D"),
	/** Duty posts 1-4 (water, garden, kitchen, field) through {@code Duties.assignSelf}. */
	POS_TUGAS("pos_tugas", 0, IzinKegiatan.KERJA, 0.3D, 2000, 2000, "39D"),
	/** Formation keeper (11) through {@code Keepers.assignSelf}; 0.25 while it reinforces. */
	JAGA_FORMASI("jaga_formasi", 11, IzinKegiatan.JAGA, 0.5D, 3000, 6000, "39D"),
	/** A temporary GUARD_HALL patrol at the gate (37's {@code ACT_JAGA_TEPI} on a sky hall). */
	JAGA_GERBANG("jaga_gerbang", 0, IzinKegiatan.JAGA, 0.4D, 2000, 4000, "39A"),
	/** Borrowed: R's {@code ACT_SOCIAL} (18) through {@code HubunganMandiri.forceTea}. */
	SOSIAL("sosial", 18, IzinKegiatan.SOSIAL, 0.2D, 1000, 1000, "39D"),
	MAKAN("makan", MandiriKegiatan.ACT_MAKAN, IzinKegiatan.SOSIAL, 0.2D, 1000, 1000, "39A"),
	/** K 0.5 Mortal-Foundation, 0.8 from Golden Core ("a cultivator's sleep"). */
	ISTIRAHAT("istirahat", MandiriKegiatan.ACT_ISTIRAHAT, IzinKegiatan.KULTIVASI, 0.5D, 1000, 6000, "39A"),
	/** Dao comprehension x2 while contemplating. */
	MERENUNG("merenung", MandiriKegiatan.ACT_MERENUNG, IzinKegiatan.KULTIVASI, 0.4D, 1000, 1000, "39B"),
	/** K 3.0 instead of 1.5, paid from the purse (39.5.6). */
	SERAP_BATU("serap_batu", MandiriKegiatan.ACT_SERAP_BATU, IzinKegiatan.KULTIVASI, 3.0D, 1000, 1000, "39B"),
	/** Until the friend's breakthrough ends (-1). */
	JAGA_TEROBOSAN("jaga_terobosan", MandiriKegiatan.ACT_JAGA_TEROBOSAN, IzinKegiatan.KULTIVASI, 0.4D, -1, -1, "39B"),
	/** Owned by {@code DiscipleBreakthrough}; until it ends. */
	TEROBOSAN("terobosan", 0, IzinKegiatan.KULTIVASI, 0.0D, -1, -1, "39B"),
	BERKEMAS("berkemas", MandiriKegiatan.ACT_BERKEMAS, IzinKegiatan.BELANJA, 0.0D, 200, 200, "39C"),
	/** Walk to the gate, or rise on the flying sword (52). */
	BERANGKAT("berangkat", MandiriKegiatan.ACT_TERBANG_JAUH, IzinKegiatan.BELANJA, 0.0D, 200, 200, "39C"),
	/** Back at the gate, carrying the goods to the Lumbung (47). */
	TIBA("tiba", MandiriKegiatan.ACT_BAWA, IzinKegiatan.BELANJA, 0.0D, 200, 600, "39C"),
	/** Away from the hall (the body is in {@code AwayStore}); length from the plan. */
	PERJALANAN("perjalanan", 0, IzinKegiatan.BERKELANA, 0.8D, 6000, 120000, "39C"),
	/** Snow, harvest or festival play in the courtyard: no body activity, the executor plays the play clips (chase, cuju). */
	BERMAIN("bermain", 0, IzinKegiatan.SOSIAL, 0.2D, 1000, 1000, "39A"),
	/** 39.F T1 quarrel (57). */
	CEKCOK("cekcok", MandiriKegiatan.ACT_CEKCOK, IzinKegiatan.SOSIAL, 0.0D, 300, 600, "39F"),
	/** 39.F T2 fist-and-palm salute before the honour duel (58). */
	TANTANG("tantang", MandiriKegiatan.ACT_TANTANG, IzinKegiatan.TANDING, 0.0D, 100, 200, "39F"),
	/** 39.F limping home bandaged while badly wounded (59); until healed. */
	TERLUKA("terluka", MandiriKegiatan.ACT_TERLUKA, IzinKegiatan.KULTIVASI, 0.0D, -1, -1, "39F"),
	/** 39.F T5 the loser's vow of revenge (60). */
	SUMPAH("sumpah", MandiriKegiatan.ACT_SUMPAH, IzinKegiatan.SOSIAL, 0.0D, 200, 200, "39F");

	/** One world hour in ticks (the planner's block unit, 39.4.1). */
	public static final int JAM = 1000;

	private final String id;
	private final int act;
	private final IzinKegiatan izin;
	private final double k;
	private final int blokMin;
	private final int blokMaks;
	private final String pemilik;

	Kegiatan(String id, int act, IzinKegiatan izin, double k, int blokMin, int blokMaks, String pemilik) {
		this.id = id;
		this.act = act;
		this.izin = izin;
		this.k = k;
		this.blokMin = blokMin;
		this.blokMaks = blokMaks;
		this.pemilik = pemilik;
	}

	public String id() {
		return id;
	}

	/** The synced body activity shown while it runs (0 = chosen by the executor). */
	public int act() {
		return act;
	}

	public IzinKegiatan izin() {
		return izin;
	}

	/** The base cultivation factor K (39.4.2, 39.5.1); executors may refine it ({@link Pelaksana#K}). */
	public double k() {
		return k;
	}

	/** Shortest block in ticks, or -1 when it lasts until the executor ends it. */
	public int blokMin() {
		return blokMin;
	}

	/** Longest block in ticks, or -1 when it lasts until the executor ends it. */
	public int blokMaks() {
		return blokMaks;
	}

	/** The DESIGN 39 module that registers the executor ("39A" ... "39F"). */
	public String pemilik() {
		return pemilik;
	}

	/** True for the journey steps that leave or re-enter the hall (BERKEMAS, BERANGKAT, TIBA, PERJALANAN). */
	public boolean perjalanan() {
		return this == BERKEMAS || this == BERANGKAT || this == TIBA || this == PERJALANAN;
	}

	/** {@code kultivator.sekte.mandiri.inti.kegiatan.<id>}. */
	public String kunciNama() {
		return "kultivator.sekte.mandiri.inti.kegiatan." + id;
	}

	public static @Nullable Kegiatan byId(@Nullable String id) {
		if (id == null) {
			return null;
		}
		for (Kegiatan kegiatan : values()) {
			if (kegiatan.id.equals(id)) {
				return kegiatan;
			}
		}
		return null;
	}

	/** By saved ordinal; {@link #KOSONG} when out of range. */
	public static Kegiatan byOrdinal(int ordinal) {
		Kegiatan[] values = values();
		return ordinal < 0 || ordinal >= values.length ? KOSONG : values[ordinal];
	}
}
