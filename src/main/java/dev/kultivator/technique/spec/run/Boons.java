package dev.kultivator.technique.spec.run;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.technique.spec.dsl.Hook;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Phase;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Technique boons on casters and allies (DESIGN 22.3.3): BUFF kinds (vanilla effects, transient attribute modifiers,
 * EMPOWER, REFLECT, LIFESTEAL, QI_REGEN, CD_RATE, COST_MOD, IMMUNE_SLOW, AIR_JUMP, NO_FALL, KH_RADIUS), GRANT_HOOK timed
 * hooks (internal cooldown ≥ 4 ticks), PROJECTILE_GUARD (hostile projectiles are discarded, never deflected), dash
 * i-frames and fall immunity, SELF_PENALTY and the Divine Sense glow. Session only; zero cost while empty.
 */
public final class Boons {

	/** Effects a buff "while the cast lasts" uses as its duration (removed when the cast ends). */
	private static final int WHILE_CAST_TICKS = 20 * 60 * 20;
	private static final int ENDLESS = Integer.MAX_VALUE / 4;

	private static final class BuffEntry {
		final Payload.BuffKind kind;
		final double value;
		final double value2;
		long until;
		final @Nullable Dao dao;
		final boolean onlyInArea;
		final boolean hpScaled;
		int stacks = 1;
		final int stackMax;
		final @Nullable Caster source;
		final boolean whileCast;
		/** The vanilla effect level this entry granted (−1: none); removed by that level whatever its duration. */
		int amplifier = -1;

		BuffEntry(Payload.Buff buff, long until, @Nullable Caster source, boolean whileCast) {
			this.kind = buff.kind();
			this.value = buff.value();
			this.value2 = buff.value2();
			this.until = until;
			this.dao = buff.dao();
			this.onlyInArea = buff.onlyInArea();
			this.hpScaled = buff.hpScaled();
			this.stackMax = Math.max(1, buff.stackMax());
			this.source = source;
			this.whileCast = whileCast;
		}
	}

	private static final class HookEntry {
		final Hook hook;
		final Caster source;
		long until;
		int chargesLeft;
		long lastFire = Long.MIN_VALUE / 2;
		long window;
		int windowCount;
		final boolean whileCast;

		HookEntry(Hook hook, Caster source, long until, boolean whileCast) {
			this.hook = hook;
			this.source = source;
			this.until = until;
			this.chargesLeft = hook.charges() <= 0 ? -1 : hook.charges();
			this.whileCast = whileCast;
		}
	}

	private static final class Guard {
		final Payload.ProjectileGuard guard;
		final Caster source;
		long until;
		int charges;
		final double radius;
		final boolean whileCast;

		Guard(Payload.ProjectileGuard guard, Caster source, long until, int charges, double radius, boolean whileCast) {
			this.guard = guard;
			this.source = source;
			this.until = until;
			this.charges = charges <= 0 ? -1 : charges;
			this.radius = radius;
			this.whileCast = whileCast;
		}
	}

	private static final class State {
		/** Who holds it: casters and allies alike. */
		LivingEntity holder;
		final List<BuffEntry> buffs = new ArrayList<>();
		final List<HookEntry> hooks = new ArrayList<>();
		final List<Guard> guards = new ArrayList<>();
		long noFallUntil;
		long iframesUntil;
		long divineSenseUntil;
		long penaltyUntil;
		boolean penaltyQiZero;
		double penaltyDamage = 1.0D;
		int resistanceAmplifier = -1;
		long resistanceUntil;

		boolean idle(long now) {
			return buffs.isEmpty() && hooks.isEmpty() && guards.isEmpty() && now >= noFallUntil && now >= iframesUntil
					&& now >= divineSenseUntil && now >= penaltyUntil && now >= resistanceUntil;
		}
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	/** True while a hook's own step runs (hooks never trigger themselves). */
	private static boolean firing;

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Boons::afterDamage);
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(Boons::afterKill);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
	}

	private static State state(LivingEntity who) {
		State state = STATES.computeIfAbsent(who.getUUID(), key -> new State());
		state.holder = who;
		return state;
	}

	private static @Nullable State peek(Entity who) {
		return STATES.get(who.getUUID());
	}

	// ================================================================== buffs

	/**
	 * Applies a BUFF for {@code ticks} ({@code whileCast}: until the granting cast ends). Vanilla effects use
	 * {@code value} as the level (resistance at most III); attribute buffs are transient {@code kultivator:tbuff_*}
	 * modifiers; the mod's own kinds are read by the runner.
	 */
	static void buff(@Nullable Caster source, LivingEntity who, Payload.Buff buff, int ticks, boolean whileCast) {
		int duration = whileCast ? WHILE_CAST_TICKS : Math.max(1, ticks);
		long now = who.level().getGameTime();
		Holder<MobEffect> effect = buff.kind().effect();
		State state = state(who);
		int amplifier = -1;
		if (effect != null) {
			amplifier = Math.max(0, (int) Math.round(buff.value()) - 1);
			if (buff.kind() == Payload.BuffKind.RESISTANCE) {
				amplifier = Math.min(2, amplifier);
				state.resistanceAmplifier = Math.max(state.resistanceAmplifier, amplifier);
				state.resistanceUntil = Math.max(state.resistanceUntil, now + duration);
			}
			MobEffectInstance active = who.getEffect(effect);
			if (active == null || active.getAmplifier() < amplifier || active.getAmplifier() == amplifier && active.getDuration() < duration) {
				who.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true), source == null ? null : source.caster());
			}
			if (!whileCast) {
				return;
			}
		}
		for (BuffEntry entry : state.buffs) {
			if (entry.kind == buff.kind() && entry.dao == buff.dao() && entry.source == source) {
				entry.until = Math.max(entry.until, now + duration);
				entry.amplifier = Math.max(entry.amplifier, amplifier);
				if (entry.stackMax > 1) {
					entry.stacks = Math.min(entry.stackMax, entry.stacks + 1);
				}
				refreshModifier(who, entry);
				return;
			}
		}
		BuffEntry entry = new BuffEntry(buff, now + duration, source, whileCast);
		entry.amplifier = amplifier;
		state.buffs.add(entry);
		refreshModifier(who, entry);
	}

	/** Ticks left on a "while the cast lasts" effect below which it is renewed (every second while the cast runs). */
	private static final int WHILE_CAST_RENEW = 600;

	/**
	 * Keeps "while the cast lasts" boons alive as long as their cast runs (a toggle held past 20 minutes): the entry's
	 * end moves on and a granted effect that was lost or runs low is put back at its level.
	 */
	private static void renewWhileCast(LivingEntity who, State state, long now) {
		for (BuffEntry buff : state.buffs) {
			if (!buff.whileCast || buff.source == null || buff.source.ended) {
				continue;
			}
			buff.until = Math.max(buff.until, now + WHILE_CAST_TICKS);
			Holder<MobEffect> effect = buff.kind.effect();
			if (effect == null || buff.amplifier < 0) {
				continue;
			}
			if (buff.kind == Payload.BuffKind.RESISTANCE) {
				state.resistanceUntil = Math.max(state.resistanceUntil, now + WHILE_CAST_TICKS);
			}
			MobEffectInstance active = who.getEffect(effect);
			if (active == null || active.getAmplifier() < buff.amplifier
					|| active.getAmplifier() == buff.amplifier && active.getDuration() < WHILE_CAST_RENEW) {
				who.addEffect(new MobEffectInstance(effect, WHILE_CAST_TICKS, buff.amplifier, false, true), buff.source.caster());
			}
		}
	}

	/**
	 * Technique Resistance granted by a direct {@code addEffect} (Dharma Form, the Body Dao domain pulse): recorded so the
	 * DR cap counts it (22.14.3).
	 */
	public static void trackResistance(LivingEntity who, int amplifier, int ticks) {
		if (ticks <= 0 || amplifier < 0) {
			return;
		}
		State state = state(who);
		state.resistanceAmplifier = Math.max(state.resistanceAmplifier, Math.min(4, amplifier));
		state.resistanceUntil = Math.max(state.resistanceUntil, who.level().getGameTime() + ticks);
	}

	private static void refreshModifier(LivingEntity who, BuffEntry entry) {
		double amount = entry.value * entry.stacks;
		switch (entry.kind) {
			case REACH -> {
				modifier(who, Attributes.ENTITY_INTERACTION_RANGE, id(entry.kind), amount, AttributeModifier.Operation.ADD_VALUE);
				modifier(who, Attributes.BLOCK_INTERACTION_RANGE, Kultivator.id("tbuff_reach_block"), amount, AttributeModifier.Operation.ADD_VALUE);
			}
			case ARMOR -> modifier(who, Attributes.ARMOR, id(entry.kind), amount, AttributeModifier.Operation.ADD_VALUE);
			// a value below 1 is a share (+50% = 0.5: lei_zhenzi_wings, bright_king_art, eighth_hex_body); 1 and above is flat
			case ATTACK_DAMAGE -> modifier(who, Attributes.ATTACK_DAMAGE, id(entry.kind), amount, Math.abs(entry.value) < 1.0D
					? AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL : AttributeModifier.Operation.ADD_VALUE);
			case ATTACK_SPEED -> modifier(who, Attributes.ATTACK_SPEED, id(entry.kind), amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			case KNOCKBACK_RESISTANCE -> modifier(who, Attributes.KNOCKBACK_RESISTANCE, id(entry.kind), amount, AttributeModifier.Operation.ADD_VALUE);
			default -> {
			}
		}
	}

	private static void removeModifier(LivingEntity who, BuffEntry entry) {
		switch (entry.kind) {
			case REACH -> {
				remove(who, Attributes.ENTITY_INTERACTION_RANGE, id(entry.kind));
				remove(who, Attributes.BLOCK_INTERACTION_RANGE, Kultivator.id("tbuff_reach_block"));
			}
			case ARMOR -> remove(who, Attributes.ARMOR, id(entry.kind));
			case ATTACK_DAMAGE -> remove(who, Attributes.ATTACK_DAMAGE, id(entry.kind));
			case ATTACK_SPEED -> remove(who, Attributes.ATTACK_SPEED, id(entry.kind));
			case KNOCKBACK_RESISTANCE -> remove(who, Attributes.KNOCKBACK_RESISTANCE, id(entry.kind));
			default -> {
				Holder<MobEffect> effect = entry.kind.effect();
				if (effect != null && entry.whileCast && entry.amplifier >= 0) {
					// the level this entry granted comes off, however long it has left (a potion of another level stays)
					MobEffectInstance active = who.getEffect(effect);
					if (active != null && active.getAmplifier() == entry.amplifier) {
						who.removeEffect(effect);
					}
				}
			}
		}
	}

	private static Identifier id(Payload.BuffKind kind) {
		return Kultivator.id("tbuff_" + kind.name().toLowerCase(Locale.ROOT));
	}

	/** The active value of every buff of {@code kind} on {@code who} (summed; hp-scaled and stacked). */
	private static double sum(LivingEntity who, Payload.BuffKind kind, @Nullable Dao dao) {
		State state = peek(who);
		if (state == null) {
			return 0.0D;
		}
		long now = who.level().getGameTime();
		double total = 0.0D;
		for (BuffEntry entry : state.buffs) {
			if (entry.kind == kind && now < entry.until && (entry.dao == null || entry.dao == dao)
					&& (!entry.onlyInArea || Fields.inOwnArea(who, who.position()))) {
				total += value(who, entry);
			}
		}
		return total;
	}

	private static double value(LivingEntity who, BuffEntry entry) {
		double value = entry.value;
		if (entry.hpScaled) {
			double missing = 1.0D - Math.max(0.0D, Math.min(1.0D, who.getHealth() / Math.max(1.0F, who.getMaxHealth())));
			value = entry.value2 + (entry.value - entry.value2) * missing;
		}
		return value * entry.stacks;
	}

	/** EMPOWER: technique damage × (1 + Σ shares) for a spec of {@code dao}, × the SELF_PENALTY damage factor. */
	static double empower(LivingEntity who, @Nullable Dao dao) {
		State state = peek(who);
		if (state == null) {
			return 1.0D;
		}
		double factor = 1.0D + sum(who, Payload.BuffKind.EMPOWER, dao);
		if (who.level().getGameTime() < state.penaltyUntil) {
			factor *= state.penaltyDamage;
		}
		return Math.max(0.0D, factor);
	}

	/** COST_MOD qi factor (product) for {@code spec}. */
	public static double qiFactor(LivingEntity who, TechniqueSpec spec) {
		return product(who, Payload.BuffKind.COST_MOD, spec.dao(), false);
	}

	/** COST_MOD cooldown factor × CD_RATE factors (product) for {@code spec}; the ×0.4 floor is applied by TechniquePower. */
	public static double cooldownFactor(LivingEntity who, TechniqueSpec spec) {
		return product(who, Payload.BuffKind.COST_MOD, spec.dao(), true) * product(who, Payload.BuffKind.CD_RATE, spec.dao(), false);
	}

	private static double product(LivingEntity who, Payload.BuffKind kind, @Nullable Dao dao, boolean second) {
		State state = peek(who);
		if (state == null) {
			return 1.0D;
		}
		long now = who.level().getGameTime();
		double factor = 1.0D;
		for (BuffEntry entry : state.buffs) {
			if (entry.kind == kind && now < entry.until && (entry.dao == null || entry.dao == dao)
					&& (!entry.onlyInArea || Fields.inOwnArea(who, who.position()))) {
				double value = second ? entry.value2 : entry.value;
				if (value > 0.0D) {
					factor *= value;
				}
			}
		}
		return factor;
	}

	/** Qi regeneration factor: QI_REGEN factors, 0 during a SELF_PENALTY that stops regeneration. */
	public static double qiRegenFactor(LivingEntity who) {
		State state = peek(who);
		if (state == null) {
			return 1.0D;
		}
		if (state.penaltyQiZero && who.level().getGameTime() < state.penaltyUntil) {
			return 0.0D;
		}
		return product(who, Payload.BuffKind.QI_REGEN, null, false);
	}

	/** REFLECT share (≤ 1). */
	static double reflect(LivingEntity who) {
		return Math.min(1.0D, sum(who, Payload.BuffKind.REFLECT, null));
	}

	/** Extra air jumps (Cloud Step). */
	public static int airJumps(LivingEntity who) {
		return (int) Math.round(sum(who, Payload.BuffKind.AIR_JUMP, null));
	}

	/** Extra Kehancuran radius while a KH_RADIUS buff lasts. */
	public static double khRadius(LivingEntity who) {
		return sum(who, Payload.BuffKind.KH_RADIUS, null);
	}

	/** No fall damage: a NO_FALL buff or the 40 ticks after a dash. */
	public static boolean noFall(LivingEntity who) {
		State state = peek(who);
		return state != null && (who.level().getGameTime() < state.noFallUntil || sum(who, Payload.BuffKind.NO_FALL, null) > 0.0D);
	}

	/** Dash i-frames. */
	public static boolean iframes(LivingEntity who) {
		State state = peek(who);
		return state != null && who.level().getGameTime() < state.iframesUntil;
	}

	static void noFall(LivingEntity who, int ticks) {
		State state = state(who);
		state.noFallUntil = Math.max(state.noFallUntil, who.level().getGameTime() + ticks);
	}

	static void iframes(LivingEntity who, int ticks) {
		if (ticks > 0) {
			State state = state(who);
			state.iframesUntil = Math.max(state.iframesUntil, who.level().getGameTime() + ticks);
		}
	}

	/** Divine Sense glow is active for the caster (condition DIVINE_SENSE, Fiery Golden Eyes). */
	public static boolean divineSense(LivingEntity who) {
		State state = peek(who);
		return state != null && who.level().getGameTime() < state.divineSenseUntil;
	}

	public static void markDivineSense(LivingEntity who, int ticks) {
		State state = state(who);
		state.divineSenseUntil = Math.max(state.divineSenseUntil, who.level().getGameTime() + ticks);
	}

	/** Technique-granted Resistance amplifier active on {@code who} (−1 for none); counted inside the DR cap. */
	static int resistance(LivingEntity who) {
		State state = peek(who);
		if (state == null || who.level().getGameTime() >= state.resistanceUntil) {
			return -1;
		}
		MobEffectInstance active = who.getEffect(MobEffects.RESISTANCE);
		return active == null ? -1 : Math.min(state.resistanceAmplifier, active.getAmplifier());
	}

	/** SELF_PENALTY when a technique ends: Weakness, qi regeneration ×0, technique damage × factor. */
	static void penalty(LivingEntity who, Payload.SelfPenalty penalty) {
		if (penalty.ticks() <= 0) {
			return;
		}
		State state = state(who);
		long until = who.level().getGameTime() + penalty.ticks();
		state.penaltyUntil = Math.max(state.penaltyUntil, until);
		state.penaltyQiZero |= penalty.qiRegenZero();
		state.penaltyDamage = Math.min(state.penaltyDamage, Math.max(0.0D, penalty.damageFactor()));
		if (penalty.weakness() > 0) {
			who.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, penalty.ticks(), penalty.weakness() - 1, false, true));
		}
	}

	// ================================================================== hooks

	static void hook(Caster source, LivingEntity who, Hook hook, int ticks, boolean whileCast) {
		long now = who.level().getGameTime();
		long until = whileCast ? now + ENDLESS : now + Math.max(1, ticks);
		state(who).hooks.add(new HookEntry(hook, source, until, whileCast));
	}

	/** One of the caster's techniques hit {@code target} (ON_TECHNIQUE_HIT and ON_ANY_HIT hooks). */
	static void onTechniqueHit(LivingEntity who, LivingEntity target) {
		if (!firing) {
			fire(who, target, Hook.Trigger.ON_TECHNIQUE_HIT, 0.0F, null);
		}
	}

	private static void fire(LivingEntity who, @Nullable LivingEntity other, Hook.Trigger trigger, float damage, @Nullable DamageSource source) {
		State state = peek(who);
		if (state == null || state.hooks.isEmpty()) {
			return;
		}
		long now = who.level().getGameTime();
		for (HookEntry entry : List.copyOf(state.hooks)) {
			Hook hook = entry.hook;
			boolean matches = hook.trigger() == trigger
					|| hook.trigger() == Hook.Trigger.ON_ANY_HIT && (trigger == Hook.Trigger.ON_MELEE_HIT || trigger == Hook.Trigger.ON_TECHNIQUE_HIT);
			if (!matches || now >= entry.until || now - entry.lastFire < Math.max(Hook.MIN_ICD, hook.icd())) {
				continue;
			}
			if (hook.needsSword() && !who.getMainHandItem().is(ItemTags.SWORDS)) {
				continue;
			}
			if (trigger == Hook.Trigger.ON_HURT && damage < who.getMaxHealth() * hook.minShare()) {
				continue;
			}
			if (hook.frontOnly() && other != null) {
				Vec3 to = other.position().subtract(who.position()).horizontal();
				if (to.lengthSqr() > 1.0E-4D && to.normalize().dot(who.getViewVector(1.0F).horizontal().normalize()) < 0.0D) {
					continue;
				}
			}
			if (hook.perSecond() > 0) {
				if (now - entry.window >= 20L) {
					entry.window = now;
					entry.windowCount = 0;
				}
				if (entry.windowCount >= hook.perSecond()) {
					continue;
				}
				entry.windowCount++;
			}
			if (!entry.source.casterValid()) {
				state.hooks.remove(entry);
				continue;
			}
			entry.lastFire = now;
			boolean last = entry.chargesLeft == 1;
			if (entry.chargesLeft > 0) {
				entry.chargesLeft--;
				if (entry.chargesLeft == 0) {
					state.hooks.remove(entry);
				}
			}
			firing = true;
			try {
				entry.source.runHook(last && hook.last() != null ? hook.last() : hook.step(), other);
			} finally {
				firing = false;
			}
		}
	}

	// ================================================================== projectile guards

	static void guard(Caster source, LivingEntity who, Payload.ProjectileGuard guard, int charges, int ticks, double radius, boolean whileCast) {
		long now = who.level().getGameTime();
		long until = whileCast ? now + ENDLESS : now + Math.max(1, ticks);
		state(who).guards.add(new Guard(guard, source, until, charges, radius, whileCast));
	}

	private static void tickGuards(LivingEntity who, State state, long now) {
		if (!(who.level() instanceof ServerLevel level)) {
			return;
		}
		for (Iterator<Guard> it = state.guards.iterator(); it.hasNext(); ) {
			Guard guard = it.next();
			if (now >= guard.until || guard.charges == 0 || !guard.source.casterValid()) {
				it.remove();
				continue;
			}
			Vec3 centre = guard.guard.coversArea() ? Fields.ownAreaCentre(who, who.position()) : who.position();
			double radius = guard.guard.coversArea() ? Math.max(guard.radius, Fields.ownAreaRadius(who)) : guard.radius;
			AABB box = new AABB(centre, centre).inflate(radius);
			List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, box, projectile -> projectile.isAlive()
					&& hostileShot(projectile, who, guard.source));
			Vec3 look = who.getViewVector(1.0F).horizontal();
			for (Projectile projectile : projectiles) {
				if (guard.guard.arc() < 359.0D && look.lengthSqr() > 1.0E-4D) {
					Vec3 to = projectile.position().subtract(who.position()).horizontal();
					if (to.lengthSqr() > 1.0E-4D && Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, to.normalize().dot(look.normalize())))))
							> guard.guard.arc() / 2.0D) {
						continue;
					}
				}
				Entity shooter = projectile.getOwner();
				// every caught shot flashes where it was caught (EXTRA7, the guard phase: pos = the projectile, to = the guarded body)
				guard.source.fx(Phase.EXTRA7, projectile.position(), who.getBoundingBox().getCenter(), 1.0F, 0.0F, 1, 0,
						shooter != null ? shooter.getId() : -1, 0, 1, 0, 0);
				projectile.discard();
				if (guard.guard.returnShot() && shooter instanceof LivingEntity living && guard.source.mayHit(living, true)) {
					guard.source.hurt(guard.source.damage(living, guard.guard.returnCoef()).precise(true));
				}
				if (guard.charges > 0 && --guard.charges == 0) {
					break;
				}
			}
		}
	}

	/**
	 * Only hostile shots are discarded (22.3.3): a projectile without a known owner (a dispenser's arrow, a firework, an
	 * owner not loaded) is never touched, nor the holder's own.
	 */
	private static boolean hostileShot(Projectile projectile, LivingEntity who, Caster source) {
		Entity owner = projectile.getOwner();
		if (owner == null || owner == who) {
			return false;
		}
		if (owner instanceof LivingEntity living) {
			return source.mayHit(living, true) || living instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == who;
		}
		return false;
	}

	// ================================================================== tick and events

	static void tick(long serverTick) {
		if (STATES.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, State> entry = it.next();
			State state = entry.getValue();
			LivingEntity who = TechniqueRunner.current(state.holder);
			if (who == null) {
				// the holder (caster or ally) is gone or dead: its transient modifiers went with the entity
				it.remove();
				continue;
			}
			state.holder = who;
			long now = who.level().getGameTime();
			if (serverTick % 20 == 0) {
				renewWhileCast(who, state, now);
			}
			for (Iterator<BuffEntry> buffs = state.buffs.iterator(); buffs.hasNext(); ) {
				BuffEntry buff = buffs.next();
				if (now >= buff.until) {
					removeModifier(who, buff);
					buffs.remove();
				}
			}
			state.hooks.removeIf(hook -> now >= hook.until);
			if (!state.guards.isEmpty()) {
				tickGuards(who, state, now);
			}
			if (serverTick % 10 == 0 && sum(who, Payload.BuffKind.IMMUNE_SLOW, null) > 0.0D) {
				who.removeEffect(MobEffects.SLOWNESS);
				if (who.getTicksFrozen() > 0) {
					who.setTicksFrozen(0);
				}
			}
			if (now >= state.penaltyUntil) {
				state.penaltyQiZero = false;
				state.penaltyDamage = 1.0D;
			}
			if (state.idle(now)) {
				it.remove();
			}
		}
	}

	/** Removes every "while the cast lasts" boon of {@code source} (toggle off, form end, cast end). */
	static void endCast(Caster source) {
		for (State state : STATES.values()) {
			state.hooks.removeIf(hook -> hook.source == source && hook.whileCast);
			state.guards.removeIf(guard -> guard.source == source && guard.whileCast);
			LivingEntity holder = TechniqueRunner.current(state.holder);
			for (Iterator<BuffEntry> it = state.buffs.iterator(); it.hasNext(); ) {
				BuffEntry buff = it.next();
				if (buff.source == source && buff.whileCast) {
					// taken off the state's own holder (an ally's stance buff comes off the ally, not the caster)
					if (holder != null) {
						removeModifier(holder, buff);
					}
					it.remove();
				}
			}
		}
	}

	/** Drops everything of a caster that left or died. */
	static void forget(LivingEntity who) {
		State state = STATES.remove(who.getUUID());
		if (state != null) {
			for (BuffEntry buff : state.buffs) {
				removeModifier(who, buff);
			}
		}
	}

	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (damageTaken <= 0.0F || STATES.isEmpty()) {
			return;
		}
		Entity attacker = source.getEntity();
		State victim = peek(entity);
		if (victim != null && attacker instanceof LivingEntity living && living != entity) {
			boolean melee = melee(source);
			if (melee) {
				double share = reflect(entity);
				if (share > 0.0D && !Caster.REFLECTING.get()) {
					for (BuffEntry buff : victim.buffs) {
						if (buff.kind == Payload.BuffKind.REFLECT && buff.source != null && buff.source.mayHit(living, true)) {
							Caster.REFLECTING.set(true);
							try {
								buff.source.reflectHurt(living, damageTaken * share);
							} finally {
								Caster.REFLECTING.set(false);
							}
							break;
						}
					}
				}
			}
			if (melee || source.getDirectEntity() != attacker) {
				fire(entity, living, Hook.Trigger.ON_HURT, damageTaken, source);
			}
		}
		if (attacker instanceof LivingEntity dealer && dealer != entity) {
			State state = peek(dealer);
			if (state != null) {
				double steal = sum(dealer, Payload.BuffKind.LIFESTEAL, null);
				if (steal > 0.0D) {
					dealer.heal((float) (damageTaken * Math.min(1.0D, steal)));
				}
				if (melee(source) && source.getDirectEntity() == dealer && !firing) {
					fire(dealer, entity, Hook.Trigger.ON_MELEE_HIT, damageTaken, source);
				}
			}
		}
	}

	private static void afterKill(ServerLevel level, Entity killer, LivingEntity killed, DamageSource source) {
		if (killer instanceof LivingEntity living && peek(living) != null && !firing) {
			fire(living, killed, Hook.Trigger.ON_KILL, 0.0F, source);
		}
	}

	private static boolean melee(DamageSource source) {
		return (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.PLAYER_ATTACK))
				&& source.getEntity() != null && source.getDirectEntity() == source.getEntity();
	}

	// ================================================================== helpers

	private static void modifier(LivingEntity who, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = who.getAttribute(attribute);
		if (instance != null) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	private static void remove(LivingEntity who, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = who.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}

	/** Counts for {@code debug teknik beban}: holders, hooks, guards. */
	static int[] counts() {
		int hooks = 0;
		int guards = 0;
		for (State state : STATES.values()) {
			hooks += state.hooks.size();
			guards += state.guards.size();
		}
		return new int[]{STATES.size(), hooks, guards};
	}

	private Boons() {
	}
}
