package dev.kultivator.client.aura.high;

import dev.kultivator.Kultivator;
import dev.kultivator.client.anim.model.Anim;
import dev.kultivator.client.anim.model.AnimStep;
import dev.kultivator.client.anim.model.ModelHandle;
import dev.kultivator.client.anim.model.ModelRequest;
import dev.kultivator.client.aura.AuraRig;
import dev.kultivator.client.aura.AuraSubject;
import dev.kultivator.technique.spec.dsl.FxLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared helpers of module H (DESIGN 31.4.6-31.4.9): textures, deterministic waves, tilted circles and the prop requests
 * of the high realms. Every shape here is a pure function of the aura clock ({@link AuraRig#time()}) and the eased stage
 * values; nothing is random and nothing breathes by size.
 */
final class HighKit {

	/** Props live until the driver or the engine ends them (an hour and more of meditation). */
	static final int PROP_LIFE = 20 * 60 * 60 * 6;
	/** Props grow in once over this many ticks (31.2.2). */
	static final int GROW_TICKS = 20;
	/** Stage values ease to their new size once over this many ticks. */
	static final int EASE = 20;
	static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
	static final float TAU = (float) (Math.PI * 2.0D);

	/** {@code kultivator:textures/misc/aura_high/<name>.png} (made by tools/gen_aura_high_assets.py). */
	static Identifier tex(String name) {
		return Kultivator.id("textures/misc/aura_high/" + name + ".png");
	}

	// ================================================================== waves and easing

	/** 0..1..0 once every {@code period} ticks (a cosine; deterministic "breathing" for alpha only). */
	static float wave(float time, float period, float phase) {
		return 0.5F - 0.5F * Mth.cos((time + phase) / period * TAU);
	}

	static float easeOut(float p) {
		float q = 1.0F - Mth.clamp(p, 0.0F, 1.0F);
		return 1.0F - q * q * q;
	}

	static float easeIn(float p) {
		float q = Mth.clamp(p, 0.0F, 1.0F);
		return q * q;
	}

	static float smooth(float p) {
		float q = Mth.clamp(p, 0.0F, 1.0F);
		return q * q * (3.0F - 2.0F * q);
	}

	/** An element that appears at {@code fromStage}: its strength (0..1) eased in once, and out again if the stage drops. */
	static float appear(AuraRig rig, String key, AuraSubject s, int fromStage) {
		return rig.easeFrom(key, 0.0F, s.stage() >= fromStage ? 1.0F : 0.0F, EASE);
	}

	/** A slow hue cycle (the ascended immortal's shifting rainbow light), {@code period} ticks per turn. */
	static int hue(float time, float period, float offset, float saturation) {
		float h = ((time + offset) % period) / period;
		return Mth.hsvToRgb(h < 0.0F ? h + 1.0F : h, saturation, 1.0F) & 0xFFFFFF;
	}

	// ================================================================== geometry

	/** The normal of a ring tilted {@code tiltDeg} from flat toward {@code toward} (a horizontal unit vector). */
	static Vec3 tilted(Vec3 toward, double tiltDeg) {
		double t = Math.toRadians(tiltDeg);
		return new Vec3(toward.x * Math.sin(t), Math.cos(t), toward.z * Math.sin(t));
	}

	/** A horizontal unit vector at Minecraft yaw {@code yawDeg} (0 faces +z). */
	static Vec3 dir(double yawDeg) {
		double y = Math.toRadians(yawDeg);
		return new Vec3(-Math.sin(y), 0.0D, Math.cos(y));
	}

	/** Two unit axes spanning the plane whose normal is {@code n} (fixed for a given normal, so circles never flip). */
	static Vec3[] basis(Vec3 n) {
		Vec3 nn = n.lengthSqr() < 1.0E-8D ? UP : n.normalize();
		Vec3 helper = Math.abs(nn.y) < 0.95D ? UP : new Vec3(1.0D, 0.0D, 0.0D);
		Vec3 u = nn.cross(helper).normalize();
		Vec3 v = nn.cross(u);
		return new Vec3[]{u, v};
	}

	/** The point at {@code angleRad} on the circle of {@code radius} about {@code c} in the plane of {@code basis}. */
	static Vec3 onCircle(Vec3 c, Vec3[] basis, double radius, double angleRad) {
		double cs = Math.cos(angleRad) * radius;
		double sn = Math.sin(angleRad) * radius;
		return c.add(basis[0].x * cs + basis[1].x * sn, basis[0].y * cs + basis[1].y * sn, basis[0].z * cs + basis[1].z * sn);
	}

	/** A world offset turned into the body frame of {@link ModelRequest#followOffset()} (x left, y up, z forward). */
	static Vec3 world(AuraSubject s, Vec3 local) {
		return local.yRot((float) Math.toRadians(-s.bodyYaw()));
	}

	/** The height of a point above the subject's feet. */
	static double above(AuraSubject s, Vec3 p) {
		return p.y - s.feet().y;
	}

	// ================================================================== props

	/**
	 * A prop riding the subject (follow with the body yaw) at {@code local} (x left, y up, z forward, blocks), turned
	 * {@code yaw}/{@code pitch}/{@code roll} from the body's facing (its model front faces the body's front at 0), growing
	 * in once over 20 ticks, priority DETAIL so techniques always win; null when the subject's entity is gone.
	 */
	static @Nullable ModelRequest follow(AuraSubject s, String id, float scale, Vec3 local, float yaw, float pitch, float roll,
			AnimStep... extra) {
		if (s.entity() == null || scale <= 0.0F) {
			return null;
		}
		List<AnimStep> anims = new ArrayList<>(1 + extra.length);
		anims.add(AnimStep.of(Anim.GROW, 0, GROW_TICKS, 0.1F, 0.0F));
		for (AnimStep step : extra) {
			anims.add(step);
		}
		Vec3 at = s.feet().add(world(s, local));
		return new ModelRequest(id, at, scale, yaw, pitch, roll, PROP_LIFE, anims, s.entityId(), local, true, true, -1,
				FxLayer.Priority.DETAIL);
	}

	/** A steady turn about the model's own axis (0 Y, 2 Z) at {@code degPerTick}, for the prop's whole life. */
	static AnimStep spin(float degPerTick, int axis) {
		return AnimStep.of(Anim.SPIN, 0, PROP_LIFE, degPerTick, axis);
	}

	/** Keeps a live prop's follow offset in step with an eased height (re-sent only when it moved, so it glides). */
	static void keepOffset(AuraRig rig, String key, AuraSubject s, Vec3 local) {
		ModelHandle handle = rig.propIfAlive(key);
		if (handle == null || s.entity() == null) {
			return;
		}
		Map<String, Vec3> last = rig.data("high.offsets", HashMap::new);
		Vec3 before = last.get(key);
		if (before == null || before.distanceToSqr(local) > 1.0E-6D) {
			last.put(key, local);
			if (before != null) {
				handle.follow(s.entityId(), local, true);
			}
		}
	}

	/** Forgets the offset kept for {@code key} (its prop was made again). */
	static void forgetOffset(AuraRig rig, String key) {
		Map<String, Vec3> last = rig.data("high.offsets", HashMap::new);
		last.remove(key);
	}

	private HighKit() {
	}
}
