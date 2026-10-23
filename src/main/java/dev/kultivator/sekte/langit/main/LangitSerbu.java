package dev.kultivator.sekte.langit.main;

import dev.kultivator.dunia.kapal.KapalBajakEntity;
import dev.kultivator.musuh.RogueCultivatorEntity;
import dev.kultivator.musuh.SectRaids;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.SkyHallView;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sect raids on a sky hall (DESIGN 37.7.5; audit 4.8: through {@code musuh.SectRaids}, not a sixth raid system). The
 * {@link SectRaids.SpawnPointProvider} hook makes raiders of a standing sky hall arrive on one of its docks
 * ({@code dermaga_*}) or landing points ({@code darat_*}, else the plaza ring) instead of on the ground below the island.
 * For every wave one Bajak Langit skiff ({@link LangitBajak#carrier}) rises from the cloud sea and lies beside the landing
 * point until the raid ends (at most {@link LangitBajak#MAX_SHIPS}). From wave 3, up to {@link #GLIDERS} raiders of the
 * wave glide in from the cloud sea to the rim (moved by the server for {@link #GLIDE_TICKS} ticks, then their own AI).
 * A raider below {@code skyFloorY - 16} has been thrown into the clouds: it is removed as slain. A raid settled as data is
 * repelled {@link #REPEL_BONUS} more often. Meter, waves, timing and results are unchanged.
 */
public final class LangitSerbu {

	public static final double REPEL_BONUS = 0.05D;
	public static final int GLIDERS = 2;
	public static final int GLIDE_TICKS = 60;
	public static final int GLIDE_FROM_WAVE = 3;

	private record Glide(RogueCultivatorEntity mob, Vec3 from, Vec3 to, long start) {
	}

	/** owner → the raid's skiffs. */
	private static final Map<UUID, List<KapalBajakEntity>> SHIPS = new HashMap<>();
	/** owner → the wave the last skiff came for. */
	private static final Map<UUID, Integer> SHIP_WAVE = new HashMap<>();
	/** owner → the last landing point. */
	private static final Map<UUID, Vec3> LANDING = new HashMap<>();
	/** owner → raiders already looked at (glide chosen or not). */
	private static final Map<UUID, Set<UUID>> SEEN = new HashMap<>();
	private static final Map<UUID, Integer> GLIDED = new HashMap<>();
	private static final List<Glide> GLIDES = new ArrayList<>();

	static void register() {
		SectRaids.addSpawnPointProvider(new SectRaids.SpawnPointProvider() {
			@Override
			public @Nullable Vec3 spawn(UUID owner, ServerLevel level, int wave, RandomSource random) {
				return spawnPoint(owner, level, wave, random);
			}

			@Override
			public double repelBonus(UUID owner) {
				return LangitConfig.on() && LangitApi.get().isSkyHall(owner) ? REPEL_BONUS : 0.0D;
			}
		});
		SectRaids.addListener(new SectRaids.RaidListener() {
			@Override
			public void onEnd(UUID owner, boolean repelled) {
				end(owner);
			}
		});
	}

	static void clear() {
		for (UUID owner : List.copyOf(SHIPS.keySet())) {
			end(owner);
		}
		GLIDES.clear();
	}

	private static @Nullable Vec3 spawnPoint(UUID owner, ServerLevel level, int wave, RandomSource random) {
		if (!LangitConfig.on() || !LangitApi.get().isSkyHall(owner)) {
			return null;
		}
		SkyHallView view = LangitApi.get().skyHall(owner);
		if (view == null || view.dim() != level.dimension()) {
			return null;
		}
		List<Vec3> points = LangitBajak.landings(view, random, 3);
		if (points.isEmpty()) {
			return null;
		}
		Vec3 point = points.get(random.nextInt(points.size()));
		if (!level.isPositionEntityTicking(BlockPos.containing(point))) {
			return null;
		}
		LANDING.put(owner, point);
		if (SHIP_WAVE.getOrDefault(owner, -1) != wave) {
			SHIP_WAVE.put(owner, wave);
			List<KapalBajakEntity> ships = SHIPS.computeIfAbsent(owner, key -> new ArrayList<>());
			ships.removeIf(ship -> ship.isRemoved());
			if (ships.size() < LangitBajak.MAX_SHIPS) {
				KapalBajakEntity ship = LangitBajak.carrier(level, view, point, random);
				if (ship != null) {
					ships.add(ship);
				}
			}
		}
		return point;
	}

	private static void end(UUID owner) {
		List<KapalBajakEntity> ships = SHIPS.remove(owner);
		if (ships != null) {
			for (KapalBajakEntity ship : ships) {
				if (!ship.isRemoved()) {
					ship.leave();
				}
			}
		}
		SHIP_WAVE.remove(owner);
		LANDING.remove(owner);
		SEEN.remove(owner);
		GLIDED.remove(owner);
	}

	/** Every tick while a sky raid runs: the gliders move. */
	static void tick(MinecraftServer server) {
		if (GLIDES.isEmpty()) {
			return;
		}
		for (Glide glide : List.copyOf(GLIDES)) {
			RogueCultivatorEntity mob = glide.mob();
			if (mob.isRemoved() || !mob.isAlive()) {
				GLIDES.remove(glide);
				continue;
			}
			long age = mob.level().getGameTime() - glide.start();
			double t = Mth.clamp(age / (double) GLIDE_TICKS, 0.0D, 1.0D);
			double ease = t * t * (3.0D - 2.0D * t);
			Vec3 at = glide.from().lerp(glide.to(), ease).add(0.0D, Math.sin(t * Math.PI) * 4.0D, 0.0D);
			mob.setPos(at.x, at.y, at.z);
			mob.setDeltaMovement(Vec3.ZERO);
			mob.resetFallDistance();
			if (t >= 1.0D) {
				GLIDES.remove(glide);
				mob.setNoGravity(false);
				mob.noPhysics = false;
				mob.setNoAi(false);
			}
		}
	}

	/** Once a second: raiders thrown off the island are slain; new raiders of wave 3+ may glide in. */
	static void second(MinecraftServer server) {
		if (!LangitConfig.on()) {
			return;
		}
		for (UUID owner : List.copyOf(SHIPS.keySet())) {
			if (!SectRaids.active(owner)) {
				end(owner);
				continue;
			}
			SkyHallView view = LangitApi.get().skyHall(owner);
			if (view == null) {
				continue;
			}
			Set<UUID> seen = SEEN.computeIfAbsent(owner, key -> new HashSet<>());
			for (RogueCultivatorEntity mob : SectRaids.liveRaiders(owner)) {
				if (mob.getY() < view.skyFloorY() - 16) {
					mob.kill((ServerLevel) mob.level());
					Halls.log(owner, Component.translatable("kultivator.sekte_langit.serbu.dibuang").withStyle(ChatFormatting.GRAY));
					continue;
				}
				if (seen.add(mob.getUUID()) && SHIP_WAVE.getOrDefault(owner, 0) >= GLIDE_FROM_WAVE && GLIDED.getOrDefault(owner, 0) < GLIDERS * waveOf(owner)) {
					glide(owner, view, mob);
				}
			}
		}
	}

	private static int waveOf(UUID owner) {
		return Math.max(1, SHIP_WAVE.getOrDefault(owner, 1) - GLIDE_FROM_WAVE + 1);
	}

	private static void glide(UUID owner, SkyHallView view, RogueCultivatorEntity mob) {
		Vec3 to = mob.position();
		Vec3 centre = Vec3.atBottomCenterOf(view.origin());
		Vec3 out = to.subtract(centre).multiply(1.0D, 0.0D, 1.0D);
		if (out.lengthSqr() < 1.0E-3D) {
			out = new Vec3(1.0D, 0.0D, 0.0D);
		}
		out = out.normalize();
		Vec3 from = new Vec3(to.x + out.x * 28.0D, view.skyFloorY() - 12.0D, to.z + out.z * 28.0D);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.noPhysics = true;
		mob.setPos(from.x, from.y, from.z);
		GLIDES.add(new Glide(mob, from, to, mob.level().getGameTime()));
		GLIDED.merge(owner, 1, Integer::sum);
	}

	private LangitSerbu() {
	}
}
