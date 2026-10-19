package dev.kultivator.technique.spec.run;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.fx.Fx;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Penguasaan Jurus (DESIGN 22.6): points per technique in {@code extra.teknik.m {id: int}} (players) and
 * {@code DiscipleData.extra.teknik.m} (disciples), turned into levels 0-4 by the grade thresholds and the comprehension
 * gates of levels 3 and 4. Per cast: +1, +1 when it hit a monster, +2 when it killed, +3 on a boss, × clamp(base cooldown /
 * 10 s, 0.25, 3); hit and kill points ≤ 200 per technique per in-game day, casts that hit nothing ≤ 20
 * ({@code extra.teknik.mday {id: [day, pts, nohit]}}). Disciples earn at half rate. The reads are pure (the client codex
 * uses them on the synced data).
 */
public final class Mastery {

	/** Point sources (22.6). */
	public enum Source { CAST, HIT, KILL, BOSS, ENLIGHTENMENT, BOOK, SLIP, TEACH, HEIR, COMMAND }

	public static final String EXTRA = "teknik";
	public static final String POINTS = "m";
	public static final String DAILY = "mday";
	public static final int DAILY_HIT_CAP = 200;
	public static final int DAILY_NO_HIT_CAP = 20;
	public static final int ENLIGHTENMENT_POINTS = 10;
	public static final int BOOK_POINTS = 15;
	public static final int SLIP_POINTS = 25;
	public static final double DISCIPLE_RATE = 0.5D;
	private static final int TOAST_COLOR = 0x8FE3C8;

	public static int points(CultivationData data, String id) {
		return data.extra().getCompoundOrEmpty(EXTRA).getCompoundOrEmpty(POINTS).getIntOr(id, 0);
	}

	public static int points(DiscipleData data, String id) {
		return data.extra().getCompoundOrEmpty(EXTRA).getCompoundOrEmpty(POINTS).getIntOr(id, 0);
	}

	/** Level 0-4 of {@code id} for the player (0 for unknown specs). */
	public static int level(CultivationData data, String id) {
		TechniqueSpec spec = TechniqueRegistry.byId(id);
		if (spec == null) {
			return 0;
		}
		int byPoints = TechniquePower.masteryLevel(spec.grade(), points(data, id));
		double comprehension = spec.dao() == null ? data.mainComprehension() : data.comprehension(spec.dao());
		return Math.min(byPoints, TechniquePower.masteryGate(spec, data.realm(), comprehension));
	}

	/** Level 0-4 of {@code id} for a disciple. */
	public static int level(DiscipleData data, String id) {
		TechniqueSpec spec = TechniqueRegistry.byId(id);
		if (spec == null) {
			return 0;
		}
		int byPoints = TechniquePower.masteryLevel(spec.grade(), points(data, id));
		return Math.min(byPoints, TechniquePower.masteryGate(spec, data.realm(), data.comprehension()));
	}

	/** Today's [hit and kill points, no-hit points] of {@code id} (for the codex). */
	public static int[] today(CultivationData data, String id, long day) {
		int[] entry = data.extra().getCompoundOrEmpty(EXTRA).getCompoundOrEmpty(DAILY).getIntArray(id).orElse(new int[0]);
		if (entry.length < 3 || entry[0] != (int) day) {
			return new int[]{0, 0};
		}
		return new int[]{entry[1], entry[2]};
	}

	/** The in-game day the daily caps count in. */
	public static long day(ServerLevel level) {
		return Math.max(0L, level.getServer().overworld().getOverworldClockTime() / 24000L);
	}

	/** Adds mastery points to the player's {@code id} with level-up feedback (no daily cap: callers apply theirs). */
	public static void add(ServerPlayer player, String id, int points, Source source) {
		TechniqueSpec spec = TechniqueRegistry.byId(id);
		if (points == 0 || spec == null) {
			return;
		}
		CultivationData data = Cultivators.get(player);
		int before = level(data, id);
		CompoundTag m = section(section(data.extra(), EXTRA), POINTS);
		m.putInt(id, Math.max(0, Math.min(1_000_000, m.getIntOr(id, 0) + points)));
		int after = level(data, id);
		Cultivators.dirty(player);
		if (after > before) {
			levelUp(player, spec, after);
			// mastery prerequisites (Dewa gates, five_elements_mountain, heavenly_fate_finger) are acted on at once
			Unlocks.refresh(player);
		}
	}

	/** Sets the player's points of {@code id} to the threshold of {@code level} (commands). */
	public static void setLevel(ServerPlayer player, String id, int level) {
		TechniqueSpec spec = TechniqueRegistry.byId(id);
		if (spec == null) {
			return;
		}
		CultivationData data = Cultivators.get(player);
		int before = level(data, id);
		CompoundTag m = section(section(data.extra(), EXTRA), POINTS);
		int target = TechniquePower.masteryThreshold(spec.grade(), Math.max(0, Math.min(TechniquePower.MAX_MASTERY, level)));
		if (target <= 0) {
			m.remove(id);
		} else {
			m.putInt(id, target);
		}
		Cultivators.dirty(player);
		if (level(data, id) > before) {
			Unlocks.refresh(player);
		}
	}

	/**
	 * Comprehension in {@code dao} rose from {@code before} to {@code after}: arts whose level rises only because the
	 * level-3 / level-4 gate (60 / 90) opened with points already banked get the toast. True when any rose.
	 */
	public static boolean gateCrossed(ServerPlayer player, @org.jetbrains.annotations.Nullable dev.kultivator.data.Dao dao, double before, double after) {
		if (!(after > before)) {
			return false;
		}
		CultivationData data = Cultivators.get(player);
		boolean any = false;
		for (TechniqueSpec spec : data.learnedSpecs()) {
			if (spec.dao() != dao && !(spec.dao() == null && dao == data.dao())) {
				continue;
			}
			int byPoints = TechniquePower.masteryLevel(spec.grade(), points(data, spec.id()));
			int was = Math.min(byPoints, TechniquePower.masteryGate(spec, data.realm(), before));
			int now = Math.min(byPoints, TechniquePower.masteryGate(spec, data.realm(), after));
			if (now > was) {
				levelUp(player, spec, now);
				any = true;
			}
		}
		return any;
	}

	/** The points of one cast (22.6), with the daily caps. Test casts, echoes and dummies never get here. */
	static void award(ServerPlayer player, TechniqueSpec spec, boolean hit, boolean kill, boolean boss) {
		CultivationData data = Cultivators.get(player);
		double scale = Math.max(0.25D, Math.min(3.0D, spec.cooldownTicks() / 200.0D));
		double raw = (1.0D + (hit ? 1.0D : 0.0D) + (kill ? 2.0D : 0.0D) + (boss ? 3.0D : 0.0D)) * scale;
		int points = capped(data.extra(), spec.id(), raw, hit, day(player.level()));
		if (points > 0) {
			add(player, spec.id(), points, hit ? Source.HIT : Source.CAST);
		}
	}

	/** A disciple's cast: half rate, the same daily caps, in {@code DiscipleData.extra.teknik}. */
	static void awardDisciple(DiscipleEntity entity, TechniqueSpec spec, boolean hit, boolean kill, boolean boss) {
		DiscipleData data = entity.data();
		double scale = Math.max(0.25D, Math.min(3.0D, spec.cooldownTicks() / 200.0D));
		double raw = (1.0D + (hit ? 1.0D : 0.0D) + (kill ? 2.0D : 0.0D) + (boss ? 3.0D : 0.0D)) * scale * DISCIPLE_RATE;
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		int points = capped(data.extra(), spec.id(), raw, hit, day(level));
		if (points > 0) {
			addDisciple(data, spec.id(), points);
		}
	}

	/** Adds points to a disciple's art (teaching sets a start, slips +25). */
	public static void addDisciple(DiscipleData data, String id, int points) {
		if (points == 0 || TechniqueRegistry.byId(id) == null) {
			return;
		}
		CompoundTag m = section(data.extraSection(EXTRA), POINTS);
		m.putInt(id, Math.max(0, Math.min(1_000_000, m.getIntOr(id, 0) + points)));
	}

	/** Sets a disciple's points of {@code id} to the threshold of {@code level}. */
	public static void setDiscipleLevel(DiscipleData data, String id, int level) {
		TechniqueSpec spec = TechniqueRegistry.byId(id);
		if (spec == null) {
			return;
		}
		CompoundTag m = section(data.extraSection(EXTRA), POINTS);
		int target = TechniquePower.masteryThreshold(spec.grade(), Math.max(0, Math.min(TechniquePower.MAX_MASTERY, level)));
		if (target <= 0) {
			m.remove(id);
		} else {
			m.putInt(id, target);
		}
	}

	/** Applies the daily caps to {@code raw} points and records them; returns the points allowed. */
	private static int capped(CompoundTag extra, String id, double raw, boolean hit, long day) {
		CompoundTag daily = section(section(extra, EXTRA), DAILY);
		int[] entry = daily.getIntArray(id).orElse(new int[0]);
		int pts = 0;
		int noHit = 0;
		if (entry.length >= 3 && entry[0] == (int) day) {
			pts = entry[1];
			noHit = entry[2];
		}
		int wanted = (int) Math.round(raw);
		if (wanted <= 0 && raw > 0.0D) {
			wanted = 1;
		}
		int allowed;
		if (hit) {
			allowed = Math.max(0, Math.min(wanted, DAILY_HIT_CAP - pts));
			pts += allowed;
		} else {
			allowed = Math.max(0, Math.min(wanted, DAILY_NO_HIT_CAP - noHit));
			noHit += allowed;
		}
		daily.putIntArray(id, new int[]{(int) day, pts, noHit});
		return allowed;
	}

	/** The jade seal toast's server side: chat line, chime, golden ring and the advancements of levels 3 and 4. */
	private static void levelUp(ServerPlayer player, TechniqueSpec spec, int level) {
		Component levelName = Component.translatable("kultivator.teknik.mastery." + level);
		Msg.chat(player, Component.translatable("kultivator.teknik.mastery.up", spec.displayName().copy().withColor(0xFFD54A),
				levelName.copy().withColor(TOAST_COLOR)).withColor(TOAST_COLOR));
		Msg.actionBar(player, Component.translatable("kultivator.teknik.mastery.up", spec.displayName(), levelName).withColor(0xFFD54A));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.4F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.6F);
		Fx.ring(player.level(), player.position().add(0.0D, 0.1D, 0.0D), 0xFFD54A, 2.5F);
		if (level >= 3) {
			Advancements.grant(player, "great_perfection");
		}
		if (level >= 4) {
			Advancements.grant(player, "peak_mastery");
		}
	}

	/** The highest mastery level of any known art (the status line). */
	public static int highest(CultivationData data) {
		int best = 0;
		for (TechniqueSpec spec : data.learnedSpecs()) {
			best = Math.max(best, level(data, spec.id()));
		}
		return best;
	}

	/** Mastery threshold of the next level, or -1 at Puncak. */
	public static int nextThreshold(TechniqueGrade grade, int level) {
		return level >= TechniquePower.MAX_MASTERY ? -1 : TechniquePower.masteryThreshold(grade, level + 1);
	}

	/** A child compound of {@code parent}, created when missing. */
	static CompoundTag section(CompoundTag parent, String key) {
		CompoundTag child = parent.getCompound(key).orElse(null);
		if (child == null) {
			child = new CompoundTag();
			parent.put(key, child);
		}
		return child;
	}

	/** The level name ({@code kultivator.teknik.mastery.<0-4>}). */
	public static Component levelName(int level) {
		return Component.translatable("kultivator.teknik.mastery." + Math.max(0, Math.min(4, level)));
	}

	private Mastery() {
	}
}
