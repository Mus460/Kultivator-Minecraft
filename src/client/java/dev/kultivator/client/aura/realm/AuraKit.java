package dev.kultivator.client.aura.realm;

import dev.kultivator.client.anim.model.Anim;
import dev.kultivator.client.anim.model.AnimStep;
import dev.kultivator.client.anim.model.ModelHandle;
import dev.kultivator.client.anim.model.ModelRequest;
import dev.kultivator.client.aura.AuraSubject;
import dev.kultivator.technique.spec.dsl.FxLayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Small shared helpers of the realm drivers: prop requests that ride the cultivator (grow in once over 20 ticks, budget
 * priority DETAIL so techniques win, lit full bright), the conversion of a world offset into the rider's own frame, and
 * a few deterministic curves. Every prop goes through {@code ModelFx} (which keys every display transform through
 * {@code DisplayKey}) and its ItemStack is made lazily by {@code ModelFx.stack}.
 */
final class AuraKit {

	/** A prop lives this long at most (30 minutes); the rig asks for a new one if an aura outlives it. */
	static final int PROP_LIFE = 20 * 60 * 30;
	/** Props grow in over this many ticks (once). */
	static final int GROW_TICKS = 20;
	static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

	/** The follow offset (in the rider's own frame, as {@code ModelFx} turns it by the body yaw) of a world point. */
	static Vec3 local(AuraSubject s, Vec3 world) {
		Vec3 offset = world.subtract(s.feet());
		return offset.yRot((float) Math.toRadians(s.bodyYaw()));
	}

	/**
	 * A prop riding the subject at the world point {@code at} (kept in the body's frame when {@code turnWithBody}, else in
	 * world axes), growing in once; {@code extra} animations are added (a slow SPIN).
	 */
	static ModelRequest riding(AuraSubject s, String model, Vec3 at, float scale, boolean turnWithBody, AnimStep... extra) {
		List<AnimStep> anims = new ArrayList<>(1 + extra.length);
		anims.add(AnimStep.of(Anim.GROW, 0, GROW_TICKS, 0.05F, 0.0F));
		for (AnimStep step : extra) {
			anims.add(step);
		}
		Vec3 offset = turnWithBody ? local(s, at) : at.subtract(s.feet());
		return new ModelRequest(model, at, scale, 0.0F, 0.0F, 0.0F, PROP_LIFE, anims, s.entityId(), offset, turnWithBody, true, -1,
				FxLayer.Priority.DETAIL);
	}

	/** Moves a riding prop to a new world point (re-keyed every tick, so a slow change glides). */
	static void ride(@Nullable ModelHandle handle, AuraSubject s, Vec3 at, boolean turnWithBody) {
		if (handle != null) {
			handle.follow(s.entityId(), turnWithBody ? local(s, at) : at.subtract(s.feet()), turnWithBody);
		}
	}

	/** 0..1 breathing: {@code 0.5 − 0.5 cos(2π t / period)}. */
	static float breath(float t, float period) {
		return (float) (0.5D - 0.5D * Math.cos(Math.PI * 2.0D * (t % period) / period));
	}

	/** A unit axis tilted {@code tiltDeg} from the vertical toward the horizontal direction at {@code azimuthDeg}. */
	static Vec3 tilted(double tiltDeg, double azimuthDeg) {
		double tilt = Math.toRadians(tiltDeg);
		double az = Math.toRadians(azimuthDeg);
		double h = Math.sin(tilt);
		return new Vec3(h * Math.cos(az), Math.cos(tilt), h * Math.sin(az));
	}

	/** A point on a horizontal circle around {@code c}. */
	static Vec3 circle(Vec3 c, double radius, double angleRad, double up) {
		return c.add(Math.cos(angleRad) * radius, up, Math.sin(angleRad) * radius);
	}

	private AuraKit() {
	}
}
