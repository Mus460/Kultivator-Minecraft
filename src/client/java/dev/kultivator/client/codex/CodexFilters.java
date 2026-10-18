package dev.kultivator.client.codex;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.TechniqueType;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The Kodeks filters and sort (DESIGN 22.12.2), combined with AND: Dao coins (all, universal, the eight Daos), grade
 * coins (none lit = every grade), the Ranah talisman (all / castable now / each realm from Inti Emas / Gerbang Dewa),
 * the Jenis talisman (all and the eleven types; PASIF hidden), the state chips (all / learned / learnable / locked), the
 * Pamungkas toggle and the search text. Filters and sort are remembered in {@code kultivator-client.json}
 * ({@code codex_filters}, {@code codex_sort}); the "seen" set behind the BARU badge is {@code codex_seen}.
 */
public final class CodexFilters {

	public enum Sort { GRADE, REALM, NAME, DAO, MASTERY }

	/** The facts a filter needs about one spec (the player's codex or a disciple's teach view). */
	public interface View {
		SpecInfo.State state(TechniqueSpec spec);

		boolean castable(TechniqueSpec spec);

		int mastery(TechniqueSpec spec);
	}

	/** Realm choices: 0 all, 1 castable now, 2..7 Inti Emas .. Dewa Abadi, 8 Gerbang Dewa. */
	public static final int REALM_CHOICES = 9;
	public static final Realm[] REALMS = {Realm.GOLDEN_CORE, Realm.NASCENT_SOUL, Realm.DEITY_TRANSFORMATION, Realm.DAO_UNION,
			Realm.IMMORTAL_ASCENSION, Realm.TRUE_IMMORTAL};
	/** The types the Jenis talisman cycles through (PASIF is reserved and hidden). */
	public static final TechniqueType[] TYPES = {TechniqueType.SERANG, TechniqueType.AREA, TechniqueType.KENDALI, TechniqueType.GERAK,
			TechniqueType.LINDUNG, TechniqueType.PULIH, TechniqueType.PANGGIL, TechniqueType.WILAYAH, TechniqueType.UBAH,
			TechniqueType.PAMUNGKAS, TechniqueType.INDRA};

	/** −2 every Dao, −1 universal, 0-7 a Dao ordinal. */
	public int dao = -2;
	/** Bit per grade tier; 0 = every grade. */
	public int grades;
	public int realm;
	/** 0 all, 1.. = TYPES[type − 1]. */
	public int type;
	/** 0 all, 1 learned, 2 learnable, 3 locked. */
	public int state;
	public boolean ultimates;
	public Sort sort = Sort.GRADE;
	public String query = "";
	private final String key;

	public CodexFilters(String key) {
		this.key = key;
	}

	/** The player's codex filters, loaded from the client config. */
	public static CodexFilters player() {
		CodexFilters filters = new CodexFilters("codex");
		filters.load();
		return filters;
	}

	public boolean test(TechniqueSpec spec, View view, String[] tokens) {
		if (dao == -1 && spec.dao() != null || dao >= 0 && (spec.dao() == null || spec.dao().ordinal() != dao)) {
			return false;
		}
		if (grades != 0 && (grades & (1 << spec.grade().tier())) == 0) {
			return false;
		}
		if (realm == 1 && !view.castable(spec)) {
			return false;
		}
		if (realm >= 2 && realm < 8) {
			Realm wanted = REALMS[realm - 2];
			if (spec.minRealm() != wanted || spec.gate() != TechniqueSpec.Gate.NONE) {
				return false;
			}
		}
		if (realm == 8 && spec.gate() == TechniqueSpec.Gate.NONE) {
			return false;
		}
		if (type > 0 && spec.type() != TYPES[type - 1]) {
			return false;
		}
		if (state > 0) {
			SpecInfo.State actual = view.state(spec);
			if (state == 1 && actual != SpecInfo.State.LEARNED || state == 2 && actual != SpecInfo.State.LEARNABLE
					|| state == 3 && actual != SpecInfo.State.LOCKED) {
				return false;
			}
		}
		if (ultimates && !spec.pamungkas()) {
			return false;
		}
		return SearchIndex.matches(spec, tokens);
	}

	/** The specs of {@code all} that pass, sorted. */
	public List<TechniqueSpec> apply(List<TechniqueSpec> all, View view) {
		String[] tokens = SearchIndex.tokens(query);
		List<TechniqueSpec> list = new ArrayList<>();
		Predicate<TechniqueSpec> keep = spec -> test(spec, view, tokens);
		for (TechniqueSpec spec : all) {
			if (keep.test(spec)) {
				list.add(spec);
			}
		}
		list.sort(comparator(view));
		return list;
	}

	private Comparator<TechniqueSpec> comparator(View view) {
		ToIntFunction<TechniqueSpec> catalogue = spec -> spec.netId();
		return switch (sort) {
			case GRADE -> Comparator.comparingInt((TechniqueSpec spec) -> spec.grade().tier()).thenComparingInt(TechniqueSpec::sub)
					.thenComparingInt(catalogue);
			case REALM -> Comparator.comparingInt((TechniqueSpec spec) -> spec.minRealm().ordinal()).thenComparingInt(TechniqueSpec::minLayer)
					.thenComparingInt(spec -> spec.gate().ordinal()).thenComparingInt(catalogue);
			case NAME -> Comparator.comparing((TechniqueSpec spec) -> SearchIndex.fold(spec.displayName().getString()));
			case DAO -> Comparator.comparingInt((TechniqueSpec spec) -> spec.dao() == null ? -1 : spec.dao().ordinal())
					.thenComparingInt(spec -> spec.grade().tier()).thenComparingInt(catalogue);
			case MASTERY -> Comparator.comparingInt((TechniqueSpec spec) -> -view.mastery(spec)).thenComparingInt(spec -> spec.grade().tier())
					.thenComparingInt(catalogue);
		};
	}

	// ================================================================== labels

	public Component realmLabel() {
		if (realm == 0) {
			return Component.translatable("kultivator.codex.filter.realm_all");
		}
		if (realm == 1) {
			return Component.translatable("kultivator.codex.filter.castable");
		}
		if (realm == 8) {
			return Component.translatable("kultivator.codex.filter.gate");
		}
		return REALMS[realm - 2].displayName();
	}

	public Component typeLabel() {
		return type == 0 ? Component.translatable("kultivator.codex.filter.type_all") : TYPES[type - 1].displayName();
	}

	public Component sortLabel() {
		return Component.translatable("kultivator.codex.sort." + sort.name().toLowerCase(java.util.Locale.ROOT));
	}

	public static Component stateLabel(int state) {
		return Component.translatable("kultivator.codex.filter.state." + state);
	}

	public Component daoLabel() {
		if (dao == -2) {
			return Component.translatable("kultivator.codex.filter.dao_all");
		}
		if (dao == -1) {
			return Component.translatable("kultivator.codex.universal");
		}
		return Dao.byOrdinal(dao) == null ? Component.empty() : Dao.byOrdinal(dao).displayName();
	}

	public boolean gradeOn(TechniqueGrade grade) {
		return (grades & (1 << grade.tier())) != 0;
	}

	public void toggleGrade(TechniqueGrade grade) {
		grades ^= 1 << grade.tier();
		save();
	}

	public void cycleRealm(int step) {
		realm = Math.floorMod(realm + step, REALM_CHOICES);
		save();
	}

	public void cycleType(int step) {
		type = Math.floorMod(type + step, TYPES.length + 1);
		save();
	}

	public void cycleSort() {
		sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
		save();
	}

	public void reset() {
		dao = -2;
		grades = 0;
		realm = 0;
		type = 0;
		state = 0;
		ultimates = false;
		query = "";
		save();
	}

	// ================================================================== persistence

	public void save() {
		JsonObject json = new JsonObject();
		json.addProperty("dao", dao);
		json.addProperty("grades", grades);
		json.addProperty("realm", realm);
		json.addProperty("type", type);
		json.addProperty("state", state);
		json.addProperty("ultimates", ultimates);
		EffectQuality.put(key + "_filters", json);
		EffectQuality.put(key + "_sort", new JsonPrimitive(sort.name()));
	}

	private void load() {
		try {
			JsonElement element = EffectQuality.get(key + "_filters");
			if (element != null && element.isJsonObject()) {
				JsonObject json = element.getAsJsonObject();
				dao = Math.max(-2, Math.min(7, intOf(json, "dao", -2)));
				grades = intOf(json, "grades", 0) & 0x3F;
				realm = Math.floorMod(intOf(json, "realm", 0), REALM_CHOICES);
				type = Math.floorMod(intOf(json, "type", 0), TYPES.length + 1);
				state = Math.floorMod(intOf(json, "state", 0), 4);
				ultimates = json.has("ultimates") && json.get("ultimates").getAsBoolean();
			}
			JsonElement sorted = EffectQuality.get(key + "_sort");
			if (sorted != null && sorted.isJsonPrimitive()) {
				for (Sort each : Sort.values()) {
					if (each.name().equals(sorted.getAsString())) {
						sort = each;
					}
				}
			}
		} catch (RuntimeException ignored) {
			// A damaged entry only resets the filters.
		}
	}

	private static int intOf(JsonObject json, String name, int fallback) {
		return json.has(name) ? json.get(name).getAsInt() : fallback;
	}

	// ================================================================== the BARU badge

	private static Set<String> seen;

	/** True when the learned spec was never opened or hovered in the codex. */
	public static boolean isNew(TechniqueSpec spec) {
		return !seen().contains(spec.id());
	}

	public static void markSeen(TechniqueSpec spec) {
		if (seen().add(spec.id())) {
			JsonArray array = new JsonArray();
			for (String id : seen) {
				array.add(id);
			}
			EffectQuality.put("codex_seen", array);
		}
	}

	private static Set<String> seen() {
		if (seen == null) {
			seen = new HashSet<>();
			JsonElement element = EffectQuality.get("codex_seen");
			if (element != null && element.isJsonArray()) {
				for (JsonElement id : element.getAsJsonArray()) {
					try {
						seen.add(id.getAsString());
					} catch (RuntimeException ignored) {
						// skip a broken entry
					}
				}
			}
		}
		return seen;
	}
}
