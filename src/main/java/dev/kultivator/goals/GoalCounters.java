package dev.kultivator.goals;

import dev.kultivator.cultivation.SpiritSites;
import dev.kultivator.cultivation.SpiritVeins;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.util.KillCredit;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The lifetime counters of the Jalan tablet (DESIGN 19.3.9, 19.9 {@code extra.goals.c}): meditation seconds by site,
 * formation tier, dimension and vein grade (read every 20 ticks while meditating), spiritual-site types seen, dimensions
 * visited, ores mined, monster, thunderstorm, elite and boss kills. The trials and chores snapshot them. Counters are
 * written silently (the attachment is saved anyway); only visible goal changes mark the player dirty. The readers that
 * combine them with the counters of other modules ({@code extra.combat}, {@code extra.harta}, {@code extra.talisman})
 * are pure.
 */
public final class GoalCounters {

	/** {@code extra.goals}: the module's whole sub-compound. */
	public static final String ROOT = "goals";
	public static final String KEY = "c";

	public static final String KILLS = "kills";
	public static final String THUNDER = "thunder";
	public static final String ELITE1 = "e1";
	public static final String ELITE2 = "e2";
	public static final String ELITE3 = "e3";
	public static final String BOSS = "boss";
	public static final String DIMS = "dims";
	public static final String SITES = "sites";
	public static final String ORES = "ores";
	public static final String MED_SITE15 = "ms15";
	public static final String MED_SITE20 = "ms20";
	public static final String MED_GREAT = "mf2";
	public static final String MED_HEAVENLY = "mf3";
	public static final String MED_END = "mend";
	public static final String MED_VEIN = "mv2";
	public static final String PLAYED = "played";

	/** {@code boss} bits, the same as {@code extra.combat.boss_ever} (DESIGN 19.9). */
	public static final int BOSS_WITHER = 1;
	public static final int BOSS_WARDEN = 2;
	public static final int BOSS_DRAGON = 4;
	public static final int BOSS_ELDER = 8;
	/** {@code dims} bits. */
	public static final int DIM_NETHER = 1;
	public static final int DIM_END = 2;

	/** Site multipliers the meditation counters need (×1.5 and ×2.0). */
	public static final double SITE_15 = 1.5D;
	public static final double SITE_20 = 2.0D;

	private static final TagKey<Block> GOLD_ORES = BlockItemTags.GOLD_ORES.block();
	private static final TagKey<Block> DIAMOND_ORES = BlockItemTags.DIAMOND_ORES.block();
	private static final TagKey<Block> EMERALD_ORES = BlockItemTags.EMERALD_ORES.block();
	private static final String SPAWNER_TAG = "kultivator.spawner";

	/** Seconds of the current unbroken meditation per player (session only; resets when meditation stops). */
	private static final Map<UUID, Integer> RUN = new HashMap<>();

	// ================================================================== sections

	/** {@code extra.goals}, created when missing (server writes). */
	static CompoundTag goals(CultivationData data) {
		return section(data.extra(), ROOT);
	}

	/** {@code extra.goals.c}, created when missing. */
	static CompoundTag counters(CultivationData data) {
		return section(goals(data), KEY);
	}

	/** A child compound of {@code parent}, created when missing (the live tag, not a copy). */
	static CompoundTag section(CompoundTag parent, String key) {
		CompoundTag tag = parent.getCompound(key).orElse(null);
		if (tag == null) {
			tag = new CompoundTag();
			parent.put(key, tag);
		}
		return tag;
	}

	/** {@code extra.goals} read-only (an empty tag when missing; client screens use this). */
	public static CompoundTag view(CultivationData data) {
		return data.extra().getCompoundOrEmpty(ROOT);
	}

	// ================================================================== pure readers

	public static int value(CultivationData data, String key) {
		return view(data).getCompoundOrEmpty(KEY).getIntOr(key, 0);
	}

	/** Elite kills of tier ≥ {@code tier} (1-3): the larger of this module's and P7-tempur's {@code extra.combat.eliteN}. */
	public static int elites(CultivationData data, int tier) {
		String own = tier >= 3 ? ELITE3 : tier == 2 ? ELITE2 : ELITE1;
		int combat = data.extra().getCompoundOrEmpty("combat").getIntOr("elite" + Math.max(1, Math.min(3, tier)), 0);
		return Math.max(value(data, own), combat);
	}

	/** Boss types ever slain (bits 1 Wither, 2 Warden, 4 Dragon, 8 Elder Guardian), with {@code extra.combat.boss_ever}. */
	public static int bossBits(CultivationData data) {
		return value(data, BOSS) | data.extra().getCompoundOrEmpty("combat").getIntOr("boss_ever", 0);
	}

	/** Monsters killed during a thunderstorm, with {@code extra.combat.thunder_kills}. */
	public static int thunderKills(CultivationData data) {
		return Math.max(value(data, THUNDER), data.extra().getCompoundOrEmpty("combat").getIntOr("thunder_kills", 0));
	}

	/** Pills refined: the larger of {@code extra.harta.refined} and the existing alchemy counter (both count every success). */
	public static int refined(CultivationData data) {
		return Math.max(data.pillsRefined(), data.extra().getCompoundOrEmpty("harta").getIntOr("refined", 0));
	}

	/** Spirit herbs found ({@code extra.harta.herbs_found}, P7-harta). */
	public static int herbsFound(CultivationData data) {
		return data.extra().getCompoundOrEmpty("harta").getIntOr("herbs_found", 0);
	}

	/** Talismans inscribed ({@code extra.talisman.made}, P7-kultivasi). */
	public static int talismansMade(CultivationData data) {
		return data.extra().getCompoundOrEmpty("talisman").getIntOr("made", 0);
	}

	/** The highest comprehension of any Dao (0-100). */
	public static double maxComprehension(CultivationData data) {
		double best = 0.0D;
		for (Dao dao : Dao.values()) {
			best = Math.max(best, data.comprehension(dao));
		}
		return best;
	}

	/** Seconds of the current unbroken meditation (server). */
	public static int run(ServerPlayer player) {
		return RUN.getOrDefault(player.getUUID(), 0);
	}

	// ================================================================== updates

	/** Every 20 ticks: the meditation counters, the unbroken run and the site types seen. */
	static void second(ServerPlayer player, CultivationData data) {
		if (!data.meditating()) {
			RUN.remove(player.getUUID());
			return;
		}
		RUN.merge(player.getUUID(), 1, Integer::sum);
		CompoundTag c = counters(data);
		double site = data.siteMultiplier();
		if (site >= SITE_15 - 1.0E-6D) {
			add(c, MED_SITE15, 1);
		}
		if (site >= SITE_20 - 1.0E-6D) {
			add(c, MED_SITE20, 1);
		}
		int formation = data.formationTier();
		if (formation >= 2) {
			add(c, MED_GREAT, 1);
		}
		if (formation >= 3) {
			add(c, MED_HEAVENLY, 1);
		}
		if (player.level().dimension().equals(Level.END)) {
			add(c, MED_END, 1);
		}
		if (SpiritVeins.grade(player.level(), player.blockPosition()) >= 2) {
			add(c, MED_VEIN, 1);
		}
		int bits = c.getIntOr(SITES, 0);
		for (SpiritSites.Site each : SpiritSites.current(player).sites()) {
			bits |= 1 << each.ordinal();
		}
		c.putInt(SITES, bits);
	}

	/** Every 100 ticks: time played and the dimensions visited. */
	static void every100(ServerPlayer player, CultivationData data) {
		CompoundTag c = counters(data);
		add(c, PLAYED, 100);
		int dims = c.getIntOr(DIMS, 0);
		if (player.level().dimension().equals(Level.NETHER)) {
			dims |= DIM_NETHER;
		} else if (player.level().dimension().equals(Level.END)) {
			dims |= DIM_END;
		}
		c.putInt(DIMS, dims);
	}

	/** A living entity died: monster, thunderstorm, elite and boss counters of the credited player. */
	static void onDeath(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof Enemy) || victim.level().isClientSide()) {
			return;
		}
		KillCredit.Credit credit = KillCredit.resolve(victim, source);
		if (credit == null || credit.owner() instanceof FakePlayer) {
			return;
		}
		ServerPlayer player = credit.owner();
		CultivationData data = Cultivators.get(player);
		CompoundTag c = counters(data);
		boolean own = credit.kind() != KillCredit.Kind.DISCIPLE;
		if (own && !victim.entityTags().contains(SPAWNER_TAG)) {
			add(c, KILLS, 1);
			if (victim.level().isThundering()) {
				add(c, THUNDER, 1);
			}
		}
		int tier = EliteMonsters.tier(victim);
		if (tier >= 1) {
			add(c, ELITE1, 1);
		}
		if (tier >= 2) {
			add(c, ELITE2, 1);
		}
		if (tier >= 3) {
			add(c, ELITE3, 1);
		}
		int boss = bossBit(victim.getType());
		if (boss != 0) {
			c.putInt(BOSS, c.getIntOr(BOSS, 0) | boss);
		}
	}

	private static int bossBit(EntityType<?> type) {
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

	/** A block the player broke: gold, diamond and emerald ores without Silk Touch (the daily chore). */
	static void onBlockBroken(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer
				|| serverPlayer.isCreative() || serverPlayer.isSpectator()) {
			return;
		}
		if (!state.is(GOLD_ORES) && !state.is(DIAMOND_ORES) && !state.is(EMERALD_ORES)) {
			return;
		}
		if (hasSilkTouch(level, serverPlayer.getMainHandItem())) {
			return;
		}
		add(counters(Cultivators.get(serverPlayer)), ORES, 1);
	}

	private static boolean hasSilkTouch(Level level, ItemStack tool) {
		if (tool.isEmpty()) {
			return false;
		}
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.SILK_TOUCH)
				.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, tool) > 0)
				.orElse(false);
	}

	static void forget(ServerPlayer player) {
		RUN.remove(player.getUUID());
	}

	static void clear() {
		RUN.clear();
	}

	private static void add(CompoundTag tag, String key, int amount) {
		tag.putInt(key, Math.max(0, tag.getIntOr(key, 0)) + amount);
	}

	private GoalCounters() {
	}
}
