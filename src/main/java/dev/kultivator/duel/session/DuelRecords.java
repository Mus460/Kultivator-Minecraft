package dev.kultivator.duel.session;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.Kultivator;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The duel records of the server (DESIGN 35.2.6), global saved data {@code kultivator:duel_records}: per UUID (players,
 * and the {@code soulId} of disciples) wins, losses, draws, Perfect Deflects, Deathblows, blade locks won, the longest
 * chain, Honour (Elo, starting at 1000) and the last eight opponents with the game time. Also the pair cooldowns (Honour
 * and rewards count a pair once per 10 minutes of game time).
 */
public final class DuelRecords extends SavedData {

	public static final int DEFAULT_HONOUR = 1000;
	public static final int K = 24;
	/** A pair counts for Honour and rewards once per this many ticks (10 minutes, like {@code Sparring}). */
	public static final long PAIR_COOLDOWN = 20L * 60L * 10L;
	public static final int RECENT = 8;

	/** One past opponent. */
	public record Recent(UUID opponent, String name, long time, int result) {
		/** {@code result}: 1 won, 0 draw, −1 lost. */
		static final Codec<Recent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				UUIDUtil.CODEC.fieldOf("opponent").forGetter(Recent::opponent),
				Codec.STRING.optionalFieldOf("name", "").forGetter(Recent::name),
				Codec.LONG.optionalFieldOf("time", 0L).forGetter(Recent::time),
				Codec.INT.optionalFieldOf("result", 0).forGetter(Recent::result)
		).apply(instance, Recent::new));
	}

	/** One fighter's record (mutable while in memory). */
	public static final class Entry {
		public int wins;
		public int losses;
		public int draws;
		public int perfects;
		public int deathblows;
		public int bindsWon;
		public int maxChain;
		public int honour = DEFAULT_HONOUR;
		public final List<Recent> recent = new ArrayList<>();

		static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("wins", 0).forGetter(e -> e.wins),
				Codec.INT.optionalFieldOf("losses", 0).forGetter(e -> e.losses),
				Codec.INT.optionalFieldOf("draws", 0).forGetter(e -> e.draws),
				Codec.INT.optionalFieldOf("perfects", 0).forGetter(e -> e.perfects),
				Codec.INT.optionalFieldOf("deathblows", 0).forGetter(e -> e.deathblows),
				Codec.INT.optionalFieldOf("binds_won", 0).forGetter(e -> e.bindsWon),
				Codec.INT.optionalFieldOf("max_chain", 0).forGetter(e -> e.maxChain),
				Codec.INT.optionalFieldOf("honour", DEFAULT_HONOUR).forGetter(e -> e.honour),
				Recent.CODEC.listOf().optionalFieldOf("recent", List.of()).forGetter(e -> List.copyOf(e.recent))
		).apply(instance, (wins, losses, draws, perfects, deathblows, binds, chain, honour, recent) -> {
			Entry entry = new Entry();
			entry.wins = wins;
			entry.losses = losses;
			entry.draws = draws;
			entry.perfects = perfects;
			entry.deathblows = deathblows;
			entry.bindsWon = binds;
			entry.maxChain = chain;
			entry.honour = honour;
			entry.recent.addAll(recent);
			return entry;
		}));

		public int total() {
			return wins + losses + draws;
		}
	}

	public static final Codec<DuelRecords> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC).optionalFieldOf("fighters", Map.of()).forGetter(r -> r.entries),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("pairs", Map.of()).forGetter(r -> r.pairs),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("rewards", Map.of()).forGetter(r -> r.rewards)
	).apply(instance, DuelRecords::new));

	public static final SavedDataType<DuelRecords> TYPE = new SavedDataType<>(Kultivator.id("duel_records"), DuelRecords::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, Entry> entries = new HashMap<>();
	/** "uuidA|uuidB" (sorted) → game time the pair last counted for Honour. */
	private final Map<String, Long> pairs = new HashMap<>();
	/** The same for rewards. */
	private final Map<String, Long> rewards = new HashMap<>();

	public DuelRecords() {
	}

	private DuelRecords(Map<UUID, Entry> entries, Map<String, Long> pairs, Map<String, Long> rewards) {
		this.entries.putAll(entries);
		this.pairs.putAll(pairs);
		this.rewards.putAll(rewards);
	}

	public static DuelRecords get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** The record of {@code id} (null when it never duelled). */
	public @Nullable Entry entry(UUID id) {
		return entries.get(id);
	}

	/** The record of {@code id}, created when missing. */
	public Entry entryOrNew(UUID id) {
		Entry entry = entries.computeIfAbsent(id, key -> new Entry());
		setDirty();
		return entry;
	}

	/** Honour of {@code id} (1000 when it never duelled). */
	public int honour(UUID id) {
		Entry entry = entries.get(id);
		return entry == null ? DEFAULT_HONOUR : entry.honour;
	}

	/** Forgets one record ({@code uji duel catatan reset <pemain>}); null forgets all. */
	public void reset(@Nullable UUID id) {
		if (id == null) {
			entries.clear();
			pairs.clear();
			rewards.clear();
		} else {
			entries.remove(id);
			String key = id.toString();
			pairs.keySet().removeIf(pair -> pair.contains(key));
			rewards.keySet().removeIf(pair -> pair.contains(key));
		}
		setDirty();
	}

	/** True (and remembered) when the pair may count for Honour now: once per {@link #PAIR_COOLDOWN}. */
	public boolean takePair(UUID a, UUID b, long now) {
		return take(pairs, a, b, now);
	}

	/** True (and remembered) when the pair may give rewards now: once per {@link #PAIR_COOLDOWN}. */
	public boolean takeReward(UUID a, UUID b, long now) {
		return take(rewards, a, b, now);
	}

	private boolean take(Map<String, Long> map, UUID a, UUID b, long now) {
		String key = key(a, b);
		Long last = map.get(key);
		if (last != null && now - last < PAIR_COOLDOWN && now >= last) {
			return false;
		}
		map.put(key, now);
		if (map.size() > 4096) {
			map.values().removeIf(time -> now - time > PAIR_COOLDOWN);
		}
		setDirty();
		return true;
	}

	private static String key(UUID a, UUID b) {
		String x = a.toString();
		String y = b.toString();
		return x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x;
	}

	/** Notes one finished duel for {@code id} against {@code opponent}. */
	public void note(UUID id, UUID opponent, String name, long time, int result) {
		Entry entry = entryOrNew(id);
		if (result > 0) {
			entry.wins++;
		} else if (result < 0) {
			entry.losses++;
		} else {
			entry.draws++;
		}
		entry.recent.add(0, new Recent(opponent, name == null ? "" : name, time, result));
		while (entry.recent.size() > RECENT) {
			entry.recent.remove(entry.recent.size() - 1);
		}
		setDirty();
	}

	// ================================================================ Honour (Elo)

	/** The expected score of A against B: {@code 1 / (1 + 10^((Rb − Ra) / 400))}. */
	public static double expected(int ra, int rb) {
		return 1.0D / (1.0D + Math.pow(10.0D, (rb - ra) / 400.0D));
	}

	/** The change of A's Honour for a score of 1 / 0.5 / 0 ({@code K} doubled by an Honour stake). */
	public static int delta(int ra, int rb, double score, boolean honourStake) {
		int k = honourStake ? K * 2 : K;
		return (int) Math.round(k * (score - expected(ra, rb)));
	}

	/** The title of an Honour (35.2.6): {@code kultivator.duel.title.<id>}. */
	public static String titleId(int honour) {
		if (honour < 900) {
			return "berkarat";
		}
		if (honour < 1100) {
			return "pendekar";
		}
		if (honour < 1300) {
			return "ternama";
		}
		if (honour < 1500) {
			return "tanpa_tanding";
		}
		return "santo";
	}

	public static Component title(int honour) {
		return Component.translatable("kultivator.duel.title." + titleId(honour));
	}
}
