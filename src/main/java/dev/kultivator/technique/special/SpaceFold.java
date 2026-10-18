package dev.kultivator.technique.special;

import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.run.CastContext;
import dev.kultivator.technique.spec.run.EndReason;
import dev.kultivator.technique.spec.run.SpecialTechnique;
import dev.kultivator.util.LoadedPath;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.PortalIndex;
import dev.kultivator.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code space_fold}, the special of {@code spatial_ancestral_symbol} (DESIGN 22.2.7, 22.9.8; Martial Universe's
 * Spatial Ancestral Symbol). The spec's own stage-1 timeline places the FIELD and its pierce pulses; this handler adds
 * what the DSL cannot say:
 * <ul>
 * <li><b>the fold</b>, once per cast: up to {@link #MAX_MOVED} non-boss monsters inside the field are moved to safe
 * spots within {@link #SPREAD} blocks of its centre ({@code Targeting.safeSpot}, loaded chunks only, never within 8
 * blocks of a portal, never a creeper, ravager, boss or player: {@code CastContext.moveAllowed});</li>
 * <li><b>the blinks</b> of stages 2-8: a free safe teleport of the caster to the looked-at spot, kept inside the field,
 * at least {@link #BLINK_GAP} ticks apart (space may skip the clear-path check, 22.3.3; never into portals or unloaded
 * chunks).</li>
 * </ul>
 * Contract with the runner: {@link #cast} runs for every stage with {@code ctx.stage()} (stage 1 starts the fold, later
 * stages blink); if the runner never calls it, {@link #tick} starts the fold on its own. No block is read in a hot loop
 * (one entity query, at most {@link #TRIES} safe-spot checks per moved monster, once per cast) and none is ever changed.
 */
public final class SpaceFold implements SpecialTechnique {

	public static final String ID = "space_fold";
	/** ST 8: the cast plus seven blinks. */
	public static final int STAGES = 8;
	public static final int WINDOW = 200;
	public static final double RANGE = 32.0D;
	public static final double RADIUS = 16.0D;
	public static final double RADIUS_CAP = 28.0D;
	public static final double HEIGHT = 12.0D;
	public static final int DURATION = 200;
	/** At most this many monsters are folded per cast. */
	public static final int MAX_MOVED = 32;
	/** Folded monsters land within this many blocks of the centre. */
	public static final double SPREAD = 3.0D;
	/** Ticks between two blinks. */
	public static final int BLINK_GAP = 20;
	/** A blink lands at least this far inside the field's edge. */
	public static final double EDGE = 1.0D;
	/** Safe-spot attempts per folded monster. */
	private static final int TRIES = 3;
	/** Blocks scanned down to put a centre in the air onto the ground. */
	private static final int GROUND_SCAN = 8;
	private static final double GOLDEN_ANGLE = Math.PI * (3.0D - Math.sqrt(5.0D));

	private static final String KEY = "kultivator.technique.spatial_ancestral_symbol.";
	private static final String STATE_STARTED = "space_fold_started";

	/** The open fold of each caster (session only). */
	private static final Map<UUID, Fold> FOLDS = new HashMap<>();

	private static final class Fold {
		private final int level;
		private final CastContext owner;
		private Vec3 centre;
		private double radius;
		private long end;
		private long lastBlink = Long.MIN_VALUE / 2;
		private boolean folded;

		private Fold(int level, CastContext owner, Vec3 centre, double radius, long end) {
			this.level = level;
			this.owner = owner;
			this.centre = centre;
			this.radius = radius;
			this.end = end;
		}
	}

	@Override
	public boolean cast(CastContext ctx) {
		if (ctx.stage() >= 2) {
			return blink(ctx);
		}
		if (!ctx.state().getBooleanOr(STATE_STARTED, false)) {
			start(ctx);
		}
		return true;
	}

	@Override
	public void tick(CastContext ctx) {
		if (ctx.stage() >= 2) {
			return;
		}
		if (!ctx.state().getBooleanOr(STATE_STARTED, false)) {
			start(ctx);
		}
		Fold fold = FOLDS.get(ctx.caster().getUUID());
		if (fold == null || fold.owner != ctx) {
			return;
		}
		if (ctx.areaRadius() > 0.0D) {
			fold.centre = ctx.areaCentre();
			fold.radius = ctx.areaRadius();
		}
		fold.end = Math.max(fold.end, ctx.level().getGameTime() + 2L);
	}

	@Override
	public void end(CastContext ctx, EndReason reason) {
		Fold fold = FOLDS.get(ctx.caster().getUUID());
		if (fold != null && fold.owner == ctx) {
			FOLDS.remove(ctx.caster().getUUID());
		}
	}

	@Override
	public @Nullable Component blocker(CastContext ctx) {
		if (ctx.stage() < 2) {
			return null;
		}
		Fold fold = active(ctx);
		if (fold == null) {
			return Component.translatable(KEY + "closed");
		}
		long wait = BLINK_GAP - (ctx.level().getGameTime() - fold.lastBlink);
		if (wait > 0L) {
			return Component.translatable(KEY + "wait", String.format(Locale.ROOT, "%.1f", wait / 20.0D));
		}
		return null;
	}

	// ================================================================== the fold

	/**
	 * Opens this caster's fold (a newer cast replaces an older one) and folds at once ("@0"): at the placed field when
	 * the runner already placed it, otherwise at the aim point the field is anchored at.
	 */
	private static void start(CastContext ctx) {
		ctx.state().putBoolean(STATE_STARTED, true);
		purge(ctx.level());
		boolean placed = ctx.areaRadius() > 0.0D;
		Vec3 centre = placed ? ctx.areaCentre() : aimCentre(ctx);
		double radius = placed ? ctx.areaRadius() : ctx.area(RADIUS, RADIUS_CAP);
		long end = ctx.level().getGameTime() + ctx.duration(DURATION, DURATION * 1.5D) + 2L;
		Fold fold = new Fold(System.identityHashCode(ctx.level()), ctx, centre, radius, end);
		FOLDS.put(ctx.caster().getUUID(), fold);
		fold(ctx, fold);
	}

	/** Moves the field's non-boss monsters next to its centre, once. */
	private static void fold(CastContext ctx, Fold fold) {
		if (fold.folded) {
			return;
		}
		fold.folded = true;
		ServerLevel level = ctx.level();
		Vec3 centre = ground(level, fold.centre);
		List<LivingEntity> found = ctx.hostiles(centre, fold.radius, false);
		int moved = 0;
		int index = 0;
		for (LivingEntity target : found) {
			if (moved >= MAX_MOVED) {
				break;
			}
			if (!target.isAlive() || target instanceof Player || TechniquePower.isBoss(target) || target.isPassenger()
					|| target.isVehicle() || Math.abs(target.getY() - centre.y) > HEIGHT || !ctx.moveAllowed(target)) {
				continue;
			}
			Vec3 spot = spotFor(ctx, level, target, centre, index++);
			if (spot == null) {
				continue;
			}
			target.teleportTo(spot.x, spot.y, spot.z);
			target.setDeltaMovement(Vec3.ZERO);
			target.resetFallDistance();
			// Portal travel could build an exit portal or the End platform (blocks); folded monsters trample nothing.
			target.setPortalCooldown();
			target.addTag(NoGrief.TAG);
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			moved++;
		}
		ctx.fx(Phase.EXTRA0, centre, centre, (float) fold.radius, (float) SPREAD, moved, 16);
		ctx.sound(Phase.EXTRA0, centre);
	}

	/** A safe spot for {@code target} within {@link #SPREAD} of {@code centre}, spread on a golden-angle spiral, or null. */
	private static @Nullable Vec3 spotFor(CastContext ctx, ServerLevel level, LivingEntity target, Vec3 centre, int index) {
		for (int attempt = 0; attempt < TRIES; attempt++) {
			int k = index * TRIES + attempt;
			double angle = k * GOLDEN_ANGLE;
			double r = 0.5D + (SPREAD - 0.5D) * ((k * 0.6180339887D) % 1.0D);
			Vec3 near = centre.add(Math.cos(angle) * r, 0.0D, Math.sin(angle) * r);
			if (!level.isLoaded(BlockPos.containing(near))) {
				continue;
			}
			Vec3 spot = Targeting.safeSpot(level, target, near);
			if (spot == null || horizontal(spot, centre) > SPREAD + 1.0D) {
				continue;
			}
			if (ctx.portals().near(level, BlockPos.containing(spot), PortalIndex.MOVE_RADIUS)) {
				continue;
			}
			return spot;
		}
		return null;
	}

	// ================================================================== the blinks

	/** One free blink of stages 2-8 to the looked-at spot, kept inside the field. True when the caster moved. */
	private static boolean blink(CastContext ctx) {
		ServerPlayer player = ctx.player();
		Fold fold = active(ctx);
		if (player == null || fold == null) {
			return false;
		}
		ServerLevel level = ctx.level();
		long now = level.getGameTime();
		if (now - fold.lastBlink < BLINK_GAP) {
			return false;
		}
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = LoadedPath.ray(level, eye, eye.add(player.getViewVector(1.0F).scale(RANGE)), player);
		Vec3 aim = hit.getLocation();
		if (hit.getType() != HitResult.Type.MISS) {
			// Step out of the face looked at: on top of a floor, in front of a wall, under a ceiling.
			Direction face = hit.getDirection();
			double dy = face == Direction.DOWN ? -player.getBbHeight() - 0.1D : face.getStepY() * 0.1D;
			aim = aim.add(face.getStepX() * 0.6D, dy, face.getStepZ() * 0.6D);
		}
		aim = insideField(aim, fold);
		if (!level.isLoaded(BlockPos.containing(aim))) {
			return false;
		}
		Vec3 spot = ctx.safeSpot(aim);
		if (spot == null || !level.isLoaded(BlockPos.containing(spot))) {
			player.sendOverlayMessage(Component.translatable(KEY + "no_spot"));
			return false;
		}
		Vec3 from = player.position();
		player.teleportTo(level, spot.x, spot.y, spot.z, Relative.ROTATION, 0.0F, 0.0F, false);
		player.resetFallDistance();
		fold.lastBlink = now;
		ctx.addAfterimage(from);
		ctx.markPath(from, spot);
		ctx.markEnd(spot);
		ctx.fx(Phase.EXTRA1, from, spot, 1.0F, (float) from.distanceTo(spot), 1, 10);
		ctx.sound(Phase.EXTRA1, spot);
		return true;
	}

	/** Pulls a point inside the fold: at least {@link #EDGE} within its radius and within its height. */
	private static Vec3 insideField(Vec3 point, Fold fold) {
		double dx = point.x - fold.centre.x;
		double dz = point.z - fold.centre.z;
		double distance = Math.sqrt(dx * dx + dz * dz);
		double limit = Math.max(1.0D, fold.radius - EDGE);
		double x = point.x;
		double z = point.z;
		if (distance > limit) {
			x = fold.centre.x + dx / distance * limit;
			z = fold.centre.z + dz / distance * limit;
		}
		double y = Math.max(fold.centre.y - HEIGHT, Math.min(fold.centre.y + HEIGHT, point.y));
		return new Vec3(x, y, z);
	}

	// ================================================================== helpers

	/** The caster's open fold in this world, or null when it closed. */
	private static @Nullable Fold active(CastContext ctx) {
		UUID id = ctx.caster().getUUID();
		Fold fold = FOLDS.get(id);
		if (fold == null) {
			return null;
		}
		if (fold.level != System.identityHashCode(ctx.level()) || ctx.level().getGameTime() > fold.end) {
			FOLDS.remove(id);
			return null;
		}
		return fold;
	}

	/** Drops folds of this world that ran out (the runner normally ends them). */
	private static void purge(ServerLevel level) {
		long now = level.getGameTime();
		int key = System.identityHashCode(level);
		Iterator<Fold> it = FOLDS.values().iterator();
		while (it.hasNext()) {
			Fold fold = it.next();
			if (fold.level == key && now > fold.end) {
				it.remove();
			}
		}
	}

	/** The field anchor when the runner has not placed it yet: the aim point, at most {@link #RANGE} from the eye. */
	private static Vec3 aimCentre(CastContext ctx) {
		Vec3 eye = ctx.eye();
		Vec3 offset = ctx.aim().subtract(eye);
		double length = offset.length();
		return length > RANGE ? eye.add(offset.scale(RANGE / length)) : ctx.aim();
	}

	/** The first standing height at or below {@code point} (read-only, ≤ 8 blocks, loaded chunks only); the point itself otherwise. */
	private static Vec3 ground(ServerLevel level, Vec3 point) {
		BlockPos start = BlockPos.containing(point);
		if (!level.isLoaded(start)) {
			return point;
		}
		for (int dy = 0; dy <= GROUND_SCAN; dy++) {
			BlockPos below = start.below(dy + 1);
			if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
				return new Vec3(point.x, below.getY() + 1.0D, point.z);
			}
		}
		return point;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
