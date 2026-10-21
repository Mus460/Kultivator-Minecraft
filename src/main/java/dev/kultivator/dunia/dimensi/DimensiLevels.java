package dev.kultivator.dunia.dimensi;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaTickets;
import dev.kultivator.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The two dimensions of module D and how bodies move between them (DESIGN 34.2.2). Both dimensions are base data
 * ({@code data/kultivator/dimension/*.json}); only their entries are switched by config.
 * <ul>
 * <li>{@code kultivator:dunia_bawah}: the Yama court is placed at the dimension origin by the {@code dimension_origin}
 * structure set with {@code start_jigsaw_name kultivator:bawah/tiba}, so the arrival jigsaw of Teras Wangxiang is at
 * x 0, z 0, y = start_height − 1 = 51 (JigsawPlacement.addPieces: the named jigsaw lands on the start position, the
 * centre piece then moves down by its ground-level delta 1). The soul arrives standing on it at y 52.</li>
 * <li>{@code kultivator:laut_tanpa_batas}: the flat sea (sand top y 39, water 40-99) with the arrival dock
 * {@code laut/dermaga_tiba} at the origin, start jigsaw {@code kultivator:laut/tiba} at y 100: arrival y 101.</li>
 * </ul>
 * Every arrival first touches the target chunk with a short {@link DuniaTickets} ticket (W5: 100 ticks, radius 1) and,
 * two ticks after the teleport, moves a player stuck in a wall to a safe spot.
 */
public final class DimensiLevels {

	public static final ResourceKey<Level> BAWAH = ResourceKey.create(Registries.DIMENSION, Kultivator.id("dunia_bawah"));
	public static final ResourceKey<Level> LAUT = ResourceKey.create(Registries.DIMENSION, Kultivator.id("laut_tanpa_batas"));
	public static final Vec3 BAWAH_ARRIVAL = new Vec3(0.5D, 52.0D, 0.5D);
	public static final Vec3 LAUT_ARRIVAL = new Vec3(0.5D, 101.0D, 0.5D);
	/** The sea surface of Laut Tanpa Batas (the top water layer). */
	public static final int LAUT_SEA_TOP = 99;

	public static boolean isBawah(@Nullable Level level) {
		return level != null && BAWAH.equals(level.dimension());
	}

	public static boolean isLaut(@Nullable Level level) {
		return level != null && LAUT.equals(level.dimension());
	}

	public static @Nullable ServerLevel bawah(MinecraftServer server) {
		return server.getLevel(BAWAH);
	}

	public static @Nullable ServerLevel laut(MinecraftServer server) {
		return server.getLevel(LAUT);
	}

	/** Moves {@code player} to {@code pos} in {@code level}, touching the chunk first; null when the teleport failed. */
	public static @Nullable ServerPlayer send(ServerPlayer player, ServerLevel level, Vec3 pos, float yRot, float xRot) {
		DuniaTickets.touch(level, ChunkPos.containing(BlockPos.containing(pos)), 1);
		ServerPlayer moved = player.teleport(new TeleportTransition(level, pos, Vec3.ZERO, yRot, xRot, TeleportTransition.DO_NOTHING));
		ServerPlayer who = moved != null ? moved : player;
		who.setPortalCooldown();
		who.fallDistance = 0.0D;
		Dimensi.schedule(2, () -> unstick(who));
		return moved;
	}

	/** Moves any entity (a ship, a passenger) to {@code pos}; returns the moved entity or null. */
	public static @Nullable Entity sendEntity(Entity entity, ServerLevel level, Vec3 pos) {
		if (entity instanceof ServerPlayer player) {
			return send(player, level, pos, player.getYRot(), player.getXRot());
		}
		DuniaTickets.touch(level, ChunkPos.containing(BlockPos.containing(pos)), 1);
		return entity.teleport(new TeleportTransition(level, pos, Vec3.ZERO, entity.getYRot(), entity.getXRot(), TeleportTransition.DO_NOTHING));
	}

	/** Two ticks after an arrival: out of a wall onto a safe spot, falling back to the heightmap top. */
	static void unstick(ServerPlayer player) {
		if (player.isRemoved() || !player.isInWall()) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 spot = Targeting.safeSpot(level, player, player.position());
		if (spot == null) {
			BlockPos at = player.blockPosition();
			int top = isBawah(level) ? surfaceBelow(level, at, 120) : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
			spot = new Vec3(at.getX() + 0.5D, top, at.getZ() + 0.5D);
		}
		player.teleportTo(spot.x, spot.y, spot.z);
	}

	/** The first standable y at or below {@code fromY} in a column (for Dunia Bawah, where spires can roof a spot). */
	public static int surfaceBelow(ServerLevel level, BlockPos at, int fromY) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(at.getX(), Math.min(fromY, level.getMaxY()), at.getZ());
		boolean air = false;
		while (pos.getY() > level.getMinY()) {
			boolean empty = level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
			if (!empty && air) {
				return pos.getY() + 1;
			}
			air = empty;
			pos.move(0, -1, 0);
		}
		return Math.max(level.getMinY() + 1, at.getY());
	}

	// ================================================================== return points

	/** Writes {@code {dim, x, y, z, yr, xr}} of where {@code player} stands into {@code tag} under {@code key}. */
	public static void saveReturn(ServerPlayer player, CompoundTag tag, String key) {
		CompoundTag ret = new CompoundTag();
		ret.putString("dim", player.level().dimension().identifier().toString());
		ret.putDouble("x", player.getX());
		ret.putDouble("y", player.getY());
		ret.putDouble("z", player.getZ());
		ret.putFloat("yr", player.getYRot());
		ret.putFloat("xr", player.getXRot());
		tag.put(key, ret);
	}

	/** The saved return point as a teleport, or null (missing, or its level is gone, or it points into {@code not}). */
	public static @Nullable TeleportTransition savedReturn(MinecraftServer server, CompoundTag tag, String key, ResourceKey<Level> not) {
		CompoundTag ret = tag.getCompoundOrEmpty(key);
		if (ret.isEmpty()) {
			return null;
		}
		Identifier dim = Identifier.tryParse(ret.getStringOr("dim", ""));
		if (dim == null) {
			return null;
		}
		ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dim);
		if (levelKey.equals(not)) {
			return null;
		}
		ServerLevel level = server.getLevel(levelKey);
		if (level == null) {
			return null;
		}
		Vec3 pos = new Vec3(ret.getDoubleOr("x", 0.0D), ret.getDoubleOr("y", 64.0D), ret.getDoubleOr("z", 0.0D));
		return new TeleportTransition(level, pos, Vec3.ZERO, ret.getFloatOr("yr", 0.0F), ret.getFloatOr("xr", 0.0F),
				TeleportTransition.DO_NOTHING);
	}

	/** Sends the player to the transition, or to their respawn point when it is null. */
	public static @Nullable ServerPlayer sendHome(ServerPlayer player, @Nullable TeleportTransition target) {
		TeleportTransition home = target != null ? target : player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
		return send(player, home.newLevel(), home.position(), home.yRot(), home.xRot());
	}

	private DimensiLevels() {
	}
}
