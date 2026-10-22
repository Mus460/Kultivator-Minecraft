package dev.kultivator.duel.clash;

import dev.kultivator.duel.Duel;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.ai.DuelistProfile;
import dev.kultivator.duel.ai.DuelistRegistry;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.voice.DuelVoices;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Kunci Bilah, the blade lock (DESIGN 35.3.7). Both fighters are held (pose locked, NPC look locked) while
 * {@link #pressure} (−100..100, positive: A ahead) moves each tick by {@code GAIN × (F_A − F_B)} with
 * {@code F = pushes per second × (1 + 0.04 × realm) × style × (0.5 + remaining posture share)}; pushes are capped at
 * 10 per second (more are dropped). {@code |pressure| ≥ 100} wins: the loser takes 30 posture, recoils 12 ticks and is
 * shoved 1.2 blocks; the winner gets an Opening. 40 ticks without a winner: both are thrown 1.5 blocks apart (+10
 * posture). The release trick (a deflect press while the other is pushing hard) makes the other stumble; at the wrong
 * moment it loses the lock. Synced every 2 ticks ({@code DuelBindSync}) for the yin-yang meter and the shaking poses.
 */
public final class Bind {

	/** Farther apart than this, a BIND becomes a CLASH. */
	public static final double MAX_DISTANCE = 2.2D;
	public static final int TICKS = 40;
	/** Pressure gained per tick per point of force difference (pushes per second). */
	public static final float GAIN = 0.8F;
	public static final int MAX_PUSHES_PER_SECOND = 10;
	public static final float LOSER_POSTURE = 30.0F;
	public static final float LEPAS_POSTURE = 10.0F;
	public static final float TRICK_POSTURE = 35.0F;
	/** The release trick works when the other pushed at least this often in the last {@link #TRICK_TICKS}. */
	public static final int TRICK_PUSHES = 2;
	public static final int TRICK_TICKS = 4;

	public final Fighter a;
	public final Fighter b;
	public final long start;
	public float pressure;
	public int ticksLeft = TICKS;
	public Vec3 contact;
	boolean ended;
	boolean firstSync = true;

	private Bind(Fighter a, Fighter b, Vec3 contact, long start) {
		this.a = a;
		this.b = b;
		this.contact = contact;
		this.start = start;
	}

	/** Starts a lock between two fighters (both held, pushes reset). */
	static void start(Fighter a, Fighter b, Vec3 contact, long now) {
		if (a.bind != null) {
			abort(a.bind, now);
		}
		if (b.bind != null) {
			abort(b.bind, now);
		}
		Bind bind = new Bind(a, b, contact, now);
		a.bind = bind;
		b.bind = bind;
		a.clearPushes();
		b.clearPushes();
		a.guarding = false;
		b.guarding = false;
		Exchange.interrupt(a, now);
		Exchange.interrupt(b, now);
		Fighters.applyHold(a, true);
		Fighters.applyHold(b, true);
		Fighters.sendBind(bind, DuelIds.BF_START);
		Fighters.markDirty(a);
		Fighters.markDirty(b);
	}

	/** The other side of {@code fighter}'s lock. */
	public Fighter other(Fighter fighter) {
		return fighter == a ? b : a;
	}

	/** One tick of the lock (called once per tick for side A). */
	void tick(long now) {
		if (ended) {
			return;
		}
		LivingEntity ea = a.entity;
		LivingEntity eb = b.entity;
		if (!ea.isAlive() || !eb.isAlive() || ea.level() != eb.level() || ea.distanceTo(eb) > MAX_DISTANCE + 1.5D) {
			abort(this, now);
			return;
		}
		float forceA = force(a, now);
		float forceB = force(b, now);
		pressure = Math.max(-100.0F, Math.min(100.0F, pressure + GAIN * (forceA - forceB)));
		ticksLeft--;
		// The contact point slides along the blades with the pressure ("steel grinding steel"); the client recomputes it.
		Vec3 mid = ea.position().add(eb.position()).scale(0.5D).add(0.0D, Math.min(ea.getBbHeight(), eb.getBbHeight()) * 0.7D, 0.0D);
		Vec3 toB = eb.position().subtract(ea.position());
		if (toB.lengthSqr() > 1.0E-6D) {
			contact = mid.add(toB.normalize().scale(pressure / 100.0D * 0.35D));
		}
		if (pressure >= 100.0F) {
			win(a, b, now);
			return;
		}
		if (pressure <= -100.0F) {
			win(b, a, now);
			return;
		}
		if (ticksLeft <= 0) {
			lepas(now);
			return;
		}
		if ((now - start) % 2 == 0) {
			int flags = (firstSync ? DuelIds.BF_START : 0) | (a.pushesWithin(now, 3) > 0 ? DuelIds.BF_A_PUSHING : 0)
					| (b.pushesWithin(now, 3) > 0 ? DuelIds.BF_B_PUSHING : 0);
			firstSync = false;
			Fighters.sendBind(this, flags);
		}
	}

	/** {@code F} of one side: pushes per second (≤ 10) × realm × style × posture left. */
	private float force(Fighter fighter, long now) {
		int pushes = Math.min(MAX_PUSHES_PER_SECOND, fighter.pushesWithin(now, 20));
		float realm = 1.0F + 0.04F * Posture.realm(fighter.entity);
		float style = 1.0F;
		DuelistProfile profile = DuelistRegistry.profile(fighter.entity);
		if (profile != null) {
			style = profile.style().push;
		}
		float left = 1.0F - fighter.posture / Math.max(1.0F, fighter.postureMax);
		return pushes * realm * style * (0.5F + Math.max(0.0F, left));
	}

	/** {@code fighter} lets go at once: the trick when the other is pushing hard, a lost lock otherwise. */
	void release(Fighter fighter, long now) {
		if (ended) {
			return;
		}
		Fighter other = other(fighter);
		if (other.pushesWithin(now, TRICK_TICKS) >= TRICK_PUSHES) {
			end(now);
			DuelRuleSet rules = Fighters.rules(fighter);
			Exchange.addPosture(other, fighter.entity, TRICK_POSTURE * Exchange.postureFactor(fighter.entity, other.entity, rules), rules, now);
			Exchange.recoil(other, ClashTiming.RECOIL_BIND, now);
			fighter.celahUntil = now + ClashTiming.CELAH_TICKS;
			shove(other.entity, fighter.entity, 0.35D);
			Fighters.sendClash(fighter.entity, other.entity, null, null, DuelIds.OUT_BIND_RELEASE_TRICK, contact, 200, 0);
			Duel.onBindResult(fighter, other, DuelIds.OUT_BIND_RELEASE_TRICK);
			Exchange.checkBreak(other, fighter.entity, now);
			return;
		}
		win(other, fighter, now);
	}

	private void win(Fighter winner, Fighter loser, long now) {
		end(now);
		DuelRuleSet rules = Fighters.rules(winner);
		Exchange.addPosture(loser, winner.entity, LOSER_POSTURE * Exchange.postureFactor(winner.entity, loser.entity, rules), rules, now);
		Exchange.recoil(loser, ClashTiming.RECOIL_BIND, now);
		winner.celahUntil = now + ClashTiming.CELAH_TICKS;
		winner.statBindsWon++;
		shove(loser.entity, winner.entity, 0.6D);
		int outcome = winner == a ? DuelIds.OUT_BIND_MENANG_A : DuelIds.OUT_BIND_MENANG_B;
		Fighters.sendClash(a.entity, b.entity, null, null, outcome, contact, 230, 0);
		Exchange.chainUp(a, b, now);
		Exchange.grant(winner.entity, "duel_bind_win");
		DuelVoices.kiai(winner.entity, DuelIds.VOICE_BIND_WIN);
		Duel.onBindResult(winner, loser, outcome);
		Exchange.checkBreak(loser, winner.entity, now);
	}

	private void lepas(long now) {
		end(now);
		DuelRuleSet rules = Fighters.rules(a);
		Exchange.addPosture(a, b.entity, LEPAS_POSTURE, rules, now);
		Exchange.addPosture(b, a.entity, LEPAS_POSTURE, rules, now);
		shove(a.entity, b.entity, 0.75D);
		shove(b.entity, a.entity, 0.75D);
		Fighters.sendClash(a.entity, b.entity, null, null, DuelIds.OUT_BIND_LEPAS, contact, 180, 0);
		Exchange.chainUp(a, b, now);
		Duel.onBindResult(a, b, DuelIds.OUT_BIND_LEPAS);
	}

	/** Ends the lock without a result (a stagger, a gone fighter, the duel's end). */
	static void abort(Bind bind, long now) {
		if (bind.ended) {
			return;
		}
		bind.end(now);
	}

	private void end(long now) {
		ended = true;
		if (a.bind == this) {
			a.bind = null;
		}
		if (b.bind == this) {
			b.bind = null;
		}
		Fighters.applyHold(a, a.held);
		Fighters.applyHold(b, b.held);
		a.nextActionAt = Math.max(a.nextActionAt, now + 4);
		b.nextActionAt = Math.max(b.nextActionAt, now + 4);
		Fighters.sendBind(this, DuelIds.BF_END);
		Fighters.markDirty(a);
		Fighters.markDirty(b);
	}

	/** Pushes {@code victim} away from {@code from} (knockback; players get the motion sent). */
	static void shove(LivingEntity victim, LivingEntity from, double power) {
		double dx = from.getX() - victim.getX();
		double dz = from.getZ() - victim.getZ();
		if (dx * dx + dz * dz < 1.0E-6D) {
			dx = -victim.getLookAngle().x;
			dz = -victim.getLookAngle().z;
		}
		victim.knockback(power, dx, dz, victim.level().damageSources().generic(), 0.0F);
		victim.needsSync = true;
	}

	public boolean ended() {
		return ended;
	}
}
