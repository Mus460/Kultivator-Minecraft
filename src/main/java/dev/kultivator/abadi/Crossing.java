package dev.kultivator.abadi;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.alam.Alam;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.Sects;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Travel between the homeworld and Alam Abadi (DESIGN 26.3.4): the Jimat Lintas Alam's 5 s channel (cancelled when hurt or
 * moving more than a block, the {@code ReturnTalisman} rule), sect mates who click [Ikut], up to 3 Murid Inti+ disciples in
 * FOLLOW within 16 blocks through {@code Disciples.depart} / {@code arrive}, the 60 s cooldown, the return point
 * ({@code extra.abadi.ret}) and the last Alam Abadi position ({@code extra.abadi.last}).
 * <p>
 * Sapuan murid: when the owner leaves Alam Abadi by any route (talisman, Return Talisman, death, portal, logout, op teleport)
 * their disciples there depart and arrive beside the owner on the owner's next level (or at the next login); a disciple
 * loaded in Alam Abadi whose owner is not there goes the same way (or to the hall's away list when the owner is offline),
 * which also runs at server start. Join check: a non-ascended survival player found in Alam Abadi is sent to {@code ret}
 * (else the respawn point) on the next tick.
 */
final class Crossing {

	public static final int CHANNEL_TICKS = 100;
	public static final double MOVE_LIMIT = 1.0D;
	public static final long COOLDOWN_TICKS = 1200L;
	public static final double MATE_RANGE = 8.0D;
	public static final int MAX_MATES = 4;
	public static final double DISCIPLE_RANGE = 16.0D;
	public static final int MAX_DISCIPLES = 3;
	public static final int ARRIVAL_SLOW_FALL = 200;
	public static final String HOME_KEY = "home";
	public static final String AWAY_REASON = "abadi";

	private static final class Channel {
		final UUID owner;
		final Vec3 start;
		final ResourceKey<Level> dim;
		final long end;
		final Set<UUID> mates = new LinkedHashSet<>();

		Channel(ServerPlayer player, long end) {
			this.owner = player.getUUID();
			this.start = player.position();
			this.dim = player.level().dimension();
			this.end = end;
		}
	}

	private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
	private static final Set<UUID> HURT = new HashSet<>();

	static void init() {
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (Abadi.isImmortalLevel(origin) && !Abadi.isImmortalLevel(destination)) {
				sweep(player, origin);
			}
			if (Abadi.isImmortalLevel(destination)) {
				Abadi.schedule(1, () -> joinCheck(player));
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!Abadi.isImmortalLevel(newPlayer.level())) {
				ServerLevel immortal = Abadi.immortalLevel(newPlayer.level().getServer());
				if (immortal != null) {
					sweep(newPlayer, immortal);
				}
			}
		});
		ServerPlayerEvents.JOIN.register(player -> Abadi.schedule(1, () -> joinCheck(player)));
		ServerPlayerEvents.LEAVE.register(player -> {
			CHANNELS.remove(player.getUUID());
			HURT.remove(player.getUUID());
			if (Abadi.isImmortalLevel(player.level())) {
				sweep(player, player.level());
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof DiscipleEntity disciple && Abadi.isImmortalLevel(level)) {
				Abadi.schedule(2, () -> relocate(disciple));
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel immortal = Abadi.immortalLevel(server);
			if (immortal != null) {
				for (Entity entity : immortal.getAllEntities()) {
					if (entity instanceof DiscipleEntity disciple) {
						Abadi.schedule(2, () -> relocate(disciple));
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CHANNELS.clear();
			HURT.clear();
		});
	}

	static boolean channeling(ServerPlayer player) {
		return CHANNELS.containsKey(player.getUUID());
	}

	/** A player was hurt (cancels a running channel on the next tick). */
	static void hurt(ServerPlayer player) {
		if (CHANNELS.containsKey(player.getUUID())) {
			HURT.add(player.getUUID());
		}
	}

	static long cooldownLeft(ServerPlayer player) {
		return Math.max(0L, Abadi.extra(player).getLongOr("cross_cd", 0L) - Abadi.now(player.level().getServer()));
	}

	// ================================================================== the channel

	/** {@code abadi lintas} and the Lintas button: starts the channel when a talisman is in the inventory. */
	static boolean useFromInventory(ServerPlayer player) {
		if (player.getInventory().countItem(AbadiItems.REALM_CROSSING_TALISMAN) <= 0) {
			Msg.chat(player, Component.translatable("kultivator.abadi.cross.no_talisman").withStyle(ChatFormatting.GRAY));
			return false;
		}
		return start(player);
	}

	/** Why the player cannot cross now, or null. */
	private static @Nullable Component blocker(ServerPlayer player) {
		if (!KultivatorConfig.get().immortalRealm || Abadi.immortalLevel(player.level().getServer()) == null) {
			return Component.translatable("kultivator.abadi.why.disabled");
		}
		if (!Abadi.ascended(player)) {
			return Component.translatable("kultivator.abadi.cross.not_ascended");
		}
		if (channeling(player)) {
			return Component.translatable("kultivator.abadi.why.running");
		}
		long left = cooldownLeft(player);
		if (left > 0L) {
			return Component.translatable("kultivator.abadi.cross.cooldown", Msg.number(Math.ceil(left / 20.0D)));
		}
		Component busy = Abadi.busyReason(player, false);
		if (busy != null) {
			return busy;
		}
		if (Abadi.recentlyHurt(player)) {
			return Component.translatable("kultivator.abadi.why.hurt");
		}
		return null;
	}

	/** Starts the 5 s channel of the crossing talisman; true when it started. */
	static boolean start(ServerPlayer player) {
		Component why = blocker(player);
		if (why != null) {
			Msg.chat(player, Component.translatable("kultivator.abadi.cross.refuse", why).withStyle(ChatFormatting.GRAY));
			return false;
		}
		MinecraftServer server = player.level().getServer();
		Channel channel = new Channel(player, Abadi.now(server) + CHANNEL_TICKS);
		CHANNELS.put(player.getUUID(), channel);
		HURT.remove(player.getUUID());
		Fx.send(player.level(), player.position(), player.position(), FxType.ABADI, AbadiFxIds.CHANNEL, CHANNEL_TICKS, 0.0F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);
		Msg.chat(player, Component.translatable(Abadi.isImmortalLevel(player.level()) ? "kultivator.abadi.cross.channel_home" : "kultivator.abadi.cross.channel_up",
				CHANNEL_TICKS / 20).withStyle(ChatFormatting.AQUA));
		for (ServerPlayer mate : player.level().players()) {
			if (mate == player || mate.distanceTo(player) > MATE_RANGE || !Sects.areSectMates(player, mate) || !Abadi.ascended(mate)
					|| channeling(mate)) {
				continue;
			}
			MutableComponent button = Component.translatable("kultivator.abadi.cross.join_button").withStyle(style -> style
					.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent.RunCommand("/kultivasi abadi ikut " + player.getName().getString())));
			Msg.chat(mate, Component.translatable("kultivator.abadi.cross.invite", player.getDisplayName(), button).withStyle(ChatFormatting.GOLD));
		}
		return true;
	}

	/** {@code abadi ikut <name>}: joins the channel of a sect mate within 8 blocks. */
	static boolean join(ServerPlayer player, String name) {
		ServerPlayer owner = player.level().getServer().getPlayerList().getPlayerByName(name);
		Channel channel = owner == null ? null : CHANNELS.get(owner.getUUID());
		if (owner == null || channel == null || owner == player || owner.level() != player.level() || owner.distanceTo(player) > MATE_RANGE
				|| !Sects.areSectMates(owner, player)) {
			Msg.chat(player, Component.translatable("kultivator.abadi.cross.join_none").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (!Abadi.ascended(player)) {
			Msg.chat(player, Component.translatable("kultivator.abadi.cross.not_ascended").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (channel.mates.size() >= MAX_MATES) {
			Msg.chat(player, Component.translatable("kultivator.abadi.cross.join_full").withStyle(ChatFormatting.GRAY));
			return false;
		}
		channel.mates.add(player.getUUID());
		Msg.chat(player, Component.translatable("kultivator.abadi.cross.joined", owner.getDisplayName()).withStyle(ChatFormatting.GREEN));
		Msg.chat(owner, Component.translatable("kultivator.abadi.cross.mate", player.getDisplayName()).withStyle(ChatFormatting.GREEN));
		return true;
	}

	static void tick(MinecraftServer server, long ticks) {
		if (!CHANNELS.isEmpty()) {
			long now = Abadi.now(server);
			for (Channel channel : List.copyOf(CHANNELS.values())) {
				ServerPlayer player = server.getPlayerList().getPlayer(channel.owner);
				if (player == null) {
					CHANNELS.remove(channel.owner);
					continue;
				}
				if (!player.isAlive() || player.level().dimension() != channel.dim || player.position().distanceTo(channel.start) > MOVE_LIMIT
						|| HURT.remove(channel.owner)) {
					CHANNELS.remove(channel.owner);
					Msg.chat(player, Component.translatable("kultivator.abadi.cross.broken").withStyle(ChatFormatting.GRAY));
					continue;
				}
				long left = channel.end - now;
				if (left > 0L && left % 20L == 0L) {
					Msg.actionBar(player, Component.translatable("kultivator.abadi.cross.counting", left / 20L).withStyle(ChatFormatting.AQUA));
					Fx.send(player.level(), player.position(), player.position(), FxType.ABADI, AbadiFxIds.CHANNEL, (int) left, 0.0F);
				}
				if (left <= 0L) {
					CHANNELS.remove(channel.owner);
					HURT.remove(channel.owner);
					complete(player, channel);
				}
			}
		}
		if (ticks % 20L == 0L) {
			processHome(server);
		}
	}

	/** The channel finished: home from Alam Abadi, otherwise up to Alam Abadi (the last position, else the arrival island). */
	private static void complete(ServerPlayer player, Channel channel) {
		MinecraftServer server = player.level().getServer();
		List<ServerPlayer> mates = new ArrayList<>();
		for (UUID id : channel.mates) {
			ServerPlayer mate = server.getPlayerList().getPlayer(id);
			if (mate != null && mate.isAlive() && mate.level() == player.level() && mate.distanceTo(player) <= MATE_RANGE + 2.0D && !channeling(mate)
					&& Abadi.busyReason(mate, false) == null) {
				mates.add(mate);
			}
		}
		Abadi.extra(player).putLong("cross_cd", Abadi.now(server) + COOLDOWN_TICKS);
		Cultivators.dirty(player);
		if (Abadi.isImmortalLevel(player.level())) {
			saveLast(player);
			for (ServerPlayer mate : mates) {
				saveLast(mate);
			}
			TeleportTransition home = homeTransition(player);
			ServerPlayer moved = travel(player, home.newLevel(), home.position(), home.yRot(), home.xRot(), mates);
			if (moved != null) {
				Advancements.grant(moved, "realm_crossing");
				Abadi.activity(moved, Abadi.ACT_CROSSED);
				Msg.chat(moved, Component.translatable("kultivator.abadi.cross.home").withStyle(ChatFormatting.AQUA));
			}
		} else {
			ServerLevel target = Abadi.immortalLevel(server);
			if (target == null) {
				return;
			}
			Ascend.saveReturn(player);
			for (ServerPlayer mate : mates) {
				Ascend.saveReturn(mate);
			}
			Vec3 pos = lastPosition(player);
			if (pos == null) {
				BlockPos island = ArrivalIsland.arrival(server);
				pos = new Vec3(island.getX() + 0.5D, island.getY() + 0.1D, island.getZ() + 0.5D);
			}
			ServerPlayer moved = travel(player, target, pos, player.getYRot(), player.getXRot(), mates);
			if (moved != null) {
				Msg.chat(moved, Component.translatable("kultivator.abadi.cross.up").withStyle(ChatFormatting.AQUA));
				Abadi.activity(moved, Abadi.ACT_CROSSED);
			}
		}
	}

	/** Where the talisman (or the {@code uji abadi pulang} test) sends a player home: {@code ret}, else the respawn point, else world spawn. */
	static TeleportTransition homeTransition(ServerPlayer player) {
		CompoundTag ret = Abadi.extra(player).getCompoundOrEmpty("ret");
		if (!ret.isEmpty()) {
			Identifier dim = Identifier.tryParse(ret.getStringOr("dim", ""));
			if (dim != null) {
				ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dim);
				ServerLevel level = key.equals(Abadi.IMMORTAL_LEVEL) || key.equals(Alam.REALM_LEVEL) ? null : player.level().getServer().getLevel(key);
				if (level != null) {
					return new TeleportTransition(level, new Vec3(ret.getDoubleOr("x", 0.0D), ret.getDoubleOr("y", 64.0D), ret.getDoubleOr("z", 0.0D)),
							Vec3.ZERO, ret.getFloatOr("yRot", 0.0F), ret.getFloatOr("xRot", 0.0F), TeleportTransition.DO_NOTHING);
				}
			}
		}
		return player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
	}

	private static void saveLast(ServerPlayer player) {
		CompoundTag last = new CompoundTag();
		last.putDouble("x", player.getX());
		last.putDouble("y", player.getY());
		last.putDouble("z", player.getZ());
		Abadi.extra(player).put("last", last);
		Cultivators.dirty(player);
	}

	private static @Nullable Vec3 lastPosition(ServerPlayer player) {
		CompoundTag last = Abadi.extra(player).getCompoundOrEmpty("last");
		if (last.isEmpty()) {
			return null;
		}
		double x = last.getDoubleOr("x", 0.0D);
		double z = last.getDoubleOr("z", 0.0D);
		int radius = KultivatorConfig.get().immortalRealmRadius;
		if (x * x + z * z > (double) radius * radius) {
			return null;
		}
		return new Vec3(x, last.getDoubleOr("y", 120.0D), z);
	}

	// ================================================================== travel with companions

	/**
	 * Teleports {@code player} (with up to 3 eligible disciples and the given mates) to {@code pos} in {@code level}. Disciples
	 * depart before and arrive beside the owner afterwards. Arriving in Alam Abadi gives Slow Falling (the void catch stays);
	 * two ticks later a player inside a wall is moved onto a safe spot. Returns the moved player, or null.
	 */
	static @Nullable ServerPlayer travel(ServerPlayer player, ServerLevel level, Vec3 pos, float yRot, float xRot, List<ServerPlayer> mates) {
		for (DiscipleEntity disciple : companions(player)) {
			CompoundTag tag = Disciples.depart(disciple, AWAY_REASON);
			if (!tag.isEmpty()) {
				pushHome(player, tag);
			}
		}
		ServerPlayer moved = teleport(player, level, pos, yRot, xRot);
		int i = 0;
		for (ServerPlayer mate : mates) {
			i++;
			double angle = Math.PI * 2.0D * i / Math.max(1, mates.size());
			Vec3 beside = pos.add(Math.cos(angle) * 1.5D, 0.0D, Math.sin(angle) * 1.5D);
			for (DiscipleEntity disciple : companions(mate)) {
				CompoundTag tag = Disciples.depart(disciple, AWAY_REASON);
				if (!tag.isEmpty()) {
					pushHome(mate, tag);
				}
			}
			ServerPlayer movedMate = teleport(mate, level, beside, yRot, xRot);
			if (movedMate != null) {
				Msg.chat(movedMate, Component.translatable("kultivator.abadi.cross.with", player.getDisplayName()).withStyle(ChatFormatting.AQUA));
			}
		}
		return moved;
	}

	private static @Nullable ServerPlayer teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yRot, float xRot) {
		ServerPlayer moved = player.teleport(new TeleportTransition(level, pos, Vec3.ZERO, yRot, xRot, TeleportTransition.DO_NOTHING));
		ServerPlayer who = moved != null ? moved : player;
		who.setPortalCooldown();
		who.resetFallDistance();
		if (Abadi.isImmortalLevel(level)) {
			who.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ARRIVAL_SLOW_FALL, 0, false, false, true));
		}
		Fx.send(level, pos, pos, FxType.ABADI, AbadiFxIds.CROSS, 0, 0.0F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
		Abadi.schedule(2, () -> unstick(who));
		return moved;
	}

	/** Two ticks after a crossing: out of a wall onto a safe spot, falling back to the heightmap top (when there is ground). */
	private static void unstick(ServerPlayer player) {
		if (player.isRemoved() || !player.isInWall()) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 spot = Targeting.safeSpot(level, player, player.position());
		if (spot == null) {
			BlockPos at = player.blockPosition();
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ());
			if (top <= level.getMinY()) {
				return;
			}
			spot = new Vec3(at.getX() + 0.5D, top, at.getZ() + 0.5D);
		}
		player.teleportTo(spot.x, spot.y, spot.z);
	}

	/** Up to 3 of the player's own Murid Inti+ disciples in FOLLOW within 16 blocks, the highest ranks first. */
	private static List<DiscipleEntity> companions(ServerPlayer owner) {
		List<DiscipleEntity> list = new ArrayList<>();
		for (DiscipleEntity disciple : Disciples.loaded(owner.getUUID())) {
			if (disciple.role() == DiscipleEntity.Role.DISCIPLE && disciple.isAlive() && !disciple.departed() && disciple.level() == owner.level()
					&& disciple.distanceTo(owner) <= DISCIPLE_RANGE && SectRanks.rank(disciple.data()) >= SectRanks.INTI && disciple.mode().follows()
					&& !DuelRules.inDuel(disciple) && !disciple.isReleasing()) {
				list.add(disciple);
			}
		}
		list.sort(Comparator.comparingInt((DiscipleEntity d) -> SectRanks.rank(d.data())).reversed());
		return list.size() > MAX_DISCIPLES ? new ArrayList<>(list.subList(0, MAX_DISCIPLES)) : list;
	}

	// ================================================================== the disciple sweep and the home list

	/** Departs every disciple of the owner in {@code immortal} (Alam Abadi) and queues them to arrive beside the owner. */
	static void sweep(ServerPlayer owner, ServerLevel immortal) {
		int n = 0;
		for (DiscipleEntity disciple : Disciples.loaded(owner.getUUID())) {
			if (disciple.level() != immortal || disciple.role() != DiscipleEntity.Role.DISCIPLE || disciple.departed()) {
				continue;
			}
			CompoundTag tag = Disciples.depart(disciple, AWAY_REASON);
			if (!tag.isEmpty()) {
				pushHome(owner, tag);
				n++;
			}
		}
		if (n > 0) {
			Msg.chat(owner, Component.translatable("kultivator.abadi.sweep", n).withStyle(ChatFormatting.AQUA));
		}
	}

	/** A disciple loaded in Alam Abadi whose owner is not there goes home (the owner online) or to the hall's away list. */
	private static void relocate(DiscipleEntity disciple) {
		if (disciple.isRemoved() || !disciple.isAlive() || disciple.departed() || disciple.role() != DiscipleEntity.Role.DISCIPLE
				|| !(disciple.level() instanceof ServerLevel level) || !Abadi.isImmortalLevel(level)) {
			return;
		}
		UUID owner = disciple.ownerId();
		if (owner == null) {
			return;
		}
		ServerPlayer master = level.getServer().getPlayerList().getPlayer(owner);
		if (master != null) {
			if (Abadi.isImmortalLevel(master.level())) {
				return;
			}
			CompoundTag tag = Disciples.depart(disciple, AWAY_REASON);
			if (!tag.isEmpty()) {
				pushHome(master, tag);
			}
		} else if (Halls.hallOf(owner) != null) {
			Halls.storeAway(owner, disciple, Abadi.now(level.getServer()), AWAY_REASON, new CompoundTag());
		}
	}

	private static ListTag homeList(ServerPlayer owner) {
		CompoundTag abadi = Abadi.extra(owner);
		ListTag list = abadi.getList(HOME_KEY).orElse(null);
		if (list == null) {
			list = new ListTag();
			abadi.put(HOME_KEY, list);
		}
		return list;
	}

	static void pushHome(ServerPlayer owner, CompoundTag tag) {
		homeList(owner).add(tag.copy());
		Cultivators.dirty(owner);
	}

	/** Every second: departed disciples of online owners arrive beside their master (not during a ritual or a realm run). */
	private static void processHome(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			CompoundTag abadi = Cultivators.get(player).extra().getCompoundOrEmpty(Abadi.EXTRA_KEY);
			ListTag list = abadi.getList(HOME_KEY).orElse(null);
			if (list == null || list.isEmpty() || !player.isAlive() || player.isSpectator() || Ascend.active(player) || channeling(player)
					|| Alam.isRealmLevel(player.level()) || Alam.inRun(player)) {
				continue;
			}
			for (int i = list.size() - 1; i >= 0; i--) {
				if (list.get(i) instanceof CompoundTag tag) {
					DiscipleEntity body = Disciples.arrive(player.level(), player.position(), tag, player.getUUID());
					if (body != null) {
						list.remove(i);
						Fx.burst(player.level(), body.position().add(0.0D, 1.0D, 0.0D), 0xBFF0D8, 20, 1.0F);
						weaken(body);
					}
				} else {
					list.remove(i);
				}
			}
			Cultivators.dirty(player);
		}
	}

	/** Tekanan Alam Abadi: a disciple below Kenaikan Abadi is weakened while in Alam Abadi (refreshed by {@link Natives}). */
	static void weaken(DiscipleEntity disciple) {
		if (Abadi.isImmortalLevel(disciple.level()) && !disciple.data().realm().atLeast(Realm.IMMORTAL_ASCENSION)) {
			disciple.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, true, true));
		}
	}

	/** The join check: a non-ascended survival player in Alam Abadi goes to {@code ret} (else the respawn point). */
	private static void joinCheck(ServerPlayer player) {
		if (player.isRemoved() || !Abadi.isImmortalLevel(player.level()) || player.isCreative() || player.isSpectator() || Abadi.ascended(player)) {
			return;
		}
		TeleportTransition home = homeTransition(player);
		teleport(player, home.newLevel(), home.position(), home.yRot(), home.xRot());
		Msg.chat(player, Component.translatable("kultivator.abadi.cross.not_ascended").withStyle(ChatFormatting.GRAY));
	}

	/** {@code uji abadi pulang}: home at once (no channel, no cooldown). */
	static void testHome(ServerPlayer player) {
		if (Abadi.isImmortalLevel(player.level())) {
			saveLast(player);
		}
		TeleportTransition home = homeTransition(player);
		travel(player, home.newLevel(), home.position(), home.yRot(), home.xRot(), List.of());
	}

	private Crossing() {
	}
}
