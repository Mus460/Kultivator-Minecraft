package dev.kultivator.talisman;

import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.CaveAbode;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.sect.Sparring;
import dev.kultivator.sect.event.PromotionTrial;
import dev.kultivator.sect.event.Tournament;
import dev.kultivator.sect.event.VoidRealm;
import dev.kultivator.technique.FlightLease;
import dev.kultivator.technique.source.InheritanceTrials;
import dev.kultivator.trial.HeartDemons;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Jimat Pulang / Return Talisman (DESIGN 19.3.6): a 5 s channel (holding the talisman), cancelled by damage or by moving
 * more than 1 block. Target in order: the cave abode in this dimension, the respawn point in this dimension, the world
 * spawn in the Overworld; otherwise refused. A safe spot only ({@code Targeting.safeSpot}); 10 min cooldown; never a
 * portal, never another dimension.
 * <p>
 * Sumber: <i>terinspirasi dari RMJ: Immortal Realm</i> ch. 289 (a golden talisman bursts into golden flames and its user
 * reappears thousands of km away) and RI ch. 12, 27 (thousand-mile talismans for trips home).
 */
public final class ReturnTalisman {

	/** Length of the channel (the talisman's use duration). */
	public static final int CHANNEL_TICKS = 100;
	/** Moving further than this from where the channel began cancels it. */
	public static final double MAX_MOVE = 1.0D;
	/** Ticks between two returns. */
	public static final long COOLDOWN_TICKS = 12000L;
	/** No return within this many ticks of being hurt. */
	public static final long HURT_LOCK_TICKS = 200L;
	private static final String COOLDOWN_KEY = "ret";

	private record Channel(Vec3 start, long time) {
	}

	private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F) {
				LAST_HURT.put(player.getUUID(), player.level().getGameTime());
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			CHANNELS.remove(player.getUUID());
			LAST_HURT.remove(player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CHANNELS.clear();
			LAST_HURT.clear();
		});
	}

	/** Why the player cannot start a return now, or null. */
	public static @Nullable Component refusal(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		long now = player.level().getGameTime();
		long left = cooldownLeft(player);
		if (left > 0L) {
			return Component.translatable("kultivator.talismans.return.cooldown", Math.max(1L, (left + 19L) / 20L));
		}
		if (data.breakingThrough() || Breakthrough.isBusy(player) || Tribulation.isActive(player)) {
			return Component.translatable("kultivator.talismans.return.refuse.breakthrough");
		}
		if (HeartDemons.inTrial(player) || InheritanceTrials.active(player)) {
			return Component.translatable("kultivator.talismans.return.refuse.trial");
		}
		if (DuelRules.inDuel(player) || Sparring.inDuel(player)) {
			return Component.translatable("kultivator.talismans.return.refuse.duel");
		}
		UUID id = player.getUUID();
		if (PromotionTrial.running(id) || Tournament.running(id) || VoidRealm.climbing(id)) {
			return Component.translatable("kultivator.talismans.return.refuse.sect");
		}
		if (Kehancuran.ordering(id)) {
			return Component.translatable("kultivator.talismans.return.refuse.kehancuran");
		}
		if (data.flyingSword() || FlightLease.holder(player) != null) {
			return Component.translatable("kultivator.talismans.return.refuse.flying");
		}
		Long hurt = LAST_HURT.get(player.getUUID());
		if (hurt != null && now - hurt < HURT_LOCK_TICKS) {
			return Component.translatable("kultivator.talismans.return.refuse.hurt", Math.max(1L, (HURT_LOCK_TICKS - (now - hurt) + 19L) / 20L));
		}
		return null;
	}

	/** Ticks left of the 10-minute cooldown ({@code extra.talisman.ret} = game time of the last return). */
	public static long cooldownLeft(ServerPlayer player) {
		long last = Talismans.section(Cultivators.get(player)).getLongOr(COOLDOWN_KEY, Long.MIN_VALUE);
		if (last == Long.MIN_VALUE) {
			return 0L;
		}
		return Math.max(0L, last + COOLDOWN_TICKS - player.level().getGameTime());
	}

	/** The channel begins (the talisman's use started on the server). */
	static void begin(ServerPlayer player) {
		CHANNELS.put(player.getUUID(), new Channel(player.position(), player.level().getGameTime()));
		player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
				SoundSource.PLAYERS, 0.7F, 1.2F);
	}

	/** One tick of the channel ({@code done} ticks held); false when it broke (the caller stops the use). */
	static boolean tick(ServerPlayer player, int done) {
		Channel channel = CHANNELS.get(player.getUUID());
		if (channel == null) {
			return false;
		}
		if (player.position().distanceToSqr(channel.start()) > MAX_MOVE * MAX_MOVE) {
			cancel(player, "kultivator.talismans.return.moved");
			return false;
		}
		Long hurt = LAST_HURT.get(player.getUUID());
		if (hurt != null && hurt >= channel.time()) {
			cancel(player, "kultivator.talismans.return.hurt");
			return false;
		}
		if (done % 10 == 0) {
			Fx.send(player.level(), player.position(), FxType.TALISMAN, TalismanFxIds.RETURN_CHANNEL, done, 0.0F);
		}
		if (done % 20 == 0) {
			player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
					SoundSource.PLAYERS, 0.5F, 0.8F + done / 100.0F);
		}
		return true;
	}

	/** The holder let go before the end. */
	static void released(ServerPlayer player) {
		if (CHANNELS.remove(player.getUUID()) != null) {
			Msg.actionBar(player, Component.translatable("kultivator.talismans.return.released").withColor(0xA8B5B0));
		}
	}

	private static void cancel(ServerPlayer player, String key) {
		CHANNELS.remove(player.getUUID());
		Msg.actionBar(player, Component.translatable(key).withColor(0xFF9A7A));
		player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
				0.6F, 1.2F);
	}

	/** The channel finished: finds the target and moves the player there. True when the talisman was used. */
	static boolean finish(ServerPlayer player) {
		Channel channel = CHANNELS.remove(player.getUUID());
		if (channel == null) {
			return false;
		}
		Component why = refusal(player);
		if (why != null) {
			Msg.chat(player, why.copy().withColor(0xFF9A7A));
			return false;
		}
		ServerLevel level = player.level();
		List<Component> tried = new ArrayList<>();
		for (Target target : targets(player)) {
			Vec3 spot = findSpot(level, player, target.pos());
			if (spot == null) {
				tried.add(target.name());
				continue;
			}
			Vec3 from = player.position();
			Fx.send(level, from, FxType.TALISMAN, TalismanFxIds.RETURN_FLASH, 0, 0.0F);
			level.playSound(null, from.x, from.y, from.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.9F, 0.8F);
			player.stopRiding();
			player.teleportTo(level, spot.x, spot.y, spot.z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
			player.setPortalCooldown();
			player.resetFallDistance();
			Fx.send(level, spot, FxType.TALISMAN, TalismanFxIds.RETURN_FLASH, 1, 0.0F);
			level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.9F, 1.2F);
			Talismans.section(Cultivators.get(player)).putLong(COOLDOWN_KEY, level.getGameTime());
			Cultivators.dirty(player);
			Msg.chat(player, Component.translatable("kultivator.talismans.return.arrived", target.name()).withColor(0xFFC94A));
			return true;
		}
		if (tried.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.talismans.return.no_target").withColor(0xFF9A7A));
		} else {
			Msg.chat(player, Component.translatable("kultivator.talismans.return.no_spot").withColor(0xFF9A7A));
		}
		return false;
	}

	private record Target(Vec3 pos, Component name) {
	}

	/** The abode, the respawn point and the world spawn, each only when it lies in the player's dimension. */
	private static List<Target> targets(ServerPlayer player) {
		List<Target> list = new ArrayList<>();
		ServerLevel level = player.level();
		GlobalPos abode = CaveAbode.of(player);
		if (abode != null && abode.dimension().equals(level.dimension())) {
			list.add(new Target(CaveAbode.top(abode.pos()), Component.translatable("kultivator.talismans.return.to.abode")));
		}
		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		if (respawn != null && respawn.respawnData().dimension().equals(level.dimension())) {
			list.add(new Target(Vec3.atBottomCenterOf(respawn.respawnData().pos()), Component.translatable("kultivator.talismans.return.to.respawn")));
		}
		if (level.dimension() == Level.OVERWORLD) {
			BlockPos spawn = level.getRespawnData().pos();
			list.add(new Target(Vec3.atBottomCenterOf(spawn), Component.translatable("kultivator.talismans.return.to.spawn")));
		}
		return list;
	}

	/** A safe spot at the position or one of its eight neighbours (read-only). */
	private static @Nullable Vec3 findSpot(ServerLevel level, ServerPlayer player, Vec3 pos) {
		Vec3 spot = Targeting.safeSpot(level, player, pos);
		if (spot != null) {
			return spot;
		}
		int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
		for (int[] offset : around) {
			spot = Targeting.safeSpot(level, player, pos.add(offset[0], 0.0D, offset[1]));
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	/** Forgets the channel without a message (the talisman left the hand). */
	static void forget(ServerPlayer player) {
		CHANNELS.remove(player.getUUID());
	}

	/** True while the channel runs. */
	public static boolean channeling(ServerPlayer player) {
		return CHANNELS.containsKey(player.getUUID());
	}

	/** Resets the 10-minute cooldown (tests). */
	public static void resetCooldown(ServerPlayer player) {
		CompoundTag section = Talismans.section(Cultivators.get(player));
		section.remove(COOLDOWN_KEY);
		Cultivators.dirty(player);
	}

	private ReturnTalisman() {
	}
}
