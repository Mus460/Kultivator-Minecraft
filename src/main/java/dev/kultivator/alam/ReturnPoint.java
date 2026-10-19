package dev.kultivator.alam;

import dev.kultivator.data.Cultivators;
import dev.kultivator.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The return point of a realm run (DESIGN 25.3.4 step 3 and 7): {@code extra.dunia.ret = {dim, x, y, z, yr, xr}}, written
 * before the teleport into the realm dimension (persistent, copied on death). Returning teleports there; two ticks later a
 * player stuck in a wall is moved with {@code Targeting.safeSpot}, falling back to the heightmap top. A missing point or a
 * missing level falls back to the respawn point, then the world spawn ({@code findRespawnPositionAndUseSpawnBlock}).
 */
public final class ReturnPoint {

	public static final String KEY = "ret";

	/** The player's {@code extra.dunia} compound (created and attached when missing). */
	public static CompoundTag dunia(ServerPlayer player) {
		CompoundTag extra = Cultivators.get(player).extra();
		CompoundTag dunia = extra.getCompound("dunia").orElse(null);
		if (dunia == null) {
			dunia = new CompoundTag();
			extra.put("dunia", dunia);
		}
		return dunia;
	}

	/** Saves where the player stands now as the return point. */
	public static void save(ServerPlayer player) {
		CompoundTag ret = new CompoundTag();
		ret.putString("dim", player.level().dimension().identifier().toString());
		ret.putDouble("x", player.getX());
		ret.putDouble("y", player.getY());
		ret.putDouble("z", player.getZ());
		ret.putFloat("yr", player.getYRot());
		ret.putFloat("xr", player.getXRot());
		dunia(player).put(KEY, ret);
		Cultivators.dirty(player);
	}

	public static boolean has(ServerPlayer player) {
		return dunia(player).contains(KEY);
	}

	public static void clear(ServerPlayer player) {
		dunia(player).remove(KEY);
		Cultivators.dirty(player);
	}

	/** The saved point as a teleport, or null when it is missing or its level is gone (or it is the realm itself). */
	public static @Nullable TeleportTransition saved(ServerPlayer player) {
		CompoundTag ret = dunia(player).getCompoundOrEmpty(KEY);
		if (ret.isEmpty()) {
			return null;
		}
		Identifier dim = Identifier.tryParse(ret.getStringOr("dim", ""));
		if (dim == null) {
			return null;
		}
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dim);
		if (key.equals(Alam.REALM_LEVEL)) {
			return null;
		}
		ServerLevel level = player.level().getServer().getLevel(key);
		if (level == null) {
			return null;
		}
		Vec3 pos = new Vec3(ret.getDoubleOr("x", 0.0D), ret.getDoubleOr("y", 64.0D), ret.getDoubleOr("z", 0.0D));
		return new TeleportTransition(level, pos, Vec3.ZERO, ret.getFloatOr("yr", 0.0F), ret.getFloatOr("xr", 0.0F),
				TeleportTransition.DO_NOTHING);
	}

	/**
	 * Sends the player back: the saved point, else the respawn point or world spawn. The point is cleared. Two ticks later a
	 * player inside a wall is moved to a safe spot. True when a teleport happened.
	 */
	public static boolean sendBack(ServerPlayer player) {
		TeleportTransition target = saved(player);
		if (target == null) {
			target = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
		}
		clear(player);
		ServerPlayer moved = player.teleport(target);
		ServerPlayer who = moved != null ? moved : player;
		who.setPortalCooldown();
		Alam.schedule(2, () -> unstick(who));
		return moved != null;
	}

	/** Two ticks after a return: out of a wall onto a safe spot, falling back to the heightmap top. */
	static void unstick(ServerPlayer player) {
		if (player.isRemoved() || !player.isInWall()) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 spot = Targeting.safeSpot(level, player, player.position());
		if (spot == null) {
			BlockPos at = player.blockPosition();
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
			spot = new Vec3(at.getX() + 0.5D, top, at.getZ() + 0.5D);
		}
		player.teleportTo(spot.x, spot.y, spot.z);
	}

	private ReturnPoint() {
	}
}
