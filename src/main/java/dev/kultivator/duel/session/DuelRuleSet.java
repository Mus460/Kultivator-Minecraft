package dev.kultivator.duel.session;

import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.net.DuelPayloads;
import org.jetbrains.annotations.Nullable;

/**
 * The rules of one duel (DESIGN 35.2.5), immutable. The enum ordinals are the {@code DuelIds} codes the payloads carry.
 *
 * @param realm          how the realm gap is handled
 * @param jurus          swords only, or techniques too
 * @param win            the 10% line only, or the Deathblow too
 * @param radius         ring radius 6, 9 or 12
 * @param timeLimitTicks the fight's time limit (default 3600 = 180 s)
 * @param introFilm      the cinematic intro (100 ticks) instead of the short one (40)
 * @param stake          what is staked
 * @param stakeGrade     spirit stone grade 0-3 (BATU_ROH)
 * @param stakeCount     spirit stones each side puts up, 1-64 (BATU_ROH)
 * @param slipSpec       the technique id of the challenger's jade slip (GULUNGAN), null otherwise
 * @param crowd          disciples of the sect arena watch and cheer
 */
public record DuelRuleSet(RealmRule realm, JurusRule jurus, WinRule win, int radius, int timeLimitTicks, boolean introFilm, StakeKind stake,
						  int stakeGrade, int stakeCount, @Nullable String slipSpec, boolean crowd) {

	/** Realm rules (35.2.5). */
	public enum RealmRule {
		/** Real stats; the lower side deals ×(1 + 0.15 per realm), at most ×1.45. */
		BEBAS,
		/** Real stats; the higher side deals less posture (×(1 − 0.08) per realm, min ×0.6) and has a narrower deflect window. */
		HANDICAP,
		/** "Tekan Ranah": damage and posture as shares of the victim's max, so both fight as equals. */
		TEKAN_RANAH;

		public static RealmRule byOrdinal(int ordinal) {
			RealmRule[] values = values();
			return ordinal < 0 || ordinal >= values.length ? BEBAS : values[ordinal];
		}
	}

	/** Technique rules (35.2.5). */
	public enum JurusRule {
		/** Every technique is refused ("only the blade and sword qi"); qi cuts and waves stay. */
		PEDANG_SAJA,
		/** Techniques are allowed (still clamped by DuelRules); directed strikes can be deflected. */
		JURUS_BEBAS;

		public static JurusRule byOrdinal(int ordinal) {
			JurusRule[] values = values();
			return ordinal < 0 || ordinal >= values.length ? PEDANG_SAJA : values[ordinal];
		}
	}

	/** Win rules (35.2.5). Ring out, surrender and the time limit always decide too. */
	public enum WinRule {
		/** Only the hit that would bring a side below 10% decides. */
		SEPULUH_PERSEN,
		/** The default: the 10% line, and a Deathblow wins at once. */
		TEBASAN_PENENTU;

		public static WinRule byOrdinal(int ordinal) {
			WinRule[] values = values();
			return ordinal < 0 || ordinal >= values.length ? TEBASAN_PENENTU : values[ordinal];
		}
	}

	/** Stake kinds (35.2.4). */
	public enum StakeKind {
		TANPA,
		BATU_ROH,
		GULUNGAN,
		KEHORMATAN;

		public static StakeKind byOrdinal(int ordinal) {
			StakeKind[] values = values();
			return ordinal < 0 || ordinal >= values.length ? TANPA : values[ordinal];
		}

		/** True when items change hands. */
		public boolean items() {
			return this == BATU_ROH || this == GULUNGAN;
		}
	}

	public static final int TIME_LIMIT_DEFAULT = 3600;
	public static final int[] RADII = {6, 9, 12};

	public DuelRuleSet {
		radius = radius <= 7 ? 6 : radius >= 11 ? 12 : 9;
		timeLimitTicks = Math.max(600, Math.min(20 * 600, timeLimitTicks));
		stakeGrade = Math.max(0, Math.min(3, stakeGrade));
		stakeCount = stake == StakeKind.BATU_ROH ? Math.max(1, Math.min(64, stakeCount)) : 0;
		slipSpec = stake == StakeKind.GULUNGAN && slipSpec != null && !slipSpec.isBlank() ? slipSpec : null;
	}

	/**
	 * The defaults: BEBAS, swords only, Deathblow wins, the ring radius and time limit of {@code kultivator-duel.json}
	 * ({@code ringRadiusDefault} 9, {@code timeLimitSeconds} 180; audit M4 R9: the time limit was never read), the intro
	 * film, no stake, no crowd.
	 */
	public static DuelRuleSet defaults() {
		dev.kultivator.duel.DuelConfig config = dev.kultivator.duel.DuelConfig.get();
		int ticks = config.timeLimitSeconds > 0 ? config.timeLimitTicks() : TIME_LIMIT_DEFAULT;
		return new DuelRuleSet(RealmRule.BEBAS, JurusRule.PEDANG_SAJA, WinRule.TEBASAN_PENENTU, config.ringRadiusDefault, ticks, true, StakeKind.TANPA, 0,
				0, null, false);
	}

	/** The design's suggestion (35.2.5): two realms or more apart → Realm Suppression first. */
	public static DuelRuleSet suggested(int realmGap) {
		DuelRuleSet base = defaults();
		return Math.abs(realmGap) >= 2 ? base.withRealm(RealmRule.TEKAN_RANAH) : base;
	}

	public DuelRuleSet withRealm(RealmRule value) {
		return new DuelRuleSet(value, jurus, win, radius, timeLimitTicks, introFilm, stake, stakeGrade, stakeCount, slipSpec, crowd);
	}

	public DuelRuleSet withStake(StakeKind value, int grade, int count, @Nullable String slip) {
		return new DuelRuleSet(realm, jurus, win, radius, timeLimitTicks, introFilm, value, grade, count, slip, crowd);
	}

	public DuelRuleSet withIntro(boolean value) {
		return new DuelRuleSet(realm, jurus, win, radius, timeLimitTicks, value, stake, stakeGrade, stakeCount, slipSpec, crowd);
	}

	public DuelRuleSet withCrowd(boolean value) {
		return new DuelRuleSet(realm, jurus, win, radius, timeLimitTicks, introFilm, stake, stakeGrade, stakeCount, slipSpec, value);
	}

	public DuelRuleSet withRadius(int value) {
		return new DuelRuleSet(realm, jurus, win, value, timeLimitTicks, introFilm, stake, stakeGrade, stakeCount, slipSpec, crowd);
	}

	public DuelRuleSet withJurus(JurusRule value) {
		return new DuelRuleSet(realm, value, win, radius, timeLimitTicks, introFilm, stake, stakeGrade, stakeCount, slipSpec, crowd);
	}

	public DuelRuleSet withWin(WinRule value) {
		return new DuelRuleSet(realm, jurus, value, radius, timeLimitTicks, introFilm, stake, stakeGrade, stakeCount, slipSpec, crowd);
	}

	/** Without any stake (disciples, watched duels, tests). */
	public DuelRuleSet withoutStake() {
		return withStake(StakeKind.TANPA, 0, 0, null);
	}

	/** The rules of a {@code DuelChallenge} payload (clamped by the constructor). */
	public static DuelRuleSet of(DuelPayloads.DuelChallenge p) {
		return new DuelRuleSet(RealmRule.byOrdinal(p.realmRule()), JurusRule.byOrdinal(p.jurusRule()), WinRule.byOrdinal(p.winRule()),
				p.radius(), p.timeLimitTicks() <= 0 ? TIME_LIMIT_DEFAULT : p.timeLimitTicks(), (p.flags() & DuelIds.RF_INTRO_FILM) != 0,
				StakeKind.byOrdinal(p.stakeKind()), p.stakeGrade(), p.stakeCount(), p.slipSpec(), (p.flags() & DuelIds.RF_CROWD) != 0);
	}

	/** The five rule bytes of the offer and the session sync. */
	public DuelPayloads.RuleBytes bytes() {
		int flags = (introFilm ? DuelIds.RF_INTRO_FILM : 0) | (crowd ? DuelIds.RF_CROWD : 0);
		return new DuelPayloads.RuleBytes(realm.ordinal(), jurus.ordinal(), win.ordinal(), radius, flags);
	}
}
