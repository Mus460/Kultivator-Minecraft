package dev.kultivator.dunia.tempat.jalan;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.tempat.TempatConfig;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highway bandits (DESIGN 38.C.6.3, {@code pos/kemah_begal}): a camp 24-40 blocks off 15 % of the roads beyond 1000
 * blocks. When a survival player comes within {@link #JARAK} blocks, is not busy ({@code Sibuk.why}) and the shared
 * daily enemy pressure allows it ({@code Sibuk.enemyRefusal}), the band of rogue cultivators wakes at the camp
 * ({@code RogueCultivators.spawnBand}, role CAMP, homed on the camp): stronger with distance from the world centre.
 * At most once per in-game day per camp; one camp band at a time per player. The band never breaks a block and the
 * camp is not protected ground. Guild missions {@code begal} (38EK) point here through the published site.
 */
public final class KemahBegal {

	public static final int JARAK = 40;

	private record Aktif(BlockPos camp, long day) {
	}

	private static final Map<Long, Aktif> AKTIF = new ConcurrentHashMap<>();
	private static final Map<Long, Long> HARI = new ConcurrentHashMap<>();

	/** Registers the shared enemy-pressure entry ({@code tempat.begal}): a live band near the player. */
	static void init() {
		Sibuk.registerEnemy("tempat.begal", player -> {
			for (Aktif a : AKTIF.values()) {
				if (a.camp().distSqr(player.blockPosition()) < 96.0D * 96.0D) {
					return true;
				}
			}
			return false;
		});
	}

	static void reset() {
		AKTIF.clear();
		HARI.clear();
	}

	static void dekat(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (!TempatConfig.get().aktif || !TempatConfig.JalanConfig.get().begal) {
			return;
		}
		long site = anchor.siteOf("pos/kemah_begal");
		long day = Math.floorDiv(level.getServer().overworld().getOverworldClockTime(), 24000L);
		if (AKTIF.containsKey(site) || HARI.getOrDefault(site, Long.MIN_VALUE) == day) {
			return;
		}
		BlockPos camp = anchor.blockPosition();
		for (ServerPlayer player : within) {
			if (player.isCreative() || player.isSpectator() || player.distanceToSqr(Vec3.atCenterOf(camp)) > (double) JARAK * JARAK) {
				continue;
			}
			if (Sibuk.why(player) != null || Sibuk.enemyRefusal(player) != null) {
				continue;
			}
			double ring = Math.hypot(camp.getX(), camp.getZ());
			int[] tiers = ring < 3000.0D ? new int[]{1, 1, 2} : ring < 8000.0D ? new int[]{1, 2, 2, 3} : new int[]{2, 2, 3, 3};
			int realm = ring < 3000.0D ? 2 : ring < 8000.0D ? 3 : 4;
			AKTIF.put(site, new Aktif(camp, day));
			int spawned = RogueCultivators.spawnBand(level, Vec3.atCenterOf(camp), tiers, realm, RogueCultivators.ROLE_CAMP, camp,
					cleared -> {
						AKTIF.remove(site);
						HARI.put(site, day);
					});
			if (spawned <= 0) {
				AKTIF.remove(site);
				return;
			}
			HARI.put(site, day);
			Sibuk.enemyTaken(player, "begal");
			Msg.title(player, Component.translatable("kultivator.jalan.begal.judul").withStyle(ChatFormatting.DARK_RED),
					Component.translatable("kultivator.jalan.begal.sub", spawned).withStyle(ChatFormatting.GRAY), 10, 50, 15);
			return;
		}
	}

	private KemahBegal() {
	}
}
