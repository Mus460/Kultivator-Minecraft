package dev.kultivator.sekte.langit.main;

import dev.kultivator.cultivation.Progression;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.sekte.langit.api.LangitPayloads;
import dev.kultivator.sekte.langit.api.SkyHallView;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A tribulation seen from above (DESIGN 37.7.3). While a player's tribulation runs, every player standing on a sky island
 * within {@link #RANGE} blocks (horizontal) and at least {@link #BELOW} blocks above it gets a
 * {@link LangitIds#FX_SENGSARA_BAWAH} effect at every strike (37V draws the dark vortex in the cloud sea). A member who
 * watches it ({@link #LOOK_PITCH}° or more downward, toward the point) for {@link #WATCH_SECONDS} seconds gains
 * {@link #XP_SHARE} of their stage's XP (Saksi Kesengsaraan): once per tribulation, at most {@link #PER_DAY} per world
 * day; rim-guard disciples ({@code ACT_JAGA_TEPI}) on that island get the same share once.
 * <p>
 * Strike factors (pure, as the contract asks): {@code sengsaraLebihKeras} makes a tribulation on one's own standing sky
 * island strike × {@link #CLOSER}; three Heaven Court refusals in a row make the owner's next tribulation strike
 * × {@link #CLOSER} (the count resets when that tribulation ends). Only detects player tribulations.
 */
public final class LangitSaksi {

	public static final double RANGE = 256.0D;
	public static final double BELOW = 40.0D;
	public static final float LOOK_PITCH = 30.0F;
	public static final int WATCH_SECONDS = 10;
	public static final double XP_SHARE = 0.02D;
	public static final int PER_DAY = 3;
	public static final double CLOSER = 1.10D;
	/** Refusals in a row before Heaven takes offence. */
	public static final int REFUSALS = 3;

	/** tribulant → where the omen gathered. */
	private static final Map<UUID, Vec3> LIVE = new HashMap<>();
	/** tribulant → witness → seconds watched (-1 when already rewarded). */
	private static final Map<UUID, Map<UUID, Integer>> WATCH = new HashMap<>();
	/** tribulant → disciples already rewarded. */
	private static final Map<UUID, Set<UUID>> GUARDS = new HashMap<>();

	static void register() {
		Tribulation.addListener(new Tribulation.Listener() {
			@Override
			public void onOmen(ServerPlayer p, Rank t) {
				LIVE.put(p.getUUID(), p.position());
				WATCH.remove(p.getUUID());
				GUARDS.remove(p.getUUID());
			}

			@Override
			public void onStrike(ServerPlayer p, Rank t, int index, int total) {
				strike(p);
			}

			@Override
			public void onEnd(ServerPlayer p, Rank t, boolean success, boolean test) {
				LIVE.remove(p.getUUID());
				WATCH.remove(p.getUUID());
				GUARDS.remove(p.getUUID());
				resetRefusals(p);
			}
		});
		Tribulation.addStrikeFactorProvider(LangitSaksi::factor);
	}

	static void clear() {
		LIVE.clear();
		WATCH.clear();
		GUARDS.clear();
	}

	// ================================================================== strike factor

	private static double factor(ServerPlayer p, Rank target, int strikeIndex, boolean last) {
		LangitConfig config = LangitConfig.get();
		if (!config.aktif) {
			return 1.0D;
		}
		double f = 1.0D;
		if (config.sengsaraLebihKeras) {
			SkyHallView view = LangitApi.get().at(p.level(), p.blockPosition());
			if (view != null && !view.tethered() && view.inIsland(p.blockPosition()) && p.getY() >= view.skyFloorY()) {
				f *= CLOSER;
			}
		}
		MinecraftServer server = p.level().getServer();
		if (server != null && config.utusanLangit) {
			CompoundTag hall = LangitData.get(server).peekHall(p.getUUID());
			if (hall != null && hall.getIntOr("tolak", 0) >= REFUSALS) {
				f *= CLOSER;
			}
		}
		return f;
	}

	private static void resetRefusals(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		if (server == null) {
			return;
		}
		LangitData data = LangitData.get(server);
		CompoundTag hall = data.peekHall(p.getUUID());
		if (hall != null && hall.getIntOr("tolak", 0) >= REFUSALS) {
			hall.putInt("tolak", 0);
			data.setDirty();
			Msg.chat(p, Component.translatable("kultivator.sekte_langit.utusan.tolak_lunas").withStyle(ChatFormatting.GRAY));
		}
	}

	// ================================================================== the view from above

	/** Each strike: the vortex effect to every island watcher of this tribulation. */
	private static void strike(ServerPlayer tribulant) {
		if (!LangitConfig.on()) {
			return;
		}
		Vec3 at = LIVE.getOrDefault(tribulant.getUUID(), tribulant.position());
		ServerLevel level = tribulant.level();
		BlockPos point = BlockPos.containing(at);
		for (ServerPlayer watcher : watchers(level, at)) {
			LangitPayloads.send(watcher, new LangitPayloads.Fx(LangitIds.FX_SENGSARA_BAWAH, point, watcher.blockPosition()));
		}
	}

	/** Players on a sky island above the point (in range, high enough), the tribulant excluded. */
	private static List<ServerPlayer> watchers(ServerLevel level, Vec3 at) {
		return level.getPlayers(player -> {
			double dx = player.getX() - at.x;
			double dz = player.getZ() - at.z;
			if (dx * dx + dz * dz > RANGE * RANGE || player.getY() - at.y < BELOW) {
				return false;
			}
			SkyHallView view = LangitApi.get().at(level, player.blockPosition());
			return view != null && player.getY() >= view.skyFloorY() && !Tribulation.isActive(player);
		});
	}

	/** Once a second: watchers looking down at a live tribulation count up; at {@link #WATCH_SECONDS} they are rewarded. */
	static void second(MinecraftServer server) {
		if (LIVE.isEmpty()) {
			return;
		}
		LangitConfig config = LangitConfig.get();
		if (!config.aktif || !config.sengsaraSaksi) {
			return;
		}
		for (Map.Entry<UUID, Vec3> entry : List.copyOf(LIVE.entrySet())) {
			ServerPlayer tribulant = server.getPlayerList().getPlayer(entry.getKey());
			if (tribulant == null || !Tribulation.isActive(tribulant)) {
				LIVE.remove(entry.getKey());
				continue;
			}
			ServerLevel level = tribulant.level();
			Vec3 at = entry.getValue();
			Map<UUID, Integer> watched = WATCH.computeIfAbsent(entry.getKey(), key -> new HashMap<>());
			for (ServerPlayer watcher : watchers(level, at)) {
				SkyHallView view = LangitApi.get().at(level, watcher.blockPosition());
				if (view == null || !LangitApi.get().member(view.owner(), watcher)) {
					continue;
				}
				int seconds = watched.getOrDefault(watcher.getUUID(), 0);
				if (seconds < 0 || !looking(watcher, at)) {
					continue;
				}
				seconds++;
				if (seconds >= WATCH_SECONDS) {
					reward(server, watcher);
					seconds = -1;
					guards(level, view, entry.getKey());
				}
				watched.put(watcher.getUUID(), seconds);
			}
		}
	}

	/** True when the player looks at least {@link #LOOK_PITCH}° down and roughly toward {@code at}. */
	private static boolean looking(ServerPlayer player, Vec3 at) {
		if (player.getXRot() < LOOK_PITCH) {
			return false;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 to = at.subtract(eye);
		if (to.lengthSqr() < 1.0E-4D) {
			return false;
		}
		return player.getLookAngle().dot(to.normalize()) >= 0.6D;
	}

	private static void reward(MinecraftServer server, ServerPlayer watcher) {
		LangitData data = LangitData.get(server);
		CompoundTag own = data.player(watcher.getUUID());
		long day = LangitPeristiwa.today(server);
		if (own.getLongOr("saksi_hari", Long.MIN_VALUE) != day) {
			own.putLong("saksi_hari", day);
			own.putInt("saksi_n", 0);
		}
		int n = own.getIntOr("saksi_n", 0);
		if (n >= PER_DAY) {
			return;
		}
		own.putInt("saksi_n", n + 1);
		data.setDirty();
		double xp = Cultivators.get(watcher).rank().xpRequired() * XP_SHARE;
		Progression.addXp(watcher, xp, XpSource.EXACT);
		Msg.actionBar(watcher, Component.translatable("kultivator.sekte_langit.sengsara.saksi", n + 1, PER_DAY).withStyle(ChatFormatting.AQUA));
	}

	/** The rim guards of the island the witness stands on share the insight once per tribulation. */
	private static void guards(ServerLevel level, SkyHallView view, UUID tribulant) {
		Set<UUID> done = GUARDS.computeIfAbsent(tribulant, key -> new HashSet<>());
		AABB box = AABB.of(view.islandBox());
		for (DiscipleEntity disciple : level.getEntitiesOfClass(DiscipleEntity.class, box,
				d -> d.activity() == LangitIds.ACT_JAGA_TEPI && view.owner().equals(d.ownerId()))) {
			if (done.add(disciple.getUUID())) {
				DiscipleCultivation.addXp(disciple, disciple.data().xpRequired() * XP_SHARE, XpSource.EXACT);
			}
		}
	}

	/** Test hook ({@code uji langit_sekte sengsara}): one vortex effect below the player as if a tribulation ran there. */
	static void testVortex(ServerPlayer player, @Nullable BlockPos below) {
		BlockPos point = below != null ? below : player.blockPosition().below(64);
		LangitPayloads.send(player, new LangitPayloads.Fx(LangitIds.FX_SENGSARA_BAWAH, point, player.blockPosition()));
	}

	private LangitSaksi() {
	}
}
