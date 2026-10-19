package dev.kultivator.client.fx.spec;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.EffectQuality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/**
 * Technique camera shake (DESIGN 22.4.5): ≤ 2.5° × intensity × the setting (Penuh 1 / Ringan 0.4 / Mati 0) × vanilla
 * {@code damageTiltStrength}, full within max(8, radius) and 0 at twice that, one shake at a time (the strongest wins).
 * The roll and pitch follow two smooth noise curves and fade out linearly. {@code GameRendererShakeMixin} calls
 * {@link #apply} at the head of {@code GameRenderer.bobHurt}, for the world camera and the hand.
 */
public final class CameraShake {

	public static final float MAX_DEGREES = 2.5F;
	/** The total amplitude per frame never exceeds this (polish S13). */
	public static final float CLAMP_DEGREES = 1.2F;
	public static final float MIN_FULL_RADIUS = 8.0F;

	private static float intensity;
	private static long startMillis;
	private static long endMillis;
	private static Vec3 at = Vec3.ZERO;
	private static float fullRadius = MIN_FULL_RADIUS;

	/** Starts a shake of {@code intensity} (0-1) for {@code ticks} centred at {@code at}; a weaker one never replaces it. */
	public static void add(float intensity, int ticks, Vec3 at, float radius) {
		add(intensity, ticks, at, radius, false);
	}

	/**
	 * Starts a shake (polish S7/S13): non-finite values are dropped, Minimal quality shakes at 0.4 and a climax may reach
	 * the full intensity; the per-frame amplitude is clamped to {@link #CLAMP_DEGREES}.
	 */
	public static void add(float intensity, int ticks, Vec3 at, float radius, boolean climax) {
		if (!Float.isFinite(intensity) || !Float.isFinite(radius) || at == null || !Double.isFinite(at.x) || !Double.isFinite(at.y)
				|| !Double.isFinite(at.z)) {
			return;
		}
		if (intensity <= 0.0F || ticks <= 0 || EffectQuality.shake() == EffectQuality.Shake.OFF) {
			return;
		}
		float quality = EffectQuality.minimal() ? 0.4F : 1.0F;
		long now = Util.getMillis();
		float strength = Math.min(climax ? 1.0F : 0.8F, intensity) * quality;
		radius = Math.max(0.0F, radius);
		if (now < endMillis && current(now) >= strength) {
			return;
		}
		CameraShake.intensity = strength;
		CameraShake.startMillis = now;
		CameraShake.endMillis = now + ticks * 50L;
		CameraShake.at = at;
		CameraShake.fullRadius = Math.max(MIN_FULL_RADIUS, radius);
	}

	/** Rotates the camera pose by the current shake (render thread). */
	public static void apply(CameraRenderState cameraState, PoseStack poseStack) {
		long now = Util.getMillis();
		if (now >= endMillis || intensity <= 0.0F) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.isPaused() || minecraft.level == null) {
			return;
		}
		float setting = EffectQuality.shake().factor();
		if (setting <= 0.0F) {
			return;
		}
		double distance = cameraState.pos.distanceTo(at);
		float falloff = distance <= fullRadius ? 1.0F : (float) Math.max(0.0D, 1.0D - (distance - fullRadius) / fullRadius);
		if (falloff <= 0.0F) {
			return;
		}
		float tilt = (float) (double) minecraft.options.damageTiltStrength().get();
		float degrees = Math.min(CLAMP_DEGREES, MAX_DEGREES * current(now) * setting * tilt * falloff);
		if (degrees <= 0.001F) {
			return;
		}
		double t = (now - startMillis) / 1000.0D;
		float roll = (float) (Math.sin(t * 31.0D) * 0.6D + Math.sin(t * 17.3D + 1.3D) * 0.4D);
		float pitch = (float) (Math.sin(t * 27.0D + 0.7D) * 0.55D + Math.sin(t * 13.1D + 2.1D) * 0.45D);
		poseStack.rotateDegrees(Axis.ZP, roll * degrees);
		poseStack.rotateDegrees(Axis.XP, pitch * degrees * 0.6F);
	}

	/** The intensity left now (linear fade over the shake's length). */
	private static float current(long now) {
		if (now >= endMillis) {
			return 0.0F;
		}
		float span = Math.max(1L, endMillis - startMillis);
		return intensity * Math.max(0.0F, (endMillis - now) / span);
	}

	/** Ends any shake (disconnect, world change). */
	static void clear() {
		intensity = 0.0F;
		endMillis = 0L;
	}

	private CameraShake() {
	}
}
