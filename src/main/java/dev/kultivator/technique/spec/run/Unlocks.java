package dev.kultivator.technique.spec.run;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.fx.Fx;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Cond;
import dev.kultivator.technique.spec.dsl.Feat;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Automatic learning and the acquisition checklist (DESIGN 22.8). Automatic routes: REALM (universal specs at the realm),
 * DAO (the Dao's first arts once it is chosen), COMP n, completed FEATs and the Gerbang Dewa (rank, ascension,
 * comprehension 90 and the named prerequisites). KNOWN n, SECOND_DAO n and PAIR n (a bonded disciple) are conditions that
 * must hold on top of the other route. Every gate (rank, Dao, comprehension, prerequisites, Dewa gate) must hold. The
 * checklist is pure: the client codex shows it on the synced data.
 */
public final class Unlocks {

	/** One checklist line: its text, whether it is met, and a progress / goal pair (0 / 0 when not counted). */
	public record Line(Component text, boolean met, int progress, int goal) {
	}

	private static final int JADE = 0x8FE3C8;
	private static final int GOLD = 0xFFD54A;
	private static final int MUTED = 0xA8B5B0;
	/** Dewa gate I/II: comprehension in the spec's Dao (main for universal). */
	public static final double DEWA_COMPREHENSION = 90.0D;
	/** Phase-7 star route: ★9 (gate I) or ★18 (gate II). */
	public static final int DEWA_STARS_I = 9;
	public static final int DEWA_STARS_II = 18;

	/** Learns every automatic spec the player now qualifies for (rank, Dao or comprehension changed). Never forgets. */
	public static void refresh(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		boolean knewNothing = data.learnedIds().isEmpty();
		List<TechniqueSpec> fresh = new ArrayList<>();
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (!spec.playerVisible() || data.knows(spec.id())) {
				continue;
			}
			if (automatic(player, data, spec) && data.learn(spec.id())) {
				fresh.add(spec);
			}
		}
		for (TechniqueSpec spec : fresh) {
			announce(player, spec);
		}
		if (knewNothing && !fresh.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.techniques.hint.keys",
					Component.keybind("key.kultivator.cast").withStyle(ChatFormatting.YELLOW),
					Component.keybind("key.kultivator.previous").withStyle(ChatFormatting.YELLOW),
					Component.keybind("key.kultivator.next").withStyle(ChatFormatting.YELLOW),
					Component.keybind("key.kultivator.info").withStyle(ChatFormatting.YELLOW)).withColor(MUTED));
		}
		boolean selectionChanged = ensureSelection(data);
		checkAdvancements(player, data);
		if (!fresh.isEmpty() || selectionChanged) {
			Cultivators.dirty(player);
		}
	}

	/** The chat line, the "Pencerahan Jurus" sound and FX (and the Dewa gate line) for a newly learned spec. */
	static void announce(ServerPlayer player, TechniqueSpec spec) {
		Msg.chat(player, Component.translatable("kultivator.techniques.learned", spec.displayName().copy().withColor(GOLD),
				spec.description().copy().withColor(MUTED)).withColor(JADE));
		if (spec.legacy() == Technique.FLYING_SWORD) {
			Msg.chat(player, Component.translatable("kultivator.techniques.hint.flying_sword",
					Component.keybind("key.kultivator.flying_sword").withStyle(ChatFormatting.YELLOW)).withColor(MUTED));
		}
		if (spec.dewa()) {
			Msg.chat(player, Component.translatable("kultivator.teknik.dewa_gate_open", spec.displayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		Msg.actionBar(player, Component.translatable("kultivator.teknik.toast.enlightened", spec.displayName()).withColor(GOLD));
		Fx.burst(player.level(), player.position().add(0.0D, 1.2D, 0.0D), spec.grade().ribbon(), 24, 1.2F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	/** True when an automatic route of {@code spec} holds and every gate allows it. */
	static boolean automatic(@Nullable ServerPlayer player, CultivationData data, TechniqueSpec spec) {
		if (!rankOk(data, spec) || !daoOk(data, spec) || comprehension(data, spec) + 1.0E-6D < spec.minComprehension()
				|| prereqBlocker(data, spec) != null) {
			return false;
		}
		if (spec.gate() != TechniqueSpec.Gate.NONE) {
			return dewaGate(data, spec);
		}
		// KNOWN, SECOND_DAO and PAIR are extra conditions of a composite route ("REALM (needs 30 known arts)",
		// "REALM + bond ≥ 50", "COMP 60 + a second Dao ≥ 30"), never routes on their own
		if (!conditionsMet(player, data, spec)) {
			return false;
		}
		for (Route route : spec.routes()) {
			boolean met = switch (route.kind()) {
				case REALM -> spec.universal() || spec.dao() == data.dao();
				case DAO -> spec.dao() != null && spec.dao() == data.dao();
				case COMP -> comprehension(data, spec) + 1.0E-6D >= route.n();
				case FEAT -> route.feat() != null && FeatTracker.progress(data, route) >= route.feat().goal();
				default -> false;
			};
			if (met) {
				return true;
			}
		}
		return false;
	}

	/** True for the route kinds that only add a condition to the spec's other routes. */
	public static boolean isCondition(Route.Kind kind) {
		return kind == Route.Kind.KNOWN || kind == Route.Kind.SECOND_DAO || kind == Route.Kind.PAIR;
	}

	/**
	 * Every condition route of {@code spec} holds (KNOWN n, SECOND_DAO n; PAIR n needs the player, a bonded disciple
	 * being loaded, and fails without one).
	 */
	static boolean conditionsMet(@Nullable ServerPlayer player, CultivationData data, TechniqueSpec spec) {
		for (Route route : spec.routes()) {
			boolean met = switch (route.kind()) {
				case KNOWN -> PlayerCaster.known(data) >= route.n();
				case SECOND_DAO -> secondDao(data) + 1.0E-6D >= route.n();
				case PAIR -> player != null && pairBond(player) >= route.n();
				default -> true;
			};
			if (!met) {
				return false;
			}
		}
		return true;
	}

	/** The rank reached (realm, layer and stage of the gate). */
	public static boolean rankOk(CultivationData data, TechniqueSpec spec) {
		Rank rank = data.rank();
		Rank need = spec.minRank();
		if (rank.realm().ordinal() != need.realm().ordinal()) {
			return rank.realm().atLeast(need.realm());
		}
		return rank.layer() > need.layer() || rank.layer() == need.layer() && rank.stage() >= need.stage();
	}

	public static boolean daoOk(CultivationData data, TechniqueSpec spec) {
		return spec.dao() == null || spec.dao() == data.dao();
	}

	/** The spec's Dao comprehension (main comprehension for universal specs). */
	public static double comprehension(CultivationData data, TechniqueSpec spec) {
		return spec.dao() == null ? data.mainComprehension() : data.comprehension(spec.dao());
	}

	/** The best comprehension of a Dao other than the main one. */
	public static double secondDao(CultivationData data) {
		double best = 0.0D;
		for (Dao dao : Dao.values()) {
			if (dao != data.dao()) {
				best = Math.max(best, data.comprehension(dao));
			}
		}
		return best;
	}

	private static int pairBond(ServerPlayer player) {
		int best = 0;
		for (DiscipleEntity entity : Disciples.loaded(player.getUUID())) {
			best = Math.max(best, DisciplePaths.bond(entity.data()));
		}
		return best;
	}

	/** Refusal 6 of 22.2.6: the Dewa gate or a prerequisite is missing (null = fine). */
	static @Nullable Component gateBlocker(CultivationData data, TechniqueSpec spec) {
		Component prereq = prereqBlocker(data, spec);
		if (prereq != null) {
			return prereq;
		}
		if (spec.gate() != TechniqueSpec.Gate.NONE && !dewaGate(data, spec)) {
			return Component.translatable("kultivator.teknik.refuse.gate", spec.displayName(),
					Component.translatable("kultivator.teknik.gate." + (spec.gate() == TechniqueSpec.Gate.DEWA_II ? "2" : "1")));
		}
		return null;
	}

	private static @Nullable Component prereqBlocker(CultivationData data, TechniqueSpec spec) {
		for (TechniqueSpec.Prereq prereq : spec.prereqs()) {
			TechniqueSpec other = TechniqueRegistry.byId(prereq.id());
			if (other == null) {
				continue;
			}
			if (!data.knows(prereq.id()) || Mastery.level(data, prereq.id()) < prereq.level()) {
				return Component.translatable("kultivator.teknik.refuse.prereq", spec.displayName(), other.displayName(), Mastery.levelName(prereq.level()));
			}
		}
		return null;
	}

	/**
	 * Gerbang Dewa (22.8.8): Dewa Abadi Puncak and ascended, comprehension ≥ 90 in the spec's Dao (main for universal), the
	 * named prerequisites at their levels; or the phase-7 star route (★9 gate I, ★18 gate II).
	 */
	public static boolean dewaGate(CultivationData data, TechniqueSpec spec) {
		if (spec.gate() == TechniqueSpec.Gate.NONE) {
			return true;
		}
		int stars = TechniquePower.playerStars(data);
		if (stars >= (spec.gate() == TechniqueSpec.Gate.DEWA_II ? DEWA_STARS_II : DEWA_STARS_I)) {
			return true;
		}
		Rank rank = data.rank();
		if (rank.realm() != Realm.TRUE_IMMORTAL || rank.stage() < Realm.TRUE_IMMORTAL.stages() - 1 || !data.ascended()) {
			return false;
		}
		if (comprehension(data, spec) + 1.0E-6D < DEWA_COMPREHENSION) {
			return false;
		}
		for (TechniqueSpec.Prereq prereq : spec.prereqs()) {
			if (!data.knows(prereq.id()) || Mastery.level(data, prereq.id()) < prereq.level()) {
				return false;
			}
		}
		return true;
	}

	private static boolean ensureSelection(CultivationData data) {
		String selected = data.selectedId();
		TechniqueSpec current = TechniqueRegistry.byId(selected);
		if (current != null && Loadout.castable(data, current)) {
			return false;
		}
		List<TechniqueSpec> list = Loadout.cycle(data);
		TechniqueSpec choice = list.isEmpty() ? null : list.get(0);
		if (choice == null) {
			return false;
		}
		if (current != null && data.knows(current.id())) {
			return false;
		}
		data.setSelectedId(choice.id());
		return true;
	}

	/** technique_50 / 100 / all, the first Langit / Abadi / Dewa spec, technique_collector. */
	static void checkAdvancements(ServerPlayer player, CultivationData data) {
		int known = PlayerCaster.known(data);
		if (known >= 10) {
			Advancements.grant(player, "technique_collector");
		}
		if (known >= 50) {
			Advancements.grant(player, "technique_50");
		}
		if (known >= 100) {
			Advancements.grant(player, "technique_100");
		}
		int total = TechniqueRegistry.playerTotal();
		if (total > 0 && known >= total) {
			Advancements.grant(player, "technique_all");
		}
		// a level reached without a toast (a comprehension gate, a command) still counts
		int highest = Mastery.highest(data);
		if (highest >= 3) {
			Advancements.grant(player, "great_perfection");
		}
		if (highest >= 4) {
			Advancements.grant(player, "peak_mastery");
		}
		for (TechniqueSpec spec : data.learnedSpecs()) {
			if (spec.grade() == TechniqueGrade.TIAN) {
				Advancements.grant(player, "heaven_grade");
			} else if (spec.grade() == TechniqueGrade.ABADI) {
				Advancements.grant(player, "immortal_grade");
			} else if (spec.grade() == TechniqueGrade.DEWA) {
				Advancements.grant(player, "god_grade");
			}
		}
	}

	/**
	 * An Enlightenment Moment (22.6, 22.8.6): +10 mastery to the selected art; 25% to comprehend the next COMP spec early
	 * when within 10 points of its threshold, or to reveal an ENLIGHT spec (Kenaikan Abadi Tahap 5+).
	 */
	static void onEnlightenment(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		String selected = data.selectedId();
		if (TechniqueRegistry.byId(selected) != null) {
			Mastery.add(player, selected, Mastery.ENLIGHTENMENT_POINTS, Mastery.Source.ENLIGHTENMENT);
		}
		if (player.getRandom().nextDouble() >= 0.25D) {
			return;
		}
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (!spec.playerVisible() || data.knows(spec.id()) || !rankOk(data, spec) || !daoOk(data, spec) || prereqBlocker(data, spec) != null
					|| spec.gate() != TechniqueSpec.Gate.NONE || !conditionsMet(player, data, spec)) {
				continue;
			}
			for (Route route : spec.routes()) {
				boolean early = route.kind() == Route.Kind.COMP && comprehension(data, spec) + 10.0D >= route.n()
						|| route.kind() == Route.Kind.ENLIGHT && data.rank().realm().atLeast(Realm.IMMORTAL_ASCENSION)
						&& (data.realm() != Realm.IMMORTAL_ASCENSION || data.rank().layer() >= 4);
				if (early && data.learn(spec.id())) {
					announce(player, spec);
					Cultivators.dirty(player);
					return;
				}
			}
		}
	}

	// ================================================================== the checklist (pure)

	/** The acquisition checklist of {@code spec} for the player (pure; the codex shows it). */
	public static List<Line> checklist(CultivationData data, TechniqueSpec spec) {
		List<Line> lines = new ArrayList<>();
		Rank need = spec.minRank();
		MutableComponent rankText = spec.minRealm() == Realm.IMMORTAL_ASCENSION || need.stage() > 0 ? need.displayName()
				: spec.minRealm().displayName().copy();
		lines.add(new Line(Component.translatable("kultivator.teknik.check.realm", rankText), rankOk(data, spec), 0, 0));
		if (spec.dao() != null) {
			lines.add(new Line(Component.translatable("kultivator.teknik.check.dao", spec.dao().displayName()), daoOk(data, spec), 0, 0));
		}
		if (spec.minComprehension() > 0.0D) {
			lines.add(new Line(Component.translatable("kultivator.teknik.check.comprehension", (int) spec.minComprehension()),
					comprehension(data, spec) + 1.0E-6D >= spec.minComprehension(), (int) comprehension(data, spec), (int) spec.minComprehension()));
		}
		for (Route route : spec.routes()) {
			lines.add(routeLine(data, spec, route));
		}
		if (spec.slipAllowed()) {
			lines.add(new Line(Component.translatable("kultivator.teknik.check.slip"), data.knows(spec.id()), 0, 0));
		}
		if (spec.pavilion() && spec.grade().pavilionPrice() > 0 && spec.routes().stream().noneMatch(route -> route.kind() == Route.Kind.PAV)) {
			lines.add(new Line(Component.translatable("kultivator.teknik.check.pav", spec.grade().displayName(), spec.grade().pavilionHallLevel(),
					spec.grade().pavilionPrice()), data.knows(spec.id()), 0, 0));
		}
		for (TechniqueSpec.Prereq prereq : spec.prereqs()) {
			TechniqueSpec other = TechniqueRegistry.byId(prereq.id());
			Component name = other == null ? Component.literal(prereq.id()) : other.displayName();
			int level = data.knows(prereq.id()) ? Mastery.level(data, prereq.id()) : 0;
			lines.add(new Line(Component.translatable("kultivator.teknik.check.prereq", name, Mastery.levelName(prereq.level())),
					level >= prereq.level(), level, prereq.level()));
		}
		if (spec.gate() != TechniqueSpec.Gate.NONE) {
			Rank rank = data.rank();
			boolean peak = rank.realm() == Realm.TRUE_IMMORTAL && rank.stage() >= Realm.TRUE_IMMORTAL.stages() - 1;
			lines.add(new Line(Component.translatable("kultivator.teknik.check.gate_rank"), peak && data.ascended(), 0, 0));
			lines.add(new Line(Component.translatable("kultivator.teknik.check.gate_comprehension", (int) DEWA_COMPREHENSION),
					comprehension(data, spec) + 1.0E-6D >= DEWA_COMPREHENSION, (int) comprehension(data, spec), (int) DEWA_COMPREHENSION));
			int stars = spec.gate() == TechniqueSpec.Gate.DEWA_II ? DEWA_STARS_II : DEWA_STARS_I;
			lines.add(new Line(Component.translatable("kultivator.teknik.check.gate_stars", stars), TechniquePower.playerStars(data) >= stars,
					TechniquePower.playerStars(data), stars));
		}
		return lines;
	}

	private static Line routeLine(CultivationData data, TechniqueSpec spec, Route route) {
		return switch (route.kind()) {
			case REALM -> new Line(Component.translatable("kultivator.teknik.check.route.realm"), rankOk(data, spec), 0, 0);
			case DAO -> new Line(Component.translatable("kultivator.teknik.check.route.dao"), daoOk(data, spec) && rankOk(data, spec), 0, 0);
			case COMP -> new Line(Component.translatable("kultivator.teknik.check.route.comp", route.n()),
					comprehension(data, spec) + 1.0E-6D >= route.n(), (int) comprehension(data, spec), route.n());
			case BOOK -> {
				List<Component> names = new ArrayList<>();
				for (String key : route.keys()) {
					String id = key.contains(":") ? key : "minecraft:" + key;
					names.add(Component.translatable("enchantment." + id.replace(':', '.')));
				}
				yield new Line(Component.translatable("kultivator.teknik.check.route.book", net.minecraft.network.chat.ComponentUtils.formatList(names,
						Component.literal(", "))), data.knows(spec.id()), 0, 0);
			}
			case PAV -> new Line(Component.translatable("kultivator.teknik.check.pav", spec.grade().displayName(), spec.grade().pavilionHallLevel(),
					spec.grade().pavilionPrice()), data.knows(spec.id()), 0, 0);
			case FEAT -> {
				Feat feat = route.feat();
				int progress = FeatTracker.progress(data, route);
				int goal = feat == null ? 1 : feat.goal();
				yield new Line(feat == null ? Component.literal("?") : featText(feat, progress), progress >= goal, progress, goal);
			}
			case ITEM -> new Line(Component.translatable("kultivator.teknik.check.route.item", Component.translatable("kultivator.teknik.item."
					+ route.key().replace(':', '.'))), data.knows(spec.id()), 0, 0);
			case SCR -> new Line(Component.translatable("kultivator.teknik.check.route.scr", route.n()), data.knows(spec.id()), 0, route.n());
			case INH -> new Line(Component.translatable("kultivator.teknik.check.route.inh", theme(route.key())), data.knows(spec.id()), 0, 0);
			case ENLIGHT -> new Line(Component.translatable("kultivator.teknik.check.route.enlight"), data.knows(spec.id()), 0, 0);
			// condition lines (KNOWN, SECOND_DAO, PAIR) are required on top of the spec's other route
			case PAIR -> new Line(Component.translatable("kultivator.teknik.check.route.pair", route.n()), data.knows(spec.id()), 0, route.n());
			case KNOWN -> new Line(Component.translatable("kultivator.teknik.check.route.known", route.n()), PlayerCaster.known(data) >= route.n(),
					PlayerCaster.known(data), route.n());
			case SECOND_DAO -> new Line(Component.translatable("kultivator.teknik.check.route.second_dao", route.n()),
					secondDao(data) + 1.0E-6D >= route.n(), (int) secondDao(data), route.n());
		};
	}

	private static Component theme(String key) {
		Dao dao = Dao.byId(key);
		return dao == null ? Component.translatable("kultivator.teknik.theme.universal") : dao.displayName();
	}

	/** "Bunuh 12/30 dengan Pedang di malam bulan purnama" and the other feat texts. */
	public static Component featText(Feat feat, int progress) {
		Component who = feat.dao() != null ? feat.dao().displayName()
				: !feat.specId().isEmpty() && TechniqueRegistry.byId(feat.specId()) != null ? TechniqueRegistry.byId(feat.specId()).displayName()
				: Component.translatable("kultivator.teknik.feat.any");
		Component where = condText(feat.cond());
		Component mobs = feat.mobs().isEmpty() ? Component.translatable("kultivator.teknik.feat.mobs_any")
				: Component.literal(String.join(", ", feat.mobs().stream().map(Unlocks::mobName).toList()));
		return switch (feat.kind()) {
			case KILLS_WITH -> Component.translatable("kultivator.teknik.feat.kills_with", progress, feat.goal(), who, where, mobs);
			case CASTS_WITH -> Component.translatable("kultivator.teknik.feat.casts_with", progress, feat.goal(), who, where);
			case FIRST_KILL -> Component.translatable(feat.all() ? "kultivator.teknik.feat.first_kill_all" : "kultivator.teknik.feat.first_kill",
					progress, feat.goal(), mobs, who);
			case MEDITATE_AT -> Component.translatable("kultivator.teknik.feat.meditate_at", progress, feat.goal(), where);
			case ALCHEMY_COUNT -> Component.translatable("kultivator.teknik.feat.alchemy", progress, feat.goal());
			case TRIB -> feat.quality() == null ? Component.translatable("kultivator.teknik.feat.trib_strikes", progress, feat.goal())
					: Component.translatable(feat.average() ? "kultivator.teknik.feat.trib_average" : "kultivator.teknik.feat.trib_quality",
					feat.quality().displayName(), feat.realm() == null ? Component.literal("-") : feat.realm().displayName());
			case HEART_DEMON_WIN -> Component.translatable("kultivator.teknik.feat.heart_demon",
					feat.realm() == null ? Component.literal("-") : feat.realm().displayName());
			case DEVIATION_RECOVERED -> Component.translatable("kultivator.teknik.feat.deviation",
					feat.realm() == null ? Component.literal("-") : feat.realm().displayName());
		};
	}

	private static String mobName(String id) {
		return id.startsWith("#") ? id : id.replace("minecraft:", "");
	}

	/** A short text for a condition ("di malam bulan purnama"). */
	public static Component condText(Cond cond) {
		return switch (cond.kind()) {
			case NONE -> Component.empty();
			case BIOME -> Component.translatable("kultivator.teknik.cond.biome", cond.key());
			case DIMENSION -> Component.translatable("kultivator.teknik.cond.dimension", cond.key());
			case ALTITUDE -> Component.translatable("kultivator.teknik.cond.altitude", (int) cond.a());
			case SITE -> Component.translatable("kultivator.teknik.cond.site", Component.translatable("kultivator.meditation.site." + cond.key()));
			case ALL, ANY -> {
				List<Component> parts = new ArrayList<>();
				for (Cond part : cond.parts()) {
					parts.add(condText(part));
				}
				yield net.minecraft.network.chat.ComponentUtils.formatList(parts, Component.translatable(cond.kind() == Cond.Kind.ALL
						? "kultivator.teknik.cond.and" : "kultivator.teknik.cond.or"));
			}
			case NOT -> Component.translatable("kultivator.teknik.cond.not", cond.parts().isEmpty() ? Component.empty() : condText(cond.parts().get(0)));
			default -> Component.translatable("kultivator.teknik.cond." + cond.kind().name().toLowerCase(Locale.ROOT));
		};
	}

	private Unlocks() {
	}
}
