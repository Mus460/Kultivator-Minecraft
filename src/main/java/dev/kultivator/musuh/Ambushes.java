package dev.kultivator.musuh;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.alam.Alam;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.Sparring;
import dev.kultivator.sect.hall.HallData;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.trial.HeartDemons;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Penyergapan, road ambushes (DESIGN 25.4.3; Sumber RMJ ch. 388 and 402). One roll per eligible player every 6,000 ticks
 * (staggered by player), chance {@code (0.06 + 0.01 × realm) × scale} capped at 15%, a day of cooldown afterwards. Three
 * seconds of killing intent come first, then a band of 1-3 masked rogues steps out 12-18 blocks ahead. It ends when the
 * band is beaten (or driven off), or when the player stays more than 96 blocks away for 30 s.
 */
final class Ambushes {

	public static final int ROLL_TICKS = 6000;
	public static final double BASE_CHANCE = 0.06D;
	public static final double CHANCE_PER_REALM = 0.01D;
	public static final double CHANCE_CAP = 0.15D;
	public static final long COOLDOWN_TICKS = 24000L;
	public static final double HALL_DISTANCE = 160.0D;
	public static final double LEAVE_RANGE = 96.0D;
	public static final int LEAVE_TICKS = 600;
	public static final int TELEGRAPH_TICKS = 60;
	public static final double TIER3_LEADER = 0.15D;

	private record Pending(long spawnAt, int[] tiers, int realm) {
	}

	private static final class Live {
		final RogueCultivators.Band band;
		long farSince = -1L;

		Live(RogueCultivators.Band band) {
			this.band = band;
		}
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	private static final Map<UUID, Live> LIVE = new HashMap<>();

	static void init() {
		ServerPlayerEvents.LEAVE.register(player -> {
			PENDING.remove(player.getUUID());
			Live live = LIVE.remove(player.getUUID());
			if (live != null) {
				live.band.dismiss();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING.clear();
			LIVE.clear();
		});
	}

	static void tick(MinecraftServer server, long tick) {
		KultivatorConfig config = KultivatorConfig.get();
		if (config.rogueAmbushes && config.rogueAmbushChanceScale > 0.0D) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if ((tick + (player.getUUID().hashCode() & 0x7FFFFFFF)) % ROLL_TICKS == 0L) {
					roll(player, false);
				}
			}
		}
		if (!PENDING.isEmpty()) {
			Iterator<Map.Entry<UUID, Pending>> iterator = PENDING.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<UUID, Pending> entry = iterator.next();
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player == null) {
					iterator.remove();
				} else if (player.level().getGameTime() >= entry.getValue().spawnAt()) {
					iterator.remove();
					strike(player, entry.getValue());
				}
			}
		}
		if (tick % 20L == 0L && !LIVE.isEmpty()) {
			for (Map.Entry<UUID, Live> entry : List.copyOf(LIVE.entrySet())) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				Live live = entry.getValue();
				if (live.band.done) {
					LIVE.remove(entry.getKey());
					continue;
				}
				if (player == null || nearest(player, live.band) > LEAVE_RANGE) {
					long now = server.overworld().getGameTime();
					if (live.farSince < 0L) {
						live.farSince = now;
					} else if (now - live.farSince >= LEAVE_TICKS) {
						live.band.dismiss();
					}
				} else {
					live.farSince = -1L;
				}
			}
		}
	}

	private static double nearest(ServerPlayer player, RogueCultivators.Band band) {
		double best = Double.MAX_VALUE;
		for (RogueCultivatorEntity mob : band.mobs) {
			if (!mob.isRemoved() && mob.level() == player.level()) {
				best = Math.min(best, mob.distanceTo(player));
			}
		}
		return best;
	}

	/**
	 * Rolls an ambush for the player ({@code forced}: no chance, no cooldown, the safety checks stay). Returns null when one
	 * is coming, otherwise the refusal (a translation key).
	 */
	static @Nullable String roll(ServerPlayer player, boolean forced) {
		String refusal = refusal(player);
		if (refusal != null) {
			return refusal;
		}
		CultivationData data = Cultivators.get(player);
		Realm realm = data.realm();
		ServerLevel level = player.level();
		long now = level.getGameTime();
		CompoundTag dunia = Musuh.dunia(data);
		if (!forced) {
			if (now < dunia.getLongOr("ambush_cd", 0L)) {
				return "kultivator.musuh.ambush.refuse.cooldown";
			}
			double chance = Math.min(CHANCE_CAP, (BASE_CHANCE + CHANCE_PER_REALM * realm.ordinal()) * KultivatorConfig.get().rogueAmbushChanceScale);
			if (level.getRandom().nextDouble() >= chance) {
				return "kultivator.musuh.ambush.refuse.chance";
			}
		}
		int[] tiers = party(realm, level.getRandom());
		dunia.putLong("ambush_cd", now + COOLDOWN_TICKS);
		Cultivators.dirty(player);
		PENDING.put(player.getUUID(), new Pending(now + TELEGRAPH_TICKS, tiers, realm.ordinal()));
		// Killing intent: "Don't hide! Reveal yourself!" (RMJ ch. 388).
		Msg.actionBar(player, Component.translatable("kultivator.musuh.ambush.alert", tiers.length).withStyle(ChatFormatting.DARK_RED));
		Fx.sendTo(player, player.position(), player.position(), FxType.MUSUH, MusuhFxIds.AMBUSH_ALERT, tiers.length, 0.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0F, 0.8F);
		return null;
	}

	/** The party of DESIGN 25.4.3 by the player's realm. */
	static int[] party(Realm realm, RandomSource random) {
		int r = realm.ordinal();
		if (r <= Realm.FOUNDATION.ordinal()) {
			return random.nextBoolean() ? new int[]{1} : new int[]{1, 1};
		}
		if (r <= Realm.NASCENT_SOUL.ordinal()) {
			return new int[]{2, 1, 1};
		}
		if (r <= Realm.DAO_UNION.ordinal()) {
			return random.nextDouble() < TIER3_LEADER ? new int[]{3, 2, 2} : new int[]{2, 2, 1};
		}
		return new int[]{3, 2, 2};
	}

	/** Why no ambush may come now (a translation key), or null. */
	static @Nullable String refusal(ServerPlayer player) {
		if (player.isCreative() || player.isSpectator()) {
			return "kultivator.musuh.ambush.refuse.mode";
		}
		ServerLevel level = player.level();
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return "kultivator.musuh.ambush.refuse.peaceful";
		}
		if (!level.dimension().equals(Level.OVERWORLD) && !level.dimension().equals(Level.NETHER)) {
			return "kultivator.musuh.ambush.refuse.dimension";
		}
		if (!Cultivators.get(player).realm().atLeast(Realm.QI_REFINING)) {
			return "kultivator.musuh.ambush.refuse.mortal";
		}
		if (PENDING.containsKey(player.getUUID()) || LIVE.containsKey(player.getUUID())) {
			return "kultivator.musuh.ambush.refuse.busy";
		}
		if (!player.onGround() || player.getAbilities().flying || player.isFallFlying() || player.isPassenger() || player.isInWater()) {
			return "kultivator.musuh.ambush.refuse.moving";
		}
		if (Alam.inRun(player) || Alam.isRealmLevel(level) || HeartDemons.inTrial(player) || DuelRules.inDuel(player) || Sparring.inDuel(player)
				|| Tribulation.isActive(player) || Breakthrough.isBusy(player)) {
			return "kultivator.musuh.ambush.refuse.busy";
		}
		Vec3 pos = player.position();
		Halls.HallView own = Halls.hallOf(player.getUUID());
		if (own != null && own.dim().equals(level.dimension()) && Vec3.atCenterOf(own.pos()).distanceToSqr(pos) <= HALL_DISTANCE * HALL_DISTANCE) {
			return "kultivator.musuh.ambush.refuse.hall";
		}
		for (UUID owner : HallData.get(level.getServer()).owners()) {
			if (Halls.inTerritory(owner, level, pos)) {
				return "kultivator.musuh.ambush.refuse.hall";
			}
		}
		if (level.isVillage(player.blockPosition())) {
			return "kultivator.musuh.ambush.refuse.village";
		}
		return null;
	}

	/** The band steps out 12-18 blocks ahead, in a loose arc, and goes for the player. */
	private static void strike(ServerPlayer player, Pending pending) {
		if (!player.isAlive() || player.isCreative() || player.isSpectator() || player.level().getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
		Vec3 centre = player.position().add(flat.scale(12.0D + random.nextDouble() * 6.0D));
		UUID id = player.getUUID();
		int size = pending.tiers().length;
		MinecraftServer server = level.getServer();
		RogueCultivators.Band band = RogueCultivators.band(level, centre, pending.tiers(), pending.realm(), RogueCultivators.ROLE_AMBUSH, null, -1,
				RogueCultivators.VARIANT_ROGUE, cleared -> finished(server, id, cleared, size));
		if (band == null) {
			Msg.actionBar(player, Component.translatable("kultivator.musuh.ambush.fizzled").withStyle(ChatFormatting.GRAY));
			return;
		}
		for (RogueCultivatorEntity mob : band.mobs) {
			mob.setTarget(player);
		}
		LIVE.put(id, new Live(band));
		Msg.chat(player, Component.translatable("kultivator.musuh.ambush.flavour", band.mobs.size()).withStyle(ChatFormatting.RED));
	}

	private static void finished(MinecraftServer server, UUID id, boolean cleared, int size) {
		LIVE.remove(id);
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player == null) {
			return;
		}
		Musuh.logEncounter(player, Musuh.KIND_AMBUSH, cleared, size);
		if (cleared) {
			Msg.chat(player, Component.translatable("kultivator.musuh.ambush.won").withStyle(ChatFormatting.GOLD));
			if (size >= 3) {
				Advancements.grant(player, "ambush_survivor");
			}
		} else {
			Msg.chat(player, Component.translatable("kultivator.musuh.ambush.gone").withStyle(ChatFormatting.GRAY));
		}
	}

	/** The ambush cooldown left in ticks (0 when ready). */
	static long cooldownLeft(ServerPlayer player) {
		long until = Musuh.dunia(Cultivators.get(player)).getLongOr("ambush_cd", 0L);
		return Math.max(0L, until - player.level().getGameTime());
	}

	/** True while an ambush is coming or under way for the player. */
	static boolean busy(UUID player) {
		return PENDING.containsKey(player) || LIVE.containsKey(player);
	}

	private Ambushes() {
	}
}
