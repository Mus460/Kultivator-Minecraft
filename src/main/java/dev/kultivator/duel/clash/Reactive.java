package dev.kultivator.duel.clash;

import dev.kultivator.data.Dao;
import dev.kultivator.duel.DuelConfig;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.net.DuelNet;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.voice.DuelVoices;
import dev.kultivator.technique.Techniques;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The clash layer for blows it did not deal itself (DESIGN 35.3.13, 35.3.14): vanilla melee and Better Combat hits,
 * mobs' claws and blades, techniques of a duel partner, projectiles at a fighter with a Companion Sword.
 * <ul>
 * <li><b>Better Combat</b>: BC's own hit is matched with the pending BC swing ({@code Fighters.bcSwingOf}); a deflect,
 * clash, lock, guard or evade cancels it, a hit passes as BC dealt it and only adds posture (and the sparks).</li>
 * <li><b>Reactive defence</b> (mobs have no visible windup): a deflect press in the 6 ticks before a blow arrives
 * deflects it, within 2 ticks it is Perfect (the mob is pushed back, its posture grows, its navigation stops); a held
 * guard takes {@code guardDamageFactor} of the damage and posture. A mob's 危 telegraph ({@code DuelHooks.telegraph})
 * applies the perilous rules.</li>
 * <li><b>Armed clash</b>: a player's blow and a blade-armed mob's blow within 2 ticks of each other, facing: both
 * cancelled (the first is healed back), full sparks.</li>
 * <li><b>Posture mobs</b>: a player's blow fills the bar; on a staggered one it becomes a Deathblow (×2.5 for rogues and
 * elites, ×1.5 capped at 15% for bosses).</li>
 * </ul>
 * Everything here only cancels, reduces ({@code ClashDamage.scaleNext}) or adds posture; extra damage is dealt one tick
 * later through {@code ClashDamage} so no damage is nested inside another.
 */
public final class Reactive {

	/** The answer for the ALLOW_DAMAGE hook. */
	public static final int ALLOW = 0;
	public static final int CANCEL = 1;

	/** Recent melee blows by attacker → victim: tick and the damage taken (for the armed clash). */
	private record Key(UUID attacker, UUID victim) {
	}

	private static final Map<Key, float[]> LAST_MELEE = new HashMap<>();
	/** Technique hits per victim: periodic damage (auras, domains) cannot be deflected after the first. */
	private static final Map<UUID, Long> LAST_TECHNIQUE = new HashMap<>();

	private record Pending(Fighter attacker, Fighter victim, long due) {
	}

	private static final List<Pending> DEATHBLOWS = new ArrayList<>();
	/** A technique burst: hits of the same victim further apart than this can be deflected. */
	private static final int BURST_GAP = 10;
	/** Posture a Sword Dao technique adds to its victim (35.3.12). */
	private static final float SWORD_TECHNIQUE_POSTURE = 12.0F;

	// ================================================================ melee

	/**
	 * A melee blow of {@code attacker} on {@code victim} that the clash layer did not deal ({@code attacker} is the direct
	 * entity). {@code CANCEL} or {@code ALLOW}.
	 */
	public static int melee(LivingEntity victim, LivingEntity attacker, DamageSource source, float amount) {
		DuelConfig config = DuelConfig.get();
		long now = ClashClock.now();
		Fighter x = Fighters.peek(attacker);
		// A. Better Combat: its own hit meets the swing it announced.
		if (attacker instanceof ServerPlayer && x != null) {
			Swing bc = Fighters.bcSwingOf(x, victim, now);
			if (bc != null) {
				if (bc.live()) {
					Exchange.resolve(bc, now);
				}
				int outcome = bc.outcome;
				bc.outcome = -2;
				if (outcome == -1 || outcome == DuelIds.OUT_HIT || outcome == DuelIds.OUT_WHIFF) {
					bodyHit(x, Fighters.get(victim), bc.weapon, bc, now);
					return ALLOW;
				}
				if (outcome >= 0) {
					return CANCEL;
				}
			}
		}
		// B. A staggered or thrown-back mob cannot strike.
		if (!(attacker instanceof ServerPlayer) && Fighters.disabled(attacker)) {
			return CANCEL;
		}
		// C. Armed clash.
		if (config.armedMobClash && armedClash(attacker, victim, now)) {
			return CANCEL;
		}
		if (attacker instanceof ServerPlayer || victim instanceof ServerPlayer) {
			LAST_MELEE.put(new Key(attacker.getUUID(), victim.getUUID()), new float[] {now, 0.0F});
		}
		// D. A player's reactive defence.
		if (victim instanceof ServerPlayer player && DuelNet.hasModule(player)) {
			Fighter y = Fighters.get(player);
			boolean duelContext = y.context != DuelIds.CONTEXT_UMUM && y.context != DuelIds.CONTEXT_NONE;
			if (config.mobParry || duelContext) {
				int answer = defend(y, attacker, source, now);
				if (answer == CANCEL) {
					return CANCEL;
				}
			}
			if (duelContext && attacker instanceof ServerPlayer && x != null) {
				bodyHit(x, y, WeaponClass.of(attacker), null, now);
			}
			return ALLOW;
		}
		// E. A player's blow on a posture mob (or a duel partner).
		if (attacker instanceof ServerPlayer && Fighters.postureOf(victim)) {
			Fighter y = Fighters.get(victim);
			Fighter by = x != null ? x : Fighters.get(attacker);
			if (y.staggered(now)) {
				queueDeathblow(by, y, now);
				return CANCEL;
			}
			bodyHit(by, y, WeaponClass.of(attacker), null, now);
		}
		return ALLOW;
	}

	/** Posture of a body hit that lands (and the sparks for blades). */
	private static void bodyHit(Fighter x, Fighter y, WeaponClass weapon, @Nullable Swing swing, long now) {
		LivingEntity attacker = x.entity;
		LivingEntity victim = y.entity;
		if (!y.staggered(now)) {
			DuelRuleSet rules = Fighters.rules(y);
			float amount = 0.5F * weapon.postureLight * Exchange.postureFactor(attacker, victim, rules);
			if (swing != null && (swing.heavy() || swing.kind.heavy())) {
				amount = 0.5F * weapon.postureHeavy * Exchange.postureFactor(attacker, victim, rules);
			}
			Exchange.addPosture(y, attacker, amount, rules, now);
		}
		if (weapon.blade()) {
			Vec3 at = swing != null ? HitTest.hitPoint(swing, victim) : HitTest.fallback(attacker, victim);
			Fighters.sendClash(attacker, victim, swing, null, DuelIds.OUT_HIT, at, swing != null ? swing.power() : 90, 0);
		}
		Exchange.checkBreak(y, attacker, now);
	}

	/** The reactive defence of {@code y} against a blow of {@code attacker} arriving now. */
	private static int defend(Fighter y, LivingEntity attacker, DamageSource source, long now) {
		LivingEntity victim = y.entity;
		if (!victim.isAlive() || y.staggered(now) || y.bind != null || y.held && y.session != null) {
			return ALLOW;
		}
		if (y.dodging(now)) {
			Vec3 at = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
			Fighters.sendClash(attacker, victim, null, null, DuelIds.OUT_EVADE, at, 60, 0);
			return CANCEL;
		}
		boolean faces = HitTest.facing(victim, attacker, ClashTiming.GUARD_ARC);
		if (!faces) {
			return ALLOW;
		}
		Fighter x = Fighters.peek(attacker);
		int peril = DuelIds.PERIL_NONE;
		if (x != null && x.telegraphKind != DuelIds.PERIL_NONE && Math.abs(x.telegraphImpact - now) <= 3L) {
			peril = x.telegraphKind;
		}
		WeaponClass attackWeapon = WeaponClass.of(attacker);
		float light = mobPosture(attacker, attackWeapon);
		DuelRuleSet rules = Fighters.rules(y);
		Fighter.Press press = reactivePress(y, now);
		if (press != null && peril != DuelIds.PERIL_SAPU && (y.weapon.parriesBlades || !attackWeapon.blade())) {
			int delta = (int) (now - press.tEff);
			boolean perfect = !press.locked && delta <= ClashTiming.REACTIVE_PERFECT;
			int outcome = perfect ? DuelIds.OUT_PERFECT : DuelIds.OUT_DEFLECT;
			if (peril == DuelIds.PERIL_MAUT && !perfect) {
				outcome = press.forward && delta <= ClashTiming.MIKIRI_WINDOW ? DuelIds.OUT_MIKIRI : -1;
			}
			if (outcome >= 0) {
				press.consumed = true;
				Fighter xf = x != null ? x : Fighters.get(attacker);
				Vec3 at = HitTest.contact(attacker, null, victim, perfect ? dev.kultivator.duel.blade.DuelPose.TANGKIS_SEMPURNA
						: dev.kultivator.duel.blade.DuelPose.TANGKIS, null);
				if (outcome == DuelIds.OUT_DEFLECT) {
					Exchange.addPosture(y, attacker, 0.4F * light, rules, now);
					if (Fighters.postureOf(attacker)) {
						Exchange.addPosture(xf, victim, 0.6F * light * Exchange.postureFactor(victim, attacker, rules), rules, now);
					}
					y.statDeflects++;
					Exchange.grant(victim, "duel_first_deflect");
				} else {
					// Perfect (or the Blade Step): the attacker is thrown back and stops.
					if (Fighters.postureOf(attacker)) {
						float gain = outcome == DuelIds.OUT_MIKIRI ? 45.0F : 1.4F * light;
						Exchange.addPosture(xf, victim, gain * Exchange.postureFactor(victim, attacker, rules), rules, now);
					}
					Exchange.recoil(xf, ClashTiming.RECOIL_HEAVY, now);
					if (attacker instanceof Mob mob) {
						mob.getNavigation().stop();
					}
					Bind.shove(attacker, victim, 0.4D);
					y.celahUntil = now + ClashTiming.CELAH_TICKS;
					y.statPerfects++;
					y.statDeflects++;
					Exchange.grant(victim, "duel_first_deflect");
					Exchange.grant(victim, outcome == DuelIds.OUT_MIKIRI ? "duel_mikiri" : "duel_first_perfect");
					if (outcome == DuelIds.OUT_MIKIRI) {
						DuelVoices.kiai(victim, DuelIds.VOICE_MIKIRI);
					}
				}
				Fighters.sendClash(attacker, victim, null, null, outcome, at, outcome == DuelIds.OUT_DEFLECT ? 120 : 200, 0);
				Exchange.chainUp(xf, y, now);
				Exchange.checkBreak(xf, victim, now);
				Exchange.checkBreak(y, attacker, now);
				return CANCEL;
			}
		}
		if (y.guarding() && peril != DuelIds.PERIL_SAPU && peril != DuelIds.PERIL_MAUT) {
			Vec3 at = HitTest.contact(attacker, null, victim, dev.kultivator.duel.blade.DuelPose.KUDA_TENGAH, null);
			boolean breaker = peril == DuelIds.PERIL_GUNUNG || attackWeapon == WeaponClass.GADA;
			if (breaker || y.posture + Exchange.scaled(light, y, rules) >= y.postureMax) {
				Exchange.guardBreak(y, attacker, now);
				Fighters.sendClash(attacker, victim, null, null, DuelIds.OUT_GUARD_BREAK, at, 220, 0);
				return ALLOW;
			}
			ClashDamage.scaleNext(victim, source, (float) DuelConfig.get().guardDamageFactor);
			Exchange.addPosture(y, attacker, light, rules, now);
			y.blockSlowUntil = now + 6;
			Fighters.sendClash(attacker, victim, null, null, DuelIds.OUT_BLOCK, at, 110, 0);
			return ALLOW;
		}
		return ALLOW;
	}

	/** The newest unused press within the reactive window (6 ticks; a spam-locked one only 2). */
	private static @Nullable Fighter.Press reactivePress(Fighter y, long now) {
		Fighter.Press best = null;
		for (Fighter.Press press : y.presses) {
			if (press.consumed) {
				continue;
			}
			long delta = now - press.tEff;
			int limit = press.locked ? ClashTiming.SPAM_WINDOW : ClashTiming.REACTIVE_DEFLECT;
			if (delta < 0 || delta > limit) {
				continue;
			}
			if (best == null || press.tEff > best.tEff) {
				best = press;
			}
		}
		return best;
	}

	/** The L of a mob's blow: its weapon's, or twice its attack damage for big claws (at most 40). */
	private static float mobPosture(LivingEntity attacker, WeaponClass weapon) {
		float light = weapon.postureLight;
		if (attacker.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
			light = Math.max(light, (float) Math.min(40.0D, attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * 2.0D));
		}
		return light;
	}

	/**
	 * A player's blow and a blade-armed mob's blow within 2 ticks, facing each other: the blades met. The earlier blow is
	 * healed back, this one is cancelled; both take the clash posture and recoil. True when it was a clash.
	 */
	private static boolean armedClash(LivingEntity attacker, LivingEntity victim, long now) {
		boolean playerHits = attacker instanceof ServerPlayer;
		LivingEntity mob = playerHits ? victim : attacker;
		LivingEntity player = playerHits ? attacker : victim;
		if (!(player instanceof ServerPlayer serverPlayer) || !(mob instanceof Mob) || !DuelNet.hasModule(serverPlayer)) {
			return false;
		}
		WeaponClass mobWeapon = WeaponClass.of(mob);
		if (!mobWeapon.edged() || !WeaponClass.of(player).edged()) {
			return false;
		}
		float[] reverse = LAST_MELEE.get(new Key(victim.getUUID(), attacker.getUUID()));
		if (reverse == null || now - (long) reverse[0] > ClashTiming.MOB_CLASH_WINDOW) {
			return false;
		}
		if (!HitTest.facing(attacker, victim, ClashTiming.CLASH_ARC) || !HitTest.facing(victim, attacker, ClashTiming.CLASH_ARC)) {
			return false;
		}
		LAST_MELEE.remove(new Key(victim.getUUID(), attacker.getUUID()));
		if (reverse[1] > 0.0F && attacker.isAlive()) {
			attacker.heal(reverse[1]);
		}
		Fighter fa = Fighters.get(attacker);
		Fighter fb = Fighters.get(victim);
		Exchange.addPosture(fa, victim, 8.0F, null, now);
		Exchange.addPosture(fb, attacker, 8.0F, null, now);
		Exchange.recoil(fa, ClashTiming.RECOIL_CLASH, now);
		Exchange.recoil(fb, ClashTiming.RECOIL_CLASH, now);
		Vec3 at = HitTest.fallback(attacker, victim);
		Fighters.sendClash(attacker, victim, null, null, DuelIds.OUT_CLASH, at, 150, 0);
		Exchange.chainUp(fa, fb, now);
		return true;
	}

	/** AFTER_DAMAGE: the damage a melee blow did (the armed clash heals it back if the blades met). */
	public static void afterDamage(LivingEntity victim, DamageSource source, float taken) {
		if (!(source.getEntity() instanceof LivingEntity attacker) || source.getEntity() != source.getDirectEntity()) {
			return;
		}
		float[] entry = LAST_MELEE.get(new Key(attacker.getUUID(), victim.getUUID()));
		if (entry != null && (long) entry[0] == ClashClock.now()) {
			entry[1] = taken;
		}
	}

	// ================================================================ Deathblow on a mob

	private static void queueDeathblow(Fighter attacker, Fighter victim, long now) {
		for (Pending pending : DEATHBLOWS) {
			if (pending.victim() == victim) {
				return;
			}
		}
		DEATHBLOWS.add(new Pending(attacker, victim, now + 1));
	}

	/** Every server tick: deals the queued Deathblows outside formal duels (one tick after the blow, never nested). */
	public static void tick(long now) {
		if (!DEATHBLOWS.isEmpty()) {
			for (Iterator<Pending> it = new ArrayList<>(DEATHBLOWS).iterator(); it.hasNext(); ) {
				Pending pending = it.next();
				if (now < pending.due()) {
					continue;
				}
				DEATHBLOWS.remove(pending);
				deathblow(pending.attacker(), pending.victim(), now);
			}
		}
		if (now % 100 == 0) {
			LAST_MELEE.values().removeIf(entry -> now - (long) entry[0] > 40L);
			LAST_TECHNIQUE.values().removeIf(tick -> now - tick > 200L);
		}
	}

	private static void deathblow(Fighter x, Fighter y, long now) {
		LivingEntity attacker = x.entity;
		LivingEntity victim = y.entity;
		if (!attacker.isAlive() || !victim.isAlive() || attacker.level() != victim.level() || attacker.distanceTo(victim) > 8.0F) {
			return;
		}
		Swing swing = new Swing(x.nextSwingId(), x);
		swing.kind = SwingKind.PENENTU;
		swing.dir = SwingDir.ATAS;
		swing.weapon = WeaponClass.of(attacker);
		swing.target = victim;
		swing.start = now;
		swing.impact = now;
		swing.resolved = true;
		swing.outcome = DuelIds.OUT_DEATHBLOW;
		x.statHits++;
		Exchange.grant(attacker, "duel_first_deathblow");
		DuelVoices.kiai(attacker, DuelIds.VOICE_DEATHBLOW);
		// In a formal duel under the Deathblow rule it wins at once; otherwise it is the heavy blow (boss caps apply).
		dev.kultivator.duel.session.DuelSession session = x.session;
		boolean decided = session != null && session.deathblow(attacker, victim);
		if (!decided) {
			Exchange.deathblowDamage(swing, y, Fighters.rules(y));
		}
		y.staggerUntil = Math.min(y.staggerUntil, now + 2);
		Vec3 at = victim.position().add(0.0D, victim.getBbHeight() * 0.6D, 0.0D);
		Fighters.sendClash(attacker, victim, swing, null, DuelIds.OUT_DEATHBLOW, at, 255, decided ? DuelIds.CF_DECISIVE : 0);
	}

	// ================================================================ techniques and projectiles

	/**
	 * A duel partner's technique on {@code victim} under {@code JURUS_BEBAS} (35.3.12): a single directed strike can be
	 * deflected (Perfect cancels it, a Deflect halves it, a guard takes 70% and posture). Pierce hits and the periodic
	 * hits of auras and domains (the same victim within 10 ticks) cannot. A Sword Dao technique that lands adds 12 posture.
	 */
	public static int technique(LivingEntity victim, LivingEntity caster, DamageSource source) {
		long now = ClashClock.now();
		Long last = LAST_TECHNIQUE.put(victim.getUUID(), now);
		boolean burst = last == null || now - last >= BURST_GAP;
		Fighter y = Fighters.get(victim);
		DuelRuleSet rules = Fighters.rules(y);
		boolean deflectable = burst && !source.is(Techniques.DAMAGE_PIERCE) && HitTest.facing(victim, caster, ClashTiming.GUARD_ARC)
				&& !y.staggered(now) && y.bind == null;
		if (deflectable) {
			Fighter.Press press = reactivePress(y, now);
			Vec3 at = HitTest.fallback(caster, victim);
			if (press != null && y.weapon.parriesBlades) {
				press.consumed = true;
				boolean perfect = !press.locked && now - press.tEff <= ClashTiming.REACTIVE_PERFECT;
				if (perfect) {
					y.statPerfects++;
					y.celahUntil = now + ClashTiming.CELAH_TICKS;
					Fighters.sendClash(caster, victim, null, null, DuelIds.OUT_PERFECT, at, 200, 0);
					return CANCEL;
				}
				ClashDamage.scaleNext(victim, source, 0.5F);
				y.statDeflects++;
				Fighters.sendClash(caster, victim, null, null, DuelIds.OUT_DEFLECT, at, 140, 0);
				return ALLOW;
			}
			if (y.guarding()) {
				ClashDamage.scaleNext(victim, source, 0.7F);
				Exchange.addPosture(y, caster, WeaponClass.PEDANG.postureLight, rules, now);
				Fighters.sendClash(caster, victim, null, null, DuelIds.OUT_BLOCK, at, 120, 0);
				Exchange.checkBreak(y, caster, now);
				return ALLOW;
			}
		}
		if (Posture.dao(caster) == Dao.SWORD && burst) {
			Exchange.addPosture(y, caster, SWORD_TECHNIQUE_POSTURE, rules, now);
			Exchange.checkBreak(y, caster, now);
		}
		return ALLOW;
	}

	/** A projectile at a fighter whose Companion Sword is ready: caught (35.3.11). */
	public static int projectile(LivingEntity victim, DamageSource source) {
		if (!(source.getDirectEntity() instanceof Projectile projectile)) {
			return ALLOW;
		}
		Fighter y = Fighters.peek(victim);
		if (y == null || !y.companion || y.session == null) {
			return ALLOW;
		}
		long now = ClashClock.now();
		if (!CompanionSword.tryDeflect(y, now)) {
			return ALLOW;
		}
		Vec3 at = projectile.position();
		LivingEntity shooter = source.getEntity() instanceof LivingEntity living ? living : victim;
		Fighters.sendClash(victim, shooter, null, null, DuelIds.OUT_COMPANION_DEFLECT, at, 140, 0);
		projectile.discard();
		return CANCEL;
	}

	/** Server stop. */
	public static void clear() {
		LAST_MELEE.clear();
		LAST_TECHNIQUE.clear();
		DEATHBLOWS.clear();
	}

	private Reactive() {
	}
}
