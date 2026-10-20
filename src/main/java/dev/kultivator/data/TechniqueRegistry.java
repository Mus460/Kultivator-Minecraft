package dev.kultivator.data;

import dev.kultivator.Kultivator;
import dev.kultivator.technique.arts.BodyArts;
import dev.kultivator.technique.arts.FireArts;
import dev.kultivator.technique.arts.IceArts;
import dev.kultivator.technique.arts.ImmortalArts;
import dev.kultivator.technique.arts.LifeArts;
import dev.kultivator.technique.arts.LightningArts;
import dev.kultivator.technique.arts.ShadowArts;
import dev.kultivator.technique.arts.SpaceArts;
import dev.kultivator.technique.arts.SwordArts;
import dev.kultivator.technique.arts.UniversalArts;
import dev.kultivator.technique.spec.dsl.Anchor;
import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.DShape;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Pose;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.run.SpecialTechnique;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * Every technique spec and special handler (DESIGN 22.2.3). Built once from {@code Kultivator.onInitialize} (both
 * physical sides; never from the client initializer, which would double-register in singleplayer): the 23 legacy specs
 * first, then each {@code *Arts.register(Sink)} in a fixed order, then {@link #freeze()}. A duplicate id or net id, a
 * net id outside the module's block, a malformed id or a late registration logs an error and refuses the spec; startup
 * never crashes.
 */
public final class TechniqueRegistry {

	/** What a content module registers through: specs and {@code SpecialTechnique} handlers. */
	public interface Sink {
		void add(TechniqueSpec spec);

		void special(String handlerId, SpecialTechnique handler);
	}

	/** Spec ids: {@code [a-z0-9_]{1,48}} (22.2.2 says 32, widened to the saved-id length so the catalogue id tribulation_thunder_emperor_array fits). */
	public static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");
	/** Ids kept in saved data (22.2.4): a little wider, so old or future ids are never dropped. */
	public static final Pattern SAVED_ID = Pattern.compile("[a-z0-9_]{1,48}");
	/** At most this many technique ids are kept per holder (learned set, cooldown keys). */
	public static final int MAX_SAVED_IDS = 512;

	private static final Map<String, TechniqueSpec> BY_ID = new LinkedHashMap<>();
	private static final Map<Integer, TechniqueSpec> BY_NET = new HashMap<>();
	private static final Map<String, SpecialTechnique> SPECIALS = new HashMap<>();
	private static List<TechniqueSpec> all = List.of();
	private static boolean initialized;
	private static boolean frozen;

	/** Builds the registry once (later calls do nothing). */
	public static synchronized void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		module("legacy", 0, Technique.values().length - 1, sink -> {
			for (TechniqueSpec spec : LegacyArts.specs()) {
				sink.add(spec);
			}
		});
		module("UniversalArts", 100, 199, UniversalArts::register);
		module("SwordArts", 200, 299, SwordArts::register);
		module("FireArts", 300, 399, FireArts::register);
		module("IceArts", 400, 499, IceArts::register);
		module("LightningArts", 500, 599, LightningArts::register);
		module("BodyArts", 600, 699, BodyArts::register);
		module("LifeArts", 700, 799, LifeArts::register);
		module("ShadowArts", 800, 899, ShadowArts::register);
		module("SpaceArts", 900, 999, SpaceArts::register);
		module("ImmortalArts", 1000, 1099, ImmortalArts::register);
		// Phase 9 (DESIGN 26.14 item 15): the three self-created art slots own_art_1..3.
		module("CiptaanArts", 1100, 1199, dev.kultivator.batin.CiptaanArts::register);
		// Phase 10b (DESIGN 29.3.1): the four Titah Langit decrees titah_pedang..titah_gunung, cast only by the seal.
		module("TitahArts", 1200, 1299, dev.kultivator.pusakalangit.TitahArts::register);
		freeze();
		Kultivator.LOGGER.info("Kultivator: {} technique specs ({} for the player), {} special handlers", all.size(), playerTotal(), SPECIALS.size());
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) {
			// DESIGN 27.1 S2: phases nobody sends, ignored template parameters, missing models (dev only; also in "uji teknik audit")
			int warned = 0;
			for (TechniqueSpec spec : all) {
				for (String warning : dev.kultivator.technique.spec.run.SpecLint.check(spec)) {
					Kultivator.LOGGER.warn("Kultivator: technique {}: {}", spec.id(), warning);
					warned++;
				}
			}
			if (warned > 0) {
				Kultivator.LOGGER.warn("Kultivator: {} technique spec warning(s) (DESIGN 27.1 S2)", warned);
			}
		}
	}

	/** Runs one module's registration with its own net id block; a crash in a module only loses that module's specs. */
	private static void module(String name, int minNet, int maxNet, Consumer<Sink> registration) {
		Sink sink = new Sink() {
			@Override
			public void add(TechniqueSpec spec) {
				if (spec.netId() < minNet || spec.netId() > maxNet) {
					Kultivator.LOGGER.error("Kultivator: {} registered {} with net id {} outside its block {}-{}; refused", name, spec.id(),
							spec.netId(), minNet, maxNet);
					return;
				}
				register(spec);
			}

			@Override
			public void special(String handlerId, SpecialTechnique handler) {
				registerSpecial(handlerId, handler);
			}
		};
		try {
			registration.accept(sink);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator: technique module {} failed to register", name, exception);
		}
	}

	/** Adds one spec; refused (logged) when late, malformed or duplicate. True when added. */
	public static synchronized boolean register(TechniqueSpec spec) {
		if (frozen) {
			Kultivator.LOGGER.error("Kultivator: technique {} registered after the registry froze; refused", spec.id());
			return false;
		}
		if (!ID.matcher(spec.id()).matches()) {
			Kultivator.LOGGER.error("Kultivator: technique id '{}' is not 1-48 characters of a-z, 0-9 and _; refused", spec.id());
			return false;
		}
		if (BY_ID.containsKey(spec.id())) {
			Kultivator.LOGGER.error("Kultivator: duplicate technique id {}; refused", spec.id());
			return false;
		}
		if (BY_NET.containsKey(spec.netId())) {
			Kultivator.LOGGER.error("Kultivator: duplicate technique net id {} ({} and {}); refused", spec.netId(),
					BY_NET.get(spec.netId()).id(), spec.id());
			return false;
		}
		BY_ID.put(spec.id(), spec);
		BY_NET.put(spec.netId(), spec);
		return true;
	}

	/** Adds a special handler; refused (logged) when late or duplicate. */
	public static synchronized boolean registerSpecial(String handlerId, SpecialTechnique handler) {
		if (frozen) {
			Kultivator.LOGGER.error("Kultivator: special handler {} registered after the registry froze; refused", handlerId);
			return false;
		}
		if (handlerId == null || handlerId.isEmpty() || handler == null || SPECIALS.containsKey(handlerId)) {
			Kultivator.LOGGER.error("Kultivator: duplicate or empty special handler {}; refused", handlerId);
			return false;
		}
		SPECIALS.put(handlerId, handler);
		return true;
	}

	/** Ends registration; the catalogue order (legacy first, then the modules in init order) is kept. */
	public static synchronized void freeze() {
		frozen = true;
		all = Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
	}

	public static boolean frozen() {
		return frozen;
	}

	// ================================================================== lookups

	public static @Nullable TechniqueSpec byId(@Nullable String id) {
		return id == null ? null : BY_ID.get(id);
	}

	public static @Nullable TechniqueSpec byNet(int netId) {
		return BY_NET.get(netId);
	}

	/** Every spec in catalogue order (legacy first). */
	public static List<TechniqueSpec> all() {
		return all;
	}

	/** Specs of one Dao (null = universal), catalogue order. */
	public static List<TechniqueSpec> ofDao(@Nullable Dao dao) {
		List<TechniqueSpec> list = new ArrayList<>();
		for (TechniqueSpec spec : all) {
			if (spec.dao() == dao) {
				list.add(spec);
			}
		}
		return list;
	}

	public static List<TechniqueSpec> ofGrade(TechniqueGrade grade) {
		List<TechniqueSpec> list = new ArrayList<>();
		for (TechniqueSpec spec : all) {
			if (spec.grade() == grade) {
				list.add(spec);
			}
		}
		return list;
	}

	/** The spec of a legacy technique (always available, also before {@link #init}). */
	public static TechniqueSpec legacy(Technique technique) {
		return LegacyArts.specs()[technique.ordinal()];
	}

	public static @Nullable SpecialTechnique special(@Nullable String handlerId) {
		return handlerId == null ? null : SPECIALS.get(handlerId);
	}

	/** Specs the player can learn (everything but disciple-only): 168 with the full catalogue. */
	public static int playerTotal() {
		int count = 0;
		for (TechniqueSpec spec : all) {
			if (spec.playerVisible()) {
				count++;
			}
		}
		return count;
	}

	/** True when {@code id} may be kept in saved data (22.2.4). */
	public static boolean savedIdOk(@Nullable String id) {
		return id != null && SAVED_ID.matcher(id).matches();
	}

	// ================================================================== the 23 legacy specs (DESIGN 22.10)

	/**
	 * One spec per {@link Technique} constant: net id = ordinal, the grade and type of 22.10, the old qi and cooldown as
	 * base values, the routes of DESIGN 8, the KH shapes and the level 3/4 bonuses. Their behaviour stays the old cast code,
	 * called by P6c-mesin's {@code LegacyArt} with a {@code CastContext}; the timelines are therefore empty.
	 */
	private static final class LegacyArts {
		private static final TechniqueSpec[] SPECS = build();

		static TechniqueSpec[] specs() {
			return SPECS;
		}

		private static TechniqueSpec[] build() {
			EnumMap<Technique, TechniqueSpec> map = new EnumMap<>(Technique.class);
			legacy(map, Technique.CLOUD_STEP, TechniqueGrade.HUANG, 0, TechniqueType.PASIF, false, b -> b
					.perfection(Bonus.extraCount(1))
					.peak(Bonus.special("landing_ring", 0.3D)));
			legacy(map, Technique.QI_PALM, TechniqueGrade.HUANG, 0, TechniqueType.SERANG, true, b -> b
					.kehancuran(DShape.bowl(3))
					.perfection(Bonus.extraCount(2, 0.6D))
					.peak(Bonus.echo(0.4D)));
			legacy(map, Technique.FLYING_SWORD, TechniqueGrade.HUANG, 2, TechniqueType.GERAK, false, b -> b
					.perfection(Bonus.special("flight_speed", 0.25D))
					.peak(Bonus.special("sword_ram", 0.5D)));
			legacy(map, Technique.DIVINE_SENSE, TechniqueGrade.XUAN, 1, TechniqueType.INDRA, false, b -> b
					.perfection(Bonus.durationTicks(120))
					.peak(Bonus.payload(mark(dev.kultivator.technique.spec.dsl.Mark.vulnerable(0.10D), 200))));
			legacy(map, Technique.BUDDHA_PALM, TechniqueGrade.XUAN, 1, TechniqueType.AREA, true, b -> b
					.kehancuran(DShape.disc(6, 2))
					.perfection(Bonus.area(0.25D))
					.peak(Bonus.extraCast(2, 0.6D, 10)));
			legacy(map, Technique.DHARMA_FORM, TechniqueGrade.DI, 0, TechniqueType.UBAH, false, b -> b
					.perfection(Bonus.durationTicks(100))
					.peak(Bonus.special("golden_arc", 0.8D)));
			legacy(map, Technique.DAO_DOMAIN, TechniqueGrade.DI, 2, TechniqueType.WILAYAH, false, b -> b
					// the domain rides the caster: its first pulse that hurts a monster cracks a ring of ground around them
					.kehancuran(DShape.ring(10, 5, 1))
					.perfection(Bonus.area(0.20D))
					.peak(Bonus.special("pulse_10", 10.0D)));
			legacy(map, Technique.SWORD_QI, TechniqueGrade.XUAN, 0, TechniqueType.SERANG, true, b -> b
					.kehancuran(DShape.trench(16, 1, 2).each())
					.perfection(Bonus.extraCount(1))
					.peak(Bonus.echo(0.4D)));
			legacy(map, Technique.THOUSAND_SWORDS, TechniqueGrade.XUAN, 2, TechniqueType.AREA, true, b -> b
					.kehancuran(DShape.scatter(1).times(12))
					.perfection(Bonus.extraCount(2))
					.peak(Bonus.steps(atEnd().shape(target().pick(Shape.Target.Pick.STRONGEST)).hit(damage(3.0D)))));
			legacy(map, Technique.FLAME_PALM, TechniqueGrade.XUAN, 0, TechniqueType.AREA, true, b -> b
					.kehancuran(DShape.fan(7, 30, 1))
					.perfection(Bonus.special("ignite_dot", 1.0D))
					.peak(Bonus.echo(0.4D)));
			legacy(map, Technique.PHOENIX_FLAME, TechniqueGrade.XUAN, 2, TechniqueType.SERANG, true, b -> b
					.kehancuran(DShape.trench(24, 2, 1).each())
					.perfection(Bonus.extraCount(1))
					.peak(Bonus.echo(0.4D)));
			legacy(map, Technique.FROST_LOTUS, TechniqueGrade.XUAN, 0, TechniqueType.KENDALI, true, b -> b
					.kehancuran(DShape.ring(6, 4, 1))
					.perfection(Bonus.payload(dev.kultivator.technique.spec.dsl.Dsl.freeze(40)))
					.peak(Bonus.steps(at(20).shape(wave().r1Previous(0.0D).speed(1.5D)).hit(damage(0.5D).shatter()))));
			legacy(map, Technique.GLACIER_PRISON, TechniqueGrade.XUAN, 2, TechniqueType.KENDALI, true, b -> b
					.bindLook(dev.kultivator.technique.spec.run.BindKind.FREEZE)
					// a shatter crater (carving only, never ice blocks) under the sealed target when the strike lands
					.kehancuran(DShape.bowl(3))
					.perfection(Bonus.durationTicks(40))
					.peak(Bonus.steps(atEnd().shape(recall()).hit(damage(1.0D).shatter()))));
			legacy(map, Technique.THUNDER_PALM, TechniqueGrade.XUAN, 0, TechniqueType.SERANG, true, b -> b
					.kehancuran(DShape.scatter(1).each())
					.perfection(Bonus.chain(2))
					.peak(Bonus.echo(0.4D)));
			legacy(map, Technique.HEAVENLY_THUNDER, TechniqueGrade.XUAN, 2, TechniqueType.AREA, true, b -> b
					.kehancuran(DShape.scatter(2).times(12))
					.perfection(Bonus.extraCount(4))
					.peak(Bonus.steps(at(50).shape(target().pick(Shape.Target.Pick.STRONGEST)).hit(damage(4.0D)))));
			legacy(map, Technique.IRON_BODY, TechniqueGrade.XUAN, 0, TechniqueType.LINDUNG, true, b -> b
					.perfection(Bonus.durationTicks(100))
					.peak(Bonus.steps(atEnd().shape(nova().radius(4)).hit(damage(1.0D), knock(0.8D)))));
			legacy(map, Technique.MOUNTAIN_STOMP, TechniqueGrade.XUAN, 2, TechniqueType.AREA, true, b -> b
					.kehancuran(DShape.disc(7, 2).nat())
					.perfection(Bonus.extraWave(1))
					.peak(Bonus.echo(0.4D)));
			legacy(map, Technique.SPRING_OF_LIFE, TechniqueGrade.XUAN, 0, TechniqueType.PULIH, true, b -> b
					.perfection(Bonus.selfPayload(cleanse()))
					.peak(Bonus.selfPayload(buff(Payload.BuffKind.REGENERATION, 2, 200))));
			legacy(map, Technique.LIFE_DRAIN, TechniqueGrade.XUAN, 2, TechniqueType.AREA, true, b -> b
					.kehancuran(DShape.wither(8))
					.perfection(Bonus.payload(drain(0.6D)))
					.peak(Bonus.echo(0.4D), Bonus.payload(drain(0.0D).qi(0.1D))));
			legacy(map, Technique.SHADOW_STEP, TechniqueGrade.XUAN, 0, TechniqueType.GERAK, true, b -> b
					.perfection(Bonus.durationTicks(20))
					.peak(Bonus.steps(at(10).shape(nova().radius(2).at(Anchor.AFTERIMAGE)).hit(damage(0.5D)))));
			legacy(map, Technique.GALE, TechniqueGrade.XUAN, 2, TechniqueType.KENDALI, true, b -> b
					.kehancuran(DShape.strip(10))
					.perfection(Bonus.durationTicks(40))
					.peak(Bonus.echo(0.4D)));
			legacy(map, Technique.SPACE_BLINK, TechniqueGrade.XUAN, 0, TechniqueType.GERAK, true, b -> b
					.perfection(Bonus.cooldown(0.2D))
					.peak(Bonus.steps(at(0).shape(nova().radius(3).at(Anchor.ORIGIN)).hit(damage(0.5D)))));
			legacy(map, Technique.SPACE_PRISON, TechniqueGrade.XUAN, 2, TechniqueType.KENDALI, true, b -> b
					.kehancuran(DShape.pit(3, 3).atRelease())
					.perfection(Bonus.durationTicks(20))
					.peak(Bonus.steps(atEnd().shape(recall()).hit(damage(1.5D)))));
			TechniqueSpec[] specs = new TechniqueSpec[Technique.values().length];
			for (Technique technique : Technique.values()) {
				specs[technique.ordinal()] = map.get(technique);
			}
			return specs;
		}

		private static void legacy(Map<Technique, TechniqueSpec> map, Technique technique, TechniqueGrade grade, int sub,
				TechniqueType type, boolean teachable, Consumer<TechniqueSpec.Builder> extra) {
			TechniqueSpec.Builder builder = TechniqueSpec.builder(technique.id(), technique.ordinal(), technique.dao())
					.grade(grade, sub)
					.type(type)
					.realm(technique.minRealm())
					.qi(technique.qiCost())
					.cooldownTicks(technique.cooldownTicks())
					.comprehension(technique.minComprehension())
					.flag(teachable ? TechniqueSpec.Flag.TEACHABLE : TechniqueSpec.Flag.NO_TEACH)
					.legacy(technique);
			legacyPose(builder, technique);
			switch (technique.unlock()) {
				case REALM -> builder.route(Route.realm());
				case DAO -> builder.route(Route.dao());
				case BOOK -> builder.route(Route.book(bookOf(technique)));
			}
			extra.accept(builder);
			map.put(technique, builder.build());
		}

		/** The cast pose of each legacy technique (DESIGN 27.2.4 table 6.12): what the body does when it is cast. */
		private static void legacyPose(TechniqueSpec.Builder builder, Technique technique) {
			switch (technique) {
				case QI_PALM, FLAME_PALM -> builder.pose(Pose.PALM_THRUST);
				case THUNDER_PALM -> builder.pose(Pose.SKY_RAISE).pose(Phase.IMPACT, Pose.PALM_THRUST);
				case MOUNTAIN_STOMP -> builder.pose(Pose.STOMP);
				case DAO_DOMAIN -> builder.pose(Pose.ARMS_SPREAD);
				case SPRING_OF_LIFE -> builder.pose(Pose.MEDITATE_FLOAT);
				case LIFE_DRAIN -> builder.pose(Pose.FINGER_POINT);
				case GALE -> builder.pose(Pose.FAN_SWING);
				case SPACE_PRISON, FROST_LOTUS -> builder.pose(Pose.SEAL);
				case BUDDHA_PALM -> builder.pose(Pose.SKY_RAISE).pose(Phase.IMPACT, Pose.OVERHEAD_SLAM);
				case THOUSAND_SWORDS -> builder.pose(Pose.SWORD_SKY);
				case SWORD_QI -> builder.pose(Pose.SWORD_SLASH);
				case PHOENIX_FLAME, GLACIER_PRISON -> builder.pose(Pose.TWIN_PALM);
				case HEAVENLY_THUNDER -> builder.pose(Pose.SKY_RAISE);
				case IRON_BODY -> builder.pose(Pose.GATE_FLEX);
				case DHARMA_FORM -> builder.pose(Pose.SUMMON_RAISE);
				case DIVINE_SENSE -> builder.pose(Pose.SEAL);
				case SHADOW_STEP, SPACE_BLINK -> builder.pose(Pose.DASH_LEAN);
				case CLOUD_STEP, FLYING_SWORD -> builder.pose(Pose.NONE);
				default -> {
				}
			}
		}

		/** The enchantments of DESIGN 7 that teach each legacy book technique. */
		private static String[] bookOf(Technique technique) {
			return switch (technique) {
				case BUDDHA_PALM -> new String[]{"minecraft:smite"};
				case THOUSAND_SWORDS -> new String[]{"minecraft:sharpness", "minecraft:sweeping_edge"};
				case PHOENIX_FLAME -> new String[]{"minecraft:fire_aspect", "minecraft:flame"};
				case GLACIER_PRISON -> new String[]{"minecraft:frost_walker"};
				case HEAVENLY_THUNDER -> new String[]{"minecraft:channeling"};
				case MOUNTAIN_STOMP -> new String[]{"minecraft:protection", "minecraft:thorns"};
				case LIFE_DRAIN -> new String[]{"minecraft:mending"};
				case GALE -> new String[]{"minecraft:swift_sneak", "minecraft:feather_falling"};
				case SPACE_PRISON -> new String[]{"minecraft:infinity", "minecraft:loyalty"};
				default -> new String[0];
			};
		}
	}

	private TechniqueRegistry() {
	}
}
