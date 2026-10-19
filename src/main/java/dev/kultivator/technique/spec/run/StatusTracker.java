package dev.kultivator.technique.spec.run;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Dao;
import dev.kultivator.technique.DaoTechniques;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Mark;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.util.NoGrief;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Lasting states technique payloads put on hostiles (DESIGN 22.3.2, 22.14.3, 22.15): DOT ticks (every 20 ticks with the
 * caster's snapshot P, the same spec refreshes, at most 4 per target), MARKs (vulnerable stacks, Dao vulnerability, wet,
 * soul, highlight), BINDs (transient attribute modifiers only, never {@code setNoAi}, released by schedule), the control
 * diminishing returns, ATTR modifiers, LINK groups, TAUNTs and continuous pulls. Everything lives in memory; the only
 * saved trace is the SUSPEND tag, which is cleared on load. Zero cost while empty.
 */
public final class StatusTracker {

	public static final String SUSPENDED_TAG = "kultivator.suspended";
	public static final int DOT_INTERVAL = 20;
	private static final int CONTROL_WINDOW = 200;
	private static final int CONTROL_IMMUNITY = 120;
	private static final int BOSS_CONTROL_IMMUNITY = 200;
	private static final double SUSPEND_LIFT = 1.5D;

	private static final Identifier BIND_MOVE = Kultivator.id("bind_move");
	private static final Identifier BIND_FLY = Kultivator.id("bind_fly");
	private static final Identifier BIND_JUMP = Kultivator.id("bind_jump");
	private static final Identifier STUN = Kultivator.id("stun");

	private static final class Dot {
		final Caster source;
		final String spec;
		final double perSecond;
		int secondsLeft;
		final Payload.DotKind element;
		final boolean pierce;
		final Map<Payload.Versus, Double> versus;

		Dot(Caster source, double perSecond, int seconds, Payload.Dot dot) {
			this.source = source;
			this.spec = source.spec().id();
			this.perSecond = perSecond;
			this.secondsLeft = seconds;
			this.element = dot.element();
			this.pierce = dot.piercing();
			this.versus = dot.versus();
		}
	}

	private static final class MarkEntry {
		final UUID caster;
		final Mark mark;
		int stacks;
		long until;

		MarkEntry(UUID caster, Mark mark, long until) {
			this.caster = caster;
			this.mark = mark;
			this.stacks = 1;
			this.until = until;
		}
	}

	private static final class Bind {
		final Payload.BindKind kind;
		long until;
		final boolean lifted;
		int age;
		/** What the clients draw (DESIGN 27.4, {@code Payloads.BindSync}). */
		BindKind look = BindKind.CHAINS;
		@Nullable LivingEntity binder;
		int spec = -1;

		Bind(Payload.BindKind kind, long until, boolean lifted) {
			this.kind = kind;
			this.until = until;
			this.lifted = lifted;
		}
	}

	/** Tells the clients around {@code target} what its bind looks like ({@code ticks} 0 = it ended). */
	private static void syncBind(LivingEntity target, Bind bind, int ticks) {
		VisualSync.bind(target, bind.look, ticks, bind.binder, bind.spec);
	}

	private static final class Control {
		final List<Long> times = new ArrayList<>();
		long immuneUntil;
	}

	private static final class AttrEntry {
		final Holder<Attribute> attribute;
		final Identifier id;
		long until;

		AttrEntry(Holder<Attribute> attribute, Identifier id, long until) {
			this.attribute = attribute;
			this.id = id;
			this.until = until;
		}
	}

	private static final class LinkGroup {
		final Caster source;
		final Set<LivingEntity> members = new HashSet<>();
		final double share;
		final double bossShare;
		final long until;

		LinkGroup(Caster source, double share, double bossShare, long until) {
			this.source = source;
			this.share = share;
			this.bossShare = bossShare;
			this.until = until;
		}
	}

	private record Taunt(LivingEntity caster, long until) {
	}

	private static final class Pull {
		final Supplier<Vec3> centre;
		final double perTick;
		long until;
		int age;

		Pull(Supplier<Vec3> centre, double perTick, long until) {
			this.centre = centre;
			this.perTick = perTick;
			this.until = until;
		}
	}

	private static final Map<LivingEntity, List<Dot>> DOTS = new HashMap<>();
	private static final Map<LivingEntity, List<MarkEntry>> MARKS = new HashMap<>();
	private static final Map<LivingEntity, Bind> BINDS = new HashMap<>();
	private static final Map<LivingEntity, Control> CONTROLS = new HashMap<>();
	private static final Map<LivingEntity, List<AttrEntry>> ATTRS = new HashMap<>();
	private static final Map<LivingEntity, LinkGroup> LINKS = new HashMap<>();
	private static final Map<LivingEntity, Taunt> TAUNTS = new HashMap<>();
	private static final Map<LivingEntity, Pull> PULLS = new HashMap<>();
	private static int entries;
	private static boolean linking;

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(StatusTracker::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> forget(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living) {
				release(living);
				forget(living);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(SUSPENDED_TAG)) {
				// A crash or a save while suspended: never leave a mob floating.
				entity.removeTag(SUSPENDED_TAG);
				entity.setNoGravity(false);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (LivingEntity entity : List.copyOf(BINDS.keySet())) {
				release(entity);
			}
			clearAll();
		});
	}

	static void clearAll() {
		DOTS.clear();
		MARKS.clear();
		BINDS.clear();
		CONTROLS.clear();
		ATTRS.clear();
		LINKS.clear();
		TAUNTS.clear();
		PULLS.clear();
		entries = 0;
	}

	static boolean idle() {
		return DOTS.isEmpty() && MARKS.isEmpty() && BINDS.isEmpty() && ATTRS.isEmpty() && LINKS.isEmpty() && TAUNTS.isEmpty()
				&& PULLS.isEmpty() && CONTROLS.isEmpty();
	}

	// ================================================================== DOT

	/** A DOT of {@code perSecond} × P for {@code seconds}; the same spec refreshes; at most 4 per target, 512 server-wide. */
	static void addDot(Caster source, LivingEntity target, Payload.Dot dot, double perSecond, int seconds) {
		if (seconds <= 0 || !(perSecond > 0.0D)) {
			return;
		}
		List<Dot> list = DOTS.computeIfAbsent(target, key -> new ArrayList<>());
		for (Dot each : list) {
			if (each.spec.equals(source.spec().id()) && each.source.caster() == source.caster()) {
				each.secondsLeft = Math.max(each.secondsLeft, seconds);
				return;
			}
		}
		if (list.size() >= TechniquePower.TRACKERS_PER_TARGET || entries >= TechniquePower.TRACKERS_SERVER) {
			return;
		}
		list.add(new Dot(source, perSecond, seconds, dot));
		entries++;
		if (dot.element() == Payload.DotKind.FIRE) {
			DaoTechniques.ignite(target, Math.min(seconds, 8));
		}
	}

	/** Drops every DOT and link of a caster that left (they need its choke point). */
	static void forgetSource(LivingEntity caster) {
		for (Iterator<Map.Entry<LivingEntity, List<Dot>>> it = DOTS.entrySet().iterator(); it.hasNext(); ) {
			List<Dot> list = it.next().getValue();
			int before = list.size();
			list.removeIf(dot -> dot.source.caster() == caster);
			entries -= before - list.size();
			if (list.isEmpty()) {
				it.remove();
			}
		}
		LINKS.values().removeIf(group -> group.source.caster() == caster);
		TAUNTS.values().removeIf(taunt -> taunt.caster() == caster);
	}

	// ================================================================== MARK

	static void mark(Caster source, LivingEntity target, Mark mark, int ticks) {
		long until = target.level().getGameTime() + Math.max(1, ticks);
		List<MarkEntry> list = MARKS.computeIfAbsent(target, key -> new ArrayList<>());
		UUID caster = source.caster().getUUID();
		for (MarkEntry each : list) {
			if (each.caster.equals(caster) && each.mark.kind() == mark.kind() && each.mark.dao() == mark.dao()) {
				each.until = Math.max(each.until, until);
				if (mark.kind() == Mark.Kind.VULNERABLE) {
					each.stacks = Math.min(Math.max(1, mark.maxStacks()), each.stacks + 1);
				}
				return;
			}
		}
		if (list.size() >= TechniquePower.TRACKERS_PER_TARGET * 2 || entries >= TechniquePower.TRACKERS_SERVER) {
			return;
		}
		list.add(new MarkEntry(caster, mark, until));
		entries++;
	}

	/**
	 * The damage factor of the marks {@code caster} put on {@code target} for a spec of {@code dao}: VULNERABLE
	 * (1 + x × stacks), DAO_VULN (1 + x for that Dao), SOUL (× k, consumed when {@code consume}). Bosses get the mark's
	 * boss share.
	 */
	static double markFactor(LivingEntity caster, LivingEntity target, @Nullable Dao dao, int tier, boolean consume) {
		List<MarkEntry> list = MARKS.get(target);
		if (list == null) {
			return 1.0D;
		}
		long now = target.level().getGameTime();
		boolean boss = TechniquePower.isBoss(target);
		double factor = 1.0D;
		for (Iterator<MarkEntry> it = list.iterator(); it.hasNext(); ) {
			MarkEntry entry = it.next();
			if (now >= entry.until) {
				it.remove();
				entries--;
				continue;
			}
			if (!entry.caster.equals(caster.getUUID())) {
				continue;
			}
			Mark mark = entry.mark;
			double value = Math.min(mark.cap() > 0.0D ? Math.max(mark.cap(), mark.value()) : mark.value(), mark.value() + mark.perTier() * tier);
			if (boss) {
				value *= mark.bossFactor();
			}
			switch (mark.kind()) {
				case VULNERABLE -> factor *= 1.0D + value * entry.stacks;
				case DAO_VULN -> {
					if (mark.dao() != null && mark.dao() == dao) {
						factor *= 1.0D + value;
					}
				}
				case SOUL -> {
					factor *= Math.max(0.0D, value);
					if (consume) {
						it.remove();
						entries--;
					}
				}
				default -> {
				}
			}
		}
		if (list.isEmpty()) {
			MARKS.remove(target);
		}
		return factor;
	}

	/** True when any caster marked {@code target} WET (it also counts as wet in water or rain, see TechniquePower). */
	static boolean wet(LivingEntity target) {
		List<MarkEntry> list = MARKS.get(target);
		if (list == null) {
			return false;
		}
		long now = target.level().getGameTime();
		for (MarkEntry entry : list) {
			if (entry.mark.kind() == Mark.Kind.WET && now < entry.until) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== control (BIND, FRZ)

	/**
	 * Diminishing returns of control on one target (22.14.3): a second control within 10 s lasts ×0.5, a third ×0.25,
	 * then 6 s of immunity; bosses ×0.25, then 10 s of immunity. Returns the factor to apply (0 = immune) and records it.
	 */
	static double controlScale(LivingEntity target) {
		long now = target.level().getGameTime();
		Control control = CONTROLS.computeIfAbsent(target, key -> new Control());
		if (now < control.immuneUntil) {
			return 0.0D;
		}
		control.times.removeIf(time -> now - time > CONTROL_WINDOW);
		if (TechniquePower.isBoss(target)) {
			control.immuneUntil = now + BOSS_CONTROL_IMMUNITY;
			control.times.clear();
			return 0.25D;
		}
		int count = control.times.size();
		control.times.add(now);
		if (count >= 2) {
			control.immuneUntil = now + CONTROL_IMMUNITY;
			control.times.clear();
			return 0.25D;
		}
		return count == 1 ? 0.5D : 1.0D;
	}

	/**
	 * BIND {@code kind} for {@code ticks} (22.3.2): transient MOVEMENT_SPEED / FLYING_SPEED −100%, jump 0, navigation stopped
	 * once, a creeper's fuse held; STUN also attack ×0; SUSPEND lifts 1.5 blocks without gravity; SLEEP breaks on damage.
	 * Bosses: {@code bossTicks} (or ×0.25 through the diminishing returns) and only ROOT; the Warden, Wither and Dragon are
	 * never suspended and the Dragon is never bound. True when it was applied. {@code source} is null for a sourceless
	 * bind ({@link ExternalControl}, phase 7c formations); a sourceless SUSPEND becomes a ROOT.
	 */
	static boolean bind(@Nullable Caster source, LivingEntity target, Payload.BindKind kind, int ticks, int bossTicks) {
		if (target instanceof EnderDragon || target instanceof Player || ticks <= 0) {
			return false;
		}
		boolean boss = TechniquePower.isBoss(target);
		double scale = controlScale(target);
		if (scale <= 0.0D) {
			return false;
		}
		int duration = boss && bossTicks >= 0 ? bossTicks : (int) Math.round(ticks * scale);
		if (duration <= 0) {
			return false;
		}
		Payload.BindKind actual = kind;
		if (boss) {
			actual = Payload.BindKind.ROOT;
		}
		if (actual == Payload.BindKind.SUSPEND && (target instanceof Warden || target instanceof WitherBoss || source == null
				|| !source.moveAllowed(target))) {
			actual = Payload.BindKind.ROOT;
		}
		long now = target.level().getGameTime();
		Bind existing = BINDS.get(target);
		if (existing != null) {
			if (now + duration > existing.until) {
				existing.until = now + duration;
				syncBind(target, existing, duration);
			}
			return true;
		}
		if (BINDS.size() >= TechniquePower.BINDS_SERVER) {
			return false;
		}
		boolean lifted = false;
		if (actual == Payload.BindKind.SUSPEND) {
			AABB raised = target.getBoundingBox().move(0.0D, SUSPEND_LIFT, 0.0D);
			if (target.level().noCollision(target, raised)) {
				target.setNoGravity(true);
				target.addTag(SUSPENDED_TAG);
				target.teleportTo(target.getX(), target.getY() + SUSPEND_LIFT, target.getZ());
				lifted = true;
			} else {
				actual = Payload.BindKind.ROOT;
			}
		}
		modifier(target, Attributes.MOVEMENT_SPEED, BIND_MOVE, -1.0D);
		modifier(target, Attributes.FLYING_SPEED, BIND_FLY, -1.0D);
		modifier(target, Attributes.JUMP_STRENGTH, BIND_JUMP, -1.0D);
		if (actual == Payload.BindKind.STUN || actual == Payload.BindKind.SLEEP) {
			modifier(target, Attributes.ATTACK_DAMAGE, STUN, -1.0D);
		}
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (target instanceof Creeper creeper) {
			creeper.setSwellDir(-1);
		}
		stop(target);
		target.addTag(NoGrief.TAG);
		target.setPortalCooldown();
		Bind made = new Bind(actual, now + duration, lifted);
		BindKind authored = source == null ? null : source.spec.bindLook();
		made.look = authored != null ? authored : BindKind.of(actual);
		made.binder = source == null ? null : source.caster;
		made.spec = source == null ? -1 : source.spec.netId();
		BINDS.put(target, made);
		syncBind(target, made, duration);
		return true;
	}

	static boolean bound(LivingEntity target) {
		return BINDS.containsKey(target);
	}

	/** Frozen look and a slow (FRZ, through the control diminishing returns). */
	static boolean freeze(LivingEntity target, int ticks, LivingEntity caster) {
		if (ticks <= 0 || !target.isAlive()) {
			return false;
		}
		double scale = controlScale(target);
		int duration = (int) Math.round(ticks * scale);
		if (duration <= 0) {
			return false;
		}
		if (target.canFreeze()) {
			target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + duration));
		}
		target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS,
				Math.max(20, duration / 2), 2), caster);
		return true;
	}

	/** Ends a freeze early (an Ice "shatter" hit). */
	static void unfreeze(LivingEntity target) {
		target.setTicksFrozen(0);
	}

	private static void releaseBind(LivingEntity target, Bind bind) {
		remove(target, Attributes.MOVEMENT_SPEED, BIND_MOVE);
		remove(target, Attributes.FLYING_SPEED, BIND_FLY);
		remove(target, Attributes.JUMP_STRENGTH, BIND_JUMP);
		remove(target, Attributes.ATTACK_DAMAGE, STUN);
		if (bind.lifted || target.entityTags().contains(SUSPENDED_TAG)) {
			target.setNoGravity(false);
			target.removeTag(SUSPENDED_TAG);
		}
		target.needsSync = true;
		target.syncVelocity = true;
	}

	/** Ends only the bind on {@code target} (other technique effects stay); used by {@link ExternalControl#release}. */
	static void unbind(LivingEntity target) {
		Bind bind = BINDS.remove(target);
		if (bind != null) {
			releaseBind(target, bind);
			syncBind(target, bind, 0);
		}
	}

	/** Releases a bound entity at once (unload, server stop). */
	static void release(LivingEntity target) {
		Bind bind = BINDS.remove(target);
		if (bind != null) {
			releaseBind(target, bind);
		}
		List<AttrEntry> attrs = ATTRS.remove(target);
		if (attrs != null) {
			for (AttrEntry entry : attrs) {
				remove(target, entry.attribute, entry.id);
			}
		}
	}

	// ================================================================== ATTR

	/** A transient attribute change for {@code ticks}: flat, or a share of the total when {@code percent}. */
	static void attr(LivingEntity target, Payload.AttrKind kind, double amount, boolean percent, int ticks) {
		if (ticks <= 0 || amount == 0.0D || target instanceof Player) {
			return;
		}
		Identifier id = Kultivator.id("tattr_" + kind.name().toLowerCase(java.util.Locale.ROOT));
		AttributeInstance instance = target.getAttribute(kind.holder());
		if (instance == null) {
			return;
		}
		instance.addOrUpdateTransientModifier(new AttributeModifier(id, percent ? Math.max(-1.0D, amount) : amount,
				percent ? AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL : AttributeModifier.Operation.ADD_VALUE));
		long until = target.level().getGameTime() + ticks;
		List<AttrEntry> list = ATTRS.computeIfAbsent(target, key -> new ArrayList<>());
		for (AttrEntry entry : list) {
			if (entry.id.equals(id)) {
				entry.until = Math.max(entry.until, until);
				return;
			}
		}
		list.add(new AttrEntry(kind.holder(), id, until));
	}

	// ================================================================== LINK, TAUNT, PULL

	/** Links {@code targets}: damage the caster deals to one is also dealt to the others at {@code share} (bosses {@code bossShare}). */
	static void link(Caster source, List<LivingEntity> targets, double share, int ticks, double bossShare) {
		if (targets.size() < 2 || ticks <= 0 || !(share > 0.0D)) {
			return;
		}
		LinkGroup group = new LinkGroup(source, share, bossShare, targets.get(0).level().getGameTime() + ticks);
		for (LivingEntity target : targets) {
			if (group.members.size() >= TechniquePower.MAX_TARGETS) {
				break;
			}
			group.members.add(target);
			LINKS.put(target, group);
			// the karmic threads every client draws between the linker and each linked foe (DESIGN 27.4)
			VisualSync.bind(target, BindKind.KARMA_LINK, ticks, source.caster, source.spec.netId());
		}
	}

	/** The target attacks {@code caster} for {@code ticks} (target and brain memory, refreshed every second). */
	static void taunt(LivingEntity target, LivingEntity caster, int ticks) {
		if (!(target instanceof Mob mob) || ticks <= 0) {
			return;
		}
		aim(mob, caster);
		TAUNTS.put(target, new Taunt(caster, target.level().getGameTime() + ticks));
	}

	/** Forgets the target: no attack target and no brain attack memory (the Warden's anger list stays vanilla). */
	static void untarget(LivingEntity target) {
		TAUNTS.remove(target);
		if (target instanceof Mob mob) {
			mob.setTarget(null);
			mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		}
	}

	private static void aim(Mob mob, LivingEntity caster) {
		mob.setTarget(caster);
		mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, caster);
	}

	/** A continuous pull of {@code perTick} blocks per tick toward {@code centre} for {@code ticks} (movement rules checked by the caller). */
	static void pull(LivingEntity target, Supplier<Vec3> centre, double perTick, int ticks) {
		if (ticks <= 0 || !(perTick > 0.0D)) {
			return;
		}
		PULLS.put(target, new Pull(centre, perTick, target.level().getGameTime() + ticks));
	}

	// ================================================================== tick and events

	static void tick(long serverTick) {
		if (!BINDS.isEmpty()) {
			tickBinds();
		}
		if (!PULLS.isEmpty()) {
			tickPulls();
		}
		if (serverTick % DOT_INTERVAL == 0) {
			if (!DOTS.isEmpty()) {
				tickDots();
			}
			sweep();
		}
	}

	private static void tickBinds() {
		for (Iterator<Map.Entry<LivingEntity, Bind>> it = BINDS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<LivingEntity, Bind> entry = it.next();
			LivingEntity target = entry.getKey();
			Bind bind = entry.getValue();
			bind.age++;
			if (!target.isAlive() || target.isRemoved() || target.level().getGameTime() >= bind.until) {
				it.remove();
				if (!target.isRemoved()) {
					releaseBind(target, bind);
				}
				syncBind(target, bind, 0);
				continue;
			}
			if (target instanceof Creeper creeper) {
				creeper.setSwellDir(-1);
			}
			Vec3 velocity = target.getDeltaMovement();
			boolean moving = Math.abs(velocity.x) > 0.01D || Math.abs(velocity.z) > 0.01D || bind.lifted && Math.abs(velocity.y) > 0.01D;
			if (moving) {
				target.setDeltaMovement(0.0D, bind.lifted ? 0.0D : Math.min(0.0D, velocity.y), 0.0D);
				if (bind.age % 4 == 1) {
					target.needsSync = true;
					target.syncVelocity = true;
				}
			}
		}
	}

	private static void tickPulls() {
		for (Iterator<Map.Entry<LivingEntity, Pull>> it = PULLS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<LivingEntity, Pull> entry = it.next();
			LivingEntity target = entry.getKey();
			Pull pull = entry.getValue();
			pull.age++;
			if (!target.isAlive() || target.isRemoved() || target.level().getGameTime() >= pull.until) {
				it.remove();
				syncMotion(target);
				continue;
			}
			Vec3 centre = pull.centre.get();
			if (centre == null) {
				it.remove();
				syncMotion(target);
				continue;
			}
			Vec3 to = centre.subtract(target.position()).horizontal();
			double distance = to.length();
			if (distance <= 1.0D) {
				continue;
			}
			Vec3 move = to.scale(Math.min(pull.perTick, distance - 0.9D) / distance);
			target.setDeltaMovement(move.x, Math.min(0.0D, target.getDeltaMovement().y), move.z);
			// the server moves it every tick; viewers get one motion packet per 4 ticks (as binds do) and one on release
			if (pull.age % 4 == 1) {
				syncMotion(target);
			}
		}
	}

	private static void syncMotion(LivingEntity target) {
		if (!target.isRemoved()) {
			target.needsSync = true;
			target.syncVelocity = true;
		}
	}

	/**
	 * One DOT pass over snapshots: a tick can kill its target (AFTER_DEATH forgets it inside the loop), share through a
	 * LINK or fire a hook that adds a DOT, so the maps are never iterated live and a removal counts only once.
	 */
	private static void tickDots() {
		for (LivingEntity target : List.copyOf(DOTS.keySet())) {
			List<Dot> list = DOTS.get(target);
			if (list == null) {
				continue;
			}
			if (!target.isAlive() || target.isRemoved()) {
				DOTS.remove(target);
				entries = Math.max(0, entries - list.size());
				continue;
			}
			for (Dot dot : List.copyOf(list)) {
				if (DOTS.get(target) != list) {
					// forgotten during this pass (died, unloaded): forget() already counted its entries
					break;
				}
				if (!list.contains(dot)) {
					continue;
				}
				if (!dot.source.casterValid()) {
					removeDot(list, dot);
					continue;
				}
				if (dot.element == Payload.DotKind.FIRE && !target.isOnFire()) {
					DaoTechniques.ignite(target, 2.0F);
				}
				dot.source.dotHurt(target, dot.perSecond, dot.pierce, dot.versus);
				if (dot.element == Payload.DotKind.FIRE && target.isAlive() && (target.fireImmune() || !target.isOnFire())) {
					// fire-immune or doused: vanilla burning shows nothing, so the burn draws its own spark
					Vec3 middle = target.getBoundingBox().getCenter();
					dot.source.fx(Phase.HIT, middle, middle, target.getBbWidth(), 0.0F, 1, 0);
				}
				if (--dot.secondsLeft <= 0) {
					removeDot(list, dot);
				}
			}
			if (list.isEmpty() && DOTS.get(target) == list) {
				DOTS.remove(target);
			}
		}
	}

	private static void removeDot(List<Dot> list, Dot dot) {
		if (list.remove(dot)) {
			entries = Math.max(0, entries - 1);
		}
	}

	/** Expired marks, attrs, links and taunts (once per second). */
	private static void sweep() {
		for (Iterator<Map.Entry<LivingEntity, List<MarkEntry>>> it = MARKS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<LivingEntity, List<MarkEntry>> entry = it.next();
			LivingEntity target = entry.getKey();
			long now = target.level().getGameTime();
			List<MarkEntry> list = entry.getValue();
			int before = list.size();
			list.removeIf(mark -> now >= mark.until || !target.isAlive());
			entries -= before - list.size();
			if (list.isEmpty()) {
				it.remove();
			}
		}
		for (Iterator<Map.Entry<LivingEntity, List<AttrEntry>>> it = ATTRS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<LivingEntity, List<AttrEntry>> entry = it.next();
			LivingEntity target = entry.getKey();
			long now = target.level().getGameTime();
			entry.getValue().removeIf(attr -> {
				if (now >= attr.until || !target.isAlive()) {
					remove(target, attr.attribute, attr.id);
					return true;
				}
				return false;
			});
			if (entry.getValue().isEmpty()) {
				it.remove();
			}
		}
		LINKS.entrySet().removeIf(entry -> !entry.getKey().isAlive() || entry.getKey().level().getGameTime() >= entry.getValue().until);
		for (Iterator<Map.Entry<LivingEntity, Taunt>> it = TAUNTS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<LivingEntity, Taunt> entry = it.next();
			LivingEntity target = entry.getKey();
			Taunt taunt = entry.getValue();
			if (!target.isAlive() || !taunt.caster().isAlive() || target.level().getGameTime() >= taunt.until()) {
				it.remove();
			} else if (target instanceof Mob mob && mob.getTarget() != taunt.caster()) {
				aim(mob, taunt.caster());
			}
		}
		CONTROLS.entrySet().removeIf(entry -> {
			LivingEntity target = entry.getKey();
			long now = target.level().getGameTime();
			Control control = entry.getValue();
			return !target.isAlive() || now >= control.immuneUntil && (control.times.isEmpty() || now - control.times.get(control.times.size() - 1) > CONTROL_WINDOW);
		});
	}

	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (damageTaken <= 0.0F) {
			return;
		}
		Bind bind = BINDS.get(entity);
		if (bind != null && bind.kind == Payload.BindKind.SLEEP) {
			BINDS.remove(entity);
			releaseBind(entity, bind);
		}
		LinkGroup group = LINKS.get(entity);
		if (group == null || linking || source.getEntity() != group.source.caster()) {
			return;
		}
		linking = true;
		try {
			for (LivingEntity other : List.copyOf(group.members)) {
				if (other == entity || !other.isAlive()) {
					continue;
				}
				double share = TechniquePower.isBoss(other) ? group.bossShare : group.share;
				group.source.linkHurt(other, damageTaken * share);
			}
		} finally {
			linking = false;
		}
	}

	private static void forget(LivingEntity entity) {
		List<Dot> dots = DOTS.remove(entity);
		if (dots != null) {
			entries = Math.max(0, entries - dots.size());
		}
		List<MarkEntry> marks = MARKS.remove(entity);
		if (marks != null) {
			entries = Math.max(0, entries - marks.size());
		}
		BINDS.remove(entity);
		CONTROLS.remove(entity);
		ATTRS.remove(entity);
		LinkGroup group = LINKS.remove(entity);
		if (group != null) {
			group.members.remove(entity);
		}
		TAUNTS.remove(entity);
		PULLS.remove(entity);
	}

	// ================================================================== helpers

	private static void modifier(LivingEntity target, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = target.getAttribute(attribute);
		if (instance != null) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void remove(LivingEntity target, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = target.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}

	private static void stop(Entity target) {
		Vec3 velocity = target.getDeltaMovement();
		if (velocity.lengthSqr() > 1.0E-4D) {
			target.setDeltaMovement(0.0D, Math.min(0.0D, velocity.y), 0.0D);
		}
		target.needsSync = true;
		target.syncVelocity = true;
	}

	/** Counts for {@code debug teknik beban}: dots+marks, binds, links. */
	static int[] counts() {
		return new int[]{entries, BINDS.size(), LINKS.size(), PULLS.size()};
	}

	private StatusTracker() {
	}
}
