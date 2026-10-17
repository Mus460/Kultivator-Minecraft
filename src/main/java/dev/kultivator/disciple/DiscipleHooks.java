package dev.kultivator.disciple;

import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Technique;
import dev.kultivator.data.XpSource;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Extension points of the phase-5 disciple code for the phase-6 modules (DESIGN 20.2.6). Each module registers its
 * providers from its own {@code init()}; the spine calls them at the listed call sites. With no provider registered every
 * call returns exactly the phase-5 value, so nothing changes until a module adds something.
 * <p>
 * Which roles reach a hook: the XP, breakthrough, teaching, feeding, mode, death, kill, {@code tick20} and info hooks run
 * for {@link DiscipleEntity.Role#DISCIPLE} only (the other roles never gain XP or break through). {@code stats},
 * {@code damageFactor}, {@code hurtFactor}, {@code onHurt}, {@code combatCast}, {@code retreatFraction} and
 * {@code regenFactor} run for every role, so providers check {@code entity.role()} themselves (a Divine Clone's 50% is
 * such a provider).
 * <p>
 * Reason strings (blockers) are translation keys that must read well WITHOUT arguments: the disciple screen shows them
 * as {@code Component.translatable(key)}. In chat the spine passes optional arguments (the disciple's name, then the
 * technique or the order), which a key may simply ignore.
 */
public final class DiscipleHooks {

	/** Combined quality bonus of all providers is kept inside this range (DESIGN 20.2.6). */
	public static final double QUALITY_BONUS_MIN = -0.10D;
	public static final double QUALITY_BONUS_MAX = 0.40D;
	/** The product of all strike factors never goes below this. */
	public static final double STRIKE_FACTOR_FLOOR = 0.5D;

	// ================================================================== provider shapes

	/** Collects the attribute modifiers of a {@code stats} provider; they are applied as permanent modifiers. */
	public interface StatSink {
		/** Adds (or, for a repeated id, replaces) one modifier on {@code attribute}. */
		void add(Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation);
	}

	@FunctionalInterface
	public interface XpFactor {
		double factor(@Nullable DiscipleEntity entity, DiscipleData data, XpSource source);
	}

	@FunctionalInterface
	public interface DamageFactor {
		double factor(DiscipleEntity entity, LivingEntity target, boolean technique);
	}

	@FunctionalInterface
	public interface HurtListener {
		void onHurt(DiscipleEntity entity, DamageSource source, float damage);
	}

	@FunctionalInterface
	public interface DeathHandler {
		/** True when the death is handled (cancelled, or the soul fled); the phase-5 death and tablet then do not happen. */
		boolean onDeath(DiscipleEntity entity, DamageSource source);
	}

	@FunctionalInterface
	public interface StrikeFactor {
		/** {@code index} is the strike number, 1 for the first. */
		double factor(DiscipleEntity entity, Rank target, int index);
	}

	@FunctionalInterface
	public interface StrikeSkip {
		/** True to skip the damage of strike {@code index} (Tahan Petir, DESIGN 20.3.18). The strike still counts. */
		boolean skip(DiscipleEntity entity, Rank target, int index);
	}

	@FunctionalInterface
	public interface QualityBonus {
		double bonus(DiscipleEntity entity, Rank target);
	}

	@FunctionalInterface
	public interface QualityFloor {
		@Nullable BreakthroughQuality floor(DiscipleEntity entity, Rank target);
	}

	/** Follows every disciple breakthrough (awakening, qi gathering, mini tribulation). */
	public interface BreakthroughListener {
		default void onStart(DiscipleEntity entity, DiscipleBreakthrough.Kind kind, Rank target) {
		}

		/** After strike {@code index} (1-based) of {@code count} landed (or was skipped). */
		default void onStrike(DiscipleEntity entity, int index, int count) {
		}

		/** The breakthrough succeeded (the new rank is already set) or failed. */
		default void onEnd(DiscipleEntity entity, boolean success, DiscipleBreakthrough.Kind kind) {
		}

		/** The breakthrough stopped without a result (carried away, death, unload, release). */
		default void onCancel(DiscipleEntity entity, DiscipleBreakthrough.Kind kind) {
		}
	}

	@FunctionalInterface
	public interface TeachBlocker {
		@Nullable String blocker(DiscipleEntity entity, ServerPlayer master, Technique technique);
	}

	@FunctionalInterface
	public interface TeachFactor {
		double factor(DiscipleEntity entity, ServerPlayer master, Technique technique);
	}

	@FunctionalInterface
	public interface TeachListener {
		void onTaught(DiscipleEntity entity, ServerPlayer master, Technique technique);
	}

	@FunctionalInterface
	public interface FeedHandler {
		/** True when this provider took the stack (it consumes the item itself); the phase-5 feeding then does not run. */
		boolean feed(DiscipleEntity entity, ServerPlayer master, ItemStack stack);
	}

	@FunctionalInterface
	public interface FeedListener {
		void onFed(DiscipleEntity entity, ServerPlayer master, ItemStack stack);
	}

	@FunctionalInterface
	public interface ModeBlocker {
		@Nullable String blocker(DiscipleEntity entity, DiscipleEntity.Mode mode);
	}

	@FunctionalInterface
	public interface InfoProvider {
		void info(DiscipleEntity entity, ServerPlayer master, CompoundTag tag);
	}

	// ================================================================== registries

	private static final List<Function<DiscipleData, Double>> POTENTIAL = new ArrayList<>();
	private static final List<Function<DiscipleData, Double>> RANK_FACTOR = new ArrayList<>();
	private static final List<XpFactor> XP_FACTOR = new ArrayList<>();
	private static final List<ToDoubleFunction<DiscipleData>> MASTER_SHARE = new ArrayList<>();
	private static final List<Predicate<DiscipleData>> MASTER_ANY_RANGE = new ArrayList<>();
	private static final List<BiConsumer<DiscipleEntity, StatSink>> STATS = new ArrayList<>();
	private static final List<DamageFactor> DAMAGE = new ArrayList<>();
	private static final List<ToDoubleBiFunction<DiscipleEntity, DamageSource>> HURT_FACTOR = new ArrayList<>();
	private static final List<HurtListener> ON_HURT = new ArrayList<>();
	private static final List<DeathHandler> ON_DEATH = new ArrayList<>();
	private static final List<BiPredicate<DiscipleEntity, LivingEntity>> COMBAT_CAST = new ArrayList<>();
	private static final List<Consumer<DiscipleEntity>> TICK20 = new ArrayList<>();
	private static final List<BiConsumer<DiscipleEntity, LivingEntity>> ON_KILL = new ArrayList<>();
	private static final List<StrikeFactor> STRIKE = new ArrayList<>();
	private static final List<StrikeSkip> STRIKE_SKIP = new ArrayList<>();
	private static final List<QualityBonus> QUALITY_BONUS = new ArrayList<>();
	private static final List<QualityFloor> QUALITY_FLOOR = new ArrayList<>();
	private static final List<ToDoubleFunction<DiscipleEntity>> FOUNDATION_CHANCE = new ArrayList<>();
	private static final List<Function<DiscipleEntity, String>> BREAKTHROUGH_BLOCKER = new ArrayList<>();
	private static final List<BreakthroughListener> BREAKTHROUGH_LISTENER = new ArrayList<>();
	private static final List<TeachBlocker> TEACH_BLOCKER = new ArrayList<>();
	private static final List<TeachFactor> TEACH_COST = new ArrayList<>();
	private static final List<Predicate<DiscipleData>> DAO_BYPASS = new ArrayList<>();
	private static final List<TeachListener> ON_TAUGHT = new ArrayList<>();
	private static final List<FeedHandler> FEED_HANDLER = new ArrayList<>();
	private static final List<FeedListener> ON_FED = new ArrayList<>();
	private static final List<ToDoubleBiFunction<DiscipleEntity, ItemStack>> PILL_EFFECT = new ArrayList<>();
	private static final List<ModeBlocker> MODE_BLOCKER = new ArrayList<>();
	private static final List<ToDoubleFunction<DiscipleEntity>> RETREAT = new ArrayList<>();
	private static final List<ToDoubleFunction<DiscipleEntity>> REGEN = new ArrayList<>();
	private static final List<ToDoubleFunction<DiscipleData>> MAX_QI = new ArrayList<>();
	private static final List<InfoProvider> INFO = new ArrayList<>();
	private static final List<ToIntFunction<ServerPlayer>> CAP = new ArrayList<>();

	// ================================================================== registration (call from a module's init)

	/** Replaces the root-grade XP factor; the first provider returning non-null wins (soul colour, DESIGN 20.3.5). */
	public static void addPotentialFactor(Function<DiscipleData, Double> provider) {
		POTENTIAL.add(provider);
	}

	/** Replaces {@link DiscipleCultivation#XP_FACTOR}; the first provider returning non-null wins (sect rank, 20.3.6). */
	public static void addRankFactor(Function<DiscipleData, Double> provider) {
		RANK_FACTOR.add(provider);
	}

	/** Multiplies the disciple XP multiplier (product of all). The entity is null for data whose body is not loaded. */
	public static void addXpFactor(XpFactor provider) {
		XP_FACTOR.add(provider);
	}

	/** Multiplies the 10% share of the master's meditation (product of all). */
	public static void addMasterShareFactor(ToDoubleFunction<DiscipleData> provider) {
		MASTER_SHARE.add(provider);
	}

	/** Any provider returning true lets the master-meditation share work at any distance and in any dimension. */
	public static void addMasterShareAnyRange(Predicate<DiscipleData> provider) {
		MASTER_ANY_RANGE.add(provider);
	}

	/** Attribute modifiers re-applied at every {@code refreshSynced}; a modifier no provider gives any more is removed. */
	public static void addStats(BiConsumer<DiscipleEntity, StatSink> provider) {
		STATS.add(provider);
	}

	/** Multiplies the disciple's damage: technique hits and melee (product of all). */
	public static void addDamageFactor(DamageFactor provider) {
		DAMAGE.add(provider);
	}

	/** Multiplies damage the disciple takes, at the start of {@code hurtServer} (product of all). */
	public static void addHurtFactor(ToDoubleBiFunction<DiscipleEntity, DamageSource> provider) {
		HURT_FACTOR.add(provider);
	}

	/** Called after the disciple really took damage. */
	public static void addOnHurt(HurtListener listener) {
		ON_HURT.add(listener);
	}

	/** Called before the death and the Soul Tablet; the first provider returning true handles it. */
	public static void addOnDeath(DeathHandler handler) {
		ON_DEATH.add(handler);
	}

	/** Asked at the start of every combat cast (each second in a fight); the first provider returning true has cast. */
	public static void addCombatCast(BiPredicate<DiscipleEntity, LivingEntity> provider) {
		COMBAT_CAST.add(provider);
	}

	/** Called once a second for every loaded disciple (role DISCIPLE). Keep it cheap. */
	public static void addTick20(Consumer<DiscipleEntity> listener) {
		TICK20.add(listener);
	}

	/** Called for every living thing the disciple kills (before the phase-5 kill XP, which only counts monsters). */
	public static void addOnKill(BiConsumer<DiscipleEntity, LivingEntity> listener) {
		ON_KILL.add(listener);
	}

	/** Multiplies each mini-tribulation strike (product of all, floored at {@link #STRIKE_FACTOR_FLOOR}). */
	public static void addStrikeFactor(StrikeFactor provider) {
		STRIKE.add(provider);
	}

	/** Any provider returning true skips the damage of that strike (Tahan Petir). */
	public static void addStrikeSkip(StrikeSkip provider) {
		STRIKE_SKIP.add(provider);
	}

	/** Added to the breakthrough quality score (sum of all, kept within -0.10..+0.40). */
	public static void addQualityBonus(QualityBonus provider) {
		QUALITY_BONUS.add(provider);
	}

	/** The breakthrough quality is raised to at least this (the highest floor wins). */
	public static void addQualityFloor(QualityFloor provider) {
		QUALITY_FLOOR.add(provider);
	}

	/** Added to the Qi Refining to Foundation chance (sum of all, before the 5%..98% clamp). */
	public static void addFoundationChance(ToDoubleFunction<DiscipleEntity> provider) {
		FOUNDATION_CHANCE.add(provider);
	}

	/** A translation key why the breakthrough cannot start (the first non-null wins), else null. */
	public static void addBreakthroughBlocker(Function<DiscipleEntity, String> provider) {
		BREAKTHROUGH_BLOCKER.add(provider);
	}

	public static void addBreakthroughListener(BreakthroughListener listener) {
		BREAKTHROUGH_LISTENER.add(listener);
	}

	/** A translation key why the technique cannot be taught (the first non-null wins); it also gets the technique as %2$s. */
	public static void addTeachBlocker(TeachBlocker provider) {
		TEACH_BLOCKER.add(provider);
	}

	/** Multiplies the master's qi cost of teaching (product of all). */
	public static void addTeachCostFactor(TeachFactor provider) {
		TEACH_COST.add(provider);
	}

	/** Any provider returning true lets the disciple learn and cast techniques of every Dao. */
	public static void addDaoBypass(Predicate<DiscipleData> provider) {
		DAO_BYPASS.add(provider);
	}

	public static void addOnTaught(TeachListener listener) {
		ON_TAUGHT.add(listener);
	}

	/** Asked before the phase-5 pill feeding, for the screen's feed button and for right-clicks with any item. */
	public static void addFeedHandler(FeedHandler handler) {
		FEED_HANDLER.add(handler);
	}

	/** Called after the phase-5 feeding accepted a pill. */
	public static void addOnFed(FeedListener listener) {
		ON_FED.add(listener);
	}

	/**
	 * Multiplies a fed pill's effects other than XP (qi, healing, deviation, breakthrough and tribulation bonuses,
	 * comprehension); XP stays on the {@code xpFactor} path. Product of all providers (Taixu blood: x1.5).
	 */
	public static void addPillEffectFactor(ToDoubleBiFunction<DiscipleEntity, ItemStack> provider) {
		PILL_EFFECT.add(provider);
	}

	public static double pillEffectFactor(DiscipleEntity entity, ItemStack stack) {
		double product = 1.0D;
		for (ToDoubleBiFunction<DiscipleEntity, ItemStack> provider : PILL_EFFECT) {
			product *= finite(provider.applyAsDouble(entity, stack));
		}
		return product;
	}

	/** A translation key why the master's order is refused (the first non-null wins); it also gets the order as %2$s. */
	public static void addModeBlocker(ModeBlocker provider) {
		MODE_BLOCKER.add(provider);
	}

	/** The health share under which the disciple runs back to its master (the highest wins, at least 0.25). */
	public static void addRetreatFraction(ToDoubleFunction<DiscipleEntity> provider) {
		RETREAT.add(provider);
	}

	/** Multiplies the out-of-combat regeneration (product of all). */
	public static void addRegenFactor(ToDoubleFunction<DiscipleEntity> provider) {
		REGEN.add(provider);
	}

	/**
	 * Multiplies the disciple's max qi (product of all; {@code DiscipleCultivation.maxQi}). It runs for any data, also of
	 * bodies that are not loaded and of fresh data with empty extras, so keep it cheap and null-safe.
	 */
	public static void addMaxQiFactor(ToDoubleFunction<DiscipleData> provider) {
		MAX_QI.add(provider);
	}

	/** Adds keys to the disciple screen's details (end of {@code DiscipleCultivation.infoFor}). */
	public static void addInfo(InfoProvider provider) {
		INFO.add(provider);
	}

	/** The master's disciple limit; the highest provider value wins (default {@link Disciples#MAX_DISCIPLES}). */
	public static void addCap(ToIntFunction<ServerPlayer> provider) {
		CAP.add(provider);
	}

	// ================================================================== spine calls

	public static double potentialFactor(DiscipleData data) {
		for (Function<DiscipleData, Double> provider : POTENTIAL) {
			Double value = provider.apply(data);
			if (value != null && Double.isFinite(value)) {
				return Math.max(0.0D, value);
			}
		}
		return data.rootGrade().multiplier();
	}

	public static double rankFactor(DiscipleData data) {
		for (Function<DiscipleData, Double> provider : RANK_FACTOR) {
			Double value = provider.apply(data);
			if (value != null && Double.isFinite(value)) {
				return Math.max(0.0D, value);
			}
		}
		return DiscipleCultivation.XP_FACTOR;
	}

	public static double xpFactor(@Nullable DiscipleEntity entity, DiscipleData data, XpSource source) {
		double product = 1.0D;
		for (XpFactor provider : XP_FACTOR) {
			product *= finite(provider.factor(entity, data, source));
		}
		return product;
	}

	public static double masterShareFactor(DiscipleData data) {
		double product = 1.0D;
		for (ToDoubleFunction<DiscipleData> provider : MASTER_SHARE) {
			product *= finite(provider.applyAsDouble(data));
		}
		return product;
	}

	public static boolean masterShareAnyRange(DiscipleData data) {
		for (Predicate<DiscipleData> provider : MASTER_ANY_RANGE) {
			if (provider.test(data)) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasStats() {
		return !STATS.isEmpty();
	}

	public static void stats(DiscipleEntity entity, StatSink sink) {
		for (BiConsumer<DiscipleEntity, StatSink> provider : STATS) {
			provider.accept(entity, sink);
		}
	}

	public static double damageFactor(DiscipleEntity entity, LivingEntity target, boolean technique) {
		double product = 1.0D;
		for (DamageFactor provider : DAMAGE) {
			product *= finite(provider.factor(entity, target, technique));
		}
		return product;
	}

	public static double hurtFactor(DiscipleEntity entity, DamageSource source) {
		double product = 1.0D;
		for (ToDoubleBiFunction<DiscipleEntity, DamageSource> provider : HURT_FACTOR) {
			product *= finite(provider.applyAsDouble(entity, source));
		}
		return product;
	}

	public static void onHurt(DiscipleEntity entity, DamageSource source, float damage) {
		for (HurtListener listener : ON_HURT) {
			listener.onHurt(entity, source, damage);
		}
	}

	public static boolean onDeath(DiscipleEntity entity, DamageSource source) {
		for (DeathHandler handler : ON_DEATH) {
			if (handler.onDeath(entity, source)) {
				return true;
			}
		}
		return false;
	}

	public static boolean combatCast(DiscipleEntity entity, LivingEntity target) {
		for (BiPredicate<DiscipleEntity, LivingEntity> provider : COMBAT_CAST) {
			if (provider.test(entity, target)) {
				return true;
			}
		}
		return false;
	}

	public static void tick20(DiscipleEntity entity) {
		for (Consumer<DiscipleEntity> listener : TICK20) {
			listener.accept(entity);
		}
	}

	public static void onKill(DiscipleEntity entity, LivingEntity victim) {
		for (BiConsumer<DiscipleEntity, LivingEntity> listener : ON_KILL) {
			listener.accept(entity, victim);
		}
	}

	public static double strikeFactor(DiscipleEntity entity, Rank target, int index) {
		if (STRIKE.isEmpty()) {
			return 1.0D;
		}
		double product = 1.0D;
		for (StrikeFactor provider : STRIKE) {
			product *= finite(provider.factor(entity, target, index));
		}
		return Math.max(STRIKE_FACTOR_FLOOR, product);
	}

	public static boolean strikeSkipped(DiscipleEntity entity, Rank target, int index) {
		for (StrikeSkip provider : STRIKE_SKIP) {
			if (provider.skip(entity, target, index)) {
				return true;
			}
		}
		return false;
	}

	public static double qualityBonus(DiscipleEntity entity, Rank target) {
		if (QUALITY_BONUS.isEmpty()) {
			return 0.0D;
		}
		double sum = 0.0D;
		for (QualityBonus provider : QUALITY_BONUS) {
			double value = provider.bonus(entity, target);
			if (Double.isFinite(value)) {
				sum += value;
			}
		}
		return Math.max(QUALITY_BONUS_MIN, Math.min(QUALITY_BONUS_MAX, sum));
	}

	public static @Nullable BreakthroughQuality qualityFloor(DiscipleEntity entity, Rank target) {
		BreakthroughQuality best = null;
		for (QualityFloor provider : QUALITY_FLOOR) {
			BreakthroughQuality floor = provider.floor(entity, target);
			if (floor != null && (best == null || floor.ordinal() > best.ordinal())) {
				best = floor;
			}
		}
		return best;
	}

	public static double foundationChance(DiscipleEntity entity) {
		double sum = 0.0D;
		for (ToDoubleFunction<DiscipleEntity> provider : FOUNDATION_CHANCE) {
			double value = provider.applyAsDouble(entity);
			if (Double.isFinite(value)) {
				sum += value;
			}
		}
		return sum;
	}

	public static @Nullable String breakthroughBlocker(DiscipleEntity entity) {
		for (Function<DiscipleEntity, String> provider : BREAKTHROUGH_BLOCKER) {
			String why = provider.apply(entity);
			if (why != null && !why.isEmpty()) {
				return why;
			}
		}
		return null;
	}

	public static void breakthroughStarted(DiscipleEntity entity, DiscipleBreakthrough.Kind kind, Rank target) {
		for (BreakthroughListener listener : BREAKTHROUGH_LISTENER) {
			listener.onStart(entity, kind, target);
		}
	}

	public static void breakthroughStrike(DiscipleEntity entity, int index, int count) {
		for (BreakthroughListener listener : BREAKTHROUGH_LISTENER) {
			listener.onStrike(entity, index, count);
		}
	}

	public static void breakthroughEnded(DiscipleEntity entity, boolean success, DiscipleBreakthrough.Kind kind) {
		for (BreakthroughListener listener : BREAKTHROUGH_LISTENER) {
			listener.onEnd(entity, success, kind);
		}
	}

	public static void breakthroughCancelled(DiscipleEntity entity, DiscipleBreakthrough.Kind kind) {
		for (BreakthroughListener listener : BREAKTHROUGH_LISTENER) {
			listener.onCancel(entity, kind);
		}
	}

	public static @Nullable String teachBlocker(DiscipleEntity entity, ServerPlayer master, Technique technique) {
		for (TeachBlocker provider : TEACH_BLOCKER) {
			String why = provider.blocker(entity, master, technique);
			if (why != null && !why.isEmpty()) {
				return why;
			}
		}
		return null;
	}

	public static double teachCostFactor(DiscipleEntity entity, ServerPlayer master, Technique technique) {
		double product = 1.0D;
		for (TeachFactor provider : TEACH_COST) {
			product *= finite(provider.factor(entity, master, technique));
		}
		return product;
	}

	public static boolean daoBypass(DiscipleData data) {
		for (Predicate<DiscipleData> provider : DAO_BYPASS) {
			if (provider.test(data)) {
				return true;
			}
		}
		return false;
	}

	public static void onTaught(DiscipleEntity entity, ServerPlayer master, Technique technique) {
		for (TeachListener listener : ON_TAUGHT) {
			listener.onTaught(entity, master, technique);
		}
	}

	public static boolean hasFeedHandlers() {
		return !FEED_HANDLER.isEmpty();
	}

	public static boolean feedHandler(DiscipleEntity entity, ServerPlayer master, ItemStack stack) {
		for (FeedHandler handler : FEED_HANDLER) {
			if (handler.feed(entity, master, stack)) {
				return true;
			}
		}
		return false;
	}

	public static void onFed(DiscipleEntity entity, ServerPlayer master, ItemStack stack) {
		for (FeedListener listener : ON_FED) {
			listener.onFed(entity, master, stack);
		}
	}

	public static @Nullable String modeBlocker(DiscipleEntity entity, DiscipleEntity.Mode mode) {
		for (ModeBlocker provider : MODE_BLOCKER) {
			String why = provider.blocker(entity, mode);
			if (why != null && !why.isEmpty()) {
				return why;
			}
		}
		return null;
	}

	/** At least {@code base} (0.25 in phase 5), raised by any provider. */
	public static double retreatFraction(DiscipleEntity entity, double base) {
		double best = base;
		for (ToDoubleFunction<DiscipleEntity> provider : RETREAT) {
			double value = provider.applyAsDouble(entity);
			if (Double.isFinite(value)) {
				best = Math.max(best, Math.min(0.95D, value));
			}
		}
		return best;
	}

	public static double regenFactor(DiscipleEntity entity) {
		double product = 1.0D;
		for (ToDoubleFunction<DiscipleEntity> provider : REGEN) {
			product *= finite(provider.applyAsDouble(entity));
		}
		return product;
	}

	public static double maxQiFactor(DiscipleData data) {
		double product = 1.0D;
		for (ToDoubleFunction<DiscipleData> provider : MAX_QI) {
			product *= finite(provider.applyAsDouble(data));
		}
		return product;
	}

	public static void info(DiscipleEntity entity, ServerPlayer master, CompoundTag tag) {
		for (InfoProvider provider : INFO) {
			provider.info(entity, master, tag);
		}
	}

	/** The disciple limit of {@code player}: the highest provider value, else {@link Disciples#MAX_DISCIPLES}. */
	public static int cap(ServerPlayer player) {
		if (CAP.isEmpty()) {
			return Disciples.MAX_DISCIPLES;
		}
		int best = 0;
		for (ToIntFunction<ServerPlayer> provider : CAP) {
			best = Math.max(best, provider.applyAsInt(player));
		}
		return best > 0 ? best : Disciples.MAX_DISCIPLES;
	}

	/** A factor that is not a finite, non-negative number counts as 1 (a broken provider changes nothing). */
	private static double finite(double value) {
		return Double.isFinite(value) && value >= 0.0D ? value : 1.0D;
	}

	private DiscipleHooks() {
	}
}
