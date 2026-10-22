package dev.kultivator.cultivation;

import dev.kultivator.Kultivator;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.XpSource;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * XP from killing monsters and mining ores (DESIGN section 1), plus a short throttled "+N Cultivation" line in the
 * action bar for those gains. Only real players count (never Fabric fake players), mining never in creative.
 */
public final class XpSources {

	/** Entity tag of the monsters drawn to a tribulation: they give half XP. */
	public static final String DAO_PROTECTOR_TAG = "kultivator.dao_protector";
	/** The heart demon gives its own reward (phase 3 module), never combat XP. */
	private static final Identifier HEART_DEMON = Kultivator.id("heart_demon");

	private static final TagKey<Block> COAL_ORES = BlockItemTags.COAL_ORES.block();
	private static final TagKey<Block> COPPER_ORES = BlockItemTags.COPPER_ORES.block();
	private static final TagKey<Block> IRON_ORES = BlockItemTags.IRON_ORES.block();
	private static final TagKey<Block> GOLD_ORES = BlockItemTags.GOLD_ORES.block();
	private static final TagKey<Block> REDSTONE_ORES = BlockItemTags.REDSTONE_ORES.block();
	private static final TagKey<Block> LAPIS_ORES = BlockItemTags.LAPIS_ORES.block();
	private static final TagKey<Block> DIAMOND_ORES = BlockItemTags.DIAMOND_ORES.block();
	private static final TagKey<Block> EMERALD_ORES = BlockItemTags.EMERALD_ORES.block();

	/** At most one gain line every 2 s per player. */
	private static final int DISPLAY_INTERVAL = 40;
	/** Gains not shown within this many ticks are dropped from the next line. */
	private static final int PENDING_TIMEOUT = 100;
	private static final Map<UUID, GainDisplay> DISPLAY = new HashMap<>();

	public static void init() {
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(XpSources::onKill);
		PlayerBlockBreakEvents.AFTER.register(XpSources::onBlockBroken);
		ServerPlayerEvents.LEAVE.register(player -> DISPLAY.remove(player.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(XpSources::flushPending);
	}

	// ================================================================== combat

	private static void onKill(ServerLevel level, Entity killer, LivingEntity killed, DamageSource source) {
		if (!(killer instanceof ServerPlayer player) || player instanceof FakePlayer || player.isSpectator()
				|| dev.kultivator.util.FormationDamage.is(source)) {
			// Formation kills (phase 7c, DESIGN 24.1 rule 3) never pay cultivation XP.
			return;
		}
		if (dev.kultivator.elite.EliteMonsters.isElite(killed)) {
			// Binatang Iblis: EliteRewards pays its own XP (DESIGN 19.3.1), for player and spirit-beast kills alike.
			return;
		}
		// Phase 7 (DESIGN 19.3.14): spawner mobs ×0.25, a boss type slain again today ×0.25, Kejenuhan Bertarung; the
		// call also keeps the kill counters and may drop a beast core.
		double factor = dev.kultivator.elite.EliteRewards.playerKill(player, killed);
		double xp = combatXp(killed) * factor * combatScale(Cultivators.get(player).rank());
		if (xp <= 0.0D) {
			return;
		}
		showGain(player, Progression.grantXp(player, xp, XpSource.COMBAT));
	}

	/** Shows a kill's gain in the throttled "+N Cultivation" line (the elite rewards of DESIGN 19.3.1). */
	public static void showKillGain(ServerPlayer player, double gained) {
		showGain(player, gained);
	}

	/**
	 * Base combat XP for killing {@code killed}: bosses (Wither, Ender Dragon, Warden, Elder Guardian) max health x 2,
	 * other monsters max health x 0.5, everything else 0; Dao Protector monsters give half.
	 */
	public static double combatXp(LivingEntity killed) {
		EntityType<?> type = killed.getType();
		if (BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(HEART_DEMON)) {
			return 0.0D;
		}
		double xp;
		if (type == EntityTypes.WITHER || type == EntityTypes.ENDER_DRAGON || type == EntityTypes.WARDEN
				|| type == EntityTypes.ELDER_GUARDIAN) {
			xp = killed.getMaxHealth() * 2.0D;
		} else if (killed instanceof Enemy) {
			xp = killed.getMaxHealth() * 0.5D;
		} else {
			return 0.0D;
		}
		if (killed.entityTags().contains(DAO_PROTECTOR_TAG)) {
			xp *= 0.5D;
		}
		return xp;
	}

	/** Balance change (audit M4 S4, approved): ordinary kill XP grows with the bar, {@code clamp(xpRequired / 4500, 1, 8)}. */
	public static final double COMBAT_SCALE_BAR = 4500.0D;
	public static final double COMBAT_SCALE_MAX = 8.0D;

	/**
	 * The kill XP scale of a rank (audit M4 S4): 1 up to Golden Core, then {@code xpRequired / 4500}, at most 8, so a monster
	 * still gives a sliver of a high realm's bar instead of nothing. Elite, rogue and envoy rewards keep their own tables.
	 */
	public static double combatScale(Rank rank) {
		return Math.max(1.0D, Math.min(COMBAT_SCALE_MAX, rank.xpRequired() / COMBAT_SCALE_BAR));
	}

	// ================================================================== Dunia milestones (audit M4 S4)

	/** Shares of the bar ({@code Stars.barSize}) for the Dunia milestones (approved balance change). */
	public static final double MILESTONE_TOWER_TEN = 0.03D;
	public static final double MILESTONE_RUINS = 0.05D;
	public static final double MILESTONE_COURT = 0.04D;
	public static final double MILESTONE_PRISON = 0.10D;
	public static final double MILESTONE_JIAO_BLOOD = 0.05D;

	/**
	 * Pays a Dunia milestone (every 10 tower floors, a cleared ruin, a won court, a sealed prison, Darah Jiao) as
	 * {@code share} of the player's bar through {@code Stars.grantShare} (XpSource.TRIAL, Stored Qi at the peak), with one
	 * chat line naming {@code what}. The caller pays each milestone once (its own first-clear flag).
	 */
	public static void milestone(ServerPlayer player, double share, Component what) {
		if (player == null || !(share > 0.0D) || !Double.isFinite(share)) {
			return;
		}
		dev.kultivator.ascension.Stars.grantShare(player, share);
		Msg.chat(player, Component.translatable("kultivator.kekuatan.milestone", what, Msg.number(share * 100.0D)).withStyle(ChatFormatting.AQUA));
	}

	// ================================================================== mining

	private static void onBlockBroken(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer
				|| serverPlayer.isCreative() || serverPlayer.isSpectator()) {
			return;
		}
		double xp = miningXp(state);
		if (xp <= 0.0D || hasSilkTouch(level, serverPlayer.getMainHandItem())) {
			return;
		}
		showGain(serverPlayer, Progression.grantXp(serverPlayer, xp, XpSource.MINING));
	}

	/**
	 * Base mining XP of a block: coal / copper / nether quartz / nether gold 1, iron / redstone / amethyst cluster 2,
	 * lapis / gold 3, diamond 10, emerald 12; deepslate variants +50%. 0 for anything else. Ancient debris gives nothing:
	 * it drops itself, so it could be placed and mined again forever.
	 */
	public static double miningXp(BlockState state) {
		double xp;
		if (state.is(Blocks.NETHER_GOLD_ORE) || state.is(Blocks.NETHER_QUARTZ_ORE)) {
			xp = 1.0D;
		} else if (state.is(Blocks.AMETHYST_CLUSTER)) {
			xp = 2.0D;
		} else if (state.is(COAL_ORES) || state.is(COPPER_ORES)) {
			xp = 1.0D;
		} else if (state.is(IRON_ORES) || state.is(REDSTONE_ORES)) {
			xp = 2.0D;
		} else if (state.is(LAPIS_ORES) || state.is(GOLD_ORES)) {
			xp = 3.0D;
		} else if (state.is(DIAMOND_ORES)) {
			xp = 10.0D;
		} else if (state.is(EMERALD_ORES)) {
			xp = 12.0D;
		} else {
			return 0.0D;
		}
		if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("deepslate_")) {
			xp *= 1.5D;
		}
		return xp;
	}

	private static boolean hasSilkTouch(Level level, ItemStack tool) {
		if (tool.isEmpty()) {
			return false;
		}
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.SILK_TOUCH)
				.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, tool) > 0)
				.orElse(false);
	}

	// ================================================================== action bar

	/** Shows "+N Cultivation" (or Stored / Immortal Qi when bottlenecked), summing gains between lines. */
	private static void showGain(ServerPlayer player, double gained) {
		if (!(gained > 0.0D)) {
			return;
		}
		long now = player.level().getServer().getTickCount();
		GainDisplay display = DISPLAY.computeIfAbsent(player.getUUID(), id -> new GainDisplay());
		if (now - display.lastGainAt > PENDING_TIMEOUT) {
			display.pending = 0.0D;
		}
		display.pending += gained;
		display.lastGainAt = now;
		flush(player, display, now);
	}

	/** Every 5 ticks: shows the gains that arrived too soon after the last line, once the line may be shown again. */
	private static void flushPending(MinecraftServer server) {
		long now = server.getTickCount();
		if (DISPLAY.isEmpty() || now % 5L != 0L) {
			return;
		}
		for (Map.Entry<UUID, GainDisplay> entry : DISPLAY.entrySet()) {
			GainDisplay display = entry.getValue();
			if (display.pending <= 0.0D) {
				continue;
			}
			if (now - display.lastGainAt > PENDING_TIMEOUT) {
				display.pending = 0.0D;
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null) {
				flush(player, display, now);
			}
		}
	}

	/** Sends the summed line when something is pending, no message is being held and the last line is 2 s old. */
	private static void flush(ServerPlayer player, GainDisplay display, long now) {
		if (!(display.pending > 0.0D) || now < display.holdUntil || now - display.shownAt < DISPLAY_INTERVAL) {
			return;
		}
		CultivationData data = Cultivators.get(player);
		Rank rank = data.rank();
		String key;
		ChatFormatting color;
		if (rank.isMax()) {
			key = "kultivator.progression.gain_immortal";
			color = ChatFormatting.GOLD;
		} else if (rank.isBottleneck() && data.barFull()) {
			key = "kultivator.progression.gain_stored";
			color = ChatFormatting.LIGHT_PURPLE;
		} else {
			key = "kultivator.progression.gain";
			color = ChatFormatting.AQUA;
		}
		Msg.actionBar(player, Component.translatable(key, format(display.pending)).withStyle(color));
		display.shownAt = now;
		display.pending = 0.0D;
	}

	/** Keeps the gain line out of the action bar for {@code ticks} (so a more important message stays visible). */
	public static void holdDisplay(ServerPlayer player, int ticks) {
		long now = player.level().getServer().getTickCount();
		GainDisplay display = DISPLAY.computeIfAbsent(player.getUUID(), id -> new GainDisplay());
		display.holdUntil = Math.max(display.holdUntil, now + Math.max(0, ticks));
	}

	/** Whole numbers from 10 up, one decimal below ("0.6", "4.5", "12"). */
	private static String format(double value) {
		if (value >= 10.0D) {
			return String.valueOf(Math.round(value));
		}
		String text = String.format(Locale.ROOT, "%.1f", value);
		return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
	}

	/** Per-player throttle state of the gain line. */
	private static final class GainDisplay {
		long shownAt = -1000L;
		long lastGainAt = -1000L;
		long holdUntil = -1000L;
		double pending;
	}

	private XpSources() {
	}
}
