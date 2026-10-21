package dev.kultivator.dunia.reruntuhan;

import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.reruntuhan.gua.SisaJiwaEntity;
import dev.kultivator.dunia.reruntuhan.menara.Menara;
import dev.kultivator.dunia.reruntuhan.penjara.IblisKecilEntity;
import dev.kultivator.dunia.reruntuhan.penjara.Prisons;
import dev.kultivator.dunia.reruntuhan.penjara.RajaIblisEntity;
import dev.kultivator.kehancuran.ProtectedZones;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.DeathGuard;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * The safety rules of module R (DESIGN 34.5.5-34.5.7):
 * <ul>
 * <li>DeathGuard stage {@code REALM} (before the Totem, never against {@code BYPASSES_INVULNERABILITY}): a lethal blow
 * inside the tower leaves 1 HP and sends the party to the lobby with Luka Menara ({@code towerDeathGuard}); a lethal
 * blow of a ruin's guardian, the mirror shadow or a puzzle hazard leaves 1 HP (a wrong move never kills); a lethal blow
 * of a demon of the prison leaves 1 HP with Luka Iblis and throws the player out of the arena
 * ({@code bossDeathGuard});</li>
 * <li>Luka Iblis and Luka Menara: technique damage ×0.85 through the caster-factor clamp;</li>
 * <li>the module's entities are refused when loaded from disk (they are never saved; state lives in anchors);</li>
 * <li>the tower dimension is a protected zone of Mode Kehancuran (the existing {@code ProtectedZones} hook).</li>
 * </ul>
 */
public final class RGuard {

	public static final double LUKA_FACTOR = 0.85D;
	/** True while module R itself deals damage (backlash, pulses): the guard treats it as a puzzle hazard. */
	static boolean hazard;

	static void init() {
		DeathGuard.register(DeathGuard.Stage.REALM, RGuard::preventDeath);
		TechniquePower.addCasterFactor((attacker, spec, target) ->
				attacker instanceof ServerPlayer player && (lukaLeft(player, "luka_iblis") > 0L || lukaLeft(player, "luka_menara") > 0L)
						? LUKA_FACTOR : 1.0D);
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, flag) -> reason != EntitySpawnReason.LOAD || !ours(entity));
		ProtectedZones.addZoneProvider((level, pos) -> Menara.isTowerLevel(level));
	}

	/** True for an entity of module R (never loaded from disk). */
	public static boolean ours(Entity entity) {
		return entity instanceof TekaPartEntity || entity instanceof ColliderEntity || entity instanceof RCreature || entity instanceof SisaJiwaEntity
				|| entity.entityTags().contains(RCreature.TAG) || entity.entityTags().contains(TekaPartEntity.TAG)
				|| entity.entityTags().contains(ColliderEntity.TAG) || entity.entityTags().contains(dev.kultivator.dunia.reruntuhan.menara.TowerRun.FOE_TAG);
	}

	private static boolean preventDeath(ServerPlayer player, DamageSource source, float amount) {
		if (Menara.inRun(player) && Reruntuhan.config().towerDeathGuard) {
			save(player);
			Menara.lethal(player);
			return true;
		}
		Entity attacker = source.getEntity();
		if (attacker instanceof RajaIblisEntity || attacker instanceof IblisKecilEntity) {
			if (!Reruntuhan.config().bossDeathGuard) {
				return false;
			}
			save(player);
			setLuka(player, "luka_iblis", 5 * 60 * 20);
			Msg.chat(player, Component.translatable("kultivator.reruntuhan.penjara.luka", 5).withStyle(ChatFormatting.DARK_RED));
			Prisons.throwOut(player);
			return true;
		}
		if (hazard || attacker instanceof RCreature) {
			save(player);
			Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.guard.saved").withStyle(ChatFormatting.GOLD));
			return true;
		}
		return false;
	}

	/** 1 HP, no fire or wither, and three seconds of shelter so the next blow cannot finish the job. */
	public static void save(ServerPlayer player) {
		player.setHealth(1.0F);
		player.setRemainingFireTicks(0);
		player.removeEffect(MobEffects.WITHER);
		player.removeEffect(MobEffects.POISON);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 4, false, false, true));
	}

	/** Ticks left of a Luka ({@code key} in {@code extra.reruntuhan}). */
	public static long lukaLeft(ServerPlayer player, String key) {
		long until = Cultivators.get(player).extra().getCompoundOrEmpty(Reruntuhan.SECTION).getLongOr(key, 0L);
		return Math.max(0L, until - player.level().getServer().overworld().getGameTime());
	}

	public static void setLuka(ServerPlayer player, String key, int ticks) {
		Reruntuhan.data(player).putLong(key, player.level().getServer().overworld().getGameTime() + ticks);
		Cultivators.dirty(player);
	}

	private RGuard() {
	}
}
