package dev.kultivator.abadi;

import dev.kultivator.ascension.Stars;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.Cultivators;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Batu Asal Abadi and Esensi Abadi (DESIGN 26.3.5). Mining: each {@code #minecraft:base_stone_overworld} block a player breaks
 * in Alam Abadi (no Silk Touch) rolls 0.6% × (1 + 0.5 × Fortune) for one stone, at most 16 per in-game day, dropped as an
 * item (never a block; Mode Kehancuran never rolls). Conversion ({@code conv} 0-100%): +1% per 30 s of meditation inside Alam
 * Abadi, +2% per Batu used (at most 10 per day). Effects: technique damage against {@code abadi_foe} ×(0.6 + 0.4 × conv)
 * (a {@code TargetFactor}), qi regeneration in Alam Abadi ×(0.5 + 0.5 × conv), meditation XP in Alam Abadi ×3, and the star
 * condensing discount: −1% per Batu carried (at most 10), the stones used consumed after each star. Inspired by RMJIR (origin
 * stones as their own grade of power); every number is game design.
 */
final class OriginStones {

	public static final double MINE_CHANCE = 0.006D;
	public static final double FORTUNE_STEP = 0.5D;
	public static final int MINE_DAY_CAP = 16;
	public static final int CONVERT_STEP = 2;
	public static final int CONVERT_PER_DAY = 10;
	public static final int MEDITATE_SECONDS = 30;
	public static final int STAR_STONES_MAX = 10;
	public static final double STAR_DISCOUNT = 0.01D;
	public static final double MEDITATION_XP = 3.0D;
	public static final double FOE_BASE = 0.6D;
	public static final double FOE_PER_CONV = 0.4D;

	private static final Map<UUID, Integer> MEDITATED = new HashMap<>();

	static void init() {
		PlayerBlockBreakEvents.AFTER.register(OriginStones::onBlockBroken);
		Stars.addCostModifier(player -> 1.0D - STAR_DISCOUNT * Math.min(STAR_STONES_MAX, Abadi.stonesIn(player)));
		Stars.addCondenseListener((player, stars) -> Abadi.takeStones(player, Math.min(STAR_STONES_MAX, Abadi.stonesIn(player))));
		Progression.addMultiplierProvider(player -> Abadi.isImmortalLevel(player.level()) && Cultivators.get(player).meditating() ? MEDITATION_XP : 1.0D);
		Qi.addRegenFactor(player -> Abadi.isImmortalLevel(player.level()) ? 0.5D + 0.5D * conv(player) / 100.0D : 1.0D);
		TechniquePower.addTargetFactor((attacker, target, spec) -> attacker instanceof ServerPlayer player
				&& target.entityTags().contains(TechniquePower.TAG_ABADI_FOE) ? FOE_BASE + FOE_PER_CONV * conv(player) / 100.0D : 1.0D);
		ServerPlayerEvents.LEAVE.register(player -> MEDITATED.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MEDITATED.clear());
	}

	/** Every second: meditation inside Alam Abadi fills the conversion (+1% per 30 s). */
	static void tick(MinecraftServer server, long ticks) {
		if (ticks % 20L != 0L) {
			return;
		}
		ServerLevel level = Abadi.immortalLevel(server);
		if (level == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (!Cultivators.get(player).meditating() || conv(player) >= 100) {
				MEDITATED.remove(player.getUUID());
				continue;
			}
			int seconds = MEDITATED.merge(player.getUUID(), 1, Integer::sum);
			if (seconds >= MEDITATE_SECONDS) {
				MEDITATED.remove(player.getUUID());
				setConv(player, conv(player) + 1);
				Msg.actionBar(player, Component.translatable("kultivator.abadi.conv.meditate", conv(player)).withStyle(ChatFormatting.AQUA));
			}
		}
	}

	// ================================================================== conversion

	/** The player's conversion 0-100. */
	static int conv(ServerPlayer player) {
		return Math.max(0, Math.min(100, Cultivators.get(player).extra().getCompoundOrEmpty(Abadi.EXTRA_KEY).getIntOr("conv", 0)));
	}

	static void setConv(ServerPlayer player, int value) {
		int clamped = Math.max(0, Math.min(100, value));
		Abadi.extra(player).putInt("conv", clamped);
		Cultivators.dirty(player);
		if (clamped >= 50) {
			Advancements.grant(player, "essence_half");
		}
		if (clamped >= 100) {
			Advancements.grant(player, "essence_converted");
		}
	}

	/** Conversions with Batu still allowed today. */
	static int convertsLeft(ServerPlayer player) {
		CompoundTag extra = Cultivators.get(player).extra().getCompoundOrEmpty(Abadi.EXTRA_KEY);
		long today = Abadi.day(player.level().getServer());
		int used = extra.getLongOr("conv_day", -1L) == today ? extra.getIntOr("conv_n", 0) : 0;
		return Math.max(0, CONVERT_PER_DAY - used);
	}

	/** Uses up to {@code count} Batu for +2% each (at most 10 per day); returns how many were used. */
	static int convert(ServerPlayer player, int count) {
		if (!Abadi.ascended(player)) {
			Msg.chat(player, Component.translatable("kultivator.abadi.cross.not_ascended").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		int room = (100 - conv(player) + CONVERT_STEP - 1) / CONVERT_STEP;
		int n = Math.min(Math.max(0, count), Math.min(convertsLeft(player), room));
		if (n <= 0) {
			Msg.chat(player, Component.translatable(conv(player) >= 100 ? "kultivator.abadi.conv.full" : "kultivator.abadi.conv.day_cap", CONVERT_PER_DAY)
					.withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (!player.isCreative() && Abadi.stonesIn(player) < n) {
			n = Abadi.stonesIn(player);
		}
		if (n <= 0 || !Abadi.takeStones(player, n)) {
			Msg.chat(player, Component.translatable("kultivator.abadi.conv.no_stones").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		CompoundTag extra = Abadi.extra(player);
		long today = Abadi.day(player.level().getServer());
		int used = extra.getLongOr("conv_day", -1L) == today ? extra.getIntOr("conv_n", 0) : 0;
		extra.putLong("conv_day", today);
		extra.putInt("conv_n", used + n);
		setConv(player, conv(player) + CONVERT_STEP * n);
		Msg.chat(player, Component.translatable("kultivator.abadi.conv.done", n, conv(player)).withStyle(ChatFormatting.AQUA));
		return n;
	}

	// ================================================================== mining

	/** Stones mined today. */
	static int minedToday(ServerPlayer player) {
		CompoundTag extra = Cultivators.get(player).extra().getCompoundOrEmpty(Abadi.EXTRA_KEY);
		return extra.getLongOr("mined_day", -1L) == Abadi.day(player.level().getServer()) ? extra.getIntOr("mined", 0) : 0;
	}

	private static void onBlockBroken(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer || serverPlayer.isCreative()
				|| serverPlayer.isSpectator() || !Abadi.isImmortalLevel(level) || !state.is(BlockTags.BASE_STONE_OVERWORLD)) {
			return;
		}
		ItemStack tool = serverPlayer.getMainHandItem();
		if (enchantment(level, tool, Enchantments.SILK_TOUCH) > 0) {
			return;
		}
		if (minedToday(serverPlayer) >= MINE_DAY_CAP) {
			return;
		}
		double chance = MINE_CHANCE * (1.0D + FORTUNE_STEP * enchantment(level, tool, Enchantments.FORTUNE));
		if (serverPlayer.getRandom().nextDouble() >= chance) {
			return;
		}
		CompoundTag extra = Abadi.extra(serverPlayer);
		long today = Abadi.day(serverPlayer.level().getServer());
		extra.putLong("mined_day", today);
		extra.putInt("mined", minedToday(serverPlayer) + 1);
		// Lifetime counter read by the ORIGIN daily chore (goals/DailyChores).
		extra.putInt("mined_total", extra.getIntOr("mined_total", 0) + 1);
		Cultivators.dirty(serverPlayer);
		ItemEntity item = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, new ItemStack(AbadiItems.IMMORTAL_ORIGIN_STONE));
		item.setDefaultPickUpDelay();
		item.addTag(dev.kultivator.util.NoGrief.TAG);
		level.addFreshEntity(item);
		Advancements.grant(serverPlayer, "origin_stone");
		Abadi.activity(serverPlayer, Abadi.ACT_STONE_MINED);
		Msg.actionBar(serverPlayer, Component.translatable("kultivator.abadi.mined", minedToday(serverPlayer), MINE_DAY_CAP).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	private static int enchantment(Level level, ItemStack tool, ResourceKey<Enchantment> key) {
		if (tool.isEmpty()) {
			return 0;
		}
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key)
				.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, tool))
				.orElse(0);
	}

	private OriginStones() {
	}
}
