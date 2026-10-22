package dev.kultivator.guide;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kitab Panduan (the in-game guide): the static list of its 16 chapters and their steps, shared by the server and the
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
 * </ul>
 * Check ids: {@code root}, {@code dao}, {@code cave}, {@code heart} (a Heart Demon defeated), {@code hall} (a Sect Hall
 * founded), {@code template}, {@code ascended} (Heaven's Acknowledgement), {@code bar_mortal}, {@code adv:<advancement>},
 * {@code rank:<realm>}, {@code at:<realm>:<layer 1-9>:<stage 1-4>}, {@code med:<minutes>}, {@code hall_level:<n>},
 * {@code disciples:<n>}, {@code tier:<n>} (template tier built), {@code stars:<n>}, {@code trib:<n>}.
 * <p>
 * Sumber: Terinspirasi dari bimbingan Yao Lao kepada Xiao Yan (Battle Through the Heavens). Isi bab adalah desain game;
 * setiap angka diambil dari kode dan docs/PANDUAN.md.
 */
public final class GuideChapters {

	/**
	 * Chapters in play order: Hari Pertama ... Tips & Kesalahan Umum, then (DESIGN 34.9) Dunia yang Luas and Ujung Dunia.
	 * Chapters 13 and 14 are appended after chapter 12; their texts live in {@code lang-src/dunia_panduan.*}. Chapters 15
	 * (Murid yang Hidup) and 16 (Sekte di Dunia) are Sekte yang Hidup's (DESIGN 36.3.6); their texts live in
	 * {@code lang-src/sekte_panduan.*} and their checks are the {@code sekte/} advancements of the ten modules.
	 */
	public static final int CHAPTER_COUNT = 16;

	/**
	 * One step. {@code chapter} is 0-based, {@code index} its place in {@link #steps()} (the bit of GuideSync),
	 * {@code hint} an optional command, {@code check} an optional automatic check id.
	 */
	public record Step(String id, int chapter, int index, @Nullable String hint, @Nullable String check) {

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
	}

	/** One chapter: its 0-based {@code index} and its steps (4-10) in order. */
	public record Chapter(int index, List<Step> steps) {

		/** 1-based, as shown to the player ("Bab 3"). */
		public int number() {
			return index + 1;
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
	}

	private static final List<Step> STEPS = new ArrayList<>();
	private static final List<Step> STEPS_VIEW = Collections.unmodifiableList(STEPS);
	private static final Map<String, Step> BY_ID = new HashMap<>();
	private static final List<Chapter> CHAPTERS;

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
		step(1, "formation3", null, "adv:qi_formation_heavenly");
		step(1, "peak", null, "at:qi_refining:1:4");
		// Duel Pedang (DESIGN 35.8.5): deflecting monsters, posture and the Deathblow
		step(1, "tangkis", null, "adv:duel_first_deflect");
		step(1, "postur", null, "adv:duel_first_deathblow");
		// 3. Pembangunan Fondasi
		step(2, "prep", null, null);
		step(2, "chance", null, null);
		step(2, "furnace", "/kultivasi resep", "adv:first_alchemy");
		step(2, "pills", null, null);
		step(2, "toxicity", null, null);
		step(2, "breakthrough", "/kultivasi breakthrough", "rank:foundation");
		step(2, "sword", null, "adv:flying_sword");
		step(2, "quality", null, null);
		// Duel Pedang (DESIGN 35.8.5): the first formal duel and the Perfect Deflect
		step(2, "duel", "/kultivasi duel tantang ", "adv:duel_first_win");
		step(2, "sempurna", null, "adv:duel_first_perfect");
		// 4. Inti Emas
		step(3, "trials", "/kultivasi jalan", "adv:foundation_trials");
		step(3, "trib_prep", null, null);
		step(3, "trib", "/kultivasi breakthrough", "rank:golden_core");
		step(3, "dao", "/kultivasi dao choose ", "dao");
		step(3, "comprehension", null, "adv:comprehension_minor");
		step(3, "codex", "/kultivasi teknik", "adv:dao_technique");
		step(3, "elite", null, "adv:first_elite");
		step(3, "heart_prep", null, null);
		// Duel Pedang (DESIGN 35.8.5): the Blade Lock and the perilous attacks
		step(3, "kunci", null, "adv:duel_bind_win");
		step(3, "bahaya", null, "adv:duel_mikiri");
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
		step(5, "hufa", "/kultivasi murid tahan", "adv:disciple_guardian");
		step(5, "disciple_gc", null, "adv:disciple_golden_core");
		step(5, "destroyer", "/kultivasi kehancuran sumpah", null);
		// Duel Pedang (DESIGN 35.8.5): watching two disciples duel
		step(5, "tonton", "/kultivasi duel murid ", "adv:duel_disciples_watched");
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
		// 8. Formasi Sekte
		step(7, "eye", null, null);
		step(7, "first", "/kultivasi formasi pola small_spirit_gathering", "adv:formation_eye");
		step(7, "power", "/kultivasi formasi setor semua", null);
		step(7, "keeper", "/kultivasi formasi penjaga ", "adv:formation_keeper");
		step(7, "grades", "/kultivasi formasi katalog", null);
		step(7, "b5", "/kultivasi formasi pola great_spirit_gathering", "adv:stone_formation");
		step(7, "seat", null, "tier:8");
		step(7, "a1", null, "adv:ten_thousand_immortals");
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
		step(8, "level9", null, "adv:hall_level_9");
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
		// 12. Tips & Kesalahan Umum
		step(11, "veins", null, null);
		step(11, "health", null, null);
		step(11, "flee", null, null);
		step(11, "pills", null, null);
		step(11, "presence", null, null);
		step(11, "stones", null, null);
		step(11, "kehancuran", "/kultivasi kehancuran", null);
		step(11, "hardcore", null, null);
		step(11, "help", "/kultivasi panduan", null);
		// Duel Pedang (DESIGN 35.8.5): the common duel mistakes
		step(11, "duel_tips", null, null);
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
		step(12, "kapal", null, "adv:dunia/ship_launched");
		// 14. Ujung Dunia (DESIGN 34.9)
		step(13, "saingan", null, "adv:dunia/rival_beaten");
		step(13, "menara", null, "adv:dunia/tower_10");
		step(13, "bawah", null, "adv:dunia/naihe_crossed");
		step(13, "yama", null, "adv:dunia/ten_courts");
		step(13, "naga", null, "adv:dunia/dragon_subdued");
		step(13, "evolusi", null, "adv:dunia/beast_tier_4");
		step(13, "laut", null, "adv:dunia/sea_arrived");
		step(13, "istana", null, "adv:dunia/dragon_king");
		step(13, "penjara", null, "adv:dunia/prison_sealed|adv:dunia/prison_slain");
		step(13, "kota_abadi", null, "adv:dunia/immortal_city");
		step(13, "pelayaran", null, "adv:dunia/ship_voyage");
		step(13, "bajak", null, "adv:dunia/ship_pirates");
		step(13, "puncak", null, "adv:dunia/tower_100");
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
		step(14, "penerus", null, "adv:sekte/tetua_penerus");
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
		step(15, "perang", null, "adv:sekte/diplomasi_menang");
		List<Chapter> chapters = new ArrayList<>(CHAPTER_COUNT);
		for (int chapter = 0; chapter < CHAPTER_COUNT; chapter++) {
			List<Step> own = new ArrayList<>();
			for (Step step : STEPS) {
				if (step.chapter() == chapter) {
					own.add(step);
				}
			}
			chapters.add(new Chapter(chapter, Collections.unmodifiableList(own)));
		}
		CHAPTERS = Collections.unmodifiableList(chapters);
	}

	private static void step(int chapter, String name, @Nullable String hint, @Nullable String check) {
		String id = "c" + (chapter + 1) + "." + name;
		Step step = new Step(id, chapter, STEPS.size(), hint, check);
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

	public static List<Chapter> chapters() {
		return CHAPTERS;
	}

	/** Chapter {@code index} (0-based, clamped to 0-13). */
	public static Chapter chapter(int index) {
		return CHAPTERS.get(Math.max(0, Math.min(CHAPTER_COUNT - 1, index)));
	}

	private GuideChapters() {
	}
}
