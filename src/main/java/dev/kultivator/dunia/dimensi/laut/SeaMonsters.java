package dev.kultivator.dunia.dimensi.laut;

import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.dunia.dimensi.DimensiConfig;
import dev.kultivator.dunia.dimensi.DimensiEntities;
import dev.kultivator.dunia.dimensi.DimensiLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The sea's monsters and its turtle (DESIGN 34.5.14). Every 10 s per player in Laut Tanpa Batas (none within 160 b of
 * the arrival dock, none while the MSPT guard holds): if fewer than {@code dimensi.monstersPerPlayer} sea monsters are
 * within 64 b, one more comes from beyond sight: a serpent (25 %) in open water, a rock crab (40 %) on the sea floor, a
 * sea yaksha (35 %) at mid depth. Their tier is the ring of 1 000 b from the origin (0 Golden Core ... 4 Immortal
 * Ascension). At most 24 sea monsters per server ({@link #MONSTER} budget). One Penyu Ao per dimension, spawned 70-110 b
 * from a player when the switch {@code dimensi.turtle} is on.
 */
public final class SeaMonsters {

	public static final String MONSTER = "laut_monster";
	public static final int SERVER_CAP = 24;
	public static final double SAFE_RADIUS = 160.0D;

	static void init() {
		DuniaBudget.setCap(MONSTER, SERVER_CAP);
	}

	/** The tier of the sea ring at {@code pos}: one per 1 000 b from the origin, 0-4. */
	public static int tierAt(Vec3 pos) {
		return Mth.clamp((int) (Math.sqrt(pos.x * pos.x + pos.z * pos.z) / 1000.0D), 0, 4);
	}

	static void tick(MinecraftServer server, long ticks) {
		if (ticks % 200L != 7L || !DuniaSpine.enabled() || !DimensiConfig.get().sea) {
			return;
		}
		ServerLevel level = DimensiLevels.laut(server);
		if (level == null || level.players().isEmpty() || DuniaBudget.overloaded(server)) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative()) {
				continue;
			}
			monsterFor(level, player);
		}
		if (DimensiConfig.get().turtle) {
			turtle(level);
		}
	}

	private static void monsterFor(ServerLevel level, ServerPlayer player) {
		int cap = DimensiConfig.get().monstersPerPlayer;
		Vec3 at = player.position();
		if (cap <= 0 || at.horizontalDistanceSqr() < SAFE_RADIUS * SAFE_RADIUS) {
			return;
		}
		int near = level.getEntitiesOfClass(Mob.class, new AABB(player.blockPosition()).inflate(64.0D),
				mob -> mob.isAlive() && (mob instanceof UlarLautEntity || mob instanceof KepitingBatuEntity
						|| mob instanceof YakshaEntity yaksha && yaksha.variant() == YakshaEntity.LAUT)).size();
		if (near >= cap || !DuniaBudget.mayAdd(level, MONSTER)) {
			return;
		}
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0D;
		double r = 28.0D + random.nextDouble() * 20.0D;
		double x = at.x + Math.cos(angle) * r;
		double z = at.z + Math.sin(angle) * r;
		if (!openWater(level, x, z)) {
			return;
		}
		int tier = tierAt(new Vec3(x, 0.0D, z));
		float roll = random.nextFloat();
		Mob mob;
		double y;
		if (roll < 0.25F) {
			UlarLautEntity serpent = DimensiEntities.ULAR_LAUT.create(level, EntitySpawnReason.EVENT);
			if (serpent == null) {
				return;
			}
			serpent.setup(tier, false);
			mob = serpent;
			y = 60.0D + random.nextDouble() * 25.0D;
		} else if (roll < 0.65F) {
			KepitingBatuEntity crab = DimensiEntities.KEPITING_BATU.create(level, EntitySpawnReason.EVENT);
			if (crab == null) {
				return;
			}
			crab.setup(tier);
			mob = crab;
			y = 40.0D;
		} else {
			YakshaEntity yaksha = DimensiEntities.YAKSHA.create(level, EntitySpawnReason.EVENT);
			if (yaksha == null) {
				return;
			}
			yaksha.setup(YakshaEntity.LAUT, tier, false);
			mob = yaksha;
			y = 55.0D + random.nextDouble() * 30.0D;
		}
		mob.snapTo(x, y, z, random.nextFloat() * 360.0F, 0.0F);
		if (level.addFreshEntity(mob)) {
			DuniaBudget.track(MONSTER, mob);
		}
	}

	/** True when the column at the waterline is open sea (no island in the way). */
	static boolean openWater(ServerLevel level, double x, double z) {
		BlockPos top = BlockPos.containing(x, DimensiLevels.LAUT_SEA_TOP, z);
		return level.getBlockState(top).getFluidState().isSource() && level.getBlockState(top.above()).isAir();
	}

	private static void turtle(ServerLevel level) {
		List<? extends PenyuAoEntity> alive = level.getEntities(DimensiEntities.PENYU_AO, turtle -> turtle.isAlive());
		if (!alive.isEmpty()) {
			return;
		}
		spawnTurtle(level, level.players().get(level.getRandom().nextInt(level.players().size())));
	}

	/** Spawns Penyu Ao 70-110 b from {@code near} on open water; null when none fit. */
	public static @Nullable PenyuAoEntity spawnTurtle(ServerLevel level, ServerPlayer near) {
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = 70.0D + random.nextDouble() * 40.0D;
			double x = near.getX() + Math.cos(angle) * r;
			double z = near.getZ() + Math.sin(angle) * r;
			if (!openWater(level, x, z) || !openWater(level, x + 8.0D, z) || !openWater(level, x - 8.0D, z)
					|| !openWater(level, x, z + 8.0D) || !openWater(level, x, z - 8.0D)) {
				continue;
			}
			PenyuAoEntity turtle = DimensiEntities.PENYU_AO.create(level, EntitySpawnReason.EVENT);
			if (turtle == null) {
				return null;
			}
			float yaw = random.nextFloat() * 360.0F;
			turtle.setup(random.nextInt(), yaw);
			turtle.snapTo(x, DimensiLevels.LAUT_SEA_TOP + 0.4D, z, yaw, 0.0F);
			return level.addFreshEntity(turtle) ? turtle : null;
		}
		return null;
	}

	private SeaMonsters() {
	}
}
