package dev.kultivator.sekte.mandiri.api;

import java.util.List;

/**
 * Contract C39.K4 (DESIGN 39.6.8, 39.6.12): the guild missions a disciple may take in town. 38EK may provide it with
 * {@code MandiriApi.setGilda}; without one, 39C's {@code GildaBawaan} uses the 39.6.8 formula (success
 * {@code clamp(0.45 + 0.12 (P - (tier - 1)), 0.10, 0.95)}, pay {@code 6 x (band + 1) x rank factor} NB with E 0.6 ... S
 * 2.0). Ranks: 0 E, 1 D, 2 C, 3 B, 4 A, 5 S (the disciple's badge; max by sect rank: Outer C, Inner B, Core A,
 * Elder S). Server side only.
 */
public interface GildaMurid {

	/** Rank letters by index. */
	List<String> PANGKAT = List.of("E", "D", "C", "B", "A", "S");

	/**
	 * One mission on the board.
	 *
	 * @param id         stable id on that board today
	 * @param pangkat    rank 0-5
	 * @param tier       danger tier (stage success uses it)
	 * @param jenis      kind ({@code antar}, {@code kawal}, {@code buru}, {@code herba}, {@code usir} ...)
	 * @param upahDasar  base pay in NB
	 * @param kunciJudul title key shown in the diary and the travel scroll
	 */
	record MisiMurid(String id, int pangkat, int tier, String jenis, long upahDasar, String kunciJudul) {
		public MisiMurid {
			id = id == null ? "" : id;
			pangkat = Math.max(0, Math.min(5, pangkat));
			tier = Math.max(0, tier);
			jenis = jenis == null ? "" : jenis;
			upahDasar = Math.max(0L, upahDasar);
			kunciJudul = kunciJudul == null ? "" : kunciJudul;
		}
	}

	/** Missions of rank up to {@code pangkat} on the board of {@code town} (packed centre). */
	List<MisiMurid> papan(long town, int pangkat);

	/** The pay of a finished mission (0 for a failure unless the provider pays a share). */
	long upah(MisiMurid misi, boolean sukses);

	/** No guild: an empty board (39C's default replaces it at init). */
	GildaMurid NONE = new GildaMurid() {
		@Override
		public List<MisiMurid> papan(long town, int pangkat) {
			return List.of();
		}

		@Override
		public long upah(MisiMurid misi, boolean sukses) {
			return 0L;
		}
	};
}
