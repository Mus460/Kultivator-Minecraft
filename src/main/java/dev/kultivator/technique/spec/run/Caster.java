package dev.kultivator.technique.spec.run;

import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.fx.Fx;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.DaoTechniques;
import dev.kultivator.technique.spec.CastLedger;
import dev.kultivator.technique.spec.DamageContext;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.SoundCue;
import dev.kultivator.technique.spec.dsl.Step;
import dev.kultivator.technique.spec.dsl.Val;
import dev.kultivator.util.LoadedPath;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.PortalIndex;
import dev.kultivator.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One running cast (DESIGN 22.2.6): the snapshot taken at the start, the timeline scheduling, the payload primitives of
 * 22.3.2 and 22.3.3 with the shared safety rules of 22.3.5, the FX and sound events, mastery bookkeeping and the
 * Kehancuran hand-off. {@link PlayerCaster} and {@link DiscipleCaster} supply the identity, the target rules, the damage
 * choke point and the costs. Shapes and step execution live in {@link Steps}.
 */
public abstract class Caster implements CastContext {

	/** True while a REFLECT answer is dealt (it never reflects again). */
	static final AtomicBoolean REFLECTING = new AtomicBoolean();
	/** The max distance a DASH or BLINK may go at all. */
	static final double MOVE_CAP = 64.0D;

	/** A scheduled action of this cast. */
	private record Scheduled(long due, Runnable action) {
	}

	/** One occurrence of a step: repetition, the hit anchor (onHit, hooks), the phantom anchor, a damage factor and kind. */
	static final class Run {
		final Step step;
		final int rep;
		final @Nullable LivingEntity hit;
		final @Nullable Vec3 at;
		final double mult;
		final DamageContext.Kind kind;
		final boolean byProduct;
		double storedDamage;
		/** EACH_TARGET / DENSEST points of this occurrence, found once (one query) and reused by every reader. */
		@Nullable List<Vec3> anchors;
		dev.kultivator.technique.spec.dsl.Anchor.@Nullable Kind anchorsKind;

		Run(Step step, int rep, @Nullable LivingEntity hit, @Nullable Vec3 at, double mult, DamageContext.Kind kind, boolean byProduct) {
			this.step = step;
			this.rep = rep;
			this.hit = hit;
			this.at = at;
			this.mult = mult;
			this.kind = kind;
			this.byProduct = byProduct;
		}

		Run child(Step sub, @Nullable LivingEntity anchor) {
			Run run = new Run(sub, 0, anchor, at, mult, kind, byProduct);
			run.storedDamage = storedDamage;
			return run;
		}

		Phase travelPhase() {
			return step.extraPhase() >= 0 ? Phase.extra(step.extraPhase()) : Phase.TRAVEL;
		}
	}

	// ================================================================== identity

	final TechniqueSpec spec;
	final LivingEntity caster;
	final ServerLevel level;
	final CastOptions options;
	final long seed;
	final long startTick;
	final @Nullable Caster parent;

	// ================================================================== snapshot

	double power;
	int mastery;
	TechniquePower.Factors factors;
	int visualTier;
	boolean kehancuran;
	boolean arena;
	boolean echo;
	double factor = 1.0D;
	int stage;
	double chargeShare = 1.0D;
	final Vec3 origin;
	Vec3 eye;
	Vec3 look;
	Vec3 aim;
	@Nullable LivingEntity target;
	Vec3 end;
	final CastLedger ledger;
	final PortalIndex.Cache portals;
	final CompoundTag state = new CompoundTag();

	// ================================================================== mastery bonuses (22.6)

	double areaBonus = 1.0D;
	double durationBonus = 1.0D;
	int durationTicks;
	int bonusCount;
	double bonusCountFactor = 1.0D;
	int bonusChain;
	int bonusWaves;
	double cooldownBonus;
	final List<Payload.Hit> bonusHits = new ArrayList<>();
	final List<Payload.Boon> bonusSelf = new ArrayList<>();
	final List<Step> bonusSteps = new ArrayList<>();
	@Nullable Bonus echoBonus;
	@Nullable Bonus extraCast;

	// ================================================================== runtime

	private final List<Scheduled> scheduled = new ArrayList<>();
	private int activity;
	long timelineStart;
	boolean charging;
	long chargeUntil;
	long channelUntil;
	boolean toggled;
	long stageWindowUntil;
	boolean endStepsRun;
	boolean ended;
	boolean aborted;
	@Nullable EndReason endReason;
	double qiPaid;
	int cooldownSet;

	final Map<UUID, Integer> hits = new HashMap<>();
	final Set<LivingEntity> hitOrder = new LinkedHashSet<>();
	int kills;
	boolean hitAny;
	boolean hitBoss;
	boolean killedAny;
	boolean comprehensionGiven;
	double drainQi;
	double restoredQi;
	double comprehension;
	int glowing;
	double lastAreaRadius = Double.NaN;
	/** The cast's last FIELD (centre and radius), for a finale after the field was replaced or ended. */
	@Nullable Vec3 lastFieldCentre;
	double lastFieldRadius;
	@Nullable Vec3 pathFrom;
	@Nullable Vec3 pathTo;
	final List<Vec3> afterimages = new ArrayList<>();
	private long fxWindow = Long.MIN_VALUE / 2;
	private int fxCount;
	@Nullable LivingEntity specialTarget;
	final Destruction destruction;
	boolean firstImpactDone;
	/** True while the target precheck evaluates (a dry run: no Kehancuran, no end point, no impact points). */
	boolean precheck;
	/** DRAIN heal for allies collected over one payload pass (one ally query after the pass). */
	private double pendingAllyHeal;
	private double pendingAllyRadius;
	/** True while {@code Steps.deliver} runs a payload pass. */
	boolean passOpen;

	Caster(TechniqueSpec spec, LivingEntity caster, ServerLevel level, CastOptions options, @Nullable Caster parent) {
		this.spec = spec;
		this.caster = caster;
		this.level = level;
		this.options = options;
		this.parent = parent;
		this.startTick = level.getGameTime();
		this.seed = level.getRandom().nextLong() ^ startTick * 31L ^ caster.getId();
		this.origin = caster.position();
		this.eye = caster.getEyePosition();
		Vec3 view = caster.getViewVector(1.0F);
		this.look = view.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : view.normalize();
		this.aim = origin;
		this.end = origin;
		this.ledger = parent != null ? parent.ledger : new CastLedger();
		this.portals = parent != null ? parent.portals : PortalIndex.cache();
		this.timelineStart = startTick;
		this.destruction = new Destruction(this);
		this.followArea = Steps.followsCaster(spec);
		this.tally = parent != null ? parent.tally
				: options.test() && caster instanceof net.minecraft.server.level.ServerPlayer ? TechniqueTests.claim(caster.getUUID(), spec.id(), this) : null;
	}

	/** The per-target damage tally of an {@code uji teknik <id>} cast (shared with its children; null otherwise). */
	final TechniqueTests.@Nullable Tally tally;

	/** Mastery bonuses of levels 3 and 4 (off in an arena), read once at the snapshot. */
	void readBonuses() {
		if (arena || parent != null) {
			return;
		}
		if (mastery >= 3) {
			for (Bonus bonus : spec.perfection()) {
				applyBonus(bonus);
			}
		}
		if (mastery >= 4) {
			for (Bonus bonus : spec.peak()) {
				if (bonus.kind() == Bonus.Kind.ECHO && !spec.echoAllowed()) {
					durationBonus *= 1.25D;
				} else {
					applyBonus(bonus);
				}
			}
		}
	}

	private void applyBonus(Bonus bonus) {
		switch (bonus.kind()) {
			case EXTRA_COUNT -> {
				bonusCount += bonus.count();
				bonusCountFactor = bonus.value();
			}
			case EXTRA_WAVE -> bonusWaves += bonus.count();
			case AREA -> areaBonus *= 1.0D + bonus.value();
			case DURATION -> durationBonus *= 1.0D + bonus.value();
			case DURATION_TICKS -> durationTicks += bonus.ticks();
			case PAYLOAD -> {
				bonusHits.addAll(bonus.hit());
				bonusSelf.addAll(bonus.self());
			}
			case CHAIN -> bonusChain += bonus.count();
			case COOLDOWN -> cooldownBonus += bonus.value();
			case EXTRA_CAST -> extraCast = bonus;
			case ECHO -> echoBonus = bonus;
			case STEPS -> bonusSteps.addAll(bonus.steps());
			default -> {
			}
		}
	}

	/** True when the mastery bonus {@code key} (SPECIAL) of level 3 / 4 applies (legacy hand-written code). */
	public boolean special(String key) {
		if (arena || parent != null) {
			return false;
		}
		if (mastery >= 3) {
			for (Bonus bonus : spec.perfection()) {
				if (bonus.kind() == Bonus.Kind.SPECIAL && key.equals(bonus.key())) {
					return true;
				}
			}
		}
		if (mastery >= 4) {
			for (Bonus bonus : spec.peak()) {
				if (bonus.kind() == Bonus.Kind.SPECIAL && key.equals(bonus.key())) {
					return true;
				}
			}
		}
		return false;
	}

	/** True when a perfection bonus (level 3) of this kind applies. */
	public boolean perfection(Bonus.Kind kind) {
		if (arena || parent != null || mastery < 3) {
			return false;
		}
		for (Bonus bonus : spec.perfection()) {
			if (bonus.kind() == kind) {
				return true;
			}
		}
		return false;
	}

	/** True when a peak bonus (level 4) of this kind applies (ECHO only where 22.3.4 allows it). */
	public boolean peak(Bonus.Kind kind) {
		if (arena || parent != null || mastery < 4) {
			return false;
		}
		if (kind == Bonus.Kind.ECHO && !spec.echoAllowed()) {
			return false;
		}
		for (Bonus bonus : spec.peak()) {
			if (bonus.kind() == kind) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== subclass contract

	abstract boolean chokeHurt(DamageContext context);

	/** The caster still exists in this level (online, alive, not removed). */
	abstract boolean casterValid();

	/** Restores qi to the caster (RESTORE_QI, DRAIN qi). */
	abstract void restoreQi(double amount);

	/** The caster's max qi. */
	abstract double maxQi();

	/** Takes qi without refusing (FORBIDDEN_COST qi share). */
	abstract void takeQi(double amount);

	/** Adds Dao comprehension from a COMPREHENSION payload (players: daily cap). */
	abstract void gainComprehension(double amount);

	/** Resets cooldowns of known specs up to {@code maxGrade} (never PAMUNGKAS). */
	abstract void resetCooldowns(TechniqueGrade maxGrade);

	/** Cast-end bookkeeping of the subclass (mastery points, cooldown after a toggle...). */
	abstract void finished(EndReason reason);

	/** A target died to this cast. */
	abstract void onKill(LivingEntity victim);

	/** The caster's realm (realm-gated values such as a volley's per-target hits). */
	abstract dev.kultivator.data.Realm casterRealm();

	/** The first hostile hit of the cast (comprehension). */
	abstract void onFirstHit(LivingEntity victim);

	/** Max targets per evaluation: 32 (Dewa 64). */
	int maxTargets() {
		return TechniquePower.maxTargets(spec.dewa());
	}

	/** A child cast (echo, extra cast, replay) sharing this cast's ledger. */
	abstract Caster child(TechniqueSpec other, double childFactor, boolean isEcho);

	// ================================================================== CastContext: identity and snapshot

	@Override
	public TechniqueSpec spec() {
		return spec;
	}

	@Override
	public LivingEntity caster() {
		return caster;
	}

	@Override
	public ServerLevel level() {
		return level;
	}

	@Override
	public CastOptions options() {
		return options;
	}

	@Override
	public long seed() {
		return seed;
	}

	@Override
	public long startTick() {
		return startTick;
	}

	@Override
	public int age() {
		return (int) Math.max(0L, level.getGameTime() - startTick);
	}

	/** Ticks since the timeline started (after a charge). */
	int timelineAge() {
		return (int) Math.max(0L, level.getGameTime() - timelineStart);
	}

	@Override
	public double power() {
		return power;
	}

	@Override
	public int mastery() {
		return mastery;
	}

	@Override
	public TechniquePower.Factors factors() {
		return factors;
	}

	@Override
	public int tier() {
		return factors.t();
	}

	@Override
	public int visualTier() {
		return visualTier;
	}

	@Override
	public boolean kehancuran() {
		return kehancuran;
	}

	@Override
	public boolean arena() {
		return arena;
	}

	@Override
	public boolean echo() {
		return echo;
	}

	@Override
	public double factor() {
		return factor;
	}

	@Override
	public int stage() {
		return stage;
	}

	@Override
	public double chargeShare() {
		return chargeShare;
	}

	@Override
	public Vec3 origin() {
		return origin;
	}

	@Override
	public Vec3 eye() {
		return eye;
	}

	@Override
	public Vec3 look() {
		return look;
	}

	@Override
	public Vec3 aim() {
		return aim;
	}

	@Override
	public @Nullable LivingEntity target() {
		if (specialTarget != null) {
			return specialTarget;
		}
		return target != null && target.isAlive() && !target.isRemoved() ? target : null;
	}

	@Override
	public Vec3 end() {
		return end;
	}

	@Override
	public void markEnd(Vec3 point) {
		if (!precheck) {
			end = point;
		}
	}

	@Override
	public void markPath(Vec3 from, Vec3 to) {
		pathFrom = from;
		pathTo = to;
		end = to;
		destruction.path(from, to);
	}

	@Override
	public void addAfterimage(Vec3 point) {
		if (afterimages.size() < 32) {
			afterimages.add(point);
		}
	}

	@Override
	public Vec3 areaCentre() {
		Fields.Area area = Fields.of(this);
		return area != null ? area.centre() : lastFieldCentre != null ? lastFieldCentre : caster.position();
	}

	@Override
	public double areaRadius() {
		Fields.Area area = Fields.of(this);
		return area != null ? area.radius : lastFieldRadius;
	}

	/** The point NEAR_ANCHOR conditions measure from: the cast's own area, else the aim. */
	Vec3 anchorForConditions() {
		Fields.Area area = Fields.of(this);
		return area != null ? area.centre() : aim;
	}

	@Override
	public CastLedger ledger() {
		return ledger;
	}

	@Override
	public PortalIndex.Cache portals() {
		return portals;
	}

	@Override
	public CompoundTag state() {
		return state;
	}

	// ================================================================== scaled reads

	/** The radius hard cap of this spec (techniqueAreaCap, Dewa ×1.25). */
	double radiusCap() {
		return TechniquePower.radiusCap(spec.dewa());
	}

	@Override
	public double area(double base, double cap) {
		return area(Val.of(base, cap), radiusCap());
	}

	@Override
	public int duration(double base, double cap) {
		return duration(Val.of(base, cap), 0.0D);
	}

	@Override
	public int count(double base, double cap) {
		return count(Val.of(base, cap), 0.0D);
	}

	@Override
	public double area(Val value, double hardCap) {
		double result = TechniquePower.area(value, factors, mastery, hardCap);
		if (Double.isNaN(result)) {
			return result;
		}
		if (areaBonus > 1.0D) {
			result *= areaBonus;
			if (hardCap > 0.0D) {
				result = Math.min(hardCap, result);
			}
		}
		return result;
	}

	@Override
	public int duration(Val value, double hardCap) {
		double result = TechniquePower.duration(value, factors, mastery, 0.0D);
		if (!value.set()) {
			return 0;
		}
		result = result * durationBonus + durationTicks;
		if (hardCap > 0.0D) {
			result = Math.min(hardCap, result);
		}
		return (int) Math.max(0L, Math.round(result));
	}

	@Override
	public int count(Val value, double hardCap) {
		if (!value.set()) {
			return 0;
		}
		return TechniquePower.count(value, factors, hardCap);
	}

	/** A count with the EXTRA_COUNT bonus (projectiles, swords, crescents, bolts...). */
	int countWithBonus(Val value, double hardCap) {
		int base = count(value, 0.0D) + bonusCount;
		return hardCap > 0.0D ? (int) Math.min(hardCap, base) : base;
	}

	@Override
	public double lin(double base, double perTier, double cap) {
		return TechniquePower.lin(base, perTier, cap, factors.above());
	}

	// ================================================================== targets

	@Override
	public boolean moveAllowed(LivingEntity target) {
		if (target instanceof Player || target instanceof Creeper || target instanceof Ravager || target instanceof EnderDragon
				|| target instanceof WitherBoss || target instanceof Warden || target instanceof ElderGuardian) {
			return false;
		}
		return !portals.near(level, target.blockPosition(), PortalIndex.MOVE_RADIUS);
	}

	@Override
	public @Nullable Vec3 safeSpot(Vec3 near) {
		if (!level.isLoaded(BlockPos.containing(near))) {
			return null;
		}
		return Targeting.safeSpot(level, caster, near);
	}

	/**
	 * Hostiles in one box query (several anchor points answered at once, 22.15.1): each half-extent at most the radius
	 * cap, nearest to the box centre first. The caller filters per point.
	 */
	List<LivingEntity> hostilesBox(AABB box, boolean precise) {
		double cap = radiusCap();
		Vec3 c = box.getCenter();
		double hx = Math.max(0.5D, Math.min(box.getXsize() * 0.5D, cap));
		double hy = Math.max(0.5D, Math.min(box.getYsize() * 0.5D, cap));
		double hz = Math.max(0.5D, Math.min(box.getZsize() * 0.5D, cap));
		return query(new AABB(c.x - hx, c.y - hy, c.z - hz, c.x + hx, c.y + hy, c.z + hz), c, precise, null);
	}

	/**
	 * Hostiles in a flattened cylinder (DOMAIN, FIELD, ZONE, RAIN, WAVE): horizontal radius at most 1.42 × the radius
	 * cap (square fields reach their corners), vertical half-height at most 16 (22.15.1 query boxes).
	 */
	List<LivingEntity> hostilesFlat(Vec3 centre, double radius, double halfHeight, boolean precise) {
		double r = Math.max(0.5D, Math.min(radius, radiusCap() * 1.42D));
		double h = Math.max(1.0D, Math.min(halfHeight, TechniquePower.AREA_HEIGHT_CAP));
		return flat(centre, r, h, precise);
	}

	/** A flattened query without the cap (the legacy Divine Sense keeps its 48→96 sense this way). */
	List<LivingEntity> flat(Vec3 centre, double r, double h, boolean precise) {
		AABB box = new AABB(centre.x - r, centre.y - h, centre.z - r, centre.x + r, centre.y + h, centre.z + r);
		double r2 = r * r;
		return query(box, centre, precise, entity -> {
			double dx = entity.getX() - centre.x;
			double dz = entity.getZ() - centre.z;
			return dx * dx + dz * dz <= r2;
		});
	}

	private List<LivingEntity> query(AABB box, Vec3 centre, boolean precise, java.util.function.@Nullable Predicate<LivingEntity> shape) {
		List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity.isAlive()
				&& (shape == null || shape.test(entity)) && mayHit(entity, precise));
		list.sort(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(centre)));
		return list;
	}

	/** Allies of the caster within {@code radius} (never monsters, never the caster itself). */
	List<LivingEntity> alliesExceptSelf(Vec3 centre, double radius) {
		List<LivingEntity> list = new ArrayList<>(allies(centre, radius));
		list.remove(caster);
		return list;
	}

	// ================================================================== damage

	@Override
	public DamageContext damage(LivingEntity target, double coef) {
		return new DamageContext(DamageContext.Kind.HIT, caster, target, spec, coef, coef, 0.0D, power, mastery,
				factor * Boons.empower(caster, spec.dao()), false, false, ledger);
	}

	@Override
	public boolean hurt(DamageContext context) {
		LivingEntity victim = context.target();
		if (victim == null || !casterValid() || !mayHit(victim, context.precise())) {
			return false;
		}
		float before = victim.getHealth() + victim.getAbsorptionAmount();
		boolean dragonWasDying = dragonDying(victim);
		boolean landed = chokeHurt(context);
		if (landed) {
			afterHit(victim, before, context, !dragonWasDying && dragonDying(victim));
		}
		return landed;
	}

	/**
	 * The Ender Dragon never dies through {@code isDeadOrDying}: its killing blow sets health 1 and the DYING phase
	 * (mcsrc {@code EnderDragon.handleKillingBlow}), so the phase is what tells a technique kill.
	 */
	private static boolean dragonDying(LivingEntity victim) {
		return victim instanceof EnderDragon dragon
				&& dragon.getPhaseManager().getCurrentPhase().getPhase() == net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.DYING;
	}

	@Override
	public boolean hurt(LivingEntity target, double coef, boolean precise) {
		return hurt(damage(target, coef).precise(precise));
	}

	/** Records a landed hit: recall list, hit counts, first-hit comprehension, hooks, kills, dummy readouts. */
	void afterHit(LivingEntity victim, float before, DamageContext context, boolean dragonKilled) {
		lastDealt = Math.max(0.0F, before - (victim.getHealth() + victim.getAbsorptionAmount()));
		boolean dead = victim.isDeadOrDying() || dragonKilled;
		if (dead) {
			lastDealt = Math.max(lastDealt, before);
		}
		boolean dummy = Dummies.isDummy(victim);
		if (dummy) {
			Dummies.record(victim, rawOf(context), lastDealt);
		}
		if (tally != null) {
			tally.add(victim, rawOf(context), lastDealt);
		}
		hits.merge(victim.getUUID(), 1, Integer::sum);
		if (hitOrder.size() < TechniquePower.RECALL_MAX * 2) {
			hitOrder.add(victim);
		}
		if (!dummy && !(victim instanceof Player) && !TechniquePower.isOwnHeartDemon(caster, victim)) {
			hitAny = true;
			if (TechniquePower.isBoss(victim)) {
				hitBoss = true;
			}
			if (!comprehensionGiven && parent == null && !options.test()) {
				comprehensionGiven = true;
				onFirstHit(victim);
			}
		}
		Boons.onTechniqueHit(caster, victim);
		if (dead) {
			kills++;
			if (!dummy) {
				killedAny = true;
				if (parent == null && !options.test()) {
					onKill(victim);
				}
			}
		}
	}

	/** The amount dealt by the last landed hit (DRAIN, LINK, dummies). */
	float lastDealt;

	/** Landed hits on {@code target} so far in this cast (Cond HIT_BEFORE). */
	int hitsOn(LivingEntity target) {
		return hits.getOrDefault(target.getUUID(), 0);
	}

	/** The raw damage before the clamps (dummy readout). */
	private double rawOf(DamageContext context) {
		return switch (context.kind()) {
			case LEGACY -> context.raw();
			case PERCENT -> context.coef() * context.target().getMaxHealth();
			case EXEC -> context.target().getHealth();
			default -> context.coef() * context.power() * TechniquePower.masteryDamage(context.mastery()) * context.f();
		};
	}

	/** A DOT tick of {@code perSecond} × P (kind DOT: boss ticks ≤ 2%). */
	void dotHurt(LivingEntity target, double perSecond, boolean pierce, Map<Payload.Versus, Double> versus) {
		if (!target.isAlive() || !casterValid()) {
			return;
		}
		double f = factor * TechniquePower.versusFactor(versus, classes(target), spec.dao(), false);
		hurt(new DamageContext(DamageContext.Kind.DOT, caster, target, spec, perSecond, perSecond, 0.0D, power, mastery, f, false,
				pierce, ledger));
	}

	/** LINK: damage actually dealt to one member, shared to another (never triggers another LINK). */
	void linkHurt(LivingEntity target, double amount) {
		if (!(amount > 0.0D) || !casterValid()) {
			return;
		}
		hurt(raw(target, amount, DamageContext.Kind.LINK));
	}

	/** REFLECT: a share of a melee hit thrown back through the choke point and the granting cast's cap. */
	void reflectHurt(LivingEntity attacker, double amount) {
		if (!(amount > 0.0D) || !casterValid()) {
			return;
		}
		hurt(raw(attacker, amount, DamageContext.Kind.REFLECT).precise(true));
	}

	/** A damage context worth {@code amount} before the clamps. */
	public DamageContext raw(LivingEntity target, double amount, DamageContext.Kind kind) {
		double scale = Math.max(1.0E-6D, power * TechniquePower.masteryDamage(mastery));
		return new DamageContext(kind, caster, target, spec, amount / scale, amount / scale, 0.0D, power, mastery, 1.0D, false, false, ledger);
	}

	/** The versus classes of a target plus a WET mark. */
	static Set<Payload.Versus> classes(LivingEntity target) {
		Set<Payload.Versus> set = TechniquePower.classify(target);
		if (StatusTracker.wet(target)) {
			set.add(Payload.Versus.WET);
		}
		return set;
	}

	// ================================================================== payloads on hostiles

	/** Per-target data of one payload pass. */
	static final class HitInfo {
		double coefFactor = 1.0D;
		double f = 1.0D;
		double edge;
		boolean precise;
		DamageContext.Kind kind = DamageContext.Kind.HIT;
		@Nullable Vec3 centre;
		double radius;
		double storedDamage;

		static HitInfo of(boolean precise) {
			HitInfo info = new HitInfo();
			info.precise = precise;
			return info;
		}
	}

	@Override
	public void apply(LivingEntity target, Payload.Hit payload) {
		applyHit(target, payload, HitInfo.of(true));
	}

	/** Applies one hostile payload with the pass data {@code info}. */
	void applyHit(LivingEntity target, Payload.Hit payload, HitInfo info) {
		if (!target.isAlive() || target.isRemoved() || !mayHit(target, info.precise)) {
			return;
		}
		boolean playerTarget = target instanceof Player;
		switch (payload) {
			case Payload.Dmg dmg -> {
				double coef = dmg.coef();
				if (!Double.isNaN(dmg.edge()) && info.radius > 0.0D && info.centre != null) {
					double t = Math.max(0.0D, Math.min(1.0D, target.position().distanceTo(info.centre) / info.radius));
					coef = dmg.coef() + (dmg.edge() - dmg.coef()) * t;
				}
				coef += switch (dmg.perKind()) {
					case STACK -> dmg.per() * stacks();
					case STORED -> dmg.per() * info.storedDamage / Math.max(1.0E-6D, power);
					case SUMMON -> dmg.per() * Phantoms.of(this);
					default -> 0.0D;
				};
				coef *= info.coefFactor;
				if (dmg.byCharge()) {
					coef *= chargeShare;
				}
				double f = info.f * factor * Boons.empower(caster, spec.dao())
						* StatusTracker.markFactor(caster, target, spec.dao(), factors.above(), true)
						* TechniquePower.versusFactor(dmg.versus(), classes(target), spec.dao(), dmg.shatters());
				DamageContext context = new DamageContext(info.kind, caster, target, spec, coef, coef, 0.0D, power, mastery, f,
						info.precise, dmg.piercing(), ledger);
				lastDealt = 0.0F;
				hurt(context);
				if (dmg.shatters() && (target.isFullyFrozen() || target.getTicksFrozen() > 0)) {
					StatusTracker.unfreeze(target);
				}
			}
			case Payload.DmgPct pct -> {
				double share = pct.share();
				double boss = pct.bossShare();
				if (arena || TechniquePower.inArena(caster, target)) {
					// every arena partner (players, disciples, rivals, Void Realm phantoms): at most 15% per cast (22.14.5)
					share = Math.min(share, TechniquePower.ARENA_PLAYER_SHARE);
					boss = Math.min(boss, TechniquePower.ARENA_PLAYER_SHARE);
				}
				lastDealt = 0.0F;
				hurt(new DamageContext(DamageContext.Kind.PERCENT, caster, target, spec, share * factor, boss * factor, 0.0D, 0.0D, 0, 1.0D,
						info.precise, false, ledger));
			}
			case Payload.Dot dot -> StatusTracker.addDot(this, target, dot, dot.perSecond() * info.coefFactor, (int) Math.round(dot.seconds() * durationBonus));
			case Payload.Ignite ignite -> DaoTechniques.ignite(target, (float) (ignite.seconds() * durationBonus + durationTicks / 20.0D));
			case Payload.Freeze freeze -> {
				int ticks = controlTicks(freeze.ticks(), playerTarget);
				if (StatusTracker.freeze(target, ticks, caster) && ticks >= 30 && !playerTarget) {
					// a frost shell on every client for the freeze (DESIGN 27.4)
					VisualSync.bind(target, spec.bindLook() == BindKind.TIME_STOP ? BindKind.TIME_STOP : BindKind.FREEZE, ticks, caster, spec.netId());
				}
			}
			case Payload.Bind bind -> {
				if (!playerTarget) {
					StatusTracker.bind(this, target, bind.kind(), duration(bind.ticks(), 0.0D), bind.bossTicks());
				}
			}
			case Payload.Move move -> move(target, move, info);
			case Payload.Effect effect -> effect(target, effect, playerTarget);
			case Payload.Marked marked -> StatusTracker.mark(this, target, marked.mark(), (int) Math.round(marked.ticks() * durationBonus) + durationTicks);
			case Payload.Attr attr -> {
				double amount = TechniquePower.isBoss(target) ? attr.amount() * attr.bossFactor() : attr.amount();
				StatusTracker.attr(target, attr.attribute(), amount, attr.percent(), (int) Math.round(attr.ticks() * durationBonus) + durationTicks);
			}
			case Payload.Exec exec -> exec(target, exec, playerTarget);
			case Payload.Drain drain -> drain(drain);
			case Payload.Untarget untarget -> StatusTracker.untarget(target);
			case Payload.Taunt taunt -> StatusTracker.taunt(target, caster, taunt.ticks());
			case Payload.Strip strip -> {
				for (var effect : strip.effects().isEmpty() ? Payload.Strip.DEFAULT_BUFFS : strip.effects()) {
					target.removeEffect(effect);
				}
			}
			case Payload.Link link -> pendingLink.add(target);
			case Payload.ChanceHit chance -> {
				if (level.getRandom().nextDouble() < chance.chance()) {
					applyHit(target, chance.payload(), info);
				}
			}
			case Payload.Special special -> special(special.id(), target);
		}
	}

	/** Targets of the current pass that a LINK payload groups (linked after the pass). */
	final List<LivingEntity> pendingLink = new ArrayList<>();

	/** Control duration with the arena rule (control ×0.3 on players). */
	int controlTicks(int ticks, boolean playerTarget) {
		int scaled = (int) Math.round(ticks * durationBonus) + durationTicks;
		return playerTarget ? (int) Math.round(scaled * 0.3D) : scaled;
	}

	private void effect(LivingEntity target, Payload.Effect effect, boolean playerTarget) {
		if (effect.effect() == Payload.Eff.GLOWING && ++glowing > TechniquePower.GLOWING_PER_CAST) {
			return;
		}
		int ticks = TechniquePower.isBoss(target) && effect.bossTicks() >= 0 ? effect.bossTicks() : duration(effect.ticks(), 0.0D);
		if (effect.effect() == Payload.Eff.LEVITATION) {
			ticks = Math.min(40, ticks);
			if (!moveAllowed(target)) {
				return;
			}
		}
		if (playerTarget) {
			ticks = (int) Math.round(ticks * 0.3D);
		}
		if (ticks > 0) {
			target.addEffect(new MobEffectInstance(effect.effect().holder(), ticks, Math.max(0, effect.level() - 1), false, true), caster);
		}
	}

	private void exec(LivingEntity target, Payload.Exec exec, boolean playerTarget) {
		if (playerTarget || arena || TechniquePower.inArena(caster, target)) {
			// never an execution in a bout: duels keep their pre-6c numbers (22.14.5)
			return;
		}
		boolean boss = TechniquePower.isBoss(target);
		float share = target.getHealth() / Math.max(1.0F, target.getMaxHealth());
		boolean below;
		if (exec.belowPower() > 0.0D) {
			// trial mobs are measured against legacy power × 1.5, never P (22.5.5 step 4, 22.14.4)
			double measure = TechniquePower.isTrialMob(target) ? TechniquePower.legacyPowerOf(caster, spec) * TechniquePower.TRIAL_MOB_FACTOR : power;
			below = !boss && target.getMaxHealth() < exec.belowPower() * measure;
		} else {
			below = boss ? share < Math.min(TechniquePower.EXEC_BOSS_HEALTH, exec.bossThreshold()) : share < exec.threshold();
		}
		if (below) {
			hurt(DamageContext.exec(caster, target, spec, ledger));
		}
	}

	private void drain(Payload.Drain drain) {
		double dealt = lastDealt;
		if (!(dealt > 0.0D)) {
			return;
		}
		if (drain.share() > 0.0D) {
			float heal = (float) (dealt * drain.share());
			float missing = caster.getMaxHealth() - caster.getHealth();
			caster.heal(heal);
			if (drain.absorbCap() > 0.0D && heal > missing) {
				float cap = (float) (caster.getMaxHealth() * drain.absorbCap());
				caster.setAbsorptionAmount(Math.min(cap, caster.getAbsorptionAmount() + (heal - missing)));
			}
		}
		if (drain.allyShare() > 0.0D) {
			// collected over the pass: one ally query after it (22.15.1), not one per target hit
			pendingAllyHeal += dealt * drain.allyShare();
			pendingAllyRadius = Math.max(pendingAllyRadius, Math.max(1.0D, drain.allyRadius()));
			if (!passOpen) {
				flushAllyHeal();
			}
		}
		if (drain.qiShare() > 0.0D) {
			double room = Math.max(0.0D, maxQi() * 0.10D - drainQi);
			double amount = Math.min(room, dealt * drain.qiShare());
			if (amount > 0.0D) {
				drainQi += amount;
				restoreQi(amount);
			}
		}
	}

	/** Heals the allies around the caster by the DRAIN share collected in this pass (one query). */
	void flushAllyHeal() {
		double heal = pendingAllyHeal;
		double radius = pendingAllyRadius;
		pendingAllyHeal = 0.0D;
		pendingAllyRadius = 0.0D;
		if (!(heal > 0.0D) || !casterValid()) {
			return;
		}
		for (LivingEntity ally : alliesExceptSelf(caster.position(), radius)) {
			ally.heal((float) heal);
		}
	}

	/** KNK / PULL / LCH / SLAM with the never-moved list, the portal rule, NoGrief and a portal cooldown. */
	private void move(LivingEntity target, Payload.Move move, HitInfo info) {
		if (!moveAllowed(target)) {
			return;
		}
		Vec3 centre = switch (move.center()) {
			case ANCHOR -> info.centre != null ? info.centre : aim;
			case AREA -> areaCentre();
			default -> caster.position();
		};
		double strength = move.strength().set() ? TechniquePower.area(move.strength(), factors, 0, 0.0D) : 0.5D;
		Vec3 away = target.position().subtract(centre).horizontal();
		double distance = away.length();
		if (move.byCloseness() && info.radius > 0.0D) {
			double closeness = 1.0D - Math.min(1.0D, distance / info.radius);
			strength = move.strength().base() + (move.strength().cap() - move.strength().base()) * closeness;
			strength *= factors.a();
		}
		double resist = Math.max(0.0D, Math.min(1.0D, target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
		if (TechniquePower.isBoss(target)) {
			resist = Math.max(resist, 0.5D);
		}
		Vec3 dir = distance < 1.0E-3D ? look.horizontal() : away.scale(1.0D / distance);
		dir = dir.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : dir.normalize();
		if (move.spin()) {
			dir = new Vec3(-dir.z, 0.0D, dir.x);
		}
		target.addTag(NoGrief.TAG);
		target.setPortalCooldown();
		Vec3 motion = target.getDeltaMovement();
		Vec3 velocity;
		switch (move.kind()) {
			case PULL -> {
				if (move.continuous()) {
					Vec3 fixed = centre;
					StatusTracker.pull(target, move.center() == Payload.Center.CASTER ? () -> caster.isAlive() ? caster.position() : null : () -> fixed,
							strength * (1.0D - resist * 0.5D), move.ticks());
					return;
				}
				double pull = move.inBlocks() ? Math.min(distance, strength) * 0.35D : strength;
				velocity = new Vec3(-dir.x * pull * (1.0D - resist), Math.max(motion.y, 0.1D), -dir.z * pull * (1.0D - resist));
			}
			case LAUNCH -> velocity = new Vec3(motion.x * 0.5D + dir.x * 0.2D, Math.max(motion.y, strength * (1.0D - resist * 0.5D)),
					motion.z * 0.5D + dir.z * 0.2D);
			case SLAM -> velocity = new Vec3(motion.x * 0.3D, -strength * (1.0D - resist * 0.5D), motion.z * 0.3D);
			default -> velocity = new Vec3(motion.x * 0.5D + dir.x * strength * (1.0D - resist), Math.max(motion.y, 0.25D * (1.0D - resist)),
					motion.z * 0.5D + dir.z * strength * (1.0D - resist));
		}
		target.setDeltaMovement(velocity);
		target.needsSync = true;
		target.syncVelocity = true;
	}

	/** Runs a special handler for one target ({@code target()} and {@code state.target} name it during the call). */
	void special(String id, @Nullable LivingEntity forTarget) {
		SpecialTechnique handler = dev.kultivator.data.TechniqueRegistry.special(id);
		if (handler == null) {
			return;
		}
		LivingEntity previous = specialTarget;
		specialTarget = forTarget;
		if (forTarget != null) {
			state.putInt("target", forTarget.getId());
		} else {
			state.remove("target");
		}
		try {
			handler.cast(this);
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.error("Kultivator: special handler {} failed", id, exception);
		} finally {
			specialTarget = previous;
		}
	}

	// ================================================================== payloads on the caster and allies

	@Override
	public void applySelf(Payload.Boon payload) {
		applyBoon(caster, payload, true, false);
	}

	@Override
	public void applyAlly(LivingEntity ally, Payload.Boon payload) {
		if (ally == caster) {
			applySelf(payload);
		} else {
			applyBoon(ally, payload, false, false);
		}
	}

	/**
	 * Applies a boon to {@code who}. {@code self}: the caster (movement, forms, summons and costs only work on the
	 * caster). {@code whileCast}: durations last as long as the cast (toggles, forms, domains).
	 */
	void applyBoon(LivingEntity who, Payload.Boon payload, boolean self, boolean pulse) {
		boolean whileCast = spec.toggle() != null;
		switch (payload) {
			case Payload.Dash dash -> {
				if (self) {
					dash(dash);
				}
			}
			case Payload.Blink blink -> {
				if (self) {
					Steps.blink(this, blink);
				}
			}
			case Payload.Heal heal -> {
				double share = heal.share().set() ? Math.min(heal.share().cap(), heal.share().base() * TechniquePower.masteryDuration(mastery)) : 0.0D;
				if (heal.over() > 0) {
					int pulses = Math.max(1, heal.over() / 20);
					float each = (float) (who.getMaxHealth() * share / pulses);
					for (int i = 0; i < pulses; i++) {
						schedule(i * 20, () -> {
							if (who.isAlive()) {
								who.heal(each);
							}
						});
					}
				} else {
					who.heal((float) (who.getMaxHealth() * share));
				}
			}
			case Payload.Cleanse cleanse -> {
				for (var effect : cleanse.effects().isEmpty() ? Payload.Cleanse.DEFAULT : cleanse.effects()) {
					who.removeEffect(effect);
				}
			}
			case Payload.Shield shield -> {
				if (!pulse) {
					QiShield.shield(who, who.getMaxHealth() * shield.share(), whileCast && !shield.ticks().set() ? 20 * 60 * 20 : duration(shield.ticks(), 0.0D));
				}
			}
			case Payload.Buff buff -> {
				boolean lasting = !buff.ticks().set();
				Boons.buff(this, who, buff, duration(buff.ticks(), 0.0D), lasting || whileCast && who == caster);
			}
			case Payload.Dr dr -> {
				double share = dr.share().set() ? Math.min(dr.share().cap(), dr.share().base() * TechniquePower.masteryArea(mastery)) : 0.0D;
				int ticks = dr.ticks().set() ? duration(dr.ticks(), 0.0D) : 20 * 60 * 20;
				QiShield.dr(who, share, dr, ticks);
			}
			case Payload.Dodge dodge -> QiShield.dodge(who, dodge.chance(), dodge.ticks().set() ? duration(dodge.ticks(), 0.0D) : 20 * 60 * 20);
			case Payload.Transform transform -> {
				if (self && !pulse) {
					int ticks = duration(transform.ticks(), 0.0D);
					double scale = transform.scale().set() ? TechniquePower.area(transform.scale(), factors, 0, Forms.MAX_SCALE) : 1.0D;
					if (Forms.start(this, who, transform, ticks, scale)) {
						formActive = true;
						activity(1);
						for (Payload.Boon effect : transform.effects()) {
							applyBoon(who, effect, true, false);
						}
					}
				}
			}
			case Payload.Summon summon -> {
				if (self && !pulse) {
					int count = countWithBonus(summon.count(), TechniquePower.PHANTOMS_PER_CAST);
					if (disciple() != null && spec.halfForDisciples()) {
						count = (count + 1) / 2;
					}
					double range = summon.range().set() ? area(summon.range(), radiusCap()) : 12.0D;
					int made = Phantoms.summon(this, summon, count, duration(summon.ticks(), 0.0D), range, aim);
					activity(made);
					if (made > 0) {
						int ticks = duration(summon.ticks(), 0.0D);
						if (!castFxSent) {
							castFx(caster.position(), aim, (float) range, 0.0F, made, ticks);
						} else {
							fx(currentPhase == Phase.CAST ? Phase.PULSE : currentPhase, caster.position(), aim, (float) range, 0.0F, made, ticks);
						}
						Phantoms.sync(this);
					}
				}
			}
			case Payload.RestoreQi restore -> {
				if (self) {
					double share = Math.min(0.30D, restore.share());
					double amount = maxQi() * share;
					if (restore.capPerCast() > 0.0D) {
						amount = Math.min(amount, Math.max(0.0D, maxQi() * restore.capPerCast() - restoredQi));
					}
					restoredQi += amount;
					restoreQi(amount);
				}
			}
			case Payload.CdReset reset -> {
				if (self) {
					resetCooldowns(reset.maxGrade());
				}
			}
			case Payload.GrantHook grant -> {
				if (self && !pulse) {
					Val ticks = grant.hook().ticks();
					Boons.hook(this, who, grant.hook(), ticks.set() ? duration(ticks, 0.0D) : 0, !ticks.set());
				}
			}
			case Payload.Counter counter -> {
				if (self && !pulse) {
					QiShield.counter(this, who, counter, duration(counter.window(), 0.0D));
					activity(1);
					schedule(duration(counter.window(), 0.0D) + 1, () -> activity(-1));
				}
			}
			case Payload.CheatDeath cheat -> {
				if (self && !pulse) {
					QiShield.cheatDeath(this, who, count(cheat.charges(), 0.0D), duration(cheat.ticks(), 0.0D), cheat.healthShare(), cheat.onTrigger());
				}
			}
			case Payload.ProjectileGuard guard -> {
				if (self && !pulse) {
					boolean lasting = !guard.ticks().set();
					Boons.guard(this, who, guard, count(guard.charges(), 0.0D), duration(guard.ticks(), 0.0D), guard.radius(), lasting);
				}
			}
			case Payload.Revert revert -> {
				if (self) {
					Forms.end(who, EndReason.CANCELLED);
				}
			}
			case Payload.SelfPenalty penalty -> {
				if (self) {
					pendingPenalties.add(penalty);
				}
			}
			case Payload.ForbiddenCost cost -> {
				if (self && !pulse) {
					forbiddenCost(cost);
				}
			}
			case Payload.Comprehension gain -> {
				if (self) {
					double amount = gain.amount() > 0.0D ? gain.amount() : gain.perSecond();
					if (gain.capPerCast() > 0.0D) {
						amount = Math.min(amount, Math.max(0.0D, gain.capPerCast() - comprehension));
					}
					if (amount > 0.0D && !options.test() && parent == null) {
						comprehension += amount;
						gainComprehension(amount);
					}
				}
			}
			case Payload.Extinguish extinguish -> who.clearFire();
			case Payload.Special special -> special(special.id(), null);
		}
	}

	boolean formActive;
	final List<Payload.SelfPenalty> pendingPenalties = new ArrayList<>();
	Phase currentPhase = Phase.CAST;

	/** FORBIDDEN_COST: {@code setHealth(max(1, …))}, never damage; the 2-HP refusal was checked before spending. */
	private void forbiddenCost(Payload.ForbiddenCost cost) {
		float basis = cost.ofMax() ? caster.getMaxHealth() : caster.getHealth();
		caster.setHealth(Math.max(1.0F, caster.getHealth() - (float) (basis * cost.hpShare())));
		if (cost.qiShare() > 0.0D) {
			takeQi(maxQi() * cost.qiShare());
		}
	}

	/** DASH along the look (or toward the target): through open, loaded space only, ending at the last safe spot. */
	void dash(Payload.Dash dash) {
		double distance = Math.min(MOVE_CAP, dash.distance().set() ? area(dash.distance(), TechniquePower.DASH_CAP) : 8.0D);
		Vec3 from = caster.position();
		Vec3 dir = switch (dash.dir()) {
			case TARGET -> {
				LivingEntity locked = target();
				yield locked != null ? locked.position().subtract(from).normalize() : look;
			}
			case UP -> new Vec3(0.0D, 1.0D, 0.0D);
			case MOVE -> {
				Vec3 moving = caster.getKnownMovement().horizontal();
				yield moving.lengthSqr() > 1.0E-4D ? moving.normalize() : look.horizontal().normalize();
			}
			default -> look;
		};
		if (dir.lengthSqr() < 1.0E-6D) {
			dir = new Vec3(0.0D, 0.0D, 1.0D);
		}
		Vec3 chest = from.add(0.0D, caster.getBbHeight() * 0.5D, 0.0D);
		BlockHitResult hit = LoadedPath.ray(level, chest, chest.add(dir.scale(distance)), caster);
		Vec3 stop = hit.getLocation().subtract(dir.scale(caster.getBbWidth() * 0.6D + 0.3D));
		Vec3 feet = stop.subtract(0.0D, caster.getBbHeight() * 0.5D, 0.0D);
		Vec3 spot = null;
		for (int back = 0; back <= 4 && spot == null; back++) {
			spot = safeSpot(feet.subtract(dir.scale(back * 1.0D)));
		}
		if (spot == null) {
			return;
		}
		// a real move over a few ticks (DESIGN 27.1 S16): the path is checked now, the body travels it, the end is snapped safe
		int ticks = Moves.dashTicks(dash.ticks(), from.distanceTo(spot));
		Boons.noFall(caster, Math.max(40, dash.noFall() + ticks));
		Boons.iframes(caster, dash.iframes() > 0 ? dash.iframes() + ticks : 0);
		markPath(from, spot);
		addAfterimage(from);
		fx(Phase.TRAVEL, from, spot, (float) caster.getBbWidth(), (float) from.distanceTo(spot), 1, ticks, -1, 0, 1, 0,
				Payloads.TechFx.FLAG_BLINK);
		Moves.glide(this, from, spot, ticks, 0.0D, null, null);
	}

	/** Moves the caster to a checked spot facing {@code yaw}/{@code pitch} (absolute: the player's camera turns too). */
	void teleport(Vec3 spot, float yaw, float pitch) {
		if (caster instanceof ServerPlayer player) {
			player.teleportTo(level, spot.x, spot.y, spot.z, java.util.Set.of(), yaw, pitch, false);
		} else {
			caster.snapTo(spot.x, spot.y, spot.z, yaw, pitch);
			caster.setYHeadRot(yaw);
			caster.setDeltaMovement(Vec3.ZERO);
			caster.needsSync = true;
		}
		caster.resetFallDistance();
		caster.setPortalCooldown();
	}

	/** Moves the caster to a checked spot (players with a teleport packet, disciples with snapTo). */
	void teleport(Vec3 spot) {
		if (caster instanceof ServerPlayer player) {
			player.teleportTo(level, spot.x, spot.y, spot.z, Relative.ROTATION, 0.0F, 0.0F, false);
		} else {
			caster.snapTo(spot.x, spot.y, spot.z, caster.getYRot(), caster.getXRot());
			caster.setDeltaMovement(Vec3.ZERO);
			caster.needsSync = true;
		}
		caster.resetFallDistance();
		caster.setPortalCooldown();
	}

	// ================================================================== stacks

	/** STACK: recast stacks (window) or kills within this cast, at most the spec's max. */
	int stacks() {
		if (spec.stack() == null) {
			return 0;
		}
		return switch (spec.stack().source()) {
			case ON_KILL -> Math.min(spec.stack().max(), kills);
			case RECAST -> Math.min(spec.stack().max(), recastStacks);
		};
	}

	int recastStacks;

	/** The F part of STACK (inside the ×3 cap). */
	double stackFactor() {
		if (spec.stack() == null || spec.stack().perStack() == 0.0D) {
			return 1.0D;
		}
		return 1.0D + spec.stack().perStack() * stacks();
	}

	// ================================================================== FX and sound

	@Override
	public void fx(Phase phase, Vec3 at, Vec3 to, float radius, float length, int count, int duration) {
		fx(phase, at, to, radius, length, count, duration, -1, 0, 1, 0, 0);
	}

	/**
	 * One TechFx v2 event (DESIGN 27.4): {@code target} the entity it is about (-1 none), {@code index}/{@code of} its place
	 * in a group, {@code aux} by flag or phase, {@code extraFlags} the event's own {@code FLAG_*} bits (FOLLOW, PRE_IMPACT,
	 * BLINK, VOLLEY, DOT, TWO_DAO). Never a NaN or infinite float on the wire; the phase's sound plays even when the event
	 * itself is capped; CAST, CHARGE, END and ABORT are never capped, HIT has its own cap and folds the rest into one
	 * MASS_HIT per tick.
	 */
	@Override
	public void fx(Phase phase, Vec3 at, Vec3 to, float radius, float length, int count, int duration, int target, int index, int of,
			int aux, int extraFlags) {
		if (!finite(at)) {
			return;
		}
		if (!finite(to)) {
			to = at;
		}
		boolean legacyPhase = spec.isLegacy() && (phase == Phase.CAST || phase == Phase.END || phase == Phase.IMPACT);
		if (phase == Phase.CAST) {
			// a handler's own CAST event counts as the cast's one CAST event (the runner's castFx then sends no second)
			castFxSent = true;
			if (duration <= 0) {
				duration = castDuration();
			}
		}
		// the sound of a phase plays whatever its layers and the event caps say (S11); Phase 10 (DESIGN 28.4.3): the climax
		// cues of a silent spec are played by every client after the silence, so the server skips them on the event that
		// carries FLAG_CLIMAX (decided below, once the event is known to go out)
		boolean silentCandidate = silentClimax(phase);
		if (!silentCandidate) {
			sound(phase, at);
		}
		swingFor(phase);
		// HIT, MASS_HIT, ABORT and PHANTOM always go out: a spec without hit layers gets the client's default hit spark, an
		// abort ends whatever the client still draws, a phantom attack lunges the phantom.
		if (spec.fx(phase).isEmpty() && !legacyPhase && phase != Phase.HIT && phase != Phase.MASS_HIT && phase != Phase.ABORT
				&& phase != Phase.PHANTOM) {
			if (silentCandidate) {
				sound(phase, at);
			}
			return;
		}
		long now = level.getGameTime();
		switch (phase) {
			case CAST, CHARGE, END, ABORT -> {
			}
			case HIT -> {
				if (now - hitWindow >= 20L) {
					hitWindow = now;
					hitCount = 0;
				}
				if (hitCount >= TechniquePower.FX_HITS_PER_SECOND) {
					fold(at, now);
					return;
				}
				hitCount++;
			}
			default -> {
				if (now - fxWindow >= 20L) {
					fxWindow = now;
					fxCount = 0;
				}
				if (fxCount >= TechniquePower.FX_EVENTS_PER_SECOND) {
					if (silentCandidate) {
						sound(phase, at);
					}
					return;
				}
				fxCount++;
			}
		}
		if (silentCandidate) {
			// this event is the climax: its cues belong to the clients (same tick, same phase stays silent here)
			markSounded(phase);
		}
		send(phase, at, to, radius, length, count, duration, target, index, of, aux, extraFlags, now);
	}

	/**
	 * True when the next event of {@code phase} will carry FLAG_CLIMAX for a spec whose Dampak is silent (DESIGN 28.4.3):
	 * the same test {@link #send} uses to set the flag.
	 */
	private boolean silentClimax(Phase phase) {
		dev.kultivator.technique.spec.dsl.Dampak dampak = spec.dampak();
		return dampak != null && dampak.silent() && !climaxSent && parent == null && !echo && phase == spec.climaxPhase()
				&& phase != Phase.ABORT;
	}

	/**
	 * The aim a Dampak spec's CAST and CHARGE carry in {@code tx,ty,tz} (DESIGN 28.2, server contract M1): the caster's
	 * feet for ring-only domains and caster-centred ultimates (self forms, novas, domains, waves; not specials), else the
	 * far end of the spec's beam, else the event's own second point when it lies away from the caster (a zone or field
	 * centre, a target, the end of a front), else the event's position when that lies away from the caster (a delayed
	 * zone's CAST sits on its centre), else the feet.
	 */
	private Vec3 dampakAim(dev.kultivator.technique.spec.dsl.Dampak dampak, Vec3 at, Vec3 to) {
		Vec3 feet = caster.position();
		if (spec.titah()) {
			// Phase 10b (DESIGN 29.3.1): a Titah decree's own CAST carries the resolved crosshair point (≤ titahRange), every
			// mark kind included; the sigil, the mark and the film are framed on it
			return finite(to) ? to : feet;
		}
		if (dampak.mark() == dev.kultivator.technique.spec.dsl.Dampak.Mark.RING_ONLY) {
			return feet;
		}
		Vec3 eyePos = caster.getEyePosition();
		boolean aimed = spec.special() != null;
		for (Step step : spec.timeline()) {
			if (step.shape() instanceof dev.kultivator.technique.spec.dsl.Shape.Beam beam) {
				Vec3 far = eyePos.add(look.scale(Steps.beamLength(this, beam)));
				return finite(far) ? far : to;
			}
			switch (step.shape().kind()) {
				case STRIKE, CONE, FRONT, ZONE, PILLAR, RAIN, CHAIN, TARGET, VOLLEY, FIELD, PATH -> aimed = true;
				default -> {
				}
			}
		}
		if (!aimed) {
			// a caster-centred ultimate: heaven marks the caster
			return feet;
		}
		if (to.distanceToSqr(feet) > 2.25D && to.distanceToSqr(eyePos) > 2.25D) {
			return to;
		}
		if (at.distanceToSqr(feet) > 9.0D) {
			return at;
		}
		return feet;
	}

	/** The CAST (or CHARGE) of a Dampak spec as sent: where it started and the resolved aim (the sigil's frame, 28.9.1). */
	private @Nullable Vec3 dampakCastPos;
	private @Nullable Vec3 dampakCastAim;

	/** Builds and sends one event (no caps; the caller checked them). */
	private void send(Phase phase, Vec3 at, Vec3 to, float radius, float length, int count, int duration, int target, int index, int of,
			int aux, int extraFlags, long now) {
		dev.kultivator.technique.spec.dsl.Dampak dampak = spec.dampak();
		if (dampak != null && parent == null && !echo && (phase == Phase.CAST || phase == Phase.CHARGE)) {
			// Phase 10 (DESIGN 28.2): every CAST and CHARGE of a Dampak spec carries the resolved aim
			to = dampakAim(dampak, at, to);
			dampakCastPos = at;
			dampakCastAim = to;
		}
		int flags = extraFlags & 0x7FFF;
		if (kehancuran) {
			flags |= Payloads.TechFx.FLAG_KEHANCURAN;
		}
		if (echo) {
			flags |= Payloads.TechFx.FLAG_ECHO;
		}
		if (TechniqueRunner.lowLoad()) {
			flags |= Payloads.TechFx.FLAG_LOW_LOAD;
		}
		if (hitBossThisPass) {
			flags |= Payloads.TechFx.FLAG_BOSS_HIT;
		}
		if (caster instanceof DiscipleEntity) {
			flags |= Payloads.TechFx.FLAG_DISCIPLE;
		}
		if (allyPass) {
			flags |= Payloads.TechFx.FLAG_ALLY;
		}
		if (options.showcase()) {
			flags |= Payloads.TechFx.FLAG_SHOWCASE;
		}
		if (followArea && (phase == Phase.CAST || phase == Phase.PULSE)) {
			flags |= Payloads.TechFx.FLAG_FOLLOW;
		}
		if (!climaxSent && parent == null && !echo && phase == spec.climaxPhase() && phase != Phase.ABORT) {
			climaxSent = true;
			flags |= Payloads.TechFx.FLAG_CLIMAX;
			if (dampak != null && dampak.sigilTrench() && dampakCastAim != null && dampakCastPos != null) {
				// Phase 10 (DESIGN 28.9.3): the sigil's lines burn with the climax's shapes (Mode Kehancuran only)
				destruction.sigil(dampakCastPos, dampakCastAim, (int) (seed ^ (seed >>> 32)));
			}
			if (dampak != null && dampak.monument() != null && !spec.titah() && !options.test()
					&& caster instanceof net.minecraft.server.level.ServerPlayer owner) {
				// Phase 10b (DESIGN 29.0): with the op's titahUltimateMonuments the sword ultimates leave a planted weapon too
				dev.kultivator.pusakalangit.TitahMonuments.ultimateMonument(owner, dampak.monument(), dampak.monumentScale(),
						dampakCastAim != null ? dampakCastAim : at);
			}
		}
		if (twoDaoRgb >= 0 && aux == 0 && (flags & Payloads.TechFx.FLAG_VOLLEY) == 0 && phase != Phase.PHANTOM) {
			flags |= Payloads.TechFx.FLAG_TWO_DAO;
			aux = twoDaoRgb;
		}
		if (spec.stages() != null && stage > 1 && aux == 0 && (flags & (Payloads.TechFx.FLAG_TWO_DAO | Payloads.TechFx.FLAG_VOLLEY)) == 0) {
			flags |= Payloads.TechFx.FLAG_STAGE;
			aux = stage - 1;
		}
		dev.kultivator.voice.VoiceHooks.techCast(caster, spec, phase, parent == null && !echo && !options.showcase());
		byte tier = (byte) ((Math.max(0, Math.min(7, visualTier)) & 7) | (Math.max(0, Math.min(7, parent == null ? mastery : 0)) << 3));
		Payloads.TechFx payload = new Payloads.TechFx(spec.netId(), (byte) phase.ordinal(), at.x, at.y, at.z, to.x, to.y, to.z, clean(radius),
				clean(length), tier, (short) flags, (short) Math.max(0, Math.min(Short.MAX_VALUE, count)), (int) (seed ^ (seed >>> 32)), caster.getId(),
				(short) Math.max(0, Math.min(Short.MAX_VALUE, duration)), target, (short) Math.max(-1, Math.min(Short.MAX_VALUE, index)),
				(short) Math.max(1, Math.min(Short.MAX_VALUE, of)), aux);
		double range = spec.pamungkas() || spec.grade().tier() >= TechniqueGrade.TIAN.tier() ? TechniquePower.FX_RANGE_BIG : TechniquePower.FX_RANGE;
		Fx.sendTech(level, payload, range);
		anyFxSent = true;
		noteFx(now);
	}

	/** HIT events over the per-second cap fold into one MASS_HIT next tick ({@code count} = folded hits). */
	private void fold(Vec3 at, long now) {
		if (foldTick != now) {
			foldTick = now;
			foldCount = 0;
			foldAt = at;
			schedule(1, () -> {
				if (foldCount > 0 && foldAt != null) {
					send(Phase.MASS_HIT, foldAt, foldAt, 1.0F, 0.0F, foldCount, 0, -1, -1, Math.max(1, foldCount), 0, 0, level.getGameTime());
				}
				foldCount = 0;
			});
		}
		foldCount++;
	}

	private static boolean finite(Vec3 v) {
		return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
	}

	/** A float for the wire: finite and not negative. */
	static float clean(float value) {
		return Float.isFinite(value) ? Math.max(0.0F, value) : 0.0F;
	}

	/** {@code lastAreaRadius} for the wire (it is NaN until a NOVA or ZONE sets it). */
	float areaRadiusForFx() {
		return Double.isNaN(lastAreaRadius) ? 0.0F : clean((float) lastAreaRadius);
	}

	/**
	 * The duration a CAST event carries when its caller gave none: the channel's ticks, else the longest SELF boon of the
	 * timeline's first steps (a guard, a counter window, a form, a buff, cheat death...), so the client holds the caster's
	 * pose and lasting layers as long as the effect really lasts (155).
	 */
	int castDuration() {
		if (spec.castTicks() > 0) {
			return spec.castTicks();
		}
		if (spec.channel() != null) {
			return spec.channel().ticks();
		}
		int longest = 0;
		for (Step step : spec.timeline()) {
			if (step.timing() != Step.Timing.AT || step.at() > 0) {
				continue;
			}
			for (Payload.Boon boon : step.self()) {
				longest = Math.max(longest, boonTicks(boon));
			}
		}
		return Math.min(20 * 60, longest);
	}

	private int boonTicks(Payload.Boon boon) {
		return switch (boon) {
			case Payload.Dr dr -> dr.ticks().set() ? duration(dr.ticks(), 0.0D) : 0;
			case Payload.Buff buff -> buff.ticks().set() ? duration(buff.ticks(), 0.0D) : 0;
			case Payload.Shield shield -> shield.ticks().set() ? duration(shield.ticks(), 0.0D) : 0;
			case Payload.Dodge dodge -> dodge.ticks().set() ? duration(dodge.ticks(), 0.0D) : 0;
			case Payload.Transform transform -> duration(transform.ticks(), 0.0D);
			case Payload.Summon summon -> duration(summon.ticks(), 0.0D);
			case Payload.Counter counter -> duration(counter.window(), 0.0D);
			case Payload.CheatDeath cheat -> duration(cheat.ticks(), 0.0D);
			case Payload.ProjectileGuard guard -> guard.ticks().set() ? duration(guard.ticks(), 0.0D) : 0;
			case Payload.GrantHook grant -> grant.hook().ticks().set() ? duration(grant.hook().ticks(), 0.0D) : 0;
			default -> 0;
		};
	}

	/**
	 * The vanilla hand swing of a cast pose (DESIGN 27.2.1): at CAST (a charged cast's CAST comes at the release) and on any
	 * phase whose authored pose swings (a slam on EXTRA0). Sent to the trackers and the caster, so everyone sees the arm.
	 * mcsrc: {@code LivingEntity.swing(InteractionHand, SwingAnimation, boolean)} (world/entity/LivingEntity.java:2022).
	 */
	private void swingFor(Phase phase) {
		if (echo || phase == Phase.HIT || phase == Phase.MASS_HIT || phase == Phase.PULSE || phase == Phase.ABORT || phase == Phase.END) {
			return;
		}
		dev.kultivator.technique.spec.dsl.Pose pose = phase == Phase.CAST ? spec.resolvedPose(Phase.CAST) : spec.pose(phase);
		if (pose.swing() == dev.kultivator.technique.spec.dsl.Pose.Swing.NONE || pose.swingTicks() <= 0) {
			return;
		}
		long now = level.getGameTime();
		if (now - lastSwing < 4L) {
			return;
		}
		lastSwing = now;
		net.minecraft.world.item.SwingAnimationType type = pose.swing() == dev.kultivator.technique.spec.dsl.Pose.Swing.STAB
				? net.minecraft.world.item.SwingAnimationType.STAB : net.minecraft.world.item.SwingAnimationType.WHACK;
		caster.swing(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.component.SwingAnimation(type, pose.swingTicks()), true);
	}

	/**
	 * Tells every client to drop what it still draws for this cast (ABORT): a cancelled, replaced or interrupted cast, a
	 * toggle cast off, the caster's death or logout. Once per cast, never capped, only after something was drawn.
	 */
	void abortFx() {
		if (abortSent || !anyFxSent && !charging) {
			return;
		}
		abortSent = true;
		Vec3 at = caster.position();
		if (!finite(at)) {
			return;
		}
		send(Phase.ABORT, at, at, 0.0F, 0.0F, 0, 0, -1, 0, 1, 0, 0, level.getGameTime());
	}

	/** TechFx events sent server-wide: [window start tick, count this second, count last second] (for {@code debug teknik beban}). */
	private static final long[] FX_STATS = new long[3];

	private static void noteFx(long now) {
		if (now - FX_STATS[0] >= 20L) {
			FX_STATS[2] = now - FX_STATS[0] < 40L ? FX_STATS[1] : 0L;
			FX_STATS[0] = now;
			FX_STATS[1] = 0L;
		}
		FX_STATS[1]++;
	}

	/** TechFx events sent in the last full second. */
	static long fxLastSecond() {
		return Math.max(FX_STATS[1], FX_STATS[2]);
	}

	boolean hitBossThisPass;
	boolean allyPass;
	boolean castFxSent;
	/** The CAST and PULSE events of a caster-following area carry FLAG_FOLLOW. */
	boolean followArea;
	private boolean climaxSent;
	private boolean anyFxSent;
	private boolean abortSent;
	private long lastSwing = Long.MIN_VALUE / 2;
	private long hitWindow = Long.MIN_VALUE / 2;
	private int hitCount;
	private long foldTick = -1L;
	private int foldCount;
	private @Nullable Vec3 foldAt;

	/** A lasting area that rides the caster (the legacy Dao Domain): its CAST and PULSE events carry FLAG_FOLLOW. */
	public void followCaster() {
		followArea = true;
	}

	/** The second Dao's RGB of a two-Dao art (-1 = none). */
	private int twoDaoRgb = -1;

	@Override
	public void twoDao(int rgb) {
		twoDaoRgb = rgb < 0 ? -1 : rgb & 0xFFFFFF;
	}

	/** The CAST event, once per cast (step 0's geometry when it has one). */
	public void castFx(Vec3 at, Vec3 to, float radius, float length, int count, int duration) {
		if (!castFxSent) {
			castFxSent = true;
			fx(Phase.CAST, at, to, radius, length, count, duration);
		}
	}

	/** The CAST event with a target entity (TARGET, CHAIN: the hand to the victim). */
	public void castFx(Vec3 at, Vec3 to, float radius, float length, int count, int duration, int target) {
		if (!castFxSent) {
			castFxSent = true;
			fx(Phase.CAST, at, to, radius, length, count, duration, target, 0, 1, 0, 0);
		}
	}

	/** The caster's main hand, roughly (eye − 0.35 up + 0.3 right + look × 0.6): where palms, fingers and swords leave. */
	public Vec3 hand() {
		Vec3 eyePos = caster.getEyePosition();
		Vec3 flat = look.horizontal();
		Vec3 right = flat.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(-flat.z, 0.0D, flat.x).normalize();
		return eyePos.add(0.0D, -0.35D, 0.0D).add(right.scale(0.3D)).add(look.scale(0.6D));
	}
	private final Set<Phase> soundedThisTick = new java.util.HashSet<>();
	private long soundTick = -1L;

	/** Marks {@code phase} as sounded this tick without playing it (the silent climax of DESIGN 28.4.3). */
	private void markSounded(Phase phase) {
		long now = level.getGameTime();
		if (now != soundTick) {
			soundTick = now;
			soundedThisTick.clear();
		}
		soundedThisTick.add(phase);
	}

	@Override
	public void sound(Phase phase, Vec3 at) {
		List<SoundCue> cues = spec.sounds(phase);
		if (cues.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		if (now != soundTick) {
			soundTick = now;
			soundedThisTick.clear();
		}
		if (!soundedThisTick.add(phase)) {
			return;
		}
		int played = 0;
		for (SoundCue cue : cues) {
			if (played++ >= TechniquePower.SOUNDS_PER_PHASE) {
				break;
			}
			if (cue.delay() > 0) {
				schedule(cue.delay(), () -> level.playSound(null, at.x, at.y, at.z, cue.sound(), SoundSource.PLAYERS, cue.volume(), cue.pitch()));
			} else {
				level.playSound(null, at.x, at.y, at.z, cue.sound(), SoundSource.PLAYERS, cue.volume(), cue.pitch());
			}
		}
	}

	// ================================================================== scheduling and lifecycle

	@Override
	public void schedule(int delayTicks, Runnable action) {
		if (ended) {
			return;
		}
		if (delayTicks <= 0) {
			action.run();
			return;
		}
		scheduled.add(new Scheduled(level.getGameTime() + delayTicks, action));
	}

	/** Travelling things, areas, phantoms and forms keep the cast alive. */
	void activity(int delta) {
		activity = Math.max(0, activity + delta);
	}

	int activityCount() {
		return activity;
	}

	void phantomGone() {
		activity(-1);
	}

	void formEnded(EndReason reason) {
		if (formActive) {
			formActive = false;
			activity(-1);
		}
	}

	@Override
	public void run(Step step) {
		Steps.schedule(this, step, timelineAge(), null, 1.0D, DamageContext.Kind.HIT, false);
	}

	/** A hook's or cheat-death's step, anchored at {@code anchor}: a by-product (no Kehancuran, no mastery). */
	void runHook(Step step, @Nullable LivingEntity anchor) {
		Steps.execute(this, new Run(step, 0, anchor, null, 1.0D, DamageContext.Kind.HIT, true));
	}

	/** A COUNTER release step with the stored damage available as {@code DMG perStored}. */
	void runRelease(Step step, double stored) {
		Run run = new Run(step, 0, null, null, 1.0D, DamageContext.Kind.COUNTER, true);
		run.storedDamage = stored;
		Steps.execute(this, run);
	}

	/** Every tick while active: scheduled actions, flows and the end check. False once the cast is finished. */
	boolean tick() {
		if (ended) {
			return false;
		}
		if (!casterValid()) {
			end(EndReason.LOGOUT);
			return false;
		}
		long now = level.getGameTime();
		if (!scheduled.isEmpty()) {
			List<Scheduled> due = null;
			for (int i = scheduled.size() - 1; i >= 0; i--) {
				Scheduled each = scheduled.get(i);
				if (each.due() <= now) {
					scheduled.remove(i);
					if (due == null) {
						due = new ArrayList<>();
					}
					due.add(0, each);
				}
			}
			if (due != null) {
				for (Scheduled each : due) {
					if (ended) {
						return false;
					}
					try {
						each.action().run();
					} catch (RuntimeException exception) {
						dev.kultivator.Kultivator.LOGGER.error("Kultivator: technique {} step failed", spec.id(), exception);
					}
				}
			}
		}
		Flows.tick(this, now);
		SpecialTechnique handler = spec.special() == null ? null : dev.kultivator.data.TechniqueRegistry.special(spec.special());
		if (handler != null && !ended) {
			try {
				handler.tick(this);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: special {} tick failed", spec.special(), exception);
			}
		}
		if (!ended && idle()) {
			if (!endStepsRun) {
				endStepsRun = true;
				Steps.runEndSteps(this);
				if (!idle()) {
					return true;
				}
			}
			end(EndReason.DONE);
			return false;
		}
		return !ended;
	}

	/** Nothing left: no scheduled actions, no activity, no flow holding the cast. */
	boolean idle() {
		return scheduled.isEmpty() && activity <= 0 && !Flows.holding(this);
	}

	@Override
	public void end(EndReason reason) {
		if (ended) {
			return;
		}
		ended = true;
		endReason = reason;
		aborted = reason != EndReason.DONE;
		scheduled.clear();
		if (aborted) {
			// every client drops this cast's runs, its pose and its cinematic (S8)
			abortFx();
		}
		Fields.endFrom(this, reason);
		Phantoms.endFrom(this);
		VirtualProjectiles.endFrom(this);
		Forms.endFrom(this, reason);
		Boons.endCast(this);
		SpecialTechnique handler = spec.special() == null ? null : dev.kultivator.data.TechniqueRegistry.special(spec.special());
		if (handler != null) {
			try {
				handler.end(this, reason);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: special {} end failed", spec.special(), exception);
			}
		}
		if (casterValid()) {
			if (reason == EndReason.DONE) {
				destruction.release();
				fx(Phase.END, caster.position(), end, areaRadiusForFx(), 0.0F, kills, 0);
			}
			for (Payload.SelfPenalty penalty : pendingPenalties) {
				Boons.penalty(caster, penalty);
			}
		}
		Flows.ended(this, reason);
		finished(reason);
	}

	@Override
	public boolean ended() {
		return ended;
	}

	boolean aborted() {
		return aborted;
	}

	@Override
	public boolean destroy(Vec3 anchor, Vec3 direction) {
		return destruction.manual(anchor, direction);
	}

	@Override
	public List<String> recentCasts(int seconds) {
		return TechniqueRunner.recentCasts(caster, seconds);
	}

	@Override
	public boolean replay(TechniqueSpec other, double replayFactor) {
		if (other == null || other.special() != null || other.toggle() != null || other.legacy() == dev.kultivator.data.Technique.FLYING_SWORD
				|| other.legacy() == dev.kultivator.data.Technique.CLOUD_STEP || caster instanceof DiscipleEntity && other.isLegacy()) {
			// never a special or a stance: a replayed toggle could not be cast off again
			return false;
		}
		Caster copy = child(other, replayFactor, false);
		copy.kehancuran = false;
		TechniqueRunner.startChild(copy);
		return true;
	}

	// ================================================================== hooks for Steps / Fields / projectiles

	void runEnded(Run run) {
		Steps.runEnded(this, run);
	}

	void projectileHit(Run run, LivingEntity victim, Vec3 at) {
		Steps.projectileHit(this, run, victim, at);
	}

	/** A projectile landed without a hit: nothing for Kehancuran (only hits are SCATTER points, 22.11.4); a LAND event. */
	void projectileLanded(Run run, Vec3 at) {
		fx(Phase.LAND, at, caster.getEyePosition(), 1.0F, 0.0F, 1, 0);
	}

	void areaEnded(Fields.Area area, EndReason reason) {
		Steps.areaEnded(this, area, reason);
	}

	void walls(Fields.Area area) {
		Steps.walls(this, area);
	}

	void pulse(Fields.Area area, double thin) {
		Steps.pulse(this, area, thin);
	}

	void phantomAttack(Phantoms.Phantom phantom) {
		Steps.phantomAttack(this, phantom);
	}

	// ================================================================== geometry helpers

	/** Rotates a direction around the vertical axis by {@code degrees}. */
	public static Vec3 rotateYaw(Vec3 dir, double degrees) {
		if (degrees == 0.0D) {
			return dir;
		}
		return dir.yRot((float) Math.toRadians(-degrees));
	}

	/** Read-only: the first block on the loaded segment, or its end. */
	Vec3 clip(Vec3 from, Vec3 to) {
		BlockHitResult hit = LoadedPath.ray(level, from, to, caster);
		return hit.getType() == HitResult.Type.MISS ? hit.getLocation() : hit.getLocation();
	}

	/** Read-only: nothing solid on the segment (and loaded). */
	boolean clear(Vec3 from, Vec3 to) {
		return LoadedPath.loaded(level, from, to) && LoadedPath.ray(level, from, to, caster).getType() == HitResult.Type.MISS;
	}

	// ================================================================== helpers for the legacy code (22.10)

	/** EXTRA_COUNT of level 3 (+n swords, crescents, bolts, palms...). */
	public int bonusCount() {
		return bonusCount;
	}

	/** The factor of the extra copies of EXTRA_COUNT (side palms ×0.6). */
	public double bonusCountFactor() {
		return bonusCountFactor;
	}

	/** CHAIN of level 3 (+n hops). */
	public int bonusChain() {
		return bonusChain;
	}

	/** EXTRA_WAVE of level 3 (+n aftershocks). */
	public int bonusWaves() {
		return bonusWaves;
	}

	/** A damaging impact for the Kehancuran shapes (the first impact submits the default shapes; points feed SCATTER and ×n). */
	public void impact(Vec3 at, Vec3 direction) {
		if (!echo && parent == null) {
			destruction.impact(at, direction, firstImpactDone);
			firstImpactDone = true;
		}
	}

	/** One more anchor point (a crescent's end, a hop, a bolt) for SCATTER, ×n and per-crescent shapes. */
	public void hop(Vec3 at, Vec3 direction) {
		if (!echo && parent == null) {
			destruction.impact(at, direction, true);
		}
	}

	/** Schedules the level-3/4 STEPS bonuses of a legacy spec from now (AT steps; END steps run when the cast ends). */
	public void runBonusSteps() {
		for (Step step : bonusSteps) {
			if (step.timing() == Step.Timing.AT) {
				Steps.schedule(this, step, 0, null, 1.0D, DamageContext.Kind.HIT, false);
			}
		}
	}

	/** The radius WAVE r1Previous reads (the last NOVA or ZONE). */
	public void setLastAreaRadius(double radius) {
		lastAreaRadius = radius;
	}

	/** Keeps the cast alive for {@code ticks} (its END steps and release shapes run after that). */
	public void keepAlive(int ticks) {
		activity(1);
		schedule(Math.max(1, ticks), () -> activity(-1));
	}

	/** Applies one hostile payload with a shape's geometry (KNK centre and closeness, edge falloff). */
	public void applyHit(LivingEntity target, Payload.Hit payload, boolean precise, Vec3 centre, double radius) {
		HitInfo info = HitInfo.of(precise);
		info.centre = centre;
		info.radius = radius;
		applyHit(target, payload, info);
	}

	/** The amount the last landed hit dealt. */
	public float lastDealt() {
		return lastDealt;
	}

	/** True when this run is a copy (echo, extra cast, replay): no Kehancuran, no mastery. */
	public boolean copy() {
		return parent != null;
	}

	/** Starts a copy of this cast at {@code childFactor} after {@code delay} ticks (legacy ECHO and EXTRA_CAST). */
	public void copyAfter(int delay, double childFactor, boolean isEcho) {
		Caster copy = child(spec, childFactor, isEcho);
		keepAlive(delay + 1);
		schedule(Math.max(1, delay), () -> TechniqueRunner.startChild(copy));
	}

	/** Type helper for the disciple AI roles. */
	TechniqueType type() {
		return spec.type();
	}

	@Nullable Dao dao() {
		return spec.dao();
	}
}
