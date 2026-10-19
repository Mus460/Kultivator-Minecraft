package dev.kultivator.technique.special;

import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Val;
import dev.kultivator.technique.spec.run.CastContext;
import dev.kultivator.technique.spec.run.EndReason;
import dev.kultivator.technique.spec.run.SpecialTechnique;
import dev.kultivator.util.DamageHooks;
import dev.kultivator.util.LoadedPath;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * {@code cloud_travel}, the special of {@code somersault_cloud} (DESIGN 22.2.7, 22.9.9; Journey to the West ch. 2: the
 * cloud somersault Subhuti teaches). Stepped safe travel of {@link #DISTANCE_BASE}→{@link #DISTANCE_CAP} blocks (at most
 * 128) along the horizontal look in {@link #RIDE_TICKS} ticks, riding {@link #CRUISE_ABOVE} blocks above the surface.
 * <p>
 * The route is planned once, read-only, before anything is spent ({@link #blocker}): it walks {@code LoadedPath} first
 * and stops at the first unloaded chunk, at a wall (the surface rising more than {@link #MAX_RISE} blocks in one step),
 * at a blocked box or clip, and before liquids, fire or portal blocks. The ride ends on a {@code safeSpot}; when no step
 * near the end has one, the cast is refused. While riding the caster is untouchable (a {@code DamageHooks} DR modifier;
 * never against {@code #kultivator:unblockable}), and after landing there is no fall damage for {@link #NO_FALL_TICKS}
 * ticks (mastery level 4: ×1.25). The player is moved by safe teleports only; no block is ever read in a hot loop or
 * changed.
 * <p>
 * Contract with the runner: the spec's own timeline holds only an empty keep-alive step at {@link #KEEP_ALIVE_TICKS};
 * the ride runs on {@code CastContext.schedule}, and the cast is ended when the caster lands.
 */
public final class CloudTravel implements SpecialTechnique {

	public static final String ID = "cloud_travel";
	public static final double DISTANCE_BASE = 64.0D;
	public static final double DISTANCE_CAP = 128.0D;
	public static final int RIDE_TICKS = 40;
	public static final double CRUISE_ABOVE = 3.0D;
	/** The surface rising more than this in one step is a wall. */
	public static final double MAX_RISE = 4.0D;
	/** The cloud sinks at most this much per step. */
	public static final double MAX_SINK = 3.0D;
	public static final double MIN_TRAVEL = 4.0D;
	public static final int NO_FALL_TICKS = 100;
	public static final double PEAK_NO_FALL = 1.25D;
	/** Steps tried backwards from the end for a safe landing. */
	public static final int EXIT_SEARCH = 12;
	public static final int KEEP_ALIVE_TICKS = 50;

	private static final String KEY = "kultivator.technique.somersault_cloud.why.";

	/** Players riding a cloud right now (session only): untouchable while riding. */
	private static final Set<UUID> RIDING = ConcurrentHashMap.newKeySet();

	static {
		DamageHooks.register(DamageHooks.Stage.DR, (entity, source, amount) -> RIDING.contains(entity.getUUID()) ? 0.0F : amount);
	}

	/** A planned ride: the step points (one per tick) and the landing spot. */
	private record Plan(Vec3 start, List<Vec3> points, Vec3 exit) {
	}

	@Override
	public @Nullable Component blocker(CastContext ctx) {
		if (ctx.player() == null) {
			return Component.translatable(KEY + "player");
		}
		return plan(ctx) == null ? Component.translatable(KEY + "exit") : null;
	}

	@Override
	public boolean cast(CastContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null) {
			return false;
		}
		Plan plan = plan(ctx);
		if (plan == null) {
			return false;
		}
		if (player.isPassenger()) {
			player.stopRiding();
		}
		RIDING.add(player.getUUID());
		player.setPortalCooldown();
		ctx.fx(Phase.TRAVEL, plan.start(), plan.exit(), 1.0F, (float) plan.start().distanceTo(plan.exit()), plan.points().size(),
				plan.points().size() + 1);
		ctx.sound(Phase.TRAVEL, plan.start());
		List<Vec3> points = plan.points();
		for (int i = 0; i < points.size(); i++) {
			Vec3 point = points.get(i);
			ctx.schedule(i + 1, () -> step(ctx, point));
		}
		ctx.schedule(points.size() + 1, () -> land(ctx, plan));
		return true;
	}

	@Override
	public void end(CastContext ctx, EndReason reason) {
		RIDING.remove(ctx.caster().getUUID());
	}

	// ================================================================== the ride

	private static void step(CastContext ctx, Vec3 point) {
		ServerPlayer player = ctx.player();
		if (ctx.ended() || player == null || player.isRemoved() || !player.isAlive() || player.level() != ctx.level()) {
			return;
		}
		ServerLevel level = ctx.level();
		if (!level.isLoaded(BlockPos.containing(point))) {
			return;
		}
		// a real ride (DESIGN 27.1 S16): the cloud carries the body toward the next checked point with the server's motion
		// packet (ServerEntity sends ClientboundSetEntityMotionPacket on syncVelocity), the landing snaps to the checked exit
		Vec3 delta = point.subtract(player.position());
		if (delta.lengthSqr() > 36.0D) {
			// far behind (a wall, lag): one safe hop instead of a huge velocity
			player.teleportTo(level, point.x, point.y, point.z, Relative.ROTATION, 0.0F, 0.0F, false);
			player.setDeltaMovement(Vec3.ZERO);
		} else {
			player.setDeltaMovement(delta);
			player.syncVelocity = true;
		}
		player.resetFallDistance();
		player.setPortalCooldown();
	}

	private static void land(CastContext ctx, Plan plan) {
		ServerPlayer player = ctx.player();
		if (player == null) {
			return;
		}
		RIDING.remove(player.getUUID());
		if (ctx.ended() || player.isRemoved() || !player.isAlive() || player.level() != ctx.level()) {
			return;
		}
		ServerLevel level = ctx.level();
		Vec3 exit = plan.exit();
		if (level.isLoaded(BlockPos.containing(exit))) {
			player.teleportTo(level, exit.x, exit.y, exit.z, Relative.ROTATION, 0.0F, 0.0F, false);
			player.setDeltaMovement(Vec3.ZERO);
		}
		player.resetFallDistance();
		int noFall = NO_FALL_TICKS;
		if (ctx.mastery() >= 4 && !ctx.arena()) {
			noFall = (int) Math.round(NO_FALL_TICKS * PEAK_NO_FALL);
		}
		ctx.applySelf(buff(Payload.BuffKind.NO_FALL, 1, noFall));
		ctx.markPath(plan.start(), player.position());
		ctx.markEnd(player.position());
		ctx.fx(Phase.EXTRA0, player.position(), player.position(), 1.5F, 0.0F, 1, 10);
		ctx.sound(Phase.EXTRA0, player.position());
		ctx.end(EndReason.DONE);
	}

	// ================================================================== planning (read-only)

	/** The ride along the horizontal look, or null when it cannot move or no step near its end has a safe landing. */
	private static @Nullable Plan plan(CastContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null) {
			return null;
		}
		ServerLevel level = ctx.level();
		Vec3 start = player.position();
		Vec3 direction = horizontal(ctx.look(), player.getYRot());
		// The spine's own growth (A × mastery area, at most 128): the radius hard cap of areas does not apply to travel.
		double distance = TechniquePower.area(Val.of(DISTANCE_BASE, DISTANCE_CAP), ctx.factors(), ctx.mastery(), DISTANCE_CAP);
		if (!(distance > 0.0D)) {
			distance = DISTANCE_BASE;
		}
		Vec3 far = LoadedPath.clip(level, start, start.add(direction.scale(distance)));
		double reach = Math.sqrt((far.x - start.x) * (far.x - start.x) + (far.z - start.z) * (far.z - start.z));
		if (reach < MIN_TRAVEL) {
			return null;
		}
		double stride = reach / RIDE_TICKS;
		List<Vec3> points = new ArrayList<>();
		Vec3 previous = start;
		double y = start.y;
		for (int i = 1; i <= RIDE_TICKS; i++) {
			double x = start.x + direction.x * stride * i;
			double z = start.z + direction.z * stride * i;
			BlockPos column = BlockPos.containing(x, y, z);
			if (!level.isLoaded(column)) {
				break;
			}
			double want = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()) + CRUISE_ABOVE;
			if (want > y + MAX_RISE) {
				break;
			}
			double next = want >= y ? want : Math.max(want, y - MAX_SINK);
			Vec3 point = new Vec3(x, next, z);
			if (!open(level, player, previous, point)) {
				break;
			}
			points.add(point);
			previous = point;
			y = next;
		}
		if (points.isEmpty()) {
			return null;
		}
		int lowest = Math.max(0, points.size() - EXIT_SEARCH);
		for (int j = points.size() - 1; j >= lowest; j--) {
			Vec3 point = points.get(j);
			BlockPos column = BlockPos.containing(point);
			double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
			Vec3 spot = ctx.safeSpot(new Vec3(point.x, ground, point.z));
			if (spot != null && level.isLoaded(BlockPos.containing(spot)) && spot.y <= point.y + 1.0D) {
				return new Plan(start, List.copyOf(points.subList(0, j + 1)), spot);
			}
		}
		return null;
	}

	/** The caster's box fits at {@code to}, with no liquid, fire or portal in it, and the clip between the points is open. */
	private static boolean open(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
		AABB box = player.getDimensions(player.getPose()).makeBoundingBox(to);
		if (!level.noCollision(player, box) || level.containsAnyLiquid(box)
				|| level.getBlockStates(box.inflate(0.01D)).anyMatch(CloudTravel::hazard)) {
			return false;
		}
		Vec3 a = from.add(0.0D, 0.9D, 0.0D);
		Vec3 b = to.add(0.0D, 0.9D, 0.0D);
		return LoadedPath.ray(level, a, b, player).getType() == HitResult.Type.MISS;
	}

	private static boolean hazard(BlockState state) {
		return state.is(BlockTags.FIRE) || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY)
				|| state.is(Blocks.POWDER_SNOW) || state.is(Blocks.COBWEB);
	}

	/** The horizontal unit vector of the look (the yaw when looking straight up or down). */
	private static Vec3 horizontal(Vec3 look, float yaw) {
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		if (flat.lengthSqr() > 1.0E-4D) {
			return flat.normalize();
		}
		Vec3 byYaw = Vec3.directionFromRotation(0.0F, yaw);
		return new Vec3(byYaw.x, 0.0D, byYaw.z).normalize();
	}
}
