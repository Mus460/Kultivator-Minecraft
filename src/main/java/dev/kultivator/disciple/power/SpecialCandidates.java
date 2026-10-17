package dev.kultivator.disciple.power;

import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.RootGrade;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleBreakthrough;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.sect.hall.Talents;
import dev.kultivator.util.Advancements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The Calon Istimewa in effect (DESIGN 20.3.4; flags rolled by module A in {@code extra.talent.special}):
 * <ul>
 * <li>Pengemis di Gerbang (Return of the Mount Hua Sect): looks TRASH / Merah; at Foundation a reveal: HEAVENLY root,
 * Nila, +30 comprehension when the Sword Dao is chosen (+10 for another), and the innate Plum Blossom passive.</li>
 * <li>Murid Regresor (Volcanic Age, Absolute Sword Sense): until the remembered realm R, XP x1.6, every breakthrough at
 * least Perfect, strikes x0.8.</li>
 * <li>Dantian Tersumbat (Stellar Transformations): cannot learn qi techniques (Body Dao only), melee x1.6, max-health
 * bonus x1.5, no Qi Deviation from a failed breakthrough, kill XP x1.4, star-qi meditation x1.5, Undying Codex free.</li>
 * </ul>
 */
final class SpecialCandidates {

	public static final double BEGGAR_SWORD_INSIGHT = 30.0D;
	public static final double BEGGAR_OTHER_INSIGHT = 10.0D;
	public static final double REGRESSOR_XP = 1.6D;
	public static final double REGRESSOR_STRIKES = 0.8D;
	public static final double BLOCKED_MELEE = 1.6D;
	public static final double BLOCKED_HEALTH = 0.5D;
	public static final double BLOCKED_KILL_XP = 1.4D;
	public static final double BLOCKED_STAR_QI = 1.5D;

	private static final Identifier BLOCKED_HEALTH_ID = DisciplePowers.id("blocked_dantian_health");

	static void register() {
		DiscipleHooks.addQualityFloor((entity, target) -> regressing(entity.data()) ? BreakthroughQuality.PERFECT : null);
		DiscipleHooks.addTeachBlocker((entity, master, technique) -> PowerCore.special(entity.data()) == PowerIds.SPECIAL_BLOCKED_DANTIAN
				&& technique.dao() != Dao.BODY ? DisciplePowers.PREFIX + "why.blocked_dantian" : null);
		DiscipleHooks.addDamageFactor((entity, target, technique) -> !technique && PowerCore.powered(entity)
				&& PowerCore.special(entity.data()) == PowerIds.SPECIAL_BLOCKED_DANTIAN ? BLOCKED_MELEE : 1.0D);
		DiscipleHooks.addStats((entity, sink) -> {
			if (PowerCore.powered(entity) && PowerCore.special(entity.data()) == PowerIds.SPECIAL_BLOCKED_DANTIAN) {
				sink.add(Attributes.MAX_HEALTH, BLOCKED_HEALTH_ID, DiscipleCultivation.maxHealthBonus(entity.data()) * BLOCKED_HEALTH,
						AttributeModifier.Operation.ADD_VALUE);
			}
		});
		DiscipleHooks.addBreakthroughListener(new DiscipleHooks.BreakthroughListener() {
			@Override
			public void onEnd(DiscipleEntity entity, boolean success, DiscipleBreakthrough.Kind kind) {
				DiscipleData data = entity.data();
				if (!success && PowerCore.special(data) == PowerIds.SPECIAL_BLOCKED_DANTIAN && data.deviating()) {
					// Qin Yu's dantian holds no energy that could run wild: the failure leaves no Qi Deviation.
					data.setDeviationTicks(0);
					entity.removeEffect(MobEffects.WEAKNESS);
					PowerCore.tell(entity, PowerCore.key("blocked.no_deviation", PowerCore.name(entity)).withColor(0xD9A05B));
				}
				if (success && hiddenBeggar(data) && data.realm().atLeast(Realm.FOUNDATION)) {
					revealBeggar(entity);
				}
			}
		});
	}

	// ================================================================== reads

	/** The beggar boy before his reveal (the card shows TRASH / Merah). */
	static boolean hiddenBeggar(DiscipleData data) {
		return PowerCore.special(data) == PowerIds.SPECIAL_BEGGAR && !PowerCore.tag(data).getBooleanOr("beggar_revealed", false);
	}

	static boolean revealedBeggar(DiscipleData data) {
		return PowerCore.special(data) == PowerIds.SPECIAL_BEGGAR && PowerCore.tag(data).getBooleanOr("beggar_revealed", false);
	}

	/** The previous-life realm a regressor remembers (Golden Core..Dao Union; Golden Core when unknown). */
	static Realm regressorRealm(DiscipleData data) {
		CompoundTag special = data.extra().getCompoundOrEmpty(Talents.EXTRA).getCompoundOrEmpty("special");
		int ordinal = special.getIntOr("realm_r", -1);
		Realm realm = ordinal >= 0 ? Realm.byOrdinal(ordinal) : Realm.byId(special.getStringOr("realm_r", ""));
		if (realm == null || !realm.atLeast(Realm.GOLDEN_CORE)) {
			realm = Realm.GOLDEN_CORE;
		}
		return realm.atLeast(Realm.DAO_UNION) ? Realm.DAO_UNION : realm;
	}

	/** A regressor still below the realm of its previous life. */
	static boolean regressing(DiscipleData data) {
		return PowerCore.special(data) == PowerIds.SPECIAL_REGRESSOR && data.realm().ordinal() < regressorRealm(data).ordinal();
	}

	static double strikeFactor(DiscipleData data) {
		return regressing(data) ? REGRESSOR_STRIKES : 1.0D;
	}

	/** XP factors of the special candidates (see TalentEffects.xpFactor). */
	static double xpFactor(DiscipleData data, XpSource source) {
		double factor = regressing(data) ? REGRESSOR_XP : 1.0D;
		if (PowerCore.special(data) == PowerIds.SPECIAL_BLOCKED_DANTIAN) {
			if (source == XpSource.COMBAT) {
				factor *= BLOCKED_KILL_XP;
			} else if (source == XpSource.MEDITATION && data.rank().isMax()) {
				// Star qi gathered under the sky by the body itself.
				factor *= BLOCKED_STAR_QI;
			}
		}
		return factor;
	}

	/** True when Body is its only way: a Body-Dao or Blocked-Dantian disciple has the Undying Codex for free. */
	static boolean codexFree(DiscipleData data) {
		return PowerCore.special(data) == PowerIds.SPECIAL_BLOCKED_DANTIAN;
	}

	// ================================================================== the beggar's reveal

	/** At Foundation: the ragged boy was the sword saint all along (HEAVENLY root, Nila). */
	static void revealBeggar(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		if (!hiddenBeggar(data)) {
			return;
		}
		PowerCore.edit(data).putBoolean("beggar_revealed", true);
		data.setRootGrade(RootGrade.HEAVENLY);
		TalentEffects.liftColour(entity, PowerIds.COLOUR_INDIGO);
		entity.refreshSynced();
		if (entity.level() instanceof ServerLevel level) {
			PowerCore.fx(level, entity.position(), PowerIds.FX_REVEAL, 1, 0.0F);
			PowerCore.sound(level, entity.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.6F);
			PowerCore.sound(level, entity.position(), SoundEvents.CHERRY_LEAVES_BREAK, 1.0F, 0.8F);
			PowerCore.sound(level, entity.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.3F);
		}
		PowerCore.tell(entity, PowerCore.key("beggar.reveal", PowerCore.name(entity)).withColor(0xFFB7D5));
		ServerPlayer master = entity.owner();
		if (master != null) {
			Advancements.grant(master, "beggar_revealed");
		}
	}

	/** Once a second: the beggar's insight once a Dao is chosen; a revealed beggar keeps his heavenly root. */
	static void tick(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		if (PowerCore.special(data) != PowerIds.SPECIAL_BEGGAR) {
			return;
		}
		if (hiddenBeggar(data)) {
			if (data.realm().atLeast(Realm.FOUNDATION)) {
				revealBeggar(entity);
			}
			return;
		}
		Dao dao = data.dao();
		CompoundTag tag = PowerCore.tag(data);
		if (dao != null && !tag.getBooleanOr("beggar_dao", false)) {
			PowerCore.edit(data).putBoolean("beggar_dao", true);
			double insight = dao == Dao.SWORD ? BEGGAR_SWORD_INSIGHT : BEGGAR_OTHER_INSIGHT;
			data.addComprehension(insight);
			PowerCore.tell(entity, PowerCore.key(dao == Dao.SWORD ? "beggar.sword" : "beggar.dao", PowerCore.name(entity),
					Math.round(insight)).withColor(0xFFB7D5));
		}
	}

	private SpecialCandidates() {
	}
}
