package dev.kultivator.duel.ai;

import dev.kultivator.duel.clash.WeaponClass;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * What a non-player duelist is (DESIGN 35.7.6): its style, rank step ({@code Rank.step()}), weapon class, qi colour, the
 * voice event key of its kiai ({@code Voice.say(npc, voiceKiai, "", pitch)}), its Honour (Elo), the health share below
 * which it yields when nothing is staked, its spirit stone stake (grade 0-3 and count, 0 = none), the technique id of
 * the jade slip it can stake (null = none) and whether it offers duels to players by itself.
 */
public record DuelistProfile(DuelStyle style, int rankStep, WeaponClass weapon, int color, String voiceKiai, int honour, float surrenderAt,
							 int stoneGrade, int stoneCount, @Nullable String slipSpec, boolean offersChallenges) {

	/** Gives the profile of an entity of a registered type (null: this one does not duel). */
	@FunctionalInterface
	public interface Provider {
		@Nullable DuelistProfile profile(LivingEntity entity);
	}

	public DuelistProfile withStyle(DuelStyle value) {
		return new DuelistProfile(value, rankStep, weapon, color, voiceKiai, honour, value.surrender, stoneGrade, stoneCount, slipSpec,
				offersChallenges);
	}

	public DuelistProfile withRank(int step) {
		return new DuelistProfile(style, step, weapon, color, voiceKiai, honour, surrenderAt, stoneGrade, stoneCount, slipSpec, offersChallenges);
	}

	public DuelistProfile withWeapon(WeaponClass value) {
		return new DuelistProfile(style, rankStep, value, color, voiceKiai, honour, surrenderAt, stoneGrade, stoneCount, slipSpec, offersChallenges);
	}

	/** True when it has a spirit stone stake to put up. */
	public boolean stakesStones() {
		return stoneCount > 0;
	}
}
