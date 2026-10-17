package dev.kultivator.client.disciple;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.RootGrade;
import dev.kultivator.data.Technique;
import dev.kultivator.disciple.DiscipleTechniques;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The disciple's details from a {@code DiscipleInfo} tag, read once per packet for the screen. Fields are looked up in
 * the root and in a nested "data" (or "disciple" / "cultivation") compound, and missing ones fall back to sensible
 * values, so the screen keeps working whatever extra live values the server adds. Keys read (see
 * {@code DiscipleCultivation.infoFor}): realm, layer, stage, xp, xp_required, stored_qi, qi, max_qi, root_elements,
 * root_grade, dao (-1 none), comprehension, learned (technique ids), total_kills, mode, health, max_health, deviating,
 * deviation_ticks, breaking_through, can_breakthrough, breakthrough_reason, breakthrough_kind, breakthrough_chance,
 * next_realm / next_layer / next_stage, can_choose_dao, dao_reason, teach_cost, pill_item,
 * pill_reason, pill_cooldown and "teachable": a list of {id, ok, known, reason_key, qi_cost, cooldown} for the master's
 * techniques. Reasons are translation keys (plain text also works).
 */
final class DiscipleView {

	/** The order ids of {@code DiscipleEntity.Mode}; guard_hall is the Sect Hall protector's patrol (DESIGN 20.2.5). */
	static final String[] MODES = {"follow", "stay", "meditate", "assist", "guard_hall"};

	/**
	 * One of the master's techniques: whether it can be taught now, whether the disciple knows it, why not, and the
	 * disciple's own qi cost and cooldown for it (-1 when unknown).
	 */
	record Entry(Technique technique, boolean ok, boolean learned, @Nullable Component reason, double qiCost, int cooldown) {
	}

	private final List<CompoundTag> scopes = new ArrayList<>();

	final String name;
	final String master;
	final Rank rank;
	final double xp;
	final double xpRequired;
	final double storedQi;
	final double qi;
	final double maxQi;
	final int rootMask;
	final RootGrade grade;
	final boolean rootKnown;
	final @Nullable Dao dao;
	final double comprehension;
	final Set<Technique> learned = EnumSet.noneOf(Technique.class);
	final List<Entry> teachable = new ArrayList<>();
	final int kills;
	final String mode;
	final double health;
	final double maxHealth;
	final int deviationTicks;
	final boolean deviating;
	final boolean breakingThrough;
	final boolean canBreakthrough;
	final @Nullable Component breakthroughReason;
	/** "awakening", "chance", "tribulation" or "none" (at the summit). */
	final String breakthroughKind;
	/** 0..1, or -1 when the server did not say. */
	final double breakthroughChance;
	/** The rank the next breakthrough leads to (the next stage's realm when not at the bottleneck yet). */
	final Rank nextRank;
	final boolean canChooseDao;
	final @Nullable Component daoReason;
	/** Qi the master pays to teach, or -1 when the server did not say. */
	final double teachCost;
	/** Registry id of the item the master held when the details were made ("" for none) and why it cannot be fed. */
	final String pillItem;
	final @Nullable Component pillReason;
	/** Ticks until that pill can be fed again. */
	final int pillCooldown;

	DiscipleView(CompoundTag tag) {
		scopes.add(tag);
		for (String key : new String[]{"data", "disciple", "cultivation", "kultivator_disciple", "live"}) {
			if (tag.get(key) instanceof CompoundTag nested) {
				scopes.add(nested);
			}
		}
		name = text("", "name", "display_name", "custom_name");
		master = text("", "master", "owner", "owner_name", "master_name");
		Realm realm = realmOf(find("realm"));
		rank = new Rank(realm, integer(0, "layer"), integer(0, "stage"));
		xp = Math.max(0.0D, number(0.0D, "xp"));
		xpRequired = Math.max(1.0D, number(rank.xpRequired(), "xp_required", "xp_max", "required_xp", "xp_needed"));
		storedQi = Math.max(0.0D, number(0.0D, "stored_qi", "storedQi"));
		qi = Math.max(0.0D, number(0.0D, "qi"));
		maxQi = Math.max(1.0D, number(100.0D + realm.ordinal() * 120.0D + rank.stage() * 30.0D + rank.layer() * 60.0D, "max_qi", "qi_max", "maxQi"));
		rootMask = rootMaskOf(find("root_elements", "root", "elements"));
		grade = gradeOf(find("root_grade", "grade"));
		rootKnown = flag(rootMask != 0, "root_tested", "root_known");
		dao = daoOf(find("dao"));
		comprehension = comprehensionOf(find("comprehension"), dao);
		readLearned(find("learned", "known", "techniques"));
		if (!readTeachable(find("teachable", "teachables", "teach"))) {
			guessTeachable();
		}
		kills = integer(0, "total_kills", "kills", "kill_count");
		mode = modeOf(find("mode", "order"));
		health = number(-1.0D, "health");
		maxHealth = number(-1.0D, "max_health");
		int seconds = integer(0, "deviation_seconds");
		deviationTicks = Math.max(integer(0, "deviation_ticks", "weakness_ticks"), seconds * 20);
		deviating = flag(deviationTicks > 0, "deviating");
		breakingThrough = flag(false, "breaking_through", "breakthrough_running");
		canBreakthrough = flag(false, "can_breakthrough", "breakthrough_ready", "can_break_through");
		breakthroughReason = reasonOf(find("breakthrough_reason", "reason_key", "bt_reason"));
		breakthroughKind = text(localKind(rank), "breakthrough_kind");
		breakthroughChance = number(-1.0D, "breakthrough_chance");
		Tag nextRealm = find("next_realm");
		nextRank = nextRealm != null ? new Rank(realmOf(nextRealm), integer(0, "next_layer"), integer(0, "next_stage")) : localNext(rank);
		canChooseDao = flag(dao == null && realm.atLeast(Realm.GOLDEN_CORE), "can_choose_dao", "dao_choosable");
		daoReason = reasonOf(find("dao_reason", "dao_why"));
		teachCost = number(-1.0D, "teach_cost", "teach_qi_cost");
		pillItem = text("", "pill_item");
		pillReason = reasonOf(find("pill_reason"));
		pillCooldown = integer(0, "pill_cooldown");
	}

	/** The kind of the next breakthrough by DESIGN 18 (used when the server did not say). */
	private static String localKind(Rank rank) {
		if (rank.isMax()) {
			return "none";
		}
		if (rank.realm().isMortal()) {
			return "awakening";
		}
		return rank.realm() == Realm.QI_REFINING ? "chance" : "tribulation";
	}

	/** The first stage of the next realm (or Immortal Ascension layer). */
	private static Rank localNext(Rank rank) {
		if (rank.isMax()) {
			return rank;
		}
		Rank peak = new Rank(rank.realm(), rank.layer(), rank.realm().stages() - 1);
		return peak.next();
	}

	/** Arts disciples can never wield (flying sword, Dharma form, Dao domain, divine sense, cloud step). */
	static boolean forbidden(Technique technique) {
		return !DiscipleTechniques.teachable(technique);
	}

	/** The bar is full at a bottleneck (the disciple waits for its master to start the breakthrough). */
	boolean barFull() {
		return rank.isBottleneck() && xp >= xpRequired - 1.0E-6D;
	}

	double barFraction() {
		return Math.max(0.0D, Math.min(1.0D, xp / xpRequired));
	}

	@Nullable Entry entry(Technique technique) {
		for (Entry entry : teachable) {
			if (entry.technique == technique) {
				return entry;
			}
		}
		return null;
	}

	/** Which mode index (0 follow .. 3 assist) is active. */
	int modeIndex() {
		for (int i = 0; i < MODES.length; i++) {
			if (MODES[i].equals(mode)) {
				return i;
			}
		}
		return 0;
	}

	// ================================================================ lookups

	private @Nullable Tag find(String... keys) {
		for (CompoundTag scope : scopes) {
			for (String key : keys) {
				Tag tag = scope.get(key);
				if (tag != null) {
					return tag;
				}
			}
		}
		return null;
	}

	private double number(double fallback, String... keys) {
		Tag tag = find(keys);
		return tag == null ? fallback : tag.asNumber().map(Number::doubleValue).orElse(fallback);
	}

	private int integer(int fallback, String... keys) {
		Tag tag = find(keys);
		return tag == null ? fallback : tag.asNumber().map(Number::intValue).orElse(fallback);
	}

	private boolean flag(boolean fallback, String... keys) {
		return truth(find(keys), fallback);
	}

	private String text(String fallback, String... keys) {
		Tag tag = find(keys);
		return tag == null ? fallback : tag.asString().orElse(fallback);
	}

	private static boolean truth(@Nullable Tag tag, boolean fallback) {
		if (tag == null) {
			return fallback;
		}
		Optional<String> string = tag.asString();
		if (string.isPresent()) {
			return "true".equalsIgnoreCase(string.get()) || "1".equals(string.get());
		}
		return tag.asNumber().map(number -> number.doubleValue() != 0.0D).orElse(fallback);
	}

	// ================================================================ conversions

	private static Realm realmOf(@Nullable Tag tag) {
		if (tag == null) {
			return Realm.MORTAL;
		}
		Optional<String> id = tag.asString();
		if (id.isPresent()) {
			Realm realm = Realm.byId(id.get());
			return realm == null ? Realm.MORTAL : realm;
		}
		return Realm.byOrdinal(tag.asNumber().map(Number::intValue).orElse(0));
	}

	private static int rootMaskOf(@Nullable Tag tag) {
		if (tag == null) {
			return 0;
		}
		if (tag instanceof ListTag list) {
			int mask = 0;
			for (int i = 0; i < list.size(); i++) {
				Element element = Element.byId(list.getStringOr(i, ""));
				if (element != null) {
					mask |= element.bit();
				}
			}
			return mask;
		}
		return tag.asNumber().map(Number::intValue).orElse(0);
	}

	private static RootGrade gradeOf(@Nullable Tag tag) {
		if (tag == null) {
			return RootGrade.MID;
		}
		Optional<String> id = tag.asString();
		if (id.isPresent()) {
			RootGrade grade = RootGrade.byId(id.get());
			return grade == null ? RootGrade.MID : grade;
		}
		return RootGrade.byOrdinal(tag.asNumber().map(Number::intValue).orElse(RootGrade.MID.ordinal()));
	}

	private static @Nullable Dao daoOf(@Nullable Tag tag) {
		if (tag == null) {
			return null;
		}
		Optional<String> id = tag.asString();
		if (id.isPresent()) {
			return Dao.byId(id.get());
		}
		return Dao.byOrdinal(tag.asNumber().map(Number::intValue).orElse(-1));
	}

	/** A number, or a compound of comprehension per Dao id (the chosen Dao's value, else the highest). */
	private static double comprehensionOf(@Nullable Tag tag, @Nullable Dao dao) {
		if (tag instanceof CompoundTag perDao) {
			if (dao != null) {
				return perDao.getDoubleOr(dao.id(), 0.0D);
			}
			double best = 0.0D;
			for (Dao each : Dao.values()) {
				best = Math.max(best, perDao.getDoubleOr(each.id(), 0.0D));
			}
			return best;
		}
		return tag == null ? 0.0D : tag.asNumber().map(Number::doubleValue).orElse(0.0D);
	}

	/** Technique ids, or entries with an "id" (like the "techniques" list of the disciple's own arts). */
	private void readLearned(@Nullable Tag tag) {
		if (tag instanceof ListTag list) {
			for (int i = 0; i < list.size(); i++) {
				Tag element = list.get(i);
				String id = element instanceof CompoundTag entry ? entry.getStringOr("id", "") : element.asString().orElse("");
				Technique technique = Technique.byId(id);
				if (technique != null) {
					learned.add(technique);
				}
			}
		} else if (tag instanceof CompoundTag map) {
			for (String key : map.keySet()) {
				Technique technique = Technique.byId(key);
				if (technique != null && truth(map.get(key), true)) {
					learned.add(technique);
				}
			}
		}
	}

	/** Reads the teachable list (a list of entries or a compound keyed by technique id). False when there is none. */
	private boolean readTeachable(@Nullable Tag tag) {
		if (tag instanceof ListTag list) {
			for (int i = 0; i < list.size(); i++) {
				Tag element = list.get(i);
				if (element instanceof CompoundTag entry) {
					addEntry(entry.getStringOr("id", entry.getStringOr("technique", "")), entry);
				} else {
					element.asString().ifPresent(id -> addEntry(id, null));
				}
			}
			return true;
		}
		if (tag instanceof CompoundTag map) {
			for (String key : map.keySet()) {
				Tag value = map.get(key);
				if (value instanceof CompoundTag entry) {
					addEntry(key, entry);
				} else if (value != null && value.asString().isPresent()) {
					String reason = value.asString().get();
					Technique technique = Technique.byId(key);
					if (technique != null) {
						boolean known = learned.contains(technique);
						teachable.add(new Entry(technique, reason.isEmpty() && !known, known, reason.isEmpty() ? null : Component.translatable(reason),
								-1.0D, -1));
					}
				} else if (value != null) {
					Technique technique = Technique.byId(key);
					if (technique != null) {
						boolean known = learned.contains(technique);
						teachable.add(new Entry(technique, truth(value, false) && !known, known, null, -1.0D, -1));
					}
				}
			}
			return true;
		}
		return false;
	}

	private void addEntry(String id, @Nullable CompoundTag entry) {
		Technique technique = Technique.byId(id);
		if (technique == null || entry(technique) != null) {
			return;
		}
		boolean known = learned.contains(technique) || entry != null && truth(firstOf(entry, "learned", "known"), false);
		if (known) {
			learned.add(technique);
		}
		boolean ok = entry == null || truth(firstOf(entry, "ok", "can", "teachable", "can_teach"), false);
		Component reason = entry == null ? null : reasonOf(firstOf(entry, "reason_key", "reason", "why"));
		double qiCost = entry == null ? -1.0D : entry.getDoubleOr("qi_cost", -1.0D);
		int cooldown = entry == null ? -1 : entry.getIntOr("cooldown", -1);
		teachable.add(new Entry(technique, ok && !known, known, reason, qiCost, cooldown));
	}

	private static @Nullable Tag firstOf(CompoundTag tag, String... keys) {
		for (String key : keys) {
			Tag value = tag.get(key);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	/** Builds the list from the master's own techniques and the rules of DESIGN 18 (realm, Dao, arts disciples lack). */
	private void guessTeachable() {
		CultivationData master = ClientCultivation.data();
		for (Technique technique : master.learnedTechniques()) {
			boolean known = learned.contains(technique);
			Component reason = null;
			if (forbidden(technique)) {
				reason = Component.translatable("kultivator.discipleui.reason.forbidden");
			} else if (!rank.realm().atLeast(technique.minRealm())) {
				reason = Component.translatable("kultivator.discipleui.reason.realm", technique.minRealm().displayName());
			} else if (technique.dao() != null && technique.dao() != dao) {
				reason = Component.translatable("kultivator.discipleui.reason.dao", technique.dao().displayName());
			}
			teachable.add(new Entry(technique, reason == null && !known, known, reason, -1.0D, -1));
		}
	}

	private static String modeOf(@Nullable Tag tag) {
		if (tag == null) {
			return MODES[0];
		}
		Optional<String> id = tag.asString();
		if (id.isPresent()) {
			return id.get().toLowerCase(Locale.ROOT);
		}
		int index = tag.asNumber().map(Number::intValue).orElse(0);
		return MODES[Math.max(0, Math.min(MODES.length - 1, index))];
	}

	/** A reason string as a translation key (plain text shows as it is); null when empty or missing. */
	private static @Nullable Component reasonOf(@Nullable Tag tag) {
		if (tag == null) {
			return null;
		}
		String text = tag.asString().orElse("");
		return text.isEmpty() ? null : Component.translatable(text);
	}
}
