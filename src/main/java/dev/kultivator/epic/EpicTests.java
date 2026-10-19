package dev.kultivator.epic;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.path.DestroyerArts;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.SiteView;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The scripted test sequences of {@code uji momen} (DESIGN 23.15.1): every moment of the 23.13 storyboards played on the
 * player with the TEST flag at its real spacing, the whole tribulation, the showcase and the stop. Tests only send cues
 * and play world sounds: nothing is announced, written, damaged, ranked or carved.
 */
public final class EpicTests {

	static final String PREFIX = "kultivator.momen_server.";

	// Where a step's scene is centred (resolved once when the test starts, so it never jumps).
	private static final int AT_SELF = 0;
	private static final int AT_DISCIPLE = 1;
	private static final int AT_BOSS = 2;
	private static final int AT_RIGHT = 3;
	private static final int AT_SECT = 4;
	private static final int AT_ZONE = 5;
	private static final int AT_CRATER = 6;
	private static final int AT_AHEAD = 7;

	/** The 12 Immortal-grade ultimates of D3. */
	static final List<String> ABADI_SPECS = List.of("zhuxian_sword_formation", "nine_suns_heavens_fury", "ice_phoenix_descent",
			"tribulation_thunder_emperor_array", "cosmic_giant_form", "guanyin_sweet_dew", "life_death_domain", "stygian_tiger_seal", "void_mirror",
			"ninth_hex", "five_elements_mountain", "tai_chi_diagram");
	/** The 11 Divine-grade ultimates of D4. */
	static final List<String> DEWA_SPECS = List.of("heaven_punishing_sword", "emperor_flame", "freeze_mount_qi", "nine_heavens_thunder_venerable",
			"ancient_god_trample", "bodhisattva_tree", "kunpeng_art", "karma_domain", "time_spirit_domain", "stellar_black_hole", "heaven_trampling");
	static final List<String> QUALITIES = List.of("retak", "normal", "sempurna", "surgawi");
	static final List<String> BOSSES = List.of("warden", "wither", "naga", "penjaga", "warisan", "raja_siluman", "penjaga_alam", "tetua_iblis",
			"binatang_purba", "panglima_langit");
	static final List<String> SAVES = List.of("jiwa_tak_hancur", "rambut", "teratai", "pohon", "darah_abadi", "perlindungan_jiwa", "balas_budi",
			"pelarian_jiwa");
	static final List<String> TOURNAMENTS = List.of("internal", "elit", "seratus");
	static final List<String> GROUPS = List.of("tribulasi", "terobosan", "krisis", "kekuatan", "murid", "sekte", "tempur", "lain");
	/** Indonesian Dao names next to the ids the other commands use. */
	private static final List<String> DAO_ALIASES = List.of("pedang", "api", "es", "petir", "tubuh", "kehidupan", "bayangan", "ruang");
	/** Indonesian realm names next to {@code Realm.id()}. */
	static final List<String> REALM_ALIASES = List.of("manusia_fana", "pemurnian_qi", "fondasi", "inti_emas", "jiwa_baru", "transformasi_dewa",
			"penyatuan_dao", "kenaikan_abadi", "dewa_abadi");

	/** One catalogue row: the id, its group, the argument hint, the length in seconds and the showcase argument. */
	record Entry(String id, String group, String args, int seconds, @Nullable String showcaseArg) {
	}

	/** Every id in catalogue order (23.13). */
	static final Map<String, Entry> CATALOGUE = new LinkedHashMap<>();

	static {
		add("tribulasi", "tribulasi", "", 12, null);
		add("petir", "tribulasi", "", 8, null);
		add("petir_terakhir", "tribulasi", "", 6, null);
		add("tribulasi_berhasil", "tribulasi", "", 7, null);
		add("tribulasi_gagal", "tribulasi", "", 5, null);
		add("kebangkitan", "terobosan", "", 9, null);
		add("fondasi", "terobosan", "<retak|normal|sempurna|surgawi>", 10, "sempurna");
		add("inti_emas", "terobosan", "", 7, null);
		add("jiwa_baru", "terobosan", "", 7, null);
		add("transformasi_dewa", "terobosan", "", 7, null);
		add("penyatuan_dao", "terobosan", "[dao]", 7, null);
		add("kenaikan_abadi", "terobosan", "<1-9>", 7, "3");
		add("dewa_abadi", "terobosan", "", 7, null);
		add("pengakuan_langit", "terobosan", "", 35, null);
		add("surgawi", "terobosan", "", 7, null);
		add("penyimpangan", "krisis", "", 4, null);
		add("iblis_hati", "krisis", "", 14, null);
		add("wujud_dharma", "kekuatan", "", 4, null);
		add("wilayah_dao", "kekuatan", "<dao>", 4, "sword");
		add("jurus_abadi", "kekuatan", "<spec>", 5, "nine_suns_heavens_fury");
		add("jurus_dewa", "kekuatan", "<spec>", 6, "heaven_punishing_sword");
		add("kehancuran", "kekuatan", "", 6, null);
		add("pemusnah", "kekuatan", "<art>", 34, "xuantian");
		add("murid_terobosan", "murid", "", 18, null);
		add("bencana", "murid", "<1-7>", 34, "3");
		add("penginjak_langit", "murid", "", 7, null);
		add("lampu_jiwa", "murid", "", 4, null);
		add("melampaui_guru", "murid", "", 6, null);
		add("penerimaan", "sekte", "[perang]", 6, null);
		add("ujian_masuk", "sekte", "<1-9>", 7, "9");
		add("pengemis", "sekte", "", 5, null);
		add("nadi_dewa_sesat", "sekte", "", 5, null);
		add("darah_dou_di", "sekte", "", 5, null);
		add("turnamen", "sekte", "[internal|elit|seratus]", 10, null);
		add("sekte_tingkat", "sekte", "", 10, null);
		add("sekte_templat", "sekte", "", 20, null);
		add("istana_langit", "sekte", "", 20, null);
		add("aula", "sekte", "", 4, null);
		add("bos", "tempur", "<warden|wither|naga|penjaga|warisan|raja_siluman|penjaga_alam|tetua_iblis|binatang_purba|panglima_langit>", 5, "warden");
		add("lolos_maut", "tempur", "<jiwa_tak_hancur|rambut|teratai|pohon|darah_abadi|perlindungan_jiwa|balas_budi|pelarian_jiwa>", 4,
				"jiwa_tak_hancur");
		add("akar", "lain", "", 8, null);
		add("pencerahan", "lain", "", 10, null);
		add("binatang", "lain", "", 5, null);
		add("pewaris", "lain", "", 4, null);
		add("cincin_guru", "lain", "", 4, null);
		add("guru_turun", "lain", "", 4, null);
		add("api_surgawi", "lain", "", 4, null);
		add("batas_suci", "lain", "", 4, null);
	}

	private static void add(String id, String group, String args, int seconds, @Nullable String showcaseArg) {
		CATALOGUE.put(id, new Entry(id, group, args, seconds, showcaseArg));
	}

	/** One cue of a script, {@code delay} ticks after the start. */
	private record Step(int delay, int moment, int beat, int a, int b, float f, int flags, int at) {
	}

	/** A built script: its steps, the scene scale and the length in ticks. */
	private record Script(List<Step> steps, int s, int length) {
	}

	/** The places of a running test. */
	private record Places(Vec3 self, @Nullable DiscipleEntity disciple, Vec3 discipleAt, Vec3 boss, Vec3 right, Vec3 sect, float sectYaw,
						  Vec3 zone, Vec3 crater, Vec3 ahead) {
	}

	private static final class Run {
		final UUID player;
		final List<Step> steps;
		final long start;
		final long end;
		final int s;
		final Places places;
		final Set<Integer> moments = new HashSet<>();
		int next;

		Run(UUID player, List<Step> steps, long start, long end, int s, Places places) {
			this.player = player;
			this.steps = steps;
			this.start = start;
			this.end = end;
			this.s = s;
			this.places = places;
		}
	}

	private static final class Show {
		final List<String> ids;
		final int gap;
		int index;
		long nextAt;

		Show(List<String> ids, int gap, long nextAt) {
			this.ids = ids;
			this.gap = gap;
			this.nextAt = nextAt;
		}
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	private static final Map<UUID, Show> SHOWS = new HashMap<>();

	// ================================================================== commands

	/** {@code uji momen <id> [arg] [skala n] [beat n]}: 1 when the test started. */
	static int play(ServerPlayer player, String id, String options) {
		if (!enabled(player)) {
			return 0;
		}
		SHOWS.remove(player.getUUID());
		Options opts = Options.parse(options);
		if (opts == null) {
			Msg.chat(player, Component.translatable(PREFIX + "test.bad_options", options).withStyle(ChatFormatting.RED));
			return 0;
		}
		return start(player, id, opts, true) ? 1 : 0;
	}

	/** {@code uji momen tribulasi_penuh <ranah> [lapis] [gagal]}. */
	static int fullTribulation(ServerPlayer player, String realmId, String options) {
		if (!enabled(player)) {
			return 0;
		}
		Realm realm = realm(realmId);
		if (realm == null) {
			Msg.chat(player, Component.translatable(PREFIX + "test.bad_realm", realmId).withStyle(ChatFormatting.RED));
			return 0;
		}
		int layer = 1;
		boolean fail = false;
		for (String token : options.trim().split("\\s+")) {
			if (token.isEmpty()) {
				continue;
			}
			if (token.equals("gagal")) {
				fail = true;
			} else {
				Integer value = number(token);
				if (value == null || value < 1 || value > 9) {
					Msg.chat(player, Component.translatable(PREFIX + "test.bad_options", options).withStyle(ChatFormatting.RED));
					return 0;
				}
				layer = value;
			}
		}
		SHOWS.remove(player.getUUID());
		Rank target = new Rank(realm, layer - 1, 0);
		Rank strength = realm.atLeast(Realm.GOLDEN_CORE) ? target : new Rank(Realm.GOLDEN_CORE, 0, 0);
		int total = Tribulation.strikeCount(strength);
		int variant = Tribulation.variant(player.level());
		List<Step> steps = new ArrayList<>();
		steps.add(step(0, EpicIds.BREAKTHROUGH, EpicIds.BT_BEGIN, EpicIds.packBreakthrough(realm.ordinal(), 0, EpicIds.KIND_TRIBULATION, strength.layer()), 0, 0));
		steps.add(step(0, EpicIds.TRIBULATION, EpicIds.TRIB_OMEN, EpicIds.packOmen(variant, strength.realm().ordinal(), strength.layer()), total, 0));
		int last = Tribulation.OMEN_TICKS;
		for (int i = 1; i <= total; i++) {
			int strikeAt = Tribulation.OMEN_TICKS + (i - 1) * Tribulation.STRIKE_INTERVAL;
			if (i == total && total > 1) {
				steps.add(step(strikeAt - EpicIds.HUSH_LEAD, EpicIds.TRIBULATION, EpicIds.TRIB_HUSH, total, total, 0));
			}
			steps.add(step(strikeAt - EpicIds.PRE_LEAD, EpicIds.TRIBULATION, EpicIds.TRIB_PRE, i, total, 0));
			steps.add(step(strikeAt, EpicIds.TRIBULATION, EpicIds.TRIB_STRIKE, i, total, i == total ? EpicIds.FLAG_FINAL : 0));
			last = strikeAt;
		}
		if (fail) {
			steps.add(step(last + 2, EpicIds.TRIBULATION, EpicIds.TRIB_END_FAIL, total, 0, EpicIds.FLAG_FINAL));
			steps.add(step(last + 2, EpicIds.BREAKTHROUGH, EpicIds.BT_FAIL, 0, 0, EpicIds.FLAG_FINAL));
		} else {
			steps.add(step(last + 30, EpicIds.TRIBULATION, EpicIds.TRIB_END_OK, total, 0, EpicIds.FLAG_FINAL));
			steps.add(step(last + 30, EpicIds.BREAKTHROUGH, EpicIds.BT_SUCCESS,
					EpicIds.packBreakthrough(realm.ordinal(), 1, EpicIds.KIND_TRIBULATION, strength.layer()), 0, EpicIds.FLAG_FINAL));
		}
		steps.sort((x, y) -> Integer.compare(x.delay(), y.delay()));
		int length = last + 30 + 140;
		begin(player, new Script(steps, EpicScale.of(strength), length));
		Msg.chat(player, Component.translatable(PREFIX + "test.full", strength.displayName(), total, (length + 19) / 20).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	/** {@code uji momen semua [kelompok] [jeda n]}. */
	static int showcase(ServerPlayer player, String options) {
		if (!enabled(player)) {
			return 0;
		}
		String group = null;
		int gap = 3;
		String[] tokens = options.trim().split("\\s+");
		for (int i = 0; i < tokens.length; i++) {
			String token = tokens[i];
			if (token.isEmpty()) {
				continue;
			}
			if (token.equals("jeda") && i + 1 < tokens.length) {
				Integer value = number(tokens[++i]);
				if (value == null || value < 1 || value > 10) {
					Msg.chat(player, Component.translatable(PREFIX + "test.bad_options", options).withStyle(ChatFormatting.RED));
					return 0;
				}
				gap = value;
			} else if (GROUPS.contains(token)) {
				group = token;
			} else {
				Msg.chat(player, Component.translatable(PREFIX + "showcase.bad_group", token).withStyle(ChatFormatting.RED));
				return 0;
			}
		}
		List<String> ids = new ArrayList<>();
		for (Entry entry : CATALOGUE.values()) {
			if (group == null || entry.group().equals(group)) {
				ids.add(entry.id());
			}
		}
		stopQuietly(player.getUUID());
		SHOWS.put(player.getUUID(), new Show(ids, gap, now(player)));
		Msg.chat(player, Component.translatable(PREFIX + "showcase.start", ids.size(), gap).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	/** {@code uji momen berhenti}: stops the test scene and the showcase; clients restore at once. */
	static int stop(ServerPlayer player) {
		Run run = RUNS.remove(player.getUUID());
		boolean show = SHOWS.remove(player.getUUID()) != null;
		EpicHooks.cancelTimers(player.getUUID(), EpicHooks.TAG_TEST);
		if (run == null && !show) {
			Msg.chat(player, Component.translatable(PREFIX + "test.nothing").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (run != null) {
			release(player, run);
		}
		Msg.chat(player, Component.translatable(PREFIX + "test.stopped").withStyle(ChatFormatting.AQUA));
		return 1;
	}

	/** {@code uji momen daftar}: every id with its group, arguments and length. */
	static int list(ServerPlayer player) {
		Msg.chat(player, Component.translatable(PREFIX + "list.header", CATALOGUE.size()).withStyle(ChatFormatting.GOLD));
		for (Entry entry : CATALOGUE.values()) {
			Msg.chat(player, Component.translatable(PREFIX + "list.line", Component.literal(entry.id()).withStyle(ChatFormatting.YELLOW),
					Component.translatable(PREFIX + "group." + entry.group()), entry.args().isEmpty() ? "-" : entry.args(), entry.seconds(),
					Component.translatable(PREFIX + "name." + entry.id())).withStyle(ChatFormatting.GRAY));
		}
		Msg.chat(player, Component.translatable(PREFIX + "list.footer").withStyle(ChatFormatting.DARK_AQUA));
		return CATALOGUE.size();
	}

	// ================================================================== ticking

	static void tick(MinecraftServer server) {
		if (RUNS.isEmpty() && SHOWS.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (Run run : List.copyOf(RUNS.values())) {
			ServerPlayer player = server.getPlayerList().getPlayer(run.player);
			if (player == null) {
				RUNS.remove(run.player);
				continue;
			}
			while (run.next < run.steps.size() && run.start + run.steps.get(run.next).delay() <= now) {
				fire(player, run, run.steps.get(run.next++), now);
			}
			if (run.next >= run.steps.size() && now >= run.end) {
				RUNS.remove(run.player, run);
			}
		}
		for (Map.Entry<UUID, Show> entry : List.copyOf(SHOWS.entrySet())) {
			Show show = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				SHOWS.remove(entry.getKey());
				continue;
			}
			if (RUNS.containsKey(entry.getKey()) || now < show.nextAt) {
				continue;
			}
			if (show.index >= show.ids.size()) {
				SHOWS.remove(entry.getKey());
				Msg.chat(player, Component.translatable(PREFIX + "showcase.done").withStyle(ChatFormatting.GOLD));
				continue;
			}
			String id = show.ids.get(show.index++);
			Entry catalogued = CATALOGUE.get(id);
			Msg.chat(player, Component.translatable(PREFIX + "showcase.step", show.index, show.ids.size(), id,
					Component.translatable(PREFIX + "name." + id)).withStyle(ChatFormatting.YELLOW));
			Options opts = new Options(catalogued == null ? null : catalogued.showcaseArg(), -1, -1);
			start(player, id, opts, false);
			show.nextAt = now + show.gap * 20L;
		}
	}

	/** The player left: drop the test and the showcase without messages. */
	static void stopQuietly(UUID player) {
		RUNS.remove(player);
		SHOWS.remove(player);
	}

	static void clear() {
		RUNS.clear();
		SHOWS.clear();
	}

	// ================================================================== internals

	/** Parsed {@code [arg] [skala n] [beat n]}. */
	private record Options(@Nullable String arg, int scale, int beat) {
		static @Nullable Options parse(String text) {
			String arg = null;
			int scale = -1;
			int beat = -1;
			String[] tokens = text.trim().split("\\s+");
			for (int i = 0; i < tokens.length; i++) {
				String token = tokens[i].toLowerCase(Locale.ROOT);
				if (token.isEmpty()) {
					continue;
				}
				if ((token.equals("skala") || token.equals("beat")) && i + 1 < tokens.length) {
					Integer value = number(tokens[++i]);
					if (value == null) {
						return null;
					}
					if (token.equals("skala")) {
						if (value < 0 || value > EpicIds.SCALE_MAX) {
							return null;
						}
						scale = value;
					} else {
						if (value < 0 || value > 15) {
							return null;
						}
						beat = value;
					}
				} else if (arg == null) {
					arg = token;
				} else {
					return null;
				}
			}
			return new Options(arg, scale, beat);
		}
	}

	private static boolean start(ServerPlayer player, String id, Options opts, boolean announce) {
		Entry entry = CATALOGUE.get(id);
		if (entry == null) {
			Msg.chat(player, Component.translatable(PREFIX + "test.unknown", id).withStyle(ChatFormatting.RED));
			return false;
		}
		Script script = build(player, id, opts.arg());
		if (script == null) {
			Msg.chat(player, Component.translatable(PREFIX + "test.bad_arg", id, entry.args()).withStyle(ChatFormatting.RED));
			return false;
		}
		List<Step> steps = script.steps();
		int length = Math.max(script.length(), entry.seconds() * 20);
		if (opts.beat() >= 0) {
			Step only = null;
			for (Step step : steps) {
				if (step.beat() == opts.beat()) {
					only = step;
					break;
				}
			}
			if (only == null) {
				Msg.chat(player, Component.translatable(PREFIX + "test.no_beat", id, opts.beat()).withStyle(ChatFormatting.RED));
				return false;
			}
			steps = List.of(new Step(0, only.moment(), only.beat(), only.a(), only.b(), only.f(), only.flags(), only.at()));
			length = 100;
		}
		begin(player, new Script(steps, opts.scale() >= 0 ? opts.scale() : script.s(), length));
		if (announce) {
			Msg.chat(player, Component.translatable(PREFIX + "test.start", Component.translatable(PREFIX + "name." + id)).withStyle(ChatFormatting.GOLD));
		}
		return true;
	}

	private static void begin(ServerPlayer player, Script script) {
		Run old = RUNS.remove(player.getUUID());
		if (old != null) {
			release(player, old);
		}
		long now = now(player);
		RUNS.put(player.getUUID(), new Run(player.getUUID(), script.steps(), now, now + script.length(), script.s(), places(player)));
	}

	private static void fire(ServerPlayer player, Run run, Step step, long now) {
		ServerLevel level = player.level();
		Places places = run.places;
		Entity subject;
		Vec3 pos;
		switch (step.at()) {
			case AT_DISCIPLE -> {
				subject = places.disciple() != null && !places.disciple().isRemoved() ? places.disciple() : null;
				pos = subject != null ? subject.position() : places.discipleAt();
			}
			case AT_BOSS -> {
				subject = null;
				pos = places.boss();
			}
			case AT_RIGHT -> {
				subject = player;
				pos = places.right();
			}
			case AT_SECT -> {
				subject = null;
				pos = places.sect();
			}
			case AT_ZONE -> {
				subject = places.disciple() != null && !places.disciple().isRemoved() ? places.disciple() : null;
				pos = places.zone();
			}
			case AT_CRATER -> {
				subject = player;
				pos = places.crater();
			}
			case AT_AHEAD -> {
				subject = null;
				pos = places.ahead();
			}
			default -> {
				subject = player;
				pos = places.self();
			}
		}
		float f = step.moment() == EpicIds.SECT ? places.sectYaw() : step.f();
		run.moments.add(step.moment());
		EpicHooks.send(level, step.moment(), step.beat(), subject, player, pos, now, step.a(), step.b(), f, run.s, step.flags() | EpicIds.FLAG_TEST);
	}

	/** Ends the long scenes of a stopped test at once (ABORT, STOP, CANCEL), so clients restore sky, sound and camera. */
	private static void release(ServerPlayer player, Run run) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		int flags = EpicIds.FLAG_TEST | EpicIds.FLAG_FINAL;
		Places places = run.places;
		Entity disciple = places.disciple() != null && !places.disciple().isRemoved() ? places.disciple() : null;
		if (run.moments.contains(EpicIds.TRIBULATION)) {
			EpicMoments.send(level, EpicIds.TRIBULATION, EpicIds.TRIB_ABORT, player, player, places.self(), now, 0, 0, 0.0F, run.s, flags);
		}
		if (run.moments.contains(EpicIds.ASCENSION)) {
			EpicMoments.send(level, EpicIds.ASCENSION, EpicIds.ASC_STOP, player, player, places.self(), now, 0, 0, 0.0F, run.s, flags);
		}
		if (run.moments.contains(EpicIds.KEHANCURAN)) {
			EpicMoments.send(level, EpicIds.KEHANCURAN, EpicIds.KEH_ABORT, disciple, player, places.zone(), now, 0, 0, 0.0F, run.s, flags);
		}
		if (run.moments.contains(EpicIds.CALAMITY)) {
			EpicMoments.send(level, EpicIds.CALAMITY, EpicIds.CAL_ABORT, disciple, player, disciple != null ? disciple.position() : places.discipleAt(),
					now, 0, 0, 0.0F, run.s, flags);
		}
		if (run.moments.contains(EpicIds.DISCIPLE_BT)) {
			EpicMoments.send(level, EpicIds.DISCIPLE_BT, EpicIds.DBT_CANCEL, disciple, player, disciple != null ? disciple.position() : places.discipleAt(),
					now, 0, 0, 0.0F, run.s, flags);
		}
		// Every other moment (Heart Demon, ultimates, sect, ...) has no stop beat: the tester's own client ends all scenes
		// at once and restores sky, sound, music and camera.
		Vec3 self = player.position();
		dev.kultivator.network.Net.send(player, new dev.kultivator.network.Payloads.EpicMoment(EpicIds.EXTRA, EpicIds.BEAT_STOP_ALL, -1, player.getId(),
				self.x, self.y, self.z, now, 0, 0, 0, 0.0F, 0, flags));
	}

	private static Places places(ServerPlayer player) {
		Vec3 self = player.position();
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
		Vec3 right = new Vec3(-flat.z, 0.0D, flat.x);
		DiscipleEntity disciple = null;
		double best = 32.0D * 32.0D;
		for (DiscipleEntity candidate : Disciples.loaded(player.getUUID())) {
			double distance = candidate.distanceToSqr(player);
			if (candidate.level() == player.level() && candidate.isAlive() && distance <= best) {
				best = distance;
				disciple = candidate;
			}
		}
		Vec3 sect = self.add(flat.scale(40.0D));
		float sectYaw = yaw(sect, self);
		SiteView site = EngineApi.get().site(player.getUUID());
		if (site != null && site.dim().equals(player.level().dimension())) {
			BlockPos main = EngineApi.get().anchor(player.getUUID(), "main_hall");
			sect = Vec3.atBottomCenterOf(main != null ? main : site.stele()).add(0.0D, 1.0D, 0.0D);
			sectYaw = yaw(sect, Vec3.atBottomCenterOf(site.stele()));
		}
		return new Places(self, disciple, self.add(flat.scale(6.0D)), self.add(flat.scale(8.0D)), self.add(right.scale(6.0D)), sect, sectYaw,
				self.add(flat.scale(30.0D)), self.add(flat.scale(12.0D)), self.add(flat.scale(4.0D)));
	}

	/** Yaw (degrees, Minecraft convention) from {@code from} toward {@code to}. */
	private static float yaw(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

	/** The script of one id, or null when its argument is missing or wrong. */
	private static @Nullable Script build(ServerPlayer player, String id, @Nullable String arg) {
		List<Step> steps = new ArrayList<>();
		ServerLevel level = player.level();
		int variant = Tribulation.variant(level);
		int gc = Realm.GOLDEN_CORE.ordinal();
		int s = 2;
		switch (id) {
			case "tribulasi" -> {
				steps.add(step(0, EpicIds.TRIBULATION, EpicIds.TRIB_OMEN, EpicIds.packOmen(variant, gc, 0), 3, 0));
				steps.add(step(Tribulation.OMEN_TICKS, EpicIds.TRIBULATION, EpicIds.TRIB_ABORT, 0, 0, EpicIds.FLAG_FINAL));
			}
			case "petir" -> {
				steps.add(step(0, EpicIds.TRIBULATION, EpicIds.TRIB_PRE, 1, 3, 0));
				steps.add(step(30, EpicIds.TRIBULATION, EpicIds.TRIB_STRIKE, 1, 3, 0));
				steps.add(step(70, EpicIds.TRIBULATION, EpicIds.TRIB_PRE, 2, 3, 0));
				steps.add(step(100, EpicIds.TRIBULATION, EpicIds.TRIB_STRIKE, 2, 3, 0));
				steps.add(step(150, EpicIds.TRIBULATION, EpicIds.TRIB_ABORT, 2, 0, EpicIds.FLAG_FINAL));
			}
			case "petir_terakhir" -> {
				steps.add(step(0, EpicIds.TRIBULATION, EpicIds.TRIB_HUSH, 3, 3, 0));
				steps.add(step(30, EpicIds.TRIBULATION, EpicIds.TRIB_PRE, 3, 3, 0));
				steps.add(step(60, EpicIds.TRIBULATION, EpicIds.TRIB_STRIKE, 3, 3, EpicIds.FLAG_FINAL));
				steps.add(step(100, EpicIds.TRIBULATION, EpicIds.TRIB_ABORT, 3, 0, EpicIds.FLAG_FINAL));
			}
			case "tribulasi_berhasil" -> success(steps, Realm.GOLDEN_CORE, 0, 1, 0);
			case "tribulasi_gagal" -> {
				steps.add(step(0, EpicIds.TRIBULATION, EpicIds.TRIB_END_FAIL, 2, 0, EpicIds.FLAG_FINAL));
				steps.add(step(0, EpicIds.BREAKTHROUGH, EpicIds.BT_FAIL, 0, 0, EpicIds.FLAG_FINAL));
			}
			case "kebangkitan" -> {
				int qi = Realm.QI_REFINING.ordinal();
				steps.add(step(0, EpicIds.BREAKTHROUGH, EpicIds.BT_BEGIN, EpicIds.packBreakthrough(qi, 0, EpicIds.KIND_AWAKENING, 0), 0, 0));
				steps.add(step(120, EpicIds.BREAKTHROUGH, EpicIds.BT_SUCCESS, EpicIds.packBreakthrough(qi, 1, EpicIds.KIND_AWAKENING, 0), 0,
						EpicIds.FLAG_FINAL));
				s = 0;
			}
			case "fondasi" -> {
				int quality = arg == null ? -1 : QUALITIES.indexOf(arg);
				if (quality < 0) {
					return null;
				}
				int foundation = Realm.FOUNDATION.ordinal();
				steps.add(step(0, EpicIds.BREAKTHROUGH, EpicIds.BT_BEGIN, EpicIds.packBreakthrough(foundation, 0, EpicIds.KIND_CHANCE, 0), 0, 0));
				steps.add(step(100, EpicIds.BREAKTHROUGH, EpicIds.BT_SUCCESS, EpicIds.packBreakthrough(foundation, quality, EpicIds.KIND_CHANCE, 0), 0,
						EpicIds.FLAG_FINAL));
				s = 1;
			}
			case "inti_emas" -> s = success(steps, Realm.GOLDEN_CORE, 0, 1, 0);
			case "jiwa_baru" -> s = success(steps, Realm.NASCENT_SOUL, 0, 1, 0);
			case "transformasi_dewa" -> s = success(steps, Realm.DEITY_TRANSFORMATION, 0, 1, 0);
			case "penyatuan_dao" -> {
				Dao dao = arg == null ? null : dao(arg);
				if (arg != null && dao == null) {
					return null;
				}
				// b = Dao ordinal + 1 (0 = the player's own Dao).
				s = success(steps, Realm.DAO_UNION, 0, 1, dao == null ? 0 : dao.ordinal() + 1);
			}
			case "kenaikan_abadi" -> {
				Integer layer = arg == null ? null : number(arg);
				if (layer == null || layer < 1 || layer > 9) {
					return null;
				}
				s = success(steps, Realm.IMMORTAL_ASCENSION, layer - 1, 1, 0);
			}
			case "dewa_abadi" -> s = success(steps, Realm.TRUE_IMMORTAL, 0, 1, 0);
			case "surgawi" -> s = success(steps, Realm.GOLDEN_CORE, 0, 3, 0);
			case "pengakuan_langit" -> {
				steps.add(step(0, EpicIds.ASCENSION, EpicIds.ASC_DARKEN, 0, 0, 0));
				steps.add(step(100, EpicIds.ASCENSION, EpicIds.ASC_PILLAR, 100, 0, 0));
				steps.add(step(240, EpicIds.ASCENSION, EpicIds.ASC_HEAVENS, 240, 0, 0));
				steps.add(step(360, EpicIds.ASCENSION, EpicIds.ASC_VOICE, 360, 0, 0));
				steps.add(step(600, EpicIds.ASCENSION, EpicIds.ASC_FINALE, 600, 0, EpicIds.FLAG_FINAL));
				s = 9;
			}
			case "penyimpangan" -> steps.add(step(0, EpicIds.DEVIATION, EpicIds.SINGLE, 0, 0, EpicIds.FLAG_FINAL));
			case "iblis_hati" -> {
				steps.add(step(0, EpicIds.HEART_DEMON, EpicIds.HD_WARNING, 0, 0, 0));
				steps.add(new Step(60, EpicIds.HEART_DEMON, EpicIds.HD_APPEAR, 0, 0, 0.0F, 0, AT_AHEAD));
				steps.add(new Step(200, EpicIds.HEART_DEMON, EpicIds.HD_WON, 0, 0, 0.0F, EpicIds.FLAG_FINAL, AT_AHEAD));
			}
			case "wujud_dharma" -> steps.add(step(0, EpicIds.DHARMA, EpicIds.SINGLE, 0, 0, EpicIds.FLAG_FINAL));
			case "wilayah_dao" -> {
				Dao dao = arg == null ? null : dao(arg);
				if (dao == null) {
					return null;
				}
				steps.add(step(0, EpicIds.DOMAIN, EpicIds.SINGLE, dao.ordinal(), 0, EpicIds.FLAG_FINAL));
				s = 5;
			}
			case "jurus_abadi", "jurus_dewa" -> {
				List<String> allowed = id.equals("jurus_abadi") ? ABADI_SPECS : DEWA_SPECS;
				TechniqueSpec spec = arg == null || !allowed.contains(arg) ? null : TechniqueRegistry.byId(arg);
				if (spec == null) {
					return null;
				}
				steps.add(step(0, EpicIds.TECH_ULTIMATE, EpicIds.TECH_CAST, spec.netId(), 0, 0));
				steps.add(step(30, EpicIds.TECH_ULTIMATE, EpicIds.TECH_CLIMAX, spec.netId(), 0, 0));
				steps.add(step(80, EpicIds.TECH_ULTIMATE, EpicIds.TECH_END, spec.netId(), 0, EpicIds.FLAG_FINAL));
				s = id.equals("jurus_abadi") ? 6 : 9;
			}
			case "kehancuran" -> {
				steps.add(step(0, EpicIds.KEHANCURAN_SELF, EpicIds.SINGLE, 0, 0, 0));
				steps.add(new Step(40, EpicIds.KEHANCURAN, EpicIds.KEH_CRATER, -1, 600, 6.0F, EpicIds.FLAG_FINAL, AT_CRATER));
				s = 5;
			}
			case "pemusnah" -> {
				DestroyerArts.Art art = arg == null ? null : art(arg);
				if (art == null) {
					return null;
				}
				int seconds = KultivatorConfig.get().kehancuranWarningSeconds;
				steps.add(new Step(0, EpicIds.KEHANCURAN, EpicIds.KEH_WARNING, art.ordinal(), seconds, 8.0F, 0, AT_ZONE));
				steps.add(new Step(seconds * 20, EpicIds.KEHANCURAN, EpicIds.KEH_IMPACT, art.ordinal(), 0, 8.0F, EpicIds.FLAG_FINAL, AT_ZONE));
				s = 9;
			}
			case "murid_terobosan" -> {
				steps.add(disciple(0, EpicIds.DISCIPLE_BT, EpicIds.DBT_START, EpicIds.packBreakthrough(gc, 0, EpicIds.KIND_TRIBULATION, 0), 0, 0));
				for (int i = 1; i <= 3; i++) {
					steps.add(disciple(60 + 60 * i, EpicIds.DISCIPLE_BT, EpicIds.DBT_STRIKE, i, 3, i == 3 ? EpicIds.FLAG_FINAL : 0));
				}
				steps.add(disciple(270, EpicIds.DISCIPLE_BT, EpicIds.DBT_END_OK, EpicIds.packBreakthrough(gc, 1, EpicIds.KIND_TRIBULATION, 0), 0,
						EpicIds.FLAG_FINAL));
			}
			case "bencana" -> {
				Integer k = arg == null ? null : number(arg);
				if (k == null || k < 1 || k > 7) {
					return null;
				}
				int total = 6 + k;
				steps.add(disciple(0, EpicIds.CALAMITY, EpicIds.CAL_OMEN, k, EpicIds.packCalamity(0, total), 0));
				int strikeAt = 0;
				for (int i = 1; i <= total; i++) {
					strikeAt = 120 + (i - 1) * 60;
					steps.add(disciple(strikeAt - 30, EpicIds.CALAMITY, EpicIds.CAL_PRE, k, EpicIds.packCalamity(i, total), 0));
					steps.add(disciple(strikeAt, EpicIds.CALAMITY, EpicIds.CAL_STRIKE, k, EpicIds.packCalamity(i, total), i == total ? EpicIds.FLAG_FINAL : 0));
				}
				steps.add(disciple(strikeAt + 30, EpicIds.CALAMITY, EpicIds.CAL_PASS, k, EpicIds.packCalamity(total, total), EpicIds.FLAG_FINAL));
				s = EpicIds.SCALE_MAX;
			}
			case "penginjak_langit" -> {
				steps.add(disciple(0, EpicIds.CALAMITY, EpicIds.CAL_TRAMPLING, 7, 0, EpicIds.FLAG_FINAL));
				s = EpicIds.SCALE_MAX;
			}
			case "lampu_jiwa" -> {
				steps.add(disciple(0, EpicIds.CALAMITY, EpicIds.CAL_LAMP, 8, 0, EpicIds.FLAG_FINAL));
				s = EpicIds.SCALE_MAX;
			}
			case "melampaui_guru" -> steps.add(disciple(0, EpicIds.SURPASS, EpicIds.SURPASS_TRIGGER, 0, 0, EpicIds.FLAG_FINAL));
			case "penerimaan" -> {
				boolean war = "perang".equals(arg);
				if (arg != null && !war) {
					return null;
				}
				steps.add(new Step(0, EpicIds.RECRUIT, EpicIds.RECRUIT_ARRIVAL, war ? 8 : 4, war ? 1 : 0, 0.0F, 0, AT_SECT));
				s = 1;
			}
			case "ujian_masuk" -> {
				Integer duan = arg == null ? null : number(arg);
				if (duan == null || duan < 1 || duan > 9) {
					return null;
				}
				// Soul colours red to indigo (no purple): Duan 1-7 one each, 8 and 9 stay indigo.
				int colour = Math.min(6, duan - 1);
				steps.add(new Step(0, EpicIds.RECRUIT, EpicIds.RECRUIT_TEST, EpicIds.packRecruitTest(duan, colour, duan >= 9 ? 1 : 0), duan, 0.0F,
						EpicIds.FLAG_FINAL, AT_SECT));
				s = 1;
			}
			case "pengemis" -> reveal(steps, Realm.FOUNDATION, EpicHooks.REVEAL_BEGGAR);
			case "nadi_dewa_sesat" -> reveal(steps, Realm.GOLDEN_CORE, EpicHooks.REVEAL_HERETIC_VEINS);
			case "darah_dou_di" -> reveal(steps, Realm.DAO_UNION, EpicHooks.REVEAL_DOU_DI);
			case "turnamen" -> {
				int kind = arg == null ? 0 : TOURNAMENTS.indexOf(arg);
				if (kind < 0) {
					return null;
				}
				steps.add(new Step(0, EpicIds.TOURNAMENT, EpicIds.TOUR_INTRO, kind, -1, 0.0F, 0, AT_RIGHT));
				steps.add(new Step(100, EpicIds.TOURNAMENT, EpicIds.TOUR_WINNER, kind, -1, 0.0F, EpicIds.FLAG_FINAL, AT_RIGHT));
				s = 3;
			}
			case "sekte_tingkat" -> {
				steps.add(new Step(0, EpicIds.SECT, EpicIds.SECT_TIER, 3, template(player, false), 0.0F, 0, AT_SECT));
				s = 1;
			}
			case "sekte_templat", "istana_langit" -> {
				int template = id.equals("istana_langit") ? 9 : template(player, true);
				steps.add(new Step(0, EpicIds.SECT, EpicIds.SECT_TIER, 9, template, 0.0F, 0, AT_SECT));
				steps.add(new Step(0, EpicIds.SECT, EpicIds.SECT_TEMPLATE, 9, template, 0.0F, EpicIds.FLAG_FINAL, AT_SECT));
				s = template >= 9 ? 9 : 5;
			}
			case "aula" -> {
				steps.add(new Step(0, EpicIds.SECT, EpicIds.SECT_HALL_LEVEL, 5, 0, 0.0F, EpicIds.FLAG_FINAL, AT_SECT));
				s = 1;
			}
			case "bos" -> {
				int kind = arg == null ? -1 : BOSSES.indexOf(arg);
				if (kind < 0) {
					return null;
				}
				steps.add(new Step(0, EpicIds.BOSS, EpicIds.BOSS_KILL, kind, EpicHooks.flameFor(kind), 0.0F, EpicIds.FLAG_FINAL, AT_BOSS));
				s = 4;
			}
			case "lolos_maut" -> {
				int kind = arg == null ? -1 : SAVES.indexOf(arg);
				if (kind < 0) {
					return null;
				}
				steps.add(step(0, EpicIds.CHEAT_DEATH, EpicIds.CHEAT_SAVED, kind, 0, EpicIds.FLAG_FINAL));
			}
			case "akar" -> {
				CultivationData data = Cultivators.get(player);
				steps.add(step(0, EpicIds.EXTRA, EpicIds.EXTRA_ROOT_TEST, data.rootGrade().ordinal(), data.rootElements(), EpicIds.FLAG_FINAL));
				s = 0;
			}
			case "pencerahan" -> steps.add(step(0, EpicIds.ENLIGHTEN, EpicIds.SINGLE, 0, 0, EpicIds.FLAG_FINAL));
			case "binatang" -> steps.add(new Step(0, EpicIds.EXTRA, EpicIds.EXTRA_BEAST_TIER3, 0, 3, 0.0F, EpicIds.FLAG_FINAL, AT_AHEAD));
			case "pewaris" -> steps.add(disciple(0, EpicIds.EXTRA, EpicIds.EXTRA_HEIR, 1, 0, EpicIds.FLAG_FINAL));
			case "cincin_guru" -> steps.add(disciple(0, EpicIds.EXTRA, EpicIds.EXTRA_RING_BOND, 0, 0, EpicIds.FLAG_FINAL));
			case "guru_turun" -> steps.add(disciple(0, EpicIds.EXTRA, EpicIds.EXTRA_MASTER_DESCENDS, Cultivators.get(player).realm().color(), 0,
					EpicIds.FLAG_FINAL));
			case "api_surgawi" -> steps.add(disciple(0, EpicIds.EXTRA, EpicIds.EXTRA_FLAME_ABSORBED, 0, 0, EpicIds.FLAG_FINAL));
			case "batas_suci" -> steps.add(disciple(0, EpicIds.EXTRA, EpicIds.EXTRA_VOID_BORDER, 50, 0, EpicIds.FLAG_FINAL));
			default -> {
				return null;
			}
		}
		int last = 0;
		for (Step step : steps) {
			last = Math.max(last, step.delay());
		}
		return new Script(steps, s, last + 100);
	}

	/** A tribulation success with its realm layer: END_OK and BREAKTHROUGH SUCCESS in the same tick. Returns S. */
	private static int success(List<Step> steps, Realm realm, int layer, int quality, int b) {
		Rank rank = new Rank(realm, layer, 0);
		int strikes = Tribulation.strikeCount(rank);
		steps.add(step(0, EpicIds.TRIBULATION, EpicIds.TRIB_END_OK, strikes, 0, EpicIds.FLAG_FINAL));
		steps.add(step(0, EpicIds.BREAKTHROUGH, EpicIds.BT_SUCCESS, EpicIds.packBreakthrough(realm.ordinal(), quality, EpicIds.KIND_TRIBULATION, layer), b,
				EpicIds.FLAG_FINAL));
		return EpicScale.of(rank);
	}

	private static void reveal(List<Step> steps, Realm realm, int kind) {
		steps.add(disciple(0, EpicIds.DISCIPLE_BT, EpicIds.DBT_REVEAL, EpicIds.packBreakthrough(realm.ordinal(), 1, EpicIds.KIND_TRIBULATION, 0), kind,
				EpicIds.FLAG_FINAL));
	}

	private static Step step(int delay, int moment, int beat, int a, int b, int flags) {
		return new Step(delay, moment, beat, a, b, 0.0F, flags, AT_SELF);
	}

	private static Step disciple(int delay, int moment, int beat, int a, int b, int flags) {
		return new Step(delay, moment, beat, a, b, 0.0F, flags, AT_DISCIPLE);
	}

	/** The player's sect template (1 without a sect; {@code belowNine} keeps it within templates 1-8). */
	private static int template(ServerPlayer player, boolean belowNine) {
		SiteView site = EngineApi.get().site(player.getUUID());
		int template = site == null ? 1 : Math.max(1, site.template());
		return belowNine ? Math.min(8, template) : template;
	}

	static @Nullable Realm realm(String id) {
		Realm realm = Realm.byId(id);
		if (realm != null) {
			return realm;
		}
		int alias = REALM_ALIASES.indexOf(id);
		return alias < 0 ? null : Realm.values()[alias];
	}

	static @Nullable Dao dao(String id) {
		Dao dao = Dao.byId(id);
		if (dao != null) {
			return dao;
		}
		int alias = DAO_ALIASES.indexOf(id);
		return alias < 0 ? null : Dao.values()[alias];
	}

	static @Nullable DestroyerArts.Art art(String id) {
		for (DestroyerArts.Art art : DestroyerArts.Art.values()) {
			if (art.id().equals(id)) {
				return art;
			}
		}
		return null;
	}

	/** The argument values of an id, for tab completion. */
	static List<String> argValues(String id) {
		List<String> values = new ArrayList<>();
		switch (id) {
			case "fondasi" -> values.addAll(QUALITIES);
			case "penyatuan_dao", "wilayah_dao" -> {
				for (Dao dao : Dao.values()) {
					values.add(dao.id());
				}
				values.addAll(DAO_ALIASES);
			}
			case "kenaikan_abadi", "ujian_masuk" -> {
				for (int i = 1; i <= 9; i++) {
					values.add(String.valueOf(i));
				}
			}
			case "bencana" -> {
				for (int i = 1; i <= 7; i++) {
					values.add(String.valueOf(i));
				}
			}
			case "jurus_abadi" -> values.addAll(ABADI_SPECS);
			case "jurus_dewa" -> values.addAll(DEWA_SPECS);
			case "pemusnah" -> {
				for (DestroyerArts.Art art : DestroyerArts.Art.values()) {
					values.add(art.id());
				}
			}
			case "penerimaan" -> values.add("perang");
			case "turnamen" -> values.addAll(TOURNAMENTS);
			case "bos" -> values.addAll(BOSSES);
			case "lolos_maut" -> values.addAll(SAVES);
			default -> {
			}
		}
		return values;
	}

	private static @Nullable Integer number(String text) {
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private static long now(ServerPlayer player) {
		return player.level().getServer().overworld().getGameTime();
	}

	private static boolean enabled(ServerPlayer player) {
		if (!KultivatorConfig.get().epicMoments) {
			Msg.chat(player, Component.translatable(PREFIX + "test.disabled").withStyle(ChatFormatting.RED));
			return false;
		}
		return true;
	}

	private EpicTests() {
	}
}
