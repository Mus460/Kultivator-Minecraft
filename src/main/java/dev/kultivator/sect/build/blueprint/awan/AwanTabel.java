package dev.kultivator.sect.build.blueprint.awan;

/**
 * The numbers of the Awan family A1-A9 (DESIGN 37.3.2, 37.4.4, 21.11): one {@link Baris} per sky template 11-19
 * ({@code ROWS[n - 1]}, n = the sibling ground template). Inputs only; every position and radius is computed by
 * {@link AwanLayout} from these.
 * <p>
 * Deviations from the design tables (documented in {@code docs/progres/langit-37B.md}): the design gave the land
 * sibling's footprint and main radii 8-44, but a plaza with the four duty posts, the Aula Pendiri and the side halls
 * cannot stand on an 8-block rock, so the islands are sized from their buildings (the footprint is the box of the whole
 * archipelago, see {@link AwanLayout#footprint}), and the territory grows to cover every satellite. The sibling's
 * disc, tower rooms, Qi seat, gate altars and exact items (DESIGN 21.11) are kept exactly.
 *
 * @param n          sibling ground template 1-9
 * @param id         stable id ({@code SectTemplates.SKY_IDS})
 * @param kelas      size class 0 (A1-A3), 1 (A4-A6), 2 (A7-A9): building sizes and plaza
 * @param satelit    number of satellite islands (3, 3, 4, 4, 5, 5, 6, 6, 6)
 * @param above      minimum height envelope above the plaza (the layout may raise it)
 * @param below      minimum envelope below the plaza floor (the layout may raise it)
 * @param discD      disc lodestone distance (sibling, Table B)
 * @param discR      disc ring radius (sibling)
 * @param territory  sibling territory radius R_t (the layout keeps at least this)
 * @param towerRooms Blazing Tower rooms (sibling)
 * @param seat       Qi seat tier of T8 (0 none: A1-A2; 1 A3; 2 A4-A6; 3 A7-A9)
 * @param gateAltars T5 gate altars (sibling: 0, 2 for A6, 4 for A7-A9)
 * @param lapis      outer terraces (1-3)
 * @param bukit      height of the pagoda hill (0 = none)
 * @param kristal    hanging crystal clusters of T9
 * @param airTerjun  mist-waterfall emitters of T4
 * @param meditasi   rim meditation pavilions of T8
 * @param dermaga    docks of T7 (1 PERAHU, 2 + SEKTE, 3 + BAHTERA)
 * @param vats       T1 water vats (exact cauldrons)
 * @param shelves    T3 bookshelves (exact)
 * @param lecterns   T3 lecterns (exact)
 * @param alchemy    T4 alchemy furnaces (cauldron + soul campfire, exact)
 * @param brewing    T4 brewing stands (exact)
 * @param dings      T6 ding campfires (exact; the first is the signal ding with its hay block)
 * @param targets    T7 target blocks (exact)
 * @param bell       T7 bell frame (templates 2-9)
 * @param drum       T7 drum barrel (templates 3-9)
 * @param pagodaFloors T8 pagoda floors (3, 5, 7)
 */
public record AwanTabel(int n, String id, int kelas, int satelit, int above, int below, int discD, int discR, int territory, int towerRooms,
		int seat, int gateAltars, int lapis, int bukit, int kristal, int airTerjun, int meditasi, int dermaga, int vats, int shelves, int lecterns,
		int alchemy, int brewing, int dings, int targets, boolean bell, boolean drum, int pagodaFloors) {

	/** Rows of A1-A9 (index n - 1). Numbers from DESIGN 37.3.2 / 37.4.4 and the sibling's DESIGN 21.11 exact items. */
	public static final AwanTabel[] ROWS = {
			new AwanTabel(1, "awan_pertapa", 0, 3, 20, 17, 2, 4, 24, 3, 0, 0, 1, 0, 2, 2, 2, 1, 2, 14, 1, 1, 1, 1, 4, false, false, 3),
			new AwanTabel(2, "awan_kabut", 0, 3, 26, 19, 6, 4, 32, 3, 0, 0, 1, 0, 3, 2, 2, 1, 2, 16, 1, 1, 1, 1, 4, true, false, 3),
			new AwanTabel(3, "awan_bangau", 0, 4, 32, 22, 6, 5, 40, 4, 1, 0, 1, 6, 4, 3, 3, 1, 4, 24, 1, 1, 1, 1, 6, true, true, 3),
			new AwanTabel(4, "awan_biru", 1, 4, 36, 27, 6, 5, 46, 4, 2, 0, 2, 6, 5, 3, 3, 2, 4, 32, 1, 1, 2, 1, 8, true, true, 5),
			new AwanTabel(5, "awan_puncak", 1, 5, 42, 31, 6, 5, 58, 5, 2, 0, 2, 8, 6, 4, 4, 2, 4, 40, 2, 1, 2, 2, 8, true, true, 5),
			new AwanTabel(6, "awan_suci", 1, 5, 46, 37, 6, 7, 68, 6, 2, 2, 2, 8, 8, 4, 4, 2, 6, 48, 2, 2, 2, 2, 10, true, true, 5),
			new AwanTabel(7, "awan_gua_surga", 2, 6, 50, 42, 6, 8, 80, 6, 3, 4, 3, 10, 9, 5, 5, 3, 6, 56, 2, 2, 3, 2, 12, true, true, 7),
			new AwanTabel(8, "awan_kunlun", 2, 6, 54, 49, 6, 8, 96, 7, 3, 4, 3, 12, 10, 6, 6, 3, 8, 64, 2, 2, 3, 2, 12, true, true, 7),
			new AwanTabel(9, "awan_sembilan_langit", 2, 6, 60, 56, 6, 9, 108, 7, 3, 4, 3, 14, 12, 8, 6, 3, 8, 72, 2, 2, 3, 2, 16, true, true, 7)};

	/** The row of sky template {@code sky} (11-19) or sibling {@code 1-9}; clamped. */
	public static AwanTabel of(int template) {
		int n = template > 10 ? template - 10 : template;
		return ROWS[Math.max(1, Math.min(9, n)) - 1];
	}

	/** The sky template number 11-19. */
	public int sky() {
		return n + 10;
	}
}
