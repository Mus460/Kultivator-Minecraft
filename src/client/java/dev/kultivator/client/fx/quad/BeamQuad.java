package dev.kultivator.client.fx.quad;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A beam as geometry (polish S14, LASER and BEAM_CORE): a white-hot core strip in a soft coloured halo that shoots out
 * over its first ticks, pulses its width, carries a turning two-strand spiral sheath (Normal and up) and flares at both
 * ends. The start may ride the caster's hand so the beam leaves the palm while the caster turns.
 */
public final class BeamQuad extends QuadFx.Quad {

	private Vec3 from;
	private Vec3 to;
	private final float width;
	private final int rgb;
	private final int core;
	private final boolean sheath;
	private int handOf = -1;
	private float shoot = 3.0F;
	private float length;

	public BeamQuad(Vec3 from, Vec3 to, float width, int rgb, int core, int life, boolean sheath) {
		super(QuadFx.Kind.BEAM, life);
		this.from = from;
		this.to = to;
		this.width = Math.max(0.05F, width);
		this.rgb = rgb & 0xFFFFFF;
		this.core = core & 0xFFFFFF;
		this.sheath = sheath;
		this.length = (float) from.distanceTo(to);
	}

	/** The start rides {@code casterId}'s main hand (re-aimed along its look every tick, keeping the length). */
	public BeamQuad fromHand(int casterId) {
		this.handOf = casterId;
		return this;
	}

	/** Ticks the head takes to reach the end (0 = at once). */
	public BeamQuad shoot(float ticks) {
		this.shoot = Math.max(0.0F, ticks);
		return this;
	}

	@Override
	protected void tick() {
		if (handOf == -1) {
			return;
		}
		Entity caster = entity(handOf);
		if (caster instanceof LivingEntity living && living.isAlive()) {
			Vec3 hand = handOf(living, Minecraft.getInstance().getCameraEntity() == living && Minecraft.getInstance().options.getCameraType().isFirstPerson());
			Vec3 look = living.getLookAngle();
			from = hand;
			to = hand.add(look.scale(length));
		}
	}

	/** The main-hand point of a living caster (first person: just below and right of the eye). */
	public static Vec3 handOf(LivingEntity living, boolean firstPerson) {
		Vec3 eye = living.getEyePosition();
		Vec3 look = living.getLookAngle();
		Vec3 side = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
		side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
		boolean right = living.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
		double s = right ? 1.0D : -1.0D;
		if (firstPerson) {
			return eye.add(side.scale(0.35D * s)).add(0.0D, -0.35D, 0.0D).add(look.scale(0.8D));
		}
		return eye.add(side.scale(0.38D * s)).add(0.0D, -0.5D, 0.0D).add(look.scale(0.55D));
	}

	@Override
	protected void render(QuadFx.Frame frame, float at) {
		float a = fade(at, 1.0F, Math.min(8.0F, life * 0.35F));
		if (a <= 0.01F) {
			return;
		}
		Vec3 delta = to.subtract(from);
		double len = delta.length();
		if (len < 0.05D) {
			return;
		}
		float head = shoot <= 0.0F ? 1.0F : Math.min(1.0F, at / shoot);
		Vec3 end = from.add(delta.scale(head));
		int pieces = (int) Math.max(1, Math.min(24, len / 3.0D));
		if (!frame.take(pieces * (sheath ? 6 : 3) + 4)) {
			return;
		}
		float pulse = 1.0F + 0.12F * (float) Math.sin(at * 1.3F);
		float w = width * pulse * (0.6F + 0.4F * Math.min(1.0F, at / 3.0F));
		for (int i = 0; i < pieces; i++) {
			Vec3 p0 = from.lerp(end, i / (double) pieces);
			Vec3 p1 = from.lerp(end, (i + 1) / (double) pieces);
			float u0 = (float) (i / (double) pieces * len * 0.25D - at * 0.4D);
			float u1 = (float) ((i + 1) / (double) pieces * len * 0.25D - at * 0.4D);
			frame.segment(frame.soft, p0, p1, w * 3.2F, w * 3.2F, QuadFx.argb(0.35F * a, rgb), QuadFx.argb(0.35F * a, rgb), u0, u1);
			frame.segment(frame.strip, p0, p1, w * 1.6F, w * 1.6F, QuadFx.argb(0.9F * a, QuadFx.mix(rgb, core, 0.4F)),
					QuadFx.argb(0.9F * a, QuadFx.mix(rgb, core, 0.4F)), u0, u1);
			frame.segment(frame.add, p0, p1, w * 0.35F, w * 0.35F, QuadFx.argb(0.8F * a, 0xFFFFFF), QuadFx.argb(0.8F * a, 0xFFFFFF), 0.0F, 1.0F);
		}
		if (sheath) {
			Vec3 axis = delta.scale(1.0D / len);
			Vec3 side = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
			side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
			Vec3 lift = side.cross(axis).normalize();
			int steps = pieces * 3;
			for (int strand = 0; strand < 2; strand++) {
				Vec3 prev = null;
				for (int i = 0; i <= steps; i++) {
					double t = i / (double) steps * head;
					double angle = t * len * 1.4D + at * 0.6D + strand * Math.PI;
					double r = w * 1.4D;
					Vec3 p = from.add(delta.scale(t)).add(side.scale(Math.cos(angle) * r)).add(lift.scale(Math.sin(angle) * r));
					if (prev != null) {
						frame.segment(frame.add, prev, p, w * 0.22F, w * 0.22F, QuadFx.argb(0.45F * a, core), QuadFx.argb(0.45F * a, core), 0.0F, 1.0F);
					}
					prev = p;
				}
			}
		}
		frame.billboard(frame.disc, from, w * 2.4F, QuadFx.argb(0.8F * a, QuadFx.mix(rgb, 0xFFFFFF, 0.5F)));
		frame.billboard(frame.disc, end, w * (head < 1.0F ? 2.6F : 3.6F + 0.6F * (float) Math.sin(at * 2.1F)), QuadFx.argb(0.85F * a, QuadFx.mix(rgb, 0xFFFFFF, 0.35F)));
	}
}
