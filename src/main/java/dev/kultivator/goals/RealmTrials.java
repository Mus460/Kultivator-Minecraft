package dev.kultivator.goals;

import dev.kultivator.Kultivator;
import dev.kultivator.alam.Alam;
import dev.kultivator.desa.Vassals;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.tempa.NatalTreasureSelf;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Ujian Fondasi / Foundation Trials (DESIGN 19.3.9 b): three optional trials for every major breakthrough from Golden
 * Core up. A set opens when the player enters the realm (or Immortal Ascension layer) below its target; counters are a
 * snapshot taken at that moment, "ever" items read lifetime flags. Each completed trial gives +0.07 breakthrough quality
 * for exactly that target, all three a further +0.05 and strikes ×0.9 ("Fondasi Kokoh"). Nothing is mandatory.
 * Saved in {@code extra.goals.trial {target, b0..b3, done, full, prog}}; the table itself is pure (the tablet reads it).
 * <p>
 * Phase 8 (DESIGN 25.8.3) adds an optional fourth row per target from the world features (ambushes, vassals, secret
 * realms, the natal spirit, raids). Any three of the four rows now count as "all three" for Fondasi Kokoh, and at most
 * three rows add quality, so the bonus ceiling is unchanged.
 * <p>
 * Sumber: Terinspirasi dari ISSTH ch. 76 and 100 (a Foundation graded by its cracks) and AWE (Earth-Dao and Heaven-Dao
 * Foundation). The trial contents are game design.
 */
public final class RealmTrials {

	public static final String KEY = "trial";
	public static final double QUALITY_PER_TRIAL = 0.07D;
	public static final double FULL_BONUS = 0.05D;
	public static final double FULL_STRIKE_FACTOR = 0.9D;

	/** What a trial asks. "Counter" kinds count from the snapshot, the others read a lifetime value. */
	public enum Kind {
		TECH_KILLS(true, false),
		MED_SITE15(true, true),
		MED_SITE20(true, true),
		MED_GREAT(true, true),
		MED_HEAVENLY(true, true),
		MED_END(true, true),
		MED_VEIN(true, true),
		ELITES(true, false),
		ELITES2(true, false),
		KINGS(true, false),
		REFINE(true, false),
		ENLIGHTEN(true, false),
		COMPREHENSION(false, false),
		NETHER(false, false),
		WITHER_OR_ELDER(false, false),
		BEAST_OR_DEMON(false, false),
		DRAGON(false, false),
		WARDEN(false, false),
		DIM_TRIBULATION(false, false),
		BOSS_TYPES(false, false),
		SITE_TYPES(false, false),
		// Phase 8 row 4 (DESIGN 25.8.3).
		AMBUSH(false, false),
		VASSAL_OR_ARTIFACT(false, false),
		HEAVENVOID(false, false),
		SPIRIT_OR_RAID(false, false),
		CRYSTALS(true, false),
		ELDERS(true, false),
		TOMB_LAYER(false, false),
		DEWA_OR_TOMB(false, false);

		private final boolean counter;
		private final boolean minutes;

		Kind(boolean counter, boolean minutes) {
			this.counter = counter;
			this.minutes = minutes;
		}

		/** Counted from the snapshot taken when the set opened. */
		public boolean counter() {
			return counter;
		}

		/** Meditation minutes (stored in seconds). */
		public boolean minutes() {
			return minutes;
		}

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** One trial: {@code need} is a count, minutes, a comprehension value, or 1 for an "ever" flag. */
	public record Trial(Kind kind, int need) {
		/** The goal in the stored unit (seconds for meditation minutes). */
		public int goal() {
			return kind.minutes() ? need * 60 : need;
		}
	}

	private static Trial t(Kind kind, int need) {
		return new Trial(kind, need);
	}

	/** The 14 sets: Golden Core, Nascent Soul, Deity Transformation, Dao Union, IA L1-L9, True Immortal (DESIGN 19.3.9). */
	private static final Trial[][] SETS = {
			{t(Kind.TECH_KILLS, 20), t(Kind.MED_SITE15, 20), t(Kind.NETHER, 1)},
			{t(Kind.ELITES, 3), t(Kind.REFINE, 3), t(Kind.COMPREHENSION, 10)},
			{t(Kind.WITHER_OR_ELDER, 1), t(Kind.MED_GREAT, 10), t(Kind.BEAST_OR_DEMON, 1)},
			{t(Kind.COMPREHENSION, 45), t(Kind.ELITES2, 3), t(Kind.MED_END, 10)},
			{t(Kind.DRAGON, 1), t(Kind.ELITES2, 5), t(Kind.MED_SITE20, 15)},
			{t(Kind.COMPREHENSION, 60), t(Kind.MED_HEAVENLY, 30), t(Kind.KINGS, 1)},
			{t(Kind.WARDEN, 1), t(Kind.ENLIGHTEN, 3), t(Kind.REFINE, 5)},
			{t(Kind.DIM_TRIBULATION, 1), t(Kind.ELITES2, 10), t(Kind.MED_VEIN, 20)},
			{t(Kind.BOSS_TYPES, 2), t(Kind.COMPREHENSION, 75), t(Kind.KINGS, 2)},
			{t(Kind.ENLIGHTEN, 5), t(Kind.MED_END, 30), t(Kind.ELITES2, 15)},
			{t(Kind.COMPREHENSION, 90), t(Kind.KINGS, 3), t(Kind.REFINE, 8)},
			{t(Kind.BOSS_TYPES, 4), t(Kind.MED_HEAVENLY, 45), t(Kind.ELITES2, 20)},
			{t(Kind.SITE_TYPES, 6), t(Kind.ENLIGHTEN, 8), t(Kind.KINGS, 5)},
			{t(Kind.SITE_TYPES, 6), t(Kind.BOSS_TYPES, 4), t(Kind.COMPREHENSION, 100)}
	};

	// ================================================================== the table (pure)

	/** The key of a breakthrough target: the realm id, Immortal Ascension with its layer ("immortal_ascension_3"). */
	public static String key(Rank target) {
		return target.realm() == Realm.IMMORTAL_ASCENSION ? target.realm().id() + "_" + (target.layer() + 1) : target.realm().id();
	}

	/** The target whose set is open at {@code rank} (the next major breakthrough, Golden Core or higher), or null. */
	public static @Nullable Rank target(Rank rank) {
		if (rank.isMax()) {
			return null;
		}
		Rank peak = new Rank(rank.realm(), rank.layer(), rank.realm().stages() - 1);
		if (peak.isMax()) {
			return null;
		}
		Rank target = peak.next();
		return target.realm().atLeast(Realm.GOLDEN_CORE) ? target : null;
	}

	/** The open set's key at {@code rank}, or null. */
	public static @Nullable String targetKey(Rank rank) {
		Rank target = target(rank);
		return target == null ? null : key(target);
	}

	/** The target rank of a key (layer 0, stage 0), or null when unknown. */
	public static @Nullable Rank rankOf(String key) {
		if (key.startsWith(Realm.IMMORTAL_ASCENSION.id() + "_")) {
			try {
				int layer = Integer.parseInt(key.substring(Realm.IMMORTAL_ASCENSION.id().length() + 1));
				return new Rank(Realm.IMMORTAL_ASCENSION, layer - 1, 0);
			} catch (NumberFormatException exception) {
				return null;
			}
		}
		Realm realm = Realm.byId(key);
		return realm == null ? null : new Rank(realm, 0, 0);
	}

	/** Artifact power of a Misterius Rendah +0 artifact (base 1.6, DESIGN 25.5.5): the row-4 "Misterius artifact" check. */
	private static final double MISTERIUS_POWER = 1.6D - 1.0E-6D;

	/** The optional fourth row of a target key (DESIGN 25.8.3), or null below Golden Core. */
	public static @Nullable Trial row4(String key) {
		Rank target = rankOf(key);
		if (target == null || !target.realm().atLeast(Realm.GOLDEN_CORE)) {
			return null;
		}
		return switch (target.realm()) {
			case GOLDEN_CORE -> t(Kind.AMBUSH, 1);
			case NASCENT_SOUL -> t(Kind.VASSAL_OR_ARTIFACT, 1);
			case DEITY_TRANSFORMATION -> t(Kind.HEAVENVOID, 1);
			case DAO_UNION -> t(Kind.SPIRIT_OR_RAID, 1);
			case IMMORTAL_ASCENSION -> {
				int layer = target.layer() + 1;
				yield layer <= 3 ? t(Kind.CRYSTALS, 9) : layer <= 6 ? t(Kind.ELDERS, 1) : t(Kind.TOMB_LAYER, 9 * (layer - 6));
			}
			default -> t(Kind.DEWA_OR_TOMB, 1);
		};
	}

	/** The three trials plus the fourth row of a target key, or null below Golden Core. */
	public static @Nullable Trial[] full(String key) {
		Trial[] set = set(key);
		Trial extra = row4(key);
		if (set == null || extra == null) {
			return set;
		}
		Trial[] all = Arrays.copyOf(set, set.length + 1);
		all[set.length] = extra;
		return all;
	}

	/** Fondasi Kokoh: any three of the (up to) four rows (DESIGN 25.8.3). */
	public static boolean isFull(int done) {
		return Integer.bitCount(done & 15) >= 3;
	}

	/** The three trials of a target key, or null below Golden Core. */
	public static @Nullable Trial[] set(String key) {
		Rank target = rankOf(key);
		if (target == null || !target.realm().atLeast(Realm.GOLDEN_CORE)) {
			return null;
		}
		int index = switch (target.realm()) {
			case IMMORTAL_ASCENSION -> 4 + target.layer();
			case TRUE_IMMORTAL -> 13;
			default -> target.realm().ordinal() - Realm.GOLDEN_CORE.ordinal();
		};
		return index >= 0 && index < SETS.length ? SETS[index] : null;
	}

	/** "Golden Core" / "Immortal Ascension 3". */
	public static MutableComponent targetName(String key) {
		Rank target = rankOf(key);
		if (target == null) {
			return Component.literal(key);
		}
		if (target.realm().layers() > 1) {
			return Component.translatable("kultivator.hud.realm_layer", target.realm().displayName(), target.layer() + 1).withColor(target.realm().color());
		}
		return target.realm().displayName().copy().withColor(target.realm().color());
	}

	/** {@code extra.goals.trial} read-only. */
	public static CompoundTag view(CultivationData data) {
		return GoalCounters.view(data).getCompoundOrEmpty(KEY);
	}

	/** Completed trials of the open set for {@code target} (bits 0-3), 0 when the open set is another one. */
	public static int doneFor(CultivationData data, Rank target) {
		CompoundTag trial = view(data);
		return key(target).equals(trial.getStringOr("target", "")) ? trial.getIntOr("done", 0) & 15 : 0;
	}

	/** The quality bonus the trials give the breakthrough into {@code target}. Pure. */
	public static double bonus(CultivationData data, Rank target) {
		int done = doneFor(data, target);
		return Math.min(3, Integer.bitCount(done)) * QUALITY_PER_TRIAL + (isFull(done) ? FULL_BONUS : 0.0D);
	}

	/** The strike factor of "Fondasi Kokoh" for {@code target} (0.9 with all three, else 1). Pure. */
	public static double strikeFactor(CultivationData data, Rank target) {
		return isFull(doneFor(data, target)) ? FULL_STRIKE_FACTOR : 1.0D;
	}

	// ================================================================== server

	static void init() {
		Breakthrough.addQualityBonusProvider((player, data, target) -> bonus(data, target));
		Tribulation.addStrikeFactorProvider((player, target, strikeIndex, last) -> strikeFactor(Cultivators.get(player), target));
	}

	/** Every 100 ticks: opens the set for the current target, updates its progress and the completed trials. */
	static boolean update(ServerPlayer player, CultivationData data) {
		CompoundTag goals = GoalCounters.goals(data);
		String key = targetKey(data.rank());
		Trial[] set = key == null ? null : full(key);
		if (key == null || set == null) {
			if (goals.contains(KEY)) {
				goals.remove(KEY);
				return true;
			}
			return false;
		}
		CompoundTag trial = GoalCounters.section(goals, KEY);
		boolean changed = false;
		if (!key.equals(trial.getStringOr("target", ""))) {
			open(player, data, trial, key, set);
			changed = true;
		}
		int[] prog = new int[set.length * 2];
		int done = trial.getIntOr("done", 0) & 15;
		int before = done;
		for (int i = 0; i < set.length; i++) {
			Trial each = set[i];
			int raw = value(player, data, each.kind());
			int have = each.kind().counter() ? Math.max(0, raw - trial.getIntOr("b" + i, raw)) : raw;
			if (have >= each.goal()) {
				done |= 1 << i;
			}
			// A completed trial stays full (an "ever" flag or a test command never makes it look unfinished again).
			prog[i * 2] = (done & (1 << i)) != 0 ? each.goal() : Math.min(have, each.goal());
			prog[i * 2 + 1] = each.goal();
		}
		int[] old = trial.getIntArray("prog").orElse(new int[0]);
		if (!Arrays.equals(old, prog)) {
			trial.putIntArray("prog", prog);
			changed = true;
		}
		if (done != before) {
			trial.putInt("done", done);
			changed = true;
			for (int i = 0; i < set.length; i++) {
				if ((done & (1 << i)) != 0 && (before & (1 << i)) == 0) {
					Msg.chat(player, Component.translatable("kultivator.goals.trial.done_one", describe(set[i]),
							targetName(key)).withStyle(ChatFormatting.GREEN));
				}
			}
		}
		if (isFull(done) && !trial.getBooleanOr("full", false)) {
			trial.putBoolean("full", true);
			changed = true;
			Msg.chat(player, Component.translatable("kultivator.goals.trial.full", targetName(key)).withStyle(ChatFormatting.GOLD));
			Advancements.grant(player, "foundation_trials");
		}
		return changed;
	}

	private static void open(ServerPlayer player, CultivationData data, CompoundTag trial, String key, Trial[] set) {
		for (String each : java.util.List.copyOf(trial.keySet())) {
			trial.remove(each);
		}
		trial.putString("target", key);
		trial.putInt("done", 0);
		trial.putBoolean("full", false);
		for (int i = 0; i < set.length; i++) {
			if (set[i].kind().counter()) {
				trial.putInt("b" + i, value(player, data, set[i].kind()));
			}
		}
	}

	/** The current raw value of a trial kind: a lifetime counter, a comprehension value, a count, or a 0/1 flag. */
	static int value(ServerPlayer player, CultivationData data, Kind kind) {
		int bosses = GoalCounters.bossBits(data);
		return switch (kind) {
			case TECH_KILLS -> data.techniqueKills();
			case MED_SITE15 -> GoalCounters.value(data, GoalCounters.MED_SITE15);
			case MED_SITE20 -> GoalCounters.value(data, GoalCounters.MED_SITE20);
			case MED_GREAT -> GoalCounters.value(data, GoalCounters.MED_GREAT);
			case MED_HEAVENLY -> GoalCounters.value(data, GoalCounters.MED_HEAVENLY);
			case MED_END -> GoalCounters.value(data, GoalCounters.MED_END);
			case MED_VEIN -> GoalCounters.value(data, GoalCounters.MED_VEIN);
			case ELITES -> GoalCounters.elites(data, 1);
			case ELITES2 -> GoalCounters.elites(data, 2);
			case KINGS -> GoalCounters.elites(data, 3);
			case REFINE -> GoalCounters.refined(data);
			case ENLIGHTEN -> data.enlightenments();
			case COMPREHENSION -> (int) Math.floor(GoalCounters.maxComprehension(data) + 1.0E-6D);
			case NETHER -> (GoalCounters.value(data, GoalCounters.DIMS) & GoalCounters.DIM_NETHER) != 0
					|| player.level().dimension().equals(net.minecraft.world.level.Level.NETHER) ? 1 : 0;
			case WITHER_OR_ELDER -> (bosses & (GoalCounters.BOSS_WITHER | GoalCounters.BOSS_ELDER)) != 0 ? 1 : 0;
			case BEAST_OR_DEMON -> done(player, "spirit_beast") || data.heartDemonsDefeated() > 0 ? 1 : 0;
			case DRAGON -> (bosses & GoalCounters.BOSS_DRAGON) != 0 ? 1 : 0;
			case WARDEN -> (bosses & GoalCounters.BOSS_WARDEN) != 0 ? 1 : 0;
			case DIM_TRIBULATION -> done(player, "nether_tribulation") || done(player, "end_tribulation") ? 1 : 0;
			case BOSS_TYPES -> Integer.bitCount(bosses & 15);
			case SITE_TYPES -> Integer.bitCount(GoalCounters.value(data, GoalCounters.SITES));
			case AMBUSH -> done(player, "ambush_survivor") ? 1 : 0;
			case VASSAL_OR_ARTIFACT -> Vassals.count(player.getUUID()) > 0 || NatalTreasureSelf.grade(player) >= 1 || carriesArtifact(player) ? 1 : 0;
			case HEAVENVOID -> Alam.clears(player, "heavenvoid_hall") > 0 || done(player, "heavenvoid_hall") ? 1 : 0;
			case SPIRIT_OR_RAID -> done(player, "spirit_elementary") || done(player, "raid_repelled") ? 1 : 0;
			case CRYSTALS -> data.extra().getCompoundOrEmpty("dunia").getIntOr("crystals", 0);
			case ELDERS -> Goals.worldCounter(data, Goals.W_ELDERS);
			case TOMB_LAYER -> tombBest(data);
			case DEWA_OR_TOMB -> done(player, "artifact_dewa") || tombBest(data) >= 27 ? 1 : 0;
		};
	}

	/** The best Makam Langit layer ({@code extra.dunia.realm.heavenly_tomb.best}, module A's data, DESIGN 25.14). */
	static int tombBest(CultivationData data) {
		return data.extra().getCompoundOrEmpty("dunia").getCompoundOrEmpty("realm").getCompoundOrEmpty("heavenly_tomb").getIntOr("best", 0);
	}

	/** A forged artifact of Misterius power or more in the inventory. */
	private static boolean carriesArtifact(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.getItem() instanceof ArtifactItem && ArtifactItem.power(stack) >= MISTERIUS_POWER) {
				return true;
			}
		}
		return false;
	}

	/** True when the player has the advancement {@code kultivator:<name>}. */
	static boolean done(ServerPlayer player, String name) {
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(Kultivator.id(name));
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	/** "20 technique kills", "20 min meditating at a site ×1.5+" (shared by the chat, the tablet and the command). */
	public static MutableComponent describe(Trial trial) {
		return Component.translatable("kultivator.goals.trial." + trial.kind().id(), trial.need());
	}

	/** {@code uji jalan ujian selesai}: completes the open set (the next update grants the rewards and the advancement). */
	static boolean completeAll(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		update(player, data);
		CompoundTag trial = GoalCounters.goals(data).getCompound(KEY).orElse(null);
		if (trial == null) {
			return false;
		}
		int[] prog = trial.getIntArray("prog").orElse(new int[0]);
		int rows = Math.max(3, prog.length / 2);
		trial.putInt("done", (1 << rows) - 1);
		for (int i = 0; i + 1 < prog.length; i += 2) {
			prog[i] = prog[i + 1];
		}
		trial.putIntArray("prog", prog);
		trial.putBoolean("full", true);
		Advancements.grant(player, "foundation_trials");
		Cultivators.dirty(player);
		return true;
	}

	/** {@code uji jalan ujian}: the open set and its progress in chat. */
	static int print(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		update(player, data);
		CompoundTag trial = view(data);
		String key = trial.getStringOr("target", "");
		Trial[] set = key.isEmpty() ? null : full(key);
		if (set == null) {
			Msg.chat(player, Component.translatable("kultivator.goals.trial.none").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		Rank target = rankOf(key);
		double bonus = target == null ? 0.0D : bonus(data, target);
		Msg.chat(player, Component.translatable("kultivator.goals.trial.header", targetName(key),
				String.format(java.util.Locale.ROOT, "%+.2f", bonus)).withStyle(ChatFormatting.GOLD));
		int[] prog = trial.getIntArray("prog").orElse(new int[0]);
		int done = trial.getIntOr("done", 0);
		for (int i = 0; i < set.length; i++) {
			int have = i * 2 < prog.length ? prog[i * 2] : 0;
			int goal = set[i].goal();
			String amount = set[i].kind().minutes() ? have / 60 + "/" + goal / 60 : have + "/" + goal;
			boolean ok = (done & (1 << i)) != 0;
			Msg.chat(player, Component.literal(ok ? "✓ " : "✗ ").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)
					.append(describe(set[i]).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" (" + amount + ")").withStyle(ChatFormatting.GRAY)));
		}
		return 1;
	}

	private RealmTrials() {
	}
}
