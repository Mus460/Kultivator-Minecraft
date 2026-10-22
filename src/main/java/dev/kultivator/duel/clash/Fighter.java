package dev.kultivator.duel.clash;

import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.ai.DuelistBrain;
import dev.kultivator.duel.session.DuelSession;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.UUID;

/**
 * The clash state of one living entity (DESIGN 35.3): posture, the current swing, guard and deflect presses, stagger,
 * recoil, dodge, Opening, the exchange chain and the blade lock. Memory only ({@code Fighters} keeps them while the
 * entity is in a duel, recently fought or has a posture bar); never saved. Read-only accessors are the public API
 * (C35.S2); the clash code changes the fields directly.
 */
public final class Fighter {

	/** One deflect press: its effective tick, whether the spam lock narrowed it, the forward key, whether it deflected. */
	public static final class Press {
		public final long tEff;
		public final boolean locked;
		public final boolean forward;
		public boolean consumed;
		/** Checked once its window is over (a press that deflected nothing starts the spam lock). */
		boolean evaluated;
		/** The client claimed a Perfect for it (diagnostics only). */
		boolean predictedPerfect;

		Press(long tEff, boolean locked, boolean forward) {
			this.tEff = tEff;
			this.locked = locked;
			this.forward = forward;
		}
	}

	/** One line of {@code uji duel jendela}: {@code impact − tEff} of the press against a swing and what came of it. */
	public record WindowLog(long tick, int delta, int window, int outcome, int latency, int comp, boolean predictedPerfect) {
	}

	public final LivingEntity entity;
	public final UUID uuid;

	// ---------------------------------------------------------------- posture (35.3.6)
	float posture;
	float postureMax = 100.0F;
	long lastPostureGain = Long.MIN_VALUE / 4;

	// ---------------------------------------------------------------- timers (server ticks)
	long staggerUntil;
	long staggerStart = Long.MIN_VALUE / 4;
	int staggerCount;
	long recoilUntil;
	long guardBrokenUntil;
	long dodgeUntil;
	long dodgeReadyAt;
	long celahUntil;
	long nextActionAt;
	long feintReadyAt;
	long feintBonusUntil;
	/** Jumped clear of a Low Sweep: an upward-from-the-air cut until then is a "Tebasan Langit". */
	long skyUntil;
	long lastHurtByStranger;
	/** Movement slowed after a BLOCK (35.3.5) until then. */
	long blockSlowUntil;
	/** Held by the duel phase (INTRO, COUNTDOWN, FINISHER) or the blade lock. */
	boolean held;

	// ---------------------------------------------------------------- guard and deflect
	boolean guarding;
	long guardSince;
	final ArrayDeque<Press> presses = new ArrayDeque<>();
	long spamLockUntil;

	// ---------------------------------------------------------------- swings
	@Nullable Swing swing;
	@Nullable Swing lastResolved;
	int nextSwingId;
	int comboCount;
	long lastLightImpact = Long.MIN_VALUE / 4;
	@Nullable SwingDir lastDir;
	int sameDirCount;
	WeaponClass weapon = WeaponClass.TANGAN_KOSONG;
	/** A perilous telegraph of a mob ({@code DuelHooks.telegraph}): kind and impact tick. */
	int telegraphKind;
	long telegraphImpact;

	// ---------------------------------------------------------------- blade lock and chain
	@Nullable Bind bind;
	/** Ticks of the recent lock pushes (a ring of the last 16). */
	final long[] pushes = new long[16];
	int pushIndex;
	int chain;
	long lastExchange = Long.MIN_VALUE / 4;

	// ---------------------------------------------------------------- context
	int context = DuelIds.CONTEXT_NONE;
	@Nullable DuelSession session;
	@Nullable LivingEntity partner;

	// ---------------------------------------------------------------- network (players)
	int comp;
	int latency;
	int lastSeq = -1;
	long rateWindowStart;
	int rateCount;
	long lastRateLog = Long.MIN_VALUE / 4;
	int inputMode = DuelIds.INPUT_OTOMATIS;
	int prefFlags;
	int kiai;
	boolean prefsKnown;
	long lastTEff;

	// ---------------------------------------------------------------- AI
	@Nullable DuelistBrain brain;

	// ---------------------------------------------------------------- companion sword (35.3.11)
	boolean companion;
	long companionReadyAt;
	long companionStrikeReadyAt;

	// ---------------------------------------------------------------- stats of the running duel
	public int statHits;
	public int statPerfects;
	public int statDeflects;
	public int statBindsWon;
	public int statStaggers;
	public int statMaxChain;
	public int statPredicted;
	public int statPredictedRight;

	// ---------------------------------------------------------------- sync
	int sentPostureX10 = -1;
	int sentMaxX10 = -1;
	int sentState = -1;
	int sentFlags = -1;
	int sentChain = -1;
	int sentWeapon = -1;
	long lastSync = Long.MIN_VALUE / 4;
	long lastActive;
	/** Crouching still since then (bow to surrender after 2 s). */
	long crouchStillSince = -1L;

	final ArrayDeque<WindowLog> windowLog = new ArrayDeque<>();

	Fighter(LivingEntity entity) {
		this.entity = entity;
		this.uuid = entity.getUUID();
	}

	// ================================================================ read-only API (C35.S2)

	public float posture() {
		return posture;
	}

	public float postureMax() {
		return postureMax;
	}

	/** {@code DuelIds.ST_*} at {@code now}. */
	public int state() {
		return state(ClashClock.now());
	}

	public int chain() {
		return chain;
	}

	public @Nullable Swing swing() {
		return swing;
	}

	public @Nullable Swing lastResolved() {
		return lastResolved;
	}

	public @Nullable Bind bind() {
		return bind;
	}

	public @Nullable DuelSession session() {
		return session;
	}

	/** {@code DuelIds.CONTEXT_*}. */
	public int context() {
		return context;
	}

	public @Nullable LivingEntity partner() {
		return partner;
	}

	public WeaponClass weapon() {
		return weapon;
	}

	public boolean guarding() {
		return guarding && !guardBroken(ClashClock.now());
	}

	public boolean staggered(long now) {
		return now < staggerUntil;
	}

	public boolean staggered() {
		return staggered(ClashClock.now());
	}

	public boolean recoiling(long now) {
		return now < recoilUntil;
	}

	public boolean dodging(long now) {
		return now < dodgeUntil;
	}

	public boolean celah(long now) {
		return now < celahUntil;
	}

	public boolean guardBroken(long now) {
		return now < guardBrokenUntil;
	}

	public boolean spamLocked(long now) {
		return now < spamLockUntil;
	}

	public boolean held() {
		return held;
	}

	public int latency() {
		return latency;
	}

	public int comp() {
		return comp;
	}

	/** The input mode the player's client reported ({@code DuelIds.INPUT_*}); OTOMATIS when unknown or not a player. */
	public int inputMode() {
		return inputMode;
	}

	/** The kiai voice the player chose ({@code DuelIds.KIAI_*}; U's {@code DuelVoices.kiai} reads it). */
	public int kiai() {
		return kiai;
	}

	public @Nullable DuelistBrain brain() {
		return brain;
	}

	/** Posture breaks in the running duel (the Sword Saint bows out after two). */
	public int staggerCount() {
		return staggerCount;
	}

	/** The tick from which the fighter may act again after a swing (recovery). */
	public long nextActionAt() {
		return nextActionAt;
	}

	/** The dodge cooldown ends at this tick. */
	public long dodgeReadyAt() {
		return dodgeReadyAt;
	}

	/** A feint is possible again from this tick. */
	public long feintReadyAt() {
		return feintReadyAt;
	}

	public boolean hasCompanion() {
		return companion;
	}

	/** The last ten windows of {@code uji duel jendela}, oldest first. */
	public Iterable<WindowLog> windowLog() {
		return windowLog;
	}

	/** The push count of the last {@code ticks} ticks (blade lock). */
	public int pushesWithin(long now, int ticks) {
		int count = 0;
		for (long tick : pushes) {
			if (tick > 0L && now - tick < ticks && now >= tick) {
				count++;
			}
		}
		return count;
	}

	/** True when {@code entity} can act at all now (not staggered, recoiling, held or dead). */
	public boolean free(long now) {
		return entity.isAlive() && !staggered(now) && !recoiling(now) && !held && bind == null;
	}

	/** {@code DuelIds.ST_*}. */
	public int state(long now) {
		if (held && bind == null) {
			return DuelIds.ST_HELD;
		}
		if (bind != null) {
			return DuelIds.ST_BIND;
		}
		if (staggered(now)) {
			return DuelIds.ST_STAGGER;
		}
		if (recoiling(now)) {
			return DuelIds.ST_RECOIL;
		}
		if (dodging(now)) {
			return DuelIds.ST_DODGE;
		}
		Swing current = swing;
		if (current != null && current.live()) {
			if (current.charging || now < current.impact - 1) {
				return DuelIds.ST_WINDUP;
			}
			return DuelIds.ST_STRIKE;
		}
		if (now < nextActionAt) {
			return DuelIds.ST_RECOVER;
		}
		if (guarding()) {
			return DuelIds.ST_GUARD;
		}
		return DuelIds.ST_IDLE;
	}

	/** {@code DuelIds.FF_*}. */
	public int flags(long now) {
		int flags = 0;
		if (celah(now)) {
			flags |= DuelIds.FF_CELAH;
		}
		Swing current = swing;
		if (current != null && current.live() && current.charging && current.chargeFull >= 0L && current.qi()) {
			flags |= DuelIds.FF_QI_READY;
		}
		if (companion) {
			flags |= DuelIds.FF_COMPANION;
		}
		if (staggered(now)) {
			flags |= DuelIds.FF_DEATHBLOW_MARK;
		}
		if (spamLocked(now)) {
			flags |= DuelIds.FF_SPAM;
		}
		return flags;
	}

	// ================================================================ package helpers

	void addPush(long now) {
		pushes[pushIndex] = now;
		pushIndex = (pushIndex + 1) % pushes.length;
	}

	void clearPushes() {
		java.util.Arrays.fill(pushes, 0L);
	}

	void addPress(Press press) {
		presses.addLast(press);
		while (presses.size() > 6) {
			presses.removeFirst();
		}
	}

	/** Forgets presses older than the widest window can still use. */
	void prunePresses(long now) {
		for (Iterator<Press> it = presses.iterator(); it.hasNext(); ) {
			Press press = it.next();
			if (now - press.tEff > ClashTiming.PARRY_WINDOW + ClashTiming.MAX_RESOLVE_WAIT + 8) {
				it.remove();
			}
		}
	}

	void log(WindowLog entry) {
		windowLog.addLast(entry);
		while (windowLog.size() > 10) {
			windowLog.removeFirst();
		}
	}

	int nextSwingId() {
		int id = nextSwingId;
		nextSwingId = (nextSwingId + 1) & 0x7FFF;
		return id;
	}

	/** Clears everything a finished duel leaves behind (the posture stays for the HUD to fade). */
	void resetCombat() {
		swing = null;
		bind = null;
		held = false;
		guarding = false;
		presses.clear();
		staggerUntil = 0L;
		recoilUntil = 0L;
		guardBrokenUntil = 0L;
		dodgeUntil = 0L;
		celahUntil = 0L;
		nextActionAt = 0L;
		chain = 0;
		comboCount = 0;
		telegraphKind = DuelIds.PERIL_NONE;
		clearPushes();
	}

	void resetStats() {
		statHits = 0;
		statPerfects = 0;
		statDeflects = 0;
		statBindsWon = 0;
		statStaggers = 0;
		statMaxChain = 0;
		staggerCount = 0;
	}
}
