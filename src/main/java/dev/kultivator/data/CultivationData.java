package dev.kultivator.data;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Everything the mod remembers about one cultivator. Stored in the {@code kultivator:cultivation} attachment (persistent,
 * copied on death), so it survives respawn, relog and dimension change. The same class is used on the client for the
 * snapshot sent by {@code SyncSelf}.
 * <p>
 * Saved fields are written in {@link #save()} and read with defaults in {@link #load(CompoundTag)}, so new fields can be
 * added at any time without breaking old saves. "Session" fields (meditating, flying...) are never saved: they are reset
 * on join and respawn, but they are sent to the owner's client with the snapshot.
 * <p>
 * After changing anything call {@code Cultivators.dirty(player)}.
 */
public final class CultivationData {

	public static final Codec<CultivationData> CODEC = CompoundTag.CODEC.xmap(CultivationData::load, CultivationData::save);

	// Visual flags the player can switch off (cosmetics shown to everyone).
	public static final int COSMETIC_AURA = 1;
	public static final int COSMETIC_ROBE = 1 << 1;
	public static final int COSMETIC_BACK_SWORD = 1 << 2;
	public static final int COSMETIC_EYES = 1 << 3;
	public static final int COSMETIC_DAO_CIRCLE = 1 << 4;
	public static final int COSMETICS_ALL = COSMETIC_AURA | COSMETIC_ROBE | COSMETIC_BACK_SWORD | COSMETIC_EYES | COSMETIC_DAO_CIRCLE;

	// ------------------------------------------------------------------ saved: rank
	private int realm;
	private int layer;
	private int stage;
	private double xp;
	private double storedQi;
	private double qi;
	private double totalXp;

	// ------------------------------------------------------------------ saved: root
	private boolean rootTested;
	private int rootElements;
	private int rootGrade = RootGrade.MID.ordinal();

	// ------------------------------------------------------------------ saved: dao
	private int dao = -1;
	private final double[] comprehension = new double[Dao.COUNT];

	// ------------------------------------------------------------------ saved: techniques
	private final Set<String> learned = new LinkedHashSet<>();
	private String selected = "";
	/** Technique id -> game time when it is ready again. */
	private final Map<String, Long> cooldowns = new HashMap<>();

	// ------------------------------------------------------------------ saved: breakthroughs
	/** Per realm ordinal: the BreakthroughQuality ordinal reached, -1 when not reached. */
	private final int[] qualities = new int[Realm.values().length];
	private int failedBreakthroughs;
	private int tribulationsSurvived;
	/** Multiplies the next tribulation's strike damage (pills), reset after it. */
	private double nextTribulationFactor = 1.0D;
	/** Added to the next breakthrough's chance / quality score (pills), reset after it. */
	private double nextBreakthroughBonus;
	private boolean hardcore;
	/** The player died during a tribulation: apply the failure after respawn. */
	private boolean pendingFailure;

	// ------------------------------------------------------------------ saved: afflictions
	private int deviationTicks;
	private double toxicity;
	private double heartDemonPressure;
	private int heartDemonsDefeated;
	/** Bit per realm ordinal: the heart demon trial before entering that realm was passed. */
	private int trialsPassed;

	// ------------------------------------------------------------------ saved: misc
	private long meditationTicks;
	private int enlightenments;
	private double alchemyXp;
	private int pillsRefined;
	private int techniqueKills;
	private int cosmetics = COSMETICS_ALL;
	/** The True Immortal ascension event has been completed. */
	private boolean ascended;
	/** Free-form extra data for modules (each module uses its own sub-key). */
	private CompoundTag extra = new CompoundTag();

	// ------------------------------------------------------------------ session (not saved)
	private boolean meditating;
	private boolean flyingSword;
	private boolean breakingThrough;
	private boolean domainActive;
	private boolean dharmaActive;
	private boolean enlightened;
	private double siteMultiplier = 1.0D;
	private double veinDensity = 1.0D;
	private int formationTier;
	private int sparringWith = -1;

	public CultivationData() {
		Arrays.fill(qualities, -1);
		qualities[Realm.MORTAL.ordinal()] = BreakthroughQuality.NORMAL.ordinal();
	}

	// ================================================================== rank

	public Rank rank() {
		return new Rank(Realm.byOrdinal(realm), layer, stage);
	}

	public Realm realm() {
		return Realm.byOrdinal(realm);
	}

	public void setRank(Rank rank) {
		this.realm = rank.realm().ordinal();
		this.layer = rank.layer();
		this.stage = rank.stage();
	}

	public double xp() {
		return xp;
	}

	public void setXp(double xp) {
		this.xp = Math.max(0.0D, xp);
	}

	/** Qi Tertimbun: XP that could not go into the bar because of a bottleneck. Never lost. */
	public double storedQi() {
		return storedQi;
	}

	public void setStoredQi(double storedQi) {
		this.storedQi = Math.max(0.0D, storedQi);
	}

	/** Current spiritual energy, spent by techniques. The maximum comes from {@code Qi.max}. */
	public double qi() {
		return qi;
	}

	public void setQi(double qi) {
		this.qi = Math.max(0.0D, qi);
	}

	public double totalXp() {
		return totalXp;
	}

	public void addTotalXp(double amount) {
		this.totalXp += Math.max(0.0D, amount);
	}

	/** 0..1 how full the cultivation bar is. */
	public double barFraction() {
		double required = rank().xpRequired();
		return required <= 0.0D ? 0.0D : Math.min(1.0D, xp / required);
	}

	public boolean barFull() {
		return xp >= rank().xpRequired() - 1.0E-6D;
	}

	// ================================================================== root

	public boolean rootTested() {
		return rootTested;
	}

	public int rootElements() {
		return rootElements;
	}

	public RootGrade rootGrade() {
		return RootGrade.byOrdinal(rootGrade);
	}

	/** Records the root rolled at the ceremony. It is never re-rolled, only improved by pills. */
	public void setRoot(int elementsMask, RootGrade grade) {
		this.rootTested = true;
		this.rootElements = elementsMask;
		this.rootGrade = grade.ordinal();
	}

	public void setRootGrade(RootGrade grade) {
		this.rootGrade = grade.ordinal();
	}

	public void setRootElements(int elementsMask) {
		this.rootElements = elementsMask;
	}

	/** Forgets the root (debug reset only). */
	public void clearRoot() {
		this.rootTested = false;
		this.rootElements = 0;
		this.rootGrade = RootGrade.MID.ordinal();
	}

	// ================================================================== dao

	public @Nullable Dao dao() {
		return Dao.byOrdinal(dao);
	}

	public void setDao(@Nullable Dao dao) {
		this.dao = dao == null ? -1 : dao.ordinal();
	}

	public double comprehension(Dao dao) {
		return comprehension[dao.ordinal()];
	}

	public void setComprehension(Dao dao, double value) {
		comprehension[dao.ordinal()] = Math.max(0.0D, Math.min(100.0D, value));
	}

	public void addComprehension(Dao dao, double amount) {
		setComprehension(dao, comprehension(dao) + amount);
	}

	/** Comprehension of the chosen Dao, 0 without one. */
	public double mainComprehension() {
		Dao chosen = dao();
		return chosen == null ? 0.0D : comprehension(chosen);
	}

	// ================================================================== techniques

	public boolean knows(Technique technique) {
		return learned.contains(technique.id());
	}

	/** True if it was not known before. */
	public boolean learn(Technique technique) {
		return learned.add(technique.id());
	}

	public void forget(Technique technique) {
		learned.remove(technique.id());
		if (selected.equals(technique.id())) {
			selected = "";
		}
	}

	public Set<String> learnedIds() {
		return java.util.Collections.unmodifiableSet(learned);
	}

	public java.util.List<Technique> learnedTechniques() {
		java.util.List<Technique> list = new java.util.ArrayList<>();
		for (Technique technique : Technique.values()) {
			if (learned.contains(technique.id())) {
				list.add(technique);
			}
		}
		return list;
	}

	public void clearTechniques() {
		learned.clear();
		cooldowns.clear();
		selected = "";
	}

	public @Nullable Technique selected() {
		return Technique.byId(selected);
	}

	public void setSelected(@Nullable Technique technique) {
		this.selected = technique == null ? "" : technique.id();
	}

	/** Ticks until the technique is ready, 0 when ready. */
	public long cooldownLeft(Technique technique, long gameTime) {
		Long readyAt = cooldowns.get(technique.id());
		return readyAt == null ? 0L : Math.max(0L, readyAt - gameTime);
	}

	public void setCooldown(Technique technique, long gameTime, int ticks) {
		cooldowns.put(technique.id(), gameTime + Math.max(0, ticks));
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
			if (each.isMortal()) {
				continue;
			}
			BreakthroughQuality quality = quality(each);
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

	public int tribulationsSurvived() {
		return tribulationsSurvived;
	}

	public void addTribulationSurvived() {
		tribulationsSurvived++;
	}

	public double nextTribulationFactor() {
		return nextTribulationFactor;
	}

	public void setNextTribulationFactor(double factor) {
		this.nextTribulationFactor = Math.max(0.5D, Math.min(1.0D, factor));
	}

	public double nextBreakthroughBonus() {
		return nextBreakthroughBonus;
	}

	public void setNextBreakthroughBonus(double bonus) {
		this.nextBreakthroughBonus = Math.max(0.0D, Math.min(0.5D, bonus));
	}

	public boolean hardcore() {
		return hardcore;
	}

	public void setHardcore(boolean hardcore) {
		this.hardcore = hardcore;
	}

	public boolean pendingFailure() {
		return pendingFailure;
	}

	public void setPendingFailure(boolean pendingFailure) {
		this.pendingFailure = pendingFailure;
	}

	// ================================================================== afflictions

	public int deviationTicks() {
		return deviationTicks;
	}

	public boolean deviating() {
		return deviationTicks > 0;
	}

	public void setDeviationTicks(int ticks) {
		this.deviationTicks = Math.max(0, ticks);
	}

	public double toxicity() {
		return toxicity;
	}

	public void setToxicity(double toxicity) {
		this.toxicity = Math.max(0.0D, Math.min(100.0D, toxicity));
	}

	public double heartDemonPressure() {
		return heartDemonPressure;
	}

	public void setHeartDemonPressure(double pressure) {
		this.heartDemonPressure = Math.max(0.0D, Math.min(200.0D, pressure));
	}

	public int heartDemonsDefeated() {
		return heartDemonsDefeated;
	}

	public void addHeartDemonDefeated() {
		heartDemonsDefeated++;
	}

	public boolean trialPassed(Realm realm) {
		return (trialsPassed & (1 << realm.ordinal())) != 0;
	}

	public void setTrialPassed(Realm realm, boolean passed) {
		trialsPassed = passed ? trialsPassed | (1 << realm.ordinal()) : trialsPassed & ~(1 << realm.ordinal());
	}

	// ================================================================== misc

	public long meditationTicks() {
		return meditationTicks;
	}

	public void addMeditationTicks(long ticks) {
		meditationTicks += Math.max(0L, ticks);
	}

	public int enlightenments() {
		return enlightenments;
	}

	public void addEnlightenment() {
		enlightenments++;
	}

	public double alchemyXp() {
		return alchemyXp;
	}

	public void setAlchemyXp(double alchemyXp) {
		this.alchemyXp = Math.max(0.0D, alchemyXp);
	}

	public int pillsRefined() {
		return pillsRefined;
	}

	public void addPillRefined() {
		pillsRefined++;
	}

	public int techniqueKills() {
		return techniqueKills;
	}

	public void addTechniqueKill() {
		techniqueKills++;
	}

	public int cosmetics() {
		return cosmetics;
	}

	public boolean cosmetic(int flag) {
		return (cosmetics & flag) != 0;
	}

	public void setCosmetic(int flag, boolean on) {
		cosmetics = on ? cosmetics | flag : cosmetics & ~flag;
	}

	public boolean ascended() {
		return ascended;
	}

	public void setAscended(boolean ascended) {
		this.ascended = ascended;
	}

	/** Module-owned extra data. Use a sub-compound named after your module; call dirty() after changing it. */
	public CompoundTag extra() {
		return extra;
	}

	// ================================================================== session

	public boolean meditating() {
		return meditating;
	}

	public void setMeditating(boolean meditating) {
		this.meditating = meditating;
	}

	public boolean flyingSword() {
		return flyingSword;
	}

	public void setFlyingSword(boolean flyingSword) {
		this.flyingSword = flyingSword;
	}

	/** A breakthrough (awakening, chance roll, heart demon trial or tribulation) is in progress. */
	public boolean breakingThrough() {
		return breakingThrough;
	}

	public void setBreakingThrough(boolean breakingThrough) {
		this.breakingThrough = breakingThrough;
	}

	public boolean domainActive() {
		return domainActive;
	}

	public void setDomainActive(boolean domainActive) {
		this.domainActive = domainActive;
	}

	public boolean dharmaActive() {
		return dharmaActive;
	}

	public void setDharmaActive(boolean dharmaActive) {
		this.dharmaActive = dharmaActive;
	}

	public boolean enlightened() {
		return enlightened;
	}

	public void setEnlightened(boolean enlightened) {
		this.enlightened = enlightened;
	}

	/** Spiritual-site multiplier at the meditation spot (HUD). */
	public double siteMultiplier() {
		return siteMultiplier;
	}

	public void setSiteMultiplier(double siteMultiplier) {
		this.siteMultiplier = siteMultiplier;
	}

	/** Spirit vein density at the meditation spot, 0..1 (HUD). */
	public double veinDensity() {
		return veinDensity;
	}

	public void setVeinDensity(double veinDensity) {
		this.veinDensity = veinDensity;
	}

	/** Qi gathering formation tier around the meditation spot, 0 for none (HUD). */
	public int formationTier() {
		return formationTier;
	}

	public void setFormationTier(int formationTier) {
		this.formationTier = formationTier;
	}

	/** Entity id of the sparring partner, -1 for none. */
	public int sparringWith() {
		return sparringWith;
	}

	public void setSparringWith(int entityId) {
		this.sparringWith = entityId;
	}

	/** Clears everything that only lasts for one session (join, respawn). */
	public void resetSession() {
		meditating = false;
		flyingSword = false;
		breakingThrough = false;
		domainActive = false;
		dharmaActive = false;
		enlightened = false;
		siteMultiplier = 1.0D;
		veinDensity = 1.0D;
		formationTier = 0;
		sparringWith = -1;
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

		tag.putBoolean("root_tested", rootTested);
		tag.putInt("root_elements", rootElements);
		tag.putInt("root_grade", rootGrade);

		tag.putInt("dao", dao);
		CompoundTag comp = new CompoundTag();
		for (Dao each : Dao.values()) {
			if (comprehension[each.ordinal()] > 0.0D) {
				comp.putDouble(each.id(), comprehension[each.ordinal()]);
			}
		}
		tag.put("comprehension", comp);

		ListTag known = new ListTag();
		for (String id : learned) {
			known.add(StringTag.valueOf(id));
		}
		tag.put("learned", known);
		tag.putString("selected", selected);
		CompoundTag cds = new CompoundTag();
		cooldowns.forEach(cds::putLong);
		tag.put("cooldowns", cds);

		CompoundTag qual = new CompoundTag();
		for (Realm each : Realm.values()) {
			qual.putInt(each.id(), qualities[each.ordinal()]);
		}
		tag.put("qualities", qual);
		tag.putInt("failed_breakthroughs", failedBreakthroughs);
		tag.putInt("tribulations_survived", tribulationsSurvived);
		tag.putDouble("next_tribulation_factor", nextTribulationFactor);
		tag.putDouble("next_breakthrough_bonus", nextBreakthroughBonus);
		tag.putBoolean("hardcore", hardcore);
		tag.putBoolean("pending_failure", pendingFailure);

		tag.putInt("deviation_ticks", deviationTicks);
		tag.putDouble("toxicity", toxicity);
		tag.putDouble("heart_demon_pressure", heartDemonPressure);
		tag.putInt("heart_demons_defeated", heartDemonsDefeated);
		tag.putInt("trials_passed", trialsPassed);

		tag.putLong("meditation_ticks", meditationTicks);
		tag.putInt("enlightenments", enlightenments);
		tag.putDouble("alchemy_xp", alchemyXp);
		tag.putInt("pills_refined", pillsRefined);
		tag.putInt("technique_kills", techniqueKills);
		tag.putInt("cosmetics", cosmetics);
		tag.putBoolean("ascended", ascended);
		tag.put("extra", extra.copy());
		return tag;
	}

	public static CultivationData load(CompoundTag tag) {
		CultivationData data = new CultivationData();
		Realm realm = Realm.byOrdinal(tag.getIntOr("realm", 0));
		data.setRank(new Rank(realm, tag.getIntOr("layer", 0), tag.getIntOr("stage", 0)));
		data.xp = Math.max(0.0D, tag.getDoubleOr("xp", 0.0D));
		data.storedQi = Math.max(0.0D, tag.getDoubleOr("stored_qi", 0.0D));
		data.qi = Math.max(0.0D, tag.getDoubleOr("qi", 0.0D));
		data.totalXp = Math.max(0.0D, tag.getDoubleOr("total_xp", 0.0D));

		data.rootTested = tag.getBooleanOr("root_tested", false);
		data.rootElements = tag.getIntOr("root_elements", 0);
		data.rootGrade = RootGrade.byOrdinal(tag.getIntOr("root_grade", RootGrade.MID.ordinal())).ordinal();

		Dao chosen = Dao.byOrdinal(tag.getIntOr("dao", -1));
		data.dao = chosen == null ? -1 : chosen.ordinal();
		CompoundTag comp = tag.getCompoundOrEmpty("comprehension");
		for (Dao each : Dao.values()) {
			data.setComprehension(each, comp.getDoubleOr(each.id(), 0.0D));
		}

		ListTag known = tag.getListOrEmpty("learned");
		for (Tag entry : known) {
			entry.asString().ifPresent(id -> {
				if (Technique.byId(id) != null) {
					data.learned.add(id);
				}
			});
		}
		String selectedId = tag.getStringOr("selected", "");
		data.selected = Technique.byId(selectedId) != null ? selectedId : "";
		CompoundTag cds = tag.getCompoundOrEmpty("cooldowns");
		for (String key : cds.keySet()) {
			data.cooldowns.put(key, cds.getLongOr(key, 0L));
		}

		CompoundTag qual = tag.getCompoundOrEmpty("qualities");
		for (Realm each : Realm.values()) {
			int value = qual.getIntOr(each.id(), data.qualities[each.ordinal()]);
			data.qualities[each.ordinal()] = BreakthroughQuality.byOrdinal(value) == null ? -1 : value;
		}
		data.failedBreakthroughs = tag.getIntOr("failed_breakthroughs", 0);
		data.tribulationsSurvived = tag.getIntOr("tribulations_survived", 0);
		data.setNextTribulationFactor(tag.getDoubleOr("next_tribulation_factor", 1.0D));
		data.setNextBreakthroughBonus(tag.getDoubleOr("next_breakthrough_bonus", 0.0D));
		data.hardcore = tag.getBooleanOr("hardcore", false);
		data.pendingFailure = tag.getBooleanOr("pending_failure", false);

		data.setDeviationTicks(tag.getIntOr("deviation_ticks", 0));
		data.setToxicity(tag.getDoubleOr("toxicity", 0.0D));
		data.setHeartDemonPressure(tag.getDoubleOr("heart_demon_pressure", 0.0D));
		data.heartDemonsDefeated = tag.getIntOr("heart_demons_defeated", 0);
		data.trialsPassed = tag.getIntOr("trials_passed", 0);

		data.meditationTicks = tag.getLongOr("meditation_ticks", 0L);
		data.enlightenments = tag.getIntOr("enlightenments", 0);
		data.alchemyXp = Math.max(0.0D, tag.getDoubleOr("alchemy_xp", 0.0D));
		data.pillsRefined = tag.getIntOr("pills_refined", 0);
		data.techniqueKills = tag.getIntOr("technique_kills", 0);
		data.cosmetics = tag.getIntOr("cosmetics", COSMETICS_ALL);
		data.ascended = tag.getBooleanOr("ascended", false);
		data.extra = tag.getCompoundOrEmpty("extra").copy();
		return data;
	}

	/** Snapshot for the owner's client: the saved fields plus the session fields. */
	public CompoundTag saveForClient() {
		CompoundTag tag = save();
		CompoundTag session = new CompoundTag();
		session.putBoolean("meditating", meditating);
		session.putBoolean("flying_sword", flyingSword);
		session.putBoolean("breaking_through", breakingThrough);
		session.putBoolean("domain", domainActive);
		session.putBoolean("dharma", dharmaActive);
		session.putBoolean("enlightened", enlightened);
		session.putDouble("site", siteMultiplier);
		session.putDouble("density", veinDensity);
		session.putInt("formation", formationTier);
		session.putInt("sparring", sparringWith);
		tag.put("session", session);
		return tag;
	}

	public static CultivationData loadFromServer(CompoundTag tag) {
		CultivationData data = load(tag);
		CompoundTag session = tag.getCompoundOrEmpty("session");
		data.meditating = session.getBooleanOr("meditating", false);
		data.flyingSword = session.getBooleanOr("flying_sword", false);
		data.breakingThrough = session.getBooleanOr("breaking_through", false);
		data.domainActive = session.getBooleanOr("domain", false);
		data.dharmaActive = session.getBooleanOr("dharma", false);
		data.enlightened = session.getBooleanOr("enlightened", false);
		data.siteMultiplier = session.getDoubleOr("site", 1.0D);
		data.veinDensity = session.getDoubleOr("density", 1.0D);
		data.formationTier = session.getIntOr("formation", 0);
		data.sparringWith = session.getIntOr("sparring", -1);
		return data;
	}

	/** A deep copy (used by copy-on-death safety and tests). */
	public CultivationData copy() {
		CultivationData copy = loadFromServer(saveForClient());
		return copy;
	}
}
