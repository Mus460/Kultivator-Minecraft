package dev.kultivator.technique.spec.run;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Real movement for DASH, leaps and ride arts (DESIGN 27.1 S16): instead of one teleport the caster travels the checked
 * path over a few ticks, so every viewer (the caster's own camera included) sees the body move. The end spot is checked
 * first (read-only), exactly as the instant version did; each tick a player gets the step as its velocity (the server's
 * motion packet, mcsrc {@code ServerEntity}: {@code syncVelocity} sends {@code ClientboundSetEntityMotionPacket}) and a
 * disciple or mob moves with collisions ({@code Entity.move(MoverType.SELF, ...)}); the last tick snaps to the checked
 * spot when the body drifted, so nobody ever ends in a wall. Never a block change.
 */
final class Moves {

	/** Ticks a leap takes (a parabola; landing steps run after it). */
	static final int LEAP_TICKS = 9;
	/** At most this many blocks per tick (above it the vanilla "moved too quickly" check could object). */
	private static final double MAX_STEP = 5.0D;

	/** Ticks a dash of {@code distance} blocks takes: the authored ticks, at least distance / 4, between 2 and 10. */
	static int dashTicks(int authored, double distance) {
		if (authored > 10) {
			return Math.min(40, authored);
		}
		if (authored <= 1) {
			return 1;
		}
		return (int) Math.max(2L, Math.min(10L, Math.max(authored, (long) Math.ceil(distance / 4.0D))));
	}

	/**
	 * Moves the caster from {@code from} to the checked {@code spot} over {@code ticks} ticks, rising {@code lift} blocks
	 * at the middle (0 = a straight line). {@code yaw} (when not null) is the facing at the end; {@code landed} runs on the
	 * last tick after the final snap. The cast stays alive until then.
	 */
	static void glide(Caster cast, Vec3 from, Vec3 spot, int ticks, double lift, @Nullable Float yaw, @Nullable Runnable landed) {
		if (ticks <= 1) {
			if (yaw != null) {
				cast.teleport(spot, yaw, cast.caster.getXRot());
			} else {
				cast.teleport(spot);
			}
			if (landed != null) {
				landed.run();
			}
			return;
		}
		cast.activity(1);
		for (int i = 1; i <= ticks; i++) {
			int step = i;
			cast.schedule(i - 1, () -> {
				if (!cast.casterValid()) {
					cast.activity(-1);
					return;
				}
				double t = step / (double) ticks;
				Vec3 want = from.lerp(spot, t).add(0.0D, lift * 4.0D * t * (1.0D - t), 0.0D);
				Vec3 delta = want.subtract(cast.caster.position());
				if (delta.length() > MAX_STEP) {
					delta = delta.normalize().scale(MAX_STEP);
				}
				if (step < ticks) {
					if (cast.caster instanceof ServerPlayer) {
						cast.caster.setDeltaMovement(delta);
						cast.caster.syncVelocity = true;
					} else {
						cast.caster.move(MoverType.SELF, delta);
						cast.caster.setDeltaMovement(Vec3.ZERO);
						cast.caster.needsSync = true;
					}
					cast.caster.resetFallDistance();
					return;
				}
				// the last tick: stop, and snap to the checked spot when the body is not already there
				cast.caster.setDeltaMovement(Vec3.ZERO);
				if (cast.caster instanceof ServerPlayer) {
					cast.caster.syncVelocity = true;
				}
				if (cast.caster.position().distanceTo(spot) > 0.6D || yaw != null) {
					if (yaw != null) {
						cast.teleport(spot, yaw, cast.caster.getXRot());
					} else {
						cast.teleport(spot);
					}
				}
				cast.caster.resetFallDistance();
				cast.activity(-1);
				if (landed != null) {
					landed.run();
				}
			});
		}
	}

	/** The yaw that faces {@code to} from {@code from} (Minecraft degrees). */
	static float yawTo(Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		return (float) (Math.atan2(d.z, d.x) * (180.0D / Math.PI)) - 90.0F;
	}

	private Moves() {
	}
}
