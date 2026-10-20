package dev.kultivator.pusakalangit;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.network.Payloads;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * The Titah Langit settings and counters on the PLAYER (DESIGN 29.2.2, 29.6.2), in {@code extra.titah}: {@code sel}
 * (the selected decree), {@code s0..s3} {k, d, v, h, m} per decree, {@code rec} (game time the shared seal recovery
 * ends), {@code cast} (bit mask of decrees cast for real, for {@code titah_all}), {@code rd}/{@code rc} (the reward day
 * {@code getGameTime() / 24000} and its count) and {@code granted} (the Panglima's seal was given). Every value the
 * client sends is clamped to its steps and the realm limit here; the menu's data goes out as {@code Payloads.TitahSync}.
 */
public final class TitahSettings {

	public static final String EXTRA = "titah";
	/** Kekuatan steps (log), 29.2.2. */
	public static final float[] K_STEPS = {0.1F, 0.25F, 0.5F, 0.75F, 1.0F, 1.5F, 2.0F, 3.0F, 4.0F, 5.0F, 7.0F, 10.0F};
	/** Kecepatan steps. */
	public static final float[] V_STEPS = {0.3F, 0.5F, 0.75F, 1.0F, 1.5F, 2.0F, 3.0F};
	/** Kerusakan steps in percent (0-100 by 10). */
	public static final int D_STEP = 10;

	public static final int FIELD_K = 0;
	public static final int FIELD_D = 1;
	public static final int FIELD_V = 2;
	public static final int FIELD_H = 3;
	public static final int FIELD_M = 4;
	/** Tingkat 1-9 (DESIGN 29.16.3): {@code OP_SET} with field 5 takes the text "1".."9". */
	public static final int FIELD_T = 5;
	/** The highest Tingkat there is. */
	public static final int T_MAX = 9;

	public static final int OP_SYNC = 0;
	public static final int OP_SET = 1;
	public static final int OP_SELECT = 2;
	public static final int OP_RESET = 3;

	/** {@code extra.titah}, created on first write. */
	static CompoundTag section(CultivationData data) {
		CompoundTag extra = data.extra();
		CompoundTag tag = extra.getCompoundOrEmpty(EXTRA);
		if (!extra.contains(EXTRA)) {
			extra.put(EXTRA, tag);
		}
		return tag;
	}

	/** The read-only view (an empty tag when nothing is stored). */
	static CompoundTag view(CultivationData data) {
		return data.extra().getCompoundOrEmpty(EXTRA);
	}

	/** The stored settings of {@code skill}, sanitised to the player's realm limits (Kekuatan and Tingkat). */
	static Titah.Settings read(ServerPlayer player, Titah.Skill skill) {
		CultivationData data = Cultivators.get(player);
		boolean bypass = Titah.bypass(player);
		return sanitize(raw(data, skill), bypass ? 10.0F : Titah.kMax(data), tierLimit(player, data));
	}

	/** The Tingkat this player may use now: {@code titahMaxTier} under the bypass, else {@code min(tierMax, titahMaxTier)}. */
	static int tierLimit(ServerPlayer player, CultivationData data) {
		return Titah.bypass(player) ? Titah.freeTierLimit() : Titah.tierLimit(data);
	}

	/** The stored settings without the realm limit (defaults where nothing is stored; a missing Tingkat reads as 1). */
	static Titah.Settings raw(CultivationData data, Titah.Skill skill) {
		CompoundTag s = view(data).getCompoundOrEmpty("s" + skill.ordinal());
		return new Titah.Settings(s.getFloatOr("k", 1.0F), s.getIntOr("d", 100) / 100.0F, s.getFloatOr("v", 1.0F), s.getBooleanOr("h", true),
				s.getBooleanOr("m", true), s.getIntOr("t", 1));
	}

	/** Snaps every value to its steps; Kekuatan never above {@code kMax}; Tingkat 1-9. */
	static Titah.Settings sanitize(Titah.Settings in, float kMax) {
		return sanitize(in, kMax, T_MAX);
	}

	/** Snaps every value to its steps; Kekuatan never above {@code kMax}, Tingkat never above {@code tMax} (29.16.2). */
	static Titah.Settings sanitize(Titah.Settings in, float kMax, int tMax) {
		float k = snap(K_STEPS, finite(in.kekuatan(), 1.0F));
		while (k > kMax + 1.0E-4F && k > K_STEPS[0]) {
			k = below(K_STEPS, k);
		}
		int d = Math.round(Math.max(0.0F, Math.min(1.0F, finite(in.kerusakan(), 1.0F))) * 100.0F / D_STEP) * D_STEP;
		float v = snap(V_STEPS, finite(in.kecepatan(), 1.0F));
		int t = Math.max(1, Math.min(Math.max(1, Math.min(T_MAX, tMax)), in.tingkat()));
		return new Titah.Settings(k, d / 100.0F, v, in.hancur(), in.monumen(), t);
	}

	private static float finite(float value, float fallback) {
		return Float.isFinite(value) ? value : fallback;
	}

	/** The nearest step. */
	static float snap(float[] steps, float value) {
		float best = steps[0];
		for (float step : steps) {
			if (Math.abs(step - value) < Math.abs(best - value)) {
				best = step;
			}
		}
		return best;
	}

	/** The step just below {@code value} (the first step at least). */
	private static float below(float[] steps, float value) {
		float best = steps[0];
		for (float step : steps) {
			if (step < value - 1.0E-4F) {
				best = step;
			}
		}
		return best;
	}

	static void write(ServerPlayer player, Titah.Skill skill, Titah.Settings settings) {
		CultivationData data = Cultivators.get(player);
		CompoundTag s = new CompoundTag();
		s.putFloat("k", settings.kekuatan());
		s.putInt("d", Math.round(settings.kerusakan() * 100.0F));
		s.putFloat("v", settings.kecepatan());
		s.putBoolean("h", settings.hancur());
		s.putBoolean("m", settings.monumen());
		s.putInt("t", Math.max(1, Math.min(T_MAX, settings.tingkat())));
		section(data).put("s" + skill.ordinal(), s);
		Cultivators.dirty(player);
	}

	/** The selected decree (the seal's right-click casts it). */
	static Titah.Skill selected(CultivationData data) {
		return Titah.Skill.byOrdinal(view(data).getIntOr("sel", 0));
	}

	static void select(ServerPlayer player, Titah.Skill skill) {
		section(Cultivators.get(player)).putInt("sel", skill.ordinal());
		Cultivators.dirty(player);
	}

	static long recoverUntil(CultivationData data) {
		return view(data).getLongOr("rec", 0L);
	}

	static void setRecover(CultivationData data, long until) {
		section(data).putLong("rec", until);
	}

	/** Marks a real cast of {@code skill}; true when this completed all four (once). */
	static boolean markCast(CultivationData data, Titah.Skill skill) {
		CompoundTag tag = section(data);
		int before = tag.getIntOr("cast", 0);
		int after = before | 1 << skill.ordinal();
		tag.putInt("cast", after);
		return after == 0xF && before != 0xF;
	}

	/**
	 * One pull-out reward of today, if any is left: {@code titahRewardsPerDay} per game day counted by
	 * {@code getGameTime() / 24000} (sleeping through the night does not reset it). True when the reward may be given
	 * (the count is taken).
	 */
	static boolean takeReward(ServerPlayer player, long gameTime) {
		int limit = KultivatorConfig.get().titahRewardsPerDay;
		if (limit <= 0) {
			return false;
		}
		CultivationData data = Cultivators.get(player);
		CompoundTag tag = section(data);
		long day = gameTime / 24000L;
		int count = tag.getLongOr("rd", -1L) == day ? tag.getIntOr("rc", 0) : 0;
		if (count >= limit) {
			return false;
		}
		tag.putLong("rd", day);
		tag.putInt("rc", count + 1);
		Cultivators.dirty(player);
		return true;
	}

	/** {@code uji pusaka_langit reset}: cooldowns, settings, the recovery and the daily reward counter. */
	static void reset(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		CompoundTag tag = section(data);
		boolean granted = tag.getBooleanOr("granted", false);
		int cast = tag.getIntOr("cast", 0);
		for (String key : java.util.List.copyOf(tag.keySet())) {
			tag.remove(key);
		}
		tag.putBoolean("granted", granted);
		tag.putInt("cast", cast);
		long now = player.level().getGameTime();
		for (Titah.Skill skill : Titah.Skill.values()) {
			data.setCooldown(skill.specId(), now, 0);
		}
		Cultivators.dirty(player);
	}

	// ================================================================== the menu (C10b.5)

	/**
	 * {@code Actions.TITAH}: {@code arg = op << 16 | skill << 8 | field}, {@code text} = the value. op 0 sync, 1 set, 2
	 * select, 3 reset this decree. Every value is clamped here; the answer is always a fresh sync.
	 */
	static void handle(ServerPlayer player, int arg, String text) {
		int op = arg >>> 16 & 0xFF;
		Titah.Skill skill = Titah.Skill.byOrdinal(arg >>> 8 & 0xFF);
		int field = arg & 0xFF;
		switch (op) {
			case OP_SET -> {
				set(player, skill, field, text == null ? "" : text.trim());
				if (field == FIELD_T || field == FIELD_H) {
					// the seal's tooltip shows the Tingkat and "Permanen" (29.16.3)
					HeavenDecreeSealItem.refreshView(player);
				}
			}
			case OP_SELECT -> {
				select(player, skill);
				HeavenDecreeSealItem.refreshView(player);
			}
			case OP_RESET -> write(player, skill, Titah.Settings.DEFAULT);
			default -> {
			}
		}
		sync(player);
	}

	private static void set(ServerPlayer player, Titah.Skill skill, int field, String text) {
		CultivationData data = Cultivators.get(player);
		Titah.Settings now = raw(data, skill);
		int t = now.tingkat();
		Titah.Settings next = switch (field) {
			case FIELD_K -> new Titah.Settings(parse(text, now.kekuatan()), now.kerusakan(), now.kecepatan(), now.hancur(), now.monumen(), t);
			case FIELD_D -> new Titah.Settings(now.kekuatan(), parse(text, now.kerusakan() * 100.0F) / 100.0F, now.kecepatan(), now.hancur(),
					now.monumen(), t);
			case FIELD_V -> new Titah.Settings(now.kekuatan(), now.kerusakan(), parse(text, now.kecepatan()), now.hancur(), now.monumen(), t);
			case FIELD_H -> new Titah.Settings(now.kekuatan(), now.kerusakan(), now.kecepatan(), flag(text, now.hancur()), now.monumen(), t);
			case FIELD_M -> new Titah.Settings(now.kekuatan(), now.kerusakan(), now.kecepatan(), now.hancur(), flag(text, now.monumen()), t);
			case FIELD_T -> now.withTingkat(Math.round(parse(text, t)));
			default -> now;
		};
		write(player, skill, sanitize(next, Titah.bypass(player) ? 10.0F : Titah.kMax(data), tierLimit(player, data)));
	}

	/**
	 * {@code /kultivasi titah tingkat <1-9> [decree]}: stores the Tingkat of {@code skill}, clamped to the player's limit.
	 * Returns the Tingkat now stored.
	 */
	static int setTier(ServerPlayer player, Titah.Skill skill, int tier) {
		set(player, skill, FIELD_T, String.valueOf(tier));
		return raw(Cultivators.get(player), skill).tingkat();
	}

	private static float parse(String text, float fallback) {
		try {
			float value = Float.parseFloat(text.replace(',', '.'));
			return Float.isFinite(value) ? value : fallback;
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static boolean flag(String text, boolean fallback) {
		if ("1".equals(text) || "true".equalsIgnoreCase(text)) {
			return true;
		}
		if ("0".equals(text) || "false".equalsIgnoreCase(text)) {
			return false;
		}
		return fallback;
	}

	/** Sends the menu's data (C10b.5) to the player. */
	static void sync(ServerPlayer player) {
		Payloads.TitahSync.send(player, build(player));
	}

	/** The TitahSync tag (documented on {@code Payloads.TitahSync}). */
	static CompoundTag build(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		KultivatorConfig config = KultivatorConfig.get();
		long now = player.level().getGameTime();
		boolean bypass = Titah.bypass(player);
		float kMax = bypass ? 10.0F : Titah.kMax(data);
		int tMax = tierLimit(player, data);
		String tWhy = Titah.tierWhy(data, bypass);
		boolean kehancuranOn = Kehancuran.selfOn(player);
		CompoundTag out = new CompoundTag();
		out.putInt("selected", selected(data).ordinal());
		out.putBoolean("kehancuranOn", kehancuranOn);
		out.putBoolean("kawahOn", Kehancuran.kawahOn(player));
		out.putBoolean("bypass", bypass);
		out.putInt("recover", (int) Math.max(0L, Math.min(Integer.MAX_VALUE, recoverUntil(data) - now)));
		ListTag skills = new ListTag();
		for (Titah.Skill skill : Titah.Skill.values()) {
			Titah.Settings s = sanitize(raw(data, skill), kMax, tMax);
			Component gate = bypass ? null : Titah.gate(data, skill);
			CompoundTag entry = new CompoundTag();
			entry.putBoolean("unlocked", gate == null);
			entry.putString("why", gate == null ? "" : gateKey(data, skill));
			entry.putInt("cdLeft", (int) Math.max(0L, Math.min(Integer.MAX_VALUE, data.cooldownLeft(skill.specId(), now))));
			entry.putInt("cdMax", Titah.cooldownTicks(skill, s.kekuatan(), s.tingkat()));
			entry.putFloat("k", s.kekuatan());
			entry.putInt("d", Math.round(s.kerusakan() * 100.0F));
			entry.putFloat("v", s.kecepatan());
			entry.putBoolean("h", s.hancur());
			entry.putBoolean("m", s.monumen());
			entry.putFloat("kMax", kMax);
			// DESIGN 29.16.3 (contract C10b.8): the used Tingkat, its limit and reason, and whether this cast would be permanent
			boolean perm = permanent(s, kehancuranOn);
			entry.putInt("t", s.tingkat());
			entry.putInt("tMax", tMax);
			entry.putString("tWhy", tWhy);
			entry.putBoolean("perm", perm);
			Readout r = readout(skill, s, config, perm);
			entry.putString("readout", r.line());
			CompoundTag ro = new CompoundTag();
			ro.putInt("rVis", r.rVis());
			ro.putInt("rDmg", r.rDmg());
			ro.putInt("blocks", r.blocks());
			ro.putInt("cdSec", r.cdSec());
			ro.putInt("qiPct", r.qiPct());
			ro.putInt("carveR", r.carveR());
			ro.putInt("tier", r.tier());
			ro.putInt("dmgR", r.rDmg());
			ro.putInt("weapon", r.weapon());
			ro.putBoolean("permanent", r.permanent());
			entry.put("ro", ro);
			skills.add(entry);
		}
		out.put("skills", skills);
		return out;
	}

	private static String gateKey(CultivationData data, Titah.Skill skill) {
		return !data.realm().atLeast(dev.kultivator.data.Realm.TRUE_IMMORTAL) ? "kultivator.pusaka_langit.refuse.realm"
				: "kultivator.pusaka_langit.stage." + skill.stage();
	}

	/** True when a cast with {@code s} would carve permanently (29.16.3 {@code perm}: Tingkat ≥ 6, Kehancuran on, "Hancurkan tanah"). */
	static boolean permanent(Titah.Settings s, boolean kehancuranOn) {
		return s.tingkat() >= Titah.PERMANENT_TIER && kehancuranOn && s.hancur();
	}

	/**
	 * The readout numbers of one decree (29.2.2, 29.16.3): R, the damage reach, the requested carve radius, the block cap,
	 * the cooldown, qi, the Tingkat and the planted weapon's length (0 for Bintang and Gunung).
	 */
	record Readout(int rVis, int rDmg, int blocks, int cdSec, int qiPct, int carveR, String line, int tier, int weapon, boolean permanent) {
		/** The chat line ({@code kultivator.pusaka_langit.tier.readout}). */
		Component component() {
			Component perm = permanent ? Component.translatable("kultivator.pusaka_langit.tier.readout.permanent") : Component.empty();
			return Component.translatable("kultivator.pusaka_langit.tier.readout", tier, rVis, rDmg, carveR, perm, (cdSec + 59) / 60, qiPct);
		}
	}

	static Readout readout(Titah.Skill skill, Titah.Settings s, KultivatorConfig config) {
		return readout(skill, s, config, false);
	}

	static Readout readout(Titah.Skill skill, Titah.Settings s, KultivatorConfig config, boolean permanent) {
		int tier = Math.max(1, Math.min(T_MAX, s.tingkat()));
		float rVis = TitahSkill.visualRadius(skill, s.kekuatan(), tier);
		int rDmg = (int) Math.round(TitahSkill.damageRadius(rVis, tier));
		int carve = TitahSkill.carveRadius(rVis, tier);
		int cdSec = Titah.cooldownTicks(skill, s.kekuatan(), tier) / 20;
		int qi = (int) Math.round(TitahArts.QI_PCT);
		int blocks = tier <= 1 ? config.titahBlocksPerCast : permanent ? config.titahBulkMaxBlocks
				: Math.min(config.titahBulkMaxBlocks, config.titahUndoMaxBlocks);
		int weapon = TitahSkill.weaponLength(skill, s.kekuatan(), tier);
		String line = String.format(Locale.ROOT, "T%d · R ≈ %d b · ≤ %d b · ≤ %d b%s · %d min · qi %d %%", tier, Math.round(rVis), rDmg, carve,
				permanent ? " · PERMANEN" : "", (cdSec + 59) / 60, qi);
		return new Readout(Math.round(rVis), rDmg, blocks, cdSec, qi, carve, line, tier, weapon, permanent);
	}

	private TitahSettings() {
	}
}
