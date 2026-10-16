package dev.kultivator.cultivation;

import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.XpSource;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enlightenment moments (Momen Pencerahan, DESIGN section 4): a sudden insight while meditating. For 10 s the player
 * glows golden (synced ENLIGHTENED flag), gains a burst of 25% of the bar and, with a Dao chosen, +3 comprehension.
 */
public final class Enlightenment {

	/** How long the golden state lasts. */
	public static final int DURATION_TICKS = 200;
	/** Colour of the effect. */
	public static final int GOLD = 0xFFD54A;
	/** Base chance per minute of meditation (times the site multiplier), and the bonus with a Dao chosen. */
	public static final double BASE_CHANCE = 0.04D;
	public static final double DAO_BONUS = 0.02D;
	public static final double COMPREHENSION_GAIN = 3.0D;

	/** Game time at which each enlightened player's golden state ends. */
	private static final Map<UUID, Long> ENDS = new HashMap<>();

	public static void init() {
	}

	/** Chance of an enlightenment for one full minute of meditation at a site with this multiplier. */
	public static double chance(CultivationData data, double siteMultiplier) {
		return BASE_CHANCE * siteMultiplier + (data.dao() != null ? DAO_BONUS : 0.0D);
	}

	/** Starts an enlightenment moment for the player now. */
	public static void trigger(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		ServerLevel level = player.level();
		ENDS.put(player.getUUID(), level.getGameTime() + DURATION_TICKS);
		data.setEnlightened(true);
		data.addEnlightenment();

		double burst = data.rank().xpRequired() * 0.25D;
		Progression.addXp(player, burst, XpSource.ENLIGHTENMENT);
		Dao dao = data.dao();
		if (dao != null) {
			Comprehension.add(player, dao, COMPREHENSION_GAIN);
		}
		Cultivators.syncNow(player);

		Fx.send(level, player.position(), FxType.ENLIGHTENMENT, GOLD, DURATION_TICKS, 0.0F);
		double x = player.getX();
		double y = player.getY() + 1.0D;
		double z = player.getZ();
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 1.4F);
		level.playSound(null, x, y, z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 1.6F);
		level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.0F);
		level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.5F);

		Msg.title(player, Component.translatable("kultivator.meditation.enlightenment.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("kultivator.meditation.enlightenment.subtitle").withStyle(ChatFormatting.YELLOW), 10, 60, 20);
		MutableComponent message = Component.translatable("kultivator.meditation.enlightenment.chat",
				Component.literal(Msg.number(Math.max(1.0D, burst))).withStyle(ChatFormatting.GOLD));
		if (dao != null) {
			message.append(Component.literal(" "))
					.append(Component.translatable("kultivator.meditation.enlightenment.dao", Msg.number(COMPREHENSION_GAIN),
							dao.displayName()).withColor(dao.color()));
		}
		Msg.chat(player, message.withStyle(ChatFormatting.YELLOW));
		Advancements.grant(player, "enlightenment");
	}

	/** True while the player's golden state lasts. */
	public static boolean isActive(ServerPlayer player) {
		return ENDS.containsKey(player.getUUID());
	}

	/** Ends the golden state when its time is up. Called every tick (by Meditation.tick). */
	public static void tick(ServerPlayer player, CultivationData data) {
		Long end = ENDS.get(player.getUUID());
		if (end == null) {
			return;
		}
		if (player.level().getGameTime() >= end || !player.isAlive()) {
			ENDS.remove(player.getUUID());
			if (data.enlightened()) {
				data.setEnlightened(false);
				Cultivators.syncNow(player);
			}
		}
	}

	/** Forgets the player (left, died or respawned); the flag itself is a session value. */
	public static void forget(ServerPlayer player) {
		ENDS.remove(player.getUUID());
	}

	private Enlightenment() {
	}
}
