package dev.kultivator.disciple;

import dev.kultivator.Kultivator;
import dev.kultivator.util.DamageEstimate;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registered duels between two living entities (DESIGN 20.2.8): tournament bouts, the Hundred Dynasties War, Void Realm
 * phantoms and Tanding dengan Guru. While a pair duels, the two partners may hurt each other whatever the normal
 * targeting rules say ({@code DiscipleEntity.mayAttack}, {@code isInvulnerableTo}, {@code DiscipleTechniques.mayHit} and
 * {@code Targeting.isHostileTo} ask here first). Damage between partners (and harm without an attacker, like a fall or
 * burning after a blow) never takes a partner below 10% of max health: the hit that would is clamped to that line and
 * ends the duel with a {@link Reason#DEFEAT}. A duel also ends at its time limit (the higher health share wins), when a
 * partner leaves the world or another dimension ({@link Reason#CANCELLED}), or when its owner ends it. When it ends,
 * both partners lose the fire, freeze, wither, poison and prison hold the bout left on them, and for 5 s more the former
 * partner's blows and harm without an attacker still stop at the 10% line. Session only.
 */
public final class DuelRules {

	/** A duel ends when a partner would drop below this share of max health. */
	public static final float END_FRACTION = 0.10F;

	/** Why a duel ended. */
	public enum Reason {
		DEFEAT,
		TIMEOUT,
		LEFT_RING,
		CANCELLED
	}

	/** Told once when the duel ends. Winner and loser are null for {@link Reason#CANCELLED}. */
	@FunctionalInterface
	public interface Listener {
		void onEnd(@Nullable LivingEntity winner, @Nullable LivingEntity loser, Reason reason);
	}

	private static final class Duel {
		final LivingEntity a;
		final LivingEntity b;
		final int maxTicks;
		final Listener listener;
		int ticks;
		boolean ended;

		Duel(LivingEntity a, LivingEntity b, int maxTicks, Listener listener) {
			this.a = a;
			this.b = b;
			this.maxTicks = maxTicks;
			this.listener = listener;
		}

		boolean involves(Entity entity) {
			return entity == a || entity == b;
		}

		LivingEntity partnerOf(Entity entity) {
			return entity == a ? b : a;
		}
	}

	/** After a duel: the former partner's blows and harm without an attacker still stop at 10% for this long (ticks). */
	public static final int GRACE_TICKS = 100;
	private static final Identifier PRISON_ID = Kultivator.id("restriction_prison");

	/** A partner of a finished duel, still under the 10% line until {@code until} (game time). */
	private record Grace(UUID partner, long until) {
	}

	/** Both partners' UUIDs point to the same duel. */
	private static final Map<UUID, Duel> DUELS = new HashMap<>();
	private static final Map<UUID, Grace> GRACE = new HashMap<>();

	/** Called once from {@link Disciples#init()}. */
	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(DuelRules::allowDamage);
		ServerTickEvents.END_SERVER_TICK.register(DuelRules::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DUELS.clear();
			GRACE.clear();
		});
	}

	// ================================================================== API

	/**
	 * Starts a duel between {@code a} and {@code b} for at most {@code maxTicks} ticks (0 or less: no limit). False when
	 * one of them is already duelling, they are the same, dead, or in different dimensions.
	 */
	public static boolean begin(LivingEntity a, LivingEntity b, int maxTicks, Listener listener) {
		if (a == null || b == null || a == b || !a.isAlive() || !b.isAlive() || a.isRemoved() || b.isRemoved()
				|| a.level() != b.level() || a.level().isClientSide() || inDuel(a) || inDuel(b) || listener == null) {
			return false;
		}
		// Phase 8 (DESIGN 25.2 item 12): no duel inside a secret realm or with a player in a run.
		if (dev.kultivator.alam.Alam.isRealmLevel(a.level()) || a instanceof net.minecraft.server.level.ServerPlayer pa && dev.kultivator.alam.Alam.inRun(pa)
				|| b instanceof net.minecraft.server.level.ServerPlayer pb && dev.kultivator.alam.Alam.inRun(pb)) {
			return false;
		}
		// DESIGN 40.2.3: a child (visible age group ANAK) is never a duellist.
		if (dev.kultivator.sosok.SosokApi.usia(a) == dev.kultivator.sosok.Sosok.ANAK
				|| dev.kultivator.sosok.SosokApi.usia(b) == dev.kultivator.sosok.Sosok.ANAK) {
			return false;
		}
		Duel duel = new Duel(a, b, maxTicks, listener);
		DUELS.put(a.getUUID(), duel);
		DUELS.put(b.getUUID(), duel);
		return true;
	}

	/** True when {@code a} and {@code b} are the two partners of a running duel. */
	public static boolean dueling(@Nullable Entity a, @Nullable Entity b) {
		if (DUELS.isEmpty() || a == null || b == null || a == b) {
			return false;
		}
		Duel duel = DUELS.get(a.getUUID());
		return duel != null && !duel.ended && duel.involves(a) && duel.involves(b);
	}

	/** True while {@code entity} is in a running duel. */
	public static boolean inDuel(@Nullable Entity entity) {
		if (DUELS.isEmpty() || entity == null) {
			return false;
		}
		Duel duel = DUELS.get(entity.getUUID());
		return duel != null && !duel.ended && duel.involves(entity);
	}

	/** The other partner of {@code entity}'s duel, or null. */
	public static @Nullable LivingEntity partnerOf(@Nullable Entity entity) {
		if (DUELS.isEmpty() || entity == null) {
			return null;
		}
		Duel duel = DUELS.get(entity.getUUID());
		return duel != null && !duel.ended && duel.involves(entity) ? duel.partnerOf(entity) : null;
	}

	/** Ticks the duel has run, or -1 when {@code entity} is not duelling. */
	public static int ticks(@Nullable Entity entity) {
		Duel duel = entity == null ? null : DUELS.get(entity.getUUID());
		return duel != null && !duel.ended && duel.involves(entity) ? duel.ticks : -1;
	}

	/**
	 * Ends {@code either}'s duel. With a {@code winner} the other partner loses. Without one: {@link Reason#TIMEOUT}
	 * picks the higher health share, {@link Reason#DEFEAT} and {@link Reason#LEFT_RING} make {@code either} the loser,
	 * {@link Reason#CANCELLED} has no winner.
	 */
	public static void end(LivingEntity either, @Nullable LivingEntity winner, Reason reason) {
		Duel duel = either == null ? null : DUELS.get(either.getUUID());
		if (duel == null || duel.ended || !duel.involves(either)) {
			return;
		}
		if (winner != null && duel.involves(winner)) {
			finish(duel, winner, duel.partnerOf(winner), reason);
			return;
		}
		switch (reason) {
			case TIMEOUT -> timeout(duel);
			case DEFEAT, LEFT_RING -> finish(duel, duel.partnerOf(either), either, reason);
			case CANCELLED -> finish(duel, null, null, Reason.CANCELLED);
		}
	}

	/** Ends {@code either}'s duel without a result. */
	public static void cancel(LivingEntity either) {
		end(either, null, Reason.CANCELLED);
	}

	// ================================================================== rules

	/**
	 * Clamps the partner's blow (or harm without any attacker during the duel) so the victim keeps 10% of max health;
	 * the clamped hit ends the duel. Read before vanilla applies armour; the estimate is an upper bound.
	 */
	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (DUELS.isEmpty() && GRACE.isEmpty()) {
			return true;
		}
		Duel duel = DUELS.get(victim.getUUID());
		if (duel == null || duel.ended || !duel.involves(victim)) {
			return graceAllows(victim, source, amount);
		}
		LivingEntity partner = duel.partnerOf(victim);
		Entity attacker = source.getEntity();
		boolean environmental = attacker == null && source.getDirectEntity() == null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		if (attacker != partner && !environmental || !(amount > 0.0F)) {
			return true;
		}
		float floor = victim.getMaxHealth() * END_FRACTION;
		float after = victim.getHealth() + victim.getAbsorptionAmount() - DamageEstimate.upperBound(victim, source, amount);
		if (after >= floor) {
			return true;
		}
		// The clamped hit: down to the 10% line (never up), and the duel is decided.
		victim.setAbsorptionAmount(0.0F);
		if (victim.getHealth() > floor) {
			victim.setHealth(floor);
		}
		finish(duel, partner, victim, Reason.DEFEAT);
		return false;
	}

	/**
	 * Just after a duel: lingering harm (fire, wither, freeze, the former partner's last blows) keeps the victim at 10%
	 * of max health, so a finished bout never kills ("duels clamp at 10%").
	 */
	private static boolean graceAllows(LivingEntity victim, DamageSource source, float amount) {
		Grace grace = GRACE.isEmpty() ? null : GRACE.get(victim.getUUID());
		if (grace == null) {
			return true;
		}
		if (victim.level().getGameTime() > grace.until()) {
			GRACE.remove(victim.getUUID());
			return true;
		}
		Entity attacker = source.getEntity();
		boolean environmental = attacker == null && source.getDirectEntity() == null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		boolean partner = attacker != null && attacker.getUUID().equals(grace.partner());
		if (!(environmental || partner) || !(amount > 0.0F)) {
			return true;
		}
		float floor = victim.getMaxHealth() * END_FRACTION;
		float after = victim.getHealth() + victim.getAbsorptionAmount() - DamageEstimate.upperBound(victim, source, amount);
		if (after >= floor) {
			return true;
		}
		victim.setAbsorptionAmount(0.0F);
		if (victim.getHealth() > floor) {
			victim.setHealth(floor);
		}
		return false;
	}

	/** The partners' lingering harm ends with the bout: fire, freeze, wither, poison and the restriction prison's hold. */
	private static void soothe(LivingEntity entity) {
		if (entity.isRemoved() || !entity.isAlive()) {
			return;
		}
		entity.clearFire();
		entity.setTicksFrozen(0);
		entity.removeEffect(MobEffects.WITHER);
		entity.removeEffect(MobEffects.POISON);
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(PRISON_ID);
		}
	}

	private static void tick(MinecraftServer server) {
		if (!GRACE.isEmpty() && server.getTickCount() % 20 == 0) {
			long now = server.overworld().getGameTime();
			GRACE.values().removeIf(grace -> now > grace.until() + GRACE_TICKS);
		}
		if (DUELS.isEmpty()) {
			return;
		}
		List<Duel> running = new ArrayList<>(new LinkedHashSet<>(DUELS.values()));
		for (Duel duel : running) {
			if (duel.ended) {
				continue;
			}
			if (gone(duel.a) || gone(duel.b) || duel.a.level() != duel.b.level()) {
				finish(duel, null, null, Reason.CANCELLED);
				continue;
			}
			duel.ticks++;
			if (duel.maxTicks > 0 && duel.ticks >= duel.maxTicks) {
				timeout(duel);
			}
		}
	}

	private static boolean gone(LivingEntity entity) {
		return entity.isRemoved() || !entity.isAlive()
				|| entity instanceof ServerPlayer player && (player.hasDisconnected() || player.isSpectator());
	}

	/** The higher share of max health wins (a tie goes to the first partner). */
	private static void timeout(Duel duel) {
		float a = duel.a.getHealth() / Math.max(1.0F, duel.a.getMaxHealth());
		float b = duel.b.getHealth() / Math.max(1.0F, duel.b.getMaxHealth());
		if (b > a) {
			finish(duel, duel.b, duel.a, Reason.TIMEOUT);
		} else {
			finish(duel, duel.a, duel.b, Reason.TIMEOUT);
		}
	}

	private static void finish(Duel duel, @Nullable LivingEntity winner, @Nullable LivingEntity loser, Reason reason) {
		if (duel.ended) {
			return;
		}
		duel.ended = true;
		DUELS.remove(duel.a.getUUID(), duel);
		DUELS.remove(duel.b.getUUID(), duel);
		soothe(duel.a);
		soothe(duel.b);
		long until = duel.a.level().getGameTime() + GRACE_TICKS;
		GRACE.put(duel.a.getUUID(), new Grace(duel.b.getUUID(), until));
		GRACE.put(duel.b.getUUID(), new Grace(duel.a.getUUID(), until));
		try {
			duel.listener.onEnd(winner, loser, reason);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("A duel listener failed", exception);
		}
	}

	private DuelRules() {
	}
}
