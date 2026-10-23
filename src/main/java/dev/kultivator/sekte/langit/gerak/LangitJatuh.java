package dev.kultivator.sekte.langit.gerak;

import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.DuniaTickets;
import dev.kultivator.formasi.effect.b.JaringAwanEffect;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.sekte.langit.api.SkyHallView;
import dev.kultivator.technique.FlightLease;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

/**
 * The base Cloud Net of DESIGN 37.5.6 (a): nobody of the sect dies falling off its own island. Entities only, never a
 * block. Every 5 ticks, for each sky site (tethered ones too) with a player near it, the protected bodies inside its zone
 * (footprint + 24 horizontally, down past the catch line and the stair foot) are looked at:
 * <ul>
 * <li>standing inside the warded volume: their <b>safe spot</b> is noted (staggered, once a second per body; LRU of
 * {@link #SAFE_MAX});</li>
 * <li>falling ({@code dy < -0.6}) below {@code skyFloorY + 2}: Slow Falling 60 ticks and a gentle brake;</li>
 * <li>{@code tangkapJarak} (24) blocks under the island, or 8 blocks above the ground, after a real fall: <b>caught</b>, put
 * back on the last safe spot (or the {@code arrival} anchor), fall distance reset, Slowness I for 20 ticks, a cloud puff
 * at both ends ({@code FX_TANGKAP}) and the breeze's sigh.</li>
 * </ul>
 * Protected: the owner, sect mates, the owner's disciples and the tame animals of members ({@code LangitApi.member});
 * while the Jaring Awan formation runs (37.5.6 b) also invited players and guests named by {@link #addGuestRule} (37G's
 * allied NPC visitors). Raiders, cloud pirates and wild animals are never caught. Anyone flying (flying sword lease,
 * creative flight, elytra, levitation, no-gravity bodies such as patrol disciples) or riding is left alone; a Cloud Dive
 * ({@link TerjunAwan}) is never caught. Runs whatever the MSPT (safety) and follows only {@code jaringDasar}.
 */
public final class LangitJatuh {

	public static final int PERIOD = 5;
	public static final int HALL_CACHE_TICKS = 40;
	public static final int SAFE_MAX = 64;
	public static final double FALL_SPEED = -0.6D;
	public static final double BRAKE_SPEED = -0.8D;
	public static final int SLOW_FALL_TICKS = 60;
	public static final int SLOWNESS_TICKS = 20;
	/** Blocks above the ground at which a fall under the island is caught anyway. */
	public static final int GROUND_MARGIN = 8;
	/** A catch needs at least this drop below the last safe spot (no catch for hopping down a few stair steps). */
	public static final int MIN_DROP = 6;
	/** Fall damage is ignored this long after a catch (ticks). */
	public static final long CATCH_GRACE = 60L;
	/** Horizontal margin of the zone around the island footprint. */
	public static final int ZONE_MARGIN = 24;

	/** A noted safe spot. */
	public record Safe(ResourceKey<Level> dim, BlockPos pos, long tick) {
	}

	/** Safe spots, least recently refreshed first (LRU, {@link #SAFE_MAX}). */
	private static final Map<UUID, Safe> AMAN = new LinkedHashMap<>(SAFE_MAX * 2, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<UUID, Safe> eldest) {
			return size() > SAFE_MAX;
		}
	};
	/** The game time a body last stood inside a sky volume (for the Cloud Dive's "left the rim within 2 s"). */
	private static final Map<UUID, Long> LAST_GROUND = new HashMap<>();
	/** Game time of the last catch per body (fall damage grace). */
	private static final Map<UUID, Long> CAUGHT = new HashMap<>();
	private static final List<BiPredicate<UUID, Entity>> GUESTS = new CopyOnWriteArrayList<>();
	private static final Map<ResourceKey<Level>, List<SkyHallView>> HALLS = new HashMap<>();
	private static long hallsAt = Long.MIN_VALUE;

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(LangitJatuh::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!source.is(DamageTypeTags.IS_FALL)) {
				return true;
			}
			Long at = CAUGHT.get(entity.getUUID());
			return at == null || entity.level().getGameTime() - at > CATCH_GRACE;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			clearAll();
			JaringAwanEffect.clearAll();
		});
	}

	/**
	 * Adds a guest rule (37G: allied NPC sect visitors): {@code test(owner, entity)} true makes the entity protected over
	 * {@code owner}'s island while the Jaring Awan formation runs.
	 */
	public static void addGuestRule(BiPredicate<UUID, Entity> test) {
		if (test != null) {
			GUESTS.add(test);
		}
	}

	/** The last safe spot of {@code id} (any dimension), or null. */
	public static @Nullable BlockPos lastSafe(UUID id) {
		Safe safe = AMAN.get(id);
		return safe == null ? null : safe.pos();
	}

	/** Notes that {@code id} stood inside a sky volume at {@code gameTime} (the dive test). */
	static void markGround(UUID id, long gameTime) {
		LAST_GROUND.put(id, gameTime);
	}

	/** The game time of {@code id}'s last catch, or {@code Long.MIN_VALUE}. */
	static long lastCaught(UUID id) {
		Long at = CAUGHT.get(id);
		return at == null ? Long.MIN_VALUE : at;
	}

	/** The game time {@code id} last stood inside a sky volume, or {@code Long.MIN_VALUE}. */
	static long lastGround(UUID id) {
		Long at = LAST_GROUND.get(id);
		return at == null ? Long.MIN_VALUE : at;
	}

	/**
	 * Catches {@code entity} now (the {@code uji langit_sekte jatuh} test and 37G): back to its last safe spot over the
	 * sky hall whose zone holds it (or, failing that, a hall it belongs to). False when no sky hall applies or no spot was
	 * found.
	 */
	public static boolean catchNow(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		SkyHallView hall = zoneOf(level, entity.position());
		if (hall == null) {
			for (SkyHallView each : halls(level)) {
				if (LangitGerak.member(each.owner(), entity)) {
					hall = each;
					break;
				}
			}
		}
		return hall != null && caught(level, hall, entity);
	}

	/** True when {@code entity} would be protected over {@code hall} now. */
	public static boolean protects(SkyHallView hall, Entity entity) {
		if (LangitGerak.member(hall.owner(), entity)) {
			return true;
		}
		if (!JaringAwanEffect.active(hall.owner(), entity.level().getGameTime())) {
			return false;
		}
		if (entity instanceof ServerPlayer player) {
			try {
				if (LangitGerak.api().invited(hall.owner(), player.getUUID())) {
					return true;
				}
			} catch (RuntimeException ignored) {
				// a broken implementation never takes the net away from members
			}
		}
		for (BiPredicate<UUID, Entity> rule : GUESTS) {
			try {
				if (rule.test(hall.owner(), entity)) {
					return true;
				}
			} catch (RuntimeException ignored) {
				// a failing rule protects nobody
			}
		}
		return false;
	}

	// ================================================================== the tick

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % PERIOD != 0 || !LangitConfig.get().jaringDasar) {
			return;
		}
		long tick = server.getTickCount();
		if (tick % 6000L == 0L) {
			// keep the per-body notes small on a long session: drop catches past their grace and stale ground marks
			long now = server.overworld().getGameTime();
			CAUGHT.values().removeIf(at -> now - at > CATCH_GRACE || at > now);
			LAST_GROUND.values().removeIf(at -> now - at > 6000L || at > now);
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			List<SkyHallView> halls = halls(level);
			for (SkyHallView hall : halls) {
				AABB zone = zone(hall);
				if (!anyPlayerIn(level, zone)) {
					continue;
				}
				boolean guests = !GUESTS.isEmpty();
				for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, zone, entity -> entity.isAlive() && !entity.isSpectator()
						&& (guests || candidate(entity)))) {
					tickBody(level, hall, body, tick);
				}
			}
		}
	}

	private static void tickBody(ServerLevel level, SkyHallView hall, LivingEntity body, long tick) {
		if (!protects(hall, body)) {
			return;
		}
		UUID id = body.getUUID();
		long now = level.getGameTime();
		Vec3 pos = body.position();
		if (body.onGround()) {
			if (hall.inVolume(pos) && !body.isPassenger()) {
				LAST_GROUND.put(id, now);
				if (Math.floorMod(tick / PERIOD + body.getId(), 4) == 0) {
					AMAN.put(id, new Safe(level.dimension(), body.blockPosition(), now));
				}
			}
			TerjunAwan.landed(body);
			return;
		}
		if (exempt(body)) {
			return;
		}
		// a player's motion is the client's (getKnownMovement); everyone else's is the server's own
		double vy = body.getKnownMovement().y;
		if (TerjunAwan.diving(id) || TerjunAwan.tryStart(level, hall, body, vy)) {
			return;
		}
		if (vy >= FALL_SPEED) {
			return;
		}
		double y = body.getY();
		if (y < hall.skyFloorY() + 2) {
			MobEffectInstance slow = body.getEffect(MobEffects.SLOW_FALLING);
			if (slow == null || slow.getDuration() < SLOW_FALL_TICKS / 2) {
				body.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, false, true));
			}
			if (vy < BRAKE_SPEED) {
				Vec3 motion = body.getKnownMovement();
				body.setDeltaMovement(motion.x * 0.8D, BRAKE_SPEED, motion.z * 0.8D);
				body.needsSync = true;
			}
		}
		Safe safe = AMAN.get(id);
		int drop = safe != null && safe.dim().equals(level.dimension()) ? safe.pos().getY() - body.getBlockY() : Integer.MAX_VALUE;
		if (drop < MIN_DROP) {
			return;
		}
		int catchLine = hall.skyFloorY() - LangitConfig.get().tangkapJarak;
		if (y < catchLine || y < hall.skyFloorY() && nearGround(level, body.blockPosition())) {
			caught(level, hall, body);
		}
	}

	/** The kinds the base net can protect at all (players, disciples, owned animals): everything else is skipped cheaply. */
	private static boolean candidate(LivingEntity entity) {
		return entity instanceof ServerPlayer || entity instanceof DiscipleEntity
				|| entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null;
	}

	/** Flying, riding or otherwise held up: never slowed or caught. */
	private static boolean exempt(LivingEntity body) {
		if (body.isPassenger() || body.isNoGravity() || body.isFallFlying() || body.hasEffect(MobEffects.LEVITATION)) {
			return true;
		}
		if (body instanceof ServerPlayer player) {
			if (player.getAbilities().flying || FlightLease.holder(player) != null) {
				return true;
			}
			try {
				return Cultivators.get(player).flyingSword();
			} catch (RuntimeException exception) {
				return false;
			}
		}
		return false;
	}

	/** A solid block within {@link #GROUND_MARGIN} below {@code feet} (loaded chunks only, read-only). */
	private static boolean nearGround(ServerLevel level, BlockPos feet) {
		BlockPos.MutableBlockPos cursor = feet.mutable();
		for (int i = 1; i <= GROUND_MARGIN; i++) {
			cursor.setY(feet.getY() - i);
			if (cursor.getY() < level.getMinY() || !level.isLoaded(cursor)) {
				return true;
			}
			if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty() || !level.getFluidState(cursor).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Puts {@code body} back up. */
	private static boolean caught(ServerLevel level, SkyHallView hall, LivingEntity body) {
		Vec3 target = target(level, hall, body);
		if (target == null) {
			return false;
		}
		BlockPos at = BlockPos.containing(target);
		if (!level.isLoaded(at)) {
			// the island the body stood on is not loaded (rare): ask for it and keep the fall slowed meanwhile
			DuniaTickets.touch(level, ChunkPos.containing(at), 1);
			body.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, false, true));
			return false;
		}
		Vec3 spot = Targeting.safeSpot(level, body, target);
		if (spot == null) {
			spot = Targeting.safeSpot(level, body, Vec3.atBottomCenterOf(hall.origin().above()));
		}
		if (spot == null) {
			body.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, false, true));
			return false;
		}
		BlockPos from = body.blockPosition();
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 0.8F, 1.0F);
		if (body instanceof ServerPlayer player) {
			player.stopRiding();
			player.teleportTo(level, spot.x, spot.y, spot.z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
			Msg.actionBar(player, Component.translatable("kultivator.sekte_langit.jatuh.tangkap").withColor(0xBFE6FF));
			Advancements.grant(player, LangitIds.ADV + "tertangkap");
		} else {
			if (body instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			body.teleportTo(spot.x, spot.y, spot.z);
		}
		body.setDeltaMovement(Vec3.ZERO);
		body.needsSync = true;
		body.resetFallDistance();
		body.removeEffect(MobEffects.SLOW_FALLING);
		body.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_TICKS, 0, false, false, true));
		CAUGHT.put(body.getUUID(), level.getGameTime());
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 0.8F, 1.0F);
		LangitGerak.fx(level, LangitIds.FX_TANGKAP, from, BlockPos.containing(spot));
		return true;
	}

	/** The last safe spot when it still lies in the hall's volume, else the {@code arrival} anchor, else the throne. */
	private static @Nullable Vec3 target(ServerLevel level, SkyHallView hall, LivingEntity body) {
		Safe safe = AMAN.get(body.getUUID());
		if (safe != null && safe.dim().equals(level.dimension()) && hall.inVolume(safe.pos())) {
			return Vec3.atBottomCenterOf(safe.pos());
		}
		BlockPos arrival = LangitGerak.anchor(hall.owner(), "arrival");
		if (arrival != null) {
			return Vec3.atBottomCenterOf(arrival);
		}
		return hall.tethered() ? null : Vec3.atBottomCenterOf(hall.origin().above());
	}

	// ================================================================== halls and zones

	/** The sky sites of {@code level}, re-read every {@link #HALL_CACHE_TICKS} ticks. */
	static List<SkyHallView> halls(ServerLevel level) {
		long now = level.getServer().getTickCount();
		if (now - hallsAt >= HALL_CACHE_TICKS || now < hallsAt) {
			HALLS.clear();
			hallsAt = now;
		}
		return HALLS.computeIfAbsent(level.dimension(), key -> {
			try {
				return new ArrayList<>(LangitGerak.api().all(level));
			} catch (RuntimeException exception) {
				return List.of();
			}
		});
	}

	/** The sky site whose net zone holds {@code pos}, or null. */
	static @Nullable SkyHallView zoneOf(ServerLevel level, Vec3 pos) {
		for (SkyHallView hall : halls(level)) {
			if (zone(hall).contains(pos)) {
				return hall;
			}
		}
		return null;
	}

	/** Footprint + 24 horizontally, from below the catch line (and the stair foot) to the top of the volume. */
	static AABB zone(SkyHallView hall) {
		BoundingBox island = hall.islandBox();
		BoundingBox volume = hall.volume();
		int bottom = hall.skyFloorY() - LangitConfig.get().tangkapJarak - 16;
		int minX = Math.min(island.minX(), volume.minX()) - ZONE_MARGIN;
		int minZ = Math.min(island.minZ(), volume.minZ()) - ZONE_MARGIN;
		int maxX = Math.max(island.maxX(), volume.maxX()) + ZONE_MARGIN;
		int maxZ = Math.max(island.maxZ(), volume.maxZ()) + ZONE_MARGIN;
		BoundingBox kaki = hall.kakiBox();
		if (kaki != null) {
			bottom = Math.min(bottom, kaki.minY() - 4);
			minX = Math.min(minX, kaki.minX() - 8);
			minZ = Math.min(minZ, kaki.minZ() - 8);
			maxX = Math.max(maxX, kaki.maxX() + 8);
			maxZ = Math.max(maxZ, kaki.maxZ() + 8);
		}
		int top = Math.max(volume.maxY(), island.maxY()) + 1;
		return new AABB(minX, bottom, minZ, maxX + 1, top, maxZ + 1);
	}

	private static boolean anyPlayerIn(ServerLevel level, AABB zone) {
		for (ServerPlayer player : level.players()) {
			double x = player.getX();
			double z = player.getZ();
			if (x >= zone.minX - 16 && x <= zone.maxX + 16 && z >= zone.minZ - 16 && z <= zone.maxZ + 16) {
				return true;
			}
		}
		return false;
	}

	static void clearAll() {
		AMAN.clear();
		LAST_GROUND.clear();
		CAUGHT.clear();
		HALLS.clear();
		hallsAt = Long.MIN_VALUE;
	}

	private LangitJatuh() {
	}
}
