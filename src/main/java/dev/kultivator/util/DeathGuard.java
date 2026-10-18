package dev.kultivator.util;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.sect.Sparring;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The only player {@code ALLOW_DEATH} listener of the mod (DESIGN 22.14.6): asks its guards in a fixed order and stops at
 * the first that prevents the death.
 * <ol>
 * <li>{@code DUEL}: sparring and duel clamps (they end the bout instead);</li>
 * <li>{@code TRIBULATION}: the never-lethal tribulation rule;</li>
 * <li>{@code TOTEM}: a death-protection item (Totem of Undying) in either hand always acts first. Fabric's ALLOW_DEATH
 * wraps the {@code isDeadOrDying} check that guards vanilla's {@code checkTotemDeathProtection} (verified in 26.3), so
 * the death is simply allowed here and vanilla's totem fires;</li>
 * <li>{@code TECHNIQUE}: cheat-death charges and HP floors; never against {@code #kultivator:unblockable}, never in a
 * duel, and one shared 10-minute lockout after any of them acted;</li>
 * <li>{@code PERK}: the layer perk Jiwa Tak Hancur (phase 7).</li>
 * </ol>
 */
public final class DeathGuard {

	public enum Stage { DUEL, TRIBULATION, TOTEM, TECHNIQUE, PERK }

	@FunctionalInterface
	public interface Guard {
		/** True when the guard kept the player alive (it set the health itself). */
		boolean preventDeath(ServerPlayer player, DamageSource source, float amount);
	}

	/** The shared lockout of every technique cheat-death source: 10 minutes. */
	public static final int TECHNIQUE_LOCKOUT_TICKS = 20 * 60 * 10;

	private static final Map<Stage, List<Guard>> GUARDS = new EnumMap<>(Stage.class);
	/** Player -> game time the technique lockout ends (session only). */
	private static final Map<UUID, Long> LOCKOUT = new HashMap<>();

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DEATH.register(DeathGuard::allowDeath);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOCKOUT.clear());
	}

	public static synchronized void register(Stage stage, Guard guard) {
		GUARDS.computeIfAbsent(stage, key -> new ArrayList<>()).add(guard);
	}

	/** True while the shared technique cheat-death lockout runs for the player. */
	public static boolean techniqueLocked(ServerPlayer player) {
		Long until = LOCKOUT.get(player.getUUID());
		if (until == null) {
			return false;
		}
		if (player.level().getGameTime() >= until) {
			LOCKOUT.remove(player.getUUID());
			return false;
		}
		return true;
	}

	/** Ticks left of the technique lockout (0 when none). */
	public static long lockoutLeft(ServerPlayer player) {
		Long until = LOCKOUT.get(player.getUUID());
		return until == null ? 0L : Math.max(0L, until - player.level().getGameTime());
	}

	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}
		for (Stage stage : Stage.values()) {
			if (stage == Stage.TOTEM && holdsDeathProtection(player) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}
			if (stage == Stage.TECHNIQUE && (source.is(DamageHooks.UNBLOCKABLE) || DuelRules.inDuel(player) || Sparring.inDuel(player)
					|| techniqueLocked(player))) {
				continue;
			}
			List<Guard> list = GUARDS.get(stage);
			if (list == null) {
				continue;
			}
			for (Guard guard : list) {
				boolean prevented;
				try {
					prevented = guard.preventDeath(player, source, amount);
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Kultivator death guard ({}) failed", stage, exception);
					prevented = false;
				}
				if (prevented) {
					if (stage == Stage.TECHNIQUE) {
						LOCKOUT.put(player.getUUID(), player.level().getGameTime() + TECHNIQUE_LOCKOUT_TICKS);
					}
					return false;
				}
			}
		}
		return true;
	}

	private static boolean holdsDeathProtection(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (player.getItemInHand(hand).get(DataComponents.DEATH_PROTECTION) != null) {
				return true;
			}
		}
		return false;
	}

	private DeathGuard() {
	}
}
