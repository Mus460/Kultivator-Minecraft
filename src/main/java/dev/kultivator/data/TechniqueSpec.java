package dev.kultivator.data;

import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.DShape;
import dev.kultivator.technique.spec.dsl.Flow;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Pose;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.technique.spec.dsl.SoundCue;
import dev.kultivator.technique.spec.dsl.Step;
import dev.kultivator.technique.spec.run.SpecialTechnique;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The immutable description of one technique (DESIGN 22.2.2): gates, costs, flow, a timeline of primitive steps, FX
 * layers and sounds per phase, Mode Kehancuran shapes at K = 1 and the mastery bonuses. Built once from
 * {@code TechniqueRegistry.init} on both physical sides, so the client knows every spec without a sync. The id is saved
 * and never renamed; the net id never changes or is reused.
 * <p>
 * Lang keys are derived: {@code kultivator.technique.<id>} (Indonesian / English name), {@code .desc}, {@code .source}
 * ("Sumber: <work> · <named technique>") and the optional {@code .lore}.
 */
public final class TechniqueSpec {

	/** Spec flags (22.2.2). */
	public enum Flag {
		/** Teachable to disciples whatever the type default says (still never for the 22.13.1 exclusions). */
		TEACHABLE,
		/** Never teachable. */
		NO_TEACH,
		/** Cast even with no hostile in reach. */
		CAST_ALWAYS,
		/** Needs a locked target (TARGET, CHAIN). */
		REQUIRES_TARGET,
		/** ALL and other shapes may hit calm neutral mobs (22.3.5 rule 2). */
		PRECISE,
		/** No ECHO as the level-4 bonus. */
		NO_ECHO,
		/** Demonic: pays with health, confirmed once. */
		FORBIDDEN,
		/** Never in the player's codex or the 168 counter (karma_domain). */
		DISCIPLE_ONLY,
		/** Sold as a jade slip in the Paviliun Yue Lu. */
		PAVILION
	}

	/** The Dewa gate (22.8.8). */
	public enum Gate { NONE, DEWA_I, DEWA_II }

	/** A prerequisite: spec {@code id} at mastery level {@code level} or higher. */
	public record Prereq(String id, int level) {
	}

	/** Default disciple star gate of DEWA specs (22.13.1). */
	public static final int DEFAULT_DEWA_STARS = 36;
	/** Cooldowns are never shorter than this (22.5.4). */
	public static final int MIN_COOLDOWN = 10;

	private final String id;
	private final int netId;
	private final @Nullable Dao dao;
	private final TechniqueGrade grade;
	private final int sub;
	private final TechniqueType type;
	private final Realm minRealm;
	private final int minLayer;
	private final int minStage;
	private final Gate gate;
	private final int dewaStars;
	private final List<Prereq> prereqs;
	private final double minComprehension;
	private final List<Route> routes;
	private final int qi;
	private final double qiPct;
	private final int qiMin;
	private final double qiPerSecond;
	private final int cooldownTicks;
	private final @Nullable Flow.Charges charges;
	private final @Nullable Flow.Charge charge;
	private final @Nullable Flow.Channel channel;
	private final @Nullable Flow.Toggle toggle;
	private final @Nullable Flow.Stages stages;
	private final @Nullable Flow.Stack stack;
	private final List<Step> timeline;
	private final Map<Phase, List<FxLayer>> fx;
	private final Map<Phase, List<SoundCue>> sounds;
	private final List<DShape> destruction;
	private final boolean naturalOnly;
	private final List<Bonus> perfection;
	private final List<Bonus> peak;
	private final Set<Flag> flags;
	private final @Nullable String special;
	private final @Nullable Technique legacy;
	private final int teachStars;
	private final int discipleStages;
	private final Map<Phase, Pose> poses;
	private final @Nullable Phase climax;
	private final int windup;
	private final int castTicks;
	private final dev.kultivator.technique.spec.run.@Nullable BindKind bindLook;

	private TechniqueSpec(Builder b) {
		this.id = b.id;
		this.netId = b.netId;
		this.dao = b.dao;
		this.grade = b.grade;
		this.sub = Math.max(0, Math.min(2, b.sub));
		this.type = b.type;
		this.minRealm = b.minRealm;
		this.minLayer = Math.max(0, b.minLayer);
		this.minStage = Math.max(0, b.minStage);
		this.gate = b.gate;
		this.dewaStars = b.dewaStars;
		this.prereqs = List.copyOf(b.prereqs);
		this.minComprehension = b.minComprehension;
		this.routes = List.copyOf(b.routes);
		this.qi = Math.max(0, b.qi);
		this.qiPct = Math.max(0.0D, b.qiPct);
		this.qiMin = Math.max(0, b.qiMin);
		this.qiPerSecond = Math.max(0.0D, b.qiPerSecond);
		this.cooldownTicks = Math.max(MIN_COOLDOWN, b.cooldownTicks);
		this.charges = b.charges;
		this.charge = b.charge;
		this.channel = b.channel;
		this.toggle = b.toggle;
		this.stages = b.stages;
		this.stack = b.stack;
		this.timeline = List.copyOf(b.timeline);
		EnumMap<Phase, List<FxLayer>> fxCopy = new EnumMap<>(Phase.class);
		b.fx.forEach((phase, layers) -> fxCopy.put(phase, List.copyOf(layers)));
		this.fx = Collections.unmodifiableMap(fxCopy);
		EnumMap<Phase, List<SoundCue>> soundCopy = new EnumMap<>(Phase.class);
		b.sounds.forEach((phase, cues) -> soundCopy.put(phase, List.copyOf(cues)));
		this.sounds = Collections.unmodifiableMap(soundCopy);
		this.destruction = List.copyOf(b.destruction);
		this.naturalOnly = b.naturalOnly;
		this.perfection = List.copyOf(b.perfection);
		this.peak = List.copyOf(b.peak);
		this.flags = b.flags.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(b.flags));
		this.special = b.special;
		this.legacy = b.legacy;
		this.teachStars = Math.max(0, b.teachStars);
		this.discipleStages = Math.max(1, b.discipleStages);
		EnumMap<Phase, Pose> poseCopy = new EnumMap<>(Phase.class);
		poseCopy.putAll(b.poses);
		this.poses = Collections.unmodifiableMap(poseCopy);
		this.climax = b.climax;
		this.windup = b.windup;
		this.castTicks = b.castTicks;
		this.bindLook = b.bindLook;
	}

	/** Stages of a STAGES spec a disciple runs in one cast (22.13.1 "Y (st1-3)"; 1 = only the first). */
	public int discipleStages() {
		return discipleStages;
	}

	public static Builder builder(String id, int netId, @Nullable Dao dao) {
		return new Builder(id, netId, dao);
	}

	// ================================================================== fields

	/** {@code [a-z0-9_]{1,48}}, saved, never renamed. */
	public String id() {
		return id;
	}

	/** Network id (22.2.3 blocks), never reused. Legacy specs use their enum ordinal. */
	public int netId() {
		return netId;
	}

	/** Null = universal. */
	public @Nullable Dao dao() {
		return dao;
	}

	public TechniqueGrade grade() {
		return grade;
	}

	/** 0 Rendah, 1 Menengah, 2 Tinggi. */
	public int sub() {
		return sub;
	}

	public TechniqueType type() {
		return type;
	}

	public Realm minRealm() {
		return minRealm;
	}

	/** Immortal Ascension layer index (0-8 = Tahap 1-9) needed; 0 for other realms. */
	public int minLayer() {
		return minLayer;
	}

	/** Stage index (0-3) needed within the realm. */
	public int minStage() {
		return minStage;
	}

	/** The rank gate as a {@link Rank}. */
	public Rank minRank() {
		return new Rank(minRealm, minLayer, minStage);
	}

	public Gate gate() {
		return gate;
	}

	/** Disciple star gate (DEWA specs, disciple-only specs and Y★n rows). */
	public int dewaStars() {
		return dewaStars;
	}

	public List<Prereq> prereqs() {
		return prereqs;
	}

	/** Dao comprehension needed (0 = none); for universal specs the main comprehension. */
	public double minComprehension() {
		return minComprehension;
	}

	public List<Route> routes() {
		return routes;
	}

	/** Flat qi cost (base; realm gap and mastery lower it, 22.5.4). */
	public int qi() {
		return qi;
	}

	/** Percent of max qi (0 = flat cost); not reduced by the realm gap. */
	public double qiPct() {
		return qiPct;
	}

	/** The least a {@link #qiPct} cost may be ("qiPct 50 (min 600)"). */
	public int qiMin() {
		return qiMin;
	}

	/** Drain while channelling or toggled. */
	public double qiPerSecond() {
		return qiPerSecond;
	}

	/** Base cooldown in ticks (≥ 10). */
	public int cooldownTicks() {
		return cooldownTicks;
	}

	public @Nullable Flow.Charges charges() {
		return charges;
	}

	public @Nullable Flow.Charge charge() {
		return charge;
	}

	public @Nullable Flow.Channel channel() {
		return channel;
	}

	public @Nullable Flow.Toggle toggle() {
		return toggle;
	}

	public @Nullable Flow.Stages stages() {
		return stages;
	}

	public @Nullable Flow.Stack stack() {
		return stack;
	}

	public List<Step> timeline() {
		return timeline;
	}

	public Map<Phase, List<FxLayer>> fx() {
		return fx;
	}

	/** The layers of one phase (empty when none). */
	public List<FxLayer> fx(Phase phase) {
		return fx.getOrDefault(phase, List.of());
	}

	public Map<Phase, List<SoundCue>> sounds() {
		return sounds;
	}

	public List<SoundCue> sounds(Phase phase) {
		return sounds.getOrDefault(phase, List.of());
	}

	/** Mode Kehancuran shapes at K = 1 (≤ 5, one shared block cap). */
	public List<DShape> destruction() {
		return destruction;
	}

	/** Every shape of this spec removes natural blocks only. */
	public boolean naturalOnly() {
		return naturalOnly;
	}

	/** Mastery level 3 bonuses. */
	public List<Bonus> perfection() {
		return perfection;
	}

	/** Mastery level 4 bonuses. */
	public List<Bonus> peak() {
		return peak;
	}

	public Set<Flag> flags() {
		return flags;
	}

	public boolean has(Flag flag) {
		return flags.contains(flag);
	}

	/** The {@code SpecialTechnique} handler id, or null. */
	public @Nullable String special() {
		return special;
	}

	/** The legacy enum constant (the 23), or null. */
	public @Nullable Technique legacy() {
		return legacy;
	}

	/** Disciples learn it only from this star on (Y★n); 0 = no star gate besides {@link #dewaStars} for Dewa. */
	public int teachStars() {
		return teachStars;
	}

	// ================================================================== cast animation and pacing (DESIGN 27.2, 27.5)

	/** The CAST pose (AUTO = the client derives it from the type, the first shape, the Dao and the phase). */
	public Pose pose() {
		return pose(Phase.CAST);
	}

	/** The pose authored for {@code phase} ({@code .pose(phase, pose)}), else AUTO. */
	public Pose pose(Phase phase) {
		return poses.getOrDefault(phase, Pose.AUTO);
	}

	/**
	 * The pose to play for {@code phase} (DESIGN 27.2.4): the authored one, else for CAST and CHARGE the automatic choice
	 * from the type, the first shape and the Dao; other phases without an authored pose return AUTO (the client accents
	 * the running pose). Pure, shared by the server (hand swing) and the client (CastPoses).
	 */
	public Pose resolvedPose(Phase phase) {
		Pose authored = poses.get(phase);
		if (authored != null && authored != Pose.AUTO) {
			return authored;
		}
		if (phase == Phase.CHARGE) {
			Pose cast = poses.getOrDefault(Phase.CAST, Pose.AUTO);
			return cast == Pose.CHARGE_OVERHEAD || cast == Pose.SWORD_SKY || cast == Pose.SUMMON_RAISE ? cast : Pose.CHARGE_BACK;
		}
		if (phase != Phase.CAST) {
			return Pose.AUTO;
		}
		dev.kultivator.technique.spec.dsl.Shape.Kind kind = dev.kultivator.technique.spec.dsl.Shape.Kind.SELF;
		for (Step step : timeline) {
			kind = step.shape().kind();
			if (kind != dev.kultivator.technique.spec.dsl.Shape.Kind.SELF) {
				break;
			}
		}
		if (type == TechniqueType.WILAYAH || kind.lasting()) {
			return Pose.ARMS_SPREAD;
		}
		if (type == TechniqueType.PANGGIL) {
			return Pose.SUMMON_RAISE;
		}
		if (type == TechniqueType.GERAK || kind == dev.kultivator.technique.spec.dsl.Shape.Kind.PATH) {
			return Pose.DASH_LEAN;
		}
		return switch (kind) {
			case STRIKE -> dao == Dao.BODY ? Pose.PUNCH : dao == Dao.SWORD ? Pose.SWORD_SLASH : Pose.PALM_THRUST;
			case TARGET -> Pose.FINGER_POINT;
			case CONE -> dao == Dao.FIRE || dao == Dao.ICE ? Pose.TWIN_PALM : Pose.PALM_THRUST;
			case BEAM -> dao == Dao.SWORD ? Pose.SWORD_THRUST : dao == Dao.LIGHTNING ? Pose.SKY_RAISE : Pose.TWIN_PALM;
			case FRONT, WAVE -> dao == Dao.SWORD ? Pose.SWORD_SLASH : Pose.TWIN_PALM;
			case NOVA -> dao == Dao.BODY || dao == Dao.ICE || dao == Dao.LIFE ? Pose.STOMP : Pose.ARMS_SPREAD;
			case ZONE, PILLAR, RAIN -> dao == Dao.SWORD ? Pose.SWORD_SKY : Pose.SKY_RAISE;
			case CHAIN -> Pose.SKY_RAISE;
			case VOLLEY -> dao == Dao.SWORD ? Pose.SWORD_THRUST : Pose.PALM_THRUST;
			case ALL, RECALL -> Pose.SEAL;
			case SELF -> switch (type) {
				case PULIH -> grade.tier() >= TechniqueGrade.TIAN.tier() ? Pose.MEDITATE_FLOAT : Pose.SEAL;
				case UBAH -> Pose.SUMMON_RAISE;
				default -> Pose.SEAL;
			};
			default -> Pose.SEAL;
		};
	}

	/** Every authored pose by phase. */
	public Map<Phase, Pose> poses() {
		return poses;
	}

	/** The explicitly authored climax phase ({@code .climax(phase)}), or null. */
	public @Nullable Phase climax() {
		return climax;
	}

	/**
	 * The phase whose first event of a cast carries {@code TechFx.FLAG_CLIMAX} (7b climax beat, the one big shake): the
	 * authored one, else the highest EXTRA phase with layers, else IMPACT, else TRAVEL, else CAST. Never PULSE, HIT or
	 * MASS_HIT (they repeat).
	 */
	public Phase climaxPhase() {
		if (climax != null) {
			return climax;
		}
		for (int i = 7; i >= 0; i--) {
			if (!fx(Phase.extra(i)).isEmpty()) {
				return Phase.extra(i);
			}
		}
		if (!fx(Phase.IMPACT).isEmpty()) {
			return Phase.IMPACT;
		}
		if (!fx(Phase.TRAVEL).isEmpty()) {
			return Phase.TRAVEL;
		}
		return Phase.CAST;
	}

	/**
	 * Ticks between the CAST event (build-up: pose wind-back, gathering qi) and the release of a wave, front, beam or rain
	 * (the TRAVEL event and the first query): the authored {@code .windup(t)}, else 4 (6 for PAMUNGKAS). Charged casts
	 * release at the end of the charge and add nothing.
	 */
	public int windup() {
		if (windup >= 0) {
			return windup;
		}
		return type == TechniqueType.PAMUNGKAS ? 6 : 4;
	}

	/** What this spec's binds look like on the clients (null = by the gameplay bind: ROOT vines, STUN chains...). */
	public dev.kultivator.technique.spec.run.@Nullable BindKind bindLook() {
		return bindLook;
	}

	/** The duration the CAST event carries (0 = derived: the channel, else the longest self boon). */
	public int castTicks() {
		return castTicks;
	}

	/** True when {@code .windup(t)} was authored. */
	public boolean windupAuthored() {
		return windup >= 0;
	}

	// ================================================================== derived

	public String nameKey() {
		return "kultivator.technique." + id;
	}

	public String descKey() {
		return nameKey() + ".desc";
	}

	public String sourceKey() {
		return nameKey() + ".source";
	}

	public String loreKey() {
		return nameKey() + ".lore";
	}

	public Component displayName() {
		return Component.translatable(nameKey());
	}

	public Component description() {
		return Component.translatable(descKey());
	}

	public Component source() {
		return Component.translatable(sourceKey());
	}

	public boolean isLegacy() {
		return legacy != null;
	}

	public boolean universal() {
		return dao == null;
	}

	public boolean pamungkas() {
		return type == TechniqueType.PAMUNGKAS;
	}

	public boolean dewa() {
		return grade == TechniqueGrade.DEWA;
	}

	/** Shown in the player's codex and counted in "Teknik x/168". */
	public boolean playerVisible() {
		return !has(Flag.DISCIPLE_ONLY);
	}

	public boolean hasDestruction() {
		return !destruction.isEmpty();
	}

	/** True when a step uses a following or placed lasting area (DOMAIN, FIELD, AURA). */
	public boolean lasting() {
		for (Step step : timeline) {
			if (step.shape().kind().lasting()) {
				return true;
			}
		}
		return false;
	}

	/** ECHO is allowed as the level-4 bonus (22.3.4): instant SERANG, AREA, KENDALI and PAMUNGKAS, no NO_ECHO. */
	public boolean echoAllowed() {
		return !has(Flag.NO_ECHO) && type.echoType() && channel == null && toggle == null && !lasting();
	}

	/** Jade slips may hold it (22.8): every spec except Dewa, disciple-only and the fusion pair. */
	public boolean slipAllowed() {
		return grade != TechniqueGrade.DEWA && !has(Flag.DISCIPLE_ONLY) && !"fusion_pair".equals(special);
	}

	/**
	 * Sold in the pavilion (22.8.4): the PAVILION flag, or by default a non-Dewa, non-special, non-disciple-only spec
	 * with a BOOK, PAV or COMP route.
	 */
	public boolean pavilion() {
		if (has(Flag.PAVILION)) {
			return true;
		}
		if (grade == TechniqueGrade.DEWA || special != null || has(Flag.DISCIPLE_ONLY) || isLegacy()) {
			return false;
		}
		for (Route route : routes) {
			if (route.kind() == Route.Kind.BOOK || route.kind() == Route.Kind.PAV || route.kind() == Route.Kind.COMP) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Teachable to disciples (22.13.1): the TEACHABLE / NO_TEACH flags over the type default, but never FORBIDDEN specs,
	 * specs with CHEAT_DEATH charges (a bare 1-HP floor is allowed) or a TRANSFORM above scale 1.5, specials without {@code supportsDisciples}, or clone summons.
	 */
	public boolean teachable() {
		if (has(Flag.NO_TEACH) || has(Flag.FORBIDDEN)) {
			return false;
		}
		if (special != null) {
			SpecialTechnique handler = TechniqueRegistry.special(special);
			if (handler == null || !handler.supportsDisciples()) {
				return false;
			}
		}
		for (Step step : timeline) {
			if (neverTaught(step)) {
				return false;
			}
		}
		return has(Flag.TEACHABLE) || type.teachableByDefault();
	}

	private static boolean neverTaught(Step step) {
		for (Payload.Boon boon : step.self()) {
			// a 1-HP floor (healthShare 0, bodhisattva_tree Y★37) is listed apart from cheat-death charges in 22.14.6
			if (boon instanceof Payload.CheatDeath cheat && cheat.healthShare() > 0.0D) {
				return true;
			}
			if (boon instanceof Payload.Transform transform && transform.scale().set() && transform.scale().cap() > 1.5D) {
				return true;
			}
			if (boon instanceof Payload.Summon summon && Payload.Summon.CLONE.equals(summon.kind())) {
				return true;
			}
		}
		for (List<Step> children : List.of(step.onHit(), step.onKill(), step.onEnd())) {
			for (Step child : children) {
				if (neverTaught(child)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Half summons and counts for disciples (PANGGIL, 22.7). */
	public boolean halfForDisciples() {
		return type == TechniqueType.PANGGIL;
	}

	@Override
	public String toString() {
		return "TechniqueSpec[" + id + "#" + netId + "]";
	}

	// ================================================================== builder

	/**
	 * The fluent builder the content modules write (22.2.5); every method maps to one field of 22.2.2. Lists append.
	 * Defaults: Kuning Rendah SERANG at Qi Refining, cooldown 1 s, no cost.
	 */
	public static final class Builder {
		private final String id;
		private final int netId;
		private final @Nullable Dao dao;
		private TechniqueGrade grade = TechniqueGrade.HUANG;
		private int sub;
		private TechniqueType type = TechniqueType.SERANG;
		private Realm minRealm = Realm.QI_REFINING;
		private int minLayer;
		private int minStage;
		private Gate gate = Gate.NONE;
		private int dewaStars = DEFAULT_DEWA_STARS;
		private final List<Prereq> prereqs = new ArrayList<>();
		private double minComprehension;
		private final List<Route> routes = new ArrayList<>();
		private int qi;
		private double qiPct;
		private int qiMin;
		private double qiPerSecond;
		private int cooldownTicks = 20;
		private @Nullable Flow.Charges charges;
		private @Nullable Flow.Charge charge;
		private @Nullable Flow.Channel channel;
		private @Nullable Flow.Toggle toggle;
		private @Nullable Flow.Stages stages;
		private final List<Integer> stageQi = new ArrayList<>();
		private final List<Realm> stageRealms = new ArrayList<>();
		private @Nullable Flow.Stack stack;
		private final List<Step> timeline = new ArrayList<>();
		private final EnumMap<Phase, List<FxLayer>> fx = new EnumMap<>(Phase.class);
		private final EnumMap<Phase, List<SoundCue>> sounds = new EnumMap<>(Phase.class);
		private final List<DShape> destruction = new ArrayList<>();
		private boolean naturalOnly;
		private final List<Bonus> perfection = new ArrayList<>();
		private final List<Bonus> peak = new ArrayList<>();
		private final EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
		private @Nullable String special;
		private @Nullable Technique legacy;
		private int teachStars;
		private int discipleStages = 1;
		private final EnumMap<Phase, Pose> poses = new EnumMap<>(Phase.class);
		private @Nullable Phase climax;
		private int windup = -1;
		private int castTicks;
		private dev.kultivator.technique.spec.run.@Nullable BindKind bindLook;

		private Builder(String id, int netId, @Nullable Dao dao) {
			this.id = id;
			this.netId = netId;
			this.dao = dao;
		}

		/** Grade and sub-rank (0 Rendah, 1 Menengah, 2 Tinggi). */
		public Builder grade(TechniqueGrade value, int subRank) {
			this.grade = value;
			this.sub = subRank;
			return this;
		}

		public Builder type(TechniqueType value) {
			this.type = value;
			return this;
		}

		public Builder realm(Realm value) {
			this.minRealm = value;
			return this;
		}

		/** Immortal Ascension Tahap {@code tahap} (1-9). */
		public Builder realm(Realm value, int tahap) {
			this.minRealm = value;
			this.minLayer = Math.max(0, tahap - 1);
			return this;
		}

		/** Stage index 0-3 needed within the realm. */
		public Builder stage(int stageIndex) {
			this.minStage = stageIndex;
			return this;
		}

		public Builder gate(Gate value) {
			this.gate = value;
			return this;
		}

		public Builder dewaStars(int stars) {
			this.dewaStars = stars;
			return this;
		}

		public Builder prereq(String specId, int masteryLevel) {
			this.prereqs.add(new Prereq(specId, masteryLevel));
			return this;
		}

		public Builder comprehension(double value) {
			this.minComprehension = value;
			return this;
		}

		public Builder route(Route... values) {
			this.routes.addAll(List.of(values));
			return this;
		}

		public Builder qi(int value) {
			this.qi = value;
			return this;
		}

		/** {@code qiPct n%}. */
		public Builder qiPct(double percent) {
			this.qiPct = percent;
			return this;
		}

		/** {@code qiPct n% (min m)}. */
		public Builder qiPct(double percent, int min) {
			this.qiPct = percent;
			this.qiMin = min;
			return this;
		}

		public Builder qiPerSecond(double value) {
			this.qiPerSecond = value;
			return this;
		}

		/** Qi per stage of a STAGES spec ("qi 25/30/45"). */
		public Builder qiPerStage(int... values) {
			this.stageQi.clear();
			for (int value : values) {
				this.stageQi.add(value);
			}
			return this;
		}

		/** Cooldown in seconds. */
		public Builder cooldown(double seconds) {
			this.cooldownTicks = (int) Math.round(seconds * 20.0D);
			return this;
		}

		public Builder cooldownTicks(int ticks) {
			this.cooldownTicks = ticks;
			return this;
		}

		/** {@code CHARGES n (recharge ticks each)}. */
		public Builder charges(int count, int rechargeTicks) {
			this.charges = new Flow.Charges(dev.kultivator.technique.spec.dsl.Val.of(count), rechargeTicks);
			return this;
		}

		/** {@code CHARGES b→c (recharge ticks each)}. */
		public Builder charges(int baseCount, int capCount, int rechargeTicks) {
			this.charges = new Flow.Charges(dev.kultivator.technique.spec.dsl.Val.of(baseCount, capCount), rechargeTicks);
			return this;
		}

		/** {@code CHG t}. */
		public Builder charge(int ticks) {
			this.charge = new Flow.Charge(ticks, Flow.Charge.MIN_SHARE, Flow.Charge.INTERRUPT);
			return this;
		}

		/** {@code CHAN t/pulse}. */
		public Builder channel(int ticks, int pulse) {
			this.channel = new Flow.Channel(ticks, pulse, 0.2D);
			return this;
		}

		/** {@code CHAN t/pulse (interrupt share)}. */
		public Builder channel(int ticks, int pulse, double interrupt) {
			this.channel = new Flow.Channel(ticks, pulse, interrupt);
			return this;
		}

		/** {@code TOG qi/s}. */
		public Builder toggle(double qiPerSecondValue) {
			this.toggle = new Flow.Toggle(qiPerSecondValue, 0);
			return this;
		}

		/** {@code TOG qi/s (start n)}. */
		public Builder toggle(double qiPerSecondValue, int startQi) {
			this.toggle = new Flow.Toggle(qiPerSecondValue, startQi);
			return this;
		}

		/** {@code ST n (window)}. */
		public Builder stages(int count, int windowTicks) {
			this.stages = new Flow.Stages(count, windowTicks, List.of(), List.of(), 0);
			return this;
		}

		/** {@code ST n (window)} whose recasts must be at least {@code gapTicks} apart. */
		public Builder stages(int count, int windowTicks, int gapTicks) {
			this.stages = new Flow.Stages(count, windowTicks, List.of(), List.of(), Math.max(0, gapTicks));
			return this;
		}

		/** A disciple runs stages 1..{@code count} of this STAGES spec in one cast (one recast step apart). */
		public Builder discipleStages(int count) {
			this.discipleStages = Math.max(1, count);
			return this;
		}

		/** The realm stage {@code stage} (1-based) needs ("realm gate st2 NS"). */
		public Builder stageRealm(int stageNumber, Realm realm) {
			while (stageRealms.size() < stageNumber) {
				stageRealms.add(Realm.MORTAL);
			}
			stageRealms.set(stageNumber - 1, realm);
			return this;
		}

		public Builder stack(Flow.Stack value) {
			this.stack = value;
			return this;
		}

		public Builder step(Step... steps) {
			this.timeline.addAll(List.of(steps));
			return this;
		}

		public Builder fx(Phase phase, FxLayer... layers) {
			this.fx.computeIfAbsent(phase, key -> new ArrayList<>()).addAll(List.of(layers));
			return this;
		}

		public Builder sound(Phase phase, SoundEvent sound, float volume, float pitch) {
			return sound(phase, SoundCue.of(sound, volume, pitch));
		}

		public Builder sound(Phase phase, Holder<SoundEvent> sound, float volume, float pitch) {
			return sound(phase, SoundCue.of(sound, volume, pitch));
		}

		public Builder sound(Phase phase, SoundCue cue) {
			this.sounds.computeIfAbsent(phase, key -> new ArrayList<>()).add(cue);
			return this;
		}

		/** Mode Kehancuran shapes at K = 1 (22.11.4). */
		public Builder kehancuran(DShape... shapes) {
			this.destruction.addAll(List.of(shapes));
			return this;
		}

		public Builder naturalOnly() {
			this.naturalOnly = true;
			return this;
		}

		public Builder perfection(Bonus... bonuses) {
			this.perfection.addAll(List.of(bonuses));
			return this;
		}

		public Builder peak(Bonus... bonuses) {
			this.peak.addAll(List.of(bonuses));
			return this;
		}

		public Builder flag(Flag... values) {
			this.flags.addAll(List.of(values));
			return this;
		}

		public Builder special(String handlerId) {
			this.special = handlerId;
			return this;
		}

		/** Disciples learn it from star {@code stars} (Y★n). */
		public Builder teachStars(int stars) {
			this.teachStars = stars;
			return this;
		}

		/** The CAST pose (DESIGN 27.2): what the caster's body does when the cast starts. */
		public Builder pose(Pose value) {
			return pose(Phase.CAST, value);
		}

		/** The pose of {@code phase} (a stomp on EXTRA0, a slam on release...). */
		public Builder pose(Phase phase, Pose value) {
			this.poses.put(phase, value);
			return this;
		}

		/** The pose at the cast and the pose the release / first impact plays (SKY_RAISE then OVERHEAD_SLAM). */
		public Builder poses(Pose cast, Phase release, Pose then) {
			this.poses.put(Phase.CAST, cast);
			this.poses.put(release, then);
			return this;
		}

		/** The climax phase (7b climax beat, FLAG_CLIMAX); the default is {@link TechniqueSpec#climaxPhase()}. */
		public Builder climax(Phase phase) {
			this.climax = phase;
			return this;
		}

		/** Build-up ticks between CAST and the release of travelling shapes (default 4, PAMUNGKAS 6). */
		public Builder windup(int ticks) {
			this.windup = Math.max(0, Math.min(20, ticks));
			return this;
		}

		/** How long the CAST event says the cast lasts (wings spread, a held prop), when no boon tells it. */
		public Builder castTicks(int ticks) {
			this.castTicks = Math.max(0, ticks);
			return this;
		}

		/** The look of this spec's binds (sleep, seal, rope, time stop...; DESIGN 27.4). */
		public Builder bindLook(dev.kultivator.technique.spec.run.BindKind value) {
			this.bindLook = value;
			return this;
		}

		/** Only the registry's LegacyArts use this. */
		Builder legacy(Technique technique) {
			this.legacy = technique;
			return this;
		}

		public TechniqueSpec build() {
			if (stages != null && (!stageQi.isEmpty() || !stageRealms.isEmpty())) {
				stages = new Flow.Stages(stages.count(), stages.window(), List.copyOf(stageQi), List.copyOf(stageRealms), stages.gap());
			}
			return new TechniqueSpec(this);
		}
	}
}
