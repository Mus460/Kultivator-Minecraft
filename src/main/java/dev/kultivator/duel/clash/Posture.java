package dev.kultivator.duel.clash;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.ai.DuelistProfile;
import dev.kultivator.duel.ai.DuelistRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Posture ("Postur", Sekiro style, DESIGN 35.3.6). Max {@code 100 + 8 × realm + 2 × stage} (+15% with the Body Dao;
 * Immortal Ascension +3 per stage on top), NPCs from their profile's rank, mobs from {@link MobGuard}. Grows by the table
 * of 35.3.5, recovers after 30 ticks without gain at {@code (0.25 + 0.75 × health share)} per tick, ×1.5 while guarding,
 * 0 while staggered. Full: Postur Jebol (stagger 30 ticks), then back to 50%.
 */
public final class Posture {

	/** Recovery starts this long after the last gain. */
	public static final int RECOVER_DELAY = 30;
	public static final float BODY_DAO_BONUS = 1.15F;
	/** Sword Dao comprehension from which the posture dealt is ×1.1. */
	public static final double SWORD_DAO_POSTURE = 50.0D;
	/** The defender with the Body Dao takes ×0.9 posture. */
	public static final float BODY_DAO_TAKEN = 0.9F;

	/** The max posture of {@code entity}. */
	public static float max(LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			CultivationData data = Cultivators.get(player);
			return ofRank(data.rank(), data.dao() == Dao.BODY);
		}
		if (entity instanceof DiscipleEntity disciple) {
			DiscipleData data = disciple.data();
			return ofRank(data.rank(), data.dao() == Dao.BODY);
		}
		DuelistProfile profile = DuelistRegistry.profile(entity);
		if (profile != null) {
			return ofRank(Rank.ofStep(profile.rankStep()), false);
		}
		return MobGuard.maxPosture(entity);
	}

	/** {@code 100 + 8 × realm + 2 × stage}, Immortal Ascension +3 per stage of its layers, Body Dao ×1.15. */
	public static float ofRank(Rank rank, boolean bodyDao) {
		Realm realm = rank.realm();
		float max = 100.0F + 8.0F * realm.ordinal() + 2.0F * rank.stage();
		if (realm == Realm.IMMORTAL_ASCENSION) {
			max += 3.0F * (rank.layer() * realm.stages() + rank.stage());
		}
		return bodyDao ? max * BODY_DAO_BONUS : max;
	}

	/** The realm ordinal of {@code entity} (players, disciples, profiled NPCs; mobs 0, elites their realm). */
	public static int realm(LivingEntity entity) {
		Rank rank = rank(entity);
		return rank == null ? MobGuard.realm(entity) : rank.realm().ordinal();
	}

	/** The rank of a cultivator (null for plain mobs). */
	public static @Nullable Rank rank(LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			return Cultivators.get(player).rank();
		}
		if (entity instanceof DiscipleEntity disciple) {
			return disciple.data().rank();
		}
		DuelistProfile profile = DuelistRegistry.profile(entity);
		return profile == null ? null : Rank.ofStep(profile.rankStep());
	}

	/** The Dao of a cultivator (null for none and for mobs). */
	public static @Nullable Dao dao(LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			return Cultivators.get(player).dao();
		}
		if (entity instanceof DiscipleEntity disciple) {
			return disciple.data().dao();
		}
		return null;
	}

	/** Sword Dao comprehension 0..100 (0 without the Sword Dao). */
	public static double swordComprehension(LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			CultivationData data = Cultivators.get(player);
			return data.comprehension(Dao.SWORD);
		}
		if (entity instanceof DiscipleEntity disciple) {
			DiscipleData data = disciple.data();
			return data.dao() == Dao.SWORD ? data.comprehension() : 0.0D;
		}
		return 0.0D;
	}

	/** The posture an attacker deals is multiplied by this (Sword Dao ≥ 50: ×1.1). */
	public static float dealtFactor(LivingEntity attacker) {
		return swordComprehension(attacker) >= SWORD_DAO_POSTURE ? 1.1F : 1.0F;
	}

	/** The posture a defender takes is multiplied by this (Body Dao: ×0.9). */
	public static float takenFactor(LivingEntity defender) {
		return dao(defender) == Dao.BODY ? BODY_DAO_TAKEN : 1.0F;
	}

	/**
	 * The per-tick recovery of {@code fighter} (0 while staggered or before the delay). The table's numbers are for a
	 * 100-max bar: under Realm Suppression ({@code shareScaled}) gains and recovery are scaled to the bar so both sides
	 * fight with equal shares; otherwise they are absolute (a higher realm's bigger bar simply lasts longer).
	 */
	static float recovery(Fighter fighter, long now, boolean shareScaled) {
		if (fighter.staggered(now) || now - fighter.lastPostureGain < RECOVER_DELAY) {
			return 0.0F;
		}
		LivingEntity entity = fighter.entity;
		float share = entity.getHealth() / Math.max(1.0F, entity.getMaxHealth());
		float rate = 0.25F + 0.75F * Math.max(0.0F, Math.min(1.0F, share));
		if (fighter.guarding()) {
			rate *= 1.5F;
		}
		return shareScaled ? rate * fighter.postureMax / 100.0F : rate;
	}

	private Posture() {
	}
}
