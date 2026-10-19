package dev.kultivator.formasi.effect.b;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.formasi.core.CoreState;
import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.core.Cores;
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
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The trip of M3 and L5 (DESIGN 24.4.10): the traveller stands within 1.5 blocks of the source eye and channels (M3 60
 * ticks, 30 with a keeper at the source; L5 100 ticks); damage or moving more than 1 block cancels. Refused in the Return
 * Talisman situations, while riding, when hurt within 10 s and during the per-player cooldown. On arrival the traveller
 * stands on top of the destination eye through {@code Targeting.safeSpot} (never a portal, never another dimension), with
 * up to 4 FOLLOW disciples within 4 blocks; pets, mobs and items never travel. During an L5 channel a short-lived
 * ender-pearl chunk ticket (40 ticks, refreshed every 20) loads the destination; nothing is ever force-loaded.
 */
public final class TransportChannel {

	public static final double STAND_RANGE = 1.5D;
	public static final double MAX_MOVE = 1.0D;
	public static final int M3_CHANNEL = 60;
	public static final int M3_CHANNEL_KEPT = 30;
	public static final int L5_CHANNEL = 100;
	public static final long HURT_LOCK_TICKS = 200L;
	public static final double QI_COST = 10.0D;
	public static final int MAX_DISCIPLES = 4;
	public static final double DISCIPLE_RANGE = 4.0D;
	public static final int TICKET_RADIUS = 2;

	/** One channel in progress. */
	private record Trip(UUID player, ResourceKey<Level> dim, int fromId, int toId, Vec3 start, long startTick, long endTick, int length,
			boolean ancient, double cost, boolean payQi, BlockPos dest) {
	}

	private static final Map<UUID, Trip> CHANNELS = new HashMap<>();
	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

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
			COOLDOWN.clear();
		});
	}

	/** Why {@code player} cannot travel now (the Return Talisman list, riding, hurt, cooldown), or null. */
	public static @Nullable Component refusal(ServerPlayer player, boolean checkCooldown) {
		if (!KultivatorConfig.get().formationTransport) {
			return Component.translatable("kultivator.formasib.trip.refuse.off");
		}
		CultivationData data = Cultivators.get(player);
		long now = player.level().getGameTime();
		UUID id = player.getUUID();
		if (data.breakingThrough() || Breakthrough.isBusy(player) || Tribulation.isActive(player)) {
			return Component.translatable("kultivator.formasib.trip.refuse.breakthrough");
		}
		if (HeartDemons.inTrial(player) || InheritanceTrials.active(player)) {
			return Component.translatable("kultivator.formasib.trip.refuse.trial");
		}
		if (DuelRules.inDuel(player) || Sparring.inDuel(player)) {
			return Component.translatable("kultivator.formasib.trip.refuse.duel");
		}
		if (PromotionTrial.running(id) || Tournament.running(id) || VoidRealm.climbing(id)) {
			return Component.translatable("kultivator.formasib.trip.refuse.sect");
		}
		if (Kehancuran.ordering(id)) {
			return Component.translatable("kultivator.formasib.trip.refuse.kehancuran");
		}
		if (data.flyingSword() || FlightLease.holder(player) != null) {
			return Component.translatable("kultivator.formasib.trip.refuse.flying");
		}
		if (player.isPassenger() || player.isVehicle()) {
			return Component.translatable("kultivator.formasib.trip.refuse.riding");
		}
		Long hurt = LAST_HURT.get(id);
		if (hurt != null && now - hurt < HURT_LOCK_TICKS) {
			return Component.translatable("kultivator.formasib.trip.refuse.hurt", Math.max(1L, (HURT_LOCK_TICKS - (now - hurt) + 19L) / 20L));
		}
		if (checkCooldown) {
			Long until = COOLDOWN.get(id);
			if (until != null && now < until) {
				return Component.translatable("kultivator.formasib.trip.refuse.cooldown", Math.max(1L, (until - now + 19L) / 20L));
			}
		}
		return null;
	}

	/** True while {@code player} channels a trip. */
	public static boolean channeling(ServerPlayer player) {
		return CHANNELS.containsKey(player.getUUID());
	}

	/**
	 * Starts (or with {@code instant} performs at once) a trip already checked by {@code TransportNetwork}: channel
	 * length, Daya cost (paid by the source core on its next pulse) and whether the traveller pays qi.
	 */
	static @Nullable Component start(ServerPlayer player, CoreView from, CoreView to, int length, boolean ancient, double cost, boolean payQi,
			boolean instant) {
		if (payQi && Cultivators.get(player).qi() < QI_COST) {
			return Component.translatable("kultivator.formasib.trip.refuse.qi", Math.round(QI_COST));
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Trip trip = new Trip(player.getUUID(), from.dim(), from.id(), to.id(), player.position(), now, now + length, length, ancient, cost, payQi,
				to.eye());
		if (instant) {
			if (ancient && !level.isLoaded(trip.dest())) {
				// A ticket loads the chunk asynchronously: wait one second for it instead of refusing at once.
				ticket(level, trip);
				CHANNELS.put(player.getUUID(), new Trip(trip.player(), trip.dim(), trip.fromId(), trip.toId(), trip.start(), now, now + 20L, 20,
						true, cost, payQi, trip.dest()));
				return null;
			}
			finish(player, trip);
			return null;
		}
		CHANNELS.put(player.getUUID(), trip);
		level.playSound(null, from.eye().getX() + 0.5D, from.eye().getY() + 1.0D, from.eye().getZ() + 0.5D, SoundEvents.RESPAWN_ANCHOR_CHARGE,
				SoundSource.PLAYERS, 0.7F, ancient ? 0.8F : 1.2F);
		Msg.actionBar(player, Component.translatable("kultivator.formasib.trip.channel", Math.max(1, (length + 19) / 20)).withColor(0xD8C8FF));
		return null;
	}

	/** Every server tick: channels move on, break, or arrive. */
	static void tick(MinecraftServer server) {
		if (CHANNELS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Trip>> it = CHANNELS.entrySet().iterator();
		while (it.hasNext()) {
			Trip trip = it.next().getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(trip.player());
			if (player == null || !player.level().dimension().equals(trip.dim()) || !player.isAlive()) {
				it.remove();
				if (player != null) {
					unticket(server.getLevel(trip.dim()), trip);
				}
				continue;
			}
			ServerLevel level = player.level();
			long now = level.getGameTime();
			if (player.position().distanceToSqr(trip.start()) > MAX_MOVE * MAX_MOVE) {
				it.remove();
				unticket(level, trip);
				cancel(player, "kultivator.formasib.trip.moved");
				continue;
			}
			Long hurt = LAST_HURT.get(player.getUUID());
			if (hurt != null && hurt >= trip.startTick()) {
				it.remove();
				unticket(level, trip);
				cancel(player, "kultivator.formasib.trip.hurt");
				continue;
			}
			long done = now - trip.startTick();
			if (done % 10L == 0L) {
				CoreView from = Cores.byId(trip.dim(), trip.fromId());
				Vec3 at = from == null ? player.position() : Vec3.atBottomCenterOf(from.eye().above());
				Fx.send(level, at, FxType.FORMASI, FxB.CHANNEL, (int) done, trip.length());
			}
			if (trip.ancient() && done % 20L == 0L) {
				ticket(level, trip);
			}
			if (now >= trip.endTick()) {
				it.remove();
				finish(player, trip);
			}
		}
	}

	/** Drops the trip's ender-pearl ticket at once (arrival or cancel; it would expire after 40 ticks anyway). */
	private static void unticket(@Nullable ServerLevel level, Trip trip) {
		if (level != null && trip.ancient()) {
			level.getChunkSource().removeTicketWithRadius(TicketType.ENDER_PEARL, ChunkPos.containing(trip.dest()), TICKET_RADIUS);
		}
	}

	private static void ticket(ServerLevel level, Trip trip) {
		level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, ChunkPos.containing(trip.dest()), TICKET_RADIUS);
	}

	private static void cancel(ServerPlayer player, String key) {
		Msg.actionBar(player, Component.translatable(key).withColor(0xFF9A7A));
		player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
				0.5F, 1.3F);
	}

	private static void finish(ServerPlayer player, Trip trip) {
		Component why = refusal(player, false);
		ServerLevel level = player.level();
		CoreView dest = Cores.byId(trip.dim(), trip.toId());
		if (why == null && (dest == null || dest.state() == CoreState.BROKEN)) {
			why = Component.translatable("kultivator.formasib.trip.refuse.broken");
		}
		if (why == null && TransportNetwork.destinationOff(dest)) {
			why = Component.translatable("kultivator.formasib.trip.refuse.dest_off");
		}
		if (why == null && !level.isLoaded(trip.dest())) {
			why = Component.translatable("kultivator.formasib.trip.refuse.unloaded");
		}
		Vec3 spot = null;
		if (why == null) {
			spot = arrival(level, player, trip.dest());
			if (spot == null) {
				why = Component.translatable("kultivator.formasib.trip.refuse.unsafe");
			}
		}
		CultivationData data = Cultivators.get(player);
		if (why == null && trip.payQi()) {
			if (data.qi() < QI_COST) {
				why = Component.translatable("kultivator.formasib.trip.refuse.qi", Math.round(QI_COST));
			} else {
				data.setQi(data.qi() - QI_COST);
				Cultivators.dirty(player);
			}
		}
		// The arrival spot is known (or the trip failed): the ticket is no longer needed; the traveller's own ticket keeps
		// the destination loaded from here on.
		unticket(level, trip);
		if (why != null) {
			Msg.chat(player, why.copy().withColor(0xFF9A7A));
			return;
		}
		TransportNetwork.addDebt(trip.dim(), trip.fromId(), trip.cost());
		Vec3 from = player.position();
		int style = trip.ancient() ? 1 : 0;
		Fx.send(level, from, spot, FxType.FORMASI, FxB.JUMP_OUT, style, 0.0F);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.9F, 0.8F);
		java.util.List<DiscipleEntity> followers = new java.util.ArrayList<>();
		for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
			if (followers.size() >= MAX_DISCIPLES) {
				break;
			}
			if (disciple.isAlive() && disciple.level() == level && disciple.mode() == DiscipleEntity.Mode.FOLLOW && !disciple.isPassenger()
					&& disciple.position().distanceToSqr(trip.start()) <= DISCIPLE_RANGE * DISCIPLE_RANGE) {
				followers.add(disciple);
			}
		}
		player.stopRiding();
		player.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), player.getYRot(), player.getXRot(), false);
		player.setPortalCooldown();
		player.resetFallDistance();
		int index = 0;
		for (DiscipleEntity disciple : followers) {
			int[] off = BUtil.rotate(1 + index / 4, 0, index);
			Vec3 near = Targeting.safeSpot(level, disciple, spot.add(off[0], 0.0D, off[1]));
			Vec3 at = near == null ? spot : near;
			disciple.getNavigation().stop();
			disciple.teleportTo(at.x, at.y, at.z);
			disciple.setPortalCooldown();
			disciple.resetFallDistance();
			index++;
		}
		Fx.send(level, spot, from, FxType.FORMASI, FxB.JUMP_IN, style, 0.0F);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.9F, 0.8F);
		long cooldown = trip.ancient() ? dev.kultivator.formasi.stack.Stacking.TRANSPORT_L5_COOLDOWN : dev.kultivator.formasi.stack.Stacking.TRANSPORT_M3_COOLDOWN;
		COOLDOWN.put(player.getUUID(), level.getGameTime() + cooldown);
		BUtil.addCount(player, "trips", 1);
		Advancements.grant(player, "first_transfer");
		Msg.actionBar(player, Component.translatable("kultivator.formasib.trip.arrived").withColor(0xD8C8FF));
	}

	/** On top of the destination eye, else on one of the cells around it (read-only search). */
	private static @Nullable Vec3 arrival(ServerLevel level, ServerPlayer player, BlockPos eye) {
		Vec3 top = Vec3.atBottomCenterOf(eye.above());
		Vec3 spot = Targeting.safeSpot(level, player, top);
		if (spot != null) {
			return spot;
		}
		for (int i = 0; i < 4; i++) {
			int[] off = BUtil.rotate(2, 0, i);
			spot = Targeting.safeSpot(level, player, top.add(off[0], 0.0D, off[1]));
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	static void clearAll() {
		CHANNELS.clear();
		COOLDOWN.clear();
	}

	private TransportChannel() {
	}
}
