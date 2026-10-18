package dev.kultivator.formasi;

import dev.kultivator.Kultivator;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.formasi.spec.EffectSink;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.Flag;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.SpecSink;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Every sect formation spec and effect handler (DESIGN 24.2.2, as 22.2.3), built once in {@code Formasi.init()} on both
 * physical sides: legacy mirrors, catalogue A, {@code FormasiB.init(specSink, effectSink)}, then {@link #freeze()}. A
 * duplicate id or netId, a late registration, a bad id, a broken pattern or (at freeze) an unknown effect id logs an
 * error and refuses that spec; startup never crashes.
 */
public final class FormationRegistry {

	private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
	private static final Map<String, FormationSpec> BY_ID = new HashMap<>();
	private static final Map<Integer, FormationSpec> BY_NET = new HashMap<>();
	private static final List<FormationSpec> ALL = new ArrayList<>();
	private static final Map<String, FormationEffect> EFFECTS = new HashMap<>();
	private static final Map<Family, List<FormationSpec>> BY_FAMILY = new EnumMap<>(Family.class);
	private static boolean frozen;

	private static final SpecSink SPECS = FormationRegistry::add;
	private static final EffectSink EFFECT_SINK = FormationRegistry::addEffect;

	/** The sink catalogues add their specs to. */
	public static SpecSink specSink() {
		return SPECS;
	}

	/** The sink modules add their handlers to. */
	public static EffectSink effectSink() {
		return EFFECT_SINK;
	}

	private static void add(FormationSpec spec) {
		String why = null;
		if (frozen) {
			why = "late registration";
		} else if (spec == null || spec.id() == null || !ID.matcher(spec.id()).matches()) {
			why = "bad id";
		} else if (spec.netId() < 0 || spec.netId() > 799) {
			why = "netId out of range";
		} else if (BY_ID.containsKey(spec.id())) {
			why = "duplicate id";
		} else if (BY_NET.containsKey(spec.netId())) {
			why = "duplicate netId " + spec.netId();
		} else if (spec.pattern() == null) {
			why = "no pattern";
		} else if (spec.pattern().error() != null) {
			why = "pattern: " + spec.pattern().error();
		} else if (spec.pattern().size() == 0 && !spec.has(Flag.LEGACY_MIRROR)) {
			why = "empty pattern";
		}
		if (why != null) {
			Kultivator.LOGGER.error("Formation spec {} refused: {}", spec == null ? "?" : spec.id(), why);
			return;
		}
		BY_ID.put(spec.id(), spec);
		BY_NET.put(spec.netId(), spec);
		ALL.add(spec);
	}

	private static void addEffect(String id, FormationEffect effect) {
		if (frozen || id == null || effect == null || EFFECTS.containsKey(id)) {
			Kultivator.LOGGER.error("Formation effect {} refused ({})", id, frozen ? "late" : "duplicate or empty");
			return;
		}
		EFFECTS.put(id, effect);
	}

	/** Ends registration: refuses specs with an unknown effect, sorts by grade then size, builds the family lists. */
	public static void freeze() {
		if (frozen) {
			return;
		}
		ALL.removeIf(spec -> {
			if (spec.has(Flag.LEGACY_MIRROR) || EFFECTS.containsKey(spec.effect())) {
				return false;
			}
			Kultivator.LOGGER.error("Formation spec {} refused: unknown effect '{}'", spec.id(), spec.effect());
			BY_ID.remove(spec.id());
			BY_NET.remove(spec.netId());
			return true;
		});
		ALL.sort(Comparator.comparingInt((FormationSpec s) -> s.grade().ordinal()).thenComparingInt(FormationSpec::netId));
		for (Family family : Family.values()) {
			BY_FAMILY.put(family, new ArrayList<>());
		}
		for (FormationSpec spec : ALL) {
			BY_FAMILY.get(spec.family()).add(spec);
		}
		frozen = true;
		Kultivator.LOGGER.info("Formasi Sekte: {} formation specs, {} effects", ALL.size(), EFFECTS.size());
	}

	public static @Nullable FormationSpec byId(String id) {
		return BY_ID.get(id);
	}

	public static @Nullable FormationSpec byNet(int netId) {
		return BY_NET.get(netId);
	}

	/** Every spec, by grade then netId (registration order before {@link #freeze()}). */
	public static List<FormationSpec> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static List<FormationSpec> ofFamily(Family family) {
		List<FormationSpec> list = BY_FAMILY.get(family);
		return list == null ? List.of() : Collections.unmodifiableList(list);
	}

	public static @Nullable FormationEffect effectOf(FormationSpec spec) {
		return spec == null ? null : EFFECTS.get(spec.effect());
	}

	public static boolean frozen() {
		return frozen;
	}

	private FormationRegistry() {
	}
}
