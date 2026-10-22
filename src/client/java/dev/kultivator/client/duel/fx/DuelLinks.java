package dev.kultivator.client.duel.fx;

import dev.kultivator.client.duel.DuelClock;
import dev.kultivator.client.duel.anim.BladeTracker;
import dev.kultivator.duel.blade.BladeArcs;
import dev.kultivator.duel.blade.BladeSegment;
import dev.kultivator.duel.blade.DuelPose;
import dev.kultivator.duel.clash.SwingDir;
import dev.kultivator.duel.clash.SwingKind;
import dev.kultivator.duel.clash.WeaponClass;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The one place where the duel visuals call modules A and U (contracts C35.V2, C35.A2, C35.A4, C35.U3): the blade
 * actually drawn ({@code BladeTracker.segment}), the shared blade geometry ({@code BladeArcs.sample}) and the client's
 * estimate of the server tick ({@code DuelClock.serverTickNow}). Every call is guarded: a failure (an older module, a
 * missing class) logs once and the visuals fall back to the server's contact anchor and the client tick.
 */
public final class DuelLinks {

	/** A blade in world space: grip (hilt end of the edge) and tip. */
	public record Blade(Vec3 grip, Vec3 tip) {
		public Vec3 dir() {
			Vec3 d = tip.subtract(grip);
			double length = d.length();
			return length < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : d.scale(1.0D / length);
		}

		public double length() {
			return tip.distanceTo(grip);
		}

		/** The point at {@code t} (0 grip .. 1 tip). */
		public Vec3 at(double t) {
			return grip.add(tip.subtract(grip).scale(t));
		}
	}

	private static boolean trackerBroken;
	private static boolean arcsBroken;
	private static boolean clockBroken;
	private static long clientTicks;

	/** The blade of {@code entityId} as drawn in the last frames (≤ 10 frames old), or null. */
	public static @Nullable Blade drawn(int entityId, float partial) {
		if (trackerBroken) {
			return null;
		}
		try {
			BladeSegment segment = BladeTracker.segment(entityId, partial);
			return segment == null ? null : new Blade(segment.grip(), segment.tip());
		} catch (RuntimeException | LinkageError error) {
			trackerBroken = true;
			DuelFx.warn("BladeTracker", error);
			return null;
		}
	}

	/**
	 * The blade of {@code entity} from the shared pose table at {@code t01} of the pose of a swing ({@code kind},
	 * {@code dir} as payload codes) with weapon class {@code weapon}, or null when that is unknown.
	 */
	public static @Nullable Blade arc(Entity entity, int weapon, int kind, int dir, float t01) {
		if (arcsBroken) {
			return null;
		}
		try {
			WeaponClass[] classes = WeaponClass.values();
			SwingKind[] kinds = SwingKind.values();
			SwingDir[] dirs = SwingDir.values();
			if (weapon < 0 || weapon >= classes.length || kind < 0 || kind >= kinds.length || dir < 0 || dir >= dirs.length) {
				return null;
			}
			DuelPose pose = DuelPose.forSwing(kinds[kind], dirs[dir]);
			if (pose == null) {
				return null;
			}
			float yaw = entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
			float scale = entity instanceof LivingEntity living ? living.getScale() : 1.0F;
			boolean left = entity instanceof LivingEntity living && living.getMainArm() == HumanoidArm.LEFT;
			BladeSegment segment = BladeArcs.sample(pose, classes[weapon], Math.max(0.0F, Math.min(1.0F, t01)), entity.position(), yaw, scale, false,
					left);
			return segment == null ? null : new Blade(segment.grip(), segment.tip());
		} catch (RuntimeException | LinkageError error) {
			arcsBroken = true;
			DuelFx.warn("BladeArcs", error);
			return null;
		}
	}

	/** The client's estimate of the server tick now (falls back to a client tick count). */
	public static double serverTick(float partial) {
		if (!clockBroken) {
			try {
				return DuelClock.serverTickNow(partial);
			} catch (RuntimeException | LinkageError error) {
				clockBroken = true;
				DuelFx.warn("DuelClock", error);
			}
		}
		return clientTicks + partial;
	}

	static void tick() {
		clientTicks++;
	}

	private DuelLinks() {
	}
}
