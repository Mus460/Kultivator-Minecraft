package dev.kultivator.technique.spec.run;

import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.technique.spec.dsl.Feat;
import dev.kultivator.technique.spec.dsl.Route;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Feat counters (DESIGN 22.8.9) in {@code extra.teknik.feat {key: n}}: technique kills and casts with a condition, first
 * boss kills, meditation time at a place, refinements, tribulation results, heart-demon victories and recovered
 * deviations. The hooks are called from existing code ({@code Tribulation}, {@code AlchemyFurnace}, {@code Meditation},
 * {@code HeartDemons}, {@code Deviation}) and from the runner. A counter that reaches its goal lets {@code Unlocks}
 * learn the spec. Test casts and dummies never count.
 */
public final class FeatTracker {

	public static final String KEY = "feat";
	/** MEDITATE_AT counts ticks; its goal is in minutes. */
	private static final int TICKS_PER_MINUTE = 1200;

	private static List<Feat> feats;

	static void init() {
	}

	/** Every distinct feat of the registry (read lazily: the registry is built after the module inits). */
	private static List<Feat> feats() {
		if (feats == null || !TechniqueRegistry.frozen()) {
			List<Feat> list = new ArrayList<>();
			java.util.Set<String> seen = new java.util.HashSet<>();
			for (TechniqueSpec spec : TechniqueRegistry.all()) {
				for (Route route : spec.routes()) {
					if (route.kind() == Route.Kind.FEAT && route.feat() != null && seen.add(route.feat().key())) {
						list.add(route.feat());
					}
				}
			}
			if (TechniqueRegistry.frozen()) {
				feats = List.copyOf(list);
			}
			return list;
		}
		return feats;
	}

	/** Progress of a FEAT route (0 for other routes); MEDITATE_AT in minutes. */
	public static int progress(CultivationData data, Route route) {
		if (route.kind() != Route.Kind.FEAT || route.feat() == null) {
			return 0;
		}
		Feat feat = route.feat();
		if (feat.kind() == Feat.Kind.TRIB) {
			return tribProgress(data, feat);
		}
		int stored = data.extra().getCompoundOrEmpty(Mastery.EXTRA).getCompoundOrEmpty(KEY).getIntOr(feat.key(), 0);
		return feat.kind() == Feat.Kind.MEDITATE_AT ? stored / TICKS_PER_MINUTE : stored;
	}

	/** TRIB feats read the saved results (lifetime strikes, the quality reached at a realm, the average quality). */
	private static int tribProgress(CultivationData data, Feat feat) {
		if (feat.quality() == null) {
			return Tribulation.strikesSurvived(data);
		}
		if (feat.average()) {
			return data.qualityFactor() + 1.0E-6D >= qualityFactor(feat.quality()) ? 1 : 0;
		}
		if (feat.realm() == null) {
			return 0;
		}
		BreakthroughQuality reached = data.quality(feat.realm());
		return reached != null && reached.ordinal() >= feat.quality().ordinal() ? 1 : 0;
	}

	private static double qualityFactor(BreakthroughQuality quality) {
		return switch (quality) {
			case CRACKED -> 0.9D;
			case NORMAL -> 1.0D;
			case PERFECT -> 1.15D;
			case HEAVENLY -> 1.3D;
		};
	}

	/** Sets a counter (debug {@code feat <kind> <n>}: every feat of that kind). Returns how many were set. */
	public static int set(ServerPlayer player, Feat.Kind kind, int value) {
		CultivationData data = Cultivators.get(player);
		CompoundTag counters = counters(data);
		int count = 0;
		for (Feat feat : feats()) {
			if (feat.kind() == kind) {
				counters.putInt(feat.key(), feat.kind() == Feat.Kind.MEDITATE_AT ? value * TICKS_PER_MINUTE : value);
				count++;
			}
		}
		Cultivators.dirty(player);
		Unlocks.refresh(player);
		return count;
	}

	private static CompoundTag counters(CultivationData data) {
		return Mastery.section(Mastery.section(data.extra(), Mastery.EXTRA), KEY);
	}

	/** Adds {@code amount} to a feat; true when it just reached its goal. */
	private static boolean add(CultivationData data, Feat feat, int amount) {
		CompoundTag counters = counters(data);
		int goal = feat.kind() == Feat.Kind.MEDITATE_AT ? feat.goal() * TICKS_PER_MINUTE : feat.goal();
		int before = counters.getIntOr(feat.key(), 0);
		if (before >= goal) {
			return false;
		}
		int after = Math.min(goal, before + amount);
		counters.putInt(feat.key(), after);
		return after >= goal;
	}

	// ================================================================== hooks from the runner

	/** A technique kill (KILLS_WITH and FIRST_KILL). */
	static void onTechniqueKill(ServerPlayer player, TechniqueSpec spec, LivingEntity victim) {
		if (Dummies.isDummy(victim)) {
			return;
		}
		CultivationData data = Cultivators.get(player);
		boolean reached = false;
		boolean changed = false;
		for (Feat feat : feats()) {
			if (feat.kind() == Feat.Kind.KILLS_WITH) {
				if (!matches(feat, spec) || !mobMatches(feat.mobs(), victim) || !Conditions.test(feat.cond(), null, player, victim)) {
					continue;
				}
				reached |= add(data, feat, 1);
				changed = true;
			} else if (feat.kind() == Feat.Kind.FIRST_KILL) {
				if (feat.dao() != null && spec.dao() != feat.dao() || !mobMatches(feat.mobs(), victim)) {
					continue;
				}
				if (!rankReached(data, feat)) {
					continue;
				}
				String bossKey = feat.key() + "#" + BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
				CompoundTag counters = counters(data);
				if (counters.getBooleanOr(bossKey, false)) {
					continue;
				}
				counters.putBoolean(bossKey, true);
				reached |= add(data, feat, 1);
				changed = true;
			}
		}
		if (changed) {
			Cultivators.dirty(player);
		}
		if (reached) {
			Unlocks.refresh(player);
		}
	}

	/** A cast (CASTS_WITH). */
	static void onCast(ServerPlayer player, TechniqueSpec spec) {
		CultivationData data = Cultivators.get(player);
		boolean reached = false;
		boolean changed = false;
		for (Feat feat : feats()) {
			if (feat.kind() == Feat.Kind.CASTS_WITH && (feat.dao() == null || feat.dao() == spec.dao())
					&& Conditions.test(feat.cond(), null, player, null)) {
				reached |= add(data, feat, 1);
				changed = true;
			}
		}
		if (changed) {
			Cultivators.dirty(player);
		}
		if (reached) {
			Unlocks.refresh(player);
		}
	}

	/**
	 * The feat's minimum rank: its realm, and inside Immortal Ascension its layer too ("first_kill(WARDEN, BODY, IA T7)"
	 * counts from Tahap 7 on, not from Tahap 1). A realm above the feat's always counts.
	 */
	private static boolean rankReached(CultivationData data, Feat feat) {
		if (feat.realm() == null) {
			return true;
		}
		dev.kultivator.data.Rank rank = data.rank();
		if (rank.realm() != feat.realm()) {
			return rank.realm().atLeast(feat.realm());
		}
		return feat.layer() <= 0 || rank.layer() >= feat.layer();
	}

	private static boolean matches(Feat feat, TechniqueSpec spec) {
		if (!feat.specId().isEmpty()) {
			return feat.specId().equals(spec.id());
		}
		return feat.dao() == null || feat.dao() == spec.dao();
	}

	/** Entity type ids ({@code minecraft:stray}) or tags ({@code #minecraft:undead}); empty = any mob. */
	static boolean mobMatches(List<String> mobs, LivingEntity victim) {
		if (mobs.isEmpty()) {
			return true;
		}
		Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
		for (String entry : mobs) {
			try {
				if (entry.startsWith("#")) {
					TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(entry.substring(1)));
					if (victim.is(tag)) {
						return true;
					}
				} else if (Identifier.parse(entry.contains(":") ? entry : "minecraft:" + entry).equals(type)) {
					return true;
				}
			} catch (RuntimeException ignored) {
				// A malformed id in a spec never matches.
			}
		}
		return false;
	}

	// ================================================================== hooks from existing code

	/** A tribulation strike was survived ({@code extra.kultivasi.trib_strikes} is already counted by Tribulation). */
	public static void onTribStrike(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		for (Feat feat : feats()) {
			if (feat.kind() == Feat.Kind.TRIB && feat.quality() == null && Tribulation.strikesSurvived(data) == feat.goal()) {
				Unlocks.refresh(player);
				return;
			}
		}
	}

	/** A refinement succeeded. */
	public static void onAlchemy(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		boolean reached = false;
		boolean changed = false;
		for (Feat feat : feats()) {
			if (feat.kind() == Feat.Kind.ALCHEMY_COUNT) {
				reached |= add(data, feat, 1);
				changed = true;
			}
		}
		if (changed) {
			Cultivators.dirty(player);
		}
		if (reached) {
			Unlocks.refresh(player);
		}
	}

	/** {@code ticks} more ticks of meditation at the player's current spot. */
	public static void onMeditate(ServerPlayer player, int ticks) {
		CultivationData data = Cultivators.get(player);
		boolean reached = false;
		boolean changed = false;
		for (Feat feat : feats()) {
			if (feat.kind() == Feat.Kind.MEDITATE_AT && Conditions.test(feat.cond(), null, player, null)) {
				reached |= add(data, feat, ticks);
				changed = true;
			}
		}
		if (changed && player.tickCount % 100 < 20) {
			Cultivators.dirty(player);
		}
		if (reached) {
			Cultivators.dirty(player);
			Unlocks.refresh(player);
		}
	}

	/** The player beat their heart demon (called by {@code HeartDemons} before any breakthrough runs). */
	public static void onHeartDemonWin(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		boolean reached = false;
		for (Feat feat : feats()) {
			if (feat.kind() == Feat.Kind.HEART_DEMON_WIN && (feat.realm() == null || data.realm().ordinal() < feat.realm().ordinal())) {
				reached |= add(data, feat, 1);
			}
		}
		Cultivators.dirty(player);
		if (reached) {
			Unlocks.refresh(player);
		}
	}

	/** A Qi Deviation ran out naturally (called by {@code Deviation}). */
	public static void onDeviationRecovered(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		boolean reached = false;
		for (Feat feat : feats()) {
			if (feat.kind() == Feat.Kind.DEVIATION_RECOVERED && (feat.realm() == null || data.realm().atLeast(feat.realm()))) {
				reached |= add(data, feat, 1);
			}
		}
		Cultivators.dirty(player);
		if (reached) {
			Unlocks.refresh(player);
		}
	}

	private FeatTracker() {
	}
}
