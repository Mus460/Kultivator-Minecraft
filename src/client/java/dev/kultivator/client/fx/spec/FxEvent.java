package dev.kultivator.client.fx.spec;

import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One decoded {@code TechFx} event as every layer of it sees it (DESIGN 22.4): the spec and phase, the two points and the
 * direction between them, the server's radius, length and duration, the caster's visual tier with the escalation
 * factors of 22.4.7 (sizes × (1 + 0.15 tier), counts × (1 + 0.10 tier)), the viewer's particle factor (quality, others'
 * casts, low load, echo), the shared per-event particle cap and the event's phantom display allowance.
 */
final class FxEvent {

	final TechniqueSpec spec;
	final Phase phase;
	final Vec3 pos;
	final Vec3 target;
	/** Unit direction from pos to target, or the caster's look when the points are the same. */
	final Vec3 dir;
	final boolean hasTarget;
	final float radius;
	final float length;
	final int tier;
	final int mastery;
	final int flags;
	final int count;
	final int seed;
	final int casterId;
	final int duration;
	final float sizeF;
	final float countF;
	/** Particle multiplier for this viewer (quality × others × low load × echo). */
	final float viewScale;
	final boolean own;
	final boolean displaysAllowed;
	/** Particles this event may still draw (shared by its layers). */
	int particlesLeft;
	/** Phantom displays this event may still create. */
	int displaysLeft;
	private Vec3 lastCaster;

	FxEvent(TechniqueSpec spec, Payloads.TechFx payload, float qualityFactor, float othersFactor, boolean displays, int displayAllowance,
			int eventCap) {
		this.spec = spec;
		this.phase = Phase.byOrdinal(payload.phase());
		this.pos = new Vec3(payload.x(), payload.y(), payload.z());
		Vec3 second = new Vec3(payload.tx(), payload.ty(), payload.tz());
		this.hasTarget = second.distanceToSqr(pos) > 1.0E-4D;
		this.target = hasTarget ? second : pos;
		this.casterId = payload.caster();
		Minecraft minecraft = Minecraft.getInstance();
		Entity caster = minecraft.level == null ? null : minecraft.level.getEntity(casterId);
		Vec3 direction;
		if (hasTarget) {
			direction = second.subtract(pos).normalize();
		} else if (caster != null) {
			direction = caster.getLookAngle();
		} else {
			direction = new Vec3(0.0D, 0.0D, 1.0D);
		}
		this.dir = direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : direction;
		this.radius = Math.max(0.0F, payload.radius());
		this.length = Math.max(0.0F, payload.length());
		this.tier = Math.max(0, Math.min(5, payload.gradeTier()));
		this.mastery = Math.max(0, Math.min(4, payload.mastery()));
		this.flags = payload.flags();
		this.count = payload.count();
		this.seed = payload.seed();
		this.duration = Math.max(0, payload.duration());
		this.sizeF = 1.0F + 0.15F * tier;
		this.countF = 1.0F + 0.10F * tier;
		this.own = minecraft.player != null && casterId == minecraft.player.getId();
		float scale = qualityFactor * othersFactor;
		if (has(Payloads.TechFx.FLAG_LOW_LOAD)) {
			scale *= 0.5F;
		}
		if (has(Payloads.TechFx.FLAG_ECHO)) {
			scale *= 0.6F;
		}
		this.viewScale = scale;
		this.displaysAllowed = displays && !has(Payloads.TechFx.FLAG_LOW_LOAD);
		this.displaysLeft = displaysAllowed ? displayAllowance : 0;
		this.particlesLeft = eventCap;
		this.lastCaster = caster == null ? pos : caster.position();
	}

	boolean has(int flag) {
		return (flags & flag) != 0;
	}

	/** The caster entity right now, or null when it is gone. */
	@Nullable Entity caster() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? null : minecraft.level.getEntity(casterId);
	}

	/** The caster's feet now (or where it was last seen). */
	Vec3 casterPos() {
		Entity caster = caster();
		if (caster != null && !caster.isRemoved()) {
			lastCaster = caster.position();
		}
		return lastCaster;
	}

	/** Distance between the two points (at least 1). */
	double span() {
		return Math.max(1.0D, target.distanceTo(pos));
	}
}
