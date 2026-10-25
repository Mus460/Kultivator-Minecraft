package dev.kultivator.sekte.langit.gerak;

import dev.kultivator.formasi.effect.b.JaringAwanEffect;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.sekte.langit.api.SkyHallView;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Terjun Awan, the Cloud Dive of DESIGN 37.5.6 (b): while the Jaring Awan formation of a sky hall runs (T5), a member who
 * leaves the island's rim holding shift (it stood inside the sky volume less than 2 s ago) is not caught by the base net
 * but glides down to the ground under Slow Falling, a thin cloud trail behind, immune to fall damage until it has stood
 * on the ground for 2 s. A quick and beautiful way down. Refused (so the net catches as usual) while the player is busy
 * ({@code Sibuk.why}: a duel, a tribulation, a trial) or when {@code terjunAwan} / {@code aktif} is off. No block changes.
 */
public final class TerjunAwan {

	/** Ticks since the last standing inside the volume within which shift starts a dive. */
	public static final long START_WINDOW = 40L;
	public static final int GLIDE_TICKS = 60;
	/** Fall damage stays off this long after landing (ticks). */
	public static final long LAND_GRACE = 40L;
	/** A dive never lasts longer than this (ticks): then the net takes over again. */
	public static final long MAX_TICKS = 20L * 90L;

	/** One dive: the hall it left, when it started, when it landed (or -1). */
	private record Dive(UUID owner, long start, long landed) {
	}

	private static final Map<UUID, Dive> DIVES = new HashMap<>();

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(TerjunAwan::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(source.is(DamageTypeTags.IS_FALL)
				&& DIVES.containsKey(entity.getUUID())));
		ServerPlayerEvents.LEAVE.register(player -> DIVES.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DIVES.clear());
	}

	/** True while {@code id} dives (not caught by the net, no fall damage). */
	public static boolean diving(UUID id) {
		Dive dive = DIVES.get(id);
		return dive != null && dive.landed() < 0L;
	}

	/**
	 * From {@link LangitJatuh}: {@code body} is in the air over {@code hall}. Starts a dive when every rule allows it and
	 * returns true; false leaves the body to the net.
	 */
	static boolean tryStart(ServerLevel level, SkyHallView hall, LivingEntity body, double vy) {
		if (!(body instanceof ServerPlayer player) || !player.isShiftKeyDown() || vy > 0.05D) {
			return false;
		}
		LangitConfig cfg = LangitConfig.get();
		if (!cfg.terjunAwan || !LangitConfig.on() || hall.tethered()) {
			return false;
		}
		long now = level.getGameTime();
		if (now - LangitJatuh.lastGround(player.getUUID()) > START_WINDOW) {
			return false;
		}
		if (!LangitGerak.member(hall.owner(), player) || !JaringAwanEffect.active(hall.owner(), now)) {
			return false;
		}
		String busy = Sibuk.why(player);
		if (busy != null) {
			Msg.actionBar(player, Component.translatable("kultivator.sekte_langit.jatuh.terjun_sibuk", Sibuk.text(busy)).withColor(0xFFB070));
			return false;
		}
		DIVES.put(player.getUUID(), new Dive(hall.owner(), now, -1L));
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, GLIDE_TICKS, 0, false, false, true));
		player.resetFallDistance();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.35F, 1.4F);
		Msg.actionBar(player, Component.translatable("kultivator.sekte_langit.jatuh.terjun").withColor(0xBFE6FF));
		LangitGerak.fx(level, LangitIds.FX_TANGKAP, player.blockPosition(), player.blockPosition().below(4));
		return true;
	}

	/** {@code uji langit_sekte jatuh terjun}: starts the dive as if shift were held (all other rules apply). */
	static boolean force(ServerLevel level, SkyHallView hall, ServerPlayer player) {
		if (diving(player.getUUID())) {
			return true;
		}
		if (!LangitConfig.get().terjunAwan || !LangitConfig.on() || hall.tethered() || !LangitGerak.member(hall.owner(), player)
				|| !JaringAwanEffect.active(hall.owner(), level.getGameTime()) || Sibuk.why(player) != null) {
			return false;
		}
		DIVES.put(player.getUUID(), new Dive(hall.owner(), level.getGameTime(), -1L));
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, GLIDE_TICKS, 0, false, false, true));
		player.resetFallDistance();
		Msg.actionBar(player, Component.translatable("kultivator.sekte_langit.jatuh.terjun").withColor(0xBFE6FF));
		return true;
	}

	/** From {@link LangitJatuh}: {@code body} stands on the ground (inside or outside the zone). */
	static void landed(LivingEntity body) {
		Dive dive = DIVES.get(body.getUUID());
		if (dive != null && dive.landed() < 0L && body instanceof ServerPlayer player) {
			land(player, dive);
		}
	}

	/** The dive ends on the ground: grace for the fall damage, a soft landing sound, the advancement. */
	private static void land(ServerPlayer player, Dive dive) {
		ServerLevel level = player.level();
		DIVES.put(player.getUUID(), new Dive(dive.owner(), dive.start(), level.getGameTime()));
		player.resetFallDistance();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 0.5F, 1.2F);
		Msg.actionBar(player, Component.translatable("kultivator.sekte_langit.jatuh.terjun_mendarat").withColor(0xBFE6FF));
		Advancements.grant(player, LangitIds.ADV + "terjun_awan");
	}

	private static void tick(MinecraftServer server) {
		if (DIVES.isEmpty() || server.getTickCount() % 5 != 0) {
			return;
		}
		Iterator<Map.Entry<UUID, Dive>> it = DIVES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Dive> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Dive dive = entry.getValue();
			if (player == null || !player.isAlive()) {
				it.remove();
				continue;
			}
			ServerLevel level = player.level();
			long now = level.getGameTime();
			if (dive.landed() >= 0L) {
				if (now - dive.landed() > LAND_GRACE) {
					it.remove();
				}
				continue;
			}
			if (player.onGround() || player.isInWater() || player.isPassenger()) {
				// the zone of the net may be behind already: land here
				entry.setValue(new Dive(dive.owner(), dive.start(), now));
				land(player, dive);
				continue;
			}
			if (now - dive.start() > MAX_TICKS) {
				// far too long in the air (a void, a broken world): hand it back to the net
				it.remove();
				continue;
			}
			MobEffectInstance slow = player.getEffect(MobEffects.SLOW_FALLING);
			if (slow == null || slow.getDuration() < GLIDE_TICKS / 2) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, GLIDE_TICKS, 0, false, false, true));
			}
			player.resetFallDistance();
			// The server tick gates this loop (every 5): game time may never land on a multiple of 20 here.
			if (server.getTickCount() % 20 == 0) {
				Vec3 at = player.position();
				LangitGerak.fx(level, LangitIds.FX_TANGKAP, BlockPos.containing(at.add(0.0D, 1.0D, 0.0D)), BlockPos.containing(at));
			}
		}
	}

	private TerjunAwan() {
	}
}
