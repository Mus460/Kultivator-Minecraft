package dev.kultivator.duel.clash;

import dev.kultivator.duel.Duel;
import dev.kultivator.duel.DuelConfig;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.blade.DuelPose;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.duel.voice.DuelVoices;
import dev.kultivator.util.Advancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The resolution of an exchange (DESIGN 35.3.5): for every swing X → Y at its {@code resolveAt}, the first match of
 * <ol>
 * <li><b>Clash</b>: Y has its own swing at X with an impact within {@link ClashTiming#CLASH_WINDOW}, both face each other
 * within 60° and stand within the shorter reach + 0.6 → {@link #pair} decides both at once (CLASH, CLASH_MENANG, BIND,
 * tip-to-tip);</li>
 * <li><b>Evade</b>: Y's dodge ticks (Shadow Step, Combat Roll), or a Low Sweep under Y in the air;</li>
 * <li><b>Deflect</b>: Y's press with {@code impact − tEff} in the window while facing → PERFECT or DEFLECT (perilous and
 * riposte rules of 35.3.8, the Blade Step against the Death Thrust);</li>
 * <li><b>Guard</b>: Y holds the guard while facing → BLOCK, or GUARD_BREAK when the posture fills, against a Mountain Cleave
 * or a mace's heavy cut;</li>
 * <li><b>Hit</b>: the shared blade arcs ({@link HitTest#sweep}) reach Y's body → HIT, else WHIFF.</li>
 * </ol>
 * Posture and health follow the table of 35.3.5; every result is sent as one {@code DuelClash} to the players near, so
 * all clients play the same sparks and sounds at the same contact point. Server thread only.
 */
public final class Exchange {

	/** A Mountain Cleave, a mace's heavy cut, a full guard: what a guard cannot hold. */
	private static final float CHIP_SHARE = 0.10F;
	private static final float AXE_CHIP_SHARE = 0.30F;
	private static final float CHIP_POSTURE_LINE = 0.70F;

	// ================================================================ resolution

	/** Resolves {@code swing} now (its {@code resolveAt} has come, or Better Combat's own hit arrived). */
	static void resolve(Swing swing, long now) {
		if (!swing.live()) {
			return;
		}
		Fighter x = swing.owner;
		LivingEntity attacker = x.entity;
		finishSwing(swing, now);
		if (!attacker.isAlive()) {
			return;
		}
		if ((swing.flags & DuelIds.SW_QI_WAVE) != 0) {
			QiWave.launch(swing);
		}
		LivingEntity target = Fighters.validTarget(x, swing.target);
		if (target == null) {
			target = Fighters.findTarget(x, HitTest.reach(swing) + 1.0D);
		}
		if (target == null) {
			whiff(swing, null, now);
			return;
		}
		swing.target = target;
		Fighter y = Fighters.get(target);
		DuelRuleSet rules = Fighters.rules(x);

		// 1. Clash: both swing at each other at (almost) the same moment.
		Swing other = y.swing;
		if (other != null && other.live() && !other.charging && other.target == attacker && Math.abs(other.impact - swing.impact) <= ClashTiming.CLASH_WINDOW
				&& swing.weapon.blade() && other.weapon.blade() && clashGeometry(swing, other)) {
			pair(swing, other, now);
			return;
		}

		// A Deathblow on a still-staggered victim cannot be stopped; once the stagger is over it is a plain heavy cut.
		boolean deathblow = swing.kind == SwingKind.PENENTU && y.staggered(swing.impact);
		if (swing.kind == SwingKind.PENENTU && !deathblow) {
			swing.kind = SwingKind.BERAT;
			swing.flags |= DuelIds.SW_HEAVY;
		}
		int perilous = swing.perilous();

		// 2. Evade.
		if (!deathblow || !y.staggered(now)) {
			if (y.dodging(swing.impact) || y.dodging(now)) {
				evade(swing, y, now);
				return;
			}
			if (perilous == DuelIds.PERIL_SAPU && airborne(target)) {
				y.skyUntil = now + ClashTiming.SKY_TICKS;
				evade(swing, y, now);
				return;
			}
		}

		boolean faces = HitTest.facing(target, attacker, ClashTiming.GUARD_ARC);

		// 3. Deflect.
		if (!deathblow && faces && perilous != DuelIds.PERIL_SAPU) {
			int window = parryWindow(y, attacker, rules);
			Fighter.Press press = bestPress(y, swing.impact, window);
			if (press != null) {
				int delta = (int) (swing.impact - press.tEff);
				boolean perfect = !press.locked && delta >= 0 && delta <= ClashTiming.PERFECT_WINDOW;
				boolean canDeflect = y.weapon.parriesBlades || !swing.weapon.blade();
				int outcome;
				if (!canDeflect) {
					outcome = -1;
				} else if (perilous == DuelIds.PERIL_MAUT) {
					if (press.forward && delta >= 0 && delta <= ClashTiming.MIKIRI_WINDOW) {
						outcome = DuelIds.OUT_MIKIRI;
					} else {
						outcome = perfect ? DuelIds.OUT_PERFECT : -2;
					}
				} else if (swing.riposte()) {
					outcome = perfect ? DuelIds.OUT_PERFECT : -2;
				} else {
					outcome = perfect ? DuelIds.OUT_PERFECT : DuelIds.OUT_DEFLECT;
				}
				logWindow(y, delta, window, outcome < 0 ? DuelIds.OUT_HIT : outcome, now);
				if (outcome >= 0) {
					press.consumed = true;
					deflect(swing, y, outcome, now);
					return;
				}
				if (outcome == -2) {
					// The press came, but only a Perfect (or the Blade Step) stops this one: it goes through.
					press.consumed = true;
					hit(swing, y, rules, now, false);
					return;
				}
				// A bare hand or a mace cannot deflect a blade: the press counts as a guard.
				if (!y.guardBroken(now)) {
					guard(swing, y, rules, now);
					return;
				}
			}
		}

		// 4. Guard.
		if (!deathblow && faces && y.guarding() && perilous != DuelIds.PERIL_SAPU) {
			if (perilous == DuelIds.PERIL_MAUT || swing.riposte()) {
				hit(swing, y, rules, now, false);
				return;
			}
			guard(swing, y, rules, now);
			return;
		}

		// 5. Hit.
		if (deathblow || HitTest.sweep(swing, target)) {
			hit(swing, y, rules, now, deathblow);
		} else {
			whiff(swing, target, now);
		}
	}

	/** Marks the swing done and starts the attacker's recovery. */
	private static void finishSwing(Swing swing, long now) {
		Fighter x = swing.owner;
		swing.resolved = true;
		swing.resolvedAt = now;
		x.lastResolved = swing;
		if (x.swing == swing) {
			x.swing = null;
		}
		boolean heavy = swing.heavy() || swing.kind.heavy() || swing.kind == SwingKind.PENENTU;
		if (heavy) {
			x.nextActionAt = Math.max(x.nextActionAt, now + ClashTiming.RECOVER_HEAVY);
			x.comboCount = 0;
		} else {
			// A combo's next light cut may start 3 ticks after the impact (at most 3 in a row), else the light recovery.
			long next = x.comboCount < ClashTiming.COMBO_MAX ? swing.impact + ClashTiming.COMBO_GAP : now + ClashTiming.RECOVER_LIGHT;
			x.nextActionAt = Math.max(x.nextActionAt, next);
			x.lastLightImpact = swing.impact;
		}
		Fighters.markDirty(x);
	}

	/** Facing and distance for a clash: both within 60° of each other and within the shorter reach + 0.6. */
	private static boolean clashGeometry(Swing a, Swing b) {
		LivingEntity ea = a.owner.entity;
		LivingEntity eb = b.owner.entity;
		if (!HitTest.facing(ea, eb, ClashTiming.CLASH_ARC) || !HitTest.facing(eb, ea, ClashTiming.CLASH_ARC)) {
			return false;
		}
		double reach = Math.min(HitTest.reach(a), HitTest.reach(b)) + 0.6D;
		return ea.distanceToSqr(eb) <= reach * reach + 1.0D;
	}

	/**
	 * Two swings meet (35.3.5 step 1): light against light → CLASH (both recoil 4, posture +8); heavy against light →
	 * CLASH_MENANG for the heavy side; heavy against heavy, or opposed light cuts of equal strength at arm's length →
	 * BIND; thrust against thrust head on → a tip-to-tip CLASH.
	 */
	static void pair(Swing a, Swing b, long now) {
		Fighter fa = a.owner;
		Fighter fb = b.owner;
		finishSwing(a, now);
		finishSwing(b, now);
		LivingEntity ea = fa.entity;
		LivingEntity eb = fb.entity;
		DuelRuleSet rules = Fighters.rules(fa);
		Vec3 at = HitTest.contact(ea, a, eb, DuelPose.KUDA_TENGAH, b);
		boolean heavyA = a.heavy() || a.kind.heavy();
		boolean heavyB = b.heavy() || b.kind.heavy();
		double distance = ea.distanceTo(eb);
		int outcome;
		if (a.dir == SwingDir.TUSUK && b.dir == SwingDir.TUSUK) {
			outcome = DuelIds.OUT_CLASH;
		} else if (heavyA && heavyB || !heavyA && !heavyB && a.dir.opposes(b.dir) && a.weapon.edged() && b.weapon.edged()) {
			outcome = distance <= Bind.MAX_DISTANCE && a.weapon.edged() && b.weapon.edged() ? DuelIds.OUT_BIND : DuelIds.OUT_CLASH;
		} else if (heavyA != heavyB) {
			outcome = DuelIds.OUT_CLASH_MENANG;
		} else {
			outcome = DuelIds.OUT_CLASH;
		}
		if (outcome == DuelIds.OUT_CLASH_MENANG && heavyB) {
			// Always report the heavy side as A of a won clash.
			Swing swap = a;
			a = b;
			b = swap;
			Fighter swapF = fa;
			fa = fb;
			fb = swapF;
			LivingEntity swapE = ea;
			ea = eb;
			eb = swapE;
		}
		switch (outcome) {
			case DuelIds.OUT_BIND -> {
				Fighters.sendClash(ea, eb, a, b, DuelIds.OUT_BIND, at, Math.max(a.power(), b.power()), 0);
				chainUp(fa, fb, now);
				Bind.start(fa, fb, at, now);
				notifySession(fa, DuelIds.OUT_BIND, ea, eb);
				return;
			}
			case DuelIds.OUT_CLASH_MENANG -> {
				addPosture(fb, ea, 0.6F * a.weapon.postureHeavy * postureFactor(ea, eb, rules), rules, now);
				addPosture(fa, eb, 4.0F * postureFactor(eb, ea, rules), rules, now);
				recoil(fb, ClashTiming.RECOIL_CLASH_LOST, now);
				interrupt(fb, now);
			}
			default -> {
				addPosture(fa, eb, 8.0F * postureFactor(eb, ea, rules), rules, now);
				addPosture(fb, ea, 8.0F * postureFactor(ea, eb, rules), rules, now);
				recoil(fa, ClashTiming.RECOIL_CLASH, now);
				recoil(fb, ClashTiming.RECOIL_CLASH, now);
			}
		}
		Fighters.sendClash(ea, eb, a, b, outcome, at, Math.max(a.power(), b.power()), 0);
		chainUp(fa, fb, now);
		notifySession(fa, outcome, ea, eb);
		checkBreak(fa, eb, now);
		checkBreak(fb, ea, now);
	}

	// ================================================================ outcomes

	private static void evade(Swing swing, Fighter y, long now) {
		LivingEntity attacker = swing.owner.entity;
		Vec3 at = y.entity.position().add(0.0D, y.entity.getBbHeight() * 0.5D, 0.0D);
		swing.outcome = DuelIds.OUT_EVADE;
		Fighters.sendClash(attacker, y.entity, swing, null, DuelIds.OUT_EVADE, at, swing.power() / 2, 0);
		notifySession(swing.owner, DuelIds.OUT_EVADE, attacker, y.entity);
	}

	private static void whiff(Swing swing, @Nullable LivingEntity near, long now) {
		Fighter x = swing.owner;
		swing.outcome = DuelIds.OUT_WHIFF;
		x.nextActionAt = Math.max(x.nextActionAt, now + ClashTiming.RECOVER_WHIFF + (swing.heavy() ? ClashTiming.RECOVER_HEAVY : ClashTiming.RECOVER_LIGHT));
		if (swing.bc()) {
			// Better Combat decides its own hits; a miss on our side is only no posture.
			swing.outcome = -1;
			return;
		}
		LivingEntity attacker = x.entity;
		Vec3 at = attacker.getEyePosition().add(attacker.getLookAngle().scale(HitTest.reach(swing) * 0.8D));
		Fighters.sendClash(attacker, near, swing, null, DuelIds.OUT_WHIFF, at, swing.power() / 3, 0);
	}

	/** PERFECT, DEFLECT or MIKIRI by {@code y} against {@code swing}. */
	private static void deflect(Swing swing, Fighter y, int outcome, long now) {
		Fighter x = swing.owner;
		LivingEntity attacker = x.entity;
		LivingEntity defender = y.entity;
		DuelRuleSet rules = Fighters.rules(x);
		float base = swing.weapon.postureLight;
		swing.outcome = outcome;
		Vec3 at = HitTest.contact(attacker, swing, defender, outcome == DuelIds.OUT_PERFECT ? DuelPose.TANGKIS_SEMPURNA : DuelPose.TANGKIS, null);
		switch (outcome) {
			case DuelIds.OUT_PERFECT -> {
				float heavyBase = swing.heavy() || swing.kind.heavy() ? swing.weapon.postureHeavy : base;
				addPosture(x, defender, 1.4F * heavyBase * postureFactor(defender, attacker, rules), rules, now);
				recoil(x, swing.heavy() || swing.kind.heavy() ? ClashTiming.RECOIL_HEAVY : ClashTiming.RECOIL_LIGHT, now);
				y.celahUntil = now + ClashTiming.CELAH_TICKS;
				y.statPerfects++;
				y.statDeflects++;
				grant(defender, "duel_first_deflect");
				grant(defender, "duel_first_perfect");
			}
			case DuelIds.OUT_MIKIRI -> {
				addPosture(x, defender, 45.0F * postureFactor(defender, attacker, rules), rules, now);
				recoil(x, ClashTiming.RECOIL_HEAVY, now);
				y.celahUntil = now + ClashTiming.CELAH_TICKS;
				y.statPerfects++;
				grant(defender, "duel_mikiri");
				DuelVoices.kiai(defender, DuelIds.VOICE_MIKIRI);
			}
			default -> {
				addPosture(y, attacker, 0.4F * base * postureFactor(attacker, defender, rules), rules, now);
				addPosture(x, defender, 0.6F * base * postureFactor(defender, attacker, rules), rules, now);
				y.statDeflects++;
				grant(defender, "duel_first_deflect");
			}
		}
		interrupt(x, now);
		if (x.brain != null) {
			x.brain.ownDeflected(now);
		}
		Fighters.sendClash(attacker, defender, swing, null, outcome, at, swing.power(), 0);
		chainUp(x, y, now);
		notifySession(x, outcome, attacker, defender);
		checkBreak(x, defender, now);
		checkBreak(y, attacker, now);
	}

	/** BLOCK, or GUARD_BREAK when the guard cannot hold. */
	private static void guard(Swing swing, Fighter y, @Nullable DuelRuleSet rules, long now) {
		Fighter x = swing.owner;
		LivingEntity attacker = x.entity;
		LivingEntity defender = y.entity;
		boolean heavy = swing.heavy() || swing.kind.heavy();
		float amount = (heavy ? swing.weapon.postureHeavy : swing.weapon.postureLight) * postureFactor(attacker, defender, rules)
				* swing.kind.posture * swing.postureMul;
		boolean breaker = swing.perilous() == DuelIds.PERIL_GUNUNG || heavy && swing.weapon == WeaponClass.GADA;
		Vec3 at = HitTest.contact(attacker, swing, defender, DuelPose.KUDA_TENGAH, null);
		if (breaker || y.posture + scaled(amount, y, rules) >= y.postureMax) {
			swing.outcome = DuelIds.OUT_GUARD_BREAK;
			guardBreak(y, attacker, now);
			Fighters.sendClash(attacker, defender, swing, null, DuelIds.OUT_GUARD_BREAK, at, 230, 0);
			notifySession(x, DuelIds.OUT_GUARD_BREAK, attacker, defender);
			return;
		}
		swing.outcome = DuelIds.OUT_BLOCK;
		addPosture(y, attacker, amount, rules, now);
		y.blockSlowUntil = now + 6;
		// Chip damage: a guard near breaking (BEBAS) leaks 10%, an axe's heavy cut ignores 30% of any guard.
		float chip = 0.0F;
		if (heavy && swing.weapon == WeaponClass.KAPAK) {
			chip = AXE_CHIP_SHARE;
		} else if (rules != null && rules.realm() == DuelRuleSet.RealmRule.BEBAS && y.posture > y.postureMax * CHIP_POSTURE_LINE) {
			chip = CHIP_SHARE;
		}
		Fighters.sendClash(attacker, defender, swing, null, DuelIds.OUT_BLOCK, at, swing.power(), 0);
		if (chip > 0.0F && !swing.bc()) {
			ClashDamage.apply(swing, defender, rules, chip);
		}
		notifySession(x, DuelIds.OUT_BLOCK, attacker, defender);
	}

	/** A guard broken: the posture fills, the guard drops for 20 ticks, the stagger begins. */
	static void guardBreak(Fighter y, LivingEntity by, long now) {
		y.guardBrokenUntil = now + ClashTiming.GUARD_BROKEN_TICKS;
		y.guarding = false;
		y.posture = y.postureMax;
		y.lastPostureGain = now;
		stagger(y, by, now, false);
		Duel.cheer(y, DuelIds.CHEER_GUARD_BREAK);
	}

	/** A cut lands on the body: posture, health, the victim's recoil, the Deathblow. */
	private static void hit(Swing swing, Fighter y, @Nullable DuelRuleSet rules, long now, boolean deathblow) {
		Fighter x = swing.owner;
		LivingEntity attacker = x.entity;
		LivingEntity victim = y.entity;
		Vec3 at = HitTest.hitPoint(swing, victim);
		if (swing.bc() && !deathblow) {
			// Better Combat deals its own damage when its hit arrives (matchBc); here only the result is kept open.
			swing.outcome = -1;
			return;
		}
		swing.outcome = deathblow ? DuelIds.OUT_DEATHBLOW : DuelIds.OUT_HIT;
		DuelSession session = x.session;
		int phaseBefore = session != null ? session.phase().ordinal() : -1;
		if (deathblow) {
			x.statHits++;
			grant(attacker, "duel_first_deathblow");
			DuelVoices.kiai(attacker, DuelIds.VOICE_DEATHBLOW);
			if (session == null || !session.deathblow(attacker, victim)) {
				// No duel rule decides it: a heavy blow instead (×2.5, or the boss caps).
				deathblowDamage(swing, y, rules);
			}
			y.staggerUntil = Math.min(y.staggerUntil, now + 2);
		} else {
			if (!(victim instanceof ServerPlayer) || y.context != DuelIds.CONTEXT_NONE) {
				addPosture(y, attacker, 0.5F * swing.weapon.postureLight * postureFactor(attacker, victim, rules) * swing.kind.posture
						* swing.postureMul, rules, now);
			}
			float dealt = ClashDamage.apply(swing, victim, rules, 1.0F);
			if (dealt > 0.0F) {
				x.statHits++;
			}
			recoil(y, ClashTiming.RECOIL_HIT, now);
			interrupt(y, now);
		}
		int flags = session != null && session.phase().ordinal() != phaseBefore && session.decided() ? DuelIds.CF_DECISIVE : 0;
		if (flags != 0) {
			session.markDecisiveSent();
		}
		Fighters.sendClash(attacker, victim, swing, null, swing.outcome, at, swing.power(), flags);
		notifySession(x, swing.outcome, attacker, victim);
		if (!deathblow) {
			checkBreak(y, attacker, now);
		}
	}

	/** The Deathblow's damage outside the Deathblow rule: ×2.5 (TEKAN 30%); bosses ×1.5 capped at 15% of max health. */
	static void deathblowDamage(Swing swing, Fighter y, @Nullable DuelRuleSet rules) {
		LivingEntity victim = y.entity;
		if (MobGuard.boss(victim)) {
			float cap = MobGuard.DEATHBLOW_BOSS_CAP * victim.getMaxHealth();
			float health = victim.getHealth();
			ClashDamage.apply(swing, victim, null, MobGuard.DEATHBLOW_BOSS / SwingKind.PENENTU.damage);
			float lost = health - victim.getHealth();
			if (lost > cap && victim.isAlive()) {
				victim.setHealth(Math.max(1.0F, health - cap));
			}
			return;
		}
		ClashDamage.apply(swing, victim, rules, 1.0F);
	}

	// ================================================================ posture

	/** The posture multiplier of a blow from {@code from} on {@code to}: Sword Dao, Body Dao, HANDICAP. */
	static float postureFactor(LivingEntity from, LivingEntity to, @Nullable DuelRuleSet rules) {
		return Posture.dealtFactor(from) * Posture.takenFactor(to) * ClashDamage.handicapPosture(from, to, rules);
	}

	/** A 100-bar amount on {@code fighter}'s bar: shares under TEKAN_RANAH, absolute otherwise. */
	static float scaled(float amount, Fighter fighter, @Nullable DuelRuleSet rules) {
		return rules != null && rules.realm() == DuelRuleSet.RealmRule.TEKAN_RANAH ? amount * fighter.postureMax / 100.0F : amount;
	}

	/** Adds posture (35.3.6); a full bar is checked by {@link #checkBreak}. */
	static void addPosture(Fighter fighter, @Nullable LivingEntity by, float amount, @Nullable DuelRuleSet rules, long now) {
		if (!(amount > 0.0F) || fighter.staggered(now)) {
			return;
		}
		fighter.posture = Math.min(fighter.postureMax, fighter.posture + scaled(amount, fighter, rules));
		fighter.lastPostureGain = now;
		Fighters.markDirty(fighter);
	}

	/** When the bar is full: Postur Jebol (stagger, the 決 mark, an Opening for the one who broke it). */
	static void checkBreak(Fighter fighter, @Nullable LivingEntity by, long now) {
		if (fighter.posture >= fighter.postureMax - 0.01F && !fighter.staggered(now) && fighter.entity.isAlive()) {
			stagger(fighter, by, now, true);
		}
	}

	/** Staggers {@code fighter} ({@code broadcast}: send the POSTURE_BREAK result). */
	static void stagger(Fighter fighter, @Nullable LivingEntity by, long now, boolean broadcast) {
		LivingEntity entity = fighter.entity;
		int ticks = entity instanceof ServerPlayer ? ClashTiming.STAGGER_TICKS : MobGuard.staggerTicks(entity);
		fighter.staggerStart = now;
		fighter.staggerUntil = now + ticks;
		fighter.staggerCount++;
		fighter.statStaggers++;
		fighter.guarding = false;
		fighter.presses.clear();
		interrupt(fighter, now);
		if (fighter.bind != null) {
			Bind.abort(fighter.bind, now);
		}
		if (entity instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		Fighters.applyStagger(fighter, true);
		if (by != null) {
			Fighter breaker = Fighters.peek(by);
			if (breaker != null) {
				breaker.celahUntil = now + ClashTiming.CELAH_TICKS;
			}
		}
		DuelVoices.kiai(entity, DuelIds.VOICE_HURT);
		if (broadcast) {
			Vec3 at = entity.position().add(0.0D, entity.getBbHeight() * 0.6D, 0.0D);
			Fighters.sendClash(by != null ? by : entity, entity, null, null, DuelIds.OUT_POSTURE_BREAK, at, 220, 0);
			notifySession(fighter, DuelIds.OUT_POSTURE_BREAK, by, entity);
		}
		Fighters.markDirty(fighter);
	}

	// ================================================================ helpers

	/** Throws {@code fighter} back: no action for {@code ticks}. */
	static void recoil(Fighter fighter, int ticks, long now) {
		fighter.recoilUntil = Math.max(fighter.recoilUntil, now + ticks);
		Fighters.markDirty(fighter);
	}

	/** Cancels the fighter's own swing in progress (a hit or a recoil interrupts its windup; a full heavy charge holds). */
	static void interrupt(Fighter fighter, long now) {
		Swing swing = fighter.swing;
		if (swing == null || !swing.live()) {
			return;
		}
		if (swing.charging && swing.chargeFull >= 0L && swing.perilous() != DuelIds.PERIL_NONE) {
			return;
		}
		swing.cancelled = true;
		fighter.swing = null;
		Fighters.sendSwing(fighter, swing, DuelIds.SW_REJECTED);
	}

	/** Exchanges within the gap add to both chains (35.3.8). */
	static void chainUp(Fighter a, Fighter b, long now) {
		long last = Math.max(a.lastExchange, b.lastExchange);
		int chain = now - last <= ClashTiming.CHAIN_GAP ? Math.max(a.chain, b.chain) + 1 : 1;
		a.chain = chain;
		b.chain = chain;
		a.lastExchange = now;
		b.lastExchange = now;
		a.statMaxChain = Math.max(a.statMaxChain, chain);
		b.statMaxChain = Math.max(b.statMaxChain, chain);
		Fighters.markDirty(a);
		Fighters.markDirty(b);
		if (chain >= 10) {
			grant(a.entity, "duel_chain_10");
			grant(b.entity, "duel_chain_10");
		}
		if (chain == 6) {
			Duel.cheer(a, DuelIds.CHEER_CHAIN);
		}
	}

	/** The newest unused press within {@code window} before {@code impact} (spam-locked presses only within 0..2). */
	static @Nullable Fighter.Press bestPress(Fighter y, long impact, int window) {
		Fighter.Press best = null;
		for (Fighter.Press press : y.presses) {
			if (press.consumed) {
				continue;
			}
			long delta = impact - press.tEff;
			int limit = press.locked ? Math.min(window, ClashTiming.SPAM_WINDOW) : window;
			if (delta < 0 || delta > limit) {
				continue;
			}
			if (best == null || press.tEff > best.tEff) {
				best = press;
			}
		}
		return best;
	}

	/** The defender's deflect window: 0..5, axe and spear −1, HANDICAP's higher side −1 per 2 realms, never below 3. */
	static int parryWindow(Fighter y, @Nullable LivingEntity attacker, @Nullable DuelRuleSet rules) {
		int window = ClashTiming.PARRY_WINDOW + y.weapon.parryWindowDelta + ClashDamage.handicapWindow(y.entity, attacker, rules);
		return Math.max(3, window);
	}

	/** True when {@code entity} is at least 0.4 blocks above the ground (a read-only ray down; nothing is changed). */
	static boolean airborne(LivingEntity entity) {
		if (entity.onGround()) {
			return false;
		}
		Vec3 feet = entity.position();
		net.minecraft.world.phys.BlockHitResult ray = entity.level().clip(new net.minecraft.world.level.ClipContext(feet,
				feet.add(0.0D, -2.0D, 0.0D), net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, entity));
		double height = ray.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? 2.0D : feet.y - ray.getLocation().y;
		return height >= ClashTiming.SWEEP_CLEAR;
	}

	private static void logWindow(Fighter y, int delta, int window, int outcome, long now) {
		if (y.entity instanceof ServerPlayer) {
			y.log(new Fighter.WindowLog(now, delta, window, outcome, y.latency, y.comp, false));
		}
	}

	private static void notifySession(Fighter fighter, int outcome, @Nullable LivingEntity attacker, @Nullable LivingEntity defender) {
		DuelSession session = fighter.session;
		if (session != null) {
			session.onOutcome(outcome, attacker, defender);
		}
	}

	static void grant(@Nullable LivingEntity entity, String advancement) {
		if (entity instanceof ServerPlayer player && DuelConfig.get().enabled) {
			Advancements.grant(player, advancement);
		}
	}

	private Exchange() {
	}
}
