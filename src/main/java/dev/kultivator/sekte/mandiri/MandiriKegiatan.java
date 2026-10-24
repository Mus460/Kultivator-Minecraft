package dev.kultivator.sekte.mandiri;

import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The synced body activities 40-60 of Murid Mandiri (DESIGN 39.4.2, 39.4.8, 39.F.5) and the executor registry of the
 * planner (contract C39.A5).
 * <p>
 * <b>Activity ids.</b> {@code DiscipleEntity.DATA_ACTIVITY} is a synced byte: 1-12 are the old activities, 13-24 DESIGN
 * 36 ({@code SekteIds}), 25-27 DESIGN 37 ({@code LangitIds}), 32 {@code Upacara.ACT_AWAY}; DESIGN 39 owns 40-56 and the
 * feud module 39F 57-60 ({@code SekteIds.ACT_MANDIRI_FIRST}..{@code ACT_MANDIRI_LAST}). The holding set (the disciple
 * keeps its spot like the duty posts) and the sitting set (counts as meditation, so the old meditation XP, regeneration,
 * Master Guidance and the formation factor of {@code DiscipleBridge} work without touching the master's order) are
 * answered by {@code SekteIds.mandiriHolding}/{@code mandiriSits}, which {@code DiscipleEntity} reads. None of 40-60 is
 * saved with the body: a reload restores 0 (rule S15) and the planner picks again. The client maps 40-56 to animation
 * clips in 40G's {@code AktivitasKlip} (DESIGN 40.5.13) and 57-60 in 39F's {@code SeteruPoses}.
 * <p>
 * <b>Executors.</b> Every {@link Kegiatan} gets at most one {@link Pelaksana}, registered by the module named in
 * {@link Kegiatan#pemilik()} from its own init through {@code MandiriApi.register}; an activity without one is never
 * chosen ({@link Pelaksana#KOSONG}).
 */
public final class MandiriKegiatan {

	/** 39D: the four-form sword kata on the training ground (sword in hand). Holds. */
	public static final int ACT_LATIH_JURUS = 40;
	/** 39D: brewing at the kitchen post, fanning a small cauldron (seated). Holds. */
	public static final int ACT_ALKIMIA = 41;
	/** 39D: writing talismans at a low desk (seated, brush). Holds. */
	public static final int ACT_JIMAT = 42;
	/** 39D: a friendly sparring pair, six scheduled exchanges. Holds. */
	public static final int ACT_TANDING = 43;
	/** 39B: a senior teaching, hands behind the back; juniors sit in {@code ACT_STUDY}. Holds. */
	public static final int ACT_MENGAJAR = 44;
	/** 39B: seclusion behind a floating seal paper. Holds and sits (meditates). */
	public static final int ACT_TUTUP_DIRI = 45;
	/** 39C: kneeling, packing a bundle before/after a journey. Pose only. */
	public static final int ACT_BERKEMAS = 46;
	/** 39C: walking with a box or bundle on the back (also the town body). Pose only. */
	public static final int ACT_BAWA = 47;
	/** 39A: eating, bowl in the left hand. Holds. */
	public static final int ACT_MAKAN = 48;
	/** 39C: bargaining with raised fingers (town body). Pose only. */
	public static final int ACT_TAWAR = 49;
	/** 39C: bent over a stall, turning an item in the hands (town body). Pose only. */
	public static final int ACT_LIHAT_LAPAK = 50;
	/** 39A: lying on the side / leaning on a tree or bench. Holds. */
	public static final int ACT_ISTIRAHAT = 51;
	/** 39C: standing on the flying sword, the spiral departure. Pose only. */
	public static final int ACT_TERBANG_JAUH = 52;
	/** 39B: contemplating a view, head slightly raised. Holds. */
	public static final int ACT_MERENUNG = 53;
	/** 39B: spirit stones orbit and dissolve into light. Holds and sits. */
	public static final int ACT_SERAP_BATU = 54;
	/** 39B: Hu Fa for a friend's breakthrough, arms crossed. Holds. */
	public static final int ACT_JAGA_TEROBOSAN = 55;
	/** 39A: self-chosen meditation at the best seat (the master's mode is not changed). Holds and sits. */
	public static final int ACT_MEDITASI_MANDIRI = 56;
	/** 39F: the quarrel, a pointing finger and a puffed chest. Holds. */
	public static final int ACT_CEKCOK = 57;
	/** 39F: the fist-and-palm challenge salute. Holds. */
	public static final int ACT_TANTANG = 58;
	/** 39F: walking home with a limp and a bandage while badly wounded. Holds (the spot is moved by 39F). */
	public static final int ACT_TERLUKA = 59;
	/** 39F: kneeling, a fist on the ground, the vow of revenge. Holds. */
	public static final int ACT_SUMPAH = 60;

	/** The activity name key {@code kultivator.sekte.mandiri.inti.act.<id>} for debug lines and the board. */
	public static String kunciNama(int act) {
		return "kultivator.sekte.mandiri.inti.act." + act;
	}

	/** The module that sets activity {@code act} (for the test listing), or null outside 40-60. */
	public static @Nullable String pemilik(int act) {
		return switch (act) {
			case ACT_LATIH_JURUS, ACT_ALKIMIA, ACT_JIMAT, ACT_TANDING -> "39D";
			case ACT_MENGAJAR, ACT_TUTUP_DIRI, ACT_MERENUNG, ACT_SERAP_BATU, ACT_JAGA_TEROBOSAN -> "39B";
			case ACT_BERKEMAS, ACT_BAWA, ACT_TAWAR, ACT_LIHAT_LAPAK, ACT_TERBANG_JAUH -> "39C";
			case ACT_MAKAN, ACT_ISTIRAHAT, ACT_MEDITASI_MANDIRI -> "39A";
			case ACT_CEKCOK, ACT_TANTANG, ACT_TERLUKA, ACT_SUMPAH -> "39F";
			default -> null;
		};
	}

	// ================================================================== executor registry (C39.A5)

	private static final Map<Kegiatan, Pelaksana> PELAKSANA = new EnumMap<>(Kegiatan.class);

	/**
	 * Registers the executor of {@code kegiatan} (replacing an earlier one, with a log line from the caller's module if it
	 * cares). Called once from the owning module's init; never per tick.
	 */
	public static synchronized void register(Kegiatan kegiatan, Pelaksana pelaksana) {
		if (kegiatan != null && kegiatan != Kegiatan.KOSONG && pelaksana != null) {
			PELAKSANA.put(kegiatan, pelaksana);
		}
	}

	/** The executor of {@code kegiatan}, or {@link Pelaksana#KOSONG} (never chosen) when none is registered. */
	public static Pelaksana pelaksana(Kegiatan kegiatan) {
		Pelaksana pelaksana = kegiatan == null ? null : PELAKSANA.get(kegiatan);
		return pelaksana == null ? Pelaksana.KOSONG : pelaksana;
	}

	/** True when a module registered an executor for {@code kegiatan}. */
	public static boolean ada(Kegiatan kegiatan) {
		return kegiatan != null && PELAKSANA.containsKey(kegiatan);
	}

	/** How many activities have an executor (for {@code uji perguruan mandiri anggaran}). */
	public static int jumlahTerdaftar() {
		return PELAKSANA.size();
	}

	private MandiriKegiatan() {
	}
}
