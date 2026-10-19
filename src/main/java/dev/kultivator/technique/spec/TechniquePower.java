package dev.kultivator.technique.spec;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Val;
import dev.kultivator.trial.HeartDemonEntity;
import dev.kultivator.util.Targeting;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToDoubleFunction;

/**
 * All technique math (DESIGN 22.5, 22.6, 22.11.3, 22.14): power P, the A/D/N growth, qi and cooldown after mastery and
 * the realm gap, mastery thresholds, boss caps, Mode Kehancuran's K and block cap, and {@link #finalDamage} with the
 * arena, trial-mob, heart-demon, F and boss rules. Common code: the codex calls exactly the functions the server uses,
 * so it shows the real numbers. Every number is a {@code public static final}.
 */
public final class TechniquePower {

	// ================================================================== power P (22.5.1)

	public static final double PLAYER_BASE = 6.0D;
	public static final double DISCIPLE_BASE = 4.5D;
	/** A[realm] for Mortal to Dao Union (Fana 0.6, QR 1.0, FE 1.7, GC 2.9, NS 5.0, DT 8.5, DU 14.5). */
	private static final double[] REALM_A = {0.6D, 1.0D, 1.7D, 2.9D, 5.0D, 8.5D, 14.5D};
	public static final double STAGE_STEP = 0.12D;
	public static final double IA_BASE = 25.0D;
	public static final double IA_LAYER_GROWTH = 1.3D;
	public static final double IA_STAGE_STEP = 0.06D;
	public static final double TI_BASE = 400.0D;
	public static final double TI_STAGE_STEP = 0.25D;
	public static final double STAR_STEP = 0.04D;
	public static final double STAR_STAGE_FACTOR = 1.15D;
	public static final int STARS_PER_STAGE = 9;
	/** {@code extra.kultivasi.stars}: the player's endless stars (0 until phase 7). */
	public static final String PLAYER_EXTRA = "kultivasi";

	// ================================================================== growth (22.5.3)

	public static final double A_PER_GAP = 0.10D;
	public static final double A_PER_LAYER = 0.02D;
	public static final double A_TRUE_IMMORTAL = 0.10D;
	public static final double A_PER_TI_STAGE = 0.05D;
	public static final double A_PER_STAR = 0.01D;
	public static final double D_PER_GAP = 0.06D;
	public static final double D_PER_STAR = 0.005D;
	public static final double N_PER_GAP = 0.15D;
	public static final double N_PER_STAR = 0.01D;

	// ================================================================== qi, cooldown, regeneration (22.5.4)

	public static final double QI_GAP_STEP = 0.13D;
	public static final double QI_GAP_FLOOR = 0.35D;
	public static final double QI_PCT_MASTERY_FLOOR = 0.5D;
	public static final double CD_GAP_STEP = 0.12D;
	/** Combined floor of every cooldown reduction (22.14.3). */
	public static final double CD_FLOOR = 0.40D;
	public static final int MIN_COOLDOWN = TechniqueSpec.MIN_COOLDOWN;
	public static final double QI_REGEN_PER_TIER = 0.1D;
	/** Disciples pay this share of a flat qi cost (22.13.3). */
	public static final double DISCIPLE_QI_FACTOR = 0.6D;
	public static final int GLOBAL_COOLDOWN = 10;
	public static final int ULTIMATE_LOCKOUT = 400;

	// ================================================================== mastery (22.6)

	public static final int MAX_MASTERY = 4;
	private static final double[] MASTERY_DAMAGE = {1.0D, 1.15D, 1.35D, 1.60D, 2.00D};
	private static final double[] MASTERY_AREA = {1.0D, 1.08D, 1.16D, 1.25D, 1.40D};
	private static final double[] MASTERY_DURATION = {1.0D, 1.10D, 1.20D, 1.30D, 1.50D};
	private static final double[] MASTERY_QI = {1.0D, 0.95D, 0.90D, 0.85D, 0.75D};
	private static final double[] MASTERY_COOLDOWN = {1.0D, 0.95D, 0.90D, 0.80D, 0.70D};
	/** Dao comprehension mastery levels 3 and 4 need (universal specs: Nascent Soul / Dao Union). */
	public static final double MASTERY_GATE_3 = 60.0D;
	public static final double MASTERY_GATE_4 = 90.0D;
	public static final Realm MASTERY_REALM_3 = Realm.NASCENT_SOUL;
	public static final Realm MASTERY_REALM_4 = Realm.DAO_UNION;

	// ================================================================== damage resolution (22.5.5, 22.14)

	public static final double F_CAP = 3.0D;
	public static final double F_CAP_ARENA = 1.5D;
	public static final double ARENA_PLAYER_SHARE = 0.15D;
	public static final double TRIAL_MOB_FACTOR = 1.5D;
	public static final double HEART_DEMON_HIT = 0.06D;
	public static final double HEART_DEMON_ULTIMATE_HIT = 0.10D;
	public static final double HEART_DEMON_CAST = 0.12D;
	public static final double BOSS_TICK_SHARE = 0.02D;
	public static final double EXEC_BOSS_HEALTH = 0.05D;
	public static final double BOSS_PER_STAR = 0.01D;
	public static final String TAG_BOSS = "kultivator.boss";
	public static final String TAG_DUMMY_BOSS = "kultivator.dummy_boss";
	public static final String TAG_DAO_PROTECTOR = "kultivator.dao_protector";
	public static final String TAG_TRIAL_GUARD = "kultivator.trial_guard";
	/** Elite demonic beasts (DESIGN 19.2.8, 19.3.1): trial-mob rule, never {@link #TAG_BOSS}. */
	public static final String TAG_ELITE = "kultivator.elite";
	/** Rogue cultivators (DESIGN 25.1.2, phase 8): trial-mob rule; the Patriarch also carries {@link #TAG_BOSS}. */
	public static final String TAG_ROGUE = "kultivator.rogue";
	/**
	 * Every phase-9 hostile (DESIGN 26.2.2): natives, envoys, the Panglima, primordials, relics, batin meteors and calamity
	 * foes. Trial-mob rule with the attacker's stars counted again: legacy power × 1.5 × sqrt(S(stars)).
	 */
	public static final String TAG_ABADI_FOE = "kultivator.abadi_foe";
	/** The product of every player star factor (DESIGN 26.14 item 5: the T factor of the Seven Calamities) stays in this range. */
	public static final double PLAYER_STAR_FACTOR_MIN = 1.0D;
	public static final double PLAYER_STAR_FACTOR_MAX = 1.331D;

	private static final List<ToDoubleFunction<CultivationData>> PLAYER_STAR_FACTORS = new CopyOnWriteArrayList<>();

	/**
	 * Adds a factor multiplied into the player's star factor S(stars) and the star health bonus (DESIGN 26.14 item 5); the
	 * product of all of them is clamped to {@link #PLAYER_STAR_FACTOR_MIN}..{@link #PLAYER_STAR_FACTOR_MAX}. Must be pure
	 * (it reads the data only); register from a module's {@code init()}. Players only, never disciples.
	 */
	public static void addPlayerStarFactor(ToDoubleFunction<CultivationData> factor) {
		if (factor != null) {
			PLAYER_STAR_FACTORS.add(factor);
		}
	}

	/** The clamped product of every player star factor (1 without any). */
	public static double playerStarProduct(CultivationData data) {
		if (PLAYER_STAR_FACTORS.isEmpty() || data == null) {
			return 1.0D;
		}
		double product = 1.0D;
		for (ToDoubleFunction<CultivationData> factor : PLAYER_STAR_FACTORS) {
			try {
				double value = factor.applyAsDouble(data);
				if (Double.isFinite(value) && value >= 0.0D) {
					product *= value;
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kultivator player star factor failed", exception);
			}
		}
		return Math.max(PLAYER_STAR_FACTOR_MIN, Math.min(PLAYER_STAR_FACTOR_MAX, product));
	}

	/** The player's S(stars) × {@link #playerStarProduct}: the star factor of P_player. */
	public static double playerStarFactor(CultivationData data) {
		return starFactor(playerStars(data)) * playerStarProduct(data);
	}

	/** The attacker's star factor for the phase-9 trial-mob branch: a player's {@link #playerStarFactor}, a disciple's S(stars), else 1. */
	public static double attackerStarFactor(LivingEntity attacker) {
		if (attacker instanceof ServerPlayer player) {
			return playerStarFactor(Cultivators.get(player));
		}
		if (attacker instanceof DiscipleEntity disciple) {
			return starFactor(DisciplePaths.stars(disciple.data()));
		}
		return 1.0D;
	}
	/** The product of all {@link CasterFactor}s is clamped to this range on its own (DESIGN 25.2 item 6). */
	public static final double CASTER_FACTOR_MIN = 0.8D;
	public static final double CASTER_FACTOR_MAX = 1.30D;

	/**
	 * A phase-8 factor of the attacker (DESIGN 25.2 item 6: the Pedang artifact, Luka Alam). Only used for the coefficient
	 * branch of {@link #finalDamage}, never in an arena or duel, never against players. Server only.
	 */
	public interface CasterFactor {
		double factor(LivingEntity attacker, @Nullable TechniqueSpec spec, LivingEntity target);
	}

	private static final List<CasterFactor> CASTER_FACTORS = new CopyOnWriteArrayList<>();

	/** Adds a caster factor (DESIGN 25.2 item 6); register from a module's {@code init()}. */
	public static void addCasterFactor(CasterFactor factor) {
		CASTER_FACTORS.add(factor);
	}

	/** The product of every {@link CasterFactor}, clamped to {@link #CASTER_FACTOR_MIN}..{@link #CASTER_FACTOR_MAX} (1 without any). */
	public static double casterFactor(LivingEntity attacker, @Nullable TechniqueSpec spec, LivingEntity target) {
		if (CASTER_FACTORS.isEmpty()) {
			return 1.0D;
		}
		double product = 1.0D;
		for (CasterFactor factor : CASTER_FACTORS) {
			double value;
			try {
				value = factor.factor(attacker, spec, target);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kultivator caster factor failed", exception);
				continue;
			}
			if (Double.isFinite(value) && value >= 0.0D) {
				product *= value;
			}
		}
		return Math.max(CASTER_FACTOR_MIN, Math.min(CASTER_FACTOR_MAX, product));
	}

	/** The product of all {@link TargetFactor}s is clamped to this range (DESIGN 19.2.8). */
	public static final double TARGET_FACTOR_MIN = 0.5D;
	public static final double TARGET_FACTOR_MAX = 2.0D;

	/**
	 * A phase-7 factor on technique damage against a target (DESIGN 19.2.8: the five elements, the elite affix Perisai
	 * Unsur). Only used for the coefficient branch of {@link #finalDamage}, never in an arena, never against players, never
	 * on LEGACY, PERCENT or EXEC amounts. Server only (the codex on the client sees no factors).
	 */
	public interface TargetFactor {
		double factor(LivingEntity attacker, LivingEntity target, @Nullable TechniqueSpec spec);
	}

	private static final List<TargetFactor> TARGET_FACTORS = new CopyOnWriteArrayList<>();

	/** Adds a target factor (DESIGN 19.2.8); register from a module's {@code init()}. */
	public static void addTargetFactor(TargetFactor factor) {
		TARGET_FACTORS.add(factor);
	}

	/**
	 * The product of every {@link TargetFactor} for this hit, clamped to {@link #TARGET_FACTOR_MIN}..{@link #TARGET_FACTOR_MAX}
	 * (1 without factors). {@link #finalDamage} applies it only where DESIGN 19.2.8 allows; {@code uji unsur} may show it.
	 */
	public static double targetFactor(LivingEntity attacker, LivingEntity target, @Nullable TechniqueSpec spec) {
		if (TARGET_FACTORS.isEmpty()) {
			return 1.0D;
		}
		double product = 1.0D;
		for (TargetFactor factor : TARGET_FACTORS) {
			double value = factor.factor(attacker, target, spec);
			if (Double.isFinite(value) && value >= 0.0D) {
				product *= value;
			}
		}
		return Math.max(TARGET_FACTOR_MIN, Math.min(TARGET_FACTOR_MAX, product));
	}
	/** {@code #kultivator:technique_bosses} (warden, wither, ender_dragon, elder_guardian). */
	public static final TagKey<EntityType<?>> TECHNIQUE_BOSSES = TagKey.create(Registries.ENTITY_TYPE, Kultivator.id("technique_bosses"));
	/** Damage reduction from technique sources is capped at 80% (Dewa Abadi 85%), heart-demon damage at 50% (22.14.3). */
	public static final double DR_CAP = 0.80D;
	public static final double DR_CAP_DEWA = 0.85D;
	public static final double DR_CAP_HEART_DEMON = 0.50D;
	public static final double DODGE_CAP = 0.50D;
	public static final double SHIELD_CAP = 2.0D;
	public static final double REFLECT_CAP = 1.0D;

	// ================================================================== hard caps (22.3.1) and budgets (22.15)

	public static final double STRIKE_RANGE_CAP = 12.0D;
	public static final double STRIKE_ARC_CAP = 220.0D;
	public static final double CONE_LENGTH_CAP = 24.0D;
	public static final double CONE_ANGLE_CAP = 60.0D;
	public static final double BEAM_LENGTH_CAP = 64.0D;
	public static final double BEAM_LENGTH_CAP_DEWA = 96.0D;
	public static final double TRAVEL_CAP = 128.0D;
	public static final double BEAM_WIDTH_CAP = 4.0D;
	public static final double FRONT_LENGTH_CAP = 64.0D;
	public static final double FRONT_WIDTH_CAP = 24.0D;
	public static final double FRONT_HEIGHT_CAP = 12.0D;
	public static final double FRONT_SPEED_MAX = 8.0D;
	public static final double NOVA_RADIUS_CAP = 32.0D;
	public static final double WAVE_RADIUS_CAP = 40.0D;
	public static final double ZONE_RANGE_CAP = 64.0D;
	public static final double ZONE_RADIUS_CAP = 24.0D;
	public static final double ZONE_RADIUS_CAP_DEWA = 32.0D;
	public static final double PILLAR_RADIUS_CAP = 6.0D;
	public static final double PILLAR_HEIGHT_CAP = 64.0D;
	public static final double PILLAR_SEARCH_CAP = 32.0D;
	public static final double RAIN_RADIUS_CAP = 32.0D;
	public static final double RAIN_IMPACTS_CAP = 40.0D;
	public static final double RAIN_IMPACT_RADIUS_CAP = 6.0D;
	public static final double CHAIN_RANGE_CAP = 48.0D;
	public static final double CHAIN_JUMP_CAP = 12.0D;
	public static final double CHAIN_JUMPS_CAP = 12.0D;
	public static final double TARGET_RANGE_CAP = 64.0D;
	public static final double TARGET_SPLASH_CAP = 8.0D;
	public static final double ALL_RADIUS_CAP = 32.0D;
	public static final int MAX_TARGETS = 32;
	public static final int MAX_TARGETS_DEWA = 64;
	public static final int RECALL_MAX = 32;
	public static final double RECALL_RANGE = 48.0D;
	public static final double VOLLEY_COUNT_CAP = 48.0D;
	public static final double VOLLEY_RANGE_CAP = 64.0D;
	public static final double VOLLEY_HIT_RADIUS_CAP = 3.0D;
	public static final double DOMAIN_RADIUS_CAP = 32.0D;
	public static final double DOMAIN_RADIUS_CAP_DEWA = 40.0D;
	public static final double DOMAIN_DURATION_CAP = 600.0D;
	public static final double DOMAIN_DURATION_CAP_DEWA = 900.0D;
	public static final double AREA_HEIGHT_CAP = 16.0D;
	public static final int PULSE_MIN = 10;
	public static final double AURA_RADIUS_CAP = 10.0D;
	public static final double AURA_DURATION_CAP = 600.0D;
	public static final double PATH_WIDTH_CAP = 3.0D;
	public static final double DASH_CAP = 64.0D;
	public static final double BLINK_CAP = 64.0D;
	public static final int ACTIVE_CASTS_PLAYER = 6;
	public static final int ACTIVE_CASTS_DISCIPLE = 3;
	public static final int PHANTOMS_PER_CAST = 8;
	public static final int PHANTOMS_PER_CASTER = 12;
	public static final int PHANTOMS_SERVER = 48;
	public static final int PROJECTILES_PER_CAST = 48;
	public static final int PROJECTILES_SERVER = 256;
	public static final int TRACKERS_PER_TARGET = 4;
	public static final int TRACKERS_SERVER = 512;
	public static final int BINDS_SERVER = 128;
	public static final int GLOWING_PER_CAST = 64;
	public static final int FIELDS_PER_CASTER = 2;
	public static final int TELEPORT_GAP = 3;
	public static final int FX_EVENTS_PER_SECOND = 20;
	public static final int FX_MASS_HIT = 8;
	public static final double FX_RANGE = 96.0D;
	public static final double FX_RANGE_BIG = 160.0D;
	public static final int SOUNDS_PER_PHASE = 4;
	public static final double PORTAL_MOVE_RADIUS = 8.0D;

	// ================================================================== Mode Kehancuran (22.11.3)

	public static final double K_PER_STAR = 0.02D;
	public static final double K_MAX = 2.0D;
	public static final int MIN_RING_INNER_EXTRA = 1;
	public static final int SCATTER_POINTS = 12;
	public static final int MAX_DESTRUCTION_SHAPES = 5;
	public static final int DEPTH_CAP = 24;

	/** A/D/N growth of one cast (22.5.3): {@code t} tier index, {@code tierGap} caster tier − grade tier, {@code gapR} realm gap, {@code above} tiers above the spec's gate (for {@code lin}). */
	public record Factors(double a, double d, double n, int t, int tierGap, int gapR, int above) {
		/** Arena casts: A = D = N = 1 (22.14.5). */
		public static final Factors ONE = new Factors(1.0D, 1.0D, 1.0D, 0, 0, 0, 0);
	}

	// ================================================================== power

	/** R(rank) of 22.5.1. */
	public static double rankFactor(Rank rank) {
		Realm realm = rank.realm();
		if (realm == Realm.TRUE_IMMORTAL) {
			return TI_BASE * (1.0D + TI_STAGE_STEP * rank.stage());
		}
		if (realm == Realm.IMMORTAL_ASCENSION) {
			return IA_BASE * Math.pow(IA_LAYER_GROWTH, rank.layer()) * (1.0D + IA_STAGE_STEP * rank.stage());
		}
		return REALM_A[Math.min(realm.ordinal(), REALM_A.length - 1)] * (1.0D + STAGE_STEP * rank.stage());
	}

	/** S(n) = (1 + 0.04 n) × 1.15^floor(n/9). */
	public static double starFactor(int stars) {
		int n = Math.max(0, stars);
		return (1.0D + STAR_STEP * n) * Math.pow(STAR_STAGE_FACTOR, n / STARS_PER_STAGE);
	}

	/** The player's endless stars ({@code extra.kultivasi.stars}; 0 until phase 7). */
	public static int playerStars(CultivationData data) {
		return Math.max(0, data.extra().getCompoundOrEmpty(PLAYER_EXTRA).getIntOr("stars", 0));
	}

	/** C = 1 + comprehension / 100 of {@code dao} (main comprehension when null). */
	public static double comprehensionFactor(CultivationData data, @Nullable Dao dao) {
		double comprehension = dao == null ? data.mainComprehension() : data.comprehension(dao);
		return 1.0D + comprehension / 100.0D;
	}

	/** P_player with the main comprehension. */
	public static double power(CultivationData data) {
		return power(data, (Dao) null);
	}

	/** P_player for a spec of {@code dao} (null = universal: main comprehension). */
	public static double power(CultivationData data, @Nullable Dao dao) {
		double quality = Math.sqrt(Math.max(0.01D, data.qualityFactor()));
		double multiplier = KultivatorConfig.get().techniqueDamageMultiplier;
		return PLAYER_BASE * rankFactor(data.rank()) * comprehensionFactor(data, dao) * quality * playerStarFactor(data) * multiplier;
	}

	public static double power(CultivationData data, TechniqueSpec spec) {
		return power(data, spec.dao());
	}

	/** P_disciple = 4.5 × R × C (the {@code damageFactor} hooks apply at the choke point). */
	public static double power(DiscipleData data) {
		return DISCIPLE_BASE * rankFactor(data.rank()) * (1.0D + data.comprehension() / 100.0D);
	}

	/** D_legacy = (4 + 2.5 realm + 0.5 stage) × C (main comprehension): pre-6c power, for arenas and trial mobs. */
	public static double legacyD(CultivationData data) {
		return legacyD(data, null);
	}

	public static double legacyD(CultivationData data, @Nullable Dao dao) {
		Rank rank = data.rank();
		return (4.0D + 2.5D * rank.realm().ordinal() + 0.5D * rank.stage()) * comprehensionFactor(data, dao);
	}

	/** U_legacy = (3 + 2 realm + 0.5 stage) × C. */
	public static double legacyU(DiscipleData data) {
		Rank rank = data.rank();
		return (3.0D + 2.0D * rank.realm().ordinal() + 0.5D * rank.stage()) * (1.0D + data.comprehension() / 100.0D);
	}

	// ================================================================== tiers and growth

	/** Caster grade tier: QR/FE 0, GC/NS 1, DT/DU 2, IA 3, TI 4, ★9+ 5 (22.5.4). */
	public static int casterTier(Rank rank, int stars) {
		if (stars >= STARS_PER_STAGE) {
			return 5;
		}
		return switch (rank.realm()) {
			case TRUE_IMMORTAL -> 4;
			case IMMORTAL_ASCENSION -> 3;
			case DEITY_TRANSFORMATION, DAO_UNION -> 2;
			case GOLDEN_CORE, NASCENT_SOUL -> 1;
			default -> 0;
		};
	}

	public static int casterTier(CultivationData data) {
		return casterTier(data.rank(), playerStars(data));
	}

	public static int casterTier(DiscipleData data) {
		return casterTier(data.rank(), DisciplePaths.stars(data));
	}

	/** Tier index t: GC 0, NS 1, DT 2, DU 3, IA T1-3 4, T4-6 5, T7-9 6, TI 7, + floor(stars/9); below GC 0. */
	public static int tierIndex(Rank rank, int stars) {
		int base = switch (rank.realm()) {
			case NASCENT_SOUL -> 1;
			case DEITY_TRANSFORMATION -> 2;
			case DAO_UNION -> 3;
			case IMMORTAL_ASCENSION -> 4 + Math.min(2, rank.layer() / 3);
			case TRUE_IMMORTAL -> 7;
			default -> 0;
		};
		return base + Math.max(0, stars) / STARS_PER_STAGE;
	}

	public static Factors factors(CultivationData data, TechniqueSpec spec) {
		return factors(data.rank(), playerStars(data), spec);
	}

	public static Factors factors(DiscipleData data, TechniqueSpec spec) {
		return factors(data.rank(), DisciplePaths.stars(data), spec);
	}

	/** A, D, N, t, tierGap, gapR (22.5.3, 22.5.4) of a caster at {@code rank} with {@code stars} for {@code spec}. */
	public static Factors factors(Rank rank, int stars, TechniqueSpec spec) {
		int n = Math.max(0, stars);
		int gapR = Math.max(0, rank.realm().ordinal() - spec.minRealm().ordinal());
		int t = tierIndex(rank, n);
		int tierGap = Math.max(0, casterTier(rank, n) - spec.grade().tier());
		double a = 1.0D + A_PER_GAP * gapR + A_PER_STAR * n;
		if (rank.realm() == Realm.IMMORTAL_ASCENSION) {
			a += A_PER_LAYER * rank.layer();
		} else if (rank.realm() == Realm.TRUE_IMMORTAL) {
			a += A_TRUE_IMMORTAL + A_PER_TI_STAGE * rank.stage();
		}
		double d = 1.0D + D_PER_GAP * gapR + D_PER_STAR * n;
		double count = 1.0D + N_PER_GAP * gapR + N_PER_STAR * n;
		int above = Math.max(0, t - tierIndex(spec.minRank(), 0));
		return new Factors(a, d, count, t, tierGap, gapR, above);
	}

	/** {@code lin(b, k, c)}: b + k per tier above the gate, at most c (no A/D/N on top). */
	public static double lin(double base, double perTier, double cap, int tiersAbove) {
		double value = base + perTier * Math.max(0, tiersAbove);
		return perTier >= 0.0D ? Math.min(cap, value) : Math.max(cap, value);
	}

	/** An area value of {@code v}: base × A × mastery area, at most the spec cap and {@code hardCap} (≤ 0 = none). */
	public static double area(Val v, Factors f, int mastery, double hardCap) {
		return scaled(v, f.a(), masteryArea(mastery), hardCap, f.above());
	}

	/** A duration in ticks: base × D × mastery duration, at most the caps. */
	public static int duration(Val v, Factors f, int mastery, double hardCap) {
		double value = scaled(v, f.d(), masteryDuration(mastery), hardCap, f.above());
		return Double.isNaN(value) ? 0 : (int) Math.round(value);
	}

	/** A count: base × N (no mastery), rounded, at most the caps. */
	public static int count(Val v, Factors f, double hardCap) {
		double value = scaled(v, f.n(), 1.0D, hardCap, f.above());
		return Double.isNaN(value) ? 0 : (int) Math.round(value);
	}

	/** NaN when {@code v} is not set. */
	private static double scaled(Val v, double factor, double mastery, double hardCap, int above) {
		if (!v.set()) {
			return Double.NaN;
		}
		double value = v.linear() ? lin(v.base(), v.perTier(), v.cap(), above) : Math.min(v.cap(), v.base() * factor * mastery);
		if (hardCap > 0.0D) {
			value = Math.min(value, hardCap);
		}
		return Math.max(0.0D, value);
	}

	/** The radius hard cap: {@code techniqueAreaCap} (32), Dewa specs × 1.25 (22.14.7). */
	public static double radiusCap(boolean dewa) {
		return KultivatorConfig.get().techniqueAreaCap * (dewa ? 1.25D : 1.0D);
	}

	public static int maxTargets(boolean dewa) {
		return dewa ? MAX_TARGETS_DEWA : MAX_TARGETS;
	}

	// ================================================================== mastery

	private static int level(int mastery) {
		return Math.max(0, Math.min(MAX_MASTERY, mastery));
	}

	public static double masteryDamage(int mastery) {
		return MASTERY_DAMAGE[level(mastery)];
	}

	public static double masteryArea(int mastery) {
		return MASTERY_AREA[level(mastery)];
	}

	public static double masteryDuration(int mastery) {
		return MASTERY_DURATION[level(mastery)];
	}

	public static double masteryQi(int mastery) {
		return MASTERY_QI[level(mastery)];
	}

	public static double masteryCooldown(int mastery) {
		return MASTERY_COOLDOWN[level(mastery)];
	}

	/** The level {@code points} reach for {@code grade} (before the comprehension gates of levels 3 and 4). */
	public static int masteryLevel(TechniqueGrade grade, int points) {
		int result = 0;
		for (int level = 1; level <= MAX_MASTERY; level++) {
			if (points >= grade.masteryThreshold(level)) {
				result = level;
			}
		}
		return result;
	}

	public static int masteryThreshold(TechniqueGrade grade, int level) {
		return grade.masteryThreshold(level);
	}

	/** The highest mastery level the gates allow (22.6): level 3 needs comprehension 60 (universal: NS), level 4 90 (DU). */
	public static int masteryGate(TechniqueSpec spec, Realm realm, double comprehension) {
		boolean universal = spec.universal();
		if (universal ? realm.atLeast(MASTERY_REALM_4) : comprehension >= MASTERY_GATE_4) {
			return 4;
		}
		if (universal ? realm.atLeast(MASTERY_REALM_3) : comprehension >= MASTERY_GATE_3) {
			return 3;
		}
		return 2;
	}

	// ================================================================== qi and cooldown

	/** Qi of one cast: flat {@code ceil(qi × M_qi × max(0.35, 1 − 0.13 tierGap))}, or % of max qi (no realm gap). */
	public static int qi(TechniqueSpec spec, CultivationData data, int mastery) {
		return qi(spec, data, mastery, 1.0D);
	}

	/** As {@link #qi(TechniqueSpec, CultivationData, int)} with an extra cost factor (COST_MOD; 0 = free). */
	public static int qi(TechniqueSpec spec, CultivationData data, int mastery, double costFactor) {
		double factor = Math.max(0.0D, costFactor);
		if (spec.qiPct() > 0.0D) {
			double base = Math.max(spec.qiMin(), Qi.max(data) * spec.qiPct() / 100.0D);
			return (int) Math.ceil(base * Math.max(QI_PCT_MASTERY_FLOOR, masteryQi(mastery)) * factor);
		}
		int tierGap = Math.max(0, casterTier(data) - spec.grade().tier());
		double gap = Math.max(QI_GAP_FLOOR, 1.0D - QI_GAP_STEP * tierGap);
		return (int) Math.ceil(spec.qi() * masteryQi(mastery) * gap * factor);
	}

	/** Qi a disciple pays (22.13.3): 60% of the flat cost after mastery and gap, or the % of its own max qi. */
	public static double qiDisciple(TechniqueSpec spec, DiscipleData data, int mastery) {
		if (spec.qiPct() > 0.0D) {
			return DiscipleCultivation.maxQi(data) * spec.qiPct() / 100.0D * Math.max(QI_PCT_MASTERY_FLOOR, masteryQi(mastery));
		}
		int tierGap = Math.max(0, casterTier(data) - spec.grade().tier());
		double gap = Math.max(QI_GAP_FLOOR, 1.0D - QI_GAP_STEP * tierGap);
		return spec.qi() * DISCIPLE_QI_FACTOR * masteryQi(mastery) * gap;
	}

	/** Cooldown ticks after mastery and the realm gap (combined floor ×0.4, at least 10; never reduced on PAMUNGKAS). */
	public static int cooldownTicks(TechniqueSpec spec, CultivationData data, int mastery) {
		return cooldownTicks(spec, casterTier(data), mastery, 1.0D);
	}

	/** As above with an extra reduction factor (CD_RATE, COST_MOD cd) inside the same ×0.4 floor. */
	public static int cooldownTicks(TechniqueSpec spec, CultivationData data, int mastery, double extraFactor) {
		return cooldownTicks(spec, casterTier(data), mastery, extraFactor);
	}

	public static int cooldownTicks(TechniqueSpec spec, DiscipleData data, int mastery) {
		return cooldownTicks(spec, casterTier(data), mastery, 1.0D);
	}

	private static int cooldownTicks(TechniqueSpec spec, int casterTier, int mastery, double extraFactor) {
		if (spec.pamungkas()) {
			return Math.max(MIN_COOLDOWN, spec.cooldownTicks());
		}
		int tierGap = Math.max(0, casterTier - spec.grade().tier());
		double gap = Math.max(CD_FLOOR, 1.0D - CD_GAP_STEP * tierGap);
		double factor = Math.max(CD_FLOOR, masteryCooldown(mastery) * gap * Math.max(0.0D, extraFactor));
		return Math.max(MIN_COOLDOWN, (int) Math.round(spec.cooldownTicks() * factor));
	}

	/** Qi regeneration factor of the caster grade tier: ×(1 + 0.1 tier), Dewa Abadi ×1.4 (22.5.4). */
	public static double qiRegenFactor(CultivationData data) {
		return 1.0D + QI_REGEN_PER_TIER * casterTier(data);
	}

	// ================================================================== boss caps (22.14.2)

	/** The rank's boss cap base (+0.01 per star). */
	public static double bossBase(Rank rank, int stars) {
		double base = switch (rank.realm()) {
			case GOLDEN_CORE -> 0.12D;
			case NASCENT_SOUL -> 0.15D;
			case DEITY_TRANSFORMATION -> 0.18D;
			case DAO_UNION -> 0.22D;
			case IMMORTAL_ASCENSION -> 0.26D + 0.03D * rank.layer();
			case TRUE_IMMORTAL -> 0.70D + 0.10D * rank.stage();
			default -> 0.06D;
		};
		return base + BOSS_PER_STAR * Math.max(0, stars);
	}

	/** Share of a boss's max health one cast of {@code grade} may take: min(1, base × grade factor). */
	public static double bossShare(CultivationData data, TechniqueGrade grade) {
		return Math.min(1.0D, bossBase(data.rank(), playerStars(data)) * grade.bossFactor());
	}

	public static double bossShare(DiscipleData data, TechniqueGrade grade) {
		return Math.min(1.0D, bossBase(data.rank(), DisciplePaths.stars(data)) * grade.bossFactor());
	}

	/** Warden, Wither, Ender Dragon, Elder Guardian, the datapack tag, the trial's final guardian and boss dummies. */
	public static boolean isBoss(LivingEntity entity) {
		return entity.is(TECHNIQUE_BOSSES) || entity.entityTags().contains(TAG_BOSS) || entity.entityTags().contains(TAG_DUMMY_BOSS);
	}

	/**
	 * Dao Protectors, trial guards, elites (19.2.8), rogue cultivators (25.1.2) and phase-9 foes (26.2.2): legacy power × 1.5
	 * instead of P (22.14.4); against {@link #TAG_ABADI_FOE} also × sqrt(S(attacker stars)).
	 */
	public static boolean isTrialMob(LivingEntity entity) {
		Set<String> tags = entity.entityTags();
		return tags.contains(TAG_DAO_PROTECTOR) || tags.contains(TAG_TRIAL_GUARD) || tags.contains(TAG_ELITE) || tags.contains(TAG_ROGUE)
				|| tags.contains(TAG_ABADI_FOE);
	}

	/** The attacker's own heart demon. */
	public static boolean isOwnHeartDemon(LivingEntity attacker, LivingEntity target) {
		return target instanceof HeartDemonEntity demon && attacker.getUUID().equals(demon.getOwnerUUID());
	}

	/** The target is the attacker's sparring or {@code DuelRules} partner (22.14.5). */
	public static boolean inArena(LivingEntity attacker, LivingEntity target) {
		if (DuelRules.dueling(attacker, target)) {
			return true;
		}
		return attacker instanceof ServerPlayer player && target instanceof ServerPlayer other && Targeting.sparring.test(player, other);
	}

	// ================================================================== Mode Kehancuran size (22.11.3)

	/** K of the player's rank (0 below Golden Core), + 0.02 per player star up to 2.0. */
	public static double kScale(CultivationData data) {
		Rank rank = data.rank();
		double k = switch (rank.realm()) {
			case GOLDEN_CORE -> 0.25D;
			case NASCENT_SOUL -> 0.35D;
			case DEITY_TRANSFORMATION -> 0.50D;
			case DAO_UNION -> 0.60D;
			case IMMORTAL_ASCENSION -> 0.65D + 0.04D * rank.layer();
			case TRUE_IMMORTAL -> 1.2D + 0.1D * rank.stage();
			default -> 0.0D;
		};
		int stars = playerStars(data);
		if (k > 0.0D && stars > 0) {
			k = Math.min(K_MAX, k + K_PER_STAR * stars);
		}
		return k;
	}

	/** Blocks one cast may remove (0 below Golden Core), never above {@code kehancuranMaxBlocksPerCast}. */
	public static int blockCap(CultivationData data) {
		int configCap = KultivatorConfig.get().kehancuranMaxBlocksPerCast;
		Rank rank = data.rank();
		int cap = switch (rank.realm()) {
			case GOLDEN_CORE -> 128;
			case NASCENT_SOUL -> 256;
			case DEITY_TRANSFORMATION -> 512;
			case DAO_UNION -> 1024;
			case IMMORTAL_ASCENSION -> 1536 + 192 * rank.layer();
			case TRUE_IMMORTAL -> configCap;
			default -> 0;
		};
		if (cap > 0 && playerStars(data) > 0) {
			cap = configCap;
		}
		return Math.min(cap, configCap);
	}

	// ================================================================== versus and F

	/** The versus classes of a target (WET from water or rain; the WET mark is added by the caller). */
	public static Set<Payload.Versus> classify(LivingEntity target) {
		EnumSet<Payload.Versus> set = EnumSet.noneOf(Payload.Versus.class);
		if (target.is(EntityTypeTags.UNDEAD)) {
			set.add(Payload.Versus.UNDEAD);
		}
		if (target.is(EntityTypeTags.ARTHROPOD)) {
			set.add(Payload.Versus.ARTHROPOD);
		}
		if (target.is(EntityTypeTags.AQUATIC)) {
			set.add(Payload.Versus.AQUATIC);
		}
		if (isBoss(target)) {
			set.add(Payload.Versus.BOSS);
		}
		if (target.level().dimension() == Level.NETHER) {
			set.add(Payload.Versus.NETHER);
		} else if (target.level().dimension() == Level.END) {
			set.add(Payload.Versus.END);
		}
		if (target.fireImmune()) {
			set.add(Payload.Versus.FIRE_IMMUNE);
		}
		if (target.isInWaterOrRain()) {
			set.add(Payload.Versus.WET);
		}
		if (target.isFullyFrozen() || target.getTicksFrozen() > 0) {
			set.add(Payload.Versus.FROZEN);
		}
		return set;
	}

	/**
	 * The versus factor of a hit: the explicit {@code vs} entries, plus the Dao defaults where the spec gives none (Fire vs
	 * FIRE_IMMUNE ×0.5, Lightning vs WET or FROZEN ×1.25) and Ice shatter vs FROZEN ×1.5 (22.3.2).
	 */
	public static double versusFactor(Map<Payload.Versus, Double> versus, Set<Payload.Versus> classes, @Nullable Dao dao, boolean shatter) {
		double factor = 1.0D;
		for (Map.Entry<Payload.Versus, Double> entry : versus.entrySet()) {
			if (classes.contains(entry.getKey())) {
				factor *= entry.getValue();
			}
		}
		if (dao == Dao.FIRE && !versus.containsKey(Payload.Versus.FIRE_IMMUNE) && classes.contains(Payload.Versus.FIRE_IMMUNE)) {
			factor *= 0.5D;
		}
		if (dao == Dao.LIGHTNING && !versus.containsKey(Payload.Versus.WET) && !versus.containsKey(Payload.Versus.FROZEN)
				&& (classes.contains(Payload.Versus.WET) || classes.contains(Payload.Versus.FROZEN))) {
			factor *= 1.25D;
		}
		if (shatter && classes.contains(Payload.Versus.FROZEN)) {
			factor *= 1.5D;
		}
		return factor;
	}

	/** F clamped to [0, 3] (arena [0, 1.5]). */
	public static double clampF(double f, boolean arena) {
		if (!Double.isFinite(f)) {
			return 1.0D;
		}
		return Math.max(0.0D, Math.min(arena ? F_CAP_ARENA : F_CAP, f));
	}

	// ================================================================== final damage (22.5.5)

	/**
	 * The one damage function of the three choke points ({@code Techniques.hurt}, {@code DiscipleTechniques.hurt},
	 * {@code PowerCore.hurt}). LEGACY amounts pass unchanged. Otherwise: raw = coef × P × mastery × F (F ≤ 3); disciples ×
	 * {@code damageFactor}; arena partners get the legacy power (F ≤ 1.5, no mastery) and players at most 15% of max health
	 * per hit and per cast; trial mobs get legacy power × 1.5; the own heart demon at most 6% (ultimates 10%) per hit and
	 * 12% per cast; bosses the cumulative per-cast cap (and 2% per DOT or pulse tick). Records the result in the ledger.
	 */
	public static float finalDamage(DamageContext ctx) {
		LivingEntity attacker = ctx.attacker();
		LivingEntity target = ctx.target();
		if (target == null || attacker == null) {
			return 0.0F;
		}
		if (ctx.kind() == DamageContext.Kind.LEGACY) {
			return sanitize(ctx.raw());
		}
		TechniqueSpec spec = ctx.spec();
		boolean arena = inArena(attacker, target);
		boolean boss = isBoss(target);
		float maxHealth = Math.max(1.0F, target.getMaxHealth());
		double raw;
		switch (ctx.kind()) {
			case PERCENT -> raw = (boss ? ctx.bossCoef() : ctx.coef()) * maxHealth;
			case EXEC -> {
				if (target instanceof Player || arena || boss && target.getHealth() / maxHealth >= EXEC_BOSS_HEALTH) {
					return 0.0F;
				}
				raw = target.getHealth() + target.getAbsorptionAmount() + 1.0D;
			}
			default -> {
				double power = ctx.power();
				double mastery = masteryDamage(ctx.mastery());
				if (arena) {
					power = legacyPower(attacker, spec);
					mastery = 1.0D;
				} else if (isTrialMob(target)) {
					power = legacyPower(attacker, spec) * TRIAL_MOB_FACTOR;
					// Phase 9 (DESIGN 26.2.2, 26.14 item 5): stars matter again against Alam Abadi foes, sub-linearly.
					if (target.entityTags().contains(TAG_ABADI_FOE)) {
						power *= Math.sqrt(attackerStarFactor(attacker));
					}
				}
				raw = ctx.coef() * power * mastery * clampF(ctx.f(), arena);
				// Phase 7 target factors (DESIGN 19.2.8): elements and Perisai Unsur, never in arenas or against players.
				if (!arena && !(target instanceof Player)) {
					raw *= targetFactor(attacker, target, spec);
					// Phase 8 caster factors (DESIGN 25.2 item 6): their own clamp, the same exclusions.
					raw *= casterFactor(attacker, spec, target);
				}
			}
		}
		// A share of max health (DMG%) and an execution are not scaled by the disciple's damage factors.
		if (attacker instanceof DiscipleEntity disciple && ctx.kind() != DamageContext.Kind.EXEC && ctx.kind() != DamageContext.Kind.PERCENT) {
			raw *= DiscipleHooks.damageFactor(disciple, target, true);
		}
		CastLedger ledger = ctx.ledger();
		double already = ledger == null ? 0.0D : ledger.dealt(target.getUUID());
		if (arena && (target instanceof Player || ctx.kind() == DamageContext.Kind.PERCENT)) {
			// players: at most 15% per hit and per cast; a share of max health is held to the same line for every partner
			double share = maxHealth * ARENA_PLAYER_SHARE;
			raw = Math.min(raw, Math.min(share, Math.max(0.0D, share - already)));
		}
		if (attacker instanceof ServerPlayer && isOwnHeartDemon(attacker, target)) {
			boolean ultimate = spec != null && spec.pamungkas();
			double hit = maxHealth * (ultimate ? HEART_DEMON_ULTIMATE_HIT : HEART_DEMON_HIT);
			double cast = maxHealth * HEART_DEMON_CAST;
			raw = Math.min(raw, Math.min(hit, Math.max(0.0D, cast - already)));
		}
		if (boss && ctx.kind() != DamageContext.Kind.EXEC && KultivatorConfig.get().techniqueBossCaps) {
			TechniqueGrade grade = spec == null ? TechniqueGrade.XUAN : spec.grade();
			double cap = bossShare(attacker, grade) * maxHealth;
			raw = Math.min(raw, Math.max(0.0D, cap - already));
			if (ctx.kind().tick()) {
				raw = Math.min(raw, maxHealth * BOSS_TICK_SHARE);
			}
		}
		float result = sanitize(raw);
		if (ledger != null && result > 0.0F) {
			ledger.add(target.getUUID(), result);
		}
		return result;
	}

	/** The attacker's legacy power (D_legacy for players, U_legacy for disciples; 0 for anything else). */
	public static double legacyPowerOf(LivingEntity attacker, @Nullable TechniqueSpec spec) {
		return legacyPower(attacker, spec);
	}

	/** The attacker's legacy power (D_legacy for players, U_legacy for disciples). */
	private static double legacyPower(LivingEntity attacker, @Nullable TechniqueSpec spec) {
		if (attacker instanceof ServerPlayer player) {
			return legacyD(Cultivators.get(player), spec == null ? null : spec.dao());
		}
		if (attacker instanceof DiscipleEntity disciple) {
			return legacyU(disciple.data());
		}
		return 0.0D;
	}

	private static double bossShare(LivingEntity attacker, TechniqueGrade grade) {
		if (attacker instanceof ServerPlayer player) {
			return bossShare(Cultivators.get(player), grade);
		}
		if (attacker instanceof DiscipleEntity disciple) {
			return bossShare(disciple.data(), grade);
		}
		return bossBase(Rank.MORTAL, 0) * grade.bossFactor();
	}

	private static float sanitize(double value) {
		if (!(value > 0.0D) || Double.isNaN(value)) {
			return 0.0F;
		}
		return (float) Math.min(value, Float.MAX_VALUE / 2.0D);
	}

	private TechniquePower() {
	}
}
