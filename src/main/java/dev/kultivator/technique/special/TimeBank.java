package dev.kultivator.technique.special;

import dev.kultivator.data.Cultivators;
import dev.kultivator.technique.Techniques;
import dev.kultivator.technique.spec.DamageContext;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.run.CastContext;
import dev.kultivator.technique.spec.run.EndReason;
import dev.kultivator.technique.spec.run.SpecialTechnique;
import dev.kultivator.util.DamageHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code time_bank}, the special of {@code time_spirit_domain} (DESIGN 22.2.7, 22.9.8; Han Li's Time Spirit Domain in
 * A Record of a Mortal's Journey). The spec's own timeline holds the DOMAIN with its "time stops" STUN, the CD_RATE and
 * the @160 strike; this handler keeps the <b>damage bank</b>: while the domain lasts, damage the caster deals to a
 * stopped monster (non-boss, non-player, inside the domain) does not land but is banked; one tick before the resume
 * every bank is released ×{@link #RELEASE} through the player's choke point ({@code Techniques.hurt} →
 * {@code TechniquePower.finalDamage}, so the heart-demon, trial-mob and ledger rules still apply).
 * <p>
 * The bank works through the spine's {@code DamageHooks} (stage COUNTER, registered once): zero cost while no bank is
 * open, only the caster's own hits (melee, arrows, techniques) are banked, unblockable damage never reaches it, and a
 * stopped monster cannot die while time stands still. The stopped set is refreshed with one entity query per domain
 * pulse. A bank is dropped without release if the caster dies, logs out or changes dimension, and any bank the runner
 * forgets is dropped after {@link #STALE} ticks.
 */
public final class TimeBank implements SpecialTechnique {

	public static final String ID = "time_bank";
	public static final double RADIUS = 24.0D;
	public static final double RADIUS_CAP = 32.0D;
	public static final double HEIGHT = 16.0D;
	/** The time stop lasts this long; the strike of the resume comes at this tick. */
	public static final int DURATION = 160;
	public static final int PULSE = 20;
	/** Banked damage is released ×1.5. */
	public static final double RELEASE = 1.5D;
	/** At most this many monsters are held in one bank (the Dewa target cap). */
	public static final int MAX_STOPPED = 64;
	/** Banks close and release one tick before the resume strike, so that strike itself lands normally. */
	public static final int CLOSE_AT = DURATION - 1;
	/** A bank older than this is ignored and dropped. */
	public static final int STALE = DURATION * 3;

	private static final String STATE_STARTED = "time_bank_started";

	/** Open banks by caster. */
	private static final Map<UUID, Bank> BANKS = new HashMap<>();
	/** Stopped monster → the bank that holds it. */
	private static final Map<UUID, Bank> STOPPED = new HashMap<>();
	private static boolean hooked;

	private static final class Bank {
		private final UUID caster;
		private final long expires;
		private boolean closed;
		private final Map<UUID, LivingEntity> victims = new LinkedHashMap<>();
		private final Map<UUID, Double> banked = new HashMap<>();
		/** The cast that opened the bank (its EXTRA1 marks every banked hit on the clients). */
		private @Nullable CastContext ctx;
		private int hits;

		private Bank(UUID caster, long expires) {
			this.caster = caster;
			this.expires = expires;
		}
	}

	public TimeBank() {
		hook();
	}

	private static synchronized void hook() {
		if (!hooked) {
			hooked = true;
			DamageHooks.register(DamageHooks.Stage.COUNTER, TimeBank::absorb);
		}
	}

	@Override
	public boolean cast(CastContext ctx) {
		if (!ctx.state().getBooleanOr(STATE_STARTED, false)) {
			open(ctx);
		}
		return true;
	}

	@Override
	public void tick(CastContext ctx) {
		if (!ctx.state().getBooleanOr(STATE_STARTED, false)) {
			open(ctx);
		}
		Bank bank = BANKS.get(ctx.caster().getUUID());
		if (bank == null || bank.closed) {
			return;
		}
		if (ctx.age() >= CLOSE_AT) {
			release(ctx, bank);
		} else if (ctx.age() % PULSE == 0) {
			collect(ctx, bank);
		}
	}

	@Override
	public void end(CastContext ctx, EndReason reason) {
		Bank bank = BANKS.get(ctx.caster().getUUID());
		if (bank == null) {
			return;
		}
		if (reason == EndReason.DEATH || reason == EndReason.LOGOUT || reason == EndReason.DIMENSION) {
			drop(bank);
		} else {
			release(ctx, bank);
		}
	}

	// ================================================================== the bank

	/** Opens the caster's bank (a newer cast replaces an older one) with the monsters the domain holds now. */
	private static void open(CastContext ctx) {
		ctx.state().putBoolean(STATE_STARTED, true);
		purge(ctx.level().getGameTime());
		Bank old = BANKS.get(ctx.caster().getUUID());
		if (old != null) {
			drop(old);
		}
		Bank bank = new Bank(ctx.caster().getUUID(), ctx.level().getGameTime() + STALE);
		bank.ctx = ctx;
		BANKS.put(bank.caster, bank);
		collect(ctx, bank);
	}

	/** Adds the non-boss monsters inside the domain to the stopped set (one entity query). */
	private static void collect(CastContext ctx, Bank bank) {
		boolean placed = ctx.areaRadius() > 0.0D;
		Vec3 centre = placed ? ctx.areaCentre() : ctx.caster().position();
		double radius = placed ? ctx.areaRadius() : ctx.area(RADIUS, RADIUS_CAP);
		for (LivingEntity target : ctx.hostiles(centre, radius, false)) {
			if (bank.victims.size() >= MAX_STOPPED) {
				break;
			}
			UUID id = target.getUUID();
			if (!target.isAlive() || target instanceof Player || TechniquePower.isBoss(target)
					|| Math.abs(target.getY() - centre.y) > HEIGHT || bank.victims.containsKey(id)) {
				continue;
			}
			Bank holder = STOPPED.get(id);
			if (holder != null && holder != bank && !holder.closed) {
				// Another caster's time already holds it.
				continue;
			}
			bank.victims.put(id, target);
			STOPPED.put(id, bank);
		}
	}

	/** DamageHooks modifier: the caster's hits on a monster its open bank holds are banked instead of landing. */
	private static float absorb(LivingEntity entity, DamageSource source, float amount) {
		if (STOPPED.isEmpty()) {
			return amount;
		}
		Bank bank = STOPPED.get(entity.getUUID());
		if (bank == null || bank.closed || entity.level().getGameTime() > bank.expires) {
			return amount;
		}
		Entity attacker = source.getEntity();
		if (attacker == null || !attacker.getUUID().equals(bank.caster)) {
			return amount;
		}
		bank.banked.merge(entity.getUUID(), (double) amount, Double::sum);
		bank.hits++;
		if (bank.ctx != null && !bank.ctx.ended()) {
			// EXTRA1: a banked hit, a gold mote on the stopped monster (aux = hits banked so far)
			Vec3 body = entity.getBoundingBox().getCenter();
			bank.ctx.fx(Phase.EXTRA1, body, attacker.position(), 1.0F, 0.0F, 1, 0, entity.getId(), 0, 1, bank.hits, 0);
		}
		return 0.0F;
	}

	/** Time resumes: closes the bank and deals every banked amount ×1.5 through the choke point. */
	private static void release(CastContext ctx, Bank bank) {
		if (bank.closed) {
			return;
		}
		List<Map.Entry<UUID, Double>> owed = new ArrayList<>(bank.banked.entrySet());
		Map<UUID, LivingEntity> victims = new HashMap<>(bank.victims);
		drop(bank);
		LivingEntity caster = ctx.caster();
		if (!caster.isAlive() || caster.isRemoved()) {
			return;
		}
		ServerPlayer player = ctx.player();
		for (Map.Entry<UUID, Double> entry : owed) {
			LivingEntity target = victims.get(entry.getKey());
			double amount = entry.getValue() * RELEASE;
			if (target == null || !target.isAlive() || target.level() != caster.level() || !(amount > 0.0D)) {
				continue;
			}
			DamageContext context = releaseContext(ctx, target, amount);
			if (player != null) {
				Techniques.hurt(player, context);
			} else {
				ctx.hurt(context);
			}
		}
	}

	/**
	 * A COUNTER hit that deals {@code amount} before the choke point's caps: the coefficient is the amount over the power
	 * {@code finalDamage} will use (legacy × 1.5 on trial mobs) and the mastery factor; pierce (armour already counted
	 * when the hit was banked), precise (the target was already hit).
	 */
	private static DamageContext releaseContext(CastContext ctx, LivingEntity target, double amount) {
		double power = ctx.power();
		double effective = power;
		ServerPlayer player = ctx.player();
		if (player != null && TechniquePower.isTrialMob(target)) {
			effective = TechniquePower.legacyD(Cultivators.get(player), ctx.spec().dao()) * TechniquePower.TRIAL_MOB_FACTOR;
		}
		double scale = effective * TechniquePower.masteryDamage(ctx.mastery());
		double coef = scale > 0.0D ? amount / scale : 0.0D;
		return new DamageContext(DamageContext.Kind.COUNTER, ctx.caster(), target, ctx.spec(), coef, coef, 0.0D, power,
				ctx.mastery(), 1.0D, true, true, ctx.ledger());
	}

	/** Closes a bank without releasing it and frees its monsters. */
	private static void drop(Bank bank) {
		bank.closed = true;
		for (UUID id : bank.victims.keySet()) {
			STOPPED.remove(id, bank);
		}
		bank.victims.clear();
		bank.banked.clear();
		BANKS.remove(bank.caster, bank);
	}

	/** Drops banks that ran out, or that come from a world whose clock is far ahead (the runner normally ends them). */
	private static void purge(long now) {
		List<Bank> stale = new ArrayList<>();
		for (Bank bank : BANKS.values()) {
			if (now > bank.expires || bank.expires - now > 2L * STALE) {
				stale.add(bank);
			}
		}
		for (Bank bank : stale) {
			drop(bank);
		}
	}
}
