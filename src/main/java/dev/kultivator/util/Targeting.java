package dev.kultivator.util;

import dev.kultivator.beast.SpiritBeasts;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Who a technique may hit: monsters (and mobs attacking the caster), never other players (unless sparring), pets,
 * villagers or passive animals. Also read-only aiming helpers; nothing here ever touches a block.
 */
public final class Targeting {

	/** Extra rules from modules: return true to EXCLUDE the entity for this caster (e.g. someone else's heart demon). */
	private static final List<BiPredicate<ServerPlayer, Entity>> EXCLUSIONS = new ArrayList<>();
	/** Set by the sect module: true when the two players are in an accepted sparring duel. */
	public static BiPredicate<ServerPlayer, ServerPlayer> sparring = (a, b) -> false;

	public static void addExclusion(BiPredicate<ServerPlayer, Entity> exclusion) {
		EXCLUSIONS.add(exclusion);
	}

	/**
	 * True when a technique of {@code caster} may hurt {@code target}. Never a creaking bound to a creaking heart: hurt by
	 * a player (or their tamed wolf), its heart grows resin blocks. Never the Wither: every hit makes it break the blocks
	 * around it a second later.
	 */
	public static boolean isHostileTo(Entity entity, ServerPlayer caster) {
		if (!(entity instanceof LivingEntity target) || target == caster || !target.isAlive() || target.isSpectator()
				|| target.isInvulnerable()) {
			return false;
		}
		if (target instanceof Creaking creaking && creaking.isHeartBound()) {
			return false;
		}
		if (target instanceof WitherBoss) {
			return false;
		}
		for (BiPredicate<ServerPlayer, Entity> exclusion : EXCLUSIONS) {
			if (exclusion.test(caster, target)) {
				return false;
			}
		}
		if (target instanceof Player other) {
			return other instanceof ServerPlayer serverOther && sparring.test(caster, serverOther);
		}
		if (target instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) {
			// Pets (anyone's) are only fair game while they are attacking the caster.
			return target instanceof Mob mob && mob.getTarget() == caster;
		}
		if (target instanceof Enemy) {
			return true;
		}
		return target instanceof Mob mob && mob.getTarget() == caster;
	}

	/** Living entities a technique may hit within {@code radius} of {@code center}, nearest first. */
	public static List<LivingEntity> hostilesAround(ServerPlayer caster, Vec3 center, double radius) {
		AABB box = new AABB(center, center).inflate(radius);
		double radiusSq = radius * radius;
		List<LivingEntity> list = caster.level().getEntitiesOfClass(LivingEntity.class, box,
				entity -> entity.distanceToSqr(center) <= radiusSq && isHostileTo(entity, caster));
		list.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)));
		return list;
	}

	/** Friendly living things near the caster that healing may touch: the caster, other players, tamed pets, spirit beasts. */
	public static List<LivingEntity> alliesAround(ServerPlayer caster, double radius) {
		AABB box = caster.getBoundingBox().inflate(radius);
		return caster.level().getEntitiesOfClass(LivingEntity.class, box, entity -> entity.isAlive()
				&& (entity instanceof Player || entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null
						|| SpiritBeasts.isSpiritBeast(entity))
				&& !isHostileTo(entity, caster));
	}

	/** Where the caster's view hits a block within {@code range} (read-only raycast), or the end point. */
	public static Vec3 aimPoint(ServerPlayer caster, double range) {
		BlockHitResult hit = blockHit(caster, range);
		return hit.getType() == HitResult.Type.MISS ? caster.getEyePosition().add(caster.getViewVector(1.0F).scale(range)) : hit.getLocation();
	}

	public static BlockHitResult blockHit(ServerPlayer caster, double range) {
		Vec3 eye = caster.getEyePosition();
		Vec3 end = eye.add(caster.getViewVector(1.0F).scale(range));
		return caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
	}

	/** The hostile entity under the crosshair within {@code range}, not behind a wall; null if none. */
	public static @Nullable LivingEntity lookedAtHostile(ServerPlayer caster, double range) {
		Vec3 eye = caster.getEyePosition();
		Vec3 look = caster.getViewVector(1.0F);
		BlockHitResult block = blockHit(caster, range);
		double maxSq = block.getType() == HitResult.Type.MISS ? range * range : block.getLocation().distanceToSqr(eye);
		Vec3 end = eye.add(look.scale(range));
		AABB sweep = caster.getBoundingBox().expandTowards(look.scale(range)).inflate(1.5D);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster, eye, end, sweep,
				entity -> isHostileTo(entity, caster) && entity.isPickable(), maxSq);
		if (hit != null && hit.getEntity() instanceof LivingEntity living) {
			return living;
		}
		// A little help for small or fast targets: the nearest hostile within a narrow cone.
		LivingEntity best = null;
		double bestScore = 0.97D;
		for (LivingEntity candidate : hostilesAround(caster, eye, range)) {
			Vec3 to = candidate.getBoundingBox().getCenter().subtract(eye);
			double distSq = to.lengthSqr();
			if (distSq > maxSq || distSq < 1.0E-4D) {
				continue;
			}
			double dot = to.normalize().dot(look);
			if (dot > bestScore && hasLineOfSight(caster, candidate)) {
				bestScore = dot;
				best = candidate;
			}
		}
		return best;
	}

	/** Read-only check that nothing solid is between the caster's eyes and the target's middle. */
	public static boolean hasLineOfSight(ServerPlayer caster, Entity target) {
		Vec3 eye = caster.getEyePosition();
		Vec3 middle = target.getBoundingBox().getCenter();
		BlockHitResult hit = caster.level().clip(new ClipContext(eye, middle, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(eye) >= middle.distanceToSqr(eye) - 0.25D;
	}

	/**
	 * A spot where {@code entity} can stand at or near {@code target}: free of blocks for its box, standing on something
	 * solid, with nothing harmful in it (fire, powder snow, cobweb, berry bush, wither rose, cactus, and portals: travel
	 * could build an exit portal or the End platform) or underfoot (magma, a lit campfire, cactus, pointed dripstone). Searches a few blocks up and down. Null when there is none. Read-only.
	 */
	public static @Nullable Vec3 safeSpot(ServerLevel level, Entity entity, Vec3 target) {
		BlockPos base = BlockPos.containing(target);
		for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
			BlockPos feet = base.above(dy);
			BlockPos below = feet.below();
			BlockState belowState = level.getBlockState(below);
			if (belowState.getCollisionShape(level, below).isEmpty() || hazardUnderfoot(belowState)) {
				continue;
			}
			Vec3 spot = new Vec3(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
			AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(spot);
			if (level.noCollision(entity, box) && !level.containsAnyLiquid(box)
					&& level.getBlockStates(box.inflate(0.01D, 0.0D, 0.01D)).noneMatch(Targeting::hazard)) {
				return spot;
			}
		}
		return null;
	}

	/**
	 * Blocks that hurt or trap whoever stands inside them (a lit campfire burns even when stepped into), and portals
	 * (an entity put inside one could travel and build blocks on the other side).
	 */
	private static boolean hazard(BlockState state) {
		return state.is(BlockTags.FIRE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.COBWEB)
				|| state.is(Blocks.WITHER_ROSE) || state.is(Blocks.CACTUS) || CampfireBlock.isLitCampfire(state)
				|| state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY);
	}

	/** Blocks that hurt whoever stands on them. */
	private static boolean hazardUnderfoot(BlockState state) {
		return state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.POINTED_DRIPSTONE)
				|| CampfireBlock.isLitCampfire(state);
	}

	private Targeting() {
	}
}
