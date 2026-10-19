package dev.kultivator.technique.spec.dsl;

import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueSpec;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The builder DSL the catalogue modules write (DESIGN 22.2.5), meant for {@code import static ...Dsl.*}. Every factory
 * maps one-to-one to a primitive of 22.3 or a template of 22.4; parameters a primitive does not have do not compile.
 * Coefficients are × P; ticks are ticks, seconds only where the notation says seconds (DOT, IGN, cooldown).
 *
 * <pre>{@code
 * r.add(art("bloodmoon_immortal_slaying_sword", 204, Dao.SWORD)
 *     .grade(TechniqueGrade.DI, 0).type(TechniqueType.AREA).realm(Realm.DEITY_TRANSFORMATION)
 *     .route(Route.feat(Feat.killsWith(Dao.SWORD, Cond.FULL_MOON_NIGHT, null, 30))).comprehension(50).flag(TEACHABLE, PAVILION)
 *     .qi(140).cooldown(40.0)
 *     .step(at(20).shape(rain(Anchor.AIM, 32).radius(8, 16).impacts(12, 36).over(40).impactRadius(2)).hit(damage(0.9)))
 *     .step(at(70).shape(zone(Anchor.AIM).radius(4)).hit(damage(3.0), mark(Mark.daoVuln(Dao.SWORD, 0.2), 200)))
 *     .fx(Phase.CAST, cloudDisc(0xB0102A).above(20), giantItem(Items.NETHERITE_SWORD).scale(8).fall(20, 20).minTier(3))
 *     .fx(Phase.IMPACT, swordShape(0xB0102A).falling(), shake(0.5F, 12))
 *     .sound(Phase.CAST, SoundEvents.BELL_BLOCK, 1.4F, 0.5F)
 *     .kehancuran(DShape.scatter(2), DShape.bowl(4))
 *     .perfection(Bonus.extraCount(6)).build());
 * }</pre>
 */
public final class Dsl {

	// ================================================================== spec and steps

	/** A new spec builder: id (saved, never renamed), net id (inside the module's block, 22.2.3) and Dao (null = universal). */
	public static TechniqueSpec.Builder art(String id, int netId, @Nullable Dao dao) {
		return TechniqueSpec.builder(id, netId, dao);
	}

	/** A step at tick {@code tick} (step 0 runs at cast time). */
	public static Step at(int tick) {
		return Step.at(tick);
	}

	/** A step when the timeline (or the effect it belongs to) ends: "at release", "onEnd", "(at the end)". */
	public static Step atEnd() {
		return Step.atEnd();
	}

	// ================================================================== shapes (22.3.1)

	public static Shape.Self self() {
		return Shape.Self.INSTANCE;
	}

	public static Shape.Strike strike() {
		return Shape.Strike.DEFAULT;
	}

	public static Shape.Cone cone() {
		return Shape.Cone.DEFAULT;
	}

	public static Shape.Beam beam() {
		return Shape.Beam.DEFAULT;
	}

	public static Shape.Front front() {
		return Shape.Front.DEFAULT;
	}

	public static Shape.Nova nova() {
		return Shape.Nova.DEFAULT;
	}

	public static Shape.Wave wave() {
		return Shape.Wave.DEFAULT;
	}

	public static Shape.Zone zone(Anchor anchor) {
		return Shape.Zone.DEFAULT.at(anchor);
	}

	public static Shape.Pillar pillar(Anchor anchor) {
		return Shape.Pillar.DEFAULT.at(anchor);
	}

	/** {@code RAIN} at {@code anchor}; {@code range} is the aim range when anchored at AIM. */
	public static Shape.Rain rain(Anchor anchor, double range) {
		return Shape.Rain.DEFAULT.at(anchor).range(range);
	}

	public static Shape.Chain chain() {
		return Shape.Chain.DEFAULT;
	}

	public static Shape.Target target() {
		return Shape.Target.DEFAULT;
	}

	public static Shape.All all() {
		return Shape.All.DEFAULT;
	}

	public static Shape.Recall recall() {
		return Shape.Recall.DEFAULT;
	}

	public static Shape.Volley volley() {
		return Shape.Volley.DEFAULT;
	}

	public static Shape.Domain domain() {
		return Shape.Domain.DEFAULT;
	}

	public static Shape.Field field(Anchor anchor) {
		return Shape.Field.DEFAULT.at(anchor);
	}

	public static Shape.Aura aura() {
		return Shape.Aura.DEFAULT;
	}

	public static Shape.Path path() {
		return Shape.Path.DEFAULT;
	}

	// ================================================================== hostile payloads (22.3.2)

	/** {@code DMG coef}. */
	public static Payload.Dmg damage(double coef) {
		return new Payload.Dmg(coef, false, Map.of(), Double.NaN, 0.0D, Payload.Per.NONE, false, false);
	}

	/** {@code DMG% share (boss share)}. */
	public static Payload.DmgPct damagePct(double share, double bossShare) {
		return new Payload.DmgPct(share, bossShare);
	}

	/** {@code DOT coef/s, seconds, element}. */
	public static Payload.Dot dot(double perSecond, int seconds, Payload.DotKind element) {
		return new Payload.Dot(perSecond, seconds, element, false, Map.of());
	}

	/** {@code IGN s}. */
	public static Payload.Ignite ignite(double seconds) {
		return new Payload.Ignite(seconds);
	}

	/** {@code FRZ t}. */
	public static Payload.Freeze freeze(int ticks) {
		return new Payload.Freeze(ticks);
	}

	/** {@code BIND t kind}. */
	public static Payload.Bind bind(Payload.BindKind kind, int ticks) {
		return new Payload.Bind(kind, Val.of(ticks), -1);
	}

	/** {@code BIND b→c t kind}. */
	public static Payload.Bind bind(Payload.BindKind kind, int baseTicks, int capTicks) {
		return new Payload.Bind(kind, Val.of(baseTicks, capTicks), -1);
	}

	/** {@code KNK s} (away from the caster). */
	public static Payload.Move knock(double strength) {
		return new Payload.Move(Payload.MoveKind.KNOCK, Val.of(strength), Payload.Center.CASTER, false, 0, false, false, false);
	}

	/** {@code KNK b→c} (grows with A). */
	public static Payload.Move knock(double base, double cap) {
		return new Payload.Move(Payload.MoveKind.KNOCK, Val.of(base, cap), Payload.Center.CASTER, false, 0, false, false, false);
	}

	/** {@code PULL s} (toward the caster unless {@code toward} says otherwise). */
	public static Payload.Move pull(double strength) {
		return new Payload.Move(Payload.MoveKind.PULL, Val.of(strength), Payload.Center.CASTER, false, 0, false, false, false);
	}

	/** {@code LCH s}: upward. */
	public static Payload.Move launch(double strength) {
		return new Payload.Move(Payload.MoveKind.LAUNCH, Val.of(strength), Payload.Center.CASTER, false, 0, false, false, false);
	}

	/** {@code SLAM s}: downward. */
	public static Payload.Move slam(double strength) {
		return new Payload.Move(Payload.MoveKind.SLAM, Val.of(strength), Payload.Center.CASTER, false, 0, false, false, false);
	}

	/** {@code EFF effect level t}: {@code level} 1 = I ("slowness II 60t" = {@code eff(SLOWNESS, 2, 60)}). */
	public static Payload.Effect eff(Payload.Eff effect, int level, int ticks) {
		return new Payload.Effect(effect, Math.max(1, level), Val.of(ticks), -1);
	}

	/** {@code EFF effect level b→c t}. */
	public static Payload.Effect eff(Payload.Eff effect, int level, int baseTicks, int capTicks) {
		return new Payload.Effect(effect, Math.max(1, level), Val.of(baseTicks, capTicks), -1);
	}

	/** {@code MARK kind} for {@code ticks}. */
	public static Payload.Marked mark(Mark mark, int ticks) {
		return new Payload.Marked(mark, ticks);
	}

	/** {@code ATTR attribute ±share t} (a percent of the total: −0.5 = −50%). */
	public static Payload.Attr attrPct(Payload.AttrKind attribute, double share, int ticks) {
		return new Payload.Attr(attribute, share, ticks, true, 1.0D);
	}

	/** {@code ATTR attribute ±amount t} (flat). */
	public static Payload.Attr attr(Payload.AttrKind attribute, double amount, int ticks) {
		return new Payload.Attr(attribute, amount, ticks, false, 1.0D);
	}

	/** {@code EXEC threshold (boss 0.05)}. */
	public static Payload.Exec exec(double threshold) {
		return new Payload.Exec(threshold, 0.05D, 0.0D);
	}

	/** {@code EXEC threshold (boss b)} (b ≤ 0.05). */
	public static Payload.Exec exec(double threshold, double bossThreshold) {
		return new Payload.Exec(threshold, Math.min(0.05D, bossThreshold), 0.0D);
	}

	/** {@code EXEC maxHealthBelow k×P} (non-bosses only). */
	public static Payload.Exec execBelow(double powerFactor) {
		return new Payload.Exec(0.0D, 0.0D, powerFactor);
	}

	/** {@code DRAIN share}. */
	public static Payload.Drain drain(double share) {
		return new Payload.Drain(share, 0.0D, 0.0D, 0.0D, 0.0D);
	}

	public static Payload.Untarget untarget() {
		return new Payload.Untarget();
	}

	public static Payload.Taunt taunt(int ticks) {
		return new Payload.Taunt(ticks);
	}

	/** {@code STRIP_BUFFS} (the default list). */
	public static Payload.Strip stripBuffs() {
		return new Payload.Strip(List.of());
	}

	/** {@code STRIP_BUFFS (list)} / {@code STRIP_EFFECT id}. */
	@SafeVarargs
	public static Payload.Strip strip(Holder<MobEffect>... effects) {
		return new Payload.Strip(List.of(effects));
	}

	/** {@code LINK share t (boss share)}. */
	public static Payload.Link link(double share, int ticks, double bossShare) {
		return new Payload.Link(share, ticks, bossShare);
	}

	/** A hostile payload with probability {@code chance} per evaluation. */
	public static Payload.ChanceHit chance(double chance, Payload.Hit payload) {
		return new Payload.ChanceHit(chance, payload);
	}

	/** {@code special(id)}: a per-target (or per-cast) handler of a SpecialTechnique. */
	public static Payload.Special special(String id) {
		return new Payload.Special(id);
	}

	// ================================================================== caster and ally payloads (22.3.3)

	/** {@code DASH dist} along the look. */
	public static Payload.Dash dash(double distance) {
		return new Payload.Dash(Val.of(distance), Payload.DashDir.LOOK, 0, 4, 40);
	}

	/** {@code DASH b→c} along the look. */
	public static Payload.Dash dash(double base, double cap) {
		return new Payload.Dash(Val.of(base, cap), Payload.DashDir.LOOK, 0, 4, 40);
	}

	/** {@code BLINK range mode}. */
	public static Payload.Blink blink(Payload.BlinkMode mode, double range) {
		return new Payload.Blink(mode, Val.of(range), Val.of(0.0D), 0.0D, 3, false, 0.0D, false, null, 0);
	}

	/** {@code BLINK b→c mode}. */
	public static Payload.Blink blink(Payload.BlinkMode mode, double base, double cap) {
		return new Payload.Blink(mode, Val.of(base, cap), Val.of(0.0D), 0.0D, 3, false, 0.0D, false, null, 0);
	}

	/** {@code HEAL share} (of max health; ally radius from the step). */
	public static Payload.Heal heal(double share) {
		return new Payload.Heal(Val.of(share), 0, false);
	}

	/** {@code HEAL b→c} (grows with mastery). */
	public static Payload.Heal heal(double base, double cap) {
		return new Payload.Heal(Val.of(base, cap), 0, false);
	}

	/** Ends the caster's current form early. */
	public static Payload.Revert revert() {
		return new Payload.Revert();
	}

	/** {@code CLEANSE} (the default list). */
	public static Payload.Cleanse cleanse() {
		return new Payload.Cleanse(List.of());
	}

	/** {@code CLEANSE (list)}. */
	@SafeVarargs
	public static Payload.Cleanse cleanse(Holder<MobEffect>... effects) {
		return new Payload.Cleanse(List.of(effects));
	}

	/** {@code SHLD share t}. */
	public static Payload.Shield shield(double share, int ticks) {
		return new Payload.Shield(share, Val.of(ticks));
	}

	/** {@code BUFF kind value t}. */
	public static Payload.Buff buff(Payload.BuffKind kind, double value, int ticks) {
		return new Payload.Buff(kind, value, 0.0D, Val.of(ticks), null, false, false, 1);
	}

	/** {@code BUFF kind value b→c t}. */
	public static Payload.Buff buff(Payload.BuffKind kind, double value, int baseTicks, int capTicks) {
		return new Payload.Buff(kind, value, 0.0D, Val.of(baseTicks, capTicks), null, false, false, 1);
	}

	/** {@code EMP +share t} for every technique. */
	public static Payload.Buff empower(double share, int ticks) {
		return buff(Payload.BuffKind.EMPOWER, share, ticks);
	}

	/** {@code EMP(dao +share) t}. */
	public static Payload.Buff empower(Dao dao, double share, int ticks) {
		return buff(Payload.BuffKind.EMPOWER, share, ticks).dao(dao);
	}

	/** {@code COST_MOD(dao qi ×q, cd ×c) t} (null dao = every technique). */
	public static Payload.Buff costMod(@Nullable Dao dao, double qiFactor, double cooldownFactor, int ticks) {
		return new Payload.Buff(Payload.BuffKind.COST_MOD, qiFactor, cooldownFactor, Val.of(ticks), dao, false, false, 1);
	}

	/** {@code CD_RATE(dao ×f) t} (within the ×0.4 floor, never PAMUNGKAS; null dao = every technique). */
	public static Payload.Buff cdRate(@Nullable Dao dao, double factor, int ticks) {
		return new Payload.Buff(Payload.BuffKind.CD_RATE, factor, 0.0D, Val.of(ticks), dao, false, false, 1);
	}

	/** {@code DR share facing t}. */
	public static Payload.Dr dr(double share, Payload.Facing facing, int ticks) {
		return new Payload.Dr(Val.of(share), facing, Val.of(ticks), false, 0.0D);
	}

	/** {@code DR b→c facing t} (grows with mastery). */
	public static Payload.Dr dr(double base, double cap, Payload.Facing facing, int ticks) {
		return new Payload.Dr(Val.of(base, cap), facing, Val.of(ticks), false, 0.0D);
	}

	/** {@code DODGE chance t}. */
	public static Payload.Dodge dodge(double chance, int ticks) {
		return new Payload.Dodge(chance, Val.of(ticks));
	}

	/** {@code TRANSFORM form t} at scale 1 (add {@code .scale(...)} and the form's buffs with {@code .with(...)}). */
	public static Payload.Transform transform(String form, int ticks) {
		return new Payload.Transform(form, Val.of(ticks), Val.of(1.0D), List.of());
	}

	/** {@code TRANSFORM form b→c t}. */
	public static Payload.Transform transform(String form, int baseTicks, int capTicks) {
		return new Payload.Transform(form, Val.of(baseTicks, capTicks), Val.of(1.0D), List.of());
	}

	/** {@code SUMMON kind n t} (virtual phantoms; follow mode, attack every 20 ticks within 12). */
	public static Payload.Summon summon(String kind, double count, int ticks) {
		return new Payload.Summon(kind, Val.of(count), Val.of(ticks), Payload.SummonMode.FOLLOW, 20, Val.of(12.0D), null, Payload.Pattern.NONE);
	}

	/** {@code SUMMON kind b→c t}. */
	public static Payload.Summon summon(String kind, double baseCount, double capCount, int ticks) {
		return new Payload.Summon(kind, Val.of(baseCount, capCount), Val.of(ticks), Payload.SummonMode.FOLLOW, 20, Val.of(12.0D), null, Payload.Pattern.NONE);
	}

	/** {@code RESTORE_QI share}. */
	public static Payload.RestoreQi restoreQi(double share) {
		return new Payload.RestoreQi(share, 0.0D);
	}

	/** {@code RESTORE_QI share (≤ cap per cast)}. */
	public static Payload.RestoreQi restoreQi(double share, double capPerCast) {
		return new Payload.RestoreQi(share, capPerCast);
	}

	public static Payload.CdReset cdReset(TechniqueGrade maxGrade) {
		return new Payload.CdReset(maxGrade);
	}

	/** {@code GRANT_HOOK}. */
	public static Payload.GrantHook hook(Hook hook) {
		return new Payload.GrantHook(hook);
	}

	/** {@code COUNTER window store cap kind}. */
	public static Payload.Counter counter(int windowTicks, double store, double cap, Payload.Store kind) {
		return new Payload.Counter(Val.of(windowTicks), store, cap, kind, 0.0D, null);
	}

	/** {@code CHEAT_DEATH charges t}: a lethal hit leaves {@code healthShare} (0 = 1 HP). */
	public static Payload.CheatDeath cheatDeath(int charges, int ticks, double healthShare) {
		return new Payload.CheatDeath(Val.of(charges), Val.of(ticks), healthShare, null);
	}

	/** {@code PROJECTILE_GUARD discard (arc°, radius, charges, t)}. */
	public static Payload.ProjectileGuard projectileGuard(double arc, double radius, int charges, int ticks) {
		return new Payload.ProjectileGuard(false, arc, radius, Val.of(charges), Val.of(ticks), 0.0D, false);
	}

	/** {@code SELF_PENALTY t}: weakness level (0 = none), qi regen ×0, technique damage ×factor (1 = none). */
	public static Payload.SelfPenalty selfPenalty(int ticks, int weakness, boolean qiRegenZero, double damageFactor) {
		return new Payload.SelfPenalty(ticks, weakness, qiRegenZero, damageFactor);
	}

	/** {@code FORBIDDEN_COST hp share (of max or current) [qi share]}. */
	public static Payload.ForbiddenCost forbiddenCost(double hpShare, boolean ofMax, double qiShare) {
		return new Payload.ForbiddenCost(hpShare, ofMax, qiShare);
	}

	/** {@code COMPREHENSION +x/s (≤ cap per cast)}. */
	public static Payload.Comprehension comprehensionGain(double perSecond, double capPerCast) {
		return new Payload.Comprehension(perSecond, 0.0D, capPerCast);
	}

	public static Payload.Extinguish extinguish() {
		return new Payload.Extinguish();
	}

	// ================================================================== FX templates (22.4.2)

	public static FxLayer arc(int color) { return FxLayer.of(FxLayer.Template.ARC, color); }
	public static FxLayer crescentFly(int color) { return FxLayer.of(FxLayer.Template.CRESCENT_FLY, color); }
	public static FxLayer beamCore(int color) { return FxLayer.of(FxLayer.Template.BEAM_CORE, color).line(); }
	public static FxLayer coneSpray(int color) { return FxLayer.of(FxLayer.Template.CONE_SPRAY, color); }
	public static FxLayer shell(int color) { return FxLayer.of(FxLayer.Template.SHELL, color); }
	public static FxLayer shockRing(int color) { return FxLayer.of(FxLayer.Template.SHOCK_RING, color); }
	public static FxLayer dome(int color) { return FxLayer.of(FxLayer.Template.DOME, color); }
	public static FxLayer column(int color) { return FxLayer.of(FxLayer.Template.COLUMN, color); }
	public static FxLayer bolt(int color) { return FxLayer.of(FxLayer.Template.BOLT, color).line(); }
	public static FxLayer boltStorm(int color) { return FxLayer.of(FxLayer.Template.BOLT_STORM, color); }
	public static FxLayer cloudDisc(int color) { return FxLayer.of(FxLayer.Template.CLOUD_DISC, color); }
	public static FxLayer vortex(int color) { return FxLayer.of(FxLayer.Template.VORTEX, color); }
	public static FxLayer runeCircle(int color) { return FxLayer.of(FxLayer.Template.RUNE_CIRCLE, color); }
	public static FxLayer petals(int color) { return FxLayer.of(FxLayer.Template.PETALS, color); }
	public static FxLayer palm(int color) { return FxLayer.of(FxLayer.Template.PALM, color); }
	public static FxLayer fist(int color) { return FxLayer.of(FxLayer.Template.FIST, color); }
	public static FxLayer swordShape(int color) { return FxLayer.of(FxLayer.Template.SWORD_SHAPE, color); }
	public static FxLayer wings(int color) { return FxLayer.of(FxLayer.Template.WINGS, color).onCaster(); }
	public static FxLayer beast(FxLayer.BeastKind kind, int color) { return FxLayer.of(FxLayer.Template.BEAST, color).beast(kind); }
	public static FxLayer trail(int color) { return FxLayer.of(FxLayer.Template.TRAIL, color).line(); }
	public static FxLayer impact(int color) { return FxLayer.of(FxLayer.Template.IMPACT, color); }
	/** Ground cracks; only drawn for Kehancuran casts (TechFx flag 1), with BLOCK_CRUMBLE. */
	public static FxLayer cracks() { return FxLayer.of(FxLayer.Template.CRACKS, 0x3A2A1A).kehancuranOnly(); }
	/** Ground cracks drawn on every cast (purely visual, never real blocks). */
	public static FxLayer cracksAlways(int color) { return FxLayer.of(FxLayer.Template.CRACKS, color); }
	public static FxLayer mist(int color) { return FxLayer.of(FxLayer.Template.MIST, color); }
	public static FxLayer shards(int color) { return FxLayer.of(FxLayer.Template.SHARDS, color); }
	public static FxLayer chains(int color) { return FxLayer.of(FxLayer.Template.CHAINS, color).line(); }
	public static FxLayer motesTo(int color) { return FxLayer.of(FxLayer.Template.MOTES_TO, color); }
	public static FxLayer healRise(int color) { return FxLayer.of(FxLayer.Template.HEAL_RISE, color); }
	public static FxLayer shieldShell(int color) { return FxLayer.of(FxLayer.Template.SHIELD_SHELL, color).onCaster(); }
	public static FxLayer afterimage(int color) { return FxLayer.of(FxLayer.Template.AFTERIMAGE, color); }
	public static FxLayer skyTear(int color) { return FxLayer.of(FxLayer.Template.SKY_TEAR, color).inSky(); }
	public static FxLayer meteor(int color) { return FxLayer.of(FxLayer.Template.METEOR, color); }
	public static FxLayer starfield(int color) { return FxLayer.of(FxLayer.Template.STARFIELD, color); }

	// ================================================================== phantom displays (22.4.3, client-only)

	/** A giant ItemDisplay (scale ≤ 24). */
	public static FxLayer giantItem(Item item) { return FxLayer.of(FxLayer.Template.GIANT_ITEM, 0xFFFFFF).item(item); }
	/** A GIANT_ITEM of the caster's held item (the held sword rising, flying or cleaving). */
	public static FxLayer heldItem() { return giantItem(FxLayer.HELD); }
	/** A giant BlockDisplay (size ≤ 24 per axis; set it with {@code blockSize}). */
	public static FxLayer giantBlock(BlockState state) { return FxLayer.of(FxLayer.Template.GIANT_BLOCK, 0xFFFFFF).block(state); }
	/** A stretched translucent BlockDisplay beam (length ≤ 128). */
	public static FxLayer laser(BlockState glass, float width, float length) { return FxLayer.of(FxLayer.Template.LASER, 0xFFFFFF).block(glass).width(width).length(length).line(); }
	/** {@code n} (≤ 16) glass petals opening on a ring of radius {@code r}. */
	public static FxLayer petalRing(BlockState state, int n, float r) { return FxLayer.of(FxLayer.Template.PETAL_RING, 0xFFFFFF).block(state).n(n).radius(r); }
	/** A billboard TextDisplay glyph (scale ≤ 40), e.g. 佛 封 定 帝 雷 剑. */
	public static FxLayer glyph(String text) { return FxLayer.of(FxLayer.Template.GLYPH, 0xFFD54A).text(text); }
	/** {@code n} (≤ 24) orbiting ItemDisplays on a ring of radius {@code r}. */
	public static FxLayer swordRing(Item item, int n, float r) { return FxLayer.of(FxLayer.Template.SWORD_RING, 0xFFFFFF).item(item).n(n).radius(r); }

	/**
	 * A 3D prop model {@code kultivator:fx/<id>} (DESIGN 27.3): an item display with the cuboid model, posed and animated
	 * by the client's ModelFx (scale, fall, grow, slide, swing...). Falls back to particles at Minimal or without budget.
	 */
	public static FxLayer model(String id) { return FxLayer.of(FxLayer.Template.MODEL, 0xFFFFFF).model(id); }
	/** A glowing 3D prop model (light 15, the prop carries its own emissive texture). */
	public static FxLayer modelGlow(String id, int tint) { return FxLayer.of(FxLayer.Template.MODEL, tint).model(id).color2(tint); }

	// ================================================================== custom renderers (22.4.4)

	/** A segmented ribbon from the position to the second point (dragons, whips, flame and blood ribbons). */
	public static FxLayer ribbon(int color) { return FxLayer.of(FxLayer.Template.RIBBON, color).line(); }

	public static FxLayer avatar(int color) { return FxLayer.of(FxLayer.Template.AVATAR, color).onCaster(); }
	public static FxLayer cloneOf(int color) { return FxLayer.of(FxLayer.Template.CLONE, color); }
	public static FxLayer swarm(int color) { return FxLayer.of(FxLayer.Template.SWARM, color); }
	public static FxLayer skySilhouette(FxLayer.BeastKind kind, int color) { return FxLayer.of(FxLayer.Template.SKY_SILHOUETTE, color).beast(kind).inSky(); }
	public static FxLayer viewerMark(int color) { return FxLayer.of(FxLayer.Template.VIEWER_MARK, color).onTarget(); }

	// ================================================================== post effects (22.4.5)

	/** {@code SHK intensity (0-1), ticks}. */
	public static FxLayer shake(float intensity, int ticks) { return FxLayer.of(FxLayer.Template.SHAKE, 0).intensity(intensity).ticks(ticks); }
	/** {@code FLASH colour, α ≤ 0.35, ≤ 8 ticks}. */
	public static FxLayer flash(int color, float alpha, int ticks) { return FxLayer.of(FxLayer.Template.FLASH, color).alpha(Math.min(0.35F, alpha)).ticks(Math.min(8, ticks)); }
	public static FxLayer vignette(int color, int ticks) { return FxLayer.of(FxLayer.Template.VIGNETTE, color).ticks(ticks); }
	/** CLOUD_DISC + SKY_TEAR + VIGNETTE for Abadi/Dewa ultimates; never a weather change. */
	public static FxLayer sky(int color) { return FxLayer.of(FxLayer.Template.SKY, color).inSky(); }

	private Dsl() {
	}
}
