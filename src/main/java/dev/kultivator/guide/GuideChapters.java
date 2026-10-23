package dev.kultivator.guide;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Kitab Panduan (the in-game guide): the static list of its 17 chapters and their steps, shared by the server and the
 * client. Pure data: no Minecraft classes, so both sides may use it freely.
 * <p>
 * Contract with {@code client.guide.GuideScreen} and {@link GuideProgress}:
 * <ul>
 * <li>A step id is {@code c<chapter number>.<name>}, e.g. {@code c1.root_test}. Its texts are
 * {@code kultivator.guide.<id>.title} and {@code kultivator.guide.<id>.body} (one key, its lines separated
 * by line breaks), in {@code lang-src/guide.*.json}.</li>
 * <li>A chapter's title is {@code kultivator.guide.chapter.<number>} (1-16) and its one-line summary
 * {@code kultivator.guide.chapter.<number>.desc}.</li>
 * <li>{@link Step#hint()} is a command the screen offers as a clickable chip (prefilled in the chat box), or null.</li>
 * <li>{@link Step#check()} is the id of an automatic check {@link GuideProgress} evaluates on the server; a step without
 * one ({@link Step#manual()}) gets a tick box the player toggles (GuideAction {@code tick <id>}).</li>
 * <li>The bit order of {@code Payloads.GuideSync} is the order of {@link #steps()} ({@link Step#index()}).</li>
 * <li>An {@link Step#optional()} step (a side goal, a late target, a tip) never holds the current chapter, and neither
 * does a manual one: only {@link Step#core()} steps do (integration audit T1).</li>
 * <li>Chapter indexes and step ids are the save keys and never change meaning; the book shows the chapters in
 * {@link #displayOrder()}, and "Bab N" is the place in that order ({@link Chapter#number()}).</li>
 * <li>A chapter has the realm it is meant for ({@link Chapter#minRealm()}), the realm from which it stops holding the
 * current chapter ({@link Chapter#pastRealm()}; its steps stay open) and one more optional check
 * ({@link Chapter#requires()}, {@code hall} for the sect chapters).</li>
 * </ul>
 * Check ids: {@code root}, {@code dao}, {@code cave}, {@code heart} (a Heart Demon defeated), {@code hall} (a Sect Hall
 * founded), {@code template}, {@code ascended} (Heaven's Acknowledgement), {@code bar_mortal}, {@code adv:<advancement>},
 * {@code rank:<realm>}, {@code at:<realm>:<layer 1-9>:<stage 1-4>}, {@code med:<minutes>}, {@code hall_level:<n>},
 * {@code disciples:<n>}, {@code tier:<n>} (template tier built), {@code stars:<n>}, {@code trib:<n>}; {@code a|b} is done
 * when either is.
 * <p>
 * Sumber: Terinspirasi dari bimbingan Yao Lao kepada Xiao Yan (Battle Through the Heavens). Isi bab adalah desain game;
 * setiap angka diambil dari kode dan docs/PANDUAN.md.
 */
public final class GuideChapters {

	/**
	 * Chapters by save index: Hari Pertama ... Tips & Kesalahan Umum, then (DESIGN 34.9) Dunia yang Luas and Ujung Dunia.
	 * Chapters 13 and 14 are appended after chapter 12; their texts live in {@code lang-src/dunia_panduan.*}. Chapters 15
	 * (Murid yang Hidup) and 16 (Sekte di Dunia) are Sekte yang Hidup's (DESIGN 36.3.6); their texts live in
	 * {@code lang-src/sekte_panduan.*} and their checks are the {@code sekte/} advancements of the ten modules. Chapter 17
	 * (save index 16, Sekte Langit, DESIGN 37.7.8, module 37G) has its texts in {@code lang-src/sekte_langit_panduan.*} and
	 * its checks are the {@code sekte_langit/} advancements. The book shows them in {@link #displayOrder()}.
	 */
	public static final int CHAPTER_COUNT = 17;

	/**
	 * The order the book shows the chapters in, as 0-based save indexes (integration audit T1): Dunia yang Luas right
	 * after Pemurnian Qi, the Sekte yang Hidup chapters beside the older sect chapters, Ujung Dunia before Kenaikan Abadi
	 * and the Tips last. Sekte Langit (37) inserts its chapter 17 (save index 16) right after chapter 9 (save index 8).
	 */
	private static final int[] DISPLAY_ORDER = {0, 1, 12, 2, 3, 4, 14, 5, 15, 6, 7, 8, 16, 13, 9, 10, 11};

	/**
	 * Per save index: {minRealm, pastRealm, requires}. minRealm: the chapter becomes current only from that realm on (null:
	 * from the start). pastRealm: from that realm on it no longer holds the current chapter, so a player who skipped a side
	 * activity is not kept on an old chapter (null: never). requires: one more check id (null: none); the sect chapters need
	 * a Sect Hall of the player's own, so a hermit is never told to found a sect. Sekte Langit's chapter 17 uses
	 * {@code {minRealm, null, "hall_level:6|tier:6"}}.
	 */
	private static final String[][] GATES = {
			{null, "foundation", null}, // 1 Hari Pertama
			{"qi_refining", "foundation", null}, // 2 Pemurnian Qi
			{"qi_refining", "golden_core", null}, // 3 Pembangunan Fondasi
			{"foundation", "nascent_soul", null}, // 4 Inti Emas
			{null, null, "hall"}, // 5 Mendirikan Sekte: current once the Prasasti stands
			{"foundation", "deity_transformation", "hall"}, // 6 Murid
			{"golden_core", "immortal_ascension", null}, // 7 Jiwa Baru Lahir sampai Penyatuan Dao
			{"golden_core", "immortal_ascension", "hall"}, // 8 Formasi Sekte
			{"nascent_soul", "true_immortal", "hall"}, // 9 Sekte Tingkat Tinggi
			{"dao_union", "true_immortal", null}, // 10 Kenaikan Abadi
			{"immortal_ascension", null, null}, // 11 Dewa Abadi
			{null, null, null}, // 12 Tips & Kesalahan Umum: only optional steps, never current
			{"qi_refining", "golden_core", null}, // 13 Dunia yang Luas
			{"golden_core", "immortal_ascension", null}, // 14 Ujung Dunia
			{"foundation", "deity_transformation", "hall"}, // 15 Murid yang Hidup
			{"golden_core", "immortal_ascension", "hall"}, // 16 Sekte di Dunia
			{"golden_core", null, "hall_level:6|tier:6"}, // 17 Sekte Langit (37G): a level-6 hall or a tier-6 site first
	};

	/**
	 * One step. {@code chapter} is the 0-based save index, {@code index} its place in {@link #steps()} (the bit of
	 * GuideSync), {@code hint} an optional command, {@code check} an optional automatic check id, {@code optional} a side
	 * goal that never holds the current chapter.
	 */
	public record Step(String id, int chapter, int index, @Nullable String hint, @Nullable String check, boolean optional) {

		public String titleKey() {
			return "kultivator.guide." + id + ".title";
		}

		/** One key; its lines are separated by line breaks. */
		public String bodyKey() {
			return "kultivator.guide." + id + ".body";
		}

		/** True when the player ticks this step by hand (no automatic check). */
		public boolean manual() {
			return check == null;
		}

		public boolean hasHint() {
			return hint != null && !hint.isEmpty();
		}

		/** True for a step that holds the current chapter until it is done: checked automatically and not optional. */
		public boolean core() {
			return !manual() && !optional;
		}
	}

	/**
	 * One chapter: its 0-based save {@code index}, its steps (4-10) in order, its 0-based {@code position} in
	 * {@link #displayOrder()} and its gates (see {@link #GATES}; realms by id).
	 */
	public record Chapter(int index, List<Step> steps, int position, @Nullable String minRealm, @Nullable String pastRealm,
			@Nullable String requires) {

		/** 1-based place in the display order, as shown to the player ("Bab 3"). */
		public int number() {
			return position + 1;
		}

		public String titleKey() {
			return "kultivator.guide.chapter." + (index + 1);
		}

		public String descKey() {
			return "kultivator.guide.chapter." + (index + 1) + ".desc";
		}

		/** The {@link Step#index()} of the chapter's first step. */
		public int firstIndex() {
			return steps.isEmpty() ? 0 : steps.get(0).index();
		}

		/** True while one of its core steps is not done. */
		public boolean coreLeft(BitSet done) {
			for (Step step : steps) {
				if (step.core() && !done.get(step.index())) {
					return true;
				}
			}
			return false;
		}

		/** True while any of its steps (core, manual or optional) is not done. */
		public boolean anyLeft(BitSet done) {
			for (Step step : steps) {
				if (!done.get(step.index())) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * What {@link #currentChapter} asks about a chapter for one player: whether its realm and its extra check are met
	 * ({@code open}) and whether the player already reached its {@link Chapter#pastRealm()} ({@code past}).
	 */
	public interface Gate {
		boolean open(Chapter chapter);

		boolean past(Chapter chapter);
	}

	private static final List<Step> STEPS = new ArrayList<>();
	private static final List<Step> STEPS_VIEW = Collections.unmodifiableList(STEPS);
	private static final Map<String, Step> BY_ID = new HashMap<>();
	private static final List<Chapter> CHAPTERS;
	private static final List<Chapter> DISPLAY;

	static {
		// 1. Hari Pertama
		step(0, "scroll", "/kultivasi", null);
		step(0, "root_test", "/kultivasi root test", "root");
		step(0, "meditate", "/kultivasi meditate", "adv:first_meditation");
		step(0, "hud", null, null);
		step(0, "settings", null, null);
		step(0, "fill_bar", null, "bar_mortal");
		step(0, "awaken", "/kultivasi breakthrough", "rank:qi_refining");
		step(0, "jalan", "/kultivasi jalan", null);
		// 2. Pemurnian Qi
		step(1, "stages", null, "at:qi_refining:1:2");
		step(1, "meditate30", "/kultivasi meditate", "med:30");
		step(1, "sites", null, "adv:spiritual_site");
		step(1, "veins", null, null);
		step(1, "stones", null, "adv:first_spirit_stone");
		step(1, "formation1", null, "adv:qi_formation_small");
		step(1, "formation2", null, "adv:qi_formation_great");
		// a later target (End Rods from The End): it never holds the chapter
		optional(1, "formation3", null, "adv:qi_formation_heavenly");
		step(1, "peak", null, "at:qi_refining:1:4");
		// Duel Pedang (DESIGN 35.8.5): deflecting monsters, posture and the Deathblow (side goals)
		optional(1, "tangkis", null, "adv:duel_first_deflect");
		optional(1, "postur", null, "adv:duel_first_deathblow");
		// 3. Pembangunan Fondasi
		step(2, "prep", null, null);
		step(2, "chance", null, null);
		step(2, "furnace", "/kultivasi resep", "adv:first_alchemy");
		step(2, "pills", null, null);
		step(2, "toxicity", null, null);
		step(2, "breakthrough", "/kultivasi breakthrough", "rank:foundation");
		step(2, "sword", null, "adv:flying_sword");
		step(2, "quality", null, null);
		// Duel Pedang (DESIGN 35.8.5): the first formal duel and the Perfect Deflect (side goals)
		optional(2, "duel", "/kultivasi duel tantang ", "adv:duel_first_win");
		optional(2, "sempurna", null, "adv:duel_first_perfect");
		// 4. Inti Emas
		step(3, "trials", "/kultivasi jalan", "adv:foundation_trials");
		step(3, "trib_prep", null, null);
		step(3, "trib", "/kultivasi breakthrough", "rank:golden_core");
		step(3, "dao", "/kultivasi dao choose ", "dao");
		step(3, "comprehension", null, "adv:comprehension_minor");
		step(3, "codex", "/kultivasi teknik", "adv:dao_technique");
		step(3, "elite", null, "adv:first_elite");
		step(3, "heart_prep", null, null);
		// Duel Pedang (DESIGN 35.8.5): the Blade Lock and the perilous attacks (side goals)
		optional(3, "kunci", null, "adv:duel_bind_win");
		optional(3, "bahaya", null, "adv:duel_mikiri");
		// 5. Mendirikan Sekte
		step(4, "stele", null, null);
		step(4, "place", "/kultivasi aula nama ", "hall");
		step(4, "template", "/kultivasi aula bangun templat", "template");
		step(4, "confirm", "/kultivasi aula bangun konfirmasi", "adv:first_stone");
		step(4, "deposit", "/kultivasi aula bangun setor butuh", "tier:1");
		step(4, "special", null, null);
		step(4, "blocked", null, null);
		step(4, "move", null, null);
		// 6. Murid
		step(5, "candidates", null, null);
		step(5, "test", null, null);
		step(5, "accept", null, "adv:first_disciple");
		step(5, "screen", "/kultivasi murid list", null);
		step(5, "guidance", null, null);
		step(5, "teach", null, "adv:disciple_taught");
		step(5, "gifts", null, null);
		optional(5, "hufa", "/kultivasi murid tahan", "adv:disciple_guardian");
		optional(5, "disciple_gc", null, "adv:disciple_golden_core");
		step(5, "destroyer", "/kultivasi kehancuran sumpah", null);
		// Duel Pedang (DESIGN 35.8.5): watching two disciples duel (a side goal)
		optional(5, "tonton", "/kultivasi duel murid ", "adv:duel_disciples_watched");
		// 7. Jiwa Baru Lahir sampai Penyatuan Dao
		step(6, "heart", null, "heart");
		step(6, "rings", null, null);
		step(6, "ns", "/kultivasi breakthrough", "rank:nascent_soul");
		step(6, "protectors", "/kultivasi kawal ", null);
		step(6, "talisman", null, "adv:first_talisman");
		step(6, "dt", "/kultivasi breakthrough", "rank:deity_transformation");
		step(6, "du", "/kultivasi breakthrough", "rank:dao_union");
		step(6, "cave", "/kultivasi gua tetapkan", "cave");
		step(6, "strengthen", "/kultivasi fondasi perkokoh", "adv:foundation_strengthened");
		// moved here from Ujung Dunia (was c14.evolusi) so that chapter keeps 10 steps: a spirit beast of tier 4
		optional(6, "evolusi", null, "adv:dunia/beast_tier_4");
		// 8. Formasi Sekte
		step(7, "eye", null, null);
		step(7, "first", "/kultivasi formasi pola small_spirit_gathering", "adv:formation_eye");
		step(7, "power", "/kultivasi formasi setor semua", null);
		step(7, "keeper", "/kultivasi formasi penjaga ", "adv:formation_keeper");
		step(7, "grades", "/kultivasi formasi katalog", null);
		step(7, "b5", "/kultivasi formasi pola great_spirit_gathering", "adv:stone_formation");
		optional(7, "seat", null, "tier:8");
		optional(7, "a1", null, "adv:ten_thousand_immortals");
		// 9. Sekte Tingkat Tinggi
		step(8, "renown", "/kultivasi aula info", "hall_level:3");
		step(8, "level5", null, "adv:hall_level_5");
		step(8, "tier5", null, "adv:sect_tier_5");
		step(8, "bulk", null, null);
		step(8, "events", null, "adv:first_expedition");
		step(8, "ranks", null, "adv:first_promotion");
		step(8, "raids", "/kultivasi serbuan info", "adv:raid_repelled");
		step(8, "vassal", null, "adv:village_pledge");
		step(8, "storage", null, null);
		optional(8, "level9", null, "adv:hall_level_9");
		// 10. Kenaikan Abadi
		step(9, "enter", "/kultivasi breakthrough", "rank:immortal_ascension");
		step(9, "caps", null, null);
		step(9, "boons", null, "at:immortal_ascension:3:1");
		step(9, "speed", null, null);
		step(9, "tomb", "/kultivasi alam masuk heavenly_tomb", "adv:heavenly_tomb_9");
		step(9, "layer5", null, "at:immortal_ascension:5:1");
		step(9, "layer9", null, "at:immortal_ascension:9:4");
		// 11. Dewa Abadi
		step(10, "ti", "/kultivasi breakthrough", "rank:true_immortal");
		step(10, "acknowledged", null, "ascended");
		step(10, "peak", null, "at:true_immortal:1:4");
		step(10, "stars", "/kultivasi bintang padatkan", "stars:1");
		step(10, "calamity", "/kultivasi bencana mulai", "adv:star_calamity_1");
		step(10, "nine", null, "stars:9");
		step(10, "ascend", "/kultivasi abadi naik", "adv:immortal_realm");
		step(10, "titah", "/kultivasi titah", "adv:titah_first");
		// moved here from Ujung Dunia (were c14.kota_abadi and c14.puncak): both belong to the Dewa Abadi years
		optional(10, "kota_abadi", null, "adv:dunia/immortal_city");
		optional(10, "puncak", null, "adv:dunia/tower_100");
		// 12. Tips & Kesalahan Umum (all optional: reading, never a gate)
		optional(11, "veins", null, null);
		optional(11, "health", null, null);
		optional(11, "flee", null, null);
		optional(11, "pills", null, null);
		optional(11, "presence", null, null);
		optional(11, "stones", null, null);
		optional(11, "kehancuran", "/kultivasi kehancuran", null);
		optional(11, "hardcore", null, null);
		optional(11, "help", "/kultivasi panduan", null);
		// Duel Pedang (DESIGN 35.8.5): the common duel mistakes
		optional(11, "duel_tips", null, null);
		// 13. Dunia yang Luas (DESIGN 34.9; appended, saved ticks are by step id so nothing shifts)
		step(12, "peta", "/kultivasi peta", "adv:dunia/map_opened");
		step(12, "kota", null, "adv:dunia/kota_found");
		step(12, "teh", null, "adv:dunia/kota_rumour");
		step(12, "lelang", null, "adv:dunia/kota_auction");
		step(12, "pengembara", null, "adv:dunia/wanderer_talk");
		step(12, "langit", null, "adv:dunia/sky_event");
		step(12, "kabut", null, "adv:dunia/qi_mist");
		step(12, "harta", null, "adv:dunia/treasure_claimed");
		step(12, "gua", null, "adv:dunia/ruin_cleared");
		// floor 10 is a Foundation target, so it belongs here (was c14.menara)
		step(12, "menara", null, "adv:dunia/tower_10");
		// 14. Ujung Dunia (DESIGN 34.9)
		step(13, "saingan", null, "adv:dunia/rival_beaten");
		// the Cloud Ship opens at Golden Core and leads to the voyage below (was c13.kapal)
		step(13, "kapal", null, "adv:dunia/ship_launched");
		step(13, "bawah", null, "adv:dunia/naihe_crossed");
		step(13, "yama", null, "adv:dunia/ten_courts");
		step(13, "naga", null, "adv:dunia/dragon_subdued");
		step(13, "laut", null, "adv:dunia/sea_arrived");
		step(13, "istana", null, "adv:dunia/dragon_king");
		step(13, "penjara", null, "adv:dunia/prison_sealed|adv:dunia/prison_slain");
		step(13, "pelayaran", null, "adv:dunia/ship_voyage");
		step(13, "bajak", null, "adv:dunia/ship_pirates");
		// 15. Murid yang Hidup (DESIGN 36.3.6; each advancement belongs to the module named in its id)
		step(14, "sifat", "/kultivasi perguruan murid", "adv:sekte/kepribadian_sifat");
		step(14, "puas", null, "adv:sekte/kepribadian_puas");
		step(14, "rukun", null, "adv:sekte/kepribadian_rukun");
		step(14, "sahabat", null, "adv:sekte/hubungan_sahabat");
		step(14, "saingan", null, "adv:sekte/hubungan_saingan");
		step(14, "tangga", null, "adv:sekte/tangga_selesai");
		step(14, "kitab", null, "adv:sekte/paviliun_belajar");
		step(14, "leluhur", null, "adv:sekte/paviliun_upacara");
		step(14, "tetua", null, "adv:sekte/tetua_jabatan");
		// the heir needs ★9 (Dewa Abadi Puncak): a late target, never a gate
		optional(14, "penerus", null, "adv:sekte/tetua_penerus");
		// 16. Sekte di Dunia (DESIGN 36.3.6)
		step(15, "lambang", "/kultivasi perguruan lambang", "adv:sekte/seragam_lambang");
		step(15, "bendera", null, "adv:sekte/seragam_bendera");
		step(15, "turnamen", null, "adv:sekte/turnamen_juara");
		step(15, "misi", null, "adv:sekte/misi_pulang");
		step(15, "bawa", null, "adv:sekte/misi_bawa");
		step(15, "kebun", null, "adv:sekte/kebun_panen");
		step(15, "tambang", null, "adv:sekte/kebun_tambang");
		step(15, "rival", "/kultivasi perguruan diplomasi", "adv:sekte/diplomasi_temu");
		step(15, "sekutu", null, "adv:sekte/diplomasi_sekutu");
		optional(15, "perang", null, "adv:sekte/diplomasi_menang");
		// 17. Sekte Langit (DESIGN 37.7.8, module 37G; appended last: the GuideSync bits follow this order). "jaring" and
		// "sembilan" are optional (audit 4.5): the net may never need to catch anyone, the Nine Heavens is a late target.
		step(16, "syarat", "/kultivasi aula langit", "hall_level:6|tier:6");
		step(16, "rancang", "/kultivasi aula langit rancang", "adv:sekte_langit/rancang");
		step(16, "tapak", null, "adv:sekte_langit/tapak_awan");
		step(16, "naik", null, "adv:sekte_langit/naik");
		step(16, "formasi", null, "adv:sekte_langit/pindah_awan");
		step(16, "tangga", null, "adv:sekte_langit/seribu_anak_tangga");
		optional(16, "jaring", null, "adv:sekte_langit/tertangkap|adv:sekte_langit/terjun_awan");
		step(16, "feri", "/kultivasi langit feri", "adv:sekte_langit/feri");
		step(16, "bajak", null, "adv:sekte_langit/bajak_awan");
		step(16, "temu", null, "adv:sekte_langit/temu_langit");
		optional(16, "sembilan", null, "adv:sekte_langit/sembilan_langit");

		if (GATES.length != CHAPTER_COUNT || DISPLAY_ORDER.length != CHAPTER_COUNT) {
			throw new IllegalStateException("Guide chapter tables out of step with CHAPTER_COUNT");
		}
		int[] positions = new int[CHAPTER_COUNT];
		java.util.Arrays.fill(positions, -1);
		for (int position = 0; position < DISPLAY_ORDER.length; position++) {
			int index = DISPLAY_ORDER[position];
			if (index < 0 || index >= CHAPTER_COUNT || positions[index] >= 0) {
				throw new IllegalStateException("Guide display order is not a permutation: " + index);
			}
			positions[index] = position;
		}
		List<Chapter> chapters = new ArrayList<>(CHAPTER_COUNT);
		for (int chapter = 0; chapter < CHAPTER_COUNT; chapter++) {
			List<Step> own = new ArrayList<>();
			for (Step step : STEPS) {
				if (step.chapter() == chapter) {
					own.add(step);
				}
			}
			String[] gate = GATES[chapter];
			chapters.add(new Chapter(chapter, Collections.unmodifiableList(own), positions[chapter], gate[0], gate[1], gate[2]));
		}
		CHAPTERS = Collections.unmodifiableList(chapters);
		List<Chapter> display = new ArrayList<>(CHAPTER_COUNT);
		for (int index : DISPLAY_ORDER) {
			display.add(CHAPTERS.get(index));
		}
		DISPLAY = Collections.unmodifiableList(display);
	}

	private static void step(int chapter, String name, @Nullable String hint, @Nullable String check) {
		add(chapter, name, hint, check, false);
	}

	/** A side goal: shown and checked like any step, but it never holds the current chapter. */
	private static void optional(int chapter, String name, @Nullable String hint, @Nullable String check) {
		add(chapter, name, hint, check, true);
	}

	private static void add(int chapter, String name, @Nullable String hint, @Nullable String check, boolean optional) {
		String id = "c" + (chapter + 1) + "." + name;
		Step step = new Step(id, chapter, STEPS.size(), hint, check, optional);
		if (BY_ID.put(id, step) != null) {
			throw new IllegalStateException("Duplicate guide step " + id);
		}
		STEPS.add(step);
	}

	/** Every step in bit order. */
	public static List<Step> steps() {
		return STEPS_VIEW;
	}

	/** The number of steps (the bits of GuideSync). */
	public static int count() {
		return STEPS.size();
	}

	/** Step {@code index}, or null when out of range. */
	public static @Nullable Step step(int index) {
		return index >= 0 && index < STEPS.size() ? STEPS.get(index) : null;
	}

	/** The step with this id, or null. */
	public static @Nullable Step byId(String id) {
		return id == null ? null : BY_ID.get(id);
	}

	/** The bit index of {@code id}, or -1. */
	public static int indexOf(String id) {
		Step step = byId(id);
		return step == null ? -1 : step.index();
	}

	/** The chapters by save index. */
	public static List<Chapter> chapters() {
		return CHAPTERS;
	}

	/** The chapters in the order the book shows them ("Bab 1" first). */
	public static List<Chapter> displayOrder() {
		return DISPLAY;
	}

	/** Chapter {@code index} (0-based save index, clamped into range). */
	public static Chapter chapter(int index) {
		return CHAPTERS.get(Math.max(0, Math.min(CHAPTER_COUNT - 1, index)));
	}

	/**
	 * The current chapter (0-based save index; {@link #CHAPTER_COUNT} when every step is done): the first chapter in
	 * display order that is open, not yet past and still has a core step left. When none fits, the first open chapter with
	 * a core step left, then the first open chapter with any step left, then the first chapter with any step left. Manual
	 * and optional steps therefore never keep a player on an old chapter (integration audit T1).
	 */
	public static int currentChapter(BitSet done, Gate gate) {
		Predicate<Chapter> open = chapter -> safe(() -> gate.open(chapter));
		Predicate<Chapter> past = chapter -> safe(() -> gate.past(chapter));
		for (Chapter chapter : DISPLAY) {
			if (chapter.coreLeft(done) && open.test(chapter) && !past.test(chapter)) {
				return chapter.index();
			}
		}
		for (Chapter chapter : DISPLAY) {
			if (chapter.coreLeft(done) && open.test(chapter)) {
				return chapter.index();
			}
		}
		for (Chapter chapter : DISPLAY) {
			if (chapter.anyLeft(done) && open.test(chapter)) {
				return chapter.index();
			}
		}
		for (Chapter chapter : DISPLAY) {
			if (chapter.anyLeft(done)) {
				return chapter.index();
			}
		}
		return CHAPTER_COUNT;
	}

	private static boolean safe(java.util.function.BooleanSupplier test) {
		try {
			return test.getAsBoolean();
		} catch (RuntimeException e) {
			return false;
		}
	}

	private GuideChapters() {
	}
}
