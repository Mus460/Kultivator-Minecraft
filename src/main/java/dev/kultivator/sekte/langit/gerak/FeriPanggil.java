package dev.kultivator.sekte.langit.gerak;

import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.langit.api.SkyHallView;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Calling the Cloud Ferry (DESIGN 37.5.5): ringing a bell within 3 blocks of a stop of a sky hall one belongs to
 * ({@code feri_atas} on the island, {@code feri_bawah} on the ground, or the stair foot {@code tangga_kaki}) calls the
 * hall's ferry to that stop; the bell still rings as usual (the callback passes). {@link #callNear} is the command's
 * entry ({@code /kultivasi langit feri}, 37G). One call per player every 2 s. It also listens to
 * {@code SekteBus.MISSION_SENT}: a mission team leaves on the ferry as a show ({@link FeriAwanEntity#misi}). No block changes.
 */
public final class FeriPanggil {

	/** The sky site tier that builds the ferry stop ({@code aw_t7_halte_feri}). */
	public static final int MIN_TIER = 7;
	public static final String TOP = "feri_atas";
	public static final String BOTTOM = "feri_bawah";
	public static final String STAIR_FOOT = "tangga_kaki";
	public static final double BELL_RANGE = 3.0D;
	private static final long COOLDOWN = 40L;

	private static final Map<UUID, Long> LAST = new HashMap<>();

	static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)
					|| !LangitConfig.on() || !LangitConfig.get().feri) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			if (!serverLevel.isLoaded(pos) || !serverLevel.getBlockState(pos).is(Blocks.BELL)) {
				return InteractionResult.PASS;
			}
			bell(serverLevel, serverPlayer, pos);
			return InteractionResult.PASS;
		});
		// 37.5.5 (37G): a team sent on an outside mission leaves on the ferry as a show (langit.feriMisi)
		SekteBus.MISSION_SENT.register((server, owner, missionId, team) -> {
			SkyHallView hall = LangitConfig.on() && LangitConfig.get().feri && LangitConfig.get().feriMisi ? LangitGerak.api().skyHall(owner) : null;
			ServerLevel level = hall == null ? null : server.getLevel(hall.dim());
			if (level != null) {
				FeriAwanEntity.misi(level, owner, team);
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> LAST.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST.clear();
			FeriAwanEntity.clearAll();
		});
	}

	/** The ground stop of {@code owner}'s ferry: {@code feri_bawah}, else the stair foot; null without either. */
	static @Nullable BlockPos bottomStop(UUID owner) {
		BlockPos bottom = LangitGerak.anchor(owner, BOTTOM);
		return bottom != null ? bottom : LangitGerak.anchor(owner, STAIR_FOOT);
	}

	private static void bell(ServerLevel level, ServerPlayer player, BlockPos bell) {
		for (SkyHallView hall : LangitJatuh.halls(level)) {
			BlockPos top = LangitGerak.anchor(hall.owner(), TOP);
			BlockPos bottom = bottomStop(hall.owner());
			boolean atTop = near(top, bell);
			boolean atBottom = !atTop && (near(bottom, bell) || near(LangitGerak.anchor(hall.owner(), STAIR_FOOT), bell));
			if (!atTop && !atBottom) {
				continue;
			}
			if (!LangitGerak.member(hall.owner(), player)) {
				Msg.actionBar(player, Component.translatable("kultivator.sekte_langit.feri.bukan_anggota").withColor(0xFF9A7A));
				return;
			}
			call(level, player, hall.owner(), atBottom);
			return;
		}
	}

	/**
	 * {@code /kultivasi langit feri}: calls the ferry of the nearest sky hall the player belongs to, to the stop on the
	 * player's side (under the island: the ground stop). Null when called, else why not.
	 */
	public static @Nullable Component callNear(ServerPlayer player) {
		ServerLevel level = player.level();
		SkyHallView best = null;
		double bestDistance = Double.MAX_VALUE;
		for (SkyHallView hall : LangitJatuh.halls(level)) {
			if (!LangitGerak.member(hall.owner(), player)) {
				continue;
			}
			double distance = hall.horizontal(player.position());
			if (distance < bestDistance) {
				bestDistance = distance;
				best = hall;
			}
		}
		if (best == null) {
			return Component.translatable("kultivator.sekte_langit.feri.bukan_langit");
		}
		boolean up = player.getY() < best.skyFloorY();
		return call(level, player, best.owner(), up);
	}

	private static @Nullable Component call(ServerLevel level, ServerPlayer player, UUID owner, boolean up) {
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last >= 0L && now - last < COOLDOWN) {
			return null;
		}
		LAST.put(player.getUUID(), now);
		Component why = FeriAwanEntity.call(level, owner, up);
		if (why != null) {
			Msg.actionBar(player, why.copy().withColor(0xFF9A7A));
		} else {
			Msg.actionBar(player, Component.translatable(up ? "kultivator.sekte_langit.feri.datang_bawah" : "kultivator.sekte_langit.feri.datang_atas")
					.withColor(0xBFE6FF));
		}
		return why;
	}

	private static boolean near(@Nullable BlockPos anchor, BlockPos bell) {
		if (anchor == null) {
			return false;
		}
		double dx = anchor.getX() - bell.getX();
		double dy = anchor.getY() - bell.getY();
		double dz = anchor.getZ() - bell.getZ();
		return dx * dx + dz * dz <= BELL_RANGE * BELL_RANGE && Math.abs(dy) <= 4.0D;
	}

	private FeriPanggil() {
	}
}
