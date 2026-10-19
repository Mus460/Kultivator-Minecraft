package dev.kultivator.abadi;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Cultivators;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.musuh.LootPouches;
import dev.kultivator.musuh.MusuhFxIds;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.sect.hall.HallData;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pengejaran, the rival's pursuit (DESIGN 26.8.3; inspired by RMJIR ch. 567 "Pursuit" and ch. 615): a rival whose hidden
 * limit was at least 90% of the final price of a lot the player won may send a band ({@code auctionPursuitChance} per
 * offended rival, at most one per session). It comes 60-180 s after the session, the next time the player is in Alam Abadi or
 * the Overworld outside any hall territory, active, and not busy: the 19.3.15 danger alert 3 s before, then a band of rogue
 * cultivators of tiers {4, 2, 2} steps out 12-18 blocks ahead (the leader's boss bar is the Patriarch bar of
 * {@code RogueCultivators}), all tagged {@code abadi_foe} with health scaled to the player's effective realm. Win: a demonic
 * loot pouch, 5-15 Batu and {@code pursuit_survived}; losing follows the normal death rules. The due time lives in
 * {@code extra.abadi.auction.pursuit_due}, so it survives a relog.
 */
final class Pursuit {

	public static final int ALERT_TICKS = 60;
	public static final long ACTIVE_TICKS = 1200L;
	public static final int[] BAND = {4, 2, 2};
	public static final int BAND_REALM = 8;
	public static final double BAND_HEALTH_DIVISOR = 3.8D;
	public static final String TAG = "kultivator.abadi_pursuit";

	private static final Identifier HEALTH_ID = Kultivator.id("abadi_pursuit");

	private record Moved(Vec3 pos, long at) {
	}

	private static final Map<UUID, Long> ALERTED = new HashMap<>();
	private static final Map<UUID, Moved> MOVED = new HashMap<>();
	private static final Map<UUID, Integer> LIVE = new HashMap<>();

	static void init() {
		ServerPlayerEvents.LEAVE.register(player -> {
			ALERTED.remove(player.getUUID());
			MOVED.remove(player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ALERTED.clear();
			MOVED.clear();
			LIVE.clear();
		});
	}

	/** Schedules a pursuit by {@code rival} for the player at overworld time {@code at}. */
	static void schedule(ServerPlayer player, int rival, long at) {
		CompoundTag auction = Abadi.section(player, "auction");
		auction.putLong("pursuit_due", at);
		auction.putInt("pursuit_rival", rival);
		Cultivators.dirty(player);
	}

	private static long due(ServerPlayer player) {
		return Cultivators.get(player).extra().getCompoundOrEmpty(Abadi.EXTRA_KEY).getCompoundOrEmpty("auction").getLongOr("pursuit_due", 0L);
	}

	private static void clearDue(ServerPlayer player) {
		CompoundTag auction = Abadi.section(player, "auction");
		auction.remove("pursuit_due");
		auction.remove("pursuit_rival");
		Cultivators.dirty(player);
	}

	static void tick(MinecraftServer server, long ticks) {
		if (ticks % 20L != 0L) {
			return;
		}
		long now = Abadi.now(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Moved moved = MOVED.get(player.getUUID());
			if (moved == null || moved.pos().distanceToSqr(player.position()) > 0.25D) {
				MOVED.put(player.getUUID(), new Moved(player.position(), now));
			}
			long due = due(player);
			if (due <= 0L || now < due || LIVE.containsKey(player.getUUID())) {
				continue;
			}
			Long alertAt = ALERTED.get(player.getUUID());
			if (alertAt == null) {
				if (refusal(player, now) == null) {
					alert(player);
					ALERTED.put(player.getUUID(), now);
				}
			} else if (now - alertAt >= ALERT_TICKS) {
				ALERTED.remove(player.getUUID());
				if (refusal(player, now) == null) {
					spawn(player);
				}
			}
		}
	}

	/** Why the pursuit cannot strike now (a translation key), or null. */
	static @Nullable String refusal(ServerPlayer player, long now) {
		if (player.isCreative() || player.isSpectator() || !player.isAlive() || player.level().getDifficulty() == Difficulty.PEACEFUL) {
			return "kultivator.abadi.pursuit.refuse.mode";
		}
		ServerLevel level = player.level();
		if (!Abadi.isImmortalLevel(level) && !level.dimension().equals(Level.OVERWORLD)) {
			return "kultivator.abadi.pursuit.refuse.place";
		}
		Moved moved = MOVED.get(player.getUUID());
		if (moved == null || now - moved.at() > ACTIVE_TICKS) {
			return "kultivator.abadi.pursuit.refuse.idle";
		}
		if (Abadi.busyReason(player, false) != null) {
			return "kultivator.abadi.pursuit.refuse.busy";
		}
		for (UUID owner : HallData.get(level.getServer()).owners()) {
			if (Halls.inTerritory(owner, level, player.position())) {
				return "kultivator.abadi.pursuit.refuse.hall";
			}
		}
		return null;
	}

	private static void alert(ServerPlayer player) {
		Msg.actionBar(player, Component.translatable("kultivator.abadi.pursuit.alert").withStyle(ChatFormatting.DARK_RED));
		Fx.sendTo(player, player.position(), player.position(), FxType.MUSUH, MusuhFxIds.AMBUSH_ALERT, BAND.length, 0.0F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0F, 0.8F);
	}

	/** The band steps out 12-18 blocks ahead. True when it came. */
	static boolean spawn(ServerPlayer player) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		int rival = Cultivators.get(player).extra().getCompoundOrEmpty(Abadi.EXTRA_KEY).getCompoundOrEmpty("auction").getIntOr("pursuit_rival", 0);
		clearDue(player);
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
		Vec3 centre = player.position().add(flat.scale(12.0D + random.nextDouble() * 6.0D));
		UUID id = player.getUUID();
		MinecraftServer server = level.getServer();
		int count = RogueCultivators.spawnBand(level, centre, BAND, BAND_REALM, RogueCultivators.ROLE_QUEST, null,
				cleared -> finished(server, id, cleared));
		if (count <= 0) {
			Msg.actionBar(player, Component.translatable("kultivator.abadi.pursuit.fizzled").withStyle(ChatFormatting.GRAY));
			return false;
		}
		LIVE.put(id, rival);
		double factor = (1.0D + 0.35D * Abadi.effectiveRealm(player)) / BAND_HEALTH_DIVISOR;
		List<Mob> band = level.getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(10.0D),
				mob -> RogueCultivators.isRogue(mob) && mob.tickCount <= 1 && !mob.entityTags().contains(TAG));
		for (Mob mob : band) {
			mob.addTag(TAG);
			mob.addTag(TechniquePower.TAG_ABADI_FOE);
			AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
			if (health != null && Math.abs(factor - 1.0D) > 1.0E-6D) {
				health.addOrReplacePermanentModifier(new AttributeModifier(HEALTH_ID, factor - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				mob.setHealth(mob.getMaxHealth());
			}
			mob.setTarget(player);
		}
		Msg.chat(player, Component.translatable("kultivator.abadi.pursuit.flavour", Component.translatable("kultivator.abadi.rival." + rival))
				.withStyle(ChatFormatting.RED));
		return true;
	}

	private static void finished(MinecraftServer server, UUID id, boolean cleared) {
		LIVE.remove(id);
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player == null) {
			return;
		}
		if (cleared) {
			RandomSource random = player.getRandom();
			Abadi.give(player, LootPouches.demonic(random.nextInt(8), BAND_REALM));
			Abadi.giveStones(player, 5 + random.nextInt(11));
			Advancements.grant(player, "pursuit_survived");
			Msg.chat(player, Component.translatable("kultivator.abadi.pursuit.won").withStyle(ChatFormatting.GOLD));
		} else {
			Msg.chat(player, Component.translatable("kultivator.abadi.pursuit.gone").withStyle(ChatFormatting.GRAY));
		}
	}

	/** {@code uji lelang kejar}: the pursuit now (the alert, then the band 3 s later; the safety checks stay). */
	static @Nullable String testNow(ServerPlayer player) {
		long now = Abadi.now(player.level().getServer());
		MOVED.put(player.getUUID(), new Moved(player.position(), now));
		String why = refusal(player, now);
		if (why != null) {
			return why;
		}
		schedule(player, player.getRandom().nextInt(Auction.RIVALS), now);
		return null;
	}

	private Pursuit() {
	}
}
