package dev.kultivator.duel;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.clash.ClashClock;
import dev.kultivator.duel.clash.ClashDamage;
import dev.kultivator.duel.clash.Fighter;
import dev.kultivator.duel.clash.Fighters;
import dev.kultivator.duel.clash.Reactive;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.sect.Sparring;
import dev.kultivator.util.DamageHooks;
import dev.kultivator.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * The public hooks of the sword duel (C35.S2) and its damage filter (35.3.14), registered by {@code Duel.init} in the
 * phase {@code kultivator:duel_clash} before the default phase, so a deflect cancels a blow before the {@code DuelRules}
 * 10% clamp sees it. Order of the filter:
 * <ol>
 * <li>the clash layer's own damage ({@code ClashDamage.resolving()}) passes;</li>
 * <li>formal duels: no damage between the two outside FIGHT; others meddling are stopped ("Jangan ikut campur"), a wild
 * monster is not (the duellists may postpone); in FIGHT the partners' direct vanilla blows are refused from KLASIK
 * clients and from AI duellists (they fight through the clash layer), techniques are refused under PEDANG_SAJA and can be
 * deflected under JURUS_BEBAS, projectiles can be caught by the Companion Sword;</li>
 * <li>everything else melee goes to {@link Reactive#melee} (Better Combat hits, reactive defence, armed clash, posture
 * mobs).</li>
 * </ol>
 */
public final class DuelHooks {

	private static final int MEDDLE_MESSAGE_GAP = 60;
	private static final java.util.Map<java.util.UUID, Long> MEDDLE_TOLD = new java.util.HashMap<>();

	// ================================================================ API (C35.S2)

	/** Combat Roll's roll (U's {@code CombatRollCompat}): the roller gets the 6 dodge ticks. */
	public static void onRoll(ServerPlayer player, Vec3 direction) {
		if (DuelConfig.get().enabled && DuelConfig.get().combatRollCompat && player.isAlive()) {
			Fighters.roll(player);
		}
	}

	/**
	 * A mob announces a perilous blow of {@code perilousKind} ({@code DuelIds.PERIL_GUNUNG}, {@code PERIL_MAUT},
	 * {@code PERIL_SAPU}) landing at server tick {@code impactTick}: the 危 mark shows, the reactive defence applies the
	 * perilous rules. Optional for elites and primordials (35.3.13).
	 */
	public static void telegraph(LivingEntity entity, int perilousKind, long impactTick) {
		if (entity != null && perilousKind != DuelIds.PERIL_NONE && impactTick > ClashClock.now()) {
			Fighters.telegraph(entity, perilousKind, impactTick);
		}
	}

	/** A Better Combat swing reported on the server side (the client normally sends {@code DuelInput BC_SWING}). */
	public static void bcSwing(ServerPlayer player, int dir, int windup, boolean heavy, boolean offhand) {
		Fighters.bcSwing(player, dir, windup, heavy, offhand);
	}

	// ================================================================ the damage filter

	/** ALLOW_DAMAGE in the phase {@code kultivator:duel_clash}: false cancels the blow. */
	static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		DuelConfig config = DuelConfig.get();
		if (!config.enabled || ClashDamage.resolving() || victim.level().isClientSide()) {
			return true;
		}
		Entity cause = source.getEntity();
		Entity direct = source.getDirectEntity();
		LivingEntity attacker = cause instanceof LivingEntity living ? living : null;
		long now = ClashClock.now();
		try {
			// Formal duels.
			DuelSession session = DuelSessions.byEntity(victim);
			if (session != null && attacker != null && attacker != victim) {
				Boolean answer = formal(session, victim, attacker, direct, source, now);
				if (answer != null) {
					return answer;
				}
			}
			DuelSession own = attacker != null ? DuelSessions.byEntity(attacker) : null;
			if (own != null && own != session && own.phase() != DuelSession.Phase.ENDED && !(victim instanceof Enemy)) {
				// A duellist does not strike bystanders while the duel runs (monsters it may).
				return false;
			}
			// Sparring players found each other through a blow: their pair gets the clash layer.
			if (attacker instanceof ServerPlayer a && victim instanceof ServerPlayer b && Sparring.areSparring(a, b)) {
				Fighters.noteSparring(a, b);
			}
			if (attacker != null && direct == attacker && attacker != victim && !DamageHooks.isTechnique(source)) {
				return Reactive.melee(victim, attacker, source, amount) == Reactive.ALLOW;
			}
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.error("Duel damage filter failed; letting the blow through", exception);
		}
		return true;
	}

	/** The formal duel rules for a blow on a duellist; null = not decided here (the general layer continues). */
	private static Boolean formal(DuelSession session, LivingEntity victim, LivingEntity attacker, Entity direct, DamageSource source, long now) {
		LivingEntity partner = session.partnerOf(victim);
		DuelSession.Phase phase = session.phase();
		if (attacker == partner) {
			if (phase != DuelSession.Phase.FIGHT) {
				// Before the first strike and after the decision: nothing between the two.
				return false;
			}
			if (DamageHooks.isTechnique(source)) {
				if (session.rules().jurus() == DuelRuleSet.JurusRule.PEDANG_SAJA) {
					return false;
				}
				return Reactive.technique(victim, attacker, source) == Reactive.ALLOW;
			}
			if (direct == attacker) {
				if (attacker instanceof ServerPlayer player) {
					Fighter fighter = Fighters.get(player);
					if (fighter.inputMode() == DuelIds.INPUT_KLASIK && DuelConfig.get().blockVanillaHitsInClassic) {
						// A KLASIK client never sends vanilla hits in a duel: these are macros.
						return false;
					}
					if (Fighters.disabled(player)) {
						return false;
					}
					return null;
				}
				// AI duellists strike only through the clash layer.
				return false;
			}
			return Reactive.projectile(victim, source) == Reactive.ALLOW;
		}
		if (phase == DuelSession.Phase.ENDED) {
			return null;
		}
		// Somebody else.
		if (attacker instanceof Enemy && !(attacker instanceof ServerPlayer) && !(attacker instanceof OwnableEntity)
				&& !(attacker instanceof DiscipleEntity)) {
			// A wild monster is not stopped; the duellists may postpone.
			session.wildHurt(now);
			return null;
		}
		if (attacker instanceof ServerPlayer meddler && (now - MEDDLE_TOLD.getOrDefault(meddler.getUUID(), Long.MIN_VALUE / 4)) > MEDDLE_MESSAGE_GAP) {
			MEDDLE_TOLD.put(meddler.getUUID(), now);
			Msg.actionBar(meddler, Component.translatable("kultivator.duel.msg.meddle").withColor(0xE06050));
		}
		return false;
	}

	/** AFTER_DAMAGE: the damage a melee blow did (armed clash). */
	static void afterDamage(LivingEntity victim, DamageSource source, float taken) {
		if (DuelConfig.get().enabled && taken > 0.0F) {
			Reactive.afterDamage(victim, source, taken);
		}
	}

	static void clear() {
		MEDDLE_TOLD.clear();
	}

	private DuelHooks() {
	}
}
