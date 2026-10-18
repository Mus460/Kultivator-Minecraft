package dev.kultivator.elite;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.cultivation.XpSources;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Kejenuhan Bertarung / Battle Fatigue (DESIGN 19.3.14), a balance rule with no lore claim. A saturation S in
 * {@code extra.combat.fatigue} (with {@code fatigue_t}) grows with every monster kill and decays by 1 per 100 ticks,
 * computed lazily from game time; kill XP and the normal-core chance are multiplied by
 * {@code 1 / (1 + max(0, S − 25) / 25)}. Normal fighting never passes 25.
 */
public final class BattleFatigue {

	/** {@code extra.combat}: owned by P7-tempur (DESIGN 19.9). */
	public static final String SECTION = "combat";
	public static final String KEY = "fatigue";
	public static final String KEY_TIME = "fatigue_t";
	/** Saturation that costs nothing. */
	public static final double FREE = 25.0D;
	/** Saturation above {@link #FREE} that halves the gains. */
	public static final double SCALE = 25.0D;
	/** One point decays every this many ticks. */
	public static final double DECAY_TICKS = 100.0D;
	public static final double PER_KILL = 1.0D;
	/** Slimes and magma cubes split into many small kills. */
	public static final double PER_SMALL_KILL = 0.3D;
	public static final double PER_ELITE_KILL = 3.0D;
	public static final double MAX = 2000.0D;
	/** The action-bar hint shows below this factor, at most once per {@link #HINT_INTERVAL} ticks. */
	public static final double HINT_BELOW = 0.6D;
	public static final long HINT_INTERVAL = 1200L;

	private static final Map<UUID, Long> LAST_HINT = new HashMap<>();

	/** The factor on kill XP and the normal-core chance, 1 when the player is not saturated. */
	public static double factor(ServerPlayer player) {
		return factorOf(saturation(player));
	}

	/** The factor for a saturation {@code s}. */
	public static double factorOf(double s) {
		return 1.0D / (1.0D + Math.max(0.0D, s - FREE) / SCALE);
	}

	/** The current saturation S (decay applied). */
	public static double saturation(ServerPlayer player) {
		CompoundTag combat = Cultivators.get(player).extra().getCompoundOrEmpty(SECTION);
		return decayed(combat.getDoubleOr(KEY, 0.0D), combat.getLongOr(KEY_TIME, 0L), now(player));
	}

	/** One kill of {@code killed} by the player: +1 (slimes and magma cubes +0.3, elites +3); shows the hint when due. */
	public static void addKill(ServerPlayer player, LivingEntity killed) {
		if (!(killed instanceof Enemy)) {
			return;
		}
		double amount = EliteMonsters.isElite(killed) ? PER_ELITE_KILL
				: killed.getType() == EntityTypes.SLIME || killed.getType() == EntityTypes.MAGMA_CUBE ? PER_SMALL_KILL : PER_KILL;
		set(player, saturation(player) + amount);
		double factor = factor(player);
		if (factor < HINT_BELOW) {
			long now = now(player);
			Long last = LAST_HINT.get(player.getUUID());
			if (last == null || now - last >= HINT_INTERVAL || now < last) {
				LAST_HINT.put(player.getUUID(), now);
				XpSources.holdDisplay(player, 60);
				Msg.actionBar(player, Component.translatable("kultivator.combat.fatigue.hint", format(factor)).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/** Sets S (tests and debug), clamped to 0..{@link #MAX}. */
	public static void set(ServerPlayer player, double value) {
		CultivationData data = Cultivators.get(player);
		CompoundTag combat = section(data);
		combat.putDouble(KEY, Math.max(0.0D, Math.min(MAX, Double.isFinite(value) ? value : 0.0D)));
		combat.putLong(KEY_TIME, now(player));
		Cultivators.dirty(player);
	}

	/** Forgets the hint timer of a player who left. */
	static void forget(UUID player) {
		LAST_HINT.remove(player);
	}

	static void clearAll() {
		LAST_HINT.clear();
	}

	/** {@code extra.combat}, created when missing. */
	static CompoundTag section(CultivationData data) {
		CompoundTag combat = data.extra().getCompound(SECTION).orElse(null);
		if (combat == null) {
			combat = new CompoundTag();
			data.extra().put(SECTION, combat);
		}
		return combat;
	}

	/** "×0.40". */
	public static String format(double factor) {
		return String.format(Locale.ROOT, "%.2f", factor);
	}

	private static double decayed(double value, long since, long now) {
		if (!(value > 0.0D) || !Double.isFinite(value)) {
			return 0.0D;
		}
		long elapsed = Math.max(0L, now - since);
		return Math.max(0.0D, value - elapsed / DECAY_TICKS);
	}

	private static long now(ServerPlayer player) {
		return player.level().getServer().overworld().getGameTime();
	}

	private BattleFatigue() {
	}
}
