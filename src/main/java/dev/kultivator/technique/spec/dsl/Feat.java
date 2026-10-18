package dev.kultivator.technique.spec.dsl;

import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * A feat route (DESIGN 22.8.9): read-only counters stored in {@code extra.teknik.feat} under {@link #key()}.
 * <ul>
 * <li>{@code KILLS_WITH(dao | spec | any, cond, mobs, n)}: kills by technique with a condition;</li>
 * <li>{@code CASTS_WITH(dao, cond, n)}: casts with a condition;</li>
 * <li>{@code FIRST_KILL(bosses, dao, minRank)}: first boss kills ({@code all} = every boss of the set);</li>
 * <li>{@code MEDITATE_AT(cond, minutes)}: meditation time where the condition holds;</li>
 * <li>{@code ALCHEMY_COUNT(n)}: successful refinements;</li>
 * <li>{@code TRIB(quality ≥ q at a realm, average quality, or lifetime strikes ≥ n)};</li>
 * <li>{@code HEART_DEMON_WIN(beforeRealm)} and {@code DEVIATION_RECOVERED(minRank)}.</li>
 * </ul>
 * {@code mobs} holds entity type ids ({@code minecraft:stray}) or tags ({@code #minecraft:undead}); empty = any mob.
 * {@code realm}/{@code layer} is the minimum rank (FIRST_KILL, DEVIATION_RECOVERED), the realm before which a heart
 * demon must be beaten (HEART_DEMON_WIN) or the realm the tribulation led into (TRIB; layer -1 = any).
 */
public record Feat(Kind kind, @Nullable Dao dao, String specId, Cond cond, List<String> mobs, @Nullable Realm realm,
		int layer, int n, @Nullable BreakthroughQuality quality, boolean average, boolean all) {

	public enum Kind {
		KILLS_WITH, CASTS_WITH, FIRST_KILL, MEDITATE_AT, ALCHEMY_COUNT, TRIB, HEART_DEMON_WIN, DEVIATION_RECOVERED
	}

	/** Kills with a technique of {@code dao} (null = any technique) while {@code cond} holds, of the {@code mobs}. */
	public static Feat killsWith(@Nullable Dao dao, Cond cond, @Nullable List<String> mobs, int n) {
		return new Feat(Kind.KILLS_WITH, dao, "", cond, mobs == null ? List.of() : List.copyOf(mobs), null, -1, n, null, false, false);
	}

	/** Kills with one named technique. */
	public static Feat killsWithSpec(String specId, Cond cond, @Nullable List<String> mobs, int n) {
		return new Feat(Kind.KILLS_WITH, null, specId, cond, mobs == null ? List.of() : List.copyOf(mobs), null, -1, n, null, false, false);
	}

	public static Feat castsWith(@Nullable Dao dao, Cond cond, int n) {
		return new Feat(Kind.CASTS_WITH, dao, "", cond, List.of(), null, -1, n, null, false, false);
	}

	/** First kill of one boss ({@code minecraft:warden}) with a technique of {@code dao} (null = any), from {@code minRealm}. */
	public static Feat firstKill(String boss, @Nullable Dao dao, @Nullable Realm minRealm, int minLayer) {
		return new Feat(Kind.FIRST_KILL, dao, "", Cond.NONE, List.of(boss), minRealm, minLayer, 1, null, false, false);
	}

	public static Feat firstKill(String boss, @Nullable Dao dao) {
		return firstKill(boss, dao, null, -1);
	}

	/** First kills of every boss of the set. */
	public static Feat firstKillAll(@Nullable Dao dao, String... bosses) {
		return new Feat(Kind.FIRST_KILL, dao, "", Cond.NONE, List.of(bosses), null, -1, bosses.length, null, false, true);
	}

	public static Feat meditateAt(Cond where, int minutes) {
		return new Feat(Kind.MEDITATE_AT, null, "", where, List.of(), null, -1, minutes, null, false, false);
	}

	public static Feat alchemyCount(int n) {
		return new Feat(Kind.ALCHEMY_COUNT, null, "", Cond.NONE, List.of(), null, -1, n, null, false, false);
	}

	/** A tribulation into {@code realm} (layer -1 = any layer) with at least {@code quality}. */
	public static Feat tribQuality(Realm realm, int layer, BreakthroughQuality quality) {
		return new Feat(Kind.TRIB, null, "", Cond.NONE, List.of(), realm, layer, 1, quality, false, false);
	}

	/** Average breakthrough quality at least {@code quality}. */
	public static Feat tribAverage(BreakthroughQuality quality) {
		return new Feat(Kind.TRIB, null, "", Cond.NONE, List.of(), null, -1, 1, quality, true, false);
	}

	/** At least {@code n} lifetime survived tribulation strikes ({@code extra.kultivasi.trib_strikes}). */
	public static Feat tribStrikes(int n) {
		return new Feat(Kind.TRIB, null, "", Cond.NONE, List.of(), null, -1, n, null, false, false);
	}

	/** A heart demon beaten before entering {@code beforeRealm}. */
	public static Feat heartDemonWin(Realm beforeRealm) {
		return new Feat(Kind.HEART_DEMON_WIN, null, "", Cond.NONE, List.of(), beforeRealm, -1, 1, null, false, false);
	}

	/** A Qi Deviation recovered from at {@code minRealm} or higher. */
	public static Feat deviationRecovered(Realm minRealm) {
		return new Feat(Kind.DEVIATION_RECOVERED, null, "", Cond.NONE, List.of(), minRealm, -1, 1, null, false, false);
	}

	/** The counter's goal ({@code n}; minutes for MEDITATE_AT). */
	public int goal() {
		return Math.max(1, n);
	}

	/** Stable storage key in {@code extra.teknik.feat} (never derived from translated text). */
	public String key() {
		StringBuilder builder = new StringBuilder(kind.name().toLowerCase(Locale.ROOT));
		builder.append('|').append(dao == null ? (specId.isEmpty() ? "any" : "spec:" + specId) : dao.id());
		builder.append('|').append(cond.key());
		builder.append('|').append(String.join(",", mobs));
		builder.append('|').append(realm == null ? "-" : realm.id()).append(':').append(layer);
		builder.append('|').append(quality == null ? "-" : quality.id()).append(average ? ":avg" : "").append(all ? ":all" : "");
		builder.append('|').append(n);
		return builder.toString();
	}
}
