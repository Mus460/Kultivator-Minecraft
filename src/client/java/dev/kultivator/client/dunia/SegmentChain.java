package dev.kultivator.client.dunia;

import dev.kultivator.client.EffectQuality;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A follow-the-leader chain of body segments for long creatures (B's dragons, D's sea serpents; DESIGN 34.6 K, contract
 * C34.S5). Call {@link #push} once per client tick with the head's pose; segment {@code i} keeps {@code spacing} blocks
 * behind segment {@code i - 1}, so the body follows the head's path. {@link #pose} interpolates between the last two ticks
 * for smooth frames. {@link #segments()} applies the level of detail: Minimal quality draws half the segments, and with
 * distance to the camera: all within 32 b, half within 96 b, a quarter within 160 b, else 4.
 */
public final class SegmentChain {

	/** One segment's pose: position and facing (degrees; yaw like an entity's, pitch positive looking down). */
	public record Pose(Vec3 pos, float yaw, float pitch) {
	}

	private final int max;
	private final float spacing;
	private final Vec3[] now;
	private final Vec3[] before;
	private final float[] yaw;
	private final float[] yawBefore;
	private final float[] pitch;
	private final float[] pitchBefore;
	private boolean started;

	public SegmentChain(int maxSegments, float spacing) {
		this.max = Math.max(1, Math.min(256, maxSegments));
		this.spacing = Math.max(0.05F, spacing);
		this.now = new Vec3[max];
		this.before = new Vec3[max];
		this.yaw = new float[max];
		this.yawBefore = new float[max];
		this.pitch = new float[max];
		this.pitchBefore = new float[max];
	}

	/** The head moved to {@code head} facing {@code headYaw} / {@code headPitch} (once per client tick). */
	public void push(Vec3 head, float headYaw, float headPitch) {
		if (!started) {
			// lay the body out straight behind the head
			double rad = Math.toRadians(headYaw);
			Vec3 back = new Vec3(Math.sin(rad), 0.0D, -Math.cos(rad)).scale(spacing);
			for (int i = 0; i < max; i++) {
				now[i] = head.add(back.scale(i));
				before[i] = now[i];
				yaw[i] = headYaw;
				yawBefore[i] = headYaw;
				pitch[i] = headPitch;
				pitchBefore[i] = headPitch;
			}
			started = true;
			return;
		}
		for (int i = 0; i < max; i++) {
			before[i] = now[i];
			yawBefore[i] = yaw[i];
			pitchBefore[i] = pitch[i];
		}
		now[0] = head;
		yaw[0] = headYaw;
		pitch[0] = headPitch;
		for (int i = 1; i < max; i++) {
			Vec3 lead = now[i - 1];
			Vec3 d = now[i].subtract(lead);
			double length = d.length();
			if (length < 1.0E-4D) {
				d = new Vec3(0.0D, 0.0D, spacing);
				length = spacing;
			}
			now[i] = lead.add(d.scale(spacing / length));
			Vec3 forward = lead.subtract(now[i]);
			double horizontal = Math.sqrt(forward.x * forward.x + forward.z * forward.z);
			yaw[i] = (float) (Mth.atan2(forward.z, forward.x) * Mth.RAD_TO_DEG) - 90.0F;
			pitch[i] = (float) (-(Mth.atan2(forward.y, horizontal) * Mth.RAD_TO_DEG));
		}
	}

	/** Forgets the path (a teleport): the next {@link #push} lays the body out straight again. */
	public void reset() {
		started = false;
	}

	/** How many segments to draw now (LOD by quality and the camera's distance to the head). */
	public int segments() {
		if (!started) {
			return 0;
		}
		int n = max;
		if (EffectQuality.minimal()) {
			n = Math.max(1, n / 2);
		}
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 camera = minecraft.gameRenderer == null ? null : minecraft.gameRenderer.mainCamera().position();
		if (camera != null && now[0] != null) {
			double d = camera.distanceTo(now[0]);
			if (d > 160.0D) {
				n = Math.min(n, 4);
			} else if (d > 96.0D) {
				n = Math.max(1, n / 4);
			} else if (d > 32.0D) {
				n = Math.max(1, n / 2);
			}
		}
		return Math.min(max, n);
	}

	/** The total segment count the chain was made for. */
	public int capacity() {
		return max;
	}

	/**
	 * Segment {@code i}'s pose between the last two ticks. With fewer drawn segments than {@link #capacity()}, callers
	 * map their draw index onto the chain ({@code i * capacity() / segments()}) so the body keeps its length.
	 */
	public Pose pose(int i, float partial) {
		int k = Mth.clamp(i, 0, max - 1);
		if (!started || now[k] == null) {
			return new Pose(Vec3.ZERO, 0.0F, 0.0F);
		}
		Vec3 a = before[k] == null ? now[k] : before[k];
		Vec3 pos = new Vec3(Mth.lerp(partial, a.x, now[k].x), Mth.lerp(partial, a.y, now[k].y), Mth.lerp(partial, a.z, now[k].z));
		return new Pose(pos, Mth.rotLerp(partial, yawBefore[k], yaw[k]), Mth.lerp(partial, pitchBefore[k], pitch[k]));
	}
}
