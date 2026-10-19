package dev.kultivator.client.fx.quad;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A soft glow disc (polish pass: eye glows, coronas, the brightest frame of an impact, a charging orb): a camera-facing
 * disc that pops in, pulses and fades, at a point or riding an entity (eyes at head height, a palm's orb at the hand).
 */
public final class GlowQuad extends QuadFx.Quad {

	private Vec3 at;
	private final float size;
	private final float endSize;
	private final int rgb;
	private final float alpha;
	private final float pulse;
	private int followId = -1;
	private Vec3 offset = Vec3.ZERO;
	private boolean eyes;

	public GlowQuad(Vec3 at, float size, float endSize, int rgb, float alpha, float pulse, int life) {
		super(QuadFx.Kind.GLOW, life);
		this.at = at;
		this.size = size;
		this.endSize = endSize;
		this.rgb = rgb & 0xFFFFFF;
		this.alpha = alpha;
		this.pulse = pulse;
	}

	/** Rides {@code entityId} at {@code offset} from its feet. */
	public GlowQuad follow(int entityId, Vec3 offset) {
		this.followId = entityId;
		this.offset = offset;
		return this;
	}

	/** Two small discs at the eyes of the followed entity (purple demon eye, fiery golden eyes). */
	public GlowQuad eyes() {
		this.eyes = true;
		return this;
	}

	@Override
	protected void tick() {
		if (followId != -1) {
			Entity entity = entity(followId);
			if (entity != null) {
				at = eyes ? entity.getEyePosition() : entity.position().add(offset);
			}
		}
	}

	@Override
	protected void render(QuadFx.Frame frame, float age) {
		float a = alpha * fade(age, 1.5F, Math.max(2.0F, life * 0.5F));
		if (a <= 0.01F || !frame.take(eyes ? 4 : 2)) {
			return;
		}
		float p = Math.min(1.0F, age / life);
		float s = (size + (endSize - size) * p) * (1.0F + pulse * (float) Math.sin(age * 0.9F));
		if (eyes) {
			Entity entity = followId == -1 ? null : entity(followId);
			double yaw = entity == null ? 0.0D : Math.toRadians(entity.getYHeadRot());
			Vec3 side = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw)).scale(0.13D);
			Vec3 front = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw)).scale(0.3D);
			Vec3 base = at.add(front);
			frame.billboard(frame.disc, base.add(side), s, QuadFx.argb(a, rgb));
			frame.billboard(frame.disc, base.subtract(side), s, QuadFx.argb(a, rgb));
			frame.billboard(frame.disc, base.add(side), s * 0.4F, QuadFx.argb(a, 0xFFFFFF));
			frame.billboard(frame.disc, base.subtract(side), s * 0.4F, QuadFx.argb(a, 0xFFFFFF));
			return;
		}
		frame.billboard(frame.disc, at, s, QuadFx.argb(a * 0.8F, rgb));
		frame.billboard(frame.disc, at, s * 0.38F, QuadFx.argb(a, QuadFx.mix(rgb, 0xFFFFFF, 0.7F)));
	}
}
