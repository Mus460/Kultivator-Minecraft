package dev.kultivator.duel.clash;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.duel.Duel;
import dev.kultivator.duel.DuelConfig;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.ai.DuelistProfile;
import dev.kultivator.duel.ai.DuelistRegistry;
import dev.kultivator.duel.net.DuelNet;
import dev.kultivator.duel.net.DuelPayloads;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.duel.voice.DuelVoices;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every {@link Fighter} of the server (DESIGN 35.3), the one entry for inputs (players through the network, AI directly,
 * C35.S2), the per-tick clock work (swings coming due, heavy charges, the blade lock, stagger, posture recovery, the spam
 * lock, the exchange chain) and the payloads of the clash layer. Session only, server thread only.
 */
public final class Fighters {

	/** −100% movement: INTRO, COUNTDOWN, FINISHER and the blade lock. */
	public static final Identifier HOLD_ID = Kultivator.id("duel_hold");
	/** −30% movement while guarding. */
	public static final Identifier GUARD_ID = Kultivator.id("duel_guard");
	/** −90% movement while staggered. */
	public static final Identifier STAGGER_ID = Kultivator.id("duel_stagger");

	/** Fighters forgotten after this long without anything happening (unless in a duel). */
	private static final int IDLE_FORGET = 600;
	private static final int MAX_FIGHTERS = 256;
	/** At most this many inputs per second from one client (more are dropped, logged once a minute). */
	public static final int MAX_INPUTS_PER_SECOND = 24;
	/** Fighter syncs at most every 2 ticks (10 Hz) while changing, and every 40 ticks while in a duel context. */
	private static final int SYNC_MIN_GAP = 2;
	private static final int SYNC_KEEPALIVE = 40;
	private static final double SESSION_VIEW = 64.0D;
	private static final double MOB_VIEW = 32.0D;

	private static final Map<UUID, Fighter> FIGHTERS = new HashMap<>();
	private static final Set<Fighter> DIRTY = new LinkedHashSet<>();

	// ================================================================ registry

	/** The fighter of {@code entity}, created when missing (C35.S2). */
	public static Fighter get(LivingEntity entity) {
		Fighter fighter = FIGHTERS.get(entity.getUUID());
		if (fighter != null && fighter.entity == entity) {
			return fighter;
		}
		if (FIGHTERS.size() >= MAX_FIGHTERS) {
			evictOne();
		}
		fighter = new Fighter(entity);
		fighter.postureMax = Posture.max(entity);
		fighter.weapon = WeaponClass.of(entity);
		fighter.lastActive = ClashClock.now();
		if (entity instanceof ServerPlayer player) {
			DuelPayloads.DuelPrefs prefs = DuelNet.prefs(player.getUUID());
			if (prefs != null) {
				applyPrefs(fighter, prefs);
			}
		}
		FIGHTERS.put(entity.getUUID(), fighter);
		refreshContext(fighter);
		return fighter;
	}

	/** The fighter of {@code entity}, or null when it has none. */
	public static @Nullable Fighter peek(@Nullable Entity entity) {
		if (entity == null || FIGHTERS.isEmpty()) {
			return null;
		}
		Fighter fighter = FIGHTERS.get(entity.getUUID());
		return fighter != null && fighter.entity == entity ? fighter : null;
	}

	/** True when {@code entity} shows and uses a posture bar: duel contexts and the posture mobs. */
	public static boolean postureOf(LivingEntity entity) {
		Fighter fighter = peek(entity);
		if (fighter != null && fighter.context != DuelIds.CONTEXT_NONE && fighter.context != DuelIds.CONTEXT_UMUM) {
			return true;
		}
		return !(entity instanceof ServerPlayer) && DuelConfig.get().mobPosture && MobGuard.hasPosture(entity);
	}

	private static void evictOne() {
		Fighter oldest = null;
		for (Fighter fighter : FIGHTERS.values()) {
			if (fighter.session == null && (oldest == null || fighter.lastActive < oldest.lastActive)) {
				oldest = fighter;
			}
		}
		if (oldest != null) {
			forget(oldest);
		}
	}

	private static void forget(Fighter fighter) {
		FIGHTERS.remove(fighter.uuid, fighter);
		DIRTY.remove(fighter);
		if (fighter.bind != null) {
			Bind.abort(fighter.bind, ClashClock.now());
		}
		removeModifiers(fighter.entity);
	}

	/** Every fighter (copy). */
	public static List<Fighter> all() {
		return new ArrayList<>(FIGHTERS.values());
	}

	// ================================================================ contexts and rules

	/** Recomputes where the clash layer runs for {@code fighter} and against whom. */
	static void refreshContext(Fighter fighter) {
		LivingEntity entity = fighter.entity;
		DuelSession session = DuelSessions.byEntity(entity);
		fighter.session = session;
		if (session != null) {
			fighter.partner = session.partnerOf(entity);
			fighter.context = DuelIds.CONTEXT_RESMI;
			return;
		}
		LivingEntity partner = DuelRules.partnerOf(entity);
		if (partner != null) {
			fighter.partner = partner;
			boolean guru = entity instanceof DiscipleEntity disciple && disciple.isOwnedBy(partner)
					|| partner instanceof DiscipleEntity other && other.isOwnedBy(entity);
			fighter.context = guru ? DuelIds.CONTEXT_GURU : DuelIds.CONTEXT_TURNAMEN;
			return;
		}
		if (fighter.partner instanceof ServerPlayer other && entity instanceof ServerPlayer self && dev.kultivator.sect.Sparring.areSparring(self, other)) {
			fighter.context = DuelIds.CONTEXT_LATIH;
			return;
		}
		fighter.partner = null;
		fighter.context = DuelIds.CONTEXT_UMUM;
	}

	/** Two sparring players found each other through a blow (Sparring has no partner lookup): remember the pair. */
	public static void noteSparring(ServerPlayer a, ServerPlayer b) {
		Fighter fa = get(a);
		Fighter fb = get(b);
		fa.partner = b;
		fb.partner = a;
		fa.context = DuelIds.CONTEXT_LATIH;
		fb.context = DuelIds.CONTEXT_LATIH;
	}

	/** The rules of the fighter's official duel (null outside one: the general rules apply). */
	public static @Nullable DuelRuleSet rules(Fighter fighter) {
		DuelSession session = fighter.session;
		return session != null ? session.rules() : null;
	}

	/** {@code target} when it is still a valid opponent of {@code fighter}, else null. */
	static @Nullable LivingEntity validTarget(Fighter fighter, @Nullable LivingEntity target) {
		if (target == null || !target.isAlive() || target.isRemoved() || target.level() != fighter.entity.level()) {
			return null;
		}
		return target;
	}

	/** The partner, or the nearest foe in front within {@code range} (the general layer). */
	static @Nullable LivingEntity findTarget(Fighter fighter, double range) {
		LivingEntity self = fighter.entity;
		if (fighter.partner != null && fighter.partner.isAlive() && fighter.partner.level() == self.level()) {
			return fighter.partner;
		}
		if (self instanceof Mob mob && mob.getTarget() != null) {
			return mob.getTarget();
		}
		LivingEntity best = null;
		double bestDistance = range * range;
		AABB box = self.getBoundingBox().inflate(range);
		for (LivingEntity other : self.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != self && e.isAlive())) {
			if (!(other instanceof Enemy) || HitTest.angleTo(self, other.position()) > 70.0D) {
				continue;
			}
			double distance = self.distanceToSqr(other);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = other;
			}
		}
		return best;
	}

	// ================================================================ session hooks (module S session code)

	/** {@code entity} enters {@code session} (any phase) against {@code partner}: fresh stats, the companion sword. */
	public static Fighter enterSession(LivingEntity entity, DuelSession session, LivingEntity partner) {
		Fighter fighter = get(entity);
		fighter.session = session;
		fighter.partner = partner;
		fighter.context = DuelIds.CONTEXT_RESMI;
		fighter.resetCombat();
		fighter.resetStats();
		fighter.postureMax = Posture.max(entity);
		fighter.posture = 0.0F;
		fighter.weapon = WeaponClass.of(entity);
		fighter.companion = CompanionSword.has(entity);
		fighter.companionReadyAt = 0L;
		fighter.companionStrikeReadyAt = 0L;
		markDirty(fighter);
		return fighter;
	}

	/** {@code entity} leaves its session: every duel modifier is removed, the AI brain dropped. */
	public static void leaveSession(LivingEntity entity) {
		Fighter fighter = peek(entity);
		if (fighter == null) {
			removeModifiers(entity);
			return;
		}
		if (fighter.bind != null) {
			Bind.abort(fighter.bind, ClashClock.now());
		}
		fighter.resetCombat();
		fighter.session = null;
		fighter.brain = null;
		fighter.companion = false;
		fighter.held = false;
		refreshContext(fighter);
		removeModifiers(entity);
		markDirty(fighter);
	}

	/** Holds or frees {@code entity} (INTRO, COUNTDOWN, FINISHER). */
	public static void setHeld(LivingEntity entity, boolean held) {
		Fighter fighter = get(entity);
		if (fighter.held != held) {
			fighter.held = held;
			if (held) {
				Exchange.interrupt(fighter, ClashClock.now());
				fighter.guarding = false;
			}
			applyHold(fighter, held);
			markDirty(fighter);
		}
	}

	/** Sets the AI brain of a non-player duelist. */
	public static void setBrain(LivingEntity entity, @Nullable dev.kultivator.duel.ai.DuelistBrain brain) {
		get(entity).brain = brain;
	}

	// ================================================================ inputs

	/** A player's network input (seq, rate and phase already checked by {@link DuelNet}). */
	public static void onNetworkInput(ServerPlayer player, DuelPayloads.DuelInput input) {
		Fighter fighter = get(player);
		long now = ClashClock.now();
		// Sequence: rising mod 65536, duplicates and old ones dropped.
		if (fighter.lastSeq >= 0) {
			int diff = (input.seq() - fighter.lastSeq) & 0xFFFF;
			if (diff == 0 || diff > 0x7FFF) {
				return;
			}
		}
		fighter.lastSeq = input.seq();
		// Rate: at most 24 per second.
		if (now - fighter.rateWindowStart >= 20) {
			fighter.rateWindowStart = now;
			fighter.rateCount = 0;
		}
		if (++fighter.rateCount > MAX_INPUTS_PER_SECOND) {
			if (now - fighter.lastRateLog > 1200) {
				fighter.lastRateLog = now;
				Kultivator.LOGGER.info("Duel: dropping inputs of {} (more than {} per second)", player.getName().getString(), MAX_INPUTS_PER_SECOND);
			}
			return;
		}
		fighter.latency = player.connection.latency();
		fighter.comp = ClashClock.compensation(fighter.latency, DuelConfig.get().maxRewindTicks);
		input(player, input.kind(), input.dir(), input.flags(), input.arg(), now);
	}

	/**
	 * One input of {@code entity} arriving at server tick {@code tick} (C35.S2): players through the network (their
	 * latency compensation is applied here), AI directly with {@code ClashClock.now()}. {@code kind}
	 * {@code DuelIds.IN_*}, {@code dir} a {@code SwingDir} ordinal, {@code flags} {@code DuelIds.INF_*}.
	 */
	public static void input(LivingEntity entity, int kind, int dir, int flags, int arg, long tick) {
		DuelConfig config = DuelConfig.get();
		if (!config.enabled || !entity.isAlive()) {
			return;
		}
		Fighter fighter = get(entity);
		long now = ClashClock.now();
		int comp = entity instanceof ServerPlayer ? fighter.comp : 0;
		long tEff = Math.max(fighter.lastTEff, tick - comp);
		fighter.lastTEff = tEff;
		fighter.lastActive = now;
		refreshContext(fighter);
		if (entity instanceof ServerPlayer player) {
			// The server's own copy of the client's keys: the forward key for the Blade Step, sprint for the qi wave.
			Input keys = player.getLastClientInput();
			if (keys.forward()) {
				flags |= DuelIds.INF_FORWARD;
			}
			if (keys.shift()) {
				flags |= DuelIds.INF_CROUCH;
			}
			if (player.isSprinting()) {
				flags |= DuelIds.INF_RUN;
			}
		}
		switch (kind) {
			case DuelIds.IN_PARRY -> parry(fighter, tEff, flags, now);
			case DuelIds.IN_FEINT -> feint(fighter, now);
			case DuelIds.IN_GUARD_ON -> guard(fighter, true, tEff, now);
			case DuelIds.IN_GUARD_OFF -> guard(fighter, false, tEff, now);
			case DuelIds.IN_SWING -> swing(fighter, SwingDir.byOrdinal(dir), flags, tick, now);
			case DuelIds.IN_CHARGE_START -> chargeStart(fighter, SwingDir.byOrdinal(dir), flags, tEff, now);
			case DuelIds.IN_CHARGE_RELEASE -> chargeRelease(fighter, flags, tick, tEff, now);
			case DuelIds.IN_BIND_PUSH -> push(fighter, Math.max(1, Math.min(3, arg)), now);
			case DuelIds.IN_BIND_RELEASE -> {
				if (fighter.bind != null) {
					fighter.bind.release(fighter, now);
				}
			}
			case DuelIds.IN_DODGE -> dodge(fighter, tEff, now);
			case DuelIds.IN_BC_SWING -> bcSwing(fighter, SwingDir.byOrdinal(dir), arg, (flags & DuelIds.INF_BC_HEAVY) != 0, tick, now);
			case DuelIds.IN_SURRENDER_HOLD -> Duel.surrender(entity);
			default -> {
			}
		}
	}

	/** True while the fighter may defend (deflect, guard): not staggered, not thrown back, not held, not locked. */
	private static boolean mayDefend(Fighter fighter, long now) {
		return fighter.entity.isAlive() && !fighter.staggered(now) && !fighter.recoiling(now) && fighter.bind == null
				&& !(fighter.held && phaseHolds(fighter));
	}

	/** INTRO and FINISHER hold everything; COUNTDOWN allows the stance (guard) but no swing. */
	private static boolean phaseHolds(Fighter fighter) {
		DuelSession session = fighter.session;
		return session == null || session.phase() != DuelSession.Phase.COUNTDOWN;
	}

	/** True when the fighter may start a swing now. */
	private static boolean maySwing(Fighter fighter, long now) {
		DuelSession session = fighter.session;
		if (session != null && session.phase() != DuelSession.Phase.FIGHT) {
			return false;
		}
		return fighter.free(now) && now >= fighter.nextActionAt && (fighter.swing == null || !fighter.swing.live());
	}

	/** True where the clash layer takes swings at all: an official fight, or a duel pair for Better Combat swings. */
	private static boolean swingContext(Fighter fighter, boolean bc) {
		if (fighter.session != null) {
			return fighter.session.phase() == DuelSession.Phase.FIGHT;
		}
		return bc && fighter.context != DuelIds.CONTEXT_NONE && fighter.context != DuelIds.CONTEXT_UMUM;
	}

	private static void parry(Fighter fighter, long tEff, int flags, long now) {
		Swing own = fighter.swing;
		if (own != null && own.live()) {
			// During the own windup a press is a feint (35.3.8); later in the swing it does nothing.
			if (own.charging || tEff < own.impact - ClashTiming.FEINT_LAST) {
				feint(fighter, now);
			}
			return;
		}
		if (!mayDefend(fighter, now) || fighter.held) {
			return;
		}
		if (fighter.session == null && !DuelConfig.get().mobParry && fighter.context == DuelIds.CONTEXT_UMUM) {
			return;
		}
		boolean locked = fighter.spamLocked(tEff) || fighter.spamLocked(now);
		Fighter.Press press = new Fighter.Press(tEff, locked, (flags & DuelIds.INF_FORWARD) != 0);
		press.predictedPerfect = (flags & DuelIds.INF_PREDICT_PERFECT) != 0;
		if (press.predictedPerfect) {
			fighter.statPredicted++;
		}
		fighter.addPress(press);
		markDirty(fighter);
		if (fighter.brain == null) {
			// Let watching duelists read the press (habit tracking of the AI).
			notifyReaders(fighter, DuelIds.IN_PARRY, now);
		}
	}

	private static void feint(Fighter fighter, long now) {
		Swing own = fighter.swing;
		if (own == null || !own.live() || now < fighter.feintReadyAt) {
			return;
		}
		if (own.kind == SwingKind.PENENTU || own.kind == SwingKind.BALIK || own.perilous() != DuelIds.PERIL_NONE) {
			return;
		}
		if (!own.charging && now >= own.impact - ClashTiming.FEINT_LAST) {
			return;
		}
		own.cancelled = true;
		fighter.swing = null;
		fighter.feintReadyAt = now + ClashTiming.FEINT_COOLDOWN;
		fighter.feintBonusUntil = now + ClashTiming.FEINT_BONUS_TICKS;
		Exchange.addPosture(fighter, null, ClashTiming.FEINT_POSTURE, rules(fighter), now);
		sendSwing(fighter, own, DuelIds.SW_FEINT_CANCEL);
		notifyReaders(fighter, DuelIds.IN_FEINT, now);
	}

	private static void guard(Fighter fighter, boolean on, long tEff, long now) {
		if (on) {
			if (!mayDefend(fighter, now) || fighter.guardBroken(now) || fighter.swing != null && fighter.swing.live()) {
				return;
			}
			if (fighter.held && phaseHolds(fighter)) {
				return;
			}
			fighter.guarding = true;
			fighter.guardSince = tEff;
		} else {
			fighter.guarding = false;
		}
		applyGuard(fighter, fighter.guarding);
		markDirty(fighter);
	}

	private static void dodge(Fighter fighter, long tEff, long now) {
		if (now < fighter.dodgeReadyAt || !fighter.free(now) || fighter.swing != null && fighter.swing.live() && !fighter.swing.charging) {
			return;
		}
		if (fighter.swing != null && fighter.swing.charging) {
			Exchange.interrupt(fighter, now);
		}
		fighter.dodgeUntil = Math.max(fighter.dodgeUntil, Math.max(tEff, now - fighter.comp) + ClashTiming.DODGE_TICKS);
		fighter.dodgeReadyAt = now + ClashTiming.DODGE_COOLDOWN;
		fighter.guarding = false;
		markDirty(fighter);
	}

	/** Combat Roll's roll ({@code DuelHooks.onRoll}): the same 6 dodge ticks, no cooldown of ours. */
	public static void roll(LivingEntity entity) {
		Fighter fighter = get(entity);
		long now = ClashClock.now();
		fighter.dodgeUntil = Math.max(fighter.dodgeUntil, now + ClashTiming.DODGE_TICKS);
		fighter.guarding = false;
		markDirty(fighter);
	}

	private static void push(Fighter fighter, int count, long now) {
		if (fighter.bind == null) {
			return;
		}
		for (int i = 0; i < count; i++) {
			if (fighter.pushesWithin(now, 20) >= Bind.MAX_PUSHES_PER_SECOND) {
				return;
			}
			fighter.addPush(now);
		}
	}

	// ================================================================ swings

	/** A light cut (or thrust, riposte, Deathblow) of {@code fighter} in {@code dir}. */
	private static void swing(Fighter fighter, SwingDir dir, int flags, long arrival, long now) {
		if (!swingContext(fighter, false)) {
			return;
		}
		if (!maySwing(fighter, now)) {
			rejectNew(fighter, dir, now);
			return;
		}
		LivingEntity self = fighter.entity;
		WeaponClass weapon = WeaponClass.of(self);
		fighter.weapon = weapon;
		LivingEntity target = findTarget(fighter, weapon.reach + 2.0D);
		Fighter other = target == null ? null : peek(target);
		Swing swing = new Swing(fighter.nextSwingId(), fighter);
		swing.weapon = weapon;
		swing.target = target;
		swing.dir = (flags & DuelIds.INF_CROUCH) != 0 ? SwingDir.TUSUK : dir;
		// The combo: a light cut soon after the last one continues it (max 3); three the same way with a heavy blade spin.
		boolean combo = now - fighter.lastLightImpact <= 14 && fighter.comboCount < ClashTiming.COMBO_MAX;
		fighter.comboCount = combo ? fighter.comboCount + 1 : 1;
		if (fighter.lastDir == swing.dir && combo) {
			fighter.sameDirCount++;
		} else {
			fighter.sameDirCount = 1;
		}
		fighter.lastDir = swing.dir;
		if (fighter.sameDirCount >= 3 && (weapon == WeaponClass.PEDANG_BERAT || weapon == WeaponClass.KAPAK)) {
			swing.dir = SwingDir.PUTAR;
			fighter.sameDirCount = 0;
		}
		int realm = Posture.realm(self);
		if (other != null && other.staggered(now) && self.distanceTo(target) <= weapon.reach + 1.5D) {
			swing.kind = SwingKind.PENENTU;
			swing.windup = 4;
		} else if (fighter.celah(now)) {
			swing.kind = SwingKind.BALIK;
			swing.windup = 3;
			swing.flags |= DuelIds.SW_RIPOSTE;
			swing.postureMul = 1.0F;
			fighter.celahUntil = 0L;
		} else if (swing.dir == SwingDir.TUSUK) {
			swing.kind = SwingKind.TUSUK;
			swing.windup = ClashTiming.realmWindup(weapon == WeaponClass.TOMBAK ? weapon.lightWindup : weapon.lightWindup + 1, realm);
		} else {
			swing.kind = SwingKind.RINGAN;
			swing.windup = ClashTiming.realmWindup(weapon.lightWindup, realm);
		}
		if (now < fighter.feintBonusUntil && swing.kind != SwingKind.PENENTU && swing.kind != SwingKind.BALIK) {
			swing.windup = Math.max(3, swing.windup - 1);
		}
		if (combo && fighter.comboCount > 1) {
			swing.flags |= DuelIds.SW_CHAIN_NEXT;
		}
		if (now < fighter.skyUntil && swing.dir.high() && Exchange.airborne(self)) {
			swing.flags |= DuelIds.SW_SKY;
			swing.postureMul *= 1.3F;
		}
		launch(fighter, swing, arrival, now);
		if (swing.kind == SwingKind.PENENTU) {
			DuelVoices.kiai(self, DuelIds.VOICE_DEATHBLOW);
		}
	}

	/** Starts holding a heavy cut (pose ANGKAT_BERAT); the swing is sent with windup 0 until it is released. */
	private static void chargeStart(Fighter fighter, SwingDir dir, int flags, long tEff, long now) {
		if (!swingContext(fighter, false)) {
			return;
		}
		if (!maySwing(fighter, now)) {
			rejectNew(fighter, dir, now);
			return;
		}
		LivingEntity self = fighter.entity;
		WeaponClass weapon = WeaponClass.of(self);
		fighter.weapon = weapon;
		Swing swing = new Swing(fighter.nextSwingId(), fighter);
		swing.weapon = weapon;
		swing.target = findTarget(fighter, weapon.reach + 4.0D);
		swing.dir = (flags & DuelIds.INF_CROUCH) != 0 ? SwingDir.TUSUK : dir;
		swing.kind = SwingKind.BERAT;
		swing.flags |= DuelIds.SW_HEAVY;
		swing.charging = true;
		swing.chargeStart = tEff;
		swing.windup = DuelIds.WINDUP_CHARGING;
		swing.start = now;
		swing.impact = Long.MAX_VALUE / 4;
		swing.resolveAt = Long.MAX_VALUE / 4;
		fighter.swing = swing;
		fighter.guarding = false;
		fighter.comboCount = 0;
		applyGuard(fighter, false);
		sendSwing(fighter, swing, 0);
		markDirty(fighter);
		notifyReaders(fighter, DuelIds.IN_CHARGE_START, now);
	}

	/** Releases a held heavy cut: too short → a light cut; full → the perilous and qi forms. */
	private static void chargeRelease(Fighter fighter, int flags, long arrival, long tEff, long now) {
		Swing swing = fighter.swing;
		if (swing == null || !swing.live() || !swing.charging) {
			return;
		}
		LivingEntity self = fighter.entity;
		long held = Math.max(0L, tEff - swing.chargeStart);
		WeaponClass weapon = swing.weapon;
		swing.charging = false;
		if (held < weapon.heavyMin) {
			swing.kind = swing.dir == SwingDir.TUSUK ? SwingKind.TUSUK : SwingKind.RINGAN;
			swing.flags &= ~(DuelIds.SW_HEAVY | DuelIds.SW_PERILOUS | DuelIds.SW_QI);
			swing.windup = ClashTiming.realmWindup(weapon.lightWindup, Posture.realm(self));
			launch(fighter, swing, arrival, now);
			return;
		}
		boolean full = swing.chargeFull >= 0L || held >= weapon.heavyFull;
		if (!full) {
			swing.flags &= ~(DuelIds.SW_PERILOUS | DuelIds.SW_QI);
		} else if (swing.chargeFull < 0L) {
			markFull(fighter, swing, now);
		}
		swing.windup = weapon.heavyRelease + (swing.perilous() != DuelIds.PERIL_NONE ? ClashTiming.PERILOUS_LEAD - weapon.heavyRelease + 1 : 0);
		swing.windup = Math.max(weapon.heavyRelease, swing.windup);
		if (full && swing.qi()) {
			if (spendQi(self)) {
				swing.kind = SwingKind.QI;
				if ((flags & DuelIds.INF_RUN) != 0 && swing.dir.wave()) {
					swing.flags |= DuelIds.SW_QI_WAVE;
				}
			} else {
				swing.flags &= ~DuelIds.SW_QI;
				swing.qiRgb = 0;
			}
		}
		DuelVoices.kiai(self, DuelIds.VOICE_HEAVY);
		launch(fighter, swing, arrival, now);
	}

	/** The charge became full: the perilous forms show their 危 now, the blade glows when the qi is there. */
	private static void markFull(Fighter fighter, Swing swing, long now) {
		swing.chargeFull = now;
		if (swing.dir == SwingDir.ATAS) {
			swing.flags |= DuelIds.SW_PERILOUS_GUNUNG;
		} else if (swing.dir == SwingDir.TUSUK) {
			swing.flags |= DuelIds.SW_PERILOUS_MAUT;
		} else if (swing.dir == SwingDir.BAWAH) {
			swing.flags |= DuelIds.SW_PERILOUS_SAPU;
		}
		if (canSpendQi(fighter.entity)) {
			swing.flags |= DuelIds.SW_QI;
			swing.qiRgb = QiWave.qiColor(fighter.entity);
		}
		sendSwing(fighter, swing, 0);
		markDirty(fighter);
		notifyReaders(fighter, DuelIds.IN_CHARGE_START, now);
	}

	/** A Better Combat swing ({@code DuelInput BC_SWING} / {@code DuelHooks.bcSwing}). */
	static void bcSwing(Fighter fighter, SwingDir dir, int windup, boolean heavy, long arrival, long now) {
		if (!swingContext(fighter, true) || !fighter.free(now)) {
			return;
		}
		Swing old = fighter.swing;
		if (old != null && old.live()) {
			if (!old.bc()) {
				return;
			}
			// BC chains attacks faster than our recovery: an unresolved BC swing gives way to the next.
			old.cancelled = true;
		}
		LivingEntity self = fighter.entity;
		WeaponClass weapon = WeaponClass.of(self);
		fighter.weapon = weapon;
		Swing swing = new Swing(fighter.nextSwingId(), fighter);
		swing.weapon = weapon;
		swing.target = findTarget(fighter, weapon.reach + 2.0D);
		swing.dir = dir;
		swing.kind = SwingKind.BC;
		swing.flags |= DuelIds.SW_BC;
		int base = heavy ? weapon.heavyRelease + weapon.heavyMin / 2 : weapon.lightWindup;
		swing.windup = Math.max(2, Math.min(base + 2, Math.max(base - 2, windup)));
		if (heavy) {
			swing.flags |= DuelIds.SW_HEAVY;
		}
		Fighter other = swing.target == null ? null : peek(swing.target);
		if (other != null && other.staggered(now)) {
			swing.kind = SwingKind.PENENTU;
			swing.flags &= ~DuelIds.SW_BC;
			swing.windup = 4;
		}
		launch(fighter, swing, arrival, now);
	}

	/** {@code DuelHooks.bcSwing}: a Better Combat swing reported by another path than {@code DuelInput} (C35.S2). */
	public static void bcSwing(ServerPlayer player, int dir, int windup, boolean heavy, boolean offhand) {
		if (!DuelConfig.get().enabled || !player.isAlive()) {
			return;
		}
		Fighter fighter = get(player);
		long now = ClashClock.now();
		fighter.latency = player.connection.latency();
		fighter.comp = ClashClock.compensation(fighter.latency, DuelConfig.get().maxRewindTicks);
		refreshContext(fighter);
		bcSwing(fighter, SwingDir.byOrdinal(dir), windup, heavy, now, now);
	}

	/**
	 * {@code DuelHooks.telegraph}: a mob announces a perilous blow landing at {@code impactTick} (35.3.13). The 危 mark
	 * is sent as a heavy perilous swing so every client shows it; the reactive defence applies the perilous rules.
	 */
	public static void telegraph(LivingEntity entity, int perilousKind, long impactTick) {
		if (!DuelConfig.get().enabled || entity instanceof ServerPlayer || !entity.isAlive()) {
			return;
		}
		Fighter fighter = get(entity);
		long now = ClashClock.now();
		fighter.telegraphKind = perilousKind;
		fighter.telegraphImpact = impactTick;
		fighter.lastActive = now;
		Swing mark = new Swing(fighter.nextSwingId(), fighter);
		mark.kind = SwingKind.BERAT;
		mark.dir = perilousKind == DuelIds.PERIL_MAUT ? SwingDir.TUSUK : perilousKind == DuelIds.PERIL_SAPU ? SwingDir.BAWAH : SwingDir.ATAS;
		mark.weapon = WeaponClass.of(entity);
		mark.start = now;
		mark.windup = (int) Math.max(1L, Math.min(255L, impactTick - now));
		mark.impact = impactTick;
		mark.flags = DuelIds.SW_HEAVY | switch (perilousKind) {
			case DuelIds.PERIL_MAUT -> DuelIds.SW_PERILOUS_MAUT;
			case DuelIds.PERIL_SAPU -> DuelIds.SW_PERILOUS_SAPU;
			case DuelIds.PERIL_GUNUNG -> DuelIds.SW_PERILOUS_GUNUNG;
			default -> 0;
		};
		mark.cancelled = true;
		sendSwing(fighter, mark, 0);
	}

	/** Times a swing (35.3.4) and sends it. */
	private static void launch(Fighter fighter, Swing swing, long arrival, long now) {
		int comp = fighter.entity instanceof ServerPlayer ? fighter.comp : 0;
		swing.start = Math.max(now - 2, arrival - Math.min(comp, 2));
		swing.impact = swing.start + swing.windup;
		if (swing.impact <= now) {
			swing.impact = now + 1;
		}
		Fighter defender = swing.target == null ? null : peek(swing.target);
		int defenderComp = defender != null && defender.entity instanceof ServerPlayer ? defender.comp : 0;
		swing.resolveAt = swing.impact + defenderComp + 1;
		fighter.swing = swing;
		fighter.guarding = false;
		applyGuard(fighter, false);
		if (fighter.entity instanceof ServerPlayer player) {
			player.resetAttackStrengthTicker();
		}
		sendSwing(fighter, swing, 0);
		markDirty(fighter);
		notifyReaders(fighter, DuelIds.IN_SWING, now);
	}

	/** Tells the client its swing was refused (the pose softens back). */
	private static void rejectNew(Fighter fighter, SwingDir dir, long now) {
		if (!(fighter.entity instanceof ServerPlayer)) {
			return;
		}
		Swing ghost = new Swing(fighter.nextSwingId(), fighter);
		ghost.kind = SwingKind.RINGAN;
		ghost.dir = dir;
		ghost.weapon = fighter.weapon;
		ghost.start = now;
		ghost.windup = fighter.weapon.lightWindup;
		ghost.cancelled = true;
		sendSwing(fighter, ghost, DuelIds.SW_REJECTED);
	}

	private static boolean canSpendQi(LivingEntity entity) {
		double cost = 6.0D + 3.0D * Posture.realm(entity);
		if (entity instanceof ServerPlayer player) {
			return dev.kultivator.data.Cultivators.get(player).qi() >= cost;
		}
		if (entity instanceof DiscipleEntity disciple) {
			return disciple.data().qi() >= cost;
		}
		return DuelistRegistry.profile(entity) != null;
	}

	private static boolean spendQi(LivingEntity entity) {
		double cost = 6.0D + 3.0D * Posture.realm(entity);
		if (entity instanceof ServerPlayer player) {
			return Qi.trySpend(player, cost);
		}
		if (entity instanceof DiscipleEntity disciple) {
			DiscipleData data = disciple.data();
			if (data.qi() < cost) {
				return false;
			}
			data.setQi(Math.max(0.0D, data.qi() - cost));
			return true;
		}
		return DuelistRegistry.profile(entity) != null;
	}

	/** Duelist brains watching this fighter read its visible actions (no hidden plans). */
	private static void notifyReaders(Fighter fighter, int kind, long now) {
		LivingEntity partner = fighter.partner;
		if (partner == null) {
			return;
		}
		Fighter watcher = peek(partner);
		if (watcher != null && watcher.brain != null) {
			watcher.brain.observe(fighter, kind, now);
		}
	}

	// ================================================================ the tick

	/** Once per server tick, after the entities moved. */
	public static void tick(MinecraftServer server) {
		long now = ClashClock.now();
		if (!FIGHTERS.isEmpty()) {
			List<Fighter> fighters = new ArrayList<>(FIGHTERS.values());
			for (Fighter fighter : fighters) {
				if (FIGHTERS.get(fighter.uuid) != fighter) {
					continue;
				}
				LivingEntity entity = fighter.entity;
				if (entity.isRemoved() || !entity.isAlive()) {
					if (fighter.session == null) {
						forget(fighter);
					}
					continue;
				}
				tickFighter(fighter, now);
			}
		}
		QiWave.tick(now);
		CompanionSword.tickStrikes(now);
		flushSyncs(now);
		if (now % 100 == 0) {
			DuelistRegistry.prune(server);
		}
	}

	private static void tickFighter(Fighter fighter, long now) {
		LivingEntity entity = fighter.entity;
		if ((now + fighter.uuid.hashCode()) % 10 == 0) {
			refreshContext(fighter);
			WeaponClass weapon = WeaponClass.of(entity);
			if (weapon != fighter.weapon) {
				fighter.weapon = weapon;
				markDirty(fighter);
			}
			float max = Posture.max(entity);
			if (Math.abs(max - fighter.postureMax) > 0.01F) {
				fighter.posture = Math.min(fighter.posture, max);
				fighter.postureMax = max;
				markDirty(fighter);
			}
		}
		// The swing in progress.
		Swing swing = fighter.swing;
		if (swing != null && swing.live()) {
			if (swing.charging) {
				long held = now - swing.chargeStart;
				if (swing.chargeFull < 0L && held >= swing.weapon.heavyFull) {
					markFull(fighter, swing, now);
				}
				if (swing.chargeFull >= 0L && now - swing.chargeFull >= ClashTiming.CHARGE_HOLD_MAX) {
					chargeRelease(fighter, entity.isSprinting() ? DuelIds.INF_RUN : 0, now, now, now);
				}
			} else if (now >= swing.resolveAt || now >= swing.impact + ClashTiming.MAX_RESOLVE_WAIT) {
				Exchange.resolve(swing, now);
			}
		}
		// The lock is ticked by its side A.
		Bind bind = fighter.bind;
		if (bind != null && bind.a == fighter) {
			bind.tick(now);
		}
		// Stagger over: the bar returns to half.
		if (fighter.staggerUntil > 0L && now >= fighter.staggerUntil) {
			fighter.staggerUntil = 0L;
			fighter.posture = fighter.postureMax * 0.5F;
			fighter.lastPostureGain = now;
			applyStagger(fighter, false);
			markDirty(fighter);
		}
		// Recovery.
		if (fighter.posture > 0.0F) {
			DuelRuleSet rules = rules(fighter);
			float back = Posture.recovery(fighter, now, rules != null && rules.realm() == DuelRuleSet.RealmRule.TEKAN_RANAH);
			if (back > 0.0F) {
				fighter.posture = Math.max(0.0F, fighter.posture - back);
				if (now % 4 == 0) {
					markDirty(fighter);
				}
			}
		}
		// Presses whose window is over: one that deflected nothing starts the spam lock (35.3.4).
		if (!fighter.presses.isEmpty()) {
			for (Fighter.Press press : fighter.presses) {
				if (!press.evaluated && now - press.tEff > ClashTiming.PARRY_WINDOW + ClashTiming.MAX_RESOLVE_WAIT) {
					press.evaluated = true;
					if (press.consumed && press.predictedPerfect) {
						fighter.statPredictedRight++;
					}
					if (!press.consumed) {
						fighter.spamLockUntil = Math.max(fighter.spamLockUntil, now + ClashTiming.SPAM_LOCK);
						markDirty(fighter);
					}
				}
			}
			fighter.prunePresses(now);
		}
		// The chain fades after the gap.
		if (fighter.chain > 0 && now - fighter.lastExchange > ClashTiming.CHAIN_GAP) {
			fighter.chain = 0;
			markDirty(fighter);
		}
		if (fighter.guarding && (fighter.staggered(now) || fighter.guardBroken(now) || fighter.bind != null)) {
			fighter.guarding = false;
			applyGuard(fighter, false);
			markDirty(fighter);
		}
		if (fighter.companion) {
			CompanionSword.tick(fighter, now);
		}
		// Keep the bar fresh for the viewers of a duel.
		if ((fighter.context != DuelIds.CONTEXT_UMUM && fighter.context != DuelIds.CONTEXT_NONE || fighter.posture > 0.0F)
				&& now - fighter.lastSync >= SYNC_KEEPALIVE) {
			markDirty(fighter);
		}
		// Forget quiet fighters outside duels.
		if (fighter.session == null && fighter.context == DuelIds.CONTEXT_UMUM && fighter.posture <= 0.0F && fighter.swing == null
				&& now - fighter.lastActive > IDLE_FORGET && fighter.presses.isEmpty()) {
			forget(fighter);
		}
	}

	// ================================================================ modifiers

	static void applyHold(Fighter fighter, boolean on) {
		modifier(fighter.entity, HOLD_ID, on || fighter.bind != null, -1.0D);
		if ((on || fighter.bind != null) && fighter.entity instanceof Mob mob) {
			mob.getNavigation().stop();
		}
	}

	static void applyGuard(Fighter fighter, boolean on) {
		modifier(fighter.entity, GUARD_ID, on, -0.3D);
	}

	static void applyStagger(Fighter fighter, boolean on) {
		modifier(fighter.entity, STAGGER_ID, on, -0.9D);
	}

	private static void modifier(LivingEntity entity, Identifier id, boolean on, double amount) {
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		if (on) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			speed.removeModifier(id);
		}
	}

	/** Removes every duel modifier from {@code entity} (end of a duel, logout, death; C35.S5). */
	public static void removeModifiers(LivingEntity entity) {
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(HOLD_ID);
			speed.removeModifier(GUARD_ID);
			speed.removeModifier(STAGGER_ID);
		}
		AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null) {
			attack.removeModifier(ClashDamage.KIND_ID);
		}
	}

	// ================================================================ payloads

	static void markDirty(Fighter fighter) {
		DIRTY.add(fighter);
	}

	private static void flushSyncs(long now) {
		if (DIRTY.isEmpty()) {
			return;
		}
		List<Fighter> list = new ArrayList<>(DIRTY);
		for (Fighter fighter : list) {
			if (now - fighter.lastSync < SYNC_MIN_GAP) {
				continue;
			}
			DIRTY.remove(fighter);
			syncFighter(fighter, now, false);
		}
	}

	/** Sends the fighter's bar when it changed (or {@code force}). */
	public static void syncFighter(Fighter fighter, long now, boolean force) {
		LivingEntity entity = fighter.entity;
		if (!(entity.level() instanceof ServerLevel level) || entity.isRemoved()) {
			return;
		}
		if (!postureOf(entity) && fighter.posture <= 0.0F && fighter.context == DuelIds.CONTEXT_UMUM && fighter.sentPostureX10 <= 0
				&& !force && fighter.presses.isEmpty()) {
			return;
		}
		int postureX10 = Math.round(fighter.posture * 10.0F);
		int maxX10 = Math.round(fighter.postureMax * 10.0F);
		int state = fighter.state(now);
		int flags = fighter.flags(now);
		int weapon = fighter.weapon.ordinal();
		boolean changed = postureX10 != fighter.sentPostureX10 || maxX10 != fighter.sentMaxX10 || state != fighter.sentState
				|| flags != fighter.sentFlags || fighter.chain != fighter.sentChain || weapon != fighter.sentWeapon;
		if (!changed && !force && now - fighter.lastSync < SYNC_KEEPALIVE) {
			return;
		}
		fighter.sentPostureX10 = postureX10;
		fighter.sentMaxX10 = maxX10;
		fighter.sentState = state;
		fighter.sentFlags = flags;
		fighter.sentChain = fighter.chain;
		fighter.sentWeapon = weapon;
		fighter.lastSync = now;
		DuelPayloads.DuelFighterSync payload = new DuelPayloads.DuelFighterSync(entity.getId(), postureX10, maxX10, state, flags,
				Math.min(255, fighter.chain), weapon);
		for (ServerPlayer player : viewers(level, entity, fighter.session != null ? SESSION_VIEW : MOB_VIEW)) {
			DuelNet.send(player, payload);
		}
	}

	/** Sends {@code swing} (again) to the players near; {@code extraFlags} adds FEINT_CANCEL or REJECTED. */
	static void sendSwing(Fighter fighter, Swing swing, int extraFlags) {
		LivingEntity entity = fighter.entity;
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		DuelPayloads.DuelSwing payload = new DuelPayloads.DuelSwing(entity.getId(), swing.id, swing.kind.ordinal(), swing.dir.ordinal(),
				swing.weapon.ordinal(), Math.max(0, Math.min(255, swing.charging ? DuelIds.WINDUP_CHARGING : swing.windup)),
				(int) swing.start, (swing.flags | extraFlags) & 0xFFFF, swing.qiRgb);
		for (ServerPlayer player : viewers(level, entity, fighter.session != null ? SESSION_VIEW : MOB_VIEW)) {
			DuelNet.send(player, payload);
		}
	}

	/** One result with the swings' colours (qi colour for QI swings, the weapon's spark colour otherwise). */
	static void sendClash(LivingEntity a, @Nullable LivingEntity b, @Nullable Swing sa, @Nullable Swing sb, int outcome, Vec3 at, int power,
			int flags) {
		sendClash(a, b, sa, sb, outcome, at, power, flags, color(a, sa), b == null ? 0xFFE7B0 : color(b, sb));
	}

	/** One result to everyone near the contact point (≤ 64 blocks). */
	static void sendClash(LivingEntity a, @Nullable LivingEntity b, @Nullable Swing sa, @Nullable Swing sb, int outcome, Vec3 at, int power,
			int flags, int rgbA, int rgbB) {
		if (!(a.level() instanceof ServerLevel level)) {
			return;
		}
		int seed = level.getRandom().nextInt(0x7FFF);
		DuelPayloads.DuelClash payload = new DuelPayloads.DuelClash(a.getId(), b == null ? -1 : b.getId(), sa == null ? -1 : sa.id,
				sb == null ? -1 : sb.id, outcome, (float) at.x, (float) at.y, (float) at.z, sa == null ? 255 : sa.dir.ordinal(),
				sb == null ? 255 : sb.dir.ordinal(), Math.max(0, Math.min(255, power)), rgbA, rgbB, (int) ClashClock.now(), seed, flags);
		for (ServerPlayer player : PlayerLookup.around(level, at, SESSION_VIEW)) {
			DuelNet.send(player, payload);
		}
		Duel.onClash(a, b, outcome);
	}

	/** A result decided outside an exchange (the session's DECISIVE result of a Better Combat or technique hit, tests). */
	public static void sendResult(LivingEntity a, @Nullable LivingEntity b, int outcome, Vec3 at, int power, int flags) {
		sendClash(a, b, null, null, outcome, at, power, flags);
	}

	/** The lock's pressure for the meter (every 2 ticks while it lasts). */
	static void sendBind(Bind bind, int flags) {
		LivingEntity a = bind.a.entity;
		if (!(a.level() instanceof ServerLevel level)) {
			return;
		}
		DuelPayloads.DuelBindSync payload = new DuelPayloads.DuelBindSync(a.getId(), bind.b.entity.getId(),
				Math.round(Math.max(-100.0F, Math.min(100.0F, bind.pressure))), Math.max(0, Math.min(255, bind.ticksLeft)), flags);
		for (ServerPlayer player : PlayerLookup.around(level, bind.contact, SESSION_VIEW)) {
			DuelNet.send(player, payload);
		}
	}

	private static int color(LivingEntity entity, @Nullable Swing swing) {
		if (swing != null && swing.qi() && swing.qiRgb != 0) {
			return swing.qiRgb;
		}
		WeaponClass weapon = swing != null ? swing.weapon : WeaponClass.of(entity);
		return weapon.sparkRgb;
	}

	private static Iterable<ServerPlayer> viewers(ServerLevel level, LivingEntity entity, double range) {
		return PlayerLookup.around(level, entity.position(), range);
	}

	// ================================================================ preferences, tests

	/** The client's duel settings arrived ({@code DuelPrefs}). */
	public static void applyPrefs(ServerPlayer player, DuelPayloads.DuelPrefs prefs) {
		Fighter fighter = peek(player);
		if (fighter != null) {
			applyPrefs(fighter, prefs);
		}
	}

	private static void applyPrefs(Fighter fighter, DuelPayloads.DuelPrefs prefs) {
		fighter.kiai = prefs.kiai();
		fighter.inputMode = prefs.inputMode();
		fighter.prefFlags = prefs.flags();
		fighter.prefsKnown = true;
	}

	/** {@code uji duel postur <0-100>}: sets the posture share (a full bar breaks at once). */
	public static void setPosture(LivingEntity entity, float share) {
		Fighter fighter = get(entity);
		long now = ClashClock.now();
		fighter.posture = Math.max(0.0F, Math.min(1.0F, share)) * fighter.postureMax;
		fighter.lastPostureGain = now;
		markDirty(fighter);
		Exchange.checkBreak(fighter, null, now);
	}

	/** {@code uji duel ikat}: forces a blade lock between the two. */
	public static boolean forceBind(LivingEntity a, LivingEntity b) {
		if (a == b || a.level() != b.level() || a.distanceTo(b) > Bind.MAX_DISTANCE + 2.0D) {
			return false;
		}
		Fighter fa = get(a);
		Fighter fb = get(b);
		Bind.start(fa, fb, HitTest.fallback(a, b), ClashClock.now());
		return true;
	}

	/** A Deathblow decided by a session: the stagger ends with the duel. */
	public static void endStagger(LivingEntity entity) {
		Fighter fighter = peek(entity);
		if (fighter != null && fighter.staggerUntil > 0L) {
			fighter.staggerUntil = ClashClock.now();
		}
	}

	/** True when {@code entity} cannot act now (staggered or thrown back): its own blows are refused. */
	public static boolean disabled(LivingEntity entity) {
		Fighter fighter = peek(entity);
		long now = ClashClock.now();
		return fighter != null && (fighter.staggered(now) || fighter.recoiling(now) && !(entity instanceof ServerPlayer)
				|| fighter.bind != null || fighter.held && fighter.session != null);
	}

	/** The swing resolved most recently for {@code swingId} matching (Better Combat), or the live one. */
	static @Nullable Swing bcSwingOf(Fighter fighter, LivingEntity target, long now) {
		Swing live = fighter.swing;
		if (live != null && live.live() && live.bc() && (live.target == null || live.target == target)
				&& Math.abs(live.impact - now) <= ClashTiming.BC_MATCH + 2) {
			return live;
		}
		Swing last = fighter.lastResolved;
		if (last != null && last.bc() && last.target == target && now - last.resolvedAt <= ClashTiming.BC_MATCH + 3) {
			return last;
		}
		return null;
	}

	/** Server stop: everything goes. */
	public static void clear() {
		for (Fighter fighter : FIGHTERS.values()) {
			removeModifiers(fighter.entity);
		}
		FIGHTERS.clear();
		DIRTY.clear();
		QiWave.clear();
		CompanionSword.clear();
	}

	/** A player left: forget them (and end their lock). */
	public static void onLeave(ServerPlayer player) {
		Fighter fighter = peek(player);
		if (fighter != null) {
			forget(fighter);
		} else {
			removeModifiers(player);
		}
	}

	private Fighters() {
	}
}
