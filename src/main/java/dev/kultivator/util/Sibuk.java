package dev.kultivator.util;

import dev.kultivator.abadi.Abadi;
import dev.kultivator.alam.Alam;
import dev.kultivator.ascension.Ascension;
import dev.kultivator.batin.Batin;
import dev.kultivator.batin.Creation;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.dunia.dimensi.bawah.SoulJourney;
import dev.kultivator.dunia.reruntuhan.menara.Menara;
import dev.kultivator.musuh.SectRaids;
import dev.kultivator.purba.Purba;
import dev.kultivator.sect.Sparring;
import dev.kultivator.technique.source.InheritanceTrials;
import dev.kultivator.trial.HeartDemons;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Sibuk, the one "is this player busy?" door (integration audit T3). Every random scheduler (heart demon pressure, the
 * Heavenly Acknowledgement, ambushes, rival bands, wrath, treasure births, wild dragons...) asks {@link #why} before it
 * starts something on a player, and <b>postpones</b> by {@link #RETRY_TICKS} instead of dropping the event while the
 * player is in real danger or in a ritual (DESIGN 7b "never during real danger").
 * <p>
 * The built-in list is registered lazily from public APIs on the first question, so no init order is needed; modules
 * with private state register their own entry ({@link #register}) or a provider ({@link #addProvider}, for 36/37).
 * Server side only: nothing here touches a client class, a block, the weather or the time.
 * <p>
 * Shared enemy pressure: entries registered with {@link #registerEnemy} are "enemy events" (ambush, rival band, sect
 * raid). At most one of them may run on a player at once, and together at most {@link #DAILY_ENEMY_CAP} start per
 * in-game day ({@link #enemyRefusal}, {@link #enemyTaken}; saved in {@code extra.sibuk}, a new key, so old saves start
 * at zero).
 */
public final class Sibuk {

	/** How long a scheduler waits before asking again when the player is busy (10 s). */
	public static final int RETRY_TICKS = 200;
	/**
	 * Enemy events (ambush, rival band, sect raid) that may start on one player per in-game day, all systems together
	 * (balance change approved by the user: "Ubah angkanya").
	 */
	public static final int DAILY_ENEMY_CAP = 3;
	/** Translation key prefix of the built-in reasons ({@code kultivator.sibuk.<id>}). */
	public static final String KEY_PREFIX = "kultivator.sibuk.";
	/** Sub-compound of {@code CultivationData.extra()} holding the daily enemy count. */
	public static final String EXTRA_KEY = "sibuk";

	/** A module's own busy reason: a translation key, or null when it does not hold the player. */
	@FunctionalInterface
	public interface Provider {
		@Nullable String why(ServerPlayer player);
	}

	private record Entry(String id, Predicate<ServerPlayer> test, boolean enemy) {
	}

	private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();
	private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();
	private static volatile boolean defaults;

	/** Registers (or replaces) the busy check {@code id}; its reason key is {@code kultivator.sibuk.<id>}. */
	public static void register(String id, Predicate<ServerPlayer> busy) {
		put(new Entry(id, busy, false));
	}

	/** Like {@link #register}, and the check counts as a live enemy event for the shared pressure. */
	public static void registerEnemy(String id, Predicate<ServerPlayer> live) {
		put(new Entry(id, live, true));
	}

	/** Adds a provider that names its own reason key (Sekte 36/37 modules, which keep their keys in their fragments). */
	public static void addProvider(Provider provider) {
		PROVIDERS.add(provider);
	}

	private static void put(Entry entry) {
		ENTRIES.removeIf(old -> old.id().equals(entry.id()));
		ENTRIES.add(entry);
	}

	/** Why the player cannot be handed a new event now (a translation key), or null when free. */
	public static @Nullable String why(ServerPlayer player) {
		return whyExcept(player, null);
	}

	/**
	 * Like {@link #why}, but ignores the entry {@code self} (a system asking whether something else holds the player while
	 * its own state is already set).
	 */
	public static @Nullable String whyExcept(ServerPlayer player, @Nullable String self) {
		ensureDefaults();
		for (Entry entry : ENTRIES) {
			if (entry.id().equals(self)) {
				continue;
			}
			if (test(entry, player)) {
				return KEY_PREFIX + entry.id();
			}
		}
		for (Provider provider : PROVIDERS) {
			String key;
			try {
				key = provider.why(player);
			} catch (RuntimeException e) {
				key = null;
			}
			if (key != null) {
				return key;
			}
		}
		return null;
	}

	/** True when anything holds the player. */
	public static boolean busy(ServerPlayer player) {
		return why(player) != null;
	}

	/** The reason as a chat component (a fallback text keeps a missing key readable). */
	public static MutableComponent text(String key) {
		return Component.translatableWithFallback(key, "Busy");
	}

	private static boolean test(Entry entry, ServerPlayer player) {
		try {
			return entry.test().test(player);
		} catch (RuntimeException e) {
			// A module that is switched off or not ready never holds the player.
			return false;
		}
	}

	// ================================================================== shared enemy pressure

	/**
	 * Why no new enemy event may start on the player now (a translation key), or null: another enemy event is live, or
	 * the shared daily count is used up. The caller checks {@link #why} as well (danger, rituals).
	 */
	public static @Nullable String enemyRefusal(ServerPlayer player) {
		ensureDefaults();
		for (Entry entry : ENTRIES) {
			if (entry.enemy() && test(entry, player)) {
				return KEY_PREFIX + "tekanan.satu";
			}
		}
		if (enemyToday(player) >= DAILY_ENEMY_CAP) {
			return KEY_PREFIX + "tekanan.harian";
		}
		return null;
	}

	/** Records that an enemy event ({@code kind}, for the log only) started on the player. */
	public static void enemyTaken(ServerPlayer player, String kind) {
		CultivationData data = Cultivators.get(player);
		CompoundTag tag = data.extra().getCompound(EXTRA_KEY).orElse(null);
		if (tag == null) {
			tag = new CompoundTag();
			data.extra().put(EXTRA_KEY, tag);
		}
		long day = day(player.level().getServer());
		if (tag.getLongOr("day", Long.MIN_VALUE) != day) {
			tag.putLong("day", day);
			tag.putInt("n", 0);
		}
		tag.putInt("n", tag.getIntOr("n", 0) + 1);
		tag.putString("last", kind);
		Cultivators.dirty(player);
	}

	/** Enemy events started on the player this in-game day. */
	public static int enemyToday(ServerPlayer player) {
		CompoundTag tag = Cultivators.get(player).extra().getCompoundOrEmpty(EXTRA_KEY);
		return tag.getLongOr("day", Long.MIN_VALUE) == day(player.level().getServer()) ? tag.getIntOr("n", 0) : 0;
	}

	private static long day(@Nullable MinecraftServer server) {
		return server == null ? 0L : Math.floorDiv(server.overworld().getOverworldClockTime(), 24000L);
	}

	// ================================================================== the built-in list

	/** The checks every module of the base mod shares; registered once, on the first question. */
	private static void ensureDefaults() {
		if (defaults) {
			return;
		}
		synchronized (Sibuk.class) {
			if (defaults) {
				return;
			}
			defaults = true;
			// Put first so modules that registered early (Ambushes, Saingan) keep their place after these.
			List<Entry> early = List.copyOf(ENTRIES);
			ENTRIES.clear();
			ENTRIES.add(new Entry("terobosan", p -> Breakthrough.isBusy(p) || Cultivators.get(p).breakingThrough(), false));
			ENTRIES.add(new Entry("tribulasi", Tribulation::isActive, false));
			ENTRIES.add(new Entry("iblis_hati", HeartDemons::inTrial, false));
			ENTRIES.add(new Entry("duel", p -> DuelRules.inDuel(p) || DuelSessions.byEntity(p) != null || Sparring.inDuel(p), false));
			ENTRIES.add(new Entry("alam", p -> Alam.inRun(p) || Alam.isRealmLevel(p.level()), false));
			ENTRIES.add(new Entry("purba", Purba::inFight, false));
			ENTRIES.add(new Entry("bencana", Purba::inCalamity, false));
			ENTRIES.add(new Entry("murka", Purba::inWrath, false));
			ENTRIES.add(new Entry("pengakuan", Ascension::isActive, false));
			ENTRIES.add(new Entry("kenaikan", Abadi::inAscension, false));
			ENTRIES.add(new Entry("lelang", Abadi::inAuction, false));
			ENTRIES.add(new Entry("warisan", InheritanceTrials::active, false));
			ENTRIES.add(new Entry("ciptaan", Creation::inRitual, false));
			ENTRIES.add(new Entry("batin", Batin::inDefence, false));
			ENTRIES.add(new Entry("jiwa", SoulJourney::isSoul, false));
			ENTRIES.add(new Entry("menara", Menara::inRun, false));
			ENTRIES.add(new Entry("serbuan", p -> SectRaids.active(p.getUUID()), true));
			for (Entry entry : early) {
				put(entry);
			}
		}
	}

	private Sibuk() {
	}
}
