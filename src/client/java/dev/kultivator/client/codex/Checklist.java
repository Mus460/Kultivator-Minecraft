package dev.kultivator.client.codex;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.Cond;
import dev.kultivator.technique.spec.dsl.Feat;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.technique.spec.run.FeatTracker;
import dev.kultivator.technique.spec.run.Unlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * The acquisition checklist of the detail slip (DESIGN 22.12.4 item 7) and the texts of routes, feats, conditions and
 * mastery bonuses. The server's own {@code Unlocks.checklist} is used when it returns lines; otherwise the codex builds
 * the same list from the spec's gates and routes (realm and Dao, comprehension, book pools, jade slip, pavilion,
 * inheritance, feat progress, prerequisites, Gerbang Dewa).
 */
public final class Checklist {

	private static final String P = "kultivator.codex.check.";

	public static List<Unlocks.Line> lines(CultivationData data, TechniqueSpec spec) {
		List<Unlocks.Line> server;
		try {
			server = Unlocks.checklist(data, spec);
		} catch (RuntimeException exception) {
			server = List.of();
		}
		if (server != null && !server.isEmpty()) {
			return server;
		}
		List<Unlocks.Line> lines = new ArrayList<>();
		lines.add(new Unlocks.Line(Component.translatable(P + "realm", SpecInfo.realmName(spec)), SpecInfo.realmOk(data, spec), 0, 0));
		if (spec.dao() != null) {
			lines.add(new Unlocks.Line(Component.translatable(P + "dao", spec.dao().displayName()), SpecInfo.daoOk(data, spec), 0, 0));
		}
		if (spec.minComprehension() > 0.0D) {
			int have = (int) Math.floor(SpecInfo.comprehension(data, spec));
			int goal = (int) Math.ceil(spec.minComprehension());
			lines.add(new Unlocks.Line(Component.translatable(P + "comp", SpecInfo.daoName(spec), goal), SpecInfo.compOk(data, spec), have, goal));
		}
		for (TechniqueSpec.Prereq prereq : spec.prereqs()) {
			TechniqueSpec other = TechniqueRegistry.byId(prereq.id());
			Component name = other == null ? Component.literal(prereq.id()) : other.displayName();
			lines.add(new Unlocks.Line(Component.translatable(P + "prereq", name, SpecInfo.masteryName(prereq.level())), SpecInfo.prereqOk(data, prereq),
					0, 0));
		}
		if (spec.gate() != TechniqueSpec.Gate.NONE) {
			lines.add(new Unlocks.Line(Component.translatable(spec.gate() == TechniqueSpec.Gate.DEWA_II ? P + "gate2" : P + "gate1"),
					SpecInfo.gateOk(data, spec), 0, 0));
		}
		for (Route route : spec.routes()) {
			lines.add(routeLine(data, spec, route));
		}
		if (spec.slipAllowed()) {
			lines.add(new Unlocks.Line(Component.translatable(P + "slip"), data.knows(spec.id()), 0, 0));
		}
		return lines;
	}

	private static Unlocks.Line routeLine(CultivationData data, TechniqueSpec spec, Route route) {
		boolean known = data.knows(spec.id());
		return switch (route.kind()) {
			case REALM -> new Unlocks.Line(Component.translatable(P + "route.realm", SpecInfo.realmName(spec)), known, 0, 0);
			case DAO -> new Unlocks.Line(Component.translatable(P + "route.dao", SpecInfo.daoName(spec)), known, 0, 0);
			case COMP -> {
				int have = (int) Math.floor(SpecInfo.comprehension(data, spec));
				yield new Unlocks.Line(Component.translatable(P + "route.comp", SpecInfo.daoName(spec), route.n()), known || have >= route.n(), have,
						route.n());
			}
			case BOOK -> new Unlocks.Line(Component.translatable(P + "route.book", books(route.keys())), known, 0, 0);
			case PAV -> new Unlocks.Line(Component.translatable(P + "route.pav", SpecInfo.number(spec.grade().pavilionPrice()),
					spec.grade().pavilionHallLevel()), known, 0, 0);
			case FEAT -> {
				int progress = FeatTracker.progress(data, route);
				int goal = route.feat() == null ? Math.max(1, route.n()) : route.feat().goal();
				yield new Unlocks.Line(route.feat() == null ? Component.literal(route.key()) : feat(route.feat(), progress), progress >= goal || known,
						progress, goal);
			}
			case ITEM -> new Unlocks.Line(Component.translatable(P + "route.item", itemName(route.key())), known, 0, 0);
			case SCR -> new Unlocks.Line(Component.translatable(P + "route.scr", route.n()), known, 0, 0);
			case INH -> new Unlocks.Line(Component.translatable(P + "route.inh", theme(route.key())), known, 0, 0);
			case ENLIGHT -> new Unlocks.Line(Component.translatable(P + "route.enlight"), known, 0, 0);
			case PAIR -> new Unlocks.Line(Component.translatable(P + "route.pair", route.n()), known, 0, 0);
			case KNOWN -> {
				int count = SpecInfo.learnedCount(data);
				yield new Unlocks.Line(Component.translatable(P + "route.known", route.n()), count >= route.n(), count, route.n());
			}
			case SECOND_DAO -> new Unlocks.Line(Component.translatable(P + "route.second_dao", route.n()), known, 0, 0);
		};
	}

	/** The enchantments of book pools, joined with "or". */
	static Component books(List<String> ids) {
		MutableComponent joined = Component.empty();
		for (int i = 0; i < ids.size(); i++) {
			String id = ids.get(i);
			String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
			if (i > 0) {
				joined.append(Component.translatable("kultivator.codex.or"));
			}
			joined.append(Component.translatable("enchantment.minecraft." + path).withColor(0xA040C0));
		}
		return joined;
	}

	private static Component itemName(String key) {
		String path = key.contains(":") ? key.substring(0, key.indexOf(':')) : key;
		return Component.translatable("item.kultivator." + path);
	}

	private static Component theme(String theme) {
		dev.kultivator.data.Dao dao = dev.kultivator.data.Dao.byId(theme);
		return dao == null ? Component.translatable("kultivator.codex.universal") : dao.displayName();
	}

	/** "Bunuh 12/30 dengan Pedang (malam bulan purnama)" and the other feat kinds of 22.8.9. */
	public static Component feat(Feat feat, int progress) {
		Component who = feat.dao() != null ? feat.dao().displayName()
				: !feat.specId().isEmpty() && TechniqueRegistry.byId(feat.specId()) != null ? TechniqueRegistry.byId(feat.specId()).displayName()
				: Component.translatable("kultivator.codex.feat.any");
		Component cond = condSuffix(feat.cond());
		return switch (feat.kind()) {
			case KILLS_WITH -> Component.translatable("kultivator.codex.feat.kills_with", progress, feat.goal(), who, mobs(feat.mobs()), cond);
			case CASTS_WITH -> Component.translatable("kultivator.codex.feat.casts_with", progress, feat.goal(), who, cond);
			case FIRST_KILL -> Component.translatable(feat.all() ? "kultivator.codex.feat.first_kill_all" : "kultivator.codex.feat.first_kill",
					mobs(feat.mobs()), who, progress, feat.goal());
			case MEDITATE_AT -> Component.translatable("kultivator.codex.feat.meditate_at", progress, feat.goal(), cond(feat.cond()));
			case ALCHEMY_COUNT -> Component.translatable("kultivator.codex.feat.alchemy", progress, feat.goal());
			case TRIB -> {
				if (feat.quality() != null && feat.average()) {
					yield Component.translatable("kultivator.codex.feat.trib_average", feat.quality().displayName());
				}
				if (feat.quality() != null) {
					Component realm = feat.realm() == null ? Component.empty() : feat.realm().displayName();
					yield Component.translatable("kultivator.codex.feat.trib_quality", realm, feat.quality().displayName());
				}
				yield Component.translatable("kultivator.codex.feat.trib_strikes", progress, feat.goal());
			}
			case HEART_DEMON_WIN -> Component.translatable("kultivator.codex.feat.heart_demon",
					feat.realm() == null ? Component.empty() : feat.realm().displayName());
			case DEVIATION_RECOVERED -> Component.translatable("kultivator.codex.feat.deviation",
					feat.realm() == null ? Component.empty() : feat.realm().displayName());
		};
	}

	private static Component mobs(List<String> mobs) {
		if (mobs.isEmpty()) {
			return Component.translatable("kultivator.codex.feat.monsters");
		}
		MutableComponent joined = Component.empty();
		for (int i = 0; i < mobs.size(); i++) {
			String id = mobs.get(i);
			if (i > 0) {
				joined.append(Component.literal(", "));
			}
			if (id.startsWith("#")) {
				String tag = id.substring(1);
				joined.append(Component.literal(tag.contains(":") ? tag.substring(tag.indexOf(':') + 1).replace('_', ' ') : tag.replace('_', ' ')));
			} else {
				String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
				joined.append(Component.translatable("entity.minecraft." + path));
			}
		}
		return joined;
	}

	private static Component condSuffix(Cond cond) {
		if (cond.kind() == Cond.Kind.NONE) {
			return Component.empty();
		}
		return Component.translatable("kultivator.codex.cond.suffix", cond(cond));
	}

	/** A condition in words ("malam bulan purnama", "di Nether", "langit terbuka & malam"). */
	public static Component cond(Cond cond) {
		String name = cond.kind().name().toLowerCase(java.util.Locale.ROOT);
		return switch (cond.kind()) {
			case TARGET_LOW_HP, CASTER_LOW_HP -> Component.translatable("kultivator.codex.cond." + name, Math.round(cond.a() * 100.0D));
			case NEAR_ANCHOR, ALTITUDE, HIT_BEFORE -> Component.translatable("kultivator.codex.cond." + name, Math.round(cond.a()));
			case TARGET_TOUGH -> Component.translatable("kultivator.codex.cond." + name, SpecInfo.decimal(cond.a(), 1));
			case TRIB_STRIKES -> Component.translatable("kultivator.codex.cond." + name, SpecInfo.decimal(cond.a() * 100.0D, 1),
					SpecInfo.decimal(cond.b() * 100.0D, 0));
			case BIOME, DIMENSION, SITE -> Component.translatable("kultivator.codex.cond." + name, pretty(cond.key()));
			case ALL, ANY -> {
				MutableComponent joined = Component.empty();
				for (int i = 0; i < cond.parts().size(); i++) {
					if (i > 0) {
						joined.append(Component.translatable(cond.kind() == Cond.Kind.ALL ? "kultivator.codex.cond.and" : "kultivator.codex.cond.or"));
					}
					joined.append(cond(cond.parts().get(i)));
				}
				yield joined;
			}
			case NOT -> Component.translatable("kultivator.codex.cond.not", cond.parts().isEmpty() ? Component.empty() : cond(cond.parts().get(0)));
			default -> Component.translatable("kultivator.codex.cond." + name);
		};
	}

	private static String pretty(String id) {
		String bare = id.startsWith("#") ? id.substring(1) : id;
		String path = bare.contains(":") ? bare.substring(bare.indexOf(':') + 1) : bare;
		return path.replace('_', ' ').replace('/', ' ');
	}

	/** A mastery bonus in words ("+2 jumlah", "Area +25%", "Gema 40%"...). */
	public static Component bonus(Bonus bonus) {
		return switch (bonus.kind()) {
			case EXTRA_COUNT -> Component.translatable("kultivator.codex.bonus.extra_count", bonus.count());
			case EXTRA_WAVE -> Component.translatable("kultivator.codex.bonus.extra_wave", bonus.count());
			case AREA -> Component.translatable("kultivator.codex.bonus.area", Math.round(bonus.value() * 100.0D));
			case DURATION -> Component.translatable("kultivator.codex.bonus.duration", Math.round(bonus.value() * 100.0D));
			case DURATION_TICKS -> Component.translatable("kultivator.codex.bonus.duration_ticks", SpecInfo.seconds(bonus.ticks()));
			case PAYLOAD -> Component.translatable("kultivator.codex.bonus.payload");
			case CHAIN -> Component.translatable("kultivator.codex.bonus.chain", bonus.count());
			case COOLDOWN -> Component.translatable("kultivator.codex.bonus.cooldown", Math.round(bonus.value() * 100.0D));
			case EXTRA_CAST -> Component.translatable("kultivator.codex.bonus.extra_cast", bonus.count(), Math.round(bonus.value() * 100.0D));
			case ECHO -> Component.translatable("kultivator.codex.bonus.echo", Math.round(bonus.value() * 100.0D));
			case STEPS -> Component.translatable("kultivator.codex.bonus.steps");
			case SPECIAL -> Component.translatable("kultivator.codex.bonus.special");
		};
	}

	/** The comprehension gate of mastery level 3 or 4 in words. */
	public static Component masteryGate(TechniqueSpec spec, int level) {
		if (spec.universal()) {
			return Component.translatable("kultivator.codex.mastery.gate_realm",
					(level >= 4 ? TechniquePower.MASTERY_REALM_4 : TechniquePower.MASTERY_REALM_3).displayName());
		}
		return Component.translatable("kultivator.codex.mastery.gate_comp", SpecInfo.daoName(spec),
				Math.round(level >= 4 ? TechniquePower.MASTERY_GATE_4 : TechniquePower.MASTERY_GATE_3));
	}

	private Checklist() {
	}
}
