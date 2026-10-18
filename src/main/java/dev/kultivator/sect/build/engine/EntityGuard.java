package dev.kultivator.sect.build.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * No living entity is trapped or dropped (DESIGN 21.10 safety checklist 5): a solid target is deferred while a living
 * entity's box overlaps the cell, and a removal is deferred while the cell holds up a living entity's feet; hanging
 * entities, armor stands, boats and minecarts count as blocking, and an item frame or painting hanging on a cell that
 * would lose its solid face blocks it too. The entities near one burst are gathered once ({@link #gather}); a cell outside
 * every gathered box gathers its own surroundings first, so no op is ever checked against an empty list. Reads only.
 */
public final class EntityGuard {

	/** The extra area gathered around a cell that lies outside every gathered box. */
	private static final double REGATHER = 8.0D;

	private final ServerLevel level;
	private final List<Entity> nearby = new ArrayList<>();
	private final Set<Integer> seen = new HashSet<>();
	private final List<AABB> covered = new ArrayList<>();

	private EntityGuard(ServerLevel level) {
		this.level = level;
	}

	/** Gathers the entities around {@code box} (one query per burst). */
	public static EntityGuard gather(ServerLevel level, AABB box) {
		EntityGuard guard = new EntityGuard(level);
		guard.add(box);
		return guard;
	}

	private void add(AABB box) {
		covered.add(box);
		for (Entity entity : level.getEntitiesOfClass(Entity.class, box.inflate(1.5D),
				entity -> entity.isAlive() && (entity instanceof LivingEntity || entity instanceof HangingEntity || entity instanceof AbstractMinecart
						|| entity instanceof AbstractBoat || entity instanceof ArmorStand))) {
			if (seen.add(entity.getId())) {
				nearby.add(entity);
			}
		}
	}

	private void ensure(BlockPos pos) {
		for (AABB box : covered) {
			if (box.contains(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)) {
				return;
			}
		}
		add(new AABB(pos).inflate(REGATHER));
	}

	/** True when placing {@code target} at {@code pos} (or removing the block there) must wait for an entity. */
	public boolean inTheWay(ServerLevel level, BlockPos pos, BlockState target) {
		ensure(pos);
		if (nearby.isEmpty()) {
			return false;
		}
		AABB cell = new AABB(pos);
		boolean solid = !target.isAir() && !target.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty();
		if (!solid && holdsHanging(pos)) {
			return true;
		}
		if (target.isAir()) {
			AABB feet = new AABB(pos.getX(), pos.getY() + 0.9D, pos.getZ(), pos.getX() + 1.0D, pos.getY() + 1.2D, pos.getZ() + 1.0D);
			for (Entity entity : nearby) {
				if (entity.getBoundingBox().intersects(feet) || !(entity instanceof LivingEntity) && entity.getBoundingBox().intersects(cell)) {
					return true;
				}
			}
			return false;
		}
		if (!solid) {
			for (Entity entity : nearby) {
				if (!(entity instanceof LivingEntity) && entity.getBoundingBox().intersects(cell)) {
					return true;
				}
			}
			return false;
		}
		for (Entity entity : nearby) {
			if (entity.getBoundingBox().intersects(cell)) {
				return true;
			}
		}
		return false;
	}

	/** An item frame or painting whose support box (its box moved half a block into the wall) touches {@code pos}. */
	private boolean holdsHanging(BlockPos pos) {
		AABB cell = new AABB(pos);
		for (Entity entity : nearby) {
			if (entity instanceof HangingEntity hanging && hanging.getDirection() != null) {
				Direction direction = hanging.getDirection();
				AABB support = hanging.getBoundingBox().move(-direction.getStepX() * 0.5D, -direction.getStepY() * 0.5D, -direction.getStepZ() * 0.5D)
						.deflate(1.0E-7D);
				if (support.intersects(cell)) {
					return true;
				}
			}
		}
		return false;
	}

	/** True when an entity that never walks away on its own (a boat, minecart, armor stand or hanging entity) blocks {@code pos}. */
	public boolean stuckAt(BlockPos pos) {
		AABB cell = new AABB(pos).inflate(0.5D);
		for (Entity entity : nearby) {
			if (!(entity instanceof LivingEntity) || entity instanceof ArmorStand) {
				if (entity.getBoundingBox().intersects(cell)) {
					return true;
				}
			}
		}
		return holdsHanging(pos);
	}

	/** Players within 16 of {@code pos} among the gathered entities. */
	public List<net.minecraft.server.level.ServerPlayer> playersNear(BlockPos pos) {
		List<net.minecraft.server.level.ServerPlayer> players = new ArrayList<>();
		for (Entity entity : nearby) {
			if (entity instanceof net.minecraft.server.level.ServerPlayer player && player.blockPosition().distSqr(pos) <= 256.0D) {
				players.add(player);
			}
		}
		return players;
	}
}
