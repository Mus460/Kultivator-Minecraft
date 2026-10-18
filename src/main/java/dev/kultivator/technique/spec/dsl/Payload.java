package dev.kultivator.technique.spec.dsl;

import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueGrade;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What a step does (DESIGN 22.3.2 and 22.3.3). {@link Hit} payloads go to hostiles the shape reached (only through
 * {@code Targeting} / {@code DiscipleTechniques.mayHit} and the damage choke points); {@link Boon} payloads go to the
 * caster ({@code SELF}) or allies ({@code ALLY}). Coefficients are × P. Pure data; the runner applies them with the
 * safety rules of 22.3.5 (never-moved list, PortalIndex, transient modifiers only, entity fire only).
 */
public sealed interface Payload permits Payload.Hit, Payload.Boon {

	/** A payload on hostiles. */
	sealed interface Hit extends Payload permits Dmg, DmgPct, Dot, Ignite, Freeze, Bind, Move, Effect, Marked, Attr, Exec,
			Drain, Untarget, Taunt, Strip, Link, ChanceHit, Special {
	}

	/** A payload on the caster or allies. */
	sealed interface Boon extends Payload permits Dash, Blink, Heal, Cleanse, Shield, Buff, Dr, Dodge, Transform, Summon,
			RestoreQi, CdReset, GrantHook, Counter, CheatDeath, ProjectileGuard, Revert, SelfPenalty, ForbiddenCost, Comprehension,
			Extinguish, Special {
	}

	// ================================================================== enums

	/** Versus classes of {@code DMG ... vs {...}} (22.3.2). */
	enum Versus { UNDEAD, ARTHROPOD, BOSS, NETHER, END, AQUATIC, FIRE_IMMUNE, WET, FROZEN }

	/** The element of a DOT; FIRE also ignites (powder-snow guard). */
	enum DotKind { FIRE, POISON, BLEED, SOUL, QI, COLD, LIGHT }

	/** BIND kinds: ROOT, STUN (attack ×0), SUSPEND (lifted 1.5 blocks), SLEEP (a stun that breaks on damage). */
	enum BindKind { ROOT, STUN, SUSPEND, SLEEP }

	/** KNK / PULL / LCH / SLAM. */
	enum MoveKind { KNOCK, PULL, LAUNCH, SLAM }

	/** What a movement pushes away from or pulls toward. */
	enum Center { CASTER, ANCHOR, AREA }

	/** The mob effects a technique may put on hostiles (22.3.2 whitelist); nothing else compiles. */
	enum Eff {
		SLOWNESS(MobEffects.SLOWNESS), WEAKNESS(MobEffects.WEAKNESS), BLINDNESS(MobEffects.BLINDNESS), DARKNESS(MobEffects.DARKNESS),
		WITHER(MobEffects.WITHER), POISON(MobEffects.POISON), GLOWING(MobEffects.GLOWING), MINING_FATIGUE(MobEffects.MINING_FATIGUE),
		LEVITATION(MobEffects.LEVITATION);

		private final Holder<MobEffect> effect;

		Eff(Holder<MobEffect> effect) {
			this.effect = effect;
		}

		public Holder<MobEffect> holder() {
			return effect;
		}
	}

	/** The attributes ATTR may change on hostiles (transient modifiers, never saved). */
	enum AttrKind {
		ARMOR(Attributes.ARMOR), ATTACK_DAMAGE(Attributes.ATTACK_DAMAGE), MOVEMENT_SPEED(Attributes.MOVEMENT_SPEED),
		FLYING_SPEED(Attributes.FLYING_SPEED), JUMP_STRENGTH(Attributes.JUMP_STRENGTH),
		FALL_DAMAGE_MULTIPLIER(Attributes.FALL_DAMAGE_MULTIPLIER), KNOCKBACK_RESISTANCE(Attributes.KNOCKBACK_RESISTANCE);

		private final Holder<Attribute> attribute;

		AttrKind(Holder<Attribute> attribute) {
			this.attribute = attribute;
		}

		public Holder<Attribute> holder() {
			return attribute;
		}
	}

	/**
	 * BUFF kinds on the caster or allies (22.3.3). Vanilla effects take {@code value} = level (I = 1; resistance at most
	 * III); REACH, ARMOR, ATTACK_DAMAGE, KNOCKBACK_RESISTANCE are flat amounts; ATTACK_SPEED, EMPOWER, REFLECT, LIFESTEAL
	 * are shares (0.3 = +30%); QI_REGEN and CD_RATE are factors; COST_MOD takes {@code value} = qi factor and
	 * {@code value2} = cooldown factor.
	 */
	enum BuffKind {
		STRENGTH(MobEffects.STRENGTH), RESISTANCE(MobEffects.RESISTANCE), SPEED(MobEffects.SPEED), JUMP(MobEffects.JUMP_BOOST),
		REGENERATION(MobEffects.REGENERATION), FIRE_RESISTANCE(MobEffects.FIRE_RESISTANCE), WATER_BREATHING(MobEffects.WATER_BREATHING),
		NIGHT_VISION(MobEffects.NIGHT_VISION), INVISIBILITY(MobEffects.INVISIBILITY), ABSORPTION(MobEffects.ABSORPTION),
		HEALTH_BOOST(MobEffects.HEALTH_BOOST), SLOW_FALLING(MobEffects.SLOW_FALLING),
		REACH(null), ARMOR(null), ATTACK_DAMAGE(null), ATTACK_SPEED(null), KNOCKBACK_RESISTANCE(null),
		EMPOWER(null), REFLECT(null), LIFESTEAL(null), QI_REGEN(null), CD_RATE(null), COST_MOD(null),
		/** Immune to slowness and freezing. */
		IMMUNE_SLOW(null),
		/** Extra air jumps ({@code value}). */
		AIR_JUMP(null),
		/** No fall damage. */
		NO_FALL(null),
		/** The caster's own Kehancuran shapes get +{@code value} radius while active (within the caps; Burning Heart). */
		KH_RADIUS(null);

		private final @Nullable Holder<MobEffect> effect;

		BuffKind(@Nullable Holder<MobEffect> effect) {
			this.effect = effect;
		}

		/** The vanilla effect, or null for the mod's own buffs. */
		public @Nullable Holder<MobEffect> effect() {
			return effect;
		}
	}

	enum DashDir { LOOK, MOVE, TARGET, UP, FOLLOW }

	enum BlinkMode { AIM, BEHIND_TARGET, TO_TARGET, BACK, ORIGIN }

	enum Facing { ALL, FRONT, BACK }

	enum SummonMode { FOLLOW, ANCHOR, CLING }

	enum Pattern { NONE, RING, PENTAGON, LINE }

	enum Store { DAMAGE_TAKEN, FRONT_MELEE, CANCEL_ALL }

	/** What {@link Dmg#per} counts. */
	enum Per { NONE, STACK, STORED, SUMMON }

	// ================================================================== hostile payloads

	/**
	 * {@code DMG coef [piercing] [vs {...}]} through {@code TechniquePower.finalDamage}. {@code edge}: linear falloff to
	 * this coefficient at the shape's edge (NaN = none); {@code per}: + this × the counted thing ({@code perKind});
	 * {@code byCharge}: × the charge share; {@code shatters}: an Ice "shatter" hit, ×1.5 on a FROZEN target and the freeze
	 * ends (22.3.2 Dao defaults).
	 */
	record Dmg(double coef, boolean piercing, Map<Versus, Double> versus, double edge, double per, Per perKind, boolean byCharge,
			boolean shatters) implements Hit {
		public Dmg pierce() { return new Dmg(coef, true, versus, edge, per, perKind, byCharge, shatters); }
		public Dmg vs(Versus kind, double factor) { return new Dmg(coef, piercing, Payload.with(versus, kind, factor), edge, per, perKind, byCharge, shatters); }
		/** Linear falloff from {@code coef} at the centre to {@code edgeCoef} at the edge. */
		public Dmg edge(double edgeCoef) { return new Dmg(coef, piercing, versus, edgeCoef, per, perKind, byCharge, shatters); }
		public Dmg perStack(double extra) { return new Dmg(coef, piercing, versus, edge, extra, Per.STACK, byCharge, shatters); }
		public Dmg perStored(double extra) { return new Dmg(coef, piercing, versus, edge, extra, Per.STORED, byCharge, shatters); }
		public Dmg perSummon(double extra) { return new Dmg(coef, piercing, versus, edge, extra, Per.SUMMON, byCharge, shatters); }
		public Dmg charged() { return new Dmg(coef, piercing, versus, edge, per, perKind, true, shatters); }
		public Dmg shatter() { return new Dmg(coef, piercing, versus, edge, per, perKind, byCharge, true); }
	}

	/** {@code DMG% share (boss share)}: a share of max health; counts in boss caps; arena clamp 15% per cast. */
	record DmgPct(double share, double bossShare) implements Hit {
	}

	/** {@code DOT coef/s, s, element}: ticked every 20 with the snapshot P; the same spec refreshes; ≤ 4 per target. */
	record Dot(double perSecond, int seconds, DotKind element, boolean piercing, Map<Versus, Double> versus) implements Hit {
		public Dot pierce() { return new Dot(perSecond, seconds, element, true, versus); }
		public Dot vs(Versus kind, double factor) { return new Dot(perSecond, seconds, element, piercing, Payload.with(versus, kind, factor)); }
	}

	/** {@code IGN s}: entity fire only. */
	record Ignite(double seconds) implements Hit {
	}

	/** {@code FRZ t}: frozen ticks plus Slowness. */
	record Freeze(int ticks) implements Hit {
	}

	/** {@code BIND t kind}; {@code bossTicks} −1 = the default ×0.25 on bosses. */
	record Bind(BindKind kind, Val ticks, int bossTicks) implements Hit {
		public Bind boss(int ticks) { return new Bind(kind, this.ticks, ticks); }
	}

	/**
	 * KNK / PULL / LCH / SLAM through the existing knock and launch rules and the never-moved list. {@code continuous}: a
	 * continuous pull of {@code strength} b/t for {@code ticks} ticks ("@0–60 PULL .5"); {@code inBlocks}: a single move of
	 * {@code strength} blocks ("PULL 6 b toward the caster"); {@code byCloseness}: stronger the closer to the centre
	 * (strength base → cap); {@code spin}: a sideways spin.
	 */
	record Move(MoveKind kind, Val strength, Center center, boolean continuous, int ticks, boolean inBlocks, boolean byCloseness,
			boolean spin) implements Hit {
		public Move toward(Center value) { return new Move(kind, strength, value, continuous, ticks, inBlocks, byCloseness, spin); }
		/** A continuous pull of {@code strength} b/t for {@code duration} ticks. */
		public Move perTick(int duration) { return new Move(kind, strength, center, true, Math.max(1, duration), inBlocks, byCloseness, spin); }
		public Move blocks() { return new Move(kind, strength, center, continuous, ticks, true, byCloseness, spin); }
		public Move byCloseness(double near) { return new Move(kind, Val.of(strength.base(), near), center, continuous, ticks, inBlocks, true, spin); }
		public Move sideways() { return new Move(kind, strength, center, continuous, ticks, inBlocks, byCloseness, true); }
	}

	/** {@code EFF effect t amp}: {@code level} 1 = I; {@code bossTicks} −1 = same as others. */
	record Effect(Eff effect, int level, Val ticks, int bossTicks) implements Hit {
		public Effect boss(int ticks) { return new Effect(effect, level, this.ticks, ticks); }
	}

	/** {@code MARK kind} for {@code ticks}. */
	record Marked(Mark mark, int ticks) implements Hit {
	}

	/**
	 * {@code ATTR attribute amount t}: a transient modifier. {@code percent}: multiplies the total (−0.5 = −50%, +1 = ×2);
	 * otherwise a flat amount. {@code bossFactor}: bosses get this share of the change.
	 */
	record Attr(AttrKind attribute, double amount, int ticks, boolean percent, double bossFactor) implements Hit {
		public Attr bossFactor(double factor) { return new Attr(attribute, amount, ticks, percent, factor); }
	}

	/**
	 * {@code EXEC threshold (boss 0.05)}: lethal damage below {@code threshold} of max health (bosses only under
	 * {@code bossThreshold}, never above 0.05); or {@code EXEC maxHealthBelow k×P} ({@code belowPower} > 0, non-bosses).
	 */
	record Exec(double threshold, double bossThreshold, double belowPower) implements Hit {
	}

	/** {@code DRAIN share [ally share, radius] [qi share]}; {@code absorbCap} > 0: overheal becomes absorption up to that share of max health. */
	record Drain(double share, double allyShare, double allyRadius, double qiShare, double absorbCap) implements Hit {
		public Drain allies(double allyShareValue, double radius) { return new Drain(share, allyShareValue, radius, qiShare, absorbCap); }
		public Drain qi(double qiShareValue) { return new Drain(share, allyShare, allyRadius, qiShareValue, absorbCap); }
		public Drain overhealAbsorb(double maxShare) { return new Drain(share, allyShare, allyRadius, qiShare, maxShare); }
	}

	/** {@code UNTARGET}: target and brain attack memory cleared. */
	record Untarget() implements Hit {
	}

	/** {@code TAUNT t}: the target attacks the caster. */
	record Taunt(int ticks) implements Hit {
	}

	/** {@code STRIP_BUFFS} / {@code STRIP_EFFECT id}: an empty list = the default buffs (22.9.2 Golden Emperor list). */
	record Strip(List<Holder<MobEffect>> effects) implements Hit {
		public static final List<Holder<MobEffect>> DEFAULT_BUFFS = List.of(MobEffects.RESISTANCE, MobEffects.ABSORPTION,
				MobEffects.STRENGTH, MobEffects.REGENERATION, MobEffects.FIRE_RESISTANCE, MobEffects.SPEED);
	}

	/** {@code LINK share t}: damage to one linked monster is also dealt to the others at the share (boss share separate). */
	record Link(double share, int ticks, double bossShare) implements Hit {
	}

	/** A hostile payload that lands with probability {@code chance} per evaluation. */
	record ChanceHit(double chance, Hit payload) implements Hit {
	}

	/** A per-target or per-cast handler of a {@code SpecialTechnique} ({@code special(id)}). */
	record Special(String id) implements Hit, Boon {
	}

	// ================================================================== caster and ally payloads

	/** {@code DASH dist}: along {@code dir} through open, loaded space; i-frames; no fall damage for {@code noFall} ticks. */
	record Dash(Val distance, DashDir dir, int iframes, int ticks, int noFall) implements Boon {
		public Dash dir(DashDir value) { return new Dash(distance, value, iframes, ticks, noFall); }
		public Dash iframes(int value) { return new Dash(distance, dir, value, ticks, noFall); }
		public Dash ticks(int value) { return new Dash(distance, dir, iframes, value, noFall); }
		public Dash noFall(int value) { return new Dash(distance, dir, iframes, ticks, value); }
	}

	/**
	 * {@code BLINK range mode}: a safe teleport; {@code chain} hops within {@code chainRange}, at least {@code gap} ticks
	 * apart, running {@code hop} at each hop (anchor HIT = that hop's target; "each STRIKE > DMG 3"); {@code noPathCheck}
	 * only for SPACE specs; {@code safeFrom}: BACK picks the loaded spot with no monster within that many blocks;
	 * {@code leaping}: drawn as a leap; {@code iframes}: invulnerable for that many ticks after each hop. BACK = backwards away
	 * from danger, ORIGIN = back to where the cast started.
	 */
	record Blink(BlinkMode mode, Val range, Val chain, double chainRange, int gap, boolean noPathCheck, double safeFrom, boolean leaping,
			@Nullable Step hop, int iframes) implements Boon {
		public Blink chain(double base, double cap, double withinRange) { return new Blink(mode, range, Val.of(base, cap), withinRange, gap, noPathCheck, safeFrom, leaping, hop, iframes); }
		public Blink gap(int ticks) { return new Blink(mode, range, chain, chainRange, Math.max(3, ticks), noPathCheck, safeFrom, leaping, hop, iframes); }
		public Blink skipPath() { return new Blink(mode, range, chain, chainRange, gap, true, safeFrom, leaping, hop, iframes); }
		public Blink safeFrom(double blocks) { return new Blink(mode, range, chain, chainRange, gap, noPathCheck, blocks, leaping, hop, iframes); }
		public Blink leap() { return new Blink(mode, range, chain, chainRange, gap, noPathCheck, safeFrom, true, hop, iframes); }
		/** Runs {@code step} at every hop (and after a single blink). */
		public Blink eachHop(Step step) { return new Blink(mode, range, chain, chainRange, gap, noPathCheck, safeFrom, leaping, step, iframes); }
		/** Invulnerable for {@code ticks} after every hop of the chain ("i-frames during the chain"; a hop renews it). */
		public Blink iframes(int ticks) { return new Blink(mode, range, chain, chainRange, gap, noPathCheck, safeFrom, leaping, hop, Math.max(0, ticks)); }
	}

	/** {@code HEAL share [over t]} (b→c grows with mastery); ally radius from the step; {@code includeAll}: villagers and golems too (Guanyin). */
	record Heal(Val share, int over, boolean includeAll) implements Boon {
		public Heal over(int ticks) { return new Heal(share, ticks, includeAll); }
		public Heal everyone() { return new Heal(share, over, true); }
	}

	/** {@code CLEANSE}: an empty list = the default list of 22.3.3. */
	record Cleanse(List<Holder<MobEffect>> effects) implements Boon {
		public static final List<Holder<MobEffect>> DEFAULT = List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.SLOWNESS,
				MobEffects.WEAKNESS, MobEffects.BLINDNESS, MobEffects.DARKNESS, MobEffects.NAUSEA, MobEffects.MINING_FATIGUE);
	}

	/** {@code SHLD share t}: Perisai Qi (pool ≤ 2 × max health; never absorbs unblockable damage). */
	record Shield(double share, Val ticks) implements Boon {
	}

	/**
	 * {@code BUFF kind value t}; {@code dao}: EMPOWER / CD_RATE / COST_MOD only for that Dao; {@code onlyInArea}: only
	 * inside the caster's own field; {@code hpScaled}: from {@code value2} at full health to {@code value} near death;
	 * {@code stackMax} > 1: recasts stack up to that level.
	 */
	record Buff(BuffKind kind, double value, double value2, Val ticks, @Nullable Dao dao, boolean onlyInArea, boolean hpScaled, int stackMax) implements Boon {
		public Buff dao(Dao value) { return new Buff(kind, this.value, value2, ticks, value, onlyInArea, hpScaled, stackMax); }
		public Buff insideArea() { return new Buff(kind, value, value2, ticks, dao, true, hpScaled, stackMax); }
		public Buff hpScaled(double atFullHealth) { return new Buff(kind, value, atFullHealth, ticks, dao, onlyInArea, true, stackMax); }
		public Buff stack(int max) { return new Buff(kind, value, value2, ticks, dao, onlyInArea, hpScaled, max); }
	}

	/**
	 * {@code DR share [FRONT/BACK/ALL] [HP-scaled]} (share b→c grows with mastery): combined under the caps of 22.14.3;
	 * {@code hpScaled}: from {@code minShare} at full health to {@code share} near death.
	 */
	record Dr(Val share, Facing facing, Val ticks, boolean hpScaled, double minShare) implements Boon {
		public Dr hpScaled(double atFullHealth) { return new Dr(share, facing, ticks, true, atFullHealth); }
	}

	/** {@code DODGE chance} (≤ 50%; never against unblockable or heart-demon damage). */
	record Dodge(double chance, Val ticks) implements Boon {
	}

	/**
	 * {@code TRANSFORM form}: one form at a time; {@code scale} ≤ 4 with the head-room check (IN_WALL check on revert);
	 * {@code effects}: the form's own buffs (transient {@code kultivator:form_*} modifiers).
	 */
	record Transform(String form, Val ticks, Val scale, List<Boon> effects) implements Boon {
		public Transform scale(double value) { return new Transform(form, ticks, Val.of(value), effects); }
		public Transform scale(Val value) { return new Transform(form, ticks, value, effects); }
		public Transform with(Boon... more) { return new Transform(form, ticks, scale, Payload.concat(effects, more)); }
	}

	/**
	 * {@code SUMMON kind n t}: virtual phantoms, never entities (≤ 8 per cast, 12 per caster, 48 per server); each attack
	 * (at least every 10 ticks, one query) runs {@code attack}; {@code half}: disciples summon half (rounded up).
	 */
	record Summon(String kind, Val count, Val ticks, SummonMode mode, int interval, Val range, @Nullable Step attack, Pattern pattern) implements Boon {
		/** Kind of clone summons: never taught to disciples (they have Klon Ilahi, 22.13.1). */
		public static final String CLONE = "clone";

		public Summon ticks(double base, double cap) { return new Summon(kind, count, Val.of(base, cap), mode, interval, range, attack, pattern); }
		public Summon mode(SummonMode value) { return new Summon(kind, count, ticks, value, interval, range, attack, pattern); }
		public Summon interval(int value) { return new Summon(kind, count, ticks, mode, Math.max(10, value), range, attack, pattern); }
		public Summon range(double value) { return new Summon(kind, count, ticks, mode, interval, Val.of(value), attack, pattern); }
		public Summon range(double base, double cap) { return new Summon(kind, count, ticks, mode, interval, Val.of(base, cap), attack, pattern); }
		public Summon attack(Step value) { return new Summon(kind, count, ticks, mode, interval, range, value, pattern); }
		public Summon pattern(Pattern value) { return new Summon(kind, count, ticks, mode, interval, range, attack, value); }
	}

	/** {@code RESTORE_QI share} (≤ 30% of max qi; {@code capPerCast} > 0 limits the total of one cast). */
	record RestoreQi(double share, double capPerCast) implements Boon {
	}

	/** {@code CD_RESET maxGrade}: never resets PAMUNGKAS. */
	record CdReset(TechniqueGrade maxGrade) implements Boon {
	}

	/** {@code GRANT_HOOK}: a timed hook on the caster. */
	record GrantHook(Hook hook) implements Boon {
	}

	/**
	 * {@code COUNTER window store cap → release}: absorbs {@code store} of what {@code kind} lets through (at most
	 * {@code cap} stored hits or × max health for CANCEL_ALL), releases early at {@code earlyAt} × max health stored,
	 * then runs {@code release}; the release counts against the cast's boss cap.
	 */
	record Counter(Val window, double store, double cap, Store kind, double earlyAt, @Nullable Step release) implements Boon {
		public Counter window(double base, double capTicks) { return new Counter(Val.of(base, capTicks), store, cap, kind, earlyAt, release); }
		public Counter earlyAt(double share) { return new Counter(window, store, cap, kind, share, release); }
		public Counter release(Step value) { return new Counter(window, store, cap, kind, earlyAt, value); }
	}

	/**
	 * {@code CHEAT_DEATH charges t} through the spine {@code DeathGuard} (stage TECHNIQUE, shared 10-minute lockout):
	 * a lethal hit leaves {@code healthShare} of max health (0 = a 1-HP floor) and runs {@code onTrigger}.
	 */
	record CheatDeath(Val charges, Val ticks, double healthShare, @Nullable Step onTrigger) implements Boon {
		public CheatDeath onTrigger(Step value) { return new CheatDeath(charges, ticks, healthShare, value); }
	}

	/**
	 * {@code PROJECTILE_GUARD}: hostile projectiles within {@code radius} (and {@code arc}° in front; 360 = all around)
	 * are discarded, never deflected; {@code returnShot} answers with a simulated TARGET hit of {@code returnCoef} on
	 * the shooter; {@code coversArea}: everything crossing the cast's field or domain.
	 */
	record ProjectileGuard(boolean returnShot, double arc, double radius, Val charges, Val ticks, double returnCoef, boolean coversArea) implements Boon {
		public ProjectileGuard returnShot(double coef) { return new ProjectileGuard(true, arc, radius, charges, ticks, coef, coversArea); }
		public ProjectileGuard wholeArea() { return new ProjectileGuard(returnShot, arc, radius, charges, ticks, returnCoef, true); }
		public ProjectileGuard charges(double base, double cap) { return new ProjectileGuard(returnShot, arc, radius, Val.of(base, cap), ticks, returnCoef, coversArea); }
		public ProjectileGuard ticks(double base, double cap) { return new ProjectileGuard(returnShot, arc, radius, charges, Val.of(base, cap), returnCoef, coversArea); }
	}

	/** Ends the caster's current form early ("ends the form"). */
	record Revert() implements Boon {
	}

	/** {@code SELF_PENALTY}: applied when the technique ends (Weakness level, qi regen ×0, technique damage ×factor). */
	record SelfPenalty(int ticks, int weakness, boolean qiRegenZero, double damageFactor) implements Boon {
	}

	/** {@code FORBIDDEN_COST hp share (current/max) [qi share]}: {@code setHealth(max(1, …))}, refused below 2 HP. */
	record ForbiddenCost(double hpShare, boolean ofMax, double qiShare) implements Boon {
	}

	/** {@code COMPREHENSION +x}: per second or once, at most {@code capPerCast}; counts towards the daily cap. */
	record Comprehension(double perSecond, double amount, double capPerCast) implements Boon {
	}

	/** Puts out the caster's fire. */
	record Extinguish() implements Boon {
	}

	// ================================================================== helpers

	private static Map<Versus, Double> with(Map<Versus, Double> base, Versus kind, double factor) {
		EnumMap<Versus, Double> copy = new EnumMap<>(Versus.class);
		copy.putAll(base);
		copy.put(kind, factor);
		return Map.copyOf(copy);
	}

	private static List<Boon> concat(List<Boon> base, Boon[] more) {
		java.util.ArrayList<Boon> list = new java.util.ArrayList<>(base);
		list.addAll(List.of(more));
		return List.copyOf(list);
	}
}
