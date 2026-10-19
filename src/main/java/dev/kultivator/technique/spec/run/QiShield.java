package dev.kultivator.technique.spec.run;

import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Step;
import dev.kultivator.trial.HeartDemonEntity;
import dev.kultivator.util.DamageHooks;
import dev.kultivator.util.DeathGuard;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The defensive side of techniques (DESIGN 22.3.3, 22.14.3, 22.14.6): Perisai Qi (a pool ≤ 2 × max health), damage
 * reduction (DR, combined multiplicatively under 80% / Dewa Abadi 85%, heart-demon damage under 50%, technique-granted
 * Resistance counted inside the cap), DODGE (≤ 50%), COUNTER stores and their release, and CHEAT_DEATH charges through
 * the spine {@code DeathGuard} (stage TECHNIQUE, shared 10-minute lockout). Registered once into {@code DamageHooks}
 * (stages DR, SHIELD, COUNTER); {@code #kultivator:unblockable} never reaches them.
 */
public final class QiShield {

	private static final class DrEntry {
		final double share;
		final double minShare;
		final Payload.Facing facing;
		final boolean hpScaled;
		long until;

		DrEntry(double share, double minShare, Payload.Facing facing, boolean hpScaled, long until) {
			this.share = share;
			this.minShare = minShare;
			this.facing = facing;
			this.hpScaled = hpScaled;
			this.until = until;
		}
	}

	private static final class CounterEntry {
		final Caster source;
		final Payload.Counter counter;
		final long until;
		double stored;
		int hits;
		boolean released;

		CounterEntry(Caster source, Payload.Counter counter, long until) {
			this.source = source;
			this.counter = counter;
			this.until = until;
		}
	}

	private static final class CheatEntry {
		final Caster source;
		int charges;
		final long until;
		final double healthShare;
		final @Nullable Step onTrigger;

		CheatEntry(Caster source, int charges, long until, double healthShare, @Nullable Step onTrigger) {
			this.source = source;
			this.charges = charges;
			this.until = until;
			this.healthShare = healthShare;
			this.onTrigger = onTrigger;
		}
	}

	private static final class State {
		/** Who holds it: casters and allies alike (a pet, a villager, a disciple that never cast). */
		LivingEntity holder;
		double shield;
		double shieldMax;
		long shieldUntil;
		final List<DrEntry> dr = new ArrayList<>();
		double dodge;
		long dodgeUntil;
		final List<CounterEntry> counters = new ArrayList<>();
		final List<CheatEntry> cheats = new ArrayList<>();

		boolean idle(long now) {
			return (shield <= 0.0D || now >= shieldUntil) && dr.isEmpty() && now >= dodgeUntil && counters.isEmpty() && cheats.isEmpty();
		}
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	static void init() {
		DamageHooks.register(DamageHooks.Stage.DR, QiShield::reduce);
		DamageHooks.register(DamageHooks.Stage.SHIELD, QiShield::absorb);
		DamageHooks.register(DamageHooks.Stage.COUNTER, QiShield::store);
		DeathGuard.register(DeathGuard.Stage.TECHNIQUE, QiShield::cheatDeath);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
	}

	private static State state(LivingEntity who) {
		State state = STATES.computeIfAbsent(who.getUUID(), key -> new State());
		state.holder = who;
		return state;
	}

	// ================================================================== adding

	/** Perisai Qi: adds {@code amount} (capped at 2 × max health) lasting {@code ticks}. */
	static void shield(LivingEntity who, double amount, int ticks) {
		if (!(amount > 0.0D) || ticks <= 0) {
			return;
		}
		State state = state(who);
		long now = who.level().getGameTime();
		double cap = who.getMaxHealth() * TechniquePower.SHIELD_CAP;
		state.shield = Math.min(cap, (now < state.shieldUntil ? state.shield : 0.0D) + amount);
		state.shieldMax = Math.max(state.shield, Math.min(cap, now < state.shieldUntil ? state.shieldMax : state.shield));
		state.shieldUntil = Math.max(state.shieldUntil, now + ticks);
	}

	static void dr(LivingEntity who, double share, Payload.Dr dr, int ticks) {
		if (!(share > 0.0D) || ticks <= 0) {
			return;
		}
		long until = who.level().getGameTime() + ticks;
		State state = state(who);
		for (DrEntry entry : state.dr) {
			// the same DR again (a pulse re-applying it) refreshes instead of stacking with itself
			if (entry.share == share && entry.minShare == dr.minShare() && entry.facing == dr.facing() && entry.hpScaled == dr.hpScaled()) {
				entry.until = Math.max(entry.until, until);
				return;
			}
		}
		state.dr.add(new DrEntry(share, dr.minShare(), dr.facing(), dr.hpScaled(), until));
	}

	static void dodge(LivingEntity who, double chance, int ticks) {
		if (!(chance > 0.0D) || ticks <= 0) {
			return;
		}
		State state = state(who);
		state.dodge = Math.min(TechniquePower.DODGE_CAP, Math.max(state.dodge, chance));
		state.dodgeUntil = Math.max(state.dodgeUntil, who.level().getGameTime() + ticks);
	}

	static void counter(Caster source, LivingEntity who, Payload.Counter counter, int window) {
		if (window <= 0) {
			return;
		}
		state(who).counters.add(new CounterEntry(source, counter, who.level().getGameTime() + window));
	}

	static void cheatDeath(Caster source, LivingEntity who, int charges, int ticks, double healthShare, @Nullable Step onTrigger) {
		if (charges <= 0 || ticks <= 0 || !(who instanceof ServerPlayer)) {
			return;
		}
		State state = state(who);
		state.cheats.removeIf(entry -> entry.source.spec() == source.spec());
		state.cheats.add(new CheatEntry(source, charges, who.level().getGameTime() + ticks, healthShare, onTrigger));
	}

	/** The current Perisai Qi pool (0 when none) and its size when it was last filled. */
	public static double[] shieldOf(LivingEntity who) {
		State state = STATES.get(who.getUUID());
		if (state == null || who.level().getGameTime() >= state.shieldUntil) {
			return new double[]{0.0D, 0.0D};
		}
		return new double[]{state.shield, state.shieldMax};
	}

	// ================================================================== damage hooks

	/**
	 * Combined DR and DODGE (22.14.3): everything a technique grants (DR entries, DODGE, technique Resistance) stays under
	 * one cap, 80% (Dewa Abadi 85%), heart demons 50%. Vanilla has already applied Resistance when this runs: technique
	 * Resistance counts inside the cap, and when it alone passes the cap the excess is given back. DODGE is folded in by
	 * lowering its chance so the expected reduction never passes the cap.
	 */
	private static float reduce(LivingEntity entity, DamageSource source, float amount) {
		State state = STATES.get(entity.getUUID());
		boolean heartDemon = source.getEntity() instanceof HeartDemonEntity;
		double cap = heartDemon ? TechniquePower.DR_CAP_HEART_DEMON : drCap(entity);
		int resistance = Boons.resistance(entity);
		double resisted = resistance >= 0 ? Math.min(0.8D, 0.2D * (resistance + 1)) : 0.0D;
		if (resisted > cap) {
			// technique Resistance alone took more than the cap (Iron Body's III vs a heart demon): give the excess back
			return (float) (amount * (1.0D - cap) / (1.0D - resisted));
		}
		if (state == null) {
			return amount;
		}
		long now = entity.level().getGameTime();
		// the share of the hit still allowed to be reduced after Resistance
		double allowed = resisted >= cap ? 0.0D : 1.0D - (1.0D - cap) / (1.0D - resisted);
		double keep = drKeep(entity, state, source, now);
		double reduction = Math.min(Math.max(0.0D, allowed), 1.0D - keep);
		double dodge = state.dodge > 0.0D && now < state.dodgeUntil && !heartDemon ? Math.min(TechniquePower.DODGE_CAP, state.dodge) : 0.0D;
		if (dodge > 0.0D && reduction < 1.0D) {
			// 1 − (1 − dodge)(1 − reduction) ≤ allowed
			double room = Math.max(0.0D, 1.0D - (1.0D - Math.max(0.0D, allowed)) / (1.0D - reduction));
			if (entity.getRandom().nextDouble() < Math.min(dodge, room)) {
				return 0.0F;
			}
		}
		return (float) (amount * (1.0D - reduction));
	}

	/** The product of what the active DR entries let through (1 = none); expired entries are dropped. */
	private static double drKeep(LivingEntity entity, State state, DamageSource source, long now) {
		if (state.dr.isEmpty()) {
			return 1.0D;
		}
		double keep = 1.0D;
		Vec3 attackerPos = source.getSourcePosition();
		for (Iterator<DrEntry> it = state.dr.iterator(); it.hasNext(); ) {
			DrEntry entry = it.next();
			if (now >= entry.until) {
				it.remove();
				continue;
			}
			if (entry.facing != Payload.Facing.ALL && attackerPos != null) {
				Vec3 to = attackerPos.subtract(entity.position()).horizontal();
				Vec3 look = entity.getViewVector(1.0F).horizontal();
				if (to.lengthSqr() > 1.0E-4D && look.lengthSqr() > 1.0E-4D) {
					boolean front = to.normalize().dot(look.normalize()) >= 0.0D;
					if (front != (entry.facing == Payload.Facing.FRONT)) {
						continue;
					}
				}
			}
			double share = entry.share;
			if (entry.hpScaled) {
				double missing = 1.0D - Math.max(0.0D, Math.min(1.0D, entity.getHealth() / Math.max(1.0F, entity.getMaxHealth())));
				share = entry.minShare + (entry.share - entry.minShare) * missing;
			}
			keep *= 1.0D - Math.max(0.0D, Math.min(0.95D, share));
		}
		return keep;
	}

	private static double drCap(LivingEntity entity) {
		Realm realm = null;
		if (entity instanceof ServerPlayer player) {
			realm = dev.kultivator.data.Cultivators.get(player).realm();
		} else if (entity instanceof DiscipleEntity disciple) {
			realm = disciple.data().realm();
		}
		return realm == Realm.TRUE_IMMORTAL ? TechniquePower.DR_CAP_DEWA : TechniquePower.DR_CAP;
	}

	/** Perisai Qi absorbs what is left. */
	private static float absorb(LivingEntity entity, DamageSource source, float amount) {
		State state = STATES.get(entity.getUUID());
		if (state == null || state.shield <= 0.0D || entity.level().getGameTime() >= state.shieldUntil) {
			return amount;
		}
		double taken = Math.min(state.shield, amount);
		state.shield -= taken;
		return (float) (amount - taken);
	}

	/** COUNTER stores: DAMAGE_TAKEN keeps a share, FRONT_MELEE only melee from the front, CANCEL_ALL cancels up to a cap. */
	private static float store(LivingEntity entity, DamageSource source, float amount) {
		State state = STATES.get(entity.getUUID());
		if (state == null || state.counters.isEmpty()) {
			return amount;
		}
		long now = entity.level().getGameTime();
		float result = amount;
		for (CounterEntry entry : state.counters) {
			if (entry.released || now >= entry.until) {
				continue;
			}
			Payload.Counter counter = entry.counter;
			double before = entry.stored;
			switch (counter.kind()) {
				case FRONT_MELEE -> {
					Entity attacker = source.getEntity();
					boolean melee = (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.PLAYER_ATTACK)) && attacker != null
							&& source.getDirectEntity() == attacker;
					if (!melee) {
						continue;
					}
					Vec3 to = attacker.position().subtract(entity.position()).horizontal();
					if (to.lengthSqr() > 1.0E-4D && to.normalize().dot(entity.getViewVector(1.0F).horizontal().normalize()) < 0.0D) {
						continue;
					}
					if (counter.cap() > 0.0D && entry.hits >= counter.cap()) {
						continue;
					}
					entry.hits++;
					entry.stored += result * counter.store();
					result *= (float) Math.max(0.0D, 1.0D - counter.store());
				}
				case CANCEL_ALL -> {
					double cap = counter.cap() * entity.getMaxHealth();
					double room = Math.max(0.0D, cap - entry.stored);
					double cancelled = Math.min(room, result);
					entry.stored += cancelled;
					result -= (float) cancelled;
				}
				default -> {
					if (counter.cap() > 0.0D && entry.hits >= counter.cap()) {
						continue;
					}
					entry.hits++;
					entry.stored += result * counter.store();
				}
			}
			if (entry.stored > before && entry.source.casterValid()) {
				// EXTRA6, the counter phase: a blow taken into the store (a mirror ripple, a moon glow), aux = blows stored
				Entity attacker = source.getEntity();
				Vec3 from = attacker != null ? attacker.getBoundingBox().getCenter() : entity.getBoundingBox().getCenter();
				entry.source.fx(dev.kultivator.technique.spec.dsl.Phase.EXTRA6, entity.getBoundingBox().getCenter(), from, 1.0F, 0.0F, 1, 0,
						attacker != null ? attacker.getId() : -1, 0, 1, entry.hits, 0);
			}
			if (counter.earlyAt() > 0.0D && entry.stored >= counter.earlyAt() * entity.getMaxHealth()) {
				release(entry);
			}
		}
		return Math.max(0.0F, result);
	}

	private static void release(CounterEntry entry) {
		if (entry.released) {
			return;
		}
		entry.released = true;
		if (entry.counter.release() != null && entry.source.casterValid()) {
			entry.source.runRelease(entry.counter.release(), entry.stored);
		}
	}

	// ================================================================== cheat death

	private static boolean cheatDeath(ServerPlayer player, DamageSource source, float amount) {
		State state = STATES.get(player.getUUID());
		if (state == null || state.cheats.isEmpty()) {
			return false;
		}
		long now = player.level().getGameTime();
		for (Iterator<CheatEntry> it = state.cheats.iterator(); it.hasNext(); ) {
			CheatEntry entry = it.next();
			if (now >= entry.until || entry.charges <= 0) {
				it.remove();
				continue;
			}
			float health = entry.healthShare > 0.0D ? (float) Math.min(player.getMaxHealth(), player.getMaxHealth() * entry.healthShare) : 1.0F;
			player.setHealth(Math.max(1.0F, health));
			if (--entry.charges <= 0) {
				it.remove();
			}
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.3F);
			dev.kultivator.util.Msg.chat(player, net.minecraft.network.chat.Component.translatable("kultivator.teknik.cheat_death",
					entry.source.spec().displayName()).withColor(0xFFD54A));
			entry.source.fx(dev.kultivator.technique.spec.dsl.Phase.EXTRA7, player.position(), player.position(), 1.0F, 0.0F, 1, 20);
			if (entry.onTrigger != null && entry.source.casterValid()) {
				entry.source.runHook(entry.onTrigger, source.getEntity() instanceof LivingEntity living ? living : null);
			}
			return true;
		}
		return false;
	}

	// ================================================================== tick

	static void tick() {
		if (STATES.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, State> next = it.next();
			State state = next.getValue();
			LivingEntity holder = TechniqueRunner.current(state.holder);
			if (holder == null) {
				// The holder left or died: everything here is session-only.
				it.remove();
				continue;
			}
			state.holder = holder;
			long now = holder.level().getGameTime();
			for (Iterator<CounterEntry> counters = state.counters.iterator(); counters.hasNext(); ) {
				CounterEntry entry = counters.next();
				if (entry.released) {
					counters.remove();
				} else if (now >= entry.until) {
					release(entry);
					counters.remove();
				}
			}
			state.dr.removeIf(entry -> now >= entry.until);
			state.cheats.removeIf(entry -> now >= entry.until || entry.charges <= 0);
			if (state.idle(now)) {
				it.remove();
			}
		}
	}

	static void forget(LivingEntity who) {
		STATES.remove(who.getUUID());
	}

	private QiShield() {
	}
}
