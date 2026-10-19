package dev.kultivator.purba;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.Cultivators;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.DeathGuard;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * The safety rules of P9-purba (DESIGN 26.1, 26.4.4, 26.5.2, 26.5.3):
 * <ul>
 * <li>DeathGuard {@code LANGIT}: a lethal blow during the player's star calamity run (any source) fails the run at 1 HP; during
 * a wrath descent it is Kalah at 1 HP with Luka Langit; a lethal primordial skill ({@code primordialDeathGuard}) leaves 1 HP
 * with Luka Purba and ends that player's part in the fight. Before the Totem, so totems and cheat-death charges are never
 * spent; never against {@code BYPASSES_INVULNERABILITY} (the spine skips those);</li>
 * <li>Luka Purba and Luka Langit: technique damage ×0.85 through the caster-factor clamp;</li>
 * <li>the module's entities (and Taotie's Pelahap) are refused when loaded from disk;</li>
 * <li>the deaths of beasts and envoys go to their fights.</li>
 * </ul>
 */
final class PurbaGuard {

	/** Luka Purba / Luka Langit: technique damage ×0.85 (game design; inside the spine's 0.8-1.30 caster clamp). */
	public static final double LUKA_FACTOR = 0.85D;

	static void init() {
		DeathGuard.register(DeathGuard.Stage.LANGIT, PurbaGuard::preventDeath);
		TechniquePower.addCasterFactor((attacker, spec, target) ->
				attacker instanceof ServerPlayer player && (lukaLeft(player) > 0L || langitLeft(player) > 0L) ? LUKA_FACTOR : 1.0D);
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, flag) -> reason != EntitySpawnReason.LOAD
				|| !(entity instanceof PrimordialBeastEntity || entity instanceof PrimordialRelicEntity || entity instanceof HeavenEnvoyEntity
				|| entity.entityTags().contains(Purba.TAG) || entity.entityTags().contains(PurbaHits.PELAHAP_TAG)));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof PrimordialBeastEntity beast) {
				PrimordialEvents.onBeastDeath(beast);
			} else if (entity instanceof HeavenEnvoyEntity envoy) {
				EnvoyAi.afterDeath(envoy, source);
			}
		});
	}

	private static boolean preventDeath(ServerPlayer player, DamageSource source, float amount) {
		if (StarCalamity.guard(player)) {
			return true;
		}
		if (HeavenlyWrath.guard(player)) {
			return true;
		}
		Entity attacker = source.getEntity();
		if (attacker instanceof PrimordialBeastEntity beast && KultivatorConfig.get().primordialDeathGuard) {
			player.setHealth(1.0F);
			player.setRemainingFireTicks(0);
			player.removeEffect(MobEffects.WITHER);
			CompoundTag purba = Purba.section(Cultivators.get(player));
			purba.putLong("luka_until", Purba.now(player) + PrimordialEvents.LUKA_TICKS);
			Cultivators.dirty(player);
			PrimordialEvents.Fight fight = beast.fight;
			if (fight != null) {
				fight.markOut(player);
			}
			Msg.chat(player, Component.translatable("kultivator.purba.luka", PrimordialEvents.LUKA_TICKS / 1200).withStyle(ChatFormatting.DARK_RED));
			return true;
		}
		return false;
	}

	/** Ticks left of Luka Purba. */
	static long lukaLeft(ServerPlayer player) {
		return Math.max(0L, Purba.read(Cultivators.get(player)).getLongOr("luka_until", 0L) - Purba.now(player));
	}

	/** Ticks left of Luka Langit. */
	static long langitLeft(ServerPlayer player) {
		return Math.max(0L, Purba.read(Cultivators.get(player)).getLongOr("langit_until", 0L) - Purba.now(player));
	}

	private PurbaGuard() {
	}
}
