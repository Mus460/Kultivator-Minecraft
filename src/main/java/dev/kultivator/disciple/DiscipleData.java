package dev.kultivator.disciple;

import dev.kultivator.cultivation.SpiritRoots;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.RootGrade;
import dev.kultivator.data.Technique;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything a disciple (Murid) has cultivated (DESIGN 18): rank, bar, Stored Qi, qi, spiritual root, Dao and its
 * comprehension, learned techniques with their cooldowns, pill cooldowns, breakthrough qualities and counters. Pure
 * data (no entity references); the rules live in {@link DiscipleCultivation}. {@link #save()} / {@link #load} keep every
 * field with a default, so old or partial tags always load. The running breakthrough is session state and never saved:
 * a disciple unloaded mid-breakthrough comes back without it, with no penalty, except after the first strike of a
 * tribulation, which then counts as failed (as for a cultivator who logs out then).
 */
public final class DiscipleData {

	/** Stored qi bigger than this is never kept (protects the save against absurd debug values). */
	private static final double MAX_STORED = 1.0E12D;

	private int realm;
	private int layer;
	private int stage;
	private double xp;
	private double storedQi;
	private double qi;
	private double totalXp;

	private int rootElements;
	private int rootGrade = RootGrade.MID.ordinal();

	private int dao = -1;
	private double comprehension;

	private final Set<String> learned = new LinkedHashSet<>();
	/** Technique id -> ticks until it can be cast again. */
	private final Map<String, Integer> cooldowns = new HashMap<>();
	/** Pill key (see DiscipleCultivation) -> ticks until another pill of that kind may be given. */
	private final Map<String, Integer> pillCooldowns = new HashMap<>();

	/** BreakthroughQuality ordinal per Realm ordinal, -1 = not reached. */
	private final int[] qualities = new int[Realm.values().length];
	private int failedBreakthroughs;
	private int breakthroughs;
	private int deviationTicks;
	private int totalKills;
	private int techniqueCasts;
	private long meditationTicks;
	/** Added to the next Qi Refining -> Foundation chance (Foundation pills, nether star). Used up by any breakthrough. */
	private double nextBreakthroughBonus;
	/** Multiplies the strikes of the next mini tribulation (Golden Core / Warding pills). Used up by any breakthrough. */
	private double nextTribulationFactor = 1.0D;
	/** Rank step whose bottleneck the master was last told about (-1: never). */
	private int notifiedStep = -1;
	/**
	 * Phase-6 data of the other modules (DESIGN 20.4.6): each module writes only its own sub-compound ({@code sect},
	 * {@code talent}, {@code activity}, {@code power}, {@code path}); everyone may read. Saved under {@code "extra"}.
	 */
	private CompoundTag extra = new CompoundTag();

	/** The running breakthrough (session only, never saved; see {@link #save()} for a tribulation left after a strike). */
	private @Nullable DiscipleBreakthrough.Trial trial;
	/** Not saved: a tribulation abandoned by unloading was counted as failed on load; the master has not been told yet. */
	private boolean fledNoticePending;

	public DiscipleData() {
		java.util.Arrays.fill(qualities, -1);
	}

	/** A new Mortal disciple with a freshly rolled spiritual root and a full (small) qi pool. */
	public static DiscipleData fresh(RandomSource random) {
		DiscipleData data = new DiscipleData();
		SpiritRoots.Roll roll = SpiritRoots.roll(random);
		data.setRoot(roll.elements(), roll.grade());
		data.qi = DiscipleCultivation.maxQi(data);
		return data;
	}

	// ================================================================== rank and bar

	public Rank rank() {
		return new Rank(Realm.byOrdinal(realm), layer, stage);
	}

	public Realm realm() {
		return Realm.byOrdinal(realm);
	}

	public int layer() {
		return layer;
	}

	public int stage() {
		return stage;
	}

	/** Sets the rank (clamped by {@link Rank}); the bar is not touched. */
	public void setRank(Rank rank) {
		this.realm = rank.realm().ordinal();
		this.layer = rank.layer();
		this.stage = rank.stage();
	}

	public double xp() {
		return xp;
	}

	public void setXp(double xp) {
		this.xp = Double.isFinite(xp) ? Math.max(0.0D, xp) : 0.0D;
	}

	/** XP that fills the bar at the current rank. */
	public double xpRequired() {
		return rank().xpRequired();
	}

	/** 0..1 how full the bar is. */
	public double barFraction() {
		double required = xpRequired();
		return required <= 0.0D ? 1.0D : Math.max(0.0D, Math.min(1.0D, xp / required));
	}

	public boolean barFull() {
		return xp + 1.0E-6D >= xpRequired();
	}

	public double storedQi() {
		return storedQi;
	}

	public void setStoredQi(double storedQi) {
		this.storedQi = Double.isFinite(storedQi) ? Math.max(0.0D, Math.min(MAX_STORED, storedQi)) : 0.0D;
	}

	public double qi() {
		return qi;
	}

	public void setQi(double qi) {
		this.qi = Double.isFinite(qi) ? Math.max(0.0D, qi) : 0.0D;
	}

	public double totalXp() {
		return totalXp;
	}

	public void addTotalXp(double amount) {
		if (amount > 0.0D && Double.isFinite(amount)) {
			totalXp = Math.min(MAX_STORED, totalXp + amount);
		}
	}

	// ================================================================== root

	public int rootElements() {
		return rootElements;
	}

	public RootGrade rootGrade() {
		return RootGrade.byOrdinal(rootGrade);
	}

	public void setRoot(int elementsMask, RootGrade grade) {
		this.rootElements = elementsMask;
		this.rootGrade = grade.ordinal();
	}

	public void setRootGrade(RootGrade grade) {
		this.rootGrade = grade.ordinal();
	}

	public void setRootElements(int elementsMask) {
		this.rootElements = elementsMask;
	}

	/** RGB of the root's qi (its first element). */
	public int rootColor() {
		return Element.mainColor(rootElements);
	}

	// ================================================================== Dao

	/** The chosen Dao, null for none. */
	public @Nullable Dao dao() {
		return Dao.byOrdinal(dao);
	}

	/** The chosen Dao's ordinal, -1 for none. */
	public int daoIndex() {
		return dao() == null ? -1 : dao;
	}

	public void setDao(@Nullable Dao dao) {
		this.dao = dao == null ? -1 : dao.ordinal();
	}

	/** Comprehension (0..100) of the disciple's own Dao. */
	public double comprehension() {
		return comprehension;
	}

	public void setComprehension(double value) {
		this.comprehension = Double.isFinite(value) ? Math.max(0.0D, Math.min(100.0D, value)) : 0.0D;
	}

	public void addComprehension(double amount) {
		setComprehension(comprehension + amount);
	}

	// ================================================================== techniques

	// Technique ids are strings (DESIGN 22.2.4): every id of the form [a-z0-9_]{1,48} is kept (up to 512), also ids whose
	// spec does not exist (any more); the enum overloads delegate to the string ones.

	public boolean knows(Technique technique) {
		return knows(technique.id());
	}

	public boolean knows(String id) {
		return learned.contains(id);
	}

	/** True when it was new. */
	public boolean learn(Technique technique) {
		return learn(technique.id());
	}

	/** True when it was new; malformed ids and ids beyond the 512 limit are refused. */
	public boolean learn(String id) {
		if (!TechniqueRegistry.savedIdOk(id) || learned.size() >= TechniqueRegistry.MAX_SAVED_IDS && !learned.contains(id)) {
			return false;
		}
		return learned.add(id);
	}

	public void forget(Technique technique) {
		forget(technique.id());
	}

	public void forget(String id) {
		learned.remove(id);
		cooldowns.remove(id);
	}

	/** The known specs in catalogue order; ids without a spec are skipped (they stay saved). */
	public List<TechniqueSpec> learnedSpecs() {
		List<TechniqueSpec> list = new ArrayList<>();
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (learned.contains(spec.id())) {
				list.add(spec);
			}
		}
		return list;
	}

	public void clearTechniques() {
		learned.clear();
		cooldowns.clear();
	}

	/** Ids of the learned techniques (read-only view). */
	public Set<String> learnedIds() {
		return Collections.unmodifiableSet(learned);
	}

	/** Learned techniques in enum order. */
	public List<Technique> learnedTechniques() {
		List<Technique> list = new ArrayList<>();
		for (Technique technique : Technique.values()) {
			if (learned.contains(technique.id())) {
				list.add(technique);
			}
		}
		return list;
	}

	/** Ticks until the technique is ready, 0 when ready. */
	public int cooldownLeft(Technique technique) {
		return cooldownLeft(technique.id());
	}

	/** Ticks until the spec {@code id} is ready, 0 when ready. */
	public int cooldownLeft(String id) {
		return Math.max(0, cooldowns.getOrDefault(id, 0));
	}

	public void setCooldown(Technique technique, int ticks) {
		setCooldown(technique.id(), ticks);
	}

	public void setCooldown(String id, int ticks) {
		if (ticks <= 0) {
			cooldowns.remove(id);
		} else if (TechniqueRegistry.savedIdOk(id) && (cooldowns.containsKey(id) || cooldowns.size() < TechniqueRegistry.MAX_SAVED_IDS)) {
			cooldowns.put(id, ticks);
		}
	}

	/** Ticks until another pill of this kind may be given, 0 when ready. */
	public int pillCooldownLeft(String key) {
		return Math.max(0, pillCooldowns.getOrDefault(key, 0));
	}

	public void setPillCooldown(String key, int ticks) {
		if (ticks <= 0) {
			pillCooldowns.remove(key);
		} else {
			pillCooldowns.put(key, ticks);
		}
	}

	/** Pill key -> ticks left (read-only view). */
	public Map<String, Integer> pillCooldowns() {
		return Collections.unmodifiableMap(pillCooldowns);
	}

	/** Counts every cooldown down by {@code elapsed} ticks and drops the finished ones. */
	public void tickCooldowns(int elapsed) {
		countDown(cooldowns, elapsed);
		countDown(pillCooldowns, elapsed);
	}

	private static void countDown(Map<String, Integer> map, int elapsed) {
		if (map.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<String, Integer>> iterator = map.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, Integer> entry = iterator.next();
			int left = entry.getValue() - elapsed;
			if (left <= 0) {
				iterator.remove();
			} else {
				entry.setValue(left);
			}
		}
	}

	public int techniqueCasts() {
		return techniqueCasts;
	}

	public void addTechniqueCast() {
		techniqueCasts++;
	}

	// ================================================================== breakthroughs

	public @Nullable BreakthroughQuality quality(Realm realm) {
		return BreakthroughQuality.byOrdinal(qualities[realm.ordinal()]);
	}

	public void setQuality(Realm realm, @Nullable BreakthroughQuality quality) {
		qualities[realm.ordinal()] = quality == null ? -1 : quality.ordinal();
	}

	/** Average stat factor of every recorded breakthrough quality (1.0 when none). */
	public double qualityFactor() {
		double sum = 0.0D;
		int count = 0;
		for (Realm each : Realm.values()) {
			BreakthroughQuality quality = each.isMortal() ? null : quality(each);
			if (quality != null) {
				sum += quality.statFactor();
				count++;
			}
		}
		return count == 0 ? 1.0D : sum / count;
	}

	public int failedBreakthroughs() {
		return failedBreakthroughs;
	}

	public void addFailedBreakthrough() {
		failedBreakthroughs++;
	}

	/** Successful breakthroughs (awakening included). */
	public int breakthroughs() {
		return breakthroughs;
	}

	public void addBreakthrough() {
		breakthroughs++;
	}

	public double nextBreakthroughBonus() {
		return nextBreakthroughBonus;
	}

	public void setNextBreakthroughBonus(double bonus) {
		this.nextBreakthroughBonus = Double.isFinite(bonus) ? Math.max(0.0D, Math.min(0.5D, bonus)) : 0.0D;
	}

	public double nextTribulationFactor() {
		return nextTribulationFactor;
	}

	public void setNextTribulationFactor(double factor) {
		this.nextTribulationFactor = Double.isFinite(factor) ? Math.max(0.5D, Math.min(1.0D, factor)) : 1.0D;
	}

	public int notifiedStep() {
		return notifiedStep;
	}

	public void setNotifiedStep(int step) {
		this.notifiedStep = step;
	}

	/** True while a breakthrough (awakening, qi gathering or mini tribulation) runs. Session only. */
	public boolean breakingThrough() {
		return trial != null;
	}

	@Nullable DiscipleBreakthrough.Trial trial() {
		return trial;
	}

	void setTrial(@Nullable DiscipleBreakthrough.Trial trial) {
		this.trial = trial;
	}

	/** True when a tribulation abandoned by unloading counted as failed and the master still has to hear of it. */
	boolean fledNoticePending() {
		return fledNoticePending;
	}

	void setFledNoticePending(boolean pending) {
		this.fledNoticePending = pending;
	}

	// ================================================================== deviation and counters

	public int deviationTicks() {
		return deviationTicks;
	}

	public boolean deviating() {
		return deviationTicks > 0;
	}

	public void setDeviationTicks(int ticks) {
		this.deviationTicks = Math.max(0, ticks);
	}

	public int totalKills() {
		return totalKills;
	}

	public void addKill() {
		totalKills++;
	}

	public long meditationTicks() {
		return meditationTicks;
	}

	public void addMeditationTicks(long ticks) {
		if (ticks > 0L) {
			meditationTicks += ticks;
		}
	}

	/**
	 * Bits for the entity's synced flags that come from cultivation: 2 breaking through, 8 deviating (1 meditating and
	 * 4 fighting are the entity's own).
	 */
	public int cultivationFlags() {
		return (breakingThrough() ? 2 : 0) | (deviating() ? 8 : 0);
	}

	// ================================================================== phase-6 module data

	/**
	 * The live compound of the phase-6 modules (never null, empty by default). Changes are saved with the disciple; a
	 * module edits only its own sub-compound (DESIGN 20.4.6).
	 */
	public CompoundTag extra() {
		return extra;
	}

	/** The sub-compound {@code key} of {@link #extra()}, created (and kept) when missing. */
	public CompoundTag extraSection(String key) {
		CompoundTag section = extra.getCompound(key).orElse(null);
		if (section == null) {
			section = new CompoundTag();
			extra.put(key, section);
		}
		return section;
	}

	// ================================================================== saving

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("realm", realm);
		tag.putInt("layer", layer);
		tag.putInt("stage", stage);
		tag.putDouble("xp", xp);
		tag.putDouble("stored_qi", storedQi);
		tag.putDouble("qi", qi);
		tag.putDouble("total_xp", totalXp);

		tag.putInt("root_elements", rootElements);
		tag.putInt("root_grade", rootGrade);

		tag.putInt("dao", daoIndex());
		tag.putDouble("comprehension", comprehension);

		ListTag known = new ListTag();
		for (String id : learned) {
			known.add(StringTag.valueOf(id));
		}
		tag.put("learned", known);
		CompoundTag cds = new CompoundTag();
		cooldowns.forEach(cds::putInt);
		tag.put("cooldowns", cds);
		CompoundTag pills = new CompoundTag();
		pillCooldowns.forEach(pills::putInt);
		tag.put("pill_cooldowns", pills);

		CompoundTag qual = new CompoundTag();
		for (Realm each : Realm.values()) {
			qual.putInt(each.id(), qualities[each.ordinal()]);
		}
		tag.put("qualities", qual);
		tag.putInt("failed_breakthroughs", failedBreakthroughs);
		tag.putInt("breakthroughs", breakthroughs);
		tag.putInt("deviation_ticks", deviationTicks);
		tag.putInt("total_kills", totalKills);
		tag.putInt("technique_casts", techniqueCasts);
		tag.putLong("meditation_ticks", meditationTicks);
		tag.putDouble("next_breakthrough_bonus", nextBreakthroughBonus);
		tag.putDouble("next_tribulation_factor", nextTribulationFactor);
		tag.putInt("notified_step", notifiedStep);
		tag.put("extra", extra.copy());
		if (trial != null && trial.kind == DiscipleBreakthrough.Kind.TRIBULATION && trial.strikesDone > 0) {
			// Saved or unloaded after the first strike: like a cultivator logging out then, the tribulation counts as failed.
			tag.putBoolean("trial_fled", true);
		}
		return tag;
	}

	public static DiscipleData load(CompoundTag tag) {
		DiscipleData data = new DiscipleData();
		Realm realm = Realm.byOrdinal(tag.getIntOr("realm", 0));
		data.setRank(new Rank(realm, tag.getIntOr("layer", 0), tag.getIntOr("stage", 0)));
		data.setXp(tag.getDoubleOr("xp", 0.0D));
		data.setStoredQi(tag.getDoubleOr("stored_qi", 0.0D));
		data.setQi(tag.getDoubleOr("qi", 0.0D));
		data.totalXp = Math.max(0.0D, tag.getDoubleOr("total_xp", 0.0D));

		data.rootElements = tag.getIntOr("root_elements", 0);
		data.rootGrade = RootGrade.byOrdinal(tag.getIntOr("root_grade", RootGrade.MID.ordinal())).ordinal();

		data.setDao(Dao.byOrdinal(tag.getIntOr("dao", -1)));
		data.setComprehension(tag.getDoubleOr("comprehension", 0.0D));

		// DESIGN 22.2.4: every well-formed id is kept (up to 512), whether or not its spec exists in this version.
		for (Tag entry : tag.getListOrEmpty("learned")) {
			entry.asString().ifPresent(id -> {
				if (TechniqueRegistry.savedIdOk(id) && data.learned.size() < TechniqueRegistry.MAX_SAVED_IDS) {
					data.learned.add(id);
				}
			});
		}
		CompoundTag cds = tag.getCompoundOrEmpty("cooldowns");
		for (String key : cds.keySet()) {
			int left = cds.getIntOr(key, 0);
			if (left > 0 && TechniqueRegistry.savedIdOk(key) && data.cooldowns.size() < TechniqueRegistry.MAX_SAVED_IDS) {
				data.cooldowns.put(key, left);
			}
		}
		CompoundTag pills = tag.getCompoundOrEmpty("pill_cooldowns");
		for (String key : pills.keySet()) {
			int left = pills.getIntOr(key, 0);
			if (left > 0) {
				data.pillCooldowns.put(key, left);
			}
		}

		CompoundTag qual = tag.getCompoundOrEmpty("qualities");
		for (Realm each : Realm.values()) {
			int value = qual.getIntOr(each.id(), -1);
			data.qualities[each.ordinal()] = BreakthroughQuality.byOrdinal(value) == null ? -1 : value;
		}
		data.failedBreakthroughs = Math.max(0, tag.getIntOr("failed_breakthroughs", 0));
		data.breakthroughs = Math.max(0, tag.getIntOr("breakthroughs", 0));
		data.setDeviationTicks(tag.getIntOr("deviation_ticks", 0));
		data.totalKills = Math.max(0, tag.getIntOr("total_kills", 0));
		data.techniqueCasts = Math.max(0, tag.getIntOr("technique_casts", 0));
		data.meditationTicks = Math.max(0L, tag.getLongOr("meditation_ticks", 0L));
		data.setNextBreakthroughBonus(tag.getDoubleOr("next_breakthrough_bonus", 0.0D));
		data.setNextTribulationFactor(tag.getDoubleOr("next_tribulation_factor", 1.0D));
		data.notifiedStep = tag.getIntOr("notified_step", -1);
		data.extra = tag.getCompoundOrEmpty("extra").copy();
		if (tag.getBooleanOr("trial_fled", false)) {
			// The failure of DiscipleBreakthrough.fail, applied now (the clouds ended with the session).
			data.setXp(data.xp * DiscipleBreakthrough.FAIL_BAR_KEEP);
			data.setDeviationTicks(DiscipleBreakthrough.DEVIATION_TICKS);
			data.failedBreakthroughs++;
			data.setNextBreakthroughBonus(0.0D);
			data.setNextTribulationFactor(1.0D);
			data.fledNoticePending = true;
		}
		return data;
	}

	/** A deep copy without the session state. */
	public DiscipleData copy() {
		return load(save());
	}
}
