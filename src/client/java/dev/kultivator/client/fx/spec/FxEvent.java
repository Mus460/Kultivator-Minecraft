package dev.kultivator.client.fx.spec;

import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One decoded {@code TechFx} event as every layer of it sees it (DESIGN 22.4, v2 in 27.4): the spec and phase, the two
 * points and the direction between them, the server's radius, length and duration, the target entity, the group index,
 * the aux value, the caster's visual tier with the escalation factors of 22.4.7 (sizes × (1 + 0.15 tier), counts ×
 * (1 + 0.10 tier)), the viewer's particle factor (quality, others' and disciples' casts, low load, echo), the shared
 * per-event particle cap and the event's phantom display allowance (per layer since the polish pass).
 * <p>
 * Polish S7: every float is sanitised ({@link #valid} rejects an event whose points are not finite); S9: a FOLLOW event's
 * POS layers ride the caster, and in one's own first-person view the start of a line leaves the hand, not the eye.
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
	/** The victim / target / bound entity id (−1 none). */
	final int targetId;
	/** Place in a multi-point group (0-based) and the group size; {@code index} −1 with {@code of} > 1 = spread by the client. */
	final int index;
	final int of;
	final int aux;
	final float sizeF;
	final float countF;
	/** Particle multiplier for this viewer (quality × others × low load × echo). */
	final float viewScale;
	final boolean own;
	/** A disciple's (or anyone else's) cast: no flash, vignette or sky, shake capped (S19). */
	final boolean foreign;
	final boolean displaysAllowed;
	/** Particles this event may still draw (shared by its layers). */
	int particlesLeft;
	/** Phantom displays one layer of this event may create (per layer since S13). */
	final int displayAllowance;
	/** Displays this whole event may still create (3 × the layer allowance). */
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
		this.dir = direction.lengthSqr() < 1.0E-6D || !finite(direction) ? new Vec3(0.0D, 0.0D, 1.0D) : direction;
		this.radius = clean(payload.radius(), 512.0F);
		this.length = clean(payload.length(), 512.0F);
		this.tier = Math.max(0, Math.min(5, payload.gradeTier()));
		this.mastery = Math.max(0, Math.min(4, payload.mastery()));
		this.flags = payload.flags() & 0xFFFF;
		this.count = Math.max(0, payload.count());
		this.seed = payload.seed();
		this.duration = Math.max(0, payload.duration());
		this.targetId = payload.target();
		this.index = payload.index();
		this.of = Math.max(1, payload.of());
		this.aux = payload.aux();
		this.sizeF = 1.0F + 0.15F * tier;
		this.countF = 1.0F + 0.10F * tier;
		this.own = minecraft.player != null && casterId == minecraft.player.getId();
		// players, disciples and monsters other than oneself
		this.foreign = !own;
		float scale = qualityFactor * othersFactor;
		if (has(Payloads.TechFx.FLAG_LOW_LOAD)) {
			scale *= 0.5F;
		}
		if (has(Payloads.TechFx.FLAG_ECHO)) {
			scale *= 0.6F;
		}
		if (has(Payloads.TechFx.FLAG_DOT)) {
			// a damage-over-time tick: light layers only
			scale *= 0.35F;
		}
		this.viewScale = scale;
		this.displaysAllowed = displays && !has(Payloads.TechFx.FLAG_LOW_LOAD) && !has(Payloads.TechFx.FLAG_DOT);
		this.displayAllowance = displaysAllowed ? displayAllowance : 0;
		this.displaysLeft = displaysAllowed ? displayAllowance * 3 : 0;
		this.particlesLeft = eventCap;
		this.lastCaster = caster == null ? pos : caster.position();
	}

	/** False for an event with a non-finite point (S7: such an event is skipped). */
	static boolean valid(Payloads.TechFx payload) {
		return Double.isFinite(payload.x()) && Double.isFinite(payload.y()) && Double.isFinite(payload.z()) && Double.isFinite(payload.tx())
				&& Double.isFinite(payload.ty()) && Double.isFinite(payload.tz());
	}

	private static float clean(float value, float max) {
		return Float.isFinite(value) ? Math.max(0.0F, Math.min(max, value)) : 0.0F;
	}

	private static boolean finite(Vec3 v) {
		return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
	}

	boolean has(int flag) {
		return (flags & flag) != 0;
	}

	/** True for one event of a group the client spreads itself ({@code index} −1, {@code of} > 1, ring radius aux / 100). */
	boolean clientRing() {
		return index < 0 && of > 1;
	}

	/** Copies the client places for a client-ring event (0 otherwise, ≤ 8). */
	int clientRingCopies() {
		return clientRing() ? Math.min(8, of) : 0;
	}

	/** True for the cast's one climax event (S12). */
	boolean climax() {
		return has(Payloads.TechFx.FLAG_CLIMAX);
	}

	/** True when POS layers of this event ride the caster (FLAG_FOLLOW). */
	boolean follow() {
		return has(Payloads.TechFx.FLAG_FOLLOW);
	}

	/** The caster entity right now, or null when it is gone. */
	@Nullable Entity caster() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? null : minecraft.level.getEntity(casterId);
	}

	/** The target entity of this event (v2 {@code target}), or null. */
	@Nullable Entity targetEntity() {
		if (targetId < 0) {
			return null;
		}
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? null : minecraft.level.getEntity(targetId);
	}

	/** The caster's feet now (or where it was last seen). */
	Vec3 casterPos() {
		Entity caster = caster();
		if (caster != null && !caster.isRemoved()) {
			lastCaster = caster.position();
		}
		return lastCaster;
	}

	/** The caster's horizontal body yaw in degrees (players: the view yaw), or the event direction's. */
	float casterYaw() {
		Entity caster = caster();
		if (caster instanceof LivingEntity living && !(caster instanceof net.minecraft.world.entity.player.Player)) {
			return living.yBodyRot;
		}
		if (caster != null) {
			return caster.getYRot();
		}
		return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
	}

	/** True in one's own first-person view (lines then start at the hand, S9). */
	boolean firstPerson() {
		Minecraft minecraft = Minecraft.getInstance();
		return own && minecraft.options.getCameraType().isFirstPerson() && minecraft.getCameraEntity() == minecraft.player;
	}

	/** Where a hand-launched line starts for this viewer: the main hand in first person, else {@code start}. */
	Vec3 handStart(Vec3 start) {
		if (!firstPerson()) {
			return start;
		}
		Entity caster = caster();
		if (caster instanceof LivingEntity living) {
			return dev.kultivator.client.fx.quad.BeamQuad.handOf(living, true);
		}
		return start;
	}

	/** The caster's main hand point (third person), or the event position. */
	Vec3 hand() {
		Entity caster = caster();
		if (caster instanceof LivingEntity living) {
			return dev.kultivator.client.fx.quad.BeamQuad.handOf(living, firstPerson());
		}
		return pos;
	}

	/** Distance between the two points (at least 1). */
	double span() {
		return Math.max(1.0D, target.distanceTo(pos));
	}
}
