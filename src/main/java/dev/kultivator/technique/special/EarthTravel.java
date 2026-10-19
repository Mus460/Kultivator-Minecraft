package dev.kultivator.technique.special;

import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Val;
import dev.kultivator.technique.spec.run.CastContext;
import dev.kultivator.technique.spec.run.EndReason;
import dev.kultivator.technique.spec.run.SpecialTechnique;
import dev.kultivator.util.LoadedPath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * {@code earth_travel} (DESIGN 22.2.7): the hidden ground travel of {@code worldwalking} (Langkah Penjelajah Bumi). The
 * caster sinks out of sight and travels 16→48 blocks toward the aim through the earth in {@link #TRAVEL_TICKS} ticks,
 * then emerges where the spec's exit PILLAR (anchor END) bursts up.
 * <ul>
 * <li>The exit is found before anything is spent ({@link #blocker}): the path must run through loaded chunks only
 * ({@code LoadedPath}) and the exit must be a safe standing spot ({@code CastContext.safeSpot}); otherwise the cast is
 * refused with a reason.</li>
 * <li>No block is ever read in a loop or changed: a handful of safe-spot lookups per cast, and the caster is moved only
 * by teleporting between safe spots (never into a portal or an unloaded chunk). The furrow is drawn by the client from
 * the TRAVEL event.</li>
 * <li>It sends the CAST event itself; the runner schedules the spec's timeline once when the cast starts: the SELF step
 * (invisibility on the way, no fall damage for 40 ticks) at once and the exit PILLAR after the trip. The fall distance
 * is also reset whenever the caster is moved.</li>
 * </ul>
 * Players only ({@code supportsDisciples} stays false).
 */
public final class EarthTravel implements SpecialTechnique {

	public static final String ID = "earth_travel";
	/** Travel distance 16→48 (an area value: base × A × mastery, at most the spec cap). */
	public static final Val DISTANCE = Val.of(16.0D, 48.0D);
	public static final double MAX_DISTANCE = 48.0D;
	/** Shorter trips than this are not worth sinking into the earth. */
	public static final double MIN_DISTANCE = 4.0D;
	/** The whole trip takes this many ticks ("in 8t"). */
	public static final int TRAVEL_TICKS = 8;
	/** How far back from the far point the exit search steps, and how often. */
	private static final double BACK_STEP = 2.0D;
	private static final int BACK_TRIES = 4;
	private static final String KEY_EXIT = "exit";
	private static final String KEY_FROM = "from";
	private static final String KEY_TRAVEL = "travelling";

	@Override
	public @Nullable Component blocker(CastContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null) {
			return Component.translatable("kultivator.technique.worldwalking.player_only");
		}
		if (player.isPassenger() || player.isSleeping() || player.isFallFlying() || player.getAbilities().flying) {
			return Component.translatable("kultivator.technique.worldwalking.not_grounded");
		}
		if (!player.onGround() && !player.isInWater()) {
			return Component.translatable("kultivator.technique.worldwalking.not_grounded");
		}
		Vec3 exit = findExit(ctx, player);
		if (exit == null) {
			return Component.translatable("kultivator.technique.worldwalking.no_exit");
		}
		putVec(ctx.state(), KEY_EXIT, exit);
		return null;
	}

	@Override
	public boolean cast(CastContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null) {
			return false;
		}
		CompoundTag state = ctx.state();
		Vec3 exit = state.contains(KEY_EXIT + "x") ? getVec(state, KEY_EXIT) : findExit(ctx, player);
		if (exit == null) {
			return false;
		}
		Vec3 from = player.position();
		putVec(state, KEY_EXIT, exit);
		putVec(state, KEY_FROM, from);
		state.putBoolean(KEY_TRAVEL, true);
		ctx.markPath(from, exit);
		ctx.markEnd(exit);
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, TRAVEL_TICKS + 4, 0, false, false, true));
		player.resetFallDistance();
		// The handler sends the CAST and TRAVEL events; the runner schedules the spec's timeline once when the cast starts
		// (the SELF step now, the exit PILLAR with anchor END when the trip is over). Each fx call also plays its sounds.
		float length = (float) from.distanceTo(exit);
		ctx.fx(Phase.CAST, from, exit, 1.0F, length, 1, TRAVEL_TICKS);
		ctx.fx(Phase.TRAVEL, from, exit, 1.0F, length, 0, TRAVEL_TICKS);
		return true;
	}

	@Override
	public void tick(CastContext ctx) {
		ServerPlayer player = ctx.player();
		CompoundTag state = ctx.state();
		if (player == null || !state.getBooleanOr(KEY_TRAVEL, false)) {
			return;
		}
		int age = ctx.age();
		Vec3 from = getVec(state, KEY_FROM);
		Vec3 exit = getVec(state, KEY_EXIT);
		if (age >= TRAVEL_TICKS) {
			emerge(ctx, player, exit);
			return;
		}
		// Racing through the earth: hop between safe spots along the way (skipped where there is none).
		Vec3 along = from.add(exit.subtract(from).scale(Math.max(0, age) / (double) TRAVEL_TICKS));
		Vec3 spot = ctx.safeSpot(along);
		if (spot != null && spot.distanceToSqr(player.position()) > 0.25D) {
			// racing under the ground as real motion (S16); a spot far off (a cliff) is reached with one safe hop
			Vec3 delta = spot.subtract(player.position());
			if (delta.lengthSqr() > 36.0D) {
				moveTo(player, spot);
			} else {
				player.setDeltaMovement(delta);
				player.syncVelocity = true;
			}
		}
		player.resetFallDistance();
	}

	@Override
	public void end(CastContext ctx, EndReason reason) {
		ServerPlayer player = ctx.player();
		CompoundTag state = ctx.state();
		if (player == null || !state.getBooleanOr(KEY_TRAVEL, false)) {
			return;
		}
		if (reason == EndReason.DEATH || reason == EndReason.LOGOUT || reason == EndReason.DIMENSION) {
			state.putBoolean(KEY_TRAVEL, false);
			return;
		}
		// The timeline ended before the last travel tick: finish the trip so the caster never stays half-way.
		emerge(ctx, player, getVec(state, KEY_EXIT));
	}

	/** Steps out at the exit when it is still safe (otherwise stays at the last safe spot reached). */
	private static void emerge(CastContext ctx, ServerPlayer player, Vec3 exit) {
		ctx.state().putBoolean(KEY_TRAVEL, false);
		Vec3 spot = ctx.safeSpot(exit);
		if (spot != null) {
			moveTo(player, spot);
		}
		player.resetFallDistance();
	}

	private static void moveTo(ServerPlayer player, Vec3 spot) {
		ServerLevel level = player.level();
		player.teleportTo(level, spot.x, spot.y, spot.z, Relative.ROTATION, 0.0F, 0.0F, false);
		player.resetFallDistance();
	}

	/**
	 * The exit: toward the aim (or along the look when the aim is close), 16→48 blocks, cut at the first unloaded chunk,
	 * then the first safe standing spot searching back toward the caster. Null when none (the cast is refused).
	 */
	static @Nullable Vec3 findExit(CastContext ctx, ServerPlayer player) {
		ServerLevel level = ctx.level();
		Vec3 from = player.position();
		double reach = ctx.area(DISTANCE, MAX_DISTANCE);
		if (!(reach >= MIN_DISTANCE)) {
			return null;
		}
		Vec3 toAim = ctx.aim().subtract(from);
		double aimDistance = toAim.horizontalDistance();
		Vec3 dir;
		double distance;
		double rise;
		if (aimDistance >= MIN_DISTANCE) {
			dir = new Vec3(toAim.x / aimDistance, 0.0D, toAim.z / aimDistance);
			distance = Math.min(reach, aimDistance);
			rise = toAim.y * (distance / aimDistance);
		} else {
			Vec3 look = ctx.look();
			double flat = look.horizontalDistance();
			if (flat < 1.0E-4D) {
				return null;
			}
			dir = new Vec3(look.x / flat, 0.0D, look.z / flat);
			distance = reach;
			rise = 0.0D;
		}
		Vec3 far = new Vec3(from.x + dir.x * distance, from.y + rise, from.z + dir.z * distance);
		Vec3 cut = LoadedPath.clip(level, from, far);
		double travelled = cut.subtract(from).horizontalDistance();
		for (int i = 0; i < BACK_TRIES; i++) {
			double back = i * BACK_STEP;
			if (travelled - back < MIN_DISTANCE) {
				break;
			}
			Vec3 point = new Vec3(cut.x - dir.x * back, cut.y, cut.z - dir.z * back);
			Vec3 spot = ctx.safeSpot(point);
			if (spot == null && Math.abs(point.y - from.y) > 1.0D) {
				// The aim line ran through a slope: also try the caster's own height.
				spot = ctx.safeSpot(new Vec3(point.x, from.y, point.z));
			}
			if (spot != null && spot.subtract(from).horizontalDistance() >= MIN_DISTANCE) {
				return spot;
			}
		}
		return null;
	}

	private static void putVec(CompoundTag tag, String key, Vec3 value) {
		tag.putDouble(key + "x", value.x);
		tag.putDouble(key + "y", value.y);
		tag.putDouble(key + "z", value.z);
	}

	private static Vec3 getVec(CompoundTag tag, String key) {
		return new Vec3(tag.getDoubleOr(key + "x", 0.0D), tag.getDoubleOr(key + "y", 0.0D), tag.getDoubleOr(key + "z", 0.0D));
	}
}
