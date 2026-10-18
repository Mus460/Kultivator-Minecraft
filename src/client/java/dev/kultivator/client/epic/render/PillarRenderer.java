package dev.kultivator.client.epic.render;

import dev.kultivator.client.epic.Element;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.fx.EpicParticles;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * G_PILLAR (DESIGN 23.10.4, 23.10.5): a light pillar drawn with the vanilla {@code BeaconRenderer.submitBeaconBeam} in any
 * colour. Its solid core is opaque and depth-writing, so the solid radius stays 0.05 and the visible size goes into the
 * glow radius (the player standing inside stays visible). It can rise from the ground, descend from the sky (the int
 * start is 0 and the pose moves by the fraction) and narrow or fade by shrinking its glow. Big pillars (glow > 0.5 b or
 * taller than 40 b) take one of the 3 / 2 / 1 slots. Fallback: a particle column.
 */
public final class PillarRenderer extends Element {

	private final Vec3 base;
	private final float height;
	private final int rgb;
	private float glow;
	private float glowTarget;
	private float glowSpeed = 0.05F;
	private float descend;
	private float descendFrom;
	private float riseTicks;
	private boolean firedFallback;

	/** A pillar standing at {@code base}, {@code height} blocks tall (capped at the build limit), glow radius {@code glow}. */
	public PillarRenderer(int delay, float life, Vec3 base, float height, int rgb, float glow) {
		super(delay, life);
		this.base = base;
		this.height = Math.max(1.0F, Math.min(height, BeaconRenderer.MAX_RENDER_Y - (float) base.y));
		this.rgb = rgb & 0xFFFFFF;
		this.glow = glow;
		this.glowTarget = glow;
	}

	/** The pillar descends from {@code from} blocks above the base over {@code ticks}. */
	public PillarRenderer descend(float from, float ticks) {
		this.descendFrom = from;
		this.descend = ticks;
		return this;
	}

	/** The pillar grows up from the ground over {@code ticks}. */
	public PillarRenderer rise(float ticks) {
		this.riseTicks = ticks;
		return this;
	}

	/** Eases the glow radius to {@code target} (narrowing into a thread, or fading out). */
	public void glowTo(float target, float ticks) {
		this.glowTarget = target;
		this.glowSpeed = Math.abs(target - glow) / Math.max(1.0F, ticks);
	}

	public Vec3 base() {
		return base;
	}

	@Override
	public boolean geometry() {
		return true;
	}

	@Override
	public boolean soft() {
		return false;
	}

	@Override
	protected void tick() {
		glow += Mth.clamp(glowTarget - glow, -glowSpeed, glowSpeed);
	}

	@Override
	protected void fallback() {
		if (!firedFallback || (int) age % 10 == 0) {
			firedFallback = true;
			float h = Math.min(48.0F, height);
			EpicParticles.column(base, h, Math.max(0.2F, glow * 0.5F), Math.min(40, Math.round(h * 1.2F)), EpicParticles.dust(rgb, 1.4F), 0.04F);
		}
	}

	@Override
	public void render(EpicRenderer.Frame frame, float at) {
		float g = glow * fade(at, 4.0F, 20.0F);
		if (g <= 0.02F) {
			return;
		}
		boolean big = g > 0.5F || height > 40.0F;
		if (big && !EpicBudget.takePillar()) {
			return;
		}
		if (!EpicBudget.takeQuads(big ? 8 : 16)) {
			return;
		}
		double y = base.y;
		float h = height;
		if (descend > 0.0F && descendFrom > 0.0F) {
			float t = Math.min(1.0F, at / descend);
			float top = descendFrom;
			float bottomOffset = top * (1.0F - t);
			y = base.y + bottomOffset;
			h = Math.max(1.0F, Math.min(height, top - bottomOffset + height * t));
		}
		if (riseTicks > 0.0F) {
			float t = Math.min(1.0F, at / riseTicks);
			h = Math.max(1.0F, height * t * (2.0F - t));
		}
		int blocks = Math.max(1, Math.round(h));
		frame.pillar(base.x, y, base.z, blocks, rgb, g, (float) frame.clock);
	}
}
