package dev.kultivator.epic;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.DaoProtectors;
import dev.kultivator.cultivation.Deviation;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.Cultivators;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.Sects;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends the 7b epic-moment cues (DESIGN 23.4, 23.5.4): who gets them (the viewer always, others by range), the rate
 * limit (3 per subject per 20 ticks, PRE beats first to go), the seed and the viewer flags. Pure presentation: nothing
 * here changes the world or any data. Flags cost one {@code Mob} box, one {@code Enemy} box and one player pass, only
 * when a cue is sent.
 */
public final class EpicMoments {

	/** Range for other players of a normal scene. */
	public static final double OTHERS_RANGE = 96.0D;
	/** Rate limit: cues per subject per {@link #RATE_WINDOW} ticks. */
	public static final int RATE_MAX = 3;
	public static final int RATE_WINDOW = 20;
	/** SAFE: no mob targeting the viewer within this range. */
	public static final double SAFE_MOB_RANGE = 16.0D;
	/** SAFE: no stranger within this range. */
	public static final double SAFE_PLAYER_RANGE = 12.0D;
	/** RESULT_SAFE: no enemy within this range. */
	public static final double RESULT_ENEMY_RANGE = 24.0D;
	/** IN_COMBAT: hurt by a mob within this many ticks. */
	public static final int COMBAT_TICKS = 100;
	/** SAFE: no damage from outside the scene within this many ticks. */
	public static final int HURT_TICKS = 40;
	/** SAFE: a sect mate who hurt the viewer this recently counts as a stranger. */
	public static final int MATE_HURT_TICKS = 200;
	private static final int LOG_MAX = 16;

	/** One sent cue, for {@code debug momen info}. */
	public record LogEntry(long time, int moment, int beat, int s, int flags, int receivers, String viewer) {
	}

	private static final Map<Long, ArrayDeque<Long>> RATE = new HashMap<>();
	private static final ArrayDeque<LogEntry> LOG = new ArrayDeque<>();

	/**
	 * Sends one beat of {@code moment} to {@code viewer} (camera, bars, cards, music) and to the other players in range
	 * (VFX only). {@code at} is the game time the beat lands, {@code s} the scene scale 0-10, {@code extraFlags} the
	 * caller's {@code EpicIds.FLAG_*} bits; the viewer flags are added here.
	 */
	public static void send(ServerLevel level, int moment, int beat, @Nullable Entity subject, @Nullable ServerPlayer viewer, Vec3 pos,
							long at, int a, int b, float f, int s, int extraFlags) {
		sendChecked(level, moment, beat, subject, viewer, pos, at, a, b, f, s, extraFlags);
	}

	/** {@link #send}, true when the cue went out (so the caller may play the beat's world sounds). */
	static boolean sendChecked(@Nullable ServerLevel level, int moment, int beat, @Nullable Entity subject, @Nullable ServerPlayer viewer,
							   Vec3 pos, long at, int a, int b, float f, int s, int extraFlags) {
		if (level == null || !KultivatorConfig.get().epicMoments) {
			return false;
		}
		try {
			return deliver(level, moment, beat, subject, viewer, pos, at, a, b, f, s, extraFlags);
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.warn("Epic moment cue {}:{} failed", EpicIds.momentName(moment), beat, exception);
			return false;
		}
	}

	private static boolean deliver(ServerLevel level, int moment, int beat, @Nullable Entity subject, @Nullable ServerPlayer viewer, Vec3 pos,
								   long at, int a, int b, float f, int s, int extraFlags) {
		MinecraftServer server = level.getServer();
		boolean pre = isPre(moment, beat);
		boolean lowLoad = lowLoad(server);
		if (pre && lowLoad) {
			return false;
		}
		long now = level.getGameTime();
		long key = rateKey(subject, viewer, pos);
		ArrayDeque<Long> window = RATE.computeIfAbsent(key, k -> new ArrayDeque<>());
		while (!window.isEmpty() && now - window.peekFirst() >= RATE_WINDOW) {
			window.pollFirst();
		}
		// PRE beats go first: they only take a slot while two are still free.
		if (window.size() >= (pre ? RATE_MAX - 1 : RATE_MAX)) {
			return false;
		}
		window.addLast(now);
		if (RATE.size() > 256) {
			prune(now);
		}

		int flags = extraFlags & ~EpicIds.FLAG_CAMERA_OK;
		if (lowLoad) {
			flags |= EpicIds.FLAG_LOW_LOAD;
		}
		if (skyScale(moment, beat, s, b)) {
			flags |= EpicIds.FLAG_SKY_SCALE;
		}
		ServerPlayer protagonist = viewer;
		if (protagonist != null) {
			if (protagonist.isRemoved() || protagonist.level() != level || protagonist.position().distanceTo(pos) > viewerRange(moment, subject, protagonist)) {
				// Too far from someone else's scene: no camera, card or music, only VFX if in range.
				protagonist = null;
			} else {
				flags |= flags(protagonist) & ~EpicIds.FLAG_CAMERA_OK;
			}
		}
		int cameraFlag = 0;
		if (protagonist != null && KultivatorConfig.get().epicCameraShots
				&& protagonist.position().distanceTo(pos) <= cameraRange(moment, subject, protagonist)) {
			cameraFlag = EpicIds.FLAG_CAMERA_OK;
		}
		int subjectId = subject == null ? -1 : subject.getId();
		int viewerId = protagonist == null ? -1 : protagonist.getId();
		int seed = seed(subject != null ? subject.getUUID() : protagonist != null ? protagonist.getUUID() : null, pos, at);
		int scale = Math.max(0, Math.min(EpicIds.SCALE_MAX, s));
		Payloads.EpicMoment others = new Payloads.EpicMoment(moment, beat, subjectId, viewerId, pos.x, pos.y, pos.z, at, seed, a, b, f, scale, flags);
		int receivers = 0;
		if (protagonist != null) {
			Net.send(protagonist, cameraFlag == 0 ? others
					: new Payloads.EpicMoment(moment, beat, subjectId, viewerId, pos.x, pos.y, pos.z, at, seed, a, b, f, scale, flags | cameraFlag));
			receivers++;
		}
		double range = (flags & EpicIds.FLAG_SKY_SCALE) != 0 ? KultivatorConfig.get().epicRange : OTHERS_RANGE;
		for (ServerPlayer player : PlayerLookup.around(level, pos, range)) {
			if (player != protagonist) {
				Net.send(player, others);
				receivers++;
			}
		}
		log(new LogEntry(now, moment, beat, scale, flags | cameraFlag, receivers, protagonist == null ? "-" : protagonist.getScoreboardName()));
		return true;
	}

	/** The viewer flags right now (SAFE, RESULT_SAFE, IN_COMBAT, PROTECTORS, LETHAL_NEXT, LOW_LOAD, CAMERA_OK). */
	public static int flags(ServerPlayer viewer) {
		int flags = 0;
		if (KultivatorConfig.get().epicCameraShots) {
			flags |= EpicIds.FLAG_CAMERA_OK;
		}
		if (lowLoad(viewer.level().getServer())) {
			flags |= EpicIds.FLAG_LOW_LOAD;
		}
		if (inCombat(viewer)) {
			flags |= EpicIds.FLAG_IN_COMBAT;
		}
		if (DaoProtectors.count(viewer) > 0) {
			flags |= EpicIds.FLAG_PROTECTORS;
		}
		if (lethalNext(viewer)) {
			flags |= EpicIds.FLAG_LETHAL_NEXT;
		}
		if (safe(viewer, null)) {
			flags |= EpicIds.FLAG_SAFE;
		}
		if (resultSafe(viewer)) {
			flags |= EpicIds.FLAG_RESULT_SAFE;
		}
		return flags;
	}

	// ================================================================== flag parts (also read by debug momen bendera)

	/** Hurt by a mob within the last {@link #COMBAT_TICKS} ticks. */
	public static boolean inCombat(ServerPlayer viewer) {
		return viewer.getLastHurtByMob() != null && viewer.tickCount - viewer.getLastHurtByMobTimestamp() < COMBAT_TICKS;
	}

	/** The next tribulation strike would fail it ({@code sessionExtra.trib.pct}, read-only). */
	public static boolean lethalNext(ServerPlayer viewer) {
		CompoundTag trib = Cultivators.get(viewer).sessionExtra().getCompoundOrEmpty("trib");
		double pct = trib.getDoubleOr("pct", 0.0D);
		return pct > 0.0D && pct * viewer.getMaxHealth() >= viewer.getHealth() + viewer.getAbsorptionAmount();
	}

	/**
	 * SAFE of DESIGN 23.4.2; every reason it is not is added to {@code reasons} as a translation key suffix (null to skip
	 * collecting them, which stops at the first reason).
	 */
	public static boolean safe(ServerPlayer viewer, @Nullable List<String> reasons) {
		boolean ok = true;
		if (viewer.isSpectator()) {
			ok = reason(reasons, "spectator");
			if (reasons == null) {
				return false;
			}
		}
		if (viewer.isPassenger()) {
			ok = reason(reasons, "riding");
			if (reasons == null) {
				return false;
			}
		}
		if (viewer.isFallFlying()) {
			ok = reason(reasons, "gliding");
			if (reasons == null) {
				return false;
			}
		}
		if (viewer.isSleeping()) {
			ok = reason(reasons, "sleeping");
			if (reasons == null) {
				return false;
			}
		}
		if (viewer.fallDistance > 2.0D) {
			ok = reason(reasons, "falling");
			if (reasons == null) {
				return false;
			}
		}
		if (viewer.isOnFire()) {
			ok = reason(reasons, "burning");
			if (reasons == null) {
				return false;
			}
		}
		if (viewer.isInWater() || viewer.isInLava() || viewer.isInLiquid()) {
			ok = reason(reasons, "fluid");
			if (reasons == null) {
				return false;
			}
		}
		if (viewer.getAirSupply() < viewer.getMaxAirSupply() * 0.6D) {
			ok = reason(reasons, "air");
			if (reasons == null) {
				return false;
			}
		}
		DamageSource last = viewer.getLastDamageSource(HURT_TICKS);
		if (last != null && !last.is(Tribulation.DAMAGE) && !last.is(Deviation.DAMAGE)) {
			ok = reason(reasons, "hurt");
			if (reasons == null) {
				return false;
			}
		}
		ServerLevel level = viewer.level();
		AABB box = viewer.getBoundingBox().inflate(SAFE_MOB_RANGE);
		if (!level.getEntitiesOfClass(Mob.class, box, mob -> mob.isAlive() && mob.getTarget() == viewer).isEmpty()) {
			ok = reason(reasons, "mob");
			if (reasons == null) {
				return false;
			}
		}
		double near = SAFE_PLAYER_RANGE * SAFE_PLAYER_RANGE;
		LivingEntity hurtBy = viewer.getLastHurtByMob();
		boolean recentlyHurt = viewer.tickCount - viewer.getLastHurtByMobTimestamp() < MATE_HURT_TICKS;
		for (ServerPlayer other : level.players()) {
			if (other == viewer || other.isSpectator() || other.distanceToSqr(viewer) > near) {
				continue;
			}
			boolean friend = Sects.areSectMates(viewer, other) && !(recentlyHurt && hurtBy == other);
			if (!friend) {
				ok = reason(reasons, "player");
				if (reasons == null) {
					return false;
				}
				break;
			}
		}
		return ok;
	}

	/** RESULT_SAFE: no enemy within 24 blocks, not burning, not falling, not in fluid. */
	public static boolean resultSafe(ServerPlayer viewer) {
		if (viewer.isOnFire() || viewer.fallDistance > 2.0D || viewer.isInWater() || viewer.isInLava() || viewer.isInLiquid()) {
			return false;
		}
		AABB box = viewer.getBoundingBox().inflate(RESULT_ENEMY_RANGE);
		return viewer.level().getEntitiesOfClass(LivingEntity.class, box, entity -> entity instanceof Enemy && entity.isAlive()).isEmpty();
	}

	/** Average tick above {@code kehancuranMsptPause} (the 6c {@code TechniqueRunner.lowLoad()} is package-private). */
	public static boolean lowLoad(@Nullable MinecraftServer server) {
		return server != null && server.getAverageTickTimeNanos() / 1_000_000.0D > KultivatorConfig.get().kehancuranMsptPause;
	}

	/** The last cues sent, oldest first. */
	public static List<LogEntry> log() {
		return new ArrayList<>(LOG);
	}

	/** Forgets the rate windows and the log (server stop). */
	static void clear() {
		RATE.clear();
		LOG.clear();
	}

	// ================================================================== internals

	private static boolean reason(@Nullable List<String> reasons, String key) {
		if (reasons != null) {
			reasons.add(key);
		}
		return false;
	}

	/** PRE beats are sent ahead and are the first to be dropped. */
	static boolean isPre(int moment, int beat) {
		return moment == EpicIds.TRIBULATION && beat == EpicIds.TRIB_PRE || moment == EpicIds.CALAMITY && beat == EpicIds.CAL_PRE;
	}

	/** Scenes with sky-scale layers go out with {@code epicRange} (23.4.4). */
	private static boolean skyScale(int moment, int beat, int s, int b) {
		return switch (moment) {
			case EpicIds.TRIBULATION -> s >= 4;
			case EpicIds.ASCENSION, EpicIds.CALAMITY -> true;
			case EpicIds.KEHANCURAN -> beat == EpicIds.KEH_WARNING || beat == EpicIds.KEH_IMPACT;
			case EpicIds.SECT -> beat == EpicIds.SECT_TEMPLATE && b == 9;
			default -> false;
		};
	}

	/** How far the viewer may be from someone else's scene and still be its protagonist. */
	private static double viewerRange(int moment, @Nullable Entity subject, ServerPlayer viewer) {
		boolean own = subject == null || subject == viewer;
		return switch (moment) {
			case EpicIds.DISCIPLE_BT, EpicIds.SURPASS -> 32.0D;
			case EpicIds.CALAMITY -> 64.0D;
			case EpicIds.CHEAT_DEATH, EpicIds.EXTRA -> own ? Double.MAX_VALUE : 64.0D;
			case EpicIds.RECRUIT, EpicIds.SECT, EpicIds.TOURNAMENT, EpicIds.KEHANCURAN -> Math.max(128.0D, KultivatorConfig.get().epicRange);
			default -> Double.MAX_VALUE;
		};
	}

	/** How far the viewer may be from the scene for a camera shot (the shot gates of the storyboards). */
	private static double cameraRange(int moment, @Nullable Entity subject, ServerPlayer viewer) {
		boolean own = subject == null || subject == viewer;
		return switch (moment) {
			case EpicIds.DISCIPLE_BT, EpicIds.SURPASS, EpicIds.RECRUIT, EpicIds.TOURNAMENT -> 32.0D;
			case EpicIds.CALAMITY, EpicIds.KEHANCURAN -> 64.0D;
			case EpicIds.BOSS -> own ? Double.MAX_VALUE : 24.0D;
			case EpicIds.SECT -> 128.0D;
			case EpicIds.EXTRA -> own ? Double.MAX_VALUE : 32.0D;
			default -> Double.MAX_VALUE;
		};
	}

	private static long rateKey(@Nullable Entity subject, @Nullable ServerPlayer viewer, Vec3 pos) {
		if (subject != null) {
			return subject.getId();
		}
		if (viewer != null) {
			return viewer.getId();
		}
		return 0x4000_0000_0000_0000L ^ ((long) Math.floor(pos.x) * 31L + (long) Math.floor(pos.z)) * 0x9E3779B97F4A7C15L;
	}

	private static void prune(long now) {
		for (Iterator<ArrayDeque<Long>> it = RATE.values().iterator(); it.hasNext(); ) {
			ArrayDeque<Long> window = it.next();
			if (window.isEmpty() || now - window.peekLast() >= RATE_WINDOW) {
				it.remove();
			}
		}
	}

	/** {@code mix(subject UUID, at)}: every client builds the same shapes. */
	static int seed(@Nullable UUID id, Vec3 pos, long at) {
		long h = id != null ? id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17)
				: Double.doubleToLongBits(pos.x) * 31L ^ Double.doubleToLongBits(pos.z);
		h ^= at * 0x9E3779B97F4A7C15L;
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (int) (h ^ h >>> 32);
	}

	private static void log(LogEntry entry) {
		LOG.addLast(entry);
		while (LOG.size() > LOG_MAX) {
			LOG.pollFirst();
		}
	}

	private EpicMoments() {
	}
}
