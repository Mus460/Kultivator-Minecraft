package dev.kultivator.elite;

import dev.kultivator.cultivation.DaoProtectors;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.cultivation.XpSources;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.Element;
import dev.kultivator.data.Realm;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.run.Dummies;
import dev.kultivator.technique.spec.run.Mastery;
import dev.kultivator.trial.HeartDemonEntity;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.KillCredit;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * What a kill gives (DESIGN 19.3.1 rewards, 19.3.3 drops, 19.3.14 Battle Fatigue): elite XP, comprehension, counters,
 * advancements and drops; the daily boss rule with its Raja core; the 1.5% Low core of an ordinary monster. Credit comes
 * from the spine's {@link KillCredit}: player and spirit-beast kills reward the owner, disciple kills reward the
 * disciple (through {@code DiscipleHooks.addOnKill}) and give the master the counters; deaths without credit give nothing
 * extra. Drops are item entities at the dead mob (block-safe). {@code XpSources} reports the player kills it already
 * paid, so a kill is never counted twice.
 */
public final class EliteRewards {

	/** XP: max(combatXp × (3 / 5 / 8), bar × (1% / 2.5% / 5%) × clamp(1 + 0.5 × (R − killer realm), 0.25, 2)). */
	public static final double[] XP_COMBAT = {1.0D, 3.0D, 5.0D, 8.0D};
	public static final double[] XP_BAR = {0.0D, 0.01D, 0.025D, 0.05D};
	public static final double REALM_STEP = 0.5D;
	public static final double REALM_MIN = 0.25D;
	public static final double REALM_MAX = 2.0D;
	/** Comprehension to the chosen Dao by tier, at most {@link #DAILY_COMPREHENSION} per in-game day. */
	public static final double[] COMPREHENSION = {0.0D, 0.3D, 0.6D, 1.5D};
	public static final double DAILY_COMPREHENSION = 3.0D;
	/** A disciple's extra XP: combatXp × KILL_XP × (2 / 4 / 7). */
	public static final double[] DISCIPLE_XP = {0.0D, 2.0D, 4.0D, 7.0D};
	public static final double HERB_CHANCE = 0.25D;
	public static final int HERB_MIN_AGE = 50;
	public static final int HERB_MAX_AGE = 400;
	public static final int KING_STONES_MIN = 1;
	public static final int KING_STONES_MAX = 3;
	/** Balance change (flow audit): a Raja Siluman (tier 3) drops a King core instead of a High one at this chance. */
	public static final double KING_CORE_CHANCE = 0.10D;
	/** Ordinary monsters: a Low core of their element, × Battle Fatigue. */
	public static final double NORMAL_CORE_CHANCE = 0.015D;
	/** A boss type slain again on the same in-game day gives this share of its XP. */
	public static final double BOSS_REPEAT = 0.25D;
	public static final double SPAWNER_FACTOR = 0.25D;
	public static final int HUNTER_KILLS = 50;
	/** {@code boss_ever} bits. */
	public static final int BOSS_WITHER = 1;
	public static final int BOSS_WARDEN = 2;
	public static final int BOSS_DRAGON = 4;
	public static final int BOSS_ELDER = 8;

	/** Victims {@code XpSources} already paid this tick. */
	private static final Set<UUID> COUNTED = new HashSet<>();

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register(EliteRewards::afterDeath);
		DiscipleHooks.addOnKill(EliteRewards::discipleKill);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!COUNTED.isEmpty()) {
				COUNTED.clear();
			}
		});
	}

	// ================================================================== entry points

	/**
	 * A kill {@code XpSources} pays (the player's own blow, never an elite): counters, Battle Fatigue, the boss day, the
	 * normal core. Returns the factor on the kill's combat XP (spawner ×0.25, repeat boss ×0.25, Battle Fatigue).
	 */
	public static double playerKill(ServerPlayer player, LivingEntity killed) {
		COUNTED.add(killed.getUUID());
		return afterKill(player, killed);
	}

	private static void afterDeath(LivingEntity victim, DamageSource source) {
		if (victim instanceof ServerPlayer player) {
			HuFaTribulation.onPlayerDeath(player);
			return;
		}
		if (!(victim.level() instanceof ServerLevel level)) {
			return;
		}
		boolean elite = EliteMonsters.isElite(victim);
		if (elite) {
			EliteMonsters.untrack(victim);
		}
		if (COUNTED.remove(victim.getUUID())) {
			return;
		}
		KillCredit.Credit credit = KillCredit.resolve(victim, source);
		if (credit == null || credit.owner() instanceof FakePlayer) {
			return;
		}
		if (elite && KultivatorConfig.get().eliteMonsters) {
			eliteDeath(level, victim, credit);
			return;
		}
		if (credit.kind() != KillCredit.Kind.DISCIPLE) {
			// A kill XpSources did not pay (a spirit beast's blow, or credit without a direct attacker): no XP, the rest applies.
			afterKill(credit.owner(), victim);
		}
	}

	private static void discipleKill(DiscipleEntity disciple, LivingEntity victim) {
		EliteData data = EliteMonsters.isElite(victim) && KultivatorConfig.get().eliteMonsters ? EliteData.of(victim) : null;
		if (data == null) {
			return;
		}
		double extra = XpSources.combatXp(victim) * DiscipleCultivation.KILL_XP * DISCIPLE_XP[data.tier()];
		DiscipleCultivation.addXp(disciple, extra, XpSource.COMBAT);
		ServerPlayer master = disciple.owner();
		if (master != null && !(master instanceof FakePlayer)) {
			count(master, victim, data);
		}
	}

	// ================================================================== elites

	private static void eliteDeath(ServerLevel level, LivingEntity victim, KillCredit.Credit credit) {
		EliteData data = EliteData.of(victim);
		if (data == null) {
			return;
		}
		Vec3 pos = victim.position();
		Element element = data.elementValue();
		boolean protector = DaoProtectors.isProtector(victim);
		int coreGrade = Math.max(BeastCores.LOW, Math.min(BeastCores.HIGH, data.tier()));
		if (data.tier() >= 3 && level.getRandom().nextDouble() < KING_CORE_CHANCE) {
			coreGrade = BeastCores.KING;
		}
		BeastCores.drop(level, pos, element, coreGrade);
		if (!protector) {
			RandomSource random = level.getRandom();
			if (data.tier() >= 2 && random.nextDouble() < HERB_CHANCE) {
				drop(level, pos, SpiritHerbs.randomFor(element, random, HERB_MIN_AGE, HERB_MAX_AGE));
			}
			if (data.tier() >= 3) {
				int stones = KING_STONES_MIN + random.nextInt(KING_STONES_MAX - KING_STONES_MIN + 1);
				drop(level, pos, SpiritStones.stack(SpiritStones.LOW, stones));
			}
			// Phase 8 providers (DESIGN 25.2 item 13): realm keys and fragments.
			EliteMonsters.extraDrops(level, victim, data.tier(), element, credit.owner(), stack -> drop(level, pos, stack));
		}
		if (credit.kind() == KillCredit.Kind.DISCIPLE) {
			// The disciple's XP and the master's counters come through DiscipleHooks.addOnKill.
			return;
		}
		ServerPlayer owner = credit.owner();
		double fatigue = BattleFatigue.factor(owner);
		BattleFatigue.addKill(owner, victim);
		double xp = eliteXp(owner, victim, data, protector) * fatigue;
		XpSources.showKillGain(owner, Progression.grantXp(owner, xp, XpSource.COMBAT));
		grantComprehension(owner, level, data.tier());
		count(owner, victim, data);
		if (data.tier() >= 3) {
			Msg.chat(owner, Component.translatable("kultivator.combat.king_slain", data.title()).withStyle(ChatFormatting.GOLD));
		}
	}

	/** The elite XP before multipliers and Battle Fatigue (protectors give half). */
	public static double eliteXp(ServerPlayer killer, LivingEntity victim, EliteData data, boolean protector) {
		CultivationData killerData = Cultivators.get(killer);
		int tier = data.tier();
		double combat = XpSources.combatXp(victim) * XP_COMBAT[tier];
		double gap = Math.max(REALM_MIN, Math.min(REALM_MAX, 1.0D + REALM_STEP * (data.realm() - killerData.realm().ordinal())));
		double bar = killerData.rank().xpRequired() * XP_BAR[tier] * gap;
		if (protector) {
			bar *= 0.5D;
		}
		return Math.max(combat, bar);
	}

	/** +0.3 / +0.6 / +1.5 to the chosen Dao, at most +3.0 per in-game day from elites ({@code extra.combat.cday}). */
	private static void grantComprehension(ServerPlayer player, ServerLevel level, int tier) {
		CultivationData data = Cultivators.get(player);
		Dao dao = data.dao();
		if (dao == null) {
			return;
		}
		CompoundTag combat = BattleFatigue.section(data);
		CompoundTag daily = combat.getCompoundOrEmpty("cday");
		long day = Mastery.day(level);
		if (daily.getLongOr("day", -1L) != day) {
			daily = new CompoundTag();
			daily.putLong("day", day);
		}
		double gained = daily.getDoubleOr("gained", 0.0D);
		double allowed = Math.min(COMPREHENSION[tier], DAILY_COMPREHENSION - gained);
		if (!(allowed > 0.0D)) {
			return;
		}
		daily.putDouble("gained", gained + allowed);
		combat.put("cday", daily);
		Comprehension.grant(player, dao, allowed);
		Cultivators.dirty(player);
	}

	/** {@code extra.combat.elite1..3} and the elite advancements (also for the master of a disciple's kill). */
	private static void count(ServerPlayer player, LivingEntity victim, EliteData data) {
		CultivationData cultivation = Cultivators.get(player);
		CompoundTag combat = BattleFatigue.section(cultivation);
		int all = combat.getIntOr("elite1", 0) + 1;
		combat.putInt("elite1", all);
		if (data.tier() >= 2) {
			combat.putInt("elite2", combat.getIntOr("elite2", 0) + 1);
		}
		if (data.tier() >= 3) {
			combat.putInt("elite3", combat.getIntOr("elite3", 0) + 1);
		}
		combat.putInt("kills", combat.getIntOr("kills", 0) + 1);
		if (victim.level().isThundering()) {
			combat.putInt("thunder_kills", combat.getIntOr("thunder_kills", 0) + 1);
		}
		Cultivators.dirty(player);
		Advancements.grant(player, "first_elite");
		if (data.tier() >= 3) {
			Advancements.grant(player, "beast_king_slain");
		}
		if (all >= HUNTER_KILLS) {
			Advancements.grant(player, "elite_hunter");
		}
	}

	// ================================================================== ordinary kills and bosses

	/** Counters, Battle Fatigue, the boss day and the normal core of one kill; returns the XP factor. */
	private static double afterKill(ServerPlayer player, LivingEntity killed) {
		if (!(killed instanceof Enemy) || !(killed.level() instanceof ServerLevel level) || killed instanceof HeartDemonEntity
				|| killed.entityTags().contains(Dummies.TAG)) {
			return 1.0D;
		}
		double factor = 1.0D;
		boolean spawner = killed.entityTags().contains(EliteMonsters.TAG_SPAWNER);
		if (spawner) {
			factor *= SPAWNER_FACTOR;
		}
		int boss = bossBit(killed.getType());
		if (boss != 0) {
			factor *= bossKill(player, killed, level, boss) ? 1.0D : BOSS_REPEAT;
		}
		double fatigue = BattleFatigue.factor(player);
		factor *= fatigue;
		BattleFatigue.addKill(player, killed);
		CompoundTag combat = BattleFatigue.section(Cultivators.get(player));
		combat.putInt("kills", combat.getIntOr("kills", 0) + 1);
		if (level.isThundering()) {
			combat.putInt("thunder_kills", combat.getIntOr("thunder_kills", 0) + 1);
		}
		Cultivators.dirty(player);
		if (boss == 0 && !spawner) {
			normalCore(player, killed, level, fatigue);
		}
		return factor;
	}

	/** Sets the {@code boss_ever} bit; the first kill of that boss type today drops a Raja core. True when it was the first. */
	private static boolean bossKill(ServerPlayer player, LivingEntity boss, ServerLevel level, int bit) {
		CultivationData data = Cultivators.get(player);
		CompoundTag combat = BattleFatigue.section(data);
		combat.putInt("boss_ever", combat.getIntOr("boss_ever", 0) | bit);
		CompoundTag days = combat.getCompoundOrEmpty("boss_day");
		long day = Mastery.day(level);
		String key = bossKey(bit);
		boolean first = days.getLongOr(key, -1L) != day;
		if (first) {
			days.putLong(key, day);
			combat.put("boss_day", days);
			Element[] all = Element.values();
			Element element = all[level.getRandom().nextInt(all.length)];
			// The dragon dies high above the island: its core lands at the slayer's feet instead.
			Vec3 at = bit == BOSS_DRAGON && player.level() == level ? player.position() : boss.position();
			BeastCores.drop(level, at, element, BeastCores.KING);
		}
		Cultivators.dirty(player);
		return first;
	}

	/** 1.5% × Battle Fatigue for a Low core of the mob's element (Qi Refining+; never from protectors, guards, dummies, demons). */
	private static void normalCore(ServerPlayer player, LivingEntity killed, ServerLevel level, double fatigue) {
		if (!Cultivators.get(player).realm().atLeast(Realm.QI_REFINING) || DaoProtectors.isProtector(killed) || killed instanceof HeartDemonEntity) {
			return;
		}
		Set<String> tags = killed.entityTags();
		if (tags.contains(TechniquePower.TAG_TRIAL_GUARD) || tags.contains(Dummies.TAG) || tags.contains(TechniquePower.TAG_DUMMY_BOSS)
				|| tags.contains(TechniquePower.TAG_BOSS)) {
			return;
		}
		Element element = MobElements.of(killed);
		if (element != null && level.getRandom().nextDouble() < NORMAL_CORE_CHANCE * fatigue) {
			BeastCores.drop(level, killed.position(), element, BeastCores.LOW);
		}
	}

	/** The {@code boss_ever} bit of a boss type, 0 for anything else. */
	public static int bossBit(EntityType<?> type) {
		if (type == EntityTypes.WITHER) {
			return BOSS_WITHER;
		}
		if (type == EntityTypes.WARDEN) {
			return BOSS_WARDEN;
		}
		if (type == EntityTypes.ENDER_DRAGON) {
			return BOSS_DRAGON;
		}
		return type == EntityTypes.ELDER_GUARDIAN ? BOSS_ELDER : 0;
	}

	private static String bossKey(int bit) {
		return switch (bit) {
			case BOSS_WITHER -> "wither";
			case BOSS_WARDEN -> "warden";
			case BOSS_DRAGON -> "ender_dragon";
			default -> "elder_guardian";
		};
	}

	/** A block-safe item entity at {@code pos} (nothing for an empty stack). */
	private static void drop(ServerLevel level, Vec3 pos, @Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemEntity item = new ItemEntity(level, pos.x, pos.y + 0.3D, pos.z, stack);
		item.setDefaultPickUpDelay();
		item.addTag(NoGrief.TAG);
		item.setPortalCooldown();
		level.addFreshEntity(item);
	}

	private EliteRewards() {
	}
}
