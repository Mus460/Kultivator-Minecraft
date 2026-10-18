package dev.kultivator.client.codex;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.Step;
import dev.kultivator.technique.spec.dsl.Val;
import dev.kultivator.technique.spec.run.Mastery;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What the codex, the wheel and the HUD know about a spec for the local player, computed on the synced data with the
 * same {@link TechniquePower} functions the server uses (DESIGN 22.12.4): the gates (realm, Dao, comprehension, Gerbang
 * Dewa, prerequisites), the state (learned / learnable / locked), the lock reason, mastery, qi and cooldown after
 * mastery and the realm gap, the live damage, area, duration and count, colours, and number formatting
 * ("8,4 rb" / "8.4k").
 */
public final class SpecInfo {

	public enum State { LEARNED, LEARNABLE, LOCKED }

	/** Live numbers of one spec. {@code area}/{@code areaBase} are NaN when it has no sized shape. */
	public record Numbers(double coef, int repeats, double damage, String areaKey, double areaBase, double area, int duration, int count,
			int qi, int cooldown, double bossShare) {
	}

	public static final int JADE_INK = 0xFF2E8963;
	public static final double DEWA_COMPREHENSION = 90.0D;

	// ================================================================== gates and state

	public static boolean realmOk(CultivationData data, TechniqueSpec spec) {
		Rank rank = data.rank();
		int realm = Integer.compare(rank.realm().ordinal(), spec.minRealm().ordinal());
		if (realm != 0) {
			return realm > 0;
		}
		if (rank.layer() != spec.minLayer()) {
			return rank.layer() > spec.minLayer();
		}
		return rank.stage() >= spec.minStage();
	}

	public static boolean daoOk(CultivationData data, TechniqueSpec spec) {
		return spec.dao() == null || spec.dao() == data.dao();
	}

	/** The comprehension that counts for {@code spec} (its Dao, or the main one for universal specs). */
	public static double comprehension(CultivationData data, TechniqueSpec spec) {
		return spec.dao() == null ? data.mainComprehension() : data.comprehension(spec.dao());
	}

	public static boolean compOk(CultivationData data, TechniqueSpec spec) {
		return spec.minComprehension() <= 0.0D || comprehension(data, spec) + 1.0E-6D >= spec.minComprehension();
	}

	/** Gerbang Dewa (22.8.8): Dewa Abadi Puncak, ascended, comprehension ≥ 90; or the phase-7 star route (9 / 18). */
	public static boolean gateOk(CultivationData data, TechniqueSpec spec) {
		if (spec.gate() == TechniqueSpec.Gate.NONE) {
			return true;
		}
		int stars = TechniquePower.playerStars(data);
		if (stars >= (spec.gate() == TechniqueSpec.Gate.DEWA_II ? 18 : 9)) {
			return true;
		}
		Rank rank = data.rank();
		return rank.realm() == Realm.TRUE_IMMORTAL && rank.stage() >= Rank.PEAK && data.ascended()
				&& comprehension(data, spec) + 1.0E-6D >= DEWA_COMPREHENSION;
	}

	public static boolean prereqOk(CultivationData data, TechniqueSpec.Prereq prereq) {
		return data.knows(prereq.id()) && Mastery.level(data, prereq.id()) >= prereq.level();
	}

	public static boolean prereqsOk(CultivationData data, TechniqueSpec spec) {
		for (TechniqueSpec.Prereq prereq : spec.prereqs()) {
			if (!prereqOk(data, prereq)) {
				return false;
			}
		}
		return true;
	}

	public static boolean gatesMet(CultivationData data, TechniqueSpec spec) {
		return realmOk(data, spec) && daoOk(data, spec) && compOk(data, spec) && gateOk(data, spec) && prereqsOk(data, spec);
	}

	public static State state(CultivationData data, TechniqueSpec spec) {
		if (data.knows(spec.id())) {
			return State.LEARNED;
		}
		return gatesMet(data, spec) ? State.LEARNABLE : State.LOCKED;
	}

	/** Learned and castable now by the gates (cooldown and qi aside); Cloud Step (PASIF) never is. */
	public static boolean castable(CultivationData data, TechniqueSpec spec) {
		return data.knows(spec.id()) && spec.type() != TechniqueType.PASIF && spec.playerVisible() && realmOk(data, spec) && daoOk(data, spec)
				&& gateOk(data, spec);
	}

	/** The castable specs, highest grade first (then catalogue order). */
	public static List<TechniqueSpec> castables(CultivationData data) {
		List<TechniqueSpec> list = new ArrayList<>();
		for (TechniqueSpec spec : data.learnedSpecs()) {
			if (castable(data, spec)) {
				list.add(spec);
			}
		}
		list.sort(Comparator.comparingInt((TechniqueSpec spec) -> -spec.grade().tier()));
		return list;
	}

	/** Every spec the player's codex shows (the disciple-only spec never does). */
	public static List<TechniqueSpec> visible() {
		List<TechniqueSpec> list = new ArrayList<>();
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (spec.playerVisible()) {
				list.add(spec);
			}
		}
		return list;
	}

	/** Learned player specs (the "Dikuasai x/168" counter). */
	public static int learnedCount(CultivationData data) {
		int count = 0;
		for (TechniqueSpec spec : data.learnedSpecs()) {
			if (spec.playerVisible()) {
				count++;
			}
		}
		return count;
	}

	/** Why the spec cannot be cast or learned now (the first gate that fails), or null when every gate is met. */
	public static @Nullable Component lockReason(CultivationData data, TechniqueSpec spec) {
		if (!realmOk(data, spec)) {
			return Component.translatable("kultivator.codex.lock.realm", realmName(spec));
		}
		if (!daoOk(data, spec) && spec.dao() != null) {
			return Component.translatable("kultivator.codex.lock.dao", spec.dao().displayName());
		}
		if (!compOk(data, spec)) {
			return Component.translatable("kultivator.codex.lock.comp", daoName(spec), number(spec.minComprehension()));
		}
		if (!gateOk(data, spec)) {
			return Component.translatable(spec.gate() == TechniqueSpec.Gate.DEWA_II ? "kultivator.codex.lock.gate2" : "kultivator.codex.lock.gate1");
		}
		for (TechniqueSpec.Prereq prereq : spec.prereqs()) {
			if (!prereqOk(data, prereq)) {
				TechniqueSpec other = TechniqueRegistry.byId(prereq.id());
				Component name = other == null ? Component.literal(prereq.id()) : other.displayName();
				return Component.translatable("kultivator.codex.lock.prereq", name, masteryName(prereq.level()));
			}
		}
		return null;
	}

	// ================================================================== names and colours

	/** "Inti Emas", "Kenaikan Abadi Tahap 5", "+ Akhir" for a stage gate, "Gerbang Dewa I" for Dewa specs. */
	public static Component realmName(TechniqueSpec spec) {
		MutableComponent text = spec.minRealm().layers() > 1
				? Component.translatable("kultivator.codex.realm_layer", spec.minRealm().displayName(), spec.minLayer() + 1)
				: spec.minRealm().displayName().copy();
		if (spec.minStage() > 0) {
			text = Component.translatable("kultivator.codex.realm_stage", text, new Rank(spec.minRealm(), spec.minLayer(), spec.minStage()).stageName());
		}
		return text.withColor(spec.minRealm().color());
	}

	/** The Dao's name, or "Universal". */
	public static Component daoName(TechniqueSpec spec) {
		return spec.dao() == null ? Component.translatable("kultivator.codex.universal") : spec.dao().displayName();
	}

	/** "Bumi Rendah". */
	public static Component gradeName(TechniqueSpec spec) {
		return Component.translatable("kultivator.codex.grade_sub", spec.grade().displayName(), TechniqueGrade.subName(spec.sub()));
	}

	/** The mastery level name 0-4 (Pemula ... Puncak Jurus). */
	public static Component masteryName(int level) {
		return Component.translatable("kultivator.codex.mastery." + Math.max(0, Math.min(4, level)));
	}

	/** Ink of the spec on parchment: its Dao's colour, or jade. */
	public static int ink(TechniqueSpec spec) {
		return spec.dao() == null ? JADE_INK : Palette.onPaper(spec.dao().color());
	}

	/** The Dao colour (opaque), or jade. */
	public static int tone(TechniqueSpec spec) {
		return spec.dao() == null ? Palette.JADE : Palette.opaque(spec.dao().color());
	}

	/** The grade ribbon colour; Dewa shimmers through the rainbow (still with "Kurangi gerak"). */
	public static int ribbon(TechniqueGrade grade) {
		if (grade == TechniqueGrade.DEWA && !EffectQuality.reduceMotion()) {
			return rainbow((Util.getMillis() % 6000L) / 6000.0F);
		}
		return Palette.opaque(grade.ribbon());
	}

	/** A soft rainbow colour at {@code hue} 0..1 (saturation 0.55, full brightness). */
	public static int rainbow(float hue) {
		float h = (hue - (float) Math.floor(hue)) * 6.0F;
		int sector = (int) h;
		float f = h - sector;
		float s = 0.55F;
		float p = 1.0F - s;
		float q = 1.0F - s * f;
		float t = 1.0F - s * (1.0F - f);
		float r;
		float g;
		float b;
		switch (sector) {
			case 0 -> { r = 1.0F; g = t; b = p; }
			case 1 -> { r = q; g = 1.0F; b = p; }
			case 2 -> { r = p; g = 1.0F; b = t; }
			case 3 -> { r = p; g = q; b = 1.0F; }
			case 4 -> { r = t; g = p; b = 1.0F; }
			default -> { r = 1.0F; g = p; b = q; }
		}
		return 0xFF000000 | Math.round(r * 255.0F) << 16 | Math.round(g * 255.0F) << 8 | Math.round(b * 255.0F);
	}

	// ================================================================== mastery, qi, cooldown

	public static int mastery(CultivationData data, TechniqueSpec spec) {
		return Mastery.level(data, spec.id());
	}

	public static int qi(CultivationData data, TechniqueSpec spec) {
		return TechniquePower.qi(spec, data, mastery(data, spec));
	}

	public static int cooldown(CultivationData data, TechniqueSpec spec) {
		return TechniquePower.cooldownTicks(spec, data, mastery(data, spec));
	}

	/** Ticks until the spec is ready again. */
	public static long cooldownLeft(TechniqueSpec spec) {
		return ClientCultivation.data().cooldownLeft(spec.id(), ClientCultivation.serverGameTime());
	}

	/** The qi left after paying for one cast is not negative. */
	public static boolean affordable(CultivationData data, TechniqueSpec spec) {
		return data.qi() + 1.0E-6D >= qi(data, spec);
	}

	// ================================================================== live numbers (22.12.4)

	/** Pre-6c coefficients of the legacy techniques (their timelines are code, not data). */
	private static double legacyCoef(Technique technique) {
		return switch (technique) {
			case QI_PALM, FLAME_PALM, FROST_LOTUS, THUNDER_PALM, LIFE_DRAIN -> 1.0D;
			case BUDDHA_PALM -> 3.0D;
			case SWORD_QI, GLACIER_PRISON, SPACE_PRISON -> 1.5D;
			case THOUSAND_SWORDS -> 0.6D;
			case PHOENIX_FLAME, HEAVENLY_THUNDER, MOUNTAIN_STOMP -> 2.0D;
			case GALE -> 0.8D;
			default -> 0.0D;
		};
	}

	/** {base, cap} and the label of the legacy techniques' main size (22.10). */
	private static double[] legacyArea(Technique technique) {
		return switch (technique) {
			case QI_PALM -> new double[]{12, 24, 1};
			case BUDDHA_PALM -> new double[]{5, 16, 0};
			case DIVINE_SENSE -> new double[]{48, 96, 0};
			case SWORD_QI -> new double[]{16, 48, 1};
			case THOUSAND_SWORDS -> new double[]{16, 32, 0};
			case FLAME_PALM -> new double[]{7, 20, 1};
			case PHOENIX_FLAME -> new double[]{24, 64, 1};
			case FROST_LOTUS -> new double[]{6, 18, 0};
			case GLACIER_PRISON, THUNDER_PALM -> new double[]{24, 48, 2};
			case HEAVENLY_THUNDER -> new double[]{16, 32, 0};
			case MOUNTAIN_STOMP -> new double[]{7, 20, 0};
			case SPRING_OF_LIFE, LIFE_DRAIN -> new double[]{8, 16, 0};
			case SHADOW_STEP -> new double[]{20, 32, 2};
			case GALE -> new double[]{10, 20, 0};
			case SPACE_BLINK -> new double[]{24, 64, 2};
			case SPACE_PRISON -> new double[]{12, 24, 0};
			default -> null;
		};
	}

	private static final String[] AREA_KEYS = {"kultivator.codex.num.radius", "kultivator.codex.num.length", "kultivator.codex.num.range"};

	public static Numbers numbers(CultivationData data, TechniqueSpec spec) {
		int mastery = mastery(data, spec);
		TechniquePower.Factors f = TechniquePower.factors(data, spec);
		double power = TechniquePower.power(data, spec);
		double coef = 0.0D;
		int repeats = 1;
		String areaKey = AREA_KEYS[0];
		double areaBase = Double.NaN;
		double area = Double.NaN;
		int duration = 0;
		int count = 0;
		if (spec.legacy() != null) {
			coef = legacyCoef(spec.legacy());
			double[] legacy = legacyArea(spec.legacy());
			if (legacy != null) {
				areaKey = AREA_KEYS[(int) legacy[2]];
				areaBase = legacy[0];
				area = TechniquePower.area(Val.of(legacy[0], legacy[1]), f, mastery, 0.0D);
			}
		} else {
			Scan scan = new Scan();
			for (Step step : spec.timeline()) {
				scan.step(step, 0);
			}
			coef = scan.coef;
			repeats = scan.repeats;
			if (scan.areaVal != null) {
				areaKey = scan.areaKey;
				areaBase = scan.areaVal.base();
				area = TechniquePower.area(scan.areaVal, f, mastery, scan.areaCap);
			}
			if (scan.durationVal != null) {
				duration = TechniquePower.duration(scan.durationVal, f, mastery, scan.durationCap);
			}
			if (scan.countVal != null) {
				count = TechniquePower.count(scan.countVal, f, scan.countCap);
			}
		}
		double damage = coef * power * TechniquePower.masteryDamage(mastery);
		return new Numbers(coef, repeats, damage, areaKey, areaBase, area, duration, count, qi(data, spec), cooldown(data, spec),
				TechniquePower.bossShare(data, spec.grade()));
	}

	/** Walks a timeline for its largest damage, its first sized shape, its first duration and its first count. */
	private static final class Scan {
		double coef;
		int repeats = 1;
		@Nullable Val areaVal;
		String areaKey = AREA_KEYS[0];
		double areaCap;
		@Nullable Val durationVal;
		double durationCap;
		@Nullable Val countVal;
		double countCap;

		void step(Step step, int depth) {
			if (depth > 3) {
				return;
			}
			for (Payload.Hit hit : step.hit()) {
				hit(hit, step.repeat());
			}
			shape(step.shape());
			for (Payload.Boon boon : step.self()) {
				if (boon instanceof Payload.Summon summon && countVal == null && summon.count().set()) {
					countVal = summon.count();
					countCap = TechniquePower.PHANTOMS_PER_CAST;
				}
				if (boon instanceof Payload.Buff buff && durationVal == null && buff.ticks().set()) {
					durationVal = buff.ticks();
					durationCap = TechniquePower.DOMAIN_DURATION_CAP_DEWA;
				}
				if (boon instanceof Payload.Transform transform && durationVal == null && transform.ticks().set()) {
					durationVal = transform.ticks();
					durationCap = TechniquePower.DOMAIN_DURATION_CAP_DEWA;
				}
			}
			for (List<Step> children : List.of(step.onHit(), step.onKill(), step.onEnd())) {
				for (Step child : children) {
					step(child, depth + 1);
				}
			}
		}

		void hit(Payload.Hit hit, int repeat) {
			if (hit instanceof Payload.ChanceHit chance) {
				hit = chance.payload();
			}
			if (hit instanceof Payload.Dmg dmg && dmg.coef() > coef) {
				coef = dmg.coef();
				repeats = Math.max(1, repeat);
			}
			if (hit instanceof Payload.Bind bind && durationVal == null && bind.ticks().set()) {
				durationVal = bind.ticks();
				durationCap = 200.0D;
			}
		}

		void shape(Shape shape) {
			boolean first = areaVal == null;
			switch (shape) {
				case Shape.Strike s -> area(first, s.range(), TechniquePower.STRIKE_RANGE_CAP, 2);
				case Shape.Cone s -> area(first, s.length(), TechniquePower.CONE_LENGTH_CAP, 1);
				case Shape.Beam s -> area(first, s.length(), TechniquePower.BEAM_LENGTH_CAP_DEWA, 1);
				case Shape.Front s -> area(first, s.length(), TechniquePower.FRONT_LENGTH_CAP, 1);
				case Shape.Nova s -> area(first, s.radius(), TechniquePower.NOVA_RADIUS_CAP, 0);
				case Shape.Wave s -> area(first, s.r1(), TechniquePower.WAVE_RADIUS_CAP, 0);
				case Shape.Zone s -> area(first, s.radius(), TechniquePower.ZONE_RADIUS_CAP_DEWA, 0);
				case Shape.Pillar s -> {
					area(first, s.radius(), TechniquePower.PILLAR_RADIUS_CAP, 0);
					count(s.onTargets(), TechniquePower.MAX_TARGETS);
				}
				case Shape.Rain s -> {
					area(first, s.radius(), TechniquePower.RAIN_RADIUS_CAP, 0);
					count(s.impacts(), TechniquePower.RAIN_IMPACTS_CAP);
				}
				case Shape.Chain s -> {
					area(first, s.range(), TechniquePower.CHAIN_RANGE_CAP, 2);
					count(s.jumps(), TechniquePower.CHAIN_JUMPS_CAP);
				}
				case Shape.Target s -> area(first, s.range(), TechniquePower.TARGET_RANGE_CAP, 2);
				case Shape.All s -> {
					area(first, s.radius(), TechniquePower.ALL_RADIUS_CAP, 0);
					count(s.max(), TechniquePower.MAX_TARGETS_DEWA);
				}
				case Shape.Volley s -> {
					area(first, s.range(), TechniquePower.VOLLEY_RANGE_CAP, 2);
					count(s.count(), TechniquePower.VOLLEY_COUNT_CAP);
				}
				case Shape.Domain s -> {
					area(first, s.radius(), TechniquePower.DOMAIN_RADIUS_CAP_DEWA, 0);
					duration(s.duration(), TechniquePower.DOMAIN_DURATION_CAP_DEWA);
				}
				case Shape.Field s -> {
					area(first, s.radius(), TechniquePower.DOMAIN_RADIUS_CAP_DEWA, 0);
					duration(s.duration(), TechniquePower.DOMAIN_DURATION_CAP_DEWA);
				}
				case Shape.Aura s -> {
					area(first, s.radius(), TechniquePower.AURA_RADIUS_CAP, 0);
					duration(s.duration(), TechniquePower.AURA_DURATION_CAP);
				}
				case Shape.Path s -> area(first, s.halfWidth(), TechniquePower.PATH_WIDTH_CAP, 0);
				default -> {
				}
			}
		}

		private void area(boolean first, Val val, double cap, int key) {
			if (first && val != null && val.set()) {
				areaVal = val;
				areaCap = cap;
				areaKey = AREA_KEYS[key];
			}
		}

		private void count(Val val, double cap) {
			if (countVal == null && val != null && val.set()) {
				countVal = val;
				countCap = cap;
			}
		}

		private void duration(Val val, double cap) {
			if (durationVal == null && val != null && val.set()) {
				durationVal = val;
				durationCap = cap;
			}
		}
	}

	// ================================================================== numbers

	private static boolean indonesian() {
		String language = Minecraft.getInstance().getLanguageManager().getSelected();
		return language != null && language.startsWith("id");
	}

	/** A whole number with group separators: "20.160" (id) / "20,160" (en). */
	public static String number(double value) {
		long rounded = Math.round(value);
		String plain = String.valueOf(Math.abs(rounded));
		StringBuilder builder = new StringBuilder();
		char group = indonesian() ? '.' : ',';
		for (int i = 0; i < plain.length(); i++) {
			if (i > 0 && (plain.length() - i) % 3 == 0) {
				builder.append(group);
			}
			builder.append(plain.charAt(i));
		}
		return (rounded < 0 ? "-" : "") + builder;
	}

	/** Compact: "8,4 rb" / "8.4k", "1,2 jt" / "1.2M"; below 10 000 a whole number. */
	public static String compact(double value) {
		double abs = Math.abs(value);
		if (abs < 10_000.0D) {
			return number(value);
		}
		boolean id = indonesian();
		String unit;
		double scaled;
		if (abs >= 1_000_000.0D) {
			scaled = value / 1_000_000.0D;
			unit = id ? " jt" : "M";
		} else {
			scaled = value / 1_000.0D;
			unit = id ? " rb" : "k";
		}
		return decimal(scaled, Math.abs(scaled) >= 100.0D ? 0 : 1) + unit;
	}

	/** {@code value} with {@code digits} decimals, the decimal mark of the language ("2,4" / "2.4"). */
	public static String decimal(double value, int digits) {
		String text = String.format(Locale.ROOT, "%." + Math.max(0, digits) + "f", value);
		if (digits > 0) {
			while (text.endsWith("0")) {
				text = text.substring(0, text.length() - 1);
			}
			if (text.endsWith(".")) {
				text = text.substring(0, text.length() - 1);
			}
		}
		return indonesian() ? text.replace('.', ',') : text;
	}

	/** Seconds from ticks: "1,5 dtk" / "1.5s" through the existing screen key. */
	public static Component seconds(long ticks) {
		double s = ticks / 20.0D;
		return Component.translatable("kultivator.screen.seconds", s >= 10.0D ? String.valueOf(Math.round(s)) : decimal(s, 1));
	}

	/** The source line of a spec: italic grey when it is a "Terinspirasi" / "Inspired" line (22.12.4). */
	public static Component sourceLine(TechniqueSpec spec) {
		Component source = spec.source();
		String text = source.getString();
		String lower = text.toLowerCase(Locale.ROOT);
		boolean inspired = lower.contains("terinspirasi") || lower.contains("inspired");
		return inspired ? source.copy().withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY) : source.copy().withColor(0x8A5A1E);
	}

	/** True when the spec's source line says "Terinspirasi" / "Inspired". */
	public static boolean inspired(TechniqueSpec spec) {
		String lower = spec.source().getString().toLowerCase(Locale.ROOT);
		return lower.contains("terinspirasi") || lower.contains("inspired");
	}

	/** The Dao trigram's Dao for filters (null = universal). */
	public static @Nullable Dao daoOf(TechniqueSpec spec) {
		return spec.dao();
	}

	private SpecInfo() {
	}
}
